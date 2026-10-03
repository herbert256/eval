# Developer Guide

Technical documentation for developers working on the Eval Android app.

## Build Environment

| Requirement | Version |
|-------------|---------|
| Java | 25 (OpenJDK), also the bytecode target |
| Android SDK | compileSdk 37 (minor 2), build tools 37.0.0, targetSdk 37, minSdk 26 |
| Gradle | 9.8.0 (checksummed wrapper) |
| AGP | 9.4.1 (built-in Kotlin support) |
| Kotlin | 2.4.20, with the Compose compiler plugin |
| Compose BOM | 2026.09.00 |

These match the AI app (`com.ai`); upgrade both together (`gradle/libs.versions.toml`). Only minSdk differs: Eval still supports Android 8.0.

### Build Commands

```bash
# Debug build
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew assembleDebug

# Release build (needs keystore in local.properties)
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew assembleRelease

# Clean
./gradlew clean

# Deploy to device and launch
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.eval/.MainActivity
```

### Version Numbering

Version name is generated at build time as `yy.DDD.minutes` (year, day-of-year, minutes-into-day). For example, `26.055.720` means year 2026, day 55, at 12:00 noon. The version code is derived from the same timestamp (`(yy * 1000 + DDD) * 1440 + minutes`), so every newer build has a higher code and Android refuses to install an older build over it.

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
| `androidx.core:core-ktx` | 1.19.1 | Kotlin extensions for Android |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | Lifecycle-aware coroutines |
| `androidx.activity:activity-compose` | 1.13.0 | Compose Activity integration |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | ViewModel for Compose |
| `androidx.navigation:navigation-compose` | 2.10.2 | Navigation framework |

### Compose (BOM 2026.09.00)
`ui`, `ui-graphics`, `material3` (`ui-tooling` in debug builds only)

### Networking
| Library | Version | Purpose |
|---------|---------|---------|
| `com.squareup.retrofit2:retrofit` | 3.0.0 | HTTP client |
| `com.squareup.retrofit2:converter-gson` | 3.0.0 | JSON serialization |
| `com.squareup.retrofit2:converter-scalars` | 3.0.0 | Plain text responses (NDJSON) |
| `com.squareup.okhttp3:okhttp` | 5.5.0 | HTTP transport |
| `com.squareup.okhttp3:logging-interceptor` | 5.5.0 | Header logging in debug builds only (never bodies: that would buffer the live game stream) |
| `com.google.code.gson:gson` | 2.14.0 | JSON (declared directly; used outside Retrofit too) |

### Other
| Library | Version | Purpose |
|---------|---------|---------|
| `kotlinx-coroutines-core` | 1.11.0 | Coroutines |
| `kotlinx-coroutines-android` | 1.11.0 | Android coroutine dispatchers |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | Text extraction from PDF imports (BouncyCastle post-quantum tables are excluded from the APK) |

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

The `GameUiState` data class (~125 fields) is the single source of truth. All UI state flows through a single `MutableStateFlow<GameUiState>` in the ViewModel.

### Three-Stage Analysis Pipeline

Orchestrated by `AnalysisOrchestrator.kt`:

| Stage | Direction | Default Time | Threads | Hash | Interruptible |
|-------|-----------|-------------|---------|------|---------------|
| Preview | Forward (0 -> end) | 50ms/move | 1 | 8 MB | No |
| Analyse | Backward (end -> 0) | 2s/move | 4 | 64 MB | Yes |
| Manual | Real-time | Depth 32 | 4 | 128 MB | N/A (continuous) |

Stockfish 16 and later have no "Use NNUE" option; the NNUE toggles are shown and sent only when the installed engine advertises that option. Analysis pauses while Eval is in the background and resumes when it returns. Move qualities compare the mover's winning chances (Lichess' logistic scale), and only scores from the same stage are compared.

**Preview**: Quick scan generating initial evaluation graph. Uses `analyzeWithTime()`.
**Analyse**: Deep analysis overlaid on preview. Calculates move qualities (brilliant/good/mistake/blunder). Uses `analyzeWithTime()`.
**Manual**: Continuous depth-based analysis. Restarts on every position change. Supports MultiPV (1-32 lines). Uses `analyze()`.

### Stockfish Integration

`StockfishEngine.kt` manages an external process:
- Requires `com.stockfish141` package installed on device
- Locates the binary in that package's `nativeLibraryDir` (`lib_sf*.so`, e.g. `lib_sf19.so`), trying the next candidate when a handshake fails
- Trusts the package's signing certificate on first use (`AppSignerTrust`); a later install signed by someone else is only used after the user confirms. The check runs in `StockfishEngine` on every start and restart (including the dedicated AI engines), and `signerChanged` asks the user
- Communicates via UCI protocol over stdin/stdout (`ProcessBuilder`)
- Safety caps: max 256 MB hash, max 4 threads (`StockfishEngine.MAX_SAFE_HASH_MB`, `maxUsableThreads()`); the settings steppers offer only these values
- Thread-safe with `Mutex` for serialization, `synchronized` for PV lines
- Restart sequence: `stop()` -> `newGame()` -> `delay(100ms)` -> start analysis

### External AI App Integration

AI reports are delegated to the companion `com.ai` app via Android intents:

```kotlin
// AiAppLauncher.kt
Intent("com.ai.ACTION_NEW_REPORT").apply {
    setPackage("com.ai")
    putExtra("title", reportTitle)
    putExtra("instructions", instructions)  // literal <system>/<prompt> blocks plus referenced context
}
```

Only `title` and `instructions` are sent. Placeholders: `@FEN@`, `@COLOR@`, `@SERVER@`, `@PLAYER@`, `@PGN@`, `@MOVES@`, `@ENGINE@`, `@BOARD@`, `@DATE@`; only referenced context is included. See [docs/ai-handoff.md](docs/ai-handoff.md) and [CALL_AI.md](CALL_AI.md). The request is sent only while Eval is in the foreground, and only to a `com.ai` whose signer Eval has seen before (or the user confirmed).

The `@BOARD@` placeholder generates a complete HTML block with chessboard.js (loaded with subresource integrity), Lichess piece images, player bars, and move indicators.

### Navigation

Simple flat navigation with 5 routes:
```kotlin
NavRoutes.GAME          // Start destination
NavRoutes.SETTINGS      // Settings hub
NavRoutes.HELP          // Help docs
NavRoutes.RETRIEVE      // Game retrieval
NavRoutes.SHARED        // Content shared to Eval from other apps
```

`RetrieveScreen` manages its own sub-screen navigation internally via the `RetrieveSubScreen` enum (Lichess sources, PGN file, opening selection, FEN input, board setup, URL, local file, clipboard history, camera). Player profiles are an overlay of the game and retrieve screens.

### Settings Persistence

`SettingsPreferences.kt` wraps SharedPreferences (`eval_prefs`) with typed load/save methods for each settings group. Settings export/import uses typed schema v5 (v2–v4 and legacy maps are accepted; unknown legacy keys are skipped, files over 2 MB are refused).

Other preference files: `eval_games` (`GameStorageManager`: current and analysed games, retrieved lists; moved out of `eval_prefs` on first start), `eval_secrets` (the optional Lichess access token), `eval_trust` (signer fingerprints of `com.stockfish141` and `com.ai`). None of these are exported, and backup/device transfer is excluded.

Key preference groups:
- Stockfish per-stage (3 groups of engine parameters)
- Board layout (colors, toggles, eval bar)
- Graph (5 colors, 2 ranges, 2 scales)
- Interface visibility (26 toggles across 3 stages)
- General (sounds, full screen, username)
- AI setup (system prompts, prompts, instructions, last choices)
- FEN history

### UI Conventions

All UI follows these rules:

1. **No popups**: Every view is full-screen with `EvalTitleBar`. No `AlertDialog`, `Dialog`, `DropdownMenu` or `ExposedDropdownMenu`, except the AI interface editor's `<`/`@` choice popup.
2. **Early return pattern**: Overlay screens check a state flag and return early:
   ```kotlin
   if (uiState.showSharePositionDialog) {
       SharePositionScreen(...)
       return
   }
   ```
3. **Radio buttons for selection**: Options presented as inline `RadioButton` groups, not dropdown menus.
4. **Color pickers**: Full-screen HSV picker; the open picker's title is kept with `rememberSaveable` and its target is looked up from it, so it survives rotation.
5. **Dark theme only**: Hardcoded via the `AppColors` object; text colours meet WCAG AA contrast on their backgrounds.
6. **Title bar always visible**: `EvalTitleBar` on every screen, never hidden.

### Export Formats

| Format | File | Description |
|--------|------|-------------|
| PGN | `PgnExporter.kt` | Full PGN with headers, evaluation comments, quality annotations, clock times, 80-char wrapping |
| GIF | `GifExporter.kt` + `AnimatedGifEncoder.kt` | 420x430px animated board replay (move bar on every frame) in the user's square colours, custom NeuQuant encoder |
| Settings JSON | `SettingsPreferences.kt` | Type-preserving export/import of all SharedPreferences |

## Project Statistics

| Metric | Count |
|--------|-------|
| Kotlin files (main) | ~80 |
| Total lines (main) | ~28,000 |
| Largest file | `RetrieveScreen.kt` (~2,100 lines) |
| Navigation routes | 5 |
| Export formats | 3 (PGN, GIF, Settings JSON) |

## Coding Conventions

- Kotlin with Jetpack Compose throughout (no XML layouts)
- `@OptIn(ExperimentalMaterial3Api::class)` used where needed
- All colors centralized in `AppColors` object
- Reusable components in `SharedComponents.kt`: `EvalTitleBar`, `ColorSettingRow`, `SettingsToggle`, `TitleBarIcon`
- Helper classes follow single-responsibility: one orchestrator/manager per concern
- JVM unit tests in `app/src/test` (chess rules, PGN, engine protocol, HTTP stack via MockWebServer, GIF encoder, AI payloads); instrumented tests in `app/src/androidTest` (need the Stockfish app, skip otherwise)
- Release builds are minified with R8; Gson models keep their field names (`proguard-rules.pro`). Verify with `./gradlew :app:minifyReleaseWithR8`
