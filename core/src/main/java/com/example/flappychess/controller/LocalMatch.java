package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Local player layouts follow their identities when a rematch swaps colors. */
public final class LocalMatch extends InputAdapter {
    private final Supplier<GameSession> sessions;
    private final MatchSeries series;
    private final SoloSettings solo;
    private final Set<Integer> keys = new HashSet<>();
    private GameSession session;
    private AiOpponent opponent;

    public LocalMatch(MatchSettings settings) { this(() -> new GameSession(settings)); }
    public LocalMatch(MatchSettings settings, SoloSettings solo) { this(() -> new GameSession(settings), solo); }
    LocalMatch(Supplier<GameSession> sessions) { this(sessions, null); }
    LocalMatch(Supplier<GameSession> sessions, SoloSettings solo) {
        this.sessions = sessions;
        this.solo = solo;
        series = new MatchSeries(solo == null ? MatchSeries.Players.LOCAL : MatchSeries.Players.SOLO,
                solo == null || solo.humanSide() == Side.WHITE);
        newSession();
    }

    private void newSession() {
        session = sessions.get();
        series.observe(session);
        session.setSoloSettings(solo);
        opponent = solo == null ? null : new AiOpponent(session, series.state().sideFor(false), solo);
    }

    public void update(float delta) {
        session.update(delta, opponent == null ? null : opponent::step);
        if (opponent != null) session.setAiPhase(session.isGameOver() ? "FINISHED" : opponent.phase());
        series.observe(session);
    }
    public GameSession getSession() { return session; }

    public void nextRound(boolean swap) {
        if (!session.isGameOver()) return;
        releaseInput();
        series.nextRound(session, swap);
        newSession();
    }

    private Side sideForKey(int key, GameAction action) {
        if (solo != null) return series.state().sideFor(true);
        if (action.isPromotion()) return session.getChess().getSideToMove();
        boolean first = key == Keys.A || key == Keys.D || key == Keys.SPACE || key == Keys.E;
        return series.state().sideFor(first);
    }

    @Override public boolean keyDown(int key) {
        GameAction action = GameAction.forKey(key);
        if (action == null) return false;
        if (keys.add(key) && (solo == null || keys.stream().filter(other -> GameAction.forKey(other) == action).count() == 1)) {
            session.command(sideForKey(key, action), action, true);
        }
        series.observe(session);
        return true;
    }

    @Override public boolean keyUp(int key) {
        GameAction action = GameAction.forKey(key);
        if (action == null) return false;
        keys.remove(key);
        if (solo != null) for (int other : keys) if (GameAction.forKey(other) == action) return true;
        session.command(sideForKey(key, action), action, false);
        return true;
    }

    public void releaseInput() {
        keys.clear();
        if (solo == null) session.releaseInput(); else session.releaseInput(series.state().sideFor(true));
    }
}
