package com.eval.chess

import org.junit.Assert.*
import org.junit.Test

class LegalMoveTreeTest {
    // Counts verified independently with Stockfish 19's `go perft` command.
    private fun count(board: ChessBoard, depth: Int): Long {
        if (depth == 0) return 1
        var result = 0L
        for (rank in 0..7) for (file in 0..7) {
            val from = Square(file, rank)
            for (to in board.getLegalMoves(from)) {
                val promotions = if (board.needsPromotion(from, to)) {
                    listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
                } else listOf(null)
                for (promotion in promotions) {
                    val next = board.copy()
                    check(next.makeMoveFromSquares(from, to, promotion))
                    result += count(next, depth - 1)
                }
            }
        }
        return result
    }

    @Test fun legal_move_trees_match_stockfish_for_castling_pins_and_promotions() {
        val cases = listOf(
            Triple(ChessBoard().getFen(), 3, 8902L),
            Triple("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3, 97862L),
            Triple("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 3, 2812L),
            Triple("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 2, 1486L)
        )
        for ((fen, depth, expected) in cases) {
            val board = ChessBoard().apply { check(setFen(fen)) }
            assertEquals(fen, expected, count(board, depth))
        }
    }
}
