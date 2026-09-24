package com.eval.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.ui.theme.EvalTheme
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardSetupScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
    private fun manualVisible() = visible().let { nodes ->
        nodes.any { it.contentDescription?.toString() == "Start AI report" } &&
            nodes.none { it.viewIdResourceName == "eval_screen_title" }
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible(): List<AccessibilityNodeInfo> {
        // Board edits replace many virtual nodes. Refresh the accessibility
        // cache before reading bounds so a tap never uses the previous layout.
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        return nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    }
    private fun matches(node: AccessibilityNodeInfo, label: String) =
        node.text?.toString() == label || node.contentDescription?.toString() == label || node.viewIdResourceName == label
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun waitFor(message: String, timeoutMillis: Long = 20_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail("$message: ${visible().map { it.text ?: it.contentDescription }}")
    }
    private fun reveal(label: String): AccessibilityNodeInfo {
        var forward = true
        repeat(24) {
            visible().firstOrNull { matches(it, label) }?.let { return it }
            val body = visible().firstOrNull { it.isScrollable }
            if (body?.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) != true)
                forward = !forward
            SystemClock.sleep(250)
        }
        error("Could not reveal $label; ${visible().map { it.text ?: it.contentDescription }}")
    }
    private fun click(label: String) {
        var target = bounds(reveal(label))
        var stableSince = SystemClock.uptimeMillis()
        waitFor("Settled bounds for $label") {
            val current = visible().firstOrNull { matches(it, label) }?.let(::bounds)
            if (current != null && current != target) {
                target = current
                stableSince = SystemClock.uptimeMillis()
            }
            current != null && SystemClock.uptimeMillis() - stableSince >= 300
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                target.exactCenterX(), target.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(60)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
    }
    private fun square(name: String) = click("setup_square_$name")
    private fun dragPiece(from: String, to: String? = null) {
        reveal("Initial position")
        SystemClock.sleep(500)
        val start = bounds(reveal("setup_square_$from"))
        val end = to?.let { bounds(reveal("setup_square_$it")) }
        val board = bounds(reveal("setup_board"))
        val endX = end?.exactCenterX() ?: (board.left - 8f)
        val endY = end?.exactCenterY() ?: start.exactCenterY()
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                start.exactCenterX() + (endX - start.exactCenterX()) * fraction,
                start.exactCenterY() + (endY - start.exactCenterY()) * fraction, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..12) { SystemClock.sleep(25); send(MotionEvent.ACTION_MOVE, step / 12f) }
        send(MotionEvent.ACTION_UP, 1f)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
    }
    private fun back() {
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        SystemClock.sleep(300)
    }
    private fun assertPiece(square: String, piece: String) {
        reveal("setup_square_$square")
        waitFor("$square contains $piece") {
            visible().any { it.contentDescription?.toString() == "$square, $piece" }
        }
    }
    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun type(tag: String, text: String) {
        val node = reveal(tag)
        val input = (listOf(node) + nodes(node)).firstOrNull { it.isEditable } ?: error("Not editable: $tag")
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        SystemClock.sleep(200)
    }

    @Test fun url_fen_opens_prefilled_editor_preserves_metadata_and_next_result_is_fresh() {
        val original = "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 12"
        val second = "7k/8/8/8/8/8/8/K7 b - - 5 20"
        val pgn = "[Event \"Import editor regression\"]\n\n1. e4 e5 *"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body((when (path) { "/second.fen" -> second; "/game.pgn" -> pgn; else -> original })
                    .toResponseBody("text/plain".toMediaType())).build()
        }.build()
        var openedFen: String? = null
        var openedPgn: String? = null
        val previous = settings.exportAllSettings()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { EvalTheme {
                    UrlGameScreen(onStartFen = { openedFen = it; true }, onStartPgn = { openedPgn = it }, onBack = {},
                        scannerFactory = { context, scope -> UrlGameScanner(context, scope, client) })
                } } }
                type("import_url", "https://fixture.test/position.fen"); click("Scan URL"); click("Review position")
                reveal("Board setup")
                assertPiece("d5", "black pawn")
                assertNull(openedFen)
                click("Place white knight"); square("c3"); assertPiece("c3", "white knight")
                click("Start from this position")
                waitFor("Edited FEN includes imported metadata") { openedFen == "r3k2r/8/8/3pP3/8/2N5/8/R3K2R w KQkq d6 0 12" }
                back()
                reveal("Review position")
                type("import_url", "https://fixture.test/second.fen"); click("Scan URL"); click("Review position")
                assertPiece("h8", "black king"); assertPiece("c3", "empty")
                click("Start from this position")
                waitFor("Second candidate starts with its own metadata") { openedFen == second }
                back()
                type("import_url", "https://fixture.test/game.pgn"); click("Scan URL"); click("Open game")
                waitFor("PGN still opens directly") { openedPgn?.contains("1. e4 e5") == true }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    @Test fun url_board_image_uses_editor_with_orientation_correction_and_found_position_reset() {
        val bytes = instrumentation.context.assets.open("url-scan/lichess-italian-black.png").use { it.readBytes() }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(bytes.toResponseBody("image/png".toMediaType())).build()
        }.build()
        var opened: String? = null
        val previous = settings.exportAllSettings()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { EvalTheme {
                    UrlGameScreen(onStartFen = { opened = it; true }, onStartPgn = {}, onBack = {},
                        scannerFactory = { context, scope -> UrlGameScanner(context, scope, client) })
                } } }
                type("import_url", "https://fixture.test/board.png"); click("Scan URL")
                waitFor("Recognized image result", timeoutMillis = 90_000) { visible().any { it.text?.toString() == "Review position" } }
                click("Review position"); reveal("Board setup")
                assertPiece("e1", "white king"); assertPiece("c4", "white bishop")
                reveal("Check the pieces, board orientation and whose turn it is. Images do not contain move history.")
                click("Rotate pieces"); assertPiece("d8", "white king")
                click("Found position"); assertPiece("e1", "white king")
                dragPiece("b1", "c3"); assertPiece("c3", "white knight")
                screenshot("imported-board-editor.png")
                click("Start from this position")
                waitFor("Corrected image starts") {
                    opened == "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w - - 0 1"
                }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    @Test fun incomplete_image_position_can_be_repaired_without_editing_fen_text() {
        var opened: String? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { EvalTheme {
                BoardSetupScreen(currentFen = null, initiallyFlipped = false, layout = BoardLayoutSettings(),
                    initialFen = "4k3/8/8/8/8/2N5/8/8 w - - 0 1", imagePosition = true,
                    importWarning = "Some pieces were uncertain. Correct the position before starting.",
                    onStart = { opened = it; true }, onBack = {})
            } } }
            assertPiece("c3", "white knight")
            assertFalse(reveal("setup_start").isEnabled)
            click("Place white king"); square("e1"); assertPiece("e1", "white king")
            click("Start from this position")
            waitFor("Repaired image starts") { opened == "4k3/8/8/8/8/2N5/8/4K3 w - - 0 1" }
        }
    }

    @Test fun conditional_castling_and_last_move_controls_set_the_started_fen() {
        var opened: String? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { EvalTheme {
                BoardSetupScreen(currentFen = null, initiallyFlipped = false, layout = BoardLayoutSettings(),
                    initialFen = "4k2r/8/8/3pPp2/8/8/8/R3K3 w Qk - 7 12",
                    onStart = { opened = it; true }, onBack = {})
            } } }
            reveal("White can castle long")
            reveal("Black can castle short")
            fun absent(label: String) = assertFalse(label, nodes(instrumentation.uiAutomation.rootInActiveWindow).any { matches(it, label) })
            absent("White can castle short"); absent("Black can castle long")
            absent("Move pieces"); absent("Erase")
            click("Black can castle short")
            click("setup_last_move")
            reveal("d7–d5"); click("f7–f5")
            click("Start from this position")
            waitFor("Last move sets en passant and resets the halfmove counter") {
                opened == "4k2r/8/8/3pPp2/8/8/8/R3K3 w Q f6 0 12"
            }
            screenshot("board-setup-move-rights.png")
            click("Black")
            absent("Last move")
            click("Clear")
            reveal("Side to move")
            for (label in listOf("White can castle short", "White can castle long", "Black can castle short", "Black can castle long", "Last move")) absent(label)
        }
    }

    @Test fun create_edit_flip_restore_and_start_position_from_select_game() {
        val previous = settings.exportAllSettings()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.dismissAiAppWarning()
                    assertTrue(vm.startFromFen(com.eval.chess.ChessBoard().getFen()))
                    vm.setAnalysisEnabled(false)
                }
                waitFor("Initial Manual screen") { manualVisible() }
                click("Select a game")
                click("Board setup")
                click("Clear")
                waitFor("Empty board cannot start") { visible().any { it.viewIdResourceName == "setup_start" && !it.isEnabled } }
                assertEquals("Place one white king and one black king.", reveal("setup_error").text.toString())
                click("Place white king"); square("e1"); assertPiece("e1", "white king")
                click("Place black king"); square("e8"); assertPiece("e8", "black king")
                click("Place white queen"); square("a3"); assertPiece("a3", "white queen")
                dragPiece("a3", "b3")
                assertPiece("a3", "empty"); assertPiece("b3", "white queen")
                dragPiece("b3")
                assertPiece("b3", "empty")
                click("Place white knight"); square("c3")
                val before = bounds(reveal("setup_square_e8"))
                click("Flip board")
                assertPiece("c3", "white knight")
                assertTrue(bounds(reveal("setup_square_e1")).top <= before.top)
                click("Black")
                scenario.recreate()
                assertPiece("e1", "white king"); assertPiece("e8", "black king"); assertPiece("c3", "white knight")
                waitFor("Black remains selected") {
                    val node = visible().firstOrNull { matches(it, "Black") }
                    generateSequence(node) { it.parent }.any { it.isSelected || it.isChecked }
                }
                assertTrue(bounds(reveal("setup_square_e1")).top < bounds(reveal("setup_square_e8")).top)
                screenshot("board-setup-edited.png")
                click("Start from this position")
                waitFor("Manual") { manualVisible() }
                val expected = "4k3/8/8/8/8/2N5/8/4K3 b - - 0 1"
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.setAnalysisEnabled(false)
                    assertEquals(expected, vm.uiState.value.currentBoard.getFen())
                }
                assertTrue(expected in settings.loadFenHistory())
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    @Test fun current_position_details_validation_and_android_back_preserve_the_game() {
        val previous = settings.exportAllSettings()
        val original = "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 12"
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.dismissAiAppWarning()
                    assertTrue(vm.startFromFen(original))
                    vm.setAnalysisEnabled(false)
                }
                waitFor("Manual") { manualVisible() }
                click("Select a game"); click("Board setup"); click("Current position")
                reveal("d7–d5")
                click("White can castle long")
                waitFor("Queenside castling switched off") {
                    val node = visible().firstOrNull { matches(it, "White can castle long") }
                    generateSequence(node) { it.parent }.firstOrNull { it.isCheckable }?.isChecked == false
                }
                click("setup_last_move"); click("Unknown / no en passant")
                click("Position details")
                type("setup_fullmove", "0")
                waitFor("Invalid move number cannot start") { visible().any { it.viewIdResourceName == "setup_start" && !it.isEnabled } }
                type("setup_fullmove", "27")
                type("setup_halfmoves", "8")
                scenario.recreate()
                reveal("Hide position details")
                screenshot("board-setup-details.png")
                click("Start from this position")
                waitFor("Manual after details") { manualVisible() }
                val expected = "r3k2r/8/8/3pP3/8/8/8/R3K2R w Kkq - 8 27"
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.setAnalysisEnabled(false)
                    assertEquals(expected, vm.uiState.value.currentBoard.getFen())
                }
                click("Select a game"); click("Board setup"); click("Clear")
                back()
                reveal("Board setup")
                back()
                waitFor("Manual after cancelling setup") { manualVisible() }
                scenario.onActivity { assertEquals(expected, ViewModelProvider(it)[GameViewModel::class.java].uiState.value.currentBoard.getFen()) }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    @Test fun shared_menu_stays_at_top_while_the_editor_title_scrolls_away() {
        val previous = settings.exportAllSettings()
        try {
            settings.saveGeneralSettings(settings.loadGeneralSettings().copy(fullScreen = true))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { ViewModelProvider(it)[GameViewModel::class.java].dismissAiAppWarning() }
                click("Select a game"); click("Board setup")
                val menu = bounds(reveal("eval_top_bar"))
                assertEquals(0, menu.top)
                val body = bounds(reveal("setup_scroll"))
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, fraction: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                        body.centerX().toFloat(), body.top + body.height() * fraction, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.uiAutomation.injectInputEvent(event, true)
                    event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, .9f)
                for (step in 1..12) { SystemClock.sleep(25); send(MotionEvent.ACTION_MOVE, .9f - .7f * step / 12) }
                send(MotionEvent.ACTION_UP, .2f)
                waitFor("Title scrolled away") { visible().none { it.viewIdResourceName == "eval_screen_title" && bounds(it).height() > 0 } }
                assertEquals(menu, bounds(reveal("eval_top_bar")))
                assertFalse(visible().any { it.text?.toString() == "< Back" })
                assertTrue(reveal("setup_start").isEnabled)
                screenshot("board-setup-scrolled.png")
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }
}
