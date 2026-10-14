package town.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Значки интерфейса (верхняя панель, ресурсы, карточки построек) в том же рисованном стиле: 40x40 в атласе
 * (20 точек в игре), мягкий объём и тёмная обводка, чтобы читались и на светлой карте, и на тёмной панели.
 */
object Hud {
    private const val N = 40

    fun all(resourceIds: List<String>): List<Sprite> {
        val out = ArrayList<Sprite>()
        out += icon("hud_gold") { coin(it, 20.0, 20.0, 14.0) }
        out += icon("hud_people") { g ->
            person(g, 14.0, 22.0, rgb(0x6a8ac8)); person(g, 26.0, 22.0, rgb(0xd8822a))
        }
        out += icon("hud_happy") { g ->
            ball(g, 20.0, 20.0, 15.0, rgb(0xf0c040))
            g.paint = rgb(0x4a3010)
            g.fill(Ellipse2D.Double(14.0, 14.0, 3.5, 4.5)); g.fill(Ellipse2D.Double(22.5, 14.0, 3.5, 4.5))
            g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Double(12.0, 13.0, 16.0, 14.0, 200.0, 140.0, Arc2D.OPEN))
        }
        out += icon("hud_season_0") { g -> flower(g) }
        out += icon("hud_season_1") { g -> sun(g) }
        out += icon("hud_season_2") { g -> leaf(g) }
        out += icon("hud_season_3") { g -> snow(g) }
        out += icon("hud_night") { g ->
            val moon = java.awt.geom.Area(Ellipse2D.Double(7.0, 6.0, 26.0, 26.0))
            moon.subtract(java.awt.geom.Area(Ellipse2D.Double(15.0, 2.0, 24.0, 24.0)))
            fillOutlined(g, moon, rgb(0xf0e0a0))
        }
        out += icon("hud_menu") { g ->
            for (k in 0 until 3) fillOutlined(g, RoundRectangle2D.Double(8.0, 10.0 + k * 8, 24.0, 4.5, 3.0, 3.0), rgb(0xf4ead0))
        }
        out += icon("hud_pause") { g ->
            fillOutlined(g, RoundRectangle2D.Double(11.0, 9.0, 6.5, 22.0, 3.0, 3.0), rgb(0xf4ead0))
            fillOutlined(g, RoundRectangle2D.Double(22.5, 9.0, 6.5, 22.0, 3.0, 3.0), rgb(0xf4ead0))
        }
        out += icon("hud_play") { g ->
            val p = Path2D.Double(); p.moveTo(13.0, 9.0); p.lineTo(31.0, 20.0); p.lineTo(13.0, 31.0); p.closePath()
            fillOutlined(g, p, rgb(0xf4ead0))
        }
        out += icon("hud_trader") { g -> cart(g) }
        // поворот карты: дуга со стрелкой по часовой
        out += icon("hud_rotate") { g ->
            val arc = java.awt.geom.Arc2D.Double(9.0, 9.0, 22.0, 22.0, 110.0, 280.0, java.awt.geom.Arc2D.OPEN)
            g.stroke = java.awt.BasicStroke(7f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND)
            g.paint = java.awt.Color(40, 28, 16); g.draw(arc)
            g.stroke = java.awt.BasicStroke(4f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND)
            g.paint = rgb(0xf4ead0); g.draw(arc)
            val p = Path2D.Double(); p.moveTo(10.0, 6.0); p.lineTo(20.5, 9.5); p.lineTo(12.0, 17.0); p.closePath()
            fillOutlined(g, p, rgb(0xf4ead0))
        }
        out += icon("hud_quest") { g -> scroll(g) }
        out += icon("hud_lock") { g ->
            g.stroke = BasicStroke(3.5f); g.paint = rgb(0x3a3020)
            g.draw(Arc2D.Double(12.0, 6.0, 16.0, 18.0, 0.0, 180.0, Arc2D.OPEN))
            g.stroke = BasicStroke(2f); g.paint = rgb(0xc8c8d0)
            g.draw(Arc2D.Double(12.0, 6.0, 16.0, 18.0, 0.0, 180.0, Arc2D.OPEN))
            fillOutlined(g, RoundRectangle2D.Double(9.0, 16.0, 22.0, 17.0, 4.0, 4.0), rgb(0xd8a840))
            g.paint = rgb(0x4a3010); g.fill(Ellipse2D.Double(18.0, 21.0, 4.0, 5.0))
        }
        out += icon("hud_road") { g ->
            val p = Path2D.Double(); p.moveTo(4.0, 34.0); p.lineTo(16.0, 6.0); p.lineTo(24.0, 6.0); p.lineTo(36.0, 34.0); p.closePath()
            fillOutlined(g, p, rgb(0xb89c6c))
            g.paint = rgb(0xf4ead0)
            for (k in 0 until 3) g.fill(Rectangle2D.Double(19.0, 10.0 + k * 8.5, 2.5, 5.0))
        }
        out += icon("hud_bell") { g ->
            val p = Path2D.Double()
            p.moveTo(8.0, 30.0); p.curveTo(10.0, 26.0, 11.0, 22.0, 11.0, 17.0); p.curveTo(11.0, 10.0, 15.0, 7.0, 20.0, 7.0)
            p.curveTo(25.0, 7.0, 29.0, 10.0, 29.0, 17.0); p.curveTo(29.0, 22.0, 30.0, 26.0, 32.0, 30.0); p.closePath()
            fillOutlined(g, p, rgb(0xe0b040))
            ball(g, 20.0, 5.5, 2.8, rgb(0xc89a30))
            ball(g, 20.0, 33.0, 3.6, rgb(0x8a6020))
            g.paint = Color(255, 255, 255, 150); g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Double(14.0, 11.0, 10.0, 14.0, 110.0, 70.0, Arc2D.OPEN))
        }
        out += icon("hud_stats") { g ->
            val cols = intArrayOf(0x6a9ad8, 0x6ab048, 0xe0a030)
            val hs = doubleArrayOf(12.0, 20.0, 28.0)
            for (k in 0 until 3) fillOutlined(g, RoundRectangle2D.Double(6.0 + k * 10.0, 34.0 - hs[k], 8.0, hs[k], 2.5, 2.5), rgb(cols[k]))
            g.paint = Color(30, 22, 14, 220); g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(java.awt.geom.Line2D.Double(4.0, 35.5, 36.0, 35.5))
        }
        out += icon("hud_up") { g ->
            val p = Path2D.Double(); p.moveTo(20.0, 6.0); p.lineTo(34.0, 30.0); p.lineTo(6.0, 30.0); p.closePath()
            fillOutlined(g, p, rgb(0x4cb04a))
        }
        out += icon("hud_down") { g ->
            val p = Path2D.Double(); p.moveTo(6.0, 10.0); p.lineTo(34.0, 10.0); p.lineTo(20.0, 34.0); p.closePath()
            fillOutlined(g, p, rgb(0xd04a3a))
        }
        out += icon("hud_star") { g ->
            val p = Path2D.Double()
            for (k in 0 until 10) {
                val a = -PI / 2 + k * PI / 5
                val r = if (k % 2 == 0) 16.0 else 7.0
                val x = 20 + cos(a) * r; val y = 21 + sin(a) * r
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.closePath()
            fillOutlined(g, p, rgb(0xf0c040))
            g.paint = Color(255, 250, 200, 190); g.fill(Ellipse2D.Double(17.0, 12.0, 4.0, 5.0))
        }
        out += icon("hud_fire") { g ->
            val p = Path2D.Double()
            p.moveTo(20.0, 3.0); p.curveTo(22.0, 11.0, 32.0, 14.0, 31.0, 25.0); p.curveTo(30.0, 33.0, 25.0, 36.0, 20.0, 36.0)
            p.curveTo(15.0, 36.0, 9.0, 33.0, 9.0, 25.0); p.curveTo(9.0, 19.0, 13.0, 17.0, 14.0, 11.0); p.curveTo(17.0, 13.0, 17.0, 16.0, 17.5, 17.0)
            p.curveTo(19.0, 13.0, 17.0, 8.0, 20.0, 3.0); p.closePath()
            fillOutlined(g, p, rgb(0xe8541c))
            val q = Path2D.Double()
            q.moveTo(20.0, 18.0); q.curveTo(22.0, 23.0, 27.0, 25.0, 26.0, 30.0); q.curveTo(25.0, 34.0, 22.0, 34.5, 20.0, 34.5)
            q.curveTo(17.0, 34.5, 14.0, 33.0, 14.0, 29.0); q.curveTo(14.0, 25.0, 19.0, 23.0, 20.0, 18.0); q.closePath()
            g.paint = rgb(0xffd848); g.fill(q)
        }
        out += icon("hud_sick") { g ->
            ball(g, 20.0, 20.0, 15.0, rgb(0xf4f4f0))
            g.paint = rgb(0x3a9a4a)
            g.fill(RoundRectangle2D.Double(17.0, 9.0, 6.0, 22.0, 2.0, 2.0))
            g.fill(RoundRectangle2D.Double(9.0, 17.0, 22.0, 6.0, 2.0, 2.0))
        }
        out += icon("hud_tax") { g ->
            val p = Path2D.Double(); p.moveTo(13.0, 12.0); p.curveTo(6.0, 20.0, 6.0, 34.0, 20.0, 35.0); p.curveTo(34.0, 34.0, 34.0, 20.0, 27.0, 12.0); p.closePath()
            fillOutlined(g, p, rgb(0xb88a52))
            fillOutlined(g, RoundRectangle2D.Double(12.0, 8.0, 16.0, 6.0, 4.0, 4.0), rgb(0x8a5a2b))
            coin(g, 20.0, 25.0, 6.0)
            val a = Path2D.Double(); a.moveTo(33.0, 2.0); a.lineTo(38.0, 9.0); a.lineTo(34.5, 9.0); a.lineTo(34.5, 14.0); a.lineTo(31.5, 14.0); a.lineTo(31.5, 9.0); a.lineTo(28.0, 9.0); a.closePath()
            fillOutlined(g, a, rgb(0x4cb04a))
        }
        out += icon("hud_layers") { g ->
            for ((k, c) in listOf(0x7a9ac0, 0x6ab048, 0xe0a030).withIndex()) {
                val y = 26.0 - k * 9.0
                val p = Path2D.Double(); p.moveTo(20.0, y - 8.0); p.lineTo(35.0, y); p.lineTo(20.0, y + 8.0); p.lineTo(5.0, y); p.closePath()
                fillOutlined(g, p, rgb(c))
            }
        }
        out += icon("hud_crown") { g ->
            val p = Path2D.Double(); p.moveTo(6.0, 30.0); p.lineTo(5.0, 12.0); p.lineTo(14.0, 20.0); p.lineTo(20.0, 8.0); p.lineTo(26.0, 20.0); p.lineTo(35.0, 12.0); p.lineTo(34.0, 30.0); p.closePath()
            fillOutlined(g, p, rgb(0xf0c040))
            fillOutlined(g, RoundRectangle2D.Double(6.0, 29.0, 28.0, 6.0, 3.0, 3.0), rgb(0xc89a30))
            ball(g, 5.0, 11.0, 3.0, rgb(0xd04a3a)); ball(g, 20.0, 7.0, 3.2, rgb(0x3a7bd5)); ball(g, 35.0, 11.0, 3.0, rgb(0xd04a3a))
        }
        out += icon("hud_prestige") { g ->
            g.paint = rgb(0x4a8a3a); g.stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Double(5.0, 5.0, 30.0, 30.0, 215.0, 250.0, Arc2D.OPEN))
            for (k in 0 until 5) {
                val t = Math.toRadians(215.0 + k * 62.0)
                ball(g, 20 + cos(t) * 15, 20 - sin(t) * 15, 3.4, rgb(0x5aa048))
            }
            val p = Path2D.Double()
            for (k in 0 until 10) {
                val a = -PI / 2 + k * PI / 5
                val r = if (k % 2 == 0) 9.0 else 4.0
                val x = 20 + cos(a) * r; val y = 20 + sin(a) * r
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.closePath()
            fillOutlined(g, p, rgb(0xf0c040))
        }
        out += icon("hud_copy") { g ->
            fillOutlined(g, RoundRectangle2D.Double(6.0, 5.0, 19.0, 24.0, 4.0, 4.0), rgb(0xa8b8d0))
            fillOutlined(g, RoundRectangle2D.Double(15.0, 12.0, 19.0, 24.0, 4.0, 4.0), rgb(0xf4f0e0))
            g.paint = rgb(0x8a8a7a); g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (k in 0 until 3) ln(g, 19.0, 19.0 + k * 5.0, 30.0, 19.0 + k * 5.0)
        }
        out += icon("hud_prio") { g ->
            ball(g, 20.0, 20.0, 16.0, rgb(0xd04a3a))
            g.paint = rgb(0xfff4e0)
            g.fill(RoundRectangle2D.Double(17.5, 9.0, 5.0, 14.0, 3.0, 3.0))
            g.fill(Ellipse2D.Double(17.5, 26.0, 5.0, 5.0))
        }
        out += icon("hud_chain") { g ->
            g.paint = Color(30, 22, 14, 230); g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for ((x0, x1) in listOf(13.0 to 17.0, 23.0 to 27.0)) {
                ln(g, x0, 20.0, x1 - 0.5, 20.0); ln(g, x1 - 3.5, 17.0, x1, 20.0); ln(g, x1 - 3.5, 23.0, x1, 20.0)
            }
            ball(g, 7.0, 20.0, 5.0, rgb(0x6a9ad8)); ball(g, 20.0, 20.0, 5.0, rgb(0x6ab048)); ball(g, 33.0, 20.0, 5.0, rgb(0xe0a030))
        }
        out += icon("hud_daily") { g ->
            fillOutlined(g, RoundRectangle2D.Double(6.0, 8.0, 28.0, 28.0, 5.0, 5.0), rgb(0xf4f0e0))
            fillOutlined(g, RoundRectangle2D.Double(6.0, 8.0, 28.0, 9.0, 5.0, 5.0), rgb(0xd04a3a))
            g.paint = Color(30, 22, 14, 230); g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            ln(g, 13.0, 5.0, 13.0, 11.0); ln(g, 27.0, 5.0, 27.0, 11.0)
            ln(g, 18.0, 23.0, 21.0, 21.0); ln(g, 21.0, 21.0, 21.0, 32.0); ln(g, 18.0, 32.0, 24.0, 32.0)
        }
        out += icon("hud_ship") { g ->
            val hull = Path2D.Double(); hull.moveTo(4.0, 26.0); hull.lineTo(36.0, 26.0); hull.lineTo(30.0, 35.0); hull.lineTo(10.0, 35.0); hull.closePath()
            fillOutlined(g, hull, rgb(0x8a5a32))
            g.paint = rgb(0x4a3010); g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); ln(g, 20.0, 4.0, 20.0, 26.0)
            val s1 = Path2D.Double(); s1.moveTo(22.0, 6.0); s1.curveTo(32.0, 10.0, 34.0, 18.0, 33.0, 24.0); s1.lineTo(22.0, 24.0); s1.closePath()
            fillOutlined(g, s1, rgb(0xf4f0e0))
            val s2 = Path2D.Double(); s2.moveTo(18.0, 10.0); s2.curveTo(11.0, 14.0, 9.0, 19.0, 9.0, 24.0); s2.lineTo(18.0, 24.0); s2.closePath()
            fillOutlined(g, s2, rgb(0xe0d4b0))
            g.paint = Color(70, 130, 200, 200); g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Double(2.0, 33.0, 8.0, 5.0, 0.0, 180.0, Arc2D.OPEN)); g.draw(Arc2D.Double(30.0, 33.0, 8.0, 5.0, 0.0, 180.0, Arc2D.OPEN))
        }
        out += icon("hud_paved") { g ->
            val base = Path2D.Double(); base.moveTo(20.0, 6.0); base.lineTo(36.0, 20.0); base.lineTo(20.0, 34.0); base.lineTo(4.0, 20.0); base.closePath()
            fillOutlined(g, base, rgb(0xa8a090))
            g.paint = Color(50, 44, 36, 220); g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            ln(g, 12.0, 13.0, 28.0, 27.0); ln(g, 28.0, 13.0, 12.0, 27.0)
            ln(g, 20.0, 6.0, 20.0, 34.0); ln(g, 4.0, 20.0, 36.0, 20.0)
        }
        out += icon("hud_bridge") { g ->
            g.paint = Color(70, 130, 200, 200); g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Double(2.0, 33.0, 10.0, 5.0, 0.0, 180.0, Arc2D.OPEN)); g.draw(Arc2D.Double(28.0, 33.0, 10.0, 5.0, 0.0, 180.0, Arc2D.OPEN))
            val deck = Path2D.Double(); deck.moveTo(3.0, 18.0); deck.curveTo(10.0, 10.0, 30.0, 10.0, 37.0, 18.0); deck.lineTo(37.0, 24.0); deck.curveTo(30.0, 16.0, 10.0, 16.0, 3.0, 24.0); deck.closePath()
            fillOutlined(g, deck, rgb(0xb8a07a))
            val arch = Path2D.Double(); arch.moveTo(8.0, 22.0); arch.curveTo(12.0, 14.0, 28.0, 14.0, 32.0, 22.0); arch.lineTo(32.0, 33.0); arch.lineTo(26.0, 33.0)
            arch.curveTo(26.0, 25.0, 14.0, 25.0, 14.0, 33.0); arch.lineTo(8.0, 33.0); arch.closePath()
            fillOutlined(g, arch, rgb(0x9a9488))
        }
        // погода: ясно, дождь, снегопад, туман, засуха (W_* в Weather.kt)
        out += icon("hud_weather_0") { g -> sun(g) }
        out += icon("hud_weather_1") { g ->
            g.paint = rgb(0x3a7bd5); g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (k in 0 until 3) ln(g, 12.0 + k * 7, 26.0, 9.0 + k * 7, 35.0)
            cloud(g, 0xe4e8f0)
        }
        out += icon("hud_weather_2") { g ->
            cloud(g, 0xf0f4fa)
            for ((x, y) in listOf(11.0 to 30.0, 20.0 to 34.0, 29.0 to 30.0)) ball(g, x, y, 2.6, rgb(0xffffff))
        }
        // туман: облако, под ним волнистые полупрозрачные полосы (не путать с кнопкой меню - три ровные полоски)
        out += icon("hud_weather_3") { g ->
            cloud(g, 0xd0d4dc)
            g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (k in 0 until 2) {
                val y = 29.0 + k * 6
                val p = Path2D.Double(); p.moveTo(5.0 + k * 3, y)
                p.curveTo(11.0 + k * 3, y - 3, 15.0 + k * 3, y + 3, 21.0 + k * 3, y); p.curveTo(26.0 + k * 3, y - 3, 30.0, y + 3, 35.0 - k * 2, y)
                g.paint = Color(200, 206, 216, 220); g.draw(p)
            }
        }
        out += icon("hud_weather_4") { g ->
            g.paint = rgb(0xe0602a); g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (k in 0 until 8) {
                val a = k * PI / 4
                ln(g, 20 + cos(a) * 12, 17 + sin(a) * 12, 20 + cos(a) * 17, 17 + sin(a) * 17)
            }
            ball(g, 20.0, 17.0, 9.0, rgb(0xf09030))
            // трещины сухой земли
            fillOutlined(g, RoundRectangle2D.Double(3.0, 31.0, 34.0, 6.0, 3.0, 3.0), rgb(0xc8a060))
        }
        out += icon("hud_flood") { g ->
            fillOutlined(g, RoundRectangle2D.Double(9.0, 6.0, 22.0, 16.0, 3.0, 3.0), rgb(0xe8d8b0))
            val roof = Path2D.Double(); roof.moveTo(6.0, 9.0); roof.lineTo(20.0, 1.0); roof.lineTo(34.0, 9.0); roof.closePath()
            fillOutlined(g, roof, rgb(0xb84e34))
            g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (k in 0 until 3) {
                val y = 22.0 + k * 6
                val p = Path2D.Double(); p.moveTo(3.0, y); p.curveTo(9.0, y - 4, 14.0, y - 4, 20.0, y); p.curveTo(26.0, y + 4, 31.0, y + 4, 37.0, y)
                g.paint = Color(30, 22, 14, 200); g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(p)
                g.paint = rgb(0x4a8ae0); g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(p)
            }
        }
        out += icon("hud_kids") { g ->
            person(g, 13.0, 18.0, rgb(0x6a8ac8))
            // ребёнок - пониже
            fillOutlined(g, RoundRectangle2D.Double(22.0, 25.0, 10.0, 11.0, 6.0, 6.0), rgb(0xe07a6a))
            ball(g, 27.0, 21.0, 4.5, rgb(0xf0c8a0))
        }
        out += icon("hud_neighbors") { g ->
            // столб с указателями
            fillOutlined(g, RoundRectangle2D.Double(18.0, 6.0, 4.5, 31.0, 2.0, 2.0), rgb(0x8a5a32))
            val a = Path2D.Double(); a.moveTo(5.0, 9.0); a.lineTo(28.0, 9.0); a.lineTo(34.0, 13.5); a.lineTo(28.0, 18.0); a.lineTo(5.0, 18.0); a.closePath()
            fillOutlined(g, a, rgb(0xd8b070))
            val b = Path2D.Double(); b.moveTo(35.0, 21.0); b.lineTo(12.0, 21.0); b.lineTo(6.0, 25.5); b.lineTo(12.0, 30.0); b.lineTo(35.0, 30.0); b.closePath()
            fillOutlined(g, b, rgb(0xc89a58))
        }
        for (id in resourceIds) out += icon("res_$id") { g -> resource(g, id) }
        out += frames()
        return out
    }

    /** Сторона заготовки-«девятки» в единицах рисования (= 24 точки интерфейса), угол - UI_CORNER */
    const val UI_SIZE = 48
    const val UI_CORNER = 16

    /**
     * Заготовки рамок интерфейса - серые со скруглёнными углами, цвет задаёт игра (Ui): одна текстура -
     * обычная, главная и опасная кнопка. Растягивается середина, углы UI_CORNER остаются как есть.
     * ui_btn - объём: светлая кромка сверху, темнее книзу, тёмный контур; ui_panel - тёмная середина
     * со светлой рамкой; ui_chip - ровная плашка.
     */
    private fun frames(): List<Sprite> {
        val s = UI_SIZE.toDouble()
        fun frame(name: String, draw: (Graphics2D) -> Unit): Sprite {
            val (img, g) = canvas(UI_SIZE, UI_SIZE)
            draw(g)
            g.dispose()
            return Sprite(name, img, 0, 0)
        }
        fun gray(v: Double, a: Int = 255): Color { val c = (v * 255).toInt().coerceIn(0, 255); return Color(c, c, c, a) }
        return listOf(
            frame("ui_btn") { g ->
                val r = 12.0
                g.paint = gray(0.32); g.fill(RoundRectangle2D.Double(0.5, 0.5, s - 1, s - 1, r * 2, r * 2))
                g.paint = java.awt.GradientPaint(0f, 2f, gray(0.95), 0f, (s - 3).toFloat(), gray(0.74))
                g.fill(RoundRectangle2D.Double(2.0, 2.0, s - 4, s - 4, (r - 1.5) * 2, (r - 1.5) * 2))
                // нижняя кромка темнее - кнопка «стоит» над панелью
                g.clip = Rectangle2D.Double(0.0, s - 6.0, s, 6.0)
                g.paint = gray(0.58); g.fill(RoundRectangle2D.Double(2.0, 2.0, s - 4, s - 4, (r - 1.5) * 2, (r - 1.5) * 2))
                g.clip = null
                g.paint = java.awt.GradientPaint(0f, 2f, gray(0.95), 0f, (s - 6).toFloat(), gray(0.76))
                g.fill(RoundRectangle2D.Double(2.0, 2.0, s - 4, s - 7, (r - 1.5) * 2, (r - 1.5) * 2))
                // светлая кромка сверху
                g.paint = gray(1.0); g.stroke = BasicStroke(1.2f)
                g.draw(Arc2D.Double(3.2, 3.2, (r - 2) * 2, (r - 2) * 2, 90.0, 90.0, Arc2D.OPEN))
                g.draw(java.awt.geom.Line2D.Double(3.2 + r - 2, 3.2, s - 3.2 - (r - 2), 3.2))
                g.draw(Arc2D.Double(s - 3.2 - (r - 2) * 2, 3.2, (r - 2) * 2, (r - 2) * 2, 0.0, 90.0, Arc2D.OPEN))
            },
            frame("ui_panel") { g ->
                val r = 14.0
                g.paint = gray(0.45); g.fill(RoundRectangle2D.Double(0.5, 0.5, s - 1, s - 1, r * 2, r * 2))
                g.paint = gray(1.0); g.stroke = BasicStroke(1.6f)
                g.draw(RoundRectangle2D.Double(2.2, 2.2, s - 4.4, s - 4.4, (r - 1.5) * 2, (r - 1.5) * 2))
                g.paint = gray(0.38); g.fill(RoundRectangle2D.Double(3.4, 3.4, s - 6.8, s - 6.8, (r - 3) * 2, (r - 3) * 2))
            },
            frame("ui_chip") { g ->
                g.paint = gray(1.0); g.fill(RoundRectangle2D.Double(0.5, 0.5, s - 1, s - 1, 16.0, 16.0))
            },
        )
    }

    private fun icon(name: String, draw: (Graphics2D) -> Unit): Sprite {
        val (img, g) = canvas(N, N)
        draw(g)
        g.dispose()
        return Sprite(name, img, px(N / 2), 0)
    }

    private fun fillOutlined(g: Graphics2D, s: java.awt.Shape, c: Color) {
        val b = s.bounds2D
        g.paint = java.awt.GradientPaint(b.minX.toFloat(), b.minY.toFloat(), c.mul(1.12), b.maxX.toFloat(), b.maxY.toFloat(), c.mul(0.82))
        g.fill(s)
        g.paint = Color(30, 22, 14, 220)
        g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(s)
    }

    private fun ln(g: Graphics2D, x0: Double, y0: Double, x1: Double, y1: Double) = g.draw(java.awt.geom.Line2D.Double(x0, y0, x1, y1))

    private fun ball(g: Graphics2D, x: Double, y: Double, r: Double, c: Color) {
        val e = Ellipse2D.Double(x - r, y - r, r * 2, r * 2)
        g.paint = RadialGradientPaint(Point2D.Double(x - r * 0.35, y - r * 0.4), (r * 1.5).toFloat(), floatArrayOf(0f, 1f), arrayOf(c.mul(1.3), c.mul(0.75)))
        g.fill(e)
        g.paint = Color(30, 22, 14, 220); g.stroke = BasicStroke(1.8f); g.draw(e)
    }

    private fun coin(g: Graphics2D, x: Double, y: Double, r: Double) {
        ball(g, x, y, r, rgb(0xf0c040))
        g.paint = rgb(0xb07a10); g.stroke = BasicStroke(1.6f)
        g.draw(Ellipse2D.Double(x - r * 0.62, y - r * 0.62, r * 1.24, r * 1.24))
        g.paint = rgb(0xfff0b0); g.fill(Ellipse2D.Double(x - r * 0.45, y - r * 0.6, r * 0.35, r * 0.25))
    }

    private fun person(g: Graphics2D, x: Double, y: Double, shirt: Color) {
        fillOutlined(g, RoundRectangle2D.Double(x - 6, y, 12.0, 14.0, 7.0, 7.0), shirt)
        ball(g, x, y - 5, 5.5, rgb(0xf0c8a0))
    }

    private fun flower(g: Graphics2D) {
        for (k in 0 until 5) {
            val a = k * 2 * PI / 5 - PI / 2
            ball(g, 20 + cos(a) * 8, 20 + sin(a) * 8, 6.0, rgb(0xf0a0c0))
        }
        ball(g, 20.0, 20.0, 5.0, rgb(0xf0d040))
    }

    private fun sun(g: Graphics2D) {
        g.paint = rgb(0xf0b030); g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (k in 0 until 8) {
            val a = k * PI / 4
            g.draw(java.awt.geom.Line2D.Double(20 + cos(a) * 12, 20 + sin(a) * 12, 20 + cos(a) * 17, 20 + sin(a) * 17))
        }
        ball(g, 20.0, 20.0, 9.0, rgb(0xf8d040))
    }

    private fun leaf(g: Graphics2D) {
        val p = Path2D.Double()
        p.moveTo(8.0, 32.0); p.curveTo(6.0, 12.0, 20.0, 4.0, 34.0, 6.0); p.curveTo(34.0, 22.0, 24.0, 34.0, 8.0, 32.0); p.closePath()
        fillOutlined(g, p, rgb(0xd8782a))
        g.paint = rgb(0x8a4010); g.stroke = BasicStroke(1.6f)
        g.draw(java.awt.geom.Line2D.Double(9.0, 31.0, 30.0, 10.0))
    }

    private fun snow(g: Graphics2D) {
        for (w in floatArrayOf(5f, 2.6f)) {
            g.paint = if (w > 3) Color(30, 40, 70) else rgb(0xe8f4ff)
            g.stroke = BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (k in 0 until 3) {
                val a = k * PI / 3
                g.draw(java.awt.geom.Line2D.Double(20 - cos(a) * 15, 20 - sin(a) * 15, 20 + cos(a) * 15, 20 + sin(a) * 15))
            }
        }
    }

    private fun cloud(g: Graphics2D, c: Int) {
        val a = java.awt.geom.Area(Ellipse2D.Double(5.0, 12.0, 14.0, 12.0))
        a.add(java.awt.geom.Area(Ellipse2D.Double(11.0, 5.0, 17.0, 17.0)))
        a.add(java.awt.geom.Area(Ellipse2D.Double(22.0, 10.0, 13.0, 13.0)))
        a.add(java.awt.geom.Area(RoundRectangle2D.Double(7.0, 15.0, 26.0, 9.0, 8.0, 8.0)))
        fillOutlined(g, a, rgb(c))
    }

    private fun cart(g: Graphics2D) {
        fillOutlined(g, Rectangle2D.Double(6.0, 12.0, 26.0, 13.0), rgb(0xa8743a))
        val p = Path2D.Double(); p.moveTo(8.0, 12.0); p.curveTo(12.0, 2.0, 26.0, 2.0, 30.0, 12.0); p.closePath()
        fillOutlined(g, p, rgb(0xe8dcc0))
        ball(g, 12.0, 29.0, 5.0, rgb(0x6a4a2a)); ball(g, 27.0, 29.0, 5.0, rgb(0x6a4a2a))
    }

    private fun scroll(g: Graphics2D) {
        fillOutlined(g, RoundRectangle2D.Double(9.0, 6.0, 22.0, 28.0, 4.0, 4.0), rgb(0xf0e0b8))
        fillOutlined(g, RoundRectangle2D.Double(6.0, 4.0, 28.0, 6.0, 5.0, 5.0), rgb(0xc8a870))
        fillOutlined(g, RoundRectangle2D.Double(6.0, 30.0, 28.0, 6.0, 5.0, 5.0), rgb(0xc8a870))
        g.paint = rgb(0x8a6a40); g.stroke = BasicStroke(1.6f)
        for (k in 0 until 3) g.draw(java.awt.geom.Line2D.Double(13.0, 15.0 + k * 5, 27.0, 15.0 + k * 5))
    }

    /** Значок ресурса по его id */
    private fun resource(g: Graphics2D, id: String) {
        when (id) {
            "logs" -> for ((k, y) in listOf(26.0, 18.0, 10.0).withIndex()) {
                val x = if (k == 1) 10.0 else 6.0
                fillOutlined(g, RoundRectangle2D.Double(x, y, 26.0, 9.0, 8.0, 8.0), rgb(0x8a5a2b))
                ball(g, x + 4, y + 4.5, 3.8, rgb(0xd8b878))
            }
            "planks" -> for (k in 0 until 3) fillOutlined(g, Rectangle2D.Double(5.0 + k * 2, 26.0 - k * 8, 30.0, 6.5), rgb(0xd8b070))
            "stone" -> { ball(g, 14.0, 24.0, 10.0, rgb(0x9a9a9e)); ball(g, 26.0, 18.0, 9.0, rgb(0xb0b0b4)) }
            "grain" -> {
                g.paint = rgb(0x9a7a20); g.stroke = BasicStroke(2f)
                for (k in -1..1) g.draw(java.awt.geom.Line2D.Double(20.0 + k * 3, 36.0, 20.0 + k * 7, 10.0))
                for (k in -1..1) for (j in 0 until 4) ball(g, 20.0 + k * 7 - j * k * 0.8, 8.0 + j * 5, 3.2, rgb(0xe8c850))
            }
            "flour" -> {
                fillOutlined(g, RoundRectangle2D.Double(8.0, 10.0, 24.0, 26.0, 10.0, 10.0), rgb(0xf4f0e0))
                g.paint = rgb(0x8a7a5a); g.stroke = BasicStroke(2f); g.draw(java.awt.geom.Line2D.Double(14.0, 12.0, 26.0, 12.0))
            }
            "bread" -> {
                fillOutlined(g, Ellipse2D.Double(4.0, 12.0, 32.0, 20.0), rgb(0xc07830))
                g.paint = rgb(0xf0c080); g.stroke = BasicStroke(2f)
                for (k in 0 until 3) g.draw(java.awt.geom.Line2D.Double(12.0 + k * 7, 15.0, 15.0 + k * 7, 25.0))
            }
            "fish" -> {
                val p = Path2D.Double(); p.moveTo(4.0, 20.0); p.curveTo(12.0, 8.0, 26.0, 10.0, 30.0, 20.0); p.curveTo(26.0, 30.0, 12.0, 32.0, 4.0, 20.0)
                p.moveTo(28.0, 20.0); p.lineTo(37.0, 12.0); p.lineTo(37.0, 28.0); p.closePath()
                fillOutlined(g, p, rgb(0x70a8c8))
                g.paint = rgb(0x203040); g.fill(Ellipse2D.Double(9.0, 17.0, 3.5, 3.5))
            }
            "water" -> {
                val p = Path2D.Double(); p.moveTo(20.0, 4.0); p.curveTo(28.0, 16.0, 32.0, 22.0, 32.0, 26.0)
                p.curveTo(32.0, 34.0, 26.0, 37.0, 20.0, 37.0); p.curveTo(14.0, 37.0, 8.0, 34.0, 8.0, 26.0); p.curveTo(8.0, 22.0, 12.0, 16.0, 20.0, 4.0)
                fillOutlined(g, p, rgb(0x3a7bd5))
                g.paint = Color(255, 255, 255, 170); g.fill(Ellipse2D.Double(13.0, 22.0, 4.0, 7.0))
            }
            "meat" -> {
                fillOutlined(g, Ellipse2D.Double(6.0, 8.0, 24.0, 20.0), rgb(0xc04a3a))
                fillOutlined(g, RoundRectangle2D.Double(24.0, 24.0, 12.0, 6.0, 4.0, 4.0), rgb(0xf0e8d8))
                g.paint = rgb(0xf0a090); g.fill(Ellipse2D.Double(12.0, 13.0, 9.0, 6.0))
            }
            "apples" -> { ball(g, 14.0, 24.0, 9.0, rgb(0xd0402a)); ball(g, 26.0, 22.0, 9.0, rgb(0x90b030)); g.paint = rgb(0x5a3a1a); g.stroke = BasicStroke(2f); g.draw(java.awt.geom.Line2D.Double(14.0, 15.0, 16.0, 9.0)) }
            "wool" -> { for ((x, y) in listOf(12.0 to 22.0, 22.0 to 18.0, 28.0 to 25.0, 18.0 to 28.0)) ball(g, x, y, 8.0, rgb(0xf0ece0)) }
            "cloth" -> {
                fillOutlined(g, RoundRectangle2D.Double(5.0, 10.0, 30.0, 22.0, 6.0, 6.0), rgb(0x7a5ab0))
                g.paint = rgb(0xc0a8e8); g.stroke = BasicStroke(1.6f)
                for (k in 0 until 3) g.draw(java.awt.geom.Line2D.Double(8.0, 16.0 + k * 5, 32.0, 16.0 + k * 5))
            }
            "ale" -> {
                fillOutlined(g, RoundRectangle2D.Double(9.0, 10.0, 18.0, 26.0, 4.0, 4.0), rgb(0xd8a030))
                g.paint = rgb(0x5a3a1a); g.stroke = BasicStroke(3f); g.draw(Arc2D.Double(22.0, 15.0, 12.0, 14.0, -90.0, 180.0, Arc2D.OPEN))
                for ((x, r) in listOf(12.0 to 5.0, 19.0 to 5.5, 25.0 to 4.0)) ball(g, x, 10.0, r, rgb(0xf8f4e8))
            }
            "ore" -> {
                ball(g, 13.0, 25.0, 9.0, rgb(0x9a6a4a)); ball(g, 27.0, 23.0, 8.0, rgb(0x8a5a3a)); ball(g, 20.0, 13.0, 8.0, rgb(0xa87a58))
                g.paint = rgb(0xf0d890); for ((x, y) in listOf(10.0 to 22.0, 25.0 to 20.0, 18.0 to 10.0, 29.0 to 26.0)) g.fill(Ellipse2D.Double(x, y, 3.0, 3.0))
            }
            "coal" -> {
                for ((x, y, r) in listOf(Triple(13.0, 26.0, 8.5), Triple(27.0, 25.0, 8.0), Triple(20.0, 13.0, 8.0))) ball(g, x, y, r, rgb(0x2a2a2e))
                g.paint = Color(255, 255, 255, 120); g.fill(Ellipse2D.Double(10.0, 22.0, 4.0, 3.0)); g.fill(Ellipse2D.Double(18.0, 9.0, 4.0, 3.0))
            }
            "iron" -> {
                val p = Path2D.Double(); p.moveTo(8.0, 28.0); p.lineTo(13.0, 14.0); p.lineTo(31.0, 14.0); p.lineTo(35.0, 28.0); p.closePath()
                fillOutlined(g, p, rgb(0x8a8e98))
                fillOutlined(g, RoundRectangle2D.Double(6.0, 28.0, 30.0, 7.0, 3.0, 3.0), rgb(0x6a6e78))
                g.paint = Color(255, 255, 255, 150); g.fill(Rectangle2D.Double(16.0, 16.0, 11.0, 3.0))
            }
            "tools" -> {
                g.stroke = BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.paint = rgb(0x6a4a2a); g.draw(java.awt.geom.Line2D.Double(9.0, 35.0, 30.0, 10.0)); g.draw(java.awt.geom.Line2D.Double(31.0, 35.0, 10.0, 10.0))
                fillOutlined(g, RoundRectangle2D.Double(22.0, 4.0, 14.0, 8.0, 3.0, 3.0), rgb(0x9a9ea8))
                val p = Path2D.Double(); p.moveTo(2.0, 12.0); p.curveTo(8.0, 2.0, 18.0, 2.0, 24.0, 8.0); p.lineTo(21.0, 11.0); p.curveTo(16.0, 7.0, 9.0, 7.0, 5.0, 14.0); p.closePath()
                fillOutlined(g, p, rgb(0x9a9ea8))
            }
            "hides" -> {
                val p = Path2D.Double(); p.moveTo(6.0, 8.0); p.lineTo(15.0, 11.0); p.lineTo(25.0, 11.0); p.lineTo(34.0, 8.0); p.lineTo(31.0, 20.0)
                p.lineTo(35.0, 31.0); p.lineTo(25.0, 29.0); p.lineTo(15.0, 29.0); p.lineTo(5.0, 31.0); p.lineTo(9.0, 20.0); p.closePath()
                fillOutlined(g, p, rgb(0xb88a5a))
                g.paint = rgb(0x6a4a2a); g.stroke = BasicStroke(1.5f)
                for ((x, y) in listOf(6.0 to 8.0, 34.0 to 8.0, 5.0 to 31.0, 35.0 to 31.0)) g.fill(Ellipse2D.Double(x - 2, y - 2, 4.0, 4.0))
            }
            "leather" -> {
                fillOutlined(g, RoundRectangle2D.Double(5.0, 12.0, 30.0, 18.0, 8.0, 8.0), rgb(0x8a5530))
                g.paint = rgb(0x5a3518); g.stroke = BasicStroke(2f); g.draw(Arc2D.Double(0.0, 12.0, 14.0, 18.0, 90.0, 180.0, Arc2D.OPEN))
                g.paint = rgb(0xd8b878); g.stroke = BasicStroke(1.8f); g.draw(java.awt.geom.Line2D.Double(9.0, 21.0, 31.0, 21.0))
                g.paint = rgb(0x3a2410); g.stroke = BasicStroke(2.2f); g.draw(java.awt.geom.Line2D.Double(17.0, 12.0, 17.0, 30.0)); g.draw(java.awt.geom.Line2D.Double(26.0, 12.0, 26.0, 30.0))
            }
            "clothes" -> {
                val p = Path2D.Double(); p.moveTo(14.0, 6.0); p.lineTo(26.0, 6.0); p.lineTo(36.0, 14.0); p.lineTo(31.0, 20.0); p.lineTo(28.0, 18.0)
                p.lineTo(28.0, 35.0); p.lineTo(12.0, 35.0); p.lineTo(12.0, 18.0); p.lineTo(9.0, 20.0); p.lineTo(4.0, 14.0); p.closePath()
                fillOutlined(g, p, rgb(0x3a70c0))
                g.paint = rgb(0x1a3a70); g.stroke = BasicStroke(1.6f); g.draw(Arc2D.Double(15.0, 2.0, 10.0, 8.0, 180.0, 180.0, Arc2D.OPEN))
                g.paint = rgb(0xf0e8d0); g.fill(Ellipse2D.Double(19.0, 14.0, 2.5, 2.5)); g.fill(Ellipse2D.Double(19.0, 20.0, 2.5, 2.5))
            }
            "pork" -> {
                fillOutlined(g, Ellipse2D.Double(5.0, 7.0, 26.0, 22.0), rgb(0xf0a0a0))
                fillOutlined(g, RoundRectangle2D.Double(26.0, 24.0, 11.0, 6.0, 4.0, 4.0), rgb(0xf4ece0))
                g.paint = rgb(0xffd8d0); g.fill(Ellipse2D.Double(11.0, 11.0, 9.0, 6.0))
                g.paint = rgb(0xc86a6a); g.stroke = BasicStroke(1.5f); g.draw(Arc2D.Double(12.0, 14.0, 14.0, 10.0, 200.0, 140.0, Arc2D.OPEN))
            }
            "smoked" -> {
                fillOutlined(g, Ellipse2D.Double(5.0, 7.0, 26.0, 22.0), rgb(0x7a2e20))
                fillOutlined(g, RoundRectangle2D.Double(26.0, 24.0, 11.0, 6.0, 4.0, 4.0), rgb(0xd8c8b0))
                g.paint = rgb(0x4a1a10); g.stroke = BasicStroke(2f)
                for (k in 0 until 3) g.draw(java.awt.geom.Line2D.Double(10.0 + k * 6, 11.0, 13.0 + k * 6, 24.0))
                g.paint = Color(200, 200, 200, 150); g.stroke = BasicStroke(1.8f)
                g.draw(Arc2D.Double(8.0, 0.0, 6.0, 8.0, 0.0, 200.0, Arc2D.OPEN)); g.draw(Arc2D.Double(20.0, 0.0, 6.0, 8.0, 180.0, 200.0, Arc2D.OPEN))
            }
            "honey" -> {
                fillOutlined(g, RoundRectangle2D.Double(7.0, 14.0, 26.0, 22.0, 12.0, 12.0), rgb(0xd89a30))
                fillOutlined(g, RoundRectangle2D.Double(10.0, 8.0, 20.0, 8.0, 4.0, 4.0), rgb(0x8a5a2b))
                g.paint = rgb(0xf8d070); g.fill(Ellipse2D.Double(12.0, 19.0, 7.0, 9.0))
                g.paint = rgb(0x5a3a1a); g.fill(Rectangle2D.Double(15.0, 24.0, 10.0, 5.0))
            }
            "wax" -> {
                fillOutlined(g, Rectangle2D.Double(5.0, 22.0, 30.0, 10.0), rgb(0xe8c850))
                fillOutlined(g, Rectangle2D.Double(9.0, 13.0, 26.0, 9.0), rgb(0xf0d868))
                fillOutlined(g, Rectangle2D.Double(5.0, 5.0, 22.0, 8.0), rgb(0xd8b438))
                g.paint = Color(255, 255, 255, 140); g.fill(Rectangle2D.Double(8.0, 24.0, 12.0, 2.5))
            }
            "candles" -> {
                for ((x, h) in listOf(9.0 to 20.0, 23.0 to 24.0)) {
                    fillOutlined(g, RoundRectangle2D.Double(x, 36.0 - h, 9.0, h, 3.0, 3.0), rgb(0xf4ecc8))
                    g.paint = rgb(0x3a2a1a); g.stroke = BasicStroke(1.5f); g.draw(java.awt.geom.Line2D.Double(x + 4.5, 36.0 - h, x + 4.5, 33.0 - h))
                    g.paint = rgb(0xffb020); g.fill(Ellipse2D.Double(x + 1.5, 27.0 - h, 6.0, 8.0))
                    g.paint = rgb(0xfff0a0); g.fill(Ellipse2D.Double(x + 3.0, 31.0 - h, 3.0, 4.0))
                }
            }
            "barrow" -> {
                g.paint = rgb(0x6a4a2a); g.stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                ln(g, 4.0, 10.0, 16.0, 22.0); ln(g, 8.0, 26.0, 13.0, 31.0)
                val tray = Path2D.Double(); tray.moveTo(12.0, 14.0); tray.lineTo(34.0, 12.0); tray.lineTo(30.0, 26.0); tray.lineTo(16.0, 26.0); tray.closePath()
                fillOutlined(g, tray, rgb(0xb8844a))
                g.paint = rgb(0xe8c890); g.stroke = BasicStroke(1.6f); ln(g, 15.0, 18.0, 32.0, 17.0)
                ball(g, 30.0, 31.0, 6.0, rgb(0x6a4a2a)); ball(g, 30.0, 31.0, 2.2, rgb(0xd8b878))
            }
            "wagon" -> {
                fillOutlined(g, RoundRectangle2D.Double(4.0, 10.0, 32.0, 14.0, 3.0, 3.0), rgb(0xb8844a))
                g.paint = rgb(0x6a4a2a); g.stroke = BasicStroke(1.8f); for (k in 0 until 3) ln(g, 12.0 + k * 9.0, 11.0, 12.0 + k * 9.0, 23.0)
                g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); ln(g, 34.0, 20.0, 39.0, 28.0)
                for (x in listOf(12.0, 28.0)) { ball(g, x, 29.0, 6.5, rgb(0x6a4a2a)); ball(g, x, 29.0, 2.4, rgb(0xd8b878)) }
            }
            else -> ball(g, 20.0, 20.0, 12.0, rgb(0xa0a0a0))
        }
    }
}
