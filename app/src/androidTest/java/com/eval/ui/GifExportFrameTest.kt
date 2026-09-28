package com.eval.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.chess.ChessBoard
import com.eval.export.GifExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GifExportFrameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val moves = listOf("e4", "e5", "Nf3")

    private data class ImageBlock(val width: Int, val height: Int, val hasLocalColorTable: Boolean)

    /** Walks the GIF block structure and returns the logical screen size and each image descriptor. */
    private fun parse(gif: ByteArray): Pair<Pair<Int, Int>, List<ImageBlock>> {
        fun u16(at: Int) = (gif[at].toInt() and 0xff) or ((gif[at + 1].toInt() and 0xff) shl 8)
        fun skipSubBlocks(start: Int): Int {
            var p = start
            while (gif[p].toInt() != 0) p += (gif[p].toInt() and 0xff) + 1
            return p + 1
        }
        assertEquals("GIF89a", String(gif, 0, 6, Charsets.US_ASCII))
        val packed = gif[10].toInt() and 0xff
        var p = 13 + if (packed and 0x80 != 0) 3 * (2 shl (packed and 7)) else 0
        val images = mutableListOf<ImageBlock>()
        while (true) {
            when (gif[p].toInt() and 0xff) {
                0x21 -> p = skipSubBlocks(p + 2)
                0x2c -> {
                    val flags = gif[p + 9].toInt() and 0xff
                    val hasLct = flags and 0x80 != 0
                    images += ImageBlock(u16(p + 5), u16(p + 7), hasLct)
                    p = skipSubBlocks(p + 11 + if (hasLct) 3 * (2 shl (flags and 7)) else 0)
                }
                0x3b -> return (u16(6) to u16(8)) to images
                else -> fail("Unexpected GIF block at $p")
            }
        }
    }

    private fun assertColor(message: String, expected: Int, actual: Int) {
        val close = abs(Color.red(expected) - Color.red(actual)) <= 16 &&
            abs(Color.green(expected) - Color.green(actual)) <= 16 &&
            abs(Color.blue(expected) - Color.blue(actual)) <= 16
        assertTrue("$message: expected #%06X, got #%06X".format(expected and 0xffffff, actual and 0xffffff), close)
    }

    @Test fun annotated_frames_share_one_size_and_use_the_board_colours() = runBlocking {
        val boards = BoardHistoryBuilder.build(moves, ChessBoard()).boards
        val light = 0xFF4CAF50.toInt()
        val dark = 0xFF1A237E.toInt()
        val file = GifExporter.exportAsGifWithAnnotations(context, boards, moves, frameDelay = 500,
            lightSquareColor = light, darkSquareColor = dark)
        try {
            val gif = file.readBytes()
            val (screen, images) = parse(gif)
            assertEquals(420 to 430, screen)
            assertEquals(boards.size, images.size)
            images.forEachIndexed { index, image ->
                assertEquals("Frame $index width", 420, image.width)
                assertEquals("Frame $index height", 430, image.height)
                assertFalse("Frame $index repeats the global palette", image.hasLocalColorTable)
            }

            val movie = requireNotNull(Movie.decodeByteArray(gif, 0, gif.size))
            val bitmap = Bitmap.createBitmap(420, 430, Bitmap.Config.ARGB_8888)
            try {
                for (frame in listOf(0, 2)) {
                    movie.setTime(frame * 500 + 250)
                    bitmap.eraseColor(Color.TRANSPARENT)
                    movie.draw(Canvas(bitmap), 0f, 0f)
                    // Annotation bar on top (frame 0 reads "Start"), then unscaled 50 px squares.
                    assertColor("Frame $frame annotation bar", 0xFF2D2D2D.toInt(), bitmap.getPixel(3, 3))
                    assertColor("Frame $frame a8 corner", light, bitmap.getPixel(2, 32))
                    assertColor("Frame $frame b8 corner", dark, bitmap.getPixel(52, 32))
                    assertColor("Frame $frame a1 bottom corner", dark, bitmap.getPixel(2, 428))
                    assertColor("Frame $frame b1 bottom corner", light, bitmap.getPixel(97, 428))
                }
            } finally { bitmap.recycle() }
        } finally { file.delete() }
    }

    @Test fun a_new_export_prunes_stale_exports_but_keeps_recent_ones() = runBlocking {
        val directory = File(context.cacheDir, "gif_exports").apply { mkdirs() }
        val stale = File(directory, "game_stale_test.gif").apply { writeBytes(byteArrayOf(1)) }
        val recent = File(directory, "game_recent_test.gif").apply { writeBytes(byteArrayOf(1)) }
        assertTrue(stale.setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L))
        var file: File? = null
        try {
            file = GifExporter.exportAsGif(context, listOf(ChessBoard()))
            assertFalse("Stale export was kept", stale.exists())
            assertTrue("Recent export (possibly still being shared) was deleted", recent.exists())
            assertTrue(file.exists())
        } finally {
            stale.delete()
            recent.delete()
            file?.delete()
        }
    }
}
