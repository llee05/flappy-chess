package com.example.flappychess.model;

import com.github.bhlangonijr.chesslib.Side;
import java.util.Objects;

/** Chess strength and flight reaction speed are independent; physics never changes. */
public record SoloSettings(Side humanSide, ChessLevel chess, Level flight) {
    /** Stockfish settings plus bounded built-in-search settings used by the desktop/failure fallback. */
    public enum ChessLevel {
        HATCHLING(0, 1320, 50, 0, 0),
        BEGINNER(2, 1400, 75, 1, 64),
        CASUAL(5, 1550, 100, 1, 128),
        CLUB(8, 1700, 150, 2, 256),
        CHALLENGER(11, 1900, 225, 2, 512),
        EXPERT(14, 2100, 350, 2, 1000),
        MASTER(17, 2400, 550, 3, 2000),
        STOCKFISH(20, 0, 1000, 3, 3000);

        private final int skill, elo, moveTimeMillis, fallbackDepth, fallbackNodes;

        ChessLevel(int skill, int elo, int moveTimeMillis, int fallbackDepth, int fallbackNodes) {
            this.skill = skill;
            this.elo = elo;
            this.moveTimeMillis = moveTimeMillis;
            this.fallbackDepth = fallbackDepth;
            this.fallbackNodes = fallbackNodes;
        }

        public int skill() { return skill; }
        /** Zero means full strength rather than UCI Elo limiting. */
        public int elo() { return elo; }
        public int moveTimeMillis() { return moveTimeMillis; }
        public int fallbackDepth() { return fallbackDepth; }
        public int fallbackNodes() { return fallbackNodes; }
        public ChessLevel next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** Flight skill remains the original three reaction profiles. */
    public enum Level { EASY, MEDIUM, HARD;
        public Level next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public static final SoloSettings DEFAULT = new SoloSettings(Side.WHITE, ChessLevel.CLUB, Level.MEDIUM);
    public SoloSettings { Objects.requireNonNull(humanSide); Objects.requireNonNull(chess); Objects.requireNonNull(flight); }
}
