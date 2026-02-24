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
 * General settings screen for app-wide settings.
 */
@Composable
fun GeneralSettingsScreen(
    generalSettings: GeneralSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (GeneralSettings) -> Unit
) {
    var moveSoundsEnabled by remember { mutableStateOf(generalSettings.moveSoundsEnabled) }
    var lichessUsername by remember { mutableStateOf(generalSettings.lichessUsername) }

    fun saveSettings() {
        onSave(generalSettings.copy(
            moveSoundsEnabled = moveSoundsEnabled,
            lichessUsername = lichessUsername
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
        EvalTitleBar(
            title = "General",
            onBackClick = onBackToSettings,
            onEvalClick = onBackToGame
        )

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
                            color = AppColors.SubtleText
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

        // Lichess settings card
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
                    text = "Lichess",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Lichess username field
                Column {
                    Text("User on lichess.org", color = Color.White)
                    Text(
                        text = "Your Lichess username for score perspective",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.SubtleText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lichessUsername,
                        onValueChange = {
                            lichessUsername = it
                            saveSettings()
                        },
                        placeholder = { Text("Enter username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = AppColors.DarkGray,
                            focusedBorderColor = AppColors.LichessGreen,
                            unfocusedPlaceholderColor = AppColors.DimGray,
                            focusedPlaceholderColor = AppColors.DimGray
                        )
                    )
                }

            }
        }

    }
}
