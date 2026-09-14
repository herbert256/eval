package com.eval.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.data.OpeningExplorerResponse
import com.eval.data.OpeningInfo
import com.eval.data.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningExplorerRegressionTest {
    private class Pending(val fen: String) {
        val result = CompletableDeferred<Result<OpeningExplorerResponse>>()
        val returned = CompletableDeferred<Unit>()
        fun complete(name: String) = result.complete(Result.Success(
            OpeningExplorerResponse(2, 1, 3, emptyList(), opening = OpeningInfo("A00", name))))
    }

    private class Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val history = BoardHistoryBuilder.build(listOf("e4", "e5", "Nf3", "Nc6", "Bb5")).boards
        val branch = mutableListOf<ChessBoard>()
        val state = MutableStateFlow(GameUiState(
            game = sampleGame(), currentStage = AnalysisStage.MANUAL,
            currentBoard = history[1], currentMoveIndex = 0,
            moves = listOf("e4", "e5", "Nf3", "Nc6", "Bb5"),
            interfaceVisibility = InterfaceVisibilitySettings(
                manualStage = ManualStageVisibility(showOpeningExplorer = true))
        ))
        val requests = Channel<Pending>(Channel.UNLIMITED)
        private val allRequests = java.util.concurrent.ConcurrentLinkedQueue<Pending>()
        val observer = OpeningExplorerLoader(state, { transform -> state.update { it.transform() } },
            scope, { history }, { branch }) { fen ->
            val pending = Pending(fen)
            allRequests.add(pending)
            requests.send(pending)
            // Model a transport that still delivers its response after cancellation.
            withContext(NonCancellable) {
                val result = pending.result.await()
                pending.returned.complete(Unit)
                result
            }
        }.observe()

        suspend fun request() = withTimeout(3000) { requests.receive() }
        suspend fun move(index: Int) = withContext(Dispatchers.Main) {
            state.update { it.copy(currentMoveIndex = index, currentBoard = history[index + 1]) }
        }
        fun close() {
            scope.cancel()
            allRequests.forEach { it.complete("Cleanup") }
        }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(3000) {
        while (!condition()) delay(10)
    }

    @Test fun an_opening_request_runs_without_waiting_for_engine_analysis() = runBlocking {
        val h = Harness()
        try {
            val first = h.request()
            assertEquals(h.history[1].getFen(), first.fen)
            assertTrue(h.state.value.openingExplorerLoading)
            assertNull(h.state.value.analysisResult)
            first.complete("King's Pawn")
            awaitCondition { !h.state.value.openingExplorerLoading }
            assertEquals("King's Pawn", h.state.value.openingExplorerData?.opening?.name)
        } finally { h.close() }
    }

    @Test fun a_late_response_cannot_replace_the_new_positions_opening() = runBlocking {
        val h = Harness()
        try {
            val old = h.request()
            h.move(1)
            val current = h.request()
            current.complete("Current opening")
            awaitCondition { h.state.value.openingExplorerData != null }
            old.complete("Obsolete opening")
            withTimeout(3000) { old.returned.await() }
            withContext(Dispatchers.Main) { assertEquals("Current opening", h.state.value.openingExplorerData?.opening?.name) }
        } finally { h.close() }
    }

    @Test fun switching_games_at_the_same_fen_discards_the_old_request() = runBlocking {
        val h = Harness()
        try {
            val old = h.request()
            withContext(Dispatchers.Main) { h.state.update { it.copy(game = it.game!!.copy(id = "new-game")) } }
            val current = h.request()
            old.complete("Old game")
            withTimeout(3000) { old.returned.await() }
            withContext(Dispatchers.Main) { assertNull(h.state.value.openingExplorerData) }
            current.complete("New game")
            awaitCondition { h.state.value.openingExplorerData != null }
            assertEquals("New game", h.state.value.openingExplorerData?.opening?.name)
        } finally { h.close() }
    }

    @Test fun hiding_opening_information_cancels_the_request_and_spinner() = runBlocking {
        val h = Harness()
        try {
            val old = h.request()
            withContext(Dispatchers.Main) {
                h.state.update { it.copy(interfaceVisibility = InterfaceVisibilitySettings()) }
            }
            awaitCondition { !h.state.value.openingExplorerLoading }
            old.complete("Hidden opening")
            withTimeout(3000) { old.returned.await() }
            withContext(Dispatchers.Main) { assertNull(h.state.value.openingExplorerData) }
            assertTrue(h.requests.tryReceive().isFailure)
        } finally { h.close() }
    }

    @Test fun reloading_the_same_saved_game_refreshes_cleared_opening_data() = runBlocking {
        val h = Harness()
        try {
            h.request().complete("Before reload")
            awaitCondition { h.state.value.openingExplorerData != null }
            withContext(Dispatchers.Main) {
                h.state.update { it.copy(game = it.game!!.copy(), openingExplorerData = null, currentOpeningName = null) }
            }
            h.request().complete("After reload")
            awaitCondition { h.state.value.openingExplorerData?.opening?.name == "After reload" }
            assertEquals("King's Pawn Opening", h.state.value.currentOpeningName)
        } finally { h.close() }
    }

    @Test fun a_failed_lookup_clears_the_spinner_and_navigation_can_retry() = runBlocking {
        val h = Harness()
        try {
            h.request().result.complete(Result.Error("Offline"))
            awaitCondition { !h.state.value.openingExplorerLoading }
            assertNull(h.state.value.openingExplorerData)
            assertEquals("Offline", h.state.value.openingExplorerError)
            assertEquals("King's Pawn Opening", h.state.value.currentOpeningName)
            h.move(1)
            h.request().complete("Recovered")
            awaitCondition { h.state.value.openingExplorerData?.opening?.name == "Recovered" }
            assertNull(h.state.value.openingExplorerError)
        } finally { h.close() }
    }

    @Test fun navigating_clears_old_statistics_and_uses_actual_moves_for_the_opening_name() = runBlocking {
        val h = Harness()
        try {
            val first = h.request()
            first.complete("Old position")
            awaitCondition { h.state.value.openingExplorerData != null }
            h.move(4)
            awaitCondition { h.state.value.currentOpeningName == "Ruy Lopez" }
            assertNull(h.state.value.openingExplorerData)
            assertTrue(h.state.value.openingExplorerLoading)
            // Return to the initial board before the debounce starts another HTTP call.
            h.move(-1)
            awaitCondition { h.state.value.currentOpeningName == null }
            h.state.update { it.copy(game = null) }
            awaitCondition { !h.state.value.openingExplorerLoading }
        } finally { h.close() }
    }

    @Test fun exploring_a_different_opening_updates_the_name_and_restores_the_main_line_name() = runBlocking {
        val h = Harness()
        try {
            h.state.update { it.copy(interfaceVisibility = InterfaceVisibilitySettings()) }
            h.branch.add(h.history[1])
            h.branch.add(h.history[1].copy().apply { check(makeUciMove("c7c5")) })
            withContext(Dispatchers.Main) {
                h.state.update { it.copy(isExploringLine = true, savedGameMoveIndex = 0,
                    exploringLineMoveIndex = 0, exploringLineMoves = listOf("c7c5"), currentBoard = h.branch.last()) }
            }
            awaitCondition { h.state.value.currentOpeningName == "Sicilian Defense" }
            withContext(Dispatchers.Main) {
                h.state.update { it.copy(isExploringLine = false, currentBoard = h.history[1]) }
            }
            awaitCondition { h.state.value.currentOpeningName == "King's Pawn Opening" }
        } finally { h.close() }
    }

    private companion object {
        fun sampleGame() = com.eval.data.LichessGame(
            id = "opening-test", rated = false, variant = "standard", speed = "blitz", perf = null,
            status = "*", winner = null,
            players = com.eval.data.Players(com.eval.data.Player(null, null, null), com.eval.data.Player(null, null, null)),
            pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 *", moves = null, clock = null, createdAt = 1L, lastMoveAt = null)
    }
}
