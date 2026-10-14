package town.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import town.Spr
import town.Storage
import town.Sprites
import town.sim.*

/**
 * Изометрия с поворотом карты на 90 градусов (rot 0..3). Мир (x, y) сначала поворачивается в «вид» (vx, vy):
 * rot 1 - (y, w - x), rot 2 - (w - x, h - y), rot 3 - (h - y, x); затем экран: sx = (vx - vy) * TW/2, sy = (vx + vy) * TH/2.
 * Земля рисуется ромбами, объекты (жители, здания, деревья) - по убыванию vx + vy, дальние первыми.
 * Здания нарисованы с 4 сторон: вид спрайта = (rot - разворот здания) mod 4 (рыбак развёрнут к воде).
 * Полная графика: луг с плавными пятнами цвета, переходы земли по двойной сетке (лес, камень, дорога,
 * вода с берегом и глубиной), мелкие предметы, дым; лёгкая (Sprites.low) - клетка одним спрайтом, без них.
 */
class WorldRenderer(private val world: World, private val sprites: Sprites) {
    val camera = OrthographicCamera()
    private val tw = Sprites.TW.toFloat()
    private val th = Sprites.TH.toFloat()

    /** Поворот карты: 0..3, по 90 градусов против часовой стрелки */
    var rot = 0
        private set

    private lateinit var treeCells: IntArray   // клетки леса и скал (объекты природы)
    private var wallCells = IntArray(0)       // клетки стен (частокол, каменная)
    /** Мелкие предметы на лугу: кусты и камыш (объекты), цветы и камни (на земле); kind >= 1000 - камыш / камни */
    private lateinit var propCells: IntArray
    private lateinit var propKind: IntArray
    private lateinit var decalCells: IntArray
    private lateinit var decalKind: IntArray
    /** Яркость и желтизна луга в узлах мира (i - 0.5, j - 0.5), i = 0..w+1, j = 0..h+1: плавные пятна без сетки */
    private lateinit var meadow: FloatArray
    private lateinit var meadowYellow: FloatArray
    /** Яркость воды в узлах клеток мира (i, j): у берега светлее, вглубь темнее */
    private lateinit var waterShade: FloatArray
    /** Разворот зданий (0: -y, 1: +x, 2: +y, 3: -x) и вариант «на берегу»; считается для рыбака по воде рядом */
    private var facing = IntArray(0)
    private var shore = BooleanArray(0)
    private var facingKnown = 0
    private val verts = FloatArray(20)
    private val resColor = world.content.resources.map { Color(it.color.toInt()) }
    /** Значки товаров для ноши (при смене атласа области обновляются на месте - список не устаревает) */
    private val carryIcons = world.content.resources.map { sprites.named("res_" + it.id) }
    /** Свет сцены: тёплый днём, золотой на рассвете и закате, синий ночью */
    private var lightR = 1f
    /** 0 днём .. 1 ночью - для света в окнах */
    private var dark = 0f
    private var lightG = 1f
    private var lightB = 1f
    /** Цвет кисти (свет x оттенок) - им же красятся вершины земли */
    private var curR = 1f
    private var curG = 1f
    private var curB = 1f
    private var curA = 1f
    private var keys = IntArray(1024)
    /** Время для анимаций (дым, крылья, огонь) - идёт и на паузе */
    private var time = 0f
    private var millDef = -1
    private val fisherDef = world.content.buildings.firstOrNull { it.id == "fisher" }?.index ?: -1

    init {
        millDef = world.content.buildings.firstOrNull { it.id == "mill" }?.index ?: -1
    }

    /** Предпросмотр дороги: клетки и можно ли на каждой проложить (null - нет предпросмотра) */
    /** Выбранное на карте (здание или клетка): левый верхний угол и размер, selX < 0 - ничего */
    var selX = -1
    var selY = 0
    var selSize = 1
    /** Выбранный житель (номер в массивах мира), -1 - никого */
    var selAgent = -1
    var roadCells: IntArray? = null
    var roadOk: BooleanArray? = null
    /** Линия - стена (ромбы), а не дорога */
    var lineWall = false
    /** Вид линии: 0 дорога, 1 стена, 2 мощёная, 3 мост */
    var lineKind = 0
    /** Слой-наложение (OV_*) и услуга для OV_SERVICE (null - число разных услуг) */
    /** Фоторежим: без значков над зданиями */
    var hideIcons = false
    var overlay = 0
    var overlayService: String? = null
    /** Рамка выделения шаблона: x0, y0, x1, y1 включительно */
    var selRect: IntArray? = null
    /** Призраки зданий шаблона (абсолютные клетки), дороги шаблона */
    var tplDefs: IntArray? = null
    var tplX: IntArray? = null
    var tplY: IntArray? = null
    var tplDir: IntArray? = null
    var tplOk: BooleanArray? = null
    var tplRoads: IntArray? = null
    var tplPaved: BooleanArray? = null
    private var ovCover = IntArray(0)
    private var ovTimer = 0f
    private var ovKey = ""
    private var traffic = FloatArray(0)
    /** Вытоптанность травы по клеткам (тики ходьбы с затуханием) - только для отрисовки, не сохраняется */
    private var wear = FloatArray(0)
    private var trafficTimer = 0f

    /** Куда житель смотрел в последний раз (стоя сохраняет): 0 - к зрителю, 1 - от него; отражение влево */
    private val dir = IntArray(Tune.MAX_AGENTS)

    companion object {
        /** Тиков на кадр ходьбы и на кадр бликов воды */
        const val WALK_FRAME = 4
        const val WATER_FRAME = 12
        const val GROUND_PAD = 0.35f
        /** Жители мельче нарисованного: рядом с крупными зданиями из Blender */
        const val PERSON_SCALE = 1f
        /** Мешок над головой - под размер фигурки из Blender */
        const val SACK_SCALE = 0.55f
        /** Высота значка ноши над головой жителя (единицы мира) */
        const val CARRY_ICON_H = 9f
        const val OV_NONE = 0
        const val OV_HAPPY = 1
        const val OV_SERVICE = 2
        const val OV_TRAFFIC = 3
        const val OV_SOIL = 4
        /** Насколько темнее земля за границей поселения */
        const val OUTSIDE_DARK = 0.28f
        /** Пятно света на земле у освещённого здания ночью - прозрачность */
        const val GLOW_A = 0.5f
        /** Значок над невыбранным зданием (простой, беда) - доля полного размера */
        const val ICON_K = 0.8f
        /** За границей (полная графика): насколько гаснет каждый канал - зелень бледнеет, земля сереет */
        const val OUT_R = 0.24f
        const val OUT_G = 0.34f
        const val OUT_B = 0.14f
        /** Вытоптанность «полная тропа» (тики ходьбы) и время зарастания (тики) */
        const val WEAR_FULL = 1500f
        const val WEAR_FADE = 6000f
        /** Дальше этого масштаба значки «можно улучшить» не рисуются */
        const val QUIET_ZOOM = 1.6f

    }
    /** Версия карты, для которой собраны списки деревьев и предметов (природа меняет лес и скалы) */
    private var seenVersion = -1
    private var count = 0
    var objectsDrawn = 0
        private set

    // видимая область с запасом на высокие спрайты
    private var vl = 0f; private var vr = 0f; private var vb = 0f; private var vt = 0f
    private val tmp = Vector3()

    init {
        refreshTrees()
    }

    fun refreshTrees() {
        val list = ArrayList<Int>()
        for (i in 0 until world.w * world.h) if (world.tiles[i] == FOREST || world.tiles[i] == ROCK) list.add(i)
        treeCells = list.toIntArray()
        val walls = ArrayList<Int>()
        for (i in 0 until world.w * world.h) if (world.tiles[i] == WALL_W || world.tiles[i] == WALL_S) walls.add(i)
        wallCells = walls.toIntArray()
        refreshNature()
    }

    // ---------- поворот ----------

    /** Размеры карты в виде */
    private val vw get() = if (rot and 1 == 0) world.w else world.h
    private val vh get() = if (rot and 1 == 0) world.h else world.w

    /** Точка мира -> вид (непрерывно) */
    private fun vX(x: Float, y: Float): Float = when (rot) { 0 -> x; 1 -> y; 2 -> world.w - x; else -> world.h - y }
    private fun vY(x: Float, y: Float): Float = when (rot) { 0 -> y; 1 -> world.w - x; 2 -> world.h - y; else -> x }

    /** Точка вида -> мир (непрерывно; для узлов сетки - целые) */
    private fun wX(a: Float, b: Float): Float = when (rot) { 0 -> a; 1 -> world.w - b; 2 -> world.w - a; else -> b }
    private fun wY(a: Float, b: Float): Float = when (rot) { 0 -> b; 1 -> a; 2 -> world.h - b; else -> world.h - a }

    /** Клетка вида -> номер клетки мира */
    private fun cellOf(xv: Int, yv: Int): Int {
        val x: Int; val y: Int
        when (rot) {
            0 -> { x = xv; y = yv }
            1 -> { x = world.w - 1 - yv; y = xv }
            2 -> { x = world.w - 1 - xv; y = world.h - 1 - yv }
            else -> { x = yv; y = world.h - 1 - xv }
        }
        return y * world.w + x
    }

    /** Повернуть карту на 90 градусов (+1 - против часовой, -1 - по часовой); точка в центре экрана остаётся в центре */
    fun rotate(step: Int) {
        val a = camera.position.x / (tw / 2); val b = camera.position.y / (th / 2)
        val fx = wX((a + b) / 2, (b - a) / 2); val fy = wY((a + b) / 2, (b - a) / 2)
        rot = (rot + step + 4) and 3
        camera.position.set(sx(fx, fy), sy(fx, fy), 0f)
    }

    /** Экран (мировые координаты камеры) для точки мира с учётом поворота */
    fun sx(x: Float, y: Float) = isoX(vX(x, y), vY(x, y))
    fun sy(x: Float, y: Float) = isoY(vX(x, y), vY(x, y))
    private fun isoX(a: Float, b: Float) = (a - b) * tw / 2
    private fun isoY(a: Float, b: Float) = (a + b) * th / 2

    /** Границы карты на экране (мировые координаты камеры) */
    val minX get() = -vh * tw / 2
    val maxX get() = vw * tw / 2
    val minY get() = 0f
    val maxY get() = (vw + vh) * th / 2

    /** Точка жителя на карте (мировые координаты камеры, на уровне груди) */
    fun agentPoint(a: Int, alpha: Float, out: com.badlogic.gdx.math.Vector2) {
        val sub = Tune.SUB.toFloat()
        val fx = (world.prevX[a] + (world.px[a] - world.prevX[a]) * alpha) / sub
        val fy = (world.prevY[a] + (world.py[a] - world.prevY[a]) * alpha) / sub
        out.set(sx(fx, fy), sy(fx, fy) + 9f)
    }

    private val pickPt = com.badlogic.gdx.math.Vector2()

    /** Ближайший видимый житель к точке касания в пределах radiusPx экранных пикселей (-1 - нет) */
    fun pickAgent(screenX: Int, screenY: Int, radiusPx: Int, alpha: Float): Int {
        tmp.set(screenX.toFloat(), screenY.toFloat(), 0f)
        camera.unproject(tmp)
        val tx = tmp.x; val ty = tmp.y
        tmp.set((screenX + radiusPx).toFloat(), screenY.toFloat(), 0f)
        camera.unproject(tmp)
        val r = tmp.x - tx
        var best = -1; var bestD = r * r
        for (a in 0 until world.agentCount) {
            if (world.state[a] == ST_INSIDE) continue
            agentPoint(a, alpha, pickPt)
            val dx = pickPt.x - tx; val dy = pickPt.y - ty
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = a }
        }
        return best
    }

    /** Экранная точка -> клетка мира (или -1, если за картой) */
    fun pick(screenX: Int, screenY: Int): Int {
        tmp.set(screenX.toFloat(), screenY.toFloat(), 0f)
        camera.unproject(tmp)
        val a = tmp.x / (tw / 2)   // vx - vy
        val b = tmp.y / (th / 2)   // vx + vy
        val fx = (a + b) / 2
        val fy = (b - a) / 2
        if (fx < 0 || fy < 0) return -1
        val x = fx.toInt(); val y = fy.toInt()
        if (x >= vw || y >= vh) return -1
        return cellOf(x, y)
    }

    /** Ближний к зрителю угол основания s x s с углом мира (bx, by) - в виде */
    private fun cornerX(bx: Int, by: Int, s: Int): Float = minOf(vX(bx.toFloat(), by.toFloat()), vX((bx + s).toFloat(), (by + s).toFloat()))
    private fun cornerY(bx: Int, by: Int, s: Int): Float = minOf(vY(bx.toFloat(), by.toFloat()), vY((bx + s).toFloat(), (by + s).toFloat()))

    // ---------- природа ----------

    /** Предметы на лугу, пятна луга и глубина воды - по хешу клетки, одинаково при каждом запуске */
    private fun refreshNature() {
        val w = world.w; val h = world.h
        val props = ArrayList<Int>(); val kinds = ArrayList<Int>()
        val decals = ArrayList<Int>(); val dKinds = ArrayList<Int>()
        fun near(x: Int, y: Int, type: Int) = (x > 0 && world.tiles[y * w + x - 1] == type) || (x < w - 1 && world.tiles[y * w + x + 1] == type) ||
            (y > 0 && world.tiles[(y - 1) * w + x] == type) || (y < h - 1 && world.tiles[(y + 1) * w + x] == type)
        for (y in 0 until h) for (x in 0 until w) {
            val c = y * w + x
            if (world.tiles[c] != GRASS) continue
            val hv = cellHash(x * 3 + 11, y * 5 + 7)
            val roll = hv % 100; val k = hv / 100
            when {
                roll < 45 && near(x, y, WATER) -> { props.add(c); kinds.add(1000 + k) }   // камыш у воды
                roll < 22 && near(x, y, FOREST) -> { props.add(c); kinds.add(k) }        // кусты у опушки
                roll < 3 -> { props.add(c); kinds.add(k) }                                 // куст в поле
                // цветы и камни реже (было 9% и 3% клеток): мелкий шум по всему лугу утомлял и спорил с жителями
                roll < 7 -> { decals.add(c); dKinds.add(k) }                               // цветы
                roll < 8 -> { decals.add(c); dKinds.add(1000 + k) }                        // камни
            }
        }
        propCells = props.toIntArray(); propKind = kinds.toIntArray()
        decalCells = decals.toIntArray(); decalKind = dKinds.toIntArray()

        // пятна луга: сглаженный шум с шагом в 6 клеток
        val lw = w + 2; val lh = h + 2
        meadow = FloatArray(lw * lh); meadowYellow = FloatArray(lw * lh)
        for (j in 0 until lh) for (i in 0 until lw) {
            val fx = i - 0.5f; val fy = j - 0.5f
            // мелкие пятна в 6 клеток и крупные в 18 - луг не плоский шум, а поляны светлее и темнее
            meadow[j * lw + i] = 0.93f + 0.13f * smoothNoise(fx / 6f, fy / 6f, 1) + 0.12f * smoothNoise(fx / 18f, fy / 18f, 3)
            meadowYellow[j * lw + i] = 0.4f * smoothNoise(fx / 9f, fy / 9f, 2) + 0.6f * smoothNoise(fx / 22f, fy / 22f, 4)
        }
        // глубина воды: шагов до суши (не больше 5), в узле - среднее четырёх клеток вокруг
        val dist = IntArray(w * h) { if (world.tiles[it] == WATER) 99 else 0 }
        repeat(5) {
            for (y in 0 until h) for (x in 0 until w) {
                val c = y * w + x
                if (dist[c] == 0) continue
                var m = dist[c]
                if (x > 0) m = minOf(m, dist[c - 1] + 1)
                if (x < w - 1) m = minOf(m, dist[c + 1] + 1)
                if (y > 0) m = minOf(m, dist[c - w] + 1)
                if (y < h - 1) m = minOf(m, dist[c + w] + 1)
                dist[c] = m
            }
        }
        waterShade = FloatArray((w + 1) * (h + 1))
        for (j in 0..h) for (i in 0..w) {
            var sum = 0
            for (k in 0 until 4) {
                val cx = (i - 1 + (k and 1)).coerceIn(0, w - 1); val cy = (j - 1 + (k shr 1)).coerceIn(0, h - 1)
                sum += minOf(dist[cy * w + cx], 5)
            }
            waterShade[j * (w + 1) + i] = 1.1f - 0.08f * sum / 4f
        }
        facingKnown = 0
    }

    /** Луг в точке мира (i - 0.5, j - 0.5) */
    private fun meadowAt(wx: Float, wy: Float, yellow: Boolean): Float {
        val i = Math.round(wx + 0.5f).coerceIn(0, world.w + 1); val j = Math.round(wy + 0.5f).coerceIn(0, world.h + 1)
        return (if (yellow) meadowYellow else meadow)[j * (world.w + 2) + i]
    }

    private fun waterAt(wx: Float, wy: Float): Float {
        val i = Math.round(wx).coerceIn(0, world.w); val j = Math.round(wy).coerceIn(0, world.h)
        return waterShade[j * (world.w + 1) + i]
    }

    /**
     * Разворот рыбака к воде: сторона, где больше воды в радиусе 4 (0: -y, 1: +x, 2: +y, 3: -x);
     * вода вплотную с этой стороны - вариант «на берегу» (мостки на воду).
     * Только для отрисовки: на симуляцию и сохранения не влияет; считается каждый кадр (только рыбаки - дёшево,
     * а номера снесённых зданий переиспользуются).
     */
    private fun updateFacing() {
        val n = world.buildingCount
        if (facing.size < n) { facing = facing.copyOf(n + 32); shore = shore.copyOf(n + 32) }
        for (b in 0 until n) {
            // здание смотрит туда, куда его повернул игрок; рыбак - сам к воде
            facing[b] = world.bDir[b]; shore[b] = false
            if (world.bDef[b] != fisherDef || world.bState[b] == B_GONE || !sprites.hasShore(fisherDef)) continue
            val s = world.def(b).size
            val bx = world.bX[b]; val by = world.bY[b]
            val side = IntArray(4)
            for (y in by - 4 until by + s + 4) for (x in bx - 4 until bx + s + 4) {
                if (x !in 0 until world.w || y !in 0 until world.h || world.tiles[y * world.w + x] != WATER) continue
                if (y < by) side[0]++
                if (x >= bx + s) side[1]++
                if (y >= by + s) side[2]++
                if (x < bx) side[3]++
            }
            var best = 0
            for (d in 1..3) if (side[d] > side[best]) best = d
            facing[b] = best
            // вода вплотную вдоль этой стороны
            var touch = 0
            for (k in 0 until s) {
                val (x, y) = when (best) { 0 -> bx + k to by - 1; 1 -> bx + s to by + k; 2 -> bx + k to by + s; else -> bx - 1 to by + k }
                if (x in 0 until world.w && y in 0 until world.h && world.tiles[y * world.w + x] == WATER) touch++
            }
            shore[b] = touch > 0
        }
        facingKnown = n
    }

    private fun buildingSprite(b: Int): Spr {
        val def = world.bDef[b]
        val f = if (b < facingKnown) facing[b] else 0
        return sprites.building(def, rot - f, winter, b < facingKnown && shore[b], world.bLevel[b])
    }

    /** Плавный шум 0..1 по решётке: значения в узлах из хеша, между ними - сглаженная смесь */
    private fun smoothNoise(x: Float, y: Float, seed: Int): Float {
        val x0 = Math.floor(x.toDouble()).toInt(); val y0 = Math.floor(y.toDouble()).toInt()
        val tx = x - x0; val ty = y - y0
        val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
        fun v(i: Int, j: Int) = cellHash(i * 31 + seed * 1013, j * 17 - seed * 271) / 32767f
        val a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * sx
        val b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * sx
        return a + (b - a) * sy
    }

    // ---------- кадр ----------

    /**
     * Отрисовка мира; ghostDef >= 0 - полупрозрачный предпросмотр здания в (ghostX, ghostY),
     * зелёный, если ставить можно, красный - если нельзя.
     */
    fun draw(batch: SpriteBatch, alpha: Float, ghostDef: Int = -1, ghostX: Int = 0, ghostY: Int = 0, ghostOk: Boolean = false, ghostDir: Int = 0) {
        camera.update()
        batch.projectionMatrix = camera.combined
        val hw = camera.viewportWidth * camera.zoom / 2
        val hh = camera.viewportHeight * camera.zoom / 2
        vl = camera.position.x - hw - tw * 2; vr = camera.position.x + hw + tw * 2
        vb = camera.position.y - hh - th * 8; vt = camera.position.y + hh + th * 2
        daylight()
        val dt = com.badlogic.gdx.Gdx.graphics.deltaTime
        time += dt
        updateTraffic()
        if (world.mapVersion != seenVersion) { seenVersion = world.mapVersion; refreshTrees() }
        updateFacing()
        // земля по сезону: осенью желтеет, зимой бледнеет и синеет
        when (world.season) {
            AUTUMN -> tint(batch, 1f, 0.88f, 0.68f, 1f)
            WINTER -> tint(batch, 0.86f, 0.92f, 1f, 1f)
            else -> tint(batch, 1f, 1f, 1f, 1f)
        }
        drawGround(batch)
        drawFlood(batch)
        drawResources(batch)
        drawLayerOverlay(batch, dt)
        drawTemplateGround(batch)
        if (ghostDef >= 0) drawOverlay(batch, ghostDef, ghostX, ghostY)
        // площадка призрака: зелёная - ставить можно, красная - нельзя
        if (ghostDef >= 0) {
            val gs = world.content.buildings[ghostDef].size
            Palette.ghost(ghostOk).let { footprint(batch, ghostX, ghostY, gs, it.r, it.g, it.b, 0.5f) }
            // клетка входа - светлая: видно, куда повёрнуто здание
            val e = world.entranceCell(ghostX, ghostY, gs, ghostDir)
            if (e in 0 until world.w * world.h) footprint(batch, e % world.w, e / world.w, 1, 1f, 1f, 0.85f, 0.7f)
        }
        drawRoadGhost(batch)
        drawGroundLights(batch)
        // выбранное: мигающая золотая рамка по краю площадки на земле (видна у входа и по бокам здания)
        if (selX >= 0) outlineRect(batch, selX.toFloat(), selY.toFloat(), selSize.toFloat(), 1f, 0.82f, 0.3f, 0.75f + 0.25f * kotlin.math.sin(time * 4f))
        tint(batch, 1f, 1f, 1f, 1f)
        drawObjects(batch, alpha)
        // и мягкое золотое свечение поверх (здание и деревья закрыли бы подсветку земли)
        if (selX >= 0) {
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE)
            footprint(batch, selX, selY, selSize, 1f, 0.8f, 0.35f, 0.3f + 0.14f * kotlin.math.sin(time * 4f))
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
        if (selAgent in 0 until world.agentCount && world.state[selAgent] != ST_INSIDE) {
            val dia = sprites.diamond
            if (dia != null) {
                agentPoint(selAgent, alpha, pickPt)
                batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE)
                batch.setColor(1f, 0.85f, 0.4f, 0.55f + 0.2f * kotlin.math.sin(time * 5f))
                sprites.draw(batch, dia, pickPt.x, pickPt.y - 9f, k = 0.5f)
                batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
                tint(batch, 1f, 1f, 1f, 1f)
            }
        }
        drawEffects(batch)
        drawWeather(batch)
        drawIcons(batch)
        if (ghostDef >= 0) {
            // нельзя: призрак бледный и серый, а красная - рамка площадки поверх всего
            // (красная заливка всего здания сливалась с красными крышами соседей)
            if (ghostOk) Palette.ghostTint(true).let { tint(batch, it.r, it.g, it.b, 0.75f) }
            else tint(batch, 0.78f, 0.76f, 0.76f, 0.5f)
            val s = world.content.buildings[ghostDef].size
            val shoreAuto = ghostDef == fisherDef && sprites.hasShore(fisherDef)
            sprites.draw(batch, sprites.building(ghostDef, if (shoreAuto) rot else rot - ghostDir, false), isoX(cornerX(ghostX, ghostY, s), cornerY(ghostX, ghostY, s)), isoY(cornerX(ghostX, ghostY, s), cornerY(ghostX, ghostY, s)))
            if (!ghostOk) Palette.ghost(false).let { outlineRect(batch, ghostX.toFloat(), ghostY.toFloat(), s.toFloat(), it.r, it.g, it.b, 0.8f + 0.2f * kotlin.math.sin(time * 5f)) }
            tint(batch, 1f, 1f, 1f, 1f)
        }
        drawTemplateGhosts(batch)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /**
     * Свет по времени суток: днём тёплый, на рассвете и закате золотой, ночью приглушённый синий
     * (k - доля дня: 0 ночь, 0.5 золотой час, 1 день).
     */
    private fun daylight() {
        val t = world.tick % Tune.TICKS_PER_DAY
        val fade = 120
        val k = when {
            t < fade -> t.toFloat() / fade
            t < Tune.NIGHT_START - fade -> 1f
            t < Tune.NIGHT_START -> 1f - (t - (Tune.NIGHT_START - fade)).toFloat() / fade
            else -> 0f
        }
        fun ramp(night: Float, gold: Float, day: Float) = if (k < 0.5f) night + (gold - night) * k * 2 else gold + (day - gold) * (k - 0.5f) * 2
        dark = 1f - k
        // ночь заметно темнее и синее - окна на её фоне светятся
        lightR = ramp(0.46f, 1f, 1f)
        lightG = ramp(0.5f, 0.82f, 0.98f)
        lightB = ramp(0.7f, 0.64f, 0.92f)
        // пасмурно: дождь и снегопад темнее и холоднее, туман - серее, засуха - теплее
        when (world.weather) {
            W_RAIN -> { lightR *= 0.84f; lightG *= 0.88f; lightB *= 0.95f }
            W_SNOW -> { lightR *= 0.9f; lightG *= 0.93f; lightB *= 0.98f }
            W_FOG -> { lightR *= 0.92f; lightG *= 0.93f; lightB *= 0.95f }
            W_DROUGHT -> { lightB *= 0.9f; lightG *= 0.97f }
        }
    }

    private fun tint(batch: SpriteBatch, r: Float, g: Float, b: Float, a: Float) {
        curR = r * lightR; curG = g * lightG; curB = b * lightB; curA = a
        batch.setColor(curR, curG, curB, a)
    }

    /** Клетки size x size от (x, y) ромбами одного цвета */
    private fun footprint(batch: SpriteBatch, x: Int, y: Int, size: Int, r: Float, g: Float, b: Float, a: Float) {
        val dia = sprites.diamond ?: return
        batch.setColor(r, g, b, a)
        for (yy in y until y + size) for (xx in x until x + size) {
            if (xx !in 0 until world.w || yy !in 0 until world.h) continue
            val ax = cornerX(xx, yy, 1); val ay = cornerY(xx, yy, 1)
            sprites.draw(batch, dia, isoX(ax, ay), isoY(ax, ay))
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Рамка по краю квадрата клеток мира (x, y, size) - линии толщиной ~2.6 точки при любом масштабе */
    private fun outlineRect(batch: SpriteBatch, x: Float, y: Float, size: Float, r: Float, g: Float, b: Float, a: Float) {
        batch.setColor(r, g, b, a)
        val lw = 2.6f * camera.zoom
        val xs = floatArrayOf(x, x + size, x + size, x); val ys = floatArrayOf(y, y, y + size, y + size)
        for (k in 0 until 4) {
            val x0 = sx(xs[k], ys[k]); val y0 = sy(xs[k], ys[k])
            val x1 = sx(xs[(k + 1) % 4], ys[(k + 1) % 4]); val y1 = sy(xs[(k + 1) % 4], ys[(k + 1) % 4])
            val dx = x1 - x0; val dy = y1 - y0
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            batch.draw(sprites.white, x0, y0 - lw / 2, 0f, lw / 2, len, lw, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees)
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    private fun drawRoadGhost(batch: SpriteBatch) {
        val cells = roadCells ?: return
        val ok = roadOk ?: return
        val kind = if (lineWall && lineKind == 0) 1 else lineKind
        val dia = sprites.diamond
        val spr = when (kind) {
            1, 3 -> dia ?: sprites.ground[ROAD]!![0]
            2 -> sprites.ground[PAVED]!![0]
            else -> sprites.ground[ROAD]!![0]
        }
        for (i in cells.indices) {
            val x = cells[i] % world.w; val y = cells[i] / world.w
            if (!ok[i]) Palette.ghostTint(false).let { tint(batch, it.r, it.g, it.b, 0.6f) }
            else when (kind) {
                1 -> tint(batch, 0.75f, 0.55f, 0.3f, 0.85f)
                2 -> if (sprites.hasPaved) tint(batch, 0.95f, 1f, 1f, 0.85f) else tint(batch, 0.8f, 0.85f, 0.95f, 0.85f)
                3 -> tint(batch, 0.62f, 0.45f, 0.26f, 0.85f)
                else -> tint(batch, 0.9f, 1f, 0.8f, 0.85f)
            }
            val cx = cornerX(x, y, 1); val cy = cornerY(x, y, 1)
            sprites.draw(batch, spr, isoX(cx, cy), isoY(cx, cy))
        }
    }

    // ---------- слои-наложения ----------

    /** Тепловая карта носильщиков: каждый кадр +тики клеткам жителей с грузом в пути, затухание ~20 с игры */
    private var lastTrafficTick = -1
    private fun updateTraffic() {
        val n = world.w * world.h
        if (traffic.size != n) { traffic = FloatArray(n); lastTrafficTick = -1 }
        val tick = world.tick
        if (lastTrafficTick < 0 || tick < lastTrafficTick) { lastTrafficTick = tick; return }
        val passed = minOf(tick - lastTrafficTick, 40)
        if (passed == 0) return
        lastTrafficTick = tick
        val sub = Tune.SUB
        if (wear.size != n) wear = FloatArray(n)
        for (a in 0 until world.agentCount) {
            if (world.state[a] != ST_MOVE) continue
            val x = world.px[a] / sub; val y = world.py[a] / sub
            if (x !in 0 until world.w || y !in 0 until world.h) continue
            wear[y * world.w + x] += passed.toFloat()
            if (world.carryN[a] > 0) traffic[y * world.w + x] += passed.toFloat()
        }
        trafficTimer += passed
        if (trafficTimer >= 10f) {
            val k = kotlin.math.exp(-trafficTimer / 400f)
            for (i in 0 until n) traffic[i] *= k
            // тропы зарастают за пару суток, если по ним не ходят
            val kw = kotlin.math.exp(-trafficTimer / WEAR_FADE)
            for (i in 0 until n) wear[i] = minOf(wear[i] * kw, WEAR_FULL * 1.5f)
            trafficTimer = 0f
        }
    }

    private val serviceKinds = HashMap<String, Int>()

    /** Покрытие услугами по правилу World.served (центры в половинах клеток), раз в ~0.5 с: битовая маска видов услуг */
    private fun updateCoverage(dt: Float) {
        val n = world.w * world.h
        ovTimer -= dt
        val key = overlayService ?: ""
        if (ovCover.size != n) { ovCover = IntArray(n); ovTimer = 0f }
        if (ovTimer > 0f && key == ovKey) return
        ovTimer = 0.5f; ovKey = key
        java.util.Arrays.fill(ovCover, 0)
        for (o in 0 until world.buildingCount) {
            if (world.bState[o] != B_ACTIVE) continue
            val d = world.def(o)
            val sv = d.serves ?: continue
            if (overlayService != null && sv != overlayService) continue
            if (world.bFire[o] > 0 || world.bUnpaid[o] != 0) continue
            if (!d.isDecor && world.bWorkers[o] == 0) continue
            val bit = 1 shl ((serviceKinds.getOrPut(sv) { serviceKinds.size }) and 30)
            val ocx = world.bX[o] * 2 + d.size; val ocy = world.bY[o] * 2 + d.size
            val r2 = world.serveRadius(o) * 2
            val x0 = maxOf(0, (ocx - r2 - 1) / 2 - 1); val x1 = minOf(world.w - 1, (ocx + r2) / 2 + 1)
            val y0 = maxOf(0, (ocy - r2 - 1) / 2 - 1); val y1 = minOf(world.h - 1, (ocy + r2) / 2 + 1)
            for (y in y0..y1) for (x in x0..x1) {
                if (maxOf(Math.abs(ocx - (x * 2 + 1)), Math.abs(ocy - (y * 2 + 1))) <= r2) ovCover[y * world.w + x] = ovCover[y * world.w + x] or bit
            }
        }
    }

    private fun drawLayerOverlay(batch: SpriteBatch, dt: Float) {
        val ov = overlay
        if (ov == OV_NONE) return
        val dia = sprites.diamond ?: return
        if (ov == OV_SERVICE) updateCoverage(dt)
        val w = world.w
        forVisible(vw - 1, vh - 1) { x, y ->
            val c = cellOf(x, y)
            var r = 0f; var g = 0f; var b = 0f; var a = 0f
            val t = world.tiles[c].toInt()
            when (ov) {
                OV_HAPPY -> a = 0.35f
                OV_SERVICE -> if (c < ovCover.size) {
                    var m = ovCover[c]; var k = 0
                    while (m != 0) { k += m and 1; m = m ushr 1 }
                    if (k > 0) {
                        val q = if (overlayService != null) 1f else minOf(1f, k / 4f)
                        r = 0.3f; g = 0.7f; b = 1f; a = 0.18f + 0.3f * q
                    }
                }
                OV_TRAFFIC -> {
                    val q = minOf(1f, traffic[c] / 30f)
                    if (q > 0.03f) {
                        if (Storage.colorblind) { val c = Palette.ramp(1f - q); r = c.r; g = c.g; b = c.b } else { r = 1f; g = 0.75f - 0.6f * q; b = 0.1f }
                        a = 0.12f + 0.55f * q
                    }
                }
                OV_SOIL -> if (t != WATER && t != BRIDGE) {
                    val f = world.fert[c] / 100f; val gw = world.ground[c] / 100f
                    if (Storage.colorblind) { val c = Palette.ramp(f); r = c.r * (1f - 0.6f * gw) + 0.6f * gw * 0.6f; g = c.g * (1f - 0.6f * gw) + 0.6f * gw * 0.35f; b = c.b * (1f - 0.6f * gw) + 0.6f * gw * 0.9f }
                    else { r = 0.45f * (1f - f) * (1f - gw); g = 0.25f + 0.65f * f * (1f - 0.5f * gw); b = 0.2f + 0.8f * gw }
                    a = 0.15f + 0.35f * maxOf(f, gw)
                    // пойма (заливает весной) - синей шахматкой поверх
                    if (world.low[c] && ((x + y) and 1) == 0) { r = 0.2f; g = 0.45f; b = 1f; a = 0.55f }
                }
            }
            if (a > 0f) {
                batch.setColor(r, g, b, a)
                val ax = cornerX(c % w, c / w, 1); val ay = cornerY(c % w, c / w, 1)
                sprites.draw(batch, dia, isoX(ax, ay), isoY(ax, ay))
            }
        }
        if (ov == OV_HAPPY) for (b in 0 until world.buildingCount) {
            if (world.bState[b] != B_ACTIVE || !world.def(b).isHouse) continue
            val h = world.bHappy[b]
            val hc = Palette.happy(h)
            footprint(batch, world.bX[b], world.bY[b], world.def(b).size, hc.r, hc.g, hc.b, if (h < 70) 0.6f else 0.55f)
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    // ---------- шаблон ----------

    /** Рамка выделения и дороги шаблона на земле */
    private fun drawTemplateGround(batch: SpriteBatch) {
        val sr = selRect
        if (sr != null) {
            val x0 = minOf(sr[0], sr[2]).coerceAtLeast(0); val x1 = maxOf(sr[0], sr[2]).coerceAtMost(world.w - 1)
            val y0 = minOf(sr[1], sr[3]).coerceAtLeast(0); val y1 = maxOf(sr[1], sr[3]).coerceAtMost(world.h - 1)
            for (y in y0..y1) for (x in x0..x1) footprint(batch, x, y, 1, 1f, 0.85f, 0.25f, 0.35f)
        }
        val cells = tplRoads ?: return
        val paved = tplPaved
        val road = sprites.ground[ROAD]!![0]
        val pav = sprites.ground[PAVED]!![0]
        for (i in cells.indices) {
            val x = cells[i] % world.w; val y = cells[i] / world.w
            val p = paved != null && i < paved.size && paved[i]
            if (p && !sprites.hasPaved) tint(batch, 0.8f, 0.85f, 0.95f, 0.8f) else tint(batch, 0.9f, 1f, 0.8f, 0.8f)
            val cx = cornerX(x, y, 1); val cy = cornerY(x, y, 1)
            sprites.draw(batch, if (p) pav else road, isoX(cx, cy), isoY(cx, cy))
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Призраки зданий шаблона: площадка зелёная или красная, спрайт полупрозрачный */
    private fun drawTemplateGhosts(batch: SpriteBatch) {
        val defs = tplDefs ?: return
        val xs = tplX ?: return; val ys = tplY ?: return
        val dirs = tplDir; val oks = tplOk
        for (i in defs.indices) {
            if (i >= xs.size || i >= ys.size) break
            val def = defs[i]
            if (def !in world.content.buildings.indices) continue
            val ok = oks == null || i >= oks.size || oks[i]
            val dir = if (dirs != null && i < dirs.size) dirs[i] else 0
            val s = world.content.buildings[def].size
            Palette.ghost(ok).let { footprint(batch, xs[i], ys[i], s, it.r, it.g, it.b, 0.5f) }
            val cx = cornerX(xs[i], ys[i], s); val cy = cornerY(xs[i], ys[i], s)
            if (!visible(isoX(cx, cy), isoY(cx, cy))) continue
            if (ok) Palette.ghostTint(true).let { tint(batch, it.r, it.g, it.b, 0.75f) } else tint(batch, 0.78f, 0.76f, 0.76f, 0.5f)
            val shoreAuto = def == fisherDef && sprites.hasShore(fisherDef)
            sprites.draw(batch, sprites.building(def, if (shoreAuto) rot else rot - dir, false), isoX(cx, cy), isoY(cx, cy))
            if (!ok) Palette.ghost(false).let { outlineRect(batch, xs[i].toFloat(), ys[i].toFloat(), s.toFloat(), it.r, it.g, it.b, 0.85f) }
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    // ---------- мосты ----------

    /** Мост поверх воды: доски вдоль той оси, где соседи - мост или суша (с учётом поворота карты) */
    private fun drawBridges(batch: SpriteBatch) {
        val dia = sprites.diamond
        val set = if (winter) sprites.bridgesSnow else sprites.bridges
        val w = world.w; val h = world.h
        fun land(x: Int, y: Int) = x in 0 until w && y in 0 until h && world.tiles[y * w + x].toInt() != WATER
        tint(batch, 1f, 1f, 1f, 1f)
        forVisible(vw - 1, vh - 1) { x, y ->
            val c = cellOf(x, y)
            if (world.tiles[c].toInt() == BRIDGE) {
                val cx = c % w; val cy = c / w
                val alongX = land(cx - 1, cy) && land(cx + 1, cy)
                val alongY = land(cx, cy - 1) && land(cx, cy + 1)
                // конец моста (с одной стороны вода): ось - та, где соседний мост
                fun br(x: Int, y: Int) = x in 0 until w && y in 0 until h && world.tiles[y * w + x].toInt() == BRIDGE
                val worldX = if (alongX != alongY) alongX else (br(cx - 1, cy) || br(cx + 1, cy)) || !(br(cx, cy - 1) || br(cx, cy + 1))
                val idx = if (worldX == (rot and 1 == 0)) 0 else 1
                val spr = set[idx] ?: set[1 - idx]
                val px = isoX(x.toFloat(), y.toFloat()); val py = isoY(x.toFloat(), y.toFloat())
                if (spr != null) sprites.draw(batch, spr, px, py)
                else if (dia != null) {
                    batch.setColor(curR * 0.62f, curG * 0.45f, curB * 0.26f, 1f)
                    sprites.draw(batch, dia, px, py)
                    tint(batch, 1f, 1f, 1f, 1f)
                }
            }
        }
    }

    /** Пни (полная графика) и косяки рыбы на воде (всегда - это игровая подсказка) */
    private fun drawResources(batch: SpriteBatch) {
        val stumps = sprites.stumps
        val fish = sprites.fishSpots
        forVisible(vw - 1, vh - 1) { x, y ->
            val c = cellOf(x, y)
            val t = world.tiles[c]
            if (t == GRASS && world.grow[c] > 0 && stumps.isNotEmpty() && !sprites.low) {
                val fx = x + 0.5f; val fy = y + 0.5f
                sprites.draw(batch, stumps[c % stumps.size], isoX(fx, fy), isoY(fx, fy) - 2f)
            } else if (t == WATER && world.res[c] > 0 && fish.isNotEmpty()) {
                val fx = x + 0.5f; val fy = y + 0.5f
                // заметнее, где рыбы больше; кадры вразнобой у соседних клеток
                tint(batch, 1f, 1f, 1f, 0.35f + 0.65f * world.res[c] / maxOf(1, world.resMax[c]))
                val f = if (sprites.low) 0 else ((time * 3f).toInt() + c) % fish.size
                sprites.draw(batch, fish[f], isoX(fx, fy), isoY(fx, fy))
            }
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /**
     * Подсветка при постановке: ферма, сад, пастбище - плодородие (зелёное - лучше), колодец - подземная вода (синее - больше);
     * добытчик - клетки с ресурсом в радиусе (жёлтые) и сам радиус.
     */
    private fun drawOverlay(batch: SpriteBatch, def: Int, gx: Int, gy: Int) {
        val d = world.content.buildings[def]
        val dia = sprites.diamond ?: return
        val soil = d.soil
        if (soil == null && d.gatherCode == G_NONE) return
        val r = if (soil != null) 4 else d.needRadius
        val cx = gx + d.size / 2; val cy = gy + d.size / 2
        for (yy in cy - r..cy + r) for (xx in cx - r..cx + r) {
            if (xx !in 0 until world.w || yy !in 0 until world.h) continue
            val c = yy * world.w + xx
            when {
                soil == "fertility" -> { val f = world.fert[c] / 100f; if (Storage.colorblind) Palette.ramp(f).let { batch.setColor(it.r, it.g, it.b, 0.42f) } else batch.setColor(1f - f * 0.8f, 0.45f + f * 0.55f, 0.15f, 0.42f) }
                soil == "groundwater" -> { val f = world.ground[c] / 100f; if (Storage.colorblind) batch.setColor(0.6f, 0.35f + f * 0.1f, 0.85f + f * 0.1f, 0.2f + f * 0.35f) else batch.setColor(0.25f, 0.45f + f * 0.3f, 0.6f + f * 0.4f, 0.2f + f * 0.35f) }
                world.hasResource(c, d.gatherCode) -> batch.setColor(1f, 0.85f, 0.25f, 0.55f)
                else -> batch.setColor(1f, 1f, 1f, 0.1f)
            }
            val ax = cornerX(xx, yy, 1); val ay = cornerY(xx, yy, 1)
            sprites.draw(batch, dia, isoX(ax, ay), isoY(ax, ay))
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Перемешанный номер клетки для выбора варианта (простая формула выстраивает варианты в полосы) */
    private fun cellHash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h ushr 16) and 0x7fff
    }

    private fun visible(px: Float, py: Float) = px > vl && px < vr && py > vb && py < vt

    /** Видимые диагонали вида: a = vx - vy и b = vx + vy клеток, попадающих на экран */
    private var aMin = 0; private var aMax = 0; private var bMin = 0; private var bMax = 0

    private fun drawGround(batch: SpriteBatch) {
        aMin = Math.floor((vl / (tw / 2)).toDouble()).toInt() - 1; aMax = Math.ceil((vr / (tw / 2)).toDouble()).toInt() + 1
        bMin = Math.floor((vb / (th / 2)).toDouble()).toInt() - 2; bMax = Math.ceil((vt / (th / 2)).toDouble()).toInt() + 1
        if (sprites.low) drawGroundSimple(batch) else drawGroundFull(batch)
        drawBridges(batch)
    }

    /** Клетки вида (x, y) в 0..maxX x 0..maxY, попадающие на экран */
    private inline fun forVisible(maxX: Int, maxY: Int, f: (Int, Int) -> Unit) {
        for (b in bMin..bMax) {
            var a = aMin + ((b - aMin) and 1)   // a = x - y и b = x + y одной чётности
            while (a <= aMax) {
                val x = (a + b) / 2; val y = (b - a) / 2
                if (x in 0..maxX && y in 0..maxY) f(x, y)
                a += 2
            }
        }
    }

    /** Цвет вершины: цвет кисти x k; yr - желтизна луга (0..1) */
    private fun vcol(k: Float, yr: Float = 0f): Float =
        Color.toFloatBits(minOf(1f, curR * k * (1f + yr * 0.05f)), minOf(1f, curG * k), minOf(1f, curB * k * (1f - yr * 0.12f)), curA)

    /** Ромб спрайта с цветами в углах прямоугольника: левый нижний, левый верхний, правый верхний, правый нижний */
    private fun quad(batch: SpriteBatch, t: Spr, x: Float, y: Float, c0: Float, c1: Float, c2: Float, c3: Float) {
        val r = t.r
        val x0 = x - t.ax - GROUND_PAD; val y0 = y - t.ay - GROUND_PAD / 2
        val x1 = x0 + t.w + GROUND_PAD * 2; val y1 = y0 + t.h + GROUND_PAD
        val v = verts
        v[0] = x0; v[1] = y0; v[2] = c0; v[3] = r.u; v[4] = r.v2
        v[5] = x0; v[6] = y1; v[7] = c1; v[8] = r.u; v[9] = r.v
        v[10] = x1; v[11] = y1; v[12] = c2; v[13] = r.u2; v[14] = r.v
        v[15] = x1; v[16] = y0; v[17] = c3; v[18] = r.u2; v[19] = r.v2
        batch.draw(r.texture, v, 0, 20)
    }

    /** Цвет воды в узле вида (a, b) */
    private fun waterCol(a: Int, b: Int): Float {
        val wx = wX(a.toFloat(), b.toFloat()); val wy = wY(a.toFloat(), b.toFloat())
        return outCol(waterAt(wx, wy), outsideAt(wx, wy))
    }

    /**
     * Цвет за границей поселения: темнее и бледнее (зелёный и красный гаснут сильнее синего - земля сереет, как в дымке),
     * а не просто тень - иначе край читался как луч прожектора. o - доля «снаружи» 0..1.
     */
    private fun outCol(k: Float, o: Float, yr: Float = 0f): Float {
        if (o <= 0f) return vcol(k, yr)
        val kr = k * (1f - OUT_R * o); val kg = k * (1f - OUT_G * o); val kb = k * (1f - OUT_B * o)
        return Color.toFloatBits(minOf(1f, curR * kr * (1f + yr * 0.05f)), minOf(1f, curG * kg), minOf(1f, curB * kb * (1f - yr * 0.12f)), curA)
    }

    private fun meadowCol(a: Float, b: Float): Float {
        val wx = wX(a, b); val wy = wY(a, b)
        val o = outsideAt(wx, wy)
        val k = meadowAt(wx, wy, false)
        val t = wornAt(wx, wy)
        if (t <= 0.01f) return outCol(k, o, meadowAt(wx, wy, true))
        // вытоптанная трава: желтее и бурее
        val r = curR * k * (1f - OUT_R * o) * (1f + 0.06f * t); val g = curG * k * (1f - OUT_G * o) * (1f - 0.12f * t)
        val bl = curB * k * (1f - OUT_B * o) * (1f - 0.38f * t)
        return Color.toFloatBits(minOf(1f, r), minOf(1f, g), minOf(1f, bl), curA)
    }

    /** Вытоптанность в узле мира: среднее четырёх клеток вокруг, 0..1 */
    private fun wornAt(wx: Float, wy: Float): Float {
        if (wear.isEmpty()) return 0f
        val w = world.w
        val x0 = Math.floor((wx - 0.5f).toDouble()).toInt(); val y0 = Math.floor((wy - 0.5f).toDouble()).toInt()
        var sum = 0f
        for (k in 0 until 4) {
            val x = (x0 + (k and 1)).coerceIn(0, w - 1); val y = (y0 + (k shr 1)).coerceIn(0, world.h - 1)
            sum += wear[y * w + x]
        }
        val v = sum / 4f / WEAR_FULL
        return if (v >= 1f) 1f else kotlin.math.sqrt(v)
    }

    /** Цвет узла вида (a, b) для слоёв земли: за границей поселения темнее */
    private fun layerCol(a: Int, b: Int): Float = outCol(1f, outsideAt(wX(a.toFloat(), b.toFloat()), wY(a.toFloat(), b.toFloat())))

    /** Насколько точка мира за границей поселения: 0 внутри, 1 снаружи, между - узкий плавный край */
    private fun outsideAt(wx: Float, wy: Float): Float {
        if (borderSeen != world.borderVer || borderField.isEmpty()) buildBorderField()
        val fw = world.w * 2 + 1
        val i = Math.round(wx * 2).coerceIn(0, world.w * 2); val j = Math.round(wy * 2).coerceIn(0, world.h * 2)
        return 1f - borderField[j * fw + i]
    }

    /**
     * Плавный край границы: граница - круги вокруг ратуши и зданий с полем border (как World.computeBorder);
     * в узлах мира с шагом в полклетки - 1 внутри, 0 снаружи, переход шириной ~0.7 клетки по расстоянию до круга (уже - читается как край, а не тень)
     * (по клеткам выходила лесенка).
     */
    private var borderField = FloatArray(0)
    private var borderSeen = -1
    private fun buildBorderField() {
        borderSeen = world.borderVer
        val fw = world.w * 2 + 1; val fh = world.h * 2 + 1
        val inside = FloatArray(fw * fh) { -9f }
        for (b in 0 until world.buildingCount) {
            if (world.bState[b] != B_ACTIVE) continue
            val def = world.def(b)
            val r = when {
                b == world.hall -> Tune.BORDER_HALL + Tune.BORDER_LEVEL * world.level
                def.border > 0 -> def.border + Tune.LEVEL_BORDER * (world.bLevel[b] - 1)
                else -> continue
            }
            // клетка внутри, если её центр в круге r - край круга в мире на r + 0.5 от центра здания
            val cx = world.bX[b] + def.size / 2f; val cy = world.bY[b] + def.size / 2f
            val edge = r + 0.5f
            val i0 = maxOf(0, ((cx - edge - 1) * 2).toInt()); val i1 = minOf(fw - 1, ((cx + edge + 1) * 2).toInt() + 1)
            val j0 = maxOf(0, ((cy - edge - 1) * 2).toInt()); val j1 = minOf(fh - 1, ((cy + edge + 1) * 2).toInt() + 1)
            for (j in j0..j1) for (i in i0..i1) {
                val dx = i / 2f - cx; val dy = j / 2f - cy
                val d = edge - kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > inside[j * fw + i]) inside[j * fw + i] = d
            }
        }
        borderField = FloatArray(fw * fh) { val v = ((inside[it] + 0.35f) / 0.7f).coerceIn(0f, 1f); v * v * (3 - 2 * v) }
    }

    /**
     * Полная графика: луг с плавными пятнами цвета по всей карте, сверху слои по двойной сетке
     * (лес, камень, дорога, вода с берегом), затем цветы и камни.
     */
    private fun drawGroundFull(batch: SpriteBatch) {
        val grass = (if (winter) sprites.solidSnow[GRASS] else null) ?: sprites.solid[GRASS] ?: sprites.ground[GRASS]!!
        forVisible(vw - 1, vh - 1) { x, y ->
            // углы прямоугольника клетки - узлы (x - 0.5, y + 0.5), (x + 0.5, y + 1.5), (x + 1.5, y + 0.5), (x + 0.5, y - 0.5)
            val fx = x.toFloat(); val fy = y.toFloat()
            quad(batch, grass[cellHash(x, y) % grass.size], isoX(fx, fy), isoY(fx, fy),
                meadowCol(fx - 0.5f, fy + 0.5f), meadowCol(fx + 0.5f, fy + 1.5f), meadowCol(fx + 1.5f, fy + 0.5f), meadowCol(fx + 0.5f, fy - 0.5f))
        }
        fun full(type: Int) = (if (winter) sprites.solidSnow[type] else null) ?: sprites.solid[type] ?: sprites.ground[type]!!
        for (pass in 0..1) drawLayer(batch, if (winter) "forest_snow" else "forest", full(FOREST), false, pass == 0) { it == FOREST || it == ROCK }
        for (pass in 0..1) drawLayer(batch, "rock", full(ROCK), false, pass == 0) { it == ROCK }
        for (pass in 0..1) drawLayer(batch, "road", full(ROAD), false, pass == 0) { it == ROAD || it == PAVED }
        // мощёная поверх дороги; без своих спрайтов - серая дорога
        val sr = curR; val sg = curG; val sb = curB
        if (!sprites.hasPaved) { curR *= 0.8f; curG *= 0.85f; curB *= 0.95f }
        for (pass in 0..1) drawLayer(batch, "paved", full(PAVED), false, pass == 0) { it == PAVED }
        curR = sr; curG = sg; curB = sb
        for (pass in 0..1) drawLayer(batch, if (winter) "water_snow" else "water", full(WATER), true, pass == 0) { it == WATER || it == BRIDGE }
        // цветы (не зимой) и камни на лугу
        val w = world.w
        for (i in decalCells.indices) {
            val c = decalCells[i]
            if (world.tiles[c] != GRASS) continue
            val k = decalKind[i]
            if (k < 1000 && winter) continue
            val list = if (k >= 1000) sprites.stones else sprites.flowers
            if (list.isEmpty()) continue
            val fx = c % w + 0.5f; val fy = c / w + 0.5f
            val px = sx(fx, fy); val py = sy(fx, fy)
            if (!visible(px, py)) continue
            sprites.draw(batch, list[(k % 1000) % list.size], px, py - 3f)
        }
    }

    /**
     * Слой по двойной сетке вида: в узле (x, y) ромб на четырёх клетках (x-1..x, y-1..y), спрайт по маске
     * «какие из них в слое»; все четыре - сплошная клетка слоя (у воды - кадры бликов и темнее вглубь).
     * Сплошные клетки (fullPass) рисуются первыми: их углы заходят на соседние ромбы и закрывают стыки,
     * а края слоя потом ложатся поверх.
     */
    private inline fun drawLayer(batch: SpriteBatch, layer: String, full: Array<Spr>, water: Boolean, fullPass: Boolean, inLayer: (Int) -> Boolean) {
        val masks = sprites.dual[layer] ?: return
        val mw = vw; val mh = vh
        val nv = Sprites.DUAL_VARIANTS
        forVisible(mw, mh) { x, y ->
            val x0 = (x - 1).coerceAtLeast(0); val x1 = x.coerceAtMost(mw - 1)
            val y0 = (y - 1).coerceAtLeast(0); val y1 = y.coerceAtMost(mh - 1)
            var m = 0
            if (inLayer(world.tiles[cellOf(x0, y0)].toInt())) m = m or 1
            if (inLayer(world.tiles[cellOf(x1, y0)].toInt())) m = m or 2
            if (inLayer(world.tiles[cellOf(x0, y1)].toInt())) m = m or 4
            if (inLayer(world.tiles[cellOf(x1, y1)].toInt())) m = m or 8
            if (m != 0 && (m == 15) == fullPass) {
                val hv = cellHash(x + 101, y + 57)
                val t = if (m == 15) {
                    if (water) full[if (sprites.low) 0 else (hv + world.tick / WATER_FRAME) % full.size] else full[hv % full.size]
                } else masks[m * nv + hv % nv]
                if (t != null) {
                    val fx = x - 0.5f; val fy = y - 0.5f
                    val px = isoX(fx, fy); val py = isoY(fx, fy)
                    if (water) {
                        // углы прямоугольника - узлы вида (x-1, y), (x, y+1), (x+1, y), (x, y-1)
                        quad(batch, t, px, py, waterCol(x - 1, y), waterCol(x, y + 1), waterCol(x + 1, y), waterCol(x, y - 1))
                    } else quad(batch, t, px, py, layerCol(x - 1, y), layerCol(x, y + 1), layerCol(x + 1, y), layerCol(x, y - 1))
                }
            }
        }
    }

    /** Лёгкая графика: клетка - один спрайт, вода без бликов */
    private fun drawGroundSimple(batch: SpriteBatch) {
        forVisible(vw - 1, vh - 1) { x, y ->
            val px = isoX(x.toFloat(), y.toFloat())
            val py = isoY(x.toFloat(), y.toFloat())
            val type = world.tiles[cellOf(x, y)].toInt()
            val variants = (if (winter) sprites.groundSnow[type] else null) ?: sprites.ground[type] ?: sprites.ground[GRASS]!!
            val t = variants[cellHash(x, y) % variants.size]
            val k = if (world.border[cellOf(x, y)]) 1f else 1f - OUTSIDE_DARK
            if (type == PAVED && !sprites.hasPaved) batch.setColor(curR * k * 0.8f, curG * k * 0.85f, curB * k * 0.95f, curA)
            else batch.setColor(curR * k, curG * k, curB * k, curA)
            // чуть шире клетки: углы соседних ромбов перекрываются, без просветов на стыках
            batch.draw(t.r, px - t.ax - GROUND_PAD, py - t.ay - GROUND_PAD / 2, t.w + GROUND_PAD * 2, t.h + GROUND_PAD)
        }
    }

    /** Ключ сортировки = (2047 - глубина в 1/8 клетки) shl 20 | номер; номера: жители, здания, деревья, предметы */
    private fun drawObjects(batch: SpriteBatch, alpha: Float) {
        count = 0
        val agents = world.agentCount
        val sub = Tune.SUB.toFloat()
        for (a in 0 until agents) {
            if (world.state[a] == ST_INSIDE) continue
            val fx = (world.prevX[a] + (world.px[a] - world.prevX[a]) * alpha) / sub
            val fy = (world.prevY[a] + (world.py[a] - world.prevY[a]) * alpha) / sub
            if (visible(sx(fx, fy), sy(fx, fy))) add(vX(fx, fy) + vY(fx, fy), a)
        }
        val bCount = world.buildingCount
        for (b in 0 until bCount) {
            if (world.bState[b] == B_GONE) continue
            val s = world.content.buildings[world.bDef[b]].size
            val cx = cornerX(world.bX[b], world.bY[b], s); val cy = cornerY(world.bX[b], world.bY[b], s)
            if (visible(isoX(cx, cy), isoY(cx, cy))) add(cx + cy + s, agents + b)
        }
        val trees0 = agents + bCount
        for (t in treeCells.indices) {
            val c = treeCells[t]
            val fx = c % world.w + 0.5f + jitter(c, 0); val fy = c / world.w + 0.5f + jitter(c, 1)
            if (visible(sx(fx, fy), sy(fx, fy))) add(vX(fx, fy) + vY(fx, fy), trees0 + t)
        }
        val props0 = trees0 + treeCells.size
        if (!sprites.low) for (p in propCells.indices) {
            val c = propCells[p]
            if (world.tiles[c] != GRASS) continue
            val fx = c % world.w + 0.5f + jitter(c, 2); val fy = c / world.w + 0.5f + jitter(c, 3)
            if (visible(sx(fx, fy), sy(fx, fy))) add(vX(fx, fy) + vY(fx, fy), props0 + p)
        }
        // стены - как здания 1x1; угрозы - по позиции, как жители
        val walls0 = props0 + propCells.size
        for (k in wallCells.indices) {
            val c = wallCells[k]
            val cx = cornerX(c % world.w, c / world.w, 1); val cy = cornerY(c % world.w, c / world.w, 1)
            if (visible(isoX(cx, cy), isoY(cx, cy))) add(cx + cy + 1, walls0 + k)
        }
        val raids0 = walls0 + wallCells.size
        for (i in 0 until Tune.MAX_RAIDS) {
            if (world.rType[i] == 0) continue
            val fx = world.rX[i] / sub; val fy = world.rY[i] / sub
            if (visible(sx(fx, fy), sy(fx, fy))) add(vX(fx, fy) + vY(fx, fy), raids0 + i)
        }
        // дети играют у своих домов (днём), повозки соседей едут к краю карты и обратно
        val kids0 = raids0 + Tune.MAX_RAIDS
        kidCount = 0
        if (!world.isNight) for (b in 0 until bCount) {
            if (world.bState[b] != B_ACTIVE || !world.def(b).isHouse) continue
            for (k in 0 until Tune.KIDS_MAX) {
                if (world.kidAge[b * Tune.KIDS_MAX + k] == 0) continue
                kidPos(b, k, time)
                if (!visible(sx(kidPt[0], kidPt[1]), sy(kidPt[0], kidPt[1]))) continue
                if (kidCount == kidB.size) { kidB = kidB.copyOf(kidCount * 2); kidK = kidK.copyOf(kidCount * 2) }
                kidB[kidCount] = b; kidK[kidCount] = k
                add(vX(kidPt[0], kidPt[1]) + vY(kidPt[0], kidPt[1]), kids0 + kidCount)
                kidCount++
            }
        }
        val wagons0 = kids0 + kidCount
        for (i in 0 until world.nbCount) {
            if (world.wgState[i] != 1 && world.wgState[i] != 3) continue
            val fx = world.wgX[i] / sub; val fy = world.wgY[i] / sub
            if (visible(sx(fx, fy), sy(fx, fy))) add(vX(fx, fy) + vY(fx, fy), wagons0 + i)
        }
        java.util.Arrays.sort(keys, 0, count)
        objectsDrawn = count

        for (i in 0 until count) {
            val id = keys[i] and 0xFFFFF
            when {
                id < agents -> drawPerson(batch, id, alpha)
                id < trees0 -> {
                    val b = id - agents
                    val s = world.content.buildings[world.bDef[b]].size
                    val cx = cornerX(world.bX[b], world.bY[b], s); val cy = cornerY(world.bX[b], world.bY[b], s)
                    val px = isoX(cx, cy); val py = isoY(cx, cy)
                    val spr = buildingSprite(b)
                    if (world.bState[b] == B_SITE) {
                        // площадка: здание проявляется по ходу стройки, поверх - леса
                        val p = world.bBuild[b].toFloat() / world.def(b).build
                        tint(batch, 0.85f, 0.75f, 0.6f, 0.15f + 0.75f * p)
                        sprites.draw(batch, spr, px, py)
                        tint(batch, 1f, 1f, 1f, 1f)
                        sprites.scaffold[world.def(b).size.coerceIn(1, 3)]?.let { sprites.draw(batch, it, px, py) }
                    } else {
                        // слой довольства: дом окрашен по довольству, остальные здания приглушены
                        val happyTint = overlay == OV_HAPPY
                        if (happyTint) {
                            val h = world.bHappy[b]
                            when {
                                !world.def(b).isHouse -> tint(batch, 0.55f, 0.55f, 0.55f, 1f)
                                else -> Palette.houseTint(h).let { tint(batch, it.r, it.g, it.b, 1f) }
                            }
                        }
                        sprites.draw(batch, spr, px, py)
                        if (happyTint) tint(batch, 1f, 1f, 1f, 1f)
                        sprites.levelMark(s, world.bLevel[b], winter)?.let { sprites.draw(batch, it, px, py) }
                        // крылья мельницы крутятся, пока она работает (ось - точка hub рендера; у старой графики - своя)
                        if (world.bDef[b] == millDef && sprites.millSails.isNotEmpty()) {
                            val frame = if (world.bStatus[b] == BS_OK) (time * 6f).toInt() % sprites.millSails.size else 0
                            val hub = spr.points["hub"]
                            sprites.draw(batch, sprites.millSails[frame], px + (hub?.get(0) ?: -8.8f), py + (hub?.get(1) ?: 36.6f))
                        }
                        drawAnim(batch, b, px, py)
                        drawWindows(batch, b, spr, px, py)
                    }
                }
                id >= wagons0 -> drawWagon(batch, id - wagons0)
                id >= kids0 -> drawKid(batch, kidB[id - kids0], kidK[id - kids0])
                id >= props0 + propCells.size + wallCells.size -> drawRaid(batch, id - (props0 + propCells.size + wallCells.size))
                id >= props0 + propCells.size -> drawWall(batch, wallCells[id - props0 - propCells.size])
                id < props0 -> {
                    val c = treeCells[id - trees0]
                    val fx = c % world.w + 0.5f + jitter(c, 0); val fy = c / world.w + 0.5f + jitter(c, 1)
                    if (world.tiles[c] == ROCK) {
                        // скала уменьшается по мере выработки
                        val k = 0.55f + 0.45f * minOf(1f, world.res[c].toFloat() / Tune.ROCK_MAX)
                        sprites.draw(batch, sprites.rocks[(c * 31) % sprites.rocks.size], sx(fx, fy), sy(fx, fy) - 2f, flip = (c + rot) % 3 == 0, k = k)
                    } else {
                        // деревья разного роста и оттенка, чтобы лес не выглядел копиями
                        val list = if (winter) sprites.treesSnow else sprites.trees
                        val hv = cellHash(c, 77)
                        // саженец растёт: от трети роста до полного
                        val young = if (world.res[c] == 0 && world.grow[c] > 0) 1f - world.grow[c].toFloat() / Tune.TREE_GROW_DAYS else 1f
                        val k = (if (sprites.low) 1f else 0.86f + (hv % 29) / 100f) * (0.35f + 0.65f * young)
                        val shade = if (sprites.low) 1f else 0.9f + ((hv / 29) % 15) / 100f
                        val out = if (world.border[c]) 1f else 1f - OUTSIDE_DARK
                        tint(batch, shade * out, shade * 1.02f * out, shade * 0.96f * out, 1f)
                        sprites.draw(batch, list[(c * 31) % list.size], sx(fx, fy), sy(fx, fy) - 2f, flip = (c + rot) % 3 == 0, k = k)
                        tint(batch, 1f, 1f, 1f, 1f)
                    }
                }
                else -> {
                    val p = id - props0
                    val c = propCells[p]
                    val kind = propKind[p]
                    val list = if (kind >= 1000) sprites.reeds else if (winter) sprites.bushesSnow else sprites.bushes
                    if (list.isEmpty()) continue
                    val fx = c % world.w + 0.5f + jitter(c, 2); val fy = c / world.w + 0.5f + jitter(c, 3)
                    sprites.draw(batch, list[(kind % 1000) % list.size], sx(fx, fy), sy(fx, fy), flip = (c + rot) % 2 == 0)
                }
            }
        }
    }

    /**
     * Стена: столб в клетке и полусегменты к соседним стенам. Полусегменты нарисованы для сторон мира при виде 0;
     * при повороте карты сторона мира d видна как (d - rot). Дальние половины (вид 1, 2) - до столба, ближние - после
     */
    private fun drawWall(batch: SpriteBatch, c: Int) {
        val x = c % world.w; val y = c / world.w
        val kind = if (world.tiles[c] == WALL_S) "stone" else "wood"
        val cx = cornerX(x, y, 1); val cy = cornerY(x, y, 1)
        val px = isoX(cx, cy); val py = isoY(cx, cy)
        val out = if (world.border[c]) 1f else 1f - OUTSIDE_DARK
        tint(batch, out, out, out, 1f)
        fun spr(name: String) = (if (winter) sprites.walls["${kind}_${name}_snow"] else null) ?: sprites.walls["${kind}_$name"]
        val post = spr("post")
        if (post == null) {
            // в атласе ещё нет стен - коричневый (серый) ромб
            val d = sprites.diamond ?: return
            if (kind == "stone") tint(batch, 0.55f * out, 0.55f * out, 0.58f * out, 1f) else tint(batch, 0.45f * out, 0.3f * out, 0.15f * out, 1f)
            sprites.draw(batch, d, px, py)
            tint(batch, 1f, 1f, 1f, 1f)
            return
        }
        val linked = BooleanArray(4)
        for (d in 0 until 4) {
            val nx = x + (if (d == 1) 1 else if (d == 3) -1 else 0); val ny = y + (if (d == 2) 1 else if (d == 0) -1 else 0)
            linked[d] = nx in 0 until world.w && ny in 0 until world.h && world.tiles[ny * world.w + nx].let { it == WALL_W || it == WALL_S }
        }
        fun half(view: Int) {
            for (d in 0 until 4) if (linked[d] && ((d - rot + 4) and 3) == view) spr(view.toString())?.let { sprites.draw(batch, it, px, py) }
        }
        half(1); half(2)
        sprites.draw(batch, post, px, py)
        half(0); half(3)
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Угроза: несколько фигурок (волки или разбойники) вокруг её позиции, бегут к следующей клетке пути */
    private fun drawRaid(batch: SpriteBatch, i: Int) {
        val sub = Tune.SUB.toFloat()
        val fx = world.rX[i] / sub; val fy = world.rY[i] / sub
        val path = world.rPath[i]
        var dir = 0
        if (path != null && world.rPos[i] < path.size) {
            val c = path[world.rPos[i]]
            val tx = c % world.w + 0.5f; val ty = c / world.w + 0.5f
            val dx = vX(tx, ty) - vX(fx, fy); val dy = vY(tx, ty) - vY(fx, fy)
            dir = (if (dx + dy > 0) 1 else 0) or (if (dx - dy < 0) 2 else 0)
        }
        val wolves = world.rType[i] == RAID_WOLVES
        val set = if (wolves) sprites.wolves else sprites.bandits
        val frame = ((time * 8f).toInt() + i) % Sprites.FRAMES
        for (k in 0 until world.rCount[i]) {
            // строй: одна впереди, остальные чуть позади и по бокам
            val ox = if (k == 0) 0f else (if (k % 2 == 1) -0.35f else 0.35f)
            val oy = if (k == 0) 0f else -0.25f * ((k + 1) / 2)
            val x = sx(fx + ox, fy + oy); val y = sy(fx + ox, fy + oy)
            if (set != null) sprites.draw(batch, set[dir][(frame + k) % Sprites.FRAMES], x, y, k = PERSON_SCALE)
            else {
                // в атласе ещё нет - тёмные фигурки жителей
                tint(batch, 0.25f, 0.22f, 0.22f, 1f)
                sprites.draw(batch, sprites.people[0][dir][(frame + k) % Sprites.FRAMES][0], x, y, k = PERSON_SCALE * (if (wolves) 0.7f else 1f))
                tint(batch, 1f, 1f, 1f, 1f)
            }
        }
    }

    /** Сдвиг дерева или предмета в клетке (-0.18..0.18), в лёгкой графике - без сдвига */
    private fun jitter(c: Int, axis: Int): Float = if (sprites.low) 0f else ((cellHash(c, 300 + axis) % 37) - 18) / 100f

    /**
     * Житель: направление по смещению за тик в виде (0 вправо-вниз, 1 вправо-вверх, 2 влево-вниз, 3 влево-вверх), кадр ходьбы по времени,
     * стоя - первый кадр; носильщик с грузом держит над головой мешок цвета ресурса.
     */
    private fun drawPerson(batch: SpriteBatch, a: Int, alpha: Float) {
        val sub = Tune.SUB.toFloat()
        val fx = (world.prevX[a] + (world.px[a] - world.prevX[a]) * alpha) / sub
        val fy = (world.prevY[a] + (world.py[a] - world.prevY[a]) * alpha) / sub
        val ox = world.prevX[a] / sub; val oy = world.prevY[a] / sub
        val nx = world.px[a] / sub; val ny = world.py[a] / sub
        val dx = vX(nx, ny) - vX(ox, oy); val dy = vY(nx, ny) - vY(ox, oy)
        val moving = dx != 0f || dy != 0f
        if (moving) dir[a] = (if (dx + dy > 0) 1 else 0) or (if (dx - dy < 0) 2 else 0)
        val frame = if (moving) ((world.tick + a * 3) / WALK_FRAME) % Sprites.FRAMES else 0
        val carry = world.carryN[a] > 0
        val job = world.job[a].coerceIn(0, 2)
        val spr = sprites.people[job][dir[a]][frame][if (carry) 1 else 0]
        val x = sx(fx, fy); val y = sy(fx, fy)
        val k = PERSON_SCALE
        sprites.draw(batch, spr, x, y, k = k)
        if (carry) {
            // ноша - значок самого товара над головой (доски, рыба, хлеб...): видно, кто что несёт;
            // нет значка - мешок цвета товара
            val r = world.carryR[a]
            val icon = carryIcons[r]
            val top = y + (spr.h - spr.ay - 1f) * k
            if (icon != null) sprites.draw(batch, icon, x, top, k = CARRY_ICON_H / icon.h)
            else {
                val c = resColor[r]
                tint(batch, c.r, c.g, c.b, 1f)
                sprites.draw(batch, sprites.sack, x, top, k = SACK_SCALE)
                tint(batch, 1f, 1f, 1f, 1f)
            }
        }
        if (!sprites.low && world.aCart[a] > 0) {
            val cart = sprites.named(if (world.aCart[a] == 2) "res_wagon" else "res_barrow")
            if (cart != null) sprites.draw(batch, cart, x + 7f * k, y + 3f * k, k = SACK_SCALE)
        }
    }

    private val winter get() = world.season == WINTER

    // ---------- дети, повозки, погода, паводок (0.11-0.12) ----------

    private var kidB = IntArray(64)
    private var kidK = IntArray(64)
    private var kidCount = 0
    private val kidPt = FloatArray(2)

    /** Где ребёнок k дома b в момент t: бегает по кругу у входа (у каждого свой круг и скорость) */
    private fun kidPos(b: Int, k: Int, t: Float) {
        val e = world.entrance(b)
        val ph = b * 1.37f + k * 2.4f
        val sp = 0.55f + 0.15f * k
        val rr = 0.55f + 0.25f * ((b + k) % 3)
        kidPt[0] = e % world.w + 0.5f + rr * MathUtils.sin(t * sp + ph)
        kidPt[1] = e / world.w + 0.5f + rr * MathUtils.cos(t * sp + ph) * 0.8f
    }

    private fun drawKid(batch: SpriteBatch, b: Int, k: Int) {
        kidPos(b, k, time)
        val x0 = kidPt[0]; val y0 = kidPt[1]
        kidPos(b, k, time + 0.05f)
        val dx = vX(kidPt[0], kidPt[1]) - vX(x0, y0); val dy = vY(kidPt[0], kidPt[1]) - vY(x0, y0)
        val d = (if (dx + dy > 0) 1 else 0) or (if (dx - dy < 0) 2 else 0)
        val frame = ((time * 9f).toInt() + b + k) % Sprites.FRAMES
        val x = sx(x0, y0); val y = sy(x0, y0)
        val set = sprites.kids
        if (set != null) sprites.draw(batch, set[d][frame], x, y, k = PERSON_SCALE)
        else {
            // в атласе ещё нет детей - житель поменьше
            tint(batch, 1f, 0.9f, 0.85f, 1f)
            sprites.draw(batch, sprites.people[0][d][frame][0], x, y, k = PERSON_SCALE * 0.62f)
            tint(batch, 1f, 1f, 1f, 1f)
        }
    }

    /** Повозка соседа: едет к следующей клетке пути */
    private fun drawWagon(batch: SpriteBatch, i: Int) {
        val sub = Tune.SUB.toFloat()
        val fx = world.wgX[i] / sub; val fy = world.wgY[i] / sub
        val path = world.wgPath[i]
        var d = 0
        if (path != null && world.wgPos[i] < path.size) {
            val c = path[world.wgPos[i]]
            val tx = c % world.w + 0.5f; val ty = c / world.w + 0.5f
            val dx = vX(tx, ty) - vX(fx, fy); val dy = vY(tx, ty) - vY(fx, fy)
            d = (if (dx + dy > 0) 1 else 0) or (if (dx - dy < 0) 2 else 0)
        }
        val x = sx(fx, fy); val y = sy(fx, fy)
        val set = sprites.wagons
        if (set != null) sprites.draw(batch, set[d][((time * 6f).toInt() + i) % Sprites.FRAMES], x, y, k = PERSON_SCALE)
        else sprites.named("res_wagon")?.let { sprites.draw(batch, it, x, y, k = 1.2f) }
    }

    /** Паводок: вода над низкими клетками, слегка рябит */
    private fun drawFlood(batch: SpriteBatch) {
        if (world.floodLeft <= 0) return
        val dia = sprites.diamond ?: return
        // вода прибывает и уходит плавно (первые и последние 300 тиков)
        val total = Tune.FLOOD_DAYS * Tune.TICKS_PER_DAY
        val k = minOf(1f, minOf(world.floodLeft, total - world.floodLeft + 1) / 300f)
        val w = world.w
        forVisible(vw - 1, vh - 1) { x, y ->
            val c = cellOf(x, y)
            if (!world.low[c]) return@forVisible
            val ripple = 0.06f * MathUtils.sin(time * 1.6f + (x * 3 + y * 5) * 0.7f)
            tint(batch, 0.24f + ripple, 0.46f + ripple, 0.8f, 0.7f * k)
            val ax = cornerX(c % w, c / w, 1); val ay = cornerY(c % w, c / w, 1)
            sprites.draw(batch, dia, isoX(ax, ay), isoY(ax, ay))
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Дождь, снег, туман поверх карты (в лёгкой графике - только приглушённый свет, без частиц) */
    private fun drawWeather(batch: SpriteBatch) {
        val wth = world.weather
        if (sprites.low || (wth != W_RAIN && wth != W_SNOW && wth != W_FOG)) return
        val puff = sprites.puff ?: return
        val width = vr - vl; val height = vt - vb
        val zoomK = maxOf(0.6f, camera.zoom)
        val n = minOf(if (wth == W_FOG) 60 else 420, (width * height / (if (wth == W_FOG) 9000f else 2600f)).toInt())
        for (i in 0 until n) {
            val hx = ((i * 7919) % 1000) / 1000f
            val hy = ((i * 104729 + 31) % 1000) / 1000f
            when (wth) {
                W_RAIN -> {
                    // косые струи: падают быстро, сносит вбок
                    val fall = (time * 0.9f + hy) % 1f
                    val x = vl + ((hx + fall * 0.06f) % 1f) * width
                    val y = vt - fall * height
                    batch.setColor(0.72f, 0.8f, 0.94f, 0.55f)
                    batch.draw(puff.r, x, y, 2.6f * zoomK, 16f * zoomK)
                }
                W_SNOW -> {
                    val fall = (time * 0.07f + hy) % 1f
                    val x = vl + ((hx + 0.02f * MathUtils.sin(time * 1.3f + i)) % 1f + 1f) % 1f * width
                    val y = vt - fall * height
                    val sz = (2.2f + (i % 3)) * zoomK
                    batch.setColor(1f, 1f, 1f, 0.8f)
                    batch.draw(puff.r, x, y, sz, sz)
                }
                else -> {
                    // туман: большие медленные клубы
                    val x = vl + ((hx + time * 0.004f * (1 + i % 3)) % 1f) * width
                    val y = vb + hy * height
                    val sz = (180f + (i % 5) * 40f) * zoomK
                    batch.setColor(0.86f, 0.88f, 0.9f, 0.10f)
                    batch.draw(puff.r, x - sz / 2, y - sz / 4, sz, sz / 2)
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f)
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Анимация здания поверх спрайта: флаг ратуши всегда, бельё - у жилого дома не зимой. В лёгкой графике нет */
    private fun drawAnim(batch: SpriteBatch, b: Int, px: Float, py: Float) {
        if (sprites.low) return
        val frames = sprites.anims[world.bDef[b]] ?: return
        val def = world.def(b)
        if (def.isHouse && (winter || world.bResidents[b] == 0 || world.bFire[b] > 0)) return
        val f = if (b < facingKnown) facing[b] else 0
        val view = frames[(rot - f) and 3]
        sprites.draw(batch, view[((time * 5f).toInt() + b) % view.size], px, py)
    }

    /**
     * Свет в окнах (точки win* рендера) в сумерки и ночью: в жилых домах, услугах и складах; тёплый, слегка мерцает,
     * не темнеет от ночного освещения. Рисуется сразу после здания - здания впереди его закрывают. В лёгкой графике нет.
     */
    private fun drawWindows(batch: SpriteBatch, b: Int, spr: Spr, px: Float, py: Float) {
        val puff = sprites.puff ?: return
        if (dark < 0.05f || sprites.low || spr.points.isEmpty() || world.bFire[b] > 0) return
        val def = world.def(b)
        if (!(if (def.isHouse) world.bResidents[b] > 0 else def.isService || def.isStore)) return
        var i = 0
        for ((name, pt) in spr.points) {
            if (!name.startsWith("win")) continue
            i++
            // окна загораются не разом: у каждого свой порог темноты
            val on = dark - ((b * 7 + i * 13) % 10) * 0.04f
            if (on <= 0f) continue
            val a = minOf(1f, on * 2.5f) * (0.82f + 0.18f * MathUtils.sin(time * 2.7f + b * 1.3f + i * 2.1f))
            val sc = 0.5f
            batch.setColor(1f, 0.78f, 0.38f, 0.85f * a)
            batch.draw(puff.r, px + pt[0] - puff.w * sc / 2, py + pt[1] - puff.h * sc / 2, puff.w * sc, puff.h * sc)
        }
        if (i > 0) tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Здание светит окнами в сумерки и ночью: жилой дом с жильцами, услуга, склад; не горит */
    private fun lit(b: Int): Boolean {
        if (world.bState[b] != B_ACTIVE || world.bFire[b] > 0) return false
        val def = world.def(b)
        return if (def.isHouse) world.bResidents[b] > 0 else def.isService || def.isStore
    }

    /**
     * Тёплые пятна света на земле перед освещёнными зданиями (сумерки и ночь): мягкое пятно, сложение цветов -
     * вечером город уютнее, чем ровная синяя заливка. Под зданиями и жителями. В лёгкой графике нет.
     */
    private fun drawGroundLights(batch: SpriteBatch) {
        val puff = sprites.puff ?: return
        if (dark < 0.05f || sprites.low) return
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE)
        for (b in 0 until world.buildingCount) {
            if (!lit(b)) continue
            val s = world.def(b).size
            // между серединой здания и ближним к зрителю углом - свет падает из окон и дверей на двор
            val mx = world.bX[b] + s / 2f; val my = world.bY[b] + s / 2f
            val cx = cornerX(world.bX[b], world.bY[b], s); val cy = cornerY(world.bX[b], world.bY[b], s)
            val px = (sx(mx, my) + isoX(cx, cy)) / 2; val py = (sy(mx, my) + isoY(cx, cy)) / 2
            if (!visible(px, py)) continue
            val on = minOf(1f, dark * 1.6f - ((b * 7) % 10) * 0.03f)
            if (on <= 0f) continue
            val a = on * GLOW_A * (0.88f + 0.12f * MathUtils.sin(time * 2.1f + b * 1.7f))
            val w = (s + 1.4f) * tw; val h = w * 0.5f
            batch.setColor(1f, 0.78f, 0.44f, a)
            batch.draw(puff.r, px - w / 2, py - h / 2, w, h)
        }
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Дым из труб (точки smoke* рендера; в лёгкой графике нет) и огонь над горящими зданиями */
    private fun drawEffects(batch: SpriteBatch) {
        val puff = if (sprites.low) null else sprites.puff
        for (b in 0 until world.buildingCount) {
            if (world.bState[b] != B_ACTIVE) continue
            val s = world.def(b).size
            val cx = cornerX(world.bX[b], world.bY[b], s); val cy = cornerY(world.bX[b], world.bY[b], s)
            val px = isoX(cx, cy); val py = isoY(cx, cy)
            if (!visible(px, py)) continue
            val spr = buildingSprite(b)
            if (world.bFire[b] > 0 && sprites.flames.isNotEmpty()) {
                val top = spr.h - spr.ay
                for (k in 0 until 3) {
                    val f = sprites.flames[((time * 8f).toInt() + k + b) % sprites.flames.size]
                    tint(batch, 1f, 1f, 1f, 1f)
                    sprites.draw(batch, f, px + (k - 1) * 9f * s / 2, py + top * (0.35f + 0.12f * k))
                }
            }
            if (puff == null || spr.points.isEmpty()) continue
            val def = world.def(b)
            val smoking = if (def.isHouse) world.bResidents[b] > 0 else world.bStatus[b] == BS_OK
            if (!smoking) continue
            // пекарня и пивоварня парят: белые клубы чаще и быстрее; зимой дома топят сильнее - дым гуще
            val steam = def.id == "bakery" || def.id == "brewery"
            val speed = if (steam) 0.8f else 0.45f
            val alpha = if (steam) 0.75f else if (def.isHouse && winter) 0.75f else 0.55f
            val shade = if (steam) 1f else 0.92f
            val n = if (steam) 4 else 3
            for ((name, pt) in spr.points) {
                if (!name.startsWith("smoke")) continue
                for (k in 0 until n) {
                    // клуб поднимается, растёт и тает; сдвиг по фазе у разных зданий
                    val ph = ((time * speed + k.toFloat() / n + b * 0.37f) % 1f)
                    val sc = 0.35f + ph * 0.75f
                    tint(batch, shade, shade, shade + 0.03f, alpha * (1f - ph))
                    batch.draw(puff.r, px + pt[0] + ph * 6f - puff.w * sc / 2, py + pt[1] + ph * 18f - puff.h * sc / 2, puff.w * sc, puff.h * sc)
                }
            }
        }
        tint(batch, 1f, 1f, 1f, 1f)
    }

    /** Значки «почему простаивает» над мастерскими и услугами и «не хватает» над домами - поверх всего */
    private fun drawIcons(batch: SpriteBatch) {
        batch.setColor(1f, 1f, 1f, 1f)
        for (b in 0 until world.buildingCount) {
            if (world.bState[b] == B_GONE) continue
            val def = world.def(b)
            val s = def.size
            val cx = cornerX(world.bX[b], world.bY[b], s); val cy = cornerY(world.bX[b], world.bY[b], s)
            val px = isoX(cx, cy); val py = isoY(cx, cy)
            if (!visible(px, py)) continue
            val active = world.bState[b] == B_ACTIVE
            val spr = if (active) buildingSprite(b) else (sprites.scaffold[s] ?: buildingSprite(b))
            val top = py + spr.h - spr.ay - 2f
            // срочное: значок слева от значка простоя (или над серединой, если простоя нет)
            val prioIcon = if (world.bPrio[b] == 1 && !hideIcons) sprites.named("hud_prio") else null
            // наложение довольства: цветной маркер над домом
            if (overlay == OV_HAPPY && active && def.isHouse) {
                val dia = sprites.diamond
                if (dia != null) {
                    val h = world.bHappy[b]
                    Palette.happy(h).let { batch.setColor(it.r, it.g, it.b, 0.95f) }
                    sprites.draw(batch, dia, px, top + 4f, k = 0.5f)
                    batch.setColor(1f, 1f, 1f, 1f)
                }
            }
            val icon = if (!active || hideIcons) null else when {
                world.bFire[b] > 0 -> sprites.icons[BS_FIRE]
                def.isHouse -> when {
                    world.lacksVital(b) -> sprites.icons[Sprites.ICON_HUNGRY]
                    world.bSick[b] > 0 -> sprites.icons[Sprites.ICON_SICK]
                    world.bUpg[b] == 1 || world.houseReady(b) -> sprites.icons[Sprites.ICON_UPGRADE]
                    else -> null
                }
                world.bUpg[b] == 1 -> sprites.icons[Sprites.ICON_UPGRADE]
                def.isProducer || def.isService -> sprites.icons[world.bStatus[b]]
                else -> null
            }
            // спокойное (лимит, не сезон) - только у выбранного и в слоях; «можно улучшить» - мелко и полупрозрачно,
            // на отдалении не видно; беды и простои - полным значком
            val code = when {
                icon == null -> -1
                icon === sprites.icons[BS_LIMIT] && !def.isHouse && world.bUpg[b] != 1 -> BS_LIMIT
                icon === sprites.icons[BS_SEASON] -> BS_SEASON
                icon === sprites.icons[Sprites.ICON_UPGRADE] -> Sprites.ICON_UPGRADE
                else -> -1
            }
            val selected = selX == world.bX[b] && selY == world.bY[b]
            // беды и простои - чуть меньше полного размера: заметны, но не ярче самого города
            var k = if (selected) 1f else ICON_K
            var a = 1f
            var show = icon != null
            if (code == BS_LIMIT || code == BS_SEASON) show = show && (selected || overlay != OV_NONE)
            else if (code == Sprites.ICON_UPGRADE) {
                show = show && (selected || camera.zoom < QUIET_ZOOM)
                if (!selected) { k = 0.5f; a = 0.6f }
            }
            // над серединой здания (у широких спрайтов с тенью привязка не по центру)
            if (show) {
                batch.setColor(1f, 1f, 1f, a)
                sprites.draw(batch, icon!!, px, top, k = k)
                batch.setColor(1f, 1f, 1f, 1f)
            }
            if (prioIcon != null) sprites.draw(batch, prioIcon, px - (if (show) icon!!.w * 0.5f * k + prioIcon.w * 0.5f else 0f), top)
        }
    }

    private fun add(depth: Float, id: Int) {
        if (count == keys.size) keys = keys.copyOf(count * 2)
        val d = (depth * 8).toInt().coerceIn(0, 2047)
        keys[count++] = ((2047 - d) shl 20) or id
    }
}
