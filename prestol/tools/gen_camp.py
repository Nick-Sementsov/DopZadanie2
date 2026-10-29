"""Draws the war tent of a siege camp and writes its block model.

  python3 -I tools/gen_camp.py

Writes textures/block/camp_canvas.png (striped canvas), camp_pennant.png and models/block/siege_camp.json: an A-frame
tent on poles with a pennant above the ridge and a straw bed inside. Needs Pillow.
"""
import json
import random
from pathlib import Path

from PIL import Image

RES = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources' / 'assets' / 'prestol'
RED = (150, 36, 36)
RED_DARK = (112, 24, 26)
CREAM = (222, 206, 168)
CREAM_DARK = (190, 172, 132)
GOLD = (214, 172, 64)


def canvas():
    rng = random.Random(7)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for x in range(16):
        stripe = (x // 4) % 2 == 0
        for y in range(16):
            base = RED if stripe else CREAM
            if y >= 14:
                base = RED_DARK if stripe else CREAM_DARK
            d = rng.randint(-8, 8)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in base) + (255,)
    # seams between the stripes
    for x in (3, 7, 11, 15):
        for y in range(0, 14, 2):
            r, g, b, a = px[x, y]
            px[x, y] = (max(0, r - 30), max(0, g - 30), max(0, b - 30), a)
    return img


def pennant():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = RED + (255,)
    for y in range(16):
        px[0, y] = GOLD + (255,)
        px[1, y] = GOLD + (255,)
    for x in range(16):
        px[x, 7] = GOLD + (255,)
        px[x, 8] = GOLD + (255,)
    return img


def face(tex, uv):
    return {'texture': tex, 'uv': uv}


def box(frm, to, faces, rotation=None):
    e = {'from': frm, 'to': to, 'faces': faces}
    if rotation:
        e['rotation'] = rotation
    return e


def model():
    canvas_faces = {
        'west': face('#canvas', [1, 0, 15, 16]),
        'east': face('#canvas', [1, 0, 15, 16]),
        'north': face('#canvas', [0, 0, 1, 16]),
        'south': face('#canvas', [0, 0, 1, 16]),
        'up': face('#canvas', [0, 0, 1, 14]),
        'down': face('#canvas', [0, 0, 1, 14]),
    }
    pole = {d: face('#pole', [6, 0, 8, 16]) for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    flag = {d: face('#pennant', [0, 4, 16, 12]) for d in ('north', 'south')}
    flag.update({d: face('#pennant', [0, 0, 1, 4]) for d in ('east', 'west', 'up', 'down')})
    bed = {d: face('#floor', [3, 3, 13, 13]) for d in ('up', 'down', 'north', 'south', 'east', 'west')}
    elements = [
        # the two slopes of the canvas, leaning on each other over the ridge
        box([1, 0, 1], [1.5, 17, 15], canvas_faces, {'origin': [1, 0, 8], 'axis': 'z', 'angle': -22.5}),
        box([14.5, 0, 1], [15, 17, 15], canvas_faces, {'origin': [15, 0, 8], 'axis': 'z', 'angle': 22.5}),
        # front and back poles and the ridge pole
        box([7.5, 0, 0.5], [8.5, 16, 1.5], pole),
        box([7.5, 0, 14.5], [8.5, 16, 15.5], pole),
        box([7.5, 15, 0.5], [8.5, 16.5, 15.5], pole),
        # the pennant over the ridge
        box([7.75, 16.5, 7.5], [8.25, 25, 8], pole),
        box([8.25, 20.5, 7.7], [13.5, 24.5, 7.8], flag),
        # a straw bed inside
        box([4, 0, 3], [12, 0.5, 13], bed),
    ]
    return {
        'parent': 'minecraft:block/block',
        'textures': {
            'particle': 'prestol:block/camp_canvas',
            'canvas': 'prestol:block/camp_canvas',
            'pole': 'minecraft:block/stripped_spruce_log',
            'pennant': 'prestol:block/camp_pennant',
            'floor': 'minecraft:block/hay_block_top',
        },
        'elements': elements,
    }


def main():
    tex = RES / 'textures' / 'block'
    tex.mkdir(parents=True, exist_ok=True)
    canvas().save(tex / 'camp_canvas.png')
    pennant().save(tex / 'camp_pennant.png')
    (RES / 'models' / 'block' / 'siege_camp.json').write_text(json.dumps(model(), indent=2) + '\n')
    print('wrote the war tent')


if __name__ == '__main__':
    main()
