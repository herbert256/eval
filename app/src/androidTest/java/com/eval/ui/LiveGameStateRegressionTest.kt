package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.audio.MoveSoundPlayer
import com.eval.chess.ChessBoard
import com.eval.data.*
import com.eval.stockfish.StockfishEngine
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class LiveGameStateRegressionTest {
    private class Harness(initial: ChessBoard = ChessBoard(), moves: List<String> = listOf("e4", "e5")) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("live_game_state_test", Context.MODE_PRIVATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val sounds = MoveSoundPlayer(context)
        val history = BoardHistoryBuilder.build(moves, initial).boards.toMutableList()
        private val streamedHistory = history.drop(1).map { board ->
            val move = board.getLastMove()!!
            val promotion = when (move.promotion) {
                com.eval.chess.PieceType.QUEEN -> "q"
                com.eval.chess.PieceType.ROOK -> "r"
                com.eval.chess.PieceType.BISHOP -> "b"
                com.eval.chess.PieceType.KNIGHT -> "n"
                else -> ""
            }
            val uci = move.from.toAlgebraic() + move.to.toAlgebraic() + promotion
            """{"lm":"$uci","fen":"${board.getFen().split(' ').take(2).joinToString(" ")}"}"""
        }
        val analysisRequests = mutableListOf<String>()
        val exploration = mutableListOf<ChessBoard>()
        var state = GameUiState(game = game(initial), moves = moves, currentBoard = history.last(),
            currentMoveIndex = moves.lastIndex, currentStage = AnalysisStage.MANUAL)
        val pending = Channel<Continuation<Response<ResponseBody>>>(Channel.UNLIMITED)
        private val api = Proxy.newProxyInstance(LichessApi::class.java.classLoader, arrayOf(LichessApi::class.java)) { _, method, args ->
            check(method.name == "streamGame")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Response<ResponseBody>>
            check(pending.trySend(continuation).isSuccess)
            COROUTINE_SUSPENDED
        } as LichessApi
        private val repository = ChessRepository(lichessApi = api)
        private val orchestrator = AnalysisOrchestrator(StockfishEngine(context), { state }, { state = it(state) },
            CoroutineScope(Job().apply { cancel() }), { history })
        val live = LiveGameManager(repository, { state }, { state = it(state) }, scope, sounds,
            analyzeDisplayedPosition = {
                analysisRequests.add(state.currentBoard.getFen())
                orchestrator.analyzePosition(state.currentBoard)
            }) { history.add(it.copy()) }
        val loader = GameLoader(repository, { state }, { state = it(state) }, scope,
            { history }, { exploration }, SettingsPreferences(prefs), GameStorageManager(prefs, Gson()), orchestrator,
            stopLiveFollow = { live.stopLiveFollow() })

        suspend fun start(): Continuation<Response<ResponseBody>> {
            withContext(Dispatchers.Main) { live.startLiveFollow("live-test") }
            return withTimeout(3000) { pending.receive() }
        }
        suspend fun deliver(request: Continuation<Response<ResponseBody>>, vararg events: String) {
            val body = (listOf("""{"id":"live-test","status":{"name":"started"}}""") + streamedHistory + events).joinToString("\n") + "\n"
            request.resume(Response.success(body.toResponseBody()))
            withTimeout(3000) { scope.coroutineContext[Job]!!.children.toList().joinAll() }
        }
        fun close() { scope.cancel(); live.cancel(); sounds.release(); prefs.edit().clear().commit() }
    }

    @Test fun a_live_response_cannot_change_a_new_local_game() = runBlocking {
        val h = Harness()
        try {
            val request = h.start()
            withContext(Dispatchers.Main) { h.loader.loadGamesFromPgnContent("[White \"Local\"]\n[Black \"Opponent\"]\n\n1. d4 d5 *") }
            val expectedFen = h.state.currentBoard.getFen()
            h.deliver(request, """{"lm":"g1f3","wc":170,"bc":180}""", """{"status":{"name":"mate"},"winner":"black"}""")
            assertEquals(listOf("d4", "d5"), h.state.moves)
            assertEquals(expectedFen, h.state.currentBoard.getFen())
            assertNull(h.state.game!!.winner)
            assertFalse(h.state.isLiveGame)
            assertNull(h.state.liveGameId)
        } finally { h.close() }
    }

    @Test fun a_live_response_cannot_repopulate_a_cleared_game() = runBlocking {
        val h = Harness()
        try {
            val request = h.start()
            withContext(Dispatchers.Main) { h.loader.clearGame() }
            h.deliver(request, """{"lm":"g1f3","wc":170,"bc":180}""")
            assertNull(h.state.game)
            assertTrue(h.state.moves.isEmpty())
            assertTrue(h.history.isEmpty())
            assertFalse(h.state.isLiveGame)
        } finally { h.close() }
    }

    @Test fun live_moves_from_a_custom_fen_keep_the_board_and_correct_players_clock() = runBlocking {
        val initial = ChessBoard().apply { check(setFen("4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17")) }
        val h = Harness(initial, emptyList())
        try {
            val request = h.start()
            val expected = initial.copy().apply { check(makeUciMove("e7e5")) }
            h.deliver(request, """{"lm":"e7e5","wc":170,"bc":123}""")
            assertEquals(expected.getFen(), h.state.currentBoard.getFen())
            assertEquals("2:03", h.state.moveDetails.single().clockTime)
        } finally { h.close() }
    }

    @Test fun live_moves_extend_the_main_game_without_replacing_an_explored_position() = runBlocking {
        val h = Harness()
        try {
            val request = h.start()
            val branchBoard = h.state.currentBoard.copy().apply { check(makeUciMove("b1c3")) }
            withContext(Dispatchers.Main) {
                h.state = h.state.copy(isExploringLine = true, exploringLineMoves = listOf("b1c3"),
                    exploringLineMoveIndex = 0, savedGameMoveIndex = 1, currentBoard = branchBoard)
            }
            h.deliver(request, """{"lm":"g1f3","wc":170,"bc":180}""")
            assertEquals(listOf("e4", "e5", "g1f3"), h.state.moves)
            assertEquals(4, h.history.size)
            assertEquals(branchBoard.getFen(), h.state.currentBoard.getFen())
            assertEquals(1, h.state.currentMoveIndex)
            assertEquals(listOf("b1c3"), h.state.exploringLineMoves)
            assertTrue(h.analysisRequests.isEmpty())
        } finally { h.close() }
    }

    @Test fun reconnecting_does_not_duplicate_moves_after_pieces_return_to_their_starting_squares() = runBlocking {
        val moves = listOf("Nf3", "Nf6", "Ng1", "Ng8")
        val h = Harness(moves = moves)
        try {
            val request = h.start()
            val expected = h.history.last().copy().apply { check(makeUciMove("e2e4")) }
            h.deliver(request, """{"lm":"e2e4","wc":160,"bc":165}""")
            assertEquals(moves + "e2e4", h.state.moves)
            assertEquals(6, h.history.size)
            assertEquals(expected.getFen(), h.state.currentBoard.getFen())
        } finally { h.close() }
    }

    @Test fun following_a_new_move_replaces_the_old_positions_evaluation() = runBlocking {
        val h = Harness()
        try {
            val request = h.start()
            withContext(Dispatchers.Main) {
                val oldFen = h.state.currentBoard.getFen()
                h.state = h.state.copy(analysisResult = com.eval.stockfish.AnalysisResult(20, 1, 1, emptyList(), oldFen),
                    analysisResultFen = oldFen)
            }
            h.deliver(request, """{"lm":"g1f3","wc":170,"bc":180}""")
            assertNull(h.state.analysisResult)
            assertNull(h.state.analysisResultFen)
            assertEquals(listOf(h.state.currentBoard.getFen()), h.analysisRequests)
        } finally { h.close() }
    }

    private companion object {
        fun game(initial: ChessBoard) = LichessGame(
            id = "live-test", rated = false, variant = "standard", speed = "blitz", perf = null,
            status = "started", winner = null,
            players = Players(Player(null, null, null), Player(null, null, null)),
            pgn = "[SetUp \"1\"]\n[FEN \"${initial.getFen()}\"]\n\n*",
            moves = null, clock = null, createdAt = 1L, lastMoveAt = null)
    }
}
