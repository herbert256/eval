# Audit fixes — 28 September 2026

Fixes for the findings in [`audit-2026-09-28.html`](../audit-2026-09-28.html). Finding IDs refer to that report.
Work started from commit `076ca73` plus the uncommitted camera rework, which was kept. Nothing was committed.

## Fixed

| Area | IDs | What changed |
| --- | --- | --- |
| Crashes | UI-1, UI-2, UI-3, NET-4 | Opening list keyed by FEN, TV list by channel name, PGN game ids made unique, player-profile page clamped after rotation. |
| Lichess | NET-1, NET-2, NET-3, NET-5, NET-6 | Body logging removed (headers in debug only); TV games load from `game/export`; stream fallback honours `initialFen` and rejects variants; opening explorer uses an optional personal token (`eval_secrets`) and sends nothing without one; paging uses `until`; readable network errors. |
| Engine | ENG-1–ENG-11, CHS-3 | New `UciSession` (reader thread + channel, write lock, stop/drain/isready per search), exact scores only, options sent only when advertised, discovery fallback, non-blocking shutdown, `EngineHistory` (position + moves) so repetitions count, `lastError` for rejected positions, win-probability move qualities from one stage only, background pause, collector-based Manual loop, AI engines pause Manual analysis. |
| Chess | CHS-1, CHS-2, CHS-4–CHS-12 | One position validator (with reasons) for FEN input and Board setup, stale castling rights repaired; variants rejected; PGN parser robustness (unclosed variations, SAN spellings, glyphs, fractional clocks, `½-½`); one result source; opening names corrected; PGN export keeps source tags; underpromotion picker. |
| State | STA-1–STA-17 | Shares no longer stop analysis; failed fetches keep the game and don't save the name; one job per content section; display toggles don't restart analysis; the newest game reopens its saved analysis; games moved to `eval_games` (JSON work off the main thread, 100 games per retrieve); Activity work only in the foreground; startup/engine-install fixes; FEN positions export as local. |
| UI | UI-4–UI-17 | NavHost collects only what the menu needs; analysis panel keeps the last lines dimmed; graph drags act once per move; vertical swipes on the board scroll; signed scores and WCAG AA colours; TalkBack labels and selectable rows; per-frame allocations removed; rotation keeps typed text, open colour pickers and URL scans; page sizes from real row heights; inline radio groups instead of dropdowns; move numbering for set-up positions. |
| Import | IMP-1–IMP-11 | PGN picker uses the bounded background reader; WebView renderer crashes handled; RTF pictures, linear scanners, archive parts, UTF-8/Windows-1252 fallback, camera photo cleanup, page-scan timeout, `Error` handling, download timeouts, one shared client. |
| Security & privacy | SEC-1–SEC-9 | Trust-on-first-use signer pins for `com.stockfish141` and `com.ai`; clipboard history keeps chess content only; shares wait for **Scan**; only chess links are followed automatically; Eval's own FileProvider URIs refused; retired API keys and reports removed; legacy import skips unknown keys and caps size; backup/transfer exclude everything; SRI on CDN scripts; release builds strip debug logging; `taskAffinity=""`. |
| Export | EXP-1–EXP-6 | Equal-size GIF frames, faster palette mapping without duplicate tables, old GIFs pruned, dead `HtmlReportBuilder` removed, check sounds, share subjects, dated settings export, board colours in GIFs. |
| Settings | SET-1–SET-4 | 2.00 s back in the stepper and neighbour stepping; thread/hash options match the engine caps; multi-line arrow colour, Preview score bars and result bar work; the example username is no longer saved. |
| Build | BLD-1, BLD-2, BLD-4, BLD-5, BLD-7–BLD-10 | Release R8 passes; Gson models keep field names; blanket keeps removed; compose-markdown and JitPack removed; Gson 2.11 pinned; BouncyCastle post-quantum tables excluded; monotonic version code; checksummed wrapper. |
| Tests & docs | TST-1, TST-2, DOC-1–DOC-5 | MockWebServer tests through the real HTTP stack, stepper-default test, move-quality, explorer-token, engine protocol, GIF and import tests; Stockfish/network skip guards; CLAUDE.md, README, DEVELOPER.md, USER.md, Help and AI handoff docs updated. |

## Not changed

| IDs | Reason |
| --- | --- |
| BLD-3 | Distributing a release build instead of the debug APK needs your keystore and a change to the cloud workflow. Release R8 now passes, so this is ready when you want it. |
| BLD-6 (toolchain part) | Done afterwards: the toolchain now matches the AI app (Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20, JDK 25, compileSdk/targetSdk 37, current AndroidX, Compose BOM 2026.09.00, Retrofit 3, OkHttp 5). |
| UI-18, SET-5, NET-7 | Informational (localisation, dead settings code, nullable API models); no user-visible defect. |
| TST-3 | Replacing fixed sleeps with idle synchronisation needs the Compose test library across ~20 test files. |
| Partly | STA-5 (the explored variation is still not saved), STA-7 (still SharedPreferences, not Room), STA-18 (dead state fields and the unreachable live-follow code remain), UI-4 (no strong skipping; `GameContent` still takes the whole state), UI-10 (stepper buttons and board semantics), UI-17 (single taps still wait for the double-tap timeout while double-tap navigation is enabled), SEC-10 (title extra and WebView storage), EXP-6 (GIFs still use Unicode pieces). |

## Found while verifying

- The first Stockfish start after an install or boot can take over 15 s on a slow device; engine startup now allows 30 s (`STARTUP_TIMEOUT_MS`), per-search readiness still 15 s.
- The Stockfish settings screen showed capped thread/hash values but saved them back for every stage; it now displays the effective value and only changes what you step.
- The automatic startup/reload game load no longer closes an AI report you already opened.
- Scores that round to zero showed as "+-0.0" in engine lines; they now read "+0.0".
- ENG-7: the one-line Stockfish card that the fix brought back above the board in the Analyse stage was removed on request; the live score in the result bar stays.

## Ultrareview follow-up

A cloud review of the uncommitted fixes found four problems, fixed here:

| Problem | Fix |
| --- | --- |
| The Stockfish signer was only checked at app start; analysis restarts and the AI moves/lines engines started a re-signed binary without asking. | `StockfishEngine` checks the signer before every start and restart and reports `signerChanged`, which opens the confirmation screen; the AI engines show why they can't start. Confirming mid-game resumes the analysis on screen instead of reloading the startup game. `AppSignerTrust` moved to `data/`. |
| Moving the games to `eval_games` used `commit()` on the main thread during ViewModel creation. | `apply()` for both files; SharedPreferences writes reach disk in order, and an interrupted move is completed on the next start. |
| The engine only accepted FEN counters up to 4/5 digits, while Board setup and FEN input accept up to 1,000,000. | Both counters may have up to 7 digits. |
| The URL scanner's link list had no item keys. | Distinct links keyed by URL. |

Found while verifying: after a stuck page timed out, the URL scanner reported it before the terminated WebView renderer had exited, so a board-image scan started right then joined the dying process and failed ("Board image recognition stopped unexpectedly"). The scanner now waits (up to 5 s) until the renderer has gone.

Verification: `assembleDebug`, 160 JVM unit tests, `lintDebug` (0 errors) and `:app:minifyReleaseWithR8` pass; `EngineIdentityIntegrationTest` has a new test that a re-signed Stockfish is refused by `initialize()` and `restart()` until trusted. Instrumented on the emulator: the engine, AI engine, startup, settings-safety, AI report and bug-hunt classes, `UrlGameScannerTest` three times after the scanner fix, and the shared/camera/local-file/document import classes. The emulator data was restored byte-for-byte; the saved game reopens with Stockfish 19. The installed APK and `/Users/herbert/cloud/eval.apk` have SHA-256 `dfaedfe23837c440adc19deaecc4a2fe6e1fff28ffb3330348d7f31dabd2ae25`.

## Verification

- `assembleDebug`, 159 JVM unit tests, `lintDebug` (0 errors, 87 warnings, down from 94) and `:app:minifyReleaseWithR8` pass. The debug APK shrank from 26.9 MB to 21.4 MB.
- All 45 instrumented classes (199 tests) pass on the emulator (API 36, one CPU, Stockfish 19), run class by class after the emulator settled. A first full run on an overloaded emulator failed 50 tests, mostly because a frozen launcher and the AI app covered the screen; the real defects it exposed are listed above.
- The emulator's Eval data was backed up before testing and restored byte-for-byte afterwards; on launch the games moved to `eval_games` and the saved game reopened with Stockfish 19 at depth 32.
- The installed APK, `app/build/outputs/apk/debug/app-debug.apk` and `/Users/herbert/cloud/eval.apk` have SHA-256 `0f9ab64c40e5182bf714b7b7a08d28d01c47dfad11f97c4bd6ec75a90e7f6d76`.
