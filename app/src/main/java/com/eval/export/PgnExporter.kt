package com.eval.export

import com.eval.data.ChessServer
import com.eval.chess.PgnParser
import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.data.LichessGame
import com.eval.data.OpeningBook
import com.eval.data.Player
import com.eval.ui.MoveDetails
import com.eval.ui.MoveQuality
import com.eval.ui.MoveScore
import com.eval.ui.gameResultToken

/**
 * Exports games as annotated PGN with evaluations and move quality symbols.
 */
object PgnExporter {

    /**
     * Export a game as annotated PGN string.
     *
     * @param game The game data
     * @param moveDetails List of move details
     * @param analyseScores Map of move index to score
     * @param moveQualities Map of move index to quality
     * @param openingName Optional opening name
     * @param server The originating server — used for the Site header
     * @return Formatted PGN string
     */
    fun exportAnnotatedPgn(
        game: LichessGame,
        moveDetails: List<MoveDetails>,
        analyseScores: Map<Int, MoveScore>,
        moveQualities: Map<Int, MoveQuality>,
        openingName: String?,
        server: ChessServer = ChessServer.LICHESS
    ): String {
        val sb = StringBuilder()
        val originalHeaders = PgnParser.parseHeaders(game.pgn.orEmpty())
        val startingBoard = requireNotNull(PgnParser.parseInitialBoard(game.pgn.orEmpty()))
        val board = startingBoard.copy()
        val openingMoves = mutableListOf<String>()
        val sanMoves = moveDetails.mapIndexed { index, detail ->
            val san = requireNotNull(board.sanForMove(detail.san)) { "Invalid move ${index + 1}: ${detail.san}" }
            check(board.makeMove(san))
            val move = board.getLastMove()!!
            // The book matches opening prefixes, before any possible promotion.
            openingMoves.add(move.from.toAlgebraic() + move.to.toAlgebraic())
            san
        }
        // A caller's opening name may really be the ECO code; that belongs in its own tag.
        val callerOpening = openingName?.takeUnless { ECO_CODE.matches(it) || it == originalHeaders["ECO"] }
        val gameOpening = originalHeaders["Opening"] ?: callerOpening ?: if (startingBoard.getFen() == ChessBoard().getFen()) {
            OpeningBook.getOpeningName(openingMoves)
        } else null
        val startsWithBlack = startingBoard.getTurn() == PieceColor.BLACK
        val firstMoveNumber = startingBoard.getFen().substringAfterLast(' ').toInt()
        val result = gameResultToken(game)
        fun tag(name: String, value: String) {
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' ')
            sb.appendLine("[$name \"$escaped\"]")
        }
        // Lichess AI opponents have no user; the source PGN still names them.
        fun playerName(player: Player, color: String): String =
            player.user?.name ?: originalHeaders[color] ?: player.aiLevel?.let { "lichess AI level $it" } ?: "?"

        // PGN Headers: the Seven Tag Roster first, then the source's other tags.
        val siteName = when (server) {
            ChessServer.LOCAL -> "?"
            ChessServer.LICHESS -> "Lichess.org"
        }
        tag("Event", originalHeaders["Event"] ?: game.perf ?: "Game")
        tag("Site", originalHeaders["Site"] ?: siteName)
        tag("Date", originalHeaders["Date"] ?: formatDate(game.createdAt))
        tag("Round", originalHeaders["Round"] ?: "?")
        tag("White", playerName(game.players.white, "White"))
        tag("Black", playerName(game.players.black, "Black"))
        tag("Result", result)
        (game.players.white.rating?.toString() ?: originalHeaders["WhiteElo"])?.let { tag("WhiteElo", it) }
        (game.players.black.rating?.toString() ?: originalHeaders["BlackElo"])?.let { tag("BlackElo", it) }
        // Titles, time control, ECO, termination, UTC date and the like are carried through unchanged.
        originalHeaders.filterKeys { it !in REWRITTEN_TAGS }.forEach { (name, value) -> tag(name, value) }
        gameOpening?.let { tag("Opening", it) }
        if (originalHeaders["FEN"] != null) {
            tag("SetUp", "1")
            tag("FEN", startingBoard.getFen())
        }
        sb.appendLine("[Annotator \"Eval App - Stockfish\"]")
        sb.appendLine()

        // Moves with annotations
        val moves = mutableListOf<String>()
        var previousHasComment = false

        for (i in moveDetails.indices) {
            val detail = moveDetails[i]
            val ply = i + if (startsWithBlack) 1 else 0
            val moveNum = firstMoveNumber + ply / 2
            val isWhite = ply % 2 == 0

            val moveText = StringBuilder()

            // Add move number; Black's move needs its own number after a comment.
            if (isWhite) {
                moveText.append("$moveNum. ")
            } else if (i == 0 || moves.isEmpty() || previousHasComment) {
                moveText.append("$moveNum... ")
            }

            // Add move notation
            moveText.append(sanMoves[i])

            // Add quality symbol (NAG)
            val quality = moveQualities[i]
            when (quality) {
                MoveQuality.BRILLIANT -> moveText.append("!!")
                MoveQuality.GOOD -> moveText.append("!")
                MoveQuality.INTERESTING -> moveText.append("!?")
                MoveQuality.DUBIOUS -> moveText.append("?!")
                MoveQuality.MISTAKE -> moveText.append("?")
                MoveQuality.BLUNDER -> moveText.append("??")
                else -> {}
            }

            // Add evaluation comment
            val score = analyseScores[i]
            if (score != null) {
                val evaluation = if (score.isMate) "#${score.mateIn}"
                    else String.format(java.util.Locale.US, "%.2f", score.score)
                moveText.append(" {[%eval $evaluation]}")
            }

            // Add clock comment if available
            detail.clockTime?.let { clock ->
                moveText.append(" {[%clk $clock]}")
            }

            previousHasComment = score != null || detail.clockTime != null
            moves.add(moveText.toString())
        }

        // Format moves with line wrapping
        var lineLength = 0
        val maxLineLength = 80

        for (move in moves) {
            if (lineLength + move.length + 1 > maxLineLength && lineLength > 0) {
                sb.appendLine()
                lineLength = 0
            }
            if (lineLength > 0) {
                sb.append(" ")
                lineLength++
            }
            sb.append(move)
            lineLength += move.length
        }

        // Add result
        sb.append(" $result")
        sb.appendLine()

        return sb.toString()
    }

    private val ECO_CODE = Regex("[A-E][0-9]{2}")

    /** Tags written from the game data above, or recomputed for the exported moves. */
    private val REWRITTEN_TAGS = setOf(
        "Event", "Site", "Date", "Round", "White", "Black", "Result", "WhiteElo", "BlackElo",
        "Opening", "SetUp", "FEN", "Annotator", "PlyCount"
    )

    private fun formatDate(timestamp: Long?): String {
        if (timestamp == null) return "????.??.??"
        val date = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US)
        return date.format(java.util.Date(timestamp))
    }
}
