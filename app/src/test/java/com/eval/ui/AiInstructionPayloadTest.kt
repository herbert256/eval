package com.eval.ui

import org.junit.Assert.*
import org.junit.Test

class AiInstructionPayloadTest {
    private val context = AiReportContext(
        title = "Test", fen = "position", color = "White", server = "lichess.org",
        player = "A & @COLOR@", pgn = "{</pgn><select>}",
        board = "<div id=\"board\">Board & text</div>", moves = "e4 (e2e4): +0.20",
        engine = "1. +0.20 (depth 12): e4 e5 Nf3"
    )

    private fun values(payload: String, tag: String) = Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
        .findAll(payload).map { it.groupValues[1] }.toList()

    @Test fun repeated_placeholders_keep_templates_and_send_each_actual_value_once() {
        val template = "<system>Coach @PLAYER@ on @DATE@; @PLAYER@</system>" +
            "<prompt>Analyse @FEN@ for @COLOR@ on @DATE@</prompt>" +
            "<open>@BOARD@ @BOARD@</open>"
        val payload = AiAppLauncher.buildInstructions(template, context)
        assertTrue(payload.startsWith(template + "\n"))
        assertEquals(listOf("position"), values(payload, "fen"))
        assertEquals(listOf("White"), values(payload, "color"))
        assertEquals(listOf("A &amp; @COLOR@"), values(payload, "player"))
        assertEquals(listOf(context.board), values(payload, "board"))
        assertEquals(1, values(payload, "date").size)
        assertTrue(values(payload, "date").single().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test fun unused_data_and_removed_controls_are_not_sent() {
        val payload = AiAppLauncher.buildInstructions(
            "<prompt>Literal question</prompt><system>Literal system</system><select>" +
                "<default>Old default</default><model>gpt-4o@OpenAI</model><edit><custom>Unused</custom>", context)
        assertEquals("<prompt>Literal question</prompt><system>Literal system</system><model>gpt-4o@OpenAI</model>", payload)
        assertTrue(AiAppLauncher.usedContextNames(payload).isEmpty())
    }

    @Test fun data_values_do_not_request_other_data_and_custom_duplicates_keep_the_last_value() {
        val payload = AiAppLauncher.buildInstructions(
            "<prompt>@PLAYER@ @custom@</prompt><custom>old</custom><CUSTOM>@ENGINE@</CUSTOM>" +
                "<unused>@MOVES@</unused><date>@DATE@</date>", context)
        assertEquals(listOf("@ENGINE@"), values(payload, "custom"))
        assertEquals(listOf("A &amp; @COLOR@"), values(payload, "player"))
        for (tag in listOf("engine", "moves", "date", "color", "unused")) assertTrue(tag, values(payload, tag).isEmpty())
        assertEquals(setOf("player", "custom"), AiAppLauncher.usedContextNames(
            "<prompt>@PLAYER@ @custom@</prompt><custom>@ENGINE@</custom><unused>@MOVES@</unused>"))
    }

    @Test fun old_context_declarations_are_deduplicated_without_editing_nested_markup() {
        val template = "<system>Use <fen>@FEN@</fen> at @DATE@</system>"
        val payload = AiAppLauncher.buildInstructions(
            "<FEN>stale</FEN><fen>other</fen><date>@DATE@</date><date>old</date>" + template, context
        )
        assertTrue(payload.startsWith(template))
        assertFalse(payload.contains("stale"))
        assertFalse(payload.contains("<date>@DATE@</date>"))
        assertEquals(1, values(payload, "date").size)
        assertTrue(payload.endsWith("</date>\n"))
    }

    @Test fun mixed_case_tokens_custom_data_and_optional_wrapper_are_preserved() {
        val body = "<system>@date@ @Date@ @CUSTOM@</system><custom>literal</custom>"
        val payload = AiAppLauncher.buildInstructions("<instructions>$body</instructions>", context)
        assertTrue(payload.startsWith("<instructions><system>@date@ @Date@ @CUSTOM@</system>\n"))
        assertTrue(payload.endsWith("</date>\n</instructions>"))
        assertEquals(1, values(payload, "date").size)
        assertEquals(listOf("literal"), values(payload, "custom"))
    }

    @Test fun requested_missing_position_data_is_empty_and_unrequested_data_is_absent() {
        val payload = AiAppLauncher.buildInstructions("<prompt>@FEN@ @PLAYER@</prompt><date>2001-02-03</date>",
            AiReportContext("Player", player = "Example"))
        assertEquals(listOf(""), values(payload, "fen"))
        assertEquals(listOf("Example"), values(payload, "player"))
        for (tag in listOf("color", "pgn", "board", "moves", "engine", "date")) assertTrue(tag, values(payload, tag).isEmpty())
    }

    @Test fun moves_tokens_stay_in_templates_and_share_one_actual_value() {
        val template = "<system>Use @MOVES@ and @moves@</system>"
        val payload = AiAppLauncher.buildInstructions("<moves>@MOVES@</moves><MOVES>old</MOVES>$template", context)
        assertTrue(payload.startsWith(template))
        assertEquals(listOf(context.moves), values(payload, "moves"))
        assertFalse(payload.contains("old"))
    }

    @Test fun engine_tokens_are_left_for_ai_and_repeated_declarations_share_one_value() {
        val template = "<system>Use @ENGINE@ and @engine@</system><prompt>Explain @ENGINE@</prompt>"
        val payload = AiAppLauncher.buildInstructions("<engine>@ENGINE@</engine><ENGINE>old</ENGINE>$template", context)
        assertTrue(payload.startsWith(template))
        assertEquals(listOf(context.engine), values(payload, "engine"))
        assertFalse(payload.contains("old"))
    }
}
