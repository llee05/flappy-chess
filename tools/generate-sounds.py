#!/usr/bin/env python3
"""Regenerate Flappy Chess's original, short PCM effects using Python's stdlib."""
import math
from pathlib import Path
import random
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


def add_sweep(buf, offset, seconds, start, end, gain, attack=0.003, decay=6.0, harmonic=0.2):
    start_idx = round(offset * RATE)
    count = round(seconds * RATE)
    phase = 0.0
    for i in range(count):
        idx = start_idx + i
        if idx >= len(buf):
            break
        t = i / count
        freq = start + (end - start) * t
        phase += 2 * math.pi * freq / RATE
        env = min(1.0, i / max(1.0, RATE * attack), (count - 1 - i) / max(1.0, RATE * 0.012))
        env *= math.exp(-decay * t)
        sample = math.sin(phase) + harmonic * math.sin(2 * phase)
        buf[idx] += sample * env * gain


def add_noise_burst(buf, offset, seconds, gain, seed, smoothing=0.55):
    rng = random.Random(seed)
    start_idx = round(offset * RATE)
    count = round(seconds * RATE)
    filtered = 0.0
    for i in range(count):
        idx = start_idx + i
        if idx >= len(buf):
            break
        t = i / count
        white = rng.uniform(-1.0, 1.0)
        filtered = smoothing * filtered + (1.0 - smoothing) * white
        env = min(1.0, i / max(1.0, RATE * 0.002)) * ((1.0 - t) ** 2.2)
        buf[idx] += filtered * env * gain


def write_wav(name, samples):
    frames = bytearray()
    for sample in samples:
        clamped = max(-32767, min(32767, round(sample)))
        frames.extend(struct.pack("<h", clamped))
    with wave.open(str(OUTPUT / f"{name}.wav"), "wb") as sound:
        sound.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        sound.writeframes(frames)


def generate_bump():
    # Crisp, light hollow knock for empty-handed midair collisions.
    buf = [0.0] * round(0.14 * RATE)
    add_sweep(buf, 0.0, 0.12, 260, 95, gain=0.72, attack=0.0025, decay=3.6, harmonic=0.22)
    add_sweep(buf, 0.0, 0.055, 620, 280, gain=0.38, attack=0.002, decay=5.0, harmonic=0.12)
    add_noise_burst(buf, 0.0, 0.024, gain=0.55, seed=1801, smoothing=0.45)
    peak = max((abs(s) for s in buf), default=1.0) or 1.0
    return [math.tanh(s / peak * 1.15) * 14500 for s in buf]


def generate_bump_drop():
    # Heavy layered thud + crack + piece-dislodge rattle for carrying bumps.
    buf = [0.0] * round(0.30 * RATE)
    add_sweep(buf, 0.0, 0.16, 195, 48, gain=1.05, attack=0.002, decay=2.8, harmonic=0.32)
    add_sweep(buf, 0.0, 0.070, 780, 210, gain=0.55, attack=0.0015, decay=4.5, harmonic=0.24)
    add_noise_burst(buf, 0.0, 0.042, gain=0.85, seed=1802, smoothing=0.35)
    # Two tumbling clacks as the knocked-loose piece flies back to its square.
    add_sweep(buf, 0.055, 0.095, 540, 380, gain=0.46, attack=0.003, decay=4.2, harmonic=0.18)
    add_sweep(buf, 0.145, 0.130, 410, 255, gain=0.38, attack=0.003, decay=3.8, harmonic=0.18)
    peak = max((abs(s) for s in buf), default=1.0) or 1.0
    return [math.tanh(s / peak * 1.35) * 19500 for s in buf]


def generate():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, notes in EFFECTS.items():
        samples = []
        for seconds, start, end in notes:
            count = round(seconds * RATE)
            phase = 0
            for i in range(count):
                phase += 2 * math.pi * (start + (end - start) * i / count) / RATE
                envelope = min(1, i / (RATE * 0.006), (count - 1 - i) / (RATE * 0.018))
                sample = (math.sin(phase) + 0.18 * math.sin(2 * phase)) if start else 0
                samples.append(sample * envelope * 9500)
        write_wav(name, samples)
    write_wav("bump", generate_bump())
    write_wav("bump-drop", generate_bump_drop())


if __name__ == "__main__":
    generate()
