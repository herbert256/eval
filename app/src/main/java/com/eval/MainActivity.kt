package com.eval

import android.content.Intent
import android.content.ClipboardManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import com.eval.data.SharedChessInput
import com.eval.data.ClipboardHistory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var gameViewModel: GameViewModel
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (window.decorView.hasWindowFocus()) ClipboardHistory.get(this).captureCurrent()
    }

    override fun onStart() {
        super.onStart()
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onStop() {
        clipboard.removePrimaryClipChangedListener(clipboardListener)
        super.onStop()
    }

    @Suppress("DEPRECATION") // System-bar colors still apply before Android 15.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Activity 1.8 does not opt into the cutout area. Unlike AI's newer
        // edge-to-edge setup, Android then moves the entire window below the
        // hidden status bar. Let Compose own the safe insets for both modes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        gameViewModel = ViewModelProvider(this)[GameViewModel::class.java]
        // Keep a pending share across rotation, and restore it after process death.
        // Once dismissed/opened, recreating the activity must not import it again.
        if (savedInstanceState?.getBoolean("sharedImportConsumed") != true && gameViewModel.sharedImport.value == null) {
            receiveShare(intent)
        }
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
                // Full screen places the menu at y=0, including on devices with a
                // top cutout. Keep side/bottom safety and apply keyboard insets once.
                Box(Modifier.fillMaxSize().background(background)) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentWindowInsets = if (fullScreen) WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        ) else WindowInsets.safeDrawing
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("sharedImportConsumed", gameViewModel.sharedImport.value == null)
        super.onSaveInstanceState(outState)
    }

    private fun receiveShare(intent: Intent) {
        val input = SharedChessInput.fromIntent(intent) ?: return
        // A launcher intent must not discard the payload needed to restore a pending share.
        setIntent(intent)
        gameViewModel.receiveSharedContent(input)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::gameViewModel.isInitialized) {
            applyFullScreen(gameViewModel.uiState.value.generalSettings.fullScreen)
            ClipboardHistory.get(this).captureCurrent()
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
