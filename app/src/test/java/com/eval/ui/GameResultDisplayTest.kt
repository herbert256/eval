package com.eval.ui

import com.eval.data.LichessGame
import com.eval.data.Player
import com.eval.data.Players
import com.eval.data.User
import org.junit.Assert.*
import org.junit.Test

class GameResultDisplayTest {
    private fun game(status: String, winner: String? = null, pgn: String? = null) = LichessGame(
        "fixture", false, "standard", "blitz", null, status, winner,
        Players(Player(User("Alice", "alice"), null, null), Player(User("Bob", "bob"), null, null)),
        pgn, null, null, null, null
    )

    @Test fun live_missing_and_unfinished_results_show_a_dash() {
        for (status in listOf("created", "started", "*", "unknown", "", "aborted", "noStart", "timeout", "outoftime")) {
            val game = game(status)
            assertEquals(status, "-", gameOutcome(game).text)
            assertEquals(status, "-", playerResultText(game, "Alice"))
        }
    }

    @Test fun only_explicit_draws_are_draws() {
        for (status in listOf("draw", "stalemate", "1/2-1/2")) {
            assertEquals("½-½", gameOutcome(game(status)).text)
            assertEquals("draw", playerResultText(game(status), "Alice"))
        }
        val drawnOnTime = game("outoftime", pgn = "[Result \"1/2-1/2\"]\n\n1. e4 e5 1/2-1/2")
        assertEquals(GameOutcome.DRAW, gameOutcome(drawnOnTime))
    }

    @Test fun wins_keep_scores_and_the_correct_player_perspective() {
        val whiteWin = game("mate", "white")
        assertEquals("1-0", gameOutcome(whiteWin).text)
        assertEquals("win", playerResultText(whiteWin, "alice"))
        assertEquals("lost", playerResultText(whiteWin, "BOB"))
        val blackWin = game("resign", "black")
        assertEquals("0-1", gameOutcome(blackWin).text)
        assertEquals("lost", playerResultText(blackWin, "Alice"))
        assertEquals("win", playerResultText(blackWin, "Bob"))
        assertEquals("0-1", playerResultText(blackWin, "Observer"))
    }

    @Test fun explicit_pgn_results_are_used_when_metadata_has_no_result() {
        assertEquals(GameOutcome.DRAW, gameOutcome(game("unknown", pgn = "1. e4 e5 1/2-1/2")))
        assertEquals(GameOutcome.WHITE_WIN, gameOutcome(game("unknown", pgn = "[Result \"1-0\"]\n\n1. e4")))
        assertEquals(GameOutcome.UNKNOWN, gameOutcome(game("unknown", pgn = "[Result \"*\"]\n\n1. e4 *")))
        assertEquals(GameOutcome.UNKNOWN, gameOutcome(game("unknown", pgn = "1. e4 { 1/2-1/2 } *")))
    }

    @Test fun live_status_wins_over_stale_pgn_result() {
        val game = game("started", pgn = "[Result \"1/2-1/2\"]\n\n1. e4 e5 *")
        assertEquals("-", gameOutcome(game).text)
        assertEquals("started", game.status)
        assertTrue(game.pgn!!.contains("[Result \"1/2-1/2\"]"))
    }
}
