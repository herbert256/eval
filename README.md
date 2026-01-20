# Chess Replay

A powerful Android application for fetching and analyzing chess games from Lichess.org using the Stockfish 17.1 chess engine. The app provides comprehensive game analysis through an intelligent three-stage system that automatically identifies critical positions and mistakes.

## Features

### Game Retrieval
- **Lichess Integration**: Fetch recent games from any Lichess.org user by username
- **Multiple Games**: Retrieve up to 100 games at once and select which to analyze
- **Auto-Load**: Automatically loads your most recent game on app startup (after initial setup)
- **PGN Parsing**: Full support for PGN notation including clock times, openings, and game metadata

### Three-Stage Analysis System

The app uses an innovative three-stage analysis approach that progressively analyzes games:

#### 1. Preview Stage
- **Quick Scan**: Rapidly evaluates all positions (100ms per move by default)
- **Forward Direction**: Analyzes from move 1 to the end of the game
- **Visual Progress**: Shows evaluation graph building in real-time
- **Non-Interruptible**: Completes fully before proceeding to ensure complete coverage

#### 2. Analyse Stage
- **Deep Analysis**: Thorough evaluation of each position (1 second per move by default)
- **Reverse Direction**: Analyzes from the end back to move 1 (more efficient for finding mistakes)
- **Interruptible**: Click the stage banner to jump directly to the most critical position
- **Dual Graphs**: Yellow line overlay shows deep analysis vs. quick preview scores

#### 3. Manual Stage
- **Interactive Exploration**: Navigate freely through the game
- **Real-Time Analysis**: Depth-based analysis (24 ply default) at each position
- **Multiple Variations**: View up to 6 principal variations simultaneously
- **Line Exploration**: Click any move in the analysis panel to explore that variation

### Interactive Chess Board
- **High-Quality Pieces**: Beautiful piece images for clear visualization
- **Move Highlighting**: Yellow squares show the last move played
- **Analysis Arrows**: Colored arrows show engine's top recommended moves
  - Blue arrows for White's suggested moves
  - Green arrows for Black's suggested moves
  - Numbered arrows (1, 2, 3, 4) indicate priority ranking
- **Board Flipping**: Automatically flips when you played as Black, with manual toggle
- **Drag Navigation**: Swipe left/right on the board to navigate through moves

### Evaluation Display
- **Graphical Evaluation**: Color-coded graph showing position evaluation over time
  - Red area: White has advantage (above center line)
  - Green area: Black has advantage (below center line)
  - Click/drag on graph to jump to any position (Manual stage)
- **Numerical Scores**: Precise centipawn evaluation for each move
  - Format: +1.5 (Black better by 1.5 pawns) or -2.3 (White better by 2.3 pawns)
  - Mate scores shown as M1, M2, etc.
- **Move-by-Move Scores**: Color-coded score indicators in the moves list

### Game Information
- **Player Details**: Names, ratings, and remaining clock time for both players
- **Opening Recognition**: Displays the opening name extracted from PGN
- **Game Source**: Shows "lichess.org" badge
- **Result Display**: Current move notation with piece symbol, coordinates, and evaluation

### Configurable Settings

#### Preview Stage Settings
- Analysis time per move: 10ms - 500ms
- Threads: 1-2
- Hash table size: 8-64 MB
- NNUE neural network: On/Off

#### Analyse Stage Settings
- Analysis time per move: 500ms - 10 seconds
- Threads: 1-4
- Hash table size: 16-128 MB
- NNUE neural network: On/Off

#### Manual Stage Settings
- Search depth: 16-32 ply
- Threads: 1-12
- Hash table size: 32-256 MB
- MultiPV (variations): 1-6 lines
- NNUE neural network: On/Off

#### Arrow Display Settings
- Enable/disable arrows
- Number of arrows: 1-8
- Show move numbers on arrows
- Custom colors for White and Black arrows (HSV color picker)

## Requirements

### System Requirements
- Android 8.0 (API 26) or higher
- Approximately 50 MB storage space

### Required External App
**Important**: This app requires the external "Stockfish 17.1 Chess Engine" app to be installed from the Google Play Store. The app will display a blocking screen if Stockfish is not detected.

The Stockfish app package: `com.stockfish141`

## Installation

### From APK
1. Install "Stockfish 17.1 Chess Engine" from Google Play Store
2. Download the Chess Replay APK
3. Enable "Install from unknown sources" if prompted
4. Install and launch Chess Replay

### Building from Source
```bash
# Clone the repository
git clone https://github.com/your-repo/ChessReplay.git
cd ChessReplay

# Build debug APK (requires Java 17)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.chessreplay/.MainActivity
```

## Usage Guide

### Getting Started
1. Launch the app (ensure Stockfish is installed)
2. Enter a Lichess username (default: "DrNykterstein" - Magnus Carlsen)
3. Set the number of games to retrieve (default: 10)
4. Tap "Retrieve last X games" or "Retrieve last game"

### During Analysis
1. **Preview Stage**: Watch the evaluation graph build - this takes about 10 seconds for a typical game
2. **Analyse Stage**: Watch the yellow analysis line appear - click "Analyse stage" banner to skip to manual mode
3. **Manual Stage**: Navigate freely, explore variations, and examine positions in detail

### Navigation Controls
- **|<** : Go to start of game
- **<** : Previous move
- **>** : Next move
- **>|** : Go to end of game
- **Analysis toggle**: Enable/disable real-time analysis
- **Flip board**: Rotate board 180 degrees
- **Arrow toggle**: Show/hide analysis arrows

### Exploring Variations
1. In Manual stage, view the analysis panel showing top engine moves
2. Click any move in a variation line to explore that position
3. The board updates to show the variation
4. Use navigation buttons to step through the variation
5. Click the main moves list to return to the actual game

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
com.chessreplay/
├── MainActivity.kt           # App entry point
├── chess/
│   ├── ChessBoard.kt        # Board state and move validation
│   └── PgnParser.kt         # PGN parsing with clock times
├── data/
│   ├── LichessApi.kt        # Retrofit API interface
│   ├── LichessModels.kt     # Data models
│   └── LichessRepository.kt # Repository pattern implementation
├── stockfish/
│   └── StockfishEngine.kt   # UCI protocol wrapper
└── ui/
    ├── GameViewModel.kt     # Central state management
    ├── GameScreen.kt        # Main screen UI
    ├── GameContent.kt       # Game display components
    ├── ChessBoardView.kt    # Canvas-based board
    ├── AnalysisComponents.kt # Evaluation graph & panel
    ├── MovesDisplay.kt      # Moves list
    └── Settings screens...  # Configuration UI
```

### Key Design Decisions

1. **External Stockfish Dependency**: Uses the system-installed Stockfish app rather than bundling the engine, reducing APK size and leveraging optimized builds.

2. **Three-Stage Analysis**: The Preview→Analyse→Manual flow provides immediate feedback while ensuring thorough analysis, with the ability to skip ahead when desired.

3. **Score Perspective**: All scores are displayed from White's perspective for consistency - positive scores favor White (shown in red), negative scores favor Black (shown in green).

4. **Mutex-Protected Engine**: Only one analysis can run at a time, preventing race conditions and ensuring clean engine state.

5. **Settings Reset on Update**: App version tracking ensures new features get proper defaults after updates.

## License

This project is provided as-is for personal use in analyzing chess games.

## Acknowledgments

- **Lichess.org** - For providing the excellent free API for game retrieval
- **Stockfish Team** - For the world's strongest open-source chess engine
- **Jetpack Compose** - For modern Android UI development

---

*Chess Replay - Understand your games, improve your play.*
