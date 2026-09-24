package com.eval.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.eval.data.ChessWebExtractor
import com.eval.data.WebChessCandidate
import com.eval.data.WebChessKind
import com.eval.data.SharedChessInput
import com.eval.data.SharedChessText
import com.eval.data.ChessDocumentReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class UrlScanState(
    val busy: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val results: List<WebChessCandidate> = emptyList(),
    val warnings: List<String> = emptyList(),
    val pageUrl: String = "",
    val hasPage: Boolean = false,
    val fileName: String = ""
)

internal class UrlGameScanner(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(false).build(),
    private val loadPage: (WebView, String, String) -> Unit = { view, url, _ -> view.loadUrl(url) }
) {
    private val state = MutableStateFlow(UrlScanState())
    val uiState = state.asStateFlow()
    private val recognizer = BoardImageRecognizer(context)
    private val script = context.assets.open("chess-page-scan.js").bufferedReader().use { it.readText() }
    private var job: Job? = null
    private var pageReady: CompletableDeferred<Unit>? = null
    private var destroyed = false
    private val visitedUrls = linkedSetOf<String>()
    val pageView = WebView(context).apply {
        // WRAP_CONTENT makes WebView report a zero-height CSS viewport inside
        // Compose's scrolling list, even when its Android view has a real size.
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) { pageReady?.complete(Unit) }
            override fun onPageFinished(view: WebView?, url: String?) { pageReady?.complete(Unit) }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
                if (request.isForMainFrame) pageReady?.completeExceptionally(IOException("The page could not be loaded."))
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame && request.url.scheme == "https") open(request.url.toString())
                return true
            }
        }
        setDownloadListener { url, _, _, _, _ -> if (url.startsWith("https://")) open(url) }
    }

    fun open(input: String) {
        val url = try { ChessWebExtractor.normalizeUrl(input) } catch (e: IllegalArgumentException) {
            state.value = state.value.copy(error = e.message)
            return
        }
        job?.cancel()
        visitedUrls.clear()
        recognizer.close()
        pageReady = null
        pageView.stopLoading()
        state.value = UrlScanState(busy = true, status = "Loading page…", pageUrl = url)
        job = scope.launch {
            try {
                scanUrl(url)
                finish()
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    recognizer.close()
                    state.value = state.value.copy(busy = false, status = "", error = "The scan timed out. Try scanning the page again.")
                }
                else throw e
            } catch (e: Exception) {
                state.value = state.value.copy(busy = false, status = "", error = e.message ?: "Could not scan this page.")
            }
        }
    }

    private suspend fun scanUrl(url: String) {
        if (url in visitedUrls) return
        if (visitedUrls.size >= 12) { warn("Scanned the first 12 URLs. Open a specific link to scan more."); return }
        visitedUrls += url
        add(withContext(Dispatchers.Default) { ChessWebExtractor.extract(url, url) })
        // Analysis URLs contain everything needed, even if the remote page is unavailable.
        val response = download(ChessWebExtractor.pgnLink(url) ?: url, 16_000_000)
        currentCoroutineContext().ensureActive()
        when {
            ChessDocumentReader.isDocument(response.data, response.type, response.url) -> {
                val links = linkedSetOf<String>()
                val images = linkedSetOf<String>()
                readDocument(response.data, response.type, response.url) { value, html, origin ->
                    val parsed = withContext(Dispatchers.Default) { SharedChessText.parse(value, html, origin) }
                    add(parsed.candidates)
                    links += parsed.urls
                    images += parsed.images
                }
                for (image in images.take(20)) {
                    try {
                        if (image.startsWith("https://")) {
                            val bytes = download(image, 4_000_000)
                            scanImage(bytes.data, bytes.type, image)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { warn("Could not read a document image: $image") }
                }
                for (link in links.filterNot { it in images }.take(12)) {
                    try { scanUrl(ChessWebExtractor.normalizeUrl(link)) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { warn("Could not read a document link: $link") }
                }
            }
            response.type.startsWith("image/") -> scanImage(response.data, response.type, url)
            response.type.contains("html") || response.data.take(200).toByteArray().toString(Charsets.UTF_8).trimStart().startsWith("<") -> {
                state.value = state.value.copy(hasPage = true, pageUrl = response.url)
                // Let Compose measure the preview before viewport-dependent widgets initialize.
                withTimeoutOrNull(500) { while (pageView.width == 0 || pageView.height == 0) delay(16) }
                val ready = CompletableDeferred<Unit>()
                pageReady = ready
                loadPage(pageView, response.url, response.data.toString(response.charset))
                withTimeout(25_000) { ready.await() }
                delay(1200)
                scanPage()
            }
            else -> add(withContext(Dispatchers.Default) {
                ChessWebExtractor.extract(response.data.toString(response.charset), response.url)
            })
        }
    }

    /** Shares use the same results, URL retrieval, and local image recognizer as manual imports. */
    fun openShared(input: SharedChessInput) = scanContent(input, localFile = false)

    fun openClipboard(input: SharedChessInput) = scanContent(input, localFile = false, source = "Clipboard")

    fun openLocalFile(uri: Uri) = scanContent(SharedChessInput(streams = listOf(uri)), localFile = true)

    private fun scanContent(input: SharedChessInput, localFile: Boolean, source: String = "Shared") {
        cancel()
        visitedUrls.clear()
        state.value = UrlScanState(busy = true,
            status = if (localFile) "Reading local file…" else "Reading ${source.lowercase()} content…", warnings = input.warnings)
        job = scope.launch {
            val urls = linkedSetOf<String>()
            val images = linkedSetOf<String>()
            suspend fun text(value: String, html: Boolean, origin: String) {
                if (value.length > SharedChessInput.MAX_TEXT) warn(if (localFile) "File text was limited to 2 MB." else "$source text was limited to 2 MB.")
                val parsed = withContext(Dispatchers.Default) { SharedChessText.parse(value, html, origin) }
                add(parsed.candidates)
                urls += parsed.urls
                images += parsed.images
            }
            suspend fun attempt(label: String, block: suspend () -> Unit) {
                try { block() }
                catch (e: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    recognizer.close()
                    warn("$label timed out. Results found so far are available.")
                }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { warn("$label: ${e.message ?: "Could not read this item."}") }
            }
            try {
                for (value in input.texts) text(value, input.mimeType == "text/html", "$source text")
                for (value in input.html) text(value, true, "$source HTML")
                for ((index, uri) in input.streams.withIndex()) {
                    state.value = state.value.copy(status = if (localFile) "Reading local file…"
                        else "Reading ${source.lowercase()} file ${index + 1} of ${input.streams.size}…")
                    attempt(if (localFile) "Local file" else "$source file ${index + 1}") {
                        val file = readContentFile(uri, input.mimeType)
                        if (localFile) state.value = state.value.copy(fileName = file.name)
                        readDocument(file.data, file.type, file.name, ::text)
                    }
                }
                if (images.size > 20) warn("Scanned the first 20 images in the content.")
                for (image in images.take(20)) {
                    attempt("$source image") {
                        if (image.startsWith("data:image/")) {
                            require(image.length <= 5_500_000) { "This image is too large." }
                            recognize(image, "$source board image", "$source HTML")
                        } else if (image.startsWith("https://")) {
                            val response = download(image, 4_000_000)
                            scanImage(response.data, response.type, image)
                        }
                    }
                }
                // Text (including captions) and attachments are kept even when a shared link fails.
                val links = urls.filterNot { it in images }
                if (links.size > 12) warn("Scanned the first 12 URLs. Open a specific link to scan more.")
                for ((index, url) in links.take(12).withIndex()) {
                    state.value = state.value.copy(status = "Scanning URL ${index + 1} of ${minOf(links.size, 12)}…")
                    attempt(url) { scanUrl(ChessWebExtractor.normalizeUrl(url)) }
                }
                finish()
                if (state.value.results.isEmpty()) state.value = state.value.copy(status = if (localFile)
                    "No chess positions or games found in this file. Choose a document containing FEN or PGN, or a clear 2D board image."
                    else if (source == "Clipboard") "No chess positions or games found in this entry. Choose another entry containing FEN, PGN, a chess URL or a clear 2D board image."
                    else "No chess positions or games found. Share FEN or PGN text, a chess page URL, a PGN file, or a clear 2D board image.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = state.value.copy(busy = false, status = "", error = e.message ?: "Could not read this content.") }
        }
    }

    private data class SharedFile(val data: ByteArray, val type: String, val name: String)

    private suspend fun readContentFile(uri: Uri, fallbackType: String?): SharedFile = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose or share a file that provides a readable content URI." }
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Selected file"
        val type = resolver.getType(uri) ?: fallbackType.orEmpty()
        val data = resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            while (true) {
                ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 16_000_000) { "$name is too large (limit 16 MB)." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("$name could not be opened. Choose or share it again.")
        // File managers sometimes send PNG/PGN files as application/octet-stream.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        SharedFile(data, if (bounds.outWidth > 0) bounds.outMimeType ?: "image/png" else type, name)
    }

    private suspend fun readDocument(data: ByteArray, type: String, name: String,
        text: suspend (String, Boolean, String) -> Unit) {
        state.value = state.value.copy(status = "Reading $name…")
        ChessDocumentReader(context,
            onText = { value, html, origin -> withContext(Dispatchers.Main) { text(value, html, origin) } },
            onImage = { bytes, mime, origin -> withContext(Dispatchers.Main) {
                state.value = state.value.copy(status = "Scanning image: $origin…")
                scanImage(bytes, mime, origin)
            } },
            onWarning = { warning -> withContext(Dispatchers.Main) { warn(warning) } }
        ).read(data, type, name)
    }

    fun rescan() {
        if (state.value.busy || !state.value.hasPage) return
        job = scope.launch {
            state.value = state.value.copy(busy = true, error = null, warnings = emptyList())
            try { scanPage(); finish() }
            catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    recognizer.close()
                    state.value = state.value.copy(busy = false, status = "", error = "The image scan timed out.")
                }
                else throw e
            }
            catch (e: Exception) { state.value = state.value.copy(busy = false, status = "", error = e.message) }
        }
    }

    private suspend fun scanPage() {
        state.value = state.value.copy(status = "Finding positions, games and images…")
        val raw = pageView.evaluate(script)
        val document = JSONObject(JSONTokener(raw).nextValue() as? String ?: error("Could not read the page."))
        val source = state.value.pageUrl
        val texts = document.getJSONArray("texts")
        val extracted = withContext(Dispatchers.Default) {
            val candidates = mutableListOf<WebChessCandidate>()
            for (i in 0 until texts.length()) {
                ensureActive()
                candidates += ChessWebExtractor.extract(texts.getString(i), source)
                if (candidates.size >= 500) break
            }
            candidates
        }
        add(extracted)
        val links = document.getJSONArray("links")
        val pgnLinks = (0 until links.length()).mapNotNull { ChessWebExtractor.pgnLink(links.getString(it)) }.distinct()
        if (pgnLinks.size > 12) warn("Scanned the first 12 PGN links. Open a specific link to scan more.")
        for ((index, url) in pgnLinks.take(12).withIndex()) {
            state.value = state.value.copy(status = "Reading game link ${index + 1} of ${minOf(pgnLinks.size, 12)}…")
            try {
                val response = download(url, 4_000_000)
                add(withContext(Dispatchers.Default) { ChessWebExtractor.extract(response.data.toString(response.charset), url) })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { warn("Could not read a linked game: $url") }
        }
        val images = document.getJSONArray("images")
        val seen = mutableSetOf<String>()
        for (index in 0 until images.length()) {
            currentCoroutineContext().ensureActive()
            val image = images.getJSONObject(index)
            val url = image.optString("url")
            if (!seen.add(url)) continue
            state.value = state.value.copy(status = "Scanning image ${index + 1} of ${images.length()}…")
            try {
                if (url.startsWith("data:image/")) {
                    if (url.length <= 5_500_000) recognize(url, image.optString("label", "Board image"), source)
                } else if (url.startsWith("https://")) {
                    val response = download(url, 4_000_000)
                    scanImage(response.data, response.type, url)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { warn("Some page images could not be read. Scroll to the board and use Scan page again.") }
        }
        // Covers CSS pieces, cross-origin canvases, and diagrams without an image URL.
        if (pageView.width > 0 && pageView.height > 0) {
            state.value = state.value.copy(status = "Scanning the visible page…")
            val bitmap = Bitmap.createBitmap(pageView.width, pageView.height, Bitmap.Config.ARGB_8888)
            pageView.draw(Canvas(bitmap))
            try {
                val data = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                }
                scanImage(data, "image/png", source)
            } finally { bitmap.recycle() }
        }
        if (document.optBoolean("limited")) warn("This page is large: scanned up to 2 MB of text and 20 images. Open a specific image or scroll and scan again for more.")
    }

    private suspend fun scanImage(bytes: ByteArray, type: String, source: String) {
        val dataUrl = withContext(Dispatchers.Default) {
            if (type.substringBefore(';') == "image/svg+xml") {
                "data:image/svg+xml;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format could not be read." }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: error("This image could not be read.")
                try {
                    ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    }
                } finally { bitmap.recycle() }
            }
        }
        recognize(dataUrl, "Board image", source)
    }

    private suspend fun recognize(dataUrl: String, title: String, source: String) {
        val result = recognizer.recognize(dataUrl) ?: return
        add(listOf(WebChessCandidate(WebChessKind.IMAGE, result.reviewFen(),
            title, source, needsReview = true, warning = result.reviewWarning())))
    }

    private fun add(candidates: List<WebChessCandidate>) {
        val combined = (state.value.results + candidates).distinctBy {
            if (it.kind == WebChessKind.PGN) "PGN:${it.content}" else "${it.kind}:${it.content}"
        }
        state.value = state.value.copy(results = combined.take(ChessWebExtractor.MAX_RESULTS))
        if (combined.size > ChessWebExtractor.MAX_RESULTS) warn("Showing the first 100 results. Open a more specific page to find more.")
    }

    private fun warn(message: String) { state.value = state.value.copy(warnings = (state.value.warnings + message).distinct()) }
    private fun finish() {
        state.value = state.value.copy(busy = false, status = if (state.value.results.isEmpty())
            "No chess positions or games found. Scroll to a board and scan again, or try a direct PGN or image URL."
            else "Found ${state.value.results.size} ${if (state.value.results.size == 1) "result" else "results"}.")
    }

    fun cancel() {
        job?.cancel()
        pageReady = null
        pageView.stopLoading()
        recognizer.close()
        state.value = state.value.copy(busy = false, status = if (state.value.busy)
            "Scan stopped. Results found so far are available." else state.value.status)
    }

    fun close() {
        if (destroyed) return
        destroyed = true
        cancel()
        pageView.destroy()
    }

    private data class Download(val data: ByteArray, val type: String, val url: String, val charset: java.nio.charset.Charset)

    private suspend fun download(url: String, limit: Long): Download = withContext(Dispatchers.IO) {
        ChessWebExtractor.normalizeUrl(url)
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Eval chess page scanner")
            .header("Accept", "text/html, application/pdf, application/x-chess-pgn, image/*, */*; q=0.9").build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Could not load the URL. ${e.message}", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val downloaded = response.use {
                            require(it.isSuccessful) { "The server returned HTTP ${it.code}." }
                            val body = it.body ?: error("The page was empty.")
                            require(body.contentLength() <= limit) { "This file is too large (limit ${limit / 1_000_000} MB)." }
                            val source = body.source()
                            require(!source.request(limit + 1)) { "This file is too large (limit ${limit / 1_000_000} MB)." }
                            Download(source.readByteArray(), body.contentType()?.toString().orEmpty(), it.request.url.toString(),
                                body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                        }
                        if (continuation.isActive) continuation.resume(downloaded)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
}
