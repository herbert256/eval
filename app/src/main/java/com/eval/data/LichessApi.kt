package com.eval.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Lichess user profile data
 */
data class LichessUserProfile(
    val id: String,
    val username: String,
    val title: String?,
    val online: Boolean?,
    val playing: String?,
    val streaming: Boolean?,
    val createdAt: Long?,
    val seenAt: Long?,
    val profile: LichessProfileInfo?,
    val perfs: Map<String, LichessPerf>?,
    val count: LichessCount?,
    val playTime: LichessPlayTime?,
    val url: String?
)

data class LichessProfileInfo(
    val country: String?,
    val location: String?,
    val bio: String?,
    val firstName: String?,
    val lastName: String?,
    val fideRating: Int?,
    val uscfRating: Int?,
    val ecfRating: Int?,
    val links: String?
)

data class LichessPerf(
    val games: Int?,
    val rating: Int?,
    val rd: Int?,
    val prog: Int?,
    val prov: Boolean?
)

data class LichessCount(
    val all: Int?,
    val rated: Int?,
    val ai: Int?,
    val draw: Int?,
    val drawH: Int?,
    val loss: Int?,
    val lossH: Int?,
    val win: Int?,
    val winH: Int?,
    val bookmark: Int?,
    val playing: Int?,
    val import: Int?,
    val me: Int?
)

data class LichessPlayTime(
    val total: Long?,
    val tv: Long?
)

interface LichessApi {
    @GET("api/user/{username}")
    suspend fun getUser(
        @Path("username") username: String
    ): Response<LichessUserProfile>

    @GET("api/games/user/{username}")
    @Headers("Accept: application/x-ndjson")
    suspend fun getGames(
        @Path("username") username: String,
        @Query("max") max: Int = 10,
        @Query("pgnInJson") pgnInJson: Boolean = true,
        @Query("clocks") clocks: Boolean = true
    ): Response<String>

    companion object {
        private const val BASE_URL = "https://lichess.org/"

        fun create(): LichessApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LichessApi::class.java)
        }
    }
}
