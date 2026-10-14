"""
Детали для зданий: вывески-знаки на кованых кронштейнах, заборы, деревья, овцы, лодка, навесы, телега,
фигура для статуи. Всё в единицах клетки; люди в игре ростом ~0.55, дверь ~0.45.
"""
import math
import bpy
import bmesh
import lib
from lib import *


def iron():
    return mat("iron", 0x2c2826, 0.45, metal=0.7, noise=0)


def _rot(o, rz):
    o.rotation_euler[2] += rz
    return o


def bar(p0, p1, r, m, name="bar", verts=6):
    """Стержень от p0 до p1 (цилиндр)"""
    a = Vector(p0); b = Vector(p1)
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=d.length, location=(a + b) / 2)
    o = bpy.context.object
    o.name = name
    o.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    o.data.materials.append(m)
    return o


# ---------- вывеска ----------

SIGN_SCALE = 1.9


def sign(face, u, z, icon, arm=0.34):
    """
    Вывеска на стене: кованый кронштейн выходит из стены наружу на arm, под концом на цепочках висит знак.
    face = ("x" | "y", координата стены, наружу -1 | +1); u - место вдоль стены; z - высота кронштейна.
    icon(x, y, z, along) строит знак с центром в (x, y, z); along - ось кронштейна ("x" | "y").
    """
    axis, c, out = face
    m = iron()
    if axis == "x":
        w0 = (c, u, z); w1 = (c + out * arm, u, z)
        brace0 = (c, u, z - 0.12)
    else:
        w0 = (u, c, z); w1 = (u, c + out * arm, z)
        brace0 = (u, c, z - 0.12)
    bar(w0, w1, 0.015, m)
    mid = tuple((w0[i] + w1[i] * 1.0) / 2 for i in range(3))
    bar(brace0, (mid[0], mid[1], z), 0.008, m)
    # завиток на конце
    tip = w1
    bpy.ops.mesh.primitive_torus_add(major_radius=0.025, minor_radius=0.006, location=(tip[0], tip[1], tip[2] + 0.02),
                                     rotation=(math.radians(90), 0, 0) if axis == "x" else (math.radians(90), 0, math.radians(90)))
    bpy.context.object.data.materials.append(m)
    # цепочки и знак
    hx = (w0[0] + w1[0] * 2) / 3 if axis == "x" else w0[0]
    hy = (w0[1] + w1[1] * 2) / 3 if axis == "y" else w0[1]
    cx = (w0[0] + w1[0]) / 2 + (0.03 * out if axis == "x" else 0)
    cy = (w0[1] + w1[1]) / 2 + (0.03 * out if axis == "y" else 0)
    bar((cx, cy, z), (cx, cy, z - 0.05), 0.006, m, verts=4)
    # знак крупнее натуры (SIGN_SCALE) - чтобы читался на карте
    before = set(bpy.data.objects)
    icon(cx, cy, z - 0.05 - 0.09 * SIGN_SCALE, axis)
    center = Vector((cx, cy, z - 0.05 - 0.09 * SIGN_SCALE))
    for o in set(bpy.data.objects) - before:
        o.location = center + (o.location - center) * SIGN_SCALE
        o.scale = o.scale * SIGN_SCALE


def _flat_rot(axis):
    # плоский знак в плоскости кронштейна (вдоль оси и вверх)
    return (math.radians(90), 0, math.radians(90)) if axis == "y" else (math.radians(90), 0, 0)


def icon_pretzel(x, y, z, axis):
    m = mat("pretzel", 0xb8743a, 0.55, noise=0.1, bump=0.2)
    r = _flat_rot(axis)
    for dx, rr in ((-0.035, 0.05), (0.035, 0.05)):
        off = (dx, 0, 0) if axis == "x" else (0, dx, 0)
        bpy.ops.mesh.primitive_torus_add(major_radius=rr, minor_radius=0.016, location=(x + off[0], y + off[1], z + 0.01), rotation=r)
        bpy.context.object.data.materials.append(m)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.07, minor_radius=0.017, location=(x, y, z - 0.01), rotation=r)
    o = bpy.context.object
    o.scale = (1, 0.6, 1) if axis == "x" else (0.6, 1, 1)
    o.data.materials.append(m)


def icon_mug(x, y, z, axis):
    wood = mat("mug", 0x9a6a3a, 0.6, kind="planks", scale=60)
    foam = mat("foam", 0xfaf4e4, 0.9, noise=0.05, bump=0.3)
    cylinder(x, y, z - 0.07, z + 0.07, 0.055, wood, verts=14)
    sphere(x, y, z + 0.075, 0.056, foam, sz=0.5)
    hx, hy = (x + 0.06, y) if axis == "x" else (x, y + 0.06)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.035, minor_radius=0.011, location=(hx, hy, z), rotation=_flat_rot(axis))
    bpy.context.object.data.materials.append(iron())


def icon_fish(x, y, z, axis):
    m = mat("fish", 0x8aa6b8, 0.35, metal=0.3, noise=0.1)
    o = sphere(x, y, z, 0.05, m, sx=2.2 if axis == "x" else 0.7, sy=0.7 if axis == "x" else 2.2, sz=1.0)
    tx, ty = (x + 0.12, y) if axis == "x" else (x, y + 0.12)
    o = cone(tx, ty, z, z + 0.001, 0.05, m, verts=3)
    o.scale = (1, 1, 1)
    o.rotation_euler = (0, math.radians(90), 0) if axis == "x" else (math.radians(90), 0, 0)
    o.location = (tx, ty, z)
    eye = mat("eye", 0x101010, 0.3, noise=0)
    ex, ey = (x - 0.07, y - 0.035) if axis == "x" else (x - 0.035, y - 0.07)
    sphere(ex, ey, z + 0.015, 0.01, eye)


def icon_yarn(x, y, z, axis):
    m = mat("yarn", 0xc84a5a, 0.95, noise=0.1, bump=0.6)
    sphere(x, y, z, 0.065, m)
    for k in range(3):
        bpy.ops.mesh.primitive_torus_add(major_radius=0.066, minor_radius=0.006, location=(x, y, z), rotation=(math.radians(40 * k), math.radians(60), 0))
        bpy.context.object.data.materials.append(mat("yarn2", 0xa83444, 0.9, noise=0))


def icon_mortar(x, y, z, axis):
    stone = mat("mortar", 0xd8d0c0, 0.7, noise=0.1)
    cylinder(x, y, z - 0.06, z + 0.03, 0.06, stone, verts=14)
    cylinder(x, y, z + 0.01, z + 0.035, 0.045, mat("herbs", 0x4a8a3a, 0.9, noise=0.2), verts=14, bevel=0)
    bar((x - 0.02, y - 0.02, z), (x + 0.04, y + 0.04, z + 0.13), 0.012, mat("pestle", 0xb89870, 0.6))


def icon_axe(x, y, z, axis):
    wood = mat("handle", 0x9a6a3a, 0.6)
    steel = mat("steel", 0xb8bcc4, 0.3, metal=0.9, noise=0)
    bar((x, y, z - 0.1), (x, y, z + 0.1), 0.012, wood)
    if axis == "x":
        box(x + 0.0, y - 0.01, z + 0.03, x + 0.09, y + 0.01, z + 0.1, steel, bevel=0.004)
    else:
        box(x - 0.01, y + 0.0, z + 0.03, x + 0.01, y + 0.09, z + 0.1, steel, bevel=0.004)


def icon_saw(x, y, z, axis):
    steel = mat("steel", 0xb8bcc4, 0.3, metal=0.9, noise=0)
    wood = mat("handle", 0x9a6a3a, 0.6)
    if axis == "x":
        box(x - 0.1, y - 0.004, z - 0.03, x + 0.08, y + 0.004, z + 0.03, steel, bevel=0)
        box(x + 0.08, y - 0.012, z - 0.04, x + 0.12, y + 0.012, z + 0.05, wood, bevel=0.004)
    else:
        box(x - 0.004, y - 0.1, z - 0.03, x + 0.004, y + 0.08, z + 0.03, steel, bevel=0)
        box(x - 0.012, y + 0.08, z - 0.04, x + 0.012, y + 0.12, z + 0.05, wood, bevel=0.004)


def icon_pick(x, y, z, axis):
    steel = mat("steel", 0x9aa0a8, 0.35, metal=0.9, noise=0)
    wood = mat("handle", 0x9a6a3a, 0.6)
    bar((x, y, z - 0.1), (x, y, z + 0.06), 0.012, wood)
    if axis == "x":
        bar((x - 0.1, y, z + 0.03), (x + 0.1, y, z + 0.08), 0.013, steel)
    else:
        bar((x, y - 0.1, z + 0.03), (x, y + 0.1, z + 0.08), 0.013, steel)


def icon_wheat(x, y, z, axis):
    gold = mat("wheat", 0xe0b448, 0.7, noise=0.15, bump=0.3)
    for k in range(5):
        a = math.radians(-30 + k * 15)
        dx, dy = (math.sin(a) * 0.1, 0) if axis == "x" else (0, math.sin(a) * 0.1)
        bar((x, y, z - 0.08), (x + dx, y + dy, z + 0.07), 0.005, gold, verts=4)
        sphere(x + dx, y + dy, z + 0.08, 0.017, gold, sz=2.0)
    cylinder(x, y, z - 0.03, z - 0.01, 0.025, mat("twine", 0x8a5a2a, 0.8), verts=10, bevel=0)


def icon_antlers(x, y, z, axis):
    bone = mat("antler", 0xe4d6b8, 0.6, noise=0.1)
    wood = mat("plaque", 0x7a4a2a, 0.6)
    if axis == "x":
        box(x - 0.05, y - 0.012, z - 0.06, x + 0.05, y + 0.012, z + 0.02, wood, bevel=0.01)
    else:
        box(x - 0.012, y - 0.05, z - 0.06, x + 0.012, y + 0.05, z + 0.02, wood, bevel=0.01)
    for s in (-1, 1):
        def P(a, b):
            return (x + a * s, y, z + b) if axis == "x" else (x, y + a * s, z + b)
        bar(P(0.02, 0.02), P(0.08, 0.1), 0.008, bone)
        bar(P(0.08, 0.1), P(0.06, 0.16), 0.007, bone)
        bar(P(0.06, 0.07), P(0.11, 0.09), 0.006, bone)


def icon_apple(x, y, z, axis):
    red = mat("apple", 0xc8302a, 0.35, noise=0.15)
    sphere(x, y, z, 0.065, red, sz=0.9)
    bar((x, y, z + 0.05), (x + 0.01, y, z + 0.09), 0.006, mat("stem", 0x5a3a20, 0.8))
    leaf = sphere(x + 0.03, y + 0.0, z + 0.085, 0.025, mat("leaf", 0x4a9a3a, 0.6), sx=1.5, sy=0.4, sz=0.6)


def icon_sapling(x, y, z, axis):
    """Ёлочка в горшке - знак лесника"""
    pot = mat("pot", 0xb8643a, 0.7, noise=0.1)
    pine = mat("pine", 0x2f6a3a, 0.8, noise=0.3, bump=0.5)
    cylinder(x, y, z - 0.09, z - 0.03, 0.04, pot, verts=12)
    for k, (z0, r) in enumerate(((-0.03, 0.06), (0.02, 0.045), (0.06, 0.03))):
        cone(x, y, z + z0, z + z0 + 0.07, r, pine, verts=10)


def icon_barrel(x, y, z, axis):
    wood = mat("barrel", 0x8a5a32, 0.7, kind="planks", scale=60)
    o = cylinder(x, y, z - 0.055, z + 0.055, 0.05, wood, verts=12)
    o.rotation_euler = (0, math.radians(90), 0) if axis == "y" else (math.radians(90), 0, 0)
    o.scale = (1, 1, 1.4)


def icon_sack(x, y, z, axis):
    m = mat("sack", 0xe2d4b0, 0.95, noise=0.1, bump=0.3)
    sphere(x, y, z - 0.01, 0.065, m, sz=1.25)
    cylinder(x, y, z + 0.06, z + 0.08, 0.02, mat("twine", 0x8a5a2a, 0.8), verts=8, bevel=0)


def icon_wool(x, y, z, axis):
    m = mat("wool", 0xf2eee4, 0.95, noise=0.1, bump=0.8)
    for dx, dz in ((-0.035, 0), (0.035, 0), (0, 0.04)):
        if axis == "x":
            sphere(x + dx, y, z + dz, 0.045, m)
        else:
            sphere(x, y + dx, z + dz, 0.045, m)


def icon_scales(x, y, z, axis):
    gold = mat("brass", 0xc89a3a, 0.35, metal=0.8, noise=0)
    bar((x, y, z - 0.08), (x, y, z + 0.08), 0.008, gold)
    if axis == "x":
        bar((x - 0.09, y, z + 0.06), (x + 0.09, y, z + 0.06), 0.006, gold)
        for s in (-1, 1):
            cylinder(x + s * 0.09, y, z - 0.03, z - 0.01, 0.04, gold, verts=12, bevel=0)
    else:
        bar((x, y - 0.09, z + 0.06), (x, y + 0.09, z + 0.06), 0.006, gold)
        for s in (-1, 1):
            cylinder(x, y + s * 0.09, z - 0.03, z - 0.01, 0.04, gold, verts=12, bevel=0)


# ---------- двор ----------

def fence(points, h=0.16, post=0.018, m=None):
    """Забор по ломаной points [(x, y)]: столбики и две жерди"""
    m = m or mat("fence", 0x9a7448, 0.8, noise=0.2)
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 0.22))
        for i in range(n + 1):
            t = i / n
            x = x0 + (x1 - x0) * t; y = y0 + (y1 - y0) * t
            box(x - post, y - post, 0, x + post, y + post, h + 0.02, m, bevel=0.004)
        for zz in (h * 0.45, h * 0.9):
            bar((x0, y0, zz), (x1, y1, zz), 0.008, m, verts=4)


def tree(x, y, h=0.9, r=0.32, fruit=None, seed=1):
    """Лиственное дерево: ствол и крона из нескольких шаров; fruit - цвет плодов (яблоки)"""
    rr = rng(seed)
    if lib._snow:
        # зимой - голое дерево в стиле леса (forest_tree), масштаб под h/r
        global _TH, _SNOWK, _BARK
        H = h * 1.08; R = r * 1.35; zc = H * 0.62
        _TH = 1.0; _SNOWK = 1.35; _BARK = (0x5e4f42, 0x7a6b5e)
        before = set(bpy.context.scene.objects)
        _trunk(zc * 0.97, 0.05, 0.034, _bark("bark_w%x" % _BARK[0], _BARK[0]))
        for o in set(bpy.context.scene.objects) - before:
            o.location.x += x; o.location.y += y
        _winter_crown(H, R, rr, zc, x, y)
        _TH = 1.0; _SNOWK = 1.0; _BARK = (0x46382c, 0x6a5b4e)
        return
    trunk = mat("trunk", 0x6a4a2c, 0.9, noise=0.2, bump=0.3)
    cylinder(x, y, 0, h * 0.55, r * 0.14, trunk, verts=8)
    leaves = mat("leaves", 0x5a9a42, 0.8, noise=0.35, bump=0.8)
    for k in range(6):
        sphere(x + rr.uniform(-r, r) * 0.55, y + rr.uniform(-r, r) * 0.55, h * 0.62 + rr.uniform(0, r * 0.7), r * rr.uniform(0.55, 0.75), leaves)
    if fruit:
        fm = mat("fruit%x" % fruit, fruit, 0.35, noise=0.1)
        for k in range(10):
            a = rr.uniform(0, 6.28); e = rr.uniform(-0.3, 0.9)
            sphere(x + math.cos(a) * r * 0.85, y + math.sin(a) * r * 0.85, h * 0.62 + e * r * 0.6, 0.035, fm)


def bush(x, y, r=0.1, flowers=None, seed=1):
    rr = rng(seed)
    m = mat("bush", 0x4f8a3a, 0.9, noise=0.3, bump=0.6)
    for k in range(3):
        sphere(x + rr.uniform(-r, r) * 0.5, y + rr.uniform(-r, r) * 0.5, r * 0.6, r * rr.uniform(0.6, 0.85), m, sz=0.85)
    if flowers:
        fm = mat("flower%x" % flowers, flowers, 0.6, noise=0.1)
        for k in range(6):
            sphere(x + rr.uniform(-r, r) * 0.8, y + rr.uniform(-r, r) * 0.8, r * 1.0 + rr.uniform(0, 0.03), 0.018, fm)


def sheep(x, y, rz=0.0, seed=1):
    wool = mat("sheepwool", 0xf4f0e6, 0.95, noise=0.1, bump=0.9)
    dark = mat("sheephead", 0x2e2a28, 0.7, noise=0)
    c, s = math.cos(rz), math.sin(rz)
    def P(a, b):
        return (x + a * c - b * s, y + a * s + b * c)
    bx, by = P(0, 0)
    sphere(bx, by, 0.1, 0.08, wool, sx=1.3 if abs(c) > 0.7 else 1.0, sy=1.0 if abs(c) > 0.7 else 1.3)
    hx, hy = P(0.11, 0)
    sphere(hx, hy, 0.12, 0.035, dark, sz=1.2)
    for a, b in ((0.05, 0.04), (0.05, -0.04), (-0.05, 0.04), (-0.05, -0.04)):
        lx, ly = P(a, b)
        cylinder(lx, ly, 0, 0.06, 0.011, dark, verts=6, bevel=0)


def boat(x, y, along_x=True, length=0.6):
    wood = mat("boat", 0x8a5a32, 0.7, kind="planks", scale=40)
    w = length * 0.32
    L = length / 2
    verts = []
    # корпус: две дуги бортов и дно
    for i in range(7):
        t = i / 6
        a = -L + t * 2 * L
        k = math.sin(math.pi * t) ** 0.6
        verts += [(a, -w / 2 * k, 0.1), (a, w / 2 * k, 0.1), (a, 0, 0.02 + 0.03 * (1 - k))]
    faces = []
    for i in range(6):
        b = i * 3; n = b + 3
        faces += [(b, n, n + 2, b + 2), (b + 2, n + 2, n + 1, b + 1)]
    if not along_x:
        verts = [(v[1], v[0], v[2]) for v in verts]
        faces = [tuple(reversed(f)) for f in faces]
    o = mesh_from([(x + v[0], y + v[1], v[2]) for v in verts], faces, wood, name="boat")
    sol = o.modifiers.new("t", 'SOLIDIFY')
    sol.thickness = 0.015
    if along_x:
        box(x - 0.03, y - w / 2.2, 0.06, x + 0.03, y + w / 2.2, 0.075, wood, bevel=0)
    else:
        box(x - w / 2.2, y - 0.03, 0.06, x + w / 2.2, y + 0.03, 0.075, wood, bevel=0)


def awning(x0, y0, x1, y1, z_back, z_front, along_x, colors=(0xc83a30, 0xf4ecd8), stripes=6):
    """Полосатый навес: полосы по очереди двух цветов, наклон от z_back (сзади) к z_front (спереди -x или -y)"""
    m = [mat("awn%x" % c, c, 0.85, noise=0.1) for c in colors]
    for i in range(stripes):
        t0 = i / stripes; t1 = (i + 1) / stripes
        if along_x:
            a0 = x0 + (x1 - x0) * t0; a1 = x0 + (x1 - x0) * t1
            verts = [(a0, y1, z_back), (a1, y1, z_back), (a1, y0, z_front), (a0, y0, z_front)]
        else:
            a0 = y0 + (y1 - y0) * t0; a1 = y0 + (y1 - y0) * t1
            verts = [(x1, a0, z_back), (x1, a1, z_back), (x0, a1, z_front), (x0, a0, z_front)]
        o = mesh_from(verts, [(0, 1, 2, 3)], m[i % 2], name="awn")
        sol = o.modifiers.new("t", 'SOLIDIFY')
        sol.thickness = 0.012
    # фестон по переднему краю
    for i in range(stripes):
        t = (i + 0.5) / stripes
        if along_x:
            px = x0 + (x1 - x0) * t
            sphere(px, y0, z_front - 0.01, (x1 - x0) / stripes / 2, m[i % 2], sy=0.15, sz=0.6)
        else:
            py = y0 + (y1 - y0) * t
            sphere(x0, py, z_front - 0.01, (y1 - y0) / stripes / 2, m[i % 2], sx=0.15, sz=0.6)


def cart(x, y, along_x=True, load=None):
    wood = mat("cart", 0x9a6a3a, 0.7, kind="planks", scale=40)
    L, W = 0.32, 0.18
    if along_x:
        box(x - L / 2, y - W / 2, 0.08, x + L / 2, y + W / 2, 0.11, wood, bevel=0.006)
        box(x - L / 2, y - W / 2, 0.11, x + L / 2, y - W / 2 + 0.015, 0.17, wood, bevel=0.004)
        box(x - L / 2, y + W / 2 - 0.015, 0.11, x + L / 2, y + W / 2, 0.17, wood, bevel=0.004)
        for s in (-1, 1):
            o = cylinder(x, y + s * (W / 2 + 0.02), 0.0, 0.01, 0.075, wood, verts=12)
            o.rotation_euler = (math.radians(90), 0, 0); o.location = (x, y + s * (W / 2 + 0.02), 0.075)
        bar((x - L / 2, y - 0.04, 0.09), (x - L / 2 - 0.22, y - 0.04, 0.04), 0.008, wood)
        bar((x - L / 2, y + 0.04, 0.09), (x - L / 2 - 0.22, y + 0.04, 0.04), 0.008, wood)
    else:
        box(x - W / 2, y - L / 2, 0.08, x + W / 2, y + L / 2, 0.11, wood, bevel=0.006)
        box(x - W / 2, y - L / 2, 0.11, x - W / 2 + 0.015, y + L / 2, 0.17, wood, bevel=0.004)
        box(x + W / 2 - 0.015, y - L / 2, 0.11, x + W / 2, y + L / 2, 0.17, wood, bevel=0.004)
        for s in (-1, 1):
            o = cylinder(x + s * (W / 2 + 0.02), y, 0.0, 0.01, 0.075, wood, verts=12)
            o.rotation_euler = (0, math.radians(90), 0); o.location = (x + s * (W / 2 + 0.02), y, 0.075)
        bar((x - 0.04, y - L / 2, 0.09), (x - 0.04, y - L / 2 - 0.22, 0.04), 0.008, wood)
        bar((x + 0.04, y - L / 2, 0.09), (x + 0.04, y - L / 2 - 0.22, 0.04), 0.008, wood)
    if load:
        load(x, y, 0.12)


def figure(x, y, z, h, m, sword=True):
    """Фигура героя для статуи: тело, голова, плащ, поднятый меч"""
    s = h / 1.0
    cylinder(x, y, z, z + 0.45 * s, 0.09 * s, m, verts=12)                          # ноги и плащ
    sphere(x, y, z + 0.55 * s, 0.12 * s, m, sz=1.2)                                  # торс
    sphere(x, y, z + 0.78 * s, 0.07 * s, m)                                          # голова
    bar((x - 0.1 * s, y, z + 0.62 * s), (x - 0.16 * s, y, z + 0.4 * s), 0.03 * s, m)
    bar((x + 0.1 * s, y, z + 0.62 * s), (x + 0.12 * s, y - 0.04 * s, z + 0.85 * s), 0.03 * s, m)
    if sword:
        bar((x + 0.12 * s, y - 0.04 * s, z + 0.85 * s), (x + 0.14 * s, y - 0.06 * s, z + 1.15 * s), 0.014 * s, m)
        bar((x + 0.06 * s, y - 0.04 * s, z + 0.86 * s), (x + 0.18 * s, y - 0.04 * s, z + 0.86 * s), 0.012 * s, m)


def lantern(x, y, z):
    m = iron()
    box(x - 0.025, y - 0.025, z, x + 0.025, y + 0.025, z + 0.06, mat("lanternglass", 0xffd890, 0.3, noise=0), bevel=0)
    box(x - 0.03, y - 0.03, z + 0.06, x + 0.03, y + 0.03, z + 0.07, m, bevel=0)
    box(x - 0.03, y - 0.03, z - 0.008, x + 0.03, y + 0.03, z, m, bevel=0)


def flower_box(face, u, z, w):
    """Ящик с цветами под окном"""
    axis, c, out = face
    wood = mat("fbox", 0x7a4a2a, 0.7)
    leaf = mat("leaf", 0x4a9a3a, 0.7, noise=0.2, bump=0.5)
    red = mat("fl_red", 0xd8404a, 0.6)
    if axis == "x":
        box(c + out * 0.06, u - w / 2, z - 0.04, c, u + w / 2, z, wood, bevel=0.004)
        for i in range(4):
            sphere(c + out * 0.035, u - w / 2 + w * (i + 0.5) / 4, z + 0.01, 0.022, leaf if i % 2 else red)
    else:
        box(u - w / 2, c + out * 0.06, z - 0.04, u + w / 2, c, z, wood, bevel=0.004)
        for i in range(4):
            sphere(u - w / 2 + w * (i + 0.5) / 4, c + out * 0.035, z + 0.01, 0.022, leaf if i % 2 else red)


def stones_pile(x, y, n=5, seed=3, size=0.05):
    rr = rng(seed)
    m = mat("blocks", 0xc4bcae, 0.85, noise=0.2, bump=0.3)
    for i in range(n):
        bx = x + rr.uniform(-0.12, 0.12); by = y + rr.uniform(-0.12, 0.12)
        s = size * rr.uniform(0.8, 1.3)
        z0 = 0 if i < n - 2 else size * 1.1
        box(bx - s, by - s * 0.8, z0, bx + s, by + s * 0.8, z0 + s * 1.2, m, bevel=0.008)


def hay(x, y, r=0.16):
    m = mat("hay", 0xdcb050, 0.95, noise=0.3, bump=1.0)
    sphere(x, y, 0.0, r, m, sz=1.25)


# ---------- природа: деревья, скалы, кусты карты (render.py nature) ----------

from lib import _obj, _mats, _principled
from mathutils import Matrix


def tbar(p0, p1, r0, r1, m, verts=6, name="branch"):
    """Сужающийся стержень от p0 (радиус r0) до p1 (радиус r1)"""
    a = Vector(p0); b = Vector(p1)
    d = b - a
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r0, radius2=r1, depth=d.length, location=(a + b) / 2)
    o = bpy.context.object
    o.name = name
    o.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    o.data.materials.append(m)
    return o


def lump(x, y, z, r, m, seed, amp=0.25, freq=1.7, subdiv=3, sx=1.0, sy=1.0, sz=1.0, flat=False, jitter=0.0, name="lump"):
    """Неровный шар: икосфера со смещением вершин шумом (крона, глыба, куст)"""
    from mathutils import noise
    rr = rng(seed * 7919 + 13)
    off = Vector((rr.uniform(-50, 50), rr.uniform(-50, 50), rr.uniform(-50, 50)))
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=subdiv, radius=r)
    for v in bm.verts:
        n = noise.noise(v.co * (freq / max(r, 0.01)) + off)
        k = 1.0 + amp * n + (rr.uniform(-jitter, jitter) if jitter else 0.0)
        v.co.x *= k * sx; v.co.y *= k * sy; v.co.z *= k * sz
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = _obj(name, me)
    o.location = (x, y, z)
    o.data.materials.append(m)
    for p in me.polygons:
        p.use_smooth = not flat
    return o


def _leaf_mats(cols, rough=0.85):
    return [mat("lf%06x" % c, c, rough, noise=0.3, bump=0.5, scale=8) for c in cols]


TREE_TRUNK = 0x6b4a2e

# лиственные: полная высота, радиус кроны, число шаров, цвета листвы (тёмный, средний, светлый)
DECID = [
    (1.55, 0.58, 15, (0x557f3a, 0x5e8a3e, 0x6a9444)),
    (1.75, 0.64, 19, (0x5c8638, 0x6a9444, 0x6f9a48)),
    (1.40, 0.56, 12, (0x4f7a38, 0x5a8640, 0x66903f)),
    (1.90, 0.60, 16, (0x5a8a3c, 0x66903f, 0x73a04a)),
]
# хвойные: высота, радиус нижнего яруса, ярусов
CONIFER = [
    (2.15, 0.62, 4),
    (2.45, 0.68, 5),
]


def _trunk(h_top, r0, r1, m=None):
    m = m or mat("trunknat", TREE_TRUNK, 0.9, noise=0.25, bump=0.4, scale=14)
    tbar((0, 0, -0.02), (0, 0, h_top), r0, r1, m, verts=8, name="trunk")
    tbar((0, 0, -0.02), (0, 0, 0.16), r0 * 1.45, r0, m, verts=8, name="flare")
    return m


def _bark(name, hex_):
    """Материал без снега по нормали (тонкие ветви в зимнем режиме не белеют)"""
    old = lib._snow
    lib._snow = False
    m = mat(name, hex_, 0.9, noise=0.2, bump=0.3, scale=14)
    lib._snow = old
    return m


_TH = 1.0   # масштаб толщины ветвей (мелкие деревья у зданий)
_SNOWK = 1.0   # во сколько раз толще полоски снега
_BARK = (0x46382c, 0x6a5b4e)   # кора каркаса и прутиков


def _grow(p, d, length, r0, level, rr, segs, crown, forks):
    """Скелет ветви: сегменты (p0, p1, r0, r1, уровень); 4 уровня, к концам тоньше; крона ограничена эллипсоидом crown"""
    R0 = (0.036, 0.024, 0.017, 0.012)
    R1 = (0.025, 0.016, 0.012, 0.009)
    r0 = min(r0, R0[level] * _TH)
    r1 = min(r0 * 0.7, R1[level] * _TH)
    pieces = 2 if level <= 1 else 1
    cur = Vector(p)
    cd = Vector(d)
    for i in range(pieces):
        seg_len = length / pieces
        nd = (cd + Vector((rr.uniform(-0.12, 0.12), rr.uniform(-0.12, 0.12), rr.uniform(-0.04, 0.08)))).normalized()
        q = cur + nd * seg_len
        ra = r0 + (r1 - r0) * i / pieces
        rb = r0 + (r1 - r0) * (i + 1) / pieces
        segs.append((cur, q, ra, rb, level))
        cur, cd = q, nd
        if level < 3 and i < pieces - 1:
            _children(cur, cd, length, rb, level, rr, segs, crown, forks, 1)
    if level < 3:
        _children(cur, cd, length, r1, level, rr, segs, crown, forks, 2 if level < 1 else rr.choice((2, 2, 3) if level == 1 else (2, 3, 3)))
    if level <= 2:
        forks.append((cur, r1))


def _inside(q, crown):
    cx, cy, cz, ax, az = crown
    return ((q.x - cx) / ax) ** 2 + ((q.y - cy) / ax) ** 2 + ((q.z - cz) / az) ** 2 <= 1.0


def _children(p, d, length, r, level, rr, segs, crown, forks, n):
    made = 0
    for _ in range(n * 3):
        if made >= n:
            break
        ang = rr.uniform(0.45, 0.95)
        az = rr.uniform(0, 2 * math.pi)
        axis = d.cross(Vector((math.cos(az), math.sin(az), 0.2)))
        if axis.length < 1e-4:
            axis = Vector((1, 0, 0))
        nd = Matrix.Rotation(ang, 3, axis.normalized()) @ d
        nd.z += 0.18
        nd.normalize()
        ln = length * rr.uniform(0.62, 0.8) * (0.78 if level >= 2 else 1.0)
        if not _inside(p + nd * ln, crown):
            ln *= 0.55
            if not _inside(p + nd * ln, crown):
                continue
        _grow(p, nd, ln, r * 0.8, level + 1, rr, segs, crown, forks)
        made += 1


def _prism(bm, a, b, ra, rb, sides, side_v, up_v):
    """Сужающийся стержень из sides граней в bmesh"""
    ring_a = []; ring_b = []
    for k in range(sides):
        t = k / sides * 2 * math.pi
        o = side_v * math.cos(t) + up_v * math.sin(t)
        ring_a.append(bm.verts.new(a + o * ra))
        ring_b.append(bm.verts.new(b + o * rb))
    for k in range(sides):
        k2 = (k + 1) % sides
        bm.faces.new((ring_a[k], ring_a[k2], ring_b[k2], ring_b[k]))


def _snow_strip(bm, a, b, ra, rb):
    """Тонкая полоска снега вдоль верха ветви: плоский клин"""
    d = b - a
    side = d.cross(Vector((0, 0, 1)))
    if side.length < 1e-5:
        return
    side.normalize()
    ext = d.normalized() * 0.012
    pts = []
    for p, r in ((a - ext, ra), (b + ext, rb)):
        z0 = p + Vector((0, 0, r * 0.45))
        w = r * 0.95 * _SNOWK
        t = r * 0.6 * _SNOWK
        pts.append([bm.verts.new(z0 - side * w), bm.verts.new(z0 + side * w),
                    bm.verts.new(z0 + side * w * 0.55 + Vector((0, 0, t))), bm.verts.new(z0 - side * w * 0.55 + Vector((0, 0, t)))])
    A, B = pts
    for k in range(4):
        k2 = (k + 1) % 4
        bm.faces.new((A[k], A[k2], B[k2], B[k]))
    bm.faces.new(A[::-1])
    bm.faces.new(B)


def _mesh_obj(bm, m, name):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = _obj(name, me)
    o.data.materials.append(m)
    for pl in me.polygons:
        pl.use_smooth = True
    return o


def _winter_crown(H, R, rr, zc, ox=0.0, oy=0.0):
    """Голая крона: 4-6 скелетных ветвей от ствола, 4 уровня; снег полосками по верху толстых ветвей"""
    before = set(bpy.context.scene.objects)
    bark = _bark("bark_w%x" % _BARK[0], _BARK[0])
    bark2 = _bark("bark_w2%x" % _BARK[1], _BARK[1])
    snow = snow_mat()
    crown = (0.0, 0.0, H * 0.72, R * 1.22, H * 0.33)
    segs = []; forks = []
    n0 = rr.choice((4, 5, 5, 6))
    for k in range(n0):
        a = (k + rr.uniform(-0.2, 0.2)) / n0 * 2 * math.pi
        zs = zc * rr.uniform(0.6, 0.95)
        d = Vector((math.cos(a) * 1.0, math.sin(a) * 1.0, 0.62)).normalized()
        _grow(Vector((0, 0, zs)), d, H * 0.3, 0.032, 0, rr, segs, crown, forks)
    d = Vector((rr.uniform(-0.2, 0.2), rr.uniform(-0.2, 0.2), 1)).normalized()
    _grow(Vector((0, 0, zc * 0.97)), d, H * 0.24, 0.03, 0, rr, segs, crown, forks)
    bms = [bmesh.new(), bmesh.new()]
    bsn = bmesh.new()
    for a, b, ra, rb, lv in segs:
        dd = (b - a).normalized()
        side = dd.cross(Vector((0, 0, 1)))
        if side.length < 1e-4:
            side = Vector((1, 0, 0))
        side.normalize()
        up = side.cross(dd).normalized()
        _prism(bms[lv // 2], a, b, ra, rb, 5 if lv < 2 else 3, side, up)
        if lv >= 2 and rr.random() < 0.3:
            m_ = (a + b) / 2
            res = bmesh.ops.create_icosphere(bsn, subdivisions=1, radius=0.013)
            for vv in res['verts']:
                vv.co = Vector((vv.co.x * 1.2, vv.co.y * 1.2, vv.co.z * 0.4)) + m_ + Vector((0, 0, rb * 1.1))
        if lv <= 2 and abs(dd.z) < 0.88 and (rr.random() < (0.8 if lv <= 1 else 0.55)):
            _snow_strip(bsn, a, b, ra, rb)
    for c, r in forks:
        if r > 0.012:
            res = bmesh.ops.create_icosphere(bsn, subdivisions=1, radius=r * 1.5)
            for vv in res["verts"]:
                vv.co = Vector((vv.co.x, vv.co.y, vv.co.z * 0.5)) + c + Vector((0, 0, r * 0.9))
    _mesh_obj(bms[0], bark, "bark0")
    _mesh_obj(bms[1], bark2, "bark1")
    _mesh_obj(bsn, snow, "snowstrips")
    for o in set(bpy.context.scene.objects) - before:
        o.location.x += ox
        o.location.y += oy


def _crown_noise(co, off, freq):
    from mathutils import noise
    return 0.65 * noise.noise(co * freq + off) + 0.35 * noise.noise(co * freq * 2.7 + off * 1.7)


def _cluster(x, y, z, r, m, seed, sz=0.85):
    """Кучка листвы: икосфера, смещённая двумя октавами шума (рваный край)"""
    rr = rng(seed * 4423 + 7)
    off = Vector((rr.uniform(-50, 50), rr.uniform(-50, 50), rr.uniform(-50, 50)))
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=3, radius=r)
    for v in bm.verts:
        n = _crown_noise(v.co / max(r, 0.01), off, 3.2)
        k = 1.0 + 0.46 * n + rr.uniform(-0.06, 0.06)
        v.co.x *= k; v.co.y *= k; v.co.z *= k * sz
    me = bpy.data.meshes.new("leafcl")
    bm.to_mesh(me)
    bm.free()
    o = _obj("leafcl", me)
    o.location = (x, y, z)
    o.data.materials.append(m)
    for pl in me.polygons:
        pl.use_smooth = True
    return o


def _lerp_hex(a, b, t):
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    c = [int(round(ca[i] + (cb[i] - ca[i]) * t)) for i in range(3)]
    return (c[0] << 16) | (c[1] << 8) | c[2]


def _summer_crown(v, H, R, ncl, cols, rr, zc):
    """Крона из множества кучек листвы разного размера; ниже и глубже - темнее, сверху - светлее"""
    dark, mid, light = cols
    deep = _lerp_hex(dark, 0x2e4a26, 0.2)
    palette = []
    for i in range(7):
        t = i / 6.0
        palette.append(_lerp_hex(deep, dark, t * 2) if t < 0.5 else _lerp_hex(dark, light, (t - 0.5) * 1.5))
    mats = [mat("lc%06x" % c, c, 0.85, noise=0.25, bump=1.5, scale=14) for c in palette]
    cz = zc + R * 0.15
    ex, ez = R * 0.92, R * 0.72
    # тёмная сердцевина: без просветов насквозь
    core = mats[1]
    sphere(0, 0, cz - R * 0.05, R * 0.5, core, sz=0.95)
    for k in range(ncl):
        for _ in range(20):
            px, py, pz = rr.uniform(-1, 1), rr.uniform(-1, 1), rr.uniform(-1, 1)
            ln = math.sqrt(px * px + py * py + pz * pz)
            if 0.05 < ln <= 1.0:
                break
        sh = 0.55 + 0.45 * ln
        px, py, pz = px / ln * sh, py / ln * sh, pz / ln * sh
        x, y, z = px * ex, py * ex, cz + pz * ez
        zr = (z - (cz - ez)) / (2 * ez)               # 0 снизу .. 1 сверху
        r = R * (0.42 - 0.17 * max(0.0, zr - 0.3) + rr.uniform(-0.07, 0.09)) * (0.8 + 0.3 * (1 - ln))
        r = max(r, R * 0.17)
        t = 0.5 + 0.42 * (zr - 0.5) - 0.22 * px + 0.1 * py + 0.3 * (ln - 0.5) + rr.uniform(-0.07, 0.07)
        mi = int(round(max(0.0, min(1.0, t)) * 6))
        _cluster(x, y, z, r, mats[mi], v * 131 + k, 0.85)
    # светлые верхушки: несколько мелких кучек на макушке
    for k in range(3 + v % 2):
        a = rr.uniform(0, 2 * math.pi); d = rr.uniform(0, 0.3) * R
        _cluster(math.cos(a) * d, math.sin(a) * d, cz + ez * rr.uniform(0.75, 0.95), R * rr.uniform(0.2, 0.28), mats[min(6, 4 + k % 2)], v * 211 + k, 0.8)


def forest_tree(v, winter):
    """Дерево леса tree_<v>: 0-3 лиственные (зимой - голые ветви со снегом), 4-5 хвойные"""
    if v < 4:
        H, R, nb, cols = DECID[v]
        rr = rng(100 + v)
        zc = H * 0.62     # центр кроны
        if winter:
            _trunk(zc * 0.97, 0.058, 0.034, _bark("bark_w", 0x46382c))
            _winter_crown(H, R, rr, zc)
        else:
            _trunk(zc, 0.065, 0.04)
            _summer_crown(v, H, R, nb, cols, rr, zc)
    else:
        H, R, n = CONIFER[v - 4]
        rr = rng(200 + v)
        _trunk(H * 0.3, 0.055, 0.04)
        tiers = (mat("fir_a", 0x3f6b3a, 0.9, noise=0.25, bump=0.4, scale=10),
                 mat("fir_b", 0x45733d, 0.9, noise=0.25, bump=0.4, scale=10),
                 mat("fir_c", 0x4a7a40, 0.9, noise=0.25, bump=0.4, scale=10))
        z0 = H * 0.17
        span = H - z0
        for i in range(n):
            t = i / n
            rad = R * (1.0 - t * 0.74)
            zb = z0 + span * t * 0.8
            hh = min(span * 0.36 * (1.0 - 0.1 * i / n) + 0.08, H - zb)
            ring = 14
            vs = [(0, 0, zb + hh)]
            for k in range(ring):
                a = (k + rr.uniform(-0.2, 0.2)) / ring * 2 * math.pi
                rk = rad * (1.0 if k % 2 == 0 else 0.78) * rr.uniform(0.92, 1.05)
                vs.append((math.cos(a) * rk, math.sin(a) * rk, zb + (0.0 if k % 2 == 0 else 0.07)))
            vs.append((0, 0, zb + 0.05))
            fs = []
            for k in range(ring):
                fs.append((0, 1 + k, 1 + (k + 1) % ring))
                fs.append((ring + 1, 1 + (k + 1) % ring, 1 + k))
            mesh_from(vs, fs, tiers[min(2, i * 3 // n)], name="tier", smooth=False)


def rock_model(v):
    """Скала rock_<v>: 2-3 глыбы тёплого серого с мхом сверху"""
    greys = (0x857f74, 0x8a8478, 0x797469)
    m = _rock_mat(greys[v % 3])
    if v == 0:
        parts = [(0.0, 0.0, 0.40, 1.15, 0.95, 0.75), (0.42, -0.30, 0.24, 1.0, 1.1, 0.7)]
    elif v == 1:
        parts = [(-0.05, 0.05, 0.36, 1.2, 0.9, 0.8), (0.38, 0.18, 0.26, 1.0, 1.0, 0.75), (0.15, -0.38, 0.2, 1.1, 0.9, 0.7)]
    else:
        parts = [(0.0, 0.0, 0.34, 1.0, 1.25, 0.8), (-0.30, 0.34, 0.22, 1.1, 1.0, 0.7)]
    for k, (x, y, r, sx, sy, sz) in enumerate(parts):
        lump(x, y, r * sz * 0.62, r, m, 300 + v * 5 + k, amp=0.34, freq=1.5, subdiv=2, sx=sx, sy=sy, sz=sz, flat=True, jitter=0.05, name="boulder")


def _rock_mat(hex_):
    """Серый камень с зерном и мхом на верхних гранях"""
    key = ("rockmoss", hex_)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new("rock%x" % hex_)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    base = srgb(hex_)
    bsdf = _principled(nt, base, 0.85)
    tc = nt.nodes.new("ShaderNodeTexCoord")
    nz = nt.nodes.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 7.0
    nz.inputs["Detail"].default_value = 5.0
    nt.links.new(tc.outputs["Object"], nz.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (0.78, 0.76, 0.74, 1)
    ramp.color_ramp.elements[1].color = (1.18, 1.15, 1.1, 1)
    nt.links.new(nz.outputs["Fac"], ramp.inputs["Fac"])
    grain = nt.nodes.new("ShaderNodeMix")
    grain.data_type = 'RGBA'; grain.blend_type = 'MULTIPLY'
    grain.inputs["Factor"].default_value = 0.8
    grain.inputs[6].default_value = (*base, 1)
    nt.links.new(ramp.outputs["Color"], grain.inputs[7])
    # мох: верхние грани, рваный край шумом
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Normal"], sep.inputs[0])
    nz2 = nt.nodes.new("ShaderNodeTexNoise")
    nz2.inputs["Scale"].default_value = 5.0
    nt.links.new(tc.outputs["Object"], nz2.inputs["Vector"])
    add = nt.nodes.new("ShaderNodeMath"); add.operation = 'ADD'
    nt.links.new(sep.outputs["Z"], add.inputs[0])
    sub = nt.nodes.new("ShaderNodeMath"); sub.operation = 'MULTIPLY_ADD'
    sub.inputs[1].default_value = 0.8; sub.inputs[2].default_value = -0.4
    nt.links.new(nz2.outputs["Fac"], sub.inputs[0])
    nt.links.new(sub.outputs[0], add.inputs[1])
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.inputs["From Min"].default_value = 0.68; mr.inputs["From Max"].default_value = 0.82
    nt.links.new(add.outputs[0], mr.inputs["Value"])
    moss = nt.nodes.new("ShaderNodeMix")
    moss.data_type = 'RGBA'
    nt.links.new(mr.outputs["Result"], moss.inputs["Factor"])
    nt.links.new(grain.outputs[2], moss.inputs[6])
    moss.inputs[7].default_value = (*srgb(0x56693a), 1)
    nt.links.new(moss.outputs[2], bsdf.inputs["Base Color"])
    bn = nt.nodes.new("ShaderNodeBump")
    bn.inputs["Strength"].default_value = 0.5
    bn.inputs["Distance"].default_value = 0.02
    nt.links.new(nz.outputs["Fac"], bn.inputs["Height"])
    nt.links.new(bn.outputs["Normal"], bsdf.inputs["Normal"])
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    _mats[key] = m
    return m


def forest_bush(v, winter):
    """Куст prop_bush_<v> / prop_bushsnow_<v>: 3 шара оливковой листвы; v == 2 - с ягодами (летом); зимой - тёмнее и со снегом"""
    rr = rng(400 + v)
    cols = ((0x4f7a38, 0x5e8a3e), (0x557f3a, 0x66903f), (0x4f7a38, 0x5a8640))[v]
    lm = _leaf_mats((0x4a6a38, 0x55703e) if winter else cols)
    spots = ((-0.13, 0.04, 0.17, 0), (0.12, -0.05, 0.19, 1), (0.0, 0.14, 0.15, 0))
    for k, (x, y, r, c) in enumerate(spots):
        lump(x + rr.uniform(-0.03, 0.03), y + rr.uniform(-0.03, 0.03), r * 0.72, r, lm[c], 400 + v * 4 + k, amp=0.22, freq=1.8, subdiv=3, sz=0.82, name="shrub")
    if v == 2 and not winter:
        red = mat("berry", 0xc23a30, 0.4, noise=0.1)
        for k in range(7):
            a = rr.uniform(0, 6.28); e = rr.uniform(0.1, 0.17)
            sphere(math.cos(a) * 0.17, math.sin(a) * 0.17, 0.1 + e, 0.022, red)
