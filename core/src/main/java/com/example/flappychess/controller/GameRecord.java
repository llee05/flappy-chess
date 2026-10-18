package com.example.flappychess.controller;

import com.example.flappychess.controller.ChessController.CompletedMove;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import java.util.ArrayList;
import java.util.List;

/** Immutable chess history. Replaying or exporting never mutates the session. */
public final class GameRecord {
    private final String initialFen, result;
    private final MatchSettings settings;
    private final List<CompletedMove> moves;
    private final List<String> positions = new ArrayList<>();

    public GameRecord(GameSession game) {
        initialFen = game.getChess().getInitialFen();
        settings = game.getSettings();
        moves = List.copyOf(game.getHistory());
        result = game.getResult() == null ? "*" : game.getResult().equals("White wins") ? "1-0"
                : game.getResult().equals("Black wins") ? "0-1" : "1/2-1/2";
        Board board = new Board();
        board.loadFromFen(initialFen);
        positions.add(board.getFen());
        for (CompletedMove move : moves) {
            if (!board.doMove(move.move(), true)) throw new IllegalArgumentException("Invalid recorded move");
            positions.add(board.getFen());
        }
    }

    public List<CompletedMove> moves() { return moves; }
    public String fenAt(int ply) { return positions.get(ply); }

    public static int capturedCount(List<CompletedMove> moves, int ply, Side by, PieceType type) {
        int count = 0;
        for (int i = 0; i < Math.min(ply, moves.size()); i++) {
            CompletedMove move = moves.get(i);
            if (move.side() == by && move.captured() != Piece.NONE && move.captured().getPieceType() == type) count++;
        }
        return count;
    }

    public String pgn() {
        StringBuilder text = new StringBuilder();
        tag(text, "Event", "Flappy Chess");
        tag(text, "Site", "?");
        tag(text, "Date", "????.??.??");
        tag(text, "Round", "?");
        tag(text, "White", "White");
        tag(text, "Black", "Black");
        tag(text, "Result", result);
        tag(text, "TimeControl", settings.pgnTimeControl());
        if (!initialFen.equals(new Board().getFen())) {
            tag(text, "SetUp", "1");
            tag(text, "FEN", initialFen);
        }
        text.append('\n');
        int number = Integer.parseInt(initialFen.split(" ")[5]);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            CompletedMove move = moves.get(i);
            if (move.side() == Side.WHITE) tokens.add(number + ".");
            else if (i == 0) tokens.add(number + "...");
            tokens.add(move.notation());
            if (move.side() == Side.BLACK) number++;
        }
        tokens.add(result);
        int column = 0;
        for (String token : tokens) {
            if (column > 0) {
                if (column + token.length() + 1 > 80) { text.append('\n'); column = 0; }
                else { text.append(' '); column++; }
            }
            text.append(token);
            column += token.length();
        }
        return text.append('\n').toString();
    }

    private static void tag(StringBuilder text, String name, String value) {
        text.append('[').append(name).append(" \"").append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                .append("\"]\n");
    }
}
