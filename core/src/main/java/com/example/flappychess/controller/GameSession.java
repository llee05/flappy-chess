package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessClock;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.view.ChessBoardView;
import com.github.bhlangonijr.chesslib.PieceType;
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
    private final ChessBoardView boardView = new ChessBoardView();
    private final ChessController chess;
    private final ChessClock clock;
    private final LongSupplier nanoTime;
    private final boolean timed;
    private final GameFeedback feedback = new GameFeedback();
    private final Set<Side> warnedLowTime = new HashSet<>();
    private final List<ChessController.CompletedMove> history = new ArrayList<>();
    private final Set<Integer> pressed = new HashSet<>();
    private final Birb white = new Birb(425, 625, 50, 50);
    private final Birb black = new Birb(425, 125, 50, 50, -1);
    private final BirbController whiteControls = new BirbController(white, 800, 800);
    private final BirbController blackControls = new BirbController(black, 800, 800, Keys.LEFT, Keys.RIGHT, Keys.UP);
    private double accumulator;
    private Side carrier;
    private String result;
    private String reason;

    public GameSession() { this(new ChessGameModel(), Duration.ofMinutes(5), System::nanoTime); }

    GameSession(ChessGameModel model, Duration initialTime, LongSupplier nanoTime) {
        this(model, initialTime, nanoTime, true);
    }

    GameSession(ChessGameModel model, Duration initialTime, LongSupplier nanoTime, boolean timed) {
        chess = new ChessController(boardView, model);
        clock = new ChessClock(initialTime);
        this.nanoTime = nanoTime;
        this.timed = timed;
        clock.tick(nanoTime.getAsLong(), chess.getSideToMove());
    }

    public void update(float delta) {
        tickClock();
        if (isGameOver()) return;
        // Bound catch-up after a suspended window; the chess clock still uses real time.
        accumulator += Math.max(0, Math.min(delta, 0.25f));
        while (accumulator + 1e-9 >= STEP) {
            whiteControls.update();
            blackControls.update();
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
        whiteControls.handleKeyPressed(key);
        blackControls.handleKeyPressed(key);
        if (key == Keys.SPACE) feedback.emit(FLAP, Side.WHITE, birdSquare(white));
        if (key == Keys.UP) feedback.emit(FLAP, Side.BLACK, birdSquare(black));
        if (key == Keys.E) toggleCarry(Side.WHITE, white);
        if (key == Keys.ENTER) toggleCarry(Side.BLACK, black);
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
        BirbController controls = side == Side.WHITE ? whiteControls : blackControls;
        if (down) controls.handleKeyPressed(action.keyFor(side));
        else controls.handleKeyReleased(action.keyFor(side));
    }

    public void updateReplica(float delta, Side ownSide) {
        if (isGameOver()) return;
        clock.tick(nanoTime.getAsLong(), chess.getSideToMove());
        accumulator += Math.max(0, Math.min(delta, 0.25f));
        while (accumulator + 1e-9 >= STEP) {
            (ownSide == Side.WHITE ? whiteControls : blackControls).update();
            accumulator -= STEP;
        }
    }

    public SessionSnapshot snapshot() {
        return new SessionSnapshot(boardView.getBoard().getFen(), white.snapshot(), black.snapshot(),
                clock.getRemainingMillis(Side.WHITE), clock.getRemainingMillis(Side.BLACK),
                boardView.getSelectedSquare(), boardView.getHiddenPieceSquare(), boardView.getLegalTargets(),
                chess.getPromotionRequest(), carrier, history, result, reason, feedback.events());
    }

    public void restoreReplica(SessionSnapshot state) {
        chess.restoreRemote(state);
        clock.restore(state.whiteMillis(), state.blackMillis(), nanoTime.getAsLong());
        carrier = state.carrier();
        history.clear();
        history.addAll(state.history());
        result = state.result();
        reason = state.reason();
        feedback.restore(state.feedback());
    }

    private void toggleCarry(Side side, Birb bird) {
        if ((carrier != null && carrier != side) || chess.getSideToMove() != side) return;
        Square square = birdSquare(bird);
        Square from = boardView.getHiddenPieceSquare();
        String symbol = chess.toggleCarryAt(square);
        carrier = symbol == null ? null : side;
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
        history.add(move);
        feedback.emit(move.notation().contains("x") ? CAPTURE : DROP, move.side(), destination);
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
        if (!timed || isGameOver()) return;
        Side side = chess.getSideToMove();
        // Input ticks before a move switches sides, charging the elapsed time correctly.
        clock.tick(nanoTime.getAsLong(), side);
        if (clock.isExpired(side)) end(sideName(side.flip()) + " wins", sideName(side) + " ran out of time");
        else if (clock.getRemainingMillis(side) <= 30_000 && warnedLowTime.add(side)) {
            Birb bird = side == Side.WHITE ? white : black;
            feedback.emit(LOW_TIME, side, birdSquare(bird));
        }
    }

    private void end(String title, String reason) {
        result = title;
        this.reason = reason;
        releaseInput();
    }

    public static String sideName(Side side) { return side == Side.WHITE ? "White" : "Black"; }
    public boolean isGameOver() { return result != null; }
    public boolean isTimed() { return timed; }
    public GameFeedback getFeedback() { return feedback; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public ChessBoardView getBoardView() { return boardView; }
    public ChessController getChess() { return chess; }
    public ChessClock getClock() { return clock; }
    public Birb getWhite() { return white; }
    public Birb getBlack() { return black; }
    public Side getCarrier() { return carrier; }
    public List<ChessController.CompletedMove> getHistory() { return Collections.unmodifiableList(history); }
}
