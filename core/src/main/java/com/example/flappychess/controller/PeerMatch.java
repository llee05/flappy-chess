package com.example.flappychess.controller;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.JsonValue;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import static com.example.flappychess.controller.PeerProtocol.*;

/** One trusted host, one guest; transport and OpenGL remain outside the match logic. */
public final class PeerMatch extends InputAdapter {
    // Normal background timers can be throttled to one second. Longer gaps cannot
    // safely replay held input, so announce an interruption before resuming flight.
    private static final long MAX_SIMULATION_GAP = 3_000_000_000L;
    private static final long RECOVERY_NOTICE = 2_000_000_000L;
    private final boolean host;
    private final MatchSeries series = new MatchSeries(MatchSeries.Players.ONLINE);
    private final Consumer<String> send;
    private final LongSupplier time;
    private final Supplier<GameSession> sessions;
    private final Set<Integer> keys = new HashSet<>();
    private final Set<GameAction> held = new HashSet<>();
    private GameSession session;
    private SessionSnapshot latest;
    private boolean opened, remoteReady, started, disconnected, ownRematch, remoteRematch;
    private boolean ownSwap, remoteSwap;
    private int epoch, inputSequence, acknowledgedInput, stateSequence, receivedState;
    private long lastHeard, lastPing, lastSnapshot;
    private long simulatedAt, lastStateAt, interruptedUntil;
    private int simulationGeneration;
    private boolean remoteInterrupted, staleHost;
    private String error;

    public PeerMatch(boolean host, Consumer<String> send, LongSupplier time) {
        this(host, send, time, MatchSettings.DEFAULT);
    }

    public PeerMatch(boolean host, Consumer<String> send, LongSupplier time, MatchSettings settings) {
        this(host, send, time, () -> new GameSession(new ChessGameModel(), settings, time));
    }

    PeerMatch(boolean host, Consumer<String> send, LongSupplier time, Supplier<GameSession> sessions) {
        this.host = host;
        this.send = send;
        this.time = time;
        this.sessions = sessions;
        session = sessions.get();
        series.observe(session);
    }

    public void open() {
        if (opened) return;
        opened = true;
        lastHeard = lastPing = time.getAsLong();
        send.accept(encode(packet("ready")));
        if (host && remoteReady) startMatch();
    }

    public void receive(String message) {
        if (disconnected) return;
        try {
            JsonValue value = parse(message);
            lastHeard = time.getAsLong();
            switch (string(value, "type", 16)) {
                case "ready" -> {
                    remoteReady = true;
                    if (host && opened && !started) startMatch();
                }
                case "ping" -> send.accept(encode(packet("pong")));
                case "pong" -> { }
                case "state" -> { if (!host) receiveState(value); }
                case "input", "release" -> { if (host && started) receiveInput(value); }
                case "rematch" -> {
                    if (started && number(value, "epoch", 1, Integer.MAX_VALUE) == epoch && session.isGameOver()) {
                        remoteRematch = true;
                        remoteSwap = bool(value, "swap");
                        if (host && ownRematch && ownSwap == remoteSwap) startMatch();
                        else if (host) sendState();
                    }
                }
                default -> throw new IllegalArgumentException("Unknown message");
            }
        } catch (RuntimeException invalid) {
            disconnect("Incompatible match data. Both players should reload the game.");
        }
    }

    private void startMatch() {
        clearLocalInput();
        if (started) series.nextRound(session, ownSwap);
        session = sessions.get();
        series.observe(session);
        started = true;
        ownRematch = remoteRematch = false;
        ownSwap = remoteSwap = false;
        epoch++;
        simulatedAt = time.getAsLong();
        interruptedUntil = 0;
        simulationGeneration = 0;
        inputSequence = acknowledgedInput = stateSequence = receivedState = 0;
        sendState();
    }

    private void receiveInput(JsonValue value) {
        advanceHost();
        if (number(value, "epoch", 1, Integer.MAX_VALUE) != epoch) return;
        int sequence = (int) number(value, "sequence", 1, Integer.MAX_VALUE);
        if (sequence <= acknowledgedInput) return;
        int generation = (int) number(value, "simulation", 0, Integer.MAX_VALUE);
        if (generation != simulationGeneration || isInterrupted()) return;
        Side remoteSide = getOwnSide().flip();
        if (string(value, "type", 16).equals("release")) session.releaseInput(remoteSide);
        else {
            GameAction action = GameAction.valueOf(string(value, "action", 12));
            // A guest never supplies its side, coordinates, board, clock, or outcome.
            session.command(remoteSide, action, bool(value, "down"));
        }
        acknowledgedInput = sequence;
        sendState();
    }

    private void receiveState(JsonValue value) {
        int nextEpoch = (int) number(value, "epoch", 1, Integer.MAX_VALUE);
        int sequence = (int) number(value, "sequence", 1, Integer.MAX_VALUE);
        if (nextEpoch < epoch || nextEpoch == epoch && sequence <= receivedState) return;
        if (epoch > 0 && nextEpoch > epoch && !(session.isGameOver() && ownRematch)) {
            throw new IllegalArgumentException("Unrequested rematch");
        }
        SessionSnapshot state = snapshot(value.require("state"));
        if (state.series().players() != MatchSeries.Players.ONLINE || state.series().round() != nextEpoch) {
            throw new IllegalArgumentException("Invalid series round");
        }
        int generation = (int) number(value, "simulation", 0, Integer.MAX_VALUE);
        boolean interrupted = bool(value, "interrupted");
        boolean resetInput = generation != simulationGeneration || interrupted || staleHost;
        int ack = (int) number(value, "ack", 0, Integer.MAX_VALUE);
        int previousFeedback = lastFeedbackSequence();
        boolean bumped = state.feedback().stream()
                .anyMatch(event -> event.sequence() > previousFeedback
                        && (event.kind() == GameFeedback.Kind.BUMP || event.kind() == GameFeedback.Kind.BUMP_DROP));
        boolean newMatch = epoch != nextEpoch;
        if (newMatch) {
            if (epoch > 0 && state.series().firstWhite() != (series.state().firstWhite() != ownSwap)) {
                throw new IllegalArgumentException("Unagreed color swap");
            }
            clearLocalInput();
            inputSequence = 0;
            ownRematch = remoteRematch = false;
            session = sessions.get();
            session.getWhite().restore(state.white());
            session.getBlack().restore(state.black());
        } else if (ack >= inputSequence || state.result() != null || bumped || resetInput) {
            Birb own = getOwnSide() == Side.WHITE ? session.getWhite() : session.getBlack();
            Birb.Motion motion = getOwnSide() == Side.WHITE ? state.white() : state.black();
            double distance = Math.hypot(own.getX() - motion.x(), own.getY() - motion.y());
            own.reconcile(motion, distance > 100 || state.result() != null || bumped || resetInput ? 1 : 0.25);
            if (bumped) {
                Birb other = getOwnSide() == Side.WHITE ? session.getBlack() : session.getWhite();
                other.reconcile(getOwnSide() == Side.WHITE ? state.black() : state.white(), 1);
            }
        }
        if (resetInput) interruptLocalInput();
        simulationGeneration = generation;
        remoteInterrupted = interrupted;
        staleHost = false;
        lastStateAt = time.getAsLong();
        epoch = nextEpoch;
        receivedState = sequence;
        latest = state;
        series.restore(state.series());
        session.restoreReplica(state);
        remoteRematch = bool(value, "hostRematch");
        remoteSwap = bool(value, "hostSwap");
        started = true;
        if (session.isGameOver()) clearLocalInput();
    }

    public void update(float delta) {
        if (!started || disconnected) return;
        pulse();
        if (!host && !disconnected) {
            session.updateReplica(isInterrupted() ? 0 : delta, getOwnSide());
            if (!isInterrupted() && latest != null) {
                Birb other = getOwnSide() == Side.WHITE ? session.getBlack() : session.getWhite();
                other.reconcile(getOwnSide() == Side.WHITE ? latest.black() : latest.white(),
                        1 - Math.exp(-16 * Math.max(0, delta)));
            }
        }
    }

    /** The sole authoritative timeline, shared by render, timer and input callbacks. */
    private void advanceHost() {
        if (!host || !started || disconnected) return;
        long now = time.getAsLong();
        long elapsed = Math.max(0, now - simulatedAt);
        simulatedAt = Math.max(simulatedAt, now);
        int before = lastFeedbackSequence();
        boolean wasInterrupted = isInterrupted();
        if (elapsed > MAX_SIMULATION_GAP && !session.isGameOver()) {
            simulationGeneration++;
            interruptedUntil = now + RECOVERY_NOTICE;
            interruptLocalInput();
            session.releaseInput();
        }
        if (interruptedUntil != 0) {
            // Clocks always account for the full real interval, including suspension.
            session.update(0);
            if (now >= interruptedUntil) interruptedUntil = 0;
        } else {
            // Keep GameSession's per-update safety cap, but consume every normal
            // timer interval (including one-second browser throttling) exactly once.
            session.update(0);
            while (elapsed > 0 && !session.isGameOver()) {
                long slice = Math.min(elapsed, 250_000_000L);
                session.update(slice / 1_000_000_000f);
                elapsed -= slice;
            }
        }
        if (session.isGameOver()) interruptedUntil = 0;
        if (wasInterrupted != isInterrupted() || lastFeedbackSequence() != before) sendState();
    }

    private int lastFeedbackSequence() {
        var events = session.getFeedback().events();
        return events.isEmpty() ? 0 : events.get(events.size() - 1).sequence();
    }

    /** Also called by a browser timer; rendering is not required for host simulation. */
    public void pulse() {
        if (!opened || disconnected) return;
        long now = time.getAsLong();
        advanceHost();
        if (!host && started && !staleHost && now - lastStateAt > MAX_SIMULATION_GAP) {
            staleHost = true;
            interruptLocalInput();
            send.accept(encode(sequenced("release")));
        }
        if (now - lastHeard > 20_000_000_000L) {
            disconnect("Connection lost. Return to the lobby to start another match.");
            return;
        }
        if (now - lastPing >= 2_000_000_000L) {
            send.accept(encode(packet("ping")));
            lastPing = now;
        }
        if (host && started) {
            if (now - lastSnapshot >= 50_000_000L) sendState();
        }
    }

    private void sendState() {
        series.observe(session);
        JsonValue value = packet("state");
        put(value, "epoch", epoch);
        put(value, "sequence", ++stateSequence);
        put(value, "ack", acknowledgedInput);
        put(value, "simulation", simulationGeneration);
        put(value, "interrupted", isInterrupted());
        put(value, "hostRematch", ownRematch);
        put(value, "guestRematch", remoteRematch);
        put(value, "hostSwap", ownSwap);
        put(value, "guestSwap", remoteSwap);
        value.addChild("state", snapshot(session.snapshot()));
        send.accept(encode(value));
        lastSnapshot = time.getAsLong();
    }

    @Override public boolean keyDown(int key) {
        pulse();
        if (isInterrupted()) {
            keys.add(key);
            return true;
        }
        if (key == com.badlogic.gdx.Input.Keys.X && started && !disconnected && session.isGameOver()) {
            if (keys.add(key)) requestRematch(true);
            return true;
        }
        GameAction action = GameAction.forKey(key);
        if (action == null || !started || disconnected) return false;
        if (!keys.add(key)) return true;
        if (session.isGameOver()) {
            if (action == GameAction.ROOK) requestRematch();
            return true;
        }
        if (!held.add(action)) return true;
        input(action, true);
        return true;
    }

    @Override public boolean keyUp(int key) {
        advanceHost();
        keys.remove(key);
        GameAction action = GameAction.forKey(key);
        if (action == null) return false;
        for (int other : keys) if (GameAction.forKey(other) == action) return true;
        if (held.remove(action) && started && !disconnected) input(action, false);
        return true;
    }

    private void input(GameAction action, boolean down) {
        if (host) {
            session.command(getOwnSide(), action, down);
            sendState();
        } else {
            session.predict(getOwnSide(), action, down);
            JsonValue value = sequenced("input");
            put(value, "action", action.name());
            put(value, "down", down);
            send.accept(encode(value));
        }
    }

    private JsonValue sequenced(String type) {
        JsonValue value = packet(type);
        put(value, "epoch", epoch);
        put(value, "sequence", ++inputSequence);
        put(value, "simulation", simulationGeneration);
        return value;
    }

    public void releaseInput() {
        advanceHost();
        clearLocalInput();
        if (!started || disconnected) return;
        if (host) sendState();
        else send.accept(encode(sequenced("release")));
    }

    private void clearLocalInput() {
        keys.clear();
        interruptLocalInput();
    }

    private void interruptLocalInput() {
        // Retain pressed key identities until key-up: browser auto-repeat after
        // recovery must not revive a held steering key or repeat a flap.
        held.clear();
        session.releaseInput(getOwnSide());
    }

    public void requestRematch() { requestRematch(false); }

    public void requestRematch(boolean swap) {
        if (!started || disconnected || !session.isGameOver() || ownRematch && ownSwap == swap) return;
        ownRematch = true;
        ownSwap = swap;
        if (host && remoteRematch && ownSwap == remoteSwap) startMatch();
        else {
            JsonValue value = packet("rematch");
            put(value, "epoch", epoch);
            put(value, "swap", swap);
            send.accept(encode(value));
            if (host) sendState();
        }
    }

    public void disconnect(String reason) {
        disconnected = true;
        error = reason;
        session.releaseInput();
        keys.clear();
        held.clear();
    }

    public String status() {
        if (disconnected) return error;
        if (isInterrupted()) return "Host interrupted. Flight paused."
                + (session.isTimed() ? " Clocks continue." : "") + " Waiting to resume...";
        if (!started) return "Waiting for the other player...";
        if (ownRematch && remoteRematch && ownSwap != remoteSwap) return "Rematch choices differ. R keeps colors; X swaps. Choose the same option.";
        if (ownRematch) return (ownSwap ? "Swap requested." : "Rematch requested.") + " Waiting for the other player...";
        if (remoteRematch) return "Your opponent wants a rematch" + (remoteSwap ? " with colors swapped. Press X to accept." : ". Press R to accept.");
        return "You are " + (host ? "P1 / creator / " : "P2 / guest / ") + GameSession.sideName(getOwnSide())
                + ". A/D or arrows, Space/Up, E/Enter.";
    }

    public GameSession getSession() { return session; }
    public Side getOwnSide() { return series.state().sideFor(host); }
    public boolean isInterrupted() { return interruptedUntil != 0 || remoteInterrupted || staleHost; }
    public boolean isStarted() { return started; }
    public boolean isDisconnected() { return disconnected; }
}
