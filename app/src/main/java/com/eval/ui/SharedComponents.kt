package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Reusable color setting row with label and color swatch.
 * Used across BoardLayoutSettingsScreen, GraphSettingsScreen, and ArrowSettingsScreen.
 */
@Composable
fun ColorSettingRow(
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

/**
 * Reusable settings toggle row with label and switch.
 * Used across BoardLayoutSettingsScreen, InterfaceSettingsScreen, and GeneralSettingsScreen.
 */
@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // The whole row is the switch, so TalkBack reads the label and tapping it toggles.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

/**
 * Number of list rows that fit in [available] once the pager controls ([reserved]) are placed,
 * for rows of [rowHeight] at the current font scale, so a page doesn't need scrolling.
 */
@Composable
fun rowsThatFit(available: Dp, rowHeight: Dp, reserved: Dp = 56.dp, minimum: Int = 3): Int {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    return ((available - reserved).value / (rowHeight.value * fontScale)).toInt().coerceAtLeast(minimum)
}
