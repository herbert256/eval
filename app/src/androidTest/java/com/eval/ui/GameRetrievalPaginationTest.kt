package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.data.ChessRepository
import com.eval.data.ChessServer
import com.eval.data.LichessApi
import com.eval.stockfish.StockfishEngine
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class GameRetrievalPaginationTest {
    private class Harness(scope: CoroutineScope, total: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("retrieval_pagination_test", Context.MODE_PRIVATE)
        var state = GameUiState()
        var failNextRequest = false
        val requests = mutableListOf<Int>()
        private val api = Proxy.newProxyInstance(LichessApi::class.java.classLoader, arrayOf(LichessApi::class.java)) { _, method, args ->
            check(method.name == "getGames")
            val max = args!![1] as Int
            requests.add(max)
            if (failNextRequest) {
                failNextRequest = false
                Response.error<String>(503, "Temporarily unavailable".toResponseBody())
            } else {
                Response.success((1..minOf(max, total)).joinToString("\n") { id ->
                    """{"id":"game-$id","rated":true,"variant":"standard","speed":"blitz","status":"mate","players":{"white":{"user":{"id":"tester","name":"tester"}},"black":{"user":{"id":"opponent","name":"opponent"}}}}"""
                })
            }
        } as LichessApi
        private val history = mutableListOf<ChessBoard>()
        private val orchestrator = AnalysisOrchestrator(
            StockfishEngine(context), { state }, { state = it(state) }, scope, { history }
        )
        val loader = GameLoader(
            ChessRepository(lichessApi = api), { state }, { state = it(state) }, scope,
            { history }, { mutableListOf() }, SettingsPreferences(prefs),
            GameStorageManager(prefs, Gson()), orchestrator
        )

        init { prefs.edit().clear().commit() }
        fun close() { prefs.edit().clear().commit() }
        fun visibleIds(pageSize: Int) = state.selectedRetrieveGames
            .drop(state.gameSelectionPage * pageSize).take(pageSize).map { it.id }
    }

    private suspend fun CoroutineScope.settle() {
        coroutineContext[Job]!!.children.toList().joinAll()
    }

    @Test fun every_game_is_reachable_when_batch_and_page_sizes_differ() = runBlocking {
        val h = Harness(this, 70)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "tester")
            settle()
            val seen = h.visibleIds(19).toMutableList()
            repeat(3) {
                h.loader.nextGameSelectionPage(19)
                settle()
                seen.addAll(h.visibleIds(19))
            }
            assertEquals((1..70).map { "game-$it" }, seen)
            assertEquals(listOf(25, 38, 57, 76), h.requests)
            assertFalse(h.state.gameSelectionHasMore)
        } finally { h.close() }
    }

    @Test fun final_partial_page_is_shown_even_when_no_more_games_are_returned() = runBlocking {
        val h = Harness(this, 25)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "tester")
            settle()
            h.loader.nextGameSelectionPage(19)
            settle()
            assertEquals((20..25).map { "game-$it" }, h.visibleIds(19))
            assertEquals(1, h.state.gameSelectionPage)
            assertFalse(h.state.gameSelectionHasMore)
        } finally { h.close() }
    }

    @Test fun failed_next_page_keeps_existing_games_and_can_be_retried() = runBlocking {
        val h = Harness(this, 70)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "tester")
            settle()
            h.failNextRequest = true
            h.loader.nextGameSelectionPage(19)
            settle()
            assertEquals(0, h.state.gameSelectionPage)
            assertEquals(25, h.state.selectedRetrieveGames.size)
            assertTrue(h.state.gameSelectionHasMore)
            assertTrue(h.state.errorMessage.orEmpty().contains("503"))
            h.loader.nextGameSelectionPage(19)
            settle()
            assertEquals((20..38).map { "game-$it" }, h.visibleIds(19))
            assertNull(h.state.errorMessage)
        } finally { h.close() }
    }

    @Test fun switching_to_a_saved_retrieve_starts_on_its_first_page() = runBlocking {
        val h = Harness(this, 70)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "tester")
            settle()
            val storage = GameStorageManager(h.prefs, Gson())
            storage.storeRetrievedGames(h.state.selectedRetrieveGames.take(2), "short-list", ChessServer.LICHESS)
            h.loader.nextGameSelectionPage(19)
            settle()
            assertEquals(1, h.state.gameSelectionPage)
            h.loader.selectPreviousRetrieve(RetrievedGamesEntry("short-list", ChessServer.LICHESS))
            assertEquals(listOf("game-1", "game-2"), h.visibleIds(19))
            assertEquals(0, h.state.gameSelectionPage)
            assertFalse(h.state.gameSelectionHasMore)
        } finally { h.close() }
    }

    @Test fun reopening_the_only_saved_retrieve_starts_on_its_first_page() = runBlocking {
        val h = Harness(this, 25)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "tester")
            settle()
            h.loader.nextGameSelectionPage(19)
            settle()
            h.loader.dismissSelectedRetrieveGames()
            h.loader.showPreviousRetrieves()
            assertEquals(0, h.state.gameSelectionPage)
            assertEquals((1..19).map { "game-$it" }, h.visibleIds(19))
        } finally { h.close() }
    }

    @Test fun an_old_page_request_cannot_replace_a_newly_selected_saved_list() = runBlocking {
        val h = Harness(this, 70)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "tester")
            settle()
            val storage = GameStorageManager(h.prefs, Gson())
            storage.storeRetrievedGames(h.state.selectedRetrieveGames.take(2), "short-list", ChessServer.LICHESS)
            h.loader.nextGameSelectionPage(19)
            assertTrue(h.state.gameSelectionLoading)
            h.loader.selectPreviousRetrieve(RetrievedGamesEntry("short-list", ChessServer.LICHESS))
            settle()
            assertEquals("short-list", h.state.selectedRetrieveEntry?.accountName)
            assertEquals(listOf("game-1", "game-2"), h.visibleIds(19))
            assertFalse(h.state.gameSelectionLoading)
            assertFalse(h.state.gameSelectionHasMore)
        } finally { h.close() }
    }
}
