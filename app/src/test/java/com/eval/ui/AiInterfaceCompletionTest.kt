package com.eval.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class AiInterfaceCompletionTest {
    private fun field(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

    @Test fun moves_placeholder_is_available_with_an_explanation_and_inserts_the_complete_token() {
        val typed = field("@")
        val choice = aiInterfacePlaceholders.single { it.name == "MOVES" }
        assertTrue(choice.description.contains("Stockfish"))
        val inserted = insertAiInterfaceChoice(typed, requireNotNull(aiInterfaceCompletion(field(""), typed)), choice)
        assertEquals("@MOVES@", inserted.text)
        assertEquals(TextRange(7), inserted.selection)
        val tag = insertAiInterfaceChoice(field("<"), requireNotNull(aiInterfaceCompletion(field(""), field("<"))),
            aiInterfaceCommands.single { it.name == "moves" })
        assertEquals("<moves></moves>", tag.text)
    }

    @Test fun command_insertion_preserves_both_sides_and_places_cursor_inside_the_pair() {
        val before = field("before after", 7)
        val typed = field("before <after", 8)
        val pending = requireNotNull(aiInterfaceCompletion(before, typed))
        val inserted = insertAiInterfaceChoice(typed, pending, aiInterfaceCommands.first { it.name == "system" })
        assertEquals("before <system></system>after", inserted.text)
        assertEquals(TextRange("before <system>".length), inserted.selection)
    }

    @Test fun trigger_replaces_selected_text_without_damaging_surrounding_text() {
        val before = TextFieldValue("left replace right", TextRange(12, 5))
        val typed = field("left @ right", 6)
        val pending = requireNotNull(aiInterfaceCompletion(before, typed))
        val inserted = insertAiInterfaceChoice(typed, pending, aiInterfacePlaceholders.first { it.name == "FEN" })
        assertEquals("left @FEN@ right", inserted.text)
        assertEquals(TextRange(10), inserted.selection)
    }

    @Test fun placeholders_can_be_inserted_inside_command_bodies() {
        val before = field("<open></open>", 6)
        val typed = field("<open>@</open>", 7)
        val pending = requireNotNull(aiInterfaceCompletion(before, typed))
        val inserted = insertAiInterfaceChoice(typed, pending, aiInterfacePlaceholders.first { it.name == "BOARD" })
        assertEquals("<open>@BOARD@</open>", inserted.text)
        assertEquals(TextRange(13), inserted.selection)
    }

    @Test fun value_free_commands_leave_the_cursor_after_the_pair() {
        val typed = field("<")
        val pending = requireNotNull(aiInterfaceCompletion(field(""), typed))
        val inserted = insertAiInterfaceChoice(typed, pending, aiInterfaceCommands.first { it.name == "select" })
        assertEquals("<select></select>", inserted.text)
        assertEquals(TextRange(inserted.text.length), inserted.selection)
    }

    @Test fun cursor_moves_deletions_and_whole_snippet_pastes_do_not_open_popups() {
        assertNull(aiInterfaceCompletion(field("<"), field("<", 0)))
        assertNull(aiInterfaceCompletion(field("<s"), field("<")))
        assertNull(aiInterfaceCompletion(field(""), field("<system></system>")))
        assertNull(aiInterfaceCompletion(field(""), field("@FEN@")))
        assertNull(aiInterfaceCompletion(field(""), field("ordinary text")))
    }

    @Test fun dismissed_picker_allows_manual_tags_placeholders_and_email_addresses() {
        assertNull(aiInterfaceCompletion(field("<"), field("<s")))
        assertNull(aiInterfaceCompletion(field("@FEN"), field("@FEN@")))
        assertNull(aiInterfaceCompletion(field("person@"), field("person@e")))
        assertNotNull(aiInterfaceCompletion(field("@FEN@ "), field("@FEN@ @")))
    }

    @Test fun insertion_ignores_a_trigger_that_is_no_longer_present() {
        val value = field("changed")
        assertEquals(value, insertAiInterfaceChoice(value, AiInterfaceCompletion(AiInterfaceChoiceKind.COMMAND, 0), aiInterfaceCommands.first()))
    }
}
