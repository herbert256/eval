package com.eval.chess

import kotlin.math.abs

/** An editor draft may be incomplete; only validated FEN is handed to the game. */
internal data class BoardSetupPosition(
    val squares: String = ".".repeat(64),
    val whiteToMove: Boolean = true,
    val castling: String = "",
    val enPassant: String = "-",
    val halfMoves: String = "0",
    val fullMove: String = "1"
) {
    init { require(squares.length == 64 && squares.all { it in ".KQRBNPkqrbnp" }) }

    fun pieceAt(index: Int): Piece? = piece(squares[index])

    fun place(index: Int, symbol: Char): BoardSetupPosition {
        require(index in 0..63 && symbol in ".KQRBNPkqrbnp")
        val changed = squares.toCharArray()
        // The king palette relocates that king, rather than creating duplicates.
        if (symbol == 'K' || symbol == 'k') changed.indices.forEach { if (changed[it] == symbol) changed[it] = '.' }
        changed[index] = symbol
        return copy(squares = String(changed)).tidyRights()
    }

    fun move(from: Int, to: Int): BoardSetupPosition {
        require(from in 0..63 && to in 0..63)
        if (from == to || squares[from] == '.') return this
        val changed = squares.toCharArray()
        changed[to] = changed[from]
        changed[from] = '.'
        return copy(squares = String(changed)).tidyRights()
    }

    fun withTurn(white: Boolean) = copy(whiteToMove = white).tidyRights()

    /** Correct an image whose orientation was read backwards, changing squares, not the view. */
    fun rotatePieces() = copy(squares = squares.reversed(), castling = "", enPassant = "-")

    fun canCastle(right: Char): Boolean = when (right) {
        'K' -> squares[4] == 'K' && squares[7] == 'R'
        'Q' -> squares[4] == 'K' && squares[0] == 'R'
        'k' -> squares[60] == 'k' && squares[63] == 'r'
        'q' -> squares[60] == 'k' && squares[56] == 'r'
        else -> false
    }

    fun withCastling(right: Char, enabled: Boolean) = copy(castling = "KQkq".filter {
        canCastle(it) && if (it == right) enabled else it in castling
    })

    /** A FEN target records the last double push even if no pawn can capture. */
    fun enPassantTargets(): List<String> = (0..7).mapNotNull { file ->
        val targetRank = if (whiteToMove) 5 else 2
        val pawnRank = if (whiteToMove) 4 else 3
        val sourceRank = if (whiteToMove) 6 else 1
        if (squares[pawnRank * 8 + file] == (if (whiteToMove) 'p' else 'P') &&
            squares[targetRank * 8 + file] == '.' && squares[sourceRank * 8 + file] == '.') {
            Square(file, targetRank).toAlgebraic()
        } else null
    }

    data class LastPawnMove(val from: String, val to: String, val enPassant: String) {
        val label: String get() = "$from–$to"
    }

    /** Offer only legal double pushes followed by at least one legal en passant capture. */
    fun enPassantLastMoves(): List<LastPawnMove> {
        // Discovery concerns the pieces and turn; counters can still be unfinished in the editor.
        val position = copy(enPassant = "-", halfMoves = "0", fullMove = "1")
        if (position.validationError() != null) return emptyList()
        return enPassantTargets().mapNotNull { targetName ->
            val target = requireNotNull(Square.fromAlgebraic(targetName))
            val pawnRank = if (whiteToMove) 4 else 3
            val source = Square(target.file, if (whiteToMove) 6 else 1)
            val destination = Square(target.file, pawnRank)
            val current = ChessBoard()
            if (!current.setFen(position.copy(enPassant = targetName).toFen())) return@mapNotNull null
            val canCapture = listOf(target.file - 1, target.file + 1).filter { it in 0..7 }.any { file ->
                val from = Square(file, pawnRank)
                squares[from.index] == (if (whiteToMove) 'P' else 'p') && current.isLegalMove(from, target)
            }
            if (!canCapture) return@mapNotNull null

            // Undo the proposed last move and verify that the resulting predecessor and push are legal.
            val before = position.move(destination.index, source.index).withTurn(!whiteToMove)
            if (before.validationError() != null) return@mapNotNull null
            val previous = ChessBoard()
            if (!previous.setFen(before.toFen()) ||
                !previous.makeUciMove(source.toAlgebraic() + destination.toAlgebraic())) return@mapNotNull null
            LastPawnMove(source.toAlgebraic(), destination.toAlgebraic(), targetName)
        }
    }

    private fun tidyRights(): BoardSetupPosition = copy(
        castling = "KQkq".filter { it in castling && canCastle(it) },
        enPassant = enPassant.takeIf { it in enPassantTargets() } ?: "-"
    )

    fun toFen(): String {
        val placement = (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val symbol = squares[rank * 8 + file]
                    if (symbol == '.') empty++ else {
                        if (empty > 0) append(empty)
                        empty = 0
                        append(symbol)
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        return "$placement ${if (whiteToMove) "w" else "b"} ${castling.ifEmpty { "-" }} $enPassant $halfMoves $fullMove"
    }

    fun validationError(): String? {
        if (squares.count { it == 'K' } != 1 || squares.count { it == 'k' } != 1)
            return "Place one white king and one black king."
        if ((0..7).any { squares[it].lowercaseChar() == 'p' || squares[56 + it].lowercaseChar() == 'p' })
            return "Pawns cannot be on the first or last rank."
        val whiteKing = squares.indexOf('K')
        val blackKing = squares.indexOf('k')
        if (abs(whiteKing / 8 - blackKing / 8) <= 1 && abs(whiteKing % 8 - blackKing % 8) <= 1)
            return "The kings cannot be next to each other."
        for (white in listOf(true, false)) {
            val pieces = squares.filter { it != '.' && it.isUpperCase() == white }
            if (pieces.length > 16 || pieces.count { it.lowercaseChar() == 'p' } > 8)
                return "Each side can have at most 16 pieces, including 8 pawns."
        }
        if (halfMoves.toIntOrNull()?.let { it >= 0 } != true || halfMoves.any { it !in '0'..'9' })
            return "Halfmove counter must be a whole number of 0 or more."
        if (fullMove.toIntOrNull()?.let { it >= 1 } != true || fullMove.any { it !in '0'..'9' })
            return "Move number must be a whole number of 1 or more."
        if (castling.any { !canCastle(it) }) return "Castling requires the king and rook on their starting squares."
        if (enPassant != "-" && enPassant !in enPassantTargets()) return "Choose a valid en passant target."
        if (enPassant != "-" && halfMoves.toIntOrNull() != 0) return "En passant requires a halfmove counter of 0."
        val board = ChessBoard()
        if (!board.setFen(toFen())) return "This position cannot be opened. Check the position details."
        if (board.isKingInCheck(if (whiteToMove) PieceColor.BLACK else PieceColor.WHITE))
            return "The side that just moved cannot be in check. Change the side to move or adjust the pieces."
        return null
    }

    companion object {
        fun initial() = fromFen(ChessBoard().getFen())

        fun fromFen(fen: String): BoardSetupPosition {
            val board = ChessBoard()
            require(board.setFen(fen))
            return requireNotNull(fromDraftFen(board.getFen())).tidyRights()
        }

        /** Read recognized pieces even when kings are missing or the position needs correction. */
        fun fromDraftFen(fen: String): BoardSetupPosition? {
            val fields = fen.trim().split(Regex("\\s+"))
            if (fields.size !in 1..6) return null
            val ranks = fields[0].split('/')
            if (ranks.size != 8) return null
            val squares = CharArray(64) { '.' }
            for ((row, rank) in ranks.withIndex()) {
                var file = 0
                for (symbol in rank) {
                    if (symbol in '1'..'8') file += symbol.digitToInt()
                    else if (symbol in "KQRBNPkqrbnp" && file < 8) squares[(7 - row) * 8 + file++] = symbol
                    else return null
                    if (file > 8) return null
                }
                if (file != 8) return null
            }
            val turn = fields.getOrElse(1) { "w" }
            if (turn !in listOf("w", "b")) return null
            val castling = fields.getOrElse(2) { "-" }.let { if (it == "-") "" else it }
            if (castling.any { it !in "KQkq" } || castling.toSet().size != castling.length) return null
            val ep = fields.getOrElse(3) { "-" }
            if (ep != "-" && Square.fromAlgebraic(ep) == null) return null
            return BoardSetupPosition(String(squares), turn == "w", castling, ep,
                fields.getOrElse(4) { "0" }, fields.getOrElse(5) { "1" })
        }

        fun piece(symbol: Char): Piece? {
            val type = when (symbol.lowercaseChar()) {
                'k' -> PieceType.KING; 'q' -> PieceType.QUEEN; 'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP; 'n' -> PieceType.KNIGHT; 'p' -> PieceType.PAWN
                else -> return null
            }
            return Piece(type, if (symbol.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK)
        }
    }
}
