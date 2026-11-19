package com.example.flappychess.model;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessGameModelTest {
    @Test
    void startsWithStandardChessPosition() {
        ChessGameModel model = new ChessGameModel();

        assertEquals(ChessGameModel.GameStatus.ACTIVE, model.getGameStatus());
        assertNull(model.getWinner());
        assertEquals(Side.WHITE, model.getSideToMove());
        assertEquals(20, model.getLegalMoves().size());
        assertEquals(Piece.WHITE_KING, model.getPiece(Square.E1));
        assertEquals(Piece.BLACK_KING, model.getPiece(Square.E8));
        assertEquals(Piece.WHITE_PAWN, model.getPiece(Square.E2));
        assertEquals(Piece.BLACK_ROOK, model.getPiece(Square.A8));
        assertEquals(Piece.NONE, model.getPiece(Square.E4));
    }

    @Test
    void exposesLegalMovesFromASquareAndTheirTargets() {
        ChessGameModel model = new ChessGameModel();

        List<Move> moves = model.getLegalMovesFrom(Square.E2);
        List<Square> targets = model.getLegalTargetSquares(Square.E2);

        assertEquals(2, moves.size());
        assertEquals(List.of(Square.E3, Square.E4), targets);
    }

    @Test
    void identifiesPiecesForCurrentSideOnly() {
        ChessGameModel model = new ChessGameModel();

        assertTrue(model.hasCurrentSidePiece(Square.E2));
        assertFalse(model.hasCurrentSidePiece(Square.E7));
        assertFalse(model.hasCurrentSidePiece(Square.E4));
        assertFalse(model.hasCurrentSidePiece(Square.NONE));
    }

    @Test
    void currentSidePieceChangesAfterLegalMove() {
        ChessGameModel model = new ChessGameModel();

        assertTrue(model.tryMove(Square.E2, Square.E4));

        assertFalse(model.hasCurrentSidePiece(Square.E4));
        assertTrue(model.hasCurrentSidePiece(Square.E7));
    }

    @Test
    void legalMoveUpdatesBoardSideToMoveAndCachedBoardState() {
        ChessGameModel model = new ChessGameModel();

        assertTrue(model.tryMove(Square.E2, Square.E4));

        assertEquals(Side.BLACK, model.getSideToMove());
        assertEquals(Piece.NONE, model.getPiece(Square.E2));
        assertEquals(Piece.WHITE_PAWN, model.getPiece(Square.E4));
        assertEquals(Piece.NONE, model.getBoardState()[Square.E2.getRank().ordinal()][Square.E2.getFile().ordinal()]);
        assertEquals(Piece.WHITE_PAWN, model.getBoardState()[Square.E4.getRank().ordinal()][Square.E4.getFile().ordinal()]);
    }

    @Test
    void illegalMoveLeavesPositionUnchanged() {
        ChessGameModel model = new ChessGameModel();

        assertFalse(model.tryMove(Square.E2, Square.E5));

        assertEquals(Side.WHITE, model.getSideToMove());
        assertEquals(Piece.WHITE_PAWN, model.getPiece(Square.E2));
        assertEquals(Piece.NONE, model.getPiece(Square.E5));
    }

    @Test
    void tryMoveObjectOnlyAcceptsLegalMoves() {
        ChessGameModel model = new ChessGameModel();

        assertTrue(model.tryMove(new Move(Square.G1, Square.F3)));
        assertEquals(Piece.WHITE_KNIGHT, model.getPiece(Square.F3));

        assertFalse(model.tryMove(new Move(Square.G8, Square.G6)));
        assertEquals(Piece.BLACK_KNIGHT, model.getPiece(Square.G8));
        assertEquals(Piece.NONE, model.getPiece(Square.G6));
    }

    @Test
    void legalTargetsDeduplicatePromotionMoves() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");

        assertEquals(List.of(Square.A8), model.getLegalTargetSquares(Square.A7));
    }

    @Test
    void pawnPromotionDefaultsToQueenWhenOnlySquaresAreProvided() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");

        assertTrue(model.tryMove(Square.A7, Square.A8));

        assertEquals(Piece.WHITE_QUEEN, model.getPiece(Square.A8));
        assertEquals(Piece.WHITE_QUEEN, model.getBoardState()[Square.A8.getRank().ordinal()][Square.A8.getFile().ordinal()]);
    }

    @Test
    void exposesSpecificPromotionMoves() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");

        Move knightPromotion = model.getPromotionMove(Square.A7, Square.A8, PieceType.KNIGHT);

        assertEquals(Piece.WHITE_KNIGHT, knightPromotion.getPromotion());
        assertEquals(4, model.getPromotionMoves(Square.A7, Square.A8).size());
    }

    @Test
    void tryMoveObjectUsesRequestedPromotionPiece() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");

        assertTrue(model.tryMove(new Move(Square.A7, Square.A8, Piece.WHITE_KNIGHT)));

        assertEquals(Piece.WHITE_KNIGHT, model.getPiece(Square.A8));
        assertEquals(Piece.WHITE_KNIGHT, model.getBoardState()[Square.A8.getRank().ordinal()][Square.A8.getFile().ordinal()]);
    }

    @Test
    void detectsCheckmateAndWinner() {
        ChessGameModel model = new ChessGameModel();

        assertTrue(model.tryMove(Square.F2, Square.F3));
        assertTrue(model.tryMove(Square.E7, Square.E5));
        assertTrue(model.tryMove(Square.G2, Square.G4));
        assertTrue(model.tryMove(Square.D8, Square.H4));

        assertEquals(ChessGameModel.GameStatus.CHECKMATE, model.getGameStatus());
        assertEquals(Side.BLACK, model.getWinner());
    }

    @Test
    void detectsStalemateAsDrawWithoutWinner() {
        ChessGameModel model = new ChessGameModel();
        model.getBoard().loadFromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");

        assertEquals(ChessGameModel.GameStatus.STALEMATE, model.getGameStatus());
        assertNull(model.getWinner());
    }

    @Test
    void loneKingHasInsufficientMatingMaterial() {
        ChessGameModel model = new ChessGameModel();
        model.restorePosition("7k/8/8/8/8/8/R7/K7 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        model.restorePosition("k7/7r/8/8/8/8/8/K7 b - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));
    }

    @Test
    void knightMatingMaterialAdjudication() {
        ChessGameModel model = new ChessGameModel();

        // K+N vs K: cannot mate bare king
        model.restorePosition("8/8/8/8/8/4k3/4N3/4K3 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N vs K+Q: cannot mate K+Q
        model.restorePosition("8/8/8/8/8/4k3/4q3/4K1N1 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N vs K+P: helpmate possible
        model.restorePosition("7k/7p/8/8/8/8/5N2/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N vs K+N: helpmate possible
        model.restorePosition("7k/7n/8/8/8/8/5N2/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N vs K+B: helpmate possible
        model.restorePosition("7k/7b/8/8/8/8/5N2/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N vs K+R: helpmate possible
        model.restorePosition("7k/7r/8/8/8/8/5N2/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N+N vs K: helpmate possible
        model.restorePosition("7k/8/8/8/8/8/4NN2/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+N+B vs K: forced mate possible
        model.restorePosition("7k/8/8/8/8/8/4NB2/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));
    }

    @Test
    void bishopMatingMaterialAdjudication() {
        ChessGameModel model = new ChessGameModel();

        // K+B vs K: cannot mate bare king
        model.restorePosition("8/8/8/8/8/4k3/4B3/4K3 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B vs K+B same color (both dark): cannot mate
        model.restorePosition("5b1k/8/8/8/8/8/8/2B1K3 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B vs K+B opposite color (c1 dark, e8 light): helpmate possible
        model.restorePosition("4b2k/8/8/8/8/8/8/2B1K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B vs K+N: helpmate possible
        model.restorePosition("7k/7n/8/8/8/8/8/2B1K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B vs K+P: helpmate possible
        model.restorePosition("7k/7p/8/8/8/8/8/2B1K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B vs K+R: cannot mate
        model.restorePosition("7k/7r/8/8/8/8/8/2B1K3 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B vs K+Q: cannot mate
        model.restorePosition("7k/7q/8/8/8/8/8/2B1K3 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B+B opposite color vs K: forced mate possible
        model.restorePosition("7k/8/8/8/8/8/8/1BB1K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // K+B+B same color vs K: cannot mate bare king
        model.restorePosition("7k/8/8/8/8/4B3/8/2B1K3 w - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));
    }

    @Test
    void majorPiecesAndPawnsAlwaysHaveSufficientMaterial() {
        ChessGameModel model = new ChessGameModel();

        // Pawns can promote
        model.restorePosition("7k/8/8/8/8/8/4P3/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // Black pawn can promote
        model.restorePosition("4k3/4p3/8/8/8/8/8/7K b - - 0 1");
        assertFalse(model.hasSufficientMatingMaterial(Side.WHITE));
        assertTrue(model.hasSufficientMatingMaterial(Side.BLACK));

        // Rooks can force mate
        model.restorePosition("7k/8/8/8/8/8/4R3/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));

        // Queens can force mate
        model.restorePosition("7k/8/8/8/8/8/4Q3/4K3 w - - 0 1");
        assertTrue(model.hasSufficientMatingMaterial(Side.WHITE));
        assertFalse(model.hasSufficientMatingMaterial(Side.BLACK));
    }
}
