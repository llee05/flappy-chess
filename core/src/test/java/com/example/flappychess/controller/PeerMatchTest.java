package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.JsonValue;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.example.flappychess.model.ChessGameModel;
import com.example.flappychess.model.MatchSettings;
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
        Pair(MatchSettings settings) {
            host = new PeerMatch(true, toGuest::add, time::get, settings);
            guest = new PeerMatch(false, toHost::add, time::get);
            host.open();
            guest.open();
            flush();
        }
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

    private static void assertMotionEquals(com.example.flappychess.model.Birb.Motion expected,
            com.example.flappychess.model.Birb.Motion actual) {
        assertEquals(expected.x(), actual.x(), 1e-7);
        assertEquals(expected.y(), actual.y(), 1e-7);
        assertEquals(expected.velocityX(), actual.velocityX(), 1e-7);
        assertEquals(expected.velocityY(), actual.velocityY(), 1e-7);
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

    @Test void hostsTimeControlOverridesGuestsDefaultAndIncrementCanExceedInitialTime() {
        Pair pair = new Pair(MatchSettings.minutes(10, 5));
        assertEquals(MatchSettings.minutes(10, 5), pair.guest.getSession().getSettings());
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().setY(425);
        pair.press(pair.host, Keys.E);
        assertEquals(605_000, pair.guest.getSession().getClock().getRemainingMillis(Side.WHITE));
        assertFalse(pair.guest.isDisconnected());
        assertEquals(pair.host.getSession().snapshot(), PeerProtocol.snapshot(PeerProtocol.snapshot(pair.host.getSession().snapshot())));
    }

    @Test void untimedReplicasDoNotConsumeClockTimeAndRematchesKeepHostSettings() {
        Pair untimed = new Pair(MatchSettings.UNTIMED);
        untimed.advance(180);
        assertEquals(MatchSettings.UNTIMED, untimed.guest.getSession().getSettings());
        assertEquals(untimed.host.getSession().snapshot().whiteMillis(), untimed.guest.getSession().snapshot().whiteMillis());
        assertFalse(untimed.guest.getSession().isGameOver());
        Pair timed = new Pair(new MatchSettings(1000, 2000));
        timed.advance(61);
        assertTrue(timed.guest.getSession().isGameOver());
        timed.host.requestRematch();
        timed.guest.requestRematch();
        timed.flush();
        assertEquals(new MatchSettings(1000, 2000), timed.guest.getSession().getSettings());
        assertFalse(timed.guest.getSession().isGameOver());
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
        pair.time.set(Duration.ofSeconds(2).toNanos());
        pair.press(pair.guest, Keys.Q);
        assertTrue(pair.host.getSession().getChess().hasPendingPromotion());
        assertTrue(pair.host.getSession().getHistory().isEmpty());
        pair.time.set(Duration.ofSeconds(4).toNanos());
        pair.press(pair.host, Keys.N);
        assertFalse(pair.guest.getSession().getChess().hasPendingPromotion());
        assertEquals(Piece.WHITE_KNIGHT, pair.guest.getSession().getBoardView().getBoard().getPiece(Square.E8));
        assertEquals(296_000, pair.guest.getSession().getClock().getRemainingMillis(Side.WHITE));
        assertEquals(300_000, pair.guest.getSession().getClock().getRemainingMillis(Side.BLACK));
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

    @Test void bothPlayersCanUseTheSamePromotionKeyOnFollowingTurns() {
        Pair pair = new Pair(Duration.ofMinutes(5), "k7/4P3/8/8/8/8/3p4/7K w - - 0 1");
        pair.host.getSession().getWhite().setY(125);
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().setY(25);
        pair.press(pair.host, Keys.E);
        pair.press(pair.host, Keys.N);
        assertEquals(Side.BLACK, pair.host.getSession().getChess().getSideToMove());

        pair.host.getSession().getBlack().restore(new com.example.flappychess.model.Birb.Motion(325, 625, 0, 0));
        pair.press(pair.guest, Keys.E);
        pair.host.getSession().getBlack().setY(725);
        pair.press(pair.guest, Keys.E);
        assertTrue(pair.guest.getSession().getChess().hasPendingPromotion());
        pair.press(pair.guest, Keys.N);
        assertFalse(pair.host.getSession().getChess().hasPendingPromotion());
        assertEquals(Piece.BLACK_KNIGHT, pair.guest.getSession().getBoardView().getBoard().getPiece(Square.D1));
        assertEquals(2, pair.guest.getSession().getHistory().size());
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

    @Test void swappingNeedsMatchingVotesAndChangesAuthorityAndPrediction() {
        Pair pair = new Pair(new MatchSettings(1000, 0));
        pair.advance(61);
        pair.host.requestRematch(true);
        pair.guest.requestRematch(false);
        pair.flush();
        assertTrue(pair.host.getSession().isGameOver());
        pair.guest.requestRematch(true);
        pair.flush();
        assertFalse(pair.guest.isDisconnected());
        assertEquals(Side.BLACK, pair.host.getOwnSide());
        assertEquals(Side.WHITE, pair.guest.getOwnSide());
        assertEquals(2, pair.guest.getSession().getSeries().secondHalfPoints());
        pair.guest.keyDown(Keys.SPACE);
        pair.guest.update(1f / 60);
        assertTrue(pair.guest.getSession().getWhite().getVelocityY() < 0);
        pair.guest.keyUp(Keys.SPACE);
        pair.flush();
        pair.host.getSession().getWhite().setY(625);
        pair.press(pair.guest, Keys.E);
        assertEquals(Side.WHITE, pair.host.getSession().getCarrier());
        pair.host.getSession().getWhite().setY(425);
        pair.press(pair.guest, Keys.E);
        assertEquals("e4", pair.host.getSession().getHistory().get(0).notation());
        pair.press(pair.host, Keys.E);
        assertEquals(Side.BLACK, pair.guest.getSession().getCarrier());
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

    @Test void peersKeepExactCaptureHistoryAndCanReviewIndependently() {
        Pair pair = new Pair(Duration.ofSeconds(1), "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        pair.host.getSession().getWhite().setY(325);
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(325, 225, 0, 0));
        pair.press(pair.host, Keys.E);
        assertEquals(Piece.BLACK_PAWN, pair.guest.getSession().getHistory().get(0).captured());
        assertEquals(pair.host.getSession().getHistory(), pair.guest.getSession().getHistory());
        pair.advance(61);
        assertTrue(pair.guest.getSession().isGameOver());
        assertEquals(new GameRecord(pair.host.getSession()).pgn(), new GameRecord(pair.guest.getSession()).pgn());
        SessionSnapshot finished = pair.host.getSession().snapshot();
        PostgameReview guestReview = new PostgameReview();
        guestReview.bind(pair.guest.getSession());
        guestReview.keyDown(Keys.V);
        guestReview.keyDown(Keys.HOME);
        assertEquals(0, guestReview.getPly());
        assertEquals(finished, pair.host.getSession().snapshot());
        pair.host.requestRematch();
        pair.guest.requestRematch();
        pair.flush();
        guestReview.bind(pair.guest.getSession());
        assertFalse(guestReview.isActive());
        assertTrue(pair.guest.getSession().getHistory().isEmpty());
    }

    @Test void maximumMoveHistoryFitsTheBoundedWireFormat() {
        Pair pair = new Pair();
        SessionSnapshot state = pair.host.getSession().snapshot();
        var moves = new java.util.ArrayList<ChessController.CompletedMove>();
        for (int i = 0; i < 2048; i++) {
            Side side = i % 2 == 0 ? Side.WHITE : Side.BLACK;
            moves.add(new ChessController.CompletedMove(side, "exd8=Q+", Square.E7, Square.D8,
                    Piece.make(side, com.github.bhlangonijr.chesslib.PieceType.QUEEN),
                    Piece.make(side.flip(), com.github.bhlangonijr.chesslib.PieceType.ROOK)));
        }
        SessionSnapshot full = new SessionSnapshot(state.fen(), state.white(), state.black(),
                state.whiteStunSteps(), state.blackStunSteps(), state.bumpCooldown(), state.hitStopSteps(),
                state.dropReturn(), state.lastBump(), state.whiteMillis(), state.blackMillis(),
                state.selected(), state.hidden(), state.targets(), state.promotion(), state.carrier(), moves, state.result(), state.reason(),
                state.feedback(), state.settings(), state.initialFen(), state.series());
        JsonValue packet = packet("state");
        packet.addChild("state", snapshot(full));
        String text = encode(packet);
        assertTrue(text.length() < MAX_MESSAGE);
        assertEquals(full, snapshot(parse(text).require("state")));
    }

    @Test void guestReceivesInvalidDropFeedbackOnceEvenWhenInputsOrSnapshotsRepeat() {
        Pair pair = new Pair();
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().setY(425);
        pair.press(pair.host, Keys.E);
        pair.press(pair.guest, Keys.E);
        pair.host.getSession().getBlack().setY(425); // A pawn cannot drop onto occupied e4.
        pair.guest.keyDown(Keys.E);
        String repeatedInput = pair.toHost.peek();
        pair.flush();
        var events = pair.host.getSession().snapshot().feedback();
        assertEquals(GameFeedback.Kind.ILLEGAL_DROP, events.get(events.size() - 1).kind());
        assertEquals(events, pair.guest.getSession().snapshot().feedback());
        pair.host.receive(repeatedInput);
        pair.advance(4);
        assertEquals(events, pair.guest.getSession().snapshot().feedback());
        assertEquals(Side.BLACK, pair.guest.getSession().getCarrier());
    }

    @Test void feedbackProtocolRejectsUnboundedOrOutOfOrderEvents() {
        for (boolean oversized : new boolean[] {false, true}) {
            Pair pair = new Pair();
            pair.host.keyDown(Keys.E);
            JsonValue value = parse(pair.toGuest.peek());
            JsonValue events = value.get("state").get("feedback");
            if (oversized) {
                for (int i = 0; i < GameFeedback.LIMIT; i++) {
                    events.addChild(new JsonValue(JsonValue.ValueType.nullValue));
                }
            } else events.get(0).get("sequence").set(0L, "0");
            pair.guest.receive(encode(value));
            assertTrue(pair.guest.isDisconnected());
        }
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

    @Test void hiddenHostTimerAdvancesFlightAndClocksAtForegroundSpeed() {
        Pair foreground = new Pair();
        Pair hidden = new Pair();
        for (Pair pair : new Pair[] {foreground, hidden}) {
            pair.guest.keyDown(Keys.D);
            pair.guest.keyDown(Keys.SPACE);
            pair.flush();
        }
        foreground.advance(60);
        hidden.time.set(1_000_000_000L); // Throttled background timer, no renders.
        hidden.host.pulse();
        hidden.flush();
        assertMotionEquals(foreground.host.getSession().getBlack().snapshot(), hidden.host.getSession().getBlack().snapshot());
        assertTrue(hidden.host.getSession().getBlack().getX() > 600);
        assertEquals(299_000, hidden.host.getSession().snapshot().whiteMillis());
        assertFalse(hidden.host.isInterrupted());
        // A render and a timer at the same instant must not consume time twice.
        SessionSnapshot once = hidden.host.getSession().snapshot();
        hidden.host.update(1);
        hidden.host.pulse();
        assertEquals(once, hidden.host.getSession().snapshot());
    }

    @Test void timerAndRenderingShareOneTimelineAndInputsDoNotApplyRetroactively() {
        Pair pair = new Pair();
        pair.time.set(200_000_000L);
        pair.guest.keyDown(Keys.D);
        pair.flush();
        assertEquals(425, pair.host.getSession().getBlack().getX());
        pair.time.set(400_000_000L);
        pair.host.update(0); // The monotonic clock, not caller delta, owns advancement.
        assertTrue(pair.host.getSession().getBlack().getX() > 440);
        var beforeRelease = pair.host.getSession().getBlack().snapshot();
        pair.guest.releaseInput();
        pair.flush();
        pair.host.update(10);
        assertMotionEquals(beforeRelease, pair.host.getSession().getBlack().snapshot());
        for (int i = 0; i < 8; i++) {
            pair.time.addAndGet(250_000_000L);
            pair.host.pulse();
            pair.flush();
        }
        assertEquals(0, pair.host.getSession().getBlack().getVelocityX());
        assertEquals(297_600, pair.host.getSession().snapshot().whiteMillis());
    }

    @Test void hidingEitherOrBothPeersDoesNotChangeHostClockOrPhysics() {
        for (boolean hostHidden : new boolean[] {false, true}) {
            for (boolean guestHidden : new boolean[] {false, true}) {
                Pair pair = new Pair();
                pair.host.keyDown(Keys.D);
                pair.guest.keyDown(Keys.D);
                pair.flush();
                if (hostHidden) pair.host.releaseInput();
                if (guestHidden) pair.guest.releaseInput();
                pair.flush();
                for (int i = 0; i < 8; i++) {
                    pair.time.addAndGet(250_000_000L);
                    if (hostHidden) pair.host.pulse(); else pair.host.update(0.25f);
                    pair.flush();
                    if (guestHidden) pair.guest.pulse(); else pair.guest.update(0.25f);
                    pair.flush();
                }
                assertEquals(hostHidden ? 425 : 750, pair.host.getSession().getWhite().getX());
                assertEquals(guestHidden ? 425 : 750, pair.host.getSession().getBlack().getX());
                assertEquals(298_000, pair.host.getSession().snapshot().whiteMillis());
                assertFalse(pair.host.isInterrupted());
                assertFalse(pair.guest.isInterrupted());
                var returned = pair.host.getSession().snapshot();
                pair.host.update(2);
                assertEquals(returned, pair.host.getSession().snapshot());
            }
        }
    }

    @Test void hostSuspensionAnnouncesInterruptionRejectsQueuedInputsAndResumesWithFreshPress() {
        Pair pair = new Pair();
        pair.host.keyDown(Keys.D);
        pair.guest.keyDown(Keys.D);
        pair.flush();
        pair.advance(12);
        pair.guest.keyDown(Keys.SPACE);
        String queued = pair.toHost.remove();
        var before = pair.host.getSession().getBlack().snapshot();
        pair.time.set(4_200_000_000L);
        pair.guest.pulse();
        assertTrue(pair.guest.isInterrupted());
        assertTrue(pair.guest.status().contains("Host interrupted"));
        pair.guest.update(0.25f);
        pair.host.receive(queued); // An input callback can be the first task after thaw.
        pair.flush();
        assertTrue(pair.host.isInterrupted());
        assertTrue(pair.guest.isInterrupted());
        assertMotionEquals(before, pair.guest.getSession().getBlack().snapshot());
        assertMotionEquals(before, pair.host.getSession().getBlack().snapshot());
        assertEquals(295_800, pair.host.getSession().snapshot().whiteMillis());
        pair.host.keyDown(Keys.SPACE);
        pair.guest.keyDown(Keys.SPACE);
        pair.flush();
        assertMotionEquals(before, pair.host.getSession().getBlack().snapshot());
        for (int i = 0; i < 8; i++) {
            pair.time.addAndGet(250_000_000L);
            pair.host.pulse();
            pair.flush();
        }
        assertFalse(pair.host.isInterrupted());
        assertFalse(pair.guest.isInterrupted());
        pair.host.receive(queued); // Old generation must remain invalid after recovery.
        assertMotionEquals(before, pair.host.getSession().getBlack().snapshot());
        pair.advance(120);
        assertEquals(0, pair.host.getSession().getBlack().getVelocityX());
        assertEquals(0, pair.host.getSession().getWhite().getVelocityX());
        pair.guest.keyDown(Keys.D); // Auto-repeat cannot revive the old held key.
        pair.host.keyDown(Keys.D);
        pair.flush();
        pair.advance(6);
        assertEquals(0, pair.host.getSession().getBlack().getVelocityX());
        assertEquals(0, pair.host.getSession().getWhite().getVelocityX());
        pair.guest.keyUp(Keys.D);
        pair.guest.keyDown(Keys.D);
        pair.flush();
        pair.advance(6);
        assertTrue(pair.host.getSession().getBlack().getVelocityX() > 0);
    }

    @Test void guestStopsPredictionButKeepsClockRunningWhenHostSnapshotsStop() {
        Pair pair = new Pair();
        pair.guest.keyDown(Keys.D);
        pair.flush();
        pair.time.set(4_000_000_000L);
        pair.guest.update(0.25f);
        var frozen = pair.guest.getSession().getBlack().snapshot();
        assertTrue(pair.guest.isInterrupted());
        pair.time.set(5_000_000_000L);
        pair.guest.keyDown(Keys.SPACE);
        pair.guest.update(0.25f);
        assertMotionEquals(frozen, pair.guest.getSession().getBlack().snapshot());
        assertEquals(295_000, pair.guest.getSession().snapshot().whiteMillis());
        assertFalse(pair.guest.isDisconnected());
    }

    @Test void suspensionStillChargesClockAndCanEndGameWithoutReplayingFlight() {
        Pair pair = new Pair(new MatchSettings(1000, 0));
        var before = pair.host.getSession().getWhite().snapshot();
        pair.time.set(5_000_000_000L);
        pair.host.pulse();
        pair.flush();
        assertTrue(pair.host.getSession().isGameOver());
        assertTrue(pair.guest.getSession().isGameOver());
        assertEquals(0, pair.host.getSession().snapshot().whiteMillis());
        assertFalse(pair.host.isInterrupted());
        assertFalse(pair.guest.isInterrupted());
        assertMotionEquals(before, pair.host.getSession().getWhite().snapshot());
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

    @Test void hostBumpSynchronizesDroppedPieceKnockbackAndFeedbackWithGuest() {
        Pair pair = new Pair();
        pair.press(pair.host, Keys.E);
        assertEquals(Side.WHITE, pair.guest.getSession().getCarrier());
        assertEquals(Square.E2, pair.guest.getSession().getBoardView().getHiddenPieceSquare());

        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        pair.host.getSession().getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));
        pair.advance(1);

        assertNull(pair.host.getSession().getCarrier());
        assertNull(pair.guest.getSession().getCarrier());
        assertEquals(Square.NONE, pair.guest.getSession().getBoardView().getHiddenPieceSquare());
        assertEquals(Piece.WHITE_PAWN, pair.guest.getSession().getBoardView().getBoard().getPiece(Square.E2));
        assertTrue(pair.guest.getSession().getHistory().isEmpty());
        assertEquals(-GameSession.BUMP_KNOCKBACK_X, pair.guest.getSession().getWhite().getVelocityX(), 1e-7);
        assertTrue(pair.guest.getSession().getWhite().isStunned());
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS - 1, pair.guest.getSession().getHitStopSteps());
        assertNotNull(pair.guest.getSession().getDropReturn());
        assertEquals(pair.host.getSession().getDropReturn(), pair.guest.getSession().getDropReturn());
        assertEquals(pair.host.getSession().getLastBump(), pair.guest.getSession().getLastBump());

        var hostEvents = pair.host.getSession().snapshot().feedback();
        var guestEvents = pair.guest.getSession().snapshot().feedback();
        assertEquals(GameFeedback.Kind.BUMP_DROP, hostEvents.get(hostEvents.size() - 1).kind());
        assertEquals(hostEvents, guestEvents);

        // A stale pre-bump state packet cannot restore the dropped carry or rewind feedback.
        JsonValue stalePacket = packet("state");
        put(stalePacket, "epoch", 1);
        put(stalePacket, "sequence", 1);
        put(stalePacket, "ack", 0);
        put(stalePacket, "hostRematch", false);
        put(stalePacket, "guestRematch", false);
        put(stalePacket, "hostSwap", false);
        put(stalePacket, "guestSwap", false);
        stalePacket.addChild("state", snapshot(pair.host.getSession().snapshot()));
        pair.guest.receive(encode(stalePacket));

        int bumpCount = (int) guestEvents.stream().filter(e -> e.kind() == GameFeedback.Kind.BUMP_DROP).count();
        assertEquals(1, bumpCount);
        pair.advance(5);
        assertEquals(1, pair.guest.getSession().snapshot().feedback().stream()
                .filter(e -> e.kind() == GameFeedback.Kind.BUMP_DROP).count());
        assertFalse(pair.guest.isDisconnected());
    }

    @Test void emptyHandedBumpSynchronizesWithoutStunningEitherBird() {
        Pair pair = new Pair();
        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        pair.host.getSession().getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));

        pair.time.addAndGet(16_666_667);
        pair.host.update(1f / 60);
        pair.flush();

        assertEquals(pair.host.getSession().snapshot(), pair.guest.getSession().snapshot());
        assertEquals(-GameSession.EMPTY_BUMP_KNOCKBACK_X,
                pair.guest.getSession().getWhite().getVelocityX(), 1e-7);
        assertEquals(GameSession.EMPTY_BUMP_RECOIL_X,
                pair.guest.getSession().getBlack().getVelocityX(), 1e-7);
        assertEquals(GameSession.EMPTY_HIT_STOP_STEPS, pair.guest.getSession().getHitStopSteps());
        assertNull(pair.guest.getSession().getDropReturn());
        assertNotNull(pair.guest.getSession().getLastBump());
        assertFalse(pair.guest.getSession().getLastBump().dropped());
        assertFalse(pair.host.getSession().getWhite().isStunned());
        assertFalse(pair.host.getSession().getBlack().isStunned());
        assertFalse(pair.guest.getSession().getWhite().isStunned());
        assertFalse(pair.guest.getSession().getBlack().isStunned());
    }

    @Test void catchUpSnapshotKeepsRemainingBumpStunDropReturnAndCooldown() {
        Pair pair = new Pair();
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        pair.host.getSession().getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));

        pair.time.addAndGet(250_000_000);
        pair.host.update(0.25f);
        pair.flush();

        // 15 steps at 60 Hz: 1 bump step + 4 hit-stop steps + 10 active steps.
        int activeSteps = 15 - 1 - GameSession.CARRY_HIT_STOP_STEPS;
        assertEquals(0, pair.host.getSession().getHitStopSteps());
        assertEquals(GameSession.BUMP_STUN_STEPS - activeSteps, pair.host.getSession().getWhite().getStunSteps());
        assertEquals(GameSession.BUMP_COOLDOWN_STEPS - activeSteps, pair.host.getSession().getBumpCooldown());
        assertEquals(GameSession.DROP_RETURN_STEPS - activeSteps, pair.host.getSession().getDropReturn().stepsRemaining());
        SessionSnapshot hostState = pair.host.getSession().snapshot();
        SessionSnapshot guestState = pair.guest.getSession().snapshot();
        assertEquals(hostState.fen(), guestState.fen());
        assertMotionEquals(hostState.white(), guestState.white());
        assertMotionEquals(hostState.black(), guestState.black());
        assertEquals(hostState.feedback(), guestState.feedback());
        assertEquals(hostState.hitStopSteps(), guestState.hitStopSteps());
        assertEquals(hostState.dropReturn(), guestState.dropReturn());
        assertEquals(hostState.lastBump(), guestState.lastBump());
        assertEquals(GameSession.BUMP_STUN_STEPS - activeSteps, pair.guest.getSession().getWhite().getStunSteps());
        assertEquals(0, pair.guest.getSession().getBlack().getStunSteps());
        assertEquals(GameSession.BUMP_COOLDOWN_STEPS - activeSteps, pair.guest.getSession().getBumpCooldown());
    }

    @Test void guestReplicaRespectsHitStopAndAdvancesDropReturnInLockstep() {
        Pair pair = new Pair();
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(425, 425, 0, 0));
        pair.host.getSession().getBlack().restore(new com.example.flappychess.model.Birb.Motion(455, 425, -3.6, 0));

        pair.time.addAndGet(16_666_667);
        pair.host.update(1f / 60);
        pair.flush();

        double guestBlackX = pair.guest.getSession().getBlack().getX();
        for (int i = 0; i < GameSession.CARRY_HIT_STOP_STEPS; i++) {
            pair.guest.update(1f / 60);
            assertEquals(guestBlackX, pair.guest.getSession().getBlack().getX(), 1e-7);
            assertEquals(GameSession.DROP_RETURN_STEPS, pair.guest.getSession().getDropReturn().stepsRemaining());
        }
        assertEquals(0, pair.guest.getSession().getHitStopSteps());
        pair.guest.update(1f / 60);
        assertTrue(pair.guest.getSession().getBlack().getX() > guestBlackX);
        assertEquals(GameSession.DROP_RETURN_STEPS - 1, pair.guest.getSession().getDropReturn().stepsRemaining());
    }

    @Test void invalidBumpTimedStateInPacketDisconnectsGuest() {
        Pair pair = new Pair();
        pair.host.keyDown(Keys.D);
        JsonValue packet = parse(pair.toGuest.peek());
        packet.get("state").get("hitStopSteps").set(GameSession.CARRY_HIT_STOP_STEPS + 1L,
                Integer.toString(GameSession.CARRY_HIT_STOP_STEPS + 1));
        pair.guest.receive(encode(packet));
        assertTrue(pair.guest.isDisconnected());
    }

    @Test void edgeClampedKnockbackKeepsBumpStunAndCooldown() {
        Pair pair = new Pair();
        pair.press(pair.host, Keys.E);
        pair.host.getSession().getWhite().restore(new com.example.flappychess.model.Birb.Motion(0, 425, 0, 0));
        pair.host.getSession().getBlack().restore(new com.example.flappychess.model.Birb.Motion(30, 425, -3.6, 0));

        pair.time.addAndGet(16_666_667);
        pair.host.update(1f / 60);
        pair.flush();

        assertEquals(0, pair.host.getSession().getWhite().getVelocityX(), 1e-7);
        assertEquals(GameSession.BUMP_STUN_STEPS, pair.host.getSession().getWhite().getStunSteps());
        assertEquals(GameSession.BUMP_COOLDOWN_STEPS, pair.host.getSession().getBumpCooldown());
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS, pair.host.getSession().getHitStopSteps());
        SessionSnapshot hostState = pair.host.getSession().snapshot();
        SessionSnapshot guestState = pair.guest.getSession().snapshot();
        assertEquals(hostState.fen(), guestState.fen());
        assertMotionEquals(hostState.white(), guestState.white());
        assertMotionEquals(hostState.black(), guestState.black());
        assertEquals(hostState.feedback(), guestState.feedback());
        assertEquals(GameSession.BUMP_STUN_STEPS, pair.guest.getSession().getWhite().getStunSteps());
        assertEquals(0, pair.guest.getSession().getBlack().getStunSteps());
        assertEquals(GameSession.BUMP_COOLDOWN_STEPS, pair.guest.getSession().getBumpCooldown());
        assertEquals(GameSession.CARRY_HIT_STOP_STEPS, pair.guest.getSession().getHitStopSteps());
    }
}
