# Eval saved-game bug hunt — 15 September 2026

Continued from clean Eval commit `763d5b0`. This pass exercises saved-game reopening, startup restoration, and exports after restoration. Production changes are limited to `GameLoader`; no UI layout or settings-schema changes were made. AI source is unchanged.

Evidence is in `/tmp/eval-2026-09-15-pass2/`. All 15 original app-data files were backed up before testing.

## Reproduced defects

| Workflow | Before correction | Correction |
| --- | --- | --- |
| Reopen an unnamed drawn PGN | A game with a `1/2-1/2` result reopened as unfinished because both default player names were “White” and “Black.” | Read the explicit PGN result, including headerless movetext. Keep the old opening-study fallback only when there is no PGN result. |
| Reopen a rated game | Both ratings became null, so subsequent PGN exports omitted WhiteElo and BlackElo. | Restore ratings from the original PGN headers. Unknown or absent ratings remain unknown. |
| Select a previous analysed game, then restart | The selected game appeared correctly, but a fresh activity restored the newer game that had last completed analysis. | Persist the successfully reopened selection as the next startup game. Invalid starting positions cannot replace it, and an unchanged startup save is not rewritten. |
| Reopen a save with missing cached move details | Board navigation had the moves, but exported PGN had no moves. | Rebuild detailed moves and clocks while replaying the saved PGN from its starting board. Retain a cached clock only when its source and destination match the replayed move. |

The missing-details defect was reproduced with a constructed legacy/incomplete save; this pass did not find missing move details in the user's current game.

## Reproduction evidence

- The baseline `GameSwitchingStateTest` run had seven tests and four failures, one for each defect above (`baseline-restoration-tests.txt`).
- A separate real-engine test analysed a drawn game, analysed another game, selected the first from Previous Analysed Games through the ViewModel, closed the activity, and launched a fresh activity and ViewModel. Before correction it reopened the second game (`baseline-live-restoration.txt`, 42.373 seconds).
- The regression coverage also checks invalid-FEN rejection without overwriting the startup game, black-to-move custom FEN restoration, knight underpromotion, move numbering, and cached clock preservation.

## Verification

- Debug APK and instrumentation APK builds passed; all 30 JVM tests passed. Lint reported zero errors and 88 warnings (`validation-build.txt`).
- All 84 Android tests passed in 238.646 seconds (`full-emulator-tests.txt`). The new real-engine restore-and-export workflow and all five focused restoration cases passed alongside existing import, retrieval, navigation, GIF, settings, and AI-handoff checks.
- Visually reviewed the test screenshot: the reopened draw shows both half-point results, Black (2385), White (2410), and White's 0:05:00 clock (`restored-draw.png`). The export test intercepts the share intent and sends nothing.
- Used the actual Select a game → Select from previous analysed games screen to reopen the same draw. Killed process 14981 and cold-launched process 15185. After asynchronous restoration settled, the same draw and ratings were visible and Stockfish reached depth 22 (`manual-cold-settled.xml`, `manual-cold-settled.png`). The process log had no fatal exception or ANR.
- Restored all 15 original app-data files byte-for-byte, removing only ten identified files created during this pass.
- Cold-launched the original DrNykterstein–yoseph2013 game at move 35/59; the previously missing ratings now show 3200 and 2964, and Stockfish reached depth 22 (`restored-original-settled.png`). All saved-game and preference bytes remain unchanged after launch; Android refreshed only its `files/profileInstalled` marker. No ANR occurred during this pass and the final app-process log has no fatal exception.
- The final local APK, installed emulator APK, and `/Users/herbert/cloud/eval.apk` all have SHA-256 `98d8bfee9a1f5047e761eb3932527a5c12315db49badfa7fd0c1a98a9e7c264f`.
