package com.eval.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eval.chess.ChessBoard
import com.eval.chess.Square
import com.eval.data.AppSignerTrust
import com.eval.data.BroadcastInfo
import com.eval.data.BroadcastRoundInfo
import com.eval.data.ChessRepository
import com.eval.data.ChessServer
import com.eval.data.SharedChessInput
import com.eval.data.LichessGame
import com.eval.data.StreamerInfo
import com.eval.data.TournamentInfo
import com.eval.data.TvChannelInfo
import com.google.gson.Gson
import com.eval.stockfish.StockfishEngine
import org.json.JSONObject
import com.eval.audio.MoveSoundPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChessRepository()
    private val stockfish = StockfishEngine(application)
    private val prefs = application.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Helper classes for settings and game storage
    private val bundledSystemPrompts = BundledAiPrompts.loadSystemPrompts(application.assets)
    private val bundledReportPrompts = BundledAiPrompts.loadReportPrompts(application.assets)
    private val settingsPrefs = SettingsPreferences(prefs).also {
        it.seedAiSystemPrompts(bundledSystemPrompts)
        it.seedAiReportPrompts(bundledReportPrompts)
    }
    private val gameStorage = GameStorageManager.create(application, gson)
    private val signerTrust = AppSignerTrust.create(application)
    // Secrets live apart from the settings file, so exports never contain them and imports keep them.
    private val secretPrefs = application.getSharedPreferences("eval_secrets", Context.MODE_PRIVATE)

    val lichessToken: String get() = secretPrefs.getString("lichess_api_token", null).orEmpty()

    fun saveLichessToken(token: String) {
        secretPrefs.edit().putString("lichess_api_token", token.trim()).apply()
        _uiState.update { it.copy(hasLichessToken = token.isNotBlank()) }
    }

    // Move sound player for audio feedback
    private val moveSoundPlayer = MoveSoundPlayer(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Slices for the Activity and the navigation host, which must not recompose on every engine update.
    val fullScreen: StateFlow<Boolean> = uiState.map { it.generalSettings.fullScreen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.generalSettings.fullScreen)
    val canReloadGame: StateFlow<Boolean> = uiState.map { it.game != null || it.hasLastServerUser }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.let { it.game != null || it.hasLastServerUser })

    private val _sharedImport = MutableStateFlow<SharedChessInput?>(null)
    internal val sharedImport = _sharedImport.asStateFlow()

    // Only record the share here. The import itself (startFromFen / loadGame) stops analysis and
    // pending retrievals when the user commits it; dismissing the share leaves the game untouched.
    internal fun receiveSharedContent(input: SharedChessInput) {
        _sharedImport.value = input
    }

    internal fun dismissSharedContent() { _sharedImport.value = null }

    private var analysisResultCollector: Job? = null
    private var stockfishReadyCollector: Job? = null
    private var aiReportJob: Job? = null
    private var aiEngineStop: CompletableDeferred<Unit>? = null
    // Set after warning that the AI app's signer changed; the next Submit confirms and trusts it.
    private var aiSignerChangeConfirmed = false

    /** A prepared AI request, sent by the foreground UI with its Activity (see [completeAiLaunch]). */
    internal class PreparedAiLaunch(val source: AiReportContext, val entry: AiInstructionEntry, val context: AiReportContext)
    private val _pendingAiLaunch = MutableStateFlow<PreparedAiLaunch?>(null)
    internal val pendingAiLaunch = _pendingAiLaunch.asStateFlow()

    private val mainTimeline = GameTimeline()
    private val exploringTimeline = GameTimeline()
    private val boardHistory = mainTimeline.snapshotList
    private val exploringLineHistory = exploringTimeline.snapshotList

    // Helper classes for better organization
    private val analysisOrchestrator: AnalysisOrchestrator
    private val gameLoader: GameLoader
    private val boardNavigationManager: BoardNavigationManager
    private val contentSourceManager: ContentSourceManager
    private val liveGameManager: LiveGameManager
    private val exportShareManager: ExportShareManager
    private val settingsManager: SettingsManager

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
    private fun loadAiInstructions(): List<AiInstructionEntry> = settingsPrefs.loadAiInstructions()
    private fun saveAiInstructions(instructions: List<AiInstructionEntry>) = settingsPrefs.saveAiInstructions(instructions)

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

    init {
        // Initialize helper classes first
        analysisOrchestrator = AnalysisOrchestrator(
            stockfish = stockfish,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            viewModelScope = viewModelScope,
            getBoardHistory = { boardHistory },
            saveManualGame = { game -> gameStorage.saveManualStageGame(game) },
            storeManualGameToList = { game ->
                gameStorage.storeManualGameToList(game)
                _uiState.update { it.copy(hasAnalysedGames = true) }
            },
            getExploringLineHistory = { exploringLineHistory }
        )

        gameLoader = GameLoader(
            repository = repository,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            viewModelScope = viewModelScope,
            getBoardHistory = { boardHistory },
            getExploringLineHistory = { exploringLineHistory },
            settingsPrefs = settingsPrefs,
            gameStorage = gameStorage,
            analysisOrchestrator = analysisOrchestrator,
            analyzeRestoredPosition = { fen -> analyzeRestoredPosition(fen) },
            getAppVersionCode = { getAppVersionCode() },
            stopLiveFollow = { stopLiveFollow() },
            onGameCommitted = { dismissAiInstructionSelection() }
        )

        boardNavigationManager = BoardNavigationManager(
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            getBoardHistory = { boardHistory },
            getExploringLineHistory = { exploringLineHistory },
            analysisOrchestrator = analysisOrchestrator,
            moveSoundPlayer = moveSoundPlayer
        )

        contentSourceManager = ContentSourceManager(
            repository = repository,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            viewModelScope = viewModelScope,
            loadGame = { game, server, username -> gameLoader.loadGame(game, server, username) }
        )

        liveGameManager = LiveGameManager(
            repository = repository,
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            viewModelScope = viewModelScope,
            moveSoundPlayer = moveSoundPlayer,
            analyzeDisplayedPosition = { analysisOrchestrator.analyzePosition(_uiState.value.currentBoard) },
            appendBoardHistory = { board -> boardHistory.add(board.copy()) }
        )

        exportShareManager = ExportShareManager(
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            viewModelScope = viewModelScope
        )

        settingsManager = SettingsManager(
            getUiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update { it.transform() } },
            viewModelScope = viewModelScope,
            settingsPrefs = settingsPrefs,
            stockfish = stockfish,
            analysisOrchestrator = analysisOrchestrator
        )

        OpeningExplorerLoader(
            state = uiState,
            updateState = { transform -> _uiState.update { it.transform() } },
            scope = viewModelScope,
            getBoardHistory = { synchronized(boardHistory) { boardHistory.toList() } },
            getExploringLineHistory = { synchronized(exploringLineHistory) { exploringLineHistory.toList() } },
            load = { fen -> repository.getOpeningExplorer(fen, lichessToken) }
        ).observe()
        _uiState.update { it.copy(hasLichessToken = lichessToken.isNotBlank()) }

        // Check if Stockfish is installed first
        val stockfishInstalled = stockfish.isStockfishInstalled()
        // Check if AI app is installed
        val aiAppInstalled = AiAppLauncher.isAiAppInstalled(application)
        _uiState.update { it.copy(
            stockfishInstalled = stockfishInstalled,
            aiAppInstalled = aiAppInstalled,
            aiAppWarningDismissed = settingsPrefs.isAiAppStartupWarningDismissed()
        ) }

        // Observe identity even when the engine is installed after Eval starts.
        viewModelScope.launch {
            stockfish.engineName.collect { name ->
                _uiState.update { it.copy(stockfishName = name ?: "Stockfish") }
            }
        }

        // Missing preferences already use defaults. Never erase settings or
        // saved games just because no server game was retrieved this version.
        loadPersistedState()
        startEngineCollectors()
        viewModelScope.launch(Dispatchers.IO) { settingsPrefs.removeRetiredData(application.filesDir) }

        if (stockfishInstalled) startEngineIfTrusted()
    }

    /** Settings and storage-derived flags; independent of whether the engine is installed yet. */
    private fun loadPersistedState() {
        val retrievesList = gameStorage.loadRetrievesList()
        _uiState.update { it.copy(
            stockfishSettings = loadStockfishSettings(),
            boardLayoutSettings = loadBoardLayoutSettings(),
            graphSettings = loadGraphSettings(),
            interfaceVisibility = loadInterfaceVisibilitySettings(),
            generalSettings = loadGeneralSettings(),
            aiInstructions = loadAiInstructions(),
            aiSystemPrompts = settingsPrefs.loadAiSystemPrompts(),
            aiReportPrompts = settingsPrefs.loadAiReportPrompts(),
            lichessMaxGames = settingsPrefs.lichessMaxGames,
            hasPreviousRetrieves = retrievesList.isNotEmpty(),
            hasAnalysedGames = gameStorage.hasManualGames(),
            hasLastServerUser = settingsPrefs.lastServerUser != null && settingsPrefs.lastServerName == "lichess.org",
            previousRetrievesList = retrievesList
        ) }
    }

    private fun startEngineCollectors() {
        analysisResultCollector = viewModelScope.launch {
            stockfish.analysisResult.collect { result ->
                if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                    if (result != null) {
                        val expectedFen = analysisOrchestrator.currentAnalysisFen
                        if (expectedFen != null && result.fen == expectedFen && expectedFen == _uiState.value.currentBoard.getFen()) {
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

        // The engine refuses to start a re-signed Stockfish on every start/restart path; ask the user.
        viewModelScope.launch {
            stockfish.signerChanged.collect { changed ->
                if (changed) _uiState.update { it.copy(untrustedAppPackage = AppSignerTrust.STOCKFISH_PACKAGE) }
            }
        }

        stockfishReadyCollector = viewModelScope.launch {
            stockfish.isReady.collect { ready ->
                _uiState.update { it.copy(
                    stockfishReady = ready,
                    engineSupportsNnueOption = if (ready) stockfish.supportsOption("Use NNUE") else it.engineSupportsNnueOption
                ) }
            }
        }
    }

    /**
     * Start the engine and restore the startup game, unless the Stockfish package is now signed
     * by someone else than before; then the user decides first (see [trustChangedApp]).
     */
    private fun startEngineIfTrusted() {
        if (signerTrust.check(AppSignerTrust.STOCKFISH_PACKAGE) == AppSignerTrust.Status.CHANGED) {
            _uiState.update { it.copy(untrustedAppPackage = AppSignerTrust.STOCKFISH_PACKAGE) }
            return
        }
        gameLoader.loadStartupGame {
            val ready = stockfish.initialize()
            if (ready) {
                analysisOrchestrator.configureForManualStage()
            }
            // stockfishReady is owned by the isReady collector; no need
            // to set it explicitly here (was racing with the collector on init).
            ready
        }
    }

    /** The user confirmed that a package with a changed signer may be used. */
    fun trustChangedApp() {
        val packageName = _uiState.value.untrustedAppPackage ?: return
        signerTrust.trustCurrent(packageName)
        _uiState.update { it.copy(untrustedAppPackage = null) }
        if (packageName != AppSignerTrust.STOCKFISH_PACKAGE) return
        when {
            // Found at startup: nothing was loaded yet.
            _uiState.value.game == null -> startEngineIfTrusted()
            // Found by a later restart: resume analysis of what is on screen.
            _uiState.value.currentStage == AnalysisStage.MANUAL -> analysisOrchestrator.restartAnalysisForExploringLine()
            else -> analysisOrchestrator.enterManualStageAtCurrentPosition()
        }
    }

    fun checkStockfishInstalled(): Boolean = stockfish.isStockfishInstalled()

    fun checkAiAppInstalled(): Boolean {
        val installed = AiAppLauncher.isAiAppInstalled(getApplication())
        _uiState.update { it.copy(aiAppInstalled = installed) }
        return installed
    }

    fun dismissAiAppWarning() {
        settingsPrefs.setAiAppStartupWarningDismissed(true)
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
        loadPersistedState()
        startEngineIfTrusted()
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
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { gameStorage.loadManualGamesList() }
            _uiState.update { it.copy(
                analysedGamesList = list,
                showAnalysedGamesSelection = true,
                gameSelectionPage = 0
            ) }
        }
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
    fun loadPgnFile(uri: android.net.Uri, onMultipleEvents: ((Boolean) -> Unit)? = null) =
        gameLoader.loadPgnFile({ com.eval.data.ChessDocumentReader.readPgnFile(getApplication(), uri) }, onMultipleEvents)
    fun selectPgnEvent(event: String) = gameLoader.selectPgnEvent(event)
    fun backToPgnEventList() = gameLoader.backToPgnEventList()
    fun dismissPgnEventSelection() = gameLoader.dismissPgnEventSelection()
    fun selectPgnGameFromEvent(game: LichessGame) = gameLoader.selectPgnGameFromEvent(game)

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
    fun choosePromotion(piece: com.eval.chess.PieceType) = boardNavigationManager.choosePromotion(piece)
    fun cancelPromotion() = boardNavigationManager.cancelPromotion()

    fun restartAnalysisAtMove(moveIndex: Int) = analysisOrchestrator.restartAnalysisAtMove(moveIndex)

    // Engine work pauses while Eval is not visible and resumes when it returns.
    private var inForeground = true

    fun onAppBackgrounded() {
        inForeground = false
        analysisOrchestrator.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        inForeground = true
        analysisOrchestrator.onAppForegrounded()
        checkAiAppInstalled()  // The AI app may have been installed or removed meanwhile.
    }

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

    fun showStreamers() = contentSourceManager.showStreamers()
    fun selectStreamer(streamer: StreamerInfo) = contentSourceManager.selectStreamer(streamer) { username, server ->
        contentSourceManager.showPlayerInfoWithServer(username, server)
    }
    fun dismissStreamers() = contentSourceManager.dismissStreamers()

    fun showPlayerInfo(username: String) = contentSourceManager.showPlayerInfoWithServer(
        username, if (getGameSiteUrl() != null) ChessServer.LICHESS else ChessServer.LOCAL
    )
    fun showPlayerInfo(username: String, server: ChessServer) = contentSourceManager.showPlayerInfoWithServer(username, server)
    fun nextPlayerGamesPage(pageSize: Int) = contentSourceManager.nextPlayerGamesPage(pageSize)
    fun previousPlayerGamesPage() = contentSourceManager.previousPlayerGamesPage()
    fun setPlayerGamesPage(page: Int) = contentSourceManager.setPlayerGamesPage(page)
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
    private suspend fun analyzeRestoredPosition(fen: String) {
        // Startup has already initialized Stockfish. Reuse it rather than
        // replacing a healthy process just to restore a saved board.
        if (stockfish.isReady.value) {
            stockfish.newGame()
            analysisOrchestrator.configureForManualStage()
        }
        val thisRequestId = analysisOrchestrator.analysisRequestId.incrementAndGet()
        analysisOrchestrator.currentAnalysisFen = fen
        analysisOrchestrator.ensureStockfishAnalysis(fen, thisRequestId)
    }

    // ===== GAME STORAGE =====

    // ===== SHARE/EXPORT =====
    fun showSharePositionDialog() = exportShareManager.showSharePositionDialog()

    fun hideSharePositionDialog() = exportShareManager.hideSharePositionDialog()

    /** Extract the Site URL from the current game's PGN headers, if it's a lichess.org URL. */
    fun getGameSiteUrl(): String? {
        val pgn = _uiState.value.game?.pgn ?: return null
        return gameSiteUrl(pgn)
    }

    fun viewGameOnSite(context: Context) {
        val url = getGameSiteUrl() ?: return
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            _uiState.update { it.copy(errorMessage = "No browser is available to open this game.") }
        } catch (_: SecurityException) {
            _uiState.update { it.copy(errorMessage = "This game link could not be opened.") }
        }
    }

    fun copyFenToClipboard(context: android.content.Context) = exportShareManager.copyFenToClipboard(context)

    fun sharePositionAsText(context: android.content.Context) = exportShareManager.sharePositionAsText(context)

    fun exportAnnotatedPgn(context: android.content.Context) = exportShareManager.exportAnnotatedPgn(context)

    fun copyPgnToClipboard(context: android.content.Context) = exportShareManager.copyPgnToClipboard(context)

    fun exportAsGif(context: android.content.Context) = exportShareManager.exportAsGif(context)

    fun cancelGifExport() = exportShareManager.cancelGifExport()

    /** Share sheets prepared in the background, launched by the foreground UI. */
    internal val shareRequests: kotlinx.coroutines.flow.Flow<android.content.Intent> get() = exportShareManager.shareRequests

    // ===== SETTINGS =====
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
                // 428 KB of JSON: parse off the main thread.
                val openings = withContext(Dispatchers.Default) {
                    val jsonString = context.assets.open("eco_codes.json").bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(jsonString)

                    val list = mutableListOf<EcoOpening>()
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val fen = keys.next()
                        val entry = jsonObject.getJSONObject(fen)
                        list.add(
                            EcoOpening(
                                fen = fen,
                                eco = entry.getString("eco"),
                                name = entry.getString("name"),
                                moves = entry.getString("moves")
                            )
                        )
                    }

                    // Sort by ECO code
                    list.sortedBy { it.eco }
                }

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
    fun startFromFen(fen: String): Boolean {
        // Replace underscores with spaces (common in URLs and clipboard pastes)
        val normalizedFen = fen.replace('_', ' ')
        // Validate FEN by trying to set up the board
        val board = com.eval.chess.ChessBoard()
        if (!board.setFen(normalizedFen)) {
            val reason = com.eval.chess.ChessBoard.fenValidationError(normalizedFen)
            _uiState.update { it.copy(
                errorMessage = if (reason != null) "Invalid FEN position: $reason" else "Invalid FEN position"
            ) }
            return false
        }

        gameLoader.invalidatePendingRetrieval()
        analysisOrchestrator.stop()
        liveGameManager.stopLiveFollow()
        dismissAiInstructionSelection()
        mainTimeline.resetToInitial(board)
        exploringTimeline.clear()

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
            pgn = "[SetUp \"1\"]\n[FEN \"${board.getFen()}\"]\n\n*",
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
            errorMessage = null,
            game = lichessGame,
            gameSelectionServer = ChessServer.LOCAL,
            gameLoadVersion = it.gameLoadVersion + 1,
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
            moveQualities = emptyMap(),
            openingExplorerData = null,
            openingExplorerLoading = false,
            openingExplorerError = null,
            currentStage = AnalysisStage.MANUAL,
            autoAnalysisIndex = -1,
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            analysisResult = null,
            analysisResultFen = null
        ) }

        gameStorage.saveManualStageGame(AnalysedGame(
            timestamp = System.currentTimeMillis(), whiteName = "White", blackName = "Black",
            result = "*", pgn = lichessGame.pgn.orEmpty(), moves = emptyList(), moveDetails = emptyList(),
            previewScores = emptyMap(), analyseScores = emptyMap()
        ))

        // Configure Stockfish for Manual stage and start analysis
        viewModelScope.launch {
            if (_uiState.value.stockfishReady) {
                analysisOrchestrator.configureForManualStage()
                analysisOrchestrator.restartAnalysisForExploringLine()
            }
        }
        return true
    }

    fun updateStockfishSettings(settings: StockfishSettings) = settingsManager.updateStockfishSettings(settings)

    fun updateBoardLayoutSettings(settings: BoardLayoutSettings) = settingsManager.updateBoardLayoutSettings(settings)

    fun updateGraphSettings(settings: GraphSettings) = settingsManager.updateGraphSettings(settings)

    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) =
        settingsManager.updateInterfaceVisibilitySettings(settings)

    fun updateGeneralSettings(settings: GeneralSettings) = settingsManager.updateGeneralSettings(settings)

    // ===== AI Instructions CRUD =====

    fun addAiInstruction(entry: AiInstructionEntry) = settingsManager.addAiInstruction(entry)

    fun updateAiInstruction(entry: AiInstructionEntry) = settingsManager.updateAiInstruction(entry)

    fun deleteAiInstruction(id: String) = settingsManager.deleteAiInstruction(id)

    fun saveAiPrompt(entry: AiPromptEntry, system: Boolean) = settingsManager.saveAiPrompt(entry, system)

    fun deleteAiPrompt(id: String, system: Boolean) = settingsManager.deleteAiPrompt(id, system)

    // ===== Named AI instructions =====

    fun requestGameAiReport() {
        dismissAiInstructionSelection()
        val state = _uiState.value
        val server = getGameSiteUrl()?.let { gameSiteHost(it) }.orEmpty()
        val moveIndex = if (state.isExploringLine) -1 else state.currentMoveIndex
        val data = AiAppLauncher.gameContext(
            fen = state.currentBoard.getFen(),
            whiteName = state.game?.players?.white?.user?.name.orEmpty(),
            blackName = state.game?.players?.black?.user?.name.orEmpty(),
            server = server, pgn = state.game?.pgn.orEmpty(),
            currentMoveIndex = moveIndex,
            lastMoveDetails = state.moveDetails.getOrNull(moveIndex)
        )
        _uiState.update { it.copy(pendingAiReport = data, aiReportSelection = settingsPrefs.loadAiReportSelection()) }
    }

    fun requestPlayerAiReport(playerName: String, server: String = "") {
        dismissAiInstructionSelection()
        _uiState.update { it.copy(pendingAiReport = AiReportContext(
            title = "Player Analysis: $playerName", player = playerName, server = server
        ), aiReportSelection = settingsPrefs.loadAiReportSelection()) }
    }

    fun dismissAiInstructionSelection() {
        aiReportJob?.cancel()
        aiReportJob = null
        aiEngineStop = null
        aiSignerChangeConfirmed = false
        _pendingAiLaunch.value = null
        _uiState.update { it.copy(pendingAiReport = null, aiReportDraft = null, aiReportEditing = false, aiMovesProgress = null,
            aiEngineProgress = null, aiEngineStopping = false, aiReportError = null) }
    }

    fun stopAiEngineAndContinue() {
        val stop = aiEngineStop ?: return
        if (stop.complete(Unit)) _uiState.update { it.copy(aiEngineStopping = true) }
    }

    fun updateAiReportSelection(selection: AiReportSelection) {
        val state = _uiState.value
        if (state.pendingAiReport == null || aiReportJob?.isActive == true) return
        val available = selection.available(state.aiSystemPrompts, state.aiReportPrompts, state.aiInstructions)
        settingsPrefs.saveAiReportSelection(available)
        _uiState.update { it.copy(aiReportSelection = available,
            aiReportDraft = if (available == it.aiReportSelection) it.aiReportDraft else null, aiReportError = null) }
    }

    fun editAiReport() {
        val state = _uiState.value
        if (state.pendingAiReport == null || aiReportJob?.isActive == true) return
        val choice = state.aiReportSelection
        val draft = state.aiReportDraft ?: AiAppLauncher.prepareDraft(
            state.aiSystemPrompts.find { it.id == choice.systemPromptId }?.text,
            state.aiReportPrompts.find { it.id == choice.promptId }?.text,
            state.aiInstructions.find { it.id == choice.instructionId }?.instructions.orEmpty()
        )
        _uiState.update { it.copy(aiReportDraft = draft, aiReportEditing = true, aiReportError = null) }
    }

    fun updateAiReportDraft(draft: AiReportDraft) {
        if (aiReportJob?.isActive != true && _uiState.value.aiReportEditing) {
            _uiState.update { it.copy(aiReportDraft = draft, aiReportError = null) }
        }
    }

    fun backToAiReportSelection() {
        if (aiReportJob?.isActive != true) _uiState.update { it.copy(aiReportEditing = false, aiReportError = null) }
    }

    fun submitAiReport(context: android.content.Context) {
        val data = _uiState.value.pendingAiReport ?: return
        val draft = _uiState.value.aiReportDraft ?: return
        if (!_uiState.value.aiReportEditing || listOf(draft.systemPrompt, draft.prompt, draft.instructions).all { it.isBlank() }) return
        if (aiReportJob?.isActive == true) return
        if (!AiAppLauncher.isAiAppInstalled(context)) {
            showAiAppNotInstalledDialog()
            return
        }
        if (signerTrust.check(AppSignerTrust.AI_PACKAGE) == AppSignerTrust.Status.CHANGED) {
            if (!aiSignerChangeConfirmed) {
                aiSignerChangeConfirmed = true
                _uiState.update { it.copy(aiReportError = "Nothing was sent: the installed AI app is signed by a different " +
                    "developer than before. If you installed it yourself, tap Submit again to trust it.") }
                return
            }
            signerTrust.trustCurrent(AppSignerTrust.AI_PACKAGE)
        }
        aiSignerChangeConfirmed = false
        val settings = _uiState.value.stockfishSettings
        val resolved = AiAppLauncher.composeInstruction(draft)
        val usedNames = AiAppLauncher.usedContextNames(resolved.instructions)
        val needsEngine = "moves" in usedNames || "engine" in usedNames
        // Only a weak reference: preparation can take minutes and must not keep an Activity alive.
        val launcher = java.lang.ref.WeakReference(context)
        // The moves that led here let Stockfish see repetitions in the AI evaluations too.
        val history = analysisOrchestrator.historyFor(data.fen)
        val stop = CompletableDeferred<Unit>()
        _uiState.update { it.copy(aiMovesProgress = "Preparing AI request…",
            aiEngineProgress = null, aiEngineStopping = false, aiReportError = null) }
        aiReportJob = viewModelScope.launch {
            // The dedicated engines get fixed search times; don't let Manual analysis compete for the CPU.
            val pausedManual = needsEngine && _uiState.value.currentStage == AnalysisStage.MANUAL
            if (pausedManual) {
                analysisOrchestrator.manualAnalysisJob?.cancel()
                stockfish.stop()
            }
            try {
                val moves = if ("moves" in usedNames) {
                    AiMovesList(getApplication()).generate(data.fen, settings.movesListForAi, history) { completed, total ->
                        _uiState.update {
                            if (it.pendingAiReport !== data) it else it.copy(
                                aiMovesProgress = "Evaluating moves: $completed of $total"
                            )
                        }
                    }
                } else ""
                val engine = if ("engine" in usedNames) {
                    _uiState.update { if (it.pendingAiReport !== data) it else it.copy(
                        aiMovesProgress = "Finding the best ${settings.engineMovesForAi.multiPv} Stockfish lines…"
                    ) }
                    aiEngineStop = stop
                    AiEngineLines(getApplication()).generate(data.fen, settings.engineMovesForAi, stop, history) { progress ->
                        _uiState.update { if (it.pendingAiReport !== data) it else it.copy(aiEngineProgress = progress) }
                    }
                } else ""
                ensureActive()
                if (_uiState.value.pendingAiReport !== data) return@launch
                val prepared = PreparedAiLaunch(data, resolved, data.copy(moves = moves, engine = engine))
                val caller = launcher.get()
                // Launch with the caller while Eval is visible; otherwise the foreground UI sends it
                // when the user returns (a background launch would be blocked by Android).
                if (inForeground && caller != null && (caller as? android.app.Activity)?.isDestroyed != true) {
                    performAiLaunch(caller, prepared)
                } else {
                    _pendingAiLaunch.value = prepared
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update { if (it.pendingAiReport !== data) it else it.copy(
                    aiReportError = "Stockfish timed out. Tap Submit to try again."
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { if (it.pendingAiReport !== data) it else it.copy(
                    aiReportError = "Could not prepare the AI request. ${e.message.orEmpty()} Tap Submit to try again."
                ) }
            } finally {
                if (aiEngineStop === stop) aiEngineStop = null
                _uiState.update { if (it.pendingAiReport !== data) it else it.copy(
                    aiMovesProgress = null, aiEngineProgress = null, aiEngineStopping = false) }
                if (pausedManual) viewModelScope.launch {
                    if (_uiState.value.currentStage == AnalysisStage.MANUAL) analysisOrchestrator.restartAnalysisForExploringLine()
                }
            }
        }
    }

    /**
     * Send a prepared AI request. Called by the UI while it is in the foreground, with its own
     * Activity, so preparation that finishes in the background waits for the user to return
     * instead of launching from a stale or background context.
     */
    internal fun completeAiLaunch(context: android.content.Context) {
        val launch = _pendingAiLaunch.value ?: return
        _pendingAiLaunch.value = null
        performAiLaunch(context, launch)
    }

    private fun performAiLaunch(context: android.content.Context, launch: PreparedAiLaunch) {
        if (_uiState.value.pendingAiReport !== launch.source) return
        if (AiAppLauncher.launchAiReport(context, launch.entry, launch.context)) {
            _uiState.update { it.copy(pendingAiReport = null, aiReportDraft = null, aiReportEditing = false, aiMovesProgress = null,
                aiEngineProgress = null, aiEngineStopping = false) }
        }
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
     */
    fun importSettings(context: android.content.Context, uri: android.net.Uri) {
        settingsManager.importSettings(context, uri) {
            settingsPrefs.seedAiSystemPrompts(bundledSystemPrompts)
            settingsPrefs.seedAiReportPrompts(bundledReportPrompts)
            loadPersistedState()
            _uiState.update { it.copy(aiReportSelection = settingsPrefs.loadAiReportSelection()) }
            // Apply imported engine settings to the running engine too.
            settingsManager.updateStockfishSettings(_uiState.value.stockfishSettings)
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
        analysisResultCollector?.cancel()
        stockfishReadyCollector?.cancel()
        analysisOrchestrator.autoAnalysisJob?.cancel()
        analysisOrchestrator.manualAnalysisJob?.cancel()
        liveGameManager.cancel()
        stockfish.shutdown()
        moveSoundPlayer.release()
    }
}
