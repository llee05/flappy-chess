package com.example.flappychess.view;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.Map;

/** Original 5x7 lettering. Integer scales keep both the HUD and SAN journal crisp. */
final class PixelFont implements Disposable {
    private final Texture texture;
    private final TextureRegion[] glyphs = new TextureRegion[128];

    PixelFont() {
        Map<Character, String> letters = new HashMap<>();
        String[] alphabet = {
            "01110/10001/10001/11111/10001/10001/10001",
            "11110/10001/10001/11110/10001/10001/11110",
            "01111/10000/10000/10000/10000/10000/01111",
            "11110/10001/10001/10001/10001/10001/11110",
            "11111/10000/10000/11110/10000/10000/11111",
            "11111/10000/10000/11110/10000/10000/10000",
            "01111/10000/10000/10111/10001/10001/01110",
            "10001/10001/10001/11111/10001/10001/10001",
            "11111/00100/00100/00100/00100/00100/11111",
            "00111/00010/00010/00010/10010/10010/01100",
            "10001/10010/10100/11000/10100/10010/10001",
            "10000/10000/10000/10000/10000/10000/11111",
            "10001/11011/10101/10101/10001/10001/10001",
            "10001/11001/10101/10011/10001/10001/10001",
            "01110/10001/10001/10001/10001/10001/01110",
            "11110/10001/10001/11110/10000/10000/10000",
            "01110/10001/10001/10001/10101/10010/01101",
            "11110/10001/10001/11110/10100/10010/10001",
            "01111/10000/10000/01110/00001/00001/11110",
            "11111/00100/00100/00100/00100/00100/00100",
            "10001/10001/10001/10001/10001/10001/01110",
            "10001/10001/10001/10001/10001/01010/00100",
            "10001/10001/10001/10101/10101/11011/10001",
            "10001/10001/01010/00100/01010/10001/10001",
            "10001/10001/01010/00100/00100/00100/00100",
            "11111/00001/00010/00100/01000/10000/11111"
        };
        String[] lowercase = {
            "00000/00000/01110/00001/01111/10001/01111",
            "10000/10000/11110/10001/10001/10001/11110",
            "00000/00000/01111/10000/10000/10000/01111",
            "00001/00001/01111/10001/10001/10001/01111",
            "00000/00000/01110/10001/11111/10000/01111",
            "00110/01001/01000/11100/01000/01000/01000",
            "00000/01111/10001/10001/01111/00001/01110",
            "10000/10000/11110/10001/10001/10001/10001",
            "00100/00000/01100/00100/00100/00100/01110",
            "00010/00000/00110/00010/00010/10010/01100",
            "10000/10000/10010/10100/11000/10100/10010",
            "01100/00100/00100/00100/00100/00100/01110",
            "00000/00000/11010/10101/10101/10101/10101",
            "00000/00000/11110/10001/10001/10001/10001",
            "00000/00000/01110/10001/10001/10001/01110",
            "00000/11110/10001/10001/11110/10000/10000",
            "00000/01111/10001/10001/01111/00001/00001",
            "00000/00000/10111/11000/10000/10000/10000",
            "00000/00000/01111/10000/01110/00001/11110",
            "01000/01000/11100/01000/01000/01001/00110",
            "00000/00000/10001/10001/10001/10011/01101",
            "00000/00000/10001/10001/10001/01010/00100",
            "00000/00000/10001/10001/10101/10101/01010",
            "00000/00000/10001/01010/00100/01010/10001",
            "00000/10001/10001/10001/01111/00001/01110",
            "00000/00000/11111/00010/00100/01000/11111"
        };
        String[] digits = {
            "01110/10001/10011/10101/11001/10001/01110",
            "00100/01100/00100/00100/00100/00100/01110",
            "01110/10001/00001/00010/00100/01000/11111",
            "11110/00001/00001/01110/00001/00001/11110",
            "00010/00110/01010/10010/11111/00010/00010",
            "11111/10000/10000/11110/00001/00001/11110",
            "01110/10000/10000/11110/10001/10001/01110",
            "11111/00001/00010/00100/01000/01000/01000",
            "01110/10001/10001/01110/10001/10001/01110",
            "01110/10001/10001/01111/00001/00001/01110"
        };
        for (int i = 0; i < 26; i++) {
            letters.put((char) ('A' + i), alphabet[i]);
            letters.put((char) ('a' + i), lowercase[i]);
        }
        for (int i = 0; i < 10; i++) letters.put((char) ('0' + i), digits[i]);
        letters.put(' ', "00000/00000/00000/00000/00000/00000/00000");
        letters.put('.', "00000/00000/00000/00000/00000/00110/00110");
        letters.put(',', "00000/00000/00000/00000/00110/00100/01000");
        letters.put(':', "00000/00110/00110/00000/00110/00110/00000");
        letters.put('/', "00001/00010/00010/00100/01000/01000/10000");
        letters.put('-', "00000/00000/00000/11111/00000/00000/00000");
        letters.put('+', "00000/00100/00100/11111/00100/00100/00000");
        letters.put('=', "00000/00000/11111/00000/11111/00000/00000");
        letters.put('#', "01010/01010/11111/01010/11111/01010/01010");
        letters.put('!', "00100/00100/00100/00100/00100/00000/00100");
        letters.put('?', "01110/10001/00001/00010/00100/00000/00100");
        letters.put('[', "01110/01000/01000/01000/01000/01000/01110");
        letters.put(']', "01110/00010/00010/00010/00010/00010/01110");
        letters.put('<', "00010/00100/01000/10000/01000/00100/00010");
        letters.put('>', "01000/00100/00010/00001/00010/00100/01000");
        letters.put('^', "00100/01010/10001/00000/00000/00000/00000");
        letters.put('(', "00010/00100/01000/01000/01000/00100/00010");
        letters.put(')', "01000/00100/00010/00010/00010/00100/01000");
        letters.put('\'', "00100/00100/00000/00000/00000/00000/00000");

        Pixmap pixels = new Pixmap(128, 64, Pixmap.Format.RGBA8888);
        pixels.setColor(Color.WHITE);
        for (var entry : letters.entrySet()) {
            int code = entry.getKey();
            int x = (code % 16) * 8, y = (code / 16) * 8;
            String[] rows = entry.getValue().split("/");
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (rows[row].charAt(col) == '1') pixels.drawPixel(x + col, y + row);
                }
            }
        }
        texture = new Texture(pixels);
        pixels.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        for (char code : letters.keySet()) {
            glyphs[code] = new TextureRegion(texture, code % 16 * 8, code / 16 * 8, 5, 7);
        }
    }

    void draw(SpriteBatch batch, String value, float x, float top, int scale, Color color) {
        draw(batch, value, x, top, scale, color, 6);
    }

    void drawCompact(SpriteBatch batch, String value, float x, float top, int scale, Color color) {
        draw(batch, value, x, top, scale, color, 5);
    }

    private void draw(SpriteBatch batch, String value, float x, float top, int scale, Color color, int advance) {
        batch.setColor(color);
        float start = Math.round(x);
        x = start;
        top = Math.round(top);
        for (int i = 0; i < value.length(); i++) {
            char letter = value.charAt(i);
            if (letter == '\n') {
                x = start;
                top -= 10 * scale;
                continue;
            }
            TextureRegion glyph = letter < glyphs.length ? glyphs[letter] : null;
            if (glyph == null) glyph = glyphs['?'];
            batch.draw(glyph, x, top - 7 * scale, 5 * scale, 7 * scale);
            x += advance * scale;
        }
        batch.setColor(Color.WHITE);
    }

    int width(String value, int scale) {
        int longest = 0;
        for (String line : value.split("\n")) longest = Math.max(longest, line.length());
        return Math.max(0, longest * 6 - 1) * scale;
    }

    @Override public void dispose() { texture.dispose(); }
}
