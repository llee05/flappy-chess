package com.example.flappychess.controller;

import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
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
            if (level != ChessLevel.PHOENIX) {
                assertTrue(level.elo() > lastElo, level.toString());
                lastElo = level.elo();
            } else assertEquals(0, level.elo(), "Full strength disables Elo limiting");
            lastSkill = level.skill();
            lastTime = level.moveTimeMillis();
        }
    }

    @Test void birdNamedLevelsKeepTheirStockfishAndFallbackSettings() {
        // name, skill, Elo, think time, fallback depth, fallback nodes; weakest first.
        Object[][] expected = {
                {"EGG", 0, 1320, 50, 0, 0},
                {"HATCHLING", 2, 1400, 75, 1, 64},
                {"SPARROW", 5, 1550, 100, 1, 128},
                {"PIGEON", 8, 1700, 150, 2, 256},
                {"RAVEN", 11, 1900, 225, 2, 512},
                {"HAWK", 14, 2100, 350, 2, 1000},
                {"EAGLE", 17, 2400, 550, 3, 2000},
                {"PHOENIX", 20, 0, 1000, 3, 3000},
        };
        ChessLevel[] levels = ChessLevel.values();
        assertEquals(expected.length, levels.length);
        for (int i = 0; i < levels.length; i++) {
            ChessLevel level = levels[i];
            assertEquals(expected[i][0], level.name());
            assertEquals(List.of(expected[i]).subList(1, 6), List.of(level.skill(), level.elo(),
                    level.moveTimeMillis(), level.fallbackDepth(), level.fallbackNodes()), level.name());
        }
    }

    @Test void desktopSetupCyclesBirdLevelsFromTheDefault() {
        MatchSetup setup = new MatchSetup(MatchSettings.DEFAULT, SoloSettings.DEFAULT);
        assertEquals("PIGEON", setup.chessLabel());
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < ChessLevel.values().length; i++) {
            setup.changeChess();
            labels.add(setup.chessLabel());
        }
        assertEquals(List.of("RAVEN", "HAWK", "EAGLE", "PHOENIX", "EGG", "HATCHLING", "SPARROW", "PIGEON"), labels);
        assertEquals(ChessLevel.PIGEON, setup.soloSettings().chess());
    }

    @Test void uciConversationWaitsForReadinessAndReturnsBestMove() {
        FakeProcess process = new FakeProcess();
        StockfishEngine engine = new StockfishEngine(process, () -> 100);
        assertEquals(List.of("uci"), process.commands);

        ChessEngine.Request request = engine.choose("test fen", ChessLevel.PIGEON);
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
        ChessEngine.Request request = engine.choose("fen", ChessLevel.PHOENIX);
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
        ChessEngine.Request first = engine.choose("fen", ChessLevel.HATCHLING);
        process.failure("load failed");
        assertEquals(ChessEngine.State.FAILED, first.state());
        assertEquals("load failed", first.failure());
        ChessEngine.Request second = engine.choose("fen", ChessLevel.EAGLE);
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
