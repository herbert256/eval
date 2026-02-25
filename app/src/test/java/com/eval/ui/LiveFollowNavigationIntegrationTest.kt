package com.eval.ui

import com.eval.chess.ChessBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveFollowNavigationIntegrationTest {

    @Test
    fun timeline_appends_live_move_and_supports_navigation_to_latest_position() {
        val initialMoves = listOf("e2e4", "e7e5", "g1f3")
        val built = BoardHistoryBuilder.build(initialMoves)
        val timeline = GameTimeline()
        timeline.replaceAll(built.boards)

        val liveBoard = built.boards.last().copy()
        val appended = liveBoard.makeUciMove("b8c6")
        assertEquals(true, appended)
        timeline.append(liveBoard)

        val latestBoard = timeline.boardAtMove(3)
        assertNotNull(latestBoard)
        assertEquals("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", latestBoard.getFen())
    }
}

