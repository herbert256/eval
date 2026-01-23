package com.eval.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings sub-screen navigation enum.
 */
enum class SettingsSubScreen {
    MAIN,
    GENERAL_SETTINGS,
    ARROW_SETTINGS,
    STOCKFISH,
    BOARD_LAYOUT,
    GRAPH_SETTINGS,
    INTERFACE_VISIBILITY,
    AI_SETTINGS,
    AI_CHATGPT,
    AI_CLAUDE,
    AI_GEMINI,
    AI_GROK,
    AI_DEEPSEEK,
    AI_MISTRAL,
    AI_DUMMY
}

/**
 * Root settings screen that manages navigation between settings sub-screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    stockfishSettings: StockfishSettings,
    boardLayoutSettings: BoardLayoutSettings,
    graphSettings: GraphSettings,
    interfaceVisibility: InterfaceVisibilitySettings,
    generalSettings: GeneralSettings,
    aiSettings: AiSettings,
    availableChatGptModels: List<String>,
    isLoadingChatGptModels: Boolean,
    availableGeminiModels: List<String>,
    isLoadingGeminiModels: Boolean,
    availableGrokModels: List<String>,
    isLoadingGrokModels: Boolean,
    availableDeepSeekModels: List<String>,
    isLoadingDeepSeekModels: Boolean,
    availableMistralModels: List<String>,
    isLoadingMistralModels: Boolean,
    onBack: () -> Unit,
    onSaveStockfish: (StockfishSettings) -> Unit,
    onSaveBoardLayout: (BoardLayoutSettings) -> Unit,
    onSaveGraph: (GraphSettings) -> Unit,
    onSaveInterfaceVisibility: (InterfaceVisibilitySettings) -> Unit,
    onSaveGeneral: (GeneralSettings) -> Unit,
    onSaveAi: (AiSettings) -> Unit,
    onFetchChatGptModels: (String) -> Unit,
    onFetchGeminiModels: (String) -> Unit,
    onFetchGrokModels: (String) -> Unit,
    onFetchDeepSeekModels: (String) -> Unit,
    onFetchMistralModels: (String) -> Unit
) {
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    // Handle Android back button
    BackHandler {
        when (currentSubScreen) {
            SettingsSubScreen.MAIN -> onBack()
            SettingsSubScreen.AI_CHATGPT,
            SettingsSubScreen.AI_CLAUDE,
            SettingsSubScreen.AI_GEMINI,
            SettingsSubScreen.AI_GROK,
            SettingsSubScreen.AI_DEEPSEEK,
            SettingsSubScreen.AI_MISTRAL,
            SettingsSubScreen.AI_DUMMY -> currentSubScreen = SettingsSubScreen.AI_SETTINGS
            else -> currentSubScreen = SettingsSubScreen.MAIN
        }
    }

    when (currentSubScreen) {
        SettingsSubScreen.MAIN -> SettingsMainScreen(
            onBack = onBack,
            onNavigate = { currentSubScreen = it }
        )
        SettingsSubScreen.GENERAL_SETTINGS -> GeneralSettingsScreen(
            generalSettings = generalSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveGeneral
        )
        SettingsSubScreen.ARROW_SETTINGS -> ArrowSettingsScreen(
            stockfishSettings = stockfishSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveStockfish
        )
        SettingsSubScreen.STOCKFISH -> StockfishSettingsScreen(
            stockfishSettings = stockfishSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveStockfish
        )
        SettingsSubScreen.BOARD_LAYOUT -> BoardLayoutSettingsScreen(
            boardLayoutSettings = boardLayoutSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveBoardLayout
        )
        SettingsSubScreen.GRAPH_SETTINGS -> GraphSettingsScreen(
            graphSettings = graphSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveGraph
        )
        SettingsSubScreen.INTERFACE_VISIBILITY -> InterfaceSettingsScreen(
            interfaceVisibility = interfaceVisibility,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveInterfaceVisibility
        )
        SettingsSubScreen.AI_SETTINGS -> AiSettingsScreen(
            aiSettings = aiSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onNavigate = { currentSubScreen = it },
            onSave = onSaveAi
        )
        SettingsSubScreen.AI_CHATGPT -> ChatGptSettingsScreen(
            aiSettings = aiSettings,
            availableModels = availableChatGptModels,
            isLoadingModels = isLoadingChatGptModels,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi,
            onFetchModels = onFetchChatGptModels
        )
        SettingsSubScreen.AI_CLAUDE -> ClaudeSettingsScreen(
            aiSettings = aiSettings,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi
        )
        SettingsSubScreen.AI_GEMINI -> GeminiSettingsScreen(
            aiSettings = aiSettings,
            availableModels = availableGeminiModels,
            isLoadingModels = isLoadingGeminiModels,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi,
            onFetchModels = onFetchGeminiModels
        )
        SettingsSubScreen.AI_GROK -> GrokSettingsScreen(
            aiSettings = aiSettings,
            availableModels = availableGrokModels,
            isLoadingModels = isLoadingGrokModels,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi,
            onFetchModels = onFetchGrokModels
        )
        SettingsSubScreen.AI_DEEPSEEK -> DeepSeekSettingsScreen(
            aiSettings = aiSettings,
            availableModels = availableDeepSeekModels,
            isLoadingModels = isLoadingDeepSeekModels,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi,
            onFetchModels = onFetchDeepSeekModels
        )
        SettingsSubScreen.AI_MISTRAL -> MistralSettingsScreen(
            aiSettings = aiSettings,
            availableModels = availableMistralModels,
            isLoadingModels = isLoadingMistralModels,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi,
            onFetchModels = onFetchMistralModels
        )
        SettingsSubScreen.AI_DUMMY -> DummySettingsScreen(
            aiSettings = aiSettings,
            onBackToAiSettings = { currentSubScreen = SettingsSubScreen.AI_SETTINGS },
            onBackToGame = onBack,
            onSave = onSaveAi
        )
    }
}

/**
 * Main settings menu screen with navigation cards.
 */
@Composable
private fun SettingsMainScreen(
    onBack: () -> Unit,
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
        // Title
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Back button at top
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // General settings card
        SettingsNavigationCard(
            title = "General settings",
            description = "Full screen mode, app-wide settings",
            onClick = { onNavigate(SettingsSubScreen.GENERAL_SETTINGS) }
        )

        // Board layout card
        SettingsNavigationCard(
            title = "Board layout",
            description = "Coordinates, colors, last move highlight",
            onClick = { onNavigate(SettingsSubScreen.BOARD_LAYOUT) }
        )

        // Graph settings card
        SettingsNavigationCard(
            title = "Graph settings",
            description = "Graph colors for scores and lines",
            onClick = { onNavigate(SettingsSubScreen.GRAPH_SETTINGS) }
        )

        // Arrow settings card
        SettingsNavigationCard(
            title = "Arrow settings",
            description = "Arrow display, colors, numbers",
            onClick = { onNavigate(SettingsSubScreen.ARROW_SETTINGS) }
        )

        // Stockfish settings card
        SettingsNavigationCard(
            title = "Stockfish",
            description = "Engine settings for all stages",
            onClick = { onNavigate(SettingsSubScreen.STOCKFISH) }
        )

        // Interface visibility settings card
        SettingsNavigationCard(
            title = "Show interface elements",
            description = "Configure visible UI elements per stage",
            onClick = { onNavigate(SettingsSubScreen.INTERFACE_VISIBILITY) }
        )

        // AI Analysis settings card
        SettingsNavigationCard(
            title = "AI analysis",
            description = "Configure AI service API keys",
            onClick = { onNavigate(SettingsSubScreen.AI_SETTINGS) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Back button at bottom
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Reusable navigation card for settings menu.
 */
@Composable
private fun SettingsNavigationCard(
    title: String,
    description: String,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFAAAAAA)
                )
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF888888)
            )
        }
    }
}

/**
 * Reusable back buttons for settings sub-screens.
 */
@Composable
fun SettingsBackButtons(
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onBackToSettings,
            modifier = Modifier.weight(1f)
        ) {
            Text("Back to settings")
        }
        Button(
            onClick = onBackToGame,
            modifier = Modifier.weight(1f)
        ) {
            Text("Back to game")
        }
    }
}
