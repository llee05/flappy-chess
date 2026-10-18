package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.view.ChessBoardView;
import com.github.bhlangonijr.chesslib.Board;

/** A separate board for browsing a finished game, independent on each peer. */
public final class PostgameReview {
    private final Board board = new Board();
    private final ChessBoardView view = new ChessBoardView();
    private GameSession observed;
    private GameRecord record;
    private int ply;
    private boolean active;

    public void bind(GameSession game) {
        if (observed != game) {
            observed = game;
            record = null;
            active = false;
            ply = 0;
        }
        if (game.isGameOver() && record == null) {
            record = new GameRecord(game);
            seek(record.moves().size());
        }
    }

    public boolean keyDown(int key) {
        if (record == null) return false;
        if (key == Keys.V) { active = !active; return true; }
        if (!active) return false;
        switch (key) {
            case Keys.LEFT -> seek(ply - 1);
            case Keys.RIGHT -> seek(ply + 1);
            case Keys.HOME -> seek(0);
            case Keys.END -> seek(record.moves().size());
            default -> { return false; }
        }
        return true;
    }

    public void seek(int next) {
        if (record == null) return;
        ply = Math.max(0, Math.min(record.moves().size(), next));
        board.loadFromFen(record.fenAt(ply));
        view.update(board);
    }

    public boolean isActive() { return active; }
    public int getPly() { return ply; }
    public GameRecord getRecord() { return record; }
    public ChessBoardView getView() { return view; }
}
