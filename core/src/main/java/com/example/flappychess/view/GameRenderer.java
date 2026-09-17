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
    private static final Color LIGHT = Color.valueOf("f0d9b5");
    private static final Color DARK = Color.valueOf("b58863");
    private static final Color PANEL = Color.valueOf("101726");
    private static final Color GREEN = Color.valueOf("43dc92");
    private static final Color MUTED = Color.valueOf("9aabc5");
    private final FitViewport viewport = new FitViewport(1200, 800);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final BitmapFont pieces;
    private final GlyphLayout layout = new GlyphLayout();
    private final Texture wings = new Texture(Gdx.files.internal("images/wings.png"));

    public GameRenderer() {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSansSymbols2-Regular.ttf"));
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameters.size = 64;
            parameters.characters = " \u2654\u2655\u2656\u2657\u2658\u2659\u265a\u265b\u265c\u265d\u265e\u265f";
            pieces = generator.generateFont(parameters);
        } finally {
            generator.dispose();
        }
        wings.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) { viewport.update(width, height, true); }
    public Vector2 unproject(int x, int y) { return viewport.unproject(new Vector2(x, y)); }

    public void render(GameSession game) {
        ScreenUtils.clear(0.025f, 0.035f, 0.055f, 1);
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        ChessBoardView board = game.getBoardView();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rect(0, 0, 200, 800, PANEL);
        rect(1000, 0, 200, 800, PANEL);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Square square = ChessBoardView.squareForCell(row, col);
                float x = 200 + col * 100, y = 700 - row * 100;
                Color tile = (row + col) % 2 == 0 ? LIGHT : DARK;
                if (square == board.getSelectedSquare()) {
                    rect(x, y, 100, 100, Color.ROYAL);
                    rect(x + 5, y + 5, 90, 90, tile);
                } else if (board.getLegalTargets().contains(square)) {
                    rect(x, y, 100, 100, GREEN);
                    rect(x + 4, y + 4, 92, 92, tile);
                } else rect(x, y, 100, 100, tile);
            }
        }
        rect(1012, 682, 176, 70, game.getChess().getSideToMove() == Side.BLACK ? Color.valueOf("174737") : Color.valueOf("202c40"));
        rect(1012, 28, 176, 70, game.getChess().getSideToMove() == Side.WHITE ? Color.valueOf("174737") : Color.valueOf("202c40"));
        shapes.end();

        batch.begin();
        for (Square square : Square.values()) {
            if (square == Square.NONE || square == board.getHiddenPieceSquare()) continue;
            Piece piece = board.getBoard().getPiece(square);
            if (piece != Piece.NONE) drawPiece(piece, 250 + square.getFile().ordinal() * 100,
                    50 + square.getRank().ordinal() * 100, 1);
        }
        drawBird(game.getWhite(), Side.WHITE, game);
        drawBird(game.getBlack(), Side.BLACK, game);
        text("FLAPPY CHESS", 16, 776, 1.2f, GREEN);
        text("MOVE HISTORY", 16, 735, 1, MUTED);
        var moves = game.getHistory();
        int firstRow = Math.max(0, (moves.size() + 1) / 2 - 25);
        for (int row = firstRow; row * 2 < moves.size(); row++) {
            int index = row * 2;
            String line = (row + 1) + ". " + moves.get(index).notation();
            if (index + 1 < moves.size()) line += "  " + moves.get(index + 1).notation();
            text(line, 16, 699 - (row - firstRow) * 24, 1, Color.WHITE);
        }
        text("BLACK", 1016, 780, 1, MUTED);
        text(time(game.getClock().getRemainingSeconds(Side.BLACK)), 1028, 730, 2, Color.WHITE);
        text("WHITE", 1016, 125, 1, MUTED);
        text(time(game.getClock().getRemainingSeconds(Side.WHITE)), 1028, 76, 2, Color.WHITE);
        text(GameSession.sideName(game.getChess().getSideToMove()) + " to move", 1016, 629, 1.15f, GREEN);
        text("WHITE CONTROLS", 1016, 547, 1, MUTED);
        text("A / D  Move\nSpace  Flap\nE  Pick up / drop", 1016, 518, 1, Color.WHITE);
        text("BLACK CONTROLS", 1016, 409, 1, MUTED);
        text("Left / Right  Move\nUp  Flap\nEnter  Pick up / drop", 1016, 380, 0.95f, Color.WHITE);
        text("Carry a piece to\na green square.\n\nEsc  Quit", 1016, 263, 1, MUTED);
        batch.end();

        if (game.isGameOver() || game.getChess().hasPendingPromotion()) overlay(game);
    }

    private void drawBird(Birb bird, Side side, GameSession game) {
        float x = 200 + (float) bird.getX(), y = 800 - (float) bird.getY() - 50;
        batch.setColor(side == Side.WHITE ? Color.WHITE : Color.GRAY);
        batch.draw(wings, x, y, 50, 50);
        batch.setColor(Color.WHITE);
        if (side == game.getCarrier()) {
            Square carried = game.getBoardView().getHiddenPieceSquare();
            if (carried != Square.NONE) drawPiece(game.getBoardView().getBoard().getPiece(carried), x + 25, y + 25, 0.55f);
        }
    }

    private void drawPiece(Piece piece, float centerX, float centerY, float scale) {
        pieces.getData().setScale(scale);
        pieces.setColor(Color.valueOf("17202c"));
        layout.setText(pieces, piece.getFanSymbol());
        pieces.draw(batch, layout, centerX - layout.width / 2, centerY + layout.height / 2);
    }

    private void overlay(GameSession game) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rect(0, 0, 1200, 800, new Color(0, 0, 0, 0.65f));
        rect(360, 240, 480, 300, PANEL);
        if (game.isGameOver()) rect(450, 260, 300, 60, GREEN);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        batch.begin();
        if (game.isGameOver()) {
            centered(game.getResult(), 600, 490, 2, GREEN);
            centered(game.getReason(), 600, 429, 1.2f, Color.WHITE);
            centered("Play again (R)", 600, 299, 1.3f, PANEL);
        } else {
            centered("PROMOTE PAWN", 600, 490, 2, GREEN);
            centered(GameSession.sideName(game.getChess().getSideToMove()) + " to choose", 600, 430, 1.2f, MUTED);
            centered("Q  Queen     R  Rook", 600, 360, 1.3f, Color.WHITE);
            centered("B  Bishop    N  Knight", 600, 312, 1.3f, Color.WHITE);
        }
        batch.end();
    }

    private void rect(float x, float y, float w, float h, Color color) { shapes.setColor(color); shapes.rect(x, y, w, h); }
    private void text(String value, float x, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(batch, value, x, y);
    }
    private void centered(String value, float x, float y, float scale, Color color) {
        font.getData().setScale(scale);
        layout.setText(font, value);
        text(value, x - layout.width / 2, y, scale, color);
    }
    private String time(long seconds) { return String.format("%02d:%02d", seconds / 60, seconds % 60); }

    @Override public void dispose() {
        wings.dispose();
        pieces.dispose();
        font.dispose();
        batch.dispose();
        shapes.dispose();
    }
}
