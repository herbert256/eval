package com.eval.ui

import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
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
    val fileName: String = "",
    /** Links found in content that are not chess sites or .pgn files; opened only when the user asks. */
    val links: List<String> = emptyList()
)

internal class UrlGameScanner(
    context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = sharedClient,
    /** Renders the downloaded HTML instead of letting the WebView load the page (for tests without network). */
    private val loadPage: ((WebView, String, String) -> Unit)? = null
) {
    private val context = context.applicationContext
    // Pages need an Activity for popups such as <select>; [attach] swaps it so a retained scanner leaks none.
    private val viewContext = MutableContextWrapper(context)
    private val state = MutableStateFlow(UrlScanState())
    val uiState = state.asStateFlow()
    private val recognizer = BoardImageRecognizer(this.context)
    private val script = this.context.assets.open("chess-page-scan.js").bufferedReader().use { it.readText() }
    private var job: Job? = null
    private var pageReady: CompletableDeferred<Unit>? = null
    private var destroyed = false
    private val visitedUrls = linkedSetOf<String>()
    private var page: WebView? = null
    // Completed when a renderer that [scanPage] terminated has actually exited.
    private var terminatedRenderer: CompletableDeferred<Unit>? = null

    /** The rendered page, created on first use and again after its renderer process has gone. */
    val pageView: WebView get() = page ?: createPageView().also { page = it }

    private fun createPageView() = WebView(viewContext).apply {
        // WRAP_CONTENT makes WebView report a zero-height CSS viewport inside
        // Compose's scrolling list, even when its Android view has a real size.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
            // Returning true keeps Eval alive when a heavy or hostile page crashes the renderer.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                terminatedRenderer?.complete(Unit)
                pageGone(view)
                return true
            }
        }
        setDownloadListener { url, _, _, _, _ -> if (url.startsWith("https://")) open(url) }
    }

    private fun pageGone(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        if (view !== page) return
        page = null
        job?.cancel()
        pageReady = null
        state.value = state.value.copy(busy = false, status = "", hasPage = false, error = "The page crashed; scan again.")
    }

    /** Pages show popups in [context]; pass the application context while no Activity is attached. */
    fun attach(context: Context) { viewContext.baseContext = context }

    fun open(input: String) {
        val url = try { ChessWebExtractor.normalizeUrl(input) } catch (e: IllegalArgumentException) {
            state.value = state.value.copy(error = e.message)
            return
        }
        job?.cancel()
        visitedUrls.clear()
        recognizer.close()
        pageReady = null
        page?.stopLoading()
        state.value = UrlScanState(busy = true, status = "Loading page…", pageUrl = url)
        launchScan("The scan timed out. Try scanning the page again.", "Could not scan this page.") {
            scanUrl(url)
            finish()
        }
    }

    /** Scans a link the user chose from [UrlScanState.links]; earlier results are kept. */
    fun scanLink(url: String) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, error = null, status = "Scanning link…", links = state.value.links - url)
        launchScan("The scan timed out. Try the link again.", "Could not scan this link.") {
            scanUrl(ChessWebExtractor.normalizeUrl(url), explicit = true)
            finish()
        }
    }

    /** Runs a scan; timeouts and unreadable content, including out-of-memory errors, end it with a message. */
    private fun launchScan(timedOut: String, failed: String, block: suspend () -> Unit) {
        job = scope.launch {
            try { block() }
            catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                recognizer.close()
                state.value = state.value.copy(busy = false, status = "", error = timedOut)
            } catch (e: Throwable) {
                state.value = state.value.copy(busy = false, status = "", error = describe(e, failed))
            }
        }
    }

    private suspend fun scanUrl(url: String, explicit: Boolean = false) {
        if (url in visitedUrls) return
        if (!explicit && visitedUrls.size >= 12) { warn("Scanned the first 12 URLs. Open a specific link to scan more."); return }
        visitedUrls += url
        add(withContext(Dispatchers.Default) { ChessWebExtractor.extract(url, url) })
        // Analysis URLs contain everything needed, even if the remote page is unavailable.
        val response = download(ChessWebExtractor.pgnLink(url) ?: url, 16_000_000, pageOnly = loadPage == null)
        currentCoroutineContext().ensureActive()
        when {
            response.page -> showPage(response.url, null)
            ChessDocumentReader.isDocument(response.data, response.type, response.url) -> {
                val links = linkedSetOf<String>()
                val images = linkedSetOf<String>()
                readDocument(response.data, response.type, response.url) { value, html, origin ->
                    val parsed = withContext(Dispatchers.Default) { SharedChessText.parse(value, html, origin) }
                    add(parsed.candidates)
                    links += parsed.urls
                    images += parsed.images
                }
                scanFound(links, images, "Document", response.url)
            }
            response.type.startsWith("image/") -> scanImage(response.data, response.type, url)
            response.type.contains("html") || response.data.take(200).toByteArray().toString(Charsets.UTF_8).trimStart().startsWith("<") ->
                showPage(response.url, response.text())
            else -> add(withContext(Dispatchers.Default) { ChessWebExtractor.extract(response.text(), response.url) })
        }
    }

    private suspend fun showPage(url: String, html: String?) {
        state.value = state.value.copy(hasPage = true, pageUrl = url)
        val view = pageView
        // Let Compose measure the preview before viewport-dependent widgets initialize.
        withTimeoutOrNull(500) { while (view.width == 0 || view.height == 0) delay(16) }
        val ready = CompletableDeferred<Unit>()
        pageReady = ready
        val render = loadPage
        // The WebView fetches the page itself, so the scanner downloads only its headers.
        if (render != null && html != null) render(view, url, html) else view.loadUrl(url)
        withTimeout(25_000) { ready.await() }
        delay(1200)
        scanPage()
    }

    /**
     * Images and links found in content. Embedded images are scanned; remote images and links are followed only
     * on chess sites or as .pgn files, so documents cannot make Eval contact arbitrary hosts. Others are listed.
     */
    private suspend fun scanFound(urls: Collection<String>, images: Collection<String>, label: String, origin: String) {
        val scanned = images.filter { it.startsWith("data:image/") || (it.startsWith("https://") && ChessWebExtractor.autoFollow(it)) }
        if (scanned.size > 20) warn("Scanned the first 20 images in the content.")
        for (image in scanned.take(20)) {
            attempt("$label image") {
                if (image.startsWith("data:image/")) {
                    require(image.length <= 5_500_000) { "This image is too large." }
                    recognize(image, "$label board image", origin)
                } else {
                    val response = download(image, 4_000_000)
                    scanImage(response.data, response.type, image)
                }
            }
        }
        val links = urls.filterNot { it in images }
        val follow = links.filter(ChessWebExtractor::autoFollow)
        offer(links.filterNot(ChessWebExtractor::autoFollow) + images.filter { it.startsWith("https://") && it !in scanned })
        if (follow.size > 12) warn("Scanned the first 12 URLs. Open a specific link to scan more.")
        for ((index, url) in follow.take(12).withIndex()) {
            state.value = state.value.copy(status = "Scanning URL ${index + 1} of ${minOf(follow.size, 12)}…")
            attempt(url) { scanUrl(ChessWebExtractor.normalizeUrl(url)) }
        }
    }

    /** Lists HTTPS links for the user to scan explicitly. */
    private fun offer(urls: Collection<String>) {
        val links = urls.mapNotNull { runCatching { ChessWebExtractor.normalizeUrl(it) }.getOrNull() }.filterNot { it in visitedUrls }
        if (links.isNotEmpty()) state.value = state.value.copy(links = (state.value.links + links).distinct().take(30))
    }

    /** One failing item keeps the results found so far. */
    private suspend fun attempt(label: String, block: suspend () -> Unit) {
        try { block() }
        catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            recognizer.close()
            warn("$label timed out. Results found so far are available.")
        }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { warn("$label: ${describe(e, "Could not read this item.")}") }
    }

    /** Shares use the same results, URL retrieval, and local image recognizer as manual imports. */
    fun openShared(input: SharedChessInput) = scanContent(input.withoutOwnFiles(context), localFile = false)

    /** Entries come from Eval's clipboard history, whose saved attachments are served by Eval's own provider. */
    fun openClipboard(input: SharedChessInput) = scanContent(input, localFile = false, source = "Clipboard")

    fun openLocalFile(uri: Uri) = scanContent(SharedChessInput(streams = listOf(uri)), localFile = true)

    /** Photos from the camera app use the same image recognizer as image files. */
    fun openCameraPhoto(uri: Uri) {
        cancel()
        visitedUrls.clear()
        state.value = UrlScanState(busy = true, status = "Scanning photo…")
        launchScan("The photo scan timed out. Use Scan again or take another photo.", "Could not read this photo.") {
            val photo = readContentFile(uri, "image/jpeg")
            scanImage(photo.data, photo.type, "Camera photo")
            finish()
            if (state.value.results.isEmpty()) state.value = state.value.copy(status = "No chessboard found in this photo. " +
                "Hold the phone straight above a 2D diagram so the whole board fills most of the photo, then take another photo.")
        }
    }

    private fun scanContent(input: SharedChessInput, localFile: Boolean, source: String = "Shared") {
        cancel()
        visitedUrls.clear()
        state.value = UrlScanState(busy = true,
            status = if (localFile) "Reading local file…" else "Reading ${source.lowercase()} content…", warnings = input.warnings)
        launchScan("The scan timed out. Use Scan again.", "Could not read this content.") {
            val urls = linkedSetOf<String>()
            val images = linkedSetOf<String>()
            suspend fun text(value: String, html: Boolean, origin: String) {
                if (value.length > SharedChessInput.MAX_TEXT) warn(if (localFile) "File text was limited to 2 MB." else "$source text was limited to 2 MB.")
                val parsed = withContext(Dispatchers.Default) { SharedChessText.parse(value, html, origin) }
                add(parsed.candidates)
                urls += parsed.urls
                images += parsed.images
            }
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
            // Text (including captions) and attachments are kept even when a linked page fails.
            scanFound(urls, images, source, "$source HTML")
            finish()
            if (state.value.results.isEmpty()) state.value = state.value.copy(status = when {
                state.value.links.isNotEmpty() -> "No chess positions or games found yet. Scan one of the links below to look further."
                localFile -> "No chess positions or games found in this file. Choose a document containing FEN or PGN, or a clear 2D board image."
                source == "Clipboard" -> "No chess positions or games found in this entry. Choose another entry containing FEN, PGN, a chess URL or a clear 2D board image."
                else -> "No chess positions or games found. Share FEN or PGN text, a chess page URL, a PGN file, or a clear 2D board image."
            })
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
        state.value = state.value.copy(busy = true, error = null, warnings = emptyList())
        launchScan("The image scan timed out.", "Could not scan this page.") {
            scanPage()
            finish()
        }
    }

    private class PageContent(val texts: List<String>, val links: List<String>, val images: List<Pair<String, String>>, val limited: Boolean)

    /** Parses the page script's result. The page can replace the script's built-ins, so its limits are applied again. */
    private fun parsePage(raw: String): PageContent {
        require(raw.length <= 40_000_000) { "This page is too large to scan. Open a specific image or PGN link instead." }
        val document = JSONObject(JSONTokener(raw).nextValue() as? String ?: error("Could not read the page."))
        var limited = document.optBoolean("limited")
        val texts = mutableListOf<String>()
        var remaining = SharedChessInput.MAX_TEXT
        document.optJSONArray("texts")?.let { array ->
            for (i in 0 until array.length()) {
                if (remaining <= 0) { limited = true; break }
                val text = array.optString(i).take(remaining)
                texts += text
                remaining -= text.length
            }
        }
        val links = document.optJSONArray("links")?.let { array ->
            (0 until array.length()).map(array::optString).filter { it.startsWith("https://") }.distinct().take(300)
        }.orEmpty()
        val images = document.optJSONArray("images")?.let { array ->
            (0 until array.length()).mapNotNull(array::optJSONObject)
                .map { it.optString("url") to it.optString("label", "Board image") }
                .filter { it.first.isNotEmpty() }.distinctBy { it.first }
        }.orEmpty()
        if (images.size > 20) limited = true
        return PageContent(texts, links, images.take(20), limited)
    }

    private suspend fun scanPage() {
        state.value = state.value.copy(status = "Finding positions, games and images…")
        // A page that blocks its script thread would otherwise keep the scan busy until Stop.
        val raw = withTimeoutOrNull(20_000) { pageView.evaluate(script) } ?: run {
            val stuck = page
            if (Build.VERSION.SDK_INT >= 29 && stuck != null) {
                // Board recognition shares the stuck renderer process; ending it lets both views start again.
                page = null
                state.value = state.value.copy(hasPage = false)
                val exited = CompletableDeferred<Unit>().also { terminatedRenderer = it }
                stuck.webViewRenderProcess?.terminate()
                // terminate() only starts the kill; a view created before the process has exited
                // (such as the next board recognition) joins it and dies with it.
                withTimeoutOrNull(5_000) { exited.await() }
                terminatedRenderer = null
            }
            throw IOException("The page did not respond. Scan it again, or open a direct PGN or image URL.")
        }
        val document = withContext(Dispatchers.Default) { parsePage(raw) }
        val source = state.value.pageUrl
        val extracted = withContext(Dispatchers.Default) {
            val candidates = mutableListOf<WebChessCandidate>()
            for (text in document.texts) {
                ensureActive()
                candidates += ChessWebExtractor.extract(text, source)
                if (candidates.size >= 500) break
            }
            candidates
        }
        add(extracted)
        val pgnLinks = document.links.mapNotNull(ChessWebExtractor::pgnLink).distinct()
        if (pgnLinks.size > 12) warn("Scanned the first 12 PGN links. Open a specific link to scan more.")
        for ((index, url) in pgnLinks.take(12).withIndex()) {
            state.value = state.value.copy(status = "Reading game link ${index + 1} of ${minOf(pgnLinks.size, 12)}…")
            try {
                val response = download(url, 4_000_000)
                add(withContext(Dispatchers.Default) { ChessWebExtractor.extract(response.text(), url) })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { warn("Could not read a linked game: $url") }
        }
        for ((index, image) in document.images.withIndex()) {
            currentCoroutineContext().ensureActive()
            val (url, label) = image
            state.value = state.value.copy(status = "Scanning image ${index + 1} of ${document.images.size}…")
            try {
                if (url.startsWith("data:image/")) {
                    if (url.length <= 5_500_000) recognize(url, label, source)
                } else if (url.startsWith("https://")) {
                    val response = download(url, 4_000_000)
                    scanImage(response.data, response.type, url)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { warn("Some page images could not be read. Scroll to the board and use Scan page again.") }
        }
        // Covers CSS pieces, cross-origin canvases, and diagrams without an image URL.
        val view = pageView
        if (view.width > 0 && view.height > 0) {
            state.value = state.value.copy(status = "Scanning the visible page…")
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            try {
                val data = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                }
                scanImage(data, "image/png", source)
            } finally { bitmap.recycle() }
        }
        if (document.limited) warn("This page is large: scanned up to 2 MB of text and 20 images. Open a specific image or scroll and scan again for more.")
    }

    private suspend fun scanImage(bytes: ByteArray, type: String, source: String) {
        val dataUrl = withContext(Dispatchers.Default) {
            if (type.substringBefore(';') == "image/svg+xml") {
                "data:image/svg+xml;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format could not be read." }
                // Decode near 1600 px, then scale down: power-of-two sampling alone can
                // halve a 12 MP photo to 1000 px and lose detail in small boards.
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: error("This image could not be read.")
                val scale = minOf(1f, 1600f / maxOf(decoded.width, decoded.height))
                val rotation = exifRotation(bytes)
                val bitmap = if (scale == 1f && rotation == 0) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }, true)
                if (bitmap !== decoded) decoded.recycle()
                try {
                    ByteArrayOutputStream().use { output ->
                        // JPEG sources such as photos stay JPEG: PNG would make the recognizer input several times larger.
                        if (bounds.outMimeType == "image/jpeg") {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                        } else {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                        }
                    }
                } finally { bitmap.recycle() }
            }
        }
        recognize(dataUrl, "Board image", source)
    }

    /** Cameras often store photos sideways with an EXIF tag; the tile classifier expects upright pieces. */
    private fun exifRotation(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }

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
        page?.stopLoading()
        recognizer.close()
        state.value = state.value.copy(busy = false, status = if (state.value.busy)
            "Scan stopped. Results found so far are available." else state.value.status)
    }

    fun close() {
        if (destroyed) return
        destroyed = true
        cancel()
        page?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        page = null
        attach(context)
    }

    private data class Download(val data: ByteArray, val type: String, val url: String, val charset: Charset?, val page: Boolean = false) {
        /** Declared charset, else UTF-8 with the windows-1252 fallback older PGN files need. */
        fun text(): String = charset?.let { data.toString(it) } ?: ChessDocumentReader.decodeBytes(data)
    }

    /** With [pageOnly], an HTML response stops after its headers: the WebView loads such pages itself. */
    private suspend fun download(url: String, limit: Long, pageOnly: Boolean = false): Download = withContext(Dispatchers.IO) {
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
                            val type = body.contentType()?.toString().orEmpty()
                            if (pageOnly && type.contains("html", ignoreCase = true)) {
                                Download(ByteArray(0), type, it.request.url.toString(), null, page = true)
                            } else {
                                require(body.contentLength() <= limit) { "This file is too large (limit ${limit / 1_000_000} MB)." }
                                val source = body.source()
                                require(!source.request(limit + 1)) { "This file is too large (limit ${limit / 1_000_000} MB)." }
                                Download(source.readByteArray(), type, it.request.url.toString(), body.contentType()?.charset())
                            }
                        }
                        if (continuation.isActive) continuation.resume(downloaded)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }

    companion object {
        // One client for every scan. Stalls fail after 20 s without data; a whole download may take three
        // minutes, enough for the 16 MB limit on a slow mobile connection.
        private val sharedClient by lazy {
            OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.MINUTES).followSslRedirects(false).build()
        }

        /** Out-of-memory and stack-overflow errors from decoders are reported like other unreadable content. */
        private fun describe(e: Throwable, fallback: String): String = when (e) {
            is OutOfMemoryError -> "This content is too large to read on this device."
            is StackOverflowError -> "This content is too complex to read."
            is Exception -> e.message ?: fallback
            else -> throw e
        }
    }
}
