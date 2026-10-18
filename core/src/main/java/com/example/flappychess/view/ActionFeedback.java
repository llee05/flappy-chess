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
    private static final Color GOLD = Color.valueOf("efb95b");
    private static final Color MINT = Color.valueOf("add2b2");
    private static final Color DANGER = Color.valueOf("ed8c78");
    private static final Color PANEL = Color.valueOf("20283c");
    private static final class Burst {
        final Event event;
        final float x, y;
        float age;
        Burst(Event event, float x, float y) { this.event = event; this.x = x; this.y = y; }
    }

    private final FeedbackAudio audio = new FeedbackAudio();
    private final List<Burst> bursts = new ArrayList<>();
    private GameSession observed;
    private int seen;
    private String message = "";
    private List<String> messageLines = List.of();
    private float messageTime;
    private Color messageColor = MINT;

    void update(GameSession game, float delta) {
        if (observed != game) {
            observed = game;
            seen = 0;
            bursts.clear();
            messageTime = 0;
        }
        for (Burst burst : bursts) burst.age += delta;
        bursts.removeIf(burst -> burst.age >= 0.55f);
        messageTime = Math.max(0, messageTime - delta);
        for (Event event : game.getFeedback().events()) {
            if (event.sequence() <= seen) continue;
            seen = event.sequence();
            float x = 250 + event.square().getFile().ordinal() * 100;
            float y = 50 + event.square().getRank().ordinal() * 100;
            if (event.kind() == Kind.FLAP || event.kind() == Kind.PICKUP) {
                Birb bird = event.side() == Side.WHITE ? game.getWhite() : game.getBlack();
                x = 225 + (float) bird.getX();
                y = 775 - (float) bird.getY();
            }
            if (bursts.size() == GameFeedback.LIMIT) bursts.remove(0);
            bursts.add(new Burst(event, x, y));
            audio.play(event.kind());
            String text = GameFeedback.message(event);
            if (!text.isEmpty()) {
                message = text;
                messageLines = wrap(text);
                messageTime = 1.8f;
                messageColor = warning(event.kind()) ? DANGER : MINT;
            }
        }
    }

    private static boolean warning(Kind kind) {
        return kind == Kind.ILLEGAL_DROP || kind == Kind.NO_PIECE || kind == Kind.CHECK || kind == Kind.LOW_TIME;
    }

    void drawShapes(ShapeRenderer shapes) {
        for (Burst burst : bursts) {
            Kind kind = burst.event.kind();
            shapes.setColor(warning(kind) ? DANGER : kind == Kind.CAPTURE ? GOLD : MINT);
            if (kind == Kind.ILLEGAL_DROP || kind == Kind.NO_PIECE || kind == Kind.CHECK) {
                if ((int) (burst.age * 12) % 2 == 0) {
                    float x = 200 + burst.event.square().getFile().ordinal() * 100;
                    float y = burst.event.square().getRank().ordinal() * 100;
                    rect(shapes, x + 4, y + 4, 92, 4);
                    rect(shapes, x + 4, y + 92, 92, 4);
                    rect(shapes, x + 4, y + 4, 4, 92);
                    rect(shapes, x + 92, y + 4, 4, 92);
                }
            } else if (kind != Kind.LOW_TIME) {
                int count = kind == Kind.CAPTURE ? 12 : kind == Kind.FLAP ? 4 : 8;
                float radius = 14 + burst.age * (kind == Kind.CAPTURE ? 112 : 64);
                float size = burst.age < 0.25f ? 6 : 4;
                for (int i = 0; i < count; i++) {
                    double angle = i * Math.PI * 2 / count;
                    float x = burst.x + (float) Math.cos(angle) * radius;
                    float y = burst.y + (float) Math.sin(angle) * radius - burst.age * burst.age * 40;
                    // Keep particles on the board, clear of the HUD and outside letterboxing.
                    if (x >= 202 && x <= 992 && y >= 2 && y <= 792) rect(shapes, x, y, size, size);
                }
            }
        }
        if (messageTime > 0) {
            shapes.setColor(PANEL);
            rect(shapes, 12, 54, 176, 76);
            shapes.setColor(messageColor);
            rect(shapes, 12, 54, 2, 76);
        }
    }

    void drawText(PixelFont font, SpriteBatch batch) {
        if (messageTime > 0) {
            for (int i = 0; i < messageLines.size(); i++) {
                font.draw(batch, messageLines.get(i), 16, 126 - i * 18, 2, messageColor);
            }
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
            if ((burst.event.kind() == Kind.DROP || burst.event.kind() == Kind.CAPTURE || burst.event.kind() == Kind.CANCEL)
                    && burst.event.square() == square && burst.age < 0.32f) {
                return (float) Math.sin(burst.age / 0.32f * Math.PI) * 12;
            }
        }
        return 0;
    }

    private static void rect(ShapeRenderer shapes, float x, float y, float w, float h) {
        shapes.rect(Math.round(x / 2) * 2, Math.round(y / 2) * 2, w, h);
    }

    String message() { return messageTime > 0 ? message : ""; }
    void toggleSound() { audio.toggle(); }
    boolean isSoundEnabled() { return audio.isEnabled(); }
    @Override public void dispose() { audio.dispose(); }
}
