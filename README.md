# Eval

A chess game analysis app for Android. Fetches games from Lichess.org and provides deep three-stage analysis using the installed Stockfish chess engine app.

## At a Glance

- **Three-stage analysis**: Quick preview, deep analysis, interactive manual exploration
- **Stockfish**: World's strongest open-source chess engine (the installed version, currently Stockfish 19) with configurable depth, threads, hash and MultiPV
- **Move quality assessment**: Automatically identifies brilliant moves, mistakes, and blunders
- **Interactive board**: Customizable colors, arrow modes, evaluation bar, graph navigation
- **Multiple game sources**: Lichess user games, tournaments, broadcasts, TV, streamers, PGN files, FEN positions, ECO openings, web pages, documents, board images, camera photos, clipboard and shares
- **AI reports**: Optional integration with companion AI app for position and player analysis
- **Export**: PGN with annotations, animated GIF

## Requirements

- Android 8.0 (API 26) or higher
- [Stockfish Chess Engine](https://play.google.com/store/apps/details?id=com.stockfish141) app, package `com.stockfish141` (required)
- [AI app](https://github.com/herbert256/ai), package `com.ai` (optional, for AI-powered analysis reports)

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
| Chess Engine | Installed Stockfish app via UCI protocol |
| Navigation | Jetpack Navigation Compose |

**About 80 Kotlin files, ~28,000 lines of application code, plus JVM and instrumented tests**

## Project Structure

```
com.eval/
├── MainActivity.kt          Entry point
├── chess/                    Board state, PGN parsing
├── data/                     Lichess API, repository, models
├── stockfish/                UCI protocol engine wrapper
├── export/                   PGN and GIF export
├── audio/                    Move sound effects
└── ui/                       Compose screens, ViewModel, settings
```

## Privacy

- All data stored locally on device; nothing is included in Android backups or device transfer
- Network requests go to Lichess.org (game retrieval, opening explorer with your own token) and to the web pages, documents and images you choose to scan. Links found inside files, clipboard entries and shares are only followed automatically for chess sites and `.pgn` files; others are listed for you to open
- Clipboard history keeps only chess content (FEN, PGN, chess links, images and documents)
- No tracking, analytics, or telemetry
- AI reports processed by the external companion app (if installed); the board HTML it receives loads chessboard.js and jQuery from public CDNs with integrity checks

## Acknowledgments

- [Lichess.org](https://lichess.org) -- Free chess server and API
- [Stockfish](https://stockfishchess.org) -- Open-source chess engine
- [Jetpack Compose](https://developer.android.com/jetpack/compose) -- Modern Android UI toolkit
- [chessboard.js](https://chessboardjs.com) -- HTML board visualization (used in the AI report board)

## License

Copyright (c) 2024-2026. All rights reserved.

This software is provided as-is for personal use in analyzing chess games. Redistribution or commercial use is not permitted without prior written consent from the author.

The following third-party components are used under their respective licenses:
- **Stockfish** -- GPL v3 (used as external app, not bundled)
- **Jetpack Compose, AndroidX, Material 3** -- Apache License 2.0
- **Retrofit, OkHttp** -- Apache License 2.0
- **Kotlin, Kotlinx Coroutines** -- Apache License 2.0
- **Gson** -- Apache License 2.0
- **PdfBox-Android** -- Apache License 2.0; **Bouncy Castle** (its dependency) -- MIT License
- **ONNX Runtime Web** -- MIT License (bundled board recognizer)
- **fenshot** board-recognition model and **chessboard detector** -- MIT License (bundled, see `app/src/main/assets/board-scanner/`)
- **chessboard.js, jQuery** -- MIT License (loaded by the AI report board, not bundled)
