package com.eval.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.util.UUID

/** The URI grants belong to the receiving activity. Read them only through ContentResolver. */
internal data class SharedChessInput(
    val id: String = UUID.randomUUID().toString(),
    val texts: List<String> = emptyList(),
    val html: List<String> = emptyList(),
    val streams: List<Uri> = emptyList(),
    val mimeType: String? = null,
    val warnings: List<String> = emptyList()
) {
    companion object {
        const val MAX_ITEMS = 20
        const val MAX_TEXT = 2_000_000

        fun fromIntent(intent: Intent): SharedChessInput? {
            if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return null
            val texts = linkedSetOf<String>()
            val html = linkedSetOf<String>()
            val streams = linkedSetOf<Uri>()
            val warnings = mutableListOf<String>()
            var remaining = MAX_TEXT
            fun addText(value: CharSequence?, target: MutableSet<String>) {
                if (value.isNullOrBlank() || value.toString() in target) return
                if (value.length > remaining) warnings += "Shared text was limited to 2 MB."
                val part = value.take(remaining).toString()
                remaining -= part.length
                if (part.isNotBlank()) target += part
            }
            fun addUri(uri: Uri?) {
                if (uri == null) return
                when (uri.scheme?.lowercase()) {
                    "content" -> if (streams.size < MAX_ITEMS) streams += uri
                        else warnings += "Scanned the first 20 shared files. Share fewer files to scan the rest."
                    "https", "http" -> addText(uri.toString(), texts)
                    else -> warnings += "A shared file could not be opened. Share it from an app that grants access to its content."
                }
            }
            try {
                addText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT), texts)
                addText(intent.getStringExtra(Intent.EXTRA_HTML_TEXT), html)
                if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.forEach(::addUri)
                } else {
                    addUri(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                }
                intent.clipData?.let { clip ->
                    if (clip.itemCount > MAX_ITEMS) warnings += "Scanned the first 20 shared items."
                    for (i in 0 until minOf(clip.itemCount, MAX_ITEMS)) {
                        val item = clip.getItemAt(i)
                        addText(item.text, texts)
                        addText(item.htmlText, html)
                        addUri(item.uri)
                    }
                }
            } catch (_: RuntimeException) {
                warnings += "Some shared content could not be read. Try sharing it again."
            }
            return SharedChessInput(texts = texts.toList(), html = html.toList(), streams = streams.toList(),
                mimeType = intent.type, warnings = warnings.distinct())
        }
    }
}
