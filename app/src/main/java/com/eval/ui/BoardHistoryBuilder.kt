package com.eval.ui

import com.eval.chess.ChessBoard

data class BoardHistoryResult(
    val boards: List<ChessBoard>,
    val validMoves: List<String>,
    val failedMoveIndex: Int? = null,
    val failedMove: String? = null
)

object BoardHistoryBuilder {
    /**
     * Builds board snapshots by applying SAN/UCI moves in sequence.
     * Stops at first invalid move to avoid diverging from the real game position.
     */
    fun build(
        moves: List<String>,
        onMoveApplied: ((index: Int, move: String, boardBefore: ChessBoard, boardAfter: ChessBoard) -> Unit)? = null,
        onMoveFailed: ((index: Int, move: String, boardBefore: ChessBoard) -> Unit)? = null
    ): BoardHistoryResult {
        val boards = mutableListOf<ChessBoard>()
        val validMoves = mutableListOf<String>()
        val tempBoard = ChessBoard()
        boards.add(tempBoard.copy())

        for ((index, move) in moves.withIndex()) {
            val boardBeforeMove = if (onMoveApplied != null || onMoveFailed != null) tempBoard.copy() else tempBoard
            val moveSuccess = tempBoard.makeMove(move) || tempBoard.makeUciMove(move)
            if (moveSuccess) {
                validMoves.add(move)
                boards.add(tempBoard.copy())
                onMoveApplied?.invoke(index, move, boardBeforeMove, tempBoard)
            } else {
                onMoveFailed?.invoke(index, move, boardBeforeMove)
                return BoardHistoryResult(
                    boards = boards,
                    validMoves = validMoves,
                    failedMoveIndex = index,
                    failedMove = move
                )
            }
        }

        return BoardHistoryResult(boards = boards, validMoves = validMoves)
    }
}

