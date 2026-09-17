package com.example.flappychess.model;

import com.github.bhlangonijr.chesslib.Side;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessClockTest {
    @Test
    void startsBothSidesWithInitialTime() {
        ChessClock clock = new ChessClock(Duration.ofSeconds(285));

        assertEquals(285, clock.getRemainingSeconds(Side.WHITE));
        assertEquals(285, clock.getRemainingSeconds(Side.BLACK));
    }

    @Test
    void firstTickOnlyStartsClock() {
        ChessClock clock = new ChessClock(Duration.ofSeconds(285));

        clock.tick(1_000_000_000L, Side.WHITE);

        assertEquals(285, clock.getRemainingSeconds(Side.WHITE));
        assertEquals(285, clock.getRemainingSeconds(Side.BLACK));
    }

    @Test
    void tickSubtractsElapsedTimeOnlyFromActiveSide() {
        ChessClock clock = new ChessClock(Duration.ofSeconds(285));

        clock.tick(0, Side.WHITE);
        clock.tick(1_000_000_000L, Side.WHITE);
        clock.tick(3_000_000_000L, Side.BLACK);

        assertEquals(284, clock.getRemainingSeconds(Side.WHITE));
        assertEquals(283, clock.getRemainingSeconds(Side.BLACK));
    }

    @Test
    void remainingSecondsRoundUpPartialSecondsForDisplay() {
        ChessClock clock = new ChessClock(Duration.ofSeconds(10));

        clock.tick(0, Side.WHITE);
        clock.tick(100_000_000L, Side.WHITE);

        assertEquals(10, clock.getRemainingSeconds(Side.WHITE));
    }

    @Test
    void clockClampsAtZero() {
        ChessClock clock = new ChessClock(Duration.ofSeconds(1));

        clock.tick(0, Side.WHITE);
        clock.tick(2_000_000_000L, Side.WHITE);

        assertEquals(0, clock.getRemainingSeconds(Side.WHITE));
        assertTrue(clock.isExpired(Side.WHITE));
        assertFalse(clock.isExpired(Side.BLACK));
    }
}
