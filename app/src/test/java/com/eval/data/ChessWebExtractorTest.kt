package com.eval.data

import com.eval.chess.PgnParser
import org.junit.Assert.*
import org.junit.Test

class ChessWebExtractorTest {
    private val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"
    private val pgn = "[Event \"Test\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n\n1. e4 e5 2. Nf3 Nc6 *"

    @Test fun extracts_fen_from_prose_encoded_links_json_and_attributes() {
        for (input in listOf("Position: $fen.",
            "https://lichess.org/analysis/standard/${fen.replace(' ', '_')}",
            "https://lichess.org/analysis?fen=${java.net.URLEncoder.encode(fen, "UTF-8").replace("+", "%20")}",
            "https://lichess.org/analysis?fen=${java.net.URLEncoder.encode(fen, "UTF-8")}",
            "{\"fen\":\"${fen.replace("/", "\\/")}\"}")) {
            val result = ChessWebExtractor.extract(input, "test")
            assertEquals(input, 1, result.size)
            assertEquals(input, fen, result.single().content)
        }
    }

    @Test fun validates_positions_and_flags_missing_state() {
        assertTrue(ChessWebExtractor.extract("8/8/8/8/8/8/8/8 w - - 0 1", "test").isEmpty())
        assertTrue(ChessWebExtractor.extract("9/8/8/8/8/8/8/4K2k w - - 0 1", "test").isEmpty())
        val placement = ChessWebExtractor.extract(fen.substringBefore(' '), "test").single()
        assertTrue(placement.needsReview)
        assertTrue(placement.content.endsWith(" w - - 0 1"))
        assertFalse(ChessWebExtractor.extract(fen, "test").single().needsReview)
    }

    @Test fun extracts_and_validates_games_without_losing_collections() {
        val games = ChessWebExtractor.extract(pgn + "\n" + pgn.replace("Alice", "Carol"), "test")
        assertEquals(2, games.size)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), PgnParser.parseMoves(games.first().content))
        assertEquals("Alice – Bob", games.first().title)
        assertTrue(ChessWebExtractor.extract(pgn.replace("Nc6", "Nc9"), "test").isEmpty())
        assertEquals(1, ChessWebExtractor.extract("1. d4 d5 2. c4 *", "test").size)
        assertEquals(1, ChessWebExtractor.extract("The opening is 1. e4 e5 2. Nf3 Nc6 followed by development.", "test").size)
    }

    @Test fun escaped_pgn_and_fen_starting_games_work() {
        assertEquals(1, ChessWebExtractor.extract(pgn.replace("\n", "\\n").replace("\"", "\\\""), "test").size)
        val custom = "[SetUp \"1\"]\n[FEN \"4k3/8/8/8/8/8/8/4K3 b - - 0 1\"]\n\n1... Kd7 *"
        assertEquals(1, ChessWebExtractor.extract(custom, "test").count { it.kind == WebChessKind.PGN })
    }

    @Test fun recognizes_only_real_lichess_hosts_and_downloads() {
        assertEquals("https://lichess.org/game/export/AbCd1234", ChessWebExtractor.pgnLink("https://lichess.org/AbCd1234/black#12"))
        assertEquals("https://lichess.org/study/AbCd1234/ZyXw9876.pgn", ChessWebExtractor.pgnLink("https://lichess.org/study/AbCd1234/ZyXw9876"))
        assertNull(ChessWebExtractor.pgnLink("https://lichess.org.evil.test/AbCd1234"))
        assertNull(ChessWebExtractor.pgnLink("https://example.com/lichess.org/AbCd1234"))
        for (route in listOf("training", "practice", "analysis", "features", "insights")) {
            assertNull(ChessWebExtractor.pgnLink("https://lichess.org/$route"))
        }
        assertEquals("https://example.com/games.pgn?download=1", ChessWebExtractor.pgnLink("https://example.com/games.pgn?download=1"))
    }

    @Test fun validates_urls_and_preserves_check_symbols() {
        assertEquals("https://lichess.org", ChessWebExtractor.normalizeUrl(" lichess.org "))
        for (url in listOf("file:///etc/passwd", "javascript:alert(1)", "http://example.com", "https://user:pass@example.com", "https://")) {
            try { ChessWebExtractor.normalizeUrl(url); fail(url) } catch (_: IllegalArgumentException) { }
        }
        assertEquals("Qh7+", ChessWebExtractor.decode("Qh7+"))
        assertEquals(fen, ChessWebExtractor.flipPlacement(ChessWebExtractor.flipPlacement(fen)))
    }
}
