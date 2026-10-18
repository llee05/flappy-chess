package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessGameModel;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static com.example.flappychess.controller.GameFeedback.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class GameFeedbackTest {
    private GameSession session(String fen) {
        ChessGameModel model = new ChessGameModel();
        if (fen != null) model.restorePosition(fen);
        return new GameSession(model, Duration.ofMinutes(5), () -> 0L);
    }
    private void press(GameSession game, int key) { game.keyDown(key); game.keyUp(key); }
    private void over(Birb bird, Square square) {
        bird.restore(new Birb.Motion(square.getFile().ordinal() * 100 + 25,
                (7 - square.getRank().ordinal()) * 100 + 25, 0, 0));
    }
    private List<GameFeedback.Kind> kinds(GameSession game) {
        return game.getFeedback().events().stream().map(GameFeedback.Event::kind).toList();
    }

    @Test void pickupInvalidDropCancelAndDeliveryHaveDistinctFeedbackWithoutChangingRules() {
        GameSession game = session(null);
        String initial = game.snapshot().fen();
        game.keyDown(Keys.E);
        game.keyDown(Keys.E);
        game.keyUp(Keys.E);
        assertEquals(List.of(PICKUP), kinds(game));
        over(game.getWhite(), Square.E5);
        press(game, Keys.E);
        assertEquals(List.of(PICKUP, ILLEGAL_DROP), kinds(game));
        assertEquals(initial, game.snapshot().fen());
        assertEquals(Side.WHITE, game.getCarrier());
        over(game.getWhite(), Square.E2);
        press(game, Keys.E);
        assertEquals(List.of(PICKUP, ILLEGAL_DROP, CANCEL), kinds(game));
        press(game, Keys.E);
        over(game.getWhite(), Square.E4);
        press(game, Keys.E);
        assertEquals(List.of(PICKUP, ILLEGAL_DROP, CANCEL, PICKUP, DROP), kinds(game));
        assertEquals(Square.E4, game.getFeedback().events().get(4).square());
        assertEquals("e4", game.getHistory().get(0).notation());
    }

    @Test void capturesIncludeEnPassantAndWaitForPromotionChoice() {
        for (String fen : new String[] {
                "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1",
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"}) {
            GameSession game = session(fen);
            boolean enPassant = fen.contains("d6");
            over(game.getWhite(), enPassant ? Square.E5 : Square.E4);
            press(game, Keys.E);
            over(game.getWhite(), enPassant ? Square.D6 : Square.D5);
            press(game, Keys.E);
            assertEquals(List.of(PICKUP, CAPTURE), kinds(game));
        }
        GameSession game = session("k4r2/4P3/8/8/8/8/8/4K3 w - - 0 1");
        over(game.getWhite(), Square.E7);
        press(game, Keys.E);
        over(game.getWhite(), Square.F8);
        press(game, Keys.E);
        assertEquals(List.of(PICKUP), kinds(game));
        assertTrue(game.getChess().hasPendingPromotion());
        press(game, Keys.Q);
        assertEquals(List.of(PICKUP, CAPTURE, CHECK), kinds(game));
        assertEquals(Square.F8, game.getFeedback().events().get(1).square());
        assertEquals(Square.A8, game.getFeedback().events().get(2).square());
    }

    @Test void lowTimeWarnsOncePerSideAndStillChargesTheCorrectClock() {
        AtomicLong time = new AtomicLong();
        GameSession game = new GameSession(new ChessGameModel(), Duration.ofMinutes(5), time::get);
        time.set(Duration.ofSeconds(270).toNanos());
        game.update(0);
        game.update(0);
        press(game, Keys.E);
        over(game.getWhite(), Square.E4);
        press(game, Keys.E);
        time.addAndGet(Duration.ofSeconds(270).toNanos());
        game.update(0);
        game.update(0);
        var warnings = game.getFeedback().events().stream().filter(e -> e.kind() == LOW_TIME).toList();
        assertEquals(List.of(Side.WHITE, Side.BLACK), warnings.stream().map(GameFeedback.Event::side).toList());
        assertEquals(30, game.getClock().getRemainingSeconds(Side.WHITE));
        assertEquals(30, game.getClock().getRemainingSeconds(Side.BLACK));
    }

    @Test void repeatedKeyDownDoesNotRepeatFlapFeedbackAndRecentEventsAreBounded() {
        GameSession game = session(null);
        game.keyDown(Keys.SPACE);
        game.keyDown(Keys.SPACE);
        assertEquals(List.of(FLAP), kinds(game));
        game.releaseInput();
        for (int i = 0; i < 40; i++) press(game, Keys.SPACE);
        var events = game.getFeedback().events();
        assertEquals(GameFeedback.LIMIT, events.size());
        assertEquals(41, events.get(events.size() - 1).sequence());
        assertEquals(events.size(), events.stream().map(GameFeedback.Event::sequence).distinct().count());
    }
}
