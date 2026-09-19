package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.data.ChessRepository
import com.eval.data.LichessApi
import com.eval.stockfish.StockfishEngine
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class StartupGameLoadingTest {
    private class Harness(scope: CoroutineScope) : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("startup_game_loading_test", Context.MODE_PRIVATE)
        val settings = SettingsPreferences(prefs)
        val storage = GameStorageManager(prefs, Gson())
        var state = GameUiState()
        val requests = CopyOnWriteArrayList<Pair<String, Int>>()
        var deferResponse = false
        val pending = CompletableDeferred<Continuation<Response<String>>>()
        val savedPgn = "[Event \"Saved game\"]\n[White \"Saved\"]\n[Black \"Opponent\"]\n\n1. d4 d5 *"
        val remotePgn = "[Event \"Latest game\"]\n[White \"LatestUser\"]\n[Black \"Opponent\"]\n\n1. e4 e5 *"
        var response: Response<String> = Response.success("""{"id":"latest","rated":true,"variant":"standard","speed":"blitz","status":"mate","players":{"white":{"user":{"id":"latestuser","name":"LatestUser"}},"black":{"user":{"id":"opponent","name":"Opponent"}}},"pgn":${Gson().toJson(remotePgn)}}""")
        private val api = Proxy.newProxyInstance(LichessApi::class.java.classLoader, arrayOf(LichessApi::class.java)) { _, method, args ->
            check(method.name == "getGames")
            requests.add(args!![0] as String to args[1] as Int)
            if (deferResponse) {
                @Suppress("UNCHECKED_CAST")
                pending.complete(args.last() as Continuation<Response<String>>)
                COROUTINE_SUSPENDED
            } else response
        } as LichessApi
        private val history = mutableListOf<ChessBoard>()
        private val engineScope = CoroutineScope(Job().apply { cancel() })
        private val orchestrator = AnalysisOrchestrator(
            StockfishEngine(context), { state }, { state = it(state) }, engineScope, { history }
        )
        val loader = GameLoader(ChessRepository(lichessApi = api), { state }, { state = it(state) }, scope,
            { history }, { mutableListOf() }, settings, storage, orchestrator)
        init { prefs.edit().clear().commit() }
        fun savePreviousGame() = storage.saveManualStageGame(AnalysedGame(
            timestamp = 42, whiteName = "Saved", blackName = "Opponent", result = "*", pgn = savedPgn,
            moves = listOf("d4", "d5"), moveDetails = emptyList(), previewScores = emptyMap(), analyseScores = emptyMap()
        ))
        override fun close() { prefs.edit().clear().commit() }
    }

    private suspend fun CoroutineScope.settle() {
        val jobs = coroutineContext[Job]!!.children.toList()
        withTimeout(5000) { jobs.joinAll() }
    }

    @Test fun saved_username_loads_one_latest_game_instead_of_the_previous_session() = runBlocking {
        Harness(this).use { h ->
            h.savePreviousGame()
            h.settings.saveLichessUsername("  LatestUser  ")
            h.settings.saveLastServerUser("OlderAccount", "lichess.org")
            h.loader.loadStartupGame { true }
            settle()
            assertEquals(listOf("LatestUser" to 1), h.requests.toList())
            assertEquals("latest", h.state.game?.id)
            assertEquals(listOf("e4", "e5"), h.state.moves)
            assertEquals(AnalysisStage.PREVIEW, h.state.currentStage)
            assertFalse(h.state.showGameSelection)
            assertFalse(h.state.isLoading)
            assertTrue(h.state.hasLastServerUser)
            assertEquals("LatestUser", h.settings.lastServerUser)
        }
    }

    @Test fun fresh_install_does_not_retrieve_the_example_username() = runBlocking {
        Harness(this).use { h ->
            assertEquals("DrNykterstein", h.settings.savedLichessUsername)
            assertNull(h.settings.knownLichessUsername)
            h.loader.loadStartupGame { true }
            settle()
            assertTrue(h.requests.isEmpty())
            assertNull(h.state.game)
            assertFalse(h.state.isLoading)
        }
    }

    @Test fun missing_or_explicitly_blank_username_restores_the_saved_game_without_network() = runBlocking {
        for (blank in listOf(false, true)) Harness(this).use { h ->
            h.savePreviousGame()
            if (blank) {
                h.settings.saveLastServerUser("OlderAccount", "lichess.org")
                h.settings.saveLichessUsername("   ")
            }
            h.loader.loadStartupGame { true }
            settle()
            assertTrue(h.requests.isEmpty())
            assertEquals(h.savedPgn, h.state.game?.pgn)
            assertEquals(AnalysisStage.MANUAL, h.state.currentStage)
            assertNull(h.state.errorMessage)
        }
    }

    @Test fun legacy_last_lichess_account_is_used_if_no_username_setting_exists() = runBlocking {
        Harness(this).use { h ->
            h.settings.saveLastServerUser("LatestUser", "lichess.org")
            h.loader.loadStartupGame { true }
            settle()
            assertEquals(listOf("LatestUser" to 1), h.requests.toList())
            assertEquals("latest", h.state.game?.id)
        }
    }

    @Test fun failed_or_empty_retrieval_restores_previous_game_and_keeps_the_error() = runBlocking {
        for (response in listOf(Response.error<String>(503, "Offline".toResponseBody()), Response.success(""))) {
            Harness(this).use { h ->
                h.savePreviousGame()
                val previous = h.storage.loadManualStageGame()
                h.settings.saveLichessUsername("LatestUser")
                h.response = response
                h.loader.loadStartupGame { true }
                settle()
                assertEquals(h.savedPgn, h.state.game?.pgn)
                assertEquals(previous, h.storage.loadManualStageGame())
                assertNotNull(h.state.errorMessage)
                assertFalse(h.state.isLoading)
            }
        }
    }

    @Test fun failed_retrieval_without_a_previous_game_stops_loading_with_an_error() = runBlocking {
        Harness(this).use { h ->
            h.settings.saveLichessUsername("LatestUser")
            h.response = Response.error(404, "Not found".toResponseBody())
            h.loader.loadStartupGame { true }
            settle()
            assertNull(h.state.game)
            assertFalse(h.state.isLoading)
            assertTrue(h.state.errorMessage.orEmpty().contains("not found"))
        }
    }

    @Test fun selecting_a_game_while_the_engine_starts_cancels_automatic_retrieval() = runBlocking {
        Harness(this).use { h ->
            h.settings.saveLichessUsername("LatestUser")
            val ready = CompletableDeferred<Boolean>()
            h.loader.loadStartupGame { ready.await() }
            yield()
            h.loader.loadGamesFromPgnContent(h.savedPgn)
            val chosen = h.state.game
            ready.complete(true)
            settle()
            assertEquals(chosen, h.state.game)
            assertTrue(h.requests.isEmpty())
        }
    }

    @Test fun a_late_startup_response_cannot_replace_a_game_selected_during_download() = runBlocking {
        for (fail in listOf(false, true)) Harness(this).use { h ->
            h.savePreviousGame()
            h.settings.saveLichessUsername("LatestUser")
            h.deferResponse = true
            h.loader.loadStartupGame { true }
            val request = withTimeout(5000) { h.pending.await() }
            h.loader.loadGamesFromPgnContent(h.savedPgn.replace("Saved game", "Chosen game"))
            val chosen = h.state.game
            request.resume(if (fail) Response.error(503, "Offline".toResponseBody()) else h.response)
            settle()
            assertEquals(chosen, h.state.game)
            assertNull(h.state.errorMessage)
            assertFalse(h.state.isLoading)
        }
    }
}
