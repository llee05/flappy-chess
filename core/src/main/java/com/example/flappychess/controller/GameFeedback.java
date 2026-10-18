package com.example.flappychess.controller;

import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Recent, numbered presentation events. Replicas replay each event at most once. */
public final class GameFeedback {
    public static final int LIMIT = 16;
    public enum Kind { FLAP, PICKUP, DROP, CAPTURE, CANCEL, ILLEGAL_DROP, NO_PIECE, CHECK, LOW_TIME }
    public record Event(int sequence, Kind kind, Side side, Square square) { }

    private final List<Event> events = new ArrayList<>();
    private int sequence;

    void emit(Kind kind, Side side, Square square) {
        events.add(new Event(++sequence, kind, side, square));
        if (events.size() > LIMIT) events.remove(0);
    }

    void restore(List<Event> recent) {
        events.clear();
        events.addAll(recent);
        sequence = events.isEmpty() ? 0 : events.get(events.size() - 1).sequence();
    }

    public List<Event> events() { return Collections.unmodifiableList(events); }

    public static String message(Event event) {
        return switch (event.kind()) {
            case FLAP -> "";
            case PICKUP -> "Picked up. Fly to a highlighted square.";
            case DROP -> "Delivered!";
            case CAPTURE -> "Capture!";
            case CANCEL -> "Pickup cancelled.";
            case ILLEGAL_DROP -> "Illegal drop. Aim for a highlighted square.";
            case NO_PIECE -> "Fly over one of your pieces to pick it up.";
            case CHECK -> "Check! Protect your king.";
            case LOW_TIME -> GameSession.sideName(event.side()) + ": 30 seconds left!";
        };
    }
}
