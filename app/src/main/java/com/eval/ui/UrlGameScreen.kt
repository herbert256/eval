package com.eval.ui

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eval.data.WebChessCandidate
import com.eval.data.WebChessKind
import com.eval.data.SharedChessInput
import kotlinx.coroutines.CoroutineScope
import java.io.File

private val ReviewCandidateSaver = listSaver<WebChessCandidate?, String>(
    save = { candidate -> candidate?.let {
        listOf(it.kind.name, it.content, it.title, it.source, it.needsReview.toString(), it.warning.orEmpty())
    } ?: emptyList() },
    restore = { if (it.isEmpty()) null else WebChessCandidate(WebChessKind.valueOf(it[0]), it[1], it[2], it[3], it[4].toBoolean(), it[5].ifEmpty { null }) }
)

/** Camera app photos in Eval's private cache, shared with the camera app through FileProvider. */
private object CameraPhotos {
    private fun directory(context: Context) = File(context.cacheDir, "camera_photos")
    fun create(context: Context) = File(directory(context).apply { mkdirs() }, "board-${System.currentTimeMillis()}.jpg")
    fun uri(context: Context, photo: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
    fun keepOnly(context: Context, photo: File?) { directory(context).listFiles()?.forEach { if (it != photo) it.delete() } }
}

/** Keeps a running scan, its results and its rendered page across configuration changes. */
internal class UrlScanModel(application: Application) : AndroidViewModel(application) {
    private var scanner: UrlGameScanner? = null
    fun scanner(create: (CoroutineScope) -> UrlGameScanner): UrlGameScanner = scanner ?: create(viewModelScope).also { scanner = it }
    fun release() { scanner?.close(); scanner = null }
    override fun onCleared() = release()
}

private fun Context.activity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
internal fun UrlGameScreen(
    onStartFen: (String) -> Boolean,
    onStartPgn: (String) -> Unit,
    onBack: () -> Unit,
    sharedInput: SharedChessInput? = null,
    localFile: Boolean = false,
    clipboardEntry: Boolean = false,
    camera: Boolean = false,
    scannerFactory: (android.content.Context, CoroutineScope) -> UrlGameScanner = { context, scope -> UrlGameScanner(context, scope) }
) {
    val context = LocalContext.current
    val activity = remember(context) { context.activity() }
    val model: UrlScanModel = viewModel(key = "url-scan:" + when {
        localFile -> "local"
        camera -> "camera"
        sharedInput != null -> (if (clipboardEntry) "clipboard:" else "shared:") + sharedInput.id
        else -> "url"
    })
    val scanner = remember(model) { model.scanner { scope -> scannerFactory(context, scope) } }
    DisposableEffect(scanner, activity) {
        scanner.attach(activity ?: context.applicationContext)
        onDispose {
            // Recreation (rotation, or the system reclaiming a background Activity) restores this screen;
            // otherwise the user has left it, so stop the scan and delete camera photos.
            val recreating = activity != null && !activity.isFinishing &&
                (activity as? LifecycleOwner)?.lifecycle?.currentState == Lifecycle.State.DESTROYED
            if (recreating) scanner.attach(context.applicationContext)
            else {
                model.release()
                if (camera) CameraPhotos.keepOnly(context, null)
            }
        }
    }
    val state by scanner.uiState.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(stateSaver = ReviewCandidateSaver) { mutableStateOf<WebChessCandidate?>(null) }
    var scannedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = remember(context) {
        SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE))
    }
    var localUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpened by rememberSaveable { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selected = null
            localUri = uri.toString()
            scanner.openLocalFile(uri)
        }
    }
    val chooseFile = { pickerOpened = true; filePicker.launch(arrayOf("*/*")) }
    // The camera app writes to a new file; the previous photo stays until a new one is taken.
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOpened by rememberSaveable { mutableStateOf(false) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val pending = pendingPhotoPath?.let(::File)
        pendingPhotoPath = null
        if (taken && pending != null && pending.length() > 0) {
            selected = null
            photoPath = pending.path
            CameraPhotos.keepOnly(context, pending)
            scanner.openCameraPhoto(CameraPhotos.uri(context, pending))
        } else pending?.delete()
    }
    val takePhoto = {
        cameraOpened = true
        cameraError = null
        val photo = CameraPhotos.create(context)
        pendingPhotoPath = photo.path
        try { takePicture.launch(CameraPhotos.uri(context, photo)) }
        catch (_: ActivityNotFoundException) {
            pendingPhotoPath = null
            cameraError = "No camera app is available. Take a photo with another app and use Start from a local file."
        }
    }
    val leave = { if (camera) CameraPhotos.keepOnly(context, null); onBack() }
    val focus = LocalFocusManager.current
    val scan = { focus.clearFocus(); selected = null; scannedUrl = url; scanner.open(url) }
    fun scanContent() { sharedInput?.let { if (clipboardEntry) scanner.openClipboard(it) else scanner.openShared(it) } }
    // Shares from other apps are not read, and nothing is downloaded, until the user taps Scan.
    val awaitingScan = sharedInput != null && !clipboardEntry && state == UrlScanState()
    // The scanner keeps results across recreation. After process death, restart the saved
    // input on returning to the scanner, without replacing pieces edited in Board setup.
    LaunchedEffect(scanner, sharedInput?.id, selected != null) {
        if (selected == null && scanner.uiState.value == UrlScanState()) {
            when {
                localFile -> {
                    val uri = localUri
                    if (uri != null) scanner.openLocalFile(Uri.parse(uri))
                    else if (!pickerOpened) chooseFile()
                }
                camera -> {
                    val photo = photoPath
                    if (photo != null) scanner.openCameraPhoto(CameraPhotos.uri(context, File(photo)))
                    else if (!cameraOpened) { CameraPhotos.keepOnly(context, null); takePhoto() }
                }
                sharedInput != null -> if (clipboardEntry) scanContent()
                scannedUrl != null -> scanner.open(scannedUrl!!)
            }
        }
    }
    BackHandler { if (selected != null) selected = null else leave() }
    val candidate = selected
    if (candidate != null) {
        key(candidate) {
            BoardSetupScreen(
                currentFen = null,
                initiallyFlipped = false,
                layout = settings.loadBoardLayoutSettings(),
                initialFen = candidate.content,
                importWarning = candidate.warning,
                imagePosition = candidate.kind == WebChessKind.IMAGE,
                onStart = { fen ->
                    onStartFen(fen).also { started ->
                        if (started) settings.saveFenToHistory(fen)
                        if (started && camera) { CameraPhotos.keepOnly(context, null); photoPath = null }
                    }
                },
                onBack = { selected = null }
            )
        }
        return
    }

    EvalScreen(
        topBar = {
            EvalTitleBar(title = when {
                localFile -> "Start from a local file"
                camera -> "Start from camera"
                clipboardEntry -> "Start from clipboard history"
                sharedInput != null -> "Shared content"
                else -> "Start from url"
            }, onBackClick = { if (selected != null) selected = null else leave() }, onEvalClick = leave)
        }
    ) {
        Spacer(Modifier.height(12.dp))
        if (camera) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = takePhoto, modifier = Modifier.weight(1f)) {
                    Text(if (photoPath == null) "Take photo" else "Take another photo")
                }
                if (state.busy) OutlinedButton(onClick = scanner::cancel) { Text("Stop") }
                else if (photoPath != null) OutlinedButton(onClick = {
                    scanner.openCameraPhoto(CameraPhotos.uri(context, File(photoPath!!)))
                }) { Text("Scan again") }
            }
            cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else if (localFile) {
            if (state.fileName.isNotBlank()) Text(state.fileName, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = chooseFile, modifier = Modifier.weight(1f)) {
                    Text(if (localUri == null) "Choose file" else "Choose another file")
                }
                if (state.busy) OutlinedButton(onClick = scanner::cancel) { Text("Stop") }
                else if (localUri != null) OutlinedButton(onClick = {
                    scanner.openLocalFile(Uri.parse(localUri!!))
                }) { Text("Scan again") }
            }
        } else if (sharedInput == null) {
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Page, document or image URL") },
                placeholder = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("import_url"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { scan() }))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = scan, enabled = url.isNotBlank() && !state.busy, modifier = Modifier.weight(1f)) { Text("Scan URL") }
                if (state.busy) OutlinedButton(onClick = scanner::cancel) { Text("Stop") }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scanContent() }, enabled = !state.busy) { Text(if (awaitingScan) "Scan" else "Scan again") }
                if (state.busy) OutlinedButton(onClick = scanner::cancel) { Text("Stop") }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.status.isNotEmpty()) Text(state.status, modifier = Modifier.padding(vertical = 8.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(when {
                    camera -> "Take a photo of a 2D chess diagram in a book or on a screen. Hold the phone straight above it so the whole board fills most of the photo. " +
                        "The photo is recognized on your device; review the position before starting. Angled photos of physical boards are not supported."
                    localFile -> "Choose text, PGN, PDF, Word (.docx), RTF, OpenDocument, EPUB, Excel/PowerPoint, ZIP or an image (up to 16 MB). Review image positions before starting. See Help for document limits."
                    awaitingScan -> "Another app shared ${sharedSummary(sharedInput!!)} with Eval. Nothing is read or downloaded until you tap Scan. " +
                        "Links to chess sites and .pgn files are then followed; other links are listed for you to choose."
                    else -> "Scans FEN, PGN, Lichess links and 2D board images. Image positions must be checked before starting."
                }, style = MaterialTheme.typography.bodySmall)
                if (awaitingScan) (sharedInput!!.texts + sharedInput.html).firstOrNull()?.let {
                    Text(it.take(300), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (state.hasPage) {
                item {
                    // Keep rendered pages attached for CSS/canvas board capture.
                    // Chess sites often size their board from the viewport height;
                    // a thumbnail-height viewport can collapse their board to zero.
                    // A retained page view may still sit in the previous Activity's layout.
                    AndroidView(factory = { scanner.pageView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                        modifier = Modifier.fillMaxWidth().height(520.dp).clipToBounds())
                    TextButton(onClick = scanner::rescan, enabled = !state.busy) { Text("Scan page again") }
                    Text("Scroll the page to reveal more boards, then scan again.", style = MaterialTheme.typography.bodySmall)
                }
            }
            itemsIndexed(state.results) { _, result ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = AppColors.BlueGrayAccent, contentColor = MaterialTheme.colorScheme.onSurface
                )) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(result.title, fontWeight = FontWeight.Bold)
                        Text(when (result.kind) { WebChessKind.PGN -> "PGN game"; WebChessKind.FEN -> "FEN position"; WebChessKind.IMAGE -> "Board image · review required" },
                            style = MaterialTheme.typography.labelMedium)
                        Text(result.content, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                        Text(result.source, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            scanner.cancel()
                            if (result.kind == WebChessKind.PGN) onStartPgn(result.content) else selected = result
                        }) { Text(if (result.kind == WebChessKind.PGN) "Open game" else "Review position") }
                    }
                }
            }
            if (state.links.isNotEmpty()) {
                item {
                    Text("Other links", fontWeight = FontWeight.Bold)
                    Text("Only chess sites and .pgn files are opened automatically. Scan a link to look for positions and games there.",
                        style = MaterialTheme.typography.bodySmall)
                }
                items(state.links.distinct(), key = { it }) { link ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(link, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scanner.scanLink(link) }, enabled = !state.busy) { Text("Scan link") }
                    }
                }
            }
            state.warnings.forEach { warning -> item { Text(warning, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

private fun sharedSummary(input: SharedChessInput): String {
    val texts = input.texts.size + input.html.size
    val files = input.streams.size
    return listOfNotNull(
        if (texts > 0) "$texts ${if (texts == 1) "text" else "texts"}" else null,
        if (files > 0) "$files ${if (files == 1) "file" else "files"}" else null
    ).joinToString(" and ").ifEmpty { "content" }
}
