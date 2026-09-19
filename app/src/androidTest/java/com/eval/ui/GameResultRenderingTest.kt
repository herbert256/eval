package com.eval.ui

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.data.*
import com.eval.ui.theme.EvalTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameResultRenderingTest {
    private fun game(status: String, winner: String? = null) = LichessGame(
        status, false, "standard", "blitz", null, status, winner,
        Players(Player(User("Alice", "alice"), null, null), Player(User(status, status), null, null)),
        null, null, null, null, null
    )

    private val games = listOf(game("started"), game("unknown"), game("draw"), game("mate", "white"))

    private fun texts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        return listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { texts(node.getChild(it)) }
    }

    @Test fun retrieval_rows_show_dashes_for_missing_results_and_keep_completed_results() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (screen in 0..2) {
                scenario.onActivity { activity ->
                    activity.setContent {
                        EvalTheme {
                            when (screen) {
                                0 -> GameSelectionScreen(games, "Alice", ChessServer.LICHESS, {}, {})
                                1 -> SelectedRetrieveGamesScreen(
                                    RetrievedGamesEntry("Alice", ChessServer.LICHESS), games, 0, false, false,
                                    null, {}, {}, {}, {}
                                )
                                else -> Column {
                                    games.forEach { TournamentGameRow(it, {}) }
                                    PgnGameRow(game("*"), {})
                                }
                            }
                        }
                    }
                }
                val deadline = SystemClock.uptimeMillis() + 10000
                var visible: List<String>
                do {
                    instrumentation.waitForIdleSync()
                    visible = texts(instrumentation.uiAutomation.rootInActiveWindow)
                    val expectedDashes = if (screen == 2) 3 else 2
                    if (visible.count { it == "-" } == expectedDashes &&
                        (if (screen == 2) "½-½" else "draw") in visible &&
                        (if (screen == 2) "1-0" else "win") in visible) break
                    SystemClock.sleep(50)
                } while (SystemClock.uptimeMillis() < deadline)
                assertEquals("screen=$screen: $visible", if (screen == 2) 3 else 2, visible.count { it == "-" })
                assertTrue(visible.toString(), (if (screen == 2) "½-½" else "draw") in visible)
                assertTrue(visible.toString(), (if (screen == 2) "1-0" else "win") in visible)
            }
        }
    }
}
