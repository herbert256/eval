package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiPromptStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun v4_instruction_links_are_retired_without_losing_text_or_catalogs() {
        val prefs = context.getSharedPreferences("ai_setup_v4_links", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            val old = """[{"id":"i","name":"Coach","instructions":"<model>test@provider</model>","systemPromptId":"s","promptId":"p"}]"""
            assertTrue(settings.importAllSettings("""{"schemaVersion":4,"aiSystemPrompts":[{"id":"s","name":"System","text":"Coach"}],"aiReportPrompts":[{"id":"p","name":"Prompt","text":"Question"}],"aiInstructions":$old}"""))
            val instruction = AiInstructionEntry("i", "Coach", "<model>test@provider</model>")
            assertEquals(listOf(instruction), settings.loadAiInstructions())
            assertEquals("Coach", settings.loadAiSystemPrompts().single().text)
            assertEquals("Question", settings.loadAiReportPrompts().single().text)
            prefs.edit().putString("ai_instructions_list", old).commit()
            assertEquals(listOf(instruction), settings.loadAiInstructions())
            val entry = com.google.gson.JsonParser().parse(prefs.getString("ai_instructions_list", "")).asJsonArray.single().asJsonObject
            assertEquals(setOf("id", "name", "instructions"), entry.keySet())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun remembered_choices_keep_none_and_discard_deleted_entries_and_bad_imports() {
        val prefs = context.getSharedPreferences("ai_setup_choices", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            val systems = listOf(AiPromptEntry("s", "Coach", "System"))
            val prompts = listOf(AiPromptEntry("p", "Question", "Prompt"))
            val instructions = listOf(AiInstructionEntry("i", "Options", "<next>View</next>"))
            settings.saveAiSetup(systems, prompts, instructions)
            settings.saveAiReportSelection(AiReportSelection("s", "p", "i"))
            settings.saveAiSetup(emptyList(), prompts, emptyList())
            assertEquals(AiReportSelection(promptId = "p"), SettingsPreferences(prefs).loadAiReportSelection())
            settings.saveAiReportSelection(AiReportSelection())
            assertEquals(AiReportSelection(), SettingsPreferences(prefs).loadAiReportSelection())
            val before = prefs.all.toMap()
            assertFalse(settings.importAllSettings("""{"schemaVersion":5,"lastAiReportSelection":{"promptId":null}}"""))
            assertEquals(before, prefs.all)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun catalogs_and_references_survive_reload_and_export_import() {
        val prefs = context.getSharedPreferences("ai_setup_roundtrip", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            val systems = listOf(AiPromptEntry("s", "Coach", "Explain @MOVES@ & @DATE@"))
            val prompts = listOf(AiPromptEntry("p", "Question", "Analyse @FEN@"))
            val instructions = listOf(AiInstructionEntry("i", "Report", "<next>View</next>"))
            settings.saveAiSetup(systems, prompts, instructions)
            val selection = AiReportSelection("s", "p", "i")
            settings.saveAiReportSelection(selection)
            val reloaded = SettingsPreferences(prefs)
            assertEquals(systems, reloaded.loadAiSystemPrompts())
            assertEquals(prompts, reloaded.loadAiReportPrompts())
            assertEquals(instructions, reloaded.loadAiInstructions())
            assertEquals(selection, reloaded.loadAiReportSelection())
            val json = reloaded.exportAllSettings()
            assertTrue(json.contains("\"schemaVersion\":5"))
            settings.resetAllSettingsToDefaults()
            assertTrue(settings.loadAiSystemPrompts().isEmpty())
            assertTrue(settings.importAllSettings(json))
            assertEquals(systems, settings.loadAiSystemPrompts())
            assertEquals(prompts, settings.loadAiReportPrompts())
            assertEquals(instructions, settings.loadAiInstructions())
            assertEquals(selection, settings.loadAiReportSelection())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun existing_v3_instructions_keep_inline_content_and_have_no_prompt_selections() {
        val prefs = context.getSharedPreferences("ai_setup_v3", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = SettingsPreferences(prefs)
            val json = """{"schemaVersion":3,"aiInstructions":[{"id":"old","name":"Coach","instructions":"<system>Coach @PLAYER@</system><prompt>Analyse @FEN@</prompt>"}]}"""
            assertTrue(settings.importAllSettings(json))
            assertTrue(settings.loadAiSystemPrompts().isEmpty())
            assertTrue(settings.loadAiReportPrompts().isEmpty())
            val old = settings.loadAiInstructions().single()
            val draft = AiAppLauncher.prepareDraft(null, null, old.instructions)
            assertEquals("Coach @PLAYER@", draft.systemPrompt)
            assertEquals("Analyse @FEN@", draft.prompt)
            assertEquals("", draft.instructions)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun invalid_catalogs_or_dangling_references_do_not_replace_saved_settings() {
        val prefs = context.getSharedPreferences("ai_setup_validation", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("lichess_username", "Keep").commit()
        try {
            val settings = SettingsPreferences(prefs)
            val before = prefs.all.toMap()
            for (fields in listOf(
                """"aiSystemPrompts":[null]""",
                """"aiReportPrompts":[{"id":"p","name":"P","text":false}]""",
                """"aiInstructions":[{"id":"i","name":"I","promptId":"missing"}]""",
                """"aiSystemPrompts":[{"id":"s","name":"S","text":"A"},{"id":"s","name":"S2","text":"B"}]""",
                """"aiInstructions":[{"systemPromptId":null}]"""
            )) {
                assertFalse(fields, settings.importAllSettings("{\"schemaVersion\":4,$fields}"))
                assertEquals(before, prefs.all)
            }
        } finally { prefs.edit().clear().commit() }
    }
}
