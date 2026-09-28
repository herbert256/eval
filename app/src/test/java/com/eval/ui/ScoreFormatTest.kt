package com.eval.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Flipping a 0.00 score to White's view gives -0.0, which was shown as "+-0.0". */
class ScoreFormatTest {
    @Test fun scores_that_round_to_zero_are_plus_zero() {
        assertEquals("+0.0", MoveScore(-0.0f, false, 0).formatDisplay())
        assertEquals("+0.0", MoveScore(-0.04f, false, 0).formatDisplay())
        assertEquals("+0.00", MoveScore(-0.0f, false, 0).formatDisplay(decimals = 2))
    }

    @Test fun other_scores_keep_their_sign() {
        assertEquals("-0.1", MoveScore(-0.06f, false, 0).formatDisplay().replace(',', '.'))
        assertEquals("+1.5", MoveScore(1.5f, false, 0).formatDisplay().replace(',', '.'))
        assertEquals("-M3", MoveScore(-100f, true, -3).formatDisplay())
    }
}
