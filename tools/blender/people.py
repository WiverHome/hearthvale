"""
Жители: носильщик (светлая рубаха), рабочий (синяя, кепка), строитель (оранжевая, шляпа). Рост ~0.55 клетки
(дверь ~0.45 - фигурки чуть крупнее жизни, чтобы читались на карте). Модель смотрит вдоль +x, стоит в (0, 0);
render.py поворачивает её на 4 направления экрана. Кадры ходьбы: фаза шага frame * 90 градусов (0 - стоя).
"""
import math
from lib import *
from props import bar

SHIRTS = (0xe8dcc0, 0x3a64b0, 0xd8822a, 0x2e2a2a)
HAIR = (0x5a3a22, 0x3a2a1c, 0xc8a040, 0x1c1814)

HIP = 0.2       # высота бедра
LEG = 0.19      # длина ноги
SHOULDER = 0.37
ARM = 0.15


def _limb(top, angle, length, r, m, sideways=0.0):
    """Конечность от точки top, отклонённая вперёд (+x) на angle; sideways - отвод в сторону (по y)"""
    x, y, z = top
    end = (x + math.sin(angle) * length, y + sideways, z - math.cos(angle) * length)
    bar(top, end, r, m, verts=8)
    sphere(end[0], end[1], end[2], r * 1.05, m)
    return end


def person(job, frame, carry):
    shirt = mat(f"shirt{job}", SHIRTS[job], 0.85, noise=0.12)
    pants = mat("pants", 0x4a3a2c, 0.8, noise=0.1)
    boots = mat("boots", 0x2e2018, 0.6, noise=0)
    skin = mat("skin", 0xf0c8a0, 0.6, noise=0.04)
    hair = mat(f"hair{job}", HAIR[job], 0.8, noise=0.1)
    belt = mat("belt", 0x5a4030, 0.6, noise=0)
    ph = frame * math.pi / 2
    swing = math.sin(ph) * math.radians(28)
    bob = -abs(math.cos(ph)) * 0.008 + 0.008     # на середине шага чуть выше
    # ноги
    for side, k in ((0.035, 1), (-0.035, -1)):
        foot = _limb((0, side, HIP + bob), swing * k, LEG, 0.024, pants)
        sphere(foot[0] + 0.015, foot[1], max(0.018, foot[2]), 0.026, boots, sx=1.5, sz=0.75)
    # рубаха до бёдер (усечённый конус) и плечи
    bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=0.078, radius2=0.06, depth=SHOULDER - HIP + 0.03,
                                    location=(0, 0, (HIP - 0.03 + SHOULDER) / 2 + bob))
    o = bpy.context.object
    o.data.materials.append(shirt)
    bpy.ops.object.shade_smooth()
    sphere(0, 0, SHOULDER - 0.005 + bob, 0.064, shirt, sx=0.85, sz=0.55)
    cylinder(0, 0, HIP + 0.035 + bob, HIP + 0.05 + bob, 0.074, belt, verts=16, bevel=0)
    # руки: в ходьбе - против ног, с грузом - вверх над головой
    for side, k in ((0.078, -1), (-0.078, 1)):
        top = (0, side, SHOULDER - 0.01 + bob)
        if carry:
            end = (0.02, side * 0.8, 0.6 + bob)
            bar(top, end, 0.019, shirt, verts=8)
            sphere(end[0], end[1], end[2], 0.02, skin)
        else:
            _limb(top, swing * k * 0.8, ARM, 0.019, shirt, sideways=side * 0.15)
    # голова
    hz = 0.455 + bob
    cylinder(0, 0, SHOULDER + bob, hz - 0.03, 0.022, skin, verts=8, bevel=0)
    sphere(0, 0, hz, 0.058, skin, sz=1.05)
    sphere(-0.012, 0, hz + 0.014, 0.058, hair, sz=0.92)
    if job == 1:
        # кепка
        cap = mat("cap", 0x2e3a5a, 0.8, noise=0.05)
        sphere(-0.004, 0, hz + 0.03, 0.06, cap, sz=0.6)
        cylinder(0.04, 0, hz + 0.022, hz + 0.03, 0.035, cap, verts=12, bevel=0)
    elif job == 2:
        # соломенная шляпа строителя
        straw = mat("straw", 0xc89a48, 0.9, noise=0.2)
        cylinder(0, 0, hz + 0.028, hz + 0.036, 0.095, straw, verts=20, bevel=0)
        cylinder(0, 0, hz + 0.036, hz + 0.075, 0.055, straw, verts=16)
    elif job == 3:
        # разбойник: тёмный капюшон и плащ за спиной, дубинка в руке
        cloak = mat("cloak", 0x3a3434, 0.9, noise=0.15)
        sphere(-0.01, 0, hz + 0.01, 0.07, cloak, sz=1.05)
        cone(-0.07, 0, SHOULDER - 0.2, SHOULDER + 0.02, 0.1, cloak, verts=12).rotation_euler = (0, math.radians(-12), 0)
        sphere(0.03, 0, hz - 0.005, 0.05, mat("bandit_face", 0x2a2020, 0.8, noise=0), sx=0.7)
        club = mat("club", 0x6a4a2c, 0.8, noise=0.2)
        if not carry:
            hand = (0.12, -0.09, SHOULDER - 0.1)
            bar(hand, (0.19, -0.1, SHOULDER + 0.1), 0.017, club, verts=8)
            sphere(0.2, -0.1, SHOULDER + 0.11, 0.028, club)


def wolf(frame):
    """Волк: серый, на четырёх лапах, ~0.5 клетки в длину; смотрит вдоль +x. Бег: фаза ног frame * 90 градусов"""
    fur = mat("wolf_fur", 0x7a7c80, 0.9, noise=0.25, bump=0.5)
    dark = mat("wolf_dark", 0x4a4c50, 0.9, noise=0.2)
    light = mat("wolf_light", 0xc8c8c4, 0.9, noise=0.1)
    ph = frame * math.pi / 2
    sw = math.sin(ph) * math.radians(32)
    lift = abs(math.cos(ph)) * 0.01
    sphere(0, 0, 0.17 + lift, 0.11, fur, sx=1.7, sy=0.8, sz=0.8)
    sphere(0.14, 0, 0.19 + lift, 0.085, fur, sx=1.0, sy=0.85, sz=0.95)
    sphere(0.27, 0, 0.21 + lift, 0.06, fur, sx=1.2, sy=0.85, sz=0.85)
    sphere(0.36, 0, 0.195 + lift, 0.03, dark, sx=1.3, sy=0.8, sz=0.8)
    sphere(0.33, 0, 0.19 + lift, 0.04, light, sx=1.1, sy=0.9, sz=0.5)
    for k in (-1, 1):
        cone(0.24, 0.035 * k, 0.25 + lift, 0.31 + lift, 0.022, dark, verts=6)
    for (lx, ly, kk) in ((0.12, 0.05, 1), (0.12, -0.05, -1), (-0.14, 0.05, -1), (-0.14, -0.05, 1)):
        top = (lx, ly, 0.15 + lift)
        end = (lx + math.sin(sw * kk) * 0.12, ly, 0.15 - math.cos(sw * kk) * 0.12 + lift)
        bar(top, end, 0.02, fur, verts=6)
        sphere(end[0], end[1], end[2] + 0.01, 0.026, dark, sx=1.3)
    # хвост, опущенный назад
    bar((-0.2, 0, 0.19 + lift), (-0.34, 0, 0.12 + lift), 0.026, fur, verts=6)
    sphere(-0.35, 0, 0.115 + lift, 0.03, dark)


def kid(frame):
    """Ребёнок: ~65% роста жителя, голова крупнее, яркая красная рубаха, бежит с размахом рук"""
    shirt = mat("kid_shirt", 0xd8403a, 0.85, noise=0.1)
    pants = mat("kid_pants", 0x3a5a8a, 0.8, noise=0.1)
    boots = mat("boots", 0x2e2018, 0.6, noise=0)
    skin = mat("skin", 0xf0c8a0, 0.6, noise=0.04)
    hair = mat("kid_hair", 0xc8a040, 0.8, noise=0.1)
    hip, leg, sh, arm = 0.13, 0.125, 0.25, 0.1
    ph = frame * math.pi / 2
    swing = math.sin(ph) * math.radians(38)
    bob = -abs(math.cos(ph)) * 0.01 + 0.01
    for side, k in ((0.03, 1), (-0.03, -1)):
        foot = _limb((0, side, hip + bob), swing * k, leg, 0.022, pants)
        sphere(foot[0] + 0.012, foot[1], max(0.016, foot[2]), 0.024, boots, sx=1.5, sz=0.75)
    bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=0.068, radius2=0.054, depth=sh - hip + 0.03,
                                    location=(0, 0, (hip - 0.03 + sh) / 2 + bob))
    o = bpy.context.object
    o.data.materials.append(shirt)
    bpy.ops.object.shade_smooth()
    sphere(0, 0, sh - 0.005 + bob, 0.056, shirt, sx=0.85, sz=0.55)
    for side, k in ((0.068, -1), (-0.068, 1)):
        _limb((0, side, sh - 0.01 + bob), swing * k * 1.1, arm, 0.017, shirt, sideways=side * 0.15)
    hz = 0.32 + bob
    cylinder(0, 0, sh + bob, hz - 0.03, 0.02, skin, verts=8, bevel=0)
    sphere(0, 0, hz, 0.066, skin, sz=1.03)
    sphere(-0.012, 0, hz + 0.014, 0.067, hair, sz=0.92)


def wagon(frame):
    """Повозка соседей: рыжая лошадь с хомутом и телега с тюками, смотрит вдоль +x, ~1.1 клетки; ход - ноги и спицы"""
    fur = mat("horse_fur", 0x8a5a32, 0.8, noise=0.2, bump=0.3)
    dark = mat("horse_dark", 0x3a2a1c, 0.8, noise=0.15)
    wood = mat("wagon_wood", 0x9a6a3a, 0.7, kind="planks", scale=40)
    rim = mat("wagon_rim", 0x3a3430, 0.5, metal=0.5, noise=0)
    cloth = mat("wagon_cloth", 0xd8d0b8, 0.9, noise=0.2, bump=0.5)
    red = mat("wagon_red", 0xb83a2a, 0.8, noise=0.1)
    ph = frame * math.pi / 2
    sw = math.sin(ph) * math.radians(30)
    lift = abs(math.cos(ph)) * 0.008
    ox = -0.12
    # лошадь
    hx = ox + 0.4
    sphere(hx, 0, 0.27 + lift, 0.11, fur, sx=1.5, sy=0.75, sz=0.85)
    bar((hx + 0.12, 0, 0.3 + lift), (hx + 0.2, 0, 0.42 + lift), 0.05, fur, verts=8)
    sphere(hx + 0.24, 0, 0.44 + lift, 0.05, fur, sx=1.4, sy=0.8, sz=0.9)
    sphere(hx + 0.3, 0, 0.42 + lift, 0.028, dark, sx=1.2)
    for k in (-1, 1):
        cone(hx + 0.21, 0.025 * k, 0.47 + lift, 0.53 + lift, 0.016, dark, verts=6)
    bar((hx + 0.12, 0, 0.46 + lift), (hx + 0.05, 0, 0.38 + lift), 0.012, dark, verts=5)
    bar((hx - 0.17, 0, 0.3 + lift), (hx - 0.24, 0, 0.18 + lift), 0.02, dark, verts=6)
    for (lx, ly, kk) in ((0.13, 0.05, 1), (0.13, -0.05, -1), (-0.13, 0.05, -1), (-0.13, -0.05, 1)):
        top = (hx + lx, ly, 0.22 + lift)
        end = (hx + lx + math.sin(sw * kk) * 0.17, ly, 0.22 - math.cos(sw * kk) * 0.17 + lift)
        bar(top, end, 0.02, fur, verts=6)
        sphere(end[0], end[1], end[2] + 0.01, 0.026, dark, sx=1.3)
    # хомут и оглобли
    sphere(hx + 0.1, 0, 0.3 + lift, 0.07, red, sx=0.5, sy=1.1, sz=1.0)
    for k in (-1, 1):
        bar((hx + 0.1, 0.07 * k, 0.28 + lift), (ox - 0.02, 0.09 * k, 0.17), 0.008, wood, verts=5)
    # телега
    cx = ox - 0.1
    box(cx - 0.27, -0.1, 0.15, cx + 0.2, 0.1, 0.18, wood, bevel=0.006)
    for s_ in (-1, 1):
        box(cx - 0.27, s_ * 0.1 - 0.008, 0.18, cx + 0.2, s_ * 0.1 + 0.008, 0.28, wood, bevel=0.004)
    box(cx - 0.27, -0.1, 0.18, cx - 0.256, 0.1, 0.28, wood, bevel=0.004)
    box(cx + 0.186, -0.1, 0.18, cx + 0.2, 0.1, 0.28, wood, bevel=0.004)
    for i, (bx, by, bz) in enumerate(((-0.15, -0.03, 0.18), (-0.02, 0.03, 0.18), (-0.1, 0.02, 0.27), (0.1, -0.02, 0.18))):
        sphere(cx + bx, by, bz + 0.07, 0.085, cloth if i % 2 == 0 else red, sx=1.1, sz=0.85)
    # колёса: обод, ступица и спицы поворачиваются на 15 градусов за кадр
    wa = frame * math.radians(15)
    for wx in (cx - 0.12, cx + 0.12):
        for s_ in (-1, 1):
            y = s_ * 0.125
            bpy.ops.mesh.primitive_torus_add(major_radius=0.095, minor_radius=0.012, major_segments=20, minor_segments=6,
                                             location=(wx, y, 0.1), rotation=(math.radians(90), 0, 0))
            bpy.context.object.data.materials.append(rim)
            sphere(wx, y, 0.1, 0.018, rim)
            for k in range(3):
                a = wa + k * math.pi / 3
                dx, dz = math.cos(a) * 0.09, math.sin(a) * 0.09
                bar((wx - dx, y, 0.1 - dz), (wx + dx, y, 0.1 + dz), 0.008, wood, verts=5)
