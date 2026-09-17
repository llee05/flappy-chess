package com.example.flappychess.view;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.File;
import com.github.bhlangonijr.chesslib.Rank;
import com.github.bhlangonijr.chesslib.Square;
import java.util.List;

/** Board presentation state, independent of a window or graphics context. */
public class ChessBoardView {
    private Board board;
    private Square hiddenPieceSquare = Square.NONE;
    private Square selectedSquare = Square.NONE;
    private List<Square> legalTargets = List.of();

    public static Square squareForCell(int row, int col) {
        if (row < 0 || row >= 8 || col < 0 || col >= 8) return Square.NONE;
        return Square.encode(Rank.allRanks[7 - row], File.allFiles[col]);
    }

    /** Bird coordinates retain the original top-left origin, in an 800px board. */
    public static Square squareForPoint(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0) return Square.NONE;
        return squareForCell((int) (y / 100), (int) (x / 100));
    }

    public void update(Board board) { this.board = board; }
    public Board getBoard() { return board; }
    public void setHiddenPieceSquare(Square square) { hiddenPieceSquare = square == null ? Square.NONE : square; }
    public Square getHiddenPieceSquare() { return hiddenPieceSquare; }
    public void showSelection(Square square, List<Square> targets) {
        selectedSquare = square;
        legalTargets = List.copyOf(targets);
    }
    public Square getSelectedSquare() { return selectedSquare; }
    public List<Square> getLegalTargets() { return legalTargets; }
}
