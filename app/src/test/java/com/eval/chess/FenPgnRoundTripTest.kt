package com.eval.chess

import com.eval.data.LichessGame
import com.eval.data.Player
import com.eval.data.Players
import com.eval.data.User
import com.eval.export.PgnExporter
import com.eval.ui.BoardHistoryBuilder
import com.eval.ui.MoveDetails
import com.eval.ui.MoveScore
import org.junit.Assert.*
import org.junit.Test

class FenPgnRoundTripTest {
    @Test fun custom_black_to_move_position_survives_annotated_export_and_reimport() {
        val fen = "4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17"
        val pgn = "[SetUp \"1\"]\n[FEN \"$fen\"]\n\n17... e5 18. e4 *"
        val originalBoard = requireNotNull(PgnParser.parseInitialBoard(pgn))
        val history = BoardHistoryBuilder.build(PgnParser.parseMoves(pgn), originalBoard)
        assertEquals(listOf("e5", "e4"), history.validMoves)
        val game = LichessGame(id = "test", rated = false, variant = "standard", speed = "classical", perf = null,
            status = "*", winner = null, players = Players(
                Player(user = User("A \"quoted\" player", "a"), rating = null, aiLevel = null),
                Player(user = User("B", "b"), rating = null, aiLevel = null)),
            pgn = pgn, moves = null, clock = null, createdAt = null, lastMoveAt = null)
        val details = history.validMoves.mapIndexed { i, san ->
            val move = requireNotNull(history.boards[i + 1].getLastMove())
            MoveDetails(san, move.from.toAlgebraic(), move.to.toAlgebraic(), false, "P", "0:05:00")
        }
        val exported = PgnExporter.exportAnnotatedPgn(game, details, mapOf(0 to MoveScore(score = 0.25f, isMate = false, mateIn = 0)), emptyMap(), null)
        assertEquals(fen, PgnParser.parseHeaders(exported)["FEN"])
        assertEquals("1", PgnParser.parseHeaders(exported)["SetUp"])
        assertEquals("A \"quoted\" player", PgnParser.parseHeaders(exported)["White"])
        assertTrue(exported.contains("17... e5"))
        assertTrue(exported.contains("18. e4"))
        assertEquals("0:05:00", PgnParser.parseMovesWithClock(exported).first().clockTime)
        val restored = BoardHistoryBuilder.build(PgnParser.parseMoves(exported), requireNotNull(PgnParser.parseInitialBoard(exported)))
        assertEquals(history.boards.last().getFen(), restored.boards.last().getFen())
        assertEquals(fen, originalBoard.getFen())
    }

    @Test fun setup_without_a_valid_fen_is_rejected() {
        assertNull(PgnParser.parseInitialBoard("[SetUp \"1\"]\n\n1. e4 *"))
        assertNull(PgnParser.parseInitialBoard("[FEN \"invalid\"]\n\n*"))
        assertEquals(ChessBoard().getFen(), PgnParser.parseInitialBoard("1. e4 *")?.getFen())
        assertEquals(ChessBoard().getFen(), PgnParser.parseInitialBoard("{[FEN \"invalid\"]} 1. e4 *")?.getFen())
    }

    @Test fun fen_accepts_pasted_whitespace_without_changing_the_position() {
        val board = ChessBoard()
        assertTrue(board.setFen("4k3/8/8/8/8/8/4P3/4K3  w\t- - 0 1"))
        assertEquals("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1", board.getFen())
    }
}
