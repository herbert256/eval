package com.eval.ui

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.ui.theme.EvalTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedMenuLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))

    private fun manualVisible() = visible().let { nodes ->
        nodes.any { it.contentDescription?.toString() == "Start AI report" } &&
            nodes.none { it.viewIdResourceName == "eval_screen_title" }
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible() = nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        assertTrue(message, condition())
    }
    private fun header(): AccessibilityNodeInfo? = visible().filter { it.viewIdResourceName == "eval_top_bar" }.singleOrNull()
    private fun systemBack() {
        assertFalse("Back is provided by Android only", visible().any { it.text?.toString() == "< Back" })
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        SystemClock.sleep(250)
    }
    private fun click(text: String) {
        fun find() = visible().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
        waitFor("Could not find $text") { find() != null }
        waitFor("Could not click $text after navigation settled") {
            generateSequence(find()) { it.parent }.firstOrNull { it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        SystemClock.sleep(180)
    }
    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(context.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun assertTitleBelowIcons() {
        waitFor("Separate title and icon rows") {
            val title = visible().singleOrNull { it.viewIdResourceName == "eval_screen_title" }
            val icons = visible().singleOrNull { it.viewIdResourceName == "eval_menu_icons" }
            title != null && icons != null && bounds(title).top >= bounds(icons).bottom
        }
        assertFalse(visible().any { it.text?.toString() == "< Back" })
    }
    private fun assertPinnedAfterScrolling(expectedTop: Int = 0, hasTitle: Boolean = true) {
        if (hasTitle) assertTitleBelowIcons() else waitFor("Manual has no title") { manualVisible() }
        waitFor("The menu must begin at y=$expectedTop") { header()?.let { bounds(it).top == expectedTop } == true }
        // Navigation briefly composes both destinations during its transition.
        // Measure only after the incoming screen's header has settled.
        var lastBounds: Rect? = null
        var stableSince = SystemClock.uptimeMillis()
        waitFor("Navigation must settle before scrolling") {
            val current = header()?.let(::bounds)
            if (current != lastBounds) { lastBounds = current; stableSince = SystemClock.uptimeMillis() }
            current != null && SystemClock.uptimeMillis() - stableSince >= 400
        }
        val before = bounds(header()!!)
        val titleBefore = visible().singleOrNull { it.viewIdResourceName == "eval_screen_title" }?.let(::bounds)
        waitFor("Scrollable content below the menu") { visible().any { it.isScrollable } }
        val body = visible().filter { it.isScrollable }.maxByOrNull { bounds(it).height() }
            ?: error("No scrollable content below the menu")
        val bodyBounds = bounds(body)
        assertTrue("Content must start below the fixed menu", bodyBounds.top >= before.bottom)
        if (!hasTitle) assertEquals("Manual must not reserve space for a title", before.bottom, bodyBounds.top)
        fun bodyText() = visible().filter { !it.text.isNullOrBlank() && bounds(it).top >= before.bottom }
            .map { it.text.toString() to bounds(it).top }
        val oldText = bodyText()
        swipe(bodyBounds, up = true)
        waitFor("The body must actually scroll") { bodyText() != oldText }
        waitFor("The title must scroll fully out of view") {
            visible().none { it.viewIdResourceName == "eval_screen_title" && bounds(it).height() > 0 }
        }
        assertEquals("Scrolling must not move or hide the icon menu", before, bounds(header()!!))
        screenshot("scrolling-title-hidden.png")
        // The title returns at the top, including when a nested LazyColumn or
        // editor owns the scrolling rather than EvalScreen's outer body.
        repeat(6) {
            val title = visible().singleOrNull { it.viewIdResourceName == "eval_screen_title" }
            if (!hasTitle || title == null || bounds(title) != titleBefore) {
                val currentBody = visible().filter { it.isScrollable }.maxByOrNull { bounds(it).height() }
                    ?: error("No scrolling body")
                swipe(bounds(currentBody), up = false)
            }
        }
        waitFor("The full title must return at the top") {
            if (hasTitle) visible().singleOrNull { it.viewIdResourceName == "eval_screen_title" }?.let(::bounds) == titleBefore else manualVisible()
        }
        assertEquals(before, bounds(header()!!))
        screenshot("scrolling-title-restored.png")
    }

    private fun swipe(body: Rect, up: Boolean) {
        val x = body.centerX().toFloat()
        val fromY = body.top + body.height() * if (up) 0.85f else 0.2f
        val toY = body.top + body.height() * if (up) 0.2f else 0.85f
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, fromY)
        for (step in 1..12) {
            SystemClock.sleep(25)
            send(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * step / 12)
        }
        SystemClock.sleep(100)
        send(MotionEvent.ACTION_UP, toY)
        SystemClock.sleep(200)
    }

    @Test fun game_and_navigation_headers_are_fixed_at_the_screen_edge_and_respect_windowed_insets() {
        val previous = settings.exportAllSettings()
        try {
            settings.saveGeneralSettings(settings.loadGeneralSettings().copy(fullScreen = true))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                    vm.dismissAiAppWarning()
                    // Restore a manual game directly, so engine startup/preview
                    // timing cannot change which content this layout test sees.
                    vm.selectAnalysedGame(AnalysedGame(
                        timestamp = 1L, whiteName = "White", blackName = "Black", result = "*",
                        pgn = "[Event \"Fixed menu test\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 *",
                        moves = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5"),
                        moveDetails = emptyList(), previewScores = emptyMap(), analyseScores = emptyMap()
                    ))
                    vm.setAnalysisEnabled(false)
                    vm.updateInterfaceVisibilitySettings(InterfaceVisibilitySettings())
                    vm.updateGraphSettings(vm.uiState.value.graphSettings.copy(lineGraphScale = 300, barGraphScale = 300))
                }
                waitFor("Manual screen") { manualVisible() }
                assertFalse(visible().any { it.viewIdResourceName == "eval_screen_title" })
                screenshot("menu-manual.png")
                click("Start AI report")
                waitFor("AI selection screen") { visible().any { it.text?.toString() == "Select AI parts" } }
                assertTitleBelowIcons()
                screenshot("menu-ai-selection.png")
                scenario.onActivity {
                    val state = ViewModelProvider(it)[GameViewModel::class.java].uiState.value
                    assertEquals(state.currentBoard.getFen(), state.pendingAiReport!!.fen)
                    assertNull(state.aiMovesProgress)
                }
                click("Next")
                waitFor("AI review screen") { visible().any { it.text?.toString() == "Edit AI request" } }
                systemBack()
                waitFor("Back to AI selection") { visible().any { it.text?.toString() == "Select AI parts" } }
                systemBack()
                waitFor("Back to Manual") { manualVisible() }
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.goToStart()
                    vm.exploreLine("e2e4 e7e5", 1)
                }
                waitFor("Exploring variation") { visible().any { it.text?.toString() == "Explore variation" } }
                systemBack()
                waitFor("Android Back restores the game") { manualVisible() }
                assertPinnedAfterScrolling(hasTitle = false)
                click("Settings")
                waitFor("Settings header") { visible().any { it.text?.toString() == "Settings" } }
                // The settings index fits on a tall phone. Its longer settings
                // pages are exercised in a compact viewport by the other test.
                waitFor("Settings menu at the top edge") { header()?.let { bounds(it).top == 0 } == true }
                systemBack()
                click("Help")
                waitFor("Help header") { visible().any { it.text?.toString() == "Help" } }
                assertPinnedAfterScrolling()
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    java.io.File(context.cacheDir, "fixed-menu-help-scrolled.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                systemBack()
                click("Select a game")
                waitFor("Game selector header") { visible().any { it.text?.toString() == "Select a game" } }
                waitFor("Game selector navigation settled") { header()?.let { bounds(it).top == 0 } == true }
                val fullScreenHeader = bounds(header()!!)
                assertEquals(0, fullScreenHeader.top)
                var expectedTop = 0
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                    vm.updateGeneralSettings(vm.uiState.value.generalSettings.copy(fullScreen = false))
                }
                waitFor("Windowed status bar") {
                    scenario.onActivity { activity ->
                        expectedTop = ViewCompat.getRootWindowInsets(activity.window.decorView)
                            ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top ?: 0
                    }
                    expectedTop > 0 && header()?.let { bounds(it).top == expectedTop } == true
                }
                assertEquals("No extra gap below the status bar", expectedTop, bounds(header()!!).top)
                assertEquals(fullScreenHeader.height(), bounds(header()!!).height())
                click("Start from url")
                waitFor("URL import ready") { visible().any { it.text?.toString() == "Scan URL" } }
                click("Help")
                waitFor("Help from nested import") { visible().any { it.text?.toString() == "Help" } }
                systemBack()
                waitFor("Import page restored") { visible().any { it.text?.toString() == "Scan URL" } }
                systemBack()
                waitFor("Back to selector") { visible().any { it.text?.toString() == "Select a game" } }
                click("Eval home")
                waitFor("Main game preserved") { manualVisible() }
                click("Start AI report")
                waitFor("AI selection") { visible().any { it.text?.toString() == "Select AI parts" } }
                click("Select a game")
                waitFor("Menu exits AI selection") { visible().any { it.text?.toString() == "Start from url" } }
                scenario.onActivity {
                    val state = ViewModelProvider(it)[GameViewModel::class.java].uiState.value
                    assertNull(state.pendingAiReport)
                    assertNotNull(state.game)
                }
                systemBack()
                waitFor("Android Back returns to preserved game") { manualVisible() }
                click("Select a game")
                waitFor("Picker ready for reselecting current game") { visible().any { it.text?.toString() == "Start from url" } }
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.selectAnalysedGame(AnalysedGame(
                        timestamp = 1L, whiteName = "White", blackName = "Black", result = "*",
                        pgn = "[Event \"Fixed menu test\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 *",
                        moves = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5"),
                        moveDetails = emptyList(), previewScores = emptyMap(), analyseScores = emptyMap()
                    ))
                    vm.setAnalysisEnabled(false)
                }
                waitFor("Reselecting the same game returns to Manual") { manualVisible() }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    private data class Screen(val title: String, val content: @Composable () -> Unit)

    @Test fun settings_bodies_scroll_in_a_small_viewport_without_moving_their_menus() {
        val previous = settings.exportAllSettings()
        try {
            settings.saveGeneralSettings(settings.loadGeneralSettings().copy(fullScreen = true))
            val screens = listOf(
                Screen("General") { GeneralSettingsScreen(GeneralSettings(), {}, {}, {}) },
                Screen("Board layout") { BoardLayoutSettingsScreen(BoardLayoutSettings(), {}, {}, {}) },
                Screen("Arrow settings") { ArrowSettingsScreen(StockfishSettings(), {}, {}, {}) },
                Screen("Graph settings") { GraphSettingsScreen(GraphSettings(), {}, {}, {}) },
                Screen("Interface elements") { InterfaceSettingsScreen(InterfaceVisibilitySettings(), {}, {}, {}) },
                Screen("Help") { HelpScreen({}) },
                Screen("Previous game retrieves") {
                    PreviousRetrievesScreen(List(40) { RetrievedGamesEntry("Player $it", com.eval.data.ChessServer.LICHESS) }, {}, {})
                },
                Screen("Edit AI request") {
                    AiReportFlowScreen(
                        GameUiState(aiReportEditing = true, aiReportDraft = AiReportDraft(prompt = "Explain the position")),
                        {}, {}, {}, {}, {}, {}
                    )
                }
            )
            val selected = mutableIntStateOf(0)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        EvalTheme {
                            Box(Modifier.fillMaxWidth().height(280.dp)) {
                                key(selected.intValue) { screens[selected.intValue].content() }
                            }
                        }
                    }
                }
                for ((index, screen) in screens.withIndex()) {
                    scenario.onActivity { selected.intValue = index }
                    waitFor("${screen.title} header") { visible().any { it.text?.toString() == screen.title } }
                    try { assertPinnedAfterScrolling() }
                    catch (failure: Throwable) { throw AssertionError("${screen.title}: ${failure.message}", failure) }
                }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }
}
