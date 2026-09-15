package com.eval.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineStartupLoadTest {
    @Test fun game_loaded_before_engine_readiness_is_eventually_analysed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val store = ViewModelStore()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: GameViewModel
        try {
            instrumentation.runOnMainSync {
                vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[GameViewModel::class.java]
                assertFalse("Fixture must load while Stockfish is still initializing", vm.uiState.value.stockfishReady)
                vm.loadGamesFromPgnContent("[Event \"Loaded during startup\"]\n\n1. e4 e5 *")
            }
            val deadline = System.currentTimeMillis() + 60000
            while (System.currentTimeMillis() < deadline) {
                val state = vm.uiState.value
                if (state.currentStage == AnalysisStage.MANUAL && state.analyseScores.size == 2 &&
                    state.stockfishReady && state.analysisResult != null &&
                    state.analysisResultFen == state.currentBoard.getFen()) break
                Thread.sleep(100)
            }
            val state = vm.uiState.value
            assertEquals("Stage: ${state.errorMessage}", AnalysisStage.MANUAL, state.currentStage)
            assertEquals(2, state.previewScores.size)
            assertEquals(2, state.analyseScores.size)
            assertEquals(listOf("e4", "e5"), state.moves)
            assertTrue(state.stockfishReady)
            assertTrue("Engine identity reaches the card state: ${state.stockfishName}",
                state.stockfishName.startsWith("Stockfish "))
            assertNotNull("Live evaluation after startup", state.analysisResult)
            assertEquals(state.currentBoard.getFen(), state.analysisResultFen)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            val editor = prefs.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        }
    }
}
