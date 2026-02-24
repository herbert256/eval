package com.eval.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Chess.com player profile
 */
data class ChessComProfile(
    val player_id: Int?,
    val url: String?,
    val username: String?,
    val title: String?,
    val status: String?,
    val name: String?,
    val location: String?,
    val country: String?,
    val joined: Long?,
    val last_online: Long?,
    val followers: Int?,
    val is_streamer: Boolean?
)

/**
 * Chess.com player stats
 */
data class ChessComStats(
    val chess_daily: ChessComStatEntry?,
    val chess_rapid: ChessComStatEntry?,
    val chess_bullet: ChessComStatEntry?,
    val chess_blitz: ChessComStatEntry?
)

data class ChessComStatEntry(
    val last: ChessComRating?,
    val record: ChessComRecord?
)

data class ChessComRating(
    val rating: Int?,
    val date: Long?
)

data class ChessComRecord(
    val win: Int?,
    val loss: Int?,
    val draw: Int?
)

/**
 * Chess.com archives response (list of monthly archive URLs)
 */
data class ChessComArchivesResponse(
    val archives: List<String>?
)

/**
 * Chess.com games response for a month
 */
data class ChessComGamesResponse(
    val games: List<ChessComGame>?
)

data class ChessComGame(
    val url: String?,
    val pgn: String?,
    val time_control: String?,
    val end_time: Long?,
    val rated: Boolean?,
    val time_class: String?,
    val rules: String?,
    val white: ChessComPlayer?,
    val black: ChessComPlayer?
)

data class ChessComPlayer(
    val rating: Int?,
    val result: String?,
    val username: String?
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
 * Chess.com leaderboards
 */
data class ChessComLeaderboards(
    val daily: List<ChessComLeaderboardEntry>?,
    val live_rapid: List<ChessComLeaderboardEntry>?,
    val live_blitz: List<ChessComLeaderboardEntry>?,
    val live_bullet: List<ChessComLeaderboardEntry>?
)

data class ChessComLeaderboardEntry(
    val player_id: Int?,
    val url: String?,
    val username: String?,
    val title: String?,
    val score: Int?,
    val rank: Int?
)

interface ChessComApi {
    @GET("pub/player/{username}")
    suspend fun getProfile(
        @Path("username") username: String
    ): Response<ChessComProfile>

    @GET("pub/player/{username}/stats")
    suspend fun getStats(
        @Path("username") username: String
    ): Response<ChessComStats>

    @GET("pub/player/{username}/games/archives")
    suspend fun getArchives(
        @Path("username") username: String
    ): Response<ChessComArchivesResponse>

    /**
     * Get games for a specific month. Pass the full URL from archives list.
     */
    @GET
    suspend fun getMonthlyGames(
        @Url url: String
    ): Response<ChessComGamesResponse>

    @GET("pub/puzzle")
    suspend fun getDailyPuzzle(): Response<ChessComDailyPuzzle>

    @GET("pub/leaderboards")
    suspend fun getLeaderboards(): Response<ChessComLeaderboards>

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
                        .header("User-Agent", "Eval Chess Analysis App")
                        .build()
                    chain.proceed(request)
                }
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
