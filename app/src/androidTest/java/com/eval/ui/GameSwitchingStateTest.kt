package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.data.ChessRepository
import com.eval.data.OpeningExplorerResponse
import com.eval.data.OpeningInfo
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.PvLine
import com.eval.stockfish.StockfishEngine
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameSwitchingStateTest {
    private val pgn = "[Event \"New game\"]\n[Opening \"New opening\"]\n[White \"New White\"]\n[Black \"New Black\"]\n\n1. d4 d5 *"
    private class Harness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("game_switching_state_test", Context.MODE_PRIVATE)
        val history = mutableListOf(ChessBoard())
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Main)
        var state = GameUiState(
            currentStage = AnalysisStage.MANUAL,
            analysisResult = AnalysisResult(24, 100, 100, listOf(PvLine(8f, false, 0, "e2e4", 1))),
            analysisResultFen = "previous position",
            currentOpeningName = "Previous opening",
            openingExplorerData = OpeningExplorerResponse(100, 30, 40, emptyList(), opening = OpeningInfo("B90", "Previous Sicilian")),
            openingExplorerLoading = true,
            moveQualities = mapOf(0 to MoveQuality.BLUNDER)
        )
        private val orchestrator = AnalysisOrchestrator(StockfishEngine(context), { state }, { state = it(state) }, scope, { history })
        val loader = GameLoader(ChessRepository(), { state }, { state = it(state) }, scope,
            { history }, { mutableListOf() }, SettingsPreferences(prefs), GameStorageManager(prefs, Gson()), orchestrator)
        init { prefs.edit().clear().commit() }
        fun close() { orchestrator.stop(); prefs.edit().clear().commit() }
    }

    @Test fun restoring_a_saved_game_does_not_reuse_the_previous_games_live_analysis() {
        val h = Harness()
        try {
            h.loader.loadAnalysedGameDirectly(AnalysedGame(1L, "New White", "New Black", "*", pgn,
                listOf("d4", "d5"), emptyList(), emptyMap(), emptyMap(), openingName = "New opening"))
            assertNull("Previous evaluation remains visible", h.state.analysisResult)
            assertNull(h.state.analysisResultFen)
            assertTrue(h.state.moveQualities.isEmpty())
            assertNull(h.state.currentOpeningName)
            assertNull(h.state.openingExplorerData)
            assertFalse(h.state.openingExplorerLoading)
            assertEquals("New opening", h.state.openingName)
        } finally { h.close() }
    }

    @Test fun a_new_pgn_does_not_keep_the_previous_opening_explorer_result() {
        val h = Harness()
        try {
            h.loader.loadGamesFromPgnContent(pgn)
            assertNull(h.state.currentOpeningName)
            assertNull(h.state.openingExplorerData)
            assertFalse(h.state.openingExplorerLoading)
            assertEquals("New opening", h.state.openingName)
        } finally { h.close() }
    }

    @Test fun clearing_a_game_clears_its_derived_position_data() {
        val h = Harness()
        try {
            h.loader.clearGame()
            assertNull(h.state.analysisResultFen)
            assertNull(h.state.currentOpeningName)
            assertNull(h.state.openingExplorerData)
            assertFalse(h.state.openingExplorerLoading)
            assertTrue(h.state.moveQualities.isEmpty())
        } finally { h.close() }
    }
}
