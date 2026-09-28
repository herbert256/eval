package com.eval.stockfish

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * In-memory scripted UCI engine. Commands written to [stdin] are recorded and passed to
 * the script; replies go to [stdout]. [emitAfter] and the script share one timer thread,
 * so delayed replies keep their order.
 */
internal class FakeUciEngine(private val script: FakeUciEngine.(String) -> Unit) {
    val commands = CopyOnWriteArrayList<String>()
    @Volatile var alive = true
        private set
    private val output = LinkedBlockingQueue<ByteArray>()
    private val end = ByteArray(0)
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it).apply { isDaemon = true } }

    fun emit(line: String) {
        if (alive) output.put("$line\n".toByteArray())
    }

    fun emitAfter(delayMs: Long, line: String) = later(delayMs) { emit(line) }

    fun later(delayMs: Long, action: () -> Unit) {
        runCatching { timer.schedule(action, delayMs, TimeUnit.MILLISECONDS) }
    }

    /** The process exits: output already written is still read, then EOF. */
    fun exit() {
        if (!alive) return
        alive = false
        output.put(end)
        timer.shutdownNow()
    }

    fun session(onEnd: (UciSession) -> Unit = {}, terminate: () -> Unit = ::exit) =
        UciSession(stdout, stdin, alive = { alive }, terminate = terminate, onEnd = onEnd)

    val stdout: InputStream = object : InputStream() {
        private var current = ByteArray(0)
        private var pos = 0
        private var eof = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= current.size) {
                if (eof) return -1
                val next = output.take()
                if (next === end) {
                    eof = true
                    return -1
                }
                current = next
                pos = 0
            }
            val n = minOf(len, current.size - pos)
            System.arraycopy(current, pos, b, off, n)
            pos += n
            return n
        }

        override fun available(): Int = current.size - pos
    }

    val stdin: OutputStream = object : OutputStream() {
        private val pending = ByteArrayOutputStream()

        @Synchronized override fun write(b: Int) {
            if (!alive) throw IOException("Broken pipe")
            if (b == '\n'.code) {
                val command = pending.toString(Charsets.UTF_8.name())
                pending.reset()
                commands += command
                script(command)
            } else {
                pending.write(b)
            }
        }
    }
}

/**
 * Behaves like Stockfish for the ENG-1 race: `isready` is answered at once even while
 * searching, a stopped search sends its last info line and bestmove a little later, and
 * `go` during a search first finishes the old one. Each position has its own best move,
 * so misattributed output is detectable.
 */
internal class FakeStockfish(private val stopLatencyMs: Long = 40, options: List<String> = listOf("Threads", "Hash", "MultiPV")) {
    private val optionLines = options.map { "option name $it type spin default 1 min 1 max 256" }
    private val lock = Any()
    @Volatile private var position = ""
    @Volatile private var current: Search? = null

    private class Search(val position: String, val maxDepth: Int) {
        var done = false
    }

    val engine: FakeUciEngine = FakeUciEngine { command -> handle(command) }

    companion object {
        fun bestMoveFor(position: String): String = if (position.contains(" b ")) "e7e5" else "e2e4"
    }

    private fun FakeUciEngine.finish(search: Search) = synchronized(lock) {
        if (search.done) return@synchronized
        search.done = true
        emit("info depth 99 seldepth 99 multipv 1 score cp 1 nodes 1 nps 1 pv ${bestMoveFor(search.position)}")
        emit("bestmove ${bestMoveFor(search.position)}")
    }

    private fun FakeUciEngine.iterate(search: Search, depth: Int) {
        synchronized(lock) {
            if (search.done) return
            emit("info depth $depth seldepth ${depth + 2} multipv 1 score cp ${depth % 7} nodes ${depth * 1000} nps 100000 pv ${bestMoveFor(search.position)}")
            if (depth >= search.maxDepth) {
                search.done = true
                emit("bestmove ${bestMoveFor(search.position)}")
                return
            }
        }
        later(5) { iterate(search, depth + 1) }
    }

    private fun FakeUciEngine.handle(command: String) {
        when {
            command == "uci" -> {
                emit("Fake Stockfish by nobody")
                emit("id name Stockfish 99")
                optionLines.forEach { emit(it) }
                emit("uciok")
            }
            command == "isready" -> emit("readyok")
            command.startsWith("position fen ") -> position = command.removePrefix("position fen ")
            command.startsWith("go") -> {
                current?.let { finish(it) }
                val maxDepth = Regex("go depth (\\d+)").find(command)?.groupValues?.get(1)?.toInt() ?: 1000
                val search = Search(position, maxDepth)
                current = search
                emit("info string NNUE evaluation using fake.nnue")
                later(5) { iterate(search, 1) }
            }
            command == "stop" -> current?.let { search -> later(stopLatencyMs) { finish(search) } }
        }
    }
}
