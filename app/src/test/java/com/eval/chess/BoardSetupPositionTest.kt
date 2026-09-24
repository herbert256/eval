package com.eval.chess

import org.junit.Assert.*
import org.junit.Test

class BoardSetupPositionTest {
    private fun square(name: String) = requireNotNull(Square.fromAlgebraic(name)).index
    private fun BoardSetupPosition.put(name: String, piece: Char) = place(square(name), piece)

    @Test fun recognized_incomplete_and_illegal_positions_remain_editable() {
        val missingKing = "4k3/8/8/8/8/2N5/8/8 w - - 0 1"
        val draft = requireNotNull(BoardSetupPosition.fromDraftFen(missingKing))
        assertEquals(missingKing, draft.toFen())
        assertNotNull(draft.validationError())
        assertNull(draft.put("e1", 'K').validationError())
        val backRankPawn = requireNotNull(BoardSetupPosition.fromDraftFen("4k3/8/8/8/8/8/8/P3K3 w - - 0 1"))
        assertTrue(backRankPawn.validationError()!!.contains("Pawns"))
        assertNull(backRankPawn.move(square("a1"), square("a2")).validationError())
        val metadata = "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 12"
        assertEquals(metadata, BoardSetupPosition.fromDraftFen(metadata)!!.toFen())
        for (bad in listOf("bad", "9/8/8/8/8/8/8/8", "8/8/8/8/8/8/8/7", "8/8/8/8/8/8/8/8 x - - 0 1")) {
            assertNull(bad, BoardSetupPosition.fromDraftFen(bad))
        }
    }

    @Test fun image_rotation_changes_piece_squares_without_changing_colors_or_turn() {
        val draft = BoardSetupPosition.fromFen("4k3/8/8/8/8/2N5/8/4K3 b - - 0 1")
        val rotated = draft.rotatePieces()
        assertEquals("3K4/8/5N2/8/8/8/8/3k4 b - - 0 1", rotated.toFen())
        assertEquals(draft, rotated.rotatePieces())
        assertEquals("", BoardSetupPosition.initial().rotatePieces().castling)
        assertEquals("-", BoardSetupPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2").rotatePieces().enPassant)
    }

    @Test fun initial_and_imported_positions_keep_all_six_fen_fields() {
        assertEquals(ChessBoard().getFen(), BoardSetupPosition.initial().toFen())
        for (fen in listOf(
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 14 32",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"
        )) {
            val draft = BoardSetupPosition.fromFen(fen)
            assertEquals(fen, draft.toFen())
            assertNull(draft.validationError())
        }
    }

    @Test fun incomplete_draft_can_place_relocate_replace_move_and_erase_pieces() {
        val empty = BoardSetupPosition()
        assertEquals("Place one white king and one black king.", empty.validationError())
        var draft = empty.put("e1", 'K').put("e8", 'k').put("d4", 'N').put("a3", 'K')
        assertEquals('.', draft.squares[square("e1")])
        assertEquals(1, draft.squares.count { it == 'K' })
        assertNull(draft.validationError())
        draft = draft.move(square("d4"), square("f5"))
        assertEquals('.', draft.squares[square("d4")])
        assertEquals('N', draft.squares[square("f5")])
        assertEquals(draft, draft.move(square("a1"), square("f5")))
        assertEquals(draft, draft.move(square("f5"), square("f5")))
        draft = draft.put("f5", 'b').put("f5", '.')
        assertEquals("4k3/8/8/8/8/K7/8/8 w - - 0 1", draft.toFen())
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", empty.toFen())
    }

    @Test fun castling_is_removed_when_king_or_rook_is_removed_and_never_automatically_reenabled() {
        var draft = BoardSetupPosition.initial().put("a1", '.')
        assertEquals("Kkq", draft.castling)
        assertFalse(draft.canCastle('Q'))
        assertEquals(draft, draft.withCastling('Q', true))
        draft = draft.put("a1", 'R')
        assertTrue(draft.canCastle('Q'))
        assertEquals("Kkq", draft.castling)
        assertEquals("KQkq", draft.withCastling('Q', true).castling)
        assertEquals("kq", draft.put("f1", 'K').castling)
        assertEquals("K", draft.move(square("e8"), square("e6")).castling)
    }

    @Test fun en_passant_tracks_side_and_double_push_and_can_be_actually_played() {
        val draft = BoardSetupPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")
        assertEquals(listOf("d6"), draft.enPassantTargets())
        assertEquals("-", draft.withTurn(false).enPassant)
        assertEquals("-", draft.put("d5", '.').enPassant)
        assertEquals("-", draft.put("d7", 'p').enPassant)
        assertEquals("-", draft.put("d6", 'n').enPassant)
        val board = ChessBoard().apply { assertTrue(setFen(draft.toFen())) }
        assertTrue(board.makeMove("exd6"))
        assertNull(board.getPiece(Square.fromAlgebraic("d5")!!))
        val black = BoardSetupPosition.fromFen("4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 2")
        assertEquals(listOf("d3"), black.enPassantTargets())
        assertNotNull(black.copy(halfMoves = "1").validationError())
    }

    @Test fun last_move_lists_both_colors_and_each_push_once_even_with_two_capturers() {
        val white = BoardSetupPosition.fromFen("4k3/8/8/2PpPpP1/8/8/8/4K3 w - - 4 9")
        assertEquals(listOf("d7–d5", "f7–f5"), white.enPassantLastMoves().map { it.label })
        assertEquals(listOf("d6", "f6"), white.enPassantLastMoves().map { it.enPassant })
        val black = BoardSetupPosition.fromFen("4k3/8/8/8/2pPpPp1/8/8/4K3 b - - 4 9")
        assertEquals(listOf("d2–d4", "f2–f4"), black.enPassantLastMoves().map { it.label })
        assertEquals(listOf("d3", "f3"), black.enPassantLastMoves().map { it.enPassant })
        assertEquals(white.enPassantLastMoves(), white.copy(halfMoves = "", fullMove = "").enPassantLastMoves())
    }

    @Test fun last_move_requires_a_legal_capture_and_a_legal_predecessor() {
        for (fen in listOf(
            "4k3/8/8/3p4/8/8/8/4K3 w - d6 0 2", // No adjacent capturer; FEN still preserves the target.
            "k3r3/8/8/3pP3/8/8/8/4K3 w - - 0 2", // e5 pawn is pinned.
            "7k/8/8/r4pPK/8/8/8/8 w - - 0 2", // EP would uncover a rook attack along the rank.
            "k7/8/8/3pP3/8/5n2/8/4K3 w - - 0 2", // EP cannot answer the knight check.
            "k7/8/8/3pP3/8/8/8/R3K3 w - - 0 2" // Last mover is still in check.
        )) {
            assertTrue(fen, BoardSetupPosition.fromFen(fen).enPassantLastMoves().isEmpty())
        }
        // White was already checked before the claimed last move; Black could not have been on move.
        val invalidBefore = BoardSetupPosition.fromFen("7k/8/2K5/3pP3/8/8/8/8 w - - 0 2")
        assertTrue(invalidBefore.enPassantLastMoves().isEmpty())
        val checkByPush = BoardSetupPosition.fromFen("7k/8/8/3pP3/4K3/8/8/8 w - - 0 2")
        assertEquals(listOf("d7–d5"), checkByPush.enPassantLastMoves().map { it.label })
        assertTrue(BoardSetupPosition().enPassantLastMoves().isEmpty())
        val valid = BoardSetupPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 2")
        assertTrue(valid.put("d7", 'n').enPassantLastMoves().isEmpty())
        assertTrue(valid.put("d6", 'n').enPassantLastMoves().isEmpty())
    }

    @Test fun rejects_adjacent_kings_pawns_on_back_rank_and_excess_pieces() {
        val draft = BoardSetupPosition().put("e1", 'K').put("e8", 'k')
        assertTrue(draft.put("d2", 'k').validationError()!!.contains("next to"))
        assertTrue(draft.put("a1", 'P').validationError()!!.contains("Pawns"))
        assertTrue(draft.put("h8", 'p').validationError()!!.contains("Pawns"))
        assertNotNull(BoardSetupPosition.initial().put("a3", 'P').validationError())
        assertNotNull(BoardSetupPosition.initial().put("b3", 'N').validationError())
    }

    @Test fun check_is_allowed_only_for_the_side_to_move_including_checkmate() {
        val check = BoardSetupPosition.fromFen("4k3/8/8/8/8/8/8/K3R3 b - - 0 1")
        assertNull(check.validationError())
        assertTrue(check.withTurn(true).validationError()!!.contains("just moved"))
        val mate = BoardSetupPosition.fromFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")
        assertNull(mate.validationError())
    }

    @Test fun invalid_or_overflowing_move_counters_do_not_start() {
        val draft = BoardSetupPosition.initial()
        for (text in listOf("", "-1", "1.5", "2147483648", "text", "+1")) {
            assertNotNull(text, draft.copy(halfMoves = text).validationError())
            assertNotNull(text, draft.copy(fullMove = text).validationError())
        }
        assertNotNull(draft.copy(fullMove = "0").validationError())
        assertNull(draft.copy(halfMoves = "100", fullMove = "52").validationError())
    }
}
