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
    AI_PROMPTS     // Prompts for external AI app
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
    aiPromptsSettings: AiPromptsSettings,
    onBack: () -> Unit,
    onSaveStockfish: (StockfishSettings) -> Unit,
    onSaveBoardLayout: (BoardLayoutSettings) -> Unit,
    onSaveGraph: (GraphSettings) -> Unit,
    onSaveInterfaceVisibility: (InterfaceVisibilitySettings) -> Unit,
    onSaveGeneral: (GeneralSettings) -> Unit,
    onSaveAiPrompts: (AiPromptsSettings) -> Unit
) {
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    // Handle Android back button
    BackHandler {
        when (currentSubScreen) {
            SettingsSubScreen.MAIN -> onBack()
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
        SettingsSubScreen.AI_PROMPTS -> AiPromptsSettingsScreen(
            aiPromptsSettings = aiPromptsSettings,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onSave = onSaveAiPrompts
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
        // Title bar
        EvalTitleBar(
            title = "Settings",
            onBackClick = onBack,
            onEvalClick = onBack
        )

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

        // AI Prompts settings card
        SettingsNavigationCard(
            title = "AI Prompts",
            description = "Configure prompts for AI analysis",
            onClick = { onNavigate(SettingsSubScreen.AI_PROMPTS) }
        )

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
 * AI Prompts settings screen - simple 3 text fields for prompts.
 */
@Composable
fun AiPromptsSettingsScreen(
    aiPromptsSettings: AiPromptsSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiPromptsSettings) -> Unit
) {
    var gamePrompt by remember { mutableStateOf(aiPromptsSettings.getGamePromptText()) }
    var serverPlayerPrompt by remember { mutableStateOf(aiPromptsSettings.getServerPlayerPromptText()) }
    var otherPlayerPrompt by remember { mutableStateOf(aiPromptsSettings.getOtherPlayerPromptText()) }

    // Save when leaving
    DisposableEffect(Unit) {
        onDispose {
            onSave(AiPromptsSettings(
                gamePrompt = gamePrompt,
                serverPlayerPrompt = serverPlayerPrompt,
                otherPlayerPrompt = otherPlayerPrompt
            ))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EvalTitleBar(
            title = "AI Prompts",
            onBackClick = onBackToSettings,
            onEvalClick = onBackToGame
        )

        // Info text
        Text(
            text = "Prompts are sent to the external AI app. Use placeholders: @FEN@, @PLAYER@, @SERVER@, @DATE@",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )

        // Game Analysis prompt
        Text(
            text = "Game Analysis Prompt",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = gamePrompt,
            onValueChange = { gamePrompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Server Player prompt
        Text(
            text = "Server Player Prompt (Lichess/Chess.com)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = serverPlayerPrompt,
            onValueChange = { serverPlayerPrompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Other Player prompt
        Text(
            text = "Other Player Prompt",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = otherPlayerPrompt,
            onValueChange = { otherPlayerPrompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Reset to defaults button
        TextButton(
            onClick = {
                gamePrompt = DEFAULT_GAME_PROMPT
                serverPlayerPrompt = DEFAULT_SERVER_PLAYER_PROMPT
                otherPlayerPrompt = DEFAULT_OTHER_PLAYER_PROMPT
            }
        ) {
            Text("Reset to defaults")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
