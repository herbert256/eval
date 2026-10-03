package com.eval.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The evaluation, score difference and time graphs share one x position per move. */
class GraphMovePositionTest {
    @Test fun moves_sit_at_the_centre_of_equal_slots() {
        assertEquals(5f, moveSlotCenterX(0, 10, 100f), 0.001f)
        assertEquals(55f, moveSlotCenterX(5, 10, 100f), 0.001f)
        assertEquals(95f, moveSlotCenterX(9, 10, 100f), 0.001f)
        assertEquals(50f, moveSlotCenterX(0, 1, 100f), 0.001f)
    }

    @Test fun a_touch_selects_the_move_whose_slot_it_is_in() {
        for (index in 0 until 10) {
            assertEquals(index, moveIndexAtX(moveSlotCenterX(index, 10, 100f), 10, 100f))
        }
        assertEquals(0, moveIndexAtX(-20f, 10, 100f))
        assertEquals(9, moveIndexAtX(100f, 10, 100f))
        assertEquals(9, moveIndexAtX(250f, 10, 100f))
        assertEquals(0, moveIndexAtX(40f, 10, 0f))
    }
}
