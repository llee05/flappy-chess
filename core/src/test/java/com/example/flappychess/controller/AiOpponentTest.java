package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
import com.example.flappychess.model.SoloSettings.Level;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiOpponentTest {
    @Test void everySquareIsReachableWithBothGravityDirectionsAndEveryFlightLevel() {
        for (Side side : Side.values()) for (Level level : Level.values()) {
            GameSession game = new GameSession(MatchSettings.UNTIMED);
            BirdPilot pilot = new BirdPilot(game, side, level);
            for (Square square : Square.values()) {
                if (square == Square.NONE) continue;
                int steps = 0;
                while (!pilot.flyTo(square) && steps++ < 2400) game.update(1f / 60);
                assertTrue(steps < 2400, side + " " + level + " " + square);
                game.update(1f / 60);
            }
        }
    }

    @Test void searchesAreLegalBoundedAndFindAnImmediateMate() {
        Board board = new Board();
        board.loadFromFen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
        String before = board.getFen();
        for (Level level : Level.values()) {
            ChessSearch search = new ChessSearch(before, level, new Random(1));
            int calls = 0;
            while (!search.isDone() && calls++ < 2000) search.step();
            assertTrue(search.isDone());
            assertTrue(search.nodes() <= 3000);
            assertTrue(board.legalMoves().contains(search.choice()));
            assertEquals(before, board.getFen());
            if (level != Level.EASY) {
                board.doMove(search.choice());
                assertTrue(board.isMated(), search.choice().toString());
                board.undoMove();
            }
        }
    }

    @Test void aiPhysicallyPicksUpAndDeliversWhileBothLayoutsBelongToHuman() {
        for (Level level : Level.values()) {
            LocalMatch match = new LocalMatch(MatchSettings.UNTIMED, new SoloSettings(Side.BLACK, level, level));
            match.keyDown(Keys.E);
            match.keyUp(Keys.E);
            assertNull(match.getSession().getCarrier());
            match.keyDown(Keys.SPACE);
            match.keyDown(Keys.UP);
            match.keyUp(Keys.SPACE);
            match.update(1f / 60);
            assertTrue(match.getSession().getBlack().getVelocityY() > 0);
            match.releaseInput();
            boolean carried = false;
            for (int steps = 0; match.getSession().getHistory().isEmpty() && steps < 5000; steps++) {
                match.update(1f / 60);
                carried |= match.getSession().getCarrier() == Side.WHITE;
            }
            assertTrue(carried, level.toString());
            assertEquals(1, match.getSession().getHistory().size(), level.toString());
            assertEquals(Side.BLACK, match.getSession().getChess().getSideToMove());
        }
    }

    @Test void aiCanDeliverAPromotion() {
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("7k/P7/8/8/8/8/8/7K w - - 0 1");
        LocalMatch match = new LocalMatch(() -> new GameSession(model, MatchSettings.UNTIMED, () -> 0),
                new SoloSettings(Side.BLACK, Level.MEDIUM, Level.HARD));
        for (int i = 0; match.getSession().getHistory().isEmpty() && i < 5000; i++) match.update(1f / 60);
        assertEquals(1, match.getSession().getHistory().size());
        assertTrue(match.getSession().getHistory().get(0).coordinate().startsWith("a7a8"));
        assertFalse(match.getSession().getChess().hasPendingPromotion());
    }

    @Test void bothPilotsCanPlayConsecutiveLegalTurns() {
        GameSession game = new GameSession(MatchSettings.UNTIMED);
        SoloSettings settings = SoloSettings.DEFAULT;
        AiOpponent white = new AiOpponent(game, Side.WHITE, settings);
        AiOpponent black = new AiOpponent(game, Side.BLACK, settings);
        for (int i = 0; i < 120_000 && game.getHistory().size() < 20 && !game.isGameOver(); i++) {
            game.update(1f / 60, () -> { white.step(); black.step(); });
        }
        assertTrue(game.getHistory().size() >= 20 || game.isGameOver(), "Pilots stalled: " + game.getHistory().size());
        assertDoesNotThrow(() -> new GameRecord(game));
    }

    @Test void swappedSoloRoundsKeepHumanIdentityAndGameOverFreezesAi() {
        AtomicLong time = new AtomicLong();
        LocalMatch match = new LocalMatch(() -> new GameSession(new ChessGameModel(), new MatchSettings(1000, 0), time::get),
                SoloSettings.DEFAULT);
        time.set(1_000_000_000L);
        match.update(1f / 60);
        var frozen = match.getSession().snapshot();
        match.update(0.2f);
        assertEquals(frozen, match.getSession().snapshot());
        match.nextRound(true);
        assertEquals(Side.BLACK, match.getSession().getSeries().sideFor(true));
        assertEquals("YOU 0-1 AI", match.getSession().getSeries().score());
        for (int i = 0; i < 3000 && match.getSession().getHistory().isEmpty(); i++) match.update(1f / 60);
        assertEquals(1, match.getSession().getHistory().size());
        assertTrue(new GameRecord(match.getSession()).pgn().contains("[White \"Computer\"]"));
    }

    @Test void flightAndThinkingUseFixedStepsAtDifferentRenderRates() {
        LocalMatch slow = new LocalMatch(MatchSettings.UNTIMED, new SoloSettings(Side.BLACK, Level.MEDIUM, Level.HARD));
        LocalMatch fast = new LocalMatch(MatchSettings.UNTIMED, new SoloSettings(Side.BLACK, Level.MEDIUM, Level.HARD));
        for (int i = 0; i < 300; i++) {
            slow.update(1f / 30);
            for (int j = 0; j < 4; j++) fast.update(1f / 120);
        }
        assertEquals(slow.getSession().getWhite().snapshot(), fast.getSession().getWhite().snapshot());
        assertEquals(slow.getSession().getHistory(), fast.getSession().getHistory());
    }
}
