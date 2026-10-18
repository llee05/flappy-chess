package com.example.flappychess.controller;

import com.badlogic.gdx.Input.Keys;
import com.example.flappychess.model.Birb;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static com.example.flappychess.controller.FlightSchool.Stage.*;
import static org.junit.jupiter.api.Assertions.*;

class FlightSchoolTest {
    private void press(FlightSchool school, int key) { school.keyDown(key); school.keyUp(key); }
    private void over(Birb bird, Square square) {
        bird.restore(new Birb.Motion(square.getFile().ordinal() * 100 + 25,
                (7 - square.getRank().ordinal()) * 100 + 25, 0, 0));
    }

    @Test void lessonNeverChargesTimeAndUsesBothGravityDirections() {
        AtomicLong time = new AtomicLong();
        FlightSchool school = new FlightSchool(time::get);
        time.set(Duration.ofHours(1).toNanos());
        press(school, Keys.SPACE);
        press(school, Keys.UP);
        school.update(1f / 60);
        assertTrue(school.getSession().getWhite().getVelocityY() < 0);
        assertTrue(school.getSession().getBlack().getVelocityY() > 0);
        assertFalse(school.getSession().isTimed());
        assertFalse(school.getSession().isGameOver());
        assertEquals(300, school.getSession().getClock().getRemainingSeconds(Side.WHITE));
    }

    @Test void bothDeliveriesCompleteTheLessonAndFreezeItsBirds() {
        FlightSchool school = new FlightSchool(() -> 0L);
        GameSession game = school.getSession();
        assertEquals(WHITE_FLY, school.getStage());
        over(game.getWhite(), Square.E2);
        school.update(0);
        assertEquals(WHITE_PICKUP, school.getStage());
        press(school, Keys.E);
        assertEquals(WHITE_DELIVER, school.getStage());
        over(game.getWhite(), Square.E4);
        press(school, Keys.E);
        assertEquals(BLACK_FLY, school.getStage());
        assertEquals(Piece.WHITE_PAWN, game.getBoardView().getBoard().getPiece(Square.E4));
        over(game.getBlack(), Square.D7);
        school.update(0);
        assertEquals(BLACK_PICKUP, school.getStage());
        press(school, Keys.ENTER);
        assertEquals(BLACK_DELIVER, school.getStage());
        over(game.getBlack(), Square.D6);
        press(school, Keys.ENTER);
        assertTrue(school.isComplete());
        assertEquals(2, game.getHistory().size());
        Birb.Motion stopped = game.getBlack().snapshot();
        press(school, Keys.UP);
        school.update(1);
        assertEquals(stopped, game.getBlack().snapshot());
    }

    @Test void invalidDropsAndCancellationRemainRecoverable() {
        FlightSchool school = new FlightSchool(() -> 0L);
        GameSession game = school.getSession();
        over(game.getWhite(), Square.E2);
        press(school, Keys.E);
        over(game.getWhite(), Square.F4);
        press(school, Keys.E);
        assertEquals(WHITE_DELIVER, school.getStage());
        assertEquals(Side.WHITE, game.getCarrier());
        over(game.getWhite(), Square.E2);
        press(school, Keys.E);
        assertEquals(WHITE_PICKUP, school.getStage());
        assertNull(game.getCarrier());
        press(school, Keys.E);
        over(game.getWhite(), Square.E3);
        press(school, Keys.E);
        assertEquals(BLACK_FLY, school.getStage());
    }
}
