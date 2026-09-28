package com.eval.ui

import com.eval.stockfish.StockfishEngine
import org.junit.Assert.assertTrue
import org.junit.Test

/** A default missing from its stepper list made "+" jump from 2.00 s down to 0.50 s. */
class StockfishStepperOptionsTest {
    private val defaults = StockfishSettings()

    @Test fun every_default_is_one_of_its_stepper_options() {
        val o = StockfishStepperOptions
        assertTrue(defaults.previewStage.secondsForMove in o.previewSeconds)
        assertTrue(defaults.previewStage.hashMb in o.previewHash)
        assertTrue(defaults.analyseStage.secondsForMove in o.analyseSeconds)
        assertTrue(defaults.analyseStage.hashMb in o.analyseHash)
        assertTrue(defaults.manualStage.depth in o.manualDepth)
        assertTrue(defaults.manualStage.hashMb in o.manualHash)
        assertTrue(defaults.manualStage.multiPv in o.manualMultiPv)
        assertTrue(defaults.movesListForAi.secondsForMove in o.aiSeconds)
        assertTrue(defaults.movesListForAi.hashMb in o.aiHash)
        assertTrue(defaults.engineMovesForAi.secondsForPosition in o.aiEngineSeconds)
        assertTrue(defaults.engineMovesForAi.hashMb in o.aiHash)
    }

    @Test fun thread_and_hash_choices_never_exceed_what_the_engine_uses() {
        val o = StockfishStepperOptions
        assertTrue(o.threads().all { it <= StockfishEngine.maxUsableThreads() })
        assertTrue((o.analyseHash + o.manualHash + o.aiHash + o.previewHash).all { it <= StockfishEngine.MAX_SAFE_HASH_MB })
    }
}
