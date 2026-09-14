package com.eval.chess

data class ParsedMove(
    val san: String,
    val clockTime: String? = null
)

class PgnParser {
    companion object {
        private val CLOCK_PATTERN = Regex("""\[%clk\s+(\d+:\d+(?::\d+)?)\]""")
        private val HEADER_PATTERN = Regex("""\[([A-Za-z][A-Za-z0-9_]*)\s+"((?:\\.|[^"\\])*)"\s*\]""")
        private val MOVE_NUMBER_PATTERN = Regex("""^\d+\.+""")
        private val RESULTS = setOf("*", "1-0", "0-1", "1/2-1/2")

        private enum class TokenKind { HEADER, COMMENT, MOVE }
        private data class Token(val kind: TokenKind, val text: String, val start: Int)

        /** Scan once using the same comment/variation rules for moves and game boundaries. */
        private fun tokens(pgn: String): Sequence<Token> = sequence {
            var index = 0
            var variationDepth = 0
            while (index < pgn.length) {
                val start = index
                when (pgn[index]) {
                    '\uFEFF' -> index++
                    '{' -> {
                        val end = pgn.indexOf('}', index + 1).let { if (it < 0) pgn.length else it }
                        if (variationDepth == 0) yield(Token(TokenKind.COMMENT, pgn.substring(index + 1, end), start))
                        index = end + 1
                    }
                    ';', '%' -> {
                        while (index < pgn.length && pgn[index] !in "\r\n") index++
                    }
                    '[' -> {
                        val header = HEADER_PATTERN.matchAt(pgn, index)
                        index = header?.range?.last?.plus(1)
                            ?: pgn.indexOf(']', index).let { if (it < 0) pgn.length else it + 1 }
                        if (variationDepth == 0 && header != null) yield(Token(TokenKind.HEADER, header.value, start))
                    }
                    '(' -> { variationDepth++; index++ }
                    ')' -> { if (variationDepth > 0) variationDepth--; index++ }
                    '$' -> {
                        index++
                        while (index < pgn.length && pgn[index].isDigit()) index++
                    }
                    else -> {
                        if (pgn[index].isWhitespace()) {
                            index++
                            continue
                        }
                        index++
                        while (index < pgn.length && !pgn[index].isWhitespace() && pgn[index] !in "{}();[%$") index++
                        if (variationDepth == 0) yield(Token(TokenKind.MOVE, pgn.substring(start, index), start))
                    }
                }
            }
        }

        private fun moveText(token: Token): String = token.text.replace(MOVE_NUMBER_PATTERN, "").trimEnd('!', '?')

        /** Read main-line tokens without interpreting comment or variation text as moves. */
        fun parseMovesWithClock(pgn: String): List<ParsedMove> {
            val result = mutableListOf<ParsedMove>()
            for (token in tokens(pgn)) {
                when (token.kind) {
                    TokenKind.COMMENT -> {
                        if (result.isNotEmpty()) {
                            CLOCK_PATTERN.find(token.text)?.let {
                                result[result.lastIndex] = result.last().copy(clockTime = it.groupValues[1])
                            }
                        }
                    }
                    TokenKind.HEADER -> if (result.isNotEmpty()) break
                    TokenKind.MOVE -> {
                        val move = moveText(token)
                        if (move in RESULTS) break
                        if (move.isNotEmpty() && move != "..." && move != "e.p.") {
                            // Keep invalid tokens so the board loader reports the first bad move
                            // rather than silently applying the rest to the wrong position.
                            result.add(ParsedMove(move))
                        }
                    }
                }
            }
            return result
        }

        fun parseMoves(pgn: String): List<String> = parseMovesWithClock(pgn).map { it.san }

        fun parseResult(pgn: String): String? = tokens(pgn)
            .filter { it.kind == TokenKind.MOVE }.map(::moveText).firstOrNull { it in RESULTS }

        /** Split collections independently of blank-line spacing or which header comes first. */
        fun splitGames(pgn: String): List<String> {
            val games = mutableListOf<String>()
            var start = 0
            var hasContent = false
            var hasMoves = false
            var finished = false
            for (token in tokens(pgn)) {
                if (token.kind == TokenKind.COMMENT) continue
                val beginsNextGame = hasContent &&
                    ((token.kind == TokenKind.HEADER && hasMoves) || finished)
                if (beginsNextGame) {
                    games.add(pgn.substring(start, token.start).trim())
                    start = token.start
                    hasMoves = false
                    finished = false
                }
                hasContent = true
                if (token.kind == TokenKind.MOVE) {
                    hasMoves = true
                    finished = moveText(token) in RESULTS
                }
            }
            if (hasContent) games.add(pgn.substring(start).trim())
            return games
        }

        fun parseInitialBoard(pgn: String): ChessBoard? {
            val headers = parseHeaders(pgn)
            val board = ChessBoard()
            val fen = headers["FEN"]
            if (fen != null) return board.takeIf { it.setFen(fen) }
            return board.takeUnless { headers["SetUp"] == "1" }
        }

        fun parseHeaders(pgn: String): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            for (token in tokens(pgn)) {
                if (token.kind == TokenKind.COMMENT) continue
                if (token.kind != TokenKind.HEADER) break
                val match = HEADER_PATTERN.matchEntire(token.text) ?: break
                headers[match.groupValues[1]] = match.groupValues[2].replace(Regex("""\\([\\"])"""), "$1")
            }
            return headers
        }
    }
}
