package com.example.flappychess.web;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.controller.FlightSchool;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.controller.MatchSetup;
import com.github.bhlangonijr.chesslib.Side;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Touch intent follows the same input processor and ownership rules as keyboard play. */
final class MobileControls {
    @JSFunctor interface Key extends JSObject { void run(String key, boolean down); }
    @JSFunctor interface Release extends JSObject { void run(); }

    static void install(Runnable release) {
        bind((name, down) -> {
            int key = switch (name) {
                case "A" -> Keys.A; case "D" -> Keys.D; case "SPACE" -> Keys.SPACE;
                case "LEFT" -> Keys.LEFT; case "RIGHT" -> Keys.RIGHT; case "UP" -> Keys.UP;
                case "DOWN" -> Keys.DOWN; case "ENTER" -> Keys.ENTER;
                case "E" -> Keys.E; case "M" -> Keys.M; case "P" -> Keys.P;
                case "Q" -> Keys.Q; case "R" -> Keys.R; case "B" -> Keys.B; case "N" -> Keys.N;
                case "V" -> Keys.V; case "X" -> Keys.X; case "S" -> Keys.S;
                case "C" -> Keys.C; case "W" -> Keys.W; case "F" -> Keys.F; case "T" -> Keys.T;
                case "HOME" -> Keys.HOME; case "END" -> Keys.END;
                default -> Keys.UNKNOWN;
            };
            if (key == Keys.UNKNOWN || Gdx.input.getInputProcessor() == null) return;
            if (down) Gdx.input.getInputProcessor().keyDown(key);
            else Gdx.input.getInputProcessor().keyUp(key);
        }, release::run);
    }

    @JSBody(params = {"key", "release"}, script = "window.FlappyMobile.bind(key, release);")
    private static native void bind(Key key, Release release);

    @JSBody(script = "return document.body.classList.contains('mobile');")
    static native boolean isMobile();

    @JSBody(script = "return Math.max(1, Math.round(document.getElementById('canvas').getBoundingClientRect().width));")
    static native int width();

    @JSBody(script = "return Math.max(1, Math.round(document.getElementById('canvas').getBoundingClientRect().height));")
    static native int height();

    static void resizeCanvas() {
        int width = width(), height = height();
        if (width != Gdx.graphics.getWidth() || height != Gdx.graphics.getHeight()) {
            Gdx.graphics.setWindowedMode(width, height);
        }
    }

    static void present(GameSession game, FlightSchool school, MatchSetup setup, String ownSide) {
        show(game.getChess().getSideToMove().name(), clock(game, Side.WHITE), clock(game, Side.BLACK),
                game.getCarrier() == null ? "" : game.getCarrier().name(), game.getChess().hasPendingPromotion(),
                game.getResult(), game.getReason(), ownSide, game.getSoloSettings() != null,
                school != null, school != null && school.isComplete(), school == null ? "" : school.title(),
                school == null ? "" : school.instruction());
        if (setup != null) showSetup(setup.opponentLabel(), setup.colorLabel(), setup.chessLabel(),
                setup.flightLabel(), setup.timeLabel(), setup.incrementLabel());
    }

    private static String clock(GameSession game, Side side) {
        if (!game.isTimed()) return "Untimed";
        long seconds = game.getClock().getRemainingSeconds(side);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    @JSBody(params = {"turn", "white", "black", "carrier", "promotion", "result", "reason", "ownSide", "solo", "tutorial", "complete", "title", "instruction"},
            script = "window.FlappyMobile.present({turn: turn, white: white, black: black, carrier: carrier, promotion: promotion, result: result, reason: reason, ownSide: ownSide, solo: solo, tutorial: tutorial, complete: complete, title: title, instruction: instruction});")
    private static native void show(String turn, String white, String black, String carrier, boolean promotion,
            String result, String reason, String ownSide, boolean solo, boolean tutorial, boolean complete,
            String title, String instruction);

    @JSBody(params = {"opponent", "color", "chess", "flight", "time", "increment"},
            script = "window.FlappyMobile.setup({opponent: opponent, color: color, chess: chess, flight: flight, time: time, increment: increment});")
    private static native void showSetup(String opponent, String color, String chess, String flight, String time, String increment);
}
