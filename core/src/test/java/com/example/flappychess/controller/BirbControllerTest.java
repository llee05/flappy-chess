package com.example.flappychess.controller;

import com.example.flappychess.model.Birb;
import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirbControllerTest {
    private static final double EPSILON = 0.0000001;

    @Test
    void rightInputMovesBirdAndReleaseLetsItCoast() {
        Birb birb = new Birb(10, 20, 30, 40);
        BirbController controller = new BirbController(birb, 800, 800);

        controller.handleKeyPressed(Keys.D);
        for (int i = 0; i < 5; i++) {
            controller.update();
        }
        double movingRightX = birb.getX();
        double movingRightVelocity = birb.getVelocityX();

        controller.handleKeyReleased(Keys.D);
        controller.update();

        assertTrue(birb.getX() > movingRightX);
        assertTrue(birb.getVelocityX() > 0);
        assertTrue(birb.getVelocityX() < movingRightVelocity);
    }

    @Test
    void customKeyBindingsMoveAndJumpTheBird() {
        Birb birb = new Birb(10, 20, 30, 40);
        BirbController controller = new BirbController(
                birb,
                800,
                800,
                Keys.LEFT,
                Keys.RIGHT,
                Keys.UP
        );

        controller.handleKeyPressed(Keys.RIGHT);
        controller.handleKeyPressed(Keys.UP);
        controller.update();

        assertEquals(10.36, birb.getX(), EPSILON);
        assertEquals(-4.88, birb.getVelocityY(), EPSILON);
    }

    @Test
    void opposingHorizontalInputsCancelOut() {
        Birb birb = new Birb(10, 20, 30, 40);
        BirbController controller = new BirbController(birb, 800, 800);

        controller.handleKeyPressed(Keys.A);
        controller.handleKeyPressed(Keys.D);
        controller.update();

        assertEquals(10, birb.getX(), EPSILON);
        assertEquals(0, birb.getVelocityX(), EPSILON);
    }

    @Test
    void releasingOneOfOpposingHorizontalInputsUsesRemainingDirection() {
        Birb birb = new Birb(10, 20, 30, 40);
        BirbController controller = new BirbController(birb, 800, 800);

        controller.handleKeyPressed(Keys.A);
        controller.handleKeyPressed(Keys.D);
        controller.update();

        controller.handleKeyReleased(Keys.A);
        controller.update();

        assertEquals(10.36, birb.getX(), EPSILON);
        assertTrue(birb.getVelocityX() > 0);
    }

    @Test
    void heldJumpOnlyTriggersOnceUntilReleased() {
        Birb birb = new Birb(10, 20, 30, 40);
        BirbController controller = new BirbController(birb, 800, 800);

        controller.handleKeyPressed(Keys.SPACE);
        controller.update();

        assertEquals(-4.88, birb.getVelocityY(), EPSILON);

        controller.handleKeyPressed(Keys.SPACE);
        controller.update();

        assertEquals(-4.76, birb.getVelocityY(), EPSILON);

        controller.handleKeyReleased(Keys.SPACE);
        controller.handleKeyPressed(Keys.SPACE);
        controller.update();

        assertEquals(-4.88, birb.getVelocityY(), EPSILON);
    }

    @Test
    void releasingHorizontalKeyPreservesFlapTrajectoryWhileGliding() {
        for (int gravity : new int[] {-1, 1}) {
            Birb released = new Birb(300, 350, 50, 50, gravity);
            Birb held = new Birb(300, 350, 50, 50, gravity);
            BirbController releasedControls = new BirbController(released, 800, 800);
            BirbController heldControls = new BirbController(held, 800, 800);
            for (BirbController controls : new BirbController[] {releasedControls, heldControls}) {
                controls.handleKeyPressed(Keys.D);
                controls.handleKeyPressed(Keys.SPACE);
            }
            for (int i = 0; i < 12; i++) {
                releasedControls.update();
                heldControls.update();
            }

            double releaseSpeed = released.getVelocityX();
            double verticalSpeed = released.getVelocityY();
            releasedControls.handleKeyReleased(Keys.D);
            assertEquals(verticalSpeed, released.getVelocityY(), EPSILON);

            for (int i = 0; i < 15; i++) {
                releasedControls.update();
                heldControls.update();
                assertEquals(held.getY(), released.getY(), EPSILON);
                assertEquals(held.getVelocityY(), released.getVelocityY(), EPSILON);
            }
            assertTrue(released.getVelocityX() > releaseSpeed * 0.4);
            assertTrue(released.getVelocityX() < releaseSpeed * 0.6);
            assertTrue(released.getVelocityY() * gravity < 0);
        }
    }

    @Test
    void updateConstrainsBirdToBounds() {
        Birb birb = new Birb(790, 790, 30, 40);
        BirbController controller = new BirbController(birb, 800, 800);

        controller.update();

        assertEquals(770, birb.getX(), EPSILON);
        assertEquals(760, birb.getY(), EPSILON);
    }
}
