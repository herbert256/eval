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
    val lines: List<PvLine>
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
        // Maximum number of moves to display in the principal variation line
        private const val MAX_PV_MOVES_DISPLAY = 8
        // Maximum safe hash table size in MB to prevent crashes on mobile devices
        private const val MAX_SAFE_HASH_MB = 256
        // Maximum safe thread count for mobile devices
        private const val MAX_SAFE_THREADS = 4
    }

    private var process: Process? = null
    private var processWriter: BufferedWriter? = null
    private var processReader: BufferedReader? = null

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private var analysisJob: Job? = null
    // Scope is created lazily and can be recreated after shutdown
    private var _scope: CoroutineScope? = null
    private val scope: CoroutineScope
        get() = _scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob()).also { _scope = it }
    // Mutex to ensure only one analysis runs at a time
    private val analysisMutex = kotlinx.coroutines.sync.Mutex()
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
        try {
            // Use system-installed Stockfish (com.stockfish141 package)
            val systemStockfishPath = findSystemStockfish()
            if (systemStockfishPath != null) {
                android.util.Log.i("StockfishEngine", "Using system Stockfish: $systemStockfishPath")
                stockfishPath = systemStockfishPath
            } else {
                android.util.Log.e("StockfishEngine", "Stockfish 17.1 Chess Engine app not installed")
                return@withContext false
            }

            // Start the process
            startProcess()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

            // Look for the Stockfish binary (lib_sf171.so for Stockfish 17.1)
            val libDir = File(nativeLibDir)
            if (libDir.exists() && libDir.isDirectory) {
                // Prefer explicit Stockfish library names to avoid false positives.
                val candidates = libDir.listFiles()?.filter { file ->
                    val lower = file.name.lowercase()
                    lower == "lib_sf171.so" ||
                        lower.contains("stockfish") ||
                        lower.contains("sf17")
                }.orEmpty()
                val stockfishFile = candidates.firstOrNull()
                if (stockfishFile != null && stockfishFile.exists() && stockfishFile.canExecute()) {
                    return stockfishFile.absolutePath
                }
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
        val path = stockfishPath ?: return

        try {
            process = ProcessBuilder(path)
                .redirectErrorStream(true)
                .start()

            val p = process ?: throw IllegalStateException("Stockfish process creation failed")
            processWriter = BufferedWriter(OutputStreamWriter(p.outputStream))
            processReader = BufferedReader(InputStreamReader(p.inputStream))

            // Initialize UCI
            sendCommand("uci")

            // Read until uciok
            var line = readLineWithTimeout(5000)
            while (line != null && line != "uciok") {
                line = readLineWithTimeout(5000)
            }
            if (line != "uciok") {
                _isReady.value = false
                return
            }

            // Send isready and wait for readyok (with timeout)
            sendCommand("isready")
            var readyAttempts = 0
            line = readLineWithTimeout(3000)
            while (line != null && line != "readyok" && readyAttempts < 50) {
                line = readLineWithTimeout(3000)
                readyAttempts++
            }

            _isReady.value = line == "readyok"

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
        val safeHashMb = hashMb.coerceAtMost(MAX_SAFE_HASH_MB)
        val safeThreads = threads.coerceAtMost(MAX_SAFE_THREADS)
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
     * Returns true if readyok was received, false otherwise (null read or max attempts exceeded).
     * Must be called from a coroutine context (checks isActive).
     */
    private suspend fun CoroutineScope.waitForEngineReady(caller: String, maxAttempts: Int = 50): Boolean {
        sendCommand("isready")

        var line = readLineWithTimeout(3000)
        var readyAttempts = 0
        while (line != null && line != "readyok" && isActive && readyAttempts < maxAttempts) {
            // Skip info lines from previous analysis that might still be in buffer
            if (line.startsWith("info ") || line.startsWith("bestmove")) {
                // Discard leftover output
            }
            line = readLineWithTimeout(3000)
            readyAttempts++
        }

        if (!isActive) {
            if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "$caller: cancelled before analysis")
            return false
        }
        if (line == null) {
            val isAlive = try { process?.isAlive == true } catch (e: Exception) { false }
            android.util.Log.e("StockfishEngine", "$caller: processReader returned null (EOF?), process.isAlive=$isAlive")
            _isReady.value = false
            return false
        }
        if (readyAttempts >= maxAttempts) {
            android.util.Log.e("StockfishEngine", "$caller: timeout waiting for readyok after $readyAttempts attempts")
            return false
        }

        return true
    }

    /**
     * Reads analysis output lines until bestmove, calling parseInfoLine() for info lines.
     * Returns the number of lines read.
     * Must be called from a coroutine context (checks isActive).
     */
    private suspend fun CoroutineScope.readAnalysisOutput(caller: String): Int {
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
                    parseInfoLine(line)
                }
                line.startsWith("bestmove") -> {
                    break
                }
            }
        }

        return linesRead
    }

    private suspend fun readLineWithTimeout(timeoutMs: Long): String? {
        val reader = processReader ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return null
            if (reader.ready()) {
                return reader.readLine()
            }
            delay(20)
        }
        return null
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

        analysisJob?.cancel()
        analysisJob = scope.launch {
            analysisMutex.withLock {
                try {
                    // Stop any ongoing analysis
                    sendCommand("stop")

                    // Clear previous lines and reset result (synchronized for thread safety)
                    synchronized(pvLinesLock) {
                        pvLines.clear()
                    }
                    currentNodes = 0
                    _analysisResult.value = null

                    // Ensure engine is ready (waits for any pending commands to complete)
                    if (!waitForEngineReady(caller)) return@withLock

                    // Set position and start analysis
                    sendCommand("position fen $fen")
                    sendCommand(goCommand)

                    // Read analysis output
                    val linesRead = readAnalysisOutput(caller)
                    onComplete?.invoke(linesRead)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        android.util.Log.e("StockfishEngine", "$caller: exception: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun parseInfoLine(line: String) {
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

            // Extract PV
            val pvMatch = Regex(" pv (.+)$").find(line)
            val pv = pvMatch?.groupValues?.get(1) ?: ""

            // Store this PV line
            val pvLine = PvLine(
                score = score,
                isMate = isMate,
                mateIn = mateIn,
                pv = pv.split(" ").take(MAX_PV_MOVES_DISPLAY).joinToString(" "),
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
                    lines = sortedLines
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        try {
            sendCommand("quit")
            processWriter?.close()
            processReader?.close()
            if (forceful) {
                val terminated = process?.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: true
                if (!terminated) {
                    process?.destroyForcibly()
                    process?.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            } else {
                process?.destroy()
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
        try {
            // Stop any ongoing analysis
            analysisJob?.cancel()
            _isReady.value = false
            _analysisResult.value = null

            // Kill the current process
            cleanupProcess(forceful = true)

            // Clear state
            process = null
            processWriter = null
            processReader = null
            synchronized(pvLinesLock) {
                pvLines.clear()
            }
            currentNodes = 0

            // Delay to ensure process is fully terminated
            kotlinx.coroutines.delay(300)

            // Start new process
            startProcess()

            _isReady.value
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shutdown() {
        analysisJob?.cancel()
        _scope?.cancel()
        _scope = null  // Allow scope to be recreated if engine is restarted
        cleanupProcess(forceful = false)
    }
}
