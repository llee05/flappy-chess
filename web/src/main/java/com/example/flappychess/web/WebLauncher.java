package com.example.flappychess.web;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.FlappyChessGame;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;

public final class WebLauncher {
    public static void main(String[] args) {
        Window.current().getDocument().getElementById("play").addEventListener("click", event -> start());
        PeerNetwork.install(WebLauncher::startPeer);
        enablePlay();
    }

    private static void start() {
        showLoading();
        FlappyChessGame game = new FlappyChessGame(false, WebLauncher::leaveGame);
        launch(new ApplicationAdapter() {
            @Override public void create() {
                game.create();
                catchGameKeys();
                ready();
            }
            @Override public void render() { game.render(); }
            @Override public void resize(int width, int height) { game.resize(width, height); }
            @Override public void pause() { game.pause(); }
            @Override public void resume() { game.resume(); }
            @Override public void dispose() { game.dispose(); }
        }, false);
    }

    private static void startPeer(boolean host) {
        launch(new PeerGame(host), true);
    }

    private static void launch(ApplicationAdapter game, boolean online) {
        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.useGL30 = false;
        config.antialiasing = false;
        config.storagePrefix = "flappy-chess";
        config.padVertical = online ? 48 : 0;
        // The backend's default provider retains a room hash, breaking script/asset URLs.
        config.baseUrlProvider = WebLauncher::assetBaseUrl;
        new WebApplication(game, config);
    }

    static void catchGameKeys() {
        for (int key : new int[] {Keys.SPACE, Keys.UP, Keys.LEFT, Keys.RIGHT, Keys.ENTER}) {
            Gdx.input.setCatchKey(key, true);
        }
    }

    @JSBody(script = "return new URL('.', document.baseURI).href;")
    private static native String assetBaseUrl();

    @JSBody(script = "document.getElementById('play').disabled = false; document.getElementById('status').textContent = 'Two players. One keyboard.';")
    private static native void enablePlay();

    @JSBody(script = "document.getElementById('play').disabled = true; document.getElementById('status').textContent = 'Preparing the board...';")
    private static native void showLoading();

    @JSBody(script = "document.getElementById('welcome').hidden = true; document.getElementById('canvas').focus(); document.body.dataset.game = 'ready';")
    private static native void ready();

    @JSBody(script = "window.FlappyNetwork.leave();")
    static native void leaveGame();
}
