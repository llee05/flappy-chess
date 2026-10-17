package com.example.flappychess.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirbTest {
    private static final double EPSILON = 0.0000001;

    @Test
    void startsAtGivenPositionAndSizeWithNoVerticalVelocity() {
        Birb birb = new Birb(10, 20, 30, 40);

        assertEquals(10, birb.getX(), EPSILON);
        assertEquals(20, birb.getY(), EPSILON);
        assertEquals(30, birb.getWidth(), EPSILON);
        assertEquals(40, birb.getHeight(), EPSILON);
        assertEquals(0, birb.getVelocityX(), EPSILON);
        assertEquals(0, birb.getVelocityY(), EPSILON);
    }

    @Test
    void updateAppliesGravityAndMovesVertically() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.update();

        assertEquals(0.12, birb.getVelocityY(), EPSILON);
        assertEquals(20.12, birb.getY(), EPSILON);
        assertEquals(10, birb.getX(), EPSILON);
    }

    @Test
    void jumpAppliesUpwardVelocityBeforeGravityOnNextUpdate() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.jump();
        birb.update();

        assertEquals(-4.88, birb.getVelocityY(), EPSILON);
        assertEquals(15.12, birb.getY(), EPSILON);
    }

    @Test
    void fallSpeedIsCapped() {
        Birb birb = new Birb(0, 0, 30, 40);

        for (int i = 0; i < 1_000; i++) {
            birb.update();
        }

        assertEquals(4.2, birb.getVelocityY(), EPSILON);
    }

    @Test
    void invertedGravityFallsUpward() {
        Birb birb = new Birb(10, 20, 30, 40, -1);

        birb.update();

        assertEquals(-0.12, birb.getVelocityY(), EPSILON);
        assertEquals(19.88, birb.getY(), EPSILON);
        assertEquals(10, birb.getX(), EPSILON);
    }

    @Test
    void invertedGravityJumpPushesDownwardBeforeGravityOnNextUpdate() {
        Birb birb = new Birb(10, 20, 30, 40, -1);

        birb.jump();
        birb.update();

        assertEquals(4.88, birb.getVelocityY(), EPSILON);
        assertEquals(24.88, birb.getY(), EPSILON);
    }

    @Test
    void invertedGravityFallSpeedIsCapped() {
        Birb birb = new Birb(0, 100, 30, 40, -1);

        for (int i = 0; i < 1_000; i++) {
            birb.update();
        }

        assertEquals(-4.2, birb.getVelocityY(), EPSILON);
    }

    @Test
    void horizontalInputAcceleratesTowardMoveSpeed() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.setHorizontalInput(1);
        for (int i = 0; i < 12; i++) {
            birb.update();
        }

        assertEquals(37.0, birb.getX(), EPSILON);
        assertEquals(3.6, birb.getVelocityX(), EPSILON);
    }

    @Test
    void horizontalInputIsNormalizedToDirection() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.setHorizontalInput(999);
        birb.update();

        assertEquals(10.36, birb.getX(), EPSILON);
    }

    @Test
    void horizontalInputCanMoveLeftAtCappedSpeed() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.setHorizontalInput(-1);
        for (int i = 0; i < 12; i++) {
            birb.update();
        }

        assertEquals(-17.0, birb.getX(), EPSILON);
        assertEquals(-3.6, birb.getVelocityX(), EPSILON);
    }

    @Test
    void reversingHorizontalInputTurnsBirdAround() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.setHorizontalInput(1);
        for (int i = 0; i < 5; i++) {
            birb.update();
        }

        birb.setHorizontalInput(-1);
        for (int i = 0; i < 5; i++) {
            birb.update();
        }

        assertTrue(birb.getVelocityX() < 0);
    }

    @Test
    void horizontalVelocityCoastsAfterInputStops() {
        Birb birb = new Birb(10, 20, 30, 40);

        birb.setHorizontalInput(1);
        for (int i = 0; i < 5; i++) {
            birb.update();
        }
        double movingRightX = birb.getX();
        double movingRightVelocity = birb.getVelocityX();

        birb.setHorizontalInput(0);
        birb.update();
        double firstDrift = birb.getX() - movingRightX;
        double releaseVelocity = birb.getVelocityX();
        birb.update();
        double secondDrift = birb.getX() - movingRightX - firstDrift;

        assertTrue(releaseVelocity > 0);
        assertTrue(releaseVelocity < movingRightVelocity);
        assertTrue(firstDrift > 0);
        assertTrue(secondDrift > 0);
        assertTrue(secondDrift < firstDrift);

        for (int i = 0; i < 28; i++) {
            birb.update();
        }

        assertTrue(birb.getX() > movingRightX + 10.0);

        for (int i = 0; i < 500; i++) {
            birb.update();
        }
        double restingX = birb.getX();

        birb.update();

        assertEquals(restingX, birb.getX(), EPSILON);
    }

    @Test
    void releasingAtFullSpeedGlidesLessThanOneSquareBeforeStopping() {
        for (int direction : new int[] {-1, 1}) {
            Birb birb = new Birb(300, 300, 50, 50);
            birb.setHorizontalInput(direction);
            for (int i = 0; i < 12; i++) birb.update();

            double releaseX = birb.getX();
            double releaseSpeed = Math.abs(birb.getVelocityX());
            birb.setHorizontalInput(0);
            for (int i = 0; i < 15; i++) birb.update();

            // Retain useful sideways momentum a quarter-second after release.
            double retainedSpeed = Math.abs(birb.getVelocityX()) / releaseSpeed;
            assertTrue(retainedSpeed > 0.4 && retainedSpeed < 0.6);
            double initialGlide = (birb.getX() - releaseX) * direction;
            assertTrue(initialGlide > 30 && initialGlide < 45);

            for (int i = 0; i < 180; i++) birb.update();

            double totalGlide = (birb.getX() - releaseX) * direction;
            assertTrue(totalGlide > 60 && totalGlide < 75);
            assertEquals(0, birb.getVelocityX(), EPSILON);
        }
    }

    @Test
    void oppositeGravityMirrorsFlappingSteeringCoastingAndBounds() {
        Birb white = new Birb(375, 375, 50, 50);
        Birb black = new Birb(375, 375, 50, 50, -1);
        for (int step = 0; step < 300; step++) {
            if (step == 0 || step == 90) {
                white.jump();
                black.jump();
            }
            int direction = step < 30 ? 1 : step < 60 ? 0 : step < 150 ? -1 : 0;
            white.setHorizontalInput(direction);
            black.setHorizontalInput(direction);
            white.update();
            black.update();
            white.constrainToBounds(800, 800);
            black.constrainToBounds(800, 800);

            assertEquals(white.getX(), black.getX(), EPSILON);
            assertEquals(750, white.getY() + black.getY(), EPSILON);
            assertEquals(white.getVelocityX(), black.getVelocityX(), EPSILON);
            assertEquals(-white.getVelocityY(), black.getVelocityY(), EPSILON);
        }
    }

    @Test
    void constrainToBoundsClampsPositionAndStopsOutwardVerticalMovement() {
        Birb birb = new Birb(-12, -4, 30, 40);
        birb.setVelocityX(-0.5);
        birb.jump();

        birb.constrainToBounds(800, 800);

        assertEquals(0, birb.getX(), EPSILON);
        assertEquals(0, birb.getY(), EPSILON);
        assertEquals(0, birb.getVelocityY(), EPSILON);
    }

    @Test
    void constrainToBoundsStopsOutwardHorizontalMovement() {
        Birb leftEdgeBirb = new Birb(-12, 10, 30, 40);
        leftEdgeBirb.setVelocityX(-0.5);

        leftEdgeBirb.constrainToBounds(800, 800);

        assertEquals(0, leftEdgeBirb.getX(), EPSILON);
        assertEquals(0, leftEdgeBirb.getVelocityX(), EPSILON);

        Birb rightEdgeBirb = new Birb(790, 10, 30, 40);
        rightEdgeBirb.setVelocityX(0.5);

        rightEdgeBirb.constrainToBounds(800, 800);

        assertEquals(770, rightEdgeBirb.getX(), EPSILON);
        assertEquals(0, rightEdgeBirb.getVelocityX(), EPSILON);
    }

    @Test
    void constrainToBoundsClampsRightAndBottomEdges() {
        Birb birb = new Birb(790, 790, 30, 40);

        birb.constrainToBounds(800, 800);

        assertEquals(770, birb.getX(), EPSILON);
        assertEquals(760, birb.getY(), EPSILON);
    }
}
