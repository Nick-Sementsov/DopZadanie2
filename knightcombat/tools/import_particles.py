#!/usr/bin/env python3
"""Builds the particle textures of the Knightly Combat mod (sparks, flashes, blood, dust) from a free particle pack
and writes the particle definitions and the credits of the textures.

Run from the repository root:  python3 knightcombat/tools/import_particles.py [cache_dir]
Requires Pillow and NumPy (pip install pillow numpy). The source pack is downloaded once into cache_dir (by default
knightcombat/build/particle-sources). The pack draws white shapes on black; each texture is cropped to its shape,
scaled down and saved as white with the brightness as transparency, so the mod can colour it.

The source is CC0 (public domain); the credits are written to assets/knightcombat/textures/particle/credits.txt,
which ships in the mod.
"""

import json
import subprocess
import sys
import urllib.parse
import zipfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

MODID = "knightcombat"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / MODID

PACK_TITLE = "Particle Pack"
PACK_AUTHOR = "Kenney (kenney.nl)"
PACK_LICENSE = "CC0 1.0"
PACK_PAGE = "https://opengameart.org/content/particle-pack-80-sprites"
PACK_URL = "https://opengameart.org/sites/default/files/kenney_particlePack.zip"


@dataclass
class Texture:
    name: str
    source: str
    size: tuple
    # Raises the brightness before it becomes the transparency: above 1 makes soft edges harder.
    gain: float = 1.0
    # Keeps the texture square around its centre instead of cropping it to the shape.
    square: bool = True


TEXTURES = [
    # A thin streak, drawn along the flight of a spark.
    Texture("spark", "trace_01.png", (8, 32), gain=1.3, square=False),
    # Four-pointed stars where blades meet.
    Texture("flash_0", "star_06.png", (32, 32)),
    Texture("flash_1", "star_07.png", (32, 32)),
    # A drop of blood, and a splash of it where a blow lands.
    Texture("blood", "circle_05.png", (16, 16), gain=2.2),
    Texture("splash_0", "dirt_01.png", (32, 32), gain=1.6),
    Texture("splash_1", "dirt_02.png", (32, 32), gain=1.6),
    # Puffs of dust kicked up by feet and hooves.
    Texture("dust_0", "smoke_01.png", (32, 32)),
    Texture("dust_1", "smoke_02.png", (32, 32)),
    Texture("dust_2", "smoke_04.png", (32, 32)),
    Texture("dust_3", "smoke_05.png", (32, 32)),
    # A ring of dust spreading over the ground.
    Texture("ring", "circle_02.png", (64, 64)),
]

PARTICLES = {
    "spark": ["spark"],
    "flash": ["flash_0", "flash_1"],
    "blood": ["blood"],
    "blood_splash": ["splash_0", "splash_1"],
    "dust": ["dust_0", "dust_1", "dust_2", "dust_3"],
    "ring": ["ring"],
}


def download(cache):
    folder = cache / "particlepack"
    folder.mkdir(parents=True, exist_ok=True)
    name = urllib.parse.unquote(PACK_URL.split("/")[-1])
    path = folder / name
    if not path.exists():
        print("downloading", PACK_URL)
        subprocess.run(["curl", "-sSL", "--fail", "--max-time", "600", "-o", str(path), PACK_URL], check=True)
    marker = folder / (name + ".extracted")
    if not marker.exists():
        with zipfile.ZipFile(path) as archive:
            archive.extractall(folder, [n for n in archive.namelist() if n.startswith("PNG/") and "/Rotated/" not in n])
        marker.touch()
    return folder / "PNG"


def convert(texture, source):
    rgb = np.asarray(Image.open(source).convert("RGB"), dtype=np.float64) / 255
    level = rgb.max(axis=2)
    level = np.clip(level / max(level.max(), 1e-6) * texture.gain, 0, 1)
    # Crop to the shape (what is brighter than a faint glow), with a little margin.
    ys, xs = np.nonzero(level > 0.02)
    h, w = level.shape
    if texture.square:
        cx, cy = w / 2, h / 2
        half = max(abs(xs - cx).max(), abs(ys - cy).max()) + 4
        box = (cx - half, cy - half, cx + half, cy + half)
    else:
        box = (xs.min() - 4, ys.min() - 4, xs.max() + 5, ys.max() + 5)
    alpha = Image.fromarray(np.uint8(np.round(level * 255)), "L")
    alpha = alpha.crop(tuple(int(round(v)) for v in box)).resize(texture.size, Image.LANCZOS)
    out = Image.new("RGBA", texture.size, (255, 255, 255, 0))
    out.putalpha(alpha)
    return out


def write_credits(path):
    lines = ["Knightly Combat particle textures",
             "",
             "The textures of the sparks, flashes, blood and dust are made from the free particle pack below:",
             "cropped, scaled down and turned into white shapes the mod colours.",
             "",
             "Source:",
             f"- \"{PACK_TITLE}\" by {PACK_AUTHOR}, {PACK_LICENSE}: {PACK_PAGE}",
             "",
             "CC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/",
             ""]
    path.write_text("\n".join(lines), encoding="utf-8")


def main():
    cache = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "particle-sources"
    sources = download(cache)
    textures = ASSETS / "textures" / "particle"
    textures.mkdir(parents=True, exist_ok=True)
    for texture in TEXTURES:
        convert(texture, sources / texture.source).save(textures / (texture.name + ".png"), optimize=True)
        print(texture.name, "<-", texture.source)
    particles = ASSETS / "particles"
    particles.mkdir(parents=True, exist_ok=True)
    for name, sprites in PARTICLES.items():
        definition = {"textures": [f"{MODID}:{sprite}" for sprite in sprites]}
        (particles / (name + ".json")).write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8")
    write_credits(textures / "credits.txt")


if __name__ == "__main__":
    main()
