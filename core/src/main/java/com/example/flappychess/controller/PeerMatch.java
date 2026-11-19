package com.example.flappychess.controller;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.JsonValue;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Side;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import static com.example.flappychess.controller.PeerProtocol.*;

/** One trusted host, one guest; transport and OpenGL remain outside the match logic. */
public final class PeerMatch extends InputAdapter {
    public enum ConnectionQuality { GOOD, FAIR, POOR }

    private record RecordedInput(
            int sequence,
            int generation,
            long guestStep,
            GameAction action,
            boolean down,
            BirbController.InputState stateAfter) { }

    private record ReplayResult(Birb.Motion motion, int stunSteps) { }

    // Normal background timers can be throttled to one second. Longer gaps cannot
    // safely replay held input, so announce an interruption before resuming flight.
    private static final long MAX_SIMULATION_GAP = 3_000_000_000L;
    private static final long RECOVERY_NOTICE = 2_000_000_000L;
    private static final long MAX_WIRE_TIME = 1_000_000_000_000_000L;
    private static final int MAX_INPUT_HISTORY = 512;
    private static final int MAX_REPLAY_STEPS = 180;
    private static final double MAX_IMMEDIATE_CORRECTION = 12.0;

    private final boolean host;
    private final MatchSeries series = new MatchSeries(MatchSeries.Players.ONLINE);
    private final Consumer<String> send;
    private final LongSupplier time;
    private final Supplier<GameSession> sessions;
    private final Set<Integer> keys = new HashSet<>();
    private final Set<GameAction> held = new HashSet<>();
    private final ArrayDeque<RecordedInput> pendingInputs = new ArrayDeque<>();
    private GameSession session;
    private SessionSnapshot latest;
    private boolean opened, remoteReady, started, disconnected, ownRematch, remoteRematch;
    private boolean ownSwap, remoteSwap;
    private int epoch, inputSequence, acknowledgedInput, stateSequence, receivedState;
    private long lastHeard, lastPing, lastSnapshot;
    private long simulatedAt, lastStateAt, interruptedUntil;
    private int simulationGeneration;
    private boolean remoteInterrupted, staleHost;
    private long timeOrigin;
    private boolean timeOriginInitialized;
    private long lastRemoteSentAt = -1;
    private long lastRemoteReceivedAt;
    private long lastEchoedSentAt = -1;
    private boolean hasTimingSample;
    private long rttNanos;
    private long clockOffsetNanos;
    private long lastStateAgeNanos;
    private long lastAppliedInputStep;
    private int lastAckedSequence;
    private long lastAckedGuestStep;
    private BirbController.InputState lastAckedInputState = new BirbController.InputState(false, false, false);
    private SessionSnapshot predictionBaseState;
    private long predictionBaseStep, lastMappedAuthStep;
    private BirbController.InputState predictionBaseInputState;
    private int pendingReconcileAck;
    private long pendingReconcileAckSteps;
    private int pendingReconcileGeneration;
    private boolean hasPendingReconcile;
    private Birb.Motion reconcileTarget;
    private int reconcileTargetStun;
    private double reconcileErrorX, reconcileErrorY;
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
        send.accept(encode(timedPacket("ready")));
        if (host && remoteReady) startMatch();
    }

    public void receive(String message) {
        if (disconnected) return;
        try {
            JsonValue value = parse(message);
            lastHeard = time.getAsLong();
            switch (string(value, "type", 16)) {
                case "ready" -> {
                    observePacketTiming(value);
                    remoteReady = true;
                    if (host && opened && !started) startMatch();
                }
                case "ping" -> {
                    observePacketTiming(value);
                    send.accept(encode(timedPacket("pong")));
                }
                case "pong" -> observePacketTiming(value);
                case "state" -> { if (!host) receiveState(value); }
                case "input", "release" -> { if (host && started) receiveInput(value); }
                case "rematch" -> {
                    if (started && number(value, "epoch", 1, Integer.MAX_VALUE) == epoch && session.isGameOver()) {
                        observePacketTiming(value);
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
        lastAppliedInputStep = session.getSimulationSteps();
        resetPredictionHistory(0, 0);
        sendState();
    }

    private void receiveInput(JsonValue value) {
        advanceHost();
        if (number(value, "epoch", 1, Integer.MAX_VALUE) != epoch) return;
        int sequence = (int) number(value, "sequence", 1, Integer.MAX_VALUE);
        if (sequence <= acknowledgedInput) return;
        int generation = (int) number(value, "simulation", 0, Integer.MAX_VALUE);
        if (generation != simulationGeneration || isInterrupted()) return;
        observePacketTiming(value);
        Side remoteSide = getOwnSide().flip();
        if (string(value, "type", 16).equals("release")) session.releaseInput(remoteSide);
        else {
            GameAction action = GameAction.valueOf(string(value, "action", 12));
            // A guest never supplies its side, coordinates, board, clock, or outcome.
            session.command(remoteSide, action, bool(value, "down"));
        }
        acknowledgedInput = sequence;
        lastAppliedInputStep = session.getSimulationSteps();
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
        long stateAgeNanos = observePacketTiming(value);
        lastStateAgeNanos = stateAgeNanos;
        int generation = (int) number(value, "simulation", 0, Integer.MAX_VALUE);
        boolean interrupted = bool(value, "interrupted");
        boolean resetInput = generation != simulationGeneration || interrupted || staleHost;
        int ack = (int) number(value, "ack", 0, Integer.MAX_VALUE);
        long ackSteps = number(value, "ackSteps", 0, Integer.MAX_VALUE);
        int previousFeedback = lastFeedbackSequence();
        boolean bumped = state.feedback().stream()
                .anyMatch(event -> event.sequence() > previousFeedback
                        && (event.kind() == GameFeedback.Kind.BUMP || event.kind() == GameFeedback.Kind.BUMP_DROP));
        boolean newMatch = epoch != nextEpoch;
        if (newMatch && epoch > 0 && state.series().firstWhite() != (series.state().firstWhite() != ownSwap)) {
            throw new IllegalArgumentException("Unagreed color swap");
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
        if (newMatch) {
            clearLocalInput();
            inputSequence = 0;
            ownRematch = remoteRematch = false;
            session = sessions.get();
            session.getWhite().restore(state.white());
            session.getBlack().restore(state.black());
            session.restoreReplica(state, stateAgeNanos);
            advanceReplicaByAge(stateAgeNanos);
            latest = session.snapshot();
            lastMappedAuthStep = session.getSimulationSteps();
            resetPredictionHistory(0, 0);
        } else if (state.result() != null || bumped || resetInput) {
            session.restoreReplica(state, stateAgeNanos);
            Birb own = getOwnSide() == Side.WHITE ? session.getWhite() : session.getBlack();
            Birb.Motion motion = getOwnSide() == Side.WHITE ? state.white() : state.black();
            own.reconcile(motion, 1);
            own.stun(getOwnSide() == Side.WHITE ? state.whiteStunSteps() : state.blackStunSteps());
            if (state.result() != null || bumped) {
                Birb other = getOwnSide() == Side.WHITE ? session.getBlack() : session.getWhite();
                other.reconcile(getOwnSide() == Side.WHITE ? state.black() : state.white(), 1);
                other.stun(getOwnSide() == Side.WHITE ? state.blackStunSteps() : state.whiteStunSteps());
            }
            if (bumped && state.result() == null && !resetInput) {
                rebasePredictionHistory(state, ack, ackSteps, generation);
            } else {
                resetPredictionHistory(ack, session.getSimulationSteps() - ackSteps);
            }
        } else {
            session.restoreReplica(state, stateAgeNanos);
            scheduleOrReconcileOwnBird(state, ack, ackSteps, generation);
        }
        remoteRematch = bool(value, "hostRematch");
        remoteSwap = bool(value, "hostSwap");
        started = true;
        if (session.isGameOver()) {
            clearLocalInput();
            resetPredictionHistory(ack, session.getSimulationSteps());
        }
    }

    public void update(float delta) {
        if (!started || disconnected) return;
        pulse();
        if (!host && !disconnected) {
            session.updateReplica(isInterrupted() ? 0 : delta, getOwnSide());
            if (!isInterrupted() && latest != null) {
                if (hasPendingReconcile) {
                    hasPendingReconcile = false;
                    reconcileOwnBird(latest, pendingReconcileAck, pendingReconcileAckSteps, pendingReconcileGeneration);
                }
                if (reconcileTarget != null) {
                    applyReconcileTarget();
                } else {
                    applyReconcileSmoothing(delta);
                }
                Birb other = getOwnSide() == Side.WHITE ? session.getBlack() : session.getWhite();
                other.reconcile(getOwnSide() == Side.WHITE ? latest.black() : latest.white(),
                        1 - Math.exp(-16 * Math.max(0, delta)));
            }
        }
    }

    private void scheduleOrReconcileOwnBird(SessionSnapshot state, int ack, long ackSteps, int generation) {
        pruneAcknowledgedInputs(ack, generation);
        long currentStep = session.getSimulationSteps();
        long authStep = computeAuthStep(ack, ackSteps);
        lastMappedAuthStep = Math.min(authStep, currentStep);
        if (authStep == currentStep + 1 && lastStateAgeNanos == 0) {
            pendingReconcileAck = ack;
            pendingReconcileAckSteps = ackSteps;
            pendingReconcileGeneration = generation;
            hasPendingReconcile = true;
            return;
        }
        hasPendingReconcile = false;
        reconcileOwnBird(state, ack, ackSteps, generation);
    }

    private void pruneAcknowledgedInputs(int ack, int generation) {
        for (RecordedInput acked : pendingInputs) {
            if (acked.sequence() > lastAckedSequence && acked.sequence() <= ack
                    && acked.generation() == generation) {
                lastAckedSequence = acked.sequence();
                lastAckedGuestStep = acked.guestStep();
                lastAckedInputState = acked.stateAfter();
            }
        }
        if (!pendingInputs.isEmpty() && pendingInputs.peekLast().sequence() <= ack) {
            pendingInputs.clear();
            predictionBaseState = null;
            predictionBaseInputState = null;
        }
    }

    private long computeAuthStep(int ack, long ackSteps) {
        long hostStep = (ack == 0 ? 0L : lastAckedGuestStep) + ackSteps;
        long stateAgeSteps = Math.round(lastStateAgeNanos * 60.0 / 1_000_000_000.0);
        return hostStep + (ack == 0 ? 0 : stateAgeSteps);
    }

    private void advanceReplicaByAge(long stateAgeNanos) {
        long remaining = stateAgeNanos;
        while (remaining > 0) {
            long slice = Math.min(remaining, 250_000_000L);
            session.updateReplica(slice / 1_000_000_000f, getOwnSide());
            remaining -= slice;
        }
    }

    private void reconcileOwnBird(SessionSnapshot state, int ack, long ackSteps, int generation) {
        pruneAcknowledgedInputs(ack, generation);
        long currentStep = session.getSimulationSteps();
        long authStep = computeAuthStep(ack, ackSteps);
        Side ownSide = getOwnSide();
        ReplayResult replayed = replayOwnBird(state, ownSide, authStep, currentStep, generation);
        reconcileTarget = replayed.motion();
        reconcileTargetStun = replayed.stunSteps();
    }

    private void applyReconcileTarget() {
        Birb own = getOwnSide() == Side.WHITE ? session.getWhite() : session.getBlack();
        Birb.Motion target = reconcileTarget;
        double dx = target.x() - own.getX();
        double dy = target.y() - own.getY();
        double distance = Math.hypot(dx, dy);
        double blend = distance <= 1e-6 ? 1.0 : Math.min(0.25, MAX_IMMEDIATE_CORRECTION / distance);
        own.reconcile(target, blend);
        own.stun(reconcileTargetStun);
        reconcileErrorX = target.x() - own.getX();
        reconcileErrorY = target.y() - own.getY();
        reconcileTarget = null;
    }

    private ReplayResult replayOwnBird(SessionSnapshot state, Side ownSide, long authStep, long currentStep, int generation) {
        SessionSnapshot replayState = state;
        BirbController.InputState replayInputState = lastAckedInputState;
        long startStep = Math.min(authStep, currentStep);
        RecordedInput firstUnacknowledged = pendingInputs.stream()
                .filter(input -> input.generation() == generation && input.sequence() > lastAckedSequence)
                .findFirst().orElse(null);
        boolean usingPredictionBase = predictionBaseState != null && firstUnacknowledged != null;
        if (usingPredictionBase) {
            replayState = predictionBaseState;
            replayInputState = predictionBaseInputState;
            startStep = Math.min(predictionBaseStep, currentStep);
        }
        if (usingPredictionBase && currentStep - startStep > MAX_REPLAY_STEPS) {
            Birb own = ownSide == Side.WHITE ? session.getWhite() : session.getBlack();
            return new ReplayResult(own.snapshot(), own.getStunSteps());
        }
        Birb.Motion baseMotion = ownSide == Side.WHITE ? replayState.white() : replayState.black();
        int baseStun = ownSide == Side.WHITE ? replayState.whiteStunSteps() : replayState.blackStunSteps();
        int replayHitStop = replayState.hitStopSteps();
        Birb scratchBird = new Birb(baseMotion.x(), baseMotion.y(), 50, 50, ownSide == Side.WHITE ? 1 : -1);
        scratchBird.restore(baseMotion);
        scratchBird.stun(baseStun);
        BirbController scratchControls = ownSide == Side.WHITE
                ? new BirbController(scratchBird, 800, 800)
                : new BirbController(scratchBird, 800, 800,
                        com.badlogic.gdx.Input.Keys.LEFT, com.badlogic.gdx.Input.Keys.RIGHT, com.badlogic.gdx.Input.Keys.UP);
        scratchControls.restoreInput(replayInputState);

        for (RecordedInput input : pendingInputs) {
            if (input.generation() == generation && input.guestStep() <= startStep) {
                applyReplayInput(scratchControls, ownSide, replayState, replayHitStop, input);
            }
        }
        for (long step = startStep; step < currentStep; step++) {
            if (step > startStep) {
                for (RecordedInput input : pendingInputs) {
                    if (input.generation() == generation && input.guestStep() == step) {
                        applyReplayInput(scratchControls, ownSide, replayState, replayHitStop, input);
                    }
                }
            }
            if (replayHitStop > 0) {
                replayHitStop--;
            } else {
                scratchControls.update();
            }
        }
        if (currentStep > startStep) {
            for (RecordedInput input : pendingInputs) {
                if (input.generation() == generation && input.guestStep() >= currentStep) {
                    applyReplayInput(scratchControls, ownSide, replayState, replayHitStop, input);
                }
            }
        }
        return new ReplayResult(scratchBird.snapshot(), scratchBird.getStunSteps());
    }

    private static void applyReplayInput(BirbController controls, Side ownSide, SessionSnapshot state,
            int hitStopSteps, RecordedInput input) {
        if (input.action() == null) {
            controls.restoreInput(new BirbController.InputState(false, false, false));
            return;
        }
        if (!input.action().isMovement()) return;
        if (input.down() && (state.result() != null || state.promotion() != null)) return;
        if (input.down() && hitStopSteps > 0 && input.action() == GameAction.FLAP) return;
        int key = input.action().keyFor(ownSide);
        if (input.down()) controls.handleKeyPressed(key);
        else controls.handleKeyReleased(key);
    }

    private void applyReconcileSmoothing(float delta) {
        double error = Math.hypot(reconcileErrorX, reconcileErrorY);
        if (error <= 1e-4) {
            reconcileErrorX = reconcileErrorY = 0;
            return;
        }
        Birb own = getOwnSide() == Side.WHITE ? session.getWhite() : session.getBlack();
        double factor = 1 - Math.exp(-14 * Math.max(0, delta));
        double step = Math.min(error * factor, MAX_IMMEDIATE_CORRECTION);
        double ratio = step / error;
        double moveX = reconcileErrorX * ratio;
        double moveY = reconcileErrorY * ratio;
        reconcileErrorX -= moveX;
        reconcileErrorY -= moveY;
        int stun = own.getStunSteps();
        own.reconcile(new Birb.Motion(own.getX() + moveX, own.getY() + moveY,
                own.getVelocityX(), own.getVelocityY()), 1.0);
        own.constrainToBounds(800, 800);
        own.stun(stun);
    }

    private void resetPredictionHistory(int ack, long ackedStep) {
        pendingInputs.clear();
        lastAckedSequence = ack;
        lastAckedGuestStep = ackedStep;
        lastAckedInputState = session.getControls(getOwnSide()).snapshotInput();
        predictionBaseState = null;
        predictionBaseInputState = null;
        hasPendingReconcile = false;
        reconcileTarget = null;
        reconcileErrorX = reconcileErrorY = 0;
    }

    private void rebasePredictionHistory(SessionSnapshot state, int ack, long ackSteps, int generation) {
        pruneAcknowledgedInputs(ack, generation);
        while (!pendingInputs.isEmpty() && pendingInputs.peekFirst().sequence() <= ack) {
            pendingInputs.removeFirst();
        }
        lastMappedAuthStep = Math.min(computeAuthStep(ack, ackSteps), session.getSimulationSteps());
        if (pendingInputs.isEmpty()) {
            predictionBaseState = null;
            predictionBaseInputState = null;
        } else {
            predictionBaseState = state;
            predictionBaseStep = lastMappedAuthStep;
            predictionBaseInputState = lastAckedInputState;
        }
        hasPendingReconcile = false;
        reconcileTarget = null;
        reconcileErrorX = reconcileErrorY = 0;
    }

    private void beginPredictionHistory() {
        if (!pendingInputs.isEmpty() || latest == null) return;
        predictionBaseState = latest;
        predictionBaseStep = Math.min(lastMappedAuthStep, session.getSimulationSteps());
        predictionBaseInputState = session.getControls(getOwnSide()).snapshotInput();
    }

    private void recordLocalInput(GameAction action, boolean down) {
        BirbController.InputState stateAfter = session.getControls(getOwnSide()).snapshotInput();
        while (pendingInputs.size() >= MAX_INPUT_HISTORY) {
            RecordedInput oldest = pendingInputs.removeFirst();
            lastAckedSequence = oldest.sequence();
            lastAckedGuestStep = oldest.guestStep();
            lastAckedInputState = oldest.stateAfter();
        }
        pendingInputs.addLast(new RecordedInput(
                inputSequence, simulationGeneration, session.getSimulationSteps(), action, down, stateAfter));
    }

    private long localTimeNanos() {
        long now = time.getAsLong();
        if (!timeOriginInitialized) {
            timeOrigin = now;
            timeOriginInitialized = true;
        }
        return Math.max(0L, Math.min(MAX_WIRE_TIME, now - timeOrigin));
    }

    private JsonValue timedPacket(String type) {
        long now = localTimeNanos();
        JsonValue value = packet(type);
        put(value, "sentAt", now);
        put(value, "echoSentAt", lastRemoteSentAt);
        put(value, "echoAge", lastRemoteSentAt >= 0 ? Math.max(0L, Math.min(MAX_WIRE_TIME, now - lastRemoteReceivedAt)) : 0L);
        return value;
    }

    private long observePacketTiming(JsonValue value) {
        long now = localTimeNanos();
        long remoteSentAt = number(value, "sentAt", 0, MAX_WIRE_TIME);
        long echoSentAt = number(value, "echoSentAt", -1, MAX_WIRE_TIME);
        long echoAge = number(value, "echoAge", 0, MAX_WIRE_TIME);
        if (echoSentAt >= 0) {
            if (echoSentAt > now || echoAge > remoteSentAt) {
                throw new IllegalArgumentException("Invalid timing echo");
            }
            long rttSample = Math.max(0L, (now - echoSentAt) - echoAge);
            long remoteReceivedAt = remoteSentAt - echoAge;
            long offsetSample = Math.round(((remoteReceivedAt - echoSentAt) + (remoteSentAt - now)) / 2.0);
            if (!hasTimingSample) {
                hasTimingSample = true;
                rttNanos = rttSample;
                clockOffsetNanos = offsetSample;
                lastEchoedSentAt = echoSentAt;
            } else if (echoSentAt > lastEchoedSentAt || echoAge == 0) {
                lastEchoedSentAt = Math.max(lastEchoedSentAt, echoSentAt);
                if (rttSample <= rttNanos) {
                    rttNanos = (rttNanos * 3 + rttSample) / 4;
                    clockOffsetNanos = (clockOffsetNanos + offsetSample) / 2;
                } else {
                    rttNanos = (rttNanos * 3 + rttSample) / 4;
                    clockOffsetNanos = (clockOffsetNanos * 7 + offsetSample) / 8;
                }
            }
        }
        if (remoteSentAt >= lastRemoteSentAt) {
            lastRemoteSentAt = remoteSentAt;
            lastRemoteReceivedAt = now;
        }
        long age = hasTimingSample ? (now + clockOffsetNanos) - remoteSentAt : 0L;
        return Math.max(0L, Math.min(MAX_SIMULATION_GAP, age));
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
            lastAppliedInputStep = session.getSimulationSteps();
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
            beginPredictionHistory();
            interruptLocalInput();
            JsonValue release = sequenced("release");
            recordLocalInput(null, false);
            send.accept(encode(release));
        }
        if (now - lastHeard > 20_000_000_000L) {
            disconnect("Connection lost. Return to the lobby to start another match.");
            return;
        }
        if (now - lastPing >= 2_000_000_000L) {
            send.accept(encode(timedPacket("ping")));
            lastPing = now;
        }
        if (host && started) {
            if (now - lastSnapshot >= 50_000_000L) sendState();
        }
    }

    private void sendState() {
        series.observe(session);
        JsonValue value = timedPacket("state");
        put(value, "epoch", epoch);
        put(value, "sequence", ++stateSequence);
        put(value, "ack", acknowledgedInput);
        put(value, "ackSteps", Math.max(0L, session.getSimulationSteps() - lastAppliedInputStep));
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
            beginPredictionHistory();
            session.predict(getOwnSide(), action, down);
            JsonValue value = sequenced("input");
            put(value, "action", action.name());
            put(value, "down", down);
            recordLocalInput(action, down);
            send.accept(encode(value));
        }
    }

    private JsonValue sequenced(String type) {
        JsonValue value = timedPacket(type);
        put(value, "epoch", epoch);
        put(value, "sequence", ++inputSequence);
        put(value, "simulation", simulationGeneration);
        return value;
    }

    public void releaseInput() {
        advanceHost();
        if (!host) beginPredictionHistory();
        clearLocalInput();
        if (!started || disconnected) return;
        if (host) sendState();
        else {
            JsonValue release = sequenced("release");
            recordLocalInput(null, false);
            send.accept(encode(release));
        }
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
            JsonValue value = timedPacket("rematch");
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
        pendingInputs.clear();
        predictionBaseState = null;
        predictionBaseInputState = null;
        hasPendingReconcile = false;
        reconcileTarget = null;
        reconcileErrorX = reconcileErrorY = 0;
    }

    public String status() {
        if (disconnected) return error;
        if (isInterrupted()) return "Host interrupted. Flight paused."
                + (session.isTimed() ? " Clocks continue." : "") + " Waiting to resume...";
        if (!started) return "Waiting for the other player...";
        if (ownRematch && remoteRematch && ownSwap != remoteSwap) return "Rematch choices differ. R keeps colors; X swaps. Choose the same option.";
        if (ownRematch) return (ownSwap ? "Swap requested." : "Rematch requested.") + " Waiting for the other player...";
        if (remoteRematch) return "Your opponent wants a rematch" + (remoteSwap ? " with colors swapped. Press X to accept." : ". Press R to accept.");
        String base = "You are " + (host ? "P1 / creator / " : "P2 / guest / ") + GameSession.sideName(getOwnSide())
                + ". A/D or arrows, Space/Up, E/Enter.";
        if (!session.isGameOver() && getConnectionQuality() == ConnectionQuality.POOR) {
            return base + " Slow connection (" + effectiveLatencyMillis() + " ms).";
        }
        return base;
    }

    public ConnectionQuality getConnectionQuality() {
        if (disconnected || isInterrupted()) return ConnectionQuality.POOR;
        long rttMillis = getRttMillis();
        long ageMillis = getStateAgeMillis();
        long sinceLastStateMillis = !host && started ? Math.max(0L, (time.getAsLong() - lastStateAt) / 1_000_000L) : 0L;
        if (rttMillis >= 400 || ageMillis >= 250 || sinceLastStateMillis >= 600) return ConnectionQuality.POOR;
        if (rttMillis >= 150 || ageMillis >= 90 || sinceLastStateMillis >= 250) return ConnectionQuality.FAIR;
        return ConnectionQuality.GOOD;
    }

    public String connectionHealthLabel() {
        if (disconnected) return "OFFLINE";
        if (isInterrupted()) return "PAUSED";
        if (!started) return "CONNECTING";
        ConnectionQuality quality = getConnectionQuality();
        long latency = effectiveLatencyMillis();
        String prefix = switch (quality) {
            case GOOD -> "GOOD";
            case FAIR -> "FAIR";
            case POOR -> "SLOW";
        };
        return hasTimingSample ? prefix + " · " + latency + " ms" : prefix;
    }

    private long effectiveLatencyMillis() {
        return Math.max(getRttMillis(), getStateAgeMillis() * 2);
    }

    public long getRttMillis() { return hasTimingSample ? (rttNanos + 500_000L) / 1_000_000L : 0L; }
    public long getStateAgeMillis() { return (lastStateAgeNanos + 500_000L) / 1_000_000L; }
    public GameSession getSession() { return session; }
    public Side getOwnSide() { return series.state().sideFor(host); }
    public boolean isInterrupted() { return interruptedUntil != 0 || remoteInterrupted || staleHost; }
    public boolean isStarted() { return started; }
    public boolean isDisconnected() { return disconnected; }
    public static int protocolVersion() { return PeerProtocol.VERSION; }
}
