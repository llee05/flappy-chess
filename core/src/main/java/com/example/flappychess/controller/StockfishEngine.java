package com.example.flappychess.controller;

import com.example.flappychess.model.SoloSettings.ChessLevel;
import java.util.Objects;
import java.util.function.LongSupplier;

/** One-at-a-time asynchronous UCI conversation with a separately running Stockfish process. */
public final class StockfishEngine implements ChessEngine {
    private static final long MINIMUM_TIMEOUT_MILLIS = 5_000;
    private final UciProcess process;
    private final LongSupplier clock;
    private Pending pending;
    private boolean uciReady, unavailable, closed;
    private String failure = "Stockfish unavailable";

    public StockfishEngine(UciProcess process) {
        this(process, System::currentTimeMillis);
    }

    StockfishEngine(UciProcess process, LongSupplier clock) {
        this.process = Objects.requireNonNull(process);
        this.clock = Objects.requireNonNull(clock);
        process.listen(this::receive, this::fail);
        process.send("uci");
    }

    @Override public Request choose(String fen, ChessLevel level) {
        Objects.requireNonNull(fen);
        Objects.requireNonNull(level);
        cancel();
        Pending next = new Pending(fen, level, clock.getAsLong() + Math.max(MINIMUM_TIMEOUT_MILLIS,
                level.moveTimeMillis() * 4L + 2_000));
        if (unavailable || closed) next.fail(failure);
        else {
            pending = next;
            if (uciReady) configure(next);
        }
        return next;
    }

    @Override public void cancel() {
        if (pending != null && pending.state == State.THINKING) {
            process.send("stop");
            pending.fail("cancelled");
        }
        pending = null;
    }

    private void configure(Pending request) {
        if (request.configured || request.state != State.THINKING) return;
        request.configured = true;
        process.send("setoption name Skill Level value " + request.level.skill());
        process.send("setoption name UCI_LimitStrength value " + (request.level.elo() == 0 ? "false" : "true"));
        if (request.level.elo() != 0) process.send("setoption name UCI_Elo value " + request.level.elo());
        process.send("ucinewgame");
        process.send("isready");
    }

    private void receive(String output) {
        if (output == null) return;
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.equals("uciok")) {
                uciReady = true;
                if (pending != null) configure(pending);
            } else if (line.equals("readyok") && pending != null && pending.configured
                    && !pending.searching && pending.state == State.THINKING) {
                pending.searching = true;
                process.send("position fen " + pending.fen);
                process.send("go movetime " + pending.level.moveTimeMillis());
            } else if (line.startsWith("bestmove ") && pending != null && pending.searching
                    && pending.state == State.THINKING) {
                String[] fields = line.split("\\s+");
                if (fields.length < 2 || fields[1].equals("(none)")) pending.fail("Stockfish returned no move");
                else pending.complete(fields[1]);
            }
        }
    }

    private void fail(String message) {
        unavailable = true;
        failure = message == null || message.isBlank() ? "Stockfish failed" : message;
        if (pending != null) pending.fail(failure);
    }

    private void checkTimeout(Pending request) {
        if (request.state == State.THINKING && clock.getAsLong() >= request.deadline) {
            process.send("stop");
            fail("Stockfish timed out");
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (pending != null && pending.state == State.THINKING) pending.fail("Stockfish closed");
        process.close();
    }

    private final class Pending implements Request {
        private final String fen;
        private final ChessLevel level;
        private final long deadline;
        private State state = State.THINKING;
        private String move, reason = "";
        private boolean configured, searching;

        private Pending(String fen, ChessLevel level, long deadline) {
            this.fen = fen;
            this.level = level;
            this.deadline = deadline;
        }

        private void complete(String coordinate) { move = coordinate; state = State.READY; }
        private void fail(String message) { reason = message; state = State.FAILED; }
        @Override public State state() { checkTimeout(this); return state; }
        @Override public String move() { return move; }
        @Override public String failure() { return reason; }
    }
}
