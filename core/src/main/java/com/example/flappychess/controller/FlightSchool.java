package com.example.flappychess.controller;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.view.ChessBoardView;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.time.Duration;
import java.util.function.LongSupplier;

/** An untimed lesson using the same input, physics, and legal moves as a match. */
public final class FlightSchool extends InputAdapter {
    public enum Stage { WHITE_FLY, WHITE_PICKUP, WHITE_DELIVER, BLACK_FLY, BLACK_PICKUP, BLACK_DELIVER, COMPLETE }

    private final GameSession session;
    private Stage stage = Stage.WHITE_FLY;

    public FlightSchool() { this(System::nanoTime); }

    FlightSchool(LongSupplier time) {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/3p4/8/8/8/8/4P3/4K3 w - - 0 1");
        session = new GameSession(model, Duration.ofMinutes(5), time, false);
        session.getWhite().restore(new Birb.Motion(125, 625, 0, 0));
        session.getBlack().restore(new Birb.Motion(625, 125, 0, 0));
    }

    public void update(float delta) {
        if (isComplete()) return;
        session.update(delta);
        advance();
    }

    @Override public boolean keyDown(int key) {
        if (isComplete()) return true;
        advance();
        if (session.getCarrier() == null && (key == Keys.E || key == Keys.ENTER)) {
            Birb bird = key == Keys.E ? session.getWhite() : session.getBlack();
            Square square = ChessBoardView.squareForPoint(bird.getX() + 25, bird.getY() + 25);
            if (square != (key == Keys.E ? Square.E2 : Square.D7)) return true;
        }
        session.keyDown(key);
        advance();
        return true;
    }

    @Override public boolean keyUp(int key) { return session.keyUp(key); }

    private void advance() {
        if (session.getHistory().size() == 2) {
            stage = Stage.COMPLETE;
            session.releaseInput();
            return;
        }
        boolean black = !session.getHistory().isEmpty();
        Side side = black ? Side.BLACK : Side.WHITE;
        Birb bird = black ? session.getBlack() : session.getWhite();
        Square square = ChessBoardView.squareForPoint(bird.getX() + 25, bird.getY() + 25);
        if (black && stage.ordinal() < Stage.BLACK_FLY.ordinal()) stage = Stage.BLACK_FLY;
        if (session.getCarrier() == side) stage = black ? Stage.BLACK_DELIVER : Stage.WHITE_DELIVER;
        else if (square == (black ? Square.D7 : Square.E2)
                || stage == Stage.WHITE_DELIVER || stage == Stage.BLACK_DELIVER) {
            stage = black ? Stage.BLACK_PICKUP : Stage.WHITE_PICKUP;
        }
    }

    public String title() {
        return switch (stage) {
            case WHITE_FLY -> "WHITE: TAKE FLIGHT";
            case WHITE_PICKUP -> "WHITE: PICK UP THE PAWN";
            case WHITE_DELIVER -> "WHITE: MAKE A DELIVERY";
            case BLACK_FLY -> "BLACK: GRAVITY IS REVERSED";
            case BLACK_PICKUP -> "BLACK: PICK UP THE PAWN";
            case BLACK_DELIVER -> "BLACK: MAKE A DELIVERY";
            case COMPLETE -> "FLIGHT SCHOOL COMPLETE";
        };
    }

    public String instruction() {
        return switch (stage) {
            case WHITE_FLY -> "A / D steer. Tap SPACE to flap up. Reach E2.";
            case WHITE_PICKUP -> "Over E2, press E to pick up your pawn.";
            case WHITE_DELIVER -> "Fly to E3 or E4. Press E again to drop.";
            case BLACK_FLY -> "LEFT / RIGHT steer. Tap UP to flap DOWN. Reach D7.";
            case BLACK_PICKUP -> "Over D7, press ENTER to pick up your pawn.";
            case BLACK_DELIVER -> "Fly to D6 or D5. Press ENTER again to drop.";
            case COMPLETE -> "Both deliveries made. You are ready for a match!";
        };
    }

    public Square target() {
        return switch (stage) {
            case WHITE_FLY, WHITE_PICKUP -> Square.E2;
            case BLACK_FLY, BLACK_PICKUP -> Square.D7;
            default -> Square.NONE;
        };
    }

    public GameSession getSession() { return session; }
    public Stage getStage() { return stage; }
    public boolean isComplete() { return stage == Stage.COMPLETE; }
}
