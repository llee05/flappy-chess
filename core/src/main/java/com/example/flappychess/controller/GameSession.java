package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessClock;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
import com.example.flappychess.view.ChessBoardView;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import static com.example.flappychess.controller.GameFeedback.Kind.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/** Game rules and input can be tested without starting OpenGL. */
public final class GameSession extends InputAdapter {
    private static final double STEP = 1.0 / 60.0;
    public static final double MIN_BUMP_SPEED = 1.2;
    public static final double BUMP_KNOCKBACK_X = 4.8;
    public static final double BUMP_KNOCKBACK_Y = 3.6;
    public static final double BUMP_RECOIL_X = 2.7;
    public static final double BUMP_RECOIL_Y = 2.0;
    public static final double EMPTY_BUMP_KNOCKBACK_X = 2.6;
    public static final double EMPTY_BUMP_KNOCKBACK_Y = 2.0;
    public static final double EMPTY_BUMP_RECOIL_X = 1.6;
    public static final double EMPTY_BUMP_RECOIL_Y = 1.2;
    public static final int BUMP_STUN_STEPS = 24;
    public static final int BUMP_COOLDOWN_STEPS = 36;
    public static final int CARRY_HIT_STOP_STEPS = 4;
    public static final int EMPTY_HIT_STOP_STEPS = 2;
    public static final int DROP_RETURN_STEPS = 20;

    public record DropReturn(Piece piece, Square origin, double fromX, double fromY, int stepsRemaining) {
        DropReturn withStepsRemaining(int steps) {
            return new DropReturn(piece, origin, fromX, fromY, steps);
        }
    }

    public record BumpImpact(int sequence, double x, double y, double speed, boolean dropped) { }

    private final ChessBoardView boardView = new ChessBoardView();
    private final ChessController chess;
    private final ChessClock clock;
    private final LongSupplier nanoTime;
    private MatchSettings settings;
    private MatchSeries.State series = new MatchSeries.State(1, 0, 0, true, MatchSeries.Players.LOCAL);
    private SoloSettings solo;
    private String aiPhase = "WAITING";
    private final GameFeedback feedback = new GameFeedback();
    private final Set<Side> warnedLowTime = new HashSet<>();
    private final List<ChessController.CompletedMove> history = new ArrayList<>();
    private final Set<Integer> pressed = new HashSet<>();
    private final Birb white = new Birb(425, 625, 50, 50);
    private final Birb black = new Birb(425, 125, 50, 50, -1);
    private final BirbController whiteControls = new BirbController(white, 800, 800);
    private final BirbController blackControls = new BirbController(black, 800, 800, Keys.LEFT, Keys.RIGHT, Keys.UP);
    private double accumulator;
    private boolean hasClockTime;
    private long clockTime;
    private long simulationSteps;
    private boolean replicaSynchronized;
    private int bumpCooldown;
    private int hitStopSteps;
    private DropReturn dropReturn;
    private BumpImpact lastBump;
    private Side lastBumper;
    private Side lastBumped;
    private Side carrier;
    private String result;
    private String reason;

    public GameSession() { this(MatchSettings.DEFAULT); }

    public GameSession(MatchSettings settings) { this(new ChessGameModel(), settings, System::nanoTime); }

    GameSession(ChessGameModel model, MatchSettings settings, LongSupplier nanoTime) {
        this(model, settings.initialTime(), nanoTime, settings.timed());
        this.settings = settings;
    }

    GameSession(ChessGameModel model, Duration initialTime, LongSupplier nanoTime) {
        this(model, initialTime, nanoTime, true);
    }

    GameSession(ChessGameModel model, Duration initialTime, LongSupplier nanoTime, boolean timed) {
        chess = new ChessController(boardView, model);
        clock = new ChessClock(initialTime);
        this.nanoTime = nanoTime;
        this.settings = timed ? new MatchSettings(initialTime.toMillis(), 0) : MatchSettings.UNTIMED;
        if (isTimed()) clock.tick(nanoTime.getAsLong(), chess.getSideToMove());
    }

    public void update(float delta) {
        update(delta, null);
    }

    void update(float delta, Runnable beforeStep) {
        tickClock();
        if (isGameOver()) return;
        // Bound catch-up after a suspended window; the chess clock still uses real time.
        accumulator += Math.max(0, Math.min(delta, 0.25f));
        while (accumulator + 1e-9 >= STEP) {
            if (hitStopSteps > 0) {
                simulationSteps++;
                hitStopSteps--;
                accumulator -= STEP;
                continue;
            }
            if (beforeStep != null) beforeStep.run();
            if (isGameOver()) { accumulator = 0; return; }
            simulationSteps++;
            advanceDropReturn();
            double prevWhiteX = white.getX(), prevWhiteY = white.getY();
            double prevBlackX = black.getX(), prevBlackY = black.getY();
            whiteControls.update();
            blackControls.update();
            resolveBump(prevWhiteX, prevWhiteY, prevBlackX, prevBlackY);
            accumulator -= STEP;
        }
    }

    @Override public boolean keyDown(int key) {
        if (!pressed.add(key)) return true;
        tickClock();
        if (isGameOver()) return true;
        if (chess.hasPendingPromotion()) {
            PieceType type = switch (key) {
                case Keys.Q -> PieceType.QUEEN;
                case Keys.R -> PieceType.ROOK;
                case Keys.B -> PieceType.BISHOP;
                case Keys.N -> PieceType.KNIGHT;
                default -> null;
            };
            if (type != null) {
                Square destination = chess.getPromotionRequest().to();
                finishMove(chess.choosePromotion(type), destination);
            }
            return true;
        }
        if (hitStopSteps > 0) {
            if (key == Keys.A || key == Keys.D) whiteControls.handleKeyPressed(key);
            if (key == Keys.LEFT || key == Keys.RIGHT) blackControls.handleKeyPressed(key);
            return true;
        }
        whiteControls.handleKeyPressed(key);
        blackControls.handleKeyPressed(key);
        if (key == Keys.SPACE && !white.isStunned()) feedback.emit(FLAP, Side.WHITE, birdSquare(white));
        if (key == Keys.UP && !black.isStunned()) feedback.emit(FLAP, Side.BLACK, birdSquare(black));
        if (key == Keys.E && !white.isStunned()) toggleCarry(Side.WHITE, white);
        if (key == Keys.ENTER && !black.isStunned()) toggleCarry(Side.BLACK, black);
        return true;
    }

    @Override public boolean keyUp(int key) {
        pressed.remove(key);
        whiteControls.handleKeyReleased(key);
        blackControls.handleKeyReleased(key);
        return true;
    }

    public void releaseInput() {
        for (int key : Set.copyOf(pressed)) keyUp(key);
        releaseInput(Side.WHITE);
        releaseInput(Side.BLACK);
    }

    public void releaseInput(Side side) {
        BirbController controls = side == Side.WHITE ? whiteControls : blackControls;
        for (GameAction action : GameAction.values()) {
            int key = action.keyFor(side);
            keyUp(key);
            controls.handleKeyReleased(key);
        }
    }

    public void command(Side side, GameAction action, boolean down) {
        if (down && action.isPromotion() && chess.getSideToMove() != side) return;
        int key = action.keyFor(side);
        if (down) keyDown(key); else keyUp(key);
    }

    /** Predict movement only; guest pickup, promotion, and outcomes wait for the host. */
    public void predict(Side side, GameAction action, boolean down) {
        if (!action.isMovement() || down && (isGameOver() || chess.hasPendingPromotion())) return;
        if (down && hitStopSteps > 0 && action == GameAction.FLAP) return;
        BirbController controls = side == Side.WHITE ? whiteControls : blackControls;
        if (down) controls.handleKeyPressed(action.keyFor(side));
        else controls.handleKeyReleased(action.keyFor(side));
    }

    public void updateReplica(float delta, Side ownSide) {
        if (isGameOver()) return;
        if (isTimed()) clock.tick(nanoTime.getAsLong(), chess.getSideToMove());
        accumulator += Math.max(0, Math.min(delta, 0.25f));
        while (accumulator + 1e-9 >= STEP) {
            simulationSteps++;
            if (hitStopSteps > 0) {
                hitStopSteps--;
                accumulator -= STEP;
                continue;
            }
            advanceDropReturn();
            (ownSide == Side.WHITE ? whiteControls : blackControls).update();
            accumulator -= STEP;
        }
    }

    public SessionSnapshot snapshot() {
        return new SessionSnapshot(boardView.getBoard().getFen(), white.snapshot(), black.snapshot(),
                white.getStunSteps(), black.getStunSteps(), bumpCooldown, hitStopSteps, dropReturn, lastBump,
                clock.getRemainingMillis(Side.WHITE), clock.getRemainingMillis(Side.BLACK),
                boardView.getSelectedSquare(), boardView.getHiddenPieceSquare(), boardView.getLegalTargets(),
                chess.getPromotionRequest(), carrier, history, result, reason, feedback.events(), settings, chess.getInitialFen(), series);
    }

    public void restoreReplica(SessionSnapshot state) {
        restoreReplica(state, 0L);
    }

    public void restoreReplica(SessionSnapshot state, long stateAgeNanos) {
        int previousMoves = history.size();
        Side previousTurn = chess.getSideToMove();
        boolean wasGameOver = isGameOver();
        long now = nanoTime.getAsLong();
        if (replicaSynchronized && isTimed() && !wasGameOver) {
            clock.tick(now, previousTurn);
        }
        chess.restoreRemote(state);
        long ageNanos = state.settings().timed() && state.result() == null ? Math.max(0L, stateAgeNanos) : 0L;
        Side activeSide = chess.getSideToMove();
        long whiteNanos = Math.max(0L, state.whiteMillis() * 1_000_000L - (activeSide == Side.WHITE ? ageNanos : 0L));
        long blackNanos = Math.max(0L, state.blackMillis() * 1_000_000L - (activeSide == Side.BLACK ? ageNanos : 0L));
        if (replicaSynchronized && state.settings().timed() && state.result() == null && !wasGameOver
                && state.history().size() == previousMoves && activeSide == previousTurn) {
            long currentActive = clock.getRemainingNanos(activeSide);
            long incomingActive = activeSide == Side.WHITE ? whiteNanos : blackNanos;
            // Preserve local sub-millisecond phase within the 1ms snapshot truncation window,
            // and avoid routinely giving time back on small positive transit jitter.
            if (currentActive > 0
                    && ((currentActive >= incomingActive && currentActive <= incomingActive + 999_999L)
                    || (incomingActive > currentActive && incomingActive - currentActive <= 100_000_000L))) {
                if (activeSide == Side.WHITE) whiteNanos = currentActive;
                else blackNanos = currentActive;
            }
        }
        clock.restoreNanos(whiteNanos, blackNanos, now);
        replicaSynchronized = true;
        carrier = state.carrier();
        white.stun(state.whiteStunSteps());
        black.stun(state.blackStunSteps());
        bumpCooldown = state.bumpCooldown();
        hitStopSteps = state.hitStopSteps();
        dropReturn = state.result() == null ? state.dropReturn() : null;
        lastBump = state.lastBump();
        history.clear();
        history.addAll(state.history());
        result = state.result();
        reason = state.reason();
        feedback.restore(state.feedback());
        settings = state.settings();
        series = state.series();
    }

    /**
     * Runs updates or commands as if the monotonic clock read {@code nanos}. An online host
     * uses this to apply a scheduled guest command at its scheduled time on its own timeline.
     */
    void atClockTime(long nanos, Runnable action) {
        boolean previous = hasClockTime;
        long previousTime = clockTime;
        hasClockTime = true;
        clockTime = nanos;
        try {
            action.run();
        } finally {
            hasClockTime = previous;
            clockTime = previousTime;
        }
    }

    /** Consumes hit-stop that already elapsed on the host while a snapshot was in transit. */
    void elapseReplicaHitStop(long steps) {
        hitStopSteps = (int) Math.max(0, hitStopSteps - Math.max(0, steps));
    }

    private void advanceDropReturn() {
        if (dropReturn == null) return;
        int remaining = dropReturn.stepsRemaining() - 1;
        dropReturn = remaining > 0 ? dropReturn.withStepsRemaining(remaining) : null;
    }

    private void resolveBump(double prevWhiteX, double prevWhiteY, double prevBlackX, double prevBlackY) {
        if (bumpCooldown > 0) {
            bumpCooldown--;
            return;
        }
        if (isGameOver() || chess.hasPendingPromotion() || !white.overlaps(black)) return;
        double rvx = white.getVelocityX() - black.getVelocityX();
        double rvy = white.getVelocityY() - black.getVelocityY();
        double impactSpeed = Math.hypot(rvx, rvy);
        if (impactSpeed < MIN_BUMP_SPEED) return;
        double prevDist = Math.hypot(prevWhiteX - prevBlackX, prevWhiteY - prevBlackY);
        double dist = Math.hypot(white.getX() - black.getX(), white.getY() - black.getY());
        if (dist > prevDist + 1e-6 && prevDist >= 10) return;

        double impactX = clamp((white.getX() + black.getX()) / 2.0 + 25.0, 0, 800);
        double impactY = clamp((white.getY() + black.getY()) / 2.0 + 25.0, 0, 800);

        double dx = black.getX() - white.getX();
        double dy = black.getY() - white.getY();
        if (Math.hypot(dx, dy) < 1e-6) {
            dx = prevBlackX - prevWhiteX;
            dy = prevBlackY - prevWhiteY;
        }
        double axisDist = Math.hypot(dx, dy);
        double whiteApproach;
        double blackApproach;
        if (axisDist > 1e-6) {
            double ux = dx / axisDist;
            double uy = dy / axisDist;
            whiteApproach = white.getVelocityX() * ux + white.getVelocityY() * uy;
            blackApproach = -(black.getVelocityX() * ux + black.getVelocityY() * uy);
        } else {
            whiteApproach = Math.hypot(white.getVelocityX(), white.getVelocityY());
            blackApproach = Math.hypot(black.getVelocityX(), black.getVelocityY());
        }

        Side bumper;
        if (whiteApproach > blackApproach + 1e-6) bumper = Side.WHITE;
        else if (blackApproach > whiteApproach + 1e-6) bumper = Side.BLACK;
        else if (carrier != null) bumper = carrier.flip();
        else bumper = chess.getSideToMove().flip();
        Side bumped = bumper.flip();

        double pushX = Math.abs(dx) > 1e-6 ? Math.signum(dx) : Math.abs(rvx) > 1e-6 ? Math.signum(rvx) : 1;
        double pushY = Math.abs(dy) > 1e-6 ? Math.signum(dy) : Math.abs(rvy) > 1e-6 ? Math.signum(rvy) : -1;

        boolean carrying = carrier != null;
        Side droppedSide = carrier;
        Square droppedOrigin = Square.NONE;
        if (carrying) {
            Birb carrierBird = droppedSide == Side.WHITE ? white : black;
            double dropFromX = clamp(carrierBird.getX() + 25.0, 24, 776);
            double dropFromY = clamp(carrierBird.getY() + 25.0 + (droppedSide == Side.WHITE ? 32.0 : -32.0), 24, 776);
            droppedOrigin = chess.dropCarriedPiece();
            Piece droppedPiece = droppedOrigin != Square.NONE ? boardView.getBoard().getPiece(droppedOrigin) : Piece.NONE;
            carrier = null;
            if (droppedOrigin != Square.NONE && droppedPiece != Piece.NONE) {
                dropReturn = new DropReturn(droppedPiece, droppedOrigin, dropFromX, dropFromY, DROP_RETURN_STEPS);
            }
        }

        double bumpedVx = carrying ? BUMP_KNOCKBACK_X : EMPTY_BUMP_KNOCKBACK_X;
        double bumpedVy = carrying ? BUMP_KNOCKBACK_Y : EMPTY_BUMP_KNOCKBACK_Y;
        double bumperVx = carrying ? BUMP_RECOIL_X : EMPTY_BUMP_RECOIL_X;
        double bumperVy = carrying ? BUMP_RECOIL_Y : EMPTY_BUMP_RECOIL_Y;

        double whiteVx = -pushX * (bumped == Side.WHITE ? bumpedVx : bumperVx);
        double whiteVy = -pushY * (bumped == Side.WHITE ? bumpedVy : bumperVy);
        double blackVx = pushX * (bumped == Side.BLACK ? bumpedVx : bumperVx);
        double blackVy = pushY * (bumped == Side.BLACK ? bumpedVy : bumperVy);

        int whiteStun = carrying && (bumped == Side.WHITE || droppedSide == Side.WHITE) ? BUMP_STUN_STEPS : 0;
        int blackStun = carrying && (bumped == Side.BLACK || droppedSide == Side.BLACK) ? BUMP_STUN_STEPS : 0;

        white.applyBump(-pushX * 4, -pushY * 4, whiteVx, whiteVy, whiteStun, 800, 800);
        black.applyBump(pushX * 4, pushY * 4, blackVx, blackVy, blackStun, 800, 800);

        bumpCooldown = BUMP_COOLDOWN_STEPS;
        hitStopSteps = carrying ? CARRY_HIT_STOP_STEPS : EMPTY_HIT_STOP_STEPS;
        lastBumper = bumper;
        lastBumped = bumped;
        Side feedbackSide = droppedSide != null ? droppedSide : bumped;
        Birb feedbackBird = feedbackSide == Side.WHITE ? white : black;
        Square eventSquare = carrying && droppedOrigin != Square.NONE ? droppedOrigin : birdSquare(feedbackBird);
        int eventSequence = feedback.emit(carrying ? BUMP_DROP : BUMP, feedbackSide, eventSquare);
        lastBump = new BumpImpact(eventSequence, impactX, impactY, impactSpeed, carrying);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void toggleCarry(Side side, Birb bird) {
        if ((carrier != null && carrier != side) || chess.getSideToMove() != side) return;
        Square square = birdSquare(bird);
        Square from = boardView.getHiddenPieceSquare();
        String symbol = chess.toggleCarryAt(square);
        carrier = symbol == null ? null : side;
        if (carrier != null) dropReturn = null;
        var move = chess.consumeCompletedMove();
        if (move != null) finishMove(move, square);
        else if (from == Square.NONE) feedback.emit(carrier == null ? NO_PIECE : PICKUP, side, square);
        else if (carrier == null) feedback.emit(CANCEL, side, square);
        else if (!chess.hasPendingPromotion()) feedback.emit(ILLEGAL_DROP, side, square);
    }

    private static Square birdSquare(Birb bird) {
        return ChessBoardView.squareForPoint(bird.getX() + 25, bird.getY() + 25);
    }

    private void finishMove(ChessController.CompletedMove move, Square destination) {
        if (move == null) return;
        carrier = null;
        dropReturn = null;
        history.add(move);
        if (isTimed()) clock.addIncrement(move.side(), settings.incrementMillis());
        feedback.emit(move.captured() != Piece.NONE ? CAPTURE : DROP, move.side(), destination);
        if (boardView.getBoard().isKingAttacked()) {
            Side checked = chess.getSideToMove();
            feedback.emit(CHECK, checked, boardView.getBoard().getKingSquare(checked));
        }
        switch (chess.getGameStatus()) {
            case CHECKMATE -> end(sideName(chess.getWinner()) + " wins", "Checkmate");
            case STALEMATE -> end("Draw", "Stalemate");
            case DRAW -> end("Draw", "Draw by chess rules");
            default -> { }
        }
    }

    private void tickClock() {
        if (!isTimed() || isGameOver()) return;
        Side side = chess.getSideToMove();
        // Input ticks before a move switches sides, charging the elapsed time correctly.
        clock.tick(hasClockTime ? clockTime : nanoTime.getAsLong(), side);
        if (clock.isExpired(side)) {
            Side opponent = side.flip();
            if (chess.hasSufficientMatingMaterial(opponent)) {
                end(sideName(opponent) + " wins", sideName(side) + " ran out of time");
            } else {
                end("Draw", sideName(side) + " ran out of time");
            }
        }
        else if (clock.getRemainingMillis(side) <= 30_000 && warnedLowTime.add(side)) {
            Birb bird = side == Side.WHITE ? white : black;
            feedback.emit(LOW_TIME, side, birdSquare(bird));
        }
    }

    private void end(String title, String reason) {
        result = title;
        this.reason = reason;
        dropReturn = null;
        releaseInput();
    }

    public static String sideName(Side side) { return side == Side.WHITE ? "White" : "Black"; }
    public boolean isGameOver() { return result != null; }
    public boolean isTimed() { return settings.timed(); }
    public MatchSettings getSettings() { return settings; }
    public MatchSeries.State getSeries() { return series; }
    public SoloSettings getSoloSettings() { return solo; }
    void setSoloSettings(SoloSettings solo) { this.solo = solo; }
    public String getAiPhase() { return aiPhase; }
    void setAiPhase(String phase) { aiPhase = phase; }
    void setSeries(MatchSeries.State series) { this.series = series; }
    public GameFeedback getFeedback() { return feedback; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public ChessBoardView getBoardView() { return boardView; }
    public ChessController getChess() { return chess; }
    public ChessClock getClock() { return clock; }
    public Birb getWhite() { return white; }
    public Birb getBlack() { return black; }
    BirbController getControls(Side side) { return side == Side.WHITE ? whiteControls : blackControls; }
    public long getSimulationSteps() { return simulationSteps; }
    public Side getCarrier() { return carrier; }
    public int getBumpCooldown() { return bumpCooldown; }
    public int getHitStopSteps() { return hitStopSteps; }
    public boolean isInHitStop() { return hitStopSteps > 0; }
    public DropReturn getDropReturn() { return dropReturn; }
    public BumpImpact getLastBump() { return lastBump; }
    public Side getLastBumper() { return lastBumper; }
    public Side getLastBumped() { return lastBumped; }
    public List<ChessController.CompletedMove> getHistory() { return Collections.unmodifiableList(history); }
}
