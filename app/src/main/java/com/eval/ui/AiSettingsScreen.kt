package com.eval.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eval.data.AiService
import com.google.gson.Gson
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import com.google.gson.JsonSyntaxException

/**
 * AI Settings data class for storing API keys for various AI services.
 */
/**
 * Default prompt template for AI chess game analysis.
 */
const val DEFAULT_GAME_PROMPT = """You are an expert chess analyst. Analyze the following chess position given in FEN notation.

FEN: @FEN@

Please provide:
1. A brief assessment of the position (who is better and why)
2. Key strategic themes and plans for the side to play

No need to use an chess engine to look for tactical opportunities, Stockfish is already doing that for me.

Keep your analysis concise but insightful, suitable for a chess player looking to understand the position better."""

// Keep old constant name for backwards compatibility
const val DEFAULT_AI_PROMPT = DEFAULT_GAME_PROMPT

/**
 * Default prompt template for lichess.org & chess.com player analysis.
 */
const val DEFAULT_SERVER_PLAYER_PROMPT = """What do you know about user @PLAYER@ on chess server @SERVER@ ?. What is the real name of this player? What is good and the bad about this player? Is there any gossip on the internet?"""

/**
 * Default prompt template for other player analysis.
 */
const val DEFAULT_OTHER_PLAYER_PROMPT = """You are a professional chess journalist. Write a profile of the chess player @PLAYER@ (1000 words) for a serious publication.

Rules: Do not invent facts, quotes, games, ratings, titles, events, or personal details. If info is missing or uncertain, say so and label it 'unverified' or 'unknown.' If web access exists, verify key facts via reputable sources (e.g., FIDE, national federation, major chess media) and list sources at the end.

Must cover (with subheadings):

Career timeline + key results + rating/title context (only if verified)

Playing style: openings, strengths/weaknesses, psychology—grounded in evidence

2–3 signature games (human explanation; minimal notation; no engine-dump)

Rivalries/peers and place in today's chess landscape

Off-the-board work (coaching/streaming/writing/sponsors/controversies—verified only)

Current form (last 12 months) and realistic outlook

End with a tight conclusion"""

/**
 * Enum for model source - API (fetched from provider) or Manual (user-maintained list)
 */
enum class ModelSource {
    API,
    MANUAL
}

data class AiSettings(
    val chatGptApiKey: String = "",
    val chatGptModel: String = "gpt-4o-mini",
    val chatGptPrompt: String = DEFAULT_GAME_PROMPT,
    val chatGptServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val chatGptOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val chatGptModelSource: ModelSource = ModelSource.API,
    val chatGptManualModels: List<String> = emptyList(),
    val claudeApiKey: String = "",
    val claudeModel: String = "claude-sonnet-4-20250514",
    val claudePrompt: String = DEFAULT_GAME_PROMPT,
    val claudeServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val claudeOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val claudeModelSource: ModelSource = ModelSource.MANUAL,
    val claudeManualModels: List<String> = CLAUDE_MODELS,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.0-flash",
    val geminiPrompt: String = DEFAULT_GAME_PROMPT,
    val geminiServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val geminiOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val geminiModelSource: ModelSource = ModelSource.API,
    val geminiManualModels: List<String> = emptyList(),
    val grokApiKey: String = "",
    val grokModel: String = "grok-3-mini",
    val grokPrompt: String = DEFAULT_GAME_PROMPT,
    val grokServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val grokOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val grokModelSource: ModelSource = ModelSource.API,
    val grokManualModels: List<String> = emptyList(),
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = "deepseek-chat",
    val deepSeekPrompt: String = DEFAULT_GAME_PROMPT,
    val deepSeekServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val deepSeekOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val deepSeekModelSource: ModelSource = ModelSource.API,
    val deepSeekManualModels: List<String> = emptyList(),
    val mistralApiKey: String = "",
    val mistralModel: String = "mistral-small-latest",
    val mistralPrompt: String = DEFAULT_GAME_PROMPT,
    val mistralServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val mistralOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val mistralModelSource: ModelSource = ModelSource.API,
    val mistralManualModels: List<String> = emptyList(),
    val perplexityApiKey: String = "",
    val perplexityModel: String = "sonar",
    val perplexityPrompt: String = DEFAULT_GAME_PROMPT,
    val perplexityServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val perplexityOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val perplexityModelSource: ModelSource = ModelSource.MANUAL,
    val perplexityManualModels: List<String> = PERPLEXITY_MODELS,
    val togetherApiKey: String = "",
    val togetherModel: String = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
    val togetherPrompt: String = DEFAULT_GAME_PROMPT,
    val togetherServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val togetherOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val togetherModelSource: ModelSource = ModelSource.API,
    val togetherManualModels: List<String> = emptyList(),
    val openRouterApiKey: String = "",
    val openRouterModel: String = "anthropic/claude-3.5-sonnet",
    val openRouterPrompt: String = DEFAULT_GAME_PROMPT,
    val openRouterServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val openRouterOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val openRouterModelSource: ModelSource = ModelSource.API,
    val openRouterManualModels: List<String> = emptyList(),
    val dummyEnabled: Boolean = false,
    val dummyPrompt: String = DEFAULT_GAME_PROMPT,
    val dummyServerPlayerPrompt: String = DEFAULT_SERVER_PLAYER_PROMPT,
    val dummyOtherPlayerPrompt: String = DEFAULT_OTHER_PLAYER_PROMPT,
    val dummyModelSource: ModelSource = ModelSource.MANUAL,
    val dummyManualModels: List<String> = listOf("dummy-model"),
    // New three-tier architecture
    val prompts: List<AiPrompt> = emptyList(),
    val agents: List<AiAgent> = emptyList()
) {
    fun getApiKey(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptApiKey
            AiService.CLAUDE -> claudeApiKey
            AiService.GEMINI -> geminiApiKey
            AiService.GROK -> grokApiKey
            AiService.DEEPSEEK -> deepSeekApiKey
            AiService.MISTRAL -> mistralApiKey
            AiService.PERPLEXITY -> perplexityApiKey
            AiService.TOGETHER -> togetherApiKey
            AiService.OPENROUTER -> openRouterApiKey
            AiService.DUMMY -> if (dummyEnabled) "enabled" else ""
        }
    }

    fun getModel(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptModel
            AiService.CLAUDE -> claudeModel
            AiService.GEMINI -> geminiModel
            AiService.GROK -> grokModel
            AiService.DEEPSEEK -> deepSeekModel
            AiService.MISTRAL -> mistralModel
            AiService.PERPLEXITY -> perplexityModel
            AiService.TOGETHER -> togetherModel
            AiService.OPENROUTER -> openRouterModel
            AiService.DUMMY -> ""
        }
    }

    fun getPrompt(service: AiService): String = getGamePrompt(service)

    fun getGamePrompt(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptPrompt
            AiService.CLAUDE -> claudePrompt
            AiService.GEMINI -> geminiPrompt
            AiService.GROK -> grokPrompt
            AiService.DEEPSEEK -> deepSeekPrompt
            AiService.MISTRAL -> mistralPrompt
            AiService.PERPLEXITY -> perplexityPrompt
            AiService.TOGETHER -> togetherPrompt
            AiService.OPENROUTER -> openRouterPrompt
            AiService.DUMMY -> dummyPrompt
        }
    }

    fun getServerPlayerPrompt(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptServerPlayerPrompt
            AiService.CLAUDE -> claudeServerPlayerPrompt
            AiService.GEMINI -> geminiServerPlayerPrompt
            AiService.GROK -> grokServerPlayerPrompt
            AiService.DEEPSEEK -> deepSeekServerPlayerPrompt
            AiService.MISTRAL -> mistralServerPlayerPrompt
            AiService.PERPLEXITY -> perplexityServerPlayerPrompt
            AiService.TOGETHER -> togetherServerPlayerPrompt
            AiService.OPENROUTER -> openRouterServerPlayerPrompt
            AiService.DUMMY -> dummyServerPlayerPrompt
        }
    }

    fun getOtherPlayerPrompt(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptOtherPlayerPrompt
            AiService.CLAUDE -> claudeOtherPlayerPrompt
            AiService.GEMINI -> geminiOtherPlayerPrompt
            AiService.GROK -> grokOtherPlayerPrompt
            AiService.DEEPSEEK -> deepSeekOtherPlayerPrompt
            AiService.MISTRAL -> mistralOtherPlayerPrompt
            AiService.PERPLEXITY -> perplexityOtherPlayerPrompt
            AiService.TOGETHER -> togetherOtherPlayerPrompt
            AiService.OPENROUTER -> openRouterOtherPlayerPrompt
            AiService.DUMMY -> dummyOtherPlayerPrompt
        }
    }

    fun withModel(service: AiService, model: String): AiSettings {
        return when (service) {
            AiService.CHATGPT -> copy(chatGptModel = model)
            AiService.CLAUDE -> copy(claudeModel = model)
            AiService.GEMINI -> copy(geminiModel = model)
            AiService.GROK -> copy(grokModel = model)
            AiService.DEEPSEEK -> copy(deepSeekModel = model)
            AiService.MISTRAL -> copy(mistralModel = model)
            AiService.PERPLEXITY -> copy(perplexityModel = model)
            AiService.TOGETHER -> copy(togetherModel = model)
            AiService.OPENROUTER -> copy(openRouterModel = model)
            AiService.DUMMY -> this
        }
    }

    fun getModelSource(service: AiService): ModelSource {
        return when (service) {
            AiService.CHATGPT -> chatGptModelSource
            AiService.CLAUDE -> claudeModelSource
            AiService.GEMINI -> geminiModelSource
            AiService.GROK -> grokModelSource
            AiService.DEEPSEEK -> deepSeekModelSource
            AiService.MISTRAL -> mistralModelSource
            AiService.PERPLEXITY -> perplexityModelSource
            AiService.TOGETHER -> togetherModelSource
            AiService.OPENROUTER -> openRouterModelSource
            AiService.DUMMY -> ModelSource.MANUAL
        }
    }

    fun getManualModels(service: AiService): List<String> {
        return when (service) {
            AiService.CHATGPT -> chatGptManualModels
            AiService.CLAUDE -> claudeManualModels
            AiService.GEMINI -> geminiManualModels
            AiService.GROK -> grokManualModels
            AiService.DEEPSEEK -> deepSeekManualModels
            AiService.MISTRAL -> mistralManualModels
            AiService.PERPLEXITY -> perplexityManualModels
            AiService.TOGETHER -> togetherManualModels
            AiService.OPENROUTER -> openRouterManualModels
            AiService.DUMMY -> emptyList()
        }
    }

    fun hasAnyApiKey(): Boolean {
        return chatGptApiKey.isNotBlank() ||
                claudeApiKey.isNotBlank() ||
                geminiApiKey.isNotBlank() ||
                grokApiKey.isNotBlank() ||
                deepSeekApiKey.isNotBlank() ||
                mistralApiKey.isNotBlank() ||
                perplexityApiKey.isNotBlank() ||
                togetherApiKey.isNotBlank() ||
                openRouterApiKey.isNotBlank() ||
                dummyEnabled
    }

    fun getConfiguredServices(): List<AiService> {
        return AiService.entries.filter { getApiKey(it).isNotBlank() }
    }

    // Helper methods for prompts
    fun getPromptById(id: String): AiPrompt? = prompts.find { it.id == id }

    fun getPromptByName(name: String): AiPrompt? = prompts.find { it.name == name }

    // Helper methods for agents
    fun getAgentById(id: String): AiAgent? = agents.find { it.id == id }

    fun getConfiguredAgents(): List<AiAgent> = agents.filter { it.apiKey.isNotBlank() }

    /**
     * Get the prompt text for an agent's game analysis.
     * Falls back to default if prompt not found.
     */
    fun getAgentGamePrompt(agent: AiAgent): String {
        return getPromptById(agent.gamePromptId)?.text ?: DEFAULT_GAME_PROMPT
    }

    /**
     * Get the prompt text for an agent's server player analysis.
     * Falls back to default if prompt not found.
     */
    fun getAgentServerPlayerPrompt(agent: AiAgent): String {
        return getPromptById(agent.serverPlayerPromptId)?.text ?: DEFAULT_SERVER_PLAYER_PROMPT
    }

    /**
     * Get the prompt text for an agent's other player analysis.
     * Falls back to default if prompt not found.
     */
    fun getAgentOtherPlayerPrompt(agent: AiAgent): String {
        return getPromptById(agent.otherPlayerPromptId)?.text ?: DEFAULT_OTHER_PLAYER_PROMPT
    }
}

/**
 * AI Prompt - user-created prompt template for AI analysis.
 */
data class AiPrompt(
    val id: String,    // UUID
    val name: String,  // User-defined name
    val text: String   // Prompt template with @FEN@, @PLAYER@, @SERVER@ placeholders
)

/**
 * AI Agent - user-created configuration combining provider, model, API key, and prompts.
 */
data class AiAgent(
    val id: String,                    // UUID
    val name: String,                  // User-defined name
    val provider: AiService,           // Reference to provider enum
    val model: String,                 // Model name
    val apiKey: String,                // API key for this agent
    val gamePromptId: String,          // Reference to AiPrompt by ID
    val serverPlayerPromptId: String,  // Reference to AiPrompt by ID
    val otherPlayerPromptId: String    // Reference to AiPrompt by ID
)

/**
 * Default prompt names for migration and initialization.
 */
const val DEFAULT_GAME_PROMPT_NAME = "Game Analysis"
const val DEFAULT_SERVER_PLAYER_PROMPT_NAME = "Server Player"
const val DEFAULT_OTHER_PLAYER_PROMPT_NAME = "Other Player"

/**
 * Main AI settings screen with navigation cards for each AI service.
 */
@Composable
fun AiSettingsScreen(
    aiSettings: AiSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onNavigate: (SettingsSubScreen) -> Unit,
    onSave: (AiSettings) -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AI Analysis",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "AI Services",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp)
        )

        AiServiceNavigationCard(
            title = "ChatGPT",
            subtitle = "OpenAI",
            accentColor = Color(0xFF10A37F),
            onClick = { onNavigate(SettingsSubScreen.AI_CHATGPT) }
        )
        AiServiceNavigationCard(
            title = "Claude",
            subtitle = "Anthropic",
            accentColor = Color(0xFFD97706),
            onClick = { onNavigate(SettingsSubScreen.AI_CLAUDE) }
        )
        AiServiceNavigationCard(
            title = "Gemini",
            subtitle = "Google",
            accentColor = Color(0xFF4285F4),
            onClick = { onNavigate(SettingsSubScreen.AI_GEMINI) }
        )
        AiServiceNavigationCard(
            title = "Grok",
            subtitle = "xAI",
            accentColor = Color(0xFFFFFFFF),
            onClick = { onNavigate(SettingsSubScreen.AI_GROK) }
        )
        AiServiceNavigationCard(
            title = "DeepSeek",
            subtitle = "DeepSeek AI",
            accentColor = Color(0xFF4D6BFE),
            onClick = { onNavigate(SettingsSubScreen.AI_DEEPSEEK) }
        )
        AiServiceNavigationCard(
            title = "Mistral",
            subtitle = "Mistral AI",
            accentColor = Color(0xFFFF7000),
            onClick = { onNavigate(SettingsSubScreen.AI_MISTRAL) }
        )
        AiServiceNavigationCard(
            title = "Perplexity",
            subtitle = "Perplexity AI",
            accentColor = Color(0xFF20B2AA),
            onClick = { onNavigate(SettingsSubScreen.AI_PERPLEXITY) }
        )
        AiServiceNavigationCard(
            title = "Together",
            subtitle = "Together AI",
            accentColor = Color(0xFF6366F1),
            onClick = { onNavigate(SettingsSubScreen.AI_TOGETHER) }
        )
        AiServiceNavigationCard(
            title = "OpenRouter",
            subtitle = "OpenRouter AI",
            accentColor = Color(0xFF6B5AED),
            onClick = { onNavigate(SettingsSubScreen.AI_OPENROUTER) }
        )
        AiServiceNavigationCard(
            title = "Dummy",
            subtitle = "For testing",
            accentColor = Color(0xFF888888),
            onClick = { onNavigate(SettingsSubScreen.AI_DUMMY) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Export AI configuration button
        if (aiSettings.hasAnyApiKey()) {
            Button(
                onClick = {
                    exportAiConfigToClipboard(context, aiSettings)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Export AI configuration")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Export API keys only button
            Button(
                onClick = {
                    exportApiKeysToClipboard(context, aiSettings)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text("Export API keys")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Import AI configuration button
        Button(
            onClick = { showImportDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text("Import AI configuration")
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }

    // Import AI configuration dialog
    if (showImportDialog) {
        ImportAiConfigDialog(
            onImport = { importedSettings ->
                onSave(importedSettings)
                showImportDialog = false
                Toast.makeText(context, "AI configuration imported successfully", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showImportDialog = false }
        )
    }
}

/**
 * Data class for AI service configuration in JSON export/import.
 * Version 2 adds serverPlayerPrompt and otherPlayerPrompt.
 */
private data class AiServiceConfig(
    val name: String,
    val apiKey: String,
    val model: String,
    val prompt: String,  // Game prompt (kept for backwards compatibility)
    val gamePrompt: String? = null,  // Same as prompt, explicit name for v2
    val serverPlayerPrompt: String? = null,
    val otherPlayerPrompt: String? = null
)

/**
 * Data class for the complete AI configuration export.
 * Version 2 includes all 3 prompt types per service.
 */
private data class AiConfigExport(
    val version: Int = 2,
    val services: List<AiServiceConfig>,
    val dummyEnabled: Boolean
)

/**
 * Export AI configuration to clipboard as JSON.
 */
private fun exportAiConfigToClipboard(context: Context, aiSettings: AiSettings) {
    val services = mutableListOf<AiServiceConfig>()

    if (aiSettings.chatGptApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "ChatGPT",
            apiKey = aiSettings.chatGptApiKey,
            model = aiSettings.chatGptModel,
            prompt = aiSettings.chatGptPrompt,
            gamePrompt = aiSettings.chatGptPrompt,
            serverPlayerPrompt = aiSettings.chatGptServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.chatGptOtherPlayerPrompt
        ))
    }

    if (aiSettings.claudeApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Claude",
            apiKey = aiSettings.claudeApiKey,
            model = aiSettings.claudeModel,
            prompt = aiSettings.claudePrompt,
            gamePrompt = aiSettings.claudePrompt,
            serverPlayerPrompt = aiSettings.claudeServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.claudeOtherPlayerPrompt
        ))
    }

    if (aiSettings.geminiApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Gemini",
            apiKey = aiSettings.geminiApiKey,
            model = aiSettings.geminiModel,
            prompt = aiSettings.geminiPrompt,
            gamePrompt = aiSettings.geminiPrompt,
            serverPlayerPrompt = aiSettings.geminiServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.geminiOtherPlayerPrompt
        ))
    }

    if (aiSettings.grokApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Grok",
            apiKey = aiSettings.grokApiKey,
            model = aiSettings.grokModel,
            prompt = aiSettings.grokPrompt,
            gamePrompt = aiSettings.grokPrompt,
            serverPlayerPrompt = aiSettings.grokServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.grokOtherPlayerPrompt
        ))
    }

    if (aiSettings.deepSeekApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "DeepSeek",
            apiKey = aiSettings.deepSeekApiKey,
            model = aiSettings.deepSeekModel,
            prompt = aiSettings.deepSeekPrompt,
            gamePrompt = aiSettings.deepSeekPrompt,
            serverPlayerPrompt = aiSettings.deepSeekServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.deepSeekOtherPlayerPrompt
        ))
    }

    if (aiSettings.mistralApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Mistral",
            apiKey = aiSettings.mistralApiKey,
            model = aiSettings.mistralModel,
            prompt = aiSettings.mistralPrompt,
            gamePrompt = aiSettings.mistralPrompt,
            serverPlayerPrompt = aiSettings.mistralServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.mistralOtherPlayerPrompt
        ))
    }

    if (aiSettings.perplexityApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Perplexity",
            apiKey = aiSettings.perplexityApiKey,
            model = aiSettings.perplexityModel,
            prompt = aiSettings.perplexityPrompt,
            gamePrompt = aiSettings.perplexityPrompt,
            serverPlayerPrompt = aiSettings.perplexityServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.perplexityOtherPlayerPrompt
        ))
    }

    if (aiSettings.togetherApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Together",
            apiKey = aiSettings.togetherApiKey,
            model = aiSettings.togetherModel,
            prompt = aiSettings.togetherPrompt,
            gamePrompt = aiSettings.togetherPrompt,
            serverPlayerPrompt = aiSettings.togetherServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.togetherOtherPlayerPrompt
        ))
    }

    if (aiSettings.openRouterApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "OpenRouter",
            apiKey = aiSettings.openRouterApiKey,
            model = aiSettings.openRouterModel,
            prompt = aiSettings.openRouterPrompt,
            gamePrompt = aiSettings.openRouterPrompt,
            serverPlayerPrompt = aiSettings.openRouterServerPlayerPrompt,
            otherPlayerPrompt = aiSettings.openRouterOtherPlayerPrompt
        ))
    }

    val export = AiConfigExport(
        services = services,
        dummyEnabled = aiSettings.dummyEnabled
    )

    val gson = Gson()
    val json = gson.toJson(export)

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI Configuration", json)
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, "AI configuration copied to clipboard", Toast.LENGTH_SHORT).show()
}

/**
 * Data class for API key export entry.
 */
private data class ApiKeyEntry(
    val service: String,
    val apiKey: String
)

/**
 * Export API keys only to clipboard as JSON array.
 */
private fun exportApiKeysToClipboard(context: Context, aiSettings: AiSettings) {
    val keys = mutableListOf<ApiKeyEntry>()

    if (aiSettings.chatGptApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("ChatGPT", aiSettings.chatGptApiKey))
    }
    if (aiSettings.claudeApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("Claude", aiSettings.claudeApiKey))
    }
    if (aiSettings.geminiApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("Gemini", aiSettings.geminiApiKey))
    }
    if (aiSettings.grokApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("Grok", aiSettings.grokApiKey))
    }
    if (aiSettings.deepSeekApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("DeepSeek", aiSettings.deepSeekApiKey))
    }
    if (aiSettings.mistralApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("Mistral", aiSettings.mistralApiKey))
    }
    if (aiSettings.perplexityApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("Perplexity", aiSettings.perplexityApiKey))
    }
    if (aiSettings.togetherApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("Together", aiSettings.togetherApiKey))
    }
    if (aiSettings.openRouterApiKey.isNotBlank()) {
        keys.add(ApiKeyEntry("OpenRouter", aiSettings.openRouterApiKey))
    }

    val gson = Gson()
    val json = gson.toJson(keys)

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("API Keys", json)
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, "${keys.size} API keys copied to clipboard", Toast.LENGTH_SHORT).show()
}

/**
 * Import AI configuration from clipboard JSON.
 */
private fun importAiConfigFromClipboard(context: Context): AiSettings? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = clipboard.primaryClip

    if (clipData == null || clipData.itemCount == 0) {
        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        return null
    }

    val json = clipData.getItemAt(0).text?.toString()
    if (json.isNullOrBlank()) {
        Toast.makeText(context, "No text in clipboard", Toast.LENGTH_SHORT).show()
        return null
    }

    return try {
        val gson = Gson()
        val export = gson.fromJson(json, AiConfigExport::class.java)

        var settings = AiSettings()

        export.services.forEach { service ->
            // Use gamePrompt if available (v2), otherwise fall back to prompt (v1)
            val gamePrompt = service.gamePrompt ?: service.prompt
            // For v1 imports, use defaults for new prompt types
            val serverPlayerPrompt = service.serverPlayerPrompt ?: DEFAULT_SERVER_PLAYER_PROMPT
            val otherPlayerPrompt = service.otherPlayerPrompt ?: DEFAULT_OTHER_PLAYER_PROMPT

            settings = when (service.name) {
                "ChatGPT" -> settings.copy(
                    chatGptApiKey = service.apiKey,
                    chatGptModel = service.model,
                    chatGptPrompt = gamePrompt,
                    chatGptServerPlayerPrompt = serverPlayerPrompt,
                    chatGptOtherPlayerPrompt = otherPlayerPrompt
                )
                "Claude" -> settings.copy(
                    claudeApiKey = service.apiKey,
                    claudeModel = service.model,
                    claudePrompt = gamePrompt,
                    claudeServerPlayerPrompt = serverPlayerPrompt,
                    claudeOtherPlayerPrompt = otherPlayerPrompt
                )
                "Gemini" -> settings.copy(
                    geminiApiKey = service.apiKey,
                    geminiModel = service.model,
                    geminiPrompt = gamePrompt,
                    geminiServerPlayerPrompt = serverPlayerPrompt,
                    geminiOtherPlayerPrompt = otherPlayerPrompt
                )
                "Grok" -> settings.copy(
                    grokApiKey = service.apiKey,
                    grokModel = service.model,
                    grokPrompt = gamePrompt,
                    grokServerPlayerPrompt = serverPlayerPrompt,
                    grokOtherPlayerPrompt = otherPlayerPrompt
                )
                "DeepSeek" -> settings.copy(
                    deepSeekApiKey = service.apiKey,
                    deepSeekModel = service.model,
                    deepSeekPrompt = gamePrompt,
                    deepSeekServerPlayerPrompt = serverPlayerPrompt,
                    deepSeekOtherPlayerPrompt = otherPlayerPrompt
                )
                "Mistral" -> settings.copy(
                    mistralApiKey = service.apiKey,
                    mistralModel = service.model,
                    mistralPrompt = gamePrompt,
                    mistralServerPlayerPrompt = serverPlayerPrompt,
                    mistralOtherPlayerPrompt = otherPlayerPrompt
                )
                "Perplexity" -> settings.copy(
                    perplexityApiKey = service.apiKey,
                    perplexityModel = service.model,
                    perplexityPrompt = gamePrompt,
                    perplexityServerPlayerPrompt = serverPlayerPrompt,
                    perplexityOtherPlayerPrompt = otherPlayerPrompt
                )
                "Together" -> settings.copy(
                    togetherApiKey = service.apiKey,
                    togetherModel = service.model,
                    togetherPrompt = gamePrompt,
                    togetherServerPlayerPrompt = serverPlayerPrompt,
                    togetherOtherPlayerPrompt = otherPlayerPrompt
                )
                "OpenRouter" -> settings.copy(
                    openRouterApiKey = service.apiKey,
                    openRouterModel = service.model,
                    openRouterPrompt = gamePrompt,
                    openRouterServerPlayerPrompt = serverPlayerPrompt,
                    openRouterOtherPlayerPrompt = otherPlayerPrompt
                )
                else -> settings
            }
        }

        settings.copy(dummyEnabled = export.dummyEnabled)
    } catch (e: JsonSyntaxException) {
        Toast.makeText(context, "Invalid AI configuration format", Toast.LENGTH_SHORT).show()
        null
    } catch (e: Exception) {
        Toast.makeText(context, "Error importing configuration: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

/**
 * Dialog for importing AI configuration from clipboard.
 */
@Composable
private fun ImportAiConfigDialog(
    onImport: (AiSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Import AI Configuration",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This will import AI configuration from the clipboard.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "The clipboard should contain a JSON configuration exported from this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Text(
                    text = "Warning: This will replace your current AI settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val imported = importAiConfigFromClipboard(context)
                    if (imported != null) {
                        onImport(imported)
                    }
                }
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Navigation card for an AI service.
 */
@Composable
private fun AiServiceNavigationCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(accentColor, shape = MaterialTheme.shapes.small)
            )

            Text(
                text = "$title / $subtitle",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = ">",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF888888)
            )
        }
    }
}

// ============================================================================
// Individual AI Service Settings Screens
// ============================================================================

/**
 * Available Claude models (hardcoded as Anthropic doesn't provide a list models API).
 */
internal val CLAUDE_MODELS = listOf(
    "claude-sonnet-4-20250514",
    "claude-opus-4-20250514",
    "claude-3-7-sonnet-20250219",
    "claude-3-5-sonnet-20241022",
    "claude-3-5-haiku-20241022",
    "claude-3-opus-20240229",
    "claude-3-sonnet-20240229",
    "claude-3-haiku-20240307"
)

/**
 * Available Perplexity models (hardcoded).
 */
internal val PERPLEXITY_MODELS = listOf(
    "sonar",
    "sonar-pro",
    "sonar-reasoning-pro",
    "sonar-deep-research"
)

/**
 * ChatGPT settings screen.
 */
@Composable
fun ChatGptSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.chatGptModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.chatGptManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "ChatGPT",
        subtitle = "OpenAI",
        accentColor = Color(0xFF10A37F),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(chatGptModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(chatGptManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.chatGptApiKey) }
        )
    }
}

/**
 * Claude settings screen.
 */
@Composable
fun ClaudeSettingsScreen(
    aiSettings: AiSettings,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.claudeModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.claudeManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "Claude",
        subtitle = "Anthropic",
        accentColor = Color(0xFFD97706),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = emptyList(), // Claude doesn't have API for listing models
            isLoadingModels = false,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(claudeModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(claudeManualModels = it))
            },
            onFetchModels = { } // No-op for Claude
        )
    }
}

/**
 * Gemini settings screen.
 */
@Composable
fun GeminiSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.geminiModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.geminiManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "Gemini",
        subtitle = "Google",
        accentColor = Color(0xFF4285F4),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(geminiModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(geminiManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.geminiApiKey) }
        )
    }
}

/**
 * Grok settings screen.
 */
@Composable
fun GrokSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.grokModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.grokManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "Grok",
        subtitle = "xAI",
        accentColor = Color(0xFFFFFFFF),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(grokModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(grokManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.grokApiKey) }
        )
    }
}

/**
 * DeepSeek settings screen.
 */
@Composable
fun DeepSeekSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.deepSeekModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.deepSeekManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "DeepSeek",
        subtitle = "DeepSeek AI",
        accentColor = Color(0xFF4D6BFE),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(deepSeekModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(deepSeekManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.deepSeekApiKey) }
        )
    }
}

/**
 * Mistral settings screen.
 */
@Composable
fun MistralSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.mistralModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.mistralManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "Mistral",
        subtitle = "Mistral AI",
        accentColor = Color(0xFFFF7000),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(mistralModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(mistralManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.mistralApiKey) }
        )
    }
}

/**
 * Perplexity settings screen.
 */
@Composable
fun PerplexitySettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.perplexityModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.perplexityManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "Perplexity",
        subtitle = "Perplexity AI",
        accentColor = Color(0xFF20B2AA),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(perplexityModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(perplexityManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.perplexityApiKey) }
        )
    }
}

/**
 * Together AI settings screen.
 */
@Composable
fun TogetherSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.togetherModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.togetherManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "Together",
        subtitle = "Together AI",
        accentColor = Color(0xFF6366F1),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(togetherModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(togetherManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.togetherApiKey) }
        )
    }
}

/**
 * OpenRouter AI settings screen.
 */
@Composable
fun OpenRouterSettingsScreen(
    aiSettings: AiSettings,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onFetchModels: (String) -> Unit
) {
    var modelSource by remember { mutableStateOf(aiSettings.openRouterModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.openRouterManualModels) }

    AiServiceSettingsScreenTemplate(
        title = "OpenRouter",
        subtitle = "OpenRouter AI",
        accentColor = Color(0xFF6B5AED),
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        UnifiedModelSelectionSection(
            modelSource = modelSource,
            manualModels = manualModels,
            availableApiModels = availableModels,
            isLoadingModels = isLoadingModels,
            onModelSourceChange = {
                modelSource = it
                onSave(aiSettings.copy(openRouterModelSource = it))
            },
            onManualModelsChange = {
                manualModels = it
                onSave(aiSettings.copy(openRouterManualModels = it))
            },
            onFetchModels = { onFetchModels(aiSettings.openRouterApiKey) }
        )
    }
}

/**
 * Dummy settings screen (for testing without real API calls).
 */
@Composable
fun DummySettingsScreen(
    aiSettings: AiSettings,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit
) {
    var enabled by remember { mutableStateOf(aiSettings.dummyEnabled) }
    var modelSource by remember { mutableStateOf(aiSettings.dummyModelSource) }
    var manualModels by remember { mutableStateOf(aiSettings.dummyManualModels) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title with color indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(0xFF888888), shape = MaterialTheme.shapes.small)
            )
            Column {
                Text(
                    text = "AI Provider: Dummy",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "For testing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFAAAAAA)
                )
            }
        }

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSettings) {
                Text("< AI Providers")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Enable/disable card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Test Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Dummy AI", color = Color.White)
                        Text(
                            text = "Returns a test response without making API calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            onSave(aiSettings.copy(dummyEnabled = it))
                        }
                    )
                }

                if (enabled) {
                    Text(
                        text = "Response: \"Hi, greetings from AI\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00E676)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Model selection section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                UnifiedModelSelectionSection(
                    modelSource = modelSource,
                    manualModels = manualModels,
                    availableApiModels = emptyList(),
                    isLoadingModels = false,
                    onModelSourceChange = {
                        modelSource = it
                        onSave(aiSettings.copy(dummyModelSource = it))
                    },
                    onManualModelsChange = {
                        manualModels = it
                        onSave(aiSettings.copy(dummyManualModels = it))
                    },
                    onFetchModels = { /* No API fetch for Dummy */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSettings) {
                Text("< AI Providers")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }
    }
}

// ============================================================================
// Shared Components
// ============================================================================

/**
 * Template for AI service settings screens.
 */
@Composable
private fun AiServiceSettingsScreenTemplate(
    title: String,
    subtitle: String,
    accentColor: Color,
    onBackToAiSettings: () -> Unit,
    onBackToGame: () -> Unit,
    additionalContent: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title with color indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(accentColor, shape = MaterialTheme.shapes.small)
            )
            Column {
                Text(
                    text = "AI Provider: $title",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFAAAAAA)
                )
            }
        }

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSettings) {
                Text("< AI Providers")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Additional content (model selection, etc.)
        additionalContent()

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSettings) {
                Text("< AI Providers")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }
    }
}

/**
 * Model selection section with fetch button (for APIs that support listing models).
 */
@Composable
private fun ModelSelectionSection(
    selectedModel: String,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    onModelChange: (String) -> Unit,
    onFetchModels: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Model Selection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Button(
                onClick = onFetchModels,
                enabled = !isLoadingModels
            ) {
                if (isLoadingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading...")
                } else {
                    Text("Retrieve models")
                }
            }

            Text(
                text = "List of models:",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )

            var expanded by remember { mutableStateOf(false) }
            val modelsToShow = if (availableModels.isNotEmpty()) {
                availableModels
            } else {
                listOf(selectedModel)
            }

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedModel,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = Color.White
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    modelsToShow.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                onModelChange(model)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Model selection section with hardcoded list (for APIs that don't support listing models).
 */
@Composable
private fun HardcodedModelSelectionSection(
    selectedModel: String,
    availableModels: List<String>,
    onModelChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Model Selection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Text(
                text = "List of models:",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )

            var expanded by remember { mutableStateOf(false) }

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedModel,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = Color.White
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                onModelChange(model)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified model selection section with source toggle (API vs Manual).
 */
@Composable
private fun UnifiedModelSelectionSection(
    modelSource: ModelSource,
    manualModels: List<String>,
    availableApiModels: List<String>,
    isLoadingModels: Boolean,
    onModelSourceChange: (ModelSource) -> Unit,
    onManualModelsChange: (List<String>) -> Unit,
    onFetchModels: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<String?>(null) }
    var newModelName by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // Model source toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model source:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAAAAA)
                )
                FilterChip(
                    selected = modelSource == ModelSource.API,
                    onClick = { onModelSourceChange(ModelSource.API) },
                    label = { Text("API") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = modelSource == ModelSource.MANUAL,
                    onClick = { onModelSourceChange(ModelSource.MANUAL) },
                    label = { Text("Manual") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }

            // API mode: Fetch button and model list
            if (modelSource == ModelSource.API) {
                Button(
                    onClick = onFetchModels,
                    enabled = !isLoadingModels
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading...")
                    } else {
                        Text("Retrieve models")
                    }
                }

                if (availableApiModels.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        availableApiModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color(0xFF2A3A4A),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Manual mode: Add button and model list management
            if (modelSource == ModelSource.MANUAL) {
                Button(
                    onClick = {
                        newModelName = ""
                        showAddDialog = true
                    }
                ) {
                    Text("+ Add model")
                }

                // Show current manual models with edit/delete
                if (manualModels.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        manualModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color(0xFF2A3A4A),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        editingModel = model
                                        newModelName = model
                                        showAddDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("✎", color = Color(0xFFAAAAAA))
                                }
                                IconButton(
                                    onClick = {
                                        val newList = manualModels.filter { it != model }
                                        onManualModelsChange(newList)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("✕", color = Color(0xFFFF6666))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit model dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingModel = null
            },
            title = {
                Text(
                    if (editingModel != null) "Edit Model" else "Add Model",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newModelName,
                    onValueChange = { newModelName = it },
                    label = { Text("Model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newModelName.isNotBlank()) {
                            val newList = if (editingModel != null) {
                                manualModels.map { if (it == editingModel) newModelName.trim() else it }
                            } else {
                                manualModels + newModelName.trim()
                            }
                            onManualModelsChange(newList)
                        }
                        showAddDialog = false
                        editingModel = null
                    },
                    enabled = newModelName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingModel = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Placeholder information card for prompts.
 */
@Composable
fun PromptPlaceholdersInfo() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A3A4A)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Placeholder Variables",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "@FEN@ - Will be replaced by the chess position in FEN notation",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )
            Text(
                text = "@PLAYER@ - Will be replaced by the player name",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )
            Text(
                text = "@SERVER@ - Will be replaced by 'lichess.org' or 'chess.com'",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )
        }
    }
}

/**
 * Single prompt editing card.
 */
@Composable
fun SinglePromptCard(
    title: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onResetToDefault: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                TextButton(onClick = onResetToDefault) {
                    Text(
                        text = "Reset",
                        color = Color(0xFF6B9BFF)
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6B9BFF),
                    unfocusedBorderColor = Color(0xFF555555)
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            )
        }
    }
}

/**
 * All prompts editing section for AI service settings.
 */
@Composable
fun AllPromptsSection(
    gamePrompt: String,
    serverPlayerPrompt: String,
    otherPlayerPrompt: String,
    onGamePromptChange: (String) -> Unit,
    onServerPlayerPromptChange: (String) -> Unit,
    onOtherPlayerPromptChange: (String) -> Unit,
    onResetGamePrompt: () -> Unit,
    onResetServerPlayerPrompt: () -> Unit,
    onResetOtherPlayerPrompt: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Placeholder info card
        PromptPlaceholdersInfo()

        // Game prompt
        SinglePromptCard(
            title = "Game prompt",
            prompt = gamePrompt,
            onPromptChange = onGamePromptChange,
            onResetToDefault = onResetGamePrompt
        )

        // Server player prompt
        SinglePromptCard(
            title = "Chess server player prompt",
            prompt = serverPlayerPrompt,
            onPromptChange = onServerPlayerPromptChange,
            onResetToDefault = onResetServerPlayerPrompt
        )

        // Other player prompt
        SinglePromptCard(
            title = "Other player prompt",
            prompt = otherPlayerPrompt,
            onPromptChange = onOtherPlayerPromptChange,
            onResetToDefault = onResetOtherPlayerPrompt
        )
    }
}

/**
 * Legacy prompt editing section for AI service settings.
 * @deprecated Use AllPromptsSection instead
 */
@Composable
fun PromptEditSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onResetToDefault: () -> Unit
) {
    SinglePromptCard(
        title = "Game prompt",
        prompt = prompt,
        onPromptChange = onPromptChange,
        onResetToDefault = onResetToDefault
    )
}

// ============================================================================
// Three-Tier AI Architecture Screens
// ============================================================================

/**
 * AI Setup hub screen with navigation cards for Providers, Prompts, and Agents.
 */
@Composable
fun AiSetupScreen(
    aiSettings: AiSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onNavigate: (SettingsSubScreen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AI Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)

        Spacer(modifier = Modifier.height(8.dp))

        // Summary info
        val configuredAgents = aiSettings.agents.count { it.apiKey.isNotBlank() }
        val totalPrompts = aiSettings.prompts.size

        Text(
            text = "Configure AI services in three steps:",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFAAAAAA)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // AI Providers card
        AiSetupNavigationCard(
            title = "AI Providers",
            description = "Configure model sources for each AI service",
            icon = "⚙",
            count = "${AiService.entries.size - 1} providers",  // Exclude DUMMY
            onClick = { onNavigate(SettingsSubScreen.AI_PROVIDERS) }
        )

        // AI Prompts card
        AiSetupNavigationCard(
            title = "AI Prompts",
            description = "Create and manage prompt templates",
            icon = "📝",
            count = "$totalPrompts prompts",
            onClick = { onNavigate(SettingsSubScreen.AI_PROMPTS) }
        )

        // AI Agents card
        AiSetupNavigationCard(
            title = "AI Agents",
            description = "Configure agents with provider, model, key, and prompts",
            icon = "🤖",
            count = "$configuredAgents configured",
            onClick = { onNavigate(SettingsSubScreen.AI_AGENTS) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }
}

/**
 * Navigation card for AI Setup screen.
 */
@Composable
private fun AiSetupNavigationCard(
    title: String,
    description: String,
    icon: String,
    count: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAAAAA)
                )
            }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00E676)
                )
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

/**
 * AI Providers screen - configure model source for each provider.
 * This is the renamed/refactored version of the old AiSettingsScreen.
 */
@Composable
fun AiProvidersScreen(
    aiSettings: AiSettings,
    onBackToAiSetup: () -> Unit,
    onBackToGame: () -> Unit,
    onNavigate: (SettingsSubScreen) -> Unit,
    onSave: (AiSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AI Providers",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSetup) {
                Text("< AI Setup")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Provider cards - navigate to individual provider screens for model config
        AiServiceNavigationCard(
            title = "ChatGPT",
            subtitle = "OpenAI",
            accentColor = Color(0xFF10A37F),
            onClick = { onNavigate(SettingsSubScreen.AI_CHATGPT) }
        )
        AiServiceNavigationCard(
            title = "Claude",
            subtitle = "Anthropic",
            accentColor = Color(0xFFD97706),
            onClick = { onNavigate(SettingsSubScreen.AI_CLAUDE) }
        )
        AiServiceNavigationCard(
            title = "Gemini",
            subtitle = "Google",
            accentColor = Color(0xFF4285F4),
            onClick = { onNavigate(SettingsSubScreen.AI_GEMINI) }
        )
        AiServiceNavigationCard(
            title = "Grok",
            subtitle = "xAI",
            accentColor = Color(0xFFFFFFFF),
            onClick = { onNavigate(SettingsSubScreen.AI_GROK) }
        )
        AiServiceNavigationCard(
            title = "DeepSeek",
            subtitle = "DeepSeek AI",
            accentColor = Color(0xFF4D6BFE),
            onClick = { onNavigate(SettingsSubScreen.AI_DEEPSEEK) }
        )
        AiServiceNavigationCard(
            title = "Mistral",
            subtitle = "Mistral AI",
            accentColor = Color(0xFFFF7000),
            onClick = { onNavigate(SettingsSubScreen.AI_MISTRAL) }
        )
        AiServiceNavigationCard(
            title = "Perplexity",
            subtitle = "Perplexity AI",
            accentColor = Color(0xFF20B2AA),
            onClick = { onNavigate(SettingsSubScreen.AI_PERPLEXITY) }
        )
        AiServiceNavigationCard(
            title = "Together",
            subtitle = "Together AI",
            accentColor = Color(0xFF6366F1),
            onClick = { onNavigate(SettingsSubScreen.AI_TOGETHER) }
        )
        AiServiceNavigationCard(
            title = "OpenRouter",
            subtitle = "OpenRouter AI",
            accentColor = Color(0xFF6B5AED),
            onClick = { onNavigate(SettingsSubScreen.AI_OPENROUTER) }
        )
        AiServiceNavigationCard(
            title = "Dummy",
            subtitle = "For testing",
            accentColor = Color(0xFF888888),
            onClick = { onNavigate(SettingsSubScreen.AI_DUMMY) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSetup) {
                Text("< AI Setup")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }
    }
}

/**
 * AI Prompts screen - CRUD for prompt templates.
 */
@Composable
fun AiPromptsScreen(
    aiSettings: AiSettings,
    onBackToAiSetup: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<AiPrompt?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<AiPrompt?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AI Prompts",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSetup) {
                Text("< AI Setup")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Placeholder info
        PromptPlaceholdersInfo()

        Spacer(modifier = Modifier.height(8.dp))

        // Add button
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text("+ Add Prompt")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Prompt list
        if (aiSettings.prompts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No prompts configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFAAAAAA)
                    )
                    Text(
                        text = "Add a prompt to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }
            }
        } else {
            aiSettings.prompts.forEach { prompt ->
                PromptListItem(
                    prompt = prompt,
                    onEdit = { editingPrompt = prompt },
                    onDelete = { showDeleteConfirm = prompt }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSetup) {
                Text("< AI Setup")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog || editingPrompt != null) {
        PromptEditDialog(
            prompt = editingPrompt,
            existingNames = aiSettings.prompts.map { it.name }.toSet(),
            onSave = { name, text ->
                val newPrompts = if (editingPrompt != null) {
                    aiSettings.prompts.map {
                        if (it.id == editingPrompt!!.id) it.copy(name = name, text = text) else it
                    }
                } else {
                    aiSettings.prompts + AiPrompt(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        text = text
                    )
                }
                onSave(aiSettings.copy(prompts = newPrompts))
                showAddDialog = false
                editingPrompt = null
            },
            onDismiss = {
                showAddDialog = false
                editingPrompt = null
            }
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { prompt ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Prompt", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to delete \"${prompt.name}\"?")

                    // Check if any agents reference this prompt
                    val referencingAgents = aiSettings.agents.filter {
                        it.gamePromptId == prompt.id ||
                        it.serverPlayerPromptId == prompt.id ||
                        it.otherPlayerPromptId == prompt.id
                    }
                    if (referencingAgents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Warning: ${referencingAgents.size} agent(s) reference this prompt.",
                            color = Color(0xFFFF9800),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newPrompts = aiSettings.prompts.filter { it.id != prompt.id }
                        onSave(aiSettings.copy(prompts = newPrompts))
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Prompt list item card.
 */
@Composable
private fun PromptListItem(
    prompt: AiPrompt,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prompt.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onEdit) {
                        Text("Edit", color = Color(0xFF6B9BFF))
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = Color(0xFFF44336))
                    }
                }
            }
            Text(
                text = prompt.text.take(100) + if (prompt.text.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
                maxLines = 2
            )
        }
    }
}

/**
 * Prompt add/edit dialog.
 */
@Composable
private fun PromptEditDialog(
    prompt: AiPrompt?,
    existingNames: Set<String>,
    onSave: (name: String, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(prompt?.name ?: "") }
    var text by remember { mutableStateOf(prompt?.text ?: "") }
    val isEditing = prompt != null

    // Validation
    val nameError = when {
        name.isBlank() -> "Name is required"
        !isEditing && existingNames.contains(name) -> "Name already exists"
        isEditing && name != prompt?.name && existingNames.contains(name) -> "Name already exists"
        else -> null
    }
    val textError = if (text.isBlank()) "Prompt text is required" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Prompt" else "Add Prompt",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = Color(0xFFF44336)) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Prompt text") },
                    isError = textError != null,
                    supportingText = textError?.let { { Text(it, color = Color(0xFFF44336)) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )

                Text(
                    text = "Use @FEN@, @PLAYER@, @SERVER@ as placeholders",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), text) },
                enabled = nameError == null && textError == null
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * AI Agents screen - CRUD for agent configurations.
 */
@Composable
fun AiAgentsScreen(
    aiSettings: AiSettings,
    availableChatGptModels: List<String>,
    availableGeminiModels: List<String>,
    availableGrokModels: List<String>,
    availableDeepSeekModels: List<String>,
    availableMistralModels: List<String>,
    availablePerplexityModels: List<String>,
    availableTogetherModels: List<String>,
    availableOpenRouterModels: List<String>,
    onBackToAiSetup: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiSettings) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAgent by remember { mutableStateOf<AiAgent?>(null) }
    var copyingAgent by remember { mutableStateOf<AiAgent?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<AiAgent?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AI Agents",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSetup) {
                Text("< AI Setup")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }

        // Add button
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text("+ Add Agent")
        }

        // Agent list
        if (aiSettings.agents.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No agents configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFAAAAAA)
                    )
                    Text(
                        text = "Add an agent to start using AI analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }
            }
        } else {
            aiSettings.agents.sortedBy { it.name.lowercase() }.forEach { agent ->
                AgentListItem(
                    agent = agent,
                    onEdit = { editingAgent = agent },
                    onCopy = { copyingAgent = agent },
                    onDelete = { showDeleteConfirm = agent }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSetup) {
                Text("< AI Setup")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }
    }

    // Add/Edit/Copy dialog
    if (showAddDialog || editingAgent != null || copyingAgent != null) {
        // For copy mode, create a template agent with new ID and "(Copy)" suffix
        val dialogAgent = when {
            copyingAgent != null -> copyingAgent!!.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${copyingAgent!!.name} (Copy)"
            )
            else -> editingAgent
        }
        val isEditMode = editingAgent != null

        AgentEditDialog(
            agent = dialogAgent,
            aiSettings = aiSettings,
            availableChatGptModels = availableChatGptModels,
            availableGeminiModels = availableGeminiModels,
            availableGrokModels = availableGrokModels,
            availableDeepSeekModels = availableDeepSeekModels,
            availableMistralModels = availableMistralModels,
            availablePerplexityModels = availablePerplexityModels,
            availableTogetherModels = availableTogetherModels,
            availableOpenRouterModels = availableOpenRouterModels,
            existingNames = aiSettings.agents.map { it.name }.toSet(),
            onSave = { newAgent ->
                val newAgents = if (isEditMode) {
                    aiSettings.agents.map { if (it.id == editingAgent!!.id) newAgent else it }
                } else {
                    aiSettings.agents + newAgent
                }
                onSave(aiSettings.copy(agents = newAgents))
                showAddDialog = false
                editingAgent = null
                copyingAgent = null
            },
            onDismiss = {
                showAddDialog = false
                editingAgent = null
                copyingAgent = null
            }
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { agent ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Agent", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${agent.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        val newAgents = aiSettings.agents.filter { it.id != agent.id }
                        onSave(aiSettings.copy(agents = newAgents))
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Agent list item card.
 */
@Composable
private fun AgentListItem(
    agent: AiAgent,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Edit", color = Color(0xFF6B9BFF))
                }
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Copy", color = Color(0xFF9C27B0))
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            }
        }
    }
}

/**
 * Agent add/edit dialog.
 */
@Composable
private fun AgentEditDialog(
    agent: AiAgent?,
    aiSettings: AiSettings,
    availableChatGptModels: List<String>,
    availableGeminiModels: List<String>,
    availableGrokModels: List<String>,
    availableDeepSeekModels: List<String>,
    availableMistralModels: List<String>,
    availablePerplexityModels: List<String>,
    availableTogetherModels: List<String>,
    availableOpenRouterModels: List<String>,
    existingNames: Set<String>,
    onSave: (AiAgent) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = agent != null

    // State
    var name by remember { mutableStateOf(agent?.name ?: "") }
    var selectedProvider by remember { mutableStateOf(agent?.provider ?: AiService.CHATGPT) }
    var model by remember { mutableStateOf(agent?.model ?: "gpt-4o-mini") }
    var apiKey by remember { mutableStateOf(agent?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var gamePromptId by remember { mutableStateOf(agent?.gamePromptId ?: aiSettings.prompts.firstOrNull()?.id ?: "") }
    var serverPlayerPromptId by remember { mutableStateOf(agent?.serverPlayerPromptId ?: aiSettings.prompts.firstOrNull()?.id ?: "") }
    var otherPlayerPromptId by remember { mutableStateOf(agent?.otherPlayerPromptId ?: aiSettings.prompts.firstOrNull()?.id ?: "") }

    // Get models for selected provider
    val modelsForProvider = when (selectedProvider) {
        AiService.CHATGPT -> {
            val apiModels = if (aiSettings.chatGptModelSource == ModelSource.API) availableChatGptModels else emptyList()
            val manualModels = if (aiSettings.chatGptModelSource == ModelSource.MANUAL) aiSettings.chatGptManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.CLAUDE -> aiSettings.claudeManualModels.ifEmpty { CLAUDE_MODELS }
        AiService.GEMINI -> {
            val apiModels = if (aiSettings.geminiModelSource == ModelSource.API) availableGeminiModels else emptyList()
            val manualModels = if (aiSettings.geminiModelSource == ModelSource.MANUAL) aiSettings.geminiManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.GROK -> {
            val apiModels = if (aiSettings.grokModelSource == ModelSource.API) availableGrokModels else emptyList()
            val manualModels = if (aiSettings.grokModelSource == ModelSource.MANUAL) aiSettings.grokManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.DEEPSEEK -> {
            val apiModels = if (aiSettings.deepSeekModelSource == ModelSource.API) availableDeepSeekModels else emptyList()
            val manualModels = if (aiSettings.deepSeekModelSource == ModelSource.MANUAL) aiSettings.deepSeekManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.MISTRAL -> {
            val apiModels = if (aiSettings.mistralModelSource == ModelSource.API) availableMistralModels else emptyList()
            val manualModels = if (aiSettings.mistralModelSource == ModelSource.MANUAL) aiSettings.mistralManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.PERPLEXITY -> aiSettings.perplexityManualModels.ifEmpty { PERPLEXITY_MODELS }
        AiService.TOGETHER -> {
            val apiModels = if (aiSettings.togetherModelSource == ModelSource.API) availableTogetherModels else emptyList()
            val manualModels = if (aiSettings.togetherModelSource == ModelSource.MANUAL) aiSettings.togetherManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.OPENROUTER -> {
            val apiModels = if (aiSettings.openRouterModelSource == ModelSource.API) availableOpenRouterModels else emptyList()
            val manualModels = if (aiSettings.openRouterModelSource == ModelSource.MANUAL) aiSettings.openRouterManualModels else emptyList()
            (apiModels + manualModels).ifEmpty { listOf(model) }
        }
        AiService.DUMMY -> aiSettings.dummyManualModels.ifEmpty { listOf("dummy-model") }
    }

    // Update model when provider changes
    LaunchedEffect(selectedProvider) {
        if (!isEditing || agent?.provider != selectedProvider) {
            model = modelsForProvider.firstOrNull() ?: getDefaultModelForProvider(selectedProvider)
        }
    }

    // Validation
    val nameError = when {
        name.isBlank() -> "Name is required"
        !isEditing && existingNames.contains(name) -> "Name already exists"
        isEditing && name != agent?.name && existingNames.contains(name) -> "Name already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Agent" else "Add Agent",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Agent Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = Color(0xFFF44336)) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Provider dropdown
                Text(
                    text = "Provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAAAAA)
                )
                var providerExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { providerExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedProvider.displayName,
                            modifier = Modifier.weight(1f)
                        )
                        Text(if (providerExpanded) "▲" else "▼")
                    }
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        AiService.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    selectedProvider = provider
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                // Model dropdown
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAAAAA)
                )
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { modelExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = model,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(if (modelExpanded) "▲" else "▼")
                    }
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        modelsForProvider.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    model = m
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                // API Key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation()
                    )
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Hide" else "Show", color = Color(0xFF6B9BFF))
                    }
                }

                // Prompt selections (only if prompts exist)
                if (aiSettings.prompts.isNotEmpty()) {
                    HorizontalDivider(color = Color(0xFF444444))

                    Text(
                        text = "Prompts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    PromptDropdown(
                        label = "Game Prompt",
                        selectedId = gamePromptId,
                        prompts = aiSettings.prompts,
                        onSelect = { gamePromptId = it }
                    )

                    PromptDropdown(
                        label = "Server Player Prompt",
                        selectedId = serverPlayerPromptId,
                        prompts = aiSettings.prompts,
                        onSelect = { serverPlayerPromptId = it }
                    )

                    PromptDropdown(
                        label = "Other Player Prompt",
                        selectedId = otherPlayerPromptId,
                        prompts = aiSettings.prompts,
                        onSelect = { otherPlayerPromptId = it }
                    )
                } else {
                    Text(
                        text = "⚠ No prompts available. Create prompts first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAgent = AiAgent(
                        id = agent?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        provider = selectedProvider,
                        model = model,
                        apiKey = apiKey.trim(),
                        gamePromptId = gamePromptId,
                        serverPlayerPromptId = serverPlayerPromptId,
                        otherPlayerPromptId = otherPlayerPromptId
                    )
                    onSave(newAgent)
                },
                enabled = nameError == null && (aiSettings.prompts.isNotEmpty() || apiKey.isBlank())
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Prompt dropdown selector.
 */
@Composable
private fun PromptDropdown(
    label: String,
    selectedId: String,
    prompts: List<AiPrompt>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPrompt = prompts.find { it.id == selectedId }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFAAAAAA)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedPrompt?.name ?: "Select...",
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "▲" else "▼")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                prompts.forEach { prompt ->
                    DropdownMenuItem(
                        text = { Text(prompt.name) },
                        onClick = {
                            onSelect(prompt.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Get default model for a provider.
 */
private fun getDefaultModelForProvider(provider: AiService): String {
    return when (provider) {
        AiService.CHATGPT -> "gpt-4o-mini"
        AiService.CLAUDE -> "claude-sonnet-4-20250514"
        AiService.GEMINI -> "gemini-2.0-flash"
        AiService.GROK -> "grok-3-mini"
        AiService.DEEPSEEK -> "deepseek-chat"
        AiService.MISTRAL -> "mistral-small-latest"
        AiService.PERPLEXITY -> "sonar"
        AiService.TOGETHER -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"
        AiService.OPENROUTER -> "anthropic/claude-3.5-sonnet"
        AiService.DUMMY -> "dummy"
    }
}
