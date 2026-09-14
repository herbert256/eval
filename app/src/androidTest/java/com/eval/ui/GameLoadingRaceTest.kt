package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.data.ChessRepository
import com.eval.data.ChessServer
import com.eval.data.LichessApi
import com.eval.data.Result
import com.eval.stockfish.StockfishEngine
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class GameLoadingRaceTest {
    private val localPgn = "[Event \"Local\"]\n[White \"Local White\"]\n[Black \"Local Black\"]\n\n1. d4 d5 *"

    private class Harness(scope: CoroutineScope) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("game_loading_race_test", Context.MODE_PRIVATE)
        val settings = SettingsPreferences(prefs)
        var state = GameUiState()
        var deferResponses = true
        val pending = Channel<Continuation<Response<String>>>(Channel.UNLIMITED)
        val remotePgn = "[Event \"Remote\"]\n[White \"Remote\"]\n[Black \"Opponent\"]\n\n1. e4 e5 *"
        fun response() = Response.success("""{"id":"remote","rated":true,"variant":"standard","speed":"blitz","status":"mate","players":{"white":{"user":{"id":"remote","name":"Remote"}},"black":{"user":{"id":"opponent","name":"Opponent"}}},"pgn":${Gson().toJson(remotePgn)}}""")
        private val api = Proxy.newProxyInstance(LichessApi::class.java.classLoader, arrayOf(LichessApi::class.java)) { _, method, args ->
            check(method.name == "getGames")
            if (deferResponses) {
                @Suppress("UNCHECKED_CAST")
                val continuation = args!!.last() as Continuation<Response<String>>
                check(pending.trySend(continuation).isSuccess)
                COROUTINE_SUSPENDED
            } else response()
        } as LichessApi
        val repository = ChessRepository(lichessApi = api)
        private val history = mutableListOf<ChessBoard>()
        private val engineScope = CoroutineScope(Job().apply { cancel() })
        private val orchestrator = AnalysisOrchestrator(
            StockfishEngine(context), { state }, { state = it(state) }, engineScope, { history }
        )
        val loader = GameLoader(repository, { state }, { state = it(state) }, scope,
            { history }, { mutableListOf() }, settings, GameStorageManager(prefs, Gson()), orchestrator)
        init { prefs.edit().clear().commit() }
        fun close() { prefs.edit().clear().commit() }
    }

    private suspend fun CoroutineScope.settle() { coroutineContext[Job]!!.children.toList().joinAll() }

    @Test fun downloaded_games_cannot_replace_a_new_local_game() = runBlocking {
        val h = Harness(this)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "remote")
            val request = withTimeout(5000) { h.pending.receive() }
            h.loader.loadGamesFromPgnContent(localPgn)
            val localId = h.state.game!!.id
            request.resume(h.response())
            settle()
            assertEquals(localId, h.state.game?.id)
            assertEquals(listOf("d4", "d5"), h.state.moves)
            assertFalse(h.state.isLoading)
        } finally { h.close() }
    }

    @Test fun last_game_response_cannot_reopen_a_cleared_game() = runBlocking {
        val h = Harness(this)
        try {
            val job = launch { h.loader.fetchLastGameFromServer(ChessServer.LICHESS, "remote") }
            val request = withTimeout(5000) { h.pending.receive() }
            h.loader.clearGame()
            request.resume(h.response())
            job.join()
            assertNull(h.state.game)
            assertTrue(h.state.showRetrieveScreen)
            assertFalse(h.state.isLoading)
        } finally { h.close() }
    }

    @Test fun queued_reload_cannot_overwrite_an_immediate_local_selection() = runBlocking {
        val h = Harness(this)
        try {
            h.deferResponses = false
            h.settings.saveLastServerUser("remote", "lichess.org")
            h.loader.reloadLastGame()
            h.loader.loadGamesFromPgnContent(localPgn)
            val localId = h.state.game!!.id
            settle()
            assertEquals(localId, h.state.game?.id)
        } finally { h.close() }
    }

    @Test fun an_imported_collection_remains_selected_after_an_older_download_finishes() = runBlocking {
        val h = Harness(this)
        try {
            h.loader.fetchGames(ChessServer.LICHESS, "remote")
            val request = withTimeout(5000) { h.pending.receive() }
            h.loader.loadGamesFromPgnContent("$localPgn\n\n${localPgn.replace("Local", "Second")}")
            request.resume(h.response())
            settle()
            assertTrue(h.state.showPgnEventSelection)
            assertFalse(h.state.isLoading)
            assertNull(h.state.game)
            assertEquals(2, h.state.pgnEvents.size)
        } finally { h.close() }
    }

    @Test fun the_latest_of_two_reload_requests_wins() = runBlocking {
        val h = Harness(this)
        try {
            val first = launch { h.loader.fetchLastGameFromServer(ChessServer.LICHESS, "first") }
            val firstResponse = withTimeout(5000) { h.pending.receive() }
            val second = launch { h.loader.fetchLastGameFromServer(ChessServer.LICHESS, "second") }
            val secondResponse = withTimeout(5000) { h.pending.receive() }
            secondResponse.resume(Response.success(h.response().body()!!.replace("remote", "newest")))
            second.join()
            firstResponse.resume(h.response())
            first.join()
            assertEquals("newest", h.state.game?.id)
        } finally { h.close() }
    }
}
