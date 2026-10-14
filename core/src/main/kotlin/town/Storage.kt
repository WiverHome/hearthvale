package town

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Хранилище: в браузере localStorage, на Android SharedPreferences (через Preferences libGDX).
 * Слоты: "auto" (автосохранение) и "1".."3" (ручные).
 */
object Storage {
    val SLOTS = listOf("auto", "1", "2", "3")
    private val prefs: Preferences by lazy { Gdx.app.getPreferences("hearthvale") }

    fun save(slot: String, data: String) {
        prefs.putString("save.$slot", data)
        prefs.flush()
    }

    fun load(slot: String): String? = prefs.getString("save.$slot", "").ifEmpty { null }

    fun has(slot: String) = load(slot) != null

    /** Громкость звуков 0..100 (шаг 10); старое выключение "sound"=false - 0 */
    var soundVol: Int
        get() = prefs.getInteger("soundVol", if (prefs.getBoolean("sound", true)) 100 else 0)
        set(v) { prefs.putInteger("soundVol", v.coerceIn(0, 100)); prefs.flush() }

    /** Громкость музыки 0..100 (шаг 10); старое выключение "music"=false - 0 */
    var musicVol: Int
        get() = prefs.getInteger("musicVol", if (prefs.getBoolean("music", true)) 100 else 0)
        set(v) { prefs.putInteger("musicVol", v.coerceIn(0, 100)); prefs.flush() }

    /** Палитра для дальтоников: сине-оранжевая вместо красно-зелёной */
    var colorblind: Boolean
        get() = prefs.getBoolean("colorblind", false)
        set(v) { prefs.putBoolean("colorblind", v); prefs.flush() }

    /** Крупный текст в панелях и окнах (Ui.BIG_TEXT) */
    var bigText: Boolean
        get() = prefs.getBoolean("bigText", false)
        set(v) { prefs.putBoolean("bigText", v); prefs.flush() }

    /** Графика: "auto" (по видеокарте и частоте кадров), "high" (красиво), "low" (быстро, для слабых телефонов) */
    var graphics: String
        get() = prefs.getString("graphics", "auto")
        set(v) { prefs.putString("graphics", v); prefs.flush() }

    /** В режиме "auto" графику уже упростили из-за низкой частоты кадров - со следующего запуска сразу лёгкая */
    var autoLow: Boolean
        get() = prefs.getBoolean("graphics.autoLow", false)
        set(v) { prefs.putBoolean("graphics.autoLow", v); prefs.flush() }

    /** Недавние постройки (номера типов через запятую, -2 - дорога) - быстрая полоса внизу */
    var recentBuilds: String
        get() = prefs.getString("recentBuilds", "")
        set(v) { prefs.putString("recentBuilds", v); prefs.flush() }

    /** Справку «Как играть» уже показывали (сама открывается один раз, в первой песочнице) */
    var helpSeen: Boolean
        get() = prefs.getBoolean("helpSeen", false)
        set(v) { prefs.putBoolean("helpSeen", v); prefs.flush() }

    /** Открытые достижения (id через запятую) - между партиями */
    var achievements: Set<String>
        get() = prefs.getString("achievements", "").split(",").filter { it.isNotEmpty() }.toSet()
        set(v) { prefs.putString("achievements", v.joinToString(",")); prefs.flush() }

    /** Угрозы в новой песочнице (последний выбор) */
    var threats: Boolean
        get() = prefs.getBoolean("threats", true)
        set(v) { prefs.putBoolean("threats", v); prefs.flush() }

    /** Звёзды кампании за сценарий (0 - не пройден); записываются лучшие */
    fun stars(id: String): Int = prefs.getInteger("stars.$id", 0)

    /** Записать звёзды сценария; true - стало лучше */
    fun recordStars(id: String, n: Int): Boolean {
        if (n <= stars(id)) return false
        prefs.putInteger("stars.$id", n)
        val ids = prefs.getString("stars.ids", "").split(",").filter { it.isNotEmpty() }.toMutableSet()
        ids.add(id)
        prefs.putString("stars.ids", ids.joinToString(","))
        prefs.flush()
        return true
    }

    /** Все сценарии кампании пройдены на три звезды (id сценариев записываются при победе) */
    fun allStars(): Boolean = prefs.getString("stars.ids", "").split(",").filter { it.isNotEmpty() }.let { ids ->
        ids.isNotEmpty() && ids.size >= campaignSize && ids.all { stars(it) >= 3 }
    }

    /** Сколько сценариев в кампании - задаёт игра при запуске (для «все на три звезды») */
    var campaignSize = 0

    /** Настройки новой песочницы (последний выбор): богатство ресурсов, стартовый запас, бедствия 0..2, спокойный режим */
    var sbRich: Int
        get() = prefs.getInteger("sb.rich", 1)
        set(v) { prefs.putInteger("sb.rich", v); prefs.flush() }
    var sbStock: Int
        get() = prefs.getInteger("sb.stock", 1)
        set(v) { prefs.putInteger("sb.stock", v); prefs.flush() }
    var sbDisasters: Int
        get() = prefs.getInteger("sb.disasters", 1)
        set(v) { prefs.putInteger("sb.disasters", v); prefs.flush() }
    var sbCalm: Boolean
        get() = prefs.getBoolean("sb.calm", false)
        set(v) { prefs.putBoolean("sb.calm", v); prefs.flush() }

    var language: String?
        get() = prefs.getString("lang", "").ifEmpty { null }
        set(v) { prefs.putString("lang", v ?: ""); prefs.flush() }

    /** Лучшие очки партии дня за дату ГГГГММДД; -1 - не играл */
    fun dailyBest(date: Int): Int = prefs.getInteger("daily.$date", -1)

    /** Записать итог партии дня; true - новый рекорд этой даты */
    fun recordDaily(date: Int, score: Int): Boolean {
        val old = dailyBest(date)
        if (score <= old) return false
        prefs.putInteger("daily.$date", score)
        val dates = (prefs.getString("daily.dates", "").split(",").filter { it.isNotEmpty() }.map { it.toInt() } + date)
            .distinct().sortedDescending().take(7)
        prefs.putString("daily.dates", dates.joinToString(","))
        prefs.flush()
        return true
    }

    /** Последние 7 дат с партией дня: (дата, лучшие очки), новые сверху */
    fun dailyHistory(): List<Pair<Int, Int>> =
        prefs.getString("daily.dates", "").split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            .sortedDescending().map { it to dailyBest(it) }
}
