package com.example.flappychess.model;

import com.github.bhlangonijr.chesslib.Side;

import java.time.Duration;

public class ChessClock {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private long whiteRemainingNanos;
    private long blackRemainingNanos;
    private long lastTickNanos = -1;

    public ChessClock(Duration initialTime) {
        long initialNanos = Math.max(0, initialTime.toNanos());
        whiteRemainingNanos = initialNanos;
        blackRemainingNanos = initialNanos;
    }

    public void tick(long nowNanos, Side sideToMove) {
        if (lastTickNanos < 0) {
            lastTickNanos = nowNanos;
            return;
        }

        long elapsedNanos = Math.max(0, nowNanos - lastTickNanos);
        lastTickNanos = nowNanos;

        if (sideToMove == Side.WHITE) {
            whiteRemainingNanos = Math.max(0, whiteRemainingNanos - elapsedNanos);
        } else if (sideToMove == Side.BLACK) {
            blackRemainingNanos = Math.max(0, blackRemainingNanos - elapsedNanos);
        }
    }

    public long getRemainingSeconds(Side side) {
        long remainingNanos = side == Side.BLACK ? blackRemainingNanos : whiteRemainingNanos;
        return (remainingNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    }

    public void addIncrement(Side side, long millis) {
        if (side == Side.WHITE) whiteRemainingNanos += millis * 1_000_000;
        else blackRemainingNanos += millis * 1_000_000;
    }

    public boolean isExpired(Side side) {
        return (side == Side.BLACK ? blackRemainingNanos : whiteRemainingNanos) == 0;
    }

    public long getRemainingMillis(Side side) {
        return (side == Side.BLACK ? blackRemainingNanos : whiteRemainingNanos) / 1_000_000;
    }

    public void restore(long whiteMillis, long blackMillis, long nowNanos) {
        whiteRemainingNanos = whiteMillis * 1_000_000;
        blackRemainingNanos = blackMillis * 1_000_000;
        lastTickNanos = nowNanos;
    }
}
