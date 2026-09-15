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
        val storage = GameStorageManager(prefs, Gson())
        val loader = GameLoader(ChessRepository(), { state }, { state = it(state) }, scope,
            { history }, { mutableListOf() }, SettingsPreferences(prefs), storage, orchestrator)
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

    @Test fun saved_draws_without_player_names_remain_draws_when_reopened() {
        val h = Harness()
        try {
            for (header in listOf("[Result \"1/2-1/2\"]\n", "")) {
                val pgn = header + "1. e4 e5 1/2-1/2"
                h.loader.loadAnalysedGameDirectly(saved(pgn, result = "1/2-1/2"))
                assertEquals("draw", h.state.game!!.status)
                assertNull(h.state.game!!.winner)
            }
            // Legacy opening studies sometimes stored a draw despite explicit ongoing PGN.
            h.loader.loadAnalysedGameDirectly(saved("1. e4 e5 *", result = "1/2-1/2"))
            assertEquals("*", h.state.game!!.status)
        } finally { h.close() }
    }

    @Test fun restored_player_ratings_survive_display_and_pgn_export() {
        val h = Harness()
        try {
            val pgn = "[WhiteElo \"2410\"]\n[BlackElo \"2385\"]\n1. e4 e5 *"
            h.loader.loadAnalysedGameDirectly(saved(pgn))
            val game = h.state.game!!
            assertEquals(2410, game.players.white.rating)
            assertEquals(2385, game.players.black.rating)
            val exported = com.eval.export.PgnExporter.exportAnnotatedPgn(game, h.state.moveDetails,
                emptyMap(), emptyMap(), null)
            val headers = com.eval.chess.PgnParser.parseHeaders(exported)
            assertEquals("2410", headers["WhiteElo"])
            assertEquals("2385", headers["BlackElo"])
        } finally { h.close() }
    }

    @Test fun reopening_a_saved_game_updates_the_next_startup_game() {
        val h = Harness()
        try {
            val first = saved("1. e4 e5 *")
            val selected = saved("1. d4 d5 *").copy(timestamp = 2L)
            h.storage.saveManualStageGame(first)
            h.loader.loadAnalysedGameDirectly(selected)
            assertEquals(selected.pgn, h.storage.loadManualStageGame()!!.pgn)
            h.loader.loadAnalysedGameDirectly(saved("[FEN \"invalid\"]\n*"))
            assertEquals("Rejected restore must not replace the saved game", selected.pgn,
                h.storage.loadManualStageGame()!!.pgn)
        } finally { h.close() }
    }

    @Test fun restored_moves_and_clocks_are_rebuilt_when_cached_details_are_missing() {
        val h = Harness()
        try {
            val pgn = "1. e4 {[%clk 0:05:00]} e5 2. Nf3 *"
            h.loader.loadAnalysedGameDirectly(saved(pgn))
            assertEquals(h.state.moves, h.state.moveDetails.map { it.san })
            assertEquals("0:05:00", h.state.moveDetails.first().clockTime)
            assertEquals("g1", h.state.moveDetails.last().from)
            assertEquals("f3", h.state.moveDetails.last().to)
            val exported = com.eval.export.PgnExporter.exportAnnotatedPgn(h.state.game!!, h.state.moveDetails,
                emptyMap(), emptyMap(), null)
            assertEquals(listOf("e4", "e5", "Nf3"), com.eval.chess.PgnParser.parseMoves(exported))
        } finally { h.close() }
    }

    @Test fun restored_black_start_study_keeps_promotion_and_cached_clock_data() {
        val h = Harness()
        try {
            val fen = "4k3/8/8/8/8/8/1p6/4K3 b - - 0 17"
            val pgn = "[SetUp \"1\"]\n[FEN \"$fen\"]\n17... b1=N 18. Kf2 *"
            val cached = MoveDetails("b1=N", "b2", "b1", false, "N", "0:01:23")
            h.loader.loadAnalysedGameDirectly(saved(pgn).copy(moveDetails = listOf(cached)))
            assertEquals(2, h.state.moveDetails.size)
            assertEquals(cached, h.state.moveDetails.first())
            assertEquals("e1", h.state.moveDetails.last().from)
            assertEquals("f2", h.state.moveDetails.last().to)
            val exported = com.eval.export.PgnExporter.exportAnnotatedPgn(h.state.game!!,
                h.state.moveDetails, emptyMap(), emptyMap(), null)
            assertEquals(fen, com.eval.chess.PgnParser.parseHeaders(exported)["FEN"])
            assertTrue(exported.contains("17... b1=N"))
            assertEquals("0:01:23", com.eval.chess.PgnParser.parseMovesWithClock(exported).first().clockTime)
        } finally { h.close() }
    }

    private fun saved(pgn: String, result: String = "*") = AnalysedGame(
        1L, "White", "Black", result, pgn, com.eval.chess.PgnParser.parseMoves(pgn),
        emptyList(), emptyMap(), emptyMap())
}
