package com.example.flappychess.view;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;

/** Hand-authored silhouettes, shaded into a tiny nearest-filtered sprite atlas. */
final class PixelSprites implements Disposable {
    private static final String[] PIECES = {
        // King: cross, crown, narrow stem, stepped plinth.
        "......xx....../.....xxxx...../......xx....../....xxxxxx..../...xxxxxxxx.../....xxxxxx..../.....xxxx...../.....xxxx...../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.",
        // Queen: three crown points and a flared skirt.
        "..xx..xx..xx../..xx..xx..xx../..xxxxxxxxxx../...xxxxxxxx.../....xxxxxx..../....xxxxxx..../.....xxxx...../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx./.xxxxxxxxxxxx.",
        // Rook: crenellations, tower, and a broad base.
        "............../............../.xxx.xxxx.xxx./.xxx.xxxx.xxx./.xxxxxxxxxxxx./..xxxxxxxxxx../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.",
        // Bishop: split mitre above a slender body.
        "......xx....../.....xxxx...../....xxxx....../...xxxx..xx.../...xxx..xxx.../....xxxxxx..../.....xxxx...../....xxxxxx..../.....xxxx...../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.",
        // Knight: ears, long muzzle, mane, and curved neck.
        ".....xx..xx.../.....xxxxxx.../....xxxxxxxx../...xxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxx../.xxxx..xxxxx../..xx...xxxxx../......xxxxxx../.....xxxxxx.../....xxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.",
        // Pawn: round head, collar, and compact pedestal.
        "............../............../............../............../.....xxxx...../....xxxxxx..../....xxxxxx..../....xxxxxx..../.....xxxx...../....xxxxxx..../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx."
    };
    private static final Color OUTLINE = Color.valueOf("202538");
    private static final Color[] WHITE = colors("b99c79", "f1dfb3", "fff3d1", "e6aa5d");
    private static final Color[] BLACK = colors("414568", "737fa4", "b0c4db", "7ed0d6");
    private static final Color[] GOLD_BIRD = colors("b77345", "efb95b", "ffe5a1", "d99045");
    private static final Color[] BLUE_BIRD = colors("416788", "78c4d0", "c6f0e7", "579eae");
    private final Texture atlas;
    private final TextureRegion[][] pieces = new TextureRegion[2][6];
    private final TextureRegion[][] birds = new TextureRegion[2][3];

    PixelSprites() {
        Pixmap pixels = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        for (int side = 0; side < 2; side++) {
            Color[] palette = side == 0 ? WHITE : BLACK;
            for (int piece = 0; piece < PIECES.length; piece++) {
                int x = piece * 18, y = side * 22;
                mask(pixels, PIECES[piece], x + 1, y + 1, palette);
                pixels.setColor(palette[3]);
                pixels.fillRectangle(x + 4, y + 16, 8, 1);
                if (piece == 0 || piece == 1) pixels.drawPixel(x + 7, y + 5);
                if (piece == 4) {
                    pixels.setColor(OUTLINE);
                    pixels.drawPixel(x + 5, y + 4);
                }
            }
            for (int frame = 0; frame < 3; frame++) drawBird(pixels, frame * 18, 44 + side * 18, side, frame);
        }
        atlas = new Texture(pixels);
        pixels.dispose();
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        for (int side = 0; side < 2; side++) {
            for (int piece = 0; piece < 6; piece++) {
                pieces[side][piece] = new TextureRegion(atlas, piece * 18, side * 22, 16, 20);
            }
            for (int frame = 0; frame < 3; frame++) {
                birds[side][frame] = new TextureRegion(atlas, frame * 18, 44 + side * 18, 16, 16);
            }
        }
    }

    private static Color[] colors(String... hex) {
        Color[] result = new Color[hex.length];
        for (int i = 0; i < hex.length; i++) result[i] = Color.valueOf(hex[i]);
        return result;
    }

    private static void mask(Pixmap pixels, String pattern, int x, int y, Color[] palette) {
        String[] rows = pattern.split("/");
        pixels.setColor(OUTLINE);
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (filled(rows, col, row)) pixels.fillRectangle(x + col - 1, y + row - 1, 3, 3);
            }
        }
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (!filled(rows, col, row)) continue;
                Color shade = !filled(rows, col + 1, row) || !filled(rows, col, row + 1)
                        ? palette[0] : !filled(rows, col - 1, row) || !filled(rows, col, row - 1)
                        ? palette[2] : palette[1];
                pixels.setColor(shade);
                pixels.drawPixel(x + col, y + row);
            }
        }
    }

    private static boolean filled(String[] rows, int x, int y) {
        return y >= 0 && y < rows.length && x >= 0 && x < rows[y].length() && rows[y].charAt(x) == 'x';
    }

    private static void drawBird(Pixmap pixels, int x, int y, int side, int frame) {
        Color[] palette = side == 0 ? GOLD_BIRD : BLUE_BIRD;
        mask(pixels, "...xxx.../.xxxxxxx./xxxxxxxxx/xxxxxxxxx/xxxxxxxxx/.xxxxxxx./..xxxxx..", x + 4, y + 5, palette);
        mask(pixels, "xx./xxx/.xx", x + 1, y + 9, palette);
        String wing = switch (frame) {
            case 0 -> "xx.../xxx../xxxx./.xxxx/..xxx";
            case 1 -> "xxxxx/xxxxx/.xxx.";
            default -> "..xxx/.xxxx/xxxx./xxx../xx...";
        };
        mask(pixels, wing, x + 3, y + (frame == 0 ? 2 : frame == 1 ? 7 : 8), palette);
        pixels.setColor(palette[2]);
        pixels.fillRectangle(x + 10, y + 6, 3, 3);
        pixels.setColor(OUTLINE);
        pixels.fillRectangle(x + 12, y + 7, 1, 2);
        pixels.setColor(Color.valueOf("e99658"));
        pixels.fillRectangle(x + 13, y + 9, 3, 2);
        pixels.setColor(palette[0]);
        pixels.drawPixel(x + 8, y + 13);
        pixels.drawPixel(x + 11, y + 13);
    }

    TextureRegion piece(Side side, PieceType type) {
        int index = switch (type) {
            case KING -> 0;
            case QUEEN -> 1;
            case ROOK -> 2;
            case BISHOP -> 3;
            case KNIGHT -> 4;
            case PAWN -> 5;
            default -> throw new IllegalArgumentException("No sprite for " + type);
        };
        return pieces[side == Side.WHITE ? 0 : 1][index];
    }

    TextureRegion bird(Side side, int frame) { return birds[side == Side.WHITE ? 0 : 1][frame]; }
    @Override public void dispose() { atlas.dispose(); }
}
