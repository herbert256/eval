package com.eval.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.eval.data.WebChessCandidate
import com.eval.data.WebChessKind
import com.eval.data.SharedChessInput
import kotlinx.coroutines.CoroutineScope

private val ReviewCandidateSaver = listSaver<WebChessCandidate?, String>(
    save = { candidate -> candidate?.let {
        listOf(it.kind.name, it.content, it.title, it.source, it.needsReview.toString(), it.warning.orEmpty())
    } ?: emptyList() },
    restore = { if (it.isEmpty()) null else WebChessCandidate(WebChessKind.valueOf(it[0]), it[1], it[2], it[3], it[4].toBoolean(), it[5].ifEmpty { null }) }
)

@Composable
internal fun UrlGameScreen(
    onStartFen: (String) -> Boolean,
    onStartPgn: (String) -> Unit,
    onBack: () -> Unit,
    sharedInput: SharedChessInput? = null,
    localFile: Boolean = false,
    clipboardEntry: Boolean = false,
    scannerFactory: (android.content.Context, CoroutineScope) -> UrlGameScanner = { context, scope -> UrlGameScanner(context, scope) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { scannerFactory(context, scope) }
    DisposableEffect(scanner) { onDispose { scanner.close() } }
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
    val focus = LocalFocusManager.current
    val scan = { focus.clearFocus(); selected = null; scannedUrl = url; scanner.open(url) }
    fun scanContent() { sharedInput?.let { if (clipboardEntry) scanner.openClipboard(it) else scanner.openShared(it) } }
    // Preserve the selected result and editor draft across recreation. Rebuild
    // results only on returning to the scanner, without replacing edited pieces.
    LaunchedEffect(scanner, sharedInput?.id, selected != null) {
        if (selected == null && !state.busy && state.results.isEmpty()) {
            when {
                localFile -> {
                    val uri = localUri
                    if (uri != null) scanner.openLocalFile(Uri.parse(uri))
                    else if (!pickerOpened) chooseFile()
                }
                sharedInput != null -> scanContent()
                scannedUrl != null -> scanner.open(scannedUrl!!)
            }
        }
    }
    BackHandler { if (selected != null) selected = null else onBack() }
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
                onStart = { fen -> onStartFen(fen).also { if (it) settings.saveFenToHistory(fen) } },
                onBack = { selected = null }
            )
        }
        return
    }

    EvalScreen(
        topBar = {
            EvalTitleBar(title = when {
                localFile -> "Start from a local file"
                clipboardEntry -> "Start from clipboard history"
                sharedInput != null -> "Shared content"
                else -> "Start from url"
            }, onBackClick = { if (selected != null) selected = null else onBack() }, onEvalClick = onBack)
        }
    ) {
        Spacer(Modifier.height(12.dp))
        if (localFile) {
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
                Button(onClick = { scanContent() }, enabled = !state.busy) { Text("Scan again") }
                if (state.busy) OutlinedButton(onClick = scanner::cancel) { Text("Stop") }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.status.isNotEmpty()) Text(state.status, modifier = Modifier.padding(vertical = 8.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(if (localFile) "Choose text, PGN, PDF, Word (.docx), RTF, OpenDocument, EPUB, Excel/PowerPoint, ZIP or an image (up to 16 MB). Review image positions before starting. See Help for document limits."
                    else "Scans FEN, PGN, Lichess links and 2D board images. Image positions must be checked before starting.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.hasPage) {
                item {
                    // Keep rendered pages attached for CSS/canvas board capture.
                    // Chess sites often size their board from the viewport height;
                    // a thumbnail-height viewport can collapse their board to zero.
                    AndroidView(factory = { scanner.pageView }, modifier = Modifier.fillMaxWidth().height(520.dp).clipToBounds())
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
            state.warnings.forEach { warning -> item { Text(warning, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
