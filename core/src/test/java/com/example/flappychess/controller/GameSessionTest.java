package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
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
        at30.keyDown(Keys.RIGHT);
        at120.keyDown(Keys.RIGHT);
        press(at30, Keys.SPACE);
        press(at120, Keys.SPACE);
        press(at30, Keys.UP);
        press(at120, Keys.UP);
        for (int i = 0; i < 15; i++) at30.update(1f / 30);
        for (int i = 0; i < 60; i++) at120.update(1f / 120);
        at30.keyUp(Keys.D);
        at120.keyUp(Keys.D);
        at30.keyUp(Keys.RIGHT);
        at120.keyUp(Keys.RIGHT);
        for (int i = 0; i < 15; i++) at30.update(1f / 30);
        for (int i = 0; i < 60; i++) at120.update(1f / 120);
        assertEquals(at30.getWhite().getX(), at120.getWhite().getX(), 1e-7);
        assertEquals(at30.getWhite().getY(), at120.getWhite().getY(), 1e-7);
        assertEquals(at30.getWhite().getVelocityX(), at120.getWhite().getVelocityX(), 1e-7);
        assertEquals(at30.getBlack().getX(), at120.getBlack().getX(), 1e-7);
        assertEquals(at30.getBlack().getY(), at120.getBlack().getY(), 1e-7);
        assertEquals(at30.getBlack().getVelocityX(), at120.getBlack().getVelocityX(), 1e-7);
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

    @Test void timeoutAgainstLoneKingResultsInDraw() {
        AtomicLong time = new AtomicLong();
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("7k/8/8/8/8/8/R7/K7 w - - 0 1");
        GameSession game = new GameSession(model, new MatchSettings(1000, 0), time::get);
        time.set(Duration.ofMillis(1100).toNanos());
        game.update(0);
        assertEquals("Draw", game.getResult());
        assertEquals("White ran out of time", game.getReason());
    }

    @Test void blackTimeoutAgainstLoneKingResultsInDraw() {
        AtomicLong time = new AtomicLong();
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("7k/7r/8/8/8/8/8/K7 b - - 0 1");
        GameSession game = new GameSession(model, new MatchSettings(1000, 0), time::get);
        time.set(Duration.ofMillis(1100).toNanos());
        game.update(0);
        assertEquals("Draw", game.getResult());
        assertEquals("Black ran out of time", game.getReason());
    }

    @Test void timeoutDuringPendingPromotionAgainstLoneKingResultsInDraw() {
        AtomicLong time = new AtomicLong();
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
        GameSession game = new GameSession(model, new MatchSettings(1000, 5000), time::get);
        game.getWhite().setY(125);
        game.command(Side.WHITE, GameAction.CARRY, true);
        game.command(Side.WHITE, GameAction.CARRY, false);
        game.getWhite().setY(25);
        game.command(Side.WHITE, GameAction.CARRY, true);
        assertTrue(game.getChess().hasPendingPromotion());
        time.set(Duration.ofMillis(1100).toNanos());
        game.update(0);
        assertEquals("Draw", game.getResult());
        assertEquals("White ran out of time", game.getReason());
        assertTrue(game.getHistory().isEmpty());
        press(game, Keys.Q);
        assertEquals("Draw", game.getResult());
        assertTrue(game.getHistory().isEmpty());
    }

    @Test void timeoutDuringPendingPromotionAgainstMatingMaterialAwardsWin() {
        AtomicLong time = new AtomicLong();
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("k4r2/4P3/8/8/8/8/8/4K3 w - - 0 1");
        GameSession game = new GameSession(model, new MatchSettings(1000, 5000), time::get);
        game.getWhite().setY(125);
        game.command(Side.WHITE, GameAction.CARRY, true);
        game.command(Side.WHITE, GameAction.CARRY, false);
        game.getWhite().setY(25);
        game.command(Side.WHITE, GameAction.CARRY, true);
        assertTrue(game.getChess().hasPendingPromotion());
        time.set(Duration.ofMillis(1100).toNanos());
        game.update(0);
        assertEquals("Black wins", game.getResult());
        assertEquals("White ran out of time", game.getReason());
    }

    @Test void timeoutAgainstHelpmateMaterialAwardsWin() {
        AtomicLong time = new AtomicLong();
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("7k/7n/8/8/8/8/7P/4K3 w - - 0 1");
        GameSession game = new GameSession(model, new MatchSettings(1000, 0), time::get);
        time.set(Duration.ofMillis(1100).toNanos());
        game.update(0);
        assertEquals("Black wins", game.getResult());
        assertEquals("White ran out of time", game.getReason());
    }

    @Test void timeoutCompletesDroppedPieceReturnPresentation() {
        AtomicLong time = new AtomicLong();
        GameSession game = new GameSession(new ChessGameModel(), Duration.ofMillis(50), time::get);
        press(game, Keys.E);
        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));
        game.update(1f / 60);
        assertNotNull(game.getDropReturn());

        time.set(Duration.ofMillis(50).toNanos());
        game.update(0);

        assertTrue(game.isGameOver());
        assertNull(game.getDropReturn());
        assertEquals(Piece.WHITE_PAWN, game.getBoardView().getBoard().getPiece(Square.E2));
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

    @Test void bumpDislodgesCarriedPieceBackToOriginAndStunsCarrier() {
        GameSession game = session(new AtomicLong());
        press(game, Keys.E);
        assertEquals(Side.WHITE, game.getCarrier());
        assertEquals(Square.E2, game.getBoardView().getHiddenPieceSquare());

        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));
        game.update(1f / 60);

        assertNull(game.getCarrier());
        assertEquals(Square.NONE, game.getBoardView().getHiddenPieceSquare());
        assertEquals(Square.NONE, game.getBoardView().getSelectedSquare());
        assertTrue(game.getBoardView().getLegalTargets().isEmpty());
        assertEquals(Piece.WHITE_PAWN, game.getBoardView().getBoard().getPiece(Square.E2));
        assertEquals(Piece.NONE, game.getBoardView().getBoard().getPiece(Square.E4));
        assertTrue(game.getHistory().isEmpty());
        assertEquals(Side.WHITE, game.getChess().getSideToMove());
        assertEquals(Side.BLACK, game.getLastBumper());
        assertEquals(Side.WHITE, game.getLastBumped());
        assertEquals(GameSession.BUMP_COOLDOWN_STEPS, game.getBumpCooldown());
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS, game.getHitStopSteps());
        assertEquals(GameSession.BUMP_STUN_STEPS, game.getWhite().getStunSteps());
        assertFalse(game.getBlack().isStunned());
        assertEquals(-GameSession.BUMP_KNOCKBACK_X, game.getWhite().getVelocityX(), 1e-7);
        assertEquals(GameSession.BUMP_RECOIL_X, game.getBlack().getVelocityX(), 1e-7);
        assertNotNull(game.getDropReturn());
        assertEquals(Piece.WHITE_PAWN, game.getDropReturn().piece());
        assertEquals(Square.E2, game.getDropReturn().origin());
        assertEquals(GameSession.DROP_RETURN_STEPS, game.getDropReturn().stepsRemaining());
        assertNotNull(game.getLastBump());
        assertTrue(game.getLastBump().dropped());
        assertTrue(game.getLastBump().speed() >= GameSession.MIN_BUMP_SPEED);
        var events = game.getFeedback().events();
        assertEquals(GameFeedback.Kind.BUMP_DROP, events.get(events.size() - 1).kind());
        assertEquals(Side.WHITE, events.get(events.size() - 1).side());
        assertEquals(Square.E2, events.get(events.size() - 1).square());
        assertEquals(events.get(events.size() - 1).sequence(), game.getLastBump().sequence());
    }

    @Test void stunAndCooldownPreventChainBumpsAndSuppressInputUntilRecovered() {
        GameSession game = session(new AtomicLong());
        press(game, Keys.E);
        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 625, 0, 0));
        game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(450, 625, -3.6, 0));
        game.update(1f / 60);

        int bumpEventsAfterFirst = (int) game.getFeedback().events().stream()
                .filter(e -> e.kind() == GameFeedback.Kind.BUMP_DROP).count();
        assertEquals(1, bumpEventsAfterFirst);
        assertTrue(game.getWhite().isStunned());
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS, game.getHitStopSteps());

        // Hit-stop freezes bird movement, stun countdown, and dropped-piece arc until it finishes.
        double frozenWhiteX = game.getWhite().getX();
        double frozenBlackX = game.getBlack().getX();
        for (int i = 0; i < GameSession.CARRY_HIT_STOP_STEPS; i++) {
            game.update(1f / 60);
            assertEquals(frozenWhiteX, game.getWhite().getX(), 1e-7);
            assertEquals(frozenBlackX, game.getBlack().getX(), 1e-7);
            assertEquals(GameSession.BUMP_STUN_STEPS, game.getWhite().getStunSteps());
            assertEquals(GameSession.DROP_RETURN_STEPS, game.getDropReturn().stepsRemaining());
        }
        assertEquals(0, game.getHitStopSteps());

        // While stunned, flap and pickup are ignored.
        double vyBefore = game.getWhite().getVelocityY();
        press(game, Keys.SPACE);
        press(game, Keys.E);
        assertEquals(vyBefore, game.getWhite().getVelocityY(), 1e-7);
        assertNull(game.getCarrier());

        // Even if forced back into overlap during cooldown, no second bump triggers.
        for (int i = 0; i < GameSession.BUMP_COOLDOWN_STEPS; i++) {
            game.getWhite().setY(625);
            game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(game.getWhite().getX() + 10, 625, -3.6, 0));
            game.update(1f / 60);
        }
        assertEquals(0, game.getBumpCooldown());
        assertFalse(game.getWhite().isStunned());
        assertNull(game.getDropReturn());
        assertEquals(1, game.getFeedback().events().stream()
                .filter(e -> e.kind() == GameFeedback.Kind.BUMP || e.kind() == GameFeedback.Kind.BUMP_DROP).count());

        // After stun expires, White can pick up on E2 again.
        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 625, 0, 0));
        press(game, Keys.E);
        assertEquals(Side.WHITE, game.getCarrier());
    }

    @Test void emptyHandedBumpAppliesMinimalKnockbackWithoutStun() {
        GameSession game = session(new AtomicLong());
        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(395, 425, 3.6, 0));
        game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        game.update(1f / 60);

        assertEquals(Side.WHITE, game.getLastBumper());
        assertEquals(Side.BLACK, game.getLastBumped());
        assertFalse(game.getWhite().isStunned());
        assertFalse(game.getBlack().isStunned());
        assertEquals(-GameSession.EMPTY_BUMP_RECOIL_X, game.getWhite().getVelocityX(), 1e-7);
        assertEquals(GameSession.EMPTY_BUMP_KNOCKBACK_X, game.getBlack().getVelocityX(), 1e-7);
        assertEquals(GameSession.BUMP_COOLDOWN_STEPS, game.getBumpCooldown());
        assertEquals(GameSession.EMPTY_HIT_STOP_STEPS, game.getHitStopSteps());
        assertNull(game.getDropReturn());
        assertNotNull(game.getLastBump());
        assertFalse(game.getLastBump().dropped());
        var events = game.getFeedback().events();
        assertEquals(GameFeedback.Kind.BUMP, events.get(events.size() - 1).kind());
        assertEquals(Side.BLACK, events.get(events.size() - 1).side());

        // Empty-handed hit-stop is shorter and releases motion after EMPTY_HIT_STOP_STEPS.
        double frozenX = game.getBlack().getX();
        for (int i = 0; i < GameSession.EMPTY_HIT_STOP_STEPS; i++) {
            game.update(1f / 60);
            assertEquals(frozenX, game.getBlack().getX(), 1e-7);
        }
        game.update(1f / 60);
        assertTrue(game.getBlack().getX() > frozenX);
    }

    @Test void carrierRammingDefenderStillDropsPieceAndStunsBothBirds() {
        GameSession game = session(new AtomicLong());
        press(game, Keys.E);
        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(395, 425, 3.6, 0));
        game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        game.update(1f / 60);

        assertNull(game.getCarrier());
        assertEquals(Piece.WHITE_PAWN, game.getBoardView().getBoard().getPiece(Square.E2));
        assertEquals(Side.WHITE, game.getLastBumper());
        assertEquals(Side.BLACK, game.getLastBumped());
        assertTrue(game.getWhite().isStunned());
        assertTrue(game.getBlack().isStunned());
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS, game.getHitStopSteps());
        assertNotNull(game.getDropReturn());
        assertEquals(-GameSession.BUMP_RECOIL_X, game.getWhite().getVelocityX(), 1e-7);
        assertEquals(GameSession.BUMP_KNOCKBACK_X, game.getBlack().getVelocityX(), 1e-7);
    }

    @Test void hitStopIgnoresFlapAndCarryWhilePreservingDirectionalHoldAndClockTime() {
        AtomicLong time = new AtomicLong();
        GameSession game = session(time);
        press(game, Keys.E);
        game.getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        game.getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));
        game.update(1f / 60);
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS, game.getHitStopSteps());

        // Non-stunned Black bird cannot flap during hit-stop, and clock still advances in real time.
        double blackVy = game.getBlack().getVelocityY();
        press(game, Keys.UP);
        assertEquals(blackVy, game.getBlack().getVelocityY(), 1e-7);
        time.addAndGet(Duration.ofSeconds(2).toNanos());
        game.update(1f / 60);
        assertEquals(298, game.getClock().getRemainingSeconds(Side.WHITE));
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS - 1, game.getHitStopSteps());
    }
}
