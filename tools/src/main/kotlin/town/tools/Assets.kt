package town.tools

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import town.data.Content

/**
 * Генерация ресурсов игры: ./gradlew :tools:run (из корня проекта).
 * 1. Спрайты (Art, Terrain, Hud) -> атлас assets/sprites/atlas.png + atlas.json (имя -> x, y, w, h, ax, ay[, свой масштаб];
 *    общий масштаб ATLAS_SCALE) и лёгкий atlas_low.png/json для слабых телефонов: всё вдвое мельче, без переходов
 *    земли и мелких предметов; значки интерфейса в полном размере (у них свой масштаб в json).
 *    Замена: tools/art/override/<имя>.png используется вместо нарисованного кодом спрайта той же роли
 *    (точка привязки масштабируется пропорционально размеру картинки) - так подставляются картинки из ИИ-генератора.
 * 2. Звуки и музыка (Synth) -> assets/sounds/<имя>.wav.
 * 3. Иконка (Icon) -> android/src/main/res/mipmap-*, assets/web (значок вкладки, логотип загрузки).
 */
fun main() {
    val content = Content.parse(File("assets/data/content.json").readText())
    // замены из ИИ-генератора (tools/art/override, tools/ai/gen.py) - сразу: переходы земли строятся из клеток tile_*
    val art = Art.all(content.buildings.map { it.id to it.size }).map { override(it) }
    // здания из Blender (build/render, см. tools/blender/render.py) заменяют нарисованные кодом
    val renders = Renders.load(File("build/render"))
    if (renders == null) println("WARNING: build/render/render.json not found - buildings drawn by code (run tools/blender/render.py)")
    val rendered = renders?.sprites?.associateBy { it.name } ?: emptyMap()
    val terrain = Terrain.all(art) + Hud.all(content.resources.map { it.id })
    // природа (деревья, скалы, кусты) из Blender заменяет и нарисованное в Art, и кусты из Terrain
    val base = art.map { rendered[it.name] ?: it } + rendered.values.filter { r -> art.none { it.name == r.name } && terrain.none { it.name == r.name } }
    val sprites = base + terrain.map { rendered[it.name] ?: override(it) }.map { if (it.name.startsWith("res_")) outlineIcon(it) else it } +
        listOfNotNull(Logo.sprite())
    val points = renders?.points ?: emptyMap()
    val dir = File("assets/sprites")
    // здания из Blender в атласе мельче (2x в полном, 1x в лёгком) - иначе 4 стороны x 2 сезона не влезают в память телефона
    // жители мелкие - в полном размере, как остальная графика
    val isRender = { sp: Sprite -> rendered.containsKey(sp.name) && !sp.name.startsWith("person_") && !sp.name.startsWith("wolf_") && !sp.name.startsWith("kid_") && !sp.name.startsWith("wagon_") && !Renders.isNature(sp.name) }
    pack(sprites.map { if (isRender(it)) Packed(resize(it, 2, 3), 2.0 / 3) else Packed(it, 1.0) }, dir, "atlas", ATLAS_SCALE, points)
    pack(sprites.filter { !it.name.startsWith("dual_") && !(it.name.startsWith("prop_") && !it.name.startsWith("prop_fish_")) && !it.name.startsWith("base_") }.map {
        if (it.name.startsWith("hud_") || it.name.startsWith("res_") || it.name.startsWith("icon_") || it.name == "white" || it.name == "ui_logo") Packed(it, 2.0)
        else if (isRender(it)) Packed(resize(it, 1, 3), 2.0 / 3)
        else Packed(half(it), 1.0)
    }, dir, "atlas_low", ATLAS_SCALE / 2, points)
    Synth.all(File("assets/sounds"))
    Icon.all(File("android/src/main/res"), File("assets/web"))
}

private fun override(s: Sprite): Sprite {
    val f = File("tools/art/override/${s.name}.png")
    if (!f.exists()) return s
    val img = ImageIO.read(f)
    val argb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
    argb.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
    println("override: ${s.name}")
    val sp = Sprite(s.name, argb, s.ax * img.width / maxOf(1, s.img.width), s.ay * img.height / maxOf(1, s.img.height))
    return if (s.name.startsWith("tree_")) calmTree(sp) else if (GRASS_TILE.matches(s.name)) calmGrass(sp) else sp
}

/**
 * Летний луг из ИИ: был кислотно-лаймовым и спорил с приглушёнными зданиями - бледнее, к оливковому и чуть темнее
 * (зимний луг - снег, его не трогаем)
 */
private val GRASS_TILE = Regex("tile_(grass|forest)_[0-9]")
private const val GRASS_SAT = 0.72f
private const val GRASS_HUE_SHIFT = 7f / 360f
private const val GRASS_VALUE = 0.94f

private fun calmGrass(s: Sprite): Sprite {
    val w = s.img.width; val h = s.img.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val hsb = FloatArray(3)
    for (y in 0 until h) for (x in 0 until w) {
        val c = s.img.getRGB(x, y)
        val a = c ushr 24
        if (a == 0) continue
        java.awt.Color.RGBtoHSB(c shr 16 and 255, c shr 8 and 255, c and 255, hsb)
        var hue = hsb[0]
        if (hue in 0.17f..0.45f) hue -= GRASS_HUE_SHIFT
        out.setRGB(x, y, (a shl 24) or (java.awt.Color.HSBtoRGB(hue, hsb[1] * GRASS_SAT, hsb[2] * GRASS_VALUE) and 0xffffff))
    }
    debugSave(s.name, out)
    return Sprite(s.name, out, s.ax, s.ay)
}

/** Деревья из ИИ: спокойнее по цвету (меньше насыщенности, мягче света, зелень к оливковой) и тёмный контур как у зданий */
private const val TREE_SAT = 0.75f
private const val TREE_TOP = 0.7f
private const val TREE_SQUASH = 0.09f
private const val TREE_HUE_SHIFT = 9f / 360f
private const val TREE_OUTLINE_R2 = 4
private const val TREE_PAD = 3

private fun calmTree(s: Sprite): Sprite {
    val w = s.img.width; val h = s.img.height
    val out = BufferedImage(w + TREE_PAD * 2, h + TREE_PAD * 2, BufferedImage.TYPE_INT_ARGB)
    val hsb = FloatArray(3)
    for (y in 0 until h) for (x in 0 until w) {
        val c = s.img.getRGB(x, y)
        val a = c ushr 24
        if (a == 0) continue
        var r = (c shr 16 and 255) / 255f; var g = (c shr 8 and 255) / 255f; var b = (c and 255) / 255f
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        r = l + (r - l) * TREE_SAT; g = l + (g - l) * TREE_SAT; b = l + (b - l) * TREE_SAT
        java.awt.Color.RGBtoHSB((r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255), hsb)
        var v = hsb[2]
        if (v > TREE_TOP) v -= (v - TREE_TOP) / (1f - TREE_TOP) * TREE_SQUASH
        var hue = hsb[0]
        if (hue in 0.19f..0.48f && hsb[1] > 0.12f) hue -= TREE_HUE_SHIFT
        out.setRGB(x + TREE_PAD, y + TREE_PAD, (a shl 24) or (java.awt.Color.HSBtoRGB(hue, hsb[1], v) and 0xffffff))
    }
    val o = Renders.outline(out, TREE_OUTLINE_R2)
    debugSave(s.name, o)
    return Sprite(s.name, o, s.ax + TREE_PAD, s.ay + TREE_PAD)
}

private fun debugSave(name: String, img: BufferedImage) {
    val d = System.getenv("TOWN_DEBUG_DIR") ?: return
    File(d).mkdirs(); ImageIO.write(img, "png", File(d, "$name.png"))
}

private const val ICON_PAD = 2
private const val ICON_OUTLINE_ALPHA = 0.85
private const val ICON_OUTLINE_COLOR = 0x2a1a0e

/** Мягкий тёмно-коричневый контур значка: ~1.3 пикселя, край полупрозрачный; холст шире на ICON_PAD с каждой стороны */
private fun outlineIcon(s: Sprite): Sprite {
    val w = s.img.width + ICON_PAD * 2; val h = s.img.height + ICON_PAD * 2
    val rad = maxOf(1.0, s.img.width / 45.0)
    val src = IntArray(w * h)
    for (y in 0 until s.img.height) for (x in 0 until s.img.width) src[(y + ICON_PAD) * w + x + ICON_PAD] = s.img.getRGB(x, y)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val reach = Math.ceil(rad + 0.5).toInt()
    for (y in 0 until h) for (x in 0 until w) {
        val c = src[y * w + x]
        val a = c ushr 24
        if (a == 255) { out.setRGB(x, y, c); continue }
        var best = 99.0
        for (dy in -reach..reach) for (dx in -reach..reach) {
            val xx = x + dx; val yy = y + dy
            if (xx !in 0 until w || yy !in 0 until h) continue
            val na = src[yy * w + xx] ushr 24
            if (na < 128) continue
            val d = Math.sqrt((dx * dx + dy * dy).toDouble()) - (na - 128) / 255.0 * 0.0
            if (d < best) best = d
        }
        val oa = (rad + 0.5 - best).coerceIn(0.0, 1.0) * ICON_OUTLINE_ALPHA
        if (oa <= 0.0) { out.setRGB(x, y, c); continue }
        val fa = a / 255.0
        val ra = fa + oa * (1 - fa)
        fun mix(sh: Int) = ((((c shr sh and 255) * fa + (ICON_OUTLINE_COLOR shr sh and 255) * oa * (1 - fa)) / ra)).toInt().coerceIn(0, 255)
        out.setRGB(x, y, ((ra * 255).toInt().coerceIn(0, 255) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0))
    }
    debugSave(s.name, out)
    return Sprite(s.name, out, s.ax + ICON_PAD, s.ay + ICON_PAD)
}

/** Полочная упаковка с отступом PAD и продлением краёв (чтобы при мипмапах соседние спрайты не просвечивали) */
private const val PAD = 3

/**
 * Цвет под прозрачные пиксели: прозрачные получают цвет ближайшего непрозрачного соседа (несколько проходов).
 * Мипмапы смешивают и прозрачные пиксели - без этого края спрайтов на отдалении темнеют (там чёрный).
 */
private fun bleed(s: Sprite): Sprite {
    val w = s.img.width; val h = s.img.height
    val px = IntArray(w * h) { s.img.getRGB(it % w, it / w) }
    val done = BooleanArray(w * h) { (px[it] ushr 24) > 0 }
    repeat(8) {
        val next = done.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (done[i]) continue
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h || !done[yy * w + xx]) continue
                val c = px[yy * w + xx]; r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; n++
            }
            if (n > 0) { px[i] = ((r / n) shl 16) or ((g / n) shl 8) or (b / n); next[i] = true }
        }
        System.arraycopy(next, 0, done, 0, done.size)
    }
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    for (i in px.indices) out.setRGB(i % w, i / w, px[i])
    return Sprite(s.name, out, s.ax, s.ay)
}

/** Спрайт в атласе; k - во сколько раз его масштаб больше общего масштаба атласа */
private class Packed(val s: Sprite, val k: Double)

/** Уменьшение вдвое: среднее 2x2 с учётом прозрачности; точка привязки делится пополам */
/**
 * Уменьшение в p/q раз (p < q): каждый пиксель повторяется p x p, затем среднее по квадратам q x q с учётом прозрачности.
 * Точка привязки масштабируется.
 */
private fun resize(s: Sprite, p: Int, q: Int): Sprite {
    val sw = s.img.width * p; val sh = s.img.height * p
    val w = maxOf(1, (sw + q - 1) / q); val h = maxOf(1, (sh + q - 1) / q)
    val src = IntArray(s.img.width * s.img.height) { s.img.getRGB(it % s.img.width, it / s.img.width) }
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) for (x in 0 until w) {
        var a = 0L; var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (dy in 0 until q) for (dx in 0 until q) {
            val ux = x * q + dx; val uy = y * q + dy
            n++
            if (ux >= sw || uy >= sh) continue
            val c = src[(uy / p) * s.img.width + ux / p]; val ca = (c ushr 24).toLong()
            a += ca; r += (c shr 16 and 255) * ca; g += (c shr 8 and 255) * ca; b += (c and 255) * ca
        }
        out.setRGB(x, y, if (a == 0L) 0 else ((a / n).toInt() shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt())
    }
    return Sprite(s.name, out, Math.round(s.ax * p.toDouble() / q).toInt(), Math.round(s.ay * p.toDouble() / q).toInt())
}

private fun half(s: Sprite): Sprite {
    val w = maxOf(1, (s.img.width + 1) / 2); val h = maxOf(1, (s.img.height + 1) / 2)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) for (x in 0 until w) {
        var a = 0; var r = 0; var g = 0; var b = 0
        for (dy in 0..1) for (dx in 0..1) {
            val sx = minOf(x * 2 + dx, s.img.width - 1); val sy = minOf(y * 2 + dy, s.img.height - 1)
            val c = s.img.getRGB(sx, sy); val ca = c ushr 24
            a += ca; r += (c shr 16 and 255) * ca; g += (c shr 8 and 255) * ca; b += (c and 255) * ca
        }
        out.setRGB(x, y, if (a == 0) 0 else ((a / 4) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (b / a))
    }
    return Sprite(s.name, out, (s.ax + 1) / 2, (s.ay + 1) / 2)
}

/** Наибольший размер страницы атласа (больше - отдельная страница) */
private const val MAX_PAGE = 4096

/**
 * Упаковка в страницы атласа: <name>.png, <name>_1.png...; здания (bld_*) - на своих страницах после остальных,
 * чтобы при отрисовке текстура менялась реже. В json у области: x, y, w, h, ax, ay, масштаб, страница.
 */
private fun pack(items: List<Packed>, dir: File, name: String, scale: Double, points: Map<String, Map<String, FloatArray>>) {
    dir.mkdirs()
    val sprites = items.map { it.s }
    val kOf = items.associate { it.s.name to it.k }
    dir.listFiles { f -> f.name.matches(Regex("${name}_[0-9]+[.]png")) }?.forEach { it.delete() }
    val pos = HashMap<String, IntArray>()   // x, y, страница
    val pages = ArrayList<Pair<Int, Int>>()
    // анимации зданий (anim_*) рисуются сразу после здания - на той же странице
    val onBuildingPage = { s: Sprite -> s.name.startsWith("bld_") || s.name.startsWith("anim_") }
    for (group in listOf(sprites.filter { !onBuildingPage(it) }, sprites.filter(onBuildingPage))) {
        var rest = group.sortedWith(compareByDescending<Sprite> { it.img.height }.thenBy { it.name })
        while (rest.isNotEmpty()) {
            val page = pages.size
            // самая узкая ширина, при которой страница не выше 2 x ширина; иначе MAX_PAGE и перенос остатка
            var width = 1024
            var placed: List<Sprite>
            var height: Int
            while (true) {
                var x = 0; var y = 0; var rowH = 0
                val ok = ArrayList<Sprite>()
                for (sp in rest) {
                    val w = sp.img.width + PAD * 2; val h = sp.img.height + PAD * 2
                    if (x + w > width) { x = 0; y += rowH; rowH = 0 }
                    if (y + h > MAX_PAGE) break
                    pos[sp.name] = intArrayOf(x + PAD, y + PAD, page)
                    ok += sp
                    x += w; rowH = maxOf(rowH, h)
                }
                height = 1
                while (height < y + rowH) height *= 2
                placed = ok
                if ((height <= width * 2 && ok.size == rest.size) || width >= MAX_PAGE) break
                width *= 2
            }
            pages += width to minOf(height, MAX_PAGE)
            val atlas = BufferedImage(width, minOf(height, MAX_PAGE), BufferedImage.TYPE_INT_ARGB)
            for (s0 in placed) {
                val sp = bleed(s0)
                val p = pos[sp.name]!!
                val w = sp.img.width; val h = sp.img.height
                for (yy in -PAD until h + PAD) for (xx in -PAD until w + PAD) {
                    val sx = xx.coerceIn(0, w - 1); val sy = yy.coerceIn(0, h - 1)
                    var c = sp.img.getRGB(sx, sy)
                    // в отступе - тот же цвет края, но прозрачный (кроме сплошных спрайтов вроде белого квадрата)
                    if ((xx != sx || yy != sy) && sp.name != "white" && !sp.name.startsWith("tile_") && !sp.name.startsWith("base_")) c = c and 0x00ffffff
                    atlas.setRGB(p[0] + xx, p[1] + yy, c)
                }
            }
            ImageIO.write(atlas, "png", File(dir, if (page == 0) "$name.png" else "${name}_$page.png"))
            rest = rest.drop(placed.size)
        }
    }
    val sb = StringBuilder("{\n  \"scale\": $scale,\n  \"pages\": [" + pages.indices.joinToString(", ") { "\"" + (if (it == 0) "$name.png" else "${name}_$it.png") + "\"" } + "],\n  \"regions\": {\n")
    val names = sprites.map { it.name }.sorted()
    val byName = sprites.associateBy { it.name }
    for ((i, n) in names.withIndex()) {
        val sp = byName[n]!!
        val p = pos[n]!!
        sb.append("    \"$n\": [${p[0]}, ${p[1]}, ${sp.img.width}, ${sp.img.height}, ${sp.ax}, ${sp.ay}, ${scale * kOf[n]!!}, ${p[2]}]")
        sb.append(if (i < names.size - 1) ",\n" else "\n")
    }
    sb.append("  },\n  \"points\": {\n")
    // точки спрайтов (единицы мира от привязки): трубы smoke*, ось крыльев hub
    val pnames = points.keys.filter { it in pos }.sorted()
    for ((i, n) in pnames.withIndex()) {
        sb.append("    \"$n\": {" + points[n]!!.entries.sortedBy { it.key }.joinToString(", ") { "\"${it.key}\": [${it.value[0]}, ${it.value[1]}]" } + "}")
        sb.append(if (i < pnames.size - 1) ",\n" else "\n")
    }
    sb.append("  }\n}\n")
    File(dir, "$name.json").writeText(sb.toString())
    println("$name: " + pages.joinToString { "${it.first}x${it.second}" } + ", ${sprites.size} sprites")
}
