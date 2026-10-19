package com.example.flappychess.controller;

import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.util.List;

/** Choices shared by the desktop setup screen and the browser lobby. */
public final class MatchSetup {
    public static final List<Integer> MINUTES = List.of(0, 1, 5, 10, 15, 30, 60);
    public static final List<Integer> INCREMENTS = List.of(0, 2, 5, 10, 15, 30, 60);
    private int timeIndex, incrementIndex;
    private boolean computer;
    private SoloSettings solo = SoloSettings.DEFAULT;

    public MatchSetup(MatchSettings settings) {
        timeIndex = Math.max(0, MINUTES.indexOf((int) (settings.initialMillis() / 60_000)));
        incrementIndex = Math.max(0, INCREMENTS.indexOf((int) (settings.incrementMillis() / 1000)));
    }

    public MatchSetup(MatchSettings settings, SoloSettings solo) {
        this(settings);
        computer = solo != null;
        if (solo != null) this.solo = solo;
    }
    public void toggleOpponent() { computer = !computer; }
    public void changeChess() { solo = new SoloSettings(solo.humanSide(), solo.chess().next(), solo.flight()); }
    public void changeFlight() { solo = new SoloSettings(solo.humanSide(), solo.chess(), solo.flight().next()); }
    public void changeColor() { solo = new SoloSettings(solo.humanSide().flip(), solo.chess(), solo.flight()); }
    public SoloSettings soloSettings() { return computer ? solo : null; }
    public String opponentLabel() { return computer ? "COMPUTER" : "LOCAL FRIEND"; }
    public String chessLabel() { return solo.chess().name(); }
    public String flightLabel() { return solo.flight().name(); }
    public String colorLabel() { return computer ? solo.humanSide().name() : "SOLO COLOR"; }

    public void changeTime(int direction) {
        timeIndex = Math.floorMod(timeIndex + direction, MINUTES.size());
        if (MINUTES.get(timeIndex) == 0) incrementIndex = 0;
    }

    public void changeIncrement(int direction) {
        if (MINUTES.get(timeIndex) != 0) incrementIndex = Math.floorMod(incrementIndex + direction, INCREMENTS.size());
    }

    public MatchSettings settings() { return MatchSettings.minutes(MINUTES.get(timeIndex), INCREMENTS.get(incrementIndex)); }
    public String timeLabel() { return MINUTES.get(timeIndex) == 0 ? "UNTIMED" : MINUTES.get(timeIndex) + " MINUTES PER SIDE"; }
    public String incrementLabel() { return INCREMENTS.get(incrementIndex) + " SECONDS PER MOVE"; }
}
