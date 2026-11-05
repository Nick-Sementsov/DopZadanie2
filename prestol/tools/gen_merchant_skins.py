"""Draws the skins of Prestol's merchants: 64x64 player-model skins of medieval traders.

  python3 -I tools/gen_merchant_skins.py

Writes src/main/resources/assets/prestol/textures/entity/merchant_<n>.png. Each merchant wears a long fur-trimmed
coat with a belt and a purse, hose and boots, and a chaperon (a hood worn as a hat); the variants differ in cloth,
hair and beard. Needs Pillow.
"""
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources' / 'assets' / 'prestol' / 'textures' / 'entity'

# coat, coat dark, hat, hat dark, hose, hair, skin, eyes
VARIANTS = [
    ((128, 32, 40), (88, 20, 28), (40, 52, 96), (26, 34, 66), (52, 44, 40), (92, 58, 30), (214, 165, 128), (60, 80, 120)),
    ((36, 64, 120), (24, 42, 84), (120, 30, 36), (82, 20, 24), (70, 54, 40), (40, 28, 20), (196, 142, 104), (70, 50, 30)),
    ((46, 92, 54), (30, 62, 36), (96, 60, 30), (66, 40, 20), (60, 50, 44), (150, 104, 56), (226, 180, 146), (50, 90, 60)),
    ((112, 84, 44), (78, 58, 30), (34, 34, 38), (20, 20, 24), (40, 40, 46), (170, 168, 162), (205, 152, 116), (60, 60, 70)),
]
FUR = ((196, 170, 132), (160, 132, 96))
BOOT = ((86, 56, 34), (60, 38, 22))
BELT = ((66, 42, 26), (212, 172, 72))
PURSE = ((128, 92, 52), (96, 66, 36))
SHIRT = (226, 218, 196)


class Skin:
    def __init__(self, seed):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rng = random.Random(seed)

    def shade(self, c, spread=10):
        d = self.rng.randint(-spread, spread)
        return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)

    def fill(self, x0, y0, x1, y1, c, spread=10):
        for x in range(x0, x1):
            for y in range(y0, y1):
                self.px[x, y] = self.shade(c, spread)

    def dot(self, x, y, c):
        self.px[x, y] = tuple(c[:3]) + (255,)

    def hline(self, x0, x1, y, c, spread=6):
        for x in range(x0, x1):
            self.px[x, y] = self.shade(c, spread)

    def save(self, path):
        self.img.save(path)


def box(s, x, y, w, h, d, faces):
    """Fills the six faces of a model box laid out at (x, y) as in a player skin: faces(name) -> (rect fill fn)."""
    rects = {
        'top': (x + d, y, x + d + w, y + d),
        'bottom': (x + d + w, y, x + d + 2 * w, y + d),
        'right': (x, y + d, x + d, y + d + h),
        'front': (x + d, y + d, x + d + w, y + d + h),
        'left': (x + d + w, y + d, x + 2 * d + w, y + d + h),
        'back': (x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h),
    }
    for name, r in rects.items():
        faces(name, r)


def paint(n, colors):
    coat, coat_dark, hat, hat_dark, hose, hair, skin, eyes = colors
    s = Skin(1000 + n)

    # ---- head: face, hair, beard
    def head(name, r):
        x0, y0, x1, y1 = r
        if name == 'top':
            s.fill(x0, y0, x1, y1, hair)
        elif name == 'bottom':
            s.fill(x0, y0, x1, y1, skin, 6)
        elif name == 'front':
            s.fill(x0, y0, x1, y1, skin, 6)
            s.hline(x0, x1, y0, hair)
            s.hline(x0, x1, y0 + 1, hair)
            s.dot(x0, y0 + 2, hair)
            s.dot(x1 - 1, y0 + 2, hair)
            # eyes and brows
            for ex in (x0 + 1, x0 + 5):
                s.dot(ex, y0 + 3, tuple(max(0, v - 50) for v in hair))
                s.dot(ex + 1, y0 + 3, tuple(max(0, v - 50) for v in hair))
                s.dot(ex, y0 + 4, (240, 240, 240))
                s.dot(ex + 1, y0 + 4, eyes)
            s.dot(x0 + 3, y0 + 5, tuple(max(0, v - 30) for v in skin))
            s.dot(x0 + 4, y0 + 5, tuple(max(0, v - 30) for v in skin))
            # beard and moustache
            for bx in range(x0, x1):
                s.dot(bx, y0 + 7, hair)
            for bx in range(x0 + 1, x1 - 1):
                s.dot(bx, y0 + 6, hair)
            s.dot(x0 + 3, y0 + 6, (120, 70, 60))
            s.dot(x0 + 4, y0 + 6, (120, 70, 60))
            s.dot(x0, y0 + 6, hair)
            s.dot(x1 - 1, y0 + 6, hair)
        else:
            s.fill(x0, y0, x1, y1, hair)
            if name in ('right', 'left'):
                s.fill(x0 + (2 if name == 'right' else 0), y0 + 3, x1 - (0 if name == 'right' else 2), y1 - 2, skin, 6)
                s.fill(x0, y1 - 2, x1, y1, hair)
    box(s, 0, 0, 8, 8, 8, head)

    # ---- chaperon (hat layer): a padded roll round the head with the cloth falling to one side
    def chaperon(name, r):
        x0, y0, x1, y1 = r
        if name == 'top':
            s.fill(x0, y0, x1, y1, hat)
            for i in range(x0, x1):
                s.dot(i, y0 + (i % 3 == 0), hat_dark)
        elif name == 'bottom':
            return
        else:
            s.fill(x0, y0, x1, y0 + 3, hat)
            s.hline(x0, x1, y0 + 2, hat_dark)
            if name == 'back':
                s.fill(x0, y0 + 3, x1, y1, hat)
                for yy in range(y0 + 3, y1):
                    s.dot(x0 + (yy % 2), yy, hat_dark)
            if name == 'left':
                s.fill(x0 + 2, y0 + 3, x1, y1 - 1, hat)
    box(s, 32, 0, 8, 8, 8, chaperon)

    # ---- body: coat over a shirt, fur collar, belt with buckle and purse
    def body(name, r, overlay=False):
        x0, y0, x1, y1 = r
        if name in ('top', 'bottom'):
            if not overlay:
                s.fill(x0, y0, x1, y1, coat if name == 'top' else coat_dark)
            else:
                s.fill(x0, y0, x1, y1, FUR[0], 12)
            return
        if not overlay:
            s.fill(x0, y0, x1, y1, coat)
            s.hline(x0, x1, y0 + 7, BELT[0])
            if name == 'front':
                s.fill(x0 + 3, y0, x0 + 5, y0 + 3, SHIRT, 4)
                s.dot(x0 + 3, y0 + 7, BELT[1])
                s.dot(x0 + 4, y0 + 7, BELT[1])
                for yy in range(y0 + 3, y1):
                    s.dot(x0 + 4, yy, coat_dark)
                # purse on the right hip
                s.fill(x0 + 1, y0 + 8, x0 + 3, y0 + 10, PURSE[0], 6)
                s.dot(x0 + 1, y0 + 10, PURSE[1])
                s.dot(x0 + 2, y0 + 10, PURSE[1])
            if name == 'back':
                for yy in range(y0 + 8, y1):
                    s.dot(x0 + 3, yy, coat_dark)
                    s.dot(x0 + 4, yy, coat_dark)
        else:
            # fur collar on the overlay, the rest left clear
            s.hline(x0, x1, y0, FUR[0], 14)
            if name == 'front':
                s.dot(x0, y0 + 1, FUR[1])
                s.dot(x1 - 1, y0 + 1, FUR[1])
                for yy in range(y0 + 1, y1):
                    s.dot(x0 + 3, yy, FUR[0])
                    s.dot(x0 + 4, yy, FUR[1])
                s.hline(x0, x1, y1 - 1, FUR[1], 10)
            elif name == 'back':
                s.hline(x0, x1, y1 - 1, FUR[1], 10)
            else:
                s.dot(x0, y1 - 1, FUR[1])
                s.dot(x1 - 1, y1 - 1, FUR[1])
    box(s, 16, 16, 8, 12, 4, body)
    box(s, 16, 32, 8, 12, 4, lambda nm, r: body(nm, r, True))

    # ---- arms: sleeves with fur cuffs, hands
    def arm(name, r, overlay=False):
        x0, y0, x1, y1 = r
        if overlay:
            if name not in ('top', 'bottom'):
                s.hline(x0, x1, y1 - 4, FUR[0], 12)
                s.hline(x0, x1, y1 - 5, FUR[1], 10)
            return
        if name == 'top':
            s.fill(x0, y0, x1, y1, coat)
        elif name == 'bottom':
            s.fill(x0, y0, x1, y1, skin, 6)
        else:
            s.fill(x0, y0, x1, y1 - 3, coat)
            s.fill(x0, y1 - 3, x1, y1, skin, 6)
            if name == 'front':
                s.hline(x0, x1, y0 + 4, coat_dark)
    box(s, 40, 16, 4, 12, 4, arm)
    box(s, 32, 48, 4, 12, 4, arm)
    box(s, 40, 32, 4, 12, 4, lambda nm, r: arm(nm, r, True))
    box(s, 48, 48, 4, 12, 4, lambda nm, r: arm(nm, r, True))

    # ---- legs: hose and boots; the coat's skirt hangs over the thighs on the overlay
    def leg(name, r, overlay=False):
        x0, y0, x1, y1 = r
        if overlay:
            if name not in ('top', 'bottom'):
                s.fill(x0, y0, x1, y0 + 5, coat)
                s.hline(x0, x1, y0 + 5, FUR[1], 10)
            return
        if name == 'top':
            s.fill(x0, y0, x1, y1, hose)
        elif name == 'bottom':
            s.fill(x0, y0, x1, y1, BOOT[1])
        else:
            s.fill(x0, y0, x1, y1 - 5, hose)
            s.fill(x0, y1 - 5, x1, y1, BOOT[0])
            s.hline(x0, x1, y1 - 5, BOOT[1])
            s.hline(x0, x1, y1 - 1, BOOT[1])
    box(s, 0, 16, 4, 12, 4, leg)
    box(s, 16, 48, 4, 12, 4, leg)
    box(s, 0, 32, 4, 12, 4, lambda nm, r: leg(nm, r, True))
    box(s, 0, 48, 4, 12, 4, lambda nm, r: leg(nm, r, True))
    return s


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for n, colors in enumerate(VARIANTS):
        paint(n, colors).save(OUT / f'merchant_{n}.png')
    print('wrote', len(VARIANTS), 'skins to', OUT)


if __name__ == '__main__':
    main()
