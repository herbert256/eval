package com.eval.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.data.ClipboardHistory
import com.eval.data.SharedChessInput
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ClipboardHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fen = "4k3/8/8/8/8/8/8/4K3 b - - 0 1"
    private val pgn = "[Event \"Clipboard game\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n\n1. e4 e5 2. Nf3 *"
    private fun manualVisible() = visible().let { nodes ->
        nodes.any { it.contentDescription?.toString() == "Start AI report" } &&
            nodes.none { it.viewIdResourceName == "eval_screen_title" }
    }
    private fun directory() = File(context.cacheDir, "settings_export/clipboard-test-${UUID.randomUUID()}").apply { mkdirs() }
    private fun uri(file: File) = FileProvider.getUriForFile(context, "com.eval.fileprovider", file)
    private suspend fun UrlGameScanner.settled(): UrlScanState {
        withTimeout(90_000) { while (uiState.value.busy) delay(30) }
        return uiState.value.also { assertNull(it.toString(), it.error) }
    }

    @Test fun keeps_newest_ten_deduplicates_reloads_and_does_not_resurrect_removed_current_clip() = runBlocking {
        val dir = directory()
        var history = ClipboardHistory(context, dir)
        try {
            val writes = (1..12).map { history.record(ClipData.newPlainText("Entry $it", "Clipboard $it")) }
            writes.forEach { it.await() }
            assertEquals((12 downTo 3).map { "Clipboard $it" }, history.uiState.value.entries.map { it.input.texts.single() })
            val last = ClipData.newPlainText("Copied again", "Clipboard 5")
            history.record(last).await()
            assertEquals(10, history.uiState.value.entries.size)
            assertEquals("Clipboard 5", history.uiState.value.entries.first().input.texts.single())
            val ids = history.uiState.value.entries.map { it.input.id }
            history.record(last).await()
            assertEquals(ids, history.uiState.value.entries.map { it.input.id })
            history.close()
            // AtomicFile must recover its backup if a write was interrupted.
            assertTrue(File(dir, "history.json").renameTo(File(dir, "history.json.bak")))
            history = ClipboardHistory(context, dir)
            history.awaitIdle().await()
            assertEquals(ids, history.uiState.value.entries.map { it.input.id })
            history.remove(ids.first()).await()
            history.record(last).await()
            assertEquals(9, history.uiState.value.entries.size)
            history.clear().await()
            history.close()
            history = ClipboardHistory(context, dir)
            history.record(last).await()
            assertTrue(history.uiState.value.entries.isEmpty())
        } finally { history.close(); dir.deleteRecursively() }
    }

    @Test fun snapshots_images_and_pgn_files_before_uri_expires_and_removes_evicted_attachments() = runBlocking {
        val dir = directory()
        var history = ClipboardHistory(context, dir)
        try {
            val board = File(dir, "board.bin").apply {
                writeBytes(instrumentation.context.assets.open("url-scan/lichess-italian-black.png").use { it.readBytes() })
            }
            val game = File(dir, "game.pgn").apply { writeBytes(pgn.toByteArray(Charsets.UTF_16)) }
            val clip = ClipData.newUri(context.contentResolver, "Chess files", uri(board)).apply { addItem(ClipData.Item(uri(game))) }
            history.record(clip).await()
            val savedFiles = history.uiState.value.entries.single().files
            assertEquals(2, savedFiles.size)
            assertTrue(board.delete()); assertTrue(game.delete())
            history.close()
            history = ClipboardHistory(context, dir)
            history.awaitIdle().await()
            val input = history.uiState.value.entries.single().input
            assertTrue(input.warnings.toString(), input.warnings.isEmpty())
            withContext(Dispatchers.Main) {
                val scanner = UrlGameScanner(context, this)
                try {
                    scanner.openClipboard(input)
                    val state = scanner.settled()
                    assertEquals(2, state.results.size)
                    assertTrue(state.results.any { it.kind == WebChessKind.PGN && it.content == pgn })
                    val image = state.results.single { it.kind == WebChessKind.IMAGE }
                    assertEquals("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w - - 0 1", image.content)
                    assertTrue(image.needsReview)
                } finally { scanner.close() }
            }
            for (i in 1..10) history.record(ClipData.newPlainText("Entry", "$i")).await()
            assertEquals(10, history.uiState.value.entries.size)
            assertTrue(File(dir, "attachments").listFiles().orEmpty().isEmpty())
        } finally { history.close(); dir.deleteRecursively() }
    }

    @Test fun copied_text_html_and_urls_use_the_shared_scanner_only_when_selected() = runBlocking {
        val dir = directory()
        val history = ClipboardHistory(context, dir)
        var downloads = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            downloads++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(pgn.toResponseBody("application/x-chess-pgn".toMediaType())).build()
        }.build()
        try {
            val link = "https://lichess.org/analysis/${fen.replace(' ', '_')}"
            history.record(ClipData.newHtmlText("Chess text", "Position $fen", "<pre>${pgn.replace("\"", "&quot;")}</pre>")).await()
            history.record(ClipData.newRawUri("Chess URL", Uri.parse(link))).await()
            assertEquals(0, downloads)
            withContext(Dispatchers.Main) {
                val scanner = UrlGameScanner(context, this, client)
                try {
                    scanner.openClipboard(history.uiState.value.entries.last().input)
                    val text = scanner.settled()
                    assertTrue(text.results.any { it.content == fen })
                    assertTrue(text.results.any { it.kind == WebChessKind.PGN })
                    assertEquals(0, downloads)
                    scanner.openClipboard(history.uiState.value.entries.first().input)
                    val url = scanner.settled()
                    assertTrue(url.results.any { it.content == fen })
                    assertTrue(url.results.any { it.content == pgn })
                    assertEquals(1, downloads)
                    scanner.openClipboard(SharedChessInput(texts = listOf("Not chess")))
                    assertTrue(scanner.settled().status.contains("No chess"))
                    assertTrue(scanner.uiState.value.results.isEmpty())
                } finally { scanner.close() }
            }
        } finally { history.close(); dir.deleteRecursively() }
    }

    @Test fun handles_sensitive_empty_oversized_and_unreadable_clips_without_losing_text() = runBlocking {
        val dir = directory()
        val history = ClipboardHistory(context, dir)
        try {
            history.record(ClipData.newPlainText("Empty", "")).await()
            assertTrue(history.uiState.value.entries.isEmpty())
            history.record(ClipData.newPlainText("Secret", "Never persist this").apply {
                description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            }).await()
            assertTrue(history.uiState.value.entries.isEmpty())
            assertTrue(history.uiState.value.notice!!.contains("sensitive"))
            val large = File(dir, "large.bin").apply { writeBytes(ByteArray(16_000_001)) }
            history.record(ClipData.newPlainText("Mixed", fen).apply {
                addItem(ClipData.Item(uri(large)))
                addItem(ClipData.Item(Uri.parse("content://com.eval.fileprovider/settings_export/missing-clipboard-file")))
                addItem(ClipData.Item(Uri.parse("file:///private/not-readable")))
            }).await()
            val entry = history.uiState.value.entries.single()
            assertEquals(listOf(fen), entry.input.texts)
            assertTrue(entry.input.streams.isEmpty())
            assertTrue(entry.input.warnings.toString(), entry.input.warnings.any { it.contains("16 MB") })
            assertTrue(File(dir, "attachments").listFiles().orEmpty().isEmpty())
            history.record(ClipData.newPlainText("Long", "x".repeat(SharedChessInput.MAX_TEXT + 1))).await()
            assertEquals(SharedChessInput.MAX_TEXT, history.uiState.value.entries.first().input.texts.single().length)
            assertTrue(history.uiState.value.entries.first().input.warnings.any { it.contains("2 MB") })
        } finally { history.close(); dir.deleteRecursively() }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible() = nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    private fun waitFor(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 25_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        fail("$message; visible: ${visible().map { it.text ?: it.contentDescription }}")
    }
    private fun click(label: String) {
        waitFor("Click $label") {
            val node = visible().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
            generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        SystemClock.sleep(200)
    }
    private fun back() {
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        SystemClock.sleep(250)
    }

    @Test fun real_clipboard_capture_menu_selection_rotation_review_and_start() {
        val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
        val previous = settings.exportAllSettings()
        val history = ClipboardHistory.get(context)
        runBlocking { history.awaitIdle().await() }
        val previousIds = history.uiState.value.entries.map { it.input.id }.toSet()
        var previousClip: ClipData? = null
        val dir = directory()
        val board = File(dir, "board.png").apply {
            writeBytes(instrumentation.context.assets.open("url-scan/lichess-italian-black.png").use { it.readBytes() })
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                try {
                    waitFor("Eval has focus") { visible().any { it.contentDescription?.toString() == "Select a game" } }
                    scenario.onActivity { activity ->
                        ViewModelProvider(activity)[GameViewModel::class.java].dismissAiAppWarning()
                        val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        previousClip = clipboard.primaryClip
                        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Clipboard board test", uri(board)))
                    }
                    waitFor("Image clipboard listener saved the entry") {
                        history.uiState.value.entries.firstOrNull()?.title == "Image · Clipboard board test"
                    }
                    scenario.onActivity { activity ->
                        val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Clipboard UI test", "Position: $fen"))
                    }
                    waitFor("Clipboard listener saved the entry") {
                        history.uiState.value.entries.firstOrNull()?.input?.texts == listOf("Position: $fen")
                    }
                    click("Select a game")
                    click("Start from clipboard history")
                    waitFor("History displays entry") { visible().any { it.text?.toString() == "Text · Clipboard UI test" } }
                    waitFor("Image preview visible") { visible().any { it.contentDescription?.toString() == "Clipboard image preview" } }
                    val screenshot = File(context.getExternalFilesDir(null), "clipboard-history-screen.png")
                    instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                        screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    click("Text · Clipboard UI test")
                    waitFor("FEN result") { visible().any { it.text?.toString() == "Review position" } }
                    scenario.recreate()
                    waitFor("Clipboard selection restored") { visible().any { it.text?.toString() == "Review position" } }
                    click("Review position")
                    waitFor("Clipboard FEN opens Board setup") { visible().any { it.text?.toString() == "Board setup" } }
                    waitFor("Clipboard position is prefilled") { visible().any { it.contentDescription?.toString() == "e8, black king" } }
                    scenario.recreate()
                    waitFor("Clipboard editor restored") { visible().any { it.text?.toString() == "Board setup" } }
                    back()
                    back()
                    waitFor("Back to history") { visible().any { it.text?.toString() == "Text · Clipboard UI test" } }
                    click("Text · Clipboard UI test")
                    click("Review position")
                    click("Start from this position")
                    waitFor("Manual screen") { manualVisible() }
                    scenario.onActivity {
                        val vm = ViewModelProvider(it)[GameViewModel::class.java]
                        vm.setAnalysisEnabled(false)
                        assertEquals(fen, vm.uiState.value.currentBoard.getFen())
                    }
                    val idsBeforeResume = history.uiState.value.entries.map { it.input.id }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    waitFor("Resumed Eval") { manualVisible() }
                    runBlocking { history.awaitIdle().await() }
                    assertEquals("Refocusing must not duplicate the same clipboard", idsBeforeResume, history.uiState.value.entries.map { it.input.id })
                } finally {
                    scenario.onActivity { activity ->
                        val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        previousClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
                    }
                }
            }
        } finally {
            runBlocking {
                history.awaitIdle().await()
                history.uiState.value.entries.filterNot { it.input.id in previousIds }.forEach { history.remove(it.input.id).await() }
            }
            assertTrue(settings.importAllSettings(previous))
            dir.deleteRecursively()
        }
    }
}
