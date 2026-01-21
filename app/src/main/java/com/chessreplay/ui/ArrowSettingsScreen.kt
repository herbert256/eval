package com.chessreplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Arrow settings screen for configuring move arrow display options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrowSettingsScreen(
    stockfishSettings: StockfishSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (StockfishSettings) -> Unit
) {
    var drawArrows by remember { mutableStateOf(stockfishSettings.manualStage.drawArrows) }
    var numArrows by remember { mutableStateOf(stockfishSettings.manualStage.numArrows) }
    var showArrowNumbers by remember { mutableStateOf(stockfishSettings.manualStage.showArrowNumbers) }
    var whiteArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.whiteArrowColor) }
    var blackArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.blackArrowColor) }
    var showWhiteColorPicker by remember { mutableStateOf(false) }
    var showBlackColorPicker by remember { mutableStateOf(false) }
    var drawArrowsExpanded by remember { mutableStateOf(false) }
    var numArrowsExpanded by remember { mutableStateOf(false) }
    var showArrowNumbersExpanded by remember { mutableStateOf(false) }

    val numArrowsOptions = listOf(1, 2, 3, 4, 5, 6, 7, 8)

    fun saveSettings(
        newDrawArrows: Boolean = drawArrows,
        newNumArrows: Int = numArrows,
        newShowArrowNumbers: Boolean = showArrowNumbers,
        newWhiteArrowColor: Long = whiteArrowColor,
        newBlackArrowColor: Long = blackArrowColor
    ) {
        onSave(stockfishSettings.copy(
            manualStage = stockfishSettings.manualStage.copy(
                drawArrows = newDrawArrows,
                numArrows = newNumArrows,
                showArrowNumbers = newShowArrowNumbers,
                whiteArrowColor = newWhiteArrowColor,
                blackArrowColor = newBlackArrowColor
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
            text = "Arrow settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)

        Spacer(modifier = Modifier.height(8.dp))

        // Draw arrows dropdown (always visible)
        ExposedDropdownMenuBox(
            expanded = drawArrowsExpanded,
            onExpandedChange = { drawArrowsExpanded = it }
        ) {
            OutlinedTextField(
                value = if (drawArrows) "Yes" else "No",
                onValueChange = {},
                readOnly = true,
                label = { Text("Draw arrows") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = drawArrowsExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = drawArrowsExpanded, onDismissRequest = { drawArrowsExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Yes") },
                    onClick = {
                        drawArrows = true
                        drawArrowsExpanded = false
                        saveSettings(newDrawArrows = true)
                    }
                )
                DropdownMenuItem(
                    text = { Text("No") },
                    onClick = {
                        drawArrows = false
                        drawArrowsExpanded = false
                        saveSettings(newDrawArrows = false)
                    }
                )
            }
        }

        // All other settings are only visible when drawArrows is true
        if (drawArrows) {
            // Number of arrows dropdown
            ExposedDropdownMenuBox(
                expanded = numArrowsExpanded,
                onExpandedChange = { numArrowsExpanded = it }
            ) {
                OutlinedTextField(
                    value = numArrows.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Number of arrows") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = numArrowsExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = numArrowsExpanded, onDismissRequest = { numArrowsExpanded = false }) {
                    numArrowsOptions.forEach { num ->
                        DropdownMenuItem(
                            text = { Text(num.toString()) },
                            onClick = {
                                numArrows = num
                                numArrowsExpanded = false
                                saveSettings(newNumArrows = num)
                            }
                        )
                    }
                }
            }

            // Show move number in arrow dropdown
            ExposedDropdownMenuBox(
                expanded = showArrowNumbersExpanded,
                onExpandedChange = { showArrowNumbersExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (showArrowNumbers) "Yes" else "No",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Show move number in arrow") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showArrowNumbersExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = showArrowNumbersExpanded, onDismissRequest = { showArrowNumbersExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Yes") },
                        onClick = {
                            showArrowNumbers = true
                            showArrowNumbersExpanded = false
                            saveSettings(newShowArrowNumbers = true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("No") },
                        onClick = {
                            showArrowNumbers = false
                            showArrowNumbersExpanded = false
                            saveSettings(newShowArrowNumbers = false)
                        }
                    )
                }
            }

            // Color picker for white move arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("White move color")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(whiteArrowColor.toInt()))
                        .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .clickable { showWhiteColorPicker = true }
                )
            }

            // Color picker for black move arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Black move color")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(blackArrowColor.toInt()))
                        .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .clickable { showBlackColorPicker = true }
                )
            }
        }

        // Color picker dialogs (outside the if block so they can still be dismissed)
        if (showWhiteColorPicker) {
            ColorPickerDialog(
                currentColor = whiteArrowColor,
                title = "Arrow color for white moves",
                onColorSelected = { color ->
                    whiteArrowColor = color
                    saveSettings(newWhiteArrowColor = color)
                },
                onDismiss = { showWhiteColorPicker = false }
            )
        }

        if (showBlackColorPicker) {
            ColorPickerDialog(
                currentColor = blackArrowColor,
                title = "Arrow color for black moves",
                onColorSelected = { color ->
                    blackArrowColor = color
                    saveSettings(newBlackArrowColor = color)
                },
                onDismiss = { showBlackColorPicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }
}
