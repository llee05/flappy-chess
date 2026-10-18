package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchSettingsTest {
    private void press(GameSession game, int key) { game.keyDown(key); game.keyUp(key); }

    @Test void untimedGamesKeepPhysicsAndLegalMovesWithoutTimeouts() {
        AtomicLong time = new AtomicLong();
        GameSession game = new GameSession(new ChessGameModel(), MatchSettings.UNTIMED, time::get);
        time.set(Duration.ofHours(3).toNanos());
        press(game, Keys.E);
        game.getWhite().setY(425);
        press(game, Keys.E);
        assertEquals("e4", game.getHistory().get(0).notation());
        assertEquals(Side.BLACK, game.getChess().getSideToMove());
        game.update(1f / 60);
        assertTrue(game.getBlack().getY() < 125);
        assertFalse(game.isGameOver());
        assertTrue(game.getFeedback().events().stream().noneMatch(e -> e.kind() == GameFeedback.Kind.LOW_TIME));
    }

    @Test void onlyCompletedMovesReceiveAnIncrementAfterElapsedTimeIsCharged() {
        AtomicLong time = new AtomicLong();
        GameSession game = new GameSession(new ChessGameModel(), MatchSettings.minutes(10, 5), time::get);
        press(game, Keys.E);
        time.set(Duration.ofSeconds(4).toNanos());
        game.getWhite().setY(325); // Illegal e5 drop.
        press(game, Keys.E);
        assertEquals(596_000, game.getClock().getRemainingMillis(Side.WHITE));
        game.getWhite().setY(425);
        press(game, Keys.E);
        assertEquals(601_000, game.getClock().getRemainingMillis(Side.WHITE));
        time.addAndGet(Duration.ofSeconds(2).toNanos());
        game.releaseInput();
        game.update(0);
        assertEquals(598_000, game.getClock().getRemainingMillis(Side.BLACK));
        assertEquals(601_000, game.getClock().getRemainingMillis(Side.WHITE));
    }

    @Test void promotionWaitsForChoiceAndCannotReviveAnExpiredClock() {
        for (boolean expire : new boolean[] {false, true}) {
            AtomicLong time = new AtomicLong();
            ChessGameModel model = new ChessGameModel();
            model.restorePosition("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
            GameSession game = new GameSession(model, MatchSettings.minutes(1, 5), time::get);
            game.getWhite().restore(new Birb.Motion(425, 125, 0, 0));
            press(game, Keys.E);
            game.getWhite().setY(25);
            time.set(Duration.ofSeconds(2).toNanos());
            press(game, Keys.E);
            assertEquals(58_000, game.getClock().getRemainingMillis(Side.WHITE));
            time.set(Duration.ofSeconds(expire ? 60 : 4).toNanos());
            press(game, Keys.Q);
            assertEquals(expire ? 0 : 61_000, game.getClock().getRemainingMillis(Side.WHITE));
            assertEquals(expire ? 0 : 1, game.getHistory().size());
        }
    }

    @Test void setupKeepsUntimedFreeOfIncrementsAndSettingsAreBounded() {
        MatchSetup setup = new MatchSetup(MatchSettings.minutes(5, 5));
        setup.changeTime(-1); // 1 minute.
        setup.changeTime(-1); // Untimed.
        setup.changeIncrement(1);
        assertEquals(MatchSettings.UNTIMED, setup.settings());
        assertThrows(IllegalArgumentException.class, () -> new MatchSettings(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> MatchSettings.minutes(61, 0));
        assertThrows(IllegalArgumentException.class, () -> MatchSettings.minutes(5, 61));
        assertThrows(IllegalArgumentException.class, () -> MatchSettings.minutes(0, 5));
    }
}
