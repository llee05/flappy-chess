package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Local player layouts follow their identities when a rematch swaps colors. */
public final class LocalMatch extends InputAdapter {
    private final Supplier<GameSession> sessions;
    private final MatchSeries series = new MatchSeries(MatchSeries.Players.LOCAL);
    private final Set<Integer> keys = new HashSet<>();
    private GameSession session;

    public LocalMatch(MatchSettings settings) { this(() -> new GameSession(settings)); }
    LocalMatch(Supplier<GameSession> sessions) {
        this.sessions = sessions;
        session = sessions.get();
        series.observe(session);
    }

    public void update(float delta) { session.update(delta); series.observe(session); }
    public GameSession getSession() { return session; }

    public void nextRound(boolean swap) {
        if (!session.isGameOver()) return;
        releaseInput();
        series.nextRound(session, swap);
        session = sessions.get();
        series.observe(session);
    }

    private Side sideForKey(int key, GameAction action) {
        if (action.isPromotion()) return session.getChess().getSideToMove();
        boolean first = key == Keys.A || key == Keys.D || key == Keys.SPACE || key == Keys.E;
        return series.state().sideFor(first);
    }

    @Override public boolean keyDown(int key) {
        GameAction action = GameAction.forKey(key);
        if (action == null) return false;
        if (keys.add(key)) session.command(sideForKey(key, action), action, true);
        series.observe(session);
        return true;
    }

    @Override public boolean keyUp(int key) {
        GameAction action = GameAction.forKey(key);
        if (action == null) return false;
        keys.remove(key);
        session.command(sideForKey(key, action), action, false);
        return true;
    }

    public void releaseInput() { keys.clear(); session.releaseInput(); }
}
