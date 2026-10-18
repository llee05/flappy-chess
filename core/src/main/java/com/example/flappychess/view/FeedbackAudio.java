package com.example.flappychess.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.example.flappychess.controller.GameFeedback.Kind;
import java.util.EnumMap;

/** Optional, quiet effects. Audio availability never determines whether a game can run. */
final class FeedbackAudio implements Disposable {
    private final EnumMap<Kind, Sound> sounds = new EnumMap<>(Kind.class);
    private Preferences preferences;
    private boolean enabled = true;

    FeedbackAudio() {
        try {
            preferences = Gdx.app.getPreferences("flappy-chess-settings");
            enabled = preferences.getBoolean("sound", true);
        } catch (RuntimeException unavailable) {
            Gdx.app.log("Audio", "Sound preference storage is unavailable.");
        }
        try {
            load(Kind.FLAP, "flap");
            load(Kind.PICKUP, "pickup");
            load(Kind.DROP, "drop");
            load(Kind.CAPTURE, "capture");
            load(Kind.ILLEGAL_DROP, "invalid");
            load(Kind.CHECK, "check");
            load(Kind.LOW_TIME, "low-time");
        } catch (RuntimeException unavailable) {
            dispose();
            Gdx.app.log("Audio", "Sound effects are unavailable on this device.");
        }
    }

    private void load(Kind kind, String name) {
        sounds.put(kind, Gdx.audio.newSound(Gdx.files.internal("sounds/" + name + ".wav")));
    }

    void play(Kind kind) {
        if (!enabled) return;
        Kind sample = kind == Kind.CANCEL ? Kind.DROP : kind == Kind.NO_PIECE ? Kind.ILLEGAL_DROP : kind;
        Sound sound = sounds.get(sample);
        if (sound != null) sound.play(kind == Kind.FLAP ? 0.14f : 0.35f);
    }

    void toggle() {
        enabled = !enabled;
        if (!enabled) for (Sound sound : sounds.values()) sound.stop();
        if (preferences != null) {
            try { preferences.putBoolean("sound", enabled).flush(); }
            catch (RuntimeException unavailable) { Gdx.app.log("Audio", "The sound preference could not be saved."); }
        }
    }

    boolean isEnabled() { return enabled; }

    @Override public void dispose() {
        for (Sound sound : sounds.values()) sound.dispose();
        sounds.clear();
    }
}
