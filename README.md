# Flappy Chess

A desktop libGDX port of the JavaFX example in `../flappy-birb`, based on
commit `5eebafb4e43d1c9a3dd2350c3a26636c1953f5cd` (Add promotion choices).
The original project is preserved. This directory is an independent Git repository.

## Run and develop

Requires a Java 17 JDK, a graphical desktop, and OpenGL support. Java 17 and the
Linux graphics libraries were already installed on the development machine.
The checked-in Gradle wrapper downloads Gradle and all Java/native dependencies;
no global Gradle, Maven, JavaFX, or Android SDK installation is needed.

```sh
cd /home/amicablepigeon/Projects/flappy-chess
./gradlew :lwjgl3:run
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

```sh
./gradlew build                 # Compile, test, and package
./gradlew :core:test            # Run game logic/input tests without a display
./gradlew :lwjgl3:installDist   # Create a launcher and dependency directory
./gradlew :lwjgl3:run --args=--smoke-test  # Render 30 frames, then exit
```

The installed launcher is `lwjgl3/build/install/flappy-chess/bin/flappy-chess`.
ZIP/TAR distributions are in `lwjgl3/build/distributions/`. They include assets
and dependencies but require Java 17. Tests are reported at
`core/build/reports/tests/test/index.html`.

In IntelliJ IDEA, open this directory as a Gradle project, select JDK 17 for the
project and Gradle JVM, and use the shared **Flappy Chess** run configuration.
In VS Code, install the workspace's recommended Java/Gradle extensions and use
the supplied run/debug configuration or build/run tasks. Linux desktop is the
validated target; additional libGDX platforms can be added later.

## Play

Both birds move continuously, with opposite gravity. Fly the active player's
bird over a piece, pick it up, then carry it to a highlighted legal destination.
The bird's center determines its board square. Dropping back on the original
square cancels the pickup; an illegal destination keeps the piece carried.

| Action | White | Black |
| --- | --- | --- |
| Move left/right | A / D | Left / Right |
| Flap against gravity | Space | Up |
| Pick up / drop | E | Enter |

Promotion: **Q** queen, **R** rook, **B** bishop, **N** knight.
After a game ends, press **R** or click **Play again**. **Esc** exits.
Each side has five minutes; the active player's clock keeps running during
promotion and while the window is unfocused.

## Project layout

- `core/src/main/java/com/example/flappychess/model`: original bird physics,
  chess rules, and clock models, with the package renamed.
- `core/.../controller`: adapted input and chess controllers plus `GameSession`.
- `core/.../view`: board presentation state and libGDX drawing.
- `lwjgl3`: desktop launcher and distribution configuration.
- `assets`: original images and a bundled font for chess symbols.
- `core/src/test`: migrated model/controller tests and new session/coordinate tests.

The libGDX loop uses a fixed 60 Hz physics step and a real-time chess clock.
The board scales with the window while retaining its original 800x800 logical
coordinates. Focus loss releases held controls. GPU resources are disposed on exit.
The original JavaFX views, FXML placeholder, and JavaFX-specific view tests were
replaced; this port has no JavaFX or FXGL dependency. Audio is disabled because
the example contains no sound assets.

Versions: Java 17, Gradle 9.1.0 (checksum-verified wrapper), libGDX 1.14.2,
chesslib 1.3.6, JUnit 5.12.1. Dependencies resolve from Maven Central, with
JitPack restricted to chesslib's group. Version settings are in
`gradle.properties` and the wrapper configuration.

Reference: [libGDX development setup](https://libgdx.com/wiki/start/setup) and
[project structure/setup](https://libgdx.com/wiki/start/project-generation).

## Attribution

Game logic and images come from the existing
[flappy-birb project](https://github.com/llee05/flappy-birb).
`wings.png` is used in the game; the other original images are retained as assets.
Noto Sans Symbols 2 is bundled under the SIL Open Font License; see
`assets/fonts/OFL.txt`. No new license is assigned to the original game or images.
