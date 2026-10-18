package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.pgn.PgnHolder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameRecordTest {
    private final AtomicLong time = new AtomicLong();
    private GameSession session(String fen) {
        ChessGameModel model = new ChessGameModel();
        if (fen != null) model.restorePosition(fen);
        return new GameSession(model, MatchSettings.minutes(5, 0), time::get);
    }
    private void press(GameSession game, int key) { game.keyDown(key); game.keyUp(key); }
    private void over(Birb bird, Square square) {
        bird.restore(new Birb.Motion(square.getFile().ordinal() * 100 + 25,
                (7 - square.getRank().ordinal()) * 100 + 25, 0, 0));
    }
    private void move(GameSession game, Square from, Square to) {
        boolean white = game.getChess().getSideToMove() == Side.WHITE;
        Birb bird = white ? game.getWhite() : game.getBlack();
        int key = white ? Keys.E : Keys.ENTER;
        over(bird, from);
        press(game, key);
        over(bird, to);
        press(game, key);
    }
    private void roundTrip(GameSession game) throws Exception {
        SessionSnapshot before = game.snapshot();
        GameRecord record = new GameRecord(game);
        assertEquals(before.fen(), record.fenAt(game.getHistory().size()));
        PgnHolder pgn = new PgnHolder();
        pgn.loadPgn(record.pgn());
        assertEquals(1, pgn.getGames().size());
        var imported = pgn.getGames().get(0);
        imported.loadMoveText();
        assertEquals(game.getHistory().size(), imported.getHalfMoves().size());
        assertEquals(before.fen(), imported.getHalfMoves().getFen());
        assertEquals(before, game.snapshot());
    }

    @Test void exactMovesAndCapturesReconstructEveryPositionAndExportValidPgn() throws Exception {
        GameSession game = session(null);
        String start = game.snapshot().fen();
        move(game, Square.E2, Square.E4);
        String afterFirst = game.snapshot().fen();
        move(game, Square.D7, Square.D5);
        move(game, Square.E4, Square.D5);
        GameRecord record = new GameRecord(game);
        assertEquals(start, record.fenAt(0));
        assertEquals(afterFirst, record.fenAt(1));
        assertEquals("e4d5", record.moves().get(2).coordinate());
        assertEquals(Piece.BLACK_PAWN, record.moves().get(2).captured());
        assertEquals(0, GameRecord.capturedCount(record.moves(), 2, Side.WHITE, PieceType.PAWN));
        assertEquals(1, GameRecord.capturedCount(record.moves(), 3, Side.WHITE, PieceType.PAWN));
        assertTrue(record.pgn().contains("1. e4 d5 2. exd5 *"));
        assertTrue(record.pgn().contains("[TimeControl \"300+0\"]"));
        roundTrip(game);
        move(game, Square.G8, Square.F6);
        assertEquals(3, record.moves().size(), "An exported record is immutable");
    }

    @Test void enPassantAndCapturePromotionRecordTheActualCapturedPiece() throws Exception {
        GameSession ep = session("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        move(ep, Square.E5, Square.D6);
        assertEquals(Piece.BLACK_PAWN, ep.getHistory().get(0).captured());
        roundTrip(ep);
        GameSession promotion = session("k4r2/4P3/8/8/8/8/8/4K3 w - - 0 1");
        move(promotion, Square.E7, Square.F8);
        assertTrue(promotion.getHistory().isEmpty());
        press(promotion, Keys.N);
        var chosen = promotion.getHistory().get(0);
        assertEquals(Piece.WHITE_KNIGHT, chosen.promotion());
        assertEquals(Piece.BLACK_ROOK, chosen.captured());
        assertEquals("e7f8n", chosen.coordinate());
        roundTrip(promotion);
    }

    @Test void castlingAndBlackStartingPositionsPreserveRooksAndMoveNumbers() throws Exception {
        GameSession castling = session("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        move(castling, Square.E1, Square.G1);
        assertEquals("O-O", castling.getHistory().get(0).notation());
        roundTrip(castling);
        GameSession black = session("k7/8/8/8/8/8/3p4/7K b - - 0 37");
        move(black, Square.D2, Square.D1);
        press(black, Keys.Q);
        String pgn = new GameRecord(black).pgn();
        assertTrue(pgn.contains("[SetUp \"1\"]"));
        assertTrue(pgn.contains("37... d1=Q+ *"));
        roundTrip(black);
    }

    @Test void reviewNavigationDoesNotChangeResultClocksBirdsOrCarriedPieces() {
        GameSession game = session(null);
        move(game, Square.E2, Square.E4);
        move(game, Square.D7, Square.D5);
        move(game, Square.E4, Square.D5);
        time.set(Duration.ofMinutes(5).toNanos());
        game.update(0);
        assertTrue(game.isGameOver());
        SessionSnapshot finished = game.snapshot();
        PostgameReview review = new PostgameReview();
        review.bind(game);
        assertTrue(review.keyDown(Keys.V));
        assertEquals(3, review.getPly());
        review.keyDown(Keys.LEFT);
        assertEquals(review.getRecord().fenAt(2), review.getView().getBoard().getFen());
        review.keyDown(Keys.HOME);
        assertEquals(0, review.getPly());
        review.keyDown(Keys.LEFT);
        assertEquals(0, review.getPly());
        review.keyDown(Keys.END);
        assertEquals(3, review.getPly());
        assertEquals(finished, game.snapshot());
        review.bind(session(null));
        assertFalse(review.isActive());
        assertNull(review.getRecord());

        time.set(0);
        GameSession carried = session(null);
        press(carried, Keys.E);
        time.set(Duration.ofMinutes(5).toNanos());
        carried.update(0);
        review.bind(carried);
        review.keyDown(Keys.V);
        assertEquals(Square.NONE, review.getView().getHiddenPieceSquare());
        assertEquals(Piece.WHITE_PAWN, review.getView().getBoard().getPiece(Square.E2));
        assertEquals(Square.E2, carried.getBoardView().getHiddenPieceSquare());
        assertTrue(new GameRecord(carried).pgn().endsWith("0-1\n"));
    }
}
