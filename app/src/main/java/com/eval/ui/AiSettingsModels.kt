package com.eval.ui

import java.util.UUID

/**
 * Default prompt template for AI chess game analysis.
 */
const val DEFAULT_GAME_PROMPT = """You are an expert chess analyst. Analyze the following chess position given in FEN notation.

FEN: @FEN@

Please provide:
1. A brief assessment of the position (who is better and why)
2. Key strategic themes and plans for the side to play

No need to use an chess engine to look for tactical opportunities, Stockfish is already doing that for me.

Keep your analysis concise but insightful, suitable for a chess player looking to understand the position better."""

/**
 * Default prompt template for lichess.org & chess.com player analysis.
 */
const val DEFAULT_SERVER_PLAYER_PROMPT = """What do you know about user @PLAYER@ on chess server @SERVER@ ?. What is the real name of this player? What is good and the bad about this player? Is there any gossip on the internet?"""

/**
 * Default prompt template for other player analysis.
 */
const val DEFAULT_OTHER_PLAYER_PROMPT = """You are a professional chess journalist. Write a profile of the chess player @PLAYER@ (1000 words) for a serious publication.

Rules: Do not invent facts, quotes, games, ratings, titles, events, or personal details. If info is missing or uncertain, say so and label it 'unverified' or 'unknown.' If web access exists, verify key facts via reputable sources (e.g., FIDE, national federation, major chess media) and list sources at the end.

Must cover (with subheadings):

Career timeline + key results + rating/title context (only if verified)

Playing style: openings, strengths/weaknesses, psychology—grounded in evidence

2–3 signature games (human explanation; minimal notation; no engine-dump)

Rivalries/peers and place in today's chess landscape

Off-the-board work (coaching/streaming/writing/sponsors/controversies—verified only)

Current form (last 12 months) and realistic outlook

End with a tight conclusion"""

/**
 * AI Prompt Entry - a single prompt with name, template, and instructions.
 *
 * The Eval app stores prompts and sends them to the external AI app (com.ai)
 * which handles all API calls and agent configuration.
 */
data class AiPromptEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val instructions: String = "",
    val email: String = ""
)

/**
 * Default prompt entries seeded on first load.
 */
fun defaultAiPrompts(): List<AiPromptEntry> = listOf(
    AiPromptEntry(
        name = "Game Analysis",
        prompt = DEFAULT_GAME_PROMPT
    ),
    AiPromptEntry(
        name = "Server Player",
        prompt = DEFAULT_SERVER_PLAYER_PROMPT
    ),
    AiPromptEntry(
        name = "Other Player",
        prompt = DEFAULT_OTHER_PLAYER_PROMPT
    )
)
