package com.eval.ui

import com.eval.chess.ChessBoard
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.PvLine
import org.junit.Assert.*
import org.junit.Test

class AiEngineLinesTest {
    @Test fun lines_include_legal_continuations_scores_depth_and_keep_engine_ranking() {
        val fen = ChessBoard().getFen()
        val result = AnalysisResult(12, 100, 1000, listOf(
            PvLine(0.25f, false, 0, "e2e4 e7e5 g1f3", 1),
            PvLine(0.15f, false, 0, "d2d4 d7d5", 2)
        ), fen)
        val text = formatAiEngineLines(fen, result)
        assertTrue(text.startsWith("Top 2 Stockfish lines, best first for White to move."))
        assertTrue(text.contains("1. +0.25 (depth 12): e4 e5 Nf3 [UCI: e2e4 e7e5 g1f3]"))
        assertTrue(text.contains("2. +0.15 (depth 12): d4 d5"))
    }

    @Test fun black_lines_keep_black_best_first_and_mate_is_scored_from_white_perspective() {
        val fen = "8/8/8/8/8/5kq1/8/7K b - - 0 1"
        val result = AnalysisResult(4, 1, 1, listOf(PvLine(100f, true, 1, "g3g2", 1)), fen)
        val text = formatAiEngineLines(fen, result)
        assertTrue(text.startsWith("Top 1 Stockfish lines, best first for Black to move."))
        assertTrue(text.contains("1. -M1 (depth 4): Qg2#"))
    }

    @Test(expected = IllegalStateException::class) fun invalid_continuation_is_not_sent() {
        formatAiEngineLines(ChessBoard().getFen(), AnalysisResult(3, 1, 1,
            listOf(PvLine(0f, false, 0, "e2e4 a1a8", 1))))
    }
}
