package com.example.flappychess.controller;

import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.ChessGameModel.GameStatus;
import com.example.flappychess.view.ChessBoardView;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveConversionException;
import com.github.bhlangonijr.chesslib.move.MoveList;

import java.util.Collections;

public class ChessController {
    private final ChessGameModel model;
    private final ChessBoardView view;
    private final MoveList moveHistory;
    private Square carriedSquare = Square.NONE;
    private CompletedMove completedMove;
    private PromotionRequest promotionRequest;

    public record CompletedMove(Side side, String notation) {
    }

    public record PromotionRequest(Side side, Square from, Square to) {
    }

    public ChessController(ChessBoardView view) {
        this(view, new ChessGameModel());
    }

    ChessController(ChessBoardView view, ChessGameModel model) {
        this.model = model;
        this.view = view;
        this.moveHistory = new MoveList(model.getBoard().getFen());
        this.view.update(model.getBoard());
    }

    public void refresh() {
        view.update(model.getBoard());
        updateSelectionHighlights();
    }

    public Side getSideToMove() {
        return model.getSideToMove();
    }

    public GameStatus getGameStatus() {
        return model.getGameStatus();
    }

    public Side getWinner() {
        return model.getWinner();
    }

    public String toggleCarryAt(Square square) {
        if (promotionRequest != null) {
            return getCarriedPieceSymbol();
        }

        completedMove = null;
        if (carriedSquare == Square.NONE) {
            pickUpAt(square);
        } else {
            dropAt(square);
        }

        return getCarriedPieceSymbol();
    }

    public CompletedMove consumeCompletedMove() {
        CompletedMove move = completedMove;
        completedMove = null;
        return move;
    }

    public PromotionRequest getPromotionRequest() {
        return promotionRequest;
    }

    public boolean hasPendingPromotion() {
        return promotionRequest != null;
    }

    void restoreRemote(SessionSnapshot state) {
        model.restorePosition(state.fen());
        carriedSquare = state.hidden();
        promotionRequest = state.promotion();
        completedMove = null;
        view.update(model.getBoard());
        view.setHiddenPieceSquare(state.hidden());
        view.showSelection(state.selected(), state.targets());
    }

    public CompletedMove choosePromotion(PieceType promotionType) {
        if (promotionRequest == null) {
            return null;
        }

        Move move = model.getPromotionMove(promotionRequest.from(), promotionRequest.to(), promotionType);
        if (move == null) {
            return null;
        }

        Side movingSide = model.getSideToMove();
        if (!model.tryMove(move)) {
            return null;
        }

        completedMove = new CompletedMove(movingSide, recordMove(move));
        promotionRequest = null;
        carriedSquare = Square.NONE;
        view.setHiddenPieceSquare(Square.NONE);
        refresh();
        return consumeCompletedMove();
    }

    private void pickUpAt(Square square) {
        if (!model.hasCurrentSidePiece(square)) {
            return;
        }

        carriedSquare = square;
        view.setHiddenPieceSquare(carriedSquare);
        refresh();
    }

    private void dropAt(Square square) {
        if (square == carriedSquare) {
            carriedSquare = Square.NONE;
            view.setHiddenPieceSquare(Square.NONE);
            refresh();
            return;
        }

        Square from = carriedSquare;
        Side movingSide = model.getSideToMove();
        if (!model.getPromotionMoves(from, square).isEmpty()) {
            promotionRequest = new PromotionRequest(movingSide, from, square);
            updateSelectionHighlights();
            return;
        }

        Move move = model.getLegalMove(from, square);
        if (move != null && model.tryMove(move)) {
            completedMove = new CompletedMove(movingSide, recordMove(move));
            carriedSquare = Square.NONE;
            view.setHiddenPieceSquare(Square.NONE);
            refresh();
            return;
        }

        updateSelectionHighlights();
    }

    private String recordMove(Move move) {
        moveHistory.add(move);
        try {
            String[] sanMoves = moveHistory.toSanArray();
            return sanMoves[sanMoves.length - 1];
        } catch (MoveConversionException exception) {
            return move.toString();
        }
    }

    private String getCarriedPieceSymbol() {
        if (carriedSquare == Square.NONE) {
            return null;
        }

        Piece piece = model.getPiece(carriedSquare);
        return piece == Piece.NONE ? null : piece.getFanSymbol();
    }

    private void updateSelectionHighlights() {
        if (carriedSquare == Square.NONE) {
            view.showSelection(Square.NONE, Collections.emptyList());
            return;
        }

        view.showSelection(carriedSquare, model.getLegalTargetSquares(carriedSquare));
    }
}
