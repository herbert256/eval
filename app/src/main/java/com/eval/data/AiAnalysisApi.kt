package com.eval.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Enum representing the supported AI services for chess position analysis.
 */
enum class AiService(val displayName: String, val baseUrl: String) {
    CHATGPT("ChatGPT", "https://api.openai.com/"),
    CLAUDE("Claude", "https://api.anthropic.com/"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/"),
    GROK("Grok", "https://api.x.ai/"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/")
}

// OpenAI / ChatGPT models
data class OpenAiMessage(
    val role: String,
    val content: String
)

data class OpenAiRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<OpenAiMessage>,
    val max_tokens: Int = 1024
)

data class OpenAiChoice(
    val message: OpenAiMessage,
    val index: Int
)

data class OpenAiResponse(
    val id: String?,
    val choices: List<OpenAiChoice>?,
    val error: OpenAiError?
)

data class OpenAiError(
    val message: String?,
    val type: String?
)

// Anthropic / Claude models
data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    val max_tokens: Int = 1024,
    val messages: List<ClaudeMessage>
)

data class ClaudeContentBlock(
    val type: String,
    val text: String?
)

data class ClaudeResponse(
    val id: String?,
    val content: List<ClaudeContentBlock>?,
    val error: ClaudeError?
)

data class ClaudeError(
    val type: String?,
    val message: String?
)

// Google / Gemini models
data class GeminiPart(
    val text: String
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiCandidate(
    val content: GeminiContent?
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiError?
)

data class GeminiError(
    val code: Int?,
    val message: String?,
    val status: String?
)

// xAI / Grok models (uses OpenAI-compatible format)
data class GrokRequest(
    val model: String = "grok-3-mini",
    val messages: List<OpenAiMessage>,
    val max_tokens: Int = 1024
)

// DeepSeek models (uses OpenAI-compatible format)
data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<OpenAiMessage>,
    val max_tokens: Int = 1024
)

/**
 * Retrofit interface for OpenAI / ChatGPT API.
 */
interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiRequest
    ): Response<OpenAiResponse>

    @retrofit2.http.GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<OpenAiModelsResponse>
}

/**
 * Retrofit interface for Anthropic / Claude API.
 */
interface ClaudeApi {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: ClaudeRequest
    ): Response<ClaudeResponse>
}

// Response for listing Gemini models
data class GeminiModelsResponse(
    val models: List<GeminiModel>?
)

data class GeminiModel(
    val name: String?,
    val displayName: String?,
    val supportedGenerationMethods: List<String>?
)

/**
 * Retrofit interface for Google / Gemini API.
 */
interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    @retrofit2.http.GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): Response<GeminiModelsResponse>
}

// Response for listing models (OpenAI-compatible format, used by Grok)
data class OpenAiModelsResponse(
    val data: List<OpenAiModel>?
)

data class OpenAiModel(
    val id: String?,
    val owned_by: String?
)

/**
 * Retrofit interface for xAI / Grok API (OpenAI-compatible).
 */
interface GrokApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: GrokRequest
    ): Response<OpenAiResponse>

    @retrofit2.http.GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<OpenAiModelsResponse>
}

/**
 * Retrofit interface for DeepSeek API (OpenAI-compatible).
 */
interface DeepSeekApi {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: DeepSeekRequest
    ): Response<OpenAiResponse>

    @retrofit2.http.GET("models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<OpenAiModelsResponse>
}

/**
 * Factory for creating API instances.
 */
object AiApiFactory {
    private val retrofitCache = mutableMapOf<String, Retrofit>()

    // OkHttpClient with extended timeouts for AI API calls
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getRetrofit(baseUrl: String): Retrofit {
        return retrofitCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    fun createOpenAiApi(): OpenAiApi {
        return getRetrofit(AiService.CHATGPT.baseUrl).create(OpenAiApi::class.java)
    }

    fun createClaudeApi(): ClaudeApi {
        return getRetrofit(AiService.CLAUDE.baseUrl).create(ClaudeApi::class.java)
    }

    fun createGeminiApi(): GeminiApi {
        return getRetrofit(AiService.GEMINI.baseUrl).create(GeminiApi::class.java)
    }

    fun createGrokApi(): GrokApi {
        return getRetrofit(AiService.GROK.baseUrl).create(GrokApi::class.java)
    }

    fun createDeepSeekApi(): DeepSeekApi {
        return getRetrofit(AiService.DEEPSEEK.baseUrl).create(DeepSeekApi::class.java)
    }
}
