# Flappy Chess contributor guide

## Project and commands

This is a Java 17 desktop libGDX chess game controlled by two flying birds.
It is an independent port of the sibling `../flappy-birb` JavaFX project;
make changes in this repository and preserve the original project.
Read `README.md` for setup, controls, distribution details, and attribution.

Use the checked-in Gradle wrapper from the repository root:

- `./gradlew :core:test`: model, input, session, and coordinate tests; no display required.
- `./gradlew build`: compile, test, and package both modules.
- `./gradlew :lwjgl3:run`: launch the desktop game (requires a display and OpenGL).
- `./gradlew :lwjgl3:run --args=--smoke-test`: render 30 frames and exit.
- `./gradlew :lwjgl3:installDist`: generate the launcher under
  `lwjgl3/build/install/flappy-chess/bin/flappy-chess`.

Use `gradlew.bat` on Windows. Dependency versions live in `gradle.properties`;
the Gradle version and checksum live in `gradle/wrapper/gradle-wrapper.properties`.
Dependencies use Maven Central and JitPack, restricted to chesslib's group.

## Architecture

Java packages are under `com.example.flappychess` in `core/src/main/java`:

- `model/Birb`: bird position, velocity, gravity, flapping, horizontal motion,
  and bounds. Keep the existing `Birb` spelling.
- `model/ChessGameModel`: chesslib board, legal moves, promotion, and game status.
- `model/ChessClock`: elapsed-time accounting for each side.
- `controller/BirbController`: held movement keys and one flap per key press.
- `controller/ChessController`: pickup/drop, selection, promotion, and SAN history.
- `controller/GameSession`: input routing, both birds, fixed-step simulation,
  real-time clocks, completed moves, and game-over state.
- `view/ChessBoardView`: presentation state and square mapping, independent of graphics.
- `view/GameRenderer`: OpenGL drawing, viewport, fonts, sprites, HUD, and overlays.
- `FlappyChessGame`: libGDX lifecycle, input multiplexer, restart, and smoke-test exit.

`lwjgl3/src/main/java/.../lwjgl3/Lwjgl3Launcher` configures the desktop window,
focus handling, and launcher arguments. `assets` is bundled through the core
module's resources. Tests mirror the core packages in `core/src/test/java`.

## Gameplay and timing invariants

- The board uses 800x800 logical pixels with a top-left origin and 100px squares.
  Birds are 50x50; their centers determine pickup/drop squares.
- The renderer converts to libGDX's bottom-left coordinates and uses a
  1200x800 fit viewport with the board between side panels. Window resizing
  must not change model coordinates or square selection.
- Both birds simulate continuously. White falls down; black falls up.
  Both use the same physics tuning with opposite gravity directions.
- `GameSession` advances physics at 60 Hz, with catch-up delta capped at 0.25s.
  `Birb` velocities are pixels per step; accelerations are pixels per step squared.
  Tune constants in `Birb`, not the render rate or chess clock. Current targets:
  216px/s horizontal travel, roughly 100px flap height, a 0.7s climb, and
  252px/s terminal fall speed.
- Horizontal motion accelerates, reverses, and coasts; bounds stop outward velocity.
  Flapping requires a new key press. Focus loss releases held input.
- Only the active side may carry a piece. Dropping on the starting square
  cancels pickup; an illegal destination keeps the piece carried.
- Promotion waits for Q/R/B/N before completing the move and switching turns.
- Each side starts with five minutes. The clock uses monotonic real time,
  including during promotion and focus loss. Charge time to the mover before
  switching sides; do not derive clock time from the capped physics delta.
- Game over freezes play. R or the Play again button starts a new session;
  Escape exits. White uses A/D, Space, E; black uses Left/Right, Up, Enter.

## Change and validation guidance

Keep game logic testable without an OpenGL context; rendering and GPU resources
belong in the renderer/lifecycle layer. Use chesslib for legality and outcomes.
Follow the surrounding Java style and avoid unrelated refactors or dependency changes.
Where appropriate, split each task into several focused commits, each covering one coherent change.

For physics or input changes, run `:core:test`; preserve mirrored gravity, bounds,
single-press flaps, focus release, and render-rate independence. Adjust existing
tuning assertions when behavior intentionally changes. Use injected clocks in
session tests instead of sleeps. Run `build` for changes spanning modules or packaging.
For rendering/assets/lifecycle changes, also run the graphical smoke test when
a display is available; report any check that could not run. A smoke test does
not replace manual assessment of gameplay feel.

Dispose owned GPU resources. Audio is currently disabled and there are no sound
assets. Preserve the existing image attribution and the bundled font's SIL Open
Font License (`assets/fonts/OFL.txt`). Do not introduce JavaFX/FXGL dependencies.
Do not edit generated `build/` outputs or commit local caches.
