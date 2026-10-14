package town.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import town.Hearthvale
import town.Sfx
import town.Storage
import town.data.Lang
import town.render.Palette
import town.render.WorldRenderer
import town.sim.*
import town.ui.onClick

/**
 * Игровой экран. Раскладка (пересобирается при повороте и смене языка; размеры фиксированы, чтобы ничего не «плыло»):
 * - сверху одна строка значков: меню, день/сезон, довольство, золото, жители, торговец (когда пришёл), пауза, скорость;
 *   под ней полоса ресурсов в одну строку (листается вбок). Учитываются вырезы экрана (safe insets);
 * - задания - свёрнутая строка под панелью, касание раскрывает список с подсказками;
 * - карточки построек с картинками (закрытые - с замком и уровнем): снизу в портрете, колонкой слева в ландшафте;
 * - панель выбранного здания или постройки: над карточками в портрете, справа в ландшафте; высота ограничена.
 * Карта: перемещение пальцем/мышью, масштаб щипком/колесом, касание - выбор, постройка или дорога.
 */
class GameScreen(private val game: Hearthvale, private var world: World) : ScreenAdapter() {
    private val ui = game.ui
    private val sprites = game.sprites
    /** Точек на пиксель для текущего размера окна (меняется в resize) */
    private var scale = game.scale
    private val renderer = WorldRenderer(world, game.sprites)
    private val cam = renderer.camera
    private val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / scale }, game.batch)

    private val speeds = intArrayOf(1, 3, 10)
    private var speedIdx = 0
    private var statAt = 0L
    /** Партия (новая и загруженная) начинается на паузе: осмотреться и спланировать */
    private var paused = true
    private var acc = 0f

    // режим строительства: выбранный тип здания (ROAD_MODE - дорога) и место предпросмотра
    private var buildDef = -1
    private var ghostX = -1
    private var ghostY = -1
    /** Дорога: начало и конец отрезка (клетки) или -1; режим «убирать дорогу» */
    private var roadStart = -1
    private var roadEnd = -1
    private var roadRemove = false

    private var autosaveAt = TimeUtils.millis() + AUTOSAVE_MS
    private var toastUntil = 0L
    private var toastCell: com.badlogic.gdx.scenes.scene2d.ui.Cell<Table>? = null
    private var topBarT: Table? = null
    private var debug = false

    // ---- элементы интерфейса (пересоздаются в buildHud) ----
    private lateinit var seasonImg: Image
    private lateinit var weatherImg: Image
    private lateinit var dayLbl: Label
    private lateinit var happyLbl: Label
    private lateinit var goldLbl: Label
    private lateinit var popLbl: Label
    private lateinit var traderHolder: Container<Actor>
    private lateinit var traderBtn: ImageButton
    private lateinit var pauseBtn: ImageButton
    private lateinit var speedBtn: TextButton
    private lateinit var resRow: Table
    private var resKey = ""
    /** Ресурсы, что уже бывали в городе (в сетке наверху), и сколько их в строке */
    private val seenRes = HashSet<Int>()
    private var resCols = 6
    private val resLabels = HashMap<Int, Label>()
    private val resTrend = HashMap<Int, Image>()

    private lateinit var sheetHolder: Container<Actor>
    private lateinit var sheet: Table
    private lateinit var sheetTitle: Label
    private lateinit var info: Label
    private lateinit var minus: TextButton
    private lateinit var plus: TextButton
    private lateinit var workersLbl: Label
    private lateinit var workersBox: Table
    private lateinit var limitBox: Table
    private lateinit var limitLbl: Label
    private lateinit var limitMinus: TextButton
    private lateinit var limitPlus: TextButton
    private lateinit var moveBtn: TextButton
    /** Кнопка-решение простоя (поставить лесника, откуда сырьё...) и её действие */
    private lateinit var fixBtn: TextButton
    private var fixAction: (() -> Unit)? = null
    /** Номер ресурса «инструменты» (-1 - нет) */
    private val toolsRes = game.content.resources.indexOfFirst { it.id == "tools" }
    private lateinit var goodsBtn: TextButton
    private lateinit var nbBtn: TextButton
    private lateinit var upgradeBtn: TextButton
    private lateinit var demolishBtn: TextButton
    private lateinit var cancelSiteBtn: TextButton
    private lateinit var undoBtn: TextButton
    private lateinit var upgradeAllBtn: TextButton
    private lateinit var rushBtn: TextButton
    private lateinit var festivalBtn: TextButton
    private lateinit var hireBtn: TextButton
    private lateinit var taxBox: Table
    private lateinit var taxBtns: Array<TextButton>
    private lateinit var crownBtn: TextButton
    private lateinit var prioSiteBtn: TextButton
    private lateinit var prioBtn: TextButton

    /** Праздник в ратуше; нельзя - почему (идёт, рано, мало золота) */
    private fun festivalPressed() {
        when {
            world.festivalLeft > 0 -> showToast(Lang.t("festival_on", dur(world.festivalLeft)), 2500)
            world.festivalWait > 0 -> showToast(Lang.t("festival_wait", (world.festivalWait + Tune.TICKS_PER_DAY - 1) / Tune.TICKS_PER_DAY), 2500)
            world.gold < world.festivalCost() -> showToast(Lang.t("no_gold"), 2500)
            else -> { world.submit(Command.FESTIVAL, 0, 0, 0); Sfx.play("level"); updateInfo() }
        }
    }
    private lateinit var rotateBtn: ImageButton
    /** Поворот со словом (телефон: подсказки при наведении там нет, значок без подписи непонятен) */
    private lateinit var rotateWordBtn: TextButton
    /** Куда смотрит ставящееся здание (сторона входа, как World.bDir); запоминается между постройками */
    private var ghostDir = 0
    /** Рыбак разворачивается к воде сам - его не поворачивают, вход - с первой стороны */
    private fun autoFacing(def: Int) = def >= 0 && game.content.buildings[def].id == "fisher"
    private val gDir get() = if (autoFacing(buildDef)) 0 else ghostDir

    /** Повернуть ставящееся здание на 90 градусов (кнопка в панели, клавиша T) */
    private fun rotateGhost() {
        if (buildDef == COPY_MODE) { tplRotate(); return }
        if (buildDef < 0 || autoFacing(buildDef)) return
        ghostDir = (ghostDir + 1) and 3
        Sfx.play("click")
        updateInfo()
    }
    private lateinit var confirm: TextButton
    private lateinit var cancel: TextButton
    private lateinit var roadRemoveBtn: TextButton
    private lateinit var tplResetBtn: TextButton
    private lateinit var tplLastBtn: TextButton
    /** Слой на карте включён автоматически на время постановки здания-услуги */
    private var autoOverlay = false
    private lateinit var actions: Table
    private var actionsKey = ""
    /** Клетка без здания, про которую открыта панель (-1 - панель не про клетку) */
    private var tileCell = -1
    /** Выбранный житель: постоянный номер (aSerial), -1 - никого; следить - камера идёт за ним */
    private var selSerial = -1
    private var following = false
    private var selIdx = -1
    private val agentPt = com.badlogic.gdx.math.Vector2()
    private lateinit var followBtn: TextButton
    private lateinit var homeBtn: TextButton
    private lateinit var workBtn: TextButton
    private val sheetSel get() = selected >= 0 || tileCell >= 0 || selSerial >= 0

    private class Card(val def: Int, val table: Table)
    /** Сколько зданий поставлено в этом заходе режима постройки (кнопка «Отмена» становится «Готово») */
    private var placedHere = 0
    private var CARD_W = 80f
    private val cards = ArrayList<Card>()

    private lateinit var toast: Label
    private lateinit var debugLabel: Label
    private lateinit var questBox: Table
    private lateinit var questLabel: Label
    private lateinit var hintLabel: Label
    private lateinit var hintHolder: Container<Actor>
    /** Задания раскрыты (подсказки и все строки) или свёрнуты в одну строку */
    /** Задания развёрнуты (награды и подсказки); по умолчанию свёрнуты в одну строку - карту не закрывают. Обучение - развёрнуто */
    private var questOpen = world.scenarioDef?.quests?.any { it.hint != null } == true
    private var questKey = ""

    private lateinit var menu: Table
    private lateinit var outcomeBox: Table
    private var shownOutcome = world.outcome
    private lateinit var trade: Table
    private lateinit var tradeTitle: Label
    private lateinit var tradeState: Label
    private val tradeRows = ArrayList<TradeRow>()
    private var wideTradeRows = false
    private var layoutKey = ""
    /** Уровень города, под который собрана панель строительства */
    private var shownLevel = world.level

    /** Перенос: какое здание переносим (-1 - обычная постройка) */
    private var moveFrom = -1
    /** Снос просит второго нажатия: какое здание и до какого времени */
    private var demolishArmed = -1
    private var demolishUntil = 0L
    /** Выбранное касанием здание (-1 - нет) */
    private var selected = -1
    private var refreshAt = 0L

    private class TradeRow(val r: Int, val nums: List<Label>, val buy: TextButton, val sell: TextButton, val limit: Label, val name: Label)

    companion object {
        private val NL = 10.toChar().toString()
        const val AUTOSAVE_MS = 30_000L
        const val TIPS = 5             // объяснений «впервые» (firstTips)
        /** Сетка ресурсов наверху: ширина ячейки и высота строки; ширина места торговца в блоке кнопок */
        const val RES_CHIP_W = 58f
        const val RES_MIN_W = 55f
        /** Добытчики, у которых своя подсказка «рядом нечего добывать» */
        val HINT_GATHER = setOf("wood", "stone", "fish", "game")
        /** Недоступная кнопка: серее и прозрачнее обычной */
        val DIM_BTN = Color(0.66f, 0.6f, 0.55f, 0.55f)
        /** Графики в палитре игры: жители - медь, довольство - зелень к оливке, золото - охра */
        val CHART_POP = Color(0.86f, 0.56f, 0.36f, 1f)
        const val RES_ROW_H = 24f
        /** ПК: ресурсы наверху крупнее (значок 22, обычный шрифт) */
        const val RES_CHIP_W_PC = 74f
        const val RES_ROW_H_PC = 28f
        const val RES_ICON_PC = 22f
        const val TRADER_CHIP_W = 84f
        const val MIN_ZOOM = 0.35f
        /** Сколько точек мира видно по короткой стороне экрана в начале партии (ПК: город крупно, телефон - как есть) */
        const val START_VIEW = 480f
        const val TRADE_STEP = 5
        const val LIMIT_STEP = 10
        /** buildDef для режима дороги */
        const val ROAD_MODE = -2
        /** Стены кладутся линией, как дорога: частокол и каменная */
        const val WALL_WOOD_MODE = -3
        const val WALL_STONE_MODE = -4
        /** Мощёная дорога и мост кладутся линией, копирование квартала - выделением и шаблоном */
        const val PAVED_MODE = -5
        const val BRIDGE_MODE = -6
        const val COPY_MODE = -7
        /** Размеры элементов интерфейса в точках */
        const val ICON = 18f
        /** Цвет справочных строк в панели здания (разметка шрифта) */
        const val MUTED = "[#CDBFA8]"
        /** Почему кнопка недоступна - красноватым (не путать с золотыми подписями и оранжевым состоянием) */
        const val WARN = "[#FF8F70]"
        /** Подписи разделов в панели ратуши - приглушённым золотом: значения после них светлее и заметнее */
        const val HEAD = "[#D4B47A]"
        /** Тусклый текст строк, которые сейчас не важны (товары, которых нет ни у кого) */
        val DIM_TEXT = Color(0.62f, 0.57f, 0.50f, 1f)
        /** Наименьшая кнопка под палец */
        const val TOUCH = 44f
        const val CARD_W_MIN = 72f
        const val CARD_W_MAX = 92f
        const val CARD_H = 92f
        const val MENU_CARD_H = 170f
        const val MENU_CARD_H_PHONE = 150f
        /** Желаемая ширина карточки меню строительства */
        const val MENU_CARD_W = 150f
        const val RECENT = 5
        /** Сколько строк в разделе «Недавно» окна «Дела в городе» */
        const val RECENT_EVENTS = 10
        val BUILD_TABS = listOf("home", "food", "materials", "services", "land", "decor", "roads")
        /** Услуги, для которых есть слой покрытия на карте */
        val SERVICE_LAYERS = listOf("market", "church", "tavern", "health", "fire", "decor", "guard")
        const val LANDSCAPE_SHEET_W = 360f
        /** Ниже этой высоты (точки) - низкий экран: кнопки панели здания плотнее */
        const val LOW_SCREEN = 500f
        /** Низкий экран: текст панели здания не ниже этого (точки) */
        const val LOW_INFO_H = 86f
        /** Низкий экран, панель ратуши: текст ниже - над ним плашки */
        const val LOW_HALL_INFO_H = 44f
    }

    /** Начальный масштаб уже подобран (только при первом resize, дальше - как оставил игрок) */
    private var camFitted = false

    init {
        // камера на ратуше: город сразу на виду
        val cx = if (world.hall >= 0) world.bX[world.hall] + 1.5f else world.w / 2f
        val cy = if (world.hall >= 0) world.bY[world.hall] + 1.5f else world.h / 2f
        cam.position.set(renderer.sx(cx, cy), renderer.sy(cx, cy), 0f)
    }

    // для звуков событий: что было в прошлый раз
    private var lastBuilt = -1
    private var lastQuests = world.questsDone
    private var lastTrader = world.traderHere
    private var lastDay = world.day
    private var lastGold = world.gold
    private var lastFail = world.failTimer

    override fun show() {
        Sfx.updateMusic()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)   // «Назад» на Android закрывает панели, а не игру
        setInput()
    }

    private fun setInput() {
        Gdx.input.inputProcessor = InputMultiplexer(Wheel(), if (photo) photoStage else stage, GestureDetector(MapGestures()), Keys())
    }

    // ---------- режим фото ----------

    private var photo = false
    private val photoStage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / scale }, game.batch)
    private var photoShot = false
    private var photoMsgUntil = 0L
    private val photoMsg = Label("", ui.label)

    /** Интерфейс скрыт, видна только карта; полупрозрачные кнопки «Снимок» и выход. Симуляция идёт как была (пробел - пауза) */
    private fun enterPhoto() {
        if (photo) return
        menu.isVisible = false
        if (buildDef != -1 || selected >= 0 || tileCell >= 0) closeSheet()
        photo = true
        renderer.hideIcons = true
        buildPhotoStage()
        setInput()
    }

    private fun exitPhoto() {
        if (!photo) return
        photo = false
        renderer.hideIcons = false
        setInput()
    }

    private fun buildPhotoStage() {
        photoStage.clear()
        val ins = game.safeInsets()
        val shot = TextButton(Lang.t("photo_shot"), ui.button)
        shot.color = Color(1f, 1f, 1f, 0.55f)
        shot.onClick { photoShot = true }
        val exit = TextButton("×", ui.button)
        exit.color = Color(1f, 1f, 1f, 0.55f)
        exit.onClick { exitPhoto() }
        photoMsg.color = Color(1f, 1f, 1f, 0.85f)
        photoMsg.isVisible = false
        val root = Table()
        root.setFillParent(true)
        root.top().right().pad(12f + ins[0] / scale, 12f + ins[1] / scale, 12f + ins[3] / scale, 12f + ins[2] / scale)
        root.add(exit).size(TOUCH).right().row()
        root.add().expand().row()
        root.add(photoMsg).right().padBottom(6f).row()
        root.add(shot).height(TOUCH).minWidth(TOUCH * 2).right()
        photoStage.addActor(root)
    }

    /** Снимок карты (кнопки уже не видны - вызывается до отрисовки слоя фото) */
    private fun takeShot() {
        val files = game.saveFiles
        var ok = false
        if (files != null) try {
            // в вебе (gdx-teavm) размер заднего буфера бывает 0 - тогда размер холста
            val w = Gdx.graphics.backBufferWidth.takeIf { it > 0 } ?: Gdx.graphics.width
            val h = Gdx.graphics.backBufferHeight.takeIf { it > 0 } ?: Gdx.graphics.height
            val px = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
            var i = 3
            while (i < px.size) { px[i] = -1; i += 4 }
            files.exportImage("hearthvale-${world.day}.png", w, h, px)
            ok = true
        } catch (e: Exception) {
            Gdx.app.error("photo", "cannot save shot", e)
        }
        photoMsg.setText(Lang.t(if (ok) "photo_saved" else "photo_failed"))
        photoMsgUntil = TimeUtils.millis() + 2500
    }

    private fun region(name: String) = sprites.named(name)!!.r
    private fun named(name: String) = sprites.named(name)?.r

    /** Повернуть карту на 90 градусов (кнопка, клавиши Q и E); центр экрана остаётся на месте */
    private fun rotateMap(step: Int) {
        renderer.rotate(step)
        clampCamera()
    }

    // ---------- раскладка ----------

    private fun buildHud() {
        stage.clear()
        cards.clear(); resLabels.clear(); resKey = ""; actionsKey = ""
        val landscape = Gdx.graphics.width > Gdx.graphics.height
        // широкий экран (ПК): карточки внизу в одну строку, панель здания справа над ними;
        // ландшафт телефона: карточки колонкой слева, панель справа
        val wide = landscape && Gdx.graphics.width / scale >= 1000f
        val column = landscape && !wide
        // вырезы экрана (камера, скругления) - в точках интерфейса
        val ins = game.safeInsets()
        val iTop = ins[0] / scale
        val iLeft = ins[1] / scale
        val iRight = ins[2] / scale
        val iBottom = ins[3] / scale

        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)

        val topT = topBar(iTop, iLeft, iRight)
        root.add(topT).expandX().fillX().colspan(3).row()

        bottomBarActor = bottomBar()
        val build = Container<Actor>(bottomBarActor).fill()
        bottomHolder = build
        sheet = buildSheet()
        sheetHolder = Container<Actor>()
        sheetHolder.fill()
        questBox = buildQuests(landscape)
        debugLabel = Label("", ui.labelSmall)
        val mid = Table()
        mid.add(questBox).left().top().padTop(4f).padLeft(4f).row()
        legendHolder = Container<Actor>()
        legendHolder.fill()
        legendBox = buildLegend()
        mid.add(legendHolder).left().top().padTop(4f).padLeft(4f).row()
        legendKey = ""
        mid.add(debugLabel).left().top().pad(6f).row()
        mid.add().expand()

        if (landscape) {
            // ландшафт: панель здания справа над нижней полосой
            val midRow = Table()
            midRow.add(mid).expand().fill()
            midRow.add(sheetHolder).right().bottom().width(LANDSCAPE_SHEET_W + (if (wide) 40f else 0f)).padRight(iRight + 6f).padBottom(6f)
            root.add(midRow).expand().fill().colspan(3).row()
            root.add(build).expandX().fillX().colspan(3).padLeft(iLeft).padRight(iRight).padBottom(iBottom).row()
        } else {
            root.add(mid).expand().fill().colspan(3).row()
            root.add(sheetHolder).expandX().fillX().colspan(3).row()
            root.add(build).expandX().fillX().colspan(3).padBottom(iBottom).row()
        }

        // всплывающие подсказки - поверх всего, на раскладку не влияют
        val toastLayer = Table()
        toastLayer.setFillParent(true)
        toastLayer.touchable = Touchable.disabled
        toast = Label("", ui.labelBold)
        toast.setWrap(true)
        toast.setAlignment(Align.center)
        val toastBox = Table()
        toastBox.background = ui.panel
        toastBox.add(toast).width(minOf(360f, Gdx.graphics.width / scale - 32f)).pad(8f)
        // сверху под панелью, а не посреди карты: не закрывает то, куда смотрит игрок
        // отступ - по настоящей высоте верхней панели (ресурсы появляются в ней позже, см. render)
        toastCell = toastLayer.add(toastBox).expand().top().padTop(topT.prefHeight + 8f)
        topBarT = topT
        toastLayer.isVisible = false
        stage.addActor(toastLayer)
        toastHolder = toastLayer
        // подсказка при наведении мыши (ПК) - поверх всего, следует за курсором
        tipLabel = Label("", ui.labelSmall)
        tipLabel.setWrap(true)
        tipBox = Table()
        tipBox.background = ui.panel
        tipBox.touchable = Touchable.disabled
        tipBox.add(tipLabel).width(240f).pad(6f)
        tipBox.isVisible = false
        stage.addActor(tipBox)

        stats = overlay(); statsBody = Table(); fillOverlay(stats, Lang.t("stats"), statsBody, null)
        events = overlay(); eventsBody = Table()
        fillOverlay(events, Lang.t("events"), eventsBody, null)
        summary = overlay(); summaryBody = Table(); fillOverlay(summary, Lang.t("summary"), summaryBody, null)
        stage.addActor(stats); stage.addActor(events); stage.addActor(summary)
        chain = overlay(); chainBody = Table(); fillOverlay(chain, Lang.t("chain"), chainBody, null, fit = true)
        layersWin = overlay(); layersBody = Table(); fillOverlay(layersWin, Lang.t("layers"), layersBody, null, fit = true)
        stage.addActor(chain); stage.addActor(layersWin)
        goodsWin = overlay(); goodsBody = Table(); fillOverlay(goodsWin, Lang.t("store_goods"), goodsBody, null, fit = true)
        stage.addActor(goodsWin)
        nbWin = overlay(); nbBody = Table(); fillOverlay(nbWin, Lang.t("nb_title"), nbBody, null)
        stage.addActor(nbWin)
        trade = buildTrade()
        trade.isVisible = false
        stage.addActor(trade)
        outcomeBox = Table()
        outcomeBox.setFillParent(true)
        outcomeBox.background = ui.dim
        outcomeBox.touchable = Touchable.enabled
        outcomeBox.isVisible = false
        stage.addActor(outcomeBox)
        menu = buildMenu()
        menu.isVisible = false
        stage.addActor(menu)
        buildWin = overlay(); buildMenuBody = Table()
        fillOverlay(buildWin, Lang.t("build_menu"), buildMenuBody, null, wide = true)
        stage.addActor(buildWin)
        if (buildDef >= 0 && !world.unlocked(buildDef)) buildDef = -1
        selectBuild(buildDef)
        refreshTop()
        questKey = ""; updateQuests()   // новая раскладка - текст заданий сразу, а не пустая плашка до следующего обновления
    }

    private var toastHolder: Table? = null
    private lateinit var evBadge: Label
    /** Сколько событий игрок уже видел (непрочитанные - после него); последнее событие, о котором всплыло сообщение */
    private var evSeen = world.evCount
    private var evToasted = world.evCount
    private lateinit var stats: Table
    private lateinit var statsBody: Table
    private lateinit var events: Table
    private lateinit var eventsBody: Table
    /** Простаивающие здания: какое показали последним (кнопка «Простаивают» перебирает по кругу) */
    private var idleCursor = -1
    private lateinit var tipBox: Table
    private lateinit var tipLabel: Label
    private var tipText: (() -> String)? = null
    private val pc get() = game.pcMode

    /**
     * Подсказка к элементу: на ПК - при наведении мыши (рядом с курсором), на телефоне - по касанию (всплывающее сообщение).
     * click - что делать по касанию/клику кроме подсказки (null - только подсказка).
     */
    private fun tip(actor: Actor, text: () -> String, click: (() -> Unit)? = null) {
        actor.touchable = Touchable.enabled
        actor.addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
            override fun enter(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                if (pointer == -1 && pc) tipText = text
            }
            override fun exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (pointer == -1 && toActor?.isDescendantOf(actor) != true) tipText = null
            }
        })
        actor.onClick { if (click != null) click() else if (!pc) showToast(text(), 2500) }
    }

    /** Только подсказка при наведении мыши (кнопки со своим действием по клику) */
    private fun hover(actor: Actor, text: () -> String) {
        actor.addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
            override fun enter(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                if (pointer == -1 && pc) tipText = text
            }
            override fun exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (pointer == -1 && toActor?.isDescendantOf(actor) != true) tipText = null
            }
        })
    }

    /** Подсказка у курсора: текст обновляется каждый кадр, рамка не выходит за экран */
    private fun updateTip() {
        val t = tipText
        if (t == null || !pc) { tipBox.isVisible = false; return }
        val s = t()
        if (s != tipLabel.text.toString()) { tipLabel.setText(s); tipBox.pack() }
        val p = stage.screenToStageCoordinates(com.badlogic.gdx.math.Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
        var x = p.x + 16f; var y = p.y - tipBox.height - 12f
        if (x + tipBox.width > stage.width) x = p.x - tipBox.width - 12f
        if (y < 0) y = p.y + 20f
        tipBox.setPosition(x, y)
        tipBox.isVisible = true
        tipBox.toFront()
    }
    private var traderChipActor: Actor? = null
    private lateinit var traderChipIcon: Image
    private lateinit var traderChipLbl: Label
    private var shipShown = false
    private lateinit var callChip: Table
    private lateinit var callLbl: Label
    private lateinit var tradeCallBtn: TextButton

    /** Верхняя панель: значки и числа фиксированной ширины, полоса ресурсов */
    private fun topBar(iTop: Float, iLeft: Float, iRight: Float): Table {
        val top = Table()
        top.background = ui.bar
        top.touchable = Touchable.enabled
        top.padTop(iTop + 2f).padLeft(iLeft + 4f).padRight(iRight + 4f).padBottom(2f)
        val row = Table()
        val menuBtn = ui.iconButton(region("hud_menu"))
        menuBtn.onClick { openMenu() }
        hover(menuBtn) { Lang.t("menu") + " (Esc)" }
        row.add(menuBtn).padRight(4f)
        fun chip(icon: Image, label: Label, w: Float, text: () -> String): Table {
            val c = Table()
            c.background = ui.chip
            c.touchable = Touchable.enabled
            tip(c, text)
            c.add(icon).size(ICON).padLeft(4f).padRight(2f)
            c.add(label).width(w).left().padRight(3f)
            return c
        }
        seasonImg = ui.image(region("hud_season_0"), ICON)
        dayLbl = Label("", ui.labelBold)
        happyLbl = Label("", ui.labelBold)
        goldLbl = Label("", ui.labelBold)
        popLbl = Label("", ui.labelBold)
        // ширины чисел подобраны под самые длинные значения («100%», «99/99» или «999», «9999», день «999»), строка не шире 380 точек
        // касание фишки - подсказка, что это за число
        weatherImg = ui.image(weatherRegion(world.weather), ICON)
        weatherShown = world.weather
        val dayChip = chip(seasonImg, dayLbl, 28f) {
            Lang.t("tip_day", world.day, Lang.t("season_year", Lang.t("season_" + world.season), world.year)) +
                (if (world.isNight) " · " + Lang.t("night") else "") + NL + weatherTip()
        }
        // погода сегодня - значком в той же фишке (прогноз на завтра - в подсказке); на телефоне строка узкая -
        // не ясная погода показывается вместо значка сезона
        if (pc) dayChip.add(weatherImg).size(ICON).padRight(3f)
        row.add(dayChip).height(34f).padRight(3f)
        row.add(chip(ui.image(region("hud_happy"), ICON), happyLbl, 40f) { Lang.t("tip_happy", world.townHappy) }).height(34f).padRight(3f)
        row.add(chip(ui.image(region("hud_gold"), ICON), goldLbl, 36f) { Lang.t("tip_gold", world.gold, taxPerDay(), world.upkeepPerDay(), taxPerDay() - world.upkeepPerDay()) + NL + Lang.t("tax_tip_line", Lang.t("tax_${world.taxLevel}")) }).height(34f).padRight(3f)
        row.add(chip(ui.image(region("hud_people"), ICON), popLbl, if (pc) 46f else 36f) { Lang.t("tip_people", world.agentCount, world.totalBeds()) }).height(34f)
        // телефон лёжа: ресурсы - в этой же строке, если влезают в две строчки (высота экрана дорога)
        resInline = Container<Actor>()
        resInline.fill()
        row.add(resInline).expandX().fillX().left().padLeft(6f)
        // ПК: события, статистика, поворот, торговец - в этой строке перед паузой (заполняется ниже)
        val pcSide = Table()
        row.add(pcSide)
        pauseBtn = ui.iconButton(region("hud_pause"))
        pauseBtn.onClick { setPaused(!paused) }
        hover(pauseBtn) { Lang.t("pause_tip") }
        row.add(pauseBtn).padLeft(3f).padRight(3f)
        speedBtn = TextButton("", ui.buttonTight)
        speedBtn.onClick { speedIdx = (speedIdx + 1) % speeds.size; setPaused(false); refreshTop() }
        hover(speedBtn) { Lang.t("speed_tip") }
        row.add(speedBtn).width(44f)
        top.add(row).expandX().fillX().row()

        // ресурсы: сетка без прокрутки (все видны сразу), строк столько, сколько нужно; справа - блок 2x2:
        // события, статистика, поворот карты, торговец
        resRow = Table()
        resRow.top().left()
        val line = Table()
        traderBtn = ui.iconButton(region("hud_trader"), 18f, toggle = true)
        val traderChip = Table()
        traderChip.background = ui.chip
        traderChip.touchable = Touchable.enabled
        traderChipIcon = ui.image(region("hud_trader"), ICON)
        traderChipLbl = Label(Lang.t("trader"), ui.labelSmall)
        shipShown = false
        traderChip.add(traderChipIcon).size(ICON).padLeft(4f).padRight(3f)
        // ширина фишки фиксирована (TRADER_CHIP_W): длинное название обрезается, раскладка не распирается
        traderChipLbl.setEllipsis(true)
        traderChip.add(traderChipLbl).minWidth(0f).padRight(5f).expandX().fillX().left()
        tip(traderChip, { Lang.t(if (world.traderShip) "ship_here" else "trader_here") }) { openTrade() }
        // пока торговца нет (днём) - чип «позвать»: значок и цена, тусклый, если золота не хватает
        callChip = Table()
        callChip.background = ui.chip
        callChip.touchable = Touchable.enabled
        callChip.add(ui.image(region("hud_trader"), ICON)).size(ICON).padLeft(4f).padRight(3f)
        // цена вызова - с монетой: видно, что это золото, а не число товара
        callChip.add(ui.image(region("hud_gold"), 12f)).size(12f).padRight(1f)
        callLbl = Label("", ui.labelSmall)
        callChip.add(callLbl).padRight(5f)
        tip(callChip, { Lang.t("call_trader_tip", world.traderCallPrice()) }) { callTrader() }
        // события (с числом непрочитанных) и статистика; на телефоне значки крупнее - кнопки там 44 точки
        val sideIcon = if (pc) 20f else 24f
        val evChip = Table()
        evChip.background = ui.chip
        evChip.add(ui.image(region("hud_bell"), sideIcon)).size(sideIcon).padLeft(4f).padRight(2f)
        evBadge = Label("", ui.labelSmall)
        evBadge.color = Color(1f, 0.6f, 0.45f, 1f)
        evChip.add(evBadge).minWidth(14f).padRight(4f)
        tip(evChip, { Lang.t("events") }) { openEvents() }
        val sideItems = ArrayList<Pair<Actor, Float>>()   // кнопка и её ширина (0 - по содержимому)
        sideItems += evChip to 0f
        val statChip = Table()
        statChip.background = ui.chip
        statChip.add(ui.image(region("hud_stats"), sideIcon)).size(sideIcon).pad(0f, 5f, 0f, 5f)
        tip(statChip, { Lang.t("stats") + if (pc) " (Tab)" else "" }) { openStats() }
        sideItems += statChip to 0f
        // поворот карты (верхняя строка на телефоне полна - здесь)
        named("hud_layers")?.let { r ->
            layersChip = Table()
            layersChip.background = ui.chip
            layersChip.add(ui.image(r, sideIcon)).size(sideIcon).pad(0f, 5f, 0f, 5f)
            tip(layersChip, { Lang.t("layers_tip") + if (pc) " (O)" else "" }) { openLayers() }
            sideItems += layersChip to 0f
        }
        // на телефоне поворот карты - пункт меню слоёв (в нижней полосе мало места)
        if (pc) named("hud_rotate")?.let { r ->
            val rotChip = Table()
            rotChip.background = ui.chip
            rotChip.add(ui.image(r, sideIcon)).size(sideIcon).pad(0f, 5f, 0f, 5f)
            tip(rotChip, { Lang.t("rotate_tip") }) { rotateMap(1) }
            sideItems += rotChip to 0f
        }
        traderHolder = Container<Actor>()
        traderHolder.fill()
        // крупный текст: фишка шире под самое длинное из «торговец»/«корабль», иначе подпись обрезается
        val traderTextW = maxOf(Label(Lang.t("trader"), ui.labelSmall).prefWidth, Label(Lang.t("ship"), ui.labelSmall).prefWidth)
        sideItems += traderHolder to maxOf(TRADER_CHIP_W, kotlin.math.ceil(ICON + 12f + traderTextW + 1f))
        traderChipActor = traderChip
        val topW = Gdx.graphics.width / scale - iLeft - iRight - 8f
        // телефон: кнопки уходят вниз, к «Строить» (под большой палец), ресурсы - ровной сеткой во всю ширину
        bottomSide.clear()
        if (!pc) {
            bottomSide.addAll(sideItems)
            resCols = maxOf(3, (topW / RES_CHIP_W).toInt())
            // целое число точек: Table округляет колонки вверх, дробная ширина распирала строку на пару точек
            resChipW = kotlin.math.floor(topW / resCols)
            resAreaW = topW
            resFullW = topW
            resLine = Container<Actor>()
            resLine.fill()
            resLine.setActor(resRow)
            resInlineW = if (Gdx.graphics.width > Gdx.graphics.height) kotlin.math.floor(topW - row.prefWidth - 8f) else -1f
            line.add(resLine).expandX().fillX().top().left()
            top.add(line).expandX().fillX().padTop(2f)
            return top
        }
        resInlineW = -1f
        // ПК: кнопки - фишками высотой с фишки слева (день, довольство...), ресурсы - вся вторая строка
        resChipW = RES_CHIP_W_PC
        resAreaW = -1f
        for ((a, wd) in sideItems) {
            val cell = pcSide.add(a).height(34f).padRight(3f)
            cell.width(if (wd > 0f) wd else 40f)
        }
        resCols = maxOf(3, (topW / RES_CHIP_W_PC).toInt())
        line.add(resRow).expandX().fillX().top().left()
        top.add(line).expandX().fillX().padTop(2f)
        return top
    }

    /**
     * Нижняя полоса: кнопка «Строить» (меню строительства) и до 5 недавних построек - быстро поставить ещё.
     * Внизу во всех раскладках - на телефоне до неё дотягивается большой палец.
     */
    /** Телефон: события, статистика, поворот, торговец - в нижней полосе справа (наверху им нет места под палец) */
    private val bottomSide = ArrayList<Pair<Actor, Float>>()
    /** Ширина ячейки ресурса: на телефоне сетка растянута на всю ширину */
    private var resChipW = RES_CHIP_W
    /** Телефон: ширина сетки ресурсов (-1 на ПК) - по ней ровные строки */
    private var resAreaW = -1f
    /** Телефон лёжа: место под ресурсы в первой строке (-1 - не лёжа или ПК) и вся ширина второй строки */
    private var resInlineW = -1f
    private var resFullW = -1f
    private lateinit var resInline: Container<Actor>
    private lateinit var resLine: Container<Actor>

    private fun bottomBar(): Table {
        shownLevel = world.level
        val bar = Table()
        bar.background = ui.bar
        bar.touchable = Touchable.enabled
        val buildBtn = TextButton(Lang.t("build_menu_btn") + if (pc) " (B)" else "", ui.buttonMain)
        buildBtn.onClick { openBuildMenu() }
        hover(buildBtn) { Lang.t("build_menu_tip") }
        bar.add(buildBtn).height(56f).width(if (pc) 150f else 104f).pad(5f)
        val list = Table()
        for ((i, def) in recent.withIndex()) {
            if (def >= 0 && (def >= game.content.buildings.size || !world.unlocked(def))) continue
            if (def < 0 && !isLine(def)) continue
            val t = Table()
            t.touchable = Touchable.enabled
            val pic = Stack()
            val icon = if (def < 0) lineIcon(def) else sprites.cards[def].r
            pic.add(Image(TextureRegionDrawable(icon), com.badlogic.gdx.utils.Scaling.fit))
            if (pc) {
                val k = Table()
                val kl = Label((i + 1).toString(), ui.labelSmall)
                kl.color = Color(1f, 0.85f, 0.45f, 1f)
                k.add(kl).expand().top().left().padLeft(1f)
                pic.add(k)
            }
            t.add(pic).size(48f, 44f).pad(3f)
            t.background = ui.card
            tip(t, { cardTip(def) }) { cardTapped(def) }
            cards.add(Card(def, t))
            list.add(t).size(56f, 56f).padRight(5f)
        }
        // недавних ещё нет (начало партии) - чтобы пустая полоса не выглядела сломанной
        // ПК: недавних ещё нет (начало партии) - полоса не тянется пустой через весь экран, видна только кнопка
        val emptyPc = pc && !list.hasChildren()
        if (emptyPc) { bar.background = null; bar.touchable = Touchable.childrenOnly }
        val pane = ScrollPane(list)
        if (emptyPc) pane.touchable = Touchable.disabled
        pane.setScrollingDisabled(false, true)
        pane.setOverscroll(false, false)
        bar.add(pane).expandX().fillX().left().height(56f).padTop(5f).padBottom(5f)
        for ((a, wd) in bottomSide) bar.add(a).size(if (wd > 0f) wd else TOUCH, TOUCH).padRight(4f)
        return bar
    }

    private lateinit var buildWin: Table
    /** Нижняя полоса прячется на время постановки здания - там своя панель с «Строить» и «Отмена» */
    private lateinit var bottomBarActor: Table
    private lateinit var bottomHolder: Container<Actor>
    private lateinit var buildMenuBody: Table
    private var buildTab = "home"
    /** Недавние постройки (типы зданий и дорога), первая - последняя выбранная; хранится между партиями */
    private val recent = ArrayList<Int>().apply {
        Storage.recentBuilds.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { if (size < RECENT) add(it) }
    }

    private fun addRecent(def: Int) {
        recent.remove(def)
        recent.add(0, def)
        while (recent.size > RECENT) recent.removeAt(recent.size - 1)
        Storage.recentBuilds = recent.joinToString(",")
    }

    private fun openBuildMenu() {
        buildWin.isVisible = true
        fillBuildMenu()
    }

    /** Меню строительства: вкладки по категориям и сетка карточек (картинка, цена, что даёт, с какого уровня откроется) */
    private fun fillBuildMenu() {
        val body = buildMenuBody
        body.clearChildren()
        val w = bodyWidth[body] ?: 360f
        // вкладки без прокрутки: переносятся на следующую строку, кнопки в строке растягиваются на всю ширину
        val tabs = Table()
        var line = Table()
        var used = 0f
        for (cat in BUILD_TABS) {
            val b = TextButton(Lang.t("cat_$cat"), ui.toggle)
            b.isChecked = cat == buildTab
            b.onClick { buildTab = cat; fillBuildMenu() }
            val bw = b.prefWidth + 4f
            if (used > 0f && used + bw > w) {
                tabs.add(line).width(w).row()
                line = Table(); used = 0f
            }
            line.add(b).expandX().fillX().padRight(4f).padBottom(4f)
            used += bw
        }
        tabs.add(line).width(w).row()
        body.add(tabs).width(w).left().padBottom(6f).row()
        // карточки: на телефоне три в ряд, на ПК - сколько влезет
        val cols = maxOf(3, (w / MENU_CARD_W).toInt())
        val cw = (w - 6f * cols) / cols
        val grid = Table()
        grid.top().left()
        val defs: List<Int> = if (buildTab == "roads") listOf(ROAD_MODE, PAVED_MODE, BRIDGE_MODE, COPY_MODE)
            else game.content.buildings.filter { it.buildable && it.cat == buildTab }.sortedBy { if (world.unlocked(it.index)) 0 else 1 + it.level }.map { it.index } +
                (if (buildTab == "land") listOf(WALL_WOOD_MODE, WALL_STONE_MODE) else emptyList())
        for ((i, def) in defs.withIndex()) {
            grid.add(menuCard(def, cw)).width(cw).height(if (pc) MENU_CARD_H else MENU_CARD_H_PHONE).pad(3f).top()
            if ((i + 1) % cols == 0) grid.row()
        }
        body.add(grid).width(w).left().row()
    }

    private fun menuCard(def: Int, cw: Float): Table {
        val locked = (def >= 0 && (!world.unlocked(def) || world.wonderTaken(def))) || lineLocked(def)
        val t = Table()
        t.touchable = Touchable.enabled
        t.background = if (locked) ui.cardLocked else ui.card
        val pic = Stack()
        pic.add(Image(TextureRegionDrawable(if (def < 0) lineIcon(def) else sprites.cards[def].r), com.badlogic.gdx.utils.Scaling.fit))
        if (locked) {
            val lock = Table()
            lock.add(ui.image(region("hud_lock"), 22f)).size(22f).expand().bottom().right()
            pic.add(lock)
        }
        t.add(pic).size(cw - 12f, if (pc) 70f else 58f).padTop(4f).row()
        // на телефоне в узкой карточке название - обычным шрифтом, чтобы длинное («Каменоломня») влезало
        val name = Label(if (def < 0) lineName(def) else Lang.t(game.content.buildings[def].name), if (pc) ui.labelBold else ui.label)
        name.setAlignment(Align.center)
        name.setEllipsis(true)
        t.add(name).width(cw - 8f).row()
        val cost = Table()
        val pairs = if (def < 0) lineCosts(def)
            else game.content.buildings[def].cost.indices.filter { game.content.buildings[def].cost[it] > 0 }.map { it to game.content.buildings[def].cost[it] }
        for ((r, n) in pairs.take(4)) {
            sprites.named("res_${game.content.resources[r].id}")?.let { cost.add(ui.image(it.r, 14f)).size(14f) }
            val cl = Label(n.toString(), ui.labelSmall)
            // не хватает на складах (с учётом обещанного другим стройкам) - красным
            if (world.freeStock(r) < n) cl.color = Color(1f, 0.45f, 0.35f, 1f)
            cost.add(cl).padRight(4f)
        }
        if (def >= 0 && game.content.buildings[def].gold > 0) {
            cost.add(ui.image(region("hud_gold"), 14f)).size(14f)
            cost.add(Label(game.content.buildings[def].gold.toString(), ui.labelSmall))
        }
        t.add(cost).height(17f).row()
        val what = Label(shortWhat(def), ui.labelSmall)
        what.setWrap(true)
        what.setAlignment(Align.center)
        if (locked) what.color = Color(1f, 0.75f, 0.55f, 1f)
        t.add(what).width(cw - 10f).expandY().top().padTop(2f).padBottom(4f)
        if (locked) t.color = Color(1f, 1f, 1f, 0.8f)
        tip(t, { cardTip(def) }) { cardTapped(def) }
        return t
    }

    /** Одна-две строки для карточки меню: что делает здание или с какого уровня откроется */
    private fun shortWhat(def: Int): String {
        if (def == ROAD_MODE) return Lang.t("road_short")
        if (def == COPY_MODE) return Lang.t("tpl_short")
        if (def == PAVED_MODE || def == BRIDGE_MODE) return if (lineLocked(def)) lineOpensAt(def) else Lang.t(if (def == PAVED_MODE) "paved_short" else "bridge_short")
        if (isWall(def)) return if (lineLocked(def)) lineOpensAt(def) else Lang.t("wall_short")
        if (world.wonderTaken(def)) return Lang.t("wonder_taken")
        val d = game.content.buildings[def]
        when (world.lockReason(def)) {
            "lock_level" -> return Lang.t("opens_at", Lang.t(game.content.levels[d.level.coerceIn(0, game.content.levels.size - 1)].name))
            "lock_prestige" -> return Lang.t("opens_fame", d.prestige)
        }
        val makes = d.output.indices.filter { d.output[it] > 0 }.map { Lang.t(game.content.resources[it].name) }
        val uses = d.input.indices.filter { d.input[it] > 0 }.map { Lang.t(game.content.resources[it].name) }
        val fame = if (d.fame > 0) NL + Lang.t("fame_line", d.fame) else ""
        return when {
            makes.isNotEmpty() -> Lang.t("makes", makes.joinToString(", ")) + if (uses.isNotEmpty()) NL + Lang.t("uses", uses.joinToString(", ")) else ""
            d.beds > 0 -> Lang.t("beds", d.beds)
            d.isDecor -> Lang.t("decor_info", d.radius) + fame
            d.serves == "market" -> Lang.t("market_short", d.radius)
            d.serves == "port" -> Lang.t("port_short") + fame
            d.serves == "fair" -> Lang.t("fair_short") + fame
            d.serves == "guild" -> Lang.t("guild_short", Tune.WONDER_SPEED) + fame
            d.serves == "school" -> Lang.t("school_short", d.radius) + fame
            d.serves == "trade" -> Lang.t("trade_short") + fame
            d.isService -> Lang.t("serves", d.radius) + fame
            d.isStore -> Lang.t("store_short", d.capacity)
            d.isGuard -> Lang.t("guard_info", d.radius)
            d.border > 0 -> Lang.t("border_info", d.border)
            else -> ""
        }
    }

    /** Особые строки подсказки и панели зданий: рынок, ярмарка, гильдия, пристань, слава */
    private fun specialLines(d: town.data.BuildingDef): String = when (d.serves) {
        "market" -> NL + Lang.t("market_info")
        "fair" -> NL + Lang.t("fair_info", 100 - Tune.FAIR_FESTIVAL, Tune.FAIR_SHARE)
        "guild" -> NL + Lang.t("guild_info", Tune.WONDER_SPEED)
        "port" -> NL + Lang.t("port_info", Tune.SHIP_SHARE)
        "school" -> NL + Lang.t("school_info", Tune.KID_SCHOOL_DAYS, Tune.SKILL_BONUS)
        "trade" -> NL + Lang.t("trade_info", Tune.CONTRACT_DAYS)
        else -> ""
    }

    /** Подсказка карточки: название, цена, что делает, закрыта ли */
    private fun cardTip(def: Int): String {
        if (def == ROAD_MODE) return Lang.t("road") + ". " + Lang.t("road_hint", Tune.ROAD_STONE)
        if (def == COPY_MODE) return Lang.t("tpl_card") + ". " + Lang.t("tpl_hint")
        if (def == PAVED_MODE) return lineName(def) + ". " + Lang.t("paved_hint", Tune.PAVE_STONE) + if (lineLocked(def)) NL + lineOpensAt(def) else ""
        if (def == BRIDGE_MODE) return lineName(def) + ". " + Lang.t("bridge_hint", Tune.BRIDGE_PLANKS, Tune.BRIDGE_STONE) + if (lineLocked(def)) NL + lineOpensAt(def) else ""
        if (def < 0) return lineName(def) + ". " + Lang.t("wall_hint", lineCosts(def)[0].second, resLower(lineCosts(def)[0].first))
        val d = game.content.buildings[def]
        val sb = StringBuilder(Lang.t(d.name))
        val parts = d.cost.indices.filter { d.cost[it] > 0 }.map { "${Lang.t(game.content.resources[it].name)} ${d.cost[it]}" } +
            (if (d.gold > 0) listOf(Lang.t("gold", d.gold)) else emptyList())
        sb.append(NL).append(Lang.t("cost", parts.joinToString(", ")))
        val makes = d.output.indices.filter { d.output[it] > 0 }.map { Lang.t(game.content.resources[it].name) }
        val uses = d.input.indices.filter { d.input[it] > 0 }.map { Lang.t(game.content.resources[it].name) }
        if (makes.isNotEmpty()) sb.append(NL).append(Lang.t("makes", makes.joinToString(", ")) + if (uses.isNotEmpty()) NL + Lang.t("uses", uses.joinToString(", ")) else "")
        if (d.beds > 0) sb.append(NL).append(Lang.t("beds", d.beds))
        if (d.isDecor) sb.append(NL).append(Lang.t("decor_info", d.radius))
        else if (d.isService) sb.append(NL).append(Lang.t("serves", d.radius))
        if (d.border > 0) sb.append(NL).append(Lang.t("border_info", d.border))
        if (d.fame > 0) sb.append(NL).append(Lang.t("fame_line", d.fame))
        sb.append(specialLines(d))
        if (d.upkeep > 0) sb.append(NL).append(Lang.t("upkeep_line", world.upkeepFor(d)))
        if (d.build > 0) sb.append(NL).append(Lang.t("eta_build", dur(world.buildEta(d))))
        if (world.lockReason(def) == "lock_prestige") sb.append(NL).append(Lang.t("locked_fame", Lang.t(d.name), d.prestige, world.prestige))
        else if (!world.unlocked(def)) {
            val lv = game.content.levels[d.level.coerceIn(0, game.content.levels.size - 1)]
            sb.append(NL).append(Lang.t("locked_until", Lang.t(d.name), Lang.t(lv.name), lv.pop, lv.happy))
        }
        return sb.toString()
    }

    private fun isLine(def: Int) = def == ROAD_MODE || def == WALL_WOOD_MODE || def == WALL_STONE_MODE || def == PAVED_MODE || def == BRIDGE_MODE
    private fun isWall(def: Int) = def == WALL_WOOD_MODE || def == WALL_STONE_MODE
    private fun lineName(def: Int) = Lang.t(when (def) {
        WALL_WOOD_MODE -> "wall_wood"; WALL_STONE_MODE -> "wall_stone"; PAVED_MODE -> "paved"; BRIDGE_MODE -> "bridge"; COPY_MODE -> "tpl_card"; else -> "road"
    })
    private fun lineIcon(def: Int) = when (def) {
        WALL_WOOD_MODE -> sprites.walls["wood_post"]?.r ?: region("hud_road")
        WALL_STONE_MODE -> sprites.walls["stone_post"]?.r ?: region("hud_road")
        PAVED_MODE -> named("hud_paved") ?: region("hud_road")
        BRIDGE_MODE -> named("hud_bridge") ?: region("hud_road")
        COPY_MODE -> named("hud_copy") ?: region("hud_road")
        else -> region("hud_road")
    }
    /** Ресурсы и сколько их на клетку дороги, мощёной, моста или стены */
    private fun lineCosts(def: Int): List<Pair<Int, Int>> = when (def) {
        WALL_WOOD_MODE -> listOf(world.wallCost(WALL_WOOD))
        WALL_STONE_MODE -> listOf(world.wallCost(WALL_STONE))
        PAVED_MODE -> listOf(game.content.resource("stone").index to Tune.PAVE_STONE)
        BRIDGE_MODE -> listOf(game.content.resource("planks").index to Tune.BRIDGE_PLANKS, game.content.resource("stone").index to Tune.BRIDGE_STONE)
        COPY_MODE -> emptyList()
        else -> listOf(game.content.resource("stone").index to Tune.ROAD_STONE)
    }
    private fun wallKind(def: Int) = if (def == WALL_STONE_MODE) WALL_STONE else WALL_WOOD
    /** Уровень города, с которого открыта линия (0 - сразу) */
    private fun lineLevel(def: Int) = when (def) { WALL_STONE_MODE -> Tune.WALL_STONE_LEVEL; PAVED_MODE -> Tune.PAVE_LEVEL; BRIDGE_MODE -> Tune.BRIDGE_LEVEL; else -> 0 }
    private fun lineLocked(def: Int) = def < 0 && world.level < lineLevel(def)
    private fun lineOpensAt(def: Int) = Lang.t("opens_at", Lang.t(game.content.levels[lineLevel(def)].name))
    private fun lineTileKind(def: Int) = when (def) { PAVED_MODE -> PAVED; BRIDGE_MODE -> BRIDGE; WALL_WOOD_MODE -> WALL_W; WALL_STONE_MODE -> WALL_S; else -> ROAD }
    private fun lineKindOf(def: Int) = when (def) { PAVED_MODE -> 2; BRIDGE_MODE -> 3; WALL_WOOD_MODE, WALL_STONE_MODE -> 1; else -> 0 }
    /** Какую услугу показывает слой при постановке здания (null - нет слоя) */
    private fun serviceLayerOf(def: Int): String? {
        if (def < 0) return null
        val d = game.content.buildings[def]
        val k = if (d.isGuard) "guard" else if (d.isService) d.serves else null
        return if (k != null && k in SERVICE_LAYERS) k else null
    }

    private fun cardTapped(def: Int) {
        if (lineLocked(def)) { showToast(lineOpensAt(def), 3000); return }
        if (def >= 0 && world.wonderTaken(def)) { showToast(Lang.t("wonder_taken"), 3000); return }
        if (def >= 0 && world.lockReason(def) == "lock_prestige") {
            val d = game.content.buildings[def]
            showToast(Lang.t("locked_fame", Lang.t(d.name), d.prestige, world.prestige), 3500)
            return
        }
        if (def >= 0 && !world.unlocked(def)) {
            val d = game.content.buildings[def]
            val lv = game.content.levels[d.level.coerceIn(0, game.content.levels.size - 1)]
            showToast(Lang.t("locked_until", Lang.t(d.name), Lang.t(lv.name), lv.pop, lv.happy), 3500)
            return
        }
        if (::buildWin.isInitialized) buildWin.isVisible = false
        placedHere = 0
        if (def != COPY_MODE) addRecent(def)
        selectBuild(def)
        buildHud()
    }

    private lateinit var hallStrip: Container<Actor>
    /** Ячейка текста панели: у ратуши на низком экране ниже (над ним плашки) */
    private var infoCell: com.badlogic.gdx.scenes.scene2d.ui.Cell<*>? = null
    private lateinit var hallPlates: Table
    private lateinit var hallLevelLbl: Label
    private lateinit var hallNextLbl: Label
    private lateinit var hallBar: Bar
    private lateinit var hallHappyLbl: Label
    private lateinit var hallTaxLbl: Label
    private lateinit var hallFameLbl: Label

    /** Полоса прогресса из белой области атласа: тёмный фон, заливка цветом на долю frac */
    private class Bar(val white: com.badlogic.gdx.graphics.g2d.TextureRegion, val fill: Color) : Actor() {
        var frac = 0f
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            val old = batch.packedColor
            batch.setColor(0f, 0f, 0f, 0.35f * parentAlpha)
            batch.draw(white, x, y, width, height)
            batch.setColor(fill.r, fill.g, fill.b, fill.a * parentAlpha)
            batch.draw(white, x, y, width * frac.coerceIn(0f, 1f), height)
            batch.packedColor = old
        }
    }

    private fun buildHallPlates(): Table {
        val t = Table()
        val lvl = Table()
        lvl.background = ui.chip
        hallLevelLbl = Label("", ui.labelHead)
        hallNextLbl = Label("", ui.labelSmall)
        hallNextLbl.color = Color(0.85f, 0.78f, 0.66f, 1f)
        hallNextLbl.setWrap(true)
        hallBar = Bar(sprites.white, town.ui.Ui.GOLD)
        lvl.add(hallLevelLbl).left().padLeft(6f).padTop(3f).row()
        lvl.add(hallNextLbl).expandX().fillX().left().padLeft(6f).padRight(6f).row()
        lvl.add(hallBar).expandX().fillX().height(6f).pad(3f, 6f, 6f, 6f).row()
        tip(lvl, { townLevelTip() })
        t.add(lvl).expandX().fillX().colspan(3).padBottom(4f).row()
        fun chip(icon: String, lbl: Label, text: () -> String): Table {
            val c = Table()
            c.background = ui.chip
            named(icon)?.let { c.add(ui.image(it, ICON)).size(ICON).padLeft(6f).padRight(4f) }
            c.add(lbl).left().padRight(6f)
            c.add().expandX()
            tip(c, text)
            return c
        }
        hallHappyLbl = Label("", ui.labelBold)
        hallTaxLbl = Label("", ui.labelBold)
        hallFameLbl = Label("", ui.labelBold)
        t.add(chip("hud_happy", hallHappyLbl) { Lang.t("tip_happy", world.townHappy) }).expandX().fillX().height(34f).padRight(4f)
        t.add(chip("hud_gold", hallTaxLbl) { Lang.t("tip_gold", world.gold, taxPerDay(), world.upkeepPerDay(), taxPerDay() - world.upkeepPerDay()) }).expandX().fillX().height(34f).padRight(4f)
        t.add(chip("hud_prestige", hallFameLbl) { Lang.t("fame_town", world.prestige, world.fameDaily()) }).expandX().fillX().height(34f)
        t.row()
        return t
    }

    private fun townLevelTip(): String {
        val levels = game.content.levels
        return if (world.level + 1 < levels.size) {
            val next = levels[world.level + 1]
            Lang.t("next_level", Lang.t(next.name), world.agentCount, next.pop, next.happy)
        } else Lang.t("max_level")
    }

    /** Плашки ратуши: показать (ратуша выбрана) или убрать, числа - свежие */
    private fun updateHallPlates(show: Boolean) {
        val want: Actor? = if (show) hallPlates else null
        if (hallStrip.actor !== want) {
            hallStrip.setActor(want); hallStrip.padBottom(if (show) 6f else 0f)
            // низкий экран (телефон лёжа): плашки и так высокие - текст ниже, подпись «дальше...» только в подсказке
            val low = Gdx.graphics.height / scale < LOW_SCREEN
            if (low) {
                hallNextLbl.isVisible = false
                (hallNextLbl.parent as? Table)?.getCell(hallNextLbl)?.height(0f)
            }
        }
        if (!show) return
        val levels = game.content.levels
        hallLevelLbl.setText(Lang.t(levels[world.level].name))
        if (world.level + 1 < levels.size) {
            val next = levels[world.level + 1]
            hallNextLbl.setText(Lang.t("hall_next_short", Lang.t(next.name), world.agentCount, next.pop, next.happy))
            hallBar.frac = world.agentCount.toFloat() / maxOf(1, next.pop)
            hallBar.isVisible = true
        } else { hallNextLbl.setText(Lang.t("max_level")); hallBar.isVisible = false }
        hallHappyLbl.setText("${world.townHappy}%")
        hallTaxLbl.setText(Lang.t("hall_tax_short", taxPerDay()))
        hallFameLbl.setText(Lang.t("hall_fame_short", world.prestige, world.fameDaily()))
    }

    /** Панель выбранного: заголовок с крестиком, текст (листается, высота ограничена), кнопки */
    private fun buildSheet(): Table {
        val s = Table()
        s.background = ui.panel
        s.touchable = Touchable.enabled
        sheetTitle = Label("", ui.labelBold)
        sheetTitle.setWrap(true)
        val close = TextButton("×", ui.button)
        close.onClick { closeSheet() }
        val head = Table()
        head.add(sheetTitle).expandX().fillX().left().padLeft(8f)
        head.add(close).size(44f)
        s.add(head).expandX().fillX().row()
        // ратуша: главное - плашками над текстом (уровень с полосой до следующего, довольство, налоги, слава)
        hallStrip = Container<Actor>()
        hallStrip.fill()
        hallPlates = buildHallPlates()
        s.add(hallStrip).expandX().fillX().padLeft(8f).padRight(8f).row()
        info = Label("", ui.label)
        info.setWrap(true)
        // панель ограничена по высоте: ползунок справа показывает, что текст листается
        val pane = ScrollPane(info, ui.scroll)
        pane.setScrollingDisabled(true, false)
        pane.setOverscroll(false, false)
        pane.setFadeScrollBars(false)
        val maxH = Gdx.graphics.height / scale * (if (Gdx.graphics.width > Gdx.graphics.height) 0.45f else 0.22f)
        // на низком экране текст не сжимается кнопками: видно состояние и подсказку, что делать (~4 строки)
        infoCell = s.add(pane).expandX().fillX().maxHeight(maxH).minHeight(if (Gdx.graphics.height / scale < LOW_SCREEN) LOW_INFO_H else 0f).padLeft(8f).padRight(8f)
        s.row()

        confirm = TextButton(Lang.t("build"), ui.buttonMain); confirm.onClick { if (buildDef == COPY_MODE) tplConfirm() else if (isLine(buildDef)) placeRoad() else placeGhost() }
        cancel = TextButton(Lang.t("cancel"), ui.button); cancel.onClick { selectBuild(-1) }
        minus = TextButton("–", ui.button); minus.onClick { changeWorkers(-1) }  // знака U+2212 нет в веб-версии PT Sans
        plus = TextButton("+", ui.button); plus.onClick { changeWorkers(1) }
        // у каждого регулятора подпись сверху: две пары «– N +» рядом иначе не различить
        fun caption(key: String): Label = Label(Lang.t(key), ui.labelSmall).also { it.color = Color(0.85f, 0.78f, 0.66f, 1f) }
        workersLbl = Label("", ui.labelBold)
        workersBox = Table()
        workersBox.add(caption("ctl_workers")).colspan(2).row()
        workersBox.add(ui.image(region("hud_people"), ICON)).size(ICON).padRight(3f)
        workersBox.add(workersLbl).minWidth(30f)
        limitLbl = Label("", ui.labelBold)
        limitLbl.setAlignment(Align.center)
        limitBox = Table()
        limitBox.add(caption("ctl_limit")).row()
        limitBox.add(limitLbl).minWidth(44f)
        hover(minus) { Lang.t("workers_less") }; hover(plus) { Lang.t("workers_more") }
        limitMinus = TextButton("–", ui.button); limitMinus.onClick { changeLimit(-1) }
        limitPlus = TextButton("+", ui.button); limitPlus.onClick { changeLimit(1) }
        hover(limitMinus) { Lang.t("limit_tip") }; hover(limitPlus) { Lang.t("limit_tip") }
        // налог: три кнопки в панели ратуши, выбранная - янтарная
        taxBtns = Array(3) { i ->
            val b = TextButton(Lang.t("tax_$i"), ui.buttonTight)
            b.onClick { world.submit(Command.TAX, i, 0, 0); Sfx.play("click"); updateInfo() }
            hover(b) { Lang.t("tax_tip", Lang.t("tax_$i"), world.taxPreview(i), taxHappy(i)) }
            b
        }
        taxBox = Table()
        // подпись слева в одной строке с кнопками: не висит мелко над ними
        taxBox.add(caption("tax_caption")).left().padLeft(3f).padRight(6f)
        val taxLine = Table()
        for (b in taxBtns) taxLine.add(b).padRight(3f)
        taxBox.add(taxLine).left()
        crownBtn = TextButton("", ui.buttonMain)
        crownBtn.onClick {
            if (world.canDeliverCrown()) { world.submit(Command.CROWN, 0, 0, 0); Sfx.play("quest"); showToast(Lang.t("crown_given"), 3000); updateInfo() }
            else showToast(Lang.t("crown_lack", world.crN - maxOf(0, world.stockAvailable(world.crR)), resLower(world.crR)), 3000)
        }
        hover(crownBtn) { Lang.t("crown_tip") }
        prioSiteBtn = iconText(Lang.t("prio_site"), ui.toggle, "hud_prio")
        prioSiteBtn.onClick { if (selected >= 0) { world.submit(Command.PRIORITY, selected, if (world.bPrio[selected] == 1) 0 else 1, 0); updateInfo() } }
        hover(prioSiteBtn) { Lang.t("prio_site_tip") }
        prioBtn = TextButton("", ui.button)
        prioBtn.onClick {
            if (selected >= 0) {
                val next = when (world.bPrio[selected]) { -1 -> 0; 0 -> 1; else -> -1 }
                world.submit(Command.PRIORITY, selected, next, 0); updateInfo()
            }
        }
        hover(prioBtn) { Lang.t("prio_tip") }
        moveBtn = TextButton(Lang.t("move"), ui.button); moveBtn.onClick { startMove() }
        fixBtn = TextButton("", ui.buttonMain); fixBtn.onClick { fixAction?.invoke() }
        goodsBtn = TextButton(Lang.t("store_goods"), ui.button); goodsBtn.onClick { if (selected >= 0) openGoods(selected) }
        nbBtn = TextButton(Lang.t("nb_btn"), ui.button); nbBtn.onClick { openNeighbors() }
        hover(nbBtn) { Lang.t("nb_tip") }
        followBtn = TextButton("", ui.button); followBtn.onClick { following = !following; updateInfo() }
        homeBtn = TextButton(Lang.t("cz_home_btn"), ui.button)
        homeBtn.onClick { val a = agentIdx(); if (a >= 0) { val b = world.home[a]; clearAgent(); focus(b) } }
        workBtn = TextButton(Lang.t("cz_work_btn"), ui.button)
        workBtn.onClick { val a = agentIdx(); if (a >= 0 && world.jobB[a] >= 0) { val b = world.jobB[a]; clearAgent(); focus(b) } }
        upgradeBtn = TextButton(Lang.t("upg_btn"), ui.button); upgradeBtn.onClick { upgradePressed() }
        hover(upgradeBtn) { if (selected >= 0) upgradeText(selected) else "" }
        upgradeAllBtn = TextButton("", ui.button); upgradeAllBtn.onClick { upgradeAll() }
        hover(upgradeAllBtn) { Lang.t("upg_all_tip") }
        demolishBtn = TextButton(Lang.t("demolish"), ui.buttonDanger); demolishBtn.onClick { demolishPressed() }
        cancelSiteBtn = TextButton(Lang.t("cancel_site"), ui.buttonDanger)
        cancelSiteBtn.onClick { if (selected >= 0) { world.submit(Command.DEMOLISH, selected, 0, 0); selected = -1; updateInfo() } }
        undoBtn = TextButton(Lang.t("undo"), ui.button); undoBtn.onClick { undo() }
        rushBtn = TextButton("", ui.button)
        rushBtn.onClick {
            val b = selected
            if (b >= 0 && world.bState[b] == B_SITE) {
                if (world.gold < world.rushCost(b)) showToast(Lang.t("no_gold"), 2500)
                else { world.submit(Command.RUSH, b, 0, 0); Sfx.play("built"); updateInfo() }
            }
        }
        hover(rushBtn) { Lang.t("rush_tip") }
        hireBtn = TextButton("", ui.button)
        hireBtn.onClick {
            val n = minOf(Tune.HIRE_MAX, world.freeBeds())
            when {
                n == 0 -> showToast(Lang.t("hire_no_beds"), 2500)
                world.gold < world.hireCost() -> showToast(Lang.t("no_gold"), 2500)
                else -> { world.submit(Command.HIRE, n, 0, 0); Sfx.play("coin"); updateInfo() }
            }
        }
        hover(hireBtn) { Lang.t("hire_tip", world.hireCost()) }
        festivalBtn = TextButton("", ui.button)
        festivalBtn.onClick { festivalPressed() }
        hover(festivalBtn) { Lang.t("festival_tip", Tune.FESTIVAL_HAPPY, Tune.FESTIVAL_DAYS) }
        rotateBtn = ui.iconButton(region("hud_rotate"))
        rotateBtn.onClick { rotateGhost() }
        hover(rotateBtn) { Lang.t("rotate_building") + if (pc) " (T)" else "" }
        rotateWordBtn = iconText(Lang.t("rotate_word"), ui.button, "hud_rotate")
        rotateWordBtn.onClick { rotateGhost() }
        tplResetBtn = TextButton(Lang.t("tpl_again"), ui.button)
        tplResetBtn.onClick { tplReset(true); updateInfo() }
        tplLastBtn = TextButton(Lang.t("tpl_last"), ui.button)
        tplLastBtn.onClick { lastTpl?.let { tpl = it; tplStage = 3; ghostX = -1; ghostY = -1; tplRot = 0; tplKey = ""; updateInfo() } }
        roadRemoveBtn = TextButton(Lang.t("road_remove"), ui.toggle)
        roadRemoveBtn.onClick { roadRemove = roadRemoveBtn.isChecked; roadStart = -1; roadEnd = -1; updateRoadGhost(); updateInfo() }
        actions = Table()
        s.add(actions).right().padRight(4f).padBottom(4f)
        return s
    }

    private fun signed(n: Int) = (if (n > 0) "+" else "") + n
    /** Как налог меняет довольство: «+8», «-12» или «без изменений» */
    private fun taxHappy(level: Int): String = Tune.TAX_HAPPY[level].let { if (it == 0) Lang.t("tax_happy_same") else signed(it) }

    /** Кнопка с значком из атласа перед подписью */
    private fun iconText(text: String, style: TextButton.TextButtonStyle, icon: String): TextButton {
        val b = TextButton(text, style)
        val lbl = b.label
        b.clearChildren()
        named(icon)?.let { b.add(ui.image(it, 16f)).size(16f).padRight(3f) }
        b.add(lbl)
        return b
    }

    private fun showSheet(on: Boolean) {
        val want: Actor? = if (on) sheet else null
        if (sheetHolder.actor !== want) sheetHolder.setActor(want)
    }

    private fun closeSheet() {
        if (buildDef != -1) selectBuild(-1)
        selected = -1; tileCell = -1; clearAgent()
        updateInfo()
    }

    private fun clearAgent() { selSerial = -1; following = false; selIdx = -1 }

    /** Номер выбранного жителя в массивах мира (-1 - ушёл из города или не выбран) */
    private fun agentIdx(): Int {
        if (selSerial < 0) return -1
        if (selIdx in 0 until world.agentCount && world.aSerial[selIdx] == selSerial) return selIdx
        selIdx = -1
        for (a in 0 until world.agentCount) if (world.aSerial[a] == selSerial) { selIdx = a; break }
        return selIdx
    }

    private fun describeAgent(a: Int): String {
        val res = game.content.resources
        fun bn(b: Int) = if (b in 0 until world.buildingCount && world.bState[b] != B_GONE) Lang.t(world.def(b).name) else "?"
        val home = world.home[a]
        val jobB = world.jobB[a]
        val f = if (game.content.citizenFemale(world.seed, world.aSerial[a])) "_f" else ""
        val job = when (world.job[a]) {
            JOB_WORKER -> if (jobB >= 0) Lang.t("cz_job_worker", bn(jobB)) else Lang.t("cz_job_carrier$f")
            JOB_BUILDER -> Lang.t("cz_job_builder$f")
            else -> Lang.t("cz_job_carrier$f")
        }
        val act = when {
            world.state[a] == ST_INSIDE -> Lang.t("cz_act_inside", bn(world.where[a]))
            world.state[a] == ST_BUILD -> Lang.t("cz_act_build")
            world.state[a] == ST_GATHER -> Lang.t("cz_act_work")
            world.carryN[a] > 0 -> {
                val dst = if (world.taskDst[a] >= 0) world.taskDst[a] else world.goalB[a]
                Lang.t("cz_act_carry", world.carryN[a], Lang.t(res[world.carryR[a]].name), bn(dst))
            }
            world.goal[a] == GOAL_SRC -> Lang.t("cz_act_src", bn(world.taskSrc[a]))
            world.goal[a] == GOAL_HOME -> Lang.t("cz_act_home")
            world.goal[a] == GOAL_WORK || world.goal[a] == GOAL_GATHER || world.goal[a] == GOAL_BACK -> Lang.t("cz_act_going_work")
            world.goal[a] == GOAL_SITE -> Lang.t("cz_act_going_site")
            else -> Lang.t("cz_act_idle")
        }
        val sb = StringBuilder(game.content.citizenName(world.seed, world.aSerial[a], Lang.code))
        sb.append(NL).append(job)
        if (world.aSkill[a] != 0) sb.append(" · ").append(Lang.t("cz_skilled", Tune.SKILL_BONUS))
        sb.append(NL).append(act)
        sb.append(NL).append(Lang.t("cz_home", bn(home), if (home in 0 until world.buildingCount) world.bHappy[home] else 0))
        sb.append(NL).append(Lang.t("cz_arrived", world.aArrived[a]))
        sb.append(NL).append(Lang.t("cz_changes", world.aJobChanges[a]))
        sb.append(NL).append(Lang.t("cz_carried", world.aCarried[a]))
        if (world.aFavR[a] > 0) sb.append(NL).append(Lang.t("cz_fav", Lang.t(res[world.aFavR[a] - 1].name)))
        return sb.toString()
    }

    /** Задания: строка-предупреждение и список; свёрнуто - только первое задание */
    private fun buildQuests(landscape: Boolean): Table {
        val box = Table()
        box.background = ui.panel
        box.touchable = Touchable.enabled
        questLabel = Label("", ui.label)
        questLabel.setWrap(true)
        hintLabel = Label("", ui.labelSmall)
        hintLabel.setWrap(true)
        hintLabel.color = Color(0.92f, 0.86f, 0.72f, 1f)
        val w = if (landscape) 380f else Gdx.graphics.width / scale - 12f
        box.add(ui.image(region("hud_quest"), ICON)).size(ICON).top().padLeft(6f).padTop(8f)
        val col = Table()
        col.add(questLabel).width(w - 40f).left().row()
        hintHolder = Container<Actor>()
        hintHolder.fill()
        col.add(hintHolder).width(w - 40f).left()
        box.add(col).pad(6f)
        box.onClick { questOpen = !questOpen; questKey = ""; updateQuests() }
        return box
    }

    /** Меню паузы: страницы «главная», «сохранить» (слоты с описанием), «настройки», «как играть» */
    private fun buildMenu(): Table {
        val m = Table()
        m.setFillParent(true)
        m.background = ui.dim
        m.touchable = Touchable.enabled
        menuCol = Table()
        menuCol.background = ui.window
        menuCol.pad(14f)
        val pane = ScrollPane(menuCol)
        pane.setScrollingDisabled(true, false)
        pane.setOverscroll(false, false)
        m.add(pane).maxHeight(Gdx.graphics.height / scale - 24f)
        fillMenu()
        return m
    }

    private lateinit var menuCol: Table
    private var menuPage = "main"
    /** Слот, который просят перезаписать: второе нажатие в течение 3 с - сохранить */
    private var saveArmed = ""
    private var saveArmedUntil = 0L

    private fun fillMenu() {
        val col = menuCol
        col.clearChildren()
        val w = minOf(if (menuPage == "help") 460f else 300f, Gdx.graphics.width / scale - 60f)
        col.defaults().width(w).padBottom(8f)
        fun add(text: String, action: () -> Unit): TextButton {
            val b = TextButton(text, ui.button)
            b.label.setWrap(true)
            b.onClick(action)
            col.add(b).row()
            return b
        }
        fun back() = add(Lang.t("back")) { menuPage = "main"; fillMenu() }.also { col.getCell(it).padTop(8f) }
        fun head(key: String) { col.add(Label(Lang.t(key), ui.labelHead)).left().padBottom(10f).row() }
        when (menuPage) {
            "save" -> {
                head("save")
                for (slot in listOf("1", "2", "3")) {
                    val data = Storage.load(slot)
                    val what = if (data == null) Lang.t("slot_empty") else slotSummary(data)
                    val armed = saveArmed == slot && TimeUtils.millis() < saveArmedUntil
                    add(if (armed) Lang.t("save_over", Lang.t("slot", slot)) else if (data == null) "${Lang.t("slot", slot)} — $what" else "${Lang.t("slot", slot)}\n$what") {
                        // занятый слот - сначала спросить (второе нажатие перезаписывает)
                        if (data != null && !(saveArmed == slot && TimeUtils.millis() < saveArmedUntil)) {
                            saveArmed = slot; saveArmedUntil = TimeUtils.millis() + 3000; fillMenu()
                        } else {
                            Storage.save(slot, SaveCodec.write(world))
                            saveArmed = ""
                            menu.isVisible = false; menuPage = "main"
                            showToast(Lang.t("saved"))
                        }
                    }
                }
                // в файл: перенести город (браузер <-> приложение на экране «Домой», другое устройство), копия перед переустановкой
                game.saveFiles?.let { files ->
                    add(Lang.t("save_file")) {
                        files.export("hearthvale-${Lang.t("day", world.day).replace(" ", "")}.json", SaveCodec.write(world))
                        showToast(Lang.t("save_file_done"), 3000)
                    }
                }
                if (game.clipboardCodes) add(Lang.t("save_code")) {
                    val code = town.SaveCode.encode(SaveCodec.write(world))
                    Gdx.app.clipboard.contents = code
                    showToast(Lang.t("save_code_done", (code.length + 1023) / 1024), 5000)
                }
                back()
            }
            "settings" -> {
                head("settings")
                col.add(town.ui.volumeRow(ui, "vol_music", { Storage.musicVol }) { Storage.musicVol = it; Sfx.updateMusic() }).row()
                col.add(town.ui.volumeRow(ui, "vol_sound", { Storage.soundVol }) { Storage.soundVol = it }).row()
                add(Lang.t(if (Storage.colorblind) "cb_on" else "cb_off")) { Storage.colorblind = !Storage.colorblind; legendKey = ""; fillMenu() }
                add(Lang.t("gfx_" + Storage.graphics)) {
                    Storage.graphics = when (Storage.graphics) { "auto" -> "high"; "high" -> "low"; else -> "auto" }
                    Storage.autoLow = false
                    game.applyGraphics()
                    fillMenu()
                }
                // язык меняет все надписи - раскладка пересобирается, меню остаётся открытым
                add(Lang.t("language")) { game.toggleLanguage(); buildHud(); menu.isVisible = true }
                back()
            }
            "help" -> {
                head("help")
                val text = Label(Lang.t("help_basics") + NL + NL + Lang.t(if (pc) "help_pc" else "help_touch") + NL + NL + Lang.t("help_more") + NL + NL + Lang.t("help_life") + NL + Lang.t(if (pc) "help_more_pc" else "help_more_touch"), ui.label)
                text.setWrap(true)
                col.add(text).row()
                back()
            }
            else -> {
                add(Lang.t("resume")) { menu.isVisible = false }
                add(Lang.t("trader")) { menu.isVisible = false; openTrade() }
                add(Lang.t("save") + "…") { menuPage = "save"; fillMenu() }
                add(Lang.t("settings") + "…") { menuPage = "settings"; fillMenu() }
                add(Lang.t("summary")) { menu.isVisible = false; openSummary() }
                add(Lang.t("photo")) { enterPhoto() }
                add(Lang.t("help")) { menuPage = "help"; fillMenu() }
                // код карты песочницы: друг вобьёт его в «Своя карта» и получит ту же долину
                world.mapCode()?.let { code ->
                    add(Lang.t("map_code", code)) {
                        if (game.clipboardCodes) { Gdx.app.clipboard.contents = code; showToast(Lang.t("map_code_copied"), 4000) }
                        // в браузере буфер недоступен - системное поле с кодом: его можно выделить и скопировать
                        else Gdx.input.getTextInput(object : com.badlogic.gdx.Input.TextInputListener {
                            override fun input(text: String?) {}
                            override fun canceled() {}
                        }, Lang.t("map_code_title"), code, "")
                    }
                }
                add(Lang.t("to_title")) { autosave(); game.showTitle() }
            }
        }
    }

    /** «64×64, день 12, жителей 40» - что лежит в слоте */
    private fun slotSummary(data: String): String = try {
        val s = SaveCodec.summary(data)
        Lang.t("slot_info", s[0], s[1] / Tune.TICKS_PER_DAY + 1, s[2])
    } catch (e: Exception) {
        "?"
    }

    private fun openMenu() {
        menuPage = "main"
        fillMenu()
        menu.isVisible = true
    }

    /** Справка «Как играть» (меню; в первой песочнице открывается сама) */
    private fun openHelp() {
        menuPage = "help"
        fillMenu()
        menu.isVisible = true
    }

    // ---------- верхняя панель ----------

    private fun setPaused(p: Boolean) {
        if (p && !paused) showToast(Lang.t("pause_hint"), 3000)
        paused = p
        refreshTop()
    }

    /** Числа и значки верхней панели (ширины фиксированы - раскладка не двигается) */
    private fun refreshTop() {
        seasonImg.drawable = TextureRegionDrawable(if (!pc && !world.isNight && world.weather != W_CLEAR) weatherRegion(world.weather)
            else region(if (world.isNight) "hud_night" else "hud_season_${world.season}"))
        if (weatherShown != world.weather) { weatherShown = world.weather; weatherImg.drawable = TextureRegionDrawable(weatherRegion(world.weather)) }
        dayLbl.setText(world.day.toString())
        happyLbl.setText("${world.townHappy}%")
        goldLbl.setText(short(world.gold))
        goldLbl.color = fcColor(world.forecastLevel(FC_GOLD))
        // «жители/кровати» помещается до 99/99; в большом городе - только жители (кровати - в подсказке)
        val beds = world.totalBeds()
        // на телефоне - только жители (кровати - в подсказке): строка не шире экрана
        popLbl.setText("${world.agentCount}")
        val canCall = world.canCallTrader()
        callLbl.setText(world.traderCallPrice().toString())
        callChip.color.a = if (canCall) 1f else 0.5f
        if (world.traderShip != shipShown) {
            shipShown = world.traderShip
            traderChipIcon.drawable = TextureRegionDrawable(region(if (shipShown && named("hud_ship") != null) "hud_ship" else "hud_trader"))
            traderChipLbl.setText(Lang.t(if (shipShown) "ship" else "trader"))
        }
        val trader: Actor? = if (world.traderHere) traderChipActor else if (!world.isNight) callChip else null
        if (traderHolder.actor !== trader) traderHolder.setActor(trader)
        (pauseBtn.style as ImageButton.ImageButtonStyle).imageUp =
            TextureRegionDrawable(region(if (paused) "hud_play" else "hud_pause")).apply { setMinSize(24f, 24f) }
        speedBtn.setText("×${speeds[speedIdx]}")
        refreshResources()
    }

    /** 12345 -> «12.3k», чтобы число влезало в фиксированную ширину */
    private fun short(n: Int): String = if (n < 10000) n.toString() else "${n / 1000}.${n % 1000 / 100}k"

    private fun refreshResources() {
        val res = game.content.resources
        // ресурс, который хоть раз был в городе, остаётся в сетке и при нуле - раскладка не прыгает
        // уже бывал в городе: сейчас есть, делали, тратили или лежал на складах в какие-то сутки (и после загрузки)
        for (r in res.indices) if (r !in seenRes && (world.totalStock(r) > 0 || world.produced(r) > 0 || world.used(r) > 0 || world.madeTotal[r] > 0 || hadStock(r))) seenRes.add(r)
        val shown = res.indices.filter { it in seenRes }
        // телефон лёжа: ресурсы в первой строке, пока влезают в две строчки, иначе - своей строкой во всю ширину
        if (resInlineW > 0f) {
            val inline = shown.size <= 2 * maxOf(3, (resInlineW / RES_MIN_W).toInt())
            val holder = if (inline) resInline else resLine
            if (holder.actor !== resRow) {
                resRow.remove()
                holder.setActor(resRow)
                resKey = ""
            }
            resAreaW = if (inline) resInlineW else resFullW
        }
        // телефон: строки ровные - ячейки чуть уже (до RES_MIN_W), лишь бы не оставался один ресурс на последней строке
        var cols = resCols
        var chipW = resChipW
        if (resAreaW > 0f && shown.isNotEmpty()) {
            val most = maxOf(3, (resAreaW / RES_MIN_W).toInt())
            val rows = (shown.size + most - 1) / most
            cols = maxOf(3, (shown.size + rows - 1) / rows)
            chipW = kotlin.math.floor(resAreaW / cols)
        }
        val key = shown.joinToString(",") + "/$cols"
        if (key != resKey) {
            resKey = key
            resRow.clearChildren(); resLabels.clear(); resTrend.clear()
            for ((i, r) in shown.withIndex()) {
                // значок и число - одна кнопка: касание показывает, что это за ресурс
                val icon = sprites.named("res_${res[r].id}")?.r ?: region("white")
                val c = Table()
                c.touchable = Touchable.enabled
                c.left()
                // ПК: экран большой - значок и число крупнее (на телефоне строка узкая - мелким шрифтом)
                val iconSz = if (pc) RES_ICON_PC else 18f
                c.add(ui.image(icon, iconSz)).size(iconSz).padRight(if (pc) 3f else 2f)
                val l = Label("", if (pc) ui.label else ui.labelSmall)
                c.add(l).left()
                // стрелка вплотную к числу - видно, к какому ресурсу она относится
                val trend = ui.image(region("white"), 11f)
                c.add(trend).size(11f).padLeft(1f)
                c.add().expandX()
                tip(c, { resTip(r) }) {
                    if (pc) openChain(r)
                    else if (chainArm == r && TimeUtils.millis() < chainArmUntil) { chainArm = -1; openChain(r) }
                    else { chainArm = r; chainArmUntil = TimeUtils.millis() + 3000; showToast(resTip(r) + NL + Lang.t("chain_again"), 3000) }
                }
                resRow.add(c).height(if (pc) RES_ROW_H_PC else RES_ROW_H).width(chipW).left()
                if ((i + 1) % cols == 0) resRow.row()
                resLabels[r] = l
                resTrend[r] = trend
            }
        }
        val fcLevel = IntArray(FC_GOLD) { world.forecastLevel(it) }
        for ((r, l) in resLabels) {
            l.setText(short(world.totalStock(r)))
            val k = world.forecastKindOf(r)
            l.color = fcColor(if (k >= 0) fcLevel[k] else 0)
        }
        // стрелка: за вчера произвели больше, чем потратили (вверх) или меньше (вниз)
        for ((r, img) in resTrend) {
            val net = world.produced(r) - world.used(r)
            val name = if (world.histDays == 0 || net == 0) null else if (net > 0) "hud_up" else "hud_down"
            img.isVisible = name != null
            if (name != null) img.drawable = TextureRegionDrawable(region(name))
        }
    }

    private fun hadStock(r: Int): Boolean {
        val n = minOf(world.dayCount, Tune.HISTORY_RES)
        for (d in 0 until n) if (world.dayStock[d * game.content.resources.size + r] > 0) return true
        return false
    }

    private fun resTip(r: Int): String {
        val name = Lang.t(game.content.resources[r].name)
        val left = world.daysLeft(r)
        return (if (world.histDays == 0) Lang.t("tip_res", name, world.totalStock(r))
        else Lang.t("tip_res_day", name, world.totalStock(r), world.produced(r), world.used(r))) +
            (if (left >= 0) NL + Lang.t("tip_res_left", left) else "") + if (pc) NL + Lang.t("chain_click") else ""
    }

    /** Цвет числа по прогнозу: красный - хватит меньше чем на 3 суток, жёлтый - меньше чем на 7 */
    private fun fcColor(level: Int): Color = when (level) {
        2 -> Palette.bad()
        1 -> Color(1f, 0.85f, 0.35f, 1f)
        else -> Color.WHITE
    }

    // ---------- фильтр склада ----------

    private lateinit var goodsWin: Table
    private lateinit var goodsBody: Table
    private var goodsB = -1

    // ---------- погода ----------

    private var weatherShown = -1

    private fun weatherRegion(w: Int) = named("hud_weather_$w") ?: region("hud_season_1")

    /** Подсказка погоды: сегодня и что она меняет, прогноз на завтра; паводок */
    private fun weatherTip(): String {
        val sb = StringBuilder(Lang.t("weather_today", Lang.t("weather_${world.weather}")))
        val eff = weatherEffects(world.weather)
        if (eff.isNotEmpty()) sb.append(": ").append(eff)
        sb.append(NL).append(Lang.t("weather_tomorrow", Lang.t("weather_${world.weatherNext}")))
        if (world.floodLeft > 0) sb.append(NL).append(Lang.t("flood_on", dur(world.floodLeft)))
        else if (world.floodYear == world.year && world.season != WINTER) sb.append(NL).append(Lang.t("flood_after", Tune.FLOOD_BONUS))
        return sb.toString()
    }

    private fun weatherEffects(w: Int): String = when (w) {
        W_RAIN -> Lang.t("weather_eff_1", Tune.RAIN_FIELDS, 100 - Tune.RAIN_WALK)
        W_SNOW -> Lang.t("weather_eff_2", 100 - Tune.SNOW_WALK)
        W_FOG -> Lang.t("weather_eff_3", Tune.FOG_GUARD)
        W_DROUGHT -> Lang.t("weather_eff_4", Tune.DROUGHT_SOIL)
        else -> ""
    }

    // ---------- дети ----------

    /** Дети дома: сколько, когда вырастут, учатся ли (школа рядом) */
    private fun kidsLine(b: Int): String {
        val ages = (0 until Tune.KIDS_MAX).map { world.kidAge[b * Tune.KIDS_MAX + it] }.filter { it > 0 }.sorted()
        if (ages.isEmpty()) return MUTED + Lang.t(if (world.level < Tune.KID_LEVEL) "kids_none_level" else "kids_none", Tune.KID_HAPPY) + "[]"
        val school = world.served(b, "school")
        return Lang.t("kids_line", ages.size, ages.joinToString(", ")) + ". " + Lang.t(if (school) "kids_school" else "kids_no_school")
    }

    // ---------- соседи ----------

    private lateinit var nbWin: Table
    private lateinit var nbBody: Table
    private var nbRefreshAt = 0L

    private fun nbName(i: Int) = if (i in 0 until world.nbCount) game.content.villageName(world.nbName[i], Lang.code) else "?"

    private fun sideName(i: Int) = Lang.t("nb_side_${world.nbSide[i]}")

    /** Товар и количество посреди фразы: «доски 20» / "20 planks", «золото 35» / "35 gold" */
    private fun goodsText(r: Int, n: Int) = if (r < 0) Lang.t("gold_n", n) else Lang.t("goods_n", n, resLower(r))

    /** Название товара посреди фразы - со строчной */
    private fun resLower(r: Int) = Lang.lower(Lang.t(game.content.resources[r.coerceIn(0, game.content.resources.size - 1)].name))

    /** Строки панели торгового поста: договоры и повозки */
    private fun tradeLines(b: Int): String {
        val sb = StringBuilder()
        var any = false
        for (i in 0 until world.nbCount) {
            if (world.ctGiveR[i] < 0 && world.wgState[i] == 0) continue
            if (any) sb.append(NL)
            any = true
            sb.append(nbName(i)).append(": ")
            if (world.ctGiveR[i] >= 0) sb.append(Lang.t("ct_line", goodsText(world.ctGiveR[i], world.ctGiveN[i]), goodsText(world.ctGetR[i], world.ctGetN[i]), world.ctDue[i],
                minOf(world.ctGiveN[i], world.stock(b, world.ctGiveR[i])), world.ctGiveN[i]))
            if (world.wgState[i] != 0) sb.append(" · ").append(Lang.t("wagon_state_${world.wgState[i]}"))
        }
        return if (any) sb.toString() else Lang.t("trade_none")
    }

    private fun openNeighbors() {
        nbWin.isVisible = true
        fillNeighbors()
    }

    /** Окно соседей: по каждому селу - где, отношения, чего много и что нужно, договор или два предложения */
    private fun fillNeighbors() {
        val body = nbBody
        body.clearChildren()
        val w = bodyWidth[body] ?: 360f
        fun text(t: String, color: Color? = null): Label {
            val l = Label(t, ui.labelSmall); l.setWrap(true); if (color != null) l.color = color; return l
        }
        val muted = Color(0.85f, 0.78f, 0.66f, 1f)
        body.add(text(Lang.t(if (world.hasTradePost()) "nb_help" else "nb_help_post", Tune.CONTRACT_DAYS), muted)).width(w).left().padBottom(6f).row()
        for (i in 0 until world.nbCount) {
            val box = Table()
            box.background = ui.chip
            box.pad(6f)
            val iw = w - 24f
            val head = Table()
            named("hud_neighbors")?.let { head.add(ui.image(it, 20f)).size(20f).padRight(4f) }
            head.add(Label(nbName(i), ui.labelBold)).left()
            head.add(Label("  " + sideName(i), ui.labelSmall)).left().expandX()
            val rel = world.nbRel[i]
            val relLbl = Label(Lang.t("nb_rel", rel), ui.labelSmall)
            relLbl.color = if (rel >= Tune.REL_GOOD) Color(0.66f, 0.88f, 0.56f, 1f) else if (rel < Tune.REL_START) Color(1f, 0.6f, 0.45f, 1f) else Color.WHITE
            head.add(relLbl).right()
            box.add(head).width(iw).left().row()
            box.add(text(Lang.t("nb_goods", Lang.t(game.content.resources[world.nbSurplus[i]].name), Lang.t(game.content.resources[world.nbNeed[i]].name)))).width(iw).left().row()
            if (world.ctGiveR[i] >= 0) {
                box.add(text(Lang.t("ct_line", goodsText(world.ctGiveR[i], world.ctGiveN[i]), goodsText(world.ctGetR[i], world.ctGetN[i]), world.ctDue[i],
                    minOf(world.ctGiveN[i], maxOf(0, world.stockAvailable(world.ctGiveR[i]))), world.ctGiveN[i]), Color(0.96f, 0.82f, 0.48f, 1f))).width(iw).left().row()
                if (world.wgState[i] != 0) box.add(text(Lang.t("wagon_state_${world.wgState[i]}"))).width(iw).left().row()
                if (world.ctFails[i] > 0) box.add(text(Lang.t("ct_failed_once"), Color(1f, 0.6f, 0.45f, 1f))).width(iw).left().row()
                val cancel = TextButton(Lang.t("ct_cancel"), ui.buttonDanger)
                cancel.onClick { world.submit(Command.CONTRACT, i, -1, 0); fillNeighbors(); updateInfo() }
                box.add(cancel).height(44f).right().padTop(4f).row()
            } else for (k in 0..1) {
                val o = i * 2 + k
                if (world.ofGiveR[o] < 0) continue
                val line = Table()
                // что у города есть из отдаваемого: запас и выработка за неделю; нет ни того, ни другого - приглушено
                val g = world.ofGiveR[o]
                val have = maxOf(0, world.stockAvailable(g))
                val week = weeklyMade(g)
                val offer = Table()
                offer.add(text(Lang.t("nb_offer", goodsText(g, world.ofGiveN[o]), goodsText(world.ofGetR[o], world.ofGetN[o]), Tune.CONTRACT_DAYS).replaceFirstChar { it.uppercaseChar() })).width(iw - 122f).left().row()
                offer.add(text(Lang.t("nb_have", have, week), muted)).width(iw - 122f).left()
                if (have < world.ofGiveN[o] && week == 0) offer.color = Color(1f, 1f, 1f, 0.5f)
                line.add(offer).width(iw - 122f).left()
                val sign = TextButton(Lang.t("ct_sign"), ui.buttonMain)
                sign.color = if (world.contractBlock(i, k) == null) Color.WHITE else Color(1f, 1f, 1f, 0.5f)
                sign.onClick {
                    val why = world.contractBlock(i, k)
                    if (why != null) showToast(Lang.t(why, Tune.CONTRACTS_MAX))
                    else { world.submit(Command.CONTRACT, i, k, 0); Sfx.play("quest"); fillNeighbors(); updateInfo() }
                }
                line.add(sign).width(116f).height(44f).padLeft(6f)
                box.add(line).width(iw).padTop(3f).row()
            }
            body.add(box).width(w).padBottom(6f).row()
        }
        body.add(text(Lang.t("nb_next_offers", world.offersDay), muted)).width(w).left().row()
    }

    /** Сколько товара r город делает за неделю - по статистике последних суток */
    private fun weeklyMade(r: Int): Int {
        if (world.histDays == 0) return 0
        var n = 0
        for (d in 1..world.histDays) n += world.produced(r, d)
        return n * Tune.SEASON_DAYS / world.histDays
    }

    private fun openGoods(b: Int) {
        goodsB = b
        goodsWin.isVisible = true
        fillGoods()
    }

    private fun fillGoods() {
        val b = goodsB
        if (b < 0 || world.bState[b] != B_ACTIVE || world.def(b).role != "store") { goodsWin.isVisible = false; return }
        val res = game.content.resources
        val body = goodsBody
        body.clearChildren()
        val w = bodyWidth[body] ?: 360f
        val help = Label(Lang.t("store_goods_help"), ui.labelSmall)
        help.setWrap(true)
        help.color = Color(0.85f, 0.78f, 0.66f, 1f)
        body.add(help).width(w).left().padBottom(6f).row()
        val presets = listOf(-1 to "store_p_all", -2 to "store_p_none", -3 to "store_p_food", -4 to "store_p_build", -5 to "store_p_craft")
        val pw = (w - 6f * 3) / 3f
        val pt = Table()
        for ((i, p) in presets.withIndex()) {
            val btn = TextButton(Lang.t(p.second), ui.buttonTight)
            btn.onClick { world.submit(Command.STORE_ACCEPT, b, p.first, 1); fillGoods(); updateInfo() }
            pt.add(btn).width(pw).height(44f).pad(3f)
            if (i == 2) pt.row()
        }
        body.add(pt).width(w).padBottom(6f).row()
        val cols = maxOf(1, (w / 76f).toInt())
        val cw = w / cols
        val grid = Table()
        for (r in res.indices) {
            val on = world.bAccept[b * world.R + r] != 0
            val cell = Table()
            cell.background = ui.chip
            cell.touchable = Touchable.enabled
            sprites.named("res_${res[r].id}")?.let { cell.add(ui.image(it.r, 32f)).size(32f).padTop(4f).row() }
            val name = Label(Lang.t(res[r].name), ui.labelSmall)
            name.setAlignment(Align.center)
            name.setEllipsis(true)
            cell.add(name).width(cw - 10f).padBottom(4f)
            cell.color = if (on) Color.WHITE else Color(1f, 0.55f, 0.5f, 0.35f)
            cell.onClick { world.submit(Command.STORE_ACCEPT, b, r, if (on) 0 else 1); fillGoods(); updateInfo() }
            grid.add(cell).width(cw - 6f).height(64f).pad(3f)
            if ((r + 1) % cols == 0) grid.row()
        }
        body.add(grid).width(w).row()
    }

    // ---------- слои на карте ----------

    private lateinit var layersWin: Table
    private lateinit var layersBody: Table
    private lateinit var layersChip: Table
    private lateinit var legendHolder: Container<Actor>
    private lateinit var legendBox: Table
    private lateinit var legendLabel: Label
    private var legendKey = ""

    private fun setLayer(ov: Int, svc: String?) {
        renderer.overlay = ov; renderer.overlayService = svc; autoOverlay = false
        layersWin.isVisible = false
        refreshLegend()
    }

    private fun openLayers() {
        layersWin.isVisible = true
        val body = layersBody
        body.clearChildren()
        val w = bodyWidth[body] ?: 360f
        // выбор, а не список: два раздела, кнопки сеткой в 2 колонки (на ПК - в 3)
        val cols = if (pc) 3 else 2
        val cw = (w - 4f * (cols - 1)) / cols
        fun section(key: String, items: List<Triple<String, Int, String?>>) {
            body.add(Label(Lang.t(key), ui.labelHead)).left().padTop(4f).padBottom(4f).row()
            val grid = Table()
            for ((i, it) in items.withIndex()) {
                val (text, ov, svc) = it
                val b = TextButton(text, ui.toggle)
                b.label.setEllipsis(true)
                b.isChecked = renderer.overlay == ov && (ov != WorldRenderer.OV_SERVICE || renderer.overlayService == svc) && !autoOverlay
                b.onClick { setLayer(ov, svc) }
                grid.add(b).width(cw).padRight(if ((i + 1) % cols == 0) 0f else 4f).padBottom(4f)
                if ((i + 1) % cols == 0) grid.row()
            }
            body.add(grid).width(w).left().row()
        }
        // «Услуги: рынок» -> «Рынок»: раздел уже называется «Услуги»
        fun svcName(key: String) = Lang.t(key).substringAfter(": ").let { s -> if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1) }
        section("layers_map", listOf(
            Triple(Lang.t("layer_none"), WorldRenderer.OV_NONE, null),
            Triple(Lang.t("layer_happy"), WorldRenderer.OV_HAPPY, null),
            Triple(Lang.t("layer_traffic"), WorldRenderer.OV_TRAFFIC, null),
            Triple(Lang.t("layer_soil"), WorldRenderer.OV_SOIL, null)))
        section("layers_services", listOf(Triple(svcName("layer_service_all"), WorldRenderer.OV_SERVICE, null)) +
            SERVICE_LAYERS.map { Triple(svcName("layer_svc_$it"), WorldRenderer.OV_SERVICE, it) })
        if (!pc) {
            val rot = TextButton(Lang.t("rotate_map"), ui.button)
            rot.onClick { rotateMap(1) }
            body.add(rot).width(w).padTop(8f).row()
        }
    }

    private fun cycleLayer(off: Boolean) {
        val next = if (off) WorldRenderer.OV_NONE else (renderer.overlay + 1) % 5
        setLayer(next, null)
        showToast(Lang.t(if (next == WorldRenderer.OV_NONE) "layer_none" else layerLegendKey(next)), 1500)
    }

    private fun layerLegendKey(ov: Int): String {
        val cb = if (Storage.colorblind) "_cb" else ""
        return when (ov) {
            WorldRenderer.OV_HAPPY -> "leg_happy$cb"
            WorldRenderer.OV_SERVICE -> "leg_service"
            WorldRenderer.OV_TRAFFIC -> "leg_traffic$cb"
            else -> "leg_soil$cb"
        }
    }

    /** Плашка под панелью заданий: какой слой включён и что значат цвета, с крестиком */
    private fun buildLegend(): Table {
        val t = Table()
        t.background = ui.panel
        t.touchable = Touchable.enabled
        legendLabel = Label("", ui.labelSmall)
        legendLabel.setWrap(true)
        val w = if (Gdx.graphics.width > Gdx.graphics.height) 380f else Gdx.graphics.width / scale - 12f
        t.add(legendLabel).width(w - 64f).left().pad(6f)
        val x = TextButton("×", ui.button)
        x.onClick { setLayer(WorldRenderer.OV_NONE, null) }
        t.add(x).size(TOUCH).padRight(2f)
        return t
    }

    private fun refreshLegend() {
        val ov = renderer.overlay
        val key = "$ov:${renderer.overlayService}:${Lang.code}:${Storage.colorblind}"
        if (key == legendKey) return
        legendKey = key
        if (ov == WorldRenderer.OV_NONE) { legendHolder.setActor(null); layersChip.color = Color.WHITE; return }
        val svc = renderer.overlayService
        legendLabel.setText(if (ov == WorldRenderer.OV_SERVICE) Lang.t("leg_service", if (svc == null) Lang.t("layer_service_all") else Lang.t("layer_svc_$svc"))
            else Lang.t(layerLegendKey(ov)))
        legendHolder.setActor(legendBox)
        layersChip.color = Color(1f, 0.85f, 0.45f, 1f)
    }

    // ---------- цепочка товара ----------

    private lateinit var chain: Table
    private lateinit var chainBody: Table
    private var chainRes = -1
    private var chainAt = 0L
    private var chainArm = -1
    private var chainArmUntil = 0L

    private fun openChain(r: Int) {
        chainRes = r
        chain.isVisible = true
        chainAt = TimeUtils.millis() + 1000
        fillChain()
    }

    /** Ближайшее к центру экрана здание этого типа (готовое или стройка); -1 - нет */
    private fun nearestOf(def: Int): Int {
        val center = renderer.pick(Gdx.graphics.width / 2, Gdx.graphics.height / 2)
        val cx = if (center >= 0) center % world.w else world.w / 2
        val cy = if (center >= 0) center / world.w else world.h / 2
        var best = -1; var bd = Int.MAX_VALUE
        for (b in 0 until world.buildingCount) {
            if (world.bDef[b] != def || (world.bState[b] != B_ACTIVE && world.bState[b] != B_SITE)) continue
            val d = Math.abs(world.bX[b] - cx) + Math.abs(world.bY[b] - cy)
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    /** Окно «Откуда и куда» по товару: кто делает, кому нужен, сводка за вчера */
    private fun fillChain() {
        val r = chainRes
        if (r < 0) return
        val res = game.content.resources
        val body = chainBody
        body.clearChildren()
        val w = bodyWidth[body] ?: 360f
        val head = Table()
        sprites.named("res_${res[r].id}")?.let { head.add(ui.image(it.r, 30f)).size(30f).padRight(6f) }
        head.add(Label(Lang.t(res[r].name), ui.labelHead)).left()
        head.add().expandX()
        head.add(Label(Lang.t("chain_stock", world.totalStock(r)), ui.labelBold)).right()
        body.add(head).width(w).padBottom(4f).row()

        fun section(key: String) { body.add(Label(Lang.t(key), ui.labelHead)).width(w).left().padTop(8f).padBottom(3f).row() }
        fun note(text: String, color: Color? = null) {
            val l = Label(text, ui.label)
            l.setWrap(true)
            if (color != null) l.color = color
            body.add(l).width(w).left().padBottom(3f).row()
        }
        fun icons(t: Table, list: List<Int>) { for (g in list) sprites.named("res_${res[g].id}")?.let { t.add(ui.image(it.r, 22f)).size(22f).padRight(3f) } }
        /** Строка здания: картинка, название, сколько готово/работает/стоит, входы или выходы; касание - к ближайшему */
        fun buildingRow(d: town.data.BuildingDef, showInputs: Boolean) {
            var ready = 0; var sites = 0; var working = 0
            val stopped = LinkedHashMap<String, Int>()
            for (b in 0 until world.buildingCount) {
                if (world.bDef[b] != d.index) continue
                if (world.bState[b] == B_SITE) sites++
                else if (world.bState[b] == B_ACTIVE) {
                    ready++
                    if (world.bStatus[b] == BS_OK && world.bWorkers[b] > 0) working++
                    else { val k = if (world.bUnpaid[b] != 0) "st_unpaid" else statusKey(world.bStatus[b]); stopped[k] = (stopped[k] ?: 0) + 1 }
                }
            }
            val row = Table()
            row.background = ui.chip
            row.touchable = Touchable.enabled
            row.add(Image(TextureRegionDrawable(sprites.cards[d.index].r), com.badlogic.gdx.utils.Scaling.fit)).size(44f, 38f).pad(3f)
            val col = Table()
            col.add(Label(Lang.t(d.name), ui.labelBold)).left().expandX()
            val ic = Table()
            icons(ic, (if (showInputs) d.input.indices.filter { d.input[it] > 0 } else d.output.indices.filter { d.output[it] > 0 }))
            col.add(ic).right().row()
            val none = ready == 0 && sites == 0
            val stat = if (none) Lang.t(if (world.unlocked(d.index)) "chain_none" else "chain_locked")
                else Lang.t("chain_count", ready, working) + (if (sites > 0) ", " + Lang.t("chain_sites", sites) else "")
            val sl = Label(stat, ui.labelSmall)
            sl.setWrap(true)
            if (none) sl.color = Color(0.80f, 0.74f, 0.64f, 1f)
            col.add(sl).colspan(2).left().expandX().fillX().row()
            if (stopped.isNotEmpty()) {
                val st = Label(Lang.t("chain_stopped", stopped.entries.joinToString(", ") { Lang.t(it.key).lowercase() + (if (it.value > 1) " ×${it.value}" else "") }), ui.labelSmall)
                st.setWrap(true)
                st.color = Color(1f, 0.69f, 0.44f, 1f)
                col.add(st).colspan(2).left().expandX().fillX()
            }
            row.add(col).expandX().fillX().pad(3f)
            tip(row, { Lang.t(d.name) }) {
                val b = nearestOf(d.index)
                if (b >= 0) { chain.isVisible = false; stats.isVisible = false; focus(b) }
                else if (world.unlocked(d.index) && d.buildable) { chain.isVisible = false; stats.isVisible = false; cardTapped(d.index) }
                else showToast(Lang.t("chain_locked"), 2000)
            }
            body.add(row).width(w).padBottom(3f).row()
        }

        section("chain_makers")
        val makers = game.content.buildings.filter { it.output[r] > 0 && (it.buildable || world.bDef.take(world.buildingCount).contains(it.index)) }
        if (makers.isEmpty()) note(Lang.t("chain_nobody"), Color(0.80f, 0.74f, 0.64f, 1f))
        for (d in makers) buildingRow(d, true)

        section("chain_users")
        val users = game.content.buildings.filter { it.input[r] > 0 && (it.buildable || world.bDef.take(world.buildingCount).contains(it.index)) }
        for (d in users) buildingRow(d, false)
        // потребности домов
        for (n in game.content.needs) {
            if (r !in n.goods || n.level > world.level) continue
            var houses = 0; var lack = 0
            for (h in 0 until world.buildingCount) {
                if (world.bState[h] != B_ACTIVE || !world.def(h).isHouse || world.bResidents[h] == 0 || n.tier > world.def(h).tier) continue
                houses++
                if (world.bNeedOk[h] and (1 shl n.index) == 0) lack++
            }
            note(Lang.t("chain_need", Lang.t(n.name), houses, lack), if (lack > 0) Color(1f, 0.69f, 0.44f, 1f) else null)
        }
        val pl = world.pledged(r)
        if (pl > 0) note(Lang.t("chain_pledged", pl))
        if (world.crR == r) note("[#F5D27A]" + crownLine() + "[]")
        if (users.isEmpty() && pl == 0 && world.crR != r && game.content.needs.none { r in it.goods }) note(Lang.t("chain_nouse"), Color(0.80f, 0.74f, 0.64f, 1f))

        body.add(Label(if (world.histDays == 0) Lang.t("st_first_day") else Lang.t("chain_yesterday", world.produced(r), world.used(r), world.totalStock(r)), ui.labelMuted).also { it.setWrap(true) })
            .width(w).left().padTop(10f).row()
    }

    /** Клавиша C: цепочка товара, который делает (или берёт) выбранное здание */
    private fun chainOfSelected() {
        val b = selected
        if (b < 0 || world.bState[b] == B_GONE) { showToast(Lang.t("chain_pick"), 2500); return }
        val d = world.def(b)
        val r = d.output.indices.firstOrNull { d.output[it] > 0 } ?: d.input.indices.firstOrNull { d.input[it] > 0 }
        if (r == null) { showToast(Lang.t("chain_pick"), 2500); return }
        openChain(r)
    }

    // ---------- торговец ----------

    /**
     * Окно торговца - таблица: товар, запас, цены, сколько у торговца; под ней (телефон) или справа (ПК) кнопки
     * купить/продать и лимит автопродажи. Пока торговца нет - сверху сказано, почему кнопки неактивны.
     */
    private fun buildTrade(): Table {
        val m = Table()
        m.setFillParent(true)
        m.background = ui.dim
        m.touchable = Touchable.enabled
        val col = Table()
        col.background = ui.window
        col.pad(12f)
        val wideTrade = Gdx.graphics.width / scale >= 760f
        wideTradeRows = wideTrade
        val w = if (wideTrade) 860f else minOf(380f, Gdx.graphics.width / scale - 24f)
        tradeTitle = Label("", ui.labelHead)
        tradeTitle.setWrap(true)
        col.add(tradeTitle).width(w).left().row()
        tradeState = Label("", ui.label)
        tradeState.setWrap(true)
        col.add(tradeState).width(w).left().padTop(2f).row()
        val help = Label(Lang.t("trade_help") + if (wideTrade) "" else " " + Lang.t("trade_pick"), ui.labelSmall)
        help.setWrap(true)
        help.color = Color(0.85f, 0.78f, 0.66f, 1f)
        col.add(help).width(w).left().padBottom(6f).row()
        // колонки чисел: запас, купить, продать, у торговца
        // телефон: колонка числа не уже самого длинного слова заголовка («торговца» крупным текстом не ломается),
        // названия - под самое длинное, сколько останется; остаток поровну числам
        val headKeys = listOf("tr_stock", "tr_buy", "tr_sell", "tr_has")
        val minCol = headKeys.map { k -> maxOf(48f, kotlin.math.ceil(Lang.t(k).split(' ').maxOf { Label(it, ui.labelSmall).prefWidth } + 6f)) }
        val longest = game.content.resources.maxOf { Label(Lang.t(it.name), ui.label).prefWidth }
        val nameW = if (wideTrade) 150f else kotlin.math.ceil(longest + 26f).coerceIn(108f, maxOf(108f, w - 8f - minCol.sum()))
        val spare = maxOf(0f, w - 8f - nameW - minCol.sum()) / 4f
        val colW = if (wideTrade) List(4) { 70f } else minCol.map { it + spare }
        fun head(key: String): Label = Label(Lang.t(key), ui.labelSmall).also { it.setAlignment(Align.center); it.color = town.ui.Ui.GOLD; it.setWrap(true) }
        val heads = Table()
        heads.add().width(nameW)
        for ((i, k) in headKeys.withIndex()) heads.add(head(k)).width(colW[i])
        col.add(heads).left().row()
        val list = Table()
        list.top().left()
        tradeList = list
        tradeLines.clear(); tradeOrderKey = ""; tradeListW = w
        tradeRows.clear()
        tradeHolders.clear()
        var odd = false
        for (res in game.content.resources) {
            if (res.price == 0) continue
            val r = res.index
            val bst = if (wideTrade) ui.button else ui.buttonTight
            val buy = TextButton(Lang.t("buy_n", TRADE_STEP), bst)
            buy.onClick { if (!buy.isDisabled) world.submit(Command.TRADE, r, TRADE_STEP, 0) }
            val sell = TextButton(Lang.t("sell_n", TRADE_STEP), bst)
            sell.onClick { if (!sell.isDisabled) world.submit(Command.TRADE, r, -TRADE_STEP, 0) }
            val less = TextButton("–", ui.button)
            less.onClick { changeSellLimit(r, -1) }
            val more = TextButton("+", ui.button)
            more.onClick { changeSellLimit(r, 1) }
            hover(less) { Lang.t("sell_limit_tip") }; hover(more) { Lang.t("sell_limit_tip") }
            val limit = Label("", ui.labelSmall)
            limit.setAlignment(Align.center)
            val nums = List(4) { Label("", ui.label).also { it.setAlignment(Align.center) } }
            val name = Table()
            sprites.named("res_${res.id}")?.let { name.add(ui.image(it.r, 20f)).size(20f).padRight(4f) }
            val nl = Label(Lang.t(res.name), ui.label)
            nl.setEllipsis(true)
            name.add(nl).expandX().fillX().left().minWidth(0f)
            val line = Table()
            if (odd) line.background = ui.rowAlt
            odd = !odd
            line.add(name).width(nameW).left()
            for ((i, l) in nums.withIndex()) line.add(l).width(colW[i])
            if (wideTrade) {
                // ПК: всё в одну строку
                line.add(buy).padLeft(10f).padRight(4f)
                line.add(sell).padRight(10f)
                line.add(less)
                line.add(limit).width(96f).center()
                line.add(more)
            } else {
                val btns = Table()
                btns.add(buy).width(82f).padRight(4f)
                btns.add(sell).width(90f).padRight(6f)
                btns.add(less)
                btns.add(limit).width(90f).center()
                btns.add(more)
                // телефон: кнопки только у выбранного товара - касание строки (иначе по пять кнопок на каждый товар)
                val holder = Container<Actor>()
                holder.right()
                line.row()
                line.add(holder).colspan(5).right()
                line.touchable = Touchable.enabled
                line.onClick { tradeSel = if (tradeSel == r) -1 else r; showTradeButtons() }
                tradeHolders[r] = holder to btns
            }
            list.add(line).width(w).padTop(2f).row()
            tradeLines[r] = line
            tradeRows.add(TradeRow(r, nums, buy, sell, limit, nl))
        }
        val pane = ScrollPane(list)
        pane.setScrollingDisabled(true, false)
        pane.setFadeScrollBars(false)
        pane.setOverscroll(false, false)
        val maxH = Gdx.graphics.height / scale * 0.58f
        col.add(pane).width(w).height(minOf(list.prefHeight + 4f, maxH)).row()
        val close = TextButton(Lang.t("close"), ui.button)
        close.onClick { m.isVisible = false }
        tradeCallBtn = TextButton("", ui.button)
        tradeCallBtn.onClick { callTrader() }
        hover(tradeCallBtn) { Lang.t("call_trader_tip", world.traderCallPrice()) }
        val foot = Table()
        foot.add(tradeCallBtn).padRight(8f)
        foot.add(close).width(140f)
        col.add(foot).padTop(10f).row()
        m.add(col)
        return m
    }

    /** Позвать торговца за золото; если нельзя - сказать почему */
    private fun callTrader() {
        if (world.canCallTrader()) { world.submit(Command.CALL_TRADER, 0, 0, 0); refreshTop(); if (trade.isVisible) updateTrade() }
        else showToast(Lang.t(if (world.isNight) "call_trader_night" else "call_trader_gold", world.traderCallPrice()), 2500)
    }

    private fun openTrade() {
        trade.isVisible = true
        showTradeButtons()
        updateTrade()
    }

    /** Телефон: товар, у которого открыты кнопки (-1 - ни у какого) */
    private var tradeSel = -1
    /** Список товаров торговца: строки по товару и их порядок (свои и продаваемые - сверху, прочие - ниже, тусклые) */
    private var tradeList: Table? = null
    private val tradeLines = HashMap<Int, Table>()
    private var tradeOrderKey = ""
    private var tradeListW = 0f

    /** Товар уже бывал в городе, на нём стоит автопродажа или его ждёт корабль (у торговца есть почти всё - это не признак) */
    private fun tradeRelevant(r: Int): Boolean =
        world.totalStock(r) > 0 || r in seenRes || world.sellAbove[r] >= 0 || world.shipWanted(r)

    /** Нужные товары - наверх; перестраивается, только когда набор сменился */
    private fun orderTrade() {
        val list = tradeList ?: return
        val order = tradeRows.map { it.r }.sortedBy { if (tradeRelevant(it)) 0 else 1 }
        val key = order.joinToString(",") { if (tradeRelevant(it)) "$it" else "-$it" }
        if (key == tradeOrderKey) return
        tradeOrderKey = key
        list.clearChildren()
        for ((i, r) in order.withIndex()) {
            val line = tradeLines[r] ?: continue
            line.background = if (i % 2 == 1) ui.rowAlt else null
            list.add(line).width(tradeListW).padTop(2f).row()
        }
    }
    private val tradeHolders = HashMap<Int, Pair<Container<Actor>, Table>>()

    private fun showTradeButtons() {
        for ((r, hb) in tradeHolders) {
            val want: Actor? = if (r == tradeSel) hb.second else null
            if (hb.first.actor !== want) hb.first.setActor(want)
            hb.first.padTop(if (want != null) 2f else 0f).padBottom(if (want != null) 4f else 0f)
        }
    }

    /** Лимит автопродажи: «выкл.» -> 0 -> 10 -> 20...; минус ниже нуля выключает */
    private fun changeSellLimit(r: Int, d: Int) {
        val cur = world.sellAbove[r]
        val next = if (d > 0) (if (cur < 0) 0 else cur + LIMIT_STEP) else (if (cur <= 0) -1 else maxOf(0, cur - LIMIT_STEP))
        if (next != cur) world.submit(Command.SELL_ABOVE, r, next, 0)
    }

    private fun updateTrade() {
        val here = world.traderHere
        val ship = here && world.traderShip
        tradeTitle.setText(Lang.t(if (ship) "ship_title" else "trader") + " · " + Lang.t("gold", world.gold))
        // торговца нет - оранжевым: почему кнопки неактивны и когда он придёт
        tradeState.setText(if (here) "[#A8E08F]" + Lang.t(if (ship) "ship_here" else "trader_here") + "[]"
            else "[#FFB070]" + Lang.t("trader_away", daysToTrader()) + " " + Lang.t("trade_wait") + "[]")
        tradeCallBtn.setText(Lang.t("call_trader", world.traderCallPrice()))
        tradeCallBtn.isVisible = !here
        tradeCallBtn.isDisabled = !world.canCallTrader()
        orderTrade()
        for (row in tradeRows) {
            val r = row.r
            val dim = !tradeRelevant(r)
            for (l in row.nums) l.color = if (dim) DIM_TEXT else Color.WHITE
            row.nums[0].setText(world.totalStock(r).toString())
            row.nums[1].setText(world.buyPrice(r).toString())
            row.nums[2].setText(world.sellPrice(r).toString())
            row.nums[3].setText(if (here) world.traderStock[r].toString() else "—")
            row.buy.isDisabled = !here
            row.sell.isDisabled = !here
            val lim = world.sellAbove[r]
            row.limit.setText(if (lim < 0) Lang.t("sell_off") else Lang.t("sell_above", lim))
            val wanted = world.shipWanted(r)
            row.name.setText(Lang.t(game.content.resources[r].name) + if (wanted) " · " + Lang.t("ship_demand") else "")
            row.name.color = if (wanted) town.ui.Ui.GOLD else if (dim) DIM_TEXT else Color.WHITE
        }
    }

    /** Через сколько суток придёт торговец: он приходит на рассвете дня, номер которого (с нуля) даёт 1 по модулю TRADER_EVERY */
    private fun daysToTrader(): Int {
        val d = world.tick / Tune.TICKS_PER_DAY
        val k = Math.floorMod(1 - d, Tune.TRADER_EVERY)
        return if (k == 0) Tune.TRADER_EVERY else k
    }

    // ---------- постройка и панель здания ----------

    private fun selectBuild(def: Int) {
        moveFrom = -1
        buildDef = def
        ghostX = -1; ghostY = -1
        roadStart = -1; roadEnd = -1; roadRemove = false
        if (::roadRemoveBtn.isInitialized) roadRemoveBtn.isChecked = false
        updateRoadGhost()
        tplReset(def == COPY_MODE)
        // здание-услуга: на время постановки показать, кого оно обслужит (если игрок сам не включил слой)
        val sl = serviceLayerOf(def)
        if (sl != null && (renderer.overlay == WorldRenderer.OV_NONE || autoOverlay)) {
            renderer.overlay = WorldRenderer.OV_SERVICE; renderer.overlayService = sl; autoOverlay = true
        } else if (autoOverlay) {
            renderer.overlay = WorldRenderer.OV_NONE; renderer.overlayService = null; autoOverlay = false
        }
        for (c in cards) {
            if (c.def >= 0 && !world.unlocked(c.def)) continue
            c.table.background = if (c.def == def) ui.cardOn else ui.card
        }
        if (def != -1) { selected = -1; tileCell = -1; clearAgent() }
        updateInfo()
    }

    private fun updateInfo() {
        if (!::sheet.isInitialized) return
        if (selected >= 0 && world.bState[selected] == B_GONE) selected = -1
        if (selSerial >= 0 && agentIdx() < 0) clearAgent()
        val placing = buildDef != -1
        setActions(actionRows(placing))
        confirm.setText(if (buildDef == COPY_MODE) tplConfirmText() else Lang.t(if (moveFrom >= 0) "move_here" else if (isLine(buildDef)) "road_build" else "build"))
        // место не подходит (или не хватает материалов) - кнопка тусклая; касание всё равно объяснит, почему
        val okHere = buildDef < 0 || buildDef == COPY_MODE || isLine(buildDef) ||
            (ghostX >= 0 && world.canBuild(buildDef, ghostX, ghostY, gDir) && (if (moveFrom >= 0) world.canMove(moveFrom) else world.canAfford(buildDef)))
        confirm.color.a = if (okHere) 1f else 0.45f
        cancel.setText(Lang.t(if (placedHere > 0 && moveFrom < 0) "done" else "cancel"))
        val armed = demolishArmed == selected && TimeUtils.millis() < demolishUntil
        demolishBtn.setText(Lang.t(if (armed) "demolish_sure" else "demolish"))
        if (selected >= 0 && world.def(selected).isProducer) {
            val lim = world.bLimit[selected]
            limitLbl.setText(if (lim < 0) Lang.t("limit_none_val") else lim.toString())
        }
        if (selected >= 0 && world.bState[selected] != B_GONE) workersLbl.setText("${world.bWorkers[selected]}/${world.bWanted[selected]}")
        updateHallPlates(!placing && selSerial < 0 && selected >= 0 && selected == world.hall)
        when {
            !placing && selSerial >= 0 -> {
                val text = describeAgent(agentIdx())
                sheetTitle.setText(text.substringBefore(NL))
                info.setText(text.substringAfter(NL, ""))
            }
            !placing && selected >= 0 -> {
                val text = describe(selected)
                sheetTitle.setText(text.substringBefore(NL))
                info.setText(text.substringAfter(NL, ""))
            }
            !placing && tileCell >= 0 -> { sheetTitle.setText(Lang.t(tileKey(world.tiles[tileCell]))); info.setText(cellText(tileCell)) }
            buildDef == COPY_MODE -> tplInfo()
            isLine(buildDef) -> roadInfo()
            placing && moveFrom >= 0 -> {
                val where = when {
                    ghostX < 0 -> Lang.t("tap_to_place")
                    !world.canBuild(buildDef, ghostX, ghostY, gDir) -> placeWhy(buildDef, ghostX, ghostY, gDir)
                    else -> ""
                }
                sheetTitle.setText(Lang.t("move") + ": " + Lang.t(game.content.buildings[buildDef].name))
                info.setText("${Lang.t("moving", Lang.t(game.content.buildings[buildDef].name), Tune.REFUND)}$NL$where")
            }
            placing -> {
                val def = game.content.buildings[buildDef]
                val parts = def.cost.indices.filter { def.cost[it] > 0 }
                    .map { "${Lang.t(game.content.resources[it].name)} ${def.cost[it]}" }
                val cost = (if (def.gold > 0) parts + Lang.t("gold", def.gold) else parts).joinToString(", ")
                val where = when {
                    world.gold < def.gold -> Lang.t("no_gold")
                    !world.hasMaterials(buildDef) -> Lang.t("no_materials", def.cost.indices.filter { def.cost[it] > 0 && world.freeStock(it) < def.cost[it] }
                        .joinToString(", ") { "${Lang.t(game.content.resources[it].name)} ${maxOf(0, world.freeStock(it))}/${def.cost[it]}" })
                    ghostX < 0 -> Lang.t(if (pc) "tap_to_place" else "place_center")
                    !world.canBuild(buildDef, ghostX, ghostY, gDir) -> WARN + placeWhy(buildDef, ghostX, ghostY, gDir) + "[]"
                    else -> Lang.t(if (pc) "click_to_place" else "press_build")
                }
                val near = when {
                    ghostX < 0 -> ""
                    def.gatherCode != G_NONE -> NL + Lang.t("gather_" + def.gather, world.nearIfPlaced(buildDef, ghostX, ghostY)) + " " + Lang.t("need_min", def.needMin)
                    def.needTile != null -> NL + Lang.t("near", Lang.t("need_" + def.needTile), world.nearIfPlaced(buildDef, ghostX, ghostY), def.needMin)
                    def.soil != null -> NL + Lang.t("soil_" + def.soil, world.yieldFor(buildDef, ghostX, ghostY))
                    else -> ""
                }
                sheetTitle.setText(Lang.t(def.name))
                val upkeep = if (def.upkeep > 0) NL + Lang.t("upkeep_line", world.upkeepFor(def)) else ""
                val decor = (if (def.isDecor) NL + Lang.t("decor_info", def.radius) else "") +
                    (if (def.border > 0) NL + Lang.t("border_info", def.border) else "")
                val eta = NL + Lang.t("eta_build", dur(world.buildEta(def)))
                val flood = if (ghostX >= 0 && world.lowCells(buildDef, ghostX, ghostY) > 0) NL + "[#9EC8FF]" + Lang.t(if (def.soil == "fertility") "flood_place_fields" else "flood_place", Tune.FLOOD_BONUS) + "[]" else ""
                info.setText("${Lang.t("cost", cost)}$upkeep$eta$decor$NL$where$near$flood")
            }
        }
        // низкий экран: место под текст держится, только когда текст есть (у клетки дороги его нет - панель не пустая и высокая)
        if (Gdx.graphics.height / scale < LOW_SCREEN) {
            val want = when {
                info.text.isBlank() -> 0f
                hallStrip.actor != null -> LOW_HALL_INFO_H
                else -> LOW_INFO_H
            }
            if (infoCell?.minHeight != want) { infoCell?.minHeight(want); sheet.invalidateHierarchy() }
        }
        showSheet(placing || sheetSel)
        // низкий экран (телефон лёжа): пока открыта панель здания, нижней полосы нет - иначе панель не влезает по высоте
        val lowOpen = sheetSel && Gdx.graphics.height / scale < LOW_SCREEN
        val wantBar: Actor? = if (placing || lowOpen) null else bottomBarActor
        if (::bottomHolder.isInitialized && bottomHolder.actor !== wantBar) bottomHolder.setActor(wantBar)
    }

    /** Почему здание нельзя поставить здесь - одной фразой */
    private fun placeWhy(def: Int, x: Int, y: Int, dir: Int): String {
        val p = world.placeProblem(def, x, y, dir)
        return when {
            p >= PP_BUSY * 100 -> (p - PP_BUSY * 100).let { t -> Lang.t("place_busy", if (t == BUILDING) Lang.t("place_bld") else Lang.lower(Lang.t(tileKey(t)))) }
            p == PP_EDGE -> Lang.t("place_edge")
            p == PP_DOORWAY -> Lang.t("place_doorway")
            p == PP_DOOR -> Lang.t("place_door") + if (pc) " (T)" else ""
            p == PP_PEOPLE -> Lang.t("place_people")
            p == PP_BORDER -> Lang.t("outside_border")
            p == PP_UNREACHABLE -> Lang.t("place_unreachable")
            p == PP_NEED -> Lang.t("place_need", game.content.buildings[def].needMin)
            p == PP_CUTS -> Lang.t("place_cuts")
            else -> Lang.t("cant_place")
        }
    }

    /** Строки кнопок панели для текущего режима */
    private fun actionRows(placing: Boolean): List<List<Actor>> {
        val rows = ArrayList<List<Actor>>()
        if (buildDef == COPY_MODE) {
            when (tplStage) {
                1 -> rows.add(listOfNotNull(tplLastBtn.takeIf { lastTpl != null }, cancel))
                2 -> rows.add(listOfNotNull(confirm.takeIf { tplDraft != null }, tplResetBtn, cancel))
                else -> rows.add(listOfNotNull(rotateBtn, confirm.takeIf { ghostX >= 0 }, cancel))
            }
            return rows
        }
        if (isLine(buildDef)) {
            rows.add(listOfNotNull(confirm.takeIf { !roadRemove && roadEnd >= 0 }, roadRemoveBtn, cancel))
            return rows
        }
        if (placing) {
            val undo = undoBtn.takeIf { moveFrom < 0 && world.canUndo }
            // телефон: поворот со словом, если в строке есть место (без кнопки отмены постройки)
            val rot = if (!pc && undo == null) rotateWordBtn else rotateBtn
            rows.add(listOfNotNull(rot.takeIf { !autoFacing(buildDef) }, confirm.takeIf { ghostX >= 0 }, undo, cancel))
            return rows
        }
        if (selSerial >= 0) {
            val a = agentIdx()
            followBtn.setText(Lang.t(if (following) "cz_unfollow" else "cz_follow"))
            val st = if (following) ui.buttonMain else ui.button
            if (followBtn.style !== st) followBtn.style = st
            rows.add(listOfNotNull(followBtn, homeBtn, workBtn.takeIf { a >= 0 && world.jobB[a] >= 0 && world.job[a] == JOB_WORKER }))
            return rows
        }
        val b = selected
        if (b < 0) return rows
        val def = world.def(b)
        if (world.bState[b] == B_SITE) {
            // достроить сейчас за золото - трата для богатого города
            rushBtn.setText(Lang.t("rush_btn", world.rushCost(b)))
            rushBtn.isDisabled = world.gold < world.rushCost(b)
            prioSiteBtn.isChecked = world.bPrio[b] == 1
            rows.add(listOf(rushBtn, prioSiteBtn))
            rows.add(listOf(cancelSiteBtn))
            return rows
        }
        // простой, который можно исправить одним действием: «Поставить лесника», «Откуда сырьё»... - главной кнопкой
        val fix = fixFor(b)
        if (fix != null) {
            fixBtn.setText(fix.first)
            fixAction = fix.second
            rows.add(listOf(fixBtn))
        }
        // рядом нечего добывать - главное действие «Перенести»
        val moveSt = if ((def.isProducer || def.isService) && world.bStatus[b] == BS_NO_RESOURCE && fix == null) ui.buttonMain else ui.button
        if (moveBtn.style !== moveSt) moveBtn.style = moveSt
        // рабочие и лимит - одной строкой: [–] 2/2 [+]  [–] лимит 20 [+]
        val ctl = ArrayList<Actor>()
        if (def.workers > 0) { ctl.add(minus); ctl.add(workersBox); ctl.add(plus) }
        if (def.isProducer) { ctl.add(limitMinus); ctl.add(limitBox); ctl.add(limitPlus) }
        if (ctl.isNotEmpty()) rows.add(ctl)
        // подвоз: второстепенно / обычно / срочно (заявки к этому зданию дальше или ближе в очереди носильщиков)
        if ((def.isProducer || def.isService) && def.workers > 0) {
            prioBtn.setText(Lang.t("prio_btn", Lang.t("prio_${world.bPrio[b]}")))
            val st = if (world.bPrio[b] == 1) ui.buttonMain else ui.button
            if (prioBtn.style !== st) prioBtn.style = st
            rows.add(listOf(prioBtn))
        }
        val canTryUpgrade = world.bUpg[b] == 0 && ((def.isHouse && world.houseReady(b)) || (world.hasLevels(b) && world.bLevel[b] < Tune.MAX_LEVEL))
        if (canTryUpgrade) {
            upgradeBtn.setText(Lang.t(if (upgradeArmedFor(b)) "upg_sure" else "upg_btn"))
            // подтверждение - главной (янтарной) кнопкой
            val st = if (upgradeArmedFor(b)) ui.buttonMain else ui.button
            if (upgradeBtn.style !== st) upgradeBtn.style = st
            upgradeBtn.color = if (world.upgradeBlock(b) == null) Color.WHITE else DIM_BTN
        }
        // после первого нажатия «Улучшить»: подтвердить это здание или улучшить все такие же разом
        if (canTryUpgrade && upgradeArmedFor(b)) {
            val n = massTargets(b).size
            upgradeAllBtn.setText(Lang.t("upg_all", n))
            rows.add(listOfNotNull(upgradeBtn, upgradeAllBtn.takeIf { n > 1 }))
            return rows
        }
        if (def.role == "store") rows.add(listOf(goodsBtn))
        if (def.serves == "trade" && world.nbCount > 0) rows.add(listOf(nbBtn))
        if (b != world.hall) rows.add(listOfNotNull(upgradeBtn.takeIf { canTryUpgrade }, moveBtn.takeIf { world.canMove(b) }, demolishBtn))
        else {
            // ратуша: праздник за золото (и улучшение, если есть)
            festivalBtn.setText(Lang.t("festival_btn", world.festivalCost()))
            festivalBtn.color = if (world.canFestival()) Color.WHITE else DIM_BTN
            for (i in 0 until 3) { val st = if (i == world.taxLevel) ui.buttonMain else ui.buttonTight; if (taxBtns[i].style !== st) taxBtns[i].style = st }
            rows.add(listOf(taxBox))
            if (world.crR >= 0) {
                crownBtn.setText(Lang.t("crown_give", world.crN, resLower(world.crR)))
                crownBtn.color = if (world.canDeliverCrown()) Color.WHITE else DIM_BTN
                rows.add(listOf(crownBtn))
            }
            rows.add(listOfNotNull(festivalBtn, upgradeBtn.takeIf { canTryUpgrade }))
            val n = minOf(Tune.HIRE_MAX, world.freeBeds())
            hireBtn.setText(Lang.t("hire_btn", maxOf(1, n), maxOf(1, n) * world.hireCost()))
            hireBtn.color = if (n > 0 && world.gold >= world.hireCost()) Color.WHITE else DIM_BTN
            rows.add(listOfNotNull(hireBtn, nbBtn.takeIf { world.nbCount > 0 }))
        }
        return rows
    }

    private fun setActions(rows: List<List<Actor>>) {
        val key = rows.joinToString("|") { r -> r.joinToString(",") { it.hashCode().toString() } }
        if (key == actionsKey) return
        actionsKey = key
        actions.clearChildren()
        // низкий экран (телефон лёжа): строки кнопок, что помещаются рядом, - в одну; иначе кнопки съедают текст панели
        val packed = if (Gdx.graphics.height / scale >= LOW_SCREEN) rows else {
            val maxW = LANDSCAPE_SHEET_W - 16f
            val out = ArrayList<List<Actor>>()
            var cur = ArrayList<Actor>()
            var used = 0f
            for (r in rows) {
                var rw = 0f
                for (a in r) rw += (a as? com.badlogic.gdx.scenes.scene2d.utils.Layout)?.prefWidth?.plus(6f) ?: (a.width + 6f)
                if (cur.isNotEmpty() && used + rw > maxW) { out.add(cur); cur = ArrayList(); used = 0f }
                cur.addAll(r); used += rw
            }
            if (cur.isNotEmpty()) out.add(cur)
            out
        }
        for (r in packed) {
            val line = Table()
            for (a in r) line.add(a).pad(3f)
            actions.add(line).right().row()
        }
    }

    // ---- копирование квартала ----

    /** Шаблон: здания (тип, смещение угла, сторона входа) и дороги в прямоугольнике w x h; смещения от его левого верхнего угла */
    private class Tpl(val w: Int, val h: Int, val defs: IntArray, val dx: IntArray, val dy: IntArray, val dir: IntArray,
                      val rx: IntArray, val ry: IntArray, val paved: BooleanArray)

    /** 0 - вне режима, 1 - выделяем углы, 2 - прямоугольник выделен, 3 - ставим шаблон */
    private var tplStage = 0
    private var tplA = -1
    private var tplB = -1
    private var tplHover = -1
    private var tplDraft: Tpl? = null
    private var tpl: Tpl? = null
    /** Последний скопированный шаблон - остаётся, пока открыт экран */
    private var lastTpl: Tpl? = null
    private var tplRot = 0
    private var tplKey = ""
    private var tplFits = 0

    private fun tplReset(on: Boolean) {
        tplStage = if (on) 1 else 0
        tplA = -1; tplB = -1; tplHover = -1; tplDraft = null; tpl = null; tplKey = ""
        renderer.selRect = null
        renderer.tplDefs = null; renderer.tplX = null; renderer.tplY = null; renderer.tplDir = null; renderer.tplOk = null
        renderer.tplRoads = null; renderer.tplPaved = null
    }

    private fun tplRect(a: Int, b: Int): IntArray {
        val ax = a % world.w; val ay = a / world.w; val bx = b % world.w; val by = b / world.w
        return intArrayOf(minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by))
    }

    private fun tplTap(cell: Int, screenX: Int, screenY: Int) {
        when (tplStage) {
            1, 2 -> {
                if (tplStage == 2 || tplA < 0) {
                    tplA = cell; tplB = -1; tplStage = 1; tplDraft = null
                    showToast(Lang.t("tpl_second"), 2500)
                } else {
                    tplB = cell; tplStage = 2
                    tplDraft = tplCollect(tplRect(tplA, tplB))
                    if (tplDraft == null) showToast(Lang.t("tpl_empty"), 2500)
                }
                updateInfo()
            }
            else -> {
                if (pc) { val a = tplAnchor(cell); ghostX = a / 10000; ghostY = a % 10000; tplPlace() } else centerOn(screenX, screenY)
            }
        }
    }

    /** Собрать шаблон из готовых зданий, целиком лежащих в прямоугольнике (без ратуши и чудес), и дорог в нём */
    private fun tplCollect(r: IntArray): Tpl? {
        val defs = ArrayList<Int>(); val dx = ArrayList<Int>(); val dy = ArrayList<Int>(); val dir = ArrayList<Int>()
        for (b in 0 until world.buildingCount) {
            if (world.bState[b] != B_ACTIVE || b == world.hall) continue
            val d = world.def(b)
            if (d.wonder || !d.buildable) continue
            val x = world.bX[b]; val y = world.bY[b]
            if (x < r[0] || y < r[1] || x + d.size - 1 > r[2] || y + d.size - 1 > r[3]) continue
            defs.add(world.bDef[b]); dx.add(x - r[0]); dy.add(y - r[1]); dir.add(world.bDir[b])
        }
        val rx = ArrayList<Int>(); val ry = ArrayList<Int>(); val paved = ArrayList<Boolean>()
        for (y in r[1]..r[3]) for (x in r[0]..r[2]) {
            val t = world.tiles[y * world.w + x]
            if (t == ROAD || t == PAVED) { rx.add(x - r[0]); ry.add(y - r[1]); paved.add(t == PAVED) }
        }
        if (defs.isEmpty() && rx.isEmpty()) return null
        return Tpl(r[2] - r[0] + 1, r[3] - r[1] + 1, defs.toIntArray(), dx.toIntArray(), dy.toIntArray(), dir.toIntArray(),
            rx.toIntArray(), ry.toIntArray(), paved.toBooleanArray())
    }

    /** Поворот шаблона на 90 градусов: (x, y) -> (h - 1 - y, x); здание размером s: угол (h - y - s, x), вход на сторону дальше */
    private fun tplTurned(t: Tpl): Tpl {
        val n = t.defs.size
        val sz = { i: Int -> game.content.buildings[t.defs[i]].size }
        return Tpl(t.h, t.w, t.defs,
            IntArray(n) { t.h - t.dy[it] - sz(it) }, IntArray(n) { t.dx[it] },
            IntArray(n) { if (autoFacing(t.defs[it])) t.dir[it] else (t.dir[it] + 1) and 3 },
            IntArray(t.rx.size) { t.h - 1 - t.ry[it] }, IntArray(t.rx.size) { t.rx[it] }, t.paved)
    }

    private fun tplRotate() {
        val t = tpl ?: return
        if (tplStage != 3) return
        tpl = tplTurned(t); tplRot = (tplRot + 1) and 3; tplKey = ""
        if (ghostX >= 0) { val a = tplAnchor((ghostY + t.h / 2) * world.w + ghostX + t.w / 2); ghostX = a / 10000; ghostY = a % 10000 }
        Sfx.play("click")
        updateInfo()
    }

    /** Левый верхний угол шаблона, когда курсор (или центр экрана) над клеткой: ghostX * 10000 + ghostY */
    private fun tplAnchor(cell: Int): Int {
        val t = tpl ?: return -1
        val x = (cell % world.w - t.w / 2).coerceIn(0, maxOf(0, world.w - t.w))
        val y = (cell / world.w - t.h / 2).coerceIn(0, maxOf(0, world.h - t.h))
        return x * 10000 + y
    }

    private fun tplCostText(t: Tpl): String {
        val res = game.content.resources
        val sum = IntArray(res.size)
        var gold = 0
        for (d in t.defs) { val bd = game.content.buildings[d]; for (r in res.indices) sum[r] += bd.cost[r]; gold += bd.gold }
        val stone = game.content.resource("stone").index
        for (p in t.paved) sum[stone] += if (p) Tune.PAVE_STONE else Tune.ROAD_STONE
        val parts = res.indices.filter { sum[it] > 0 }.map { "${Lang.t(res[it].name)} ${sum[it]}" }
        return (if (gold > 0) parts + Lang.t("gold", gold) else parts).joinToString(", ")
    }

    private fun tplConfirmText(): String {
        val t = if (tplStage == 2) tplDraft else tpl
        return if (t == null) Lang.t("build") else if (tplStage == 2) Lang.t("tpl_copy", t.defs.size, tplCostText(t)) else Lang.t("tpl_put")
    }

    private fun tplInfo() {
        sheetTitle.setText(Lang.t("tpl_card"))
        val sb = StringBuilder()
        when (tplStage) {
            1 -> sb.append(Lang.t(if (tplA < 0) "tpl_first" else "tpl_second"))
            2 -> {
                val t = tplDraft
                if (t == null) sb.append(Lang.t("tpl_empty"))
                else sb.append(Lang.t("tpl_selected", t.defs.size, t.rx.size, tplCostText(t)))
            }
            else -> {
                val t = tpl
                if (t != null) {
                    sb.append(Lang.t("tpl_placing", t.defs.size, t.rx.size, tplCostText(t)))
                    sb.append(NL).append(if (ghostX < 0) Lang.t(if (pc) "tap_to_place" else "place_center") else Lang.t("tpl_fits", tplFits, t.defs.size))
                }
            }
        }
        info.setText(sb.toString())
    }

    private fun tplConfirm() {
        if (tplStage == 2) {
            val d = tplDraft ?: return
            tpl = d; lastTpl = d; tplStage = 3; tplRot = 0; tplKey = ""
            ghostX = -1; ghostY = -1
            tplA = -1; tplB = -1
            renderer.selRect = null
            updateInfo()
        } else if (tplStage == 3) tplPlace()
    }

    /** Поставить шаблон: сперва дороги (на лугу), потом здания по порядку - каждое при наличии места и материалов */
    private fun tplPlace() {
        val t = tpl ?: return
        if (ghostX < 0) { updateInfo(); return }
        for (i in t.rx.indices) {
            val x = ghostX + t.rx[i]; val y = ghostY + t.ry[i]
            if (x !in 0 until world.w || y !in 0 until world.h || world.tiles[y * world.w + x] != GRASS) continue
            if (t.paved[i]) { if (world.canPave(x, y)) world.submit(Command.ROAD, x, y, 2) }
            else if (world.canRoad(x, y)) world.submit(Command.ROAD, x, y, 1)
        }
        var placed = 0
        val lacks = LinkedHashSet<String>()
        for (i in t.defs.indices) {
            val def = t.defs[i]
            val x = ghostX + t.dx[i]; val y = ghostY + t.dy[i]
            if (!world.unlocked(def) || world.wonderTaken(def) || !world.canBuild(def, x, y, t.dir[i])) continue
            if (world.canAfford(def)) { world.submit(Command.PLACE, def, x, y, t.dir[i]); placed++ }
            else {
                val bd = game.content.buildings[def]
                for (r in game.content.resources.indices) if (bd.cost[r] > 0 && world.freeStock(r) < bd.cost[r]) lacks.add(Lang.t(game.content.resources[r].name))
                if (world.gold < bd.gold) lacks.add(Lang.t("tpl_gold"))
            }
        }
        if (placed > 0 || t.rx.isNotEmpty()) Sfx.play("place")
        placedHere++
        tplKey = ""
        showToast(Lang.t("tpl_done", placed, t.defs.size) + if (lacks.isNotEmpty()) " " + Lang.t("tpl_lack", lacks.joinToString(", ")) else "", 3500)
        updateInfo()
    }

    /** Рамка выделения и призраки шаблона для отрисовки (шаблон пересчитывается при сдвиге, повороте и раз в 0,25 с) */
    private fun tplView() {
        if (buildDef != COPY_MODE) return
        when (tplStage) {
            1 -> {
                renderer.selRect = if (tplA < 0) null else tplRect(tplA, if (tplB >= 0) tplB else if (pc && tplHover >= 0) tplHover else tplA)
            }
            2 -> renderer.selRect = if (tplA >= 0 && tplB >= 0) tplRect(tplA, tplB) else null
            else -> {
                renderer.selRect = null
                val t = tpl
                if (t == null || ghostX < 0) { renderer.tplDefs = null; renderer.tplRoads = null; return }
                val key = "$ghostX:$ghostY:$tplRot"
                if (key == tplKey) return
                tplKey = key
                val n = t.defs.size
                val xs = IntArray(n) { ghostX + t.dx[it] }; val ys = IntArray(n) { ghostY + t.dy[it] }
                val ok = BooleanArray(n) { world.unlocked(t.defs[it]) && !world.wonderTaken(t.defs[it]) && world.canBuild(t.defs[it], xs[it], ys[it], t.dir[it]) }
                tplFits = ok.count { it }
                renderer.tplDefs = t.defs; renderer.tplX = xs; renderer.tplY = ys; renderer.tplDir = t.dir; renderer.tplOk = ok
                renderer.tplRoads = IntArray(t.rx.size) { (ghostY + t.ry[it]) * world.w + ghostX + t.rx[it] }
                renderer.tplPaved = t.paved
            }
        }
    }

    // ---- дорога ----

    /** Клетки дороги от начала до конца: сначала по x, потом по y */
    private fun roadPath(): IntArray {
        if (roadStart < 0) return IntArray(0)
        val end = if (roadEnd >= 0) roadEnd else roadStart
        val x0 = roadStart % world.w; val y0 = roadStart / world.w
        val x1 = end % world.w; val y1 = end / world.w
        val out = ArrayList<Int>()
        val sx = if (x1 >= x0) 1 else -1
        var x = x0
        while (true) { out.add(y0 * world.w + x); if (x == x1) break; x += sx }
        val sy = if (y1 >= y0) 1 else -1
        var y = y0
        while (y != y1) { y += sy; out.add(y * world.w + x1) }
        return out.toIntArray()
    }

    private fun updateRoadGhost() {
        if (!isLine(buildDef) || roadStart < 0) { renderer.roadCells = null; renderer.roadOk = null; return }
        val cells = roadPath()
        val costs = lineCosts(buildDef)
        val stock = IntArray(costs.size) { world.stockAvailable(costs[it].first) }
        val wall = isWall(buildDef)
        val kind = lineTileKind(buildDef)
        var prevOk = false
        val ok = BooleanArray(cells.size) { i ->
            val c = cells[i]
            val x = c % world.w; val y = c / world.w
            val t = world.tiles[c]
            val free = when (buildDef) {
                BRIDGE_MODE -> t == WATER && world.border[c] && (prevOk || world.canBridge(x, y))
                PAVED_MODE -> (t == GRASS || t == ROAD) && world.border[c] && world.level >= Tune.PAVE_LEVEL
                else -> t == GRASS && world.border[c] && (!wall || world.entranceOf(c) < 0)
            }
            val afford = costs.indices.all { stock[it] >= costs[it].second }
            val res = if (free && afford) { for (k in costs.indices) stock[k] -= costs[k].second; true } else t == kind
            prevOk = res
            res
        }
        renderer.roadCells = cells; renderer.roadOk = ok; renderer.lineWall = wall; renderer.lineKind = lineKindOf(buildDef)
    }

    /** Клетка, на которую линия этого вида ложится (для счёта клеток в подсказке) */
    private fun lineFits(c: Int): Boolean {
        val t = world.tiles[c]
        return when (buildDef) {
            BRIDGE_MODE -> t == WATER
            PAVED_MODE -> t == GRASS || t == ROAD
            else -> t == GRASS
        }
    }

    private fun roadInfo() {
        sheetTitle.setText(lineName(buildDef))
        val costs = lineCosts(buildDef)
        val (r, per) = costs[0]
        val wall = isWall(buildDef)
        if (roadRemove) { info.setText(Lang.t(if (wall) "wall_remove_hint" else "road_remove_hint")); return }
        if (roadStart < 0) {
            info.setText(when (buildDef) {
                PAVED_MODE -> Lang.t("paved_hint", Tune.PAVE_STONE)
                BRIDGE_MODE -> Lang.t("bridge_hint", Tune.BRIDGE_PLANKS, Tune.BRIDGE_STONE)
                else -> if (wall) Lang.t("wall_hint", per, resLower(r)) else Lang.t("road_hint", Tune.ROAD_STONE)
            })
            return
        }
        val cells = roadPath()
        val n = cells.count { lineFits(it) }
        val text = when {
            buildDef == PAVED_MODE || buildDef == BRIDGE_MODE ->
                Lang.t("line_count", n, costs.joinToString(", ") { "${Lang.t(game.content.resources[it.first].name)} ${n * it.second}" })
            wall -> Lang.t("wall_count", n, n * per, resLower(r))
            else -> Lang.t("road_count", n, n * Tune.ROAD_STONE)
        }
        info.setText(text + if (roadEnd < 0) NL + Lang.t("road_end") else "")
    }

    private fun placeRoad() {
        if (roadStart < 0 || roadEnd < 0) return
        var built = 0
        for (c in roadPath()) {
            val x = c % world.w; val y = c / world.w
            if (buildDef == ROAD_MODE) {
                if (!world.canRoad(x, y)) continue
                world.submit(Command.ROAD, x, y, 1)
            } else if (buildDef == PAVED_MODE) {
                if (!world.canPave(x, y)) continue
                world.submit(Command.ROAD, x, y, 2)
            } else if (buildDef == BRIDGE_MODE) {
                if (!world.canBridge(x, y)) continue
                world.submit(Command.ROAD, x, y, 3)
            } else {
                if (!world.canWall(x, y, wallKind(buildDef))) continue
                world.submit(Command.WALL, x, y, wallKind(buildDef))
            }
            built++
        }
        if (built > 0) Sfx.play("place")
        roadStart = -1; roadEnd = -1
        updateRoadGhost()
        updateInfo()
    }

    /** Первое нажатие «Улучшить» у мастерской показывает цену и пользу (текст панели), второе - улучшает */
    private var upgradeArmed = -1
    private var upgradeUntil = 0L
    private fun upgradeArmedFor(b: Int) = upgradeArmed == b && TimeUtils.millis() < upgradeUntil

    /**
     * Здания для массового улучшения: того же вида и уровня, готовые к улучшению (не хватать может только
     * золота или материалов); выбранное - первым, остальные - по близости к нему
     */
    private fun massTargets(b: Int): List<Int> {
        val wait = setOf("upg_no", "upg_max", "upg_city", "upg_house_wait")
        return (0 until world.buildingCount).filter {
            world.bDef[it] == world.bDef[b] && world.bLevel[it] == world.bLevel[b] && world.upgradeBlock(it).let { r -> r == null || r !in wait }
        }.sortedBy { if (it == b) -1 else Math.abs(world.bX[it] - world.bX[b]) + Math.abs(world.bY[it] - world.bY[b]) }
    }

    /** Улучшить все такие же: по очереди, пока хватает золота и материалов; итог - подсказкой */
    private fun upgradeAll() {
        val b = selected
        if (b < 0) return
        upgradeArmed = -1
        val list = massTargets(b)
        var done = 0
        var why: String? = null
        for (t in list) {
            val r = world.upgradeBlock(t)
            if (r == null) { world.submit(Command.UPGRADE, t, 0, 0); done++ } else if (why == null) why = r
        }
        if (done > 0) Sfx.play("place")
        val rest = list.size - done
        showToast(Lang.t("upg_all_done", done, list.size) + if (rest > 0 && why != null) " " + Lang.t("upg_all_" + why) else "", 3500)
        updateInfo()
    }

    /** Улучшить выбранное здание; нельзя - подсказка, почему */
    private fun upgradePressed() {
        val b = selected
        if (b < 0) return
        // мастерская - всегда сначала цена и польза; дом - если есть ещё готовые такие же (предложить улучшить все)
        if ((!world.def(b).isHouse || massTargets(b).size > 1) && !upgradeArmedFor(b)) {
            upgradeArmed = b; upgradeUntil = TimeUtils.millis() + 6000
            updateInfo()
            return
        }
        upgradeArmed = -1
        val why = upgradeWhy(b)
        if (why != null) {
            showToast(why, 3000)
            return
        }
        world.submit(Command.UPGRADE, b, 0, 0)
        Sfx.play("place")
        updateInfo()
    }

    private fun startMove() {
        val b = selected
        if (b < 0 || !world.canMove(b)) return
        val def = world.bDef[b]
        selectBuild(def)
        moveFrom = b
        ghostDir = world.bDir[b]   // перенос - в ту же сторону, можно повернуть
        updateInfo()
    }

    /** Снос: первое нажатие спрашивает подтверждение, второе (в течение 3 секунд) сносит */
    private fun demolishPressed() {
        val b = selected
        if (b < 0) return
        if (demolishArmed == b && TimeUtils.millis() < demolishUntil) {
            world.submit(Command.DEMOLISH, b, 0, 0)
            Sfx.play("demolish")
            demolishArmed = -1
            selected = -1
        } else {
            demolishArmed = b
            demolishUntil = TimeUtils.millis() + 3000
        }
        updateInfo()
    }

    private fun undo() {
        if (!world.canUndo) return
        world.submit(Command.UNDO, 0, 0, 0)
        showToast(Lang.t("undone"))
        updateInfo()
    }

    /** Лимит «не больше N»: нет -> 20 -> 30...; минус ниже 10 снимает лимит */
    private fun changeLimit(d: Int) {
        val b = selected
        if (b < 0 || !world.def(b).isProducer) return
        val cur = world.bLimit[b]
        val next = if (d > 0) (if (cur < 0) 2 * LIMIT_STEP else cur + LIMIT_STEP) else (if (cur <= LIMIT_STEP) -1 else cur - LIMIT_STEP)
        world.submit(Command.LIMIT, b, next, 0)
        updateInfo()
    }

    private fun showToast(text: String, ms: Long = 2000) {
        if (!::toast.isInitialized) return
        toast.setText(text)
        toastUntil = TimeUtils.millis() + ms
    }

    // ---------- карта ----------

    private fun onTap(screenX: Int, screenY: Int) {
        if (buildDef == -1) {
            val a = renderer.pickAgent(screenX, screenY, (22 * scale).toInt(), 1f)
            if (a >= 0) {
                selected = -1; tileCell = -1; demolishArmed = -1
                selSerial = world.aSerial[a]; selIdx = a; following = false
                updateInfo()
                return
            }
        }
        val cell = renderer.pick(screenX, screenY)
        if (cell < 0) return
        val x = cell % world.w
        val y = cell / world.w
        if (buildDef == COPY_MODE) { tplTap(cell, screenX, screenY); return }
        if (isLine(buildDef)) {
            if (roadRemove) {
                if (!isWall(buildDef)) { if (world.tiles[cell] == lineTileKind(buildDef) && world.canRemoveRoad(x, y)) world.submit(Command.ROAD, x, y, 0) }
                else if (world.tiles[cell] == WALL_W || world.tiles[cell] == WALL_S) world.submit(Command.WALL, x, y, 0)
            } else when {
                roadStart < 0 -> roadStart = cell
                roadEnd == cell -> placeRoad()
                else -> roadEnd = cell
            }
            updateRoadGhost()
            updateInfo()
            return
        }
        if (buildDef >= 0) {
            // повторное касание той же клетки - построить
            // ПК: клик ставит сразу (предпросмотр уже под курсором); телефон: первое касание - предпросмотр, второе - постройка
            if (pc) { ghostX = x; ghostY = y; placeGhost() } else centerOn(screenX, screenY)
            return
        }
        val b = world.buildingAt[cell]
        clearAgent()
        selected = b
        revealB = b; revealMoving = false
        demolishArmed = -1
        // пустая клетка: та же панель, что у здания - название клетки и запас ресурса
        tileCell = if (b < 0) cell else -1
        updateInfo()
    }

    /** Точка экрана, над которой стоит призрак на телефоне (чуть выше середины - снизу панель) */
    private fun ghostScreenX() = Gdx.graphics.width / 2
    private fun ghostScreenY() = (Gdx.graphics.height * 0.42f).toInt()

    /** Сдвинуть карту так, чтобы точка касания оказалась под призраком */
    /** Здание, которое надо вывести из-под панели (телефон стоя), -1 - нет */
    private var revealB = -1
    private var revealMoving = false
    private var revealSeen = -1

    /**
     * Выбранное здание ушло под панель снизу (телефон стоя) - карта плавно сдвигается, пока здание не окажется
     * между верхней панелью и панелью здания.
     */
    private fun revealSelected(delta: Float) {
        // выбрали другое здание (касанием, из событий, из окна) - проверить, видно ли его
        if (selected != revealSeen) { revealSeen = selected; revealB = selected; revealMoving = false }
        val b = revealB
        if (b < 0) return
        val sh = sheetHolder.actor
        if (selected != b || world.bState[b] == B_GONE || sh == null || Gdx.graphics.width > Gdx.graphics.height) { revealB = -1; return }
        if (sh.height <= 0f) return   // панель ещё не разложена - со следующего кадра
        val top = sh.localToStageCoordinates(com.badlogic.gdx.math.Vector2(0f, sh.height)).y * scale   // пиксели снизу
        val upper = Gdx.graphics.height - (topBarT?.height ?: 0f) * scale
        val d = world.def(b)
        val cx = world.bX[b] + d.size / 2f; val cy = world.bY[b] + d.size / 2f
        // точка чуть выше основания здания - видна и крыша
        val p = cam.project(com.badlogic.gdx.math.Vector3(renderer.sx(cx, cy), renderer.sy(cx, cy) + d.size * 10f, 0f))
        val want = top + (upper - top) * 0.35f
        // здание видно над панелью - не трогаем; ушло под неё - ведём до трети просвета
        if (!revealMoving && (p.y >= top + 24f * scale || upper - top < 80f * scale)) { revealB = -1; return }
        revealMoving = true
        if (p.y >= want - 6f * scale) { revealB = -1; revealMoving = false; return }
        val k = 1f - kotlin.math.exp(-delta * 10f)
        val a = cam.unproject(com.badlogic.gdx.math.Vector3(0f, Gdx.graphics.height - p.y, 0f))
        val c = cam.unproject(com.badlogic.gdx.math.Vector3(0f, Gdx.graphics.height - want, 0f))
        val before = cam.position.y
        cam.position.y += (a.y - c.y) * k
        clampCamera()
        // упёрлись в край карты или почти доехали - хватит
        if (kotlin.math.abs(cam.position.y - before) < 0.05f) { revealB = -1; revealMoving = false }
    }

    private fun centerOn(screenX: Int, screenY: Int) {
        val a = cam.unproject(com.badlogic.gdx.math.Vector3(screenX.toFloat(), screenY.toFloat(), 0f))
        val b = cam.unproject(com.badlogic.gdx.math.Vector3(ghostScreenX().toFloat(), ghostScreenY().toFloat(), 0f))
        cam.position.add(a.x - b.x, a.y - b.y, 0f)
        clampCamera()
    }

    /** Телефон: призрак здания - в клетке под точкой ghostScreen (карта двигается под ним) */
    private fun followCenter() {
        if (pc || buildDef < 0 && !(buildDef == COPY_MODE && tplStage == 3)) return
        val cell = renderer.pick(ghostScreenX(), ghostScreenY())
        if (cell < 0) return
        if (buildDef == COPY_MODE) {
            val a = tplAnchor(cell)
            if (a != ghostX * 10000 + ghostY) { ghostX = a / 10000; ghostY = a % 10000; updateInfo() }
            return
        }
        val s = game.content.buildings[buildDef].size
        val x = (cell % world.w - (s - 1) / 2).coerceIn(0, world.w - s)
        val y = (cell / world.w - (s - 1) / 2).coerceIn(0, world.h - s)
        if (x != ghostX || y != ghostY) { ghostX = x; ghostY = y; updateInfo() }
    }

    /** Текст панели здания: первая строка - заголовок, дальше состояние, рабочие, жильцы, запасы */
    private fun describe(b: Int): String {
        val def = world.def(b)
        val res = game.content.resources
        val sb = StringBuilder(if (world.hasLevels(b)) Lang.t("lvl_title", Lang.t(def.name), world.bLevel[b]) else Lang.t(def.name))
        fun amounts(filter: (Int) -> Boolean, value: (Int) -> String): String {
            val parts = res.indices.filter(filter).map { "${Lang.t(res[it].name)} ${value(it)}" }
            return if (parts.isEmpty()) Lang.t("stock_empty") else parts.joinToString(", ")
        }
        sb.append(NL)
        if (world.bState[b] == B_SITE) {
            sb.append(Lang.t("st_site", world.bBuild[b] * 100 / def.build, dur(world.siteEta(b))))
            if (world.bMoveFrom[b] >= 0) sb.append(". ").append(Lang.t("site_move"))
            sb.append(NL).append(Lang.t("materials", amounts({ def.cost[it] > 0 }) { "${world.stock(b, it)}/${def.cost[it]}" }))
            return sb.toString()
        }
        if (b == world.hall) return sb.append(hallInfo(b)).toString()
        when {
            def.isProducer -> {
                sb.append(statusLine(world.bStatus[b]))
                statusHint(b, world.bStatus[b]).let { if (it.isNotEmpty()) sb.append(NL).append(MUTED).append(it).append("[]") }
                // инструменты одной строкой: износ и запасные (в «Запасах» их не повторяем)
                val toolR = if (def.tools) toolsRes else -1
                if (def.tools) {
                    sb.append(NL).append(
                        if (world.bTool[b] > 0) Lang.t("tool_ok", (Tune.TOOL_LIFE - world.bTool[b]).coerceIn(0, Tune.TOOL_LIFE) * 100 / Tune.TOOL_LIFE)
                        else Lang.t("tool_none", Tune.TOOL_SLOW))
                    if (toolR >= 0 && world.stock(b, toolR) > 0) sb.append(", ").append(Lang.t("tool_spare", world.stock(b, toolR)))
                }
                sb.append(NL).append(Lang.t("stock", amounts({ it != toolR && world.stock(b, it) > 0 }) { "${world.stock(b, it)}" }))
                if (def.gatherCode != G_NONE) sb.append(NL).append(Lang.t("gather_" + def.gather, world.bNear[b]))
                if (def.soil != null) {
                    sb.append(NL).append(Lang.t("soil_" + def.soil, world.bYield[b]))
                    val wy = world.weatherYield(b)
                    if (wy != 100) sb.append(NL).append(Lang.t("weather_yield", (if (wy > 100) "+" else "") + (wy - 100)))
                }
            }
            def.isStore -> {
                var used = 0
                for (r in res.indices) used += world.stock(b, r)
                sb.append(Lang.t("store_load", used, world.capacityOf(b)))
                if (def.role == "store") {
                    var acc = 0
                    for (r in res.indices) if (world.bAccept[b * world.R + r] != 0) acc++
                    sb.append(NL).append(if (acc == res.size) Lang.t("store_accepts_all") else Lang.t("store_accepts_n", acc, res.size))
                }
                sb.append(NL).append(amounts({ world.stock(b, it) > 0 }) { "${world.stock(b, it)}" })
            }
            def.isDecor -> {
                sb.append(statusLine(world.bStatus[b]))
                sb.append(NL).append(Lang.t("decor_info", def.radius))
            }
            def.isService -> {
                sb.append(statusLine(world.bStatus[b]))
                statusHint(b, world.bStatus[b]).let { if (it.isNotEmpty()) sb.append(NL).append(MUTED).append(it).append("[]") }
                sb.append(NL).append(Lang.t("serves", def.radius))
            }
            def.isGuard -> {
                sb.append(statusLine(if (world.bUnpaid[b] != 0) BS_UNPAID else if (world.bWorkers[b] > 0 || def.wonder) BS_OK else BS_NO_WORKERS))
                sb.append(NL).append(Lang.t("guard_info", def.radius + Tune.LEVEL_GUARD * (world.bLevel[b] - 1)))
            }
            def.isHouse -> {
                if (world.bFire[b] > 0) sb.append(Lang.t("st_fire")).append(NL)
                if (world.bSick[b] > 0) sb.append(Lang.t("house_sick")).append(NL)
                sb.append(Lang.t("residents", world.bResidents[b], def.beds))
                sb.append(". ").append(Lang.t("happiness", world.bHappy[b]))
                val needs = game.content.needs.filter { it.level <= world.level }
                val ok = needs.filter { world.bNeedOk[b] and (1 shl it.index) != 0 }.map { Lang.t(it.name) }
                val missing = needs.filter { world.bNeedOk[b] and (1 shl it.index) == 0 }.map { Lang.t(it.name) }
                if (missing.isNotEmpty()) sb.append(NL).append(Lang.t("needs_missing", missing.joinToString(", ")))
                if (ok.isNotEmpty()) sb.append(NL).append(Lang.t("needs_ok", ok.joinToString(", ")))
                val goods = res.indices.filter { world.stock(b, it) > 0 }
                if (goods.isNotEmpty()) sb.append(NL).append(Lang.t("stock", goods.joinToString(", ") { "${Lang.t(res[it].name)} ${world.stock(b, it)}" }))
                sb.append(NL).append(kidsLine(b))
            }
        }
        if (def.fame > 0) sb.append(NL).append(Lang.t("fame_line", def.fame))
        sb.append(specialLines(def))
        if (def.serves == "market") {
            var held = 0
            for (r in res.indices) held += world.stock(b, r)
            sb.append(NL).append(Lang.t("market_stock", held))
        }
        if (def.serves == "trade") sb.append(NL).append(tradeLines(b))
        if (world.bLow[b]) sb.append(NL).append(MUTED).append(Lang.t(if (world.flooded(b)) "flood_now" else "flood_zone")).append("[]")
        if (def.serves == "port") sb.append(NL).append(when {
            world.traderShip -> Lang.t("ship_here")
            world.season == WINTER -> Lang.t("ship_winter")
            world.shipNext > world.day -> Lang.t("ship_next", world.shipNext)
            else -> Lang.t("ship_soon")
        })
        if (def.border > 0) sb.append(NL).append(Lang.t("border_info", def.border + Tune.LEVEL_BORDER * (world.bLevel[b] - 1)))
        if (def.upkeep > 0) {
            sb.append(NL).append(Lang.t("upkeep_line", world.upkeepOf(b)))
            if (world.bUnpaid[b] != 0) sb.append(". ").append(Lang.t("upkeep_unpaid"))
            else if (def.workers > 0 && world.bWanted[b] == 0) sb.append(". ").append(Lang.t("upkeep_idle"))
            else if (def.workers > 0) sb.append(". ").append(Lang.t("upkeep_hint"))
        }
        // улучшение - приглушённым цветом отдельным абзацем: справка, а не состояние
        // уровень мастерской: что даст и сколько стоит - только после первого нажатия «Улучшить» (и пока улучшается);
        // у дома - всегда (условия, когда он станет лучше)
        if (def.isHouse || world.bUpg[b] == 1 || upgradeArmedFor(b)) sb.append(NL).append(MUTED).append(upgradeText(b)).append("[]")
        // кнопка «Улучшить» тусклая - сразу видно почему (а не только по нажатию)
        val canTryUpgrade = world.bUpg[b] == 0 && ((def.isHouse && world.houseReady(b)) || (world.hasLevels(b) && world.bLevel[b] < Tune.MAX_LEVEL))
        if (canTryUpgrade) upgradeWhy(b)?.let { sb.append(NL).append(WARN).append(Lang.t("why_upgrade", Lang.lower(it))).append("[]") }
        if (world.movingTo(b) >= 0) sb.append(NL).append(Lang.t("moving_to"))
        return sb.toString()
    }

    /**
     * Панель ратуши: город, следующий уровень, налог, слава... - подпись раздела золотом, склад ратуши в конце,
     * под ними - почему недоступны кнопки (нанять, праздник, улучшить)
     */
    private fun hallInfo(b: Int): String {
        val res = game.content.resources
        val sb = StringBuilder()
        fun line(text: String) {
            if (sb.isNotEmpty()) sb.append(NL)
            val i = text.indexOf(": ")
            if (i in 1..48) sb.append(HEAD).append(text, 0, i + 1).append("[]").append(text, i + 1, text.length) else sb.append(text)
        }
        townInfo(plates = true).split(NL).forEach { line(it) }
        var used = 0
        for (r in res.indices) used += world.stock(b, r)
        line(Lang.t("hall_store", used, world.capacityOf(b)))
        val goods = res.indices.filter { world.stock(b, it) > 0 }
        if (goods.isNotEmpty()) sb.append(NL).append(MUTED).append(goods.joinToString(", ") { "${Lang.t(res[it].name)} ${world.stock(b, it)}" }).append("[]")
        val n = minOf(Tune.HIRE_MAX, world.freeBeds())
        val hireWhy = if (n == 0) Lang.t("hire_no_beds") else if (world.gold < world.hireCost()) Lang.t("no_gold") else null
        if (hireWhy != null) sb.append(NL).append(WARN).append(Lang.t("why_hire", Lang.lower(hireWhy))).append("[]")
        val festWhy = when {
            world.festivalLeft > 0 -> null
            world.festivalWait > 0 -> Lang.t("festival_wait", (world.festivalWait + Tune.TICKS_PER_DAY - 1) / Tune.TICKS_PER_DAY)
            world.gold < world.festivalCost() -> Lang.t("no_gold")
            else -> null
        }
        if (festWhy != null) sb.append(NL).append(WARN).append(Lang.t("why_festival", Lang.lower(festWhy))).append("[]")
        val canTryUpgrade = world.bUpg[b] == 0 && world.hasLevels(b) && world.bLevel[b] < Tune.MAX_LEVEL
        if (canTryUpgrade) upgradeWhy(b)?.let { sb.append(NL).append(WARN).append(Lang.t("why_upgrade", Lang.lower(it))).append("[]") }
        return sb.toString()
    }

    /** Почему улучшить нельзя - текстом (null - можно) */
    private fun upgradeWhy(b: Int): String? {
        val why = world.upgradeBlock(b) ?: return null
        return when (why) {
            "upg_city" -> Lang.t("upg_city", Lang.t(game.content.levels[if (world.def(b).isHouse) game.content.building(world.def(b).upgradeTo!!).level.coerceIn(0, game.content.levels.size - 1) else Tune.LEVEL_CITY[minOf(world.bLevel[b] + 1, Tune.MAX_LEVEL)]].name))
            "no_materials" -> Lang.t("no_materials", game.content.resources.indices.filter { world.upgradeCost(b, it) > 0 && world.freeStock(it) < world.upgradeCost(b, it) }
                .joinToString(", ") { "${Lang.t(game.content.resources[it].name)} ${maxOf(0, world.freeStock(it))}/${world.upgradeCost(b, it)}" })
            else -> Lang.t(why)
        }
    }

    /** Сырьё, которого мастерской не хватает на цикл */
    private fun missingInputs(b: Int): List<Int> {
        val d = world.def(b)
        return d.input.indices.filter { d.input[it] > 0 && world.stock(b, it) < d.input[it] }
    }

    /** Что сделать с простоем - одной строкой (пусто - объяснять нечего) */
    private fun statusHint(b: Int, st: Int): String {
        val def = world.def(b)
        return when (st) {
            BS_NO_WORKERS -> Lang.t(if (world.bWanted[b] == 0) "hint_workers_off" else "hint_no_people")
            BS_NO_RESOURCE -> Lang.t(if (def.gather in HINT_GATHER) "hint_res_${def.gather}" else "hint_res")
            BS_NO_INPUT -> missingInputs(b).let { m -> if (m.isEmpty()) "" else Lang.t("hint_no_input", m.joinToString(", ") { resLower(it) }) }
            BS_SEASON -> Lang.t("hint_season")
            BS_UNPAID -> Lang.t("hint_unpaid")
            BS_FLOOD -> Lang.t("hint_flood")
            else -> ""
        }
    }

    /** Простой, который чинится одним действием: подпись кнопки и действие (null - такого нет) */
    private fun fixFor(b: Int): Pair<String, () -> Unit>? {
        val def = world.def(b)
        if (!(def.isProducer || def.isService) || world.bState[b] != B_ACTIVE) return null
        fun bld(id: String) = game.content.buildings.firstOrNull { it.id == id }?.index?.takeIf { world.unlocked(it) }
        return when (world.bStatus[b]) {
            BS_NO_RESOURCE -> if (def.gather == "wood") bld("forester")?.let { f -> Lang.t("fix_forester") to { selectBuild(f) } } else null
            BS_NO_INPUT -> missingInputs(b).firstOrNull()?.let { r -> Lang.t("fix_inputs") to { openChain(r) } }
            BS_FAR -> bld("store")?.let { s -> Lang.t("fix_store") to { selectBuild(s) } }
            BS_NO_WORKERS -> if (world.bWanted[b] > 0 && world.freeBeds() == 0) bld("house")?.let { h -> Lang.t("fix_house") to { selectBuild(h) } } else null
            else -> null
        }
    }

    /** Материалы улучшения: «Доски 3/8, Камень 0/4» (привезено / нужно) */
    private fun upgradeMaterials(b: Int, delivered: Boolean): String {
        val res = game.content.resources
        return res.indices.filter { world.upgradeCost(b, it) > 0 }.joinToString(", ") {
            val need = world.upgradeCost(b, it)
            val got = if (world.def(b).isHouse) world.stock(b, it) else world.upInv[b * world.R + it]
            if (delivered) "${Lang.t(res[it].name)} ${minOf(got, need)}/$need" else "${Lang.t(res[it].name)} $need"
        }
    }

    /** Что даст следующий уровень */
    private fun levelEffect(b: Int, next: Int): String {
        val def = world.def(b)
        val speed = Tune.LEVEL_SPEED * (next - 1)
        val parts = ArrayList<String>()
        when {
            def.isStore -> { parts += Lang.t("lvl_eff_store", Tune.LEVEL_CAPACITY * (next - 1), Tune.LEVEL_BORDER * (next - 1)) }
            def.isService -> parts += Lang.t("lvl_eff_serve", Tune.LEVEL_SERVE * (next - 1))
            def.isTower -> parts += Lang.t("lvl_eff_border", Tune.LEVEL_BORDER * (next - 1))
            def.isGuard -> parts += Lang.t("lvl_eff_guard", Tune.LEVEL_GUARD * (next - 1))
            else -> {
                parts += Lang.t("lvl_eff_speed", speed)
                if (def.gatherCode != G_NONE) parts += Lang.t("lvl_eff_radius", next - 1)
            }
        }
        if (next >= Tune.MAX_LEVEL && def.workers > 0) parts += Lang.t("lvl_eff_worker")
        return parts.joinToString(", ")
    }

    /** Улучшение: дом - до следующего вида, мастерская, услуга, склад - уровень; что нужно или каких материалов ждёт */
    /** Срок в тиках при x1 -> «45 с», «2 мин 30 с» (до минуты - с шагом 5 с, дольше - 10 с) */
    private fun dur(ticks: Int): String {
        val s = (ticks + Tune.TICKS_PER_SECOND - 1) / Tune.TICKS_PER_SECOND
        if (s < 60) return Lang.t("dur_s", maxOf(5, (s + 4) / 5 * 5))
        val r = (s + 5) / 10 * 10
        return if (r % 60 == 0) Lang.t("dur_m", r / 60) else Lang.t("dur_ms", r / 60, r % 60)
    }

    private fun upgradeText(b: Int): String {
        val def = world.def(b)
        if (!def.isHouse) {
            val lv = world.bLevel[b]
            if (lv >= Tune.MAX_LEVEL) return Lang.t("lvl_max")
            if (world.bUpg[b] == 1) return Lang.t("lvl_waiting", lv + 1, upgradeMaterials(b, true)) + ", " + Lang.t("eta_left", dur(world.upgradeEta(b, true)))
            var text = Lang.t("lvl_next", lv + 1, levelEffect(b, lv + 1)) + NL +
                Lang.t("lvl_cost", upgradeMaterials(b, false), world.upgradeGold(b), dur(world.upgradeEta(b, false)))
            if (world.level < Tune.LEVEL_CITY[lv + 1]) text += ". " + Lang.t("upg_city", Lang.t(game.content.levels[Tune.LEVEL_CITY[lv + 1]].name))
            return text
        }
        val next = def.upgradeTo?.let { game.content.building(it) } ?: return Lang.t("upg_max")
        if (world.bUpg[b] == 1) return Lang.t("upg_waiting", Lang.t(next.name), upgradeMaterials(b, true)) + ", " + Lang.t("eta_left", dur(world.upgradeEta(b, true)))
        if (world.houseReady(b)) return Lang.t("upg_ready", Lang.t(next.name), upgradeMaterials(b, false), next.gold) + ", " + Lang.t("eta_upg", dur(world.upgradeEta(b, false)))
        if (!world.unlocked(next.index)) {
            val lv = game.content.levels[next.level.coerceIn(0, game.content.levels.size - 1)]
            return Lang.t("upg_locked", Lang.t(next.name), Lang.t(lv.name))
        }
        var text = Lang.t("upg_need", Lang.t(next.name), Tune.UPGRADE_HAPPY, world.bUpgDays[b], Tune.UPGRADE_DAYS, next.beds)
        if (next.gold > 0) {
            text += ". " + Lang.t("upg_gold", next.gold)
            if (world.bUpgDays[b] >= Tune.UPGRADE_DAYS && world.gold < next.gold) text += " " + Lang.t("upg_no_gold")
        }
        return text
    }

    /** Налоги в сутки при нынешних жильцах и довольстве */
    private fun taxPerDay(): Int = world.taxPreview(world.taxLevel)

    /** Заказ короны одной строкой: что, сколько, срок, сколько уже есть, награда */
    private fun crownLine(): String = Lang.t("crown_order", world.crN, resLower(world.crR), world.crEnd,
        minOf(world.crN, maxOf(0, world.stockAvailable(world.crR))), world.crGold, world.crPrestige)

    /** Сведения о городе в панели ратуши: уровень, налоги, что нужно для следующего уровня, торговец */
    /** plates - в панели ратуши: уровень, довольство и слава уже показаны плашками, в тексте их нет */
    private fun townInfo(plates: Boolean = false): String {
        val levels = game.content.levels
        val sb = StringBuilder()
        if (!plates) {
            sb.append(Lang.t("hall_town", Lang.t(levels[world.level].name), world.townHappy, taxPerDay())).append(NL)
            if (world.level + 1 < levels.size) {
                val next = levels[world.level + 1]
                sb.append(Lang.t("next_level", Lang.t(next.name), world.agentCount, next.pop, next.happy))
            } else sb.append(Lang.t("max_level"))
            sb.append(NL)
        }
        sb.append(Lang.t("tax_now", Lang.t("tax_${world.taxLevel}"), taxPerDay(), taxHappy(world.taxLevel)))
        if (!plates) sb.append(NL).append(Lang.t("fame_town", world.prestige, world.fameDaily()))
        sb.append(NL).append(Lang.t("town_kids", world.kidsTotal(), world.kidsGrown, world.skilledTotal()))
        if (world.nbCount > 0) sb.append(NL).append(Lang.t("town_nb", world.nbCount, world.contracts(), world.deliveries))
        if (world.scenarioDef == null) {
            if (world.crR >= 0) sb.append(NL).append("[#F5D27A]").append(crownLine()).append("[]")
            else if (world.level >= Tune.CROWN_LEVEL) sb.append(NL).append(Lang.t("crown_next", maxOf(world.crNext, world.day + 1)))
        }
        sb.append(NL).append(if (world.traderHere) Lang.t(if (world.traderShip) "ship_here" else "trader_here") else Lang.t("trader_away", daysToTrader()))
        if (world.festivalLeft > 0) sb.append(NL).append("[#A8E08F]").append(Lang.t("festival_on", dur(world.festivalLeft))).append("[]")
        return sb.toString()
    }

    /** Строка состояния цветом: работает - зелёная, простой - оранжевая */
    private fun statusLine(st: Int): String {
        val k = statusKey(st)
        return (if (k == "st_ok") "[#A8E08F]" else "[#FFB070]") + Lang.t(k) + "[]"
    }

    private fun statusKey(st: Int) = when (st) {
        BS_NO_WORKERS -> "st_no_workers"
        BS_NO_RESOURCE -> "st_no_resource"
        BS_OUTPUT_FULL -> "st_full"
        BS_NO_INPUT -> "st_no_input"
        BS_WAITING -> "st_waiting"
        BS_SEASON -> "st_season"
        BS_LIMIT -> "st_limit"
        BS_FAR -> "st_far"
        BS_UNPAID -> "st_unpaid"
        BS_FIRE -> "st_fire"
        BS_FLOOD -> "st_flood"
        else -> "st_ok"
    }

    private fun changeWorkers(d: Int) {
        val b = selected
        if (b < 0 || world.def(b).workers == 0) return
        val n = (world.bWanted[b] + d).coerceIn(0, world.def(b).workers)
        if (n != world.bWanted[b]) world.submit(Command.SET_WORKERS, b, n, 0)
    }

    /** Панель клетки: сколько ресурса (брёвна, саженец, камень, рыба, дичь); заголовок - название клетки */
    private fun cellText(c: Int): String {
        val t = world.tiles[c]
        return when {
            t == FOREST && world.res[c] == 0 && world.grow[c] > 0 -> Lang.t("cell_sapling", world.grow[c])
            t == FOREST -> Lang.t("cell_logs", world.res[c]) + if (world.game[c] > 0) NL + Lang.t("cell_game") else ""
            t == ROCK -> Lang.t("cell_stone", world.res[c])
            t == WATER && world.resMax[c] > 0 -> Lang.t("cell_fish", world.res[c], world.resMax[c])
            t == GRASS && world.grow[c] > 0 -> Lang.t("cell_stump")
            else -> ""
        }
    }

    private fun tileKey(t: Int) = when (t) {
        FOREST -> "tile_forest"
        WATER -> "tile_water"
        ROAD -> "tile_road"
        PAVED -> "tile_paved"
        BRIDGE -> "tile_bridge"
        WALL_W, WALL_S -> "tile_wall"
        ROCK -> "tile_rock"
        else -> "tile_grass"
    }

    private fun placeGhost() {
        if (buildDef < 0 || ghostX < 0) return
        if (moveFrom >= 0) {
            if (!world.canBuild(buildDef, ghostX, ghostY, gDir) || !world.canMove(moveFrom)) { updateInfo(); return }
            world.submit(Command.MOVE, moveFrom, ghostX, ghostY, gDir)
            Sfx.play("place")
            selectBuild(-1)
            selected = world.lastPlaced
            updateInfo()
            return
        }
        if (!world.canBuild(buildDef, ghostX, ghostY, gDir) || !world.canAfford(buildDef)) {
            updateInfo()
            return
        }
        world.submit(Command.PLACE, buildDef, ghostX, ghostY, gDir)
        Sfx.play("place")
        placedHere++
        // ПК: призрак снова под курсором при движении мыши; телефон: следующий - снова в центре экрана
        ghostX = -1; ghostY = -1
        updateInfo()
    }

    private fun maxZoom(): Float {
        val fit = maxOf((renderer.maxX - renderer.minX) / cam.viewportWidth, (renderer.maxY - renderer.minY) / cam.viewportHeight)
        return fit.coerceIn(1f, 6f)
    }

    /** Камера не уходит за карту и не масштабируется за пределы */
    private fun clampCamera() {
        cam.zoom = cam.zoom.coerceIn(MIN_ZOOM, maxZoom())
        cam.position.x = cam.position.x.coerceIn(renderer.minX, renderer.maxX)
        cam.position.y = cam.position.y.coerceIn(renderer.minY, renderer.maxY)
    }

    private inner class MapGestures : GestureDetector.GestureAdapter() {
        private var startZoom = 1f

        override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
            startZoom = cam.zoom
            return false
        }

        override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
            if (photo) return true
            // правая кнопка - отменить постройку или закрыть панель
            if (button == Input.Buttons.RIGHT) { if (buildDef != -1 || sheetSel) closeSheet(); return true }
            onTap(x.toInt(), y.toInt())
            return true
        }

        override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
            following = false
            cam.position.add(-deltaX * cam.zoom / scale, deltaY * cam.zoom / scale, 0f)
            clampCamera()
            return true
        }

        override fun zoom(initialDistance: Float, distance: Float): Boolean {
            cam.zoom = startZoom * initialDistance / distance
            clampCamera()
            return true
        }
    }

    /**
     * Колесо над картой - масштаб; над интерфейсом - отдаём Stage (листать панель).
     * Стоит первым: иначе после касания панели строительства Stage держит фокус прокрутки на ней.
     */
    private inner class Wheel : InputAdapter() {
        private val p = com.badlogic.gdx.math.Vector2()

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            stage.screenToStageCoordinates(p.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
            if (!photo && stage.hit(p.x, p.y, true) != null) return false
            stage.scrollFocus = null
            cam.zoom *= if (amountY > 0) 1.15f else 1 / 1.15f
            clampCamera()
            return true
        }
    }

    private inner class Keys : InputAdapter() {

        override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
            if (buildDef == COPY_MODE) {
                val cell = renderer.pick(screenX, screenY)
                if (cell >= 0) {
                    tplHover = cell
                    if (tplStage == 3) {
                        val a = tplAnchor(cell)
                        if (a != ghostX * 10000 + ghostY) { ghostX = a / 10000; ghostY = a % 10000; updateInfo() }
                    }
                }
                return false
            }
            if (buildDef < 0) return false
            val cell = renderer.pick(screenX, screenY)
            if (cell >= 0 && (cell % world.w != ghostX || cell / world.w != ghostY)) {
                ghostX = cell % world.w; ghostY = cell / world.w
                updateInfo()
            }
            return false
        }

        override fun keyDown(keycode: Int): Boolean {
            if (photo) {
                when (keycode) {
                    Input.Keys.ESCAPE, Input.Keys.BACK, Input.Keys.P -> exitPhoto()
                    Input.Keys.SPACE -> setPaused(!paused)
                    Input.Keys.Q -> rotateMap(-1)
                    Input.Keys.E -> rotateMap(1)
                    else -> return false
                }
                return true
            }
            when (keycode) {
                Input.Keys.P -> enterPhoto()
                Input.Keys.SPACE -> setPaused(!paused)
                Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3, Input.Keys.NUM_4, Input.Keys.NUM_5,
                Input.Keys.NUM_6, Input.Keys.NUM_7, Input.Keys.NUM_8, Input.Keys.NUM_9, Input.Keys.NUM_0 -> {
                    val i = if (keycode == Input.Keys.NUM_0) 9 else keycode - Input.Keys.NUM_1
                    if (i < cards.size) cardTapped(cards[i].def)
                }
                Input.Keys.B -> {
                    if (buildWin.isVisible) buildWin.isVisible = false else openBuildMenu()
                }
                Input.Keys.PLUS, Input.Keys.EQUALS, Input.Keys.NUMPAD_ADD -> { speedIdx = minOf(speeds.size - 1, speedIdx + 1); setPaused(false); refreshTop() }
                Input.Keys.MINUS, Input.Keys.NUMPAD_SUBTRACT -> { speedIdx = maxOf(0, speedIdx - 1); setPaused(false); refreshTop() }
                Input.Keys.R -> cardTapped(ROAD_MODE)
                Input.Keys.TAB -> if (stats.isVisible) stats.isVisible = false else openStats()
                Input.Keys.L -> if (events.isVisible) events.isVisible = false else openEvents()
                Input.Keys.T -> rotateGhost()
                Input.Keys.O -> cycleLayer(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
                Input.Keys.C -> if (chain.isVisible) chain.isVisible = false else chainOfSelected()
                Input.Keys.Q -> rotateMap(-1)
                Input.Keys.E -> rotateMap(1)
                Input.Keys.ESCAPE, Input.Keys.BACK -> when {
                    outcomeBox.isVisible -> outcomeBox.isVisible = false
                    goodsWin.isVisible -> goodsWin.isVisible = false
                    nbWin.isVisible -> nbWin.isVisible = false
                    chain.isVisible -> chain.isVisible = false
                    layersWin.isVisible -> layersWin.isVisible = false
                    buildWin.isVisible -> buildWin.isVisible = false
                    trade.isVisible -> trade.isVisible = false
                    summary.isVisible -> summary.isVisible = false
                    stats.isVisible -> stats.isVisible = false
                    events.isVisible -> events.isVisible = false
                    menu.isVisible -> menu.isVisible = false
                    buildDef != -1 || sheetSel -> closeSheet()
                    else -> openMenu()
                }
                Input.Keys.F3 -> debug = !debug
                Input.Keys.F4 -> bench()   // B - меню строительства; замер на телефоне: adb shell input keyevent KEYCODE_F4
                Input.Keys.Z -> if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) undo() else return false
                else -> return false
            }
            return true
        }
    }

    // ---------- цикл ----------

    /** Окна по 10 с для «Авто»-графики: кадров и время; плохих окон подряд (первое после запуска не считается) */
    private var fpsFrames = 0
    private var fpsTime = 0f
    private var fpsWindows = 0
    private var fpsSlow = 0

    /**
     * Графика «Авто»: два окна подряд меньше FPS_LOW кадров/с - переключиться на лёгкую и запомнить.
     * Долгий кадр (вкладка была скрыта, приложение свёрнуто) сбрасывает окно, чтобы не путать с тормозами.
     */
    private fun watchFps(delta: Float) {
        if (Storage.graphics != "auto" || game.sprites.low) return
        if (delta > 0.25f) { fpsFrames = 0; fpsTime = 0f; return }
        fpsFrames++; fpsTime += delta
        if (fpsTime < 10f) return
        val fps = fpsFrames / fpsTime
        fpsFrames = 0; fpsTime = 0f
        if (fpsWindows++ == 0) return
        fpsSlow = if (fps < FPS_LOW) fpsSlow + 1 else 0
        if (fpsSlow >= 2) {
            Storage.autoLow = true
            game.applyGraphics()
            showToast(Lang.t("gfx_auto_low"), 4000)
        }
    }

    /** Первая песочница: справка «Как играть» открывается сама один раз (в обучении подсказки дают задания) */
    private var helpChecked = false

    override fun render(delta: Float) {
        if (!helpChecked) {
            helpChecked = true
            if (!Storage.helpSeen && world.scenarioDef == null) { Storage.helpSeen = true; openHelp() }
            if (paused) showToast(Lang.t("start_paused"), 4000)
        }
        val dt = delta.coerceAtMost(0.25f)
        val tickDt = 1f / Tune.TICKS_PER_SECOND
        val stopped = paused || menu.isVisible || trade.isVisible || outcomeBox.isVisible || stats.isVisible || events.isVisible || buildWin.isVisible || summary.isVisible ||
            chain.isVisible || layersWin.isVisible || goodsWin.isVisible || nbWin.isVisible
        followCenter()
        revealSelected(delta)
        if (!stopped) acc += dt * speeds[speedIdx]
        var steps = 0
        while (acc >= tickDt && steps < 40) {
            world.step(); acc -= tickDt; steps++
        }
        if (steps == 40) acc = 0f
        val alpha = if (stopped) 1f else (acc / tickDt).coerceIn(0f, 1f)

        val sa = agentIdx()
        renderer.selAgent = sa
        if (following && sa >= 0 && world.state[sa] != ST_INSIDE) {
            renderer.agentPoint(sa, alpha, agentPt)
            val k = 1f - kotlin.math.exp(-delta * 6f)
            cam.position.add((agentPt.x - cam.position.x) * k, (agentPt.y - cam.position.y) * k, 0f)
            clampCamera()
        }

        if (TimeUtils.millis() >= autosaveAt) autosave()
        watchFps(delta)
        // замер для проверки на телефоне: logcat по тегу stat
        if (TimeUtils.millis() >= statAt) {
            statAt = TimeUtils.millis() + 5000
            Gdx.app.log("stat", "fps ${Gdx.graphics.framesPerSecond} citizens ${world.agentCount} buildings ${world.buildingCount} speed ${speeds[speedIdx]}x${if (stopped) " paused" else ""}")
        }

        if (world.level != shownLevel) levelUp()
        if (TimeUtils.millis() >= refreshAt) {
            refreshAt = TimeUtils.millis() + 250
            refreshTop()
            Sfx.setSeason(world.season)
            // на клетке поставили здание - панель клетки закрывается
            if (tileCell >= 0 && world.buildingAt[tileCell] >= 0) { tileCell = -1; updateInfo() }
            if (sheetSel || buildDef != -1) updateInfo()
            if (trade.isVisible) updateTrade()
            if (stats.isVisible) updateStats()
            // окно соседей - раз в секунду (повозки, запас в посту)
            if (nbWin.isVisible && TimeUtils.millis() >= nbRefreshAt) { nbRefreshAt = TimeUtils.millis() + 1000; fillNeighbors() }
            tplKey = ""
            eventToasts()
            firstTips()
            checkAchievements()
            // на колокольчике - сколько сейчас бед в городе (а не непрочитанных записей)
            val bad = problems().count { !it.good }
            evBadge.setText(if (bad > 0) bad.toString() else "")
            updateQuests()
            traderBtn.isChecked = world.traderHere
        }
        toastHolder?.isVisible = TimeUtils.millis() < toastUntil
        // подсказка - под верхней панелью, сколько бы строк ресурсов в ней ни было
        val tb = topBarT; val tc = toastCell
        if (tb != null && tc != null && toastHolder?.isVisible == true && tc.padTop != tb.height + 8f) { tc.padTop(tb.height + 8f); toastHolder?.invalidate() }
        if (chain.isVisible && TimeUtils.millis() >= chainAt) { chainAt = TimeUtils.millis() + 1000; fillChain() }
        refreshLegend()
        updateTip()
        keyPan(dt)
        debugLabel.isVisible = debug
        if (world.outcome != shownOutcome) {
            shownOutcome = world.outcome
            Sfx.play(if (world.outcome == WON) "level" else "warn")
            showOutcome()
        }
        eventSounds()
        if (debug) debugLabel.setText("FPS ${Gdx.graphics.framesPerSecond}  objects ${renderer.objectsDrawn}  tick ${world.tick}  queue ${world.queueSize}")

        Gdx.gl.glClearColor(0.10f, 0.10f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val ghostOk = buildDef >= 0 && ghostX >= 0 && world.canBuild(buildDef, ghostX, ghostY, gDir)
        // выбранное здание или клетка подсвечены на карте - видно, о чём панель
        when {
            buildDef == -1 && selected >= 0 && world.bState[selected] != B_GONE -> {
                renderer.selX = world.bX[selected]; renderer.selY = world.bY[selected]; renderer.selSize = world.def(selected).size
            }
            buildDef == -1 && tileCell >= 0 -> { renderer.selX = tileCell % world.w; renderer.selY = tileCell / world.w; renderer.selSize = 1 }
            else -> renderer.selX = -1
        }
        if (photo) renderer.selX = -1
        tplView()
        game.batch.begin()
        renderer.draw(game.batch, alpha, if (!photo && ghostX >= 0 && buildDef >= 0) buildDef else -1, ghostX, ghostY, ghostOk, gDir)
        game.batch.end()
        if (photo) {
            if (photoShot) { photoShot = false; takeShot() }
            photoMsg.isVisible = TimeUtils.millis() < photoMsgUntil
            photoStage.act(delta)
            photoStage.draw()
        } else {
            stage.act(delta)
            stage.draw()
        }
    }

    /** ПК: WASD и стрелки двигают камеру (скорость не зависит от масштаба экрана) */
    private fun keyPan(dt: Float) {
        if (!pc) return
        var dx = 0f; var dy = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) dy += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy -= 1f
        if (dx == 0f && dy == 0f) return
        val speed = 700f * cam.zoom * dt
        cam.position.add(dx * speed, dy * speed, 0f)
        clampCamera()
    }

    // ---------- задания ----------

    /**
     * Панель заданий. Свёрнуто: предупреждение (если есть) и первое задание одной строкой; раскрыто: все задания,
     * награды и подсказки сценария. Новое задание с подсказкой раскрывает панель само.
     */
    private fun updateQuests() {
        val sc = world.scenarioDef
        val warn = StringBuilder()
        if (sc != null && world.outcome == PLAYING) {
            if (world.failTimer > 0) {
                val why = if (sc.failPop > 0 && world.agentCount < sc.failPop) Lang.t("fail_pop", sc.failPop) else Lang.t("fail_happy", sc.failHappy)
                warn.append(Lang.t("fail_warn", why, (world.failTimer + Tune.TICKS_PER_DAY - 1) / Tune.TICKS_PER_DAY))
            }
            if (sc.deadline > 0 && sc.deadline - world.day <= 3) {
                if (warn.isNotEmpty()) warn.append(NL)
                warn.append(Lang.t("deadline", sc.deadline, maxOf(0, sc.deadline - world.day + 1)))
            }
        }
        if (world.outcome == LOST) warn.append(Lang.t("lost_short"))
        val active = (0 until Tune.MAX_QUESTS).filter { world.qType[it] != 0 }
        val hints = StringBuilder()
        val key = active.joinToString(",") { "${world.qType[it]}:${world.qSrc[it]}:${world.qB[it]}" }
        if (key != questKey) {
            // новое задание сценария с подсказкой - показать подсказку
            if (questKey.isNotEmpty() && sc != null && active.any { world.qSrc[it] >= 0 && sc.quests[world.qSrc[it]].hint != null }) questOpen = true
            questKey = key
        }
        val sb = StringBuilder()
        for ((n, i) in active.withIndex()) {
            if (!questOpen && n > 0) { sb.append("  ").append(MUTED).append(Lang.t("quest_more", active.size - 1)).append("[]"); break }
            if (n > 0) sb.append(NL)
            sb.append(questText(i))
            if (questOpen) {
                val reward = rewardText(i)
                if (reward.isNotEmpty()) sb.append(" (").append(reward).append(")")
                val src = world.qSrc[i]
                if (sc != null && src >= 0) sc.quests[src].hint?.let { if (hints.isNotEmpty()) hints.append(NL); hints.append(Lang.t(it)) }
            }
        }
        if (sc != null && sc.deadline > 0 && questOpen && warn.isEmpty()) sb.append(NL).append(Lang.t("deadline", sc.deadline, maxOf(0, sc.deadline - world.day + 1)))
        val daily = if (world.dailyDate > 0) "[#F5D27A]" + Lang.t("daily_line", minOf(world.day, Tune.DAILY_DAYS), Tune.DAILY_DAYS,
            if (world.dailyFinalScore >= 0) world.dailyFinalScore else world.dailyScore()) + "[]" + (if (warn.isNotEmpty() || sb.isNotEmpty()) NL else "") else ""
        val text = daily + (if (warn.isNotEmpty()) "[#FF9070]$warn[]" + (if (sb.isNotEmpty()) NL else "") else "") + sb
        questBox.isVisible = text.isNotEmpty()
        if (text != questLabel.text.toString()) questLabel.setText(text)
        val h = hints.toString()
        if (h != hintLabel.text.toString()) hintLabel.setText(h)
        val want: Actor? = if (h.isNotEmpty()) hintLabel else null
        if (hintHolder.actor !== want) hintHolder.setActor(want)
    }

    private fun questText(i: Int): String {
        val v = world.questValue(i)
        val t = world.questTarget(i)
        val c = game.content
        return when (world.qType[i]) {
            town.data.Quest.STOCK -> Lang.t("q_stock", Lang.t(c.resources[world.qA[i]].name), v, t)
            town.data.Quest.BUILD -> Lang.t("q_build", Lang.t(c.buildings[world.qA[i]].name), v, t)
            town.data.Quest.POP -> Lang.t("q_pop", v, t)
            town.data.Quest.HAPPY -> Lang.t("q_happy", world.qB[i], v, t)
            town.data.Quest.LEVEL -> Lang.t("q_level", Lang.t(c.levels[t.coerceIn(0, c.levels.size - 1)].name))
            town.data.Quest.MADE -> Lang.t("q_made", Lang.t(c.resources[world.qA[i]].name), v, t)
            town.data.Quest.DEFEND -> Lang.t("q_defend", v, t)
            town.data.Quest.DELIVER -> Lang.t("q_deliver", v, t)
            town.data.Quest.GROWN -> Lang.t("q_grown", v, t)
            else -> Lang.t("q_gold", v, t)
        }
    }

    private fun rewardText(i: Int): String {
        val parts = ArrayList<String>()
        if (world.qGold[i] > 0) parts.add(Lang.t("gold", world.qGold[i]))
        if (world.qResR[i] >= 0 && world.qResN[i] > 0) parts.add("${Lang.t(game.content.resources[world.qResR[i]].name)} ${world.qResN[i]}")
        return if (parts.isEmpty()) "" else Lang.t("q_reward", parts.joinToString(", "))
    }

    /** Окно итога: победа - играть дальше или в меню; провал - заново, дальше без цели или в меню */
    private fun showOutcome() {
        if (world.outcome == PLAYING) return
        if (world.dailyDate > 0) { showDailyEnd(); return }
        val sc = world.scenarioDef ?: return
        outcomeBox.clearChildren()
        val col = Table()
        col.background = ui.panel
        col.pad(16f)
        col.defaults().width(300f).padBottom(10f)
        val won = world.outcome == WON
        col.add(Label(Lang.t(if (won) "won" else "lost"), ui.title)).row()
        val text = Label(if (won) Lang.t("won_text", Lang.t(sc.name), world.day) else Lang.t("lost_text", Lang.t(sc.name)), ui.label)
        text.setWrap(true)
        col.add(text).row()
        if (won) {
            // звёзды кампании: по запасу времени до срока
            val stars = world.campaignStars()
            town.Storage.recordStars(sc.id, stars)
            val row = Table()
            for (k in 0 until 3) named("hud_star")?.let { r -> val im = ui.image(r, 32f); if (k >= stars) im.color = Color(0.3f, 0.3f, 0.3f, 0.7f); row.add(im).size(32f).pad(2f) }
            col.add(row).row()
            col.add(Label(Lang.t("camp_stars_hint"), ui.labelSmall).also { it.setWrap(true) }).row()
        }
        fun add(key: String, action: () -> Unit) {
            val b = TextButton(Lang.t(key), ui.button)
            b.onClick(action)
            col.add(b).row()
        }
        if (!won) add("retry") { game.play(World.create(game.content, sc)) }
        add("summary") { outcomeBox.isVisible = false; openSummary() }
        add("keep_playing") { outcomeBox.isVisible = false }
        add("to_title") { autosave(); game.showTitle() }
        outcomeBox.add(col)
        outcomeBox.isVisible = true
    }

    /** Партия дня окончена: очки с разбивкой, рекорд за этот день, кнопки */
    private fun showDailyEnd() {
        val score = if (world.dailyFinalScore >= 0) world.dailyFinalScore else world.dailyScore()
        val record = Storage.recordDaily(world.dailyDate, score)
        val best = maxOf(score, Storage.dailyBest(world.dailyDate))
        outcomeBox.clearChildren()
        val col = Table()
        col.background = ui.window
        col.pad(16f)
        val w = minOf(320f, Gdx.graphics.width / scale - 40f)
        col.defaults().width(w).padBottom(8f)
        col.add(Label(Lang.t("daily_over"), ui.title)).row()
        val text = Label(Lang.t("daily_score", score, best), ui.labelBold)
        text.setWrap(true)
        col.add(text).row()
        if (record) col.add(Label("[#F5D27A]" + Lang.t("daily_record") + "[]", ui.labelBold)).row()
        var zebra = false
        fun kv(key: String, value: Int, mul: String, pts: Int) {
            val row = Table()
            if (zebra) row.background = ui.rowAlt
            zebra = !zebra
            row.add(Label(Lang.t(key), ui.labelMuted)).expandX().left().padLeft(6f)
            row.add(Label("$value $mul = $pts", ui.labelBold)).right().padRight(6f)
            col.add(row).height(28f).padBottom(0f).row()
        }
        kv("daily_pop", world.agentCount, "×10", world.agentCount * 10)
        kv("daily_happy", world.townHappy, "×5", world.townHappy * 5)
        kv("daily_quests", world.questsDone, "×30", world.questsDone * 30)
        kv("daily_fame", world.prestige, "×2", world.prestige * 2)
        kv("daily_level", world.level, "×100", world.level * 100)
        kv("daily_gold", world.gold, "/10", world.gold / 10)
        kv("daily_crown", world.crDone, "×50", world.crDone * 50)
        fun add(key: String, action: () -> Unit) {
            val b = TextButton(Lang.t(key), ui.button)
            b.onClick(action)
            col.add(b).padTop(4f).row()
        }
        add("summary") { outcomeBox.isVisible = false; openSummary() }
        add("keep_playing") { outcomeBox.isVisible = false }
        add("to_title") { autosave(); game.showTitle() }
        outcomeBox.add(col)
        outcomeBox.isVisible = true
    }

    // ---------- статистика и события ----------

    /** Затемнённое окно поверх игры */
    private fun overlay(): Table {
        val m = Table()
        m.setFillParent(true)
        m.background = ui.dim
        m.touchable = Touchable.enabled
        m.isVisible = false
        return m
    }

    /** Окно: заголовок, листаемое содержимое, необязательная кнопка и «Закрыть» */
    private fun fillOverlay(m: Table, title: String, body: Table, extra: Actor?, wide: Boolean = false, fit: Boolean = false) {
        val col = Table()
        col.background = ui.window
        col.pad(12f)
        val w = minOf(if (wide) 900f else if (pc) 640f else 380f, Gdx.graphics.width / scale - 24f)
        // заголовок крупнее текста и тонкая линия под ним
        col.add(Label(title, ui.labelTitle)).width(w).left().padBottom(4f).row()
        col.add(Image(ui.rule)).width(w).height(1f).padBottom(8f).row()
        body.top().left()
        // ползунок справа - видно, что окно листается
        val pane = ScrollPane(body, ui.scroll)
        pane.setScrollingDisabled(true, false)
        pane.setFadeScrollBars(false)
        pane.setOverscroll(false, false)
        // на телефоне окно почти во весь экран: больше видно без прокрутки
        // fit - окно по содержимому (не выше того же предела): у короткого нет пустой половины
        val paneH = Gdx.graphics.height / scale * (if (pc) 0.6f else 0.68f)
        if (fit) col.add(pane).width(w).maxHeight(paneH).row() else col.add(pane).width(w).height(paneH).row()
        val row = Table()
        if (extra != null) row.add(extra).padRight(8f)
        val close = TextButton(Lang.t("close"), ui.button)
        close.onClick { m.isVisible = false }
        row.add(close).width(160f)
        col.add(row).padTop(8f)
        m.add(col)
        bodyWidth[body] = w
    }

    private val bodyWidth = HashMap<Table, Float>()

    private lateinit var summary: Table
    private lateinit var summaryBody: Table

    /** Итоги партии: главное числами и графики по суткам (жители, довольство, золото) */
    private fun openSummary() {
        summary.isVisible = true
        val body = summaryBody
        body.clearChildren()
        val w = bodyWidth[body] ?: 360f
        val days = minOf(world.dayCount, Tune.HISTORY)
        val start = world.dayCount - days
        fun series(arr: IntArray) = IntArray(days) { arr[(start + it) % Tune.HISTORY] }
        val pop = series(world.dayPop); val happy = series(world.dayHappy); val gold = series(world.dayGold)
        var zebra = false
        fun kv(key: String, value: String) {
            val row = Table()
            if (zebra) row.background = ui.rowAlt
            zebra = !zebra
            row.add(Label(key, ui.labelMuted)).expandX().left().padLeft(6f)
            row.add(Label(value, ui.labelBold)).right().padRight(6f)
            body.add(row).width(w).height(30f).row()
        }
        kv(Lang.t("sum_days"), world.day.toString())
        kv(Lang.t("sum_level"), Lang.t(game.content.levels[world.level].name))
        kv(Lang.t("sum_pop"), Lang.t("sum_now_peak", world.agentCount, maxOf(world.agentCount, pop.maxOrNull() ?: 0)))
        kv(Lang.t("st_happiness"), "${world.townHappy}%")
        kv(Lang.t("sum_gold"), Lang.t("sum_now_peak", world.gold, maxOf(world.gold, gold.maxOrNull() ?: 0)))
        if (world.threatsOn) kv(Lang.t("sum_defended"), Lang.t("sum_defended_of", world.raidsRepelled + world.raidsStopped, world.raidsTotal))
        kv(Lang.t("sum_quests"), world.questsDone.toString())
        // больше всего произведено
        val made = game.content.resources.map { it to world.madeTotal[it.index] + world.produced(it.index, 0) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        if (made.isNotEmpty()) {
            body.add(Label(Lang.t("sum_made"), ui.labelHead)).width(w).left().padTop(10f).padBottom(4f).row()
            val grid = Table()
            for ((k, p) in made.withIndex()) {
                val cell = Table()
                sprites.named("res_${p.first.id}")?.let { cell.add(ui.image(it.r, 22f)).size(22f).padRight(4f) }
                cell.add(Label(short(p.second), ui.labelBold))
                grid.add(cell).width(w / 3).left().pad(2f)
                if (k % 3 == 2) grid.row()
            }
            body.add(grid).width(w).left().row()
        }
        if (days < 2) {
            body.add(Label(Lang.t("sum_first_days"), ui.labelMuted).also { it.setWrap(true) }).width(w).left().padTop(8f).row()
            return
        }
        fun chart(titleKey: String, values: IntArray, color: Color, max: Int) {
            body.add(Label(Lang.t(titleKey) + "  " + Lang.t("sum_range", values.minOrNull() ?: 0, values.maxOrNull() ?: 0), ui.labelHead)).width(w).left().padTop(10f).padBottom(4f).row()
            val frame = Table()
            frame.background = ui.chip
            frame.add(Chart(sprites.white, downsample(values, 90), color, max)).width(w - 12f).height(70f).pad(6f)
            body.add(frame).width(w).row()
        }
        chart("sum_chart_pop", pop, CHART_POP, maxOf(1, pop.maxOrNull() ?: 1))
        chart("sum_chart_happy", happy, chartGood(), 100)
        chart("sum_chart_gold", gold, town.ui.Ui.GOLD, maxOf(1, gold.maxOrNull() ?: 1))
        body.add(Label(Lang.t("sum_days_axis", world.day - days + 1, world.day), ui.labelSmall).also { it.color = Color(0.8f, 0.74f, 0.64f, 1f) }).width(w).left().padTop(4f).row()
    }

    private fun chartGood(): Color = Color(Palette.good()).lerp(0.62f, 0.68f, 0.32f, 1f, 0.35f)

    /** Не больше n столбиков: соседние сутки - средним */
    private fun downsample(v: IntArray, n: Int): IntArray {
        if (v.size <= n) return v
        return IntArray(n) { i ->
            val a = i * v.size / n; val b = maxOf(a + 1, (i + 1) * v.size / n)
            var s = 0L
            for (k in a until b) s += v[k]
            (s / (b - a)).toInt()
        }
    }

    /**
     * График из белой области атласа (без отдельных текстур): линия по суткам с полупрозрачной заливкой под ней,
     * точки между сутками - по прямой; столбиками по 3-4 суток выглядело как заглушка
     */
    private class Chart(val white: com.badlogic.gdx.graphics.g2d.TextureRegion, val values: IntArray, val barColor: Color, val max: Int) : Actor() {
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            if (values.isEmpty()) return
            val old = batch.packedColor
            val n = values.size
            val cols = maxOf(1, width.toInt() / 2)
            val cw = width / cols
            fun at(i: Int): Float {
                val t = if (n == 1) 0f else i.toFloat() / maxOf(1, cols - 1) * (n - 1)
                val a = t.toInt().coerceIn(0, n - 1); val b = minOf(n - 1, a + 1)
                val v = values[a] + (values[b] - values[a]) * (t - a)
                return maxOf(1f, height * maxOf(0f, v) / max)
            }
            // сетка: три тонкие линии на четвертях высоты - видно, где середина и верх
            batch.setColor(1f, 1f, 1f, 0.08f * parentAlpha)
            for (q in 1..3) batch.draw(white, x, y + height * q / 4f, width, 1f)
            for (i in 0 until cols) {
                val h = at(i)
                batch.setColor(barColor.r, barColor.g, barColor.b, 0.18f * barColor.a * parentAlpha)
                batch.draw(white, x + i * cw, y, cw, h)
                // линия: столбик в 2 точки у верха, со ступенькой до соседа - без разрывов на крутых местах
                val hn = if (i + 1 < cols) at(i + 1) else h
                val lo = minOf(h, hn) - 1.5f; val hi = maxOf(h, hn) + 0.5f
                batch.setColor(barColor.r, barColor.g, barColor.b, barColor.a * parentAlpha)
                batch.draw(white, x + i * cw, y + lo, cw, hi - lo)
            }
            // точка на последнем значении - «сейчас»
            val last = at(cols - 1)
            batch.setColor(barColor.r, barColor.g, barColor.b, barColor.a * parentAlpha)
            batch.draw(white, x + width - 4f, y + last - 3f, 5f, 5f)
            batch.packedColor = old
        }
    }

    /** Новые достижения - сообщением (проверка раз в пару секунд, на симуляцию не влияет) */
    private var achCheckAt = 0L
    private fun checkAchievements() {
        if (TimeUtils.millis() < achCheckAt) return
        achCheckAt = TimeUtils.millis() + 2000
        val fresh = town.Achievements.check(world)
        if (fresh.isEmpty()) return
        val id = fresh.last()
        val sc = if (id.startsWith("win_")) game.content.scenarios.firstOrNull { "win_" + it.id == id } else null
        showToast(Lang.t("ach_unlocked", if (sc != null) Lang.t("ach_win", Lang.t(sc.name)) else Lang.t("ach_$id")), 4000)
        Sfx.play("level")
    }

    /** Вкладка статистики: 0 - обзор, 1 - прогноз, 2 - графики; товар на графике запаса (-1 - первый из бывших) */
    private var statsTab = 0
    private var chartRes = -1

    /** Графики по суткам: жители, довольство, золото и запас выбранного товара на складах */
    private fun statsCharts(w: Float) {
        val days = minOf(world.dayCount, Tune.HISTORY)
        if (days < 2) {
            statsBody.add(Label(Lang.t("sum_first_days"), ui.labelMuted).also { it.setWrap(true) }).width(w).left().padTop(8f).row()
            return
        }
        val start = world.dayCount - days
        fun series(arr: IntArray) = IntArray(days) { arr[(start + it) % Tune.HISTORY] }
        // ландшафт - графики в две колонки (иначе не помещаются по высоте), портрет - в одну
        val two = stage.width > stage.height
        val cw = if (two) (w - 12f) / 2f else w
        val blocks = ArrayList<Table>()
        fun block(title: Actor, values: IntArray, color: Color, max: Int, under: String? = null): Table {
            val t = Table()
            t.add(title).width(cw).left().padTop(8f).padBottom(3f).row()
            val frame = Table()
            frame.background = ui.chip
            frame.add(Chart(sprites.white, downsample(values, 90), color, max)).width(cw - 12f).height(if (two) 50f else 58f).pad(6f)
            t.add(frame).width(cw).row()
            if (under != null) t.add(Label(under, ui.labelSmall).also { it.color = Color(0.8f, 0.74f, 0.64f, 1f) }).width(cw).left().padTop(2f).row()
            return t
        }
        fun chart(title: String, values: IntArray, color: Color, max: Int) {
            val l = Label(title + "  " + Lang.t("sum_range", values.minOrNull() ?: 0, values.maxOrNull() ?: 0), ui.labelHead)
            l.setEllipsis(true)
            blocks.add(block(l, values, color, max))
        }
        val pop = series(world.dayPop); val gold = series(world.dayGold)
        chart(Lang.t("sum_chart_pop"), pop, CHART_POP, maxOf(1, pop.maxOrNull() ?: 1))
        chart(Lang.t("sum_chart_happy"), series(world.dayHappy), chartGood(), 100)
        chart(Lang.t("sum_chart_gold"), gold, town.ui.Ui.GOLD, maxOf(1, gold.maxOrNull() ?: 1))
        // запас товара: выбор стрелками по товарам, что уже бывали в городе
        val list = game.content.resources.indices.filter { it in seenRes }
        if (list.isNotEmpty()) {
            if (chartRes !in list) chartRes = list[0]
            val r = chartRes
            val sel = Table()
            val prev = TextButton("<", ui.button); val next = TextButton(">", ui.button)
            val at = list.indexOf(r)
            prev.onClick { chartRes = list[(at + list.size - 1) % list.size]; updateStats() }
            next.onClick { chartRes = list[(at + 1) % list.size]; updateStats() }
            sel.add(prev).width(44f).height(44f)
            val mid = Table()
            sprites.named("res_${game.content.resources[r].id}")?.let { mid.add(ui.image(it.r, 22f)).size(22f).padRight(6f) }
            // в узкой колонке - только название товара (значок рядом), иначе «Запас: ...»
            val rn = Lang.t(game.content.resources[r].name)
            val name = Label(if (two) rn else Lang.t("ch_stock", rn), ui.labelBold)
            name.setEllipsis(true)
            mid.add(name).minWidth(0f).expandX().fillX()
            sel.add(mid).expandX().fillX().minWidth(0f)
            sel.add(next).width(44f).height(44f)
            val sd = minOf(world.dayCount, Tune.HISTORY_RES)
            val s0 = world.dayCount - sd
            val stock = IntArray(sd + 1) { if (it < sd) world.dayStock[((s0 + it) % Tune.HISTORY_RES) * game.content.resources.size + r] else world.totalStock(r) }
            blocks.add(block(sel, stock, Color(0.95f, 0.75f, 0.4f, 1f), maxOf(1, stock.maxOrNull() ?: 1),
                (if (two) Lang.t("ch_stock", rn) + ", " else "") + Lang.t("sum_range", stock.minOrNull() ?: 0, stock.maxOrNull() ?: 0)))
        }
        if (two) {
            for (i in blocks.indices step 2) {
                val row = Table()
                row.add(blocks[i]).width(cw).top()
                if (i + 1 < blocks.size) row.add(blocks[i + 1]).width(cw).top().padLeft(12f)
                statsBody.add(row).width(w).left().row()
            }
        } else for (bl in blocks) statsBody.add(bl).width(w).row()
        statsBody.add(Label(Lang.t("sum_days_axis", world.day - days + 1, world.day), ui.labelSmall).also { it.color = Color(0.8f, 0.74f, 0.64f, 1f) }).width(w).left().padTop(4f).row()
    }

    /** Товар, у которого в статистике открыт лимит города (-1 - ни у какого) */
    private var statsSel = -1

    /** Лимит города: нет -> 50 -> 60 ...; шаг 10 до 100, дальше 50; минус ниже 10 снимает лимит */
    private fun townLimitStep(cur: Int, d: Int): Int {
        if (d > 0) return if (cur < 0) 50 else cur + (if (cur < 100) 10 else 50)
        if (cur < 0) return -1
        if (cur <= 10) return -1
        return cur - (if (cur <= 100) 10 else 50)
    }

    private fun openStats() {
        stats.isVisible = true
        updateStats()
    }

    /** Статистика: жители и занятость, налоги, нехватки в домах, по ресурсам - запас, приход и расход за вчера */
    private fun updateStats() {
        val w = bodyWidth[statsBody] ?: 360f
        statsBody.clearChildren()
        var carriers = 0; var busy = 0; var workers = 0; var builders = 0
        for (a in 0 until world.agentCount) when (world.job[a]) {
            JOB_CARRIER -> { carriers++; if (world.carryN[a] > 0 || world.state[a] == ST_MOVE) busy++ }
            JOB_WORKER -> workers++
            else -> builders++
        }
        val warn = Color(Palette.warn()); val green = Color(Palette.good()); val red = Color(Palette.bad())
        var zebra = false
        fun head(text: String) {
            statsBody.add(Label(text, ui.labelHead)).width(w).left().padTop(10f).padBottom(4f).row()
            zebra = false
        }
        /** Строка «подпись - значение», через одну - с подложкой */
        fun kv(key: String, value: String, color: Color? = null) {
            val row = Table()
            if (zebra) row.background = ui.rowAlt
            zebra = !zebra
            row.add(Label(key, ui.labelMuted)).expandX().left().padLeft(6f)
            val v = Label(value, ui.labelBold)
            if (color != null) v.color = color
            row.add(v).right().padRight(6f)
            statsBody.add(row).width(w).height(30f).row()
        }
        fun note(text: String) {
            val l = Label(text, ui.label)
            l.color = warn
            l.setWrap(true)
            statsBody.add(l).width(w).left().padTop(4f).row()
        }

        val tabRow = Table()
        for ((i, k) in listOf("st_tab_over", "st_tab_fc", "st_tab_charts").withIndex()) {
            val b = TextButton(Lang.t(k), ui.toggle)
            b.isChecked = statsTab == i
            b.onClick { statsTab = i; statsSel = -1; updateStats() }
            tabRow.add(b).expandX().fillX().padRight(if (i < 2) 4f else 0f)
        }
        statsBody.add(tabRow).width(w).padBottom(4f).row()
        if (statsTab == 1) {
            head(Lang.t("st_h_fc"))
            for (k in 0 until FC_COUNT) {
                val d = world.forecastDays(k)
                val lvl = world.forecastLevel(k)
                val text = if (d == 0) Lang.t("fc_none") else if (d > 0) Lang.t("fc_days", d) else if (k == FC_HEAT && world.season != AUTUMN && world.season != WINTER) Lang.t("fc_heat_later") else Lang.t("fc_ok")
                kv(Lang.t("fc_name_$k"), text, if (lvl == 2) red else if (lvl == 1) Color(1f, 0.85f, 0.35f, 1f) else green)
            }
            val l = Label(Lang.t("fc_note"), ui.labelMuted)
            l.setWrap(true)
            statsBody.add(l).width(w).left().padTop(8f).row()
            return
        }
        if (statsTab == 2) { statsCharts(w); return }

        head(Lang.t("st_h_people"))
        kv(Lang.t("st_citizens"), "${world.agentCount} / ${world.totalBeds()}")
        val busyPct = if (carriers > 0) busy * 100 / carriers else 0
        kv(Lang.t("st_carriers"), Lang.t("st_carriers_val", carriers, busyPct), if (busyPct >= 90) warn else null)
        kv(Lang.t("st_workers"), workers.toString())
        kv(Lang.t("st_builders"), builders.toString())
        kv(Lang.t("st_happiness"), "${world.townHappy}%", if (world.townHappy < Tune.ARRIVE_MIN) warn else null)
        if (carriers > 0 && busyPct >= 90) note(Lang.t("st_carriers_busy"))
        if (world.barrowR >= 0) kv(Lang.t("st_carts"), Lang.t("st_carts_val", world.cartsInUse(1), world.totalStock(world.barrowR)))
        if (world.wagonR >= 0) kv(Lang.t("st_wagons"), Lang.t("st_carts_val", world.cartsInUse(2), world.totalStock(world.wagonR)))
        kv(Lang.t("st_fame"), Lang.t("st_fame_val", world.prestige, world.fameDaily()))

        head(Lang.t("st_h_gold"))
        fun signed(n: Int) = (if (n > 0) "+" else "") + n
        kv(Lang.t("st_tax"), Lang.t("st_two", world.lastTax, taxPerDay()))
        kv(Lang.t("st_upkeep_row"), Lang.t("st_two", world.lastUpkeep, world.upkeepPerDay()))
        val netNow = taxPerDay() - world.upkeepPerDay()
        kv(Lang.t("st_net_gold"), Lang.t("st_two_s", signed(world.lastTax - world.lastUpkeep), signed(netNow)), if (netNow < 0) red else green)
        if (world.badHarvest) note(Lang.t("ev_bad_harvest"))
        if (world.harshWinter) note(Lang.t("ev_harsh_winter"))

        // нехватки: сколько домов без каждой потребности
        val lacks = game.content.needs.mapNotNull { n ->
            var c = 0
            for (h in 0 until world.buildingCount) {
                if (world.bState[h] != B_ACTIVE || !world.def(h).isHouse || world.bResidents[h] == 0) continue
                if (world.bNeedOk[h] and (1 shl n.index) == 0) c++
            }
            if (c > 0) n to c else null
        }
        if (lacks.isNotEmpty()) {
            head(Lang.t("st_h_lacks"))
            for ((n, c) in lacks) kv(Lang.t(n.name), Lang.t("st_houses", c), warn)
        }

        // таблица ресурсов: значок и название, запас, приход, расход, итог за вчера
        head(Lang.t("st_h_res"))
        val numW = if (w < 420f) 50f else 70f
        val nameW = w - 4 * numW
        val hrow = Table()
        hrow.add(Label("", ui.labelMuted)).width(nameW)
        for (k in listOf("st_stock", "st_made", "st_spent", "st_net")) {
            val l = Label(Lang.t(k), ui.labelSmall)
            l.color = ui.labelMuted.fontColor
            l.setAlignment(Align.right)
            hrow.add(l).width(numW).right().padRight(if (k == "st_net") 6f else 0f)
        }
        statsBody.add(hrow).width(w).height(26f).row()
        zebra = true
        for (res in game.content.resources) {
            val r = res.index
            val stock = world.totalStock(r); val p = world.produced(r); val u = world.used(r)
            if (stock == 0 && p == 0 && u == 0) continue
            val row = Table()
            if (zebra) row.background = ui.rowAlt
            zebra = !zebra
            val name = Table()
            name.left()
            sprites.named("res_${res.id}")?.let { name.add(ui.image(it.r, 22f)).size(22f).padLeft(4f).padRight(6f) }
            // лимит города на товар - рядом с названием: «(до 100)»
            val lim = world.townLimit[r]
            val nl = Label(Lang.t(res.name) + if (lim >= 0) " " + Lang.t("limit_tag", lim) else "", ui.label)
            nl.setEllipsis(true)
            name.add(nl).width(nameW - 36f).left()
            row.add(name).width(nameW).left()
            tip(row, { Lang.t("limit_town_tip") }) { statsSel = if (statsSel == r) -1 else r; updateStats() }
            fun num(text: String, color: Color? = null, last: Boolean = false) {
                val l = Label(text, ui.labelBold)
                if (color != null) l.color = color
                l.setAlignment(Align.right)
                row.add(l).width(numW).right().padRight(if (last) 6f else 0f)
            }
            val net = p - u
            num(short(stock)); num(if (p > 0) "+$p" else "0"); num(if (u > 0) "-$u" else "0")
            num(signed(net), if (net > 0) green else if (net < 0) red else null, last = true)
            statsBody.add(row).width(w).height(32f).row()
            // выбранный товар: сколько хранить в городе (мастерские встают, когда на складах столько)
            if (statsSel == r) {
                val ctl = Table()
                ctl.background = ui.chip
                val cap = Label(Lang.t("limit_town"), ui.label)
                cap.setWrap(true)
                ctl.add(cap).expandX().fillX().left().padLeft(6f)
                val less = TextButton("–", ui.button); val more = TextButton("+", ui.button)
                less.onClick { world.submit(Command.STOCK_LIMIT, r, townLimitStep(world.townLimit[r], -1), 0); updateStats() }
                more.onClick { world.submit(Command.STOCK_LIMIT, r, townLimitStep(world.townLimit[r], 1), 0); updateStats() }
                ctl.add(less).padRight(2f)
                ctl.add(Label(if (lim < 0) Lang.t("limit_none_val") else lim.toString(), ui.labelBold).also { it.setAlignment(Align.center) }).width(54f)
                ctl.add(more).padLeft(2f).padRight(4f)
                statsBody.add(ctl).width(w).padBottom(2f).row()
                // откуда и куда: цепочка товара
                val chainBtn = iconText(Lang.t("chain_btn"), ui.button, "hud_chain")
                chainBtn.onClick { openChain(r) }
                val cb = Table()
                cb.add(chainBtn).right().expandX()
                statsBody.add(cb).width(w).padBottom(4f).row()
            }
        }
        if (world.histDays == 0) {
            val l = Label(Lang.t("st_first_day"), ui.labelMuted)
            l.setWrap(true)
            statsBody.add(l).width(w).left().padTop(6f).row()
        }
    }

    /** Живая проблема города: подпись, значок, здания (касание строки перебирает их по кругу), хорошая ли это новость */
    private class Problem(val key: String, val text: String, val icon: com.badlogic.gdx.graphics.g2d.TextureRegion?, val list: List<Int>, val good: Boolean = false)
    /** Какое здание группы показали последним (по ключу группы) */
    private val problemCursor = HashMap<String, Int>()

    /**
     * Что сейчас не так в городе - по состоянию мира, а не по журналу: пожары, нехватка еды/воды/дров по видам,
     * болезни, долги по содержанию, простои мастерских по причинам; и хорошее - дома, готовые к улучшению
     */
    private fun problems(): List<Problem> {
        val out = ArrayList<Problem>()
        val active = (0 until world.buildingCount).filter { world.bState[it] == B_ACTIVE }
        fun add(key: String, text: String, icon: String?, list: List<Int>, good: Boolean = false) {
            if (list.isNotEmpty()) out += Problem(key, text, icon?.let { sprites.named(it)?.r }, list, good)
        }
        for (i in 0 until Tune.MAX_RAIDS) if (world.rType[i] != 0 && world.rTarget[i] >= 0) {
            val wolves = world.rType[i] == RAID_WOLVES
            add("raid$i", Lang.t(if (wolves) "ev_wolves_coming" else "ev_bandits_coming", bname(world.rTarget[i])),
                if (sprites.named("person_3_0_0") != null) "person_3_0_0" else "icon_fire", listOf(world.rTarget[i]))
        }
        add("fire", Lang.t("prob_fire", active.count { world.bFire[it] > 0 }), "icon_fire", active.filter { world.bFire[it] > 0 })
        for (n in game.content.needs) {
            if (!n.vital || n.level > world.level) continue
            val hit = active.filter { world.def(it).isHouse && world.bResidents[it] > 0 && n.tier <= world.def(it).tier && world.bNeedOk[it] and (1 shl n.index) == 0 }
            add("need_${n.index}", Lang.t("prob_need", Lang.t(n.name).lowercase(), hit.size), "icon_hungry", hit)
        }
        val sick = active.filter { world.bSick[it] > 0 }
        add("sick", Lang.t("prob_sick", sick.size), "icon_sick", sick)
        val unpaid = active.filter { world.bUnpaid[it] != 0 }
        add("unpaid", Lang.t("prob_unpaid", unpaid.size), if (sprites.named("icon_unpaid") != null) "icon_unpaid" else "icon_no_workers", unpaid)
        for ((st, icon) in listOf(BS_NO_WORKERS to "icon_no_workers", BS_NO_INPUT to "icon_no_input", BS_OUTPUT_FULL to "icon_output_full",
            BS_NO_RESOURCE to "icon_no_resource", BS_FAR to "icon_far")) {
            val hit = active.filter { (world.def(it).isProducer || world.def(it).isService) && world.bStatus[it] == st && world.bUnpaid[it] == 0 }
            add("st_$st", Lang.t("prob_status", Lang.t(statusKey(st)), hit.size), icon, hit)
        }
        if (world.crR >= 0 && world.hall >= 0) add("crown", crownLine(), "hud_crown", listOf(world.hall), good = true)
        val ready = active.filter { world.def(it).isHouse && world.bUpg[it] == 0 && world.houseReady(it) }
        add("ready", Lang.t("prob_ready", ready.size), "icon_upgrade", ready, good = true)
        return out
    }

    /** Важные события для раздела «Недавно» (остальное видно по состоянию города или в заданиях) */
    private val importantEvents = intArrayOf(EV_FIRE, EV_FIRE_OUT, EV_SICK, EV_LEFT, EV_LEVEL, EV_BAD_HARVEST, EV_HARSH_WINTER, EV_UNPAID, EV_FESTIVAL,
        EV_RAID_REPELLED, EV_RAID_BLOCKED, EV_WOLVES, EV_BANDITS, EV_CROWN, EV_CROWN_DONE, EV_CROWN_FAILED, EV_SHIP, EV_FORECAST,
        EV_FLOOD, EV_DROUGHT, EV_CONTRACT_FAIL, EV_WAGON_BACK)

    /**
     * Окно «Город»: сверху - что не так сейчас (группами, касание - к следующему зданию группы),
     * ниже - недавние важные события, одинаковые за сутки - одной строкой «×N»
     */
    private fun openEvents() {
        events.isVisible = true
        evSeen = world.evCount
        eventsBody.clearChildren()
        val w = bodyWidth[eventsBody] ?: 360f
        fun head(key: String) { eventsBody.add(Label(Lang.t(key), ui.labelHead)).left().padTop(6f).padBottom(4f).row() }
        fun line(icon: com.badlogic.gdx.graphics.g2d.TextureRegion?, text: String, color: Color?): Table {
            val row = Table()
            row.background = ui.chip
            if (icon != null) row.add(ui.image(icon, 22f)).size(22f).pad(5f) else row.add().size(22f).pad(5f)
            val l = Label(text, ui.label)
            l.setWrap(true)
            if (color != null) l.color = color
            row.add(l).width(w - 46f).left().pad(5f)
            eventsBody.add(row).width(w).padBottom(3f).row()
            return row
        }
        head("ev_now")
        val probs = problems()
        if (probs.none { !it.good }) line(sprites.named("hud_happy")?.r, Lang.t("ev_all_good"), Palette.good())
        for (p in probs) {
            val row = line(p.icon, p.text, if (p.good) Palette.good() else null)
            tip(row, { p.text }) {
                // по кругу: следующее здание этой группы
                val cur = problemCursor[p.key] ?: -1
                val next = p.list.firstOrNull { it > cur } ?: p.list[0]
                problemCursor[p.key] = next
                events.isVisible = false
                focus(next)
            }
        }
        head("ev_recent")
        val first = maxOf(0, world.evCount - Tune.EVENTS)
        val shown = ArrayList<Pair<IntArray, Int>>()
        for (n in world.evCount - 1 downTo first) {
            val e = world.eventAt(n)
            if (e[0] !in importantEvents || (e[0] == EV_FIRE_OUT && e[2] != 1)) continue
            // за одни сутки одинаковые события - одной строкой
            val same = shown.indexOfFirst { it.first[0] == e[0] && it.first[3] / Tune.TICKS_PER_DAY == e[3] / Tune.TICKS_PER_DAY }
            if (same >= 0) shown[same] = shown[same].first to shown[same].second + 1 else shown.add(e to 1)
            if (shown.size >= RECENT_EVENTS) break
        }
        if (shown.isEmpty()) line(null, Lang.t("no_events"), Color(0.80f, 0.74f, 0.64f, 1f))
        for ((e, times) in shown) {
            val text = Lang.t("day", e[3] / Tune.TICKS_PER_DAY + 1) + ". " + (if (times > 1) eventGroupText(e[0], times) else eventText(e))
            val row = line(eventIcon(e[0]), text, null)
            val b = eventBuilding(e)
            if (b >= 0 && times == 1) tip(row, { eventText(e) }) { events.isVisible = false; focus(b) }
        }
    }

    /** Несколько одинаковых событий за сутки - одной фразой с числом */
    private fun eventGroupText(type: Int, n: Int): String = when (type) {
        EV_FIRE -> Lang.t("evg_fire", n)
        EV_FIRE_OUT -> Lang.t("evg_burned", n)
        EV_SICK -> Lang.t("evg_sick", n)
        EV_LEFT -> Lang.t("evg_left", n)
        else -> eventText(intArrayOf(type, -1, 0, 0)) + "  ×$n"
    }

    /** Здание события, если оно ещё стоит (-1 - нет) */
    private fun eventBuilding(e: IntArray): Int = when (e[0]) {
        EV_CROWN, EV_CROWN_DONE, EV_CROWN_FAILED -> world.hall
        EV_LEVEL, EV_QUEST, EV_BAD_HARVEST, EV_HARSH_WINTER, EV_UNPAID, EV_FESTIVAL, EV_RAID_BLOCKED, EV_DAILY_END, EV_FORECAST,
        EV_FLOOD, EV_DROUGHT, EV_CONTRACT, EV_WAGON_OUT, EV_WAGON_BACK, EV_CONTRACT_FAIL -> -1
        else -> if (e[1] in 0 until world.buildingCount && world.bState[e[1]] != B_GONE) e[1] else -1
    }

    private fun bname(b: Int) = if (b in 0 until world.buildingCount && world.bState[b] != B_GONE) Lang.t(world.def(b).name) else "?"

    private fun eventText(e: IntArray): String = when (e[0]) {
        // чего именно нет (маска потребностей; 0 - старое сохранение)
        EV_HUNGRY -> if (e[2] != 0) Lang.t("ev_hungry_what", bname(e[1]), game.content.needs.filter { e[2] and (1 shl it.index) != 0 }.joinToString(", ") { Lang.t(it.name).lowercase() })
            else Lang.t("ev_hungry", bname(e[1]))
        EV_LEFT -> Lang.t("ev_left", bname(e[1]))
        EV_BUILT -> Lang.t("ev_built", bname(e[1]))
        EV_UPGRADING -> Lang.t("ev_upgrading", bname(e[1]))
        EV_UPGRADED -> Lang.t("ev_upgraded", bname(e[1]))
        EV_FIRE -> Lang.t(if (e[2] == 1) "ev_fire_watch" else "ev_fire", bname(e[1]))
        EV_FIRE_OUT -> Lang.t(if (e[2] == 1) "ev_fire_burned" else "ev_fire_out", bname(e[1]))
        EV_SICK -> Lang.t("ev_sick", bname(e[1]))
        EV_TRADER -> Lang.t("trader_arrived")
        EV_LEVEL -> Lang.t("level_up", Lang.t(game.content.levels[e[1].coerceIn(0, game.content.levels.size - 1)].name))
        EV_QUEST -> Lang.t("ev_quest")
        EV_BAD_HARVEST -> Lang.t("ev_bad_harvest")
        EV_HARSH_WINTER -> Lang.t("ev_harsh_winter")
        EV_STORE_FULL -> Lang.t("ev_store_full", bname(e[1]))
        EV_UNPAID -> Lang.t("ev_unpaid")
        EV_FESTIVAL -> Lang.t("ev_festival")
        EV_RAID -> Lang.t(if (e[2] == RAID_WOLVES) "ev_wolves_coming" else "ev_bandits_coming", bname(e[1]))
        EV_RAID_REPELLED -> Lang.t(if (e[2] == RAID_WOLVES) "ev_wolves_repelled" else "ev_bandits_repelled", bname(e[1]))
        EV_RAID_BLOCKED -> Lang.t(if (e[2] == RAID_WOLVES) "ev_wolves_blocked" else "ev_bandits_blocked")
        EV_WOLVES -> Lang.t(if (e[2] == 1) "ev_wolves_bit" else "ev_wolves_ate", bname(e[1]))
        EV_BANDITS -> Lang.t("ev_bandits_robbed", bname(e[1]), e[2] % 1000, e[2] / 1000)
        EV_CROWN -> Lang.t("ev_crown", e[2], resLower(e[1]))
        EV_CROWN_DONE -> Lang.t("ev_crown_done", resLower(e[1]), e[2])
        EV_CROWN_FAILED -> Lang.t("ev_crown_failed", resLower(e[1]))
        EV_SHIP -> Lang.t("ev_ship")
        EV_DAILY_END -> Lang.t("ev_daily_end", e[1])
        EV_FORECAST -> Lang.t("ev_forecast_${e[1].coerceIn(0, FC_COUNT - 1)}", e[2])
        EV_FLOOD -> Lang.t(if (e[2] == 1) "ev_flood" else "ev_flood_end", Tune.FLOOD_DAYS, Tune.FLOOD_BONUS)
        EV_DROUGHT -> Lang.t("ev_drought", Tune.DROUGHT_SOIL)
        EV_GROWN -> Lang.t(if (e[2] == 1) "ev_grown_skilled" else "ev_grown", bname(e[1]))
        EV_CONTRACT -> Lang.t("ev_contract", nbName(e[1]))
        EV_WAGON_OUT -> Lang.t("ev_wagon_out", nbName(e[1]))
        EV_WAGON_BACK -> Lang.t("ev_wagon_back", nbName(e[1]), if (e[2] > 0) resLower(e[2] - 1) else Lang.t("gold_word"))
        EV_CONTRACT_FAIL -> Lang.t(if (e[2] == 1) "ev_contract_cancel" else "ev_contract_fail", nbName(e[1]))
        else -> ""
    }

    private fun eventIcon(type: Int): com.badlogic.gdx.graphics.g2d.TextureRegion? = sprites.named(when (type) {
        EV_HUNGRY -> "icon_hungry"
        EV_LEFT -> "hud_people"
        EV_BUILT -> "hud_road"
        EV_UPGRADING, EV_UPGRADED -> "icon_upgrade"
        EV_FIRE -> "icon_fire"
        EV_FIRE_OUT -> "hud_fire"
        EV_SICK -> "icon_sick"
        EV_TRADER -> "hud_trader"
        EV_LEVEL, EV_FESTIVAL, EV_RAID_REPELLED, EV_RAID_BLOCKED -> "hud_star"
        EV_RAID, EV_WOLVES, EV_BANDITS -> if (sprites.named("person_3_0_0") != null) "person_3_0_0" else "icon_fire"
        EV_QUEST -> "hud_quest"
        EV_CROWN, EV_CROWN_DONE, EV_CROWN_FAILED -> "hud_crown"
        EV_SHIP -> "hud_ship"
        EV_DAILY_END -> "hud_daily"
        EV_FORECAST -> "icon_hungry"
        EV_FLOOD -> "hud_flood"
        EV_DROUGHT -> "hud_weather_4"
        EV_GROWN -> "hud_kids"
        EV_CONTRACT, EV_WAGON_OUT, EV_WAGON_BACK, EV_CONTRACT_FAIL -> "hud_neighbors"
        EV_BAD_HARVEST -> "res_grain"
        EV_HARSH_WINTER -> "hud_season_3"
        EV_STORE_FULL -> "icon_output_full"
        EV_UNPAID -> if (sprites.named("icon_unpaid") != null) "icon_unpaid" else "icon_no_workers"
        else -> "hud_bell"
    })?.r

    /** Важные новые события - всплывающим сообщением (последнее из пришедших) */
    private fun eventToasts() {
        if (world.evCount <= evToasted) return
        var shown: IntArray? = null
        for (n in maxOf(evToasted, world.evCount - Tune.EVENTS) until world.evCount) {
            val e = world.eventAt(n)
            // всплывает только срочное; нехватки и простои - на колокольчике и в окне «Дела в городе»
            if (e[0] in intArrayOf(EV_FIRE, EV_LEFT, EV_SICK, EV_BAD_HARVEST, EV_HARSH_WINTER, EV_UNPAID, EV_RAID, EV_RAID_REPELLED, EV_RAID_BLOCKED, EV_WOLVES, EV_BANDITS,
                    EV_CROWN, EV_CROWN_FAILED, EV_SHIP, EV_FORECAST, EV_FLOOD, EV_DROUGHT, EV_CONTRACT_FAIL, EV_WAGON_BACK) || (e[0] == EV_FIRE_OUT && e[2] == 1)) shown = e
            if (e[0] == EV_FIRE || e[0] == EV_RAID || e[0] == EV_CROWN_FAILED || e[0] == EV_CONTRACT_FAIL || (e[0] == EV_FLOOD && e[2] == 1)) Sfx.play("warn", 0.5f)
            if (e[0] == EV_CROWN) Sfx.play("quest")
        }
        evToasted = world.evCount
        shown?.let { showToast(eventText(it), 3500) }
    }

    /**
     * Объяснение при первой встрече с механикой (раз за партию, бит в World.tipsSeen): окно поверх карты,
     * игра на это время стоит. Обучение их не показывает - там свои подсказки заданий
     */
    private fun firstTips() {
        if (outcomeBox.isVisible || world.outcome != PLAYING || world.scenario == 0) return
        val order = (0 until TIPS).firstOrNull { t -> world.tipsSeen and (1 shl t) == 0 && tipNow(t) } ?: return
        world.tipsSeen = world.tipsSeen or (1 shl order)
        outcomeBox.clearChildren()
        val col = Table()
        col.background = ui.panel
        col.pad(16f)
        col.defaults().width(320f).padBottom(10f)
        col.add(Label(Lang.t("first_title_$order"), ui.title).also { it.setWrap(true) }).row()
        col.add(Label(Lang.t("first_text_$order"), ui.label).also { it.setWrap(true) }).row()
        val ok = TextButton(Lang.t("first_ok"), ui.buttonMain)
        ok.onClick { outcomeBox.isVisible = false }
        col.add(ok).row()
        outcomeBox.add(col)
        outcomeBox.isVisible = true
    }

    /** Есть ли сейчас то, что объясняет подсказка t (0 дождь, 1 засуха, 2 ребёнок, 3 паводок, 4 торговый пост) */
    private fun tipNow(t: Int) = when (t) {
        0 -> world.weather == W_RAIN
        1 -> world.weather == W_DROUGHT
        2 -> world.kidsTotal() > 0
        3 -> world.floodLeft > 0
        else -> world.hasTradePost()
    }

    /** Камера на здание и его панель */
    private fun focus(b: Int) {
        val d = world.def(b)
        val cx = world.bX[b] + d.size / 2f; val cy = world.bY[b] + d.size / 2f
        cam.position.set(renderer.sx(cx, cy), renderer.sy(cx, cy), 0f)
        clampCamera()
        selectBuild(-1)
        selected = b; tileCell = -1; clearAgent()
        updateInfo()
    }

    /** Следующее простаивающее здание (нет рабочих, сырья, места, далеко, горит) */
    private fun nextIdle() {
        val idle = (0 until world.buildingCount).filter {
            world.bState[it] == B_ACTIVE && (world.def(it).isProducer || world.def(it).isService) &&
                world.bStatus[it] in intArrayOf(BS_NO_WORKERS, BS_NO_RESOURCE, BS_OUTPUT_FULL, BS_NO_INPUT, BS_FAR, BS_FIRE, BS_UNPAID)
        }
        if (idle.isEmpty()) { showToast(Lang.t("no_idle")); return }
        idleCursor = idle.firstOrNull { it > idleCursor } ?: idle[0]
        events.isVisible = false
        focus(idleCursor)
    }

    /** Звуки событий мира: достроено, задание выполнено, торговец пришёл, налоги, предупреждение о провале */
    private fun eventSounds() {
        var built = 0
        for (b in 0 until world.buildingCount) if (world.bState[b] == B_ACTIVE) built++
        if (lastBuilt in 0 until built) Sfx.play("built")
        lastBuilt = built
        if (world.questsDone > lastQuests) Sfx.play("quest")
        lastQuests = world.questsDone
        if (world.traderHere && !lastTrader) { Sfx.play("trader"); showToast(Lang.t(if (world.traderShip) "ship_arrived" else "trader_arrived"), 3000) }
        lastTrader = world.traderHere
        if (world.day != lastDay && world.gold > lastGold) Sfx.play("coin", 0.5f)
        lastDay = world.day; lastGold = world.gold
        if (world.failTimer > 0 && lastFail < 0) Sfx.play("warn")
        lastFail = world.failTimer
    }

    /** Город вырос: пересобрать карточки построек и сказать, что открылось */
    private fun levelUp() {
        val opened = game.content.buildings.filter { it.buildable && it.level in shownLevel + 1..world.level }.map { Lang.t(it.name) }
        val name = Lang.t(game.content.levels[world.level].name)
        Sfx.play("level")
        buildHud()
        showToast(if (opened.isEmpty()) Lang.t("level_up", name) else Lang.t("level_up_open", name, opened.joinToString(", ")), 5000)
    }

    /**
     * Действия для съёмки экранов (desktop :shots): select b - выбрать здание, build def - режим постройки,
     * ghost x y - предпросмотр, trade, menu, close, quests - свернуть/раскрыть задания, center b - камера на здание,
     * zoom n - масштаб камеры n/100.
     */
    /** Для съёмки: что в раскладке вылезает за экран или не влезает (town.ui.Overflow) */
    fun debugOverflow(): List<String> = town.ui.Overflow.check(stage)

    fun debugAction(action: String, a: Int = 0, b: Int = 0) {
        when (action) {
            "select" -> { selectBuild(-1); selected = a; tileCell = -1; clearAgent(); updateInfo() }
            // касание по экранной точке жителя (как пальцем): камера на нём, затем обычный onTap
            "tapAgent" -> {
                val v = com.badlogic.gdx.math.Vector2()
                renderer.agentPoint(a, 1f, v)
                cam.position.set(v.x, v.y, 0f); cam.zoom = MIN_ZOOM; cam.update()
                val p3 = com.badlogic.gdx.math.Vector3(v.x + 4f, v.y + 3f, 0f)
                renderer.camera.project(p3)
                onTap(p3.x.toInt(), Gdx.graphics.height - p3.y.toInt())
            }
            "citizen" -> { selectBuild(-1); selected = -1; tileCell = -1; selSerial = world.aSerial[a]; selIdx = a; updateInfo() }
            "tile" -> { selectBuild(-1); selected = -1; tileCell = a; updateInfo() }
            "build" -> selectBuild(a)
            "ghost" -> { ghostX = a; ghostY = b; updateInfo() }
            "turn" -> rotateGhost()
            "trade" -> openTrade()
            "menu" -> openMenu()
            "settings" -> { menuPage = "settings"; fillMenu(); menu.isVisible = true }
            "help" -> openHelp()
            "close" -> { goodsWin.isVisible = false; nbWin.isVisible = false; chain.isVisible = false; layersWin.isVisible = false; buildWin.isVisible = false; menu.isVisible = false; trade.isVisible = false; stats.isVisible = false; events.isVisible = false; summary.isVisible = false; closeSheet(); tipText = null }
            "summary" -> openSummary()
            "stats" -> openStats()
            "statstab" -> { statsTab = a; openStats() }
            "events" -> openEvents()
            "quests" -> { questOpen = !questOpen; questKey = ""; updateQuests() }
            "tipcard" -> tipText = { cardTip(cards.getOrNull(a)?.def ?: ROAD_MODE) }
            "buildmenu" -> { buildTab = BUILD_TABS[a.coerceIn(0, BUILD_TABS.size - 1)]; openBuildMenu() }
            "rotate" -> rotateMap(a)
            "tap" -> if (buildDef == COPY_MODE) tplTap(a, ghostScreenX(), ghostScreenY())
            "tplgo" -> tplConfirm()
            "chain" -> openChain(a)
            "layer" -> setLayer(a, null)
            "layers" -> openLayers()
            "goods" -> { selected = a; openGoods(a) }
            "neighbors" -> openNeighbors()
            "zoom" -> { cam.zoom = a / 100f; clampCamera(); toastUntil = 0 }
            "center" -> cam.position.set(renderer.sx(world.bX[a] + 1f, world.bY[a] + 1f), renderer.sy(world.bX[a] + 1f, world.bY[a] + 1f), 0f)
        }
        refreshAt = 0
    }

    private fun bench() {
        val w = SaveCodec.read(game.content, SaveCodec.write(world))
        val t0 = TimeUtils.nanoTime()
        repeat(2000) { w.step() }
        Gdx.app.log("bench", "2000 ticks in ${(TimeUtils.nanoTime() - t0) / 1_000_000} ms, checksum ${w.checksum()}")
    }

    fun saveNow() = autosave()

    private fun autosave() {
        Storage.save("auto", SaveCodec.write(world))
        autosaveAt = TimeUtils.millis() + AUTOSAVE_MS
    }

    override fun resize(width: Int, height: Int) {
        scale = game.layoutScale(width, height)
        (stage.viewport as ScreenViewport).unitsPerPixel = 1f / scale
        val center = cam.position.cpy()
        cam.setToOrtho(false, width / scale, height / scale)
        cam.position.set(center)
        // первый кадр партии: на большом экране город не теряется посреди луга - видно около START_VIEW точек мира
        if (!camFitted) {
            camFitted = true
            cam.zoom = minOf(1f, START_VIEW / minOf(cam.viewportWidth, cam.viewportHeight)).coerceAtLeast(MIN_ZOOM)
        }
        stage.viewport.update(width, height, true)
        (photoStage.viewport as ScreenViewport).unitsPerPixel = 1f / scale
        photoStage.viewport.update(width, height, true)
        if (photo) buildPhotoStage()
        // пересборка при повороте и при смене размера/вырезов (не на каждый resize того же размера)
        val key = "${width}x$height:${game.safeInsets().joinToString(",")}"
        if (layoutKey != key) { layoutKey = key; buildHud() }
        clampCamera()
    }

    /** Android: приложение свёрнуто - сохраняемся */
    override fun pause() = autosave()

    override fun dispose() {
        stage.dispose()
        photoStage.dispose()
    }
}

/** «Авто»-графика: ниже стольких кадров/с два окна подряд - упростить */
private const val FPS_LOW = 27f
