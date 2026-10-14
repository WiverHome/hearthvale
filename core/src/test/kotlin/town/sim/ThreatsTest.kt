package town.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import town.data.Content

/** Угрозы и стены, лимит города на товар, найм, перенос сохранений на новые ресурсы */
class ThreatsTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())

    /** Клетка леса за границей поселения, ближайшая к ратуше (волки выходят оттуда) */
    private fun forestOutside(wd: World): Int {
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        return (0 until wd.w * wd.h).filter { wd.tiles[it] == FOREST && !wd.border[it] && wd.raidPath(it, wd.entrance(wd.hall)) != null }
            .minByOrNull { Math.abs(it % wd.w - hx) + Math.abs(it / wd.w - hy) } ?: error("no forest outside")
    }

    private fun house(wd: World) = (0 until wd.buildingCount).first { wd.bState[it] == B_ACTIVE && wd.def(it).isHouse }

    @Test
    fun wolvesReachHouseAndBite() {
        val wd = World.create(content, 5, MapSize.SMALL)
        val h = house(wd)
        wd.bResidents[h] = maxOf(1, wd.bResidents[h])
        assertTrue(wd.startRaid(0, RAID_WOLVES, forestOutside(wd), h))
        var steps = 0
        while (wd.rType[0] != 0 && steps < Tune.TICKS_PER_DAY) { wd.step(); steps++ }
        assertEquals(0, wd.rType[0], "волки должны дойти за сутки")
        assertTrue(wd.bSick[h] > 0, "дом, куда дошли волки, болеет")
    }

    @Test
    fun guardTowerRepels() {
        val wd = World.create(content, 5, MapSize.SMALL)
        val h = house(wd)
        val guard = content.building("guardtower").index
        // вышку - прямо у дома; страже нужен работник - ждём, пока придёт
        val spot = (0 until wd.w * wd.h).first { wd.canBuild(guard, it % wd.w, it / wd.w) &&
            Math.abs(it % wd.w - wd.bX[h]) + Math.abs(it / wd.w - wd.bY[h]) <= 4 }
        wd.debugGrant(500, 1)
        for (r in 0 until wd.R) wd.inv[wd.hall * wd.R + r] += 50
        wd.submit(Command.PLACE, guard, spot % wd.w, spot / wd.w)
        val g = wd.lastPlaced
        var t = 0
        while ((wd.bState[g] != B_ACTIVE || wd.bWorkers[g] == 0) && t < Tune.TICKS_PER_DAY * 3) { wd.step(); t++ }
        assertTrue(wd.bWorkers[g] > 0, "на вышке должен быть стражник")
        val before = wd.raidsRepelled
        assertTrue(wd.startRaid(0, RAID_WOLVES, forestOutside(wd), h))
        var steps = 0
        while (wd.rType[0] != 0 && steps < Tune.TICKS_PER_DAY) { wd.step(); steps++ }
        assertEquals(before + 1, wd.raidsRepelled, "стража должна прогнать волков")
    }

    @Test
    fun wallsBlockRaid() {
        val wd = World.create(content, 5, MapSize.SMALL)
        wd.debugGrant(500, 3)
        for (r in 0 until wd.R) wd.inv[wd.hall * wd.R + r] += 500
        // кольцо частокола вокруг ратуши (на свободных клетках луга)
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        val rad = 6
        for (y in hy - rad..hy + rad + 2) for (x in hx - rad..hx + rad + 2) {
            if (maxOf(Math.abs(x - hx - 1), Math.abs(y - hy - 1)) != rad + 1) continue
            wd.submit(Command.WALL, x, y, WALL_WOOD)
        }
        val start = forestOutside(wd)
        val path = wd.raidPath(start, wd.entrance(wd.hall))
        // кольцо может прерываться скалой или водой - тогда путь есть, но он идёт не через стену
        if (path != null) for (c in path) assertTrue(wd.tiles[c] != WALL_W, "путь угрозы не проходит сквозь стену")
        else {
            val before = wd.raidsStopped
            wd.startRaid(0, RAID_BANDITS, start, wd.hall)
            assertEquals(before + 1, wd.raidsStopped)
        }
    }

    @Test
    fun townLimitStopsProduction() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val logs = content.resource("logs").index
        wd.submit(Command.STOCK_LIMIT, logs, 0, 0)
        assertEquals(0, wd.townLimit[logs])
        val wcDef = content.building("woodcutter").index
        val spot = (0 until wd.w * wd.h).first { wd.canBuild(wcDef, it % wd.w, it / wd.w) }
        wd.submit(Command.PLACE, wcDef, spot % wd.w, spot / wd.w)
        val wc = wd.lastPlaced
        repeat(Tune.BASE_DAY * 3) { wd.step() }
        if (wd.bState[wc] == B_ACTIVE) assertEquals(BS_LIMIT, wd.bStatus[wc], "лимит города 0 - дровосек стоит")
    }

    @Test
    fun hireSettlers() {
        val wd = World.create(content, 3, MapSize.SMALL)
        wd.debugGrant(1000, 1)
        val free = wd.freeBeds()
        val n0 = wd.agentCount
        wd.submit(Command.HIRE, 3, 0, 0)
        assertEquals(n0 + minOf(3, free), wd.agentCount)
    }

    /** Город на данных 0.7.0 (13 ресурсов, 8 потребностей) загружается на нынешних и идёт дальше */
    @Test
    fun oldSaveLoadsWithNewResources() {
        val old = Content.parse(File("src/test/resources/content_0.7.0.json").readText(), null)
        val a = World.create(old, 42, MapSize.SMALL)
        repeat(Tune.TICKS_PER_DAY * 2) { a.step() }
        val text = SaveCodec.write(a).replace(Regex(",\"R\":\\d+"), "").replace(Regex(",\"N\":\\d+"), "")
        val b = SaveCodec.read(content, text)
        assertEquals(a.agentCount, b.agentCount)
        for (r in 0 until old.resources.size) assertEquals(a.totalStock(r), b.totalStock(r), old.resources[r].id)
        for (r in old.resources.size until content.resources.size) assertEquals(-1, b.sellAbove[r])
        repeat(Tune.TICKS_PER_DAY) { b.step() }
    }
}
