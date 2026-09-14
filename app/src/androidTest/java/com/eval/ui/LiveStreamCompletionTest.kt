package com.eval.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.data.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class LiveStreamCompletionTest {
    @Test fun final_game_description_reports_win_and_draw() = runBlocking {
        for ((status, winner) in listOf("mate" to "white", "draw" to null)) {
            val fixture = listOf(
                """{"id":"test","status":{"id":20,"name":"started"}}""",
                """{"fen":"initial","wc":180,"bc":180}""",
                """{"lm":"e2e4","wc":179,"bc":180}""",
                """{"id":"test","status":{"id":30,"name":"$status"},"winner":${winner?.let { "\"$it\"" } ?: "null"}}"""
            ).joinToString("\n") + "\n"
            val api = Proxy.newProxyInstance(LichessApi::class.java.classLoader, arrayOf(LichessApi::class.java)) { _, method, _ ->
                check(method.name == "streamGame")
                Response.success(fixture.toResponseBody())
            } as LichessApi
            val repository = ChessRepository(lichessApi = api)
            val events = repository.streamLiveGame("test").toList()
            assertEquals(1, events.filterIsInstance<LiveGameEvent.Move>().size)
            val ends = events.filterIsInstance<LiveGameEvent.GameEnd>()
            assertEquals("Missing end event: $events", 1, ends.size)
            assertEquals(status, ends.single().status)
            assertEquals(winner, ends.single().winner)
            assertEquals(LiveGameEvent.Disconnected, events.last())
            assertTrue(events.filterIsInstance<LiveGameEvent.Error>().isEmpty())
            val snapshot = repository.streamLichessGame("test")
            assertTrue(snapshot.toString(), snapshot is Result.Success)
            val game = (snapshot as Result.Success).data
            assertEquals("Snapshot lost final status", status, game.status)
            assertEquals(winner, game.winner)
            val expectedResult = if (winner == "white") "1-0" else "1/2-1/2"
            assertEquals(expectedResult, com.eval.chess.PgnParser.parseHeaders(game.pgn!!)["Result"])
            assertEquals(expectedResult, com.eval.chess.PgnParser.parseResult(game.pgn!!))
        }
    }
}
