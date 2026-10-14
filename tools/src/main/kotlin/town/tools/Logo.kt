package town.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.sin

/**
 * Вывеска-логотип заглавного экрана: дубовая доска на двух верёвках, слово «Hearthvale» вырезано и покрыто
 * золотом (шрифт Alegreya SC, OFL - tools/fonts, в игру не входит: в атлас идёт готовая картинка).
 */
object Logo {
    private const val W = 960
    private const val H = 300
    /** Доска: отступ сверху под верёвки, высота */
    private const val TOP = 62
    private const val BOARD_H = 222

    fun sprite(): Sprite? {
        val f = File("tools/fonts/AlegreyaSC-Bold.ttf")
        if (!f.exists()) { println("WARNING: tools/fonts/AlegreyaSC-Bold.ttf not found - no logo"); return null }
        val font = Font.createFont(Font.TRUETYPE_FONT, f)
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        // верёвки к крюкам над доской
        for (x in listOf(190.0, W - 190.0)) {
            g.stroke = BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.paint = rgb(0x2a1a0e)
            g.draw(Line2D.Double(x, 6.0, x, TOP + 14.0))
            g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.paint = rgb(0x8a7450)
            g.draw(Line2D.Double(x, 6.0, x, TOP + 14.0))
            // витки
            g.stroke = BasicStroke(1.6f)
            g.paint = Color(0x3a, 0x2a, 0x16, 160)
            var y = 10.0
            while (y < TOP + 10) { g.draw(Line2D.Double(x - 2.5, y, x + 2.5, y + 4)); y += 7.0 }
        }

        // доска: тёплое дерево, светлее кверху (свет сверху слева), со швом и волокнами
        val board = RoundRectangle2D.Double(10.0, TOP.toDouble(), W - 20.0, BOARD_H.toDouble(), 46.0, 46.0)
        g.paint = rgb(0x24160a)
        g.fill(RoundRectangle2D.Double(14.0, TOP + 8.0, W - 20.0, BOARD_H.toDouble(), 46.0, 46.0))   // тень
        g.paint = GradientPaint(0f, TOP.toFloat(), rgb(0x9a6a3c), 0f, (TOP + BOARD_H).toFloat(), rgb(0x5c3a1e))
        g.fill(board)
        val old = g.clip
        g.clip(board)
        val rnd = java.util.Random(7)
        for (k in 0 until 46) {
            val y0 = TOP + 6 + rnd.nextDouble() * (BOARD_H - 12)
            val amp = 1.5 + rnd.nextDouble() * 3.5
            val ph = rnd.nextDouble() * 6.28
            val dark = rnd.nextBoolean()
            g.paint = if (dark) Color(0x3a, 0x22, 0x10, 40 + rnd.nextInt(40)) else Color(0xc8, 0x94, 0x5c, 25 + rnd.nextInt(30))
            g.stroke = BasicStroke((0.8 + rnd.nextDouble() * 1.6).toFloat())
            val path = java.awt.geom.Path2D.Double()
            path.moveTo(0.0, y0)
            var x = 0.0
            while (x <= W) { x += 12.0; path.lineTo(x, y0 + amp * sin(x / (60 + k * 3) + ph)) }
            g.draw(path)
        }
        // сучки
        for (k in 0 until 3) {
            val cx = 120.0 + rnd.nextDouble() * (W - 240); val cy = TOP + 20.0 + rnd.nextDouble() * (BOARD_H - 40)
            g.paint = Color(0x3a, 0x22, 0x10, 90)
            g.fill(Ellipse2D.Double(cx - 9, cy - 4, 18.0, 8.0))
        }
        // шов между двумя досками
        val seam = TOP + BOARD_H / 2.0 + 3
        g.paint = Color(0x2a, 0x18, 0x0a, 150); g.stroke = BasicStroke(3f)
        g.draw(Line2D.Double(0.0, seam, W.toDouble(), seam))
        g.paint = Color(0xd0, 0xa0, 0x68, 70); g.stroke = BasicStroke(1.5f)
        g.draw(Line2D.Double(0.0, seam + 2.5, W.toDouble(), seam + 2.5))
        g.clip = old
        // кромка: тёмная обводка и светлая фаска внутри
        g.paint = rgb(0x2a1a0e); g.stroke = BasicStroke(7f)
        g.draw(board)
        g.paint = Color(0xd8, 0xa8, 0x70, 110); g.stroke = BasicStroke(2.5f)
        g.draw(RoundRectangle2D.Double(19.0, TOP + 9.0, W - 38.0, BOARD_H - 18.0, 36.0, 36.0))
        // гвозди по углам
        for ((nx, ny) in listOf(44.0 to TOP + 32.0, W - 44.0 to TOP + 32.0, 44.0 to TOP + BOARD_H - 32.0, W - 44.0 to TOP + BOARD_H - 32.0)) {
            g.paint = rgb(0x22160c); g.fill(Ellipse2D.Double(nx - 9, ny - 9, 18.0, 18.0))
            g.paint = rgb(0x7a7068); g.fill(Ellipse2D.Double(nx - 6.5, ny - 7.5, 13.0, 13.0))
            g.paint = Color(255, 255, 255, 120); g.fill(Ellipse2D.Double(nx - 4.5, ny - 6.0, 5.0, 4.0))
        }

        // надпись: вырезана (тёмный паз со смещением вниз-вправо и светлая кромка снизу) и покрыта золотом
        val text = "Hearthvale"
        var size = 150f
        var fnt = font.deriveFont(size)
        val frc = g.fontRenderContext
        var bounds = fnt.createGlyphVector(frc, text).visualBounds
        while (bounds.width > W - 170) { size -= 4f; fnt = font.deriveFont(size); bounds = fnt.createGlyphVector(frc, text).visualBounds }
        val gv = fnt.createGlyphVector(frc, text)
        val tx = (W - bounds.width) / 2 - bounds.x
        val ty = TOP + BOARD_H / 2.0 - bounds.y - bounds.height / 2 - 4
        val shape = AffineTransform.getTranslateInstance(tx, ty).createTransformedShape(gv.outline)
        fun shifted(dx: Double, dy: Double) = AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(shape)
        g.paint = Color(0xe0, 0xb0, 0x78, 130); g.fill(shifted(-1.5, 3.5))     // светлая нижняя кромка паза
        g.paint = Color(0x1a, 0x0e, 0x06, 230); g.fill(shifted(3.0, 4.0))      // паз
        val top = (ty + bounds.y).toFloat(); val bottom = (ty + bounds.y + bounds.height).toFloat()
        g.paint = GradientPaint(0f, top, rgb(0xfff0b0), 0f, bottom, rgb(0xc88a2a))
        g.fill(shape)
        g.paint = rgb(0x3a2410); g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        // блик по верху букв
        val clip = g.clip
        g.clip(shape)
        g.paint = Color(255, 255, 240, 90)
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, top.toDouble(), W.toDouble(), (bottom - top) * 0.28))
        g.clip = clip
        g.dispose()
        return Sprite("ui_logo", img, W / 2, 0)
    }
}
