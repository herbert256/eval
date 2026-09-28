package com.eval.chess

data class ParsedMove(
    val san: String,
    val clockTime: String? = null
)

class PgnParser {
    companion object {
        private val CLOCK_PATTERN = Regex("""\[%clk\s+(\d+:\d+(?::\d+)?(?:\.\d+)?)\]""")
        private val HEADER_PATTERN = Regex("""\[([A-Za-z][A-Za-z0-9_]*)\s*"((?:\\.|[^"\\])*)"\s*\]""")
        private val MOVE_NUMBER_PATTERN = Regex("""^\d+[.…]+""")
        // "e8(Q)" is a promotion spelling, not a one-letter variation.
        private val PROMOTION_IN_PARENS = Regex("""\([QRBNqrbn]\)""")
        private val MOVE_LIKE = Regex("""^(?:[KQRBN]?[a-h]?[1-8]?[x:-]?[a-h][1-8]|[O0]-[O0])""")
        // Annotation glyphs written as their own tokens (±, +-, =, !?, …); "--" is a null move, not a glyph.
        private const val GLYPH_CHARS = "!?+-−=/±∓⩲⩱∞□‼⁇⁉⁈→↑⇆∆Δ⊕⊥⨀○⟳.…"
        private val NAMED_ANNOTATIONS = setOf("N", "TN", "e.p.", "ep")
        private val STANDARD_VARIANTS = setOf("", "standard", "fromposition", "chess", "normal")

        private enum class TokenKind { HEADER, COMMENT, MOVE, UNCLOSED_VARIATION }
        private data class Token(val kind: TokenKind, val text: String, val start: Int)

        private fun startsLine(pgn: String, index: Int): Boolean {
            var i = index - 1
            while (i >= 0 && (pgn[i] == ' ' || pgn[i] == '\t' || pgn[i] == '\uFEFF')) i--
            return i < 0 || pgn[i] == '\n' || pgn[i] == '\r'
        }

        private fun closesVariation(pgn: String, index: Int): Boolean {
            var i = index
            while (i < pgn.length && pgn[i].isWhitespace()) i++
            return i < pgn.length && pgn[i] == ')'
        }

        /** Scan once using the same comment/variation rules for moves and game boundaries. */
        private fun tokens(pgn: String): Sequence<Token> = sequence {
            var index = 0
            var variationDepth = 0
            var variationStart = 0
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
                        // A tag line inside a variation means its ")" is missing and a new game begins.
                        if (variationDepth > 0 && header != null && startsLine(pgn, index)) {
                            yield(Token(TokenKind.UNCLOSED_VARIATION, pgn.substring(variationStart, index), variationStart))
                            variationDepth = 0
                        }
                        index = header?.range?.last?.plus(1)
                            ?: pgn.indexOf(']', index).let { if (it < 0) pgn.length else it + 1 }
                        if (variationDepth == 0 && header != null) yield(Token(TokenKind.HEADER, header.value, start))
                    }
                    '(' -> {
                        if (variationDepth == 0) variationStart = index
                        variationDepth++
                        index++
                    }
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
                        while (true) {
                            while (index < pgn.length && !pgn[index].isWhitespace() && pgn[index] !in "{}();[%$") index++
                            if (pgn[index - 1] in "18" && PROMOTION_IN_PARENS.matchAt(pgn, index) != null) index += 3 else break
                        }
                        val text = pgn.substring(start, index)
                        // Variations cannot end a game, so a result that doesn't close one ends the game.
                        if (variationDepth > 0 && resultToken(stripMoveNumber(text)) != null && !closesVariation(pgn, index)) {
                            yield(Token(TokenKind.UNCLOSED_VARIATION, pgn.substring(variationStart, start), variationStart))
                            variationDepth = 0
                        }
                        if (variationDepth == 0) yield(Token(TokenKind.MOVE, text, start))
                    }
                }
            }
            if (variationDepth > 0) {
                yield(Token(TokenKind.UNCLOSED_VARIATION, pgn.substring(variationStart), variationStart))
            }
        }

        private fun stripMoveNumber(text: String): String = text.replace(MOVE_NUMBER_PATTERN, "")

        /** Move numbers, "…", "e.p." and loose glyphs such as ± or +- carry no move. */
        private fun isAnnotation(text: String): Boolean = text in NAMED_ANNOTATIONS ||
            (text.all { it in GLYPH_CHARS } && !(text.length > 1 && text.all { it == '-' }))

        private fun looksLikeMove(text: String): Boolean =
            MOVE_LIKE.containsMatchIn(ChessBoard.normalizeSan(stripMoveNumber(text)))

        /** Shown as the unreadable move, so the loader's import error names the problem. */
        private fun unclosedVariationMove(text: String): String {
            val snippet = text.replace(Regex("\\s+"), " ").trim()
            val shown = if (snippet.length > 24) snippet.take(24).trimEnd() + "…" else snippet
            return "unclosed variation “$shown”"
        }

        /** Normalize a game termination marker ("1-0", "½-½", "0–1", "*"); null for any other text. */
        fun resultToken(text: String): String? {
            val marker = text.replace('–', '-').replace('—', '-').replace('−', '-').replace("½", "1/2")
            return marker.takeIf { it in setOf("1-0", "0-1", "1/2-1/2", "*") }
        }

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
                    TokenKind.UNCLOSED_VARIATION -> {
                        // The main line's continuation is lost inside the variation; report it
                        // through the loader instead of silently ending the game here.
                        result.add(ParsedMove(unclosedVariationMove(token.text)))
                        break
                    }
                    TokenKind.MOVE -> {
                        val move = stripMoveNumber(token.text)
                        if (resultToken(move) != null) break
                        if (!isAnnotation(move)) {
                            // Keep invalid tokens (including the null move "--") so the board loader
                            // reports the first bad move rather than silently applying the rest to the
                            // wrong position.
                            result.add(ParsedMove(ChessBoard.normalizeSan(move)))
                        }
                    }
                }
            }
            return result
        }

        fun parseMoves(pgn: String): List<String> = parseMovesWithClock(pgn).map { it.san }

        fun parseResult(pgn: String): String? = tokens(pgn)
            .filter { it.kind == TokenKind.MOVE }.firstNotNullOfOrNull { resultToken(stripMoveNumber(it.text)) }

        /** Split collections independently of blank-line spacing or which header comes first. */
        fun splitGames(pgn: String): List<String> {
            val games = mutableListOf<String>()
            var start = 0
            var hasContent = false
            var hasMoves = false
            var finished = false
            // Text with neither a tag nor anything move-like (a note before the first game) is not a game.
            var isGame = false
            for (token in tokens(pgn)) {
                if (token.kind == TokenKind.COMMENT) continue
                val beginsNextGame = hasContent &&
                    ((token.kind == TokenKind.HEADER && hasMoves) || finished)
                if (beginsNextGame) {
                    if (isGame) games.add(pgn.substring(start, token.start).trim())
                    start = token.start
                    hasMoves = false
                    finished = false
                    isGame = false
                }
                hasContent = true
                when (token.kind) {
                    TokenKind.HEADER -> isGame = true
                    TokenKind.UNCLOSED_VARIATION -> hasMoves = true
                    TokenKind.MOVE -> {
                        hasMoves = true
                        finished = resultToken(stripMoveNumber(token.text)) != null
                        if (looksLikeMove(token.text)) isGame = true
                    }
                    TokenKind.COMMENT -> {}
                }
            }
            if (hasContent && isGame) games.add(pgn.substring(start).trim())
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

        /** The PGN Variant tag, or null when the game names none (standard chess). */
        fun variantTag(pgn: String): String? = parseHeaders(pgn)["Variant"]?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * True for standard chess, including games that start from a set-up position. Accepts a
         * PGN Variant tag ("Standard", "From Position") or a Lichess variant key ("fromPosition").
         */
        fun isStandardVariant(variant: String?): Boolean =
            variant == null || variant.lowercase().filter { it.isLetterOrDigit() } in STANDARD_VARIANTS
    }
}
