package com.chessreplay.data

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

interface ChessComApi {
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
                        .header("User-Agent", "ChessReplay/1.0")
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
