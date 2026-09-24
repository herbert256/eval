package com.eval.ui

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real window geometry: Compose padding alone cannot reclaim a letterboxed cutout. */
@RunWith(AndroidJUnit4::class)
class FullScreenCutoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    @Test fun hidden_status_bar_area_is_used_on_a_cutout_display_after_toggles_and_recreation() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val overlay = "com.android.internal.display.cutout.emulation.tall"
        val overlays = shell("cmd overlay list --user 0")
        assumeTrue("Requires the Android emulator cutout overlay", overlays.contains(overlay))
        val alreadyEnabled = overlays.lineSequence().any { it.trim() == "[x] $overlay" }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
        val previous = settings.exportAllSettings()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            if (!alreadyEnabled) shell("cmd overlay enable --user 0 $overlay")
            settings.saveGeneralSettings(settings.loadGeneralSettings().copy(fullScreen = true))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { ViewModelProvider(it)[GameViewModel::class.java].dismissAiAppWarning() }
            fun awaitGeometry(fullScreen: Boolean) {
                val deadline = SystemClock.uptimeMillis() + 15_000
                var measured = "No window yet"
                do {
                    var matched = false
                    val header = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                        .singleOrNull { it.isVisibleToUser && it.viewIdResourceName == "eval_top_bar" }
                    val top = header?.let { Rect().also(it::getBoundsInScreen).top }
                    scenario!!.onActivity { activity ->
                        val decor = activity.window.decorView
                        val insets = ViewCompat.getRootWindowInsets(decor)
                        val origin = IntArray(2).also(decor::getLocationOnScreen)
                        val cutout = insets?.displayCutout?.safeInsetTop ?: 0
                        val expected = if (fullScreen) 0 else insets?.getInsets(
                            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top
                        measured = "windowY=${origin[1]}, menuY=$top, expected=$expected, cutout=$cutout"
                        matched = cutout > 0 && origin[1] == 0 && top == expected &&
                            insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == !fullScreen &&
                            insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) == true
                    }
                    if (matched) {
                        if (fullScreen) {
                            var cutouts = emptyList<Rect>()
                            scenario!!.onActivity { cutouts = ViewCompat.getRootWindowInsets(it.window.decorView)?.displayCutout?.boundingRects.orEmpty() }
                            val menu = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                                .singleOrNull { it.isVisibleToUser && it.viewIdResourceName == "eval_menu_icons" }
                            val buttons = nodes(menu).filter { it.isClickable && it.isVisibleToUser }
                            if (buttons.isEmpty()) { SystemClock.sleep(100); continue }
                            val overlaps = buttons.any { button ->
                                val bounds = Rect().also(button::getBoundsInScreen)
                                cutouts.any { Rect.intersects(it, bounds) }
                            }
                            val title = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                                .singleOrNull { it.isVisibleToUser && it.viewIdResourceName == "eval_screen_title" }
                            val titleOverlaps = title?.let { node ->
                                val bounds = Rect().also(node::getBoundsInScreen)
                                cutouts.any { Rect.intersects(it, bounds) }
                            } == true
                            if (overlaps || titleOverlaps) { measured += ", menu/title overlaps camera cutout"; SystemClock.sleep(100); continue }
                        }
                        return
                    }
                    SystemClock.sleep(100)
                } while (SystemClock.uptimeMillis() < deadline)
                fail("Full screen=$fullScreen must reclaim the window's top edge: $measured")
            }
            awaitGeometry(true)
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                vm.updateGeneralSettings(vm.uiState.value.generalSettings.copy(fullScreen = false))
            }
            awaitGeometry(false)
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                vm.updateGeneralSettings(vm.uiState.value.generalSettings.copy(fullScreen = true))
            }
            awaitGeometry(true)
            scenario.recreate()
            awaitGeometry(true)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitGeometry(true)
        } finally {
            scenario?.close()
            assertTrue(settings.importAllSettings(previous))
            if (!alreadyEnabled) shell("cmd overlay disable --user 0 $overlay")
        }
    }
}
