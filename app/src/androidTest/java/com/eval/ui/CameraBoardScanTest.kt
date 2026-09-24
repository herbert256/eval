package com.eval.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraBoardScanTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun frame(name: String) = InstrumentationRegistry.getInstrumentation().context.assets
        .open("url-scan/$name").use { BitmapFactory.decodeStream(it) }

    @Test fun camera_jpeg_frames_are_recognized_and_prefilled_for_review() = runBlocking(Dispatchers.Main) {
        val recognizer = BoardImageRecognizer(context)
        try {
            val result = recognizer.recognize(frame("chesscom-italian-white.png").toJpegDataUrl())
            assertNotNull(result)
            assertEquals("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w - - 0 1", result!!.reviewFen())
            assertTrue(result.reviewWarning().isNotBlank())
            assertNull(recognizer.recognize(frame("reddit-chrome-no-board.png").toJpegDataUrl()))
        } finally { recognizer.close() }
    }
}
