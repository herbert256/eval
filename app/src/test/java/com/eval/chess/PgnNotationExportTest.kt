package com.eval.chess

import com.eval.data.*
import com.eval.export.PgnExporter
import com.eval.ui.MoveDetails
import org.junit.Assert.*
import org.junit.Test

class PgnNotationExportTest {
    private fun exportedMoves(moves: List<String>, fen: String? = null): List<String> {
        val pgn = fen?.let { "[SetUp \"1\"]\n[FEN \"$it\"]\n\n*" } ?: "*"
        val game = LichessGame("test", false, "standard", "blitz", null, "started", null,
            Players(Player(User("A", "a"), null, null), Player(User("B", "b"), null, null)),
            pgn, null, null, null, null)
        val output = PgnExporter.exportAnnotatedPgn(game,
            moves.map { MoveDetails(it, "", "", false, "") }, emptyMap(), emptyMap(), null)
        val parsed = PgnParser.parseMoves(output)
        val board = PgnParser.parseInitialBoard(output)!!
        parsed.forEach { assertTrue("Exported move is not legal SAN: $it", board.makeMove(it)) }
        return parsed
    }

    @Test fun imported_uci_moves_are_exported_as_standard_san() {
        assertEquals(listOf("e4", "e5", "Nf3"), exportedMoves(listOf("e2e4", "e7e5", "g1f3")))
    }

    @Test fun castling_and_promotion_are_exported_as_san() {
        assertEquals(listOf("O-O", "O-O-O"), exportedMoves(listOf("e1g1", "e8c8"), "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        assertEquals(listOf("a8=N"), exportedMoves(listOf("a7a8n"), "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"))
        assertEquals(listOf("a8=R+"), exportedMoves(listOf("a7a8r"), "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"))
    }

    @Test fun en_passant_and_disambiguation_are_exported_as_san() {
        assertEquals(listOf("exd6"), exportedMoves(listOf("e5d6"), "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"))
        assertEquals(listOf("Ndf3"), exportedMoves(listOf("d2f3"), "7k/8/8/8/8/8/3N4/4K1N1 w - - 0 1"))
        assertEquals(listOf("R1a2"), exportedMoves(listOf("a1a2"), "4k3/8/8/8/8/R7/8/R3K3 w - - 0 1"))
    }

    @Test fun checkmate_and_zero_castling_are_normalized_for_export() {
        assertEquals(listOf("f3", "e5", "g4", "Qh4#"), exportedMoves(listOf("f3", "e5", "g4", "Qh4")))
        assertEquals(listOf("O-O"), exportedMoves(listOf("0-0"), "4k3/8/8/8/8/8/8/4K2R w K - 0 1"))
    }
}
