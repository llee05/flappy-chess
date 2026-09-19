package com.example.flappychess.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.model.Birb;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;

/** libGDX drawing; model coordinates are converted from top-left to bottom-left here. */
public final class GameRenderer implements Disposable {
    private static final Color LIGHT = Color.valueOf("e5e7d6");
    private static final Color DARK = Color.valueOf("7f9b8b");
    private static final Color PANEL = Color.valueOf("162c28");
    private static final Color CARD = Color.valueOf("203a33");
    private static final Color EDGE = Color.valueOf("345047");
    private static final Color INK = Color.valueOf("243d35");
    private static final Color CREAM = Color.valueOf("f4efdf");
    private static final Color MINT = Color.valueOf("b9ddba");
    private static final Color GOLD = Color.valueOf("e9bc73");
    private static final Color BLUE = Color.valueOf("a8ccdf");
    private static final Color MUTED = Color.valueOf("a0b5a8");
    private static final Color DANGER = Color.valueOf("f09983");
    private static final Color SHADOW = new Color(0.06f, 0.12f, 0.10f, 0.18f);
    private static final Color SELECTION = new Color(0.96f, 0.78f, 0.39f, 0.36f);
    private static final Color TARGET = new Color(0.10f, 0.30f, 0.23f, 0.45f);
    private static final Color VEIL = new Color(0.035f, 0.075f, 0.06f, 0.78f);
    private final FitViewport viewport = new FitViewport(1200, 800);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = makeFont("LiberationSans-Regular.ttf", 18, null, null);
    private final BitmapFont heading = makeFont("LiberationSans-Bold.ttf", 26, null, null);
    private final BitmapFont clockFont = makeFont("LiberationSans-Regular.ttf", 44, null, null);
    private final BitmapFont whitePieces = makeFont("NotoSansSymbols2-Regular.ttf", 66, CREAM, INK);
    private final BitmapFont blackPieces = makeFont("NotoSansSymbols2-Regular.ttf", 66, INK, CREAM);
    private final GlyphLayout layout = new GlyphLayout();
    private final Texture wings = new Texture(Gdx.files.internal("images/wings.png"));
    private float animationTime;

    public GameRenderer() {
        wings.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private static BitmapFont makeFont(String file, int size, Color fill, Color border) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/" + file));
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameters.size = size;
            parameters.minFilter = Texture.TextureFilter.Linear;
            parameters.magFilter = Texture.TextureFilter.Linear;
            if (fill != null) {
                // Use filled silhouettes for both sides, with contrasting edges on every tile.
                parameters.characters = " \u265a\u265b\u265c\u265d\u265e\u265f";
                parameters.color = fill;
                parameters.borderColor = border;
                parameters.borderWidth = 1.3f;
                parameters.shadowOffsetY = 2;
                parameters.shadowColor = SHADOW;
            }
            return generator.generateFont(parameters);
        } finally {
            generator.dispose();
        }
    }

    public void resize(int width, int height) { viewport.update(width, height, true); }
    public Vector2 unproject(int x, int y) { return viewport.unproject(new Vector2(x, y)); }

    public void render(GameSession game) {
        animationTime = (animationTime + Math.min(Gdx.graphics.getDeltaTime(), 0.1f)) % 1000;
        ScreenUtils.clear(0.045f, 0.085f, 0.07f, 1);
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        beginShapes();
        drawBoard(game);
        drawPanels(game);
        shapes.end();

        batch.begin();
        drawCoordinates();
        ChessBoardView board = game.getBoardView();
        for (Square square : Square.values()) {
            if (square == Square.NONE || square == board.getHiddenPieceSquare()) continue;
            Piece piece = board.getBoard().getPiece(square);
            if (piece != Piece.NONE) drawPiece(piece, squareX(square) + 50, squareY(square) + 51, 1);
        }
        drawWings(game.getWhite(), Side.WHITE, game);
        drawWings(game.getBlack(), Side.BLACK, game);
        drawPanelText(game);
        batch.end();

        beginShapes();
        drawBirdBody(game.getWhite(), Side.WHITE, game);
        drawBirdBody(game.getBlack(), Side.BLACK, game);
        shapes.end();
        batch.begin();
        drawCarriedPiece(game.getWhite(), Side.WHITE, game);
        drawCarriedPiece(game.getBlack(), Side.BLACK, game);
        batch.end();
        if (game.isGameOver() || game.getChess().hasPendingPromotion()) overlay(game);
    }

    private void drawBoard(GameSession game) {
        ChessBoardView board = game.getBoardView();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Square square = ChessBoardView.squareForCell(row, col);
                float x = squareX(square), y = squareY(square);
                rect(x, y, 100, 100, (row + col) % 2 == 0 ? LIGHT : DARK);
                if (square == board.getSelectedSquare()) {
                    rect(x, y, 100, 100, SELECTION);
                    outline(x + 3, y + 3, 94, 94, 3, GOLD);
                }
                Piece piece = board.getBoard().getPiece(square);
                if (piece != Piece.NONE && square != board.getHiddenPieceSquare()) {
                    shapes.setColor(SHADOW);
                    shapes.ellipse(x + 25, y + 16, 50, 12, 32);
                }
                if (board.getLegalTargets().contains(square)) {
                    if (piece == Piece.NONE) {
                        circle(x + 50, y + 50, 12, TARGET);
                        circle(x + 50, y + 50, 4, MINT);
                    } else {
                        outline(x + 5, y + 5, 90, 90, 4, TARGET);
                    }
                }
            }
        }
        if (!game.isGameOver() && !game.getChess().hasPendingPromotion()) {
            Birb active = game.getChess().getSideToMove() == Side.WHITE ? game.getWhite() : game.getBlack();
            Square square = ChessBoardView.squareForPoint(active.getX() + 25, active.getY() + 25);
            if (square != Square.NONE) {
                float x = squareX(square), y = squareY(square);
                // Corner brackets mark the exact square used by pickup/drop.
                Color color = game.getChess().getSideToMove() == Side.WHITE ? GOLD : BLUE;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        rect(x + 6 + i * 73, y + 6 + j * 85, 15, 3, color);
                        rect(x + 6 + i * 85, y + 6 + j * 73, 3, 15, color);
                    }
                }
            }
        }
    }

    private void drawPanels(GameSession game) {
        rect(0, 0, 200, 800, PANEL);
        rect(1000, 0, 200, 800, PANEL);
        rect(196, 0, 4, 800, EDGE);
        rect(1000, 0, 4, 800, EDGE);
        rect(20, 771, 28, 3, GOLD);
        rounded(20, 640, 156, 26, 7, CARD);
        rect(20, 613, 156, 1, EDGE);
        rect(20, 551, 156, 1, EDGE);
        int rows = (game.getHistory().size() + 1) / 2;
        int firstRow = Math.max(0, rows - 16);
        for (int row = firstRow; row < rows; row++) {
            float y = 518 - (row - firstRow) * 23;
            if ((row - firstRow) % 2 == 0) rounded(15, y - 5, 166, 23, 4, CARD);
            if (row == rows - 1) rect(15, y - 2, 2, 17, GOLD);
        }
        rect(20, 130, 156, 1, EDGE);
        drawClockCard(game, Side.BLACK, 650);
        drawClockCard(game, Side.WHITE, 28);
        rounded(1016, 550, 168, 76, 10, CARD);
        circle(1032, 603, 4, game.isGameOver() ? MUTED : MINT);
        rect(1020, 336, 160, 1, EDGE);
        drawKey(1020, 464, 61);
        drawKey(1020, 431, 61);
        drawKey(1020, 398, 61);
        drawKey(1020, 261, 77);
        drawKey(1020, 228, 77);
        drawKey(1020, 195, 77);
    }

    private void drawClockCard(GameSession game, Side side, float y) {
        boolean active = !game.isGameOver() && game.getChess().getSideToMove() == side;
        long seconds = game.getClock().getRemainingSeconds(side);
        Color accent = seconds <= 30 ? DANGER : side == Side.WHITE ? GOLD : BLUE;
        rounded(1016, y, 168, 130, 10, active ? EDGE : CARD);
        if (active) rect(1016, y + 20, 3, 90, accent);
        circle(1035, y + 105, 7, side == Side.WHITE ? CREAM : INK);
        if (side == Side.BLACK) circle(1035, y + 105, 3, BLUE);
        rect(1032, y + 14, 136, 3, PANEL);
        rect(1032, y + 14, 136 * Math.min(1, seconds / 300f), 3, accent);
    }

    private void drawKey(float x, float y, float width) {
        rounded(x, y - 2, width, 26, 5, PANEL);
        rounded(x, y, width, 26, 5, EDGE);
    }

    private void drawCoordinates() {
        for (int i = 0; i < 8; i++) {
            text(font, Integer.toString(8 - i), 207, 791 - i * 100, 0.67f, i % 2 == 0 ? INK : CREAM);
            text(font, Character.toString((char) ('a' + i)), 287 + i * 100, 17, 0.67f, i % 2 == 0 ? CREAM : INK);
        }
    }

    private void drawPanelText(GameSession game) {
        text(heading, "FLAPPY", 20, 751, 1.03f, CREAM);
        text(heading, "CHESS", 20, 720, 1.03f, CREAM);
        text(font, "A little flight. A little strategy.", 20, 683, 0.65f, MUTED);
        centered(font, "LOCAL DUEL   /   5 MIN", 98, 658, 0.65f, MINT);
        text(heading, "Move journal", 20, 595, 0.67f, CREAM);
        text(font, "#", 23, 570, 0.65f, MUTED);
        text(font, "WHITE", 53, 570, 0.65f, MUTED);
        text(font, "BLACK", 119, 570, 0.65f, MUTED);
        var moves = game.getHistory();
        if (moves.isEmpty()) {
            text(font, "The board is yours.", 20, 525, 0.82f, CREAM);
            text(font, "Take flight and make\nthe first move.", 20, 498, 0.78f, MUTED);
        } else {
            int rows = (moves.size() + 1) / 2;
            int firstRow = Math.max(0, rows - 16);
            for (int row = firstRow; row < rows; row++) {
                int index = row * 2;
                float y = 534 - (row - firstRow) * 23;
                text(font, Integer.toString(row + 1), 23, y, 0.7f, MUTED);
                text(font, moves.get(index).notation(), 53, y, 0.8f, CREAM);
                if (index + 1 < moves.size()) text(font, moves.get(index + 1).notation(), 119, y, 0.8f, CREAM);
            }
        }
        text(font, "FLY. PICK UP. DELIVER.", 20, 110, 0.66f, GOLD);
        text(font, "Carry your piece to a\nmarked destination.", 20, 86, 0.78f, MUTED);
        text(font, "ESC", 20, 28, 0.65f, CREAM);
        text(font, "Leave game", 54, 28, 0.7f, MUTED);
        drawClockText(game, Side.BLACK, 650);
        drawClockText(game, Side.WHITE, 28);
        text(font, game.isGameOver() ? "GAME COMPLETE" : "ON THE WING", 1044, 610, 0.64f, MINT);
        String status = game.isGameOver() ? "Well played." : GameSession.sideName(game.getChess().getSideToMove()) + " to move";
        if (!game.isGameOver() && game.getBoardView().getBoard().isKingAttacked()) status = "King in check";
        text(heading, status, 1030, 587, 0.70f, CREAM);
        text(font, "WHITE", 1020, 529, 0.73f, GOLD);
        text(font, "Gravity pulls down", 1020, 509, 0.7f, MUTED);
        control("A / D", "Move", 464, 61);
        control("SPACE", "Flap", 431, 61);
        control("E", "Pick / drop", 398, 61);
        text(font, "BLACK", 1020, 326, 0.73f, BLUE);
        text(font, "Gravity pulls up", 1020, 306, 0.7f, MUTED);
        control("LEFT / RIGHT", "Move", 261, 77);
        control("UP", "Flap", 228, 77);
        control("ENTER", "Pick / drop", 195, 77);
        text(font, "One press. One flap.", 1020, 182, 0.7f, MUTED);
    }

    private void control(String key, String action, float y, float width) {
        centered(font, key, 1020 + width / 2, y + 18, key.length() > 8 ? 0.55f : 0.65f, CREAM);
        text(font, action, 1020 + width + 9, y + 19, 0.72f, MUTED);
    }

    private void drawClockText(GameSession game, Side side, float y) {
        boolean active = !game.isGameOver() && game.getChess().getSideToMove() == side;
        long seconds = game.getClock().getRemainingSeconds(side);
        text(font, GameSession.sideName(side).toUpperCase(), 1050, y + 111, 0.73f, CREAM);
        text(clockFont, time(seconds), 1031, y + 86, 1, seconds <= 30 ? DANGER : CREAM);
        text(font, game.isGameOver() ? "FINISHED" : active ? "YOUR MOVE" : "WAITING", 1032, y + 34, 0.61f, active ? MINT : MUTED);
    }

    private void drawWings(Birb bird, Side side, GameSession game) {
        float x = 200 + (float) bird.getX(), y = 750 - (float) bird.getY();
        float flutter = game.isGameOver() ? 0 : (float) Math.sin(animationTime * 9) * 4;
        batch.setColor(side == Side.WHITE ? CREAM : BLUE);
        batch.draw(wings, x - 7, y + 2 - flutter / 2, 64, 46 + flutter);
        batch.setColor(Color.WHITE);
    }

    private void drawBirdBody(Birb bird, Side side, GameSession game) {
        float x = 225 + (float) bird.getX(), y = 775 - (float) bird.getY();
        boolean active = !game.isGameOver() && side == game.getChess().getSideToMove();
        Color accent = side == Side.WHITE ? GOLD : BLUE;
        circle(x, y - 3, 17, SHADOW);
        circle(x, y, active ? 17 : 14, active ? accent : EDGE);
        circle(x, y, 12, side == Side.WHITE ? CREAM : INK);
        if (side != game.getCarrier()) {
            circle(x + 4, y + 4, 4, CREAM);
            circle(x + 5, y + 4, 2, INK);
            shapes.setColor(GOLD);
            shapes.triangle(x + 10, y + 1, x + 20, y - 2, x + 10, y - 5);
        }
    }

    private void drawCarriedPiece(Birb bird, Side side, GameSession game) {
        if (side == game.getCarrier()) {
            Square carried = game.getBoardView().getHiddenPieceSquare();
            if (carried != Square.NONE) drawPiece(game.getBoardView().getBoard().getPiece(carried),
                    225 + (float) bird.getX(), 775 - (float) bird.getY(), 0.53f);
        }
    }

    private void drawPiece(Piece piece, float centerX, float centerY, float scale) {
        BitmapFont pieceFont = piece.getPieceSide() == Side.WHITE ? whitePieces : blackPieces;
        String symbol = Piece.make(Side.BLACK, piece.getPieceType()).getFanSymbol();
        pieceFont.getData().setScale(scale);
        layout.setText(pieceFont, symbol);
        pieceFont.draw(batch, layout, centerX - layout.width / 2, centerY + layout.height / 2);
    }

    private void overlay(GameSession game) {
        Vector2 pointer = unproject(Gdx.input.getX(), Gdx.input.getY());
        boolean hover = pointer.x >= 450 && pointer.x <= 750 && pointer.y >= 260 && pointer.y <= 320;
        beginShapes();
        rect(0, 0, 1200, 800, VEIL);
        rounded(334, 209, 532, 366, 18, SHADOW);
        rounded(340, 220, 520, 360, 16, EDGE);
        rounded(342, 222, 516, 356, 15, PANEL);
        rect(565, 545, 70, 3, GOLD);
        if (game.isGameOver()) {
            rounded(450, 260, 300, 60, 9, hover ? CREAM : MINT);
        } else {
            for (int i = 0; i < 4; i++) rounded(384 + i * 112, 279, 96, 118, 8, CARD);
        }
        shapes.end();
        batch.begin();
        centered(font, game.isGameOver() ? "END OF THE FLIGHT" : "A NEW PERSPECTIVE", 600, 524, 0.72f, GOLD);
        centered(heading, game.isGameOver() ? game.getResult() : "Promote your pawn", 600, 484, 1.23f, CREAM);
        centered(font, game.isGameOver() ? game.getReason() : "Choose a piece. Your clock is still running.", 600, 435, 0.95f, MUTED);
        if (game.isGameOver()) {
            centered(font, "Ready for another round?", 600, 371, 1, CREAM);
            centered(heading, "Play again   [R]", 600, 301, 0.85f, PANEL);
        } else {
            Piece[] options = {Piece.BLACK_QUEEN, Piece.BLACK_ROOK, Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT};
            String[] labels = {"Q  Queen", "R  Rook", "B  Bishop", "N  Knight"};
            for (int i = 0; i < options.length; i++) {
                drawPiece(Piece.make(game.getChess().getSideToMove(), options[i].getPieceType()), 432 + i * 112, 357, 0.7f);
                centered(font, labels[i], 432 + i * 112, 307, 0.83f, CREAM);
            }
        }
        centered(font, "ESC  Leave game", 600, 245, 0.68f, MUTED);
        batch.end();
    }

    private static float squareX(Square square) { return 200 + square.getFile().ordinal() * 100; }
    private static float squareY(Square square) { return square.getRank().ordinal() * 100; }
    private void beginShapes() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }
    private void rect(float x, float y, float w, float h, Color color) { shapes.setColor(color); shapes.rect(x, y, w, h); }
    private void circle(float x, float y, float radius, Color color) { shapes.setColor(color); shapes.circle(x, y, radius, 32); }
    private void outline(float x, float y, float w, float h, float thickness, Color color) {
        rect(x, y, w, thickness, color);
        rect(x, y + h - thickness, w, thickness, color);
        rect(x, y, thickness, h, color);
        rect(x + w - thickness, y, thickness, h, color);
    }
    private void rounded(float x, float y, float w, float h, float radius, Color color) {
        rect(x + radius, y, w - radius * 2, h, color);
        rect(x, y + radius, radius, h - radius * 2, color);
        rect(x + w - radius, y + radius, radius, h - radius * 2, color);
        circle(x + radius, y + radius, radius, color);
        circle(x + w - radius, y + radius, radius, color);
        circle(x + radius, y + h - radius, radius, color);
        circle(x + w - radius, y + h - radius, radius, color);
    }
    private void text(BitmapFont face, String value, float x, float y, float scale, Color color) {
        face.getData().setScale(scale);
        face.setColor(color);
        face.draw(batch, value, x, y);
    }
    private void centered(BitmapFont face, String value, float x, float y, float scale, Color color) {
        face.getData().setScale(scale);
        layout.setText(face, value);
        text(face, value, x - layout.width / 2, y, scale, color);
    }
    private String time(long seconds) { return String.format("%02d:%02d", seconds / 60, seconds % 60); }

    @Override public void dispose() {
        wings.dispose();
        whitePieces.dispose();
        blackPieces.dispose();
        font.dispose();
        heading.dispose();
        clockFont.dispose();
        batch.dispose();
        shapes.dispose();
    }
}
