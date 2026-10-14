package town.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import town.Hearthvale
import town.Storage
import town.data.Lang
import town.sim.MapSize
import town.sim.SaveCodec
import town.sim.Tune
import town.sim.World
import town.ui.onClick

/** Главное меню: продолжить, новая игра (песочница с размером карты или сценарий), загрузка слота, язык */
class TitleScreen(private val game: Hearthvale, settings: Boolean = false) : ScreenAdapter() {
    private val ui = game.ui
    private val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / game.scale }, game.batch)
    private val root = Table()
    private var size = MapSize.MEDIUM
    private val backdrop = town.render.Backdrop(game.sprites, game.content)
    /** Живой город из сохранения (assets/data/title_town.json) - заставка: та же графика, что в игре; нет его - деревня из атласа */
    private val scene: town.render.TitleScene? = town.render.TitleScene.load(game.content, game.sprites)

    init {
        root.setFillParent(true)
        stage.addActor(root)
        if (settings) showSettings() else showMain()
    }

    override fun show() {
        Gdx.input.inputProcessor = stage
        town.Sfx.updateMusic()
    }

    /** stacked - меню под вывеской (главное меню: оно короткое); длинные списки - рядом с вывеской */
    private fun page(stacked: Boolean = false): Table {
        // название сверху, в середине видна деревня-заставка, меню снизу
        // название и меню - на подложках: поверх пёстрой картины без них не читаются
        root.clear()
        verBoxActor?.remove(); verBoxActor = null
        // вывеска: резная доска из атласа (ui_logo) и подзаголовок на плашке; нет её - название текстом
        val logo = Table()
        val board = game.sprites.named("ui_logo")
        if (board != null) {
            val lw = minOf(LOGO_W, stage.width - 24f)
            logo.add(ui.image(board.r, lw, lw * board.r.regionHeight / board.r.regionWidth)).size(lw, lw * board.r.regionHeight / board.r.regionWidth).row()
            val tag = Table()
            tag.background = ui.panel
            tag.add(Label(Lang.t("tagline"), ui.labelMuted)).pad(2f, 14f, 4f, 14f)
            logo.add(tag).padTop(2f)
        } else {
            logo.background = ui.panel
            logo.add(Label(Lang.t("title"), ui.title)).pad(6f, 28f, 2f, 28f).row()
            logo.add(com.badlogic.gdx.scenes.scene2d.ui.Image(ui.rule)).height(1f).growX().padLeft(36f).padRight(36f).row()
            logo.add(Label(Lang.t("tagline"), ui.labelMuted)).pad(4f, 20f, 8f, 20f)
        }
        val col = Table()
        col.background = ui.panel
        // низкий экран (телефон лёжа): название слева, меню справа во всю высоту, кнопки плотнее
        short = stage.height < 600f && stage.width > stage.height
        if (short) {
            col.pad(8f, 12f, 2f, 12f)
            col.defaults().width(300f).padBottom(6f)
            val row = Table()
            row.add(logo).expandX().top().padTop(20f)
            row.add(col).expandX()
            root.add(row).expand().fill().row()
            return col
        }
        // широкий экран (ПК, планшет лёжа): вывеска и меню колонкой слева - середина картины открыта
        wide = stage.width > stage.height * 1.25f
        if (wide && !stacked) {
            col.pad(14f, 14f, 4f, 14f)
            col.defaults().width(MENU_W).padBottom(10f)
            val row = Table()
            row.add(logo).expandY().top().left().padTop(maxOf(24f, stage.height * 0.05f))
            row.add(col).expandY().left().padLeft(24f)
            row.add().expandX()
            root.add(row).expand().fill().padLeft(maxOf(24f, stage.width * 0.04f)).row()
            return col
        }
        if (wide) {
            col.pad(14f, 14f, 4f, 14f)
            col.defaults().width(300f).padBottom(10f)
            // вывеска вверху слева, меню рядом с ней по центру высоты: длинные списки (песочница) не упираются в край
            // вывеска и меню одной колонкой слева - живой город справа открыт (камера сдвинута, см. TitleScene.shiftX)
            val left = Table()
            left.add(logo).left().padTop(maxOf(24f, stage.height * 0.05f)).row()
            left.add(col).left().padTop(18f).padLeft((LOGO_W - MENU_W - 28f) / 2f).row()
            left.add().expandY()
            val row = Table()
            row.add(left).expandY().fillY().top().left()
            row.add().expandX()
            root.add(row).expand().fill().padLeft(maxOf(24f, stage.width * 0.04f)).row()
            return col
        }
        root.add(logo).padTop(28f).row()
        root.add().expandY().row()
        col.pad(14f, 14f, 4f, 14f)
        col.defaults().width(300f).padBottom(10f)
        root.add(col).padBottom(16f).row()
        return col
    }

    /**
     * Низкий экран: длинный список кнопок - в две колонки (последняя, «Назад», - во всю ширину внизу),
     * иначе нижние кнопки уходят за край
     */
    private fun twoColumns(col: Table) {
        if (!short || col.cells.size <= 6) return
        val items = col.cells.map { it.actor }
        col.clearChildren()
        col.defaults().width(250f).padBottom(6f)
        val last = items.size - 1
        for (i in 0 until last) {
            val c = col.add(items[i])
            if (i % 2 == 0) c.padRight(8f) else c.row()
        }
        if (last % 2 == 1) col.row()
        col.add(items[last]).colspan(2).width(250f).padTop(4f).row()
    }

    companion object {
        /** Ширина вывески и колонки меню (точки) */
        const val LOGO_W = 400f
        const val MENU_W = 300f
    }

    /** Экран низкий - раскладка в ряд (page) */
    private var short = false
    /** Экран широкий - вывеска и меню колонкой слева */
    private var wide = false
    /** Номер версии в углу (в ряду ему нет места) */
    private var verBoxActor: com.badlogic.gdx.scenes.scene2d.Actor? = null

    private fun showMain() {
        val col = page(stacked = true)
        // главное действие - янтарной кнопкой: продолжить, а без сохранения - новая игра
        val cont = usable("auto")
        if (cont) col.add(button(Lang.t("continue"), main = true) { load("auto") }).row()
        col.add(button(Lang.t("new_game"), main = !cont) { showNew() }).row()
        val today = town.todayDate()
        val best = Storage.dailyBest(today)
        val dayName = town.dateText(today).substring(0, 5)
        col.add(button(if (best >= 0) Lang.t("title_daily_best", dayName, best) else Lang.t("title_daily", dayName)) {
            game.play(World.createDaily(game.content, today))
        }).row()
        // второстепенное - сеткой 2x2 под главными: три крупных действия видны сразу, меню не закрывает город
        val more = Table()
        val half = MENU_W / 2f - 4f
        more.add(button(Lang.t("load")) { showLoad() }).width(half).padRight(8f)
        more.add(button(Lang.t("records")) { showRecords() }).width(half).row()
        more.add(button(Lang.t("achievements")) { showAchievements() }).width(half).padRight(8f).padTop(8f)
        more.add(button(Lang.t("settings")) { showSettings() }).width(half).padTop(8f).row()
        col.add(more).padTop(6f).row()
        val ver = Label(town.Hearthvale.VERSION, ui.labelSmall)
        ver.color = com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0.7f)
        val verBox = Table()
        verBox.background = ui.panel
        verBox.add(ver).pad(2f, 8f, 2f, 8f)
        if (short || wide) { verBox.setPosition(8f, 8f); verBox.pack(); stage.addActor(verBox); verBoxActor?.remove(); verBoxActor = verBox }
        else { verBoxActor?.remove(); verBoxActor = null; root.add(verBox).padBottom(8f).row() }
    }

    /** Настройки: язык, звуки, музыка */
    private fun showSettings() {
        val col = page()
        col.add(button(Lang.t("language")) { game.toggleLanguage(); showSettings() }).row()
        col.add(town.ui.volumeRow(ui, "vol_music", { town.Storage.musicVol }) { town.Storage.musicVol = it; town.Sfx.updateMusic() }).width(280f).row()
        col.add(town.ui.volumeRow(ui, "vol_sound", { town.Storage.soundVol }) { town.Storage.soundVol = it }).width(280f).row()
        col.add(button(Lang.t(if (town.Storage.colorblind) "cb_on" else "cb_off")) { town.Storage.colorblind = !town.Storage.colorblind; showSettings() }).row()
        col.add(button(Lang.t("gfx_" + town.Storage.graphics)) { cycleGraphics(); showSettings() }).row()
        col.add(button(Lang.t(if (town.Storage.bigText) "big_text_on" else "big_text_off")) { game.toggleBigText() }).row()
        col.add(button(Lang.t("about")) { showAbout() }).row()
        col.add(button(Lang.t("back")) { showMain() }).padTop(10f).row()
    }

    /** Графика по кругу: Авто -> Красиво -> Быстро */
    private fun cycleGraphics() {
        town.Storage.graphics = when (town.Storage.graphics) { "auto" -> "high"; "high" -> "low"; else -> "auto" }
        town.Storage.autoLow = false
        game.applyGraphics()
    }

    fun debugOverflow(): List<String> = town.ui.Overflow.check(stage)

    /** Для съёмки экранов (desktop Shots) */
    fun debugAbout() = showAbout()
    fun debugCampaign() = showCampaign()
    fun debugSandbox() = showSandbox()

    /** Об игре: версия, ссылка и лицензии (assets/licenses/NOTICE.txt) */
    private fun showAbout() {
        val col = page()
        val w = minOf(560f, stage.width - 32f)
        val box = Table()
        box.background = ui.panel
        box.add(Label("Hearthvale ${town.Hearthvale.VERSION}", ui.labelBold)).left().pad(10f, 10f, 4f, 10f).row()
        val about = Label(Lang.t("about_text"), ui.label)
        about.setWrap(true)
        box.add(about).width(w - 20f).padLeft(10f).padRight(10f).padBottom(8f).row()
        val notice = Label(Gdx.files.internal("licenses/NOTICE.txt").readString("UTF-8"), ui.labelSmall)
        notice.setWrap(true)
        val pane = ScrollPane(notice)
        pane.setScrollingDisabled(true, false)
        pane.setOverscroll(false, false)
        box.add(pane).width(w - 20f).height(stage.height * 0.42f).padLeft(10f).padRight(10f).padBottom(10f).row()
        col.add(box).width(w).row()
        col.add(button(Lang.t("back")) { showSettings() }).padTop(10f).row()
    }

    /** Рекорды партии дня за последние 7 дат */
    private fun showRecords() {
        val col = page()
        col.add(note(Lang.t("title_daily_desc"))).padBottom(8f).row()
        val hist = Storage.dailyHistory()
        if (hist.isEmpty()) col.add(note(Lang.t("records_empty"))).row()
        for ((date, score) in hist) col.add(note(Lang.t("records_row", town.dateText(date), score))).padBottom(4f).row()
        col.add(button(Lang.t("back")) { showMain() }).padTop(10f).row()
    }

    /** Достижения: открытые - золотом, закрытые - приглушённо с условием */
    private fun showAchievements() {
        val col = page()
        val w = minOf(460f, stage.width - 32f)
        val box = Table()
        box.background = ui.panel
        val got = town.Storage.achievements
        val ids = town.Achievements.ids(game.content)
        box.add(Label(Lang.t("achievements") + " - " + ids.count { it in got } + " / " + ids.size, ui.labelHead)).left().pad(8f).row()
        val list = Table()
        for (id in ids) {
            val open = id in got
            val row = Table()
            row.background = ui.chip
            val sc = if (id.startsWith("win_")) game.content.scenarios.firstOrNull { "win_" + it.id == id } else null
            val name = if (sc != null) Lang.t("ach_win", Lang.t(sc.name)) else Lang.t("ach_$id")
            val desc = if (sc != null) Lang.t("ach_win_desc") else Lang.t("ach_${id}_d")
            val l = Label((if (open) "[#F2C35A]" else "[#9A8E7E]") + name + "[]\n" + desc, ui.label)
            l.setWrap(true)
            row.add(l).width(w - 36f).left().pad(6f)
            list.add(row).width(w - 16f).padBottom(3f).row()
        }
        val pane = ScrollPane(list)
        pane.setScrollingDisabled(true, false)
        pane.setOverscroll(false, false)
        box.add(pane).width(w - 16f).height(stage.height * 0.55f).pad(0f, 8f, 8f, 8f).row()
        col.add(box).width(w).row()
        col.add(button(Lang.t("back")) { showMain() }).padTop(10f).row()
    }

    /** Выбор режима: песочница или один из сценариев (первый - обучение) */
    private fun showNew() {
        val col = page()
        col.add(button(Lang.t("campaign"), main = true) { showCampaign() }).row()
        col.add(button(Lang.t("sandbox")) { showSandbox() }).row()
        col.add(button(Lang.t("back")) { showMain() }).padTop(10f).row()
    }

    /** Сценарий i кампании открыт: первый всегда, дальше - когда пройден предыдущий */
    private fun campaignOpen(i: Int): Boolean {
        if (i == 0) return true
        val prev = game.content.scenarios[i - 1].id
        return Storage.stars(prev) > 0 || ("win_$prev") in Storage.achievements
    }

    /** Звёзды сценария (победа до звёзд, из достижений - одна) */
    private fun starsOf(id: String) = maxOf(Storage.stars(id), if (("win_$id") in Storage.achievements) 1 else 0)

    private var campSel = -1

    /** Кампания: карта королевства, сценарии по порядку дорогой, у каждого звёзды; выбранный - описание и «Начать» */
    private fun showCampaign() {
        val col = page()
        val scs = game.content.scenarios
        if (campSel !in scs.indices || !campaignOpen(campSel)) campSel = scs.indices.lastOrNull { campaignOpen(it) } ?: 0
        val mw = minOf(if (short) 420f else 520f, stage.width - 32f)
        val mh = minOf(mw * 0.62f, stage.height * (if (short) 0.62f else 0.42f))
        val map = KingdomMap(mw, mh)
        col.add(map).width(mw).height(mh).row()
        val sc = scs[campSel]
        val info = Table()
        info.background = ui.panel
        val head = Table()
        head.add(Label(Lang.t(sc.name), ui.labelBold)).left().expandX()
        for (k in 0 until 3) game.sprites.named("hud_star")?.let { r -> val im = ui.image(r.r, 18f); if (k >= starsOf(sc.id)) im.color = com.badlogic.gdx.graphics.Color(0.3f, 0.3f, 0.3f, 0.7f); head.add(im).size(18f) }
        info.add(head).width(mw - 20f).pad(6f, 10f, 2f, 10f).row()
        val d = Label(Lang.t(sc.desc), ui.labelSmall)
        d.setWrap(true)
        info.add(d).width(mw - 20f).pad(0f, 10f, 8f, 10f).row()
        col.add(info).width(mw).row()
        val row = Table()
        row.add(button(Lang.t("start"), main = true) { game.play(World.create(game.content, sc)) }).width(150f).padRight(8f)
        row.add(button(Lang.t("back")) { showNew() }).width(150f)
        col.add(row).width(mw).row()
    }

    /**
     * Карта королевства: пергамент, река, дорога точками от сценария к сценарию (места - поле map в scenarios.json),
     * пройденные - со звёздами, закрытые - тусклые с замком. Касание - выбрать
     */
    private inner class KingdomMap(val mw: Float, val mh: Float) : com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup() {
        private val parchment = game.sprites.named("ui_chip")?.let { com.badlogic.gdx.graphics.g2d.NinePatch(it.r, 16, 16, 16, 16) }
        init {
            setSize(mw, mh)
            val scs = game.content.scenarios
            for ((i, sc) in scs.withIndex()) {
                val open = campaignOpen(i)
                val node = Table()
                node.background = if (i == campSel) ui.buttonMain.up else ui.button.up
                node.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
                val num = Label((i + 1).toString(), ui.labelBold)
                if (!open) num.color = com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0.45f)
                node.add(num).padBottom(0f).row()
                val stars = Table()
                if (open) for (k in 0 until 3) game.sprites.named("hud_star")?.let { r -> val im = ui.image(r.r, 11f); if (k >= starsOf(sc.id)) im.color = com.badlogic.gdx.graphics.Color(0.2f, 0.2f, 0.2f, 0.6f); stars.add(im).size(11f) }
                else game.sprites.named("hud_lock")?.let { r -> stars.add(ui.image(r.r, 14f)).size(14f) }
                node.add(stars)
                val s = 48f
                node.setSize(s, s)
                node.setPosition(sc.mapX / 100f * (mw - s), (1f - sc.mapY / 100f) * (mh - s))
                node.onClick { if (open) { campSel = i; showCampaign() } }
                addActor(node)
            }
        }

        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            val dot = game.sprites.puff?.r
            // пергамент: белая заготовка-«девятка» из атласа, окрашенная
            parchment.let { bg ->
                val c = batch.color.cpy()
                batch.setColor(0.88f, 0.8f, 0.62f, parentAlpha)
                bg?.draw(batch, x, y, width, height)
                // река волной сверху вниз
                if (dot != null) {
                    batch.setColor(0.3f, 0.52f, 0.82f, 0.9f * parentAlpha)
                    var t = 0f
                    while (t <= 1f) {
                        val rx = x + width * (0.42f + 0.12f * kotlin.math.sin(t * 7f))
                        batch.draw(dot, rx - 7f, y + t * height - 7f, 14f, 14f)
                        t += 0.012f
                    }
                    // леса и холмы пятнами
                    for (k in 0 until 14) {
                        val fx = ((k * 37) % 100) / 100f; val fy = ((k * 61 + 13) % 100) / 100f
                        batch.setColor(if (k % 3 == 0) 0.62f else 0.45f, if (k % 3 == 0) 0.58f else 0.6f, 0.38f, 0.5f * parentAlpha)
                        batch.draw(dot, x + fx * width - 16f, y + fy * height - 10f, 32f, 20f)
                    }
                    // дорога точками между сценариями
                    val scs = game.content.scenarios
                    val s = 48f
                    for (i in 1 until scs.size) {
                        val ax = x + scs[i - 1].mapX / 100f * (width - s) + s / 2; val ay = y + (1f - scs[i - 1].mapY / 100f) * (height - s) + s / 2
                        val bx = x + scs[i].mapX / 100f * (width - s) + s / 2; val by = y + (1f - scs[i].mapY / 100f) * (height - s) + s / 2
                        val open = campaignOpen(i)
                        batch.setColor(0.42f, 0.28f, 0.16f, (if (open) 0.85f else 0.35f) * parentAlpha)
                        val n = 10
                        for (k in 1 until n) {
                            val q = k.toFloat() / n
                            batch.draw(dot, ax + (bx - ax) * q - 3f, ay + (by - ay) * q - 3f, 6f, 6f)
                        }
                    }
                }
                batch.color = c
            }
            super.draw(batch, parentAlpha)
        }
    }

    private fun showSandbox() {
        val col = page()
        col.add(note(Lang.t("sandbox_desc"))).padBottom(12f).row()
        col.add(Label(Lang.t("map_size"), ui.labelBold)).row()
        // размер карты - одной строкой из трёх переключателей
        val sizes = listOf(MapSize.SMALL to "size_small", MapSize.MEDIUM to "size_medium", MapSize.LARGE to "size_large")
        val buttons = ArrayList<TextButton>()
        val sizeRow = Table()
        for ((s, key) in sizes) {
            val b = TextButton(Lang.t(key), ui.toggle)
            b.isChecked = s == size
            b.onClick { size = s; for (o in buttons) o.isChecked = o == b }
            buttons.add(b)
            sizeRow.add(b).width(97f).height(44f).pad(0f, 1f, 0f, 1f)
        }
        col.add(sizeRow).row()
        // настройки: по кругу «мало / обычно / много»
        fun cycle(key: String, get: () -> Int, set: (Int) -> Unit) {
            val b = TextButton("", ui.button)
            fun label() = Lang.t(key, Lang.t("sb_level_${get()}"))
            b.setText(label())
            b.onClick { set((get() + 1) % 3); b.setText(label()) }
            col.add(b).row()
        }
        cycle("sb_rich", { Storage.sbRich }) { Storage.sbRich = it }
        cycle("sb_stock", { Storage.sbStock }) { Storage.sbStock = it }
        cycle("sb_disasters", { Storage.sbDisasters }) { Storage.sbDisasters = it }
        // угрозы (волки, разбойники) - можно выключить для спокойной стройки; спокойный режим выключает и их, и уход жителей
        val threats = TextButton(Lang.t(if (town.Storage.threats) "threats_on" else "threats_off"), ui.toggle)
        threats.isChecked = town.Storage.threats
        threats.onClick { town.Storage.threats = threats.isChecked; threats.setText(Lang.t(if (threats.isChecked) "threats_on" else "threats_off")) }
        val calm = TextButton(Lang.t(if (Storage.sbCalm) "calm_on" else "calm_off"), ui.toggle)
        calm.isChecked = Storage.sbCalm
        threats.isDisabled = Storage.sbCalm
        calm.onClick { Storage.sbCalm = calm.isChecked; calm.setText(Lang.t(if (calm.isChecked) "calm_on" else "calm_off")); threats.isDisabled = calm.isChecked }
        col.add(calm).row()
        col.add(threats).row()
        // своя карта: код друга (сид, размер и условия) или любое слово - сид; пусто - случайная долина
        val custom = button(customText?.let { Lang.t("seed_set", it) } ?: Lang.t("seed_btn")) { askSeed() }
        custom.label.setWrap(true)
        col.add(custom).row()
        col.add(button(Lang.t("start"), main = true) {
            val set = town.sim.SandboxSettings(Storage.sbRich, Storage.sbStock, Storage.sbDisasters, Storage.sbCalm)
            game.play(World.create(game.content, customSeed ?: TimeUtils.millis().toInt(), size, town.Storage.threats, set))
        }).padTop(6f).row()
        col.add(button(Lang.t("back")) { showNew() }).row()
        twoColumns(col)
    }

    /** Сид своей карты (null - случайная) и как его показать (код или слово) */
    private var customSeed: Int? = null
    private var customText: String? = null

    /** Ввод кода карты или слова (системное поле: в браузере на телефоне - с экранной клавиатурой) */
    private fun askSeed() {
        Gdx.input.getTextInput(object : com.badlogic.gdx.Input.TextInputListener {
            override fun input(text: String?) {
                Gdx.app.postRunnable {
                    val t = text?.trim() ?: ""
                    val code = town.sim.MapCode.decode(t)
                    when {
                        t.isEmpty() -> { customSeed = null; customText = null }
                        code != null -> {
                            // код друга: та же карта и те же условия
                            customSeed = code.seed; customText = town.sim.MapCode.encode(code)
                            size = code.size
                            Storage.sbRich = code.settings.rich; Storage.sbStock = code.settings.stock; Storage.sbDisasters = code.settings.disasters
                            Storage.sbCalm = code.settings.calm; Storage.threats = code.threats || code.settings.calm && Storage.threats
                        }
                        else -> { customSeed = town.sim.MapCode.seedOf(t); customText = "«" + t.take(24) + "»" }
                    }
                    showSandbox()
                }
            }
            override fun canceled() {}
        }, Lang.t("seed_title"), customText?.removePrefix("«")?.removeSuffix("»") ?: "", Lang.t("seed_hint"))
    }

    private var loadNote: String? = null

    private fun showLoad() {
        val col = page()
        loadNote?.let { col.add(note(it)).row(); loadNote = null }
        for (slot in Storage.SLOTS) {
            val data = Storage.load(slot)?.takeIf { SaveCodec.compatible(it) }
            val name = if (slot == "auto") Lang.t("autosave") else Lang.t("slot", slot)
            // название слота и под ним, что в нём, - в две строки: длинное описание не вылезает за кнопку
            val text = if (data == null) "$name — ${Lang.t("slot_empty")}" else "$name\n${describe(data)}"
            val b = button(text) { load(slot) }
            b.label.setWrap(true)
            b.isDisabled = data == null
            col.add(b).row()
        }
        // из файла (сохранённого кнопкой «Сохранить в файл»)
        game.saveFiles?.let { files ->
            col.add(button(Lang.t("load_file")) {
                files.import { text ->
                    val world = try { if (text != null && SaveCodec.compatible(text)) SaveCodec.read(game.content, text) else null } catch (e: Exception) { null }
                    if (world != null) game.play(world) else if (text != null) { loadNote = Lang.t("load_file_bad"); showLoad() }
                }
            }).padTop(6f).row()
        }
        if (game.clipboardCodes) {
            col.add(button(Lang.t("paste_code")) {
                val text = town.SaveCode.decode(Gdx.app.clipboard.contents ?: "")
                val world = try { if (text != null && SaveCodec.compatible(text)) SaveCodec.read(game.content, text) else null } catch (e: Exception) { null }
                if (world != null) game.play(world) else { loadNote = Lang.t("code_bad"); showLoad() }
            }).padTop(6f).row()
        }
        col.add(button(Lang.t("back")) { showMain() }).padTop(10f).row()
    }

    private fun describe(data: String): String = try {
        val s = SaveCodec.summary(data)
        Lang.t("slot_info", s[0], s[1] / Tune.TICKS_PER_DAY + 1, s[2])
    } catch (e: Exception) {
        "?"
    }

    /** Есть ли в слоте сохранение текущего формата (старые форматы до выпуска 0.1 не поддерживаются) */
    private fun usable(slot: String) = Storage.load(slot)?.let { SaveCodec.compatible(it) } ?: false

    private fun load(slot: String) {
        val data = Storage.load(slot) ?: return
        val world = try {
            SaveCodec.read(game.content, data)
        } catch (e: Exception) {
            Gdx.app.error("save", "cannot load slot $slot", e)
            showMain()
            return
        }
        game.play(world)
    }

    /** Пояснение на подложке (поверх заставки текст без фона не читается) */
    private fun note(text: String): Table {
        val t = Table()
        t.background = ui.panel
        val l = Label(text, ui.label)
        l.setWrap(true)
        t.add(l).width(280f).pad(10f)
        return t
    }

    private fun button(text: String, main: Boolean = false, action: () -> Unit): TextButton {
        val b = TextButton(text, if (main) ui.buttonMain else ui.button)
        b.onClick { if (!b.isDisabled) action() }
        return b
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.12f, 0.16f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val s = game.layoutScale(Gdx.graphics.width, Gdx.graphics.height)
        if (scene != null) {
            scene.shiftX = if (wide) 0.2f else 0f
            scene.draw(game.batch, Gdx.graphics.width / s, Gdx.graphics.height / s, delta)
        } else backdrop.draw(game.batch, Gdx.graphics.width / s, Gdx.graphics.height / s)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        (stage.viewport as ScreenViewport).unitsPerPixel = 1f / game.layoutScale(width, height)
        stage.viewport.update(width, height, true)
        // повернули телефон: раскладка (в ряд или столбиком) другая - меню заново
        if ((stage.height < 600f && stage.width > stage.height) != short) showMain()
    }

    override fun dispose() {
        stage.dispose()
    }
}
