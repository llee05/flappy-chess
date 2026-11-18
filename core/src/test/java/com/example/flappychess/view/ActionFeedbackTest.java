package com.example.flappychess.view;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionFeedbackTest {
    private static void triggerCarryingBump(GameSession game, double whiteVx, double blackVx) {
        game.keyDown(Keys.E);
        game.keyUp(Keys.E);
        game.getWhite().restore(new Birb.Motion(425, 425, whiteVx, 0));
        game.getBlack().restore(new Birb.Motion(455, 425, blackVx, 0));
        game.update(1f / 60);
    }

    private static void triggerEmptyBump(GameSession game, double whiteVx, double blackVx) {
        game.getWhite().restore(new Birb.Motion(425, 425, whiteVx, 0));
        game.getBlack().restore(new Birb.Motion(455, 425, blackVx, 0));
        game.update(1f / 60);
    }

    @Test void carryingBumpProducesStrongerShakeFlashAndCalloutThanEmptyBump() {
        GameSession carrying = new GameSession(MatchSettings.UNTIMED);
        ActionFeedback carryingFeedback = new ActionFeedback(null);
        triggerCarryingBump(carrying, 0, -3.6);
        carryingFeedback.update(carrying, 0);

        GameSession empty = new GameSession(MatchSettings.UNTIMED);
        ActionFeedback emptyFeedback = new ActionFeedback(null);
        triggerEmptyBump(empty, 0, -3.6);
        emptyFeedback.update(empty, 0);

        assertTrue(carryingFeedback.currentShakeAmplitude() > emptyFeedback.currentShakeAmplitude());
        assertTrue(carryingFeedback.boardFlashAlpha() > 0);
        assertEquals(0, emptyFeedback.boardFlashAlpha(), 1e-6);
        assertEquals("DROPPED!", carryingFeedback.calloutText());
        assertEquals("", emptyFeedback.calloutText());
        assertEquals("Bumped! Piece dropped!", carryingFeedback.message());
        assertEquals("Midair bump!", emptyFeedback.message());

        // Empty bump shake settles before carrying bump shake finishes.
        carryingFeedback.update(carrying, ActionFeedback.EMPTY_SHAKE_DURATION + 0.01f);
        emptyFeedback.update(empty, ActionFeedback.EMPTY_SHAKE_DURATION + 0.01f);
        assertEquals(0, emptyFeedback.currentShakeAmplitude(), 1e-6);
        assertTrue(carryingFeedback.currentShakeAmplitude() > 0);
    }

    @Test void cameraShakeScalesWithImpactSpeed() {
        GameSession slow = new GameSession(MatchSettings.UNTIMED);
        ActionFeedback slowFeedback = new ActionFeedback(null);
        triggerCarryingBump(slow, 0, -1.6);
        slowFeedback.update(slow, 0);

        GameSession fast = new GameSession(MatchSettings.UNTIMED);
        ActionFeedback fastFeedback = new ActionFeedback(null);
        triggerCarryingBump(fast, 3.6, -3.6);
        fastFeedback.update(fast, 0);

        assertTrue(fastFeedback.currentShakeAmplitude() > slowFeedback.currentShakeAmplitude());
    }

    @Test void reducedMotionDisablesShakeAndBoardFlashWhileKeepingCalloutAndMessage() {
        GameSession game = new GameSession(MatchSettings.UNTIMED);
        ActionFeedback feedback = new ActionFeedback(null);
        feedback.setReducedMotion(true);
        triggerCarryingBump(game, 3.6, -3.6);
        feedback.update(game, 0);

        assertEquals(0, feedback.currentShakeAmplitude(), 1e-6);
        assertEquals(0, feedback.shakeX(), 1e-6);
        assertEquals(0, feedback.shakeY(), 1e-6);
        assertEquals(0, feedback.boardFlashAlpha(), 1e-6);
        assertEquals("DROPPED!", feedback.calloutText());
        assertEquals("Bumped! Piece dropped!", feedback.message());
    }

    @Test void droppedPieceLandingBounceOccursAtReturnArrivalAndDoesNotReplay() {
        GameSession game = new GameSession(MatchSettings.UNTIMED);
        ActionFeedback feedback = new ActionFeedback(null);
        triggerCarryingBump(game, 0, -3.6);
        feedback.update(game, 0);

        assertEquals(0, feedback.dropOffset(Square.E2), 1e-6);
        int steps = 0;
        while (game.getDropReturn() != null) {
            assertEquals(0, feedback.dropOffset(Square.E2), 1e-6);
            game.update(1f / 60);
            feedback.update(game, 1f / 60);
            assertTrue(++steps < 60, "Dropped piece did not return within one second");
        }
        assertTrue(feedback.dropOffset(Square.E2) > 0);

        // Once callout and shake expire, repeated updates without new events do not re-trigger them.
        feedback.update(game, 1.0f);
        assertEquals(0, feedback.currentShakeAmplitude(), 1e-6);
        assertEquals("", feedback.calloutText());
        assertEquals(0, feedback.dropOffset(Square.E2), 1e-6);
        feedback.update(game, 0.016f);
        assertEquals(0, feedback.currentShakeAmplitude(), 1e-6);
        assertEquals("", feedback.calloutText());
    }
}
