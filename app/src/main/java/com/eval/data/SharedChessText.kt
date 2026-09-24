package com.eval.data

import android.text.Html

internal data class SharedChessText(
    val candidates: List<WebChessCandidate>,
    val urls: List<String>,
    val images: List<String>
) {
    companion object {
        private val webUrl = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
        private val blocks = Regex("<(pre|code|textarea)\\b[^>]*>(.*?)</\\1\\s*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        fun parse(text: String, isHtml: Boolean, source: String): SharedChessText {
            val raw = text.take(SharedChessInput.MAX_TEXT)
            val images = linkedSetOf<String>()
            val snippets = mutableListOf(raw)
            if (isHtml) {
                fun plain(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY,
                    Html.ImageGetter { url -> images += url; null }, null).toString()
                // Parse inert markup: shared HTML must not execute scripts or navigate the app.
                snippets += blocks.findAll(raw).take(100).map { plain(it.groupValues[2]) }
                snippets += plain(raw)
            }
            // XHTML/EPUB namespace and DTD identifiers are metadata, not links to retrieve.
            val linkText = if (isHtml) raw.replace(Regex("\\sxmlns(?::[\\w.-]+)?\\s*=\\s*([\"']).*?\\1", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "") else raw
            val urls = webUrl.findAll(if (isHtml) Html.fromHtml(linkText.replace("<", "&lt;")
                .replace(">", "&gt;"), Html.FROM_HTML_MODE_LEGACY).toString() else raw)
                .map { it.value.trimEnd('.', ',', ';', ')', ']') }.distinct().take(13).toList()
            return SharedChessText(snippets.flatMap { ChessWebExtractor.extract(it, source) }
                .distinctBy { it.kind to it.content }, urls, images.take(21))
        }
    }
}
