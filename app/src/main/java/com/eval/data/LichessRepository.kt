package com.eval.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

/**
 * Enum to track which chess server a game came from
 */
enum class ChessServer {
    LICHESS,
    CHESS_COM
}

class ChessRepository(
    private val lichessApi: LichessApi = LichessApi.create(),
    private val chessComApi: ChessComApi = ChessComApi.create()
) {
    private val gson = Gson()

    /**
     * Get recent games from Lichess.org
     */
    suspend fun getLichessGames(
        username: String,
        maxGames: Int
    ): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getGames(username, max = maxGames)

            if (!response.isSuccessful) {
                return@withContext when (response.code()) {
                    404 -> Result.Error("User not found on Lichess")
                    else -> Result.Error("Failed to fetch game data from Lichess")
                }
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No games found for this user on Lichess")
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
                return@withContext Result.Error("No games found for this user on Lichess")
            }

            Result.Success(games)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Get recent games from Chess.com
     * Chess.com stores games in monthly archives, so we fetch the most recent archive(s)
     */
    suspend fun getChessComGames(
        username: String,
        maxGames: Int
    ): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            // First get the list of archives
            val archivesResponse = chessComApi.getArchives(username.lowercase())

            if (!archivesResponse.isSuccessful) {
                return@withContext when (archivesResponse.code()) {
                    404 -> Result.Error("User not found on Chess.com")
                    else -> Result.Error("Failed to fetch game data from Chess.com")
                }
            }

            val archives = archivesResponse.body()?.archives
            if (archives.isNullOrEmpty()) {
                return@withContext Result.Error("No games found for this user on Chess.com")
            }

            // Get games from the most recent archives until we have enough
            val allGames = mutableListOf<LichessGame>()

            // Start from the most recent archive (last in the list)
            for (archiveUrl in archives.reversed()) {
                if (allGames.size >= maxGames) break

                // Parse year and month from archive URL
                // Format: https://api.chess.com/pub/player/{username}/games/{year}/{month}
                val parts = archiveUrl.split("/")
                if (parts.size < 2) continue

                val month = parts.last()
                val year = parts[parts.size - 2].toIntOrNull() ?: continue

                try {
                    val gamesResponse = chessComApi.getGamesForMonth(username.lowercase(), year, month)
                    if (gamesResponse.isSuccessful) {
                        val chessComGames = gamesResponse.body()?.games ?: emptyList()
                        // Convert Chess.com games to LichessGame format (reversed to get most recent first)
                        val converted = chessComGames.reversed().mapNotNull { game ->
                            convertChessComGameToLichessFormat(game, username)
                        }
                        allGames.addAll(converted)
                    }
                } catch (e: Exception) {
                    // Continue to next archive if one fails
                    android.util.Log.e("ChessRepository", "Error fetching Chess.com archive: ${e.message}")
                }
            }

            if (allGames.isEmpty()) {
                return@withContext Result.Error("No games found for this user on Chess.com")
            }

            // Return only the requested number of games
            Result.Success(allGames.take(maxGames))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Convert a Chess.com game to LichessGame format for unified handling
     */
    private fun convertChessComGameToLichessFormat(game: ChessComGame, searchedUsername: String): LichessGame? {
        val pgn = game.pgn ?: return null

        // Determine winner from results
        val winner = when {
            game.white.result == "win" -> "white"
            game.black.result == "win" -> "black"
            else -> null
        }

        // Parse time control (e.g., "600" or "180+2")
        val timeControl = game.time_control ?: "0"
        val (initial, increment) = if (timeControl.contains("+")) {
            val parts = timeControl.split("+")
            Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
        } else {
            Pair(timeControl.toIntOrNull() ?: 0, 0)
        }

        // Determine speed from time class or time control
        val speed = game.time_class ?: when {
            initial < 180 -> "bullet"
            initial < 600 -> "blitz"
            initial < 1800 -> "rapid"
            else -> "classical"
        }

        return LichessGame(
            id = game.uuid ?: game.url.substringAfterLast("/"),
            rated = game.rated ?: false,
            variant = game.rules ?: "standard",
            speed = speed,
            perf = game.time_class,
            status = game.white.result ?: game.black.result ?: "unknown",
            winner = winner,
            players = Players(
                white = Player(
                    user = User(
                        name = game.white.username,
                        id = game.white.username.lowercase()
                    ),
                    rating = game.white.rating,
                    aiLevel = null
                ),
                black = Player(
                    user = User(
                        name = game.black.username,
                        id = game.black.username.lowercase()
                    ),
                    rating = game.black.rating,
                    aiLevel = null
                )
            ),
            pgn = pgn,
            moves = null,
            clock = Clock(initial = initial, increment = increment),
            createdAt = game.end_time?.times(1000),  // Convert to milliseconds
            lastMoveAt = game.end_time?.times(1000)
        )
    }

    /**
     * Legacy method for backward compatibility - calls getLichessGames
     */
    suspend fun getRecentGames(
        username: String,
        maxGames: Int
    ): Result<List<LichessGame>> = getLichessGames(username, maxGames)
}
