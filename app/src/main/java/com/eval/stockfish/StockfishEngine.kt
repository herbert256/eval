package com.eval.stockfish

import android.content.Context
import com.eval.data.AppSignerTrust
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import java.io.File

class StockfishEngine(private val context: Context) {
    companion object {
        // Maximum safe hash table size in MB to prevent crashes on mobile devices
        const val MAX_SAFE_HASH_MB = 256
        // Maximum safe thread count for mobile devices
        const val MAX_SAFE_THREADS = 4
        internal const val READY_TIMEOUT_MS = 15000L
        // Starting the process includes loading the neural network, which on a slow device with
        // a cold file cache (first start after boot or an update) can take well over 15 s.
        internal const val STARTUP_TIMEOUT_MS = 30000L
        // A stopped search normally sends its bestmove within milliseconds.
        internal const val STOP_TIMEOUT_MS = 5000L
        // How long a timed search may overrun its limit before it is stopped explicitly.
        private const val TIME_LIMIT_GRACE_MS = 5000L
        // Stockfish 16 and later always use NNUE and no longer advertise this option.
        private const val NNUE_OPTION = "Use NNUE"

        /** Threads configure() really uses: the safe cap, limited to this device's CPUs. */
        fun maxUsableThreads(): Int =
            minOf(MAX_SAFE_THREADS, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))

        internal fun parseUciEngineName(line: String): String? = UciParsing.engineName(line)
    }

    // Replaced by restart() on another thread; commands and reads go through it.
    @Volatile private var session: UciSession? = null
    @Volatile private var options: Set<String> = emptySet()

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _engineName = MutableStateFlow<String?>(null)
    val engineName: StateFlow<String?> = _engineName

    /**
     * The last position the engine refused or died on, with a readable message.
     * Cleared when a later search produces output. Restarting does not clear it.
     */
    private val _lastError = MutableStateFlow<EngineError?>(null)
    val lastError: StateFlow<EngineError?> = _lastError

    /** Whether the running engine advertises "Use NNUE" (Stockfish 15.1 and older). */
    private val _supportsNnueToggle = MutableStateFlow(false)
    val supportsNnueToggle: StateFlow<Boolean> = _supportsNnueToggle

    @Volatile private var analysisJob: Job? = null
    // Scope is created lazily and can be recreated after shutdown
    private var _scope: CoroutineScope? = null
    private val scope: CoroutineScope
        get() = _scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob()).also { _scope = it }
    // Mutex to ensure only one analysis runs at a time
    private val analysisMutex = kotlinx.coroutines.sync.Mutex()
    private val lifecycleMutex = kotlinx.coroutines.sync.Mutex()
    // Guards publishing results against a concurrent restart or shutdown
    private val pvLinesLock = Any()

    private var stockfishPath: String? = null

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

    /** Whether the running engine advertised [name] during its UCI handshake (case-insensitive). */
    fun supportsOption(name: String): Boolean = options.contains(name)

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            try {
                // Use system-installed Stockfish (com.stockfish141 package)
                val candidates = findSystemStockfish()
                if (candidates.isEmpty()) {
                    android.util.Log.e("StockfishEngine", "Stockfish Chess Engine app not installed")
                    return@withContext false
                }
                // A repeated initialize() replaces the running process instead of leaking it.
                analysisJob?.cancelAndJoin()
                detachSession()?.close(wait = true)
                startFirstWorking(candidates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Finds Stockfish binaries in the system-installed com.stockfish141 package,
     * best candidate first. Returns an empty list if none is found.
     */
    private fun findSystemStockfish(): List<String> {
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
                }.sortedByDescending { it.name }.filter { it.canExecute() }
                if (candidates.isNotEmpty()) {
                    android.util.Log.i("StockfishEngine", "Found Stockfish binaries: ${candidates.map { it.name }}")
                    return candidates.map { it.absolutePath }
                }
                // Log all files for debugging if no match found
                android.util.Log.w("StockfishEngine", "No Stockfish binary found in $nativeLibDir. Files: ${allFiles.map { it.name }}")
            }
            emptyList()
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "System Stockfish package not installed")
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("StockfishEngine", "Error finding system Stockfish: ${e.message}")
            emptyList()
        }
    }

    // True when the installed com.stockfish141 is signed by someone else than the signer Eval trusted.
    private val _signerChanged = MutableStateFlow(false)
    val signerChanged: StateFlow<Boolean> = _signerChanged

    /** Why [initialize] returned false, for callers that show it to the user. */
    fun unavailableMessage(): String =
        if (_signerChanged.value) "The Stockfish app is signed by a different developer than before. Confirm it in Eval before it is used."
        else "Stockfish is unavailable. Install or restart Stockfish and try again."

    /** Starts the first binary that completes the UCI handshake; the previous working one is tried first. */
    private suspend fun startFirstWorking(candidates: List<String>): Boolean {
        // Every start and restart checks the signer (its binary runs as Eval), not only the first
        // start of the app: a package replaced meanwhile is not run until the user confirms it.
        if (AppSignerTrust.create(context).check(AppSignerTrust.STOCKFISH_PACKAGE) == AppSignerTrust.Status.CHANGED) {
            android.util.Log.w("StockfishEngine", "com.stockfish141 is signed by a different developer; not starting it")
            _signerChanged.value = true
            return false
        }
        _signerChanged.value = false
        val ordered = (listOfNotNull(stockfishPath) + candidates).distinct()
        for (path in ordered) {
            if (startProcess(path)) {
                android.util.Log.i("StockfishEngine", "Using system Stockfish: $path")
                stockfishPath = path
                return true
            }
        }
        return false
    }

    private suspend fun startProcess(path: String): Boolean {
        _engineName.value = null
        var started: UciSession? = null
        try {
            // Keep CPU-bound engine workers below the UI's scheduling priority.
            // In particular, several normal-priority workers can starve input
            // delivery on devices with few available CPUs.
            val nice = File("/system/bin/nice")
            val command = if (nice.canExecute()) listOf(nice.path, "-n", "10", path) else listOf(path)
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val s = UciSession.start(process, ::onSessionEnded)
            started = s

            // Loading the engine's neural network can take several seconds on
            // a cold device. Bound the whole handshake, not each output line.
            val handshake = s.handshake(STARTUP_TIMEOUT_MS)
            if (handshake == null || !s.awaitReady(STARTUP_TIMEOUT_MS)) {
                android.util.Log.e("StockfishEngine", "Engine did not complete the UCI handshake: $path")
                s.close(wait = true)
                _isReady.value = false
                return false
            }
            options = handshake.options
            _supportsNnueToggle.value = NNUE_OPTION in handshake.options
            _engineName.value = handshake.name
            session = s
            _isReady.value = !s.ended
            return _isReady.value
        } catch (e: CancellationException) {
            started?.takeIf { it !== session }?.close(wait = false)
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            started?.takeIf { it !== session }?.close(wait = true)
            _isReady.value = false
            return false
        }
    }

    /** Called on the reader thread when an engine's output ends without close(). */
    private fun onSessionEnded(ended: UciSession) {
        if (session !== ended) return
        android.util.Log.e("StockfishEngine", "Engine closed its output")
        _isReady.value = false
    }

    private fun sendCommand(command: String) {
        session?.send(command)
    }

    /** Sends a setoption only for options the engine advertised. */
    private fun setOption(name: String, value: Any) {
        if (supportsOption(name)) {
            sendCommand("setoption name $name value $value")
        } else if (com.eval.BuildConfig.DEBUG) {
            android.util.Log.d("StockfishEngine", "Engine has no option \"$name\"; not sent")
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
        val safeThreads = threads.coerceIn(1, maxUsableThreads())
        if (safeHashMb != hashMb || safeThreads != threads) {
            android.util.Log.w("StockfishEngine", "Settings capped for stability: Hash ${hashMb}→${safeHashMb}MB, Threads ${threads}→${safeThreads}")
        }

        setOption("Threads", safeThreads)
        setOption("Hash", safeHashMb)
        setOption("MultiPV", multiPv.coerceAtLeast(1))
        setOption(NNUE_OPTION, useNnue)

        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "Configured: Threads=$safeThreads, Hash=$safeHashMb, MultiPV=$multiPv, NNUE=$useNnue")
        // Note: each search waits for the previous one to end and for "readyok" before starting
    }

    private class SearchCompletion(
        val linesRead: Int,
        val bestMove: String?,
        val result: AnalysisResult?,
        val bestLineIsExact: Boolean
    )

    /**
     * Runs one search; callers hold analysisMutex. The previous search is stopped and
     * its bestmove awaited before "isready", "position" and "go", and only this
     * search's output is parsed, so a previous position's lines or bestmove are never
     * attributed to [fen]. Returns null when the search did not complete.
     */
    private suspend fun runSearch(
        caller: String,
        fen: String,
        history: EngineHistory?,
        goCommand: String,
        timeLimitMs: Long?,
        setup: List<String> = emptyList(),
        onExactInfo: ((AnalysisResult, PvLine) -> Unit)? = null
    ): SearchCompletion? {
        synchronized(pvLinesLock) { _analysisResult.value = null }
        val s = session
        if (s == null) {
            android.util.Log.e("StockfishEngine", "$caller: engine is not running")
            _isReady.value = false
            return null
        }
        val position = UciParsing.positionCommand(fen, history)
        if (position == null) {
            android.util.Log.e("StockfishEngine", "$caller: not a valid FEN: $fen")
            _lastError.value = EngineError(fen, "This position cannot be sent to Stockfish.")
            return null
        }
        if (history != null && history.uciMoves.isNotEmpty() && !position.usesHistory) {
            android.util.Log.w("StockfishEngine", "$caller: move history does not lead to the position; searching without it")
        }
        val lines = PvAccumulator(fen)
        val outcome = s.search(position.command, goCommand, timeLimitMs?.plus(TIME_LIMIT_GRACE_MS),
            STOP_TIMEOUT_MS, READY_TIMEOUT_MS, setup) { text ->
            val recorded = lines.record(text) ?: return@search
            if (_lastError.value != null) _lastError.value = null
            val result = lines.result ?: return@search
            synchronized(pvLinesLock) {
                if (session === s) _analysisResult.value = result
            }
            if (recorded.exact) onExactInfo?.invoke(result, recorded.line)
        }
        // A cancelled search, or one whose process restart()/shutdown() ended, reports nothing.
        currentCoroutineContext().ensureActive()
        if (session !== s) return null
        return when (outcome) {
            is SearchOutcome.Completed -> {
                _lastError.value = null
                SearchCompletion(outcome.linesRead, outcome.bestMove, lines.result, lines.bestLineIsExact)
            }
            is SearchOutcome.Rejected -> {
                android.util.Log.e("StockfishEngine", "$caller: ${outcome.message} ($fen)")
                _lastError.value = EngineError(fen, outcome.message)
                if (s.ended && session === s) _isReady.value = false
                null
            }
            is SearchOutcome.Died -> {
                android.util.Log.e("StockfishEngine", "$caller: engine died during analysis")
                if (session === s) _isReady.value = false
                if (!outcome.outputSeen) {
                    _lastError.value = EngineError(fen, "Stockfish stopped while analysing this position.")
                }
                null
            }
            SearchOutcome.StillSearching, SearchOutcome.NotReady, SearchOutcome.TimedOut -> {
                val reason = when (outcome) {
                    SearchOutcome.StillSearching -> "previous search did not stop"
                    SearchOutcome.NotReady -> "Timed out waiting for readyok"
                    else -> "engine ignored stop after the time limit"
                }
                android.util.Log.e("StockfishEngine", "$caller: $reason")
                if (session === s) _isReady.value = false
                null
            }
        }
    }

    /**
     * Analyse [fen] to [depth]. With [history] (moves from its start position to [fen])
     * the engine sees repetitions; results are still tagged with [fen].
     */
    fun analyze(fen: String, depth: Int = 16, history: EngineHistory? = null) {
        startAnalysis("analyze", fen, history, "go depth $depth", timeLimitMs = null)
    }

    fun analyzeWithTime(fen: String, timeMs: Int, history: EngineHistory? = null) {
        if (com.eval.BuildConfig.DEBUG) android.util.Log.d("StockfishEngine", "analyzeWithTime: starting analysis for ${timeMs}ms")
        startAnalysis("analyzeWithTime", fen, history, "go movetime $timeMs", timeMs.toLong()) { linesRead ->
            if (linesRead < 3) {
                android.util.Log.e("StockfishEngine", "analyzeWithTime: analysis ended early, only $linesRead lines read")
            }
        }
    }

    private fun failureFor(fen: String, fallback: String): String =
        _lastError.value?.takeIf { it.fen == fen }?.message ?: fallback

    /** Evaluate one legal root move, retaining the original side-to-move/mate perspective.
     * Used sequentially by the dedicated AI moves engine; never returns an unfinished
     * (bound) score.
     */
    suspend fun evaluateMove(
        fen: String, move: String, timeMs: Int, history: EngineHistory? = null
    ): AnalysisResult = withContext(Dispatchers.IO) {
        require(UciParsing.UCI_MOVE.matches(move))
        require(timeMs > 0)
        check(_isReady.value) { "Stockfish is not ready." }
        analysisJob?.cancelAndJoin()
        analysisMutex.withLock {
            try {
                kotlinx.coroutines.withTimeout(timeMs.toLong() + READY_TIMEOUT_MS + STOP_TIMEOUT_MS + TIME_LIMIT_GRACE_MS) {
                    val completed = runSearch("evaluateMove", fen, history, "go movetime $timeMs searchmoves $move", timeMs.toLong())
                    val result = completed?.result
                    check(completed != null && completed.bestLineIsExact && completed.bestMove == move &&
                        result?.fen == fen && result.bestMove == move) {
                        failureFor(fen, "Stockfish did not finish evaluating $move.")
                    }
                    checkNotNull(result)
                }
            } finally {
                sendCommand("stop")
            }
        }
    }

    /** A finished MultiPV search, using only a full iteration of exact lines at a common depth. */
    suspend fun evaluateLines(
        fen: String, lineCount: Int, timeMs: Int,
        history: EngineHistory? = null,
        onIteration: (AnalysisResult) -> Unit = {}
    ): AnalysisResult = withContext(Dispatchers.IO) {
        require(lineCount in 1..32 && timeMs > 0)
        check(_isReady.value) { "Stockfish is not ready." }
        analysisJob?.cancelAndJoin()
        analysisMutex.withLock {
            try {
                kotlinx.coroutines.withTimeout(timeMs.toLong() + READY_TIMEOUT_MS + STOP_TIMEOUT_MS + TIME_LIMIT_GRACE_MS) {
                    val completedIteration = CompletedPvIteration(lineCount)
                    val setup = if (supportsOption("MultiPV")) listOf("setoption name MultiPV value $lineCount") else emptyList()
                    val completed = runSearch("evaluateLines", fen, history, "go movetime $timeMs", timeMs.toLong(), setup) { info, line ->
                        val previous = completedIteration.result
                        completedIteration.record(info, line)
                        completedIteration.result?.takeIf { it !== previous }?.let(onIteration)
                    }
                    check(completed != null && !completed.bestMove.isNullOrBlank() && completed.bestMove !in listOf("(none)", "0000")) {
                        failureFor(fen, "Stockfish did not finish the engine lines search.")
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
     * Cancels the previous analysis coroutine, then runs the search under the mutex.
     * The optional onComplete callback receives the number of lines read.
     */
    private fun startAnalysis(
        caller: String,
        fen: String,
        history: EngineHistory?,
        goCommand: String,
        timeLimitMs: Long?,
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
                    val completed = runSearch(caller, fen, history, goCommand, timeLimitMs)
                    onComplete?.invoke(completed?.linesRead ?: 0)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        android.util.Log.e("StockfishEngine", "$caller: exception: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
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

    /** Detaches the current process so results and state changes from it are ignored. */
    private fun detachSession(): UciSession? {
        _engineName.value = null
        val old = session
        // Clear under pvLinesLock and publish null as the last write so a straggler
        // result from the old process can't overwrite the cleared result.
        synchronized(pvLinesLock) {
            session = null
            _analysisResult.value = null
        }
        return old
    }

    /**
     * Restart the Stockfish engine. Kills the current process and starts a new one.
     * Returns true if the restart was successful.
     */
    suspend fun restart(): Boolean = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            try {
                // Stop any ongoing analysis and wait for the coroutine to unwind.
                _isReady.value = false
                analysisJob?.cancelAndJoin()
                analysisJob = null

                // Terminate the current process first, so its reader thread sees EOF.
                detachSession()?.close(wait = true)

                // Delay to ensure process is fully terminated
                kotlinx.coroutines.delay(300)

                // Start new process, trying the other installed binaries if it fails
                startFirstWorking(findSystemStockfish())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /** Safe to call on the main thread: the process is terminated and released without blocking. */
    fun shutdown() {
        _isReady.value = false
        analysisJob?.cancel()
        _scope?.cancel()
        _scope = null  // Allow scope to be recreated if engine is restarted
        detachSession()?.close(wait = false)
    }
}
