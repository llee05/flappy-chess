package com.example.flappychess.controller;

import com.example.flappychess.model.Birb;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.List;

/** Complete presentation state; only the host applies chess rules or declares outcomes. */
public record SessionSnapshot(String fen, Birb.Motion white, Birb.Motion black,
        long whiteMillis, long blackMillis, Square selected, Square hidden,
        List<Square> targets, ChessController.PromotionRequest promotion, Side carrier,
        List<ChessController.CompletedMove> history, String result, String reason) {
    public SessionSnapshot {
        targets = List.copyOf(targets);
        history = List.copyOf(history);
    }
}
