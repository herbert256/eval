package com.eval.stockfish

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class UciSessionTest {
    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

    private fun elapsedMs(since: Long) = (System.nanoTime() - since) / 1_000_000

    @Test fun timed_read_returns_promptly_and_the_late_line_is_read_next() = runBlocking {
        val fake = FakeUciEngine { if (it == "ping") emitAfter(800, "late line") }
        val session = fake.session()
        try {
            assertTrue(session.send("ping"))
            val started = System.nanoTime()
            assertNull(session.readLine(100))
            assertTrue("Timeout took ${elapsedMs(started)} ms", elapsedMs(started) < 500)
            assertEquals("late line", session.readLine(3000)?.text)
        } finally {
            session.close(wait = true)
        }
    }

    @Test fun no_line_is_lost_when_reads_keep_timing_out() = runBlocking {
        val fake = FakeUciEngine {}
        val session = fake.session()
        val producer = thread {
            val random = java.util.Random(7)
            for (i in 0 until 400) {
                fake.emit("line $i")
                if (random.nextInt(4) == 0) Thread.sleep(random.nextInt(3).toLong())
            }
            fake.emit("done")
        }
        val received = mutableListOf<String>()
        while (received.lastOrNull() != "done") {
            session.readLine(1)?.let { received += it.text }
        }
        producer.join()
        assertEquals((0 until 400).map { "line $it" } + "done", received)
        session.close(wait = true)
    }

    @Test fun handshake_reads_the_name_and_options_case_insensitively() = runBlocking {
        val stockfish = FakeStockfish(options = listOf("Threads", "Hash", "MultiPV", "Use NNUE"))
        val session = stockfish.engine.session()
        try {
            val handshake = requireNotNull(session.handshake(2000))
            assertEquals("Stockfish 99", handshake.name)
            assertTrue("use nnue" in handshake.options)
            assertTrue("MULTIPV" in handshake.options)
            assertFalse("Skill Level" in handshake.options)
            assertTrue(session.awaitReady(2000))
        } finally {
            session.close(wait = true)
        }
    }

    @Test fun new_search_never_receives_the_stopped_searchs_output() = runBlocking {
        val stockfish = FakeStockfish(stopLatencyMs = 40)
        val session = stockfish.engine.session()
        try {
            assertNotNull(session.handshake(2000))
            repeat(20) { trial ->
                val (oldFen, newFen) = if (trial % 2 == 0) startFen to afterE4 else afterE4 to startFen
                val oldLines = CopyOnWriteArrayList<String>()
                val old = launch(Dispatchers.IO) {
                    session.search("position fen $oldFen", "go depth 1000", null, 2000, 2000) { oldLines += it }
                }
                withTimeout(2000) { while (oldLines.count { it.startsWith("info depth") } < 3) delay(5) }
                // As StockfishEngine.stop(): cancel the reader, then send stop (half the trials rely on search() to stop).
                old.cancelAndJoin()
                if (trial % 4 < 2) session.send("stop")

                val newLines = mutableListOf<String>()
                val outcome = session.search("position fen $newFen", "go depth 5", null, 2000, 2000) { newLines += it }
                val expected = FakeStockfish.bestMoveFor(newFen)
                assertTrue("Trial $trial: $outcome", outcome is SearchOutcome.Completed)
                assertEquals("Trial $trial bestmove", expected, (outcome as SearchOutcome.Completed).bestMove)
                val pvs = newLines.filter { it.startsWith("info depth") }.map { it.substringAfter(" pv ") }
                assertEquals("Trial $trial lines: $newLines", 5, pvs.size)
                assertTrue("Trial $trial got another position's lines: $pvs", pvs.all { it == expected })
            }
        } finally {
            session.close(wait = true)
        }
    }

    @Test fun critical_error_rejects_the_position_with_a_readable_reason() = runBlocking {
        val fake = FakeUciEngine { command ->
            when {
                command == "isready" -> emit("readyok")
                command.startsWith("position") -> {
                    emit("info string CRITICAL ERROR: Command `$command` failed. Reason: Unsupported position. King can be captured.")
                    emit("")
                    exit()
                }
            }
        }
        var ended: UciSession? = null
        val session = fake.session(onEnd = { ended = it })
        val outcome = session.search("position fen 4k3/4Q3/8/8/8/8/8/4K3 w - - 0 1", "go depth 5", null, 1000, 1000) {
            fail("No output may be attributed to a rejected position: $it")
        }
        assertTrue(outcome is SearchOutcome.Rejected)
        assertEquals("Stockfish rejected the position: Unsupported position. King can be captured.",
            (outcome as SearchOutcome.Rejected).message)
        assertTrue(session.ended)
        assertSame(session, ended)
    }

    @Test fun engine_exit_before_any_output_is_reported_as_death_without_output() = runBlocking {
        val fake = FakeUciEngine { command ->
            if (command == "isready") emit("readyok")
            if (command.startsWith("go")) exit()
        }
        val session = fake.session()
        val outcome = session.search("position fen $startFen", "go depth 5", null, 1000, 1000) {}
        assertTrue(outcome is SearchOutcome.Died)
        assertFalse((outcome as SearchOutcome.Died).outputSeen)
    }

    @Test fun time_limited_search_is_stopped_and_a_silent_engine_times_out() = runBlocking {
        val fake = FakeUciEngine { if (it == "isready") emit("readyok") }
        val session = fake.session()
        try {
            val started = System.nanoTime()
            val outcome = session.search("position fen $startFen", "go movetime 100", 200, 200, 1000) {}
            assertSame(SearchOutcome.TimedOut, outcome)
            assertTrue(elapsedMs(started) in 350..2000)
            assertEquals(listOf("isready", "position fen $startFen", "go movetime 100", "stop"), fake.commands.toList())
        } finally {
            session.close(wait = true)
        }
    }

    @Test fun ready_wait_times_out_against_a_silent_engine() = runBlocking {
        val session = FakeUciEngine {}.session()
        try {
            val started = System.nanoTime()
            assertFalse(session.awaitReady(200))
            assertTrue(elapsedMs(started) < 1000)
            assertNull(session.handshake(200))
        } finally {
            session.close(wait = true)
        }
    }

    @Test fun commands_from_several_threads_are_never_interleaved() {
        val fake = FakeUciEngine {}
        val session = fake.session()
        val writers = (0 until 8).map { t ->
            thread { repeat(200) { i -> assertTrue(session.send("setoption name Writer$t value $i")) } }
        }
        writers.forEach { it.join() }
        val expected = (0 until 8).flatMap { t -> (0 until 200).map { "setoption name Writer$t value $it" } }.toSet()
        assertEquals(1600, fake.commands.size)
        assertEquals(expected, fake.commands.toSet())
        assertFalse("A command with a line break must not be sent", session.send("stop\nquit"))
        session.close(wait = true)
    }

    @Test fun close_without_waiting_returns_while_the_reader_is_still_blocked() {
        val fake = FakeUciEngine {}
        // An engine that ignores termination at first: the caller must not wait for it.
        val session = fake.session(terminate = {})
        val started = System.nanoTime()
        session.close(wait = false)
        assertTrue(elapsedMs(started) < 100)
        assertFalse(session.ended)
        fake.exit()
        val deadline = System.nanoTime() + 2_000_000_000
        while (!session.ended && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(session.ended)
    }
}
