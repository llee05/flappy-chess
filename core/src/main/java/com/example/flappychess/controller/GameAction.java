package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.github.bhlangonijr.chesslib.Side;

/** Player intent, independent of keyboard layout and network transport. */
public enum GameAction {
    LEFT, RIGHT, FLAP, CARRY, QUEEN, ROOK, BISHOP, KNIGHT;

    public boolean isMovement() { return this == LEFT || this == RIGHT || this == FLAP; }
    public boolean isPromotion() { return ordinal() >= QUEEN.ordinal(); }

    public int keyFor(Side side) {
        return switch (this) {
            case LEFT -> side == Side.WHITE ? Keys.A : Keys.LEFT;
            case RIGHT -> side == Side.WHITE ? Keys.D : Keys.RIGHT;
            case FLAP -> side == Side.WHITE ? Keys.SPACE : Keys.UP;
            case CARRY -> side == Side.WHITE ? Keys.E : Keys.ENTER;
            case QUEEN -> Keys.Q;
            case ROOK -> Keys.R;
            case BISHOP -> Keys.B;
            case KNIGHT -> Keys.N;
        };
    }

    /** Online players may use either familiar control layout for their own bird. */
    public static GameAction forKey(int key) {
        return switch (key) {
            case Keys.A, Keys.LEFT -> LEFT;
            case Keys.D, Keys.RIGHT -> RIGHT;
            case Keys.SPACE, Keys.UP -> FLAP;
            case Keys.E, Keys.ENTER -> CARRY;
            case Keys.Q -> QUEEN;
            case Keys.R -> ROOK;
            case Keys.B -> BISHOP;
            case Keys.N -> KNIGHT;
            default -> null;
        };
    }
}
