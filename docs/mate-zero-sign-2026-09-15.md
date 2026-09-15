# Mate-zero winner display — 15 September 2026

The reported screen showed White's `Qxf8#`, a 1–0 result, a red `-M0` live score, and a final graph segment below zero. The current game was backed up before testing. Local evidence is in `/tmp/eval-mate-zero-2026-09-15/`; personal game data and screenshots are not committed.

## Cause and correction

Both saved score maps already contained the correct White-relative numeric score `100.0`, together with `isMate=true` and `mateIn=0`. The graph and several labels used `mateIn > 0` to identify White as the winner. Integer zero has no sign, so these consumers incorrectly treated White's completed mate as a loss.

`MoveScore` now uses the existing numeric score to identify the winner when the mate distance is zero. Nonzero mates retain their signed distances. The same decision is used in the live score, preview and analyse graph layers, mate transitions in the difference graph, score labels and colors, the stored-score evaluation bar, and HTML/GIF displays. Live PV displays convert side-to-move scores to White's perspective before formatting.

Existing saved scores work with this change; no saved-game migration, reanalysis, settings-schema change, or UI layout change is required.

## Verification

- The targeted baseline JVM run reproduced the defect: the White-winning mate-zero display test failed while the nonzero-mate test passed (`baseline-unit-tests.txt`).
- The debug and instrumentation builds passed, all 33 JVM tests passed, and lint reported zero errors and 88 warnings (`validation-build.txt`).
- New Android coverage includes actual rendered pixels for both preview and analyse graph layers, White and Black mate-zero HTML scores and graphs, decoded GIF evaluation bars, and real Stockfish analysis followed by saved-game restoration for both winning colors.
- All 88 Android tests passed in 237.894 seconds (`full-emulator-tests.txt`), including all of the new rendering and engine cases.
- Restored all 15 original files byte-for-byte and removed only the 12 test-created files. After cold launch, returned to the reported game's final move. Both score displays visibly read green `+M0`, and the first graph ends at its positive maximum without the former red drop (`final-ui.xml`, `final-screen.png`). Saved games and preferences remained unchanged after launch; Android refreshed only its profile-installer marker.
- No ANR occurred during the pass, and the final app-process log contains no fatal exception.
- The final local APK, installed emulator APK, and `/Users/herbert/cloud/eval.apk` all have SHA-256 `a7fa5f941fff9d827c4de8bef3bd16fbff1b85582eb60a99fe863db860930f7e`.
