package com.example.flappychess.web;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.FlappyChessGame;
import com.example.flappychess.model.MatchSettings;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;

public final class WebLauncher {
    public static void main(String[] args) {
        Window.current().getDocument().getElementById("play").addEventListener("click", event -> start(false));
        Window.current().getDocument().getElementById("tutorial").addEventListener("click", event -> start(true));
        PeerNetwork.install(WebLauncher::startPeer);
        enablePlay();
    }

    private static void start(boolean tutorial) {
        showLoading();
        FlappyChessGame game = new FlappyChessGame(false, WebLauncher::leaveGame, tutorial, selectedSettings());
        launch(new ApplicationAdapter() {
            @Override public void create() {
                game.create();
                catchGameKeys();
                describeTimeControl(game.getSession().getSettings().label());
                ready(tutorial);
            }
            @Override public void render() {
                game.render();
                var school = game.getFlightSchool();
                describeLesson(school == null ? "" : school.getStage().name(),
                        school == null ? "" : school.title() + ". " + school.instruction());
                describeFeedback(game.feedbackMessage(), game.isSoundEnabled());
                describeTimeControl(game.getSession().getSettings().label());
                describeSetup(game.getMatchSetup() == null ? "" : game.getMatchSetup().settings().label());
            }
            @Override public void resize(int width, int height) { game.resize(width, height); }
            @Override public void pause() { game.pause(); }
            @Override public void resume() { game.resume(); }
            @Override public void dispose() { game.dispose(); }
        }, false);
    }

    private static void startPeer(boolean host) {
        launch(new PeerGame(host, host ? selectedSettings() : MatchSettings.DEFAULT), true);
    }

    static MatchSettings selectedSettings() { return MatchSettings.minutes(selectedMinutes(), selectedIncrement()); }

    @JSBody(script = "return window.FlappySettings.read().minutes;")
    private static native int selectedMinutes();

    @JSBody(script = "return window.FlappySettings.read().increment;")
    private static native int selectedIncrement();

    @JSBody(params = "label", script = "document.body.dataset.timeControl = label;")
    static native void describeTimeControl(String label);

    @JSBody(params = "label", script = "document.body.dataset.setup = label; const summary = document.getElementById('setup-summary'); const text = label ? 'New match setup: ' + label + '. Left and Right change time; Up and Down change bonus; Enter starts a new match. Existing match clocks keep running.' : ''; if (summary.textContent !== text) summary.textContent = text;")
    private static native void describeSetup(String label);

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

    @JSBody(script = "document.getElementById('play').disabled = false; document.getElementById('tutorial').disabled = false; document.getElementById('status').textContent = 'Try Flight School, or play with a friend.';")
    private static native void enablePlay();

    @JSBody(script = "document.getElementById('play').disabled = true; document.getElementById('tutorial').disabled = true; document.getElementById('status').textContent = 'Preparing the board...';")
    private static native void showLoading();

    @JSBody(params = "tutorial", script = "document.getElementById('welcome').hidden = true; document.getElementById('canvas').focus(); document.body.dataset.game = 'ready'; document.body.dataset.mode = tutorial ? 'tutorial' : 'local';")
    private static native void ready(boolean tutorial);

    @JSBody(params = {"stage", "text"}, script = "const summary = document.getElementById('lesson-summary'); summary.dataset.stage = stage; if (summary.textContent !== text) summary.textContent = text; document.body.dataset.mode = stage ? 'tutorial' : 'local';")
    private static native void describeLesson(String stage, String text);

    @JSBody(params = {"text", "sound"}, script = "const summary = document.getElementById('action-feedback'); if (summary.textContent !== text) summary.textContent = text; document.body.dataset.sound = sound ? 'on' : 'off';")
    static native void describeFeedback(String text, boolean sound);

    @JSBody(script = "window.FlappyNetwork.leave();")
    static native void leaveGame();
}
