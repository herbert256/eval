# Manual board swipe navigation — 2026-09-15

In Manual mode, a horizontal swipe left goes to the previous move; a swipe right
goes to the next. The mapping remains the same when the board is flipped, and
navigation uses the existing move/variation controls and their boundary checks.

Swipes start on empty squares or opponent pieces. Starting on a piece belonging
to the side to move retains piece dragging, including cancellation for illegal
or outside-board drops. Short, mostly vertical, diagonal and cancelled gestures
do not navigate. Turning Manual interaction off cancels any pending swipe.

Validation:

- Debug/test APK builds and all 35 unit tests passed; lint: no errors, 88 existing warnings.
- All 10 focused emulator tests passed (swipes, piece dragging and variations).
  The test waits for Compose to apply interaction-mode changes before sending the next gesture.
- On the original saved game, left/right swipes moved back/forward one ply.
  Swiping left at the initial position and right at checkmate did not move past
  the game boundaries; swiping right from the initial position played the first move.
- Original app files were backed up and restored byte-for-byte after testing.

Local evidence: `/tmp/eval-board-swipe-2026-09-15/`.
