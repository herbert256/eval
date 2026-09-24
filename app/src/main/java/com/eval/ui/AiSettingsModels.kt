package com.eval.ui

import java.util.UUID

/** Reusable prompt text, kept locally in Eval and resolved when a report starts. */
data class AiPromptEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val text: String = ""
)

/** Named, reusable AI instruction text, independent of both prompt catalogs. */
data class AiInstructionEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val instructions: String = ""
)

/** Last choices made when starting an AI request. Empty IDs mean None. */
data class AiReportSelection(
    val systemPromptId: String = "",
    val promptId: String = "",
    val instructionId: String = ""
) {
    fun available(systems: List<AiPromptEntry>, prompts: List<AiPromptEntry>, instructions: List<AiInstructionEntry>) = copy(
        systemPromptId = systemPromptId.takeIf { id -> systems.any { it.id == id } }.orEmpty(),
        promptId = promptId.takeIf { id -> prompts.any { it.id == id } }.orEmpty(),
        instructionId = instructionId.takeIf { id -> instructions.any { it.id == id } }.orEmpty()
    )
}

/** Editable text for this request only; it never changes the saved catalogs. */
data class AiReportDraft(val systemPrompt: String = "", val prompt: String = "", val instructions: String = "")

/** A snapshot of the position or player for which the user is choosing instructions. */
data class AiReportContext(
    val title: String,
    val fen: String = "",
    val color: String = "",
    val server: String = "",
    val player: String = "",
    val pgn: String = "",
    val board: String = "",
    val moves: String = "",
    val engine: String = ""
)
