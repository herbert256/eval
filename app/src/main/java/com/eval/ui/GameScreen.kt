package com.eval.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    // Handle full screen mode (using generalSettings.longTapForFullScreen as the state)
    val window = (context as? Activity)?.window
    val isFullScreen = uiState.generalSettings.longTapForFullScreen

    // Track if we've ever entered full screen to avoid modifying window on startup
    var hasBeenFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreen) {
        if (window != null) {
            if (isFullScreen) {
                // Enter full screen mode
                hasBeenFullScreen = true
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else if (hasBeenFullScreen) {
                // Only restore normal mode if we were previously in full screen
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Restore normal mode when composable is disposed (only if we modified it)
    DisposableEffect(Unit) {
        onDispose {
            if (window != null && hasBeenFullScreen) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Show settings screen or main game screen
    if (uiState.showSettingsDialog) {
        SettingsScreen(
            stockfishSettings = uiState.stockfishSettings,
            boardLayoutSettings = uiState.boardLayoutSettings,
            graphSettings = uiState.graphSettings,
            interfaceVisibility = uiState.interfaceVisibility,
            generalSettings = uiState.generalSettings,
            onBack = { viewModel.hideSettingsDialog() },
            onSaveStockfish = { viewModel.updateStockfishSettings(it) },
            onSaveBoardLayout = { viewModel.updateBoardLayoutSettings(it) },
            onSaveGraph = { viewModel.updateGraphSettings(it) },
            onSaveInterfaceVisibility = { viewModel.updateInterfaceVisibilitySettings(it) },
            onSaveGeneral = { viewModel.updateGeneralSettings(it) }
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

    // Show retrieve screen
    if (uiState.showRetrieveScreen) {
        RetrieveScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = { viewModel.hideRetrieveScreen() }
        )
        return
    }

    // Show ActivePlayer validation error popup
    val activePlayerError = uiState.activePlayerError
    if (activePlayerError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissActivePlayerError() },
            title = { Text("ActivePlayer Validation Error") },
            text = {
                Column {
                    Text(activePlayerError)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ActivePlayer: ${uiState.activePlayer ?: "null"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val game = uiState.game
                    if (game != null) {
                        Text(
                            "White: ${game.players.white.user?.name ?: "unknown"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Black: ${game.players.black.user?.name ?: "unknown"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissActivePlayerError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Show game selection screen (full screen, outside scrollable column)
    if (uiState.showGameSelection && uiState.gameList.isNotEmpty()) {
        GameSelectionScreen(
            games = uiState.gameList,
            username = uiState.gameSelectionUsername,
            server = uiState.gameSelectionServer,
            onSelectGame = { viewModel.selectGame(it) },
            onDismiss = { viewModel.dismissGameSelection() }
        )
        return
    }

    // Show analysed games selection screen
    if (uiState.showAnalysedGamesSelection && uiState.analysedGamesList.isNotEmpty()) {
        AnalysedGamesScreen(
            games = uiState.analysedGamesList,
            onSelectGame = { viewModel.selectAnalysedGame(it) },
            onDismiss = { viewModel.dismissAnalysedGamesSelection() }
        )
        return
    }

    // Show previous game retrieves selection screen
    if (uiState.showPreviousRetrievesSelection && uiState.previousRetrievesList.isNotEmpty()) {
        PreviousRetrievesScreen(
            retrieves = uiState.previousRetrievesList,
            onSelectRetrieve = { viewModel.selectPreviousRetrieve(it) },
            onDismiss = { viewModel.dismissPreviousRetrievesSelection() }
        )
        return
    }

    // Show games from selected retrieve
    val selectedRetrieveEntry = uiState.selectedRetrieveEntry
    if (uiState.showSelectedRetrieveGames && selectedRetrieveEntry != null) {
        SelectedRetrieveGamesScreen(
            entry = selectedRetrieveEntry,
            games = uiState.selectedRetrieveGames,
            onSelectGame = { viewModel.selectGameFromRetrieve(it) },
            onDismiss = { viewModel.dismissSelectedRetrieveGames() }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        viewModel.toggleFullScreen()
                    }
                )
            }
    ) {
        // Title row with buttons (when game loaded) and settings button - hidden in full screen mode
        if (!isFullScreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Buttons on the left
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Reload last game from Active player/server (only when game loaded and Active stored)
                    if (uiState.game != null && uiState.hasActive) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { viewModel.reloadLastGame() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↻", fontSize = 44.sp, color = Color.White, modifier = Modifier.offset(y = (-12).dp))
                        }
                    }
                    // Show retrieve games view - always visible
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                if (uiState.game != null) {
                                    viewModel.clearGame()
                                } else {
                                    viewModel.showRetrieveScreen()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("≡", fontSize = 44.sp, color = Color.White, modifier = Modifier.offset(y = (-12).dp))
                    }
                    // Arrow mode toggle - only show in Manual stage when game loaded
                    if (uiState.game != null && uiState.currentStage == AnalysisStage.MANUAL) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { viewModel.cycleArrowMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↗", fontSize = 40.sp, color = Color.White, modifier = Modifier.offset(y = (-11).dp))
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
                    text = "Eval",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }

        // Stage indicator - only show during Preview and Analyse stages, hidden in full screen mode
        if (uiState.game != null && uiState.currentStage != AnalysisStage.MANUAL && !isFullScreen) {
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
                        text = "Analysis running - tap to end",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = Color.Yellow
                    )
                }
            }
        }

        // Main view when no game is loaded - show logo and conditional First card
        if (uiState.game == null) {
            // Logo
            EvalLogo()

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

            // First card - only shown when no Active is stored
            if (!uiState.hasActive) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "First",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Please select a game to analyse, use the \u2261 icon at the top left for this",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCCCCCC),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
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

/**
 * Eval logo displayed on the main screen when no game is loaded.
 * Features a stylized chess-themed design.
 */
@Composable
fun EvalLogo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chess pieces decoration
        Text(
            text = "\u265A \u265B \u265C",
            fontSize = 36.sp,
            color = Color(0xFF6B9BFF),
            letterSpacing = 8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main title
        Text(
            text = "Eval",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 4.sp
        )

        // Subtitle
        Text(
            text = "Chess Game Analyser",
            fontSize = 16.sp,
            color = Color(0xFF888888),
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // More chess pieces
        Text(
            text = "\u265D \u265E \u265F",
            fontSize = 36.sp,
            color = Color(0xFF6B9BFF),
            letterSpacing = 8.sp
        )
    }
}
