"""
Рендер зданий, жителей (имя people), природы (nature) и анимаций зданий (anims [id...]): blender -b --python tools/blender/render.py -- [имена...] [--out папка] [--samples N] [--rots 0123] [--summer|--winter]
Для каждого здания и поворота карты r (0..3) - bld_<id>[_r<r>].png и bld_<id>[_r<r>]_snow.png
(снег на гранях, смотрящих вверх); поворот 0 без суффикса. Камера и солнце поворачиваются вместе -
свет на экране всегда слева. В render.json: точка привязки (пиксели, ay от низа) - ближний к зрителю угол
основания, и точки (трубы, ось крыльев) в единицах мира игры от привязки.
Генератор ресурсов (./gradlew :tools:run) подставляет эти картинки вместо нарисованных кодом.
"""
import sys
import os
import json
import math
import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib
from buildings import BUILDINGS, level_marks

# у каких зданий есть уровни (как World.hasLevels): мастерские, услуги с работниками, склады
_content = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../assets/data/content.json"), encoding="utf-8"))
LEVELED = {b["id"] for b in _content["buildings"] if not b.get("wonder") and (b["role"] in ("producer", "store", "tower", "guard") or (b["role"] == "service" and b.get("workers", 0) > 0))}
LEVELED.add("fisher_shore")

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out = "build/render"
samples = 96
rots = [0, 1, 2, 3]
seasons = [False, True]
levels = [1]
points_only = False   # --points: только пересчитать точки в render.json, без рендера
names = []
i = 0
while i < len(argv):
    if argv[i] == "--out":
        out = argv[i + 1]; i += 2
    elif argv[i] == "--samples":
        samples = int(argv[i + 1]); i += 2
    elif argv[i] == "--rots":
        rots = [int(c) for c in argv[i + 1]]; i += 2
    elif argv[i] == "--points":
        points_only = True; i += 1
    elif argv[i] == "--summer":
        seasons = [False]; i += 1
    elif argv[i] == "--winter":
        seasons = [True]; i += 1
    elif argv[i] == "--levels":
        levels = [int(c) for c in argv[i + 1]]; i += 2
    else:
        names.append(argv[i]); i += 1
if not names:
    names = list(BUILDINGS) + ["mill_sails", "marks", "people", "walls", "nature"]
    render_all_anims = True
else:
    render_all_anims = False
if points_only:
    names = [n for n in names if n in BUILDINGS]
out = os.path.abspath(out)
os.makedirs(out, exist_ok=True)
meta_path = os.path.join(out, "render.json")
meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}

def render_sails():
    """Крылья мельницы: 4 кадра (по 22.5 градуса), плоскость к зрителю - одинаковы при любом повороте карты;
    привязка - ось крыльев (её место на здании - точка hub)"""
    from buildings import mill_sails
    for frame in range(4):
        lib.reset()
        lib._mats.clear()
        lib.set_snow(False)
        lib.setup_render(samples)
        lib.setup_light()
        before = set(bpy.data.objects)
        mill_sails(frame)
        hub = Vector((0.75, 0.75, 1.2))
        rotz = math.radians(-45)
        from mathutils import Matrix
        m = Matrix.Translation(hub) @ Matrix.Rotation(rotz, 4, 'Z')
        for o in set(bpy.data.objects) - before:
            o.matrix_world = m @ o.matrix_world
        lib.camera_for(1.5, 2.4, margin=0.3)
        sprite = f"mill_sails_{frame}"
        lib.render_to(os.path.join(out, sprite + ".png"))
        ax, ay = lib.project(hub)
        meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
        print("rendered", sprite)


def render_marks():
    """
    Признаки уровня отдельным наложением (рисуется поверх обычного спрайта здания): флажок у левого угла, навес
    у правого, знамя и фонари - у ближнего; эти углы на экране всегда спереди и сбоку здания, поэтому одна картинка
    годится для любого здания этого размера и любого поворота карты. lvlmark_<размер>_<уровень>[_snow].
    """
    for size in (2, 3):
        for lv in (2, 3):
            for snow in (False, True):
                lib.reset()
                lib._mats.clear()
                lib.set_snow(snow)
                lib.setup_render(samples)
                lib.setup_light()
                lib.shadow_ground()
                level_marks(size, lv)
                lib.camera_for(size, 1.6)
                sprite = f"lvlmark_{size}_{lv}" + ("_snow" if snow else "")
                lib.render_to(os.path.join(out, sprite + ".png"))
                ax, ay = lib.project((0, 0, 0))
                meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
                print("rendered", sprite)


def render_people():
    """
    Жители: person_<профессия>_<направление>_<кадр>[_c - руки вверх, держит груз]. Направление - куда идёт на экране:
    0 вправо-вниз, 1 вправо-вверх, 2 влево-вниз, 3 влево-вверх (свет всегда слева, поэтому без отражения).
    Привязка - точка между ступнями. Без линий Freestyle: контур добавляет генератор атласа.
    """
    from people import person
    from mathutils import Matrix
    # в какую сторону мира смотреть, чтобы на экране (камера rot 0) идти в направлении d
    yaw = {0: -90, 1: 0, 2: 180, 3: 90}
    for job in range(4):
        for frame in range(4):
            for carry in ((False,) if job == 3 else (False, True)):
                lib.reset()
                lib._mats.clear()
                lib.set_snow(False)
                lib.setup_render(samples)
                bpy.context.scene.render.use_freestyle = False
                lib.setup_light()
                lib.shadow_ground()
                before = set(bpy.data.objects)
                person(job, frame, carry)
                figure = list(set(bpy.data.objects) - before)
                base = {o.name: o.matrix_world.copy() for o in figure}
                lib.camera_for(0, 0.75, margin=0.45)
                for d in range(4):
                    m = Matrix.Rotation(math.radians(yaw[d]), 4, 'Z')
                    for o in figure:
                        o.matrix_world = m @ base[o.name]
                    sprite = f"person_{job}_{d}_{frame}" + ("_c" if carry else "")
                    lib.render_to(os.path.join(out, sprite + ".png"))
                    ax, ay = lib.project((0, 0, 0))
                    meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
                    print("rendered", sprite)


def render_nature():
    """
    Природа карты: tree_<0..5>[_snow] (0-3 лиственные, 4-5 хвойные), rock_<0..2>, prop_bush_<0..2> и prop_bushsnow_<0..2>.
    Один вид без поворотов (со всех сторон одинаково), камера как у жителя; привязка - основание ствола (центр клетки).
    Без Freestyle: тонкий контур добавляет генератор атласа. Зимой лиственные - голые ветви со снегом.
    """
    from props import forest_tree, rock_model, forest_bush
    jobs = []
    for v in range(6):
        jobs.append((f"tree_{v}", lambda v=v: forest_tree(v, False), 2.7))
        jobs.append((f"tree_{v}_snow", lambda v=v: forest_tree(v, True), 2.7))
    for v in range(3):
        jobs.append((f"rock_{v}", lambda v=v: rock_model(v), 0.8))
        jobs.append((f"prop_bush_{v}", lambda v=v: forest_bush(v, False), 0.5))
        jobs.append((f"prop_bushsnow_{v}", lambda v=v: forest_bush(v, True), 0.5))
    for sprite, build, height in jobs:
        lib.reset()
        lib._mats.clear()
        lib.set_snow(sprite.endswith("_snow") or "bushsnow" in sprite, 0.1 if sprite.endswith("_snow") else 0.0)
        lib.setup_render(samples)
        bpy.context.scene.render.use_freestyle = False
        lib.setup_light(nature=True)
        lib.shadow_ground()
        build()
        lib.camera_for(0, height, margin=1.7)
        lib.render_to(os.path.join(out, sprite + ".png"))
        ax, ay = lib.project((0, 0, 0))
        meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
        print("rendered", sprite)


def render_walls():
    """
    Стены: wall_<wood|stone>_post - столб в центре клетки, wall_<вид>_<d> - полусегмент от центра к краю клетки
    (d: 0 к -y, 1 к +x, 2 к +y, 3 к -x), камера и привязка как у здания 1x1 при повороте 0; зимние - с суффиксом _snow.
    """
    from buildings import wall_post, wall_seg
    for kind in ("wood", "stone"):
        for part in ("post", 0, 1, 2, 3):
            for snow in (False, True):
                lib.reset()
                lib._mats.clear()
                lib.set_snow(snow)
                lib.setup_render(samples)
                lib.setup_light()
                lib.shadow_ground()
                if part == "post":
                    wall_post(kind)
                else:
                    wall_seg(kind, part)
                lib.camera_for(1, 0.95)
                sprite = f"wall_{kind}_{part}" + ("_snow" if snow else "")
                lib.render_to(os.path.join(out, sprite + ".png"))
                ax, ay = lib.project((0, 0, 0))
                meta[sprite] = {"ax": round(ax), "ay": round(ay), "corner": [0, 0], "points": {}}
                print("rendered", sprite)


def render_creatures():
    """Волк: wolf_<направление>_<кадр> (направления и привязка как у жителей)"""
    from people import wolf
    from mathutils import Matrix
    yaw = {0: -90, 1: 0, 2: 180, 3: 90}
    for frame in range(4):
        lib.reset()
        lib._mats.clear()
        lib.set_snow(False)
        lib.setup_render(samples)
        bpy.context.scene.render.use_freestyle = False
        lib.setup_light()
        lib.shadow_ground()
        before = set(bpy.data.objects)
        wolf(frame)
        figure = list(set(bpy.data.objects) - before)
        base = {o.name: o.matrix_world.copy() for o in figure}
        lib.camera_for(0, 0.5, margin=0.55)
        for d in range(4):
            m = Matrix.Rotation(math.radians(yaw[d]), 4, 'Z')
            for o in figure:
                o.matrix_world = m @ base[o.name]
            sprite = f"wolf_{d}_{frame}"
            lib.render_to(os.path.join(out, sprite + ".png"))
            ax, ay = lib.project((0, 0, 0))
            meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
            print("rendered", sprite)


def render_extras():
    """Ребёнок kid_<направление>_<кадр> и повозка соседей wagon_<направление>_<кадр> (направления и привязка как у жителей)"""
    from people import kid, wagon
    from mathutils import Matrix
    yaw = {0: -90, 1: 0, 2: 180, 3: 90}
    for prefix, build, height, margin in (("kid", kid, 0.45, 0.45), ("wagon", wagon, 0.6, 0.85)):
        for frame in range(4):
            lib.reset()
            lib._mats.clear()
            lib.set_snow(False)
            lib.setup_render(samples)
            bpy.context.scene.render.use_freestyle = False
            lib.setup_light()
            lib.shadow_ground()
            before = set(bpy.data.objects)
            build(frame)
            figure = list(set(bpy.data.objects) - before)
            base = {o.name: o.matrix_world.copy() for o in figure}
            lib.camera_for(0, height, margin=margin)
            for d in range(4):
                m = Matrix.Rotation(math.radians(yaw[d]), 4, 'Z')
                for o in figure:
                    o.matrix_world = m @ base[o.name]
                sprite = f"{prefix}_{d}_{frame}"
                lib.render_to(os.path.join(out, sprite + ".png"))
                ax, ay = lib.project((0, 0, 0))
                meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
                print("rendered", sprite)


def view(size, height, rot, base_sun):
    """Камера под поворот карты rot и солнце, повёрнутое вместе с ней (свет на экране всегда слева)"""
    cam = bpy.data.objects.get("cam")
    if cam is not None:
        bpy.data.objects.remove(cam)
    lib.camera_for(size, height, rot=rot)
    from mathutils import Quaternion
    sun = bpy.data.objects["sun"]
    sun.rotation_euler = (Quaternion((0, 0, 1), math.radians(90 * rot)) @ base_sun.to_quaternion()).to_euler()


def anchor(size, rot):
    """Ближний к зрителю угол основания - точка привязки спрайта здания"""
    yaw = math.radians(45 + 90 * rot)
    fwd = Vector((math.cos(yaw), math.sin(yaw), 0))
    corners = [Vector((0, 0, 0)), Vector((size, 0, 0)), Vector((0, size, 0)), Vector((size, size, 0))]
    near = min(corners, key=lambda c: c.dot(fwd))
    return lib.project(near)


ANIMATED = ["hall", "house", "house2"]


def render_anims(which):
    """
    Анимированные части зданий (флаг ратуши, бельё у домов): anim_<id>_<кадр>[_r<поворот>], 4 кадра, только лето.
    Здание - невидимая преграда (lib.prepare_anim), камера и привязка те же, что у bld_<id>, поэтому кадр рисуется
    поверх спрайта здания в той же точке.
    """
    for name in which:
        for frame in range(4):
            lib.reset()
            lib._mats.clear()
            lib.set_snow(False)
            lib.anim_frame = frame
            lib.setup_render(samples)
            lib.setup_light()
            lib.shadow_ground()
            size, height, _ = BUILDINGS[name]()
            lib.prepare_anim()
            base_sun = bpy.data.objects["sun"].rotation_euler.copy()
            for rot in rots:
                view(size, height, rot, base_sun)
                sprite = f"anim_{name}_{frame}" + (f"_r{rot}" if rot else "")
                lib.render_to(os.path.join(out, sprite + ".png"))
                ax, ay = anchor(size, rot)
                meta[sprite] = {"ax": round(ax), "ay": round(ay), "points": {}}
                print("rendered", sprite)
        lib.anim_frame = None


if "anims" in names:
    names.remove("anims")
    render_anims([n for n in names if n in ANIMATED] or ANIMATED)
    names = []

if "people" in names:
    names.remove("people")
    render_people()
    render_creatures()
    render_extras()

if "extras" in names:
    names.remove("extras")
    render_extras()

if "creatures" in names:
    names.remove("creatures")
    render_creatures()

if "nature" in names:
    names.remove("nature")
    render_nature()

if "walls" in names:
    names.remove("walls")
    render_walls()

if "marks" in names:
    names.remove("marks")
    render_marks()

if "mill_sails" in names:
    names.remove("mill_sails")
    render_sails()

jobs = [(n, lv) for n in names for lv in levels if lv == 1 or n in LEVELED]
for name, lv in jobs:
    for snow in seasons:
        lib.reset()
        lib._mats.clear()
        lib.set_snow(snow)
        lib.setup_render(samples)
        lib.setup_light()
        lib.shadow_ground()
        size, height, points = BUILDINGS[name]()
        level_marks(size, lv)
        sun = bpy.data.objects["sun"]
        base_sun = sun.rotation_euler.copy()
        for rot in rots:
            cam = bpy.data.objects.get("cam")
            if cam is not None:
                bpy.data.objects.remove(cam)
            lib.camera_for(size, height, rot=rot)
            # солнце поворачивается вместе с камерой вокруг вертикали
            sun.rotation_euler = base_sun.copy()
            sun.rotation_euler.rotate_axis('Z', 0)
            q = sun.rotation_euler.to_quaternion()
            from mathutils import Quaternion
            sun.rotation_euler = (Quaternion((0, 0, 1), math.radians(90 * rot)) @ q).to_euler()
            sprite = f"bld_{name}" + (f"_l{lv}" if lv > 1 else "") + (f"_r{rot}" if rot else "") + ("_snow" if snow else "")
            if not points_only:
                lib.render_to(os.path.join(out, sprite + ".png"))
            if not snow and rot == 0 and not points_only:
                bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, f"bld_{name}" + (f"_l{lv}" if lv > 1 else "") + ".blend"), check_existing=False)
            # ближний к зрителю угол основания - точка привязки
            yaw = math.radians(45 + 90 * rot)
            fwd = Vector((math.cos(yaw), math.sin(yaw), 0))
            corners = [Vector((0, 0, 0)), Vector((size, 0, 0)), Vector((0, size, 0)), Vector((size, size, 0))]
            near = min(corners, key=lambda c: c.dot(fwd))
            ax, ay = lib.project(near)
            pts = {}
            for k, p in points.items():
                px, py = lib.project(p)
                pts[k] = [round((px - ax) / lib.ATLAS_SCALE, 2), round((py - ay) / lib.ATLAS_SCALE, 2)]
            # окна на стенах, обращённых к зрителю: свет ночью
            for wi, (c, nrm) in enumerate(w for w in lib.windows if w[1].dot(fwd) < -0.1):
                px, py = lib.project(c)
                pts[f"win{wi}"] = [round((px - ax) / lib.ATLAS_SCALE, 2), round((py - ay) / lib.ATLAS_SCALE, 2)]
            meta[sprite] = {"ax": round(ax), "ay": round(ay), "corner": [int(near.x), int(near.y)], "points": pts}
            print("points" if points_only else "rendered", sprite)
if render_all_anims and not points_only:
    render_anims(ANIMATED)
json.dump(meta, open(meta_path, "w"), indent=1, sort_keys=True)
