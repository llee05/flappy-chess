package com.example.flappychess.model;

import java.time.Duration;

/** A bounded time control shared by local games and online peers. Zero means untimed. */
public record MatchSettings(long initialMillis, long incrementMillis) {
    public static final long MAX_INITIAL_MILLIS = 3_600_000;
    public static final long MAX_INCREMENT_MILLIS = 60_000;
    public static final long MAX_REMAINING_MILLIS = MAX_INITIAL_MILLIS + 2048 * MAX_INCREMENT_MILLIS;
    public static final MatchSettings DEFAULT = minutes(5, 0);
    public static final MatchSettings UNTIMED = new MatchSettings(0, 0);

    public MatchSettings {
        if (initialMillis < 0 || initialMillis > MAX_INITIAL_MILLIS || incrementMillis < 0
                || incrementMillis > MAX_INCREMENT_MILLIS || initialMillis == 0 && incrementMillis != 0) {
            throw new IllegalArgumentException("Invalid time control");
        }
    }

    public static MatchSettings minutes(int minutes, int incrementSeconds) {
        return new MatchSettings(minutes * 60_000L, incrementSeconds * 1000L);
    }

    public boolean timed() { return initialMillis > 0; }
    public Duration initialTime() { return Duration.ofMillis(initialMillis); }
    public String label() { return timed() ? initialMillis / 60_000 + "+" + incrementMillis / 1000 : "UNTIMED"; }
    public String pgnTimeControl() { return timed() ? initialMillis / 1000 + "+" + incrementMillis / 1000 : "-"; }
}
