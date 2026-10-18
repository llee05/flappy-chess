package com.example.flappychess.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.example.flappychess.FlappyChessGame;
import java.util.Arrays;

public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        FlappyChessGame game = new FlappyChessGame(Arrays.asList(args).contains("--smoke-test"));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Flappy Chess");
        config.setWindowedMode(1200, 800);
        config.setWindowSizeLimits(600, 400, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);
        // Rendering checks do not need an audio device. Normal play uses optional effects.
        config.disableAudio(Arrays.asList(args).contains("--smoke-test"));
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override public void focusLost() { game.releaseInput(); }
        });
        new Lwjgl3Application(game, config);
    }
}
