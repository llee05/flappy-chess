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
    private final ChessEngine engine;
    private final BirdPilot pilot;
    private final Random random = new Random();
    private ChessSearch search;
    private ChessEngine.Request engineRequest;
    private Move move;
    private int ply = -1;
    private boolean fallback;
    private String phase = "WAITING";

    AiOpponent(GameSession game, Side side, SoloSettings settings) {
        this(game, side, settings, null);
    }

    AiOpponent(GameSession game, Side side, SoloSettings settings, ChessEngine engine) {
        this.game = game; this.side = side; this.settings = settings;
        this.engine = engine;
        pilot = new BirdPilot(game, side, settings.flight());
    }

    void step() {
        if (game.isGameOver() || game.getChess().getSideToMove() != side) {
            pilot.release(); phase = game.isGameOver() ? "FINISHED" : "WAITING"; return;
        }
        if (ply != game.getHistory().size()) {
            ply = game.getHistory().size();
            String fen = game.getBoardView().getBoard().getFen();
            search = null;
            engineRequest = engine == null ? null : engine.choose(fen, settings.chess());
            fallback = false;
            move = null;
        }
        if (move == null) {
            if (engineRequest != null) {
                ChessEngine.State state = engineRequest.state();
                if (state == ChessEngine.State.THINKING) { phase = "STOCKFISH: THINKING"; return; }
                if (state == ChessEngine.State.READY) {
                    try {
                        Move candidate = new Move(engineRequest.move(), side);
                        if (game.getBoardView().getBoard().legalMoves().contains(candidate)) move = candidate;
                        else fallback = true;
                    } catch (RuntimeException ignored) { fallback = true; }
                } else fallback = true;
                engineRequest = null;
            }
            if (move == null) {
                if (search == null) {
                    search = new ChessSearch(game.getBoardView().getBoard().getFen(), settings.chess(), random);
                }
                phase = fallback ? "FALLBACK: THINKING" : "BUILT-IN: THINKING";
                search.step();
                if (!search.isDone()) return;
                move = search.choice();
                if (move == null) return;
            }
        }
        if (game.getChess().hasPendingPromotion()) {
            pilot.press(switch (move.getPromotion().getPieceType()) {
                case ROOK -> GameAction.ROOK; case BISHOP -> GameAction.BISHOP;
                case KNIGHT -> GameAction.KNIGHT; default -> GameAction.QUEEN;
            });
        } else {
            boolean carrying = game.getCarrier() == side;
            String action = carrying ? "DELIVERING" : "PICKING UP";
            phase = fallback ? "FALLBACK: " + action : action;
            if (pilot.flyTo(carrying ? move.getTo() : move.getFrom())) pilot.press(GameAction.CARRY);
        }
    }
    String phase() { return phase; }
    boolean usedFallback() { return fallback; }
}
