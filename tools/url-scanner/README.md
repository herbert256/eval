# Bundled board recognition

`npm ci && npm run build` regenerates the JavaScript and WASM assets under
`app/src/main/assets/board-scanner`. Android builds use these checked-in assets;
Node, an account, network model downloads, and an image upload service are not
needed on the phone.

The vendored TypeScript and `chess-tiles-v2.onnx` model come from
https://github.com/scoriiu/fenshot at commit
`f964fd16de798f73db3ea0f9f1e374e4052a2665` (MIT). The fixture PNGs in
`app/src/androidTest/assets/url-scan` come from that same revision. Upstream
credits Elucidation/tensorflow_chessbot (MIT) for the board detector. ONNX Runtime
Web 1.26.0 is MIT licensed. License texts are bundled in the APK.

The recognizer reads axis-aligned 2D diagrams and screenshots. It does not read
perspective photos of physical boards. Every image result requires review;
orientation is a heuristic and side to move, castling, en passant and move
counters cannot be established from pixels. Eval defaults to white to move,
no castling/en passant and counters 0/1, and allows editing before import.

The isolated recognizer WebView serves only bundled assets and supplied image
bytes, with no network access or native JavaScript bridge. The separate page
WebView loads HTTPS pages, has no local file/content access or native bridge,
and is destroyed when leaving the URL screen.
