#!/usr/bin/env python3
"""Paints the texture of the siege engines (256x256) and the icons of their items (16x16).

The boxes of the models (client/siege/*Model.java) take their faces from the corner of the field of their material;
the fields here must match client/siege/SiegeTexture.java.

    python3 tools/siege_texture.py src/main/resources/assets/druzhina/textures
"""
import math
import os
import random
import sys

from PIL import Image

W, H = 256, 256
rnd = random.Random(1453)


def clamp(v):
    return max(0, min(255, int(round(v))))


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w, self.h = w, h

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (clamp(c[0]), clamp(c[1]), clamp(c[2]), a)

    def get(self, x, y):
        return self.px[x, y]


def shade(c, f):
    return tuple(v * f for v in c)


def noisy(c, n):
    d = rnd.uniform(-n, n)
    return (c[0] + d, c[1] + d * 0.95, c[2] + d * 0.85)


# ------------------------------------------------------------------ materials


def wood(cv, x0, y0, w, h, base, vertical=False, seam=8):
    """Wood with its grain along x (or along y when vertical): streaks, a seam between planks now and then, knots."""
    long_, across = (h, w) if vertical else (w, h)
    phase = [rnd.uniform(0, 6.28) for _ in range(across)]
    tone = [rnd.uniform(-10, 10) for _ in range(across)]
    for a in range(across):
        for l in range(long_):
            c = noisy(base, 4)
            c = tuple(v + tone[a] for v in c)
            # streaks along the grain
            g = math.sin(l * 0.11 + phase[a] + a * 0.7) * 0.5 + 0.5
            c = shade(c, 0.9 + 0.12 * g)
            if a % seam == seam - 1:
                c = shade(c, 0.68)
            if a % seam == 0:
                c = shade(c, 1.08)
            x, y = (x0 + a, y0 + l) if vertical else (x0 + l, y0 + a)
            cv.put(x, y, c)
    # knots
    for _ in range(max(1, w * h // 700)):
        kx, ky = x0 + rnd.randrange(w), y0 + rnd.randrange(h)
        for dx in range(-2, 3):
            for dy in range(-1, 2):
                if dx * dx / 4 + dy * dy <= 1.2:
                    cv.put(kx + dx, ky + dy, shade(base, 0.62 if dx * dx + dy * dy < 2 else 0.78))


def iron(cv, x0, y0, w, h):
    base = (104, 104, 110)
    for y in range(h):
        for x in range(w):
            c = noisy(base, 9)
            if (x + y * 3) % 11 == 0:
                c = shade(c, 1.15)
            cv.put(x0 + x, y0 + y, c)
    # rivets
    for y in range(1, h, 4):
        for x in range(1, w, 4):
            cv.put(x0 + x, y0 + y, (150, 150, 156))
            cv.put(x0 + x + 1, y0 + y + 1, (60, 60, 66))


def rope(cv, x0, y0, w, h):
    base = (178, 152, 102)
    for y in range(h):
        for x in range(w):
            c = noisy(base, 8)
            if (x + y) % 3 == 0:
                c = shade(c, 0.78)
            cv.put(x0 + x, y0 + y, c)


def hide(cv, x0, y0, w, h):
    """Hides sewn together over the roof: brown, patchy, with seams."""
    base = (116, 80, 50)
    patches = [(x0 + rnd.randrange(w), y0 + rnd.randrange(h), rnd.uniform(6, 16), rnd.uniform(-18, 14)) for _ in range(26)]
    for y in range(h):
        for x in range(w):
            c = noisy(base, 6)
            for px, py, r, d in patches:
                if (x0 + x - px) ** 2 + (y0 + y - py) ** 2 < r * r:
                    c = tuple(v + d for v in c)
                    break
            # seams of the hides
            if x % 21 == 0 or y % 17 == 0:
                c = shade(c, 0.66)
                if (x + y) % 3 == 0:
                    c = (190, 170, 130)
            cv.put(x0 + x, y0 + y, c)


def wheel_face(cv, x0, y0, size):
    """A spoked wheel seen from the side: a rim, spokes, a hub; the corners stay clear."""
    r = size / 2
    rim = (88, 60, 36)
    spoke = (128, 92, 56)
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - r, y + 0.5 - r
            d = math.hypot(dx, dy)
            if d > r:
                continue
            ang = math.atan2(dy, dx)
            if d > r - 1.6:
                c = noisy(rim, 6)
                if d > r - 0.7:
                    c = (70, 70, 76)  # the iron tyre
            elif d < 1.6:
                c = (80, 80, 86)
            elif min(abs(math.sin(ang * 3)), 1) < 0.28:
                c = noisy(spoke, 6)
            else:
                continue
            cv.put(x0 + x, y0 + y, c)


def wheel_box(cv, u, v, w, size):
    """The faces of a wheel box (w wide, size high and deep), laid out as Minecraft lays out a box."""
    d = size
    h = size
    # the two sides: discs
    wheel_face(cv, u, v + d, size)
    wheel_face(cv, u + d + w, v + d, size)
    # the tread: the top, the bottom, the front and the back, only where the round rim touches them
    for (fx, fy, fw, fh, along_y) in ((u + d, v, w, d, True), (u + d + w, v, w, d, True), (u + d, v + d, w, h, True),
                                      (u + 2 * d + w, v + d, w, h, True)):
        for yy in range(fh):
            if abs(yy + 0.5 - fh / 2) <= fh * 0.22:
                for xx in range(fw):
                    cv.put(fx + xx, fy + yy, (70, 70, 76))


def stone(cv, x0, y0, w, h):
    for y in range(h):
        for x in range(w):
            g = rnd.uniform(96, 150)
            if rnd.random() < 0.18:
                g *= 0.7
            cv.put(x0 + x, y0 + y, (g, g, g * 1.02))


def weight_box(cv, u, v):
    """The counterweight: a box of planks with iron bands, stones heaped in its open top."""
    bw, bh, bd = 12, 11, 10
    # top: stones
    stone(cv, u + bd, v, bw, bd)
    # bottom
    wood(cv, u + bd + bw, v, bw, bd, (92, 64, 40))
    # the four sides: planks with iron bands
    x = u
    for side_w in (bd, bw, bd, bw):
        wood(cv, x, v + bd, side_w, bh, (110, 76, 46), seam=4)
        for yy in (1, bh - 2):
            for xx in range(side_w):
                cv.put(x + xx, v + bd + yy, noisy((90, 90, 96), 6))
        x += side_w


# ------------------------------------------------------------------ the texture


def texture(path):
    cv = Canvas(W, H)
    wood(cv, 0, 0, 256, 64, (150, 108, 64))
    wood(cv, 0, 64, 96, 96, (138, 98, 58), vertical=True, seam=6)
    wood(cv, 96, 64, 96, 64, (88, 62, 40), seam=5)
    iron(cv, 192, 64, 64, 48)
    rope(cv, 192, 112, 64, 48)
    hide(cv, 0, 160, 128, 96)
    wheel_box(cv, 128, 160, 2, 10)
    wheel_box(cv, 128, 192, 2, 8)
    stone(cv, 192, 160, 32, 16)
    weight_box(cv, 128, 224)
    cv.img.save(path)


# ------------------------------------------------------------------ the icons

WOOD_C = (150, 108, 64)
DARK_C = (88, 62, 40)
IRON_C = (110, 110, 118)
ROPE_C = (190, 164, 110)
HIDE_C = (120, 82, 52)


def line(cv, x0, y0, x1, y1, c, wide=1):
    n = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
    for i in range(int(n) + 1):
        t = i / n
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        for k in range(wide):
            cv.put(int(round(x)) + (k if abs(x1 - x0) < abs(y1 - y0) else 0),
                   int(round(y)) + (0 if abs(x1 - x0) < abs(y1 - y0) else k), c)


def outline(cv):
    """A dark edge around the drawing, as Minecraft's items have."""
    src = [[cv.get(x, y) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if src[y][x][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src[ny][nx][3] > 0:
                        cv.put(x, y, (34, 24, 16))
                        break


def wheel_icon(cv, cx, cy):
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1), (2, 0), (0, 2), (-1, 1), (1, -1), (1, 2), (2, 1)):
        cv.put(cx + dx, cy + dy, (70, 52, 34))
    cv.put(cx, cy, IRON_C)


def icon_trebuchet(path):
    cv = Canvas(16, 16)
    line(cv, 1, 13, 14, 13, WOOD_C)
    line(cv, 3, 13, 7, 5, DARK_C)
    line(cv, 11, 13, 7, 5, DARK_C)
    # the arm, thrown up, with the sling and the weight
    line(cv, 2, 2, 12, 8, WOOD_C)
    line(cv, 2, 2, 1, 5, ROPE_C)
    cv.put(1, 6, (130, 130, 136))
    for x in range(11, 15):
        for y in range(8, 11):
            cv.put(x, y, noisy((96, 96, 104), 8))
    cv.put(7, 5, IRON_C)
    wheel_icon(cv, 3, 13)
    wheel_icon(cv, 11, 13)
    outline(cv)
    cv.img.save(path)


def icon_ballista(path):
    cv = Canvas(16, 16)
    line(cv, 2, 12, 13, 12, WOOD_C)
    line(cv, 7, 12, 7, 9, DARK_C)
    # the stock, the arms and the string
    line(cv, 2, 9, 13, 6, WOOD_C)
    line(cv, 10, 7, 12, 2, DARK_C)
    line(cv, 10, 7, 14, 10, DARK_C)
    line(cv, 12, 2, 5, 8, ROPE_C)
    line(cv, 14, 10, 5, 8, ROPE_C)
    line(cv, 4, 8, 15, 5, (70, 50, 32))
    cv.put(15, 5, IRON_C)
    wheel_icon(cv, 3, 12)
    wheel_icon(cv, 11, 12)
    outline(cv)
    cv.img.save(path)


def icon_ram(path):
    cv = Canvas(16, 16)
    # the roof of hides
    for y in range(3, 8):
        half = (y - 3) * 2 + 1
        for x in range(8 - half, 8 + half):
            if 0 <= x < 16:
                cv.put(x, y, noisy(HIDE_C, 10))
    line(cv, 1, 8, 14, 8, WOOD_C)
    line(cv, 2, 8, 2, 12, DARK_C)
    line(cv, 13, 8, 13, 12, DARK_C)
    # the log with its iron head
    line(cv, 3, 10, 13, 10, (122, 88, 52), 2)
    for y in range(9, 13):
        cv.put(0, y, IRON_C)
        cv.put(1, y, IRON_C)
    line(cv, 1, 13, 14, 13, WOOD_C)
    wheel_icon(cv, 3, 13)
    wheel_icon(cv, 11, 13)
    outline(cv)
    cv.img.save(path)


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/druzhina/textures"
    os.makedirs(os.path.join(root, "entity"), exist_ok=True)
    os.makedirs(os.path.join(root, "item"), exist_ok=True)
    texture(os.path.join(root, "entity", "siege.png"))
    icon_trebuchet(os.path.join(root, "item", "trebuchet.png"))
    icon_ballista(os.path.join(root, "item", "ballista.png"))
    icon_ram(os.path.join(root, "item", "battering_ram.png"))
