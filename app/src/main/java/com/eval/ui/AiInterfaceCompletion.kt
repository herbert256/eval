package com.eval.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class AiInterfaceChoiceKind(val trigger: Char, val title: String) {
    COMMAND('<', "AI commands"),
    PLACEHOLDER('@', "Context placeholders")
}

internal data class AiInterfaceChoice(
    val name: String,
    val description: String,
    val takesValue: Boolean = true
)

// Keep these choices aligned with CALL_AI.md and AiAppLauncher.buildInstructions.
internal val aiInterfaceCommands = listOf(
    AiInterfaceChoice("system", "Use this text as the system prompt, with any context placeholders."),
    AiInterfaceChoice("parameters", "Select a saved generation Parameters preset."),
    AiInterfaceChoice("prompt", "Use this text as the report prompt, with any context placeholders."),
    AiInterfaceChoice("agent", "Select an Agent by name. Can be used more than once."),
    AiInterfaceChoice("flock", "Select a Flock by name. Can be used more than once."),
    AiInterfaceChoice("swarm", "Select a Swarm by name. Can be used more than once."),
    AiInterfaceChoice("type", "Choose the report format: Classic or Table."),
    AiInterfaceChoice("open", "Add opening report content, including HTML and scripts."),
    AiInterfaceChoice("close", "Add closing report content, including HTML and scripts."),
    AiInterfaceChoice("next", "After completion: View, Share, Browser or Email."),
    AiInterfaceChoice("email", "Open the email chooser with the report and this recipient."),
    AiInterfaceChoice("moves", "Supply legal moves and scores only when @MOVES@ is used. Eval fills this automatically.", false),
    AiInterfaceChoice("engine", "Supply best lines and scores only when @ENGINE@ is used. Eval fills this automatically.", false),
    AiInterfaceChoice("select", "Open model selection after confirmation. No value needed.", false),
    AiInterfaceChoice("return", "Close the AI activity after its completion action. No value needed.", false)
)

internal val aiInterfacePlaceholders = listOf(
    AiInterfaceChoice("FEN", "The current chess position, including an explored variation."),
    AiInterfaceChoice("COLOR", "The side to move: White or Black."),
    AiInterfaceChoice("SERVER", "The chess server, when known."),
    AiInterfaceChoice("PLAYER", "The side-to-move player or the selected profile player."),
    AiInterfaceChoice("PGN", "The available game moves and headers."),
    AiInterfaceChoice("MOVES", "All legal moves in the current position, with Stockfish scores from White's perspective."),
    AiInterfaceChoice("ENGINE", "Stockfish's best lines, with continuations and scores. Set the number in Engine moves for AI."),
    AiInterfaceChoice("BOARD", "An interactive chessboard for opening or closing report content."),
    AiInterfaceChoice("DATE", "Today's local date in year-month-day format.")
)

internal data class AiInterfaceCompletion(val kind: AiInterfaceChoiceKind, val offset: Int)

/** Single inserted triggers open the picker; cursor moves, deletions and whole snippets do not. */
internal fun aiInterfaceCompletion(
    previous: TextFieldValue,
    current: TextFieldValue
): AiInterfaceCompletion? {
    if (!current.selection.collapsed || current.text == previous.text) return null
    val offset = previous.selection.min
    val trigger = current.text.getOrNull(offset) ?: return null
    val kind = AiInterfaceChoiceKind.values().firstOrNull { it.trigger == trigger } ?: return null
    if (current.selection.start != offset + 1) return null
    val expected = previous.text.replaceRange(previous.selection.min, previous.selection.max, trigger.toString())
    if (current.text != expected) return null
    // Let users finish a manually entered placeholder after dismissing its picker.
    if (kind == AiInterfaceChoiceKind.PLACEHOLDER &&
        Regex("@[A-Za-z_][A-Za-z0-9_.:-]*$").containsMatchIn(current.text.take(offset))) return null
    return AiInterfaceCompletion(kind, offset)
}

internal fun insertAiInterfaceChoice(
    value: TextFieldValue,
    completion: AiInterfaceCompletion,
    choice: AiInterfaceChoice
): TextFieldValue {
    if (value.text.getOrNull(completion.offset) != completion.kind.trigger) return value
    val insertion = when (completion.kind) {
        AiInterfaceChoiceKind.COMMAND -> "<${choice.name}></${choice.name}>"
        AiInterfaceChoiceKind.PLACEHOLDER -> "@${choice.name}@"
    }
    val cursor = completion.offset + if (completion.kind == AiInterfaceChoiceKind.COMMAND && choice.takesValue)
        choice.name.length + 2 else insertion.length
    return TextFieldValue(
        text = value.text.replaceRange(completion.offset, completion.offset + 1, insertion),
        selection = TextRange(cursor)
    )
}
