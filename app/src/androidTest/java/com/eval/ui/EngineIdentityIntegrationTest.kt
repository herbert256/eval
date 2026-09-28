package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.data.AppSignerTrust
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.EngineHistory
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineIdentityIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun reads_the_installed_engine_identity_again_after_restart() = runBlocking {
        val engine = StockfishEngine(context)
        assertNull(engine.engineName.value)
        try {
            assertTrue(engine.initialize())
            val name = engine.engineName.value
            assertNotNull(name)
            assertTrue("Reported name: $name", name!!.startsWith("Stockfish "))
            assertTrue(engine.restart())
            assertEquals(name, engine.engineName.value)
        } finally {
            engine.shutdown()
        }
        assertNull(engine.engineName.value)
    }

    @Test fun a_re_signed_stockfish_is_not_started_by_any_start_path_until_trusted() = runBlocking {
        val trust = AppSignerTrust.create(context)
        assertEquals(AppSignerTrust.Status.TRUSTED, trust.check(AppSignerTrust.STOCKFISH_PACKAGE))
        val prefs = context.getSharedPreferences(AppSignerTrust.PREFS_NAME, Context.MODE_PRIVATE)
        val key = "signer_" + AppSignerTrust.STOCKFISH_PACKAGE
        val pinned = prefs.getStringSet(key, null)!!.toSet()
        val engine = StockfishEngine(context)
        try {
            // Someone else's certificate was trusted before: the installed package is "re-signed".
            prefs.edit().putStringSet(key, setOf("0".repeat(64))).commit()
            assertFalse(engine.initialize())
            assertTrue(engine.signerChanged.value)
            assertFalse(engine.isReady.value)
            assertFalse(engine.restart())
            assertTrue(engine.unavailableMessage(), engine.unavailableMessage().contains("different developer"))

            trust.trustCurrent(AppSignerTrust.STOCKFISH_PACKAGE)
            assertTrue(engine.restart())
            assertFalse(engine.signerChanged.value)
        } finally {
            engine.shutdown()
            prefs.edit().putStringSet(key, pinned).commit()
        }
    }

    @Test fun only_advertised_options_are_used_and_a_rejected_position_is_reported() = runBlocking {
        val engine = StockfishEngine(context)
        try {
            assertTrue(engine.initialize())
            assertTrue(engine.supportsOption("Threads") && engine.supportsOption("multipv"))
            assertEquals(engine.supportsOption("Use NNUE"), engine.supportsNnueToggle.value)
            engine.configure(1, 8, 1, useNnue = false)
            // Black is in check with White to move: Stockfish 19 prints CRITICAL ERROR and exits.
            val illegal = "4k3/4Q3/8/8/8/8/8/4K3 w - - 0 1"
            engine.analyzeWithTime(illegal, 200)
            val error = withTimeout(10000) { engine.lastError.filterNotNull().first() }
            assertEquals(illegal, error.fen)
            assertTrue(error.message, error.message.isNotBlank())
            withTimeout(5000) { engine.isReady.first { !it } }
            assertTrue(engine.restart())
            assertEquals("Restarting keeps the error for the caller", error, engine.lastError.value)
            engine.configure(1, 8, 1)
            engine.analyzeWithTime(ChessBoard().getFen(), 200)
            assertTrue(engine.waitForCompletion(20000))
            assertNull(engine.lastError.value)
            assertEquals(ChessBoard().getFen(), engine.analysisResult.value?.fen)
        } finally {
            engine.shutdown()
        }
    }

    @Test fun navigating_mid_search_never_shows_the_previous_positions_lines() = runBlocking {
        // A move-list tap in Manual analyses the new position while the old search still runs.
        val positions = listOf(
            ChessBoard().getFen(),
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"
        )
        val engine = StockfishEngine(context)
        try {
            assertTrue(engine.initialize())
            engine.configure(2, 16, 3)
            repeat(10) { trial ->
                val old = positions[trial % 3]
                val new = positions[(trial + 1) % 3]
                engine.analyze(old, 40)
                withTimeout(15000) { engine.analysisResult.first { it?.fen == old && it.depth >= 10 } }
                engine.analyze(new, 40)
                val seen = mutableListOf<AnalysisResult>()
                withTimeoutOrNull<Unit>(2000) { engine.analysisResult.collect { if (it?.fen == new) seen += it } }
                assertTrue("Trial $trial: no result for the new position", seen.isNotEmpty())
                for (result in seen) for (line in result.lines) {
                    val board = ChessBoard().apply { assertTrue(setFen(new)) }
                    line.pv.split(' ').filter { it.isNotBlank() }.forEach {
                        assertTrue("Trial $trial: ${line.pv} is not legal from $new", board.makeUciMove(it))
                    }
                }
                assertTrue("Trial $trial: the new position's analysis stopped updating: ${seen.map { it.depth }}",
                    seen.last().depth > seen.first().depth)
            }
        } finally {
            engine.shutdown()
        }
    }

    @Test fun move_history_lets_the_engine_see_a_threefold_repetition() = runBlocking {
        // White is a queen up, but Black to move can repeat the start position a third time.
        val start = "6nk/8/8/8/8/8/Q7/1N5K w - - 0 1"
        val moves = listOf("b1c3", "g8f6", "c3b1", "f6g8", "b1c3", "g8f6", "c3b1")
        val board = ChessBoard().apply { assertTrue(setFen(start)) }
        moves.forEach { assertTrue(it, board.makeUciMove(it)) }
        val fen = board.getFen()
        val engine = StockfishEngine(context)
        try {
            assertTrue(engine.initialize())
            engine.configure(1, 16, 1)
            engine.analyzeWithTime(fen, 1000)
            assertTrue(engine.waitForCompletion(20000))
            val without = requireNotNull(engine.analysisResult.value)
            engine.analyzeWithTime(fen, 1000, EngineHistory(start, moves))
            assertTrue(engine.waitForCompletion(20000))
            val with = requireNotNull(engine.analysisResult.value)
            assertTrue("Without history Black is lost: ${without.score}", without.score < -3)
            assertEquals("Results stay tagged with the analysed position", fen, with.fen)
            assertEquals("f6g8", with.bestMove)
            assertEquals(0f, with.score)
        } finally {
            engine.shutdown()
        }
    }
}
