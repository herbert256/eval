package com.eval.stockfish

import com.eval.chess.ChessBoard
import org.junit.Assert.*
import org.junit.Test

class EngineProtocolTest {
    private val start = ChessBoard().getFen()

    private fun boardsAfter(vararg uci: String): List<ChessBoard> {
        val boards = mutableListOf(ChessBoard())
        for (move in uci) boards += boards.last().copy().apply { assertTrue(move, makeUciMove(move)) }
        return boards
    }

    @Test fun info_lines_parse_scores_ranks_and_capped_principal_variations() {
        val info = UciParsing.info("info depth 20 seldepth 31 multipv 2 score cp -35 nodes 123456 nps 654321 hashfull 10 tbhits 0 time 188 pv e7e5 g1f3")!!
        assertEquals(20, info.depth)
        assertEquals(123456L, info.nodes)
        assertEquals(654321L, info.nps)
        assertEquals(PvLine(-0.35f, false, 0, "e7e5 g1f3", 2), info.line)
        assertFalse(info.bound)
        val mate = UciParsing.info("info depth 9 score mate -3 upperbound nodes 5 pv " + List(80) { "a1a2" }.joinToString(" "))!!
        assertEquals(PvLine(-100f, true, -3, List(64) { "a1a2" }.joinToString(" "), 1), mate.line)
        assertTrue(mate.bound)
        assertNull(UciParsing.info("info depth 12 currmove e2e4 currmovenumber 1"))
        assertNull(UciParsing.info("bestmove e2e4"))
    }

    @Test fun bounds_never_replace_exact_lines_and_are_only_a_fallback() {
        val lines = PvAccumulator(start)
        assertEquals(PvAccumulator.Recorded(PvLine(0.3f, false, 0, "e2e4 e7e5", 1), exact = true),
            lines.record("info depth 10 multipv 1 score cp 30 nodes 10 nps 5 pv e2e4 e7e5"))
        assertNull("A bound after an exact line is ignored", lines.record("info depth 11 multipv 1 score cp 90 lowerbound nodes 20 nps 5 pv d2d4"))
        assertEquals(10, lines.result!!.depth)
        assertEquals("e2e4 e7e5", lines.result!!.pv)
        assertTrue(lines.bestLineIsExact)

        // Rank 2 has only a bound so far: shown until its exact line arrives.
        assertEquals(false, lines.record("info depth 11 multipv 2 score cp 10 upperbound nodes 30 nps 5 pv c2c4")!!.exact)
        assertEquals(listOf("e2e4 e7e5", "c2c4"), lines.result!!.lines.map { it.pv })
        lines.record("info depth 11 multipv 2 score cp 12 nodes 40 nps 5 pv g1f3")
        assertEquals(listOf("e2e4 e7e5", "g1f3"), lines.result!!.lines.map { it.pv })
        assertEquals(start, lines.result!!.fen)
        assertEquals(40L, lines.result!!.nodes)

        val onlyBound = PvAccumulator(start)
        onlyBound.record("info depth 3 score cp 50 lowerbound pv e2e4")
        assertEquals("e2e4", onlyBound.result!!.bestMove)
        assertFalse("evaluateMove must not accept this", onlyBound.bestLineIsExact)
    }

    @Test fun position_uses_the_history_only_when_it_is_legal_and_reaches_the_fen() {
        val boards = boardsAfter("g1f3", "g8f6", "f3g1", "f6g8", "g1f3")
        val history = requireNotNull(EngineHistory.fromBoards(boards))
        assertEquals(EngineHistory(start, listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3")), history)
        val fen = boards.last().getFen()
        assertEquals(PositionCommand("position fen $start moves g1f3 g8f6 f3g1 f6g8 g1f3", usesHistory = true),
            UciParsing.positionCommand(fen, history))

        val plain = PositionCommand("position fen $fen", usesHistory = false)
        assertEquals(plain, UciParsing.positionCommand(fen, null))
        assertEquals(plain, UciParsing.positionCommand(fen, EngineHistory(fen, emptyList())))
        // Leads to another position, contains an illegal move, or tries to inject a command.
        assertEquals(plain, UciParsing.positionCommand(fen, EngineHistory(start, listOf("e2e4"))))
        assertEquals("Same placement, but the repetitions would be lost",
            plain, UciParsing.positionCommand(fen, EngineHistory(start, listOf("g1f3"))))
        assertEquals(plain, UciParsing.positionCommand(fen, EngineHistory(start, listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1g3"))))
        assertEquals(plain, UciParsing.positionCommand(fen, EngineHistory(start, listOf("g1f3\nquit"))))
        assertEquals(plain, UciParsing.positionCommand(fen, EngineHistory("$start\nquit", history.uciMoves)))
        assertNull(UciParsing.positionCommand("$fen\ngo infinite", null))
        assertNull(UciParsing.positionCommand("startpos", null))
        assertNull(UciParsing.positionCommand("", null))
    }

    @Test fun promotions_are_part_of_the_history() {
        val board = ChessBoard().apply { assertTrue(setFen("7k/P7/8/8/8/8/8/K7 w - - 0 1")) }
        val promoted = board.copy().apply { assertTrue(makeUciMove("a7a8n")) }
        val history = requireNotNull(EngineHistory.fromBoards(listOf(board, promoted)))
        assertEquals(listOf("a7a8n"), history.uciMoves)
        assertTrue(UciParsing.positionCommand(promoted.getFen(), history)!!.usesHistory)
        assertNull("A board without a last move breaks the chain", EngineHistory.fromBoards(listOf(board, board.copy())))
    }

    @Test fun critical_errors_become_readable_messages() {
        assertEquals("Stockfish rejected the position: Unsupported position. King can be captured.",
            UciParsing.criticalError("info string CRITICAL ERROR: Command `position fen 4k3/4Q3/8/8/8/8/8/4K3 w - - 0 1` failed. Reason: Unsupported position. King can be captured."))
        assertEquals("Stockfish rejected the position: Illegal move: e2e5",
            UciParsing.criticalError("info string CRITICAL ERROR: Command `position startpos moves e2e5` failed. Reason: Illegal move: e2e5"))
        assertEquals("Stockfish reported an error: something broke", UciParsing.criticalError("info string CRITICAL ERROR: something broke"))
        assertNull(UciParsing.criticalError("info string NNUE evaluation using nn-1a298aa575a0.nnue"))
    }

    @Test fun option_names_come_from_the_uci_handshake() {
        assertEquals("Use NNUE", UciParsing.optionName("option name Use NNUE type check default true"))
        assertEquals("Clear Hash", UciParsing.optionName("option name Clear Hash type button"))
        assertEquals("EvalFile", UciParsing.optionName("option name EvalFile type string default nn-1a298aa575a0.nnue"))
        assertNull(UciParsing.optionName("id name Stockfish 19"))
        assertNull(UciParsing.optionName("No such option: Use NNUE"))
    }

    @Test fun every_counter_the_board_accepts_reaches_the_engine() {
        val max = ChessBoard.MAX_MOVE_COUNTER
        val fen = "4k3/8/8/8/8/8/8/4K2R w K - $max $max"
        assertTrue(fen, ChessBoard().setFen(fen))
        assertEquals("position fen $fen", UciParsing.positionCommand(fen, null)?.command)
        assertNull(UciParsing.positionCommand("4k3/8/8/8/8/8/8/4K2R w K - 0 1\nquit", null))
        assertNull(UciParsing.positionCommand("4k3/8/8/8/8/8/8/4K2R w K - 0 12345678", null))
    }
}
