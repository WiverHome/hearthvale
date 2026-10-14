package town.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import town.data.Content

/** Прогноз запаса: суток хватит, склад по суткам, сохранение флагов */
class ForecastTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())
    private val day = Tune.TICKS_PER_DAY

    @Test
    fun noHistoryMeansNoForecast() {
        val w = World.create(content, 3, MapSize.SMALL)
        assertEquals(-1, w.daysLeftGoods(w.forecastGoods(FC_FOOD)))
        assertEquals(0, w.forecastLevel(FC_FOOD))
        assertEquals(-1, w.forecastDays(FC_HEAT))
    }

    @Test
    fun goldDaysMatchNetUse() {
        val w = World.create(content, 3, MapSize.MEDIUM)
        repeat(day * 4) { w.step() }
        val net = w.upkeepPerDay() - w.taxPreview(w.taxLevel)
        val d = w.forecastDays(FC_GOLD)
        if (net > 0) assertEquals(w.gold / net, d) else assertEquals(-1, d)
        assertTrue(w.dayCount >= 3)
    }

    @Test
    fun groupsAreFoundByResource() {
        val w = World.create(content, 3, MapSize.SMALL)
        val goods = w.forecastGoods(FC_FOOD)
        assertTrue(goods.isNotEmpty())
        assertEquals(FC_FOOD, w.forecastKindOf(goods[0]))
        assertEquals(FC_HEAT, w.forecastKindOf(content.resource("logs").index))
    }

    @Test
    fun forecastStateSurvivesSave() {
        val w = World.create(content, 3, MapSize.MEDIUM)
        repeat(day * 5) { w.step() }
        val text = SaveCodec.write(w)
        val b = SaveCodec.read(content, text)
        assertEquals(text, SaveCodec.write(b))
        assertEquals(w.fcState, b.fcState)
        for (i in w.dayStock.indices) assertEquals(w.dayStock[i], b.dayStock[i])
    }
}
