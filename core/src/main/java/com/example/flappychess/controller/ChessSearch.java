package com.example.flappychess.controller;

import com.example.flappychess.model.SoloSettings.Level;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Bounded incremental negamax search on an owned board, portable to TeaVM. */
final class ChessSearch {
    private static final int INF = 1_000_000;
    private static final class Node {
        final Move via;
        final int depth, beta;
        int alpha, best = -INF, index;
        List<Move> moves;
        Move bestMove;
        Node(Move via, int depth, int alpha, int beta) {
            this.via = via; this.depth = depth; this.alpha = alpha; this.beta = beta;
        }
    }
    private final Board board = new Board();
    private final ArrayDeque<Node> stack = new ArrayDeque<>();
    private final int maxDepth, maxNodes;
    private int depth = 1, nodes;
    private boolean done;
    private Move choice;

    ChessSearch(String fen, Level level, Random random) {
        board.loadFromFen(fen);
        List<Move> legal = board.legalMoves();
        choice = legal.isEmpty() ? null : legal.get(random.nextInt(legal.size()));
        maxDepth = level == Level.HARD ? 3 : 1;
        maxNodes = level == Level.HARD ? 3000 : 256;
        done = level == Level.EASY || choice == null;
        if (!done) stack.push(new Node(null, depth, -INF, INF));
    }

    void step() {
        for (int work = 0; work < 12 && !done; work++) {
            if (nodes >= maxNodes) { done = true; break; }
            Node node = stack.peek();
            if (node.moves == null) {
                nodes++;
                node.moves = new ArrayList<>(board.legalMoves());
                if (node.moves.isEmpty()) {
                    finish(board.isKingAttacked() ? -100_000 - node.depth : 0);
                    continue;
                }
                if (board.isRepetition() || board.isInsufficientMaterial() || board.getHalfMoveCounter() >= 100) {
                    finish(0); continue;
                }
                if (node.depth == 0) { finish(evaluate()); continue; }
                node.moves.sort(Comparator.comparingInt((Move move) -> order(move)).reversed());
            }
            if (node.index >= node.moves.size() || node.alpha >= node.beta) { finish(node.best); continue; }
            Move move = node.moves.get(node.index++);
            board.doMove(move);
            stack.push(new Node(move, node.depth - 1, -node.beta, -node.alpha));
        }
    }

    private void finish(int value) {
        Node node = stack.pop();
        if (node.via == null) {
            if (node.bestMove != null) choice = node.bestMove;
            if (++depth > maxDepth) done = true;
            else stack.push(new Node(null, depth, -INF, INF));
        } else {
            board.undoMove();
            Node parent = stack.peek();
            int score = -value;
            if (score > parent.best) { parent.best = score; parent.bestMove = node.via; }
            parent.alpha = Math.max(parent.alpha, score);
        }
    }

    private int order(Move move) {
        return value(board.getPiece(move.getTo()).getPieceType()) * 10
                + value(move.getPromotion().getPieceType());
    }

    private int evaluate() {
        int score = 0;
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece piece = board.getPiece(square);
            if (piece == Piece.NONE) continue;
            int rank = square.getRank().ordinal(), file = square.getFile().ordinal();
            int central = 6 - Math.abs(2 * file - 7) - Math.abs(2 * rank - 7);
            int positional = switch (piece.getPieceType()) {
                case PAWN -> 8 * (piece.getPieceSide() == Side.WHITE ? rank : 7 - rank) + central;
                case KNIGHT, BISHOP -> central * 4;
                default -> 0;
            };
            score += (piece.getPieceSide() == board.getSideToMove() ? 1 : -1)
                    * (value(piece.getPieceType()) + positional);
        }
        return score;
    }

    private static int value(PieceType type) {
        if (type == null) return 0;
        return switch (type) {
            case PAWN -> 100; case KNIGHT -> 320; case BISHOP -> 330;
            case ROOK -> 500; case QUEEN -> 900; default -> 0;
        };
    }
    boolean isDone() { return done; }
    Move choice() { return choice; }
    int nodes() { return nodes; }
}
