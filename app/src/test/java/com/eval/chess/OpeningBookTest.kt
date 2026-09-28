package com.eval.chess

import com.eval.data.OpeningBook
import org.junit.Assert.*
import org.junit.Test

class OpeningBookTest {
    private fun name(san: String): String? {
        val board = ChessBoard()
        val uci = PgnParser.parseMoves("$san *").map {
            check(board.makeMove(it)) { it }
            board.getLastMove()!!.let { m -> m.from.toAlgebraic() + m.to.toAlgebraic() }
        }
        return OpeningBook.getOpeningName(uci)
    }

    @Test fun open_sicilian_lines_are_named_by_blacks_fifth_move() {
        val open = "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3"
        assertEquals("Sicilian Defense: Open", name(open))
        assertEquals("Sicilian Defense: Najdorf Variation", name("$open a6"))
        assertEquals("Sicilian Defense: Classical Variation", name("$open Nc6"))
        assertEquals("Sicilian Defense: Scheveningen Variation", name("$open e6"))
        assertEquals("Sicilian Defense: Dragon Variation", name("$open g6"))
        assertEquals("Sicilian Defense: Four Knights Variation",
            name("1. e4 c5 2. Nf3 e6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 Nc6"))
    }

    @Test fun corrected_names_match_eco() {
        assertEquals("Caro-Kann Defense: Modern Variation", name("1. e4 c6 2. d4 d5 3. Nd2"))
        assertEquals("Caro-Kann Defense", name("1. e4 c6 2. d4 d5 3. Nc3"))
        assertEquals("Caro-Kann Defense: Classical Variation", name("1. e4 c6 2. d4 d5 3. Nc3 dxe4 4. Nxe4 Bf5"))
        assertEquals("Three Knights Opening", name("1. e4 e5 2. Nf3 Nc6 3. Nc3"))
        assertEquals("Four Knights Game", name("1. e4 e5 2. Nf3 Nc6 3. Nc3 Nf6"))
        assertEquals("Pirc Defense", name("1. e4 d6 2. d4 Nf6 3. Nc3 g6"))
        assertEquals("Pirc Defense: Classical Variation", name("1. e4 d6 2. d4 Nf6 3. Nc3 g6 4. Nf3"))
        assertEquals("King's Indian Defense", name("1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6"))
        assertEquals("Benoni Defense", name("1. d4 Nf6 2. c4 c5 3. d5"))
        assertEquals("Modern Benoni", name("1. d4 Nf6 2. c4 c5 3. d5 e6"))
        assertEquals("Queen's Gambit Declined", name("1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5"))
        assertEquals("Ruy Lopez: Morphy Defense", name("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6"))
        assertEquals("Ruy Lopez: Closed", name("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7"))
        assertEquals("Dutch Defense", name("1. d4 f5 2. g3"))
    }

    @Test fun an_index_beyond_the_game_means_the_whole_game() {
        val moves = listOf("e2e4", "c7c5")
        assertEquals("Sicilian Defense", OpeningBook.getOpeningName(moves, upToIndex = 10))
        assertEquals("King's Pawn Opening", OpeningBook.getOpeningName(moves, upToIndex = 0))
        assertNull(OpeningBook.getOpeningName(moves, upToIndex = -1))
    }
}
