package com.eval.chess

data class ParsedMove(
    val san: String,
    val clockTime: String? = null  // Format: "H:MM:SS" or "M:SS" or null if not available
)

class PgnParser {
    companion object {
        private val UCI_PATTERN = Regex("""[a-h][1-8][a-h][1-8][qrbn]?""")
        private val WHITESPACE_PATTERN = Regex("""\s+""")
        private val MOVE_PATTERN = Regex(
            """(\d+\.+\s*)?([KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?|O-O-O|O-O)(\s*\{[^}]*\})?"""
        )
        private val CLOCK_PATTERN = Regex("""\[%clk\s+(\d+:\d+(?::\d+)?)\]""")
        private val HEADER_PATTERN = Regex("""\[([A-Za-z]+)\s+"([^"]*)"\]""")
        private val MOVE_NUMBER_PATTERN = Regex("""^\d+\.""")

        /**
         * Parse moves with clock times from PGN.
         * Clock times are extracted from comments like {[%clk 0:10:00]} or {[%clk 1:30]}
         * Supports both SAN notation (e4, Nf3) and UCI notation (e2e4, g1f3)
         */
        fun parseMovesWithClock(pgn: String): List<ParsedMove> {
            // Remove headers (lines starting with [) then strip PGN variations
            // so the main-line regex doesn't pick up variation moves and emit
            // them as part of the game. NAGs ("$1", "$5") fall through the
            // move-pattern regex unchanged and don't match, so they're ignored.
            val raw = pgn.lines()
                .dropWhile { it.startsWith("[") || it.isBlank() }
                .joinToString(" ")
                .trim()
            val movesSection = stripVariations(raw)

            val result = mutableListOf<ParsedMove>()

            // Check if this looks like UCI format (moves like e2e4, g1f3)
            val firstMoves = movesSection.split(WHITESPACE_PATTERN).take(5).filter {
                MOVE_NUMBER_PATTERN.matches(it) == false && it != "*"
            }
            val looksLikeUci = firstMoves.any { UCI_PATTERN.matches(it) }

            if (looksLikeUci) {
                return parseUciMoves(movesSection)
            }

            for (match in MOVE_PATTERN.findAll(movesSection)) {
                val san = match.groupValues[2]
                if (san.isBlank()) continue

                // Check if there's a comment with clock time
                val commentPart = match.groupValues[3]
                var clockTime: String? = null

                if (commentPart.isNotBlank()) {
                    val clockMatch = CLOCK_PATTERN.find(commentPart)
                    if (clockMatch != null) {
                        clockTime = clockMatch.groupValues[1]
                    }
                }

                result.add(ParsedMove(san, clockTime))
            }

            return result
        }

        /**
         * Parse UCI format moves (e2e4, g1f3, etc.)
         */
        private fun parseUciMoves(movesSection: String): List<ParsedMove> {
            val result = mutableListOf<ParsedMove>()

            // Split by whitespace and filter for UCI moves
            for (token in movesSection.split(WHITESPACE_PATTERN)) {
                val cleaned = token.replace(MOVE_NUMBER_PATTERN, "") // Remove move numbers
                if (UCI_PATTERN.matches(cleaned)) {
                    result.add(ParsedMove(cleaned, null))
                }
            }

            return result
        }

        fun parseMoves(pgn: String): List<String> {
            return parseMovesWithClock(pgn).map { it.san }
        }

        /**
         * Remove parenthesised PGN variations, including nested ones, while
         * preserving comments in braces. Walks the string once counting paren
         * depth; a comment that spans a paren (unusual but allowed) is kept
         * because we suspend depth tracking inside '{...}'.
         */
        private fun stripVariations(text: String): String {
            val sb = StringBuilder(text.length)
            var depth = 0
            var inComment = false
            for (c in text) {
                when {
                    inComment -> {
                        sb.append(c)
                        if (c == '}') inComment = false
                    }
                    c == '{' -> { sb.append(c); inComment = true }
                    c == '(' -> depth++
                    c == ')' -> if (depth > 0) depth-- else sb.append(c)
                    depth == 0 -> sb.append(c)
                    // else: inside a variation, drop char
                }
            }
            return sb.toString()
        }

        fun parseHeaders(pgn: String): Map<String, String> {
            val headers = mutableMapOf<String, String>()

            for (line in pgn.lines()) {
                val match = HEADER_PATTERN.find(line)
                if (match != null) {
                    headers[match.groupValues[1]] = match.groupValues[2]
                }
            }

            return headers
        }
    }
}
