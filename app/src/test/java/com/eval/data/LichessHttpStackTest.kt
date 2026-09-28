package com.eval.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Goes through the real OkHttp/Retrofit stack (interceptors included) instead of a fake
 * LichessApi, which is how a body-buffering logger once made Lichess TV hang until a game ended.
 */
class LichessHttpStackTest {
    private val server = MockWebServer()

    private val gameInfo = """{"id":"live1","variant":{"key":"standard","name":"Standard"},"speed":"blitz","rated":true,""" +
        """"initialFen":"startpos","players":{"white":{"user":{"name":"alice","id":"alice"},"rating":2000},""" +
        """"black":{"user":{"name":"bob","id":"bob"},"rating":1990}}}"""

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun repository() = ChessRepository(lichessApi = LichessApi.create(server.url("/").toString()))

    @Test fun a_stream_that_stays_open_still_returns_the_moves_played_so_far() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setChunkedBody(listOf(
                    gameInfo,
                    """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}""",
                    """{"fen":"x","lm":"e2e4"}""",
                    """{"fen":"x","lm":"e7e5"}"""
                ).joinToString("\n", postfix = "\n"), 64)
                // The live game continues: the connection is never closed by the server.
                .setSocketPolicy(SocketPolicy.KEEP_OPEN)
        }
        val started = System.nanoTime()
        val result = repository().streamLichessGame("live1")
        val seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
        assertTrue("Result: $result", result is Result.Success)
        val game = (result as Result.Success).data
        assertTrue(game.pgn.orEmpty(), game.pgn.orEmpty().contains("1. e4 e5"))
        assertTrue("Took $seconds s; the stream must not be buffered to its end", seconds < 10)
    }

    @Test fun game_selection_requests_the_export_endpoint_that_includes_a_pgn() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"abc","rated":true,"variant":"standard","speed":"blitz",""" +
            """"status":"started","players":{"white":{"user":{"id":"a","name":"a"}},"black":{"user":{"id":"b","name":"b"}}},""" +
            """"pgn":"[Event \"x\"]\n\n1. e4 *"}"""))
        val result = repository().getLichessGame("abc")
        assertTrue(result is Result.Success)
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.startsWith("/game/export/abc"))
        assertTrue(path.contains("pgnInJson=true"))
    }

    @Test fun offline_and_rate_limited_errors_are_readable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val limited = repository().getLichessGames("someone", 5)
        assertEquals("Lichess is limiting requests. Please try again in a minute.", (limited as Result.Error).message)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val failed = repository().getLichessGames("someone", 5)
        assertFalse((failed as Result.Error).message.contains("Exception"))
    }

    @Test fun tv_channels_are_unique_and_standard_only() = runBlocking {
        server.enqueue(MockResponse().setBody("""{
            "best":{"gameId":"same","user":{"name":"p1"}},
            "bullet":{"gameId":"same","user":{"name":"p1"}},
            "blitz":{"gameId":"b1","user":{"name":"p2"}},
            "chess960":{"gameId":"c960","user":{"name":"p3"}}
        }"""))
        val channels = (repository().getLichessTvChannels() as Result.Success).data
        assertEquals(channels.size, channels.map { it.channelName }.toSet().size)
        assertFalse(channels.any { it.gameId == "c960" })
    }
}
