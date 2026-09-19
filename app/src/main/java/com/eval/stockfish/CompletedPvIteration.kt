package com.eval.stockfish

/** Keep the last complete MultiPV iteration when the time limit interrupts the next one. */
internal class CompletedPvIteration(private val lineCount: Int) {
    private var depth = -1
    private val lines = mutableMapOf<Int, PvLine>()
    var result: AnalysisResult? = null
        private set

    fun record(info: AnalysisResult, line: PvLine) {
        if (info.depth != depth || line.multipv == 1) {
            depth = info.depth
            lines.clear()
        }
        if (line.multipv !in 1..lineCount || line.pv.isBlank()) return
        lines[line.multipv] = line
        if ((1..lineCount).all { it in lines }) {
            val ordered = (1..lineCount).map { lines.getValue(it) }
            if (ordered.map { it.pv.substringBefore(' ') }.distinct().size == lineCount) {
                result = info.copy(lines = ordered)
            }
        }
    }
}
