package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PieceImagesTest {
    @Test fun boards_share_native_resolution_images_without_density_upscaling() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val boards = List(3) { async { PieceImages.load(context) } }.awaitAll()
        val images = boards.first()
        assertEquals(12, images.size)
        boards.forEach { assertSame(images, it) }
        images.values.forEach {
            assertEquals(480, it.width)
            assertEquals(480, it.height)
        }
        // A recreated board can display the cache immediately without decoding.
        assertSame(images, PieceImages.cached)
    }
}
