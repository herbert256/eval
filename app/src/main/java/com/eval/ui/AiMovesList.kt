package com.eval.ui

import android.content.Context
import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.chess.Square
import com.eval.stockfish.PvLine
import com.eval.stockfish.StockfishEngine
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class AiLegalMove(val uci: String, val san: String)

/** Enumerate every legal move, including all four promotions, without altering the board. */
internal fun legalAiMoves(board: ChessBoard): List<AiLegalMove> = buildList {
    for (index in 0..63) {
        val from = Square(index % 8, index / 8)
        for (to in board.getLegalMoves(from)) {
            val suffixes = if (board.needsPromotion(from, to)) listOf("q", "r", "b", "n") else listOf("")
            for (suffix in suffixes) {
                val uci = from.toAlgebraic() + to.toAlgebraic() + suffix
                add(AiLegalMove(uci, checkNotNull(board.sanForMove(uci))))
            }
        }
    }
}

internal fun aiMoveScore(line: PvLine, whiteToMove: Boolean): String {
    if (line.isMate) {
        val whiteMates = (line.mateIn > 0) == whiteToMove
        return "${if (whiteMates) "+" else "-"}M${kotlin.math.abs(line.mateIn)}"
    }
    val whiteScore = if (whiteToMove) line.score else -line.score
    return String.format(Locale.US, "%+.2f", if (whiteScore == 0f) 0f else whiteScore)
}

/** A separate engine keeps board analysis and its settings untouched. */
internal class AiMovesList(private val context: Context) {
    suspend fun generate(
        fen: String,
        settings: AiMovesSettings,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        if (fen.isBlank()) return@withContext ""
        val board = ChessBoard()
        check(board.setFen(fen)) { "The position cannot be analysed." }
        val moves = legalAiMoves(board)
        onProgress(0, moves.size)
        if (moves.isEmpty()) return@withContext "No legal moves in this position."

        val engine = StockfishEngine(context.applicationContext)
        try {
            check(engine.initialize()) { "Stockfish is unavailable. Install or restart Stockfish and try again." }
            engine.configure(settings.threads, settings.hashMb, 1, settings.useNnue)
            val rows = mutableListOf<String>()
            for ((index, move) in moves.withIndex()) {
                currentCoroutineContext().ensureActive()
                val result = engine.evaluateMove(fen, move.uci, (settings.secondsForMove * 1000).toInt())
                val score = aiMoveScore(checkNotNull(result.bestLine), board.getTurn() == PieceColor.WHITE)
                rows += "${move.san} (${move.uci}): $score (depth ${result.depth})"
                onProgress(index + 1, moves.size)
            }
            "All ${moves.size} legal moves. Stockfish evaluations in pawns from White's perspective " +
                "(positive = White advantage; negative = Black advantage; +M/-M = White/Black mates in N moves).\n" +
                rows.joinToString("\n")
        } finally {
            engine.shutdown()
        }
    }
}
