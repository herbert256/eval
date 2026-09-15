package com.eval.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.chess.ChessBoard
import com.eval.export.GifExporter
import com.eval.export.HtmlReportBuilder
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.PvLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MateZeroRenderingTest {
    @Test fun both_graph_layers_end_on_the_winning_side_at_checkmate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (whiteWins in listOf(true, false)) for (analyse in listOf(false, true)) {
            val sign = if (whiteWins) 1 else -1
            val scores = mapOf(0 to MoveScore(sign * 100f, true, sign), 1 to MoveScore(sign * 100f, true, 0))
            val bounds = AtomicReference(Rect.Zero)
            val settings = GraphSettings()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EvaluationGraph(if (analyse) emptyMap() else scores, if (analyse) scores else emptyMap(),
                                emptyMap(), 2, -1, AnalysisStage.MANUAL, false, settings, {},
                                Modifier.size(280.dp, 200.dp).onGloballyPositioned { bounds.set(it.boundsInWindow()) })
                        }
                    }
                }
                val deadline = SystemClock.uptimeMillis() + 10000
                while (bounds.get().width == 0f && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                instrumentation.waitForIdleSync()
                assertTrue(bounds.get().width > 0f)
                val rect = bounds.get()
                val x = (rect.left + rect.width * 0.8f).toInt()
                val winningY = (rect.top + rect.height * if (whiteWins) 0.25f else 0.75f).toInt()
                val losingY = (rect.top + rect.height * if (whiteWins) 0.75f else 0.25f).toInt()
                val color = (if (whiteWins) settings.plusScoreColor else settings.negativeScoreColor).toInt()
                // Compose layout can finish before its frame reaches the display. Wait for
                // the rendered graph instead of sampling the previous screen's pixels.
                val renderDeadline = SystemClock.uptimeMillis() + 5000
                var screenshot = instrumentation.uiAutomation.takeScreenshot()
                while ((screenshot.getPixel(x, winningY) != color ||
                        screenshot.getPixel(x, losingY) != settings.backgroundColor.toInt()) &&
                    SystemClock.uptimeMillis() < renderDeadline) {
                    screenshot.recycle()
                    SystemClock.sleep(50)
                    screenshot = instrumentation.uiAutomation.takeScreenshot()
                }
                try {
                    assertEquals("Graph layer analyse=$analyse whiteWins=$whiteWins", color, screenshot.getPixel(x, winningY))
                    assertEquals(settings.backgroundColor.toInt(), screenshot.getPixel(x, losingY))
                } finally { screenshot.recycle() }
            }
        }
    }

    @Test fun html_graphs_and_live_scores_keep_mate_zero_perspective() {
        for (whiteWins in listOf(true, false)) {
            val board = ChessBoard().apply {
                check(setFen(if (whiteWins) "7k/6Q1/6K1/8/8/8/8/8 b - - 1 1"
                    else "8/8/8/8/8/6k1/6q1/7K w - - 1 2"))
            }
            val score = MoveScore(if (whiteWins) 100f else -100f, true, 0)
            val state = GameUiState(currentBoard = board, previewScores = mapOf(1 to score),
                analyseScores = mapOf(1 to score), analysisResult = AnalysisResult(0, 0, 0,
                    listOf(PvLine(-100f, true, 0, "", 1)), board.getFen()))
            val html = HtmlReportBuilder.convertMarkdownToHtml("Test", "", state, "test")
            val graphValue = if (whiteWins) "10.0" else "-10.0"
            assertEquals(2, Regex(Regex.escape("\"move\":1,\"score\":$graphValue")).findAll(html).count())
            assertTrue(html.contains("pv-score ${if (whiteWins) "positive" else "negative"}\">${score.formatDisplay()}"))
        }
    }

    @Test fun exported_gif_bar_keeps_the_winner_at_mate_zero() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (whiteWins in listOf(true, false)) {
            val file = GifExporter.exportAsGif(context, listOf(ChessBoard()),
                mapOf(0 to MoveScore(if (whiteWins) 100f else -100f, true, 0)))
            try {
                val movie = requireNotNull(Movie.decodeFile(file.absolutePath))
                val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
                try {
                    movie.setTime(0)
                    movie.draw(Canvas(bitmap), 0f, 0f)
                    val color = bitmap.getPixel(movie.width() - 5, 20)
                    val channel = android.graphics.Color.red(color)
                    assertTrue("GIF whiteWins=$whiteWins bar channel=$channel", if (whiteWins) channel > 240 else channel < 80)
                } finally { bitmap.recycle() }
            } finally { file.delete() }
        }
    }
}
