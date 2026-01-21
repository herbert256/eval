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
 * Graph settings screen for configuring evaluation graph appearance.
 */
@Composable
fun GraphSettingsScreen(
    graphSettings: GraphSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (GraphSettings) -> Unit
) {
    var plusScoreColor by remember { mutableStateOf(graphSettings.plusScoreColor) }
    var negativeScoreColor by remember { mutableStateOf(graphSettings.negativeScoreColor) }
    var backgroundColor by remember { mutableStateOf(graphSettings.backgroundColor) }
    var analyseLineColor by remember { mutableStateOf(graphSettings.analyseLineColor) }
    var verticalLineColor by remember { mutableStateOf(graphSettings.verticalLineColor) }

    var showPlusScoreColorPicker by remember { mutableStateOf(false) }
    var showNegativeScoreColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showAnalyseLineColorPicker by remember { mutableStateOf(false) }
    var showVerticalLineColorPicker by remember { mutableStateOf(false) }

    fun saveSettings(
        newPlusScoreColor: Long = plusScoreColor,
        newNegativeScoreColor: Long = negativeScoreColor,
        newBackgroundColor: Long = backgroundColor,
        newAnalyseLineColor: Long = analyseLineColor,
        newVerticalLineColor: Long = verticalLineColor
    ) {
        onSave(GraphSettings(
            plusScoreColor = newPlusScoreColor,
            negativeScoreColor = newNegativeScoreColor,
            backgroundColor = newBackgroundColor,
            analyseLineColor = newAnalyseLineColor,
            verticalLineColor = newVerticalLineColor
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
            text = "Graph settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)

        Spacer(modifier = Modifier.height(8.dp))

        // Plus score color picker
        ColorSettingRow(
            label = "Plus score color",
            color = Color(plusScoreColor.toInt()),
            onClick = { showPlusScoreColorPicker = true }
        )

        // Negative score color picker
        ColorSettingRow(
            label = "Negative score color",
            color = Color(negativeScoreColor.toInt()),
            onClick = { showNegativeScoreColorPicker = true }
        )

        // Background color picker
        ColorSettingRow(
            label = "Background color",
            color = Color(backgroundColor.toInt()),
            onClick = { showBackgroundColorPicker = true }
        )

        // Analyse line color picker
        ColorSettingRow(
            label = "Line color in Analyse stage",
            color = Color(analyseLineColor.toInt()),
            onClick = { showAnalyseLineColorPicker = true }
        )

        // Vertical line color picker
        ColorSettingRow(
            label = "Vertical line color",
            color = Color(verticalLineColor.toInt()),
            onClick = { showVerticalLineColorPicker = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reset to defaults button
        Button(
            onClick = {
                plusScoreColor = DEFAULT_GRAPH_PLUS_SCORE_COLOR
                negativeScoreColor = DEFAULT_GRAPH_NEGATIVE_SCORE_COLOR
                backgroundColor = DEFAULT_GRAPH_BACKGROUND_COLOR
                analyseLineColor = DEFAULT_GRAPH_ANALYSE_LINE_COLOR
                verticalLineColor = DEFAULT_GRAPH_VERTICAL_LINE_COLOR
                onSave(GraphSettings())
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to defaults")
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButtons(onBackToSettings = onBackToSettings, onBackToGame = onBackToGame)
    }

    // Color picker dialogs
    if (showPlusScoreColorPicker) {
        ColorPickerDialog(
            currentColor = plusScoreColor,
            title = "Plus score color",
            onColorSelected = { color ->
                plusScoreColor = color
                saveSettings(newPlusScoreColor = color)
            },
            onDismiss = { showPlusScoreColorPicker = false }
        )
    }

    if (showNegativeScoreColorPicker) {
        ColorPickerDialog(
            currentColor = negativeScoreColor,
            title = "Negative score color",
            onColorSelected = { color ->
                negativeScoreColor = color
                saveSettings(newNegativeScoreColor = color)
            },
            onDismiss = { showNegativeScoreColorPicker = false }
        )
    }

    if (showBackgroundColorPicker) {
        ColorPickerDialog(
            currentColor = backgroundColor,
            title = "Background color",
            onColorSelected = { color ->
                backgroundColor = color
                saveSettings(newBackgroundColor = color)
            },
            onDismiss = { showBackgroundColorPicker = false }
        )
    }

    if (showAnalyseLineColorPicker) {
        ColorPickerDialog(
            currentColor = analyseLineColor,
            title = "Line color in Analyse stage",
            onColorSelected = { color ->
                analyseLineColor = color
                saveSettings(newAnalyseLineColor = color)
            },
            onDismiss = { showAnalyseLineColorPicker = false }
        )
    }

    if (showVerticalLineColorPicker) {
        ColorPickerDialog(
            currentColor = verticalLineColor,
            title = "Vertical line color",
            onColorSelected = { color ->
                verticalLineColor = color
                saveSettings(newVerticalLineColor = color)
            },
            onDismiss = { showVerticalLineColorPicker = false }
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
