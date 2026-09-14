package com.eval.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.PgnParser
import com.eval.data.ChessRepository
import com.eval.data.LichessApi
import com.eval.data.LichessGame
import com.eval.data.Result
import com.eval.export.PgnExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class PgnCollectionRegressionTest {
    private val repository = ChessRepository()
    private val first = "[Event \"First\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n\n1. e4 e5 *"
    private val second = "[Event \"Second\"]\n[White \"Carol\"]\n[Black \"Dave\"]\n\n1. d4 d5 *"
    private fun games(pgn: String): List<LichessGame> {
        val result = repository.parseGamesFromPgnContent(pgn)
        assertTrue(result.toString(), result is Result.Success)
        return (result as Result.Success).data
    }

    @Test fun collections_accept_different_spacing_and_header_orders() {
        for (separator in listOf("\n", "\n\n", "\n \n  ", "\r\n\t\r\n")) {
            val parsed = games(first + separator + second)
            assertEquals("Separator ${separator.toByteArray().toList()}", 2, parsed.size)
            assertEquals(listOf("e4", "e5"), PgnParser.parseMoves(parsed[0].pgn!!))
            assertEquals(listOf("d4", "d5"), PgnParser.parseMoves(parsed[1].pgn!!))
        }
        val withoutEvent = second.replace("[Event \"Second\"]\n", "")
        assertEquals(2, games(first + "\n\n" + withoutEvent).size)
        assertEquals(2, games("1. e4 e5 *\n1. d4 d5 *").size)
    }

    @Test fun event_text_inside_comments_does_not_create_a_phantom_game() {
        val pgn = first.replace("1. e4", "{A comment\n\n[Event \"Not a game\"]\n1. d4 *}\n1. e4")
        val parsed = games(pgn)
        assertEquals(1, parsed.size)
        assertEquals(listOf("e4", "e5"), PgnParser.parseMoves(parsed.single().pgn!!))
    }

    @Test fun escaped_names_are_preserved_and_comment_tags_are_not_metadata() {
        val pgn = "[Event \"Quoted\"]\n[White \"Alice \\\"Ace\\\" Smith\"]\n[Black \"B\\\\C\"]\n\n1. e4 e5 *"
        val game = games(pgn).single()
        assertEquals("Alice \"Ace\" Smith", game.players.white.user?.name)
        assertEquals("B\\C", game.players.black.user?.name)
        val commentOnly = games("[Event \"Study\"]\n\n{[White \"Fake player\"]} 1. e4 *").single()
        assertEquals("White", commentOnly.players.white.user?.name)
    }

    @Test fun imported_draws_remain_draws_in_state_and_export() {
        for (pgn in listOf("[Result \"1/2-1/2\"]\n\n1. e4 e5 1/2-1/2", "1. e4 e5 1/2-1/2")) {
            val game = games(pgn).single()
            assertEquals("draw", game.status)
            val details = PgnParser.parseMoves(pgn).map { MoveDetails(it, "", "", false, "P") }
            val exported = PgnExporter.exportAnnotatedPgn(game, details, emptyMap(), emptyMap(), null)
            assertEquals("1/2-1/2", PgnParser.parseHeaders(exported)["Result"])
            assertTrue(exported.trimEnd().endsWith("1/2-1/2"))
        }
    }

    @Test fun broadcast_collections_use_the_same_game_boundaries() = runBlocking {
        val api = Proxy.newProxyInstance(LichessApi::class.java.classLoader, arrayOf(LichessApi::class.java)) { _, method, _ ->
            check(method.name == "getBroadcastRoundPgn")
            Response.success(first + "\n \n  " + second)
        } as LichessApi
        val result = ChessRepository(lichessApi = api).getLichessBroadcastGames("test-round")
        assertTrue(result.toString(), result is Result.Success)
        assertEquals(2, (result as Result.Success).data.size)
    }
}
