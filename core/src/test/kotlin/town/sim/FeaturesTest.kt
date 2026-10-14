package town.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import town.data.Content

/** Налог, приоритеты, тачки, рынок, мосты и мощение, корона, партия дня, корабль, сохранение новых полей */
class FeaturesTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())
    private val day = Tune.TICKS_PER_DAY

    private fun res(id: String) = content.resource(id).index

    @Test
    fun highTaxGivesMoreGoldAndLessHappiness() {
        val base = World.create(content, 3, MapSize.MEDIUM)
        repeat(day * 2 + 10) { base.step() }
        val text = SaveCodec.write(base)
        val low = SaveCodec.read(content, text)
        val high = SaveCodec.read(content, text)
        // в большом городе часть потребностей не выполнена (рынок, церковь, украшения) - налог уже заметен
        low.debugGrant(low.gold, 4); high.debugGrant(high.gold, 4)
        low.submit(Command.TAX, 0, 0, 0)
        high.submit(Command.TAX, 2, 0, 0)
        assertEquals(0, low.taxLevel)
        assertEquals(2, high.taxLevel)
        assertTrue(high.taxPreview(2) > high.taxPreview(0), "прогноз налога")
        // довольство: пока горожане сыты, высокий налог его снижает
        repeat(800) { low.step(); high.step() }
        assertTrue(low.townHappy > high.townHappy, "довольство: низкий налог ${low.townHappy}, высокий ${high.townHappy}")
        // золото за рассвет
        repeat(day - low.tick % day + 1) { low.step(); high.step() }
        assertTrue(high.lastTax > low.lastTax, "налог: высокий ${high.lastTax}, низкий ${low.lastTax}")
    }

    @Test
    fun urgentSiteIsBuiltFirst() {
        val wd = World.create(content, 3, MapSize.SMALL)
        val house = content.building("house").index
        val s1 = placeNearNoStep(wd, "house")
        val s2 = placeNearNoStep(wd, "house")
        assertNotEquals(s1, s2)
        assertEquals(house, wd.bDef[s2])
        wd.submit(Command.PRIORITY, s2, 1, 0)
        assertEquals(1, wd.bPrio[s2])
        // материалов хватает на один дом: достроится тот, что срочный
        for (r in 0 until wd.R) wd.inv[wd.hall * wd.R + r] = content.building("house").cost[r]
        var n = 0
        while (n++ < day * 3 && wd.bState[s1] == B_SITE && wd.bState[s2] == B_SITE) wd.step()
        assertEquals(B_ACTIVE, wd.bState[s2], "срочная достроена")
        assertEquals(B_SITE, wd.bState[s1], "обычная ещё строится")
        // готовым мастерским приоритет тоже можно менять; пустым местам и домам - нет
        wd.submit(Command.PRIORITY, wd.hall, 1, 0)
        assertEquals(0, wd.bPrio[wd.hall])
    }

    @Test
    fun wagonsCarryMoreAndWearOut() {
        val wd = town(42)
        val wagon = res("wagon"); val barrow = res("barrow")
        wd.debugGrant(2000, 2)
        for (r in listOf("planks", "stone", "logs")) wd.inv[wd.hall * wd.R + res(r)] += 150
        wd.inv[wd.hall * wd.R + wagon] = 10
        for (id in listOf("house", "house", "sawmill", "market")) placeNearNoStep(wd, id)
        var most = 0
        var wagonsSeen = 0
        repeat(day) {
            wd.step()
            for (a in 0 until wd.agentCount) if (wd.taskDst[a] >= 0) {
                most = maxOf(most, wd.taskN[a])
                if (wd.aCart[a] == 2) wagonsSeen++
            }
            assertTrue(wd.cartsInUse(2) <= 10)
        }
        assertTrue(most > Tune.CARRY, "носильщик с телегой должен нести больше $most")
        assertTrue(most <= Tune.CARRY_WAGON)
        assertTrue(wagonsSeen > 0)
        checkInvariants(wd)

        // износ - в работе: ломается примерно одна из CART_TRIPS ходок с тачкой или телегой, лежащие на складе целы
        val w2 = town(42)
        w2.debugGrant(2000, 2)
        for (r in listOf("planks", "stone", "logs")) w2.inv[w2.hall * w2.R + res(r)] += 150
        for (id in listOf("house", "house", "sawmill", "market")) placeNearNoStep(w2, id)
        w2.inv[w2.hall * w2.R + wagon] = 50
        var trips = 0
        val had = IntArray(w2.agentCount + 200)
        repeat(day * 3) {
            w2.step()
            for (a in 0 until w2.agentCount) {
                if (had[a] > 0 && w2.aCart[a] == 0) trips++
                had[a] = w2.aCart[a]
            }
        }
        val broken = 50 - w2.totalStock(wagon)
        assertTrue(trips > 0, "телеги в деле")
        assertTrue(broken >= 0 && broken <= trips / 20 + 3, "сломано $broken за $trips ходок")
        val w3 = town(42)
        w3.inv[w3.hall * w3.R + barrow] = 50
        // грузы маленькие - тачки не нужны и не ломаются
        repeat(day) { w3.step() }
        assertEquals(50, w3.totalStock(barrow) + 0 * w3.cartsInUse(1) + (0 until w3.agentCount).count { w3.aCart[it] == 1 } * 0)
    }

    @Test
    fun marketWorkerDeliversFoodToHouses() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.debugGrant(500, 1)
        val bread = res("bread")
        val def = content.building("market")
        val spot = nearestSpot(wd, def.index)
        val m = wd.addBuilding(def.index, spot % wd.w, spot / wd.w, B_ACTIVE)
        assertEquals(listOf(res("bread"), res("fish"), res("meat"), res("apples"), res("smoked"), res("honey")).sorted(), def.distributes.toList())
        var delivered = false
        var n = 0
        while (n++ < day && !delivered) {
            wd.step()
            if (n == 60) {
                // дома пусты, на рынке есть хлеб
                for (h in 0 until wd.buildingCount) if (wd.def(h).isHouse) wd.inv[h * wd.R + bread] = 0
                wd.inv[m * wd.R + bread] = Tune.MARKET_FILL
                // носильщики заняты, чтобы дело досталось работнику рынка
                for (a in 0 until wd.agentCount) wd.timer[a] = if (wd.job[a] == JOB_WORKER && wd.jobB[a] == m) 0 else 1000
            }
            for (a in 0 until wd.agentCount) {
                if (wd.job[a] == JOB_WORKER && wd.jobB[a] == m && wd.taskDst[a] >= 0 && wd.taskSrc[a] == m && wd.def(wd.taskDst[a]).isHouse) delivered = true
            }
            if (n % 100 == 0) checkInvariants(wd)
        }
        assertTrue(delivered, "работник рынка должен отнести еду в дом")
    }

    @Test
    fun bridgesAndPavingCostMaterials() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val stone = res("stone"); val planks = res("planks")
        // мост только с BRIDGE_LEVEL, мощение - с PAVE_LEVEL
        wd.debugGrant(100, 1)
        val cell = (0 until wd.w * wd.h).first { wd.canBridge(it % wd.w, it / wd.w) }
        val x = cell % wd.w; val y = cell / wd.w
        val st0 = wd.totalStock(stone); val pl0 = wd.totalStock(planks)
        wd.submit(Command.ROAD, x, y, 3)
        assertEquals(BRIDGE, wd.tiles[cell])
        assertEquals(st0 - Tune.BRIDGE_STONE, wd.totalStock(stone))
        assertEquals(pl0 - Tune.BRIDGE_PLANKS, wd.totalStock(planks))
        assertTrue(wd.cost[cell] > 0)
        val shore = listOf(cell - 1, cell + 1, cell - wd.w, cell + wd.w).first { wd.tiles[it] != WATER && wd.tiles[it] != BRIDGE && wd.cost[it] > 0 }
        assertTrue(wd.findPath(shore, cell) != null, "через мост можно пройти")
        wd.submit(Command.ROAD, x, y, 0)
        assertEquals(WATER, wd.tiles[cell])

        // мощёная
        val p = (0 until wd.w * wd.h).first { wd.tiles[it] == GRASS && wd.border[it] }
        assertTrue(!wd.canPave(p % wd.w, p / wd.w), "в деревне ещё нельзя мостить")
        wd.submit(Command.ROAD, p % wd.w, p / wd.w, 2)
        assertEquals(GRASS, wd.tiles[p])
        wd.debugGrant(100, Tune.PAVE_LEVEL)
        val s1 = wd.totalStock(stone)
        wd.submit(Command.ROAD, p % wd.w, p / wd.w, 2)
        assertEquals(PAVED, wd.tiles[p])
        assertEquals(s1 - Tune.PAVE_STONE, wd.totalStock(stone))
        assertEquals(Tune.COST_PAVED, wd.cost[p])
        wd.submit(Command.ROAD, p % wd.w, p / wd.w, 0)
        assertTrue(wd.tiles[p] == GRASS || wd.tiles[p] == ROAD)
    }

    @Test
    fun crownOrderPaysOrCostsPrestige() {
        val wd = town(42)
        wd.debugGrant(100, Tune.CROWN_LEVEL)
        repeat(day - wd.tick % day + 1) { wd.step() }
        assertTrue(wd.crR >= 0, "заказ короны появился")
        assertTrue(wd.crN >= 20 && wd.crN % 10 == 0)
        assertTrue(wd.crEnd > wd.day)
        assertTrue(!wd.canDeliverCrown() || wd.stockAvailable(wd.crR) >= wd.crN)
        val r = wd.crR; val n = wd.crN
        wd.inv[wd.hall * wd.R + r] += n + 10
        assertTrue(wd.canDeliverCrown())
        val gold = wd.gold; val fame = wd.prestige; val pay = wd.crGold
        wd.submit(Command.CROWN, 0, 0, 0)
        assertEquals(gold + pay, wd.gold)
        assertEquals(fame + Tune.CROWN_PRESTIGE, wd.prestige)
        assertEquals(1, wd.crDone)
        assertEquals(-1, wd.crR)
        assertTrue(wd.crNext >= wd.day + Tune.CROWN_GAP)

        // просрочка
        val late = town(42)
        late.debugGrant(100, Tune.CROWN_LEVEL)
        repeat(day - late.tick % day + 1) { late.step() }
        assertTrue(late.crR >= 0)
        late.prestige = 100
        late.crEnd = late.day - 1
        repeat(day) { late.step() }
        assertEquals(1, late.crFailed)
        assertTrue(late.prestige <= 100 - Tune.CROWN_FAIL + 100, "слава упала на штраф и выросла от зданий")
        assertEquals(-1, late.crR)
    }

    @Test
    fun prestigeUnlocksBuildings() {
        val wd = World.create(content, 3, MapSize.SMALL)
        val guild = content.building("guildhall").index
        wd.debugGrant(0, 5)
        assertEquals("lock_prestige", wd.lockReason(guild))
        assertTrue(!wd.unlocked(guild))
        wd.prestige = 500
        assertEquals(null, wd.lockReason(guild))
        wd.debugGrant(0, 0)
        assertEquals("lock_level", wd.lockReason(guild))
    }

    @Test
    fun dailyGameIsSharedAndEnds() {
        val date = 20261012
        val a = World.createDaily(content, date)
        val b = World.createDaily(content, date)
        assertEquals(date, a.dailyDate)
        repeat(day * (Tune.DAILY_DAYS + 1)) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
        assertEquals(WON, a.outcome)
        assertTrue(a.dailyFinalScore >= 0)
        assertEquals(a.dailyFinalScore, b.dailyFinalScore)
        val c = World.createDaily(content, date + 1)
        assertTrue(!a.tiles.contentEquals(c.tiles) || a.seed != c.seed)
        assertEquals(-1, c.dailyFinalScore)
    }

    @Test
    fun harborBringsShip() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.debugGrant(5000, 3)
        val def = content.building("harbor")
        val spot = nearestSpot(wd, def.index)
        val h = wd.addBuilding(def.index, spot % wd.w, spot / wd.w, B_ACTIVE)
        repeat(day - wd.tick % day + 1) { wd.step() }   // рассвет второго дня
        assertTrue(wd.bWorkers[h] > 0, "на пристани работают")
        assertTrue(wd.traderShip && wd.traderHere, "пришёл корабль")
        val wanted = (0 until wd.R).count { wd.shipWanted(it) }
        assertEquals(Tune.SHIP_WANT, wanted)
        val r = (0 until wd.R).maxByOrNull { content.resources[it].price }!!
        assertTrue(wd.sellPrice(r) > wd.buyPrice(r) * Tune.SELL_SHARE / 100, "корабль платит больше")
        // уходит до ночи
        repeat(Tune.NIGHT_START) { wd.step() }
        assertTrue(!wd.traderShip && !wd.traderHere)
    }

    @Test
    fun newFieldsSurviveSave() {
        val a = town(42)
        a.debugGrant(500, Tune.CROWN_LEVEL)
        val wagon = res("wagon")
        a.inv[a.hall * a.R + wagon] = 5
        a.submit(Command.TAX, 2, 0, 0)
        val site = placeNearNoStep(a, "house")
        a.submit(Command.PRIORITY, site, 1, 0)
        repeat(day + 300) { a.step() }
        a.prestige += 17
        val text = SaveCodec.write(a)
        val b = SaveCodec.read(content, text)
        assertEquals(a.checksum(), b.checksum())
        assertEquals(text, SaveCodec.write(b))
        assertEquals(a.taxLevel, b.taxLevel)
        assertEquals(a.prestige, b.prestige)
        assertEquals(a.crR, b.crR)
        assertEquals(a.crEnd, b.crEnd)
        for (k in 0 until a.buildingCount) assertEquals(a.bPrio[k], b.bPrio[k])
        for (k in 0 until a.agentCount) assertEquals(a.aCart[k], b.aCart[k])
        repeat(day * 2) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
        assertEquals(SaveCodec.write(a), SaveCodec.write(b))
    }

    @Test
    fun harborShipSurvivesSave() {
        val a = World.create(content, 5, MapSize.MEDIUM)
        a.debugGrant(5000, 3)
        val def = content.building("harbor")
        val spot = nearestSpot(a, def.index)
        a.addBuilding(def.index, spot % a.w, spot / a.w, B_ACTIVE)
        repeat(day + 600) { a.step() }
        assertTrue(a.traderShip)
        val b = SaveCodec.read(content, SaveCodec.write(a))
        assertTrue(b.traderShip)
        for (r in 0 until a.R) assertEquals(a.shipWanted(r), b.shipWanted(r))
        repeat(day * 4) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
    }

    // ---- помощники (как в WorldTest) ----

    private fun checkInvariants(wd: World) {
        val res = IntArray(wd.buildingCount); val workers = IntArray(wd.buildingCount)
        for (a in 0 until wd.agentCount) {
            res[wd.home[a]]++
            if (wd.job[a] == JOB_WORKER) {
                assertEquals(B_ACTIVE, wd.bState[wd.jobB[a]], "работа жителя $a")
                workers[wd.jobB[a]]++
            }
            if (wd.taskDst[a] >= 0) assertTrue(wd.bState[wd.taskDst[a]] != B_GONE, "заявка жителя $a")
        }
        val inc = IntArray(wd.buildingCount * wd.R); val res2 = IntArray(wd.buildingCount * wd.R)
        for (a in 0 until wd.agentCount) if (wd.taskDst[a] >= 0) {
            inc[wd.taskDst[a] * wd.R + wd.taskR[a]] += wd.taskN[a]
            if (wd.carryN[a] == 0) res2[wd.taskSrc[a] * wd.R + wd.taskR[a]] += wd.taskN[a]
        }
        for (k in inc.indices) {
            assertEquals(inc[k], wd.incoming[k], "incoming ${k / wd.R}/${k % wd.R}")
            assertEquals(res2[k], wd.reserved[k], "reserved ${k / wd.R}/${k % wd.R}")
        }
        for (b in 0 until wd.buildingCount) {
            assertEquals(res[b], wd.bResidents[b], "жильцы здания $b")
            assertEquals(workers[b], wd.bWorkers[b], "рабочие здания $b")
            for (r in 0 until wd.R) assertTrue(wd.inv[b * wd.R + r] >= 0, "запас $b/$r")
        }
    }

    /** Небольшой город с дровосеком, лесопилкой и домами */
    private fun town(seed: Int): World {
        val wd = World.create(content, seed, MapSize.MEDIUM)
        for (id in listOf("woodcutter", "house", "sawmill", "well", "house")) {
            placeNearNoStep(wd, id)
            repeat(50) { wd.step() }
        }
        return wd
    }

    private fun nearestSpot(wd: World, def: Int): Int {
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        for (d in 0 until wd.w + wd.h) {
            for (dx in -d..d) for (sgn in intArrayOf(-1, 1)) {
                val dy = (d - Math.abs(dx)) * sgn
                if (dy == 0 && sgn > 0) continue
                val x = hx + dx; val y = hy + dy
                if (x in 0 until wd.w && y in 1 until wd.h && wd.canBuild(def, x, y)) return y * wd.w + x
            }
        }
        error("no place for ${wd.content.buildings[def].id}")
    }

    private fun placeNearNoStep(wd: World, id: String): Int {
        val def = content.building(id)
        val best = nearestSpot(wd, def.index)
        wd.submit(Command.PLACE, def.index, best % wd.w, best / wd.w)
        return wd.lastPlaced
    }

    @Test
    fun toolsWearAndSlowWork() {
        val wd = World.create(content, 3, MapSize.SMALL)
        val tools = res("tools")
        val b = wd.addBuilding(content.building("sawmill").index, 2, 2, B_ACTIVE)
        wd.bTool[b] = Tune.TOOL_LIFE
        val fast = wd.workSpeed(b)
        assertEquals(wd.levelSpeed(b), fast)
        // инструмент изнашивается; запасного нет - работа медленнее
        wd.wearTool(b, Tune.TOOL_LIFE)
        assertEquals(wd.levelSpeed(b) * (100 - Tune.TOOL_SLOW) / 100, wd.workSpeed(b))
        // запасной инструмент берётся сам
        wd.inv[b * wd.R + tools] = 1
        wd.wearTool(b, 0)
        assertEquals(0, wd.inv[b * wd.R + tools])
        assertEquals(Tune.TOOL_LIFE, wd.bTool[b])
        assertEquals(fast, wd.workSpeed(b))
        // износ уходит в сохранение
        wd.bTool[b] = 777
        assertEquals(777, SaveCodec.read(content, SaveCodec.write(wd)).bTool[b])
    }
}
