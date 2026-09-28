package com.eval.ui

import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.stockfish.EngineHistory
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages the three-stage analysis pipeline (Preview → Analyse → Manual).
 * Orchestrates Stockfish analysis across different stages with appropriate settings.
 */
internal class AnalysisOrchestrator(
    private val stockfish: StockfishEngine,
    private val getUiState: () -> GameUiState,
    private val updateUiState: (GameUiState.() -> GameUiState) -> Unit,
    private val viewModelScope: CoroutineScope,
    private val getBoardHistory: () -> MutableList<ChessBoard>,
    private val saveManualGame: (AnalysedGame) -> Unit = {},
    private val storeManualGameToList: (AnalysedGame) -> Unit = {},
    private val getExploringLineHistory: () -> List<ChessBoard> = { emptyList() }
) {
    private val analysisMutex = Mutex()
    var autoAnalysisJob: Job? = null
    var manualAnalysisJob: Job? = null
    var currentAnalysisFen: String? = null
    val analysisRequestId = AtomicLong(0)

    // Analysis pauses while Eval is in the background (the engine would otherwise keep
    // several cores busy for minutes behind other apps).
    private val foreground = MutableStateFlow(true)
    private var manualPausedInBackground = false

    fun onAppBackgrounded() {
        foreground.value = false
        if (getUiState().currentStage == AnalysisStage.MANUAL && manualAnalysisJob?.isActive == true) {
            manualPausedInBackground = true
            analysisRequestId.incrementAndGet()
            manualAnalysisJob?.cancel()
            stockfish.stop()
        }
    }

    fun onAppForegrounded() {
        foreground.value = true
        if (manualPausedInBackground) {
            manualPausedInBackground = false
            if (getUiState().currentStage == AnalysisStage.MANUAL) restartAnalysisForExploringLine()
        }
    }

    /**
     * The moves that led to [fen] on the displayed line, so Stockfish can see repetitions
     * (a FEN alone can't). Null when they aren't known; the engine then gets the FEN only.
     */
    fun historyFor(fen: String): EngineHistory? {
        val state = getUiState()
        val main = getBoardHistory().let { synchronized(it) { it.toList() } }
        val boards = if (state.isExploringLine) {
            val branch = getExploringLineHistory().let { synchronized(it) { it.toList() } }
            main.take(state.savedGameMoveIndex + 2) + branch.drop(1).take(state.exploringLineMoveIndex + 1)
        } else {
            main.take(state.currentMoveIndex + 2)
        }
        if (boards.lastOrNull()?.getFen() != fen) return null
        return EngineHistory.fromBoards(boards)
    }

    fun configureForPreviewStage() {
        val settings = getUiState().stockfishSettings.previewStage
        stockfish.configure(settings.threads, settings.hashMb, 1, settings.useNnue)
    }

    fun configureForAnalyseStage() {
        val settings = getUiState().stockfishSettings.analyseStage
        stockfish.configure(settings.threads, settings.hashMb, 1, settings.useNnue)
    }

    fun configureForManualStage() {
        val settings = getUiState().stockfishSettings.manualStage
        stockfish.configure(settings.threads, settings.hashMb, settings.multiPv, settings.useNnue)
    }

    /**
     * Build the list of move indices for analysis based on the current stage.
     * Preview stage: Forward sequence (move 1 to end)
     * Analyse stage: Backwards sequence (end to move 1)
     */
    private fun buildMoveIndices(): List<Int> {
        val state = getUiState()
        val moves = state.moves
        return when (state.currentStage) {
            AnalysisStage.PREVIEW -> (0 until moves.size).toList()
            AnalysisStage.ANALYSE -> (moves.size - 1 downTo 0).toList()
            AnalysisStage.MANUAL -> emptyList()
        }
    }

    /**
     * Start the three-stage analysis flow: Preview → Analyse → Manual.
     */
    fun startAnalysis() {
        autoAnalysisJob?.cancel()

        autoAnalysisJob = viewModelScope.launch {
            try {
                // A game selected during engine startup must not remain stuck
                // in Preview. Keep this request queued until initialization ends.
                if (!stockfish.isReady.value) {
                    val ready = withTimeoutOrNull(StockfishEngine.STARTUP_TIMEOUT_MS) {
                        stockfish.isReady.first { it }
                    } ?: stockfish.restart()
                    updateUiState { copy(stockfishReady = ready) }
                    if (!ready) {
                        updateUiState { copy(errorMessage = "Stockfish could not start analysis") }
                        return@launch
                    }
                }
                val moves = getUiState().moves
                if (moves.isEmpty()) {
                    android.util.Log.e("Analysis", "EXIT: moves list is empty")
                    enterManualStageInternal(-1)
                    return@launch
                }

                val boardHistory = getBoardHistory()
                val expectedBoardHistorySize = boardHistory.size
                if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "START: moves=${moves.size}, boardHistory=$expectedBoardHistorySize")

                // ===== PREVIEW STAGE =====
                if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "Starting PREVIEW stage")

                updateUiState {
                    copy(
                        currentStage = AnalysisStage.PREVIEW,
                        previewScores = emptyMap(),
                        analyseScores = emptyMap(),
                        autoAnalysisCurrentScore = null
                    )
                }

                stockfish.stop()
                var ready = stockfish.restart()
                updateUiState { copy(stockfishReady = ready) }
                if (!ready) {
                    android.util.Log.e("Analysis", "Failed to start Stockfish for Preview stage")
                    enterManualStageInternal(-1)
                    return@launch
                }

                stockfish.newGame()
                configureForPreviewStage()
                delay(50)

                val previewTimeMs = (getUiState().stockfishSettings.previewStage.secondsForMove * 1000).toInt()
                val previewComplete = runStageAnalysis(
                    stageName = "PREVIEW",
                    timePerMoveMs = previewTimeMs,
                    expectedBoardHistorySize = expectedBoardHistorySize,
                    storeScore = { moveIndex, score ->
                        updateUiState {
                            copy(
                                previewScores = previewScores + (moveIndex to score),
                                autoAnalysisCurrentScore = score
                            )
                        }
                    },
                    configureEngine = { configureForPreviewStage() }
                )

                if (!previewComplete) {
                    if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "Preview stage was interrupted or failed")
                    return@launch
                }

                // ===== ANALYSE STAGE =====
                if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "Starting ANALYSE stage")

                updateUiState {
                    copy(
                        currentStage = AnalysisStage.ANALYSE,
                        autoAnalysisCurrentScore = null
                    )
                }

                stockfish.stop()
                ready = stockfish.restart()
                updateUiState { copy(stockfishReady = ready) }
                if (!ready) {
                    android.util.Log.e("Analysis", "Failed to start Stockfish for Analyse stage")
                    enterManualStageInternal(findBiggestScoreChangeMove())
                    return@launch
                }

                stockfish.newGame()
                configureForAnalyseStage()
                delay(50)

                val analyseTimeMs = (getUiState().stockfishSettings.analyseStage.secondsForMove * 1000).toInt()
                val analyseComplete = runStageAnalysis(
                    stageName = "ANALYSE",
                    timePerMoveMs = analyseTimeMs,
                    expectedBoardHistorySize = expectedBoardHistorySize,
                    storeScore = { moveIndex, score ->
                        updateUiState {
                            copy(
                                analyseScores = analyseScores + (moveIndex to score),
                                autoAnalysisCurrentScore = score
                            )
                        }
                    },
                    configureEngine = { configureForAnalyseStage() }
                )

                if (!analyseComplete) {
                    if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "Analyse stage was interrupted")
                    return@launch
                }

                // ===== MANUAL STAGE =====
                if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "Analysis complete, entering MANUAL stage")

                val biggestChangeMoveIndex = findBiggestScoreChangeMove()
                enterManualStageInternal(biggestChangeMoveIndex)

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Analysis", "Error during analysis: ${e.message}")
                updateUiState {
                    copy(
                        currentStage = AnalysisStage.MANUAL,
                        autoAnalysisIndex = -1
                    )
                }
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
        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "$stageName: analyzing ${moveIndices.size} moves, time=${timePerMoveMs}ms")

        val remainingMoves = moveIndices.toMutableList()
        var analyzedCount = 0
        val boardHistory = getBoardHistory()

        for (moveIndex in moveIndices) {
            yield()
            // Wait while Eval is in the background; the current move's search has finished.
            foreground.first { it }

            if (boardHistory.size != expectedBoardHistorySize) {
                android.util.Log.e("Analysis", "$stageName EXIT: boardHistory changed")
                return false
            }

            val board = boardHistory.getOrNull(moveIndex + 1) ?: continue

            remainingMoves.remove(moveIndex)

            updateUiState {
                copy(
                    autoAnalysisIndex = moveIndex,
                    currentBoard = board,
                    currentMoveIndex = moveIndex,
                    autoAnalysisCurrentScore = null,
                    analysisResult = null
                )
            }

            val fen = board.getFen()
            // Lets the live main-line card follow this search (it matches results by FEN).
            currentAnalysisFen = fen

            stockfish.analyzeWithTime(fen, timePerMoveMs, EngineHistory.fromBoards(boardHistory.take(moveIndex + 2)))

            val completed = stockfish.waitForCompletion(timePerMoveMs.toLong() + StockfishEngine.READY_TIMEOUT_MS + 2000)
            if (!completed) {
                stockfish.stop()
                delay(100)
            }

            if (!stockfish.isReady.value) {
                android.util.Log.w("Analysis", "$stageName: Engine died at move $moveIndex, restarting...")
                val restarted = stockfish.restart()
                if (restarted) {
                    stockfish.newGame()
                    configureEngine()
                    delay(100)

                    stockfish.analyzeWithTime(fen, timePerMoveMs, EngineHistory.fromBoards(boardHistory.take(moveIndex + 2)))
                    val retryCompleted = stockfish.waitForCompletion(timePerMoveMs.toLong() + StockfishEngine.READY_TIMEOUT_MS + 2000)
                    if (!retryCompleted) {
                        stockfish.stop()
                        delay(100)
                    }
                } else {
                    android.util.Log.e("Analysis", "$stageName: Failed to restart engine")
                    return false
                }
            }

            yield()

            if (boardHistory.size != expectedBoardHistorySize) {
                android.util.Log.e("Analysis", "$stageName EXIT after wait: boardHistory changed")
                return false
            }

            val result = stockfish.analysisResult.value
            if (result != null && result.fen == board.getFen()) {
                val bestLine = result.bestLine
                if (bestLine != null) {
                    analyzedCount++
                    val isWhiteToMove = board.getTurn() == PieceColor.WHITE
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

        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("Analysis", "$stageName completed: analyzed=$analyzedCount out of ${moveIndices.size} moves")
        return true
    }

    /**
     * Merge preview and analyse scores: analyse scores override preview scores where available.
     */
    private fun getMergedScores(): Map<Int, MoveScore> {
        val state = getUiState()
        return if (state.analyseScores.isNotEmpty()) {
            state.previewScores + state.analyseScores
        } else {
            state.previewScores
        }
    }

    /**
     * Find the move index with the biggest score change compared to the previous move.
     */
    fun findBiggestScoreChangeMove(): Int {
        val scores = getMergedScores()
        if (scores.size < 2) return 0

        var maxChange = 0f
        var maxChangeIndex = 0

        val sortedIndices = scores.keys.sorted()
        for (i in 1 until sortedIndices.size) {
            val currentIndex = sortedIndices[i]
            val prevIndex = sortedIndices[i - 1]
            val currentScore = scores[currentIndex] ?: continue
            val prevScore = scores[prevIndex] ?: continue

            val change = kotlin.math.abs(
                MoveQualityThresholds.winningChances(currentScore) - MoveQualityThresholds.winningChances(prevScore)
            )
            if (change > maxChange) {
                maxChange = change
                maxChangeIndex = currentIndex
            }
        }

        return maxChangeIndex
    }

    /**
     * Calculate move qualities based on evaluation changes.
     */
    fun calculateMoveQualities(scores: Map<Int, MoveScore> = getMergedScores()): Map<Int, MoveQuality> {
        val qualities = mutableMapOf<Int, MoveQuality>()
        for (moveIndex in scores.keys) {
            val current = scores[moveIndex] ?: continue
            if (moveIndex == 0) {
                qualities[moveIndex] = MoveQuality.NORMAL
                continue
            }
            // Compare the positions immediately before and after this move.
            // Skipping the opponent's move incorrectly attributes its swing to this player.
            val previous = scores[moveIndex - 1] ?: continue
            qualities[moveIndex] = moveQuality(moveIndex, current, previous)
        }
        return qualities
    }

    /**
     * Move qualities that only compare scores from the same stage: a 2-second score next to a
     * 50 ms one differs mostly by search depth. [analyseScores] may already be filled with
     * preview scores (as stored games are); entries identical to the preview score count as preview.
     */
    fun calculateMoveQualities(previewScores: Map<Int, MoveScore>, analyseScores: Map<Int, MoveScore>): Map<Int, MoveQuality> {
        val deep = analyseScores.filter { (index, score) -> previewScores[index] != score }
        val qualities = mutableMapOf<Int, MoveQuality>()
        for (moveIndex in (previewScores.keys + deep.keys)) {
            if (moveIndex == 0) {
                qualities[moveIndex] = MoveQuality.NORMAL
                continue
            }
            val pair = listOf(deep, previewScores).firstNotNullOfOrNull { stage ->
                val current = stage[moveIndex]
                val previous = stage[moveIndex - 1]
                if (current != null && previous != null) current to previous else null
            } ?: continue
            qualities[moveIndex] = moveQuality(moveIndex, pair.first, pair.second)
        }
        return qualities
    }

    private fun moveQuality(moveIndex: Int, current: MoveScore, previous: MoveScore): MoveQuality {
        // Scores are from WHITE's perspective; flip them to the mover's.
        val isWhiteMove = getBoardHistory().getOrNull(moveIndex)?.getTurn() == PieceColor.WHITE
        val sign = if (isWhiteMove) 1f else -1f
        val before = sign * MoveQualityThresholds.winningChances(previous)
        val change = sign * MoveQualityThresholds.winningChances(current) - before
        return when {
            change <= -MoveQualityThresholds.BLUNDER -> MoveQuality.BLUNDER
            change <= -MoveQualityThresholds.MISTAKE -> MoveQuality.MISTAKE
            change <= -MoveQualityThresholds.DUBIOUS -> MoveQuality.DUBIOUS
            // Converting an already winning position (for example into a found mate) is not brilliant.
            before >= 0.5f && change >= MoveQualityThresholds.GOOD -> MoveQuality.GOOD
            change >= MoveQualityThresholds.BRILLIANT -> MoveQuality.BRILLIANT
            change >= MoveQualityThresholds.GOOD -> MoveQuality.GOOD
            else -> MoveQuality.NORMAL
        }
    }

    /**
     * Internal function to enter Manual stage at a specific move.
     */
    fun enterManualStageInternal(moveIndex: Int) {
        // Fill missing analyse scores from preview scores so the result is
        // identical regardless of whether the analyse stage ran to completion
        // or was interrupted early by the user.
        val state = getUiState()
        val filledAnalyseScores = state.previewScores + state.analyseScores

        val moveQualities = calculateMoveQualities(state.previewScores, state.analyseScores)
        val boardHistory = getBoardHistory()

        val previousJob = manualAnalysisJob
        manualAnalysisJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            autoAnalysisJob?.cancel()
            stockfish.stop()

            val validIndex = moveIndex.coerceIn(-1, boardHistory.size - 2)
            val board = boardHistory.getOrNull(validIndex + 1) ?: ChessBoard()

            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze
            val thisRequestId = analysisRequestId.incrementAndGet()

            updateUiState {
                copy(
                    currentStage = AnalysisStage.MANUAL,
                    autoAnalysisIndex = -1,
                    currentMoveIndex = validIndex,
                    currentBoard = board.copy(),
                    autoAnalysisCurrentScore = null,
                    stockfishReady = false,
                    analysisResult = null,
                    analysisResultFen = null,
                    moveQualities = moveQualities,
                    analyseScores = filledAnalyseScores
                )
            }

            // Save the game for auto-restore on next startup
            val updatedState = getUiState()
            val game = updatedState.game
            if (game != null) {
                val analysedGame = AnalysedGame(
                    timestamp = System.currentTimeMillis(),
                    whiteName = game.players.white.user?.name ?: "White",
                    blackName = game.players.black.user?.name ?: "Black",
                    result = gameResultToken(game),
                    pgn = game.pgn ?: "",
                    moves = updatedState.moves,
                    moveDetails = updatedState.moveDetails,
                    previewScores = updatedState.previewScores,
                    analyseScores = filledAnalyseScores,
                    openingName = updatedState.openingName,
                    speed = game.speed
                )
                // Multi-megabyte JSON: serialise and write off the main thread.
                withContext(Dispatchers.IO) {
                    saveManualGame(analysedGame)
                    storeManualGameToList(analysedGame)
                }
            }

            val ready = stockfish.restart()
            updateUiState { copy(stockfishReady = ready) }

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
     */
    fun enterManualStageAtCurrentPosition() {
        val currentIndex = getUiState().currentMoveIndex
        enterManualStageInternal(currentIndex)
    }

    /**
     * Enter Manual stage at the move with the biggest score change.
     */
    fun enterManualStageAtBiggestChange() {
        if (getUiState().currentStage != AnalysisStage.ANALYSE) return
        val biggestChangeMoveIndex = findBiggestScoreChangeMove()
        enterManualStageInternal(biggestChangeMoveIndex)
    }

    /**
     * Enter Manual stage at a specific move index.
     */
    fun enterManualStageAtMove(moveIndex: Int) {
        if (getUiState().currentStage == AnalysisStage.PREVIEW) return
        enterManualStageInternal(moveIndex)
    }

    /**
     * Ensure Stockfish analysis is running and producing results in manual stage.
     */
    suspend fun ensureStockfishAnalysis(fen: String, requestId: Long) {
        val maxRetries = 2
        var attempt = 0

        while (attempt < maxRetries) {
            if (analysisRequestId.get() != requestId || getUiState().currentStage != AnalysisStage.MANUAL) return
            if (!stockfish.isReady.value) {
                val ready = stockfish.restart()
                updateUiState { copy(stockfishReady = ready) }
                if (!ready) {
                    attempt++
                    continue
                }
                configureForManualStage()
            }

            if (analysisRequestId.get() != requestId || getUiState().currentStage != AnalysisStage.MANUAL) return
            val depth = getUiState().stockfishSettings.manualStage.depth
            stockfish.analyze(fen, depth, historyFor(fen))

            val maxWaitTime = StockfishEngine.READY_TIMEOUT_MS + 2000
            val firstResult = withTimeoutOrNull(maxWaitTime) {
                stockfish.analysisResult.first { it != null && it.fen == fen }
            }
            if (firstResult != null) {
                // Follow the search by collecting engine updates (no polling). This suspends
                // quietly once the search has finished, and ends when the request or stage
                // changes or the engine dies; only a dead engine is restarted.
                combine(stockfish.analysisResult, stockfish.isReady) { result, ready -> result to ready }
                    .takeWhile { (_, ready) ->
                        ready && analysisRequestId.get() == requestId && getUiState().currentStage == AnalysisStage.MANUAL
                    }
                    .collect { (result, _) ->
                        if (result != null && result.fen == fen) {
                            updateUiState { copy(analysisResult = result, analysisResultFen = fen) }
                        }
                    }
                if (analysisRequestId.get() != requestId || getUiState().currentStage != AnalysisStage.MANUAL) return
                android.util.Log.w("Analysis", "Stockfish stopped during analysis, restarting (attempt ${attempt + 1})")
            }

            // Stockfish refused this position (it exits on illegal ones): retrying can't help.
            stockfish.lastError.value?.takeIf { it.fen == fen }?.let { error ->
                updateUiState { copy(errorMessage = error.message) }
                return
            }
            if (firstResult == null) {
                android.util.Log.w("Analysis", "No Stockfish results after ${maxWaitTime}ms, restarting (attempt ${attempt + 1})")
            }

            stockfish.stop()
            updateUiState { copy(stockfishReady = false) }

            val ready = stockfish.restart()
            updateUiState { copy(stockfishReady = ready) }

            if (ready) {
                configureForManualStage()
            }

            attempt++
        }
    }

    /**
     * Analyze a specific board position.
     */
    fun analyzePosition(board: ChessBoard) {
        if (!getUiState().analysisEnabled) return

        manualAnalysisJob?.cancel()

        val thisRequestId = analysisRequestId.incrementAndGet()

        val fenToAnalyze = board.getFen()
        currentAnalysisFen = fenToAnalyze
        updateUiState { copy(analysisResult = null, analysisResultFen = null) }

        if (getUiState().currentStage != AnalysisStage.MANUAL) {
            return
        }

        manualAnalysisJob = viewModelScope.launch {
            ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
        }
    }

    /**
     * Restart Stockfish analysis for exploring line moves.
     */
    fun restartAnalysisForExploringLine() {
        val previousJob = manualAnalysisJob
        val thisRequestId = analysisRequestId.incrementAndGet()
        val fenToAnalyze = getUiState().currentBoard.getFen()
        currentAnalysisFen = fenToAnalyze
        updateUiState { copy(analysisResult = null, analysisResultFen = null) }
        manualAnalysisJob = viewModelScope.launch {
            // Wait for the previous analysis coroutine to unwind before entering
            // analysisMutex so the two don't interleave stop/newGame commands.
            previousJob?.cancelAndJoin()
            analysisMutex.withLock {
                stockfish.stop()

                delay(50)

                stockfish.newGame()
                delay(50)

                if (getUiState().currentStage == AnalysisStage.MANUAL) {
                    ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
                }
            }
        }
    }

    /**
     * Restart analysis at a specific move.
     */
    fun restartAnalysisAtMove(moveIndex: Int) {
        val previousJob = manualAnalysisJob

        val boardHistory = getBoardHistory()
        if (boardHistory.isEmpty()) return
        val validIndex = moveIndex.coerceIn(-1, boardHistory.size - 2)
        val board = boardHistory[validIndex + 1].copy()
        val fenToAnalyze = board.getFen()
        val thisRequestId = analysisRequestId.incrementAndGet()
        currentAnalysisFen = fenToAnalyze

        // Navigation is immediate even while the previous engine search unwinds.
        // Otherwise consecutive taps all read the same old move index.
        updateUiState {
            copy(
                currentMoveIndex = validIndex,
                currentBoard = board,
                analysisResult = null,
                analysisResultFen = null
            )
        }

        manualAnalysisJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            analysisMutex.withLock {
                stockfish.stop()

                delay(50)

                stockfish.newGame()
                delay(50)

                if (getUiState().currentStage == AnalysisStage.MANUAL) {
                    ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
                }
            }
        }
    }

    fun stop() {
        analysisRequestId.incrementAndGet()
        currentAnalysisFen = null
        autoAnalysisJob?.cancel()
        manualAnalysisJob?.cancel()
        stockfish.stop()
    }
}
