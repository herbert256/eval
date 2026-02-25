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
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Main game screen content composable that handles game display.
 * Uses navigation callbacks for full-screen destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreenContent(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToRetrieve: () -> Unit = {}
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

    // Show warning screen if AI app is not installed (can be dismissed)
    if (!uiState.aiAppInstalled && !uiState.aiAppWarningDismissed) {
        AiAppNotInstalledScreen(
            onContinue = {
                viewModel.dismissAiAppWarning()
            },
            onCheckInstalled = {
                viewModel.checkAiAppInstalled()
            },
            onInstalled = {
                // AI app installed, warning will auto-dismiss because aiAppInstalled becomes true
            }
        )
        return
    }

    // Calculate background color based on game result
    val backgroundColor = remember(uiState.game, uiState.userPlayedBlack) {
        val game = uiState.game
        if (game == null) {
            AppColors.CardBackground  // Default dark gray when no game loaded
        } else {
            val userPlayedBlack = uiState.userPlayedBlack
            val userPlayedWhite = !uiState.userPlayedBlack

            when {
                game.winner == "white" && userPlayedWhite -> AppColors.ResultWinBackground
                game.winner == "black" && userPlayedBlack -> AppColors.ResultWinBackground
                game.winner == "white" && userPlayedBlack -> AppColors.ResultLossBackground
                game.winner == "black" && userPlayedWhite -> AppColors.ResultLossBackground
                game.winner == null -> AppColors.ResultDrawBackground
                else -> AppColors.CardBackground
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

    // Note: Full-screen destinations (Settings, Help, Trace, Retrieve) are now handled via navigation

    // Show GIF export progress (full screen)
    if (uiState.showGifExportDialog) {
        GifExportScreen(
            progress = uiState.gifExportProgress ?: 0f,
            onCancel = { viewModel.cancelGifExport() }
        )
        return
    }

    // Show AI app not installed dialog (full screen)
    if (uiState.showAiAppNotInstalledDialog) {
        AiAppNotInstalledDialog(
            onDismiss = { viewModel.hideAiAppNotInstalledDialog() },
            onInstallClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.ai"))
                context.startActivity(intent)
            },
            onDontAskAgain = { viewModel.setAiAppDontAskAgain() }
        )
        return
    }

    // Show share position screen (full screen)
    if (uiState.showSharePositionDialog) {
        val gameSiteUrl = viewModel.getGameSiteUrl()
        SharePositionScreen(
            gameSiteUrl = gameSiteUrl,
            onCopyFen = { viewModel.copyFenToClipboard(context) },
            onShare = { viewModel.sharePositionAsText(context) },
            onExportPgn = { viewModel.exportAnnotatedPgn(context) },
            onCopyPgn = { viewModel.copyPgnToClipboard(context) },
            onExportGif = { viewModel.exportAsGif(context) },
            onGenerateAiReports = {
                viewModel.hideSharePositionDialog()
                viewModel.showAiPromptSelectionDialog()
            },
            onViewOnSite = {
                gameSiteUrl?.let { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                }
            },
            onDismiss = { viewModel.hideSharePositionDialog() }
        )
        return
    }

    // Show AI prompt selection screen (full screen)
    if (uiState.showAiPromptSelectionDialog) {
        AiPromptSelectionScreen(
            prompts = uiState.aiPrompts.filter { it.safeCategory == AiPromptCategory.GAME },
            onSelectPrompt = { promptEntry ->
                viewModel.hideAiPromptSelectionDialog()
                viewModel.launchGameAnalysis(context, promptEntry)
            },
            onDismiss = { viewModel.hideAiPromptSelectionDialog() }
        )
        return
    }

    // Show player info screen (triggered by clicking on player names)
    if (uiState.showPlayerInfoScreen) {
        PlayerInfoScreen(
            playerInfo = uiState.playerInfo,
            isLoading = uiState.playerInfoLoading,
            error = uiState.playerInfoError,
            games = uiState.playerGames,
            gamesLoading = uiState.playerGamesLoading,
            currentPage = uiState.playerGamesPage,
            hasMoreGames = uiState.playerGamesHasMore,
            onNextPage = { pageSize -> viewModel.nextPlayerGamesPage(pageSize) },
            onPreviousPage = { viewModel.previousPlayerGamesPage() },
            onGameSelected = { game -> viewModel.selectGameFromPlayerInfo(game) },
            onAiReportsClick = {
                uiState.playerInfo?.let { info ->
                    val serverName = if (uiState.playerInfoError != null) {
                        null
                    } else {
                        "lichess.org"
                    }
                    if (serverName != null) {
                        viewModel.launchServerPlayerAnalysis(context, info.username, serverName)
                    } else {
                        viewModel.launchOtherPlayerAnalysis(context, info.username)
                    }
                }
            },
            hasAiApiKeys = viewModel.isAiAppInstalled(context),
            onDismiss = { viewModel.dismissPlayerInfo() }
        )
        return
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title bar - always shown
        EvalTitleBar(
                onEvalClick = { viewModel.clearGame() },
                leftContent = {
                    // Menu icon - navigate to retrieve
                    TitleBarIcon(
                        icon = "≡",
                        onClick = {
                            if (uiState.game != null) {
                                viewModel.clearGame()
                            }
                            onNavigateToRetrieve()
                        },
                        fontSize = 34,
                        offsetY = -8
                    )
                    // Reload last game from server
                    if (uiState.game != null || uiState.hasLastServerUser) {
                        TitleBarIcon(
                            icon = "↻",
                            onClick = { viewModel.reloadLastGame() },
                            fontSize = 34,
                            offsetY = -8
                        )
                    }
                    // Settings icon
                    TitleBarIcon(
                        icon = "⚙",
                        onClick = { onNavigateToSettings() }
                    )
                    // Help icon
                    TitleBarIcon(
                        icon = "?",
                        onClick = { onNavigateToHelp() }
                    )
                }
            )

        // Stage indicator - only show during Preview and Analyse stages
        if (uiState.game != null && uiState.currentStage != AnalysisStage.MANUAL) {
            val isPreviewStage = uiState.currentStage == AnalysisStage.PREVIEW
            val stageText = if (isPreviewStage) "Preview stage" else "Analyse stage"
            val stageColor = if (isPreviewStage) Color(0xFFFFAA00) else AppColors.AccentBlue

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

        // Main view when no game is loaded - show logo centered on screen
        if (uiState.game == null) {
            // Center the logo vertically, offset 10% up from center
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    modifier = Modifier.offset(y = (-60).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EvalLogo()

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Welcome to the Eval app !",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use the top left icon  ",
                            fontSize = 18.sp,
                            color = AppColors.LightGray
                        )
                        Text(
                            text = "≡",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "  to select a game",
                            fontSize = 18.sp,
                            color = AppColors.LightGray
                        )
                    }
                }
            }

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
                        text = uiState.errorMessage ?: "Unknown error",
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
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
            .background(AppColors.CardBackground),
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
                        color = AppColors.AccentBlue,
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
 * Full-screen view shown when user tries to use AI features without the AI app installed.
 */
@Composable
fun AiAppNotInstalledDialog(
    onDismiss: () -> Unit,
    onInstallClick: () -> Unit,
    onDontAskAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EvalTitleBar(
            title = "AI App",
            onBackClick = onDismiss,
            onEvalClick = onDismiss
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "AI App Not Installed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "The AI app is required to generate AI reports.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        Text(
            text = "Install the AI app from the Google Play Store to enable AI-powered game and player analysis.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                onInstallClick()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Install")
        }

        TextButton(
            onClick = {
                onDontAskAgain()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Don't ask again")
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Warning screen shown when AI app is not installed.
 * User can continue without AI features or install the AI app.
 * Automatically checks every 2 seconds if AI app has been installed.
 */
@Composable
fun AiAppNotInstalledScreen(
    onContinue: () -> Unit,
    onCheckInstalled: () -> Boolean,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.ai"

    // Check every 2 seconds if AI app has been installed
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
            .background(AppColors.CardBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                    text = "AI App Not Installed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "The AI app enables AI-powered game and player analysis features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Clickable link to Play Store
                val annotatedText = buildAnnotatedString {
                    append("Install ")
                    pushStringAnnotation(tag = "URL", annotation = playStoreUrl)
                    withStyle(style = SpanStyle(
                        color = AppColors.AccentBlue,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append("AI App")
                    }
                    pop()
                    append(" from the Google Play Store for full features.")
                }

                ClickableText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    text = "The screen will update automatically once the AI app is installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Continue Without AI")
                }
            }
        }
    }
}

/**
 * Eval logo displayed on the main screen when no game is loaded.
 * Features a stylized chess-themed design with light green background.
 */
@Composable
fun EvalLogo() {
    Card(
        modifier = Modifier
            .padding(horizontal = 48.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.LogoBackground
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top chess pieces - black pieces
            Text(
                text = "\u265A \u265B \u265C",
                fontSize = 40.sp,
                color = AppColors.DarkBackground,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main title
            Text(
                text = "Eval",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 4.sp
            )

            // Subtitle
            Text(
                text = "Chess Game Analyser",
                fontSize = 16.sp,
                color = AppColors.LightGray,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom chess pieces - white pieces
            Text(
                text = "\u2657 \u2658 \u2659",
                fontSize = 40.sp,
                color = Color.White,
                letterSpacing = 8.sp
            )
        }
    }
}

/**
 * Converts markdown text to a styled HTML document with chessboard and game data.
 * Delegates to HtmlReportBuilder in the export package.
 */
internal fun convertMarkdownToHtml(serviceName: String, markdown: String, uiState: GameUiState, appVersion: String): String =
    com.eval.export.HtmlReportBuilder.convertMarkdownToHtml(serviceName, markdown, uiState, appVersion)



/**
 * Full-screen view for sharing the current position.
 */
@Composable
fun SharePositionScreen(
    gameSiteUrl: String?,
    onCopyFen: () -> Unit,
    onShare: () -> Unit,
    onExportPgn: () -> Unit,
    onCopyPgn: () -> Unit,
    onExportGif: () -> Unit,
    onGenerateAiReports: () -> Unit,
    onViewOnSite: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = "Share / Export",
            onBackClick = onDismiss,
            onEvalClick = onDismiss
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Copy FEN button
        Button(
            onClick = {
                onCopyFen()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Copy FEN to Clipboard")
        }

        // Copy PGN button
        Button(
            onClick = {
                onCopyPgn()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Copy PGN to Clipboard")
        }

        // Share button
        Button(
            onClick = {
                onShare()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Share Position")
        }

        // Export PGN button
        Button(
            onClick = {
                onExportPgn()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Share Annotated PGN")
        }

        // Export GIF button
        Button(
            onClick = {
                onExportGif()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Export as Animated GIF")
        }

        // AI Reports button
        Button(
            onClick = {
                onGenerateAiReports()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Generate AI Reports")
        }

        // View on lichess.org / chess.com button
        if (gameSiteUrl != null) {
            val siteName = when {
                gameSiteUrl.contains("lichess.org") -> "lichess.org"
                gameSiteUrl.contains("chess.com") -> "chess.com"
                else -> "site"
            }
            Button(
                onClick = {
                    onViewOnSite()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
            ) {
                Text("View on $siteName")
            }
        }
    }
}

/**
 * Full-screen view for selecting an AI prompt before launching analysis.
 */
@Composable
fun AiPromptSelectionScreen(
    prompts: List<AiPromptEntry>,
    onSelectPrompt: (AiPromptEntry) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = "Select AI Prompt",
            onBackClick = onDismiss,
            onEvalClick = onDismiss
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (prompts.isEmpty()) {
            Text(
                text = "No prompts configured. Go to Settings > AI Prompts to add prompts.",
                color = AppColors.SubtleText
            )
        } else {
            prompts.sortedBy { it.name.lowercase() }.forEach { prompt ->
                Button(
                    onClick = { onSelectPrompt(prompt) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
                ) {
                    Text(prompt.name)
                }
            }
        }
    }
}

/**
 * Full-screen view for GIF export progress.
 */
@Composable
fun GifExportScreen(
    progress: Float,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EvalTitleBar(
            title = "Exporting GIF",
            onBackClick = onCancel,
            onEvalClick = onCancel
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp
            )
            Text(
                text = "Creating animated GIF...",
                color = Color.Gray
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}
