package com.eval

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.eval.ui.EvalNavHost
import com.eval.ui.GameViewModel
import com.eval.ui.theme.EvalTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var gameViewModel: GameViewModel

    @Suppress("DEPRECATION") // System-bar colors still apply before Android 15.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gameViewModel = ViewModelProvider(this)[GameViewModel::class.java]
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                gameViewModel.uiState.map { it.generalSettings.fullScreen }
                    .distinctUntilChanged()
                    .collect { applyFullScreen(it) }
            }
        }
        setContent {
            val fullScreen by remember {
                gameViewModel.uiState.map { it.generalSettings.fullScreen }.distinctUntilChanged()
            }.collectAsState(initial = gameViewModel.uiState.value.generalSettings.fullScreen)
            EvalTheme {
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    val color = background.toArgb()
                    window.statusBarColor = color
                    window.navigationBarColor = color
                    window.setBackgroundDrawable(ColorDrawable(color))
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = background.luminance() > 0.5f
                        isAppearanceLightNavigationBars = background.luminance() > 0.5f
                    }
                }
                // Match AI: fill the freed status-bar area, keep Android navigation,
                // and shrink content above the keyboard. Scaffold handles remaining
                // system insets; avoid a second system-bar/cutout padding layer.
                Box(Modifier.fillMaxSize().background(background)) {
                    Scaffold(
                        modifier = if (fullScreen) Modifier.fillMaxSize().imePadding()
                            else Modifier.fillMaxSize().statusBarsPadding().imePadding()
                    ) { innerPadding ->
                        EvalNavHost(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = gameViewModel
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::gameViewModel.isInitialized) {
            applyFullScreen(gameViewModel.uiState.value.generalSettings.fullScreen)
        }
    }

    private fun applyFullScreen(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.navigationBars())
        if (enabled) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
