# Eval

A powerful Android application for fetching and analyzing chess games from Lichess.org and Chess.com using the Stockfish 17.1 chess engine. The app provides comprehensive game analysis through an intelligent three-stage system that automatically identifies critical positions and mistakes.

## Features

### Game Retrieval
- **Dual Server Support**: Fetch recent games from both Lichess.org and Chess.com
- **Multiple Games**: Retrieve up to 25 games at once and select which to analyze
- **Quick Reload**: Reload button for instant access to most recent game from last used server/user
- **Auto-Load**: Automatically loads your most recent game on app startup (after initial setup)
- **PGN Parsing**: Full support for PGN notation including clock times, openings, and game metadata
- **Persistent Usernames**: Both Lichess and Chess.com usernames are saved for convenience

### Three-Stage Analysis System

The app uses an innovative three-stage analysis approach that progressively analyzes games:

#### 1. Preview Stage
- **Quick Scan**: Rapidly evaluates all positions (50ms per move by default)
- **Forward Direction**: Analyzes from move 1 to the end of the game
- **Visual Progress**: Shows evaluation graph building in real-time
- **Non-Interruptible**: Completes fully before proceeding to ensure complete coverage

#### 2. Analyse Stage
- **Deep Analysis**: Thorough evaluation of each position (1 second per move by default)
- **Reverse Direction**: Analyzes from the end back to move 1 (more efficient for finding mistakes)
- **Interruptible**: Tap "Analysis running - tap to end" banner to jump to the most critical position
- **Dual Graphs**: Yellow line overlay shows deep analysis vs. quick preview scores

#### 3. Manual Stage
- **Interactive Exploration**: Navigate freely through the game
- **Real-Time Analysis**: Depth-based analysis (32 ply default) at each position
- **Multiple Variations**: View up to 6 principal variations simultaneously
- **Line Exploration**: Click any move in the analysis panel to explore that variation
- **Back to Game**: Easy return to the actual game when exploring variations

### Interactive Chess Board
- **High-Quality Pieces**: Beautiful piece images with customizable colors
- **Move Highlighting**: Yellow squares show the last move played
- **Three Arrow Modes**:
  - **None**: Clean board without arrows
  - **Main Line**: Multiple arrows showing sequence of best moves (numbered 1, 2, 3...)
    - Blue arrows for White's suggested moves
    - Green arrows for Black's suggested moves
  - **Multi Lines**: One arrow per engine line with evaluation score displayed
- **Board Flipping**: Automatically flips when you played as Black, with manual toggle
- **Graph Navigation**: Tap or drag on evaluation graph to jump to any position

### Player Bars
- **Four Display Modes**:
  - **None**: No player information shown
  - **Top**: Single combined bar at top (white player left, black right)
  - **Bottom**: Single combined bar at bottom
  - **Both**: Separate bars above and below board (default)
- **Turn Indicator**: Optional red border highlights which player is to move
- **Game Result**: Shows 1 (win), 0 (loss), or ½ (draw) for each player
- **Clock Times**: Displays remaining time when available

### Board Customization
- **Square Colors**: Customize white and black square colors via HSV picker
- **Piece Colors**: Tint pieces with custom colors
- **Coordinates**: Toggle file/rank labels on/off
- **Last Move Highlight**: Toggle last move highlighting on/off
- **Reset to Defaults**: One-button reset in settings

### Evaluation Display
- **Graphical Evaluation**: Color-coded graph showing position evaluation over time
  - Red area: White has advantage (above center line)
  - Green area: Black has advantage (below center line)
  - Yellow line: Deep analysis scores overlay
- **Numerical Scores**: Precise centipawn evaluation for each move
  - Format: +1.5 (White better) or -2.3 (Black better)
  - Mate scores shown as M1, M2, etc.
- **Move-by-Move Scores**: Color-coded score indicators in the moves list

### Game Information
- **Player Details**: Names, ratings, and remaining clock time for both players
- **Opening Recognition**: Displays the opening name extracted from PGN
- **Game Source**: Shows "lichess.org" badge
- **Result Display**: Current move notation with piece symbol, coordinates, and evaluation
- **Background Color**: Screen background indicates game result (green=win, red=loss, blue=draw)

### Configurable Settings

#### Board Layout
- Show/hide coordinates (toggle)
- Show/hide last move highlight (toggle)
- Player bar(s): None / Top / Bottom / Both
- Red border for player to move (toggle)
- Customize square colors (HSV color picker)
- Customize piece colors (HSV color picker)
- Reset to defaults button

#### Interface Elements (per stage)
Configure which UI elements are visible in each analysis stage:
- **Preview Stage**: Move list, board, game info, PGN
- **Analyse Stage**: Move list, score graphs (line & bars), result bar, game info, board, PGN
- **Manual Stage**: Result bar, score graphs (line & bars), move list, game info, PGN

#### Arrow Settings
- **Draw Arrows**: None / Main line / Multi lines
- **Main Line Settings**:
  - Number of arrows: 1-8
  - Show move numbers on arrows
  - White move arrow color (HSV picker)
  - Black move arrow color (HSV picker)
- **Multi Lines Settings**:
  - Arrow color (HSV picker)

#### Stockfish Settings (per stage)

**Preview Stage:**
- Analysis time per move: 10ms - 500ms
- Threads: 1-2
- Hash table size: 8-64 MB
- NNUE neural network: On/Off

**Analyse Stage:**
- Analysis time per move: 500ms - 10 seconds
- Threads: 1-4
- Hash table size: 16-128 MB
- NNUE neural network: On/Off

**Manual Stage:**
- Search depth: 16-32 ply
- Threads: 1-12
- Hash table size: 32-256 MB
- MultiPV (variations): 1-6 lines
- NNUE neural network: On/Off

## Requirements

### System Requirements
- Android 8.0 (API 26) or higher
- Approximately 50 MB storage space

### Required External App
**Important**: This app requires the external "Stockfish 17.1 Chess Engine" app to be installed from the Google Play Store. The app will display a blocking screen with installation instructions if Stockfish is not detected.

The Stockfish app package: `com.stockfish141`

## Installation

### From APK
1. Install "Stockfish 17.1 Chess Engine" from Google Play Store
2. Download the Eval APK
3. Enable "Install from unknown sources" if prompted
4. Install and launch Eval

### Building from Source
```bash
# Clone the repository
git clone https://github.com/your-repo/Eval.git
cd Eval

# Build debug APK (requires Java 17)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.eval/.MainActivity
```

## Usage Guide

### Getting Started
1. Launch the app (ensure Stockfish is installed)
2. Enter a Lichess username (default: "DrNykterstein" - Magnus Carlsen)
3. Set the number of games to retrieve (default: 10)
4. Tap "Retrieve last X games" or "Retrieve last game"

### During Analysis
1. **Preview Stage**: Watch the evaluation graph build - this takes about 5-10 seconds for a typical game
2. **Analyse Stage**: Watch the yellow analysis line appear - tap "Analysis running - tap to end" to skip ahead
3. **Manual Stage**: Navigate freely, explore variations, and examine positions in detail

### Navigation Controls
- **⏮** : Go to start of game
- **◀** : Previous move
- **▶** : Next move
- **⏭** : Go to end of game
- **↻** : Flip board

### Top Bar Controls
- **↻** : Reload latest game from Lichess
- **≡** : Return to game list
- **↗** : Cycle arrow mode (None → Main line → Multi lines)
- **⚙** : Open settings
- **?** : Open help screen

### Exploring Variations
1. In Manual stage, view the analysis panel showing top engine moves
2. Click any move in a variation line to explore that position
3. The board updates to show the variation
4. Use "Back to game" button to return
5. Click the main moves list to return to the actual game position

## Technical Architecture

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: Retrofit with OkHttp
- **Async Operations**: Kotlin Coroutines with StateFlow
- **Chess Engine**: Stockfish 17.1 via UCI protocol

### Project Structure
```
com.eval/
├── MainActivity.kt              # App entry point
├── chess/
│   ├── ChessBoard.kt           # Board state and move validation
│   └── PgnParser.kt            # PGN parsing with clock times
├── data/
│   ├── LichessApi.kt           # Retrofit API interface
│   ├── LichessModels.kt        # Data models
│   └── LichessRepository.kt    # Repository pattern implementation
├── stockfish/
│   └── StockfishEngine.kt      # UCI protocol wrapper
└── ui/
    ├── GameViewModel.kt        # Central state management (~2,040 lines)
    ├── GameScreen.kt           # Main screen UI
    ├── GameContent.kt          # Game display components (~1,030 lines)
    ├── ChessBoardView.kt       # Canvas-based board with arrows
    ├── AnalysisComponents.kt   # Evaluation graph & panel
    ├── MovesDisplay.kt         # Moves list
    ├── SettingsScreen.kt       # Settings navigation
    ├── BoardLayoutSettingsScreen.kt  # Board appearance & player bars
    ├── InterfaceSettingsScreen.kt    # UI visibility per stage
    ├── ArrowSettingsScreen.kt        # Arrow configuration
    ├── StockfishSettingsScreen.kt    # Engine settings
    ├── HelpScreen.kt           # In-app help
    └── ColorPickerDialog.kt    # HSV color picker
```

### Key Design Decisions

1. **External Stockfish Dependency**: Uses the system-installed Stockfish app rather than bundling the engine, reducing APK size and leveraging optimized builds.

2. **Three-Stage Analysis**: The Preview→Analyse→Manual flow provides immediate feedback while ensuring thorough analysis, with the ability to skip ahead when desired.

3. **Score Perspective**: All scores are displayed from White's perspective for consistency - positive scores favor White (shown in red), negative scores favor Black (shown in green).

4. **Arrow System**: Three modes provide flexibility - no arrows for clean viewing, main line for sequential analysis, and multi-lines for comparing alternatives.

5. **Player Bar Modes**: Four modes (None/Top/Bottom/Both) allow users to customize information display based on preference and screen size.

6. **Piece Color Tinting**: Uses white piece images with color modulation for custom colors, allowing any color for both white and black pieces.

## License

This project is provided as-is for personal use in analyzing chess games.

## Acknowledgments

- **Lichess.org** - For providing an excellent free API for game retrieval
- **Chess.com** - For providing a public API for game retrieval
- **Stockfish Team** - For the world's strongest open-source chess engine
- **Jetpack Compose** - For modern Android UI development

---

*Eval - Understand your games, improve your play.*
