package com.example.flappychess.controller;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonValue.ValueType;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.example.flappychess.model.Birb;
import com.example.flappychess.model.MatchSettings;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.ArrayList;
import java.util.List;

/** Bounded, reflection-free wire format shared by the JVM tests and browser. */
final class PeerProtocol {
    static final int VERSION = 4;
    static final int MAX_MESSAGE = 262_144;

    static JsonValue packet(String type) {
        JsonValue result = new JsonValue(ValueType.object);
        put(result, "version", VERSION);
        put(result, "type", type);
        return result;
    }

    static String encode(JsonValue value) { return value.toJson(OutputType.json); }

    static JsonValue parse(String text) {
        if (text == null || text.length() > MAX_MESSAGE) throw new IllegalArgumentException("Message size");
        JsonValue value = new JsonReader().parse(text);
        if (!value.isObject() || number(value, "version", VERSION, VERSION) != VERSION) {
            throw new IllegalArgumentException("Protocol version");
        }
        return value;
    }

    static void put(JsonValue object, String key, String value) {
        object.addChild(key, value == null ? new JsonValue(ValueType.nullValue) : new JsonValue(value));
    }
    static void put(JsonValue object, String key, long value) { object.addChild(key, new JsonValue(value)); }
    static void put(JsonValue object, String key, boolean value) { object.addChild(key, new JsonValue(value)); }

    static long number(JsonValue object, String key, long min, long max) {
        JsonValue value = object.require(key);
        double n = value.asDouble();
        if (!value.isNumber() || !Double.isFinite(n) || n != Math.floor(n) || n < min || n > max) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return (long) n;
    }

    static String string(JsonValue object, String key, int max) {
        JsonValue value = object.require(key);
        if (!value.isString() || value.asString().length() > max) throw new IllegalArgumentException("Invalid " + key);
        return value.asString();
    }

    static boolean bool(JsonValue object, String key) {
        JsonValue value = object.require(key);
        if (!value.isBoolean()) throw new IllegalArgumentException("Invalid " + key);
        return value.asBoolean();
    }

    private static String nullable(JsonValue object, String key, int max) {
        return object.require(key).isNull() ? null : string(object, key, max);
    }

    static JsonValue snapshot(SessionSnapshot state) {
        JsonValue value = new JsonValue(ValueType.object);
        put(value, "fen", state.fen());
        put(value, "initialFen", state.initialFen());
        value.addChild("white", motion(state.white()));
        value.addChild("black", motion(state.black()));
        put(value, "whiteMillis", state.whiteMillis());
        put(value, "blackMillis", state.blackMillis());
        put(value, "initialMillis", state.settings().initialMillis());
        put(value, "incrementMillis", state.settings().incrementMillis());
        put(value, "selected", state.selected().name());
        put(value, "hidden", state.hidden().name());
        put(value, "carrier", state.carrier() == null ? null : state.carrier().name());
        put(value, "result", state.result());
        put(value, "reason", state.reason());
        JsonValue targets = new JsonValue(ValueType.array);
        for (Square square : state.targets()) targets.addChild(new JsonValue(square.name()));
        value.addChild("targets", targets);
        JsonValue history = new JsonValue(ValueType.array);
        for (var move : state.history()) {
            JsonValue entry = new JsonValue(ValueType.array);
            entry.addChild(new JsonValue(move.side().name()));
            entry.addChild(new JsonValue(move.notation()));
            entry.addChild(new JsonValue(move.coordinate()));
            entry.addChild(new JsonValue(move.captured().name()));
            history.addChild(entry);
        }
        value.addChild("history", history);
        JsonValue feedback = new JsonValue(ValueType.array);
        for (var event : state.feedback()) {
            JsonValue entry = new JsonValue(ValueType.object);
            put(entry, "sequence", event.sequence());
            put(entry, "kind", event.kind().name());
            put(entry, "side", event.side().name());
            put(entry, "square", event.square().name());
            feedback.addChild(entry);
        }
        value.addChild("feedback", feedback);
        JsonValue promotion = new JsonValue(ValueType.nullValue);
        if (state.promotion() != null) {
            promotion = new JsonValue(ValueType.object);
            put(promotion, "side", state.promotion().side().name());
            put(promotion, "from", state.promotion().from().name());
            put(promotion, "to", state.promotion().to().name());
        }
        value.addChild("promotion", promotion);
        return value;
    }

    static SessionSnapshot snapshot(JsonValue value) {
        String fen = string(value, "fen", 128);
        String initialFen = string(value, "initialFen", 128);
        Board initial = new Board();
        initial.loadFromFen(initialFen);
        if (initial.getKingSquare(Side.WHITE) == Square.NONE || initial.getKingSquare(Side.BLACK) == Square.NONE) {
            throw new IllegalArgumentException("Missing initial king");
        }
        Board position = new Board();
        position.loadFromFen(fen);
        if (position.getKingSquare(Side.WHITE) == Square.NONE || position.getKingSquare(Side.BLACK) == Square.NONE) {
            throw new IllegalArgumentException("Missing king");
        }
        JsonValue targets = value.require("targets"), moves = value.require("history");
        if (!targets.isArray() || targets.size > 64 || !moves.isArray() || moves.size > 2048) {
            throw new IllegalArgumentException("Invalid list");
        }
        List<Square> squares = new ArrayList<>();
        for (JsonValue square : targets) squares.add(Square.valueOf(square.asString()));
        List<ChessController.CompletedMove> history = new ArrayList<>();
        for (JsonValue move : moves) {
            if (!move.isArray() || move.size != 4) throw new IllegalArgumentException("Invalid move");
            for (JsonValue field : move) if (!field.isString()) throw new IllegalArgumentException("Invalid move field");
            Side side = Side.valueOf(move.get(0).asString());
            String notation = move.get(1).asString(), coordinate = move.get(2).asString();
            if (notation.length() > 12 || !coordinate.matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
                throw new IllegalArgumentException("Invalid move notation");
            }
            Move exact = new Move(coordinate, side);
            Piece captured = Piece.valueOf(move.get(3).asString());
            if (captured != Piece.NONE && (captured.getPieceSide() == side || captured.getPieceType() == PieceType.KING)) {
                throw new IllegalArgumentException("Invalid capture");
            }
            if (side != (history.size() % 2 == 0 ? initial.getSideToMove() : initial.getSideToMove().flip())) {
                throw new IllegalArgumentException("Invalid move order");
            }
            history.add(new ChessController.CompletedMove(side, notation, exact.getFrom(), exact.getTo(), exact.getPromotion(), captured));
        }
        JsonValue promotion = value.require("promotion");
        var request = promotion.isNull() ? null : new ChessController.PromotionRequest(
                Side.valueOf(string(promotion, "side", 5)), Square.valueOf(string(promotion, "from", 2)),
                Square.valueOf(string(promotion, "to", 2)));
        String carrier = nullable(value, "carrier", 5);
        String result = nullable(value, "result", 64), reason = nullable(value, "reason", 160);
        if ((result == null) != (reason == null)) throw new IllegalArgumentException("Incomplete result");
        JsonValue feedback = value.require("feedback");
        if (!feedback.isArray() || feedback.size > GameFeedback.LIMIT) throw new IllegalArgumentException("Invalid feedback");
        List<GameFeedback.Event> events = new ArrayList<>();
        int lastEvent = 0;
        for (JsonValue event : feedback) {
            int sequence = (int) number(event, "sequence", 1, Integer.MAX_VALUE);
            if (sequence <= lastEvent) throw new IllegalArgumentException("Feedback order");
            Square square = Square.valueOf(string(event, "square", 2));
            if (square == Square.NONE) throw new IllegalArgumentException("Feedback square");
            events.add(new GameFeedback.Event(sequence, GameFeedback.Kind.valueOf(string(event, "kind", 12)),
                    Side.valueOf(string(event, "side", 5)), square));
            lastEvent = sequence;
        }
        return new SessionSnapshot(fen, motion(value.require("white")), motion(value.require("black")),
                number(value, "whiteMillis", 0, MatchSettings.MAX_REMAINING_MILLIS),
                number(value, "blackMillis", 0, MatchSettings.MAX_REMAINING_MILLIS),
                Square.valueOf(string(value, "selected", 4)), Square.valueOf(string(value, "hidden", 4)),
                squares, request, carrier == null ? null : Side.valueOf(carrier), history, result, reason, events,
                new MatchSettings(number(value, "initialMillis", 0, MatchSettings.MAX_INITIAL_MILLIS),
                        number(value, "incrementMillis", 0, MatchSettings.MAX_INCREMENT_MILLIS)), initialFen);
    }

    private static JsonValue motion(Birb.Motion motion) {
        JsonValue array = new JsonValue(ValueType.array);
        for (double n : new double[] {motion.x(), motion.y(), motion.velocityX(), motion.velocityY()}) {
            array.addChild(new JsonValue(n));
        }
        return array;
    }

    private static Birb.Motion motion(JsonValue array) {
        if (!array.isArray() || array.size != 4) throw new IllegalArgumentException("Invalid bird");
        for (JsonValue element : array) if (!element.isNumber()) throw new IllegalArgumentException("Invalid bird number");
        double[] values = array.asDoubleArray();
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite bird");
        if (values[0] < 0 || values[0] > 750 || values[1] < 0 || values[1] > 750
                || Math.abs(values[2]) > 8 || Math.abs(values[3]) > 8) throw new IllegalArgumentException("Bird bounds");
        return new Birb.Motion(values[0], values[1], values[2], values[3]);
    }
}
