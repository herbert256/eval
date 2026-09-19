package com.eval.ui

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.ui.theme.EvalTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMovesSettingsScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible() = nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    private fun click(node: AccessibilityNodeInfo): Boolean =
        generateSequence(node) { it.parent }.first { it.isClickable }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    private fun scrollTo(text: String) {
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (visible().any { it.text?.toString() == text }) return
            val scrollable = visible().firstOrNull { it.isScrollable }
            if (scrollable == null) {
                SystemClock.sleep(100)
                return@repeat
            }
            val bounds = Rect().also(scrollable::getBoundsInScreen)
            val x = bounds.centerX().toFloat()
            val fromY = bounds.top + bounds.height() * 0.75f
            val toY = bounds.top + bounds.height() * 0.5f
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, y: Float) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.uiAutomation.injectInputEvent(event, true)
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, fromY)
            for (step in 1..10) {
                SystemClock.sleep(30)
                send(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * step / 10)
            }
            // Stop the finger before lifting, so a fling cannot skip the target card.
            SystemClock.sleep(150)
            send(MotionEvent.ACTION_MOVE, toY)
            send(MotionEvent.ACTION_UP, toY)
            SystemClock.sleep(100)
        }
        fail("Could not scroll to $text; visible text: ${visible().mapNotNull { it.text }}")
    }
    private fun waitFor(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10000
        do {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < end)
        fail("Timed out; visible text: ${visible().mapNotNull { it.text }}")
    }

    @Test fun fourth_card_edits_its_own_settings_and_preparation_can_be_cancelled() {
        var saved = StockfishSettings()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    EvalTheme { StockfishSettingsScreen(StockfishSettings(), {}, {}, { saved = it }) }
                }
            }
            waitFor { visible().any { it.text?.toString() == "Preview Stage" } }
            assertTrue(click(visible().first { it.text?.toString() == "Moves list for AI" }))
            waitFor { visible().any { it.text?.toString() == "Seconds per move" } }
            scrollTo("0.25 s")
            waitFor { visible().any { it.text?.toString() == "Moves list for AI" } }
            assertTrue("Title bar remains visible when scrolling to the fourth card", visible().any { it.text?.toString() == "Stockfish" })
            val value = visible().first { it.text?.toString() == "0.25 s" }
            val bounds = Rect().also { value.getBoundsInScreen(it) }
            val increment = visible().filter { it.text?.toString() == "+" }.minBy {
                val rect = Rect().also(it::getBoundsInScreen)
                kotlin.math.abs(rect.centerY() - bounds.centerY())
            }
            assertTrue(click(increment))
            waitFor { saved.movesListForAi.secondsForMove == 0.5f }
            waitFor { visible().any { it.text?.toString() == "0.5 s" } }
            assertEquals(StockfishSettings().previewStage, saved.previewStage)
            assertEquals(StockfishSettings().analyseStage, saved.analyseStage)
            assertEquals(StockfishSettings().manualStage, saved.manualStage)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir, "ai-moves-settings.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }

            var cancelled = false
            scenario.onActivity { activity ->
                activity.setContent {
                    EvalTheme {
                        AiInstructionSelectionScreen(
                            listOf(AiInstructionEntry(name = "Test instruction")), {}, { cancelled = true },
                            progress = "Evaluating moves: 3 of 20"
                        )
                    }
                }
            }
            waitFor { visible().any { it.text?.toString() == "Evaluating moves: 3 of 20" } }
            assertFalse(generateSequence(visible().first { it.text?.toString() == "Test instruction" }) { it.parent }.all { it.isEnabled })
            assertTrue(click(visible().first { it.text?.toString() == "Cancel" }))
            waitFor { cancelled }
        }
    }

    @Test fun fifth_card_changes_line_count_independently_and_keeps_title_visible() {
        var saved = StockfishSettings()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { EvalTheme { StockfishSettingsScreen(StockfishSettings(), {}, {}, { saved = it }) } }
            }
            waitFor { visible().any { it.text?.toString() == "Preview Stage" } }
            assertTrue(click(visible().first { it.text?.toString() == "Engine moves for AI" }))
            waitFor { visible().any { it.text?.toString() == "Number of lines" } }
            scrollTo("Seconds per position")
            assertTrue(visible().any { it.text?.toString() == "Engine moves for AI" })
            assertTrue(visible().any { it.text?.toString() == "Stockfish" })
            val value = visible().first { it.text?.toString() == "3" }
            val bounds = Rect().also(value::getBoundsInScreen)
            val increment = visible().filter { it.text?.toString() == "+" }.minBy {
                kotlin.math.abs(Rect().also(it::getBoundsInScreen).centerY() - bounds.centerY())
            }
            assertTrue(click(increment))
            waitFor { saved.engineMovesForAi.multiPv == 4 }
            waitFor { visible().any { it.text?.toString() == "4" } }
            assertEquals(StockfishSettings().copy(engineMovesForAi = AiEngineSettings(multiPv = 4)), saved)
            java.io.File(instrumentation.targetContext.cacheDir, "ai-engine-settings.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
