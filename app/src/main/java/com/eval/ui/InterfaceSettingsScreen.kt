package com.eval.ui

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
 * Reusable toggle row for interface visibility settings.
 */
@Composable
private fun VisibilityToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

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
    // Preview Stage visibility state
    var previewShowMoveList by remember { mutableStateOf(interfaceVisibility.previewStage.showMoveList) }
    var previewShowBoard by remember { mutableStateOf(interfaceVisibility.previewStage.showBoard) }
    var previewShowGameInfo by remember { mutableStateOf(interfaceVisibility.previewStage.showGameInfo) }
    var previewShowPgn by remember { mutableStateOf(interfaceVisibility.previewStage.showPgn) }

    // Analyse Stage visibility state
    var analyseShowMoveList by remember { mutableStateOf(interfaceVisibility.analyseStage.showMoveList) }
    var analyseShowScoreLineGraph by remember { mutableStateOf(interfaceVisibility.analyseStage.showScoreLineGraph) }
    var analyseShowScoreBarsGraph by remember { mutableStateOf(interfaceVisibility.analyseStage.showScoreBarsGraph) }
    var analyseShowResultBar by remember { mutableStateOf(interfaceVisibility.analyseStage.showResultBar) }
    var analyseShowGameInfo by remember { mutableStateOf(interfaceVisibility.analyseStage.showGameInfo) }
    var analyseShowBoard by remember { mutableStateOf(interfaceVisibility.analyseStage.showBoard) }
    var analyseShowPgn by remember { mutableStateOf(interfaceVisibility.analyseStage.showPgn) }

    // Manual Stage visibility state
    var manualShowResultBar by remember { mutableStateOf(interfaceVisibility.manualStage.showResultBar) }
    var manualShowScoreLineGraph by remember { mutableStateOf(interfaceVisibility.manualStage.showScoreLineGraph) }
    var manualShowScoreBarsGraph by remember { mutableStateOf(interfaceVisibility.manualStage.showScoreBarsGraph) }
    var manualShowMoveList by remember { mutableStateOf(interfaceVisibility.manualStage.showMoveList) }
    var manualShowGameInfo by remember { mutableStateOf(interfaceVisibility.manualStage.showGameInfo) }
    var manualShowPgn by remember { mutableStateOf(interfaceVisibility.manualStage.showPgn) }

    fun saveAllSettings() {
        onSave(InterfaceVisibilitySettings(
            previewStage = PreviewStageVisibility(
                showMoveList = previewShowMoveList,
                showBoard = previewShowBoard,
                showGameInfo = previewShowGameInfo,
                showPgn = previewShowPgn
            ),
            analyseStage = AnalyseStageVisibility(
                showMoveList = analyseShowMoveList,
                showScoreLineGraph = analyseShowScoreLineGraph,
                showScoreBarsGraph = analyseShowScoreBarsGraph,
                showResultBar = analyseShowResultBar,
                showGameInfo = analyseShowGameInfo,
                showBoard = analyseShowBoard,
                showPgn = analyseShowPgn
            ),
            manualStage = ManualStageVisibility(
                showResultBar = manualShowResultBar,
                showScoreLineGraph = manualShowScoreLineGraph,
                showScoreBarsGraph = manualShowScoreBarsGraph,
                showMoveList = manualShowMoveList,
                showGameInfo = manualShowGameInfo,
                showPgn = manualShowPgn
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
            text = "Show interface elements",
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

                VisibilityToggle(
                    label = "Show move list",
                    checked = previewShowMoveList,
                    onCheckedChange = {
                        previewShowMoveList = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show board",
                    checked = previewShowBoard,
                    onCheckedChange = {
                        previewShowBoard = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show game info",
                    checked = previewShowGameInfo,
                    onCheckedChange = {
                        previewShowGameInfo = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show PGN",
                    checked = previewShowPgn,
                    onCheckedChange = {
                        previewShowPgn = it
                        saveAllSettings()
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

                VisibilityToggle(
                    label = "Show move list",
                    checked = analyseShowMoveList,
                    onCheckedChange = {
                        analyseShowMoveList = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show score line graph",
                    checked = analyseShowScoreLineGraph,
                    onCheckedChange = {
                        analyseShowScoreLineGraph = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show score bars graph",
                    checked = analyseShowScoreBarsGraph,
                    onCheckedChange = {
                        analyseShowScoreBarsGraph = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show result bar",
                    checked = analyseShowResultBar,
                    onCheckedChange = {
                        analyseShowResultBar = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show game info",
                    checked = analyseShowGameInfo,
                    onCheckedChange = {
                        analyseShowGameInfo = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show board",
                    checked = analyseShowBoard,
                    onCheckedChange = {
                        analyseShowBoard = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show PGN",
                    checked = analyseShowPgn,
                    onCheckedChange = {
                        analyseShowPgn = it
                        saveAllSettings()
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

                VisibilityToggle(
                    label = "Show result bar",
                    checked = manualShowResultBar,
                    onCheckedChange = {
                        manualShowResultBar = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show score line graph",
                    checked = manualShowScoreLineGraph,
                    onCheckedChange = {
                        manualShowScoreLineGraph = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show score bars graph",
                    checked = manualShowScoreBarsGraph,
                    onCheckedChange = {
                        manualShowScoreBarsGraph = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show move list",
                    checked = manualShowMoveList,
                    onCheckedChange = {
                        manualShowMoveList = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show game info",
                    checked = manualShowGameInfo,
                    onCheckedChange = {
                        manualShowGameInfo = it
                        saveAllSettings()
                    }
                )

                VisibilityToggle(
                    label = "Show PGN",
                    checked = manualShowPgn,
                    onCheckedChange = {
                        manualShowPgn = it
                        saveAllSettings()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }
}
