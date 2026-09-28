package com.eval.export

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AnimatedGifEncoderTest {
    private val width = 48
    private val height = 40

    /** A chess-like frame: two square colours, a moving "piece" and an anti-aliased edge. */
    private fun frame(step: Int): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        when {
            x in (step * 6) until (step * 6 + 6) && y in 12 until 18 -> 0xFF000000.toInt()
            x == step * 6 + 6 && y in 12 until 18 -> 0xFF7A5A40.toInt()
            ((x / 6) + (y / 5)) % 2 == 0 -> 0xFFF0D9B5.toInt()
            else -> 0xFFB58863.toInt()
        }
    }

    private fun encode(frames: List<IntArray>, reusePalette: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.reusePalette = reusePalette
        assertTrue(encoder.start(out))
        encoder.setDelay(500)
        encoder.setRepeat(0)
        encoder.setQuality(1) // sample every pixel so the tiny frame's rare edge colour is learnt
        frames.forEach { assertTrue(encoder.addFrame(it, width, height)) }
        assertTrue(encoder.finish())
        return out.toByteArray()
    }

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
        val screen = u16(6) to u16(8)
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
                    p += 10 + if (hasLct) 3 * (2 shl (flags and 7)) else 0
                    p = skipSubBlocks(p + 1) // LZW minimum code size, then data
                }
                0x3b -> return screen to images
                else -> fail("Unexpected GIF block 0x%02x at %d".format(gif[p].toInt() and 0xff, p))
            }
        }
    }

    @Test fun reused_palette_frames_share_the_global_table_and_decode_exactly() {
        val frames = (0 until 5).map { frame(it) }
        val gif = encode(frames, reusePalette = true)

        val (screen, images) = parse(gif)
        assertEquals(width to height, screen)
        assertEquals(frames.size, images.size)
        images.forEach {
            assertEquals(width, it.width)
            assertEquals(height, it.height)
            assertFalse("Reused palette must not repeat a local colour table", it.hasLocalColorTable)
        }

        val reader = GifReader(gif)
        assertEquals(frames.size, reader.count)
        frames.forEachIndexed { index, expected ->
            val decoded = reader.read(index)
            assertEquals(width, decoded.width)
            assertEquals(height, decoded.height)
            for (i in expected.indices) {
                val actual = decoded.rgb(i % width, i / width)
                for (shift in intArrayOf(16, 8, 0)) {
                    val diff = ((actual shr shift) and 0xff) - ((expected[i] shr shift) and 0xff)
                    assertTrue("Frame $index pixel $i channel differs by $diff", kotlin.math.abs(diff) <= 12)
                }
            }
        }
    }

    @Test fun per_frame_palettes_still_write_local_tables() {
        val gif = encode(listOf(frame(0), frame(1)), reusePalette = false)
        val (screen, images) = parse(gif)
        assertEquals(width to height, screen)
        assertEquals(listOf(false, true), images.map { it.hasLocalColorTable })
        assertEquals(2, GifReader(gif).count)
    }

    @Test fun frames_of_another_size_are_rejected() {
        val encoder = AnimatedGifEncoder()
        assertTrue(encoder.start(ByteArrayOutputStream()))
        assertTrue(encoder.addFrame(frame(0), width, height))
        assertFalse(encoder.addFrame(IntArray(width * (height + 2)), width, height + 2))
    }
}

/**
 * javax.imageio decodes GIFs on the JVM that runs these tests, but it is not on Android's
 * compile classpath, so it is reached through its public API classes by reflection.
 */
private class GifReader(gif: ByteArray) {
    class Image(val width: Int, val height: Int, private val pixel: (Int, Int) -> Int) {
        fun rgb(x: Int, y: Int) = pixel(x, y)
    }

    private val imageIo = Class.forName("javax.imageio.ImageIO")
    private val readerClass = Class.forName("javax.imageio.ImageReader")
    private val bufferedImage = Class.forName("java.awt.image.BufferedImage")
    private val reader: Any = (imageIo.getMethod("getImageReadersByFormatName", String::class.java)
        .invoke(null, "gif") as Iterator<*>).next()!!

    init {
        val stream = imageIo.getMethod("createImageInputStream", Any::class.java).invoke(null, ByteArrayInputStream(gif))
        readerClass.getMethod("setInput", Any::class.java).invoke(reader, stream)
    }

    val count: Int get() = readerClass.getMethod("getNumImages", Boolean::class.javaPrimitiveType).invoke(reader, true) as Int

    fun read(index: Int): Image {
        val image = readerClass.getMethod("read", Int::class.javaPrimitiveType).invoke(reader, index)!!
        val getRgb = bufferedImage.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        return Image(
            bufferedImage.getMethod("getWidth").invoke(image) as Int,
            bufferedImage.getMethod("getHeight").invoke(image) as Int
        ) { x, y -> getRgb.invoke(image, x, y) as Int }
    }
}
