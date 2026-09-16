package com.eval.ui

import com.eval.chess.PgnParser
import java.net.URI
import java.util.Locale

/** The supported server of an absolute web URL, rather than a name anywhere in its text. */
internal fun gameSiteHost(url: String): String? {
    val uri = try { URI(url) } catch (_: Exception) { return null }
    if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) return null
    if (uri.rawUserInfo != null) return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    return listOf("lichess.org").firstOrNull { host == it || host.endsWith(".$it") }
}

internal fun gameSiteUrl(pgn: String): String? {
    val site = PgnParser.parseHeaders(pgn)["Site"]?.trim() ?: return null
    return site.takeIf { gameSiteHost(it) != null }
}
