package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.eval.stockfish.StockfishEngine

/**
 * Reusable stepper component for settings.
 */
@Composable
private fun SettingStepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.SubtleText,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.DarkGray, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDecrement,
                enabled = canDecrement,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color(0xFF444444)
                )
            ) {
                Text("−", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onIncrement,
                enabled = canIncrement,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color(0xFF444444)
                )
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Expansion is local to this visit; saved engine settings live in the screen above the cards. */
@Composable
private fun CollapsibleStockfishCard(
    title: String,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse $title" else "Expand $title",
                    onClick = { expanded = !expanded }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(if (expanded) "▾" else "▸", fontSize = 20.sp, color = Color.White,
                modifier = Modifier.clearAndSetSemantics { })
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content
            )
        }
    }
}

/** Stepper values. Thread and hash lists stop at what the engine will actually use. */
internal object StockfishStepperOptions {
    val aiEngineSeconds = listOf(0.25f, 0.5f, 1f, 2f, 5f, 10f, 30f, 60f)
    val aiSeconds = listOf(0.05f, 0.10f, 0.25f, 0.50f, 1f, 2f, 5f, 10f)
    val aiHash = listOf(8, 16, 32, 64, 128, 256)
    val previewSeconds = listOf(0.01f, 0.05f, 0.10f, 0.25f, 0.50f)
    val previewHash = listOf(8, 16, 64)
    val analyseSeconds = listOf(0.50f, 0.75f, 1.00f, 1.50f, 2.00f, 2.50f, 5.00f, 10.00f)
    val analyseHash = listOf(16, 64, 96, 128, 192, 256).filter { it <= StockfishEngine.MAX_SAFE_HASH_MB }
    val manualDepth = (16..64 step 2).toList()
    val manualHash = listOf(32, 64, 96, 128, 192, 256).filter { it <= StockfishEngine.MAX_SAFE_HASH_MB }
    val manualMultiPv = (1..32).toList()

    fun threads(limit: Int = Int.MAX_VALUE) = (1..minOf(limit, StockfishEngine.maxUsableThreads())).toList()
}

/** Stockfish settings for board analysis and AI handoffs. */
@Composable
fun StockfishSettingsScreen(
    stockfishSettings: StockfishSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (StockfishSettings) -> Unit,
    // Stockfish 16+ has no "Use NNUE" option; only show the toggles when the engine advertises it.
    showNnueToggles: Boolean = false
) {
    // Preview Stage state
    var previewSeconds by remember { mutableStateOf(stockfishSettings.previewStage.secondsForMove) }
    var previewThreads by remember { mutableStateOf(stockfishSettings.previewStage.threads) }
    var previewHash by remember { mutableStateOf(stockfishSettings.previewStage.hashMb) }
    var previewNnue by remember { mutableStateOf(stockfishSettings.previewStage.useNnue) }

    // Analyse Stage state
    var analyseSeconds by remember { mutableStateOf(stockfishSettings.analyseStage.secondsForMove) }
    var analyseThreads by remember { mutableStateOf(stockfishSettings.analyseStage.threads) }
    var analyseHash by remember { mutableStateOf(stockfishSettings.analyseStage.hashMb) }
    var analyseNnue by remember { mutableStateOf(stockfishSettings.analyseStage.useNnue) }

    // Manual Stage state
    var manualDepth by remember { mutableStateOf(stockfishSettings.manualStage.depth) }
    var manualThreads by remember { mutableStateOf(stockfishSettings.manualStage.threads) }
    var manualHash by remember { mutableStateOf(stockfishSettings.manualStage.hashMb) }
    var manualMultiPv by remember { mutableStateOf(stockfishSettings.manualStage.multiPv) }
    var manualNnue by remember { mutableStateOf(stockfishSettings.manualStage.useNnue) }

    var aiMoves by remember { mutableStateOf(stockfishSettings.movesListForAi) }
    var aiEngine by remember { mutableStateOf(stockfishSettings.engineMovesForAi) }
    val aiEngineSecondsOptions = StockfishStepperOptions.aiEngineSeconds
    val aiSecondsOptions = StockfishStepperOptions.aiSeconds
    val aiHashOptions = StockfishStepperOptions.aiHash

    val maxThreads = StockfishEngine.maxUsableThreads()
    val previewSecondsOptions = StockfishStepperOptions.previewSeconds
    val previewThreadsOptions = StockfishStepperOptions.threads(limit = 4)
    val previewHashOptions = StockfishStepperOptions.previewHash

    val analyseSecondsOptions = StockfishStepperOptions.analyseSeconds
    val analyseThreadsOptions = StockfishStepperOptions.threads()
    val analyseHashOptions = StockfishStepperOptions.analyseHash

    val manualDepthOptions = StockfishStepperOptions.manualDepth
    val manualThreadsOptions = StockfishStepperOptions.threads()
    val manualHashOptions = StockfishStepperOptions.manualHash
    val manualMultiPvOptions = StockfishStepperOptions.manualMultiPv

    fun saveAllSettings() {
        onSave(stockfishSettings.copy(
            movesListForAi = aiMoves,
            engineMovesForAi = aiEngine,
            previewStage = PreviewStageSettings(
                secondsForMove = previewSeconds,
                threads = previewThreads,
                hashMb = previewHash,
                useNnue = previewNnue
            ),
            analyseStage = AnalyseStageSettings(
                secondsForMove = analyseSeconds,
                threads = analyseThreads,
                hashMb = analyseHash,
                useNnue = analyseNnue
            ),
            manualStage = stockfishSettings.manualStage.copy(
                depth = manualDepth,
                threads = manualThreads,
                hashMb = manualHash,
                multiPv = manualMultiPv,
                useNnue = manualNnue
            )
        ))
    }

    // Helper functions for stepping through list options
    fun <T : Comparable<T>> stepInList(current: T, options: List<T>, delta: Int): T {
        val currentIndex = options.indexOf(current)
        // Values from older versions or imports can fall between options: step to the neighbour.
        if (currentIndex == -1) {
            return if (delta > 0) options.firstOrNull { it > current } ?: options.last()
            else options.lastOrNull { it < current } ?: options.first()
        }
        val newIndex = (currentIndex + delta).coerceIn(0, options.lastIndex)
        return options[newIndex]
    }

    EvalScreen(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = {
            EvalTitleBar(
                title = "Stockfish",
                onBackClick = onBackToSettings,
                onEvalClick = onBackToGame
            )
        }
    ) {

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ===== PREVIEW STAGE CARD =====
            CollapsibleStockfishCard(title = "Preview Stage") {
                // Seconds for move
                SettingStepper(
                    label = "Seconds for move",
                    value = String.format("%.2f s", previewSeconds),
                    onDecrement = {
                        previewSeconds = stepInList(previewSeconds, previewSecondsOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        previewSeconds = stepInList(previewSeconds, previewSecondsOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = previewSecondsOptions.any { it < previewSeconds },
                    canIncrement = previewSecondsOptions.any { it > previewSeconds }
                )

                // Number of threads
                SettingStepper(
                    label = "Number of threads",
                    value = minOf(previewThreads, maxThreads).toString(),
                    onDecrement = {
                        previewThreads = stepInList(minOf(previewThreads, maxThreads), previewThreadsOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        previewThreads = stepInList(minOf(previewThreads, maxThreads), previewThreadsOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = previewThreadsOptions.any { it < minOf(previewThreads, maxThreads) },
                    canIncrement = previewThreadsOptions.any { it > minOf(previewThreads, maxThreads) }
                )

                // Hash memory
                SettingStepper(
                    label = "Hash memory (MB)",
                    value = "${minOf(previewHash, StockfishEngine.MAX_SAFE_HASH_MB)} MB",
                    onDecrement = {
                        previewHash = stepInList(minOf(previewHash, StockfishEngine.MAX_SAFE_HASH_MB), previewHashOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        previewHash = stepInList(minOf(previewHash, StockfishEngine.MAX_SAFE_HASH_MB), previewHashOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = previewHashOptions.any { it < minOf(previewHash, StockfishEngine.MAX_SAFE_HASH_MB) },
                    canIncrement = previewHashOptions.any { it > minOf(previewHash, StockfishEngine.MAX_SAFE_HASH_MB) }
                )

                if (showNnueToggles) {
                    NnueToggleRow(checked = previewNnue) {
                        previewNnue = it
                        saveAllSettings()
                    }
                }
            }

            // ===== ANALYSE STAGE CARD =====
            CollapsibleStockfishCard(title = "Analyse Stage") {
                // Seconds for move
                SettingStepper(
                    label = "Seconds for move",
                    value = String.format("%.2f s", analyseSeconds),
                    onDecrement = {
                        analyseSeconds = stepInList(analyseSeconds, analyseSecondsOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        analyseSeconds = stepInList(analyseSeconds, analyseSecondsOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = analyseSecondsOptions.any { it < analyseSeconds },
                    canIncrement = analyseSecondsOptions.any { it > analyseSeconds }
                )

                // Number of threads
                SettingStepper(
                    label = "Number of threads",
                    value = minOf(analyseThreads, maxThreads).toString(),
                    onDecrement = {
                        analyseThreads = stepInList(minOf(analyseThreads, maxThreads), analyseThreadsOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        analyseThreads = stepInList(minOf(analyseThreads, maxThreads), analyseThreadsOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = analyseThreadsOptions.any { it < minOf(analyseThreads, maxThreads) },
                    canIncrement = analyseThreadsOptions.any { it > minOf(analyseThreads, maxThreads) }
                )

                // Hash memory
                SettingStepper(
                    label = "Hash memory (MB)",
                    value = "${minOf(analyseHash, StockfishEngine.MAX_SAFE_HASH_MB)} MB",
                    onDecrement = {
                        analyseHash = stepInList(minOf(analyseHash, StockfishEngine.MAX_SAFE_HASH_MB), analyseHashOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        analyseHash = stepInList(minOf(analyseHash, StockfishEngine.MAX_SAFE_HASH_MB), analyseHashOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = analyseHashOptions.any { it < minOf(analyseHash, StockfishEngine.MAX_SAFE_HASH_MB) },
                    canIncrement = analyseHashOptions.any { it > minOf(analyseHash, StockfishEngine.MAX_SAFE_HASH_MB) }
                )

                if (showNnueToggles) {
                    NnueToggleRow(checked = analyseNnue) {
                        analyseNnue = it
                        saveAllSettings()
                    }
                }
            }

            // ===== MANUAL STAGE CARD =====
            CollapsibleStockfishCard(title = "Manual Stage") {
                // Depth
                SettingStepper(
                    label = "Depth",
                    value = manualDepth.toString(),
                    onDecrement = {
                        manualDepth = stepInList(manualDepth, manualDepthOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        manualDepth = stepInList(manualDepth, manualDepthOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = manualDepthOptions.any { it < manualDepth },
                    canIncrement = manualDepthOptions.any { it > manualDepth }
                )

                // Number of threads
                SettingStepper(
                    label = "Number of threads",
                    value = minOf(manualThreads, maxThreads).toString(),
                    onDecrement = {
                        manualThreads = stepInList(minOf(manualThreads, maxThreads), manualThreadsOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        manualThreads = stepInList(minOf(manualThreads, maxThreads), manualThreadsOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = manualThreadsOptions.any { it < minOf(manualThreads, maxThreads) },
                    canIncrement = manualThreadsOptions.any { it > minOf(manualThreads, maxThreads) }
                )

                // Hash memory
                SettingStepper(
                    label = "Hash memory (MB)",
                    value = "${minOf(manualHash, StockfishEngine.MAX_SAFE_HASH_MB)} MB",
                    onDecrement = {
                        manualHash = stepInList(minOf(manualHash, StockfishEngine.MAX_SAFE_HASH_MB), manualHashOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        manualHash = stepInList(minOf(manualHash, StockfishEngine.MAX_SAFE_HASH_MB), manualHashOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = manualHashOptions.any { it < minOf(manualHash, StockfishEngine.MAX_SAFE_HASH_MB) },
                    canIncrement = manualHashOptions.any { it > minOf(manualHash, StockfishEngine.MAX_SAFE_HASH_MB) }
                )

                // MultiPV lines
                SettingStepper(
                    label = "MultiPV lines",
                    value = manualMultiPv.toString(),
                    onDecrement = {
                        manualMultiPv = stepInList(manualMultiPv, manualMultiPvOptions, -1)
                        saveAllSettings()
                    },
                    onIncrement = {
                        manualMultiPv = stepInList(manualMultiPv, manualMultiPvOptions, 1)
                        saveAllSettings()
                    },
                    canDecrement = manualMultiPvOptions.any { it < manualMultiPv },
                    canIncrement = manualMultiPvOptions.any { it > manualMultiPv }
                )

                if (showNnueToggles) {
                    NnueToggleRow(checked = manualNnue) {
                        manualNnue = it
                        saveAllSettings()
                    }
                }
            }

            CollapsibleStockfishCard(title = "Moves list for AI", spacing = 16.dp) {
                Text(
                    "Evaluate every legal move before sending a position to AI. More time per move gives deeper analysis and a longer wait.",
                    color = AppColors.SubtleText
                )
                SettingStepper(
                    label = "Seconds per move",
                    value = "${aiMoves.secondsForMove} s",
                    onDecrement = {
                        aiMoves = aiMoves.copy(secondsForMove = stepInList(aiMoves.secondsForMove, aiSecondsOptions, -1))
                        saveAllSettings()
                    },
                    onIncrement = {
                        aiMoves = aiMoves.copy(secondsForMove = stepInList(aiMoves.secondsForMove, aiSecondsOptions, 1))
                        saveAllSettings()
                    },
                    canDecrement = aiMoves.secondsForMove > aiSecondsOptions.first(),
                    canIncrement = aiMoves.secondsForMove < aiSecondsOptions.last()
                )
                SettingStepper(
                    label = "Number of threads",
                    value = aiMoves.threads.toString(),
                    onDecrement = { aiMoves = aiMoves.copy(threads = aiMoves.threads - 1); saveAllSettings() },
                    onIncrement = { aiMoves = aiMoves.copy(threads = aiMoves.threads + 1); saveAllSettings() },
                    canDecrement = aiMoves.threads > 1,
                    canIncrement = aiMoves.threads < maxThreads
                )
                SettingStepper(
                    label = "Hash memory (MB)",
                    value = "${aiMoves.hashMb} MB",
                    onDecrement = {
                        aiMoves = aiMoves.copy(hashMb = stepInList(aiMoves.hashMb, aiHashOptions, -1))
                        saveAllSettings()
                    },
                    onIncrement = {
                        aiMoves = aiMoves.copy(hashMb = stepInList(aiMoves.hashMb, aiHashOptions, 1))
                        saveAllSettings()
                    },
                    canDecrement = aiMoves.hashMb > aiHashOptions.first(),
                    canIncrement = aiMoves.hashMb < aiHashOptions.last()
                )
                if (showNnueToggles) {
                    NnueToggleRow(checked = aiMoves.useNnue) {
                        aiMoves = aiMoves.copy(useNnue = it)
                        saveAllSettings()
                    }
                }
            }

            CollapsibleStockfishCard(title = "Engine moves for AI", spacing = 16.dp) {
                Text(
                    "Send the best Stockfish continuations to AI. Search time is shared across the selected lines; more time gives deeper analysis.",
                    color = AppColors.SubtleText
                )
                SettingStepper(
                    label = "Number of lines",
                    value = aiEngine.multiPv.toString(),
                    onDecrement = { aiEngine = aiEngine.copy(multiPv = aiEngine.multiPv - 1); saveAllSettings() },
                    onIncrement = { aiEngine = aiEngine.copy(multiPv = aiEngine.multiPv + 1); saveAllSettings() },
                    canDecrement = aiEngine.multiPv > 1,
                    canIncrement = aiEngine.multiPv < 32
                )
                SettingStepper(
                    label = "Seconds per position",
                    value = "${aiEngine.secondsForPosition} s",
                    onDecrement = {
                        aiEngine = aiEngine.copy(secondsForPosition = stepInList(aiEngine.secondsForPosition, aiEngineSecondsOptions, -1))
                        saveAllSettings()
                    },
                    onIncrement = {
                        aiEngine = aiEngine.copy(secondsForPosition = stepInList(aiEngine.secondsForPosition, aiEngineSecondsOptions, 1))
                        saveAllSettings()
                    },
                    canDecrement = aiEngine.secondsForPosition > aiEngineSecondsOptions.first(),
                    canIncrement = aiEngine.secondsForPosition < aiEngineSecondsOptions.last()
                )
                SettingStepper(
                    label = "Number of threads",
                    value = aiEngine.threads.toString(),
                    onDecrement = { aiEngine = aiEngine.copy(threads = aiEngine.threads - 1); saveAllSettings() },
                    onIncrement = { aiEngine = aiEngine.copy(threads = aiEngine.threads + 1); saveAllSettings() },
                    canDecrement = aiEngine.threads > 1,
                    canIncrement = aiEngine.threads < maxThreads
                )
                SettingStepper(
                    label = "Hash memory (MB)",
                    value = "${aiEngine.hashMb} MB",
                    onDecrement = {
                        aiEngine = aiEngine.copy(hashMb = stepInList(aiEngine.hashMb, aiHashOptions, -1))
                        saveAllSettings()
                    },
                    onIncrement = {
                        aiEngine = aiEngine.copy(hashMb = stepInList(aiEngine.hashMb, aiHashOptions, 1))
                        saveAllSettings()
                    },
                    canDecrement = aiEngine.hashMb > aiHashOptions.first(),
                    canIncrement = aiEngine.hashMb < aiHashOptions.last()
                )
                if (showNnueToggles) {
                    NnueToggleRow(checked = aiEngine.useNnue) {
                        aiEngine = aiEngine.copy(useNnue = it)
                        saveAllSettings()
                    }
                }
            }
        }

    }
}

@Composable
private fun NnueToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Use NNUE", color = Color.White)
        Switch(checked = checked, onCheckedChange = null)
    }
}
