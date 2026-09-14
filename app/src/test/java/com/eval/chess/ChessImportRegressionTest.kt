package com.eval.chess

import com.eval.ui.BoardHistoryBuilder
import org.junit.Assert.*
import org.junit.Test

class ChessImportRegressionTest {
    @Test fun uci_moves_cannot_be_misread_as_pawn_san() {
        val board = ChessBoard()
        assertFalse(board.makeMove("g1f3"))
        assertTrue(board.makeUciMove("g1f3"))
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.WHITE), board.getPiece(5, 2))
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), board.getPiece(5, 1))
    }

    @Test fun illegal_imports_leave_the_position_unchanged() {
        for (move in listOf("e2e5", "e7e5", "e1g1", "e2e4q", "e2e4junk", "e2e4z")) {
            val board = ChessBoard()
            val initial = board.getFen()
            assertFalse(move, board.makeUciMove(move))
            assertEquals(initial, board.getFen())
        }
        val board = ChessBoard()
        assertFalse(board.makeMove("O-O"))
        assertFalse(board.makeMove("g1f3"))
        assertFalse(board.makeMove("a3b4"))
        assertEquals(ChessBoard().getFen(), board.getFen())
    }

    @Test fun san_chooses_the_legal_knight_when_the_other_is_pinned() {
        val board = ChessBoard()
        assertTrue(board.setFen("k3r3/8/8/8/8/8/4N1N1/4K3 w - - 0 1"))
        assertTrue(board.makeMove("Nf4"))
        assertNotNull(board.getPiece(4, 1))
        assertNull(board.getPiece(6, 1))
        assertEquals(Square(6, 1), board.getLastMove()?.from)
    }

    @Test fun san_disambiguation_is_respected_even_with_only_one_candidate() {
        val board = ChessBoard()
        assertFalse(board.makeMove("Naf3"))
        assertTrue(board.makeMove("Ngf3"))
    }

    @Test fun comments_and_nested_variations_never_become_moves_or_clocks() {
        val pgn = """
            [Event "Annotation regression"]

            {Try d4 first} 1. e4! {[%eval 0.2]} {[%clk 0:04:59]}
            (1. d4 {[%clk 0:00:01] c5} (1... Nf6)) e5 ; consider Nc6
            2. Nf3 Nc6 *
        """.trimIndent()
        val moves = PgnParser.parseMovesWithClock(pgn)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), moves.map { it.san })
        assertEquals("0:04:59", moves.first().clockTime)
        assertEquals(5, BoardHistoryBuilder.build(moves.map { it.san }).boards.size)
    }

    @Test fun uci_comments_and_attached_move_numbers_are_supported() {
        val moves = PgnParser.parseMovesWithClock("1.e2e4 {[%clk 0:01:00]} 1...e7e5 2.g1f3 *")
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), moves.map { it.san })
        assertEquals("0:01:00", moves.first().clockTime)
    }

    @Test fun castling_check_and_zero_notation_are_preserved() {
        assertEquals(listOf("O-O+", "0-0-0#"), PgnParser.parseMoves("1. O-O+ 0-0-0# *"))
    }

    @Test fun malformed_movetext_stops_import_instead_of_silently_skipping_it() {
        val result = BoardHistoryBuilder.build(PgnParser.parseMoves("1. e4 e5 2. nonsense Nc6 *"))
        assertEquals(listOf("e4", "e5"), result.validMoves)
        assertEquals("nonsense", result.failedMove)
    }

    @Test fun utf8_bom_and_nags_do_not_hide_moves() {
        assertEquals(listOf("e4", "e5", "Nf3"), PgnParser.parseMoves("\uFEFF[Event \"Test\"]\r\n\r\n1.e4$1 e5$2 2.Nf3!? *"))
    }

    @Test fun normal_castling_en_passant_and_underpromotion_remain_legal() {
        val castle = ChessBoard()
        assertTrue(castle.setFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        assertTrue(castle.makeMove("O-O"))
        assertTrue(castle.makeUciMove("e8c8"))
        assertEquals(Piece(PieceType.ROOK, PieceColor.BLACK), castle.getPiece(3, 7))
        val ep = ChessBoard()
        assertTrue(ep.setFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"))
        assertTrue(ep.makeMove("exd6"))
        assertNull(ep.getPiece(3, 4))
        val promotion = ChessBoard()
        assertTrue(promotion.setFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1"))
        assertTrue(promotion.makeUciMove("a7a8n"))
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.WHITE), promotion.getPiece(0, 7))
    }
}
