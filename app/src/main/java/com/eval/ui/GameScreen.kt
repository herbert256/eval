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
import com.eval.data.AiAnalysisResponse
import com.eval.data.ChessServer
import com.eval.data.PlayerInfo
import dev.jeziellago.compose.markdowntext.MarkdownText
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
            aiSettings = uiState.aiSettings,
            availableChatGptModels = uiState.availableChatGptModels,
            isLoadingChatGptModels = uiState.isLoadingChatGptModels,
            availableGeminiModels = uiState.availableGeminiModels,
            isLoadingGeminiModels = uiState.isLoadingGeminiModels,
            availableGrokModels = uiState.availableGrokModels,
            isLoadingGrokModels = uiState.isLoadingGrokModels,
            availableDeepSeekModels = uiState.availableDeepSeekModels,
            isLoadingDeepSeekModels = uiState.isLoadingDeepSeekModels,
            availableMistralModels = uiState.availableMistralModels,
            isLoadingMistralModels = uiState.isLoadingMistralModels,
            onBack = { viewModel.hideSettingsDialog() },
            onSaveStockfish = { viewModel.updateStockfishSettings(it) },
            onSaveBoardLayout = { viewModel.updateBoardLayoutSettings(it) },
            onSaveGraph = { viewModel.updateGraphSettings(it) },
            onSaveInterfaceVisibility = { viewModel.updateInterfaceVisibilitySettings(it) },
            onSaveGeneral = { viewModel.updateGeneralSettings(it) },
            onSaveAi = { viewModel.updateAiSettings(it) },
            onFetchChatGptModels = { viewModel.fetchChatGptModels(it) },
            onFetchGeminiModels = { viewModel.fetchGeminiModels(it) },
            onFetchGrokModels = { viewModel.fetchGrokModels(it) },
            onFetchDeepSeekModels = { viewModel.fetchDeepSeekModels(it) },
            onFetchMistralModels = { viewModel.fetchMistralModels(it) }
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

    // Show AI analysis dialog
    if (uiState.showAiAnalysisDialog) {
        AiAnalysisDialog(
            serviceName = uiState.aiAnalysisServiceName,
            result = uiState.aiAnalysisResult,
            isLoading = uiState.aiAnalysisLoading,
            uiState = uiState,
            onDismiss = { viewModel.dismissAiAnalysisDialog() }
        )
    }

    // Show player info screen (full screen)
    if (uiState.showPlayerInfoScreen) {
        PlayerInfoScreen(
            playerInfo = uiState.playerInfo,
            isLoading = uiState.playerInfoLoading,
            error = uiState.playerInfoError,
            games = uiState.playerGames,
            gamesLoading = uiState.playerGamesLoading,
            currentPage = uiState.playerGamesPage,
            pageSize = uiState.playerGamesPageSize,
            hasMoreGames = uiState.playerGamesHasMore,
            onNextPage = { viewModel.nextPlayerGamesPage() },
            onPreviousPage = { viewModel.previousPlayerGamesPage() },
            onGameSelected = { game -> viewModel.selectGameFromPlayerInfo(game) },
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
                    fontSize = 36.sp,
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
                            text = "First run",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("Please select a chess game to analyse, use the ")
                                withStyle(style = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)) {
                                    append("\u2261")
                                }
                                append(" icon at the top left for this")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCCCCCC),
                            modifier = Modifier.fillMaxWidth(),
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

/**
 * Dialog displaying AI analysis results for the current chess position.
 */
@Composable
fun AiAnalysisDialog(
    serviceName: String,
    result: AiAnalysisResponse?,
    isLoading: Boolean,
    uiState: GameUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showEmailDialog by remember { mutableStateOf(false) }

    // Load saved email from SharedPreferences
    val prefs = context.getSharedPreferences(SettingsPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    var savedEmail by remember { mutableStateOf(prefs.getString(SettingsPreferences.KEY_AI_REPORT_EMAIL, "") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$serviceName Analysis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 400.dp)
            ) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF6B9BFF)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing position...",
                                color = Color(0xFFAAAAAA)
                            )
                        }
                    }
                    result?.error != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.error,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    result?.analysis != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownText(
                                markdown = result.analysis,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "No analysis available",
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (result?.analysis != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        openAnalysisInChrome(context, serviceName, result.analysis, uiState)
                    }) {
                        Text("View in Chrome")
                    }
                    TextButton(onClick = { showEmailDialog = true }) {
                        Text("Send by email")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    // Email dialog
    if (showEmailDialog && result?.analysis != null) {
        SendReportEmailDialog(
            initialEmail = savedEmail,
            onDismiss = { showEmailDialog = false },
            onSendEmail = { email ->
                // Save email for next time
                prefs.edit().putString(SettingsPreferences.KEY_AI_REPORT_EMAIL, email).apply()
                savedEmail = email
                // Send the report
                sendAnalysisReportByEmail(context, serviceName, result.analysis, uiState, email)
                showEmailDialog = false
            }
        )
    }
}

/**
 * Dialog for entering email address to send the AI analysis report.
 */
@Composable
private fun SendReportEmailDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSendEmail: (String) -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var emailError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Send Report by Email",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter the email address to receive the analysis report.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    label = { Text("Email address") },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialEmail.isNotBlank() && email == initialEmail) {
                    Text(
                        text = "Using previously saved email address",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00E676)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isBlank()) {
                        emailError = "Email address is required"
                    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailError = "Invalid email address"
                    } else {
                        onSendEmail(email.trim())
                    }
                }
            ) {
                Text("Send")
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
 * Full screen showing player information from Lichess or Chess.com.
 */
@Composable
fun PlayerInfoScreen(
    playerInfo: PlayerInfo?,
    isLoading: Boolean,
    error: String?,
    games: List<com.eval.data.LichessGame>,
    gamesLoading: Boolean,
    currentPage: Int,
    pageSize: Int,
    hasMoreGames: Boolean,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onGameSelected: (com.eval.data.LichessGame) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))  // Dark blue background
            .padding(16.dp)
    ) {
        // Header with username and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playerInfo?.username ?: "Player Info",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (playerInfo?.title != null) {
                Surface(
                    color = Color(0xFFFFD700),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = playerInfo.title,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF6B9BFF),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading player info...",
                            color = Color(0xFFAAAAAA),
                            fontSize = 16.sp
                        )
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = Color(0xFFAAAAAA),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                playerInfo != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Server badge
                        val serverName = when (playerInfo.server) {
                            ChessServer.LICHESS -> "Lichess"
                            ChessServer.CHESS_COM -> "Chess.com"
                        }
                        val serverColor = when (playerInfo.server) {
                            ChessServer.LICHESS -> Color(0xFF629924) // Lichess green
                            ChessServer.CHESS_COM -> Color(0xFF769656) // Chess.com green
                        }
                        Surface(
                            color = serverColor,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = serverName,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Profile URL
                        playerInfo.profileUrl?.let { url ->
                            ClickableUrlText(
                                text = url,
                                url = url,
                                context = context
                            )
                        }

                        // Real name
                        playerInfo.name?.let { name ->
                            PlayerInfoRow("Name", name)
                        }

                        // Location/Country
                        val location = listOfNotNull(playerInfo.location, playerInfo.country)
                            .joinToString(", ")
                        if (location.isNotBlank()) {
                            PlayerInfoRow("Location", location)
                        }

                        // Bio
                        playerInfo.bio?.let { bio ->
                            if (bio.isNotBlank()) {
                                Text(
                                    text = "Bio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFAAAAAA)
                                )
                                TextWithClickableUrls(
                                    text = bio,
                                    context = context
                                )
                            }
                        }

                        // Ratings section
                        val hasRatings = listOfNotNull(
                            playerInfo.bulletRating,
                            playerInfo.blitzRating,
                            playerInfo.rapidRating,
                            playerInfo.classicalRating,
                            playerInfo.dailyRating
                        ).isNotEmpty()

                        if (hasRatings) {
                            HorizontalDivider(color = Color(0xFF404040))
                            Text(
                                text = "Ratings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                playerInfo.bulletRating?.let { rating ->
                                    RatingBadge("Bullet", rating)
                                }
                                playerInfo.blitzRating?.let { rating ->
                                    RatingBadge("Blitz", rating)
                                }
                                playerInfo.rapidRating?.let { rating ->
                                    RatingBadge("Rapid", rating)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                playerInfo.classicalRating?.let { rating ->
                                    RatingBadge("Classical", rating)
                                }
                                playerInfo.dailyRating?.let { rating ->
                                    RatingBadge("Daily", rating)
                                }
                            }
                        }

                        // Game statistics section
                        val hasStats = playerInfo.totalGames != null ||
                                playerInfo.wins != null ||
                                playerInfo.losses != null ||
                                playerInfo.draws != null

                        if (hasStats) {
                            HorizontalDivider(color = Color(0xFF404040))
                            Text(
                                text = "Game Statistics",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                playerInfo.totalGames?.let { total ->
                                    StatBadge("Total", total.toString(), Color(0xFF64B5F6))
                                }
                                playerInfo.wins?.let { wins ->
                                    StatBadge("Wins", wins.toString(), Color(0xFF00E676))
                                }
                                playerInfo.losses?.let { losses ->
                                    StatBadge("Losses", losses.toString(), Color(0xFFFF5252))
                                }
                                playerInfo.draws?.let { draws ->
                                    StatBadge("Draws", draws.toString(), Color(0xFF90A4AE))
                                }
                            }
                        }

                        // Play time
                        playerInfo.playTimeSeconds?.let { seconds ->
                            val hours = seconds / 3600
                            val days = hours / 24
                            val playTimeText = when {
                                days > 0 -> "$days days ${hours % 24} hours"
                                hours > 0 -> "$hours hours"
                                else -> "${seconds / 60} minutes"
                            }
                            PlayerInfoRow("Play Time", playTimeText)
                        }

                        // Account info
                        HorizontalDivider(color = Color(0xFF404040))
                        Text(
                            text = "Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        // Online status
                        playerInfo.online?.let { online ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (online) Color(0xFF00E676) else Color(0xFF757575),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (online) "Online" else "Offline",
                                    fontSize = 16.sp,
                                    color = if (online) Color(0xFF00E676) else Color(0xFF757575)
                                )
                            }
                        }

                        // Streamer status
                        if (playerInfo.isStreamer == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📺",
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Streamer",
                                    fontSize = 16.sp,
                                    color = Color(0xFFE040FB) // Purple for streamers
                                )
                            }
                        }

                        // Created date
                        playerInfo.createdAt?.let { timestamp ->
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(java.util.Date(timestamp))
                            PlayerInfoRow("Member Since", date)
                        }

                        // Last seen
                        playerInfo.lastOnline?.let { timestamp ->
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(timestamp))
                            PlayerInfoRow("Last Seen", date)
                        }

                        // Followers
                        playerInfo.followers?.let { followers ->
                            PlayerInfoRow("Followers", followers.toString())
                        }

                        // Games section
                        HorizontalDivider(color = Color(0xFF404040))
                        Text(
                            text = "Recent Games",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        if (gamesLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF6B9BFF),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Loading games...",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 14.sp
                                )
                            }
                        } else if (games.isEmpty()) {
                            Text(
                                text = "No games found",
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            // Games table
                            val startIndex = currentPage * pageSize
                            val endIndex = minOf(startIndex + pageSize, games.size)
                            val currentGames = games.subList(startIndex, endIndex)
                            val totalPages = (games.size + pageSize - 1) / pageSize

                            // Table header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2A2A4A))
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Opponent",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFAAAAAA),
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(
                                    text = "Format",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFAAAAAA),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Result",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFAAAAAA),
                                    modifier = Modifier.weight(0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Table rows
                            currentGames.forEach { game ->
                                val isUserWhite = game.players.white.user?.name?.equals(playerInfo.username, ignoreCase = true) == true
                                val opponent = if (isUserWhite) {
                                    game.players.black.user?.name ?: "Anonymous"
                                } else {
                                    game.players.white.user?.name ?: "Anonymous"
                                }
                                val format = buildString {
                                    append(game.speed.replaceFirstChar { it.uppercase() })
                                    game.clock?.let { clock ->
                                        val minutes = clock.initial / 60
                                        val increment = clock.increment
                                        append(" $minutes+$increment")
                                    }
                                }
                                val (resultText, resultColor) = when {
                                    game.winner == "white" && isUserWhite -> "Won" to Color(0xFF00E676)
                                    game.winner == "black" && !isUserWhite -> "Won" to Color(0xFF00E676)
                                    game.winner == "white" && !isUserWhite -> "Lost" to Color(0xFFFF5252)
                                    game.winner == "black" && isUserWhite -> "Lost" to Color(0xFFFF5252)
                                    game.winner == null -> "Draw" to Color(0xFF90A4AE)
                                    else -> "?" to Color(0xFFAAAAAA)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onGameSelected(game) }
                                        .background(Color(0xFF1E1E3A))
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = opponent,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        modifier = Modifier.weight(1.5f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = format,
                                        fontSize = 12.sp,
                                        color = Color(0xFFAAAAAA),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = resultText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = resultColor,
                                        modifier = Modifier.weight(0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF303050), thickness = 0.5.dp)
                            }

                            // Pagination controls - show if we have more than one page or there might be more to fetch
                            val nextPageStartIndex = (currentPage + 1) * pageSize
                            val canGoNext = nextPageStartIndex < games.size || hasMoreGames
                            val showPagination = games.size > pageSize || hasMoreGames || currentPage > 0

                            if (showPagination) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Previous button
                                    if (currentPage > 0) {
                                        TextButton(onClick = onPreviousPage) {
                                            Text(
                                                text = "← Previous $pageSize",
                                                color = Color(0xFF64B5F6),
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    // Page indicator
                                    Text(
                                        text = if (hasMoreGames) "Page ${currentPage + 1}" else "Page ${currentPage + 1} of $totalPages",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 12.sp
                                    )

                                    // Next button - show if there are more games in cache or more to fetch
                                    if (canGoNext) {
                                        TextButton(
                                            onClick = onNextPage,
                                            enabled = !gamesLoading
                                        ) {
                                            if (gamesLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color(0xFF64B5F6),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(
                                                    text = "Next $pageSize →",
                                                    color = Color(0xFF64B5F6),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                }
                            }

                            // Show count info
                            Text(
                                text = if (hasMoreGames) {
                                    "Showing ${startIndex + 1}-$endIndex (${games.size} loaded)"
                                } else {
                                    "Showing ${startIndex + 1}-$endIndex of ${games.size} games"
                                },
                                color = Color(0xFF757575),
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Add some bottom padding for scrolling
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No player information available",
                            color = Color(0xFFAAAAAA),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Back to game button at the bottom
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3A5A7C)
            )
        ) {
            Text(
                text = "Back to game",
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * Clickable URL text that opens the URL in Chrome when tapped.
 */
@Composable
private fun ClickableUrlText(
    text: String,
    url: String,
    context: android.content.Context
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color(0xFF64B5F6),
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                // URL could not be opened
            }
        }
    )
}

/**
 * Text with embedded URLs that are clickable and open in Chrome.
 * Detects http:// and https:// URLs in the text and makes them clickable.
 */
@Composable
private fun TextWithClickableUrls(
    text: String,
    context: android.content.Context
) {
    val urlPattern = Regex("""(https?://[^\s]+)""")
    val matches = urlPattern.findAll(text).toList()

    if (matches.isEmpty()) {
        // No URLs found, just display plain text
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    } else {
        // Build annotated string with clickable URLs
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            matches.forEach { match ->
                // Add text before the URL
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                // Add the URL with annotation
                val url = match.value
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF64B5F6),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
                pop()
                lastIndex = match.range.last + 1
            }
            // Add remaining text after the last URL
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }

        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // URL could not be opened
                        }
                    }
            }
        )
    }
}

/**
 * Row displaying a label and value for player info.
 */
@Composable
private fun PlayerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Badge displaying a rating category and value.
 */
@Composable
private fun RatingBadge(category: String, rating: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = rating.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF64B5F6)
        )
        Text(
            text = category,
            fontSize = 11.sp,
            color = Color(0xFFAAAAAA)
        )
    }
}

/**
 * Badge displaying a statistic with label and value.
 */
@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFFAAAAAA)
        )
    }
}

/**
 * Sends the AI analysis report via email as an HTML attachment.
 */
private fun sendAnalysisReportByEmail(
    context: android.content.Context,
    serviceName: String,
    markdown: String,
    uiState: GameUiState,
    email: String
) {
    try {
        // Generate the HTML report
        val html = convertMarkdownToHtml(serviceName, markdown, uiState)

        // Create cache directory for AI analysis files
        val cacheDir = java.io.File(context.cacheDir, "ai_analysis")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Write HTML to file
        val htmlFile = java.io.File(cacheDir, "analysis_report.html")
        htmlFile.writeText(html)

        // Get content URI using FileProvider
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            htmlFile
        )

        // Create email subject
        val game = uiState.game
        val whiteName = game?.players?.white?.user?.name ?: "White"
        val blackName = game?.players?.black?.user?.name ?: "Black"
        val subject = "Chess Analysis Report - $whiteName vs $blackName ($serviceName)"

        // Create email intent with HTML attachment
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Please find the $serviceName chess analysis report attached.\n\nOpen the HTML file in a browser to view the full interactive report with chessboard, evaluation graphs, and move list.")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Send report via email"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to send email: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Converts markdown to HTML and opens it in Chrome.
 */
private fun openAnalysisInChrome(context: android.content.Context, serviceName: String, markdown: String, uiState: GameUiState) {
    try {
        // Convert markdown to HTML with game data
        val html = convertMarkdownToHtml(serviceName, markdown, uiState)

        // Create cache directory for AI analysis files
        val cacheDir = java.io.File(context.cacheDir, "ai_analysis")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Write HTML to file
        val htmlFile = java.io.File(cacheDir, "analysis.html")
        htmlFile.writeText(html)

        // Get content URI using FileProvider
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            htmlFile
        )

        // Create intent to open in Chrome
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Try to open specifically in Chrome
            setPackage("com.android.chrome")
        }

        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Chrome not installed, try any browser
            intent.setPackage(null)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to open in browser: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Converts markdown text to a styled HTML document with chessboard and game data.
 */
private fun convertMarkdownToHtml(serviceName: String, markdown: String, uiState: GameUiState): String {
    // Basic markdown to HTML conversion
    var analysisHtml = markdown
        // Escape HTML entities first
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        // Headers
        .replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        .replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        .replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
        // Bold
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        // Italic
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        // Bullet points
        .replace(Regex("^- (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
        .replace(Regex("^\\* (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
        // Numbered lists
        .replace(Regex("^\\d+\\. (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
        // Line breaks
        .replace("\n\n", "</p><p>")
        .replace("\n", "<br>")

    // Wrap consecutive <li> items in <ul>
    analysisHtml = analysisHtml.replace(Regex("(<li>.*?</li>)+")) { match ->
        "<ul>${match.value}</ul>"
    }

    // Extract game data
    val game = uiState.game
    val whiteName = game?.players?.white?.user?.name
        ?: game?.players?.white?.aiLevel?.let { "Stockfish $it" }
        ?: "White"
    val blackName = game?.players?.black?.user?.name
        ?: game?.players?.black?.aiLevel?.let { "Stockfish $it" }
        ?: "Black"
    val whiteRating = game?.players?.white?.rating?.toString() ?: ""
    val blackRating = game?.players?.black?.rating?.toString() ?: ""
    val fen = uiState.currentBoard.getFen()
    val currentMoveIndex = uiState.currentMoveIndex
    val isWhiteToMove = uiState.currentBoard.getTurn() == com.eval.chess.PieceColor.WHITE
    val flippedBoard = uiState.flippedBoard

    // Get current score for eval bar
    val currentScore = uiState.analyseScores[currentMoveIndex]
        ?: uiState.previewScores[currentMoveIndex]
    val evalScore = currentScore?.let {
        if (it.isMate) {
            if (it.mateIn > 0) "M${it.mateIn}" else "M${-it.mateIn}"
        } else {
            val score = it.score
            if (score >= 0) "+%.1f".format(score) else "%.1f".format(score)
        }
    } ?: "0.0"
    val evalPercent = currentScore?.let {
        if (it.isMate) {
            if (it.mateIn > 0) 100f else 0f
        } else {
            // Convert centipawns to percentage (sigmoid-like)
            val cp = it.score * 100
            (50 + 50 * (2 / (1 + kotlin.math.exp(-0.004 * cp)) - 1)).toFloat()
        }
    } ?: 50f

    // Generate move list HTML
    val moveListHtml = buildMoveListHtml(uiState)

    // Generate preview scores for line graph
    val previewScoresJson = uiState.previewScores.entries
        .sortedBy { it.key }
        .joinToString(",") { (idx, score) ->
            val value = if (score.isMate) {
                if (score.mateIn > 0) 10.0 else -10.0
            } else {
                score.score.toDouble().coerceIn(-10.0, 10.0)
            }
            """{"move":$idx,"score":$value}"""
        }

    // Generate analyse scores for line graph
    val analyseScoresJson = uiState.analyseScores.entries
        .sortedBy { it.key }
        .joinToString(",") { (idx, score) ->
            val value = if (score.isMate) {
                if (score.mateIn > 0) 10.0 else -10.0
            } else {
                score.score.toDouble().coerceIn(-10.0, 10.0)
            }
            """{"move":$idx,"score":$value}"""
        }

    // Generate Stockfish analysis HTML
    val stockfishHtml = buildStockfishAnalysisHtml(uiState)

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$serviceName Analysis</title>
    <link rel="stylesheet" href="https://unpkg.com/@chrisoakman/chessboardjs@1.0.0/dist/chessboard-1.0.0.min.css">
    <script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>
    <script src="https://unpkg.com/@chrisoakman/chessboardjs@1.0.0/dist/chessboard-1.0.0.min.js"></script>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            padding: 16px;
            max-width: 900px;
            margin: 0 auto;
            background-color: #1a1a1a;
            color: #e0e0e0;
        }
        h1, h2, h3 {
            color: #ffffff;
            margin-top: 1.5em;
            margin-bottom: 0.5em;
        }
        h1 { font-size: 1.8em; border-bottom: 2px solid #6B9BFF; padding-bottom: 8px; }
        h2 { font-size: 1.4em; color: #6B9BFF; margin-top: 1em; }
        h3 { font-size: 1.2em; color: #8BB8FF; }
        p { margin: 1em 0; }
        ul { padding-left: 20px; }
        li { margin: 0.5em 0; }
        strong { color: #ffffff; }
        em { color: #b0b0b0; }

        /* Board container */
        .board-section {
            margin-bottom: 24px;
        }
        .board-container {
            display: flex;
            gap: 8px;
            justify-content: center;
            align-items: stretch;
        }
        .board-wrapper {
            width: 320px;
        }

        /* Player bars */
        .player-bar {
            display: flex;
            align-items: center;
            padding: 8px 12px;
            background: #2d2d2d;
            border-radius: 4px;
            margin-bottom: 4px;
        }
        .player-bar.bottom { margin-top: 4px; margin-bottom: 0; }
        .player-name { font-weight: bold; color: #fff; }
        .player-rating { color: #888; margin-left: 8px; font-size: 0.9em; }
        .player-indicator {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            margin-right: 8px;
        }
        .player-indicator.white { background: #fff; border: 1px solid #666; }
        .player-indicator.black { background: #000; border: 1px solid #666; }
        .to-move { box-shadow: 0 0 0 2px #ff4444; }

        /* Eval bar */
        .eval-bar {
            width: 24px;
            background: #000;
            border-radius: 4px;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            position: relative;
        }
        .eval-bar-white {
            background: #fff;
            transition: height 0.3s;
        }
        .eval-bar-black {
            background: #333;
            flex: 1;
        }
        .eval-score {
            position: absolute;
            width: 100%;
            text-align: center;
            font-size: 10px;
            font-weight: bold;
            top: 50%;
            transform: translateY(-50%);
            color: #888;
            text-shadow: 0 0 2px #000;
        }

        /* Analysis section */
        .analysis-section {
            background: #242424;
            padding: 16px;
            border-radius: 8px;
            margin: 16px 0;
        }

        /* Graphs */
        .graphs-section {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 16px;
            margin: 16px 0;
        }
        .graph-container {
            background: #1a1a1a;
            border-radius: 8px;
            padding: 12px;
            border: 1px solid #333;
        }
        .graph-title {
            font-size: 0.9em;
            color: #888;
            margin-bottom: 8px;
        }
        .graph-canvas {
            width: 100%;
            height: 120px;
            background: #111;
            border-radius: 4px;
        }

        /* Move list */
        .moves-section {
            background: #242424;
            padding: 16px;
            border-radius: 8px;
            margin: 16px 0;
        }
        .moves-grid {
            display: grid;
            grid-template-columns: 40px 1fr 1fr;
            gap: 4px 8px;
            font-family: monospace;
            font-size: 14px;
        }
        .move-number { color: #666; text-align: right; }
        .move { padding: 2px 6px; border-radius: 3px; }
        .move.current { background: #4a4a00; }
        .move.white-move { color: #fff; }
        .move.black-move { color: #ccc; }

        /* Stockfish section */
        .stockfish-section {
            background: #242424;
            padding: 16px;
            border-radius: 8px;
            margin: 16px 0;
        }
        .pv-line {
            font-family: monospace;
            padding: 8px;
            background: #1a1a1a;
            border-radius: 4px;
            margin: 4px 0;
        }
        .pv-score {
            display: inline-block;
            min-width: 60px;
            font-weight: bold;
        }
        .pv-score.positive { color: #4CAF50; }
        .pv-score.negative { color: #f44336; }
        .pv-moves { color: #aaa; }

        @media (max-width: 600px) {
            .graphs-section { grid-template-columns: 1fr; }
            .board-wrapper { width: 280px; }
        }
    </style>
</head>
<body>
    <h1>$serviceName Analysis</h1>

    <!-- Chess Board Section -->
    <div class="board-section">
        <div class="board-container">
            <!-- Eval Bar -->
            <div class="eval-bar" id="evalBar">
                <div class="eval-bar-white" id="evalWhite" style="height: ${evalPercent}%;"></div>
                <div class="eval-bar-black"></div>
                <div class="eval-score" id="evalScore">$evalScore</div>
            </div>

            <!-- Board with player bars -->
            <div class="board-wrapper">
                <div class="player-bar" id="topPlayer">
                    <div class="player-indicator ${if (flippedBoard) "white" else "black"} ${if ((flippedBoard && isWhiteToMove) || (!flippedBoard && !isWhiteToMove)) "to-move" else ""}"></div>
                    <span class="player-name">${if (flippedBoard) whiteName else blackName}</span>
                    <span class="player-rating">${if (flippedBoard) whiteRating else blackRating}</span>
                </div>
                <div id="board" style="width: 320px;"></div>
                <div class="player-bar bottom" id="bottomPlayer">
                    <div class="player-indicator ${if (flippedBoard) "black" else "white"} ${if ((flippedBoard && !isWhiteToMove) || (!flippedBoard && isWhiteToMove)) "to-move" else ""}"></div>
                    <span class="player-name">${if (flippedBoard) blackName else whiteName}</span>
                    <span class="player-rating">${if (flippedBoard) blackRating else whiteRating}</span>
                </div>
            </div>
        </div>
        <p style="text-align: center; color: #666; font-size: 0.9em; margin-top: 8px;">
            Move ${currentMoveIndex + 1} of ${uiState.moves.size} • ${if (isWhiteToMove) "White" else "Black"} to move
        </p>
    </div>

    <!-- AI Analysis -->
    <div class="analysis-section">
        <h2>$serviceName Analysis</h2>
        <p>$analysisHtml</p>
    </div>

    <!-- Graphs -->
    <div class="graphs-section">
        <div class="graph-container">
            <div class="graph-title">Score Line Graph (Preview)</div>
            <canvas id="lineGraph" class="graph-canvas"></canvas>
        </div>
        <div class="graph-container">
            <div class="graph-title">Score Line Graph (Analyse)</div>
            <canvas id="analyseGraph" class="graph-canvas"></canvas>
        </div>
    </div>

    <!-- Move List -->
    <div class="moves-section">
        <h2>Move List</h2>
        $moveListHtml
    </div>

    <!-- Stockfish Analysis -->
    <div class="stockfish-section">
        <h2>Stockfish Analysis</h2>
        $stockfishHtml
    </div>

    <script>
        // Initialize chessboard
        var board = Chessboard('board', {
            position: '$fen',
            orientation: '${if (flippedBoard) "black" else "white"}',
            pieceTheme: 'https://chessboardjs.com/img/chesspieces/wikipedia/{piece}.png'
        });

        // Draw line graphs
        function drawLineGraph(canvasId, data, currentMove) {
            var canvas = document.getElementById(canvasId);
            var ctx = canvas.getContext('2d');
            var width = canvas.width = canvas.offsetWidth * 2;
            var height = canvas.height = canvas.offsetHeight * 2;
            ctx.scale(2, 2);
            var w = width / 2;
            var h = height / 2;

            // Background
            ctx.fillStyle = '#111';
            ctx.fillRect(0, 0, w, h);

            // Draw zero line
            ctx.strokeStyle = '#333';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(0, h/2);
            ctx.lineTo(w, h/2);
            ctx.stroke();

            if (data.length === 0) {
                ctx.fillStyle = '#666';
                ctx.font = '12px sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText('No data', w/2, h/2);
                return;
            }

            // Find range
            var maxMove = Math.max(...data.map(d => d.move));
            var range = 7; // -7 to +7

            // Draw filled area
            ctx.beginPath();
            ctx.moveTo(0, h/2);
            data.forEach(function(d, i) {
                var x = (d.move / Math.max(maxMove, 1)) * w;
                var y = h/2 - (d.score / range) * (h/2);
                y = Math.max(0, Math.min(h, y));
                if (i === 0) ctx.lineTo(x, y);
                else ctx.lineTo(x, y);
            });
            var lastX = (data[data.length-1].move / Math.max(maxMove, 1)) * w;
            ctx.lineTo(lastX, h/2);
            ctx.closePath();

            // Fill with gradient
            var gradient = ctx.createLinearGradient(0, 0, 0, h);
            gradient.addColorStop(0, 'rgba(244, 67, 54, 0.5)');
            gradient.addColorStop(0.5, 'rgba(100, 100, 100, 0.3)');
            gradient.addColorStop(1, 'rgba(76, 175, 80, 0.5)');
            ctx.fillStyle = gradient;
            ctx.fill();

            // Draw line
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            data.forEach(function(d, i) {
                var x = (d.move / Math.max(maxMove, 1)) * w;
                var y = h/2 - (d.score / range) * (h/2);
                y = Math.max(0, Math.min(h, y));
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            });
            ctx.stroke();

            // Draw current move line
            if (currentMove >= 0) {
                var cx = (currentMove / Math.max(maxMove, 1)) * w;
                ctx.strokeStyle = '#2196F3';
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(cx, 0);
                ctx.lineTo(cx, h);
                ctx.stroke();
            }
        }

        var previewData = [$previewScoresJson];
        var analyseData = [$analyseScoresJson];
        var currentMove = $currentMoveIndex;

        drawLineGraph('lineGraph', previewData, currentMove);
        drawLineGraph('analyseGraph', analyseData, currentMove);

        // Resize handler
        window.addEventListener('resize', function() {
            board.resize();
            drawLineGraph('lineGraph', previewData, currentMove);
            drawLineGraph('analyseGraph', analyseData, currentMove);
        });
    </script>
</body>
</html>
    """.trimIndent()
}

/**
 * Builds HTML for the move list.
 */
private fun buildMoveListHtml(uiState: GameUiState): String {
    val moves = uiState.moveDetails
    if (moves.isEmpty()) return "<p>No moves available</p>"

    val sb = StringBuilder()
    sb.append("<div class=\"moves-grid\">")

    for (i in moves.indices step 2) {
        val moveNum = (i / 2) + 1
        val whiteMove = moves[i]
        val blackMove = moves.getOrNull(i + 1)

        val whiteCurrent = if (i == uiState.currentMoveIndex) "current" else ""
        val blackCurrent = if (i + 1 == uiState.currentMoveIndex) "current" else ""

        sb.append("<div class=\"move-number\">$moveNum.</div>")
        sb.append("<div class=\"move white-move $whiteCurrent\">${whiteMove.san}</div>")
        if (blackMove != null) {
            sb.append("<div class=\"move black-move $blackCurrent\">${blackMove.san}</div>")
        } else {
            sb.append("<div></div>")
        }
    }

    sb.append("</div>")
    return sb.toString()
}

/**
 * Builds HTML for the Stockfish analysis.
 */
private fun buildStockfishAnalysisHtml(uiState: GameUiState): String {
    val result = uiState.analysisResult
    if (result == null) return "<p>No Stockfish analysis available</p>"

    val sb = StringBuilder()
    sb.append("<p style=\"color: #888;\">Depth: ${result.depth} • Nodes: ${formatNodes(result.nodes)}</p>")

    result.lines.forEach { line ->
        val scoreClass = when {
            line.isMate -> if (line.mateIn > 0) "positive" else "negative"
            line.score >= 0 -> "positive"
            else -> "negative"
        }
        val scoreText = if (line.isMate) {
            if (line.mateIn > 0) "M${line.mateIn}" else "M${-line.mateIn}"
        } else {
            val s = line.score
            if (s >= 0) "+%.2f".format(s) else "%.2f".format(s)
        }

        // Convert UCI moves to readable format (simplified)
        val movesText = line.pv.split(" ").take(8).joinToString(" ")

        sb.append("""
            <div class="pv-line">
                <span class="pv-score $scoreClass">$scoreText</span>
                <span class="pv-moves">$movesText</span>
            </div>
        """.trimIndent())
    }

    return sb.toString()
}

/**
 * Formats node count for display.
 */
private fun formatNodes(nodes: Long): String {
    return when {
        nodes >= 1_000_000_000 -> "%.1fB".format(nodes / 1_000_000_000.0)
        nodes >= 1_000_000 -> "%.1fM".format(nodes / 1_000_000.0)
        nodes >= 1_000 -> "%.1fK".format(nodes / 1_000.0)
        else -> nodes.toString()
    }
}
