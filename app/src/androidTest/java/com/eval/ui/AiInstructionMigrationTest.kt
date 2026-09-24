package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiInstructionMigrationTest {
    private val legacy = """[{"id":"old-id","name":"Coach","prompt":"old model text","system":"old system text","instructions":"<select>","email":"test@example.com","category":"GAME"}]"""

    @Test fun upgrade_keeps_names_and_instructions_then_removes_old_prompt_storage() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("instructions_migration_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("ai_prompts_list", legacy).commit()
        try {
            val settings = SettingsPreferences(prefs)
            val entries = settings.loadAiInstructions()
            assertEquals(listOf(AiInstructionEntry("old-id", "Coach", "<select>\n<email>test@example.com</email>")), entries)
            assertFalse(prefs.contains("ai_prompts_list"))
            assertFalse(prefs.getString("ai_instructions_list", "")!!.contains("old model text"))
            assertEquals(entries, SettingsPreferences(prefs).loadAiInstructions())
            settings.saveAiInstructions(emptyList())
            assertTrue(SettingsPreferences(prefs).loadAiInstructions().isEmpty())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun imports_v2_and_round_trips_in_v5() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("instructions_import_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            assertTrue(settings.importAllSettings("""{"schemaVersion":2,"aiPrompts":$legacy}"""))
            val expected = settings.loadAiInstructions()
            assertEquals("Coach", expected.single().name)
            val exported = settings.exportAllSettings()
            assertFalse(exported.contains("old model text"))
            assertFalse(exported.contains("old system text"))
            assertTrue(exported.contains("\"schemaVersion\":5"))
            settings.saveAiInstructions(emptyList())
            assertTrue(settings.importAllSettings(exported))
            assertEquals(expected, settings.loadAiInstructions())
        } finally { prefs.edit().clear().commit() }
    }
}
