package com.eval.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eval.data.ClipboardHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ClipboardHistoryScreen(
    onStartFen: (String) -> Boolean,
    onStartPgn: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val history = remember { ClipboardHistory.get(context) }
    val state by history.uiState.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.entries.firstOrNull { it.input.id == selectedId }
    LaunchedEffect(history) { history.captureCurrent() }
    BackHandler { if (selectedId != null) selectedId = null else onBack() }
    if (selected != null) {
        key(selected.input.id) {
            UrlGameScreen(sharedInput = selected.input, clipboardEntry = true,
                onStartFen = onStartFen, onStartPgn = onStartPgn, onBack = { selectedId = null })
        }
        return
    }
    EvalScreen(topBar = {
        EvalTitleBar(title = "Start from clipboard history", onBackClick = onBack, onEvalClick = onBack)
    }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("The last 10 entries captured by Eval, newest first.", modifier = Modifier.padding(top = 12.dp))
                Text("Android provides the current clipboard item when you return to Eval and changes while Eval is open. Earlier keyboard clipboard history is not available here.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = history::captureCurrent) { Text("Refresh clipboard") }
                    TextButton(onClick = { history.clear() }, enabled = state.entries.isNotEmpty()) { Text("Clear history") }
                }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text("${state.entries.size} of 10 entries", style = MaterialTheme.typography.labelMedium)
            }
            if (!state.loading && state.entries.isEmpty()) item {
                Text("No clipboard entries yet. Copy text, a URL or an image, then return to Eval.")
            }
            items(state.entries, key = { it.input.id }) { entry ->
                Card(Modifier.fillMaxWidth().clickable { selectedId = entry.input.id },
                    colors = CardDefaults.cardColors(containerColor = AppColors.BlueGrayAccent,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        entry.input.streams.firstOrNull()?.let { ClipboardThumbnail(it) }
                        Text(entry.preview, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.capturedAt)),
                            style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { selectedId = entry.input.id }) { Text("Scan entry") }
                            TextButton(onClick = { history.remove(entry.input.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardThumbnail(uri: Uri) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 256) inSampleSize *= 2
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    thumbnail?.let {
        Image(it.asImageBitmap(), contentDescription = "Clipboard image preview",
            modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Fit)
    }
}
