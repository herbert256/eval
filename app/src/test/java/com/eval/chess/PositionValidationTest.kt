package com.eval.chess

import org.junit.Assert.*
import org.junit.Test

class PositionValidationTest {
    private fun rejects(fen: String, reason: String) {
        val board = ChessBoard().apply { check(makeUciMove("g1f3")) }
        val previous = board.getFen()
        assertFalse("Accepted illegal FEN: $fen", board.setFen(fen))
        assertEquals("Rejection changed the board", previous, board.getFen())
        val error = ChessBoard.fenValidationError(fen)
        assertTrue("$fen: $error", error!!.contains(reason))
    }

    @Test fun positions_that_crash_or_confuse_the_engine_are_rejected_with_a_reason() {
        rejects("4k2R/8/8/8/8/8/8/4K3 w - - 0 1", "just moved")
        rejects("4k3/4Q3/8/8/8/8/8/4K3 w - - 0 1", "just moved")
        rejects("8/8/8/8/8/8/8/3kK3 w - - 0 1", "next to each other")
        rejects("4k3/8/8/8/P7/PPPPPPPP/8/4K3 w - - 0 1", "8 pawns")
        rejects("4k3/8/8/8/8/NNNNNNNN/NNNNNNNN/4K3 w - - 0 1", "16 pieces")
        rejects("8/8/8/8/8/8/8/8 w - - 0 1", "one white king")
        rejects("4k3/8/8/8/8/8/8/P3K3 w - - 0 1", "first or last rank")
    }

    @Test fun extra_pieces_must_be_paid_for_by_missing_pawns() {
        rejects("QQ2k3/8/8/8/8/PPPPPPPP/8/4K3 w - - 0 1", "White has more promoted pieces")
        rejects("4k3/pppppppp/8/8/8/8/8/1nnnK3 w - - 0 1", "Black has more promoted pieces")
        // a8 and c8 are both light squares: one of those bishops must be promoted.
        rejects("B1B1k3/8/8/8/8/PPPPPPPP/8/4K3 w - - 0 1", "promoted pieces")
        val board = ChessBoard()
        assertTrue(board.setFen("BB2k3/8/8/8/8/PPPPPPP1/8/4K3 w - - 0 1"))
        assertTrue(board.setFen("QQ6/8/8/8/8/PPPPPPP1/8/k3K3 w - - 0 1"))
        assertTrue(board.setFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
    }

    @Test fun stale_castling_rights_are_repaired_instead_of_rejected() {
        val board = ChessBoard()
        assertTrue(board.setFen("4k3/8/8/8/8/8/8/3K3R w K - 0 1"))
        assertEquals("4k3/8/8/8/8/8/8/3K3R w - - 0 1", board.getFen())
        assertFalse(board.copy().makeMove("O-O"))
        assertTrue(board.setFen("5rk1/8/8/8/8/8/8/4K3 b KQkq - 0 1"))
        assertEquals("5rk1/8/8/8/8/8/8/4K3 b - - 0 1", board.getFen())
        assertTrue(board.setFen("r3k2r/8/8/8/8/8/8/R3K1R1 w KQkq - 0 1"))
        assertEquals("r3k2r/8/8/8/8/8/8/R3K1R1 w Qkq - 0 1", board.getFen())
        assertNull(ChessBoard.fenValidationError("4k3/8/8/8/8/8/8/3K3R w K - 0 1"))
    }

    @Test fun side_to_move_may_be_in_check_or_mated() {
        val board = ChessBoard()
        assertTrue(board.setFen("4k2R/8/8/8/8/8/8/4K3 b - - 0 1"))
        assertTrue(board.setFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1"))
    }

    @Test fun syntax_errors_explain_the_problem() {
        assertTrue(ChessBoard.fenValidationError("4k3/8/8/8/8/8/8/4K3 x")!!.contains("side to move"))
        assertTrue(ChessBoard.fenValidationError("4k3/8/8/8/8/8/8")!!.contains("eight ranks"))
        assertTrue(ChessBoard.fenValidationError("4k3/9/8/8/8/8/8/4K3 w - - 0 1")!!.contains("Rank 7"))
        assertTrue(ChessBoard.fenValidationError("4k3/8/8/8/8/8/8/4KX2 w - - 0 1")!!.contains("'X'"))
        assertTrue(ChessBoard.fenValidationError("4k3/8/8/8/8/8/8/4K3 w - e3 0 1")!!.contains("En passant"))
        assertTrue(ChessBoard.fenValidationError("r3k2r/8/8/8/8/8/8/R3K2R w KX - 0 1")!!.contains("Castling"))
        assertNull(ChessBoard.fenValidationError(ChessBoard().getFen()))
    }

    @Test fun board_setup_uses_the_same_rules_and_repairs_stale_rights_on_import() {
        val promoted = requireNotNull(BoardSetupPosition.fromDraftFen("QQ2k3/8/8/8/8/PPPPPPPP/8/4K3 w - - 0 1"))
        assertEquals(ChessBoard.fenValidationError(promoted.toFen()), promoted.validationError())
        val stale = requireNotNull(BoardSetupPosition.fromDraftFen("4k3/8/8/8/8/8/8/3K3R w KQkq - 0 1"))
        assertEquals("", stale.castling)
        assertNull(stale.validationError())
        val kept = requireNotNull(BoardSetupPosition.fromDraftFen("r3k3/8/8/8/8/8/8/4K2R w KQkq - 0 1"))
        assertEquals("Kq", kept.castling)
    }

    @Test fun move_counters_are_bounded_and_never_overflow() {
        val board = ChessBoard()
        assertFalse(board.setFen("4k3/8/8/8/8/8/8/4K3 w - - 0 2147483647"))
        assertFalse(board.setFen("4k3/8/8/8/8/8/8/4K3 w - - 0 ${ChessBoard.MAX_MOVE_COUNTER + 1}"))
        assertTrue(board.setFen("4k3/8/8/8/8/8/8/4K2R b - - 999999 ${ChessBoard.MAX_MOVE_COUNTER}"))
        assertTrue(board.makeMove("Kd7"))
        assertTrue(board.makeMove("Rh2"))
        assertEquals("8/3k4/8/8/8/8/7R/4K3 b - - 1000000 1000000", board.getFen())
        assertTrue(ChessBoard().setFen(board.getFen()))
    }

    @Test fun out_of_range_squares_have_no_piece() {
        val board = ChessBoard()
        assertNull(board.getPiece(Square(8, 0)))
        assertNull(board.getPiece(Square(-1, 3)))
        assertNull(board.getPiece(Square(0, 8)))
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), board.getPiece(Square(0, 0)))
    }
}
