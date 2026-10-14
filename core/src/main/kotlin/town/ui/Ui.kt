package town.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/**
 * Шрифты (PT Sans через FreeType, с кириллицей) и стили интерфейса, собранные в коде:
 * загрузка скина из JSON идёт через рефлексию, которой в TeaVM почти нет.
 * scale - во сколько раз физических пикселей больше «точки» интерфейса (плотность экрана).
 */
class Ui(white: TextureRegion, val scale: Float, private val lookup: (String) -> TextureRegion? = { null }, textScale: Float = 1f) {
    private lateinit var base: TextureRegionDrawable

    /** Скруглённая рамка из заготовки атласа (ui_btn, ui_panel, ui_chip) и её цвет - для смены атласа */
    private class Framed(val d: com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable, val name: String, val color: Color)
    private val framed = ArrayList<Framed>()

    /** Заготовка 24x24 точки, углы - треть стороны; в любом атласе (полном или лёгком) углы одного размера в точках */
    private fun patch(r: TextureRegion, c: Color): com.badlogic.gdx.graphics.g2d.NinePatch {
        val px = r.regionWidth
        val split = Math.round(px / 3f)
        val p = com.badlogic.gdx.graphics.g2d.NinePatch(r, split, split, split, split)
        val k = FRAME_PTS / px
        p.scale(k, k)
        p.color = c
        return p
    }

    /** Рамка с цветом c из заготовки name; нет её в атласе (старый атлас) - плоская заливка */
    private fun frame(name: String, c: Color): Drawable {
        val r = lookup(name) ?: return tint(c)
        val d = com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(patch(r, c))
        framed.add(Framed(d, name, Color(c)))
        return d
    }
    /** Окрашенные копии белой области: при смене атласа (Sprites.setLow) их надо перенацелить */
    private val tinted = ArrayList<com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable>()

    private fun tint(c: Color): Drawable = (base.tint(c) as com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable).also { tinted.add(it) }

    /** Атлас сменился: окрашенные фоны берут белую область из нового (цвет остаётся) */
    fun retarget(white: TextureRegion) {
        for (d in tinted) d.sprite.setRegion(white)
        // рамки - из заготовки нового атласа, отступы и наименьший размер остаются прежними
        for (f in framed) {
            val r = lookup(f.name) ?: continue
            val l = f.d.leftWidth; val rr = f.d.rightWidth; val t = f.d.topHeight; val b = f.d.bottomHeight
            val mw = f.d.minWidth; val mh = f.d.minHeight
            f.d.patch = patch(r, f.color)
            f.d.leftWidth = l; f.d.rightWidth = rr; f.d.topHeight = t; f.d.bottomHeight = b
            f.d.minWidth = mw; f.d.minHeight = mh
        }
    }

    val font: BitmapFont
    val fontBold: BitmapFont
    val fontBig: BitmapFont
    val fontSmall: BitmapFont

    val label: Label.LabelStyle
    val labelBold: Label.LabelStyle
    val title: Label.LabelStyle
    val labelSmall: Label.LabelStyle
    val labelWarn: Label.LabelStyle
    val button: TextButton.TextButtonStyle
    val toggle: TextButton.TextButtonStyle
    /** Узкая кнопка 44x44 для коротких надписей («×10») */
    val buttonTight: TextButton.TextButtonStyle
    /** Главное действие (янтарная: «Строить», «Продолжить») и опасное (красная: «Снести») */
    val buttonMain: TextButton.TextButtonStyle
    val buttonDanger: TextButton.TextButtonStyle
    /** Окно и плашка поверх карты: скруглённые, со светлой рамкой */
    val panel: Drawable
    /** Полоса во всю ширину экрана (верх, низ): ровная, без скруглений */
    val bar: Drawable
    val dim: Drawable
    /** Фон карточки постройки: обычный, выбранный, закрытый */
    val card: Drawable
    val cardOn: Drawable
    val cardLocked: Drawable
    /** Фон «фишки» на верхней панели (значок + число) */
    val chip: Drawable
    /** Окно поверх карты: непрозрачное (сквозь полупрозрачное просвечивали задания) */
    val window: Drawable
    /** Полоса через строку в таблицах (статистика) */
    val rowAlt: Drawable
    /** Подписи в таблицах - приглушённым цветом, заголовки разделов - золотом */
    val labelMuted: Label.LabelStyle
    val labelHead: Label.LabelStyle
    /** Заголовок окна - крупнее текста (жирный 22) */
    val fontTitle: BitmapFont
    val labelTitle: Label.LabelStyle
    /** Тонкая золотая линия (под заголовком окна) */
    val rule: Drawable
    /** Прокрутка с видимым ползунком справа: видно, что панель листается */
    val scroll: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle
    /** Кнопка-значок 44x44: фон кнопки и картинка поверх */
    private val iconUp: Drawable
    private val iconDown: Drawable
    private val iconOn: Drawable

    companion object {
        const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя№«»—–…·×−"

        val PANEL = Color(0.16f, 0.13f, 0.10f, 0.88f)
        val BUTTON = Color(0.42f, 0.30f, 0.18f, 1f)
        val BUTTON_DOWN = Color(0.30f, 0.21f, 0.12f, 1f)
        val BUTTON_ON = Color(0.70f, 0.52f, 0.22f, 1f)
        val TEXT = Color(0.97f, 0.93f, 0.84f, 1f)
        /** Текст на янтарной кнопке - тёмный (светлый на ней плохо читается) */
        val TEXT_DARK = Color(0.22f, 0.14f, 0.06f, 1f)
        // цвета рамок (заготовки серые: кнопка светлее кверху, у панели середина темнее рамки)
        val WOOD = Color(0.58f, 0.42f, 0.27f, 1f)
        val WOOD_DOWN = Color(0.43f, 0.31f, 0.20f, 1f)
        val AMBER = Color(0.95f, 0.72f, 0.34f, 1f)
        val AMBER_DOWN = Color(0.76f, 0.56f, 0.26f, 1f)
        val RED = Color(0.74f, 0.34f, 0.27f, 1f)
        val RED_DOWN = Color(0.56f, 0.25f, 0.20f, 1f)
        val EDGE = Color(0.46f, 0.37f, 0.25f, 0.95f)
        /** Сторона заготовки рамки в точках */
        const val FRAME_PTS = 24f
        val GOLD = Color(1f, 0.85f, 0.45f, 1f)
        /** Недоступная кнопка: серо-бурая рамка и приглушённый текст */
        val DISABLED = Color(0.31f, 0.28f, 0.25f, 1f)
        val DISABLED_TEXT = Color(0.56f, 0.51f, 0.45f, 1f)
        /** Настройка «Крупный текст»: обычный и мелкий шрифт крупнее во столько раз */
        const val BIG_TEXT = 1.18f
    }

    init {
        // «Крупный текст» - только обычный и мелкий шрифт (тексты панелей и окон); жирный - в верхней панели
        // и на кнопках фиксированной ширины, он прежний, чтобы раскладка не плыла
        font = generate("fonts/PT_Sans-Web-Regular.ttf", Math.round(17 * textScale))
        fontBold = generate("fonts/PT_Sans-Web-Bold.ttf", 17)
        fontBig = generate("fonts/PT_Sans-Web-Bold.ttf", 40)
        fontSmall = generate("fonts/PT_Sans-Web-Regular.ttf", Math.round(13 * textScale))
        fontTitle = generate("fonts/PT_Sans-Web-Bold.ttf", 22)
        // цветные вставки [#rrggbb]...[] в обычном шрифте (предупреждения в заданиях); скобок «[» в текстах нет
        font.data.markupEnabled = true

        base = TextureRegionDrawable(white)
        bar = tint(Color(PANEL.r, PANEL.g, PANEL.b, 0.94f))
        panel = frame("ui_panel", EDGE)
        dim = tint(Color(0f, 0f, 0f, 0.55f))
        label = Label.LabelStyle(font, TEXT)
        labelBold = Label.LabelStyle(fontBold, TEXT)
        title = Label.LabelStyle(fontBig, GOLD)
        labelSmall = Label.LabelStyle(fontSmall, TEXT)
        labelWarn = Label.LabelStyle(fontBold, Color(1f, 0.55f, 0.4f, 1f))
        card = frame("ui_btn", WOOD)
        cardOn = frame("ui_btn", AMBER)
        cardLocked = frame("ui_btn", Color(0.34f, 0.29f, 0.24f, 1f))
        chip = frame("ui_chip", Color(0f, 0f, 0f, 0.28f))
        window = frame("ui_panel", Color(EDGE.r, EDGE.g, EDGE.b, 1f))
        // окна и плашки: отступ внутри рамки, чтобы содержимое не ложилось на кромку
        for (d in listOf(panel, window)) { d.leftWidth = 5f; d.rightWidth = 5f; d.topHeight = 5f; d.bottomHeight = 5f }
        for (d in listOf(card, cardOn, cardLocked, chip)) { d.leftWidth = 0f; d.rightWidth = 0f; d.topHeight = 0f; d.bottomHeight = 0f; d.minWidth = 0f; d.minHeight = 0f }
        rowAlt = tint(Color(1f, 1f, 1f, 0.06f))
        labelMuted = Label.LabelStyle(font, Color(0.80f, 0.74f, 0.64f, 1f))
        labelHead = Label.LabelStyle(fontBold, GOLD)
        labelTitle = Label.LabelStyle(fontTitle, GOLD)
        rule = tint(Color(GOLD.r, GOLD.g, GOLD.b, 0.35f)).also { it.minHeight = 1f }
        scroll = com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle()
        scroll.vScrollKnob = tint(Color(GOLD.r, GOLD.g, GOLD.b, 0.55f)).also { it.minWidth = 4f; it.minHeight = 16f }
        fun sized(d: Drawable): Drawable { d.minWidth = 44f; d.minHeight = 44f; return d }
        fun bare(d: Drawable): Drawable { d.leftWidth = 0f; d.rightWidth = 0f; d.topHeight = 0f; d.bottomHeight = 0f; return d }
        iconUp = sized(bare(frame("ui_btn", WOOD))); iconDown = sized(bare(frame("ui_btn", WOOD_DOWN))); iconOn = sized(bare(frame("ui_btn", AMBER)))

        fun padded(d: Drawable): Drawable {
            d.leftWidth = 14f; d.rightWidth = 14f; d.topHeight = 9f; d.bottomHeight = 9f
            d.minWidth = 44f; d.minHeight = 44f   // палец: не меньше 44 точек
            return d
        }
        button = TextButton.TextButtonStyle(padded(frame("ui_btn", WOOD)), padded(frame("ui_btn", WOOD_DOWN)), null, fontBold)
        button.fontColor = TEXT
        // недоступная - серая и плоская на вид (тёмная, текст приглушён), а не «чуть темнее обычной»
        button.disabledFontColor = DISABLED_TEXT
        button.disabled = padded(frame("ui_btn", DISABLED))
        toggle = TextButton.TextButtonStyle(padded(frame("ui_btn", WOOD)), padded(frame("ui_btn", WOOD_DOWN)), padded(frame("ui_btn", AMBER)), fontBold)
        toggle.fontColor = TEXT
        toggle.checkedFontColor = TEXT_DARK
        fun tight(d: Drawable): Drawable { val t = padded(d); t.leftWidth = 3f; t.rightWidth = 3f; return t }
        buttonTight = TextButton.TextButtonStyle(tight(frame("ui_btn", WOOD)), tight(frame("ui_btn", WOOD_DOWN)), null, fontBold)
        buttonTight.fontColor = TEXT
        buttonTight.disabledFontColor = button.disabledFontColor
        buttonTight.disabled = tight(frame("ui_btn", DISABLED))
        buttonMain = TextButton.TextButtonStyle(padded(frame("ui_btn", AMBER)), padded(frame("ui_btn", AMBER_DOWN)), null, fontBold)
        buttonMain.fontColor = TEXT_DARK
        buttonMain.disabledFontColor = button.disabledFontColor
        buttonMain.disabled = button.disabled
        buttonDanger = TextButton.TextButtonStyle(padded(frame("ui_btn", RED)), padded(frame("ui_btn", RED_DOWN)), null, fontBold)
        buttonDanger.fontColor = TEXT
    }

    /** Кнопка с картинкой 44x44 точки (картинка - область атласа, size - её размер в точках) */
    fun iconButton(region: com.badlogic.gdx.graphics.g2d.TextureRegion, size: Float = 24f, toggle: Boolean = false): com.badlogic.gdx.scenes.scene2d.ui.ImageButton {
        val img = TextureRegionDrawable(region)
        img.setMinSize(size, size)
        val st = com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle(iconUp, iconDown, if (toggle) iconOn else null, img, null, null)
        return com.badlogic.gdx.scenes.scene2d.ui.ImageButton(st)
    }

    /** Картинка из атласа заданного размера в точках */
    fun image(region: com.badlogic.gdx.graphics.g2d.TextureRegion, w: Float, h: Float = w): com.badlogic.gdx.scenes.scene2d.ui.Image {
        val i = com.badlogic.gdx.scenes.scene2d.ui.Image(TextureRegionDrawable(region), com.badlogic.gdx.utils.Scaling.fit)
        i.setSize(w, h)
        return i
    }

    /** Шрифт рисуется в физических пикселях (чёткий), а в интерфейсе масштабируется обратно в точки */
    private fun generate(file: String, size: Int): BitmapFont {
        val gen = FreeTypeFontGenerator(Gdx.files.internal(file))
        val p = FreeTypeFontGenerator.FreeTypeFontParameter()
        p.size = Math.round(size * scale)
        p.characters = CHARS
        p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
        p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
        val f = gen.generateFont(p)
        gen.dispose()
        f.data.setScale(1f / scale)
        f.setUseIntegerPositions(false)
        return f
    }

    fun dispose() {
        font.dispose(); fontBold.dispose(); fontBig.dispose(); fontSmall.dispose(); fontTitle.dispose()
    }
}
