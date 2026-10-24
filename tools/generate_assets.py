#!/usr/bin/env python3
"""Generates the textures, models, blockstates, recipes and loot tables of the chess mod.

Run from the repository root:  python3 tools/generate_assets.py
Requires Pillow and NumPy (pip install pillow numpy). Files are written into src/main/resources
and overwrite earlier versions, so edit this script rather than the generated files.
"""

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

MODID = "chessmod"
ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / MODID
DATA = RESOURCES / "data" / MODID

PIECES = ["pawn", "knight", "bishop", "rook", "queen", "king"]
SIDES = ["white", "black"]
RNG = np.random.default_rng(20240613)


# --------------------------------------------------------------------------- helpers

def save_png(image, relative):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def value_noise(width, height, cell_x, cell_y, seed):
    """Smooth value noise in 0..1, interpolated from a random grid with the given cell size."""
    rng = np.random.default_rng(seed)
    gw = int(math.ceil(width / cell_x)) + 2
    gh = int(math.ceil(height / cell_y)) + 2
    grid = rng.random((gh, gw))
    ys = np.arange(height) / cell_y
    xs = np.arange(width) / cell_x
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = grid[y0][:, x0]
    b = grid[y0][:, x0 + 1]
    c = grid[y0 + 1][:, x0]
    d = grid[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx[None, :]
    bottom = c + (d - c) * fx[None, :]
    return top + (bottom - top) * fy[:, None]


def wood(width, height, base, seed, grain=0.10, streak=0.05, speckle=0.025, vertical=False):
    """Wood texture: long soft streaks along the grain plus fine speckles."""
    w, h = (height, width) if vertical else (width, height)
    long_noise = value_noise(w, h, 24, 1.6, seed)
    rings = np.sin((value_noise(w, h, 40, 6, seed + 1) * 6 + np.arange(h)[:, None] * 0.9) * 1.7)
    fine = RNG.random((h, w))
    shade = 1 + grain * (long_noise - 0.5) * 2 + streak * rings + speckle * (fine - 0.5) * 2
    if vertical:
        shade = shade.T
    rgb = np.clip(np.array(base, dtype=float)[None, None, :] * shade[:, :, None], 0, 255)
    return rgb


def to_image(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    if alpha is None:
        alpha = np.full(rgb.shape[:2], 255, dtype=np.uint8)
    return Image.fromarray(np.dstack([rgb, np.clip(alpha, 0, 255).astype(np.uint8)]), "RGBA")


def shade_edges(rgb, x0, y0, x1, y1, light, dark):
    """Bevel: lighten the top/left edge of a rectangle and darken the bottom/right edge."""
    rgb[y0, x0:x1] *= light
    rgb[y0:y1, x0] *= light
    rgb[y1 - 1, x0:x1] *= dark
    rgb[y0:y1, x1 - 1] *= dark


LIGHT_SQUARE = (238, 216, 178)
DARK_SQUARE = (170, 120, 80)
FRAME_WOOD = (88, 57, 35)
PANEL_WOOD = (70, 46, 29)


def checkerboard(square, seed):
    """An 8x8 board of light maple and dark walnut squares, rank 8 at the top, a8 light."""
    size = square * 8
    rgb = np.zeros((size, size, 3))
    for row in range(8):
        for column in range(8):
            light = (row + column) % 2 == 0
            tile = wood(square, square, LIGHT_SQUARE if light else DARK_SQUARE, seed + row * 8 + column,
                        grain=0.06 if light else 0.09, streak=0.03, speckle=0.02)
            shade_edges(tile, 0, 0, square, square, 1.035, 0.95)
            rgb[row * square:(row + 1) * square, column * square:(column + 1) * square] = tile
    return rgb


# --------------------------------------------------------------------------- pixel-art piece sprites

SPRITES = {
    "pawn": [
        "................",
        "................",
        "................",
        "......####......",
        ".....######.....",
        ".....######.....",
        ".....######.....",
        "......####......",
        ".....######.....",
        "......####......",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "................",
    ],
    "knight": [
        "................",
        "......#..#......",
        ".....######.....",
        "....########....",
        "...###x######...",
        "..###########...",
        ".############...",
        ".####...######..",
        "..##.....#####..",
        "........######..",
        ".......#######..",
        "......########..",
        "....#########...",
        "...##########...",
        "...##########...",
        "................",
    ],
    "bishop": [
        "................",
        ".......##.......",
        "......####......",
        ".......##.......",
        "......####......",
        ".....###x##.....",
        ".....##x###.....",
        ".....######.....",
        "......####......",
        ".....######.....",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "................",
    ],
    "rook": [
        "................",
        "................",
        "...##..##..##...",
        "...##..##..##...",
        "...##########...",
        "....########....",
        ".....######.....",
        ".....######.....",
        ".....######.....",
        ".....######.....",
        ".....######.....",
        "....########....",
        "...##########...",
        "..############..",
        "..############..",
        "................",
    ],
    "queen": [
        "................",
        "..#....##....#..",
        "...#..####..#...",
        "...##.####.##...",
        "...##########...",
        "....########....",
        ".....######.....",
        "......####......",
        ".....######.....",
        "......####......",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "................",
    ],
    "king": [
        "................",
        ".......##.......",
        "......####......",
        ".......##.......",
        "...###.##.###...",
        "..############..",
        "..############..",
        "...##########...",
        "....########....",
        ".....######.....",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "................",
    ],
}

ICON = [
    ".........",
    "...###...",
    "..#####..",
    "...###...",
    "...###...",
    "..#####..",
    ".#######.",
    ".#######.",
    ".........",
]

FLIP_ICON = [
    ".........",
    "..#......",
    ".###.....",
    "#.#.#....",
    "..#...#..",
    "....#.#.#",
    ".....###.",
    "......#..",
    ".........",
]

SPRITE_COLOURS = {
    "white": {"outline": (58, 42, 30), "light": (255, 253, 247), "base": (236, 226, 206), "shadow": (192, 175, 150)},
    "black": {"outline": (12, 10, 16), "light": (108, 104, 124), "base": (62, 59, 73), "shadow": (36, 34, 45)},
}


def sprite(mask, colours):
    """Turns a mask ('#' body, 'x' engraved line) into a shaded sprite with a one pixel outline."""
    height, width = len(mask), len(mask[0])
    body = np.array([[c in "#x" for c in row] for row in mask])
    image = np.zeros((height, width, 4), dtype=np.uint8)

    def filled(x, y):
        return 0 <= x < width and 0 <= y < height and body[y, x]

    for y in range(height):
        x = 0
        while x < width:
            if not body[y, x]:
                x += 1
                continue
            start = x
            while x < width and body[y, x]:
                x += 1
            end = x - 1
            for px in range(start, end + 1):
                t = (px - start) / max(1, end - start)
                if end - start <= 1:
                    tone = "light" if px == start and end > start else "base" if end == start else "shadow"
                else:
                    tone = "light" if t < 0.34 else "shadow" if t > 0.70 else "base"
                if not filled(px, y - 1) and tone == "base":
                    tone = "light"
                if not filled(px, y + 1) and tone == "light" and end - start > 1:
                    tone = "base"
                if mask[y][px] == "x":
                    tone = "outline"
                image[y, px] = (*colours[tone], 255)
    for y in range(height):
        for x in range(width):
            if body[y, x]:
                continue
            if filled(x - 1, y) or filled(x + 1, y) or filled(x, y - 1) or filled(x, y + 1):
                image[y, x] = (*colours["outline"], 255)
    return image


# --------------------------------------------------------------------------- block textures

def block_textures():
    top = checkerboard(8, 100)
    top[0, :] *= 0.85
    top[:, 0] *= 0.85
    top[-1, :] *= 0.8
    top[:, -1] *= 0.8
    save_png(to_image(top), "textures/block/chess_board_top.png")

    side = wood(64, 64, FRAME_WOOD, 200, grain=0.12, streak=0.06)
    for band in range(0, 64, 8):
        side[band, :] *= 1.35
        side[band + 7, :] *= 0.7
    save_png(to_image(side), "textures/block/chess_board_side.png")

    felt_noise = value_noise(64, 64, 3, 3, 300)
    felt = np.array([46, 82, 54], dtype=float)[None, None, :] * (0.9 + 0.2 * felt_noise[:, :, None])
    felt *= (0.95 + 0.1 * RNG.random((64, 64)))[:, :, None]
    save_png(to_image(felt), "textures/block/chess_board_bottom.png")

    turbulence = value_noise(16, 16, 6, 6, 400) * 0.7 + value_noise(16, 16, 3, 3, 402) * 0.3
    marble_veins = np.abs(np.sin(np.arange(16)[None, :] * 0.32 + np.arange(16)[:, None] * 0.21 + turbulence * 5.5))
    veins = np.clip(1 - marble_veins * 3.2, 0, 1) ** 1.5
    soft = value_noise(16, 16, 4, 4, 401)
    white = np.array([238, 231, 216], dtype=float)[None, None, :] * (0.96 + 0.06 * soft[:, :, None])
    white -= veins[:, :, None] * np.array([34, 30, 22])
    save_png(to_image(white), "textures/block/chess_piece_white.png")

    black = np.array([46, 43, 53], dtype=float)[None, None, :] * (0.9 + 0.2 * soft[:, :, None])
    black += veins[:, :, None] * np.array([30, 28, 40])
    save_png(to_image(black), "textures/block/chess_piece_black.png")

    gradient = (np.arange(16)[:, None] + np.arange(16)[None, :]) / 30
    gold = np.array([236, 186, 70], dtype=float)[None, None, :] * (1.15 - 0.35 * gradient[:, :, None])
    gold *= (0.95 + 0.1 * value_noise(16, 16, 3, 3, 500))[:, :, None]
    save_png(to_image(gold), "textures/block/chess_gold.png")

    highlight_alpha = np.full((16, 16), 150)
    highlight_alpha[0, :] = highlight_alpha[-1, :] = highlight_alpha[:, 0] = highlight_alpha[:, -1] = 255
    save_png(to_image(np.full((16, 16, 3), 255.0), highlight_alpha), "textures/misc/board_highlight.png")


# --------------------------------------------------------------------------- GUI texture

def disc_alpha(size, radius, inner=0.0, centre=None):
    """Anti-aliased disc (or ring when inner > 0) coverage in 0..1."""
    c = (size / 2 - 0.5) if centre is None else centre
    samples = 4
    coverage = np.zeros((size, size))
    offsets = (np.arange(samples) + 0.5) / samples - 0.5
    for oy in offsets:
        for ox in offsets:
            yy, xx = np.mgrid[0:size, 0:size]
            d = np.hypot(xx + ox - c, yy + oy - c)
            coverage += ((d <= radius) & (d >= inner)).astype(float)
    return coverage / samples ** 2


def gui_texture():
    atlas = np.zeros((512, 512, 4), dtype=np.uint8)

    # Main panel: dark wood with a bevelled border, the board frame and the move history inset.
    panel = wood(312, 192, PANEL_WOOD, 600, grain=0.10, streak=0.05)
    panel[0, :] = panel[-1, :] = panel[:, 0] = panel[:, -1] = (22, 13, 7)
    panel[1, 1:-1] *= 1.6
    panel[1:-1, 1] *= 1.6
    panel[-2, 1:-1] *= 0.6
    panel[1:-1, -2] *= 0.6

    frame = wood(180, 180, FRAME_WOOD, 601, grain=0.12, streak=0.06)
    shade_edges(frame, 0, 0, 180, 180, 1.45, 0.55)
    shade_edges(frame, 1, 1, 179, 179, 1.2, 0.75)
    frame[8:172, 8] = frame[8:172, 171] = frame[8, 8:172] = frame[171, 8:172] = (26, 15, 8)
    frame[9:171, 9] *= 0.6
    frame[9, 9:171] *= 0.6
    panel[6:186, 6:186] = frame
    panel[16:176, 16:176] = checkerboard(20, 700)

    history = np.array([38, 25, 16], dtype=float)[None, None, :] * (0.95 + 0.1 * value_noise(114, 54, 6, 6, 800))[:, :, None]
    shade_edges(history, 0, 0, 114, 54, 0.55, 1.6)
    panel[72:126, 192:306] = history

    alpha = np.full((192, 312), 255)
    for cy, cx in [(0, 0), (0, 311), (191, 0), (191, 311)]:
        alpha[cy, cx] = 0
    atlas[0:192, 0:312] = np.dstack([np.clip(panel, 0, 255), alpha]).astype(np.uint8)

    def put(x, y, rgb, a):
        atlas[y:y + a.shape[0], x:x + a.shape[1]] = np.dstack([np.broadcast_to(np.array(rgb), a.shape + (3,)),
                                                               np.clip(a, 0, 255)]).astype(np.uint8)

    # Square overlays, 20x20 each.
    s = 20
    selected = np.full((s, s), 105.0)
    selected[0, :] = selected[-1, :] = selected[:, 0] = selected[:, -1] = 210
    put(0, 200, (255, 214, 80), selected)
    put(20, 200, (246, 214, 74), np.full((s, s), 88.0))
    put(40, 200, (34, 22, 12), disc_alpha(s, 3.4) * 120)
    put(60, 200, (34, 22, 12), disc_alpha(s, 9.6, inner=7.6) * 125)
    yy, xx = np.mgrid[0:s, 0:s]
    glow = np.clip(1 - np.hypot(xx - 9.5, yy - 9.5) / 12.5, 0, 1) ** 1.3
    put(80, 200, (255, 44, 30), glow * 235)
    hover = np.full((s, s), 42.0)
    hover[0, :] = hover[-1, :] = hover[:, 0] = hover[:, -1] = 95
    put(100, 200, (255, 255, 255), hover)

    # Nine-slice frame for small panels (32x32, 6 px border).
    nine = np.zeros((32, 32, 3))
    nine[:] = wood(32, 32, FRAME_WOOD, 900, grain=0.08)
    nine[6:26, 6:26] = PANEL_WOOD
    nine[0, :] = nine[-1, :] = nine[:, 0] = nine[:, -1] = (22, 13, 7)
    shade_edges(nine, 1, 1, 31, 31, 1.55, 0.6)
    shade_edges(nine, 5, 5, 27, 27, 0.6, 1.4)
    nine_alpha = np.full((32, 32), 255)
    for cy, cx in [(0, 0), (0, 31), (31, 0), (31, 31)]:
        nine_alpha[cy, cx] = 0
    atlas[200:232, 128:160] = np.dstack([np.clip(nine, 0, 255), nine_alpha]).astype(np.uint8)

    # Piece sprites: white on row 224, black on row 240, in PieceType order.
    for side_index, side in enumerate(SIDES):
        for piece_index, piece in enumerate(PIECES):
            atlas[224 + 16 * side_index:240 + 16 * side_index, piece_index * 16:(piece_index + 1) * 16] = \
                sprite(SPRITES[piece], SPRITE_COLOURS[side])
        atlas[256:265, 9 * side_index:9 * side_index + 9] = sprite(ICON, SPRITE_COLOURS[side])

    # Flip-board button (an up and a down arrow): normal, hovered and disabled states, 9 px apart.
    for state, (background, arrow) in enumerate((((26, 15, 8, 170), (243, 230, 207, 255)),
                                                  ((70, 46, 20, 220), (255, 211, 122, 255)),
                                                  ((26, 15, 8, 120), (120, 100, 80, 255)))):
        y0 = 270 + 9 * state
        atlas[y0:y0 + 9, 0:9] = background
        for y, row in enumerate(FLIP_ICON):
            for x, c in enumerate(row):
                if c == "#":
                    atlas[y0 + y, x] = arrow

    save_png(Image.fromarray(atlas, "RGBA"), "textures/gui/chess_board.png")
    return atlas


def logo(atlas):
    """Mod list logo: a board in the starting position next to two large kings."""
    width, height = 320, 160
    canvas = to_image(wood(width, height, PANEL_WOOD, 1000, grain=0.1))
    board = to_image(checkerboard(16, 1001))
    frame = to_image(wood(136, 136, FRAME_WOOD, 1002))
    canvas.alpha_composite(frame, (12, 12))
    canvas.alpha_composite(board, (16, 16))
    back_rank = ["rook", "knight", "bishop", "queen", "king", "bishop", "knight", "rook"]
    sprites = Image.fromarray(atlas, "RGBA")

    def piece(side, name):
        u = PIECES.index(name) * 16
        v = 224 if side == "white" else 240
        return sprites.crop((u, v, u + 16, v + 16))

    for column, name in enumerate(back_rank):
        canvas.alpha_composite(piece("black", name), (16 + column * 16, 16))
        canvas.alpha_composite(piece("black", "pawn"), (16 + column * 16, 32))
        canvas.alpha_composite(piece("white", "pawn"), (16 + column * 16, 112))
        canvas.alpha_composite(piece("white", name), (16 + column * 16, 128))
    canvas.alpha_composite(piece("white", "king").resize((96, 96), Image.NEAREST), (164, 34))
    canvas.alpha_composite(piece("black", "queen").resize((80, 80), Image.NEAREST), (236, 50))
    path = RESOURCES / "logo.png"
    canvas.save(path)


# --------------------------------------------------------------------------- 3D piece models

# A round slice is drawn like a pixel-art circle: a wide bar, a deep bar and a square overlapping in the middle.
# Their outline is a stepped circle, which suits Minecraft and stays crisp at any size.
ROUND_STEPS = ((1.0, 0.55), (0.55, 1.0), (0.84, 0.84))
ROUND_SCALE = 0.95


def tier(width, y0, y1, texture="#body", round_=True):
    """A horizontal slice of a turned piece, centred on the block."""
    if not round_:
        half = width / 2
        return [box(8 - half, y0, 8 - half, 8 + half, y1, 8 + half, texture)]
    radius = width * ROUND_SCALE / 2
    elements = []
    for index, (rx, rz) in enumerate(ROUND_STEPS):
        # Offset the caps a little so the overlapping boxes never fight over the same plane.
        inset = 0.01 * index
        elements.append(box(8 - radius * rx, y0 + inset, 8 - radius * rz, 8 + radius * rx, y1 - inset, 8 + radius * rz, texture))
    return elements


def box(x0, y0, z0, x1, y1, z1, texture="#body", rotation=None):
    faces = {face: {"texture": texture} for face in ("north", "east", "south", "west", "up", "down")}
    element = {"from": [round(x0, 3), round(y0, 3), round(z0, 3)], "to": [round(x1, 3), round(y1, 3), round(z1, 3)], "faces": faces}
    if rotation is not None:
        element["rotation"] = rotation
    return element


def stack(*tiers):
    return [element for t in tiers for element in t]


def base():
    return stack(tier(8.5, 0, 1.25), tier(7.25, 1.25, 2.5))


def piece_elements(name):
    if name == "pawn":
        return stack(tier(7.5, 0, 1.25), tier(6.25, 1.25, 2.5), tier(4.75, 2.5, 4.5), tier(3.75, 4.5, 6.5),
                     tier(5.5, 6.5, 7.25), tier(5, 7.25, 10), tier(3.5, 10, 10.75))
    if name == "rook":
        elements = stack(base(), tier(6, 2.5, 9.5), tier(7.5, 9.5, 10.75, round_=False))
        lo, hi = 8 - 3.75, 8 + 3.75
        for x0, x1 in ((lo, lo + 2), (hi - 2, hi)):
            for z0, z1 in ((lo, lo + 2), (hi - 2, hi)):
                elements.append(box(x0, 10.75, z0, x1, 12.5, z1))
        elements.append(box(7.25, 10.75, lo, 8.75, 12.5, lo + 1.25))
        elements.append(box(7.25, 10.75, hi - 1.25, 8.75, 12.5, hi))
        elements.append(box(lo, 10.75, 7.25, lo + 1.25, 12.5, 8.75))
        elements.append(box(hi - 1.25, 10.75, 7.25, hi, 12.5, 8.75))
        return elements
    if name == "knight":
        return stack(base(), [
            box(5.5, 2.5, 5.5, 10.5, 6.5, 10.5),       # chest
            box(5.75, 6.5, 6.25, 10.25, 10, 10.75),    # neck
            box(6, 9, 3.5, 10, 12.75, 8.5),            # head
            box(6.5, 8.75, 1.75, 9.5, 11.5, 4),        # muzzle
            box(6.5, 8, 3.5, 9.5, 9, 7),               # jaw
            box(7.25, 5.5, 9.75, 8.75, 13.25, 11.5),   # mane
            box(6.25, 12.75, 6.5, 7.25, 14.25, 7.5),   # ears
            box(8.75, 12.75, 6.5, 9.75, 14.25, 7.5),
            box(5.85, 10.75, 4.75, 6.05, 11.5, 5.5, "#accent"),   # eyes
            box(9.95, 10.75, 4.75, 10.15, 11.5, 5.5, "#accent"),
        ])
    if name == "bishop":
        return stack(base(), tier(4.75, 2.5, 7.5), tier(6.25, 7.5, 8.25), tier(5.5, 8.25, 11),
                     tier(4.25, 11, 12.75), tier(2.75, 12.75, 13.5), tier(1.75, 13.5, 14.5, "#accent"))
    if name == "queen":
        elements = stack(base(), tier(5.75, 2.5, 4.5), tier(4.25, 4.5, 9.5), tier(6.25, 9.5, 10.25),
                         tier(5, 10.25, 12.5), tier(6.5, 12.5, 13.25))
        for angle in (0, 45):
            for dx, dz in ((2.35, 0), (-2.35, 0), (0, 2.35), (0, -2.35)):
                point = box(8 + dx - 0.5, 13.25, 8 + dz - 0.5, 8 + dx + 0.5, 14.25, 8 + dz + 0.5)
                if angle:
                    point["rotation"] = {"origin": [8, 13.25, 8], "axis": "y", "angle": 45}
                elements.append(point)
        elements += tier(2.25, 13.25, 15.25, "#accent")
        return elements
    if name == "king":
        return stack(base(), tier(5.75, 2.5, 4.5), tier(4.5, 4.5, 10), tier(6.5, 10, 10.75), tier(5.5, 10.75, 12.75),
                     tier(4.25, 12.75, 13.5), [
                         box(7.4, 13.5, 7.4, 8.6, 16, 8.6, "#accent"),
                         box(6.4, 14.4, 7.4, 9.6, 15.3, 8.6, "#accent"),
                     ])
    raise ValueError(name)


def models():
    blockstates = ASSETS / "blockstates"
    block_models = ASSETS / "models" / "block"
    item_models = ASSETS / "models" / "item"

    for piece in PIECES:
        save_json(block_models / f"template_chess_{piece}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": "#body"},
            "elements": piece_elements(piece),
        })
        for side in SIDES:
            name = f"{side}_{piece}"
            save_json(block_models / f"{name}.json", {
                "parent": f"{MODID}:block/template_chess_{piece}",
                "textures": {
                    "body": f"{MODID}:block/chess_piece_{side}",
                    "accent": f"{MODID}:block/chess_gold",
                },
            })
            save_json(item_models / f"{name}.json", {"parent": f"{MODID}:block/{name}"})
            save_json(blockstates / f"{name}.json", {"variants": {
                f"facing={facing}": ({"model": f"{MODID}:block/{name}", "y": y} if y else {"model": f"{MODID}:block/{name}"})
                for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
            }})

    side_face = {"uv": [0, 14, 16, 16], "texture": "#side"}
    save_json(block_models / "chess_board.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{MODID}:block/chess_board_side",
            "top": f"{MODID}:block/chess_board_top",
            "side": f"{MODID}:block/chess_board_side",
            "bottom": f"{MODID}:block/chess_board_bottom",
        },
        "elements": [{
            "from": [0, 0, 0], "to": [16, 2, 16],
            "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
                "north": dict(side_face), "east": dict(side_face), "south": dict(side_face), "west": dict(side_face),
            },
        }],
    })
    save_json(item_models / "chess_board.json", {"parent": f"{MODID}:block/chess_board"})
    save_json(blockstates / "chess_board.json", {"variants": {
        f"facing={facing}": ({"model": f"{MODID}:block/chess_board", "y": y} if y else {"model": f"{MODID}:block/chess_board"})
        for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270))
    }})


# --------------------------------------------------------------------------- data

def data():
    def loot(name):
        save_json(DATA / "loot_tables" / "blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": f"{MODID}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

    loot("chess_board")
    save_json(DATA / "recipes" / "chess_board.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["LDL", "DLD", "SSS"],
        "key": {
            "L": {"item": "minecraft:birch_planks"},
            "D": {"item": "minecraft:dark_oak_planks"},
            "S": {"tag": "minecraft:wooden_slabs"},
        },
        "result": {"item": f"{MODID}:chess_board"},
    })
    materials = {"white": "minecraft:quartz_block", "black": "minecraft:polished_blackstone"}
    for side in SIDES:
        for piece in PIECES:
            name = f"{side}_{piece}"
            loot(name)
            save_json(DATA / "recipes" / f"{name}_from_stonecutting.json", {
                "type": "minecraft:stonecutting",
                "ingredient": {"item": materials[side]},
                "result": f"{MODID}:{name}",
                "count": 1,
            })

    tags = RESOURCES / "data" / "minecraft" / "tags" / "blocks" / "mineable"
    save_json(tags / "axe.json", {"replace": False, "values": [f"{MODID}:chess_board"]})
    save_json(tags / "pickaxe.json", {"replace": False, "values": [f"{MODID}:{side}_{piece}" for side in SIDES for piece in PIECES]})


if __name__ == "__main__":
    block_textures()
    atlas = gui_texture()
    logo(atlas)
    models()
    data()
    print("Assets written to", RESOURCES)
