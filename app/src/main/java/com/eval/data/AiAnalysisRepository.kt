package com.eval.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Token usage statistics from AI API response.
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

/**
 * Response from AI analysis containing either the analysis text or an error message.
 */
data class AiAnalysisResponse(
    val service: AiService,
    val analysis: String?,
    val error: String?,
    val tokenUsage: TokenUsage? = null
) {
    val isSuccess: Boolean get() = analysis != null && error == null
}

/**
 * Repository for making AI analysis requests to various AI services.
 */
class AiAnalysisRepository {
    private val openAiApi = AiApiFactory.createOpenAiApi()
    private val claudeApi = AiApiFactory.createClaudeApi()
    private val geminiApi = AiApiFactory.createGeminiApi()
    private val grokApi = AiApiFactory.createGrokApi()
    private val deepSeekApi = AiApiFactory.createDeepSeekApi()
    private val mistralApi = AiApiFactory.createMistralApi()
    private val perplexityApi = AiApiFactory.createPerplexityApi()

    /**
     * Builds the chess analysis prompt by replacing @FEN@ placeholder with actual FEN.
     */
    private fun buildChessPrompt(promptTemplate: String, fen: String): String {
        return promptTemplate.replace("@FEN@", fen)
    }

    /**
     * Builds the player analysis prompt by replacing @PLAYER@ and @SERVER@ placeholders.
     */
    private fun buildPlayerPrompt(promptTemplate: String, playerName: String, server: String?): String {
        var result = promptTemplate.replace("@PLAYER@", playerName)
        if (server != null) {
            result = result.replace("@SERVER@", server)
        }
        return result
    }

    /**
     * Analyzes a chess player using the specified AI service.
     *
     * @param service The AI service to use
     * @param playerName The name of the player to analyze
     * @param server The chess server (e.g., "lichess.org", "chess.com") or null for other players
     * @param apiKey The API key for the service
     * @param prompt The custom prompt template (use @PLAYER@ and @SERVER@ as placeholders)
     * @param chatGptModel The ChatGPT model to use
     * @param claudeModel The Claude model to use
     * @param geminiModel The Gemini model to use
     * @param grokModel The Grok model to use
     * @param deepSeekModel The DeepSeek model to use
     * @param mistralModel The Mistral model to use
     * @return AiAnalysisResponse containing either the analysis or an error
     */
    suspend fun analyzePlayer(
        service: AiService,
        playerName: String,
        server: String?,
        apiKey: String,
        prompt: String,
        chatGptModel: String = "gpt-4o-mini",
        claudeModel: String = "claude-sonnet-4-20250514",
        geminiModel: String = "gemini-2.0-flash",
        grokModel: String = "grok-3-mini",
        deepSeekModel: String = "deepseek-chat",
        mistralModel: String = "mistral-small-latest",
        perplexityModel: String = "sonar"
    ): AiAnalysisResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AiAnalysisResponse(
                service = service,
                analysis = null,
                error = "API key not configured for ${service.displayName}"
            )
        }

        val finalPrompt = buildPlayerPrompt(prompt, playerName, server)

        suspend fun makeApiCall(): AiAnalysisResponse {
            return when (service) {
                AiService.CHATGPT -> analyzeWithChatGpt(apiKey, finalPrompt, chatGptModel)
                AiService.CLAUDE -> analyzeWithClaude(apiKey, finalPrompt, claudeModel)
                AiService.GEMINI -> analyzeWithGemini(apiKey, finalPrompt, geminiModel)
                AiService.GROK -> analyzeWithGrok(apiKey, finalPrompt, grokModel)
                AiService.DEEPSEEK -> analyzeWithDeepSeek(apiKey, finalPrompt, deepSeekModel)
                AiService.MISTRAL -> analyzeWithMistral(apiKey, finalPrompt, mistralModel)
                AiService.PERPLEXITY -> analyzeWithPerplexity(apiKey, finalPrompt, perplexityModel)
                AiService.DUMMY -> analyzeWithDummy()
            }
        }

        // First attempt
        try {
            val result = makeApiCall()
            if (result.isSuccess) {
                return@withContext result
            }
            // API returned an error response, retry after delay
            android.util.Log.w("AiAnalysis", "${service.displayName} player analysis first attempt failed: ${result.error}, retrying...")
            delay(500)
            makeApiCall()
        } catch (e: Exception) {
            // Network/exception error, retry after delay
            android.util.Log.w("AiAnalysis", "${service.displayName} player analysis first attempt exception: ${e.message}, retrying...")
            try {
                delay(500)
                makeApiCall()
            } catch (e2: Exception) {
                AiAnalysisResponse(
                    service = service,
                    analysis = null,
                    error = "Failed after retry: ${e2.message}"
                )
            }
        }
    }

    /**
     * Analyzes a chess position using the specified AI service.
     *
     * @param service The AI service to use
     * @param fen The FEN string representing the chess position
     * @param apiKey The API key for the service
     * @param prompt The custom prompt template (use @FEN@ as placeholder)
     * @param chatGptModel The ChatGPT model to use
     * @param claudeModel The Claude model to use
     * @param geminiModel The Gemini model to use
     * @param grokModel The Grok model to use
     * @param deepSeekModel The DeepSeek model to use
     * @return AiAnalysisResponse containing either the analysis or an error
     */
    suspend fun analyzePosition(
        service: AiService,
        fen: String,
        apiKey: String,
        prompt: String,
        chatGptModel: String = "gpt-4o-mini",
        claudeModel: String = "claude-sonnet-4-20250514",
        geminiModel: String = "gemini-2.0-flash",
        grokModel: String = "grok-3-mini",
        deepSeekModel: String = "deepseek-chat",
        mistralModel: String = "mistral-small-latest",
        perplexityModel: String = "sonar"
    ): AiAnalysisResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AiAnalysisResponse(
                service = service,
                analysis = null,
                error = "API key not configured for ${service.displayName}"
            )
        }

        val finalPrompt = buildChessPrompt(prompt, fen)

        suspend fun makeApiCall(): AiAnalysisResponse {
            return when (service) {
                AiService.CHATGPT -> analyzeWithChatGpt(apiKey, finalPrompt, chatGptModel)
                AiService.CLAUDE -> analyzeWithClaude(apiKey, finalPrompt, claudeModel)
                AiService.GEMINI -> analyzeWithGemini(apiKey, finalPrompt, geminiModel)
                AiService.GROK -> analyzeWithGrok(apiKey, finalPrompt, grokModel)
                AiService.DEEPSEEK -> analyzeWithDeepSeek(apiKey, finalPrompt, deepSeekModel)
                AiService.MISTRAL -> analyzeWithMistral(apiKey, finalPrompt, mistralModel)
                AiService.PERPLEXITY -> analyzeWithPerplexity(apiKey, finalPrompt, perplexityModel)
                AiService.DUMMY -> analyzeWithDummy()
            }
        }

        // First attempt
        try {
            val result = makeApiCall()
            if (result.isSuccess) {
                return@withContext result
            }
            // API returned an error response, retry after delay
            android.util.Log.w("AiAnalysis", "${service.displayName} first attempt failed: ${result.error}, retrying...")
            delay(500)
            makeApiCall()
        } catch (e: Exception) {
            // Network/exception error, retry after delay
            android.util.Log.w("AiAnalysis", "${service.displayName} first attempt exception: ${e.message}, retrying...")
            try {
                delay(500)
                makeApiCall()
            } catch (e2: Exception) {
                AiAnalysisResponse(
                    service = service,
                    analysis = null,
                    error = "Network error after retry: ${e2.message ?: "Unknown error"}"
                )
            }
        }
    }

    private suspend fun analyzeWithChatGpt(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = OpenAiRequest(
            model = model,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )
        val response = openAiApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )

        return if (response.isSuccessful) {
            val body = response.body()
            val content = body?.choices?.firstOrNull()?.message?.content
            val usage = body?.usage?.let {
                TokenUsage(
                    inputTokens = it.prompt_tokens ?: 0,
                    outputTokens = it.completion_tokens ?: 0
                )
            }
            if (content != null) {
                AiAnalysisResponse(AiService.CHATGPT, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.CHATGPT, null, errorMsg)
            }
        } else {
            AiAnalysisResponse(AiService.CHATGPT, null, "API error: ${response.code()} ${response.message()}")
        }
    }

    private suspend fun analyzeWithClaude(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = ClaudeRequest(
            model = model,
            messages = listOf(ClaudeMessage(role = "user", content = prompt))
        )
        val response = claudeApi.createMessage(apiKey = apiKey, request = request)

        return if (response.isSuccessful) {
            val body = response.body()
            val content = body?.content?.firstOrNull { it.type == "text" }?.text
            val usage = body?.usage?.let {
                TokenUsage(
                    inputTokens = it.input_tokens ?: 0,
                    outputTokens = it.output_tokens ?: 0
                )
            }
            if (content != null) {
                AiAnalysisResponse(AiService.CLAUDE, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.CLAUDE, null, errorMsg)
            }
        } else {
            AiAnalysisResponse(AiService.CLAUDE, null, "API error: ${response.code()} ${response.message()}")
        }
    }

    private suspend fun analyzeWithGemini(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            )
        )

        android.util.Log.d("GeminiAPI", "Making request with model: $model, key length: ${apiKey.length}")

        val response = geminiApi.generateContent(model = model, apiKey = apiKey, request = request)

        android.util.Log.d("GeminiAPI", "Response code: ${response.code()}, message: ${response.message()}")

        return if (response.isSuccessful) {
            val body = response.body()
            val content = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val usage = body?.usageMetadata?.let {
                TokenUsage(
                    inputTokens = it.promptTokenCount ?: 0,
                    outputTokens = it.candidatesTokenCount ?: 0
                )
            }
            if (content != null) {
                AiAnalysisResponse(AiService.GEMINI, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.GEMINI, null, errorMsg)
            }
        } else {
            val errorBody = response.errorBody()?.string()
            android.util.Log.e("GeminiAPI", "Error body: $errorBody")
            AiAnalysisResponse(AiService.GEMINI, null, "API error: ${response.code()} ${response.message()} - $errorBody")
        }
    }

    private suspend fun analyzeWithGrok(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = GrokRequest(
            model = model,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )
        val response = grokApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )

        return if (response.isSuccessful) {
            val body = response.body()
            val content = body?.choices?.firstOrNull()?.message?.content
            val usage = body?.usage?.let {
                TokenUsage(
                    inputTokens = it.prompt_tokens ?: 0,
                    outputTokens = it.completion_tokens ?: 0
                )
            }
            if (content != null) {
                AiAnalysisResponse(AiService.GROK, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.GROK, null, errorMsg)
            }
        } else {
            AiAnalysisResponse(AiService.GROK, null, "API error: ${response.code()} ${response.message()}")
        }
    }

    private suspend fun analyzeWithDeepSeek(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = DeepSeekRequest(
            model = model,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )
        val response = deepSeekApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )

        return if (response.isSuccessful) {
            val body = response.body()
            val message = body?.choices?.firstOrNull()?.message
            // DeepSeek reasoning models may return content in reasoning_content field
            // Use content first, fall back to reasoning_content
            val content = message?.content ?: message?.reasoning_content
            val usage = body?.usage?.let {
                TokenUsage(
                    inputTokens = it.prompt_tokens ?: 0,
                    outputTokens = it.completion_tokens ?: 0
                )
            }
            if (!content.isNullOrBlank()) {
                AiAnalysisResponse(AiService.DEEPSEEK, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.DEEPSEEK, null, errorMsg)
            }
        } else {
            AiAnalysisResponse(AiService.DEEPSEEK, null, "API error: ${response.code()} ${response.message()}")
        }
    }

    private suspend fun analyzeWithMistral(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = MistralRequest(
            model = model,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )
        val response = mistralApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )

        return if (response.isSuccessful) {
            val body = response.body()
            val content = body?.choices?.firstOrNull()?.message?.content
            val usage = body?.usage?.let {
                TokenUsage(
                    inputTokens = it.prompt_tokens ?: 0,
                    outputTokens = it.completion_tokens ?: 0
                )
            }
            if (content != null) {
                AiAnalysisResponse(AiService.MISTRAL, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.MISTRAL, null, errorMsg)
            }
        } else {
            AiAnalysisResponse(AiService.MISTRAL, null, "API error: ${response.code()} ${response.message()}")
        }
    }

    private suspend fun analyzeWithPerplexity(apiKey: String, prompt: String, model: String): AiAnalysisResponse {
        val request = PerplexityRequest(
            model = model,
            messages = listOf(OpenAiMessage(role = "user", content = prompt))
        )
        val response = perplexityApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )

        return if (response.isSuccessful) {
            val body = response.body()
            val content = body?.choices?.firstOrNull()?.message?.content
            val usage = body?.usage?.let {
                TokenUsage(
                    inputTokens = it.prompt_tokens ?: 0,
                    outputTokens = it.completion_tokens ?: 0
                )
            }
            if (content != null) {
                AiAnalysisResponse(AiService.PERPLEXITY, content, null, usage)
            } else {
                val errorMsg = body?.error?.message ?: "No response content"
                AiAnalysisResponse(AiService.PERPLEXITY, null, errorMsg)
            }
        } else {
            AiAnalysisResponse(AiService.PERPLEXITY, null, "API error: ${response.code()} ${response.message()}")
        }
    }

    private fun analyzeWithDummy(): AiAnalysisResponse {
        return AiAnalysisResponse(AiService.DUMMY, "Hi, greetings from AI", null, TokenUsage(10, 5))
    }

    /**
     * Fetch available Gemini models that support generateContent.
     */
    suspend fun fetchGeminiModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = geminiApi.listModels(apiKey)
            if (response.isSuccessful) {
                val models = response.body()?.models ?: emptyList()
                // Filter models that support generateContent and return their names without "models/" prefix
                models
                    .filter { model ->
                        model.supportedGenerationMethods?.contains("generateContent") == true
                    }
                    .mapNotNull { model ->
                        model.name?.removePrefix("models/")
                    }
                    .sorted()
            } else {
                android.util.Log.e("GeminiAPI", "Failed to fetch models: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiAPI", "Error fetching models: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch available Grok models.
     */
    suspend fun fetchGrokModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = grokApi.listModels("Bearer $apiKey")
            if (response.isSuccessful) {
                val models = response.body()?.data ?: emptyList()
                models
                    .mapNotNull { it.id }
                    .filter { it.startsWith("grok") }  // Only include grok models
                    .sorted()
            } else {
                android.util.Log.e("GrokAPI", "Failed to fetch models: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("GrokAPI", "Error fetching models: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch available ChatGPT/OpenAI models.
     */
    suspend fun fetchChatGptModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = openAiApi.listModels("Bearer $apiKey")
            if (response.isSuccessful) {
                val models = response.body()?.data ?: emptyList()
                models
                    .mapNotNull { it.id }
                    .filter { it.startsWith("gpt") }  // Only include GPT models
                    .sorted()
            } else {
                android.util.Log.e("ChatGptAPI", "Failed to fetch models: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatGptAPI", "Error fetching models: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch available DeepSeek models.
     */
    suspend fun fetchDeepSeekModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = deepSeekApi.listModels("Bearer $apiKey")
            if (response.isSuccessful) {
                val models = response.body()?.data ?: emptyList()
                models
                    .mapNotNull { it.id }
                    .filter { it.startsWith("deepseek") }  // Only include DeepSeek models
                    .sorted()
            } else {
                android.util.Log.e("DeepSeekAPI", "Failed to fetch models: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("DeepSeekAPI", "Error fetching models: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch available Mistral models.
     */
    suspend fun fetchMistralModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = mistralApi.listModels("Bearer $apiKey")
            if (response.isSuccessful) {
                val models = response.body()?.data ?: emptyList()
                models
                    .mapNotNull { it.id }
                    .filter { it.startsWith("mistral") || it.startsWith("open-mistral") || it.startsWith("codestral") || it.startsWith("pixtral") }
                    .sorted()
            } else {
                android.util.Log.e("MistralAPI", "Failed to fetch models: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("MistralAPI", "Error fetching models: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch available Perplexity models.
     */
    suspend fun fetchPerplexityModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = perplexityApi.listModels("Bearer $apiKey")
            if (response.isSuccessful) {
                val models = response.body()?.data ?: emptyList()
                models
                    .mapNotNull { it.id }
                    .filter { it.contains("sonar") || it.contains("llama") }
                    .sorted()
            } else {
                android.util.Log.e("PerplexityAPI", "Failed to fetch models: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("PerplexityAPI", "Error fetching models: ${e.message}")
            emptyList()
        }
    }

}
