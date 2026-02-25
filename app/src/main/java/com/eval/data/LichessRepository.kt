package com.eval.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

/**
 * Unified player info from either Lichess or Chess.com
 */
data class PlayerInfo(
    val username: String,
    val server: ChessServer,
    val title: String?,
    val name: String?,
    val country: String?,
    val location: String?,
    val bio: String?,
    val online: Boolean?,
    val createdAt: Long?,
    val lastOnline: Long?,
    val profileUrl: String?,
    // Ratings by time control
    val bulletRating: Int?,
    val blitzRating: Int?,
    val rapidRating: Int?,
    val classicalRating: Int?,
    val dailyRating: Int?,
    // Game counts
    val totalGames: Int?,
    val wins: Int?,
    val losses: Int?,
    val draws: Int?,
    // Play time in seconds
    val playTimeSeconds: Long?,
    // Additional info
    val followers: Int?,
    val isStreamer: Boolean?
)

class ChessRepository(
    private val lichessApi: LichessApi = LichessApi.create(),
    private val chessComApi: ChessComApi = ChessComApi.create()
) {
    private val gson = Gson()

    private inline fun <reified T> parseNdjson(body: String): List<T> {
        return body.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    gson.fromJson(line, T::class.java)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Process an NDJSON API response: validate the response body, parse each
     * line as JSON of type [T], and return a [Result] wrapping the parsed list.
     *
     * @param body       The raw response body string (may be null or blank).
     * @param emptyError The error message to use when the body is blank or
     *                   parsing yields an empty list.
     */
    private inline fun <reified T> processNdjsonResponse(
        body: String?,
        emptyError: String
    ): Result<List<T>> {
        if (body.isNullOrBlank()) {
            return Result.Error(emptyError)
        }
        val items = parseNdjson<T>(body)
        if (items.isEmpty()) {
            return Result.Error(emptyError)
        }
        return Result.Success(items)
    }

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
            processNdjsonResponse<LichessGame>(body, "No games found for this user on Lichess")
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get player info from Lichess
     */
    suspend fun getLichessPlayerInfo(username: String): Result<PlayerInfo> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getUser(username)

            if (!response.isSuccessful) {
                return@withContext when (response.code()) {
                    404 -> Result.Error("User not found on Lichess")
                    else -> Result.Error("Failed to fetch user data from Lichess")
                }
            }

            val user = response.body() ?: return@withContext Result.Error("No user data received")

            val perfs = user.perfs ?: emptyMap()
            val count = user.count

            Result.Success(PlayerInfo(
                username = user.username,
                server = ChessServer.LICHESS,
                title = user.title,
                name = listOfNotNull(user.profile?.firstName, user.profile?.lastName)
                    .takeIf { it.isNotEmpty() }?.joinToString(" "),
                country = user.profile?.country,
                location = user.profile?.location,
                bio = user.profile?.bio,
                online = user.online,
                createdAt = user.createdAt,
                lastOnline = user.seenAt,
                profileUrl = user.url ?: "https://lichess.org/@/${user.username}",
                bulletRating = perfs["bullet"]?.rating,
                blitzRating = perfs["blitz"]?.rating,
                rapidRating = perfs["rapid"]?.rating,
                classicalRating = perfs["classical"]?.rating,
                dailyRating = perfs["correspondence"]?.rating,
                totalGames = count?.all,
                wins = count?.win,
                losses = count?.loss,
                draws = count?.draw,
                playTimeSeconds = user.playTime?.total,
                followers = null,
                isStreamer = user.streaming
            ))
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get player info from the appropriate server
     */
    suspend fun getPlayerInfo(username: String, server: ChessServer): Result<PlayerInfo> {
        return when (server) {
            ChessServer.LICHESS -> getLichessPlayerInfo(username)
            ChessServer.CHESS_COM -> getChessComPlayerInfo(username)
        }
    }

    /**
     * Get Lichess leaderboard (top players for each format)
     */
    suspend fun getLichessLeaderboard(): Result<Map<String, List<LeaderboardPlayer>>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getLeaderboard()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch Lichess leaderboard")
            }

            val leaderboard = response.body() ?: return@withContext Result.Error("No leaderboard data received")

            val result = mutableMapOf<String, List<LeaderboardPlayer>>()

            // Convert each category to LeaderboardPlayer list
            leaderboard.bullet?.let { players ->
                result["Bullet"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username,
                        title = p.title,
                        rating = p.perfs?.get("bullet")?.rating,
                        server = ChessServer.LICHESS
                    )
                }
            }
            leaderboard.blitz?.let { players ->
                result["Blitz"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username,
                        title = p.title,
                        rating = p.perfs?.get("blitz")?.rating,
                        server = ChessServer.LICHESS
                    )
                }
            }
            leaderboard.rapid?.let { players ->
                result["Rapid"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username,
                        title = p.title,
                        rating = p.perfs?.get("rapid")?.rating,
                        server = ChessServer.LICHESS
                    )
                }
            }
            leaderboard.classical?.let { players ->
                result["Classical"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username,
                        title = p.title,
                        rating = p.perfs?.get("classical")?.rating,
                        server = ChessServer.LICHESS
                    )
                }
            }

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get Lichess live streamers
     */
    suspend fun getLichessStreamers(): Result<List<StreamerInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getLiveStreamers()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch Lichess streamers")
            }

            val responseBody = response.body() ?: return@withContext Result.Error("No streamer data received")

            // Parse the JSON array of streamers
            val streamers = mutableListOf<StreamerInfo>()
            try {
                val jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray::class.java)
                for (element in jsonArray) {
                    if (!element.isJsonObject) continue
                    val obj = element.asJsonObject
                    val username = obj.get("id")?.asString ?: continue
                    val name = obj.get("name")?.asString ?: username
                    val twitch = obj.get("twitch")?.asJsonObject
                    val twitchUrl = twitch?.get("url")?.asString
                    streamers.add(StreamerInfo(
                        username = name,
                        isLive = true,
                        twitchUrl = twitchUrl,
                        profileUrl = "https://lichess.org/@/$username",
                        server = ChessServer.LICHESS
                    ))
                }
            } catch (e: Exception) {
                return@withContext Result.Error("Failed to parse streamers: ${e.message}")
            }

            Result.Success(streamers)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get Lichess current tournaments
     */
    suspend fun getLichessTournaments(): Result<List<TournamentInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getTournaments()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch Lichess tournaments")
            }

            val tournamentList = response.body() ?: return@withContext Result.Error("No tournament data received")

            val result = mutableListOf<TournamentInfo>()

            // Add started tournaments first (most relevant)
            tournamentList.started?.forEach { t ->
                result.add(TournamentInfo(
                    id = t.id,
                    name = t.fullName ?: t.name ?: "Unknown",
                    status = "In Progress",
                    playerCount = t.nbPlayers ?: 0,
                    startsAt = t.startsAt,
                    variant = t.variant?.name ?: "Standard",
                    timeControl = formatTimeControl(t.clock?.limit, t.clock?.increment),
                    server = ChessServer.LICHESS
                ))
            }

            // Add created (upcoming) tournaments
            tournamentList.created?.take(10)?.forEach { t ->
                result.add(TournamentInfo(
                    id = t.id,
                    name = t.fullName ?: t.name ?: "Unknown",
                    status = "Starting Soon",
                    playerCount = t.nbPlayers ?: 0,
                    startsAt = t.startsAt,
                    variant = t.variant?.name ?: "Standard",
                    timeControl = formatTimeControl(t.clock?.limit, t.clock?.increment),
                    server = ChessServer.LICHESS
                ))
            }

            // Add recently finished tournaments
            tournamentList.finished?.take(10)?.forEach { t ->
                result.add(TournamentInfo(
                    id = t.id,
                    name = t.fullName ?: t.name ?: "Unknown",
                    status = "Finished",
                    playerCount = t.nbPlayers ?: 0,
                    startsAt = t.startsAt,
                    variant = t.variant?.name ?: "Standard",
                    timeControl = formatTimeControl(t.clock?.limit, t.clock?.increment),
                    server = ChessServer.LICHESS
                ))
            }

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    private fun formatTimeControl(limitSeconds: Int?, increment: Int?): String {
        if (limitSeconds == null) return "Unknown"
        val minutes = limitSeconds / 60
        return if (increment != null && increment > 0) {
            "$minutes+$increment"
        } else {
            "$minutes min"
        }
    }

    /**
     * Get games from a Lichess tournament
     */
    suspend fun getLichessTournamentGames(tournamentId: String): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getTournamentGames(tournamentId)

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch tournament games")
            }

            val body = response.body()
            processNdjsonResponse<LichessGame>(body, "No games found in this tournament")
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get Lichess current broadcasts (official events)
     */
    suspend fun getLichessBroadcasts(): Result<List<BroadcastInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getBroadcasts()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch Lichess broadcasts")
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No broadcast data received")
            }

            val broadcasts = parseNdjson<LichessBroadcast>(body)
            val result = broadcasts.mapNotNull { broadcast ->
                val tour = broadcast.tour ?: return@mapNotNull null

                // Convert all rounds to BroadcastRoundInfo
                val rounds = broadcast.rounds?.mapNotNull roundLoop@{ round ->
                    val roundId = round.id ?: return@roundLoop null
                    BroadcastRoundInfo(
                        id = roundId,
                        name = round.name ?: "Round",
                        ongoing = round.ongoing == true,
                        finished = round.finished == true,
                        startsAt = round.startsAt
                    )
                } ?: emptyList()

                val hasOngoing = rounds.any { it.ongoing }
                val firstRoundStart = rounds.firstOrNull()?.startsAt

                BroadcastInfo(
                    id = tour.id ?: return@mapNotNull null,
                    name = tour.name ?: "Unknown",
                    description = tour.description,
                    rounds = rounds,
                    ongoing = hasOngoing,
                    startsAt = firstRoundStart,
                    server = ChessServer.LICHESS
                )
            }

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get games from a Lichess broadcast round
     */
    suspend fun getLichessBroadcastGames(roundId: String): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getBroadcastRoundPgn(roundId)

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch broadcast games: ${response.code()}")
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No games found in this broadcast")
            }

            // Parse PGN - games are separated by double newlines
            val games = body.split(Regex("\n\n(?=\\[Event)"))
                .filter { it.isNotBlank() }
                .mapNotNull { pgn ->
                    try {
                        convertPgnToLichessGame(pgn.trim())
                    } catch (e: Exception) {
                        android.util.Log.e("ChessRepository", "Error parsing broadcast game: ${e.message}")
                        null
                    }
                }

            if (games.isEmpty()) {
                return@withContext Result.Error("No games found in this broadcast")
            }

            Result.Success(games)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Convert a PGN string to LichessGame format
     */
    private fun convertPgnToLichessGame(pgn: String): LichessGame? {
        if (pgn.isBlank()) return null

        val whiteName = extractPgnTag(pgn, "White") ?: "White"
        val blackName = extractPgnTag(pgn, "Black") ?: "Black"
        val result = extractPgnTag(pgn, "Result")

        val winner = when (result) {
            "1-0" -> "white"
            "0-1" -> "black"
            else -> null
        }

        val gameUrl = extractPgnTag(pgn, "GameURL")
        val gameId = gameUrl?.substringAfterLast("/") ?: java.util.UUID.randomUUID().toString()

        return LichessGame(
            id = gameId,
            rated = false,
            variant = "standard",
            speed = "classical",
            perf = "classical",
            status = result ?: "unknown",
            winner = winner,
            players = Players(
                white = Player(
                    user = User(name = whiteName, id = whiteName.lowercase().replace(" ", "_")),
                    rating = extractPgnTag(pgn, "WhiteElo")?.toIntOrNull(),
                    aiLevel = null
                ),
                black = Player(
                    user = User(name = blackName, id = blackName.lowercase().replace(" ", "_")),
                    rating = extractPgnTag(pgn, "BlackElo")?.toIntOrNull(),
                    aiLevel = null
                )
            ),
            pgn = pgn,
            moves = null,
            clock = null,
            createdAt = null,
            lastMoveAt = null
        )
    }

    private fun extractPgnTag(pgn: String, tagName: String): String? {
        val regex = """\[$tagName\s+"([^"]+)"\]""".toRegex()
        return regex.find(pgn)?.groupValues?.get(1)
    }

    /**
     * Parse multiple games from a PGN file content.
     * Games are separated by double newlines before [Event tag.
     */
    fun parseGamesFromPgnContent(pgnContent: String): Result<List<LichessGame>> {
        if (pgnContent.isBlank()) {
            return Result.Error("PGN file is empty")
        }

        // Split PGN content into individual games
        // Games are separated by double newlines followed by [Event
        val gameStrings = pgnContent.split(Regex("\n\n(?=\\[Event)"))
            .filter { it.isNotBlank() }

        if (gameStrings.isEmpty()) {
            return Result.Error("No games found in PGN file")
        }

        val games = gameStrings.mapNotNull { pgn ->
            try {
                convertPgnToLichessGame(pgn.trim())
            } catch (e: Exception) {
                null
            }
        }

        if (games.isEmpty()) {
            return Result.Error("Failed to parse any games from PGN file")
        }

        return Result.Success(games)
    }

    /**
     * Get Lichess TV channels (current top games)
     */
    suspend fun getLichessTvChannels(): Result<List<TvChannelInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getTvChannels()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch Lichess TV channels: ${response.code()}")
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No TV data received")
            }

            val channels = try {
                gson.fromJson(body, LichessTvChannels::class.java)
            } catch (e: Exception) {
                return@withContext Result.Error("Error parsing TV data: ${e.message}")
            }

            if (channels == null) {
                return@withContext Result.Error("Failed to parse TV data")
            }

            val result = mutableListOf<TvChannelInfo>()

            channels.best?.let { game ->
                if (game.gameId != null) {
                    result.add(TvChannelInfo(
                        channelName = "Best",
                        gameId = game.gameId,
                        playerName = game.user?.name ?: "Unknown",
                        playerTitle = game.user?.title,
                        rating = game.rating,
                        server = ChessServer.LICHESS
                    ))
                }
            }
            channels.bullet?.let { game ->
                if (game.gameId != null) {
                    result.add(TvChannelInfo(
                        channelName = "Bullet",
                        gameId = game.gameId,
                        playerName = game.user?.name ?: "Unknown",
                        playerTitle = game.user?.title,
                        rating = game.rating,
                        server = ChessServer.LICHESS
                    ))
                }
            }
            channels.blitz?.let { game ->
                if (game.gameId != null) {
                    result.add(TvChannelInfo(
                        channelName = "Blitz",
                        gameId = game.gameId,
                        playerName = game.user?.name ?: "Unknown",
                        playerTitle = game.user?.title,
                        rating = game.rating,
                        server = ChessServer.LICHESS
                    ))
                }
            }
            channels.rapid?.let { game ->
                if (game.gameId != null) {
                    result.add(TvChannelInfo(
                        channelName = "Rapid",
                        gameId = game.gameId,
                        playerName = game.user?.name ?: "Unknown",
                        playerTitle = game.user?.title,
                        rating = game.rating,
                        server = ChessServer.LICHESS
                    ))
                }
            }
            channels.classical?.let { game ->
                if (game.gameId != null) {
                    result.add(TvChannelInfo(
                        channelName = "Classical",
                        gameId = game.gameId,
                        playerName = game.user?.name ?: "Unknown",
                        playerTitle = game.user?.title,
                        rating = game.rating,
                        server = ChessServer.LICHESS
                    ))
                }
            }
            channels.chess960?.let { game ->
                if (game.gameId != null) {
                    result.add(TvChannelInfo(
                        channelName = "Chess960",
                        gameId = game.gameId,
                        playerName = game.user?.name ?: "Unknown",
                        playerTitle = game.user?.title,
                        rating = game.rating,
                        server = ChessServer.LICHESS
                    ))
                }
            }

            if (com.eval.BuildConfig.DEBUG) android.util.Log.d("ChessRepository", "getLichessTvChannels: returning ${result.size} channels")
            Result.Success(result)
        } catch (e: Exception) {
            android.util.Log.e("ChessRepository", "getLichessTvChannels: Exception: ${e.message}", e)
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get a single Lichess game by ID
     */
    suspend fun getLichessGame(gameId: String): Result<LichessGame> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getGame(gameId)

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch game")
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No game data received")
            }

            val game = gson.fromJson(body, LichessGame::class.java)
            Result.Success(game)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Stream a live Lichess game to get all moves.
     * This works for ongoing games that don't have PGN yet.
     */
    suspend fun streamLichessGame(gameId: String): Result<LichessGame> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.streamGame(gameId)

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to stream game: ${response.code()}")
            }

            val responseBody = response.body()
                ?: return@withContext Result.Error("No stream data received")

            val reader = responseBody.source()
            val lines = mutableListOf<String>()

            // Set a timeout for reading - the stream sends historical moves quickly,
            // then waits for new moves (which would block forever)
            reader.timeout().timeout(2, java.util.concurrent.TimeUnit.SECONDS)

            try {
                // Read lines until timeout (meaning no more historical moves)
                while (true) {
                    val line = reader.readUtf8Line() ?: break
                    if (line.isNotBlank()) {
                        lines.add(line)
                    }
                    // Safety limit - don't read forever
                    if (lines.size > 500) break
                }
            } catch (e: java.io.InterruptedIOException) {
                // Timeout expected - we've read all available data
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout expected - we've read all available data
            } finally {
                responseBody.close()
            }

            if (lines.isEmpty()) {
                return@withContext Result.Error("No game data in stream")
            }

            // First line is game metadata
            val gameInfo = try {
                gson.fromJson(lines[0], StreamGameInfo::class.java)
            } catch (e: Exception) {
                return@withContext Result.Error("Failed to parse game info: ${e.message}")
            }

            // Remaining lines contain moves (skip line 1 which is starting position)
            val moves = mutableListOf<String>()
            for (i in 2 until lines.size) {
                try {
                    val moveData = gson.fromJson(lines[i], StreamMoveData::class.java)
                    moveData.lm?.let { moves.add(it) }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }

            // Build a pseudo-PGN from the moves
            val pgn = buildPgnFromMoves(moves, gameInfo)

            // Create LichessGame from streamed data
            val game = LichessGame(
                id = gameInfo.id ?: gameId,
                rated = gameInfo.rated ?: false,
                variant = gameInfo.variant?.key ?: "standard",
                speed = gameInfo.speed ?: "rapid",
                perf = gameInfo.perf,
                status = "started",
                winner = null,
                players = Players(
                    white = Player(
                        user = gameInfo.players?.white?.user?.let {
                            User(name = it.name ?: "White", id = it.id ?: "white")
                        },
                        rating = gameInfo.players?.white?.rating,
                        aiLevel = null
                    ),
                    black = Player(
                        user = gameInfo.players?.black?.user?.let {
                            User(name = it.name ?: "Black", id = it.id ?: "black")
                        },
                        rating = gameInfo.players?.black?.rating,
                        aiLevel = null
                    )
                ),
                pgn = pgn,
                moves = moves.joinToString(" "),
                clock = null,
                createdAt = gameInfo.createdAt,
                lastMoveAt = null
            )

            Result.Success(game)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Stream a live Lichess game as a Flow of events.
     * This is for continuous following of a live game.
     *
     * @param gameId The Lichess game ID
     * @return Flow of LiveGameEvent
     */
    fun streamLiveGame(gameId: String): Flow<LiveGameEvent> = flow {
        try {
            emit(LiveGameEvent.Connected)

            val response = lichessApi.streamGame(gameId)

            if (!response.isSuccessful) {
                emit(LiveGameEvent.Error("Failed to connect: ${response.code()}"))
                emit(LiveGameEvent.Disconnected)
                return@flow
            }

            val responseBody = response.body()
            if (responseBody == null) {
                emit(LiveGameEvent.Error("No stream data"))
                emit(LiveGameEvent.Disconnected)
                return@flow
            }

            val reader = responseBody.source()
            var isFirstLine = true
            var isSecondLine = true

            try {
                while (!reader.exhausted()) {
                    val line = reader.readUtf8Line() ?: break
                    if (line.isBlank()) continue

                    if (isFirstLine) {
                        // First line is game info
                        try {
                            val gameInfo = gson.fromJson(line, StreamGameInfo::class.java)
                            emit(LiveGameEvent.GameInfo(gameInfo))
                        } catch (e: Exception) {
                            emit(LiveGameEvent.Error("Failed to parse game info"))
                        }
                        isFirstLine = false
                    } else if (isSecondLine) {
                        // Second line is starting position, skip it
                        isSecondLine = false
                    } else {
                        // Subsequent lines are moves
                        try {
                            val moveData = gson.fromJson(line, StreamMoveData::class.java)
                            if (moveData.lm != null) {
                                emit(LiveGameEvent.Move(moveData))
                            }
                        } catch (e: Exception) {
                            // May be game end info or other data
                            // Check for game end
                            if (line.contains("\"status\":") && (line.contains("\"winner\":") || line.contains("draw"))) {
                                try {
                                    val endData = gson.fromJson(line, GameEndData::class.java)
                                    emit(LiveGameEvent.GameEnd(endData.winner, endData.status))
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                // Stream closed, possibly game ended
            } finally {
                responseBody.close()
            }
            emit(LiveGameEvent.Disconnected)
        } catch (e: Exception) {
            emit(LiveGameEvent.Error(e.message ?: "Stream error"))
            emit(LiveGameEvent.Disconnected)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Build a minimal PGN from UCI moves
     */
    private fun buildPgnFromMoves(moves: List<String>, gameInfo: StreamGameInfo): String {
        val whiteName = gameInfo.players?.white?.user?.name ?: "White"
        val blackName = gameInfo.players?.black?.user?.name ?: "Black"
        val whiteRating = gameInfo.players?.white?.rating
        val blackRating = gameInfo.players?.black?.rating

        val headers = buildString {
            appendLine("[Event \"Live Game\"]")
            appendLine("[Site \"lichess.org\"]")
            appendLine("[White \"$whiteName\"]")
            appendLine("[Black \"$blackName\"]")
            whiteRating?.let { appendLine("[WhiteElo \"$it\"]") }
            blackRating?.let { appendLine("[BlackElo \"$it\"]") }
            appendLine("[Result \"*\"]")
            appendLine()
        }

        // Convert UCI moves to numbered format (not SAN, but loadGame uses UCI internally)
        val moveText = buildString {
            moves.forEachIndexed { index, move ->
                if (index % 2 == 0) {
                    append("${(index / 2) + 1}. ")
                }
                append("$move ")
            }
            append("*")
        }

        return headers + moveText
    }

    // ==================== CHESS.COM API METHODS ====================

    /**
     * Convert a Chess.com game to LichessGame format for reuse in the pipeline.
     */
    private fun convertChessComGameToLichessGame(game: ChessComGame): LichessGame? {
        val pgn = game.pgn ?: return null
        val whiteName = game.white?.username ?: "White"
        val blackName = game.black?.username ?: "Black"

        val winner = when {
            game.white?.result == "win" -> "white"
            game.black?.result == "win" -> "black"
            else -> null
        }

        val status = when {
            winner != null -> "mate"
            game.white?.result == "agreed" || game.white?.result == "repetition" ||
            game.white?.result == "stalemate" || game.white?.result == "insufficient" ||
            game.white?.result == "50move" -> "draw"
            else -> game.white?.result ?: "unknown"
        }

        val gameUrl = game.url
        val gameId = gameUrl?.substringAfterLast("/") ?: java.util.UUID.randomUUID().toString()

        val speed = game.time_class ?: "unknown"

        return LichessGame(
            id = gameId,
            rated = game.rated ?: false,
            variant = if (game.rules == "chess") "standard" else game.rules ?: "standard",
            speed = speed,
            perf = speed,
            status = status,
            winner = winner,
            players = Players(
                white = Player(
                    user = User(name = whiteName, id = whiteName.lowercase()),
                    rating = game.white?.rating,
                    aiLevel = null
                ),
                black = Player(
                    user = User(name = blackName, id = blackName.lowercase()),
                    rating = game.black?.rating,
                    aiLevel = null
                )
            ),
            pgn = pgn,
            moves = null,
            clock = null,
            createdAt = game.end_time?.times(1000),
            lastMoveAt = game.end_time?.times(1000)
        )
    }

    /**
     * Get recent games from Chess.com.
     * Chess.com stores games in monthly archives, so we fetch the most recent months
     * in reverse order until we have enough games.
     */
    suspend fun getChessComGames(
        username: String,
        maxGames: Int
    ): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            val archivesResponse = chessComApi.getArchives(username)

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

            // Fetch most recent months in reverse order
            val allGames = mutableListOf<LichessGame>()
            val reversedArchives = archives.reversed()

            for (archiveUrl in reversedArchives) {
                if (allGames.size >= maxGames) break

                try {
                    val gamesResponse = chessComApi.getMonthlyGames(archiveUrl)
                    if (gamesResponse.isSuccessful) {
                        val monthGames = gamesResponse.body()?.games
                        if (monthGames != null) {
                            // Convert and add in reverse order (most recent first)
                            val converted = monthGames.reversed().mapNotNull { game ->
                                convertChessComGameToLichessGame(game)
                            }
                            allGames.addAll(converted)
                        }
                    }
                } catch (e: Exception) {
                    // Skip failed months, continue to next
                    android.util.Log.w("ChessRepository", "Failed to fetch archive $archiveUrl: ${e.message}")
                }
            }

            if (allGames.isEmpty()) {
                return@withContext Result.Error("No games found for this user on Chess.com")
            }

            Result.Success(allGames.take(maxGames))
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get player info from Chess.com
     */
    suspend fun getChessComPlayerInfo(username: String): Result<PlayerInfo> = withContext(Dispatchers.IO) {
        try {
            val profileResponse = chessComApi.getProfile(username)

            if (!profileResponse.isSuccessful) {
                return@withContext when (profileResponse.code()) {
                    404 -> Result.Error("User not found on Chess.com")
                    else -> Result.Error("Failed to fetch user data from Chess.com")
                }
            }

            val profile = profileResponse.body()
                ?: return@withContext Result.Error("No user data received")

            // Fetch stats separately
            var bulletRating: Int? = null
            var blitzRating: Int? = null
            var rapidRating: Int? = null
            var dailyRating: Int? = null
            var totalWins = 0
            var totalLosses = 0
            var totalDraws = 0

            try {
                val statsResponse = chessComApi.getStats(username)
                if (statsResponse.isSuccessful) {
                    val stats = statsResponse.body()
                    bulletRating = stats?.chess_bullet?.last?.rating
                    blitzRating = stats?.chess_blitz?.last?.rating
                    rapidRating = stats?.chess_rapid?.last?.rating
                    dailyRating = stats?.chess_daily?.last?.rating

                    listOfNotNull(
                        stats?.chess_bullet?.record,
                        stats?.chess_blitz?.record,
                        stats?.chess_rapid?.record,
                        stats?.chess_daily?.record
                    ).forEach { record ->
                        totalWins += record.win ?: 0
                        totalLosses += record.loss ?: 0
                        totalDraws += record.draw ?: 0
                    }
                }
            } catch (e: Exception) {
                // Stats fetch failed, continue with profile only
            }

            val totalGames = totalWins + totalLosses + totalDraws

            Result.Success(PlayerInfo(
                username = profile.username ?: username,
                server = ChessServer.CHESS_COM,
                title = profile.title,
                name = profile.name,
                country = profile.country?.substringAfterLast("/"),
                location = profile.location,
                bio = null,
                online = profile.status == "online",
                createdAt = profile.joined?.times(1000),
                lastOnline = profile.last_online?.times(1000),
                profileUrl = profile.url ?: "https://www.chess.com/member/${profile.username ?: username}",
                bulletRating = bulletRating,
                blitzRating = blitzRating,
                rapidRating = rapidRating,
                classicalRating = null,
                dailyRating = dailyRating,
                totalGames = if (totalGames > 0) totalGames else null,
                wins = if (totalWins > 0) totalWins else null,
                losses = if (totalLosses > 0) totalLosses else null,
                draws = if (totalDraws > 0) totalDraws else null,
                playTimeSeconds = null,
                followers = profile.followers,
                isStreamer = profile.is_streamer
            ))
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get Chess.com leaderboard (top players for each format)
     */
    suspend fun getChessComLeaderboard(): Result<Map<String, List<LeaderboardPlayer>>> = withContext(Dispatchers.IO) {
        try {
            val response = chessComApi.getLeaderboards()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch Chess.com leaderboard")
            }

            val leaderboards = response.body()
                ?: return@withContext Result.Error("No leaderboard data received")

            val result = mutableMapOf<String, List<LeaderboardPlayer>>()

            leaderboards.live_bullet?.let { players ->
                result["Bullet"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username ?: "Unknown",
                        title = p.title,
                        rating = p.score,
                        server = ChessServer.CHESS_COM
                    )
                }
            }
            leaderboards.live_blitz?.let { players ->
                result["Blitz"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username ?: "Unknown",
                        title = p.title,
                        rating = p.score,
                        server = ChessServer.CHESS_COM
                    )
                }
            }
            leaderboards.live_rapid?.let { players ->
                result["Rapid"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username ?: "Unknown",
                        title = p.title,
                        rating = p.score,
                        server = ChessServer.CHESS_COM
                    )
                }
            }
            leaderboards.daily?.let { players ->
                result["Daily"] = players.take(10).map { p ->
                    LeaderboardPlayer(
                        username = p.username ?: "Unknown",
                        title = p.title,
                        rating = p.score,
                        server = ChessServer.CHESS_COM
                    )
                }
            }

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get Chess.com daily puzzle
     */
    suspend fun getChessComDailyPuzzle(): Result<PuzzleInfo> = withContext(Dispatchers.IO) {
        try {
            val response = chessComApi.getDailyPuzzle()

            if (!response.isSuccessful) {
                return@withContext Result.Error("Failed to fetch daily puzzle")
            }

            val puzzle = response.body()
                ?: return@withContext Result.Error("No puzzle data received")

            val fen = puzzle.fen
                ?: return@withContext Result.Error("Puzzle has no FEN position")

            Result.Success(PuzzleInfo(
                title = puzzle.title ?: "Daily Puzzle",
                fen = fen,
                pgn = puzzle.pgn,
                url = puzzle.url,
                publishTime = puzzle.publish_time?.times(1000),
                server = ChessServer.CHESS_COM
            ))
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Get opening explorer data for a position.
     */
    suspend fun getOpeningExplorer(fen: String): Result<OpeningExplorerResponse> = withContext(Dispatchers.IO) {
        try {
            val response = openingExplorerApi.getLichessOpeningExplorer(fen)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.Success(body)
                } else {
                    Result.Error("Empty response body for opening data")
                }
            } else {
                Result.Error("Failed to fetch opening data: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    companion object {
        private val openingExplorerApi: OpeningExplorerApi by lazy {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            retrofit2.Retrofit.Builder()
                .baseUrl("https://explorer.lichess.org/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(OpeningExplorerApi::class.java)
        }
    }
}

/**
 * Unified leaderboard player from either Lichess or Chess.com
 */
data class LeaderboardPlayer(
    val username: String,
    val title: String?,
    val rating: Int?,
    val server: ChessServer
)

/**
 * Tournament info from either Lichess or Chess.com
 */
data class TournamentInfo(
    val id: String,
    val name: String,
    val status: String,
    val playerCount: Int,
    val startsAt: Long?,
    val variant: String,
    val timeControl: String,
    val server: ChessServer
)

/**
 * Broadcast round info
 */
data class BroadcastRoundInfo(
    val id: String,
    val name: String,
    val ongoing: Boolean,
    val finished: Boolean,
    val startsAt: Long?
)

/**
 * Broadcast info from Lichess
 */
data class BroadcastInfo(
    val id: String,
    val name: String,
    val description: String?,
    val rounds: List<BroadcastRoundInfo>,
    val ongoing: Boolean,
    val startsAt: Long?,
    val server: ChessServer
)

/**
 * TV channel info from Lichess
 */
data class TvChannelInfo(
    val channelName: String,
    val gameId: String,
    val playerName: String,
    val playerTitle: String?,
    val rating: Int?,
    val server: ChessServer
)

/**
 * Puzzle info from Chess.com
 */
data class PuzzleInfo(
    val title: String,
    val fen: String,
    val pgn: String?,
    val url: String?,
    val publishTime: Long?,
    val server: ChessServer
)

/**
 * Streamer info from Chess.com
 */
data class StreamerInfo(
    val username: String,
    val isLive: Boolean,
    val twitchUrl: String?,
    val profileUrl: String?,
    val server: ChessServer
)

/**
 * Stream game info (first line of /api/stream/game/{id})
 */
data class StreamGameInfo(
    val id: String?,
    val variant: StreamVariant?,
    val speed: String?,
    val perf: String?,
    val rated: Boolean?,
    val createdAt: Long?,
    val players: StreamPlayers?
)

data class StreamVariant(
    val key: String?,
    val name: String?
)

data class StreamPlayers(
    val white: StreamPlayer?,
    val black: StreamPlayer?
)

data class StreamPlayer(
    val user: StreamUser?,
    val rating: Int?
)

data class StreamUser(
    val name: String?,
    val id: String?,
    val title: String?
)

/**
 * Stream move data (subsequent lines of /api/stream/game/{id})
 */
data class StreamMoveData(
    val fen: String?,
    val lm: String?,  // Last move in UCI format
    val wc: Int?,     // White clock in seconds
    val bc: Int?      // Black clock in seconds
)

/**
 * Event emitted during live game streaming
 */
sealed class LiveGameEvent {
    data class GameInfo(val info: StreamGameInfo) : LiveGameEvent()
    data class Move(val data: StreamMoveData) : LiveGameEvent()
    data class GameEnd(val winner: String?, val status: String?) : LiveGameEvent()
    data class Error(val message: String) : LiveGameEvent()
    object Connected : LiveGameEvent()
    object Disconnected : LiveGameEvent()
}

/**
 * Game end data from stream
 */
data class GameEndData(
    val winner: String?,
    val status: String?
)
