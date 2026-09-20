package com.eval.ui

import android.content.Context
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullScreenSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun older_settings_default_to_windowed_and_full_screen_survives_export_import() {
        val name = "fullscreen_preferences_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            assertFalse(settings.loadGeneralSettings().fullScreen)
            settings.saveGeneralSettings(settings.loadGeneralSettings().copy(fullScreen = true))
            assertTrue(SettingsPreferences(prefs).loadGeneralSettings().fullScreen)
            val exported = settings.exportAllSettings()
            assertTrue(settings.importAllSettings("""{"schemaVersion":2,"generalSettings":{"moveSoundsEnabled":false}}"""))
            assertFalse(settings.loadGeneralSettings().fullScreen)
            assertFalse(settings.loadGeneralSettings().moveSoundsEnabled)
            assertTrue(settings.importAllSettings(exported))
            assertTrue(settings.loadGeneralSettings().fullScreen)
            assertTrue(settings.importAllSettings("""{"full_screen":{"_type":"Boolean","_value":false}}"""))
            assertFalse(settings.loadGeneralSettings().fullScreen)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun full_screen_hides_only_status_bar_and_survives_a_fresh_activity() {
        val prefs = context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val settings = SettingsPreferences(prefs)
        val previous = settings.exportAllSettings()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            settings.saveGeneralSettings(settings.loadGeneralSettings().copy(fullScreen = false))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            fun awaitBars(hidden: Boolean) {
                val deadline = System.currentTimeMillis() + 15000
                var matched = false
                while (!matched && System.currentTimeMillis() < deadline) {
                    scenario!!.onActivity { activity ->
                        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                        matched = insets != null &&
                            insets.isVisible(WindowInsetsCompat.Type.statusBars()) == !hidden &&
                            insets.isVisible(WindowInsetsCompat.Type.navigationBars())
                    }
                    if (!matched) Thread.sleep(50)
                }
                assertTrue("Status bar hidden=$hidden; navigation bar visible", matched)
            }
            lateinit var firstVm: GameViewModel
            awaitBars(false)
            scenario.onActivity { activity ->
                firstVm = ViewModelProvider(activity)[GameViewModel::class.java]
                firstVm.updateGeneralSettings(firstVm.uiState.value.generalSettings.copy(fullScreen = true))
            }
            awaitBars(true)
            assertTrue(settings.loadGeneralSettings().fullScreen)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitBars(true)
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                assertNotSame(firstVm, vm)
                assertTrue(vm.uiState.value.generalSettings.fullScreen)
            }
            awaitBars(true)
            scenario.recreate()
            awaitBars(true)
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                vm.updateGeneralSettings(vm.uiState.value.generalSettings.copy(fullScreen = false))
            }
            awaitBars(false)
            assertFalse(settings.loadGeneralSettings().fullScreen)
        } finally {
            scenario?.close()
            assertTrue(settings.importAllSettings(previous))
            prefs.edit().commit()
        }
    }
}
