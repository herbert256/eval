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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Generic title bar used across all screens in the app.
 * Shows a back button on the left, optional title in the middle, and "Eval" on the right.
 *
 * @param title The screen title to display (optional)
 * @param onBackClick Callback when back button is clicked (if null, no back button shown)
 * @param onEvalClick Callback when "Eval" text is clicked (typically navigates to home)
 * @param backText The text/icon for the back button (default: "< Back")
 * @param leftContent Optional custom content for the left side (replaces back button if provided)
 */
@Composable
fun EvalTitleBar(
    title: String? = null,
    onBackClick: (() -> Unit)? = null,
    onEvalClick: () -> Unit = {},
    backText: String = "< Back",
    leftContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: custom content or back button
        if (leftContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                leftContent()
            }
        } else if (onBackClick != null) {
            Text(
                text = backText,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onBackClick() }
            )
        }

        // Middle: title (if provided) - takes remaining space
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Right side: "Eval" branding
        Text(
            text = "Eval",
            fontSize = 24.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable { onEvalClick() }
        )
    }
}

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
 * Title bar icon button used in the Game screen.
 */
@Composable
fun TitleBarIcon(
    icon: String,
    onClick: () -> Unit,
    fontSize: Int = 26,
    size: Int = 38,
    offsetY: Int = -3
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = fontSize.sp,
            color = Color.White,
            modifier = Modifier.offset(y = offsetY.dp)
        )
    }
}
