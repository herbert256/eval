package com.eval.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMovesIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fast = AiMovesSettings(secondsForMove = 0.05f, hashMb = 8)

    @Test fun every_starting_move_is_evaluated_and_sent_once_without_expanding_templates() = runBlocking {
        val fen = ChessBoard().getFen()
        val progress = mutableListOf<Pair<Int, Int>>()
        val value = AiMovesList(context).generate(fen, fast) { done, total -> progress += done to total }
        val rows = value.lines().drop(1)
        assertEquals(20, rows.size)
        val found = rows.map { Regex("\\(([a-h][1-8][a-h][1-8][qrbn]?)\\)").find(it)!!.groupValues[1] }.toSet()
        assertEquals(legalAiMoves(ChessBoard()).map { it.uci }.toSet(), found)
        assertTrue(rows.all { it.contains(Regex(": [+-](\\d+\\.\\d{2}|M\\d+) \\(depth [1-9]\\d*\\)")) })
        assertEquals((0..20).map { it to 20 }, progress)
        var sent: Intent? = null
        val receiver = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { sent = intent }
        }
        val template = "<system>Explain @MOVES@ and @moves@</system><moves>old</moves>"
        assertTrue(AiAppLauncher.launchAiReport(receiver, AiInstructionEntry(instructions = template),
            AiAppLauncher.gameContext(fen).copy(moves = value)))
        val payload = requireNotNull(sent?.getStringExtra("instructions"))
        assertTrue(payload.startsWith("<system>Explain @MOVES@ and @moves@</system>"))
        assertEquals(1, Regex("<moves>").findAll(payload).count())
        assertTrue(payload.contains("<moves>${value.replace("'", "&#39;")}</moves>"))
    }

    @Test fun promotions_are_individually_evaluated_and_black_mate_has_negative_white_score() = runBlocking {
        val promotionFen = "7k/P7/8/8/8/8/8/7K w - - 0 1"
        val value = AiMovesList(context).generate(promotionFen, fast)
        for (suffix in listOf("q", "r", "b", "n")) assertTrue(value, value.contains("(a7a8$suffix):"))
        val engine = StockfishEngine(context)
        try {
            assertTrue(engine.initialize())
            engine.configure(1, 8, 1)
            val result = engine.evaluateMove("8/8/8/8/8/5kq1/8/7K b - - 0 1", "g3g2", 100)
            assertTrue(result.bestLine!!.isMate)
            assertEquals("-M1", aiMoveScore(result.bestLine!!, false))
        } finally { engine.shutdown() }
    }

    @Test fun empty_and_terminal_contexts_need_no_search_and_incomplete_lists_are_cancellable() = runBlocking {
        val generator = AiMovesList(context)
        assertEquals("", generator.generate("", fast))
        assertEquals("No legal moves in this position.", generator.generate("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", fast))
        assertEquals("No legal moves in this position.", generator.generate("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", fast))
        val firstMove = CompletableDeferred<Unit>()
        var returned = false
        val job = launch {
            generator.generate(ChessBoard().getFen(), fast.copy(secondsForMove = 2f)) { done, _ ->
                if (done == 1) firstMove.complete(Unit)
            }
            returned = true
        }
        withTimeout(15000) { firstMove.await() }
        withTimeout(5000) { job.cancelAndJoin() }
        assertFalse("A cancelled list must never be handed off", returned)
        // A fresh request remains usable after cancelling an active engine search.
        assertTrue(generator.generate("7k/P7/8/8/8/8/8/7K w - - 0 1", fast).contains("a7a8n"))
    }

    @Test fun moves_settings_survive_restart_export_import_and_old_exports_get_defaults() {
        val name = "ai_moves_settings_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            assertEquals(AiMovesSettings(), settings.loadStockfishSettings().movesListForAi)
            val custom = AiMovesSettings(1f, 2, 64, false)
            settings.saveStockfishSettings(StockfishSettings(movesListForAi = custom))
            assertEquals(custom, SettingsPreferences(prefs).loadStockfishSettings().movesListForAi)
            val exported = settings.exportAllSettings()
            for (schema in 2..3) {
                assertTrue(settings.importAllSettings("""{"schemaVersion":$schema,"stockfishSettings":{"manualStage":{"depth":28}}}"""))
                assertEquals(AiMovesSettings(), settings.loadStockfishSettings().movesListForAi)
                assertEquals(28, settings.loadStockfishSettings().manualStage.depth)
            }
            assertTrue(settings.importAllSettings(exported))
            assertEquals(custom, settings.loadStockfishSettings().movesListForAi)
            for (field in listOf("\"secondsForMove\":0", "\"threads\":5", "\"hashMb\":512", "\"useNnue\":\"true\"")) {
                assertFalse(settings.importAllSettings("""{"schemaVersion":3,"stockfishSettings":{"movesListForAi":{$field}}}"""))
                assertEquals(custom, settings.loadStockfishSettings().movesListForAi)
            }
            assertTrue(settings.importAllSettings("""{"ai_moves_seconds":{"_type":"Float","_value":0.5}}"""))
            assertEquals(0.5f, settings.loadStockfishSettings().movesListForAi.secondsForMove)
            settings.resetAllSettingsToDefaults()
            assertEquals(AiMovesSettings(), settings.loadStockfishSettings().movesListForAi)
        } finally { context.deleteSharedPreferences(name) }
    }
}
