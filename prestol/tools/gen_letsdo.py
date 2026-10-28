"""Generates the brewer and winemaker content of Prestol from the installed mods.

  python3 -I tools/gen_letsdo.py <minecolonies.jar> <letsdo-brewery.jar> <letsdo-vinery.jar>

Writes into src/main/resources:
  blueprints/prestol/brewery, blueprints/prestol/winery  - structure packs (3 levels each), made from the
      MineColonies "medieval oak" kitchen and composter by swapping the hut block (MineColonies blueprints, GPL-3.0);
      the pack pictures are drawn here on Prestol's own scroll (needs Pillow)
  packs/letsdo_brewery, packs/letsdo_vinery              - built-in data packs with the MineColonies crafter
      recipes made from the Let's Do brewing, drying, mashing, pressing and fermentation recipes; Prestol turns
      each one on only when its Let's Do mod is installed
"""
import json
import math
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

OUT = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources'
STYLE = 'blueprints/minecolonies/medievaloak'
HUTS = {
    # our building: (source blueprint path without level, source hut block, source building type, pack, pack mods)
    'brewer': ('craftsmanship/luxury/cookery', 'minecolonies:blockhutkitchen', 'minecolonies:kitchen', 'brewery', 'brewery'),
    'winemaker': ('agriculture/horticulture/composter', 'minecolonies:blockhutcomposter', 'minecolonies:composter', 'winery', 'vinery'),
}
PACK_TEXT = {
    'brewery': ('Prestol: Brewery', 'The brewer hut of Prestol for Let\'s Do Brewery, built in the medieval oak style.'),
    'winery': ('Prestol: Winery', 'The winemaker hut of Prestol for Let\'s Do Vinery, built in the medieval oak style.'),
}
LEVELS = 3


def blueprints(mc):
    for hut, (src, block, btype, pack, mod) in HUTS.items():
        folder = OUT / 'blueprints' / 'prestol' / pack
        folder.mkdir(parents=True, exist_ok=True)
        name, desc = PACK_TEXT[pack]
        (folder / 'pack.json').write_text(json.dumps({
            'icon': 'icon.png', 'name': name, 'authors': ['MineColonies team (buildings)', 'Prestol'], 'desc': desc,
            'mods': ['structurize', 'minecolonies', 'prestol', mod], 'version': '1', 'pack-format': '1',
        }, indent=2) + '\n')
        for level in range(1, LEVELS + 1):
            title, root = nbt.read(mc.read(f'{STYLE}/{src}{level}.blueprint'))
            swapped = 0
            for state in root.v['palette'].v:
                if state.v['Name'].v == block:
                    state.v['Name'].v = f'prestol:blockhut{hut}'
                    swapped += 1
            for te in root.v['tile_entities'].v:
                if 'type' in te.v and te.v['type'].v == btype:
                    te.v['type'].v = f'prestol:{hut}'
                    for k in ('path', 'pack'):
                        te.v.pop(k, None)
            assert swapped == 1, f'{hut}{level}: hut block not found'
            root.v['name'].v = f'{hut}{level}.blueprint'
            mods = root.v['required_mods'].v
            for m in ('prestol', mod):
                if all(x.v != m for x in mods):
                    mods.append(nbt.Tag(nbt.STR, m))
            sub = folder / 'craftsmanship'
            sub.mkdir(exist_ok=True)
            (sub / f'{hut}{level}.blueprint').write_bytes(nbt.write(title, root))
        for icon in ('icon.png', 'icon_disabled.png'):
            (folder / 'craftsmanship' / icon).write_bytes(mc.read(f'{STYLE}/craftsmanship/{icon}'))
        pack_icon(pack, folder / 'icon.png')


def pack_icon(pack, out):
    """A 400x400 picture for the build tool: the drink of the pack on the Prestol scroll."""
    from PIL import Image, ImageDraw, ImageFont

    res = OUT / 'assets' / 'prestol'
    scroll = Image.open(res / 'textures' / 'gui' / 'scroll.png').convert('RGBA')
    img = Image.new('RGBA', (400, 400), (58, 40, 26, 255))
    img.alpha_composite(scroll.resize((400, 280), Image.LANCZOS), (0, 60))
    art = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    if pack == 'brewery':
        # a wooden mug with a foamy head
        for x in range(9, 22):
            d.line([(x, 11), (x, 27)], fill=(122, 82, 46) if x % 3 else (92, 60, 32))
        for y in (14, 24):
            d.line([(9, y), (21, y)], fill=(150, 150, 150))
        d.rectangle([22, 14, 26, 15], fill=(92, 60, 32))
        d.rectangle([25, 14, 26, 23], fill=(92, 60, 32))
        d.rectangle([22, 22, 26, 23], fill=(92, 60, 32))
        for cx, cy, r in ((11, 9, 3), (15, 8, 3), (19, 9, 3), (13, 11, 2), (18, 11, 2)):
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(246, 238, 214))
        title = 'Пивоварня'
    else:
        # a bunch of grapes under a vine leaf
        d.line([(16, 4), (16, 9)], fill=(84, 58, 30), width=1)
        d.polygon([(17, 5), (24, 2), (26, 7), (20, 9)], fill=(70, 128, 52))
        for cx, cy in ((11, 12), (15, 12), (19, 12), (23, 12), (13, 16), (17, 16), (21, 16), (15, 20), (19, 20), (17, 24)):
            d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=(92, 34, 84), outline=(60, 18, 56))
            d.point((cx - 1, cy - 1), fill=(170, 110, 160))
        title = 'Винодельня'
    img.alpha_composite(art.resize((192, 192), Image.NEAREST), (104, 82))
    font = ImageFont.truetype(str(res / 'font' / 'title.ttf'), 44)
    d = ImageDraw.Draw(img)
    w = d.textlength(title, font=font)
    d.text(((400 - w) / 2, 4), title, font=font, fill=(236, 214, 160))
    small = ImageFont.truetype(str(res / 'font' / 'body.ttf'), 26)
    sub = 'Престол × Let\'s Do'
    w = d.textlength(sub, font=small)
    d.text(((400 - w) / 2, 354), sub, font=small, fill=(236, 214, 160))
    img.convert('RGB').save(out)


def recipe(crafter, inputs, result, count=1, level=1, extra=None):
    merged = {}
    for item in inputs:
        merged[item] = merged.get(item, 0) + 1
    body = {
        'type': 'recipe',
        'crafter': f'{crafter}_custom',
        'inputs': [{'item': i, 'count': c} if c > 1 else {'item': i} for i, c in merged.items()],
        'result': result,
        'show-tooltip': True,
    }
    if count > 1:
        body['count'] = count
    if level > 1:
        body['min-building-level'] = level
    if extra:
        body.update(extra)
    return body


def data_pack(name, title):
    root = OUT / 'packs' / name
    root.mkdir(parents=True, exist_ok=True)
    (root / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15, 'description': title}}, indent=2) + '\n')
    return root / 'data' / 'prestol' / 'crafterrecipes'


def write(folder, name, body):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f'{name}.json').write_text(json.dumps(body, indent=2) + '\n')


def brewer(jar):
    out = data_pack('letsdo_brewery', 'Prestol: brewer recipes for Let\'s Do Brewery') / 'brewer'
    for grain in ('barley', 'corn', 'wheat'):
        source = f'brewery:{grain}' if grain != 'wheat' else 'minecraft:wheat'
        write(out, f'dried_{grain}', recipe('brewer', [source], f'brewery:dried_{grain}'))
    material_level = {'WOOD': 1, 'COPPER': 2, 'NETHERITE': 3}
    n = 0
    for entry in jar.namelist():
        if entry.startswith('data/brewery/recipes/brewing/') and entry.endswith('.json'):
            r = json.loads(jar.read(entry))
            items = [i['item'] for i in r['ingredients'] if 'item' in i]
            level = material_level.get(r.get('material', 'WOOD'), 1)
            body = recipe('brewer', items + ['minecraft:water_bucket'], r['result']['item'], count=2, level=level,
                          extra={'additional-output': [{'item': 'minecraft:bucket'}]})
            write(out, Path(entry).stem, body)
            n += 1
    return n


JUICE = {
    'red_general': ('vinery:red_grapejuice', 'vinery:red_grape'),
    'white_general': ('vinery:white_grapejuice', 'vinery:white_grape'),
    'red_taiga': ('vinery:red_taiga_grapejuice', 'vinery:taiga_grapes_red'),
    'white_taiga': ('vinery:white_taiga_grapejuice', 'vinery:taiga_grapes_white'),
    'red_savanna': ('vinery:red_savanna_grapejuice', 'vinery:savanna_grapes_red'),
    'white_savanna': ('vinery:white_savanna_grapejuice', 'vinery:savanna_grapes_white'),
    'red_jungle': ('vinery:red_jungle_grapejuice', 'vinery:jungle_grapes_red'),
    'white_jungle': ('vinery:white_jungle_grapejuice', 'vinery:jungle_grapes_white'),
    'apple': ('vinery:apple_juice', None),
}
BASIC = {'minecraft:sugar', 'minecraft:honey_bottle', 'minecraft:sweet_berries', 'vinery:cherry', 'minecraft:kelp',
         'minecraft:cocoa_beans', 'minecraft:snowball', 'vinery:apple_juice'}


def winemaker(jar):
    out = data_pack('letsdo_vinery', 'Prestol: winemaker recipes for Let\'s Do Vinery') / 'winemaker'
    for juice, grape in JUICE.values():
        if grape:
            write(out, Path(juice).name.split(':')[-1], recipe('winemaker', [grape] * 4 + ['minecraft:glass_bottle'], juice))
    for entry in jar.namelist():
        if not entry.startswith('data/vinery/recipes/') or not entry.endswith('.json'):
            continue
        r = json.loads(jar.read(entry))
        kind = r.get('type')
        if kind == 'vinery:apple_mashing':
            write(out, Path(entry).stem, recipe('winemaker', [r['input']['item']], r['output']['item'], r['output'].get('count', 1)))
        elif kind == 'vinery:apple_fermenting':
            write(out, Path(entry).stem, recipe('winemaker', [r['input']['item'], 'vinery:wine_bottle'], r['output']['item'],
                                                 r['output'].get('count', 1)))
        elif kind == 'vinery:wine_fermentation':
            juice = JUICE.get(r['juice']['type'])
            if not juice:
                continue
            bottles = max(1, math.ceil(r['juice']['amount'] / 5))
            items = [i['item'] for i in r['ingredients'] if 'item' in i]
            level = 1 if r['juice']['amount'] <= 10 and all(i in BASIC for i in items) else 2
            if any(i.startswith('vinery:') and i.endswith(('_wine', 'noir')) for i in items) or len(items) >= 3:
                level = 3
            inputs = [juice[0]] * bottles + items
            if r.get('wine_bottle', {}).get('required'):
                inputs.append('vinery:wine_bottle')
            write(out, Path(entry).stem, recipe('winemaker', inputs, r['result']['item'], r['result'].get('count', 1), level))


def main():
    mc, brewery, vinery = (zipfile.ZipFile(p) for p in sys.argv[1:4])
    blueprints(mc)
    print('brewing recipes:', brewer(brewery))
    winemaker(vinery)
    print('done')


if __name__ == '__main__':
    main()
