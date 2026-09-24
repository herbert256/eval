package com.eval.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import com.eval.R
import kotlin.math.roundToInt

/** App destinations are available to every screen, including nested editors. */
internal data class EvalMenuActions(
    val home: () -> Unit,
    val selectGame: () -> Unit,
    val settings: () -> Unit,
    val help: () -> Unit,
    val reload: (() -> Unit)? = null
)

internal val LocalEvalMenuActions = staticCompositionLocalOf<EvalMenuActions?> { null }

@OptIn(ExperimentalMaterial3Api::class)
internal val LocalEvalTitleScrollState = staticCompositionLocalOf<TopAppBarState?> { null }

/** Shared fixed icon menu with a title row that scrolls out of view. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EvalTitleBar(
    title: String? = null,
    onBackClick: (() -> Unit)? = null,
    onEvalClick: () -> Unit = {},
    onAiClick: (() -> Unit)? = null
) {
    // Former on-screen Back callbacks also cover nested pages without their
    // own handler (colour pickers, sharing and progress screens).
    BackHandler(enabled = onBackClick != null) { onBackClick?.invoke() }
    val actions = LocalEvalMenuActions.current
    val slots: List<@Composable () -> Unit> = buildList {
        add { BrandButton(R.drawable.eval_brand_glyph, "Eval home", actions?.home ?: onEvalClick) }
        if (actions != null) {
            add { MenuIcon("📂", "Select a game", actions.selectGame) }
            actions.reload?.let { reload -> add { MenuIcon("🔄", "Reload latest game", reload) } }
        }
        onAiClick?.let { ai -> add { BrandButton(R.drawable.ai_brand_glyph, "Start AI report", ai) } }
        if (actions != null) {
            add { MenuIcon("⚙️", "Settings", actions.settings) }
            add { MenuIcon("❓", "Help", actions.help) }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().testTag("eval_top_bar")) {
            MenuIconRow(slots)
            val density = LocalDensity.current
            val cutoutTop = WindowInsets.displayCutout.getTop(density)
            if (WindowInsets.statusBars.getTop(density) == 0) {
                // Keep content below a tall camera cutout as the title scrolls.
                Spacer(Modifier.height((with(density) { cutoutTop.toDp() } - 48.dp).coerceAtLeast(0.dp)))
            }
        }
        val scrollState = LocalEvalTitleScrollState.current
        if (!title.isNullOrBlank()) {
            Column(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                val offset = scrollState?.heightOffset?.coerceIn(-child.height.toFloat(), 0f)?.roundToInt() ?: 0
                layout(child.width, child.height + offset) { child.placeRelative(0, offset) }
            }.onSizeChanged { scrollState?.heightOffsetLimit = -it.height.toFloat() }) {
                Text(
                    title,
                    modifier = Modifier.fillMaxWidth().testTag("eval_screen_title")
                        .padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
            }
        } else {
            SideEffect {
                // Returning to Manual must not consume scrolling for a title that no longer exists.
                scrollState?.heightOffsetLimit = 0f
                scrollState?.heightOffset = 0f
            }
        }
    }
}

@Composable
private fun BrandButton(drawable: Int, description: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(drawable), description, Modifier.fillMaxSize().padding(3.dp))
    }
}

@Composable
private fun MenuIcon(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Use the hidden status-bar area, leaving any physical camera hole between icons. */
@Composable
private fun MenuIconRow(slots: List<@Composable () -> Unit>) {
    val view = LocalView.current
    val density = LocalDensity.current
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    val statusTop = WindowInsets.statusBars.getTop(density)
    var windowX by remember { mutableFloatStateOf(0f) }
    var windowY by remember { mutableFloatStateOf(0f) }
    val cutout = if (cutoutTop > 0 && statusTop == 0) {
        ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects?.firstOrNull {
            it.top < windowY + with(density) { 48.dp.toPx() } && it.bottom > windowY
        }
    } else null
    Layout(
        content = { slots.forEach { slot -> Box { slot() } } },
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("eval_menu_icons")
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                windowX = bounds.left
                windowY = bounds.top
            }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inset = 4.dp.roundToPx()
        val gapStart = cutout?.let { (it.left - windowX).roundToInt().coerceIn(inset, width - inset) }
        val gapEnd = cutout?.let { (it.right - windowX).roundToInt().coerceIn(inset, width - inset) }
        val count = measurables.size
        val regions = if (gapStart != null && gapEnd != null) {
            val left = gapStart - inset
            val right = width - inset - gapEnd
            val leftCount = (count * left.toFloat() / (left + right).coerceAtLeast(1)).roundToInt().coerceIn(0, count)
            List(leftCount) { i -> inset + left * i / leftCount to left / leftCount } +
                List(count - leftCount) { i -> gapEnd + right * i / (count - leftCount) to right / (count - leftCount) }
        } else {
            List(count) { i -> inset + (width - inset * 2) * i / count to (width - inset * 2) / count }
        }
        val children = measurables.mapIndexed { i, measurable ->
            measurable.measure(Constraints.fixed(regions[i].second.coerceAtLeast(1), height))
        }
        layout(width, height) {
            children.forEachIndexed { i, child -> child.place(regions[i].first, 0) }
        }
    }
}
