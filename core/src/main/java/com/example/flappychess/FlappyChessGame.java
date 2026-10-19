package com.example.flappychess;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.controller.FlightSchool;
import com.example.flappychess.controller.MatchSetup;
import com.example.flappychess.controller.LocalMatch;
import com.example.flappychess.controller.PostgameReview;
import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
import java.util.function.Function;
import com.example.flappychess.view.GameRenderer;

public final class FlappyChessGame extends ApplicationAdapter {
    private final boolean smokeTest;
    private final Runnable exitAction;
    private final boolean startInSchool;
    private GameSession session;
    private LocalMatch match;
    private FlightSchool school;
    private GameRenderer renderer;
    private int frames;
    private boolean soundHeld;
    private MatchSettings settings;
    private SoloSettings solo;
    private MatchSetup setup;
    private boolean waitingToStart;
    private final Function<String, String> pgnExporter;

    public FlappyChessGame() { this(false); }
    public FlappyChessGame(boolean smokeTest) {
        this(smokeTest, () -> Gdx.app.exit());
        waitingToStart = !smokeTest;
    }
    public FlappyChessGame(boolean smokeTest, Runnable exitAction) {
        this(smokeTest, exitAction, false);
    }

    public FlappyChessGame(boolean smokeTest, Runnable exitAction, boolean startInSchool) {
        this(smokeTest, exitAction, startInSchool, MatchSettings.DEFAULT);
    }

    public FlappyChessGame(boolean smokeTest, Runnable exitAction, boolean startInSchool, MatchSettings settings) {
        this(smokeTest, exitAction, startInSchool, settings, FlappyChessGame::savePgn);
    }

    public FlappyChessGame(boolean smokeTest, Runnable exitAction, boolean startInSchool, MatchSettings settings,
            Function<String, String> pgnExporter) {
        this.smokeTest = smokeTest;
        this.exitAction = exitAction;
        this.startInSchool = startInSchool;
        this.settings = settings;
        this.pgnExporter = pgnExporter;
    }

    public FlappyChessGame(boolean smokeTest, Runnable exitAction, MatchSettings settings,
            SoloSettings solo, Function<String, String> pgnExporter) {
        this(smokeTest, exitAction, false, settings, pgnExporter);
        this.solo = solo;
    }

    @Override public void create() {
        renderer = new GameRenderer();
        restart(startInSchool);
        if (waitingToStart) setup = new MatchSetup(settings, solo);
    }

    private void restart(boolean tutorial) {
        if (session != null) session.releaseInput();
        school = tutorial ? new FlightSchool() : null;
        match = tutorial ? null : new LocalMatch(settings, solo);
        session = tutorial ? school.getSession() : match.getSession();
        installInput();
    }

    private void rematch(boolean swap) {
        match.nextRound(swap);
        session = match.getSession();
        installInput();
    }

    private void installInput() {
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Keys.M) {
                    if (!soundHeld) renderer.toggleSound();
                    soundHeld = true;
                    return true;
                }
                if (key == Keys.ESCAPE) { exitAction.run(); return true; }
                if (setup != null) {
                    if (key == Keys.LEFT) setup.changeTime(-1);
                    if (key == Keys.RIGHT) setup.changeTime(1);
                    if (key == Keys.DOWN) setup.changeIncrement(-1);
                    if (key == Keys.UP) setup.changeIncrement(1);
                    if (key == Keys.C) setup.toggleOpponent();
                    if (key == Keys.W) setup.changeColor();
                    if (key == Keys.E) setup.changeChess();
                    if (key == Keys.F) setup.changeFlight();
                    if (key == Keys.ENTER) startConfiguredMatch();
                    if (key == Keys.S && !waitingToStart) setup = null;
                    return true;
                }
                if (key == Keys.S) { openSetup(); return true; }
                if (school == null && key == Keys.P) { renderer.exportPgn(session, pgnExporter); return true; }
                if (school == null && renderer.historyKey(session, key)) return true;
                if (key == Keys.T) { restart(true); return true; }
                if (school != null && school.isComplete()) {
                    if (key == Keys.ENTER) restart(false);
                    else if (key == Keys.R) restart(true);
                    return true;
                }
                if ((key == Keys.R || key == Keys.X) && session.isGameOver()) { rematch(key == Keys.X); return true; }
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
                if (setup != null) {
                    if (point.y >= 500 && point.y <= 544) {
                        if (point.x >= 380 && point.x < 600) setup.toggleOpponent();
                        if (point.x >= 600 && point.x <= 820) setup.changeColor();
                    }
                    if (point.y >= 196 && point.y <= 236) {
                        if (point.x >= 380 && point.x < 600) setup.changeChess();
                        if (point.x >= 600 && point.x <= 820) setup.changeFlight();
                    }
                    if (point.x >= 380 && point.x <= 820) {
                        if (point.y >= 430 && point.y <= 480) setup.changeTime(1);
                        if (point.y >= 354 && point.y <= 404) setup.changeIncrement(1);
                    }
                    if (point.x >= 450 && point.x <= 750 && point.y >= 260 && point.y <= 320) startConfiguredMatch();
                    return true;
                }
                if (renderer.isSetupAt(point)) { openSetup(); return true; }
                if (school == null && renderer.isExportAt(session, point)) {
                    renderer.exportPgn(session, pgnExporter);
                    return true;
                }
                if (school == null && renderer.historyClick(session, point)) return true;
                if (school == null && renderer.isSwapAt(session, point)) { rematch(true); return true; }
                if ((session.isGameOver() || school != null && school.isComplete()) && renderer.isPlayAgainAt(point)) {
                    if (school != null) restart(false); else rematch(false);
                    return true;
                }
                return false;
            }
        }, school == null ? match : school));
    }

    @Override public void render() {
        if (!waitingToStart) {
            if (school == null) match.update(Gdx.graphics.getDeltaTime());
            else school.update(Gdx.graphics.getDeltaTime());
        }
        renderer.render(session, school, setup);
        if (smokeTest && ++frames == 30) {
            Gdx.app.log("SmokeTest", "Rendered 30 frames with board, birds, font, and HUD.");
            Gdx.app.exit();
        }
    }

    @Override public void resize(int width, int height) { renderer.resize(width, height); }
    public FlightSchool getFlightSchool() { return school; }
    public GameSession getSession() { return session; }
    public MatchSetup getMatchSetup() { return setup; }
    private void openSetup() { releaseInput(); setup = new MatchSetup(settings, solo); }
    private void startConfiguredMatch() {
        settings = setup.settings();
        solo = setup.soloSettings();
        setup = null;
        waitingToStart = false;
        restart(false);
    }
    public boolean isSoundEnabled() { return renderer.isSoundEnabled(); }
    public String feedbackMessage() { return renderer.feedbackMessage(); }
    public PostgameReview getReview() { return renderer.getReview(); }
    private static String savePgn(String pgn) {
        String path = "exports/flappy-chess-" + System.currentTimeMillis() + ".pgn";
        Gdx.files.local(path).writeString(pgn, false, "UTF-8");
        Gdx.app.log("PGN", "Saved " + Gdx.files.local(path).file().getAbsolutePath());
        return "Saved PGN in exports.";
    }
    @Override public void pause() { releaseInput(); }
    public void releaseInput() {
        soundHeld = false;
        if (match != null) match.releaseInput();
        else if (session != null) session.releaseInput();
    }
    @Override public void dispose() { if (renderer != null) renderer.dispose(); }
}
