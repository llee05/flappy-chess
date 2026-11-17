package com.example.flappychess.controller;

import com.example.flappychess.model.SoloSettings.ChessLevel;

/** Asynchronous chess-move source. Implementations must never block the game loop. */
public interface ChessEngine extends AutoCloseable {
    enum State { THINKING, READY, FAILED }

    interface Request {
        State state();
        String move();
        String failure();
    }

    Request choose(String fen, ChessLevel level);
    default void cancel() { }
    @Override default void close() { }
}
