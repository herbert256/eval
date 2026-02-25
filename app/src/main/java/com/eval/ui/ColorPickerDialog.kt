package com.eval.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Full-screen color picker with hue/saturation grid, brightness slider, and opacity slider.
 */
@Composable
fun ColorPickerDialog(
    currentColor: Long,
    title: String,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Extract HSV from current color
    val initialColor = Color(currentColor.toInt())
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (initialColor.red * 255).toInt(),
        (initialColor.green * 255).toInt(),
        (initialColor.blue * 255).toInt(),
        hsv
    )

    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var brightness by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableStateOf(initialColor.alpha) }

    // Compute current color from HSV
    val currentHsvColor = remember(hue, saturation, brightness, alpha) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        Color(
            red = android.graphics.Color.red(rgb) / 255f,
            green = android.graphics.Color.green(rgb) / 255f,
            blue = android.graphics.Color.blue(rgb) / 255f,
            alpha = alpha
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = title,
            onBackClick = onDismiss,
            onEvalClick = onDismiss
        )

        // Color preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(currentHsvColor)
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
        )

        // Hue/Saturation picker (2D grid)
        Text("Color", style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
        ) {
            // Cache gradient bitmap to avoid redrawing thousands of rects on every touch
            var cachedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            var cachedBrightness by remember { mutableFloatStateOf(-1f) }
            var cachedWidth by remember { mutableIntStateOf(0) }
            var cachedHeight by remember { mutableIntStateOf(0) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                            saturation = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val offset = change.position
                            hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                            saturation = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        }
                    }
            ) {
                val w = size.width.toInt()
                val h = size.height.toInt()
                if (w > 0 && h > 0) {
                    // Regenerate bitmap only when brightness or size changes
                    if (cachedBitmap == null || cachedBrightness != brightness || cachedWidth != w || cachedHeight != h) {
                        val step = 4
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        val paint = android.graphics.Paint()
                        val hsvValues = floatArrayOf(0f, 0f, brightness)
                        for (x in 0 until w step step) {
                            for (y in 0 until h step step) {
                                hsvValues[0] = x.toFloat() / w * 360f
                                hsvValues[1] = 1f - y.toFloat() / h
                                paint.color = android.graphics.Color.HSVToColor(hsvValues)
                                canvas.drawRect(x.toFloat(), y.toFloat(), (x + step).toFloat(), (y + step).toFloat(), paint)
                            }
                        }
                        cachedBitmap = bmp
                        cachedBrightness = brightness
                        cachedWidth = w
                        cachedHeight = h
                    }
                    cachedBitmap?.let { bmp ->
                        drawImage(
                            bmp.asImageBitmap(),
                            topLeft = Offset.Zero
                        )
                    }
                }
                // Draw crosshair at current position
                val crossX = hue / 360f * size.width
                val crossY = (1f - saturation) * size.height
                drawCircle(Color.White, 8f, Offset(crossX, crossY))
                drawCircle(Color.Black, 6f, Offset(crossX, crossY))
            }
        }

        // Brightness slider
        GradientSlider(
            label = "Brightness",
            value = brightness,
            onValueChange = { brightness = it }
        ) { size ->
            for (x in 0 until size.width.toInt()) {
                val b = x / size.width
                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, b))
                drawLine(
                    Color(rgb),
                    Offset(x.toFloat(), 0f),
                    Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
        }

        // Alpha/Opacity slider
        GradientSlider(
            label = "Opacity",
            value = alpha,
            onValueChange = { alpha = it }
        ) { size ->
            // Draw checkerboard background for transparency
            val checkSize = 8f
            for (x in 0 until (size.width / checkSize).toInt()) {
                for (y in 0 until (size.height / checkSize).toInt()) {
                    val isLight = (x + y) % 2 == 0
                    drawRect(
                        if (isLight) Color.White else Color.LightGray,
                        Offset(x * checkSize, y * checkSize),
                        Size(checkSize, checkSize)
                    )
                }
            }
            // Draw alpha gradient
            val baseRgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
            val baseColor = Color(baseRgb)
            for (x in 0 until size.width.toInt()) {
                val a = x / size.width
                drawLine(
                    baseColor.copy(alpha = a),
                    Offset(x.toFloat(), 0f),
                    Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(onClick = {
                // Convert to Long color value
                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
                val r = android.graphics.Color.red(rgb)
                val g = android.graphics.Color.green(rgb)
                val b = android.graphics.Color.blue(rgb)
                val a = (alpha * 255).toInt()
                val colorLong = ((a.toLong() and 0xFF) shl 24) or
                        ((r.toLong() and 0xFF) shl 16) or
                        ((g.toLong() and 0xFF) shl 8) or
                        (b.toLong() and 0xFF)
                onColorSelected(colorLong)
                onDismiss()
            }) {
                Text("Select")
            }
        }
    }
}

/**
 * Reusable gradient slider composable for color picker controls.
 * Handles tap and horizontal drag gestures to update a 0..1 value,
 * draws a custom gradient via [gradientDrawer], and renders an indicator line.
 */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    gradientDrawer: DrawScope.(Size) -> Unit
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            gradientDrawer(size)
            // Draw indicator
            val indicatorX = value * size.width
            drawLine(Color.White, Offset(indicatorX, 0f), Offset(indicatorX, size.height), 3f)
            drawLine(Color.Black, Offset(indicatorX, 0f), Offset(indicatorX, size.height), 1f)
        }
    }
}
