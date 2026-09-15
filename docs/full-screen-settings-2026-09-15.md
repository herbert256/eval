# Full screen setting — 2026-09-15

Settings → General settings → Display now includes **Full screen**. The option
defaults to off, applies immediately, and persists across restarts. It hides both
Android system bars; an edge swipe temporarily reveals them. Display cutouts
remain protected. Settings export/import and reset include the option, while
older settings files default to off.

Validation on `emulator-5554` (API 36.1):

- Debug APK and instrumentation APK built successfully; 33 unit tests passed.
- Lint: zero errors, 88 existing warnings.
- The 90-test emulator suite passed 89 tests and exposed a screenshot timing
  issue in `MateZeroRenderingTest`: it sampled the previous Eval logo frame.
  The test now waits for the graph to reach the display, retaining the exact
  winning-side and background color assertions. All 11 affected tests passed on
  rerun, including full screen, graph rendering, settings safety and round-trip.
- Full-screen tests cover both system bars, background/foreground transitions,
  a fresh activity, activity recreation, default values and older imports.
- Manual checks confirmed the switch, expanded settings/game layout, temporary
  edge-swipe bars, automatic hiding, text-field focus and a cold process restart.

Local logs and screenshots: `/tmp/eval-fullscreen-2026-09-15/`.
The original app data was backed up before testing and restored afterward.
