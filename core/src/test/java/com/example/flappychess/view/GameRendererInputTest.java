package com.example.flappychess.view;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameRendererInputTest {
    @Test void promotionCardsMapToTheSameKeysAsKeyboardPromotion() {
        assertEquals(Keys.Q, GameRenderer.promotionKeyAt(new Vector2(632, 337)));
        assertEquals(Keys.R, GameRenderer.promotionKeyAt(new Vector2(744, 337)));
        assertEquals(Keys.B, GameRenderer.promotionKeyAt(new Vector2(856, 337)));
        assertEquals(Keys.N, GameRenderer.promotionKeyAt(new Vector2(968, 337)));
        assertEquals(Keys.UNKNOWN, GameRenderer.promotionKeyAt(new Vector2(800, 430)));
        assertEquals(Keys.UNKNOWN, GameRenderer.promotionKeyAt(new Vector2(800, 270)));
    }
}
