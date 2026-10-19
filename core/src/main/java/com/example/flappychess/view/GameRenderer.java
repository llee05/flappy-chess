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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.controller.FlightSchool;
import com.example.flappychess.controller.MatchSetup;
import com.example.flappychess.controller.GameRecord;
import com.example.flappychess.controller.PostgameReview;
import com.example.flappychess.controller.ChessController.CompletedMove;
import com.example.flappychess.model.Birb;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.Locale;
import java.util.function.Function;

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
    private static final Color MUTED = Color.valueOf("94a3b8");
    private static final Color DANGER = Color.valueOf("ed8c78");
    private static final Color SELECTED = Color.valueOf("baa96d");
    private static final Color LAST_MOVE = new Color(0.94f, 0.73f, 0.35f, 0.24f);
    private static final Color SHADOW = new Color(0.08f, 0.12f, 0.18f, 0.22f);
    private static final Color VEIL = new Color(0.06f, 0.08f, 0.15f, 0.86f);
    private final FitViewport viewport = new FitViewport(1200, 800);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final PixelFont font = new PixelFont();
    private final PixelSprites sprites = new PixelSprites();
    private final ActionFeedback feedback = new ActionFeedback();
    private final PostgameReview review = new PostgameReview();
    // A fixed raster gives shapes, lettering, and sprites the same two-logical-pixel grid.
    private final FrameBuffer canvas = new FrameBuffer(Pixmap.Format.RGBA8888, 600, 400, false);
    private final TextureRegion canvasImage;
    private final boolean online;
    private float animationTime;
    private FlightSchool school;
    private ChessBoardView displayBoard;

    public GameRenderer() { this(false); }

    public GameRenderer(boolean online) {
        this.online = online;
        canvas.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        canvasImage = new TextureRegion(canvas.getColorBufferTexture());
        canvasImage.flip(false, true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) { viewport.update(width, height, true); }
    public Vector2 unproject(int x, int y) { return viewport.unproject(new Vector2(x, y)); }
    public void toggleSound() { feedback.toggleSound(); }
    public boolean isSoundEnabled() { return feedback.isSoundEnabled(); }
    public String feedbackMessage() { return feedback.message(); }
    public boolean isSoundToggleAt(Vector2 point) {
        return point.x >= 16 && point.x <= 184 && point.y >= 658 && point.y <= 684;
    }
    public boolean isSetupAt(Vector2 point) {
        return !online && point.x >= 16 && point.x <= 184 && point.y >= 580 && point.y <= 656;
    }
    public PostgameReview getReview() { return review; }
    public boolean historyKey(GameSession game, int key) {
        review.bind(game);
        boolean handled = review.keyDown(key);
        if (handled) feedback.clearPresentation();
        return handled;
    }
    public void exportPgn(GameSession game, Function<String, String> exporter) {
        try { feedback.notice(exporter.apply(new GameRecord(game).pgn())); }
        catch (RuntimeException unavailable) {
            feedback.notice("PGN export failed.");
            Gdx.app.log("PGN", "The game could not be exported.");
        }
    }
    public boolean isExportAt(GameSession game, Vector2 point) {
        return game.isGameOver() && (review.isActive()
                ? inside(point, 1016, 210, 168, 30) : inside(point, 608, 348, 208, 48));
    }
    public boolean isPlayAgainAt(Vector2 point) {
        return review.isActive() ? inside(point, 1016, 174, 84, 30)
                : school != null ? inside(point, 450, 260, 300, 60) : inside(point, 384, 260, 208, 60);
    }
    public boolean isSwapAt(GameSession game, Vector2 point) {
        return game.isGameOver() && (review.isActive() ? inside(point, 1100, 174, 84, 30) : inside(point, 608, 260, 208, 60));
    }
    public boolean historyClick(GameSession game, Vector2 point) {
        review.bind(game);
        if (!game.isGameOver()) return false;
        if (!review.isActive()) {
            if (!inside(point, 384, 348, 208, 48)) return false;
            review.keyDown(Keys.V);
            feedback.clearPresentation();
            return true;
        }
        if (inside(point, 1016, 400, 168, 30)) review.seek(review.getPly() - 1);
        else if (inside(point, 1016, 362, 168, 30)) review.seek(review.getPly() + 1);
        else if (inside(point, 1016, 324, 168, 30)) review.seek(0);
        else if (inside(point, 1016, 286, 168, 30)) review.seek(review.getRecord().moves().size());
        else if (inside(point, 1016, 248, 168, 30)) review.keyDown(Keys.V);
        else return false;
        feedback.clearPresentation();
        return true;
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
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        canvas.begin();
        ScreenUtils.clear(PANEL);
        beginShapes();
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
        batch.begin();
        feedback.drawText(font, batch);
        batch.end();
        if (school != null && !school.isComplete()) lessonBanner();
        if (game.isGameOver() && !review.isActive() || game.getChess().hasPendingPromotion() || school != null && school.isComplete()) overlay(game);
        if (setup != null) setupOverlay(setup);
        canvas.end();

        ScreenUtils.clear(INK);
        viewport.apply();
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
        rect(0, 0, 200, 800, PANEL);
        rect(1000, 0, 200, 800, PANEL);
        rect(192, 0, 4, 800, EDGE);
        rect(196, 0, 4, 800, INK);
        rect(1000, 0, 4, 800, INK);
        rect(1004, 0, 4, 800, EDGE);
        rect(20, 776, 24, 4, GOLD);
        rect(48, 776, 8, 4, GOLD);
        star(168, 744, 4, GOLD);
        star(158, 704, 2, BLUE);
        stepped(16, 580, 168, 76, CARD, EDGE);
        rect(20, 566, 156, 2, EDGE);
        int rows = school == null ? (game.getHistory().size() + 1) / 2 : 0;
        int firstRow = journalFirstRow(game, rows);
        for (int row = firstRow; row < Math.min(rows, firstRow + 10); row++) {
            float y = 480 - (row - firstRow) * 22;
            if ((row - firstRow) % 2 == 0) rect(16, y - 2, 168, 22, CARD);
            int selectedRow = review.isActive() ? review.getPly() == 0 ? -1 : (review.getPly() - 1) / 2 : rows - 1;
            if (row == selectedRow) rect(16, y, 2, 16, MINT);
        }
        rect(20, 134, 156, 2, EDGE);
        stepped(16, 24, 42, 24, CARD, EDGE);
        drawClockCard(game, Side.BLACK, 650);
        drawClockCard(game, Side.WHITE, 24);
        stepped(1016, 550, 168, 76, CARD, EDGE);
        diamond(1032, 608, 4, game.isGameOver() ? MUTED : MINT);
        if (review.isActive()) {
            for (int y : new int[] {400, 362, 324, 286, 248, 210, 174}) stepped(1016, y, 168, 30, CARD, EDGE);
        } else {
            rect(1020, 340, 160, 2, EDGE);
            for (int y : new int[] {462, 428, 394, 260, 226, 192}) drawKey(1020, y, 68);
        }
    }

    private void drawClockCard(GameSession game, Side side, float y) {
        boolean active = !game.isGameOver() && game.getChess().getSideToMove() == side;
        long seconds = game.getClock().getRemainingSeconds(side);
        Color color = game.isTimed() && seconds <= 30 ? DANGER : accent(side);
        stepped(1016, y, 168, 130, CARD, active ? color : EDGE);
        if (active) rect(1016, y + 38, 4, 54, color);
        for (int i = 0; i < 17; i++) {
            double fraction = game.isTimed() ? seconds * 1000.0 / game.getSettings().initialMillis() : 1;
            rect(1032 + i * 8, y + 14, 6, 4, i < (int) Math.ceil(17 * Math.min(1, fraction)) ? color : EDGE);
        }
    }

    private void drawKey(float x, float y, float width) {
        stepped(x, y, width, 26, EDGE, INK);
        rect(x + 4, y + 22, width - 8, 2, MUTED);
    }

    private void drawCoordinates() {
        for (int i = 0; i < 8; i++) {
            text(Integer.toString(8 - i), 208, 792 - i * 100, 2, i % 2 == 0 ? DARK_EDGE : LIGHT_SHINE);
            text(Character.toString((char) ('a' + i)), 286 + i * 100, 18, 2, i % 2 == 0 ? LIGHT_SHINE : DARK_EDGE);
        }
    }

    private void drawPanelText(GameSession game) {
        text("FLAPPY", 20, 760, 4, CREAM);
        text("CHESS", 20, 720, 4, CREAM);
        text(feedback.isSoundEnabled() ? "M SOUND: ON" : "M SOUND: OFF", 16, 674, 2, MUTED);
        centered(school != null ? "FLIGHT SCHOOL" : "ROUND " + game.getSeries().round(), 100, 642, 2, GOLD);
        centered(school != null ? "UNTIMED" : game.getSeries().score(), 100, 620, 2, MINT);
        centered(school != null ? "T RESTART" : (online ? "ONLINE " : "S SETUP ") + game.getSettings().label(), 100, 596, 2, MUTED);
        if (school != null) {
            text("YOUR LESSON", 20, 550, 2, CREAM);
            text("NO CLOCK.\nNO RUSH.", 16, 508, 2, MINT);
            String[] steps = {"WHITE: FLY", "PICK UP", "DELIVER", "BLACK: FLY", "PICK UP", "DELIVER"};
            for (int i = 0; i < steps.length; i++) {
                text((i + 1) + " " + steps[i], 16, 434 - i * 38, 2,
                        i < school.getStage().ordinal() ? MINT : i == school.getStage().ordinal() ? GOLD : MUTED);
            }
            text("DROP AT START\nTO CANCEL.", 16, 216, 2, MUTED);
        } else {
            drawMoveLog(game);
            drawCaptured(game, Side.WHITE, 270, 232);
            drawCaptured(game, Side.BLACK, 206, 168);
        }
        if (school != null) text("T FLIGHT SCHOOL", 16, 154, 2, MINT);
        if (feedback.message().isEmpty()) {
            text("MAKE EVERY", 20, 114, 2, CREAM);
            text("FLAP COUNT.", 20, 92, 2, GOLD);
        }
        centered("ESC", 37, 42, 2, CREAM);
        text("EXIT GAME", 66, 42, 2, MUTED);
        drawClockText(game, Side.BLACK, 650);
        drawClockText(game, Side.WHITE, 24);
        text(game.isGameOver() ? "FINISHED" : "TURN " + String.format(Locale.ROOT, "%02d", game.getHistory().size() / 2 + 1), 1046, 614, 2, MINT);
        String status = game.isGameOver() ? "WELL PLAYED!" : game.getChess().getSideToMove() == Side.WHITE ? "WHITE TO MOVE" : "BLACK TO MOVE";
        if (!game.isGameOver()) {
            if (game.getBoardView().getBoard().isKingAttacked()) status = "KING IN CHECK";
            else if (game.getCarrier() != null) status = "DELIVER PIECE";
        }
        centered(status, 1100, 584, 2, CREAM);
        if (review.isActive()) { drawReviewControls(); return; }
        boolean firstWhite = school != null || game.getSeries().firstWhite();
        text(game.getSeries().shortNameFor(Side.WHITE) + " WHITE", 1020, 534, 2, GOLD);
        text("FALLS DOWN", 1020, 508, 2, MUTED);
        control(firstWhite ? "A / D" : "< / >", "MOVE", 462);
        control(firstWhite ? "SPACE" : "UP", "FLAP", 428);
        control(firstWhite ? "E" : "ENTER", "CARRY", 394);
        text("PICK UP / DROP", 1020, 376, 2, MUTED);
        text(game.getSeries().shortNameFor(Side.BLACK) + " BLACK", 1020, 328, 2, BLUE);
        text("FALLS UP", 1020, 302, 2, MUTED);
        control(firstWhite ? "< / >" : "A / D", "MOVE", 260);
        control(firstWhite ? "UP" : "SPACE", "FLAP", 226);
        control(firstWhite ? "ENTER" : "E", "CARRY", 192);
        text("1 PRESS 1 FLAP", 1020, 176, 2, MUTED);
    }

    private void drawMoveLog(GameSession game) {
        text("MOVE LOG", 20, 550, 2, CREAM);
        text("#", 22, 520, 2, MUTED);
        text("WHITE", 44, 520, 2, GOLD);
        text("BLACK", 116, 520, 2, BLUE);
        var moves = game.getHistory();
        if (moves.isEmpty()) {
            text("TAKE FLIGHT.", 20, 478, 2, CREAM);
            text("Find a piece.\nE to pick up.", 20, 448, 2, MUTED);
            text("01", 20, 396, 4, EDGE);
            text("FLY", 78, 392, 2, MINT);
            text("02", 20, 344, 4, EDGE);
            text("PICK UP", 78, 340, 2, GOLD);
            text("03", 20, 292, 4, EDGE);
            text("DELIVER", 78, 288, 2, BLUE);
        } else {
            int rows = (moves.size() + 1) / 2;
            int firstRow = journalFirstRow(game, rows);
            for (int row = firstRow; row < Math.min(rows, firstRow + 10); row++) {
                int index = row * 2;
                float y = 496 - (row - firstRow) * 22;
                String number = Integer.toString(row + 1);
                text(number, 40 - font.width(number, 2), y, 2, MUTED);
                journalMove(moves.get(index).notation(), 44, y);
                if (index + 1 < moves.size()) journalMove(moves.get(index + 1).notation(), 116, y);
            }
        }
    }

    private int journalFirstRow(GameSession game, int rows) {
        int focus = review.isActive() ? Math.max(0, (review.getPly() - 1) / 2) : rows - 1;
        return Math.max(0, Math.min(rows - 10, focus - 4));
    }

    private CompletedMove lastMove(GameSession game) {
        var moves = review.isActive() ? review.getRecord().moves() : game.getHistory();
        int ply = review.isActive() ? review.getPly() : moves.size();
        return ply == 0 ? null : moves.get(ply - 1);
    }

    private void drawCaptured(GameSession game, Side by, float labelY, float pieceY) {
        text(by == Side.WHITE ? "WHITE TOOK" : "BLACK TOOK", 16, labelY, 2, accent(by));
        var moves = review.isActive() ? review.getRecord().moves() : game.getHistory();
        int ply = review.isActive() ? review.getPly() : moves.size();
        PieceType[] types = {PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN};
        boolean any = false;
        for (int i = 0; i < types.length; i++) {
            int count = GameRecord.capturedCount(moves, ply, by, types[i]);
            if (count == 0) continue;
            any = true;
            float x = 32 + i * 32;
            drawPiece(Piece.make(by.flip(), types[i]), x, pieceY, 2);
            if (count > 1) text(Integer.toString(count), x + 4, pieceY - 8, 2, accent(by));
        }
        if (!any) text("NONE", 20, pieceY + 8, 2, MUTED);
    }

    private void drawReviewControls() {
        centered("REVIEW", 1100, 534, 2, GOLD);
        centered(review.getPly() + " / " + review.getRecord().moves().size(), 1100, 500, 2, CREAM);
        centered(review.getPly() == 0 ? "START POSITION" : review.getRecord().moves().get(review.getPly() - 1).notation(), 1100, 464, 2, MINT);
        text("< PREVIOUS", 1020, 420, 2, CREAM);
        text("> NEXT", 1020, 382, 2, CREAM);
        text("HOME FIRST", 1020, 344, 2, CREAM);
        text("END LAST", 1020, 306, 2, CREAM);
        text("V RESULT", 1020, 268, 2, GOLD);
        text("P EXPORT PGN", 1020, 230, 2, MINT);
        text("R AGAIN", 1020, 194, 2, MINT);
        text("X SWAP", 1108, 194, 2, MINT);
    }

    private void journalMove(String notation, float x, float y) {
        font.drawCompact(batch, notation, snap(x), snap(y), 2, CREAM);
    }

    private void control(String key, String action, float y) {
        centered(key, 1054, y + 20, 2, CREAM);
        text(action, 1098, y + 20, 2, MUTED);
    }

    private void drawClockText(GameSession game, Side side, float y) {
        boolean active = !game.isGameOver() && game.getChess().getSideToMove() == side;
        long seconds = game.getClock().getRemainingSeconds(side);
        drawBirdSprite(side, 1038, y + 106, 2, false, active ? frame() : 1);
        text(game.getSeries().shortNameFor(side) + " " + GameSession.sideName(side).toUpperCase(Locale.ROOT), 1062, y + 116, 2, accent(side));
        if (game.isTimed()) text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), 1034, y + 84, 4, seconds <= 30 ? DANGER : CREAM);
        else text("UNTIMED", 1032, y + 82, 2, CREAM);
        text(game.isGameOver() ? "FINISHED" : active ? "> YOUR MOVE" : "  WAITING", 1032, y + 40, 2, active ? MINT : MUTED);
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
            if (!complete) {
                stepped(384, 348, 208, 48, CARD, EDGE);
                stepped(608, 348, 208, 48, CARD, EDGE);
            }
        } else {
            for (int i = 0; i < 4; i++) stepped(384 + i * 112, 278, 96, 118, CARD, EDGE);
        }
        shapes.end();
        batch.begin();
        centered(complete ? "FLIGHT SCHOOL COMPLETE" : ended ? "ROUND " + game.getSeries().round() + " COMPLETE" : "LEVEL UP", 600, 528, 2, GOLD);
        centered(complete ? "READY TO PLAY!" : ended ? game.getResult().toUpperCase(Locale.ROOT) : "PROMOTION", 600, 486, 4, CREAM);
        centered(complete ? "Both birds mastered. R to practice again." : ended ? game.getReason() : "Choose a piece. The clock is running.", 600, 434, 2, MUTED);
        if (ended) {
            if (complete) {
                drawBirdSprite(Side.WHITE, 566, 376, 4, false, 1);
                drawBirdSprite(Side.BLACK, 634, 376, 4, true, 1);
            } else {
                centered("REVIEW [V]", 488, 378, 2, CREAM);
                centered("PGN [P]", 712, 378, 2, CREAM);
            }
            if (complete) centered("PLAY LOCAL [ENTER]", 600, 298, 2, INK);
            else {
                centered("AGAIN [R]", 488, 298, 2, INK);
                centered("SWAP [X]", 712, 298, 2, INK);
            }
        } else {
            PieceType[] options = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
            String[] labels = {"[Q]", "[R]", "[B]", "[N]"};
            for (int i = 0; i < options.length; i++) {
                drawPiece(Piece.make(game.getChess().getSideToMove(), options[i]), 432 + i * 112, 352, 4);
                centered(labels[i], 432 + i * 112, 306, 2, GOLD);
            }
        }
        centered("ESC  EXIT GAME", 600, 248, 2, MUTED);
        batch.end();
    }

    private void lessonBanner() {
        beginShapes();
        stepped(216, 712, 768, 72, PANEL, EDGE);
        for (int i = 0; i < 6; i++) rect(236 + i * 18, 722, 12, 4,
                i <= school.getStage().ordinal() ? MINT : EDGE);
        shapes.end();
        batch.begin();
        text(school.title(), 236, 774, 2, GOLD);
        text(school.instruction(), 236, 750, 2, CREAM);
        batch.end();
    }

    private void setupOverlay(MatchSetup setup) {
        beginShapes();
        rect(0, 0, 1200, 800, VEIL);
        stepped(292, 206, 616, 388, PANEL, EDGE);
        stepped(380, 430, 440, 50, CARD, EDGE);
        stepped(380, 354, 440, 50, CARD, EDGE);
        stepped(450, 260, 300, 60, MINT, INK);
        shapes.end();
        batch.begin();
        centered("NEW MATCH", 600, 558, 4, CREAM);
        centered("Choose time per side and bonus for each move.", 600, 510, 2, MUTED);
        centered(setup.timeLabel(), 600, 464, 2, GOLD);
        centered("LEFT / RIGHT OR CLICK", 600, 424, 2, MUTED);
        centered(setup.incrementLabel(), 600, 388, 2, BLUE);
        centered("UP / DOWN OR CLICK", 600, 348, 2, MUTED);
        centered("START [ENTER]", 600, 298, 2, INK);
        centered("S CLOSE SETUP    ESC EXIT", 600, 240, 2, MUTED);
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
