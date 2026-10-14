package town.tools

import com.badlogic.gdx.utils.JsonReader
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Здания и жители (person_*) из Blender (tools/blender/render.py -> build/render): картинки bld_<id>[_r<поворот>][_snow].png
 * и render.json с точкой привязки и точками (трубы, ось крыльев). Картинка обрезается по непрозрачному,
 * вокруг здания (не тени) рисуется тёмный контур - как обводка у остальной графики.
 */
object Renders {
    class Result(val sprites: List<Sprite>, val points: Map<String, Map<String, FloatArray>>)

    /** Контур: толщина в пикселях атласа и цвет (ARGB) */
    private const val OUTLINE_R2 = 6
    /** У жителей контур тоньше - фигурки мелкие */
    private const val PERSON_OUTLINE_R2 = 2
    /** Деревья и скалы - полный размер, контур 2 пикселя; кусты мельче - 1,4 */
    private const val NATURE_OUTLINE_R2 = 4
    private const val BUSH_OUTLINE_R2 = 2
    private const val OUTLINE = 0xe6241a10.toInt()
    /** Голые зимние лиственные: без контура (иначе полупрозрачные прутики целиком темнеют) */
    private val WINTER_TREE = Regex("tree_[0-3]_snow")
    private const val SOFT_OUTLINE = 0xb3301f10.toInt()
    private val NATURE = Regex("(tree_[0-9]+(_snow)?|rock_[0-9]+|prop_bush(snow)?_[0-9]+)")

    /** Природа карты из Blender: в атласе в полном размере, как жители */
    fun isNature(name: String) = NATURE.matches(name)
    /** Непрозрачнее этого - здание; полупрозрачная тень не обводится */
    private const val SOLID = 200

    fun load(dir: File): Result? {
        val meta = File(dir, "render.json")
        if (!meta.exists()) return null
        val root = JsonReader().parse(meta.readText())
        val sprites = ArrayList<Sprite>()
        val points = HashMap<String, Map<String, FloatArray>>()
        var e = root.child
        while (e != null) {
            val f = File(dir, e.name + ".png")
            if (f.exists()) {
                val src = ImageIO.read(f)
                val argb = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
                argb.createGraphics().apply { drawImage(src, 0, 0, null); dispose() }
                // у мелких жителей шум ловушки теней (почти прозрачные пиксели по всему кадру) раздувает обрезку
                if (isNature(e.name) || e.name.startsWith("person_") || e.name.startsWith("wolf_") || e.name.startsWith("kid_") || e.name.startsWith("wagon_") || e.name.startsWith("anim_")) for (y in 0 until argb.height) for (x in 0 until argb.width) {
                    if ((argb.getRGB(x, y) ushr 24) < 16) argb.setRGB(x, y, 0)
                }
                // у жителей и анимаций (бельё, флаг) контур тоньше
                val outlined = if (WINTER_TREE.matches(e.name)) softOutline(argb) else outline(argb, if (e.name.startsWith("prop_bush")) BUSH_OUTLINE_R2 else if (isNature(e.name)) NATURE_OUTLINE_R2 else if (e.name.startsWith("person_") || e.name.startsWith("wolf_") || e.name.startsWith("kid_") || e.name.startsWith("wagon_") || e.name.startsWith("anim_")) PERSON_OUTLINE_R2 else OUTLINE_R2)
                sprites += trim(e.name, outlined, e.getInt("ax"), e.getInt("ay"))
                // картинка для карточки постройки: только здание, без тени и полей
                if (e.name.matches(Regex("bld_[a-z0-9]+")) && !e.name.endsWith("_shore")) sprites += card("card_" + e.name.removePrefix("bld_"), outlined)
                val pts = HashMap<String, FloatArray>()
                var p = e.get("points")?.child
                while (p != null) { pts[p.name] = p.asFloatArray(); p = p.next }
                if (pts.isNotEmpty()) points[e.name] = pts
            }
            e = e.next
        }
        println("renders: ${sprites.size} from ${dir.path}")
        return Result(sprites, points)
    }

    /** Мягкий контур для голых зимних деревьев: 1 px, полупрозрачный, только у плотных пикселей (ствол и толстые ветви), не у прутиков */
    private fun softOutline(src: BufferedImage): BufferedImage {
        val w = src.width; val h = src.height
        val a = IntArray(w * h) { src.getRGB(it % w, it / w) ushr 24 }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) {
            var c = src.getRGB(x, y)
            if (a[y * w + x] < 60) {
                var near = false
                loop@ for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until w && yy in 0 until h && a[yy * w + xx] >= 150) { near = true; break@loop }
                }
                if (near) c = SOFT_OUTLINE
            }
            out.setRGB(x, y, c)
        }
        return out
    }

    internal fun outline(src: BufferedImage, r2: Int): BufferedImage {
        val w = src.width; val h = src.height
        val a = IntArray(w * h) { src.getRGB(it % w, it / w) ushr 24 }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var c = src.getRGB(x, y)
            if (a[i] < SOLID) {
                var near = false
                loop@ for (dy in -2..2) for (dx in -2..2) {
                    if (dx * dx + dy * dy > r2) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until w && yy in 0 until h && a[yy * w + xx] >= SOLID) { near = true; break@loop }
                }
                if (near) c = OUTLINE
            }
            out.setRGB(x, y, c)
        }
        return out
    }

    /** Обрезка по самому зданию (непрозрачное, без полупрозрачной тени) с полем в 4 пикселя */
    private fun card(name: String, img: BufferedImage): Sprite {
        var minX = img.width; var minY = img.height; var maxX = -1; var maxY = -1
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if ((img.getRGB(x, y) ushr 24) >= SOLID) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        minX = maxOf(0, minX - 4); minY = maxOf(0, minY - 4); maxX = minOf(img.width - 1, maxX + 4); maxY = minOf(img.height - 1, maxY + 4)
        val out = BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB)
        for (y in minY..maxY) for (x in minX..maxX) {
            val c = img.getRGB(x, y)
            out.setRGB(x - minX, y - minY, if ((c ushr 24) < SOLID && c != OUTLINE) 0 else c)
        }
        return Sprite(name, out, out.width / 2, 0)
    }

    /** Обрезка до непрозрачной области; ay в render.json - от низа кадра */
    private fun trim(name: String, img: BufferedImage, ax: Int, ayBottom: Int): Sprite {
        var minX = img.width; var minY = img.height; var maxX = -1; var maxY = -1
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if ((img.getRGB(x, y) ushr 24) > 4) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        val ayTop = img.height - ayBottom   // привязка в координатах картинки (сверху)
        minX = minOf(minX, ax); maxX = maxOf(maxX, ax)
        minY = minOf(minY, ayTop - 1); maxY = maxOf(maxY, ayTop - 1)
        val sub = img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
        val copy = BufferedImage(sub.width, sub.height, BufferedImage.TYPE_INT_ARGB)
        copy.createGraphics().apply { drawImage(sub, 0, 0, null); dispose() }
        return Sprite(name, copy, ax - minX, (maxY + 1) - ayTop)
    }
}
