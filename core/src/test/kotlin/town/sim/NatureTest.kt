package town.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import town.data.Content

/** Природа: вырубка и пни, лесник и рост саженцев, рыба восстанавливается, сохранение с ресурсами */
class NatureTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())
    private val day = Tune.TICKS_PER_DAY

    /** Ближайшее к ратуше место, где здание можно поставить (и ресурса не меньше min) */
    private fun place(wd: World, id: String, min: Int = 0): Int {
        val def = content.building(id)
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        for (d in 3 until 60) for (dx in -d..d) for (s in intArrayOf(-1, 1)) {
            val x = hx + dx; val y = hy + (d - Math.abs(dx)) * s
            if (x !in 0 until wd.w || y !in 1 until wd.h || !wd.canBuild(def.index, x, y)) continue
            if (wd.nearIfPlaced(def.index, x, y) < min) continue
            wd.submit(Command.PLACE, def.index, x, y)
            return wd.lastPlaced
        }
        error("no place for $id")
    }

    private fun count(wd: World, f: (Int) -> Boolean): Int {
        var n = 0
        for (i in 0 until wd.w * wd.h) if (f(i)) n++
        return n
    }

    @Test
    fun woodcutterFellsTreesLeavingStumps() {
        val wd = World.create(content, 11, MapSize.MEDIUM)
        val logs = content.resource("logs").index
        val forest0 = count(wd) { wd.tiles[it] == FOREST }
        val wood0 = count(wd) { wd.tiles[it] == FOREST && wd.res[it] == Tune.TREE_LOGS }
        assertEquals(forest0, wood0, "в начале все деревья зрелые")
        val b = place(wd, "woodcutter", 12)
        repeat(Tune.BASE_DAY * 4) { wd.step() }   // 4 минуты: пни ещё не сгнили
        val made = wd.produced(logs, 1) + wd.produced(logs, 2) + wd.produced(logs, 3)
        assertTrue(made > 0, "дровосек приносит брёвна: $made")
        val cut = count(wd) { wd.tiles[it] == FOREST && wd.res[it] in 1 until Tune.TREE_LOGS } +
            count(wd) { wd.tiles[it] == GRASS && wd.grow[it] > 0 }
        assertTrue(cut > 0, "деревья у дровосека срублены или начаты")
        assertTrue(wd.bNear[b] > 0)
    }

    @Test
    fun foresterPlantsSaplingsThatGrow() {
        val wd = World.create(content, 5, MapSize.MEDIUM)
        place(wd, "forester", 8)
        repeat(day * 3) { wd.step() }
        val saplings = count(wd) { wd.tiles[it] == FOREST && wd.res[it] == 0 && wd.grow[it] > 0 }
        assertTrue(saplings >= 3, "лесник сажает: $saplings")
        repeat(day * (Tune.TREE_GROW_DAYS + 1)) { wd.step() }
        val young = count(wd) { wd.tiles[it] == FOREST && wd.res[it] == 0 && wd.grow[it] > 0 }
        val grownTrees = count(wd) { wd.tiles[it] == FOREST && wd.res[it] == Tune.TREE_LOGS }
        assertTrue(grownTrees > 0, "саженцы вырастают")
        println("forester: $saplings saplings after 3 days, $young still growing later, $grownTrees mature trees")
    }

    @Test
    fun fishRegrowsAndSoilVaries() {
        val wd = World.create(content, 42, MapSize.LARGE)
        val shoal = (0 until wd.w * wd.h).first { wd.resMax[it] > 0 }
        assertTrue(wd.tiles[shoal] == WATER && wd.res[shoal] == Tune.FISH_MAX)
        wd.res[shoal] = 0
        while (wd.tick % day != day - 1) wd.step()
        wd.step()   // рассвет
        assertEquals(Tune.FISH_REGEN, wd.res[shoal])
        val f = wd.fert.filterIndexed { i, _ -> wd.tiles[i] == GRASS }
        assertTrue(f.maxOrNull()!! - f.minOrNull()!! >= 30, "плодородие разное по карте")
        val farm = content.building("farm").index
        val yields = (0 until 400).map { wd.yieldFor(farm, it % 20 * 4 + 4, it / 20 * 4 + 4) }
        assertTrue(yields.minOrNull()!! >= 70 && yields.maxOrNull()!! <= 130)
    }

    @Test
    fun borderLimitsBuildingAndGrows() {
        val wd = World.create(content, 3, MapSize.LARGE)
        val house = content.building("house").index
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        val far = hx + Tune.BORDER_HALL + 4
        assertTrue(!wd.border[hy * wd.w + far], "за границей")
        val inside = (0 until wd.w * wd.h).count { wd.border[it] }
        wd.debugGrant(wd.gold, 2)
        val grown = (0 until wd.w * wd.h).count { wd.border[it] }
        assertTrue(grown > inside, "граница растёт с уровнем: $inside -> $grown")
        // вне границы строить нельзя
        var outside = 0
        for (y in 1 until wd.h) for (x in 0 until wd.w) if (wd.canPlace(house, x, y) && !wd.insideBorder(house, x, y)) { assertTrue(!wd.canBuild(house, x, y)); outside++ }
        assertTrue(outside > 0)
    }

    @Test
    fun materialsMustBeInStock() {
        val wd = World.create(content, 4, MapSize.MEDIUM)
        val planks = content.resource("planks").index
        val house = content.building("house")
        // ставим дома, пока хватает досок: каждый обещает свои доски, и следующий уже не заложить
        var placed = 0
        while (wd.hasMaterials(house.index) && placed < 50) { place(wd, "house"); placed++ }
        assertTrue(placed in 1 until 50, "домов заложено: $placed")
        assertTrue(wd.freeStock(planks) < house.cost[planks])
        val before = wd.buildingCount
        val spot = (0 until wd.w * wd.h).first { wd.canBuild(house.index, it % wd.w, it / wd.w) }
        wd.submit(Command.PLACE, house.index, spot % wd.w, spot / wd.w)
        assertEquals(before, wd.buildingCount, "без материалов стройка не закладывается")
    }

    @Test
    fun saveLoadKeepsNature() {
        val a = World.create(content, 9, MapSize.SMALL)
        a.debugGrant(a.gold, 3)   // граница шире - скалы досягаемы (тест не про границу)
        place(a, "woodcutter", 8); place(a, "forester", 4)
        repeat(day * 2 + 400) { a.step() }   // посреди ходок
        val text = SaveCodec.write(a)
        val b = SaveCodec.read(content, text)
        assertEquals(a.checksum(), b.checksum())
        repeat(day * 2) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
        assertTrue((0 until a.w * a.h).all { a.res[it] == b.res[it] && a.grow[it] == b.grow[it] && a.fert[it] == b.fert[it] })
    }
}
