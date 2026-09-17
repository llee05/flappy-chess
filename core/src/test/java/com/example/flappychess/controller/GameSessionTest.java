package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.ChessGameModel;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {
    private GameSession session(AtomicLong time) {
        return new GameSession(new ChessGameModel(), Duration.ofMinutes(5), time::get);
    }

    private void press(GameSession game, int key) { game.keyDown(key); game.keyUp(key); }

    @Test void physicsIsIndependentOfRenderRate() {
        GameSession at30 = session(new AtomicLong());
        GameSession at120 = session(new AtomicLong());
        at30.keyDown(Keys.D);
        at120.keyDown(Keys.D);
        press(at30, Keys.SPACE);
        press(at120, Keys.SPACE);
        for (int i = 0; i < 30; i++) at30.update(1f / 30);
        for (int i = 0; i < 120; i++) at120.update(1f / 120);
        assertEquals(at30.getWhite().getX(), at120.getWhite().getX(), 1e-7);
        assertEquals(at30.getWhite().getY(), at120.getWhite().getY(), 1e-7);
        assertEquals(at30.getBlack().getY(), at120.getBlack().getY(), 1e-7);
    }

    @Test void inactivePlayerCannotPickUpAndHeldCarryDoesNotDrop() {
        GameSession game = session(new AtomicLong());
        press(game, Keys.ENTER);
        assertNull(game.getCarrier());
        game.keyDown(Keys.E);
        game.keyDown(Keys.E);
        assertEquals(Side.WHITE, game.getCarrier());
        assertEquals(Square.E2, game.getBoardView().getHiddenPieceSquare());
        game.keyUp(Keys.E);
        press(game, Keys.E);
        assertNull(game.getCarrier());
        assertTrue(game.getHistory().isEmpty());
    }

    @Test void moveChargesElapsedTimeToMoverBeforeSwitchingClock() {
        AtomicLong time = new AtomicLong();
        GameSession game = session(time);
        press(game, Keys.E);
        game.getWhite().setY(425); // e4
        time.set(2_000_000_000L);
        press(game, Keys.E);
        assertEquals(Side.BLACK, game.getChess().getSideToMove());
        assertEquals("e4", game.getHistory().get(0).notation());
        assertEquals(298, game.getClock().getRemainingSeconds(Side.WHITE));
        time.set(3_000_000_000L);
        game.update(0);
        assertEquals(299, game.getClock().getRemainingSeconds(Side.BLACK));
    }

    @Test void promotionInputCompletesChosenPieceAndClearsCarrier() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
        GameSession game = new GameSession(model, Duration.ofMinutes(5), () -> 0L);
        game.getWhite().setY(125); // e7
        press(game, Keys.E);
        game.getWhite().setY(25); // e8
        press(game, Keys.E);
        assertTrue(game.getChess().hasPendingPromotion());
        assertTrue(game.getHistory().isEmpty());
        press(game, Keys.N);
        assertEquals(Piece.WHITE_KNIGHT, model.getPiece(Square.E8));
        assertFalse(game.getChess().hasPendingPromotion());
        assertNull(game.getCarrier());
        assertEquals(1, game.getHistory().size());
    }

    @Test void timeoutFreezesPlayAndRejectsMoves() {
        AtomicLong time = new AtomicLong();
        GameSession game = session(time);
        time.set(Duration.ofMinutes(5).toNanos());
        game.update(0);
        assertEquals("Black wins", game.getResult());
        assertEquals("White ran out of time", game.getReason());
        double y = game.getWhite().getY();
        press(game, Keys.E);
        press(game, Keys.SPACE);
        game.update(1);
        assertEquals(y, game.getWhite().getY());
        assertNull(game.getCarrier());
    }

    @Test void focusLossReleasesHeldKeys() {
        GameSession game = session(new AtomicLong());
        game.keyDown(Keys.D);
        game.releaseInput();
        game.update(1f / 60);
        assertEquals(425, game.getWhite().getX());
        game.keyDown(Keys.SPACE);
        game.update(1f / 60);
        game.releaseInput();
        game.keyDown(Keys.SPACE);
        assertEquals(-5.0, game.getWhite().getVelocityY());
    }
}
