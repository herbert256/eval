package com.eval.ui

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class MateZeroScoreTest {
    @Test fun checkmate_zero_retains_the_winning_color_in_display_and_saved_scores() {
        for ((numeric, expected) in listOf(100f to "+M0", -100f to "-M0")) {
            val score = MoveScore(numeric, true, 0)
            assertEquals(expected, score.formatDisplay())
            val restored = Gson().fromJson(Gson().toJson(score), MoveScore::class.java)
            assertEquals(expected, restored.formatDisplay())
        }
    }

    @Test fun nonzero_mates_keep_their_signed_distance() {
        assertEquals("+M3", MoveScore(100f, true, 3).formatDisplay())
        assertEquals("-M3", MoveScore(-100f, true, -3).formatDisplay())
    }

    @Test fun graph_endpoints_keep_the_winner_at_mate_zero_across_ranges() {
        for (range in listOf(3f, 10f, 100f)) {
            assertEquals(range, MoveScore(100f, true, 0).graphValue(range), 0f)
            assertEquals(-range, MoveScore(-100f, true, 0).graphValue(range), 0f)
            assertEquals(range, MoveScore(100f, true, 1).graphValue(range), 0f)
            assertEquals(-range, MoveScore(-100f, true, -1).graphValue(range), 0f)
        }
        assertEquals(0f, MoveScore(0f, false, 0).graphValue(10f), 0f)
        assertFalse(MoveScore(0f, false, 0).isPositiveMate)
    }
}
