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
        return getRemainingNanos(side) / 1_000_000;
    }

    public long getRemainingNanos(Side side) {
        return side == Side.BLACK ? blackRemainingNanos : whiteRemainingNanos;
    }

    public void restore(long whiteMillis, long blackMillis, long nowNanos) {
        restoreNanos(whiteMillis * 1_000_000, blackMillis * 1_000_000, nowNanos);
    }

    public void restoreNanos(long whiteNanos, long blackNanos, long nowNanos) {
        whiteRemainingNanos = Math.max(0, whiteNanos);
        blackRemainingNanos = Math.max(0, blackNanos);
        lastTickNanos = nowNanos;
    }
}
