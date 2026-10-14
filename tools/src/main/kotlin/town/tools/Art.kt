package town.tools

import java.awt.Color
import java.awt.GradientPaint
import java.awt.RadialGradientPaint
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Спрайты в стиле рисованной изометрии. Свет сверху слева: левые грани светлые, правые в тени,
 * мягкие градиенты, тёмная обводка, текстуры (доски, фахверк, кладка, черепица, солома).
 * Всё детерминировано (сид на каждый спрайт) - повторный запуск даёт тот же атлас.
 */
object Art {
    fun all(buildingIds: List<Pair<String, Int>>): List<Sprite> {
        val out = ArrayList<Sprite>()
        out += tiles()
        out += snowTiles()
        for (v in 0 until 6) out += tree(v)
        for (v in 0 until 6) out += tree(v, snow = true)
        for (v in 0 until 3) out += rock(v)
        // новые здания генерируются всегда, даже если их ещё нет в content.json
        val extra = listOf("house2", "house3", "firewatch", "healer", "tavern")
        val ids = buildingIds + extra.filter { e -> buildingIds.none { it.first == e } }.map { it to 2 }
        for ((id, size) in ids) { out += building(id, size); out += building(id, size, snow = true) }
        for (k in 0 until 4) out += millSails(k)
        out += effects()
        for (s in 1..3) out += scaffold(s)
        out += people()
        out += icons()
        out += sack()
        out += white()
        return out
    }

    // ---------- земля ----------

    private fun tiles(): List<Sprite> {
        val out = ArrayList<Sprite>()
        for (v in 0 until 4) out += tile("tile_grass_$v", rgb(0x74a84e), 100L + v) { c, iso, d ->
            iso.speckle(d, c, 0.16, 90)
            repeat(10) { tuft(iso, d, rgb(0x5a8a3a)) }
            if (v == 3) repeat(4) { flower(iso, d) }
        }
        for (v in 0 until 3) out += tile("tile_forest_$v", rgb(0x4f7f3a), 200L + v) { c, iso, d ->
            iso.speckle(d, c, 0.22, 110)
            repeat(8) { leaf(iso, d) }
        }
        for (v in 0 until 4) out += tile("tile_water_$v", rgb(0x3d7cc4), 300L) { c, iso, d ->
            // кадры воды: блики смещаются
            val old = iso.g.clip; iso.g.clip(d)
            val r = java.util.Random(300L)
            repeat(9) { k ->
                val x = r.nextDouble() * TW; val y = r.nextDouble() * TH
                val dx = (v * 4 + k * 3) % 14 - 7.0
                iso.line(Point2D.Double(x + dx, y), Point2D.Double(x + dx + 6 + r.nextDouble() * 6, y - 1), Color(220, 240, 255, 110), 1.3f)
            }
            iso.g.clip = old
        }
        for (v in 0 until 2) out += tile("tile_road_$v", rgb(0xb89c6c), 400L + v) { c, iso, d ->
            iso.speckle(d, c, 0.18, 70)
            repeat(10) { pebble(iso, d) }
        }
        for (v in 0 until 2) out += tile("tile_paved_$v", rgb(0x6e6656), 450L + v) { c, iso, d ->
            // брусчатка: ряды камней вразбежку, щели темнее, камни серо-бежевые с разбросом тона
            val old = iso.g.clip; iso.g.clip(d)
            val n = 5
            for (j in 0 until n) for (i in -1..n) {
                val shift = if (j % 2 == 1) 0.5 else 0.0
                val u0 = (i + shift) / n + 0.03; val u1 = (i + 1 + shift) / n - 0.03
                val v0 = j / n.toDouble() + 0.04; val v1 = (j + 1) / n.toDouble() - 0.04
                val k = 0.86 + iso.rnd.nextDouble() * 0.28
                val warm = iso.rnd.nextDouble() * 0.1
                val col = Color(((0xb4 + 20 * warm) * k).toInt().coerceIn(0, 255), ((0xaa + 8 * warm) * k).toInt().coerceIn(0, 255), ((0x94 - 10 * warm) * k).toInt().coerceIn(0, 255))
                iso.fill(iso.path(iso.p(u0, v0, 0.0), iso.p(u1, v0, 0.0), iso.p(u1, v1, 0.0), iso.p(u0, v1, 0.0)), col, outline = false, grad = 0.1)
            }
            iso.g.clip = old
        }
        for (v in 0 until 2) out += tile("tile_rock_$v", rgb(0x86907a), 500L + v) { c, iso, d ->
            iso.speckle(d, c, 0.2, 100)
            repeat(8) { pebble(iso, d) }
        }
        return out
    }

    /**
     * Клетка: ромб без градиента (иначе видна сетка), залитый с нахлёстом на полпикселя за край,
     * чтобы между соседними клетками не просвечивал фон.
     */
    private fun tile(name: String, base: Color, seed: Long, grad: Double = 0.0, deco: (Color, Iso, java.awt.Shape) -> Unit): Sprite {
        val iso = Iso(TW, TH, TW / 2.0, TH.toDouble(), seed)
        val d = iso.path(iso.p(0.0, 0.0, 0.0), iso.p(1.0, 0.0, 0.0), iso.p(1.0, 1.0, 0.0), iso.p(0.0, 1.0, 0.0))
        val e = 1.2
        val big = iso.path(
            java.awt.geom.Point2D.Double(TW / 2.0, TH + e), java.awt.geom.Point2D.Double(TW + e * 2, TH / 2.0),
            java.awt.geom.Point2D.Double(TW / 2.0, -e), java.awt.geom.Point2D.Double(-e * 2, TH / 2.0),
        )
        iso.g.paint = base
        iso.g.fill(big)
        if (grad > 0) iso.fill(d, base, outline = false, grad = grad)
        deco(base, iso, d)
        val img = iso.img
        iso.g.dispose()
        return Sprite(name, img, px(TW / 2), 0)
    }

    private fun inShape(iso: Iso, d: java.awt.Shape): Point2D.Double {
        while (true) {
            val x = iso.rnd.nextDouble() * TW; val y = iso.rnd.nextDouble() * TH
            if (d.contains(x, y)) return Point2D.Double(x, y)
        }
    }

    private fun tuft(iso: Iso, d: java.awt.Shape, c: Color) {
        val p = inShape(iso, d)
        for (k in -1..1) iso.line(p, Point2D.Double(p.x + k * 1.6, p.y - 3 - iso.rnd.nextDouble() * 2), c.alpha(200), 1f)
    }

    private fun flower(iso: Iso, d: java.awt.Shape) {
        val p = inShape(iso, d)
        iso.g.paint = if (iso.rnd.nextBoolean()) rgb(0xf0e070) else rgb(0xf0f0f0)
        iso.g.fill(Ellipse2D.Double(p.x, p.y, 2.2, 2.0))
    }

    private fun leaf(iso: Iso, d: java.awt.Shape) {
        val p = inShape(iso, d)
        iso.g.paint = mix(rgb(0x6a5a2a), rgb(0x3a6a2a), iso.rnd.nextDouble()).alpha(200)
        iso.g.fill(Ellipse2D.Double(p.x, p.y, 3.0, 1.6))
    }

    private fun pebble(iso: Iso, d: java.awt.Shape) {
        val p = inShape(iso, d)
        val c = rgb(0x9a9488).mul(0.85 + iso.rnd.nextDouble() * 0.3)
        iso.fill(Ellipse2D.Double(p.x, p.y, 2.5 + iso.rnd.nextDouble() * 2, 1.6 + iso.rnd.nextDouble()), c, outline = false)
    }

    // ---------- природа ----------

    private fun tree(v: Int, snow: Boolean = false): Sprite {
        val iso = Iso(90, 120, 45.0, 108.0, 600L + v)
        iso.shadow(-0.15, -0.15, 0.55, 0.55)
        val conifer = v >= 4
        val trunk = rgb(0x6a4a2c)
        val base = iso.p(0.2, 0.2, 0.0)
        if (conifer) {
            iso.fill(java.awt.geom.Rectangle2D.Double(base.x - 2.5, base.y - 16, 5.0, 16.0), trunk)
            val greens = intArrayOf(0x2f6a3a, 0x37764a)
            val h = 64.0 + v * 6
            for (k in 0 until 4) {
                val y0 = base.y - 10 - k * h / 5.2
                val w = 22.0 - k * 4.5
                val tri = iso.path(Point2D.Double(base.x - w, y0), Point2D.Double(base.x + w, y0), Point2D.Double(base.x + (k % 2) * 1.0, y0 - h / 2.6))
                iso.fill(tri, rgb(greens[k % 2]), grad = 0.25)
                iso.speckle(tri, rgb(greens[k % 2]), 0.25, 25)
                if (snow) {
                    // шапка снега на ярусе: верхушка треугольника и рваный низ
                    val ax = base.x + (k % 2) * 1.0; val ay = y0 - h / 2.6
                    fun edge(sx: Double, t: Double) = Point2D.Double(ax + (sx - ax) * t, ay + (y0 - ay) * t)
                    val cap = java.awt.geom.Path2D.Double()
                    cap.moveTo(ax, ay - 0.5)
                    val l0 = edge(base.x - w, 0.62); cap.lineTo(l0.x, l0.y)
                    for (j in 1..5) {
                        val tx = l0.x + (edge(base.x + w, 0.62).x - l0.x) * j / 5.0
                        cap.lineTo(tx - 2, l0.y + if (j % 2 == 1) 4.0 else 0.5); cap.lineTo(tx, l0.y + if (j % 2 == 1) 0.5 else 3.0)
                    }
                    cap.closePath()
                    iso.g.paint = iso.snowCol(1.08); iso.g.fill(cap)
                    iso.stroke(cap, iso.snowCol(0.8).alpha(160), 0.8f)
                }
            }
        } else {
            iso.fill(iso.path(
                Point2D.Double(base.x - 3.5, base.y), Point2D.Double(base.x + 3.5, base.y),
                Point2D.Double(base.x + 2.2, base.y - 30), Point2D.Double(base.x - 2.2, base.y - 30),
            ), trunk)
            val greens = intArrayOf(0x4a8a3a, 0x5a9a42, 0x3f7a34, 0x68a448)
            val cx = base.x; val cy = base.y - 44 - v * 3
            val r = iso.rnd
            // крона из нескольких пятен, от тёмных снизу к светлым сверху
            val blobs = ArrayList<DoubleArray>()
            repeat(7) { blobs += doubleArrayOf(cx + (r.nextDouble() - 0.5) * 30, cy + (r.nextDouble() - 0.4) * 22, 11.0 + r.nextDouble() * 7) }
            blobs.sortByDescending { it[1] }
            for ((k, b) in blobs.withIndex()) {
                val c = rgb(greens[(k + v) % greens.size]).mul(0.85 + k * 0.035)
                val e = Ellipse2D.Double(b[0] - b[2], b[1] - b[2] * 0.9, b[2] * 2, b[2] * 1.8)
                iso.g.paint = RadialGradientPaint(
                    Point2D.Double(b[0] - b[2] * 0.4, b[1] - b[2] * 0.5), (b[2] * 1.6).toFloat(),
                    floatArrayOf(0f, 1f), arrayOf(c.mul(1.25), c.mul(0.75)),
                )
                iso.g.fill(e)
                iso.stroke(e, c.mul(0.5).alpha(120), 1f)
                if (snow) {
                    val old = iso.g.clip; iso.g.clip(e)
                    val capE = Ellipse2D.Double(b[0] - b[2] * 1.1, b[1] - b[2] * 1.15, b[2] * 2.2, b[2] * 1.3)
                    iso.g.paint = RadialGradientPaint(
                        Point2D.Double(b[0] - b[2] * 0.3, b[1] - b[2] * 0.9), (b[2] * 1.4).toFloat(),
                        floatArrayOf(0f, 1f), arrayOf(iso.snowCol(1.15), iso.snowCol(0.85)),
                    )
                    iso.g.fill(capE)
                    iso.g.clip = old
                }
            }
            repeat(14) {
                val dx = cx - 18 + r.nextDouble() * 30; val dy = cy - 18 + r.nextDouble() * 18
                if (!snow) {
                    iso.g.paint = rgb(0xa8d070).alpha(150)
                    iso.g.fill(Ellipse2D.Double(dx, dy, 2.5, 2.0))
                }
            }
        }
        iso.ox = base.x; iso.oy = base.y
        return iso.toSprite(if (snow) "tree_${v}_snow" else "tree_$v")
    }

    private fun rock(v: Int): Sprite {
        val iso = Iso(70, 60, 35.0, 46.0, 700L + v)
        iso.shadow(0.05, 0.05, 0.95, 0.95, 0.9)
        val r = iso.rnd
        val n = 2 + v % 2
        for (k in 0 until n) {
            val c = iso.p(0.25 + r.nextDouble() * 0.5, 0.25 + r.nextDouble() * 0.5, 0.0)
            val w = 12.0 + r.nextDouble() * 9; val h = 9.0 + r.nextDouble() * 9
            val pts = (0 until 7).map { i ->
                val a = i * 2 * PI / 7
                val rr = 0.8 + r.nextDouble() * 0.3
                Point2D.Double(c.x + cos(a) * w * rr, c.y - h * 0.5 + sin(a) * h * rr * 0.8 - if (sin(a) < 0) h * 0.3 else 0.0)
            }.toTypedArray()
            val shape = iso.path(*pts)
            iso.fill(shape, rgb(0x9a9a9e).mul(0.9 + k * 0.08), grad = 0.3)
            iso.speckle(shape, rgb(0x9a9a9e), 0.2, 20)
            iso.line(Point2D.Double(c.x - w * 0.4, c.y - h * 1.0), Point2D.Double(c.x + w * 0.1, c.y - h * 1.15), Color(255, 255, 255, 90), 1.4f)
        }
        return iso.toSprite("rock_$v")
    }

    // ---------- здания ----------

    private fun building(id: String, s: Int, snow: Boolean = false): Sprite {
        val w = s * TW + 24; val h = s * TH + 230
        val iso = Iso(w, h, w / 2.0, h - 8.0, id.hashCode().toLong())
        iso.snow = snow
        val sd = s.toDouble()
        iso.shadow(0.0, 0.0, sd, sd, 0.95)
        iso.ground(0.05, 0.05, sd - 0.05, sd - 0.05, rgb(0xa89870))
        if (snow) iso.snowPatches(0.05, 0.05, sd - 0.05, sd - 0.05, 3 + s * 2, id.hashCode().toLong() + 77)
        when (id) {
            "house2" -> house2(iso)
            "house3" -> house3(iso)
            "firewatch" -> firewatch(iso)
            "healer" -> healer(iso)
            "tavern" -> tavern(iso)
            "flowerbed" -> flowerbed(iso)
            "fountain" -> fountain(iso)
            "statue" -> statue(iso)
            "hall" -> hall(iso)
            "house" -> house(iso)
            "store" -> store(iso)
            "woodcutter" -> woodcutter(iso)
            "sawmill" -> sawmill(iso)
            "quarry" -> quarry(iso)
            "farm" -> farm(iso)
            "mill" -> mill(iso)
            "bakery" -> bakery(iso)
            "fisher" -> fisher(iso)
            "well" -> well(iso)
            "market" -> market(iso)
            "church" -> church(iso)
            "hunter" -> hunter(iso)
            "orchard" -> orchard(iso)
            "pasture" -> pasture(iso)
            "weaver" -> weaver(iso)
            "brewery" -> brewery(iso)
            else -> genericHouse(iso, sd)
        }
        return iso.toSprite(if (snow) "bld_${id}_snow" else "bld_$id")
    }

    private val plaster = rgb(0xe8dcc0)
    private val stone = rgb(0xb8b0a0)
    private val wood = rgb(0x9a6a40)
    private val redRoof = rgb(0xb4503a)

    private fun genericHouse(iso: Iso, s: Double) {
        val (l, r) = iso.box(0.25, 0.25, s - 0.25, s - 0.25, 0.0, 26.0, plaster, "timber")
        r.door(0.4, 0.6, 0.75)
        iso.roof(0.25, 0.25, s - 0.25, s - 0.25, 26.0, 24.0, true, redRoof, gable = plaster)
    }

    private fun hall(iso: Iso) {
        // главный корпус, за ним пристройка; впереди слева башня с флагом
        val (l, r) = iso.box(0.9, 0.4, 2.8, 2.6, 0.0, 40.0, stone, "stone")
        for (u in doubleArrayOf(0.15, 0.55)) l.window(u, 0.45, u + 0.16, 0.8)
        r.door(0.35, 0.6, 0.6)
        r.window(0.72, 0.45, 0.88, 0.8)
        iso.roof(0.9, 0.4, 2.8, 2.6, 40.0, 34.0, true, rgb(0x46638e), "slate", gable = stone)
        val (tl, tr) = iso.box(0.2, 0.3, 1.1, 1.2, 0.0, 78.0, stone.mul(1.03), "stone")
        tl.window(0.35, 0.62, 0.65, 0.8)
        tr.window(0.35, 0.62, 0.65, 0.8)
        iso.spire(0.2, 0.3, 1.1, 1.2, 78.0, 34.0, rgb(0x46638e))
        val top = iso.p(0.65, 0.75, 112.0)
        iso.line(top, Point2D.Double(top.x, top.y - 18), rgb(0x3a3020), 1.6f)
        iso.fill(iso.path(Point2D.Double(top.x, top.y - 18), Point2D.Double(top.x + 13, top.y - 14), Point2D.Double(top.x, top.y - 10)), rgb(0xd04030))
    }

    private fun house(iso: Iso) {
        val (l, r) = iso.box(0.35, 0.3, 1.7, 1.65, 0.0, 26.0, plaster, "timber")
        l.window(0.3, 0.35, 0.5, 0.75)
        l.window(0.65, 0.35, 0.85, 0.75)
        r.door(0.4, 0.62, 0.8)
        iso.roof(0.35, 0.3, 1.7, 1.65, 26.0, 26.0, true, redRoof, gable = plaster)
        iso.box(1.25, 1.0, 1.42, 1.17, 34.0, 56.0, rgb(0xa09080), "stone", topColor = rgb(0x504540))
    }

    private fun store(iso: Iso) {
        val (l, r) = iso.box(0.3, 0.6, 1.75, 1.75, 0.0, 30.0, wood, "planks")
        r.door(0.25, 0.75, 0.85, rgb(0x5a3a22))
        iso.roof(0.3, 0.6, 1.75, 1.75, 30.0, 22.0, false, rgb(0x8a6a3a), "plank", gable = wood)
        // ящики и бочка у входа
        iso.box(0.25, 0.15, 0.55, 0.45, 0.0, 12.0, rgb(0xc09050), "planks", topColor = rgb(0xc09050))
        iso.box(0.62, 0.12, 0.88, 0.38, 0.0, 10.0, rgb(0xb08048), "planks", topColor = rgb(0xb08048))
        iso.cylinder(1.25, 0.3, 0.14, 0.0, 14.0, rgb(0x8a5a30))
    }

    private fun woodcutter(iso: Iso) {
        val (l, r) = iso.box(0.9, 0.8, 1.75, 1.75, 0.0, 22.0, wood, "planks")
        r.door(0.3, 0.55, 0.85)
        iso.roof(0.9, 0.8, 1.75, 1.75, 22.0, 18.0, true, rgb(0x5a7a3a), "thatch", gable = wood)
        // поленница
        for (k in 0 until 3) iso.log(0.2, 0.75, 0.55 - k * 0.0, k * 7.0, 0.12, rgb(0x8a5a30))
        for (k in 0 until 2) iso.log(0.25, 0.7, 0.25, k * 7.0, 0.12, rgb(0x7a5028))
        iso.cylinder(1.25, 0.35, 0.16, 0.0, 8.0, rgb(0x7a5028))
        val t = iso.p(1.25, 0.35, 8.0)
        iso.line(t, Point2D.Double(t.x + 7, t.y - 10), rgb(0x6a4a2a), 2f)
        iso.fill(iso.path(Point2D.Double(t.x + 5, t.y - 12), Point2D.Double(t.x + 11, t.y - 11), Point2D.Double(t.x + 8, t.y - 6)), rgb(0xb0b0b8))
    }

    private fun sawmill(iso: Iso) {
        // открытый навес на столбах, под ним бревно на козлах; штабель досок
        val roofZ = 26.0
        for ((x, y) in listOf(1.75 to 1.75, 0.35 to 1.75, 1.75 to 0.45, 0.35 to 0.45)) iso.box(x - 0.06, y - 0.06, x + 0.06, y + 0.06, 0.0, roofZ, wood)
        iso.box(0.5, 1.0, 1.6, 1.15, 0.0, 8.0, rgb(0x7a5530), topColor = rgb(0x7a5530))
        iso.log(0.5, 1.6, 0.95, 8.0, 0.1, rgb(0x8a5a30))
        iso.roof(0.3, 0.4, 1.8, 1.8, roofZ, 16.0, true, rgb(0x6a4a2a), "plank")
        for (k in 0 until 4) iso.box(0.2, 0.1, 0.9, 0.35, k * 3.0, k * 3.0 + 3, rgb(0xd8b070), topColor = rgb(0xe0c080))
    }

    private fun quarry(iso: Iso) {
        val (l, r) = iso.box(1.0, 1.0, 1.8, 1.8, 0.0, 20.0, stone, "stone")
        r.door(0.3, 0.6, 0.8)
        iso.roof(1.0, 1.0, 1.8, 1.8, 20.0, 14.0, false, rgb(0x6a5a50), "slate", gable = stone)
        // каменные блоки
        val blocks = listOf(doubleArrayOf(0.2, 0.2, 0.0), doubleArrayOf(0.55, 0.2, 0.0), doubleArrayOf(0.2, 0.55, 0.0), doubleArrayOf(0.35, 0.35, 10.0), doubleArrayOf(0.9, 0.25, 0.0))
        for (b in blocks.sortedByDescending { it[0] + it[1] - it[2] / 100 }) iso.box(b[0], b[1], b[0] + 0.3, b[1] + 0.3, b[2], b[2] + 10, rgb(0xa8a8a8), topColor = rgb(0xb8b8b8))
        iso.line(iso.p(1.4, 0.5, 0.0), iso.p(1.4, 0.5, 30.0), wood, 2f)
        iso.line(iso.p(1.4, 0.5, 30.0), iso.p(0.9, 0.5, 26.0), wood, 2f)
        iso.line(iso.p(0.9, 0.5, 26.0), iso.p(0.9, 0.5, 14.0), rgb(0x404040), 1f)
    }

    private fun farm(iso: Iso) {
        // поле рядами спереди, дом с амбаром сзади
        for (k in 0 until 6) {
            val y0 = 0.15 + k * 0.27
            val row = iso.path(iso.p(0.15, y0, 0.0), iso.p(1.6, y0, 0.0), iso.p(1.6, y0 + 0.2, 0.0), iso.p(0.15, y0 + 0.2, 0.0))
            iso.fill(row, rgb(0xd8b84a), outline = false, grad = 0.15)
            iso.speckle(row, rgb(0xc8a038), 0.25, 30)
            for (x in 0 until 10) {
                val p = iso.p(0.2 + x * 0.14, y0 + 0.1, 0.0)
                iso.line(p, Point2D.Double(p.x + 1, p.y - 7), rgb(0xc89a30), 1.2f)
                iso.g.paint = rgb(0xe8c860)
                iso.g.fill(Ellipse2D.Double(p.x - 0.5, p.y - 9, 2.6, 3.0))
            }
        }
        val (l, r) = iso.box(1.8, 0.9, 2.8, 2.8, 0.0, 26.0, wood.mul(1.1), "planks")
        r.door(0.3, 0.7, 0.85, rgb(0x6a3a22))
        iso.roof(1.8, 0.9, 2.8, 2.8, 26.0, 22.0, false, rgb(0xc8a050), "thatch", gable = wood)
        // забор вдоль поля
        for (k in 0..6) iso.line(iso.p(0.1 + k * 0.25, 0.08, 0.0), iso.p(0.1 + k * 0.25, 0.08, 7.0), wood, 1.4f)
        iso.line(iso.p(0.1, 0.08, 5.0), iso.p(1.6, 0.08, 5.0), wood, 1.4f)
    }

    private fun mill(iso: Iso) {
        val (l, r) = iso.box(0.5, 0.5, 1.5, 1.5, 0.0, 52.0, rgb(0xece4d4), "stone")
        r.door(0.38, 0.62, 0.35)
        l.window(0.4, 0.6, 0.6, 0.75)
        iso.spire(0.5, 0.5, 1.5, 1.5, 52.0, 26.0, rgb(0x8a3a2a))
        // крылья вынесены в отдельные кадры mill_sails_k (вращаются в игре), здесь только втулка
        val hub = iso.p(0.45, 1.0, 50.0)
        iso.fill(Ellipse2D.Double(hub.x - 3, hub.y - 3, 6.0, 6.0), rgb(0x5a3a22))
    }

    private fun bakery(iso: Iso) {
        val (l, r) = iso.box(0.6, 0.6, 1.75, 1.75, 0.0, 24.0, rgb(0xe0c8a0), "stone")
        r.door(0.55, 0.78, 0.8)
        l.window(0.35, 0.4, 0.6, 0.75)
        iso.roof(0.6, 0.6, 1.75, 1.75, 24.0, 22.0, true, rgb(0xa0583a), gable = rgb(0xe0c8a0))
        iso.box(1.4, 1.3, 1.6, 1.5, 30.0, 54.0, rgb(0xa08070), "stone", topColor = rgb(0x403530))
        // печь-купол спереди
        val c = iso.p(0.45, 0.4, 0.0)
        val dome = java.awt.geom.Arc2D.Double(c.x - 15, c.y - 20, 30.0, 34.0, 0.0, 180.0, java.awt.geom.Arc2D.CHORD)
        iso.fill(dome, rgb(0xc08060), grad = 0.25)
        iso.fill(java.awt.geom.Arc2D.Double(c.x - 6, c.y - 9, 12.0, 14.0, 0.0, 180.0, java.awt.geom.Arc2D.CHORD), rgb(0x402018))
        iso.g.paint = rgb(0xf08030).alpha(200)
        iso.g.fill(Ellipse2D.Double(c.x - 3, c.y - 6, 6.0, 4.0))
    }

    private fun fisher(iso: Iso) {
        val (l, r) = iso.box(0.9, 0.9, 1.75, 1.75, 0.0, 20.0, rgb(0x8a9aa0), "planks")
        r.door(0.3, 0.55, 0.85)
        iso.roof(0.9, 0.9, 1.75, 1.75, 20.0, 16.0, true, rgb(0x3e5a78), gable = rgb(0x8a9aa0))
        // рама с сетью
        val a = iso.p(0.2, 0.3, 0.0); val b = iso.p(0.85, 0.3, 0.0)
        iso.line(a, Point2D.Double(a.x, a.y - 26), wood, 2f)
        iso.line(b, Point2D.Double(b.x, b.y - 26), wood, 2f)
        iso.line(Point2D.Double(a.x, a.y - 26), Point2D.Double(b.x, b.y - 26), wood, 2f)
        for (k in 0..6) {
            val t = k / 6.0
            val x = a.x + (b.x - a.x) * t; val y = a.y + (b.y - a.y) * t
            iso.line(Point2D.Double(x, y - 26), Point2D.Double(x + 1, y - 8), rgb(0xd8d0b8).alpha(200), 0.8f)
        }
        for (k in 0..3) iso.line(Point2D.Double(a.x, a.y - 22 + k * 4), Point2D.Double(b.x, b.y - 22 + k * 4), rgb(0xd8d0b8).alpha(180), 0.8f)
        // рыба сушится
        for (k in 0 until 3) {
            val x = a.x + 6 + k * 9.0; val y = a.y - 30 - k * 4.5 + 8
            iso.fill(Ellipse2D.Double(x, y, 4.0, 9.0), rgb(0x70a8c8))
        }
    }

    private fun well(iso: Iso) {
        val c = iso.p(1.0, 1.0, 0.0)
        val rw = 22.0; val rh = 11.0
        // каменное кольцо
        val wall = java.awt.geom.Area(java.awt.geom.Rectangle2D.Double(c.x - rw, c.y - 14 - rh / 2, rw * 2, 14.0))
        wall.add(java.awt.geom.Area(Ellipse2D.Double(c.x - rw, c.y - rh, rw * 2, rh * 2)))
        iso.g.paint = GradientPaint((c.x - rw).toFloat(), 0f, stone.mul(1.15), (c.x + rw).toFloat(), 0f, stone.mul(0.7))
        iso.g.fill(wall)
        iso.stroke(wall, stone.mul(0.5), 1.4f)
        for (k in 0 until 8) {
            val x = c.x - rw + k * rw / 4
            iso.line(Point2D.Double(x, c.y - 14), Point2D.Double(x, c.y + 4), stone.mul(0.6).alpha(150), 1f)
        }
        val topE = Ellipse2D.Double(c.x - rw, c.y - 14 - rh, rw * 2, rh * 2)
        iso.fill(topE, stone.mul(1.1))
        iso.fill(Ellipse2D.Double(c.x - rw + 5, c.y - 14 - rh + 3, rw * 2 - 10, rh * 2 - 6), rgb(0x2a4a70), outline = false, grad = 0.3)
        // столбы и крыша
        iso.line(Point2D.Double(c.x - rw + 3, c.y - 14), Point2D.Double(c.x - rw + 3, c.y - 46), wood, 2.6f)
        iso.line(Point2D.Double(c.x + rw - 3, c.y - 14), Point2D.Double(c.x + rw - 3, c.y - 46), wood, 2.6f)
        iso.line(Point2D.Double(c.x - rw + 3, c.y - 38), Point2D.Double(c.x + rw - 3, c.y - 38), wood.mul(0.8), 2f)
        iso.line(Point2D.Double(c.x, c.y - 38), Point2D.Double(c.x, c.y - 26), rgb(0x404040), 1f)
        iso.fill(java.awt.geom.Rectangle2D.Double(c.x - 3, c.y - 27, 6.0, 6.0), rgb(0x8a6a40))
        val roof = iso.path(Point2D.Double(c.x - rw - 6, c.y - 44), Point2D.Double(c.x + rw + 6, c.y - 44), Point2D.Double(c.x, c.y - 62))
        iso.fill(roof, rgb(0x7a4a32))
    }

    private fun flowerbed(iso: Iso) {
        // низкая клумба: каменный бордюр, тёмная земля, зелень и цветы
        val (l, r) = iso.box(0.12, 0.12, 0.88, 0.88, 0.0, 6.0, rgb(0xa09484), "stone", topColor = rgb(0x6a4a30))
        val soil = iso.path(iso.p(0.2, 0.2, 6.0), iso.p(0.8, 0.2, 6.0), iso.p(0.8, 0.8, 6.0), iso.p(0.2, 0.8, 6.0))
        iso.speckle(soil, rgb(0x6a4a30), 0.2, 30)
        val cols = intArrayOf(0xe86a9a, 0xf2d03a, 0xf4f0e8, 0xe86a9a, 0xb878d8, 0xf2d03a)
        var n = 0
        for (sum in 14 downTo 0) {
            for (i in 0..sum) {
                val j = sum - i
                if (i > 4 || j > 4) continue
                val x = 0.26 + i * 0.12 + (iso.rnd.nextDouble() - 0.5) * 0.04
                val y = 0.26 + j * 0.12 + (iso.rnd.nextDouble() - 0.5) * 0.04
                val p = iso.p(x, y, 6.0)
                iso.fill(Ellipse2D.Double(p.x - 4, p.y - 6, 8.0, 6.0), rgb(0x4f9a3a), outline = false, grad = 0.3)
                val h = 4 + (n % 3) * 2.0
                iso.line(p, Point2D.Double(p.x, p.y - h), rgb(0x3f7a30), 1f)
                val c = rgb(cols[n % cols.size])
                iso.fill(Ellipse2D.Double(p.x - 2.6, p.y - h - 2.6, 5.2, 5.2), c, grad = 0.2)
                iso.fill(Ellipse2D.Double(p.x - 0.9, p.y - h - 0.9, 1.8, 1.8), rgb(0xf8e070), outline = false, grad = 0.0)
                n++
            }
        }
    }

    private fun fountain(iso: Iso) {
        // круглая каменная чаша с голубой водой, колонна и струя
        val c = iso.p(1.0, 1.0, 0.0)
        val rw = 46.0; val rh = 23.0; val hh = 16.0
        val wall = java.awt.geom.Area(java.awt.geom.Rectangle2D.Double(c.x - rw, c.y - hh, rw * 2, hh))
        wall.add(java.awt.geom.Area(Ellipse2D.Double(c.x - rw, c.y - rh, rw * 2, rh * 2)))
        iso.g.paint = GradientPaint((c.x - rw).toFloat(), 0f, stone.mul(1.15), (c.x + rw).toFloat(), 0f, stone.mul(0.7))
        iso.g.fill(wall)
        iso.stroke(wall, stone.mul(0.5), 1.4f)
        for (k in 0 until 12) {
            val x = c.x - rw + k * rw / 6
            iso.line(Point2D.Double(x, c.y - hh), Point2D.Double(x, c.y + 2), stone.mul(0.6).alpha(130), 1f)
        }
        iso.fill(Ellipse2D.Double(c.x - rw, c.y - hh - rh, rw * 2, rh * 2), stone.mul(1.1))
        val water = Ellipse2D.Double(c.x - rw + 6, c.y - hh - rh + 3, rw * 2 - 12, rh * 2 - 6)
        iso.fill(water, rgb(0x5aa8e0), outline = false, grad = 0.25)
        iso.stroke(Ellipse2D.Double(c.x - rw + 14, c.y - hh - rh + 7, rw * 2 - 28, rh * 2 - 14), Color(230, 245, 255, 150), 1.2f)
        // колонна и верхняя чаша
        iso.cylinder(1.0, 1.0, 0.16, hh - 2, hh + 24, stone)
        val tb = iso.p(1.0, 1.0, hh + 24)
        iso.fill(Ellipse2D.Double(tb.x - 15, tb.y - 7, 30.0, 14.0), stone.mul(1.1))
        iso.fill(Ellipse2D.Double(tb.x - 11, tb.y - 5, 22.0, 10.0), rgb(0x5aa8e0), outline = false, grad = 0.25)
        // струя
        val top = Point2D.Double(tb.x, tb.y - 2)
        val jet = java.awt.geom.Path2D.Double()
        jet.moveTo(top.x, top.y - 12)
        jet.curveTo(top.x - 14, top.y - 14, top.x - 18, top.y - 2, top.x - 14, top.y + 2)
        jet.moveTo(top.x, top.y - 12)
        jet.curveTo(top.x + 14, top.y - 14, top.x + 18, top.y - 2, top.x + 14, top.y + 2)
        iso.line(top, Point2D.Double(top.x, top.y - 12), Color(210, 235, 255, 230), 2.4f)
        iso.stroke(jet, Color(210, 235, 255, 220), 1.8f)
        iso.g.paint = Color(255, 255, 255, 230)
        iso.g.fill(Ellipse2D.Double(top.x - 2, top.y - 14, 4.0, 4.0))
    }

    private fun statue(iso: Iso) {
        // ступенчатый постамент и светлая статуя человека с поднятой рукой
        iso.box(0.25, 0.25, 1.75, 1.75, 0.0, 6.0, stone.mul(0.95), "stone")
        iso.box(0.5, 0.5, 1.5, 1.5, 6.0, 12.0, stone, "stone")
        iso.box(0.7, 0.7, 1.3, 1.3, 12.0, 46.0, stone.mul(1.05), "stone", topColor = stone.mul(1.2))
        val c = iso.p(1.0, 1.0, 46.0)
        val mar = rgb(0xe6e2da)
        // фигура: плащ, торс, голова, рука
        val robe = java.awt.geom.Path2D.Double()
        robe.moveTo(c.x - 11, c.y + 2); robe.lineTo(c.x - 7, c.y - 40); robe.lineTo(c.x + 7, c.y - 40); robe.lineTo(c.x + 11, c.y + 2)
        robe.curveTo(c.x + 6, c.y + 6, c.x - 6, c.y + 6, c.x - 11, c.y + 2); robe.closePath()
        iso.fill(robe, mar, grad = 0.2)
        iso.line(Point2D.Double(c.x - 3, c.y - 36), Point2D.Double(c.x - 5, c.y - 2), mar.mul(0.75), 1.2f)
        iso.line(Point2D.Double(c.x + 3, c.y - 36), Point2D.Double(c.x + 5, c.y - 2), mar.mul(0.75), 1.2f)
        iso.line(Point2D.Double(c.x + 6, c.y - 36), Point2D.Double(c.x + 15, c.y - 52), mar.mul(0.8), 3.4f)
        iso.fill(Ellipse2D.Double(c.x + 12.5, c.y - 56, 5.0, 5.0), mar, grad = 0.2)
        iso.fill(Ellipse2D.Double(c.x - 6.5, c.y - 54, 13.0, 14.0), mar, grad = 0.2)
        iso.fill(java.awt.geom.Rectangle2D.Double(c.x - 8, c.y - 40, 16.0, 3.0), mar.mul(0.85), grad = 0.1)
        // золотая табличка на постаменте
        val pl = iso.p(1.0, 1.0, 0.0)
        iso.fill(Ellipse2D.Double(pl.x - 4, pl.y - 26, 8.0, 5.0), rgb(0xd8a840), grad = 0.2)
    }

    private fun market(iso: Iso) {
        stall(iso, 1.0, 1.0, rgb(0xc84a3a))
        stall(iso, 0.25, 0.3, rgb(0x3a6ac8))
        iso.box(1.3, 0.2, 1.55, 0.45, 0.0, 9.0, rgb(0xc09050), "planks", topColor = rgb(0xc09050))
        iso.cylinder(1.7, 0.6, 0.12, 0.0, 12.0, rgb(0x8a5a30))
    }

    private fun stall(iso: Iso, x: Double, y: Double, c: Color) {
        val w = 0.75
        iso.box(x, y, x + w, y + w * 0.6, 0.0, 10.0, wood, "planks", topColor = rgb(0xb08850))
        // товар на прилавке
        val goods = intArrayOf(0xe0c040, 0xc06030, 0x80b040, 0xd08040)
        for (k in 0 until 4) {
            val p = iso.p(x + 0.12 + k * 0.15, y + 0.2, 10.0)
            iso.fill(Ellipse2D.Double(p.x - 3, p.y - 4, 6.0, 4.5), rgb(goods[k]))
        }
        for ((px, py) in listOf(x to y, x + w to y, x to y + w * 0.6, x + w to y + w * 0.6)) iso.line(iso.p(px, py, 0.0), iso.p(px, py, 26.0), wood, 1.6f)
        // полосатый навес
        val o = 0.08
        val a = iso.p(x - o, y - o, 22.0); val b = iso.p(x + w + o, y - o, 22.0)
        val cc = iso.p(x + w + o, y + w * 0.6 + o, 30.0); val d = iso.p(x - o, y + w * 0.6 + o, 30.0)
        for (k in 0 until 6) {
            val t0 = k / 6.0; val t1 = (k + 1) / 6.0
            fun lerp(p: Point2D.Double, q: Point2D.Double, t: Double) = Point2D.Double(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)
            val strip = iso.path(lerp(a, b, t0), lerp(a, b, t1), lerp(d, cc, t1), lerp(d, cc, t0))
            iso.fill(strip, if (k % 2 == 0) c else rgb(0xf0ead8), outline = false, grad = 0.1)
        }
        iso.stroke(iso.path(a, b, cc, d), c.mul(0.5), 1.2f)
    }

    private fun hunter(iso: Iso) {
        val (l, r) = iso.box(0.8, 0.8, 1.75, 1.75, 0.0, 20.0, rgb(0x7a5a3a), "planks")
        r.door(0.3, 0.55, 0.85)
        iso.roof(0.8, 0.8, 1.75, 1.75, 20.0, 18.0, false, rgb(0x5a6a3a), "thatch", gable = rgb(0x7a5a3a))
        // рама со шкурой и рога над дверью
        val a = iso.p(0.25, 0.35, 0.0)
        iso.line(a, Point2D.Double(a.x, a.y - 24), rgb(0x6a4a2a), 2f)
        iso.line(Point2D.Double(a.x + 22, a.y - 11), Point2D.Double(a.x + 22, a.y - 35), rgb(0x6a4a2a), 2f)
        val hide = iso.path(Point2D.Double(a.x + 2, a.y - 22), Point2D.Double(a.x + 20, a.y - 33), Point2D.Double(a.x + 20, a.y - 14), Point2D.Double(a.x + 2, a.y - 4))
        iso.fill(hide, rgb(0xb08050))
        val h = iso.p(1.3, 0.8, 18.0)
        iso.line(h, Point2D.Double(h.x - 6, h.y - 8), rgb(0xe8dcc0), 1.8f)
        iso.line(h, Point2D.Double(h.x + 6, h.y - 8), rgb(0xe8dcc0), 1.8f)
    }

    private fun orchard(iso: Iso) {
        // ряды яблонь и корзины с яблоками
        for ((x, y) in listOf(1.5 to 1.5, 0.75 to 1.5, 1.5 to 0.75, 0.75 to 0.75).sortedByDescending { it.first + it.second }) {
            val b = iso.p(x, y, 0.0)
            iso.fill(iso.path(Point2D.Double(b.x - 2.5, b.y), Point2D.Double(b.x + 2.5, b.y), Point2D.Double(b.x + 1.8, b.y - 18), Point2D.Double(b.x - 1.8, b.y - 18)), rgb(0x6a4a2c))
            val crown = Ellipse2D.Double(b.x - 15, b.y - 40, 30.0, 26.0)
            iso.g.paint = RadialGradientPaint(Point2D.Double(b.x - 5, b.y - 34), 22f, floatArrayOf(0f, 1f), arrayOf(rgb(0x7ab048), rgb(0x3f7a34)))
            iso.g.fill(crown)
            iso.stroke(crown, rgb(0x2a4a20), 1f)
            for (k in 0 until 5) {
                iso.g.paint = rgb(0xd8402a)
                iso.g.fill(Ellipse2D.Double(b.x - 10 + iso.rnd.nextDouble() * 18, b.y - 36 + iso.rnd.nextDouble() * 16, 4.0, 4.0))
            }
        }
        iso.cylinder(0.25, 0.3, 0.13, 0.0, 9.0, rgb(0xa8743a))
        val t = iso.p(0.25, 0.3, 9.0)
        for (k in 0 until 3) { iso.g.paint = rgb(0xd8402a); iso.g.fill(Ellipse2D.Double(t.x - 5 + k * 3.5, t.y - 3, 4.0, 4.0)) }
    }

    private fun pasture(iso: Iso) {
        iso.ground(0.05, 0.05, 1.95, 1.95, rgb(0x8ab858))
        val (l, r) = iso.box(1.2, 1.2, 1.85, 1.85, 0.0, 16.0, wood, "planks")
        r.door(0.3, 0.7, 0.8)
        iso.roof(1.2, 1.2, 1.85, 1.85, 16.0, 12.0, true, rgb(0x8a5a3a), "plank", gable = wood)
        // овцы
        for ((x, y) in listOf(0.5 to 1.2, 1.0 to 0.5, 0.45 to 0.5)) {
            val p = iso.p(x, y, 0.0)
            iso.fill(Ellipse2D.Double(p.x - 8, p.y - 12, 16.0, 10.0), rgb(0xf0ece0), grad = 0.2)
            iso.fill(Ellipse2D.Double(p.x + 5, p.y - 13, 6.0, 6.0), rgb(0x3a3030))
            iso.line(Point2D.Double(p.x - 4, p.y - 3), Point2D.Double(p.x - 4, p.y), rgb(0x3a3030), 1.4f)
            iso.line(Point2D.Double(p.x + 4, p.y - 3), Point2D.Double(p.x + 4, p.y), rgb(0x3a3030), 1.4f)
        }
        // изгородь
        for (k in 0..7) iso.line(iso.p(0.05 + k * 0.27, 0.05, 0.0), iso.p(0.05 + k * 0.27, 0.05, 8.0), wood, 1.4f)
        for (k in 0..7) iso.line(iso.p(0.05, 0.05 + k * 0.27, 0.0), iso.p(0.05, 0.05 + k * 0.27, 8.0), wood, 1.4f)
        iso.line(iso.p(0.05, 0.05, 6.0), iso.p(1.95, 0.05, 6.0), wood, 1.4f)
        iso.line(iso.p(0.05, 0.05, 6.0), iso.p(0.05, 1.95, 6.0), wood, 1.4f)
    }

    private fun weaver(iso: Iso) {
        val (l, r) = iso.box(0.4, 0.4, 1.7, 1.7, 0.0, 26.0, rgb(0xe0d0b8), "timber")
        l.window(0.25, 0.4, 0.45, 0.75)
        l.window(0.6, 0.4, 0.8, 0.75)
        r.door(0.35, 0.58, 0.8)
        iso.roof(0.4, 0.4, 1.7, 1.7, 26.0, 22.0, true, rgb(0x6a4a8a), gable = rgb(0xe0d0b8))
        // рулоны ткани у входа
        for ((k, c) in listOf(0x7a5ab0, 0xc84a3a).withIndex()) iso.log(0.2 + k * 0.35, 0.45 + k * 0.35, 0.2, 0.0, 0.09, rgb(c))
    }

    private fun brewery(iso: Iso) {
        val (l, r) = iso.box(0.6, 0.5, 1.8, 1.8, 0.0, 28.0, stone.mul(0.95), "stone")
        r.door(0.4, 0.7, 0.75, rgb(0x5a3020))
        l.window(0.35, 0.45, 0.6, 0.78)
        iso.roof(0.6, 0.5, 1.8, 1.8, 28.0, 22.0, false, rgb(0x7a4a2a), gable = stone)
        iso.box(1.3, 1.2, 1.5, 1.4, 34.0, 58.0, rgb(0xa08070), "stone", topColor = rgb(0x403530))
        // бочки
        for ((x, y) in listOf(0.2 to 0.6, 0.2 to 0.25, 0.55 to 0.2)) iso.cylinder(x, y, 0.13, 0.0, 14.0, rgb(0x8a5a30))
    }

    private fun church(iso: Iso) {
        val (l, r) = iso.box(1.0, 0.7, 2.8, 2.3, 0.0, 44.0, rgb(0xe8e2d6), "stone")
        for (u in doubleArrayOf(0.15, 0.45, 0.75)) {
            l.window(u, 0.35, u + 0.1, 0.8)
        }
        iso.roof(1.0, 0.7, 2.8, 2.3, 44.0, 36.0, true, rgb(0x5a6a8a), "slate", gable = rgb(0xe8e2d6))
        val (tl, tr) = iso.box(0.25, 1.0, 1.05, 1.8, 0.0, 96.0, rgb(0xece6da), "stone")
        tr.door(0.3, 0.7, 0.28, rgb(0x5a3020))
        tl.window(0.38, 0.72, 0.62, 0.86)
        tr.window(0.38, 0.72, 0.62, 0.86)
        iso.spire(0.25, 1.0, 1.05, 1.8, 96.0, 56.0, rgb(0x5a6a8a))
        val top = iso.p(0.65, 1.4, 152.0)
        iso.line(top, Point2D.Double(top.x, top.y - 16), rgb(0xd8b050), 2f)
        iso.line(Point2D.Double(top.x - 5, top.y - 11), Point2D.Double(top.x + 5, top.y - 11), rgb(0xd8b050), 2f)
    }

    private fun house2(iso: Iso) {
        // двухэтажный фахверковый дом, крупнее обычного
        val (l, r) = iso.box(0.2, 0.2, 1.8, 1.8, 0.0, 44.0, rgb(0xeadcbc), "timber")
        for (u in doubleArrayOf(0.14, 0.64)) { l.window(u, 0.1, u + 0.2, 0.42); l.window(u, 0.6, u + 0.2, 0.92) }
        r.door(0.38, 0.62, 0.42)
        r.window(0.12, 0.6, 0.32, 0.92); r.window(0.68, 0.6, 0.88, 0.92)
        iso.roof(0.2, 0.2, 1.8, 1.8, 44.0, 30.0, true, redRoof, gable = rgb(0xeadcbc))
        iso.box(1.3, 1.1, 1.52, 1.32, 58.0, 84.0, rgb(0xa09080), "stone", topColor = rgb(0x504540))
    }

    private fun house3(iso: Iso) {
        // двухэтажный каменный дом под сланцем
        val sc = rgb(0xbdb5a5)
        val (l, r) = iso.box(0.2, 0.25, 1.8, 1.8, 0.0, 46.0, sc, "stone")
        for (u in doubleArrayOf(0.1, 0.4, 0.7)) { l.window(u, 0.1, u + 0.16, 0.4); l.window(u, 0.6, u + 0.16, 0.9) }
        r.door(0.4, 0.6, 0.42, rgb(0x5a3a22))
        for (u in doubleArrayOf(0.12, 0.72)) { r.window(u, 0.1, u + 0.16, 0.4); r.window(u, 0.6, u + 0.16, 0.9) }
        r.window(0.4, 0.6, 0.56, 0.9)
        iso.roof(0.2, 0.25, 1.8, 1.8, 46.0, 26.0, true, rgb(0x56627a), "slate", gable = sc)
        iso.box(0.45, 1.2, 0.67, 1.42, 56.0, 86.0, rgb(0x9a8e80), "stone", topColor = rgb(0x403a38))
        iso.box(1.3, 1.1, 1.5, 1.3, 56.0, 86.0, rgb(0x9a8e80), "stone", topColor = rgb(0x403a38))
    }

    private fun firewatch(iso: Iso) {
        // высокая деревянная вышка на столбах с кабиной, колоколом и бочкой воды
        val legs = listOf(0.45 to 0.45, 1.45 to 0.45, 0.45 to 1.45, 1.45 to 1.45).sortedByDescending { it.first + it.second }
        for ((x, y) in legs) iso.box(x, y, x + 0.12, y + 0.12, 0.0, 56.0, wood.mul(0.95), "planks")
        iso.line(iso.p(0.51, 0.45, 4.0), iso.p(1.51, 0.45, 50.0), wood.mul(0.75), 2f)
        iso.line(iso.p(1.51, 0.45, 4.0), iso.p(0.51, 0.45, 50.0), wood.mul(0.75), 2f)
        iso.line(iso.p(0.45, 0.51, 4.0), iso.p(0.45, 1.51, 50.0), wood.mul(0.75), 2f)
        iso.line(iso.p(0.45, 1.51, 4.0), iso.p(0.45, 0.51, 50.0), wood.mul(0.75), 2f)
        iso.box(0.3, 0.3, 1.7, 1.7, 56.0, 62.0, wood.mul(0.85), "planks", topColor = rgb(0xb08850))
        val (l, r) = iso.box(0.4, 0.4, 1.6, 1.6, 62.0, 92.0, wood, "planks")
        l.window(0.3, 0.4, 0.7, 0.8, rgb(0xd8a840))
        r.window(0.3, 0.4, 0.7, 0.8, rgb(0xd8a840))
        iso.spire(0.35, 0.35, 1.65, 1.65, 92.0, 28.0, rgb(0x8a4a32))
        // колокол на кронштейне
        iso.line(iso.p(0.4, 1.0, 84.0), iso.p(0.15, 1.0, 84.0), rgb(0x4a3018), 2f)
        val b = iso.p(0.15, 1.0, 84.0)
        val bell = java.awt.geom.Path2D.Double()
        bell.moveTo(b.x - 5, b.y + 12); bell.curveTo(b.x - 5, b.y + 2, b.x - 2, b.y, b.x, b.y); bell.curveTo(b.x + 2, b.y, b.x + 5, b.y + 2, b.x + 5, b.y + 12); bell.closePath()
        iso.fill(bell, rgb(0xd8a840), grad = 0.25)
        // лестница на левой грани
        val a0 = iso.p(0.3, 0.6, 0.0); val a1 = iso.p(0.3, 0.6, 56.0)
        iso.line(a0, a1, wood.mul(0.8), 1.6f)
        iso.line(Point2D.Double(a0.x + 8, a0.y - 4), Point2D.Double(a1.x + 8, a1.y - 4), wood.mul(0.8), 1.6f)
        for (k in 1..7) iso.line(Point2D.Double(a0.x, a0.y - k * 7.0), Point2D.Double(a0.x + 8, a0.y - 4 - k * 7.0), wood.mul(0.7), 1.2f)
        // бочка с водой
        iso.cylinder(0.3, 0.2, 0.15, 0.0, 15.0, rgb(0x8a5a30))
        val t = iso.p(0.3, 0.2, 15.0)
        iso.g.paint = rgb(0x4a80c0)
        iso.g.fill(Ellipse2D.Double(t.x - 7, t.y - 3.5, 14.0, 7.0))
    }

    private fun healer(iso: Iso) {
        // белёный домик с зелёной крышей и грядками трав спереди
        val wall = rgb(0xf2eee2)
        val (l, r) = iso.box(1.0, 0.9, 1.9, 1.85, 0.0, 24.0, wall)
        l.window(0.3, 0.4, 0.7, 0.75)
        r.door(0.35, 0.6, 0.78, rgb(0x4a6a3a))
        r.window(0.7, 0.4, 0.88, 0.7)
        iso.roof(1.0, 0.9, 1.9, 1.85, 24.0, 22.0, true, rgb(0x4a8a4a), gable = wall)
        // грядки: тёмная земля, ряды зелёных и лиловых растений
        val soil = iso.path(iso.p(0.12, 0.12, 0.0), iso.p(0.92, 0.12, 0.0), iso.p(0.92, 0.95, 0.0), iso.p(0.12, 0.95, 0.0))
        iso.fill(soil, rgb(0x6a4a30), outline = false, grad = 0.1)
        iso.speckle(soil, rgb(0x6a4a30), 0.2, 30)
        val plantColors = intArrayOf(0x5aa040, 0x8a56b0, 0x70b048, 0xa070c8)
        for (row in 2 downTo 0) {
            val y = 0.3 + row * 0.26
            for (k in 5 downTo 0) {
                val p = iso.p(0.2 + k * 0.13, y, 0.0)
                val c = rgb(plantColors[(row + k) % 4])
                iso.fill(Ellipse2D.Double(p.x - 4, p.y - 7, 8.0, 7.0), c, grad = 0.3)
            }
        }
        for (k in 0..4) iso.line(iso.p(0.1 + k * 0.2, 0.08, 0.0), iso.p(0.1 + k * 0.2, 0.08, 6.0), wood, 1.3f)
        iso.line(iso.p(0.1, 0.08, 4.0), iso.p(0.9, 0.08, 4.0), wood, 1.3f)
    }

    private fun tavern(iso: Iso) {
        // длинный фахверковый дом с тёплыми окнами, вывеской на столбе и бочками у двери
        val wall = rgb(0xd8c49a)
        val warm = rgb(0xf0b040)
        val (l, r) = iso.box(0.3, 0.4, 1.9, 1.85, 0.0, 32.0, wall, "timber")
        for (u in doubleArrayOf(0.18, 0.68)) l.window(u, 0.35, u + 0.14, 0.82, warm)
        r.door(0.12, 0.3, 0.7, rgb(0x5a3a22))
        r.window(0.5, 0.35, 0.66, 0.82, warm); r.window(0.74, 0.35, 0.9, 0.82, warm)
        iso.roof(0.3, 0.4, 1.9, 1.85, 32.0, 26.0, true, rgb(0x7a4630), gable = wall)
        iso.box(1.5, 1.2, 1.72, 1.42, 44.0, 72.0, rgb(0xa09080), "stone", topColor = rgb(0x504540))
        // бочки у двери
        iso.cylinder(0.65, 0.15, 0.14, 0.0, 14.0, rgb(0x8a5a30))
        iso.cylinder(0.95, 0.2, 0.14, 0.0, 14.0, rgb(0x7a4a28))
        // столб с вывеской
        val a = iso.p(0.15, 0.15, 0.0)
        iso.line(a, Point2D.Double(a.x, a.y - 52), rgb(0x5a3a22), 2.6f)
        iso.line(Point2D.Double(a.x, a.y - 50), Point2D.Double(a.x + 17, a.y - 50), rgb(0x5a3a22), 2.2f)
        iso.line(Point2D.Double(a.x + 6, a.y - 50), Point2D.Double(a.x + 6, a.y - 45), rgb(0x3a2a18), 1f)
        iso.line(Point2D.Double(a.x + 15, a.y - 50), Point2D.Double(a.x + 15, a.y - 45), rgb(0x3a2a18), 1f)
        iso.fill(java.awt.geom.Rectangle2D.Double(a.x + 3.0, a.y - 45.0, 15.0, 12.0), rgb(0xc89a40))
        iso.g.paint = rgb(0x5a3a22)
        iso.g.fill(Ellipse2D.Double(a.x + 7.5, a.y - 42.0, 6.0, 6.0))
        iso.g.paint = rgb(0xf4ead0)
        iso.g.fill(Ellipse2D.Double(a.x + 8.5, a.y - 41.0, 4.0, 2.5))
    }

    /** Крылья мельницы: крест из 4 лопастей, повёрнутый на k * 22.5 градусов; привязка - центр втулки */
    private fun millSails(k: Int): Sprite {
        val iso = Iso(100, 100, 50.0, 50.0, 800L + k)
        val hub = Point2D.Double(50.0, 50.0)
        for (j in 0 until 4) {
            val a = PI / 4 + j * PI / 2 + k * PI / 8
            val tip = Point2D.Double(hub.x + cos(a) * 34, hub.y + sin(a) * 34)
            val nx = -sin(a) * 6; val ny = cos(a) * 6
            val sail = iso.path(
                Point2D.Double(hub.x + cos(a) * 8, hub.y + sin(a) * 8), tip,
                Point2D.Double(tip.x + nx, tip.y + ny), Point2D.Double(hub.x + cos(a) * 8 + nx, hub.y + sin(a) * 8 + ny),
            )
            iso.fill(sail, rgb(0xf0ead8), grad = 0.1)
            iso.line(hub, tip, rgb(0x6a4a2a), 2f)
        }
        iso.fill(Ellipse2D.Double(hub.x - 3, hub.y - 3, 6.0, 6.0), rgb(0x5a3a22))
        return iso.toSprite("mill_sails_$k")
    }

    /** Заснеженные клетки: белый покров с просвечивающей зеленью, без градиента (как tile) */
    private fun snowTiles(): List<Sprite> {
        val out = ArrayList<Sprite>()
        for (v in 0 until 4) out += tile("tile_grass_snow_$v", rgb(0xe8eef6), 1100L + v) { c, iso, d ->
            iso.speckle(d, c, 0.05, 90)
            repeat(5) { greenSpot(iso, d, rgb(0x74a84e)) }
            repeat(4) { tuft(iso, d, rgb(0x5a8a3a)) }
        }
        for (v in 0 until 3) out += tile("tile_forest_snow_$v", rgb(0xe2e9f2), 1200L + v) { c, iso, d ->
            iso.speckle(d, c, 0.06, 100)
            repeat(6) { greenSpot(iso, d, rgb(0x4f7f3a)) }
            repeat(3) { leaf(iso, d) }
        }
        return out
    }

    private fun greenSpot(iso: Iso, d: java.awt.Shape, c: Color) {
        val p = inShape(iso, d)
        iso.g.paint = c.alpha(150)
        iso.g.fill(Ellipse2D.Double(p.x, p.y, 3.0 + iso.rnd.nextDouble() * 3, 1.6 + iso.rnd.nextDouble()))
    }

    // ---------- эффекты ----------

    private fun effects(): List<Sprite> {
        val out = ArrayList<Sprite>()
        // облачко дыма: белый радиальный градиент с затуханием альфы
        run {
            val (img, g) = canvas(24, 24)
            g.paint = RadialGradientPaint(
                Point2D.Double(12.0, 12.0), 12f, floatArrayOf(0f, 0.45f, 1f),
                arrayOf(Color(255, 255, 255, 230), Color(255, 255, 255, 120), Color(255, 255, 255, 0)),
            )
            g.fill(Ellipse2D.Double(0.0, 0.0, 24.0, 24.0))
            g.dispose()
            out += Sprite("puff", img, px(12), px(12))
        }
        // кадры огня 28x40: три слоя, язык покачивается
        for (f in 0 until 3) {
            val (img, g) = canvas(28, 40)
            val sway = doubleArrayOf(-2.5, 2.5, 0.5)[f]
            val tall = doubleArrayOf(0.0, -3.0, 2.0)[f]
            val layers = listOf(0xd83a18 to 1.0, 0xf08020 to 0.78, 0xffd848 to 0.5)
            for ((i, lay) in layers.withIndex()) {
                val k = lay.second
                val w = 12.0 * k; val top = 36.0 * k + tall * k
                val p = java.awt.geom.Path2D.Double()
                val by = 39.0
                p.moveTo(14.0 - w, by - 6)
                p.curveTo(14.0 - w, by - 14 * k, 14.0 - w * 0.3 + sway * 0.4, by - top * 0.6, 14.0 + sway, by - top)
                p.curveTo(14.0 + w * 0.3 + sway * 0.4, by - top * 0.55, 14.0 + w, by - 14 * k, 14.0 + w, by - 6)
                p.curveTo(14.0 + w, by + 1, 14.0 - w, by + 1, 14.0 - w, by - 6)
                p.closePath()
                g.paint = rgb(lay.first)
                g.fill(p)
                if (i == 0) { g.paint = Color(120, 20, 10, 140); g.stroke = java.awt.BasicStroke(1f); g.draw(p) }
            }
            g.dispose()
            out += Sprite("flame_$f", img, px(14), 0)
        }
        return out
    }

    /** Леса стройки поверх площадки: столбы по углам и настил */
    private fun scaffold(s: Int): Sprite {
        val w = s * TW + 24; val h = s * TH + 120
        val iso = Iso(w, h, w / 2.0, h - 8.0, 900L + s)
        val sd = s.toDouble()
        val poles = ArrayList<Pair<Double, Double>>()
        for (i in 0..s) for (j in 0..s) if (i == 0 || j == 0 || i == s || j == s) poles += (i * 0.92 + 0.04) * sd / s to (j * 0.92 + 0.04) * sd / s
        poles.sortByDescending { it.first + it.second }
        val pc = rgb(0xb08a58)
        for ((x, y) in poles) iso.line(iso.p(x, y, 0.0), iso.p(x, y, 40.0), pc, 2f)
        for (z in doubleArrayOf(14.0, 30.0)) {
            iso.line(iso.p(0.04, 0.04, z), iso.p(sd - 0.04, 0.04, z), pc.mul(0.9), 2f)
            iso.line(iso.p(0.04, 0.04, z), iso.p(0.04, sd - 0.04, z), pc, 2f)
        }
        iso.line(iso.p(0.04, 0.04, 0.0), iso.p(sd - 0.04, 0.04, 30.0), pc.mul(0.8), 1.4f)
        // камни и доски у площадки
        iso.box(0.1, -0.05, 0.4, 0.15, 0.0, 6.0, rgb(0xa8a8a8), topColor = rgb(0xb8b8b8))
        iso.box(0.5, -0.05, 1.0, 0.1, 0.0, 4.0, rgb(0xd8b070), topColor = rgb(0xe0c080))
        return iso.toSprite("scaffold_$s")
    }

    // ---------- жители ----------

    /**
     * person_<профессия>_<направление: 0 вправо-вниз, 1 вправо-вверх, 2 и 3 - то же влево>_<кадр 0..3>[_c - с грузом над головой].
     * Запасной вариант, если нет рендеров Blender (tools/blender/people.py); 2 и 3 - отражение 0 и 1.
     */
    private fun people(): List<Sprite> {
        val out = ArrayList<Sprite>()
        val shirts = intArrayOf(0xe8dcc0, 0x3a64b0, 0xd8822a)
        for (job in 0 until 3) for (dir in 0 until 2) for (f in 0 until 4) for (carry in 0..1) {
            val p = person(job, shirts[job], dir, f, carry == 1)
            out += p
            out += mirror(p, p.name.replaceFirst("person_${job}_$dir", "person_${job}_${dir + 2}"))
        }
        return out
    }

    private fun mirror(s: Sprite, name: String): Sprite {
        val w = s.img.width
        val out = BufferedImage(w, s.img.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until s.img.height) for (x in 0 until w) out.setRGB(w - 1 - x, y, s.img.getRGB(x, y))
        return Sprite(name, out, w - s.ax, s.ay)
    }

    private fun person(job: Int, shirt: Int, dir: Int, frame: Int, carry: Boolean): Sprite {
        val iso = Iso(18, 34, 9.0, 32.0, 1000L + job)
        val g = iso.g
        val ph = frame * PI / 2
        val step = sin(ph) * 2.2
        val bob = -Math.abs(cos(ph)) * 0.8
        // тень
        g.paint = Color(0, 0, 0, 60)
        g.fill(Ellipse2D.Double(3.0, 30.0, 12.0, 4.0))
        val pants = rgb(0x4a3a2c)
        // ноги
        iso.fill(RoundRectangle2D.Double(6.0 + step * 0.4, 21.0 + bob, 2.6, 10.0 - step.coerceAtLeast(0.0) * 0.4, 1.5, 1.5), pants, grad = 0.0)
        iso.fill(RoundRectangle2D.Double(9.4 - step * 0.4, 21.0 + bob, 2.6, 10.0 + step.coerceAtMost(0.0) * 0.4, 1.5, 1.5), pants, grad = 0.0)
        // туловище
        val body = RoundRectangle2D.Double(4.5, 11.0 + bob, 9.0, 12.0, 4.0, 4.0)
        iso.fill(body, rgb(shirt), grad = 0.2)
        g.paint = rgb(0x5a4030); g.fill(java.awt.geom.Rectangle2D.Double(4.7, 19.5 + bob, 8.6, 1.4))   // пояс
        // руки
        val skin = rgb(0xf0c8a0)
        if (carry) {
            iso.line(Point2D.Double(5.5, 13.0 + bob), Point2D.Double(5.0, 4.0 + bob), rgb(shirt).mul(0.85), 2.4f)
            iso.line(Point2D.Double(12.5, 13.0 + bob), Point2D.Double(13.0, 4.0 + bob), rgb(shirt).mul(0.85), 2.4f)
        } else {
            iso.line(Point2D.Double(4.8, 13.0 + bob), Point2D.Double(4.4 - step * 0.5, 20.0 + bob), rgb(shirt).mul(0.85), 2.4f)
            iso.line(Point2D.Double(13.2, 13.0 + bob), Point2D.Double(13.6 + step * 0.5, 20.0 + bob), rgb(shirt).mul(0.85), 2.4f)
        }
        // голова
        val head = Ellipse2D.Double(5.5, 3.0 + bob, 7.0, 7.4)
        iso.fill(head, skin, grad = 0.15)
        val hair = if (job == 2) rgb(0xc8a040) else rgb(0x5a3a22)
        if (dir == 0) {
            g.paint = hair; g.fill(java.awt.geom.Arc2D.Double(5.3, 2.6 + bob, 7.4, 5.0, 0.0, 180.0, java.awt.geom.Arc2D.CHORD))
            g.paint = rgb(0x2a2020)
            g.fill(Ellipse2D.Double(7.2, 6.4 + bob, 1.1, 1.2)); g.fill(Ellipse2D.Double(9.8, 6.4 + bob, 1.1, 1.2))
        } else {
            g.paint = hair; g.fill(Ellipse2D.Double(5.4, 2.8 + bob, 7.2, 6.6))
        }
        if (job == 2) {
            // строитель в шапке
            g.paint = rgb(0xb06a20); g.fill(java.awt.geom.Arc2D.Double(4.8, 1.6 + bob, 8.4, 6.0, 0.0, 180.0, java.awt.geom.Arc2D.CHORD))
        }
        iso.g.dispose()
        return Sprite("person_${job}_${dir}_${frame}" + if (carry) "_c" else "", iso.img, px(9), px(2))
    }

    // ---------- значки и прочее ----------

    private fun icons(): List<Sprite> {
        fun icon(name: String, bg: Int, glyph: (java.awt.Graphics2D) -> Unit): Sprite {
            val iso = Iso(28, 28, 14.0, 28.0, 1)
            val g = iso.g
            g.paint = Color(32, 32, 32); g.fill(Ellipse2D.Double(1.0, 1.0, 26.0, 26.0))
            g.paint = RadialGradientPaint(Point2D.Double(10.0, 9.0), 16f, floatArrayOf(0f, 1f), arrayOf(rgb(bg).mul(1.3), rgb(bg).mul(0.85)))
            g.fill(Ellipse2D.Double(3.0, 3.0, 22.0, 22.0))
            g.paint = Color.WHITE
            g.stroke = java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
            glyph(g)
            g.dispose()
            return Sprite(name, iso.img, px(14), 0)
        }
        return listOf(
            icon("icon_no_workers", 0x7a7a80) { it.fill(Ellipse2D.Double(10.5, 6.0, 7.0, 7.0)); it.fill(RoundRectangle2D.Double(9.0, 14.0, 10.0, 8.0, 4.0, 4.0)) },
            icon("icon_no_resource", 0x8a6a3a) { it.fill(java.awt.Polygon(intArrayOf(14, 7, 21), intArrayOf(5, 19, 19), 3)); it.fillRect(13, 19, 3, 4) },
            icon("icon_output_full", 0xc89a20) { it.fillRect(6, 14, 7, 7); it.fillRect(15, 14, 7, 7); it.fillRect(10, 6, 8, 7) },
            icon("icon_no_input", 0xc03a30) { it.drawRect(8, 8, 12, 12) },
            icon("icon_season", 0x4a7ad0) { g -> g.drawLine(14, 5, 14, 23); g.drawLine(5, 14, 23, 14); g.drawLine(8, 8, 20, 20); g.drawLine(20, 8, 8, 20) },
            icon("icon_far", 0x8a50b0) { it.fill(Ellipse2D.Double(6.0, 16.0, 5.0, 7.0)); it.fill(Ellipse2D.Double(12.0, 10.0, 5.0, 7.0)); it.fill(Ellipse2D.Double(18.0, 4.0, 5.0, 7.0)) },
            icon("icon_unpaid", 0x8a6a2a) { g ->
                g.color = java.awt.Color(0xf2c030); g.fill(Ellipse2D.Double(5.0, 5.0, 18.0, 18.0))
                g.color = java.awt.Color(0xb88410); g.stroke = java.awt.BasicStroke(1.5f); g.draw(Ellipse2D.Double(8.0, 8.0, 12.0, 12.0))
                g.color = java.awt.Color(0xd02a20); g.stroke = java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND); g.drawLine(5, 23, 23, 5)
            },
            icon("icon_limit",0x3a9a5a) { it.fillRect(7, 9, 14, 4); it.fillRect(7, 16, 14, 4) },
            icon("icon_flood", 0x3a7bd5) { g ->
                g.stroke = java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
                for (k in 0 until 3) {
                    val y = 9.0 + k * 5.5
                    val p = java.awt.geom.Path2D.Double(); p.moveTo(5.0, y)
                    p.curveTo(8.0, y - 3, 11.0, y - 3, 14.0, y); p.curveTo(17.0, y + 3, 20.0, y + 3, 23.0, y)
                    g.draw(p)
                }
            },
            icon("icon_hungry", 0xd04020) { it.fillRect(12, 5, 4, 12); it.fill(Ellipse2D.Double(11.5, 19.0, 5.0, 5.0)) },
            icon("icon_fire", 0xd8401c) {
                val p = java.awt.geom.Path2D.Double()
                p.moveTo(14.0, 4.0); p.curveTo(15.0, 9.0, 21.0, 11.0, 20.5, 17.0); p.curveTo(20.0, 22.0, 17.0, 23.5, 14.0, 23.5)
                p.curveTo(11.0, 23.5, 7.5, 22.0, 7.5, 17.0); p.curveTo(7.5, 13.0, 10.0, 12.0, 11.0, 8.5); p.curveTo(12.5, 10.0, 12.0, 12.0, 12.5, 12.5)
                p.curveTo(13.5, 10.0, 12.5, 6.5, 14.0, 4.0); p.closePath()
                it.fill(p)
            },
            icon("icon_sick", 0x3a9a4a) { it.fillRect(12, 6, 4, 16); it.fillRect(6, 12, 16, 4) },
            icon("icon_upgrade", 0xc89a20) { it.fill(java.awt.Polygon(intArrayOf(14, 6, 11, 11, 17, 17, 22), intArrayOf(4, 13, 13, 23, 23, 13, 13), 7)) },
        )
    }

    /** Мешок над головой носильщика (белый - в игре красится цветом ресурса) */
    private fun sack(): Sprite {
        val iso = Iso(16, 14, 8.0, 14.0, 2)
        val shape = RoundRectangle2D.Double(1.5, 3.0, 13.0, 10.0, 7.0, 7.0)
        iso.g.paint = RadialGradientPaint(Point2D.Double(6.0, 6.0), 10f, floatArrayOf(0f, 1f), arrayOf(Color.WHITE, Color(170, 170, 170)))
        iso.g.fill(shape)
        iso.stroke(shape, Color(90, 90, 90), 1.2f)
        iso.line(Point2D.Double(6.0, 3.5), Point2D.Double(10.0, 3.5), Color(90, 90, 90), 1.4f)
        iso.g.dispose()
        return Sprite("sack", iso.img, px(8), 0)
    }

    private fun white(): Sprite {
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 8) for (x in 0 until 8) img.setRGB(x, y, -1)
        return Sprite("white", img, 0, 0)
    }
}
