package com.eval.chess

import org.junit.Assert.*
import org.junit.Test

class FenValidationRegressionTest {
    private fun rejectsWithoutChangingBoard(fen: String) {
        val board = ChessBoard().apply { check(makeUciMove("g1f3")) }
        val previous = board.getFen()
        val previousMove = board.getLastMove()
        assertFalse("Accepted malformed FEN: $fen", board.setFen(fen))
        assertEquals(previous, board.getFen())
        assertEquals(previousMove, board.getLastMove())
    }

    @Test fun invalid_empty_square_counts_are_rejected() {
        for (rank in listOf("80", "08", "44", "٨")) {
            rejectsWithoutChangingBoard("4k3/$rank/8/8/8/8/8/4K3 w - - 0 1")
        }
    }

    @Test fun malformed_castling_and_extra_fields_are_rejected() {
        for (rights in listOf("Kz", "KK", "K-")) {
            rejectsWithoutChangingBoard("r3k2r/8/8/8/8/8/8/R3K2R w $rights - 0 1")
        }
        rejectsWithoutChangingBoard("4k3/8/8/8/8/8/8/4K3 w - - 0 1 ignored")
    }

    @Test fun en_passant_requires_a_real_double_pushed_enemy_pawn() {
        for (fen in listOf(
            "4k3/8/8/4P3/8/8/8/4K3 w - d6 0 2",
            "4k3/8/8/3PP3/8/8/8/4K3 w - d6 0 2",
            "4k3/8/3n4/3pP3/8/8/8/4K3 w - d6 0 2",
            "4k3/3p4/8/3pP3/8/8/8/4K3 w - d6 0 2",
            "4k3/8/8/8/4p3/8/8/4K3 b - d3 0 2"
        )) rejectsWithoutChangingBoard(fen)
    }

    @Test fun valid_en_passant_targets_do_not_require_an_available_capture() {
        val board = ChessBoard()
        assertTrue(board.setFen("4k3/8/8/3p4/8/8/8/4K3 w - d6 0 2"))
        assertTrue(board.setFen("4k3/8/8/8/3P4/8/8/4K3 b - d3 0 2"))
        assertTrue(board.setFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"))
        assertTrue(board.makeMove("exd6"))
        assertNull(board.getPiece(Square(3, 4)))
    }

    @Test fun pinned_en_passant_and_castling_through_check_remain_illegal() {
        val board = ChessBoard()
        assertTrue(board.setFen("k7/8/8/4KPpr/8/8/8/8 w - g6 0 2"))
        assertFalse(board.makeUciMove("f5g6"))
        assertTrue(board.setFen("4kr2/8/8/8/8/8/8/4K2R w K - 0 1"))
        assertFalse(board.makeMove("O-O"))
    }
}
