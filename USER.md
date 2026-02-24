# User Guide

Complete guide for using the Eval chess analysis app.

## What is Eval?

Eval is an Android app that fetches your chess games from Lichess.org and analyzes them using the Stockfish 17.1 chess engine. It automatically identifies your mistakes, blunders, and brilliant moves, and lets you explore any position with real-time engine analysis.

## Requirements

- **Android 8.0** (Oreo) or higher
- **Stockfish 17.1 Chess Engine** app from Google Play Store (required)
- **AI App** (`com.ai`) from Google Play Store (optional, for AI-powered reports)
- Internet connection for fetching games

## Getting Started

### First Launch

1. Install "Stockfish 17.1 Chess Engine" from the Google Play Store
2. Install and launch Eval
3. If Stockfish is not detected, the app shows installation instructions and checks automatically every 2 seconds

### Loading Your First Game

1. Tap the **menu icon** (three lines) in the top-left corner
2. Enter your **Lichess username**
3. Set the number of games to retrieve
4. Tap **Retrieve**
5. Select a game from the list
6. Analysis starts automatically

## The Three Analysis Stages

Every game goes through three stages automatically:

### Stage 1: Preview (Orange Banner)

A quick scan of every position (about 50 milliseconds per move). You'll see the evaluation graph build in real-time as the engine works through the game from start to finish. This stage cannot be interrupted.

### Stage 2: Analyse (Blue Banner)

A deep analysis working backward from the last move to the first (about 2 seconds per move). This stage identifies critical positions and classifies each move:

| Symbol | Quality | What It Means |
|--------|---------|---------------|
| !! | Brilliant | Exceptional move |
| ! | Good | Strong move |
| !? | Interesting | Creative but risky |
| ?! | Dubious | Questionable choice |
| ? | Mistake | Losing some advantage |
| ?? | Blunder | Serious error |

**Tip:** Tap the blue "Analysis running" banner at any time to skip ahead. The app jumps to the position with the biggest evaluation change (usually the most critical moment in the game).

### Stage 3: Manual (No Banner)

Interactive mode where you explore freely. The engine runs continuously, updating in real-time as you navigate through moves. This is where you spend most of your time.

## Screen Layout

### Title Bar (Top)

Always visible with these controls (left to right):
- **Menu** (three lines) - Go to game retrieval screen
- **Reload** (circular arrow) - Reload the last game
- **Settings** (gear) - Open settings
- **Help** (?) - Open help screen
- **Eval** (right side) - Return to home screen

### Board Area

The interactive chess board with:
- **Piece colors** - Customizable white and black piece colors
- **Last move highlight** - Yellow squares showing the previous move
- **Evaluation bar** - Vertical bar on left or right showing who's winning
- **Arrows** - Engine-suggested moves drawn on the board

### Result Bar

Shows the current move and evaluation:
- Move notation with piece symbol (e.g., "Nf3")
- Evaluation score (e.g., "+2.1")
- Delta from previous move (e.g., "/ -0.8" means this move lost 0.8 pawns)
- Color-coded: green = good move, red = bad move, blue = neutral

### Player Bars

Show player names, ratings, clock times, and game result. Can be positioned at top, bottom, both, or hidden.

### Evaluation Graphs

Two graphs showing the game flow:
- **Line graph**: Smooth evaluation curve (from Analyse stage)
- **Bar graph**: Per-move evaluation bars (from Preview stage)

Tap or drag on either graph to jump to any position.

### Navigation Buttons

At the bottom of the screen:
- **Start** - Jump to the beginning
- **Previous** - Go back one move
- **Next** - Go forward one move
- **End** - Jump to the last move
- **Flip** - Rotate the board 180 degrees

## Arrow Modes

Cycle through arrow modes by tapping the arrow icon in the title bar:

1. **None** - Clean board, no arrows
2. **Main Line** - Numbered sequence of the best moves from the current position. Blue arrows for White's moves, green for Black's.
3. **Multi Lines** - One arrow per engine variation, each showing its evaluation score

## Exploring Variations

In Manual stage:
1. The analysis panel below the board shows engine lines
2. Tap any move in a variation to explore it
3. The board updates and analysis continues from the new position
4. Tap "Back to game" to return to the actual game moves
5. Tap any move in the main move list to jump back

## Game Sources

### Lichess.org
- **User Games** - Fetch games by username
- **Tournaments** - Browse and load tournament games
- **Broadcasts** - Watch broadcast events (e.g., world championship)
- **TV Channels** - Featured live games
- **Top Rankings** - View top players by time control (bullet, blitz, rapid, classical, ultra-bullet, chess960)
- **Streamers** - Active Lichess streamers

### Local Sources
- **PGN File** - Import PGN files from your device (supports ZIP archives with multiple games)
- **Opening Study** - Start from any ECO opening code (A00-E99)
- **FEN Position** - Analyze any position by pasting a FEN string (keeps history of recent positions)
- **Analysed Games** - Access previously analyzed games

### Live Games
Select a live game from TV channels or streamers and enable auto-follow to watch in real-time with automatic move updates.

## AI Analysis (Optional)

If the companion AI app (`com.ai`) is installed, you can generate AI-powered analysis reports:

1. Load a game and reach Manual stage
2. Tap the **share icon** to open Share / Export
3. Tap **Generate AI Reports**
4. Select a prompt to use
5. The AI app opens with the position data

### AI Prompt Categories

- **Game** - Analyze the current board position (sends FEN and board image)
- **Chess Server Player** - Research a Lichess player profile
- **Player** - General chess player profile research

### Managing Prompts

Go to **Settings > AI Prompts** to create, edit, copy, or delete prompt templates. Prompts support these placeholders:
- `@FEN@` - Current position in FEN notation
- `@BOARD@` - Interactive HTML chessboard
- `@PLAYER@` - Player name
- `@SERVER@` - Chess server name (e.g., "lichess.org")
- `@DATE@` - Current date

## Export Features

From the Share / Export screen (tap share icon during a game):

- **Copy FEN** - Copy the current position to clipboard
- **Copy PGN** - Copy the annotated game to clipboard
- **Share Position** - Share position as text via any app
- **Share Annotated PGN** - Share the full PGN with evaluation comments
- **Export as Animated GIF** - Create an animated replay of the game
- **Generate AI Reports** - Launch AI analysis (requires AI app)
- **View on lichess.org** - Open the game on the Lichess website

## Player Information

Tap on any player name to view their profile:
- Rating across all time controls
- Win/loss/draw statistics
- Recent games (tap to load and analyze)
- AI player reports (if AI app is installed)

## Settings

Access via the gear icon in the title bar.

### Board Layout
- Show/hide coordinates and last move highlights
- Player bar position (none / top / bottom / both)
- Red border indicator for the side to move
- White and black square colors (full-screen color picker)
- White and black piece colors
- Evaluation bar position (none / left / right), colors, and range

### Show Interface Elements
Control which UI elements are visible during each analysis stage:
- **Preview**: Score bars graph, result bar, board, move list, PGN
- **Analyse**: Score graphs, board, Stockfish analysis, result bar, move list, game info, PGN
- **Manual**: Result bar, score graphs, time graph, opening explorer, opening name, raw Stockfish score, move list, game info, PGN

### Graph Settings
- Colors for positive/negative scores, background, analyse line, vertical line
- Range (1-10 pawns) for line and bar graphs
- Scale (50%-300%) for graph height

### Arrow Settings
- Arrow mode (none / main line / multi lines)
- Number of arrows (1-8) for main line mode
- Show/hide move numbers in arrows
- Arrow colors for white moves, black moves, and multi-line mode

### Stockfish Engine
Per-stage configuration:
- **Preview**: Time per move (10ms-500ms), threads, hash, NNUE
- **Analyse**: Time per move (500ms-10s), threads, hash, NNUE
- **Manual**: Search depth (16-64), threads (1-16), hash (32-512 MB), MultiPV (1-32), NNUE

### AI Prompts
Create and manage prompt templates for the AI app.

### General
- Rows per page for pagination (5-50)
- Move sounds on/off
- Lichess username (for score perspective coloring)

### Settings Export/Import
All settings can be exported as JSON and imported on another device or after reinstalling.

## Tips

- **Score perspective**: Evaluation scores are always shown from the active player's perspective. A positive score means the player to move is winning.
- **Background color**: The game screen background changes color based on the result: green if you won, red if you lost, blue for a draw.
- **Quick reload**: Tap the reload icon to re-fetch the latest game from the last username you searched.
- **Opening identification**: The app automatically identifies the opening (ECO code and name) as you navigate through moves.
- **Opening explorer**: In Manual stage, the opening explorer shows statistics from the Lichess database for the current position.
- **Clock time graph**: Enable the time graph in Manual stage to visualize how much time each player used per move.

## Troubleshooting

**"Stockfish not installed"**
Install "Stockfish 17.1 Chess Engine" from Google Play Store (package: `com.stockfish141`). The app checks automatically.

**Games not loading**
- Verify the username is spelled correctly
- Check your internet connection
- The Lichess API may be temporarily unavailable

**Analysis seems slow**
- Reduce threads and hash in Stockfish settings
- Lower the analysis time per move
- Disable NNUE for faster (but less accurate) analysis

**GIF export fails**
- Ensure sufficient storage space on device
- Try with a shorter game

**AI reports not working**
- Install the companion AI app (`com.ai`) from Google Play Store
- Check that prompts are configured in Settings > AI Prompts
