package com.example.flappychess.controller;

import com.example.flappychess.model.Birb;
import com.example.flappychess.model.SoloSettings.Level;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;

/** Predicts coasting and flap apex using copies of the regular bird physics. */
final class BirdPilot {
    private final GameSession game;
    private final Side side;
    private final int cadence, flapInterval;
    private int ticks, lastFlap = -100, direction;

    BirdPilot(GameSession game, Side side, Level level) {
        this.game = game; this.side = side;
        cadence = switch (level) { case EASY -> 6; case MEDIUM -> 3; case HARD -> 1; };
        flapInterval = switch (level) { case EASY -> 32; case MEDIUM -> 26; case HARD -> 20; };
    }

    boolean flyTo(Square square) {
        ticks++;
        Birb bird = side == Side.WHITE ? game.getWhite() : game.getBlack();
        double x = square.getFile().ordinal() * 100 + 50;
        double y = (7 - square.getRank().ordinal()) * 100 + 50;
        if (ticks % cadence == 0) {
            Birb coast = copy(bird);
            for (int i = 0; i < 120; i++) { coast.update(); coast.constrainToBounds(800, 800); }
            double error = x - (coast.getX() + 25);
            setDirection(Math.abs(error) < 10 ? 0 : error < 0 ? -1 : 1);
            Birb arc = copy(bird);
            double apex = height(arc);
            for (int i = 0; i < 45; i++) {
                arc.update(); arc.constrainToBounds(800, 800);
                apex = Math.max(apex, height(arc));
            }
            double targetHeight = side == Side.WHITE ? 800 - y : y;
            if (apex < targetHeight - 25 && ticks - lastFlap >= flapInterval) {
                press(GameAction.FLAP); lastFlap = ticks;
            }
        }
        return Math.abs(bird.getX() + 25 - x) < 36 && Math.abs(bird.getY() + 25 - y) < 36;
    }

    private double height(Birb bird) { return side == Side.WHITE ? 800 - bird.getY() - 25 : bird.getY() + 25; }
    private Birb copy(Birb bird) {
        Birb copy = new Birb(0, 0, 50, 50, side == Side.WHITE ? 1 : -1);
        copy.restore(bird.snapshot());
        return copy;
    }
    private void setDirection(int next) {
        if (next == direction) return;
        if (direction != 0) game.command(side, direction < 0 ? GameAction.LEFT : GameAction.RIGHT, false);
        direction = next;
        if (direction != 0) game.command(side, direction < 0 ? GameAction.LEFT : GameAction.RIGHT, true);
    }
    void press(GameAction action) { game.command(side, action, true); game.command(side, action, false); }
    void release() { setDirection(0); game.releaseInput(side); }
}
