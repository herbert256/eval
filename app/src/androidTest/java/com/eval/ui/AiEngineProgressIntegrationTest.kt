package com.eval.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.chess.ChessBoard
import com.eval.ui.theme.EvalTheme
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiEngineProgressIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fen = ChessBoard().getFen()

    @Test fun normal_completion_reports_timed_progress_and_the_same_complete_lines_as_the_payload() = runBlocking {
        val progress = CopyOnWriteArrayList<AiEngineProgress>()
        val value = AiEngineLines(context).generate(fen, AiEngineSettings(secondsForPosition = 1f, hashMb = 8),
            onProgress = { progress.add(it) })
        assertTrue(progress.any { !it.searching })
        assertTrue(progress.any { it.searching && it.fraction > 0f })
        assertTrue(progress.all { it.fraction in 0f..1f })
        val result = requireNotNull(progress.last().result)
        assertEquals(3, result.lines.size)
        assertEquals(formatAiEngineLines(fen, result), value)
    }

    @Test fun stopping_before_the_first_iteration_returns_promptly_with_an_explicit_empty_result() = runBlocking {
        val stop = CompletableDeferred<Unit>().apply { complete(Unit) }
        val value = withTimeout(5000) {
            AiEngineLines(context).generate(fen, AiEngineSettings(secondsForPosition = 60f, hashMb = 8), stop)
        }
        assertEquals("Stockfish search stopped before a complete set of lines was available.", value)
    }

    @Test fun live_card_stop_button_sends_complete_lines_once_without_waiting_for_the_time_limit() {
        exerciseScreen(stop = true)
    }

    @Test fun cancel_button_discards_the_request_without_opening_ai() {
        exerciseScreen(stop = false)
    }

    @Test fun unused_engine_placeholders_skip_both_searches_and_send_no_context() {
        exerciseScreen(stop = false, needsEngine = false)
    }

    private fun exerciseScreen(stop: Boolean, needsEngine: Boolean = true) {
        val sent = CopyOnWriteArrayList<Intent>()
        val receiver = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { sent.add(intent) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            scenario.onActivity { activity -> vm = ViewModelProvider(activity)[GameViewModel::class.java] }
            // The real game entry point is in Manual mode, after its engine is ready.
            // Avoid starting a second cold engine during the activity's own UCI handshake.
            if (needsEngine) waitUntil(45000) { vm.uiState.value.stockfishReady }
            scenario.onActivity { activity ->
                vm.dismissAiInstructionSelection()
                // Only change this fixture's runtime state; preserve the user's saved settings and game.
                val field = GameViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val state = field.get(vm) as MutableStateFlow<GameUiState>
                val data = AiReportContext("Progress test", fen = fen)
                state.value = state.value.copy(pendingAiReport = data, stockfishSettings = state.value.stockfishSettings.copy(
                    movesListForAi = AiMovesSettings(secondsForMove = if (needsEngine) 0.05f else 2f, hashMb = 8),
                    engineMovesForAi = AiEngineSettings(secondsForPosition = 60f, hashMb = 8)
                ))
                activity.setContent {
                    val ui by vm.uiState.collectAsState()
                    EvalTheme {
                        AiReportProgressScreen(vm::dismissAiInstructionSelection,
                            progress = ui.aiMovesProgress, error = ui.aiReportError,
                            engineProgress = ui.aiEngineProgress, stopping = ui.aiEngineStopping,
                            onStopAndContinue = vm::stopAiEngineAndContinue)
                    }
                }
                vm.editAiReport()
                vm.updateAiReportDraft(AiReportDraft(prompt = if (needsEngine) "Explain @ENGINE@" else "Literal question"))
                vm.submitAiReport(receiver)
            }
            if (!needsEngine) {
                waitUntil(5000) { vm.uiState.value.pendingAiReport == null }
                assertNull(vm.uiState.value.aiEngineProgress)
                assertNull(vm.uiState.value.aiReportError)
                assertEquals(1, sent.size)
                assertEquals("<prompt>Literal question</prompt>", sent.single().getStringExtra("instructions"))
                return@use
            }
            // Cold app startup also restores the current game and initializes its engine.
            waitUntil(45000) { vm.uiState.value.aiReportError != null || vm.uiState.value.pendingAiReport == null ||
                vm.uiState.value.aiEngineProgress?.let { it.result != null && it.elapsedMs >= 500 } == true }
            assertNull(vm.uiState.value.aiReportError, vm.uiState.value.aiReportError)
            assertNotNull("Request ended before a complete live result; sent=${sent.size}", vm.uiState.value.aiEngineProgress?.result)
            assertTrue(vm.uiState.value.aiEngineProgress!!.fraction > 0f)
            waitUntil(5000) {
                val texts = nodes(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }
                texts.any { it.startsWith("Depth:") } && "Stop and go to AI" in texts && "Best 3 Stockfish lines" in texts
            }
            if (stop) {
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(context.cacheDir, "ai-engine-progress.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            val started = SystemClock.elapsedRealtime()
            clickText(if (stop) "Stop and go to AI" else "Cancel")
            waitUntil(5000) { vm.uiState.value.pendingAiReport == null }
            assertTrue(SystemClock.elapsedRealtime() - started < 5000)
            assertNull(vm.uiState.value.aiReportError)
            assertNull(vm.uiState.value.aiEngineProgress)
            if (stop) {
                assertEquals(1, sent.size)
                assertEquals("com.ai.ACTION_NEW_REPORT", sent.single().action)
                val payload = requireNotNull(sent.single().getStringExtra("instructions"))
                assertFalse("Unrequested FEN must not be sent", payload.contains("<fen>"))
                assertFalse("Unrequested moves must not be sent", payload.contains("<moves>"))
                val engine = payload.substringAfter("<engine>").substringBefore("</engine>")
                assertTrue(engine, engine.startsWith("Top 3 Stockfish lines"))
                val rows = engine.lines().drop(1)
                assertEquals(3, rows.size)
                assertEquals(1, rows.map { it.substringAfter("(depth ").substringBefore(")") }.distinct().size)
                rows.forEach { row ->
                    val board = ChessBoard()
                    row.substringAfter("[UCI: ").removeSuffix("]").split(' ').forEach {
                        assertTrue(row, board.makeUciMove(it))
                    }
                }
                scenario.onActivity { vm.stopAiEngineAndContinue() }
                assertEquals(1, sent.size)
            } else {
                SystemClock.sleep(500)
                assertTrue("Cancel must not launch AI", sent.isEmpty())
            }
        }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    private fun clickText(text: String) {
        var node = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.toString() == text }
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Condition did not become true within $timeoutMs ms", condition())
    }
}
