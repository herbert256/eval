package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Common layout for every Eval screen. Only the icon menu stays fixed. The
 * title scrolls away before the body, including nested editors and lazy lists.
 * System/keyboard insets are owned by MainActivity.
 * Screens with a LazyColumn or their own scrolling region leave [scrollable] false.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
internal fun EvalScreen(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    horizontalPadding: Dp = 16.dp,
    scrollable: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val titleScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CompositionLocalProvider(LocalEvalTitleScrollState provides titleScroll.state) {
        Column(modifier.fillMaxSize().background(backgroundColor)
            .nestedScroll(titleScroll.nestedScrollConnection)
            .semantics { testTagsAsResourceId = true }) {
            Box(Modifier.fillMaxWidth()) { topBar() }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .padding(start = horizontalPadding, end = horizontalPadding, bottom = 16.dp)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    }
}
