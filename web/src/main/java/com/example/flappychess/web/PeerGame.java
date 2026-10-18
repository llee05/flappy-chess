package com.example.flappychess.web;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.example.flappychess.controller.PeerMatch;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.view.GameRenderer;

/** Browser lifecycle for a single online match; local play keeps its existing lifecycle. */
final class PeerGame extends ApplicationAdapter {
    private final boolean host;
    private final MatchSettings settings;
    private PeerMatch match;
    private GameRenderer renderer;
    private boolean reportedDisconnect;
    private boolean soundHeld;

    PeerGame(boolean host, MatchSettings settings) { this.host = host; this.settings = settings; }

    @Override public void create() {
        renderer = new GameRenderer(true);
        match = new PeerMatch(host, PeerNetwork::send, System::nanoTime, settings);
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Keys.M) {
                    if (!soundHeld) renderer.toggleSound();
                    soundHeld = true;
                    return true;
                }
                if (key != Keys.ESCAPE) return false;
                WebLauncher.leaveGame();
                return true;
            }
            @Override public boolean keyUp(int key) {
                if (key != Keys.M) return false;
                soundHeld = false;
                return true;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                Vector2 point = renderer.unproject(x, y);
                if (renderer.isSoundToggleAt(point)) { renderer.toggleSound(); return true; }
                if (match.getSession().isGameOver() && point.x >= 450 && point.x <= 750
                        && point.y >= 260 && point.y <= 320) {
                    match.requestRematch();
                    return true;
                }
                return false;
            }
        }, match));
        WebLauncher.catchGameKeys();
        PeerNetwork.bind(match::receive, match::disconnect, match::pulse);
    }

    @Override public void render() {
        // The backend delays initial rendering; start the handshake once the board can be shown.
        match.open();
        match.update(Gdx.graphics.getDeltaTime());
        renderer.render(match.getSession());
        WebLauncher.describeFeedback(renderer.feedbackMessage(), renderer.isSoundEnabled());
        WebLauncher.describeTimeControl(match.getSession().getSettings().label());
        PeerNetwork.status(match.status(), host, match.isStarted());
        GameSession session = match.getSession();
        var history = session.getHistory();
        PeerNetwork.describe(GameSession.sideName(session.getChess().getSideToMove()),
                history.isEmpty() ? "" : history.get(history.size() - 1).notation(), history.size(),
                session.getCarrier() == null ? "" : GameSession.sideName(session.getCarrier()),
                session.getChess().hasPendingPromotion(), session.getResult());
        if (match.isDisconnected() && !reportedDisconnect) {
            reportedDisconnect = true;
            PeerNetwork.fail(match.status());
        }
    }

    @Override public void resize(int width, int height) { if (renderer != null) renderer.resize(width, height); }
    @Override public void pause() {
        soundHeld = false;
        if (match != null) match.releaseInput();
    }
    @Override public void dispose() {
        if (match != null) match.releaseInput();
        if (renderer != null) renderer.dispose();
    }
}
