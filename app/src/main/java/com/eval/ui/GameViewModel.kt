package com.eval.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eval.chess.ChessBoard
import com.eval.chess.Square
import com.eval.data.BroadcastInfo
import com.eval.data.BroadcastRoundInfo
import com.eval.data.ChessRepository
import com.eval.data.ChessServer
import com.eval.data.LichessGame
import com.eval.data.OpeningBook
import com.eval.data.Result
import com.eval.data.StreamerInfo
import com.eval.data.TournamentInfo
import com.eval.data.TvChannelInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.eval.stockfish.StockfishEngine
import org.json.JSONObject
import com.eval.audio.MoveSoundPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChessRepository()
    private val stockfish = StockfishEngine(application)
    private val prefs = application.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Helper classes for settings and game storage
    private val settingsPrefs = SettingsPreferences(prefs)
    private val gameStorage = GameStorageManager(prefs, gson)

    // Move sound player for audio feedback
    private val moveSoundPlayer = MoveSoundPlayer(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var boardHistory = mutableListOf<ChessBoard>()
    private var exploringLineHistory = mutableListOf<ChessBoard>()

    // Track settings when dialog opens to detect changes
    private var settingsOnDialogOpen: SettingsSnapshot? = null

    private data class SettingsSnapshot(
        val previewStageSettings: PreviewStageSettings,
        val analyseStageSettings: AnalyseStageSettings,
        val manualStageSettings: ManualStageSettings
    )

    // Helper classes for better organization
    private val analysisOrchestrator: AnalysisOrchestrator
    private val gameLoader: GameLoader
    private val boardNavigationManager: BoardNavigationManager
    private val contentSourceManager: ContentSourceManager
    private val liveGameManager: LiveGameManager

    // Opening explorer job
    private var openingExplorerJob: Job? = null

    val savedLichessUsername: String
        get() = settingsPrefs.savedLichessUsername

    private fun loadStockfishSettings(): StockfishSettings = settingsPrefs.loadStockfishSettings()
    private fun saveStockfishSettings(settings: StockfishSettings) = settingsPrefs.saveStockfishSettings(settings)
    private fun loadBoardLayoutSettings(): BoardLayoutSettings = settingsPrefs.loadBoardLayoutSettings()
    private fun saveBoardLayoutSettings(settings: BoardLayoutSettings) = settingsPrefs.saveBoardLayoutSettings(settings)
    private fun loadGraphSettings(): GraphSettings = settingsPrefs.loadGraphSettings()
    private fun saveGraphSettings(settings: GraphSettings) = settingsPrefs.saveGraphSettings(settings)
    private fun loadInterfaceVisibilitySettings(): InterfaceVisibilitySettings = settingsPrefs.loadInterfaceVisibilitySettings()
    private fun saveInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) = settingsPrefs.saveInterfaceVisibilitySettings(settings)
    private fun loadGeneralSettings(): GeneralSettings = settingsPrefs.loadGeneralSettings()
    private fun saveGeneralSettings(settings: GeneralSettings) = settingsPrefs.saveGeneralSettings(settings)
    private fun loadAiPrompts(): List<AiPromptEntry> = settingsPrefs.loadAiPrompts()
    private fun saveAiPrompts(prompts: List<AiPromptEntry>) = settingsPrefs.saveAiPrompts(prompts)

    private fun getAppVersionCode(): Long {
        return try {
            val packageInfo = getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun isFirstRun(): Boolean {
        val savedVersionCode = settingsPrefs.getFirstGameRetrievedVersion()
        val currentVersion = getAppVersionCode()
        return savedVersionCode != currentVersion
    }

    private fun markFirstRunComplete() {
        settingsPrefs.setFirstGameRetrievedVersion(getAppVersionCode())
    }

    private fun resetSettingsToDefaults() {
        settingsPrefs.resetAllSettingsToDefaults()
    }

    init {
        // Initialize helper classes first
        analysisOrchestrator = AnalysisOrchestrator(
            stockfish = stockfish,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            viewModelScope = viewModelScope,
            getBoardHistory = { boardHistory },
            storeAnalysedGame = { },
            fetchOpeningExplorer = { fetchOpeningExplorer() },
            saveManualGame = { game -> gameStorage.saveManualStageGame(game) }
        )

        gameLoader = GameLoader(
            repository = repository,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            viewModelScope = viewModelScope,
            getBoardHistory = { boardHistory },
            getExploringLineHistory = { exploringLineHistory },
            settingsPrefs = settingsPrefs,
            gameStorage = gameStorage,
            analysisOrchestrator = analysisOrchestrator,
            fetchOpeningExplorer = { fetchOpeningExplorer() },
            restartStockfishAndAnalyze = { fen -> restartStockfishAndAnalyze(fen) },
            getAppVersionCode = { getAppVersionCode() }
        )

        boardNavigationManager = BoardNavigationManager(
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            getBoardHistory = { boardHistory },
            getExploringLineHistory = { exploringLineHistory },
            analysisOrchestrator = analysisOrchestrator,
            moveSoundPlayer = moveSoundPlayer
        )

        contentSourceManager = ContentSourceManager(
            repository = repository,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            viewModelScope = viewModelScope,
            loadGame = { game, server, username -> gameLoader.loadGame(game, server, username) }
        )

        liveGameManager = LiveGameManager(
            repository = repository,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            viewModelScope = viewModelScope,
            moveSoundPlayer = moveSoundPlayer
        )

        // Check if Stockfish is installed first
        val stockfishInstalled = stockfish.isStockfishInstalled()
        // Check if AI app is installed
        val aiAppInstalled = AiAppLauncher.isAiAppInstalled(application)
        _uiState.value = _uiState.value.copy(
            stockfishInstalled = stockfishInstalled,
            aiAppInstalled = aiAppInstalled
        )

        if (stockfishInstalled) {
            if (isFirstRun()) {
                resetSettingsToDefaults()
            }

            val settings = loadStockfishSettings()
            val boardSettings = loadBoardLayoutSettings()
            val graphSettings = loadGraphSettings()
            val interfaceVisibility = loadInterfaceVisibilitySettings()
            val generalSettings = loadGeneralSettings()

            val aiPrompts = loadAiPrompts()
            val lichessMaxGames = settingsPrefs.lichessMaxGames
            val retrievesList = gameStorage.loadRetrievesList()
            val hasPreviousRetrieves = retrievesList.isNotEmpty()

            _uiState.value = _uiState.value.copy(
                stockfishSettings = settings,
                boardLayoutSettings = boardSettings,
                graphSettings = graphSettings,
                interfaceVisibility = interfaceVisibility,
                generalSettings = generalSettings,
                aiPrompts = aiPrompts,
                lichessMaxGames = lichessMaxGames,
                hasPreviousRetrieves = hasPreviousRetrieves,
                previousRetrievesList = retrievesList,
                playerGamesPageSize = generalSettings.paginationPageSize,
                gameSelectionPageSize = generalSettings.paginationPageSize
            )

            viewModelScope.launch {
                val ready = stockfish.initialize()
                if (ready) {
                    analysisOrchestrator.configureForManualStage()
                }
                _uiState.value = _uiState.value.copy(stockfishReady = ready)

                // Auto-restore manual stage game from previous session
                if (ready) {
                    val manualGame = gameStorage.loadManualStageGame()
                    if (manualGame != null) {
                        gameLoader.loadAnalysedGameDirectly(manualGame)
                    }
                }
            }

            viewModelScope.launch {
                stockfish.analysisResult.collect { result ->
                    if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                        if (result != null) {
                            val expectedFen = analysisOrchestrator.currentAnalysisFen
                            if (expectedFen != null && expectedFen == _uiState.value.currentBoard.getFen()) {
                                _uiState.value = _uiState.value.copy(
                                    analysisResult = result,
                                    analysisResultFen = expectedFen
                                )
                            }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                analysisResult = null,
                                analysisResultFen = null
                            )
                        }
                    }
                }
            }

            viewModelScope.launch {
                stockfish.isReady.collect { ready ->
                    _uiState.value = _uiState.value.copy(stockfishReady = ready)
                }
            }
        }
    }

    fun checkStockfishInstalled(): Boolean = stockfish.isStockfishInstalled()

    fun checkAiAppInstalled(): Boolean {
        val installed = AiAppLauncher.isAiAppInstalled(getApplication())
        _uiState.value = _uiState.value.copy(aiAppInstalled = installed)
        return installed
    }

    fun dismissAiAppWarning() {
        _uiState.value = _uiState.value.copy(aiAppWarningDismissed = true)
    }

    fun showAiAppNotInstalledDialog() {
        // Don't show if user chose "Don't ask again"
        if (settingsPrefs.getAiAppDontAskAgain()) {
            return
        }
        _uiState.value = _uiState.value.copy(showAiAppNotInstalledDialog = true)
    }

    fun hideAiAppNotInstalledDialog() {
        _uiState.value = _uiState.value.copy(showAiAppNotInstalledDialog = false)
    }

    fun setAiAppDontAskAgain() {
        settingsPrefs.setAiAppDontAskAgain(true)
        _uiState.value = _uiState.value.copy(showAiAppNotInstalledDialog = false)
    }

    fun initializeStockfish() {
        val installed = stockfish.isStockfishInstalled()
        if (!installed) return

        _uiState.value = _uiState.value.copy(stockfishInstalled = true)

        if (isFirstRun()) {
            resetSettingsToDefaults()
        }

        val settings = loadStockfishSettings()
        val boardSettings = loadBoardLayoutSettings()
        val graphSettings = loadGraphSettings()
        val interfaceVisibility = loadInterfaceVisibilitySettings()
        val generalSettings = loadGeneralSettings()
        val aiPrompts = loadAiPrompts()
        val lichessMaxGames = settingsPrefs.lichessMaxGames

        _uiState.value = _uiState.value.copy(
            stockfishSettings = settings,
            boardLayoutSettings = boardSettings,
            graphSettings = graphSettings,
            interfaceVisibility = interfaceVisibility,
            generalSettings = generalSettings,
            aiPrompts = aiPrompts,
            lichessMaxGames = lichessMaxGames,
            playerGamesPageSize = generalSettings.paginationPageSize,
            gameSelectionPageSize = generalSettings.paginationPageSize
        )

        viewModelScope.launch {
            val ready = stockfish.initialize()
            if (ready) {
                analysisOrchestrator.configureForManualStage()
            }
            _uiState.value = _uiState.value.copy(stockfishReady = ready)
        }

        viewModelScope.launch {
            stockfish.analysisResult.collect { result ->
                if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                    if (result != null) {
                        val expectedFen = analysisOrchestrator.currentAnalysisFen
                        if (expectedFen != null && expectedFen == _uiState.value.currentBoard.getFen()) {
                            _uiState.value = _uiState.value.copy(
                                analysisResult = result,
                                analysisResultFen = expectedFen
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            analysisResult = null,
                            analysisResultFen = null
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            stockfish.isReady.collect { ready ->
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
            }
        }
    }

    // ===== GAME LOADING DELEGATION =====
    fun reloadLastGame() = gameLoader.reloadLastGame()
    fun fetchGames(server: ChessServer, username: String) = gameLoader.fetchGames(server, username)
    fun selectGame(game: LichessGame) = gameLoader.selectGame(game)
    fun dismissGameSelection() = gameLoader.dismissGameSelection()
    fun clearGame() = gameLoader.clearGame()
    fun selectGameFromRetrieve(game: LichessGame) = gameLoader.selectGameFromRetrieve(game)
    fun showPreviousRetrieves() = gameLoader.showPreviousRetrieves()
    fun dismissPreviousRetrievesSelection() = gameLoader.dismissPreviousRetrievesSelection()
    fun selectPreviousRetrieve(entry: RetrievedGamesEntry) = gameLoader.selectPreviousRetrieve(entry)
    fun dismissSelectedRetrieveGames() = gameLoader.dismissSelectedRetrieveGames()
    fun nextGameSelectionPage() = gameLoader.nextGameSelectionPage()
    fun previousGameSelectionPage() = gameLoader.previousGameSelectionPage()
    fun setLichessMaxGames(max: Int) = gameLoader.setLichessMaxGames(max)

    // PGN file loading
    fun loadGamesFromPgnContent(pgnContent: String, onMultipleEvents: ((Boolean) -> Unit)? = null) =
        gameLoader.loadGamesFromPgnContent(pgnContent, onMultipleEvents)
    fun selectPgnEvent(event: String) = gameLoader.selectPgnEvent(event)
    fun backToPgnEventList() = gameLoader.backToPgnEventList()
    fun dismissPgnEventSelection() = gameLoader.dismissPgnEventSelection()
    fun selectPgnGameFromEvent(game: LichessGame) = gameLoader.selectPgnGameFromEvent(game)
    fun selectPgnGame(game: LichessGame) = gameLoader.selectPgnGame(game)

    // ===== NAVIGATION DELEGATION =====
    fun goToStart() = boardNavigationManager.goToStart()
    fun goToEnd() = boardNavigationManager.goToEnd()
    fun goToMove(index: Int) = boardNavigationManager.goToMove(index)
    fun nextMove() = boardNavigationManager.nextMove()
    fun prevMove() = boardNavigationManager.prevMove()
    fun exploreLine(pv: String, moveIndex: Int = 0) = boardNavigationManager.exploreLine(pv, moveIndex)
    fun backToOriginalGame() = boardNavigationManager.backToOriginalGame()
    fun flipBoard() = boardNavigationManager.flipBoard()
    fun makeManualMove(from: Square, to: Square) = boardNavigationManager.makeManualMove(from, to)

    fun restartAnalysisAtMove(moveIndex: Int) = analysisOrchestrator.restartAnalysisAtMove(moveIndex)

    fun setAnalysisEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(analysisEnabled = enabled)
        if (enabled) {
            analysisOrchestrator.restartAnalysisForExploringLine()
        } else {
            stockfish.stop()
        }
    }

    // ===== ANALYSIS DELEGATION =====
    fun enterManualStageAtBiggestChange() = analysisOrchestrator.enterManualStageAtBiggestChange()
    fun enterManualStageAtMove(moveIndex: Int) = analysisOrchestrator.enterManualStageAtMove(moveIndex)

    // ===== CONTENT SOURCE DELEGATION =====
    fun showTournaments(server: ChessServer) = contentSourceManager.showTournaments(server)
    fun selectTournament(tournament: TournamentInfo) = contentSourceManager.selectTournament(tournament)
    fun backToTournamentList() = contentSourceManager.backToTournamentList()
    fun dismissTournaments() = contentSourceManager.dismissTournaments()
    fun selectTournamentGame(game: LichessGame) = contentSourceManager.selectTournamentGame(game)

    fun showBroadcasts() = contentSourceManager.showBroadcasts()
    fun selectBroadcast(broadcast: BroadcastInfo) = contentSourceManager.selectBroadcast(broadcast)
    fun selectBroadcastRound(round: BroadcastRoundInfo) = contentSourceManager.selectBroadcastRound(round)
    fun backToBroadcastList() = contentSourceManager.backToBroadcastList()
    fun dismissBroadcasts() = contentSourceManager.dismissBroadcasts()
    fun selectBroadcastGame(game: LichessGame) = contentSourceManager.selectBroadcastGame(game)

    fun showLichessTv() = contentSourceManager.showLichessTv()
    fun selectTvGame(channel: TvChannelInfo) = contentSourceManager.selectTvGame(channel)
    fun dismissLichessTv() = contentSourceManager.dismissLichessTv()

    fun showDailyPuzzle() = contentSourceManager.showDailyPuzzle()
    fun dismissDailyPuzzle() = contentSourceManager.dismissDailyPuzzle()

    fun showStreamers() = contentSourceManager.showStreamers()
    fun selectStreamer(streamer: StreamerInfo) = contentSourceManager.selectStreamer(streamer) { username, server ->
        contentSourceManager.showPlayerInfoWithServer(username, server)
    }
    fun dismissStreamers() = contentSourceManager.dismissStreamers()

    fun showPlayerInfo(username: String) = contentSourceManager.showPlayerInfo(username, null)
    fun showPlayerInfo(username: String, server: ChessServer) = contentSourceManager.showPlayerInfoWithServer(username, server)
    fun nextPlayerGamesPage() = contentSourceManager.nextPlayerGamesPage()
    fun previousPlayerGamesPage() = contentSourceManager.previousPlayerGamesPage()
    fun selectGameFromPlayerInfo(game: LichessGame) = contentSourceManager.selectGameFromPlayerInfo(game)
    fun dismissPlayerInfo() = contentSourceManager.dismissPlayerInfo()

    fun showTopRankings(server: ChessServer) = contentSourceManager.showTopRankings(server)
    fun dismissTopRankings() = contentSourceManager.dismissTopRankings()
    fun selectTopRankingPlayer(username: String, server: ChessServer) = contentSourceManager.selectTopRankingPlayer(username, server)

    // ===== LIVE GAME DELEGATION =====
    fun startLiveFollow(gameId: String) = liveGameManager.startLiveFollow(gameId)
    fun stopLiveFollow() = liveGameManager.stopLiveFollow()
    fun toggleAutoFollowLive() = liveGameManager.toggleAutoFollowLive()

    // ===== STOCKFISH HELPERS =====
    private suspend fun restartStockfishAndAnalyze(fen: String) {
        analysisOrchestrator.stop()
        val ready = stockfish.restart()
        _uiState.value = _uiState.value.copy(stockfishReady = ready)
        if (ready) {
            stockfish.newGame()
            analysisOrchestrator.configureForManualStage()
            delay(100)
            val thisRequestId = analysisOrchestrator.analysisRequestId.incrementAndGet()
            analysisOrchestrator.currentAnalysisFen = fen
            analysisOrchestrator.ensureStockfishAnalysis(fen, thisRequestId)
        }
    }

    // ===== GAME STORAGE =====

    // ===== OPENING EXPLORER =====
    fun fetchOpeningExplorer() {
        val manualSettings = _uiState.value.interfaceVisibility.manualStage
        if (!manualSettings.showOpeningExplorer && !manualSettings.showOpeningName) return

        openingExplorerJob?.cancel()
        openingExplorerJob = viewModelScope.launch {
            delay(500)
            _uiState.value = _uiState.value.copy(openingExplorerLoading = true)

            val fen = _uiState.value.currentBoard.getFen()
            when (val result = repository.getOpeningExplorer(fen)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        openingExplorerData = result.data,
                        openingExplorerLoading = false
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        openingExplorerData = null,
                        openingExplorerLoading = false
                    )
                }
            }
        }
    }

    fun toggleOpeningExplorer() {
        _uiState.value = _uiState.value.copy(showOpeningExplorer = !_uiState.value.showOpeningExplorer)
    }

    // ===== SHARE/EXPORT =====
    fun showSharePositionDialog() {
        _uiState.value = _uiState.value.copy(showSharePositionDialog = true)
    }

    fun hideSharePositionDialog() {
        _uiState.value = _uiState.value.copy(showSharePositionDialog = false)
    }

    fun getCurrentFen(): String = _uiState.value.currentBoard.getFen()

    /** Extract the Site URL from the current game's PGN headers, if it's a lichess.org or chess.com URL. */
    fun getGameSiteUrl(): String? {
        val pgn = _uiState.value.game?.pgn ?: return null
        val headers = com.eval.chess.PgnParser.parseHeaders(pgn)
        val site = headers["Site"] ?: return null
        return when {
            site.contains("lichess.org") -> site
            site.contains("chess.com") -> site
            else -> null
        }
    }

    fun copyFenToClipboard(context: android.content.Context) {
        val fen = getCurrentFen()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Chess FEN", fen)
        clipboard.setPrimaryClip(clip)
    }

    fun sharePositionAsText(context: android.content.Context) {
        val fen = getCurrentFen()
        val moveIndex = _uiState.value.currentMoveIndex
        val game = _uiState.value.game
        val analysis = _uiState.value.analysisResult

        val shareText = buildString {
            if (game != null) {
                appendLine("${game.players.white.user?.name ?: "White"} vs ${game.players.black.user?.name ?: "Black"}")
                appendLine()
            }
            appendLine("Position after move ${(moveIndex + 2) / 2}${if (moveIndex % 2 == 0) "." else "..."}")
            appendLine()
            appendLine("FEN: $fen")
            if (analysis != null && analysis.lines.isNotEmpty()) {
                val topLine = analysis.lines.first()
                val evalText = if (topLine.isMate) {
                    "Mate in ${kotlin.math.abs(topLine.mateIn)}"
                } else {
                    "%.2f".format(topLine.score)
                }
                appendLine()
                appendLine("Evaluation: $evalText (depth ${analysis.depth})")
                appendLine("Best move: ${topLine.pv.split(" ").firstOrNull() ?: "N/A"}")
            }
            appendLine()
            appendLine("Analyze at: https://lichess.org/analysis/$fen")
        }

        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share position")
        context.startActivity(shareIntent)
    }

    fun exportAnnotatedPgn(context: android.content.Context) {
        val game = _uiState.value.game ?: return
        val moveDetails = _uiState.value.moveDetails
        val analyseScores = _uiState.value.analyseScores
        val moveQualities = _uiState.value.moveQualities
        val openingName = _uiState.value.currentOpeningName ?: _uiState.value.openingName

        val pgn = com.eval.export.PgnExporter.exportAnnotatedPgn(
            game = game,
            moveDetails = moveDetails,
            analyseScores = analyseScores,
            moveQualities = moveQualities,
            openingName = openingName
        )

        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, pgn)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Chess Game PGN - ${game.players.white.user?.name} vs ${game.players.black.user?.name}")
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, "Export PGN")
        context.startActivity(shareIntent)
    }

    fun copyPgnToClipboard(context: android.content.Context) {
        val game = _uiState.value.game ?: return
        val moveDetails = _uiState.value.moveDetails
        val analyseScores = _uiState.value.analyseScores
        val moveQualities = _uiState.value.moveQualities
        val openingName = _uiState.value.currentOpeningName ?: _uiState.value.openingName

        val pgn = com.eval.export.PgnExporter.exportAnnotatedPgn(
            game = game,
            moveDetails = moveDetails,
            analyseScores = analyseScores,
            moveQualities = moveQualities,
            openingName = openingName
        )

        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Chess PGN", pgn)
        clipboard.setPrimaryClip(clip)
    }

    fun exportAsGif(context: android.content.Context) {
        if (_uiState.value.game == null) return
        val moveDetails = _uiState.value.moveDetails
        // Merge scores: use analyseScores where available, otherwise previewScores
        val analyseScores = if (_uiState.value.analyseScores.isNotEmpty()) {
            _uiState.value.previewScores + _uiState.value.analyseScores
        } else {
            _uiState.value.previewScores
        }

        _uiState.value = _uiState.value.copy(
            showGifExportDialog = true,
            gifExportProgress = 0f
        )

        viewModelScope.launch {
            try {
                val boards = mutableListOf<ChessBoard>()
                var board = ChessBoard()
                boards.add(board.copy())

                for (i in moveDetails.indices) {
                    val move = moveDetails[i]
                    val success = board.makeMove(move.san)
                    if (success) {
                        boards.add(board.copy())
                    }
                }

                val boardScores = mutableMapOf<Int, MoveScore>()
                analyseScores.forEach { (moveIndex, score) ->
                    boardScores[moveIndex + 1] = score
                }

                val moves = moveDetails.map { it.san }
                val file = com.eval.export.GifExporter.exportAsGifWithAnnotations(
                    context = context,
                    boards = boards,
                    moves = moves,
                    scores = boardScores,
                    frameDelay = 1000,
                    callback = object : com.eval.export.GifExporter.ProgressCallback {
                        override fun onProgress(current: Int, total: Int) {
                            _uiState.value = _uiState.value.copy(
                                gifExportProgress = current.toFloat() / total
                            )
                        }
                    }
                )

                _uiState.value = _uiState.value.copy(
                    showGifExportDialog = false,
                    gifExportProgress = null
                )

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/gif"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share GIF"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showGifExportDialog = false,
                    gifExportProgress = null,
                    errorMessage = "GIF export failed: ${e.message}"
                )
            }
        }
    }

    fun cancelGifExport() {
        _uiState.value = _uiState.value.copy(
            showGifExportDialog = false,
            gifExportProgress = null
        )
    }

    // ===== SETTINGS =====
    fun showSettingsDialog() {
        settingsOnDialogOpen = SettingsSnapshot(
            previewStageSettings = _uiState.value.stockfishSettings.previewStage,
            analyseStageSettings = _uiState.value.stockfishSettings.analyseStage,
            manualStageSettings = _uiState.value.stockfishSettings.manualStage
        )
        _uiState.value = _uiState.value.copy(showSettingsDialog = true)
    }

    fun hideSettingsDialog() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = false)

        val originalSettings = settingsOnDialogOpen
        val currentPreviewStageSettings = _uiState.value.stockfishSettings.previewStage
        val currentAnalyseStageSettings = _uiState.value.stockfishSettings.analyseStage
        val currentManualStageSettings = _uiState.value.stockfishSettings.manualStage

        val previewStageSettingsChanged = originalSettings?.previewStageSettings != currentPreviewStageSettings
        val analyseStageSettingsChanged = originalSettings?.analyseStageSettings != currentAnalyseStageSettings
        val manualStageSettingsChanged = originalSettings?.manualStageSettings != currentManualStageSettings

        settingsOnDialogOpen = null

        if (_uiState.value.game == null) return
        if (!previewStageSettingsChanged && !analyseStageSettingsChanged && !manualStageSettingsChanged) return

        viewModelScope.launch {
            analysisOrchestrator.stop()

            _uiState.value = _uiState.value.copy(stockfishReady = false)

            val ready = stockfish.restart()

            if (ready) {
                kotlinx.coroutines.delay(200)
                val confirmedReady = stockfish.isReady.value
                _uiState.value = _uiState.value.copy(stockfishReady = confirmedReady)

                if (!confirmedReady) return@launch
            } else {
                _uiState.value = _uiState.value.copy(stockfishReady = false)
                return@launch
            }

            if (previewStageSettingsChanged || analyseStageSettingsChanged) {
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap()
                )
                analysisOrchestrator.startAnalysis()
            } else if (manualStageSettingsChanged) {
                if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                    analysisOrchestrator.configureForManualStage()
                    analysisOrchestrator.restartAnalysisForExploringLine()
                } else {
                    analysisOrchestrator.enterManualStageAtCurrentPosition()
                }
            }
        }
    }

    fun showHelpScreen() {
        _uiState.value = _uiState.value.copy(showHelpScreen = true)
    }

    fun hideHelpScreen() {
        _uiState.value = _uiState.value.copy(showHelpScreen = false)
    }

    fun showRetrieveScreen() {
        _uiState.value = _uiState.value.copy(showRetrieveScreen = true)
    }

    fun hideRetrieveScreen() {
        _uiState.value = _uiState.value.copy(showRetrieveScreen = false)
    }

    // ===== ECO OPENING SELECTION =====
    fun loadEcoOpenings() {
        if (_uiState.value.ecoOpenings.isNotEmpty()) return // Already loaded

        _uiState.value = _uiState.value.copy(ecoOpeningsLoading = true)

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val jsonString = context.assets.open("eco_codes.json").bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)

                val openings = mutableListOf<EcoOpening>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val fen = keys.next()
                    val entry = jsonObject.getJSONObject(fen)
                    openings.add(
                        EcoOpening(
                            fen = fen,
                            eco = entry.getString("eco"),
                            name = entry.getString("name"),
                            moves = entry.getString("moves")
                        )
                    )
                }

                // Sort by ECO code
                openings.sortBy { it.eco }

                _uiState.value = _uiState.value.copy(
                    ecoOpenings = openings,
                    ecoOpeningsLoading = false
                )
            } catch (e: Exception) {
                android.util.Log.e("GameViewModel", "Error loading ECO openings: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    ecoOpeningsLoading = false,
                    errorMessage = "Failed to load openings: ${e.message}"
                )
            }
        }
    }

    fun startWithOpening(opening: EcoOpening) {
        // Create a simple PGN with the opening moves
        val pgn = """
[Event "Opening Study"]
[Site "Eval App"]
[Date "????.??.??"]
[Round "?"]
[White "White"]
[Black "Black"]
[Result "*"]
[Opening "${opening.name}"]
[ECO "${opening.eco}"]

${opening.moves} *
        """.trimIndent()

        // Hide the retrieve screen and load the game
        _uiState.value = _uiState.value.copy(showRetrieveScreen = false)

        // Load the game from PGN content
        gameLoader.loadGamesFromPgnContent(pgn) { _ ->
            // No multiple events expected for a single opening
        }
    }

    /**
     * Start Manual stage directly from a FEN position.
     * No PGN, no move list, no graphs - just board analysis.
     */
    fun startFromFen(fen: String) {
        // Replace underscores with spaces (common in URLs and clipboard pastes)
        val normalizedFen = fen.replace('_', ' ')
        // Validate FEN by trying to set up the board
        val board = com.eval.chess.ChessBoard()
        if (!board.setFen(normalizedFen)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Invalid FEN position"
            )
            return
        }

        // Create a minimal game object for FEN analysis
        val lichessGame = com.eval.data.LichessGame(
            id = "fen_${System.currentTimeMillis()}",
            rated = false,
            variant = "standard",
            speed = "classical",
            perf = null,
            status = "*",  // Ongoing/study
            winner = null,
            players = com.eval.data.Players(
                white = com.eval.data.Player(
                    user = com.eval.data.User(name = "White", id = "white"),
                    rating = null,
                    aiLevel = null
                ),
                black = com.eval.data.Player(
                    user = com.eval.data.User(name = "Black", id = "black"),
                    rating = null,
                    aiLevel = null
                )
            ),
            pgn = "[FEN \"$normalizedFen\"]\n\n*",
            moves = null,
            clock = null,
            createdAt = System.currentTimeMillis(),
            lastMoveAt = null
        )

        // Determine if it's white or black to move from FEN
        val isWhiteToMove = board.getTurn() == com.eval.chess.PieceColor.WHITE

        // Hide retrieve screen and go directly to Manual stage
        _uiState.value = _uiState.value.copy(
            showRetrieveScreen = false,
            isLoading = false,
            game = lichessGame,
            openingName = null,
            currentOpeningName = null,
            moves = emptyList(),
            moveDetails = emptyList(),
            currentBoard = board,
            currentMoveIndex = -1,
            flippedBoard = !isWhiteToMove,  // Flip board if black to move
            userPlayedBlack = !isWhiteToMove,
            previewScores = emptyMap(),
            analyseScores = emptyMap(),
            currentStage = AnalysisStage.MANUAL,
            autoAnalysisIndex = -1,
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            analysisResult = null,
            analysisResultFen = null
        )

        // Configure Stockfish for Manual stage and start analysis
        viewModelScope.launch {
            if (_uiState.value.stockfishReady) {
                analysisOrchestrator.configureForManualStage()
                analysisOrchestrator.restartAnalysisForExploringLine()
            }
        }
    }

    fun updateStockfishSettings(settings: StockfishSettings) {
        saveStockfishSettings(settings)
        _uiState.value = _uiState.value.copy(stockfishSettings = settings)
        if (_uiState.value.stockfishReady) {
            when (_uiState.value.currentStage) {
                AnalysisStage.PREVIEW -> analysisOrchestrator.configureForPreviewStage()
                AnalysisStage.ANALYSE -> analysisOrchestrator.configureForAnalyseStage()
                AnalysisStage.MANUAL -> analysisOrchestrator.configureForManualStage()
            }
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                analysisOrchestrator.restartAnalysisForExploringLine()
            }
        }
    }

    fun updateBoardLayoutSettings(settings: BoardLayoutSettings) {
        saveBoardLayoutSettings(settings)
        _uiState.value = _uiState.value.copy(boardLayoutSettings = settings)
    }

    fun updateGraphSettings(settings: GraphSettings) {
        saveGraphSettings(settings)
        _uiState.value = _uiState.value.copy(graphSettings = settings)
    }

    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        val currentSettings = _uiState.value.interfaceVisibility

        val previewChanged = currentSettings.previewStage != settings.previewStage
        val analyseChanged = currentSettings.analyseStage != settings.analyseStage

        saveInterfaceVisibilitySettings(settings)
        _uiState.value = _uiState.value.copy(interfaceVisibility = settings)

        if ((previewChanged || analyseChanged) && _uiState.value.game != null) {
            analysisOrchestrator.stop()

            _uiState.value = _uiState.value.copy(
                currentStage = AnalysisStage.PREVIEW,
                previewScores = emptyMap(),
                analyseScores = emptyMap(),
                autoAnalysisIndex = -1
            )

            viewModelScope.launch {
                val ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (ready) {
                    stockfish.newGame()
                    analysisOrchestrator.startAnalysis()
                }
            }
        }
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        saveGeneralSettings(settings)
        _uiState.value = _uiState.value.copy(
            generalSettings = settings,
            playerGamesPageSize = settings.paginationPageSize,
            gameSelectionPageSize = settings.paginationPageSize
        )
    }

    /**
     * Reset the app to the homepage (logo only), clearing all game state.
     */
    fun resetToHomepage() {
        // Stop any ongoing analysis
        analysisOrchestrator.stop()

        // Clear board histories
        boardHistory.clear()
        exploringLineHistory.clear()

        // Reset UI state to show only the homepage with logo
        _uiState.value = _uiState.value.copy(
            game = null,
            gameList = emptyList(),
            showGameSelection = false,
            showRetrieveScreen = false,  // Show homepage with logo only
            currentBoard = ChessBoard(),
            moves = emptyList(),
            moveDetails = emptyList(),
            currentMoveIndex = -1,
            analysisResult = null,
            analysisResultFen = null,
            flippedBoard = false,
            userPlayedBlack = false,
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            currentStage = AnalysisStage.PREVIEW,
            previewScores = emptyMap(),
            analyseScores = emptyMap(),
            autoAnalysisIndex = -1,
            openingName = null,
            currentOpeningName = null
        )
    }

    // ===== AI Prompts CRUD =====

    fun updateAiPrompts(prompts: List<AiPromptEntry>) {
        saveAiPrompts(prompts)
        _uiState.value = _uiState.value.copy(aiPrompts = prompts)
    }

    fun addAiPrompt(prompt: AiPromptEntry) {
        val updated = _uiState.value.aiPrompts + prompt
        updateAiPrompts(updated)
    }

    fun updateAiPrompt(prompt: AiPromptEntry) {
        val updated = _uiState.value.aiPrompts.map { if (it.id == prompt.id) prompt else it }
        updateAiPrompts(updated)
    }

    fun deleteAiPrompt(id: String) {
        val updated = _uiState.value.aiPrompts.filter { it.id != id }
        updateAiPrompts(updated)
    }

    // ===== AI Prompt Selection Dialog =====

    fun showAiPromptSelectionDialog() {
        _uiState.value = _uiState.value.copy(showAiPromptSelectionDialog = true)
    }

    fun hideAiPromptSelectionDialog() {
        _uiState.value = _uiState.value.copy(showAiPromptSelectionDialog = false)
    }

    /**
     * Launch the external AI app for game position analysis with a selected prompt.
     * Shows warning dialog if AI app is not installed.
     * @param context Android context needed for intent launching
     * @param promptEntry The selected AI prompt entry
     * @return true if AI app was launched, false if not installed
     */
    fun launchGameAnalysis(context: android.content.Context, promptEntry: AiPromptEntry): Boolean {
        if (!AiAppLauncher.isAiAppInstalled(context)) {
            showAiAppNotInstalledDialog()
            return false
        }
        val fen = _uiState.value.currentBoard.getFen()
        val whiteName = _uiState.value.game?.players?.white?.user?.name ?: ""
        val blackName = _uiState.value.game?.players?.black?.user?.name ?: ""
        val currentMoveIndex = _uiState.value.currentMoveIndex
        val lastMoveDetails = if (currentMoveIndex >= 0 && currentMoveIndex < _uiState.value.moveDetails.size) {
            _uiState.value.moveDetails[currentMoveIndex]
        } else null
        return AiAppLauncher.launchGameAnalysis(
            context, fen, promptEntry.prompt, whiteName, blackName,
            currentMoveIndex, lastMoveDetails, promptEntry.instructions
        )
    }

    /**
     * Launch the external AI app for server player analysis (Lichess/Chess.com).
     * Uses the first CHESS_SERVER_PLAYER prompt, falling back to defaults.
     */
    fun launchServerPlayerAnalysis(context: android.content.Context, playerName: String, server: String): Boolean {
        if (!AiAppLauncher.isAiAppInstalled(context)) {
            showAiAppNotInstalledDialog()
            return false
        }
        val prompts = _uiState.value.aiPrompts
        val prompt = prompts.firstOrNull { it.safeCategory == AiPromptCategory.CHESS_SERVER_PLAYER }
            ?: prompts.firstOrNull()
        val promptTemplate = prompt?.prompt ?: DEFAULT_SERVER_PLAYER_PROMPT
        val instructions = prompt?.instructions ?: ""
        return AiAppLauncher.launchServerPlayerAnalysis(context, playerName, server, promptTemplate, instructions)
    }

    /**
     * Launch the external AI app for general player analysis.
     * Uses the first PLAYER prompt, falling back to defaults.
     */
    fun launchOtherPlayerAnalysis(context: android.content.Context, playerName: String): Boolean {
        if (!AiAppLauncher.isAiAppInstalled(context)) {
            showAiAppNotInstalledDialog()
            return false
        }
        val prompts = _uiState.value.aiPrompts
        val prompt = prompts.firstOrNull { it.safeCategory == AiPromptCategory.PLAYER }
            ?: prompts.firstOrNull()
        val promptTemplate = prompt?.prompt ?: DEFAULT_OTHER_PLAYER_PROMPT
        val instructions = prompt?.instructions ?: ""
        return AiAppLauncher.launchOtherPlayerAnalysis(context, playerName, promptTemplate, instructions)
    }

    /**
     * Check if the external AI app is installed.
     */
    fun isAiAppInstalled(context: android.content.Context): Boolean {
        return AiAppLauncher.isAiAppInstalled(context)
    }

    // ===== Settings Export / Import =====

    /**
     * Export all settings to a JSON file and share via share sheet.
     */
    fun exportSettings(context: android.content.Context) {
        try {
            val json = settingsPrefs.exportAllSettings()
            val cacheDir = java.io.File(context.cacheDir, "settings_export")
            cacheDir.mkdirs()
            val file = java.io.File(cacheDir, "eval_settings.json")
            file.writeText(json)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Settings"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Import settings from a JSON file URI. Reloads all settings into UI state after import.
     * @return true if import succeeded
     */
    fun importSettings(context: android.content.Context, uri: android.net.Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val json = inputStream?.bufferedReader()?.use { it.readText() } ?: return false
            val success = settingsPrefs.importAllSettings(json)
            if (success) {
                // Reload all settings into UI state
                val settings = loadStockfishSettings()
                val boardSettings = loadBoardLayoutSettings()
                val graphSettings = loadGraphSettings()
                val interfaceVisibility = loadInterfaceVisibilitySettings()
                val generalSettings = loadGeneralSettings()
                val aiPrompts = loadAiPrompts()
                _uiState.value = _uiState.value.copy(
                    stockfishSettings = settings,
                    boardLayoutSettings = boardSettings,
                    graphSettings = graphSettings,
                    interfaceVisibility = interfaceVisibility,
                    generalSettings = generalSettings,
                    aiPrompts = aiPrompts
                )
                android.widget.Toast.makeText(context, "Settings imported", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Import failed: invalid file", android.widget.Toast.LENGTH_SHORT).show()
            }
            success
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            false
        }
    }

    // ===== REMOVED AI FUNCTIONS =====
    // The following AI functions have been removed as the external AI app now handles:
    // - requestAiAnalysis (direct API calls)
    // - AI Reports generation (multi-service reports)
    // ===== MISC =====
    fun cycleArrowMode() {
        val currentSettings = _uiState.value.stockfishSettings
        val currentMode = currentSettings.manualStage.arrowMode
        val newMode = when (currentMode) {
            ArrowMode.NONE -> ArrowMode.MAIN_LINE
            ArrowMode.MAIN_LINE -> ArrowMode.MULTI_LINES
            ArrowMode.MULTI_LINES -> ArrowMode.NONE
        }
        val newSettings = currentSettings.copy(
            manualStage = currentSettings.manualStage.copy(arrowMode = newMode)
        )
        saveStockfishSettings(newSettings)
        _uiState.value = _uiState.value.copy(stockfishSettings = newSettings)
    }

    fun toggleFullScreen() {
        val currentSettings = _uiState.value.generalSettings
        val newSettings = currentSettings.copy(
            longTapForFullScreen = !currentSettings.longTapForFullScreen
        )
        _uiState.value = _uiState.value.copy(generalSettings = newSettings)
    }

    override fun onCleared() {
        super.onCleared()
        analysisOrchestrator.autoAnalysisJob?.cancel()
        analysisOrchestrator.manualAnalysisJob?.cancel()
        openingExplorerJob?.cancel()
        liveGameManager.cancel()
        stockfish.shutdown()
        moveSoundPlayer.release()
    }
}
