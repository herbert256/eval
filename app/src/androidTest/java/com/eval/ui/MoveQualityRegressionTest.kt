package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoveQualityRegressionTest {
    private fun qualities(values: Map<Int, Float>, initial: ChessBoard = ChessBoard(), moves: List<String> = listOf("e4", "e5", "Nf3", "Nc6")): Map<Int, MoveQuality> {
        val history = BoardHistoryBuilder.build(moves, initial).boards.toMutableList()
        val state = GameUiState()
        val scope = CoroutineScope(Job().apply { cancel() })
        val orchestrator = AnalysisOrchestrator(StockfishEngine(ApplicationProvider.getApplicationContext<Context>()),
            { state }, {}, scope, { history })
        return orchestrator.calculateMoveQualities(values.mapValues { MoveScore(it.value, false, 0) })
    }

    private fun scored(scores: Map<Int, MoveScore>): Map<Int, MoveQuality> {
        val history = BoardHistoryBuilder.build(listOf("e4", "e5", "Nf3", "Nc6"), ChessBoard()).boards.toMutableList()
        val orchestrator = AnalysisOrchestrator(StockfishEngine(ApplicationProvider.getApplicationContext<Context>()),
            { GameUiState() }, {}, CoroutineScope(Job().apply { cancel() }), { history })
        return orchestrator.calculateMoveQualities(scores)
    }

    @Test fun keeping_a_crushing_advantage_instead_of_a_mate_is_not_a_blunder() {
        val mateIn4 = MoveScore(100f, true, 4)
        // Black to move after 1.e4 (index 1 is Black's move; index 2 is White's).
        val result = scored(mapOf(1 to mateIn4, 2 to MoveScore(9.5f, false, 0)))
        assertNotEquals(MoveQuality.BLUNDER, result[2])
        assertNotEquals(MoveQuality.MISTAKE, result[2])
    }

    @Test fun finding_a_mate_from_an_already_winning_position_is_not_brilliant() {
        val result = scored(mapOf(1 to MoveScore(3f, false, 0), 2 to MoveScore(100f, true, 3)))
        assertNotEquals(MoveQuality.BRILLIANT, result[2])
    }

    @Test fun deep_and_preview_scores_are_not_compared_with_each_other() {
        val history = BoardHistoryBuilder.build(listOf("e4", "e5", "Nf3", "Nc6"), ChessBoard()).boards.toMutableList()
        val orchestrator = AnalysisOrchestrator(StockfishEngine(ApplicationProvider.getApplicationContext<Context>()),
            { GameUiState() }, {}, CoroutineScope(Job().apply { cancel() }), { history })
        val preview = mapOf(0 to MoveScore(0.2f, false, 0), 1 to MoveScore(0.3f, false, 0), 2 to MoveScore(0.2f, false, 0))
        // Only move 2 has a deep score, which differs from the 50 ms estimate by depth noise.
        val analyse = preview + (2 to MoveScore(-0.9f, false, 0, depth = 20))
        val result = orchestrator.calculateMoveQualities(preview, analyse)
        assertEquals(MoveQuality.NORMAL, result[2])
    }

    @Test fun returning_an_opponents_blunder_is_still_a_blunder() {
        val result = qualities(mapOf(0 to 0f, 1 to 5f, 2 to 0f))
        assertEquals("Black lost five pawns on its first move", MoveQuality.BLUNDER, result[1])
        assertEquals("White gave back the five-pawn advantage", MoveQuality.BLUNDER, result[2])
    }

    @Test fun keeping_an_advantage_is_not_credited_with_the_opponents_previous_mistake() {
        val result = qualities(mapOf(0 to 0f, 1 to 5f, 2 to 5f, 3 to 5f))
        assertEquals(MoveQuality.NORMAL, result[2])
        assertEquals(MoveQuality.NORMAL, result[3])
    }

    @Test fun missing_previous_position_scores_do_not_attribute_a_two_ply_swing() {
        assertNull(qualities(mapOf(0 to 0f, 2 to -5f))[2])
    }

    @Test fun a_black_to_move_fen_uses_the_actual_mover_for_classification() {
        val board = ChessBoard().apply { check(setFen("4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17")) }
        val result = qualities(mapOf(0 to 0f, 1 to -4f, 2 to 0f), board, listOf("e5", "e4", "Ke7"))
        assertEquals(MoveQuality.BLUNDER, result[1])
        assertEquals(MoveQuality.BLUNDER, result[2])
    }
}
