package com.eval.stockfish

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** One engine output line, tagged with the number of the search that was running when it arrived. */
internal class UciLine(val text: String, val search: Long)

internal class UciHandshake(val name: String?, val options: Set<String>)

internal sealed class SearchOutcome {
    class Completed(val bestMove: String?, val linesRead: Int) : SearchOutcome()
    /** The engine refused the position (Stockfish 19 prints CRITICAL ERROR and exits). */
    class Rejected(val message: String) : SearchOutcome()
    /** The engine's output ended; [outputSeen] tells whether this search produced any info first. */
    class Died(val outputSeen: Boolean) : SearchOutcome()
    /** The previous search did not end after stop. */
    object StillSearching : SearchOutcome()
    object NotReady : SearchOutcome()
    /** The time limit passed and the engine ignored stop. */
    object TimedOut : SearchOutcome()
}

/**
 * One engine process. A dedicated thread reads its output into a channel, so a timed
 * read really times out and a line arriving later is kept for the next read. Commands
 * are written whole under one lock. The reader counts `go`/`bestmove` and
 * `isready`/`readyok` pairs, so a new search can wait for the previous one to end and
 * ignore its output, even when a consumer was cancelled mid-read.
 * Reads must come from one consumer at a time.
 */
internal class UciSession(
    input: InputStream,
    output: OutputStream,
    private val alive: () -> Boolean,
    private val terminate: () -> Unit,
    private val kill: () -> Unit = terminate,
    private val onEnd: (UciSession) -> Unit = {}
) {
    companion object {
        const val IDLE_POLL_MS = 3000L

        fun start(process: Process, onEnd: (UciSession) -> Unit = {}) = UciSession(
            process.inputStream, process.outputStream,
            alive = { process.isAlive },
            terminate = { process.destroy() },
            kill = { process.destroyForcibly() },
            onEnd = onEnd
        )
    }

    private val writer = BufferedWriter(OutputStreamWriter(output))
    private val reader = BufferedReader(InputStreamReader(input))
    private val writeLock = Any()
    private val lines = Channel<UciLine>(Channel.UNLIMITED)
    private val searchesStarted = AtomicLong()
    private val searchesEnded = AtomicLong()
    private val readyRequests = AtomicLong()
    private val readyReplies = AtomicLong()

    /** True once the engine's output has ended (process exit or read error). */
    @Volatile var ended = false
        private set
    @Volatile private var closing = false

    val searching: Boolean get() = searchesEnded.get() < searchesStarted.get()

    private val readerThread = thread(isDaemon = true, name = "uci-reader") { readLoop() }

    private fun readLoop() {
        try {
            while (true) {
                val text = reader.readLine()?.trimEnd() ?: break
                // Counters change before the line is queued, so whoever receives
                // this line or a later one also sees the updated counts.
                val search = if (text.startsWith("bestmove")) {
                    searchesEnded.incrementAndGet()
                } else {
                    if (text == "readyok") readyReplies.incrementAndGet()
                    searchesEnded.get() + 1
                }
                lines.trySend(UciLine(text, search))
            }
        } catch (_: Exception) {
            // Closed or failed stream: treated as the end of output, never thrown on this thread.
        } finally {
            ended = true
            lines.close()
            runCatching { reader.close() }
            if (!closing) runCatching { onEnd(this) }
        }
    }

    fun send(command: String): Boolean = synchronized(writeLock) { write(command) }

    private fun write(vararg commands: String): Boolean {
        // A line break would let text through as a second command.
        if (commands.any { command -> command.any { it == '\n' || it == '\r' } }) return false
        return try {
            for (command in commands) {
                writer.write(command)
                writer.newLine()
            }
            writer.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Next output line, or null after [timeoutMs] or once the output has ended and been read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun readLine(timeoutMs: Long): UciLine? {
        lines.tryReceive().getOrNull()?.let { return it }
        if (timeoutMs <= 0) return null
        // Unlike cancelling a receive with withTimeoutOrNull, a select timeout never
        // takes a line out of the channel, so a line arriving at the deadline is kept.
        return select {
            lines.onReceiveCatching { it.getOrNull() }
            onTimeout(timeoutMs) { null }
        }
    }

    /** Waits for [condition], discarding output meanwhile. */
    private suspend fun awaitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) return false
            if (readLine(remaining) == null && ended) return condition()
        }
        return true
    }

    /** Sends `uci` and reads the engine name and advertised options up to `uciok`. */
    suspend fun handshake(timeoutMs: Long): UciHandshake? {
        if (!send("uci")) return null
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        var name: String? = null
        val options = TreeSet(String.CASE_INSENSITIVE_ORDER)
        while (true) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) return null
            val line = readLine(remaining) ?: if (ended) return null else continue
            if (line.text == "uciok") return UciHandshake(name, options)
            UciParsing.engineName(line.text)?.let { name = it }
            UciParsing.optionName(line.text)?.let { options += it }
        }
    }

    /** Sends `isready` and waits for its own `readyok`. */
    suspend fun awaitReady(timeoutMs: Long): Boolean {
        val target = synchronized(writeLock) {
            val request = readyRequests.incrementAndGet()
            if (!write("isready")) return false
            request
        }
        return awaitUntil(timeoutMs) { readyReplies.get() >= target }
    }

    /** Stops the running search, if any, and waits until its bestmove has arrived. */
    suspend fun stopSearch(timeoutMs: Long): Boolean {
        if (!searching) return true
        send("stop")
        return awaitUntil(timeoutMs) { !searching }
    }

    /**
     * Runs one search. Any earlier search is stopped and its bestmove awaited, [setup]
     * commands are sent, `isready` is answered, and then [position] and [go] start the
     * search. Only this search's lines reach [onLine]. With [timeLimitMs], `stop` is sent
     * when it passes, and the engine then has [stopTimeoutMs] to answer.
     */
    suspend fun search(
        position: String,
        go: String,
        timeLimitMs: Long?,
        stopTimeoutMs: Long,
        readyTimeoutMs: Long,
        setup: List<String> = emptyList(),
        onLine: (String) -> Unit
    ): SearchOutcome {
        require((position + go).none { it == '\n' || it == '\r' })
        if (!stopSearch(stopTimeoutMs)) return if (ended) SearchOutcome.Died(false) else SearchOutcome.StillSearching
        setup.forEach { send(it) }
        if (!awaitReady(readyTimeoutMs)) return if (ended) SearchOutcome.Died(false) else SearchOutcome.NotReady
        val sent: Boolean
        val id = synchronized(writeLock) {
            val next = searchesStarted.incrementAndGet()
            sent = write(position, go)
            next
        }
        val limit = timeLimitMs?.let { System.nanoTime() + it * 1_000_000 }
        // A failed write means the engine is exiting, e.g. after rejecting the position:
        // read what it printed until its output ends, but no longer than stopTimeoutMs.
        var stopDeadline: Long? = if (sent) null else System.nanoTime() + stopTimeoutMs * 1_000_000
        var linesRead = 0
        var outputSeen = false
        var rejected: String? = null
        var deadPolls = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val now = System.nanoTime()
            if (stopDeadline != null && now >= stopDeadline) return SearchOutcome.TimedOut
            if (stopDeadline == null && limit != null && now >= limit) {
                send("stop")
                stopDeadline = now + stopTimeoutMs * 1_000_000
            }
            // Without a time limit (depth searches) wait as long as the engine is alive.
            val due = stopDeadline ?: limit
            val wait = if (due == null) IDLE_POLL_MS else ((due - now) / 1_000_000).coerceIn(1, IDLE_POLL_MS)
            val line = readLine(wait)
            if (line == null) {
                if (ended || (!alive() && ++deadPolls >= 2)) {
                    return rejected?.let { SearchOutcome.Rejected(it) } ?: SearchOutcome.Died(outputSeen)
                }
                continue
            }
            // Output of an earlier search that ended before this one started.
            if (line.search != id) continue
            linesRead++
            val text = line.text
            if (text.startsWith("bestmove")) {
                return rejected?.let { SearchOutcome.Rejected(it) }
                    ?: SearchOutcome.Completed(text.split(' ').getOrNull(1), linesRead)
            }
            val error = UciParsing.criticalError(text)
            if (error != null) {
                // If the engine keeps running, its output belongs to the previous position.
                rejected = rejected ?: error
            } else if (rejected == null) {
                if (text.startsWith("info depth")) outputSeen = true
                onLine(text)
            }
        }
    }

    /**
     * Terminates the engine first, so the reader thread sees EOF instead of blocking,
     * then releases the streams. With [wait] false the release runs on its own thread.
     */
    fun close(wait: Boolean) {
        closing = true
        runCatching { terminate() }
        val release = {
            // End of input also makes the engine quit.
            runCatching { writer.close() }
            readerThread.join(1000)
            if (readerThread.isAlive) {
                runCatching { kill() }
                readerThread.join(1000)
            }
        }
        if (wait) release() else thread(isDaemon = true, name = "uci-close") { runCatching { release() } }
    }
}
