"""
Картинки из ИИ-генератора SDXL Turbo (локально, видеокарта NVIDIA) для замены графики игры.
Окружение: <папка>/sdxl/.venv (см. tools/ai/README.md). Запуск из корня проекта:

  python tools/ai/gen.py free "cozy medieval cottage" --out build/ai/cottage.png [--w 512 --h 512 --seed 1 --steps 2 --n 4 --nobg --tile]
  python tools/ai/gen.py set res|trees|rocks|bushes|ground|title|icon [--seed N] [--only id,id]

Наборы (set) подгоняют картинку под спрайт атласа (размер и точка привязки из assets/sprites/atlas.json) и пишут
tools/art/override/<имя>.png - генератор атласа (./gradlew :tools:run) берёт их вместо нарисованного кодом.
Черновики всех вариантов - build/ai/<набор>/ (выбрать лучший seed и перегенерировать с --seed).
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OVERRIDE = os.path.join(ROOT, "tools", "art", "override")
DRAFTS = os.path.join(ROOT, "build", "ai")
MODEL = "stabilityai/sdxl-turbo"

# общий стиль: рисованная изометрия, свет сверху слева, мягкие градиенты (как здания из Blender)
STYLE = "hand-painted game art, soft gradients, vibrant natural colors, light from top left, clean shapes, high detail"
NEG = "text, letters, watermark, signature, frame, border, blurry, photo, realistic photo, people"

_pipe = None
_img2img = None


def pipe():
    global _pipe
    if _pipe is None:
        import torch
        from diffusers import AutoPipelineForText2Image
        _pipe = AutoPipelineForText2Image.from_pretrained(MODEL, torch_dtype=torch.float16, variant="fp16")
        # 6 ГБ видеопамяти: модель по частям между видеокартой и памятью
        _pipe.enable_model_cpu_offload()
        _pipe.vae.enable_slicing()
        _pipe.set_progress_bar_config(disable=True)
    return _pipe


def img2img():
    global _img2img
    if _img2img is None:
        from diffusers import AutoPipelineForImage2Image
        _img2img = AutoPipelineForImage2Image.from_pipe(pipe())
        _img2img.set_progress_bar_config(disable=True)
    return _img2img


def set_tiling(on):
    """Бесшовная текстура: свёртки модели и декодера с круговыми краями"""
    import torch
    p = pipe()
    for model in (p.unet, p.vae):
        for m in model.modules():
            if isinstance(m, torch.nn.Conv2d):
                m.padding_mode = "circular" if on else "zeros"


# для отдельных предметов (спрайты, значки): без земли и пейзажа вокруг - фон потом вырезается
ISOLATED = ", isolated single object, no ground, no grass patch, no scenery"
NEG_OBJECT = ", ground, grass patch, landscape, scenery, multiple objects, cropped"


def generate(prompt, w=512, h=512, seed=1, steps=2, tile=False, isolated=False):
    import torch
    set_tiling(tile)
    g = torch.Generator("cpu").manual_seed(seed)
    if isolated:
        prompt += ISOLATED
    img = pipe()(prompt=prompt + ", " + STYLE, negative_prompt=NEG + (NEG_OBJECT if isolated else ""), width=w, height=h,
                 num_inference_steps=steps, guidance_scale=0.0, generator=g).images[0]
    if tile:
        set_tiling(False)
    return img


def restyle(img, prompt, strength=0.5, seed=1, steps=2):
    """Та же картинка в другом виде (например, зимой): img2img, steps * strength >= 1"""
    import torch
    g = torch.Generator("cpu").manual_seed(seed)
    return img2img()(prompt=prompt + ISOLATED + ", " + STYLE, negative_prompt=NEG + NEG_OBJECT, image=img.convert("RGB"), strength=strength,
                     num_inference_steps=steps, guidance_scale=0.0, generator=g).images[0]


# выбранные варианты: имя спрайта -> seed (--pick tree_0=12,res_stone=5)
PICK = {}


def seed_for(name, default):
    return PICK.get(name, default)


_rembg = None


def cutout(img):
    """Убрать фон (модель rembg u2net) -> RGBA, подчистить полупрозрачный мусор"""
    global _rembg
    from rembg import new_session, remove
    if _rembg is None:
        _rembg = new_session("u2net")
    out = remove(img, session=_rembg).convert("RGBA")
    a = np.array(out)
    a[..., 3] = np.where(a[..., 3] < 24, 0, a[..., 3])
    return Image.fromarray(a)


def cutout_white(img, thresh=38):
    """
    Белый фон -> прозрачный: заливка от краёв картинки по почти белому (rembg путает светлую листву с фоном).
    Край силуэта смягчается: чем ближе к белому, тем прозрачнее.
    """
    from PIL import ImageDraw
    rgb = img.convert("RGB")
    probe = rgb.copy()
    w, h = probe.size
    key = (255, 0, 255)
    for x in range(0, w, 16):
        for y in (0, h - 1):
            if sum(probe.getpixel((x, y))) > 3 * 225:
                ImageDraw.floodfill(probe, (x, y), key, thresh=thresh)
    for y in range(0, h, 16):
        for x in (0, w - 1):
            if sum(probe.getpixel((x, y))) > 3 * 225:
                ImageDraw.floodfill(probe, (x, y), key, thresh=thresh)
    p = np.array(probe).astype(np.int32)
    bg = (p[..., 0] == 255) & (p[..., 1] == 0) & (p[..., 2] == 255)
    a = np.where(bg, 0, 255).astype(np.uint8)
    # мягкий край: непрозрачные пиксели рядом с фоном - по удалённости от белого
    near = np.array(Image.fromarray(np.where(bg, 255, 0).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))) > 0
    src = np.array(rgb).astype(np.int32)
    whiteness = np.clip((src.min(axis=2) - 170) * 3, 0, 255)
    edge = near & ~bg
    a[edge] = np.clip(255 - whiteness[edge], 60, 255)
    out = np.dstack([np.array(rgb), a])
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def trim(img):
    box = img.getbbox()
    return img.crop(box) if box else img


def outline(img, r=2, color=(36, 26, 16, 230)):
    """Тёмная обводка снаружи силуэта - как у остальной графики"""
    a = np.array(img)
    solid = a[..., 3] >= 128
    grown = np.array(Image.fromarray((solid * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(r * 2 + 1))) > 0
    ring = grown & ~solid & (a[..., 3] < 128)
    a[ring] = color
    return Image.fromarray(a)


def shadow(size, cx, cy, rx, ry, alpha=70):
    """Мягкая тень-эллипс (основание дерева, камня)"""
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w]
    d = ((xx - cx) / max(1, rx)) ** 2 + ((yy - cy) / max(1, ry)) ** 2
    a = np.clip(1 - d, 0, 1) ** 1.5 * alpha
    s = np.zeros((h, w, 4), np.uint8)
    s[..., 3] = a.astype(np.uint8)
    return Image.fromarray(s)


# ---------- атлас: размеры и привязка спрайтов ----------

_atlas = None


def region(name):
    """(w, h, ax, ay) спрайта в полном атласе; ay - от низа; у зданий из Blender масштаб другой (здесь не нужны)"""
    global _atlas
    if _atlas is None:
        _atlas = json.load(open(os.path.join(ROOT, "assets", "sprites", "atlas.json")))["regions"]
    r = _atlas[name]
    return r[2], r[3], r[4], r[5]


def names(prefix, suffix=""):
    global _atlas
    region("white")
    return sorted(n for n in _atlas if n.startswith(prefix) and n.endswith(suffix) and n[len(prefix):len(n) - len(suffix) or None].isdigit())


def place_on_anchor(obj, name, fit_w, fit_h, shadow_k=0.0):
    """Вписать вырезанный объект в холст спрайта name: низ объекта по центру - в точку привязки"""
    w, h, ax, ay = region(name)
    obj = trim(obj)
    k = min(fit_w / obj.width, fit_h / obj.height)
    obj = obj.resize((max(1, round(obj.width * k)), max(1, round(obj.height * k))), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    base_y = h - ay
    if shadow_k > 0:
        canvas.alpha_composite(shadow((w, h), ax + obj.width * 0.12, base_y - 1, obj.width * shadow_k, obj.width * shadow_k * 0.35))
    x = round(ax - obj.width / 2)
    y = base_y - obj.height
    canvas.alpha_composite(obj, (max(0, min(w - obj.width, x)), max(0, y)))
    return outline(canvas, r=1)


def save_override(img, name):
    os.makedirs(OVERRIDE, exist_ok=True)
    img.save(os.path.join(OVERRIDE, name + ".png"))
    print("override", name, img.size)


def draft(img, kind, name):
    d = os.path.join(DRAFTS, kind)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name + ".png"))


# ---------- наборы ----------

RES = {
    "logs": "a small stack of three cut wooden logs", "planks": "a neat stack of wooden planks",
    "stone": "a small pile of three grey cut stone blocks", "water": "a wooden bucket full of clear blue water",
    "bread": "a round loaf of golden bread", "fish": "a fresh silver fish", "grain": "a bundle of golden wheat ears",
    "flour": "a white sack of flour", "meat": "a ham leg on the bone", "apples": "two shiny red apples",
    "wool": "a fluffy white sheep wool fleece bundle", "cloth": "a folded roll of red cloth", "ale": "a wooden mug of foamy ale",
}


def set_res(seed, only):
    for rid, what in RES.items():
        if only and rid not in only:
            continue
        name = "res_" + rid
        try:
            w, h, _, _ = region(name)
        except KeyError:
            continue
        img = generate(f"game inventory icon of {what}, single object centered, plain white background, bold simple shape", seed=seed_for(name, seed), isolated=True)
        draft(img, "res", name)
        obj = trim(cutout(img))
        k = min((w - 6) / obj.width, (h - 6) / obj.height)
        obj = obj.resize((max(1, round(obj.width * k)), max(1, round(obj.height * k))), Image.LANCZOS)
        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        canvas.alpha_composite(obj, ((w - obj.width) // 2, (h - obj.height) // 2))
        save_override(outline(canvas, r=2), name)


TREES = ["a round leafy deciduous oak tree", "a tall dark green fir tree", "a small young birch tree with white trunk and a compact crown, lots of empty space around",
         "a lush green broadleaf tree", "a tall green pine tree", "a bushy green maple tree"]
WINTER = ["a bare deciduous oak tree with snow on its branches", "a fir tree covered with white snow",
          "a bare birch tree with snow on its branches", "a bare broadleaf tree with snow on its branches",
          "a pine tree covered with white snow", "a bare deciduous oak tree with snow on its branches"]   # клён генератор рисует только с осенней листвой


def set_trees(seed, only):
    for i, name in enumerate(names("tree_")):
        for snow in (False, True):
            n = name + ("_snow" if snow else "")
            if only and n not in only and name not in only:
                continue
            w, h, ax, ay = region(n)
            what = (WINTER if snow else TREES)[i % len(TREES)]
            img = generate(f"game sprite of {what}, full crown and trunk, plain white background", seed=seed_for(n, seed + i), isolated=True)
            draft(img, "trees", n)
            save_override(place_on_anchor(cutout_white(img), n, w - 4, h - ay - 2, shadow_k=0.4), n)


def set_rocks(seed, only):
    for i, name in enumerate(names("rock_")):
        if only and name not in only:
            continue
        w, h, ax, ay = region(name)
        img = generate("isometric game sprite of a large grey mossy boulder rock formation, wide and low, plain white background", seed=seed_for(name, seed + i), isolated=True)
        draft(img, "rocks", name)
        save_override(place_on_anchor(cutout(img), name, w - 6, h - 4), name)


def set_bushes(seed, only):
    for i, name in enumerate(names("prop_bush_")):
        if only and name not in only:
            continue
        w, h, ax, ay = region(name)
        img = generate("isometric game sprite of a small round green bush, plain white background", seed=seed + i, isolated=True)
        draft(img, "bushes", name)
        save_override(place_on_anchor(cutout(img), name, w - 2, h - 2), name)
        snow = restyle(img, "the same small bush covered with snow, plain white background", strength=0.5, seed=seed + i)
        draft(snow, "bushes", name + "_snow")
        sname = name.replace("prop_bush_", "prop_bushsnow_")
        save_override(place_on_anchor(cutout(snow), sname, w - 2, h - 2), sname)


GROUND = {
    "grass": "seamless texture of short lush green meadow grass seen from above, small flowers",
    "grass_snow": "seamless texture of smooth fresh white snow surface seen from above, soft blue shading, a few grass tips, no cracks",
    "forest": "seamless texture of dark green forest floor moss and fallen needles seen from above",
    "forest_snow": "seamless texture of snowy forest floor with moss patches seen from above",
    "road": "seamless texture of a packed brown dirt road with small pebbles seen from above",
    "rock": "seamless texture of rough natural grey rocky ground with gravel and moss seen from above, no tiles, no pavement",
}


# сколько фактуры ИИ-текстуры класть на исходный цвет клетки (1 - как есть; меньше - мягче, повтор клеток незаметнее)
GROUND_DETAIL = {"grass": 0.55, "grass_snow": 0.35, "forest": 0.7, "forest_snow": 0.5, "road": 0.7, "rock": 0.7}


def original_color(name):
    """Средний цвет исходной клетки (нарисованной кодом) - из атласа в git (build/ai/orig_atlas.*)"""
    atlas = os.path.join(DRAFTS, "orig_atlas.png")
    meta = os.path.join(DRAFTS, "orig_atlas.json")
    if not os.path.exists(atlas):
        return None
    r = json.load(open(meta))["regions"][name]
    img = Image.open(atlas if r[7] == 0 else atlas.replace(".png", f"_{r[7]}.png")).convert("RGBA")
    a = np.asarray(img.crop((r[0], r[1], r[0] + r[2], r[1] + r[3])), dtype=np.float32)
    m = a[..., 3] > 250
    return a[..., :3][m].mean(axis=0)


def diamond(tex, name, detail=1.0):
    """
    Бесшовная квадратная текстура -> ромб клетки (одна клетка - один период текстуры, соседние стыкуются).
    Цвет - исходной клетки игры, от ИИ - только фактура (отклонения от среднего, с силой detail).
    """
    w, h, _, _ = region(name)
    t = np.asarray(tex.convert("RGB").resize((128, 128), Image.LANCZOS), dtype=np.float32)
    base = original_color(name)
    if base is not None:
        t = np.clip(base + (t - t.reshape(-1, 3).mean(axis=0)) * detail, 0, 255)
    n = t.shape[0]
    out = np.zeros((h, w, 4), np.uint8)
    for py in range(h):
        for px in range(w):
            # ромб: левый (0, h/2), верхний (w/2, 0), правый (w, h/2), нижний (w/2, h) углы
            sx = (px + 0.5) / (w / 2) - 1
            sy = (py + 0.5) / (h / 2) - 1
            if abs(sx) + abs(sy) > 1.0:
                continue
            u = ((sx + sy) / 2 + 0.5) * n
            v = ((sy - sx) / 2 + 0.5) * n
            x0, y0 = int(math.floor(u)) % n, int(math.floor(v)) % n
            fx, fy = u - math.floor(u), v - math.floor(v)
            x1, y1 = (x0 + 1) % n, (y0 + 1) % n
            c = (t[y0, x0] * (1 - fx) * (1 - fy) + t[y0, x1] * fx * (1 - fy) + t[y1, x0] * (1 - fx) * fy + t[y1, x1] * fx * fy)
            out[py, px, :3] = np.clip(c, 0, 255)
            out[py, px, 3] = 255
    return Image.fromarray(out)


def set_ground(seed, only):
    for kind, prompt in GROUND.items():
        if only and kind not in only:
            continue
        tex = generate(prompt + ", flat even lighting, top-down", seed=seed_for("tile_" + kind, seed), tile=True)
        draft(tex, "ground", kind)
        tile = None
        for name in names(f"tile_{kind}_"):
            tile = tile or diamond(tex, name, GROUND_DETAIL.get(kind, 1.0))
            save_override(tile, name)   # все варианты одинаковые: разные текстуры не стыковались бы между клетками


def set_title(seed, only):
    img = generate("wide panoramic painting of a cozy medieval village in a green valley, half-timbered houses with red roofs,"
                   " a town hall with a clock tower, a windmill, a river and forest, isometric view, golden afternoon light",
                   w=1344, h=768, seed=seed, steps=4)
    draft(img, "title", "title")
    out = os.path.join(ROOT, "tools", "art", "title.jpg")
    img.convert("RGB").save(out, quality=88)
    print("title", out)


def set_icon(seed, only):
    img = generate("app icon, a cozy medieval half-timbered cottage with a red roof on a green hill, blue sky, centered, bold simple shapes",
                   seed=seed, steps=4)
    draft(img, "icon", "icon")
    img.convert("RGB").save(os.path.join(OVERRIDE, "icon.png"))
    print("icon", os.path.join(OVERRIDE, "icon.png"))


SETS = {"res": set_res, "trees": set_trees, "rocks": set_rocks, "bushes": set_bushes, "ground": set_ground,
        "title": set_title, "icon": set_icon}


def main():
    ap = argparse.ArgumentParser(description="SDXL Turbo для Hearthvale")
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("free", help="одна картинка (или несколько) по запросу")
    f.add_argument("prompt")
    f.add_argument("--out", default=os.path.join(DRAFTS, "free", "out.png"))
    f.add_argument("--w", type=int, default=512)
    f.add_argument("--h", type=int, default=512)
    f.add_argument("--seed", type=int, default=1)
    f.add_argument("--steps", type=int, default=2)
    f.add_argument("--n", type=int, default=1, help="сколько вариантов (seed, seed+1, ...)")
    f.add_argument("--nobg", action="store_true", help="убрать фон (прозрачный PNG)")
    f.add_argument("--tile", action="store_true", help="бесшовная текстура")
    f.add_argument("--object", action="store_true", help="отдельный предмет: без земли и пейзажа (вместе с --nobg)")
    s = sub.add_parser("set", help="набор для игры -> tools/art/override")
    s.add_argument("name", nargs="+", choices=sorted(SETS), help="один или несколько наборов (модель грузится один раз)")
    s.add_argument("--seed", type=int, default=7)
    s.add_argument("--pick", default="", help="свои seed для отдельных спрайтов: tree_0=12,res_stone=5")
    s.add_argument("--only", default="", help="через запятую: только эти (res: logs,bread; trees: tree_0...)")
    a = ap.parse_args()
    if a.cmd == "free":
        os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
        for k in range(a.n):
            img = generate(a.prompt, a.w, a.h, a.seed + k, a.steps, a.tile, a.object)
            if a.nobg:
                img = cutout(img)
            path = a.out if a.n == 1 else a.out.replace(".png", f"_{a.seed + k}.png")
            img.save(path)
            print("saved", path)
    else:
        for kv in filter(None, a.pick.split(",")):
            k, v = kv.split("=")
            PICK[k.strip()] = int(v)
        for name in a.name:
            SETS[name](a.seed, set(x for x in a.only.split(",") if x))


if __name__ == "__main__":
    sys.exit(main())
