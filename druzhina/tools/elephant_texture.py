#!/usr/bin/env python3
"""Paints the war elephant's texture (256x128) for client/ElephantModel.java.

Every box of the model has its place on the texture (Minecraft's box layout: the top and the bottom above, then the
four sides in a strip: right, front, left, back). The places here must match the texOffs of the model.

    python3 tools/elephant_texture.py src/main/resources/assets/druzhina/textures/entity/war_elephant.png
"""
import math
import random
import sys

from PIL import Image

W, H = 256, 128
rnd = random.Random(1240)
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def clamp(v):
    return max(0, min(255, int(round(v))))


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = (clamp(c[0]), clamp(c[1]), clamp(c[2]), 255)


def shade(c, f):
    return tuple(v * f for v in c)


def noisy(c, n):
    d = rnd.uniform(-n, n)
    return (c[0] + d, c[1] + d * 0.95, c[2] + d * 0.9)


# ------------------------------------------------------------------ materials: paint(x0, y0, w, h, face)

SKIN = (122, 116, 112)


def skin(x0, y0, w, h, face, base=SKIN, wrinkle=3, dark_bottom=True):
    for y in range(h):
        # wrinkles: darker lines across, a little wavy
        for x in range(w):
            c = noisy(base, 7)
            t = y / max(1, h - 1)
            if dark_bottom and face in ("side", "front", "back"):
                c = shade(c, 1.04 - 0.14 * t)
            if face == "bottom":
                c = shade(c, 0.72)
            if face == "top":
                c = shade(c, 1.06)
            if wrinkle and face != "top" and (y + (1 if (x // 3) % 2 else 0)) % wrinkle == 0 and rnd.random() < 0.7:
                c = shade(c, 0.86)
            put(x0 + x, y0 + y, c)


def cloth(x0, y0, w, h, face, base=(152, 26, 32), gold=(222, 178, 62), border_bottom=True, lattice=True):
    for y in range(h):
        for x in range(w):
            c = noisy(base, 6)
            # a lattice of gold-stitched diamonds
            if lattice and face in ("side", "front", "back") and ((x + y) % 6 == 0 or (x - y) % 6 == 0):
                c = shade(base, 0.78)
            if lattice and face in ("side", "front", "back") and (x % 6 == 3 and y % 6 == 0):
                c = noisy(gold, 10)
            put(x0 + x, y0 + y, c)
    if face in ("side", "front", "back"):
        # a gold border along the hem and a thin one at the top
        for x in range(w):
            if border_bottom:
                for y in (h - 1, h - 2):
                    put(x0 + x, y0 + y, noisy(gold if y == h - 2 else shade(gold, 0.7), 8))
                put(x0 + x, y0 + h - 3, shade(base, 0.6))
                if x % 3 == 1:
                    put(x0 + x, y0 + h - 1, (240, 214, 120))
            put(x0 + x, y0, noisy(gold, 8))
        for y in range(h):
            put(x0, y0 + y, noisy(shade(gold, 0.85), 6))
            put(x0 + w - 1, y0 + y, noisy(shade(gold, 0.85), 6))
    elif face == "top":
        for x in range(w):
            put(x0 + x, y0, noisy(gold, 8))
            put(x0 + x, y0 + h - 1, noisy(gold, 8))


def wood(x0, y0, w, h, face, base=(112, 74, 40), planks_vertical=False):
    for y in range(h):
        for x in range(w):
            along = y if planks_vertical else x
            across = x if planks_vertical else y
            grain = math.sin(along * 0.9 + across * 2.3) * 6
            c = noisy((base[0] + grain, base[1] + grain * 0.7, base[2] + grain * 0.4), 5)
            if across % 4 == 3:
                c = shade(c, 0.72)
            put(x0 + x, y0 + y, c)


def gilded(x0, y0, w, h, face):
    for y in range(h):
        for x in range(w):
            c = noisy((196, 150, 52), 12)
            if (x + y) % 5 == 0:
                c = (236, 200, 96)
            put(x0 + x, y0 + y, c)


def steel(x0, y0, w, h, face):
    for y in range(h):
        for x in range(w):
            c = noisy((168, 170, 176), 6)
            c = shade(c, 1.08 - 0.2 * y / max(1, h - 1))
            put(x0 + x, y0 + y, c)
    if face == "front":
        # a ridge down the middle, a gold edge, rivets
        for y in range(h):
            put(x0 + w // 2, y0 + y, (214, 216, 222))
            put(x0 + w // 2 - 1, y0 + y, (130, 132, 138))
        for x in range(w):
            put(x0 + x, y0, (210, 168, 64))
            put(x0 + x, y0 + h - 1, (170, 128, 44))
        for y in range(h):
            put(x0, y0 + y, (190, 148, 54))
            put(x0 + w - 1, y0 + y, (170, 128, 44))
        for (rx, ry) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2), (2, h // 2), (w - 3, h // 2)):
            put(x0 + rx, y0 + ry, (236, 206, 110))
        # a red jewel on the brow
        put(x0 + w // 2, y0 + 3, (200, 30, 40))
        put(x0 + w // 2 - 1, y0 + 3, (150, 20, 30))


def ivory(x0, y0, w, h, face):
    for y in range(h):
        for x in range(w):
            c = noisy((234, 226, 204), 5)
            if face == "bottom":
                c = shade(c, 0.85)
            put(x0 + x, y0 + y, c)


def hair(x0, y0, w, h, face):
    for y in range(h):
        for x in range(w):
            put(x0 + x, y0 + y, noisy((46, 40, 36), 10))


def box(u, v, w, h, d, paint, **kw):
    """Paints the six faces of a box of w x h x d at texture place (u, v)."""
    paint(u + d, v, w, d, "top", **kw)
    paint(u + d + w, v, w, d, "bottom", **kw)
    paint(u, v + d, d, h, "side", **kw)
    paint(u + d, v + d, w, h, "front", **kw)
    paint(u + d + w, v + d, d, h, "side", **kw)
    paint(u + d + w + d, v + d, w, h, "back", **kw)


# ------------------------------------------------------------------ the boxes

# body 20x18x30
box(0, 0, 20, 18, 30, skin)
# caparison 20x11x22
box(0, 48, 20, 11, 22, cloth)
# head 13x14x9, brow dome 11x4x7
box(100, 0, 13, 14, 9, skin, wrinkle=4, dark_bottom=False)
box(144, 0, 11, 4, 7, skin, wrinkle=0, dark_bottom=False)
# the eyes: on both sides of the head, near the front edge
for (ex, ey) in ((100 + 9 - 3, 9 + 4), (100 + 9 + 13 + 1, 9 + 4)):
    put(ex, ey, (24, 18, 14))
    put(ex + 1, ey, (24, 18, 14))
    put(ex, ey - 1, (88, 80, 76))
    put(ex + 1, ey - 1, (88, 80, 76))
    put(ex, ey + 1, (240, 230, 210) if False else (96, 90, 86))
# the mouth under the trunk, on the front of the head
for x in range(100 + 9 + 3, 100 + 9 + 10):
    put(x, 9 + 13, (86, 64, 62))
# ears 1x12x10: grey with pink mottling at the edge
box(100, 23, 1, 12, 10, skin, wrinkle=0, dark_bottom=False)
for y in range(12):
    for x in range(10):
        if (x > 6 or y > 8) and rnd.random() < 0.5:
            put(100 + x, 23 + 10 + y, (176, 140, 132))
            put(100 + 10 + 1 + x, 23 + 10 + y, (176, 140, 132))
# trunk 5x8x5, 4x7x4, 3x6x3: rings
box(180, 0, 5, 8, 5, skin, wrinkle=2, dark_bottom=False)
box(200, 0, 4, 7, 4, skin, wrinkle=2, dark_bottom=False)
box(216, 0, 3, 6, 3, skin, wrinkle=2, dark_bottom=False)
# the tip of the trunk, darker
for x in range(216, 228):
    put(x, 3 + 5, (92, 84, 80))
# tusks 2x2x6
box(228, 0, 2, 2, 6, ivory)
box(228, 8, 2, 2, 6, ivory)
# tail 1x12x1 and its tuft 2x3x2
box(244, 0, 1, 12, 1, skin, wrinkle=0)
box(244, 16, 2, 3, 2, hair)
# legs 7x14x7: wrinkled, with toenails on the front
box(124, 23, 7, 14, 7, skin, wrinkle=2)
for x in range(7):
    put(124 + 7 + x, 23 + 7 + 13, (70, 64, 60))
for nx in (124 + 7 + 1, 124 + 7 + 3, 124 + 7 + 5):
    put(nx, 23 + 7 + 13, (214, 204, 182))
    put(nx, 23 + 7 + 12, (196, 186, 166))
# the soles
for y in range(7):
    for x in range(7):
        put(124 + 7 + 7 + x, 23 + y, (66, 60, 56))
# brow plate 10x10x1
box(152, 23, 10, 10, 1, steel)
# howdah: floor 18x2x16, front/back wall 18x6x1, side walls 1x6x14, posts 1x12x1
box(84, 48, 18, 2, 16, wood)
box(152, 48, 18, 6, 1, cloth, border_bottom=False, base=(140, 22, 30))
box(190, 48, 1, 6, 14, cloth, border_bottom=False, base=(140, 22, 30))
# wooden rails on the walls: the top row of the wall sides
for x in range(152, 190):
    put(x, 48 + 1, (120, 80, 42))
    put(x, 48 + 6, (96, 62, 32))
for x in range(190, 220):
    put(x, 48 + 14, (120, 80, 42))
    put(x, 48 + 19, (96, 62, 32))
box(220, 48, 1, 12, 1, gilded)
# canopy: roof 20x1x18 and two steps of a peak
box(0, 81, 20, 1, 18, cloth, lattice=False)
box(76, 81, 14, 2, 12, cloth)
box(128, 81, 8, 2, 6, gilded)

out = sys.argv[1] if len(sys.argv) > 1 else "war_elephant.png"
img.save(out)
print("wrote", out)
