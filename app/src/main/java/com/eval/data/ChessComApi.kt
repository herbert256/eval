package com.eval.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Chess.com API response models
 */

// User profile models
data class ChessComUserProfile(
    val player_id: Int?,
    val url: String?,
    val name: String?,
    val username: String?,
    val title: String?,
    val followers: Int?,
    val country: String?,
    val location: String?,
    val last_online: Long?,
    val joined: Long?,
    val status: String?,
    val is_streamer: Boolean?,
    val verified: Boolean?,
    val league: String?,
    val streaming_platforms: List<String>?
)

data class ChessComUserStats(
    val chess_daily: ChessComStatCategory?,
    val chess_rapid: ChessComStatCategory?,
    val chess_bullet: ChessComStatCategory?,
    val chess_blitz: ChessComStatCategory?,
    val tactics: ChessComTactics?,
    val puzzle_rush: ChessComPuzzleRush?
)

data class ChessComStatCategory(
    val last: ChessComRating?,
    val best: ChessComRating?,
    val record: ChessComRecord?
)

data class ChessComRating(
    val rating: Int?,
    val date: Long?,
    val rd: Int?
)

data class ChessComRecord(
    val win: Int?,
    val loss: Int?,
    val draw: Int?,
    val time_per_move: Int?,
    val timeout_percent: Float?
)

data class ChessComTactics(
    val highest: ChessComRating?,
    val lowest: ChessComRating?
)

data class ChessComPuzzleRush(
    val best: ChessComPuzzleRushBest?
)

data class ChessComPuzzleRushBest(
    val total_attempts: Int?,
    val score: Int?
)

data class ChessComArchives(
    val archives: List<String>
)

data class ChessComGamesResponse(
    val games: List<ChessComGame>
)

data class ChessComGame(
    val url: String,
    val pgn: String?,
    val time_control: String?,
    val end_time: Long?,
    val rated: Boolean?,
    val tcn: String?,
    val uuid: String?,
    val initial_setup: String?,
    val fen: String?,
    val time_class: String?,
    val rules: String?,
    val white: ChessComPlayer,
    val black: ChessComPlayer
)

data class ChessComPlayer(
    val rating: Int?,
    val result: String?,
    val username: String,
    val uuid: String?
)

/**
 * Chess.com leaderboard data
 */
data class ChessComLeaderboards(
    val daily: List<ChessComLeaderboardPlayer>?,
    val daily960: List<ChessComLeaderboardPlayer>?,
    val live_rapid: List<ChessComLeaderboardPlayer>?,
    val live_blitz: List<ChessComLeaderboardPlayer>?,
    val live_bullet: List<ChessComLeaderboardPlayer>?,
    val live_bughouse: List<ChessComLeaderboardPlayer>?,
    val live_blitz960: List<ChessComLeaderboardPlayer>?,
    val live_threecheck: List<ChessComLeaderboardPlayer>?,
    val live_crazyhouse: List<ChessComLeaderboardPlayer>?,
    val live_kingofthehill: List<ChessComLeaderboardPlayer>?,
    val tactics: List<ChessComLeaderboardPlayer>?,
    val rush: List<ChessComLeaderboardPlayer>?,
    val battle: List<ChessComLeaderboardPlayer>?
)

data class ChessComLeaderboardPlayer(
    val player_id: Int?,
    val url: String?,
    val username: String?,
    val score: Int?,
    val rank: Int?,
    val country: String?,
    val title: String?,
    val name: String?,
    val status: String?,
    val avatar: String?,
    val trend_score: ChessComTrendScore?,
    val trend_rank: ChessComTrendRank?,
    val flair_code: String?,
    val win_count: Int?,
    val loss_count: Int?,
    val draw_count: Int?
)

data class ChessComTrendScore(
    val direction: Int?,
    val delta: Int?
)

data class ChessComTrendRank(
    val direction: Int?,
    val delta: Int?
)

/**
 * Chess.com daily puzzle
 */
data class ChessComDailyPuzzle(
    val title: String?,
    val url: String?,
    val publish_time: Long?,
    val fen: String?,
    val pgn: String?,
    val image: String?
)

/**
 * Chess.com streamers
 */
data class ChessComStreamers(
    val streamers: List<ChessComStreamer>?
)

data class ChessComStreamer(
    val username: String?,
    val avatar: String?,
    val twitch_url: String?,
    val url: String?,
    val is_live: Boolean?,
    val is_community_streamer: Boolean?
)

/**
 * Chess.com club data
 */
data class ChessComClubMatches(
    val finished: List<ChessComClubMatch>?,
    val in_progress: List<ChessComClubMatch>?,
    val registered: List<ChessComClubMatch>?
)

data class ChessComClubMatch(
    val name: String?,
    @com.google.gson.annotations.SerializedName("@id")
    val id: String?,
    val opponent: String?,
    val result: String?,
    val start_time: Long?,
    val time_class: String?
)

data class ChessComMatchDetails(
    val name: String?,
    val url: String?,
    val start_time: Long?,
    val end_time: Long?,
    val status: String?,
    val boards: Int?,
    val settings: ChessComMatchSettings?,
    val teams: ChessComMatchTeams?
)

data class ChessComMatchSettings(
    val time_class: String?,
    val time_control: String?,
    val rules: String?
)

data class ChessComMatchTeams(
    val team1: ChessComMatchTeam?,
    val team2: ChessComMatchTeam?
)

data class ChessComMatchTeam(
    val name: String?,
    val url: String?,
    val score: Float?,
    val result: String?,
    val players: List<ChessComMatchPlayer>?
)

data class ChessComMatchPlayer(
    val username: String?,
    val board: String?,
    val rating: Int?,
    val played_as_white: String?,
    val played_as_black: String?
)

data class ChessComMatchBoard(
    val board_scores: ChessComBoardScores?,
    val games: List<ChessComGame>?
)

data class ChessComBoardScores(
    val player1: Float?,
    val player2: Float?
)

interface ChessComApi {
    @GET("pub/player/{username}")
    suspend fun getUser(
        @Path("username") username: String
    ): Response<ChessComUserProfile>

    @GET("pub/player/{username}/stats")
    suspend fun getUserStats(
        @Path("username") username: String
    ): Response<ChessComUserStats>

    @GET("pub/player/{username}/games/archives")
    suspend fun getArchives(
        @Path("username") username: String
    ): Response<ChessComArchives>

    @GET("pub/player/{username}/games/{year}/{month}")
    suspend fun getGamesForMonth(
        @Path("username") username: String,
        @Path("year") year: Int,
        @Path("month") month: String
    ): Response<ChessComGamesResponse>

    @GET("pub/leaderboards")
    suspend fun getLeaderboards(): Response<ChessComLeaderboards>

    // Daily puzzle
    @GET("pub/puzzle")
    suspend fun getDailyPuzzle(): Response<ChessComDailyPuzzle>

    @GET("pub/puzzle/random")
    suspend fun getRandomPuzzle(): Response<ChessComDailyPuzzle>

    // Streamers
    @GET("pub/streamers")
    suspend fun getStreamers(): Response<ChessComStreamers>

    // Club endpoints
    @GET("pub/club/{urlId}/matches")
    suspend fun getClubMatches(
        @Path("urlId") clubUrlId: String
    ): Response<ChessComClubMatches>

    @GET("pub/match/{matchId}")
    suspend fun getMatch(
        @Path("matchId") matchId: String
    ): Response<ChessComMatchDetails>

    @GET("pub/match/{matchId}/{boardNum}")
    suspend fun getMatchBoard(
        @Path("matchId") matchId: String,
        @Path("boardNum") boardNum: Int
    ): Response<ChessComMatchBoard>

    companion object {
        private const val BASE_URL = "https://api.chess.com/"

        fun create(): ChessComApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Eval/1.0")
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(TracingInterceptor())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChessComApi::class.java)
        }
    }
}
