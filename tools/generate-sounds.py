#!/usr/bin/env python3
"""Regenerate Flappy Chess's original, short PCM effects using Python's stdlib."""
import math
from pathlib import Path
import struct
import wave

OUTPUT = Path(__file__).resolve().parents[1] / "assets" / "sounds"
RATE = 22050
# Frequency sweeps/chords, kept quiet with smooth attack/release to avoid clicks.
EFFECTS = {
    "flap": [(0.07, 380, 600)],
    "pickup": [(0.06, 440, 440), (0.08, 660, 660)],
    "drop": [(0.05, 660, 660), (0.10, 440, 440)],
    "capture": [(0.06, 440, 660), (0.06, 660, 880), (0.10, 880, 880)],
    "invalid": [(0.09, 190, 130)],
    "check": [(0.09, 740, 740), (0.04, 0, 0), (0.13, 990, 990)],
    "low-time": [(0.11, 520, 520), (0.08, 0, 0), (0.11, 520, 520)],
}


def generate():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, notes in EFFECTS.items():
        frames = bytearray()
        for seconds, start, end in notes:
            count = round(seconds * RATE)
            phase = 0
            for i in range(count):
                phase += 2 * math.pi * (start + (end - start) * i / count) / RATE
                envelope = min(1, i / (RATE * 0.006), (count - 1 - i) / (RATE * 0.018))
                sample = (math.sin(phase) + 0.18 * math.sin(2 * phase)) if start else 0
                frames.extend(struct.pack("<h", round(sample * envelope * 9500)))
        with wave.open(str(OUTPUT / f"{name}.wav"), "wb") as sound:
            sound.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
            sound.writeframes(frames)


if __name__ == "__main__":
    generate()
