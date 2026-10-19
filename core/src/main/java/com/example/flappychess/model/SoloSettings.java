package com.example.flappychess.model;

import com.github.bhlangonijr.chesslib.Side;
import java.util.Objects;

/** Chess skill and flight reaction speed are independent; physics never changes. */
public record SoloSettings(Side humanSide, Level chess, Level flight) {
    public enum Level { EASY, MEDIUM, HARD;
        public Level next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public static final SoloSettings DEFAULT = new SoloSettings(Side.WHITE, Level.MEDIUM, Level.MEDIUM);
    public SoloSettings { Objects.requireNonNull(humanSide); Objects.requireNonNull(chess); Objects.requireNonNull(flight); }
}
