package com.eval.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.PvLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PositionSharingRegressionTest {
    private fun share(state: GameUiState): String {
        var sent: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) { sent = intent }
        }
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Main)
        ExportShareManager({ state }, {}, scope).sharePositionAsText(context)
        @Suppress("DEPRECATION")
        return sent!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getStringExtra(Intent.EXTRA_TEXT)!!
    }

    private fun analysis(score: Float = 1.25f, mate: Int? = null) =
        AnalysisResult(18, 100, 100, listOf(PvLine(score, mate != null, mate ?: 0, "e7e5 e2e4", 1)))

    @Test fun position_label_uses_the_board_turn_and_fullmove_even_during_exploration() {
        val board = ChessBoard().apply { assertTrue(setFen("4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17")) }
        val state = GameUiState(currentBoard = board, currentMoveIndex = 0, isExploringLine = true)
        assertTrue(share(state).contains("Black to move (move 17)"))
        assertTrue(share(GameUiState()).contains("White to move (move 1)"))
    }

    @Test fun sharing_does_not_attach_evaluation_from_another_position() {
        val board = ChessBoard().apply { assertTrue(makeUciMove("e2e4")) }
        val text = share(GameUiState(currentBoard = board, analysisResult = analysis(), analysisResultFen = ChessBoard().getFen()))
        assertTrue(text.contains(board.getFen()))
        assertFalse(text.contains("Evaluation:"))
        assertFalse(text.contains("Best move:"))
    }

    @Test fun shared_scores_and_mate_winner_have_an_explicit_perspective() {
        val board = ChessBoard().apply { assertTrue(setFen("4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17")) }
        val state = GameUiState(currentBoard = board, analysisResultFen = board.getFen())
        assertTrue(share(state.copy(analysisResult = analysis())).contains("-1.25 (White's perspective; depth 18)"))
        assertTrue(share(state.copy(analysisResult = analysis(mate = 3))).contains("Black mates in 3"))
        assertTrue(share(state.copy(analysisResult = analysis(mate = -3))).contains("White mates in 3"))
    }
}
