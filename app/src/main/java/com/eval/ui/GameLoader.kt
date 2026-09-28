package com.eval.ui

import com.eval.chess.ChessBoard
import com.eval.chess.PgnParser
import com.eval.chess.PieceType
import com.eval.data.ChessRepository
import com.eval.data.ChessServer
import com.eval.data.LichessGame
import com.eval.data.Player
import com.eval.data.Players
import com.eval.data.Result
import com.eval.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles loading and parsing games from various sources.
 */
internal class GameLoader(
    private val repository: ChessRepository,
    private val getUiState: () -> GameUiState,
    private val updateUiState: (GameUiState.() -> GameUiState) -> Unit,
    private val viewModelScope: CoroutineScope,
    private val getBoardHistory: () -> MutableList<ChessBoard>,
    private val getExploringLineHistory: () -> MutableList<ChessBoard>,
    private val settingsPrefs: SettingsPreferences,
    private val gameStorage: GameStorageManager,
    private val analysisOrchestrator: AnalysisOrchestrator,
    private val analyzeRestoredPosition: suspend (String) -> Unit = { },
    private val getAppVersionCode: () -> Long = { 0L },
    private val stopLiveFollow: () -> Unit = {},
    // Clears per-game UI flows (such as a pending AI report) when a different game is committed.
    private val onGameCommitted: () -> Unit = {}
) {
    // Temporary storage for server/username when showing game selection dialog
    private var pendingGameSelectionServer: ChessServer? = null
    private var pendingGameSelectionUsername: String? = null
    private var isPgnFileSelection: Boolean = false
    private var gameSelectionGeneration = 0L

    /** A newer local selection owns the screen even if an older download finishes later. */
    fun invalidatePendingRetrieval() {
        gameSelectionGeneration++
        updateUiState {
            copy(
                isLoading = false,
                gameSelectionLoading = false,
                showGameSelection = false,
                showSelectedRetrieveGames = false,
                selectedRetrieveEntry = null,
                selectedRetrieveGames = emptyList()
            )
        }
    }

    val savedLichessUsername: String
        get() = settingsPrefs.savedLichessUsername

    /** Load the account's newest game, retaining the previous save as an offline fallback. */
    fun loadStartupGame(prepareEngine: suspend () -> Boolean) {
        // Reserve startup's place before engine initialization can suspend. A later
        // import, reload, or selection owns the screen even if startup finishes last.
        val generation = ++gameSelectionGeneration
        viewModelScope.launch {
            val engineReady = prepareEngine()
            if (generation != gameSelectionGeneration) return@launch
            val username = settingsPrefs.knownLichessUsername
            // Without an engine, still show the saved game; it doesn't need analysis to be viewed.
            if (engineReady && username != null) {
                settingsPrefs.saveLastServerUser(username, "lichess.org")
                updateUiState { copy(hasLastServerUser = true) }
                fetchLastGameFromServer(ChessServer.LICHESS, username, generation)
            }
            if (generation == gameSelectionGeneration && getUiState().game == null) {
                val retrievalError = getUiState().errorMessage
                gameStorage.loadManualStageGame()?.let { loadAnalysedGameDirectly(it) }
                if (retrievalError != null) updateUiState { copy(errorMessage = retrievalError) }
            }
        }
    }

    /** Retrieve the latest game for the last requested Lichess account. */
    fun reloadLastGame() {
        val username = settingsPrefs.lastServerUser
        val serverName = settingsPrefs.lastServerName
        if (username != null) {
            val server = when (serverName) {
                "lichess.org" -> ChessServer.LICHESS
                else -> return
            }
            val generation = ++gameSelectionGeneration
            viewModelScope.launch {
                fetchLastGameFromServer(server, username, generation)
            }
        }
    }

    /**
     * Fetch the most recent game from a specific server for a username.
     */
    suspend fun fetchLastGameFromServer(server: ChessServer, username: String) {
        if (username.isBlank() || server != ChessServer.LICHESS) return
        fetchLastGameFromServer(server, username, ++gameSelectionGeneration)
    }

    private suspend fun fetchLastGameFromServer(server: ChessServer, username: String, generation: Long) {
        if (username.isBlank() || server != ChessServer.LICHESS || generation != gameSelectionGeneration) return

        updateUiState {
            copy(
                isLoading = true,
                errorMessage = null
            )
        }

        val result = repository.getLichessGames(username, 1)
        if (generation != gameSelectionGeneration) return

        when (result) {
            is Result.Success -> {
                val games = result.data
                if (games.isNotEmpty()) {
                    updateUiState {
                        copy(
                            isLoading = false,
                            gameList = games,
                            showGameSelection = false,
                            gameSelectionUsername = username
                        )
                    }
                    // The newest game is often the one already analysed: reopen that analysis
                    // instead of spending minutes re-analysing it.
                    val latest = games.first()
                    val analysed = latest.pgn?.let { pgn ->
                        withContext(Dispatchers.IO) { gameStorage.loadManualGamesList() }.firstOrNull { it.pgn == pgn }
                    }
                    if (generation != gameSelectionGeneration) return
                    if (analysed != null) loadAnalysedGameDirectly(analysed)
                    else loadGame(latest, server, username, userChosen = false)
                } else {
                    updateUiState {
                        copy(
                            isLoading = false,
                            errorMessage = "No games found for $username on Lichess"
                        )
                    }
                }
            }
            is Result.Error -> {
                updateUiState {
                    copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun fetchGames(server: ChessServer, username: String) {
        if (server != ChessServer.LICHESS) return
        val generation = ++gameSelectionGeneration

        val pageSize = 25

        // The current game and its analysis stay until another game is actually chosen.
        viewModelScope.launch {
            if (generation != gameSelectionGeneration) return@launch
            updateUiState {
                copy(
                    isLoading = true,
                    errorMessage = null,
                    gameList = emptyList(),
                    showGameSelection = false,
                    gameSelectionPage = 0,
                    gameSelectionLoading = false,
                    gameSelectionHasMore = true
                )
            }

            val result = repository.getLichessGames(username, pageSize)
            if (generation != gameSelectionGeneration) return@launch

            when (result) {
                is Result.Success -> {
                    // Only a name that Lichess accepted becomes the saved account.
                    settingsPrefs.saveLichessUsername(username)
                    settingsPrefs.saveLastServerUser(username, "lichess.org")
                    settingsPrefs.setFirstGameRetrievedVersion(getAppVersionCode())
                    updateUiState { copy(hasLastServerUser = true) }
                    val games = result.data
                    if (games.isNotEmpty()) {
                        storeRetrievedGames(games, username, server)
                    }
                    if (games.size == 1) {
                        updateUiState {
                            copy(
                                isLoading = false,
                                gameList = games,
                                showGameSelection = false,
                                gameSelectionUsername = username,
                                gameSelectionServer = server,
                                gameSelectionHasMore = false
                            )
                        }
                        loadGame(games.first(), server, username)
                    } else {
                        val entry = RetrievedGamesEntry(accountName = username, server = server)
                        updateUiState {
                            copy(
                                isLoading = false,
                                showRetrieveScreen = false,
                                showSelectedRetrieveGames = true,
                                selectedRetrieveEntry = entry,
                                selectedRetrieveGames = games,
                                gameSelectionPage = 0,
                                gameSelectionHasMore = games.size >= pageSize
                            )
                        }
                    }
                }
                is Result.Error -> {
                    updateUiState {
                        copy(
                            isLoading = false,
                            errorMessage = result.message,
                            gameSelectionHasMore = false
                        )
                    }
                }
            }
        }
    }

    fun selectGame(game: LichessGame) {
        updateUiState { copy(showGameSelection = false, showRetrieveScreen = false) }

        if (isPgnFileSelection) {
            isPgnFileSelection = false
            pendingGameSelectionServer = null
            pendingGameSelectionUsername = null
            val whiteName = game.players.white.user?.name ?: "White"
            loadGame(game, null, whiteName)
            return
        }

        val server = pendingGameSelectionServer
        val username = pendingGameSelectionUsername
        pendingGameSelectionServer = null
        pendingGameSelectionUsername = null
        loadGame(game, server, username)
    }

    fun dismissGameSelection() {
        updateUiState { copy(showGameSelection = false) }
        isPgnFileSelection = false
    }

    fun clearGame() {
        invalidatePendingRetrieval()
        stopLiveFollow()
        analysisOrchestrator.stop()
        val boardHistory = getBoardHistory()
        val exploringLineHistory = getExploringLineHistory()
        boardHistory.clear()
        exploringLineHistory.clear()
        updateUiState {
            copy(
                game = null,
                gameList = emptyList(),
                showGameSelection = false,
                showRetrieveScreen = true,
                currentBoard = ChessBoard(),
                moves = emptyList(),
                moveDetails = emptyList(),
                currentMoveIndex = -1,
                analysisResult = null,
                analysisResultFen = null,
                openingName = null,
                currentOpeningName = null,
                openingExplorerData = null,
                openingExplorerLoading = false,
                openingExplorerError = null,
                moveQualities = emptyMap(),
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
    }

    /**
     * [userChosen] is false for the automatic startup/reload load: it must not close flows the
     * user already opened (such as a pending AI report) the way choosing a different game does.
     */
    fun loadGame(game: LichessGame, server: ChessServer?, username: String?, userChosen: Boolean = true) {
        val pgn = game.pgn
        if (pgn == null) {
            updateUiState {
                copy(
                    isLoading = false,
                    errorMessage = "No PGN data available"
                )
            }
            return
        }

        val pgnHeaders = PgnParser.parseHeaders(pgn)
        val variant = unsupportedVariant(game, pgnHeaders)
        if (variant != null) {
            updateUiState { copy(isLoading = false, errorMessage = "$variant games can't be analysed: Eval supports standard chess only") }
            return
        }
        val startingBoard = PgnParser.parseInitialBoard(pgn)
        if (startingBoard == null) {
            updateUiState { copy(isLoading = false, errorMessage = "Invalid PGN starting position") }
            return
        }
        invalidatePendingRetrieval()
        analysisOrchestrator.stop()
        if (userChosen) onGameCommitted()
        stopLiveFollow()
        // The previous save stays as the startup fallback until this game reaches Manual and is saved.
        // An ECO code is not an opening name; without one the name is derived from the moves.
        val openingName = pgnHeaders["Opening"]

        val parsedMoves = PgnParser.parseMovesWithClock(pgn)
        val moveDetailsList = mutableListOf<MoveDetails>()

        val (boards, validMoves) = buildBoardHistory(
            moves = parsedMoves.map { it.san },
            initialBoard = startingBoard,
            onMoveApplied = { index, move, boardBefore, boardAfter ->
                val details = buildMoveDetails(boardAfter, boardBefore, move, parsedMoves[index].clockTime)
                if (details != null) {
                    moveDetailsList.add(details)
                }
            },
            onMoveFailed = { index, move, boardBefore ->
                val moveNum = (index / 2) + 1
                val isWhite = index % 2 == 0
                val prefix = if (isWhite) "$moveNum." else "$moveNum..."
                android.util.Log.e("GameLoader", "FAILED to apply move $prefix $move - FEN: ${boardBefore.getFen()}")
            }
        )

        val boardHistory = getBoardHistory()
        val exploringLineHistory = getExploringLineHistory()
        boardHistory.clear()
        exploringLineHistory.clear()
        boardHistory.addAll(boards)
        val initialBoard = boards.firstOrNull() ?: ChessBoard()

        val providedUsername = username ?: savedLichessUsername
        val whitePlayerName = game.players.white.user?.name ?: "White"
        val blackPlayerName = game.players.black.user?.name?.lowercase() ?: ""

        val usernameMatchesGame = providedUsername.isNotEmpty() &&
            (providedUsername.lowercase() == whitePlayerName.lowercase() ||
             providedUsername.lowercase() == blackPlayerName)

        val perspectivePlayer = if (usernameMatchesGame) providedUsername else whitePlayerName
        val userPlayedBlack = perspectivePlayer.lowercase() == blackPlayerName

        updateUiState {
            copy(
                isLoading = false,
                game = game,
                gameLoadVersion = gameLoadVersion + 1,
                gameSelectionServer = server ?: if (gameSiteUrl(game.pgn.orEmpty()) != null) ChessServer.LICHESS else ChessServer.LOCAL,
                errorMessage = importError(parsedMoves.map { it.san }, validMoves),
                openingName = openingName,
                currentOpeningName = null,
                openingExplorerData = null,
                openingExplorerLoading = false,
                openingExplorerError = null,
                moves = validMoves,
                moveDetails = moveDetailsList,
                currentBoard = initialBoard,
                currentMoveIndex = -1,
                flippedBoard = userPlayedBlack,
                userPlayedBlack = userPlayedBlack,
                showRetrieveScreen = false,
                isExploringLine = false,
                exploringLineMoves = emptyList(),
                exploringLineMoveIndex = -1,
                savedGameMoveIndex = -1,
                currentStage = AnalysisStage.PREVIEW,
                previewScores = emptyMap(),
                analyseScores = emptyMap(),
                moveQualities = emptyMap(),
                analysisResult = null,
                analysisResultFen = null,
                autoAnalysisIndex = -1
            )
        }

        analysisOrchestrator.startAnalysis()
    }

    fun loadAnalysedGameDirectly(analysedGame: AnalysedGame) {
        val startingBoard = PgnParser.parseInitialBoard(analysedGame.pgn)
        if (startingBoard == null) {
            updateUiState { copy(isLoading = false, errorMessage = "Invalid PGN starting position") }
            return
        }
        invalidatePendingRetrieval()
        analysisOrchestrator.stop()
        stopLiveFollow()

        val headers = PgnParser.parseHeaders(analysedGame.pgn)
        val parsedMoves = PgnParser.parseMovesWithClock(analysedGame.pgn)
        val moveDetails = mutableListOf<MoveDetails>()
        val (boards, validMoves) = buildBoardHistory(
            parsedMoves.map { it.san }, initialBoard = startingBoard,
            onMoveApplied = { index, move, before, after ->
                // PGN is the source of truth; older saves can have incomplete cached details.
                val lastMove = after.getLastMove()
                val cached = analysedGame.moveDetails.getOrNull(index)?.takeIf {
                    it.from == lastMove?.from?.toAlgebraic() && it.to == lastMove?.to?.toAlgebraic()
                }
                buildMoveDetails(after, before, move, parsedMoves[index].clockTime ?: cached?.clockTime)
                    ?.let { moveDetails.add(it) }
            }
        )
        val pgnResult = headers["Result"]?.takeIf { it in setOf("1-0", "0-1", "1/2-1/2", "*") }
            ?: PgnParser.parseResult(analysedGame.pgn)
        val result = pgnResult ?: when {
            // Preserve the legacy opening-study correction only when PGN has no result.
            analysedGame.whiteName == "White" && analysedGame.blackName == "Black" &&
                analysedGame.result == "1/2-1/2" -> "*"
            else -> analysedGame.result
        }

        val boardHistory = getBoardHistory()
        val exploringLineHistory = getExploringLineHistory()
        boardHistory.clear()
        exploringLineHistory.clear()
        boardHistory.addAll(boards)

        val lichessGame = LichessGame(
            id = "analysed_${analysedGame.timestamp}",
            rated = false,
            variant = "standard",
            speed = analysedGame.speed ?: "unknown",
            perf = null,
            status = when (result) {
                "1-0", "0-1" -> "mate"
                "1/2-1/2" -> "draw"
                else -> "*"
            },
            winner = when (result) {
                "1-0" -> "white"
                "0-1" -> "black"
                else -> null
            },
            players = Players(
                white = Player(
                    user = User(name = analysedGame.whiteName, id = analysedGame.whiteName.lowercase()),
                    rating = headers["WhiteElo"]?.toIntOrNull(),
                    aiLevel = null
                ),
                black = Player(
                    user = User(name = analysedGame.blackName, id = analysedGame.blackName.lowercase()),
                    rating = headers["BlackElo"]?.toIntOrNull(),
                    aiLevel = null
                )
            ),
            pgn = analysedGame.pgn,
            moves = null,
            clock = null,
            createdAt = analysedGame.timestamp,
            lastMoveAt = analysedGame.timestamp
        )

        val biggestChangeMoveIndex = findBiggestScoreChangeInScores(
            analysedGame.analyseScores, analysedGame.previewScores, analysedGame.moves.size
        )
        val validIndex = biggestChangeMoveIndex.coerceIn(-1, boardHistory.size - 2)
        val board = if (validIndex >= 0 && validIndex < boardHistory.size - 1) {
            boardHistory[validIndex + 1]
        } else {
            boardHistory.firstOrNull() ?: ChessBoard()
        }

        // Default to white player's perspective
        val userPlayedBlack = false

        updateUiState {
            copy(
                game = lichessGame,
                gameLoadVersion = gameLoadVersion + 1,
                gameSelectionServer = if (gameSiteUrl(analysedGame.pgn) != null) ChessServer.LICHESS else ChessServer.LOCAL,
                moves = validMoves,
                moveDetails = moveDetails,
                errorMessage = importError(parsedMoves.map { it.san }, validMoves),
                currentMoveIndex = validIndex,
                currentBoard = board.copy(),
                flippedBoard = userPlayedBlack,
                userPlayedBlack = userPlayedBlack,
                openingName = analysedGame.openingName,
                currentOpeningName = null,
                openingExplorerData = null,
                openingExplorerLoading = false,
                openingExplorerError = null,
                analysisResult = null,
                analysisResultFen = null,
                moveQualities = analysisOrchestrator.calculateMoveQualities(
                    analysedGame.previewScores, analysedGame.analyseScores),
                showRetrieveScreen = false,
                previewScores = analysedGame.previewScores,
                analyseScores = analysedGame.analyseScores,
                currentStage = AnalysisStage.MANUAL,
                autoAnalysisIndex = -1,
                isExploringLine = false,
                exploringLineMoves = emptyList(),
                exploringLineMoveIndex = -1,
                savedGameMoveIndex = -1
            )
        }

        // Reopening a previous game must also select it for the next startup.
        // Avoid rewriting storage when startup is already restoring this exact save.
        if (gameStorage.loadManualStageGame() != analysedGame) {
            gameStorage.saveManualStageGame(analysedGame)
        }
        val fenToAnalyze = board.getFen()

        analysisOrchestrator.manualAnalysisJob = viewModelScope.launch {
            analyzeRestoredPosition(fenToAnalyze)
        }
    }

    /** The variant's name when it isn't standard chess (Lichess game field or PGN [Variant] tag), else null. */
    private fun unsupportedVariant(game: LichessGame, headers: Map<String, String>): String? {
        val lichess = game.variant.trim()
        if (lichess.isNotEmpty() && !PgnParser.isStandardVariant(lichess)) return lichess.replaceFirstChar { it.uppercase() }
        val tag = headers["Variant"]?.trim().orEmpty()
        return tag.takeIf { it.isNotEmpty() && !PgnParser.isStandardVariant(it) }
    }

    private fun buildMoveDetails(
        boardAfterMove: ChessBoard,
        boardBeforeMove: ChessBoard,
        san: String,
        clockTime: String?
    ): MoveDetails? {
        val lastMove = boardAfterMove.getLastMove() ?: return null
        val fromSquare = lastMove.from.toAlgebraic()
        val toSquare = lastMove.to.toAlgebraic()
        val capturedPiece = boardBeforeMove.getPiece(lastMove.to)
        // After a promotion this is the new piece, so the move list shows what the pawn became.
        val movedPiece = boardAfterMove.getPiece(lastMove.to)
        val pieceType = when (movedPiece?.type) {
            PieceType.KING -> "K"
            PieceType.QUEEN -> "Q"
            PieceType.ROOK -> "R"
            PieceType.BISHOP -> "B"
            PieceType.KNIGHT -> "N"
            PieceType.PAWN -> "P"
            else -> "P"
        }
        val isEnPassant = pieceType == "P" &&
            lastMove.from.file != lastMove.to.file &&
            capturedPiece == null
        val isCapture = capturedPiece != null || isEnPassant

        return MoveDetails(
            san = san,
            from = fromSquare,
            to = toSquare,
            isCapture = isCapture,
            pieceType = pieceType,
            clockTime = clockTime
        )
    }

    /**
     * Build a board history by applying each move to a fresh ChessBoard.
     * Returns the list of board states (starting with the initial position)
     * and the list of moves that were successfully applied.
     *
     * @param moves list of SAN or UCI move strings to apply
     * @param onMoveApplied optional callback invoked after each successful move with
     *        (index, move, boardBefore, boardAfter)
     * @param onMoveFailed optional callback invoked when a move fails with
     *        (index, move, boardBefore)
     */
    private fun buildBoardHistory(
        moves: List<String>,
        initialBoard: ChessBoard = ChessBoard(),
        onMoveApplied: ((index: Int, move: String, boardBefore: ChessBoard, boardAfter: ChessBoard) -> Unit)? = null,
        onMoveFailed: ((index: Int, move: String, boardBefore: ChessBoard) -> Unit)? = null
    ): Pair<List<ChessBoard>, List<String>> {
        val result = BoardHistoryBuilder.build(
            moves = moves,
            initialBoard = initialBoard,
            onMoveApplied = onMoveApplied,
            onMoveFailed = onMoveFailed
        )
        return Pair(result.boards, result.validMoves)
    }

    private fun importError(moves: List<String>, validMoves: List<String>): String? {
        if (moves.size == validMoves.size) return null
        return "Cannot read move ${validMoves.size + 1}: ${moves[validMoves.size]}. Loaded ${validMoves.size} of ${moves.size} moves."
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

    fun selectGameFromRetrieve(game: LichessGame) {
        val entry = getUiState().selectedRetrieveEntry ?: return
        updateUiState {
            copy(
                showSelectedRetrieveGames = false,
                showRetrieveScreen = false,
                selectedRetrieveEntry = null,
                selectedRetrieveGames = emptyList()
            )
        }
        loadGame(game, entry.server, entry.accountName)
    }

    private fun storeRetrievedGames(games: List<LichessGame>, username: String, server: ChessServer) {
        gameStorage.storeRetrievedGames(games, username, server)
        val retrievesList = gameStorage.loadRetrievesList()
        updateUiState {
            copy(
                hasPreviousRetrieves = retrievesList.isNotEmpty(),
                previousRetrievesList = retrievesList
            )
        }
    }

    fun showPreviousRetrieves() {
        val retrievesList = gameStorage.loadRetrievesList()
        if (retrievesList.isEmpty()) return

        if (retrievesList.size == 1) {
            selectPreviousRetrieve(retrievesList.first())
        } else {
            updateUiState {
                copy(
                    showRetrieveScreen = false,
                    previousRetrievesList = retrievesList,
                    showPreviousRetrievesSelection = true
                )
            }
        }
    }

    fun dismissPreviousRetrievesSelection() {
        updateUiState { copy(showPreviousRetrievesSelection = false) }
    }

    fun selectPreviousRetrieve(entry: RetrievedGamesEntry) {
        val games = gameStorage.loadGamesForRetrieve(entry)
        if (games.isNotEmpty()) {
            gameSelectionGeneration++
            updateUiState {
                copy(
                    showRetrieveScreen = false,
                    isLoading = false,
                    showPreviousRetrievesSelection = false,
                    showSelectedRetrieveGames = true,
                    selectedRetrieveEntry = entry,
                    selectedRetrieveGames = games,
                    gameSelectionPage = 0,
                    gameSelectionLoading = false,
                    gameSelectionHasMore = entry.server == ChessServer.LICHESS && games.size >= 25,
                    errorMessage = null
                )
            }
        }
    }

    fun dismissSelectedRetrieveGames() {
        gameSelectionGeneration++
        updateUiState {
            copy(
                showSelectedRetrieveGames = false,
                gameSelectionLoading = false,
                selectedRetrieveEntry = null,
                selectedRetrieveGames = emptyList()
            )
        }
    }

    fun nextGameSelectionPage(pageSize: Int) {
        val state = getUiState()
        if (state.gameSelectionLoading) return
        val currentPage = state.gameSelectionPage

        // Handle analysed games selection (no API fetching needed)
        if (state.showAnalysedGamesSelection) {
            val nextPageStartIndex = (currentPage + 1) * pageSize
            if (nextPageStartIndex < state.analysedGamesList.size) {
                updateUiState { copy(gameSelectionPage = currentPage + 1) }
            }
            return
        }

        val currentGames = state.selectedRetrieveGames
        val hasMore = state.gameSelectionHasMore
        val entry = state.selectedRetrieveEntry ?: return

        val nextPageStartIndex = (currentPage + 1) * pageSize
        val nextPageEndIndex = nextPageStartIndex + pageSize

        // Fill the next page before showing it. Otherwise a partial page from
        // the initial batch skips its missing games when the user taps Next again.
        if (nextPageEndIndex > currentGames.size && hasMore && entry.server == ChessServer.LICHESS) {
            val generation = gameSelectionGeneration
            updateUiState { copy(gameSelectionLoading = true, errorMessage = null) }

            viewModelScope.launch {
                val newCount = nextPageEndIndex
                // Fetch only the games after the ones already loaded when their time is known.
                val until = currentGames.lastOrNull()?.createdAt
                val gamesResult = if (until != null) {
                    when (val more = repository.getLichessGames(entry.accountName, newCount - currentGames.size, until - 1)) {
                        is Result.Success -> Result.Success((currentGames + more.data).distinctBy { it.id })
                        is Result.Error -> more
                    }
                } else repository.getLichessGames(entry.accountName, newCount)
                if (generation != gameSelectionGeneration) return@launch
                when (gamesResult) {
                    is Result.Success -> {
                        val fetchedGames = gamesResult.data
                        if (fetchedGames.isNotEmpty()) {
                            storeRetrievedGames(fetchedGames, entry.accountName, entry.server)
                        }
                        updateUiState {
                            copy(
                                selectedRetrieveGames = fetchedGames,
                                gameSelectionLoading = false,
                                gameSelectionPage = if (nextPageStartIndex < fetchedGames.size) currentPage + 1 else currentPage,
                                gameSelectionHasMore = fetchedGames.size >= newCount
                            )
                        }
                    }
                    is Result.Error -> {
                        updateUiState {
                            copy(
                                gameSelectionLoading = false,
                                errorMessage = gamesResult.message
                            )
                        }
                    }
                }
            }
        } else if (nextPageStartIndex < currentGames.size) {
            updateUiState { copy(gameSelectionPage = currentPage + 1) }
        }
    }

    fun previousGameSelectionPage() {
        val currentPage = getUiState().gameSelectionPage
        if (currentPage > 0) {
            updateUiState { copy(gameSelectionPage = currentPage - 1) }
        }
    }

    // PGN file loading
    fun loadGamesFromPgnContent(pgnContent: String, onMultipleEvents: ((Boolean) -> Unit)? = null) {
        showParsedPgn(repository.parseGamesFromPgnContent(pgnContent), onMultipleEvents)
    }

    /**
     * Read and parse a PGN file off the main thread: [readText] is bounded (size, archive limits)
     * and cancellable, and failures are shown instead of being dropped.
     */
    fun loadPgnFile(readText: suspend () -> String, onMultipleEvents: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true, errorMessage = null) }
            val result = try {
                val text = readText()
                withContext(Dispatchers.Default) { repository.parseGamesFromPgnContent(text) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.message ?: "This file could not be read.")
            } catch (e: OutOfMemoryError) {
                Result.Error("This file is too large to open.")
            }
            updateUiState { copy(isLoading = false) }
            showParsedPgn(result, onMultipleEvents)
        }
    }

    private fun showParsedPgn(result: Result<List<LichessGame>>, onMultipleEvents: ((Boolean) -> Unit)?) {
        when (result) {
            is Result.Success -> {
                invalidatePendingRetrieval()
                val games = result.data
                if (games.size == 1) {
                    selectPgnGame(games.first())
                } else {
                    val gamesByEvent = games.groupBy { game ->
                        game.pgn?.let { PgnParser.parseHeaders(it)["Event"] } ?: "Unknown Event"
                    }

                    updateUiState {
                        copy(
                            showPgnEventSelection = true,
                            pgnEvents = gamesByEvent.keys.toList().sorted(),
                            pgnGamesByEvent = gamesByEvent,
                            selectedPgnEvent = if (gamesByEvent.size == 1) gamesByEvent.keys.first() else null,
                            pgnGamesForSelectedEvent = if (gamesByEvent.size == 1) games else emptyList()
                        )
                    }
                    onMultipleEvents?.invoke(true)
                }
            }
            is Result.Error -> {
                updateUiState { copy(errorMessage = result.message) }
            }
        }
    }

    fun selectPgnEvent(event: String) {
        val games = getUiState().pgnGamesByEvent[event] ?: return
        updateUiState {
            copy(
                selectedPgnEvent = event,
                pgnGamesForSelectedEvent = games
            )
        }
    }

    fun backToPgnEventList() {
        updateUiState {
            copy(
                selectedPgnEvent = null,
                pgnGamesForSelectedEvent = emptyList()
            )
        }
    }

    fun dismissPgnEventSelection() {
        updateUiState {
            copy(
                showPgnEventSelection = false,
                pgnEvents = emptyList(),
                pgnGamesByEvent = emptyMap(),
                selectedPgnEvent = null,
                pgnGamesForSelectedEvent = emptyList()
            )
        }
    }

    fun selectPgnGameFromEvent(game: LichessGame) {
        dismissPgnEventSelection()
        val whiteName = game.players.white.user?.name ?: "White"
        loadGame(game, null, whiteName)
    }

    fun selectPgnGame(game: LichessGame) {
        updateUiState {
            copy(
                showGameSelection = false,
                showRetrieveScreen = false,
                showPgnEventSelection = false,
                pgnEvents = emptyList(),
                pgnGamesByEvent = emptyMap()
            )
        }
        val whiteName = game.players.white.user?.name ?: "White"
        loadGame(game, null, whiteName)
    }

    fun setLichessMaxGames(max: Int) {
        val validMax = max.coerceIn(1, 25)
        settingsPrefs.saveLichessMaxGames(validMax)
        updateUiState { copy(lichessMaxGames = validMax) }
    }
}

internal fun PieceType?.toUciSuffix(): String = when (this) {
    PieceType.QUEEN -> "q"
    PieceType.ROOK -> "r"
    PieceType.BISHOP -> "b"
    PieceType.KNIGHT -> "n"
    else -> ""
}
