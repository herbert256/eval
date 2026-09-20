# Calling the AI App

Eval's sender-side guide to `com.ai.ACTION_NEW_REPORT`.

Companion: [AI repo — doc/custom-intent.md](../ai/doc/custom-intent.md)
([GitHub](https://github.com/herbert256/ai/blob/master/doc/custom-intent.md)).
The local link assumes sibling `eval` and `ai` checkouts. Keep the shared
contract and examples between the marked comments identical in both files
when either side changes. The implementation notes below are repo-specific.

<!-- BEGIN SHARED AI INTENT CONTRACT -->
## Intent contract

Send action `com.ai.ACTION_NEW_REPORT` restricted to package `com.ai`.
This is separate from Android's `ACTION_SEND` share-sheet flow.

| String extra | Requirement | Meaning |
|---|---|---|
| `title` | Optional | Report title; generation uses `AI Report` when blank. |
| `instructions` | Required for Eval's current handoff | Paired instruction/data tags and standalone control flags. The extra key is **`instructions`**, not `externalInstructions`. |
| `prompt` | Optional | Question for other/older callers. `<prompt>` takes precedence when present. |
| `system` | Optional | Literal system text for other/older callers, as a fallback below configured worker/provider/report prompts. |

**Eval sends only `title` and `instructions`.** Its named instruction entries
contain literal prompt/system text, control tags and context placeholders.
Parameters and worker configurations remain in the AI app.

A bare `prompt` without instructions pre-fills New Report. Instruction-bearing
requests show **External request** confirmation before generation. If neither
`<prompt>`, a non-empty `prompt` extra nor Agent/Flock/Swarm selection supplies
a question, AI offers its saved-prompt picker. A supplied `<prompt>` never
selects or searches a saved definition, even if its text equals a saved name or ID.

For older callers, `prompt` can contain `-- end prompt --`: text before the
marker is the question and text after it is instructions. A supplied
`instructions` extra takes precedence over that split, even when empty.

## Instruction tags

Tag names ignore case. Use paired tags for values; flags may be standalone
or empty pairs, such as `<select>` or `<select></select>`.

| Tag | Meaning |
|---|---|
| `<system>text</system>` | Use this literal system-prompt text as the report-level override for all selected models. Never look up a saved prompt. |
| `<prompt>text</prompt>` | Use this literal report question. Never look up a saved prompt. |
| `<parameters>Name</parameters>` | Select a saved Parameters preset by stable ID or unique name, ignoring name case. |
| `<agent>Name</agent>` | Select a configured Agent by name; repeatable. |
| `<flock>Name</flock>` | Select a configured Flock by name; repeatable. |
| `<swarm>Name</swarm>` | Select a configured Swarm by name; repeatable. |
| `<open>content</open>` / `<close>content</close>` | Opening/closing report presentation, including HTML, CSS and JavaScript. |
| `<next>View</next>` | Completion action: `View`, `Share`, `Browser` or `Email`. Email uses AI's configured default email address. |
| `<email>recipient@example.com</email>` | Open the email chooser with the completed report attached and recipient filled in. |
| `<select>` | Open model selection after confirmation. |
| `<return>` | Finish AI after its completion action. No report data is returned as an Android activity result. |
| `<name>value</name>` | Supply data for matching `@name@` placeholders. Send it only when that placeholder is used. |

`<default>`, `<model>`, `<edit>` and `<type>` no longer control the handoff. They do
not choose defaults, models or layouts, or route to editing. Like other custom names,
a paired value can only supply data if explicitly referenced by a placeholder.

`<prompt>` takes precedence over the `prompt` extra. If no question is supplied,
selected workers can use their assigned default prompts. `<system>` overrides
worker/provider defaults, the `system` extra and system text inside Parameters.
Prompt and system bodies are trimmed, decoded once and substituted independently.
Names or IDs matching saved prompts are still literal text. For example,
`<prompt>Chess analysis</prompt>` asks exactly `Chess analysis`.

Only `<parameters>` resolves a saved preset: stable IDs take precedence over
unique names. Missing, empty or ambiguous references appear on confirmation and
prevent continuation. Worker selection still resolves Agent/Flock/Swarm names.
Requests do not change saved templates or worker assignments. Generation captures
the effective prompt and parameters for retry/regenerate.

External reports use Classic (One by one) as their initial layout.
After confirmation, immediate generation requires at least one Agent/Flock/Swarm
and no `<select>`. Other requests continue to model selection.
A valid selection, question and provider configuration are still required.
Completion actions run after generation; Share and Email open Android choosers.

## Context and placeholder substitution

1. **Eval sends unchanged templates plus referenced data.** Only data whose
   `@name@` placeholder occurs in the instruction/presentation text is included.
   This applies to standard fields, the date and custom data. Control tags are
   always retained. Repeated or differently cased placeholders share one
   lowercase data tag. Unused data declarations are removed.
2. **AI expands the text.** Matching placeholders in prompt/system text and
   opening/closing presentation use supplied data, ignoring case. Values are
   inserted literally in a single pass. A token inside a data value does not
   request another field and is not recursively expanded.

| Eval-supplied tag, when referenced | Value |
|---|---|
| `<fen>…</fen>` | Current position, including an explored variation. |
| `<color>…</color>` | White or Black, from the side to move. |
| `<server>…</server>` | Chess server when known. |
| `<player>…</player>` | Side-to-move player for a position report; selected player for a profile report. |
| `<pgn>…</pgn>` | Available game PGN. |
| `<board>…</board>` | Board HTML/JavaScript. |
| `<moves>…</moves>` | Every legal move with SAN, UCI, Stockfish evaluation and depth. |
| `<engine>…</engine>` | Best N Stockfish continuations with SAN, UCI, scores and depth. |
| `<date>…</date>` | Current local date in `yyyy-MM-dd` format. |

Eval runs **Moves list for AI** only when `@MOVES@` is used. It evaluates every
legal move using that Stockfish settings card, showing cancellable progress.
A failed search never sends a partial list. Scores are in pawns from White's
perspective; positive favors White, and `+M3` / `-M3` denotes White / Black mate
in three moves. All promotion choices are included.

Eval runs **Engine moves for AI** only when `@ENGINE@` is used. That settings
card controls line count (1–32), time per position, threads, memory and NNUE.
The timed progress screen shows the same results card as Manual mode.
**Stop and go to AI** sends the latest complete MultiPV iteration at a common
depth. If none is ready, the handoff explains that the search stopped before
results were available. Cancel sends no request. Fewer legal root moves means
fewer returned lines. Both searches use the same captured position.

Terminal positions supply `No legal moves in this position.` Requested but
unavailable fields are empty. Player-only requests never reuse the last game.
If neither engine placeholder is used, Eval opens AI without either search.
Templates saved only in AI do not cause Eval to send unrequested data.

Eval replaces top-level declarations of requested standard fields with the
current actual value and keeps one value per name. Custom duplicates use the
last value. A declaration such as `<fen>@FEN@</fen>` alone does not request FEN:
put `@FEN@` in a prompt, system or presentation body where it is needed.
An optional `<instructions>...</instructions>` wrapper is supported.

Plain data is XML-escaped (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&#39;`). AI decodes
it once; `open`, `close` and `board` remain raw markup. Custom names start with
a letter or underscore and may include digits, dots, hyphens and colons.
Values may span lines. Empty data replaces its token with empty text. Use each
single-value control once; the first is read. Nested tags inside a value never
become top-level commands.

Unmatched tokens remain unchanged, apart from AI's existing built-ins such as
`@MODEL@`, `@PROVIDER@`, `@AGENT@` and `@DATE@` in default prompts. Explicitly
supplied data takes precedence over built-ins.

Keep `@BOARD@` in `<open>`/`<close>` for report presentation. The data tag does
not add a board to model prompts unless the prompt/system explicitly uses it.
HTML bodies, including CSS, scripts and event handlers, appear in the in-app
HTML view, Complete/Short HTML and zipped HTML index. A literal `</open>` or
`</close>` ends its body even inside a JavaScript string. Text-only presentation
retains Markdown formatting; for HTML, write the whole body as HTML.

## Examples

### Eval position report

```xml
<system>You are a chess coach. Respond in @language@.</system>
<prompt>Analyse @FEN@ for @COLOR@. Use @ENGINE@ to explain candidate moves.</prompt>
<language>English</language>
<open>@BOARD@</open>
<select>
<next>View</next>
```

Eval calculates the best lines and sends only `fen`, `color`, `engine`, `board`
and `language` data. It leaves the prompt/system/presentation tokens unchanged.
AI previews the expanded literal prompts, then lets the user select models.

### Eval player report without Stockfish

```xml
<system>State what cannot be inferred from the supplied information.</system>
<prompt>Summarize the playing style of @PLAYER@ on @SERVER@.</prompt>
<select>
```

Only `player` and `server` data are sent. Neither Stockfish search runs.

### Another Android caller

```kotlin
val instructions = """
    <system>Answer in @language@.</system>
    <prompt>Describe @topic@ in three sentences.</prompt>
    <topic>Amsterdam &amp; Utrecht</topic>
    <language>Dutch</language>
    <select>
""".trimIndent()
startActivity(Intent("com.ai.ACTION_NEW_REPORT")
    .setPackage("com.ai")
    .putExtra("title", "City summary")
    .putExtra("instructions", instructions))
```

AI uses `Answer in Dutch.` and `Describe Amsterdam & Utrecht in three sentences.`
exactly, regardless of any saved prompt names. No other extras are required.

### Generate with a configured worker after confirmation

```xml
<prompt>Explain @topic@ simply.</prompt>
<topic>Photosynthesis</topic>
<agent>Science tutor</agent>
<next>View</next>
```

Create the Agent in AI first. Confirmation offers Generate because a worker
is supplied and `<select>` is absent.
<!-- END SHARED AI INTENT CONTRACT -->

## Eval implementation

- [`AiAppLauncher.kt`](app/src/main/java/com/eval/ui/AiAppLauncher.kt)
  implements `launchAiReport(context, entry, reportContext)`,
  `buildInstructions` and board HTML generation. It sends only `title` and
  `instructions`, restricted to package `com.ai`.
- [`AiSettingsModels.kt`](app/src/main/java/com/eval/ui/AiSettingsModels.kt)
  defines `AiInstructionEntry(id, name, instructions)` and
  `AiReportContext(title, fen, color, server, player, pgn, board, moves, engine)`.
- [`GameViewModel.kt`](app/src/main/java/com/eval/ui/GameViewModel.kt)
  captures the current position or selected player's context and stages the
  instruction choice. [`GameScreen.kt`](app/src/main/java/com/eval/ui/GameScreen.kt)
  shows that chooser before launching AI.
- [`SettingsScreen.kt`](app/src/main/java/com/eval/ui/SettingsScreen.kt)
  manages named entries under Settings → AI Instructions.
- [`SettingsPreferences.kt`](app/src/main/java/com/eval/ui/SettingsPreferences.kt)
  stores `ai_instructions_list`; typed settings schema v3 uses
  `aiInstructions`. Migration retains old IDs, names and instructions and
  converts a legacy email field to an `<email>` instruction. Old prompt and
  system-prompt text is not part of active Eval instruction storage.
- [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) declares package
  visibility queries for `com.ai` and the custom action, used by
  `resolveActivity`/the installed-app check.

The current launcher checks that AI can handle the intent and returns
`false` with a toast when unavailable. It also supports an optional pinned
signing-certificate fingerprint; the current empty constant leaves pinning
disabled. Model/API configuration and all report generation belong to AI.

Existing Eval call sites use the instruction-entry API, not the obsolete
`launchAiReport(context, title, prompt, system, instructions)` signature.
For example, from an Activity inside Eval:

```kotlin
val entry = AiInstructionEntry(
    name = "Position report",
    instructions = "<system>Explain clearly.</system><prompt>Analyse @FEN@.</prompt><select>"
)
val position = AiAppLauncher.gameContext(
    fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
)
AiAppLauncher.launchAiReport(this, entry, position)
```

The position values here are synthetic examples; production call sites use
the live `GameViewModel` snapshot. Both position and player requests require
the user to select a named instruction entry.
