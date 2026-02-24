# Developer Guide

Technical documentation for developers working on the Eval Android app.

## Build Environment

| Requirement | Version |
|-------------|---------|
| Java | 17 (OpenJDK) |
| Android SDK | compileSdk 34, targetSdk 34, minSdk 26 |
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| Compose Compiler | 1.5.8 |
| Compose BOM | 2024.02.00 |

### Build Commands

```bash
# Debug build
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Release build (needs keystore in local.properties)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease

# Clean
./gradlew clean

# Deploy to device and launch
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.eval/.MainActivity
```

### Version Numbering

Version name is generated at build time as `yy.DDD.minutes` (year, day-of-year, minutes-into-day). For example, `26.055.720` means year 2026, day 55, at 12:00 noon.

### Release Signing

Configure `local.properties` with:
```properties
KEYSTORE_FILE=path/to/keystore.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

## Dependencies

### Core
| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin extensions for Android |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.7.0 | Lifecycle-aware coroutines |
| `androidx.activity:activity-compose` | 1.8.2 | Compose Activity integration |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.7.0 | ViewModel for Compose |
| `androidx.navigation:navigation-compose` | 2.7.7 | Navigation framework |

### Compose (BOM 2024.02.00)
`ui`, `ui-graphics`, `ui-tooling-preview`, `material3`

### Networking
| Library | Version | Purpose |
|---------|---------|---------|
| `com.squareup.retrofit2:retrofit` | 2.9.0 | HTTP client |
| `com.squareup.retrofit2:converter-gson` | 2.9.0 | JSON serialization |
| `com.squareup.retrofit2:converter-scalars` | 2.9.0 | Plain text responses (NDJSON) |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP transport |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.0 | Request/response logging |

### Other
| Library | Version | Purpose |
|---------|---------|---------|
| `kotlinx-coroutines-core` | 1.7.3 | Coroutines |
| `kotlinx-coroutines-android` | 1.7.3 | Android coroutine dispatchers |
| `compose-markdown` | 0.5.8 | Markdown rendering in Compose |

## Architecture

### MVVM with Jetpack Compose

```
GameViewModel (StateFlow<GameUiState>)
├── AnalysisOrchestrator  - 3-stage Stockfish pipeline
├── GameLoader             - Loading games from all sources
├── BoardNavigationManager - Move navigation, line exploration
├── ContentSourceManager   - Tournaments, broadcasts, TV, streamers
└── LiveGameManager        - Real-time game following via streaming
```

The `GameUiState` data class (~145 fields) is the single source of truth. All UI state flows through a single `MutableStateFlow<GameUiState>` in the ViewModel.

### Three-Stage Analysis Pipeline

Orchestrated by `AnalysisOrchestrator.kt`:

| Stage | Direction | Default Time | Threads | Hash | NNUE | Interruptible |
|-------|-----------|-------------|---------|------|------|---------------|
| Preview | Forward (0 -> end) | 50ms/move | 1 | 8 MB | Off | No |
| Analyse | Backward (end -> 0) | 2s/move | 4 | 64 MB | On | Yes |
| Manual | Real-time | Depth 32 | 4 | 128 MB | On | N/A (continuous) |

**Preview**: Quick scan generating initial evaluation graph. Uses `analyzeWithTime()`.
**Analyse**: Deep analysis overlaid on preview. Calculates move qualities (brilliant/good/mistake/blunder). Uses `analyzeWithTime()`.
**Manual**: Continuous depth-based analysis. Restarts on every position change. Supports MultiPV (1-32 lines). Uses `analyze()`.

### Stockfish Integration

`StockfishEngine.kt` manages an external process:
- Requires `com.stockfish141` package installed on device
- Locates binary via `nativeLibraryDir` (searches for `lib_sf171.so`)
- Communicates via UCI protocol over stdin/stdout (`ProcessBuilder`)
- Safety caps: max 256 MB hash, max 4 threads
- Thread-safe with `Mutex` for serialization, `synchronized` for PV lines
- Restart sequence: `stop()` -> `newGame()` -> `delay(100ms)` -> start analysis

### External AI App Integration

AI reports are delegated to the companion `com.ai` app via Android intents:

```kotlin
// AiAppLauncher.kt
Intent("com.ai.ACTION_NEW_REPORT").apply {
    setPackage("com.ai")
    putExtra("title", reportTitle)
    putExtra("system", systemPrompt)
    putExtra("prompt", promptText)
    putExtra("instructions", instructions)
}
```

Prompt templates use placeholders: `@FEN@`, `@BOARD@`, `@PLAYER@`, `@SERVER@`, `@DATE@`

The `@BOARD@` placeholder generates a complete HTML block with chessboard.js, Lichess piece images, player bars, and move indicators.

### Navigation

Simple flat navigation with 5 routes:
```kotlin
NavRoutes.GAME          // Start destination
NavRoutes.SETTINGS      // Settings hub
NavRoutes.HELP          // Help docs
NavRoutes.RETRIEVE      // Game retrieval
NavRoutes.PLAYER_INFO   // Player profiles
```

`RetrieveScreen` manages its own sub-screen navigation internally via `RetrieveSubScreen` enum (MAIN, LICHESS, TOP_RANKINGS_LICHESS, TOURNAMENTS, BROADCASTS, TV, STREAMERS, PGN_FILE, OPENING_SELECTION, FEN_INPUT).

### Settings Persistence

`SettingsPreferences.kt` wraps SharedPreferences (`eval_prefs`) with typed load/save methods for each settings group. Full settings export/import as JSON with type preservation (`Boolean`, `Int`, `Long`, `Float`, `String`, `StringSet`).

Key preference groups:
- Stockfish per-stage (3 groups of engine parameters)
- Board layout (colors, toggles, eval bar)
- Graph (5 colors, 2 ranges, 2 scales)
- Interface visibility (23 toggles across 3 stages)
- General (pagination, sounds, username)
- AI prompts (JSON list)
- Game storage (current game, retrieves list, analysed games, FEN history)

### UI Conventions

All UI follows these rules:

1. **No popups**: Every view is full-screen with `EvalTitleBar`. No `AlertDialog`, `Dialog`, or `ExposedDropdownMenu` anywhere.
2. **Early return pattern**: Overlay screens check a state flag and return early:
   ```kotlin
   if (uiState.showSharePositionDialog) {
       SharePositionScreen(...)
       return
   }
   ```
3. **Radio buttons for selection**: Options presented as inline `RadioButton` groups, not dropdown menus.
4. **Color pickers**: Full-screen HSV picker via `activeColorPicker` / `activeColorCallback` state pair.
5. **Dark theme only**: Hardcoded via `AppColors` object (16 centralized color constants).
6. **Title bar always visible**: `EvalTitleBar` on every screen, never hidden.

### Export Formats

| Format | File | Description |
|--------|------|-------------|
| PGN | `PgnExporter.kt` | Full PGN with headers, evaluation comments, quality annotations, clock times, 80-char wrapping |
| GIF | `GifExporter.kt` + `AnimatedGifEncoder.kt` | 420x400px animated board replay with eval bar, custom NeuQuant encoder |
| HTML | `HtmlReportBuilder.kt` | Interactive chessboard.js report with markdown-to-HTML conversion |
| Settings JSON | `SettingsPreferences.kt` | Type-preserving export/import of all SharedPreferences |

## Project Statistics

| Metric | Count |
|--------|-------|
| Kotlin files | 46 |
| Total lines | ~22,300 |
| Largest file | `RetrieveScreen.kt` (2,088 lines) |
| Data classes/enums | ~30 |
| Navigation routes | 5 |
| Settings toggles | ~40 |
| Export formats | 4 (PGN, GIF, HTML, Settings JSON) |

## Coding Conventions

- Kotlin with Jetpack Compose throughout (no XML layouts)
- `@OptIn(ExperimentalMaterial3Api::class)` used where needed
- All colors centralized in `AppColors` object
- Reusable components in `SharedComponents.kt`: `EvalTitleBar`, `ColorSettingRow`, `SettingsToggle`, `TitleBarIcon`
- Helper classes follow single-responsibility: one orchestrator/manager per concern
- No unit tests currently
- ProGuard/R8 minification disabled (`isMinifyEnabled = false`)
