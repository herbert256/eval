package com.eval.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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

    /** A file in Eval's own FileProvider, which shares must not be able to reach. */
    private fun ownFile(name: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "settings_export").apply { mkdirs() }
        val file = File(directory, "share-test-$name").apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "com.eval.fileprovider", file)
    }

    private val foreignFiles = mutableListOf<Uri>()
    /** A file served by the system media provider, standing in for another app's share. */
    private fun sharedFile(name: String, bytes: ByteArray, type: String = "application/octet-stream"): Uri {
        assumeTrue("MediaStore downloads need Android 10", Build.VERSION.SDK_INT >= 29)
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "share-test-${System.nanoTime()}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE, type)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EvalTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })!!
        foreignFiles += uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    @After fun deleteForeignFiles() { foreignFiles.forEach { context.contentResolver.delete(it, null, null) } }

    private val requests = mutableListOf<String>()
    private fun client(body: String = pgn, type: String = "application/x-chess-pgn") =
        OkHttpClient.Builder().addInterceptor { chain ->
            synchronized(requests) { requests += chain.request().url.toString() }
            if (chain.request().url.encodedPath == "/slow.pgn") Thread.sleep(800)
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

    @Test fun uris_into_evals_own_file_provider_are_ignored() {
        val foreign = Uri.parse("content://fixture/board.png")
        val input = SharedChessInput(streams = listOf(Uri.parse("content://com.eval.fileprovider/settings_export/eval_settings.json"),
            Uri.parse("content://0@com.eval.fileprovider/clipboard_history/x"), Uri.parse("content://COM.EVAL.FILEPROVIDER/x"), foreign))
            .withoutOwnFiles(context)
        assertEquals(listOf(foreign), input.streams)
        assertEquals(listOf("A file from Eval's own storage was ignored."), input.warnings)
        assertSame(input, input.withoutOwnFiles(context))
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

    @Test fun shared_urls_follow_chess_links_and_list_other_links_until_chosen() = runBlocking {
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this, client())
            try {
                scanner.openShared(SharedChessInput(texts = listOf("A game to analyse\nhttps://fixture.test/game.pgn")))
                assertEquals(pgn, scanner.settled().results.single().content)
                scanner.openShared(SharedChessInput(texts = listOf("https://lichess.org/analysis/${fen.replace(' ', '_')}")))
                val state = scanner.settled()
                assertTrue(state.results.any { it.content == fen })
                assertTrue(state.results.any { it.kind == WebChessKind.PGN })
                requests.clear()
                // Other hosts, for example a password-reset link, are only listed.
                scanner.openShared(SharedChessInput(texts = listOf("$fen\nhttps://fixture.test/missing\nhttps://example.com/reset?token=1")))
                val listed = scanner.settled()
                assertEquals(fen, listed.results.single().content)
                assertEquals(listOf("https://fixture.test/missing", "https://example.com/reset?token=1"), listed.links)
                assertTrue(listed.warnings.toString(), listed.warnings.isEmpty())
                assertTrue(requests.toString(), requests.isEmpty())
                scanner.scanLink("https://fixture.test/missing")
                withTimeout(10_000) { while (scanner.uiState.value.busy) delay(30) }
                val chosen = scanner.uiState.value
                assertTrue(chosen.toString(), chosen.error.orEmpty().contains("404"))
                assertEquals(fen, chosen.results.single().content)
                assertEquals(listOf("https://example.com/reset?token=1"), chosen.links)
                assertEquals(listOf("https://fixture.test/missing"), requests)
            } finally { scanner.close() }
        }
    }

    @Test fun shared_files_scan_pgn_and_board_images_and_preserve_results_after_unreadable_items() = runBlocking {
        val image = instrumentation.context.assets.open("url-scan/lichess-italian-black.png").use { it.readBytes() }
        val own = pgn.replace("Alice", "Settings")
        val streams = listOf(sharedFile("game.pgn", pgn.toByteArray(Charsets.UTF_16)),
            sharedFile("board.bin", image), ownFile("own.pgn", own.toByteArray()))
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this)
            try {
                scanner.openShared(SharedChessInput(texts = listOf(fen), streams = streams, mimeType = "application/octet-stream"))
                val state = scanner.settled()
                assertEquals(3, state.results.size)
                assertTrue(state.results.any { it.content == pgn })
                assertTrue(state.results.none { it.content == own })
                val board = state.results.single { it.kind == WebChessKind.IMAGE }
                assertEquals("$placement w - - 0 1", board.content)
                assertTrue(board.needsReview)
                assertEquals(listOf("A file from Eval's own storage was ignored."), state.warnings)
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
                scanner.openShared(SharedChessInput(texts = listOf("https://fixture.test/slow.pgn")))
                delay(50)
                scanner.openShared(SharedChessInput(texts = listOf(fen)))
                assertEquals(fen, scanner.settled().results.single().content)
                delay(1000)
                assertEquals(fen, scanner.uiState.value.results.single().content)
                scanner.openShared(SharedChessInput(texts = listOf("https://fixture.test/slow.pgn")))
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

    private suspend fun waitForText(text: String) {
        withTimeout(30_000) { while (nodeWithText(text) == null) delay(100) }
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
            // Nothing is read from a share until the user taps Scan.
            clickText("Scan")
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
            waitForText("Scan")
            assertNull("Shares wait for Scan", nodeWithText("Open game"))
            clickText("Scan")
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
