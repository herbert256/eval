package com.eval.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.chess.ChessBoard
import com.eval.chess.Square
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BoardSwipeNavigationTest {
    private class Fixture(flipped: Boolean = false, enabled: Boolean = true) : AutoCloseable {
        val navigation = CopyOnWriteArrayList<String>()
        val moves = CopyOnWriteArrayList<Pair<Square, Square>>()
        val interactive = mutableStateOf(enabled)
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val measuredBounds = AtomicReference(Rect.Zero)
        private val appliedInteraction = AtomicReference<Boolean?>(null)
        private val scenario = ActivityScenario.launch(MainActivity::class.java)
        private val board = ChessBoard().apply { check(setFen("4k3/8/8/8/3R4/8/8/4K3 w - - 0 1")) }

        init {
            scenario.onActivity { activity ->
                activity.setContent {
                    val enabledNow = interactive.value
                    SideEffect { appliedInteraction.set(enabledNow) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(280.dp)) {
                            ChessBoardView(board, flipped = flipped, interactionEnabled = enabledNow,
                                onMove = { from, to -> moves.add(from to to) },
                                onPreviousMove = { navigation.add("previous") },
                                onNextMove = { navigation.add("next") },
                                modifier = Modifier.onGloballyPositioned { measuredBounds.set(it.boundsInWindow()) })
                        }
                    }
                }
            }
            val deadline = SystemClock.uptimeMillis() + 10000
            while (measuredBounds.get().width == 0f && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            instrumentation.waitForIdleSync()
            assertTrue(measuredBounds.get().width > 0f)
        }

        fun setEnabled(value: Boolean) {
            scenario.onActivity { interactive.value = value }
            // Main-thread idleness alone does not wait for the next Compose frame.
            val deadline = SystemClock.uptimeMillis() + 5000
            while (appliedInteraction.get() != value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(16)
            assertEquals(value, appliedInteraction.get())
            instrumentation.waitForIdleSync()
        }

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float,
                  cancel: Boolean = false, halfway: (() -> Unit)? = null) {
            val bounds = measuredBounds.get()
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    bounds.left + x * bounds.width, bounds.top + y * bounds.height, 0)
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, x1, y1)
            for (i in 1..12) {
                SystemClock.sleep(20)
                send(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12)
                if (i == 6) halfway?.invoke()
            }
            send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x2, y2)
            instrumentation.waitForIdleSync()
        }

        override fun close() = scenario.close()
    }

    @Test fun empty_square_swipes_navigate_once_in_both_board_orientations() {
        for (flipped in listOf(false, true)) {
            Fixture(flipped).use { fixture ->
                fixture.swipe(.8f, .3f, .2f, .3f)
                fixture.swipe(.2f, .3f, .8f, .3f)
                assertEquals(listOf("previous", "next"), fixture.navigation.toList())
                assertTrue(fixture.moves.isEmpty())
            }
        }
    }

    @Test fun opponent_piece_swipes_navigate_but_short_vertical_diagonal_and_cancelled_drags_do_not() {
        Fixture().use { fixture ->
            fixture.swipe(.5625f, .0625f, .2f, .0625f) // Black king on e8.
            assertEquals(listOf("previous"), fixture.navigation.toList())
            fixture.navigation.clear()
            fixture.swipe(.7f, .3f, .65f, .3f)
            fixture.swipe(.7f, .3f, .7f, .8f)
            fixture.swipe(.8f, .2f, .2f, .8f)
            fixture.swipe(.8f, .3f, .2f, .3f, cancel = true)
            assertTrue(fixture.navigation.isEmpty())
            assertTrue(fixture.moves.isEmpty())
        }
    }

    @Test fun swipes_are_disabled_outside_manual_interaction_and_cancel_when_it_ends() {
        Fixture(enabled = false).use { fixture ->
            fixture.swipe(.8f, .3f, .2f, .3f)
            fixture.swipe(.2f, .3f, .8f, .3f)
            assertTrue(fixture.navigation.isEmpty())
            fixture.setEnabled(true)
            fixture.swipe(.8f, .3f, .2f, .3f, halfway = { fixture.setEnabled(false) })
            assertTrue(fixture.navigation.isEmpty())
            fixture.setEnabled(true)
            fixture.swipe(.2f, .3f, .8f, .3f)
            assertEquals(listOf("next"), fixture.navigation.toList())
        }
    }
}
