package com.example.flappychess.controller;

import com.example.flappychess.model.SoloSettings;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.Random;

/** Chooses chess intent, then physically delivers the piece through normal input. */
final class AiOpponent {
    private final GameSession game;
    private final Side side;
    private final SoloSettings settings;
    private final BirdPilot pilot;
    private final Random random = new Random();
    private ChessSearch search;
    private Move move;
    private int ply = -1;
    private String phase = "WAITING";

    AiOpponent(GameSession game, Side side, SoloSettings settings) {
        this.game = game; this.side = side; this.settings = settings;
        pilot = new BirdPilot(game, side, settings.flight());
    }

    void step() {
        if (game.isGameOver() || game.getChess().getSideToMove() != side) {
            pilot.release(); phase = game.isGameOver() ? "FINISHED" : "WAITING"; return;
        }
        if (ply != game.getHistory().size()) {
            ply = game.getHistory().size();
            search = new ChessSearch(game.getBoardView().getBoard().getFen(), settings.chess(), random);
            move = null;
        }
        if (move == null) {
            phase = "THINKING";
            search.step();
            if (!search.isDone()) return;
            move = search.choice();
            if (move == null) return;
        }
        if (game.getChess().hasPendingPromotion()) {
            pilot.press(switch (move.getPromotion().getPieceType()) {
                case ROOK -> GameAction.ROOK; case BISHOP -> GameAction.BISHOP;
                case KNIGHT -> GameAction.KNIGHT; default -> GameAction.QUEEN;
            });
        } else {
            boolean carrying = game.getCarrier() == side;
            phase = carrying ? "DELIVERING" : "PICKING UP";
            if (pilot.flyTo(carrying ? move.getTo() : move.getFrom())) pilot.press(GameAction.CARRY);
        }
    }
    String phase() { return phase; }
}
