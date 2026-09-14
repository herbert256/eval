package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.audio.MoveSoundPlayer
import com.eval.chess.ChessBoard
import com.eval.chess.Square
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardExplorationRegressionTest {
    private class Harness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Main)
        val sounds = MoveSoundPlayer(context)
        val history = BoardHistoryBuilder.build(listOf("d4", "d5")).boards.toMutableList()
        val exploration = mutableListOf<ChessBoard>()
        var state = GameUiState(currentStage = AnalysisStage.MANUAL, moves = listOf("d4", "d5"),
            currentBoard = history.first(), currentMoveIndex = -1)
        val orchestrator = AnalysisOrchestrator(StockfishEngine(context), { state }, { state = it(state) }, scope, { history })
        val navigation = BoardNavigationManager({ state }, { state = it(state) }, { history }, { exploration }, orchestrator, sounds)
        fun close() { orchestrator.stop(); sounds.release() }
    }

    @Test fun selecting_a_continuation_inside_a_variation_keeps_the_played_prefix() {
        val h = Harness()
        try {
            h.navigation.exploreLine("e2e4 e7e5 g1f3", 1)
            h.navigation.exploreLine("b1c3 b8c6", 0)
            assertEquals(listOf("e2e4", "e7e5", "b1c3", "b8c6"), h.state.exploringLineMoves)
            assertEquals(2, h.state.exploringLineMoveIndex)
            val selectedFen = h.state.currentBoard.getFen()
            h.navigation.goToStart()
            assertEquals("Variation start changed", h.history.first().getFen(), h.state.currentBoard.getFen())
            h.navigation.goToMove(2)
            assertEquals(selectedFen, h.state.currentBoard.getFen())
            h.navigation.backToOriginalGame()
            assertEquals(h.history.first().getFen(), h.state.currentBoard.getFen())
            assertEquals(listOf("d4", "d5"), h.state.moves)
        } finally { h.close() }
    }

    @Test fun an_invalid_continuation_leaves_the_existing_variation_untouched() {
        val h = Harness()
        try {
            h.navigation.exploreLine("e2e4 e7e5", 0)
            val previous = h.state
            val history = h.exploration.map { it.getFen() }
            h.navigation.exploreLine("e2e4") // stale line: that pawn is already on e4
            assertEquals(previous, h.state)
            assertEquals(history, h.exploration.map { it.getFen() })
        } finally { h.close() }
    }

    @Test fun moving_into_a_variation_clears_the_old_evaluation_before_engine_work_runs() {
        val h = Harness()
        try {
            h.state = h.state.copy(
                analysisResult = com.eval.stockfish.AnalysisResult(20, 1, 1, emptyList(), h.state.currentBoard.getFen()),
                analysisResultFen = h.state.currentBoard.getFen())
            h.navigation.makeManualMove(Square(4, 1), Square(4, 3))
            assertNull(h.state.analysisResult)
            assertNull(h.state.analysisResultFen)
        } finally { h.close() }
    }

    @Test fun rapid_main_game_navigation_updates_before_engine_work_runs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Main)
        val sounds = MoveSoundPlayer(context)
        val moves = listOf("e4", "e5", "Nf3", "Nc6")
        val history = BoardHistoryBuilder.build(moves).boards.toMutableList()
        var state = GameUiState(currentStage = AnalysisStage.MANUAL, moves = moves,
            currentBoard = history.first().copy(), currentMoveIndex = -1)
        val orchestrator = AnalysisOrchestrator(StockfishEngine(context), { state }, { state = it(state) }, scope, { history })
        val navigation = BoardNavigationManager({ state }, { state = it(state) }, { history }, { mutableListOf() }, orchestrator, sounds)
        try {
            repeat(3) { navigation.nextMove() }
            assertEquals(2, state.currentMoveIndex)
            assertEquals(history[3].getFen(), state.currentBoard.getFen())
            repeat(2) { navigation.prevMove() }
            assertEquals(0, state.currentMoveIndex)
            navigation.goToEnd()
            navigation.prevMove()
            assertEquals(2, state.currentMoveIndex)
            navigation.goToStart()
            assertEquals(-1, state.currentMoveIndex)
            assertEquals(history[0].getFen(), state.currentBoard.getFen())
        } finally { scope.cancel(); orchestrator.stop(); sounds.release() }
    }

    @Test fun branching_after_rewind_replaces_the_abandoned_future() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A cancelled scope keeps this state-management regression independent of engine timing.
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Main)
        val sounds = MoveSoundPlayer(context)
        var state = GameUiState(currentStage = AnalysisStage.MANUAL)
        val history = mutableListOf(ChessBoard())
        val exploration = mutableListOf<ChessBoard>()
        val engine = StockfishEngine(context)
        val orchestrator = AnalysisOrchestrator(engine, { state }, { state = it(state) }, scope, { history })
        val navigation = BoardNavigationManager({ state }, { state = it(state) }, { history }, { exploration }, orchestrator, sounds)
        try {
            navigation.makeManualMove(Square(4, 1), Square(4, 3)) // e4
            navigation.makeManualMove(Square(4, 6), Square(4, 4)) // e5
            navigation.prevMove()
            navigation.makeManualMove(Square(2, 6), Square(2, 4)) // replace e5 with c5
            val branchFen = state.currentBoard.getFen()
            assertEquals(listOf("e2e4", "c7c5"), state.exploringLineMoves)
            assertEquals(3, exploration.size)
            navigation.goToStart()
            assertEquals(ChessBoard().getFen(), state.currentBoard.getFen())
            navigation.goToEnd()
            assertEquals(branchFen, state.currentBoard.getFen())
            navigation.backToOriginalGame()
            assertEquals(ChessBoard().getFen(), state.currentBoard.getFen())
            assertFalse(state.isExploringLine)
        } finally {
            scope.cancel()
            orchestrator.stop()
            sounds.release()
        }
    }
}
