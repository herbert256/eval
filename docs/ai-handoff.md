# Eval → AI report handoff

Eval stores named instruction entries (`id`, `name`, `instructions`). Both position and player reports require the user to choose an entry. Prompts and system prompts are created and stored in the AI app.

In **Edit AI interface** (or **New AI interface**), type `<` to choose a command
or `@` to choose a context placeholder. Each popup lists names and short
descriptions. A command inserts matching opening and closing tags; the cursor
starts between them for commands that need a value. Flags need no value and
leave the cursor after the tag pair. A placeholder inserts its complete token.
Cancel keeps the typed character so custom tags and literal text can still be
entered manually. The AI app also accepts the editor's paired flag tags.

Eval sends `com.ai.ACTION_NEW_REPORT`, restricted to package `com.ai`, with `title` and `instructions` extras. There are no `prompt` or `system` extras.

Eval leaves placeholders in the selected instruction text unchanged and sends the seven standard context tags below, even when a value is unavailable. Repeated placeholders share one data field. When the interface uses a date placeholder, Eval also sends one `date` tag with the actual current local date. Existing top-level declarations of these supplied fields are deduplicated.

```xml
<fen>r4rk1/1b2bppp/ppq1p3/2ppB2n/5P2/1P1BP3/P1PPQ1PP/R4RK1 w - - 0 15</fen>
<color>White</color>
<server></server>
<player>White</player>
<pgn>[FEN "r4rk1/1b2bppp/ppq1p3/2ppB2n/5P2/1P1BP3/P1PPQ1PP/R4RK1 w - - 0 15"]

*</pgn>
<board>Generated board HTML and JavaScript</board>
<moves>All legal moves, each with its Stockfish evaluation</moves>
```

- `fen`: the current position, including an explored variation.
- `color`: `White` or `Black`, read from that FEN.
- `server`: `lichess.org` when known. Local FEN positions have no server.
- `player`: for position reports, the side-to-move player's name; for profile reports, the selected player.
- `pgn`: the available full game PGN. The separate FEN is authoritative for the current position.
- `moves`: every legal move at the captured FEN, including all promotions, with SAN, UCI, Stockfish evaluation and search depth. Scores use White's perspective: positive favors White; negative favors Black; +M/-M marks mate for White/Black.
- `board`: generated chessboard HTML/JavaScript. It belongs in report presentation, not model request bodies.

A player-only report has empty FEN, color, PGN, board and moves tags. It does not inherit the last opened game.

Plain values use XML escaping (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&#39;`). The receiver decodes those values once. Board markup is raw inside its enclosing tag. All seven context tags and `<open>`/`<close>` bodies must be removed before interpreting control tags, so markup and PGN are never interpreted as commands.

Instructions may use `@FEN@`, `@COLOR@`, `@SERVER@`, `@PLAYER@`, `@PGN@`, `@MOVES@`, `@BOARD@` and `@DATE@`. For example:

```xml
<type>Classic</type><select><next>View</next>
<open>@BOARD@</open>
```

The AI receiver resolves placeholders in the selected prompt, system prompt and opening/closing report content. Eval never expands those templates. Standard context remains available to templates saved only in AI. A prompt or system template explicitly using the board placeholder receives its supplied value; merely sending board data does not include it in model requests. See the shared custom-intent contract for selection and substitution details.

Optional references can select AI-owned templates by stable ID or unique name:

```xml
<prompt>Chess position analysis</prompt>
<system>Chess coach</system>
<type>Classic</type><select>
<open>@BOARD@</open>
```

Saved templates are resolved by ID or unique name. Unresolved `<system>` or `<prompt>` references are used as literal system-prompt text, with placeholder substitution performed by AI. Older callers that supply a prompt extra remain supported by the AI app.

## Existing Eval settings

Settings schema v3 uses `aiInstructions` and the preference key `ai_instructions_list`. The upgrade retains old entry IDs, names and instruction text; a legacy email field becomes an `<email>` instruction. Old prompt, system-prompt and category fields are not retained in active Eval storage. Schema v2 exports and legacy preference-map exports can still be imported. New exports contain only instruction entries.

## Moves list for AI

The fourth card in Settings → Stockfish controls the moves list independently of board analysis: seconds per move, threads, hash memory and NNUE. Defaults are 0.25 seconds per move, one thread, 32 MB and NNUE on. Before every position handoff, Eval evaluates each legal root move with these settings and shows cancellable progress. The complete list is always supplied, including when only an AI-saved template uses it. If any search fails, the user can retry; no partial list is sent. Terminal positions send “No legal moves in this position.” Player-only requests send an empty moves field.
