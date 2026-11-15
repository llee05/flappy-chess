package com.example.flappychess.view;

import com.badlogic.gdx.Gdx;
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
    private static final Color LIGHT = Color.valueOf("daccaa");
    private static final Color LIGHT_EDGE = Color.valueOf("bdb496");
    private static final Color LIGHT_SHINE = Color.valueOf("e5d9ba");
    private static final Color DARK = Color.valueOf("78958e");
    private static final Color DARK_EDGE = Color.valueOf("657e7c");
    private static final Color DARK_SHINE = Color.valueOf("87a297");
    private static final Color PANEL = Color.valueOf("20283c");
    private static final Color CARD = Color.valueOf("2c354d");
    private static final Color EDGE = Color.valueOf("46516a");
    private static final Color INK = Color.valueOf("202538");
    private static final Color CREAM = Color.valueOf("f1dfb3");
    private static final Color MINT = Color.valueOf("add2b2");
    private static final Color GOLD = Color.valueOf("efb95b");
    private static final Color BLUE = Color.valueOf("78c4d0");
    private static final Color SKY_HIGH = Color.valueOf("4d83ad");
    private static final Color SKY_MID = Color.valueOf("6ba8c4");
    private static final Color SKY_LOW = Color.valueOf("a8d5d4");
    private static final Color CLOUD = Color.valueOf("e9e4c9");
    private static final Color CLOUD_SHADE = Color.valueOf("c5d4c4");
    private static final Color MUTED = Color.valueOf("94a3b8");
    private static final Color DANGER = Color.valueOf("ed8c78");
    private static final Color SELECTED = Color.valueOf("baa96d");
    private static final Color LAST_MOVE = new Color(0.94f, 0.73f, 0.35f, 0.24f);
    private static final Color SHADOW = new Color(0.08f, 0.12f, 0.18f, 0.22f);
    private static final Color VEIL = new Color(0.06f, 0.08f, 0.15f, 0.86f);
    private final FitViewport viewport = new FitViewport(1200, 800);
    private final Matrix4 rasterProjection = new Matrix4().setToOrtho2D(0, 0, 1200, 800);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final PixelFont font = new PixelFont();
    private final PixelSprites sprites = new PixelSprites();
    private final ActionFeedback feedback = new ActionFeedback();
    private final PostgameReview review = new PostgameReview();
    // A fixed raster gives shapes, lettering, and sprites the same two-logical-pixel grid.
    private final FrameBuffer canvas = new FrameBuffer(Pixmap.Format.RGBA8888, 600, 400, false);
    private final TextureRegion canvasImage;
    private float animationTime;
    private FlightSchool school;
    private ChessBoardView displayBoard;
    private boolean boardOnly;

    public GameRenderer() { this(false); }

    public GameRenderer(boolean online) {
        canvas.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        canvasImage = new TextureRegion(canvas.getColorBufferTexture());
        canvasImage.flip(false, true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        viewport.getCamera().position.set(600, 400, 0);
        viewport.getCamera().update();
    }
    /** Browser touch layouts present clocks and actions outside the board. */
    public void setBoardOnly(boolean boardOnly) {
        if (this.boardOnly == boardOnly) return;
        this.boardOnly = boardOnly;
        viewport.setWorldSize(boardOnly ? 800 : 1200, 800);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
    public Vector2 unproject(int x, int y) { return viewport.unproject(new Vector2(x, y)); }
    public void toggleSound() { feedback.toggleSound(); }
    public boolean isSoundEnabled() { return feedback.isSoundEnabled(); }
    public String feedbackMessage() { return feedback.message(); }
    public PostgameReview getReview() { return review; }
    public boolean isPlayAgainAt(Vector2 point) {
        return school != null ? inside(point, 450, 260, 300, 60) : inside(point, 384, 260, 208, 60);
    }
    public boolean isSwapAt(GameSession game, Vector2 point) {
        return game.isGameOver() && inside(point, 608, 260, 208, 60);
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
        ScreenUtils.clear(SKY_HIGH);
        beginShapes();
        drawSkyBackdrop();
        drawBoard(game);
        drawPanels(game);
        if (!review.isActive()) drawBirdMarkers(game);
        shapes.end();

        batch.begin();
        drawCoordinates();
        ChessBoardView board = displayBoard;
        for (Square square : Square.values()) {
            if (square == Square.NONE || square == board.getHiddenPieceSquare()) continue;
            Piece piece = board.getBoard().getPiece(square);
            if (piece != Piece.NONE) drawPiece(piece, squareX(square) + 50,
                    squareY(square) + 50 + feedback.dropOffset(square), 4);
        }
        if (!review.isActive()) {
            drawBird(game.getWhite(), Side.WHITE, game);
            drawBird(game.getBlack(), Side.BLACK, game);
        }
        drawPanelText(game);
        batch.end();
        beginShapes();
        if (!review.isActive()) feedback.drawShapes(shapes);
        shapes.end();
        if (!boardOnly) {
            if (game.isGameOver() && !review.isActive() || game.getChess().hasPendingPromotion() || school != null && school.isComplete()) overlay(game);
            if (setup != null) setupOverlay(setup);
        }
        canvas.end();

        ScreenUtils.clear(SKY_HIGH);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(canvasImage, 0, 0, 1200, 800);
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
                rect(x, y, 100, 100, edge);
                rect(x + 2, y + 4, 96, 94, base);
                rect(x + 4, y + 96, 92, 2, shine);
                rect(x + 2, y + 6, 2, 88, shine);
                // Sparse, deterministic stone flecks: no texture shimmer between frames.
                for (int i = 0; i < 4; i++) {
                    int dx = 12 + (row * 17 + col * 29 + i * 23) % 74;
                    int dy = 12 + (row * 31 + col * 11 + i * 19) % 74;
                    rect(x + dx / 2 * 2, y + dy / 2 * 2, i % 2 == 0 ? 4 : 2, 2, shine);
                }
                if (last != null && (square == last.from() || square == last.to())) {
                    rect(x + 4, y + 4, 92, 92, LAST_MOVE);
                    corners(x + 6, y + 6, 88, 88, 16, 2, MINT);
                }
                if (square == board.getSelectedSquare()) {
                    rect(x + 4, y + 4, 92, 92, SELECTED);
                    corners(x + 6, y + 6, 88, 88, 18, 4, GOLD);
                }
                Piece piece = board.getBoard().getPiece(square);
                if (piece != Piece.NONE && square != board.getHiddenPieceSquare()) {
                    rect(x + 26, y + 10, 48, 6, SHADOW);
                    rect(x + 32, y + 8, 36, 2, SHADOW);
                }
                if (board.getLegalTargets().contains(square)) {
                    if (piece == Piece.NONE) {
                        diamond(x + 50, y + 50, 10, INK);
                        diamond(x + 50, y + 52, 4, MINT);
                    } else {
                        corners(x + 8, y + 8, 84, 84, 22, 6, DANGER);
                    }
                }
                if (piece.getPieceType() == PieceType.KING && piece.getPieceSide() == board.getBoard().getSideToMove()
                        && board.getBoard().isKingAttacked()) {
                    outline(x + 4, y + 4, 92, 92, 4, DANGER);
                }
            }
        }
        if (school != null && school.target() != Square.NONE) {
            Square target = school.target();
            corners(squareX(target) + 6, squareY(target) + 6, 88, 88, 26, 6,
                    (int) (animationTime * 3) % 2 == 0 ? GOLD : INK);
        }
        if (!review.isActive() && !game.isGameOver() && !game.getChess().hasPendingPromotion()) {
            Side side = game.getChess().getSideToMove();
            Birb active = side == Side.WHITE ? game.getWhite() : game.getBlack();
            Square square = ChessBoardView.squareForPoint(active.getX() + 25, active.getY() + 25);
            if (square != Square.NONE) corners(squareX(square) + 8, squareY(square) + 8, 84, 84, 14, 4, accent(side));
        }
    }

    private void drawPanels(GameSession game) {
        rect(196, 0, 4, 800, EDGE);
        rect(1000, 0, 4, 800, INK);
        rect(1004, 0, 2, 800, EDGE);
        drawClockCard(game, Side.BLACK, 666);
        drawClockCard(game, Side.WHITE, 24);
    }

    private void drawSkyBackdrop() {
        rect(0, 0, 1200, 800, SKY_LOW);
        rect(0, 250, 1200, 550, SKY_MID);
        rect(0, 560, 1200, 240, SKY_HIGH);
        diamond(94, 690, 28, GOLD);
        cloud(16, 546, 2);
        cloud(82, 432, 1);
        cloud(1018, 414, 2);
        cloud(1080, 274, 1);
    }

    private void cloud(float x, float y, int scale) {
        float p = 8 * scale;
        rect(x + p, y, p * 7, p, CLOUD_SHADE);
        rect(x, y + p, p * 10, p * 2, CLOUD);
        rect(x + p * 2, y + p * 3, p * 6, p, CLOUD);
        rect(x + p * 3, y + p * 4, p * 2, p, CLOUD);
        rect(x + p * 6, y + p * 4, p, p, CLOUD);
    }

    private void drawClockCard(GameSession game, Side side, float y) {
        boolean active = !game.isGameOver() && game.getChess().getSideToMove() == side;
        long seconds = game.getClock().getRemainingSeconds(side);
        Color color = game.isTimed() && seconds <= 30 ? DANGER : accent(side);
        stepped(1016, y, 168, 110, CARD, active ? color : EDGE);
        if (active) rect(1016, y + 22, 4, 66, color);
        for (int i = 0; i < 17; i++) {
            double fraction = game.isTimed() ? seconds * 1000.0 / game.getSettings().initialMillis() : 1;
            rect(1032 + i * 8, y + 12, 6, 3, i < (int) Math.ceil(17 * Math.min(1, fraction)) ? color : EDGE);
        }
    }

    private void drawCoordinates() {
        for (int i = 0; i < 8; i++) {
            text(Integer.toString(8 - i), 208, 792 - i * 100, 2, i % 2 == 0 ? DARK_EDGE : LIGHT_SHINE);
            text(Character.toString((char) ('a' + i)), 286 + i * 100, 18, 2, i % 2 == 0 ? LIGHT_SHINE : DARK_EDGE);
        }
    }

    private void drawPanelText(GameSession game) {
        drawClockText(game, Side.BLACK, 666);
        drawClockText(game, Side.WHITE, 24);
    }

    private CompletedMove lastMove(GameSession game) {
        var moves = review.isActive() ? review.getRecord().moves() : game.getHistory();
        int ply = review.isActive() ? review.getPly() : moves.size();
        return ply == 0 ? null : moves.get(ply - 1);
    }

    private void drawClockText(GameSession game, Side side, float y) {
        long seconds = game.getClock().getRemainingSeconds(side);
        if (game.isTimed()) centered(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                1100, y + 66, 4, seconds <= 30 ? DANGER : CREAM);
        else centered("UNTIMED", 1100, y + 64, 2, CREAM);
    }

    private void drawBirdMarkers(GameSession game) {
        for (Side side : Side.values()) {
            Birb bird = side == Side.WHITE ? game.getWhite() : game.getBlack();
            float x = snap(225 + (float) bird.getX()), y = snap(775 - (float) bird.getY());
            rect(x - 18, y - 24, 36, 6, SHADOW);
            if (!game.isGameOver() && side == game.getChess().getSideToMove()) {
                float markerY = Math.max(16, Math.min(784, y + (side == Side.WHITE ? 40 : -40)));
                diamond(x, markerY, 6, INK);
                diamond(x, markerY, 2, accent(side));
            }
            if (side == game.getCarrier()) {
                float pieceY = carriedY(y, side) + feedback.pickupOffset(side);
                rect(x - 8, Math.min(y, pieceY), 2, Math.abs(y - pieceY), GOLD);
                rect(x + 6, Math.min(y, pieceY), 2, Math.abs(y - pieceY), GOLD);
            }
        }
    }

    private void drawBird(Birb bird, Side side, GameSession game) {
        float x = snap(225 + (float) bird.getX()), y = snap(775 - (float) bird.getY());
        drawBirdSprite(side, x, y, 4, bird.getVelocityX() < -0.1, game.isGameOver() ? 1 : frame());
        if (side == game.getCarrier()) {
            Square carried = game.getBoardView().getHiddenPieceSquare();
            if (carried != Square.NONE) drawPiece(game.getBoardView().getBoard().getPiece(carried), x,
                    carriedY(y, side) + feedback.pickupOffset(side), 2);
        }
    }

    private static float carriedY(float birdY, Side side) {
        return Math.max(24, Math.min(776, birdY + (side == Side.WHITE ? -32 : 32)));
    }

    private int frame() {
        int step = (int) (animationTime * 7) % 4;
        return step == 3 ? 1 : step;
    }

    private void drawBirdSprite(Side side, float x, float y, int scale, boolean left, int frame) {
        TextureRegion sprite = sprites.bird(side, frame);
        batch.draw(sprite, snap(x + (left ? 8 : -8) * scale), snap(y - 8 * scale),
                (left ? -16 : 16) * scale, 16 * scale);
    }

    private void drawPiece(Piece piece, float x, float y, int scale) {
        batch.draw(sprites.piece(piece.getPieceSide(), piece.getPieceType()), snap(x - 8 * scale), snap(y - 10 * scale), 16 * scale, 20 * scale);
    }

    private void overlay(GameSession game) {
        boolean complete = school != null && school.isComplete();
        boolean ended = game.isGameOver() || complete;
        Vector2 pointer = unproject(Gdx.input.getX(), Gdx.input.getY());
        boolean hover = pointer.x >= 450 && pointer.x <= 750 && pointer.y >= 260 && pointer.y <= 320;
        beginShapes();
        rect(0, 0, 1200, 800, VEIL);
        stepped(332, 216, 536, 368, PANEL, EDGE);
        outline(344, 228, 512, 344, 2, CARD);
        star(370, 548, 4, GOLD);
        star(830, 548, 4, GOLD);
        rect(564, 554, 72, 4, GOLD);
        if (ended) {
            if (complete) stepped(450, 260, 300, 60, hover ? CREAM : MINT, INK);
            else {
                stepped(384, 260, 208, 60, MINT, INK);
                stepped(608, 260, 208, 60, BLUE, INK);
            }
        } else {
            for (int i = 0; i < 4; i++) stepped(384 + i * 112, 278, 96, 118, CARD, EDGE);
        }
        shapes.end();
        batch.begin();
        centered(complete ? "FLIGHT SCHOOL COMPLETE" : ended ? "ROUND " + game.getSeries().round() + " COMPLETE" : "LEVEL UP", 600, 528, 2, GOLD);
        centered(complete ? "READY TO PLAY!" : ended ? game.getResult().toUpperCase(Locale.ROOT) : "PROMOTION", 600, 486, 4, CREAM);
        centered(complete ? "Both birds mastered." : ended ? game.getReason() : "Choose a piece. The clock is running.", 600, 434, 2, MUTED);
        if (ended) {
            if (complete) {
                drawBirdSprite(Side.WHITE, 566, 376, 4, false, 1);
                drawBirdSprite(Side.BLACK, 634, 376, 4, true, 1);
            }
            if (complete) centered("PLAY LOCAL", 600, 298, 2, INK);
            else {
                centered("PLAY AGAIN", 488, 298, 2, INK);
                centered("SWAP COLORS", 712, 298, 2, INK);
            }
        } else {
            PieceType[] options = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
            String[] labels = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"};
            for (int i = 0; i < options.length; i++) {
                drawPiece(Piece.make(game.getChess().getSideToMove(), options[i]), 432 + i * 112, 352, 4);
                centered(labels[i], 432 + i * 112, 306, 2, GOLD);
            }
        }
        batch.end();
    }

    private void setupOverlay(MatchSetup setup) {
        beginShapes();
        rect(0, 0, 1200, 800, VEIL);
        stepped(292, 146, 616, 510, PANEL, EDGE);
        stepped(380, 500, 216, 44, CARD, EDGE);
        stepped(604, 500, 216, 44, CARD, EDGE);
        stepped(380, 430, 440, 50, CARD, EDGE);
        stepped(380, 354, 440, 50, CARD, EDGE);
        stepped(450, 260, 300, 60, MINT, INK);
        stepped(380, 196, 216, 40, CARD, EDGE);
        stepped(604, 196, 216, 40, CARD, EDGE);
        shapes.end();
        batch.begin();
        centered("NEW MATCH", 600, 622, 4, CREAM);
        centered("CHOOSE THE MATCH OPTIONS", 600, 570, 2, MUTED);
        centered(setup.opponentLabel(), 488, 530, 2, GOLD);
        centered(setup.colorLabel(), 712, 530, 2, BLUE);
        centered(setup.timeLabel(), 600, 464, 2, GOLD);
        centered("CLICK TO CHANGE", 600, 424, 2, MUTED);
        centered(setup.incrementLabel(), 600, 388, 2, BLUE);
        centered("CLICK TO CHANGE", 600, 348, 2, MUTED);
        centered("START", 600, 298, 2, INK);
        centered("CHESS " + setup.chessLabel(), 488, 224, 2, CREAM);
        centered("FLIGHT " + setup.flightLabel(), 712, 224, 2, CREAM);
        centered("CLICK A CHOICE TO CHANGE IT", 600, 184, 2, MUTED);
        batch.end();
    }

    private static Color accent(Side side) { return side == Side.WHITE ? GOLD : BLUE; }
    private static float squareX(Square square) { return 200 + square.getFile().ordinal() * 100; }
    private static float squareY(Square square) { return square.getRank().ordinal() * 100; }
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
        font.dispose();
        batch.dispose();
        shapes.dispose();
    }
}
