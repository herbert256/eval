package com.eval.data

import com.eval.chess.ChessBoard
import com.eval.chess.PgnParser
import java.net.URI
import java.net.URLDecoder

internal enum class WebChessKind { FEN, PGN, IMAGE }

internal data class WebChessCandidate(
    val kind: WebChessKind,
    val content: String,
    val title: String,
    val source: String,
    val needsReview: Boolean = false,
    val warning: String? = null
)

/** Pure extraction shared by rendered pages, text downloads, and URL fields. */
internal object ChessWebExtractor {
    const val MAX_RESULTS = 100
    private val placement = "[prnbqkPRNBQK1-8]{1,8}(?:/[prnbqkPRNBQK1-8]{1,8}){7}"
    private val fen = Regex("(?<![prnbqkPRNBQK1-8])($placement)(?:[\\s_+]+([wb])[\\s_+]+([KQkq]+|-)[\\s_+]+([a-h][36]|-)(?:[\\s_+]+(\\d+)[\\s_+]+(\\d+))?)?(?![prnbqkPRNBQK1-8/])")
    private val header = Regex("""\[[A-Za-z][A-Za-z0-9_]*\s+"(?:\\.|[^"\\])*"\s*\]""")
    private val moveStart = Regex("""(?<!\d)\d+\.(?:\.\.)?\s*(?:[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8]|O-O|0-0)""")
    // Bounded: at most nine characters, so matching it at every position stays linear.
    private val sanMove = java.util.regex.Pattern.compile("[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?")
    private val chessHosts = setOf("lichess.org", "chess.com", "chess24.com", "chessgames.com", "chessbase.com",
        "chesstempo.com", "365chess.com", "ficsgames.org", "theweekinchess.com")
    private val gameResults = listOf("1/2-1/2", "1-0", "0-1", "*")

    fun normalizeUrl(input: String): String {
        val text = input.trim()
        require(text.isNotEmpty()) { "Enter a web address." }
        val uri = try { URI(if ("://" in text) text else "https://$text") }
        catch (_: Exception) { throw IllegalArgumentException("Enter a valid HTTPS web address.") }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Use an HTTPS web address without a username or password."
        }
        return uri.toASCIIString()
    }

    fun decode(text: String): String {
        var value = text.replace("\\/", "/").replace("\\n", "\n")
            .replace("\\r", "\n").replace("\\\"", "\"")
            .replace("\\u002F", "/", true).replace("\\u0020", " ", true)
        repeat(2) {
            if ('%' in value) value = try {
                // A literal + is valid in SAN and must survive percent decoding.
                URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) { value }
        }
        return value.replace('\u00a0', ' ')
    }

    fun extract(text: String, source: String): List<WebChessCandidate> {
        val decoded = decode(text.take(2_000_000))
        val results = linkedMapOf<String, WebChessCandidate>()
        for (match in fen.findAll(decoded)) {
            val parts = match.groupValues
            val hasTurn = parts[2].isNotEmpty()
            // Without a side to move, prefer White; use Black when Black is in check, which makes White to move illegal.
            val values = if (hasTurn) listOf("${parts[1]} ${parts[2]} ${parts[3]} ${parts[4]} ${parts[5].ifEmpty { "0" }} ${parts[6].ifEmpty { "1" }}")
                else listOf("${parts[1]} w - - 0 1", "${parts[1]} b - - 0 1")
            val board = values.firstNotNullOfOrNull { value -> ChessBoard().takeIf { it.setFen(value) } }
            if (board != null) {
                val candidate = WebChessCandidate(WebChessKind.FEN, board.getFen(), "FEN position", source,
                    needsReview = !hasTurn,
                    warning = if (!hasTurn) "Only piece placement was supplied. Check turn and position details." else null)
                results["fen:${candidate.content}"] = candidate
            }
            if (results.size >= MAX_RESULTS) break
        }

        fun addPgn(pgn: String) {
            if (results.size >= MAX_RESULTS) return
            val board = PgnParser.parseInitialBoard(pgn) ?: return
            val moves = PgnParser.parseMoves(pgn)
            if (moves.isEmpty() || moves.size > 1500 || moves.any { !board.makeMove(it) }) return
            val headers = PgnParser.parseHeaders(pgn)
            val title = if (headers["White"] != null || headers["Black"] != null)
                "${headers["White"] ?: "White"} – ${headers["Black"] ?: "Black"}"
                else headers["Event"] ?: "PGN game (${moves.size} moves)"
            val key = "pgn:${headers["FEN"].orEmpty()}:${moves.joinToString(" ")}:${headers["White"]}:${headers["Black"]}"
            results.putIfAbsent(key, WebChessCandidate(WebChessKind.PGN, pgn.trim(), title, source))
        }

        val firstHeader = header.find(decoded)
        if (firstHeader != null) {
            // Callers supply individual pre/textarea/script blocks as well as page text.
            for (game in PgnParser.splitGames(decoded.substring(firstHeader.range.first)).take(MAX_RESULTS)) {
                addPgn(game)
            }
        } else if (moveStart.containsMatchIn(decoded)) {
            val trimmed = decoded.trim()
            if (moveStart.find(trimmed)?.range?.first == 0) addPgn(trimmed)
            else moveRuns(decoded).take(MAX_RESULTS).forEach { run ->
                if (PgnParser.parseMoves(run).size >= 2) addPgn(run)
            }
        }
        return results.values.toList()
    }

    fun pgnLink(url: String): String? {
        val uri = try { URI(url) } catch (_: Exception) { return null }
        if (uri.scheme != "https") return null
        if (uri.path.orEmpty().endsWith(".pgn", true)) return url
        if (uri.host !in setOf("lichess.org", "www.lichess.org")) return null
        val path = uri.path.orEmpty()
        // Several site routes are eight letters long, just like a game ID.
        if (path.trim('/') in setOf("analysis", "training", "practice", "features", "insights",
                "checkout", "password", "variants", "timeline", "streamer")) return null
        Regex("^/([A-Za-z0-9]{8})(?:[A-Za-z0-9]{4})?(?:/(?:white|black))?/?$").matchEntire(path)?.let {
            return "https://lichess.org/game/export/${it.groupValues[1]}"
        }
        Regex("^/study/([A-Za-z0-9]{8})(?:/([A-Za-z0-9]{8}))?/?$").matchEntire(path)?.let {
            return "https://lichess.org/study/${it.groupValues[1]}${it.groupValues[2].let { chapter -> if (chapter.isEmpty()) "" else "/$chapter" }}.pgn"
        }
        return url.takeIf { path.startsWith("/game/export/") }
    }

    /** Links fetched without a tap: HTTPS on a known chess site, or a .pgn download. Others are only listed. */
    fun autoFollow(url: String): Boolean {
        val uri = try { URI(url.trim()) } catch (_: Exception) { return false }
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return chessHosts.any { host == it || host.endsWith(".$it") } || uri.path.orEmpty().endsWith(".pgn", true)
    }

    /**
     * Runs of chess tokens in prose, as the former regex
     * `\d+\.(?:\.\.)?\s*(?:(?:\d+\.(?:\.\.)?|SAN[!?]*|O-O(?:-O)?[+#]?|0-0(?:-0)?[+#]?|1/2-1/2|1-0|0-1|\*|\$\d+|\{[^}]*\}|\([^)]*\))\s*)+`
     * found them, but in linear time: the regex rescanned unclosed comments and digit runs from every start.
     * Restricting prose extraction to chess tokens never imports a valid prefix of a broken PGN block.
     */
    fun moveRuns(text: String): Sequence<String> = sequence {
        val n = text.length
        val san = sanMove.matcher(text)
        // Next '}' and ')' at or after the last lookup (n when none remain); lookups only move forward.
        var brace = -1
        var paren = -1
        fun closing(c: Char, from: Int): Int {
            if (c == '}') { if (brace < from) brace = text.indexOf(c, from).let { if (it < 0) n else it }; return brace }
            if (paren < from) paren = text.indexOf(c, from).let { if (it < 0) n else it }
            return paren
        }
        fun digits(from: Int): Int { var i = from; while (i < n && text[i] in '0'..'9') i++; return i }
        fun spaces(from: Int): Int { var i = from; while (i < n && text[i] in " \t\n\u000B\u000C\r") i++; return i }
        fun moveNumber(from: Int): Int {
            val end = digits(from)
            if (end == from || end >= n || text[end] != '.') return -1
            return if (text.startsWith("..", end + 1)) end + 3 else end + 1
        }
        fun castle(from: Int, c: Char): Int {
            if (!text.startsWith("$c-$c", from)) return -1
            var end = from + 3
            if (text.startsWith("-$c", end)) end += 2
            return if (end < n && (text[end] == '+' || text[end] == '#')) end + 1 else end
        }
        // Alternatives in the regex's order; the first that matches wins.
        fun token(p: Int): Int {
            if (p >= n) return -1
            moveNumber(p).let { if (it >= 0) return it }
            san.region(p, n)
            if (san.lookingAt()) {
                var end = san.end()
                while (end < n && (text[end] == '!' || text[end] == '?')) end++
                return end
            }
            castle(p, 'O').let { if (it >= 0) return it }
            castle(p, '0').let { if (it >= 0) return it }
            for (result in gameResults) if (text.startsWith(result, p)) return p + result.length
            return when (text[p]) {
                '$' -> digits(p + 1).takeIf { it > p + 1 } ?: -1
                '{' -> closing('}', p + 1).let { if (it < n) it + 1 else -1 }
                '(' -> closing(')', p + 1).let { if (it < n) it + 1 else -1 }
                else -> -1
            }
        }
        var start = 0
        while (start < n) {
            if (text[start] !in '0'..'9') { start++; continue }
            var end = -1
            val first = moveNumber(start)
            if (first >= 0) {
                var next = token(spaces(first))
                while (next >= 0) { end = spaces(next); next = token(end) }
            }
            if (end >= 0) { yield(text.substring(start, end)); start = end }
            // Every start inside this digit run fails the same way.
            else start = digits(start)
        }
    }
}
