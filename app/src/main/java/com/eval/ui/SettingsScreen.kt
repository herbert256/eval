package com.eval.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    AI_SETUP
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
    aiInstructions: List<AiInstructionEntry>,
    onBack: () -> Unit,
    onSaveStockfish: (StockfishSettings) -> Unit,
    onSaveBoardLayout: (BoardLayoutSettings) -> Unit,
    onSaveGraph: (GraphSettings) -> Unit,
    onSaveInterfaceVisibility: (InterfaceVisibilitySettings) -> Unit,
    onSaveGeneral: (GeneralSettings) -> Unit,
    onAddAiInstruction: (AiInstructionEntry) -> Unit,
    onUpdateAiInstruction: (AiInstructionEntry) -> Unit,
    onDeleteAiInstruction: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (Uri) -> Unit,
    aiSystemPrompts: List<AiPromptEntry> = emptyList(),
    aiReportPrompts: List<AiPromptEntry> = emptyList(),
    onSaveAiPrompt: (AiPromptEntry, Boolean) -> Unit = { _, _ -> },
    onDeleteAiPrompt: (String, Boolean) -> Unit = { _, _ -> }
) {
    var currentSubScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.MAIN) }

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
            onNavigate = { currentSubScreen = it },
            onExportSettings = onExportSettings,
            onImportSettings = onImportSettings
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
        SettingsSubScreen.AI_SETUP -> AiSetupScreen(
            systemPrompts = aiSystemPrompts, prompts = aiReportPrompts, instructions = aiInstructions,
            onBack = { currentSubScreen = SettingsSubScreen.MAIN }, onBackToGame = onBack,
            onSavePrompt = onSaveAiPrompt, onDeletePrompt = onDeleteAiPrompt,
            onSaveInstruction = { entry ->
                if (aiInstructions.any { it.id == entry.id }) onUpdateAiInstruction(entry) else onAddAiInstruction(entry)
            }, onDeleteInstruction = onDeleteAiInstruction
        )
    }
}

/**
 * Main settings menu screen with navigation cards.
 */
@Composable
private fun SettingsMainScreen(
    onBack: () -> Unit,
    onNavigate: (SettingsSubScreen) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (Uri) -> Unit
) {
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportSettings(it) }
    }

    EvalScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = {
            EvalTitleBar(
                title = "Settings",
                onBackClick = onBack,
                onEvalClick = onBack
            )
        }
    ) {

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

        // AI Instructions settings card
        SettingsNavigationCard(
            title = "AI setup",
            description = "System prompts, prompts and AI instructions",
            onClick = { onNavigate(SettingsSubScreen.AI_SETUP) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Export / Import buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onExportSettings,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.ButtonGreen
                )
            ) {
                Text("Export")
            }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.ButtonGreen
                )
            ) {
                Text("Import")
            }
        }
    }
}

/**
 * Reusable navigation card for settings menu.
 */
@Composable
internal fun SettingsNavigationCard(
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
                    color = AppColors.SubtleText
                )
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.headlineMedium,
                color = AppColors.MediumGray
            )
        }
    }
}

/**
 * AI Instructions list screen - shows all instructions with edit/delete actions.
 */
@Composable
fun AiInstructionsListScreen(
    instructions: List<AiInstructionEntry>,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onEditInstruction: (String) -> Unit,
    onAddInstruction: () -> Unit,
    onCopyInstruction: (AiInstructionEntry) -> Unit,
    onDeleteInstruction: (String) -> Unit
) {
    var entryToDelete by remember { mutableStateOf<AiInstructionEntry?>(null) }

    BackHandler(enabled = entryToDelete != null) { entryToDelete = null }

    // Delete confirmation (full screen, early return pattern)
    entryToDelete?.let { entry ->
        EvalScreen(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            topBar = {
                EvalTitleBar(
                    title = "Delete Instruction",
                    onBackClick = { entryToDelete = null },
                    onEvalClick = onBackToGame
                )
            }
        ) {

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Delete \"${entry.name}\"?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onDeleteInstruction(entry.id)
                    entryToDelete = null
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }

            TextButton(
                onClick = { entryToDelete = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
        return
    }

    EvalScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = {
            EvalTitleBar(
                title = "AI instructions",
                onBackClick = onBackToSettings,
                onEvalClick = onBackToGame
            )
        }
    ) {

        Text(
            text = "Save reusable AI instruction text here. Choose system prompts and prompts separately when calling AI. Type @ for context or < for AI options.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.MediumGray
        )

        // Instruction list (sorted by name)
        instructions.sortedBy { it.name.lowercase() }.forEach { entry ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditInstruction(entry.id) }
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
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = entry.instructions.take(80).replace("\n", " ") + if (entry.instructions.length > 80) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.SubtleText,
                            maxLines = 2
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            onCopyInstruction(entry)
                        }) {
                            Text("\u2398", color = AppColors.ButtonGreen)
                        }
                        TextButton(onClick = { entryToDelete = entry }) {
                            Text("X", color = AppColors.NegativeRed, modifier = Modifier.semantics { contentDescription = "Delete ${entry.name}" })
                        }
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AppColors.MediumGray
                        )
                    }
                }
            }
        }

        // Add entry button
        Button(
            onClick = onAddInstruction,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.ButtonGreen
            )
        ) {
            Text("+ Add Instruction")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
