package com.eval.stockfish

import org.junit.Assert.*
import org.junit.Test

class CompletedPvIterationTest {
    private fun record(tracker: CompletedPvIteration, depth: Int, rank: Int, move: String) {
        val line = PvLine(0.2f, false, 0, move, rank)
        tracker.record(AnalysisResult(depth, 100, 1000, listOf(line), "position"), line)
    }

    @Test fun incomplete_next_depth_keeps_the_previous_complete_ranking() {
        val tracker = CompletedPvIteration(3)
        record(tracker, 8, 1, "e2e4")
        record(tracker, 8, 2, "d2d4")
        assertNull(tracker.result)
        record(tracker, 8, 3, "g1f3")
        record(tracker, 9, 1, "d2d4")
        assertEquals(8, tracker.result!!.depth)
        assertEquals(listOf("e2e4", "d2d4", "g1f3"), tracker.result!!.lines.map { it.pv })
        record(tracker, 9, 2, "e2e4")
        record(tracker, 9, 3, "c2c4")
        assertEquals(9, tracker.result!!.depth)
        assertEquals(listOf("d2d4", "e2e4", "c2c4"), tracker.result!!.lines.map { it.pv })
    }

    @Test fun duplicate_roots_empty_lines_and_mixed_depths_never_form_a_complete_set() {
        val tracker = CompletedPvIteration(2)
        record(tracker, 1, 1, "e2e4")
        record(tracker, 2, 2, "d2d4")
        assertNull(tracker.result)
        record(tracker, 3, 1, "e2e4")
        record(tracker, 3, 2, "e2e4")
        assertNull(tracker.result)
        record(tracker, 4, 1, "e2e4")
        record(tracker, 4, 2, "")
        assertNull(tracker.result)
    }
}
