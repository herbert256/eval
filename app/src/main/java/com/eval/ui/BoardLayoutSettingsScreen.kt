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
 * Board layout settings screen for configuring chess board appearance.
 */
@Composable
fun BoardLayoutSettingsScreen(
    boardLayoutSettings: BoardLayoutSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (BoardLayoutSettings) -> Unit
) {
    var showCoordinates by remember { mutableStateOf(boardLayoutSettings.showCoordinates) }
    var showLastMove by remember { mutableStateOf(boardLayoutSettings.showLastMove) }
    var playerBarMode by remember { mutableStateOf(boardLayoutSettings.playerBarMode) }
    var showRedBorderForPlayerToMove by remember { mutableStateOf(boardLayoutSettings.showRedBorderForPlayerToMove) }
    var whiteSquareColor by remember { mutableStateOf(boardLayoutSettings.whiteSquareColor) }
    var blackSquareColor by remember { mutableStateOf(boardLayoutSettings.blackSquareColor) }
    var whitePieceColor by remember { mutableStateOf(boardLayoutSettings.whitePieceColor) }
    var blackPieceColor by remember { mutableStateOf(boardLayoutSettings.blackPieceColor) }
    // Evaluation bar settings
    var evalBarPosition by remember { mutableStateOf(boardLayoutSettings.evalBarPosition) }
    var evalBarColor1 by remember { mutableStateOf(boardLayoutSettings.evalBarColor1) }
    var evalBarColor2 by remember { mutableStateOf(boardLayoutSettings.evalBarColor2) }
    var evalBarRange by remember { mutableStateOf(boardLayoutSettings.evalBarRange) }

    // Color picker state: pair of (title, currentColor) and callback
    var activeColorPicker by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var activeColorCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    fun saveSettings(
        newShowCoordinates: Boolean = showCoordinates,
        newShowLastMove: Boolean = showLastMove,
        newPlayerBarMode: PlayerBarMode = playerBarMode,
        newShowRedBorderForPlayerToMove: Boolean = showRedBorderForPlayerToMove,
        newWhiteSquareColor: Long = whiteSquareColor,
        newBlackSquareColor: Long = blackSquareColor,
        newWhitePieceColor: Long = whitePieceColor,
        newBlackPieceColor: Long = blackPieceColor,
        newEvalBarPosition: EvalBarPosition = evalBarPosition,
        newEvalBarColor1: Long = evalBarColor1,
        newEvalBarColor2: Long = evalBarColor2,
        newEvalBarRange: Int = evalBarRange
    ) {
        onSave(BoardLayoutSettings(
            showCoordinates = newShowCoordinates,
            showLastMove = newShowLastMove,
            playerBarMode = newPlayerBarMode,
            showRedBorderForPlayerToMove = newShowRedBorderForPlayerToMove,
            whiteSquareColor = newWhiteSquareColor,
            blackSquareColor = newBlackSquareColor,
            whitePieceColor = newWhitePieceColor,
            blackPieceColor = newBlackPieceColor,
            evalBarPosition = newEvalBarPosition,
            evalBarColor1 = newEvalBarColor1,
            evalBarColor2 = newEvalBarColor2,
            evalBarRange = newEvalBarRange
        ))
    }

    // Full-screen color picker (early return pattern)
    activeColorPicker?.let { (title, color) ->
        ColorPickerDialog(
            currentColor = color,
            title = title,
            onColorSelected = { newColor ->
                activeColorCallback?.invoke(newColor)
            },
            onDismiss = {
                activeColorPicker = null
                activeColorCallback = null
            }
        )
        return
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
            title = "Board layout",
            onBackClick = onBackToSettings,
            onEvalClick = onBackToGame
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show coordinates toggle
        SettingsToggle(
            label = "Show coordinates",
            checked = showCoordinates,
            onCheckedChange = {
                showCoordinates = it
                saveSettings(newShowCoordinates = it)
            }
        )

        // Show last move toggle
        SettingsToggle(
            label = "Show last move",
            checked = showLastMove,
            onCheckedChange = {
                showLastMove = it
                saveSettings(newShowLastMove = it)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Player bar(s) - inline radio buttons
        Text("Player bar(s)", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayerBarMode.entries.forEach { mode ->
                val label = when (mode) {
                    PlayerBarMode.NONE -> "None"
                    PlayerBarMode.TOP -> "Top"
                    PlayerBarMode.BOTTOM -> "Bottom"
                    PlayerBarMode.BOTH -> "Both"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = playerBarMode == mode,
                        onClick = {
                            playerBarMode = mode
                            saveSettings(newPlayerBarMode = mode)
                        }
                    )
                    Text(label, color = Color.White)
                }
            }
        }

        // Red border for player to move - only visible when player bar mode is not NONE
        if (playerBarMode != PlayerBarMode.NONE) {
            SettingsToggle(
                label = "Red border for player to move",
                checked = showRedBorderForPlayerToMove,
                onCheckedChange = {
                    showRedBorderForPlayerToMove = it
                    saveSettings(newShowRedBorderForPlayerToMove = it)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // White squares color picker
        ColorSettingRow(
            label = "White squares color",
            color = Color(whiteSquareColor.toInt()),
            onClick = {
                activeColorPicker = "White squares color" to whiteSquareColor
                activeColorCallback = { color ->
                    whiteSquareColor = color
                    saveSettings(newWhiteSquareColor = color)
                }
            }
        )

        // Black squares color picker
        ColorSettingRow(
            label = "Black squares color",
            color = Color(blackSquareColor.toInt()),
            onClick = {
                activeColorPicker = "Black squares color" to blackSquareColor
                activeColorCallback = { color ->
                    blackSquareColor = color
                    saveSettings(newBlackSquareColor = color)
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // White pieces color picker
        ColorSettingRow(
            label = "White pieces color",
            color = Color(whitePieceColor.toInt()),
            onClick = {
                activeColorPicker = "White pieces color" to whitePieceColor
                activeColorCallback = { color ->
                    whitePieceColor = color
                    saveSettings(newWhitePieceColor = color)
                }
            }
        )

        // Black pieces color picker
        ColorSettingRow(
            label = "Black pieces color",
            color = Color(blackPieceColor.toInt()),
            onClick = {
                activeColorPicker = "Black pieces color" to blackPieceColor
                activeColorCallback = { color ->
                    blackPieceColor = color
                    saveSettings(newBlackPieceColor = color)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== EVALUATION BAR CARD =====
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
                    text = "Evaluation bar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Eval bar position - inline radio buttons
                Text("Show evaluation bar", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EvalBarPosition.entries.forEach { pos ->
                        val label = when (pos) {
                            EvalBarPosition.NONE -> "None"
                            EvalBarPosition.LEFT -> "Left"
                            EvalBarPosition.RIGHT -> "Right"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = evalBarPosition == pos,
                                onClick = {
                                    evalBarPosition = pos
                                    saveSettings(newEvalBarPosition = pos)
                                }
                            )
                            Text(label, color = Color.White)
                        }
                    }
                }

                // Only show other settings if eval bar is visible
                if (evalBarPosition != EvalBarPosition.NONE) {
                    // Color 1 (score color)
                    ColorSettingRow(
                        label = "Color 1 (score)",
                        color = Color(evalBarColor1.toInt()),
                        onClick = {
                            activeColorPicker = "Evaluation bar color 1 (score)" to evalBarColor1
                            activeColorCallback = { color ->
                                evalBarColor1 = color
                                saveSettings(newEvalBarColor1 = color)
                            }
                        }
                    )

                    // Color 2 (filler color)
                    ColorSettingRow(
                        label = "Color 2 (filler)",
                        color = Color(evalBarColor2.toInt()),
                        onClick = {
                            activeColorPicker = "Evaluation bar color 2 (filler)" to evalBarColor2
                            activeColorCallback = { color ->
                                evalBarColor2 = color
                                saveSettings(newEvalBarColor2 = color)
                            }
                        }
                    )

                    // Range stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Range (pawns)", color = Color.White)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (evalBarRange > 1) {
                                        evalBarRange -= 1
                                        saveSettings(newEvalBarRange = evalBarRange)
                                    }
                                },
                                enabled = evalBarRange > 1,
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("-")
                            }
                            Text(
                                text = evalBarRange.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    if (evalBarRange < 10) {
                                        evalBarRange += 1
                                        saveSettings(newEvalBarRange = evalBarRange)
                                    }
                                },
                                enabled = evalBarRange < 10,
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("+")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reset to defaults button
        Button(
            onClick = {
                // Reset all values to defaults
                showCoordinates = true
                showLastMove = true
                playerBarMode = PlayerBarMode.BOTH
                showRedBorderForPlayerToMove = false
                whiteSquareColor = DEFAULT_WHITE_SQUARE_COLOR
                blackSquareColor = DEFAULT_BLACK_SQUARE_COLOR
                whitePieceColor = DEFAULT_WHITE_PIECE_COLOR
                blackPieceColor = DEFAULT_BLACK_PIECE_COLOR
                evalBarPosition = EvalBarPosition.RIGHT
                evalBarColor1 = DEFAULT_EVAL_BAR_COLOR_1
                evalBarColor2 = DEFAULT_EVAL_BAR_COLOR_2
                evalBarRange = 5
                // Save the default settings
                onSave(BoardLayoutSettings())
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to defaults")
        }

    }
}
