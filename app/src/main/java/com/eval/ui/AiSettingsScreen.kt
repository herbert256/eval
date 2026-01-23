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
import com.google.gson.JsonSyntaxException

/**
 * AI Settings data class for storing API keys for various AI services.
 */
/**
 * Default prompt template for AI chess analysis.
 */
const val DEFAULT_AI_PROMPT = """You are an expert chess analyst. Analyze the following chess position given in FEN notation.

FEN: @FEN@

Please provide:
1. A brief assessment of the position (who is better and why)
2. Key strategic themes and plans for the side to play

No need to use an chess engine to look for tactical opportunities, Stockfish is already doing that for me.

Keep your analysis concise but insightful, suitable for a chess player looking to understand the position better."""

data class AiSettings(
    val chatGptApiKey: String = "",
    val chatGptModel: String = "gpt-4o-mini",
    val chatGptPrompt: String = DEFAULT_AI_PROMPT,
    val claudeApiKey: String = "",
    val claudeModel: String = "claude-sonnet-4-20250514",
    val claudePrompt: String = DEFAULT_AI_PROMPT,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.0-flash",
    val geminiPrompt: String = DEFAULT_AI_PROMPT,
    val grokApiKey: String = "",
    val grokModel: String = "grok-3-mini",
    val grokPrompt: String = DEFAULT_AI_PROMPT,
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = "deepseek-chat",
    val deepSeekPrompt: String = DEFAULT_AI_PROMPT,
    val mistralApiKey: String = "",
    val mistralModel: String = "mistral-small-latest",
    val mistralPrompt: String = DEFAULT_AI_PROMPT,
    val dummyEnabled: Boolean = false
) {
    fun getApiKey(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptApiKey
            AiService.CLAUDE -> claudeApiKey
            AiService.GEMINI -> geminiApiKey
            AiService.GROK -> grokApiKey
            AiService.DEEPSEEK -> deepSeekApiKey
            AiService.MISTRAL -> mistralApiKey
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
            AiService.DUMMY -> ""
        }
    }

    fun getPrompt(service: AiService): String {
        return when (service) {
            AiService.CHATGPT -> chatGptPrompt
            AiService.CLAUDE -> claudePrompt
            AiService.GEMINI -> geminiPrompt
            AiService.GROK -> grokPrompt
            AiService.DEEPSEEK -> deepSeekPrompt
            AiService.MISTRAL -> mistralPrompt
            AiService.DUMMY -> ""
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
            AiService.DUMMY -> this
        }
    }

    fun hasAnyApiKey(): Boolean {
        return chatGptApiKey.isNotBlank() ||
                claudeApiKey.isNotBlank() ||
                geminiApiKey.isNotBlank() ||
                grokApiKey.isNotBlank() ||
                deepSeekApiKey.isNotBlank() ||
                mistralApiKey.isNotBlank() ||
                dummyEnabled
    }

    fun getConfiguredServices(): List<AiService> {
        return AiService.entries.filter { getApiKey(it).isNotBlank() }
    }
}

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

        // ChatGPT
        AiServiceNavigationCard(
            title = "ChatGPT",
            subtitle = "OpenAI",
            accentColor = Color(0xFF10A37F),
            isConfigured = aiSettings.chatGptApiKey.isNotBlank(),
            selectedModel = if (aiSettings.chatGptApiKey.isNotBlank()) aiSettings.chatGptModel else null,
            onClick = { onNavigate(SettingsSubScreen.AI_CHATGPT) }
        )

        // Claude
        AiServiceNavigationCard(
            title = "Claude",
            subtitle = "Anthropic",
            accentColor = Color(0xFFD97706),
            isConfigured = aiSettings.claudeApiKey.isNotBlank(),
            selectedModel = if (aiSettings.claudeApiKey.isNotBlank()) aiSettings.claudeModel else null,
            onClick = { onNavigate(SettingsSubScreen.AI_CLAUDE) }
        )

        // Gemini
        AiServiceNavigationCard(
            title = "Gemini",
            subtitle = "Google",
            accentColor = Color(0xFF4285F4),
            isConfigured = aiSettings.geminiApiKey.isNotBlank(),
            selectedModel = if (aiSettings.geminiApiKey.isNotBlank()) aiSettings.geminiModel else null,
            onClick = { onNavigate(SettingsSubScreen.AI_GEMINI) }
        )

        // Grok
        AiServiceNavigationCard(
            title = "Grok",
            subtitle = "xAI",
            accentColor = Color(0xFFFFFFFF),
            isConfigured = aiSettings.grokApiKey.isNotBlank(),
            selectedModel = if (aiSettings.grokApiKey.isNotBlank()) aiSettings.grokModel else null,
            onClick = { onNavigate(SettingsSubScreen.AI_GROK) }
        )

        // DeepSeek
        AiServiceNavigationCard(
            title = "DeepSeek",
            subtitle = "DeepSeek AI",
            accentColor = Color(0xFF4D6BFE),
            isConfigured = aiSettings.deepSeekApiKey.isNotBlank(),
            selectedModel = if (aiSettings.deepSeekApiKey.isNotBlank()) aiSettings.deepSeekModel else null,
            onClick = { onNavigate(SettingsSubScreen.AI_DEEPSEEK) }
        )

        // Mistral
        AiServiceNavigationCard(
            title = "Mistral",
            subtitle = "Mistral AI",
            accentColor = Color(0xFFFF7000),
            isConfigured = aiSettings.mistralApiKey.isNotBlank(),
            selectedModel = if (aiSettings.mistralApiKey.isNotBlank()) aiSettings.mistralModel else null,
            onClick = { onNavigate(SettingsSubScreen.AI_MISTRAL) }
        )

        // Dummy (for testing)
        AiServiceNavigationCard(
            title = "Dummy",
            subtitle = "For testing",
            accentColor = Color(0xFF888888),
            isConfigured = aiSettings.dummyEnabled,
            selectedModel = if (aiSettings.dummyEnabled) "Test mode" else null,
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
 */
private data class AiServiceConfig(
    val name: String,
    val apiKey: String,
    val model: String,
    val prompt: String
)

/**
 * Data class for the complete AI configuration export.
 */
private data class AiConfigExport(
    val version: Int = 1,
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
            prompt = aiSettings.chatGptPrompt
        ))
    }

    if (aiSettings.claudeApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Claude",
            apiKey = aiSettings.claudeApiKey,
            model = aiSettings.claudeModel,
            prompt = aiSettings.claudePrompt
        ))
    }

    if (aiSettings.geminiApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Gemini",
            apiKey = aiSettings.geminiApiKey,
            model = aiSettings.geminiModel,
            prompt = aiSettings.geminiPrompt
        ))
    }

    if (aiSettings.grokApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Grok",
            apiKey = aiSettings.grokApiKey,
            model = aiSettings.grokModel,
            prompt = aiSettings.grokPrompt
        ))
    }

    if (aiSettings.deepSeekApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "DeepSeek",
            apiKey = aiSettings.deepSeekApiKey,
            model = aiSettings.deepSeekModel,
            prompt = aiSettings.deepSeekPrompt
        ))
    }

    if (aiSettings.mistralApiKey.isNotBlank()) {
        services.add(AiServiceConfig(
            name = "Mistral",
            apiKey = aiSettings.mistralApiKey,
            model = aiSettings.mistralModel,
            prompt = aiSettings.mistralPrompt
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
            settings = when (service.name) {
                "ChatGPT" -> settings.copy(
                    chatGptApiKey = service.apiKey,
                    chatGptModel = service.model,
                    chatGptPrompt = service.prompt
                )
                "Claude" -> settings.copy(
                    claudeApiKey = service.apiKey,
                    claudeModel = service.model,
                    claudePrompt = service.prompt
                )
                "Gemini" -> settings.copy(
                    geminiApiKey = service.apiKey,
                    geminiModel = service.model,
                    geminiPrompt = service.prompt
                )
                "Grok" -> settings.copy(
                    grokApiKey = service.apiKey,
                    grokModel = service.model,
                    grokPrompt = service.prompt
                )
                "DeepSeek" -> settings.copy(
                    deepSeekApiKey = service.apiKey,
                    deepSeekModel = service.model,
                    deepSeekPrompt = service.prompt
                )
                "Mistral" -> settings.copy(
                    mistralApiKey = service.apiKey,
                    mistralModel = service.model,
                    mistralPrompt = service.prompt
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
    isConfigured: Boolean,
    selectedModel: String?,
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
            // Color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(accentColor, shape = MaterialTheme.shapes.small)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAAAAA)
                )
                if (selectedModel != null) {
                    Text(
                        text = selectedModel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B9BFF)
                    )
                }
            }

            if (isConfigured) {
                Text(
                    text = "Configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00E676)
                )
            }

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
    var apiKey by remember { mutableStateOf(aiSettings.chatGptApiKey) }
    var selectedModel by remember { mutableStateOf(aiSettings.chatGptModel) }
    var prompt by remember { mutableStateOf(aiSettings.chatGptPrompt) }
    var showKey by remember { mutableStateOf(false) }

    AiServiceSettingsScreenTemplate(
        title = "ChatGPT",
        subtitle = "OpenAI",
        accentColor = Color(0xFF10A37F),
        apiKey = apiKey,
        showKey = showKey,
        onApiKeyChange = {
            apiKey = it
            onSave(aiSettings.copy(chatGptApiKey = it.trim()))
        },
        onToggleVisibility = { showKey = !showKey },
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        // Model selection
        if (apiKey.isNotBlank()) {
            ModelSelectionSection(
                selectedModel = selectedModel,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                onModelChange = {
                    selectedModel = it
                    onSave(aiSettings.copy(chatGptModel = it))
                },
                onFetchModels = { onFetchModels(apiKey) }
            )

            // Prompt editing
            PromptEditSection(
                prompt = prompt,
                onPromptChange = {
                    prompt = it
                    onSave(aiSettings.copy(chatGptPrompt = it))
                },
                onResetToDefault = {
                    prompt = DEFAULT_AI_PROMPT
                    onSave(aiSettings.copy(chatGptPrompt = DEFAULT_AI_PROMPT))
                }
            )
        }
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
    var apiKey by remember { mutableStateOf(aiSettings.claudeApiKey) }
    var selectedModel by remember { mutableStateOf(aiSettings.claudeModel) }
    var prompt by remember { mutableStateOf(aiSettings.claudePrompt) }
    var showKey by remember { mutableStateOf(false) }

    AiServiceSettingsScreenTemplate(
        title = "Claude",
        subtitle = "Anthropic",
        accentColor = Color(0xFFD97706),
        apiKey = apiKey,
        showKey = showKey,
        onApiKeyChange = {
            apiKey = it
            onSave(aiSettings.copy(claudeApiKey = it.trim()))
        },
        onToggleVisibility = { showKey = !showKey },
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        // Model selection (hardcoded list)
        if (apiKey.isNotBlank()) {
            HardcodedModelSelectionSection(
                selectedModel = selectedModel,
                availableModels = CLAUDE_MODELS,
                onModelChange = {
                    selectedModel = it
                    onSave(aiSettings.copy(claudeModel = it))
                }
            )

            // Prompt editing
            PromptEditSection(
                prompt = prompt,
                onPromptChange = {
                    prompt = it
                    onSave(aiSettings.copy(claudePrompt = it))
                },
                onResetToDefault = {
                    prompt = DEFAULT_AI_PROMPT
                    onSave(aiSettings.copy(claudePrompt = DEFAULT_AI_PROMPT))
                }
            )
        }
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
    var apiKey by remember { mutableStateOf(aiSettings.geminiApiKey) }
    var selectedModel by remember { mutableStateOf(aiSettings.geminiModel) }
    var prompt by remember { mutableStateOf(aiSettings.geminiPrompt) }
    var showKey by remember { mutableStateOf(false) }

    AiServiceSettingsScreenTemplate(
        title = "Gemini",
        subtitle = "Google",
        accentColor = Color(0xFF4285F4),
        apiKey = apiKey,
        showKey = showKey,
        onApiKeyChange = {
            apiKey = it
            onSave(aiSettings.copy(geminiApiKey = it.trim()))
        },
        onToggleVisibility = { showKey = !showKey },
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        // Model selection
        if (apiKey.isNotBlank()) {
            ModelSelectionSection(
                selectedModel = selectedModel,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                onModelChange = {
                    selectedModel = it
                    onSave(aiSettings.copy(geminiModel = it))
                },
                onFetchModels = { onFetchModels(apiKey) }
            )

            // Prompt editing
            PromptEditSection(
                prompt = prompt,
                onPromptChange = {
                    prompt = it
                    onSave(aiSettings.copy(geminiPrompt = it))
                },
                onResetToDefault = {
                    prompt = DEFAULT_AI_PROMPT
                    onSave(aiSettings.copy(geminiPrompt = DEFAULT_AI_PROMPT))
                }
            )
        }
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
    var apiKey by remember { mutableStateOf(aiSettings.grokApiKey) }
    var selectedModel by remember { mutableStateOf(aiSettings.grokModel) }
    var prompt by remember { mutableStateOf(aiSettings.grokPrompt) }
    var showKey by remember { mutableStateOf(false) }

    AiServiceSettingsScreenTemplate(
        title = "Grok",
        subtitle = "xAI",
        accentColor = Color(0xFFFFFFFF),
        apiKey = apiKey,
        showKey = showKey,
        onApiKeyChange = {
            apiKey = it
            onSave(aiSettings.copy(grokApiKey = it.trim()))
        },
        onToggleVisibility = { showKey = !showKey },
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        // Model selection
        if (apiKey.isNotBlank()) {
            ModelSelectionSection(
                selectedModel = selectedModel,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                onModelChange = {
                    selectedModel = it
                    onSave(aiSettings.copy(grokModel = it))
                },
                onFetchModels = { onFetchModels(apiKey) }
            )

            // Prompt editing
            PromptEditSection(
                prompt = prompt,
                onPromptChange = {
                    prompt = it
                    onSave(aiSettings.copy(grokPrompt = it))
                },
                onResetToDefault = {
                    prompt = DEFAULT_AI_PROMPT
                    onSave(aiSettings.copy(grokPrompt = DEFAULT_AI_PROMPT))
                }
            )
        }
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
    var apiKey by remember { mutableStateOf(aiSettings.deepSeekApiKey) }
    var selectedModel by remember { mutableStateOf(aiSettings.deepSeekModel) }
    var prompt by remember { mutableStateOf(aiSettings.deepSeekPrompt) }
    var showKey by remember { mutableStateOf(false) }

    AiServiceSettingsScreenTemplate(
        title = "DeepSeek",
        subtitle = "DeepSeek AI",
        accentColor = Color(0xFF4D6BFE),
        apiKey = apiKey,
        showKey = showKey,
        onApiKeyChange = {
            apiKey = it
            onSave(aiSettings.copy(deepSeekApiKey = it.trim()))
        },
        onToggleVisibility = { showKey = !showKey },
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        // Model selection
        if (apiKey.isNotBlank()) {
            ModelSelectionSection(
                selectedModel = selectedModel,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                onModelChange = {
                    selectedModel = it
                    onSave(aiSettings.copy(deepSeekModel = it))
                },
                onFetchModels = { onFetchModels(apiKey) }
            )

            // Prompt editing
            PromptEditSection(
                prompt = prompt,
                onPromptChange = {
                    prompt = it
                    onSave(aiSettings.copy(deepSeekPrompt = it))
                },
                onResetToDefault = {
                    prompt = DEFAULT_AI_PROMPT
                    onSave(aiSettings.copy(deepSeekPrompt = DEFAULT_AI_PROMPT))
                }
            )
        }
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
    var apiKey by remember { mutableStateOf(aiSettings.mistralApiKey) }
    var selectedModel by remember { mutableStateOf(aiSettings.mistralModel) }
    var prompt by remember { mutableStateOf(aiSettings.mistralPrompt) }
    var showKey by remember { mutableStateOf(false) }

    AiServiceSettingsScreenTemplate(
        title = "Mistral",
        subtitle = "Mistral AI",
        accentColor = Color(0xFFFF7000),
        apiKey = apiKey,
        showKey = showKey,
        onApiKeyChange = {
            apiKey = it
            onSave(aiSettings.copy(mistralApiKey = it.trim()))
        },
        onToggleVisibility = { showKey = !showKey },
        onBackToAiSettings = onBackToAiSettings,
        onBackToGame = onBackToGame
    ) {
        // Model selection
        if (apiKey.isNotBlank()) {
            ModelSelectionSection(
                selectedModel = selectedModel,
                availableModels = availableModels,
                isLoadingModels = isLoadingModels,
                onModelChange = {
                    selectedModel = it
                    onSave(aiSettings.copy(mistralModel = it))
                },
                onFetchModels = { onFetchModels(apiKey) }
            )

            // Prompt editing
            PromptEditSection(
                prompt = prompt,
                onPromptChange = {
                    prompt = it
                    onSave(aiSettings.copy(mistralPrompt = it))
                },
                onResetToDefault = {
                    prompt = DEFAULT_AI_PROMPT
                    onSave(aiSettings.copy(mistralPrompt = DEFAULT_AI_PROMPT))
                }
            )
        }
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
                    text = "Dummy",
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
                Text("< AI Settings")
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

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSettings) {
                Text("< AI Settings")
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
    apiKey: String,
    showKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
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
                    text = title,
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
                Text("< AI Settings")
            }
            OutlinedButton(onClick = onBackToGame) {
                Text("< Back to game")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // API Key card
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
                    text = "API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                if (apiKey.isNotBlank()) {
                    Text(
                        text = "Configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00E676)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        placeholder = {
                            Text(
                                text = "Enter API key...",
                                color = Color(0xFF666666)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF555555)
                        )
                    )
                    TextButton(onClick = onToggleVisibility) {
                        Text(
                            text = if (showKey) "Hide" else "Show",
                            color = Color(0xFF6B9BFF)
                        )
                    }
                }

                Text(
                    text = "Your API key is stored locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888)
                )
            }
        }

        // Additional content (model selection, etc.)
        additionalContent()

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom back buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToAiSettings) {
                Text("< AI Settings")
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
                text = "Selected model:",
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
                text = "Selected model:",
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
 * Prompt editing section for AI service settings.
 */
@Composable
fun PromptEditSection(
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
                    text = "Custom Prompt",
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

            Text(
                text = "Use @FEN@ where you want the position to be inserted",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
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
