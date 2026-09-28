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
    }

    @Test fun placement_only_fen_uses_black_when_white_to_move_is_illegal() {
        // Black is in check from the rook, so only Black can be to move.
        val placement = "4k3/8/8/8/8/8/8/4RK2"
        val result = ChessWebExtractor.extract("Diagram: $placement", "test").single()
        assertEquals("$placement b - - 0 1", result.content)
        assertTrue(result.needsReview)
        assertEquals("4k3/8/8/8/8/8/8/5K2 w - - 0 1", ChessWebExtractor.extract("4k3/8/8/8/8/8/8/5K2", "test").single().content)
    }

    @Test fun follows_only_chess_sites_and_pgn_files_automatically() {
        for (url in listOf("https://lichess.org/abcd1234", "https://www.chess.com/game/live/1", "https://en.chessbase.com/post/x",
            "https://www.chessgames.com/perl/chessgame?gid=1", "https://theweekinchess.com/zips/twic1500g.zip",
            "https://example.com/games/round1.PGN", "https://example.com/dl/file.pgn?x=1")) {
            assertTrue(url, ChessWebExtractor.autoFollow(url))
        }
        for (url in listOf("https://example.com/reset?token=1", "https://lichess.org.evil.test/abcd1234", "https://evil-chess.com/x",
            "http://lichess.org/abcd1234", "https://user@lichess.org/x", "https://example.com/pgn", "not a url")) {
            assertFalse(url, ChessWebExtractor.autoFollow(url))
        }
    }

    // The former regex, kept as the reference for the linear scanner.
    private val legacyMoveRun = Regex("""\d+\.(?:\.\.)?\s*(?:(?:\d+\.(?:\.\.)?|[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?[!?]*|O-O(?:-O)?[+#]?|0-0(?:-0)?[+#]?|1/2-1/2|1-0|0-1|\*|\$\d+|\{[^}]*\}|\([^)]*\))\s*)+""")

    @Test fun move_run_scanner_matches_the_former_regex() {
        val samples = mutableListOf(
            "The opening is 1. e4 e5 2. Nf3 Nc6 followed by development.",
            "1. d4 d5 2. c4 {Queen's Gambit} 2... e6 (2... dxc4 3. e3) 3. Nc3 Nf6 4. Bg5 Be7 1-0",
            "12. O-O-O! Qxa2+ 13. Kb1 $14 0-1 and 14.Rd8#", "1... Kd7 *", "1.e4 1/2-1/2", "1. exd8=Q+ Kxd8 1. {unclosed",
            "2023. Year 1. a4 2. h5 99.", "1111. 2. Nf3 {a} {b", "1.. e4 1... e5 1.... e6", "Price 1. 5$ and 2. ( open")
        val tokens = listOf("1.", "2...", "10.", "e4", "exd5", "Nf3", "Qh7+", "e8=Q", "O-O", "O-O-O", "0-0", "{c}", "{", "}", "(",
            ")", " ", "\n", "1-0", "0-1", "1/2-1/2", "*", "$1", "$", "x", "!?", "12", ".", "..", "a", "8", "+", "K", "\t")
        val random = java.util.Random(7)
        repeat(3000) { samples += (1..random.nextInt(30)).joinToString("") { tokens[random.nextInt(tokens.size)] } }
        for (text in samples) {
            assertEquals(text, legacyMoveRun.findAll(text).map { it.value }.toList(), ChessWebExtractor.moveRuns(text).toList())
        }
    }

    @Test fun crafted_prose_is_scanned_in_linear_time() {
        for (unit in listOf("1. {", "1. (", "1", "1. e4 {", "1. e4 ($1 ")) {
            // A leading move sends extract() to the prose scanner.
            val text = "Game 1. e4 e5 " + unit.repeat(2_000_000 / unit.length)
            val started = System.nanoTime()
            ChessWebExtractor.moveRuns(text).count()
            ChessWebExtractor.extract(text, "test")
            val millis = (System.nanoTime() - started) / 1_000_000
            assertTrue("$unit: $millis ms", millis < 1_000)
        }
    }
}
