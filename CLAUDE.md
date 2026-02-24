# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Build Commands

```bash
# Build debug APK (requires Java 17)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Build release APK (requires keystore in local.properties)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease

# Clean build
./gradlew clean

# Deploy to device
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.eval/.MainActivity
```

## Project Overview

Eval is an Android chess analysis app. It fetches games from Lichess.org, parses PGN, and provides three-stage Stockfish 17.1 analysis with an interactive board. AI reports are delegated to an external companion app (`com.ai`).

**Codebase:** 46 Kotlin files, ~22,300 lines | **SDK:** minSdk 26, targetSdk 34 | **UI:** Jetpack Compose + Material 3

## Architecture

### Package Structure

```
com.eval/
├── MainActivity.kt (33 lines) - Entry point, sets up Compose theme
├── chess/
│   ├── ChessBoard.kt (643) - Board state, move validation, FEN parsing
│   └── PgnParser.kt (98) - PGN parsing with clock time extraction
├── data/
│   ├── LichessApi.kt (304) - Retrofit interface for Lichess API
│   ├── LichessModels.kt (40) - Data classes: LichessGame, Players, Clock
│   ├── LichessRepository.kt (1,069) - Repository with ChessServer enum
│   ├── OpeningBook.kt (217) - ECO opening identification
│   └── OpeningExplorerApi.kt (57) - Opening statistics API
├── stockfish/
│   └── StockfishEngine.kt (511) - UCI protocol wrapper, process management
├── export/
│   ├── GifExporter.kt (319) - Animated GIF export
│   ├── PgnExporter.kt (134) - PGN file export
│   ├── AnimatedGifEncoder.kt (898) - GIF encoding library
│   └── HtmlReportBuilder.kt (580) - HTML report for AI analysis
├── audio/
│   └── MoveSoundPlayer.kt (83) - Move sound effects
└── ui/
    ├── GameViewModel.kt (1,213) - Central state management
    ├── AnalysisOrchestrator.kt (666) - 3-stage analysis pipeline
    ├── GameLoader.kt (736) - Game loading from APIs, files, storage
    ├── BoardNavigationManager.kt (324) - Move navigation, line exploration
    ├── ContentSourceManager.kt (581) - Tournaments, broadcasts, TV, streamers
    ├── LiveGameManager.kt (200) - Real-time game following
    ├── AiAppLauncher.kt (260) - External AI app integration via intents
    ├── AiSettingsModels.kt (80) - AI prompt data classes and defaults
    ├── GameScreen.kt (974) - Main screen, share/export, AI prompts
    ├── GameContent.kt (1,837) - Board, players, moves, result bar, eval bar
    ├── ChessBoardView.kt (661) - Canvas-based interactive chess board
    ├── AnalysisComponents.kt (872) - Evaluation graphs, analysis panel
    ├── MovesDisplay.kt (222) - Move list with scores and piece symbols
    ├── OpeningExplorerPanel.kt (234) - Opening statistics display
    ├── PlayerInfoScreen.kt (807) - Player profile and game history
    ├── GameSelectionDialog.kt (799) - Game selection from multiple games
    ├── RetrieveScreen.kt (2,088) - Game retrieval UI with all sources
    ├── SettingsScreen.kt (631) - Settings navigation + AI prompts list
    ├── StockfishSettingsScreen.kt (441) - Engine settings for 3 stages
    ├── ArrowSettingsScreen.kt (278) - Arrow display configuration
    ├── BoardLayoutSettingsScreen.kt (378) - Board colors, pieces, eval bar
    ├── InterfaceSettingsScreen.kt (329) - UI visibility per stage
    ├── GraphSettingsScreen.kt (447) - Graph color and range settings
    ├── GeneralSettingsScreen.kt (177) - General app settings
    ├── HelpScreen.kt (291) - In-app help documentation
    ├── ColorPickerDialog.kt (252) - HSV color picker (full-screen)
    ├── GameModels.kt (420) - Data classes and enums
    ├── SettingsPreferences.kt (570) - SharedPreferences persistence
    ├── GameStorageManager.kt (183) - Game persistence and retrieval
    ├── SharedComponents.kt (168) - EvalTitleBar, ColorSettingRow, etc.
    ├── Navigation.kt (126) - Jetpack Navigation routes
    └── theme/Theme.kt (32) - Material3 dark theme
```

### Key Patterns

1. **MVVM**: `GameViewModel` exposes `StateFlow<GameUiState>` (~145 fields), UI recomposes reactively
2. **Three-Stage Analysis**: PREVIEW (forward, fast) -> ANALYSE (backward, deep) -> MANUAL (interactive)
3. **Helper Classes**: ViewModel delegates to `AnalysisOrchestrator`, `GameLoader`, `BoardNavigationManager`, `ContentSourceManager`, `LiveGameManager`
4. **Full-Screen Views**: All screens use `EvalTitleBar` + full-screen Column. No popups or dialogs anywhere.
5. **External AI App**: AI features delegated to `com.ai` via Android intents (not direct API calls)
6. **Result Pattern**: `sealed class Result<T> { Success, Error }` for API responses

### Navigation Routes

```kotlin
object NavRoutes {
    const val GAME = "game"           // Main game display (start destination)
    const val SETTINGS = "settings"   // Settings hub
    const val HELP = "help"           // Help documentation
    const val RETRIEVE = "retrieve"   // Game retrieval
    const val PLAYER_INFO = "player_info"
}
```

### Key Enums

| Enum | Values |
|------|--------|
| `ChessServer` | `LICHESS` |
| `AnalysisStage` | `PREVIEW`, `ANALYSE`, `MANUAL` |
| `ArrowMode` | `NONE`, `MAIN_LINE`, `MULTI_LINES` |
| `PlayerBarMode` | `NONE`, `TOP`, `BOTTOM`, `BOTH` |
| `EvalBarPosition` | `NONE`, `LEFT`, `RIGHT` |
| `MoveQuality` | `BRILLIANT`, `GOOD`, `INTERESTING`, `DUBIOUS`, `MISTAKE`, `BLUNDER`, `BOOK`, `NORMAL` |
| `AiPromptCategory` | `GAME`, `CHESS_SERVER_PLAYER`, `PLAYER` |

### Key Data Classes

```kotlin
data class GameUiState(...)          // ~145 fields - central UI state
data class StockfishSettings(...)    // Combined settings for 3 stages
data class BoardLayoutSettings(...)  // Board visual settings + eval bar
data class GraphSettings(...)        // Graph colors, ranges, scales
data class GeneralSettings(...)      // Pagination, sounds, username
data class MoveScore(...)            // score, isMate, mateIn, depth, nodes, nps
data class MoveDetails(...)          // san, from, to, isCapture, pieceType, clockTime
data class AnalysedGame(...)         // Stored game with all analysis data
data class AiPromptEntry(...)        // Prompt with id, name, system, prompt, instructions
data class AnalysisResult(...)       // Engine output: depth, nodes, nps, lines
data class PvLine(...)               // Principal variation: score, isMate, pv, multipv
```

### External App Dependencies

| App | Package | Required | Purpose |
|-----|---------|----------|---------|
| Stockfish 17.1 | `com.stockfish141` | Yes | Chess engine analysis |
| AI App | `com.ai` | No | AI-powered report generation |

## Analysis Stages

### 1. Preview Stage
- 50ms per move (10ms-500ms), forward, 1 thread, 8MB hash, NNUE off, non-interruptible

### 2. Analyse Stage
- 2s per move (500ms-10s), backward, 4 threads, 64MB hash, NNUE on, interruptible (tap to end)

### 3. Manual Stage
- Depth 32 (16-64), 4 threads, 128MB hash, MultiPV 3 (1-32), NNUE on, continuous real-time

## AI Integration

AI features use Android intents to the external `com.ai` app:
- **Intent action**: `com.ai.ACTION_NEW_REPORT`
- **Extras**: title, system prompt, prompt text, instructions
- **Placeholders**: `@FEN@`, `@BOARD@`, `@PLAYER@`, `@SERVER@`, `@DATE@`
- `@BOARD@` generates full HTML with chessboard.js and piece images from Lichess CDN

Three prompt categories: `GAME` (position analysis), `CHESS_SERVER_PLAYER` (online player), `PLAYER` (general player profile)

## Content Sources

**Lichess.org:** User games (NDJSON streaming), tournaments, broadcasts, TV channels, top rankings, streamers, live game following

**Local:** PGN file upload (with ZIP support), ECO opening selection (A00-E99), FEN position entry (with history), previously analysed games

## Settings Persistence

All settings via `SettingsPreferences` using SharedPreferences (`eval_prefs`). Key groups:
- Stockfish per-stage: seconds/depth, threads, hash, NNUE, multiPV
- Board layout: colors, coordinates, player bars, eval bar
- Graph: colors, ranges, scales
- Interface visibility: ~23 toggles across 3 stages
- General: pagination, move sounds, Lichess username
- AI prompts: JSON list of `AiPromptEntry`
- Settings export/import: Full JSON round-trip with type preservation

## Common Tasks

### Adding a New Setting
1. Add field to data class in `GameModels.kt`
2. Add SharedPreferences key in `SettingsPreferences.kt`
3. Update load/save functions in `SettingsPreferences.kt`
4. Add UI in the appropriate settings screen
5. Use the value in relevant code

### Adding a New Content Source
1. Add API endpoint to `LichessApi.kt`
2. Add data models if needed
3. Add repository method in `LichessRepository.kt`
4. Add state fields to `GameUiState` in `GameModels.kt`
5. Add methods to `ContentSourceManager.kt`
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
2. `GameContent.kt` - Layout, player bars, result bar, eval bar
3. `AnalysisComponents.kt` - Evaluation graphs, analysis panel

### Triggering Stockfish Analysis
Use `restartAnalysisForExploringLine()` in `AnalysisOrchestrator`: stop -> newGame -> delay(100ms) -> start

## UI Conventions

- **No popups**: All views are full-screen with `EvalTitleBar`. No `AlertDialog`, `Dialog`, or `ExposedDropdownMenu`.
- **Early return pattern**: Overlay screens use `if (showX) { XScreen(...); return }`
- **Radio buttons**: Selection controls use inline radio button groups (not dropdowns)
- **Dark theme only**: Hardcoded dark color scheme via `AppColors` object
- **Title bar always visible**: `EvalTitleBar` shown on every screen
- **Color pickers**: Full-screen HSV picker with early-return pattern via `activeColorPicker` state

## Verification Checklist

- [ ] Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug`
- [ ] No `AlertDialog`, `Dialog`, or `ExposedDropdownMenu` in UI code
- [ ] Title bar visible on all screens
- [ ] Load game from Lichess
- [ ] Full analysis pipeline (Preview -> Analyse -> Manual)
- [ ] Arrow modes cycle correctly
- [ ] Settings persist across restarts
- [ ] Export features work (PGN, GIF)
- [ ] Color pickers show as full-screen views
