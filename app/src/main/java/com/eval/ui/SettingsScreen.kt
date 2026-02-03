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
    AI_PROMPTS,          // Prompts list for external AI app
    AI_PROMPT_EDIT       // Edit a single prompt
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
    aiPrompts: List<AiPromptEntry>,
    onBack: () -> Unit,
    onSaveStockfish: (StockfishSettings) -> Unit,
    onSaveBoardLayout: (BoardLayoutSettings) -> Unit,
    onSaveGraph: (GraphSettings) -> Unit,
    onSaveInterfaceVisibility: (InterfaceVisibilitySettings) -> Unit,
    onSaveGeneral: (GeneralSettings) -> Unit,
    onAddAiPrompt: (AiPromptEntry) -> Unit,
    onUpdateAiPrompt: (AiPromptEntry) -> Unit,
    onDeleteAiPrompt: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (Uri) -> Unit
) {
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }
    var editingPromptId by remember { mutableStateOf<String?>(null) }

    // Handle Android back button
    BackHandler {
        when (currentSubScreen) {
            SettingsSubScreen.MAIN -> onBack()
            SettingsSubScreen.AI_PROMPT_EDIT -> currentSubScreen = SettingsSubScreen.AI_PROMPTS
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
        SettingsSubScreen.AI_PROMPTS -> AiPromptsListScreen(
            prompts = aiPrompts,
            onBackToSettings = { currentSubScreen = SettingsSubScreen.MAIN },
            onBackToGame = onBack,
            onEditPrompt = { id ->
                editingPromptId = id
                currentSubScreen = SettingsSubScreen.AI_PROMPT_EDIT
            },
            onAddPrompt = {
                editingPromptId = null
                currentSubScreen = SettingsSubScreen.AI_PROMPT_EDIT
            },
            onCopyPrompt = { prompt ->
                val copy = prompt.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = prompt.name + " (copy)"
                )
                onAddAiPrompt(copy)
                editingPromptId = copy.id
                currentSubScreen = SettingsSubScreen.AI_PROMPT_EDIT
            },
            onDeletePrompt = onDeleteAiPrompt
        )
        SettingsSubScreen.AI_PROMPT_EDIT -> {
            val existingPrompt = editingPromptId?.let { id -> aiPrompts.firstOrNull { it.id == id } }
            AiPromptEditScreen(
                existingPrompt = existingPrompt,
                onBackToList = { currentSubScreen = SettingsSubScreen.AI_PROMPTS },
                onBackToGame = onBack,
                onSave = { prompt ->
                    if (existingPrompt != null) {
                        onUpdateAiPrompt(prompt)
                    } else {
                        onAddAiPrompt(prompt)
                    }
                    currentSubScreen = SettingsSubScreen.AI_PROMPTS
                }
            )
        }
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
                    containerColor = Color(0xFF6B8E23)
                )
            ) {
                Text("Export")
            }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B8E23)
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
 * AI Prompts list screen - shows all prompts with edit/delete actions.
 */
@Composable
fun AiPromptsListScreen(
    prompts: List<AiPromptEntry>,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onEditPrompt: (String) -> Unit,
    onAddPrompt: () -> Unit,
    onCopyPrompt: (AiPromptEntry) -> Unit,
    onDeletePrompt: (String) -> Unit
) {
    var promptToDelete by remember { mutableStateOf<AiPromptEntry?>(null) }

    // Delete confirmation dialog
    promptToDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { promptToDelete = null },
            title = { Text("Delete Prompt", fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"${prompt.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePrompt(prompt.id)
                    promptToDelete = null
                }) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { promptToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = "AI Prompts",
            onBackClick = onBackToSettings,
            onEvalClick = onBackToGame
        )

        // Info text
        Text(
            text = "Prompts are sent to the external AI app. Use placeholders: @FEN@, @BOARD@, @PLAYER@, @SERVER@, @DATE@",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )

        // Prompt list (sorted by name)
        prompts.sortedBy { it.name.lowercase() }.forEach { prompt ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditPrompt(prompt.id) }
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
                            text = prompt.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = prompt.safeCategory.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B8E23)
                        )
                        Text(
                            text = prompt.prompt.take(80).replace("\n", " ") + if (prompt.prompt.length > 80) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAAAAA),
                            maxLines = 2
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            onCopyPrompt(prompt)
                        }) {
                            Text("\u2398", color = Color(0xFF6B8E23))
                        }
                        TextButton(onClick = { promptToDelete = prompt }) {
                            Text("X", color = Color(0xFFFF5252))
                        }
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
        }

        // Add prompt button
        Button(
            onClick = onAddPrompt,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B8E23)
            )
        ) {
            Text("+ Add Prompt")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * AI Prompt edit screen - edit name, prompt template, and instructions.
 */
@Composable
fun AiPromptEditScreen(
    existingPrompt: AiPromptEntry?,
    onBackToList: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiPromptEntry) -> Unit
) {
    var name by remember { mutableStateOf(existingPrompt?.name ?: "") }
    var category by remember { mutableStateOf(existingPrompt?.safeCategory ?: AiPromptCategory.GAME) }
    var prompt by remember { mutableStateOf(existingPrompt?.prompt ?: "") }
    var instructions by remember { mutableStateOf(existingPrompt?.instructions ?: "") }
    var email by remember { mutableStateOf(existingPrompt?.email ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = if (existingPrompt != null) "Edit Prompt" else "New Prompt",
            onBackClick = onBackToList,
            onEvalClick = onBackToGame
        )

        // Name field
        Text(
            text = "Name",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. Game Analysis") },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        // Category selector
        Text(
            text = "Category",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiPromptCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat.displayName) }
                )
            }
        }

        // Prompt field
        Text(
            text = "Prompt",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Placeholders: @FEN@, @BOARD@, @PLAYER@, @SERVER@, @DATE@",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Instructions field
        Text(
            text = "Instructions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Appended after the prompt, separated by \"-- end prompt --\"",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )
        OutlinedTextField(
            value = instructions,
            onValueChange = { instructions = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Email field
        Text(
            text = "Email",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. user@example.com") },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        // Save button
        Button(
            onClick = {
                if (name.isNotBlank() && prompt.isNotBlank()) {
                    val entry = AiPromptEntry(
                        id = existingPrompt?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        prompt = prompt,
                        instructions = instructions,
                        email = email.trim(),
                        category = category
                    )
                    onSave(entry)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && prompt.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B8E23)
            )
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
