package com.example.flappychess.web;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.FlappyChessGame;
import com.example.flappychess.model.MatchSettings;
import com.example.flappychess.model.SoloSettings;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.controller.MatchSetup;
import com.example.flappychess.controller.PostgameReview;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;

public final class WebLauncher {
    public static void main(String[] args) {
        Window.current().getDocument().getElementById("play").addEventListener("click", event -> start(false));
        Window.current().getDocument().getElementById("tutorial").addEventListener("click", event -> start(true));
        Window.current().getDocument().getElementById("solo").addEventListener("click", event -> start(false, true));
        PeerNetwork.install(WebLauncher::startPeer);
        enablePlay();
    }

    private static void start(boolean tutorial) {
        start(tutorial, false);
    }

    private static void start(boolean tutorial, boolean solo) {
        showLoading();
        FlappyChessGame game = solo
                ? new FlappyChessGame(false, WebLauncher::leaveGame, selectedSettings(), selectedSolo(), WebLauncher::exportPgn)
                : new FlappyChessGame(false, WebLauncher::leaveGame, tutorial, selectedSettings(), WebLauncher::exportPgn);
        launch(new ApplicationAdapter() {
            @Override public void create() {
                game.create();
                MobileControls.install(game::releaseInput);
                catchGameKeys();
                describeTimeControl(game.getSession().getSettings().label());
                ready(tutorial);
            }
            @Override public void render() {
                MobileControls.resizeCanvas();
                game.setBoardOnly(MobileControls.isMobile());
                game.render();
                var school = game.getFlightSchool();
                describeLesson(school == null ? "" : school.getStage().name(),
                        school == null ? "" : school.title() + ". " + school.instruction());
                describeFeedback(game.feedbackMessage(), game.isSoundEnabled());
                describeTimeControl(game.getSession().getSettings().label());
                describeSetup(game.getMatchSetup());
                describeHistory(game.getSession(), game.getReview());
                describeSeries(game.getSession());
                describeSolo(game.getSession());
                MobileControls.present(game.getSession(), school, game.getMatchSetup(), "");
            }
            @Override public void resize(int width, int height) { game.resize(width, height); }
            @Override public void pause() { game.pause(); }
            @Override public void resume() { game.resume(); }
            @Override public void dispose() { game.dispose(); }
        });
    }

    private static void startPeer(boolean host) {
        launch(new PeerGame(host, host ? selectedSettings() : MatchSettings.DEFAULT));
    }

    static MatchSettings selectedSettings() { return MatchSettings.minutes(selectedMinutes(), selectedIncrement()); }

    private static SoloSettings selectedSolo() {
        return new SoloSettings(Side.valueOf(soloChoice("side")), SoloSettings.Level.valueOf(soloChoice("chess")),
                SoloSettings.Level.valueOf(soloChoice("flight")));
    }

    @JSBody(params = "name", script = "return window.FlappySettings.solo()[name];")
    private static native String soloChoice(String name);

    private static void describeSolo(GameSession game) {
        var solo = game.getSoloSettings();
        soloSummary(solo != null, solo == null ? "" : game.getSeries().sideFor(false).name(),
                solo == null ? "" : solo.chess().name(), solo == null ? "" : solo.flight().name(), game.getAiPhase(),
                game.getCarrier() == null ? "" : game.getCarrier().name());
    }

    @JSBody(params = {"enabled", "side", "chess", "flight", "phase", "carrier"}, script = "const el = document.getElementById('solo-summary'); el.dataset.side = side; el.dataset.chess = chess; el.dataset.flight = flight; el.dataset.phase = phase; el.dataset.carrier = carrier; if (enabled) document.body.dataset.mode = 'solo'; const text = enabled ? 'Computer plays ' + side + '. Chess ' + chess + ', flying ' + flight + '. ' + phase + '. Either keyboard layout controls your bird.' : ''; if (el.textContent !== text) el.textContent = text;")
    private static native void soloSummary(boolean enabled, String side, String chess, String flight, String phase, String carrier);

    @JSBody(script = "return window.FlappySettings.read().minutes;")
    private static native int selectedMinutes();

    @JSBody(script = "return window.FlappySettings.read().increment;")
    private static native int selectedIncrement();

    @JSBody(params = "label", script = "document.body.dataset.timeControl = label;")
    static native void describeTimeControl(String label);

    private static void describeSetup(MatchSetup setup) {
        setupSummary(setup == null ? "" : setup.settings().label(), setup == null ? "" :
                "New match setup: " + setup.settings().label() + ". Opponent " + setup.opponentLabel()
                        + ", solo color " + setup.colorLabel() + ", chess " + setup.chessLabel() + ", flying " + setup.flightLabel()
                        + ". C changes opponent; W changes solo color; E changes chess; F changes flying. Left and Right change time; Up and Down change bonus; Enter starts a new match. Existing match clocks keep running.");
    }

    @JSBody(params = {"label", "text"}, script = "document.body.dataset.setup = label; const summary = document.getElementById('setup-summary'); if (summary.textContent !== text) summary.textContent = text;")
    private static native void setupSummary(String label, String text);

    static String exportPgn(String pgn) { downloadPgn(pgn); return "PGN downloaded."; }

    static void describeSeries(GameSession game) {
        var series = game.getSeries();
        seriesSummary(series.round(), series.firstHalfPoints(), series.secondHalfPoints(), series.firstWhite(),
                "Round " + series.round() + ". " + series.score() + ". " + series.nameFor(Side.WHITE)
                        + " is White; " + series.nameFor(Side.BLACK) + " is Black. "
                        + (series.players() == com.example.flappychess.controller.MatchSeries.Players.LOCAL
                        ? "Player 1 uses A/D, Space, E; Player 2 uses Left/Right, Up, Enter. "
                        : "Either keyboard layout controls your own bird. ")
                        + "After the result, R keeps colors; X swaps colors.");
    }

    @JSBody(params = {"round", "first", "second", "firstWhite", "text"}, script = "const el = document.getElementById('series-summary'); el.dataset.round = String(round); el.dataset.firstPoints = String(first / 2); el.dataset.secondPoints = String(second / 2); el.dataset.firstWhite = String(firstWhite); if (el.textContent !== text) el.textContent = text;")
    private static native void seriesSummary(int round, int first, int second, boolean firstWhite, String text);

    @JSBody(params = "pgn", script = "const url = URL.createObjectURL(new Blob([pgn], {type: 'application/x-chess-pgn;charset=utf-8'})); const link = document.createElement('a'); link.href = url; link.download = 'flappy-chess.pgn'; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);")
    private static native void downloadPgn(String pgn);

    static void describeHistory(GameSession game, PostgameReview review) {
        var moves = game.getHistory();
        int ply = review.isActive() ? review.getPly() : moves.size();
        var last = ply == 0 ? null : moves.get(ply - 1);
        int whiteCaptures = 0, blackCaptures = 0;
        for (int i = 0; i < ply; i++) {
            var move = moves.get(i);
            if (move.captured() == Piece.NONE) continue;
            if (move.side() == Side.WHITE) whiteCaptures++; else blackCaptures++;
        }
        StringBuilder notation = new StringBuilder();
        for (int i = Math.max(0, (ply + 1) / 2 - 10) * 2; i < ply; i++) {
            if (i % 2 == 0) notation.append(i / 2 + 1).append(". ");
            notation.append(moves.get(i).notation()).append(' ');
        }
        historySummary(game.isGameOver(), review.isActive(), ply, moves.size(), whiteCaptures, blackCaptures,
                last == null ? "" : last.coordinate(), notation.toString().trim(),
                review.isActive() ? review.getView().getBoard().getFen() : game.getBoardView().getBoard().getFen());
    }

    @JSBody(params = {"ended", "review", "ply", "count", "whiteCaptures", "blackCaptures", "last", "notation", "fen"}, script = "const summary = document.getElementById('history-summary'); summary.dataset.ended = String(ended); summary.dataset.review = String(review); summary.dataset.ply = String(ply); summary.dataset.moves = String(count); summary.dataset.whiteCaptured = String(whiteCaptures); summary.dataset.blackCaptured = String(blackCaptures); summary.dataset.last = last; summary.dataset.notation = notation; summary.dataset.fen = fen; const text = review ? 'Reviewing position ' + ply + ' of ' + count + '. White captured ' + whiteCaptures + ' pieces; Black captured ' + blackCaptures + '. Left and Right step through moves; Home and End jump; V returns to the result; P exports PGN.' : ended ? 'Game finished. V reviews the moves, P exports PGN, R plays again.' : ''; if (summary.textContent !== text) summary.textContent = text;")
    private static native void historySummary(boolean ended, boolean review, int ply, int count, int whiteCaptures, int blackCaptures, String last, String notation, String fen);

    private static void launch(ApplicationAdapter game) {
        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        // Size from the canvas element so phone HUD and touch pads have their own space.
        config.width = MobileControls.width();
        config.height = MobileControls.height();
        config.useGL30 = false;
        config.antialiasing = false;
        config.storagePrefix = "flappy-chess";
        // The backend's default provider retains a room hash, breaking script/asset URLs.
        config.baseUrlProvider = WebLauncher::assetBaseUrl;
        new WebApplication(game, config);
    }

    static void catchGameKeys() {
        for (int key : new int[] {Keys.SPACE, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT, Keys.HOME, Keys.END, Keys.ENTER}) {
            Gdx.input.setCatchKey(key, true);
        }
    }

    @JSBody(script = "return new URL('.', document.baseURI).href;")
    private static native String assetBaseUrl();

    @JSBody(script = "document.getElementById('play').disabled = false; document.getElementById('tutorial').disabled = false; document.getElementById('solo').disabled = false; document.getElementById('status').textContent = 'Try Flight School, the computer, or a friend.';")
    private static native void enablePlay();

    @JSBody(script = "document.getElementById('play').disabled = true; document.getElementById('tutorial').disabled = true; document.getElementById('solo').disabled = true; document.getElementById('status').textContent = 'Preparing the board...';")
    private static native void showLoading();

    @JSBody(params = "tutorial", script = "document.getElementById('welcome').hidden = true; document.getElementById('canvas').focus(); document.body.dataset.game = 'ready'; document.body.dataset.mode = tutorial ? 'tutorial' : 'local';")
    private static native void ready(boolean tutorial);

    @JSBody(params = {"stage", "text"}, script = "const summary = document.getElementById('lesson-summary'); summary.dataset.stage = stage; if (summary.textContent !== text) summary.textContent = text; document.body.dataset.mode = stage ? 'tutorial' : 'local';")
    private static native void describeLesson(String stage, String text);

    @JSBody(params = {"text", "sound"}, script = "const summary = document.getElementById('action-feedback'); if (summary.textContent !== text) summary.textContent = text; document.body.dataset.sound = sound ? 'on' : 'off';")
    static native void describeFeedback(String text, boolean sound);

    @JSBody(script = "window.FlappyNetwork.leave();")
    static native void leaveGame();
}
