# Eval → AI report handoff

## AI setup

**Settings → AI setup** has independent CRUD screens for **System prompts**, **Prompts**
and **AI instructions**. Prompt records contain `id`, `name` and `text`. Instruction
records contain only `id`, `name` and `instructions`. Instructions can also be copied.

Both prompt editors offer `@` completion. The instruction editor also offers `<`
completion. Popups describe each choice, insert full placeholders or paired tags,
and restore focus. Cancelling leaves the typed character available for custom text.

## Sending a report

Position and player reports use the same two screens:

1. **Select AI parts** offers three dropdowns for System prompts, Prompts and AI
   instructions, each including **None**. Each choice is remembered immediately by
   stable ID, including None, and restored on later requests and app restarts.
2. **Next** opens **Edit AI request** with three editable text boxes. `@` completion
   is available in all three; `<` completion is also available in AI instructions.
   Edits apply only to this request. Back retains edits until a selection changes.
   **Submit** prepares the requested context and opens AI. Empty catalogs can be
   skipped and text entered manually; a completely empty request cannot be submitted.

Selecting parts and pressing Next performs no Stockfish work or external handoff.
Submit captures the final edited text and detects context from that text, so adding
or removing `@MOVES@` or `@ENGINE@` in review changes which searches run. Cancelling
preparation discards the request; retry after an error retains the edited text.

Legacy inline `<system>` and `<prompt>` blocks are placed in the corresponding
review fields, unless a catalog choice overrides them. Their entities are decoded
once for editing. Report markup and other instructions remain intact. Final prompt
text is XML-escaped into literal `<system>` and `<prompt>` blocks.

Eval sends `com.ai.ACTION_NEW_REPORT` to package `com.ai`, with `title` and
`instructions` extras. AI decodes prompt bodies once and expands placeholders.
Only referenced context is included; repeated placeholders share one field.
`@MOVES@` requests evaluations of legal moves; `@ENGINE@` requests the best
Stockfish continuations. Their independent settings are under **Settings →
Stockfish**. Other placeholders are `@FEN@`, `@COLOR@`, `@SERVER@`, `@PLAYER@`,
`@PGN@`, `@BOARD@` and `@DATE@`.

A player request uses the selected player and available server, with empty position
context. The board contains HTML/JavaScript and can be used in `<open>@BOARD@</open>`
or `<close>@BOARD@</close>` for report presentation. Plain context is XML-escaped;
board markup remains raw. Context values are literal and are not recursively expanded.

The complete receiver contract, controls and examples are in [CALL_AI.md](../CALL_AI.md).

## Persistence and migration

Settings schema v5 exports `aiSystemPrompts`, `aiReportPrompts`, `aiInstructions`
and `lastAiReportSelection`. The remembered choice contains `systemPromptId`,
`promptId` and `instructionId`; these IDs are independent of saved instruction text.
Deleting a chosen entry clears only that choice. Stale IDs are treated as None.

The importer accepts v2, v3, v4 and legacy preference-map exports. Instruction IDs,
names and text are preserved. Old v4 links inside instructions are retired; their
system prompts and prompts remain in their own catalogs. The older v2 migration
still converts an email field into `<email>`. Invalid catalog entries or malformed
choice data are rejected before settings replacement. Saved games and retrieval
history remain untouched. Seed history and last choices survive export/import.

## Bundled system prompts and prompts

Editable defaults live at the repository root in `assets/system_prompts` for system
prompts and `assets/prompts` for prompts, one JSON file per prompt with exactly
`title` and `text` string fields. Gradle validates and packages both directories as
Android assets; the prompt text is not duplicated in Kotlin.
Keep filenames stable because they determine each default's ID.

The bundled prompts and their context are:

| Prompt | Context |
| --- | --- |
| Analyse a FEN position | `@FEN@` |
| Annotate a chess game | `@PGN@` |
| Find tactical opportunities | `@FEN@`, `@ENGINE@` |
| Make a strategic plan | `@FEN@` |
| Explain the engine choices | `@FEN@`, `@MOVES@`, `@ENGINE@` |
| Review mistakes and turning points | `@PGN@` |
| Create a training plan | `@PGN@` |

Placeholders are kept in the saved text and resolved from the selected report
context when the final edited request uses that placeholder. Submitting the tactics
or engine explanation prompt requests the corresponding Stockfish preparation.

On startup, Eval adds previously unseeded defaults to fresh or existing settings.
Existing IDs and same-name prompts win, so local edits and remembered choices
are preserved. The seed history prevents deleted defaults from returning and is
included in settings export/import. New asset files are added once on an upgrade;
editing an existing JSON file changes its text for fresh installations without
replacing an existing user's saved text.
