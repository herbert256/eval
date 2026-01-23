package com.eval.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eval.chess.ChessBoard
import com.eval.chess.PgnParser
import com.eval.data.AiAnalysisRepository
import com.eval.data.AiAnalysisResponse
import com.eval.data.AiService
import com.eval.data.ChessRepository
import com.eval.data.ChessServer
import com.eval.data.LichessGame
import com.eval.data.Result
import com.google.gson.Gson
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChessRepository()
    private val stockfish = StockfishEngine(application)
    private val aiAnalysisRepository = AiAnalysisRepository()
    private val prefs = application.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Helper classes for settings and game storage
    private val settingsPrefs = SettingsPreferences(prefs)
    private val gameStorage = GameStorageManager(prefs, gson)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var boardHistory = mutableListOf<ChessBoard>()
    private var exploringLineHistory = mutableListOf<ChessBoard>()
    private var autoAnalysisJob: Job? = null

    // Track settings when dialog opens to detect changes
    private var settingsOnDialogOpen: SettingsSnapshot? = null

    private data class SettingsSnapshot(
        val previewStageSettings: PreviewStageSettings,
        val analyseStageSettings: AnalyseStageSettings,
        val manualStageSettings: ManualStageSettings
    )

    val savedLichessUsername: String
        get() = settingsPrefs.savedLichessUsername

    val savedChessComUsername: String
        get() = settingsPrefs.savedChessComUsername

    val savedActiveServer: ChessServer?
        get() = settingsPrefs.savedActiveServer

    val savedActivePlayer: String?
        get() = settingsPrefs.savedActivePlayer

    private fun loadStockfishSettings(): StockfishSettings = settingsPrefs.loadStockfishSettings()
    private fun saveStockfishSettings(settings: StockfishSettings) = settingsPrefs.saveStockfishSettings(settings)
    private fun loadBoardLayoutSettings(): BoardLayoutSettings = settingsPrefs.loadBoardLayoutSettings()
    private fun saveBoardLayoutSettings(settings: BoardLayoutSettings) = settingsPrefs.saveBoardLayoutSettings(settings)
    private fun loadGraphSettings(): GraphSettings = settingsPrefs.loadGraphSettings()
    private fun saveGraphSettings(settings: GraphSettings) = settingsPrefs.saveGraphSettings(settings)
    private fun loadInterfaceVisibilitySettings(): InterfaceVisibilitySettings = settingsPrefs.loadInterfaceVisibilitySettings()
    private fun saveInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) = settingsPrefs.saveInterfaceVisibilitySettings(settings)
    private fun loadGeneralSettings(): GeneralSettings = settingsPrefs.loadGeneralSettings()
    @Suppress("UNUSED_PARAMETER")
    private fun saveGeneralSettings(settings: GeneralSettings) = settingsPrefs.saveGeneralSettings(settings)
    private fun loadAiSettings(): AiSettings = settingsPrefs.loadAiSettings()
    private fun saveAiSettings(settings: AiSettings) = settingsPrefs.saveAiSettings(settings)

    private fun saveCurrentAnalysedGame(analysedGame: AnalysedGame) = gameStorage.saveCurrentAnalysedGame(analysedGame)
    private fun loadCurrentAnalysedGame(): AnalysedGame? = gameStorage.loadCurrentAnalysedGame()
    private fun loadRetrievesList(): List<RetrievedGamesEntry> = gameStorage.loadRetrievesList()
    private fun loadGamesForRetrieve(entry: RetrievedGamesEntry): List<LichessGame> = gameStorage.loadGamesForRetrieve(entry)
    private fun loadAnalysedGames(): List<AnalysedGame> = gameStorage.loadAnalysedGames()

    private fun configureForPreviewStage() {
        val settings = _uiState.value.stockfishSettings.previewStage
        stockfish.configure(settings.threads, settings.hashMb, 1, settings.useNnue) // MultiPV=1 for preview stage
    }

    private fun configureForAnalyseStage() {
        val settings = _uiState.value.stockfishSettings.analyseStage
        stockfish.configure(settings.threads, settings.hashMb, 1, settings.useNnue) // MultiPV=1 for analyse stage
    }

    private fun configureForManualStage() {
        val settings = _uiState.value.stockfishSettings.manualStage
        stockfish.configure(settings.threads, settings.hashMb, settings.multiPv, settings.useNnue)
    }

    /**
     * Validate that activePlayer is set and matches either the white or black player.
     * Returns an error message if validation fails, null if validation passes.
     */
    private fun validateActivePlayer(stage: AnalysisStage): String? {
        val game = _uiState.value.game ?: return "No game loaded"
        val activePlayer = _uiState.value.activePlayer

        if (activePlayer.isNullOrBlank()) {
            return "ActivePlayer not set at ${stage.name} stage"
        }

        val whiteName = game.players.white.user?.name?.lowercase()
            ?: game.players.white.aiLevel?.let { "stockfish $it" }
            ?: "anonymous"
        val blackName = game.players.black.user?.name?.lowercase()
            ?: game.players.black.aiLevel?.let { "stockfish $it" }
            ?: "anonymous"
        val activePlayerLower = activePlayer.lowercase()

        if (activePlayerLower != whiteName && activePlayerLower != blackName) {
            return "ActivePlayer '$activePlayer' does not match white ('$whiteName') or black ('$blackName') at ${stage.name} stage"
        }

        return null // Validation passed
    }

    /**
     * Run activePlayer validation and set error state if it fails.
     * Returns true if validation passed, false otherwise.
     */
    private fun checkActivePlayer(stage: AnalysisStage): Boolean {
        val error = validateActivePlayer(stage)
        if (error != null) {
            android.util.Log.e("ActivePlayer", "VALIDATION FAILED: $error")
            _uiState.value = _uiState.value.copy(activePlayerError = error)
            return false
        }
        return true
    }

    /**
     * Dismiss the activePlayer error popup.
     */
    fun dismissActivePlayerError() {
        _uiState.value = _uiState.value.copy(activePlayerError = null)
    }

    /**
     * Get the current app version code.
     */
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

    /**
     * Check if this is a first run (fresh install or app update).
     * Returns true if user hasn't made a game retrieval choice for this app version.
     */
    private fun isFirstRun(): Boolean {
        val savedVersionCode = settingsPrefs.getFirstGameRetrievedVersion()
        return savedVersionCode != getAppVersionCode()
    }

    /**
     * Mark that the user has made their first game retrieval choice for this app version.
     */
    private fun markFirstRunComplete() {
        settingsPrefs.setFirstGameRetrievedVersion(getAppVersionCode())
    }

    /**
     * Reset all settings to their default values.
     * Called on first run after fresh install or app update.
     */
    private fun resetSettingsToDefaults() {
        settingsPrefs.resetAllSettingsToDefaults()
    }

    init {
        // Check if Stockfish is installed first
        val stockfishInstalled = stockfish.isStockfishInstalled()
        _uiState.value = _uiState.value.copy(stockfishInstalled = stockfishInstalled)

        // Only proceed with initialization if Stockfish is installed
        if (stockfishInstalled) {
            // Reset settings to defaults on first run (fresh install or app update)
            if (isFirstRun()) {
                resetSettingsToDefaults()
            }

            // Load saved settings (will use defaults if reset or not previously set)
            val settings = loadStockfishSettings()
            val boardSettings = loadBoardLayoutSettings()
            val graphSettings = loadGraphSettings()
            val interfaceVisibility = loadInterfaceVisibilitySettings()
            val generalSettings = loadGeneralSettings()
            val aiSettings = loadAiSettings()
            val lichessMaxGames = settingsPrefs.lichessMaxGames
            val chessComMaxGames = settingsPrefs.chessComMaxGames
            val hasActive = savedActiveServer != null && savedActivePlayer != null
            // Check for previous retrieves
            val retrievesList = loadRetrievesList()
            val hasPreviousRetrieves = retrievesList.isNotEmpty()
            // Check for stored analysed games
            val hasAnalysedGames = gameStorage.hasAnalysedGames()
            _uiState.value = _uiState.value.copy(
                stockfishSettings = settings,
                boardLayoutSettings = boardSettings,
                graphSettings = graphSettings,
                interfaceVisibility = interfaceVisibility,
                generalSettings = generalSettings,
                aiSettings = aiSettings,
                lichessMaxGames = lichessMaxGames,
                chessComMaxGames = chessComMaxGames,
                hasActive = hasActive,
                hasPreviousRetrieves = hasPreviousRetrieves,
                previousRetrievesList = retrievesList,
                hasAnalysedGames = hasAnalysedGames
            )

            // Initialize Stockfish with manual stage settings (default)
            viewModelScope.launch {
                val ready = stockfish.initialize()
                if (ready) {
                    configureForManualStage()
                }
                _uiState.value = _uiState.value.copy(stockfishReady = ready)

                // Auto-load the last user's most recent game and start analysis
                // Skip on first run (after install or update) - user must make a choice first
                if (ready && !isFirstRun()) {
                    autoLoadLastGame()
                }
            }

            // Observe analysis results (only for Preview/Analyse stages - Manual stage handles its own updates)
            viewModelScope.launch {
                stockfish.analysisResult.collect { result ->
                    // In Manual stage, results are handled directly by ensureStockfishAnalysis
                    // to avoid race conditions. Only update UI here for other stages.
                    if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                        if (result != null) {
                            val expectedFen = currentAnalysisFen
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

            // Observe engine ready state
            viewModelScope.launch {
                stockfish.isReady.collect { ready ->
                    _uiState.value = _uiState.value.copy(stockfishReady = ready)
                }
            }
        }
    }

    /**
     * Check if Stockfish is installed. Returns true if installed.
     */
    fun checkStockfishInstalled(): Boolean {
        return stockfish.isStockfishInstalled()
    }

    /**
     * Initialize Stockfish after it has been installed.
     * Called when the app detects Stockfish was installed while on the "not installed" screen.
     */
    fun initializeStockfish() {
        val installed = stockfish.isStockfishInstalled()
        if (!installed) return

        _uiState.value = _uiState.value.copy(stockfishInstalled = true)

        // Reset settings to defaults on first run (fresh install or app update)
        if (isFirstRun()) {
            resetSettingsToDefaults()
        }

        // Load saved settings (will use defaults if reset or not previously set)
        val settings = loadStockfishSettings()
        val boardSettings = loadBoardLayoutSettings()
        val graphSettings = loadGraphSettings()
        val interfaceVisibility = loadInterfaceVisibilitySettings()
        val generalSettings = loadGeneralSettings()
        val aiSettings = loadAiSettings()
        val lichessMaxGames = settingsPrefs.lichessMaxGames
        val chessComMaxGames = settingsPrefs.chessComMaxGames
        val hasActive = savedActiveServer != null && savedActivePlayer != null
        _uiState.value = _uiState.value.copy(
            stockfishSettings = settings,
            boardLayoutSettings = boardSettings,
            graphSettings = graphSettings,
            interfaceVisibility = interfaceVisibility,
            generalSettings = generalSettings,
            aiSettings = aiSettings,
            lichessMaxGames = lichessMaxGames,
            chessComMaxGames = chessComMaxGames,
            hasActive = hasActive
        )

        // Initialize Stockfish with manual stage settings (default)
        viewModelScope.launch {
            val ready = stockfish.initialize()
            if (ready) {
                configureForManualStage()
            }
            _uiState.value = _uiState.value.copy(stockfishReady = ready)

            // Auto-load the last user's most recent game and start analysis
            // Skip on first run (after install or update) - user must make a choice first
            if (ready && !isFirstRun()) {
                autoLoadLastGame()
            }
        }

        // Observe analysis results (only for Preview/Analyse stages - Manual stage handles its own updates)
        viewModelScope.launch {
            stockfish.analysisResult.collect { result ->
                // In Manual stage, results are handled directly by ensureStockfishAnalysis
                // to avoid race conditions. Only update UI here for other stages.
                if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                    if (result != null) {
                        val expectedFen = currentAnalysisFen
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

        // Observe engine ready state
        viewModelScope.launch {
            stockfish.isReady.collect { ready ->
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
            }
        }
    }

    /**
     * Automatically load a game and start analysis on app startup.
     * First tries to load the stored current analysed game (goes directly to Manual stage),
     * then falls back to fetching the most recent game from Lichess for DrNykterstein.
     */
    private suspend fun autoLoadLastGame() {
        // First, try to load the stored current analysed game
        val storedGame = loadCurrentAnalysedGame()
        if (storedGame != null) {
            loadAnalysedGameDirectly(storedGame) // Load directly into Manual stage
            return
        }

        // No stored game - check if we have an Active player/server stored
        val server = savedActiveServer
        val username = savedActivePlayer
        if (server == null || username == null) {
            // No Active stored, show the First card (nothing to auto-load)
            return
        }

        // Fetch the last game from Active player/server
        fetchLastGameFromServer(server, username)
    }

    /**
     * Reload the last game from the stored Active player/server.
     * Called when user clicks the reload button.
     * Uses the saved Active to fetch fresh game.
     */
    fun reloadLastGame() {
        val server = savedActiveServer ?: return
        val player = savedActivePlayer ?: return

        viewModelScope.launch {
            fetchLastGameFromServer(server, player)
        }
    }

    /**
     * Fetch the most recent game from a specific server for a username.
     * Used by the reload button - always fetches fresh from the server.
     */
    private suspend fun fetchLastGameFromServer(server: ChessServer, username: String) {
        if (username.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        val result = when (server) {
            ChessServer.LICHESS -> repository.getLichessGames(username, 1)
            ChessServer.CHESS_COM -> repository.getChessComGames(username, 1)
        }

        when (result) {
            is Result.Success -> {
                val games = result.data
                if (games.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        gameList = games,
                        showGameSelection = false
                    )
                    loadGame(games.first(), server, username)
                } else {
                    val serverName = if (server == ChessServer.LICHESS) "Lichess" else "Chess.com"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No games found for $username on $serverName"
                    )
                }
            }
            is Result.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }
        }
    }

    fun setLichessMaxGames(max: Int) {
        val validMax = max.coerceIn(1, 25)
        settingsPrefs.saveLichessMaxGames(validMax)
        _uiState.value = _uiState.value.copy(lichessMaxGames = validMax)
    }

    fun setChessComMaxGames(max: Int) {
        val validMax = max.coerceIn(1, 25)
        settingsPrefs.saveChessComMaxGames(validMax)
        _uiState.value = _uiState.value.copy(chessComMaxGames = validMax)
    }

    fun fetchGames(server: ChessServer, username: String, maxGames: Int) {
        // Save the username for next time
        when (server) {
            ChessServer.LICHESS -> settingsPrefs.saveLichessUsername(username)
            ChessServer.CHESS_COM -> settingsPrefs.saveChessComUsername(username)
        }

        // Mark first run complete - user has made their game retrieval choice
        markFirstRunComplete()

        // Cancel any ongoing auto-analysis
        autoAnalysisJob?.cancel()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                game = null,
                gameList = emptyList(),
                showGameSelection = false
            )

            val result = when (server) {
                ChessServer.LICHESS -> repository.getLichessGames(username, maxGames)
                ChessServer.CHESS_COM -> repository.getChessComGames(username, maxGames)
            }

            when (result) {
                is Result.Success -> {
                    val games = result.data
                    // Store the retrieved games for later use
                    if (games.isNotEmpty()) {
                        storeRetrievedGames(games, username, server)
                    }
                    if (games.size == 1) {
                        // Auto-select if only 1 game
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            gameList = games,
                            showGameSelection = false,
                            gameSelectionUsername = username,
                            gameSelectionServer = server
                        )
                        loadGame(games.first(), server, username)
                    } else {
                        // Show the SelectedRetrieveGamesScreen directly
                        val entry = RetrievedGamesEntry(accountName = username, server = server)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showRetrieveScreen = false,
                            showSelectedRetrieveGames = true,
                            selectedRetrieveEntry = entry,
                            selectedRetrieveGames = games
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    // ===== RETRIEVED GAMES LIST STORAGE =====

    /**
     * Store the Active player/server for the reload button.
     * Called whenever activePlayer or activeServer changes.
     */
    private fun storeActive(player: String, server: ChessServer) {
        settingsPrefs.saveActivePlayerAndServer(player, server)
        _uiState.value = _uiState.value.copy(hasActive = true)
    }

    /**
     * Store a retrieved games list for a specific account/server.
     * Updates the retrieves list and maintains max 25 entries.
     */
    private fun storeRetrievedGames(games: List<LichessGame>, username: String, server: ChessServer) {
        gameStorage.storeRetrievedGames(games, username, server)
        val retrievesList = loadRetrievesList()
        _uiState.value = _uiState.value.copy(
            hasPreviousRetrieves = retrievesList.isNotEmpty(),
            previousRetrievesList = retrievesList
        )
    }

    /**
     * Show the list of previous retrieves.
     */
    fun showPreviousRetrieves() {
        val retrievesList = loadRetrievesList()
        if (retrievesList.isEmpty()) return

        // If only one retrieve, skip selection and go directly to games
        if (retrievesList.size == 1) {
            val entry = retrievesList.first()
            val games = loadGamesForRetrieve(entry)
            if (games.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    showRetrieveScreen = false,
                    showSelectedRetrieveGames = true,
                    selectedRetrieveEntry = entry,
                    selectedRetrieveGames = games
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                showRetrieveScreen = false,
                previousRetrievesList = retrievesList,
                showPreviousRetrievesSelection = true
            )
        }
    }

    /**
     * Dismiss the previous retrieves selection.
     */
    fun dismissPreviousRetrievesSelection() {
        _uiState.value = _uiState.value.copy(showPreviousRetrievesSelection = false)
    }

    /**
     * Select a previous retrieve to show its games.
     */
    fun selectPreviousRetrieve(entry: RetrievedGamesEntry) {
        val games = loadGamesForRetrieve(entry)
        if (games.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                showPreviousRetrievesSelection = false,
                showSelectedRetrieveGames = true,
                selectedRetrieveEntry = entry,
                selectedRetrieveGames = games
            )
        }
    }

    /**
     * Dismiss the selected retrieve games view.
     */
    fun dismissSelectedRetrieveGames() {
        _uiState.value = _uiState.value.copy(
            showSelectedRetrieveGames = false,
            selectedRetrieveEntry = null,
            selectedRetrieveGames = emptyList()
        )
    }

    /**
     * Select a game from a previous retrieve and start analysis.
     * Sets ActivePlayer from the retrieve entry's account name.
     */
    fun selectGameFromRetrieve(game: LichessGame) {
        val entry = _uiState.value.selectedRetrieveEntry ?: return
        _uiState.value = _uiState.value.copy(
            showSelectedRetrieveGames = false,
            showRetrieveScreen = false,
            selectedRetrieveEntry = null,
            selectedRetrieveGames = emptyList()
        )
        // Load the game with the account name as the active player
        loadGame(game, entry.server, entry.accountName)
    }

    // ===== ANALYSED GAMES STORAGE =====

    private fun storeAnalysedGame() {
        val game = _uiState.value.game ?: return
        val moves = _uiState.value.moves
        if (moves.isEmpty()) return

        gameStorage.storeAnalysedGame(
            game = game,
            moves = moves,
            moveDetails = _uiState.value.moveDetails,
            previewScores = _uiState.value.previewScores,
            analyseScores = _uiState.value.analyseScores,
            openingName = _uiState.value.openingName,
            activePlayer = _uiState.value.activePlayer,
            activeServer = _uiState.value.activeServer
        )

        _uiState.value = _uiState.value.copy(hasAnalysedGames = true)
    }

    fun showAnalysedGames() {
        val games = loadAnalysedGames()
        if (games.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                analysedGamesList = games,
                showAnalysedGamesSelection = true,
                showRetrieveScreen = false
            )
        }
    }

    fun dismissAnalysedGamesSelection() {
        _uiState.value = _uiState.value.copy(showAnalysedGamesSelection = false)
    }

    fun selectAnalysedGame(game: AnalysedGame) {
        _uiState.value = _uiState.value.copy(showAnalysedGamesSelection = false, showRetrieveScreen = false)
        loadAnalysedGameDirectly(game)
    }

    private fun loadAnalysedGameDirectly(analysedGame: AnalysedGame) {
        // Cancel any ongoing analysis
        autoAnalysisJob?.cancel()

        // Parse the PGN to get board states
        val parsedMoves = PgnParser.parseMoves(analysedGame.pgn)

        // Build board history
        boardHistory.clear()
        val tempBoard = ChessBoard()
        boardHistory.add(tempBoard.copy())

        for (move in parsedMoves) {
            val moveSuccess = tempBoard.makeMove(move)
            if (moveSuccess) {
                boardHistory.add(tempBoard.copy())
            }
        }

        // Create a minimal LichessGame object for display purposes
        val lichessGame = LichessGame(
            id = "analysed_${analysedGame.timestamp}",
            rated = false,
            variant = "standard",
            speed = analysedGame.speed ?: "unknown",
            perf = null,
            status = if (analysedGame.result == "1/2-1/2") "draw" else "mate",
            winner = when (analysedGame.result) {
                "1-0" -> "white"
                "0-1" -> "black"
                else -> null
            },
            players = com.eval.data.Players(
                white = com.eval.data.Player(
                    user = com.eval.data.User(name = analysedGame.whiteName, id = analysedGame.whiteName.lowercase()),
                    rating = null,
                    aiLevel = null
                ),
                black = com.eval.data.Player(
                    user = com.eval.data.User(name = analysedGame.blackName, id = analysedGame.blackName.lowercase()),
                    rating = null,
                    aiLevel = null
                )
            ),
            pgn = analysedGame.pgn,
            moves = null,
            clock = null,
            createdAt = analysedGame.timestamp,
            lastMoveAt = analysedGame.timestamp
        )

        // Start directly in Manual stage at the biggest score change
        val biggestChangeMoveIndex = findBiggestScoreChangeInScores(analysedGame.analyseScores, analysedGame.previewScores, analysedGame.moves.size)
        val validIndex = biggestChangeMoveIndex.coerceIn(-1, boardHistory.size - 2)
        val board = if (validIndex >= 0 && validIndex < boardHistory.size - 1) {
            boardHistory[validIndex + 1]
        } else {
            boardHistory.firstOrNull() ?: ChessBoard()
        }

        // Determine if active player played black (for score perspective)
        val activePlayerName = analysedGame.activePlayer ?: ""
        val activePlayerLower = activePlayerName.lowercase()
        val userPlayedBlack = activePlayerLower.isNotEmpty() && activePlayerLower == analysedGame.blackName.lowercase()

        _uiState.value = _uiState.value.copy(
            game = lichessGame,
            moves = analysedGame.moves,
            moveDetails = analysedGame.moveDetails,
            currentMoveIndex = validIndex,
            currentBoard = board.copy(),
            flippedBoard = userPlayedBlack,
            userPlayedBlack = userPlayedBlack,
            activePlayer = activePlayerName,
            activeServer = analysedGame.activeServer,
            activePlayerError = null,
            openingName = analysedGame.openingName,
            previewScores = analysedGame.previewScores,
            analyseScores = analysedGame.analyseScores,
            currentStage = AnalysisStage.MANUAL,
            autoAnalysisIndex = -1,
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1
        )

        // Store Active for reload button
        val activeServer = analysedGame.activeServer
        if (activePlayerName.isNotEmpty() && activeServer != null) {
            storeActive(activePlayerName, activeServer)
        }

        // Save as current game
        saveCurrentAnalysedGame(analysedGame)

        // Validate ActivePlayer at start of Manual stage (loaded from analysed game)
        if (!checkActivePlayer(AnalysisStage.MANUAL)) {
            android.util.Log.e("Analysis", "ActivePlayer validation failed at MANUAL stage (from analysed game)")
            // Continue anyway but error will be shown to user
        }

        // Start Stockfish analysis for current position
        val fenToAnalyze = board.getFen()
        currentAnalysisFen = fenToAnalyze
        analysisRequestId++
        val thisRequestId = analysisRequestId

        viewModelScope.launch {
            stockfish.stop()
            val ready = stockfish.restart()
            _uiState.value = _uiState.value.copy(stockfishReady = ready)
            if (ready) {
                stockfish.newGame()
                configureForManualStage()
                delay(100)
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    private fun findBiggestScoreChangeInScores(
        analyseScores: Map<Int, MoveScore>,
        previewScores: Map<Int, MoveScore>,
        totalMoves: Int
    ): Int {
        var biggestChangeIndex = 0
        var biggestChange = 0f

        for (i in 1 until totalMoves) {
            val prevScore = analyseScores[i - 1] ?: previewScores[i - 1]
            val currScore = analyseScores[i] ?: previewScores[i]

            if (prevScore != null && currScore != null) {
                val change = kotlin.math.abs(currScore.score - prevScore.score)
                if (change > biggestChange) {
                    biggestChange = change
                    biggestChangeIndex = i
                }
            }
        }

        return biggestChangeIndex
    }

    // Temporary storage for server/username when showing game selection dialog
    private var pendingGameSelectionServer: ChessServer? = null
    private var pendingGameSelectionUsername: String? = null

    fun selectGame(game: LichessGame) {
        _uiState.value = _uiState.value.copy(showGameSelection = false, showRetrieveScreen = false)
        val server = pendingGameSelectionServer
        val username = pendingGameSelectionUsername
        pendingGameSelectionServer = null
        pendingGameSelectionUsername = null
        loadGame(game, server, username)
    }

    fun dismissGameSelection() {
        _uiState.value = _uiState.value.copy(showGameSelection = false)
    }

    fun clearGame() {
        // Stop any ongoing auto-analysis
        autoAnalysisJob?.cancel()
        stockfish.stop()

        // Clear game state and show retrieve screen
        boardHistory.clear()
        exploringLineHistory.clear()
        _uiState.value = _uiState.value.copy(
            game = null,
            gameList = emptyList(),
            showGameSelection = false,
            showRetrieveScreen = true,
            currentBoard = ChessBoard(),
            moves = emptyList(),
            moveDetails = emptyList(),
            currentMoveIndex = -1,
            analysisResult = null,
            flippedBoard = false,
            userPlayedBlack = false,
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            currentStage = AnalysisStage.PREVIEW,
            previewScores = emptyMap(),
            analyseScores = emptyMap(),
            autoAnalysisIndex = -1
        )
    }

    private fun loadGame(game: LichessGame, server: ChessServer?, username: String?) {
        // Cancel any ongoing analysis before loading new game
        autoAnalysisJob?.cancel()
        manualAnalysisJob?.cancel()
        stockfish.stop()

        val pgn = game.pgn
        if (pgn == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "No PGN data available"
            )
            return
        }

        // Save as Active for reload button
        if (server != null && username != null) {
            storeActive(username, server)
        }

        // Extract opening name from PGN headers
        val pgnHeaders = PgnParser.parseHeaders(pgn)
        val openingName = pgnHeaders["Opening"] ?: pgnHeaders["ECO"]

        val parsedMoves = PgnParser.parseMovesWithClock(pgn)
        val initialBoard = ChessBoard()
        boardHistory.clear()
        exploringLineHistory.clear()
        boardHistory.add(initialBoard.copy())

        // Pre-compute all board positions and move details for efficient navigation
        val tempBoard = ChessBoard()
        val moveDetailsList = mutableListOf<MoveDetails>()
        val validMoves = mutableListOf<String>()

        for ((index, parsedMove) in parsedMoves.withIndex()) {
            val move = parsedMove.san
            val moveNum = (index / 2) + 1
            val isWhite = index % 2 == 0
            // Check if this move is a capture (target square has a piece before the move)
            val boardBeforeMove = tempBoard.copy()
            val moveSuccess = tempBoard.makeMove(move)
            if (!moveSuccess) {
                // Skip invalid moves (e.g., malformed PGN artifacts)
                val prefix = if (isWhite) "$moveNum." else "$moveNum..."
                android.util.Log.e("GameViewModel", "FAILED to apply move $prefix $move - FEN: ${boardBeforeMove.getFen()}")
                continue
            }
            validMoves.add(move)
            boardHistory.add(tempBoard.copy())

            // Get move details from the board's last move
            val lastMove = tempBoard.getLastMove()
            if (lastMove != null) {
                val fromSquare = lastMove.from.toAlgebraic()
                val toSquare = lastMove.to.toAlgebraic()
                val capturedPiece = boardBeforeMove.getPiece(lastMove.to)
                val movedPiece = tempBoard.getPiece(lastMove.to)
                val pieceType = when (movedPiece?.type) {
                    com.eval.chess.PieceType.KING -> "K"
                    com.eval.chess.PieceType.QUEEN -> "Q"
                    com.eval.chess.PieceType.ROOK -> "R"
                    com.eval.chess.PieceType.BISHOP -> "B"
                    com.eval.chess.PieceType.KNIGHT -> "N"
                    com.eval.chess.PieceType.PAWN -> "P"
                    else -> "P"
                }
                // Check for en passant capture (pawn capture but no piece on target square)
                val isEnPassant = pieceType == "P" &&
                    lastMove.from.file != lastMove.to.file &&
                    capturedPiece == null
                val isCapture = capturedPiece != null || isEnPassant

                moveDetailsList.add(MoveDetails(
                    san = move,
                    from = fromSquare,
                    to = toSquare,
                    isCapture = isCapture,
                    pieceType = pieceType,
                    clockTime = parsedMove.clockTime
                ))
            }
        }

        // Determine the active player - the user whose perspective we're viewing from
        // Use the username parameter if provided, otherwise fall back to saved Lichess username
        val activePlayerName = username ?: savedLichessUsername ?: ""
        val blackPlayerName = game.players.black.user?.name?.lowercase() ?: ""
        val userPlayedBlack = activePlayerName.isNotEmpty() && activePlayerName.lowercase() == blackPlayerName

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            game = game,
            openingName = openingName,
            moves = validMoves,
            moveDetails = moveDetailsList,
            currentBoard = initialBoard,
            currentMoveIndex = -1,
            flippedBoard = userPlayedBlack,
            userPlayedBlack = userPlayedBlack,
            activePlayer = activePlayerName,
            activeServer = server,
            activePlayerError = null,
            showRetrieveScreen = false,
            // Reset exploring state
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            // Reset analysis state - start at Preview stage
            currentStage = AnalysisStage.PREVIEW,
            previewScores = emptyMap(),
            analyseScores = emptyMap(),
            autoAnalysisIndex = -1
        )

        // Start analysis - runs Preview stage, then Analyse stage, then enters Manual stage
        // The current game is saved when entering Manual stage (in storeAnalysedGame)
        startAnalysis()
    }

    /**
     * Check if navigation is allowed in the current stage.
     * Preview stage: not interruptible, navigation not allowed
     * Analyse stage: interruptible, will switch to Manual stage
     * Manual stage: navigation always allowed
     */
    private fun canNavigate(): Boolean {
        return _uiState.value.currentStage != AnalysisStage.PREVIEW
    }

    /**
     * Handle navigation during Analyse stage - interrupts analysis and switches to Manual stage.
     * Returns true if we should proceed with navigation, false if blocked.
     */
    private fun handleNavigationInterrupt(): Boolean {
        when (_uiState.value.currentStage) {
            AnalysisStage.PREVIEW -> return false  // Preview stage is not interruptible
            AnalysisStage.ANALYSE -> {
                // Interrupt analyse stage and switch to manual
                enterManualStageAtCurrentPosition()
                return false  // Don't proceed - enterManualStageAtCurrentPosition handles navigation
            }
            AnalysisStage.MANUAL -> return true  // Allow navigation in manual stage
        }
    }

    fun goToStart() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val newBoard = exploringLineHistory.firstOrNull()?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = -1
            )
            analyzePosition(newBoard)
        } else {
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(-1)
            } else {
                val newBoard = boardHistory.firstOrNull()?.copy() ?: ChessBoard()
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = -1
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun goToEnd() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val moves = _uiState.value.exploringLineMoves
            if (moves.isEmpty()) {
                analyzePosition(_uiState.value.currentBoard)
                return
            }
            val newBoard = exploringLineHistory.lastOrNull()?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = moves.size - 1
            )
            analyzePosition(newBoard)
        } else {
            val moves = _uiState.value.moves
            if (moves.isEmpty()) return
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(moves.size - 1)
            } else {
                val newBoard = boardHistory.lastOrNull()?.copy() ?: ChessBoard()
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = moves.size - 1
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun goToMove(index: Int) {
        if (!handleNavigationInterrupt()) return

        val newBoard: ChessBoard
        if (_uiState.value.isExploringLine) {
            val moves = _uiState.value.exploringLineMoves
            if (index < -1 || index >= moves.size) return
            newBoard = exploringLineHistory.getOrNull(index + 1)?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = index
            )
        } else {
            val moves = _uiState.value.moves
            if (index < -1 || index >= moves.size) return
            newBoard = boardHistory.getOrNull(index + 1)?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                currentMoveIndex = index
            )
        }
        // Pass the exact board we just set to avoid any race conditions
        analyzePosition(newBoard)
    }

    /**
     * Restart analysis at a specific move - stops Stockfish and starts fresh.
     * Used for graph clicks in manual stage to ensure clean state.
     */
    fun restartAnalysisAtMove(moveIndex: Int) {
        // Cancel any running analysis
        manualAnalysisJob?.cancel()

        // Get the board for the clicked position
        val validIndex = moveIndex.coerceIn(-1, boardHistory.size - 2)
        val board = boardHistory.getOrNull(validIndex + 1) ?: ChessBoard()

        viewModelScope.launch {
            // Stop Stockfish completely
            stockfish.stop()

            // Increment request ID to invalidate any pending results
            analysisRequestId++
            val thisRequestId = analysisRequestId

            // Set up the new position
            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze

            // Update UI state - keep analysisResult to avoid UI jumping, just clear the FEN
            // The card will stay visible with old content until new results arrive
            _uiState.value = _uiState.value.copy(
                currentMoveIndex = validIndex,
                currentBoard = board.copy(),
                analysisResultFen = null  // Mark as stale, but keep result for UI stability
            )

            // Small delay to ensure Stockfish has stopped
            delay(50)

            // Send new game command to clear Stockfish's internal state
            stockfish.newGame()
            delay(50)

            // Start fresh analysis
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    fun nextMove() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val currentIndex = _uiState.value.exploringLineMoveIndex
            val moves = _uiState.value.exploringLineMoves
            if (currentIndex >= moves.size - 1) return
            val newIndex = currentIndex + 1
            val newBoard = exploringLineHistory.getOrNull(newIndex + 1)?.copy() ?: _uiState.value.currentBoard
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = newIndex
            )
            // Use full restart for proper Stockfish analysis
            restartAnalysisForExploringLine()
        } else {
            val currentIndex = _uiState.value.currentMoveIndex
            val moves = _uiState.value.moves
            if (currentIndex >= moves.size - 1) return
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(currentIndex + 1)
            } else {
                val newIndex = currentIndex + 1
                val newBoard = boardHistory.getOrNull(newIndex + 1)?.copy() ?: _uiState.value.currentBoard
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = newIndex
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun prevMove() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val currentIndex = _uiState.value.exploringLineMoveIndex
            if (currentIndex < 0) return
            val newIndex = currentIndex - 1
            val newBoard = exploringLineHistory.getOrNull(newIndex + 1)?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = newIndex
            )
            // Use full restart for proper Stockfish analysis
            restartAnalysisForExploringLine()
        } else {
            val currentIndex = _uiState.value.currentMoveIndex
            if (currentIndex < 0) return
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(currentIndex - 1)
            } else {
                val newIndex = currentIndex - 1
                val newBoard = boardHistory.getOrNull(newIndex + 1)?.copy() ?: ChessBoard()
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = newIndex
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun exploreLine(pv: String, moveIndex: Int = 0) {
        if (pv.isBlank()) return

        // Save current game position
        val savedMoveIndex = _uiState.value.currentMoveIndex

        // Get the starting board (current position before exploring)
        val startBoard = _uiState.value.currentBoard.copy()

        // Parse UCI moves and build board history for the line
        val uciMoves = pv.split(" ").filter { it.isNotBlank() }
        exploringLineHistory.clear()
        exploringLineHistory.add(startBoard)

        val tempBoard = startBoard.copy()
        for (uciMove in uciMoves) {
            if (tempBoard.makeUciMove(uciMove)) {
                exploringLineHistory.add(tempBoard.copy())
            } else {
                break // Invalid move, stop here
            }
        }

        // Go to the specified move index
        val targetIndex = moveIndex.coerceIn(-1, exploringLineHistory.size - 2)

        _uiState.value = _uiState.value.copy(
            isExploringLine = true,
            exploringLineMoves = uciMoves.take(exploringLineHistory.size - 1),
            exploringLineMoveIndex = targetIndex,
            savedGameMoveIndex = savedMoveIndex,
            currentBoard = exploringLineHistory.getOrNull(targetIndex + 1)?.copy() ?: startBoard
        )

        // Use full restart for proper Stockfish analysis
        restartAnalysisForExploringLine()
    }

    fun backToOriginalGame() {
        val savedIndex = _uiState.value.savedGameMoveIndex
        exploringLineHistory.clear()

        _uiState.value = _uiState.value.copy(
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            currentBoard = boardHistory.getOrNull(savedIndex + 1)?.copy() ?: ChessBoard(),
            currentMoveIndex = savedIndex
        )

        // Use full restart for proper Stockfish analysis
        restartAnalysisForExploringLine()
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(analysisEnabled = enabled)
        if (enabled) {
            // Use full restart for proper Stockfish analysis
            restartAnalysisForExploringLine()
        } else {
            stockfish.stop()
        }
    }

    fun flipBoard() {
        _uiState.value = _uiState.value.copy(flippedBoard = !_uiState.value.flippedBoard)
    }

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

    fun showSettingsDialog() {
        // Store current settings to detect changes when dialog closes
        settingsOnDialogOpen = SettingsSnapshot(
            previewStageSettings = _uiState.value.stockfishSettings.previewStage,
            analyseStageSettings = _uiState.value.stockfishSettings.analyseStage,
            manualStageSettings = _uiState.value.stockfishSettings.manualStage
        )
        _uiState.value = _uiState.value.copy(showSettingsDialog = true)
    }

    fun hideSettingsDialog() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = false)

        // Check what settings changed
        val originalSettings = settingsOnDialogOpen
        val currentPreviewStageSettings = _uiState.value.stockfishSettings.previewStage
        val currentAnalyseStageSettings = _uiState.value.stockfishSettings.analyseStage
        val currentManualStageSettings = _uiState.value.stockfishSettings.manualStage

        val previewStageSettingsChanged = originalSettings?.previewStageSettings != currentPreviewStageSettings
        val analyseStageSettingsChanged = originalSettings?.analyseStageSettings != currentAnalyseStageSettings
        val manualStageSettingsChanged = originalSettings?.manualStageSettings != currentManualStageSettings

        // Clear the snapshot
        settingsOnDialogOpen = null

        // If no game loaded or no settings changed, nothing to do
        if (_uiState.value.game == null) return
        if (!previewStageSettingsChanged && !analyseStageSettingsChanged && !manualStageSettingsChanged) return

        viewModelScope.launch {
            // Stop any ongoing analysis
            autoAnalysisJob?.cancel()
            stockfish.stop()

            // Set stockfishReady to false while restarting
            _uiState.value = _uiState.value.copy(stockfishReady = false)

            // Kill and restart Stockfish engine
            val ready = stockfish.restart()

            // Verify Stockfish is truly ready by checking isReady flow
            if (ready) {
                // Wait a moment for the engine to stabilize
                kotlinx.coroutines.delay(200)
                val confirmedReady = stockfish.isReady.value
                _uiState.value = _uiState.value.copy(stockfishReady = confirmedReady)

                if (!confirmedReady) {
                    return@launch
                }
            } else {
                _uiState.value = _uiState.value.copy(stockfishReady = false)
                return@launch
            }

            // Decide which mode to activate based on what changed
            if (previewStageSettingsChanged || analyseStageSettingsChanged) {
                // Stockfish stage settings changed
                // -> Restart analysis from Preview stage
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap()
                )
                startAnalysis()
            } else if (manualStageSettingsChanged) {
                // Only Manual stage settings changed
                // -> If in Manual stage, just reconfigure; otherwise enter Manual stage
                if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                    configureForManualStage()
                    // Use full restart for proper Stockfish analysis
                    restartAnalysisForExploringLine()
                } else {
                    enterManualStageAtCurrentPosition()
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

    fun updateStockfishSettings(settings: StockfishSettings) {
        saveStockfishSettings(settings)
        _uiState.value = _uiState.value.copy(
            stockfishSettings = settings
        )
        // Apply new settings to Stockfish based on current stage
        if (_uiState.value.stockfishReady) {
            when (_uiState.value.currentStage) {
                AnalysisStage.PREVIEW -> configureForPreviewStage()
                AnalysisStage.ANALYSE -> configureForAnalyseStage()
                AnalysisStage.MANUAL -> configureForManualStage()
            }
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                // Use full restart for proper Stockfish analysis
                restartAnalysisForExploringLine()
            }
        }
    }

    fun updateBoardLayoutSettings(settings: BoardLayoutSettings) {
        saveBoardLayoutSettings(settings)
        _uiState.value = _uiState.value.copy(
            boardLayoutSettings = settings
        )
    }

    fun updateGraphSettings(settings: GraphSettings) {
        saveGraphSettings(settings)
        _uiState.value = _uiState.value.copy(
            graphSettings = settings
        )
    }

    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        val currentSettings = _uiState.value.interfaceVisibility

        // Check if Preview or Analyse stage visibility changed
        val previewChanged = currentSettings.previewStage != settings.previewStage
        val analyseChanged = currentSettings.analyseStage != settings.analyseStage

        saveInterfaceVisibilitySettings(settings)
        _uiState.value = _uiState.value.copy(
            interfaceVisibility = settings
        )

        // If Preview or Analyse stage visibility changed, restart from Preview stage
        if ((previewChanged || analyseChanged) && _uiState.value.game != null) {
            // Cancel any ongoing analysis
            autoAnalysisJob?.cancel()
            manualAnalysisJob?.cancel()
            stockfish.stop()

            // Reset to Preview stage and restart analysis
            _uiState.value = _uiState.value.copy(
                currentStage = AnalysisStage.PREVIEW,
                previewScores = emptyMap(),
                analyseScores = emptyMap(),
                autoAnalysisIndex = -1
            )

            // Restart analysis from Preview stage
            viewModelScope.launch {
                val ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (ready) {
                    stockfish.newGame()
                    startAnalysis()
                }
            }
        }
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        saveGeneralSettings(settings)
        _uiState.value = _uiState.value.copy(
            generalSettings = settings
        )
    }

    fun updateAiSettings(settings: AiSettings) {
        saveAiSettings(settings)
        _uiState.value = _uiState.value.copy(
            aiSettings = settings
        )
    }

    /**
     * Request AI analysis for the current position using the specified AI service.
     */
    fun requestAiAnalysis(service: AiService) {
        val apiKey = _uiState.value.aiSettings.getApiKey(service)
        if (apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                showAiAnalysisDialog = true,
                aiAnalysisLoading = false,
                aiAnalysisServiceName = service.displayName,
                aiAnalysisResult = AiAnalysisResponse(
                    service = service,
                    analysis = null,
                    error = "API key not configured for ${service.displayName}. Please configure it in Settings > AI Analysis."
                )
            )
            return
        }

        val fen = _uiState.value.currentBoard.getFen()

        _uiState.value = _uiState.value.copy(
            showAiAnalysisDialog = true,
            aiAnalysisLoading = true,
            aiAnalysisServiceName = service.displayName,
            aiAnalysisResult = null
        )

        viewModelScope.launch {
            val aiSettings = _uiState.value.aiSettings
            val prompt = when (service) {
                AiService.CHATGPT -> aiSettings.chatGptPrompt
                AiService.CLAUDE -> aiSettings.claudePrompt
                AiService.GEMINI -> aiSettings.geminiPrompt
                AiService.GROK -> aiSettings.grokPrompt
                AiService.DEEPSEEK -> aiSettings.deepSeekPrompt
            }
            val result = aiAnalysisRepository.analyzePosition(
                service = service,
                fen = fen,
                apiKey = apiKey,
                prompt = prompt,
                chatGptModel = aiSettings.chatGptModel,
                claudeModel = aiSettings.claudeModel,
                geminiModel = aiSettings.geminiModel,
                grokModel = aiSettings.grokModel,
                deepSeekModel = aiSettings.deepSeekModel
            )
            _uiState.value = _uiState.value.copy(
                aiAnalysisLoading = false,
                aiAnalysisResult = result
            )
        }
    }

    /**
     * Dismiss the AI analysis dialog.
     */
    fun dismissAiAnalysisDialog() {
        _uiState.value = _uiState.value.copy(
            showAiAnalysisDialog = false,
            aiAnalysisResult = null,
            aiAnalysisLoading = false
        )
    }

    /**
     * Fetch available Gemini models using the provided API key.
     */
    fun fetchChatGptModels(apiKey: String) {
        if (apiKey.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoadingChatGptModels = true)

        viewModelScope.launch {
            val models = aiAnalysisRepository.fetchChatGptModels(apiKey)
            _uiState.value = _uiState.value.copy(
                availableChatGptModels = models,
                isLoadingChatGptModels = false
            )
        }
    }

    fun fetchGeminiModels(apiKey: String) {
        if (apiKey.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoadingGeminiModels = true)

        viewModelScope.launch {
            val models = aiAnalysisRepository.fetchGeminiModels(apiKey)
            _uiState.value = _uiState.value.copy(
                availableGeminiModels = models,
                isLoadingGeminiModels = false
            )
        }
    }

    /**
     * Fetch available Grok models using the provided API key.
     */
    fun fetchGrokModels(apiKey: String) {
        if (apiKey.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoadingGrokModels = true)

        viewModelScope.launch {
            val models = aiAnalysisRepository.fetchGrokModels(apiKey)
            _uiState.value = _uiState.value.copy(
                availableGrokModels = models,
                isLoadingGrokModels = false
            )
        }
    }

    fun fetchDeepSeekModels(apiKey: String) {
        if (apiKey.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoadingDeepSeekModels = true)

        viewModelScope.launch {
            val models = aiAnalysisRepository.fetchDeepSeekModels(apiKey)
            _uiState.value = _uiState.value.copy(
                availableDeepSeekModels = models,
                isLoadingDeepSeekModels = false
            )
        }
    }

    /**
     * Toggle full screen mode via long tap.
     * Directly toggles the fullScreenMode setting.
     */
    fun toggleFullScreen() {
        val currentSettings = _uiState.value.generalSettings
        val newSettings = currentSettings.copy(
            longTapForFullScreen = !currentSettings.longTapForFullScreen
        )
        _uiState.value = _uiState.value.copy(
            generalSettings = newSettings
        )
    }

    private var manualAnalysisJob: Job? = null
    private var currentAnalysisFen: String? = null  // Track which FEN is being analyzed
    private var analysisRequestId: Long = 0  // Incremented for each new analysis request

    /**
     * Analyze the current position from UI state.
     * Use analyzePosition(board) when you have the board directly to avoid race conditions.
     */
    private fun analyzeCurrentPosition() {
        analyzePosition(_uiState.value.currentBoard)
    }

    /**
     * Analyze a specific board position.
     * This is the preferred method when you have the board directly (e.g., after navigation).
     */
    private fun analyzePosition(board: ChessBoard) {
        if (!_uiState.value.analysisEnabled) return

        // Cancel any previous manual analysis job
        manualAnalysisJob?.cancel()

        // Increment request ID to invalidate any pending results from old analyses
        analysisRequestId++
        val thisRequestId = analysisRequestId

        // Track which position we're analyzing and clear old result
        val fenToAnalyze = board.getFen()
        currentAnalysisFen = fenToAnalyze
        _uiState.value = _uiState.value.copy(analysisResult = null, analysisResultFen = null)

        // Only run manual analysis in Manual stage
        if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
            return
        }

        // In manual stage: ensure Stockfish card is shown - pass the FEN and request ID
        manualAnalysisJob = viewModelScope.launch {
            ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
        }
    }

    /**
     * Ensure Stockfish analysis is running and producing results in manual stage.
     * If no results come back, restart Stockfish and try again.
     * @param fen The FEN position to analyze (captured at call time to avoid race conditions)
     * @param requestId The request ID to validate results against
     */
    private suspend fun ensureStockfishAnalysis(fen: String, requestId: Long) {
        val maxRetries = 2
        var attempt = 0

        while (attempt < maxRetries) {
            // Check if Stockfish is ready, restart if not
            if (!_uiState.value.stockfishReady) {
                val ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (!ready) {
                    attempt++
                    continue
                }
                configureForManualStage()
            }

            // Start analysis with the FEN that was captured when this analysis was requested
            val depth = _uiState.value.stockfishSettings.manualStage.depth
            stockfish.analyze(fen, depth)

            // Wait for results (up to 2 seconds)
            var waitTime = 0
            val maxWaitTime = 2000
            val checkInterval = 50L

            var gotFirstResult = false
            while (true) {
                delay(checkInterval)

                // Check if a new analysis request was started - abort this one
                if (analysisRequestId != requestId) {
                    return // User navigated away, a new analysis will be started
                }

                // If we're no longer in manual stage, abort
                if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                    return
                }

                // Check if we got results directly from Stockfish
                val result = stockfish.analysisResult.value
                if (result != null) {
                    // Double-check request ID before updating UI
                    if (analysisRequestId == requestId) {
                        _uiState.value = _uiState.value.copy(
                            analysisResult = result,
                            analysisResultFen = fen
                        )
                        gotFirstResult = true
                    } else {
                        return // Request changed while checking
                    }
                }

                // If we haven't got any result after timeout, break to retry
                if (!gotFirstResult) {
                    waitTime += checkInterval.toInt()
                    if (waitTime >= maxWaitTime) {
                        break
                    }
                }
            }

            // No results after waiting - restart Stockfish and try again
            android.util.Log.w("GameViewModel", "No Stockfish results after ${maxWaitTime}ms, restarting (attempt ${attempt + 1})")

            stockfish.stop()
            _uiState.value = _uiState.value.copy(stockfishReady = false)

            val ready = stockfish.restart()
            _uiState.value = _uiState.value.copy(stockfishReady = ready)

            if (ready) {
                configureForManualStage()
            }

            attempt++
        }

        // Failed to get Stockfish analysis after max retries
    }

    /**
     * Build the list of move indices for analysis based on the current stage.
     * Preview stage: Forward sequence (move 1 to end)
     * Analyse stage: Backwards sequence (end to move 1), unless board is visible then forward
     */
    private fun buildMoveIndices(): List<Int> {
        val moves = _uiState.value.moves
        val showBoardInAnalyse = _uiState.value.interfaceVisibility.analyseStage.showBoard
        return when (_uiState.value.currentStage) {
            AnalysisStage.PREVIEW -> (0 until moves.size).toList()  // Forward
            AnalysisStage.ANALYSE -> if (showBoardInAnalyse) {
                (0 until moves.size).toList()  // Forward when board is visible
            } else {
                (moves.size - 1 downTo 0).toList()  // Backwards when board is hidden
            }
            AnalysisStage.MANUAL -> emptyList()  // No auto-analysis in manual stage
        }
    }

    /**
     * Start the three-stage analysis flow: Preview → Analyse → Manual.
     * Each stage kills the current Stockfish process and starts a new one with appropriate settings.
     */
    private fun startAnalysis() {
        if (!_uiState.value.stockfishReady) return

        // Cancel any previous analysis
        autoAnalysisJob?.cancel()

        autoAnalysisJob = viewModelScope.launch {
            try {
                val moves = _uiState.value.moves
                if (moves.isEmpty()) {
                    android.util.Log.e("Analysis", "EXIT: moves list is empty")
                    enterManualStageInternal(-1)
                    return@launch
                }

                // Store expected board history size to detect if game was reloaded
                val expectedBoardHistorySize = boardHistory.size
                android.util.Log.d("Analysis", "START: moves=${moves.size}, boardHistory=$expectedBoardHistorySize")

                // ===== PREVIEW STAGE =====
                android.util.Log.d("Analysis", "Starting PREVIEW stage")

                // Validate ActivePlayer at start of Preview stage
                if (!checkActivePlayer(AnalysisStage.PREVIEW)) {
                    android.util.Log.e("Analysis", "ActivePlayer validation failed at PREVIEW stage")
                    // Continue anyway but error will be shown to user
                }
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap(),
                    autoAnalysisCurrentScore = null,
                    remainingAnalysisMoves = buildMoveIndices()
                )

                // Kill current Stockfish and start new one for Preview stage
                stockfish.stop()
                var ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (!ready) {
                    android.util.Log.e("Analysis", "Failed to start Stockfish for Preview stage")
                    enterManualStageInternal(-1)
                    return@launch
                }

                stockfish.newGame()
                configureForPreviewStage()
                delay(50)

                val previewTimeMs = (_uiState.value.stockfishSettings.previewStage.secondsForMove * 1000).toInt()
                val previewComplete = runStageAnalysis(
                    stageName = "PREVIEW",
                    timePerMoveMs = previewTimeMs,
                    expectedBoardHistorySize = expectedBoardHistorySize,
                    storeScore = { moveIndex, score ->
                        _uiState.value = _uiState.value.copy(
                            previewScores = _uiState.value.previewScores + (moveIndex to score),
                            autoAnalysisCurrentScore = score
                        )
                    },
                    configureEngine = { configureForPreviewStage() }
                )

                if (!previewComplete) {
                    android.util.Log.d("Analysis", "Preview stage was interrupted or failed")
                    return@launch
                }

                // ===== ANALYSE STAGE =====
                android.util.Log.d("Analysis", "Starting ANALYSE stage")

                // Validate ActivePlayer at start of Analyse stage
                if (!checkActivePlayer(AnalysisStage.ANALYSE)) {
                    android.util.Log.e("Analysis", "ActivePlayer validation failed at ANALYSE stage")
                    // Continue anyway but error will be shown to user
                }

                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.ANALYSE,
                    autoAnalysisCurrentScore = null,
                    remainingAnalysisMoves = buildMoveIndices()
                )

                // Kill current Stockfish and start new one for Analyse stage
                stockfish.stop()
                ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (!ready) {
                    android.util.Log.e("Analysis", "Failed to start Stockfish for Analyse stage")
                    enterManualStageInternal(findBiggestScoreChangeMove())
                    return@launch
                }

                stockfish.newGame()
                configureForAnalyseStage()
                delay(50)

                val analyseTimeMs = (_uiState.value.stockfishSettings.analyseStage.secondsForMove * 1000).toInt()
                val analyseComplete = runStageAnalysis(
                    stageName = "ANALYSE",
                    timePerMoveMs = analyseTimeMs,
                    expectedBoardHistorySize = expectedBoardHistorySize,
                    storeScore = { moveIndex, score ->
                        _uiState.value = _uiState.value.copy(
                            analyseScores = _uiState.value.analyseScores + (moveIndex to score),
                            autoAnalysisCurrentScore = score
                        )
                    },
                    configureEngine = { configureForAnalyseStage() }
                )

                if (!analyseComplete) {
                    android.util.Log.d("Analysis", "Analyse stage was interrupted")
                    return@launch
                }

                // ===== MANUAL STAGE =====
                android.util.Log.d("Analysis", "Analysis complete, entering MANUAL stage")

                // Store the analysed game before entering manual stage
                storeAnalysedGame()

                val biggestChangeMoveIndex = findBiggestScoreChangeMove()
                enterManualStageInternal(biggestChangeMoveIndex)

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Re-throw to properly cancel
            } catch (e: Exception) {
                android.util.Log.e("Analysis", "Error during analysis: ${e.message}")
                // Enter manual stage on error
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.MANUAL,
                    autoAnalysisIndex = -1
                )
            }
        }
    }

    /**
     * Run a single stage of analysis (Preview or Analyse).
     * Returns true if completed successfully, false if interrupted or failed.
     */
    private suspend fun runStageAnalysis(
        stageName: String,
        timePerMoveMs: Int,
        expectedBoardHistorySize: Int,
        storeScore: (Int, MoveScore) -> Unit,
        configureEngine: () -> Unit
    ): Boolean {
        val moveIndices = buildMoveIndices()
        android.util.Log.d("Analysis", "$stageName: analyzing ${moveIndices.size} moves, time=${timePerMoveMs}ms")

        val remainingMoves = moveIndices.toMutableList()
        var analyzedCount = 0

        for (moveIndex in moveIndices) {
            // Check for cancellation
            kotlinx.coroutines.yield()

            // Check if board history was modified (game reloaded)
            if (boardHistory.size != expectedBoardHistorySize) {
                android.util.Log.e("Analysis", "$stageName EXIT: boardHistory changed")
                return false
            }

            // Get the board position after this move
            val board = boardHistory.getOrNull(moveIndex + 1) ?: continue

            // Update remaining moves
            remainingMoves.remove(moveIndex)

            // Update UI state
            _uiState.value = _uiState.value.copy(
                autoAnalysisIndex = moveIndex,
                currentBoard = board,
                currentMoveIndex = moveIndex,
                autoAnalysisCurrentScore = null,
                analysisResult = null,
                remainingAnalysisMoves = remainingMoves.toList()
            )

            val fen = board.getFen()

            // Start analysis with time limit
            stockfish.analyzeWithTime(fen, timePerMoveMs)

            // Wait for completion
            val completed = stockfish.waitForCompletion(timePerMoveMs.toLong() + 2000)
            if (!completed) {
                stockfish.stop()
                delay(100)
            }

            // Check if engine crashed and restart if needed
            if (!stockfish.isReady.value) {
                android.util.Log.w("Analysis", "$stageName: Engine died at move $moveIndex, restarting...")
                val restarted = stockfish.restart()
                if (restarted) {
                    stockfish.newGame()
                    configureEngine()
                    delay(100)

                    // Retry the failed move
                    stockfish.analyzeWithTime(fen, timePerMoveMs)
                    val retryCompleted = stockfish.waitForCompletion(timePerMoveMs.toLong() + 2000)
                    if (!retryCompleted) {
                        stockfish.stop()
                        delay(100)
                    }
                } else {
                    android.util.Log.e("Analysis", "$stageName: Failed to restart engine")
                    return false
                }
            }

            // Check for cancellation after waiting
            kotlinx.coroutines.yield()

            // Check if board history was modified during wait
            if (boardHistory.size != expectedBoardHistorySize) {
                android.util.Log.e("Analysis", "$stageName EXIT after wait: boardHistory changed")
                return false
            }

            // Get the current analysis result and store the score
            val result = stockfish.analysisResult.value
            if (result != null) {
                val bestLine = result.bestLine
                if (bestLine != null) {
                    analyzedCount++
                    // Score adjustment: Stockfish gives score from side-to-move's perspective
                    // We want score from WHITE's perspective (positive = good for white)
                    val isWhiteToMove = board.getTurn() == com.eval.chess.PieceColor.WHITE
                    val adjustedScore = if (isWhiteToMove) bestLine.score else -bestLine.score
                    val adjustedMateIn = if (isWhiteToMove) bestLine.mateIn else -bestLine.mateIn

                    val score = MoveScore(
                        score = adjustedScore,
                        isMate = bestLine.isMate,
                        mateIn = adjustedMateIn,
                        depth = result.depth,
                        nodes = result.nodes,
                        nps = result.nps
                    )
                    storeScore(moveIndex, score)
                }
            }
        }

        android.util.Log.d("Analysis", "$stageName completed: analyzed=$analyzedCount out of ${moveIndices.size} moves")
        return true
    }

    /**
     * Find the move index with the biggest score change compared to the previous move.
     * Uses analyse scores if available, otherwise preview scores.
     */
    private fun findBiggestScoreChangeMove(): Int {
        val scores = _uiState.value.analyseScores.ifEmpty { _uiState.value.previewScores }
        if (scores.size < 2) return 0

        var maxChange = 0f
        var maxChangeIndex = 0

        val sortedIndices = scores.keys.sorted()
        for (i in 1 until sortedIndices.size) {
            val currentIndex = sortedIndices[i]
            val prevIndex = sortedIndices[i - 1]
            val currentScore = scores[currentIndex]?.score ?: continue
            val prevScore = scores[prevIndex]?.score ?: continue

            val change = kotlin.math.abs(currentScore - prevScore)
            if (change > maxChange) {
                maxChange = change
                maxChangeIndex = currentIndex
            }
        }

        return maxChangeIndex
    }

    /**
     * Internal function to enter Manual stage at a specific move.
     * Kills current Stockfish and starts a new one configured for Manual stage.
     */
    private fun enterManualStageInternal(moveIndex: Int) {
        // Validate ActivePlayer at start of Manual stage
        if (!checkActivePlayer(AnalysisStage.MANUAL)) {
            android.util.Log.e("Analysis", "ActivePlayer validation failed at MANUAL stage")
            // Continue anyway but error will be shown to user
        }

        viewModelScope.launch {
            // Stop any running analysis
            autoAnalysisJob?.cancel()
            stockfish.stop()

            // Navigate to the specified move
            val validIndex = moveIndex.coerceIn(-1, boardHistory.size - 2)
            val board = boardHistory.getOrNull(validIndex + 1) ?: ChessBoard()

            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze
            analysisRequestId++
            val thisRequestId = analysisRequestId

            _uiState.value = _uiState.value.copy(
                currentStage = AnalysisStage.MANUAL,
                autoAnalysisIndex = -1,
                currentMoveIndex = validIndex,
                currentBoard = board.copy(),
                autoAnalysisCurrentScore = null,
                remainingAnalysisMoves = emptyList(),
                stockfishReady = false,
                analysisResult = null,
                analysisResultFen = null
            )

            // Start new Stockfish process for Manual stage
            val ready = stockfish.restart()
            _uiState.value = _uiState.value.copy(stockfishReady = ready)

            if (ready) {
                delay(200)
                stockfish.newGame()
                delay(100)
                configureForManualStage()
                delay(100)
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    /**
     * Enter Manual stage at the current position.
     * Called when user interrupts Analyse stage by navigating.
     */
    private fun enterManualStageAtCurrentPosition() {
        val currentIndex = _uiState.value.currentMoveIndex
        enterManualStageInternal(currentIndex)
    }

    /**
     * Enter Manual stage at the move with the biggest score change.
     * Called when user clicks the stage indicator bar during Analyse stage.
     */
    fun enterManualStageAtBiggestChange() {
        if (_uiState.value.currentStage != AnalysisStage.ANALYSE) return
        val biggestChangeMoveIndex = findBiggestScoreChangeMove()
        enterManualStageInternal(biggestChangeMoveIndex)
    }

    /**
     * Enter Manual stage at a specific move index.
     * Called when user clicks on the graph during Analyse stage.
     */
    fun enterManualStageAtMove(moveIndex: Int) {
        if (_uiState.value.currentStage == AnalysisStage.PREVIEW) return  // Preview is not interruptible
        enterManualStageInternal(moveIndex)
    }

    /**
     * Make a manual move on the board (from user drag-and-drop).
     * Only allowed during Manual stage.
     */
    fun makeManualMove(from: com.eval.chess.Square, to: com.eval.chess.Square) {
        // Only allow moves during Manual stage
        if (_uiState.value.currentStage != AnalysisStage.MANUAL) return

        val currentBoard = _uiState.value.currentBoard

        // Check if move is legal
        if (!currentBoard.isLegalMove(from, to)) return

        // Handle pawn promotion - default to queen for simplicity
        val promotion = if (currentBoard.needsPromotion(from, to)) {
            com.eval.chess.PieceType.QUEEN
        } else null

        // Make a copy of the board and execute the move
        val newBoard = currentBoard.copy()
        if (!newBoard.makeMoveFromSquares(from, to, promotion)) return

        if (_uiState.value.isExploringLine) {
            // Add the new board position to exploring line history
            exploringLineHistory.add(newBoard.copy())
            val newMoveIndex = _uiState.value.exploringLineMoveIndex + 1
            val uciMove = from.toAlgebraic() + to.toAlgebraic() + (promotion?.let {
                when (it) {
                    com.eval.chess.PieceType.QUEEN -> "q"
                    com.eval.chess.PieceType.ROOK -> "r"
                    com.eval.chess.PieceType.BISHOP -> "b"
                    com.eval.chess.PieceType.KNIGHT -> "n"
                    else -> ""
                }
            } ?: "")

            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoves = _uiState.value.exploringLineMoves + uciMove,
                exploringLineMoveIndex = newMoveIndex
            )
        } else {
            // In main game: enter exploring line mode with this move
            exploringLineHistory.clear()
            exploringLineHistory.add(currentBoard.copy()) // Starting position
            exploringLineHistory.add(newBoard.copy())     // After the move

            val uciMove = from.toAlgebraic() + to.toAlgebraic() + (promotion?.let {
                when (it) {
                    com.eval.chess.PieceType.QUEEN -> "q"
                    com.eval.chess.PieceType.ROOK -> "r"
                    com.eval.chess.PieceType.BISHOP -> "b"
                    com.eval.chess.PieceType.KNIGHT -> "n"
                    else -> ""
                }
            } ?: "")

            _uiState.value = _uiState.value.copy(
                isExploringLine = true,
                exploringLineMoves = listOf(uciMove),
                exploringLineMoveIndex = 0,
                savedGameMoveIndex = _uiState.value.currentMoveIndex,
                currentBoard = newBoard
            )
        }

        // Run Stockfish analysis on the new position - use full restart similar to navigation
        restartAnalysisForExploringLine()
    }

    /**
     * Restart Stockfish analysis for exploring line moves.
     * Similar to restartAnalysisAtMove but uses the current board position.
     */
    private fun restartAnalysisForExploringLine() {
        // Cancel any running analysis
        manualAnalysisJob?.cancel()

        viewModelScope.launch {
            // Stop Stockfish completely
            stockfish.stop()

            // Increment request ID to invalidate any pending results
            analysisRequestId++
            val thisRequestId = analysisRequestId

            // Get the current board (already set by makeManualMove)
            val board = _uiState.value.currentBoard
            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze

            // Clear analysis result but keep UI stable
            _uiState.value = _uiState.value.copy(
                analysisResultFen = null  // Mark as stale, but keep result for UI stability
            )

            // Small delay to ensure Stockfish has stopped
            delay(50)

            // Send new game command to clear Stockfish's internal state
            stockfish.newGame()
            delay(50)

            // Start fresh analysis
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoAnalysisJob?.cancel()
        manualAnalysisJob?.cancel()
        stockfish.shutdown()
    }
}
