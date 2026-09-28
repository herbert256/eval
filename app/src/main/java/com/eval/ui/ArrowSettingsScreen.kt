package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role

/**
 * Arrow settings screen for configuring move arrow display options.
 */
@Composable
fun ArrowSettingsScreen(
    stockfishSettings: StockfishSettings,
    onBackToSettings: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (StockfishSettings) -> Unit
) {
    var arrowMode by remember { mutableStateOf(stockfishSettings.manualStage.arrowMode) }
    // Main line settings
    var numArrows by remember { mutableStateOf(stockfishSettings.manualStage.numArrows) }
    var showArrowNumbers by remember { mutableStateOf(stockfishSettings.manualStage.showArrowNumbers) }
    var whiteArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.whiteArrowColor) }
    var blackArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.blackArrowColor) }
    // Multi lines settings
    var multiLinesArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.multiLinesArrowColor) }

    // Color picker state
    // Only the open picker's title is saved, so it survives rotation; the target is looked up below.
    var activeColorPicker by rememberSaveable { mutableStateOf<String?>(null) }

    val numArrowsOptions = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    val arrowModeOptions = listOf(
        ArrowMode.NONE to "None",
        ArrowMode.MAIN_LINE to "Main line",
        ArrowMode.MULTI_LINES to "Multi lines"
    )

    fun saveSettings(
        newArrowMode: ArrowMode = arrowMode,
        newNumArrows: Int = numArrows,
        newShowArrowNumbers: Boolean = showArrowNumbers,
        newWhiteArrowColor: Long = whiteArrowColor,
        newBlackArrowColor: Long = blackArrowColor,
        newMultiLinesArrowColor: Long = multiLinesArrowColor
    ) {
        onSave(stockfishSettings.copy(
            manualStage = stockfishSettings.manualStage.copy(
                arrowMode = newArrowMode,
                numArrows = newNumArrows,
                showArrowNumbers = newShowArrowNumbers,
                whiteArrowColor = newWhiteArrowColor,
                blackArrowColor = newBlackArrowColor,
                multiLinesArrowColor = newMultiLinesArrowColor
            )
        ))
    }

    // Full-screen color picker (early return pattern)
    val colorTarget: Pair<Long, (Long) -> Unit>? = when (activeColorPicker) {
        "Arrow color for white moves" -> whiteArrowColor to { color: Long -> whiteArrowColor = color; saveSettings(newWhiteArrowColor = color) }
        "Arrow color for black moves" -> blackArrowColor to { color: Long -> blackArrowColor = color; saveSettings(newBlackArrowColor = color) }
        "Arrow color for multi lines" -> multiLinesArrowColor to { color: Long -> multiLinesArrowColor = color; saveSettings(newMultiLinesArrowColor = color) }
        else -> null
    }
    if (colorTarget != null) {
        ColorPickerDialog(
            currentColor = colorTarget.first,
            title = activeColorPicker.orEmpty(),
            onColorSelected = { newColor -> colorTarget.second(newColor) },
            onDismiss = { activeColorPicker = null }
        )
        return
    }

    EvalScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = {
            EvalTitleBar(
                title = "Arrow settings",
                onBackClick = onBackToSettings,
                onEvalClick = onBackToGame
            )
        }
    ) {

        Spacer(modifier = Modifier.height(8.dp))

        // ===== CARD 1: Arrow Mode - inline radio buttons =====
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
                Text("Draw arrows", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    arrowModeOptions.forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // The row is the radio button: TalkBack reads its label and tapping the label selects it.
                            modifier = Modifier.weight(1f).selectable(
                                selected = arrowMode == mode,
                                role = Role.RadioButton,
                                onClick = {
                                    arrowMode = mode
                                    saveSettings(newArrowMode = mode)
                                }
                            )
                        ) {
                            RadioButton(selected = arrowMode == mode, onClick = null)
                            Text(label, color = Color.White)
                        }
                    }
                }
            }
        }

        // ===== CARD 2: Main Line Settings =====
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
                    text = "Main line",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Number of arrows - inline radio buttons
                Text("Number of arrows", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    numArrowsOptions.forEach { num ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // The row is the radio button: TalkBack reads its label and tapping the label selects it.
                            modifier = Modifier.selectable(
                                selected = numArrows == num,
                                role = Role.RadioButton,
                                onClick = {
                                    numArrows = num
                                    saveSettings(newNumArrows = num)
                                }
                            )
                        ) {
                            RadioButton(selected = numArrows == num, onClick = null)
                            Text(num.toString(), color = Color.White)
                        }
                    }
                }

                // Show move number in arrow - toggle switch
                SettingsToggle(
                    label = "Show move number in arrow",
                    checked = showArrowNumbers,
                    onCheckedChange = {
                        showArrowNumbers = it
                        saveSettings(newShowArrowNumbers = it)
                    }
                )

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
                            .clickable {
                                activeColorPicker = "Arrow color for white moves"
                            }
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
                            .clickable {
                                activeColorPicker = "Arrow color for black moves"
                            }
                    )
                }
            }
        }

        // ===== CARD 3: Multi Lines Settings =====
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
                    text = "Multi lines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Color picker for multi lines arrows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Arrow color")
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(multiLinesArrowColor.toInt()))
                            .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .clickable {
                                activeColorPicker = "Arrow color for multi lines"
                            }
                    )
                }
            }
        }

    }
}
