package town.tools

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

/**
 * Масштаб рисования к миру: все размеры в коде генератора заданы для спрайтов вдвое крупнее мира (S),
 * а холсты дополнительно увеличены в Q раз (полный атлас - 3 пикселя на единицу мира).
 * Лёгкий атлас для слабых телефонов уменьшается из полного вдвое (Assets.pack).
 */
const val S = 2
const val Q = 1.5
const val ATLAS_SCALE = S * Q
const val TW = 32 * S
const val TH = 16 * S

/** Размер в единицах рисования -> пиксели холста */
fun px(v: Double) = Math.round(v * Q).toInt()
fun px(v: Int) = px(v.toDouble())

/** Холст w x h в единицах рисования (пикселей в Q раз больше) со сглаживанием */
fun canvas(w: Int, h: Int): Pair<BufferedImage, Graphics2D> {
    val img = BufferedImage(Math.ceil(w * Q).toInt(), Math.ceil(h * Q).toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.scale(Q, Q)
    return img to g
}

/** Готовый спрайт: картинка и точка привязки (ax от левого края, ay от нижнего) */
class Sprite(val name: String, val img: BufferedImage, val ax: Int, val ay: Int)

fun rgb(hex: Int) = Color(hex shr 16 and 255, hex shr 8 and 255, hex and 255)

fun Color.mul(k: Double): Color = Color(
    (red * k).toInt().coerceIn(0, 255), (green * k).toInt().coerceIn(0, 255), (blue * k).toInt().coerceIn(0, 255), alpha,
)

fun Color.alpha(a: Int) = Color(red, green, blue, a)

fun mix(a: Color, b: Color, t: Double) = Color(
    (a.red + (b.red - a.red) * t).toInt(), (a.green + (b.green - a.green) * t).toInt(), (a.blue + (b.blue - a.blue) * t).toInt(),
)

/**
 * Холст в изометрии: точка мира (x, y в клетках, z в пикселях атласа вверх) -> пиксель картинки.
 * (0, 0, 0) - нижний угол ромба основания, он же точка привязки спрайта.
 */
class Iso(w: Int, h: Int, var ox: Double, var oy: Double, seed: Long) {
    private val cv = canvas(w, h)
    val img: BufferedImage = cv.first
    val g: Graphics2D = cv.second
    val rnd = java.util.Random(seed)

    /** Зимний вариант: крыши, шатры и верх коробок покрыты снегом (остальное рисуется так же) */
    var snow = false

    fun p(x: Double, y: Double, z: Double) = Point2D.Double(ox + (x - y) * TW / 2.0, oy - (x + y) * TH / 2.0 - z)

    fun path(vararg pts: Point2D.Double): Path2D.Double {
        val pa = Path2D.Double()
        pa.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) pa.lineTo(pts[i].x, pts[i].y)
        pa.closePath()
        return pa
    }

    /** Залить многоугольник с мягким градиентом (светлее сверху слева) и обвести тёмным */
    fun fill(shape: java.awt.Shape, base: Color, outline: Boolean = true, grad: Double = 0.12) {
        val b = shape.bounds2D
        g.paint = GradientPaint(
            b.minX.toFloat(), b.minY.toFloat(), base.mul(1 + grad),
            b.maxX.toFloat(), b.maxY.toFloat(), base.mul(1 - grad),
        )
        g.fill(shape)
        if (outline) stroke(shape, base.mul(0.5), 1.6f)
    }

    fun stroke(shape: java.awt.Shape, c: Color, w: Float) {
        g.paint = c
        g.stroke = BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
    }

    fun line(a: Point2D.Double, b: Point2D.Double, c: Color, w: Float) {
        g.paint = c
        g.stroke = BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(java.awt.geom.Line2D.Double(a, b))
    }

    /** Мягкая тень на земле под основанием (эллипс, сдвинутый вправо-вниз) */
    fun shadow(x0: Double, y0: Double, x1: Double, y1: Double, k: Double = 1.0) {
        val c = p((x0 + x1) / 2, (y0 + y1) / 2, 0.0)
        val rw = (x1 - x0 + y1 - y0) * TW / 4.0 * 1.05 * k
        val rh = rw / 2
        val old = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f)
        g.paint = RadialGradientPaint(
            Point2D.Double(c.x + rw * 0.15, c.y + rh * 0.1), rw.toFloat(),
            floatArrayOf(0f, 0.7f, 1f), arrayOf(Color(0, 0, 0, 255), Color(0, 0, 0, 160), Color(0, 0, 0, 0)),
        )
        g.fill(Ellipse2D.Double(c.x - rw + rw * 0.15, c.y - rh + rh * 0.1, rw * 2, rh * 2))
        g.composite = old
    }

    /** Площадка под зданием: утоптанная земля ромбом основания */
    fun ground(x0: Double, y0: Double, x1: Double, y1: Double, c: Color) {
        val shape = path(p(x0, y0, 0.0), p(x1, y0, 0.0), p(x1, y1, 0.0), p(x0, y1, 0.0))
        fill(shape, c, outline = false, grad = 0.05)
        speckle(shape, c, 0.12, 60)
    }

    /** Пятнышки по фигуре - «мазки» */
    fun speckle(shape: java.awt.Shape, c: Color, amp: Double, n: Int) {
        val b = shape.bounds2D
        val old = g.clip
        g.clip(shape)
        repeat(n) {
            val k = 1 + (rnd.nextDouble() * 2 - 1) * amp
            g.paint = c.mul(k).alpha(150)
            val x = b.minX + rnd.nextDouble() * b.width; val y = b.minY + rnd.nextDouble() * b.height
            val r = 1.0 + rnd.nextDouble() * 2.0
            g.fill(Ellipse2D.Double(x, y, r * 1.6, r))
        }
        g.clip = old
    }

    // ---------- коробки и крыши ----------

    /** Стена: грань, заданная отображением (u, v) в [0..1]^2 -> точка; u вдоль стены, v вверх */
    inner class Face(val f: (Double, Double) -> Point2D.Double, val base: Color) {
        val shape get() = path(f(0.0, 0.0), f(1.0, 0.0), f(1.0, 1.0), f(0.0, 1.0))

        fun quad(u0: Double, v0: Double, u1: Double, v1: Double) = path(f(u0, v0), f(u1, v0), f(u1, v1), f(u0, v1))

        /** Окно: тёмное стекло, светлая рама, переплёт */
        fun window(u0: Double, v0: Double, u1: Double, v1: Double, glassColor: Color = rgb(0x5a6a80)) {
            fill(quad(u0, v0, u1, v1), rgb(0x3a3020), outline = false)
            val glass = quad(u0 + 0.01, v0 + 0.03, u1 - 0.01, v1 - 0.03)
            fill(glass, glassColor, outline = false, grad = 0.3)
            stroke(quad(u0, v0, u1, v1), rgb(0xe8dcc0), 1.4f)
            line(f((u0 + u1) / 2, v0), f((u0 + u1) / 2, v1), rgb(0xe8dcc0), 1f)
            line(f(u0, (v0 + v1) / 2), f(u1, (v0 + v1) / 2), rgb(0xe8dcc0), 1f)
        }

        fun door(u0: Double, u1: Double, v1: Double, c: Color = rgb(0x6a4228)) {
            fill(quad(u0, 0.0, u1, v1), c)
            for (k in 1..2) line(f(u0 + (u1 - u0) * k / 3, 0.02), f(u0 + (u1 - u0) * k / 3, v1 - 0.02), c.mul(0.6), 1f)
            g.paint = rgb(0xd0b060)
            val k = f(u1 - (u1 - u0) * 0.2, v1 * 0.45)
            g.fill(Ellipse2D.Double(k.x - 1, k.y - 1, 2.4, 2.4))
        }

        /** Доски: горизонтальные линии через каждые step пикселей высоты */
        fun planks(rows: Int) {
            val old = g.clip; g.clip(shape)
            for (k in 1 until rows) {
                val v = k.toDouble() / rows
                line(f(0.0, v), f(1.0, v), base.mul(0.62).alpha(170), 1.1f)
            }
            for (k in 0..rows * 2) {
                val v = (k / 2 + rnd.nextDouble() * 0.6) / rows
                val u = rnd.nextDouble()
                line(f(u, v), f(min(1.0, u + 0.02), v), base.mul(0.7).alpha(120), 1f)
            }
            g.clip = old
        }

        /** Фахверк: тёмные балки по краям, середине и раскосы */
        fun timber(beam: Color = rgb(0x5a3a22)) {
            val old = g.clip; g.clip(shape)
            for (u in doubleArrayOf(0.02, 0.5, 0.98)) line(f(u, 0.0), f(u, 1.0), beam, 2.2f)
            line(f(0.0, 0.02), f(1.0, 0.02), beam, 2.2f)
            line(f(0.0, 0.52), f(1.0, 0.52), beam, 2f)
            line(f(0.0, 0.98), f(1.0, 0.98), beam, 2.2f)
            line(f(0.04, 0.52), f(0.46, 0.96), beam, 1.6f)
            line(f(0.54, 0.96), f(0.96, 0.52), beam, 1.6f)
            g.clip = old
        }

        /** Каменная кладка: ряды камней со смещением */
        fun stones(rows: Int, cols: Int) {
            val old = g.clip; g.clip(shape)
            for (r in 0 until rows) {
                val v0 = r.toDouble() / rows; val v1 = (r + 1.0) / rows
                val shift = if (r % 2 == 0) 0.0 else 0.5 / cols
                var u = -shift
                while (u < 1) {
                    val w = (0.7 + rnd.nextDouble() * 0.6) / cols
                    val q = quad(u + 0.008, v0 + 0.02, u + w - 0.008, v1 - 0.02)
                    g.paint = base.mul(0.9 + rnd.nextDouble() * 0.2)
                    g.fill(q)
                    stroke(q, base.mul(0.62).alpha(200), 0.9f)
                    u += w
                }
            }
            g.clip = old
        }

        fun paint(outline: Boolean = true) = fill(shape, base, outline, 0.08)
    }

    /** Левая видимая грань коробки (x = x0), освещена */
    fun left(x0: Double, y0: Double, y1: Double, z0: Double, z1: Double, base: Color) =
        Face({ u, v -> p(x0, y1 + (y0 - y1) * u, z0 + (z1 - z0) * v) }, base)

    /** Правая видимая грань коробки (y = y0), в тени */
    fun right(x0: Double, x1: Double, y0: Double, z0: Double, z1: Double, base: Color) =
        Face({ u, v -> p(x0 + (x1 - x0) * u, y0, z0 + (z1 - z0) * v) }, base.mul(0.74))

    fun top(x0: Double, y0: Double, x1: Double, y1: Double, z: Double, base: Color) {
        val shape = path(p(x0, y0, z), p(x1, y0, z), p(x1, y1, z), p(x0, y1, z))
        if (snow) { snowFill(shape, base, 1.12); return }
        fill(shape, base.mul(1.12))
    }

    // ---------- снег ----------

    /** Цвет снега: серо-голубой в тени, почти белый на свету (k - яркость плоскости, 0.7..1.15) */
    fun snowCol(k: Double) = mix(rgb(0x8d9cb8), rgb(0xf0f4fa), ((k - 0.68) / 0.45).coerceIn(0.0, 1.0))

    /** Плоскость под снегом: белая заливка с мягкой тенью, обводка цветом материала */
    private fun snowFill(shape: java.awt.Shape, c: Color, k: Double) {
        val sc = snowCol(k)
        val b = shape.bounds2D
        g.paint = GradientPaint(b.minX.toFloat(), b.minY.toFloat(), sc.mul(1.03), b.maxX.toFloat(), b.maxY.toFloat(), sc.mul(0.93))
        g.fill(shape)
        stroke(shape, mix(c.mul(0.5), rgb(0x70809c), 0.5), 1.4f)
    }

    private fun roofFill(shape: java.awt.Shape, c: Color, k: Double) {
        if (snow) snowFill(shape, c, k) else fill(shape, c)
    }

    /** Чуть неровная белая кромка снега вдоль карниза a-b (свой генератор, чтобы не сбивать rnd) */
    private fun eaveSnow(a: Point2D.Double, b: Point2D.Double, k: Double) {
        if (!snow) return
        val r = java.util.Random((a.x * 31 + a.y * 17 + b.x * 7).toLong())
        val len = Math.hypot(b.x - a.x, b.y - a.y)
        val n = (len / 3.5).toInt().coerceAtLeast(2)
        for (i in 0..n) {
            val t = i.toDouble() / n
            val rr = 2.0 + r.nextDouble() * 1.8
            val x = a.x + (b.x - a.x) * t; val y = a.y + (b.y - a.y) * t + r.nextDouble() * 1.5
            g.paint = snowCol(k + 0.05)
            g.fill(Ellipse2D.Double(x - rr, y - rr * 0.7, rr * 2, rr * 1.4))
        }
    }

    /** Несколько белых пятен снега на площадке (отдельный генератор) */
    fun snowPatches(x0: Double, y0: Double, x1: Double, y1: Double, n: Int, seed: Long) {
        val r = java.util.Random(seed)
        val d = path(p(x0, y0, 0.0), p(x1, y0, 0.0), p(x1, y1, 0.0), p(x0, y1, 0.0))
        var left = n
        while (left > 0) {
            val q = p(x0 + r.nextDouble() * (x1 - x0), y0 + r.nextDouble() * (y1 - y0), 0.0)
            if (!d.contains(q.x, q.y)) continue
            val w = 6.0 + r.nextDouble() * 9
            g.paint = snowCol(1.1)
            g.fill(Ellipse2D.Double(q.x - w / 2, q.y - w / 5, w, w / 2.5))
            g.paint = snowCol(0.85).alpha(160)
            g.fill(Ellipse2D.Double(q.x - w / 2 + 1, q.y + w / 12, w - 2, w / 6))
            left--
        }
    }

    /** Коробка с текстурой стен; возвращает грани для окон и дверей */
    fun box(
        x0: Double, y0: Double, x1: Double, y1: Double, z0: Double, z1: Double, base: Color,
        tex: String = "", topColor: Color? = null,
    ): Pair<Face, Face> {
        val l = left(x0, y0, y1, z0, z1, base)
        val r = right(x0, x1, y0, z0, z1, base)
        for (f in listOf(l, r)) {
            f.paint()
            when (tex) {
                "planks" -> f.planks(((z1 - z0) / 7).toInt().coerceAtLeast(2))
                "timber" -> f.timber()
                "stone" -> f.stones(((z1 - z0) / 7).toInt().coerceAtLeast(2), (((x1 - x0) + (y1 - y0)) * 3).toInt().coerceAtLeast(3))
            }
            stroke(f.shape, f.base.mul(0.5), 1.4f)
        }
        if (topColor != null) top(x0, y0, x1, y1, z1, topColor)
        return l to r
    }

    /**
     * Двускатная крыша над прямоугольником на высоте z, конёк на высоте z + h.
     * alongX - конёк вдоль x (скаты смотрят вправо и влево-вверх), иначе вдоль y. style: shingle, thatch, slate, plank.
     */
    fun roof(x0: Double, y0: Double, x1: Double, y1: Double, z: Double, h: Double, alongX: Boolean, c: Color, style: String = "shingle", gable: Color? = null) {
        val o = 0.12
        val eave = z - 3
        if (alongX) {
            val ym = (y0 + y1) / 2
            val back = path(p(x0 - o, y1 + o, eave), p(x1 + o, y1 + o, eave), p(x1 + o, ym, z + h), p(x0 - o, ym, z + h))
            roofFill(back, c.mul(0.9), 0.9)
            if (gable != null) {
                val gf = path(p(x0, y0, z), p(x0, y1, z), p(x0, ym, z + h))
                fill(gf, gable, grad = 0.06)
                Face({ u, v -> p(x0, y1 + (y0 - y1) * u, z + h * v * (1 - Math.abs(u - 0.5) * 2)) }, gable).planks(4)
                stroke(gf, gable.mul(0.5), 1.4f)
            }
            val front = path(p(x0 - o, y0 - o, eave), p(x1 + o, y0 - o, eave), p(x1 + o, ym, z + h), p(x0 - o, ym, z + h))
            val fc = c.mul(0.8)
            roofFill(front, fc, 0.8)
            roofTexture({ u, v -> lerp3(p(x0 - o, y0 - o, eave), p(x1 + o, y0 - o, eave), p(x0 - o, ym, z + h), p(x1 + o, ym, z + h), u, v) }, front, fc, style)
            line(p(x0 - o, ym, z + h), p(x1 + o, ym, z + h), if (snow) snowCol(1.15) else c.mul(1.15), 2f)
            eaveSnow(p(x0 - o, y0 - o, eave), p(x1 + o, y0 - o, eave), 0.8)
        } else {
            val xm = (x0 + x1) / 2
            val back = path(p(x1 + o, y0 - o, eave), p(x1 + o, y1 + o, eave), p(xm, y1 + o, z + h), p(xm, y0 - o, z + h))
            roofFill(back, c.mul(0.75), 0.75)
            if (gable != null) {
                val gf = path(p(x0, y0, z), p(x1, y0, z), p(xm, y0, z + h))
                fill(gf, gable.mul(0.74), grad = 0.06)
                Face({ u, v -> p(x0 + (x1 - x0) * u, y0, z + h * v * (1 - Math.abs(u - 0.5) * 2)) }, gable.mul(0.74)).planks(4)
                stroke(gf, gable.mul(0.4), 1.4f)
            }
            val front = path(p(x0 - o, y0 - o, eave), p(x0 - o, y1 + o, eave), p(xm, y1 + o, z + h), p(xm, y0 - o, z + h))
            val fc = c.mul(1.05)
            roofFill(front, fc, 1.05)
            roofTexture({ u, v -> lerp3(p(x0 - o, y1 + o, eave), p(x0 - o, y0 - o, eave), p(xm, y1 + o, z + h), p(xm, y0 - o, z + h), u, v) }, front, fc, style)
            line(p(xm, y0 - o, z + h), p(xm, y1 + o, z + h), if (snow) snowCol(1.15) else c.mul(1.2), 2f)
            eaveSnow(p(x0 - o, y1 + o, eave), p(x0 - o, y0 - o, eave), 1.05)
        }
    }

    /** Билинейная точка в четырёхугольнике: a-b - нижний край (u), c-d - верхний */
    private fun lerp3(a: Point2D.Double, b: Point2D.Double, c: Point2D.Double, d: Point2D.Double, u: Double, v: Double): Point2D.Double {
        val bx = a.x + (b.x - a.x) * u; val by = a.y + (b.y - a.y) * u
        val tx = c.x + (d.x - c.x) * u; val ty = c.y + (d.y - c.y) * u
        return Point2D.Double(bx + (tx - bx) * v, by + (ty - by) * v)
    }

    private fun roofTexture(f: (Double, Double) -> Point2D.Double, shape: java.awt.Shape, c: Color, style: String) {
        val old = g.clip; g.clip(shape)
        val oldComp = g.composite
        if (snow) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)
        when (style) {
            "thatch" -> {
                repeat(260) {
                    val u = rnd.nextDouble(); val v = rnd.nextDouble()
                    line(f(u, v), f(u + (rnd.nextDouble() - 0.5) * 0.02, v + 0.09), c.mul(0.75 + rnd.nextDouble() * 0.5).alpha(170), 1f)
                }
            }
            "plank" -> for (k in 1 until 10) line(f(k / 10.0, 0.0), f(k / 10.0, 1.0), c.mul(0.65).alpha(180), 1f)
            else -> {
                // черепица или сланец: ряды с короткими вертикальными стыками вразбежку
                val rows = if (style == "slate") 8 else 6
                for (r in 0 until rows) {
                    val v = r.toDouble() / rows
                    line(f(0.0, v), f(1.0, v), c.mul(0.6).alpha(200), 1.2f)
                    val n = 12
                    for (k in 0 until n) {
                        val u = (k + if (r % 2 == 0) 0.0 else 0.5) / n
                        line(f(u, v), f(u, v + 1.0 / rows), c.mul(0.7).alpha(140), 0.9f)
                    }
                    // светлая кромка ряда
                    line(f(0.0, v + 0.9 / rows), f(1.0, v + 0.9 / rows), c.mul(1.18).alpha(90), 1f)
                }
            }
        }
        g.composite = oldComp
        g.clip = old
    }

    /** Четырёхскатный шатёр (для башен) над квадратом на высоте z */
    fun spire(x0: Double, y0: Double, x1: Double, y1: Double, z: Double, h: Double, c: Color) {
        val o = 0.08
        val top = p((x0 + x1) / 2, (y0 + y1) / 2, z + h)
        val l = path(p(x0 - o, y1 + o, z), p(x0 - o, y0 - o, z), top)
        val r = path(p(x0 - o, y0 - o, z), p(x1 + o, y0 - o, z), top)
        roofFill(l, c.mul(1.05), 1.05); roofFill(r, c.mul(0.75), 0.75)
        roofTexture({ u, v -> lerp3(p(x0 - o, y1 + o, z), p(x0 - o, y0 - o, z), top, top, u, v) }, l, c.mul(1.05), "slate")
        roofTexture({ u, v -> lerp3(p(x0 - o, y0 - o, z), p(x1 + o, y0 - o, z), top, top, u, v) }, r, c.mul(0.75), "slate")
        eaveSnow(p(x0 - o, y1 + o, z), p(x0 - o, y0 - o, z), 1.05)
        eaveSnow(p(x0 - o, y0 - o, z), p(x1 + o, y0 - o, z), 0.75)
    }

    /** Цилиндр (бревно, бочка) стоймя: эллипс сверху, бока с градиентом */
    fun cylinder(x: Double, y: Double, r: Double, z0: Double, z1: Double, c: Color) {
        val b = p(x, y, z0); val t = p(x, y, z1)
        val rw = r * TW / 2; val rh = rw / 2
        val side = java.awt.geom.Area(java.awt.geom.Rectangle2D.Double(b.x - rw, t.y, rw * 2, b.y - t.y))
        side.add(java.awt.geom.Area(Ellipse2D.Double(b.x - rw, b.y - rh, rw * 2, rh * 2)))
        g.paint = GradientPaint((b.x - rw).toFloat(), 0f, c.mul(1.15), (b.x + rw).toFloat(), 0f, c.mul(0.65))
        g.fill(side)
        stroke(side, c.mul(0.45), 1.2f)
        val top = Ellipse2D.Double(t.x - rw, t.y - rh, rw * 2, rh * 2)
        fill(top, c.mul(1.25), grad = 0.05)
    }

    /** Бревно лёжа вдоль x: торец светлый с кольцами */
    fun log(x0: Double, x1: Double, y: Double, z: Double, r: Double, c: Color) {
        val rw = r * TW / 2
        val a = p(x0, y, z + rw); val b = p(x1, y, z + rw)
        val body = path(
            Point2D.Double(a.x, a.y - rw), Point2D.Double(b.x, b.y - rw), Point2D.Double(b.x, b.y + rw), Point2D.Double(a.x, a.y + rw),
        )
        fill(body, c)
        repeat(3) { k -> line(Point2D.Double(a.x + 3, a.y - rw * 0.5 + k * rw * 0.5), Point2D.Double(b.x - 3, b.y - rw * 0.5 + k * rw * 0.5), c.mul(0.7).alpha(150), 1f) }
        val end = Ellipse2D.Double(a.x - rw * 0.55, a.y - rw, rw * 1.1, rw * 2)
        fill(end, rgb(0xd8b878), grad = 0.05)
        stroke(Ellipse2D.Double(a.x - rw * 0.3, a.y - rw * 0.5, rw * 0.6, rw), rgb(0xa88050), 0.8f)
    }

    /** Обрезка до непрозрачной области; точка привязки (ox, oy) переносится */
    fun toSprite(name: String): Sprite {
        g.dispose()
        var minX = img.width; var minY = img.height; var maxX = -1; var maxY = -1
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if ((img.getRGB(x, y) ushr 24) > 4) {
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            }
        }
        if (maxX < 0) return Sprite(name, BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0, 0)
        // привязка должна остаться внутри или на краю картинки
        val ax = px(ox); val ay = px(oy)
        minX = min(minX, ax); maxX = max(maxX, ax); maxY = max(maxY, ay - 1); minY = min(minY, ay - 1)
        val out = img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
        val copy = BufferedImage(out.width, out.height, BufferedImage.TYPE_INT_ARGB)
        copy.createGraphics().apply { drawImage(out, 0, 0, null); dispose() }
        return Sprite(name, copy, ax - minX, (maxY + 1) - ay)
    }
}
