package com.example.flappychess.model;

import com.github.bhlangonijr.chesslib.Bitboard;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChessGameModel {
    private final Board board;
    private final Piece[][] boardState;

    public enum GameStatus {
        ACTIVE,
        CHECKMATE,
        STALEMATE,
        DRAW
    }

    public ChessGameModel() {
        board = new Board();
        boardState = new Piece[8][8];
        this.update();
    }

    public Board getBoard() {
        return board;
    }

    public void restorePosition(String fen) {
        board.loadFromFen(fen);
        update();
    }

    private void update() {
        // could be redundant; consider moving this logic to view
        for (Piece[] row : boardState) {
            Arrays.fill(row, Piece.NONE);
        }

        for (Square square : Square.values()) {
            if (square == Square.NONE) {
                continue;
            }
            Piece piece = board.getPiece(square);
            int row_index = square.getRank().ordinal();
            int col_index = square.getFile().ordinal();
            boardState[row_index][col_index] = piece;
        }
    }

    public Piece[][] getBoardState() {
        return boardState;
    }



    public List<Move> getLegalMoves() {
        return board.legalMoves();
    }

    public List<Move> getLegalMovesFrom(Square from) {
        return getLegalMoves().stream()
                .filter(move -> move.getFrom() == from)
                .collect(Collectors.toList());
    }

    public List<Move> getLegalMovesBetween(Square from, Square to) {
        return getLegalMovesFrom(from).stream()
                .filter(move -> move.getTo() == to)
                .collect(Collectors.toList());
    }

    public List<Move> getPromotionMoves(Square from, Square to) {
        return getLegalMovesBetween(from, to).stream()
                .filter(move -> move.getPromotion() != Piece.NONE)
                .collect(Collectors.toList());
    }

    public List<Square> getLegalTargetSquares(Square from) {
        return getLegalMovesFrom(from).stream()
                .map(Move::getTo)
                .distinct()
                .collect(Collectors.toList());
    }

    public Piece getPiece(Square square) {
        return board.getPiece(square);
    }

    public Side getSideToMove() {
        return board.getSideToMove();
    }

    public GameStatus getGameStatus() {
        if (board.isMated()) {
            return GameStatus.CHECKMATE;
        }

        if (board.isStaleMate()) {
            return GameStatus.STALEMATE;
        }

        if (board.isDraw()) {
            return GameStatus.DRAW;
        }

        return GameStatus.ACTIVE;
    }

    public Side getWinner() {
        return getGameStatus() == GameStatus.CHECKMATE ? getSideToMove().flip() : null;
    }

    public boolean hasCurrentSidePiece(Square square) {
        if (square == Square.NONE) {
            return false;
        }

        Piece piece = getPiece(square);
        return piece != Piece.NONE && piece.getPieceSide() == getSideToMove();
    }

    public boolean tryMove(Square from, Square to) {
        Move legalMove = findLegalMove(from, to);
        if (legalMove == null) {
            return false;
        }

        board.doMove(legalMove);
        update();
        return true;
    }

    public boolean tryMove(Move move) {
        if (getLegalMoves().contains(move)) {
            board.doMove(move);
            update();
            return true;
        }
        return false;
    }

    public Move getLegalMove(Square from, Square to) {
        return findLegalMove(from, to);
    }

    public Move getPromotionMove(Square from, Square to, PieceType promotionType) {
        return getPromotionMoves(from, to).stream()
                .filter(move -> move.getPromotion().getPieceType() == promotionType)
                .findFirst()
                .orElse(null);
    }

    private Move findLegalMove(Square from, Square to) {
        Move fallback = null;

        for (Move move : getLegalMovesFrom(from)) {
            if (move.getTo() != to) {
                continue;
            }

            if (move.getPromotion() == Piece.NONE) {
                return move;
            }

            if (fallback == null) {
                fallback = move;
            }

            if (move.getPromotion().getPieceType() == PieceType.QUEEN) {
                return move;
            }
        }

        return fallback;
    }

    /**
     * Determines whether the specified side has sufficient material to checkmate the opponent
     * by any possible series of legal moves (FIDE Laws of Chess, Article 6.9).
     *
     * @param side the side to check for mating capability
     * @return true if checkmate by this side is legally possible against the opponent's pieces
     */
    public boolean hasSufficientMatingMaterial(Side side) {
        long pawns = board.getBitboard(Piece.make(side, PieceType.PAWN));
        long rooks = board.getBitboard(Piece.make(side, PieceType.ROOK));
        long queens = board.getBitboard(Piece.make(side, PieceType.QUEEN));
        if ((pawns | rooks | queens) != 0L) {
            return true;
        }

        long sidePieces = board.getBitboard(side);
        long knights = board.getBitboard(Piece.make(side, PieceType.KNIGHT));
        if (knights != 0L) {
            if (Long.bitCount(sidePieces) > 2) {
                return true;
            }
            Side opp = side.flip();
            long oppPawns = board.getBitboard(Piece.make(opp, PieceType.PAWN));
            long oppKnights = board.getBitboard(Piece.make(opp, PieceType.KNIGHT));
            long oppBishops = board.getBitboard(Piece.make(opp, PieceType.BISHOP));
            long oppRooks = board.getBitboard(Piece.make(opp, PieceType.ROOK));
            return (oppPawns | oppKnights | oppBishops | oppRooks) != 0L;
        }

        long bishops = board.getBitboard(Piece.make(side, PieceType.BISHOP));
        if (bishops != 0L) {
            long allBishops = board.getBitboard(Piece.WHITE_BISHOP) | board.getBitboard(Piece.BLACK_BISHOP);
            boolean sameColor = (allBishops & Bitboard.darkSquares) == 0L || (allBishops & Bitboard.lightSquares) == 0L;
            long allPawns = board.getBitboard(Piece.WHITE_PAWN) | board.getBitboard(Piece.BLACK_PAWN);
            long allKnights = board.getBitboard(Piece.WHITE_KNIGHT) | board.getBitboard(Piece.BLACK_KNIGHT);
            return !sameColor || allPawns != 0L || allKnights != 0L;
        }

        return false;
    }
}
