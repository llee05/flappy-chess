# Flappy Chess contributor guide

## Project and commands

This is a Java 17 libGDX chess game with desktop and TeaVM browser launchers,
controlled by two flying birds.
It is an independent port of the sibling `../flappy-birb` JavaFX project;
make changes in this repository and preserve the original project.
Read `README.md` for setup, controls, distribution details, and attribution.

Use the checked-in Gradle wrapper from the repository root:

- `./gradlew :core:test`: model, input, session, and coordinate tests; no display required.
- `./gradlew build`: compile, test, and package all modules, including the browser distribution.
- `./gradlew :lwjgl3:run`: launch the desktop game (requires a display and OpenGL).
- `./gradlew :lwjgl3:run --args=--smoke-test`: render 30 frames and exit.
- `./gradlew :lwjgl3:installDist`: generate the launcher under
  `lwjgl3/build/install/flappy-chess/bin/flappy-chess`.
- `./gradlew :web:dist`: compile the static browser game into `web/build/dist`.
- `./gradlew :web:run`: build and serve at `http://localhost:8080` (optional `-PwebPort=8088`).
- `./gradlew :web:browserTest`: browser smoke checks; requires Node 22+ and Chromium,
  or set `CHROME_BIN` to the Chrome executable.
- `npm ci --prefix web --ignore-scripts` then `./gradlew :web:peerTest`: real
  WebRTC multiplayer checks using a local test-only PeerServer and Chromium.

Use `gradlew.bat` on Windows. Dependency versions live in `gradle.properties`;
the Gradle version and checksum live in `gradle/wrapper/gradle-wrapper.properties`.
Dependencies use Maven Central and JitPack, restricted to chesslib's group.

## Architecture

Java packages are under `com.example.flappychess` in `core/src/main/java`:

- `model/Birb`: bird position, velocity, gravity, flapping, horizontal motion,
  and bounds. Keep the existing `Birb` spelling.
- `model/ChessGameModel`: chesslib board, legal moves, promotion, and game status.
- `model/ChessClock`: elapsed-time accounting for each side.
- `model/MatchSettings` and `controller/MatchSetup`: bounded time controls and setup choices.
- `controller/BirbController`: held movement keys and one flap per key press.
- `controller/ChessController`: pickup/drop, selection, promotion, and SAN history.
- `controller/GameSession`: input routing, both birds, fixed-step simulation,
  real-time clocks, completed moves, and game-over state.
- `controller/FlightSchool`: untimed, six-step pawn-delivery lesson using the
  regular session's input and physics, with both gravity directions.
- `controller/GameFeedback`: bounded, numbered presentation events for local
  and online play; graphics and audio remain in the view layer.
- `controller/PeerMatch`: portable host/guest match synchronization, input
  ownership, prediction, heartbeat, disconnects, and rematch agreement.
- `controller/PeerProtocol` and `SessionSnapshot`: versioned, bounded JSON
  messages and complete presentation snapshots; no reflection or browser APIs.
- `view/ChessBoardView`: presentation state and square mapping, independent of graphics.
- `view/GameRenderer`: OpenGL drawing, viewport, fonts, sprites, HUD, and overlays.
- `FlappyChessGame`: libGDX lifecycle, input multiplexer, restart, and smoke-test exit.

`lwjgl3/src/main/java/.../lwjgl3/Lwjgl3Launcher` configures the desktop window,
focus handling, and launcher arguments. `assets` is bundled through the core
module's resources. Tests mirror the core packages in `core/src/test/java`.

`web` uses the released gdx-teavm JavaScript backend. Its `WebLauncher` hosts the
local two-player game or `PeerGame`, `BuildWeb` compiles it, and `ServeWeb` previews the
static output using the JDK. Browser-specific code and JavaScript interop belong
in `web`; keep `core` portable. `FlappyChessGame` accepts an exit callback so
Escape can return to the browser start screen. Pages deployment is configured in
`.github/workflows/pages.yml`. `network.js` owns the room lobby, PeerJS signaling,
and reliable WebRTC data channel; `PeerNetwork` bridges it to `PeerGame`.
PeerJS is pinned and vendored with its license; public signaling/ICE defaults
can be overridden in `peer-config.js`. The npm dependency is for local tests only.

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
- The default is five minutes per side with no increment; configurable matches
  support untimed play or up to sixty minutes and sixty seconds per move.
  Add increments only after completed legal moves, including promotion choice.
  The clock uses monotonic real time,
  including during promotion and focus loss. Charge time to the mover before
  switching sides; do not derive clock time from the capped physics delta.
- Game over freezes play. R or the Play again button starts a new session;
  Escape exits. White uses A/D, Space, E; black uses Left/Right, Up, Enter.
- Flight School is untimed and available through the browser lobby or T in local
  play. Enter starts a normal match after completion; R repeats the lesson.
  M or the Sound control toggles device-local audio without changing game state.
- Desktop play begins with match setup; S opens setup for a new local match.
  Existing clocks keep running while setup is open. Online settings come from
  the host and survive rematches; untimed replicas must not tick their clocks.
- Online: creator is White and authoritative for both birds, chess, clocks,
  and results; guest is Black and sends intent only. Either key layout controls
  the local player's bird. Guest predicts movement and smooths host corrections.
  Start clocks after both peers are ready; release only the local player's input
  on focus loss. Both players must request a rematch. Each round has a new epoch;
  stale snapshots and repeated inputs cannot rewind the game or repeat a flap.
  Disconnection freezes the match; reconnecting or migrating a match is unsupported.

## Change and validation guidance

Keep game logic testable without an OpenGL context; rendering and GPU resources
belong in the renderer/lifecycle layer. Use chesslib for legality and outcomes.
Follow the surrounding Java style and avoid unrelated refactors or dependency changes.
Split each task into appropriately sized, focused commits, each covering one coherent change.

For physics or input changes, run `:core:test`; preserve mirrored gravity, bounds,
single-press flaps, focus release, and render-rate independence. Adjust existing
tuning assertions when behavior intentionally changes. Use injected clocks in
session tests instead of sleeps. Run `build` for changes spanning modules or packaging.
For rendering/assets/lifecycle changes, also run the graphical smoke test when
a display is available; report any check that could not run. A smoke test does
not replace manual assessment of gameplay feel.

For browser or shared lifecycle changes, also run `:web:browserTest` when Node
and Chromium are available. It verifies project-site asset paths, keyboard
input, focus release, resizing, WebGL errors, and returning to the start screen.
Keep browser URLs relative so the game works below a GitHub Pages repository
path. Preserve clock accounting across browser focus and visibility changes.
For networking changes, also run `:web:peerTest`; keep its signaling local and
exercise actual WebRTC rather than replacing the data channel with a stub.

Dispose owned GPU and audio resources. Optional effects are original synthesized
WAV files in `assets/sounds`, regenerated by `tools/generate-sounds.py`; audio
availability must not prevent play. Preserve the existing image attribution and
the bundled font's SIL Open Font License (`assets/fonts/OFL.txt`). Do not
introduce JavaFX/FXGL dependencies.
Do not edit generated `build/` outputs or commit local caches.
