# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK (requires Java 17)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Build release APK (requires keystore in local.properties)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease

# Clean build
./gradlew clean

# Install and run on connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.chessreplay/.MainActivity
```

## Project Overview

Chess Replay is an Android app for fetching and analyzing chess games from Lichess.org using the Stockfish 17.1 chess engine. The app retrieves games via the Lichess API, parses PGN notation, and provides multi-stage computer analysis with an interactive board display.

**Key Dependencies:**
- External app required: "Stockfish 17.1 Chess Engine" (com.stockfish141) from Google Play Store
- Android SDK: minSdk 26, targetSdk 34, compileSdk 34
- Kotlin with Jetpack Compose for UI
- Retrofit for networking (Lichess API)

## Architecture

### Package Structure (19 Kotlin files, ~6,200 lines)

```
com.chessreplay/
├── MainActivity.kt (34 lines) - Entry point, sets up Compose theme
├── chess/
│   ├── ChessBoard.kt (533 lines) - Board state, move validation, FEN generation
│   └── PgnParser.kt (70 lines) - PGN parsing with clock time extraction
├── data/
│   ├── LichessApi.kt (48 lines) - Retrofit interface for Lichess NDJSON API
│   ├── LichessModels.kt (40 lines) - Data classes: LichessGame, Players, Clock
│   └── LichessRepository.kt (56 lines) - Repository with sealed Result<T> type
├── stockfish/
│   └── StockfishEngine.kt (503 lines) - UCI protocol wrapper, process management
└── ui/
    ├── GameViewModel.kt (1,431 lines) - Central state management, analysis orchestration
    ├── GameScreen.kt (370 lines) - Main screen, Lichess username input, Stockfish check
    ├── GameContent.kt (686 lines) - Game display: board, players, moves, result bar
    ├── ChessBoardView.kt (458 lines) - Canvas-based interactive chess board
    ├── AnalysisComponents.kt (425 lines) - Evaluation graph, analysis panel
    ├── MovesDisplay.kt (188 lines) - Move list with scores and piece symbols
    ├── GameSelectionDialog.kt (190 lines) - Dialog for selecting from multiple games
    ├── SettingsScreen.kt (182 lines) - Settings navigation hub
    ├── StockfishSettingsScreen.kt (495 lines) - Engine settings for all 3 stages
    ├── ArrowSettingsScreen.kt (238 lines) - Arrow display configuration
    ├── ColorPickerDialog.kt (254 lines) - HSV color picker for arrow colors
    └── theme/Theme.kt (32 lines) - Material3 dark theme
```

### Key Design Patterns

1. **MVVM with Jetpack Compose**: `GameViewModel` exposes `StateFlow<GameUiState>` with 25+ fields, UI recomposes reactively

2. **Sealed Result Type**: Network operations return `Result<T>` with `Success`/`Error` variants

3. **Three-Stage Analysis System**: Games progress through PREVIEW → ANALYSE → MANUAL stages automatically

4. **Mutex-Protected Engine Access**: `analysisMutex` ensures only one Stockfish analysis runs at a time

5. **SharedPreferences Persistence**: Settings saved with first-run detection to reset defaults on app updates

## Analysis Stages

The app uses three sequential analysis stages, each with configurable settings:

### 1. Preview Stage
- **Purpose**: Quick initial scan of all positions
- **Timing**: 100ms per move (configurable: 10ms-500ms)
- **Direction**: Forward through game (move 0 → end)
- **Settings**: 1 thread, 16MB hash, NNUE disabled
- **UI**: Board and result bar hidden, only evaluation graph shown
- **Interruptible**: No

### 2. Analyse Stage
- **Purpose**: Deep analysis focusing on critical positions
- **Timing**: 1 second per move (configurable: 500ms-10s)
- **Direction**: Backward through game (end → move 0)
- **Settings**: 2 threads, 32MB hash, NNUE enabled
- **UI**: Full display, clickable "Analyse stage" banner to skip to Manual
- **Interruptible**: Yes (click to enter Manual at biggest evaluation change)

### 3. Manual Stage
- **Purpose**: Interactive exploration with real-time analysis
- **Analysis**: Depth-based (default 24), MultiPV support (1-6 lines)
- **Settings**: 4 threads, 64MB hash, NNUE enabled
- **Features**:
  - Draggable board navigation
  - Arrow display showing top engine moves
  - Line exploration (click PV moves to explore variations)
  - Evaluation graph with current position indicator

## UI Components

### Main Display Elements

1. **Title Bar**: Reload button, menu button, "Chess Replay" title, settings gear

2. **Stage Indicator**: Shows current stage (Preview/Analyse), clickable in Analyse stage

3. **Player Bars**: Username, rating, remaining clock time (if available)

4. **Chess Board** (`ChessBoardView`):
   - Canvas-based rendering with piece images
   - Last move highlighting (yellow squares)
   - Analysis arrows (colored by player, numbered optionally)
   - Board flipping support
   - Drag gesture for move navigation

5. **Result Bar**: Current move notation with piece symbol, evaluation score, move number

6. **Navigation Controls**: |< < > >| buttons, analysis toggle, board flip, arrow toggle

7. **Evaluation Graph**:
   - Red fill for white advantage, green for black
   - Yellow line overlay for Analyse stage scores
   - Tap/drag to navigate (Manual stage only)

8. **Analysis Panel**: Stockfish 17.1 results with multiple PV lines, clickable moves

9. **Moves List**: Two-column display with piece symbols, capture notation (x), score colors

### Color Conventions
- **Score Colors**: Red = white better (+), Green = black better (-)
- **Arrows**: Blue (default) for white moves, Green (default) for black moves
- **Evaluation Graph**: Bright red (#FF5252) above axis, bright green (#00E676) below

## Stockfish Integration

### Engine Management (`StockfishEngine.kt`)

- **Requirement**: External "Stockfish 17.1 Chess Engine" app must be installed
- **Detection**: `isStockfishInstalled()` checks for `com.stockfish141` package
- **Binary Location**: Uses native library from system app (`lib_sf171.so`)
- **Process Control**: Managed via `ProcessBuilder`, UCI protocol communication
- **Safety Limits**: Hash capped at 32MB, threads capped at 4 (mobile stability)

### UCI Communication
- `uci` / `uciok` - Initialize
- `isready` / `readyok` - Synchronization
- `setoption name X value Y` - Configure (Threads, Hash, MultiPV)
- `position fen X` - Set position
- `go depth X` or `go movetime X` - Start analysis
- `stop` - Halt analysis
- `ucinewgame` - Clear hash table

### Analysis Output (`AnalysisResult`)
```kotlin
data class AnalysisResult(
    val depth: Int,
    val nodes: Long,
    val lines: List<PvLine>  // Multiple principal variations
)

data class PvLine(
    val score: Float,      // Centipawns / 100
    val isMate: Boolean,
    val mateIn: Int,
    val pv: String,        // Space-separated UCI moves
    val multipv: Int       // Line number (1-6)
)
```

## Data Flow

1. **Game Fetching**: User enters Lichess username → `LichessApi.getGames()` → NDJSON parsing → `List<LichessGame>`

2. **Game Loading**: Select game → `PgnParser.parseMovesWithClock()` → Build `boardHistory` list → Start Preview stage

3. **Analysis Flow**:
   - Preview: Iterate forward, 100ms per position, store in `previewScores`
   - Analyse: Iterate backward, 1s per position, store in `analyseScores`
   - Manual: Analyze current position on navigation, display in `AnalysisPanel`

4. **State Updates**: All changes go through `_uiState.value = _uiState.value.copy(...)` pattern

## Settings Persistence

Settings stored in `chess_replay_prefs` SharedPreferences:

- `lichess_username` - Last used username (default: "DrNykterstein")
- `lichess_max_games` - Number of games to fetch (default: 10)
- `preview_*` - Preview stage settings
- `analyse_*` - Analyse stage settings
- `manual_*` - Manual stage settings (depth, threads, hash, multiPv, arrows)
- `app_version_code` - For detecting app updates to reset defaults

## Recent Changes (Latest First)

1. **Stockfish Installation Check** - App requires external Stockfish app, shows blocking screen if not installed
2. **Score Sign Fix** - Corrected +/- prefix display
3. **Score Color Fix** - Red=white advantage, Green=black advantage consistently
4. **Lichess Only** - Removed Chess.com support for simplicity
5. **Navigation Stability** - Fixed button positions when some are hidden
6. **Capture Notation** - Use 'x' separator for captures in result bar and moves
7. **Arrow Improvements** - Default 4 arrows, show numbers, longer into target square
8. **UI Refactoring** - Split GameScreen.kt (2,813 lines) into 9 focused files

## Common Tasks

### Adding a New Setting
1. Add field to appropriate settings data class in `GameViewModel.kt`
2. Add SharedPreferences key constant
3. Update `loadStockfishSettings()` and `saveStockfishSettings()`
4. Add UI control in `StockfishSettingsScreen.kt` or `ArrowSettingsScreen.kt`
5. Use setting value in relevant analysis/display code

### Modifying Analysis Behavior
1. Check `AnalysisStage` enum and stage-specific settings classes
2. Modify `startAutoAnalysis()` for automatic analysis flow
3. Modify `configureFor*Stage()` methods for engine configuration
4. Update `processCurrentAnalysisResult()` for score collection

### Changing Board Display
1. `ChessBoardView.kt` - Canvas drawing, gestures, arrows
2. `GameContent.kt` - Layout, player bars, result bar
3. `AnalysisComponents.kt` - Evaluation graph, analysis panel
