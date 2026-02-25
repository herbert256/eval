package com.eval.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PgnLoadFailureBehaviorTest {

    @Test
    fun board_history_builder_stops_at_first_invalid_move() {
        val moves = listOf("e2e4", "e7e5", "invalid_move", "g1f3")
        val result = BoardHistoryBuilder.build(moves)

        assertEquals(listOf("e2e4", "e7e5"), result.validMoves)
        assertEquals(3, result.boards.size) // initial + 2 valid moves
        assertEquals(2, result.failedMoveIndex)
        assertEquals("invalid_move", result.failedMove)
        assertNotNull(result.boards.last())
    }
}

