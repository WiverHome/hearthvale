package town.tools

import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Иконка игры: домик с красной крышей и тёплым светом в окне на зелёном холме под вечерним небом.
 * Android: адаптивная иконка (фон + передний план 108dp, рисунок в безопасной зоне 66dp) и запасные PNG;
 * веб: значок вкладки и логотип загрузки с названием.
 */
object Icon {
    /** Картинка иконки из ИИ-генератора (tools/ai/gen.py set icon); нет - рисуется кодом */
    private val art: BufferedImage? = File("tools/art/override/icon.png").takeIf { it.exists() }?.let { ImageIO.read(it) }

    private fun drawArt(g: java.awt.Graphics2D, a: BufferedImage, x: Double, y: Double, size: Double) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(a, x.toInt(), y.toInt(), size.toInt(), size.toInt(), null)
    }

    private val densities = listOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0)

    fun all(res: File, web: File) {
        for ((d, k) in densities) {
            val dir = File(res, "mipmap-$d"); dir.mkdirs()
            ImageIO.write(full((48 * k).toInt(), round = false), "png", File(dir, "ic_launcher.png"))
            ImageIO.write(full((48 * k).toInt(), round = true), "png", File(dir, "ic_launcher_round.png"))
            ImageIO.write(foreground((108 * k).toInt()), "png", File(dir, "ic_launcher_foreground.png"))
        }
        val any = File(res, "mipmap-anydpi-v26"); any.mkdirs()
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
        File(any, "ic_launcher.xml").writeText(xml)
        File(any, "ic_launcher_round.xml").writeText(xml)
        val values = File(res, "values"); values.mkdirs()
        File(values, "ic_launcher_background.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <color name=\"ic_launcher_background\">#2E4A3A</color>\n</resources>\n",
        )
        web.mkdirs()
        ImageIO.write(full(192, round = false), "png", File(web, "icon-192.png"))
        // установка веб-версии как приложения: Android (512) и экран «Домой» на iPhone (180)
        ImageIO.write(full(512, round = false), "png", File(web, "icon-512.png"))
        ImageIO.write(full(180, round = false), "png", File(web, "icon-180.png"))
        ImageIO.write(full(32, round = false), "png", File(web, "favicon.png"))
        ImageIO.write(logo(), "png", File(web, "logo.png"))
        // заставка приложения на iPhone (иначе до загрузки - белый экран): тёмный фон и логотип, по размеру экрана
        val splash = File(web, "splash"); splash.mkdirs()
        val lg = logo()
        for ((w, h) in SPLASH) {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.paint = rgb(0x1f2a1b); g.fillRect(0, 0, w, h)
            val lw = w / 2; val lh = lw * lg.height / lg.width
            g.drawImage(lg, (w - lw) / 2, (h - lh) / 2, lw, lh, null)
            g.dispose()
            ImageIO.write(img, "jpg", File(splash, "splash_${w}x$h.jpg"))
        }
        println("icons written")
    }

    private fun canvas(n: Int): Pair<BufferedImage, java.awt.Graphics2D> {
        val img = BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        return img to g
    }

    /** Полная иконка: небо, холм, домик; скруглённый квадрат или круг */
    private fun full(n: Int, round: Boolean): BufferedImage {
        val (img, g) = canvas(n)
        val s = n.toDouble()
        val shape = if (round) Ellipse2D.Double(0.0, 0.0, s, s) else RoundRectangle2D.Double(0.0, 0.0, s, s, s * 0.36, s * 0.36)
        g.clip(shape)
        if (art != null) drawArt(g, art, 0.0, 0.0, s)
        else { sky(g, s); scene(g, s * 0.1, s * 0.12, s * 0.8) }
        g.dispose()
        return img
    }

    /** Передний план адаптивной иконки: небо и сцена, рисунок в центральных 66 из 108 */
    private fun foreground(n: Int): BufferedImage {
        val (img, g) = canvas(n)
        val s = n.toDouble()
        val safe = s * 66 / 108
        if (art != null) {
            // видимая часть адаптивной иконки - около 72 из 108: картинка целиком в ней, вокруг - она же крупнее
            drawArt(g, art, -s * 0.1, -s * 0.1, s * 1.2)
            val vis = s * 72 / 108
            drawArt(g, art, (s - vis) / 2, (s - vis) / 2, vis)
        } else {
            sky(g, s)
            scene(g, (s - safe) / 2, (s - safe) / 2 + safe * 0.02, safe)
        }
        g.dispose()
        return img
    }

    private fun sky(g: java.awt.Graphics2D, s: Double) {
        g.paint = GradientPaint(0f, 0f, rgb(0x3a5878), 0f, s.toFloat(), rgb(0xe8a860))
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, 0.0, s, s))
        // звёздочки и мягкое солнце у горизонта
        g.paint = RadialGradientPaint(Point2D.Double(s * 0.72, s * 0.62), (s * 0.35).toFloat(), floatArrayOf(0f, 1f), arrayOf(Color(255, 220, 150, 200), Color(255, 220, 150, 0)))
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, 0.0, s, s))
        g.paint = Color(255, 255, 230, 200)
        for ((x, y) in listOf(0.18 to 0.14, 0.32 to 0.08, 0.84 to 0.18, 0.62 to 0.1)) g.fill(Ellipse2D.Double(s * x, s * y, s * 0.012, s * 0.012))
    }

    /** Холм с домиком в квадрате (x, y, size) */
    private fun scene(g: java.awt.Graphics2D, x: Double, y: Double, size: Double) {
        fun px(u: Double) = x + u * size
        fun py(v: Double) = y + v * size
        // холмы
        g.paint = GradientPaint(0f, py(0.6).toFloat(), rgb(0x5a9a48), 0f, py(1.0).toFloat(), rgb(0x2e5a2a))
        g.fill(Ellipse2D.Double(px(-0.4), py(0.62), size * 1.8, size * 0.9))
        g.paint = rgb(0x4a8a3a)
        g.fill(Ellipse2D.Double(px(-0.5), py(0.74), size * 1.0, size * 0.6))
        // ёлка
        g.paint = rgb(0x2f5a32)
        g.fill(java.awt.Polygon(intArrayOf(px(0.14).toInt(), px(0.02).toInt(), px(0.26).toInt()), intArrayOf(py(0.38).toInt(), py(0.72).toInt(), py(0.72).toInt()), 3))
        // стены
        val wall = java.awt.geom.Rectangle2D.Double(px(0.3), py(0.46), size * 0.42, size * 0.3)
        g.paint = GradientPaint(px(0.3).toFloat(), 0f, rgb(0xf0e4c8), px(0.72).toFloat(), 0f, rgb(0xc8b898))
        g.fill(wall)
        g.paint = rgb(0x5a3a22)
        g.stroke = java.awt.BasicStroke((size * 0.02).toFloat())
        g.draw(wall)
        g.drawLine(px(0.3).toInt(), py(0.6).toInt(), px(0.72).toInt(), py(0.6).toInt())
        // крыша
        val roof = java.awt.Polygon(
            intArrayOf(px(0.24).toInt(), px(0.51).toInt(), px(0.78).toInt()),
            intArrayOf(py(0.48).toInt(), py(0.24).toInt(), py(0.48).toInt()), 3,
        )
        g.paint = GradientPaint(px(0.24).toFloat(), py(0.24).toFloat(), rgb(0xd86448), px(0.78).toFloat(), py(0.48).toFloat(), rgb(0x8a3424))
        g.fill(roof)
        g.paint = rgb(0x5a2418); g.draw(roof)
        // труба и дымок
        g.paint = rgb(0x8a7060)
        g.fill(java.awt.geom.Rectangle2D.Double(px(0.62), py(0.27), size * 0.06, size * 0.12))
        g.paint = Color(240, 240, 240, 150)
        for (k in 0 until 3) g.fill(Ellipse2D.Double(px(0.63 + k * 0.04), py(0.18 - k * 0.07), size * (0.06 + k * 0.02), size * (0.05 + k * 0.015)))
        // окно с тёплым светом и дверь
        g.paint = RadialGradientPaint(Point2D.Double(px(0.4), py(0.53)), (size * 0.12).toFloat(), floatArrayOf(0f, 1f), arrayOf(Color(255, 230, 140), Color(255, 160, 60)))
        g.fill(java.awt.geom.Rectangle2D.Double(px(0.35), py(0.5), size * 0.1, size * 0.08))
        g.paint = rgb(0x6a4228)
        g.fill(java.awt.geom.Rectangle2D.Double(px(0.55), py(0.62), size * 0.1, size * 0.14))
    }

    /** Экраны iPhone для заставки (пиксели, портрет); то же в web/build.gradle.kts - ссылки с media-условиями */
    val SPLASH = listOf(750 to 1334, 828 to 1792, 1080 to 2340, 1125 to 2436, 1170 to 2532, 1179 to 2556,
        1206 to 2622, 1242 to 2688, 1284 to 2778, 1290 to 2796, 1320 to 2868)

    /** Логотип загрузки веб-версии: иконка и название шрифтом игры */
    private fun logo(): BufferedImage {
        val w = 360; val h = 300
        val (img, g) = canvas(w)
        val icon = full(180, round = false)
        g.drawImage(icon, (w - 180) / 2, 10, null)
        val font = try {
            Font.createFont(Font.TRUETYPE_FONT, File("assets/fonts/PT_Sans-Web-Bold.ttf")).deriveFont(52f)
        } catch (e: Exception) {
            Font(Font.SERIF, Font.BOLD, 52)
        }
        g.font = font
        val fm = g.fontMetrics
        val text = "Hearthvale"
        g.paint = rgb(0xffd870)
        g.drawString(text, (w - fm.stringWidth(text)) / 2, 260)
        g.dispose()
        return img.getSubimage(0, 0, w, h)
    }
}
