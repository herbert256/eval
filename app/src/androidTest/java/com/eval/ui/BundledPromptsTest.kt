package com.eval.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledPromptsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val defaults get() = BundledAiPrompts.loadReportPrompts(context.assets)
    private val originalDefaults get(): List<AiPromptEntry> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.list("prompts-v1")!!.sorted().map { file ->
            val obj = assets.open("prompts-v1/$file").bufferedReader().use {
                JsonParser().parse(it.readText()).asJsonObject
            }
            AiPromptEntry("bundled-prompt:${file.removeSuffix(".json")}",
                obj.get("title").asString, obj.get("text").asString)
        }
    }

    private fun isolated(test: (SettingsPreferences, SharedPreferences) -> Unit) {
        val prefs = context.getSharedPreferences("bundled_prompts_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try { test(SettingsPreferences(prefs), prefs) }
        finally { prefs.edit().clear().commit() }
    }

    @Test fun bundled_prompts_are_compact_and_request_the_matching_chess_context() {
        val shortText = mapOf(
            "Analyse a FEN position" to "Analyse this FEN.\n\n@FEN@",
            "Annotate a chess game" to "Annotate this game.\n\n@PGN@"
        )
        assertEquals(shortText, defaults.filter { it.name in shortText }.associate { it.name to it.text })
        assertEquals(originalDefaults.map { it.id to it.name }, defaults.map { it.id to it.name })
        val expectedFields = mapOf(
            "Analyse a FEN position" to setOf("fen"),
            "Annotate a chess game" to setOf("pgn"),
            "Find tactical opportunities" to setOf("fen", "engine"),
            "Make a strategic plan" to setOf("fen"),
            "Explain the engine choices" to setOf("fen", "moves", "engine"),
            "Review mistakes and turning points" to setOf("pgn"),
            "Create a training plan" to setOf("pgn")
        )
        assertEquals(expectedFields.keys, defaults.map { it.name }.toSet())
        assertEquals(expectedFields.size, defaults.size)
        assertEquals(defaults.size, defaults.map { it.id }.distinct().size)
        val context = AiReportContext("Game",
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            pgn = "1. e4 e5 2. Nf3 *", moves = "e4: +0.30", engine = "1. e4 e5 2. Nf3")
        val values = mapOf("fen" to context.fen, "pgn" to context.pgn,
            "moves" to context.moves, "engine" to context.engine)
        defaults.forEach { prompt ->
            val original = originalDefaults.single { it.id == prompt.id }
            assertTrue("${prompt.name} stays compact", prompt.text.length in 1..250)
            assertTrue("${prompt.name} uses at least 55% less text", prompt.text.length < original.text.length * .45)
            val resolved = AiAppLauncher.composeInstruction(AiReportDraft(prompt = prompt.text))
            val fields = expectedFields.getValue(prompt.name)
            assertEquals(fields, AiAppLauncher.usedContextNames(resolved.instructions))
            val payload = AiAppLauncher.buildInstructions(resolved.instructions, context)
            assertTrue(payload.contains("<prompt>${prompt.text}</prompt>"))
            values.forEach { (field, value) ->
                assertEquals("${prompt.name}: $field", field in fields,
                    payload.contains("<$field>$value</$field>"))
            }
        }
    }

    @Test fun original_prompts_shorten_after_upgrade_and_restore_without_changing_choices() = isolated { settings, prefs ->
        settings.seedAiReportPrompts(originalDefaults)
        val systems = listOf(AiPromptEntry("coach", "My coach", "My system text"))
        val instructions = listOf(AiInstructionEntry("options", "Options", "<next>View</next>"))
        settings.saveAiSetup(systems, originalDefaults, instructions)
        val selection = AiReportSelection("coach", originalDefaults.first().id, "options")
        settings.saveAiReportSelection(selection)
        val oldBackup = settings.exportAllSettings()
        repeat(2) {
            settings.seedAiReportPrompts(defaults)
            assertEquals(defaults, settings.loadAiReportPrompts())
            assertEquals(systems, settings.loadAiSystemPrompts())
            assertEquals(instructions, settings.loadAiInstructions())
            assertEquals(selection, settings.loadAiReportSelection())
            val saved = prefs.all.toMap()
            SettingsPreferences(prefs).seedAiReportPrompts(defaults)
            assertEquals("Repeated seeding is a no-op", saved, prefs.all)
            if (it == 0) {
                prefs.edit().clear().commit()
                assertTrue(settings.importAllSettings(oldBackup))
            }
        }
    }

    @Test fun shortening_preserves_edited_renamed_copied_and_deleted_prompts() = isolated { settings, prefs ->
        val old = originalDefaults
        settings.seedAiReportPrompts(old)
        val edited = old[0].copy(text = old[0].text + "\nUse French.")
        val renamed = old[1].copy(name = "My game annotation")
        val copied = old[2].copy(id = "my-copy")
        settings.saveAiSetup(emptyList(), listOf(copied, edited, renamed) + old.drop(3), emptyList())
        val expected = listOf(copied, edited, renamed) + defaults.drop(3)
        settings.seedAiReportPrompts(defaults)
        assertEquals(expected, settings.loadAiReportPrompts())
        val backup = settings.exportAllSettings()
        prefs.edit().clear().commit()
        assertTrue(settings.importAllSettings(backup))
        SettingsPreferences(prefs).seedAiReportPrompts(defaults)
        assertEquals(expected, settings.loadAiReportPrompts())
    }

    @Test fun upgrade_preserves_existing_prompts_and_instruction_references() = isolated { settings, prefs ->
        val system = AiPromptEntry("system", "My coach", "Coaching text")
        val custom = AiPromptEntry("custom", "My question", "Question text")
        val sameName = AiPromptEntry("existing", "analyse a fen position", "My edited text")
        val instruction = AiInstructionEntry("instruction", "Report", "<next>View</next>")
        settings.saveAiSetup(listOf(system), listOf(custom, sameName), listOf(instruction))
        settings.seedAiReportPrompts(defaults)
        val expected = listOf(custom, sameName) + defaults.filterNot { it.name.equals(sameName.name, ignoreCase = true) }
        assertEquals(expected, settings.loadAiReportPrompts())
        assertEquals(listOf(system), settings.loadAiSystemPrompts())
        assertEquals(listOf(instruction), settings.loadAiInstructions())
        SettingsPreferences(prefs).seedAiReportPrompts(defaults)
        assertEquals(expected, settings.loadAiReportPrompts())
    }

    @Test fun edits_and_deletions_survive_restart_export_import_and_future_defaults() = isolated { settings, prefs ->
        // Settings from before bundled prompts existed receive all defaults.
        assertTrue(settings.importAllSettings("""{"schemaVersion":4,"aiReportPrompts":[]}"""))
        settings.seedAiReportPrompts(defaults)
        assertEquals(defaults, settings.loadAiReportPrompts())
        val kept = listOf(defaults.first().copy(name = "My FEN question", text = "Keep my edits"))
        settings.saveAiSetup(emptyList(), kept, emptyList())
        val exported = settings.exportAllSettings()
        prefs.edit().clear().commit()
        assertTrue(settings.importAllSettings(exported))
        val reloaded = SettingsPreferences(prefs)
        reloaded.seedAiReportPrompts(defaults)
        assertEquals(kept, reloaded.loadAiReportPrompts())
        val future = AiPromptEntry("future-prompt", "Future prompt", "New question")
        reloaded.seedAiReportPrompts(defaults + future)
        assertEquals(kept + future, reloaded.loadAiReportPrompts())
    }

    @Test fun malformed_saved_catalog_and_seed_metadata_cannot_replace_existing_data() = isolated { settings, prefs ->
        prefs.edit().putString("ai_report_prompts", "{broken json").commit()
        settings.seedAiReportPrompts(defaults)
        assertEquals("{broken json", prefs.getString("ai_report_prompts", null))
        assertFalse(prefs.contains("seeded_ai_report_prompt_ids"))
        val original = prefs.all.toMap()
        assertFalse(settings.importAllSettings("""{"schemaVersion":4,"seededAiReportPromptIds":[null]}"""))
        assertEquals(original, prefs.all)
        assertFalse(settings.importAllSettings("""{"seeded_ai_report_prompt_ids":{"_type":"String","_value":"wrong"}}"""))
        assertEquals(original, prefs.all)
    }
}
