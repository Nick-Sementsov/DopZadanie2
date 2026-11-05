#!/usr/bin/env python3
"""Builds the sounds of the Knightly Combat mod from recordings of free sound libraries and writes sounds.json
and the credits of the sounds.

Run from the repository root:  python3 knightcombat/tools/import_sounds.py [cache_dir]
Requires NumPy, py7zr (pip install numpy py7zr) and ffmpeg with the libvorbis encoder. The source packs are
downloaded once into cache_dir (by default knightcombat/build/sound-sources). Each sound is cut out of a recording,
cleaned (rumble filtered out, silence trimmed, faded, normalized) and saved as mono Ogg Vorbis, so that Minecraft
places it in the world.

Every source is CC0 (public domain) or free to use with credit (CC-BY 3.0, OGA-BY 3.0); the credits are written to
assets/knightcombat/sounds/credits.txt, which ships in the mod.
"""

import json
import os
import shutil
import subprocess
import sys
import urllib.parse
import wave
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

MODID = "knightcombat"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / MODID
RATE = 44100


@dataclass
class Pack:
    title: str
    author: str
    license: str
    page: str
    urls: list


OGA = "https://opengameart.org/sites/default/files/"
PACKS = {
    "impacts": Pack("Medieval sound effects - Weapon impacts", "Still North Media (posted by MedicineStorm)", "CC0 1.0",
                    "https://opengameart.org/content/medieval-sound-effects-weapon-impacts",
                    [OGA + "medieval_sfx_weapon_on_weapon_1_of_2.7z", OGA + "medieval_sfx_weapon_on_weapon_2_of_2.7z"]),
    "swishes": Pack("Swishes Sound Pack", "artisticdude", "CC0 1.0",
                    "https://opengameart.org/content/swishes-sound-pack", [OGA + "swishes.zip"]),
    "rpg": Pack("RPG Sound Pack", "artisticdude", "CC0 1.0",
                "https://opengameart.org/content/rpg-sound-pack", [OGA + "rpg_sound_pack.zip"]),
    "fleshy": Pack("Fleshy Fight Sounds", "will_leamon", "OGA-BY 3.0",
                   "https://opengameart.org/content/fleshy-fight-sounds", [OGA + "fleshy_fight_sounds.zip"]),
    "kenney": Pack("Impact Sounds", "Kenney (www.kenney.nl)", "CC0 1.0", "https://kenney.nl/assets/impact-sounds",
                   ["https://kenney.nl/media/pages/assets/impact-sounds/87b4ddecda-1677589768/kenney_impact-sounds.zip"]),
    "clashes": Pack("Fantasy Weapons and Apparel SFX Library", "Vehicle", "CC0 1.0",
                    "https://opengameart.org/content/fantasy-weapons-and-apparel-sfx-library", [OGA + "weapons-apparel.zip"]),
    "battlecry": Pack("Battlecry", "spookymodem", "CC-BY 3.0",
                      "https://opengameart.org/content/battlecry", [OGA + "Battlecry.wav"]),
    "yells": Pack("Male Grunt/Yelling sounds", "HaelDB", "CC0 1.0",
                  "https://opengameart.org/content/male-gruntyelling-sounds", [OGA + "yelling%20sounds.zip"]),
    "pain": Pack("grunts of male death and pain", "thebardofblasphemy", "CC0 1.0",
                 "https://opengameart.org/content/grunts-male-death-and-pain", [OGA + "death%20pain%20grunts.wav"]),
    "shieldblocks": Pack("10 Impact/Shield Blocks", "StarNinjas", "CC0 1.0",
                         "https://opengameart.org/content/10-impactshield-blocks", [OGA + "impact_-_starninjas.zip"]),
    "woodcracks": Pack("35 wooden cracks/hits/destructions", "qubodup (Independent.nu Ljudbank)", "CC0 1.0",
                       "https://opengameart.org/content/35-wooden-crackshitsdestructions",
                       [OGA + "independent_nu_ljudbank-wood_crack_hit_destruction.7z"]),
    "crunch": Pack("5 break, crunch impacts", "qubodup (Independent.nu Ljudbank)", "CC0 1.0",
                   "https://opengameart.org/content/5-break-crunch-impacts", [OGA + "independent_nu_ljudbank-break_crunch_impact.7z"]),
    "breaking": Pack("75 CC0 breaking / falling / hit sfx", "rubberduck", "CC0 1.0",
                     "https://opengameart.org/content/75-cc0-breaking-falling-hit-sfx", [OGA + "sfx_breaking_and_falling.zip"]),
    "breath": Pack("Breathing Tired", "mikeask", "CC0 1.0",
                   "https://opengameart.org/content/breathing-tired", [OGA + "breathing%20tired.wav"]),
    "footsteps": Pack("Footsteps Leather, Cloth, Armor", "HaelDB", "CC0 1.0",
                      "https://opengameart.org/content/footsteps-leather-cloth-armor", [OGA + "footsteps.zip"]),
}


@dataclass
class Clip:
    """A piece of a recording: the whole file, or start..end seconds of it; played at {speed} (lower is deeper)."""
    pack: str
    path: str
    start: float = 0.0
    end: float = None
    speed: float = 1.0
    gain: float = 1.0
    delay: float = 0.0
    layers: list = field(default_factory=list)
    align: bool = False


def clip(pack, path, start=0.0, end=None, speed=1.0, gain=1.0, delay=0.0, *layers, align=False):
    return Clip(pack, path, start, end, speed, gain, delay, list(layers), align)


@dataclass
class Event:
    name: str
    folder: str
    subtitle: str
    volume: float
    clips: list
    voice: bool = False
    impact: bool = False
    attenuation: int = None
    # Silence before the sound, for sounds that come a moment after what plays them (a body falling after the blow).
    lead: float = 0.0


def strikes(path, times, length):
    """Single blows cut out of a long recording of weapons striking each other again and again."""
    return [clip("impacts", path, t - 0.01, t + length) for t in times]


EVENTS = [
    # Swings: short real swishes; heavy weapons get the heavier ones played slower and deeper.
    Event("weapon.whoosh", "weapon", "weapon.whoosh", 0.55,
          [clip("swishes", f"swishes/swish-{n}.wav") for n in (1, 2, 3, 4)]
          + [clip("rpg", f"RPG Sound Pack/battle/{name}.wav") for name in ("swing", "swing2", "swing3")]),
    Event("weapon.whoosh_heavy", "weapon", "weapon.whoosh_heavy", 0.7,
          [clip("swishes", f"swishes/swish-{n}.wav", speed=0.72) for n in (5, 6, 7, 8, 9)]),
    Event("weapon.feint", "weapon", "weapon.feint", 0.4,
          [clip("swishes", f"swishes/swish-{n}.wav", speed=1.12) for n in (10, 11, 12)]),
    # Blows that land: cuts and stabs, on flesh and on armour.
    # The recordings of hits start with the swing; only the blow itself is kept, so it sounds the moment it lands.
    Event("weapon.hit", "weapon", "weapon.hit", 1.0,
          [clip("fleshy", f"{name}.wav") for name in ("sword-1a", "sword-1b", "sword-arm-2a", "sword-arm-2b",
                                                       "piercing-1a", "piercing-1b", "piercing-arm-2a", "piercing-arm-2b")],
          impact=True),
    Event("weapon.hit_heavy", "weapon", "weapon.hit_heavy", 1.0,
          [clip("fleshy", f"{name}.wav", 0.0, None, 1.0, 1.0, 0.0,
                clip("kenney", f"Audio/impactPunch_heavy_00{i}.ogg", gain=0.45))
           for i, name in enumerate(("hammer-1a", "hammer-1b", "hammer-arm-2a", "hammer-arm-2b"))], impact=True),
    # Steel on steel: short clangs for blocks, long ringing ones for parries, a mace crashing on a blade for a broken guard.
    Event("weapon.block", "weapon", "weapon.block", 0.85,
          strikes("Norse Sword Katana Blade on Blade.wav", (0.23, 2.84), 0.5)
          + strikes("Axe Norse Sword Blade on Blade.wav", (0.10, 2.08), 0.5)
          + strikes("Sabre Katana Blade on Blade.wav", (0.44, 2.63), 0.5), impact=True),
    # A parry: the clang of the blades with the long bright ring of a blade sliding along another on top.
    Event("weapon.parry", "weapon", "weapon.parry", 0.9,
          [clip("impacts", path, t - 0.01, t + 0.45, 1.0, 1.0, 0.0,
                clip("clashes", f"sword-knife-clash-{ring:02d}.wav", gain=0.6, align=True), align=True)
           for path, t, ring in (("Norse Sword Katana Blade on Blade.wav", 0.23, 41),
                                 ("Axe Norse Sword Blade on Blade.wav", 2.08, 8),
                                 ("Sabre Katana Blade on Blade.wav", 0.44, 9),
                                 ("Seax Norse Sword Blade on Blade.wav", 1.86, 4),
                                 ("Norse Sword Katana Blade on Blade.wav", 2.84, 17))], attenuation=24),
    Event("weapon.guard_break", "weapon", "weapon.guard_break", 0.95,
          [clip("impacts", "Mace Norse Sword Blade.wav", 0.57, 1.05, 1.0, 1.0, 0.0,
                clip("kenney", "Audio/impactPlate_heavy_000.ogg", gain=0.5)),
           clip("impacts", "Mace Norse Sword Blade.wav", 3.63, 5.2),
           clip("impacts", "Mace Norse Sword Blade.wav", 5.74, 6.6, 1.0, 1.0, 0.0,
                clip("kenney", "Audio/impactPlate_heavy_002.ogg", gain=0.5))], impact=True, attenuation=24),
    Event("weapon.wall", "weapon", "weapon.wall", 0.8,
          [clip("kenney", f"Audio/impactMining_00{i}.ogg") for i in range(5)]),
    Event("combat.kick", "combat", "combat.kick", 0.9,
          [clip("kenney", f"Audio/impactPunch_heavy_00{i}.ogg") for i in range(5)]
          + [clip("fleshy", f"{name}.wav") for name in ("punch_1a", "punch_1b")], impact=True),
    # Voices: long war cries, short shouts of effort when striking, grunts of pain when struck.
    Event("combat.battle_cry", "combat", "combat.battle_cry", 1.0,
          [clip("battlecry", "Battlecry.wav")]
          + [clip("yells", f"yelling sounds/{name}.wav") for name in ("3yell16", "3yell6", "yell11", "3yell9", "3yell1", "yell10")],
          voice=True, attenuation=48),
    Event("combat.attack_yell", "combat", "combat.attack_yell", 0.8,
          [clip("yells", f"yelling sounds/{name}.wav") for name in
           ("1yell4", "1yell5", "1yell12", "1yell13", "2yell7", "2yell9", "2yell11", "3yell5", "3yell8", "3yell12",
            "3yell15", "yell7", "yell12", "3grunt1", "3grunt2", "3grunt5")],
          voice=True, attenuation=24),
    Event("combat.pain", "combat", "combat.pain", 0.9,
          [clip("pain", "death pain grunts.wav", start, end) for start, end in
           ((6.9, 7.4), (8.3, 9.15), (27.5, 28.5), (29.8, 30.35), (31.45, 32.55), (40.35, 41.1), (0.45, 1.9), (35.9, 37.35))],
          voice=True),
    # A strained grunt as a heavy attack is wound up.
    Event("combat.effort", "combat", "combat.effort", 0.75,
          [clip("yells", f"yelling sounds/{name}.wav") for name in ("3grunt3", "3grunt4", "3grunt6")], voice=True, attenuation=16),
    # Plate and mail ringing under a blow, on top of the hit itself.
    Event("weapon.hit_armor", "weapon", "weapon.hit_armor", 0.8,
          [clip("kenney", f"Audio/impactPlate_light_00{i}.ogg") for i in range(5)]
          + [clip("kenney", f"Audio/impactPlate_medium_00{i}.ogg") for i in range(5)]),
    # A blade slid out of its scabbard when a weapon is taken in hand.
    Event("weapon.draw", "weapon", "weapon.draw", 0.6,
          [clip("rpg", f"RPG Sound Pack/battle/{name}.wav")
           for name in ("sword-unsheathe", "sword-unsheathe2", "sword-unsheathe3", "sword-unsheathe4", "sword-unsheathe5")]
          + [clip("clashes", f"{name}.wav") for name in ("knife-unsheathe-02", "knife-unsheathe-04", "seax-unsheathe-01", "seax-unsheathe-03")]),
    # A dodge: boots pushing off and landing, leather creaking, the body swishing through the air.
    Event("combat.dodge", "combat", "combat.dodge", 0.7,
          [clip("clashes", f"boots-leather-jump-0{i}.wav", 0.0, None, 1.0, 1.0, 0.0,
                clip("clashes", f"quiver-leather-squeeze-0{j}.wav", gain=0.45),
                clip("swishes", f"swishes/swish-{k}.wav", speed=0.9, gain=0.35))
           for i, j, k in ((1, 1, 10), (2, 5, 11), (3, 7, 12))]
          + [clip("clashes", f"boots-leather-step-0{i}.wav", 0.0, None, 1.0, 1.0, 0.0,
                  clip("swishes", f"swishes/swish-{k}.wav", speed=0.9, gain=0.35))
             for i, k in ((1, 13), (3, 2))]),
    # Throws: the arm swung hard; and a spinning weapon cutting the air in short whirrs.
    Event("weapon.throw", "weapon", "weapon.throw", 0.8,
          [clip("swishes", f"swishes/swish-{a}.wav", 0.0, None, 0.75, 1.0, 0.0,
                clip("swishes", f"swishes/swish-{b}.wav", speed=0.9, gain=0.7, delay=0.06)) for a, b in ((5, 9), (7, 6), (9, 8))]
          + [clip("rpg", "RPG Sound Pack/battle/swing3.wav", 0.0, None, 0.8, 1.0, 0.0,
                  clip("rpg", "RPG Sound Pack/battle/swing2.wav", speed=0.95, gain=0.6, delay=0.07))]),
    Event("weapon.spin", "weapon", "weapon.spin", 0.45,
          [clip("swishes", f"swishes/swish-{n}.wav", speed=1.3) for n in (1, 3, 4, 6)]),
    # A thrown weapon biting into wood or earth, the steel ringing a little after it.
    Event("weapon.stick", "weapon", "weapon.stick", 0.9,
          [clip("kenney", f"Audio/impactWood_heavy_00{i}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                clip("kenney", f"Audio/impactMetal_light_00{i}.ogg", gain=0.3)) for i in range(5)]
          + [clip("kenney", f"Audio/impactPlank_medium_00{i}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                  clip("kenney", f"Audio/impactMetal_light_00{(i + 2) % 5}.ogg", gain=0.3)) for i in range(3)]),
    # A weapon dropping on the ground or ringing off a wall.
    Event("weapon.clatter", "weapon", "weapon.clatter", 0.75,
          [clip("kenney", f"Audio/impactMetal_medium_00{i}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                clip("kenney", f"Audio/impactSoft_medium_00{i % 3}.ogg", gain=0.4)) for i in range(5)]),
    # A body in armour hitting the ground a moment after the killing blow: a heavy thud and the rattle of plates.
    Event("combat.body_fall", "combat", "combat.body_fall", 0.9,
          [clip("kenney", f"Audio/impactSoft_heavy_00{i}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                clip("kenney", f"Audio/impactPlate_light_00{(i + 1) % 5}.ogg", gain=0.35, delay=0.05)) for i in range(5)]
          + [clip("kenney", f"Audio/impactSoft_heavy_00{i}.ogg", 0.0, None, 0.9, 1.0, 0.0,
                  clip("breaking", f"bfh1_metal_falling_0{j}.ogg", gain=0.6, delay=0.04)) for i, j in ((1, 1), (3, 2))],
          lead=0.25),
    # Blows on a shield: the dull thud of the boards with the ring of the iron rim and boss, and recorded shield blocks.
    Event("weapon.shield_block", "weapon", "weapon.shield_block", 0.9,
          [clip("kenney", f"Audio/impactWood_heavy_00{i}.ogg", 0.0, None, 0.9, 1.0, 0.0,
                clip("kenney", f"Audio/impactPlate_medium_00{i}.ogg", gain=0.3)) for i in range(5)]
          + [clip("kenney", f"Audio/impactPlank_medium_00{i}.ogg", 0.0, None, 0.85, 1.0, 0.0,
                  clip("kenney", f"Audio/impactPlate_light_00{(i + 2) % 5}.ogg", gain=0.35)) for i in range(3)]
          + [clip("shieldblocks", f"impact.{n}.ogg", start) for n, start in ((1, 0.0), (2, 0.0), (3, 0.3), (5, 0.5), (6, 0.0), (9, 0.0))]),
    # A shield driven into someone: a heavy shove and the boards knocking.
    Event("combat.shield_bash", "combat", "combat.shield_bash", 0.95,
          [clip("kenney", f"Audio/impactPunch_heavy_00{i}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                clip("kenney", f"Audio/impactWood_heavy_00{(i + 1) % 5}.ogg", gain=0.7)) for i in range(5)]),
    # A lance driven home at a gallop: a heavy blow on the body, the shaft cracking and the shove of the horse behind it.
    Event("weapon.lance_hit", "weapon", "weapon.lance_hit", 1.0,
          [clip("fleshy", f"{name}.wav", 0.0, None, 0.9, 1.0, 0.0,
                clip("kenney", f"Audio/impactWood_heavy_00{i}.ogg", gain=0.8),
                clip("kenney", f"Audio/impactPunch_heavy_00{(i + 2) % 5}.ogg", gain=0.5))
           for i, name in enumerate(("hammer-1a", "hammer-1b", "hammer-arm-2a", "hammer-arm-2b"))], attenuation=32),
    # The shaft of a lance shattering on a blow struck at a gallop.
    Event("weapon.lance_splinter", "weapon", "weapon.lance_splinter", 0.85,
          [clip("breaking", f"bfh1_wood_breaking_0{n}.ogg", 0.0, None, 0.9, 1.0, 0.0,
                clip("woodcracks", f"wood_impact/crack{c:02d}.mp3.flac", 0.0, 0.5, 1.0, 0.7)) for n, c in ((1, 1), (2, 3), (3, 4), (4, 5))],
          attenuation=32),
    # Raising a shield: the leather of the straps creaking and the boards knocking against the arm.
    Event("weapon.shield_raise", "weapon", "weapon.shield_raise", 0.45,
          [clip("clashes", f"quiver-leather-squeeze-{i:02d}.wav", 0.0, 0.45, 1.0, 1.0, 0.0,
                clip("kenney", f"Audio/impactWood_light_00{j}.ogg", gain=0.6, delay=0.04))
           for i, j in ((2, 0), (4, 1), (9, 2), (12, 3))], attenuation=12),
    # A shield giving way under a blow: the boards cracking and splitting.
    Event("weapon.shield_splinter", "weapon", "weapon.shield_splinter", 0.9,
          [clip("woodcracks", f"wood_impact/crack{n:02d}.mp3.flac", 0.0, 0.9) for n in (7, 8, 9, 10)]
          + [clip("breaking", f"bfh1_wood_breaking_0{n}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                  clip("kenney", f"Audio/impactWood_heavy_00{n}.ogg", gain=0.6)) for n in (1, 2, 3, 4)], attenuation=24),
    # Arrows and thrown weapons striking a shield: a hollow knock of the boards.
    Event("weapon.arrow_shield", "weapon", "weapon.arrow_shield", 0.8,
          [clip("woodcracks", f"wood_impact/impactwood{n:02d}.mp3.flac") for n in (1, 4, 7, 10, 13, 16)], impact=True),
    # A thrust turned aside by a blade: steel sliding along steel.
    Event("weapon.scrape", "weapon", "weapon.scrape", 0.55,
          [clip("clashes", f"sword-table-leg-scrape-0{i}.wav", 0.0, 0.7) for i in (1, 2, 3, 4)]
          + [clip("clashes", f"sword-thermos-scrape-0{i}.wav", s, s + 0.8) for i, s in ((1, 0.6), (2, 0.7), (4, 0.1))]),
    # Looking a weapon over: the blade ringing softly as it is turned in the hand.
    Event("weapon.inspect", "weapon", "weapon.inspect", 0.3,
          [clip("rpg", "RPG Sound Pack/inventory/metal-ringing.wav")]
          + [clip("rpg", f"RPG Sound Pack/inventory/metal-small{n}.wav") for n in (1, 2, 3)], attenuation=8),
    # Bones giving way under a crushing blow.
    Event("combat.crunch", "combat", "combat.crunch", 0.8,
          [clip("crunch", f"impcrunch/impactcrunch0{n}.mp3.flac", start) for n, start in ((1, 0.3), (2, 0.0), (3, 0.0), (4, 0.0), (5, 0.45))]),
    # Out of breath: a fighter whose stamina ran out gasping for air.
    Event("combat.exhausted", "combat", "combat.exhausted", 0.6,
          [clip("breath", "breathing tired.wav", start, end, speed)
           for start, end in ((0.1, 0.8), (1.25, 1.95), (2.35, 3.15)) for speed in (1.0, 0.93)], attenuation=12),
    # Mail and plates rattling as an armoured fighter dodges, lands or is thrown about.
    Event("armor.rattle", "armor", "armor.rattle", 0.6,
          [clip("rpg", f"RPG Sound Pack/inventory/{name}.wav") for name in ("chainmail1", "chainmail2", "armor-light")]
          + [clip("footsteps", f"footsteps/{name}.ogg", 0.0, None, 1.0, 1.0, 0.0,
                  clip("rpg", "RPG Sound Pack/inventory/chainmail1.wav", gain=0.5, delay=0.03))
             for name in ("step_metal", "step_metal (2)", "step_metal (3)", "step_metal (4)")], attenuation=16),
    # Footsteps in armour: the plates and the mail clinking with every step.
    Event("armor.step", "armor", "armor.step", 0.3,
          [clip("footsteps", f"footsteps/{name}.ogg") for name in ("step_metal", "step_metal (2)", "step_metal (3)", "step_metal (4)")]
          + [clip("footsteps", f"footsteps/{name}.ogg", speed=0.9) for name in ("step_metal (2)", "step_metal (4)")], attenuation=12),
    # Lowering a lance: leather creaking and a buckle clinking.
    Event("weapon.couch", "weapon", "weapon.couch", 0.55,
          [clip("clashes", f"sheath-squeeze-0{i}.wav", 0.0, 0.6, 1.0, 1.0, 0.0,
                clip("clashes", f"belt-buckle-0{j}.wav", gain=0.4, delay=0.05)) for i, j in ((1, 3), (4, 4), (6, 3), (7, 4))]),
]


# --------------------------------------------------------------------------- sources

def download(cache):
    for key, pack in PACKS.items():
        folder = cache / key
        folder.mkdir(parents=True, exist_ok=True)
        for url in pack.urls:
            name = urllib.parse.unquote(url.split("/")[-1])
            path = folder / name
            if not path.exists():
                print("downloading", url)
                subprocess.run(["curl", "-sSL", "--fail", "--max-time", "600", "-o", str(path), url], check=True)
            marker = folder / (name + ".extracted")
            if name.endswith(".zip") and not marker.exists():
                zipfile.ZipFile(path).extractall(folder)
                marker.touch()
            elif name.endswith(".7z") and not marker.exists():
                import py7zr
                with py7zr.SevenZipFile(path) as archive:
                    archive.extractall(folder)
                marker.touch()


def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64)


# --------------------------------------------------------------------------- cleaning

def envelope(x, ms):
    n = max(1, int(RATE * ms / 1000))
    return np.sqrt(np.convolve(x * x, np.ones(n) / n, mode="same"))


def decibels(v):
    return 20 * np.log10(np.maximum(v, 1e-9))


def highpass(x, hz):
    """Removes rumble and handling noise below {hz} (two passes of a one-pole filter)."""
    a = np.exp(-2 * np.pi * hz / RATE)
    for _ in range(2):
        y = np.empty_like(x)
        prev_x = prev_y = 0.0
        for i, v in enumerate(x):
            prev_y = a * (prev_y + v - prev_x)
            prev_x = v
            y[i] = prev_y
        x = y
    return x


def trim(x, start_db, end_db):
    """Cuts the silence before the sound and the noise after it fades below {end_db} under its peak."""
    level = decibels(envelope(x, 5))
    peak = level.max()
    loud = np.where(level > peak + start_db)[0]
    tail = np.where(level > peak + end_db)[0]
    if len(loud) == 0:
        return x
    start = max(0, loud[0] - int(0.004 * RATE))
    end = min(len(x), tail[-1] + int(0.02 * RATE))
    return x[start:end]


def fade(x, fade_in, fade_out):
    x = x.copy()
    a = min(len(x), int(fade_in * RATE))
    b = min(len(x), int(fade_out * RATE))
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def resample(x, speed):
    """Plays the sound {speed} times faster: higher and shorter, or lower and longer below 1."""
    if speed == 1.0:
        return x
    positions = np.arange(0, len(x) - 1, speed)
    return np.interp(positions, np.arange(len(x)), x)


def render(c, cache):
    folder = cache / c.pack
    matches = sorted(folder.rglob(c.path)) if "/" not in c.path else [folder / c.path]
    x = load(next(p for p in matches if "__MACOSX" not in str(p)))
    start = int(c.start * RATE)
    end = int(c.end * RATE) if c.end is not None else len(x)
    x = resample(x[start:end], c.speed) * c.gain
    if c.align:
        # Starts at its own blow and keeps only that one, so it lines up with the clip it is laid over.
        x = single_blow(trim(x, -30, -90))
    x = np.concatenate([np.zeros(int(c.delay * RATE)), x])
    for layer in c.layers:
        other = render(layer, cache)
        other = other / (np.abs(other).max() + 1e-9) * np.abs(x).max() * layer.gain
        length = max(len(x), len(other))
        x = np.pad(x, (0, length - len(x))) + np.pad(other, (0, length - len(other)))
    return x


def onset_after(level, start, rise=10, floor=-35):
    """The first point after {start} where the level jumps by {rise} dB within 20 ms: a new blow or a new word."""
    lag = int(0.02 * RATE)
    peak = level.max()
    jump = level[lag:] - level[:-lag]
    found = np.where((jump > rise) & (level[lag:] > peak + floor))[0] + lag
    found = found[found > start]
    return found[0] - lag if len(found) else None


def single_blow(x):
    """Keeps one blow: from the rise of the loudest transient (dropping a swing heard before it) to the next blow."""
    level = decibels(envelope(x, 3))
    peak_at = int(np.argmax(level))
    quiet = np.where(level[:peak_at] < level[peak_at] - 12)[0]
    start = max(0, (quiet[-1] if len(quiet) else 0) - int(0.01 * RATE))
    end = onset_after(level, peak_at + int(0.08 * RATE))
    return x[start:end - int(0.01 * RATE)] if end is not None else x[start:]


def first_utterance(x):
    """Keeps the first shout of a voice recording: everything after a pause of 60 ms or more is cut off."""
    level = decibels(envelope(x, 10))
    peak = level.max()
    loud = level > peak - 30
    first = int(np.argmax(loud))
    pause = int(0.06 * RATE)
    quiet_run = 0
    for i in range(first, len(x)):
        quiet_run = quiet_run + 1 if not loud[i] else 0
        if quiet_run >= pause:
            return x[:i]
    return x


def clean(x, event):
    x = highpass(x, 90 if event.voice else 45)
    if event.impact:
        x = single_blow(x)
    x = trim(x, -40, -48 if event.voice else -52)
    if event.voice:
        x = first_utterance(x)
    x = fade(x, 0.002 if event.impact else 0.003, 0.08 if event.voice else 0.04)
    return x / (np.abs(x).max() + 1e-9) * 0.89


# --------------------------------------------------------------------------- output

def write_ogg(path, x):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", "-fflags", "+bitexact", str(path)],
                   input=data, check=True)


def write_credits(path):
    lines = ["Sounds of Knightly Combat",
             "",
             "All sounds are recordings from free sound libraries, cut, filtered, mixed, resampled and normalized for the mod.",
             "",
             "Sources:"]
    for pack in PACKS.values():
        lines.append(f"- \"{pack.title}\" by {pack.author}, {pack.license}: {pack.page}")
    lines += ["",
              "CC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/",
              "CC-BY 3.0: https://creativecommons.org/licenses/by/3.0/",
              "OGA-BY 3.0: https://static.opengameart.org/OGA-BY-3.0.txt",
              ""]
    path.write_text("\n".join(lines), encoding="utf-8")


def main():
    cache = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "sound-sources"
    download(cache)
    sounds_dir = ASSETS / "sounds"
    if sounds_dir.exists():
        shutil.rmtree(sounds_dir)
    events = {}
    for event in EVENTS:
        entries = []
        name = event.name.split(".")[1]
        for i, c in enumerate(event.clips, start=1):
            relative = f"{event.folder}/{name}{i}"
            sound = clean(render(c, cache), event)
            write_ogg(sounds_dir / (relative + ".ogg"), np.concatenate([np.zeros(int(event.lead * RATE)), sound]))
            entry = {"name": f"{MODID}:{relative}", "volume": event.volume}
            if event.attenuation:
                entry["attenuation_distance"] = event.attenuation
            entries.append(entry)
        events[event.name] = {"subtitle": f"subtitles.{MODID}.{event.subtitle}", "sounds": entries}
        print(f"{event.name}: {len(entries)} sounds")
    (ASSETS / "sounds.json").write_text(json.dumps(events, indent=2) + "\n", encoding="utf-8")
    write_credits(sounds_dir / "credits.txt")


if __name__ == "__main__":
    main()
