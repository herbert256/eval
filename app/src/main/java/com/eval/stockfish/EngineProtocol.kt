package com.eval.stockfish

import com.eval.chess.ChessBoard
import com.eval.chess.PieceType

data class PvLine(
    val score: Float,
    val isMate: Boolean,
    val mateIn: Int,
    val pv: String,
    val multipv: Int
)

data class AnalysisResult(
    val depth: Int,
    val nodes: Long,
    val nps: Long,
    val lines: List<PvLine>,
    val fen: String? = null
) {
    // Convenience properties for backward compatibility
    val bestLine: PvLine? get() = lines.firstOrNull()
    val score: Float get() = bestLine?.score ?: 0f
    val isMate: Boolean get() = bestLine?.isMate ?: false
    val mateIn: Int get() = bestLine?.mateIn ?: 0
    val bestMove: String get() = bestLine?.pv?.split(" ")?.firstOrNull() ?: ""
    val pv: String get() = bestLine?.pv ?: ""
}

/**
 * The moves from [startFen] to the analysed position. The engine then receives
 * `position fen <startFen> moves <uci…>` and can see repetitions, while results
 * stay tagged with the analysed position's own FEN.
 */
data class EngineHistory(val startFen: String, val uciMoves: List<String>) {
    companion object {
        /** History of consecutive boards (each the previous one plus its last move); null if a link is missing. */
        fun fromBoards(boards: List<ChessBoard>): EngineHistory? {
            val start = boards.firstOrNull() ?: return null
            val moves = boards.drop(1).map { board ->
                val move = board.getLastMove() ?: return null
                move.from.toAlgebraic() + move.to.toAlgebraic() + when (move.promotion) {
                    PieceType.QUEEN -> "q"
                    PieceType.ROOK -> "r"
                    PieceType.BISHOP -> "b"
                    PieceType.KNIGHT -> "n"
                    else -> ""
                }
            }
            return EngineHistory(start.getFen(), moves)
        }
    }
}

/** A failure tied to the position that caused it ([fen]); retrying the same position fails again. */
data class EngineError(val fen: String?, val message: String)

internal data class PositionCommand(val command: String, val usesHistory: Boolean)

internal object UciParsing {
    // Hard cap on the number of PV tokens we parse from an info line. Stockfish
    // can emit very long principal variations at high depth; a generous ceiling
    // keeps memory bounded without truncating useful mate/forced sequences.
    const val MAX_PV_TOKENS = 64
    val UCI_MOVE = Regex("[a-h][1-8][a-h][1-8][qrbn]?")
    // Only characters that can occur in a FEN, so no text can reach the engine as a second command.
    private val FEN = Regex("""[pnbrqkPNBRQK1-8/]{15,71} [wb] (-|[KQkqA-Ha-h]{1,4}) (-|[a-h][1-8])( \d{1,7} \d{1,7})?""")
    private val NAME = Regex("""id\s+name\s+(.+)""")
    private val OPTION = Regex("""option\s+name\s+(.+?)\s+type\s+\S+.*""")
    private val DEPTH = Regex("depth (\\d+)")
    private val NODES = Regex("nodes (\\d+)")
    private val NPS = Regex("nps (\\d+)")
    private val MULTIPV = Regex("multipv (\\d+)")
    private val MATE = Regex("score mate (-?\\d+)")
    private val CP = Regex("score cp (-?\\d+)")

    fun engineName(line: String): String? =
        NAME.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    fun optionName(line: String): String? =
        OPTION.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    fun isSafeFen(fen: String): Boolean = FEN.matches(fen)

    /**
     * The `position` command for [fen]. With a [history] whose moves are legal and lead
     * to [fen], the moves are sent so repetitions count; otherwise only [fen] is sent.
     * Returns null when [fen] is not a single well-formed FEN line.
     */
    fun positionCommand(fen: String, history: EngineHistory?): PositionCommand? {
        if (!isSafeFen(fen)) return null
        val plain = PositionCommand("position fen $fen", usesHistory = false)
        if (history == null || history.uciMoves.isEmpty()) return plain
        if (!isSafeFen(history.startFen) || history.uciMoves.any { !UCI_MOVE.matches(it) }) return plain
        // Stockfish exits on an illegal move in the list, so replay it first.
        val board = ChessBoard()
        if (!board.setFen(history.startFen)) return plain
        if (!history.uciMoves.all { board.makeUciMove(it) }) return plain
        // Same placement, side, castling, en passant and halfmove clock (a shorter
        // history can reach the same placement, but would hide repetitions).
        if (board.getFen().split(' ').take(5) != fen.split(' ').take(5)) return plain
        return PositionCommand("position fen ${history.startFen} moves ${history.uciMoves.joinToString(" ")}", usesHistory = true)
    }

    /** A readable message for Stockfish's "info string CRITICAL ERROR …" line, or null for other lines. */
    fun criticalError(line: String): String? {
        val index = line.indexOf("CRITICAL ERROR")
        if (index < 0) return null
        val reason = line.substringAfter("Reason:", "").trim()
        return if (reason.isNotEmpty()) "Stockfish rejected the position: $reason"
        else "Stockfish reported an error: ${line.substring(index + "CRITICAL ERROR".length).trimStart(':', ' ')}"
    }

    data class Info(val depth: Int, val nodes: Long?, val nps: Long?, val line: PvLine, val bound: Boolean)

    /** Parses an `info depth … score …` line; other lines return null. */
    fun info(line: String): Info? {
        if (!line.startsWith("info depth") || !line.contains("score")) return null
        return try {
            val depth = DEPTH.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val nodes = NODES.find(line)?.groupValues?.get(1)?.toLongOrNull()
            val nps = NPS.find(line)?.groupValues?.get(1)?.toLongOrNull()
            val multipv = MULTIPV.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            var score = 0f
            var isMate = false
            var mateIn = 0
            val mateMatch = MATE.find(line)
            val cpMatch = CP.find(line)
            if (mateMatch != null) {
                isMate = true
                mateIn = mateMatch.groupValues[1].toIntOrNull() ?: 0
                score = if (mateIn > 0) 100f else -100f
            } else if (cpMatch != null) {
                score = (cpMatch.groupValues[1].toIntOrNull() ?: 0) / 100f
            }

            // Extract PV: everything after the literal " pv " token. The UCI spec
            // places pv last in an info line so this is safe even if future Stockfish
            // builds reorder earlier fields; we anchor on the token position itself
            // rather than a greedy regex.
            val pvMarker = " pv "
            val pvIdx = line.indexOf(pvMarker)
            val pv = if (pvIdx >= 0) line.substring(pvIdx + pvMarker.length).trim() else ""
            // Cap tokens to keep memory bounded but keep enough to cover long mating
            // sequences (a previous 8-move cap silently truncated mate-in-N lines).
            val pvLine = PvLine(score, isMate, mateIn,
                pv.split(' ').filter { it.isNotEmpty() }.take(MAX_PV_TOKENS).joinToString(" "), multipv)
            Info(depth, nodes, nps, pvLine, line.contains(" lowerbound") || line.contains(" upperbound"))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * One search's lines for [fen]. A bound (`lowerbound`/`upperbound`) is not a final
 * score: it is kept only until that rank has an exact line and never replaces one.
 */
internal class PvAccumulator(private val fen: String) {
    data class Recorded(val line: PvLine, val exact: Boolean)

    private val exact = mutableMapOf<Int, PvLine>()
    private val bounds = mutableMapOf<Int, PvLine>()
    private var nodes = 0L
    private var nps = 0L
    var result: AnalysisResult? = null
        private set

    /** Records an engine output line; returns the line when [result] changed. */
    fun record(text: String): Recorded? {
        val info = UciParsing.info(text) ?: return null
        info.nodes?.let { nodes = it }
        info.nps?.let { nps = it }
        val rank = info.line.multipv
        if (info.bound) {
            if (rank in exact) return null
            bounds[rank] = info.line
        } else {
            exact[rank] = info.line
            bounds.remove(rank)
        }
        result = AnalysisResult(info.depth, nodes, nps, (bounds + exact).values.sortedBy { it.multipv }, fen)
        return Recorded(info.line, exact = !info.bound)
    }

    /** True when the best line of [result] is an exact score. */
    val bestLineIsExact: Boolean get() = result?.bestLine?.let { it.multipv in exact } == true
}
