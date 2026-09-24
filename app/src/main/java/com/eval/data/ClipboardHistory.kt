package com.eval.data

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

internal data class ClipboardEntry(
    val input: SharedChessInput,
    val title: String,
    val preview: String,
    val capturedAt: Long,
    val fingerprint: String,
    val files: List<String>
)

internal data class ClipboardHistoryState(
    val entries: List<ClipboardEntry> = emptyList(),
    val loading: Boolean = true,
    val notice: String? = null
)

/** App-private, bounded history of clips Android actually lets Eval read. No background polling. */
internal class ClipboardHistory(
    context: Context,
    private val directory: File = File(context.filesDir, "clipboard-history")
) {
    private val context = context.applicationContext
    private val attachments = File(directory, "attachments")
    private val index = AtomicFile(File(directory, "history.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Serialize captures, deletion and persistence so rapid copies retain their order.
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    private val state = MutableStateFlow(ClipboardHistoryState())
    val uiState = state.asStateFlow()
    private var lastObserved = ""

    init {
        scope.launch {
            try { load() }
            catch (_: Exception) {
                state.value = ClipboardHistoryState(loading = false, notice = "Clipboard history could not be read.")
            }
            for (command in commands) command()
        }
    }

    private fun enqueue(action: () -> Unit): Deferred<Unit> {
        val done = CompletableDeferred<Unit>()
        if (!commands.trySend {
            try { action(); done.complete(Unit) }
            catch (e: Exception) {
                state.value = state.value.copy(notice = "Could not save clipboard history. Try again.")
                done.completeExceptionally(e)
            }
        }.isSuccess) done.completeExceptionally(IllegalStateException("History is closed"))
        return done
    }

    /** Call only from a focused Activity or an explicit clipboard action. */
    fun captureCurrent() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        runCatching { clipboard.primaryClip }.getOrNull()?.let { record(it) }
    }

    fun record(clip: ClipData): Deferred<Unit> {
        // Snapshot text immediately; file reads and all persistence happen off the UI thread.
        if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) {
            return enqueue { state.value = state.value.copy(notice = "Items marked sensitive are not saved.") }
        }
        val input = SharedChessInput.fromIntent(Intent(Intent.ACTION_SEND).apply {
            type = if (clip.description.mimeTypeCount > 0) clip.description.getMimeType(0) else null
            clipData = clip
        })!!
        val label = clip.description.label?.toString()?.take(120).orEmpty()
        val timestamp = clip.description.timestamp
        return enqueue {
            val token = digest(JSONObject().put("text", JSONArray(input.texts)).put("html", JSONArray(input.html))
                .put("uris", JSONArray(input.streams.map(Uri::toString))).put("type", input.mimeType)
                .put("time", timestamp).toString().toByteArray())
            capture(input, label, token)
        }
    }

    fun remove(id: String) = enqueue { save(state.value.entries.filterNot { it.input.id == id }, lastObserved) }
    fun clear() = enqueue { save(emptyList(), lastObserved) }
    internal fun awaitIdle() = enqueue { }
    internal fun close() { commands.close(); scope.cancel() }

    private fun capture(input: SharedChessInput, label: String, token: String) {
        if (token == lastObserved) return
        if (input.texts.isEmpty() && input.html.isEmpty() && input.streams.isEmpty() && input.warnings.isEmpty()) return
        attachments.mkdirs()
        val warnings = input.warnings.map { it.replace("Shared", "Clipboard").replace("shared", "clipboard") }.toMutableList()
        val files = mutableListOf<String>()
        val hashes = mutableListOf<String>()
        var remaining = MAX_FILE_BYTES
        try {
            for ((number, uri) in input.streams.withIndex()) {
                var destination: File? = null
                try {
                    val resolver = context.contentResolver
                    val originalName = runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull() ?: "clipboard-file"
                    val name = originalName.takeLast(100).replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    destination = File(attachments, "${input.id}-$number-$name")
                    val hash = MessageDigest.getInstance("SHA-256")
                    resolver.openInputStream(uri)?.use { source ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(16_384)
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                require(count <= remaining) { "Clipboard files exceed the 16 MB limit." }
                                remaining -= count
                                output.write(buffer, 0, count)
                                hash.update(buffer, 0, count)
                            }
                        }
                    } ?: error("Cannot open clipboard file")
                    files += destination.name
                    hashes += hash.digest().hex()
                } catch (_: Exception) {
                    destination?.delete()
                    warnings += if (remaining < 16_384) "Clipboard files were limited to 16 MB."
                        else "A clipboard file could not be saved. Copy it again while Eval is open, or use Start from a local file."
                }
            }
            val savedInput = input.copy(streams = files.map(::fileUri), warnings = warnings.distinct())
            val fingerprint = digest(JSONObject().put("text", JSONArray(input.texts)).put("html", JSONArray(input.html))
                .put("files", JSONArray(hashes)).put("type", input.mimeType).put("warnings", JSONArray(warnings))
                .toString().toByteArray())
            val kind = when {
                input.streams.isNotEmpty() -> if (input.mimeType?.startsWith("image/") == true) "Image" else "File"
                input.html.isNotEmpty() -> "HTML"
                input.texts.any { it.trim().startsWith("https://", true) || it.trim().startsWith("http://", true) } -> "URL"
                else -> "Text"
            }
            val preview = input.texts.firstOrNull()?.take(300)
                ?: input.html.firstOrNull()?.take(300)
                ?: files.joinToString { it.substringAfter("${input.id}-").substringAfter('-') }.take(300)
                    .ifBlank { "No readable text or file. Select to see details." }
            val entry = ClipboardEntry(savedInput, if (label.isBlank()) kind else "$kind · $label", preview,
                System.currentTimeMillis(), fingerprint, files)
            save((listOf(entry) + state.value.entries.filterNot { it.fingerprint == fingerprint }).take(10), token)
        } catch (e: Exception) {
            files.forEach { File(attachments, it).delete() }
            throw e
        }
    }

    private fun fileUri(name: String): Uri {
        require(name == File(name).name && name != "." && name != "..")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(attachments, name))
    }

    private fun save(entries: List<ClipboardEntry>, observed: String) {
        directory.mkdirs()
        val json = JSONObject().put("observed", observed).put("entries", JSONArray(entries.map { entry ->
            JSONObject().put("id", entry.input.id).put("title", entry.title).put("preview", entry.preview)
                .put("time", entry.capturedAt).put("hash", entry.fingerprint).put("files", JSONArray(entry.files))
                .put("text", JSONArray(entry.input.texts)).put("html", JSONArray(entry.input.html))
                .put("type", entry.input.mimeType).put("warnings", JSONArray(entry.input.warnings))
        }))
        val stream = index.startWrite()
        try { stream.write(json.toString().toByteArray()); index.finishWrite(stream) }
        catch (e: Exception) { index.failWrite(stream); throw e }
        lastObserved = observed
        state.value = ClipboardHistoryState(entries = entries, loading = false)
        val retained = entries.flatMap { it.files }.toSet()
        attachments.listFiles()?.filterNot { it.name in retained }?.forEach { it.delete() }
    }

    private fun load() {
        val contents = try { index.openRead().bufferedReader().use { it.readText() } }
        catch (_: FileNotFoundException) { state.value = ClipboardHistoryState(loading = false); return }
        val json = JSONObject(contents)
        lastObserved = json.optString("observed")
        val list = json.getJSONArray("entries")
        val entries = (0 until minOf(list.length(), 10)).map { n ->
            val value = list.getJSONObject(n)
            fun strings(key: String): List<String> = value.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
            val files = strings("files")
            ClipboardEntry(SharedChessInput(id = value.getString("id"), texts = strings("text"), html = strings("html"),
                streams = files.map(::fileUri), mimeType = value.optString("type").takeIf(String::isNotBlank), warnings = strings("warnings")),
                value.getString("title"), value.getString("preview"), value.getLong("time"), value.getString("hash"), files)
        }
        state.value = ClipboardHistoryState(entries = entries, loading = false)
    }

    companion object {
        private const val MAX_FILE_BYTES = 16_000_000
        // The store keeps only applicationContext; no Activity or View is retained.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: ClipboardHistory? = null
        fun get(context: Context): ClipboardHistory = instance ?: synchronized(this) {
            instance ?: ClipboardHistory(context).also { instance = it }
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    }
}
