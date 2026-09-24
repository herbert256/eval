package com.eval.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans camera frames with the on-device board recognizer. A board read the
 * same on two consecutive frames opens Board setup for review; a single read
 * can be a motion-blurred misread.
 */
@Composable
internal fun CameraBoardScreen(
    onStartFen: (String) -> Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings = remember(context) {
        SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
    }
    val recognizer = remember { BoardImageRecognizer(context) }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }
    var foundFen by rememberSaveable { mutableStateOf<String?>(null) }
    var foundWarning by rememberSaveable { mutableStateOf<String?>(null) }
    // One recognition at a time, including one still running when review opens.
    val scanning = remember { AtomicBoolean(false) }

    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(permitted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted && !asked) { asked = true; permission.launch(Manifest.permission.CAMERA) } }
    // Access may be granted in Android settings while Eval is in the background.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = permitted() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val fen = foundFen
    if (fen != null) {
        key(fen) {
            BoardSetupScreen(
                currentFen = null,
                initiallyFlipped = false,
                layout = settings.loadBoardLayoutSettings(),
                initialFen = fen,
                importWarning = foundWarning,
                imagePosition = true,
                onStart = { start -> onStartFen(start).also { if (it) settings.saveFenToHistory(start) } },
                onBack = { foundFen = null }
            )
        }
        return
    }

    var status by remember { mutableStateOf("Looking for a chessboard…") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var lastPlacement by remember { mutableStateOf<String?>(null) }

    suspend fun scan(dataUrl: String) {
        val result = try {
            recognizer.recognize(dataUrl)
        } catch (e: TimeoutCancellationException) {
            status = "Board recognition is taking too long. Retrying…"; return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Board recognition failed. Retrying…"; return
        }
        if (foundFen != null) return
        val placement = result?.optString("placement").orEmpty()
        when {
            result == null || placement.isEmpty() -> { lastPlacement = null; status = "Looking for a chessboard…" }
            placement != lastPlacement -> { lastPlacement = placement; status = "Board found. Hold still…" }
            else -> { lastPlacement = null; foundWarning = result.reviewWarning(); foundFen = result.reviewFen() }
        }
    }

    EvalScreen(
        backgroundColor = AppColors.DarkBlueBackground,
        topBar = { EvalTitleBar(title = "Camera", onBackClick = onBack, onEvalClick = onBack) }
    ) {
        Spacer(Modifier.height(12.dp))
        if (!granted) {
            Text("Eval needs camera access to scan a chessboard.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                }) { Text("App settings") }
            }
            return@EvalScreen
        }
        Text("Point the camera at a 2D chess diagram in a book or on a screen. Hold the phone straight above it so the whole board is visible. " +
            "Photos of physical boards at an angle are not supported.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
            CameraPreview(onFrame = { image ->
                if (!scanning.compareAndSet(false, true)) { image.close(); return@CameraPreview }
                val dataUrl = try { image.toJpegDataUrl() } catch (_: Exception) { null } finally { image.close() }
                if (dataUrl == null) scanning.set(false)
                else scope.launch { try { scan(dataUrl) } finally { scanning.set(false) } }
            }, onError = { cameraError = it })
            // Framing guide only: the recognizer searches the whole frame.
            Box(Modifier.size(minOf(maxWidth, maxHeight) * 0.85f).border(2.dp, Color.White.copy(alpha = 0.6f)))
        }
        Spacer(Modifier.height(12.dp))
        val error = cameraError
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Back camera preview with frame analysis, bound to the current lifecycle while composed. */
@Composable
private fun CameraPreview(onFrame: (ImageProxy) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnError by rememberUpdatedState(onError)
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    DisposableEffect(lifecycleOwner, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        // Board tiles need more pixels than the 640x480 analysis default.
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(executor) { image -> currentOnFrame(image) } }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameras = providerFuture.get()
                val selector = if (cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                    else CameraSelector.DEFAULT_FRONT_CAMERA
                cameras.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                provider = cameras
            } catch (e: Exception) {
                currentOnError("The camera could not be started. Close other camera apps and try again.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
}

/** Upright JPEG of a camera frame: the tile classifier expects unrotated pieces. */
private fun ImageProxy.toJpegDataUrl(): String {
    val frame = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) frame
        else Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    try {
        return upright.toJpegDataUrl()
    } finally {
        if (upright !== frame) upright.recycle()
        frame.recycle()
    }
}

internal fun Bitmap.toJpegDataUrl(): String = ByteArrayOutputStream().use { output ->
    compress(Bitmap.CompressFormat.JPEG, 90, output)
    "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
