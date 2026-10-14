package town.tools

import java.awt.Color
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.sin

/*
 * Переходы земли по двойной сетке: спрайт dual_<слой>_<маска>_<вариант> - ромб размером с клетку,
 * поставленный на узел сетки (между четырьмя клетками). Маска - какие из четырёх соседних клеток
 * принадлежат слою: бит 0 - клетка (x-1, y-1), 1 - (x, y-1), 2 - (x-1, y), 3 - (x, y).
 * Граница проходит по середине между центрами клеток и скругляется на углах (билинейная смесь масок),
 * края шевелит шум, равный нулю на краях ромба - соседние спрайты стыкуются при любых вариантах.
 * Маска 15 (все четыре клетки) рисуется обычной клеткой слоя, маска 0 - ничем.
 *
 * Мелкие предметы prop_*: кусты и камыш (объекты, сортируются с жителями), цветы и камни (на земле).
 */
object Terrain {
    const val VARIANTS = 2

    fun all(art: List<Sprite>): List<Sprite> {
        fun img(name: String) = art.first { it.name == name }.img
        val out = ArrayList<Sprite>()
        for (v in 0 until VARIANTS) for (m in 1..14) {
            out += layer("forest", m, v, img("tile_forest_0"), lo = 0.4, hi = 0.6, amp = 0.32)
            out += layer("forest_snow", m, v, img("tile_forest_snow_0"), lo = 0.4, hi = 0.6, amp = 0.32)
            out += layer("rock", m, v, img("tile_rock_0"), lo = 0.44, hi = 0.58, amp = 0.28)
            out += layer("road", m, v, img("tile_road_0"), lo = 0.5, hi = 0.6, amp = 0.12, rim = true)
            out += layer("paved", m, v, img("tile_paved_0"), lo = 0.5, hi = 0.6, amp = 0.12, rim = true)
            out += water(m, v, img("tile_water_0"), snow = false)
            out += water(m, v, img("tile_water_0"), snow = true)
        }
        // сплошные подложки: луг и клетки слоёв целиком (маска 15)
        for (t in art.filter { it.name.startsWith("tile_") }) out += solid("base_" + t.name.removePrefix("tile_"), t.img)
        out += props()
        return out
    }

    /**
     * Подложка луга: клетка без прозрачных углов (углы берутся у соседних ромбов сдвигом на полклетки).
     * Подложки перекрываются - на отдалении мипмапы не смешивают углы с фоном и сетка не проступает.
     */
    private fun solid(name: String, tex: BufferedImage): Sprite {
        val w = tex.width; val h = tex.height
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) {
            var c = tex.getRGB(x, y)
            if ((c ushr 24) < 255) {
                for ((dx, dy) in listOf(w / 2 to h / 2, -w / 2 to h / 2, w / 2 to -h / 2, -w / 2 to -h / 2)) {
                    val sx = x + dx; val sy = y + dy
                    if (sx in 0 until w && sy in 0 until h && (tex.getRGB(sx, sy) ushr 24) == 255) { c = tex.getRGB(sx, sy); break }
                }
                if ((c ushr 24) < 255) c = tex.getRGB(w / 2, h / 2)
            }
            img.setRGB(x, y, c or (255 shl 24))
        }
        return Sprite(name, img, w / 2, 0)
    }

    // ---------- маски ----------

    /** Доля слоя в точке (u, v) ромба с шумом по краю */
    private fun field(mask: Int, variant: Int, u: Double, v: Double, amp: Double, seed: Int): Double {
        fun b(i: Int) = if (mask and (1 shl i) != 0) 1.0 else 0.0
        val f = b(0) * (1 - u) * (1 - v) + b(1) * u * (1 - v) + b(2) * (1 - u) * v + b(3) * u * v
        val ph = (seed * 7 + variant * 13 + mask * 3).toDouble()
        val n = sin(2 * PI * (2 * u + v) + ph) * 0.55 + sin(2 * PI * (3 * v - u) + ph * 1.7) * 0.3 + sin(2 * PI * (5 * u + 4 * v) + ph * 0.6) * 0.15
        // на краях ромба шум гаснет - стыки с соседними спрайтами совпадают
        val fade = sin(PI * u) * sin(PI * v)
        return f + n * amp * fade
    }

    private fun smooth(e0: Double, e1: Double, x: Double): Double {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Обход пикселей ромба: (x, y, u, v) по всей картинке TW x TH в пикселях холста */
    private inline fun each(w: Int, h: Int, f: (Int, Int, Double, Double) -> Unit) {
        for (y in 0 until h) for (x in 0 until w) {
            val a = (x + 0.5 - w / 2.0) / (w / 2.0)   // u - v
            val b = (h - (y + 0.5)) / (h / 2.0)        // u + v
            val u = (a + b) / 2; val v = (b - a) / 2
            if (u < -0.02 || v < -0.02 || u > 1.02 || v > 1.02) continue
            f(x, y, u.coerceIn(0.0, 1.0), v.coerceIn(0.0, 1.0))
        }
    }

    private fun argb(a: Double, c: Int): Int = ((a.coerceIn(0.0, 1.0) * ((c ushr 24) and 255)).toInt() shl 24) or (c and 0xffffff)

    private fun mixRgb(c1: Int, c2: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun ch(s: Int) = (((c1 shr s) and 255) * (1 - k) + ((c2 shr s) and 255) * k).toInt()
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun mulRgb(c: Int, k: Double): Int {
        fun ch(s: Int) = (((c shr s) and 255) * k).toInt().coerceIn(0, 255)
        return (c and 0xff000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Слой с текстурой клетки: прозрачность по маске, у дороги - тёмная кромка */
    private fun layer(name: String, mask: Int, variant: Int, tex: BufferedImage, lo: Double, hi: Double, amp: Double, rim: Boolean = false): Sprite {
        val w = tex.width; val h = tex.height
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        each(w, h) { x, y, u, v ->
            val f = field(mask, variant, u, v, amp, name.hashCode())
            val a = smooth(lo, hi, f)
            if (a <= 0.0) return@each
            var c = tex.getRGB(x, y)
            if ((c ushr 24) == 0) c = tex.getRGB(w / 2, h / 2)
            if (rim) c = mulRgb(c, 0.78 + 0.22 * smooth(hi, hi + 0.08, f))
            img.setRGB(x, y, argb(a, c or (255 shl 24)))
        }
        return Sprite("dual_${name}_${mask}_$variant", img, w / 2, 0)
    }

    /** Берег: песок (зимой снег) -> пена -> отмель -> вода клетки */
    private fun water(mask: Int, variant: Int, tex: BufferedImage, snow: Boolean): Sprite {
        val w = tex.width; val h = tex.height
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val sand = if (snow) 0xe6ecf4 else 0xe0c890
        val wet = if (snow) 0xb8c4d4 else 0xa8905c
        val shallow = if (snow) 0x6a9cc0 else 0x62b0c8
        each(w, h) { x, y, u, v ->
            val f = field(mask, variant, u, v, 0.26, 4242)
            if (f < 0.28) return@each
            var water = tex.getRGB(x, y)
            if ((water ushr 24) == 0) water = tex.getRGB(w / 2, h / 2)
            val c: Int; val a: Double
            if (f < 0.47) {
                a = smooth(0.28, 0.36, f)
                c = mixRgb(sand, wet, smooth(0.38, 0.47, f))
            } else {
                a = 1.0
                val deep = mixRgb(shallow, water, smooth(0.52, 0.82, f))
                val foam = 1 - smooth(0.0, 0.05, Math.abs(f - 0.5))
                c = mixRgb(mixRgb(wet, deep, smooth(0.46, 0.5, f)), 0xf4f8fa, foam * 0.75)
            }
            img.setRGB(x, y, argb(a, c))
        }
        return Sprite("dual_${if (snow) "water_snow" else "water"}_${mask}_$variant", img, w / 2, 0)
    }

    // ---------- мелкие предметы ----------

    private fun props(): List<Sprite> {
        val out = ArrayList<Sprite>()
        for (v in 0 until 3) { out += bush(v, false); out += bush(v, true) }
        for (v in 0 until 3) out += flowers(v)
        for (v in 0 until 2) out += stones(v)
        for (v in 0 until 2) out += reeds(v)
        for (v in 0 until 2) out += stump(v)
        for (f in 0 until 3) out += fish(f)
        out += diamond()
        for (v in 0 until 2) { out += bridge(v, false); out += bridge(v, true) }
        return out
    }

    /** Пень на месте срубленного дерева (с щепой) */
    private fun stump(v: Int): Sprite {
        val iso = Iso(30, 26, 15.0, 20.0, 1600L + v)
        val r = iso.rnd
        repeat(6) { iso.g.paint = rgb(0xd8b078).alpha(200); iso.g.fill(Ellipse2D.Double(5.0 + r.nextDouble() * 20, 15.0 + r.nextDouble() * 6, 3.0, 1.6)) }
        val side = java.awt.geom.Rectangle2D.Double(9.0, 9.0, 12.0, 9.0)
        iso.fill(side, rgb(0x7a5232), grad = 0.25)
        iso.fill(Ellipse2D.Double(9.0, 18.0 - 3.5, 12.0, 7.0), rgb(0x7a5232), grad = 0.2)
        val top = Ellipse2D.Double(9.0, 6.0, 12.0, 6.0)
        iso.fill(top, rgb(0xe0b878), grad = 0.15)
        iso.stroke(Ellipse2D.Double(12.0, 7.5, 6.0, 3.0), rgb(0xa88050), 0.8f)
        iso.ox = 15.0; iso.oy = 18.0
        return iso.toSprite("prop_stump_$v")
    }

    /** Косяк рыбы: круги на воде и спинки рыб, 3 кадра (круги расходятся) */
    private fun fish(f: Int): Sprite {
        val iso = Iso(44, 24, 22.0, 12.0, 1700L)
        for (k in 0 until 3) {
            val rr = 6.0 + ((k * 6 + f * 2) % 18)
            iso.g.stroke = java.awt.BasicStroke(1.2f)
            iso.g.paint = Color(230, 245, 255, (200 - rr * 8).toInt().coerceIn(30, 200))
            iso.g.draw(Ellipse2D.Double(22.0 - rr, 12.0 - rr / 2, rr * 2, rr))
        }
        val r = java.util.Random(1700L)
        repeat(4) {
            val x = 10.0 + r.nextDouble() * 24; val y = 8.0 + r.nextDouble() * 8
            val dx = if (f == 1) 1.5 else if (f == 2) -1.0 else 0.0
            iso.g.paint = Color(40, 60, 80, 200); iso.g.fill(Ellipse2D.Double(x + dx, y, 6.0, 2.2))
            iso.g.paint = Color(200, 220, 235, 160); iso.g.fill(Ellipse2D.Double(x + dx + 1, y + 0.2, 3.0, 0.9))
        }
        iso.ox = 22.0; iso.oy = 12.0
        return iso.toSprite("prop_fish_$f")
    }

    /**
     * Мост: дощатый настил поперёк реки с перилами по двум сторонам, ромб клетки, рисуется поверх воды.
     * Вариант 0 - доски вдоль оси x (идут поперёк движения), перила вдоль x; 1 - то же вдоль y.
     */
    private fun bridge(v: Int, snow: Boolean): Sprite {
        val h = TH + 44
        val iso = Iso(TW, h, TW / 2.0, h.toDouble(), 900L + v)
        iso.snow = snow
        val alongX = v == 0
        fun box(a0: Double, b0: Double, a1: Double, b1: Double, z0: Double, z1: Double, c: Color, top: Color) {
            if (alongX) iso.box(a0, b0, a1, b1, z0, z1, c, topColor = top) else iso.box(b0, a0, b1, a1, z0, z1, c, topColor = top)
        }
        fun pt(a: Double, b: Double, z: Double) = if (alongX) iso.p(a, b, z) else iso.p(b, a, z)
        val wood = rgb(0xa87a48); val dark = rgb(0x5a3a20)
        val lo = 0.1; val hi = 0.9
        val deckTop = 6.0
        // тень на воде под настилом
        val sh = iso.path(pt(0.02, lo + 0.02, 0.0), pt(0.98, lo + 0.02, 0.0), pt(0.98, hi + 0.04, 0.0), pt(0.02, hi + 0.04, 0.0))
        iso.g.paint = Color(10, 30, 60, 70); iso.g.fill(sh)
        fun rail(b: Double) {
            for (a in listOf(0.06, 0.5, 0.94)) box(a - 0.035, b - 0.035, a + 0.035, b + 0.035, deckTop - 1.0, deckTop + 12.0, rgb(0x8a5e34), rgb(0x9a6e42))
            for (z in listOf(deckTop + 5.0, deckTop + 10.0)) iso.line(pt(0.06, b, z), pt(0.94, b, z), dark, 2.6f)
            for (z in listOf(deckTop + 5.0, deckTop + 10.0)) iso.line(pt(0.06, b, z + 0.5), pt(0.94, b, z + 0.5), rgb(0xc89a60), 1.2f)
            if (snow) iso.line(pt(0.06, b, deckTop + 11.4), pt(0.94, b, deckTop + 11.4), iso.snowCol(1.12), 2.4f)
        }
        // дальний борт, настил, ближний борт (порядок по глубине)
        rail(hi)
        box(0.0, lo, 1.0, hi, 2.0, deckTop, wood, rgb(0xb8884e))
        val n = 8
        for (k in 1 until n) {
            val a = k / n.toDouble()
            iso.line(pt(a, lo, deckTop), pt(a, hi, deckTop), if (snow) Color(150, 165, 190, 120) else dark.alpha(150), 1.2f)
        }
        rail(lo)
        return iso.toSprite(if (snow) "bridge_snow_$v" else "bridge_$v")
    }

    /** Белый ромб клетки - подсветка (плодородие, подземная вода, ресурс в радиусе) */
    private fun diamond(): Sprite {
        val iso = Iso(TW, TH, TW / 2.0, TH.toDouble(), 1)
        iso.g.paint = Color.WHITE
        iso.g.fill(iso.path(iso.p(0.0, 0.0, 0.0), iso.p(1.0, 0.0, 0.0), iso.p(1.0, 1.0, 0.0), iso.p(0.0, 1.0, 0.0)))
        iso.g.dispose()
        return Sprite("diamond", iso.img, px(TW / 2), 0)
    }

    private fun bush(v: Int, snow: Boolean): Sprite {
        val iso = Iso(44, 34, 22.0, 30.0, 1200L + v)
        iso.shadow(0.1, 0.1, 0.4, 0.4, 1.0)
        val greens = intArrayOf(0x4f8a3a, 0x5e9a44, 0x447c34)
        val r = iso.rnd
        val blobs = (0 until 5).map { doubleArrayOf(22.0 + (r.nextDouble() - 0.5) * 18, 22.0 - r.nextDouble() * 8, 6.0 + r.nextDouble() * 3) }.sortedByDescending { it[1] }
        for ((k, b) in blobs.withIndex()) {
            val c = rgb(greens[(k + v) % 3]).mul(0.85 + k * 0.05)
            val e = Ellipse2D.Double(b[0] - b[2], b[1] - b[2] * 0.85, b[2] * 2, b[2] * 1.7)
            iso.g.paint = java.awt.RadialGradientPaint(Point2D.Double(b[0] - b[2] * 0.4, b[1] - b[2] * 0.5), (b[2] * 1.6).toFloat(), floatArrayOf(0f, 1f), arrayOf(c.mul(1.25), c.mul(0.7)))
            iso.g.fill(e)
            iso.stroke(e, c.mul(0.45).alpha(130), 0.9f)
            if (snow) {
                // снежная шапка на верхней половине пятна
                val old = iso.g.clip; iso.g.clip(e)
                iso.g.paint = java.awt.RadialGradientPaint(Point2D.Double(b[0] - b[2] * 0.3, b[1] - b[2]), (b[2] * 1.4).toFloat(), floatArrayOf(0f, 1f), arrayOf(iso.snowCol(1.15), iso.snowCol(0.85)))
                iso.g.fill(Ellipse2D.Double(b[0] - b[2] * 1.1, b[1] - b[2] * 1.2, b[2] * 2.2, b[2] * 1.25))
                iso.g.clip = old
            }
        }
        if (v == 2 && !snow) repeat(5) {
            iso.g.paint = if (it % 2 == 0) rgb(0xd8443a) else rgb(0xf0e8e0)
            iso.g.fill(Ellipse2D.Double(14.0 + r.nextDouble() * 16, 13.0 + r.nextDouble() * 9, 2.4, 2.4))
        }
        iso.ox = 22.0; iso.oy = 28.0
        return iso.toSprite(if (snow) "prop_bushsnow_$v" else "prop_bush_$v")
    }

    private fun flowers(v: Int): Sprite {
        val iso = Iso(36, 24, 18.0, 20.0, 1300L + v)
        val r = iso.rnd
        val cols = arrayOf(intArrayOf(0xf4d84a, 0xfff6d0), intArrayOf(0xe8608a, 0xf8c8d8), intArrayOf(0x9a80e8, 0xf0ecff))[v]
        // кустик листьев и цветы шапкой поверх
        repeat(9) {
            val x = 8.0 + r.nextDouble() * 20; val y = 9.0 + r.nextDouble() * 8
            iso.g.paint = mix(rgb(0x3f7a30), rgb(0x5a9a3c), r.nextDouble()); iso.g.fill(Ellipse2D.Double(x - 3, y - 1.5, 6.0, 3.6))
        }
        repeat(14) {
            val x = 8.0 + r.nextDouble() * 20; val y = 6.0 + r.nextDouble() * 9
            val c = rgb(cols[it % 2])
            iso.g.paint = c.mul(0.6); iso.g.fill(Ellipse2D.Double(x - 1.6, y - 1.0, 3.6, 3.2))
            iso.g.paint = c; iso.g.fill(Ellipse2D.Double(x - 1.8, y - 1.6, 3.4, 3.0))
        }
        return iso.toSprite("prop_flowers_$v")
    }

    private fun stones(v: Int): Sprite {
        val iso = Iso(30, 18, 15.0, 14.0, 1400L + v)
        val r = iso.rnd
        repeat(3 + v) {
            val x = 6.0 + r.nextDouble() * 18; val y = 6.0 + r.nextDouble() * 7
            val e = Ellipse2D.Double(x, y, 4.0 + r.nextDouble() * 3.5, 2.6 + r.nextDouble() * 2)
            iso.g.paint = Color(0, 0, 0, 50); iso.g.fill(Ellipse2D.Double(e.x + 1, e.y + 1.2, e.width, e.height))
            iso.fill(e, rgb(0xa8a49a).mul(0.85 + r.nextDouble() * 0.25), grad = 0.3)
        }
        return iso.toSprite("prop_stones_$v")
    }

    private fun reeds(v: Int): Sprite {
        val iso = Iso(30, 40, 15.0, 37.0, 1500L + v)
        val r = iso.rnd
        repeat(11) {
            val x = 6.0 + r.nextDouble() * 18; val top = 10.0 + r.nextDouble() * 12
            iso.line(Point2D.Double(x, 37.0), Point2D.Double(x + (r.nextDouble() - 0.5) * 5, top), mix(rgb(0x6a8a3a), rgb(0x9aaa4a), r.nextDouble()), 1.1f)
            if (it % 3 == 0) { iso.g.paint = rgb(0x6a4a2a); iso.g.fill(java.awt.geom.RoundRectangle2D.Double(x - 1.0, top - 1, 2.2, 6.0, 2.0, 2.0)) }
        }
        return iso.toSprite("prop_reeds_$v")
    }
}
