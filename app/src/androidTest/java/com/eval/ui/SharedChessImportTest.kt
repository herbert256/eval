package com.eval.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.data.SharedChessInput
import com.eval.data.SharedChessText
import com.eval.data.WebChessKind
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SharedChessImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fen = "4k3/8/8/8/8/8/8/4K3 b - - 0 1"
    private val pgn = "[Event \"Shared game\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n\n1. e4 e5 2. Nf3 *"
    private val placement = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"

    private fun sharedFile(name: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "settings_export").apply { mkdirs() }
        val file = File(directory, "share-test-$name").apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "com.eval.fileprovider", file)
    }

    private fun client(body: String = pgn, type: String = "application/x-chess-pgn") =
        OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath == "/slow") Thread.sleep(800)
            val code = if (chain.request().url.encodedPath == "/missing") 404 else 200
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .body(body.toResponseBody(type.toMediaType())).build()
        }.build()

    private suspend fun UrlGameScanner.settled(): UrlScanState {
        withTimeout(90_000) { while (uiState.value.busy) delay(30) }
        return uiState.value.also { assertNull(it.toString(), it.error) }
    }

    @Test fun android_resolves_eval_for_text_pgn_images_and_multiple_attachments() {
        for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            for (type in listOf("text/plain", "text/html", "image/png", "image/jpeg", "application/x-chess-pgn", "application/octet-stream")) {
                val intent = Intent(action).setType(type).setPackage(context.packageName)
                @Suppress("DEPRECATION")
                val activities = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                assertTrue("$action $type", activities.any { it.activityInfo.name == MainActivity::class.java.name })
            }
        }
    }

    @Test fun reads_extras_and_clip_data_once_and_rejects_file_uris() {
        val uri = Uri.parse("content://fixture/board.png")
        val clip = ClipData.newPlainText("position", fen).apply {
            addItem(ClipData.Item(uri))
            addItem(ClipData.Item(Uri.parse("file:///private/board.png")))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png")
            .putExtra(Intent.EXTRA_TEXT, fen)
            .putExtra(Intent.EXTRA_HTML_TEXT, "<pre>$pgn</pre>")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri, uri))
            .apply { clipData = clip }
        val input = SharedChessInput.fromIntent(intent)!!
        assertEquals(listOf(fen), input.texts)
        assertEquals(listOf(uri), input.streams)
        assertEquals(1, input.html.size)
        assertEquals(1, input.warnings.size)
        assertNull(SharedChessInput.fromIntent(Intent(Intent.ACTION_MAIN)))
        val tooMany = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,
            ArrayList((1..25).map { Uri.parse("content://fixture/$it.png") }))
        assertEquals(20, SharedChessInput.fromIntent(tooMany)!!.streams.size)
        assertTrue(SharedChessInput.fromIntent(tooMany)!!.warnings.isNotEmpty())
    }

    @Test fun parses_html_entities_embedded_pgn_positions_and_image_references_without_running_scripts() {
        val html = """<p>A game:</p><pre>${pgn.replace("\"", "&quot;")}</pre>
            <div data-fen="$fen"></div><img src="https://fixture.test/board.png?a=1&amp;b=2">
            <a href="https://fixture.test/game.pgn">Game</a>"""
        val result = SharedChessText.parse(html, true, "HTML")
        assertTrue(result.candidates.any { it.kind == WebChessKind.PGN && it.title == "Alice – Bob" })
        assertTrue(result.candidates.any { it.content == fen })
        assertEquals(listOf("https://fixture.test/board.png?a=1&b=2"), result.images)
        assertTrue(result.urls.contains("https://fixture.test/game.pgn"))
    }

    @Test fun shared_urls_use_retrieval_and_keep_fen_text_when_a_link_fails() = runBlocking {
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this, client())
            try {
                scanner.openShared(SharedChessInput(texts = listOf("A game to analyse\nhttps://fixture.test/game.pgn")))
                assertEquals(pgn, scanner.settled().results.single().content)
                scanner.openShared(SharedChessInput(texts = listOf("https://lichess.org/analysis/${fen.replace(' ', '_')}")))
                val state = scanner.settled()
                assertTrue(state.results.any { it.content == fen })
                assertTrue(state.results.any { it.kind == WebChessKind.PGN })
                scanner.openShared(SharedChessInput(texts = listOf("$fen\nhttps://fixture.test/missing")))
                val failed = scanner.settled()
                assertEquals(fen, failed.results.single().content)
                assertTrue(failed.warnings.single().contains("404"))
            } finally { scanner.close() }
        }
    }

    @Test fun shared_files_scan_pgn_and_board_images_and_preserve_results_after_unreadable_items() = runBlocking {
        val image = instrumentation.context.assets.open("url-scan/lichess-italian-black.png").use { it.readBytes() }
        val streams = listOf(sharedFile("game.pgn", pgn.toByteArray(Charsets.UTF_16)),
            sharedFile("board.bin", image), Uri.parse("content://com.eval.fileprovider/settings_export/missing-share-test.pgn"))
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this)
            try {
                scanner.openShared(SharedChessInput(texts = listOf(fen), streams = streams, mimeType = "application/octet-stream"))
                val state = scanner.settled()
                assertEquals(3, state.results.size)
                assertTrue(state.results.any { it.content == pgn })
                val board = state.results.single { it.kind == WebChessKind.IMAGE }
                assertEquals("$placement w - - 0 1", board.content)
                assertTrue(board.needsReview)
                assertEquals(1, state.warnings.size)
            } finally { scanner.close() }
        }
    }

    @Test fun empty_oversized_and_cancelled_shares_settle_without_stale_results() = runBlocking {
        val bigFile = sharedFile("oversize.pgn", ByteArray(16_000_001) { 65 })
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this, client())
            try {
                scanner.openShared(SharedChessInput())
                assertTrue(scanner.settled().status.startsWith("No chess"))
                scanner.openShared(SharedChessInput(streams = listOf(bigFile), texts = listOf(fen)))
                assertTrue(scanner.settled().warnings.single().contains("too large"))
                scanner.openShared(SharedChessInput(texts = listOf("https://fixture.test/slow")))
                delay(50)
                scanner.openShared(SharedChessInput(texts = listOf(fen)))
                assertEquals(fen, scanner.settled().results.single().content)
                delay(1000)
                assertEquals(fen, scanner.uiState.value.results.single().content)
                scanner.openShared(SharedChessInput(texts = listOf("https://fixture.test/slow")))
                delay(50)
                scanner.cancel()
                delay(1000)
                assertFalse(scanner.uiState.value.busy)
                assertTrue(scanner.uiState.value.results.isEmpty())
            } finally { scanner.close() }
        }
    }

    private fun nodeWithText(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }

    private suspend fun clickText(text: String) {
        withTimeout(30_000) {
            while (true) {
                var node = nodeWithText(text)
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) break
                delay(100)
            }
        }
    }

    @Test fun cold_and_warm_shares_open_review_and_games_and_do_not_reappear_after_recreation(): Unit = runBlocking {
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, fen)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var firstId = ""
            scenario.onActivity { firstId = ViewModelProvider(it)[GameViewModel::class.java].sharedImport.value!!.id }
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(Intent.ACTION_SEND, it.intent.action) }
            scenario.recreate()
            scenario.onActivity { assertEquals(firstId, ViewModelProvider(it)[GameViewModel::class.java].sharedImport.value!!.id) }
            clickText("Review position")
            clickText("Start from this position")
            scenario.onActivity {
                val model = ViewModelProvider(it)[GameViewModel::class.java]
                assertNull(model.sharedImport.value)
                assertEquals(fen, model.uiState.value.currentBoard.getFen())
            }
            scenario.recreate()
            scenario.onActivity { assertNull(ViewModelProvider(it)[GameViewModel::class.java].sharedImport.value) }
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, pgn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            clickText("Open game")
            scenario.onActivity {
                val model = ViewModelProvider(it)[GameViewModel::class.java]
                assertNull(model.sharedImport.value)
                assertEquals(listOf("e4", "e5", "Nf3"), model.uiState.value.moves)
                assertEquals("Alice", model.uiState.value.game?.players?.white?.user?.name)
            }
        }
        Unit
    }
}
