package com.chessreplay.data

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

interface LichessApi {
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
