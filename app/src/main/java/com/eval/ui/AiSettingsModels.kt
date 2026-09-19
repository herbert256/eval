package com.eval.ui

import java.util.UUID

/** Named control instructions. Model prompts and system prompts belong to the AI app. */
data class AiInstructionEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val instructions: String = ""
)

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
