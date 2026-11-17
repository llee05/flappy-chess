# Stockfish browser engine

This directory contains the unmodified **lite, single-threaded WebAssembly**
build from the `stockfish` npm package version 19.0.0. It runs as a separate Web
Worker and communicates with Flappy Chess only through the UCI text protocol.
The single-threaded build does not require cross-origin-isolation headers, so it
works on static hosts including GitHub Pages.

Stockfish and Stockfish.js are distributed under GNU GPL version 3. The complete
licence text is in `COPYING.txt`. Flappy Chess does not modify or link the engine
into its TeaVM program.

Corresponding source and build instructions:

- Stockfish.js 19.0.0 source: <https://github.com/nmrugg/stockfish.js/tree/v19.0.0>
- Release source archive: <https://github.com/nmrugg/stockfish.js/archive/refs/tags/v19.0.0.tar.gz>
- Exact binary package: <https://www.npmjs.com/package/stockfish/v/19.0.0>
- Upstream Stockfish source: <https://github.com/official-stockfish/Stockfish>

Shipped file checksums (SHA-256):

- `stockfish-19-lite-single.js`: `d3344124ab067fb0b90ee77873bb8e9fbf5fc01bc525fe714b0f942581e889e6`
- `stockfish-19-lite-single.wasm`: `57ac2d72312aba346760e3f173f687a8c211208e97a87268436f7f0e10bb5387`
