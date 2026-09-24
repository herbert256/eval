package com.eval.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.chess.BoardSetupPosition
import com.eval.chess.Square
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BoardSetupGestureTest {
    private fun checkGestures(flipped: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val initial = BoardSetupPosition.fromFen("4k3/8/5b2/8/3R4/8/8/4K3 w - - 0 1")
        val result = AtomicReference(initial)
        val rendered = AtomicReference<BoardSetupPosition>()
        val measured = AtomicReference(Rect.Zero)
        val scroll = AtomicReference<ScrollState>()
        lateinit var reset: () -> Unit
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                var draft by remember { mutableStateOf(initial) }
                val state = rememberScrollState()
                SideEffect { rendered.set(draft); scroll.set(state); reset = { draft = initial; result.set(initial) } }
                fun update(value: BoardSetupPosition) { draft = value; result.set(value) }
                Column(Modifier.fillMaxSize().verticalScroll(state), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(80.dp))
                    Box(Modifier.size(280.dp).onGloballyPositioned { measured.set(it.boundsInWindow()) }) {
                        SetupBoard(draft, flipped, BoardLayoutSettings(), emptyMap(),
                            onSquare = { update(draft.place(it, 'N')) },
                            onDrop = { from, to -> update(if (to == null) draft.place(from, '.') else draft.move(from, to)) })
                    }
                    Spacer(Modifier.height(1200.dp))
                }
            } }
            val deadline = SystemClock.uptimeMillis() + 10000
            while (measured.get().width == 0f && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            instrumentation.waitForIdleSync()
            val board = measured.get()
            assertTrue(board.width > 0)
            val cell = board.width / 8
            fun awaitRender() {
                val end = SystemClock.uptimeMillis() + 5000
                while (rendered.get() != result.get() && SystemClock.uptimeMillis() < end) SystemClock.sleep(20)
                assertEquals("Editor recomposed", result.get(), rendered.get())
                instrumentation.waitForIdleSync()
            }
            fun index(square: String) = requireNotNull(Square.fromAlgebraic(square)).index
            fun center(square: String): Offset {
                val sq = requireNotNull(Square.fromAlgebraic(square))
                return Offset(board.left + (if (flipped) 7.5f - sq.file else sq.file + .5f) * cell,
                    board.top + (if (flipped) sq.rank + .5f else 7.5f - sq.rank) * cell)
            }
            fun gesture(from: Offset, destinations: List<Offset>, cancel: Boolean = false) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, point: Offset) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
                    instrumentation.sendPointerSync(event)
                    event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, from)
                var last = from
                for (end in destinations) {
                    for (i in 1..12) {
                        SystemClock.sleep(20)
                        send(MotionEvent.ACTION_MOVE, last + (end - last) * (i / 12f))
                    }
                    last = end
                }
                send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, last)
                instrumentation.waitForIdleSync()
                awaitRender()
            }
            fun piece(square: String, expected: Char) = assertEquals(square, expected, result.get().squares[index(square)])
            fun resetBoard() { scenario.onActivity { reset() }; awaitRender() }

            // Taps place the active knight, but dragging always moves the original piece, of either color.
            gesture(center("b2"), emptyList()); piece("b2", 'N')
            gesture(center("d4"), listOf(center("d6"))); piece("d4", '.'); piece("d6", 'R')
            assertEquals("Vertical piece drag must not scroll", 0, scroll.get().value)
            gesture(center("f6"), listOf(center("a6"))); piece("f6", '.'); piece("a6", 'b')
            gesture(center("a6"), emptyList()); piece("a6", 'N')
            val from = center("d4")
            val outside = listOf(Offset(board.left - cell / 2, from.y), Offset(board.right + cell / 2, from.y),
                Offset(from.x, board.top - cell / 2), Offset(from.x, board.bottom + cell / 2))
            for (end in outside) {
                resetBoard()
                gesture(from, listOf(end))
                assertEquals("Drop outside at $end, board $board", '.', result.get().squares[index("d4")])
                assertEquals(3, result.get().squares.count { it != '.' })
                assertEquals(0, scroll.get().value)
            }
            // Cancellation and excursions outside the board do not erase before release.
            resetBoard()
            gesture(from, listOf(outside.first()), cancel = true)
            assertEquals(initial, result.get())
            gesture(from, listOf(outside.first(), center("a4")))
            piece("d4", '.'); piece("a4", 'R')
            resetBoard()
            gesture(from, listOf(center("e4"), from))
            assertEquals(initial, result.get())
            // Empty-square swipes are not taps and still allow the surrounding screen to scroll.
            gesture(center("c3"), listOf(center("f3")))
            assertEquals(initial, result.get())
            val emptyStart = if (flipped) center("c6") else center("c3")
            gesture(emptyStart, listOf(emptyStart - Offset(0f, cell * 2)))
            assertEquals(initial, result.get())
            assertTrue("Empty-square swipe must scroll", scroll.get().value > 0)
        }
    }

    @Test fun white_orientation_combines_tap_move_erase_and_scroll() = checkGestures(false)
    @Test fun black_orientation_combines_tap_move_erase_and_scroll() = checkGestures(true)
}
