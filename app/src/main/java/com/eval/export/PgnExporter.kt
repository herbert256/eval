package com.eval.export

import com.eval.data.ChessServer
import com.eval.chess.PgnParser
import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.data.LichessGame
import com.eval.data.OpeningBook
import com.eval.ui.MoveDetails
import com.eval.ui.MoveQuality
import com.eval.ui.MoveScore

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
        val gameOpening = originalHeaders["Opening"] ?: openingName ?: if (startingBoard.getFen() == ChessBoard().getFen()) {
            OpeningBook.getOpeningName(openingMoves)
        } else null
        val startsWithBlack = startingBoard.getTurn() == PieceColor.BLACK
        val firstMoveNumber = startingBoard.getFen().substringAfterLast(' ').toInt()
        fun tag(name: String, value: String) {
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' ')
            sb.appendLine("[$name \"$escaped\"]")
        }

        // PGN Headers
        val siteName = when (server) {
            ChessServer.LOCAL -> "?"
            ChessServer.LICHESS -> "Lichess.org"
        }
        tag("Event", originalHeaders["Event"] ?: game.perf ?: "Game")
        tag("Site", originalHeaders["Site"] ?: siteName)
        tag("Date", originalHeaders["Date"] ?: formatDate(game.createdAt))
        tag("White", game.players.white.user?.name ?: "Unknown")
        tag("Black", game.players.black.user?.name ?: "Unknown")
        sb.appendLine("[Result \"${formatResult(game.winner, game.status)}\"]")
        game.players.white.rating?.let { sb.appendLine("[WhiteElo \"$it\"]") }
        game.players.black.rating?.let { sb.appendLine("[BlackElo \"$it\"]") }
        gameOpening?.let { tag("Opening", it) }
        if (originalHeaders["FEN"] != null) {
            tag("SetUp", "1")
            tag("FEN", startingBoard.getFen())
        }
        sb.appendLine("[Annotator \"Eval App - Stockfish\"]")
        sb.appendLine()

        // Moves with annotations
        val moves = mutableListOf<String>()

        for (i in moveDetails.indices) {
            val detail = moveDetails[i]
            val ply = i + if (startsWithBlack) 1 else 0
            val moveNum = firstMoveNumber + ply / 2
            val isWhite = ply % 2 == 0

            val moveText = StringBuilder()

            // Add move number
            if (isWhite) {
                moveText.append("$moveNum. ")
            } else if (i == 0 || moves.isEmpty()) {
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
        sb.append(" ${formatResult(game.winner, game.status)}")
        sb.appendLine()

        return sb.toString()
    }

    private fun formatDate(timestamp: Long?): String {
        if (timestamp == null) return "????.??.??"
        val date = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US)
        return date.format(java.util.Date(timestamp))
    }

    private fun formatResult(winner: String?, status: String?): String {
        return when {
            winner == "white" -> "1-0"
            winner == "black" -> "0-1"
            status == "draw" || status == "stalemate" || status == "1/2-1/2" -> "1/2-1/2"
            else -> "*"
        }
    }
}
