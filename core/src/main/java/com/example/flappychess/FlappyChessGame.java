package com.example.flappychess;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.controller.FlightSchool;
import com.example.flappychess.view.GameRenderer;

public final class FlappyChessGame extends ApplicationAdapter {
    private final boolean smokeTest;
    private final Runnable exitAction;
    private final boolean startInSchool;
    private GameSession session;
    private FlightSchool school;
    private GameRenderer renderer;
    private int frames;
    private boolean soundHeld;

    public FlappyChessGame() { this(false); }
    public FlappyChessGame(boolean smokeTest) { this(smokeTest, () -> Gdx.app.exit()); }
    public FlappyChessGame(boolean smokeTest, Runnable exitAction) {
        this(smokeTest, exitAction, false);
    }

    public FlappyChessGame(boolean smokeTest, Runnable exitAction, boolean startInSchool) {
        this.smokeTest = smokeTest;
        this.exitAction = exitAction;
        this.startInSchool = startInSchool;
    }

    @Override public void create() {
        renderer = new GameRenderer();
        restart(startInSchool);
    }

    private void restart(boolean tutorial) {
        if (session != null) session.releaseInput();
        school = tutorial ? new FlightSchool() : null;
        session = school == null ? new GameSession() : school.getSession();
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Keys.M) {
                    if (!soundHeld) renderer.toggleSound();
                    soundHeld = true;
                    return true;
                }
                if (key == Keys.ESCAPE) { exitAction.run(); return true; }
                if (key == Keys.T) { restart(true); return true; }
                if (school != null && school.isComplete()) {
                    if (key == Keys.ENTER) restart(false);
                    else if (key == Keys.R) restart(true);
                    return true;
                }
                if (key == Keys.R && session.isGameOver()) { restart(false); return true; }
                return false;
            }
            @Override public boolean keyUp(int key) {
                if (key != Keys.M) return false;
                soundHeld = false;
                return true;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                Vector2 point = renderer.unproject(x, y);
                if (renderer.isSoundToggleAt(point)) { renderer.toggleSound(); return true; }
                if ((session.isGameOver() || school != null && school.isComplete()) && point.x >= 450 && point.x <= 750
                        && point.y >= 260 && point.y <= 320) {
                    restart(false);
                    return true;
                }
                return false;
            }
        }, school == null ? session : school));
    }

    @Override public void render() {
        if (school == null) session.update(Gdx.graphics.getDeltaTime());
        else school.update(Gdx.graphics.getDeltaTime());
        renderer.render(session, school);
        if (smokeTest && ++frames == 30) {
            Gdx.app.log("SmokeTest", "Rendered 30 frames with board, birds, font, and HUD.");
            Gdx.app.exit();
        }
    }

    @Override public void resize(int width, int height) { renderer.resize(width, height); }
    public FlightSchool getFlightSchool() { return school; }
    public boolean isSoundEnabled() { return renderer.isSoundEnabled(); }
    public String feedbackMessage() { return renderer.feedbackMessage(); }
    @Override public void pause() { releaseInput(); }
    public void releaseInput() {
        soundHeld = false;
        if (session != null) session.releaseInput();
    }
    @Override public void dispose() { if (renderer != null) renderer.dispose(); }
}
