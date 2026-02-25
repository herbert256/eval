package com.eval.ui

import com.eval.chess.ChessBoard

/**
 * Mutable timeline of board snapshots where index 0 is the start position
 * and index N is the board after N moves.
 */
class GameTimeline {
    private val snapshots = java.util.Collections.synchronizedList(mutableListOf<ChessBoard>())

    val snapshotList: MutableList<ChessBoard>
        get() = snapshots

    fun clear() {
        snapshots.clear()
    }

    fun resetToInitial(board: ChessBoard = ChessBoard()) {
        snapshots.clear()
        snapshots.add(board.copy())
    }

    fun replaceAll(boards: List<ChessBoard>) {
        snapshots.clear()
        snapshots.addAll(boards.map { it.copy() })
    }

    fun append(board: ChessBoard) {
        snapshots.add(board.copy())
    }

    fun boardAtMove(moveIndex: Int): ChessBoard {
        return snapshots.getOrNull(moveIndex + 1)?.copy() ?: ChessBoard()
    }
}

