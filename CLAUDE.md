# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Build Commands

```bash
# Build debug APK (requires Java 25)
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew assembleDebug

# Build release APK (requires keystore in local.properties)
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew assembleRelease

# Clean build
./gradlew clean

# Deploy to device (emulator or connected device)
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.eval/.MainActivity

# Deploy to cloud (shared APK location)
cp app/build/outputs/apk/debug/app-debug.apk /Users/herbert/cloud/eval.apk

# Deploy to both targets
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.eval/.MainActivity && \
cp app/build/outputs/apk/debug/app-debug.apk /Users/herbert/cloud/eval.apk
```

**Cloud upload rule:** At the end of every prompt that changes the app, once the latest debug build succeeds and has been tested, copy it to `/Users/herbert/cloud/eval.apk` (the "Deploy to cloud" command). Never upload a build that failed to compile or failed testing; report that instead. Say in the final reply that the cloud APK was updated.

## Project Overview

Eval is an Android chess analysis app. It fetches games from Lichess.org, imports games and positions from files, web pages, images, the camera, the clipboard and shares, and provides three-stage Stockfish analysis with an interactive board. AI reports are delegated to an external companion app (`com.ai`).

**Codebase:** about 80 Kotlin files, ~28,000 lines (plus JVM tests in `app/src/test` and instrumented tests in `app/src/androidTest`) | **SDK:** minSdk 26, compileSdk/targetSdk 37 | **Toolchain:** Gradle 9.8, AGP 9.4, Kotlin 2.4, JDK 25 (same as the AI app) | **UI:** Jetpack Compose + Material 3

## Architecture

### Package Structure

```
com.eval/
├── MainActivity.kt - Entry point: theme, full screen, share intents, clipboard capture, background pause
├── chess/
│   ├── ChessBoard.kt - Board state, legal moves, SAN/UCI, FEN parsing and validation (fenValidationError)
│   ├── BoardSetupPosition.kt - Board-setup draft model; shares the position validator with ChessBoard
│   └── PgnParser.kt - PGN splitting, tokens, headers, clocks, variant tag, result tokens
├── data/
│   ├── LichessApi.kt - Retrofit interface and OkHttp client (header logging in debug only)
│   ├── LichessModels.kt - LichessGame, Players, Clock
│   ├── LichessRepository.kt - ChessRepository: Lichess content, PGN parsing, TV, streams, explorer
│   ├── OpeningBook.kt / OpeningExplorerApi.kt - Local opening names; Lichess explorer (needs a token)
│   ├── ChessDocumentReader.kt, RtfChessText.kt - Bounded PDF/Office/EPUB/RTF/ZIP/PGN readers
│   ├── ChessWebExtractor.kt, SharedChessText.kt, SharedChessInput.kt - FEN/PGN/link extraction, share intake
│   ├── ClipboardHistory.kt - Chess-only clipboard history (10 entries)
│   └── AppSignerTrust.kt - Trust-on-first-use signer pinning of com.stockfish141 and com.ai
├── stockfish/
│   ├── StockfishEngine.kt - Engine process lifecycle, options, searches (history, lastError)
│   ├── UciSession.kt - Reader thread, write lock, stop/drain/isready search protocol
│   ├── EngineProtocol.kt - AnalysisResult, PvLine, info parsing, EngineHistory, EngineError
│   └── CompletedPvIteration.kt - Last complete MultiPV iteration for AI lines
├── export/ - PgnExporter, GifExporter + AnimatedGifEncoder
├── audio/ - MoveSoundPlayer
└── ui/
    ├── GameViewModel.kt - Central state; delegates to the managers below
    ├── AnalysisOrchestrator.kt - Preview → Analyse → Manual pipeline, move qualities, background pause
    ├── GameLoader.kt - Loading games (Lichess, PGN files, saved games), startup restore
    ├── BoardNavigationManager.kt - Navigation, exploration, manual moves, promotion choice
    ├── ContentSourceManager.kt - Tournaments, broadcasts, TV, streamers, rankings, player info
    ├── LiveGameManager.kt - Live following (not wired to the UI; kept with its tests)
    ├── GameStorageManager.kt - Games in `eval_games` preferences (migrated from `eval_prefs`)
    ├── SettingsPreferences.kt, SettingsImportValidation.kt, SettingsManager.kt - Settings, export/import
    ├── AiAppLauncher.kt, AiMovesList.kt, AiEngineLines.kt, AiSettingsModels.kt, BundledAiPrompts.kt - AI handoff
    ├── AiReportScreens.kt, AiSetupScreen.kt, AiInterfaceEditor.kt, AiInterfaceCompletion.kt - AI UI
    ├── ExportShareManager.kt - Share/export actions (share intents emitted to the foreground UI)
    ├── OpeningExplorerLoader.kt - Opening name and explorer statistics for the shown position
    ├── Navigation.kt, EvalScreen.kt, EvalTitleBar.kt, SharedComponents.kt - Routes and shared scaffolding
    ├── GameScreen.kt, GameContent.kt, ChessBoardView.kt, AnalysisComponents.kt, MovesDisplay.kt - Game screen
    ├── RetrieveScreen.kt, GameSelectionDialog.kt, PlayerInfoScreen.kt - Game retrieval
    ├── UrlGameScanner.kt, UrlGameScreen.kt, BoardImageRecognizer.kt, ClipboardHistoryScreen.kt - Imports
    ├── BoardSetupScreen.kt - Position editor
    ├── *SettingsScreen.kt, SettingsScreen.kt, ColorPickerDialog.kt, HelpScreen.kt - Settings and help
    └── GameModels.kt, theme/Theme.kt - Data classes, enums, AppColors, theme
```

### Key Patterns

1. **MVVM**: `GameViewModel` exposes `StateFlow<GameUiState>` (~125 fields); update it only with `_uiState.update {}`
2. **Three-Stage Analysis**: PREVIEW (forward, fast) -> ANALYSE (backward, deep) -> MANUAL (interactive)
3. **Helper Classes**: ViewModel delegates to `AnalysisOrchestrator`, `GameLoader`, `BoardNavigationManager`, `ContentSourceManager`, `LiveGameManager`, `SettingsManager`, `ExportShareManager`
4. **Full-Screen Views**: All screens use `EvalTitleBar` + full-screen Column. No popups or dialogs (see UI Conventions).
5. **External AI App**: AI features delegated to `com.ai` via Android intents (not direct API calls)
6. **Result Pattern**: `sealed class Result<T> { Success, Error }` for API responses; user-facing messages come from `friendlyError`/`httpError`
7. **Stale responses**: async loads check a generation token (`GameLoader`) or run as one job per section (`ContentSourceManager.launchLatest`)
8. **Foreground-only side effects**: long jobs keep only a weak reference to the caller's context. A finished AI request launches with that context while Eval is visible; otherwise it waits in `pendingAiLaunch`, and GIF share sheets go through `shareRequests`; `EvalNavHost` performs both while the app is resumed

### Navigation Routes

```kotlin
object NavRoutes {
    const val GAME = "game"           // Main game display (start destination)
    const val SETTINGS = "settings"   // Settings hub
    const val HELP = "help"           // Help documentation
    const val RETRIEVE = "retrieve"   // Game retrieval (sub-screens via RetrieveSubScreen)
    const val SHARED = "shared"       // Content shared to Eval
}
```

### Key Enums

| Enum | Values |
|------|--------|
| `ChessServer` | `LICHESS`, `LOCAL` |
| `AnalysisStage` | `PREVIEW`, `ANALYSE`, `MANUAL` |
| `ArrowMode` | `NONE`, `MAIN_LINE`, `MULTI_LINES` |
| `PlayerBarMode` | `NONE`, `TOP`, `BOTTOM`, `BOTH` |
| `EvalBarPosition` | `NONE`, `LEFT`, `RIGHT` |
| `MoveQuality` | `BRILLIANT`, `GOOD`, `INTERESTING`, `DUBIOUS`, `MISTAKE`, `BLUNDER`, `BOOK`, `NORMAL` |

### Key Data Classes

```kotlin
data class GameUiState(...)          // ~125 fields - central UI state
data class StockfishSettings(...)    // Combined settings for 3 stages + 2 AI cards
data class BoardLayoutSettings(...)  // Board visual settings + eval bar
data class GraphSettings(...)        // Graph colors, ranges, scales
data class GeneralSettings(...)      // Sounds, full screen, username
data class MoveScore(...)            // score (White's view), isMate, mateIn, depth, nodes, nps
data class MoveDetails(...)          // san, from, to, isCapture, pieceType, clockTime
data class AnalysedGame(...)         // Stored game with all analysis data
data class AiInstructionEntry(...)   // id, name, instructions
data class AnalysisResult(...)       // Engine output: fen, depth, nodes, nps, lines
data class PvLine(...)               // Principal variation: score, isMate, pv, multipv
data class EngineHistory(...)        // startFen + UCI moves, so the engine sees repetitions
```

### External App Dependencies

| App | Package | Required | Purpose |
|-----|---------|----------|---------|
| Stockfish (installed version, currently 19) | `com.stockfish141` | Yes | Chess engine analysis |
| AI App | `com.ai` | No | AI-powered report generation (https://github.com/herbert256/ai) |

Both are pinned to the signer seen first (`AppSignerTrust`); a changed signer needs the user's confirmation.

## Analysis Stages

### 1. Preview Stage
- 50ms per move (10ms-500ms), forward, 1 thread, 8MB hash, non-interruptible

### 2. Analyse Stage
- 2s per move (500ms-10s), backward, 4 threads, 64MB hash, interruptible (tap to end)

### 3. Manual Stage
- Depth 32 (16-64), 4 threads, 128MB hash, MultiPV 3 (1-32), continuous real-time

Threads are capped at `StockfishEngine.maxUsableThreads()` (4 or the CPU count) and hash at 256 MB; the steppers only offer usable values. Stockfish 16+ has no "Use NNUE" option, so the NNUE toggles are only shown and sent when the engine advertises it. Searches send `position fen <start> moves …` (`EngineHistory`) so repetitions count. Analysis pauses in the background (`onAppBackgrounded`/`onAppForegrounded`). Move qualities use the mover's change in winning chances and compare only scores from one stage.

## AI Integration

AI reports use Android intents to the external `com.ai` app:
- **Intent action**: `com.ai.ACTION_NEW_REPORT`, restricted to package `com.ai`
- **Extras**: `title`, `instructions`; no `prompt` or `system` extra
- Eval stores three independent catalogs: system prompts and prompts (`AiPromptEntry`: id, name, text) and AI instructions (`AiInstructionEntry`: id, name, instructions). Bundled defaults live in `assets/system_prompts` and `assets/prompts`.
- A report request chooses a system prompt, a prompt and an instruction (each may be None) from inline option lists, then edits all three before Submit. The chosen text is sent as literal `<system>`/`<prompt>` blocks inside `instructions`.
- Only context referenced by a placeholder in the final text is appended: `<fen>`, `<color>`, `<server>`, `<player>`, `<pgn>`, `<board>`, `<moves>`, `<engine>` and `<date>`.
- FEN/color describe the current position. For position reports player is the side-to-move player's name; for profile reports it is the selected player. PGN is the available full game; board is generated HTML/JavaScript (chessboard.js and jQuery with subresource integrity).
- Moves list for AI is the fourth Stockfish settings card: seconds per legal move, threads, hash. A dedicated engine evaluates every legal root move at the captured FEN before handoff; scores use White's perspective. Progress is cancellable; failures never send a partial list.
- Engine moves for AI is the fifth Stockfish card: line count (1-32), time per position, threads, hash. Send the last complete MultiPV iteration at one depth, ranked for the side to move, with legal continuations and White-perspective scores. Stopping before the first complete iteration sends nothing.
- Manual analysis pauses while these are prepared; both use the moves that led to the position.
- The request is launched only while Eval is visible (with the caller's context, or later by `EvalNavHost` via `pendingAiLaunch`), never from the background.
- Plain context values use XML escaping; `<board>` contains raw generated HTML/JavaScript. Receivers must treat all fields as data, not control tags, and decode plain values once.
- Instructions may use `@FEN@`, `@COLOR@`, `@SERVER@`, `@PLAYER@`, `@PGN@`, `@MOVES@`, `@ENGINE@`, `@BOARD@`, `@DATE@`.
- Settings schema v5 exports the catalogs and the last selection; the importer accepts v2–v5 and legacy maps. See docs/ai-handoff.md and CALL_AI.md.

## Content Sources

**Lichess.org:** User games (NDJSON, paged with `until`), tournaments, broadcasts, TV channels (loaded via `game/export`, standard channels only), top rankings, streamers, player profiles. Non-standard variants are rejected. The opening explorer needs a personal Lichess token (Settings > General, stored in `eval_secrets`).

**Local:** PGN file upload (with ZIP support, bounded and read off the main thread), ECO opening selection (A00-E99), FEN position entry (with history and validation reasons), board setup, URL scanner, local documents and images, clipboard history (chess content only), camera photo of 2D board diagrams (system camera app via TakePicture, scanned by the same on-device recognizer as image files, then Board setup for review), shares from other apps (scanned only after the user confirms), previously analysed games.

## Settings Persistence

Settings via `SettingsPreferences` using SharedPreferences (`eval_prefs`). Key groups:
- Stockfish per-stage: seconds/depth, threads, hash, NNUE, multiPV; AI moves and AI engine cards
- Board layout: colors, coordinates, player bars, eval bar
- Graph: colors, ranges, scales
- Interface visibility: ~26 toggles across 3 stages
- General: move sounds, full screen, Lichess username
- AI setup: system prompts, prompts, instructions, last selection
- Settings export/import: typed JSON (schema v5), max 2 MB; unknown legacy keys are skipped

Other files: `eval_games` (GameStorageManager), `eval_secrets` (Lichess token), `eval_trust` (app signers). They are never exported; backup and device transfer exclude everything.

## Common Tasks

### Adding a New Setting
1. Add field to data class in `GameModels.kt`
2. Add SharedPreferences key in `SettingsPreferences.kt`
3. Update load/save functions in `SettingsPreferences.kt`
4. Add UI in the appropriate settings screen
5. Use the value in relevant code
6. If Gson serializes a new model class, keep its fields in `app/proguard-rules.pro`

### Adding a New Content Source
1. Add API endpoint to `LichessApi.kt`
2. Add data models if needed
3. Add repository method in `LichessRepository.kt`
4. Add state fields to `GameUiState` in `GameModels.kt`
5. Add methods to `ContentSourceManager.kt` (use `launchLatest(section)` and cancel it on dismiss)
6. Add UI in `RetrieveScreen.kt`

### Adding a New Navigation Route
1. Add route constant to `NavRoutes` in `Navigation.kt`
2. Add `composable()` block in `EvalNavHost`
3. Create screen composable with `EvalTitleBar`
4. Pass navigation callbacks from parent screens

### Modifying Arrow Behavior
1. `ArrowMode` enum in `GameModels.kt`
2. Arrow generation in `GameContent.kt`
3. Arrow drawing in `ChessBoardView.kt`

### Modifying the Board Display
1. `ChessBoardView.kt` - Canvas drawing, gestures, arrows
2. `GameContent.kt` - Layout, player bars, result bar, eval bar, promotion picker
3. `AnalysisComponents.kt` - Evaluation graphs, analysis panel

### Triggering Stockfish Analysis
Use `restartAnalysisForExploringLine()` in `AnalysisOrchestrator`: stop -> newGame -> delay(100ms) -> start

## UI Conventions

- **Full-screen views**: Use `EvalTitleBar` and avoid `AlertDialog`, `Dialog`, `DropdownMenu` or `ExposedDropdownMenu`. The AI interface editor is the only exception: typing `<` or `@` opens a choice popup while retaining the editor underneath.
- **Early return pattern**: Overlay screens use `if (showX) { XScreen(...); return }`
- **Radio buttons**: Selection controls use inline radio button groups (not dropdowns); make the whole row `selectable`/`toggleable` so TalkBack reads the label
- **Dark theme only**: Hardcoded dark color scheme via `AppColors` object; keep text at WCAG AA contrast
- **Title bar always visible**: `EvalTitleBar` shown on every screen
- **Color pickers**: Full-screen HSV picker with early-return pattern via a `rememberSaveable` `activeColorPicker` title
- **Dynamic pagination**: Page sizes are computed from available screen space with `rowsThatFit` (row height × font scale). Screens with `weight(1f)` use `BoxWithConstraints`; scrollable screens use `LocalConfiguration.current.screenHeightDp`.
- **Lists**: `LazyColumn` keys must be unique for any data (e.g. openings by FEN, TV by channel name)

## Verification Checklist

- [ ] Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew assembleDebug`
- [ ] JVM tests: `./gradlew testDebugUnitTest`; release shrinking: `./gradlew :app:minifyReleaseWithR8`
- [ ] No `AlertDialog`, `Dialog`, `DropdownMenu` or `ExposedDropdownMenu` outside the AI interface editor's choice popups
- [ ] Title bar visible on all screens
- [ ] Load game from Lichess (user games, TV)
- [ ] Full analysis pipeline (Preview -> Analyse -> Manual)
- [ ] Arrow modes cycle correctly
- [ ] Settings persist across restarts
- [ ] Export features work (PGN, GIF)
- [ ] Color pickers show as full-screen views
