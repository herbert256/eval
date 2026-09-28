package com.eval.chess

import com.eval.data.*
import com.eval.export.PgnExporter
import com.eval.ui.BoardHistoryBuilder
import com.eval.ui.MoveDetails
import com.eval.ui.MoveScore
import org.junit.Assert.*
import org.junit.Test

class PgnExportConformanceTest {
    private fun game(pgn: String, status: String = "mate", winner: String? = null,
                     white: Player = Player(User("A", "a"), null, null),
                     black: Player = Player(User("B", "b"), null, null)) =
        LichessGame("test", false, "standard", "blitz", null, status, winner, Players(white, black), pgn, null, null, null, null)

    private fun export(game: LichessGame, openingName: String? = null, scores: Map<Int, MoveScore> = emptyMap()): String {
        val pgn = requireNotNull(game.pgn)
        val history = BoardHistoryBuilder.build(PgnParser.parseMoves(pgn), requireNotNull(PgnParser.parseInitialBoard(pgn)))
        val details = history.validMoves.mapIndexed { i, san ->
            val move = history.boards[i + 1].getLastMove()!!
            MoveDetails(san, move.from.toAlgebraic(), move.to.toAlgebraic(), false, "P")
        }
        return PgnExporter.exportAnnotatedPgn(game, details, scores, emptyMap(), openingName)
    }

    @Test fun a_draw_on_time_keeps_its_result() {
        val pgn = "[Result \"1/2-1/2\"]\n[Termination \"Time forfeit\"]\n\n1. e4 e5 1/2-1/2"
        val exported = export(game(pgn, status = "outoftime"))
        assertEquals("1/2-1/2", PgnParser.parseHeaders(exported)["Result"])
        assertTrue(exported, exported.trimEnd().endsWith(" 1/2-1/2"))
        assertTrue(export(game("1. f3 e5 2. g4 Qh4#", winner = "black")).trimEnd().endsWith(" 0-1"))
        assertTrue(export(game("1. e4 *", status = "started")).trimEnd().endsWith(" *"))
    }

    @Test fun source_tags_are_carried_through_after_the_seven_tag_roster() {
        val pgn = """
            [Event "Rated blitz game"]
            [Site "https://lichess.org/abcdefgh"]
            [Date "2026.01.02"]
            [Round "3"]
            [White "A"]
            [Black "B"]
            [Result "1-0"]
            [UTCDate "2026.01.02"]
            [WhiteTitle "GM"]
            [TimeControl "180+2"]
            [ECO "C20"]
            [Termination "Normal"]
            [PlyCount "99"]

            1. e4 e5 1-0
        """.trimIndent()
        val exported = export(game(pgn, winner = "white"))
        val headers = PgnParser.parseHeaders(exported)
        assertEquals(listOf("Event", "Site", "Date", "Round", "White", "Black", "Result"), headers.keys.take(7))
        assertEquals("3", headers["Round"])
        for (name in listOf("UTCDate", "WhiteTitle", "TimeControl", "ECO", "Termination")) {
            assertEquals(name, PgnParser.parseHeaders(pgn)[name], headers[name])
        }
        assertNull("Ply count must describe the exported moves, not the source", headers["PlyCount"])
        assertEquals("?", PgnParser.parseHeaders(export(game("1. e4 *")))["Round"])
    }

    @Test fun an_eco_code_is_not_written_as_the_opening_name() {
        val exported = export(game("[ECO \"C20\"]\n\n1. e4 e5 *", status = "*"), openingName = "C20")
        assertEquals("C20", PgnParser.parseHeaders(exported)["ECO"])
        assertEquals("Open Game", PgnParser.parseHeaders(exported)["Opening"])
    }

    @Test fun ai_opponents_keep_their_pgn_name() {
        val pgn = "[White \"Stockfish level 8\"]\n[Black \"B\"]\n\n1. e4 *"
        val exported = export(game(pgn, white = Player(null, null, 8)))
        assertEquals("Stockfish level 8", PgnParser.parseHeaders(exported)["White"])
        val noTag = export(game("1. e4 *", white = Player(null, null, 3)))
        assertEquals("lichess AI level 3", PgnParser.parseHeaders(noTag)["White"])
    }

    @Test fun blacks_move_is_numbered_after_a_comment() {
        val scores = mapOf(0 to MoveScore(0.3f, false, 0), 1 to MoveScore(0.2f, false, 0))
        val exported = export(game("1. e4 e5 2. Nf3 *", status = "*"), scores = scores)
        assertTrue(exported, exported.contains("1. e4 {[%eval 0.30]} 1... e5 {[%eval 0.20]} 2. Nf3"))
        val plain = export(game("1. e4 e5 2. Nf3 *", status = "*"))
        assertTrue(plain, plain.contains("1. e4 e5 2. Nf3"))
        assertEquals(listOf("e4", "e5", "Nf3"), PgnParser.parseMoves(exported))
    }
}
