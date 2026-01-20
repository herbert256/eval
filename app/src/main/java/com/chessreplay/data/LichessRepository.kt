package com.chessreplay.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

enum class ChessSource {
    LICHESS,
    CHESS_COM
}

class ChessRepository(
    private val lichessApi: LichessApi = LichessApi.create(),
    private val chessComApi: ChessComApi = ChessComApi.create()
) {
    private val gson = Gson()

    suspend fun getRecentGames(
        username: String,
        source: ChessSource,
        maxGames: Int
    ): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        when (source) {
            ChessSource.LICHESS -> getLichessGames(username, maxGames)
            ChessSource.CHESS_COM -> getChessComGames(username, maxGames)
        }
    }

    private suspend fun getLichessGames(username: String, maxGames: Int): Result<List<LichessGame>> {
        try {
            val response = lichessApi.getGames(username, max = maxGames)

            if (!response.isSuccessful) {
                return when (response.code()) {
                    404 -> Result.Error("User not found on Lichess")
                    else -> Result.Error("Failed to fetch game data from Lichess")
                }
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return Result.Error("No games found for this user on Lichess")
            }

            // Parse NDJSON (each line is a game)
            val games = body.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        gson.fromJson(line, LichessGame::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }

            if (games.isEmpty()) {
                return Result.Error("No games found for this user on Lichess")
            }

            return Result.Success(games)
        } catch (e: Exception) {
            return Result.Error(e.message ?: "Unknown error occurred")
        }
    }

    private suspend fun getChessComGames(username: String, maxGames: Int): Result<List<LichessGame>> {
        try {
            // First get the list of archives
            val archivesResponse = chessComApi.getArchives(username.lowercase())

            if (!archivesResponse.isSuccessful) {
                return when (archivesResponse.code()) {
                    404 -> Result.Error("User not found on Chess.com")
                    else -> Result.Error("Failed to fetch game data from Chess.com (${archivesResponse.code()})")
                }
            }

            val archives = archivesResponse.body()?.archives
            if (archives.isNullOrEmpty()) {
                return Result.Error("No games found for this user on Chess.com")
            }

            // Get games from the most recent archives until we have enough
            val allGames = mutableListOf<LichessGame>()
            val sortedArchives = archives.sortedDescending() // Most recent first

            for (archiveUrl in sortedArchives) {
                if (allGames.size >= maxGames) break

                // Extract year and month from archive URL (format: .../games/2024/01)
                val parts = archiveUrl.split("/")
                if (parts.size >= 2) {
                    val month = parts.last()
                    val year = parts[parts.size - 2].toIntOrNull() ?: continue

                    val gamesResponse = chessComApi.getGamesForMonth(username.lowercase(), year, month)
                    if (gamesResponse.isSuccessful) {
                        val chessComGames = gamesResponse.body()?.games ?: emptyList()
                        // Convert Chess.com games to our format and add in reverse order (most recent first)
                        val convertedGames = chessComGames
                            .sortedByDescending { it.end_time }
                            .map { convertChessComGame(it) }
                        allGames.addAll(convertedGames)
                    }
                }
            }

            if (allGames.isEmpty()) {
                return Result.Error("No games found for this user on Chess.com")
            }

            // Return only the requested number of games
            return Result.Success(allGames.take(maxGames))
        } catch (e: Exception) {
            return Result.Error(e.message ?: "Unknown error occurred")
        }
    }

    private fun convertChessComGame(game: ChessComGame): LichessGame {
        // Determine winner from results
        val winner = when {
            game.white.result == "win" -> "white"
            game.black.result == "win" -> "black"
            else -> null
        }

        // Determine status
        val status = when {
            game.white.result == "checkmated" || game.black.result == "checkmated" -> "mate"
            game.white.result == "resigned" || game.black.result == "resigned" -> "resign"
            game.white.result == "timeout" || game.black.result == "timeout" -> "outoftime"
            game.white.result == "stalemate" || game.black.result == "stalemate" -> "stalemate"
            game.white.result == "agreed" || game.black.result == "agreed" -> "draw"
            game.white.result == "repetition" || game.black.result == "repetition" -> "draw"
            game.white.result == "insufficient" || game.black.result == "insufficient" -> "draw"
            else -> "unknown"
        }

        // Extract game ID from URL
        val gameId = game.url.substringAfterLast("/")

        return LichessGame(
            id = gameId,
            rated = game.rated ?: false,
            variant = game.rules ?: "standard",
            speed = game.time_class ?: "unknown",
            perf = game.time_class,
            status = status,
            winner = winner,
            players = Players(
                white = Player(
                    user = User(name = game.white.username, id = game.white.username.lowercase()),
                    rating = game.white.rating,
                    aiLevel = null
                ),
                black = Player(
                    user = User(name = game.black.username, id = game.black.username.lowercase()),
                    rating = game.black.rating,
                    aiLevel = null
                )
            ),
            pgn = game.pgn,
            moves = null,
            clock = null,
            createdAt = game.end_time?.times(1000), // Convert to milliseconds
            lastMoveAt = game.end_time?.times(1000)
        )
    }
}
