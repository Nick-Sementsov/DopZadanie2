#!/usr/bin/env python3
"""Builds the meshes and textures of Druzhina's war elephant and siege engines.

Every model is written here as parts (pivots and turns, as Minecraft's ModelPart has them) holding boxes of a
material. The script lays the boxes out on a texture of their own, paints each face of each box (wood with its grain
along the beam and rings on a sawn end, iron with rivets, rope, hides, cloth, the elephant's wrinkled skin...), darkens
the edges so that the boxes read as solid, and writes for each model a Java class with its LayerDefinition
(client/mesh/*Mesh.java) and the texture (textures/entity/*.png). The animated part names are those the model
classes (client/siege/*Model.java, client/ElephantModel.java) look for.

    python3 tools/models.py                 # writes the Java meshes and the textures
    python3 tools/models.py --preview DIR   # and pictures of every model from four sides, for a look without the game
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
JAVA = os.path.join(ROOT, "src/main/java/io/github/nicksementsov/druzhina/client/mesh")
TEXTURES = os.path.join(ROOT, "src/main/resources/assets/druzhina/textures/entity")


# ====================================================================== the model


class Box:
    def __init__(self, mat, x, y, z, w, h, d, grain=None, inflate=0.0, mirror=False, variant=0):
        self.mat, self.x, self.y, self.z, self.w, self.h, self.d = mat, x, y, z, w, h, d
        if grain is None:
            dims = {"x": w, "y": h, "z": d}
            grain = max(dims, key=lambda k: dims[k])
        self.grain = grain
        self.inflate = inflate
        self.mirror = mirror
        self.variant = variant
        self.uv = None

    def key(self):
        return (self.mat, math.ceil(self.w), math.ceil(self.h), math.ceil(self.d), self.grain, self.variant)

    def size(self):
        w, h, d = math.ceil(self.w), math.ceil(self.h), math.ceil(self.d)
        return 2 * (w + d), d + h


class Part:
    def __init__(self, model, name, parent, pivot, rot):
        self.model, self.name, self.parent, self.pivot, self.rot = model, name, parent, pivot, rot
        self.boxes = []

    def box(self, mat, x, y, z, w, h, d, **kw):
        self.boxes.append(Box(mat, x, y, z, w, h, d, **kw))
        return self

    def child(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        return self.model.part(name, self, pivot, rot)


class Model:
    def __init__(self, cls, texture, width=256, doc=""):
        self.cls, self.texture, self.width, self.doc = cls, texture, width, doc
        self.parts = []
        self.names = set()
        self.height = 0

    def part(self, name, parent=None, pivot=(0, 0, 0), rot=(0, 0, 0)):
        assert name not in self.names, name
        self.names.add(name)
        p = Part(self, name, parent, pivot, rot)
        self.parts.append(p)
        return p


# ====================================================================== helpers for the shapes


def brace(model, parent, name, a, b, mat, size=3, size2=None, variant=0):
    """A beam from point a to point b (in the parent's frame), square in section, as a part turned to lie along it."""
    size2 = size if size2 is None else size2
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    ux, uy, uz = dx / length, dy / length, dz / length
    # the box runs along -y; rotationZYX(0, yRot, xRot) takes (0,-1,0) to (-sin x sin y, -cos x, -sin x cos y)
    xr = math.acos(max(-1.0, min(1.0, -uy)))
    yr = math.atan2(-ux, -uz) if abs(math.sin(xr)) > 1e-6 else 0.0
    p = model.part(name, parent, a, (math.degrees(xr), math.degrees(yr), 0))
    p.box(mat, -size / 2, -length, -size2 / 2, size, length, size2, grain="y", variant=variant)
    return p


def wheel(model, parent, name, at, r, thick=2, spokes=4, mat="oak", tyre=True):
    """A spoked wheel turning about x: a hub, spokes, and a rim of eight felloes (and an iron tyre)."""
    w = model.part(name, parent, at, (0, 0, 0))
    w.box("iron", -thick / 2 - 0.5, -1.5, -1.5, thick + 1, 3, 3, grain="x")
    seg = 2 * r * math.tan(math.pi / 8) + 0.6
    for k in range(8):
        f = model.part(f"{name}_felloe{k}", w, (0, 0, 0), (k * 45, 0, 0))
        f.box(mat, -thick / 2, -r, -seg / 2, thick, 1.6, seg, grain="z", variant=1)
        if tyre:
            f.box("iron", -thick / 2, -r - 0.01, -seg / 2, thick, 0.6, seg, grain="z", inflate=0.06)
    for k in range(spokes):
        s = model.part(f"{name}_spoke{k}", w, (0, 0, 0), (k * 180 / spokes + 22.5, 0, 0))
        s.box(mat, -0.5, -r + 1, -0.5, 1, 2 * r - 2, 1, grain="y", variant=2)
    return w


def band(part, x, y, z, w, h, d, inflate=0.3):
    """An iron band round a beam."""
    part.box("iron", x, y, z, w, h, d, inflate=inflate, grain="x" if w <= min(h, d) else ("z" if d <= h else "y"))


# ====================================================================== painting


R = random.Random(1240)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, f):
    return tuple(v * f for v in c)


def jitter(c, n, rnd=R):
    d = rnd.uniform(-n, n)
    return (c[0] + d, c[1] + d * 0.96, c[2] + d * 0.9)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (clamp(c[0]), clamp(c[1]), clamp(c[2]), a)

    def get(self, x, y):
        return self.px[x, y]


class Face:
    """One face of a box on the texture: where it is, how big, which axes run across (u) and down (v) it."""

    def __init__(self, name, x0, y0, w, h, au, av, box):
        self.name, self.x0, self.y0, self.w, self.h, self.au, self.av, self.box = name, x0, y0, w, h, au, av, box

    def along(self):
        """How the grain of the box lies on this face: 'u', 'v' or 'end'."""
        g = self.box.grain
        return "u" if g == self.au else "v" if g == self.av else "end"

    @property
    def vertical(self):
        return self.name in ("west", "north", "east", "south")


def faces(box):
    u, v = box.uv
    w, h, d = math.ceil(box.w), math.ceil(box.h), math.ceil(box.d)
    return [
        Face("top", u + d, v, w, d, "x", "z", box),
        Face("bottom", u + d + w, v, w, d, "x", "z", box),
        Face("west", u, v + d, d, h, "z", "y", box),
        Face("north", u + d, v + d, w, h, "x", "y", box),
        Face("east", u + d + w, v + d, d, h, "z", "y", box),
        Face("south", u + 2 * d + w, v + d, w, h, "x", "y", box),
    ]


def face_pixels(f):
    for j in range(f.h):
        for i in range(f.w):
            yield i, j


# ---------------------------------------------------------------------- materials


def paint_wood(cv, f, base, rnd, knots=True, planks=0):
    along = f.along()
    if along == "end":
        # a sawn end: rings round the heart, a crack or two
        cx, cy = (f.w - 1) / 2 + rnd.uniform(-0.6, 0.6), (f.h - 1) / 2 + rnd.uniform(-0.6, 0.6)
        light = mix(base, (196, 168, 120), 0.35)
        for i, j in face_pixels(f):
            d = math.hypot(i - cx, j - cy)
            ring = 0.5 + 0.5 * math.sin(d * 2.2)
            c = mix(shade(light, 0.95), shade(light, 0.78), ring * 0.6)
            cv.put(f.x0 + i, f.y0 + j, jitter(c, 4, rnd))
        if f.w >= 3 and f.h >= 3 and rnd.random() < 0.6:
            for k in range(int(min(f.w, f.h) / 2)):
                cv.put(f.x0 + int(cx + k), f.y0 + int(cy), shade(base, 0.55))
        return
    long_, across = (f.w, f.h) if along == "u" else (f.h, f.w)
    phase = [rnd.uniform(0, 6.3) for _ in range(across)]
    tone = [rnd.uniform(-9, 9) for _ in range(across)]
    for a in range(across):
        for l in range(long_):
            g = math.sin(l * 0.17 + phase[a] + math.sin(l * 0.05 + a) * 1.3)
            c = shade(base, 0.92 + 0.08 * g)
            c = tuple(v + tone[a] for v in c)
            if rnd.random() < 0.07:
                c = shade(c, 0.86)
            if planks and a % planks == planks - 1:
                c = shade(c, 0.62)
            if planks and a % planks == 0:
                c = shade(c, 1.07)
            i, j = (l, a) if along == "u" else (a, l)
            cv.put(f.x0 + i, f.y0 + j, jitter(c, 3, rnd))
    if planks:
        # nail heads at the ends of the boards
        for a in range(planks // 2, across, planks):
            for l in (1, long_ - 2):
                if 0 < l < long_ - 1:
                    i, j = (l, a) if along == "u" else (a, l)
                    cv.put(f.x0 + i, f.y0 + j, (52, 50, 52))
    if knots and f.w * f.h > 70 and f.w > 2 and f.h > 2 and rnd.random() < 0.55:
        kx, ky = rnd.randrange(1, f.w - 1), rnd.randrange(1, f.h - 1)
        cv.put(f.x0 + kx, f.y0 + ky, shade(base, 0.5))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if along == ("u" if dy == 0 else "v"):
                cv.put(f.x0 + kx + dx, f.y0 + ky + dy, shade(base, 0.7))


def paint_iron(cv, f, base, rnd, rust=0.06, rivets=True):
    for i, j in face_pixels(f):
        c = jitter(base, 5, rnd)
        if (i * 7 + j * 3) % 11 == 0:
            c = shade(c, 1.08)
        if rnd.random() < rust:
            c = mix(c, (112, 72, 46), rnd.uniform(0.4, 0.8))
        cv.put(f.x0 + i, f.y0 + j, c)
    if rivets and f.w >= 5 and f.h >= 4:
        for i in (1, f.w - 2):
            for j in range(1, f.h - 1, 3):
                cv.put(f.x0 + i, f.y0 + j, shade(base, 1.45))
                cv.put(f.x0 + i + 1 if i == 1 else f.x0 + i, f.y0 + j + 1, shade(base, 0.6))


def paint_rope(cv, f, base, rnd):
    for i, j in face_pixels(f):
        t = (i + j * (1 if f.along() != "v" else 2)) % 3
        c = shade(base, (1.06, 0.92, 0.78)[t])
        cv.put(f.x0 + i, f.y0 + j, jitter(c, 4, rnd))


def paint_hide(cv, f, base, rnd):
    blot = [(rnd.uniform(0, f.w), rnd.uniform(0, f.h), rnd.uniform(3, 9), rnd.uniform(-14, 10)) for _ in range(max(1, f.w * f.h // 160))]
    for i, j in face_pixels(f):
        c = jitter(base, 6, rnd)
        for bx, by, r, d in blot:
            if (i - bx) ** 2 + (j - by) ** 2 < r * r:
                c = tuple(v + d for v in c)
                break
        if rnd.random() < 0.15:
            c = shade(c, 0.88)
        # seams where the hides are sewn together
        if (i % 17 == 0 or j % 13 == 0) and f.w > 6 and f.h > 6:
            c = shade(c, 0.7)
            if (i + j) % 2 == 0:
                c = (176, 150, 108)
        cv.put(f.x0 + i, f.y0 + j, c)


def paint_cloth(cv, f, base, rnd, trim=(160, 126, 58), pattern=True):
    for i, j in face_pixels(f):
        c = jitter(base, 4, rnd)
        if (i + j) % 2 == 0:
            c = shade(c, 0.95)
        if pattern and f.vertical and f.w > 6 and f.h > 6 and ((i + j) % 8 == 0 or (i - j) % 8 == 0):
            c = shade(base, 0.8)
        cv.put(f.x0 + i, f.y0 + j, c)
    if f.vertical and f.h >= 5:
        # a woven border of tarnished gold along the hem, a dark line above it
        for i in range(f.w):
            cv.put(f.x0 + i, f.y0 + f.h - 1, jitter(shade(trim, 0.8), 6, rnd))
            cv.put(f.x0 + i, f.y0 + f.h - 2, jitter(trim, 8, rnd) if i % 3 else shade(trim, 1.15))
            cv.put(f.x0 + i, f.y0 + f.h - 3, shade(base, 0.55))


def paint_skin(cv, f, base, rnd, wrinkle=3, pink=0.0, ring=False):
    """An elephant's hide: grey-brown, mottled, creased across (or in rings round the trunk), darker below."""
    blot = [(rnd.uniform(0, f.w), rnd.uniform(0, f.h), rnd.uniform(2, 7), rnd.uniform(-10, 8)) for _ in range(max(1, f.w * f.h // 90))]
    offs = [rnd.randrange(wrinkle) for _ in range(f.w + 1)]
    for i, j in face_pixels(f):
        c = jitter(base, 5, rnd)
        for bx, by, r, d in blot:
            if (i - bx) ** 2 + (j - by) ** 2 < r * r:
                c = tuple(v + d for v in c)
                break
        if f.name == "bottom":
            c = shade(c, 0.78)
        elif f.vertical and f.h > 4:
            c = shade(c, 1.04 - 0.16 * j / max(1, f.h - 1))
        if f.name != "top" or ring:
            creased = (j + offs[i // 2]) % wrinkle == 0 if not ring else (j % 2 == 0)
            if creased and rnd.random() < 0.85:
                c = shade(c, 0.78)
            elif creased is False and (j + offs[i // 2]) % wrinkle == 1 and not ring:
                c = shade(c, 1.04)
        elif rnd.random() < 0.08:
            c = shade(c, 0.88)
        if pink and rnd.random() < pink:
            c = mix(c, (176, 142, 128), rnd.uniform(0.4, 0.8))
        cv.put(f.x0 + i, f.y0 + j, c)


def paint_ivory(cv, f, rnd):
    for i, j in face_pixels(f):
        t = j / max(1, f.h - 1) if f.along() == "v" else i / max(1, f.w - 1)
        c = mix((222, 210, 182), (196, 176, 136), t * 0.5)
        if (i if f.along() == "v" else j) % 2 == 0:
            c = shade(c, 0.95)
        cv.put(f.x0 + i, f.y0 + j, jitter(c, 4, rnd))


def paint_stone(cv, f, base, rnd):
    for i, j in face_pixels(f):
        c = jitter(base, 14, rnd)
        if rnd.random() < 0.12:
            c = shade(c, 0.72)
        cv.put(f.x0 + i, f.y0 + j, c)


def paint_flat(cv, f, base, rnd, n=5):
    for i, j in face_pixels(f):
        cv.put(f.x0 + i, f.y0 + j, jitter(base, n, rnd))


def paint_crate(cv, f, rnd):
    """The trebuchet's box of stones: planked sides with iron corners and bands, stones heaped on top."""
    if f.name == "top":
        paint_stone(cv, f, (124, 120, 114), rnd)
        return
    paint_wood(cv, f, (104, 80, 56), rnd, knots=False, planks=4)
    if f.vertical:
        for i in range(f.w):
            for j in (1, f.h - 2):
                cv.put(f.x0 + i, f.y0 + j, jitter((70, 72, 78), 5, rnd))


def paint_muzzle(cv, f, rnd):
    paint_iron(cv, f, (66, 66, 70), rnd, rust=0.1, rivets=False)
    if f.name == "north":
        cx, cy, r = (f.w - 1) / 2, (f.h - 1) / 2, min(f.w, f.h) * 0.34
        for i, j in face_pixels(f):
            d = math.hypot(i - cx, j - cy)
            if d < r:
                cv.put(f.x0 + i, f.y0 + j, (16 + d * 3, 14 + d * 3, 13 + d * 3))
            elif d < r + 1:
                cv.put(f.x0 + i, f.y0 + j, (96, 96, 100))


def paint_loop_planks(cv, f, rnd):
    """Upright boards with loopholes: slits through the front and the back."""
    paint_wood(cv, f, (116, 92, 66), rnd, planks=4)
    if f.name in ("north", "south"):
        for cx in range(5, f.w - 3, 9):
            for j in range(int(f.h * 0.28), int(f.h * 0.28) + 4):
                cv.put(f.x0 + cx, f.y0 + j, (0, 0, 0), 0)
                cv.put(f.x0 + cx + 1, f.y0 + j, (0, 0, 0), 0)


MATERIALS = {
    "oak": lambda cv, f, rnd: paint_wood(cv, f, (118, 94, 68), rnd),
    "oak_light": lambda cv, f, rnd: paint_wood(cv, f, (138, 112, 80), rnd),
    "dark": lambda cv, f, rnd: paint_wood(cv, f, (78, 62, 48), rnd),
    "planks": lambda cv, f, rnd: paint_wood(cv, f, (124, 100, 72), rnd, planks=5),
    "dark_planks": lambda cv, f, rnd: paint_wood(cv, f, (88, 70, 52), rnd, planks=5),
    "loop_planks": paint_loop_planks,
    "iron": lambda cv, f, rnd: paint_iron(cv, f, (70, 72, 78), rnd),
    "steel": lambda cv, f, rnd: paint_iron(cv, f, (122, 126, 132), rnd, rust=0.02),
    "bronze": lambda cv, f, rnd: paint_iron(cv, f, (128, 96, 54), rnd, rust=0.0),
    "gold": lambda cv, f, rnd: paint_iron(cv, f, (170, 136, 62), rnd, rust=0.0, rivets=False),
    "rope": lambda cv, f, rnd: paint_rope(cv, f, (150, 128, 90), rnd),
    "chain": lambda cv, f, rnd: paint_rope(cv, f, (88, 90, 96), rnd),
    "hide": lambda cv, f, rnd: paint_hide(cv, f, (104, 78, 54), rnd),
    "leather": lambda cv, f, rnd: paint_flat(cv, f, (92, 64, 42), rnd),
    "stone": lambda cv, f, rnd: paint_stone(cv, f, (122, 118, 112), rnd),
    "clay": lambda cv, f, rnd: paint_stone(cv, f, (146, 86, 56), rnd),
    "pitch": lambda cv, f, rnd: paint_flat(cv, f, (34, 28, 24), rnd, 4),
    "crate": paint_crate,
    "muzzle": paint_muzzle,
    "skin": lambda cv, f, rnd: paint_skin(cv, f, (114, 105, 98), rnd),
    "skin_pink": lambda cv, f, rnd: paint_skin(cv, f, (114, 105, 98), rnd, pink=0.14),
    "trunk": lambda cv, f, rnd: paint_skin(cv, f, (110, 102, 96), rnd, ring=True, pink=0.06),
    "ear": lambda cv, f, rnd: paint_skin(cv, f, (118, 108, 102), rnd, wrinkle=4, pink=0.1),
    "toenail": lambda cv, f, rnd: paint_flat(cv, f, (176, 164, 138), rnd),
    "eye": lambda cv, f, rnd: paint_flat(cv, f, (28, 22, 20), rnd, 2),
    "ivory": paint_ivory,
    "crimson": lambda cv, f, rnd: paint_cloth(cv, f, (104, 30, 30), rnd),
    "indigo": lambda cv, f, rnd: paint_cloth(cv, f, (40, 46, 74), rnd, pattern=False),
    "canopy": lambda cv, f, rnd: paint_cloth(cv, f, (118, 38, 34), rnd, trim=(150, 118, 56)),
    "hair": lambda cv, f, rnd: paint_flat(cv, f, (44, 38, 34), rnd, 8),
}

# metals: the edges catch the light instead of falling into shadow
SHINY = {"iron", "steel", "bronze", "gold", "chain", "muzzle"}


def edges(cv, f, shiny):
    if f.w < 2 or f.h < 2:
        return
    k = 1.22 if shiny else (0.8 if min(f.w, f.h) >= 3 else 0.9)
    for i, j in face_pixels(f):
        if i in (0, f.w - 1) or j in (0, f.h - 1):
            r, g, b, a = cv.get(f.x0 + i, f.y0 + j)
            if a:
                cv.put(f.x0 + i, f.y0 + j, shade((r, g, b), k), a)


# ====================================================================== layout, painting, Java


def layout(model):
    """Places every box (or its twin of the same material and size) on the texture, in shelves, tallest first."""
    unique = {}
    for p in model.parts:
        for b in p.boxes:
            unique.setdefault(b.key(), []).append(b)
    order = sorted(unique.items(), key=lambda kv: (-kv[1][0].size()[1], -kv[1][0].size()[0]))
    x = y = shelf = 0
    W = model.width
    for key, boxes in order:
        w, h = boxes[0].size()
        assert w <= W, (model.cls, key)
        if x + w > W:
            x, y, shelf = 0, y + shelf, 0
        for b in boxes:
            b.uv = (x, y)
        x += w
        shelf = max(shelf, h)
    used = y + shelf
    H = 16
    while H < used:
        H *= 2
    model.height = H
    return unique


def paint(model, unique):
    cv = Canvas(model.width, model.height)
    for key, boxes in unique.items():
        b = boxes[0]
        rnd = random.Random(hash((model.cls,) + key) & 0xFFFFFFFF)
        for f in faces(b):
            MATERIALS[b.mat](cv, f, rnd)
            edges(cv, f, b.mat in SHINY)
    return cv.img


def num(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return s + "f" if "." in s else s


def java(model):
    var = {p.name: f"p{i}" for i, p in enumerate(model.parts)}
    out = []
    for p in model.parts:
        parent = "root" if p.parent is None else var[p.parent.name]
        cubes = ["CubeListBuilder.create()"]
        mirror = False
        for b in p.boxes:
            if b.mirror != mirror:
                cubes.append(f".mirror({'true' if b.mirror else 'false'})")
                mirror = b.mirror
            dims = ", ".join(num(t) for t in (b.x, b.y, b.z, b.w, b.h, b.d))
            deform = f", new CubeDeformation({num(b.inflate)})" if b.inflate else ""
            cubes.append(f".texOffs({b.uv[0]}, {b.uv[1]}).addBox({dims}{deform})")
        px, py, pz = p.pivot
        rx, ry, rz = (math.radians(a) for a in p.rot)
        pose = (f"PartPose.offsetAndRotation({num(px)}, {num(py)}, {num(pz)}, {num(rx)}, {num(ry)}, {num(rz)})"
                if any(abs(a) > 1e-9 for a in (rx, ry, rz)) else f"PartPose.offset({num(px)}, {num(py)}, {num(pz)})")
        body = "\n                ".join(cubes)
        out.append(f"        PartDefinition {var[p.name]} = {parent}.addOrReplaceChild(\"{p.name}\", {body},\n            {pose});")
    text = "\n".join(out)
    return f"""package io.github.nicksementsov.druzhina.client.mesh;

import io.github.nicksementsov.druzhina.Druzhina;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * {model.doc}
 *
 * <p>Written by {{@code tools/models.py}}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class {model.cls} {{
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/{model.texture}.png");

    private {model.cls}() {{
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{text}
        return LayerDefinition.create(mesh, {model.width}, {model.height});
    }}
}}
"""


# ====================================================================== a quick look without the game


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def preview(model, tex, path, poses=None):
    """Draws the model with its texture from four sides (orthographic, a z-buffer, simple light)."""
    import numpy as np
    poses = poses or {}
    world = {}
    for p in model.parts:
        rot = poses.get(p.name, p.rot)
        m = mat_mul(translate(*p.pivot), mat_mul(rot_z(math.radians(rot[2])), mat_mul(rot_y(math.radians(rot[1])), rot_x(math.radians(rot[0])))))
        world[p.name] = m if p.parent is None else mat_mul(world[p.parent.name], m)
    T = np.asarray(tex.convert("RGBA"), dtype=np.float32)
    TW, TH = tex.size
    views = [(30, 25), (150, 25), (250, 20), (90, 70)]
    S = 360
    sheet = Image.new("RGB", (S * 2, S * 2), (200, 214, 230))
    quads = []
    for p in model.parts:
        m = world[p.name]
        for b in p.boxes:
            g = b.inflate
            x0, y0, z0 = b.x - g, b.y - g, b.z - g
            x1, y1, z1 = b.x + b.w + g, b.y + b.h + g, b.z + b.d + g
            if b.mirror:
                x0, x1 = x1, x0
            V = {0: (x0, y0, z0), 1: (x1, y0, z0), 2: (x1, y1, z0), 3: (x0, y1, z0),
                 4: (x0, y0, z1), 5: (x1, y0, z1), 6: (x1, y1, z1), 7: (x0, y1, z1)}
            u, v = b.uv
            w, h, d = b.w, b.h, b.d
            u0, u1, u2, u3, u4, u5 = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
            v0, v1, v2 = v, v + d, v + d + h
            polys = [((5, 4, 0, 1), (u1, v0, u2, v1)), ((2, 3, 7, 6), (u2, v1, u3, v0)), ((0, 4, 7, 3), (u0, v1, u1, v2)),
                     ((1, 0, 3, 2), (u1, v1, u2, v2)), ((5, 1, 2, 6), (u2, v1, u4, v2)), ((4, 5, 6, 7), (u4, v1, u5, v2))]
            for idx, (pu0, pv0, pu1, pv1) in polys:
                pts = [apply(m, V[i]) for i in idx]
                uvs = [(pu1, pv0), (pu0, pv0), (pu0, pv1), (pu1, pv1)]
                quads.append((pts, uvs))
    allpts = [q for quad in quads for q in quad[0]]
    cx = sum(p[0] for p in allpts) / len(allpts)
    cy = sum(p[1] for p in allpts) / len(allpts)
    cz = sum(p[2] for p in allpts) / len(allpts)
    ext = max(max(abs(p[0] - cx), abs(p[1] - cy), abs(p[2] - cz)) for p in allpts) * 1.9
    for vi, (yaw, pitch) in enumerate(views):
        img = np.zeros((S, S, 3), dtype=np.float32)
        img[:] = (200, 214, 230)
        zb = np.full((S, S), -1e9, dtype=np.float32)
        ya, pa = math.radians(yaw), math.radians(pitch)

        def proj(p):
            x, y, z = p[0] - cx, -(p[1] - cy), p[2] - cz
            x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
            y, z = y * math.cos(pa) + z * math.sin(pa), -y * math.sin(pa) + z * math.cos(pa)
            k = S / ext
            return S / 2 + x * k, S / 2 - y * k, -z

        light = np.array([0.4, 0.8, -0.45])
        light /= np.linalg.norm(light)
        for pts, uvs in quads:
            P = [proj(p) for p in pts]
            o = np.array(P[1][:2])
            e1 = np.array(P[0][:2]) - o
            e2 = np.array(P[2][:2]) - o
            det = e1[0] * e2[1] - e1[1] * e2[0]
            if abs(det) < 1e-6:
                continue
            a3 = np.array(pts[0]) - np.array(pts[1])
            b3 = np.array(pts[2]) - np.array(pts[1])
            n = np.cross(a3, b3)
            nn = np.linalg.norm(n)
            if nn < 1e-9:
                continue
            n = n / nn
            n = np.array([n[0], -n[1], n[2]])
            lum = 0.55 + 0.45 * abs(float(np.dot(n, light)))
            xs = [p[0] for p in P]
            ys = [p[1] for p in P]
            xa, xb = max(0, int(min(xs))), min(S - 1, int(max(xs)) + 1)
            ya_, yb = max(0, int(min(ys))), min(S - 1, int(max(ys)) + 1)
            if xa > xb or ya_ > yb:
                continue
            gx, gy = np.meshgrid(np.arange(xa, xb + 1) + 0.5, np.arange(ya_, yb + 1) + 0.5)
            dx, dy = gx - o[0], gy - o[1]
            s = (dx * e2[1] - dy * e2[0]) / det
            t = (e1[0] * dy - e1[1] * dx) / det
            inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
            if not inside.any():
                continue
            depth = P[1][2] + s * (P[0][2] - P[1][2]) + t * (P[2][2] - P[1][2])
            (u_a, v_a), (u_b, v_b), (u_c, v_c) = uvs[1], uvs[0], uvs[2]
            tu = u_a + s * (u_b - u_a) + t * (u_c - u_a)
            tv = v_a + s * (v_b - v_a) + t * (v_c - v_a)
            ti = np.clip(tu.astype(int), 0, TW - 1)
            tj = np.clip(tv.astype(int), 0, TH - 1)
            col = T[tj, ti]
            ok = inside & (col[..., 3] > 0) & (depth > zb[ya_:yb + 1, xa:xb + 1])
            sub = img[ya_:yb + 1, xa:xb + 1]
            sub[ok] = col[..., :3][ok] * lum
            zb[ya_:yb + 1, xa:xb + 1][ok] = depth[ok]
        sheet.paste(Image.fromarray(np.clip(img, 0, 255).astype("uint8")), ((vi % 2) * S, (vi // 2) * S))
    sheet.save(path)


# ====================================================================== the models (pixels; the ground is at y 24, the front at -z)


def trebuchet():
    m = Model("TrebuchetMesh", "siege/trebuchet", 256,
              "A trebuchet on four wheels: a frame of beams, two uprights braced fore and aft, an iron axle on them and on it the throwing arm, iron-banded, with a box of stones at its short end and a sling at its long end; a winch at the back to wind it down and a trough for the sling.")
    frame = m.part("frame")
    for x in (-16, 13):
        frame.box("oak", x, 10, -24, 3, 4, 48)
    frame.box("oak", -13, 10, -24, 26, 4, 3)
    frame.box("oak", -13, 10, 21, 26, 4, 3)
    frame.box("oak", -13, 11, -2, 26, 3, 4)
    for x in (-16.5, 12.5):
        for z in (-18, 14):
            frame.box("dark", x, 14, z, 4, 3, 4)
    for z in (-17, 15):
        frame.box("dark", -20, 15, z, 40, 2, 2)
    for x in (-13, 9):
        frame.box("oak", x, -37, -2, 4, 47, 4)
        band(frame, x, 5, -2, 4, 2, 4)
        band(frame, x, -20, -2, 4, 2, 4)
        frame.box("iron", x - 0.5, -39, -2.5, 5, 2, 5)
    frame.box("iron", -15, -36, -1, 30, 2, 2)
    # the trough for the sling, and the winch at the back
    frame.box("planks", -4, 8, 2, 8, 2, 19, grain="z")
    frame.box("dark", -4, 6, 2, 1, 2, 19)
    frame.box("dark", 3, 6, 2, 1, 2, 19)
    for x in (-12, 10):
        frame.box("oak", x, 0, 21, 2, 10, 3)
    frame.box("dark", -10, 1, 20.5, 20, 4, 4)
    frame.box("rope", -6, 0.6, 20.1, 12, 4.8, 4.8, grain="x")
    for x in (-14.5, 12.5):
        frame.box("dark", x, -1, 22, 2, 8, 1)
        frame.box("dark", x, 2.5, 18.5, 2, 1, 8)
    for side in (-11, 11):
        for front in (-1, 1):
            brace(m, frame, f"brace{side}_{front}", (side, 10, 21 * front), (side, -24, 2.5 * front), "oak", 3)
    arm = m.part("arm", None, (0, -35, 0), (-35, 0, 0))
    arm.box("oak", -2, -2, -14, 4, 4, 30)
    arm.box("oak", -1.5, -1.5, 16, 3, 3, 40)
    arm.box("iron", -2.5, -2.5, -2.5, 5, 5, 5)
    for z in (-11, 7, 13):
        band(arm, -2, -2, z, 4, 4, 2)
    for z in (28, 44):
        band(arm, -1.5, -1.5, z, 3, 3, 2)
    arm.box("iron", -1.5, -1.5, 54, 3, 3, 2, inflate=0.3)
    weight = arm.child("weight", (0, 0, -12))
    weight.box("iron", -7.5, -0.5, -0.5, 15, 1, 1)
    for x in (-6.5, 5.5):
        weight.box("iron", x, -1, -1, 1, 5, 2)
    weight.box("crate", -7, 3, -6, 14, 12, 12)
    weight.box("stone", -6, 1.5, -5, 12, 2, 10, variant=1)
    sling = arm.child("sling", (0, 0, 55))
    sling.box("rope", -1.5, 0, -0.5, 1, 12, 1)
    sling.box("rope", 0.5, 0, -0.5, 1, 12, 1)
    sling.box("leather", -2.5, 11, -2.5, 5, 2, 5)
    stone = sling.child("stone", (0, 7.5, 0))
    stone.box("stone", -2, 0, -2, 4, 4, 4)
    for i, (x, z) in enumerate(((-21, -16), (21, -16), (-21, 16), (21, 16))):
        wheel(m, None, f"wheel{i}", (x, 16, z), 8)
    return m


def ballista():
    m = Model("BallistaMesh", "siege/ballista", 256,
              "A ballista on a four-wheeled carriage: a planked bed between rails, a post and a turntable, and on it the head that tilts to aim: the stock with its groove and rails, the frame of the two sinew springs with iron washers, the arms in the springs, the string, the slider with its claw, the bolt, and the winch at the back.")
    cart = m.part("cart")
    cart.box("planks", -9, 12, -12, 18, 2, 24, grain="z")
    for x in (-11, 9):
        cart.box("oak", x, 11, -13, 2, 4, 26)
    for z in (-11, 9):
        cart.box("dark", -14, 17, z, 28, 2, 2)
    cart.box("oak", -2, 1, -2, 4, 11, 4)
    cart.box("dark", -5, 0, -5, 10, 1, 10)
    for i, (a, b) in enumerate((((-9, 12, -8), (-1.5, 4, -1.5)), ((9, 12, -8), (1.5, 4, -1.5)), ((-9, 12, 8), (-1.5, 4, 1.5)), ((9, 12, 8), (1.5, 4, 1.5)))):
        brace(m, cart, f"strut{i}", a, b, "dark", 2)
    head = m.part("head", None, (0, -1, 0))
    head.box("oak", -2.5, -2, -16, 5, 3, 32)
    head.box("dark", -2.5, -3, -16, 1, 1, 32)
    head.box("dark", 1.5, -3, -16, 1, 1, 32)
    # the spring frame
    head.box("oak", -9, -11, -14.5, 18, 2, 5)
    head.box("oak", -9, 3, -14.5, 18, 2, 5)
    for x in (-9, 7):
        head.box("oak", x, -9, -14.5, 2, 12, 5)
    for x in (-3.5, 2.5):
        head.box("dark", x, -9, -14, 1, 12, 4)
    for x in (-7, 3.5):
        head.box("rope", x, -9, -13.5, 3.5, 12, 3, grain="y")
        head.box("iron", x - 0.5, -12, -14, 4.5, 1, 4)
        head.box("iron", x - 0.5, 5, -14, 4.5, 1, 4)
    band(head, -9, -11, -14.5, 18, 2, 5, 0.2)
    # the winch
    for x in (-5, 4):
        head.box("oak", x, -5, 11, 1, 5, 4)
    head.box("dark", -4, -4, 11.5, 8, 3, 3)
    head.box("rope", -3, -4.2, 11.3, 6, 3.4, 3.4, grain="x")
    head.box("iron", 5, -3, 12.5, 4, 1, 1)
    head.box("iron", -9, -3, 12.5, 4, 1, 1)
    head.box("iron", 8, -6, 12.5, 1, 7, 1)
    head.box("iron", -9, -6, 12.5, 1, 7, 1)
    for side, name in ((-1, "arm_left"), (1, "arm_right")):
        a = head.child(name, (5.5 * side, -3, -12))
        x0 = -14 if side < 0 else 0
        a.box("dark", x0, -1, -1, 14, 2, 2)
        a.box("iron", (-15 if side < 0 else 13), -1.5, -1.5, 2, 3, 3)
    for name in ("string_left", "string_right"):
        head.child(name, (0, -3, 0)).box("rope", 0, -0.5, -0.5, 1, 1, 1, variant=1)
    slider = head.child("slider", (0, -3, 11))
    slider.box("dark", -2, -1, -1, 4, 1, 4)
    slider.box("iron", -1, -2, -0.5, 2, 1, 1)
    bolt = slider.child("bolt")
    bolt.box("dark", -0.5, -2, -24, 1, 1, 24)
    bolt.box("iron", -1, -2.5, -27, 2, 2, 3)
    bolt.box("leather", -0.5, -3.5, -4, 1, 2, 3, variant=1)
    for i, (x, z) in enumerate(((-15, -10), (15, -10), (-15, 10), (15, 10))):
        wheel(m, None, f"wheel{i}", (x, 18, z), 6, thick=2)
    return m


def ram():
    m = Model("RamMesh", "siege/ram", 256,
              "A covered battering ram: a frame on four wheels with six posts, a ridged roof of boards under wet hides with skirts of hide down the sides, and under it a log with an iron ram's head hung on two chains from the ridge.")
    f = m.part("frame")
    for x in (-15, 12):
        f.box("oak", x, 12, -26, 3, 4, 52)
    f.box("oak", -12, 12, -26, 24, 3, 3)
    f.box("oak", -12, 12, 23, 24, 3, 3)
    f.box("oak", -12, 13, -1.5, 24, 2, 3)
    for z in (-17, 15):
        f.box("dark", -17, 17, z, 34, 2, 2)
    for x in (-14.5, 11.5):
        for z in (-24, -1.5, 21):
            f.box("oak", x, -8, z, 3, 20, 3)
    for x in (-16, 13):
        f.box("oak", x, -11, -26, 3, 3, 52)
    f.box("dark", -1.5, -24.5, -27, 3, 3, 54)
    f.box("dark", -1, -21.5, -15, 2, 3, 2)
    f.box("dark", -1, -21.5, 6, 2, 3, 2)
    for x in (-16.6, 15.6):
        f.box("hide", x, -9, -25, 1, 14, 50, grain="z")
    slope = math.degrees(math.atan2(14, 16))
    for side, name in ((-1, "roof_left"), (1, "roof_right")):
        r = m.part(name, None, (0, -23, 0), (0, 0, side * slope))
        x0 = 0 if side > 0 else -22
        r.box("planks", x0, 0, -27, 22, 1, 54, grain="z")
        r.box("hide", x0, -1, -27.5, 22, 1, 55, grain="z", variant=1)
    hanger = m.part("hanger", None, (0, -19, 0))
    for z in (-14, 7):
        hanger.box("chain", -0.5, 0, z - 0.5, 1, 15, 1)
    log = hanger.child("log", (0, 15, 0))
    log.box("dark", -3, -3, -30, 6, 6, 40)
    for z in (-24, -10, 4):
        band(log, -3, -3, z, 6, 6, 2)
    log.box("iron", -4, -4, -36, 8, 8, 6)
    log.box("iron", -2.5, -2.5, -38, 5, 5, 2, variant=1)
    for side in (-1, 1):
        log.box("iron", 4 if side > 0 else -5.5, -4.5, -35, 1.5, 4, 4, variant=2)
        log.box("iron", 4.5 if side > 0 else -6, -1, -37, 1.5, 3, 3, variant=3)
    for i, (x, z) in enumerate(((-18, -16), (18, -16), (-18, 16), (18, 16))):
        wheel(m, None, f"wheel{i}", (x, 18, z), 6)
    return m


def mangonel():
    m = Model("MangonelMesh", "siege/mangonel", 256,
              "A mangonel: a frame of beams on four wheels, a thick skein of twisted rope across it, and in the skein the throwing arm with an iron-bound cup; it is wound back by a winch and strikes a padded crossbar when it throws.")
    f = m.part("frame")
    for x in (-11, 8):
        f.box("oak", x, 10, -20, 3, 4, 40)
    f.box("oak", -8, 10, -20, 16, 4, 3)
    f.box("oak", -8, 10, 17, 16, 4, 3)
    for z in (-14, 12):
        f.box("dark", -15, 15, z, 30, 2, 2)
    # the skein and its iron washers
    f.box("rope", -8, 9.5, 1, 16, 5, 5, grain="x")
    for x in (-11.5, 8.5):
        f.box("iron", x, 9, 0.5, 3, 6, 6)
    # the crossbar the arm strikes, on two uprights braced from the front
    for x in (-11, 8):
        f.box("oak", x, -15, -6, 3, 25, 3)
    f.box("oak", -11, -18, -6, 22, 3, 3)
    f.box("hide", -5, -19, -8, 10, 5, 3, variant=1)
    band(f, -11, -17.5, -6, 22, 2, 3, 0.2)
    for x in (-9.5, 9.5):
        brace(m, f, f"prop{x}", (x, 10, -19), (x, -12, -6), "oak", 2.5)
    # the winch at the back
    for x in (-10, 8):
        f.box("oak", x, 3, 13, 2, 7, 3)
    f.box("dark", -8, 4, 13, 16, 3, 3)
    f.box("rope", -5, 3.8, 12.8, 10, 3.4, 3.4, grain="x")
    for x in (-12, 10):
        f.box("dark", x, 1, 14, 2, 9, 1)
        f.box("dark", x, 5, 10, 2, 1, 9)
    arm = m.part("arm", None, (0, 12, 3.5), (-78, 0, 0))
    arm.box("oak", -1.5, -26, -1.5, 3, 26, 3)
    arm.box("iron", -2, -3, -2, 4, 5, 4)
    band(arm, -1.5, -15, -1.5, 3, 2, 3)
    arm.box("dark", -3.5, -30, -3.5, 7, 3, 7)
    arm.box("iron", -3.5, -31, -3.5, 7, 1, 7, inflate=0.2)
    pot = arm.child("pot", (0, -31, 0))
    pot.box("clay", -2, -3, -2, 4, 3, 4)
    pot.box("pitch", -1, -4, -1, 2, 1, 2)
    for i, (x, z) in enumerate(((-14, -13), (14, -13), (-14, 13), (14, 13))):
        wheel(m, None, f"wheel{i}", (x, 19, z), 5)
    return m


def bombard():
    m = Model("BombardMesh", "siege/bombard", 256,
              "A bombard on a wheeled carriage: two cheeks with transoms and iron straps, a trail to the ground, two great wheels, and on trunnions between the cheeks the barrel of welded iron bars bound with hoops, its chamber behind.")
    c = m.part("carriage")
    for x in (-8, 5):
        c.box("oak", x, 6, -8, 3, 8, 24)
        band(c, x, 6, -6, 3, 8, 2)
        band(c, x, 6, 10, 3, 8, 2)
    c.box("oak", -5, 10, -6, 10, 3, 3)
    c.box("oak", -5, 11, 11, 10, 3, 3)
    c.box("dark", -14, 14, 3, 28, 2, 2)
    brace(m, c, "trail", (0, 13, 14), (0, 22.5, 38), "oak", 5, 4)
    c.box("oak", -3, 2, 9, 6, 3, 7, variant=1)
    barrel = m.part("barrel", None, (0, 3, 2))
    barrel.box("muzzle", -5, -5, -22, 10, 10, 28)
    b45 = barrel.child("barrel45", (0, 0, 0), (0, 0, 45))
    b45.box("iron", -4.6, -4.6, -21.9, 9.2, 9.2, 27.8, grain="z", variant=1)
    for z in (-22, -15, -8, -1):
        band(barrel, -5, -5, z, 10, 10, 2, 0.45)
    barrel.box("iron", -3.5, -3.5, 6, 7, 7, 10)
    band(barrel, -3.5, -3.5, 13, 7, 7, 2, 0.35)
    barrel.box("iron", -6.5, -1, -1, 13, 2, 2, variant=2)
    barrel.box("pitch", -0.5, -4, 9, 1, 1, 1)
    for i, x in enumerate((-12, 12)):
        wheel(m, None, f"wheel{i}", (x, 15, 4), 9, thick=3, spokes=6)
    return m


def tower():
    m = Model("SiegeTowerMesh", "siege/siege_tower", 256,
              "A siege tower on four wheels: four corner posts, three floors, walls of boards with battens, wet hides over the front, a ladder up through it seen through the door at the back, a crenellated parapet at the top and a drawbridge before it that is let down onto the wall.")
    f = m.part("frame")
    for x in (-20, 16):
        f.box("oak", x, 12, -20, 4, 4, 40)
    for z in (-20, 16):
        f.box("oak", -16, 12, z, 32, 4, 4)
    for z in (-14, 10):
        f.box("dark", -22, 17, z, 44, 2, 2)
    for x in (-20, 16):
        for z in (-20, 16):
            f.box("oak", x, -64, z, 4, 76, 4)
    f.box("planks", -16, 10, -16, 32, 2, 32, grain="x")
    f.box("planks", -20, -22, -20, 40, 2, 40, grain="x", variant=1)
    f.box("planks", -20, -66, -20, 40, 2, 40, grain="x", variant=2)
    # walls: boards on the sides, hides on the front, the back with its door
    for x in (-20.8, 19.8):
        f.box("planks", x, -62, -16, 1, 72, 32, grain="y")
    f.box("hide", -16, -62, -21.2, 32, 72, 1)
    f.box("planks", -16, -62, 20, 32, 50, 1, grain="y", variant=1)
    f.box("planks", -16, -12, 20, 11, 22, 1, grain="y", variant=2)
    f.box("planks", 5, -12, 20, 11, 22, 1, grain="y", variant=2)
    for y in (-26, -58, 6):
        for x in (-21.6, 20.6):
            f.box("dark", x, y, -20, 1, 3, 40)
        f.box("dark", -20, y, 20.6, 40, 3, 1)
    # the ladder inside, behind the door
    f.box("oak", -3, -64, 16, 1, 74, 1)
    f.box("oak", 2, -64, 16, 1, 74, 1)
    for y in range(-60, 10, 6):
        f.box("dark", -2, y, 16, 4, 1, 1, variant=1)
    # the parapet
    for x in (-20.5, 19):
        f.box("planks", x, -72, -20, 1.5, 6, 40, grain="z", variant=3)
        for z in (-18, -6, 6):
            f.box("planks", x, -76, z, 1.5, 4, 6, grain="y", variant=4)
    f.box("planks", -19, -72, 18.5, 38, 6, 1.5, variant=5)
    for x in (-17, -5, 7):
        f.box("planks", x, -76, 18.5, 6, 4, 1.5, grain="y", variant=4)
    bridge = m.part("bridge", None, (0, -64, -20))
    bridge.box("planks", -14, -26, -2, 28, 26, 2, grain="y", variant=6)
    for y in (-24, -13, -3):
        bridge.box("dark", -14, y, -2.8, 28, 2, 1)
    bridge.box("iron", -14, -27, -2.2, 28, 1, 2.4, variant=1)
    for x in (-15, 13):
        bridge.box("chain", x, -26, -1, 2, 1, 1, variant=1)
    for i, (x, z) in enumerate(((-23, -12), (23, -12), (-23, 12), (23, 12))):
        wheel(m, None, f"wheel{i}", (x, 17, z), 7, thick=3)
    return m


def mantlet():
    m = Model("MantletMesh", "siege/mantlet", 256,
              "A mobile wall: upright boards with loopholes, iron-banded, on an axle with two wheels at the foot, held up by two props behind; men walk behind it and shoot through the slits.")
    w = m.part("wall")
    w.box("loop_planks", -22, -18, -3, 44, 34, 3, grain="y")
    for y in (-15, 9):
        w.box("dark", -23, y, -4, 46, 3, 1)
        w.box("dark", -23, y, 0, 46, 3, 1, variant=1)
    w.box("dark", -23, -21, -4, 46, 3, 5, variant=2)
    for x in (-23, 21):
        w.box("oak", x, -21, -4, 2, 37, 5)
    for x in (-18, 16):
        brace(m, w, f"prop{x}", (x + 1, -10, 0), (x + 1, 23, 20), "oak", 2.5)
    w.box("dark", -17, 15, -2, 34, 2, 2)
    w.box("hide", -20, -26, -3.5, 40, 6, 1, variant=1)
    for i, x in enumerate((-19, 19)):
        wheel(m, None, f"wheel{i}", (x, 18, -1), 6)
    return m


def elephant():
    m = Model("ElephantMesh", "war_elephant", 256,
              "A war elephant of India: a heavy body rising to the shoulders, wrinkled grey skin, pillar legs with broad feet and toenails, a domed head with an iron brow plate, fanning ears, a trunk of five tapering pieces and curved tusks; over it a crimson caparison with a tarnished gold border and tassels, a girth, and a howdah of dark wood with a railing, posts and a canopy.")
    body = m.part("body")
    # a rounded bulk: a core and boxes over it a little narrower and taller, or wider and lower
    body.box("skin", -10, -6, -14, 20, 16, 28)
    body.box("skin", -9, -9, -13, 18, 21, 26, variant=1)
    body.box("skin", -10.8, -4, -12, 21.6, 12, 24, variant=2)
    body.box("skin", -8.5, -10.5, -11, 17, 3, 19, variant=3)
    body.box("skin", -8, -3, -15.6, 16, 12, 2, variant=4)
    body.box("skin", -8, -4, 13.6, 16, 13, 2, variant=5)
    body.box("skin", -7.5, 11.5, -9, 15, 2, 18, variant=6)
    # the caparison: over the back and down the sides, with a gold border and tassels
    body.box("crimson", -10, -11.2, -9.5, 20, 1, 18, grain="z")
    for x in (-11.5, 10.9):
        body.box("crimson", x, -10.6, -9.5, 0.6, 15, 18, grain="z", variant=1)
        for z in range(-9, 8, 3):
            body.box("gold", x, 4.4, z + 0.5, 0.6, 2, 1, variant=1)
    body.box("leather", -11.2, -4, -1, 22.4, 1.5, 2, inflate=0.1, variant=1)
    body.box("leather", -10.2, 11.6, -1, 20.4, 1, 2, variant=2)
    tail = body.child("tail", (0, -5, 15.5), (14, 0, 0))
    tail.box("skin", -0.5, 0, -0.5, 1, 12, 1, variant=6)
    tail.child("tuft", (0, 12, 0)).box("hair", -1, 0, -1, 2, 3, 2)
    # the howdah
    h = body.child("howdah", (0, -11.2, 1))
    h.box("dark_planks", -9, -2, -8, 18, 2, 16, grain="x")
    for z in (-8.5, 7.5):
        h.box("dark", -9, -7, z, 18, 1, 1)
        h.box("dark", -9, -3, z, 18, 1, 1, variant=1)
        for x in range(-7, 8, 3):
            h.box("dark", x, -6, z + 0.25, 0.6, 3, 0.6, variant=2)
    for x in (-9.5, 8.5):
        h.box("dark", x, -7, -8, 1, 1, 16)
        h.box("dark", x, -3, -8, 1, 1, 16, variant=1)
        for z in range(-6, 7, 3):
            h.box("dark", x + 0.25, -6, z, 0.6, 3, 0.6, variant=2)
    for x in (-9.5, 8.5):
        for z in (-8.5, 7.5):
            h.box("dark", x, -18, z, 1, 16, 1, grain="y")
            h.box("gold", x - 0.2, -19, z - 0.2, 1.4, 1, 1.4, variant=2)
    h.box("canopy", -10.5, -20, -9.5, 21, 1, 19, grain="x")
    h.box("canopy", -8, -22, -7, 16, 2, 14, variant=1)
    h.box("canopy", -5, -24, -4, 10, 2, 8, variant=2)
    h.box("gold", -1, -27, -1, 2, 3, 2, variant=3)
    for x in (-10.4, 9.8):
        h.box("canopy", x, -19, -9.5, 0.6, 3, 19, grain="z", variant=3)
    head = body.child("head", (0, -3, -16))
    head.box("skin", -6.5, -4, -9, 13, 11, 9, variant=7)
    head.box("skin", -5.5, -8, -7.5, 5.2, 4, 7, variant=8)
    head.box("skin", 0.3, -8, -7.5, 5.2, 4, 7, variant=8)
    head.box("skin_pink", -4, -3, -9.8, 8, 9, 1)
    head.box("skin", -3, 7, -6, 6, 2.5, 5, variant=9)
    for x in (-6.7, 6.2):
        head.box("eye", x, -1, -6.5, 0.5, 1, 1.5)
    # a steel brow plate edged with gold, a short spike on it
    head.box("gold", -4, -8.5, -10.4, 8, 8, 0.6, variant=4)
    head.box("steel", -3.5, -8, -10.9, 7, 7, 0.7)
    head.box("steel", -0.5, -5, -12.2, 1, 1, 1.4, variant=1)
    head.box("crimson", -6, -8.6, -4, 12, 1, 5, variant=2)
    for side, name in ((1, "ear_left"), (-1, "ear_right")):
        e = head.child(name, (6.5 * side, -4, -5), (0, 30 * side, 0))
        x0 = 0 if side > 0 else -1
        e.box("ear", x0, 0, 0, 1, 13, 11, mirror=side < 0)
    t = head.child("trunk1", (0, 5, -7.5), (8, 0, 0))
    t.box("trunk", -2.5, 0, -2.5, 5, 6, 5)
    t = t.child("trunk2", (0, 5.6, 0), (6, 0, 0))
    t.box("trunk", -2.2, 0, -2.2, 4.4, 6, 4.4, variant=1)
    t = t.child("trunk3", (0, 5.6, 0), (6, 0, 0))
    t.box("trunk", -1.9, 0, -1.9, 3.8, 5, 3.8, variant=2)
    t = t.child("trunk4", (0, 4.6, 0), (-12, 0, 0))
    t.box("trunk", -1.6, 0, -1.6, 3.2, 4, 3.2, variant=3)
    t = t.child("trunk5", (0, 3.6, 0), (-20, 0, 0))
    t.box("trunk", -1.3, 0, -1.3, 2.6, 3, 2.6, variant=4)
    t.box("skin_pink", -1.4, 2.6, -1.4, 2.8, 0.8, 2.8, variant=1)
    for side, name in ((1, "tusk_left"), (-1, "tusk_right")):
        k = head.child(name, (3.6 * side, 6, -6.5), (55, -10 * side, 0))
        k.box("ivory", -1.1, -1.1, -5, 2.2, 2.2, 5)
        k = k.child(name + "_mid", (0, 0, -4.7), (-35, 0, 0))
        k.box("ivory", -0.9, -0.9, -4, 1.8, 1.8, 4, variant=1)
        k = k.child(name + "_tip", (0, 0, -3.8), (-35, 0, 0))
        k.box("ivory", -0.7, -0.7, -3, 1.4, 1.4, 3, variant=2)
        k.box("gold", -1, -1, -0.3, 2, 2, 1, inflate=0.1, variant=5)
    for name, x, z in (("leg_front_left", 6, -9.5), ("leg_front_right", -6, -9.5), ("leg_back_left", 6, 9.5), ("leg_back_right", -6, 9.5)):
        leg = m.part(name, None, (x, 10, z))
        leg.box("skin", -4, 0, -4, 8, 7, 8, variant=10)
        leg.box("skin", -3.5, 7, -3.5, 7, 5, 7, variant=11)
        leg.box("skin", -4, 12, -4, 8, 2, 8, variant=12)
        for tx in (-2.5, 0.5):
            leg.box("toenail", tx, 12.6, -4.3, 2, 1.4, 0.6)
    return m


MODELS = [trebuchet, ballista, ram, mangonel, bombard, tower, mantlet, elephant]


def main():
    preview_dir = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    os.makedirs(JAVA, exist_ok=True)
    os.makedirs(os.path.join(TEXTURES, "siege"), exist_ok=True)
    for make in MODELS:
        model = make()
        unique = layout(model)
        tex = paint(model, unique)
        tex.save(os.path.join(TEXTURES, model.texture + ".png"))
        with open(os.path.join(JAVA, model.cls + ".java"), "w") as f:
            f.write(java(model))
        print(f"{model.cls}: {len(model.parts)} parts, {sum(len(p.boxes) for p in model.parts)} boxes, texture {model.width}x{model.height}")
        if preview_dir:
            os.makedirs(preview_dir, exist_ok=True)
            preview(model, tex, os.path.join(preview_dir, model.cls + ".png"))


if __name__ == "__main__":
    main()
