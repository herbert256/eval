# Eval

A powerful Android application for fetching and analyzing chess games from Lichess.org and Chess.com using the Stockfish 17.1 chess engine and AI-powered position analysis. The app provides comprehensive game analysis through an intelligent three-stage system that automatically identifies critical positions and mistakes.

## Features

### Game Retrieval
- **Dual Server Support**: Fetch recent games from both Lichess.org and Chess.com
- **Multiple Games**: Retrieve up to 25 games at once and select which to analyze
- **Quick Reload**: Reload button for instant access to most recent game from last used server/user
- **Game History**: Access previously retrieved games and analyzed games
- **Auto-Load**: Automatically loads your most recent analyzed game on app startup
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
- **Dual Graphs**: Shows deep analysis line overlaid on quick preview scores

#### 3. Manual Stage
- **Interactive Exploration**: Navigate freely through the game
- **Real-Time Analysis**: Depth-based analysis (32 ply default) at each position
- **Multiple Variations**: View up to 32 principal variations simultaneously
- **Line Exploration**: Click any move in the analysis panel to explore that variation
- **AI Analysis**: Get position insights from 5 AI services
- **Back to Game**: Easy return to the actual game when exploring variations

### AI Position Analysis

Get intelligent position analysis from 5 leading AI services:

- **ChatGPT** (OpenAI) - GPT-4o, GPT-4o-mini, and other models
- **Claude** (Anthropic) - Claude 4 Sonnet, Claude 4 Opus, and other models
- **Gemini** (Google) - Gemini 2.0 Flash, Gemini Pro, and other models
- **Grok** (xAI) - Grok-3 and other models
- **DeepSeek** - DeepSeek Chat and other models

#### AI Features
- **Custom Prompts**: Customize the analysis prompt for each service using @FEN@ placeholder
- **Dynamic Models**: Automatically fetches available models from each service
- **View in Chrome**: Opens rich HTML report with interactive chessboard, evaluation graphs, move list, and Stockfish analysis
- **Send by Email**: Email the HTML report as an attachment (remembers your email address)
- **Export API Keys**: Backup your configured API keys via email

### Interactive Chess Board
- **High-Quality Pieces**: Beautiful piece images with customizable colors
- **Move Highlighting**: Yellow squares show the last move played
- **Evaluation Bar**: Vertical bar showing position evaluation (configurable position: left, right, or hidden)
- **Three Arrow Modes**:
  - **None**: Clean board without arrows
  - **Main Line**: Multiple arrows showing sequence of best moves (numbered 1, 2, 3...)
    - Blue arrows for White's suggested moves
    - Green arrows for Black's suggested moves
  - **Multi Lines**: One arrow per engine line with evaluation score displayed
- **Board Flipping**: Automatically flips when you played as Black, with manual toggle
- **Graph Navigation**: Tap or drag on evaluation graph to jump to any position
- **AI Logos**: Tap AI service logos next to the board for instant position analysis

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
- **Evaluation Bar**: Position (left/right/none), colors, and range
- **Reset to Defaults**: One-button reset in settings

### Evaluation Display
- **Graphical Evaluation**: Color-coded graph showing position evaluation over time
  - Red area: White has advantage (above center line)
  - Green area: Black has advantage (below center line)
  - Analysis line: Shows deep analysis scores overlay
- **Evaluation Bar**: Vertical bar next to board showing current position evaluation
- **Numerical Scores**: Precise centipawn evaluation for each move
  - Format: +1.5 (White better) or -2.3 (Black better)
  - Mate scores shown as M1, M2, etc.
- **Move-by-Move Scores**: Color-coded score indicators in the moves list
- **Customizable Graph Colors**: Configure colors for positive/negative scores, background, and lines

### Game Information
- **Player Details**: Names, ratings, and remaining clock time for both players
- **Opening Recognition**: Displays the opening name extracted from PGN
- **Game Source**: Shows server badge (Lichess/Chess.com)
- **Result Display**: Current move notation with piece symbol, coordinates, and evaluation
- **Background Color**: Screen background indicates game result (green=win, red=loss, blue=draw)

### Configurable Settings

#### Board Layout
- Show/hide coordinates
- Show/hide last move highlight
- Player bar(s): None / Top / Bottom / Both
- Red border for player to move
- Customize square colors (HSV color picker)
- Customize piece colors (HSV color picker)
- Evaluation bar position: None / Left / Right
- Evaluation bar colors and range
- Reset to defaults button

#### Interface Elements (per stage)
Configure which UI elements are visible in each analysis stage:
- **Preview Stage**: Score bars graph, result bar, board, move list, PGN
- **Analyse Stage**: Score graphs (line & bars), board, Stockfish analyse, result bar, move list, game info, PGN
- **Manual Stage**: Result bar, score graphs (line & bars), move list, game info, PGN

#### Graph Settings
- Plus score color (positive evaluation)
- Negative score color
- Background color
- Analysis line color
- Vertical position indicator color
- Line graph range (-7 to +7 default)
- Bar graph range (-3 to +3 default)

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
- Threads: 1-4
- Hash table size: 8-64 MB
- NNUE neural network: On/Off

**Analyse Stage:**
- Analysis time per move: 500ms - 10 seconds
- Threads: 1-8
- Hash table size: 16-256 MB
- NNUE neural network: On/Off

**Manual Stage:**
- Search depth: 16-64 ply
- Threads: 1-16
- Hash table size: 32-512 MB
- MultiPV (variations): 1-32 lines
- NNUE neural network: On/Off

#### AI Analysis Settings
- Show/hide AI logos on board
- Per-service configuration:
  - API key (securely stored locally)
  - Model selection (dynamically fetched)
  - Custom prompt template with @FEN@ placeholder
- Export API keys via email

#### General Settings
- Long tap for fullscreen mode

## Requirements

### System Requirements
- Android 8.0 (API 26) or higher
- Approximately 50 MB storage space

### Required External App
**Important**: This app requires the external "Stockfish 17.1 Chess Engine" app to be installed from the Google Play Store. The app will display a blocking screen with installation instructions if Stockfish is not detected.

The Stockfish app package: `com.stockfish141`

### Optional: AI Service API Keys
To use AI position analysis, you'll need API keys from one or more services:
- OpenAI (ChatGPT): https://platform.openai.com/api-keys
- Anthropic (Claude): https://console.anthropic.com/
- Google (Gemini): https://makersuite.google.com/app/apikey
- xAI (Grok): https://console.x.ai/
- DeepSeek: https://platform.deepseek.com/

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
2. Tap the Lichess or Chess.com card to enter a username
3. Set the number of games to retrieve (default: 10)
4. Tap "Retrieve" to fetch games
5. Select a game to analyze

### During Analysis
1. **Preview Stage**: Watch the evaluation graph build - this takes about 5-10 seconds for a typical game
2. **Analyse Stage**: Watch the analysis line appear - tap "Analysis running - tap to end" to skip ahead
3. **Manual Stage**: Navigate freely, explore variations, use AI analysis, and examine positions in detail

### Navigation Controls
- **⏮** : Go to start of game
- **◀** : Previous move
- **▶** : Next move
- **⏭** : Go to end of game
- **↻** : Flip board

### Top Bar Controls
- **↻** : Reload latest game
- **≡** : Return to retrieve screen
- **↗** : Cycle arrow mode (None → Main line → Multi lines)
- **⚙** : Open settings
- **?** : Open help screen

### Using AI Analysis
1. Configure at least one AI service in Settings > AI Analysis
2. In Manual stage, AI logos appear next to the board
3. Tap any AI logo to analyze the current position
4. View the analysis in the popup dialog
5. Tap "View in Chrome" for a rich HTML report with board and graphs
6. Tap "Send by email" to email the report

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
- **AI Integration**: 5 AI services via REST APIs

### Project Structure
```
com.eval/
├── MainActivity.kt              # App entry point
├── chess/
│   ├── ChessBoard.kt           # Board state and move validation
│   └── PgnParser.kt            # PGN parsing with clock times
├── data/
│   ├── LichessApi.kt           # Lichess API interface
│   ├── ChessComApi.kt          # Chess.com API interface
│   ├── LichessModels.kt        # Game data models
│   ├── LichessRepository.kt    # Game retrieval repository
│   ├── AiAnalysisApi.kt        # AI service API interfaces
│   └── AiAnalysisRepository.kt # AI analysis repository
├── stockfish/
│   └── StockfishEngine.kt      # UCI protocol wrapper
└── ui/
    ├── GameViewModel.kt        # Central state management (~2,245 lines)
    ├── GameScreen.kt           # Main screen with AI dialog (~1,482 lines)
    ├── GameContent.kt          # Game display components (~1,427 lines)
    ├── ChessBoardView.kt       # Canvas-based board with arrows
    ├── AnalysisComponents.kt   # Evaluation graphs & panel
    ├── MovesDisplay.kt         # Moves list display
    ├── GameSelectionDialog.kt  # Game picker dialog
    ├── RetrieveScreen.kt       # Game retrieval screen
    ├── SettingsScreen.kt       # Settings navigation
    ├── AiSettingsScreen.kt     # AI service configuration (~1,159 lines)
    ├── BoardLayoutSettingsScreen.kt  # Board appearance & eval bar
    ├── GraphSettingsScreen.kt  # Graph color settings
    ├── InterfaceSettingsScreen.kt    # UI visibility per stage
    ├── ArrowSettingsScreen.kt        # Arrow configuration
    ├── StockfishSettingsScreen.kt    # Engine settings
    ├── GeneralSettingsScreen.kt      # General preferences
    ├── HelpScreen.kt           # In-app help
    ├── ColorPickerDialog.kt    # HSV color picker
    ├── GameModels.kt           # Data classes and enums
    ├── SettingsPreferences.kt  # Settings persistence
    └── GameStorageManager.kt   # Game storage operations
```

### Key Design Decisions

1. **External Stockfish Dependency**: Uses the system-installed Stockfish app rather than bundling the engine, reducing APK size and leveraging optimized builds.

2. **Three-Stage Analysis**: The Preview→Analyse→Manual flow provides immediate feedback while ensuring thorough analysis, with the ability to skip ahead when desired.

3. **Score Perspective**: All scores displayed from active player's perspective - positive scores mean the active player is better.

4. **Arrow System**: Three modes provide flexibility - no arrows for clean viewing, main line for sequential analysis, and multi-lines for comparing alternatives.

5. **Player Bar Modes**: Four modes (None/Top/Bottom/Both) allow users to customize information display based on preference and screen size.

6. **Evaluation Bar**: Visual representation of position evaluation that updates in real-time during analysis.

7. **AI Integration**: Five AI services with customizable prompts allow users to get varied perspectives on positions.

8. **Rich HTML Reports**: AI analysis can be viewed in Chrome with an interactive chessboard (using chessboard.js), evaluation graphs, move list, and Stockfish analysis.

9. **Helper Classes**: Large ViewModel split into `SettingsPreferences` and `GameStorageManager` for better maintainability.

## Privacy

- All data is stored locally on your device
- API keys are stored in SharedPreferences (local storage only)
- No data is sent to any server except:
  - Lichess.org/Chess.com when retrieving games
  - AI services when requesting position analysis (only the FEN position is sent)
- The "Export API Keys" feature sends keys only to the email address you specify

## License

This project is provided as-is for personal use in analyzing chess games.

## Acknowledgments

- **Lichess.org** - For providing an excellent free API for game retrieval
- **Chess.com** - For providing a public API for game retrieval
- **Stockfish Team** - For the world's strongest open-source chess engine
- **Jetpack Compose** - For modern Android UI development
- **OpenAI, Anthropic, Google, xAI, DeepSeek** - For AI analysis capabilities
- **chessboard.js** - For the HTML report chess board visualization

---

*Eval - Understand your games, improve your play.*
