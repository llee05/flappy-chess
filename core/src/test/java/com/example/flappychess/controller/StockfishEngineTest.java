package com.example.flappychess.controller;

import com.example.flappychess.model.SoloSettings.ChessLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockfishEngineTest {
    @Test void allEightLevelsHaveIncreasingStrengthAndBoundedFallbacks() {
        assertEquals(8, ChessLevel.values().length);
        int lastSkill = -1, lastElo = -1, lastTime = -1;
        for (ChessLevel level : ChessLevel.values()) {
            assertTrue(level.skill() > lastSkill, level.toString());
            assertTrue(level.moveTimeMillis() > lastTime, level.toString());
            assertTrue(level.fallbackDepth() >= 0 && level.fallbackDepth() <= 3);
            assertTrue(level.fallbackNodes() >= 0 && level.fallbackNodes() <= 3000);
            if (level != ChessLevel.STOCKFISH) {
                assertTrue(level.elo() > lastElo, level.toString());
                lastElo = level.elo();
            } else assertEquals(0, level.elo(), "Full strength disables Elo limiting");
            lastSkill = level.skill();
            lastTime = level.moveTimeMillis();
        }
    }

    @Test void uciConversationWaitsForReadinessAndReturnsBestMove() {
        FakeProcess process = new FakeProcess();
        StockfishEngine engine = new StockfishEngine(process, () -> 100);
        assertEquals(List.of("uci"), process.commands);

        ChessEngine.Request request = engine.choose("test fen", ChessLevel.CLUB);
        assertEquals(ChessEngine.State.THINKING, request.state());
        process.output("id name Stockfish\nuciok");
        assertTrue(process.commands.contains("setoption name Skill Level value 8"));
        assertTrue(process.commands.contains("setoption name UCI_LimitStrength value true"));
        assertTrue(process.commands.contains("setoption name UCI_Elo value 1700"));
        assertEquals("isready", process.commands.get(process.commands.size() - 1));

        process.output("readyok");
        assertEquals("position fen test fen", process.commands.get(process.commands.size() - 2));
        assertEquals("go movetime 150", process.commands.get(process.commands.size() - 1));
        process.output("info depth 4 score cp 12\nbestmove e2e4 ponder e7e5");
        assertEquals(ChessEngine.State.READY, request.state());
        assertEquals("e2e4", request.move());
        assertEquals("", request.failure());
    }

    @Test void fullStrengthDisablesEloLimitAndTimeoutFailsTheRequest() {
        AtomicLong clock = new AtomicLong();
        FakeProcess process = new FakeProcess();
        StockfishEngine engine = new StockfishEngine(process, clock::get);
        ChessEngine.Request request = engine.choose("fen", ChessLevel.STOCKFISH);
        process.output("uciok");
        assertTrue(process.commands.contains("setoption name UCI_LimitStrength value false"));
        assertFalse(process.commands.stream().anyMatch(command -> command.startsWith("setoption name UCI_Elo")));
        clock.set(6_001);
        assertEquals(ChessEngine.State.FAILED, request.state());
        assertEquals("Stockfish timed out", request.failure());
        assertEquals("stop", process.commands.get(process.commands.size() - 1));
    }

    @Test void workerFailureFailsCurrentAndFutureRequests() {
        FakeProcess process = new FakeProcess();
        StockfishEngine engine = new StockfishEngine(process, () -> 0);
        ChessEngine.Request first = engine.choose("fen", ChessLevel.BEGINNER);
        process.failure("load failed");
        assertEquals(ChessEngine.State.FAILED, first.state());
        assertEquals("load failed", first.failure());
        ChessEngine.Request second = engine.choose("fen", ChessLevel.MASTER);
        assertEquals(ChessEngine.State.FAILED, second.state());
        assertEquals("load failed", second.failure());
    }

    private static final class FakeProcess implements UciProcess {
        final List<String> commands = new ArrayList<>();
        Consumer<String> output, failure;
        @Override public void listen(Consumer<String> output, Consumer<String> failure) {
            this.output = output;
            this.failure = failure;
        }
        @Override public void send(String command) { commands.add(command); }
        @Override public void close() { }
        void output(String line) { output.accept(line); }
        void failure(String message) { failure.accept(message); }
    }
}
