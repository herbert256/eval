package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Interface visibility settings screen for configuring which UI elements are shown in each stage.
 */
@Composable
fun InterfaceSettingsScreen(
    interfaceVisibility: InterfaceVisibilitySettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (InterfaceVisibilitySettings) -> Unit
) {
    var preview by remember { mutableStateOf(interfaceVisibility.previewStage) }
    var analyse by remember { mutableStateOf(interfaceVisibility.analyseStage) }
    var manual by remember { mutableStateOf(interfaceVisibility.manualStage) }

    fun save() {
        onSave(InterfaceVisibilitySettings(preview, analyse, manual))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = "Interface elements",
            onBackClick = onBackToSettings,
            onEvalClick = onBackToGame
        )

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

                Text(
                    text = "Always shown: Score Line graph, Game Information",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                SettingsToggle(
                    label = "Score Bars graph",
                    checked = preview.showScoreBarsGraph,
                    onCheckedChange = {
                        preview = preview.copy(showScoreBarsGraph = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Result bar",
                    checked = preview.showResultBar,
                    onCheckedChange = {
                        preview = preview.copy(showResultBar = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Board",
                    checked = preview.showBoard,
                    onCheckedChange = {
                        preview = preview.copy(showBoard = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Move list",
                    checked = preview.showMoveList,
                    onCheckedChange = {
                        preview = preview.copy(showMoveList = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "PGN",
                    checked = preview.showPgn,
                    onCheckedChange = {
                        preview = preview.copy(showPgn = it)
                        save()
                    }
                )
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

                SettingsToggle(
                    label = "Score Line graph",
                    checked = analyse.showScoreLineGraph,
                    onCheckedChange = {
                        analyse = analyse.copy(showScoreLineGraph = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Score Bars graph",
                    checked = analyse.showScoreBarsGraph,
                    onCheckedChange = {
                        analyse = analyse.copy(showScoreBarsGraph = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Board",
                    checked = analyse.showBoard,
                    onCheckedChange = {
                        analyse = analyse.copy(showBoard = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Stockfish Analyse",
                    checked = analyse.showStockfishAnalyse,
                    onCheckedChange = {
                        analyse = analyse.copy(showStockfishAnalyse = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Result bar",
                    checked = analyse.showResultBar,
                    onCheckedChange = {
                        analyse = analyse.copy(showResultBar = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Move list",
                    checked = analyse.showMoveList,
                    onCheckedChange = {
                        analyse = analyse.copy(showMoveList = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Game Information",
                    checked = analyse.showGameInfo,
                    onCheckedChange = {
                        analyse = analyse.copy(showGameInfo = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "PGN",
                    checked = analyse.showPgn,
                    onCheckedChange = {
                        analyse = analyse.copy(showPgn = it)
                        save()
                    }
                )
            }
        }

        // ===== MANUAL STAGE CARD =====
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
                    text = "Manual Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = "Always shown: Board, Navigation bar, Stockfish",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                SettingsToggle(
                    label = "Result bar",
                    checked = manual.showResultBar,
                    onCheckedChange = {
                        manual = manual.copy(showResultBar = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Score Line graph",
                    checked = manual.showScoreLineGraph,
                    onCheckedChange = {
                        manual = manual.copy(showScoreLineGraph = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Score Bars graph",
                    checked = manual.showScoreBarsGraph,
                    onCheckedChange = {
                        manual = manual.copy(showScoreBarsGraph = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Time Usage graph",
                    checked = manual.showTimeGraph,
                    onCheckedChange = {
                        manual = manual.copy(showTimeGraph = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Opening Explorer",
                    checked = manual.showOpeningExplorer,
                    onCheckedChange = {
                        manual = manual.copy(showOpeningExplorer = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Opening Name",
                    checked = manual.showOpeningName,
                    onCheckedChange = {
                        manual = manual.copy(showOpeningName = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Raw Stockfish score",
                    checked = manual.showRawStockfishScore,
                    onCheckedChange = {
                        manual = manual.copy(showRawStockfishScore = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Move list",
                    checked = manual.showMoveList,
                    onCheckedChange = {
                        manual = manual.copy(showMoveList = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "Game Information",
                    checked = manual.showGameInfo,
                    onCheckedChange = {
                        manual = manual.copy(showGameInfo = it)
                        save()
                    }
                )

                SettingsToggle(
                    label = "PGN",
                    checked = manual.showPgn,
                    onCheckedChange = {
                        manual = manual.copy(showPgn = it)
                        save()
                    }
                )
            }
        }

    }
}
