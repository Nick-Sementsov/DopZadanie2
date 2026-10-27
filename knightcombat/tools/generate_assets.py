#!/usr/bin/env python3
"""Draws the textures of the Knightly Combat mod: the stamina bar of the HUD and the logo of the mod list.

Run from the repository root:  python3 knightcombat/tools/generate_assets.py [preview.png]
Requires Pillow and NumPy (pip install pillow numpy). Files are written into src/main/resources
and overwrite earlier versions, so edit this script rather than the generated files.
The sounds are made by generate_sounds.py.

The swords of the logo are drawn along the diagonal of a texture, from the bottom left corner (pommel or butt)
to the top right corner (tip), like Minecraft's own swords. Shapes are given in "uv" coordinates:
u runs along that diagonal and v across it (positive v is the upper left side), both in pixels.
"""

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

MODID = "knightcombat"
ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / MODID
SQ2 = math.sqrt(2)

# Colours of each material: light side, middle, shadow side and outline.
PALETTE = {
    "steel": ((238, 243, 247), (189, 199, 209), (128, 139, 152), (52, 58, 68)),
    "fuller": ((160, 171, 183), (140, 151, 164), (118, 129, 142), (52, 58, 68)),
    "iron": ((168, 176, 186), (120, 128, 140), (80, 86, 98), (34, 38, 46)),
    "gold": ((255, 228, 124), (224, 172, 66), (152, 102, 34), (88, 54, 16)),
    "leather": ((150, 98, 60), (110, 68, 38), (74, 44, 24), (38, 22, 10)),
    "wood": ((166, 118, 70), (128, 88, 50), (92, 61, 34), (46, 29, 15)),
    "red": ((222, 84, 72), (176, 44, 44), (116, 26, 30), (58, 12, 14)),
}


class Sprite:
    """A square texture filled with materials, shaded and outlined at the end."""

    def __init__(self, size):
        self.size = size
        self.material = np.full((size, size), "", dtype=object)
        self.tone = np.full((size, size), np.nan)  # explicit shade -1..1, NaN means shade by neighbours
        ys, xs = np.mgrid[0:size, 0:size]
        big_x = xs + 0.5
        big_y = size - (ys + 0.5)
        self.u = (big_x + big_y) / SQ2
        self.v = (big_y - big_x) / SQ2

    def fill(self, mask, material, tone=None):
        self.material[mask] = material
        self.tone[mask] = np.nan if tone is None else tone[mask]

    def band(self, u0, u1, half_width, material, centre=0.0, taper=0.0, style="stripes", min_width=0.0):
        """A straight piece along the diagonal: blades, grips, shafts. The last {taper} pixels narrow to a point."""
        width = np.full(self.u.shape, float(half_width))
        if taper > 0:
            width = np.where(self.u > u1 - taper, half_width * np.clip((u1 - self.u) / taper, 0, 1), width)
            width = np.maximum(width, min_width)
        offset = self.v - centre
        mask = (self.u >= u0) & (self.u <= u1) & (np.abs(offset) <= width + 1e-6)
        if style == "stripes":
            tone = np.clip(offset / np.maximum(width, 0.35), -1, 1)
        elif style == "wrap":
            step = np.round(self.u / (SQ2 / 2)).astype(int)
            tone = np.where(step % 3 == 0, -0.8, np.clip(offset / np.maximum(width, 0.35), -1, 1) * 0.6 + 0.2)
        else:
            tone = None
        self.fill(mask, material, tone)

    def cross(self, u0, u1, v0, v1, material):
        """A piece across the diagonal: guards, collars, bands."""
        mask = (self.u >= u0) & (self.u <= u1) & (self.v >= v0) & (self.v <= v1)
        self.fill(mask, material)

    def disc(self, u, v, radius, material):
        mask = (self.u - u) ** 2 + (self.v - v) ** 2 <= radius * radius
        self.fill(mask, material)

    def poly(self, points, material):
        mask = point_in_polygon(self.u, self.v, points)
        self.fill(mask, material)

    def dot(self, u, v, material, tone=None):
        """The single pixel nearest to (u, v)."""
        x = (u - v) / SQ2
        y = self.size - (u + v) / SQ2
        px, py = int(math.floor(x)), int(math.floor(y))
        if 0 <= px < self.size and 0 <= py < self.size:
            self.material[py, px] = material
            self.tone[py, px] = np.nan if tone is None else tone

    def render(self):
        size = self.size
        filled = self.material != ""
        rgba = np.zeros((size, size, 4), dtype=np.uint8)
        for y in range(size):
            for x in range(size):
                name = self.material[y, x]
                if not name:
                    continue
                light, mid, dark, _ = PALETTE[name]
                tone = self.tone[y, x]
                if np.isnan(tone):
                    tone = neighbour_tone(self.material, x, y)
                if tone > 0.34:
                    colour = light
                elif tone < -0.34:
                    colour = dark
                else:
                    colour = mid
                rgba[y, x] = (*colour, 255)
        # Outline: every empty pixel next to a filled one (4-neighbourhood) takes the darkest outline around it.
        for y in range(size):
            for x in range(size):
                if filled[y, x]:
                    continue
                best = None
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < size and 0 <= ny < size and filled[ny, nx]:
                        line = PALETTE[self.material[ny, nx]][3]
                        if best is None or sum(line) < sum(best):
                            best = line
                if best is not None:
                    rgba[y, x] = (*best, 255)
        return Image.fromarray(rgba, "RGBA")


def neighbour_tone(material, x, y):
    """Light comes from the top left: edges facing it are light, edges facing away are dark."""
    size = material.shape[0]
    name = material[y, x]

    def outside(dx, dy):
        nx, ny = x + dx, y + dy
        return not (0 <= nx < size and 0 <= ny < size) or material[ny, nx] != name

    lit = outside(-1, 0) or outside(0, -1)
    shaded = outside(1, 0) or outside(0, 1)
    if lit and not shaded:
        return 1.0
    if shaded and not lit:
        return -1.0
    return 0.0


def point_in_polygon(u, v, points):
    inside = np.zeros(u.shape, dtype=bool)
    n = len(points)
    for i in range(n):
        u1, v1 = points[i]
        u2, v2 = points[(i + 1) % n]
        crosses = (v1 > v) != (v2 > v)
        at = u1 + (v - v1) * (u2 - u1) / ((v2 - v1) if v2 != v1 else 1e-9)
        inside ^= crosses & (u < at)
    return inside



# --------------------------------------------------------------------------- logo

def longsword():
    s = Sprite(32)
    s.disc(4.4, 0, 1.9, "gold")
    s.band(5.9, 16.2, 0.95, "leather", style="wrap")
    s.cross(16.2, 18.3, -6.6, 6.6, "gold")
    s.disc(17.2, 6.7, 1.1, "gold")
    s.disc(17.2, -6.7, 1.1, "gold")
    s.band(18.3, 44.6, 1.5, "steel", taper=5.0, min_width=0.3)
    s.band(19.0, 36.0, 0.36, "fuller")
    return s


def halberd():
    s = Sprite(32)
    s.band(0.8, 38.6, 0.9, "wood")
    s.band(9.0, 17.0, 0.95, "leather", style="wrap")
    s.cross(0.6, 2.2, -1.1, 1.1, "iron")
    s.cross(28.0, 30.0, -1.2, 1.2, "red")
    s.poly([(30.4, -1.0), (35.0, -1.0), (35.8, -3.0), (37.4, -6.6), (35.8, -7.8), (31.6, -8.0), (27.6, -7.2),
            (26.6, -5.8), (29.8, -3.0)], "iron")
    s.poly([(26.4, -6.2), (37.0, -6.8), (35.8, -7.8), (31.6, -8.0), (27.6, -7.2)], "steel")
    s.poly([(31.4, 1.0), (33.8, 1.0), (36.8, 5.6), (35.4, 5.4), (33.0, 2.8)], "iron")
    s.cross(29.6, 38.6, -1.2, 1.2, "iron")
    s.band(38.6, 45.2, 1.4, "steel", taper=4.6, min_width=0.3)
    return s


def logo():
    """Mod list logo: a longsword and a halberd crossed over a heater shield."""
    size = 128
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ys, xs = np.mgrid[0:size, 0:size]
    cx = size / 2
    # Heater shield: straight top, sides curving to a point at the bottom.
    half = 38 - np.clip((ys - 58) / 52, 0, 1) ** 1.6 * 38
    shield = (ys >= 16) & (ys <= 112) & (np.abs(xs + 0.5 - cx) <= half)
    rim = shield & ~((ys >= 21) & (ys <= 106) & (np.abs(xs + 0.5 - cx) <= half - 5))
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    shade = (1 - 0.35 * (ys - 16) / 96)[..., None]
    body = np.zeros((size, size, 3))
    body[...] = np.array([150, 28, 32]) * shade
    rgba[..., :3] = np.where(shield[..., None], body, 0).astype(np.uint8)
    rgba[rim] = (226, 176, 72, 255)
    rgba[shield & ~rim, 3] = 255
    image = Image.fromarray(rgba, "RGBA")
    sword = longsword().render().resize((96, 96), Image.NEAREST)
    polearm = halberd().render().resize((96, 96), Image.NEAREST).transpose(Image.FLIP_LEFT_RIGHT)
    image.alpha_composite(polearm, (16, 14))
    image.alpha_composite(sword, (16, 14))
    image.save(RESOURCES / "logo.png")


# --------------------------------------------------------------------------- stamina bar
# The bar is a sword lying across the screen: pommel, grip and guard on the left, the blade fills with gold
# as stamina comes back. The atlas holds the empty sword and three fills of the blade (the same size, so a fill
# is drawn over the sword cut at the stamina level): gold, red for low stamina, and white for stamina just spent.

BAR_W, BAR_H = 76, 11
BLADE_X0 = 13          # first column of the blade (after the guard)
TIP = 11               # columns over which the blade narrows to its point
OUTLINE = (24, 20, 26)
GOLD = ((255, 232, 140), (232, 180, 72), (168, 112, 36), (70, 42, 12))
LEATHER = ((128, 82, 48), (98, 60, 34), (70, 42, 22), (38, 22, 10))
# Rows of the blade from its upper edge to its lower edge, between the outlines.
EMPTY_ROWS = [(92, 98, 110), (58, 62, 72), (42, 45, 53), (50, 54, 62), (34, 36, 42)]
FILL_ROWS = {
    "gold": [(255, 246, 196), (250, 210, 104), (222, 166, 60), (204, 142, 44), (146, 94, 26)],
    "red": [(255, 190, 170), (238, 96, 74), (204, 56, 46), (180, 42, 38), (118, 24, 24)],
    "white": [(255, 255, 255), (255, 250, 236), (255, 244, 220), (255, 240, 210), (236, 220, 196)],
}


def blade_half_height(x):
    """Half height (in rows) of the blade at column x: 2 along the blade, shrinking to 0 at the point."""
    left = BAR_W - 1 - x
    return min(2.0, left * 2.0 / TIP)


def stamina_bar():
    atlas = np.zeros((64, 128, 4), dtype=np.uint8)
    frame = atlas[0:BAR_H, 0:BAR_W]
    mid = BAR_H // 2

    def put(img, x, y, colour, alpha=255):
        img[y, x] = (*colour, alpha)

    # Blade: outline, then the steel rows; the point narrows row by row.
    for x in range(BLADE_X0, BAR_W):
        half = blade_half_height(x)
        rows = [r for r in range(-2, 3) if abs(r) <= half + 0.01]
        for r in rows:
            put(frame, x, mid + r, EMPTY_ROWS[r + 2])
        top, bottom = mid + min(rows) - 1, mid + max(rows) + 1
        put(frame, x, top, OUTLINE)
        put(frame, x, bottom, OUTLINE)
    put(frame, BAR_W - 1, mid, OUTLINE)
    # Guard: a gold bar across the blade with rounded ends.
    for y in range(0, BAR_H):
        end = y in (0, BAR_H - 1)
        for x in (11, 12):
            colour = GOLD[0] if x == 11 else GOLD[2]
            if end:
                colour = GOLD[3]
            put(frame, x, y, colour)
        put(frame, 10, y, GOLD[3] if not end else OUTLINE)
    for x in (10, 11, 12):
        put(frame, x, 0, GOLD[3])
        put(frame, x, BAR_H - 1, GOLD[3])
    put(frame, 10, 0, (0, 0, 0), 0)
    put(frame, 10, BAR_H - 1, (0, 0, 0), 0)
    # Grip: leather wrapped every second column.
    for x in range(4, 10):
        for dy in (-1, 0, 1):
            wrap = (x + dy) % 2 == 0
            colour = LEATHER[2] if wrap else (LEATHER[0] if dy < 0 else LEATHER[1])
            put(frame, x, mid + dy, colour)
        put(frame, x, mid - 2, LEATHER[3])
        put(frame, x, mid + 2, LEATHER[3])
    # Pommel: a small gold ball.
    pommel = {(1, -2): 3, (2, -2): 3, (0, -1): 3, (1, -1): 0, (2, -1): 0, (3, -1): 1, (0, 0): 3, (1, 0): 0, (2, 0): 1,
              (3, 0): 1, (0, 1): 3, (1, 1): 1, (2, 1): 2, (3, 1): 2, (1, 2): 3, (2, 2): 3}
    for (x, dy), tone in pommel.items():
        put(frame, x, mid + dy, GOLD[tone])
    put(frame, 3, mid - 2, GOLD[3])
    put(frame, 3, mid + 2, GOLD[3])

    for index, name in enumerate(("gold", "red", "white")):
        fill = atlas[16 * (index + 1):16 * (index + 1) + BAR_H, 0:BAR_W]
        for x in range(BLADE_X0, BAR_W - 1):
            half = blade_half_height(x)
            for r in range(-2, 3):
                if abs(r) <= half + 0.01:
                    put(fill, x, mid + r, FILL_ROWS[name][r + 2])
    image = Image.fromarray(atlas, "RGBA")
    save_png(image, "textures/gui/stamina_bar.png")
    return image


# --------------------------------------------------------------------------- helpers and main

def save_png(image, relative):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def main():
    logo()
    bar = stamina_bar()
    if len(sys.argv) > 1:
        # The bar at four times its size: empty, half full and with a spent part.
        preview = Image.new("RGBA", (BAR_W * 4 + 20, BAR_H * 4 * 3 + 40), (110, 120, 110, 255))
        frame = bar.crop((0, 0, BAR_W, BAR_H))
        for row, (share, ghost) in enumerate(((0.0, 0.0), (0.55, 0.0), (0.2, 0.45))):
            composed = frame.copy()
            if ghost:
                composed.alpha_composite(bar.crop((0, 48, BLADE_X0 + int(ghost * (BAR_W - BLADE_X0)), 48 + BAR_H)))
            fill_row = 16 if share >= 0.3 or share == 0 else 32
            if share:
                composed.alpha_composite(bar.crop((0, fill_row, BLADE_X0 + int(share * (BAR_W - BLADE_X0)), fill_row + BAR_H)))
            preview.alpha_composite(composed.resize((BAR_W * 4, BAR_H * 4), Image.NEAREST), (10, 10 + row * (BAR_H * 4 + 10)))
        preview.save(sys.argv[1])


if __name__ == "__main__":
    main()
