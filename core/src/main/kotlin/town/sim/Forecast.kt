package town.sim

/*
 * Прогноз: на сколько суток хватит еды, воды, дров и золота. По статистике последних суток (приход и расход
 * на складах); дрова зимой и осенью - по ожидаемому зимнему расходу домов. Только для показа и событий:
 * на ход симуляции не влияет (кроме флагов fcState, чтобы не повторять предупреждение).
 */

const val FC_FOOD = 0
const val FC_WATER = 1
const val FC_HEAT = 2
const val FC_GOLD = 3
const val FC_COUNT = 4

private val FC_NEED = arrayOf("food", "water", "heat")

/** Товары группы (еда, вода, дрова); для золота - пусто */
fun World.forecastGoods(kind: Int): IntArray =
    if (kind in FC_NEED.indices) content.needs.firstOrNull { it.id == FC_NEED[kind] }?.goods ?: IntArray(0) else IntArray(0)

/** Группа прогноза ресурса (FC_FOOD, FC_WATER, FC_HEAT) или -1 */
fun World.forecastKindOf(r: Int): Int {
    for (k in 0 until FC_GOLD) if (forecastGoods(k).contains(r)) return k
    return -1
}

/** Сколько суток хватит запаса на складах, если расход по статистике больше прихода (-1 - не убывает или нет данных) */
fun World.daysLeftGoods(goods: IntArray): Int {
    if (histDays == 0 || goods.isEmpty()) return -1
    var stock = 0L; var net = 0L
    for (r in goods) {
        stock += totalStock(r)
        for (d in 1..histDays) net += used(r, d) - produced(r, d)
    }
    if (net <= 0) return -1
    return (stock * histDays / net).toInt()
}

/** Суток запаса ресурса r: для еды, воды и дров - по всей группе, для остальных - по нему самому */
fun World.daysLeft(r: Int): Int {
    val k = forecastKindOf(r)
    return if (k >= 0) forecastDays(k) else daysLeftGoods(intArrayOf(r))
}

/** Суток до зимы (в зиму и после - 0) */
private fun World.daysToWinter(): Int =
    if (season == WINTER) 0 else (WINTER - season) * Tune.SEASON_DAYS - (tick / Tune.TICKS_PER_DAY) % Tune.SEASON_DAYS

/** Дров на сутки зимой по нынешним домам (в тысячных), за вычетом дровяной добычи по статистике; <= 0 - хватает */
private fun World.heatNetMilli(goods: IntArray): Long {
    val need = content.needs.firstOrNull { it.id == "heat" } ?: return 0
    var use = 0L
    val m = need.season[WINTER] * (if (harshWinter) 2 else 1)
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE || !def(b).isHouse || need.level > level || need.tier > def(b).tier) continue
        use += bResidents[b].toLong() * need.use * m
    }
    use = use * Tune.TICKS_PER_DAY * 1000 / (10000L * Tune.BASE_DAY)
    var made = 0L
    // уголь греет дольше: штука считается за content.resources[r].lasts
    if (histDays > 0) for (r in goods) for (d in 1..histDays) made += produced(r, d).toLong() * content.resources[r].lasts
    if (histDays > 0) made = made * 1000 / histDays
    return use - made
}

/**
 * Суток запаса группы: еда, вода - по статистике; дрова - только осенью и зимой по зимнему расходу;
 * золото - если содержание больше налога. -1 - не убывает (или не актуально)
 */
fun World.forecastDays(kind: Int): Int = when (kind) {
    FC_FOOD, FC_WATER -> daysLeftGoods(forecastGoods(kind))
    FC_HEAT -> {
        val goods = forecastGoods(kind)
        if (goods.isEmpty() || (season != AUTUMN && season != WINTER)) -1
        else {
            val net = heatNetMilli(goods)
            var stock = 0L
            for (r in goods) stock += totalStock(r).toLong() * content.resources[r].lasts
            if (net <= 0) -1 else (stock * 1000 / net).toInt()
        }
    }
    FC_GOLD -> {
        val net = upkeepPerDay() - taxPreview(taxLevel)
        if (net <= 0) -1 else gold.coerceAtLeast(0) / net
    }
    else -> -1
}

/** Хватит меньше чем на limit суток (дрова осенью - меньше чем до зимы плюс limit) */
fun World.forecastShort(kind: Int, limit: Int): Boolean {
    val d = forecastDays(kind)
    if (d < 0) return false
    return d < limit + if (kind == FC_HEAT && season == AUTUMN) daysToWinter() else 0
}

/** 0 - всё в порядке, 1 - хватит меньше чем на FORECAST_YELLOW суток, 2 - меньше чем на FORECAST_RED */
fun World.forecastLevel(kind: Int): Int = when {
    forecastShort(kind, Tune.FORECAST_RED) -> 2
    forecastShort(kind, Tune.FORECAST_YELLOW) -> 1
    else -> 0
}

/** Рассвет: предупреждение один раз, пока запас не восстановится */
internal fun World.forecastDawn() {
    for (k in 0 until FC_COUNT) {
        val bit = 1 shl k
        val lvl = forecastLevel(k)
        if (lvl == 2 && fcState and bit == 0) {
            fcState = fcState or bit
            event(EV_FORECAST, k, forecastDays(k))
        } else if (lvl == 0) fcState = fcState and bit.inv()
    }
}
