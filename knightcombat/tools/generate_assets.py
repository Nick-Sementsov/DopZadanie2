#!/usr/bin/env python3
"""Generates the weapon textures, item models, sounds.json and recipes of the Knightly Combat mod.

Run from the repository root:  python3 knightcombat/tools/generate_assets.py [preview.png]
Requires Pillow and NumPy (pip install pillow numpy). Files are written into src/main/resources
and overwrite earlier versions, so edit this script rather than the generated files.

Weapons are drawn along the diagonal of the texture, from the bottom left corner (pommel or butt)
to the top right corner (tip), like Minecraft's own swords. Shapes are given in "uv" coordinates:
u runs along that diagonal and v across it (positive v is the upper left side), both in pixels.
"""

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

MODID = "knightcombat"
ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / MODID
DATA = RESOURCES / "data" / MODID
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


def spikes(sprite, centre_u, centre_v, radius, angles, length, material="steel"):
    for angle in angles:
        a = math.radians(angle)
        for step in np.arange(radius, radius + length, 0.5):
            sprite.dot(centre_u + step * math.cos(a), centre_v + step * math.sin(a), material, 0.6)


# --------------------------------------------------------------------------- weapons
# Every weapon has an icon (16x16) and the texture held in hand: the icon itself for short weapons,
# a 32x32 texture for long ones. GRIPS gives the u coordinate of the hand on the held texture.

def dagger(size=16):
    s = Sprite(16)
    s.disc(5.6, 0, 0.95, "gold")
    s.band(6.3, 9.4, 0.4, "leather", centre=0.35, style="wrap")
    s.cross(9.4, 10.1, -1.9, 1.9, "gold")
    s.band(10.1, 19.4, 0.75, "steel", taper=3.0, min_width=0.2)
    return s


def falchion(size=16):
    s = Sprite(16)
    s.disc(2.0, 0, 0.8, "iron")
    s.band(2.6, 6.4, 0.4, "leather", centre=0.35, style="wrap")
    s.cross(6.4, 7.2, -2.2, 2.2, "iron")
    s.poly([(7.2, 0.75), (18.8, 0.75), (21.4, 0.3), (20.6, -1.3), (17.6, -2.4), (12.5, -1.9), (7.2, -0.75)], "steel")
    return s


def morning_star(size=16):
    s = Sprite(16)
    s.disc(1.6, 0, 0.75, "iron")
    s.band(2.0, 13.4, 0.4, "wood", centre=0.35)
    s.band(2.0, 5.6, 0.4, "leather", centre=0.35, style="wrap")
    s.cross(12.4, 13.4, -1.05, 1.05, "iron")
    s.disc(16.2, 0, 2.7, "iron")
    spikes(s, 16.2, 0, 2.6, [0, 60, 120, 180 + 60, 300, 90 + 180 - 30], 1.2)
    s.dot(17.0, 0.9, "steel", 1.0)
    return s


def longsword(size):
    if size == 16:
        s = Sprite(16)
        s.disc(2.0, 0, 1.0, "gold")
        s.band(2.7, 7.4, 0.4, "leather", centre=0.35, style="wrap")
        s.cross(7.4, 8.5, -3.2, 3.2, "gold")
        s.band(8.5, 21.9, 0.75, "steel", taper=2.6, min_width=0.2)
        return s
    s = Sprite(32)
    s.disc(4.4, 0, 1.9, "gold")
    s.band(5.9, 16.2, 0.95, "leather", style="wrap")
    s.cross(16.2, 18.3, -6.6, 6.6, "gold")
    s.disc(17.2, 6.7, 1.1, "gold")
    s.disc(17.2, -6.7, 1.1, "gold")
    s.band(18.3, 44.6, 1.5, "steel", taper=5.0, min_width=0.3)
    s.band(19.0, 36.0, 0.36, "fuller")
    return s


def zweihander(size):
    if size == 16:
        s = Sprite(16)
        s.disc(1.6, 0, 1.0, "iron")
        s.band(2.3, 7.4, 0.4, "leather", centre=0.35, style="wrap")
        s.cross(7.4, 8.5, -3.6, 3.6, "iron")
        s.band(8.5, 10.6, 0.4, "steel", centre=0.35)
        s.cross(10.6, 11.2, -1.5, 1.5, "iron")
        s.band(11.2, 22.2, 0.75, "steel", taper=2.4, min_width=0.2)
        return s
    s = Sprite(32)
    s.disc(2.9, 0, 2.0, "iron")
    s.band(4.4, 15.4, 0.95, "leather", style="wrap")
    s.cross(15.4, 17.6, -6.2, 6.2, "iron")
    s.disc(17.4, 6.4, 1.2, "iron")
    s.disc(17.4, -6.4, 1.2, "iron")
    s.band(17.6, 22.6, 1.05, "steel")
    s.poly([(21.4, 1.0), (22.8, 3.1), (23.6, 1.0)], "iron")
    s.poly([(21.4, -1.0), (22.8, -3.1), (23.6, -1.0)], "iron")
    s.band(22.6, 45.0, 1.6, "steel", taper=4.6, min_width=0.3)
    s.band(23.4, 38.0, 0.36, "fuller")
    return s


def battle_axe(size):
    if size == 16:
        s = Sprite(16)
        s.band(0.9, 19.6, 0.4, "wood", centre=0.35)
        s.band(0.9, 5.2, 0.4, "leather", centre=0.35, style="wrap")
        s.poly([(13.0, 0.3), (15.6, 0.3), (15.8, 1.6), (18.0, 4.0), (17.2, 5.3), (13.8, 5.7), (10.6, 5.0), (10.6, 3.8), (12.8, 1.6)], "iron")
        s.cross(13.4, 15.2, -1.6, -0.3, "iron")
        s.cross(19.0, 20.2, -0.75, 0.75, "iron")
        return s
    s = Sprite(32)
    s.band(1.6, 41.6, 0.9, "wood")
    s.band(6.0, 18.0, 0.95, "leather", style="wrap")
    s.cross(1.4, 3.0, -1.1, 1.1, "iron")
    s.poly([(30.0, 0.8), (35.4, 0.8), (35.8, 3.6), (39.6, 8.0), (38.6, 10.6), (33.0, 11.6), (27.0, 10.8),
            (25.6, 8.4), (29.4, 3.6)], "iron")
    s.poly([(26.6, 8.6), (39.0, 8.8), (38.4, 10.6), (33.0, 11.6), (27.2, 10.8)], "steel")
    s.cross(30.6, 34.8, -3.0, -0.8, "iron")
    s.cross(40.6, 43.0, -1.3, 1.3, "iron")
    s.dot(32.6, 4.4, "steel", 1.0)
    return s


def war_hammer(size):
    if size == 16:
        s = Sprite(16)
        s.band(0.9, 18.4, 0.4, "wood", centre=0.35)
        s.band(0.9, 5.2, 0.4, "leather", centre=0.35, style="wrap")
        s.cross(13.6, 17.0, 0.4, 3.6, "iron")
        s.cross(13.6, 17.0, 3.0, 3.6, "steel")
        s.poly([(14.0, -0.4), (16.6, -0.4), (15.4, -4.2)], "iron")
        s.poly([(17.0, -0.7), (17.0, 0.7), (20.6, 0.0)], "steel")
        return s
    s = Sprite(32)
    s.band(1.6, 38.0, 0.9, "wood")
    s.band(6.0, 18.0, 0.95, "leather", style="wrap")
    s.cross(1.4, 3.0, -1.1, 1.1, "iron")
    s.cross(30.0, 38.0, -1.2, 1.2, "iron")
    s.cross(32.4, 38.6, 1.0, 7.0, "iron")
    s.cross(32.4, 38.6, 6.0, 7.6, "steel")
    s.poly([(33.0, -1.0), (38.0, -1.0), (35.6, -8.8)], "iron")
    s.poly([(37.6, -1.6), (37.6, 1.6), (44.8, 0.0)], "steel")
    s.dot(34.4, 4.0, "steel", 1.0)
    return s


def halberd(size):
    if size == 16:
        s = Sprite(16)
        s.band(0.5, 18.8, 0.4, "wood", centre=0.35)
        s.poly([(14.2, -0.3), (16.6, -0.3), (17.6, -3.4), (16.4, -4.6), (12.6, -4.4), (13.6, -2.0)], "iron")
        s.poly([(15.0, 0.4), (16.4, 0.4), (17.6, 3.4), (16.4, 2.0)], "iron")
        s.band(18.6, 22.4, 0.75, "steel", taper=2.6, min_width=0.2)
        return s
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


def spear(size):
    if size == 16:
        s = Sprite(16)
        s.band(0.5, 16.8, 0.4, "wood", centre=0.35)
        s.band(15.8, 17.4, 0.75, "iron")
        s.poly([(17.2, 0.0), (18.8, 1.8), (22.3, 0.0), (18.8, -1.8)], "steel")
        return s
    s = Sprite(32)
    s.band(0.8, 35.6, 0.9, "wood")
    s.band(9.0, 17.0, 0.95, "leather", style="wrap")
    s.cross(0.6, 2.2, -1.1, 1.1, "iron")
    s.band(33.6, 37.0, 1.3, "iron")
    s.poly([(35.4, 1.3), (36.6, 3.4), (37.4, 1.3)], "iron")
    s.poly([(35.4, -1.3), (36.6, -3.4), (37.4, -1.3)], "iron")
    s.poly([(36.8, 0.0), (39.6, 2.9), (45.0, 0.0), (39.6, -2.9)], "steel")
    s.band(37.6, 43.0, 0.36, "fuller")
    return s


WEAPONS = {
    # name: (drawing, size of the held texture, u of the hand on the held texture)
    "dagger": (dagger, 16, 8.0),
    "falchion": (falchion, 16, 5.0),
    "morning_star": (morning_star, 16, 4.6),
    "longsword": (longsword, 32, 14.2),
    "zweihander": (zweihander, 32, 13.6),
    "battle_axe": (battle_axe, 32, 14.0),
    "war_hammer": (war_hammer, 32, 14.0),
    "halberd": (halberd, 32, 13.0),
    "spear": (spear, 32, 13.0),
}


# --------------------------------------------------------------------------- models

def grip_pixel(size, u):
    """The hand point (x, y from the top left, in pixels) of a texture held at u on the diagonal."""
    x = u / SQ2
    return x, size - x


def euler_xyz(rx, ry, rz):
    """Rotation matrix of a model display transform (rotations about X, then Y, then Z, as in ItemTransform)."""
    def rot(axis, deg):
        a = math.radians(deg)
        c, s = math.cos(a), math.sin(a)
        if axis == "x":
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
        if axis == "y":
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return rot("x", rx) @ rot("y", ry) @ rot("z", rz)


def held_display(size, u):
    """Hand transforms that put the grip of the texture where Minecraft puts the grip of its swords."""
    gx, gy = grip_pixel(size, u)
    grip = np.array([gx / size - 0.5, (size - gy) / size - 0.5, 0.0])
    factor = size / 16

    def transform(rotation, scale, mirror):
        r = euler_xyz(*rotation)
        s = np.diag(scale)
        translation = -(r @ s @ grip) * 16
        if mirror:
            translation[0] = -translation[0]
            rotation = (rotation[0], -rotation[1], -rotation[2])
        return {"rotation": [round(a, 3) for a in rotation],
                "translation": [round(float(t), 3) for t in translation],
                "scale": [round(float(c), 3) for c in scale]}

    third = ((0, -90, 55), (0.85 * factor, 0.85 * factor, 0.85))
    first = ((0, -90, 25), (0.68 * factor, 0.68 * factor, 0.68))
    return {
        "thirdperson_righthand": transform(*third, False),
        "thirdperson_lefthand": transform(*third, True),
        "firstperson_righthand": transform(*first, False),
        "firstperson_lefthand": transform(*first, True),
    }


def models():
    out = {}
    for name, (draw, held_size, grip_u) in WEAPONS.items():
        icon = draw(16).render()
        save_png(icon, f"textures/item/{name}.png")
        display = held_display(held_size, grip_u)
        if held_size == 16:
            save_json(ASSETS / f"models/item/{name}.json", {
                "parent": "minecraft:item/handheld",
                "textures": {"layer0": f"{MODID}:item/{name}"},
                "display": display,
            })
            out[name] = (icon, icon)
        else:
            held = draw(held_size).render()
            save_png(held, f"textures/item/{name}_held.png")
            save_json(ASSETS / f"models/item/{name}_held.json", {
                "parent": "minecraft:item/handheld",
                "textures": {"layer0": f"{MODID}:item/{name}_held"},
                "display": display,
            })
            save_json(ASSETS / f"models/item/{name}_icon.json", {
                "parent": "minecraft:item/handheld",
                "textures": {"layer0": f"{MODID}:item/{name}"},
            })
            icon_model = {"parent": f"{MODID}:item/{name}_icon"}
            save_json(ASSETS / f"models/item/{name}.json", {
                "loader": "forge:separate_transforms",
                "base": {"parent": f"{MODID}:item/{name}_held"},
                "perspectives": {"gui": icon_model, "fixed": icon_model, "ground": icon_model},
            })
            out[name] = (icon, held)
        gx, gy = grip_pixel(held_size, grip_u)
        print(f"{name:13s} held {held_size}px, grip ({gx:.1f}f, {gy:.1f}f)")
    return out


# --------------------------------------------------------------------------- sounds and recipes

def sound(name, volume=1.0, pitch=1.0):
    return {"name": f"minecraft:{name}", "volume": volume, "pitch": pitch}


def sounds():
    def event(subtitle, *entries):
        return {"subtitle": f"subtitles.{MODID}.{subtitle}", "sounds": list(entries)}

    data = {
        "weapon.whoosh": event("weapon.whoosh",
                               *[sound(f"entity/player/attack/sweep{i}", 0.55, 1.15) for i in range(1, 8)],
                               *[sound(f"entity/player/attack/weak{i}", 0.7, 0.9) for i in range(1, 5)]),
        "weapon.hit": event("weapon.hit", *[sound(f"entity/player/attack/strong{i}", 1.0, 0.95) for i in range(1, 7)]),
        "weapon.block": event("weapon.block",
                              sound("random/anvil_use", 0.35, 1.45),
                              *[sound(f"item/shield/block{i}", 0.8, 1.3) for i in range(1, 6)]),
        "weapon.parry": event("weapon.parry", sound("random/anvil_land", 0.3, 1.85), sound("random/anvil_use", 0.45, 1.7)),
        "weapon.guard_break": event("weapon.guard_break", sound("random/anvil_break", 0.5, 1.25), sound("random/anvil_land", 0.45, 1.2)),
        "weapon.wall": event("weapon.wall",
                             *[sound(f"dig/stone{i}", 1.0, 0.75) for i in range(1, 5)],
                             sound("random/anvil_use", 0.25, 1.9)),
        "weapon.feint": event("weapon.feint", *[sound(f"entity/player/attack/weak{i}", 0.5, 1.3) for i in range(1, 5)]),
        "combat.kick": event("combat.kick", *[sound(f"entity/player/attack/knockback{i}", 1.0, 0.85) for i in range(1, 5)]),
        "combat.battle_cry": event("combat.battle_cry",
                                   *[sound(f"mob/pillager/celebrate{i}", 1.0, 0.85) for i in range(1, 5)],
                                   *[sound(f"mob/vindication_illager/celebrate{i}", 1.0, 0.9) for i in range(1, 3)]),
    }
    save_json(ASSETS / "sounds.json", data)


def shaped(name, pattern, key):
    save_json(DATA / f"recipes/{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": pattern,
        "key": {k: {"item": v} if ":" in v else {"tag": v[1:]} for k, v in key.items()},
        "result": {"item": f"{MODID}:{name}"},
    })


def recipes():
    iron, stick, nugget, block, leather, gold = ("minecraft:iron_ingot", "minecraft:stick", "minecraft:iron_nugget",
                                                 "minecraft:iron_block", "minecraft:leather", "minecraft:gold_ingot")
    shaped("dagger", ["I", "L"], {"I": iron, "L": leather})
    shaped("falchion", [" II", " I ", "L  "], {"I": iron, "L": leather})
    shaped("longsword", ["  I", "GI ", "LG "], {"I": iron, "G": gold, "L": leather})
    shaped("zweihander", ["  I", "IB ", "LI "], {"I": iron, "B": block, "L": leather})
    shaped("battle_axe", ["III", "IS ", " S "], {"I": iron, "S": stick})
    shaped("morning_star", ["NIN", " S ", " L "], {"N": nugget, "I": iron, "S": stick, "L": leather})
    shaped("war_hammer", ["IBI", " S ", " S "], {"I": iron, "B": block, "S": stick})
    shaped("halberd", [" II", " SI", "S  "], {"I": iron, "S": stick})
    shaped("spear", ["  I", " S ", "S  "], {"I": iron, "S": stick})


# --------------------------------------------------------------------------- helpers and main

def save_png(image, relative):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def preview(images, path):
    """Icons and held textures side by side, enlarged, on a grey background."""
    cell = 160
    sheet = Image.new("RGBA", (cell * 2 + 30, (cell + 20) * len(images) + 10), (150, 150, 150, 255))
    for row, (icon, held) in enumerate(images.values()):
        y = 10 + row * (cell + 20)
        sheet.alpha_composite(icon.resize((cell, cell), Image.NEAREST), (10, y))
        sheet.alpha_composite(held.resize((cell, cell), Image.NEAREST), (cell + 20, y))
    sheet.save(path)


def logo(images):
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
    sword = images["longsword"][1].resize((96, 96), Image.NEAREST)
    polearm = images["halberd"][1].resize((96, 96), Image.NEAREST).transpose(Image.FLIP_LEFT_RIGHT)
    image.alpha_composite(polearm, (16, 14))
    image.alpha_composite(sword, (16, 14))
    image.save(RESOURCES / "logo.png")


def main():
    images = models()
    sounds()
    recipes()
    logo(images)
    if len(sys.argv) > 1:
        preview(images, sys.argv[1])


if __name__ == "__main__":
    main()
