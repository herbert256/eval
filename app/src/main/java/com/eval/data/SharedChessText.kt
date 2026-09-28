package com.eval.data

import android.text.Html

internal data class SharedChessText(
    val candidates: List<WebChessCandidate>,
    val urls: List<String>,
    val images: List<String>
) {
    companion object {
        private val webUrl = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
        private val blockTags = listOf("pre", "code", "textarea")
        private val blockEnds = blockTags.associateWith { Regex("</$it\\s*>", RegexOption.IGNORE_CASE) }

        fun parse(text: String, isHtml: Boolean, source: String): SharedChessText {
            val raw = text.take(SharedChessInput.MAX_TEXT)
            val images = linkedSetOf<String>()
            val snippets = mutableListOf(raw)
            if (isHtml) {
                fun plain(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY,
                    Html.ImageGetter { url -> images += url; null }, null).toString()
                // Parse inert markup: shared HTML must not execute scripts or navigate the app.
                snippets += codeBlocks(raw).map(::plain)
                snippets += plain(raw)
            }
            // XHTML/EPUB namespace and DTD identifiers are metadata, not links to retrieve.
            val linkText = if (isHtml) withoutDoctypes(raw.replace(Regex("\\sxmlns(?::[\\w.-]+)?\\s*=\\s*([\"']).*?\\1", RegexOption.DOT_MATCHES_ALL), "")) else raw
            val urls = webUrl.findAll(if (isHtml) Html.fromHtml(linkText.replace("<", "&lt;")
                .replace(">", "&gt;"), Html.FROM_HTML_MODE_LEGACY).toString() else raw)
                .map { it.value.trimEnd('.', ',', ';', ')', ']') }.distinct().take(13).toList()
            return SharedChessText(snippets.flatMap { ChessWebExtractor.extract(it, source) }
                .distinctBy { it.kind to it.content }, urls, images.take(21))
        }

        /** Whether text holds a chess import: a valid FEN or PGN, a chess-site/.pgn link, or an embedded image. */
        fun hasChess(text: String, isHtml: Boolean): Boolean = parse(text, isHtml, "").let { parsed ->
            parsed.candidates.isNotEmpty() || parsed.urls.any(ChessWebExtractor::autoFollow) ||
                parsed.images.any { it.startsWith("data:image/") || ChessWebExtractor.autoFollow(it) }
        }

        /**
         * Contents of pre/code/textarea elements, as `<(pre|code|textarea)\b[^>]*>(.*?)</\1\s*>` found them
         * (case-insensitively), but in linear time: that regex rescanned the rest of the text for every unclosed tag.
         */
        fun codeBlocks(html: String, limit: Int = 100): List<String> {
            val blocks = mutableListOf<String>()
            // First closing tag per name at or after the last lookup; null once none remain.
            val ends = HashMap<String, MatchResult?>()
            var tag = html.indexOf('<')
            while (tag >= 0 && blocks.size < limit) {
                val name = blockTags.firstOrNull { html.regionMatches(tag + 1, it, 0, it.length, ignoreCase = true) }
                val after = tag + 1 + (name?.length ?: 0)
                if (name != null && (after >= html.length || !(html[after].isLetterOrDigit() || html[after] == '_'))) {
                    val open = html.indexOf('>', after)
                    // Without a later '>', no other tag can match either.
                    if (open < 0) break
                    var end = ends[name]
                    if ((name !in ends || (end != null && end.range.first <= open))) {
                        end = blockEnds.getValue(name).find(html, open + 1)
                        ends[name] = end
                    }
                    if (end != null) {
                        blocks += html.substring(open + 1, end.range.first)
                        tag = html.indexOf('<', end.range.last + 1)
                        continue
                    }
                }
                tag = html.indexOf('<', tag + 1)
            }
            return blocks
        }

        /** Removes `<!DOCTYPE …>` declarations in one pass, like the regex `<!DOCTYPE[^>]*>` without its rescans. */
        private fun withoutDoctypes(html: String): String {
            val output = StringBuilder(html.length)
            var from = 0
            while (true) {
                val start = html.indexOf("<!DOCTYPE", from, ignoreCase = true)
                val end = if (start < 0) -1 else html.indexOf('>', start)
                if (end < 0) break
                output.append(html, from, start)
                from = end + 1
            }
            return output.append(html, from, html.length).toString()
        }
    }
}
