#!/usr/bin/env python3
"""Paints the icons of the siege engines' items (16x16). The engines themselves are made by tools/models.py.

    python3 tools/siege_icons.py src/main/resources/assets/druzhina/textures
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


def noisy(c, n):
    d = rnd.uniform(-n, n)
    return (c[0] + d, c[1] + d * 0.95, c[2] + d * 0.85)


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


def icon_mangonel(path):
    cv = Canvas(16, 16)
    line(cv, 1, 12, 14, 12, WOOD_C)
    line(cv, 10, 12, 10, 4, DARK_C)
    line(cv, 8, 4, 13, 4, DARK_C)
    line(cv, 5, 11, 2, 3, WOOD_C)
    for x, y in ((1, 2), (2, 2), (1, 1), (2, 1)):
        cv.put(x, y, (146, 86, 56))
    cv.put(1, 0, (240, 170, 60))
    cv.put(2, 0, (250, 210, 90))
    line(cv, 4, 11, 7, 11, ROPE_C)
    wheel_icon(cv, 3, 13)
    wheel_icon(cv, 11, 13)
    outline(cv)
    cv.img.save(path)


def icon_bombard(path):
    cv = Canvas(16, 16)
    for x in range(1, 12):
        for y in range(5, 10):
            c = noisy((78, 80, 86), 8)
            if x in (2, 5, 8, 11):
                c = (110, 112, 118)
            cv.put(x, y - (x // 4), c)
    for y in range(3, 7):
        cv.put(0, y, (22, 20, 18))
    line(cv, 6, 11, 15, 14, WOOD_C)
    line(cv, 4, 10, 10, 10, DARK_C)
    wheel_icon(cv, 6, 12)
    outline(cv)
    cv.img.save(path)


def icon_tower(path):
    cv = Canvas(16, 16)
    for x in range(4, 12):
        for y in range(2, 14):
            c = noisy((124, 100, 72), 8)
            if x in (4, 11):
                c = (90, 70, 50)
            if y in (5, 9):
                c = (78, 62, 48)
            cv.put(x, y, c)
    for x in (4, 6, 8, 10):
        cv.put(x, 1, (124, 100, 72))
    for y in range(2, 7):
        cv.put(3, y, (140, 112, 80))
    wheel_icon(cv, 4, 13)
    wheel_icon(cv, 11, 13)
    outline(cv)
    cv.img.save(path)


def icon_mantlet(path):
    cv = Canvas(16, 16)
    for x in range(1, 15):
        for y in range(2, 12):
            c = noisy((116, 92, 66), 8)
            if x % 3 == 0:
                c = (88, 70, 52)
            if y in (3, 10):
                c = (78, 62, 48)
            cv.put(x, y, c)
    for x in (4, 10):
        for y in (5, 6):
            cv.put(x, y, (0, 0, 0), 0)
    wheel_icon(cv, 3, 13)
    wheel_icon(cv, 12, 13)
    outline(cv)
    cv.img.save(path)


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/druzhina/textures"
    os.makedirs(os.path.join(root, "entity"), exist_ok=True)
    os.makedirs(os.path.join(root, "item"), exist_ok=True)
    icon_trebuchet(os.path.join(root, "item", "trebuchet.png"))
    icon_ballista(os.path.join(root, "item", "ballista.png"))
    icon_ram(os.path.join(root, "item", "battering_ram.png"))
    icon_mangonel(os.path.join(root, "item", "mangonel.png"))
    icon_bombard(os.path.join(root, "item", "bombard.png"))
    icon_tower(os.path.join(root, "item", "siege_tower.png"))
    icon_mantlet(os.path.join(root, "item", "mantlet.png"))
