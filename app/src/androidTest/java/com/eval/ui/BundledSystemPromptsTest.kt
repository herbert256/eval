package com.eval.ui

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSystemPromptsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val defaults get() = BundledAiPrompts.loadSystemPrompts(context.assets)
    private val originalDefaults get(): List<AiPromptEntry> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.list("system-prompts-v1")!!.sorted().map { file ->
            val obj = assets.open("system-prompts-v1/$file").bufferedReader().use {
                JsonParser().parse(it.readText()).asJsonObject
            }
            AiPromptEntry("bundled-system-prompt:${file.removeSuffix(".json")}",
                obj.get("title").asString, obj.get("text").asString)
        }
    }
    private fun isolated(test: (SettingsPreferences, android.content.SharedPreferences) -> Unit) {
        val prefs = context.getSharedPreferences("bundled_system_prompts_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try { test(SettingsPreferences(prefs), prefs) }
        finally { prefs.edit().clear().commit() }
    }

    @Test fun apk_contains_four_compact_system_prompts_with_stable_names_and_ids() {
        assertEquals(setOf("Professional Coach", "Friendly talkative chess coach", "Grumpy old GM", "Youtuber"),
            defaults.map { it.name }.toSet())
        assertEquals(4, defaults.size)
        assertEquals(4, defaults.map { it.id }.distinct().size)
        context.assets.list("system_prompts")!!.forEach { name ->
            val obj = context.assets.open("system_prompts/$name").bufferedReader().use {
                JsonParser().parse(it.readText()).asJsonObject
            }
            assertEquals(setOf("title", "text"), obj.keySet())
            assertTrue(obj.get("text").asString.length in 1..500)
        }
        assertEquals(originalDefaults.map { it.id to it.name }, defaults.map { it.id to it.name })
        defaults.forEach { prompt ->
            val original = originalDefaults.single { it.id == prompt.id }
            assertTrue("${prompt.name} should use at least 70% less text", prompt.text.length < original.text.length * .3)
        }
    }

    @Test fun unchanged_long_defaults_update_even_after_restore_and_keep_the_last_selection() = isolated { settings, prefs ->
        settings.seedAiSystemPrompts(originalDefaults)
        val prompts = listOf(AiPromptEntry("question", "Position", "Analyse @FEN@"))
        val instructions = listOf(AiInstructionEntry("options", "Options", "<next>View</next>"))
        settings.saveAiSetup(originalDefaults, prompts, instructions)
        val selection = AiReportSelection(originalDefaults.first().id, "question", "options")
        settings.saveAiReportSelection(selection)
        val oldBackup = settings.exportAllSettings()

        repeat(2) {
            settings.seedAiSystemPrompts(defaults)
            assertEquals(defaults, settings.loadAiSystemPrompts())
            assertEquals(selection, settings.loadAiReportSelection())
            assertEquals(prompts, settings.loadAiReportPrompts())
            assertEquals(instructions, settings.loadAiInstructions())
            val saved = prefs.all.toMap()
            SettingsPreferences(prefs).seedAiSystemPrompts(defaults)
            assertEquals("Repeated seeding is a no-op", saved, prefs.all)
            if (it == 0) {
                prefs.edit().clear().commit()
                assertTrue(settings.importAllSettings(oldBackup))
            }
        }
    }

    @Test fun shortening_preserves_edited_renamed_copied_and_deleted_system_prompts() = isolated { settings, prefs ->
        val old = originalDefaults
        settings.seedAiSystemPrompts(old)
        val edited = old[0].copy(text = old[0].text + "\nUse French.")
        val renamed = old[1].copy(name = "My grumpy coach")
        // Delete the bundled third entry, keeping a manually created copy under its old name.
        val copied = old[2].copy(id = "my-copy")
        settings.saveAiSetup(listOf(copied, edited, renamed, old[3]), emptyList(), emptyList())
        val expected = listOf(copied, edited, renamed, defaults.single { it.id == old[3].id })
        settings.seedAiSystemPrompts(defaults)
        assertEquals(expected, settings.loadAiSystemPrompts())
        val backup = settings.exportAllSettings()
        prefs.edit().clear().commit()
        assertTrue(settings.importAllSettings(backup))
        SettingsPreferences(prefs).seedAiSystemPrompts(defaults)
        assertEquals(expected, settings.loadAiSystemPrompts())
    }

    @Test fun fresh_settings_are_seeded_on_activity_start_and_survive_another_start() {
        val prefs = context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val settings = SettingsPreferences(prefs)
        val previous = settings.exportAllSettings()
        try {
            // Clear only settings: saved games are unrelated and must remain untouched.
            settings.resetAllSettingsToDefaults()
            assertFalse(prefs.contains("ai_system_prompts"))
            assertFalse(prefs.contains("ai_report_prompts"))
            val reportDefaults = BundledAiPrompts.loadReportPrompts(context.assets)
            repeat(2) {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        val state = ViewModelProvider(activity)[GameViewModel::class.java].uiState.value
                        assertEquals(defaults, settings.loadAiSystemPrompts())
                        assertEquals(defaults, state.aiSystemPrompts)
                        assertEquals(reportDefaults, settings.loadAiReportPrompts())
                        assertEquals(reportDefaults, state.aiReportPrompts)
                    }
                }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    @Test fun upgrade_preserves_custom_prompts_same_name_entries_and_instruction_references() = isolated { settings, prefs ->
        val custom = AiPromptEntry("custom", "My coach", "My text")
        val coach = AiPromptEntry("existing-coach", "Professional Coach", "My edited coaching instructions")
        val instruction = AiInstructionEntry("instruction", "Report", "<next>View</next>")
        settings.saveAiSetup(listOf(custom, coach), emptyList(), listOf(instruction))
        settings.seedAiSystemPrompts(defaults)
        val seeded = settings.loadAiSystemPrompts()
        assertEquals(5, seeded.size)
        assertEquals(coach, seeded.single { it.name == coach.name })
        assertTrue(seeded.contains(custom))
        assertEquals(listOf(instruction), settings.loadAiInstructions())
        SettingsPreferences(prefs).seedAiSystemPrompts(defaults)
        assertEquals(seeded, settings.loadAiSystemPrompts())
    }

    @Test fun edits_deletions_and_seed_history_survive_reload_and_export_import() = isolated { settings, prefs ->
        settings.seedAiSystemPrompts(defaults)
        val kept = defaults.drop(1).mapIndexed { index, entry ->
            if (index == 0) entry.copy(name = "My edited coach", text = "Keep my edits") else entry
        }
        settings.saveAiSetup(kept, emptyList(), emptyList())
        val reloaded = SettingsPreferences(prefs)
        reloaded.seedAiSystemPrompts(defaults)
        assertEquals(kept, reloaded.loadAiSystemPrompts())
        val exported = reloaded.exportAllSettings()
        prefs.edit().clear().commit()
        assertTrue(settings.importAllSettings(exported))
        SettingsPreferences(prefs).seedAiSystemPrompts(defaults)
        assertEquals(kept, settings.loadAiSystemPrompts())
    }

    @Test fun older_exports_receive_defaults_without_losing_inline_instructions() = isolated { settings, _ ->
        assertTrue(settings.importAllSettings("""{"schemaVersion":3,"aiInstructions":[{"id":"old","name":"Old","instructions":"<system>Keep this</system>"}]}"""))
        settings.seedAiSystemPrompts(defaults)
        assertEquals(defaults, settings.loadAiSystemPrompts())
        assertEquals("<system>Keep this</system>", settings.loadAiInstructions().single().instructions)
    }

    @Test fun future_defaults_can_be_added_without_restoring_previously_deleted_entries() = isolated { settings, _ ->
        settings.seedAiSystemPrompts(defaults)
        settings.saveAiSetup(emptyList(), emptyList(), emptyList())
        val newPrompt = AiPromptEntry("future-default", "Future coach", "New text")
        settings.seedAiSystemPrompts(defaults + newPrompt)
        assertEquals(listOf(newPrompt), settings.loadAiSystemPrompts())
    }

    @Test fun malformed_saved_data_and_invalid_seed_metadata_are_not_overwritten() = isolated { settings, prefs ->
        prefs.edit().putString("ai_system_prompts", "{broken json").commit()
        settings.seedAiSystemPrompts(defaults)
        assertEquals("{broken json", prefs.getString("ai_system_prompts", null))
        assertFalse(prefs.contains("seeded_ai_system_prompt_ids"))
        val original = prefs.all.toMap()
        assertFalse(settings.importAllSettings("""{"schemaVersion":4,"seededAiSystemPromptIds":[null]}"""))
        assertEquals(original, prefs.all)
    }
}
