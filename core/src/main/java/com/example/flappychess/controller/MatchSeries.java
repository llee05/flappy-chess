package com.example.flappychess.controller;

import com.github.bhlangonijr.chesslib.Side;

/** Scores belong to players, while colors can change between rounds. */
public final class MatchSeries {
    public enum Players { LOCAL, ONLINE, SOLO }
    public record State(int round, int firstHalfPoints, int secondHalfPoints, boolean firstWhite, Players players) {
        public State {
            if (round < 1 || round > 1_000_000 || firstHalfPoints < 0 || secondHalfPoints < 0
                    || firstHalfPoints + (long) secondHalfPoints > round * 2L || players == null) {
                throw new IllegalArgumentException("Invalid series");
            }
        }
        public Side sideFor(boolean first) { return first == firstWhite ? Side.WHITE : Side.BLACK; }
        public String nameFor(Side side) {
            boolean first = side == sideFor(true);
            return switch (players) {
                case LOCAL -> first ? "Player 1" : "Player 2";
                case ONLINE -> first ? "Creator" : "Guest";
                case SOLO -> first ? "You" : "Computer";
            };
        }
        public String shortNameFor(Side side) {
            return players == Players.SOLO ? side == sideFor(true) ? "YOU" : "AI" : side == sideFor(true) ? "P1" : "P2";
        }
        public String score() {
            return (players == Players.SOLO ? "YOU " : "P1 ") + points(firstHalfPoints) + "-" + points(secondHalfPoints)
                    + (players == Players.SOLO ? " AI" : " P2");
        }
        private static String points(int half) { return Integer.toString(half / 2) + (half % 2 == 0 ? "" : ".5"); }
    }

    private State state;
    private GameSession scored;

    public MatchSeries(Players players) { this(players, true); }
    public MatchSeries(Players players, boolean firstWhite) { state = new State(1, 0, 0, firstWhite, players); }
    public State state() { return state; }
    void restore(State state) { this.state = state; }

    public void observe(GameSession game) {
        if (game.isGameOver() && scored != game) {
            int first = 0, second = 0;
            if ("Draw".equals(game.getResult())) first = second = 1;
            else {
                Side winner = "White wins".equals(game.getResult()) ? Side.WHITE : Side.BLACK;
                if (winner == state.sideFor(true)) first = 2; else second = 2;
            }
            state = new State(state.round(), state.firstHalfPoints() + first, state.secondHalfPoints() + second,
                    state.firstWhite(), state.players());
            scored = game;
        }
        game.setSeries(state);
    }

    public void nextRound(GameSession finished, boolean swap) {
        if (!finished.isGameOver()) throw new IllegalStateException("Round is still playing");
        observe(finished);
        state = new State(state.round() + 1, state.firstHalfPoints(), state.secondHalfPoints(),
                state.firstWhite() != swap, state.players());
    }
}
