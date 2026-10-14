package town.sim

/*
 * Погода и весенний паводок. Погода выбирается на рассвете через rng мира: сегодняшняя - вчерашний прогноз,
 * прогноз на завтра - по сезону завтрашнего дня (Tune.WEATHER_ODDS). Дождь: поля, сады и пастбища быстрее,
 * ходить по земле медленнее; снегопад - ещё медленнее; засуха (летом): поля, сады, пастбища и колодцы слабее,
 * пожары чаще; туман: караул видит хуже. Паводок: в начале весны река может выйти из берегов - здания на низких
 * клетках у воды стоят FLOOD_DAYS суток, зато земля там плодороднее до зимы.
 */

const val W_CLEAR = 0
const val W_RAIN = 1
const val W_SNOW = 2
const val W_FOG = 3
const val W_DROUGHT = 4

/** Погода на день сезона s по таблице шансов */
internal fun World.rollWeather(s: Int): Int {
    val odds = Tune.WEATHER_ODDS[s and 3]
    var k = rng.next(100)
    for (i in odds.indices) {
        if (k < odds[i]) return i
        k -= odds[i]
    }
    return W_CLEAR
}

/** Сезон завтрашнего дня */
private fun World.seasonTomorrow() = ((tick / Tune.TICKS_PER_DAY + 1) / Tune.SEASON_DAYS) % 4

/** Рассвет: погода по вчерашнему прогнозу, новый прогноз, паводок в начале весны */
internal fun World.weatherDawn() {
    val was = weather
    weather = weatherNext
    // прогноз мог быть на другой сезон (вчера была осень) - снег не идёт весной, засуха - только летом
    if (weather == W_SNOW && season != WINTER) weather = W_RAIN
    if (weather == W_RAIN && season == WINTER) weather = W_SNOW
    if (weather == W_DROUGHT && season != SUMMER) weather = W_CLEAR
    weatherNext = rollWeather(seasonTomorrow())
    if (weather == W_DROUGHT && was != W_DROUGHT) event(EV_DROUGHT, 0)
    val dayIndex = tick / Tune.TICKS_PER_DAY
    // в любой из первых FLOOD_WINDOW дней весны, раз в год (в первую весну партии тоже - со второго дня)
    if (season == SPRING && dayIndex % Tune.SEASON_DAYS < Tune.FLOOD_WINDOW && floodYear != year && floodLeft == 0 && hasLow() &&
        rng.next(100) < Tune.FLOOD_CHANCE * Tune.DISASTER_PCT[setDisasters] / 100
    ) {
        floodLeft = Tune.FLOOD_DAYS * Tune.TICKS_PER_DAY
        floodYear = year
        event(EV_FLOOD, 0, 1)
    }
}

private fun World.hasLow(): Boolean {
    for (c in 0 until w * h) if (low[c]) return true
    return false
}

/** Идёт паводок и здание b в воде */
fun World.flooded(b: Int) = floodLeft > 0 && bLow[b]

/** Низкие клетки у реки: суша не дальше FLOOD_DIST от воды (у самой воды - всегда, дальше - по шуму из сида) */
internal fun World.computeLow() {
    java.util.Arrays.fill(low, false)
    val d = waterDistance(Tune.FLOOD_DIST)
    for (c in 0 until w * h) {
        if (d[c] == 0 || d[c] > Tune.FLOOD_DIST || tiles[c] == ROCK) continue
        if (d[c] == 1 || lowHash(c) % 100 < 55) low[c] = true
    }
}

private fun World.lowHash(c: Int): Int {
    // пятна по 2x2 клетки, чтобы край поймы был неровным, но не в крапинку
    var h = (c % w / 2) * 374761393 + (c / w / 2) * 668265263 + seed * 1274126177
    h = (h xor (h ushr 13)) * 1103515245
    return (h ushr 8) and 0x7fff
}

/** Хоть одна клетка здания b - низкая */
internal fun World.lowUnder(b: Int): Boolean {
    val s = def(b).size
    for (y in bY[b] until bY[b] + s) for (x in bX[b] until bX[b] + s) if (low[y * w + x]) return true
    return false
}

/** Сколько клеток здания в пойме - для подсказки при постройке */
fun World.lowCells(defIndex: Int, x: Int, y: Int): Int {
    val s = defs[defIndex].size
    var n = 0
    for (yy in y until y + s) for (xx in x until x + s) if (xx in 0 until w && yy in 0 until h && low[yy * w + xx]) n++
    return n
}

/** Погода и паводок на выработку здания, проценты: поля, сады, пастбища (плодородие) и колодцы (подземная вода) */
fun World.weatherYield(b: Int): Int {
    val soil = def(b).soil ?: return 100
    var p = 100
    when (weather) {
        W_RAIN -> if (soil == "fertility") p += Tune.RAIN_FIELDS
        W_DROUGHT -> p -= Tune.DROUGHT_SOIL
    }
    if (soil == "fertility" && bLow[b] && floodYear == year && season != WINTER && floodLeft == 0) p += Tune.FLOOD_BONUS
    return p
}

/** Скорость ходьбы по клетке c в процентах (дождь и снег - медленнее, кроме мощёной дороги и моста) */
internal fun World.weatherWalk(c: Int): Int {
    if (weather != W_RAIN && weather != W_SNOW) return 100
    if (tiles[c] == PAVED || tiles[c] == BRIDGE) return 100
    return if (weather == W_RAIN) Tune.RAIN_WALK else Tune.SNOW_WALK
}
