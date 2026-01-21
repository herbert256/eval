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
 * Board layout settings screen for configuring chess board appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardLayoutSettingsScreen(
    boardLayoutSettings: BoardLayoutSettings,
    onBack: () -> Unit,
    onSave: (BoardLayoutSettings) -> Unit
) {
    var showCoordinates by remember { mutableStateOf(boardLayoutSettings.showCoordinates) }
    var showLastMove by remember { mutableStateOf(boardLayoutSettings.showLastMove) }
    var whiteSquareColor by remember { mutableStateOf(boardLayoutSettings.whiteSquareColor) }
    var blackSquareColor by remember { mutableStateOf(boardLayoutSettings.blackSquareColor) }
    var whitePieceColor by remember { mutableStateOf(boardLayoutSettings.whitePieceColor) }
    var blackPieceColor by remember { mutableStateOf(boardLayoutSettings.blackPieceColor) }

    var showCoordinatesExpanded by remember { mutableStateOf(false) }
    var showLastMoveExpanded by remember { mutableStateOf(false) }
    var showWhiteSquareColorPicker by remember { mutableStateOf(false) }
    var showBlackSquareColorPicker by remember { mutableStateOf(false) }
    var showWhitePieceColorPicker by remember { mutableStateOf(false) }
    var showBlackPieceColorPicker by remember { mutableStateOf(false) }

    fun saveSettings(
        newShowCoordinates: Boolean = showCoordinates,
        newShowLastMove: Boolean = showLastMove,
        newWhiteSquareColor: Long = whiteSquareColor,
        newBlackSquareColor: Long = blackSquareColor,
        newWhitePieceColor: Long = whitePieceColor,
        newBlackPieceColor: Long = blackPieceColor
    ) {
        onSave(BoardLayoutSettings(
            showCoordinates = newShowCoordinates,
            showLastMove = newShowLastMove,
            whiteSquareColor = newWhiteSquareColor,
            blackSquareColor = newBlackSquareColor,
            whitePieceColor = newWhitePieceColor,
            blackPieceColor = newBlackPieceColor
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
            text = "Board layout",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButton(onClick = onBack)

        Spacer(modifier = Modifier.height(8.dp))

        // Show coordinates dropdown
        ExposedDropdownMenuBox(
            expanded = showCoordinatesExpanded,
            onExpandedChange = { showCoordinatesExpanded = it }
        ) {
            OutlinedTextField(
                value = if (showCoordinates) "Yes" else "No",
                onValueChange = {},
                readOnly = true,
                label = { Text("Show coordinates") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCoordinatesExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = showCoordinatesExpanded, onDismissRequest = { showCoordinatesExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Yes") },
                    onClick = {
                        showCoordinates = true
                        showCoordinatesExpanded = false
                        saveSettings(newShowCoordinates = true)
                    }
                )
                DropdownMenuItem(
                    text = { Text("No") },
                    onClick = {
                        showCoordinates = false
                        showCoordinatesExpanded = false
                        saveSettings(newShowCoordinates = false)
                    }
                )
            }
        }

        // Show last move dropdown
        ExposedDropdownMenuBox(
            expanded = showLastMoveExpanded,
            onExpandedChange = { showLastMoveExpanded = it }
        ) {
            OutlinedTextField(
                value = if (showLastMove) "Yes" else "No",
                onValueChange = {},
                readOnly = true,
                label = { Text("Show last move") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLastMoveExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = showLastMoveExpanded, onDismissRequest = { showLastMoveExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Yes") },
                    onClick = {
                        showLastMove = true
                        showLastMoveExpanded = false
                        saveSettings(newShowLastMove = true)
                    }
                )
                DropdownMenuItem(
                    text = { Text("No") },
                    onClick = {
                        showLastMove = false
                        showLastMoveExpanded = false
                        saveSettings(newShowLastMove = false)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // White squares color picker
        ColorSettingRow(
            label = "White squares color",
            color = Color(whiteSquareColor.toInt()),
            onClick = { showWhiteSquareColorPicker = true }
        )

        // Black squares color picker
        ColorSettingRow(
            label = "Black squares color",
            color = Color(blackSquareColor.toInt()),
            onClick = { showBlackSquareColorPicker = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // White pieces color picker
        ColorSettingRow(
            label = "White pieces color",
            color = Color(whitePieceColor.toInt()),
            onClick = { showWhitePieceColorPicker = true }
        )

        // Black pieces color picker
        ColorSettingRow(
            label = "Black pieces color",
            color = Color(blackPieceColor.toInt()),
            onClick = { showBlackPieceColorPicker = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsBackButton(onClick = onBack)
    }

    // Color picker dialogs
    if (showWhiteSquareColorPicker) {
        ColorPickerDialog(
            currentColor = whiteSquareColor,
            title = "White squares color",
            onColorSelected = { color ->
                whiteSquareColor = color
                saveSettings(newWhiteSquareColor = color)
            },
            onDismiss = { showWhiteSquareColorPicker = false }
        )
    }

    if (showBlackSquareColorPicker) {
        ColorPickerDialog(
            currentColor = blackSquareColor,
            title = "Black squares color",
            onColorSelected = { color ->
                blackSquareColor = color
                saveSettings(newBlackSquareColor = color)
            },
            onDismiss = { showBlackSquareColorPicker = false }
        )
    }

    if (showWhitePieceColorPicker) {
        ColorPickerDialog(
            currentColor = whitePieceColor,
            title = "White pieces color",
            onColorSelected = { color ->
                whitePieceColor = color
                saveSettings(newWhitePieceColor = color)
            },
            onDismiss = { showWhitePieceColorPicker = false }
        )
    }

    if (showBlackPieceColorPicker) {
        ColorPickerDialog(
            currentColor = blackPieceColor,
            title = "Black pieces color",
            onColorSelected = { color ->
                blackPieceColor = color
                saveSettings(newBlackPieceColor = color)
            },
            onDismiss = { showBlackPieceColorPicker = false }
        )
    }
}

@Composable
private fun ColorSettingRow(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
        )
    }
}
