# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore in local.properties)
./gradlew assembleRelease

# Run instrumentation tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

Chess Replay is an Android app for fetching and analyzing chess games from Lichess.org and Chess.com using the Stockfish engine.

### Package Structure

- **`com.chessreplay.chess`** - Chess logic layer: `ChessBoard` (board state, move validation, FEN generation), `PgnParser` (extracts moves and clock times from PGN)
- **`com.chessreplay.data`** - Data layer: `LichessApi` (NDJSON), `ChessComApi` (JSON), `LichessRepository` (unified repository that converts Chess.com responses to common `LichessGame` model)
- **`com.chessreplay.stockfish`** - Engine integration: `StockfishEngine` wraps UCI protocol, manages process lifecycle, supports both system Stockfish package and bundled binary
- **`com.chessreplay.ui`** - Presentation: `GameViewModel` (central state management with 30+ fields in `GameUiState`), `GameScreen` (main Compose UI), `ChessBoardView` (Canvas-based interactive board)

### Key Patterns

- **MVVM with Jetpack Compose**: ViewModel exposes `StateFlow<GameUiState>`, UI recomposes reactively
- **Repository pattern**: `LichessRepository` abstracts both APIs, normalizes responses to `LichessGame` model
- **Sealed Result type**: `Result<T>` with `Success`/`Error` for network operations
- **Mutex-protected analysis**: Only one Stockfish analysis runs at a time via `analysisMutex`

### Analysis Modes

1. **Analyse Stage** - Auto-analysis with 2 rounds: fast pass (50ms/move) then deep pass (1000ms/move), configurable sequence (forwards/backwards/mixed)
2. **Manual Stage** - Interactive deep analysis at depth 20-24 with multiple principal variations displayed as colored arrows

### Engine Management

`StockfishEngine` attempts system Stockfish first (`com.stockfish141` package), falls back to bundled binary. Hardware-aware defaults cap hash at 32MB and threads at 4. Engine restarts automatically on settings changes, game reload, or stage switching.
