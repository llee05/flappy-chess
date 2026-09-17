package com.example.flappychess.model;

public class Birb {
    private static final double GRAVITY = 0.003;
    private static final double JUMP_SPEED = 0.8;
    private static final double HORIZONTAL_MOVE_SPEED = 0.45;
    private static final double MAX_FALL_SPEED = 1.0;
    private static final double HORIZONTAL_ACCELERATION = 0.10;
    private static final double HORIZONTAL_TURN_ACCELERATION = 0.10;
    private static final double HORIZONTAL_FRICTION = 0.985;
    private static final double MIN_HORIZONTAL_SPEED = 0.001;

    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private int horizontalInput;

    private final double width;
    private final double height;
    private final int gravityDirection;

    public Birb(double x, double y, double width, double height) {
        this(x, y, width, height, 1);
    }

    public Birb(double x, double y, double width, double height, int gravityDirection) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.gravityDirection = gravityDirection < 0 ? -1 : 1;
        this.velocityX = 0;
        this.velocityY = 0;
        this.horizontalInput = 0;
    }

    public void jump() {
        velocityY = -gravityDirection * JUMP_SPEED;
    }

    public void setHorizontalInput(int direction) {
        horizontalInput = Integer.compare(direction, 0);
    }

    public void update() {
        velocityY += GRAVITY * gravityDirection;
        velocityY = capFallSpeed(velocityY);
        updateHorizontalVelocity();

        x += velocityX;
        y += velocityY;
    }

    private double capFallSpeed(double velocity) {
        if (gravityDirection > 0) {
            return Math.min(velocity, MAX_FALL_SPEED);
        }

        return Math.max(velocity, -MAX_FALL_SPEED);
    }

    private void updateHorizontalVelocity() {
        if (horizontalInput != 0) {
            double targetVelocity = horizontalInput * HORIZONTAL_MOVE_SPEED;
            double acceleration = Math.signum(velocityX) == -horizontalInput
                    ? HORIZONTAL_TURN_ACCELERATION
                    : HORIZONTAL_ACCELERATION;

            velocityX = moveToward(velocityX, targetVelocity, acceleration);
        } else {
            velocityX *= HORIZONTAL_FRICTION;
        }

        if (horizontalInput == 0 && Math.abs(velocityX) < MIN_HORIZONTAL_SPEED) {
            velocityX = 0;
        }
    }

    private double moveToward(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        return Math.max(current - maxDelta, target);
    }

    public void constrainToBounds(double maxWidth, double maxHeight) {
        if (x < 0) {
            x = 0;
            velocityX = Math.max(0, velocityX);
        } else if (x + width > maxWidth) {
            x = maxWidth - width;
            velocityX = Math.min(0, velocityX);
        }

        if (y < 0) {
            y = 0;
            velocityY = Math.max(0, velocityY);
        } else if (y + height > maxHeight) {
            y = maxHeight - height;
            velocityY = Math.min(0, velocityY);
        }
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setVelocityX(double x) {
        this.velocityX = x;
    }
}
