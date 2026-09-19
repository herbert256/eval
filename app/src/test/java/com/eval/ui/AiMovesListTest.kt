package com.eval.ui

import com.eval.chess.ChessBoard
import com.eval.stockfish.PvLine
import org.junit.Assert.*
import org.junit.Test

class AiMovesListTest {
    private fun moves(fen: String) = legalAiMoves(ChessBoard().apply { assertTrue(setFen(fen)) })

    @Test fun starting_position_has_all_twenty_moves_without_changing_board() {
        val board = ChessBoard()
        val fen = board.getFen()
        val result = legalAiMoves(board)
        assertEquals(20, result.size)
        assertEquals(20, result.map { it.uci }.toSet().size)
        assertTrue(AiLegalMove("e2e4", "e4") in result)
        assertEquals(fen, board.getFen())
    }

    @Test fun includes_all_promotions_castling_and_legal_en_passant_but_not_pinned_moves() {
        val promotions = moves("7k/P7/8/8/8/8/8/7K w - - 0 1")
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"),
            promotions.filter { it.uci.startsWith("a7") }.map { it.uci }.toSet())
        assertTrue(promotions.any { it.san == "a8=N" })
        val castling = moves("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertTrue(AiLegalMove("e1g1", "O-O") in castling)
        assertTrue(AiLegalMove("e1c1", "O-O-O") in castling)
        assertTrue(moves("7k/8/8/3pP3/8/8/8/7K w - d6 0 1").any { it.uci == "e5d6" })
        assertFalse(moves("4r2k/8/8/3pP3/8/8/8/4K3 w - d6 0 1").any { it.uci == "e5d6" })
        assertFalse(moves("4r2k/8/8/8/8/8/4R3/4K3 w - - 0 1").any { it.uci == "e2d2" })
    }

    @Test fun terminal_positions_have_no_moves() {
        assertTrue(moves("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1").isEmpty())
        assertTrue(moves("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").isEmpty())
    }

    @Test fun scores_and_mate_signs_always_use_white_perspective() {
        fun line(score: Float = 0f, mate: Int? = null) = PvLine(score, mate != null, mate ?: 0, "e2e4", 1)
        assertEquals("+1.25", aiMoveScore(line(1.25f), true))
        assertEquals("-1.25", aiMoveScore(line(1.25f), false))
        assertEquals("+0.00", aiMoveScore(line(), false))
        assertEquals("+M2", aiMoveScore(line(mate = 2), true))
        assertEquals("-M2", aiMoveScore(line(mate = 2), false))
        assertEquals("+M3", aiMoveScore(line(mate = -3), false))
        assertEquals("-M3", aiMoveScore(line(mate = -3), true))
    }
}
