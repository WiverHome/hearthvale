package town.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import town.data.Content

class WorldTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())
    private val day = Tune.TICKS_PER_DAY

    @Test
    fun deterministicAndFast() {
        val a = town(42, MapSize.LARGE)
        val t0 = System.nanoTime()
        repeat(day * 3) { a.step() }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val b = town(42, MapSize.LARGE)
        repeat(day * 3) { b.step() }
        assertEquals(a.checksum(), b.checksum())
        // эта строка сравнивается с замером в браузере и на Android (клавиша B)
        println("JVM: ${day * 3} ticks in $ms ms, checksum ${a.checksum()}, agents ${a.agentCount}, buildings ${a.buildingCount}")
    }

    @Test
    fun allSizesStart() {
        for (size in MapSize.values()) {
            val wd = World.create(content, 7, size)
            assertEquals(content.start.citizens, wd.agentCount, "$size")
            assertEquals(1 + content.start.houses, wd.buildingCount, "$size")
            repeat(300) { wd.step() }
        }
    }

    @Test
    fun economyGrows() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val logs = content.resource("logs").index
        val logs0 = wd.totalStock(logs)
        val wc = placeNear(wd, "woodcutter")
        placeNear(wd, "house")
        repeat(Tune.BASE_DAY * 5) { wd.step() }   // 5 минут: без еды жители дольше не ждут
        assertEquals(B_ACTIVE, wd.bState[wc], "дровосек должен достроиться")
        assertTrue(wd.totalStock(logs) > logs0, "брёвна должны прибывать: ${wd.totalStock(logs)} vs $logs0")
        assertTrue(wd.agentCount > content.start.citizens, "в новый дом должны прийти жители: ${wd.agentCount}")
        println("economy: logs ${wd.totalStock(logs)}, agents ${wd.agentCount}, workers at woodcutter ${wd.bWorkers[wc]}")
    }

    @Test
    fun saveLoadContinuesIdentically() {
        val a = town(42, MapSize.MEDIUM)
        repeat(day + 300) { a.step() }   // ночью и днём, с грузами в руках
        val text = SaveCodec.write(a)
        val b = SaveCodec.read(content, text)
        assertEquals(a.checksum(), b.checksum())
        assertEquals(text, SaveCodec.write(b))
        repeat(day * 2) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
        println("save size ${text.length / 1024} KB (96x96, ${a.agentCount} agents, ${a.buildingCount} buildings)")
    }

    @Test
    fun citizenNamesAndHistoryStable() {
        content.loadNames(File("../assets/data/names.json").readText())
        val a = town(42, MapSize.MEDIUM)
        repeat(day * 2) { a.step() }
        assertTrue(a.agentCount > 2)
        for (i in 0 until a.agentCount) assertTrue(a.aSerial[i] in 0 until a.nextSerial)
        assertEquals(a.agentCount, a.aSerial.take(a.agentCount).toSet().size)
        assertTrue((0 until a.agentCount).any { a.aCarried[it] > 0 })
        val b = SaveCodec.read(content, SaveCodec.write(a))
        for (i in 0 until a.agentCount) {
            assertEquals(a.aSerial[i], b.aSerial[i])
            assertEquals(a.aCarried[i], b.aCarried[i])
            assertEquals(a.aFavR[i], b.aFavR[i])
            assertEquals(content.citizenName(a.seed, a.aSerial[i], "ru"), content.citizenName(b.seed, b.aSerial[i], "ru"))
        }
        assertEquals(a.nextSerial, b.nextSerial)
        assertTrue(content.citizenName(42, 0, "en") != "#0")
    }

    @Test
    fun replayFromCommandLog() {
        val a = World.create(content, 5, MapSize.SMALL)
        for (id in listOf("woodcutter", "house", "well", "house")) {
            repeat(200) { a.step() }
            placeNear(a, id)
        }
        a.submit(Command.UNDO, 0, 0, 0)
        repeat(300) { a.step() }
        a.submit(Command.DEMOLISH, a.buildingCount - 2, 0, 0)
        placeNear(a, "house")
        repeat(day * 2) { a.step() }
        assertTrue(a.log.size >= 3, "команды должны примениться: ${a.log.size}")

        val b = World.create(content, 5, MapSize.SMALL)
        b.schedule(a.log.toList())
        repeat(a.tick) { b.step() }
        assertEquals(a.checksum(), b.checksum())
    }

    @Test
    fun housesGetGoodsAndPayTaxes() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val food = content.needs.first { it.id == "food" }.index
        val gold0 = wd.gold
        repeat(day * 2 + 10) { wd.step() }
        val houses = (0 until wd.buildingCount).filter { wd.def(it).isHouse && wd.bResidents[it] > 0 }
        assertTrue(houses.isNotEmpty())
        for (h in houses) assertTrue(wd.bNeedOk[h] and (1 shl food) != 0, "в дом $h должны принести еду")
        assertTrue(wd.townHappy >= Tune.ARRIVE_MIN, "довольство ${wd.townHappy}")
        assertTrue(wd.gold > gold0, "налоги: ${wd.gold} vs $gold0")
    }

    @Test
    fun hungerDrivesCitizensAway() {
        val wd = World.create(content, 4, MapSize.SMALL)
        for (id in listOf("bread", "fish")) wd.inv[wd.hall * wd.R + content.resource(id).index] = 0
        placeNear(wd, "woodcutter")
        val start = wd.agentCount
        repeat(day * 4) { wd.step(); if (wd.tick % 97 == 0) checkInvariants(wd) }
        assertTrue(wd.townHappy < Tune.ARRIVE_MIN, "без еды довольство падает: ${wd.townHappy}")
        assertTrue(wd.agentCount < start, "голодные уходят: ${wd.agentCount} из $start")
        checkInvariants(wd)
    }

    @Test
    fun removingCitizensKeepsWorldConsistent() {
        val a = town(42, MapSize.MEDIUM)
        repeat(day / 2) { a.step() }
        // убираем жителей посреди дня - с грузами, маршрутами и в очереди путей
        var k = 0
        while (a.agentCount > 6 && k < 5) {
            val c = (0 until a.agentCount).lastOrNull { a.taskDst[it] < 0 } ?: break
            a.removeCitizen(c); k++
        }
        checkInvariants(a)
        repeat(day) { a.step() }
        checkInvariants(a)
    }

    @Test
    fun winterStopsFarms() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.level = 1
        val farm = placeNear(wd, "farm")
        repeat(day * Tune.SEASON_DAYS * 3 + day / 2 - wd.tick) { wd.step() }
        assertEquals(WINTER, wd.season)
        wd.bFire[farm] = 0; wd.step()   // случайный пожар здесь не проверяется
        assertEquals(B_ACTIVE, wd.bState[farm])
        assertEquals(BS_SEASON, wd.bStatus[farm])
        assertEquals(0, wd.bWorkers[farm], "зимой фермеры становятся носильщиками")
    }

    @Test
    fun traderBuysAndSells() {
        val wd = World.create(content, 3, MapSize.SMALL)
        repeat(day + 5) { wd.step() }
        assertTrue(wd.traderHere, "торговец приходит на второй день")
        val planks = content.resource("planks").index
        val stone = content.resource("stone").index
        val p0 = wd.stock(wd.hall, planks); val g0 = wd.gold
        wd.submit(Command.TRADE, planks, 5, 0); wd.step()
        assertEquals(p0 + 5, wd.stock(wd.hall, planks))
        assertEquals(g0 - 5 * wd.buyPrice(planks), wd.gold)
        wd.submit(Command.SELL_ABOVE, stone, 10, 0); wd.step()
        assertTrue(wd.totalStock(stone) <= 10 + Tune.CARRY * wd.agentCount, "автопродажа камня: ${wd.totalStock(stone)}")
        assertTrue(wd.gold > g0 - 5 * wd.buyPrice(planks))
        repeat(Tune.TRADER_STAY) { wd.step() }
        assertTrue(!wd.traderHere, "торговец уходит до следующего утра")
    }

    @Test
    fun unpaidBuildingStopsAndResumes() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.level = 1
        val def = content.building("sawmill")
        val spot = nearestSpot(wd, def.index)
        val b = wd.addBuilding(def.index, spot % wd.w, spot / wd.w, B_ACTIVE)
        wd.gold = 0; wd.bUnpaid[b] = 1
        repeat(Tune.NEEDS_INTERVAL * 2) { wd.step() }
        assertEquals(BS_UNPAID, wd.bStatus[b])
        assertEquals(0, wd.bWorkers[b], "без жалованья рабочие уходят носить")
        wd.gold = 100
        repeat(Tune.NEEDS_INTERVAL) { wd.step() }
        assertEquals(0, wd.bUnpaid[b], "золото появилось - содержание заплачено")
        assertEquals(100 - wd.upkeepOf(b), wd.gold)
        // на рассвете платятся все здания (задания не мешают: их награда тоже золото)
        for (i in 0 until Tune.MAX_QUESTS) wd.qType[i] = 0
        wd.scenario = 0   // сценарий без заданий в слотах - новые не придумываются
        wd.gold = 1000
        repeat(day - wd.tick % day) { wd.step() }
        assertEquals(wd.upkeepPerDay(), wd.lastUpkeep)
        assertEquals(1000 + wd.lastTax - wd.lastUpkeep, wd.gold)
    }

    @Test
    fun callingTraderCostsGold() {
        val wd = World.create(content, 3, MapSize.SMALL)
        repeat(5) { wd.step() }
        assertTrue(!wd.traderHere, "в первый день торговца нет")
        wd.gold = 500
        val price = wd.traderCallPrice()
        wd.submit(Command.CALL_TRADER, 0, 0, 0)
        assertTrue(wd.traderHere)
        assertEquals(500 - price, wd.gold)
        assertTrue(wd.traderStock[content.resource("planks").index] > 0)
        wd.submit(Command.CALL_TRADER, 0, 0, 0)
        assertEquals(500 - price, wd.gold, "второй раз не зовут: он уже здесь")
    }

    @Test
    fun decorServesWithoutWorkers() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val house = content.building("house")
        val spot = nearestSpot(wd, house.index)
        val h = wd.addBuilding(house.index, spot % wd.w, spot / wd.w, B_ACTIVE)
        assertTrue(!wd.served(h, "decor"))
        val bed = content.building("flowerbed")
        var near = -1
        for (dy in -2..3) for (dx in -2..3) {
            val x = wd.bX[h] + dx; val y = wd.bY[h] + dy
            if (near < 0 && wd.canBuild(bed.index, x, y)) near = y * wd.w + x
        }
        val f = wd.addBuilding(bed.index, near % wd.w, near / wd.w, B_ACTIVE)
        repeat(Tune.NEEDS_INTERVAL) { wd.step() }
        assertEquals(0, wd.bWorkers[f])
        assertEquals(BS_OK, wd.bStatus[f])
        assertTrue(wd.served(h, "decor"), "клумба рядом с домом")
    }

    @Test
    fun commandsApplyOnPause() {
        val wd = World.create(content, 3, MapSize.SMALL)
        val n = wd.buildingCount
        val t = wd.tick
        placeNearNoStep(wd, "house")
        assertEquals(n + 1, wd.buildingCount, "площадка появляется без хода времени")
        assertEquals(t, wd.tick)
        assertTrue(wd.canUndo)
    }

    @Test
    fun undoRefundsEverything() {
        val wd = World.create(content, 3, MapSize.SMALL)
        val planks = content.resource("planks").index; val stone = content.resource("stone").index
        val p0 = wd.totalStock(planks); val s0 = wd.totalStock(stone)
        val site = placeNear(wd, "store")
        repeat(60) { wd.step() }
        assertTrue(wd.totalStock(planks) < p0, "материалы везут на площадку")
        wd.submit(Command.UNDO, 0, 0, 0)
        assertEquals(B_GONE, wd.bState[site])
        assertTrue(!wd.canUndo)
        checkInvariants(wd)
        repeat(400) { wd.step() }
        assertEquals(p0, wd.totalStock(planks)); assertEquals(s0, wd.totalStock(stone))
        val again = placeNear(wd, "house")
        assertEquals(site, again, "номер снесённого здания используется снова")
    }

    @Test
    fun demolishAndMove() {
        val wd = town(42, MapSize.MEDIUM)
        repeat(day * 2) { wd.step() }
        val wc = (0 until wd.buildingCount).first { wd.def(it).id == "woodcutter" && wd.bState[it] == B_ACTIVE }
        val workers = wd.bWorkers[wc]
        assertTrue(workers > 0)
        // перенос дровосека на другое место у леса
        val def = wd.def(wc)
        // ближайшее подходящее место дальше 4 клеток (дальний угол карты строители за день не обойдут)
        val spot = (0 until wd.w * wd.h).filter {
            val x = it % wd.w; val y = it / wd.w
            y > 0 && Math.abs(x - wd.bX[wc]) + Math.abs(y - wd.bY[wc]) > 4 && wd.canBuild(def.index, x, y)
        }.minByOrNull { Math.abs(it % wd.w - wd.bX[wc]) + Math.abs(it / wd.w - wd.bY[wc]) }!!
        wd.submit(Command.MOVE, wc, spot % wd.w, spot / wd.w)
        val site = wd.lastPlaced
        assertEquals(wc, wd.bMoveFrom[site])
        assertEquals(site, wd.movingTo(wc))
        repeat(day * 3) { wd.step(); if (wd.tick % 101 == 0) checkInvariants(wd) }
        assertEquals(B_ACTIVE, wd.bState[site], "новое место достроено")
        assertEquals(B_GONE, wd.bState[wc], "старое снесено")
        assertEquals(workers, wd.bWorkers[site], "рабочие переехали")

        // снос дома: жильцы переселяются или уходят, половина стоимости возвращается
        val house = (0 until wd.buildingCount).first { wd.def(it).isHouse && wd.bState[it] == B_ACTIVE && wd.bResidents[it] > 0 }
        val planks = content.resource("planks").index
        val p0 = wd.totalStock(planks)
        wd.submit(Command.DEMOLISH, house, 0, 0)
        assertEquals(B_GONE, wd.bState[house])
        assertTrue(wd.totalStock(planks) >= p0 + wd.def(house).cost[planks] * Tune.REFUND / 100)
        checkInvariants(wd)

        // сохранение со снесёнными зданиями
        val b = SaveCodec.read(content, SaveCodec.write(wd))
        repeat(day) { wd.step(); b.step() }
        assertEquals(wd.checksum(), b.checksum())
        checkInvariants(wd)
    }

    @Test
    fun productionLimit() {
        val wd = town(42, MapSize.MEDIUM)
        repeat(day * 2) { wd.step() }
        val wc = (0 until wd.buildingCount).first { wd.def(it).id == "woodcutter" }
        val logs = content.resource("logs").index
        wd.bFire[wc] = 0   // пожар (случайный) важнее лимита
        wd.submit(Command.LIMIT, wc, wd.totalStock(logs), 0)
        wd.step()
        assertEquals(BS_LIMIT, wd.bStatus[wc])
    }

    @Test
    fun sandboxGeneratesQuestsAndPaysRewards() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        repeat(Tune.NEEDS_INTERVAL) { wd.step() }
        assertEquals(Tune.SANDBOX_QUESTS, wd.activeQuests(), "в песочнице сразу есть задания")
        // задание на постройку выполняется постройкой
        val q = (0 until Tune.MAX_QUESTS).firstOrNull { wd.qType[it] == town.data.Quest.BUILD }
        if (q != null) {
            val id = wd.content.buildings[wd.qA[q]].id
            val gold0 = wd.gold
            wd.level = maxOf(wd.level, wd.content.buildings[wd.qA[q]].level)
            wd.gold += 500
            repeat(wd.qB[q] - wd.countBuilt(wd.qA[q])) { placeNear(wd, id) }
            repeat(day * 3) { wd.step() }
            assertTrue(wd.questsDone >= 1, "задание на постройку $id выполнено")
            assertTrue(wd.gold > gold0, "награда")
        }
        repeat(day * 4) { wd.step() }
        assertEquals(Tune.SANDBOX_QUESTS, wd.activeQuests(), "выполненные задания сменяются новыми")
    }

    @Test
    fun scenariosLoadAndStart() {
        assertTrue(content.scenarios.size >= 3)
        for (sc in content.scenarios) {
            val wd = World.create(content, sc)
            assertEquals(sc.index, wd.scenario)
            assertEquals(1, wd.activeQuests(), "${sc.id}: первое задание выдано")
            assertEquals(sc.startDay, wd.day, sc.id)
            repeat(day) { wd.step() }
            assertEquals(PLAYING, wd.outcome, sc.id)
            val b = SaveCodec.read(content, SaveCodec.write(wd))
            repeat(200) { wd.step(); b.step() }
            assertEquals(wd.checksum(), b.checksum(), sc.id)
        }
    }

    @Test
    fun tutorialChainAdvancesToVictory() {
        val sc = content.scenarios.first { it.id == "tutorial" }
        val wd = World.create(content, sc)
        // проходим цепочку, подставляя выполнение каждого условия напрямую
        var guard = 0
        while (wd.outcome == PLAYING && guard++ < 40) {
            val i = (0 until Tune.MAX_QUESTS).first { wd.qType[it] != 0 }
            when (wd.qType[i]) {
                town.data.Quest.BUILD -> {
                    val id = wd.content.buildings[wd.qA[i]].id
                    wd.level = maxOf(wd.level, wd.content.buildings[wd.qA[i]].level)
                    wd.gold += 200
                    while (wd.countBuilt(wd.qA[i]) + countSites(wd, wd.qA[i]) < wd.qB[i]) {
                        val site = placeNear(wd, id)
                        val cost = wd.def(site).cost
                        for (r in cost.indices) wd.inv[site * wd.R + r] = cost[r]   // материалы сразу на площадке
                    }
                    var t = 0
                    while (wd.countBuilt(wd.qA[i]) < wd.qB[i] && t++ < day * 10) wd.step()
                }
                town.data.Quest.LEVEL -> wd.level = wd.qB[i]
                town.data.Quest.STOCK -> wd.inv[wd.hall * wd.R + wd.qA[i]] += wd.qB[i]
                town.data.Quest.HAPPY -> { }
                else -> error("неожиданное задание")
            }
            wd.inv[wd.hall * wd.R + content.resource("bread").index] = 40   // экономика здесь не проверяется: город сыт
            wd.inv[wd.hall * wd.R + content.resource("water").index] = 40
            repeat(day + Tune.NEEDS_INTERVAL) { wd.step() }
            if (wd.qType[i] == town.data.Quest.HAPPY) repeat(day * 4) { wd.step() }
        }
        assertEquals(WON, wd.outcome, "обучение пройдено, заданий выполнено ${wd.questsDone}")
        assertTrue(wd.questsDone >= sc.quests.size, "после победы задания идут как в песочнице")
    }

    @Test
    fun scenarioFailsAfterDeadlineAndGrace() {
        val sc = content.scenarios.first { it.id == "long_winter" }
        val wd = World.create(content, sc)
        // жители уходят и не возвращаются - через FAIL_GRACE провал
        fun emptyTown() { while (wd.agentCount > 0) wd.removeCitizen(wd.agentCount - 1) }
        repeat(Tune.NEEDS_INTERVAL * 2) { emptyTown(); wd.step() }
        assertTrue(wd.failTimer > 0, "предупреждение: таймер провала идёт")
        assertEquals(PLAYING, wd.outcome)
        repeat(Tune.FAIL_GRACE) { emptyTown(); wd.step() }
        assertEquals(LOST, wd.outcome)

        // срок
        val late = World.create(content, sc)
        late.tick = sc.deadline * Tune.TICKS_PER_DAY
        repeat(Tune.NEEDS_INTERVAL) { late.step() }
        assertEquals(LOST, late.outcome)
    }

    /** Ближайшее к ратуше место, где можно строить (кольцами по манхэттенскому расстоянию) */
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

    private fun countSites(wd: World, def: Int) = (0 until wd.buildingCount).count { wd.bState[it] == B_SITE && wd.bDef[it] == def }

    @Test
    fun bigTownPerformance() {
        // город на 200 жителей: 50 домов и мастерские вокруг ратуши, ставятся сразу готовыми
        val wd = World.create(content, 11, MapSize.LARGE)
        wd.level = 2
        fun put(id: String) {
            val def = content.building(id)
            val best = nearestSpot(wd, def.index)
            wd.addBuilding(def.index, best % wd.w, best / wd.w, B_ACTIVE)
        }
        repeat(50) { put("house") }
        for (id in listOf("woodcutter", "woodcutter", "sawmill", "quarry", "well", "well", "fisher", "fisher", "farm", "farm", "mill", "bakery", "market", "church", "store", "store")) put(id)
        while (wd.agentCount < 200) wd.spawnCitizen()
        repeat(200) { wd.step() }
        val t0 = System.nanoTime()
        val paths0 = wd.pathsDone; val hits0 = wd.pathCacheHits; val nodes0 = wd.nodesExpanded
        repeat(2000) { wd.step() }
        val ms = (System.nanoTime() - t0) / 1_000_000
        checkInvariants(wd)
        val paths = wd.pathsDone - paths0
        println("200 citizens: 2000 ticks in $ms ms (${2000 * 1000 / maxOf(1, ms)} ticks/s; 10x speed needs 200), buildings ${wd.buildingCount}, " +
            "paths $paths, from cache ${(wd.pathCacheHits - hits0) * 100 / maxOf(1, paths)}%, A* nodes ${wd.nodesExpanded - nodes0}")
        assertTrue(ms < 4000, "2000 тиков дольше 4 с: $ms мс")
    }

    @Test
    fun roadsCostStoneAndSpeedUpWalking() {
        val wd = World.create(content, 3, MapSize.SMALL)
        val stone = content.resource("stone").index
        val s0 = wd.totalStock(stone)
        val x = wd.bX[wd.hall] + 4; val y = wd.bY[wd.hall] - 3
        val cell = (0 until wd.w * wd.h).first { wd.canRoad(it % wd.w, it / wd.w) && Math.abs(it % wd.w - x) + Math.abs(it / wd.w - y) < 30 }
        wd.submit(Command.ROAD, cell % wd.w, cell / wd.w, 1)
        assertEquals(ROAD, wd.tiles[cell])
        assertEquals(s0 - Tune.ROAD_STONE, wd.totalStock(stone))
        assertTrue(wd.cost[cell] < Tune.COST_GRASS)
        wd.submit(Command.ROAD, cell % wd.w, cell / wd.w, 0)
        assertEquals(GRASS, wd.tiles[cell])
        // брод не убирается
        val ford = (0 until wd.w * wd.h).first { wd.tiles[it] == ROAD }
        wd.submit(Command.ROAD, ford % wd.w, ford / wd.w, 0)
        assertEquals(ROAD, wd.tiles[ford])
    }

    @Test
    fun statsCountProductionAndConsumption() {
        val wd = town(42, MapSize.MEDIUM)
        repeat(day * 3) { wd.step() }
        val logs = content.resource("logs").index
        val planks = content.resource("planks").index
        assertTrue(wd.produced(logs) > 0, "брёвна произведены за вчера")
        // за прошлые сутки (лесопилка могла уже встать на лимите досок)
        assertTrue((1..wd.histDays).sumOf { wd.produced(planks, it) + wd.used(logs, it) } > 0, "лесопилка: доски из брёвен")
        val food = content.needs.first { it.id == "food" }.goods
        assertTrue(food.sumOf { wd.used(it) } > 0, "дома едят")
        assertTrue(wd.histDays in 1..Tune.STAT_DAYS)
        assertTrue(wd.lastTax > 0)
    }

    @Test
    fun fullHappyHouseUpgrades() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.level = 1
        val house = (0 until wd.buildingCount).first { wd.def(it).isHouse }
        assertEquals(wd.def(house).beds, wd.bResidents[house], "стартовый дом полон")
        // дом сам не улучшается: готов - игрок жмёт «Улучшить»
        var pressed = false
        repeat(day * (Tune.UPGRADE_DAYS + 1)) {
            wd.step(); if (wd.tick % 97 == 0) checkInvariants(wd)
            if (!pressed && wd.houseReady(house)) {
                assertEquals(null, wd.upgradeBlock(house))
                wd.submit(Command.UPGRADE, house, 0, 0); pressed = true
            }
        }
        assertTrue(pressed, "дом стал готов к улучшению")
        assertEquals("house2", wd.def(house).id, "дом улучшен: довольство ${wd.bHappy[house]}, ждёт материалов ${wd.bUpg[house]}")
        assertTrue((0 until wd.evCount).any { wd.eventAt(it)[0] == EV_UPGRADED })
        repeat(Tune.BASE_DAY) { wd.step() }
        assertTrue(wd.bResidents[house] > 4, "в улучшенный дом въехали: ${wd.bResidents[house]}")
    }

    @Test
    fun workshopLevelsUp() {
        val wd = town(42, MapSize.MEDIUM)
        repeat(day * 2) { wd.step() }   // лесопилка достроена
        wd.debugGrant(wd.gold + 500, 2)
        val saw = (0 until wd.buildingCount).first { wd.def(it).id == "sawmill" && wd.bState[it] == B_ACTIVE }
        assertEquals(1, wd.bLevel[saw])
        val gold0 = wd.gold
        assertEquals(null, wd.upgradeBlock(saw), "можно улучшить")
        wd.submit(Command.UPGRADE, saw, 0, 0)
        assertEquals(gold0 - Tune.LEVEL_GOLD[2], wd.gold, "золото сразу")
        assertEquals(1, wd.bUpg[saw])
        repeat(day * 2) { wd.step(); if (wd.tick % 97 == 0) checkInvariants(wd) }
        assertEquals(2, wd.bLevel[saw], "материалы привезли - уровень 2")
        assertEquals(0, wd.bUpg[saw])
        // сохранение помнит уровень
        val b = SaveCodec.read(content, SaveCodec.write(wd))
        assertEquals(2, b.bLevel[saw])
        assertEquals(wd.checksum(), b.checksum())
        // третий уровень - ещё рабочее место (материалы - на склад ратуши)
        for (r in 0 until wd.R) wd.inv[wd.hall * wd.R + r] += wd.upgradeCost(saw, r)
        assertEquals(null, wd.upgradeBlock(saw))
        wd.submit(Command.UPGRADE, saw, 0, 0)
        repeat(day * 2) { wd.step() }
        assertEquals(3, wd.bLevel[saw])
        assertEquals(wd.def(saw).workers + 1, wd.maxWorkers(saw))
        assertEquals("upg_max", wd.upgradeBlock(saw))
    }

    @Test
    fun fireBurnsStockUnlessWatchNearby() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val logs = content.resource("logs").index
        val house = (0 until wd.buildingCount).first { wd.def(it).isHouse }
        wd.inv[house * wd.R + logs] = 5
        wd.bFire[house] = Tune.NEEDS_INTERVAL * 2
        repeat(Tune.NEEDS_INTERVAL * 3) { wd.step() }
        assertEquals(0, wd.bFire[house])
        assertEquals(0, wd.stock(house, logs), "без каланчи запасы сгорели")
        assertTrue((0 until wd.evCount).any { wd.eventAt(it)[0] == EV_FIRE_OUT && wd.eventAt(it)[2] == 1 })
        // с каланчей - без потерь
        wd.inv[house * wd.R + logs] = 5
        wd.bFire[house] = Tune.NEEDS_INTERVAL * 2
        wd.bFireSafe[house] = 1
        repeat(Tune.NEEDS_INTERVAL * 3) { wd.step() }
        assertEquals(5, wd.stock(house, logs) + wd.used(logs, 0), "с каланчей запасы целы")
    }

    @Test
    fun disastersAreDeterministicAndSaved() {
        val a = town(42, MapSize.MEDIUM)
        repeat(day * 2) { a.step() }
        // в ленте последние Tune.EVENTS событий - «построено» проверяем, пока его не вытеснили
        assertTrue((maxOf(0, a.evCount - Tune.EVENTS) until a.evCount).any { a.eventAt(it)[0] == EV_BUILT }, "событие «построено»")
        repeat(day * 12) { a.step() }
        val b = SaveCodec.read(content, SaveCodec.write(a))
        repeat(day * 4) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
        assertEquals(a.evCount, b.evCount)
        checkInvariants(a)
    }

    /** Жильцы, рабочие и брони сходятся с жителями */
    private fun checkInvariants(wd: World) {
        val res = IntArray(wd.buildingCount); val workers = IntArray(wd.buildingCount)
        for (a in 0 until wd.agentCount) {
            assertTrue(wd.home[a] in 0 until wd.buildingCount && wd.bState[wd.home[a]] != B_GONE, "дом жителя $a")
            res[wd.home[a]]++
            if (wd.job[a] == JOB_WORKER) {
                assertEquals(B_ACTIVE, wd.bState[wd.jobB[a]], "работа жителя $a")
                workers[wd.jobB[a]]++
            }
            if (wd.taskDst[a] >= 0) assertTrue(wd.bState[wd.taskDst[a]] != B_GONE, "заявка жителя $a")
        }
        // брони и ожидания сходятся с заявками носильщиков
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
        for (i in 0 until wd.queueSize) assertTrue(wd.queue[(wd.qHead + i) % Tune.MAX_AGENTS] < wd.agentCount, "очередь путей")
    }

    /** Небольшой город с дровосеком, лесопилкой и домами - для проверок детерминизма и сохранений */
    private fun town(seed: Int, size: MapSize): World {
        val wd = World.create(content, seed, size)
        for (id in listOf("woodcutter", "house", "sawmill", "well", "house")) {
            placeNear(wd, id)
            repeat(50) { wd.step() }
        }
        return wd
    }

    @Test
    fun rotatedBuildingsKeepEntranceAndSurviveSave() {
        val a = World.create(content, 5, MapSize.SMALL)
        val def = content.building("house")
        val s = def.size
        // по дому на каждую сторону; вход - снаружи на выбранной стороне
        for (dir in 0..3) {
            var spot = -1
            for (c in 0 until a.w * a.h) if (a.canBuild(def.index, c % a.w, c / a.w, dir)) { spot = c; break }
            assertTrue(spot >= 0, "нет места для дома со стороной $dir")
            val x = spot % a.w; val y = spot / a.w
            a.submit(Command.PLACE, def.index, x, y, dir)
            val b = a.lastPlaced
            assertEquals(dir, a.bDir[b])
            val e = a.entrance(b); val ex = e % a.w; val ey = e / a.w
            val ok = when (dir) { 0 -> ey == y - 1 && ex in x until x + s; 1 -> ex == x + s && ey in y until y + s
                2 -> ey == y + s && ex in x until x + s; else -> ex == x - 1 && ey in y until y + s }
            assertTrue(ok, "вход ($ex, $ey) не на стороне $dir дома в ($x, $y)")
        }
        repeat(day) { a.step() }
        val b = SaveCodec.read(content, SaveCodec.write(a))
        for (k in 0 until a.buildingCount) assertEquals(a.bDir[k], b.bDir[k])
        repeat(day) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum())
    }

    private fun placeNear(wd: World, id: String): Int {
        val b = placeNearNoStep(wd, id)
        wd.step()
        return b
    }

    /** Ставит здание в ближайшее к ратуше подходящее место (с нужными клетками рядом); возвращает его номер */
    private fun placeNearNoStep(wd: World, id: String): Int {
        val def = content.building(id)
        val best = nearestSpot(wd, def.index)
        wd.submit(Command.PLACE, def.index, best % wd.w, best / wd.w)
        return wd.lastPlaced
    }

    @Test
    fun storeFilterSkipsRefusedGoods() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.level = 3
        val cut = content.building("woodcutter"); val st = content.building("store")
        val logs = content.resource("logs").index
        val sp = nearestSpot(wd, cut.index)
        val c = wd.addBuilding(cut.index, sp % wd.w, sp / wd.w, B_ACTIVE)
        val sp2 = nearestSpot(wd, st.index)
        val s = wd.addBuilding(st.index, sp2 % wd.w, sp2 / wd.w, B_ACTIVE)
        wd.computeRegions()
        val before = wd.storeWithSpace(c, logs)
        wd.submit(Command.STORE_ACCEPT, s, logs, 0); wd.step()
        assertEquals(0, wd.bAccept[s * wd.R + logs])
        assertTrue(wd.storeWithSpace(c, logs) != s, "склад без брёвен не выбирается")
        assertTrue(wd.storeWithSpace(c, logs) >= 0)
        wd.submit(Command.STORE_ACCEPT, s, -1, 1); wd.step()
        assertEquals(1, wd.bAccept[s * wd.R + logs])
        assertTrue(before >= 0)
        wd.submit(Command.STORE_ACCEPT, s, -2, 0); wd.step()
        assertEquals(0, wd.bAccept[s * wd.R + content.resource("stone").index])
    }
}
