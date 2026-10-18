package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.JsonValue;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.example.flappychess.model.ChessGameModel;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.flappychess.controller.PeerProtocol.*;

class PeerMatchTest {
    private static final class Pair {
        final AtomicLong time = new AtomicLong();
        final ArrayDeque<String> toHost = new ArrayDeque<>(), toGuest = new ArrayDeque<>();
        final PeerMatch host, guest;
        Pair() { this(Duration.ofMinutes(5), null); }
        Pair(Duration duration, String fen) {
            host = new PeerMatch(true, toGuest::add, time::get, () -> session(duration, fen));
            guest = new PeerMatch(false, toHost::add, time::get, () -> session(duration, fen));
            host.open();
            guest.open();
            flush();
        }
        GameSession session(Duration duration, String fen) {
            ChessGameModel model = new ChessGameModel();
            if (fen != null) model.restorePosition(fen);
            return new GameSession(model, duration, time::get);
        }
        void flush() {
            for (int i = 0; !toHost.isEmpty() || !toGuest.isEmpty(); i++) {
                assertTrue(i < 100, "Unexpected message loop");
                while (!toHost.isEmpty()) host.receive(toHost.remove());
                while (!toGuest.isEmpty()) guest.receive(toGuest.remove());
            }
        }
        void advance(int steps) {
            for (int i = 0; i < steps; i++) {
                time.addAndGet(16_666_667);
                host.update(1f / 60);
                flush();
                guest.update(1f / 60);
                flush();
            }
        }
        void press(PeerMatch match, int key) { match.keyDown(key); match.keyUp(key); flush(); }
    }

    @Test void handshakeStartsOneSharedMatchWithFiveMinuteClocks() {
        Pair pair = new Pair();
        assertTrue(pair.host.isStarted());
        assertTrue(pair.guest.isStarted());
        assertEquals(Side.WHITE, pair.host.getOwnSide());
        assertEquals(Side.BLACK, pair.guest.getOwnSide());
        assertEquals(pair.host.getSession().snapshot(), pair.guest.getSession().snapshot());
        pair.advance(60);
        assertEquals(299, pair.host.getSession().getClock().getRemainingSeconds(Side.WHITE));
        assertEquals(300, pair.host.getSession().getClock().getRemainingSeconds(Side.BLACK));
    }

    @Test void eachKeyboardControlsOnlyItsOwnBirdAndGuestPredictsImmediately() {
        Pair pair = new Pair();
        pair.host.keyDown(Keys.RIGHT); // Host may use arrows, but controls White.
        pair.guest.keyDown(Keys.D); // Guest may use WASD, but controls Black.
        pair.guest.keyDown(Keys.SPACE);
        pair.guest.update(1f / 60);
        assertTrue(pair.guest.getSession().getBlack().getVelocityY() > 0);
        assertEquals(0, pair.host.getSession().getBlack().getVelocityY());
        pair.flush();
        pair.advance(12);
        assertTrue(pair.host.getSession().getWhite().getX() > 425);
        assertTrue(pair.host.getSession().getBlack().getX() > 425);
        assertFalse(pair.host.isDisconnected());
    }

    @Test void hostMovesAndCarryStateReachGuestWithSanAndTurnSwitch() {
        Pair pair = new Pair();
        pair.press(pair.guest, Keys.E); // Black cannot pick up during White's turn.
        assertNull(pair.host.getSession().getCarrier());
        pair.press(pair.host, Keys.E);
        assertEquals(Side.WHITE, pair.guest.getSession().getCarrier());
        assertEquals(Square.E2, pair.guest.getSession().getBoardView().getHiddenPieceSquare());
        assertTrue(pair.guest.getSession().getBoardView().getLegalTargets().contains(Square.E4));
        pair.host.getSession().getWhite().setY(425);
        pair.press(pair.host, Keys.E);
        assertEquals(Side.BLACK, pair.guest.getSession().getChess().getSideToMove());
        assertEquals("e4", pair.guest.getSession().getHistory().get(0).notation());
        assertEquals(pair.host.getSession().snapshot().fen(), pair.guest.getSession().snapshot().fen());
        assertNull(pair.guest.getSession().getCarrier());
        pair.press(pair.guest, Keys.E);
        assertEquals(Side.BLACK, pair.host.getSession().getCarrier());
        pair.host.getSession().getBlack().setY(325);
        pair.press(pair.guest, Keys.E);
        assertEquals("e5", pair.guest.getSession().getHistory().get(1).notation());
    }

    @Test void promotionBelongsToMoverAndIsReplicatedAfterSelection() {
        Pair pair = new Pair(Duration.ofMinutes(5), "k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
        pair.host.getSession().getWhite().setY(125);
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().setY(25);
        pair.press(pair.host, Keys.E);
        assertTrue(pair.guest.getSession().getChess().hasPendingPromotion());
        pair.press(pair.guest, Keys.Q);
        assertTrue(pair.host.getSession().getChess().hasPendingPromotion());
        pair.press(pair.host, Keys.N);
        assertFalse(pair.guest.getSession().getChess().hasPendingPromotion());
        assertEquals(Piece.WHITE_KNIGHT, pair.guest.getSession().getBoardView().getBoard().getPiece(Square.E8));
    }

    @Test void focusReleaseStopsOnlyThePlayerWhoLostFocus() {
        Pair pair = new Pair();
        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(100, 625, 0, 0));
        pair.host.keyDown(Keys.D);
        pair.guest.keyDown(Keys.D);
        pair.flush();
        pair.advance(12);
        pair.guest.releaseInput();
        pair.flush();
        double before = pair.host.getSession().getBlack().getX();
        pair.advance(130);
        assertEquals(0, pair.host.getSession().getBlack().getVelocityX());
        assertTrue(pair.host.getSession().getBlack().getX() - before < 75);
        assertTrue(pair.host.getSession().getWhite().getVelocityX() > 0);
    }

    @Test void releasingOneKeyboardAliasDoesNotReleaseTheOther() {
        Pair pair = new Pair();
        pair.guest.keyDown(Keys.D);
        pair.guest.keyDown(Keys.RIGHT);
        pair.guest.keyUp(Keys.D);
        pair.flush();
        pair.advance(12);
        assertEquals(3.6, pair.host.getSession().getBlack().getVelocityX(), 1e-7);
        pair.guest.keyUp(Keys.RIGHT);
        pair.flush();
        pair.advance(120);
        assertEquals(0, pair.host.getSession().getBlack().getVelocityX());
    }

    @Test void rematchNeedsBothPlayersAndRejectsOldRoundInputs() {
        Pair pair = new Pair(Duration.ofMillis(1), null);
        pair.guest.keyDown(Keys.D);
        String stale = pair.toHost.peek();
        pair.flush();
        pair.advance(1);
        assertTrue(pair.host.getSession().isGameOver());
        pair.host.requestRematch();
        pair.flush();
        assertTrue(pair.host.getSession().isGameOver());
        assertTrue(pair.guest.status().contains("opponent wants"));
        pair.guest.requestRematch();
        pair.flush();
        assertFalse(pair.host.getSession().isGameOver());
        assertFalse(pair.guest.getSession().isGameOver());
        pair.host.receive(stale);
        assertEquals(0, pair.host.getSession().getBlack().getVelocityX());
    }

    @Test void staleSnapshotsAndDuplicateInputsCannotRewindTheMatch() {
        Pair pair = new Pair();
        pair.host.keyDown(Keys.D);
        String staleState = pair.toGuest.peek();
        pair.flush();
        pair.advance(12);
        String current = pair.guest.getSession().snapshot().fen();
        double x = pair.guest.getSession().getWhite().getX();
        pair.guest.receive(staleState);
        assertEquals(current, pair.guest.getSession().snapshot().fen());
        assertEquals(x, pair.guest.getSession().getWhite().getX());
        pair.guest.keyDown(Keys.SPACE);
        String input = pair.toHost.peek();
        pair.flush();
        pair.advance(5);
        double velocity = pair.host.getSession().getBlack().getVelocityY();
        pair.host.receive(input);
        assertEquals(velocity, pair.host.getSession().getBlack().getVelocityY());
    }

    @Test void guestCanRequestRematchFirstAndHeldInputsDoNotCarryIntoNewRound() {
        Pair pair = new Pair(Duration.ofMillis(1), null);
        pair.host.keyDown(Keys.D);
        pair.advance(4);
        assertTrue(pair.guest.getSession().isGameOver());
        pair.guest.requestRematch();
        pair.flush();
        assertTrue(pair.host.status().contains("opponent wants"));
        pair.host.requestRematch();
        pair.flush();
        assertFalse(pair.guest.getSession().isGameOver());
        assertEquals(425, pair.host.getSession().getWhite().getX());
        assertEquals(0, pair.host.getSession().getWhite().getVelocityX());
    }

    @Test void disconnectedMatchFreezesPhysicsClockAndInput() {
        Pair pair = new Pair();
        pair.host.keyDown(Keys.D);
        pair.advance(12);
        pair.host.disconnect("Opponent left");
        SessionSnapshot frozen = pair.host.getSession().snapshot();
        pair.time.addAndGet(10_000_000_000L);
        pair.host.keyDown(Keys.SPACE);
        pair.host.update(0.25f);
        pair.host.pulse();
        assertEquals(frozen, pair.host.getSession().snapshot());
        assertEquals("Opponent left", pair.host.status());
    }

    @Test void snapshotRoundTripKeepsEveryPresentationField() {
        Pair pair = new Pair();
        pair.press(pair.host, Keys.E);
        SessionSnapshot state = pair.host.getSession().snapshot();
        assertEquals(state, snapshot(snapshot(state)));
    }

    @Test void invalidOrOversizedPacketsStopTheConnectionWithoutMutatingTheBoard() {
        for (String invalid : new String[] {"not json", "{}", "{\"version\":999,\"type\":\"ready\"}", "x".repeat(MAX_MESSAGE + 1)}) {
            Pair pair = new Pair();
            String fen = pair.host.getSession().snapshot().fen();
            pair.host.receive(invalid);
            assertTrue(pair.host.isDisconnected());
            assertEquals(fen, pair.host.getSession().snapshot().fen());
        }
        Pair pair = new Pair();
        pair.host.keyDown(Keys.D);
        JsonValue value = parse(pair.toGuest.peek());
        value.get("state").get("black").get(0).set(9000L, "9000");
        pair.guest.receive(encode(value));
        assertTrue(pair.guest.isDisconnected());
    }

    @Test void heartbeatKeepsHostClockRunningWithoutRenderingAndDetectsLostPeers() {
        Pair pair = new Pair();
        pair.time.set(3_000_000_000L);
        pair.host.pulse();
        pair.flush();
        assertEquals(297, pair.guest.getSession().getClock().getRemainingSeconds(Side.WHITE));
        pair.time.set(25_000_000_000L);
        pair.guest.pulse();
        assertTrue(pair.guest.isDisconnected());
    }
}
