package com.eval.ui

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

internal suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(value ?: "null") }
}

/** Images carry only piece placement: default the rest for review in Board setup. */
internal fun JSONObject.reviewFen(): String = getString("placement") + " w - - 0 1"

internal fun JSONObject.reviewWarning(): String =
    if (optBoolean("reliable")) "Check the pieces, board orientation and whose turn it is. Images do not contain move history."
    else "Some pieces were uncertain. Correct the position before starting."

/** A local-only WebView: remote pages never share this origin or its execution context. */
internal class BoardImageRecognizer(private val context: Context) {
    private val assets = context.applicationContext.assets
    private var view: WebView? = null

    suspend fun recognize(dataUrl: String): JSONObject? = withContext(Dispatchers.Main) {
        withTimeout(45_000) {
            val web = view ?: WebView(context).also { created ->
                view = created
                created.settings.javaScriptEnabled = true
                created.settings.allowFileAccess = false
                created.settings.allowContentAccess = false
                created.settings.blockNetworkLoads = true
                created.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest): WebResourceResponse {
                        val uri = request.url
                        val name = uri.path.orEmpty().removePrefix("/")
                        if (uri.scheme == "https" && uri.host == "eval-board.invalid" &&
                            name.matches(Regex("[a-zA-Z0-9.-]+"))) {
                            try {
                                val mime = when {
                                    name.endsWith("html") -> "text/html"
                                    name.endsWith("js") -> "text/javascript"
                                    name.endsWith("wasm") -> "application/wasm"
                                    else -> "application/octet-stream"
                                }
                                return WebResourceResponse(mime, "UTF-8", assets.open("board-scanner/$name"))
                            } catch (_: Exception) { /* Return a closed, empty response below. */ }
                        }
                        return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
                }
                created.loadUrl("https://eval-board.invalid/index.html")
            }
            while (web.evaluate("window.scannerReady === true") != "true") delay(100)
            web.evaluate("window.scanBoard(${JSONObject.quote(dataUrl)}); null")
            var value: String
            do {
                delay(100)
                value = web.evaluate("window.scanResult || null")
            } while (value == "null")
            val result = JSONObject(value)
            if (result.has("error")) throw IllegalStateException("Board image recognition failed. Try another image or scan again.")
            result.takeUnless { it.optBoolean("empty") }
        }
    }

    fun close() {
        view?.stopLoading()
        view?.destroy()
        view = null
    }
}
