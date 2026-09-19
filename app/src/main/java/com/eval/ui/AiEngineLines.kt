package com.eval.ui

import android.content.Context
import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Format complete legal continuations, retaining Stockfish's best-first MultiPV order. */
internal fun formatAiEngineLines(fen: String, result: AnalysisResult): String {
    val root = ChessBoard().apply { check(setFen(fen)) }
    val rows = result.lines.mapIndexed { index, line ->
        val board = root.copy()
        val continuation = line.pv.split(' ').filter { it.isNotBlank() }.map { uci ->
            val san = checkNotNull(board.sanForMove(uci)) { "Stockfish returned an invalid continuation." }
            check(board.makeUciMove(uci)) { "Stockfish returned an invalid continuation." }
            san
        }
        check(continuation.isNotEmpty()) { "Stockfish returned an empty continuation." }
        "${index + 1}. ${aiMoveScore(line, root.getTurn() == PieceColor.WHITE)} " +
            "(depth ${result.depth}): ${continuation.joinToString(" ")} [UCI: ${line.pv}]"
    }
    val side = if (root.getTurn() == PieceColor.WHITE) "White" else "Black"
    return "Top ${rows.size} Stockfish lines, best first for $side to move. " +
        "Scores in pawns from White's perspective (positive = White advantage; negative = Black advantage; " +
        "+M/-M = White/Black mates in N moves).\n" + rows.joinToString("\n")
}

internal class AiEngineLines(private val context: Context) {
    suspend fun generate(fen: String, settings: AiEngineSettings): String = withContext(Dispatchers.IO) {
        if (fen.isBlank()) return@withContext ""
        val board = ChessBoard().apply { check(setFen(fen)) { "The position cannot be analysed." } }
        val legalMoveCount = legalAiMoves(board).size
        if (legalMoveCount == 0) return@withContext "No legal moves in this position."
        val count = minOf(settings.multiPv, legalMoveCount)
        val engine = StockfishEngine(context.applicationContext)
        try {
            check(engine.initialize()) { "Stockfish is unavailable. Install or restart Stockfish and try again." }
            engine.configure(settings.threads, settings.hashMb, count, settings.useNnue)
            val result = engine.evaluateLines(fen, count, (settings.secondsForPosition * 1000).toInt())
            formatAiEngineLines(fen, result)
        } finally {
            engine.shutdown()
        }
    }
}
