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
    // Restrict prose extraction to chess tokens; never import a valid prefix of a broken PGN block.
    private val moveRun = Regex("""\d+\.(?:\.\.)?\s*(?:(?:\d+\.(?:\.\.)?|[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?[!?]*|O-O(?:-O)?[+#]?|0-0(?:-0)?[+#]?|1/2-1/2|1-0|0-1|\*|\$\d+|\{[^}]*\}|\([^)]*\))\s*)+""")

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
            val value = if (hasTurn) "${parts[1]} ${parts[2]} ${parts[3]} ${parts[4]} ${parts[5].ifEmpty { "0" }} ${parts[6].ifEmpty { "1" }}"
                else "${parts[1]} w - - 0 1"
            val board = ChessBoard()
            if (board.setFen(value)) {
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
            else moveRun.findAll(decoded).take(MAX_RESULTS).forEach { match ->
                if (PgnParser.parseMoves(match.value).size >= 2) addPgn(match.value)
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

    fun flipPlacement(fen: String): String {
        val fields = fen.split(' ').toMutableList()
        fields[0] = fields[0].reversed()
        return fields.joinToString(" ")
    }
}
