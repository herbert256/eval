package com.eval.chess

import com.eval.ui.BoardHistoryBuilder
import org.junit.Assert.*
import org.junit.Test

class PgnImportRobustnessTest {
    private fun applied(pgn: String) = BoardHistoryBuilder.build(
        PgnParser.parseMoves(pgn), requireNotNull(PgnParser.parseInitialBoard(pgn)))

    @Test fun an_unclosed_variation_ends_at_the_next_game_and_is_reported() {
        val db = "[Event \"G1\"]\n\n1. e4 (1. d4 e5 2. Nf3 *\n\n[Event \"G2\"]\n\n1. d4 d5 *\n\n[Event \"G3\"]\n\n1. c4 *\n"
        val games = PgnParser.splitGames(db)
        assertEquals(3, games.size)
        assertEquals(listOf("G1", "G2", "G3"), games.map { PgnParser.parseHeaders(it)["Event"] })
        assertEquals(listOf("d4", "d5"), PgnParser.parseMoves(games[1]))
        assertEquals(listOf("c4"), PgnParser.parseMoves(games[2]))
        // The first game keeps its main line, then fails at the variation so the loader shows an error.
        val first = applied(games[0])
        assertEquals(listOf("e4"), first.validMoves)
        val failed = requireNotNull(first.failedMove)
        assertTrue(failed, failed.contains("unclosed variation"))
        assertEquals("*", PgnParser.parseResult(games[0]))
    }

    @Test fun a_result_inside_an_unclosed_variation_ends_the_game() {
        val games = PgnParser.splitGames("1. e4 (1. d4 d5 *\n1. c4 c5 *")
        assertEquals(2, games.size)
        assertEquals(listOf("c4", "c5"), PgnParser.parseMoves(games[1]))
        assertTrue(PgnParser.parseMoves("1. e4 e5 2. Nf3 (2. Nc3 Nf6").last().contains("unclosed variation"))
    }

    @Test fun balanced_variations_are_still_ignored_even_when_they_contain_a_result() {
        val pgn = "1. e4 (1. d4 d5 2. c4 1-0) e5 (1... c5 (1... e6)) 2. Nf3 *"
        assertEquals(listOf("e4", "e5", "Nf3"), PgnParser.parseMoves(pgn))
        assertEquals("*", PgnParser.parseResult(pgn))
    }

    @Test fun fractional_clock_times_are_kept() {
        val moves = PgnParser.parseMovesWithClock("1. e4 {[%clk 0:02:59.9]} e5 {[%clk 0:03:00]} *")
        assertEquals(listOf("0:02:59.9", "0:03:00"), moves.map { it.clockTime })
    }

    @Test fun book_and_legacy_spellings_import_as_standard_moves() {
        val pgn = "1. ♘f3 ♞f6 2. e2-e4 Nxe4 3. Qe2 Nf6 4. Qxe7+ Bxe7 *"
        assertEquals(listOf("Nf3", "Nf6", "e2-e4", "Nxe4", "Qe2", "Nf6", "Qxe7+", "Bxe7"), applied(pgn).validMoves)
        val promotions = "[SetUp \"1\"]\n[FEN \"8/P3k3/8/8/8/8/8/4K3 w - - 0 1\"]\n\n1. a8(Q) Kd6 2. Qa7 *"
        assertEquals(listOf("a8=Q", "Kd6", "Qa7"), applied(promotions).validMoves)
        for (spelling in listOf("a8Q", "a8=q", "a8(Q)", "a8/Q", "a7-a8=Q", "Pa8=Q")) {
            val board = ChessBoard().apply { check(setFen("8/P3k3/8/8/8/8/8/4K3 w - - 0 1")) }
            assertTrue(spelling, board.makeMove(spelling))
            assertEquals(spelling, Piece(PieceType.QUEEN, PieceColor.WHITE), board.getPiece(0, 7))
        }
        val ep = "[SetUp \"1\"]\n[FEN \"4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2\"]\n\n2. exd6e.p. *"
        assertEquals(listOf("exd6"), applied(ep).validMoves)
        assertEquals(listOf("exd6"), PgnParser.parseMoves("2. exd6 e.p. *"))
        val fileOnly = ChessBoard().apply { check(setFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")) }
        assertTrue(fileOnly.makeMove("ed6"))
    }

    @Test fun long_algebraic_must_match_the_board() {
        val board = ChessBoard()
        assertFalse(board.copy().makeMove("Ng1-e2"))   // occupied by a pawn
        assertFalse(board.copy().makeMove("Bg1-f3"))   // wrong piece letter
        assertFalse(board.copy().makeMove("e2xe4"))    // not a capture
        assertTrue(board.copy().makeMove("Ng1-f3"))
        assertFalse(board.makeMove("g1f3"))           // UCI remains for makeUciMove
    }

    @Test fun move_numbers_with_unicode_ellipsis_and_loose_glyphs_are_skipped() {
        val pgn = "1. e4 ± e5 2. Nf3 += N Nc6?! 3. Bb5 !? a6 +- 4. Ba4 ⩲ 4… Nf6 = -+ ∓ 5. O-O *"
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O"), applied(pgn).validMoves)
        assertEquals(listOf("e4", "e5"), PgnParser.parseMoves("1.e4 1…e5 *"))
    }

    @Test fun unicode_draw_marker_is_a_result_not_a_move() {
        val pgn = "1. e4 e5 ½-½"
        assertEquals(listOf("e4", "e5"), PgnParser.parseMoves(pgn))
        assertEquals("1/2-1/2", PgnParser.parseResult(pgn))
        assertEquals(listOf("1/2-1/2", "1-0", "0-1", "*", null), listOf("½-½", "1–0", "0-1", "*", "0-0").map(PgnParser::resultToken))
    }

    @Test fun null_moves_stop_the_import_with_an_error() {
        val result = applied("1. e4 -- 2. d4 *")
        assertEquals(listOf("e4"), result.validMoves)
        assertEquals("--", result.failedMove)
    }

    @Test fun tags_without_a_space_are_read() {
        assertEquals(mapOf("Event" to "X", "Site" to "Y"), PgnParser.parseHeaders("[Event\"X\"]\n[Site \"Y\"]\n\n1. e4 *"))
    }

    @Test fun text_around_a_database_does_not_become_a_game() {
        val games = PgnParser.splitGames("Downloaded from a forum\n\n[Event \"A\"]\n\n1. e4 e5 1-0\n\nThanks for reading!")
        assertEquals(1, games.size)
        assertEquals("A", PgnParser.parseHeaders(games[0])["Event"])
        assertEquals(listOf("1. e4 e5 *"), PgnParser.splitGames("1. e4 e5 *"))
        assertEquals(2, PgnParser.splitGames("1. e4 e5 1-0 1. d4 d5 0-1").size)
    }

    @Test fun variant_helpers_accept_standard_and_set_up_positions_only() {
        assertNull(PgnParser.variantTag("[Event \"X\"]\n\n1. e4 *"))
        assertEquals("Chess960", PgnParser.variantTag("[Variant \"Chess960\"]\n\n1. e4 *"))
        for (standard in listOf(null, "Standard", "standard", "From Position", "fromPosition")) {
            assertTrue("$standard", PgnParser.isStandardVariant(standard))
        }
        for (variant in listOf("Chess960", "chess960", "Crazyhouse", "King of the Hill", "threeCheck", "Atomic")) {
            assertFalse(variant, PgnParser.isStandardVariant(variant))
        }
    }
}
