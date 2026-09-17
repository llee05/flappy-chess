package com.example.flappychess.controller;

import com.example.flappychess.model.Birb;
import com.badlogic.gdx.Input.Keys;

public class BirbController {
    private final Birb bird;
    private final double maxWidth;
    private final double maxHeight;
    private final int leftKey;
    private final int rightKey;
    private final int jumpKey;

    private boolean jumpPressed;
    private boolean leftPressed;
    private boolean rightPressed;

    public BirbController(Birb bird, double maxWidth, double maxHeight) {
        this(bird, maxWidth, maxHeight, Keys.A, Keys.D, Keys.SPACE);
    }

    public BirbController(Birb bird, double maxWidth, double maxHeight, int leftKey, int rightKey, int jumpKey) {
        this.bird = bird;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.jumpKey = jumpKey;
    }

    public void handleKeyPressed(int keyCode) {
        if (keyCode == jumpKey) {
            if (!jumpPressed) {
                bird.jump();
            }
            jumpPressed = true;
        }

        if (keyCode == leftKey) {
            leftPressed = true;
        }

        if (keyCode == rightKey) {
            rightPressed = true;
        }
    }

    public void handleKeyReleased(int keyCode) {
        if (keyCode == jumpKey) {
            jumpPressed = false;
        }

        if (keyCode == leftKey) {
            leftPressed = false;
        }

        if (keyCode == rightKey) {
            rightPressed = false;
        }
    }

    public void update() {
        bird.setHorizontalInput(getHorizontalInput());
        bird.update();
        bird.constrainToBounds(maxWidth, maxHeight);
    }

    private int getHorizontalInput() {
        if (leftPressed == rightPressed) {
            return 0;
        }

        return leftPressed ? -1 : 1;
    }
}
