package com.eval.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.chess.ChessBoard
import com.eval.data.LichessGame
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class GifCancellationTest {
    @Test fun cancelling_export_stops_work_removes_partial_file_and_never_opens_share() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moves = listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6")
        val history = BoardHistoryBuilder.build(moves, ChessBoard())
        val game = Gson().fromJson("""{"id":"cancel-test","players":{"white":{},"black":{}},"pgn":"1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 *"}""", LichessGame::class.java)
        assertNull(history.failedMoveIndex)
        val details = moves.map { MoveDetails(it, "", "", false, "") }
        val state = MutableStateFlow(GameUiState(game = game, moves = moves, moveDetails = details))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val firstFrame = CountDownLatch(1)
        val resumeEncoder = CountDownLatch(1)
        val shared = AtomicBoolean(false)
        val wrapper = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { shared.set(true) }
        }
        val directory = File(context.cacheDir, "gif_exports")
        val previousFiles = directory.list()?.toSet().orEmpty()
        val manager = ExportShareManager({ state.value }, { transform ->
            state.update { transform(it) }
            if ((state.value.gifExportProgress ?: 0f) > 0f && firstFrame.count > 0) {
                firstFrame.countDown()
                check(resumeEncoder.await(15, TimeUnit.SECONDS))
            }
        }, scope)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { manager.exportAsGif(wrapper) }
            assertTrue("Export did not start", firstFrame.await(15, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { manager.cancelGifExport() }
            resumeEncoder.countDown()
            runBlocking { withTimeout(15000) { scope.coroutineContext[Job]!!.children.toList().joinAll() } }
            assertFalse("Cancelled export opened share chooser", shared.get())
            assertFalse(state.value.showGifExportDialog)
            assertNull(state.value.gifExportProgress)
            assertNull("Cancellation was displayed as an error", state.value.errorMessage)
            assertEquals("Cancelled export left a file", previousFiles, directory.list()?.toSet().orEmpty())
        } finally {
            resumeEncoder.countDown()
            scope.cancel()
            directory.listFiles()?.filter { it.name !in previousFiles }?.forEach { it.delete() }
        }
    }
}
