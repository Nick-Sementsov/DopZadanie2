#!/usr/bin/env python3
"""Synthesizes the sounds of the Knightly Combat mod and writes sounds.json.

Run from the repository root:  python3 knightcombat/tools/generate_sounds.py [preview_dir]
Requires NumPy and ffmpeg with the libvorbis encoder. Every sound is made from scratch here (noise, resonators,
inharmonic partials and a formant voice), so the mod ships no recorded or borrowed audio. The random seeds are
fixed: running the script again gives the same files. With preview_dir, WAV copies are written there too.

Sounds are mono so that Minecraft places them in the world (stereo sounds would not fade with distance).
"""

import json
import math
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

MODID = "knightcombat"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / MODID
RATE = 44100


# --------------------------------------------------------------------------- building blocks

def seconds(duration):
    return np.arange(int(duration * RATE)) / RATE


def noise(rng, duration):
    return rng.standard_normal(int(duration * RATE))


def envelope(t, attack, decay, hold=0.0):
    """Rises in {attack} seconds, holds, then falls exponentially with time constant {decay}."""
    rise = np.clip(t / max(attack, 1e-4), 0, 1) ** 2
    fall = np.exp(-np.clip(t - attack - hold, 0, None) / decay)
    return rise * fall


def resonator(signal, freq, bandwidth):
    """Two-pole band-pass filter whose centre frequency and bandwidth may change with every sample."""
    n = len(signal)
    freq = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    bandwidth = np.broadcast_to(np.asarray(bandwidth, dtype=float), (n,))
    r = np.exp(-math.pi * bandwidth / RATE)
    a1 = 2 * r * np.cos(2 * math.pi * freq / RATE)
    a2 = -r * r
    gain = 1 - r
    out = np.zeros(n)
    y1 = y2 = 0.0
    for i in range(n):
        y = gain[i] * signal[i] + a1[i] * y1 + a2[i] * y2
        out[i] = y
        y2, y1 = y1, y
    return out


def lowpass(signal, cutoff):
    """One-pole low-pass filter."""
    alpha = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    out = np.zeros(len(signal))
    y = 0.0
    for i, x in enumerate(signal):
        y += alpha * (x - y)
        out[i] = y
    return out


def highpass(signal, cutoff):
    return signal - lowpass(signal, cutoff)


def partials(t, f0, ratios, decays, amps, rng, detune=0.0):
    """A struck metal body: sine partials at inharmonic ratios, each dying away at its own rate."""
    out = np.zeros(len(t))
    for ratio, decay, amp in zip(ratios, decays, amps):
        f = f0 * ratio * (1 + detune * rng.uniform(-1, 1))
        phase = rng.uniform(0, 2 * math.pi)
        out += amp * np.sin(2 * math.pi * f * t + phase) * np.exp(-t / decay)
    return out


def room(signal, rng, size=0.25, mix=0.18):
    """A small stone room: the dry sound plus a burst of decaying noise convolved with it."""
    tail = int(size * RATE)
    impulse = rng.standard_normal(tail) * np.exp(-np.arange(tail) / (tail / 6))
    impulse = lowpass(impulse, 3500)
    impulse /= np.sqrt(np.sum(impulse ** 2))
    wet = np.convolve(signal, impulse)
    dry = np.concatenate([signal, np.zeros(len(wet) - len(signal))])
    return dry + mix * wet


def tail_fade(t, duration, share=0.35):
    """1 until the last {share} of the sound, then a smooth fall to 0, so ringing never stops with a click."""
    x = np.clip((duration - t) / (share * duration), 0, 1)
    return x * x * (3 - 2 * x)


def fade_out(signal, duration=0.02):
    n = min(len(signal), int(duration * RATE))
    signal = signal.copy()
    signal[-n:] *= np.linspace(1, 0, n)
    return signal


def normalize(signal, peak=0.9):
    signal = signal - np.mean(signal)
    return signal / (np.max(np.abs(signal)) + 1e-9) * peak


def mix(*parts):
    length = max(len(p) for p in parts)
    out = np.zeros(length)
    for p in parts:
        out[: len(p)] += p
    return out


def delayed(signal, delay):
    return np.concatenate([np.zeros(int(delay * RATE)), signal])


# --------------------------------------------------------------------------- weapons

def whoosh(rng, heavy=False):
    """Air cut by a blade: filtered noise whose pitch rises and falls as the blade passes, like a Doppler sweep."""
    duration = rng.uniform(0.42, 0.52) if heavy else rng.uniform(0.28, 0.36)
    t = seconds(duration)
    x = t / duration
    peak = rng.uniform(0.45, 0.6)
    shape = np.exp(-((x - peak) / (0.2 if heavy else 0.16)) ** 2)
    low, high = (180, 720) if heavy else (420, 1900)
    freq = low + (high - low) * shape * rng.uniform(0.85, 1.1)
    body = resonator(noise(rng, duration), freq, freq * (0.35 if heavy else 0.28))
    hiss = resonator(noise(rng, duration), freq * 2.6, freq * 0.9) * 0.25
    sound = (body + hiss) * shape ** 1.4
    if heavy:
        # A heavy head drags a low rumble behind it.
        rumble = resonator(noise(rng, duration), 95 + 60 * shape, 60) * shape ** 2 * 2.5
        sound = sound + rumble
    return fade_out(normalize(sound, 0.85))


def feint(rng):
    """A swing pulled back: a short whoosh that stops early and swishes back."""
    duration = 0.22
    t = seconds(duration)
    x = t / duration
    shape = np.sin(math.pi * np.clip(x / 0.55, 0, 1)) * np.exp(-np.clip(x - 0.55, 0, None) * 6)
    freq = 600 + 900 * np.sin(math.pi * np.clip(x / 0.6, 0, 1))
    sound = resonator(noise(rng, duration), freq, freq * 0.4) * shape
    return fade_out(normalize(sound, 0.6))


def clang(rng, f0, ring, bright=1.0):
    """Steel on steel: a sharp click, a short scrape of noise and inharmonic partials ringing out."""
    duration = ring * 2.2 + 0.1
    t = seconds(duration)
    ratios = [1.0, 2.32, 2.76, 3.91, 5.41, 6.83, 8.93, 11.4]
    decays = [ring, ring * 0.75, ring * 0.6, ring * 0.45, ring * 0.3, ring * 0.22, ring * 0.15, ring * 0.1]
    amps = [1.0, 0.7, 0.45, 0.55 * bright, 0.4 * bright, 0.3 * bright, 0.22 * bright, 0.15 * bright]
    metal = partials(t, f0, ratios, decays, amps, rng, detune=0.01)
    # The second blade rings a little out of tune with the first, so the sound beats.
    metal += 0.6 * partials(t, f0 * rng.uniform(1.05, 1.12), ratios, decays, amps, rng, detune=0.01)
    click = highpass(noise(rng, duration), 2500) * np.exp(-t / 0.004) * 2.5
    scrape = resonator(noise(rng, duration), 3200 * bright, 1800) * np.exp(-t / 0.05) * 3
    return (metal + click + scrape) * tail_fade(t, duration)


def block(rng):
    sound = clang(rng, rng.uniform(430, 560), rng.uniform(0.18, 0.26), bright=0.9)
    t = np.arange(len(sound)) / RATE
    thud = np.sin(2 * math.pi * 110 * t) * np.exp(-t / 0.04) * 0.8
    return fade_out(normalize(room(sound + thud, rng, mix=0.12)))


def parry(rng):
    """A perfect parry rings bright and long, with the scrape of one blade sliding along the other."""
    sound = clang(rng, rng.uniform(780, 950), rng.uniform(0.45, 0.6), bright=1.3)
    t = seconds(0.25)
    slide = resonator(noise(rng, 0.25), 2500 + 3500 * t / 0.25, 900) * np.sin(math.pi * t / 0.25) * 1.6
    return fade_out(normalize(room(mix(sound, delayed(slide, 0.01)), rng, mix=0.2)))


def guard_break(rng):
    """The guard gives way: a heavy dull clang, a crunch and a ring falling in pitch."""
    duration = 0.9
    t = seconds(duration)
    heavy = clang(rng, rng.uniform(260, 320), 0.28, bright=0.6)
    crunch = resonator(noise(rng, duration), 1400, 1600) * np.exp(-t / 0.06) * 3
    drop = np.sin(2 * math.pi * np.cumsum(700 * np.exp(-t / 0.35) + 180) / RATE) * np.exp(-t / 0.3) * 0.5
    thump = np.sin(2 * math.pi * np.cumsum(90 * np.exp(-t / 0.1) + 45) / RATE) * np.exp(-t / 0.09) * 1.5
    return fade_out(normalize(room(mix(heavy, crunch, drop * tail_fade(t, duration), thump), rng, mix=0.2)))


def hit(rng):
    """A blow that lands: a low thump of the body, the crunch of mail and cloth, and a little ring of armour."""
    duration = 0.45
    t = seconds(duration)
    thump = np.sin(2 * math.pi * np.cumsum(rng.uniform(70, 95) * (1 + 1.5 * np.exp(-t / 0.02))) / RATE) * np.exp(-t / 0.07) * 1.6
    crunch = resonator(noise(rng, duration), rng.uniform(900, 1500), 1400) * np.exp(-t / 0.035) * 3
    rattle = resonator(noise(rng, duration), 5200, 2500) * envelope(t, 0.005, 0.06) * 1.2
    ring = partials(t, rng.uniform(1300, 1700), [1, 2.7, 4.1], [0.09, 0.06, 0.04], [0.25, 0.2, 0.1], rng)
    return fade_out(normalize(mix(thump, crunch, rattle, ring)))


def wall(rng):
    """A blade striking stone: a dull knock, grit and a small ping of the steel."""
    duration = 0.5
    t = seconds(duration)
    knock = resonator(noise(rng, duration), rng.uniform(180, 260), 120) * np.exp(-t / 0.05) * 6
    grit = highpass(noise(rng, duration), 1800) * np.exp(-t / 0.03) * (0.7 + 0.3 * np.sign(np.sin(2 * math.pi * 90 * t)))
    ping = partials(t, rng.uniform(1900, 2300), [1, 2.4, 3.8], [0.12, 0.08, 0.05], [0.35, 0.2, 0.12], rng)
    return fade_out(normalize(room(mix(knock, grit, ping), rng, mix=0.25)))


def kick(rng):
    """A boot in the chest: a deep thud and the slap of leather."""
    duration = 0.4
    t = seconds(duration)
    thud = np.sin(2 * math.pi * np.cumsum(55 * (1 + 2 * np.exp(-t / 0.015))) / RATE) * np.exp(-t / 0.09) * 2
    slap = resonator(noise(rng, duration), rng.uniform(700, 1000), 900) * np.exp(-t / 0.025) * 3
    rustle = highpass(noise(rng, duration), 3000) * envelope(t, 0.01, 0.05) * 0.5
    return fade_out(normalize(mix(thud, slap, rustle)))


# --------------------------------------------------------------------------- battle cries

# Formants (frequency, bandwidth) of shouted vowels; a shout raises the first formant.
VOWELS = {
    "a": [(880, 90), (1300, 110), (2600, 160), (3500, 220)],
    "o": [(600, 80), (950, 100), (2500, 160), (3400, 220)],
    "u": [(380, 70), (760, 90), (2400, 160), (3300, 220)],
    "e": [(640, 80), (1800, 120), (2600, 160), (3500, 220)],
    "r": [(450, 120), (1250, 140), (1700, 200), (3300, 250)],
}


def glottis(rng, f0):
    """The buzz of the vocal folds following the pitch curve {f0}: every harmonic up to 9 kHz, falling off
    gently as in a strained shout, with a little jitter, a growl at half the pitch and some breath."""
    n = len(f0)
    jitter = 1 + 0.004 * lowpass(rng.standard_normal(n), 40) / 0.03
    phase = np.cumsum(f0 * jitter) / RATE
    source = np.zeros(n)
    for k in range(1, int(9000 / np.min(f0)) + 1):
        audible = np.clip((9000 - k * f0) / 1000, 0, 1)
        source += audible * np.sin(2 * math.pi * k * phase) / k ** 0.85
    # Every second cycle a little weaker: the rough "growl" of a shouting voice.
    growl = 1 + 0.18 * np.sin(math.pi * phase)
    shimmer = 1 + 0.05 * lowpass(rng.standard_normal(n), 25) / 0.024
    breath = highpass(rng.standard_normal(n), 1500) * 0.035 * (1 + np.sin(2 * math.pi * phase))
    return source * growl * shimmer * 0.25 + breath


def voice(rng, segments, f0_points, duration, brightness=1.0):
    """A voice saying the vowels in {segments} [(vowel, start, end)], with formants gliding between them."""
    t = seconds(duration)
    times, values = zip(*f0_points)
    f0 = np.interp(t, times, values)
    vibrato = 1 + 0.025 * np.sin(2 * math.pi * 5.5 * t + rng.uniform(0, 6)) * np.clip((t - 0.25) / 0.3, 0, 1)
    f0 = f0 * vibrato
    source = glottis(rng, f0)
    # Formant tracks: each vowel holds between its start and end and the tracks glide across the gaps.
    keys = []
    for vowel, start, end in segments:
        keys.append((start, VOWELS[vowel]))
        keys.append((end, VOWELS[vowel]))
    out = np.zeros(len(t))
    for k in range(4):
        freqs = np.interp(t, [key[0] for key in keys], [key[1][k][0] * brightness for key in keys])
        widths = np.interp(t, [key[0] for key in keys], [key[1][k][1] for key in keys])
        out += resonator(source, freqs, widths) * (1.0, 0.7, 0.35, 0.2)[k]
    return out


def shout_envelope(t, onset, end, release=0.12):
    return np.clip((t - onset) / 0.05, 0, 1) * np.clip((end - t) / release, 0, 1) ** 0.7


def battle_cry(rng, kind, pitch):
    """One knight's cry: a long 'A-a-a!', a rising 'U-ra-a!' or a short 'Ho!' followed by a long 'A!'."""
    if kind == "aaa":
        duration = rng.uniform(1.25, 1.45)
        segments = [("a", 0.0, duration)]
        f0 = [(0, 150 * pitch), (0.12, 210 * pitch), (0.35, 235 * pitch), (duration - 0.3, 225 * pitch), (duration, 170 * pitch)]
    elif kind == "ura":
        duration = rng.uniform(1.3, 1.5)
        segments = [("u", 0.0, 0.16), ("r", 0.2, 0.26), ("a", 0.32, duration)]
        f0 = [(0, 165 * pitch), (0.16, 175 * pitch), (0.3, 215 * pitch), (0.6, 245 * pitch), (duration - 0.25, 235 * pitch), (duration, 180 * pitch)]
    else:
        duration = rng.uniform(1.15, 1.3)
        segments = [("o", 0.0, 0.22), ("o", 0.22, 0.28), ("a", 0.42, duration)]
        f0 = [(0, 170 * pitch), (0.2, 185 * pitch), (0.3, 150 * pitch), (0.45, 220 * pitch), (duration - 0.25, 230 * pitch), (duration, 175 * pitch)]
    t = seconds(duration)
    sound = voice(rng, segments, f0, duration, brightness=rng.uniform(0.97, 1.05))
    amp = shout_envelope(t, 0.0, duration)
    if kind == "ho":
        # A breath of 'h' before the first vowel and a short break between the two words.
        sound = sound * (1 - 0.85 * np.exp(-((t - 0.34) / 0.05) ** 2))
        h = highpass(noise(rng, duration), 1200) * np.exp(-((t - 0.03) / 0.04) ** 2) * 0.12
        sound = sound + h * np.max(np.abs(sound))
    if kind == "ura":
        # The 'r' is a quick flap: two short dips in loudness.
        sound = sound * (1 - 0.6 * np.exp(-((t - 0.21) / 0.012) ** 2) - 0.5 * np.exp(-((t - 0.25) / 0.012) ** 2))
    return sound * amp


def saturate(signal, drive):
    """Soft clipping that gives the voice the strain of shouting."""
    return np.tanh(signal * drive) / np.tanh(drive)


def battle_cries(rng):
    cries = []
    for kind, pitch in (("aaa", 1.0), ("ura", 0.92), ("ho", 1.05), ("aaa", 0.85)):
        voice_sound = normalize(battle_cry(rng, kind, pitch))
        # Two more voices a little behind, as if the knights around join in.
        others = [normalize(battle_cry(rng, kind, pitch * rng.uniform(0.85, 1.15))) for _ in range(2)]
        group = mix(voice_sound, delayed(others[0] * 0.45, rng.uniform(0.04, 0.09)), delayed(others[1] * 0.35, rng.uniform(0.08, 0.14)))
        group = saturate(normalize(group), 1.6)
        group = highpass(group, 120)
        cries.append(fade_out(normalize(room(group, rng, size=0.35, mix=0.22))))
    return cries


# --------------------------------------------------------------------------- output

def write_wav(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = (np.clip(signal, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as file:
        file.setnchannels(1)
        file.setsampwidth(2)
        file.setframerate(RATE)
        file.writeframes(data.tobytes())


def write_ogg(relative, signal, preview_dir):
    path = ASSETS / "sounds" / (relative + ".ogg")
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "sound.wav"
        write_wav(wav, signal)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                        "-map_metadata", "-1", "-fflags", "+bitexact", str(path)], check=True)
        if preview_dir:
            shutil.copy(wav, Path(preview_dir) / (relative.replace("/", "_") + ".wav"))


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    if preview_dir:
        Path(preview_dir).mkdir(parents=True, exist_ok=True)
    sounds_dir = ASSETS / "sounds"
    if sounds_dir.exists():
        shutil.rmtree(sounds_dir)

    rng = np.random.default_rng(1180)
    events = {}

    def make(event, folder, name, generate, count, subtitle, volume=1.0, attenuation=None):
        entries = []
        for i in range(1, count + 1):
            relative = f"{folder}/{name}{i}"
            write_ogg(relative, generate(), preview_dir)
            entry = {"name": f"{MODID}:{relative}", "volume": volume}
            if attenuation:
                entry["attenuation_distance"] = attenuation
            entries.append(entry)
        events[event] = {"subtitle": f"subtitles.{MODID}.{subtitle}", "sounds": entries}

    make("weapon.whoosh", "weapon", "whoosh", lambda: whoosh(rng), 5, "weapon.whoosh", 0.7)
    make("weapon.whoosh_heavy", "weapon", "whoosh_heavy", lambda: whoosh(rng, heavy=True), 4, "weapon.whoosh_heavy", 0.85)
    make("weapon.hit", "weapon", "hit", lambda: hit(rng), 5, "weapon.hit")
    make("weapon.block", "weapon", "block", lambda: block(rng), 5, "weapon.block", 0.8)
    make("weapon.parry", "weapon", "parry", lambda: parry(rng), 4, "weapon.parry", 0.85, 24)
    make("weapon.guard_break", "weapon", "guard_break", lambda: guard_break(rng), 3, "weapon.guard_break", 0.9, 24)
    make("weapon.wall", "weapon", "wall", lambda: wall(rng), 4, "weapon.wall", 0.8)
    make("weapon.feint", "weapon", "feint", lambda: feint(rng), 3, "weapon.feint", 0.6)
    make("combat.kick", "combat", "kick", lambda: kick(rng), 4, "combat.kick")
    cries = iter(battle_cries(rng))
    make("combat.battle_cry", "combat", "battle_cry", lambda: next(cries), 4, "combat.battle_cry", 1.0, 48)

    path = ASSETS / "sounds.json"
    path.write_text(json.dumps(events, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
