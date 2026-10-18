package com.example.flappychess;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.view.GameRenderer;

public final class FlappyChessGame extends ApplicationAdapter {
    private final boolean smokeTest;
    private final Runnable exitAction;
    private GameSession session;
    private GameRenderer renderer;
    private int frames;

    public FlappyChessGame() { this(false); }
    public FlappyChessGame(boolean smokeTest) { this(smokeTest, () -> Gdx.app.exit()); }
    public FlappyChessGame(boolean smokeTest, Runnable exitAction) {
        this.smokeTest = smokeTest;
        this.exitAction = exitAction;
    }

    @Override public void create() {
        renderer = new GameRenderer();
        restart();
    }

    private void restart() {
        session = new GameSession();
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Keys.ESCAPE) { exitAction.run(); return true; }
                if (key == Keys.R && session.isGameOver()) { restart(); return true; }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                Vector2 point = renderer.unproject(x, y);
                if (session.isGameOver() && point.x >= 450 && point.x <= 750
                        && point.y >= 260 && point.y <= 320) {
                    restart();
                    return true;
                }
                return false;
            }
        }, session));
    }

    @Override public void render() {
        session.update(Gdx.graphics.getDeltaTime());
        renderer.render(session);
        if (smokeTest && ++frames == 30) {
            Gdx.app.log("SmokeTest", "Rendered 30 frames with board, birds, font, and HUD.");
            Gdx.app.exit();
        }
    }

    @Override public void resize(int width, int height) { renderer.resize(width, height); }
    @Override public void pause() { session.releaseInput(); }
    public void releaseInput() { if (session != null) session.releaseInput(); }
    @Override public void dispose() { if (renderer != null) renderer.dispose(); }
}
