package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchSeriesTest {
    @Test void resultsCountOnceAndLayoutsFollowPlayersAcrossSwaps() {
        AtomicLong time = new AtomicLong();
        LocalMatch match = new LocalMatch(() -> new GameSession(new ChessGameModel(), new MatchSettings(1000, 0), time::get));
        time.set(1_000_000_000L);
        match.update(0);
        match.update(0);
        assertEquals(2, match.getSession().getSeries().secondHalfPoints());
        match.nextRound(true);
        assertEquals(2, match.getSession().getSeries().round());
        assertFalse(match.getSession().getSeries().firstWhite());
        match.keyDown(Keys.SPACE);
        assertTrue(match.getSession().getBlack().getVelocityY() > 0);
        assertEquals(0, match.getSession().getWhite().getVelocityY());
        match.keyDown(Keys.D);
        match.releaseInput();
        match.update(0.2f);
        assertEquals(0, match.getSession().getBlack().getVelocityX());
        time.set(2_000_000_000L);
        match.update(0);
        assertEquals(2, match.getSession().getSeries().firstHalfPoints());
        match.nextRound(false);
        assertFalse(match.getSession().getSeries().firstWhite());
        assertEquals("P1 1-1 P2", match.getSession().getSeries().score());
        assertTrue(new GameRecord(match.getSession()).pgn().contains("[White \"Player 2\"]"));
    }

    @Test void drawsGiveEachPlayerHalfAPoint() {
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
        GameSession game = new GameSession(model, MatchSettings.UNTIMED, () -> 0);
        game.getWhite().setY(125);
        game.command(Side.WHITE, GameAction.CARRY, true);
        game.command(Side.WHITE, GameAction.CARRY, false);
        game.getWhite().setY(25);
        game.command(Side.WHITE, GameAction.CARRY, true);
        game.command(Side.WHITE, GameAction.KNIGHT, true);
        assertEquals("Draw", game.getResult());
        MatchSeries series = new MatchSeries(MatchSeries.Players.LOCAL);
        series.observe(game);
        series.observe(game);
        assertEquals("P1 0.5-0.5 P2", series.state().score());
    }
}
