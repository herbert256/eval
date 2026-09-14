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
