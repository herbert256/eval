package com.eval.ui

import android.content.Context
import android.os.SystemClock
import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.StockfishEngine
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

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

data class AiEngineProgress(
    val fen: String,
    val lineCount: Int,
    val durationMs: Long,
    val elapsedMs: Long = 0,
    val result: AnalysisResult? = null,
    val engineName: String = "Stockfish",
    val searching: Boolean = false
) {
    val fraction: Float get() = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal class AiEngineLines(private val context: Context) {
    suspend fun generate(
        fen: String,
        settings: AiEngineSettings,
        stopRequested: Deferred<Unit>? = null,
        onProgress: (AiEngineProgress) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (fen.isBlank()) return@withContext ""
        val board = ChessBoard().apply { check(setFen(fen)) { "The position cannot be analysed." } }
        val legalMoveCount = legalAiMoves(board).size
        if (legalMoveCount == 0) return@withContext "No legal moves in this position."
        val count = minOf(settings.multiPv, legalMoveCount)
        val durationMs = (settings.secondsForPosition * 1000).toLong().coerceAtLeast(1)
        val latest = AtomicReference<AnalysisResult?>(null)
        val startedAt = AtomicLong(0)
        val engine = StockfishEngine(context.applicationContext)
        fun publish() {
            val start = startedAt.get()
            onProgress(AiEngineProgress(fen, count, durationMs,
                elapsedMs = if (start == 0L) 0 else SystemClock.elapsedRealtime() - start,
                result = latest.get(), engineName = engine.engineName.value ?: "Stockfish",
                searching = start != 0L))
        }
        try {
            coroutineScope {
                publish()
                val search = async {
                    check(engine.initialize()) { "Stockfish is unavailable. Install or restart Stockfish and try again." }
                    engine.configure(settings.threads, settings.hashMb, count, settings.useNnue)
                    startedAt.set(SystemClock.elapsedRealtime())
                    engine.evaluateLines(fen, count, durationMs.toInt()) { latest.set(it) }
                }
                val ticker = launch {
                    while (isActive) {
                        publish()
                        delay(100)
                    }
                }
                try {
                    val result = if (stopRequested == null) search.await() else select<AnalysisResult?> {
                        stopRequested.onAwait {
                            search.cancelAndJoin()
                            latest.get()
                        }
                        search.onAwait { it }
                    }
                    if (result == null) {
                        "Stockfish search stopped before a complete set of lines was available."
                    } else {
                        latest.set(result)
                        publish()
                        formatAiEngineLines(fen, result)
                    }
                } finally {
                    ticker.cancelAndJoin()
                }
            }
        } finally {
            engine.shutdown()
        }
    }
}
