package com.example.flappychess.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.controller.FlightSchool;
import com.example.flappychess.controller.MatchSetup;
import com.example.flappychess.controller.PostgameReview;
import com.example.flappychess.controller.ChessController.CompletedMove;
import com.example.flappychess.model.Birb;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.Locale;

/** Pixel-art presentation; model coordinates retain their original top-left origin. */
public final class GameRenderer implements Disposable {
    private static final float WORLD_WIDTH = 1600;
    private static final float WORLD_HEIGHT = 800;
    // Matches the inset in tools/generate-game-art.py; all flight feedback uses these scales.
    static final float BOARD_X = 448;
    static final float BOARD_Y = 88;
    static final float BOARD_SCALE_X = 0.89f;
    static final float BOARD_SCALE_Y = 0.78f;
    static final float SQUARE_WIDTH = 89;
    static final float SQUARE_HEIGHT = 78;
    static final float BOARD_WIDTH = SQUARE_WIDTH * 8;
    static final float BOARD_HEIGHT = SQUARE_HEIGHT * 8;
    private static final Color LIGHT = Color.valueOf("ffe9c3");
    private static final Color LIGHT_EDGE = Color.valueOf("dfc69e");
    private static final Color LIGHT_SHINE = Color.valueOf("fff0cf");
    private static final Color DARK = Color.valueOf("87a897");
    private static final Color DARK_EDGE = Color.valueOf("739488");
    private static final Color DARK_SHINE = Color.valueOf("96b3a0");
    private static final Color PANEL = Color.valueOf("20283c");
    private static final Color CARD = Color.valueOf("29364c");
    private static final Color EDGE = Color.valueOf("4b6d75");
    private static final Color INK = Color.valueOf("202538");
    private static final Color CREAM = Color.valueOf("e4cd9f");
    private static final Color MINT = Color.valueOf("add2b2");
    private static final Color GOLD = Color.valueOf("efb95b");
    private static final Color GOLD_BRIGHT = Color.valueOf("ffe5a1");
    private static final Color BLUE = Color.valueOf("78c4d0");
    private static final Color MENU_SKY = Color.valueOf("68b5e5");
    private static final Color MENU_CLOUD_FAR = Color.valueOf("d4edf7");
    private static final Color MENU_CLOUD_FAR_SHADE = Color.valueOf("a1cfe8");
    private static final Color MENU_CLOUD_NEAR = Color.valueOf("f3fbff");
    private static final Color MENU_CLOUD_NEAR_SHADE = Color.valueOf("b6ddee");
    private static final Color MUTED = Color.valueOf("94a3b8");
    private static final Color DANGER = Color.valueOf("ed8c78");
    private static final Color SELECTED = Color.valueOf("baa96d");
    private static final Color LAST_MOVE = new Color(0.94f, 0.73f, 0.35f, 0.24f);
    private static final Color SHADOW = new Color(0.08f, 0.12f, 0.18f, 0.22f);
    private static final Color VEIL = new Color(0.06f, 0.08f, 0.15f, 0.86f);
    private final FitViewport viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
    private final Matrix4 rasterProjection = new Matrix4().setToOrtho2D(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final PixelFont font = new PixelFont();
    private final PixelSprites sprites = new PixelSprites();
    private final Texture scrollArtwork = new Texture(Gdx.files.internal("images/game-scrolls.png"));
    private final ActionFeedback feedback = new ActionFeedback();
    private final PostgameReview review = new PostgameReview();
    // A fixed raster gives shapes, lettering, and sprites the same two-logical-pixel grid.
    private final FrameBuffer canvas = new FrameBuffer(Pixmap.Format.RGBA8888, 800, 400, false);
    private final TextureRegion canvasImage;
    private float animationTime;
    private FlightSchool school;
    private ChessBoardView displayBoard;
    private boolean boardOnly;
    private boolean reducedMotion;

    public GameRenderer() { this(false); }

    public GameRenderer(boolean online) {
        scrollArtwork.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        canvas.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        canvasImage = new TextureRegion(canvas.getColorBufferTexture());
        canvasImage.flip(false, true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        viewport.getCamera().position.set(800, 400, 0);
        viewport.getCamera().update();
    }
    /** Browser touch layouts present clocks and actions outside the board. */
    public void setBoardOnly(boolean boardOnly) {
        if (this.boardOnly == boardOnly) return;
        this.boardOnly = boardOnly;
        viewport.setWorldSize(boardOnly ? 800 : WORLD_WIDTH, boardOnly ? 800 : WORLD_HEIGHT);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        feedback.setReducedMotion(reducedMotion);
    }
    public Vector2 unproject(int x, int y) { return viewport.unproject(new Vector2(x, y)); }
    public void toggleSound() { feedback.toggleSound(); }
    public void setSoundEnabled(boolean enabled) { feedback.setSoundEnabled(enabled); }
    public boolean isSoundEnabled() { return feedback.isSoundEnabled(); }
    public String feedbackMessage() { return feedback.message(); }
    public PostgameReview getReview() { return review; }
    public boolean isPlayAgainAt(Vector2 point) {
        return school != null ? inside(point, 650, 260, 300, 60) : inside(point, 584, 260, 208, 60);
    }
    public boolean isSwapAt(GameSession game, Vector2 point) {
        return game.isGameOver() && inside(point, 808, 260, 208, 60);
    }
    public static int promotionKeyAt(Vector2 point) {
        if (point.y < 278 || point.y > 396) return Keys.UNKNOWN;
        int[] keys = {Keys.Q, Keys.R, Keys.B, Keys.N};
        for (int i = 0; i < keys.length; i++) {
            if (inside(point, 584 + i * 112, 278, 96, 118)) return keys[i];
        }
        return Keys.UNKNOWN;
    }
    private static boolean inside(Vector2 point, float x, float y, float width, float height) {
        return point.x >= x && point.x <= x + width && point.y >= y && point.y <= y + height;
    }

    public void render(GameSession game) {
        render(game, null);
    }

    public void render(GameSession game, FlightSchool school) {
        render(game, school, null);
    }

    public void render(GameSession game, FlightSchool school, MatchSetup setup) {
        this.school = school;
        review.bind(game);
        displayBoard = review.isActive() ? review.getView() : game.getBoardView();
        float delta = Math.max(0, Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
        animationTime = (animationTime + delta) % 1000;
        feedback.update(game, delta);
        shapes.setProjectionMatrix(rasterProjection);
        batch.setProjectionMatrix(rasterProjection);
        canvas.begin();
        ScreenUtils.clear(MENU_SKY);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(scrollArtwork, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        batch.end();
        beginShapes();
        drawBoard(game);
        drawPanels(game);
        if (!review.isActive()) {
            drawDropReturnMarkers(game);
            drawBirdMarkers(game);
        }
        shapes.end();

        batch.begin();
        drawCoordinates();
        ChessBoardView board = displayBoard;
        GameSession.DropReturn dropReturn = !review.isActive() ? game.getDropReturn() : null;
        for (Square square : Square.values()) {
            if (square == Square.NONE || square == board.getHiddenPieceSquare()) continue;
            Piece piece = board.getBoard().getPiece(square);
            if (dropReturn != null && square == dropReturn.origin() && piece == dropReturn.piece()) continue;
            if (piece != Piece.NONE) drawPiece(piece, squareX(square) + SQUARE_WIDTH / 2,
                    squareY(square) + SQUARE_HEIGHT / 2 + feedback.dropOffset(square) * BOARD_SCALE_Y,
                    4, BOARD_SCALE_X, BOARD_SCALE_Y);
        }
        if (!review.isActive()) {
            drawBird(game.getWhite(), Side.WHITE, game);
            drawBird(game.getBlack(), Side.BLACK, game);
            if (dropReturn != null) drawDropReturnPiece(dropReturn);
        }
        drawPanelText(game);
        batch.end();
        beginShapes();
        if (!review.isActive()) {
            drawStunIndicators(game);
            feedback.drawBoardFlash(shapes);
            feedback.drawShapes(shapes);
            feedback.drawCalloutShapes(shapes);
        }
        shapes.end();
        if (!review.isActive() && feedback.hasCallout()) {
            batch.begin();
            feedback.drawCalloutText(batch, font);
            batch.end();
        }
        if (!boardOnly) {
            if (game.isGameOver() && !review.isActive() || game.getChess().hasPendingPromotion() || school != null && school.isComplete()) overlay(game);
            if (setup != null) setupOverlay(setup);
        }
        canvas.end();

        ScreenUtils.clear(MENU_SKY);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        float shakeX = review.isActive() ? 0 : feedback.shakeX();
        float shakeY = review.isActive() ? 0 : feedback.shakeY();
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(canvasImage, shakeX, shakeY, WORLD_WIDTH, WORLD_HEIGHT);
        batch.end();
    }

    private void drawBoard(GameSession game) {
        ChessBoardView board = displayBoard;
        CompletedMove last = lastMove(game);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Square square = ChessBoardView.squareForCell(row, col);
                float x = squareX(square), y = squareY(square);
                boolean light = (row + col) % 2 == 0;
                Color base = light ? LIGHT : DARK;
                Color edge = light ? LIGHT_EDGE : DARK_EDGE;
                Color shine = light ? LIGHT_SHINE : DARK_SHINE;
                rect(x, y, SQUARE_WIDTH, SQUARE_HEIGHT, edge);
                rect(x + bx(2), y + by(4), bx(96), by(94), base);
                rect(x + bx(4), y + by(96), bx(92), by(2), shine);
                rect(x + bx(2), y + by(6), bx(2), by(88), shine);
                // Sparse paper grain stays fixed to the board as the birds fly.
                for (int i = 0; i < 4; i++) {
                    int dx = 12 + (row * 17 + col * 29 + i * 23) % 74;
                    int dy = 12 + (row * 31 + col * 11 + i * 19) % 74;
                    rect(x + bx(dx / 2 * 2), y + by(dy / 2 * 2), bx(i % 2 == 0 ? 4 : 2), by(2), shine);
                }
                if (last != null && (square == last.from() || square == last.to())) {
                    rect(x + bx(4), y + by(4), bx(92), by(92), LAST_MOVE);
                    corners(x + bx(6), y + by(6), bx(88), by(88), by(16), by(2), MINT);
                }
                if (square == board.getSelectedSquare()) {
                    rect(x + bx(4), y + by(4), bx(92), by(92), SELECTED);
                    corners(x + bx(6), y + by(6), bx(88), by(88), by(18), by(4), GOLD);
                }
                Piece piece = board.getBoard().getPiece(square);
                GameSession.DropReturn drop = !review.isActive() ? game.getDropReturn() : null;
                boolean returningHere = drop != null && square == drop.origin() && piece == drop.piece();
                if (piece != Piece.NONE && square != board.getHiddenPieceSquare() && !returningHere) {
                    rect(x + bx(26), y + by(10), bx(48), by(6), SHADOW);
                    rect(x + bx(32), y + by(8), bx(36), by(2), SHADOW);
                }
                if (board.getLegalTargets().contains(square)) {
                    if (piece == Piece.NONE) {
                        diamond(x + SQUARE_WIDTH / 2, y + SQUARE_HEIGHT / 2, 10, INK);
                        diamond(x + SQUARE_WIDTH / 2, y + by(52), 4, MINT);
                    } else {
                        corners(x + bx(8), y + by(8), bx(84), by(84), by(22), by(6), DANGER);
                    }
                }
                if (piece.getPieceType() == PieceType.KING && piece.getPieceSide() == board.getBoard().getSideToMove()
                        && board.getBoard().isKingAttacked()) {
                    outline(x + bx(4), y + by(4), bx(92), by(92), by(4), DANGER);
                }
            }
        }
        if (school != null && school.target() != Square.NONE) {
            Square target = school.target();
            corners(squareX(target) + bx(6), squareY(target) + by(6), bx(88), by(88), by(26), by(6),
                    (int) (animationTime * 3) % 2 == 0 ? GOLD : INK);
        }
        if (!review.isActive() && !game.isGameOver() && !game.getChess().hasPendingPromotion()) {
            Side side = game.getChess().getSideToMove();
            Birb active = side == Side.WHITE ? game.getWhite() : game.getBlack();
            Square square = ChessBoardView.squareForPoint(active.getX() + 25, active.getY() + 25);
            if (square != Square.NONE) corners(squareX(square) + bx(8), squareY(square) + by(8),
                    bx(84), by(84), by(14), by(4), accent(side));
        }
    }

    private void drawPanels(GameSession game) {
        if (boardOnly) return;
        drawClockCard(game, Side.BLACK, 590);
        drawClockCard(game, Side.WHITE, 98);
    }

    private void drawClockCard(GameSession game, Side side, float y) {
        boolean active = !game.isGameOver() && game.getChess().getSideToMove() == side;
        long seconds = game.getClock().getRemainingSeconds(side);
        Color color = game.isTimed() && seconds <= 30 ? DANGER : accent(side);
        // A warm gold bevel surrounds the slate face, with a side-colored active inset.
        clockBevel(1220, y - 4, 216, 86, INK);
        clockBevel(1216, y, 216, 86, INK);
        clockBevel(1218, y + 2, 212, 82, GOLD);
        clockBevel(1222, y + 6, 204, 74, GOLD_BRIGHT);
        clockBevel(1226, y + 10, 196, 66, PANEL);
        clockBevel(1230, y + 14, 188, 58, active ? color : CARD);
        clockBevel(1232, y + 16, 184, 54, PANEL);
        diamond(1228, y + 43, 3, active ? color : GOLD);
        diamond(1420, y + 43, 3, active ? color : GOLD);
        for (int i = 0; i < 19; i++) {
            double fraction = game.isTimed() ? seconds * 1000.0 / game.getSettings().initialMillis() : 1;
            rect(1242 + i * 9, y + 15, 7, 3,
                    i < (int) Math.ceil(19 * Math.min(1, fraction)) ? color : EDGE);
        }
    }

    private void clockBevel(float x, float y, float width, float height, Color color) {
        rect(x + 12, y, width - 24, height, color);
        rect(x + 6, y + 6, width - 12, height - 12, color);
        rect(x, y + 12, width, height - 24, color);
    }

    private void drawCoordinates() {
        for (int i = 0; i < 8; i++) {
            text(Integer.toString(8 - i), BOARD_X + bx(12), BOARD_Y + BOARD_HEIGHT - by(12) - i * SQUARE_HEIGHT,
                    2, i % 2 == 0 ? DARK_EDGE : LIGHT_SHINE);
            text(Character.toString((char) ('a' + i)), BOARD_X + bx(80) + i * SQUARE_WIDTH,
                    BOARD_Y + by(22), 2, i % 2 == 0 ? LIGHT_SHINE : DARK_EDGE);
        }
    }

    private void drawPanelText(GameSession game) {
        if (boardOnly) return;
        drawBirdSprite(Side.BLACK, 284, 464, 6.5f, false, 0);
        drawBirdSprite(Side.WHITE, 1324, 460, 6.5f, true, 0);
        drawClockText(game, Side.BLACK, 590);
        drawClockText(game, Side.WHITE, 98);
    }

    private CompletedMove lastMove(GameSession game) {
        var moves = review.isActive() ? review.getRecord().moves() : game.getHistory();
        int ply = review.isActive() ? review.getPly() : moves.size();
        return ply == 0 ? null : moves.get(ply - 1);
    }

    private void drawClockText(GameSession game, Side side, float y) {
        long seconds = game.getClock().getRemainingSeconds(side);
        if (game.isTimed()) centered(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                1324, y + 64, 6, seconds <= 30 ? DANGER : GOLD_BRIGHT);
        else centered("UNTIMED", 1324, y + 58, 4, GOLD_BRIGHT);
    }

    private void drawBirdMarkers(GameSession game) {
        for (Side side : Side.values()) {
            Birb bird = side == Side.WHITE ? game.getWhite() : game.getBlack();
            float x = birdX(bird), y = birdY(bird);
            rect(x - 18, y - 24, 36, 6, SHADOW);
            if (!game.isGameOver() && side == game.getChess().getSideToMove()) {
                float markerY = Math.max(BOARD_Y, Math.min(BOARD_Y + BOARD_HEIGHT,
                        y + (side == Side.WHITE ? by(40) : -by(40))));
                diamond(x, markerY, 6, INK);
                diamond(x, markerY, 2, accent(side));
            }
            if (side == game.getCarrier()) {
                float pieceY = carriedY(y, side) + feedback.pickupOffset(side) * BOARD_SCALE_Y;
                rect(x - 8, Math.min(y, pieceY), 2, Math.abs(y - pieceY), GOLD);
                rect(x + 6, Math.min(y, pieceY), 2, Math.abs(y - pieceY), GOLD);
            }
        }
    }

    private void drawBird(Birb bird, Side side, GameSession game) {
        float x = birdX(bird), y = birdY(bird);
        int birdFrame = game.isGameOver() ? 1 : bird.isStunned() ? 2 : frame();
        if (bird.isStunned() && !reducedMotion && bird.getStunSteps() > GameSession.BUMP_STUN_STEPS / 2) {
            x += (bird.getStunSteps() % 2 == 0 ? 2 : -2);
        }
        drawBirdSprite(side, x, y, 4, bird.getVelocityX() < -0.1, birdFrame);
        if (side == game.getCarrier()) {
            Square carried = game.getBoardView().getHiddenPieceSquare();
            if (carried != Square.NONE) drawPiece(game.getBoardView().getBoard().getPiece(carried), x,
                    carriedY(y, side) + feedback.pickupOffset(side) * BOARD_SCALE_Y,
                    2, BOARD_SCALE_X, BOARD_SCALE_Y);
        }
    }

    private void drawStunIndicators(GameSession game) {
        for (Side side : Side.values()) {
            Birb bird = side == Side.WHITE ? game.getWhite() : game.getBlack();
            if (!bird.isStunned()) continue;
            float cx = birdX(bird);
            float cy = Math.min(BOARD_Y + BOARD_HEIGHT - 16, birdY(bird) + 32);
            // Brief impact brackets right after the collision hits.
            if (bird.getStunSteps() > GameSession.BUMP_STUN_STEPS - 6) {
                float left = Math.max(BOARD_X + 2, cx - 26);
                float bottom = Math.max(BOARD_Y + 2, birdY(bird) - 24);
                float width = Math.min(52, BOARD_X + BOARD_WIDTH - left - 2);
                float height = Math.min(48, BOARD_Y + BOARD_HEIGHT - bottom - 2);
                if (width > 16 && height > 16) {
                    corners(left, bottom, width, height, 10, 4,
                            bird.getStunSteps() % 2 == 0 ? GOLD_BRIGHT : DANGER);
                }
            }
            double baseAngle = reducedMotion
                    ? 0.35 + (side == Side.WHITE ? 0 : Math.PI)
                    : animationTime * 7.5 + (side == Side.WHITE ? 0 : Math.PI);
            for (int i = 0; i < 3; i++) {
                double angle = baseAngle + i * (Math.PI * 2 / 3);
                float sx = cx + (float) Math.cos(angle) * 26f;
                float sy = cy + (float) Math.sin(angle) * 9f;
                if (sx < BOARD_X + 8 || sx > BOARD_X + BOARD_WIDTH - 8
                        || sy < BOARD_Y + 8 || sy > BOARD_Y + BOARD_HEIGHT - 8) continue;
                rect(sx - 6, sy - 2, 14, 6, INK);
                rect(sx - 2, sy - 6, 6, 14, INK);
                star(sx, sy, 4, i == 0 ? GOLD_BRIGHT : GOLD);
            }
        }
    }

    private void drawDropReturnMarkers(GameSession game) {
        GameSession.DropReturn drop = game.getDropReturn();
        if (drop == null) return;
        Square origin = drop.origin();
        float ox = squareX(origin), oy = squareY(origin);
        boolean bright = (drop.stepsRemaining() / 3) % 2 == 0;
        corners(ox + bx(6), oy + by(6), bx(88), by(88), by(20), by(6), bright ? GOLD_BRIGHT : DANGER);
        float progress = 1f - (float) drop.stepsRemaining() / GameSession.DROP_RETURN_STEPS;
        float shadowW = bx(22 + 26 * progress);
        rect(ox + (SQUARE_WIDTH - shadowW) / 2, oy + by(10), shadowW, by(6), SHADOW);
        // Trailing pixel pips along the parabolic return path.
        for (int i = 1; i <= 3; i++) {
            float trailT = progress - i * 0.09f;
            if (trailT < 0) continue;
            float tx = dropReturnX(drop, trailT);
            float ty = dropReturnY(drop, trailT);
            if (tx >= BOARD_X + 4 && tx <= BOARD_X + BOARD_WIDTH - 8
                    && ty >= BOARD_Y + 4 && ty <= BOARD_Y + BOARD_HEIGHT - 8) {
                float size = i == 1 ? 6 : 4;
                rect(tx - size / 2, ty - size / 2, size, size, i == 1 ? GOLD_BRIGHT : GOLD);
            }
        }
    }

    private void drawDropReturnPiece(GameSession.DropReturn drop) {
        float t = 1f - (float) drop.stepsRemaining() / GameSession.DROP_RETURN_STEPS;
        float eased = 1f - (1f - t) * (1f - t);
        float px = dropReturnX(drop, t);
        float py = dropReturnY(drop, t);
        float scale = 2.2f + 1.8f * eased;
        drawPiece(drop.piece(), px, py, scale, BOARD_SCALE_X, BOARD_SCALE_Y);
    }

    private static float dropReturnX(GameSession.DropReturn drop, float t) {
        float eased = 1f - (1f - t) * (1f - t);
        float startX = BOARD_X + (float) drop.fromX() * BOARD_SCALE_X;
        float endX = squareX(drop.origin()) + SQUARE_WIDTH / 2f;
        return startX + (endX - startX) * eased;
    }

    private static float dropReturnY(GameSession.DropReturn drop, float t) {
        float eased = 1f - (1f - t) * (1f - t);
        float startY = BOARD_Y + (800f - (float) drop.fromY()) * BOARD_SCALE_Y;
        float endY = squareY(drop.origin()) + SQUARE_HEIGHT / 2f;
        float arc = 58f * 4f * t * (1f - t);
        return Math.max(BOARD_Y + by(24), Math.min(BOARD_Y + BOARD_HEIGHT - by(24),
                startY + (endY - startY) * eased + arc));
    }

    private static float carriedY(float birdY, Side side) {
        return Math.max(BOARD_Y + by(24), Math.min(BOARD_Y + BOARD_HEIGHT - by(24),
                birdY + (side == Side.WHITE ? -by(32) : by(32))));
    }

    private static float birdX(Birb bird) { return snap(BOARD_X + ((float) bird.getX() + 25) * BOARD_SCALE_X); }
    private static float birdY(Birb bird) { return snap(BOARD_Y + (775 - (float) bird.getY()) * BOARD_SCALE_Y); }

    private int frame() {
        int step = (int) (animationTime * 7) % 4;
        return step == 3 ? 1 : step;
    }

    private void drawBirdSprite(Side side, float x, float y, float scale, boolean left, int frame) {
        TextureRegion sprite = sprites.bird(side, frame);
        batch.draw(sprite, snap(x + (left ? 8 : -8) * scale), snap(y - 8 * scale),
                (left ? -16 : 16) * scale, 16 * scale);
    }

    private void drawPiece(Piece piece, float x, float y, int scale) {
        drawPiece(piece, x, y, scale, 1, 1);
    }

    private void drawPiece(Piece piece, float x, float y, float scale, float scaleX, float scaleY) {
        float width = 25 * scale * scaleX;
        float height = 25 * scale * scaleY;
        batch.draw(sprites.piece(piece.getPieceSide(), piece.getPieceType()), snap(x - width / 2),
                snap(y - height / 2), width, height);
    }

    private void overlay(GameSession game) {
        boolean complete = school != null && school.isComplete();
        boolean ended = game.isGameOver() || complete;
        Vector2 pointer = unproject(Gdx.input.getX(), Gdx.input.getY());
        boolean hover = pointer.x >= 650 && pointer.x <= 950 && pointer.y >= 260 && pointer.y <= 320;
        beginShapes();
        rect(0, 0, WORLD_WIDTH, WORLD_HEIGHT, VEIL);
        stepped(532, 216, 536, 368, PANEL, EDGE);
        outline(544, 228, 512, 344, 2, CARD);
        star(570, 548, 4, GOLD);
        star(1030, 548, 4, GOLD);
        rect(764, 554, 72, 4, GOLD);
        if (ended) {
            if (complete) stepped(650, 260, 300, 60, hover ? CREAM : MINT, INK);
            else {
                stepped(584, 260, 208, 60, MINT, INK);
                stepped(808, 260, 208, 60, BLUE, INK);
            }
        } else {
            for (int i = 0; i < 4; i++) stepped(584 + i * 112, 278, 96, 118, CARD, EDGE);
        }
        shapes.end();
        batch.begin();
        centered(complete ? "FLIGHT SCHOOL COMPLETE" : ended ? "ROUND " + game.getSeries().round() + " COMPLETE" : "LEVEL UP", 800, 528, 2, GOLD);
        centered(complete ? "READY TO PLAY!" : ended ? game.getResult().toUpperCase(Locale.ROOT) : "PROMOTION", 800, 486, 4, CREAM);
        centered(complete ? "Both birds mastered." : ended ? game.getReason() : "Choose a piece. The clock is running.", 800, 434, 2, MUTED);
        if (ended) {
            if (complete) {
                drawBirdSprite(Side.WHITE, 766, 376, 4, false, 1);
                drawBirdSprite(Side.BLACK, 834, 376, 4, true, 1);
            }
            if (complete) centered("PLAY LOCAL", 800, 298, 2, INK);
            else {
                centered("PLAY AGAIN", 688, 298, 2, INK);
                centered("SWAP COLORS", 912, 298, 2, INK);
            }
        } else {
            PieceType[] options = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
            String[] labels = {"Q QUEEN", "R ROOK", "B BISHOP", "N KNIGHT"};
            for (int i = 0; i < options.length; i++) {
                drawPiece(Piece.make(game.getChess().getSideToMove(), options[i]), 632 + i * 112, 352, 4);
                centered(labels[i], 632 + i * 112, 306, 2, GOLD);
            }
        }
        batch.end();
    }

    /** Menu-only scenery: far clouds, distant birds, near clouds, foreground birds. */
    private void drawMenuSky() {
        rect(0, 0, WORLD_WIDTH, WORLD_HEIGHT, MENU_SKY);
        menuCloud(80, 650, false);
        menuCloud(920, 690, false);
        menuCloud(1100, 310, false);
        menuCloud(400, 70, false);
        shapes.end();
        batch.begin();
        menuBird(0, 180, 48);
        menuBird(1, 600, 48);
        batch.end();
        beginShapes();
        menuCloud(180, 160, true);
        menuCloud(1220, 580, true);
        menuCloud(20, 390, true);
        menuCloud(1120, 80, true);
        shapes.end();
        batch.begin();
        menuBird(2, 710, 64);
        menuBird(3, 120, 64);
        batch.end();
        beginShapes();
    }

    private void menuCloud(float x, float y, boolean near) {
        float p = 6;
        rect(x, y, 30 * p, 5 * p, near ? MENU_CLOUD_NEAR_SHADE : MENU_CLOUD_FAR_SHADE);
        Color light = near ? MENU_CLOUD_NEAR : MENU_CLOUD_FAR;
        rect(x + 2 * p, y + 2 * p, 26 * p, 4 * p, light);
        rect(x + 6 * p, y + 3 * p, 9 * p, 6 * p, light);
        rect(x + 8 * p, y + 8 * p, 5 * p, 2 * p, light);
        rect(x + 17 * p, y + 3 * p, 7 * p, 5 * p, light);
    }

    private void menuBird(int index, float y, float size) {
        boolean right = index % 2 == 0;
        float progress = ((reducedMotion ? 0 : animationTime * (index < 2 ? 42 : 62))
                + index * 410 + 140) % (WORLD_WIDTH + 160);
        float x = right ? progress - 80 : WORLD_WIDTH + 80 - progress;
        int frame = reducedMotion ? 1 : (int) (animationTime * 8 + index) % 3;
        float bob = reducedMotion ? 0 : (float) Math.sin(animationTime * 2 + index) * 8;
        batch.setColor(Color.WHITE);
        batch.draw(sprites.bird(right ? Side.WHITE : Side.BLACK, frame),
                snap(x + (right ? 0 : size)), snap(y + bob), right ? size : -size, size);
    }

    private void setupOverlay(MatchSetup setup) {
        beginShapes();
        drawMenuSky();
        stepped(492, 146, 616, 510, PANEL, EDGE);
        stepped(580, 500, 216, 44, CARD, EDGE);
        stepped(804, 500, 216, 44, CARD, EDGE);
        stepped(580, 430, 440, 50, CARD, EDGE);
        stepped(580, 354, 440, 50, CARD, EDGE);
        stepped(650, 260, 300, 60, MINT, INK);
        stepped(580, 196, 216, 40, CARD, EDGE);
        stepped(804, 196, 216, 40, CARD, EDGE);
        shapes.end();
        batch.begin();
        centered("NEW MATCH", 800, 622, 4, CREAM);
        centered("CHOOSE THE MATCH OPTIONS", 800, 570, 2, MUTED);
        centered(setup.opponentLabel(), 688, 530, 2, GOLD);
        centered(setup.colorLabel(), 912, 530, 2, BLUE);
        centered(setup.timeLabel(), 800, 464, 2, GOLD);
        centered("CLICK TO CHANGE", 800, 424, 2, MUTED);
        centered(setup.incrementLabel(), 800, 388, 2, BLUE);
        centered("CLICK TO CHANGE", 800, 348, 2, MUTED);
        centered("START", 800, 298, 2, INK);
        centered("CHESS " + setup.chessLabel(), 688, 224, setup.chessLabel().length() > 7 ? 1 : 2, CREAM);
        centered("FLIGHT " + setup.flightLabel(), 912, 224, 2, CREAM);
        centered("CLICK A CHOICE TO CHANGE IT", 800, 184, 2, MUTED);
        batch.end();
    }

    private static Color accent(Side side) { return side == Side.WHITE ? GOLD : BLUE; }
    private static float squareX(Square square) { return BOARD_X + square.getFile().ordinal() * SQUARE_WIDTH; }
    private static float squareY(Square square) { return BOARD_Y + square.getRank().ordinal() * SQUARE_HEIGHT; }
    private static float bx(float value) { return value * BOARD_SCALE_X; }
    private static float by(float value) { return value * BOARD_SCALE_Y; }
    private static float snap(float value) { return Math.round(value / 2) * 2; }

    private void beginShapes() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    private void rect(float x, float y, float w, float h, Color color) {
        shapes.setColor(color);
        shapes.rect(snap(x), snap(y), snap(w), snap(h));
    }

    private void outline(float x, float y, float w, float h, float thickness, Color color) {
        rect(x, y, w, thickness, color);
        rect(x, y + h - thickness, w, thickness, color);
        rect(x, y, thickness, h, color);
        rect(x + w - thickness, y, thickness, h, color);
    }

    private void corners(float x, float y, float w, float h, float length, float thickness, Color color) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                rect(x + i * (w - length), y + j * (h - thickness), length, thickness, color);
                rect(x + i * (w - thickness), y + j * (h - length), thickness, length, color);
            }
        }
    }

    private void stepped(float x, float y, float w, float h, Color fill, Color border) {
        rect(x + 6, y - 4, w - 8, h, INK);
        rect(x + 4, y, w - 8, h, border);
        rect(x, y + 4, w, h - 8, border);
        rect(x + 4, y + 4, w - 8, h - 8, fill);
    }

    private void diamond(float x, float y, int radius, Color color) {
        for (int offset = -radius + 2; offset < radius; offset += 2) {
            int width = radius - Math.abs(offset);
            rect(x - width, y + offset, width * 2, 2, color);
        }
    }

    private void star(float x, float y, int radius, Color color) {
        rect(x - radius, y, radius * 2 + 2, 2, color);
        rect(x, y - radius, 2, radius * 2 + 2, color);
    }

    private void triangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        shapes.setColor(color);
        shapes.triangle(snap(x1), snap(y1), snap(x2), snap(y2), snap(x3), snap(y3));
    }

    private void text(String value, float x, float y, int scale, Color color) {
        font.draw(batch, value, snap(x), snap(y), scale, color);
    }

    private void centered(String value, float x, float y, int scale, Color color) {
        text(value, x - font.width(value, scale) / 2f, y, scale, color);
    }

    @Override public void dispose() {
        feedback.dispose();
        canvas.dispose();
        sprites.dispose();
        scrollArtwork.dispose();
        font.dispose();
        batch.dispose();
        shapes.dispose();
    }
}
