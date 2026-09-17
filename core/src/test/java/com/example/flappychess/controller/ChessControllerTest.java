package com.example.flappychess.controller;

import com.example.flappychess.view.ChessBoardView;
import com.example.flappychess.model.ChessGameModel;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChessControllerTest {
    @Test
    void constructorRendersInitialBoard() {
        RecordingChessBoardView view = new RecordingChessBoardView();

        new ChessController(view);

        assertEquals(1, view.updateCount);
        assertEquals(Side.WHITE, view.lastBoard.getSideToMove());
    }

    @Test
    void refreshRendersBoardAndClearsSelectionWhenNothingIsCarried() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        controller.refresh();

        assertEquals(2, view.updateCount);
        assertEquals(Square.NONE, view.selectedSquare);
        assertEquals(Collections.emptyList(), view.legalTargets);
    }

    @Test
    void cannotPickUpOpposingPieceAtStart() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        assertNull(controller.toggleCarryAt(Square.E7));

        assertEquals(Square.NONE, view.hiddenPieceSquare);
        assertEquals(1, view.updateCount);
        assertEquals(Square.NONE, view.selectedSquare);
        assertEquals(Collections.emptyList(), view.legalTargets);
    }

    @Test
    void afterMoveOnlyNextSideToMoveCanPickUpPieces() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        controller.toggleCarryAt(Square.E2);
        controller.toggleCarryAt(Square.E4);
        int updateCountAfterMove = view.updateCount;
        assertEquals(Side.BLACK, controller.getSideToMove());

        assertNull(controller.toggleCarryAt(Square.E4));
        assertEquals(updateCountAfterMove, view.updateCount);
        assertEquals(Square.NONE, view.hiddenPieceSquare);

        assertEquals(Piece.BLACK_PAWN.getFanSymbol(), controller.toggleCarryAt(Square.E7));
        assertEquals(Square.E7, view.hiddenPieceSquare);
        assertEquals(Square.E7, view.selectedSquare);
        assertEquals(List.of(Square.E5, Square.E6), view.legalTargets);
    }

    @Test
    void pickingUpCurrentSidePieceHidesItAndShowsLegalTargets() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        String carriedSymbol = controller.toggleCarryAt(Square.E2);

        assertEquals(Piece.WHITE_PAWN.getFanSymbol(), carriedSymbol);
        assertEquals(Square.E2, view.hiddenPieceSquare);
        assertEquals(2, view.updateCount);
        assertEquals(Square.E2, view.selectedSquare);
        assertEquals(List.of(Square.E3, Square.E4), view.legalTargets);
    }

    @Test
    void droppingOnLegalTargetMovesPieceAndClearsCarryState() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        controller.toggleCarryAt(Square.E2);
        String carriedSymbolAfterDrop = controller.toggleCarryAt(Square.E4);
        ChessController.CompletedMove completedMove = controller.consumeCompletedMove();

        assertNull(carriedSymbolAfterDrop);
        assertEquals(Side.WHITE, completedMove.side());
        assertEquals("e4", completedMove.notation());
        assertEquals(Square.NONE, view.hiddenPieceSquare);
        assertEquals(Side.BLACK, view.lastBoard.getSideToMove());
        assertEquals(Piece.WHITE_PAWN, view.lastBoard.getPiece(Square.E4));
        assertEquals(Square.NONE, view.selectedSquare);
        assertEquals(Collections.emptyList(), view.legalTargets);
    }

    @Test
    void completedMoveUsesStandardNotationForPieces() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        controller.toggleCarryAt(Square.G1);
        controller.toggleCarryAt(Square.F3);
        ChessController.CompletedMove completedMove = controller.consumeCompletedMove();

        assertEquals(Side.WHITE, completedMove.side());
        assertEquals("Nf3", completedMove.notation());
    }

    @Test
    void promotionDropWaitsForPieceChoice() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view, model);

        controller.toggleCarryAt(Square.A7);
        String carriedSymbol = controller.toggleCarryAt(Square.A8);

        assertEquals(Piece.WHITE_PAWN.getFanSymbol(), carriedSymbol);
        assertNull(controller.consumeCompletedMove());
        assertEquals(new ChessController.PromotionRequest(Side.WHITE, Square.A7, Square.A8), controller.getPromotionRequest());
        assertEquals(Side.WHITE, controller.getSideToMove());
        assertEquals(Square.A7, view.hiddenPieceSquare);
    }

    @Test
    void choosingPromotionPieceCompletesMove() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view, model);

        controller.toggleCarryAt(Square.A7);
        controller.toggleCarryAt(Square.A8);
        ChessController.CompletedMove completedMove = controller.choosePromotion(PieceType.KNIGHT);

        assertEquals(Side.WHITE, completedMove.side());
        assertEquals("a8=N", completedMove.notation());
        assertNull(controller.getPromotionRequest());
        assertEquals(Piece.WHITE_KNIGHT, view.lastBoard.getPiece(Square.A8));
        assertEquals(Square.NONE, view.hiddenPieceSquare);
    }

    @Test
    void droppingOnSameSquareCancelsCarryWithoutMoving() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        controller.toggleCarryAt(Square.E2);
        String carriedSymbolAfterDrop = controller.toggleCarryAt(Square.E2);

        assertNull(carriedSymbolAfterDrop);
        assertNull(controller.consumeCompletedMove());
        assertEquals(Square.NONE, view.hiddenPieceSquare);
        assertEquals(Side.WHITE, view.lastBoard.getSideToMove());
        assertEquals(Piece.WHITE_PAWN, view.lastBoard.getPiece(Square.E2));
        assertEquals(Square.NONE, view.selectedSquare);
        assertEquals(Collections.emptyList(), view.legalTargets);
    }

    @Test
    void illegalDropKeepsPieceCarriedAndSelectionVisible() {
        RecordingChessBoardView view = new RecordingChessBoardView();
        ChessController controller = new ChessController(view);

        controller.toggleCarryAt(Square.E2);
        int updateCountAfterPickUp = view.updateCount;
        String carriedSymbolAfterDrop = controller.toggleCarryAt(Square.E5);

        assertEquals(Piece.WHITE_PAWN.getFanSymbol(), carriedSymbolAfterDrop);
        assertNull(controller.consumeCompletedMove());
        assertEquals(updateCountAfterPickUp, view.updateCount);
        assertEquals(Square.E2, view.hiddenPieceSquare);
        assertEquals(Side.WHITE, view.lastBoard.getSideToMove());
        assertEquals(Piece.WHITE_PAWN, view.lastBoard.getPiece(Square.E2));
        assertEquals(Square.E2, view.selectedSquare);
        assertEquals(List.of(Square.E3, Square.E4), view.legalTargets);
    }

    private static class RecordingChessBoardView extends ChessBoardView {
        private int updateCount;
        private Board lastBoard;
        private Square hiddenPieceSquare = Square.NONE;
        private Square selectedSquare = Square.NONE;
        private List<Square> legalTargets = Collections.emptyList();

        @Override
        public void update(Board board) {
            updateCount++;
            lastBoard = board;
        }

        @Override
        public void setHiddenPieceSquare(Square hiddenPieceSquare) {
            this.hiddenPieceSquare = hiddenPieceSquare == null ? Square.NONE : hiddenPieceSquare;
        }

        @Override
        public void showSelection(Square selectedSquare, List<Square> legalTargets) {
            this.selectedSquare = selectedSquare;
            this.legalTargets = new ArrayList<>(legalTargets);
        }
    }
}
