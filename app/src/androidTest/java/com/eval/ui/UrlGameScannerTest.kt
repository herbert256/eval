package com.eval.ui

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.data.WebChessKind
import com.eval.MainActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UrlGameScannerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val expectedPlacement = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
    private val fen = "4k3/8/8/8/8/8/8/4K3 b - - 0 1"

    private fun fixture(name: String): ByteArray = InstrumentationRegistry.getInstrumentation().context.assets
        .open("url-scan/$name").use { it.readBytes() }

    @Test fun compose_preview_has_a_nonzero_portrait_css_viewport() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var scanner: UrlGameScanner? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    val instance = UrlGameScanner(activity, scope)
                    scanner = instance
                    activity.setContent {
                        LazyColumn {
                            item { AndroidView(factory = { instance.pageView }, modifier = Modifier.width(300.dp).height(500.dp)) }
                        }
                    }
                    instance.pageView.loadDataWithBaseURL("https://fixture.test/", "<meta name='viewport' content='width=device-width,initial-scale=1'><p>Page</p>", "text/html", "UTF-8", null)
                }
                withContext(Dispatchers.Main) {
                    val view = scanner!!.pageView
                    withTimeout(10_000) {
                        while (view.width == 0 || view.evaluate("document.readyState") != "\"complete\"") delay(50)
                    }
                    assertEquals("true", view.evaluate("matchMedia('(min-height: 499px)').matches"))
                    assertEquals("true", view.evaluate("matchMedia('(orientation: portrait)').matches"))
                }
            } finally {
                withContext(Dispatchers.Main) { scanner?.close(); scope.cancel() }
            }
        }
    }

    @Test fun bundled_model_reads_white_and_black_orientation_and_rejects_nonboards() = runBlocking {
        withContext(Dispatchers.Main) {
            val recognizer = BoardImageRecognizer(context)
            try {
                for (name in listOf("chesscom-italian-white.png", "lichess-italian-black.png")) {
                    val result = recognizer.recognize("data:image/png;base64," + Base64.encodeToString(fixture(name), Base64.NO_WRAP))
                    assertNotNull(name, result)
                    assertEquals(name, expectedPlacement, result!!.getString("placement"))
                    assertTrue(name, result.getBoolean("reliable"))
                }
                assertNull(recognizer.recognize("data:image/png;base64," + Base64.encodeToString(fixture("reddit-chrome-no-board.png"), Base64.NO_WRAP)))
            } finally { recognizer.close() }
        }
    }

    @Test fun scans_rendered_page_encoded_links_pgn_downloads_and_board_images() = runBlocking {
        withContext(Dispatchers.Main) {
            val image = Base64.encodeToString(fixture("chesscom-italian-white.png"), Base64.NO_WRAP)
            val html = """
                <html><body>
                <pre>[Event "Embedded"]
                [White "Alice"]
                [Black "Bob"]

                1. e4 e5 *</pre>
                <a href="https://lichess.org/analysis/${fen.replace(' ', '_')}">Position</a>
                <a href="https://fixture.test/game.pgn">Download game</a>
                <script>setTimeout(() => { document.body.insertAdjacentHTML('beforeend', '<div data-fen="$fen">dynamic position</div>'); }, 100);</script>
                <img width="500" height="500" src="data:image/png;base64,$image">
                </body></html>
            """.trimIndent()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val pgn = chain.request().url.encodedPath.endsWith(".pgn")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((if (pgn) "[Event \"Linked\"]\n\n1. d4 d5 *" else html)
                        .toResponseBody((if (pgn) "application/x-chess-pgn" else "text/html").toMediaType())).build()
            }.build()
            val scanner = UrlGameScanner(context, this, client) { view, url, content ->
                view.loadDataWithBaseURL(url, content, "text/html", "UTF-8", url)
            }
            try {
                scanner.open("https://fixture.test/page")
                withTimeout(90_000) { while (scanner.uiState.value.busy) delay(100) }
                val state = scanner.uiState.value
                assertNull(state.toString(), state.error)
                assertTrue(state.toString(), state.warnings.isEmpty())
                assertEquals(1, state.results.count { it.kind == WebChessKind.FEN && it.content == fen })
                assertEquals(2, state.results.count { it.kind == WebChessKind.PGN })
                val board = state.results.single { it.kind == WebChessKind.IMAGE }
                assertEquals("$expectedPlacement w - - 0 1", board.content)
                assertTrue(board.needsReview)
            } finally { scanner.close() }
        }
    }

    private fun pageClient(html: String, requests: MutableList<String> = mutableListOf()) = OkHttpClient.Builder().addInterceptor { chain ->
        val path = chain.request().url.encodedPath
        synchronized(requests) { requests += path }
        val (code, body, type) = when {
            path.endsWith(".pgn") -> Triple(200, "[Event \"Linked\"]\n\n1. d4 d5 *", "application/x-chess-pgn")
            path.endsWith(".png") -> Triple(404, "", "text/plain")
            else -> Triple(200, html, "text/html")
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
            .body(body.toResponseBody(type.toMediaType())).build()
    }.build()

    private fun renderedScanner(scope: CoroutineScope, client: OkHttpClient) = UrlGameScanner(context, scope, client) { view, url, content ->
        view.loadDataWithBaseURL(url, content, "text/html", "UTF-8", url)
    }

    @Test fun a_lost_page_renderer_is_reported_and_the_next_scan_uses_a_new_page() = runBlocking {
        assumeTrue("Renderer termination needs Android 10", Build.VERSION.SDK_INT >= 29)
        withContext(Dispatchers.Main) {
            val scanner = renderedScanner(this, pageClient("<p>Position: $fen</p>"))
            try {
                scanner.open("https://fixture.test/page")
                withTimeout(90_000) { while (scanner.uiState.value.busy) delay(100) }
                assertTrue(scanner.uiState.value.hasPage)
                val first = scanner.pageView
                assertTrue(first.webViewRenderProcess!!.terminate())
                withTimeout(30_000) { while (scanner.uiState.value.error == null) delay(50) }
                assertEquals("The page crashed; scan again.", scanner.uiState.value.error)
                assertFalse(scanner.uiState.value.hasPage)
                scanner.open("https://fixture.test/page")
                withTimeout(90_000) { while (scanner.uiState.value.busy) delay(100) }
                val state = scanner.uiState.value
                assertNull(state.toString(), state.error)
                assertTrue(state.results.any { it.content == fen })
                assertNotSame(first, scanner.pageView)
            } finally { scanner.close() }
        }
    }

    @Test fun an_unresponsive_page_times_out_and_board_recognition_still_works() = runBlocking {
        assumeTrue("Renderer termination needs Android 10", Build.VERSION.SDK_INT >= 29)
        val stall = "<p>Stall</p><script>addEventListener('load', () => setTimeout(() => { for (;;) {} }, 300));</script>"
        withContext(Dispatchers.Main) {
            val scanner = renderedScanner(this, pageClient(stall))
            try {
                scanner.open("https://fixture.test/stall")
                withTimeout(60_000) { while (scanner.uiState.value.busy) delay(100) }
                assertTrue(scanner.uiState.value.toString(), scanner.uiState.value.error.orEmpty().contains("did not respond"))
                assertFalse(scanner.uiState.value.hasPage)
                val recognizer = BoardImageRecognizer(context)
                try {
                    val result = recognizer.recognize("data:image/png;base64," + Base64.encodeToString(fixture("chesscom-italian-white.png"), Base64.NO_WRAP))
                    assertEquals(expectedPlacement, result!!.getString("placement"))
                } finally { recognizer.close() }
            } finally { scanner.close() }
        }
    }

    @Test fun page_results_are_capped_even_when_the_page_replaces_the_scan_script_built_ins() = runBlocking {
        val forged = """<script>
            const real = JSON.stringify;
            JSON.stringify = () => real({texts: ['$fen'],
              links: Array.from({length: 1000}, (_, i) => 'https://fixture.test/g' + i + '.pgn'),
              images: Array.from({length: 50}, (_, i) => ({url: 'https://fixture.test/i' + i + '.png', label: 'x'})), limited: false});
            </script><p>Forged</p>"""
        val requests = mutableListOf<String>()
        withContext(Dispatchers.Main) {
            val scanner = renderedScanner(this, pageClient(forged, requests))
            try {
                scanner.open("https://fixture.test/page")
                withTimeout(120_000) { while (scanner.uiState.value.busy) delay(100) }
                val state = scanner.uiState.value
                assertNull(state.toString(), state.error)
                assertTrue(state.results.any { it.content == fen })
                assertEquals(12, synchronized(requests) { requests.count { it.endsWith(".pgn") } })
                assertEquals(20, synchronized(requests) { requests.count { it.endsWith(".png") } })
                assertTrue(state.warnings.toString(), state.warnings.any { it.startsWith("This page is large") })
            } finally { scanner.close() }
        }
    }

    @Test fun handles_http_errors_direct_pgn_and_cancel_without_stale_results() = runBlocking {
        withContext(Dispatchers.Main) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                if (chain.request().url.encodedPath == "/slow") Thread.sleep(1200)
                val code = if (chain.request().url.encodedPath == "/missing") 404 else 200
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                    .body("1. e4 e5 *".toResponseBody("application/x-chess-pgn".toMediaType())).build()
            }.build()
            val scanner = UrlGameScanner(context, this, client)
            try {
                scanner.open("https://fixture.test/missing")
                withTimeout(10_000) { while (scanner.uiState.value.busy) delay(20) }
                assertTrue(scanner.uiState.value.error.orEmpty().contains("404"))
                assertEquals("", scanner.uiState.value.status)
                scanner.open("https://fixture.test/game.pgn")
                withTimeout(10_000) { while (scanner.uiState.value.busy) delay(20) }
                assertEquals(WebChessKind.PGN, scanner.uiState.value.results.single().kind)
                scanner.open("https://fixture.test/slow")
                delay(100)
                scanner.cancel()
                delay(1400)
                assertFalse(scanner.uiState.value.busy)
                assertTrue(scanner.uiState.value.results.isEmpty())
            } finally { scanner.close() }
        }
    }
}
