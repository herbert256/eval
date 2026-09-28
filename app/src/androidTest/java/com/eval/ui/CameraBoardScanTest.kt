package com.eval.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.data.WebChessKind
import java.io.File
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraBoardScanTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val italian = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w - - 0 1"

    /** A JPEG stored sideways with an EXIF tag, as most phone cameras save portrait photos. */
    private fun sidewaysPhoto(name: String): ByteArray {
        val upright = instrumentation.context.assets.open("url-scan/$name").use { BitmapFactory.decodeStream(it) }
        val stored = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(270f) }, true)
        val file = File(context.cacheDir, "camera-test.jpg")
        file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }
    // This test's own input photos; the UI test checks that Eval deletes the photos it takes itself.
    private val inputPhotos = mutableListOf<File>()
    private fun photo(bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(directory, "test-${System.nanoTime()}.jpg").apply { writeBytes(bytes) }
        inputPhotos += file
        return FileProvider.getUriForFile(context, "com.eval.fileprovider", file)
    }
    @After fun deleteInputPhotos() { inputPhotos.forEach { it.delete() } }
    private suspend fun UrlGameScanner.settled(): UrlScanState {
        withTimeout(90_000) { while (uiState.value.busy) delay(30) }
        return uiState.value.also { assertNull(it.toString(), it.error) }
    }

    @Test fun rotated_camera_photos_are_recognized_and_prefilled_for_review() = runBlocking {
        val board = photo(sidewaysPhoto("chesscom-italian-white.png"))
        val empty = photo(sidewaysPhoto("reddit-chrome-no-board.png"))
        withContext(Dispatchers.Main) {
            val scanner = UrlGameScanner(context, this)
            try {
                scanner.openCameraPhoto(board)
                val found = scanner.settled().results.single()
                assertEquals(WebChessKind.IMAGE, found.kind)
                assertEquals(italian, found.content)
                assertEquals("Camera photo", found.source)
                assertTrue(found.needsReview)
                assertFalse(found.warning.isNullOrBlank())
                scanner.openCameraPhoto(empty)
                val none = scanner.settled()
                assertTrue(none.results.isEmpty())
                assertTrue(none.status, none.status.startsWith("No chessboard found in this photo"))
            } finally { scanner.close() }
        }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        return nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    }
    private fun waitFor(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        fail("$message; visible: ${visible().map { it.text ?: it.contentDescription }}")
    }
    private fun shown(label: String) = visible().any { it.text?.toString() == label || it.contentDescription?.toString() == label }
    private fun click(label: String) {
        waitFor("Click $label") {
            val node = visible().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
            generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        SystemClock.sleep(200)
    }

    /** Stands in for the camera app: writes [photo] to the requested output, or cancels. */
    private class CameraApp(private val context: Context, var photo: ByteArray?) : Instrumentation.ActivityMonitor() {
        var outputs = mutableListOf<Uri>()
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
            @Suppress("DEPRECATION") val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
            outputs += output
            val bytes = photo ?: return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            context.contentResolver.openOutputStream(output)!!.use { it.write(bytes) }
            return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
    }

    @Test fun select_game_camera_cancel_photo_review_and_start() {
        val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
        val previous = settings.exportAllSettings()
        val camera = CameraApp(context, null)
        val photos = File(context.cacheDir, "camera_photos")
        instrumentation.addMonitor(camera)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { ViewModelProvider(it)[GameViewModel::class.java].dismissAiAppWarning() }
                click("Select a game")
                click("Start from camera")
                waitFor("Camera app opened once") { camera.outputs.size == 1 }
                waitFor("Cancelled camera can be reopened") { shown("Take photo") }
                assertTrue("Cancelled photo is deleted", photos.listFiles().isNullOrEmpty())
                camera.photo = sidewaysPhoto("lichess-italian-black.png")
                click("Take photo")
                waitFor("Board found in photo") { shown("Review position") }
                assertTrue(shown("Take another photo"))
                assertEquals(1, photos.listFiles()!!.size)
                scenario.recreate()
                waitFor("Photo result restored after rotation") { shown("Review position") }
                assertEquals("Recreation must not reopen the camera", 2, camera.outputs.size)
                click("Review position")
                waitFor("Photo opens Board setup") { shown("Board setup") }
                click("Start from this position")
                waitFor("Manual screen") { visible().any { it.contentDescription?.toString() == "Start AI report" } }
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[GameViewModel::class.java]
                    vm.setAnalysisEnabled(false)
                    assertEquals(italian, vm.uiState.value.currentBoard.getFen())
                }
                waitFor("Photo deleted after starting the position") { photos.listFiles().isNullOrEmpty() }
            }
        } finally {
            instrumentation.removeMonitor(camera)
            assertTrue(settings.importAllSettings(previous))
        }
    }
}
