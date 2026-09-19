package com.eval.stockfish

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import java.io.*

data class PvLine(
    val score: Float,
    val isMate: Boolean,
    val mateIn: Int,
    val pv: String,
    val multipv: Int
)

data class AnalysisResult(
    val depth: Int,
    val nodes: Long,
    val nps: Long,
    val lines: List<PvLine>,
    val fen: String? = null
) {
    // Convenience properties for backward compatibility
    val bestLine: PvLine? get() = lines.firstOrNull()
    val score: Float get() = bestLine?.score ?: 0f
    val isMate: Boolean get() = bestLine?.isMate ?: false
    val mateIn: Int get() = bestLine?.mateIn ?: 0
    val bestMove: String get() = bestLine?.pv?.split(" ")?.firstOrNull() ?: ""
    val pv: String get() = bestLine?.pv ?: ""
}

class StockfishEngine(private val context: Context) {
    companion object {
        // Hard cap on the number of PV tokens we parse from an info line. Stockfish
        // can emit very long principal variations at high depth; a generous ceiling
        // keeps memory bounded without truncating useful mate/forced sequences.
        private const val MAX_PV_TOKENS = 64
        // Maximum safe hash table size in MB to prevent crashes on mobile devices
        private const val MAX_SAFE_HASH_MB = 256
        // Maximum safe thread count for mobile devices
        private const val MAX_SAFE_THREADS = 4
        internal const val READY_TIMEOUT_MS = 15000L
        private val UCI_NAME = Regex("""id\s+name\s+(.+)""")

        internal fun parseUciEngineName(line: String): String? =
            UCI_NAME.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private var process: Process? = null
    private var processWriter: BufferedWriter? = null
    private var processReader: BufferedReader? = null

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _engineName = MutableStateFlow<String?>(null)
    val engineName: StateFlow<String?> = _engineName

    private var analysisJob: Job? = null
    // Scope is created lazily and can be recreated after shutdown
    private var _scope: CoroutineScope? = null
    private val scope: CoroutineScope
        get() = _scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob()).also { _scope = it }
    // Mutex to ensure only one analysis runs at a time
    private val analysisMutex = kotlinx.coroutines.sync.Mutex()
    private val lifecycleMutex = kotlinx.coroutines.sync.Mutex()
    // Lock for thread-safe access to pvLines
    private val pvLinesLock = Any()

    private var stockfishPath: String? = null
    private val pvLines = mutableMapOf<Int, PvLine>()
    private var currentNodes: Long = 0
    private var currentNps: Long = 0

    /**
     * Check if Stockfish is installed on the system (com.stockfish141 package).
     * This is a synchronous check that can be called before initialize().
     */
    fun isStockfishInstalled(): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getApplicationInfo("com.stockfish141", 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            try {
                // Use system-installed Stockfish (com.stockfish141 package)
                val systemStockfishPath = findSystemStockfish()
                if (systemStockfishPath != null) {
                    android.util.Log.i("StockfishEngine", "Using system Stockfish: $systemStockfishPath")
                    stockfishPath = systemStockfishPath
                } else {
                    android.util.Log.e("StockfishEngine", "Stockfish Chess Engine app not installed")
                    return@withContext false
                }

                // Start the process
                startProcess()
                _isReady.value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Attempts to find Stockfish binary from the system-installed com.stockfish141 package.
     * Returns the path to the binary if found, null otherwise.
     */
    private fun findSystemStockfish(): String? {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo("com.stockfish141", 0)
            val nativeLibDir = appInfo.nativeLibraryDir

            // Look for the Stockfish binary in the native lib directory.
            // Binary names vary by version: lib_sf171.so (17.1), lib_sf18.so (18), etc.
            val libDir = File(nativeLibDir)
            if (libDir.exists() && libDir.isDirectory) {
                val allFiles = libDir.listFiles().orEmpty()
                // Prefer explicit Stockfish library names, ordered by version (newest first).
                val candidates = allFiles.filter { file ->
                    val lower = file.name.lowercase()
                    lower.contains("stockfish") ||
                        lower.startsWith("lib_sf") ||
                        lower.contains("sf18") ||
                        lower.contains("sf17")
                }.sortedByDescending { it.name }
                val stockfishFile = candidates.firstOrNull { it.canExecute() }
                if (stockfishFile != null) {
                    android.util.Log.i("StockfishEngine", "Found Stockfish binary: ${stockfishFile.name}")
                    return stockfishFile.absolutePath
                }
                // Log all files for debugging if no match found
                android.util.Log.w("StockfishEngine", "No Stockfish binary found in $nativeLibDir. Files: ${allFiles.map { it.name }}")
            }
            null
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "System Stockfish package not installed")
            null
        } catch (e: Exception) {
            android.util.Log.e("StockfishEngine", "Error finding system Stockfish: ${e.message}")
            null
        }
    }

    private suspend fun startProcess() {
        _engineName.value = null
        val path = stockfishPath ?: return

        try {
            // Keep CPU-bound engine workers below the UI's scheduling priority.
            // In particular, several normal-priority workers can starve input
            // delivery on devices with few available CPUs.
            val nice = File("/system/bin/nice")
            val command = if (nice.canExecute()) listOf(nice.path, "-n", "10", path) else listOf(path)
            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val p = process ?: throw IllegalStateException("Stockfish process creation failed")
            processWriter = BufferedWriter(OutputStreamWriter(p.outputStream))
            processReader = BufferedReader(InputStreamReader(p.inputStream))

            // Initialize UCI
            sendCommand("uci")

            // Read until uciok
            // Loading the engine's neural network can take several seconds on
            // a cold device. Bound the whole handshake, not each output line.
            val uciDeadline = android.os.SystemClock.elapsedRealtime() + 15000
            var reportedName: String? = null
            var line = readLineWithTimeout(15000)
            while (line != null && line != "uciok") {
                parseUciEngineName(line)?.let { reportedName = it }
                val remaining = uciDeadline - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                line = readLineWithTimeout(remaining)
            }
            if (line != "uciok") {
                android.util.Log.e("StockfishEngine", "Engine did not complete the UCI handshake")
                _isReady.value = false
                return
            }
            _engineName.value = reportedName

            // Send isready and wait for readyok (with timeout)
            sendCommand("isready")
            var readyAttempts = 0
            line = readLineWithTimeout(10000)
            while (line != null && line != "readyok" && readyAttempts < 50) {
                line = readLineWithTimeout(3000)
                readyAttempts++
            }

            _isReady.value = line == "readyok"

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            _isReady.value = false
        }
    }

    private fun sendCommand(command: String) {
        try {
            processWriter?.write(command)
            processWriter?.newLine()
            processWriter?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Start a new game (clears hash table and resets engine state).
     * Should be called before analyzing a new game or between analysis rounds.
     */
    fun newGame() {
        if (!_isReady.value) {
            android.util.Log.e("StockfishEngine", "newGame called but engine not ready!")
            return
        }
        sendCommand("stop")
        sendCommand("ucinewgame")
        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "New game started (hash cleared)")
    }

    fun configure(threads: Int, hashMb: Int, multiPv: Int, useNnue: Boolean = true) {
        if (!_isReady.value) {
            android.util.Log.e("StockfishEngine", "configure called but engine not ready!")
            return
        }

        // Stop any ongoing analysis first
        sendCommand("stop")

        // Cap hash size to prevent memory-related crashes
        // On mobile devices, large hash tables can cause the process to die
        val safeHashMb = hashMb.coerceIn(1, MAX_SAFE_HASH_MB)
        val availableCpus = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val safeThreads = threads.coerceIn(1, minOf(MAX_SAFE_THREADS, availableCpus))
        if (safeHashMb != hashMb || safeThreads != threads) {
            android.util.Log.w("StockfishEngine", "Settings capped for stability: Hash ${hashMb}→${safeHashMb}MB, Threads ${threads}→${safeThreads}")
        }

        sendCommand("setoption name Threads value $safeThreads")
        sendCommand("setoption name Hash value $safeHashMb")
        sendCommand("setoption name MultiPV value $multiPv")
        sendCommand("setoption name Use NNUE value $useNnue")

        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "Configured: Threads=$safeThreads, Hash=$safeHashMb, MultiPV=$multiPv, NNUE=$useNnue")
        // Note: The analysis job will send "isready" and wait for "readyok" before starting
    }

    /**
     * Sends "isready" and reads until "readyok", discarding leftover info/bestmove lines.
     * Uses one deadline for the handshake, including any leftover output.
     * Must be called from a coroutine context (checks isActive).
     */
    private suspend fun CoroutineScope.waitForEngineReady(caller: String): Boolean {
        sendCommand("isready")
        val deadline = android.os.SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (isActive) {
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            val line = readLineWithTimeout(remaining) ?: break
            if (line == "readyok") return true
        }
        currentCoroutineContext().ensureActive()
        val alive = process?.isAlive == true
        android.util.Log.e("StockfishEngine", "$caller: ${if (alive) "Timed out waiting for readyok" else "Engine closed its output"}")
        _isReady.value = false
        return false
    }

    /**
     * Reads analysis output lines until bestmove, calling parseInfoLine() for info lines.
     * Returns the number of lines read and the final bestmove, if the search completed.
     * Must be called from a coroutine context (checks isActive).
     */
    private data class SearchCompletion(val linesRead: Int, val bestMove: String? = null)

    private suspend fun CoroutineScope.readAnalysisOutput(
        caller: String,
        fen: String,
        onExactInfo: ((AnalysisResult, PvLine) -> Unit)? = null
    ): SearchCompletion {
        var linesRead = 0
        var idlePolls = 0
        val maxIdlePolls = 10
        while (isActive) {
            val line = readLineWithTimeout(3000)
            if (line == null) {
                idlePolls++
                if (idlePolls >= maxIdlePolls) {
                    val isAlive = try { process?.isAlive == true } catch (e: Exception) { false }
                    if (!isAlive) {
                        android.util.Log.e("StockfishEngine", "$caller: engine died during analysis")
                        _isReady.value = false
                    } else {
                        android.util.Log.w("StockfishEngine", "$caller: timed out waiting for engine output")
                    }
                    break
                }
                continue
            }
            idlePolls = 0
            linesRead++
            when {
                line.startsWith("info depth") && line.contains("score") -> {
                    val parsed = parseInfoLine(line, fen)
                    if (parsed != null && !line.contains(" lowerbound") && !line.contains(" upperbound")) {
                        _analysisResult.value?.let { onExactInfo?.invoke(it, parsed) }
                    }
                }
                line.startsWith("bestmove") -> {
                    return SearchCompletion(linesRead, line.split(' ').getOrNull(1))
                }
            }
        }

        currentCoroutineContext().ensureActive()
        return SearchCompletion(linesRead)
    }

    private suspend fun readLineWithTimeout(timeoutMs: Long): String? {
        val reader = processReader ?: return null
        // runInterruptible makes the blocking readLine() cancellable: cooperative
        // coroutine cancellation triggers Thread.interrupt on the IO worker, which
        // the FileInputStream under a Process stream surfaces as
        // InterruptedIOException. withTimeoutOrNull provides the timeout bound.
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                try {
                    reader.readLine()
                } catch (_: java.io.InterruptedIOException) {
                    null
                }
            }
        }
    }

    fun analyze(fen: String, depth: Int = 16) {
        startAnalysis("analyze", fen, "go depth $depth")
    }

    fun analyzeWithTime(fen: String, timeMs: Int) {
        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "analyzeWithTime: starting analysis for ${timeMs}ms")
        startAnalysis("analyzeWithTime", fen, "go movetime $timeMs") { linesRead ->
            if (linesRead < 3) {
                android.util.Log.e("StockfishEngine", "analyzeWithTime: analysis ended early, only $linesRead lines read")
            }
        }
    }

    /** Evaluate one legal root move, retaining the original side-to-move/mate perspective.
     * Used sequentially by the dedicated AI moves engine; never returns an unfinished score.
     */
    suspend fun evaluateMove(fen: String, move: String, timeMs: Int): AnalysisResult = withContext(Dispatchers.IO) {
        require(Regex("[a-h][1-8][a-h][1-8][qrbn]?").matches(move))
        require(timeMs > 0)
        check(_isReady.value) { "Stockfish is not ready." }
        analysisJob?.cancelAndJoin()
        analysisMutex.withLock {
            try {
                kotlinx.coroutines.withTimeout(timeMs.toLong() + READY_TIMEOUT_MS + 5000) {
                    sendCommand("stop")
                    synchronized(pvLinesLock) {
                        pvLines.clear()
                        currentNodes = 0
                        currentNps = 0
                        _analysisResult.value = null
                    }
                    check(waitForEngineReady("evaluateMove")) { "Stockfish did not become ready." }
                    sendCommand("position fen $fen")
                    sendCommand("go movetime $timeMs searchmoves $move")
                    val completed = readAnalysisOutput("evaluateMove", fen)
                    val result = _analysisResult.value
                    check(completed.bestMove == move && result?.fen == fen && result.bestMove == move) {
                        "Stockfish did not finish evaluating $move."
                    }
                    checkNotNull(result)
                }
            } finally {
                sendCommand("stop")
            }
        }
    }

    /** A finished MultiPV search, using only a full iteration at a common depth. */
    suspend fun evaluateLines(fen: String, lineCount: Int, timeMs: Int): AnalysisResult = withContext(Dispatchers.IO) {
        require(lineCount in 1..32 && timeMs > 0)
        check(_isReady.value) { "Stockfish is not ready." }
        analysisJob?.cancelAndJoin()
        analysisMutex.withLock {
            try {
                kotlinx.coroutines.withTimeout(timeMs.toLong() + READY_TIMEOUT_MS + 5000) {
                    sendCommand("stop")
                    sendCommand("setoption name MultiPV value $lineCount")
                    synchronized(pvLinesLock) {
                        pvLines.clear()
                        currentNodes = 0
                        currentNps = 0
                        _analysisResult.value = null
                    }
                    check(waitForEngineReady("evaluateLines")) { "Stockfish did not become ready." }
                    val completedIteration = CompletedPvIteration(lineCount)
                    sendCommand("position fen $fen")
                    sendCommand("go movetime $timeMs")
                    val completed = readAnalysisOutput("evaluateLines", fen, completedIteration::record)
                    check(!completed.bestMove.isNullOrBlank() && completed.bestMove !in listOf("(none)", "0000")) {
                        "Stockfish did not finish the engine lines search."
                    }
                    checkNotNull(completedIteration.result) {
                        "Stockfish did not finish all $lineCount lines. Increase the time per position and try again."
                    }
                }
            } finally {
                sendCommand("stop")
            }
        }
    }

    /**
     * Common analysis launcher used by both analyze() and analyzeWithTime().
     * Handles job cancellation, mutex locking, engine readiness, position setup,
     * output reading, and exception handling.
     * The optional onComplete callback receives the number of lines read.
     */
    private fun startAnalysis(
        caller: String,
        fen: String,
        goCommand: String,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (!_isReady.value) {
            android.util.Log.e("StockfishEngine", "$caller: engine not ready, skipping")
            return
        }

        val previousJob = analysisJob
        analysisJob = scope.launch {
            // Wait for the previous analysis coroutine to fully unwind before we
            // acquire the mutex. Without the join, the fair mutex would hand us
            // the lock eventually, but we'd race with the old coroutine's catch/
            // cleanup and could send a "stop" in parallel with it.
            previousJob?.cancelAndJoin()
            analysisMutex.withLock {
                try {
                    // Stop any ongoing analysis
                    sendCommand("stop")

                    // Clear previous lines and reset result (synchronized for thread safety)
                    synchronized(pvLinesLock) {
                        pvLines.clear()
                        currentNodes = 0
                        currentNps = 0
                        _analysisResult.value = null
                    }

                    // Ensure engine is ready (waits for any pending commands to complete)
                    if (!waitForEngineReady(caller)) return@withLock

                    // Set position and start analysis
                    sendCommand("position fen $fen")
                    sendCommand(goCommand)

                    // Read analysis output
                    val completed = readAnalysisOutput(caller, fen)
                    onComplete?.invoke(completed.linesRead)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        android.util.Log.e("StockfishEngine", "$caller: exception: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun parseInfoLine(line: String, fen: String): PvLine? {
        try {
            // Extract depth
            val depthMatch = Regex("depth (\\d+)").find(line)
            val depth = depthMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // Extract nodes
            val nodesMatch = Regex("nodes (\\d+)").find(line)
            val nodes = nodesMatch?.groupValues?.get(1)?.toLongOrNull() ?: currentNodes
            currentNodes = nodes

            // Extract nps (nodes per second)
            val npsMatch = Regex("nps (\\d+)").find(line)
            val nps = npsMatch?.groupValues?.get(1)?.toLongOrNull() ?: currentNps
            currentNps = nps

            // Extract multipv (defaults to 1)
            val multipvMatch = Regex("multipv (\\d+)").find(line)
            val multipv = multipvMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

            // Extract score
            var score = 0f
            var isMate = false
            var mateIn = 0

            val mateMatch = Regex("score mate (-?\\d+)").find(line)
            val cpMatch = Regex("score cp (-?\\d+)").find(line)

            if (mateMatch != null) {
                isMate = true
                mateIn = mateMatch.groupValues[1].toIntOrNull() ?: 0
                score = if (mateIn > 0) 100f else -100f
            } else if (cpMatch != null) {
                score = (cpMatch.groupValues[1].toIntOrNull() ?: 0) / 100f
            }

            // Extract PV: everything after the literal " pv " token. The UCI spec
            // places pv last in an info line so this is safe even if future Stockfish
            // builds reorder earlier fields; we anchor on the token position itself
            // rather than a greedy regex.
            val pvMarker = " pv "
            val pvIdx = line.indexOf(pvMarker)
            val pv = if (pvIdx >= 0) line.substring(pvIdx + pvMarker.length).trim() else ""

            // Store this PV line; cap tokens to keep memory bounded but keep enough
            // to cover long mating sequences (previous 8-move cap silently truncated
            // mate-in-N lines used by the analysis screen).
            val pvLine = PvLine(
                score = score,
                isMate = isMate,
                mateIn = mateIn,
                pv = pv.split(' ').filter { it.isNotEmpty() }.take(MAX_PV_TOKENS).joinToString(" "),
                multipv = multipv
            )

            // Update pvLines and emit result (synchronized for thread safety)
            synchronized(pvLinesLock) {
                pvLines[multipv] = pvLine
                val sortedLines = pvLines.values.sortedBy { it.multipv }
                _analysisResult.value = AnalysisResult(
                    depth = depth,
                    nodes = currentNodes,
                    nps = currentNps,
                    lines = sortedLines,
                    fen = fen
                )
            }
            return pvLine
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun stop() {
        analysisJob?.cancel()
        sendCommand("stop")
    }

    /**
     * Wait for the current analysis job to complete.
     * Returns true if completed, false if timed out or no job running.
     */
    suspend fun waitForCompletion(timeoutMs: Long = 5000): Boolean {
        val job = analysisJob ?: return true  // No job means "complete"
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                job.join()
                true
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w("StockfishEngine", "waitForCompletion: timeout after ${timeoutMs}ms")
            false
        }
    }

    /**
     * Cleans up the Stockfish process by sending quit, closing streams, and terminating.
     * In forceful mode (used by restart), waits for termination with timeout and
     * destroys forcibly if not terminated. In non-forceful mode (used by shutdown),
     * simply calls destroy().
     */
    private fun cleanupProcess(forceful: Boolean) {
        _engineName.value = null
        val oldProcess = process
        val oldWriter = processWriter
        val oldReader = processReader
        process = null
        processWriter = null
        processReader = null
        try {
            runCatching { oldWriter?.apply { write("quit"); newLine(); flush() } }
            runCatching { oldWriter?.close() }
            runCatching { oldReader?.close() }
            if (forceful) {
                val terminated = oldProcess?.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: true
                if (!terminated) {
                    oldProcess?.destroyForcibly()
                    oldProcess?.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            } else {
                oldProcess?.destroy()
            }
        } catch (e: Exception) {
            if (!forceful) {
                e.printStackTrace()
            }
            // In forceful mode, ignore errors during cleanup
        }
    }

    /**
     * Restart the Stockfish engine. Kills the current process and starts a new one.
     * Returns true if the restart was successful.
     */
    suspend fun restart(): Boolean = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            try {
                // Stop any ongoing analysis and wait for the coroutine to unwind so a
                // buffered info line from the old engine can't race past the clear.
                _isReady.value = false
                analysisJob?.cancelAndJoin()
                analysisJob = null

                // Kill the current process
                cleanupProcess(forceful = true)

                // Clear state under pvLinesLock, then publish null as the last write so
                // any straggler parseInfoLine callback can't overwrite the cleared result.
                process = null
                processWriter = null
                processReader = null
                synchronized(pvLinesLock) {
                    pvLines.clear()
                    currentNodes = 0
                    currentNps = 0
                    _analysisResult.value = null
                }

                // Delay to ensure process is fully terminated
                kotlinx.coroutines.delay(300)

                // Start new process
                startProcess()

                _isReady.value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun shutdown() {
        _isReady.value = false
        analysisJob?.cancel()
        _scope?.cancel()
        _scope = null  // Allow scope to be recreated if engine is restarted
        cleanupProcess(forceful = false)
    }
}
