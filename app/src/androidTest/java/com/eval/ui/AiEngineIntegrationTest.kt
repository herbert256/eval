package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiEngineIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val quick = AiEngineSettings(secondsForPosition = 1f, hashMb = 8)

    @Test fun real_stockfish_returns_requested_ranked_legal_lines_up_to_the_available_moves() = runBlocking {
        val generator = AiEngineLines(context)
        for (requested in listOf(1, 3, 32)) {
            val value = generator.generate(ChessBoard().getFen(), quick.copy(multiPv = requested))
            val count = minOf(requested, 20)
            assertTrue(value, value.startsWith("Top $count Stockfish lines"))
            val rows = value.lines().drop(1)
            assertEquals(count, rows.size)
            val roots = rows.map { row ->
                val uci = row.substringAfter("[UCI: ").removeSuffix("]").split(' ')
                val board = ChessBoard()
                uci.forEach { assertTrue("Illegal PV: $row", board.makeUciMove(it)) }
                uci.first()
            }
            assertEquals(count, roots.distinct().size)
            val scores = rows.map { it.substringAfter(". ").substringBefore(" ").toFloat() }
            assertEquals(scores.sortedDescending(), scores)
            val payload = AiAppLauncher.buildInstructions("<system>@ENGINE@</system><engine>@ENGINE@</engine>",
                AiReportContext("Engine test", fen = ChessBoard().getFen(), engine = value))
            assertTrue(payload.startsWith("<system>@ENGINE@</system>"))
            assertEquals(1, Regex("<engine>").findAll(payload).count())
            assertTrue(payload.contains("<engine>${value.replace("'", "&#39;")}</engine>"))
        }
        val single = "8/8/8/8/8/5kr1/8/7K w - - 0 1"
        assertEquals(1, legalAiMoves(ChessBoard().apply { assertTrue(setFen(single)) }).size)
        assertTrue(generator.generate(single, quick.copy(multiPv = 32)).startsWith("Top 1 Stockfish lines"))
    }

    @Test fun black_mate_terminal_and_empty_contexts_are_reported_correctly() = runBlocking {
        val generator = AiEngineLines(context)
        val mate = generator.generate("8/8/8/8/8/5kq1/8/7K b - - 0 1", quick.copy(multiPv = 1))
        assertTrue(mate, mate.contains("best first for Black to move"))
        assertTrue(mate, mate.contains("1. -M1"))
        assertEquals("No legal moves in this position.", generator.generate("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", quick))
        assertEquals("", generator.generate("", quick))
    }

    @Test fun cancelled_search_never_returns_a_partial_set_and_a_new_search_still_works() = runBlocking {
        var returned = false
        val generator = AiEngineLines(context)
        val engine = StockfishEngine(context)
        try {
            assertTrue(engine.initialize())
            engine.configure(1, 8, 3)
            val job = launch {
                engine.evaluateLines(ChessBoard().getFen(), 3, 60000)
                returned = true
            }
            withTimeout(10000) { engine.analysisResult.filterNotNull().first() }
            withTimeout(5000) { job.cancelAndJoin() }
            assertFalse(returned)
        } finally {
            engine.shutdown()
        }
        assertTrue(generator.generate(ChessBoard().getFen(), quick).startsWith("Top 3 Stockfish lines"))
    }

    @Test fun engine_settings_persist_and_imports_validate_line_count_and_keep_old_defaults() {
        val name = "ai_engine_settings_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            assertEquals(AiEngineSettings(), settings.loadStockfishSettings().engineMovesForAi)
            val custom = AiEngineSettings(5f, 2, 64, 7, false)
            val moves = AiMovesSettings(0.5f, 2, 16, false)
            settings.saveStockfishSettings(StockfishSettings(movesListForAi = moves, engineMovesForAi = custom))
            assertEquals(custom, SettingsPreferences(prefs).loadStockfishSettings().engineMovesForAi)
            val exported = settings.exportAllSettings()
            for (schema in 2..3) {
                assertTrue(settings.importAllSettings("""{"schemaVersion":$schema,"stockfishSettings":{"movesListForAi":{"secondsForMove":0.5}}}"""))
                assertEquals(AiEngineSettings(), settings.loadStockfishSettings().engineMovesForAi)
                assertEquals(0.5f, settings.loadStockfishSettings().movesListForAi.secondsForMove)
            }
            assertTrue(settings.importAllSettings(exported))
            assertEquals(custom, settings.loadStockfishSettings().engineMovesForAi)
            assertEquals(moves, settings.loadStockfishSettings().movesListForAi)
            for (field in listOf("\"multiPv\":0", "\"multiPv\":33", "\"multiPv\":1.5", "\"secondsForPosition\":0", "\"threads\":5", "\"hashMb\":512", "\"useNnue\":\"true\"")) {
                assertFalse(settings.importAllSettings("""{"schemaVersion":3,"stockfishSettings":{"engineMovesForAi":{$field}}}"""))
                assertEquals(custom, settings.loadStockfishSettings().engineMovesForAi)
            }
            assertTrue(settings.importAllSettings("""{"ai_engine_lines":{"_type":"Int","_value":5}}"""))
            assertEquals(5, settings.loadStockfishSettings().engineMovesForAi.multiPv)
            settings.resetAllSettingsToDefaults()
            assertEquals(AiEngineSettings(), settings.loadStockfishSettings().engineMovesForAi)
        } finally { context.deleteSharedPreferences(name) }
    }
}
