package com.chessreplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Stockfish settings screen for configuring engine parameters for all analysis stages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockfishSettingsScreen(
    stockfishSettings: StockfishSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (StockfishSettings) -> Unit
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

    // Manual Analyse Stage state
    var manualDepth by remember { mutableStateOf(stockfishSettings.manualStage.depth) }
    var manualThreads by remember { mutableStateOf(stockfishSettings.manualStage.threads) }
    var manualHash by remember { mutableStateOf(stockfishSettings.manualStage.hashMb) }
    var manualMultiPv by remember { mutableStateOf(stockfishSettings.manualStage.multiPv) }
    var manualNnue by remember { mutableStateOf(stockfishSettings.manualStage.useNnue) }

    // Dropdown expanded state
    var previewSecondsExpanded by remember { mutableStateOf(false) }
    var previewThreadsExpanded by remember { mutableStateOf(false) }
    var previewHashExpanded by remember { mutableStateOf(false) }
    var analyseSecondsExpanded by remember { mutableStateOf(false) }
    var analyseThreadsExpanded by remember { mutableStateOf(false) }
    var analyseHashExpanded by remember { mutableStateOf(false) }
    var manualDepthExpanded by remember { mutableStateOf(false) }
    var manualThreadsExpanded by remember { mutableStateOf(false) }
    var manualHashExpanded by remember { mutableStateOf(false) }
    var manualMultiPvExpanded by remember { mutableStateOf(false) }

    // Options for dropdowns
    val previewSecondsOptions = listOf(0.01f, 0.05f, 0.10f, 0.25f, 0.50f)
    val previewThreadsOptions = (1..2).toList()
    val previewHashOptions = listOf(8, 16, 32, 64)

    val analyseSecondsOptions = listOf(0.50f, 0.75f, 1.00f, 1.50f, 2.50f, 5.00f, 10.00f)
    val analyseThreadsOptions = (1..4).toList()
    val analyseHashOptions = listOf(16, 32, 64, 128)

    val manualDepthOptions = (16..32).toList()
    val manualThreadsOptions = (1..12).toList()
    val manualHashOptions = listOf(32, 64, 128, 256)
    val manualMultiPvOptions = (1..6).toList()

    fun saveAllSettings() {
        onSave(stockfishSettings.copy(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Stockfish",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)

        Spacer(modifier = Modifier.height(8.dp))

        // ===== PREVIEW STAGE CARD =====
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Preview Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Seconds for move
                ExposedDropdownMenuBox(
                    expanded = previewSecondsExpanded,
                    onExpandedChange = { previewSecondsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = String.format("%.2f s", previewSeconds),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Seconds for move") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = previewSecondsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = previewSecondsExpanded, onDismissRequest = { previewSecondsExpanded = false }) {
                        previewSecondsOptions.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(String.format("%.2f s", seconds)) },
                                onClick = {
                                    previewSeconds = seconds
                                    previewSecondsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Number of threads
                ExposedDropdownMenuBox(
                    expanded = previewThreadsExpanded,
                    onExpandedChange = { previewThreadsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = previewThreads.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Number of threads") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = previewThreadsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = previewThreadsExpanded, onDismissRequest = { previewThreadsExpanded = false }) {
                        previewThreadsOptions.forEach { threads ->
                            DropdownMenuItem(
                                text = { Text(threads.toString()) },
                                onClick = {
                                    previewThreads = threads
                                    previewThreadsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Hash memory
                ExposedDropdownMenuBox(
                    expanded = previewHashExpanded,
                    onExpandedChange = { previewHashExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "$previewHash MB",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hash memory (MB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = previewHashExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = previewHashExpanded, onDismissRequest = { previewHashExpanded = false }) {
                        previewHashOptions.forEach { hash ->
                            DropdownMenuItem(
                                text = { Text("$hash MB") },
                                onClick = {
                                    previewHash = hash
                                    previewHashExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Use NNUE toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use NNUE", color = Color.White)
                    Switch(
                        checked = previewNnue,
                        onCheckedChange = {
                            previewNnue = it
                            saveAllSettings()
                        }
                    )
                }
            }
        }

        // ===== ANALYSE STAGE CARD =====
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Analyse Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Seconds for move
                ExposedDropdownMenuBox(
                    expanded = analyseSecondsExpanded,
                    onExpandedChange = { analyseSecondsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = String.format("%.2f s", analyseSeconds),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Seconds for move") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = analyseSecondsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = analyseSecondsExpanded, onDismissRequest = { analyseSecondsExpanded = false }) {
                        analyseSecondsOptions.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(String.format("%.2f s", seconds)) },
                                onClick = {
                                    analyseSeconds = seconds
                                    analyseSecondsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Number of threads
                ExposedDropdownMenuBox(
                    expanded = analyseThreadsExpanded,
                    onExpandedChange = { analyseThreadsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = analyseThreads.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Number of threads") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = analyseThreadsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = analyseThreadsExpanded, onDismissRequest = { analyseThreadsExpanded = false }) {
                        analyseThreadsOptions.forEach { threads ->
                            DropdownMenuItem(
                                text = { Text(threads.toString()) },
                                onClick = {
                                    analyseThreads = threads
                                    analyseThreadsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Hash memory
                ExposedDropdownMenuBox(
                    expanded = analyseHashExpanded,
                    onExpandedChange = { analyseHashExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "$analyseHash MB",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hash memory (MB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = analyseHashExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = analyseHashExpanded, onDismissRequest = { analyseHashExpanded = false }) {
                        analyseHashOptions.forEach { hash ->
                            DropdownMenuItem(
                                text = { Text("$hash MB") },
                                onClick = {
                                    analyseHash = hash
                                    analyseHashExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Use NNUE toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use NNUE", color = Color.White)
                    Switch(
                        checked = analyseNnue,
                        onCheckedChange = {
                            analyseNnue = it
                            saveAllSettings()
                        }
                    )
                }
            }
        }

        // ===== MANUAL ANALYSE STAGE CARD =====
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Manual Analyse Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Depth
                ExposedDropdownMenuBox(
                    expanded = manualDepthExpanded,
                    onExpandedChange = { manualDepthExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manualDepth.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Depth") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualDepthExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualDepthExpanded, onDismissRequest = { manualDepthExpanded = false }) {
                        manualDepthOptions.forEach { depth ->
                            DropdownMenuItem(
                                text = { Text(depth.toString()) },
                                onClick = {
                                    manualDepth = depth
                                    manualDepthExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Number of threads
                ExposedDropdownMenuBox(
                    expanded = manualThreadsExpanded,
                    onExpandedChange = { manualThreadsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manualThreads.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Number of threads") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualThreadsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualThreadsExpanded, onDismissRequest = { manualThreadsExpanded = false }) {
                        manualThreadsOptions.forEach { threads ->
                            DropdownMenuItem(
                                text = { Text(threads.toString()) },
                                onClick = {
                                    manualThreads = threads
                                    manualThreadsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Hash memory
                ExposedDropdownMenuBox(
                    expanded = manualHashExpanded,
                    onExpandedChange = { manualHashExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "$manualHash MB",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hash memory (MB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualHashExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualHashExpanded, onDismissRequest = { manualHashExpanded = false }) {
                        manualHashOptions.forEach { hash ->
                            DropdownMenuItem(
                                text = { Text("$hash MB") },
                                onClick = {
                                    manualHash = hash
                                    manualHashExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // MultiPV lines
                ExposedDropdownMenuBox(
                    expanded = manualMultiPvExpanded,
                    onExpandedChange = { manualMultiPvExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manualMultiPv.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("MultiPV lines") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualMultiPvExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualMultiPvExpanded, onDismissRequest = { manualMultiPvExpanded = false }) {
                        manualMultiPvOptions.forEach { multiPv ->
                            DropdownMenuItem(
                                text = { Text(multiPv.toString()) },
                                onClick = {
                                    manualMultiPv = multiPv
                                    manualMultiPvExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Use NNUE toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use NNUE", color = Color.White)
                    Switch(
                        checked = manualNnue,
                        onCheckedChange = {
                            manualNnue = it
                            saveAllSettings()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }
}
