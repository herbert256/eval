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
import com.eval.data.Result
import com.eval.data.StreamerInfo
import com.eval.data.TournamentInfo
import com.eval.data.TvChannelInfo
import com.google.gson.Gson
import com.eval.stockfish.StockfishEngine
import org.json.JSONObject
import com.eval.audio.MoveSoundPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    private val mainTimeline = GameTimeline()
    private val exploringTimeline = GameTimeline()
    private val boardHistory = mainTimeline.snapshotList
    private val exploringLineHistory = exploringTimeline.snapshotList

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
    private val exportShareManager: ExportShareManager
    private val settingsManager: SettingsManager

    // Opening explorer job
    private var openingExplorerJob: Job? = null

    val savedLichessUsername: String
        get() = settingsPrefs.savedLichessUsername

    val savedChessComUsername: String
        get() = settingsPrefs.savedChessComUsername

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
            fetchOpeningExplorer = { fetchOpeningExplorer() },
            saveManualGame = { game -> gameStorage.saveManualStageGame(game) },
            storeManualGameToList = { game ->
                gameStorage.storeManualGameToList(game)
                _uiState.update { it.copy(hasAnalysedGames = true) }
            }
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
            moveSoundPlayer = moveSoundPlayer,
            appendBoardHistory = { board -> boardHistory.add(board.copy()) }
        )

        exportShareManager = ExportShareManager(
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            viewModelScope = viewModelScope
        )

        settingsManager = SettingsManager(
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.value = _uiState.value.transform() },
            viewModelScope = viewModelScope,
            settingsPrefs = settingsPrefs,
            stockfish = stockfish,
            analysisOrchestrator = analysisOrchestrator
        )

        // Check if Stockfish is installed first
        val stockfishInstalled = stockfish.isStockfishInstalled()
        // Check if AI app is installed
        val aiAppInstalled = AiAppLauncher.isAiAppInstalled(application)
        _uiState.update { it.copy(
            stockfishInstalled = stockfishInstalled,
            aiAppInstalled = aiAppInstalled
        ) }

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
            val hasAnalysedGames = gameStorage.hasManualGames()
            val hasLastServerUser = settingsPrefs.lastServerUser != null

            _uiState.update { it.copy(
                stockfishSettings = settings,
                boardLayoutSettings = boardSettings,
                graphSettings = graphSettings,
                interfaceVisibility = interfaceVisibility,
                generalSettings = generalSettings,
                aiPrompts = aiPrompts,
                lichessMaxGames = lichessMaxGames,
                hasPreviousRetrieves = hasPreviousRetrieves,
                hasAnalysedGames = hasAnalysedGames,
                hasLastServerUser = hasLastServerUser,
                previousRetrievesList = retrievesList
            ) }

            viewModelScope.launch {
                val ready = stockfish.initialize()
                if (ready) {
                    analysisOrchestrator.configureForManualStage()
                }
                _uiState.update { it.copy(stockfishReady = ready) }

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
                                _uiState.update { it.copy(
                                    analysisResult = result,
                                    analysisResultFen = expectedFen
                                ) }
                            }
                        } else {
                            _uiState.update { it.copy(
                                analysisResult = null,
                                analysisResultFen = null
                            ) }
                        }
                    }
                }
            }

            viewModelScope.launch {
                stockfish.isReady.collect { ready ->
                    _uiState.update { it.copy(stockfishReady = ready) }
                }
            }
        }
    }

    fun checkStockfishInstalled(): Boolean = stockfish.isStockfishInstalled()

    fun checkAiAppInstalled(): Boolean {
        val installed = AiAppLauncher.isAiAppInstalled(getApplication())
        _uiState.update { it.copy(aiAppInstalled = installed) }
        return installed
    }

    fun dismissAiAppWarning() {
        _uiState.update { it.copy(aiAppWarningDismissed = true) }
    }

    fun showAiAppNotInstalledDialog() {
        // Don't show if user chose "Don't ask again"
        if (settingsPrefs.getAiAppDontAskAgain()) {
            return
        }
        _uiState.update { it.copy(showAiAppNotInstalledDialog = true) }
    }

    fun hideAiAppNotInstalledDialog() {
        _uiState.update { it.copy(showAiAppNotInstalledDialog = false) }
    }

    fun setAiAppDontAskAgain() {
        settingsPrefs.setAiAppDontAskAgain(true)
        _uiState.update { it.copy(showAiAppNotInstalledDialog = false) }
    }

    fun initializeStockfish() {
        val installed = stockfish.isStockfishInstalled()
        if (!installed) return

        _uiState.update { it.copy(stockfishInstalled = true) }

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

        _uiState.update { it.copy(
            stockfishSettings = settings,
            boardLayoutSettings = boardSettings,
            graphSettings = graphSettings,
            interfaceVisibility = interfaceVisibility,
            generalSettings = generalSettings,
            aiPrompts = aiPrompts,
            lichessMaxGames = lichessMaxGames
        ) }

        viewModelScope.launch {
            val ready = stockfish.initialize()
            if (ready) {
                analysisOrchestrator.configureForManualStage()
            }
            _uiState.update { it.copy(stockfishReady = ready) }
        }

        // Collectors are already set up in init block, no need to duplicate
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
    fun nextGameSelectionPage(pageSize: Int) = gameLoader.nextGameSelectionPage(pageSize)
    fun previousGameSelectionPage() = gameLoader.previousGameSelectionPage()
    fun setLichessMaxGames(max: Int) = gameLoader.setLichessMaxGames(max)

    // Previously analysed games
    fun showAnalysedGames() {
        val list = gameStorage.loadManualGamesList()
        _uiState.update { it.copy(
            analysedGamesList = list,
            showAnalysedGamesSelection = true,
            gameSelectionPage = 0
        ) }
    }

    fun dismissAnalysedGamesSelection() {
        _uiState.update { it.copy(
            showAnalysedGamesSelection = false,
            analysedGamesList = emptyList()
        ) }
    }

    fun selectAnalysedGame(game: AnalysedGame) {
        _uiState.update { it.copy(
            showAnalysedGamesSelection = false,
            analysedGamesList = emptyList()
        ) }
        gameLoader.loadAnalysedGameDirectly(game)
    }

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
        _uiState.update { it.copy(analysisEnabled = enabled) }
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
    fun nextPlayerGamesPage(pageSize: Int) = contentSourceManager.nextPlayerGamesPage(pageSize)
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
        _uiState.update { it.copy(stockfishReady = ready) }
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
            _uiState.update { it.copy(openingExplorerLoading = true) }

            val fen = _uiState.value.currentBoard.getFen()
            when (val result = repository.getOpeningExplorer(fen)) {
                is Result.Success -> {
                    _uiState.update { it.copy(
                        openingExplorerData = result.data,
                        openingExplorerLoading = false
                    ) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(
                        openingExplorerData = null,
                        openingExplorerLoading = false
                    ) }
                }
            }
        }
    }

    // ===== SHARE/EXPORT =====
    fun showSharePositionDialog() = exportShareManager.showSharePositionDialog()

    fun hideSharePositionDialog() = exportShareManager.hideSharePositionDialog()

    fun getCurrentFen(): String = exportShareManager.getCurrentFen()

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

    fun copyFenToClipboard(context: android.content.Context) = exportShareManager.copyFenToClipboard(context)

    fun sharePositionAsText(context: android.content.Context) = exportShareManager.sharePositionAsText(context)

    fun exportAnnotatedPgn(context: android.content.Context) = exportShareManager.exportAnnotatedPgn(context)

    fun copyPgnToClipboard(context: android.content.Context) = exportShareManager.copyPgnToClipboard(context)

    fun exportAsGif(context: android.content.Context) = exportShareManager.exportAsGif(context)

    fun cancelGifExport() = exportShareManager.cancelGifExport()

    // ===== SETTINGS =====
    fun showSettingsDialog() {
        settingsOnDialogOpen = SettingsSnapshot(
            previewStageSettings = _uiState.value.stockfishSettings.previewStage,
            analyseStageSettings = _uiState.value.stockfishSettings.analyseStage,
            manualStageSettings = _uiState.value.stockfishSettings.manualStage
        )
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun hideSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }

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

            _uiState.update { it.copy(stockfishReady = false) }

            val ready = stockfish.restart()

            if (ready) {
                kotlinx.coroutines.delay(200)
                val confirmedReady = stockfish.isReady.value
                _uiState.update { it.copy(stockfishReady = confirmedReady) }

                if (!confirmedReady) return@launch
            } else {
                _uiState.update { it.copy(stockfishReady = false) }
                return@launch
            }

            if (previewStageSettingsChanged || analyseStageSettingsChanged) {
                _uiState.update { it.copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap()
                ) }
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
        _uiState.update { it.copy(showHelpScreen = true) }
    }

    fun hideHelpScreen() {
        _uiState.update { it.copy(showHelpScreen = false) }
    }

    fun showRetrieveScreen() {
        _uiState.update { it.copy(showRetrieveScreen = true) }
    }

    fun hideRetrieveScreen() {
        _uiState.update { it.copy(showRetrieveScreen = false) }
    }

    // ===== ECO OPENING SELECTION =====
    fun loadEcoOpenings() {
        if (_uiState.value.ecoOpenings.isNotEmpty()) return // Already loaded

        _uiState.update { it.copy(ecoOpeningsLoading = true) }

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

                _uiState.update { it.copy(
                    ecoOpenings = openings,
                    ecoOpeningsLoading = false
                ) }
            } catch (e: Exception) {
                android.util.Log.e("GameViewModel", "Error loading ECO openings: ${e.message}")
                _uiState.update { it.copy(
                    ecoOpeningsLoading = false,
                    errorMessage = "Failed to load openings: ${e.message}"
                ) }
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
        _uiState.update { it.copy(showRetrieveScreen = false) }

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
            _uiState.update { it.copy(
                errorMessage = "Invalid FEN position"
            ) }
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
        _uiState.update { it.copy(
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
        ) }

        // Configure Stockfish for Manual stage and start analysis
        viewModelScope.launch {
            if (_uiState.value.stockfishReady) {
                analysisOrchestrator.configureForManualStage()
                analysisOrchestrator.restartAnalysisForExploringLine()
            }
        }
    }

    fun updateStockfishSettings(settings: StockfishSettings) = settingsManager.updateStockfishSettings(settings)

    fun updateBoardLayoutSettings(settings: BoardLayoutSettings) = settingsManager.updateBoardLayoutSettings(settings)

    fun updateGraphSettings(settings: GraphSettings) = settingsManager.updateGraphSettings(settings)

    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) =
        settingsManager.updateInterfaceVisibilitySettings(settings)

    fun updateGeneralSettings(settings: GeneralSettings) = settingsManager.updateGeneralSettings(settings)

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
        _uiState.update { it.copy(
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
        ) }
    }

    // ===== AI Prompts CRUD =====

    fun updateAiPrompts(prompts: List<AiPromptEntry>) = settingsManager.updateAiPrompts(prompts)

    fun addAiPrompt(prompt: AiPromptEntry) = settingsManager.addAiPrompt(prompt)

    fun updateAiPrompt(prompt: AiPromptEntry) = settingsManager.updateAiPrompt(prompt)

    fun deleteAiPrompt(id: String) = settingsManager.deleteAiPrompt(id)

    // ===== AI Prompt Selection Dialog =====

    fun showAiPromptSelectionDialog() {
        _uiState.update { it.copy(showAiPromptSelectionDialog = true) }
    }

    fun hideAiPromptSelectionDialog() {
        _uiState.update { it.copy(showAiPromptSelectionDialog = false) }
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
            context, fen, promptEntry.prompt, promptEntry.system, whiteName, blackName,
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
        val systemPrompt = prompt?.system ?: ""
        val instructions = prompt?.instructions ?: ""
        return AiAppLauncher.launchServerPlayerAnalysis(context, playerName, server, promptTemplate, systemPrompt, instructions)
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
        val systemPrompt = prompt?.system ?: ""
        val instructions = prompt?.instructions ?: ""
        return AiAppLauncher.launchOtherPlayerAnalysis(context, playerName, promptTemplate, systemPrompt, instructions)
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
    fun exportSettings(context: android.content.Context) = settingsManager.exportSettings(context)

    /**
     * Import settings from a JSON file URI. Reloads all settings into UI state after import.
     * @return true if import succeeded
     */
    fun importSettings(context: android.content.Context, uri: android.net.Uri): Boolean {
        return settingsManager.importSettings(context, uri) {
            val settings = loadStockfishSettings()
            val boardSettings = loadBoardLayoutSettings()
            val graphSettings = loadGraphSettings()
            val interfaceVisibility = loadInterfaceVisibilitySettings()
            val generalSettings = loadGeneralSettings()
            val aiPrompts = loadAiPrompts()
            _uiState.update {
                it.copy(
                    stockfishSettings = settings,
                    boardLayoutSettings = boardSettings,
                    graphSettings = graphSettings,
                    interfaceVisibility = interfaceVisibility,
                    generalSettings = generalSettings,
                    aiPrompts = aiPrompts
                )
            }
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
        _uiState.update { it.copy(stockfishSettings = newSettings) }
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
