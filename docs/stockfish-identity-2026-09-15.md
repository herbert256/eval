# Engine identity — 2026-09-15

The Stockfish card now uses the engine's `id name` response from the UCI startup
handshake. It refreshes on engine restart and falls back to `Stockfish` when the
engine has not reported a name. Long build names can wrap without pushing the
depth/node counts out of the card.

The installed Android package reported app version `2.3`, while its executable
`lib_sf19.so` reported `id name Stockfish 19`. The card was visually verified as
`Stockfish 19` after a cold app launch with the original saved game.

Removed obsolete `17.1` claims from help, installation text and PGN attribution.
PGN attribution stays version-neutral because saved analyses can predate the
currently installed engine.

Validation: debug and test APK builds succeeded; 35 unit tests and five focused
emulator tests passed; lint reported zero errors and 88 existing warnings. Tests
cover release/development names, missing identity, engine restart/shutdown,
analysis startup and graph rendering. Original app files were restored and
verified byte-for-byte. Evidence: `/tmp/eval-engine-version-2026-09-15/`.
