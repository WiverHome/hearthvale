package town

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import town.data.Content
import town.data.Lang
import town.screens.GameScreen
import town.screens.TitleScreen
import town.sim.World
import town.ui.Ui

/** Точка входа игры для всех платформ. systemLang - язык устройства/браузера ("ru", "en-US"...). */
class Hearthvale(private val systemLang: String?) : Game() {
    lateinit var content: Content
    lateinit var sprites: Sprites
    lateinit var ui: Ui
    lateinit var batch: SpriteBatch
    /** Физических пикселей на точку интерфейса и мира */
    var scale = 1f
        private set

    override fun create() {
        scale = if (Gdx.app.type == Application.ApplicationType.Android) maxOf(1f, Gdx.graphics.density) else screenScale()
        batch = SpriteBatch(8191)
        setScreen(LoadingScreen(this))
    }

    /**
     * Загрузка по шагам (экран загрузки рисует кадр между шагами): данные и строки, атлас, шрифты, звуки.
     * Возвращает долю готового 0..1; 1 - всё загружено.
     */
    fun loadStep(step: Int): Float {
        when (step) {
            0 -> {
                content = Content.parse(
                    Gdx.files.internal("data/content.json").readString("UTF-8"),
                    Gdx.files.internal("data/scenarios.json").readString("UTF-8"),
                )
                content.loadNames(Gdx.files.internal("data/names.json").readString("UTF-8"))
                Storage.campaignSize = content.scenarios.size
                Lang.load(Gdx.files.internal("i18n/strings.json").readString("UTF-8"))
                Lang.code = Storage.language ?: Lang.fromSystem(systemLang)
            }
            1 -> { sprites = Sprites(content, lowGraphics()); applyFrameCap() }
            2 -> ui = makeUi()
            3 -> Sfx.load()
        }
        return (step + 1) / LOAD_STEPS.toFloat()
    }

    /**
     * ПК и браузер: крупнее на больших окнах (720 точек по высоте - масштаб 1, 1080 - 1.5, 1440 - 2),
     * но не уже 440 точек по ширине (телефон в браузере остаётся с масштабом 1). Шаг 0.25 - шрифты чёткие.
     */
    private fun screenScale(): Float = scaleFor(Gdx.graphics.width, Gdx.graphics.height, 2f)

    private fun scaleFor(width: Int, height: Int, max: Float): Float {
        val s = minOf(height / 720f, width / 440f).coerceIn(1f, max)
        return Math.floor(s * 4.0).toFloat() / 4f
    }

    /**
     * Масштаб раскладки для текущего размера окна: окно сузили - интерфейс мельче (не больше стартового,
     * под который отрисованы шрифты). На Android - всегда плотность экрана.
     */
    fun layoutScale(width: Int, height: Int): Float =
        if (Gdx.app.type == Application.ApplicationType.Android) scale else minOf(scale, scaleFor(width, height, scale))

    /**
     * Ограничение 30 кадров/с в лёгкой графике. На Android setForegroundFPS не работает - лаунчер
     * подставляет свой ограничитель; на ПК - setForegroundFPS; в браузере частоту задаёт сам браузер.
     */
    var frameCap: ((Boolean) -> Unit)? = null

    /** Нужна ли лёгкая графика: по настройке, а в режиме «Авто» - по видеокарте и прошлому упрощению */
    fun lowGraphics(): Boolean {
        val maxTex = run {
            val buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
            Gdx.gl.glGetIntegerv(com.badlogic.gdx.graphics.GL20.GL_MAX_TEXTURE_SIZE, buf)
            buf.get(0)
        }
        if (maxTex in 1 until Sprites.FULL_ATLAS_SIZE) return true
        return when (Storage.graphics) {
            "high" -> false
            "low" -> true
            else -> Storage.autoLow
        }
    }

    private fun makeUi() = Ui(sprites.white, scale, { sprites.named(it)?.r }, if (Storage.bigText) Ui.BIG_TEXT else 1f)

    /** Крупный текст включили или выключили: шрифты заново, титульный экран - сразу на настройках */
    fun toggleBigText() {
        Storage.bigText = !Storage.bigText
        val old = ui
        ui = makeUi()
        switchTo(TitleScreen(this, settings = true))
        old.dispose()
    }

    /** Применить настройку графики на лету */
    fun applyGraphics() = setLowGraphics(lowGraphics())

    fun setLowGraphics(low: Boolean) {
        sprites.setLow(low)
        if (::ui.isInitialized) ui.retarget(sprites.white)
        applyFrameCap()
    }

    private fun applyFrameCap() {
        val cap = sprites.low
        frameCap?.invoke(cap) ?: Gdx.graphics.setForegroundFPS(if (cap) 30 else 60)
    }

    /**
     * Основной ввод - палец (браузер на телефоне или планшете; ставит веб-лаунчер по CSS pointer: coarse).
     * Тогда режима ПК нет даже в широком окне: телефон в ландшафте не должен ставить здание с одного касания.
     */
    var touchScreen = false

    /**
     * Веб: безопасные отступы экрана из CSS env(safe-area-inset-*) - вырез, полоса «Домой» на iPhone
     * (у веб-бэкенда libGDX их нет). Строка «верх,лево,право,низ,ширина окна» в точках CSS; null - не веб.
     */
    var webInsets: (() -> String)? = null

    /** Сохранение в файл и из файла (веб: «Файлы» на iPhone, загрузки на ПК) - перенос города между браузером и приложением */
    interface SaveFiles {
        fun export(name: String, text: String)
        /** Сохранить картинку PNG (снимок карты): rgba - w*h*4 байт, строки сверху вниз */
        fun exportImage(name: String, w: Int, h: Int, rgba: ByteArray)
        /** Выбрать файл; done(текст) - в потоке игры, null - отменили или не прочитать */
        fun import(done: (String?) -> Unit)
    }
    var saveFiles: SaveFiles? = null

    /** Работает ли буфер обмена (код партии); веб-лаунчер ставит false, если нет */
    var clipboardCodes = true

    /** Безопасные отступы в пикселях экрана: верх, лево, право, низ */
    fun safeInsets(): IntArray {
        val f = webInsets ?: return intArrayOf(Gdx.graphics.safeInsetTop, Gdx.graphics.safeInsetLeft, Gdx.graphics.safeInsetRight, Gdx.graphics.safeInsetBottom)
        val v = try { f().split(",").map { it.toFloat() } } catch (e: Exception) { return intArrayOf(0, 0, 0, 0) }
        if (v.size < 5 || v[4] <= 0f) return intArrayOf(0, 0, 0, 0)
        val k = Gdx.graphics.width / v[4]
        return IntArray(4) { Math.round(v[it] * k) }
    }

    /** Режим ПК: мышь и клавиатура (не Android, не сенсорный экран и окно шире 700 точек) - горячие клавиши, подсказки при наведении */
    val pcMode get() = Gdx.app.type != Application.ApplicationType.Android && !touchScreen &&
        Gdx.graphics.width / layoutScale(Gdx.graphics.width, Gdx.graphics.height) >= 700f

    companion object {
        const val LOAD_STEPS = 4
        /** Версия для титульного экрана; при выпуске поднимать вместе с versionName в android/build.gradle.kts */
        const val VERSION = "1.0.0"
    }

    fun showTitle() = switchTo(TitleScreen(this))

    /** Сохранить партию прямо сейчас (вкладку браузера прячут или закрывают) */
    fun saveNow() {
        (screen as? GameScreen)?.saveNow()
    }

    fun play(world: World) = switchTo(GameScreen(this, world))

    fun toggleLanguage() {
        Lang.code = if (Lang.code == "ru") "en" else "ru"
        Storage.language = Lang.code
    }

    private fun switchTo(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun dispose() {
        screen?.dispose()
        batch.dispose()
        if (::sprites.isInitialized) sprites.dispose()
        if (::ui.isInitialized) ui.dispose()
        Sfx.dispose()
    }
}
