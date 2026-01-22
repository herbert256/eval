# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Deploy Commands

```bash
# Build debug APK (requires Java 17)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Build release APK (requires keystore in local.properties)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease

# Clean build
./gradlew clean

# Deploy to emulator (also copy to cloud folder)
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.eval/.MainActivity && \
cp app/build/outputs/apk/debug/app-debug.apk /Users/herbert/cloud/
```

## Project Overview

Eval is an Android app for fetching and analyzing chess games from Lichess.org and Chess.com using the Stockfish 17.1 chess engine. The app retrieves games via both APIs, parses PGN notation, and provides multi-stage computer analysis with an interactive board display.

**Key Dependencies:**
- External app required: "Stockfish 17.1 Chess Engine" (com.stockfish141) from Google Play Store
- Android SDK: minSdk 26, targetSdk 34, compileSdk 34
- Kotlin with Jetpack Compose for UI
- Retrofit for networking (Lichess and Chess.com APIs)

## Architecture

### Package Structure (23 Kotlin files, ~8,900 lines)

```
com.eval/
├── MainActivity.kt (33 lines) - Entry point, sets up Compose theme
├── chess/
│   ├── ChessBoard.kt (550 lines) - Board state, move validation, FEN generation
│   └── PgnParser.kt (70 lines) - PGN parsing with clock time extraction
├── data/
│   ├── LichessApi.kt (48 lines) - Retrofit interface for Lichess NDJSON API
│   ├── ChessComApi.kt (88 lines) - Retrofit interface for Chess.com API
│   ├── LichessModels.kt (40 lines) - Data classes: LichessGame, Players, Clock
│   └── LichessRepository.kt (207 lines) - Repository with ChessServer enum and dual-server support
├── stockfish/
│   └── StockfishEngine.kt (504 lines) - UCI protocol wrapper, process management
└── ui/
    ├── GameViewModel.kt (2,100 lines) - Central state management, analysis orchestration
    ├── GameScreen.kt (590 lines) - Main screen, dual-server cards (Lichess/Chess.com), Stockfish check
    ├── GameContent.kt (1,030 lines) - Game display: board, players, moves, result bar
    ├── ChessBoardView.kt (554 lines) - Canvas-based interactive chess board with arrows
    ├── AnalysisComponents.kt (611 lines) - Evaluation graph, analysis panel
    ├── MovesDisplay.kt (195 lines) - Move list with scores and piece symbols
    ├── GameSelectionDialog.kt (190 lines) - Dialog for selecting from multiple games
    ├── SettingsScreen.kt (230 lines) - Settings navigation hub
    ├── StockfishSettingsScreen.kt (447 lines) - Engine settings for all 3 stages
    ├── ArrowSettingsScreen.kt (336 lines) - Arrow display configuration
    ├── BoardLayoutSettingsScreen.kt (333 lines) - Board colors, pieces, coordinates, player bars
    ├── InterfaceSettingsScreen.kt (334 lines) - UI visibility settings per stage
    ├── HelpScreen.kt (217 lines) - In-app help documentation
    ├── ColorPickerDialog.kt (254 lines) - HSV color picker for colors
    └── theme/Theme.kt (32 lines) - Material3 dark theme
```

### Key Data Classes

```kotlin
// Chess servers
enum class ChessServer { LICHESS, CHESS_COM }

// Analysis stages
enum class AnalysisStage { PREVIEW, ANALYSE, MANUAL }

// Arrow modes
enum class ArrowMode { NONE, MAIN_LINE, MULTI_LINES }

// Player bar display modes
enum class PlayerBarMode { NONE, TOP, BOTTOM, BOTH }

// Settings for each analysis stage
data class PreviewStageSettings(secondsForMove, threads, hashMb, useNnue)
data class AnalyseStageSettings(secondsForMove, threads, hashMb, useNnue)
data class ManualStageSettings(depth, threads, hashMb, multiPv, useNnue,
    arrowMode, numArrows, showArrowNumbers, whiteArrowColor, blackArrowColor, multiLinesArrowColor)

// Board appearance
data class BoardLayoutSettings(showCoordinates, showLastMove, playerBarMode, showRedBorderForPlayerToMove,
    whiteSquareColor, blackSquareColor, whitePieceColor, blackPieceColor)

// Interface visibility per stage
data class PreviewStageVisibility(showMoveList, showBoard, showGameInfo, showPgn)
data class AnalyseStageVisibility(showMoveList, showScoreLineGraph, showScoreBarsGraph, showResultBar, showGameInfo, showBoard, showPgn)
data class ManualStageVisibility(showResultBar, showScoreLineGraph, showScoreBarsGraph, showMoveList, showGameInfo, showPgn)
data class InterfaceVisibilitySettings(previewStage, analyseStage, manualStage)

// UI state (30+ fields)
data class GameUiState(stockfishInstalled, isLoading, game, currentBoard,
    currentMoveIndex, analysisResult, currentStage, previewScores, analyseScores,
    isExploringLine, stockfishSettings, boardLayoutSettings, interfaceVisibility, ...)
```

### Key Design Patterns

1. **MVVM with Jetpack Compose**: `GameViewModel` exposes `StateFlow<GameUiState>`, UI recomposes reactively

2. **Three-Stage Analysis System**: Games progress through PREVIEW → ANALYSE → MANUAL stages automatically

3. **Arrow System with 3 Modes**:
   - NONE: No arrows displayed
   - MAIN_LINE: Multiple arrows from PV line (1-8 arrows, colored by side, numbered)
   - MULTI_LINES: One arrow per Stockfish line with evaluation score displayed

4. **Player Bar Modes**:
   - NONE: No player bars
   - TOP: Single combined bar at top (white left, black right)
   - BOTTOM: Single combined bar at bottom
   - BOTH: Separate bars above and below board (default)

5. **Piece Color Tinting**: Uses white piece images with ColorFilter.Modulate for custom colors

6. **SharedPreferences Persistence**: All settings saved, with version tracking for defaults reset on app updates

## Analysis Stages

### 1. Preview Stage
- **Purpose**: Quick initial scan of all positions
- **Timing**: 50ms per move (configurable: 10ms-500ms)
- **Direction**: Forward through game (move 0 → end)
- **Settings**: 1 thread, 8MB hash, NNUE disabled
- **UI**: Board hidden by default, evaluation graph shown
- **Interruptible**: No

### 2. Analyse Stage
- **Purpose**: Deep analysis focusing on critical positions
- **Timing**: 1 second per move (configurable: 500ms-10s)
- **Direction**: Backward through game (end → move 0)
- **Settings**: 2 threads, 32MB hash, NNUE enabled
- **UI**: "Analysis running - tap to end" banner (yellow text on blue)
- **Interruptible**: Yes (tap to enter Manual at biggest evaluation change)

### 3. Manual Stage
- **Purpose**: Interactive exploration with real-time analysis
- **Analysis**: Depth-based (default 32), MultiPV support (1-6 lines)
- **Settings**: 4 threads, 64MB hash, NNUE enabled
- **Features**:
  - Navigation buttons: ⏮ ◀ ▶ ⏭ ↻ (flip)
  - Three arrow modes (cycle with ↗ icon)
  - Line exploration (click PV moves to explore variations)
  - "Back to game" button when exploring
  - Evaluation graph with tap/drag navigation

## UI Components

### Title Bar Icons (left to right when game loaded)
- **↻** : Reload last game from Lichess
- **≡** : Return to game selection
- **↗** : Arrow mode toggle (cycles: None → Main line → Multi lines)
- **⚙** : Settings
- **?** : Help screen

### Settings Structure
```
Settings (main menu)
├── Board layout
│   ├── Show coordinates (toggle)
│   ├── Show last move (toggle)
│   ├── Player bar(s) (None / Top / Bottom / Both)
│   ├── Red border for player to move (toggle, only if bars enabled)
│   ├── White/Black squares color (color pickers)
│   ├── White/Black pieces color (color pickers)
│   └── Reset to defaults (button)
├── Show interface elements
│   ├── Preview Stage: move list, board, game info, PGN
│   ├── Analyse Stage: move list, score graphs, result bar, game info, board, PGN
│   └── Manual Stage: result bar, score graphs, move list, game info, PGN
├── Arrow settings
│   ├── Card 1: Draw arrows (None / Main line / Multi lines)
│   ├── Card 2 "Main line": numArrows, showNumbers, white/black colors
│   └── Card 3 "Multi lines": arrow color
└── Stockfish
    ├── Preview Stage: seconds, threads, hash, NNUE
    ├── Analyse Stage: seconds, threads, hash, NNUE
    └── Manual Stage: depth, threads, hash, multiPV, NNUE
```

### Color Conventions
- **Score Colors**: Green = good for player (+), Red = bad for player (-)
- **Main Line Arrows**: Blue (default) for white moves, Green (default) for black moves
- **Multi Lines Arrows**: Color based on evaluation (green=good, red=bad, blue=equal)
- **Evaluation Graph**: Red above axis (white better), Green below (black better)
- **Background Color**: Changes based on game result (green=win, red=loss, blue=draw)

## Stockfish Integration

### Engine Management (`StockfishEngine.kt`)
- **Requirement**: External "Stockfish 17.1 Chess Engine" app must be installed
- **Detection**: `isStockfishInstalled()` checks for `com.stockfish141` package
- **Binary Location**: Uses native library from system app (`lib_sf171.so`)
- **Process Control**: Managed via `ProcessBuilder`, UCI protocol communication
- **Restart Sequence**: `stop()` → `newGame()` → `delay(100ms)` → start analysis

### Analysis Output
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

## Settings Persistence

SharedPreferences keys in `eval_prefs`:

```
// Lichess
lichess_username, lichess_max_games

// Chess.com
chesscom_username, chesscom_max_games

// Last server/user for reload
last_server, last_username

// Preview stage
preview_seconds, preview_threads, preview_hash, preview_nnue

// Analyse stage
analyse_seconds, analyse_threads, analyse_hash, analyse_nnue

// Manual stage
manual_depth, manual_threads, manual_hash, manual_multipv, manual_nnue
manual_arrow_mode, manual_numarrows, manual_shownumbers
manual_white_arrow_color, manual_black_arrow_color, manual_multilines_arrow_color

// Board layout
board_show_coordinates, board_show_last_move, board_player_bar_mode, board_red_border_player_to_move
board_white_square_color, board_black_square_color
board_white_piece_color, board_black_piece_color

// Interface visibility - Preview
preview_vis_movelist, preview_vis_board, preview_vis_gameinfo, preview_vis_pgn

// Interface visibility - Analyse
analyse_vis_movelist, analyse_vis_scorelinegraph, analyse_vis_scorebarsgraph
analyse_vis_resultbar, analyse_vis_gameinfo, analyse_vis_board, analyse_vis_pgn

// Interface visibility - Manual
manual_vis_resultbar, manual_vis_scorelinegraph, manual_vis_scorebarsgraph
manual_vis_movelist, manual_vis_gameinfo, manual_vis_pgn

// App versioning
first_game_retrieved_version
```

## Common Tasks

### Adding a New Setting
1. Add field to appropriate settings data class in `GameViewModel.kt`
2. Add SharedPreferences key constant in companion object
3. Update corresponding load function (`loadStockfishSettings()`, `loadBoardLayoutSettings()`, or `loadInterfaceVisibilitySettings()`)
4. Update corresponding save function
5. Add UI control in appropriate settings screen
6. Use setting value in relevant code

### Modifying Arrow Behavior
1. Check `ArrowMode` enum in `GameViewModel.kt`
2. Update `MoveArrow` data class in `ChessBoardView.kt` if needed
3. Modify arrow generation in `GameContent.kt` (around line 410)
4. Update arrow drawing in `ChessBoardView.kt` (around line 290)

### Changing Board Display
1. `ChessBoardView.kt` - Canvas drawing, gestures, arrows, piece tinting
2. `GameContent.kt` - Layout, player bars (PlayerBar, CombinedPlayerBar), result bar
3. `AnalysisComponents.kt` - Evaluation graph, analysis panel

### Modifying Player Bars
1. `PlayerBarMode` enum in `GameViewModel.kt` controls display mode
2. `PlayerBar` composable in `GameContent.kt` for BOTH mode (separate bars)
3. `CombinedPlayerBar` composable in `GameContent.kt` for TOP/BOTTOM mode (split bar)
4. `showRedBorderForPlayerToMove` controls turn indicator

### Triggering Stockfish Analysis
Use `restartAnalysisForExploringLine()` for proper restart sequence:
- Stops current analysis
- Sends newGame command
- Waits 100ms
- Starts fresh analysis
