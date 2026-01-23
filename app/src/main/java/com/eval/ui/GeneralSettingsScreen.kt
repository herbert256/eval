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
import kotlin.math.roundToInt

/**
 * General settings screen for app-wide settings.
 */
@Composable
fun GeneralSettingsScreen(
    generalSettings: GeneralSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (GeneralSettings) -> Unit
) {
    var longTapForFullScreen by remember { mutableStateOf(generalSettings.longTapForFullScreen) }
    var paginationPageSize by remember { mutableFloatStateOf(generalSettings.paginationPageSize.toFloat()) }
    var moveSoundsEnabled by remember { mutableStateOf(generalSettings.moveSoundsEnabled) }

    fun saveSettings() {
        onSave(GeneralSettings(
            longTapForFullScreen = longTapForFullScreen,
            paginationPageSize = paginationPageSize.roundToInt(),
            moveSoundsEnabled = moveSoundsEnabled
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
            text = "General settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)

        Spacer(modifier = Modifier.height(8.dp))

        // General settings card
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
                    text = "Display",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Full screen mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full screen mode", color = Color.White)
                        Text(
                            text = "Long tap anywhere to toggle (not saved)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                    Switch(
                        checked = longTapForFullScreen,
                        onCheckedChange = {
                            longTapForFullScreen = it
                            saveSettings()
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF404040))

                // Pagination page size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rows per page when pagination", color = Color.White)
                        Text(
                            text = "${paginationPageSize.roundToInt()}",
                            color = Color(0xFF6B9BFF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Number of items shown per page (5-50)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA)
                    )
                    Slider(
                        value = paginationPageSize,
                        onValueChange = { paginationPageSize = it },
                        onValueChangeFinished = { saveSettings() },
                        valueRange = 5f..50f,
                        steps = 8,  // 5, 10, 15, 20, 25, 30, 35, 40, 45, 50
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = Color(0xFF404040))

                // Move sounds toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Move sounds", color = Color.White)
                        Text(
                            text = "Play sound when navigating moves",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                    Switch(
                        checked = moveSoundsEnabled,
                        onCheckedChange = {
                            moveSoundsEnabled = it
                            saveSettings()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }
}
