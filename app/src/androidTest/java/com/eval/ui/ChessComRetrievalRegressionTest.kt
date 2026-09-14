package com.eval.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@RunWith(AndroidJUnit4::class)
class ChessComRetrievalRegressionTest {
    private fun game(id: String) = ChessComGame(
        url = "https://www.chess.com/game/live/$id", pgn = "[White \"White\"]\n[Black \"Black\"]\n\n1. e4 *",
        time_control = "180", end_time = 1L, rated = true, time_class = "blitz", rules = "chess",
        white = ChessComPlayer(1500, "win", "White"), black = ChessComPlayer(1500, "resigned", "Black")
    )

    private fun repository(months: List<String>, monthly: suspend (String) -> List<ChessComGame>): ChessRepository {
        val api = Proxy.newProxyInstance(ChessComApi::class.java.classLoader, arrayOf(ChessComApi::class.java)) { _, method, args ->
            when (method.name) {
                "getArchives" -> Response.success(ChessComArchivesResponse(months))
                "getMonthlyGames" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Response<ChessComGamesResponse>>
                    val call: suspend () -> Response<ChessComGamesResponse> = {
                        Response.success(ChessComGamesResponse(monthly(args[0] as String)))
                    }
                    call.startCoroutineUninterceptedOrReturn(continuation)
                }
                else -> error(method.name)
            }
        } as ChessComApi
        return ChessRepository(chessComApi = api)
    }

    @Test fun enough_recent_games_do_not_wait_for_a_stalled_older_archive() = runBlocking {
        val repository = repository(listOf("older", "newest")) { month ->
            if (month == "older") awaitCancellation()
            listOf(game("newest"))
        }
        val result = withTimeout(2000) { repository.getChessComGames("White", 1) }
        assertTrue(result is Result.Success)
        assertEquals(listOf("newest"), (result as Result.Success).data.map { it.id })
    }

    @Test fun games_remain_newest_first_when_older_requests_finish_first() = runBlocking {
        val repository = repository(listOf("older", "newest")) { month ->
            if (month == "newest") { delay(100); listOf(game("newer"), game("newest")) }
            else listOf(game("oldest"), game("older"))
        }
        val result = withTimeout(2000) { repository.getChessComGames("White", 3) }
        assertEquals(listOf("newest", "newer", "older"), (result as Result.Success).data.map { it.id })
    }
}
