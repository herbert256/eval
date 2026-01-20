package com.chessreplay.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main game screen composable that handles game selection and display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var lichessUsername by remember { mutableStateOf(viewModel.savedLichessUsername) }
    var lichessGamesCount by remember { mutableStateOf(uiState.lichessMaxGames.toString()) }
    val focusManager = LocalFocusManager.current

    // Keep screen on during Preview and Analyse stages
    val view = LocalView.current
    DisposableEffect(uiState.currentStage) {
        if (uiState.currentStage != AnalysisStage.MANUAL) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Show settings screen or main game screen
    if (uiState.showSettingsDialog) {
        SettingsScreen(
            stockfishSettings = uiState.stockfishSettings,
            onBack = { viewModel.hideSettingsDialog() },
            onSaveStockfish = { viewModel.updateStockfishSettings(it) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))  // Lighter dark gray background
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title row with buttons (when game loaded) and settings button
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.game != null) {
                // Two buttons on the left
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-4).dp)) {
                    // Reload last game from active server
                    IconButton(onClick = { viewModel.reloadLastGame() }) {
                        Text("↻", fontSize = 34.sp, lineHeight = 34.sp, modifier = Modifier.offset(y = (-3).dp))
                    }
                    // Show retrieve games view
                    IconButton(onClick = { viewModel.clearGame() }) {
                        Text("≡", fontSize = 34.sp, lineHeight = 34.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(96.dp))
            }
            Text(
                text = "Chess Replay",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.showSettingsDialog() }) {
                Text("⚙", fontSize = 30.sp, lineHeight = 30.sp)
            }
        }

        // Stage indicator - only show during Preview and Analyse stages
        if (uiState.game != null && uiState.currentStage != AnalysisStage.MANUAL) {
            val isPreviewStage = uiState.currentStage == AnalysisStage.PREVIEW
            val stageText = if (isPreviewStage) "Preview stage" else "Analyse stage"
            val stageColor = if (isPreviewStage) Color(0xFFFFAA00) else Color(0xFF6B9BFF)

            if (isPreviewStage) {
                // Preview stage: not clickable, just a label
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(stageColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stageText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = stageColor
                    )
                }
            } else {
                // Analyse stage: clickable, enters Manual stage at biggest change
                Button(
                    onClick = { viewModel.enterManualStageAtBiggestChange() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = stageColor.copy(alpha = 0.2f),
                        contentColor = stageColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "$stageText - Click for manual stage",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Search section - only show when no game is loaded
        if (uiState.game == null) {
            // Subtitle
            Text(
                text = "Enter Lichess Username",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Error message
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ===== LICHESS CARD =====
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "lichess.org",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE0E0E0)
                    )

                    // Username field
                    OutlinedTextField(
                        value = lichessUsername,
                        onValueChange = { lichessUsername = it },
                        placeholder = { Text("Enter username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFF555555),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Games count field
                    OutlinedTextField(
                        value = lichessGamesCount,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            lichessGamesCount = filtered
                            filtered.toIntOrNull()?.let { count ->
                                viewModel.setLichessMaxGames(count)
                            }
                        },
                        label = { Text("Number of games") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFF555555),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val lichessCount = lichessGamesCount.toIntOrNull() ?: uiState.lichessMaxGames
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (lichessUsername.isNotBlank()) {
                                    viewModel.fetchGames(lichessUsername, lichessCount)
                                }
                            },
                            enabled = !uiState.isLoading && lichessUsername.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retrieve last $lichessCount games")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (lichessUsername.isNotBlank()) {
                                    viewModel.fetchGames(lichessUsername, 1)
                                }
                            },
                            enabled = !uiState.isLoading && lichessUsername.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retrieve last game")
                        }
                    }
                }
            }

            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fetching games...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Game selection dialog
        if (uiState.showGameSelection && uiState.gameList.isNotEmpty()) {
            GameSelectionDialog(
                games = uiState.gameList,
                onSelectGame = { viewModel.selectGame(it) },
                onDismiss = { viewModel.dismissGameSelection() }
            )
        }

        // Game content
        if (uiState.game != null) {
            GameContent(uiState = uiState, viewModel = viewModel)
        }
    }
}
