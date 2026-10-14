package town

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.JsonReader
import town.data.Content
import town.sim.*

/** Спрайт из атласа: область и точка привязки, всё в единицах мира (атлас нарисован крупнее, см. scale) */
class Spr(val r: TextureRegion, var ax: Float, var ay: Float, var w: Float, var h: Float) {
    /** Точки спрайта в единицах мира от привязки (x вправо, y вверх): трубы smoke*, ось крыльев hub */
    var points: Map<String, FloatArray> = emptyMap()
}

/**
 * Графика - атлас assets/sprites/atlas.png (+ страницы atlas_1.png... со зданиями; смена текстуры = лишний вызов
 * отрисовки, поэтому здания на своих страницах). Атлас и atlas.json собирает генератор ресурсов (./gradlew :tools:run).
 * Клетка - ромб TW x TH; точка привязки: у клетки и здания - ближний к зрителю угол ромба, у дерева и жителя - основание.
 * Здания нарисованы с 4 сторон (bld_<id>_r<поворот>) - карту можно поворачивать.
 */
class Sprites(content: Content, low: Boolean) {
    companion object {
        const val TW = 32
        const val TH = 16
        const val ICON_HUNGRY = 100
        const val ICON_SICK = 101
        const val ICON_UPGRADE = 102
        /** Кадров ходьбы и направлений (0 - к зрителю, 1 - от зрителя; влево - отражение) */
        const val FRAMES = 4
        const val ANIM_FRAMES = 4
        const val DUAL_VARIANTS = 2
        /** Размер полного атласа: если видеокарта не держит такую текстуру - только лёгкая графика */
        const val FULL_ATLAS_SIZE = 4096
    }

    private var pages: List<Texture>
    /**
     * Здания с 4 сторон: [уровень 1..3][тип][вид 0..3]; вид = (поворот карты - разворот здания) mod 4.
     * Уровни 2 и 3 - с флажками и пристройками (bld_<id>_l2...); нет спрайта - как уровень ниже
     */
    private val bldViews = Array(4) { ArrayList<Array<Spr>>() }
    private val bldViewsSnow = Array(4) { ArrayList<Array<Spr>>() }
    /** Вариант «на берегу» (рыбак, вода вплотную): [уровень] тип -> виды */
    private val shoreViews = Array(4) { HashMap<Int, Array<Spr>>() }
    private val shoreViewsSnow = Array(4) { HashMap<Int, Array<Spr>>() }
    private val all = HashMap<String, Spr>()
    /** Лёгкая графика: атлас вдвое мельче, без переходов земли (dual) и мелких предметов (prop) */
    var low = low
        private set

    val ground = HashMap<Int, Array<Spr>>()   // тип клетки -> варианты (у воды - кадры)
    val trees = ArrayList<Spr>()
    val rocks = ArrayList<Spr>()
    val buildings = ArrayList<Spr>()           // по индексу типа здания
    /** Картинки для карточек построек: только здание, без тени (нет - как на карте) */
    val cards = ArrayList<Spr>()
    /** Леса стройки по размеру здания (1..3) */
    val scaffold = HashMap<Int, Spr>()
    /** Жители: [профессия][направление][кадр][с грузом 0/1] */
    val people: Array<Array<Array<Array<Spr>>>>
    /** Значки над зданиями: по коду состояния BS_* и ICON_HUNGRY */
    val icons = HashMap<Int, Spr>()
    val sack: Spr
    /** Зимние варианты: здания (по индексу типа, нет - обычный), деревья, клетки по типу */
    val buildingsSnow = ArrayList<Spr>()
    val treesSnow = ArrayList<Spr>()
    val groundSnow = HashMap<Int, Array<Spr>>()
    /** Крылья мельницы (4 кадра поворота), огонь (3 кадра), клуб дыма */
    var millSails: Array<Spr> = emptyArray()
    /** Анимации зданий (флаг ратуши, бельё у домов): [здание] -> [вид 0..3] -> кадры; нет - null */
    val anims = HashMap<Int, Array<Array<Spr>>>()
    var flames: Array<Spr> = emptyArray()
    var puff: Spr? = null
    /** Угрозы: разбойники и волки [направление][кадр] (как жители); нет в атласе - null */
    var bandits: Array<Array<Spr>>? = null
    var wolves: Array<Array<Spr>>? = null
    /** Дети и повозка соседей [направление][кадр]; нет в атласе - null */
    var kids: Array<Array<Spr>>? = null
    var wagons: Array<Array<Spr>>? = null
    /** Стены: "wood_post", "wood_0".."wood_3" (полусегмент к стороне мира), "stone_..."; зимние - с "_snow" */
    val walls = HashMap<String, Spr>()
    /** Белый квадрат для фонов интерфейса и подсветки (середина без краёв) */
    val white: TextureRegion

    /** Переходы земли: слой -> [маска * DUAL_VARIANTS + вариант] (маски 1..14), только в полной графике */
    val dual = HashMap<String, Array<Spr?>>()
    /** Мелкие предметы: кусты, камыш (объекты), цветы, камни (на земле) */
    var bushes: Array<Spr> = emptyArray()
    /** Пни, косяки рыбы (кадры), белый ромб клетки для подсветки */
    var stumps: Array<Spr> = emptyArray()
    var fishSpots: Array<Spr> = emptyArray()
    var diamond: Spr? = null
    var bushesSnow: Array<Spr> = emptyArray()
    /** Клетки без прозрачных углов (полная графика): подложка луга и слои целиком; по типу клетки */
    val solid = HashMap<Int, Array<Spr>>()
    val solidSnow = HashMap<Int, Array<Spr>>()
    var reeds: Array<Spr> = emptyArray()
    var flowers: Array<Spr> = emptyArray()
    var stones: Array<Spr> = emptyArray()
    /** Мост: 0 - доски вдоль x, 1 - вдоль y (null - нет в атласе); мощёная в атласе */
    val bridges = arrayOfNulls<Spr>(2)
    val bridgesSnow = arrayOfNulls<Spr>(2)
    var hasPaved = false

    /**
     * Загрузить атлас (полный или лёгкий). При смене графики области обновляются на месте:
     * все ссылки на Spr и TextureRegion (карточки построек, значки интерфейса) остаются рабочими.
     */
    private fun load(): List<Texture> {
        val name = if (low) "atlas_low" else "atlas"
        val root = JsonReader().parse(Gdx.files.internal("sprites/$name.json").readString("UTF-8"))
        val files = root.get("pages")?.asStringArray() ?: arrayOf("$name.png")
        val tex = files.map { f ->
            Texture(Gdx.files.internal("sprites/$f"), true).apply { setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear) }
        }
        val base = root.getFloat("scale")
        var e = root.get("regions").child
        while (e != null) {
            val a = e.asFloatArray()
            val scale = if (a.size > 6) a[6] else base
            val page = tex[if (a.size > 7) a[7].toInt() else 0]
            val x = a[0].toInt(); val y = a[1].toInt(); val w = a[2].toInt(); val h = a[3].toInt()
            val old = all[e.name]
            if (old == null) all[e.name] = Spr(TextureRegion(page, x, y, w, h), a[4] / scale, a[5] / scale, w / scale, h / scale)
            else {
                old.r.setTexture(page); old.r.setRegion(x, y, w, h)
                old.ax = a[4] / scale; old.ay = a[5] / scale; old.w = w / scale; old.h = h / scale
            }
            e = e.next
        }
        var p = root.get("points")?.child
        while (p != null) {
            val m = HashMap<String, FloatArray>()
            var q = p.child
            while (q != null) { m[q.name] = q.asFloatArray(); q = q.next }
            all[p.name]?.points = m
            p = p.next
        }
        return tex
    }

    /** Сменить графику на лету (настройка или автоматическое упрощение) */
    fun setLow(v: Boolean) {
        if (v == low) return
        low = v
        val old = pages
        pages = load()
        old.forEach { it.dispose() }
        val w = all["white"]!!.r
        white.setTexture(w.texture); white.setRegion(w.regionX + 2, w.regionY + 2, 4, 4)
    }

    init {
        pages = load()
        fun list(prefix: String): Array<Spr> {
            val out = ArrayList<Spr>()
            var i = 0
            while (all.containsKey("$prefix$i")) out.add(all["$prefix${i++}"]!!)
            return out.toTypedArray()
        }
        ground[GRASS] = list("tile_grass_")
        ground[FOREST] = list("tile_forest_")
        ground[WATER] = list("tile_water_")
        ground[ROAD] = list("tile_road_")
        ground[ROCK] = list("tile_rock_")
        ground[BUILDING] = ground[GRASS]!!
        hasPaved = all.containsKey("tile_paved_0")
        ground[PAVED] = list("tile_paved_").takeIf { it.isNotEmpty() } ?: ground[ROAD]!!
        ground[BRIDGE] = ground[WATER]!!
        for (i in 0..1) {
            bridges[i] = all["bridge_$i"]
            bridgesSnow[i] = all["bridge_snow_$i"] ?: all["bridge_$i"]
        }
        trees.addAll(list("tree_"))
        rocks.addAll(list("rock_"))
        val fallback = all["bld_house"]!!
        for (def in content.buildings) buildings.add(all["bld_${def.id}"] ?: fallback)
        for ((i, def) in content.buildings.withIndex()) cards.add(all["card_${def.id}"] ?: buildings[i])
        for (s in 1..3) scaffold[s] = all["scaffold_$s"]!!
        people = Array(3) { job -> Array(4) { dir -> Array(FRAMES) { f -> Array(2) { c -> all["person_${job}_${dir}_$f" + if (c == 1) "_c" else ""]!! } } } }
        icons[BS_NO_WORKERS] = all["icon_no_workers"]!!
        icons[BS_NO_RESOURCE] = all["icon_no_resource"]!!
        icons[BS_OUTPUT_FULL] = all["icon_output_full"]!!
        icons[BS_NO_INPUT] = all["icon_no_input"]!!
        icons[BS_SEASON] = all["icon_season"]!!
        icons[BS_FAR] = all["icon_far"]!!
        icons[BS_LIMIT] = all["icon_limit"]!!
        icons[ICON_HUNGRY] = all["icon_hungry"]!!
        sack = all["sack"]!!
        icons[BS_FIRE] = all["icon_fire"] ?: icons[BS_NO_INPUT]!!
        icons[ICON_SICK] = all["icon_sick"] ?: icons[ICON_HUNGRY]!!
        icons[BS_UNPAID] = all["icon_unpaid"] ?: icons[BS_NO_WORKERS]!!
        icons[ICON_UPGRADE] =all["icon_upgrade"] ?: icons[BS_LIMIT]!!
        icons[BS_FLOOD] = all["icon_flood"] ?: icons[BS_SEASON]!!
        for ((i, def) in content.buildings.withIndex()) buildingsSnow.add(all["bld_${def.id}_snow"] ?: buildings[i])
        for (i in trees.indices) treesSnow.add(all["tree_${i}_snow"] ?: trees[i])
        groundSnow[GRASS] = list("tile_grass_snow_").takeIf { it.isNotEmpty() } ?: ground[GRASS]!!
        groundSnow[FOREST] = list("tile_forest_snow_").takeIf { it.isNotEmpty() } ?: ground[FOREST]!!
        groundSnow[BUILDING] = groundSnow[GRASS]!!
        millSails = list("mill_sails_")
        for (def in content.buildings) {
            if (all["anim_${def.id}_0"] == null) continue
            anims[def.index] = Array(4) { v -> Array(ANIM_FRAMES) { f -> all["anim_${def.id}_$f" + (if (v > 0) "_r$v" else "")] ?: all["anim_${def.id}_$f"]!! } }
        }
        flames = list("flame_")
        puff = all["puff"]
        if (all["person_3_0_0"] != null) bandits = Array(4) { d -> Array(FRAMES) { f -> all["person_3_${d}_$f"] ?: all["person_3_0_0"]!! } }
        if (all["wolf_0_0"] != null) wolves = Array(4) { d -> Array(FRAMES) { f -> all["wolf_${d}_$f"] ?: all["wolf_0_0"]!! } }
        if (all["kid_0_0"] != null) kids = Array(4) { d -> Array(FRAMES) { f -> all["kid_${d}_$f"] ?: all["kid_0_0"]!! } }
        if (all["wagon_0_0"] != null) wagons = Array(4) { d -> Array(FRAMES) { f -> all["wagon_${d}_$f"] ?: all["wagon_0_0"]!! } }
        walls.clear()
        for ((name, spr) in all) if (name.startsWith("wall_")) walls[name.removePrefix("wall_")] = spr
        for (layer in listOf("forest", "forest_snow", "rock", "road", "paved", "water", "water_snow"))
            dual[layer] = Array(15 * DUAL_VARIANTS) { i -> all["dual_${layer}_${i / DUAL_VARIANTS}_${i % DUAL_VARIANTS}"] }
        for ((type, key) in listOf(GRASS to "grass", FOREST to "forest", WATER to "water", ROAD to "road", ROCK to "rock", PAVED to "paved")) {
            list("base_${key}_").takeIf { it.isNotEmpty() }?.let { solid[type] = it }
            list("base_${key}_snow_").takeIf { it.isNotEmpty() }?.let { solidSnow[type] = it }
        }
        stumps = list("prop_stump_"); fishSpots = list("prop_fish_"); diamond = all["diamond"]
        bushes = list("prop_bush_"); bushesSnow = list("prop_bushsnow_"); reeds = list("prop_reeds_"); flowers = list("prop_flowers_"); stones = list("prop_stones_")
        val w = all["white"]!!.r
        white = TextureRegion(w.texture, w.regionX + 2, w.regionY + 2, 4, 4)
        for ((i, def) in content.buildings.withIndex()) for (lv in 1..3) {
            fun views(id: String, snow: Boolean, below: Array<Spr>?): Array<Spr> = Array(4) { r ->
                val sfx = (if (lv > 1) "_l$lv" else "") + (if (r > 0) "_r$r" else "") + (if (snow) "_snow" else "")
                all["bld_$id$sfx"] ?: below?.get(r) ?: (if (snow) buildingsSnow[i] else buildings[i])
            }
            bldViews[lv].add(views(def.id, false, if (lv > 1) bldViews[lv - 1][i] else null))
            bldViewsSnow[lv].add(views(def.id, true, if (lv > 1) bldViewsSnow[lv - 1][i] else null))
            if (all.containsKey("bld_${def.id}_shore")) {
                shoreViews[lv][i] = views(def.id + "_shore", false, if (lv > 1) shoreViews[lv - 1][i] else null)
                shoreViewsSnow[lv][i] = views(def.id + "_shore", true, if (lv > 1) shoreViewsSnow[lv - 1][i] else null)
            }
        }
    }


    fun building(def: Int, view: Int, snow: Boolean, shore: Boolean = false, level: Int = 1): Spr {
        val v = view and 3
        val lv = level.coerceIn(1, 3)
        if (shore) (if (snow) shoreViewsSnow[lv][def] else shoreViews[lv][def])?.let { return it[v] }
        return (if (snow) bldViewsSnow[lv] else bldViews[lv])[def][v]
    }

    fun hasShore(def: Int) = shoreViews[1].containsKey(def)

    /** Признаки уровня 2-3 (флажок, навес, знамя, фонари) - наложение поверх здания размера size, или null */
    fun levelMark(size: Int, level: Int, snow: Boolean): Spr? =
        if (level < 2) null else all["lvlmark_${size}_$level" + (if (snow) "_snow" else "")] ?: all["lvlmark_${size}_$level"]

    /** Спрайт по имени из атласа (значки интерфейса hud_*, res_*, здания bld_*) или null */
    fun named(name: String): Spr? = all[name]

    /** Нарисовать спрайт так, чтобы его точка привязки попала в (x, y); flip - отразить по горизонтали, k - масштаб */
    fun draw(batch: SpriteBatch, s: Spr, x: Float, y: Float, flip: Boolean = false, k: Float = 1f) {
        if (flip) batch.draw(s.r, x + s.ax * k, y - s.ay * k, -s.w * k, s.h * k)
        else batch.draw(s.r, x - s.ax * k, y - s.ay * k, s.w * k, s.h * k)
    }

    fun dispose() {
        pages.forEach { it.dispose() }
    }
}
