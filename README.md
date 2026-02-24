# Eval

A chess game analysis app for Android. Fetches games from Lichess.org and provides deep three-stage analysis using the Stockfish 17.1 chess engine.

## At a Glance

- **Three-stage analysis**: Quick preview, deep analysis, interactive manual exploration
- **Stockfish 17.1**: World's strongest open-source chess engine with configurable depth, threads, hash, MultiPV, and NNUE
- **Move quality assessment**: Automatically identifies brilliant moves, mistakes, and blunders
- **Interactive board**: Customizable colors, arrow modes, evaluation bar, graph navigation
- **Multiple game sources**: Lichess user games, tournaments, broadcasts, TV, streamers, PGN files, FEN positions, ECO openings
- **Live game following**: Watch games in real-time with automatic move updates
- **AI reports**: Optional integration with companion AI app for position and player analysis
- **Export**: PGN with annotations, animated GIF, HTML reports

## Requirements

- Android 8.0 (API 26) or higher
- [Stockfish 17.1 Chess Engine](https://play.google.com/store/apps/details?id=com.stockfish141) (required)
- AI App (optional, for AI-powered analysis reports)

## Quick Start

```bash
# Build (requires Java 17)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Install and launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.eval/.MainActivity
```

## Documentation

| Document | Audience | Contents |
|----------|----------|----------|
| [USER.md](USER.md) | End users | How to use the app: analysis stages, game sources, settings, tips, troubleshooting |
| [DEVELOPER.md](DEVELOPER.md) | Developers | Build environment, dependencies, architecture, code conventions, project statistics |
| [CLAUDE.md](CLAUDE.md) | Claude Code | Package structure, key patterns, common tasks, UI conventions, verification checklist |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM with StateFlow |
| Networking | Retrofit 2.9 + OkHttp 4.12 |
| Chess Engine | Stockfish 17.1 via UCI protocol |
| Navigation | Jetpack Navigation Compose |

**46 Kotlin files, ~22,300 lines of code**

## Project Structure

```
com.eval/
├── MainActivity.kt          Entry point
├── chess/                    Board state, PGN parsing
├── data/                     Lichess API, repository, models
├── stockfish/                UCI protocol engine wrapper
├── export/                   PGN, GIF, HTML export
├── audio/                    Move sound effects
└── ui/                       Compose screens, ViewModel, settings
```

## Privacy

- All data stored locally on device
- Network requests only to Lichess.org (game retrieval) and Lichess opening explorer
- No tracking, analytics, or telemetry
- AI reports processed by external companion app (if installed)

## Acknowledgments

- [Lichess.org](https://lichess.org) -- Free chess server and API
- [Stockfish](https://stockfishchess.org) -- Open-source chess engine
- [Jetpack Compose](https://developer.android.com/jetpack/compose) -- Modern Android UI toolkit
- [chessboard.js](https://chessboardjs.com) -- HTML board visualization (used in HTML exports)

## License

Copyright (c) 2024-2026. All rights reserved.

This software is provided as-is for personal use in analyzing chess games. Redistribution or commercial use is not permitted without prior written consent from the author.

The following third-party components are used under their respective licenses:
- **Stockfish** -- GPL v3 (used as external app, not bundled)
- **Jetpack Compose, AndroidX, Material 3** -- Apache License 2.0
- **Retrofit, OkHttp** -- Apache License 2.0
- **Kotlin, Kotlinx Coroutines** -- Apache License 2.0
- **compose-markdown** -- Apache License 2.0
- **chessboard.js** -- MIT License
