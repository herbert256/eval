package com.eval.chess

data class ParsedMove(
    val san: String,
    val clockTime: String? = null  // Format: "H:MM:SS" or "M:SS" or null if not available
)

class PgnParser {
    companion object {
        /**
         * Parse moves with clock times from PGN.
         * Clock times are extracted from comments like {[%clk 0:10:00]} or {[%clk 1:30]}
         */
        fun parseMovesWithClock(pgn: String): List<ParsedMove> {
            // Remove headers (lines starting with [)
            val movesSection = pgn.lines()
                .dropWhile { it.startsWith("[") || it.isBlank() }
                .joinToString(" ")
                .trim()

            val result = mutableListOf<ParsedMove>()

            // Regex to match a move followed by optional clock comment
            // Matches: e4 {[%clk 0:10:00]} or Nf3 {[%clk 1:30]} or just e4
            val movePattern = Regex(
                """(\d+\.+\s*)?([KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?|O-O-O|O-O)(\s*\{[^}]*\})?"""
            )

            val clockPattern = Regex("""\[%clk\s+(\d+:\d+(?::\d+)?)\]""")

            for (match in movePattern.findAll(movesSection)) {
                val san = match.groupValues[2]
                if (san.isBlank()) continue

                // Check if there's a comment with clock time
                val commentPart = match.groupValues[3]
                var clockTime: String? = null

                if (commentPart.isNotBlank()) {
                    val clockMatch = clockPattern.find(commentPart)
                    if (clockMatch != null) {
                        clockTime = clockMatch.groupValues[1]
                    }
                }

                result.add(ParsedMove(san, clockTime))
            }

            return result
        }

        fun parseMoves(pgn: String): List<String> {
            return parseMovesWithClock(pgn).map { it.san }
        }

        fun parseHeaders(pgn: String): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            val headerRegex = Regex("\\[([A-Za-z]+)\\s+\"([^\"]*)\"\\]")

            for (line in pgn.lines()) {
                val match = headerRegex.find(line)
                if (match != null) {
                    headers[match.groupValues[1]] = match.groupValues[2]
                }
            }

            return headers
        }
    }
}
