"""
Hearthvale: рендер спрайтов в Blender (запуск - render.py).

Мир: 1 клетка карты = 1 единица Blender, x и y - по земле, z - вверх; здание стоит на клетках
x 0..s, y 0..s, точка привязки спрайта - угол (0, 0, 0), ближний к зрителю.
Камера ортогональная под изометрию игры 2:1: смотрит вдоль +x+y, наклон 30 градусов;
одна клетка вдоль x даёт на экране +16 по горизонтали и +8 по вертикали (единицы мира игры),
в атласе - 3 пикселя на единицу мира (ATLAS_SCALE), то есть 48 x 24 пикселя.
Свет сверху слева (как у остальной графики): солнце со стороны -x+y, левые грани светлые, правые в тени;
тени падают на прозрачную «землю» (shadow catcher) и попадают в спрайт полупрозрачными.
"""
import bpy
import bmesh
import math
import random
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

ATLAS_SCALE = 3.0
PX_PER_UNIT = 16 * ATLAS_SCALE * math.sqrt(2)   # пикселей атласа на единицу Blender по горизонтали экрана

# ---------- сцена ----------


# анимированные части (флаг, бельё): в обычном рендере не строятся; render.py рендерит их кадрами отдельно -
# здание при этом невидимо, но закрывает то, что за ним (holdout), так кадр ложится поверх спрайта здания
anim_frame = None
anim_objects = []


def animated(build):
    """build(frame) строит анимированную часть для кадра 0..3; в обычном рендере пропускается"""
    if anim_frame is None:
        return
    before = set(bpy.data.objects)
    build(anim_frame)
    anim_objects.extend(set(bpy.data.objects) - before)


def cloth(origin, along, length, height, frame, m, taper=0.0, amp=0.035, waves=1.3, seg=8, name="cloth"):
    """
    Полотно (флаг, бельё), закреплённое краем в origin: вдоль along (единичный вектор по горизонтали) на length,
    вниз на height; taper - насколько сужается к свободному краю (1 - в точку). Волна бежит от древка, кадр - фаза.
    """
    a = Vector(along).normalized()
    side = Vector((-a.y, a.x, 0))
    ph = frame * math.pi / 2
    verts, faces = [], []
    for i in range(seg + 1):
        u = i / seg
        h = height * (1 - taper * u)
        off = side * (math.sin(u * waves * math.pi * 2 - ph) * amp * u)
        top = Vector(origin) + a * (length * u) + off
        verts.append(tuple(top))
        verts.append(tuple(top + Vector((0, 0, -h))))
    for i in range(seg):
        faces.append((i * 2, i * 2 + 2, i * 2 + 3, i * 2 + 1))
    o = mesh_from(verts, faces, m, name=name)
    o.modifiers.new("t", 'SOLIDIFY').thickness = 0.008
    return o


def prepare_anim():
    """Кадр анимации: всё, кроме анимированных частей, - невидимая преграда без тени (тень земли - от анимации)"""
    for o in bpy.data.objects:
        if o.type != 'MESH' or o in anim_objects or o.name == "ground":
            continue
        o.is_holdout = True
        o.visible_shadow = False
        move_to(o, no_lines_collection())


# окна текущей модели: (центр стекла, нормаль наружу) - для света в окнах ночью (точки win* в render.json)
windows = []


def reset():
    windows.clear()
    anim_objects.clear()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    for c in (bpy.data.meshes, bpy.data.materials, bpy.data.objects):
        for x in list(c):
            c.remove(x)


def setup_render(samples=96):
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    prefs = bpy.context.preferences.addons['cycles'].preferences
    device = 'CPU'
    for kind in ('OPTIX', 'CUDA'):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                for d in prefs.devices:
                    d.use = d.type == kind
                device = 'GPU'
                break
        except TypeError:
            pass
    sc.cycles.device = device
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 6
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'
    sc.render.image_settings.color_depth = '8'
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    sc.view_settings.exposure = 0.0
    sc.render.pixel_aspect_x = 1
    sc.render.pixel_aspect_y = 1
    sc.render.resolution_percentage = 100
    # тёмная обводка, как у рисованной графики: контур и острые рёбра
    sc.render.use_freestyle = True
    sc.render.line_thickness_mode = 'ABSOLUTE'
    sc.render.line_thickness = 1.0
    vl = sc.view_layers[0]
    vl.use_freestyle = True
    fs = vl.freestyle_settings
    fs.crease_angle = math.radians(100)
    # два слоя линий: толстый внешний контур всего силуэта и тонкие полупрозрачные рёбра внутри
    while len(fs.linesets) < 2:
        fs.linesets.new("lines%d" % len(fs.linesets))
    for k, ls in enumerate(fs.linesets[:2]):
        ls.select_by_visibility = True
        ls.select_by_collection = True
        ls.collection_negation = 'EXCLUSIVE'
        ls.collection = no_lines_collection()
        ls.select_silhouette = k == 1
        ls.select_border = k == 1
        ls.select_crease = k == 1
        ls.select_external_contour = k == 0
        if ls.linestyle is None:
            ls.linestyle = bpy.data.linestyles.new("outline%d" % k)
        ls.linestyle.color = (0.14, 0.09, 0.05)
        ls.linestyle.alpha = 0.95 if k == 0 else 0.35
        ls.linestyle.thickness = 2.6 if k == 0 else 0.9


def no_lines_collection():
    c = bpy.data.collections.get("nolines")
    if c is None:
        c = bpy.data.collections.new("nolines")
        bpy.context.scene.collection.children.link(c)
    return c


def setup_light(night=False, nature=False):
    sc = bpy.context.scene
    world = bpy.data.worlds.new("sky")
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.9, 0.9, 0.95, 1)
    bg.inputs[1].default_value = 0.55
    sun = bpy.data.lights.new("sun", 'SUN')
    sun.energy = 4.2
    sun.color = (1.0, 0.93, 0.8)
    sun.angle = math.radians(4)
    o = bpy.data.objects.new("sun", sun)
    sc.collection.objects.link(o)
    # солнце слева (-x), чуть сзади и высоко: левая (-x) грань и видимый скат освещены, правая (-y) в тени
    d = Vector((1.0, -0.35, -1.6)).normalized()
    if nature:
        # природа: солнце круче (короткая тень), мягче и с большей долей рассеянного света (светлая тень); азимут тот же
        d = Vector((1.0, -0.35, -4.2)).normalized()
        sun.energy = 3.4
        sun.angle = math.radians(12)
        bg.inputs[1].default_value = 0.85
    o.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()


def shadow_ground(size=12):
    bpy.ops.mesh.primitive_plane_add(size=size, location=(0, 0, 0))
    p = bpy.context.object
    p.name = "ground"
    p.is_shadow_catcher = True
    move_to(p, no_lines_collection())
    return p


def move_to(o, col):
    for c in list(o.users_collection):
        c.objects.unlink(o)
    col.objects.link(o)


def camera_for(size, height, margin=0.6, rot=0):
    """Ортокамера под изометрию; кадр охватывает ромб size x size с запасом и высоту height (единицы Blender)"""
    sc = bpy.context.scene
    cam = bpy.data.cameras.new("cam")
    cam.type = 'ORTHO'
    cam.sensor_fit = 'HORIZONTAL'
    o = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(o)
    sc.camera = o
    o.rotation_euler = (math.radians(60), 0, math.radians(-45 + 90 * rot))
    # ширина: ромб s клеток = s * 2 * 48 пикселей + поля; высота: ромб + здание
    w_units = (size + 2 * margin) * math.sqrt(2)
    res_x = int(math.ceil(w_units * PX_PER_UNIT / 2)) * 2
    h_px = (size + 2 * margin) * 48 + height * math.cos(math.radians(30)) * PX_PER_UNIT + 40
    res_y = int(math.ceil(h_px / 2)) * 2
    sc.render.resolution_x = res_x
    sc.render.resolution_y = res_y
    cam.ortho_scale = res_x / PX_PER_UNIT
    # центр кадра - середина между ближним нижним углом (0, 0, 0) и дальним верхним (s, s, height)
    yaw = math.radians(45 + 90 * rot)
    fwd = Vector((math.cos(math.radians(30)) * math.cos(yaw), math.cos(math.radians(30)) * math.sin(yaw), -math.sin(math.radians(30))))
    center = Vector((size / 2, size / 2, height / 2))
    o.location = center - fwd * 30
    cam.clip_end = 100
    return o


def project(p):
    """Точка мира -> пиксель кадра (x слева, y снизу)"""
    sc = bpy.context.scene
    bpy.context.view_layer.update()   # без рендера матрица новой камеры иначе не пересчитана
    co = world_to_camera_view(sc, sc.camera, Vector(p))
    return co.x * sc.render.resolution_x, co.y * sc.render.resolution_y


def render_to(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


# ---------- материалы ----------

_snow = False
_snow_bias = 0.0   # >0: снег ложится и на крутые склоны (ярусы ели)


def set_snow(v, bias=0.0):
    global _snow, _snow_bias
    _snow = v
    _snow_bias = bias


def _principled(nt, color, rough, metal=0.0):
    b = nt.nodes.new("ShaderNodeBsdfPrincipled")
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    return b


def srgb(hex_):
    def ch(v):
        v = v / 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return (ch(hex_ >> 16 & 255), ch(hex_ >> 8 & 255), ch(hex_ & 255))


_mats = {}


def mat(name, hex_, rough=0.8, noise=0.15, bump=0.0, scale=12.0, kind=None, metal=0.0, ground=False):
    """
    Материал: цвет hex (sRGB), шероховатость; noise - разброс цвета пятнами, bump - рельеф шумом.
    kind: "brick" - кладка/черепица (Brick Texture), "planks" - доски, None - однородный.
    Кладка на стенах и крышах раскладывается по (x + y, z) - ряды горизонтальны на обеих видимых стенах;
    ground - по (x, y) для площадок. В зимнем режиме (set_snow) на гранях, смотрящих вверх, лежит снег.
    """
    key = (name, _snow)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new(name + ("_snow" if _snow else ""))
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    base = srgb(hex_)
    bsdf = _principled(nt, base, rough, metal)
    tc = nt.nodes.new("ShaderNodeTexCoord")
    col_socket = None
    wall_uv = tc.outputs["Object"]
    if not ground:
        sep = nt.nodes.new("ShaderNodeSeparateXYZ")
        nt.links.new(tc.outputs["Object"], sep.inputs[0])
        su = nt.nodes.new("ShaderNodeMath")
        su.operation = 'ADD'
        nt.links.new(sep.outputs["X"], su.inputs[0])
        nt.links.new(sep.outputs["Y"], su.inputs[1])
        comb = nt.nodes.new("ShaderNodeCombineXYZ")
        nt.links.new(su.outputs[0], comb.inputs[0])
        nt.links.new(sep.outputs["Z"], comb.inputs[1])
        wall_uv = comb.outputs[0]
    if kind == "brick":
        br = nt.nodes.new("ShaderNodeTexBrick")
        nt.links.new(wall_uv, br.inputs["Vector"])
        br.inputs["Scale"].default_value = scale
        br.inputs["Color1"].default_value = (*base, 1)
        br.inputs["Color2"].default_value = (*[c * 0.78 for c in base], 1)
        br.inputs["Mortar"].default_value = (*[c * 0.55 for c in base], 1)
        br.inputs["Mortar Size"].default_value = 0.03
        br.inputs["Brick Width"].default_value = 0.5
        br.inputs["Row Height"].default_value = 0.25
        col_socket = br.outputs["Color"]
        if bump == 0:
            bump = 0.25
        bsrc = br.outputs["Fac"]
    elif kind == "planks":
        br = nt.nodes.new("ShaderNodeTexBrick")
        nt.links.new(wall_uv, br.inputs["Vector"])
        br.inputs["Scale"].default_value = scale
        br.inputs["Color1"].default_value = (*base, 1)
        br.inputs["Color2"].default_value = (*[c * 0.82 for c in base], 1)
        br.inputs["Mortar"].default_value = (*[c * 0.45 for c in base], 1)
        br.inputs["Mortar Size"].default_value = 0.01
        br.inputs["Brick Width"].default_value = 2.5
        br.inputs["Row Height"].default_value = 0.18
        br.offset = 0.4
        col_socket = br.outputs["Color"]
        if bump == 0:
            bump = 0.15
        bsrc = br.outputs["Fac"]
    else:
        bsrc = None
    if noise > 0:
        nz = nt.nodes.new("ShaderNodeTexNoise")
        nt.links.new(tc.outputs["Object"], nz.inputs["Vector"])
        nz.inputs["Scale"].default_value = 6.0
        nz.inputs["Detail"].default_value = 4.0
        mixc = nt.nodes.new("ShaderNodeMix")
        mixc.data_type = 'RGBA'
        mixc.blend_type = 'MULTIPLY'
        mixc.inputs["Factor"].default_value = noise * 2
        if col_socket is not None:
            nt.links.new(col_socket, mixc.inputs[6])
        else:
            mixc.inputs[6].default_value = (*base, 1)
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.elements[0].color = (0.8, 0.8, 0.8, 1)
        ramp.color_ramp.elements[1].color = (1.15, 1.12, 1.08, 1)
        nt.links.new(nz.outputs["Fac"], ramp.inputs["Fac"])
        nt.links.new(ramp.outputs["Color"], mixc.inputs[7])
        col_socket = mixc.outputs[2]
        if bsrc is None and bump > 0:
            bsrc = nz.outputs["Fac"]
    if col_socket is not None:
        nt.links.new(col_socket, bsdf.inputs["Base Color"])
    if bump > 0 and bsrc is not None:
        bn = nt.nodes.new("ShaderNodeBump")
        bn.inputs["Strength"].default_value = bump
        bn.inputs["Distance"].default_value = 0.02
        nt.links.new(bsrc, bn.inputs["Height"])
        nt.links.new(bn.outputs["Normal"], bsdf.inputs["Normal"])
    shader = bsdf.outputs["BSDF"]
    if _snow:
        shader = _add_snow(nt, shader)
    nt.links.new(shader, out.inputs["Surface"])
    _mats[key] = m
    return m


def _add_snow(nt, shader):
    """Снег на гранях, смотрящих вверх: по нормали (z > ~0.5) с рваным краем шумом"""
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Normal"], sep.inputs[0])
    nz = nt.nodes.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 9.0
    add = nt.nodes.new("ShaderNodeMath")
    add.operation = 'ADD'
    nt.links.new(sep.outputs["Z"], add.inputs[0])
    sub = nt.nodes.new("ShaderNodeMath")
    sub.operation = 'MULTIPLY_ADD'
    sub.inputs[1].default_value = 0.7
    sub.inputs[2].default_value = -0.38 + _snow_bias
    nt.links.new(nz.outputs["Fac"], sub.inputs[0])
    nt.links.new(sub.outputs[0], add.inputs[1])
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.inputs["From Min"].default_value = 0.45
    mr.inputs["From Max"].default_value = 0.6
    nt.links.new(add.outputs[0], mr.inputs["Value"])
    snow = _principled(nt, srgb(0xf2f5fa), 0.6)
    snow.inputs["Subsurface Weight"].default_value = 0.2
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(mr.outputs["Result"], mix.inputs[0])
    nt.links.new(shader, mix.inputs[1])
    nt.links.new(snow.outputs["BSDF"], mix.inputs[2])
    return mix.outputs[0]


def glass():
    key = ("glass", _snow)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new("glass")
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*srgb(0x2a3448), 1)
    b.inputs["Roughness"].default_value = 0.15
    b.inputs["Specular IOR Level"].default_value = 0.8
    # тёплый свет изнутри чуть-чуть - окна живые
    b.inputs["Emission Color"].default_value = (*srgb(0xffc870), 1)
    b.inputs["Emission Strength"].default_value = 0.08
    _mats[key] = m
    return m


# ---------- геометрия ----------

def _obj(name, mesh):
    o = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(o)
    return o


def box(x0, y0, z0, x1, y1, z1, material, bevel=0.012, name="box", lines=True):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x = x0 + (v.co.x + 0.5) * (x1 - x0)
        v.co.y = y0 + (v.co.y + 0.5) * (y1 - y0)
        v.co.z = z0 + (v.co.z + 0.5) * (z1 - z0)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = _obj(name, me)
    o.data.materials.append(material)
    if bevel > 0:
        md = o.modifiers.new("bevel", 'BEVEL')
        md.width = bevel
        md.segments = 2
        md.limit_method = 'ANGLE'
    if not lines:
        move_to(o, no_lines_collection())
    return o


def poly_prism(points, z0, z1, material, bevel=0.01, name="prism"):
    """Призма: многоугольник points [(x, y)] от z0 до z1"""
    bm = bmesh.new()
    vs = [bm.verts.new((x, y, z0)) for x, y in points]
    f = bm.faces.new(vs)
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in ext["geom"]:
        if isinstance(v, bmesh.types.BMVert):
            v.co.z = z1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = _obj(name, me)
    o.data.materials.append(material)
    if bevel > 0:
        md = o.modifiers.new("bevel", 'BEVEL')
        md.width = bevel
        md.segments = 2
        md.limit_method = 'ANGLE'
    return o


def mesh_from(verts, faces, material, name="mesh", smooth=False):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    o = _obj(name, me)
    o.data.materials.append(material)
    if smooth:
        for p in me.polygons:
            p.use_smooth = True
    return o


def snow_mat():
    key = ("snowcap", True)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new("snowcap")
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*srgb(0xf4f7fb), 1)
    bsdf.inputs["Roughness"].default_value = 0.6
    bsdf.inputs["Subsurface Weight"].default_value = 0.2
    _mats[key] = m
    return m


def snow_roll(p0, p1, normal, rn, rw, caps=True):
    """Валик снега между точками p0 и p1: сечение - эллипс (rn вдоль normal, rw поперёк), концы скруглены шарами"""
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    ln = d.length
    if ln < 1e-4:
        return []
    z = d / ln
    x = (Vector(normal) - z * Vector(normal).dot(z)).normalized()
    y = z.cross(x)
    from mathutils import Matrix
    objs = []
    rot = Matrix(((x.x, y.x, z.x), (x.y, y.y, z.y), (x.z, y.z, z.z))).to_4x4()
    mat_ = snow_mat()
    bpy.ops.mesh.primitive_cylinder_add(vertices=12, radius=1, depth=2, location=(p0 + p1) / 2)
    o = bpy.context.object
    o.name = "snowroll"
    o.matrix_world = Matrix.Translation((p0 + p1) / 2) @ rot @ Matrix.Diagonal((rn, rw, ln / 2, 1))
    o.data.materials.append(mat_)
    bpy.ops.object.shade_smooth()
    move_to(o, no_lines_collection())
    objs.append(o)
    if caps:
        for pt in (p0, p1):
            bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=1, location=pt)
            c = bpy.context.object
            c.name = "snowcap"
            c.matrix_world = Matrix.Translation(pt) @ rot @ Matrix.Diagonal((rn, rw, rw, 1))
            c.data.materials.append(mat_)
            bpy.ops.object.shade_smooth()
            move_to(c, no_lines_collection())
            objs.append(c)
    return objs



def cylinder(x, y, z0, z1, r, material, verts=16, name="cyl", bevel=0.01):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=z1 - z0, location=(x, y, (z0 + z1) / 2))
    o = bpy.context.object
    o.name = name
    o.data.materials.append(material)
    if bevel > 0:
        md = o.modifiers.new("bevel", 'BEVEL')
        md.width = bevel
        md.segments = 2
        md.limit_method = 'ANGLE'
    bpy.ops.object.shade_smooth()
    return o


def cone(x, y, z0, z1, r, material, verts=16, name="cone", eave=False):
    """eave - кровля башни: зимой валик снега по кромке (у четырёхгранной - с поворотом на 45 градусов, как её ставят) и по рёбрам"""
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, radius2=0, depth=z1 - z0, location=(x, y, (z0 + z1) / 2))
    o = bpy.context.object
    o.name = name
    o.data.materials.append(material)
    if eave and _snow:
        off = math.pi / 4 if verts == 4 else 0.0
        k = min(1.0, r / 0.3)
        rn, rw = max(0.04 * k, 0.046), max(0.035 * k, 0.042)   # у малых кровель валик не тоньше предела, чтобы в игре был виден
        h = z1 - z0
        apex = Vector((x, y, z1))
        base = [Vector((x + r * math.cos(off + 2 * math.pi * i / verts), y + r * math.sin(off + 2 * math.pi * i / verts), z0)) for i in range(verts)]
        for i in range(verts):
            a, b = base[i], base[(i + 1) % verts]
            mid = (a + b) / 2
            rad = Vector((mid.x - x, mid.y - y, 0))
            rm = rad.length
            if rm < 1e-4:
                continue
            u = rad / rm
            nrm = (u * h + Vector((0, 0, rm))).normalized()
            out = u * 0.01
            snow_roll(a + nrm * 0.02 + out, b + nrm * 0.02 + out, nrm, rn, rw)
            if verts <= 6 and r >= 0.3:
                nrm2 = (Vector((a.x - x, a.y - y, 0)).normalized() * h + Vector((0, 0, r))).normalized()
                snow_roll(a + nrm2 * 0.025, a.lerp(apex, 0.82) + nrm2 * 0.03, nrm2, 0.024, 0.022)
        # шапочка снега на острой вершине
        cr = max(0.042, min(0.1, r * 0.2))
        bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=1, location=(x, y, z1 - cr * 0.6))
        c = bpy.context.object
        c.name = "snowtip"
        c.scale = (cr, cr, cr * 1.35)
        c.data.materials.append(snow_mat())
        bpy.ops.object.shade_smooth()
        move_to(c, no_lines_collection())
    return o


def sphere(x, y, z, r, material, name="sphere", sx=1.0, sy=1.0, sz=1.0):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=10, radius=r, location=(x, y, z))
    o = bpy.context.object
    o.name = name
    o.scale = (sx, sy, sz)
    o.data.materials.append(material)
    bpy.ops.object.shade_smooth()
    return o


def gable_roof(x0, y0, x1, y1, z, h, along_x, material, over=0.08, thick=0.05, rows=7, ridge_mat=None, gable_mat=None, wall_gap=0.0):
    """
    Двускатная крыша над прямоугольником (x0..x1, y0..y1) с основанием на высоте z и коньком на z + h.
    Конёк вдоль x (along_x) или вдоль y. Скаты - ряды черепицы/соломы ступеньками (настоящая тень от рядов),
    фронтоны закрыты треугольниками (gable_mat - материал стены, None - как у крыши).
    """
    objs = []
    if along_x:
        a0, a1 = x0 - over, x1 + over      # вдоль конька
        b0, b1 = y0 - over, y1 + over      # поперёк
        mid = (y0 + y1) / 2
    else:
        a0, a1 = y0 - over, y1 + over
        b0, b1 = x0 - over, x1 + over
        mid = (x0 + x1) / 2

    def P(a, b, zz):
        return (a, b, zz) if along_x else (b, a, zz)

    zr = z + h
    zb = z - over * h / ((b1 - b0) / 2)   # свес продолжает скат вниз
    for side in (0, 1):
        be = b0 if side == 0 else b1
        for r in range(rows):
            t0 = r / rows
            t1 = (r + 1.25) / rows
            if t1 > 1:
                t1 = 1.0
            bb0 = be + (mid - be) * t0
            bb1 = be + (mid - be) * t1
            zz0 = zb + (zr - zb) * t0
            zz1 = zb + (zr - zb) * t1
            # толщина ряда: низ ряда чуть выступает (ступенька черепицы)
            dz = thick
            verts = [P(a0, bb0, zz0), P(a1, bb0, zz0), P(a1, bb1, zz1), P(a0, bb1, zz1),
                     P(a0, bb0, zz0 - dz), P(a1, bb0, zz0 - dz), P(a1, bb1, zz1 - dz * 0.3), P(a0, bb1, zz1 - dz * 0.3)]
            faces = [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
            if side == 1:
                faces = [tuple(reversed(f)) for f in faces]
            objs.append(mesh_from(verts, faces, material, name="roofrow"))
    if _snow:
        # снежная шапка: валик по свесу и торцам каждого ската и по коньку
        for side in (0, 1):
            be = b0 if side == 0 else b1
            sg = 1 if side == 0 else -1
            tb, tz = mid - be, zr - zb
            tl = math.hypot(tb, tz)
            nb, nz = -tz / tl * sg, tb / tl * sg
            nrm = Vector((nb, 0, nz)) if not along_x else Vector((0, nb, nz))
            lift = 0.022
            def Q(a, b, zz):
                return Vector(P(a, b, zz)) + nrm * lift
            inward = 0.015 * sg
            objs += snow_roll(Q(a0, be + inward, zb), Q(a1, be + inward, zb), nrm, 0.04, 0.038)
            for ae, ai in ((a0, 0.012), (a1, -0.012)):
                objs += snow_roll(Q(ae + ai, be + inward, zb), Q(ae + ai, mid, zr), nrm, 0.04, 0.032)
        objs += snow_roll(P(a0, mid, zr + 0.03), P(a1, mid, zr + 0.03), (0, 0, 1), 0.05, 0.055)
    # конёк
    rm = ridge_mat or material
    if along_x:
        objs.append(box(a0 - 0.01, mid - 0.035, zr - 0.03, a1 + 0.01, mid + 0.035, zr + 0.035, rm, bevel=0.015))
    else:
        objs.append(box(mid - 0.035, a0 - 0.01, zr - 0.03, mid + 0.035, a1 + 0.01, zr + 0.035, rm, bevel=0.015))
    # фронтоны (треугольники стены под скатами)
    gm = gable_mat or material
    for a in ((x0, x1) if along_x else (y0, y1)):
        tri = [P(a, (y0 if along_x else x0), z), P(a, (y1 if along_x else x1), z), P(a, mid, zr - 0.02)]
        o = mesh_from([(p[0], p[1], p[2]) for p in tri], [(0, 1, 2)], gm, name="gable")
        sol = o.modifiers.new("thick", 'SOLIDIFY')
        sol.thickness = 0.04
        objs.append(o)
    return objs


def window(face, u, z, w, h, frame_mat, shutter_mat=None, depth=0.03):
    """
    Окно на стене. face = (ось, координата стены, направление наружу): ("x", 0.4, -1) - стена x = 0.4, смотрит в -x;
    u - центр окна вдоль стены, z - низ окна.
    """
    axis, c, out = face
    g = glass()
    t = 0.025

    def B(u0, u1, z0, z1, d0, d1, m, bevel=0.006):
        if axis == "x":
            return box(c + out * d0, u0, z0, c + out * d1, u1, z1, m, bevel=bevel)
        return box(u0, c + out * d0, z0, u1, c + out * d1, z1, m, bevel=bevel)

    def Bs(u0, u1, z0, z1, d0, d1, m, bevel=0.006):
        a, b = sorted((d0, d1))
        return B(u0, u1, z0, z1, a, b, m, bevel)

    Bs(u - w / 2, u + w / 2, z, z + h, -0.01, 0.005, g, bevel=0)
    centre = (c + out * 0.01, u, z + h / 2) if axis == "x" else (u, c + out * 0.01, z + h / 2)
    windows.append((Vector(centre), Vector((out, 0, 0)) if axis == "x" else Vector((0, out, 0))))
    Bs(u - w / 2 - t, u - w / 2 + t * 0.4, z - t, z + h + t, 0, depth, frame_mat)
    Bs(u + w / 2 - t * 0.4, u + w / 2 + t, z - t, z + h + t, 0, depth, frame_mat)
    Bs(u - w / 2 - t, u + w / 2 + t, z + h - t * 0.3, z + h + t * 1.2, 0, depth, frame_mat)
    Bs(u - w / 2 - t * 1.6, u + w / 2 + t * 1.6, z - t * 1.6, z, 0, depth * 1.8, frame_mat)   # подоконник
    Bs(u - t * 0.3, u + t * 0.3, z, z + h, 0, depth * 0.6, frame_mat, bevel=0)                 # переплёт
    Bs(u - w / 2, u + w / 2, z + h * 0.5 - t * 0.3, z + h * 0.5 + t * 0.3, 0, depth * 0.6, frame_mat, bevel=0)
    if shutter_mat is not None:
        Bs(u - w / 2 - t - w * 0.42, u - w / 2 - t, z, z + h, 0.0, 0.012, shutter_mat)
        Bs(u + w / 2 + t, u + w / 2 + t + w * 0.42, z, z + h, 0.0, 0.012, shutter_mat)


def door(face, u, w, h, mat_door, frame_mat, z=0.0):
    axis, c, out = face
    t = 0.03

    def Bs(u0, u1, z0, z1, d0, d1, m, bevel=0.006):
        a, b = sorted((d0, d1))
        if axis == "x":
            return box(c + out * a, u0, z0, c + out * b, u1, z1, m, bevel=bevel)
        return box(u0, c + out * a, z0, u1, c + out * b, z1, m, bevel=bevel)

    Bs(u - w / 2, u + w / 2, z, z + h, -0.01, 0.008, mat_door)
    Bs(u - w / 2 - t, u - w / 2, z, z + h + t, 0, 0.03, frame_mat)
    Bs(u + w / 2, u + w / 2 + t, z, z + h + t, 0, 0.03, frame_mat)
    Bs(u - w / 2 - t, u + w / 2 + t, z + h, z + h + t, 0, 0.03, frame_mat)


def timber_frame(face, u0, u1, z0, z1, beam_mat, braces=True, posts=4):
    """Фахверк: балки по краям, пояс, стойки и раскосы поверх стены"""
    axis, c, out = face
    t = 0.035
    d0, d1 = (0.0, 0.018)

    def Bs(a0, a1, zz0, zz1):
        a, b = sorted((out * d0, out * d1))
        if axis == "x":
            return box(c + a, a0, zz0, c + b, a1, zz1, beam_mat, bevel=0.004)
        return box(a0, c + a, zz0, a1, c + b, zz1, beam_mat, bevel=0.004)

    Bs(u0, u1, z0, z0 + t)
    Bs(u0, u1, z1 - t, z1)
    Bs(u0, u1, (z0 + z1) / 2 - t / 2, (z0 + z1) / 2 + t / 2)
    for i in range(posts + 1):
        uu = u0 + (u1 - u0) * i / posts
        Bs(uu - t / 2, uu + t / 2, z0, z1)
    if braces:
        # раскосы в крайних полях
        for (ua, ub) in ((u0, u0 + (u1 - u0) / posts), (u1 - (u1 - u0) / posts, u1)):
            _brace(face, ua, ub, z0 + t, (z0 + z1) / 2, beam_mat)


def _brace(face, ua, ub, za, zb, m):
    axis, c, out = face
    t = 0.028
    d = 0.018 * out
    if axis == "x":
        p = [(c, ua, za), (c, ua + t, za), (c, ub, zb), (c, ub - t, zb)]
        verts = [(x + (d if k else 0), y, z) for k in (0, 1) for (x, y, z) in p]
    else:
        p = [(ua, c, za), (ua + t, c, za), (ub, c, zb), (ub - t, c, zb)]
        verts = [(x, y + (d if k else 0), z) for k in (0, 1) for (x, y, z) in p]
    faces = [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    o = mesh_from(verts, faces, m, name="brace")
    o.modifiers.new("n", 'WEIGHTED_NORMAL')


def chimney(x, y, z0, z1, m, cap_mat=None):
    box(x - 0.07, y - 0.07, z0, x + 0.07, y + 0.07, z1, m, bevel=0.01)
    box(x - 0.09, y - 0.09, z1, x + 0.09, y + 0.09, z1 + 0.035, cap_mat or m, bevel=0.01)
    return (x, y, z1 + 0.04)


def barrel(x, y, r=0.07, h=0.16):
    wood = mat("barrel", 0x8a5a32, 0.7, kind="planks", scale=40)
    hoop = mat("hoop", 0x3a3430, 0.5, metal=0.6, noise=0)
    cylinder(x, y, 0, h, r, wood, verts=14)
    for zz in (h * 0.2, h * 0.8):
        cylinder(x, y, zz - 0.008, zz + 0.008, r * 1.04, hoop, verts=14, bevel=0)


def crate(x, y, s=0.12, z=0.0):
    box(x - s / 2, y - s / 2, z, x + s / 2, y + s / 2, z + s, mat("crate", 0xb58a52, 0.75, kind="planks", scale=30), bevel=0.008)


def log_pile(x, y, along_x=True, n=3, r=0.035, length=0.36):
    wood = mat("log", 0x7a5232, 0.8, noise=0.2)
    end = mat("logend", 0xd8b078, 0.7, noise=0.1)
    k = 0
    for row in range(n):
        for i in range(n - row):
            cx = x + (i - (n - row - 1) / 2) * r * 2.05
            cz = r + row * r * 1.75
            if along_x:
                bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=r, depth=length, location=(x, y + (i - (n - row - 1) / 2) * r * 2.05, cz), rotation=(0, math.radians(90), 0))
            else:
                bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=r, depth=length, location=(cx, y, cz), rotation=(math.radians(90), 0, 0))
            o = bpy.context.object
            o.data.materials.append(wood)
            o.data.materials.append(end)
            for p in o.data.polygons:
                if len(p.vertices) > 4:
                    p.material_index = 1
            k += 1


def yard(s, hex_=0xa89870, inset=0.04, cobble=False):
    """Утоптанная площадка под зданием: плита чуть выше земли с мягкой кромкой"""
    m = mat("yard_cobble" if cobble else "yard", hex_, 0.95, noise=0.25, bump=0.3, kind="brick" if cobble else None, scale=26, ground=True)
    return box(inset, inset, -0.02, s - inset, s - inset, 0.012, m, bevel=0.02, lines=False)


def rng(seed):
    return random.Random(seed)
