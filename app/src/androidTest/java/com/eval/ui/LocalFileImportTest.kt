package com.eval.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.data.WebChessKind
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFileImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fen = "4k3/8/8/8/8/8/8/4K3 b - - 0 1"
    private val pgn = "[Event \"Local file\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n\n1. e4 e5 2. Nf3 *"
    private fun manualVisible() = visible().let { nodes ->
        nodes.any { it.contentDescription?.toString() == "Start AI report" } &&
            nodes.none { it.viewIdResourceName == "eval_screen_title" }
    }
    private fun file(name: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "settings_export/local-import-test").apply { mkdirs() }
        return FileProvider.getUriForFile(context, "com.eval.fileprovider", File(directory, name).apply { writeBytes(bytes) })
    }
    private suspend fun UrlGameScanner.settled(): UrlScanState {
        withTimeout(90_000) { while (uiState.value.busy) delay(30) }
        return uiState.value.also { assertNull(it.toString(), it.error) }
    }

    @Test fun reads_plain_utf16_and_html_documents_for_fen_and_pgn() = runBlocking {
        val text = file("notes.txt", "Position: $fen\n\n$pgn".toByteArray(Charsets.UTF_16))
        val html = file("chess.html", "<pre>${pgn.replace("\"", "&quot;")}</pre><p>$fen</p>".toByteArray())
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this)
            try {
                for (uri in listOf(text, html)) {
                    scanner.openLocalFile(uri)
                    val result = scanner.settled()
                    assertTrue(result.toString(), result.results.any { it.kind == WebChessKind.FEN && it.content == fen })
                    assertTrue(result.toString(), result.results.any {
                        it.kind == WebChessKind.PGN &&
                            com.eval.chess.PgnParser.parseHeaders(it.content)["White"] == "Alice" &&
                            com.eval.chess.PgnParser.parseMoves(it.content) == listOf("e4", "e5", "Nf3")
                    })
                    assertTrue(result.warnings.toString(), result.warnings.isEmpty())
                    assertTrue(result.fileName in listOf("notes.txt", "chess.html"))
                }
            } finally { scanner.close() }
        }
    }

    @Test fun detects_board_image_with_generic_file_type_and_requires_review() = runBlocking {
        val image = instrumentation.context.assets.open("url-scan/lichess-italian-black.png").use { it.readBytes() }
        val uri = file("board.bin", image)
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this)
            try {
                scanner.openLocalFile(uri)
                val state = scanner.settled()
                val board = state.results.single()
                assertEquals(WebChessKind.IMAGE, board.kind)
                assertEquals("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w - - 0 1", board.content)
                assertTrue(board.needsReview)
                assertEquals("board.bin", state.fileName)
            } finally { scanner.close() }
        }
    }

    @Test fun unsupported_missing_empty_and_oversized_files_do_not_keep_previous_results() = runBlocking {
        val good = file("position.fen", fen.toByteArray())
        val empty = file("empty.txt", byteArrayOf())
        val binary = file("binary.bin", byteArrayOf(0, 1, 2, 0, 1))
        val large = file("large.txt", ByteArray(16_000_001) { 65 })
        val missing = Uri.parse("content://com.eval.fileprovider/settings_export/local-import-test/missing.txt")
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this)
            try {
                scanner.openLocalFile(good)
                assertEquals(fen, scanner.settled().results.single().content)
                for (uri in listOf(empty, binary, missing, large)) {
                    scanner.openLocalFile(uri)
                    val state = scanner.settled()
                    assertTrue(state.results.isEmpty())
                    assertTrue(state.status.startsWith("No chess"))
                    if (uri != empty) assertTrue(state.toString(), state.warnings.isNotEmpty())
                }
                scanner.openLocalFile(good)
                scanner.cancel()
                delay(200)
                assertFalse(scanner.uiState.value.busy)
                assertTrue(scanner.uiState.value.results.isEmpty())
            } finally { scanner.close() }
        }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        return nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    }
    private fun waitFor(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20_000
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
    private fun pickerResult(uri: Uri?): Instrumentation.ActivityMonitor {
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("*/*")
        }
        return instrumentation.addMonitor(filter, Instrumentation.ActivityResult(
            if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            uri?.let { Intent().setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ), true)
    }

    @Test fun select_game_file_picker_cancel_restore_review_and_start() {
        val uri = file("picked-position.txt", "A position to review: $fen".toByteArray())
        val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
        val previous = settings.exportAllSettings()
        var monitor: Instrumentation.ActivityMonitor? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { ViewModelProvider(it)[GameViewModel::class.java].dismissAiAppWarning() }
                click("Select a game")
                monitor = pickerResult(null)
                click("Start from a local file")
                waitFor("File picker opened once") { monitor!!.hits == 1 }
                waitFor("Cancelled picker can be reopened") { visible().any { it.text?.toString() == "Choose file" } }
                instrumentation.removeMonitor(monitor!!)
                monitor = pickerResult(uri)
                click("Choose file")
                waitFor("FEN result") { visible().any { it.text?.toString() == "Review position" } }
                assertEquals(1, monitor!!.hits)
                instrumentation.removeMonitor(monitor!!)
                monitor = pickerResult(null)
                click("Choose another file")
                waitFor("Replacement picker cancelled") { monitor!!.hits == 1 }
                waitFor("Cancel keeps existing result") { visible().any { it.text?.toString() == "Review position" } }
                scenario.recreate()
                waitFor("Selected file restored after rotation") { visible().any { it.text?.toString() == "Review position" } }
                assertEquals("Recreation must not reopen the picker", 1, monitor!!.hits)
                click("Review position")
                waitFor("Found FEN opens Board setup") { visible().any { it.text?.toString() == "Board setup" } }
                waitFor("Imported pieces are editable") { visible().any { it.contentDescription?.toString() == "Place white knight" } }
                click("Place white knight")
                click("c3, empty")
                waitFor("Knight placed in imported position") { visible().any { it.contentDescription?.toString() == "c3, white knight" } }
                scenario.recreate()
                waitFor("Editor and edits survive recreation") { visible().any { it.contentDescription?.toString() == "c3, white knight" } }
                back()
                waitFor("Back restores file results") { visible().any { it.text?.toString() == "Review position" } }
                click("Scan again")
                click("Review position")
                click("Start from this position")
                waitFor("Manual screen") { manualVisible() }
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.setAnalysisEnabled(false)
                    assertEquals(fen, vm.uiState.value.currentBoard.getFen())
                }
            }
        } finally {
            monitor?.let(instrumentation::removeMonitor)
            assertTrue(settings.importAllSettings(previous))
        }
    }
}
