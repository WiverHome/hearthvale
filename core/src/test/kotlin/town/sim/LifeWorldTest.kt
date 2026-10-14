package town.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import town.data.Content

/** 0.11-0.12: дети и школа, погода и паводок, соседи и договоры, строители, настройки песочницы */
class LifeWorldTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())
    private val day = Tune.TICKS_PER_DAY

    private fun res(id: String) = content.resource(id).index

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

    private fun addActive(wd: World, id: String): Int {
        val def = content.building(id)
        val spot = nearestSpot(wd, def.index)
        return wd.addBuilding(def.index, spot % wd.w, spot / wd.w, B_ACTIVE)
    }

    @Test
    fun kidsAreBornGrowUpAndLearnAtSchool() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val house = (0 until wd.buildingCount).first { wd.def(it).isHouse }
        // довольный полный дом, ребёнок вот-вот вырастет; второй - только родился
        wd.bHappy[house] = 100
        wd.kidAge[house * Tune.KIDS_MAX] = 1
        wd.kidSchool[house * Tune.KIDS_MAX] = Tune.KID_SCHOOL_DAYS
        repeat(day - wd.tick % day - 1) { wd.step() }
        wd.kidAge[house * Tune.KIDS_MAX] = 1
        val extra = addActive(wd, "house")   // свободные кровати для выросшего (до рассвета их не займут пришлые)
        assertTrue(wd.bResidents[extra] == 0)
        val pop = wd.agentCount
        wd.step()
        assertTrue(wd.agentCount >= pop + 1, "ребёнок вырос и стал жителем (и пришли новые)")
        assertEquals(1, wd.kidsGrown)
        assertEquals(1, wd.skilledTotal(), "учился в школе - учёный")
        assertEquals(0, wd.kidAge[house * Tune.KIDS_MAX])
        // рождаются в довольных домах (за несколько рассветов хоть один) - с уровня «Деревня»
        wd.debugGrant(wd.gold, Tune.KID_LEVEL)
        for (b in 0 until wd.buildingCount) if (wd.def(b).isHouse) wd.bHappy[b] = 100
        var born = false
        repeat(day * 6) {
            wd.step()
            for (b in 0 until wd.buildingCount) if (wd.def(b).isHouse && wd.bResidents[b] > 0) wd.bHappy[b] = 100
            if (wd.kidsTotal() > 0) born = true
        }
        assertTrue(born, "в довольных домах рождаются дети")
    }

    @Test
    fun kidsEatAndSkilledWorkFaster() {
        // одинаковые миры, в одном дети: еды уходит больше
        val a = World.create(content, 3, MapSize.MEDIUM)
        val b = SaveCodec.read(content, SaveCodec.write(a))
        val house = (0 until b.buildingCount).first { b.def(it).isHouse }
        b.kidAge[house * Tune.KIDS_MAX] = 99; b.kidAge[house * Tune.KIDS_MAX + 1] = 99
        repeat(day) { a.step(); b.step() }
        val food = content.needs.first { it.id == "food" }.goods
        val ua = food.sumOf { a.used(it) }; val ub = food.sumOf { b.used(it) }
        assertTrue(ub > ua, "дети едят: без детей $ua, с детьми $ub")
        // учёный работает быстрее
        assertTrue(Tune.SKILL_BONUS > 0)
        val plain = a.agentSpeed(0, a.hall)
        a.aSkill[0] = 1
        assertEquals(plain * (100 + Tune.SKILL_BONUS) / 100, a.agentSpeed(0, a.hall))
    }

    @Test
    fun weatherIsDeterministicAndAffectsFields() {
        val a = World.create(content, 11, MapSize.SMALL)
        val b = World.create(content, 11, MapSize.SMALL)
        val seen = HashSet<Int>()
        repeat(day * 40) {
            a.step(); b.step()
            seen.add(a.weather)
            assertEquals(a.weather, b.weather)
            assertEquals(a.weatherNext, b.weatherNext)
            if (a.season != WINTER) assertTrue(a.weather != W_SNOW)
            if (a.season == WINTER) assertTrue(a.weather != W_RAIN)
            if (a.season != SUMMER) assertTrue(a.weather != W_DROUGHT)
        }
        assertTrue(seen.size >= 3, "за 40 суток разная погода: $seen")
        val farm = a.addBuilding(content.building("farm").index, 2, 2, B_ACTIVE)
        a.weather = W_RAIN
        assertEquals(100 + Tune.RAIN_FIELDS, a.weatherYield(farm))
        a.weather = W_DROUGHT
        assertEquals(100 - Tune.DROUGHT_SOIL, a.weatherYield(farm))
        val saw = a.addBuilding(content.building("sawmill").index, 6, 2, B_ACTIVE)
        assertEquals(100, a.weatherYield(saw), "мастерским погода не мешает")
    }

    @Test
    fun floodStopsLowBuildingsThenFertilizes() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        var lowCells = 0
        for (c in 0 until wd.w * wd.h) if (wd.low[c]) { lowCells++; assertTrue(wd.tiles[c] != WATER) }
        assertTrue(lowCells > 0, "у реки есть пойма")
        // ферма в пойме
        val def = content.building("farm").index
        var spot = -1
        for (c in 0 until wd.w * wd.h) if (wd.canPlace(def, c % wd.w, c / wd.w) && wd.lowCells(def, c % wd.w, c / wd.w) > 0) { spot = c; break }
        assertTrue(spot >= 0)
        val farm = wd.addBuilding(def, spot % wd.w, spot / wd.w, B_ACTIVE)
        assertTrue(wd.bLow[farm])
        wd.floodLeft = 100; wd.floodYear = wd.year
        wd.step()
        assertEquals(BS_FLOOD, wd.bStatus[farm], "в паводок здание в пойме стоит")
        repeat(100) { wd.step() }
        assertEquals(0, wd.floodLeft)
        wd.weather = W_CLEAR
        assertEquals(100 + Tune.FLOOD_BONUS, wd.weatherYield(farm), "после паводка земля плодороднее")
        // сохранение
        val c = SaveCodec.read(content, SaveCodec.write(wd))
        assertEquals(wd.floodYear, c.floodYear)
        assertTrue(c.bLow[farm])
    }

    @Test
    fun neighborsContractDeliversAndPays() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        assertTrue(wd.nbCount in 2..Tune.NB_MAX, "соседи есть: ${wd.nbCount}")
        for (i in 0 until wd.nbCount) assertTrue(wd.nbSurplus[i] != wd.nbNeed[i])
        val sides = (0 until wd.nbCount).map { wd.nbSide[it] }.toSet()
        assertEquals(wd.nbCount, sides.size, "у каждого свой край")
        assertEquals("nb_need_post", wd.contractBlock(0, 1))
        wd.debugGrant(2000, 2)
        val post = addActive(wd, "tradepost")
        assertEquals(null, wd.contractBlock(0, 1))
        val give = wd.ofGiveR[1]; val n = wd.ofGiveN[1]; val goldGain = wd.ofGetN[1]
        wd.submit(Command.CONTRACT, 0, 1, 0)
        assertEquals(give, wd.ctGiveR[0])
        assertEquals("nb_has_contract", wd.contractBlock(0, 0))
        // товара хватает на складе - носильщики везут его в пост; еды и воды с запасом, чтобы город не разбежался
        wd.inv[wd.hall * wd.R + give] += n * 3
        wd.inv[post * wd.R + give] += n   // первая поставка уже в посту
        wd.inv[wd.hall * wd.R + res("bread")] += 200; wd.inv[wd.hall * wd.R + res("water")] += 200
        val gold0 = wd.gold
        var out = false; var back = false
        var t = 0
        while (t++ < day * 8 && !back) {
            wd.step()
            if (wd.wgState[0] != 0) out = true
            if (out && wd.wgState[0] == 0) back = true
        }
        assertTrue(out, "повозка уехала")
        assertTrue(back, "повозка вернулась")
        assertEquals(1, wd.deliveries)
        assertTrue(wd.gold >= gold0 + goldGain - 400, "оплата золотом пришла")
        assertTrue(wd.nbRel[0] > Tune.REL_START, "отношения выросли")
        // сохранение посреди пути повозки
        wd.ctDue[0] = wd.day + 1
        repeat(day + 50) { wd.step() }
        val c = SaveCodec.read(content, SaveCodec.write(wd))
        repeat(800) { wd.step(); c.step() }
        assertEquals(wd.checksum(), c.checksum(), "после загрузки повозка едет так же")
    }

    @Test
    fun missedDeliveriesHurtAndCancel() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        wd.debugGrant(2000, 2)
        addActive(wd, "tradepost")
        wd.submit(Command.CONTRACT, 0, 0, 0)
        val give = wd.ctGiveR[0]
        for (s in 0 until wd.buildingCount) wd.inv[s * wd.R + give] = 0
        wd.nbRel[0] = 50
        repeat(day * (Tune.CONTRACT_DAYS * Tune.CONTRACT_FAILS + 3)) {
            wd.step()
            for (s in 0 until wd.buildingCount) if (wd.bState[s] == B_ACTIVE && wd.def(s).isStore) wd.inv[s * wd.R + give] = 0
        }
        assertEquals(-1, wd.ctGiveR[0], "два срыва - договор расторгнут")
        assertEquals(50 - Tune.REL_FAIL * Tune.CONTRACT_FAILS, wd.nbRel[0])
    }

    @Test
    fun moreBuildersOnlyWithManySites() {
        val wd = World.create(content, 3, MapSize.MEDIUM)
        val base = wd.def(wd.hall).builders
        assertEquals(base, wd.builderTarget())
        wd.debugGrant(5000, 3)
        repeat(base + 5) {
            val d = content.building("house").index
            val s = nearestSpot(wd, d)
            wd.addBuilding(d, s % wd.w, s / wd.w, B_SITE)
        }
        assertEquals(base + 3 * Tune.BUILDERS_LEVEL, wd.builderTarget(), "по строителю за уровень города, пока строек много")
    }

    @Test
    fun sandboxSettings() {
        val poor = World.create(content, 5, MapSize.MEDIUM, true, SandboxSettings(rich = 0, stock = 0, disasters = 0, calm = true))
        val rich = World.create(content, 5, MapSize.MEDIUM, true, SandboxSettings(rich = 2, stock = 2, disasters = 2))
        fun count(w: World, t: Int) = w.tiles.count { it == t }
        assertTrue(count(rich, FOREST) > count(poor, FOREST), "богатая карта - больше леса")
        assertTrue(rich.gold > poor.gold && rich.totalStock(res("planks")) > poor.totalStock(res("planks")), "стартовый запас")
        assertTrue(!poor.threatsOn && poor.calm, "спокойный режим без угроз")
        // спокойный режим: недовольные не уходят
        for (b in 0 until poor.buildingCount) poor.bHappy[b] = 0
        val pop = poor.agentCount
        repeat(day + 5) { poor.step(); for (b in 0 until poor.buildingCount) poor.bHappy[b] = 0 }
        assertTrue(poor.agentCount >= pop, "никто не ушёл")
        val c = SaveCodec.read(content, SaveCodec.write(rich))
        assertEquals(2, c.setDisasters)
        assertTrue(SaveCodec.read(content, SaveCodec.write(poor)).calm)
    }

    @Test
    fun mapCodeGivesSameMap() {
        val a = World.create(content, -123456789, MapSize.LARGE, false, SandboxSettings(rich = 2, stock = 0, disasters = 1, calm = false))
        val code = a.mapCode()!!
        assertEquals(11, code.length)
        val p = MapCode.decode(code.lowercase().replace("0", "o"))!!   // регистр и O вместо нуля не мешают
        assertEquals(-123456789, p.seed); assertEquals(MapSize.LARGE, p.size); assertEquals(2, p.settings.rich); assertEquals(0, p.settings.stock)
        assertTrue(!p.threats)
        val b = World.create(content, p.seed, p.size, p.threats, p.settings)
        assertEquals(code, b.mapCode())
        repeat(500) { a.step(); b.step() }
        assertEquals(a.checksum(), b.checksum(), "та же карта - та же партия")
        // опечатка в одном знаке - не код (проверочные биты)
        val bad = code.substring(0, 3) + (if (code[3] == 'A') 'B' else 'A') + code.substring(4)
        assertEquals(null, MapCode.decode(bad))
        // слово - сид, одинаковый всегда
        assertEquals(MapCode.seedOf("Мойгород"), MapCode.seedOf(" мойгород "))
        assertEquals(42, MapCode.seedOf("42"))
        // у сценария кода нет
        assertEquals(null, World.create(content, content.scenarios[0]).mapCode())
    }

    @Test
    fun mapCodeSurvivesGarbage() {
        // мусорный ввод: не падает, код - только настоящий код, остальное - сид из слова
        val junk = listOf("", " ", "-----", "😀😀😀", "ZZZZZ-ZZZZZ", "00000-00000", "x".repeat(10000), "12345678901234567890", "-2147483648",
            "мой город", "UUUUU-UUUUU", "\u0000\n\t", "AAAAA-AAAA", "AAAAA-AAAAAA")
        for (t in junk) {
            val p = MapCode.decode(t)
            if (p != null) assertEquals(MapCode.encode(p).replace("-", ""), t.uppercase().replace("-", "").replace("O", "0"))
            MapCode.seedOf(t)
        }
        assertEquals(Int.MIN_VALUE, MapCode.seedOf("-2147483648"))
        // все сочетания условий кодируются и раскодируются туда-обратно
        val rng = Rng(7)
        for (k in 0 until 2000) {
            val p = MapCode.Params(rng.next(), MapSize.values()[rng.next(3)], SandboxSettings(rng.next(3), rng.next(3), rng.next(3), rng.next(2) == 1), rng.next(2) == 1)
            val q = MapCode.decode(MapCode.encode(p))!!
            assertEquals(p.seed, q.seed); assertEquals(p.size, q.size); assertEquals(p.threats, q.threats)
            assertEquals(p.settings.rich, q.settings.rich); assertEquals(p.settings.stock, q.settings.stock)
            assertEquals(p.settings.disasters, q.settings.disasters); assertEquals(p.settings.calm, q.settings.calm)
        }
    }
}
