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

Eval is an Android app for fetching and analyzing chess games from Lichess.org and Chess.com using the Stockfish 17.1 chess engine and 5 AI services (ChatGPT, Claude, Gemini, Grok, DeepSeek). The app retrieves games via APIs, parses PGN notation, and provides multi-stage computer analysis with an interactive board display.

**Key Dependencies:**
- External app required: "Stockfish 17.1 Chess Engine" (com.stockfish141) from Google Play Store
- Android SDK: minSdk 26, targetSdk 34, compileSdk 34
- Kotlin with Jetpack Compose for UI
- Retrofit for networking (Lichess, Chess.com, and AI service APIs)

## Architecture

### Package Structure (32 Kotlin files, ~14,600 lines)

```
com.eval/
├── MainActivity.kt (33 lines) - Entry point, sets up Compose theme
├── chess/
│   ├── ChessBoard.kt (550 lines) - Board state, move validation, FEN generation
│   └── PgnParser.kt (70 lines) - PGN parsing with clock time extraction
├── data/
│   ├── LichessApi.kt (48 lines) - Retrofit interface for Lichess NDJSON API
│   ├── ChessComApi.kt (87 lines) - Retrofit interface for Chess.com API
│   ├── LichessModels.kt (40 lines) - Data classes: LichessGame, Players, Clock
│   ├── LichessRepository.kt (206 lines) - Repository with ChessServer enum and dual-server support
│   ├── AiAnalysisApi.kt (264 lines) - Retrofit interfaces for 5 AI services
│   └── AiAnalysisRepository.kt (300 lines) - AI position analysis with model fetching
├── stockfish/
│   └── StockfishEngine.kt (512 lines) - UCI protocol wrapper, process management
└── ui/
    ├── GameViewModel.kt (2,245 lines) - Central state management, analysis orchestration
    ├── GameScreen.kt (1,482 lines) - Main screen, dialogs, AI analysis dialog with Chrome/email export
    ├── GameContent.kt (1,427 lines) - Game display: board, players, moves, result bar, AI logos
    ├── ChessBoardView.kt (546 lines) - Canvas-based interactive chess board with arrows
    ├── AnalysisComponents.kt (615 lines) - Evaluation graphs, analysis panel
    ├── MovesDisplay.kt (195 lines) - Move list with scores and piece symbols
    ├── GameSelectionDialog.kt (562 lines) - Dialog for selecting from multiple games
    ├── SettingsScreen.kt (342 lines) - Settings navigation hub
    ├── StockfishSettingsScreen.kt (447 lines) - Engine settings for all 3 stages
    ├── ArrowSettingsScreen.kt (336 lines) - Arrow display configuration
    ├── BoardLayoutSettingsScreen.kt (507 lines) - Board colors, pieces, coordinates, player bars, eval bar
    ├── InterfaceSettingsScreen.kt (368 lines) - UI visibility settings per stage
    ├── GraphSettingsScreen.kt (371 lines) - Evaluation graph color and range settings
    ├── AiSettingsScreen.kt (1,159 lines) - AI service settings (keys, prompts, models, export)
    ├── GeneralSettingsScreen.kt (99 lines) - General app settings
    ├── HelpScreen.kt (217 lines) - In-app help documentation
    ├── ColorPickerDialog.kt (254 lines) - HSV color picker for colors
    ├── RetrieveScreen.kt (303 lines) - Game retrieval UI screen
    ├── GameModels.kt (298 lines) - Data classes and enums (core domain models)
    ├── SettingsPreferences.kt (476 lines) - SharedPreferences persistence layer
    ├── GameStorageManager.kt (208 lines) - Game persistence and retrieval
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

// Evaluation bar position
enum class EvalBarPosition { NONE, LEFT, RIGHT }

// AI Services
enum class AiService(displayName, baseUrl) {
    CHATGPT("ChatGPT", "https://api.openai.com/"),
    CLAUDE("Claude", "https://api.anthropic.com/"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/"),
    GROK("Grok", "https://api.x.ai/"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/")
}

// Settings for each analysis stage
data class PreviewStageSettings(secondsForMove, threads, hashMb, useNnue)
data class AnalyseStageSettings(secondsForMove, threads, hashMb, useNnue)
data class ManualStageSettings(depth, threads, hashMb, multiPv, useNnue,
    arrowMode, numArrows, showArrowNumbers, whiteArrowColor, blackArrowColor, multiLinesArrowColor)

// Board appearance
data class BoardLayoutSettings(showCoordinates, showLastMove, playerBarMode, showRedBorderForPlayerToMove,
    whiteSquareColor, blackSquareColor, whitePieceColor, blackPieceColor,
    evalBarPosition, evalBarColor1, evalBarColor2, evalBarRange)

// Graph settings
data class GraphSettings(plusScoreColor, negativeScoreColor, backgroundColor,
    analyseLineColor, verticalLineColor, lineGraphRange, barGraphRange)

// Interface visibility per stage
data class PreviewStageVisibility(showScoreBarsGraph, showResultBar, showBoard, showMoveList, showPgn)
data class AnalyseStageVisibility(showScoreLineGraph, showScoreBarsGraph, showBoard, showStockfishAnalyse,
    showResultBar, showMoveList, showGameInfo, showPgn)
data class ManualStageVisibility(showResultBar, showScoreLineGraph, showScoreBarsGraph,
    showMoveList, showGameInfo, showPgn)

// AI Settings
data class AiSettings(showAiLogos, chatGptApiKey, chatGptModel, chatGptPrompt,
    claudeApiKey, claudeModel, claudePrompt, geminiApiKey, geminiModel, geminiPrompt,
    grokApiKey, grokModel, grokPrompt, deepSeekApiKey, deepSeekModel, deepSeekPrompt)

// Game storage
data class AnalysedGame(timestamp, whiteName, blackName, result, pgn, moves, moveDetails,
    previewScores, analyseScores, openingName, speed, activePlayer, activeServer)
data class RetrievedGamesEntry(accountName, server)

// UI state (40+ fields)
data class GameUiState(stockfishInstalled, isLoading, game, currentBoard,
    currentMoveIndex, analysisResult, currentStage, previewScores, analyseScores,
    isExploringLine, stockfishSettings, boardLayoutSettings, graphSettings,
    interfaceVisibility, aiSettings, showAiAnalysisDialog, aiAnalysisResult, ...)
```

### Key Design Patterns

1. **MVVM with Jetpack Compose**: `GameViewModel` exposes `StateFlow<GameUiState>`, UI recomposes reactively

2. **Three-Stage Analysis System**: Games progress through PREVIEW → ANALYSE → MANUAL stages automatically

3. **Helper Class Pattern**: Large ViewModel split into helper classes:
   - `SettingsPreferences` - All settings load/save operations
   - `GameStorageManager` - Game persistence operations

4. **Arrow System with 3 Modes**:
   - NONE: No arrows displayed
   - MAIN_LINE: Multiple arrows from PV line (1-8 arrows, colored by side, numbered)
   - MULTI_LINES: One arrow per Stockfish line with evaluation score displayed

5. **Player Bar Modes**:
   - NONE: No player bars
   - TOP: Single combined bar at top (white left, black right)
   - BOTTOM: Single combined bar at bottom
   - BOTH: Separate bars above and below board (default)

6. **Evaluation Bar**: Vertical bar showing position evaluation (LEFT, RIGHT, or NONE)

7. **AI Analysis Integration**: 5 AI services with custom prompts using @FEN@ placeholder

8. **SharedPreferences Persistence**: All settings saved via `SettingsPreferences` helper

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
- **Settings**: 2 threads, 64MB hash, NNUE enabled
- **UI**: "Analysis running - tap to end" banner (yellow text on blue)
- **Interruptible**: Yes (tap to enter Manual at biggest evaluation change)

### 3. Manual Stage
- **Purpose**: Interactive exploration with real-time analysis
- **Analysis**: Depth-based (default 32), MultiPV support (1-32 lines)
- **Settings**: 4 threads, 128MB hash, NNUE enabled
- **Features**:
  - Navigation buttons: ⏮ ◀ ▶ ⏭ ↻ (flip)
  - Three arrow modes (cycle with ↗ icon)
  - AI logos for position analysis (5 services)
  - Line exploration (click PV moves to explore variations)
  - "Back to game" button when exploring
  - Evaluation graph with tap/drag navigation

## AI Analysis Feature

### Supported Services
| Service | API Endpoint | Auth Method |
|---------|-------------|-------------|
| ChatGPT | `api.openai.com/v1/chat/completions` | Bearer token |
| Claude | `api.anthropic.com/v1/messages` | x-api-key header |
| Gemini | `generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` | Query param |
| Grok | `api.x.ai/v1/chat/completions` | Bearer token |
| DeepSeek | `api.deepseek.com/chat/completions` | Bearer token |

### Features
- **AI Logos**: Displayed next to board in Manual stage (configurable visibility)
- **Custom Prompts**: Template with @FEN@ placeholder for position injection
- **Dynamic Models**: Fetches available models from each service
- **Analysis Dialog**: Shows AI response with markdown rendering
- **View in Chrome**: Opens rich HTML report with chessboard.js, graphs, and move list
- **Send by Email**: Emails HTML report as attachment (remembers email address)
- **Export API Keys**: Export configured keys via email from settings

## UI Components

### Title Bar Icons (left to right when game loaded)
- **↻** : Reload last game
- **≡** : Return to game selection / Retrieve screen
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
│   ├── Red border for player to move (toggle)
│   ├── White/Black squares color (color pickers)
│   ├── White/Black pieces color (color pickers)
│   ├── Evaluation bar (None / Left / Right)
│   ├── Eval bar colors and range
│   └── Reset to defaults (button)
├── Show interface elements
│   ├── Preview Stage: score bars graph, result bar, board, move list, PGN
│   ├── Analyse Stage: score graphs, board, Stockfish analyse, result bar, move list, game info, PGN
│   └── Manual Stage: result bar, score graphs, move list, game info, PGN
├── Graph settings
│   ├── Plus/Negative score colors
│   ├── Background, analyse line, vertical line colors
│   └── Line graph range, bar graph range
├── Arrow settings
│   ├── Draw arrows (None / Main line / Multi lines)
│   ├── Main line: numArrows, showNumbers, white/black colors
│   └── Multi lines: arrow color
├── Stockfish
│   ├── Preview Stage: seconds, threads, hash, NNUE
│   ├── Analyse Stage: seconds, threads, hash, NNUE
│   └── Manual Stage: depth, threads, hash, multiPV, NNUE
├── AI analysis
│   ├── Show AI logos (toggle)
│   ├── ChatGPT: API key, model, custom prompt
│   ├── Claude: API key, model, custom prompt
│   ├── Gemini: API key, model, custom prompt
│   ├── Grok: API key, model, custom prompt
│   ├── DeepSeek: API key, model, custom prompt
│   └── Export API keys (button)
└── General
    └── Long tap for fullscreen (toggle)
```

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
    val nps: Long,
    val lines: List<PvLine>  // Multiple principal variations
)

data class PvLine(
    val score: Float,      // Centipawns / 100
    val isMate: Boolean,
    val mateIn: Int,
    val pv: String,        // Space-separated UCI moves
    val multipv: Int       // Line number (1-32)
)
```

## Settings Persistence

All settings managed via `SettingsPreferences` class with SharedPreferences (`eval_prefs`):

```
// Server configuration
lichess_username, lichess_max_games, chesscom_username, chesscom_max_games
active_server, active_player

// Game storage
current_game_json, retrieves_list, retrieved_games_{server}_{username}
analysed_games

// Stockfish settings (per stage)
preview_seconds, preview_threads, preview_hash, preview_nnue
analyse_seconds, analyse_threads, analyse_hash, analyse_nnue
manual_depth, manual_threads, manual_hash, manual_multipv, manual_nnue
manual_arrow_mode, manual_numarrows, manual_shownumbers
manual_white_arrow_color, manual_black_arrow_color, manual_multilines_arrow_color

// Board layout
board_show_coordinates, board_show_last_move, board_player_bar_mode
board_red_border_player_to_move
board_white_square_color, board_black_square_color
board_white_piece_color, board_black_piece_color
eval_bar_position, eval_bar_color_1, eval_bar_color_2, eval_bar_range

// Graph settings
graph_plus_score_color, graph_negative_score_color, graph_background_color
graph_analyse_line_color, graph_vertical_line_color
graph_line_range, graph_bar_range

// Interface visibility (per stage)
preview_vis_*, analyse_vis_*, manual_vis_*

// AI settings
ai_show_logos, ai_report_email
ai_chatgpt_api_key, ai_chatgpt_model, ai_chatgpt_prompt
ai_claude_api_key, ai_claude_model, ai_claude_prompt
ai_gemini_api_key, ai_gemini_model, ai_gemini_prompt
ai_grok_api_key, ai_grok_model, ai_grok_prompt
ai_deepseek_api_key, ai_deepseek_model, ai_deepseek_prompt

// General
general_long_tap_fullscreen
```

## Common Tasks

### Adding a New Setting
1. Add field to appropriate settings data class in `GameModels.kt`
2. Add SharedPreferences key constant in `SettingsPreferences.kt` companion object
3. Update corresponding load function in `SettingsPreferences.kt`
4. Update corresponding save function in `SettingsPreferences.kt`
5. Add UI control in appropriate settings screen
6. Use setting value in relevant code

### Adding a New AI Service
1. Add enum value to `AiService` in `AiAnalysisApi.kt`
2. Create Retrofit interface for the service in `AiAnalysisApi.kt`
3. Add factory method in `AiApiFactory`
4. Add analysis method in `AiAnalysisRepository.kt`
5. Add settings fields to `AiSettings` in `AiSettingsScreen.kt`
6. Add UI in `AiSettingsScreen.kt` (navigation card + settings screen)
7. Add SharedPreferences keys in `SettingsPreferences.kt`
8. Update load/save methods for AI settings

### Modifying Arrow Behavior
1. Check `ArrowMode` enum in `GameModels.kt`
2. Update `MoveArrow` data class in `ChessBoardView.kt` if needed
3. Modify arrow generation in `GameContent.kt` (around line 500)
4. Update arrow drawing in `ChessBoardView.kt` (around line 290)

### Changing Board Display
1. `ChessBoardView.kt` - Canvas drawing, gestures, arrows, piece tinting
2. `GameContent.kt` - Layout, player bars, result bar, eval bar, AI logos
3. `AnalysisComponents.kt` - Evaluation graphs, analysis panel

### Modifying HTML Report (View in Chrome)
1. Update `convertMarkdownToHtml()` in `GameScreen.kt`
2. HTML uses chessboard.js and chess.js from CDN
3. Includes: board with eval bar, player bars, AI analysis, graphs, move list, Stockfish analysis

### Triggering Stockfish Analysis
Use `restartAnalysisForExploringLine()` for proper restart sequence:
- Stops current analysis
- Sends newGame command
- Waits 100ms
- Starts fresh analysis

## File Provider Configuration

For sharing HTML reports via email, the app uses FileProvider configured in:
- `AndroidManifest.xml` - Provider declaration
- `res/xml/file_paths.xml` - Cache directory paths
