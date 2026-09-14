package com.eval.ui

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDataSafetyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun incorrect_legacy_types_and_numeric_values_are_rejected_atomically() {
        val prefs = context.getSharedPreferences("legacy_settings_types_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("lichess_username", "KeepMe").commit()
        try {
            val settings = SettingsPreferences(prefs)
            val original = prefs.all.toMap()
            for (json in listOf(
                """{"lichess_username":{"_type":"Boolean","_value":true}}""",
                """{"manual_multipv":{"_type":"Int","_value":-1}}""",
                """{"manual_threads":{"_type":"Int","_value":1.5}}""",
                """{"preview_seconds":{"_type":"Float","_value":1e100}}""",
                """{"graph_line_scale":{"_type":"Int","_value":-100}}""",
                """{"ai_instructions_list":{"_type":"String","_value":"[null]"}}""",
                """{"ai_instructions_list":{"_type":"String","_value":"[{\"name\":null}]"}}""",
                """{"fen_history":{"_type":"String","_value":"[null]"}}"""
            )) {
                assertFalse("Accepted invalid import: $json", settings.importAllSettings(json))
                assertEquals(original, prefs.all)
                assertEquals("KeepMe", settings.savedLichessUsername)
            }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun typed_settings_reject_invalid_ranges_and_coerced_primitive_types() {
        val prefs = context.getSharedPreferences("typed_settings_validation_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("lichess_username", "KeepMe").commit()
        try {
            val settings = SettingsPreferences(prefs)
            val original = prefs.all.toMap()
            for (fields in listOf(
                """"stockfishSettings":{"manualStage":{"multiPv":-1}}""",
                """"stockfishSettings":{"previewStage":{"secondsForMove":0}}""",
                """"stockfishSettings":{"analyseStage":{"secondsForMove":1e100}}""",
                """"stockfishSettings":{"manualStage":{"threads":1.5}}""",
                """"graphSettings":{"lineGraphScale":-100}""",
                """"graphSettings":{"barGraphRange":0}""",
                """"generalSettings":{"moveSoundsEnabled":"yes"}""",
                """"lichessUsername":true""",
                """"fenHistory":[null]""",
                """"aiInstructions":[{"name":null}]"""
            )) {
                val json = "{\"schemaVersion\":3,$fields}"
                assertFalse("Accepted invalid import: $json", settings.importAllSettings(json))
                assertEquals(original, prefs.all)
            }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun importing_settings_preserves_saved_games_and_retrieve_history() {
        val prefs = context.getSharedPreferences("settings_data_safety_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            settings.saveLichessUsername("ImportedUser")
            val exported = settings.exportAllSettings()
            val gameData = mapOf(
                SettingsPreferences.KEY_CURRENT_MANUAL_GAME to "saved current game",
                SettingsPreferences.KEY_LIST_MANUAL_GAMES to "saved analysed games",
                SettingsPreferences.KEY_RETRIEVES_LIST to "saved retrieves index",
                "${SettingsPreferences.KEY_RETRIEVED_GAMES_PREFIX}lichess_tester" to "saved retrieved games"
            )
            gameData.forEach { (key, value) -> prefs.edit().putString(key, value).commit() }
            settings.saveLichessUsername("BeforeImport")
            assertTrue(settings.importAllSettings(exported))
            assertEquals("ImportedUser", settings.savedLichessUsername)
            gameData.forEach { (key, value) -> assertEquals(key, value, prefs.getString(key, null)) }
            assertTrue(settings.importAllSettings("""{"lichess_username":{"_type":"String","_value":"LegacyUser"}}"""))
            assertEquals("LegacyUser", settings.savedLichessUsername)
            gameData.forEach { (key, value) -> assertEquals(key, value, prefs.getString(key, null)) }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun invalid_imports_are_rejected_without_changing_preferences() {
        val prefs = context.getSharedPreferences("invalid_settings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("lichess_username", "KeepMe").commit()
        try {
            val settings = SettingsPreferences(prefs)
            val original = prefs.all.toMap()
            for (json in listOf("{}", "null", "[]", """{"unrelated":true}""",
                """{"schemaVersion":99}""", """{"schemaVersion":3,"stockfishSettings":null}""",
                """{"lichess_username":{"_type":"Unsupported","_value":"Bad"}}""")) {
                assertFalse("Accepted invalid import: $json", settings.importAllSettings(json))
                assertEquals("Changed preferences for $json", original, prefs.all)
            }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun startup_keeps_local_user_settings_before_remote_retrieval_and_after_upgrade() {
        val prefs = context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        try {
            for (savedVersion in listOf(0L, -1L)) {
                prefs.edit().clear().commit()
                val settings = SettingsPreferences(prefs)
                settings.setFirstGameRetrievedVersion(savedVersion)
                settings.saveGeneralSettings(GeneralSettings(moveSoundsEnabled = false, lichessUsername = "LocalUser"))
                settings.saveAiInstructions(listOf(AiInstructionEntry("local", "Local coach", "Keep this instruction")))
                settings.saveFenToHistory("4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17")
                prefs.edit().putString(SettingsPreferences.KEY_LIST_MANUAL_GAMES, "[]").commit()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                        assertFalse("Startup reset move sounds for version $savedVersion", vm.uiState.value.generalSettings.moveSoundsEnabled)
                        assertEquals("Local coach", vm.uiState.value.aiInstructions.single().name)
                        assertEquals(1, settings.loadFenHistory().size)
                        assertTrue(prefs.contains(SettingsPreferences.KEY_LIST_MANUAL_GAMES))
                    }
                }
            }
        } finally {
            val editor = prefs.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        }
    }
}
