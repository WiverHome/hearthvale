"""
Здания Hearthvale для рендера в Blender. Каждая функция строит модель на клетках 0..s и возвращает
(размер s, высота кадра, точки) - точки (имя -> (x, y, z)): трубы для дыма, ось крыльев мельницы.
Единица - клетка; этаж ~0.62, дверь ~0.45, житель ~0.55. Видимые в обычном повороте стены - x = x0 (смотрит в -x)
и y = y0 (смотрит в -y); окна есть на всех стенах - карту можно поворачивать.
Узнаваемость: свой силуэт, рабочий двор с инструментами и товаром, вывеска-знак ремесла у входа.
"""
import math
from lib import *
from props import *
from props import _flat_rot

# палитра: тёплая, насыщенная, но не кислотная
PLASTER = 0xeee0c4
PLASTER_WARM = 0xecd4a8
PLASTER_WHITE = 0xf2ece0
BEAM = 0x5a3a24
STONE = 0xb8ae9c
STONE_LIGHT = 0xd0c6b2
STONE_DARK = 0x8f8778
ROOF_RED = 0xb84e34
ROOF_BROWN = 0x8a4a30
ROOF_SLATE = 0x4e5a78
THATCH = 0xc8a050
WOOD = 0x9a6a40
LOG = 0x8a6040
FRAME = 0xf2e8d2
SHUTTER = 0x4f6e48
SHUTTER_BLUE = 0x46628a


def M():
    """Общие материалы (кэшируются по имени)"""
    class m:
        plaster = mat("plaster", PLASTER, 0.9, noise=0.12, bump=0.05)
        plaster_warm = mat("plaster_warm", PLASTER_WARM, 0.9, noise=0.12, bump=0.05)
        plaster_white = mat("plaster_white", PLASTER_WHITE, 0.9, noise=0.1, bump=0.05)
        beam = mat("beam", BEAM, 0.7, noise=0.2)
        plinth = mat("plinth", STONE, 0.9, kind="brick", scale=10, noise=0.2)
        stone = mat("stone", STONE_LIGHT, 0.9, kind="brick", scale=7, noise=0.2)
        stone_dark = mat("stone_dark", STONE_DARK, 0.85, noise=0.15)
        roof_red = mat("roof_red", ROOF_RED, 0.75, noise=0.25, kind="brick", scale=18)
        roof_brown = mat("roof_brown", ROOF_BROWN, 0.75, noise=0.25, kind="brick", scale=18)
        slate = mat("slate", ROOF_SLATE, 0.6, noise=0.2, kind="brick", scale=24)
        thatch = mat("thatch", THATCH, 0.95, noise=0.35, bump=0.9)
        shingle = mat("shingle", 0x7a5a3a, 0.85, noise=0.3, kind="brick", scale=22)
        frame = mat("frame", FRAME, 0.6, noise=0.05)
        shutter = mat("shutter", SHUTTER, 0.7, kind="planks", scale=40)
        shutter_blue = mat("shutter_blue", SHUTTER_BLUE, 0.7, kind="planks", scale=40)
        door = mat("door", 0x6a4228, 0.7, kind="planks", scale=34)
        wood = mat("wood", WOOD, 0.75, kind="planks", scale=16)
        logs = mat("logwall", LOG, 0.85, noise=0.2, bump=0.4)
        chim = mat("chimney", 0xa07e6a, 0.9, kind="brick", scale=40)
    return m


def body(x0, y0, x1, y1, z1, wall, plinth=0.1, m=None):
    """Корпус: каменный цоколь и стены до z1"""
    m = m or M()
    box(x0 - 0.025, y0 - 0.025, 0, x1 + 0.025, y1 + 0.025, plinth, m.plinth, bevel=0.015)
    box(x0, y0, plinth, x1, y1, z1, wall, bevel=0.01)


def timber_all(x0, y0, x1, y1, z0, z1, m, posts_x=4, posts_y=3):
    timber_frame(("y", y0, -1), x0, x1, z0, z1, m.beam, posts=posts_x)
    timber_frame(("y", y1, 1), x0, x1, z0, z1, m.beam, posts=posts_x)
    timber_frame(("x", x0, -1), y0, y1, z0, z1, m.beam, posts=posts_y, braces=False)
    timber_frame(("x", x1, 1), y0, y1, z0, z1, m.beam, posts=posts_y, braces=False)


def windows_all(x0, y0, x1, y1, z, m, nx=2, ny=1, w=0.17, h=0.24, shutter=None, skip=()):
    """Окна на всех четырёх стенах: nx на стенах вдоль x, ny - вдоль y; skip - пропустить {("y-1", номер)}"""
    for face, a, b, n in ((("y", y0, -1), x0, x1, nx), (("y", y1, 1), x0, x1, nx), (("x", x0, -1), y0, y1, ny), (("x", x1, 1), y0, y1, ny)):
        for i in range(n):
            if (face[0] + str(face[2]), i) in skip:
                continue
            u = a + (b - a) * (i + 0.5) / n
            window(face, u, z, w, h, m.frame, shutter)


def log_walls(x0, y0, x1, y1, zw, m, r=0.045):
    """Стены из брёвен: сплошной сруб и лежачие брёвна по всем четырём сторонам с выпуском на углах"""
    box(x0, y0, 0, x1, y1, zw, m.logs, bevel=0.01)
    z = r
    while z < zw:
        for (ax, a, b, c) in (("x", y0, y1, x0), ("x", y0, y1, x1), ("y", x0, x1, y0), ("y", x0, x1, y1)):
            if ax == "x":
                o = cylinder(c, (a + b) / 2, 0, 0.01, r, m.logs, verts=10, bevel=0)
                o.scale = (1, 1, (b - a + 0.16) / 0.01); o.rotation_euler = (math.radians(90), 0, 0); o.location = (c, (a + b) / 2, z)
            else:
                o = cylinder((a + b) / 2, c, 0, 0.01, r, m.logs, verts=10, bevel=0)
                o.scale = (1, 1, (b - a + 0.16) / 0.01); o.rotation_euler = (0, math.radians(90), 0); o.location = ((a + b) / 2, c, z)
        z += r * 1.85


# ---------- жильё ----------

LINEN = (0xf4f0e6, 0x8ab0d8, 0xe0b0a0, 0xf0e0a0)


def laundry(x0, x1, y, z, frame, seed=0):
    """Бельё на верёвке между двумя столбиками вдоль x: рубахи и простыни качаются на ветру"""
    post = mat("post", 0x7a5a3a, 0.8, kind="planks", scale=30)
    rope = mat("rope", 0xd8c8a0, 0.9, noise=0)
    cylinder(x0, y, 0, z + 0.02, 0.012, post, verts=6, bevel=0)
    cylinder(x1, y, 0, z + 0.02, 0.012, post, verts=6, bevel=0)
    bar((x0, y, z), (x1, y, z), 0.004, rope, verts=4)
    n = 3
    w = (x1 - x0) / n * 0.85
    for i in range(n):
        u = x0 + (x1 - x0) * (i + 0.5) / n - w / 2
        m = mat(f"linen{(i + seed) % 4}", LINEN[(i + seed) % 4], 0.95, noise=0.08)
        # полотно висит вниз от верёвки и качается поперёк неё
        cloth((u, y, z), (1, 0, 0), w, 0.2 + 0.05 * ((i + seed) % 2), (frame + i) % 4, m, amp=0.04, waves=0.6, seg=4, name="linen")


def house():
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.3, 0.42, 1.72, 1.6
    zp, zw = 0.1, 0.95
    body(x0, y0, x1, y1, zw, m.plaster, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m)
    gable_roof(x0, y0, x1, y1, zw, 0.62, True, m.roof_red, over=0.11, rows=8, gable_mat=m.plaster)
    windows_all(x0, y0, x1, y1, 0.48, m, nx=2, ny=1, shutter=m.shutter, skip={("x-1", 0)})
    window(("x", x0, -1), 0.72, 0.48, 0.15, 0.22, m.frame, m.shutter)
    door(("x", x0, -1), 1.22, 0.2, 0.42, m.door, m.beam, z=zp)
    box(x0 - 0.1, 1.08, zp - 0.05, x0, 1.36, zp, m.plinth, bevel=0.01)
    flower_box(("y", y0, -1), 0.65, 0.47, 0.22)
    flower_box(("y", y0, -1), 1.37, 0.47, 0.22)
    top = chimney(1.35, 1.3, 1.2, 1.95, m.chim)
    barrel(0.17, 1.62)
    log_pile(1.0, y0 - 0.13, along_x=False, n=3)
    box(0.13, 0.55, 0.0, 0.24, 0.9, 0.1, m.wood, bevel=0.01)
    animated(lambda f: laundry(0.3, 0.9, 0.2, 0.42, f))
    return s, 2.2, {"smoke": top}


def house2():
    """Крепкий дом: два этажа, верхний нависает над нижним"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.32, 0.45, 1.68, 1.55
    zp, z1, z2 = 0.1, 0.72, 1.36
    body(x0, y0, x1, y1, z1, m.plaster_warm, zp, m)
    timber_all(x0, y0, x1, y1, zp, z1, m)
    j = 0.06
    box(x0 - j, y0 - j, z1, x1 + j, y1 + j, z1 + 0.05, m.beam, bevel=0.01)
    box(x0 - j, y0 - j, z1 + 0.05, x1 + j, y1 + j, z2, m.plaster_warm, bevel=0.01)
    timber_all(x0 - j, y0 - j, x1 + j, y1 + j, z1 + 0.05, z2, m)
    gable_roof(x0 - j, y0 - j, x1 + j, y1 + j, z2, 0.6, True, m.roof_brown, over=0.1, rows=8, gable_mat=m.plaster_warm)
    windows_all(x0, y0, x1, y1, 0.36, m, nx=2, ny=1, shutter=m.shutter_blue, skip={("x-1", 0)})
    windows_all(x0 - j, y0 - j, x1 + j, y1 + j, 0.95, m, nx=3, ny=2, shutter=m.shutter_blue)
    door(("x", x0, -1), 1.0, 0.2, 0.42, m.door, m.beam, z=zp)
    box(x0 - 0.1, 0.86, zp - 0.05, x0, 1.14, zp, m.plinth, bevel=0.01)
    lantern(x0 - 0.05, 1.18, 0.5)
    top = chimney(1.3, 1.25, 1.6, 2.25, m.chim)
    fence([(0.08, 0.12), (1.9, 0.12)])
    bush(0.6, 0.25, 0.09, flowers=0xf0d040, seed=4)
    bush(1.3, 0.25, 0.09, flowers=0xd85a8a, seed=5)
    barrel(0.15, 1.7)
    animated(lambda f: laundry(0.45, 1.25, 1.8, 0.42, f, seed=1))
    return s, 2.55, {"smoke": top}


def house3():
    """Каменный дом: два этажа камня, сланцевая крыша, две трубы"""
    s = 2
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.28, 0.4, 1.72, 1.6
    zp, z1 = 0.1, 1.32
    body(x0, y0, x1, y1, z1, m.stone, zp, m)
    box(x0 - 0.02, y0 - 0.02, 0.7, x1 + 0.02, y1 + 0.02, 0.75, m.stone_dark, bevel=0.01)
    gable_roof(x0, y0, x1, y1, z1, 0.62, True, m.slate, over=0.1, rows=9, gable_mat=m.stone)
    windows_all(x0, y0, x1, y1, 0.3, m, nx=3, ny=2, h=0.26, skip={("x-1", 1)})
    windows_all(x0, y0, x1, y1, 0.9, m, nx=3, ny=2, h=0.26)
    door(("x", x0, -1), 1.3, 0.22, 0.45, m.door, m.stone_dark, z=zp)
    t1 = chimney(0.55, 1.0, 1.5, 2.25, m.chim)
    t2 = chimney(1.45, 1.0, 1.5, 2.25, m.chim)
    bush(0.12, 0.3, 0.09, seed=7)
    bush(0.12, 0.85, 0.09, flowers=0xffffff, seed=8)
    return s, 2.6, {"smoke": t1, "smoke2": t2}


# ---------- склад и сырьё ----------

def store():
    """Склад: большой амбар из досок, широкие ворота, подъёмная балка, ящики и мешки, телега"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.45, 0.4, 1.75, 1.7
    zp, zw = 0.08, 0.9
    body(x0, y0, x1, y1, zw, m.wood, zp, m)
    for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        box(x - 0.04, y - 0.04, 0, x + 0.04, y + 0.04, zw, m.beam, bevel=0.006)
    gable_roof(x0, y0, x1, y1, zw, 0.7, False, m.shingle, over=0.1, rows=8, gable_mat=m.wood)
    door(("y", y0, -1), 1.1, 0.5, 0.58, mat("gate", 0x7a4e2c, 0.7, kind="planks", scale=24), m.beam, z=zp)
    door(("y", y1, 1), 1.1, 0.5, 0.58, mat("gate", 0x7a4e2c, 0.7, kind="planks", scale=24), m.beam, z=zp)
    for face in (("x", x0, -1), ("x", x1, 1)):
        window(face, 0.8, 0.45, 0.15, 0.18, m.frame)
        window(face, 1.4, 0.45, 0.15, 0.18, m.frame)
    window(("y", y0, -1), 1.1, 1.08, 0.18, 0.18, m.frame)
    bar((1.1, y0, 1.38), (1.1, y0 - 0.3, 1.38), 0.025, m.beam)
    bar((1.1, y0 - 0.27, 1.37), (1.1, y0 - 0.27, 0.9), 0.004, mat("rope", 0xc8b080, 0.9))
    sign(("x", x0, -1), 1.55, 0.78, icon_sack)
    for (cx, cy) in ((0.2, 0.3), (0.2, 0.48), (0.36, 0.25)):
        crate(cx, cy, 0.14)
    crate(0.2, 0.39, 0.12, z=0.14)
    sack = mat("sack", 0xe2d4b0, 0.95, noise=0.1, bump=0.3)
    for (sx_, sy_) in ((0.15, 0.85), (0.15, 1.0), (0.2, 0.92)):
        sphere(sx_, sy_, 0.07, 0.07, sack, sz=1.2)
    barrel(0.18, 1.3); barrel(0.18, 1.47)
    cart(1.55, 0.18, along_x=True, load=lambda x, y, z: [crate(x - 0.06, y, 0.1, z=z), crate(x + 0.06, y, 0.1, z=z)])
    return s, 2.0, {}


def woodcutter():
    """Дровосек: бревенчатая избушка, колода с топором, поленницы, вывеска-топор"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.7, 0.75, 1.75, 1.75
    zw = 0.75
    log_walls(x0, y0, x1, y1, zw, m)
    gable_roof(x0, y0, x1, y1, zw, 0.55, True, m.shingle, over=0.12, rows=7, gable_mat=m.logs)
    door(("y", y0, -1), 1.0, 0.2, 0.42, m.door, m.beam, z=0.02)
    window(("y", y0, -1), 1.45, 0.38, 0.15, 0.18, m.frame, m.shutter)
    window(("x", x0, -1), 1.25, 0.38, 0.15, 0.18, m.frame, m.shutter)
    window(("x", x1, 1), 1.25, 0.38, 0.15, 0.18, m.frame, m.shutter)
    window(("y", y1, 1), 1.25, 0.38, 0.15, 0.18, m.frame, m.shutter)
    sign(("x", x0, -1), 1.5, 0.62, icon_axe)
    top = chimney(1.5, 1.4, 1.0, 1.5, m.plinth)
    cylinder(0.45, 0.45, 0, 0.14, 0.09, mat("stump", 0x8a6a48, 0.85, noise=0.2), verts=12)
    bar((0.45, 0.45, 0.13), (0.38, 0.38, 0.3), 0.01, mat("handle", 0x9a6a3a, 0.6))
    box(0.42, 0.39, 0.12, 0.5, 0.41, 0.17, mat("steel", 0xb8bcc4, 0.3, metal=0.9, noise=0), bevel=0.003)
    log_pile(0.25, 1.2, along_x=False, n=4, r=0.04, length=0.5)
    log_pile(1.3, 0.3, along_x=True, n=3, r=0.04, length=0.5)
    return s, 1.8, {"smoke": top}


def forester():
    """Лесник: бревенчатый домик под моховой крышей, питомник саженцев рядами, лопата и лейка, вывеска-ёлочка"""
    s = 2
    m = M()
    yard(s, 0x9a8a5a)
    x0, y0, x1, y1 = 0.95, 0.95, 1.8, 1.8
    log_walls(x0, y0, x1, y1, 0.7, m, r=0.04)
    gable_roof(x0, y0, x1, y1, 0.7, 0.5, False, mat("mossroof", 0x5a7a3a, 0.9, noise=0.35, bump=0.7), over=0.1, rows=6, gable_mat=m.logs)
    door(("x", x0, -1), 1.35, 0.2, 0.42, m.door, m.beam, z=0.0)
    window(("y", y0, -1), 1.4, 0.36, 0.15, 0.18, m.frame, m.shutter)
    window(("x", x1, 1), 1.35, 0.36, 0.15, 0.18, m.frame)
    window(("y", y1, 1), 1.4, 0.36, 0.15, 0.18, m.frame)
    sign(("x", x0, -1), 1.05, 0.6, icon_sapling, arm=0.3)
    top = chimney(1.6, 1.6, 0.9, 1.35, m.plinth)
    # питомник: грядки с саженцами разного роста
    soil = mat("soil", 0x6a4a30, 0.95, noise=0.3, bump=0.5)
    pine = mat("pine", 0x2f6a3a, 0.8, noise=0.3, bump=0.5)
    leaf = mat("leaves", 0x5a9a42, 0.8, noise=0.35, bump=0.8)
    for row in range(3):
        y = 0.2 + row * 0.25
        box(0.15, y, 0, 1.75, y + 0.17, 0.04, soil, bevel=0.01, lines=False)
        for k in range(6):
            x = 0.25 + k * 0.27
            hgt = 0.12 + 0.05 * ((k + row) % 3)
            if (k + row) % 2 == 0:
                cone(x, y + 0.085, 0.04, 0.04 + hgt, 0.05, pine, verts=8)
            else:
                cylinder(x, y + 0.085, 0.04, 0.04 + hgt * 0.5, 0.008, mat("trunk", 0x6a4a2c, 0.9), verts=6, bevel=0)
                sphere(x, y + 0.085, 0.04 + hgt * 0.7, 0.045, leaf)
    # лопата и лейка у двери
    bar((0.75, 1.15, 0), (0.8, 1.15, 0.38), 0.01, mat("handle", 0x9a6a3a, 0.6))
    box(0.71, 1.13, 0, 0.79, 1.17, 0.08, mat("steel", 0xb8bcc4, 0.3, metal=0.9, noise=0), bevel=0.004)
    can = mat("can", 0x6a8a9a, 0.4, metal=0.6, noise=0.05)
    cylinder(0.55, 1.6, 0, 0.09, 0.05, can, verts=12)
    bar((0.55, 1.6, 0.06), (0.43, 1.6, 0.12), 0.008, can)
    return s, 1.6, {"smoke": top}


def sawmill():
    """Лесопилка: открытый навес на столбах, козлы с бревном и рамной пилой, штабеля досок"""
    s = 2
    m = M()
    yard(s, 0xb8a078)
    x0, y0, x1, y1 = 0.35, 0.5, 1.75, 1.6
    zw = 0.85
    for x in (x0, (x0 + x1) / 2, x1):
        for y in (y0, y1):
            box(x - 0.04, y - 0.04, 0, x + 0.04, y + 0.04, zw, m.beam, bevel=0.008)
    box(x0, y1 - 0.02, 0, x1, y1 + 0.02, zw, m.wood, bevel=0.005)
    gable_roof(x0, y0, x1, y1, zw, 0.5, True, m.shingle, over=0.12, rows=7)
    wood = mat("logbark", 0x7a5232, 0.85, noise=0.2, bump=0.4)
    for x in (0.65, 1.45):
        bar((x - 0.08, 1.05, 0), (x, 1.05, 0.3), 0.02, m.beam)
        bar((x + 0.08, 1.05, 0), (x, 1.05, 0.3), 0.02, m.beam)
    o = cylinder(1.05, 1.05, 0, 0.01, 0.08, wood, verts=12)
    o.scale = (1, 1, 110); o.rotation_euler = (0, math.radians(90), 0); o.location = (1.05, 1.05, 0.38)
    steel = mat("steel", 0xb8bcc4, 0.3, metal=0.9, noise=0)
    box(1.03, 0.98, 0.2, 1.07, 1.12, 0.75, m.beam, bevel=0.004)
    box(1.045, 1.0, 0.25, 1.055, 1.1, 0.7, steel, bevel=0)
    planks = mat("planks_pale", 0xd8b078, 0.8, kind="planks", scale=30)
    for i in range(5):
        box(0.15, 0.2, i * 0.035, 0.85, 0.36, 0.035 + i * 0.035, planks, bevel=0.004)
    for i in range(3):
        box(1.0, 0.18, i * 0.035, 1.7, 0.32, 0.035 + i * 0.035, planks, bevel=0.004)
    sphere(0.6, 0.85, 0.0, 0.12, mat("sawdust", 0xe8c890, 0.95, noise=0.2, bump=0.5), sz=0.3)
    sign(("x", x0, -1), 0.65, 0.7, icon_saw, arm=0.32)
    return s, 1.7, {}


def quarry():
    """Каменоломня: скальный выступ, деревянный кран, тёсаные блоки, навес с тачкой"""
    s = 2
    m = M()
    yard(s, 0xa09888)
    rock = mat("rock", 0x9a968c, 0.95, noise=0.3, bump=0.8)
    rr = rng(11)
    for i in range(9):
        x = rr.uniform(0.9, 1.75); y = rr.uniform(0.9, 1.75)
        w = rr.uniform(0.25, 0.42)
        box(x - w / 2, y - w / 2, 0, x + w / 2, y + w / 2, rr.uniform(0.25, 0.75), rock, bevel=0.06)
    bar((0.55, 0.9, 0), (0.55, 0.9, 1.2), 0.035, m.beam)
    bar((0.55, 0.9, 1.15), (0.2, 0.4, 0.95), 0.025, m.beam)
    bar((0.55, 0.9, 0.5), (0.3, 0.55, 0.85), 0.018, m.beam)
    bar((0.22, 0.42, 0.95), (0.22, 0.42, 0.25), 0.004, mat("rope", 0xc8b080, 0.9))
    stones_pile(0.25, 0.4, n=1, size=0.07)
    stones_pile(1.05, 0.3, n=6, seed=5, size=0.06)
    for (x, y) in ((0.15, 1.2), (0.55, 1.2), (0.15, 1.75), (0.55, 1.75)):
        box(x - 0.025, y - 0.025, 0, x + 0.025, y + 0.025, 0.55, m.beam, bevel=0.005)
    gable_roof(0.12, 1.17, 0.58, 1.78, 0.55, 0.22, False, m.shingle, over=0.06, rows=4)
    cart(0.4, 1.45, along_x=False, load=lambda x, y, z: stones_pile(x, y, n=2, size=0.04))
    box(0.08, 0.75, 0, 0.13, 0.8, 0.7, m.beam, bevel=0.005)
    sign(("x", 0.13, 1), 0.775, 0.66, icon_pick, arm=0.32)
    return s, 1.6, {}


def farm():
    """Ферма: дом с соломенной крышей, амбар, поле пшеницы рядами, стог, изгородь"""
    s = 3
    m = M()
    yard(s, 0xa89870)
    soil = mat("soil", 0x7a5a38, 0.95, noise=0.3, bump=0.5)
    wheat = mat("wheatfield", 0xe0b448, 0.85, noise=0.3, bump=0.9)
    box(0.15, 0.15, 0, 2.85, 1.35, 0.03, soil, bevel=0.01, lines=False)
    for i in range(7):
        y = 0.25 + i * 0.155
        box(0.2, y, 0.03, 2.8, y + 0.09, 0.12, wheat, bevel=0.03)
    fence([(0.08, 1.4), (0.08, 0.08), (2.92, 0.08), (2.92, 1.4)])
    x0, y0, x1, y1 = 0.3, 1.6, 1.5, 2.75
    body(x0, y0, x1, y1, 0.85, m.plaster_white, 0.1, m)
    timber_all(x0, y0, x1, y1, 0.1, 0.85, m, posts_x=3, posts_y=3)
    gable_roof(x0, y0, x1, y1, 0.85, 0.65, True, m.thatch, over=0.14, thick=0.09, rows=6, gable_mat=m.plaster_white)
    windows_all(x0, y0, x1, y1, 0.42, m, nx=2, ny=1, shutter=m.shutter)
    door(("x", x0, -1), 2.45, 0.2, 0.42, m.door, m.beam, z=0.1)
    sign(("y", y0, -1), 0.45, 0.72, icon_wheat)
    top = chimney(1.2, 2.4, 1.0, 1.75, m.chim)
    barn = mat("barnwood", 0x9a4a32, 0.8, kind="planks", scale=16)
    bx0, by0, bx1, by1 = 1.75, 1.6, 2.85, 2.8
    body(bx0, by0, bx1, by1, 0.95, barn, 0.06, m)
    gable_roof(bx0, by0, bx1, by1, 0.95, 0.7, False, m.shingle, over=0.1, rows=7, gable_mat=barn)
    door(("y", by0, -1), 2.3, 0.45, 0.55, mat("gate", 0x7a4e2c, 0.7, kind="planks", scale=24), m.frame, z=0.06)
    hay(1.6, 1.48, 0.15)
    return s, 2.3, {"smoke": top}


def mill():
    """Мельница: побелённая башня конусом, соломенный шатёр; крылья - отдельный спрайт (крутятся)"""
    s = 2
    m = M()
    yard(s)
    cx, cy = 1.05, 1.05
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=0.6, radius2=0.45, depth=1.55, location=(cx, cy, 0.775))
    o = bpy.context.object
    o.data.materials.append(m.plaster_white)
    o.modifiers.new("b", 'BEVEL').width = 0.01
    cylinder(cx, cy, 0, 0.12, 0.63, m.plinth, verts=24)
    cone(cx, cy, 1.55, 2.25, 0.55, m.thatch, verts=24, eave=True)
    cylinder(cx, cy, 1.5, 1.58, 0.5, m.beam, verts=24)
    door(("y", cy - 0.56, -1), cx - 0.1, 0.2, 0.42, m.door, m.beam, z=0.12)
    window(("x", cx - 0.53, -1), cy, 0.75, 0.14, 0.2, m.frame)
    window(("x", cx + 0.5, 1), cy, 0.75, 0.14, 0.2, m.frame)
    window(("y", cy + 0.5, 1), cx, 0.75, 0.14, 0.2, m.frame)
    sack = mat("sack", 0xe2d4b0, 0.95, noise=0.1, bump=0.3)
    for (sx_, sy_, sz_) in ((0.45, 0.32, 0.07), (0.6, 0.28, 0.07), (0.52, 0.3, 0.18)):
        sphere(sx_, sy_, sz_, 0.07, sack, sz=1.2)
    sign(("y", cy - 0.56, -1), cx + 0.25, 0.7, icon_sack, arm=0.32)
    cart(1.6, 0.35, along_x=True, load=lambda x, y, z: [sphere(x - 0.05, y, z + 0.06, 0.06, sack, sz=1.1), sphere(x + 0.06, y, z + 0.06, 0.06, sack, sz=1.1)])
    # ось крыльев - на оси башни под шатром; крылья всегда обращены к зрителю
    return s, 2.6, {"hub": (cx, cy, 1.62)}


def mill_sails(frame):
    """Крылья мельницы в плоскости xz: 4 лопасти с полотном, кадр frame поворачивает на 22.5 градуса"""
    wood = mat("sailwood", 0xd8c8a8, 0.8, noise=0.1)
    cloth = mat("sailcloth", 0xf4ecdc, 0.9, noise=0.1)
    beam = mat("beam", BEAM, 0.7, noise=0.2)
    L = 1.05
    for k in range(4):
        a = math.radians(frame * 22.5 + k * 90 + 12)
        dx, dz = math.cos(a), math.sin(a)
        bar((0, 0, 0), (dx * L, 0, dz * L), 0.022, beam)
        px, pz = -dz, dx
        verts = [(dx * 0.18, 0.01, dz * 0.18), (dx * L, 0.01, dz * L), (dx * L + px * 0.22, 0.01, dz * L + pz * 0.22), (dx * 0.18 + px * 0.16, 0.01, dz * 0.18 + pz * 0.16)]
        o = mesh_from(verts, [(0, 1, 2, 3)], cloth, name="sail")
        o.modifiers.new("t", 'SOLIDIFY').thickness = 0.01
        for t in (0.35, 0.6, 0.85):
            bar((dx * L * t, 0.0, dz * L * t), (dx * L * t + px * 0.2, 0.0, dz * L * t + pz * 0.2), 0.006, wood, verts=4)
    sphere(0, 0, 0, 0.06, beam)


def bakery():
    s = 2
    m = M()
    yard(s)
    brick = mat("oven", 0xb86a48, 0.85, kind="brick", scale=26)
    x0, y0, x1, y1 = 0.62, 0.55, 1.75, 1.65
    zp, zw = 0.1, 0.9
    body(x0, y0, x1, y1, zw, m.plaster_warm, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m, posts_x=3, posts_y=3)
    gable_roof(x0, y0, x1, y1, zw, 0.58, False, m.roof_red, over=0.1, rows=7, gable_mat=m.plaster_warm)
    windows_all(x0, y0, x1, y1, 0.45, m, nx=1, ny=2, w=0.2, skip={("y-1", 0)})
    window(("y", y0, -1), 1.45, 0.45, 0.2, 0.22, m.frame)
    door(("y", y0, -1), 0.95, 0.2, 0.42, m.door, m.beam, z=zp)
    sign(("y", y0, -1), 0.7, 0.72, icon_pretzel)
    box(0.12, 0.78, 0.0, 0.62, 1.32, 0.08, m.plinth, bevel=0.01)
    sphere(0.38, 1.05, 0.08, 0.27, brick, sx=1.0, sy=1.1, sz=0.85)
    fire = mat("ovenfire", 0xff8a30, 0.9, noise=0)
    b = fire.node_tree.nodes.get("Principled BSDF")
    if b is not None:
        b.inputs["Emission Color"].default_value = (1.0, 0.45, 0.1, 1)
        b.inputs["Emission Strength"].default_value = 3.0
    box(0.09, 0.97, 0.08, 0.15, 1.13, 0.22, fire, bevel=0.02)
    top = chimney(0.42, 1.25, 0.1, 0.75, brick)
    box(1.0, 0.18, 0, 1.6, 0.36, 0.22, m.wood, bevel=0.01)
    bread = mat("bread", 0xc8843e, 0.6, noise=0.15, bump=0.3)
    for i in range(4):
        sphere(1.08 + i * 0.15, 0.27, 0.25, 0.055, bread, sx=1.4, sz=0.7)
    awning(1.0, 0.12, 1.6, 0.45, 0.62, 0.48, True, colors=(0xc8a030, 0xf4ecd8), stripes=5)
    for x in (1.02, 1.58):
        box(x - 0.015, 0.13, 0, x + 0.015, 0.16, 0.48, m.beam, bevel=0)
    sack = mat("sack", 0xe2d4b0, 0.95, noise=0.1, bump=0.3)
    for (sx_, sy_, sz_) in ((1.85, 0.6, 0.07), (1.85, 0.78, 0.07)):
        sphere(sx_, sy_, sz_, 0.07, sack, sz=1.2)
    return s, 1.9, {"smoke": top}


def fisher(shore=False):
    """
    Рыбак: хижина на сваях у заднего края, двор с сетями и бочкой, вывеска-рыба.
    Вода - со стороны -y (в игре здание разворачивается к воде). shore - вода вплотную:
    мостки выходят за площадку на воду, лодка у мостков; иначе лодка на козлах во дворе.
    """
    s = 2
    m = M()
    yard(s, 0xd0c090)
    bluewood = mat("bluewood", 0x6a7f96, 0.8, kind="planks", scale=18)
    x0, y0, x1, y1 = 0.45, 1.0, 1.65, 1.85
    for x in (x0, x1):
        for y in (y0, y1):
            box(x - 0.03, y - 0.03, 0, x + 0.03, y + 0.03, 0.15, m.beam, bevel=0.005)
    box(x0 - 0.05, y0 - 0.12, 0.15, x1 + 0.05, y1 + 0.05, 0.19, m.wood, bevel=0.008)
    box(x0, y0, 0.19, x1, y1, 0.85, bluewood, bevel=0.01)
    gable_roof(x0, y0, x1, y1, 0.85, 0.5, True, m.slate, over=0.1, rows=7, gable_mat=bluewood)
    door(("y", y0, -1), 1.05, 0.2, 0.42, m.door, m.beam, z=0.19)
    window(("y", y0, -1), 1.42, 0.48, 0.16, 0.2, m.frame, m.shutter)
    window(("y", y0, -1), 0.7, 0.48, 0.16, 0.2, m.frame, m.shutter)
    window(("x", x0, -1), 1.42, 0.48, 0.14, 0.2, m.frame)
    window(("x", x1, 1), 1.42, 0.48, 0.14, 0.2, m.frame)
    window(("y", y1, 1), 1.05, 0.48, 0.16, 0.2, m.frame, m.shutter)
    sign(("x", x0, -1), 1.15, 0.75, icon_fish)
    # сети на шестах и бочка с рыбой
    net = mat("net", 0x8a7a5a, 0.9, noise=0.2)
    for x in (0.15, 0.6):
        box(x - 0.015, 0.7 - 0.015, 0, x + 0.015, 0.7 + 0.015, 0.5, m.beam, bevel=0)
    o = mesh_from([(0.15, 0.7, 0.48), (0.6, 0.7, 0.48), (0.55, 0.7, 0.2), (0.2, 0.7, 0.22)], [(0, 1, 2, 3)], net, name="net")
    o.modifiers.new("s", 'SUBSURF').levels = 3
    o.modifiers.new("w", 'WIREFRAME').thickness = 0.006
    barrel(1.85, 0.75)
    fish = mat("fish", 0x8aa6b8, 0.35, metal=0.3, noise=0.1)
    for k in range(3):
        sphere(1.82 + k * 0.03, 0.73 + k * 0.02, 0.17, 0.025, fish, sx=2.0, sz=0.6)
    if shore:
        # мостки через край площадки на воду (соседняя клетка - вода), сваи, лодка у мостков
        for i in range(9):
            y = 0.9 - i * 0.14
            box(0.95, y - 0.1, 0.1, 1.25, y, 0.13, m.wood, bevel=0.004)
        for y in (0.6, 0.1, -0.4):
            for x in (0.97, 1.23):
                box(x - 0.02, y - 0.02, -0.3, x + 0.02, y + 0.02, 0.15, m.beam, bevel=0)
        boat(0.65, -0.25, along_x=False, length=0.6)
        bar((0.75, -0.1, 0.08), (0.97, 0.0, 0.12), 0.004, mat("rope", 0xc8b080, 0.9))
        box(0.1, -0.02, 0, 1.9, 0.12, 0.02, mat("wetsand", 0xb8a070, 0.9, noise=0.2), bevel=0.01, lines=False)
    else:
        # лодка на козлах и вёсла
        for y in (0.25, 0.65):
            bar((1.2, y, 0), (1.3, y, 0.18), 0.012, m.beam)
            bar((1.4, y, 0), (1.3, y, 0.18), 0.012, m.beam)
        boat(1.3, 0.45, along_x=False, length=0.6)
        bar((1.6, 0.2, 0.02), (1.75, 0.65, 0.02), 0.01, m.wood)
    return s, 1.8, {}


def fisher_shore():
    return fisher(True)


def well():
    """Колодец: каменное кольцо, двускатный навес на столбах, ворот, ведро"""
    s = 2
    m = M()
    yard(s, cobble=True)
    cx, cy = 1.0, 1.0
    stone = mat("wellstone", 0xb8b0a0, 0.9, kind="brick", scale=18, noise=0.2)
    cylinder(cx, cy, 0, 0.36, 0.5, stone, verts=24)
    cylinder(cx, cy, 0.34, 0.39, 0.53, m.stone_dark, verts=24)
    cylinder(cx, cy, 0.24, 0.35, 0.42, mat("wellwater", 0x2a4a70, 0.05, noise=0), verts=24, bevel=0)
    for y in (cy - 0.46, cy + 0.46):
        box(cx - 0.035, y - 0.035, 0.34, cx + 0.035, y + 0.035, 1.25, m.beam, bevel=0.006)
    gable_roof(cx - 0.5, cy - 0.62, cx + 0.5, cy + 0.62, 1.25, 0.45, False, m.shingle, over=0.06, rows=5)
    bar((cx, cy - 0.48, 1.0), (cx, cy + 0.48, 1.0), 0.045, m.wood)
    bar((cx, cy + 0.48, 1.0), (cx - 0.14, cy + 0.56, 0.92), 0.014, iron())
    bar((cx, cy, 1.0), (cx, cy, 0.66), 0.005, iron())
    bucket = mat("bucket", 0x8a6a42, 0.7, kind="planks", scale=60)
    cylinder(cx, cy, 0.5, 0.66, 0.075, bucket, verts=12)
    cylinder(0.4, 0.4, 0, 0.14, 0.08, bucket, verts=12)
    box(1.45, 0.2, 0, 1.85, 0.42, 0.14, m.wood, bevel=0.01)   # корыто
    box(1.48, 0.23, 0.1, 1.82, 0.39, 0.13, mat("wellwater", 0x2a4a70, 0.05, noise=0), bevel=0)
    return s, 1.7, {}


def market():
    """Рынок: три лавки с полосатыми навесами - фрукты, ткани, горшки; бочки и ящики, вывеска-весы"""
    s = 2
    m = M()
    yard(s, 0xb0a084, cobble=True)

    def stall(x0, y0, x1, y1, colors, goods):
        for x in (x0, x1):
            for y in (y0, y1):
                box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.62 if y == y1 else 0.5, m.beam, bevel=0.004)
        box(x0, y0, 0.2, x1, y1, 0.26, m.wood, bevel=0.008)
        box(x0 + 0.02, y0 + 0.02, 0, x1 - 0.02, y1 - 0.02, 0.2, mat("stallbase", 0x7a5232, 0.8, kind="planks", scale=30), bevel=0.006)
        awning(x0 - 0.04, y0 - 0.08, x1 + 0.04, y1 + 0.02, 0.66, 0.5, True, colors=colors)
        goods(x0, y0, x1, y1)

    def fruit(x0, y0, x1, y1):
        for i, c in enumerate((0xc8302a, 0xe8a030, 0x7ab040)):
            fm = mat("fr%x" % c, c, 0.4, noise=0.1)
            bx = x0 + 0.06 + i * (x1 - x0 - 0.12) / 3
            box(bx, y0 + 0.04, 0.26, bx + 0.13, y0 + 0.2, 0.3, m.wood, bevel=0.004)
            for k in range(4):
                sphere(bx + 0.035 + (k % 2) * 0.06, y0 + 0.08 + (k // 2) * 0.07, 0.33, 0.032, fm)

    def cloth(x0, y0, x1, y1):
        for i, c in enumerate((0x3a5aa8, 0xc8a030, 0x8a3a8a, 0x3a8a5a)):
            cm = mat("cl%x" % c, c, 0.9, noise=0.1)
            bx = x0 + 0.05 + i * (x1 - x0 - 0.1) / 4
            box(bx, y0 + 0.05, 0.26, bx + (x1 - x0 - 0.1) / 4 - 0.01, y0 + 0.22, 0.26 + 0.03 * (4 - i), cm, bevel=0.01)

    def pots(x0, y0, x1, y1):
        clay = mat("clay", 0xb8643a, 0.7, noise=0.15)
        for i in range(4):
            cylinder(x0 + 0.08 + i * 0.12, y0 + 0.12, 0.26, 0.36 + 0.03 * (i % 2), 0.045, clay, verts=12)

    stall(0.2, 0.25, 0.95, 0.65, (0xc83a30, 0xf4ecd8), fruit)
    stall(1.1, 0.25, 1.85, 0.65, (0x3a62b0, 0xf4ecd8), cloth)
    stall(0.2, 1.15, 0.95, 1.55, (0x3a8a4a, 0xf4ecd8), pots)
    stall(1.1, 1.15, 1.85, 1.55, (0xc8a030, 0xf4ecd8), fruit)
    barrel(1.3, 0.95); crate(1.7, 0.92, 0.14); crate(1.7, 0.92, 0.11, z=0.14)
    box(0.06, 0.82, 0, 0.11, 0.87, 0.75, m.beam, bevel=0.004)
    sign(("x", 0.11, 1), 0.845, 0.7, icon_scales, arm=0.3)
    return s, 1.5, {}


def church():
    s = 3
    m = M()
    yard(s, 0xb0a084, cobble=True)
    stone = mat("church_stone", 0xd2c8b4, 0.9, kind="brick", scale=6, noise=0.2)
    trim = mat("church_trim", STONE_DARK, 0.85, noise=0.1)
    glassm = mat("stained", 0x3a4a8a, 0.2, noise=0.3)
    nx0, ny0, nx1, ny1 = 1.1, 0.85, 2.8, 2.15
    box(nx0 - 0.03, ny0 - 0.03, 0, nx1 + 0.03, ny1 + 0.03, 0.12, trim, bevel=0.015)
    box(nx0, ny0, 0.12, nx1, ny1, 1.25, stone, bevel=0.01)
    for x in (1.5, 1.95, 2.4):
        for y, d in ((ny0, -1), (ny1, 1)):
            box(x - 0.05, min(y, y + d * 0.12), 0, x + 0.05, max(y, y + d * 0.12), 0.9, trim, bevel=0.012)
    gable_roof(nx0, ny0, nx1, ny1, 1.25, 0.8, True, m.slate, over=0.08, rows=10, gable_mat=stone)
    for x in (1.28, 1.73, 2.18, 2.6):
        window(("y", ny0, -1), x, 0.45, 0.14, 0.5, trim, None)
        window(("y", ny1, 1), x, 0.45, 0.14, 0.5, trim, None)
    o = cylinder(nx1 + 0.01, (ny0 + ny1) / 2, 0, 0.01, 0.16, glassm, verts=20, bevel=0)
    o.rotation_euler = (0, math.radians(90), 0); o.location = (nx1 + 0.012, (ny0 + ny1) / 2, 1.45)
    tx0, ty0, tx1, ty1 = 0.35, 1.1, 1.15, 1.9
    box(tx0 - 0.03, ty0 - 0.03, 0, tx1 + 0.03, ty1 + 0.03, 0.14, trim, bevel=0.015)
    box(tx0, ty0, 0.14, tx1, ty1, 2.5, stone, bevel=0.01)
    for z in (1.0, 1.8):
        box(tx0 - 0.03, ty0 - 0.03, z, tx1 + 0.03, ty1 + 0.03, z + 0.06, trim, bevel=0.01)
    box(tx0 - 0.05, ty0 - 0.05, 2.5, tx1 + 0.05, ty1 + 0.05, 2.6, trim, bevel=0.01)
    for face, u in ((("x", tx0, -1), 1.5), (("y", ty0, -1), 0.75), (("x", tx1, 1), 1.5), (("y", ty1, 1), 0.75)):
        window(face, u, 2.0, 0.24, 0.38, trim, None)
        window(face, u, 1.25, 0.12, 0.35, trim, None)
    bell = mat("bell", 0xc89a3a, 0.3, metal=0.9, noise=0)
    cone((tx0 + tx1) / 2, (ty0 + ty1) / 2, 2.05, 2.32, 0.13, bell, verts=16)
    door(("x", tx0, -1), 1.5, 0.3, 0.55, mat("church_door", 0x5a3420, 0.6, kind="planks", scale=30), trim, z=0.14)
    box(tx0 - 0.18, 1.3, 0, tx0, 1.7, 0.07, trim, bevel=0.01)
    cx, cy = (tx0 + tx1) / 2, (ty0 + ty1) / 2
    h0, h1 = 2.6, 4.0
    r = 0.46
    verts = [(cx - r, cy - r, h0), (cx + r, cy - r, h0), (cx + r, cy + r, h0), (cx - r, cy + r, h0), (cx, cy, h1)]
    mesh_from(verts, [(0, 1, 4), (1, 2, 4), (2, 3, 4), (3, 0, 4), (3, 2, 1, 0)], m.slate, name="spire")
    gold = mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0)
    box(cx - 0.015, cy - 0.015, h1, cx + 0.015, cy + 0.015, h1 + 0.3, gold, bevel=0)
    box(cx - 0.015, cy - 0.09, h1 + 0.17, cx + 0.015, cy + 0.09, h1 + 0.21, gold, bevel=0)
    for y in (1.05, 1.95):
        bush(0.15, y, 0.1, flowers=0xd85a6a, seed=int(y * 10))
    tree(2.75, 0.4, h=1.0, r=0.3, seed=3)
    return s, 4.5, {}


def hunter():
    """Охотник: бревенчатый домик с моховой крышей, рога над дверью, шкура на раме"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.75, 0.7, 1.75, 1.7
    zw = 0.78
    log_walls(x0, y0, x1, y1, zw, m, r=0.04)
    gable_roof(x0, y0, x1, y1, zw, 0.55, True, mat("mossroof", 0x5a7a3a, 0.9, noise=0.35, bump=0.7), over=0.12, rows=6, gable_mat=m.logs)
    door(("y", y0, -1), 1.05, 0.2, 0.42, m.door, m.beam, z=0.0)
    window(("y", y0, -1), 1.5, 0.38, 0.15, 0.18, m.frame)
    window(("x", x0, -1), 1.2, 0.38, 0.15, 0.18, m.frame)
    window(("x", x1, 1), 1.2, 0.38, 0.15, 0.18, m.frame)
    window(("y", y1, 1), 1.25, 0.38, 0.15, 0.18, m.frame)
    icon_antlers(1.05, y0 - 0.02, 0.6, "y")
    sign(("x", x0, -1), 0.95, 0.65, icon_antlers, arm=0.32)
    for x in (0.2, 0.6):
        box(x - 0.015, 0.3, 0, x + 0.015, 0.33, 0.55, m.beam, bevel=0)
    bar((0.2, 0.315, 0.52), (0.6, 0.315, 0.52), 0.012, m.beam)
    hide = mat("hide", 0x9a6a3e, 0.9, noise=0.3, bump=0.4)
    o = mesh_from([(0.25, 0.315, 0.5), (0.55, 0.315, 0.5), (0.5, 0.315, 0.12), (0.3, 0.315, 0.12)], [(0, 1, 2, 3)], hide, name="hide")
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.01
    top = chimney(1.5, 1.4, 1.0, 1.5, m.plinth)
    log_pile(0.3, 1.3, along_x=False, n=3)
    return s, 1.7, {"smoke": top}


def orchard():
    """Сад: яблони, сарайчик, корзины с яблоками, лестница"""
    s = 2
    m = M()
    yard(s, 0x8aa050)
    for i, (x, y) in enumerate(((0.45, 0.45), (1.0, 0.4), (0.4, 1.05), (1.0, 1.0), (0.45, 1.6))):
        tree(x, y, h=0.85, r=0.27, fruit=0xd0302a, seed=i + 20)
    x0, y0, x1, y1 = 1.4, 1.3, 1.85, 1.85
    body(x0, y0, x1, y1, 0.55, m.wood, 0.05, m)
    gable_roof(x0, y0, x1, y1, 0.55, 0.3, True, m.shingle, over=0.06, rows=4)
    door(("x", x0, -1), 1.58, 0.18, 0.38, m.door, m.beam, z=0.05)
    sign(("y", y0, -1), 1.6, 0.48, icon_apple, arm=0.32)
    basket = mat("basket", 0xb08a4a, 0.85, kind="planks", scale=70)
    red = mat("apple", 0xc8302a, 0.35, noise=0.15)
    for (bx, by) in ((1.45, 0.3), (1.65, 0.38)):
        cylinder(bx, by, 0, 0.1, 0.075, basket, verts=12)
        for k in range(4):
            sphere(bx + (k % 2 - 0.5) * 0.06, by + (k // 2 - 0.5) * 0.06, 0.11, 0.032, red)
    bar((1.25, 0.75, 0), (1.15, 0.65, 0.65), 0.012, m.wood)
    bar((1.32, 0.68, 0), (1.22, 0.58, 0.65), 0.012, m.wood)
    return s, 1.5, {}


def pasture():
    """Пастбище: загон с изгородью, овцы, пастушья хижина, кормушка"""
    s = 2
    m = M()
    yard(s, 0x7aa848)
    fence([(0.08, 1.92), (0.08, 0.08), (1.92, 0.08), (1.92, 1.25)])
    for i, (x, y, r) in enumerate(((0.45, 0.4, 0.3), (0.95, 0.3, 2.0), (0.6, 0.85, 4.0), (1.4, 0.55, 1.0), (0.3, 1.3, 5.5))):
        sheep(x, y, r, seed=i)
    x0, y0, x1, y1 = 1.25, 1.3, 1.85, 1.85
    body(x0, y0, x1, y1, 0.6, m.wood, 0.05, m)
    gable_roof(x0, y0, x1, y1, 0.6, 0.35, False, m.thatch, over=0.08, thick=0.07, rows=4, gable_mat=m.wood)
    door(("x", x0, -1), 1.58, 0.18, 0.4, m.door, m.beam, z=0.05)
    sign(("y", y0, -1), 1.4, 0.5, icon_wool, arm=0.32)
    box(1.0, 1.1, 0, 1.2, 1.4, 0.1, m.wood, bevel=0.01)
    sphere(1.1, 1.25, 0.1, 0.09, mat("hay", 0xdcb050, 0.95, noise=0.3, bump=1.0), sz=0.5)
    return s, 1.3, {}


def weaver():
    """Ткач: фахверковый дом, ткацкий станок под навесом, крашеные ткани на верёвке, тюки шерсти"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.7, 0.75, 1.75, 1.75
    zp, zw = 0.1, 0.92
    body(x0, y0, x1, y1, zw, m.plaster, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m, posts_x=3, posts_y=3)
    gable_roof(x0, y0, x1, y1, zw, 0.6, True, mat("roof_purple", 0x6a4a8a, 0.7, noise=0.25, kind="brick", scale=18), over=0.1, rows=7, gable_mat=m.plaster)
    windows_all(x0, y0, x1, y1, 0.46, m, nx=2, ny=2, shutter=m.shutter, skip={("y-1", 0)})
    door(("y", y0, -1), 0.95, 0.2, 0.42, m.door, m.beam, z=zp)
    sign(("x", x0, -1), 1.0, 0.75, icon_yarn)
    for (x, y) in ((0.15, 0.2), (0.6, 0.2), (0.15, 0.65), (0.6, 0.65)):
        box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.6, m.beam, bevel=0)
    gable_roof(0.12, 0.17, 0.66, 0.68, 0.6, 0.18, True, m.shingle, over=0.04, rows=3)
    for x in (0.22, 0.52):
        box(x - 0.02, 0.35, 0, x + 0.02, 0.38, 0.4, m.wood, bevel=0)
    bar((0.22, 0.365, 0.38), (0.52, 0.365, 0.38), 0.015, m.wood)
    bar((0.22, 0.365, 0.15), (0.52, 0.365, 0.15), 0.015, m.wood)
    box(0.24, 0.36, 0.16, 0.5, 0.37, 0.37, mat("weave", 0xc84a5a, 0.9, noise=0.1), bevel=0)
    bar((1.0, 0.25, 0.55), (1.85, 0.25, 0.55), 0.004, mat("rope", 0xc8b080, 0.9))
    for x in (1.0, 1.85):
        box(x - 0.015, 0.235, 0, x + 0.015, 0.265, 0.58, m.beam, bevel=0)
    for i, c in enumerate((0x3a62b0, 0xd8b030, 0x3a8a4a)):
        box(1.08 + i * 0.25, 0.245, 0.25, 1.28 + i * 0.25, 0.255, 0.55, mat("cl%x" % c, c, 0.9, noise=0.1), bevel=0)
    wool = mat("wool", 0xf2eee4, 0.95, noise=0.1, bump=0.8)
    for (bx, by, bz) in ((0.25, 1.1, 0.08), (0.25, 1.3, 0.08), (0.27, 1.2, 0.22)):
        sphere(bx, by, bz, 0.09, wool)
    return s, 1.8, {}


def brewery():
    """Пивоварня: каменный дом, медный чан с трубой, бочки штабелем, хмель на жердях, вывеска-бочка"""
    s = 2
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.75, 0.75, 1.8, 1.8
    body(x0, y0, x1, y1, 0.95, m.stone, 0.1, m)
    gable_roof(x0, y0, x1, y1, 0.95, 0.6, False, m.roof_red, over=0.1, rows=7, gable_mat=m.stone)
    windows_all(x0, y0, x1, y1, 0.48, m, nx=2, ny=2, skip={("x-1", 0)})
    door(("x", x0, -1), 1.05, 0.24, 0.45, m.door, m.stone_dark, z=0.1)
    sign(("y", y0, -1), 1.0, 0.8, icon_barrel)
    copper = mat("copper", 0xc8743a, 0.3, metal=0.9, noise=0.05)
    cylinder(0.4, 1.35, 0, 0.12, 0.25, m.plinth, verts=16)
    cylinder(0.4, 1.35, 0.12, 0.42, 0.22, copper, verts=20)
    sphere(0.4, 1.35, 0.42, 0.22, copper, sz=0.5)
    top = chimney(0.4, 1.35, 0.5, 0.95, copper)
    wood = mat("barrel", 0x8a5a32, 0.7, kind="planks", scale=40)
    for (bx, bz) in ((0.95, 0.09), (1.15, 0.09), (1.35, 0.09), (1.05, 0.25), (1.25, 0.25)):
        o = cylinder(bx, 0.35, 0, 0.01, 0.09, wood, verts=14)
        o.scale = (1, 1, 22); o.rotation_euler = (math.radians(90), 0, 0); o.location = (bx, 0.35, bz)
    hop = mat("hop", 0x6aa040, 0.8, noise=0.3, bump=0.8)
    for x in (0.2, 0.45):
        bar((x, 0.25, 0), (x, 0.25, 0.8), 0.01, m.wood)
        for k in range(4):
            sphere(x + 0.02 * (k % 2), 0.25, 0.3 + k * 0.13, 0.06, hop)
    return s, 1.8, {"smoke": top}


# ---------- услуги ----------

def hall():
    """Ратуша: каменный низ с аркадой, фахверковый верх, высокая крыша, башенка с часами и флагом, знамёна"""
    s = 3
    m = M()
    yard(s, 0xb0a084, cobble=True)
    x0, y0, x1, y1 = 0.45, 0.6, 2.6, 2.45
    z1, z2 = 0.8, 1.5
    body(x0, y0, x1, y1, z1, m.stone, 0.12, m)
    dark = mat("arcade", 0x3a2e26, 0.9, noise=0)
    for x in (0.85, 1.3, 1.75, 2.2):
        for y, d in ((y0, -1), (y1, 1)):
            box(x - 0.14, y - 0.01 * -d, 0.12, x + 0.14, y + 0.02 * d, 0.6, dark, bevel=0)
    j = 0.08
    box(x0 - j, y0 - j, z1, x1 + j, y1 + j, z1 + 0.06, m.beam, bevel=0.01)
    box(x0 - j, y0 - j, z1 + 0.06, x1 + j, y1 + j, z2, m.plaster_white, bevel=0.01)
    timber_all(x0 - j, y0 - j, x1 + j, y1 + j, z1 + 0.06, z2, m, posts_x=6, posts_y=5)
    windows_all(x0 - j, y0 - j, x1 + j, y1 + j, 1.02, m, nx=5, ny=4, h=0.28, shutter=m.shutter_blue)
    windows_all(x0, y0, x1, y1, 0.3, m, nx=0, ny=3, h=0.3, skip={("x-1", 1)})
    gable_roof(x0 - j, y0 - j, x1 + j, y1 + j, z2, 0.95, True, m.roof_red, over=0.12, rows=10, gable_mat=m.plaster_white)
    door(("x", x0, -1), 1.5, 0.32, 0.55, m.door, m.stone_dark, z=0.12)
    box(x0 - 0.2, 1.25, 0, x0, 1.75, 0.06, m.stone_dark, bevel=0.01)
    box(x0 - 0.12, 1.28, 0.06, x0, 1.72, 0.11, m.stone_dark, bevel=0.01)
    banner = mat("banner", 0xb83030, 0.8, noise=0.1)
    gold = mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0)
    for y in (1.1, 1.9):
        box(x0 - j - 0.02, y - 0.1, 0.9, x0 - j - 0.005, y + 0.1, 1.4, banner, bevel=0)
        sphere(x0 - j - 0.03, y, 1.2, 0.04, gold, sx=0.3)
    cx, cy = 1.55, 1.55
    zt = z2 + 0.95
    box(cx - 0.22, cy - 0.22, zt - 0.4, cx + 0.22, cy + 0.22, zt + 0.5, m.plaster_white, bevel=0.01)
    clockm = mat("clock", 0xf4f0e4, 0.5, noise=0)
    for (axis, c, out) in (("x", cx - 0.22, -1), ("y", cy - 0.22, -1), ("x", cx + 0.22, 1), ("y", cy + 0.22, 1)):
        o = cylinder(0, 0, 0, 0.01, 0.12, clockm, verts=20, bevel=0)
        if axis == "x":
            o.rotation_euler = (0, math.radians(90), 0); o.location = (c + out * 0.012, cy, zt + 0.2)
        else:
            o.rotation_euler = (math.radians(90), 0, 0); o.location = (cx, c + out * 0.012, zt + 0.2)
    cone(cx, cy, zt + 0.5, zt + 1.05, 0.34, m.slate, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    bar((cx, cy, zt + 1.0), (cx, cy, zt + 1.4), 0.012, iron())
    # флаг развевается (кадры анимации - lib.animated)
    animated(lambda f: cloth((cx, cy - 0.012, zt + 1.39), (0, -1, 0), 0.32, 0.17, f, mat("flag", 0xc83a30, 0.8, noise=0.1),
                             taper=0.85, amp=0.03, name="flag"))
    t1 = chimney(0.9, 2.1, 1.9, 2.6, m.chim)
    for y in (0.25, 2.75):
        bush(0.2, y, 0.1, flowers=0xd85a6a, seed=int(y * 7))
    return s, 4.2, {"smoke": t1}


def firewatch():
    """Каланча: высокая деревянная вышка с колоколом под шатром, лестница, бочки с водой и вёдра"""
    s = 2
    m = M()
    yard(s)
    c0, c1 = 0.65, 1.35
    H = 1.9
    for x in (c0, c1):
        for y in (c0, c1):
            sx_ = 1 if x < 1 else -1; sy_ = 1 if y < 1 else -1
            bar((x - sx_ * 0.06, y - sy_ * 0.06, 0), (x + sx_ * 0.02, y + sy_ * 0.02, H), 0.035, m.beam)
    for z in (0.5, 1.0, 1.5):
        k = 0.02 * z / H
        for (a, b) in (((c0 + k, c0 + k), (c1 - k, c0 + k)), ((c0 + k, c0 + k), (c0 + k, c1 - k)), ((c1 - k, c0 + k), (c1 - k, c1 - k)), ((c0 + k, c1 - k), (c1 - k, c1 - k))):
            bar((a[0], a[1], z), (b[0], b[1], z), 0.018, m.beam)
    box(c0 - 0.05, c0 - 0.05, H, c1 + 0.05, c1 + 0.05, H + 0.06, m.wood, bevel=0.01)
    for (a, b) in (((c0, c0), (c1, c0)), ((c0, c0), (c0, c1)), ((c1, c0), (c1, c1)), ((c0, c1), (c1, c1))):
        bar((a[0], a[1], H + 0.25), (b[0], b[1], H + 0.25), 0.012, m.beam)
    for (x, y) in ((c0, c0), (c1, c0), (c0, c1), (c1, c1)):
        box(x - 0.025, y - 0.025, H, x + 0.025, y + 0.025, H + 0.6, m.beam, bevel=0)
    cone(1.0, 1.0, H + 0.6, H + 1.1, 0.6, m.roof_red, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    bell = mat("bell", 0xc89a3a, 0.3, metal=0.9, noise=0)
    cone(1.0, 1.0, H + 0.25, H + 0.5, 0.12, bell, verts=16)
    bar((0.42, 0.6, 0), (0.6, 0.6, H), 0.012, m.wood)
    bar((0.42, 0.78, 0), (0.6, 0.78, H), 0.012, m.wood)
    for i in range(10):
        t = (i + 0.5) / 10
        bar((0.42 + 0.18 * t, 0.6, H * t), (0.42 + 0.18 * t, 0.78, H * t), 0.008, m.wood, verts=4)
    water = mat("wellwater", 0x2a4a70, 0.05, noise=0)
    for (bx, by) in ((1.5, 0.4), (1.7, 0.55)):
        barrel(bx, by, r=0.08, h=0.18)
        cylinder(bx, by, 0.17, 0.175, 0.075, water, verts=12, bevel=0)
    for k in range(3):
        cylinder(0.3 + k * 0.12, 1.65, 0, 0.08, 0.045, mat("bucket_red", 0xb83a2a, 0.6), verts=10)
    return s, 3.2, {}


def healer():
    """Лекарь: белёный домик, грядки с травами, пучки трав сушатся, вывеска-ступка"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.75, 0.75, 1.75, 1.75
    zp, zw = 0.1, 0.88
    body(x0, y0, x1, y1, zw, m.plaster_white, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m, posts_x=3, posts_y=3)
    gable_roof(x0, y0, x1, y1, zw, 0.6, False, mat("roof_green", 0x4a7a5a, 0.7, noise=0.25, kind="brick", scale=18), over=0.1, rows=7, gable_mat=m.plaster_white)
    windows_all(x0, y0, x1, y1, 0.45, m, nx=2, ny=2, shutter=m.shutter, skip={("x-1", 1)})
    door(("x", x0, -1), 1.45, 0.2, 0.42, m.door, m.beam, z=zp)
    sign(("x", x0, -1), 1.15, 0.75, icon_mortar)
    bar((x0, 0.95, 0.75), (x0 - 0.3, 0.95, 0.62), 0.012, m.beam)
    herbs = mat("herbs", 0x5a8a3a, 0.9, noise=0.3, bump=0.5)
    for k in range(3):
        sphere(x0 - 0.08 - k * 0.08, 0.95, 0.58 - k * 0.02, 0.03, herbs, sz=1.8)
    soil = mat("soil", 0x6a4a30, 0.95, noise=0.3, bump=0.5)
    for i, c in enumerate((0x6aa040, 0x9a6ac8, 0xe8c040, 0x4a8a5a)):
        bx = 0.15 + (i % 2) * 0.32; by = 0.15 + (i // 2) * 0.3
        box(bx, by, 0, bx + 0.26, by + 0.22, 0.05, mat("bed", 0x7a5232, 0.8), bevel=0.01)
        box(bx + 0.02, by + 0.02, 0.05, bx + 0.24, by + 0.2, 0.06, soil, bevel=0, lines=False)
        pm = mat("plant%x" % c, c, 0.8, noise=0.2)
        for k in range(4):
            sphere(bx + 0.06 + (k % 2) * 0.13, by + 0.06 + (k // 2) * 0.1, 0.08, 0.035, pm)
    return s, 1.8, {}


def tavern():
    """Таверна: два этажа, балкон над входом, кружка на вывеске, столы с лавками у входа, фонари"""
    s = 2
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.6, 0.7, 1.8, 1.8
    zp, z1, z2 = 0.1, 0.72, 1.38
    body(x0, y0, x1, y1, z1, m.stone, zp, m)
    box(x0 - 0.04, y0 - 0.04, z1, x1 + 0.04, y1 + 0.04, z2, m.plaster_warm, bevel=0.01)
    timber_all(x0 - 0.04, y0 - 0.04, x1 + 0.04, y1 + 0.04, z1, z2, m, posts_x=4, posts_y=4)
    gable_roof(x0 - 0.04, y0 - 0.04, x1 + 0.04, y1 + 0.04, z2, 0.65, True, m.roof_brown, over=0.12, rows=8, gable_mat=m.plaster_warm)
    windows_all(x0, y0, x1, y1, 0.32, m, nx=3, ny=2, h=0.22, skip={("y-1", 1)})
    windows_all(x0 - 0.04, y0 - 0.04, x1 + 0.04, y1 + 0.04, 0.92, m, nx=3, ny=3, h=0.24, shutter=m.shutter)
    door(("y", y0, -1), 1.2, 0.24, 0.45, m.door, m.beam, z=zp)
    box(0.95, y0 - 0.25, z1, 1.45, y0 - 0.04, z1 + 0.04, m.wood, bevel=0.005)
    for x in (0.97, 1.43):
        bar((x, y0 - 0.23, 0), (x, y0 - 0.23, z1), 0.02, m.beam)
    for i in range(6):
        x = 0.97 + i * 0.092
        bar((x, y0 - 0.24, z1), (x, y0 - 0.24, z1 + 0.17), 0.007, m.wood, verts=4)
    bar((0.95, y0 - 0.24, z1 + 0.17), (1.45, y0 - 0.24, z1 + 0.17), 0.012, m.wood)
    sign(("x", x0, -1), 0.9, 0.62, icon_mug, arm=0.38)
    lantern(0.9, y0 - 0.06, 0.55)
    lantern(1.5, y0 - 0.06, 0.55)
    top = chimney(1.5, 1.45, 1.8, 2.4, m.chim)
    for (tx, ty) in ((0.3, 0.35), (0.3, 1.25)):
        box(tx - 0.12, ty - 0.18, 0.16, tx + 0.12, ty + 0.18, 0.19, m.wood, bevel=0.005)
        box(tx - 0.02, ty - 0.02, 0, tx + 0.02, ty + 0.02, 0.16, m.beam, bevel=0)
        for s_ in (-1, 1):
            box(tx + s_ * 0.18 - 0.04, ty - 0.18, 0.08, tx + s_ * 0.18 + 0.04, ty + 0.18, 0.1, m.wood, bevel=0.004)
        icon_mug(tx, ty, 0.25, "x")
    barrel(1.92, 0.5); barrel(1.92, 0.7)
    return s, 2.6, {"smoke": top}


def watchtower():
    """
    Смотровая вышка (1 клетка): каменный цоколь, четыре бревенчатые ноги с раскосами, площадка с перилами
    под шатровой крышей, флаг на шпиле, лестница со стороны входа (-y)
    """
    s = 1
    m = M()
    yard(s)
    c0, c1 = 0.24, 0.76
    box(c0 - 0.06, c0 - 0.06, 0, c1 + 0.06, c1 + 0.06, 0.3, m.stone, bevel=0.02)
    H = 1.75
    for x in (c0, c1):
        for y in (c0, c1):
            bar((x, y, 0.3), (x, y, H), 0.04, m.logs)
    # раскосы крест-накрест на двух ярусах
    for z0, z1 in ((0.35, 1.0), (1.0, 1.65)):
        for (a, b) in (((c0, c0), (c1, c0)), ((c0, c1), (c1, c1))):
            bar((a[0], a[1], z0), (b[0], b[1], z1), 0.015, m.beam)
            bar((b[0], b[1], z0), (a[0], a[1], z1), 0.015, m.beam)
        for (a, b) in (((c0, c0), (c0, c1)), ((c1, c0), (c1, c1))):
            bar((a[0], a[1], z0), (b[0], b[1], z1), 0.015, m.beam)
            bar((b[0], b[1], z0), (a[0], a[1], z1), 0.015, m.beam)
    # площадка шире ног, перила
    p0, p1 = c0 - 0.14, c1 + 0.14
    box(p0, p0, H, p1, p1, H + 0.06, m.wood, bevel=0.01)
    for (a, b) in (((p0, p0), (p1, p0)), ((p0, p0), (p0, p1)), ((p1, p0), (p1, p1)), ((p0, p1), (p1, p1))):
        bar((a[0], a[1], H + 0.28), (b[0], b[1], H + 0.28), 0.012, m.beam)
    for (x, y) in ((p0, p0), (p1, p0), (p0, p1), (p1, p1)):
        box(x - 0.022, y - 0.022, H, x + 0.022, y + 0.022, H + 0.62, m.beam, bevel=0)
    for k in range(1, 4):
        t = p0 + (p1 - p0) * k / 4
        for (x0, y0, x1, y1) in ((t, p0, t, p0), (p0, t, p0, t), (t, p1, t, p1), (p1, t, p1, t)):
            bar((x0, y0, H + 0.06), (x1, y1, H + 0.28), 0.007, m.wood, verts=4)
    cone(0.5, 0.5, H + 0.62, H + 1.12, 0.48, m.roof_red, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    # шпиль с флагом
    pole = mat("pole", 0x6a4a2c, 0.7)
    bar((0.5, 0.5, H + 1.05), (0.5, 0.5, H + 1.42), 0.012, pole)
    red = mat("banner_red", 0xb83030, 0.8, noise=0.1)
    o = mesh_from([(0.5, 0.5, H + 1.41), (0.5, 0.18, H + 1.34), (0.5, 0.5, H + 1.26)], [(0, 1, 2)], red, name="tower_flag")
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.012
    # лестница к площадке со стороны входа
    bar((0.38, 0.02, 0), (0.42, c0 - 0.1, H), 0.012, m.wood)
    bar((0.58, 0.02, 0), (0.62, c0 - 0.1, H), 0.012, m.wood)
    for i in range(11):
        t = (i + 0.5) / 11
        y = 0.02 + (c0 - 0.12) * t
        bar((0.38 + 0.04 * t, y, H * t), (0.58 + 0.04 * t, y, H * t), 0.007, m.wood, verts=4)
    return s, 3.4, {}


# ---------- новые ремёсла ----------

def _pt(axis, x, y, u, z):
    """Точка знака: u - вдоль плоскости знака"""
    return (x, y + u, z) if axis == "x" else (x + u, y, z)


def icon_scissors(x, y, z, axis):
    steel = mat("steel", 0xb8bcc4, 0.3, metal=0.9, noise=0)
    for k in (-1, 1):
        bar(_pt(axis, x, y, -0.05 * k, z + 0.1), _pt(axis, x, y, 0.0, z - 0.01), 0.011, steel, verts=4)
        bar(_pt(axis, x, y, 0.0, z - 0.01), _pt(axis, x, y, 0.045 * k, z - 0.09), 0.009, iron(), verts=4)
    for k in (-1, 1):
        bpy.ops.mesh.primitive_torus_add(major_radius=0.03, minor_radius=0.008, location=_pt(axis, x, y, 0.05 * k, z - 0.11), rotation=_flat_rot(axis))
        bpy.context.object.data.materials.append(iron())


def icon_candle(x, y, z, axis):
    wax = mat("candle_wax", 0xf4e8b8, 0.6, noise=0.05)
    cylinder(x, y, z - 0.09, z + 0.05, 0.035, wax, verts=12)
    sphere(x, y, z + 0.09, 0.02, mat("flame", 0xffb030, 0.3, noise=0), sz=1.6)


def icon_horseshoe(x, y, z, axis):
    steel = iron()
    bpy.ops.mesh.primitive_torus_add(major_radius=0.06, minor_radius=0.014, location=(x, y, z), rotation=_flat_rot(axis))
    bpy.context.object.data.materials.append(steel)
    for k in (-1, 1):
        sphere(*_pt(axis, x, y, 0.055 * k, z + 0.04), 0.017, steel)
    bar(_pt(axis, x, y, -0.07, z - 0.02), _pt(axis, x, y, 0.07, z - 0.02), 0.01, steel, verts=4)


def _anvil(x, y, z=0.0, s=1.0):
    m = iron()
    box(x - 0.05 * s, y - 0.035 * s, z, x + 0.05 * s, y + 0.035 * s, z + 0.03 * s, m, bevel=0.004)
    box(x - 0.03 * s, y - 0.025 * s, z + 0.03 * s, x + 0.03 * s, y + 0.025 * s, z + 0.06 * s, m, bevel=0.004)
    box(x - 0.08 * s, y - 0.035 * s, z + 0.06 * s, x + 0.07 * s, y + 0.035 * s, z + 0.09 * s, m, bevel=0.004)
    cone(x - 0.11 * s, y, z + 0.06 * s, z + 0.09 * s, 0.03 * s, m, verts=6).rotation_euler = (0, math.radians(90), 0)


def _cottage(m, x0, y0, x1, y1, zw, wall, roof, along_x=True, rows=6, hroof=0.5, door_u=None, shutter=None, nx=2, ny=1, skipw=()):
    zp = 0.1
    body(x0, y0, x1, y1, zw, wall, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m, posts_x=3, posts_y=3)
    gable_roof(x0, y0, x1, y1, zw, hroof, along_x, roof, over=0.09, rows=rows, gable_mat=wall)
    windows_all(x0, y0, x1, y1, zw * 0.55, m, nx=nx, ny=ny, shutter=shutter or m.shutter, skip=skipw)
    if door_u is not None:
        door(("y", y0, -1), door_u, 0.2, 0.42, m.door, m.beam, z=zp)


def _sack(x, y, color, r=0.055, name="sack"):
    sm = mat(name + "%x" % color, color, 0.95, noise=0.2, bump=0.4)
    sphere(x, y, r * 0.9, r, sm, sz=1.1)
    sphere(x, y, r * 1.9, r * 0.45, sm, sz=0.8)


def mine(coal=False):
    """Шахта: штольня в каменном склоне, деревянная крепь, вагонетка с рудой на рельсах, кирка (coal - угольная копь)"""
    s = 2
    m = M()
    yard(s)
    rk = mat("rock_dark", 0x77716a, 0.9, noise=0.3, bump=0.8)
    rk2 = mat("rock_mid", 0x8f8778, 0.9, noise=0.3, bump=0.8)
    rr = rng(11)
    box(0.35, 1.05, 0, 1.65, 1.8, 0.85, rk, bevel=0.06)
    for i in range(16):
        x = rr.uniform(0.25, 1.75); y = rr.uniform(1.1, 1.85)
        h = 0.55 + 0.5 * (1 - abs(x - 1.0)) * (y - 0.85)
        sphere(x, y, 0.1 + h * rr.uniform(0.25, 0.5), rr.uniform(0.2, 0.3), rk if i % 2 else rk2, sz=rr.uniform(0.9, 1.3))
    for i in range(7):
        sphere(rr.uniform(0.5, 1.5), rr.uniform(1.2, 1.7), 0.8 + rr.uniform(0, 0.12), rr.uniform(0.15, 0.22), rk2, sz=0.8)
    # штольня в стене y = 1.05
    dark = mat("tunnel", 0x0e0c0a, 1.0, noise=0)
    box(0.72, 1.0, 0.08, 1.28, 1.1, 0.5, dark, bevel=0)
    for x in (0.72, 1.28):
        box(x - 0.035, 0.96, 0, x + 0.035, 1.04, 0.55, m.beam, bevel=0.006)
    box(0.66, 0.95, 0.5, 1.34, 1.05, 0.58, m.beam, bevel=0.006)
    bar((0.72, 0.95, 0.4), (0.8, 0.95, 0.5), 0.015, m.beam)
    bar((1.28, 0.95, 0.4), (1.2, 0.95, 0.5), 0.015, m.beam)
    for x in (0.9, 1.1):
        bar((x, 0.95, 0.02), (x, 0.3, 0.02), 0.012, iron(), verts=4)
    for k in range(7):
        y = 0.35 + k * 0.1
        box(0.84, y - 0.015, 0.0, 1.16, y + 0.015, 0.02, m.wood, bevel=0)
    # вагонетка с рудой
    cart_m = mat("minecart", 0x5a4a3c, 0.7, noise=0.2)
    box(0.8, 0.45, 0.07, 1.2, 0.7, 0.2, cart_m, bevel=0.01)
    for (cx, cy) in ((0.85, 0.5), (1.15, 0.5), (0.85, 0.65), (1.15, 0.65)):
        cylinder(cx, cy, 0.0, 0.07, 0.035, iron(), verts=8, bevel=0).rotation_euler = (0, math.radians(90), 0)
    ore = mat("coal_lump", 0x26262a, 0.55, noise=0.3, bump=0.9) if coal else mat("ore", 0x9a6a4a, 0.8, noise=0.35, bump=0.8)
    for i in range(7):
        sphere(0.86 + (i % 4) * 0.09, 0.52 + (i // 4) * 0.1, 0.22 + 0.02 * (i % 2), 0.06, ore, sz=0.8)
    spark = mat("coal_spark", 0x8a8a96, 0.25, metal=0.6, noise=0) if coal else mat("ore_spark", 0xe8c880, 0.3, metal=0.8, noise=0)
    for (ox, oy) in ((0.93, 0.55), (1.08, 0.6), (1.02, 0.5)):
        sphere(ox, oy, 0.26, 0.012, spark)
    if coal:
        # мешки угля у входа
        sack = mat("coal_sack", 0x6a5a44, 0.9, noise=0.3, bump=0.5)
        for (sx, sy) in ((0.3, 0.45), (0.42, 0.62), (0.28, 0.75)):
            sphere(sx, sy, 0.0, 0.11, sack, sz=1.1)
            sphere(sx, sy, 0.2, 0.06, ore, sz=0.6)
    # кирка и кучка руды
    bar((1.6, 0.45, 0.02), (1.6, 0.45, 0.28), 0.012, mat("handle", 0x9a6a3a, 0.6))
    bar((1.5, 0.45, 0.27), (1.7, 0.45, 0.27), 0.013, iron(), verts=6)
    for i in range(5):
        sphere(1.45 + i * 0.07, 0.8 + (i % 2) * 0.06, 0.05, 0.05, ore, sz=0.8)
    lantern(0.62, 0.9, 0.45)
    return s, 1.5, {}


def coalmine():
    return mine(coal=True)


def charcoal():
    """Углежог: кладка из брёвен под дёрном, дым, навес, поленница и чёрные мешки угля"""
    s = 2
    m = M()
    yard(s)
    turf = mat("turf", 0x5a6a3a, 0.95, noise=0.35, bump=0.7)
    soil = mat("charsoil", 0x3a2e24, 0.95, noise=0.3, bump=0.6)
    sphere(1.3, 1.25, 0.0, 0.64, soil, sz=0.75)
    sphere(1.3, 1.25, 0.0, 0.6, turf, sz=0.7)
    sphere(1.3, 1.25, 0.28, 0.3, soil, sz=0.5)
    logm = mat("charlog", 0x6a4a30, 0.85, noise=0.2)
    endm = mat("charlogend", 0xc89a60, 0.7, noise=0.1)
    for k in range(14):
        a = k * math.pi * 2 / 14
        bar((1.3 + math.cos(a) * 0.38, 1.25 + math.sin(a) * 0.38, 0.04), (1.3 + math.cos(a) * 0.62, 1.25 + math.sin(a) * 0.62, 0.02), 0.035, logm, verts=8)
        sphere(1.3 + math.cos(a) * 0.62, 1.25 + math.sin(a) * 0.62, 0.02, 0.036, endm, sz=0.7)
    top = (1.3, 1.25, 0.66)
    cylinder(1.3, 1.25, 0.5, 0.64, 0.07, soil, verts=10, bevel=0)
    cylinder(1.3, 1.25, 0.62, 0.66, 0.05, mat("ember", 0xa03a1a, 0.7, noise=0), verts=10, bevel=0)
    for (x, y) in ((0.2, 0.2), (0.7, 0.2), (0.2, 0.7), (0.7, 0.7)):
        box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.45, m.beam, bevel=0)
    gable_roof(0.15, 0.15, 0.75, 0.75, 0.45, 0.18, False, m.shingle, over=0.05, rows=4)
    for i in range(6):
        _sack(0.28 + (i % 3) * 0.13, 0.3 + (i // 3) * 0.14, 0x1c1a1a, r=0.055, name="coalsack")
    log_pile(1.55, 0.35, True, n=4, r=0.04, length=0.5)
    log_pile(1.6, 0.6, True, n=3, r=0.04, length=0.5)
    barrel(0.5, 1.4)
    return s, 1.3, {"smoke": top}


def smelter():
    """Плавильня: каменная домна с высокой трубой, мехи, слитки у входа"""
    s = 2
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.7, 0.7, 1.5, 1.5
    zp = 0.1
    box(x0 - 0.04, y0 - 0.04, 0, x1 + 0.04, y1 + 0.04, zp, m.plinth, bevel=0.015)
    box(x0, y0, zp, x1, y1, 0.62, m.stone, bevel=0.015)
    box(x0 + 0.1, y0 + 0.1, 0.62, x1 - 0.1, y1 - 0.1, 1.0, m.stone, bevel=0.015)
    box(x0 + 0.02, y0 + 0.02, 0.6, x1 - 0.02, y1 - 0.02, 0.65, iron(), bevel=0.004)
    box(x0 + 0.08, y0 + 0.08, 0.98, x1 - 0.08, y1 - 0.08, 1.03, iron(), bevel=0.004)
    chim = chimney(1.1, 1.1, 1.0, 1.95, m.stone)
    glow = mat("furnace_glow", 0xff7a1a, 0.3, noise=0.1)
    box(0.98, y0 - 0.02, 0.14, 1.22, y0 + 0.05, 0.4, mat("furnace_dark", 0x1a1412, 1.0, noise=0), bevel=0.005)
    box(1.02, y0 - 0.03, 0.17, 1.18, y0 + 0.05, 0.3, glow, bevel=0)
    bm = mat("bellows", 0x7a4a2a, 0.8, noise=0.2)
    box(x0 - 0.28, 0.88, 0.14, x0 - 0.04, 1.12, 0.3, bm, bevel=0.02)
    bar((x0 - 0.28, 1.0, 0.2), (x0 - 0.5, 1.0, 0.1), 0.02, m.wood)
    bar((x0 - 0.5, 1.0, 0.1), (x0 - 0.5, 1.0, 0.42), 0.012, m.beam)
    ingot = mat("ingot", 0xa8acb4, 0.3, metal=0.9, noise=0.1)
    for r_ in range(2):
        for i in range(3 - r_):
            box(0.4 + i * 0.12 + r_ * 0.06, 0.18, r_ * 0.05, 0.5 + i * 0.12 + r_ * 0.06, 0.3, r_ * 0.05 + 0.05, ingot, bevel=0.008)
    for (ix, iy) in ((1.2, 0.2), (1.34, 0.28)):
        box(ix, iy, 0, ix + 0.12, iy + 0.07, 0.04, ingot, bevel=0.006)
    box(1.55, 0.2, 0, 1.85, 0.5, 0.18, m.wood, bevel=0.01)
    coal = mat("coal", 0x1c1a1a, 0.9, noise=0.3, bump=0.8)
    for i in range(5):
        sphere(1.63 + (i % 3) * 0.09, 0.3 + (i // 3) * 0.1, 0.2, 0.05, coal, sz=0.8)
    return s, 2.3, {"smoke": chim}


def smithy():
    """Кузница: дом с открытым навесом, горн с огнём, наковальня, вывеска-подкова"""
    s = 2
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.9, 0.8, 1.8, 1.7
    zp, zw = 0.1, 0.62
    body(x0, y0, x1, y1, zw, m.stone_dark, zp, m)
    gable_roof(x0, y0, x1, y1, zw, 0.5, False, mat("roof_dark", 0x4a3e3a, 0.7, noise=0.25, kind="brick", scale=18), over=0.1, rows=6, gable_mat=m.stone_dark)
    windows_all(x0, y0, x1, y1, 0.38, m, nx=2, ny=2, shutter=m.shutter, skip={("x-1", 0), ("x-1", 1)})
    door(("y", y0, -1), 1.5, 0.2, 0.42, m.door, m.beam, z=zp)
    top = chimney(1.5, 1.45, 0.9, 1.65, m.chim)
    for (x, y) in ((0.1, 0.2), (0.75, 0.2), (0.1, 0.9), (0.75, 0.9)):
        box(x - 0.025, y - 0.025, 0, x + 0.025, y + 0.025, 0.5, m.beam, bevel=0)
    gable_roof(0.05, 0.15, 0.82, 0.95, 0.5, 0.2, True, m.shingle, over=0.05, rows=4)
    box(0.12, 0.7, 0, 0.5, 0.9, 0.28, m.stone_dark, bevel=0.01)
    box(0.16, 0.72, 0.28, 0.46, 0.88, 0.32, mat("forge_dark", 0x201a18, 0.9, noise=0), bevel=0)
    glow = mat("forge_glow", 0xff8a20, 0.3, noise=0.1)
    for i in range(4):
        sphere(0.2 + i * 0.07, 0.8, 0.33, 0.04, glow, sz=0.6)
    box(0.13, 0.86, 0.32, 0.49, 0.9, 0.55, m.stone_dark, bevel=0.01)
    cylinder(0.45, 0.4, 0, 0.14, 0.07, m.logs, verts=10)
    _anvil(0.45, 0.4, 0.14, 1.0)
    barrel(0.2, 0.35, r=0.07, h=0.17)
    cylinder(0.2, 0.35, 0.16, 0.17, 0.065, mat("wellwater", 0x2a4a70, 0.05, noise=0), verts=12, bevel=0)
    for i in range(3):
        bar((0.62, 0.3 + i * 0.05, 0.03), (0.82, 0.3 + i * 0.05, 0.03), 0.01, iron(), verts=4)
    sign(("y", y0, -1), 1.05, 0.7, icon_horseshoe, arm=0.3)
    return s, 1.6, {"smoke": top}


def tannery():
    """Дубильня: сарай, чаны с бурой жидкостью, шкуры на рамах"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.9, 0.9, 1.85, 1.75
    zp, zw = 0.08, 0.6
    body(x0, y0, x1, y1, zw, m.logs, zp, m)
    gable_roof(x0, y0, x1, y1, zw, 0.45, False, m.thatch, over=0.1, rows=6)
    windows_all(x0, y0, x1, y1, 0.36, m, nx=2, ny=1, shutter=m.shutter)
    door(("y", y0, -1), 1.35, 0.2, 0.4, m.door, m.beam, z=zp)
    liquid = mat("tan_liquid", 0x6a4a28, 0.1, noise=0.15)
    hoop = mat("hoop", 0x3a3430, 0.5, metal=0.6, noise=0)
    for (vx, vy) in ((0.3, 0.3), (0.6, 0.3), (0.3, 0.62), (0.6, 0.62)):
        cylinder(vx, vy, 0, 0.18, 0.12, m.wood, verts=14)
        cylinder(vx, vy, 0.15, 0.16, 0.105, liquid, verts=14, bevel=0)
        for zz in (0.04, 0.14):
            cylinder(vx, vy, zz - 0.007, zz + 0.007, 0.123, hoop, verts=14, bevel=0)
    hide = mat("hide", 0xb08a5c, 0.9, noise=0.3, bump=0.4)
    for (fx, fy, ax_) in ((1.15, 0.35, "x"), (1.55, 0.35, "x"), (1.9, 0.5, "y")):
        if ax_ == "x":
            a, b = (fx - 0.15, fy, 0), (fx + 0.15, fy, 0)
            quad = [(fx - 0.13, fy, 0.36), (fx + 0.13, fy, 0.36), (fx + 0.1, fy, 0.08), (fx - 0.1, fy, 0.12)]
        else:
            a, b = (fx, fy - 0.15, 0), (fx, fy + 0.15, 0)
            quad = [(fx, fy - 0.13, 0.36), (fx, fy + 0.13, 0.36), (fx, fy + 0.1, 0.08), (fx, fy - 0.1, 0.12)]
        bar(a, (a[0], a[1], 0.38), 0.012, m.beam); bar(b, (b[0], b[1], 0.38), 0.012, m.beam)
        bar((a[0], a[1], 0.38), (b[0], b[1], 0.38), 0.012, m.beam)
        mesh_from(quad, [(0, 1, 2, 3)], hide, name="hide").modifiers.new("t", 'SOLIDIFY').thickness = 0.01
    barrel(0.2, 1.1); barrel(0.2, 1.3)
    for i in range(4):
        box(0.4, 1.1, i * 0.025, 0.72, 1.35, i * 0.025 + 0.025, hide, bevel=0.004)
    return s, 1.3, {}


def tailor():
    """Портной: аккуратный домик с синей крышей, вывеска-ножницы, рулоны ткани у входа, манекен"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.7, 0.8, 1.7, 1.75
    _cottage(m, x0, y0, x1, y1, 0.85, m.plaster_white, mat("roof_blue", 0x3a62a0, 0.7, noise=0.25, kind="brick", scale=18),
             along_x=False, rows=7, hroof=0.6, door_u=1.3, shutter=m.shutter_blue, nx=2, ny=2, skipw={("y-1", 1)})
    sign(("x", x0, -1), 1.05, 0.72, icon_scissors, arm=0.34)
    top = chimney(1.5, 1.55, 0.9, 1.5, m.chim)
    for i, c in enumerate((0xc84a5a, 0x3a62a0, 0xe8c040, 0x5a9a5a)):
        cylinder(0.2 + i * 0.1, 0.35, 0.0, 0.2, 0.04, mat("bolt%x" % c, c, 0.9, noise=0.1, bump=0.3), verts=10, bevel=0)
    for i, c in enumerate((0xe0e0d8, 0x8a5a8a)):
        cylinder(0.3 + i * 0.1, 0.35, 0.2, 0.4, 0.04, mat("bolt%x" % c, c, 0.9, noise=0.1, bump=0.3), verts=10, bevel=0)
    cloth_m = mat("mannequin", 0xd8c8a8, 0.9, noise=0.1)
    bar((1.5, 0.35, 0.0), (1.5, 0.35, 0.3), 0.01, m.wood)
    cone(1.5, 0.35, 0.28, 0.5, 0.09, mat("dress", 0x3a62a0, 0.9, noise=0.1), verts=14).rotation_euler = (math.radians(180), 0, 0)
    sphere(1.5, 0.35, 0.54, 0.04, cloth_m)
    return s, 1.6, {"smoke": top}


def piggery():
    """Свинарник: низкий сарай, загон из жердей, свинки, корыто"""
    s = 2
    m = M()
    yard(s)
    mud = mat("mud", 0x6a5036, 0.95, noise=0.35, bump=0.5, ground=True)
    box(0.15, 0.15, 0.0, 1.45, 1.1, 0.02, mud, bevel=0.02, lines=False)
    x0, y0, x1, y1 = 0.8, 1.15, 1.85, 1.85
    zp, zw = 0.08, 0.45
    body(x0, y0, x1, y1, zw, m.plaster_warm, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m, posts_x=4, posts_y=3)
    gable_roof(x0, y0, x1, y1, zw, 0.35, True, m.roof_brown, over=0.1, rows=5, gable_mat=m.plaster_warm)
    windows_all(x0, y0, x1, y1, 0.28, m, nx=2, ny=1, w=0.14, h=0.16, shutter=m.shutter)
    door(("y", y0, -1), 1.35, 0.22, 0.32, m.door, m.beam, z=zp)
    fence([(0.1, 0.1), (1.5, 0.1), (1.5, 1.1), (0.1, 1.1), (0.1, 0.1)], h=0.16)
    pig = mat("pig", 0xf0a8a0, 0.7, noise=0.1)
    pig2 = mat("pig2", 0xe0948c, 0.7, noise=0.1)
    snout = mat("snout", 0xd07880, 0.7, noise=0)

    def pigmodel(px, py, rz, pm):
        c, s_ = math.cos(rz), math.sin(rz)
        P = lambda a, b: (px + a * c - b * s_, py + a * s_ + b * c)
        bx, by = P(0, 0)
        along = abs(c) > 0.7
        sphere(bx, by, 0.09, 0.085, pm, sx=1.35 if along else 0.95, sy=0.95 if along else 1.35)
        hx, hy = P(0.11, 0)
        sphere(hx, hy, 0.1, 0.055, pm)
        nx_, ny_ = P(0.16, 0)
        sphere(nx_, ny_, 0.095, 0.025, snout)
        for (a, b) in ((0.07, 0.04), (0.07, -0.04), (-0.07, 0.04), (-0.07, -0.04)):
            lx, ly = P(a, b)
            cylinder(lx, ly, 0, 0.05, 0.014, snout, verts=6, bevel=0)
        tx, ty = P(-0.12, 0)
        sphere(tx, ty, 0.11, 0.015, snout)
    pigmodel(0.45, 0.4, 0.3, pig)
    pigmodel(0.9, 0.75, 3.4, pig2)
    pigmodel(0.5, 0.85, 1.8, pig)
    pigmodel(1.15, 0.35, -0.5, pig2)
    box(1.2, 0.8, 0.0, 1.42, 0.9, 0.07, m.wood, bevel=0.005)
    box(1.22, 0.82, 0.05, 1.4, 0.88, 0.075, mat("slop", 0xb0904a, 0.9, noise=0.3), bevel=0, lines=False)
    hay(1.7, 0.5, r=0.14)
    barrel(1.7, 0.9)
    return s, 1.0, {}


def smokehouse():
    """Коптильня: домик с дымящей трубой, окорока под навесом, поленница"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.9, 0.85, 1.75, 1.75
    zp, zw = 0.1, 0.8
    body(x0, y0, x1, y1, zw, m.logs, zp, m)
    log_walls(x0, y0, x1, y1, zw, m)
    gable_roof(x0, y0, x1, y1, zw, 0.5, False, m.shingle, over=0.1, rows=6)
    windows_all(x0, y0, x1, y1, 0.45, m, nx=2, ny=1, w=0.13, h=0.18, shutter=m.shutter, skip={("y-1", 0), ("y-1", 1)})
    door(("y", y0, -1), 1.32, 0.2, 0.42, m.door, m.beam, z=zp)
    top = chimney(1.4, 1.5, 1.0, 1.9, m.chim)
    for (x, y) in ((0.1, 0.2), (0.75, 0.2), (0.1, 0.8), (0.75, 0.8)):
        box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.5, m.beam, bevel=0)
    bar((0.1, 0.5, 0.46), (0.75, 0.5, 0.46), 0.014, m.beam)
    ham = mat("ham_smoked", 0x8a3a24, 0.6, noise=0.2)
    for i in range(5):
        hx = 0.17 + i * 0.13
        bar((hx, 0.5, 0.46), (hx, 0.5, 0.38), 0.004, m.beam, verts=4)
        sphere(hx, 0.5, 0.3, 0.045, ham, sz=1.5)
    gable_roof(0.05, 0.15, 0.8, 0.85, 0.5, 0.2, True, m.shingle, over=0.04, rows=4)
    log_pile(1.6, 0.35, True, n=4, r=0.04, length=0.5)
    log_pile(1.65, 0.6, True, n=3, r=0.04, length=0.45)
    barrel(0.3, 1.3); barrel(0.5, 1.5)
    return s, 1.7, {"smoke": top}


def apiary():
    """Пасека: ульи-домики на лужайке с цветами, сарайчик, бочонки с мёдом"""
    s = 2
    m = M()
    yard(s, 0x7a9a50)
    x0, y0, x1, y1 = 1.2, 1.3, 1.85, 1.85
    zp, zw = 0.08, 0.4
    body(x0, y0, x1, y1, zw, m.plaster_warm, zp, m)
    gable_roof(x0, y0, x1, y1, zw, 0.3, True, m.thatch, over=0.08, rows=5, gable_mat=m.plaster_warm)
    windows_all(x0, y0, x1, y1, 0.25, m, nx=1, ny=1, w=0.12, h=0.14, shutter=m.shutter)
    door(("y", y0, -1), 1.4, 0.18, 0.3, m.door, m.beam, z=zp)
    hive_w = mat("hive", 0xe8c860, 0.75, noise=0.15, kind="planks", scale=30)
    hive_r = mat("hive_roof", 0xd05a3a, 0.75, noise=0.2)
    slit = mat("slit", 0x201810, 1.0, noise=0)
    for (hx, hy) in ((0.3, 0.3), (0.75, 0.25), (1.2, 0.35), (0.35, 0.8), (0.8, 0.75), (1.25, 0.85)):
        for (a, b) in ((-0.07, -0.07), (0.07, -0.07), (-0.07, 0.07), (0.07, 0.07)):
            box(hx + a - 0.01, hy + b - 0.01, 0, hx + a + 0.01, hy + b + 0.01, 0.05, m.wood, bevel=0)
        box(hx - 0.1, hy - 0.1, 0.05, hx + 0.1, hy + 0.1, 0.2, hive_w, bevel=0.008)
        gable_roof(hx - 0.1, hy - 0.1, hx + 0.1, hy + 0.1, 0.2, 0.07, True, hive_r, over=0.03, rows=2, thick=0.02)
        box(hx - 0.04, hy - 0.11, 0.07, hx + 0.04, hy - 0.09, 0.09, slit, bevel=0)
    rr = rng(7)
    for i in range(10):
        bush(rr.uniform(0.15, 1.1), rr.uniform(0.1, 1.3), r=0.07, flowers=(0xf0d040, 0xe05a8a, 0x9a70e0, 0xf4f0f0)[i % 4], seed=i + 3)
    honey = mat("honeybarrel", 0xd8a030, 0.5, noise=0.15)
    for (bx, by) in ((1.55, 1.1), (1.75, 1.12)):
        barrel(bx, by, r=0.07, h=0.15)
        cylinder(bx, by, 0.14, 0.153, 0.065, honey, verts=12, bevel=0)
    return s, 1.0, {}


def chandler():
    """Свечник: домик, вывеска-свеча, полки со свечами, котёл с воском"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.7, 0.8, 1.7, 1.75
    _cottage(m, x0, y0, x1, y1, 0.82, m.plaster_warm, m.roof_brown, along_x=True, rows=6, hroof=0.55, door_u=1.25, nx=2, ny=2, skipw={("y-1", 1)})
    sign(("x", x0, -1), 1.05, 0.7, icon_candle, arm=0.34)
    top = chimney(1.45, 1.55, 0.95, 1.55, m.chim)
    for zz in (0.12, 0.24, 0.36):
        box(0.15, 0.2, zz - 0.012, 0.65, 0.34, zz, m.wood, bevel=0.003)
    for x in (0.16, 0.64):
        box(x - 0.01, 0.2, 0, x + 0.01, 0.34, 0.4, m.beam, bevel=0)
    wax = mat("candle_wax", 0xf4e8b8, 0.6, noise=0.05)
    flame = mat("flame", 0xffb030, 0.3, noise=0)
    for zi, zz in enumerate((0.12, 0.24, 0.36)):
        for i in range(7):
            cylinder(0.2 + i * 0.065, 0.27, zz, zz + 0.07, 0.012, wax, verts=6, bevel=0)
            if zi == 2 and i % 3 == 0:
                sphere(0.2 + i * 0.065, 0.27, zz + 0.085, 0.009, flame, sz=1.5)
    iron_m = iron()
    sphere(1.35, 0.3, 0.14, 0.12, iron_m, sz=0.85)
    cylinder(1.35, 0.3, 0.19, 0.2, 0.1, mat("wax_hot", 0xf0d880, 0.4, noise=0.1), verts=14, bevel=0)
    for a in (0, 2.1, 4.2):
        bar((1.35 + math.cos(a) * 0.12, 0.3 + math.sin(a) * 0.12, 0.0), (1.35, 0.3, 0.16), 0.01, iron_m, verts=4)
    log_pile(1.65, 0.45, True, n=2, r=0.035, length=0.3)
    crate(0.4, 0.55, 0.13)
    barrel(0.25, 1.4)
    return s, 1.6, {"smoke": top}


# ---------- охрана и чудеса ----------

def guardtower():
    """Караульная вышка: низкая крепкая каменная башня с зубцами, флаг, бойницы, дверь на -y"""
    s = 1
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.18, 0.18, 0.82, 0.82
    H = 1.15
    box(x0 - 0.04, y0 - 0.04, 0, x1 + 0.04, y1 + 0.04, 0.12, m.plinth, bevel=0.015)
    box(x0, y0, 0.12, x1, y1, H, m.stone, bevel=0.015)
    box(x0 - 0.06, y0 - 0.06, H, x1 + 0.06, y1 + 0.06, H + 0.07, m.stone_dark, bevel=0.012)
    xa, xb = x0 - 0.06, x1 + 0.06
    for k in range(4):
        t = xa + (xb - xa) * (k + 0.5) / 4
        for (px, py) in ((t, xa + 0.05), (t, xb - 0.05), (xa + 0.05, t), (xb - 0.05, t)):
            box(px - 0.05, py - 0.05, H + 0.07, px + 0.05, py + 0.05, H + 0.22, m.stone, bevel=0.01)
    for (px, py) in ((xa + 0.05, xa + 0.05), (xb - 0.05, xa + 0.05), (xa + 0.05, xb - 0.05), (xb - 0.05, xb - 0.05)):
        box(px - 0.06, py - 0.06, H + 0.07, px + 0.06, py + 0.06, H + 0.24, m.stone, bevel=0.01)
    dark = mat("slitdark", 0x14100e, 1.0, noise=0)
    for z in (0.55, 0.9):
        for cx in (0.5,):
            box(cx - 0.02, y0 - 0.012, z, cx + 0.02, y0 + 0.012, z + 0.14, dark, bevel=0)
            box(cx - 0.02, y1 - 0.012, z, cx + 0.02, y1 + 0.012, z + 0.14, dark, bevel=0)
            box(x0 - 0.012, cx - 0.02, z, x0 + 0.012, cx + 0.02, z + 0.14, dark, bevel=0)
            box(x1 - 0.012, cx - 0.02, z, x1 + 0.012, cx + 0.02, z + 0.14, dark, bevel=0)
    door(("y", y0, -1), 0.5, 0.2, 0.36, m.door, m.beam, z=0.12)
    lantern(0.3, y0 - 0.04, 0.42)
    pole = mat("pole", 0x6a4a2c, 0.7)
    bar((0.5, 0.5, H + 0.07), (0.5, 0.5, H + 0.75), 0.012, pole)
    red = mat("banner_red", 0xb83030, 0.8, noise=0.1)
    o = mesh_from([(0.5, 0.5, H + 0.74), (0.5, 0.2, H + 0.67), (0.5, 0.5, H + 0.58)], [(0, 1, 2)], red, name="gt_flag")
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.012
    return s, 2.3, {}


def _round_tower(cx, cy, r, h, m, roof_m, flag=False):
    cylinder(cx, cy, 0, 0.1, r + 0.04, m.plinth, verts=20)
    cylinder(cx, cy, 0.1, h, r, m.stone, verts=20, bevel=0.01)
    cylinder(cx, cy, h, h + 0.06, r + 0.05, m.stone_dark, verts=20)
    cone(cx, cy, h + 0.04, h + 0.04 + r * 2.1, r + 0.07, roof_m, verts=20, eave=True)
    dark = mat("slitdark", 0x14100e, 1.0, noise=0)
    for ang in (-0.8, 0.8, 2.4, 3.9):
        sphere(cx + math.cos(ang) * r, cy + math.sin(ang) * r, h * 0.65, 0.025, dark, sz=3.0)
    if flag:
        pole = mat("pole", 0x6a4a2c, 0.7)
        zt = h + 0.04 + r * 2.1
        bar((cx, cy, zt - 0.02), (cx, cy, zt + 0.35), 0.012, pole)
        red = mat("banner_red", 0xb83030, 0.8, noise=0.1)
        o = mesh_from([(cx, cy, zt + 0.34), (cx, cy - 0.28, zt + 0.27), (cx, cy, zt + 0.2)], [(0, 1, 2)], red, name="flag")
        o.modifiers.new("t", 'SOLIDIFY').thickness = 0.012


def _disc_y(x, y, z, r, material, thick=0.04):
    """Круглая розетка в плоскости, перпендикулярной y"""
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=r, depth=thick, location=(x, y, z), rotation=(math.radians(90), 0, 0))
    bpy.context.object.data.materials.append(material)


def cathedral():
    """Собор: каменный неф под шиферной крышей, колокольня со шпилем у входа (-y), розетка, контрфорсы"""
    s = 4
    m = M()
    yard(s, 0x9a8c74, cobble=True)
    nx0, nx1 = 1.1, 2.9
    ny0, ny1 = 1.0, 3.8
    zp, zw = 0.12, 1.25
    body(nx0, ny0, nx1, ny1, zw, m.stone, zp, m)
    gable_roof(nx0, ny0, nx1, ny1, zw, 0.9, False, m.slate, over=0.12, rows=9, gable_mat=m.stone)
    glassm = mat("cathedral_glass", 0x5a8ad0, 0.2, noise=0.1)
    for yi in range(4):
        y = 1.6 + yi * 0.55
        for xs, side in ((nx0, -1), (nx1, 1)):
            window(("x", xs, side), y, 0.7, 0.17, 0.5, m.frame)
    for yi in range(4):
        y = 1.35 + yi * 0.55
        for xs, side in ((nx0, -1), (nx1, 1)):
            x_out = xs + side * 0.28
            box(min(xs, x_out), y - 0.05, 0, max(xs, x_out), y + 0.05, 0.85, m.stone, bevel=0.01)
            bar((xs, y, 1.15), (x_out, y, 0.8), 0.04, m.stone)
    tx0, ty0, tx1, ty1 = 1.35, 0.35, 2.65, 1.45
    cx, cy = 2.0, 0.9
    TH = 2.2
    box(tx0 - 0.04, ty0 - 0.04, 0, tx1 + 0.04, ty1 + 0.04, zp, m.plinth, bevel=0.015)
    box(tx0, ty0, zp, tx1, ty1, TH, m.stone, bevel=0.015)
    for z in (0.8, 1.5):
        box(tx0 - 0.02, ty0 - 0.02, z, tx1 + 0.02, ty1 + 0.02, z + 0.05, m.stone_dark, bevel=0.01)
    box(tx0 - 0.03, ty0 - 0.03, TH, tx1 + 0.03, ty1 + 0.03, TH + 0.06, m.stone_dark, bevel=0.01)
    dark = mat("slitdark", 0x14100e, 1.0, noise=0)
    for z, hh in ((1.0, 0.35), (1.75, 0.3)):
        for (px, py) in ((cx, ty0), (cx, ty1)):
            box(px - 0.1, py - 0.02, z, px + 0.1, py + 0.02, z + hh, dark, bevel=0)
        for (px, py) in ((tx0, cy), (tx1, cy)):
            box(px - 0.02, py - 0.1, z, px + 0.02, py + 0.1, z + hh, dark, bevel=0)
    cone(cx, cy, TH + 0.05, TH + 1.3, 0.92, m.slate, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    gold = mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0)
    bar((cx, cy, TH + 1.28), (cx, cy, TH + 1.7), 0.014, gold)
    sphere(cx, cy, TH + 1.72, 0.04, gold)
    for (px, py) in ((tx0, ty0), (tx1, ty0), (tx0, ty1), (tx1, ty1)):
        cone(px, py, TH, TH + 0.5, 0.12, m.slate, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    door(("y", ty0, -1), cx, 0.45, 0.8, m.door, m.stone_dark, z=zp)
    _disc_y(2.0, ny1, 0.98, 0.2, glassm)
    _disc_y(cx, ty0 - 0.01, 1.2, 0.17, glassm)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.17, minor_radius=0.014, location=(cx, ty0 - 0.015, 1.2), rotation=(math.radians(90), 0, 0))
    bpy.context.object.data.materials.append(m.frame)
    for k in range(4):
        a = k * math.pi / 4
        bar((cx + math.cos(a) * 0.17, ty0 - 0.02, 1.2 + math.sin(a) * 0.17), (cx - math.cos(a) * 0.17, ty0 - 0.02, 1.2 - math.sin(a) * 0.17), 0.007, m.frame, verts=4)
    for i in range(3):
        box(cx - 0.45 - i * 0.05, ty0 - 0.1 - i * 0.07, 0, cx + 0.45 + i * 0.05, ty0 - 0.03, 0.12 - i * 0.04, m.stone_dark, bevel=0.008)
    lantern(cx - 0.55, ty0 - 0.08, 0.5); lantern(cx + 0.55, ty0 - 0.08, 0.5)
    bush(0.6, 0.6, 0.12, flowers=0xf4f0f0, seed=1); bush(3.4, 0.6, 0.12, flowers=0xf4f0f0, seed=2)
    bush(0.7, 2.8, 0.12, seed=3); bush(3.3, 2.8, 0.12, seed=4)
    return s, 5.0, {}


def castle():
    """Замок: стены с зубцами, четыре круглые башни с красными коническими крышами, донжон, ворота на -y"""
    s = 4
    m = M()
    yard(s, 0x8f8470, cobble=True)
    a0, a1 = 0.45, 3.55
    WH = 0.95
    t = 0.2
    gx0, gx1 = 1.65, 2.35
    box(a0, a0, 0, gx0, a0 + t, WH, m.stone, bevel=0.012)
    box(gx1, a0, 0, a1, a0 + t, WH, m.stone, bevel=0.012)
    box(gx0, a0, 0.62, gx1, a0 + t, WH, m.stone, bevel=0.012)
    box(a0, a1 - t, 0, a1, a1, WH, m.stone, bevel=0.012)
    box(a0, a0, 0, a0 + t, a1, WH, m.stone, bevel=0.012)
    box(a1 - t, a0, 0, a1, a1, WH, m.stone, bevel=0.012)
    for (x0_, y0_, x1_, y1_) in ((a0, a0, a1, a0 + t), (a0, a1 - t, a1, a1), (a0, a0, a0 + t, a1), (a1 - t, a0, a1, a1)):
        box(x0_ - 0.02, y0_ - 0.02, WH, x1_ + 0.02, y1_ + 0.02, WH + 0.05, m.stone_dark, bevel=0.008)
    nz = 8
    for k in range(nz):
        tt = a0 + (a1 - a0) * (k + 0.5) / nz
        for (px, py) in ((tt, a0 + 0.1), (tt, a1 - 0.1), (a0 + 0.1, tt), (a1 - 0.1, tt)):
            if (px < 0.8 or px > 3.2) and (py < 0.8 or py > 3.2):
                continue
            if gx0 < px < gx1 and py < 0.6:
                continue
            box(px - 0.065, py - 0.065, WH + 0.05, px + 0.065, py + 0.065, WH + 0.2, m.stone, bevel=0.008)
    dark = mat("slitdark", 0x14100e, 1.0, noise=0)
    box(gx0 + 0.05, a0 - 0.02, 0.02, gx1 - 0.05, a0 + t - 0.05, 0.62, dark, bevel=0)
    for i in range(5):
        x = gx0 + 0.12 + i * 0.11
        bar((x, a0 - 0.04, 0.05), (x, a0 - 0.04, 0.6), 0.01, iron(), verts=5)
    for z in (0.2, 0.4, 0.58):
        bar((gx0 + 0.07, a0 - 0.04, z), (gx1 - 0.07, a0 - 0.04, z), 0.008, iron(), verts=4)
    for x in (gx0 + 0.02, gx1 - 0.02):
        box(x - 0.05, a0 - 0.06, 0, x + 0.05, a0 + 0.12, WH + 0.12, m.stone_dark, bevel=0.01)
    for (cx, cy, fl) in ((a0, a0, False), (a1, a0, True), (a0, a1, False), (a1, a1, False)):
        _round_tower(cx, cy, 0.26, 1.45, m, m.roof_red, flag=fl)
    dx0, dy0, dx1, dy1 = 1.5, 1.5, 2.5, 2.5
    box(dx0 - 0.04, dy0 - 0.04, 0, dx1 + 0.04, dy1 + 0.04, 0.1, m.plinth, bevel=0.015)
    box(dx0, dy0, 0.1, dx1, dy1, 2.15, m.stone, bevel=0.015)
    box(dx0 - 0.08, dy0 - 0.08, 2.15, dx1 + 0.08, dy1 + 0.08, 2.23, m.stone_dark, bevel=0.012)
    for k in range(4):
        tt = dx0 - 0.08 + (dx1 - dx0 + 0.16) * (k + 0.5) / 4
        for (px, py) in ((tt, dy0 - 0.08), (tt, dy1 + 0.08), (dx0 - 0.08, tt), (dx1 + 0.08, tt)):
            box(px - 0.065, py - 0.065, 2.23, px + 0.065, py + 0.065, 2.4, m.stone, bevel=0.008)
    cone(2.0, 2.0, 2.23, 3.15, 0.5, m.roof_red, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    bar((2.0, 2.0, 3.1), (2.0, 2.0, 3.55), 0.014, mat("pole", 0x6a4a2c, 0.7))
    red = mat("banner_red", 0xb83030, 0.8, noise=0.1)
    o = mesh_from([(2.0, 2.0, 3.54), (2.0, 1.66, 3.47), (2.0, 2.0, 3.38)], [(0, 1, 2)], red, name="keep_flag")
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.014
    windows_all(dx0, dy0, dx1, dy1, 1.2, m, nx=2, ny=2, w=0.14, h=0.3, shutter=m.shutter_blue, skip={("y-1", 0), ("y-1", 1)})
    windows_all(dx0, dy0, dx1, dy1, 1.75, m, nx=2, ny=2, w=0.12, h=0.22)
    door(("y", dy0, -1), 2.0, 0.22, 0.4, m.door, m.beam, z=0.1)
    barrel(1.0, 1.0); barrel(1.12, 1.05); crate(3.0, 1.0, 0.13); crate(2.9, 1.1, 0.1)
    cylinder(1.0, 3.0, 0, 0.14, 0.14, m.stone, verts=14)
    cylinder(1.0, 3.0, 0.13, 0.145, 0.1, mat("wellwater", 0x2a4a70, 0.05, noise=0), verts=14, bevel=0)
    return s, 5.0, {}


# ---------- 0.9: столярная, тележная, пристань, парк, ярмарка, гильдейский дом ----------

def _wheel(x, y, z, r, axis, spokes=8, m=None, rim=0.012):
    """Колесо со спицами и ступицей; axis - нормаль плоскости колеса: "y" (в плоскости x-z) или "x" (в плоскости y-z)"""
    m = m or mat("wheelwood", 0x9a6a3a, 0.7, noise=0.2)
    rot = (math.radians(90), 0, 0) if axis == "y" else (0, math.radians(90), 0)
    bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=rim, location=(x, y, z), rotation=rot)
    bpy.context.object.data.materials.append(m)
    for k in range(spokes):
        a = k * math.pi * 2 / spokes
        if axis == "y":
            bar((x, y, z), (x + math.cos(a) * r, y, z + math.sin(a) * r), rim * 0.45, m, verts=4)
        else:
            bar((x, y, z), (x, y + math.cos(a) * r, z + math.sin(a) * r), rim * 0.45, m, verts=4)
    o = cylinder(x, y, 0, 0.01, rim * 1.5, m, verts=8, bevel=0)
    o.rotation_euler = rot
    o.location = (x, y, z)


def _barrow(x, y, rz=0.0):
    """Тачка: короб, одно колесо спереди, две ручки и ножки"""
    wood = mat("barrow", 0xa87a48, 0.7, kind="planks", scale=40)
    c, s = math.cos(rz), math.sin(rz)

    def P(a, b, z):
        return (x + a * c - b * s, y + a * s + b * c, z)
    tray = [P(-0.07, -0.06, 0.1), P(0.1, -0.045, 0.09), P(0.1, 0.045, 0.09), P(-0.07, 0.06, 0.1)]
    top = [P(-0.09, -0.075, 0.15), P(0.11, -0.06, 0.13), P(0.11, 0.06, 0.13), P(-0.09, 0.075, 0.15)]
    mesh_from(tray, [(0, 1, 2, 3)], wood, name="barrow").modifiers.new("t", 'SOLIDIFY').thickness = 0.012
    for i in range(4):
        j = (i + 1) % 4
        mesh_from([tray[i], tray[j], top[j], top[i]], [(0, 1, 2, 3)], wood, name="barrow").modifiers.new("t", 'SOLIDIFY').thickness = 0.01
    for sd in (-1, 1):
        bar(P(-0.05, sd * 0.045, 0.1), P(-0.24, sd * 0.06, 0.13), 0.009, wood, verts=5)
        bar(P(-0.13, sd * 0.05, 0.12), P(-0.13, sd * 0.055, 0.0), 0.008, wood, verts=5)
    wx, wy, _ = P(0.15, 0, 0.055)
    _wheel(wx, wy, 0.055, 0.055, "y" if abs(s) < 0.7 else "x", spokes=6, rim=0.01)


def icon_wheel(x, y, z, axis):
    wood = mat("wheelwood", 0x9a6a3a, 0.7, noise=0.2)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.075, minor_radius=0.014, location=(x, y, z), rotation=_flat_rot(axis))
    bpy.context.object.data.materials.append(wood)
    for k in range(4):
        a = k * math.pi / 4
        bar(_pt(axis, x, y, math.cos(a) * 0.075, z + math.sin(a) * 0.075), _pt(axis, x, y, -math.cos(a) * 0.075, z - math.sin(a) * 0.075), 0.007, wood, verts=4)
    sphere(x, y, z, 0.015, iron())


def icon_barrow(x, y, z, axis):
    wood = mat("barrow", 0xa87a48, 0.7, kind="planks", scale=40)
    bar(_pt(axis, x, y, -0.07, z), _pt(axis, x, y, 0.05, z - 0.03), 0.012, wood, verts=5)
    bar(_pt(axis, x, y, 0.05, z - 0.03), _pt(axis, x, y, 0.08, z + 0.03), 0.012, wood, verts=5)
    bar(_pt(axis, x, y, -0.07, z), _pt(axis, x, y, -0.05, z + 0.05), 0.012, wood, verts=5)
    bar(_pt(axis, x, y, -0.05, z + 0.05), _pt(axis, x, y, 0.08, z + 0.03), 0.012, wood, verts=5)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.035, minor_radius=0.01, location=_pt(axis, x, y, 0.0, z - 0.06), rotation=_flat_rot(axis))
    bpy.context.object.data.materials.append(iron())


def _banner(x, y, z_top, w, h, color, trim=0xd8b040):
    """Знамя с зубчатым низом в плоскости x-z (на стене y); герб - золотой шарик"""
    bm = mat("banner%x" % color, color, 0.8, noise=0.1)
    pts = [(-w / 2, 0), (w / 2, 0), (w / 2, -h), (0, -h * 0.82), (-w / 2, -h)]
    verts = [(x + u, y, z_top + dz) for (u, dz) in pts]
    mesh_from(verts, [(0, 1, 2, 3, 4)], bm, name="banner").modifiers.new("t", 'SOLIDIFY').thickness = 0.012
    sphere(x, y - 0.012, z_top - h * 0.4, w * 0.16, mat("bannergold", trim, 0.35, metal=0.8, noise=0))


def carpenter():
    """Столярная: дом с фахверком, верстак под навесом, стопка досок, тачки во дворе, вывеска-тачка"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.95, 0.85, 1.85, 1.75
    _cottage(m, x0, y0, x1, y1, 0.6, m.plaster_warm, m.roof_brown, along_x=False, rows=6, hroof=0.48, door_u=1.45, nx=2, ny=2)
    top = chimney(1.7, 1.55, 0.8, 1.55, m.chim)
    sign(("y", y0, -1), 1.1, 0.7, icon_barrow, arm=0.3)
    # верстак под навесом
    for (x, y) in ((0.1, 0.12), (0.8, 0.12), (0.1, 0.62), (0.8, 0.62)):
        box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.46, m.beam, bevel=0)
    gable_roof(0.05, 0.07, 0.86, 0.68, 0.46, 0.2, True, m.shingle, over=0.05, rows=4)
    box(0.2, 0.28, 0.17, 0.72, 0.46, 0.2, m.wood, bevel=0.006)
    for (x, y) in ((0.24, 0.3), (0.68, 0.3), (0.24, 0.44), (0.68, 0.44)):
        box(x - 0.015, y - 0.015, 0, x + 0.015, y + 0.015, 0.17, m.beam, bevel=0)
    box(0.3, 0.3, 0.2, 0.46, 0.34, 0.215, mat("plank_new", 0xd8b078, 0.7, kind="planks", scale=30), bevel=0.004)
    box(0.5, 0.35, 0.2, 0.58, 0.41, 0.235, m.wood, bevel=0.006)                    # рубанок
    # стопка досок у стены и поленья
    plank = mat("plank_stack", 0xc89c60, 0.75, kind="planks", scale=30)
    for i in range(6):
        box(0.15, 1.05, i * 0.03, 0.62, 1.4, i * 0.03 + 0.03, plank, bevel=0.003)
    log_pile(0.3, 1.65, True, n=3, r=0.04, length=0.4)
    # готовые тачки
    _barrow(1.3, 0.42, 0.0)
    _barrow(1.6, 0.3, math.radians(35))
    barrel(1.85, 0.5)
    return s, 1.35, {"smoke": top}


def wheelwright():
    """Тележная мастерская: каменный низ, широкие ворота, колесо-вывеска, колёса у стены, недоделанная телега"""
    s = 2
    m = M()
    yard(s, cobble=True)
    x0, y0, x1, y1 = 0.85, 0.8, 1.85, 1.75
    zp, zw = 0.1, 0.62
    body(x0, y0, x1, y1, zw, m.stone, zp, m)
    box(x0, y0, zw, x1, y1, zw + 0.18, m.plaster_warm, bevel=0.01)
    timber_all(x0, y0, x1, y1, zw, zw + 0.18, m, posts_x=4, posts_y=4)
    gable_roof(x0, y0, x1, y1, zw + 0.18, 0.5, False, m.roof_red, over=0.1, rows=6, gable_mat=m.plaster_warm)
    windows_all(x0, y0, x1, y1, 0.4, m, nx=2, ny=2, shutter=m.shutter_blue, skip={("y-1", 0), ("y-1", 1)})
    door(("y", y0, -1), 1.35, 0.5, 0.46, m.door, m.beam, z=zp)                   # широкие ворота
    top = chimney(1.7, 1.55, 0.8, 1.6, m.chim)
    sign(("y", y0, -1), 0.98, 0.7, icon_wheel, arm=0.3)
    # колёса, прислонённые к стене x = x0
    for i, (yy, r) in enumerate(((1.0, 0.16), (1.28, 0.14), (1.5, 0.15))):
        _wheel(x0 - 0.04 - i * 0.015, yy, r + 0.01, r, "x", spokes=8, rim=0.018)
    # недоделанная телега: рама, рёбра бортов, ось и одно колесо, оглобля
    wood = mat("cart", 0x9a6a3a, 0.7, kind="planks", scale=40)
    cx, cy = 0.55, 0.35
    box(cx - 0.3, cy - 0.16, 0.12, cx + 0.3, cy + 0.16, 0.15, wood, bevel=0.006)
    for xx in (cx - 0.28, cx - 0.1, cx + 0.1, cx + 0.28):
        bar((xx, cy - 0.16, 0.15), (xx, cy - 0.16, 0.3), 0.01, wood, verts=5)
        bar((xx, cy + 0.16, 0.15), (xx, cy + 0.16, 0.3), 0.01, wood, verts=5)
    bar((cx - 0.3, cy - 0.16, 0.3), (cx + 0.28, cy - 0.16, 0.3), 0.008, wood, verts=5)
    bar((cx, cy - 0.22, 0.08), (cx, cy + 0.22, 0.08), 0.012, m.beam, verts=6)
    _wheel(cx, cy + 0.2, 0.09, 0.09, "y", spokes=8, rim=0.014)
    bar((cx + 0.3, cy, 0.13), (cx + 0.58, cy + 0.05, 0.07), 0.01, wood, verts=5)
    cylinder(1.5, 0.35, 0, 0.12, 0.09, m.logs, verts=12)
    crate(1.8, 0.3, 0.13)
    barrel(1.7, 0.6)
    return s, 1.45, {"smoke": top}


def harbor():
    """Пристань: склад с воротами, дощатый причал по стороне +y (вход - с -y), тумбы, бочки, журавль, лодка"""
    s = 3
    m = M()
    yard(s, 0xb0a084, cobble=True)
    plankm = mat("quay", 0x8e6a42, 0.8, kind="planks", scale=26)
    rope = mat("rope", 0xd8c8a0, 0.9, noise=0)
    # причал на сваях и швартовые тумбы
    box(0.15, 2.15, 0.1, 2.85, 2.8, 0.15, plankm, bevel=0.006)
    for i in range(7):
        x = 0.25 + i * 0.4
        for y in (2.2, 2.75):
            box(x - 0.03, y - 0.03, -0.1, x + 0.03, y + 0.03, 0.17, m.beam, bevel=0.004)
    for x in (0.5, 1.5, 2.5):
        cylinder(x, 2.72, 0.15, 0.27, 0.035, iron(), verts=8, bevel=0)
        sphere(x, 2.72, 0.27, 0.045, iron(), sz=0.6)
        bar((x, 2.72, 0.24), (x + 0.02, 2.55, 0.16), 0.006, rope, verts=4)
    # склад
    x0, y0, x1, y1 = 0.3, 0.4, 1.6, 1.3
    zp, zw = 0.1, 0.5
    body(x0, y0, x1, y1, zw, m.logs, zp, m)
    gable_roof(x0, y0, x1, y1, zw, 0.36, True, m.shingle, over=0.12, rows=6, gable_mat=m.logs)
    windows_all(x0, y0, x1, y1, 0.3, m, nx=3, ny=2, shutter=m.shutter_blue, skip={("y-1", 1)})
    door(("y", y0, -1), 1.0, 0.44, 0.44, m.door, m.beam, z=zp)
    top = chimney(1.45, 1.15, 0.6, 0.98, m.chim)
    sign(("y", y0, -1), 0.55, 0.62, icon_fish, arm=0.28)
    # навес у склада со стороны причала
    for (x, y) in ((0.35, 1.45), (1.55, 1.45), (0.35, 1.85), (1.55, 1.85)):
        box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.34, m.beam, bevel=0)
    gable_roof(0.3, 1.4, 1.6, 1.9, 0.34, 0.12, True, m.shingle, over=0.04, rows=4)
    crate(0.55, 1.65, 0.16); crate(0.55, 1.65, 0.13, z=0.16); crate(0.85, 1.7, 0.15)
    barrel(1.2, 1.65); barrel(1.35, 1.7); barrel(1.1, 1.77)
    for i in range(3):
        _sack(1.45 + (i % 2) * 0.08, 1.6 + (i // 2) * 0.09, (0x9a8a60, 0xb0a070)[i % 2])
    # ящики и бочки на причале
    crate(0.4, 2.45, 0.15, z=0.15); crate(0.55, 2.4, 0.12, z=0.15)
    barrel(2.05, 2.4); barrel(2.2, 2.35); barrel(2.12, 2.5)
    # кран-журавль
    mx, my = 2.6, 2.3
    cylinder(mx, my, 0.15, 0.95, 0.04, m.beam, verts=8, bevel=0)
    bar((mx, my, 0.45), (mx - 0.18, my - 0.1, 0.15), 0.014, m.beam, verts=5)
    bar((mx, my, 0.45), (mx + 0.18, my - 0.1, 0.15), 0.014, m.beam, verts=5)
    bar((mx, my, 0.9), (mx, my + 0.48, 0.8), 0.022, m.wood, verts=6)
    bar((mx, my, 0.9), (mx, my - 0.2, 0.96), 0.02, m.wood, verts=6)
    bar((mx, my + 0.46, 0.8), (mx, my + 0.46, 0.4), 0.005, rope, verts=4)
    crate(mx, my + 0.46, 0.11, z=0.28)
    # лодка у причала
    boat(1.3, 2.93, along_x=True, length=0.75)
    bar((1.1, 2.82, 0.12), (0.5, 2.72, 0.22), 0.004, rope, verts=4)
    bar((0.2, 2.2, 0.15), (0.2, 2.2, 0.55), 0.012, iron())
    lantern(0.2, 2.2, 0.55)
    return s, 1.5, {"smoke": top}


def park():
    """Парк: газон, крест дорожек, деревья, скамейки, клумба, беседка и маленький пруд. Без стен, низкий силуэт"""
    s = 3
    m = M()
    grass = mat("park_grass", 0x6f9a4a, 0.95, noise=0.3, bump=0.5, ground=True)
    box(0.05, 0.05, -0.02, 2.95, 2.95, 0.02, grass, bevel=0.03, lines=False)
    path = mat("park_path", 0xcdbf9a, 0.95, noise=0.25, kind="brick", scale=28, ground=True)
    box(1.25, 0.05, 0.0, 1.75, 2.95, 0.03, path, bevel=0.01, lines=False)
    box(0.05, 1.25, 0.0, 2.95, 1.75, 0.03, path, bevel=0.01, lines=False)
    # круглая клумба в центре
    stone = mat("bedstone", 0xc8c0b0, 0.9, noise=0.2)
    cylinder(1.5, 1.5, 0.0, 0.1, 0.3, stone, verts=20)
    cylinder(1.5, 1.5, 0.09, 0.11, 0.25, mat("soil", 0x6a4a30, 0.95, noise=0.3, bump=0.5), verts=20, bevel=0)
    rr = rng(8)
    leaf = mat("leaf", 0x4a9a3a, 0.7, noise=0.2, bump=0.5)
    cols = [0xd8404a, 0xf0d040, 0x9a70e0, 0xf4f0f0, 0xe87a30]
    for i in range(14):
        a = rr.uniform(0, 6.28); r = rr.uniform(0, 0.2)
        sphere(1.5 + math.cos(a) * r, 1.5 + math.sin(a) * r, 0.14, 0.04, leaf)
        sphere(1.5 + math.cos(a) * r, 1.5 + math.sin(a) * r, 0.18, 0.024, mat("fl%x" % cols[i % 5], cols[i % 5], 0.6, noise=0.1))
    for i, (x, y) in enumerate(((0.45, 0.5), (2.5, 0.45), (0.5, 2.55), (0.7, 0.95), (2.35, 1.0))):
        tree(x, y, h=0.85, r=0.3, seed=3 + i)
    # беседка
    gx, gy = 2.35, 2.35
    cylinder(gx, gy, 0.0, 0.05, 0.36, stone, verts=12)
    wmat = mat("gazebo", 0xf0e8d4, 0.7, noise=0.05)
    for k in range(6):
        a = k * math.pi / 3
        cylinder(gx + math.cos(a) * 0.28, gy + math.sin(a) * 0.28, 0.05, 0.5, 0.017, wmat, verts=6, bevel=0)
    import lib as _lib
    _was = _lib._snow
    _lib._snow = False   # кровля красная без снежного шума; снег - гладкой шапкой сверху
    gz_roof = mat("gazebo_roof", ROOF_RED, 0.75, noise=0.25, kind="brick", scale=18)
    _lib._snow = _was
    cone(gx, gy, 0.48, 0.78, 0.42, gz_roof, verts=6, eave=True)
    if _was:
        cone(gx, gy, 0.48 + 0.3 * 0.33, 0.792, 0.42 * 0.67 * 1.05, _lib.snow_mat(), verts=6, name="gazebo_cap")
    sphere(gx, gy, 0.8, 0.025, mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0))
    # пруд с кувшинками
    cylinder(0.65, 2.3, 0.0, 0.035, 0.34, stone, verts=20)
    cylinder(0.65, 2.3, 0.03, 0.045, 0.28, mat("pond", 0x4a8ab8, 0.05, noise=0.1), verts=20, bevel=0)
    for k in range(3):
        sphere(0.55 + k * 0.1, 2.3 + (k % 2) * 0.1, 0.055, 0.035, mat("lily", 0x4a9a3a, 0.7, noise=0.1), sz=0.25)
    wood = mat("bench", 0x9a6a3a, 0.75, kind="planks", scale=30)

    def bench(x, y, along_x):
        if along_x:
            box(x - 0.14, y - 0.045, 0.1, x + 0.14, y + 0.045, 0.12, wood, bevel=0.004)
            box(x - 0.14, y + 0.04, 0.12, x + 0.14, y + 0.055, 0.22, wood, bevel=0.004)
            for xx in (x - 0.11, x + 0.11):
                box(xx - 0.01, y - 0.04, 0, xx + 0.01, y + 0.04, 0.1, iron(), bevel=0)
        else:
            box(x - 0.045, y - 0.14, 0.1, x + 0.045, y + 0.14, 0.12, wood, bevel=0.004)
            box(x + 0.04, y - 0.14, 0.12, x + 0.055, y + 0.14, 0.22, wood, bevel=0.004)
            for yy in (y - 0.11, y + 0.11):
                box(x - 0.04, yy - 0.01, 0, x + 0.04, yy + 0.01, 0.1, iron(), bevel=0)
    bench(0.9, 1.15, True); bench(2.1, 1.85, True); bench(1.15, 0.7, False); bench(1.85, 2.3, False)
    for (x, y) in ((1.18, 1.18), (1.82, 1.82)):
        bar((x, y, 0), (x, y, 0.4), 0.012, iron())
        lantern(x, y, 0.4)
    bush(0.25, 1.0, 0.1, flowers=0xf0d040, seed=11); bush(2.75, 1.0, 0.1, flowers=0xd8404a, seed=12)
    bush(1.0, 2.75, 0.1, flowers=0xf4f0f0, seed=13); bush(2.0, 0.25, 0.1, seed=14)
    return s, 1.1, {}


def fairground():
    """Ярмарочная площадь: шатры и палатки с полосатыми навесами, флажки на верёвках, помост, бочки, телега торговца"""
    s = 3
    m = M()
    yard(s, 0xb8a47a)
    pole = mat("pole", 0x6a4a2c, 0.7)

    def tent(cx, cy, c1, c2, r=0.34, h=0.3, hr=0.5):
        a = mat("tent%x" % c1, c1, 0.85, noise=0.1)
        b = mat("tent%x" % c2, c2, 0.85, noise=0.1)
        n = 8
        for k in range(n):
            a0 = k * 2 * math.pi / n; a1 = (k + 1) * 2 * math.pi / n
            mm = a if k % 2 == 0 else b
            p = lambda ang, rad, z: (cx + math.cos(ang) * rad, cy + math.sin(ang) * rad, z)
            mesh_from([p(a0, r, 0.0), p(a1, r, 0.0), p(a1, r, h), p(a0, r, h)], [(0, 1, 2, 3)], mm, name="tentwall").modifiers.new("t", 'SOLIDIFY').thickness = 0.012
            mesh_from([p(a0, r * 1.12, h), p(a1, r * 1.12, h), (cx, cy, h + hr)], [(0, 1, 2)], mm, name="tentroof").modifiers.new("t", 'SOLIDIFY').thickness = 0.012
        bar((cx, cy, h + hr - 0.02), (cx, cy, h + hr + 0.2), 0.01, pole)
        fl = mat("tflag%x" % c2, c2, 0.8, noise=0.1)
        mesh_from([(cx, cy, h + hr + 0.2), (cx + 0.14, cy, h + hr + 0.16), (cx, cy, h + hr + 0.11)], [(0, 1, 2)], fl, name="tflag").modifiers.new("t", 'SOLIDIFY').thickness = 0.01

    def stall(x0, y0, x1, y1, colors, goods):
        for x in (x0, x1):
            for y in (y0, y1):
                box(x - 0.02, y - 0.02, 0, x + 0.02, y + 0.02, 0.62 if y == y1 else 0.5, m.beam, bevel=0.004)
        box(x0 + 0.02, y0 + 0.02, 0, x1 - 0.02, y1 - 0.02, 0.2, mat("stallbase", 0x7a5232, 0.8, kind="planks", scale=30), bevel=0.006)
        box(x0, y0, 0.2, x1, y1, 0.26, m.wood, bevel=0.008)
        awning(x0 - 0.04, y0 - 0.08, x1 + 0.04, y1 + 0.02, 0.66, 0.5, True, colors=colors)
        goods(x0, y0, x1, y1)

    def apples(x0, y0, x1, y1):
        for i, c in enumerate((0xc8302a, 0xe8a030, 0x7ab040)):
            fm = mat("fr%x" % c, c, 0.4, noise=0.1)
            bx = x0 + 0.06 + i * (x1 - x0 - 0.12) / 3
            for k in range(3):
                sphere(bx + 0.03 + (k % 2) * 0.05, y0 + 0.1 + (k // 2) * 0.07, 0.29, 0.03, fm)

    def cloths(x0, y0, x1, y1):
        for i, c in enumerate((0x3a5aa8, 0xc8a030, 0x8a3a8a)):
            cm = mat("cl%x" % c, c, 0.9, noise=0.1)
            bx = x0 + 0.05 + i * (x1 - x0 - 0.1) / 3
            box(bx, y0 + 0.05, 0.26, bx + (x1 - x0 - 0.1) / 3 - 0.01, y0 + 0.22, 0.3 + 0.03 * (3 - i), cm, bevel=0.01)

    stall(0.3, 2.2, 1.1, 2.62, (0xc83a30, 0xf4ecd8), apples)
    stall(1.6, 2.35, 2.4, 2.77, (0x3a62b0, 0xf4ecd8), cloths)
    tent(0.55, 1.4, 0xc83a30, 0xf4ecd8)
    tent(2.35, 1.55, 0x3a62b0, 0xf4ecd8, r=0.38, h=0.32, hr=0.55)
    tent(2.4, 0.5, 0x3a8a4a, 0xe8d070, r=0.3, h=0.26, hr=0.45)
    # помост со ступенями
    plank = mat("stage", 0x9a7448, 0.8, kind="planks", scale=22)
    box(1.0, 1.0, 0.0, 1.8, 1.55, 0.14, plank, bevel=0.01)
    for i in range(3):
        box(0.92 - i * 0.08, 1.1, i * 0.045, 1.0, 1.45, 0.045 * (i + 1), plank, bevel=0.004)
    # флажки на верёвках между столбами
    posts = [(0.15, 0.15), (1.5, 0.15), (2.85, 0.15), (2.85, 1.5), (2.85, 2.85)]
    for (x, y) in posts:
        cylinder(x, y, 0, 0.62, 0.02, pole, verts=6, bevel=0)
    rope = mat("rope", 0xd8c8a0, 0.9, noise=0)
    flagc = (0xc83a30, 0xf0d040, 0x3a62b0, 0x3a8a4a, 0xf4ecd8)

    def sag(a, b, t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, 0.6 - 0.08 * math.sin(math.pi * t))
    for (a, b) in zip(posts, posts[1:]):
        n = 7
        dx, dy = (0.04, 0.0) if b[1] == a[1] else (0.0, 0.04)
        for k in range(n):
            bar(sag(a, b, k / n), sag(a, b, (k + 1) / n), 0.004, rope, verts=4)
            pm = sag(a, b, (k + 0.5) / n)
            fm = mat("bunt%x" % flagc[k % 5], flagc[k % 5], 0.8, noise=0.1)
            mesh_from([(pm[0] - dx, pm[1] - dy, pm[2]), (pm[0] + dx, pm[1] + dy, pm[2]), (pm[0], pm[1], pm[2] - 0.1)], [(0, 1, 2)], fm, name="bunting").modifiers.new("t", 'SOLIDIFY').thickness = 0.008
    barrel(0.35, 0.65); barrel(0.5, 0.75); crate(0.35, 0.9, 0.13)
    barrel(1.9, 2.0)
    goods = mat("cartgoods", 0xd8b050, 0.8, noise=0.3, bump=0.5)
    cart(1.5, 0.55, along_x=True, load=lambda x, y, z: [sphere(x - 0.08 + i * 0.08, y, z + 0.03, 0.05, goods) for i in range(3)])
    return s, 1.3, {}


def guildhall():
    """Гильдейский дом: каменный низ с аркадой, фахверковый верх, ступенчатые фронтоны, башенка с часами, знамёна гильдий"""
    s = 4
    m = M()
    yard(s, 0x9a8c74, cobble=True)
    x0, y0, x1, y1 = 0.6, 1.0, 3.4, 3.5
    zp, z1, z2 = 0.12, 0.78, 1.45
    cx = 2.0
    body(x0, y0, x1, y1, z1, m.stone, zp, m)
    box(x0 - 0.05, y0 - 0.05, z1, x1 + 0.05, y1 + 0.05, z1 + 0.05, m.stone_dark, bevel=0.01)
    box(x0, y0, z1 + 0.05, x1, y1, z2, m.plaster_white, bevel=0.01)
    timber_all(x0, y0, x1, y1, z1 + 0.05, z2, m, posts_x=8, posts_y=7)
    gable_roof(x0, y0, x1, y1, z2, 0.78, False, m.roof_red, over=0.1, rows=9, gable_mat=m.stone)
    windows_all(x0, y0, x1, y1, 0.36, m, nx=4, ny=4, w=0.15, h=0.28, shutter=m.shutter_blue, skip={("y-1", 0), ("y-1", 1), ("y-1", 2), ("y-1", 3)})
    windows_all(x0, y0, x1, y1, 0.98, m, nx=6, ny=5, w=0.14, h=0.26, shutter=m.shutter_blue)
    # аркада перед входом: колонны, арки, плоская кровля с балюстрадой
    ay = y0 - 0.34
    cols = [0.78 + k * 0.5 for k in range(6)]
    for x in cols:
        cylinder(x, ay, zp, z1 - 0.1, 0.055, m.stone, verts=10)
        box(x - 0.075, ay - 0.075, z1 - 0.1, x + 0.075, ay + 0.075, z1 - 0.06, m.stone_dark, bevel=0.01)
        box(x - 0.07, ay - 0.07, zp, x + 0.07, ay + 0.07, zp + 0.05, m.stone_dark, bevel=0.01)
    for a, b in zip(cols, cols[1:]):
        box(a, ay - 0.06, z1 - 0.1, b, ay + 0.06, z1 - 0.02, m.stone, bevel=0.01)
        bpy.ops.mesh.primitive_torus_add(major_radius=(b - a) / 2 - 0.03, minor_radius=0.025, location=((a + b) / 2, ay, z1 - 0.1), rotation=(math.radians(90), 0, 0))
        bpy.context.object.data.materials.append(m.stone_dark)
    box(cols[0] - 0.1, ay - 0.1, z1 - 0.02, cols[-1] + 0.1, y0 + 0.02, z1 + 0.04, m.stone_dark, bevel=0.01)
    for x in cols:
        box(x - 0.025, ay - 0.1, z1 + 0.04, x + 0.025, ay - 0.06, z1 + 0.12, m.stone, bevel=0.004)
    bar((cols[0], ay - 0.08, z1 + 0.12), (cols[-1], ay - 0.08, z1 + 0.12), 0.012, m.stone, verts=6)
    door(("y", y0, -1), cx, 0.3, 0.52, m.door, m.stone_dark, z=zp)
    for i in range(3):
        box(cx - 0.4 - i * 0.04, ay - 0.1 - i * 0.07, 0, cx + 0.4 + i * 0.04, ay - 0.03, 0.12 - i * 0.035, m.stone_dark, bevel=0.008)
    for xx in (cols[0] - 0.18, cols[-1] + 0.18):
        bar((xx, ay - 0.2, 0), (xx, ay - 0.2, 0.45), 0.012, iron())
        lantern(xx, ay - 0.2, 0.45)
    # ступенчатые фронтоны на торцах y0 и y1
    for (yy, side) in ((y0, -1), (y1, 1)):
        hw = (x1 - x0) / 2 + 0.1
        for k in range(5):
            half = hw - k * 0.3
            if half < 0.12:
                break
            ya, yb = (yy - 0.06, yy + 0.1) if side == -1 else (yy - 0.1, yy + 0.06)
            box((x0 + x1) / 2 - half, ya, z2 + k * 0.145, (x0 + x1) / 2 + half, yb, z2 + k * 0.145 + 0.145, m.stone, bevel=0.008)
        box((x0 + x1) / 2 - 0.05, yy - 0.04, z2 + 0.66, (x0 + x1) / 2 + 0.05, yy + 0.04, z2 + 0.8, m.stone_dark, bevel=0.008)
    # башенка с часами на коньке
    tx0, ty0, tx1, ty1 = 1.65, 2.05, 2.35, 2.75
    tcx, tcy = 2.0, 2.4
    TH = 2.65
    box(tx0 - 0.03, ty0 - 0.03, 1.3, tx1 + 0.03, ty1 + 0.03, 1.36, m.stone_dark, bevel=0.01)
    box(tx0, ty0, 1.34, tx1, ty1, TH, m.stone, bevel=0.012)
    box(tx0 - 0.04, ty0 - 0.04, TH, tx1 + 0.04, ty1 + 0.04, TH + 0.06, m.stone_dark, bevel=0.01)
    for (px, py) in ((tx0, ty0), (tx1, ty0), (tx0, ty1), (tx1, ty1)):
        cone(px, py, TH + 0.06, TH + 0.32, 0.07, m.roof_red, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    cone(tcx, tcy, TH + 0.05, TH + 0.95, 0.5, m.roof_red, verts=4, eave=True).rotation_euler = (0, 0, math.radians(45))
    gold = mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0)
    bar((tcx, tcy, TH + 0.9), (tcx, tcy, TH + 1.2), 0.012, gold)
    sphere(tcx, tcy, TH + 1.22, 0.035, gold)
    clockface = mat("clockface", 0xf4efe0, 0.4, noise=0)
    dark = mat("slitdark", 0x14100e, 1.0, noise=0)
    zc = 2.2
    for (py, side) in ((ty0, -1), (ty1, 1)):
        _disc_y(tcx, py + side * 0.012, zc, 0.2, clockface, thick=0.03)
        bpy.ops.mesh.primitive_torus_add(major_radius=0.2, minor_radius=0.014, location=(tcx, py + side * 0.02, zc), rotation=(math.radians(90), 0, 0))
        bpy.context.object.data.materials.append(gold)
        bar((tcx, py + side * 0.03, zc), (tcx, py + side * 0.03, zc + 0.15), 0.008, dark, verts=4)
        bar((tcx, py + side * 0.03, zc), (tcx + 0.1, py + side * 0.03, zc), 0.008, dark, verts=4)
    for (px, side) in ((tx0, -1), (tx1, 1)):
        bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=0.2, depth=0.03, location=(px + side * 0.012, tcy, zc), rotation=(0, math.radians(90), 0))
        bpy.context.object.data.materials.append(clockface)
        bpy.ops.mesh.primitive_torus_add(major_radius=0.2, minor_radius=0.014, location=(px + side * 0.02, tcy, zc), rotation=(0, math.radians(90), 0))
        bpy.context.object.data.materials.append(gold)
        bar((px + side * 0.03, tcy, zc), (px + side * 0.03, tcy, zc + 0.15), 0.008, dark, verts=4)
        bar((px + side * 0.03, tcy, zc), (px + side * 0.03, tcy + 0.1, zc), 0.008, dark, verts=4)
    for (py, side) in ((ty0, -1), (ty1, 1)):
        box(tcx - 0.08, py - 0.02, 1.6, tcx + 0.08, py + 0.02, 1.9, dark, bevel=0)
    # гербовые знамёна гильдий над входом
    for i, col in enumerate((0xb83030, 0x2f5aa8, 0x3a8a4a, 0xc8a030)):
        bx = 1.15 + i * 0.57
        bar((bx, y0, 1.38), (bx, y0 - 0.17, 1.38), 0.01, iron(), verts=5)
        bar((bx - 0.1, y0 - 0.17, 1.38), (bx + 0.1, y0 - 0.17, 1.38), 0.01, iron(), verts=5)
        _banner(bx, y0 - 0.17, 1.37, 0.17, 0.4, col)
    top = chimney(3.1, 3.2, 1.2, 2.1, m.chim)
    bush(0.35, 0.5, 0.12, flowers=0xf4f0f0, seed=21); bush(3.65, 0.5, 0.12, flowers=0xd8404a, seed=22)
    bush(0.35, 3.7, 0.12, seed=23); bush(3.65, 3.7, 0.12, seed=24)
    barrel(0.3, 1.3); crate(3.7, 1.3, 0.14)
    return s, 4.1, {"smoke": top}


# ---------- стены ----------

WALL_DIRS = {0: (0.5, 0.0), 1: (1.0, 0.5), 2: (0.5, 1.0), 3: (0.0, 0.5)}


def wall_post(kind):
    """Столб/башенка в центре клетки"""
    m = M()
    if kind == "wood":
        wood = mat("palisade", 0x8a6040, 0.85, noise=0.25, bump=0.5)
        for (dx, dy, h) in ((0, 0, 0.78), (-0.07, 0.05, 0.7), (0.07, -0.05, 0.7), (0.06, 0.07, 0.66), (-0.06, -0.06, 0.66)):
            cylinder(0.5 + dx, 0.5 + dy, 0, h, 0.055, wood, verts=8, bevel=0)
            cone(0.5 + dx, 0.5 + dy, h, h + 0.13, 0.055, wood, verts=8)
    else:
        box(0.34, 0.34, 0, 0.66, 0.66, 0.78, m.stone, bevel=0.015)
        box(0.31, 0.31, 0.78, 0.69, 0.69, 0.85, m.stone_dark, bevel=0.012)
        for (dx, dy) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            box(0.5 + dx * 0.13 - 0.06, 0.5 + dy * 0.13 - 0.06, 0.85, 0.5 + dx * 0.13 + 0.06, 0.5 + dy * 0.13 + 0.06, 0.97, m.stone, bevel=0.008)


def wall_seg(kind, d):
    """Полусегмент стены от центра клетки к её краю в сторону d"""
    m = M()
    ex, ey = WALL_DIRS[d]
    cx, cy = 0.5, 0.5
    dx, dy = ex - cx, ey - cy
    if kind == "wood":
        wood = mat("palisade", 0x8a6040, 0.85, noise=0.25, bump=0.5)
        wood2 = mat("palisade2", 0x7a5434, 0.85, noise=0.25, bump=0.5)
        n = 4
        for i in range(n):
            t = (i + 1) / n
            px, py = cx + dx * t * 1.0, cy + dy * t * 1.0
            h = 0.5 + 0.06 * ((i * 7) % 3)
            for side in (-0.04, 0.04):
                qx, qy = px + (side if dy else 0), py + (side if dx else 0)
                mm = wood if (i + (side > 0)) % 2 else wood2
                cylinder(qx, qy, 0, h, 0.045, mm, verts=8, bevel=0)
                cone(qx, qy, h, h + 0.1, 0.045, mm, verts=8)
        bar((cx, cy, 0.18), (ex, ey, 0.18), 0.015, m.beam)
        bar((cx, cy, 0.38), (ex, ey, 0.38), 0.015, m.beam)
    else:
        hx = 0.09
        if dx:
            x0, x1 = min(cx, ex), max(cx, ex)
            y0, y1 = cy - hx, cy + hx
        else:
            y0, y1 = min(cy, ey), max(cy, ey)
            x0, x1 = cx - hx, cx + hx
        box(x0, y0, 0, x1, y1, 0.58, m.stone, bevel=0.012)
        box(x0 - (0.015 if dy else 0), y0 - (0.015 if dx else 0), 0.58, x1 + (0.015 if dy else 0), y1 + (0.015 if dx else 0), 0.62, m.stone_dark, bevel=0.008)
        sg = 1 if (dx + dy) > 0 else -1
        for off in (0.1, 0.36):
            px = cx + (sg * off if dx else 0); py = cy + (sg * off if dy else 0)
            bx, by = (0.055, 0.1) if dx else (0.1, 0.055)
            box(px - bx, py - by, 0.62, px + bx, py + by, 0.74, m.stone, bevel=0.008)


# ---------- украшения ----------

def flowerbed():
    s = 1
    stone = mat("bedstone", 0xc8c0b0, 0.9, noise=0.2)
    box(0.08, 0.08, 0, 0.92, 0.92, 0.1, stone, bevel=0.02)
    box(0.13, 0.13, 0.1, 0.87, 0.87, 0.11, mat("soil", 0x6a4a30, 0.95, noise=0.3, bump=0.5), bevel=0, lines=False)
    rr = rng(5)
    leaf = mat("leaf", 0x4a9a3a, 0.7, noise=0.2, bump=0.5)
    cols = [0xd8404a, 0xf0d040, 0x9a70e0, 0xf4f0f0, 0xe87a30]
    for i in range(22):
        x = rr.uniform(0.2, 0.8); y = rr.uniform(0.2, 0.8)
        sphere(x, y, 0.15, 0.045, leaf)
        sphere(x, y, 0.2, 0.026, mat("fl%x" % cols[i % 5], cols[i % 5], 0.6, noise=0.1))
    return s, 0.6, {}


def fountain():
    s = 2
    yard(s, 0x9a8c74, cobble=True)
    stone = mat("fstone", 0xc8b898, 0.7, noise=0.2, kind="brick", scale=9)
    water = mat("fwater", 0x4a9ad8, 0.05, noise=0.1)
    cylinder(1, 1, 0, 0.22, 0.78, stone, verts=32)
    cylinder(1, 1, 0.2, 0.235, 0.7, water, verts=32, bevel=0)
    cylinder(1, 1, 0.2, 0.62, 0.1, stone, verts=16)
    cylinder(1, 1, 0.6, 0.68, 0.32, stone, verts=24)
    cylinder(1, 1, 0.66, 0.695, 0.27, water, verts=24, bevel=0)
    cylinder(1, 1, 0.68, 0.95, 0.05, stone, verts=12)
    sphere(1, 1, 1.0, 0.08, stone)
    spray = mat("spray", 0xd8eefa, 0.05, noise=0)
    for k in range(8):
        a = k * math.pi / 4
        for t in (0.25, 0.5, 0.75):
            r = 0.1 + t * 0.22
            sphere(1 + math.cos(a) * r, 1 + math.sin(a) * r, 1.0 - t * t * 0.35, 0.02, spray)
    return s, 1.4, {}


def statue():
    s = 2
    yard(s, 0x9a8c74, cobble=True)
    stone = mat("fstone", 0xc8b898, 0.7, noise=0.2, kind="brick", scale=9)
    box(0.25, 0.25, 0, 1.75, 1.75, 0.08, stone, bevel=0.02)
    box(0.4, 0.4, 0.08, 1.6, 1.6, 0.16, stone, bevel=0.02)
    box(0.7, 0.7, 0.16, 1.3, 1.3, 0.75, stone, bevel=0.02)
    box(0.66, 0.66, 0.7, 1.34, 1.34, 0.78, stone, bevel=0.02)
    bronze = mat("bronze", 0x6a8a6a, 0.4, metal=0.7, noise=0.2)
    figure(1.0, 1.0, 0.78, 1.0, bronze)
    plaque = mat("brass", 0xc89a3a, 0.35, metal=0.8, noise=0)
    box(0.85, 0.69, 0.35, 1.15, 0.7, 0.5, plaque, bevel=0)
    for (x, y) in ((0.3, 0.3), (1.7, 0.3), (0.3, 1.7), (1.7, 1.7)):
        bush(x, y, 0.09, seed=int(x * 10 + y))
    return s, 2.3, {}


def level_marks(s, level):
    """
    Признаки уровня поверх любой модели: 2 - синий флажок на шесте у левого угла и навес с ящиками у правого;
    3 - ещё красно-золотое знамя на втором шесте, фонари у ближнего угла и мощёная кромка двора.
    """
    if level < 2:
        return
    m = M()
    pole = mat("pole", 0x6a4a2c, 0.7)
    blue = mat("pennant_blue", 0x2f5aa8, 0.7, noise=0.1)
    # шест с флажком - у левого угла (x=0, y=s)
    px, py = 0.12, s - 0.12
    bar((px, py, 0), (px, py, 1.25), 0.018, pole)
    o = mesh_from([(px, py, 1.22), (px, py - 0.34, 1.13), (px, py, 1.03)], [(0, 1, 2)], blue, name="pennant")
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.012
    sphere(px, py, 1.27, 0.03, mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0))
    # навес с ящиками - у правого угла (x=s, y=0)
    nx, ny = s - 0.32, 0.1
    for (x, y) in ((nx, ny), (nx + 0.26, ny), (nx, ny + 0.24), (nx + 0.26, ny + 0.24)):
        box(x - 0.015, y - 0.015, 0, x + 0.015, y + 0.015, 0.36, m.beam, bevel=0)
    gable_roof(nx - 0.02, ny - 0.02, nx + 0.28, ny + 0.26, 0.36, 0.12, True, m.shingle, over=0.03, rows=3)
    crate(nx + 0.08, ny + 0.08, 0.1); crate(nx + 0.19, ny + 0.15, 0.09); barrel(nx + 0.08, ny + 0.18, r=0.05, h=0.12)
    if level < 3:
        return
    red = mat("banner_red", 0xb83030, 0.8, noise=0.1)
    gold = mat("gold", 0xd8b040, 0.3, metal=0.9, noise=0)
    # второй шест со знаменем рядом с флажком
    qx, qy = 0.34, s - 0.1
    bar((qx, qy, 0), (qx, qy, 1.45), 0.02, pole)
    bar((qx, qy, 1.38), (qx, qy - 0.26, 1.38), 0.01, pole)
    o = mesh_from([(qx, qy - 0.02, 1.37), (qx, qy - 0.25, 1.37), (qx, qy - 0.25, 1.02), (qx, qy - 0.135, 1.08), (qx, qy - 0.02, 1.02)],
                  [(0, 1, 2, 3, 4)], red, name="banner")
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.01
    sphere(qx, qy - 0.135, 1.2, 0.035, gold, sx=0.3)
    sphere(qx, qy, 1.47, 0.035, gold)
    # фонари у ближнего угла и мощёная кромка
    for (lx, ly) in ((0.08, 0.3), (0.3, 0.08)):
        bar((lx, ly, 0), (lx, ly, 0.42), 0.012, iron())
        lantern(lx, ly, 0.42)
    cob = mat("yard_edge", 0xb0a48a, 0.9, noise=0.25, kind="brick", scale=30, ground=True)
    box(0.0, 0.0, 0.0, s, 0.06, 0.03, cob, bevel=0.01, lines=False)
    box(0.0, 0.0, 0.0, 0.06, s, 0.03, cob, bevel=0.01, lines=False)


def icon_book(x, y, z, axis):
    cover = mat("book_cover", 0x3a5a98, 0.6, noise=0.05)
    pages = mat("book_pages", 0xf4ecd8, 0.8, noise=0.05)
    if axis == "x":
        box(x - 0.012, y - 0.085, z - 0.07, x + 0.012, y + 0.085, z + 0.07, cover, bevel=0.004)
        box(x - 0.016, y - 0.07, z - 0.055, x + 0.016, y + 0.075, z + 0.055, pages, bevel=0.002)
    else:
        box(x - 0.085, y - 0.012, z - 0.07, x + 0.085, y + 0.012, z + 0.07, cover, bevel=0.004)
        box(x - 0.07, y - 0.016, z - 0.055, x + 0.075, y + 0.016, z + 0.055, pages, bevel=0.002)


def bench(x, y, along_x=True, length=0.3):
    wood = mat("bench", 0x8a5e36, 0.75, kind="planks", scale=30)
    if along_x:
        box(x - length / 2, y - 0.04, 0.09, x + length / 2, y + 0.04, 0.105, wood, bevel=0.004)
        for s_ in (-1, 1):
            box(x + s_ * length * 0.4 - 0.012, y - 0.03, 0, x + s_ * length * 0.4 + 0.012, y + 0.03, 0.09, wood, bevel=0)
    else:
        box(x - 0.04, y - length / 2, 0.09, x + 0.04, y + length / 2, 0.105, wood, bevel=0.004)
        for s_ in (-1, 1):
            box(x - 0.03, y + s_ * length * 0.4 - 0.012, 0, x + 0.03, y + s_ * length * 0.4 + 0.012, 0.09, wood, bevel=0)


def school():
    """Школа: беленый домик с колокольней на коньке, вывеска-книга и грифельная доска у входа, скамья, палисадник"""
    s = 2
    m = M()
    yard(s)
    x0, y0, x1, y1 = 0.6, 0.8, 1.7, 1.6
    zp, zw = 0.1, 0.72
    body(x0, y0, x1, y1, zw, m.plaster_white, zp, m)
    timber_all(x0, y0, x1, y1, zp, zw, m, posts_x=4, posts_y=3)
    gable_roof(x0, y0, x1, y1, zw, 0.5, True, m.roof_red, over=0.1, rows=7, gable_mat=m.plaster_white)
    windows_all(x0, y0, x1, y1, 0.34, m, nx=3, ny=2, shutter=m.shutter_blue, skip={("y-1", 1)})
    door(("y", y0, -1), 1.15, 0.22, 0.42, m.door, m.beam, z=zp)
    sign(("y", y0, -1), 1.5, 0.62, icon_book, arm=0.3)
    # звонница на коньке
    zr = zw + 0.5
    cx, cy = 0.82, (y0 + y1) / 2
    for dx in (-0.06, 0.06):
        for dy in (-0.06, 0.06):
            box(cx + dx - 0.012, cy + dy - 0.012, zr - 0.02, cx + dx + 0.012, cy + dy + 0.012, zr + 0.28, m.beam, bevel=0)
    brass = mat("brass", 0xc89a3a, 0.35, metal=0.8, noise=0)
    cone(cx, cy, zr + 0.1, zr + 0.24, 0.055, brass, verts=12)
    sphere(cx, cy, zr + 0.1, 0.014, iron())
    box(cx - 0.085, cy - 0.085, zr + 0.28, cx + 0.085, cy + 0.085, zr + 0.3, m.beam, bevel=0)
    cone(cx, cy, zr + 0.3, zr + 0.5, 0.15, m.roof_red, verts=4)
    # грифельная доска на мольберте
    slate = mat("slateboard", 0x2e3a38, 0.8, noise=0.1)
    bx, by = 0.35, 0.45
    box(bx - 0.13, by - 0.015, 0.14, bx + 0.13, by + 0.015, 0.34, m.wood, bevel=0.006)
    box(bx - 0.11, by - 0.02, 0.16, bx + 0.11, by + 0.02, 0.32, slate, bevel=0.003)
    chalk = mat("chalk", 0xf0f0e8, 0.9, noise=0)
    bar((bx - 0.07, by - 0.025, 0.28), (bx + 0.03, by - 0.025, 0.28), 0.004, chalk, verts=4)
    bar((bx - 0.07, by - 0.025, 0.23), (bx + 0.07, by - 0.025, 0.23), 0.004, chalk, verts=4)
    for sx_ in (-0.1, 0.1):
        bar((bx + sx_, by, 0.14), (bx + sx_ * 1.3, by + 0.1, 0), 0.008, m.wood, verts=4)
    bar((bx, by, 0.34), (bx, by + 0.12, 0), 0.008, m.wood, verts=4)
    # скамья и палисадник
    bench(1.4, 0.55)
    bench(1.65, 0.55, along_x=False, length=0.25)
    fence([(0.15, 0.2), (1.85, 0.2), (1.85, 0.75)], h=0.14)
    fence([(0.15, 0.2), (0.15, 1.9)], h=0.14)
    tree(0.3, 1.55, h=0.85, r=0.3, seed=21)
    tree(1.7, 1.85, h=0.8, r=0.26, seed=22)
    bush(0.5, 0.75, 0.1, flowers=0xe87a8a, seed=4)
    bush(1.6, 0.35, 0.09, flowers=0xf0d040, seed=5)
    return s, 1.6, {}


def tradepost():
    """Торговый пост: бревенчатый амбар-контора с навесом, коновязь, телега у ворот, ящики, бочки, тюки, весы"""
    s = 3
    m = M()
    yard(s, 0xb0a084, cobble=True)
    x0, y0, x1, y1 = 0.35, 1.1, 2.15, 2.5
    zp, zw = 0.1, 0.62
    body(x0, y0, x1, y1, zw, m.logs, zp, m)
    gable_roof(x0, y0, x1, y1, zw, 0.42, True, m.roof_brown, over=0.12, rows=7, gable_mat=m.logs)
    windows_all(x0, y0, x1, y1, 0.34, m, nx=3, ny=2, shutter=m.shutter_blue, skip={("y-1", 0), ("y-1", 1)})
    # ворота и дверь на стороне -y
    door(("y", y0, -1), 0.8, 0.5, 0.46, m.door, m.beam, z=zp)
    sign(("y", y0, -1), 1.25, 0.72, icon_scales, arm=0.3)
    top = chimney(2.0, 2.3, 0.6, 1.05, m.chim)
    # навес у фасада справа от входа
    for (x, y) in ((1.45, 0.5), (2.12, 0.5)):
        box(x - 0.025, y - 0.025, 0, x + 0.025, y + 0.025, 0.4, m.beam, bevel=0)
    gable_roof(1.4, 0.45, 2.2, 1.1, 0.4, 0.1, True, m.shingle, over=0.04, rows=4)
    for (x, y, z, c_) in ((0.45, 0.85, 0.0, 0.14), (0.62, 0.9, 0.0, 0.12), (0.52, 0.87, 0.14, 0.11)):
        crate(x, y, c_, z=z)
    barrel(1.6, 0.85); barrel(1.77, 0.9); barrel(1.66, 0.7)
    for i, (sx_, sy_) in enumerate(((2.0, 0.85), (2.1, 0.88), (2.03, 0.72), (2.04, 0.8))):
        _sack(sx_, sy_, (0x9a8a60, 0xb0a070)[i % 2])
    # весы на столбе
    box(1.1, 0.6, 0, 1.2, 0.7, 0.03, m.stone_dark, bevel=0.005)
    icon_scales(1.15, 0.65, 0.3, "y")
    bar((1.15, 0.65, 0.03), (1.15, 0.65, 0.22), 0.01, iron())
    # коновязь
    for x in (0.4, 1.2):
        box(x - 0.025, 0.2, 0, x + 0.025, 0.25, 0.26, m.beam, bevel=0)
    bar((0.4, 0.225, 0.22), (1.2, 0.225, 0.22), 0.016, m.wood, verts=6)
    # телега у ворот (гружёная тюками)
    def load(x, y, z):
        for k in range(2):
            _sack(x - 0.07 + k * 0.14, y, 0xa89868, r=0.06)
    cart(2.45, 0.9, along_x=False, load=load)
    # ящики и бочки сбоку
    for (x, y, z, c_) in ((2.55, 1.6, 0.0, 0.18), (2.55, 1.85, 0.0, 0.16), (2.55, 1.7, 0.18, 0.14)):
        crate(x, y, c_, z=z)
    barrel(2.6, 2.3); barrel(2.45, 2.4)
    tree(0.2, 2.75, h=0.8, r=0.26, seed=31)
    tree(2.75, 2.7, h=0.8, r=0.26, seed=32)
    bar((0.2, 1.0, 0), (0.2, 1.0, 0.5), 0.012, iron())
    lantern(0.2, 1.0, 0.5)
    return s, 1.4, {"smoke": top}


BUILDINGS = {
    "hall": hall, "house": house, "house2": house2, "house3": house3, "store": store,
    "woodcutter": woodcutter, "forester": forester, "sawmill": sawmill, "quarry": quarry, "farm": farm, "mill": mill,
    "bakery": bakery, "fisher": fisher, "fisher_shore": fisher_shore, "well": well, "market": market, "church": church,
    "hunter": hunter, "orchard": orchard, "pasture": pasture, "weaver": weaver, "brewery": brewery,
    "firewatch": firewatch, "healer": healer, "tavern": tavern,
    "watchtower": watchtower,
    "mine": mine, "charcoal": charcoal, "smelter": smelter, "smithy": smithy, "tannery": tannery, "tailor": tailor,
    "piggery": piggery, "smokehouse": smokehouse, "apiary": apiary, "chandler": chandler,
    "guardtower": guardtower, "cathedral": cathedral, "castle": castle,
    "flowerbed": flowerbed, "fountain": fountain, "statue": statue,
    "carpenter": carpenter, "wheelwright": wheelwright, "harbor": harbor, "park": park, "fairground": fairground, "guildhall": guildhall,
    "school": school, "tradepost": tradepost, "coalmine": coalmine,
}
