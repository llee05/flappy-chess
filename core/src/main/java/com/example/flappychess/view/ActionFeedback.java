package com.example.flappychess.view;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.example.flappychess.controller.GameFeedback;
import com.example.flappychess.controller.GameFeedback.Event;
import com.example.flappychess.controller.GameFeedback.Kind;
import com.example.flappychess.controller.GameSession;
import com.example.flappychess.model.Birb;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.ArrayList;
import java.util.List;

/** Short visual accents and audio; all positions and timers here are presentation-only. */
final class ActionFeedback implements Disposable {
    static final float CARRY_SHAKE_DURATION = 0.26f;
    static final float EMPTY_SHAKE_DURATION = 0.14f;
    static final float CARRY_SHAKE_MIN = 8f;
    static final float CARRY_SHAKE_MAX = 16f;
    static final float EMPTY_SHAKE_MIN = 3f;
    static final float EMPTY_SHAKE_MAX = 6f;
    static final float BOARD_FLASH_DURATION = 0.09f;
    static final float CALLOUT_DURATION = 0.78f;
    static final float DROP_LANDING_TIME = (GameSession.CARRY_HIT_STOP_STEPS + GameSession.DROP_RETURN_STEPS) / 60f;
    private static final float BURST_MAX_AGE = 0.68f;
    private static final Color GOLD = Color.valueOf("efb95b");
    private static final Color GOLD_BRIGHT = Color.valueOf("ffe5a1");
    private static final Color TEAL = Color.valueOf("78c4d0");
    private static final Color TEAL_BRIGHT = Color.valueOf("c6f0e7");
    private static final Color CREAM = Color.valueOf("e4cd9f");
    private static final Color MINT = Color.valueOf("add2b2");
    private static final Color DANGER = Color.valueOf("ed8c78");
    private static final Color PANEL = Color.valueOf("20283c");
    private static final Color INK = Color.valueOf("202538");
    private static final Color FLASH = new Color(1f, 0.94f, 0.78f, 0f);

    private static final class Burst {
        final Event event;
        final float x, y;
        final float impactScale;
        float age;
        Burst(Event event, float x, float y, float impactScale) {
            this.event = event;
            this.x = x;
            this.y = y;
            this.impactScale = impactScale;
        }
    }

    private final FeedbackAudio audio;
    private final List<Burst> bursts = new ArrayList<>();
    private GameSession observed;
    private int seen;
    private String message = "";
    private List<String> messageLines = List.of();
    private float messageTime;
    private Color messageColor = MINT;
    private boolean reducedMotion;
    private float shakeTime;
    private float shakeDuration;
    private float shakeAmplitude;
    private float flashTime;
    private float flashDuration;
    private String calloutText = "";
    private float calloutX;
    private float calloutY;
    private float calloutAge = CALLOUT_DURATION;

    ActionFeedback() { this(new FeedbackAudio()); }

    ActionFeedback(FeedbackAudio audio) { this.audio = audio; }

    void setReducedMotion(boolean reducedMotion) { this.reducedMotion = reducedMotion; }
    boolean isReducedMotion() { return reducedMotion; }

    static float impactFactor(double speed) {
        return (float) Math.max(0.0, Math.min(1.0, (speed - GameSession.MIN_BUMP_SPEED) / 6.0));
    }

    void update(GameSession game, float delta) {
        if (observed != game) {
            observed = game;
            seen = 0;
            bursts.clear();
            messageTime = 0;
            shakeTime = 0;
            shakeDuration = 0;
            shakeAmplitude = 0;
            flashTime = 0;
            flashDuration = 0;
            calloutText = "";
            calloutAge = CALLOUT_DURATION;
        }
        float step = Math.max(0, delta);
        for (Burst burst : bursts) burst.age += step;
        bursts.removeIf(burst -> burst.age >= (burst.event.kind() == Kind.BUMP_DROP ? BURST_MAX_AGE : 0.55f));
        messageTime = Math.max(0, messageTime - step);
        if (shakeDuration > 0) {
            shakeTime += step;
            if (shakeTime >= shakeDuration) {
                shakeTime = 0;
                shakeDuration = 0;
                shakeAmplitude = 0;
            }
        }
        if (flashDuration > 0) {
            flashTime += step;
            if (flashTime >= flashDuration) {
                flashTime = 0;
                flashDuration = 0;
            }
        }
        if (calloutAge < CALLOUT_DURATION) {
            calloutAge = Math.min(CALLOUT_DURATION, calloutAge + step);
        }
        for (Event event : game.getFeedback().events()) {
            if (event.sequence() <= seen) continue;
            seen = event.sequence();
            float x = GameRenderer.BOARD_X + (event.square().getFile().ordinal() * 100 + 50) * GameRenderer.BOARD_SCALE_X;
            float y = GameRenderer.BOARD_Y + (event.square().getRank().ordinal() * 100 + 50) * GameRenderer.BOARD_SCALE_Y;
            float impactScale = 0f;
            if (event.kind() == Kind.FLAP || event.kind() == Kind.PICKUP) {
                Birb bird = event.side() == Side.WHITE ? game.getWhite() : game.getBlack();
                x = GameRenderer.BOARD_X + ((float) bird.getX() + 25) * GameRenderer.BOARD_SCALE_X;
                y = GameRenderer.BOARD_Y + (775 - (float) bird.getY()) * GameRenderer.BOARD_SCALE_Y;
            } else if (event.kind() == Kind.BUMP || event.kind() == Kind.BUMP_DROP) {
                GameSession.BumpImpact impact = game.getLastBump();
                double speed = 3.6;
                if (impact != null && impact.sequence() == event.sequence()) {
                    x = GameRenderer.BOARD_X + (float) impact.x() * GameRenderer.BOARD_SCALE_X;
                    y = GameRenderer.BOARD_Y + (800 - (float) impact.y()) * GameRenderer.BOARD_SCALE_Y;
                    speed = impact.speed();
                } else {
                    float midX = (float) (game.getWhite().getX() + game.getBlack().getX()) / 2f + 25;
                    float midY = 775 - (float) (game.getWhite().getY() + game.getBlack().getY()) / 2f;
                    x = GameRenderer.BOARD_X + midX * GameRenderer.BOARD_SCALE_X;
                    y = GameRenderer.BOARD_Y + midY * GameRenderer.BOARD_SCALE_Y;
                }
                impactScale = impactFactor(speed);
                triggerBumpFeel(event.kind() == Kind.BUMP_DROP, impactScale, x, y);
            }
            if (bursts.size() == GameFeedback.LIMIT) bursts.remove(0);
            bursts.add(new Burst(event, x, y, impactScale));
            if (audio != null) audio.play(event.kind());
            String text = GameFeedback.message(event);
            if (!text.isEmpty()) {
                message = text;
                messageLines = wrap(text);
                messageTime = event.kind() == Kind.BUMP_DROP ? 2.1f : 1.8f;
                messageColor = warning(event.kind()) ? DANGER : MINT;
            }
        }
    }

    private void triggerBumpFeel(boolean dropped, float factor, float x, float y) {
        float amplitude = dropped
                ? CARRY_SHAKE_MIN + (CARRY_SHAKE_MAX - CARRY_SHAKE_MIN) * factor
                : EMPTY_SHAKE_MIN + (EMPTY_SHAKE_MAX - EMPTY_SHAKE_MIN) * factor;
        float duration = dropped ? CARRY_SHAKE_DURATION : EMPTY_SHAKE_DURATION;
        if (amplitude >= currentShakeAmplitude()) {
            shakeAmplitude = amplitude;
            shakeDuration = duration;
            shakeTime = 0;
        }
        if (dropped) {
            flashDuration = BOARD_FLASH_DURATION;
            flashTime = 0;
            calloutText = "DROPPED!";
            calloutX = Math.max(GameRenderer.BOARD_X + 76,
                    Math.min(GameRenderer.BOARD_X + GameRenderer.BOARD_WIDTH - 76, x));
            calloutY = Math.max(GameRenderer.BOARD_Y + 46,
                    Math.min(GameRenderer.BOARD_Y + GameRenderer.BOARD_HEIGHT - 64, y + 48));
            calloutAge = 0;
        }
    }

    float currentShakeAmplitude() {
        if (reducedMotion || shakeDuration <= 0 || shakeTime >= shakeDuration) return 0;
        float remaining = 1f - shakeTime / shakeDuration;
        return shakeAmplitude * remaining * remaining;
    }

    float shakeX() {
        float mag = currentShakeAmplitude();
        if (mag <= 0) return 0;
        return snap((float) Math.cos(shakeTime * 96f + 0.4f) * mag);
    }

    float shakeY() {
        float mag = currentShakeAmplitude();
        if (mag <= 0) return 0;
        return snap((float) Math.sin(shakeTime * 118f + 1.1f) * mag * 0.85f);
    }

    float boardFlashAlpha() {
        if (reducedMotion || flashDuration <= 0 || flashTime >= flashDuration) return 0;
        float remaining = 1f - flashTime / flashDuration;
        return 0.20f * remaining * remaining;
    }

    boolean hasCallout() {
        return !calloutText.isEmpty() && calloutAge < CALLOUT_DURATION;
    }

    String calloutText() { return hasCallout() ? calloutText : ""; }

    private float calloutCurrentY() {
        float t = Math.min(1f, calloutAge / CALLOUT_DURATION);
        float eased = 1f - (1f - t) * (1f - t);
        return Math.min(GameRenderer.BOARD_Y + GameRenderer.BOARD_HEIGHT - 24, calloutY + eased * 22f);
    }

    private static boolean warning(Kind kind) {
        return kind == Kind.ILLEGAL_DROP || kind == Kind.NO_PIECE || kind == Kind.CHECK
                || kind == Kind.LOW_TIME || kind == Kind.BUMP || kind == Kind.BUMP_DROP;
    }

    void drawBoardFlash(ShapeRenderer shapes) {
        float alpha = boardFlashAlpha();
        if (alpha <= 0.005f) return;
        FLASH.a = alpha;
        shapes.setColor(FLASH);
        shapes.rect(GameRenderer.BOARD_X, GameRenderer.BOARD_Y, GameRenderer.BOARD_WIDTH, GameRenderer.BOARD_HEIGHT);
    }

    void drawShapes(ShapeRenderer shapes) {
        for (Burst burst : bursts) {
            Kind kind = burst.event.kind();
            if (kind == Kind.BUMP_DROP) {
                drawCarryingBump(shapes, burst);
                continue;
            }
            if (kind == Kind.BUMP) {
                drawEmptyBump(shapes, burst);
                continue;
            }
            shapes.setColor(warning(kind) ? DANGER : kind == Kind.CAPTURE ? GOLD : MINT);
            if (kind == Kind.ILLEGAL_DROP || kind == Kind.NO_PIECE || kind == Kind.CHECK) {
                if ((int) (burst.age * 12) % 2 == 0) {
                    float x = GameRenderer.BOARD_X + burst.event.square().getFile().ordinal() * GameRenderer.SQUARE_WIDTH;
                    float y = GameRenderer.BOARD_Y + burst.event.square().getRank().ordinal() * GameRenderer.SQUARE_HEIGHT;
                    rect(shapes, x + 4, y + 4, GameRenderer.SQUARE_WIDTH - 8, 4);
                    rect(shapes, x + 4, y + GameRenderer.SQUARE_HEIGHT - 8, GameRenderer.SQUARE_WIDTH - 8, 4);
                    rect(shapes, x + 4, y + 4, 4, GameRenderer.SQUARE_HEIGHT - 8);
                    rect(shapes, x + GameRenderer.SQUARE_WIDTH - 8, y + 4, 4, GameRenderer.SQUARE_HEIGHT - 8);
                }
            } else if (kind != Kind.LOW_TIME) {
                int count = kind == Kind.CAPTURE ? 12 : kind == Kind.FLAP ? 4 : 8;
                float radius = 14 + burst.age * (kind == Kind.CAPTURE ? 112 : 64);
                float size = burst.age < 0.25f ? 6 : 4;
                for (int i = 0; i < count; i++) {
                    double angle = i * Math.PI * 2 / count;
                    float x = burst.x + (float) Math.cos(angle) * radius;
                    float y = burst.y + (float) Math.sin(angle) * radius - burst.age * burst.age * 40;
                    drawClipped(shapes, x, y, size, size);
                }
            }
        }
    }

    private void drawCarryingBump(ShapeRenderer shapes, Burst burst) {
        float age = burst.age;
        float scale = 1f + 0.32f * burst.impactScale;

        // 1. Expanding crisp pixel shockwave ring.
        if (age < 0.26f) {
            float t = age / 0.26f;
            float ringRadius = (16f + 74f * (1f - (1f - t) * (1f - t))) * scale;
            float pip = age < 0.14f ? 6f : 4f;
            int segments = 16;
            for (int i = 0; i < segments; i++) {
                double angle = i * Math.PI * 2 / segments + 0.19;
                float rx = burst.x + (float) Math.cos(angle) * ringRadius;
                float ry = burst.y + (float) Math.sin(angle) * ringRadius * GameRenderer.BOARD_SCALE_Y;
                shapes.setColor(i % 2 == 0 ? GOLD_BRIGHT : GOLD);
                drawClipped(shapes, rx - pip / 2, ry - pip / 2, pip, pip);
            }
        }

        // 2. Bold 8-ray impact starburst flash (brightest during hit-stop).
        if (age < 0.18f) {
            float decay = 1f - age / 0.18f;
            float ray = (16f + 26f * decay) * scale;
            float diag = ray * 0.68f;
            shapes.setColor(DANGER);
            drawClipped(shapes, burst.x - ray, burst.y - 3, ray * 2, 6);
            drawClipped(shapes, burst.x - 3, burst.y - ray, 6, ray * 2);
            shapes.setColor(GOLD);
            float inner = ray * 0.72f;
            drawClipped(shapes, burst.x - inner, burst.y - 4, inner * 2, 8);
            drawClipped(shapes, burst.x - 4, burst.y - inner, 8, inner * 2);
            for (int sx : new int[] {-1, 1}) {
                for (int sy : new int[] {-1, 1}) {
                    for (int step = 1; step <= 4; step++) {
                        float frac = step / 4f;
                        float dx = sx * diag * frac;
                        float dy = sy * diag * frac;
                        shapes.setColor(step <= 2 ? GOLD_BRIGHT : step == 3 ? GOLD : DANGER);
                        drawClipped(shapes, burst.x + dx - 3, burst.y + dy - 3, 6, 6);
                    }
                }
            }
            shapes.setColor(GOLD_BRIGHT);
            float core = 8f + 10f * decay;
            drawClipped(shapes, burst.x - core / 2, burst.y - core / 2, core, core);
        }

        // 3. Layered feather & spark burst (both bird palettes + impact sparks).
        if (age < 0.60f) {
            int count = 16;
            float t = age / 0.60f;
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count + (i % 3) * 0.14;
                boolean isSpark = i % 4 == 0;
                float speed = (isSpark ? 175f : 118f + (i % 3) * 22f) * scale;
                float travel = speed * (age * (1f - 0.35f * t));
                float flutter = isSpark ? 0f : (float) Math.sin(age * 18f + i * 1.7f) * 5f;
                float gravity = isSpark ? age * age * 45f : age * age * 72f;
                float px = burst.x + (float) Math.cos(angle) * travel + flutter;
                float py = burst.y + (float) Math.sin(angle) * travel * GameRenderer.BOARD_SCALE_Y - gravity;
                if (isSpark) {
                    float s = age < 0.28f ? 6f : 4f;
                    shapes.setColor(age < 0.14f ? GOLD_BRIGHT : DANGER);
                    drawClipped(shapes, px - s / 2, py - s / 2, s, s);
                } else {
                    // Alternating gold (white bird) and teal (black bird) pixel feathers.
                    boolean goldFeather = i % 2 == 1;
                    Color body = goldFeather ? GOLD : TEAL;
                    Color tip = goldFeather ? GOLD_BRIGHT : TEAL_BRIGHT;
                    float w = age < 0.36f ? 8f : 6f;
                    float h = 4f;
                    shapes.setColor(INK);
                    drawClipped(shapes, px - 2, py - 2, w + 2, h + 2);
                    shapes.setColor(body);
                    drawClipped(shapes, px, py, w, h);
                    shapes.setColor(tip);
                    drawClipped(shapes, px + (goldFeather ? 2 : 0), py + 2, 4, 2);
                }
            }
        }

        // 4. Landing dust puff on the origin square when the dropped piece touches down.
        if (age >= DROP_LANDING_TIME && age < DROP_LANDING_TIME + 0.24f) {
            float landAge = age - DROP_LANDING_TIME;
            float sx = GameRenderer.BOARD_X + (burst.event.square().getFile().ordinal() * 100 + 50) * GameRenderer.BOARD_SCALE_X;
            float sy = GameRenderer.BOARD_Y + (burst.event.square().getRank().ordinal() * 100 + 50) * GameRenderer.BOARD_SCALE_Y;
            float r = 14f + landAge * 135f;
            float size = landAge < 0.12f ? 6f : 4f;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + 0.39;
                shapes.setColor(i % 2 == 0 ? GOLD_BRIGHT : CREAM);
                drawClipped(shapes, sx + (float) Math.cos(angle) * r - size / 2,
                        sy + (float) Math.sin(angle) * r * GameRenderer.BOARD_SCALE_Y - size / 2, size, size);
            }
        }
    }

    private void drawEmptyBump(ShapeRenderer shapes, Burst burst) {
        float age = burst.age;
        float scale = 0.9f + 0.25f * burst.impactScale;
        if (age < 0.14f) {
            float decay = 1f - age / 0.14f;
            float arm = (16f + 16f * decay) * scale;
            shapes.setColor(INK);
            drawClipped(shapes, burst.x - arm - 2, burst.y - 4, arm * 2 + 4, 8);
            drawClipped(shapes, burst.x - 4, burst.y - arm - 2, 8, arm * 2 + 4);
            shapes.setColor(GOLD);
            drawClipped(shapes, burst.x - arm, burst.y - 2, arm * 2, 4);
            drawClipped(shapes, burst.x - 2, burst.y - arm, 4, arm * 2);
            shapes.setColor(GOLD_BRIGHT);
            drawClipped(shapes, burst.x - 6, burst.y - 6, 12, 12);
        }
        if (age < 0.20f) {
            float t = age / 0.20f;
            float r = (18f + 38f * (1f - (1f - t) * (1f - t))) * scale;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + 0.25;
                float px = burst.x + (float) Math.cos(angle) * r;
                float py = burst.y + (float) Math.sin(angle) * r * GameRenderer.BOARD_SCALE_Y;
                shapes.setColor(INK);
                drawClipped(shapes, px - 4, py - 4, 8, 8);
                shapes.setColor(i % 2 == 0 ? GOLD_BRIGHT : CREAM);
                drawClipped(shapes, px - 2, py - 2, 4, 4);
            }
        }
        if (age < 0.42f) {
            int count = 8;
            float t = age / 0.42f;
            float radius = (20f + 56f * (1f - (1f - t) * (1f - t))) * scale;
            float size = age < 0.22f ? 6f : 4f;
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count + 0.12;
                float px = burst.x + (float) Math.cos(angle) * radius;
                float py = burst.y + (float) Math.sin(angle) * radius * GameRenderer.BOARD_SCALE_Y - age * age * 45f;
                shapes.setColor(INK);
                drawClipped(shapes, px - size / 2 - 2, py - 4, size + 4, 8);
                shapes.setColor(i % 2 == 0 ? GOLD : TEAL);
                drawClipped(shapes, px - size / 2, py - 2, size, 4);
            }
        }
    }

    void drawCalloutShapes(ShapeRenderer shapes) {
        if (!hasCallout()) return;
        float cx = calloutX;
        float cy = calloutCurrentY();
        float w = 116f;
        float h = 28f;
        float left = cx - w / 2f;
        float bottom = cy - h / 2f;
        // Crisp stepped pixel badge consistent with GameRenderer overlays.
        shapes.setColor(INK);
        rect(shapes, left + 4, bottom - 4, w - 4, h);
        shapes.setColor(calloutAge < 0.16f ? GOLD_BRIGHT : DANGER);
        rect(shapes, left + 2, bottom, w - 4, h);
        rect(shapes, left, bottom + 2, w, h - 4);
        shapes.setColor(PANEL);
        rect(shapes, left + 4, bottom + 4, w - 8, h - 8);
    }

    void drawCalloutText(SpriteBatch batch, PixelFont font) {
        if (!hasCallout()) return;
        float cx = calloutX;
        float cy = calloutCurrentY();
        int width = font.width(calloutText, 2);
        font.draw(batch, calloutText, snap(cx - width / 2f), snap(cy + 7f), 2,
                calloutAge < 0.16f ? GOLD_BRIGHT : GOLD);
    }

    private static void drawClipped(ShapeRenderer shapes, float x, float y, float w, float h) {
        if (x >= GameRenderer.BOARD_X && x + w <= GameRenderer.BOARD_X + GameRenderer.BOARD_WIDTH
                && y >= GameRenderer.BOARD_Y && y + h <= GameRenderer.BOARD_Y + GameRenderer.BOARD_HEIGHT) {
            rect(shapes, x, y, w, h);
        }
    }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > 14) {
                lines.add(line);
                line = "";
            }
            line = line.isEmpty() ? word : line + " " + word;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    float pickupOffset(Side side) {
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            if (burst.event.kind() == Kind.PICKUP && burst.event.side() == side && burst.age < 0.24f) {
                return 18 * (1 - burst.age / 0.24f) * (side == Side.WHITE ? -1 : 1);
            }
        }
        return 0;
    }

    float dropOffset(Square square) {
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            Kind kind = burst.event.kind();
            if ((kind == Kind.DROP || kind == Kind.CAPTURE || kind == Kind.CANCEL)
                    && burst.event.square() == square && burst.age < 0.32f) {
                return (float) Math.sin(burst.age / 0.32f * Math.PI) * 12;
            }
            if (kind == Kind.BUMP_DROP && burst.event.square() == square
                    && burst.age >= DROP_LANDING_TIME && burst.age < DROP_LANDING_TIME + 0.24f) {
                float t = (burst.age - DROP_LANDING_TIME) / 0.24f;
                return (float) Math.sin(t * Math.PI) * 14f;
            }
        }
        return 0;
    }

    private static float snap(float value) { return Math.round(value / 2f) * 2f; }

    private static void rect(ShapeRenderer shapes, float x, float y, float w, float h) {
        shapes.rect(snap(x), snap(y), snap(w), snap(h));
    }

    String message() { return messageTime > 0 ? message : ""; }
    void clearPresentation() {
        bursts.clear();
        messageTime = 0;
        shakeTime = 0;
        shakeDuration = 0;
        shakeAmplitude = 0;
        flashTime = 0;
        flashDuration = 0;
        calloutText = "";
        calloutAge = CALLOUT_DURATION;
    }
    void notice(String text) {
        message = text;
        messageLines = wrap(text);
        messageTime = 3;
        messageColor = MINT;
    }
    void toggleSound() { if (audio != null) audio.toggle(); }
    boolean isSoundEnabled() { return audio != null && audio.isEnabled(); }
    @Override public void dispose() { if (audio != null) audio.dispose(); }
}
