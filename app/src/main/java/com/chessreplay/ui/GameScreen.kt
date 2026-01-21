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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

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
    val context = LocalContext.current

    // Show blocking screen if Stockfish is not installed
    if (!uiState.stockfishInstalled) {
        StockfishNotInstalledScreen(
            onExit = {
                (context as? Activity)?.finish()
            },
            onCheckInstalled = {
                viewModel.checkStockfishInstalled()
            },
            onInstalled = {
                viewModel.initializeStockfish()
            }
        )
        return
    }

    var lichessUsername by remember { mutableStateOf(viewModel.savedLichessUsername) }
    var lichessGamesCount by remember { mutableStateOf(uiState.lichessMaxGames.toString()) }
    val focusManager = LocalFocusManager.current

    // Calculate background color based on game result
    val backgroundColor = remember(uiState.game) {
        val game = uiState.game
        if (game == null) {
            Color(0xFF2A2A2A)  // Default dark gray when no game loaded
        } else {
            val searchedUser = viewModel.savedLichessUsername.lowercase()
            val whitePlayerName = game.players.white.user?.name?.lowercase() ?: ""
            val blackPlayerName = game.players.black.user?.name?.lowercase() ?: ""

            // Determine which color the user played
            val userPlayedWhite = searchedUser.isNotEmpty() && searchedUser == whitePlayerName
            val userPlayedBlack = searchedUser.isNotEmpty() && searchedUser == blackPlayerName

            when {
                game.winner == "white" && userPlayedWhite -> Color(0xFF2A4A2A)  // Light green - user won as white
                game.winner == "black" && userPlayedBlack -> Color(0xFF2A4A2A)  // Light green - user won as black
                game.winner == "white" && userPlayedBlack -> Color(0xFF4A2A2A)  // Light red - user lost as black
                game.winner == "black" && userPlayedWhite -> Color(0xFF4A2A2A)  // Light red - user lost as white
                game.winner == null -> Color(0xFF2A3A4A)  // Light blue - draw
                else -> Color(0xFF2A2A2A)  // Default dark gray
            }
        }
    }

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
            boardLayoutSettings = uiState.boardLayoutSettings,
            onBack = { viewModel.hideSettingsDialog() },
            onSaveStockfish = { viewModel.updateStockfishSettings(it) },
            onSaveBoardLayout = { viewModel.updateBoardLayoutSettings(it) }
        )
        return
    }

    // Show help screen
    if (uiState.showHelpScreen) {
        HelpScreen(
            onBack = { viewModel.hideHelpScreen() }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title row with buttons (when game loaded) and settings button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-8).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Buttons on the left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                if (uiState.game != null) {
                    // Reload last game from active server
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { viewModel.reloadLastGame() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↻", fontSize = 44.sp, color = Color.White, modifier = Modifier.offset(y = (-12).dp))
                    }
                    // Show retrieve games view
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { viewModel.clearGame() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("≡", fontSize = 44.sp, color = Color.White, modifier = Modifier.offset(y = (-12).dp))
                    }
                    // Arrow mode toggle - only show in Manual stage
                    if (uiState.currentStage == AnalysisStage.MANUAL) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { viewModel.cycleArrowMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↗", fontSize = 40.sp, color = Color.White, modifier = Modifier.offset(y = (-11).dp))
                        }
                    }
                }
                // Settings and help icons
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { viewModel.showSettingsDialog() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", fontSize = 30.sp, color = Color.White, modifier = Modifier.offset(y = (-3).dp))
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { viewModel.showHelpScreen() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-3).dp))
                }
            }
            Text(
                text = "Chess Replay",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1
            )
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
                        .offset(y = (-8).dp)
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
                        .offset(y = (-8).dp)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Analys running - tap to end",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = Color.Yellow
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

/**
 * Blocking screen shown when Stockfish is not installed.
 * User must install "Stockfish 17.1 Chess Engine" from Google Play Store.
 * Automatically checks every 2 seconds if Stockfish has been installed.
 */
@Composable
fun StockfishNotInstalledScreen(
    onExit: () -> Unit,
    onCheckInstalled: () -> Boolean,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.stockfish141"

    // Check every 2 seconds if Stockfish has been installed
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            if (onCheckInstalled()) {
                onInstalled()
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Stockfish Not Installed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This app requires the Stockfish chess engine to analyze games.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                // Clickable link to Play Store
                val annotatedText = buildAnnotatedString {
                    append("Please install ")
                    pushStringAnnotation(tag = "URL", annotation = playStoreUrl)
                    withStyle(style = SpanStyle(
                        color = Color(0xFF6B9BFF),
                        textDecoration = TextDecoration.Underline
                    )) {
                        append("Stockfish 17.1 Chess Engine")
                    }
                    pop()
                    append(" from the Google Play Store.")
                }

                ClickableText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    ),
                    onClick = { offset ->
                        annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.item))
                                context.startActivity(intent)
                            }
                    }
                )

                Text(
                    text = "The app will start automatically once Stockfish is installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Exit")
                }
            }
        }
    }
}
