package com.example.flappychess.view;

import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChessBoardViewTest {
    @Test void mapsBirdCoordinatesWithWhiteAtBottom() {
        assertEquals(Square.A8, ChessBoardView.squareForPoint(0, 0));
        assertEquals(Square.H1, ChessBoardView.squareForPoint(799.9, 799.9));
        assertEquals(Square.E2, ChessBoardView.squareForPoint(450, 650));
        assertEquals(Square.E7, ChessBoardView.squareForPoint(450, 150));
    }

    @Test void rejectsOutsideBoardAndNonFiniteCoordinates() {
        assertEquals(Square.NONE, ChessBoardView.squareForPoint(-0.1, 0));
        assertEquals(Square.NONE, ChessBoardView.squareForPoint(0, 800));
        assertEquals(Square.NONE, ChessBoardView.squareForPoint(800, 0));
        assertEquals(Square.NONE, ChessBoardView.squareForPoint(Double.NaN, 0));
        assertEquals(Square.NONE, ChessBoardView.squareForPoint(0, Double.POSITIVE_INFINITY));
    }
}
