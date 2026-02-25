package com.eval.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eval.data.ChessServer
import com.eval.data.LeaderboardPlayer

/**
 * Sub-screen navigation for retrieve screen.
 */
private enum class RetrieveSubScreen {
    MAIN,
    LICHESS,
    TOP_RANKINGS_LICHESS,
    TOURNAMENTS_LICHESS,
    BROADCASTS,
    LICHESS_TV,
    STREAMERS,
    CHESS_COM,
    TOP_RANKINGS_CHESS_COM,
    DAILY_PUZZLE,
    PGN_FILE,
    OPENING_SELECTION,
    FEN_INPUT
}

/**
 * Retrieve screen for fetching chess games from Lichess or Chess.com.
 */
@Composable
fun RetrieveScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit,
    onNavigateToGame: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(RetrieveSubScreen.MAIN) }
    // Track which screen we came from when showing player info
    var previousScreen by remember { mutableStateOf(RetrieveSubScreen.MAIN) }

    // Navigate to PGN file screen when PGN events are loaded
    LaunchedEffect(uiState.showPgnEventSelection) {
        if (uiState.showPgnEventSelection && currentScreen == RetrieveSubScreen.MAIN) {
            currentScreen = RetrieveSubScreen.PGN_FILE
        }
    }

    // Track the game we've already navigated for to avoid re-triggering on back navigation
    var navigatedGameId by remember { mutableStateOf<String?>(null) }

    // Navigate to game screen when a NEW game is loaded (keeps RetrieveScreen in back stack)
    LaunchedEffect(uiState.game?.id) {
        val currentGameId = uiState.game?.id
        if (currentGameId != null && currentGameId != navigatedGameId) {
            navigatedGameId = currentGameId
            onNavigateToGame()
        }
    }

    // Show analysed games selection screen
    if (uiState.showAnalysedGamesSelection && uiState.analysedGamesList.isNotEmpty()) {
        AnalysedGamesSelectionScreen(
            games = uiState.analysedGamesList,
            currentPage = uiState.gameSelectionPage,
            onNextPage = { pageSize -> viewModel.nextGameSelectionPage(pageSize) },
            onPreviousPage = { viewModel.previousGameSelectionPage() },
            onSelectGame = { viewModel.selectAnalysedGame(it) },
            onDismiss = { viewModel.dismissAnalysedGamesSelection() }
        )
        return
    }

    // Show selected retrieve games screen (games from a username fetch)
    val selectedRetrieveEntry = uiState.selectedRetrieveEntry
    if (uiState.showSelectedRetrieveGames && selectedRetrieveEntry != null) {
        SelectedRetrieveGamesScreen(
            entry = selectedRetrieveEntry,
            games = uiState.selectedRetrieveGames,
            currentPage = uiState.gameSelectionPage,
            isLoading = uiState.gameSelectionLoading,
            hasMoreGames = uiState.gameSelectionHasMore,
            onNextPage = { pageSize -> viewModel.nextGameSelectionPage(pageSize) },
            onPreviousPage = { viewModel.previousGameSelectionPage() },
            onSelectGame = { viewModel.selectGameFromRetrieve(it) },
            onDismiss = { viewModel.dismissSelectedRetrieveGames() }
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

    // Show player info screen if requested (from top rankings)
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
                    // If there's a profile error (e.g., "Profile not found"), use null for server
                    // to trigger the "Other Player Prompt" instead of "Server Player Prompt"
                    val serverName = if (uiState.playerInfoError != null) {
                        null
                    } else {
                        when (info.server) {
                            ChessServer.LICHESS -> "lichess.org"
                            ChessServer.CHESS_COM -> "chess.com"
                        }
                    }
                    if (serverName != null) {
                        viewModel.launchServerPlayerAnalysis(context, info.username, serverName)
                    } else {
                        viewModel.launchOtherPlayerAnalysis(context, info.username)
                    }
                }
            },
            hasAiApiKeys = viewModel.isAiAppInstalled(context),
            onDismiss = {
                viewModel.dismissPlayerInfo()
                // Go back to the top rankings screen we came from
                currentScreen = previousScreen
            }
        )
        return
    }

    // Handle back navigation
    BackHandler {
        when (currentScreen) {
            RetrieveSubScreen.MAIN -> onBack()
            RetrieveSubScreen.LICHESS -> currentScreen = RetrieveSubScreen.MAIN
            RetrieveSubScreen.TOP_RANKINGS_LICHESS, RetrieveSubScreen.TOURNAMENTS_LICHESS,
            RetrieveSubScreen.BROADCASTS, RetrieveSubScreen.LICHESS_TV,
            RetrieveSubScreen.STREAMERS -> currentScreen = RetrieveSubScreen.LICHESS
            RetrieveSubScreen.CHESS_COM -> currentScreen = RetrieveSubScreen.MAIN
            RetrieveSubScreen.TOP_RANKINGS_CHESS_COM,
            RetrieveSubScreen.DAILY_PUZZLE -> currentScreen = RetrieveSubScreen.CHESS_COM
            RetrieveSubScreen.PGN_FILE -> {
                if (uiState.selectedPgnEvent != null) {
                    viewModel.backToPgnEventList()
                } else {
                    viewModel.dismissPgnEventSelection()
                    currentScreen = RetrieveSubScreen.MAIN
                }
            }
            RetrieveSubScreen.OPENING_SELECTION -> currentScreen = RetrieveSubScreen.MAIN
            RetrieveSubScreen.FEN_INPUT -> currentScreen = RetrieveSubScreen.MAIN
        }
    }

    when (currentScreen) {
        RetrieveSubScreen.MAIN -> RetrieveMainScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = onBack,
            onLichessClick = { currentScreen = RetrieveSubScreen.LICHESS },
            onChessComClick = { currentScreen = RetrieveSubScreen.CHESS_COM },
            onPgnFileLoaded = { hasMultipleEvents ->
                if (hasMultipleEvents) {
                    currentScreen = RetrieveSubScreen.PGN_FILE
                }
            },
            onOpeningClick = {
                viewModel.loadEcoOpenings()
                currentScreen = RetrieveSubScreen.OPENING_SELECTION
            },
            onFenClick = { currentScreen = RetrieveSubScreen.FEN_INPUT }
        )
        RetrieveSubScreen.LICHESS -> LichessRetrieveScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = { currentScreen = RetrieveSubScreen.MAIN },
            onTopRankingsClick = {
                viewModel.showTopRankings(ChessServer.LICHESS)
                previousScreen = RetrieveSubScreen.TOP_RANKINGS_LICHESS
                currentScreen = RetrieveSubScreen.TOP_RANKINGS_LICHESS
            },
            onTournamentsClick = {
                viewModel.showTournaments(ChessServer.LICHESS)
                currentScreen = RetrieveSubScreen.TOURNAMENTS_LICHESS
            },
            onBroadcastsClick = {
                viewModel.showBroadcasts()
                currentScreen = RetrieveSubScreen.BROADCASTS
            },
            onTvClick = {
                viewModel.showLichessTv()
                currentScreen = RetrieveSubScreen.LICHESS_TV
            },
            onStreamersClick = {
                viewModel.showStreamers()
                currentScreen = RetrieveSubScreen.STREAMERS
            }
        )
        RetrieveSubScreen.TOP_RANKINGS_LICHESS -> TopRankingsScreen(
            uiState = uiState,
            serverName = "lichess.org",
            serverColor = AppColors.LichessGreen,
            onBack = { currentScreen = RetrieveSubScreen.LICHESS },
            onPlayerClick = { player ->
                previousScreen = RetrieveSubScreen.TOP_RANKINGS_LICHESS
                viewModel.selectTopRankingPlayer(player.username, ChessServer.LICHESS)
            }
        )
        RetrieveSubScreen.CHESS_COM -> ChessComRetrieveScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = { currentScreen = RetrieveSubScreen.MAIN },
            onTopRankingsClick = {
                viewModel.showTopRankings(ChessServer.CHESS_COM)
                previousScreen = RetrieveSubScreen.TOP_RANKINGS_CHESS_COM
                currentScreen = RetrieveSubScreen.TOP_RANKINGS_CHESS_COM
            },
            onDailyPuzzleClick = {
                viewModel.showDailyPuzzle()
                currentScreen = RetrieveSubScreen.DAILY_PUZZLE
            }
        )
        RetrieveSubScreen.TOP_RANKINGS_CHESS_COM -> TopRankingsScreen(
            uiState = uiState,
            serverName = "chess.com",
            serverColor = AppColors.ChessComGreen,
            onBack = { currentScreen = RetrieveSubScreen.CHESS_COM },
            onPlayerClick = { player ->
                previousScreen = RetrieveSubScreen.TOP_RANKINGS_CHESS_COM
                viewModel.selectTopRankingPlayer(player.username, ChessServer.CHESS_COM)
            }
        )
        RetrieveSubScreen.DAILY_PUZZLE -> DailyPuzzleScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = {
                viewModel.dismissDailyPuzzle()
                currentScreen = RetrieveSubScreen.CHESS_COM
            }
        )
        RetrieveSubScreen.TOURNAMENTS_LICHESS -> TournamentsScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = {
                viewModel.dismissTournaments()
                currentScreen = RetrieveSubScreen.LICHESS
            }
        )
        RetrieveSubScreen.BROADCASTS -> BroadcastsScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = {
                viewModel.dismissBroadcasts()
                currentScreen = RetrieveSubScreen.LICHESS
            }
        )
        RetrieveSubScreen.LICHESS_TV -> LichessTvScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = {
                viewModel.dismissLichessTv()
                currentScreen = RetrieveSubScreen.LICHESS
            }
        )
        RetrieveSubScreen.STREAMERS -> StreamersScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = {
                viewModel.dismissStreamers()
                currentScreen = RetrieveSubScreen.LICHESS
            }
        )
        RetrieveSubScreen.PGN_FILE -> PgnFileScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = {
                viewModel.dismissPgnEventSelection()
                currentScreen = RetrieveSubScreen.MAIN
            }
        )
        RetrieveSubScreen.OPENING_SELECTION -> OpeningSelectionScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = { currentScreen = RetrieveSubScreen.MAIN }
        )
        RetrieveSubScreen.FEN_INPUT -> {
            val fenContext = LocalContext.current
            val fenSettingsPrefs = remember {
                val prefs = fenContext.getSharedPreferences(SettingsPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                SettingsPreferences(prefs)
            }
            var fenInput by remember { mutableStateOf("") }
            var fenHistory by remember { mutableStateOf(fenSettingsPrefs.loadFenHistory()) }
            FenInputScreen(
                fenInput = fenInput,
                onFenInputChange = { fenInput = it },
                fenHistory = fenHistory,
                onStart = {
                    if (fenInput.isNotBlank()) {
                        val trimmedFen = fenInput.trim()
                        fenSettingsPrefs.saveFenToHistory(trimmedFen)
                        viewModel.startFromFen(trimmedFen)
                        currentScreen = RetrieveSubScreen.MAIN
                    }
                },
                onDismiss = { currentScreen = RetrieveSubScreen.MAIN }
            )
        }
    }
}

/**
 * Main retrieve screen with options to select Lichess.
 */
@Composable
private fun RetrieveMainScreen(
    uiState: GameUiState,
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onLichessClick: () -> Unit,
    onChessComClick: () -> Unit,
    onPgnFileLoaded: (hasMultipleEvents: Boolean) -> Unit,
    onOpeningClick: () -> Unit,
    onFenClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // File picker launcher for PGN files (supports ZIP files containing PGN)
    val pgnFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    // Read first bytes to check if it's a ZIP file
                    val bufferedStream = java.io.BufferedInputStream(inputStream)
                    bufferedStream.mark(4)
                    val header = ByteArray(4)
                    bufferedStream.read(header)
                    bufferedStream.reset()

                    // ZIP files start with PK (0x50 0x4B)
                    val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

                    val pgnContent = if (isZip) {
                        // Extract PGN content from ZIP file
                        extractPgnFromZip(bufferedStream)
                    } else {
                        // Read as plain text PGN
                        bufferedStream.bufferedReader().use { reader -> reader.readText() }
                    }

                    bufferedStream.close()

                    if (pgnContent != null && pgnContent.isNotBlank()) {
                        viewModel.loadGamesFromPgnContent(pgnContent, onPgnFileLoaded)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RetrieveScreen", "Error reading file: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = "Select a game",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Lichess card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLichessClick() },
                colors = CardDefaults.cardColors(
                    containerColor = AppColors.LichessGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "lichess.org",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = ">",
                        fontSize = 24.sp,
                        color = Color.White
                    )
                }
            }

            // Chess.com card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChessComClick() },
                colors = CardDefaults.cardColors(
                    containerColor = AppColors.ChessComGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "chess.com",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = ">",
                        fontSize = 24.sp,
                        color = Color.White
                    )
                }
            }

            HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Error message
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Button to select from previous retrieves
            if (uiState.hasPreviousRetrieves) {
                Button(
                    onClick = { viewModel.showPreviousRetrieves() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.BlueGrayAccent
                    )
                ) {
                    Text("Select from a previous retrieve")
                }
            }

            // Button to select from previously analysed games
            if (uiState.hasAnalysedGames) {
                Button(
                    onClick = { viewModel.showAnalysedGames() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.BlueGrayAccent
                    )
                ) {
                    Text("Select from previous analysed games")
                }
            }

            // Button to select from PGN file
            Button(
                onClick = {
                    pgnFileLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.BlueGrayAccent
                )
            ) {
                Text("Select from a PGN file")
            }

            // Button to start with opening
            Button(
                onClick = onOpeningClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.BlueGrayAccent
                )
            ) {
                Text("Start with opening")
            }

            // Button to start from FEN position
            Button(
                onClick = onFenClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.BlueGrayAccent
                )
            ) {
                Text("Start from FEN position")
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
                        CircularProgressIndicator(color = AppColors.AccentBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fetching games...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lichess retrieve screen.
 */
@Composable
private fun LichessRetrieveScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit,
    onTopRankingsClick: () -> Unit,
    onTournamentsClick: () -> Unit,
    onBroadcastsClick: () -> Unit,
    onTvClick: () -> Unit,
    onStreamersClick: () -> Unit
) {
    var username by remember { mutableStateOf(viewModel.savedLichessUsername) }
    val focusManager = LocalFocusManager.current

    // Handle back navigation
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = "lichess.org",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error message
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("Enter Lichess username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.DarkGray,
                    focusedBorderColor = AppColors.LichessGreen,
                    unfocusedLabelColor = AppColors.SubtleText,
                    focusedLabelColor = AppColors.LichessGreen
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Retrieve button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (username.isNotBlank()) {
                        viewModel.fetchGames(ChessServer.LICHESS, username)
                    }
                },
                enabled = !uiState.isLoading && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.LichessGreen
                )
            ) {
                Text(
                    text = "Retrieve games",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top rankings button
            OutlinedButton(
                onClick = onTopRankingsClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.LichessGreen
                )
            ) {
                Text(
                    text = "Select from top rankings",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Tournaments button
            OutlinedButton(
                onClick = onTournamentsClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.LichessGreen
                )
            ) {
                Text(
                    text = "Current tournaments",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Broadcasts button
            OutlinedButton(
                onClick = onBroadcastsClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.LichessGreen
                )
            ) {
                Text(
                    text = "Broadcasts (official events)",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Lichess TV button
            OutlinedButton(
                onClick = onTvClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.LichessGreen
                )
            ) {
                Text(
                    text = "Lichess TV",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Streamers button
            OutlinedButton(
                onClick = onStreamersClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.LichessGreen
                )
            ) {
                Text(
                    text = "Streamers",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
                        CircularProgressIndicator(color = AppColors.LichessGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fetching games from Lichess...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top rankings screen for Lichess.
 */
@Composable
private fun TopRankingsScreen(
    uiState: GameUiState,
    serverName: String = "lichess.org",
    serverColor: Color = AppColors.LichessGreen,
    onBack: () -> Unit,
    onPlayerClick: (LeaderboardPlayer) -> Unit
) {

    // Handle back navigation
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = "Top Rankings",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Text(
            text = serverName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.SubtleText,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )

        // Content
        when {
            uiState.topRankingsLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading top rankings...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
            uiState.topRankingsError != null -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.topRankingsError.orEmpty(),
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Show each format
                    uiState.topRankings.forEach { (format, players) ->
                        item {
                            FormatSection(
                                format = format,
                                players = players,
                                serverColor = serverColor,
                                onPlayerClick = onPlayerClick
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section for a single format (Bullet, Blitz, etc.) showing top 10 players.
 */
@Composable
private fun FormatSection(
    format: String,
    players: List<LeaderboardPlayer>,
    serverColor: Color,
    onPlayerClick: (LeaderboardPlayer) -> Unit
) {
    Column {
        // Format header
        Text(
            text = format,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = serverColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Players table
        Card(
            colors = CardDefaults.cardColors(
                containerColor = AppColors.CardBackground
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "#",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.width(30.dp)
                    )
                    Text(
                        text = "Player",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Rating",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Player rows
                players.forEachIndexed { index, player ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerClick(player) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 14.sp,
                            color = AppColors.SubtleText,
                            modifier = Modifier.width(30.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            player.title?.let { title ->
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6A800)
                                )
                            }
                            Text(
                                text = player.username,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = player.rating?.toString() ?: "-",
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// ==================== TOURNAMENTS SCREEN ====================

/**
 * Tournaments screen for Lichess.
 */
@Composable
private fun TournamentsScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    val serverColor = AppColors.LichessGreen

    BackHandler {
        if (uiState.selectedTournament != null) {
            viewModel.backToTournamentList()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = if (uiState.selectedTournament != null) "Tournament Games" else "Tournaments",
            onBackClick = {
                if (uiState.selectedTournament != null) {
                    viewModel.backToTournamentList()
                } else {
                    onBack()
                }
            },
            onEvalClick = onBack
        )

        if (uiState.selectedTournament != null) {
            Text(
                text = uiState.selectedTournament.name,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.SubtleText,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )
        } else {
            Text(
                text = "lichess.org",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.SubtleText,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )
        }

        // Content
        when {
            uiState.tournamentsLoading || uiState.tournamentGamesLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.tournamentGamesLoading) "Loading games..." else "Loading tournaments...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
            uiState.tournamentsError != null -> {
                Text(
                    text = uiState.tournamentsError,
                    color = AppColors.NegativeRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
            uiState.selectedTournament != null -> {
                // Show tournament games
                if (uiState.tournamentGames.isEmpty()) {
                    Text(
                        text = "No games found in this tournament",
                        color = AppColors.SubtleText,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.tournamentGames, key = { it.id }) { game ->
                            TournamentGameRow(
                                game = game,
                                onClick = { viewModel.selectTournamentGame(game) }
                            )
                        }
                    }
                }
            }
            else -> {
                // Show tournament list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tournamentsList, key = { it.id }) { tournament ->
                        TournamentRow(
                            tournament = tournament,
                            serverColor = serverColor,
                            onClick = { viewModel.selectTournament(tournament) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TournamentRow(
    tournament: com.eval.data.TournamentInfo,
    serverColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tournament.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = tournament.status,
                    color = when (tournament.status) {
                        "In Progress" -> AppColors.PositiveGreen
                        "Starting Soon" -> Color(0xFFFFD700)
                        else -> AppColors.SubtleText
                    },
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = tournament.timeControl,
                    color = serverColor,
                    fontSize = 12.sp
                )
                Text(
                    text = "${tournament.playerCount} players",
                    color = AppColors.SubtleText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TournamentGameRow(
    game: com.eval.data.LichessGame,
    onClick: () -> Unit
) {
    val whiteName = game.players.white.user?.name ?: "White"
    val blackName = game.players.black.user?.name ?: "Black"
    val result = when (game.winner) {
        "white" -> "1-0"
        "black" -> "0-1"
        null -> if (game.status == "started") "*" else "½-½"
        else -> "?"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(AppColors.CardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = whiteName,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = result,
            color = AppColors.SubtleText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            text = blackName,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

// ==================== BROADCASTS SCREEN ====================

/**
 * Broadcasts screen for Lichess official events.
 */
@Composable
private fun BroadcastsScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    val serverColor = AppColors.LichessGreen

    // Determine current level: broadcasts -> rounds -> games
    val showingRounds = uiState.selectedBroadcast != null &&
            uiState.selectedBroadcastRound == null &&
            uiState.selectedBroadcast.rounds.size > 1
    val showingGames = uiState.selectedBroadcastRound != null

    BackHandler {
        if (uiState.selectedBroadcast != null) {
            viewModel.backToBroadcastList()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = when {
                showingGames -> "Games"
                showingRounds -> "Rounds"
                else -> "Broadcasts"
            },
            onBackClick = {
                if (uiState.selectedBroadcast != null) {
                    viewModel.backToBroadcastList()
                } else {
                    onBack()
                }
            },
            onEvalClick = onBack
        )

        // Subtitle showing current broadcast/round
        when {
            showingGames && uiState.selectedBroadcast != null -> {
                Column(modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                    Text(
                        text = uiState.selectedBroadcast.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.SubtleText
                    )
                    uiState.selectedBroadcastRound?.let { round ->
                        Text(
                            text = round.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = serverColor
                        )
                    }
                }
            }
            showingRounds && uiState.selectedBroadcast != null -> {
                Text(
                    text = uiState.selectedBroadcast.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.SubtleText,
                    modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                )
            }
            else -> {
                Text(
                    text = "Official events from lichess.org",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.SubtleText,
                    modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                )
            }
        }

        // Content
        when {
            uiState.broadcastsLoading || uiState.broadcastGamesLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.broadcastGamesLoading) "Loading games..." else "Loading broadcasts...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
            uiState.broadcastsError != null -> {
                Text(
                    text = uiState.broadcastsError,
                    color = AppColors.NegativeRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
            showingGames -> {
                // Show broadcast games
                if (uiState.broadcastGames.isEmpty()) {
                    Text(
                        text = "No games found in this round",
                        color = AppColors.SubtleText,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.broadcastGames, key = { it.id }) { game ->
                            TournamentGameRow(
                                game = game,
                                onClick = { viewModel.selectBroadcastGame(game) }
                            )
                        }
                    }
                }
            }
            showingRounds && uiState.selectedBroadcast != null -> {
                // Show round selection
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.selectedBroadcast.rounds, key = { it.id }) { round ->
                        BroadcastRoundRow(
                            round = round,
                            serverColor = serverColor,
                            onClick = { viewModel.selectBroadcastRound(round) }
                        )
                    }
                }
            }
            else -> {
                // Show broadcast list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.broadcastsList, key = { it.id }) { broadcast ->
                        BroadcastRow(
                            broadcast = broadcast,
                            serverColor = serverColor,
                            onClick = { viewModel.selectBroadcast(broadcast) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastRow(
    broadcast: com.eval.data.BroadcastInfo,
    serverColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = broadcast.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (broadcast.ongoing) {
                    Text(
                        text = "LIVE",
                        color = AppColors.NegativeRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Show number of rounds
            val roundCount = broadcast.rounds.size
            val ongoingRounds = broadcast.rounds.count { it.ongoing }
            Text(
                text = when {
                    roundCount == 1 -> broadcast.rounds.first().name
                    ongoingRounds > 0 -> "$roundCount rounds ($ongoingRounds live)"
                    else -> "$roundCount rounds"
                },
                color = serverColor,
                fontSize = 12.sp
            )
            broadcast.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Text(
                        text = desc.take(100) + if (desc.length > 100) "..." else "",
                        color = AppColors.SubtleText,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun BroadcastRoundRow(
    round: com.eval.data.BroadcastRoundInfo,
    serverColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = round.name,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (round.ongoing) {
                    Text(
                        text = "LIVE",
                        color = AppColors.NegativeRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (round.finished) {
                    Text(
                        text = "Finished",
                        color = AppColors.SubtleText,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = ">",
                    color = serverColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== PGN FILE SCREEN ====================

/**
 * PGN file screen for selecting events and games from a PGN file.
 * Similar layout to BroadcastsScreen.
 */
@Composable
private fun PgnFileScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    val accentColor = AppColors.BlueGrayAccent

    // Determine current level: events -> games
    val showingGames = uiState.selectedPgnEvent != null
    val hasMultipleEvents = uiState.pgnEvents.size > 1

    BackHandler {
        if (showingGames && hasMultipleEvents) {
            viewModel.backToPgnEventList()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = if (showingGames) "Games" else "Events",
            onBackClick = {
                if (showingGames && hasMultipleEvents) {
                    viewModel.backToPgnEventList()
                } else {
                    onBack()
                }
            },
            onEvalClick = onBack
        )

        // Subtitle
        if (showingGames) {
            Text(
                text = uiState.selectedPgnEvent ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.SubtleText,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )
        } else {
            Text(
                text = "Select an event from PGN file",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.SubtleText,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )
        }

        // Content
        when {
            showingGames -> {
                // Show games for selected event
                if (uiState.pgnGamesForSelectedEvent.isEmpty()) {
                    Text(
                        text = "No games found",
                        color = AppColors.SubtleText,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.pgnGamesForSelectedEvent, key = { it.id }) { game ->
                            PgnGameRow(
                                game = game,
                                onClick = { viewModel.selectPgnGameFromEvent(game) }
                            )
                        }
                    }
                }
            }
            else -> {
                // Show event list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.pgnEvents, key = { it }) { event ->
                        val gameCount = uiState.pgnGamesByEvent[event]?.size ?: 0
                        PgnEventRow(
                            eventName = event,
                            gameCount = gameCount,
                            accentColor = accentColor,
                            onClick = { viewModel.selectPgnEvent(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PgnEventRow(
    eventName: String,
    gameCount: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eventName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$gameCount games",
                    color = accentColor,
                    fontSize = 12.sp
                )
            }
            Text(
                text = ">",
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PgnGameRow(
    game: com.eval.data.LichessGame,
    onClick: () -> Unit
) {
    val whiteName = game.players.white.user?.name ?: "White"
    val blackName = game.players.black.user?.name ?: "Black"
    val result = game.status

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(AppColors.CardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = whiteName,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = result,
            color = AppColors.SubtleText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(
            text = blackName,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

// ==================== LICHESS TV SCREEN ====================

/**
 * Lichess TV screen showing current top games.
 */
@Composable
private fun LichessTvScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    val serverColor = AppColors.LichessGreen

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = "Lichess TV",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Text(
            text = "Current top games",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.SubtleText,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )

        // Content
        when {
            uiState.tvLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading TV channels...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
            uiState.tvError != null -> {
                Text(
                    text = uiState.tvError,
                    color = AppColors.NegativeRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tvChannels, key = { it.gameId }) { channel ->
                        TvChannelRow(
                            channel = channel,
                            serverColor = serverColor,
                            onClick = { viewModel.selectTvGame(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvChannelRow(
    channel: com.eval.data.TvChannelInfo,
    serverColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = channel.channelName,
                    fontWeight = FontWeight.Bold,
                    color = serverColor,
                    fontSize = 16.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    channel.playerTitle?.let { title ->
                        Text(
                            text = title,
                            color = Color(0xFFE6A800),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = channel.playerName,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            channel.rating?.let { rating ->
                Text(
                    text = rating.toString(),
                    color = AppColors.SubtleText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ==================== STREAMERS SCREEN ====================

/**
 * Lichess streamers screen.
 */
@Composable
private fun StreamersScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    val serverColor = AppColors.LichessGreen

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = "Streamers",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Text(
            text = "lichess.org streamers",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.SubtleText,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )

        // Content
        when {
            uiState.streamersLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading streamers...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
            uiState.streamersList.isEmpty() -> {
                Text(
                    text = "No streamers found",
                    color = AppColors.SubtleText,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.streamersList, key = { it.username }) { streamer ->
                        StreamerRow(
                            streamer = streamer,
                            onClick = { viewModel.selectStreamer(streamer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamerRow(
    streamer: com.eval.data.StreamerInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = streamer.username,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                streamer.twitchUrl?.let {
                    Text(
                        text = "Twitch",
                        color = Color(0xFF9146FF),
                        fontSize = 12.sp
                    )
                }
            }
            if (streamer.isLive) {
                Text(
                    text = "LIVE",
                    color = AppColors.NegativeRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== CHESS.COM RETRIEVE SCREEN ====================

/**
 * Chess.com retrieve screen.
 */
@Composable
private fun ChessComRetrieveScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit,
    onTopRankingsClick: () -> Unit,
    onDailyPuzzleClick: () -> Unit
) {
    var username by remember { mutableStateOf(viewModel.savedChessComUsername) }
    val focusManager = LocalFocusManager.current
    val serverColor = AppColors.ChessComGreen

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = "chess.com",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error message
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("Enter Chess.com username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.DarkGray,
                    focusedBorderColor = serverColor,
                    unfocusedLabelColor = AppColors.SubtleText,
                    focusedLabelColor = serverColor
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Retrieve button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (username.isNotBlank()) {
                        viewModel.fetchGames(ChessServer.CHESS_COM, username)
                    }
                },
                enabled = !uiState.isLoading && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = serverColor
                )
            ) {
                Text(
                    text = "Retrieve games",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top rankings button
            OutlinedButton(
                onClick = onTopRankingsClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = serverColor
                )
            ) {
                Text(
                    text = "Select from top rankings",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Daily puzzle button
            OutlinedButton(
                onClick = onDailyPuzzleClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = serverColor
                )
            ) {
                Text(
                    text = "Daily puzzle",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fetching games from Chess.com...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
        }
    }
}

// ==================== DAILY PUZZLE SCREEN ====================

/**
 * Daily puzzle screen for Chess.com.
 */
@Composable
private fun DailyPuzzleScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    val serverColor = AppColors.ChessComGreen

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = "Daily Puzzle",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Text(
            text = "chess.com",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.SubtleText,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )

        when {
            uiState.dailyPuzzleLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = serverColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading daily puzzle...",
                            color = AppColors.SubtleText
                        )
                    }
                }
            }
            uiState.dailyPuzzle != null -> {
                val puzzle = uiState.dailyPuzzle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Puzzle title
                    Text(
                        text = puzzle.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // FEN display
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AppColors.CardBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "FEN",
                                style = MaterialTheme.typography.labelMedium,
                                color = serverColor,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = puzzle.fen,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Start from this position button
                    Button(
                        onClick = {
                            viewModel.dismissDailyPuzzle()
                            viewModel.startFromFen(puzzle.fen)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = serverColor
                        )
                    ) {
                        Text(
                            text = "Start from this position",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
            else -> {
                // Error or no puzzle
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = AppColors.NegativeRed,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Text(
                        text = "No puzzle available",
                        color = AppColors.SubtleText,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Extract PGN content from a ZIP file.
 * Looks for .pgn files inside the ZIP and concatenates their content.
 */
private fun extractPgnFromZip(inputStream: java.io.InputStream): String? {
    val zipInputStream = java.util.zip.ZipInputStream(inputStream)
    val pgnContent = StringBuilder()

    try {
        var entry = zipInputStream.nextEntry
        while (entry != null) {
            // Look for PGN files (case-insensitive)
            if (!entry.isDirectory && entry.name.lowercase().endsWith(".pgn")) {
                val content = zipInputStream.bufferedReader().readText()
                if (pgnContent.isNotEmpty()) {
                    pgnContent.append("\n\n")
                }
                pgnContent.append(content)
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
    } catch (e: Exception) {
        android.util.Log.e("RetrieveScreen", "Error extracting ZIP: ${e.message}")
    } finally {
        zipInputStream.close()
    }

    return if (pgnContent.isNotEmpty()) pgnContent.toString() else null
}

/**
 * Opening selection screen with search functionality.
 */
@Composable
private fun OpeningSelectionScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // Filter openings based on search query (searches in ECO code and name)
    val filteredOpenings = remember(searchQuery, uiState.ecoOpenings) {
        if (searchQuery.isBlank()) {
            uiState.ecoOpenings
        } else {
            val query = searchQuery.lowercase()
            uiState.ecoOpenings.filter { opening ->
                opening.eco.lowercase().contains(query) ||
                opening.name.lowercase().contains(query)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBlueBackground)
            .padding(16.dp)
    ) {
        // Header
        EvalTitleBar(
            title = "Start with opening",
            onBackClick = onBack,
            onEvalClick = onBack
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Search input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search openings...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppColors.ButtonGreen,
                unfocusedBorderColor = AppColors.Divider,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Results count
        Text(
            text = "${filteredOpenings.size} openings",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Loading indicator or list
        if (uiState.ecoOpeningsLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.ButtonGreen)
            }
        } else {
            // Opening list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredOpenings, key = { it.eco }) { opening ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.startWithOpening(opening)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2A2A3E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = opening.eco,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.ButtonGreen,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = opening.moves,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = opening.name,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen FEN input view.
 */
@Composable
fun FenInputScreen(
    fenInput: String,
    onFenInputChange: (String) -> Unit,
    fenHistory: List<String>,
    onStart: () -> Unit,
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
            title = "Enter FEN Position",
            onBackClick = onDismiss,
            onEvalClick = onDismiss
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Paste or type a FEN string:",
            color = Color.Gray,
            fontSize = 14.sp
        )

        OutlinedTextField(
            value = fenInput,
            onValueChange = onFenInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fontSize = 11.sp) },
            singleLine = false,
            maxLines = 3
        )

        // FEN History
        if (fenHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Recent positions:",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                fenHistory.forEach { fen ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFenInputChange(fen) },
                        colors = CardDefaults.cardColors(
                            containerColor = AppColors.CardBackground
                        )
                    ) {
                        Text(
                            text = fen,
                            fontSize = 11.sp,
                            color = AppColors.LightGray,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) {
            Text("Start")
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}
