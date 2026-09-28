package com.eval.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.eval.chess.PgnParser
import kotlinx.coroutines.CancellationException
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
    LOCAL
}

/**
 * Player information from Lichess
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
    private val lichessApi: LichessApi = LichessApi.create()
) {
    private val gson = Gson()

    private inline fun <reified T> parseNdjson(body: String): List<T> {
        var failed = 0
        val parsed = body.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    gson.fromJson(line, T::class.java)
                } catch (e: Exception) {
                    failed++
                    // Log the first failure (with a snippet) so dropped lines don't
                    // disappear silently — they used to return null with zero trace.
                    if (failed == 1) {
                        android.util.Log.w(
                            "ChessRepository",
                            "NDJSON parse failed for ${T::class.simpleName}: ${e.message}; line=${line.take(200)}"
                        )
                    }
                    null
                }
            }
        if (failed > 1) {
            android.util.Log.w("ChessRepository", "NDJSON: dropped $failed malformed lines for ${T::class.simpleName}")
        }
        return parsed
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

    /** Readable message for a failed request; users don't see exception class names. */
    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "No internet connection. Check your connection and try again."
        is java.io.InterruptedIOException -> "Lichess did not respond in time. Please try again."
        is com.google.gson.JsonParseException, is IllegalStateException ->
            "Lichess sent data that Eval could not read. Please try again later."
        is IllegalArgumentException -> "That name is not valid on Lichess."
        is java.io.IOException -> "The connection to Lichess failed. Please try again."
        else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Readable message for an HTTP error status when fetching [what]. */
    private fun httpError(code: Int, what: String): String = when (code) {
        404 -> "Lichess could not find $what."
        429 -> "Lichess is limiting requests. Please try again in a minute."
        in 500..599 -> "Lichess is having problems right now (HTTP $code). Please try again later."
        else -> "Could not fetch $what from Lichess (HTTP $code)."
    }

    /**
     * Get recent games from Lichess.org
     */
    suspend fun getLichessGames(
        username: String,
        maxGames: Int,
        until: Long? = null
    ): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getGames(username, max = maxGames, until = until)

            if (!response.isSuccessful) {
                return@withContext when (response.code()) {
                    404 -> Result.Error("User not found on Lichess")
                    else -> Result.Error(httpError(response.code(), "games for this player"))
                }
            }

            val body = response.body()
            processNdjsonResponse<LichessGame>(body, "No games found for this user on Lichess")
        } catch (e: Exception) {
            Result.Error(friendlyError(e))
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
                    else -> Result.Error(httpError(response.code(), "this player"))
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
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Get player info from the appropriate server
     */
    suspend fun getPlayerInfo(username: String, server: ChessServer): Result<PlayerInfo> {
        return when (server) {
            ChessServer.LICHESS -> getLichessPlayerInfo(username)
            ChessServer.LOCAL -> Result.Error("Online profiles are unavailable for local games")
        }
    }

    /**
     * Get Lichess leaderboard (top players for each format)
     */
    suspend fun getLichessLeaderboard(): Result<Map<String, List<LeaderboardPlayer>>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getLeaderboard()

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "Lichess leaderboard"))
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
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Get Lichess live streamers
     */
    suspend fun getLichessStreamers(): Result<List<StreamerInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getLiveStreamers()

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "Lichess streamers"))
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
                    val twitch = obj.get("twitch")?.asJsonObject
                    val twitchUrl = twitch?.get("url")?.asString
                    streamers.add(StreamerInfo(
                        username = username,
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
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Get Lichess current tournaments
     */
    suspend fun getLichessTournaments(): Result<List<TournamentInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getTournaments()

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "Lichess tournaments"))
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
            Result.Error(friendlyError(e))
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
                return@withContext Result.Error(httpError(response.code(), "tournament games"))
            }

            val body = response.body()
            processNdjsonResponse<LichessGame>(body, "No games found in this tournament")
        } catch (e: Exception) {
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Get Lichess current broadcasts (official events)
     */
    suspend fun getLichessBroadcasts(): Result<List<BroadcastInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getBroadcasts()

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "Lichess broadcasts"))
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
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Get games from a Lichess broadcast round
     */
    suspend fun getLichessBroadcastGames(roundId: String): Result<List<LichessGame>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getBroadcastRoundPgn(roundId)

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "broadcast games"))
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No games found in this broadcast")
            }

            val games = PgnParser.splitGames(body)
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

            Result.Success(withUniqueIds(games))
        } catch (e: Exception) {
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Convert a PGN string to LichessGame format
     */
    private fun convertPgnToLichessGame(pgn: String): LichessGame? {
        if (pgn.isBlank()) return null

        val headers = PgnParser.parseHeaders(pgn)
        val whiteName = headers["White"] ?: "White"
        val blackName = headers["Black"] ?: "Black"
        val result = headers["Result"] ?: PgnParser.parseResult(pgn)

        val winner = when (result) {
            "1-0" -> "white"
            "0-1" -> "black"
            else -> null
        }

        val gameId = headers["GameURL"]?.trimEnd('/')?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()

        return LichessGame(
            id = gameId,
            rated = false,
            variant = "standard",
            speed = "classical",
            perf = "classical",
            status = if (result == "1/2-1/2") "draw" else result ?: "unknown",
            winner = winner,
            players = Players(
                white = Player(
                    user = User(name = whiteName, id = whiteName.lowercase().replace(" ", "_")),
                    rating = headers["WhiteElo"]?.toIntOrNull(),
                    aiLevel = null
                ),
                black = Player(
                    user = User(name = blackName, id = blackName.lowercase().replace(" ", "_")),
                    rating = headers["BlackElo"]?.toIntOrNull(),
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

    /** Merged databases can repeat a GameURL; list keys must stay unique. */
    private fun withUniqueIds(games: List<LichessGame>): List<LichessGame> {
        val seenIds = HashSet<String>()
        return games.map { game ->
            if (seenIds.add(game.id)) game
            else game.copy(id = "${game.id}-${java.util.UUID.randomUUID()}").also { seenIds.add(it.id) }
        }
    }

    /**
     * Parse multiple games from a PGN file content.
     * Uses PGN syntax rather than requiring a particular blank-line separator.
     */
    fun parseGamesFromPgnContent(pgnContent: String): Result<List<LichessGame>> {
        if (pgnContent.isBlank()) {
            return Result.Error("PGN file is empty")
        }

        val gameStrings = PgnParser.splitGames(pgnContent)

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

        return Result.Success(withUniqueIds(games))
    }

    /**
     * Get Lichess TV channels (current top games)
     */
    suspend fun getLichessTvChannels(): Result<List<TvChannelInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getTvChannels()

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "Lichess TV channels"))
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

            if (com.eval.BuildConfig.DEBUG) android.util.Log.d("ChessRepository", "getLichessTvChannels: returning ${result.size} channels")
            Result.Success(result)
        } catch (e: Exception) {
            android.util.Log.e("ChessRepository", "getLichessTvChannels: Exception: ${e.message}", e)
            Result.Error(friendlyError(e))
        }
    }

    /**
     * Get a single Lichess game by ID
     */
    suspend fun getLichessGame(gameId: String): Result<LichessGame> = withContext(Dispatchers.IO) {
        try {
            val response = lichessApi.getGame(gameId)

            if (!response.isSuccessful) {
                return@withContext Result.Error(httpError(response.code(), "game"))
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                return@withContext Result.Error("No game data received")
            }

            val game = gson.fromJson(body, LichessGame::class.java)
            Result.Success(game)
        } catch (e: Exception) {
            Result.Error(friendlyError(e))
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
                return@withContext Result.Error(httpError(response.code(), "the live game"))
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

            val ending = lines.asReversed().firstNotNullOfOrNull { line ->
                try {
                    gameEndFromJson(JsonParser().parse(line).asJsonObject)
                } catch (_: Exception) { null }
            }
            val result = when {
                ending?.winner == "white" -> "1-0"
                ending?.winner == "black" -> "0-1"
                ending?.status in setOf("draw", "stalemate") -> "1/2-1/2"
                else -> "*"
            }
            val variantKey = gameInfo.variant?.key ?: "standard"
            if (variantKey != "standard" && variantKey != "fromPosition") {
                return@withContext Result.Error("${gameInfo.variant?.name ?: variantKey} games are not supported; Eval analyses standard chess only")
            }
            val pgn = buildPgnFromMoves(moves, gameInfo, result)
                ?: return@withContext Result.Error("Could not replay the streamed moves")

            // Create LichessGame from streamed data
            val game = LichessGame(
                id = gameInfo.id ?: gameId,
                rated = gameInfo.rated ?: false,
                variant = gameInfo.variant?.key ?: "standard",
                speed = gameInfo.speed ?: "rapid",
                perf = gameInfo.perf,
                status = ending?.status ?: "started",
                winner = ending?.winner,
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
            Result.Error(friendlyError(e))
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
            val response = lichessApi.streamGame(gameId)

            if (!response.isSuccessful) {
                emit(LiveGameEvent.Error(httpError(response.code(), "the live game")))
                emit(LiveGameEvent.Disconnected)
                return@flow
            }

            val responseBody = response.body()
            if (responseBody == null) {
                emit(LiveGameEvent.Error("No stream data"))
                emit(LiveGameEvent.Disconnected)
                return@flow
            }
            emit(LiveGameEvent.Connected)

            val reader = responseBody.source()
            var isFirstLine = true

            try {
                while (!reader.exhausted()) {
                    val line = reader.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    // The terminal message is another game description, with
                    // status.name. Gson also accepts it as an empty move object,
                    // so checking for completion only on a parse error loses it.
                    val event = try {
                        val json = JsonParser().parse(line).asJsonObject
                        val ending = gameEndFromJson(json)
                        when {
                            ending != null -> LiveGameEvent.GameEnd(ending.winner, ending.status)
                            isFirstLine -> LiveGameEvent.GameInfo(gson.fromJson(json, StreamGameInfo::class.java))
                            json.get("lm")?.isJsonNull == false -> LiveGameEvent.Move(gson.fromJson(json, StreamMoveData::class.java))
                            else -> null // Initial position and keep-alive messages have no move.
                        }
                    } catch (e: Exception) {
                        LiveGameEvent.Error("Failed to parse stream message")
                    }
                    isFirstLine = false
                    // Keep emit outside the parse catch so cancellation is never
                    // reinterpreted as invalid JSON or a stream error.
                    if (event != null) emit(event)
                    if (event is LiveGameEvent.GameEnd) break
                }
            } catch (e: java.io.IOException) {
                // Stream closed, possibly game ended
            } finally {
                responseBody.close()
            }
            emit(LiveGameEvent.Disconnected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(LiveGameEvent.Error(e.message ?: "Stream error"))
            emit(LiveGameEvent.Disconnected)
        }
    }.flowOn(Dispatchers.IO)

    private fun gameEndFromJson(json: JsonObject): GameEndData? {
        val value = json.get("status")?.takeUnless { it.isJsonNull } ?: return null
        val status = if (value.isJsonObject) value.asJsonObject.get("name")?.asString else value.asString
        if (status == null || status in setOf("created", "started")) return null
        val winner = json.get("winner")?.takeUnless { it.isJsonNull }?.asString
        return GameEndData(winner, status)
    }

    /**
     * Build a minimal PGN from UCI moves
     */
    /**
     * Rebuild a PGN from streamed UCI moves, starting from the stream's initial position.
     * Returns null when a move cannot be replayed, so callers never load a silently truncated game.
     */
    private fun buildPgnFromMoves(moves: List<String>, gameInfo: StreamGameInfo, result: String = "*"): String? {
        val whiteName = gameInfo.players?.white?.user?.name ?: "White"
        val blackName = gameInfo.players?.black?.user?.name ?: "Black"
        val whiteRating = gameInfo.players?.white?.rating
        val blackRating = gameInfo.players?.black?.rating

        val board = com.eval.chess.ChessBoard()
        val startFen = gameInfo.initialFen?.takeIf { it.isNotBlank() && it != "startpos" && it != board.getFen() }
        if (startFen != null && !board.setFen(startFen)) return null

        val headers = buildString {
            appendLine("[Event \"Live Game\"]")
            appendLine("[Site \"lichess.org\"]")
            appendLine("[White \"$whiteName\"]")
            appendLine("[Black \"$blackName\"]")
            whiteRating?.let { appendLine("[WhiteElo \"$it\"]") }
            blackRating?.let { appendLine("[BlackElo \"$it\"]") }
            appendLine("[Result \"$result\"]")
            if (startFen != null) {
                appendLine("[SetUp \"1\"]")
                appendLine("[FEN \"$startFen\"]")
            }
            appendLine()
        }

        val firstMoveNumber = board.getFen().substringAfterLast(' ').toIntOrNull() ?: 1
        val blackStarts = board.getTurn() == com.eval.chess.PieceColor.BLACK
        val moveText = buildString {
            moves.forEachIndexed { index, uci ->
                val san = board.sanForMove(uci) ?: return null
                if (!board.makeUciMove(uci)) return null
                val ply = index + if (blackStarts) 1 else 0
                val number = firstMoveNumber + ply / 2
                when {
                    ply % 2 == 0 -> append("$number. ")
                    index == 0 -> append("$number... ")
                }
                append("$san ")
            }
            append(result)
        }

        return headers + moveText
    }

    /**
     * Get opening explorer data for a position.
     *
     * Retrofit's @Query URL-encodes the FEN automatically (space, slash, dash),
     * so no manual encoding is needed here. We trim in case the caller passes a
     * FEN with stray whitespace and reject an empty string up front — otherwise
     * the API returns a 400 that looks like an opaque network failure.
     */
    suspend fun getOpeningExplorer(fen: String, token: String = ""): Result<OpeningExplorerResponse> = withContext(Dispatchers.IO) {
        val trimmed = fen.trim()
        if (trimmed.isEmpty()) return@withContext Result.Error("FEN is empty")
        if (token.isBlank()) return@withContext Result.Error("Opening statistics need a Lichess access token (Settings > General).")
        try {
            val response = openingExplorerApi.getLichessOpeningExplorer(trimmed, "Bearer $token")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.Success(body)
                } else {
                    Result.Error("Empty response body for opening data")
                }
            } else {
                Result.Error(when (response.code()) {
                    401, 403 -> com.eval.ui.OpeningExplorerLoader.TOKEN_REJECTED
                    429 -> "Lichess opening statistics are temporarily rate limited. Try again later."
                    else -> "Could not load opening statistics (HTTP ${response.code()})."
                })
            }
        } catch (e: Exception) {
            Result.Error(friendlyError(e))
        }
    }

    companion object {
        private val openingExplorerApi: OpeningExplorerApi by lazy {
            val client = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "Eval/${com.eval.BuildConfig.VERSION_NAME} (Android; ${com.eval.BuildConfig.APPLICATION_ID})")
                        .build())
                }
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
 * Leaderboard player from Lichess
 */
data class LeaderboardPlayer(
    val username: String,
    val title: String?,
    val rating: Int?,
    val server: ChessServer
)

/**
 * Tournament information from Lichess
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
 * Streamer information from Lichess
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
    val initialFen: String?,
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
