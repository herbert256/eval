package com.eval.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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

@RunWith(AndroidJUnit4::class)
class BoardDragRegressionTest {
    private fun checkDrag(flipped: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val moves = CopyOnWriteArrayList<Pair<Square, Square>>()
        val measuredBounds = java.util.concurrent.atomic.AtomicReference(Rect.Zero)
        val board = ChessBoard().apply { check(setFen("4k3/8/8/8/3R4/8/8/4K3 w - - 0 1")) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(280.dp)) {
                            ChessBoardView(board, flipped = flipped, interactionEnabled = true,
                                onMove = { from, to -> moves.add(from to to) },
                                modifier = Modifier.onGloballyPositioned { measuredBounds.set(it.boundsInWindow()) })
                        }
                    }
                }
            }
            val layoutDeadline = SystemClock.uptimeMillis() + 10000
            while (measuredBounds.get().width == 0f && SystemClock.uptimeMillis() < layoutDeadline) {
                SystemClock.sleep(50)
            }
            instrumentation.waitForIdleSync()
            val bounds = measuredBounds.get()
            assertTrue(bounds.width > 0)
            val size = bounds.width / 8f
            val startX = bounds.left + (if (flipped) 4.5f else 3.5f) * size
            val y = bounds.top + (if (flipped) 3.5f else 4.5f) * size
            fun drag(endX: Float) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    instrumentation.sendPointerSync(event)
                    event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, startX)
                for (i in 1..12) {
                    SystemClock.sleep(20)
                    send(MotionEvent.ACTION_MOVE, startX + (endX - startX) * i / 12)
                }
                send(MotionEvent.ACTION_UP, endX)
                instrumentation.waitForIdleSync()
            }
            drag(bounds.left - size / 2)
            assertTrue("Releasing outside the board must cancel the move: $moves", moves.isEmpty())
            drag(bounds.left + size / 2)
            assertEquals(listOf(Square(3, 3) to Square(if (flipped) 7 else 0, 3)), moves.toList())
        }
    }

    @Test fun dragging_outside_white_board_cancels_but_an_inside_edge_drop_moves() = checkDrag(false)
    @Test fun dragging_outside_flipped_board_cancels_but_an_inside_edge_drop_moves() = checkDrag(true)
}
