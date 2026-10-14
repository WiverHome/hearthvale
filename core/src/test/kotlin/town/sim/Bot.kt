package town.sim

/**
 * Бот-игрок для проверки баланса: играет как толковый игрок по простым правилам и пишет отчёт по суткам.
 * Только через World.submit (как игрок), без подкормки. Решение - раз в DECIDE тиков днём.
 *
 * Правила: дома, когда кроватей почти не осталось; производство - по числу жителей (таблица target);
 * услуги - по числу домов; не больше maxSites строек одновременно; новая стройка, только если на складах
 * хватает материалов на неё.
 */
class Bot(val wd: World) {
    private val c = wd.content
    private val day = Tune.TICKS_PER_DAY
    val report = StringBuilder()
    /** По суткам: жители, довольство (для итогов) */
    val popByDay = ArrayList<Int>()
    val happyByDay = ArrayList<Int>()
    val fameByDay = ArrayList<Int>()
    val cartsByDay = ArrayList<Int>()
    var crownIssued = 0
    private var lastCrEnd = -1
    private var lastTaxDay = -10
    private val roadCells = ArrayList<Int>()

    // доля времени носильщиков за сутки (выборка каждые SAMPLE тиков днём)
    private var sIdle = 0; private var sToSrc = 0; private var sCarry = 0; private var sOther = 0
    private var samples = 0
    private var hungrySamples = 0
    private val needMiss = IntArray(wd.content.needs.size)
    /** Состояния зданий по типам за сутки: [тип][статус] - число выборок; рабочие внутри / нужно */
    private val statusSamples = HashMap<String, IntArray>()
    private val lastStatus = HashMap<String, IntArray>()
    /** Выборок подряд, когда здание стоит без дела (полное, без сырья, без ресурса, на лимите) */
    private var idleRun = IntArray(0)
    /** Топлива (брёвна и уголь) меньше спроса и мало на складах - по последнему расчёту желаний */
    private var fuelShort = false
    private var limitsSet = false
    /** Замер сроков, тики по типу: стройка (площадка -> готово), улучшение (заказ -> готово; house2, sawmill_l2...) */
    val buildTicks = HashMap<String, MutableList<Int>>()
    val upgradeTicks = HashMap<String, MutableList<Int>>()
    private val siteStart = HashMap<Int, IntArray>()   // здание -> тип, тик
    private val upgStart = HashMap<Int, IntArray>()    // здание -> тип, уровень, тик

    private fun trackTimes() {
        for (b in 0 until wd.buildingCount) {
            val d = wd.bDef[b]; val st = wd.bState[b]
            val s = siteStart[b]
            if (st == B_SITE) { if (s == null || s[0] != d) siteStart[b] = intArrayOf(d, wd.tick) }
            else if (s != null) {
                siteStart.remove(b)
                if (st == B_ACTIVE && s[0] == d) buildTicks.getOrPut(c.buildings[d].id) { ArrayList() }.add(wd.tick - s[1])
            }
            val u = upgStart[b]
            if (st == B_ACTIVE && wd.bUpg[b] == 1) { if (u == null) upgStart[b] = intArrayOf(d, wd.bLevel[b], wd.tick) }
            else if (u != null) {
                upgStart.remove(b)
                val key = when {
                    st != B_ACTIVE -> null
                    d != u[0] -> c.buildings[d].id                               // дом стал house2/house3
                    wd.bLevel[b] > u[1] -> c.buildings[d].id + "_l" + wd.bLevel[b]
                    else -> null
                }
                if (key != null) upgradeTicks.getOrPut(key) { ArrayList() }.add(wd.tick - u[2])
            }
        }
    }

    /** Печатать разбор зданий каждые столько суток (0 - нет) */
    var detailEvery = 5
    /** Вехи партии: что - в какие сутки впервые (кривая сложности: когда приходит каждая механика) */
    val milestones = java.util.LinkedHashMap<String, Int>()
    private var evMilestone = 0
    private fun milestone(k: String, d: Int) { if (k !in milestones) milestones[k] = d }
    private fun trackMilestones(d: Int) {
        for (l in 1..wd.level) milestone("level_$l", d)
        if (wd.kidsTotal() > 0 || wd.kidsGrown > 0) milestone("first_kid", d)
        if (wd.kidsGrown > 0) milestone("first_grown", d)
        for (id in listOf("market", "church", "school", "tradepost", "store", "carpenter", "smithy", "guardtower")) if (count(id) > 0) milestone("built_$id", d)
        if (wd.contracts() > 0) milestone("first_contract", d)
        if (wd.deliveries > 0) milestone("first_delivery", d)
        if (wd.crR >= 0 || wd.crDone > 0) milestone("first_crown", d)
        for (pop in listOf(25, 50, 100, 150)) if (wd.agentCount >= pop) milestone("pop_$pop", d)
        for (n in evMilestone until wd.evCount) {
            if (n < wd.evCount - Tune.EVENTS) continue
            when (wd.eventAt(n)[0]) {
                EV_FIRE -> milestone("first_fire", d); EV_SICK -> milestone("first_sick", d); EV_RAID -> milestone("first_raid", d)
                EV_FLOOD -> milestone("first_flood", d); EV_DROUGHT -> milestone("first_drought", d); EV_LEFT -> milestone("first_left", d)
                EV_HUNGRY -> milestone("first_hungry", d); EV_UNPAID -> milestone("first_unpaid", d)
            }
        }
        evMilestone = wd.evCount
    }

    /** В какие сутки выполнялись задания (по порядку) */
    val questDays = ArrayList<Int>()
    private var questsSeen = 0

    fun runTicks(n: Int) { repeat(n) { if (wd.tick % DECIDE == 0 && !wd.isNight) decide(); wd.step() } }

    fun run(days: Int) {
        report.append(String.format("%-4s %-3s %4s %4s %4s %3s %-14s  %-22s %4s %3s %4s  %s%n",
            "day", "sn", "pop", "beds", "hap", "lvl", "gold tax/upk", "carriers idle/go/carry", "wrk", "bld", "hng", "stock / made-used"))
        for (d in 0 until days) {
            if (wd.outcome != PLAYING) break
            repeat(day) {
                if (wd.tick % DECIDE == 0 && !wd.isNight) decide()
                if (wd.tick % SAMPLE == 0 && !wd.isNight) sample()
                if (wd.tick % 10 == 0) trackTimes()
                wd.step()
            }
            if (wd.crR >= 0 && wd.crEnd != lastCrEnd) { crownIssued++; lastCrEnd = wd.crEnd }
            trackMilestones(d + 1)
            fameByDay.add(wd.prestige)
            dayLine(d + 1)
            if (wd.questsDone > questsSeen) {
                questDays.add(d + 1)
                report.append("     quest done (${wd.questsDone})\n")
                questsSeen = wd.questsDone
            }
        }
    }

    private fun sample() {
        for (a in 0 until wd.agentCount) {
            if (wd.job[a] != JOB_CARRIER) continue
            when {
                wd.carryN[a] > 0 -> sCarry++
                wd.taskDst[a] >= 0 -> sToSrc++
                wd.state[a] == ST_MOVE || wd.state[a] == ST_WAIT_PATH -> sOther++
                else -> sIdle++
            }
        }
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE) continue
            val def = wd.def(b)
            if (def.isHouse && wd.bHungry[b] != 0) hungrySamples++
            if (def.isHouse && wd.bResidents[b] > 0) for (n in c.needs) {
                if (n.level <= wd.level && n.tier <= def.tier && (wd.bNeedOk[b] and (1 shl n.index)) == 0) needMiss[n.index]++
            }
            if (def.isProducer) statusSamples.getOrPut(def.id) { IntArray(STATUS.size) }[wd.bStatus[b]]++
            // зимой зерна нет и мельницы с пекарнями стоят без сырья - это не простой
            if (def.isProducer && wd.season != WINTER) {
                if (idleRun.size < wd.buildingCount) idleRun = idleRun.copyOf(wd.buildingCount + 32)
                val st = wd.bStatus[b]
                // жизненно нужное на лимите или полное - это запас (топливо к зиме), не простой
                val stuck = st == BS_NO_INPUT || st == BS_NO_RESOURCE
                if (stuck || ((st == BS_OUTPUT_FULL || st == BS_LIMIT) && def.id !in VITAL && def.id != "coalmine")) idleRun[b]++
                else if (st == BS_OK) idleRun[b] = 0
            }
        }
        samples++
    }

    private fun dayLine(d: Int) {
        var carriers = 0; var workers = 0; var builders = 0
        for (a in 0 until wd.agentCount) when (wd.job[a]) {
            JOB_CARRIER -> carriers++
            JOB_WORKER -> workers++
            JOB_BUILDER -> builders++
        }
        val tot = maxOf(1, sIdle + sToSrc + sCarry + sOther)
        val car = String.format("%3d %2d%%/%2d%%/%2d%%", carriers, (sIdle + sOther) * 100 / tot, sToSrc * 100 / tot, sCarry * 100 / tot)
        val hungry = if (samples > 0) hungrySamples * 10 / samples else 0   // домов без еды/воды/тепла в среднем, x10
        val res = StringBuilder()
        val pos = (wd.histPos + Tune.STAT_DAYS - 1) % Tune.STAT_DAYS
        for (id in listOf("logs", "planks", "stone", "bread", "fish", "meat", "apples", "water", "cloth", "ale")) {
            val r = c.resource(id).index
            val made = wd.histProd[pos * c.resources.size + r]; val used = wd.histUse[pos * c.resources.size + r]
            if (made == 0 && used == 0 && wd.totalStock(r) == 0) continue
            res.append(id.take(3)).append(' ').append(wd.totalStock(r)).append(' ').append(made).append('-').append(used).append("  ")
        }
        report.append(String.format("%-4d %-3s %4d %4d %4d %3d %-14s  %-22s %4d %3d %4s  %s%n",
            d, "spsuauwi".substring(wd.season * 2, wd.season * 2 + 2), wd.agentCount, beds(), wd.townHappy, wd.level, "${wd.gold} ${wd.lastTax}/${wd.lastUpkeep}",
            car, workers, builders, "${hungry / 10}.${hungry % 10}", res))
        if (samples > 0) {
            val miss = c.needs.filter { needMiss[it.index] * 10 / samples >= 5 }.joinToString(" ") { it.id + " " + needMiss[it.index] * 10 / samples / 10 + "." + needMiss[it.index] * 10 / samples % 10 }
            if (miss.isNotEmpty()) report.append("     houses missing: ").append(miss).append('\n')
        }
        java.util.Arrays.fill(needMiss, 0)
        if (detailEvery > 0 && d % detailEvery == 0) {
            report.append("     buildings: ")
            for ((id, st) in statusSamples.entries.sortedBy { it.key }) {
                val t = maxOf(1, st.sum())
                report.append(id).append(' ').append(count(id)).append(" [")
                val parts = ArrayList<String>()
                for (k in st.indices) if (st[k] * 100 / t >= 5) parts.add(STATUS[k] + " " + st[k] * 100 / t + "%")
                report.append(parts.joinToString(", ")).append("]  ")
            }
            report.append('\n')
        }
        val carts = cartsNow()
        cartsByDay.add(carts)
        report.append("     tax ${intArrayOf(60, 100, 150)[wd.taxLevel]}%  fame ${wd.prestige} (+${wd.fameDaily()}/d)  crown ${wd.crDone} done/${wd.crFailed} failed" +
            (if (wd.crR >= 0) " (open: ${c.resources[wd.crR].id} x${wd.crN} till d${wd.crEnd}, have ${wd.stockAvailable(wd.crR)})" else "") +
            "  barrows ${stockOf("barrow")} wagons ${stockOf("wagon")}  paved ${pavedCount()}\n")
        lastStatus.clear(); lastStatus.putAll(statusSamples.mapValues { it.value.copyOf() })
        statusSamples.clear()
        popByDay.add(wd.agentCount); happyByDay.add(wd.townHappy)
        sIdle = 0; sToSrc = 0; sCarry = 0; sOther = 0; samples = 0; hungrySamples = 0
    }

    private fun stockOf(id: String): Int { val r = c.resources.indexOfFirst { it.id == id }; return if (r < 0) 0 else wd.totalStock(r) }
    private fun cartsNow() = stockOf("barrow") + stockOf("wagon")
    fun pavedCount(): Int { var n = 0; for (t in wd.tiles) if (t.toInt() == PAVED) n++; return n }
    fun summaryLine(): String = "tax ${wd.taxLevel} fame ${wd.prestige} crown issued $crownIssued done ${wd.crDone} failed ${wd.crFailed} " +
        "barrows ${stockOf("barrow")} wagons ${stockOf("wagon")} paved ${pavedCount()} " +
        "kids ${wd.kidsGrown} skilled ${wd.skilledTotal()} school ${count("school")} post ${count("tradepost")} deliveries ${wd.deliveries} builders ${wd.builderTarget()}"

    // ---------- решения ----------

    private fun count(vararg ids: String): Int {
        var n = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] != B_GONE && wd.def(b).id in ids) n++
        return n
    }

    private fun sitesOf(id: String): Int {
        var n = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_SITE && wd.def(b).id == id) n++
        return n
    }

    private fun sites(): Int {
        var n = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_SITE) n++
        return n
    }

    private fun beds(): Int {
        var n = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_ACTIVE && wd.def(b).isHouse) n += wd.def(b).beds
        return n
    }

    private fun houses() = count("house", "house2", "house3")

    /** Цель задания «накопить золото» (не тратить ниже неё) */
    private fun goldGoal(): Int {
        var g = 0
        for (i in 0 until Tune.MAX_QUESTS) if (wd.qType[i] == town.data.Quest.GOLD) g = maxOf(g, wd.qB[i])
        return g
    }

    /** Запас казны: не меньше двух суток содержания (содержание растёт с городом - иначе здания встанут без оплаты) */
    private fun reserve() = maxOf(GOLD_RESERVE, goldGoal(), wd.upkeepPerDay() * 2)

    /** Налоги в сутки при нынешних жильцах и довольстве (как в статистике игры) */
    private fun estTax(): Int {
        var t = 0L
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_ACTIVE && wd.def(b).isHouse) t += wd.bResidents[b].toLong() * wd.bHappy[b] * wd.def(b).tax
        return (t * Tune.TAX * Tune.TICKS_PER_DAY / (10000L * Tune.BASE_DAY)).toInt()
    }

    /** Новое содержание по силам: налоги его покрывают или в казне запас на десять суток */
    private fun affordUpkeep(extra: Int): Boolean {
        // задание «накопить золото»: держать остаток в сутки, чтобы дойти до цели примерно за четверо суток
        val save = if (goldGoal() > wd.gold) (goldGoal() - wd.gold) / 4 else 0
        return estTax() - wd.upkeepPerDay() - save >= extra || wd.gold >= reserve() + extra * 10
    }

    /** Задания: построить - в начало списка желаний; запасти - лимит мастерских не ниже цели */
    private fun questWishes(): List<String> {
        val list = ArrayList<String>()
        for (i in 0 until Tune.MAX_QUESTS) when (wd.qType[i]) {
            town.data.Quest.BUILD -> c.buildings[wd.qA[i]].id.let { if (count(it) < wd.qB[i]) list.add(it) }
            // «произвести N»: цепочка до товара, но не больше двух мастерских самого товара
            town.data.Quest.MADE -> {
                val p = c.buildings.firstOrNull { it.isProducer && it.output[wd.qA[i]] > 0 && it.buildable }
                if (p != null && count(p.id) + sitesOf(p.id) < 2) chain(wd.qA[i], list, 0)
            }
            town.data.Quest.DEFEND -> if (count("guardtower") < 2 + wd.questValue(i)) list.add("guardtower")
            town.data.Quest.DELIVER -> if (count("tradepost") + sitesOf("tradepost") == 0) list.add("tradepost")
            town.data.Quest.GROWN -> if (count("school") + sitesOf("school") == 0) list.add("school")
            town.data.Quest.STOCK -> {
                val r = wd.qA[i]
                // запас растёт медленно - ещё одна мастерская этого товара
                if (wd.totalStock(r) < wd.qB[i]) chain(r, list, 0)
                for (b in 0 until wd.buildingCount) {
                    if (wd.bState[b] == B_ACTIVE && wd.def(b).output[r] > 0 && wd.bLimit[b] in 0 until wd.qB[i] + 10)
                        wd.submit(Command.LIMIT, b, wd.qB[i] + 10, 0)
                }
            }
        }
        return list
    }

    /** Цепочка до товара r: сначала недостающие звенья сырья, потом ещё одна мастерская самого товара */
    private fun chain(r: Int, list: ArrayList<String>, depth: Int) {
        // из нескольких мастерских товара - открытая без сырья (уголь из копи, а не из брёвен)
        val ps = c.buildings.filter { it.isProducer && it.output[r] > 0 && it.buildable }
        val p = ps.firstOrNull { wd.unlocked(it.index) && it.input.all { n -> n == 0 } } ?: ps.firstOrNull() ?: return
        if (depth < 3) for (i in 0 until c.resources.size) if (p.input[i] > 0) {
            val q = c.buildings.firstOrNull { it.isProducer && it.output[i] > 0 && it.buildable }
            // звена нет - или оно есть, но сырья для него нет совсем: идём по цепочке глубже
            if (q != null && (count(q.id) == 0 || wd.totalStock(i) == 0)) chain(i, list, depth + 1)
        }
        if (!saturated(p.id) && sitesOf(p.id) == 0 && count(p.id) < 1 + wd.agentCount / 8) list.add(p.id)
    }

    private fun decide() {
        val pop = wd.agentCount
        val food = listOf("bread", "fish", "meat", "apples").sumOf { wd.totalStock(c.resource(it).index) }
        if (food < pop / 2 && wd.canCallTrader() && wd.gold >= wd.traderCallPrice() + reserve()) wd.submit(Command.CALL_TRADER, 0, 0, 0)
        if (wd.traderHere) trade(pop)
        // задание «довольство N»: не хватает немного - праздник в ратуше
        for (i in 0 until Tune.MAX_QUESTS) if (wd.qType[i] == town.data.Quest.HAPPY && wd.townHappy < wd.qB[i] &&
            wd.townHappy + Tune.FESTIVAL_HAPPY >= wd.qB[i] && wd.canFestival()) wd.submit(Command.FESTIVAL, 0, 0, 0)
        if (!off("crown")) crownDeliver()
        if (!off("nb")) contracts()
        if (!off("tax")) tax()
        setLimits()
        clearDepleted()
        pruneIdle()
        upgrades()
        if (!off("prio")) priorities()
        if (!off("pave")) paving()
        val maxSites = 2 + pop / 30
        if (sites() >= maxSites) return
        roads()
        if (foresters()) return
        // мастерская стоит «далеко носить» - склад рядом с ней
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] == B_ACTIVE && wd.bStatus[b] == BS_FAR && wd.def(b).isProducer && !storeNear(b)) {
                if (tryPlace("store", b)) return
            }
        }
        // угрозы: караул у складов, ратуши и скота (туда идут разбойники и волки)
        if (wd.threatsOn && wd.level >= Tune.RAID_LEVEL) for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE) continue
            val d = wd.def(b)
            if (!(d.isStore || d.id == "pasture" || d.id == "piggery")) continue
            if (hasNear(b, "guardtower", 6)) continue
            if (tryPlace("guardtower", b)) return
            break
        }
        val quest = questWishes() + crownWishes()
        for (id in quest) if (tryPlace(id, anchor(id))) return
        // в сценарии стройка для задания ждёт материалов - копим их, кроме жизненно нужного
        // много домов без рынка или церкви - сначала услуги (иначе довольство не даёт расти)
        if ((wd.scenario < 0 || goldGoal() == 0) && uncovered() >= 4 && coverServices()) return
        if (!off("school") && school()) return
        val all = wishes(pop)
        // такие уже стоят полными (готовое некуда девать) - ещё одна не поможет, только содержание
        for (id in if (quest.isEmpty() || wd.scenario < 0) all else all.filter { it in VITAL }) if (!saturated(id) && tryPlace(id, anchor(id))) return
        if (coverServices()) return
        decorate()
    }

    /**
     * Улучшения: готовые дома - как только хватает золота с запасом; мастерские, которые работают без простоя, -
     * когда золота заметно больше запаса (по одной за раз)
     */
    private fun upgrades() {
        for (b in 0 until wd.buildingCount) {
            if (!wd.houseReady(b) || wd.upgradeBlock(b) != null) continue
            // дом - вложение (больше кроватей и налога): небольшой запас, без оглядки на цель «накопить золото»
            if (wd.gold >= wd.upgradeGold(b) + HOUSE_RESERVE) wd.submit(Command.UPGRADE, b, 0, 0)
        }
        if (wd.gold < reserve() + 250) return
        for (b in 0 until wd.buildingCount) if (wd.bUpg[b] == 1 && !wd.def(b).isHouse) return
        for (b in 0 until wd.buildingCount) {
            if (!wd.def(b).isProducer || wd.bStatus[b] != BS_OK || wd.upgradeBlock(b) != null) continue
            // уровень - +50% содержания: только если налоги это покрывают
            if (!affordUpkeep(wd.upkeepOf(b) * Tune.LEVEL_UPKEEP / 100 + 5)) continue
            wd.submit(Command.UPGRADE, b, 0, 0)
            return
        }
    }

    /** Добытчик, у которого вокруг ничего не осталось (лес вырублен, скала выбрана), - снести: рабочие нужнее в другом месте */
    private fun clearDepleted() {
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE || wd.def(b).gatherCode == G_NONE || wd.def(b).gatherCode == G_PLANT) continue
            // дровосека у лесника не сносим - лес отрастёт
            if (wd.bNear[b] == 0 && !(wd.def(b).id == "woodcutter" && hasNear(b, "forester", 8))) wd.submit(Command.DEMOLISH, b, 0, 0)
        }
    }

    /**
     * Лишние мастерские и добытчики, что двое суток стоят без дела, - на снос: содержание и рабочие нужнее
     * другим (иначе налоги и содержание сходятся в ноль и на топливо к зиме золота нет). Последнее здание вида
     * и дровосек у лесника остаются; за решение - не больше одного
     */
    private fun pruneIdle() {
        for (b in 0 until minOf(wd.buildingCount, idleRun.size)) {
            if (wd.bState[b] != B_ACTIVE || idleRun[b] < IDLE_PRUNE) continue
            val def = wd.def(b)
            if (def.id == "woodcutter" && hasNear(b, "forester", 8)) continue
            if (def.id in KEEP_BUILT || count(def.id) < 2) continue
            idleRun[b] = 0
            wd.submit(Command.DEMOLISH, b, 0, 0)
            return
        }
    }

    /** Половина дровосеков рубит редкий лес (или их нет совсем) - новые дровосеки не помогут, топливо - углём */
    private fun thinForest(): Boolean {
        var n = 0; var thin = 0
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE || wd.def(b).id != "woodcutter") continue
            n++
            if (wd.bNear[b] < THIN_NEAR / 2) thin++
        }
        return thin * 2 >= n
    }

    /** Лесник у дровосека, когда деревьев вокруг поубавилось */
    private fun foresters(): Boolean {
        if (count("forester") >= count("woodcutter")) return false
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE || wd.def(b).id != "woodcutter" || wd.bNear[b] >= 16) continue
            if (!hasNear(b, "forester", 8) && tryPlace("forester", b)) return true
        }
        return false
    }

    private fun hasNear(b: Int, id: String, d: Int): Boolean {
        for (f in 0 until wd.buildingCount) {
            if (wd.bState[f] == B_GONE || wd.def(f).id != id) continue
            if (Math.abs(wd.bX[f] - wd.bX[b]) + Math.abs(wd.bY[f] - wd.bY[b]) <= d) return true
        }
        return false
    }

    /** Лишнее золото - в украшения у домов без них: лучшее из открытых, с запасом в казне */
    private fun decorate() {
        val id = listOf("park", "statue", "fountain", "flowerbed").firstOrNull {
            val d = c.building(it); wd.unlocked(d.index) && wd.gold >= d.gold + reserve()
        } ?: return
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE || !wd.def(b).isHouse || wd.bResidents[b] == 0 || coveredBy(b, "decor")) continue
            if (tryPlace(id, b)) return
        }
    }

    /** Лимиты, как ставит игрок: досок и камня не больше 80, дров и угля - по числу жителей */
    private fun setLimits() {
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE) continue
            // тачки и телеги: парк по числу жителей (поправляется по ходу)
            val cart = when (wd.def(b).id) { "carpenter" -> maxOf(2, wd.agentCount / 4); "wheelwright" -> maxOf(1, wd.agentCount / 8); else -> -1 }
            if (cart > 0) { if (wd.bLimit[b] != cart) wd.submit(Command.LIMIT, b, cart, 0); continue }
            // топливо: запас на зиму растёт с городом (около 8 на жителя), лимит поправляется по ходу
            if (wd.def(b).id == "woodcutter" || wd.def(b).id == "coalmine") {
                // уголь греет вдвое дольше - его вдвое меньше
                val fuel = maxOf(120, wd.agentCount * 8 / c.resources[wd.def(b).output.indexOfFirst { it > 0 }].lasts) / 20 * 20
                if (wd.bLimit[b] != fuel) wd.submit(Command.LIMIT, b, fuel, 0)
                continue
            }
            // еда без порчи копится тысячами (охотники при полных складах) - запас около 6 на жителя (хватит на зиму), рабочие носят
            if (wd.def(b).id == "hunter" || wd.def(b).id == "fisher" || wd.def(b).id == "orchard") {
                val food = maxOf(60, wd.agentCount * 6) / 20 * 20
                if (wd.bLimit[b] != food) wd.submit(Command.LIMIT, b, food, 0)
                continue
            }
            if (wd.bLimit[b] >= 0) continue
            val lim = when (wd.def(b).id) { "sawmill" -> 80; "quarry" -> 80; else -> -1 }
            if (lim > 0) wd.submit(Command.LIMIT, b, lim, 0)
        }
    }

    /** Мастерские этого типа вчера часто стояли полными (носильщики не успевают) - ещё одна не поможет */
    private fun saturated(id: String): Boolean {
        val st = lastStatus[id] ?: return false
        val t = st.sum()
        return t > 0 && (st[BS_OUTPUT_FULL] + st[BS_FAR] + st[BS_LIMIT]) * 100 / t >= 30
    }

    /** У торговца - еда, если её мало, и камень/доски для стройки, если кончились */
    private fun trade(pop: Int) {
        if (wd.traderShip) sellToShip()
        val food = listOf("bread", "fish", "meat", "apples").sumOf { wd.totalStock(c.resource(it).index) }
        if (food < pop) for (id in listOf("fish", "bread", "meat", "apples")) {
            val r = c.resource(id).index
            if (wd.traderStock[r] > 0 && wd.gold > 60) wd.submit(Command.TRADE, r, minOf(pop - food, wd.traderStock[r]), 0)
        }
        // задание «запасти» - докупить недостающее
        for (i in 0 until Tune.MAX_QUESTS) if (wd.qType[i] == town.data.Quest.STOCK) {
            val r = wd.qA[i]
            val need = wd.qB[i] - wd.totalStock(r)
            if (need > 0 && wd.traderStock[r] > 0) wd.submit(Command.TRADE, r, minOf(need, wd.traderStock[r], (wd.gold - goldGoal()) / wd.buyPrice(r)), 0)
        }
        for (id in listOf("stone", "planks")) {
            val r = c.resource(id).index
            if (wd.totalStock(r) < 10 && wd.gold > 150) wd.submit(Command.TRADE, r, 10, 0)
        }
    }

    /** Сколько ресурса r здание даёт за Tune.BASE_DAY тиков (под них числа спроса ниже; рабочие днём, с поправкой на ходьбу) */
    private fun rate(def: town.data.BuildingDef, r: Int) =
        if (def.time == 0) 0 else def.output[r] * def.workers * Tune.BASE_DAY * 3 / 4 * 6 / 10 / def.time

    /** То же для стоящего здания: добытчик без ресурса не даёт ничего, ферма - по плодородию */
    private fun rateOf(b: Int, r: Int): Int {
        val def = wd.def(b)
        if (def.gatherCode != G_NONE && wd.bState[b] == B_ACTIVE && wd.bNear[b] == 0) return 0
        // поредевший лес (выбранная скала) - дальше ходить и чаще ждать: выработка меньше
        val thin = if (def.gatherCode != G_NONE && def.gatherCode != G_PLANT && wd.bState[b] == B_ACTIVE) minOf(wd.bNear[b], THIN_NEAR) else THIN_NEAR
        return rate(def, r) * wd.bYield[b] / 100 * thin / THIN_NEAR
    }

    private fun inputRate(def: town.data.BuildingDef, r: Int) =
        if (def.time == 0) 0 else def.input[r] * def.workers * Tune.BASE_DAY * 3 / 4 * 6 / 10 / def.time

    /** Список желаемых построек по порядку важности: спрос по ресурсам против того, что дают здания (и стройки) */
    private fun wishes(pop: Int): List<String> {
        val R = c.resources.size
        val supply = IntArray(R); val demand = IntArray(R)
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] == B_GONE) continue
            val def = wd.def(b)
            for (r in 0 until R) { supply[r] += rateOf(b, r); demand[r] += inputRate(def, r) }
        }
        fun res(id: String) = c.resource(id).index
        val people = pop + 6 + wd.kidsTotal() * Tune.KID_EAT / 100   // с запасом на рост; дети едят меньше
        val food = listOf("bread", "fish", "meat", "apples").map { res(it) }
        val foodSupply = food.sumOf { supply[it] }
        val foodDemand = people * 50 / 100 + 1
        demand[res("water")] += people * 60 / 100
        val coldSoon = wd.season == AUTUMN || wd.season == WINTER || (wd.season == SUMMER && wd.tick / day % Tune.SEASON_DAYS >= Tune.SEASON_DAYS / 2)
        demand[res("logs")] += 3 + if (coldSoon) people * 60 / 100 * (if (wd.harshWinter) 2 else 1) else 0
        demand[res("planks")] += 6 + pop / 8
        demand[res("stone")] += 4 + pop / 12
        var tier2 = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_ACTIVE && wd.def(b).isHouse && wd.def(b).tier >= 2) tier2 += wd.bResidents[b]
        demand[res("cloth")] += tier2 * 15 * 15 / 1000
        // уголь сверх нужд плавилен и кузниц греет дома наравне с дровами
        val coalFree = supply[res("coal")] - demand[res("coal")]
        if (coalFree > 0) supply[res("logs")] += coalFree * c.resource("coal").lasts
        fun short(id: String) = supply[res(id)] * 100 < demand[res(id)] * 115

        val h = houses()
        val freeBeds = beds() + bedsInSites() - pop
        val list = ArrayList<String>()
        if (count("woodcutter") == 0) list.add("woodcutter")
        if (count("well") == 0) list.add("well")
        // без лесопилки и каменоломни материалы кончатся, а потом их не на что построить
        if (count("sawmill") == 0) list.add("sawmill")
        if (count("quarry") == 0) list.add("quarry")
        // перед зимой и зимой новых жителей не зовём без запаса еды на несколько суток (фермы зимой стоят)
        val foodStock = food.sumOf { wd.totalStock(it) }
        val winterShort = (wd.season == AUTUMN || wd.season == WINTER) && foodStock < people * 50 / 100 * Tune.TICKS_PER_DAY * 3 / Tune.BASE_DAY
        if (freeBeds < 3 && wd.townHappy >= 50 && !winterShort) list.add("house")
        // еды на складах на неделю - новые добытчики еды не нужны (иначе охотники множатся при полных складах)
        val foodPlenty = foodStock >= people * 50 / 100 * Tune.TICKS_PER_DAY * 7 / Tune.BASE_DAY
        if (foodSupply * 100 < foodDemand * 115 && !foodPlenty) {
            // хлеб - когда открыт (цепочку дотянут спрос на зерно и муку), иначе рыба, дичь, яблоки
            // осенью и зимой новая ферма не успеет: сначала рыба и дичь
            val growing = wd.season == SPRING || wd.season == SUMMER
            if (!growing) { list.add("fisher"); list.add("hunter") }
            if (wd.unlocked(c.building("bakery").index) && (growing || count("farm") > 0)) list.add(if (count("farm") == 0) "farm" else "bakery")
            list.add("fisher"); list.add("hunter"); list.add("orchard")
        }
        if (short("water")) list.add("well")
        if (short("grain")) list.add("farm")
        if (short("flour")) list.add("mill")
        // топливо: в редком лесу - угольная копь; дровосека поставить негде (лес вырублен) - тоже она, следом по списку
        // копь в обход проверки золота - только когда топлива на складах меньше ~4 на жителя (иначе бот ставит
        // копи под полный зимний расход, не глядя на запас, и содержание съедает город)
        val fuelStock = wd.totalStock(res("logs")) + wd.totalStock(res("coal")) * c.resource("coal").lasts
        fuelShort = short("logs") && fuelStock < pop * 4
        if (short("logs")) {
            val coal = wd.unlocked(c.building("coalmine").index)
            if (coal && thinForest()) list.add("coalmine")
            else { list.add("woodcutter"); if (coal) list.add("coalmine") }
        }
        if (short("planks")) list.add("sawmill")
        if (short("stone")) list.add("quarry")
        if (count("store") < h / 10 || storesFull()) list.add("store")
        if (tier2 > 0) {
            if (short("cloth")) list.add("weaver")
            if (short("wool")) list.add("pasture")
        }
        if (count("house3") > 0) {
            if (short("ale")) list.add("brewery")
            if (short("grain")) list.add("farm")
        }
        // новые потребности домов (копчёности, платье, свечи) и инструменты - цепочкой до товара,
        // но только когда еды и воды хватает (иначе новые мастерские отнимут материалы, рабочих и золото у главного)
        val basicsOk = foodSupply * 100 >= foodDemand * 115 && !short("water") && wd.gold >= reserve()
        if (basicsOk) for (n in c.needs) {
            if (n.goods.isEmpty() || n.level > wd.level || n.vital) continue
            var res = 0
            for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_ACTIVE && wd.def(b).isHouse && wd.def(b).tier >= n.tier) res += wd.bResidents[b]
            if (res == 0) continue
            val g = n.goods.last()
            demand[g] += res * n.use * 15 / 1000
            if (supply[g] * 100 < demand[g] * 115) chain(g, list, 0)
        }
        val tools = res("tools")
        if (basicsOk && wd.level >= 2 && wd.totalStock(tools) < 6 && supply[tools] == 0) chain(tools, list, 0)
        // угрозы: караул у складов и ратуши
        if (wd.threatsOn && wd.level >= Tune.RAID_LEVEL && count("guardtower") < 1 + houses() / 12) list.add("guardtower")
        // тачки и телеги: когда город вырос и досок с запасом
        if (basicsOk && pop >= 25 && wd.totalStock(res("planks")) >= 15 && count("carpenter") + sitesOf("carpenter") == 0) list.add(0, "carpenter")
        if (basicsOk && pop >= 60 && wd.totalStock(res("iron")) > 0 && wd.totalStock(res("leather")) > 0 &&
            wd.totalStock(res("planks")) >= 15 && count("wheelwright") + sitesOf("wheelwright") == 0 && count("carpenter") > 0) list.add("wheelwright")
        // пристань: одна, если есть место у воды
        if (pop >= 60 && count("harbor") + sitesOf("harbor") == 0 && wd.gold >= reserve() + 150 && harborCanBe()) list.add("harbor")
        // торговый пост: когда у соседей есть выгодное предложение на наши излишки
        if (!off("nb") && basicsOk && pop >= 40 && count("tradepost") + sitesOf("tradepost") == 0 &&
            wd.gold >= c.building("tradepost").gold + reserve() && goodOffer() >= 0) list.add(0, "tradepost")
        // слава: ярмарка, когда открыта и золота с запасом; гильдейский дом - как чудо, когда открыт и копится золото
        val fair = c.building("fairground")
        if (wd.unlocked(fair.index) && count("fairground") + sitesOf("fairground") == 0 && wd.gold >= fair.gold + reserve()) list.add("fairground")
        val guild = c.building("guildhall")
        if (wd.unlocked(guild.index) && !wd.wonderTaken(guild.index) && wd.gold >= guild.gold + reserve() && count("guildhall") + sitesOf("guildhall") == 0) list.add("guildhall")
        return list.filter { id -> !(c.building(id).isProducer && saturated(id)) && !(off("bld") && id in NEW) }
    }

    /** Склады заполнены на 80% и новый не строится - пора ставить ещё один */
    private fun storesFull(): Boolean {
        if (sitesOf("store") > 0) return false
        var cap = 0; var used = 0
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE || !wd.def(b).isStore) continue
            cap += wd.capacityOf(b)
            for (r in 0 until c.resources.size) used += wd.inv[b * c.resources.size + r]
        }
        return cap > 0 && used * 100 >= cap * 80
    }

    private fun bedsInSites(): Int {
        var n = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_SITE && wd.def(b).isHouse) n += wd.def(b).beds
        return n
    }

    /** Услуги - туда, где есть дома без них (по радиусу, как в игре; здание считается и стройкой) */
    private fun coverServices(): Boolean {
        for (id in listOf("market", "church", "healer", "firewatch", "tavern")) {
            val def = c.building(id)
            if (!wd.unlocked(def.index) || !wd.canAfford(def.index)) continue
            if (id == "tavern" && count("house3") == 0) continue
            if (id == "firewatch" && houses() < 6) continue
            for (b in 0 until wd.buildingCount) {
                if (wd.bState[b] != B_ACTIVE || !wd.def(b).isHouse || wd.bResidents[b] == 0) continue
                if (coveredBy(b, def.serves!!)) continue
                if (tryPlace(id, b)) return true
                break
            }
        }
        return false
    }

    /** Сколько жилых домов без открытой услуги (рынок, церковь) рядом */
    private fun uncovered(): Int {
        var n = 0
        for (id in listOf("market", "church")) {
            val def = c.building(id)
            if (!wd.unlocked(def.index)) continue
            for (b in 0 until wd.buildingCount) {
                if (wd.bState[b] == B_ACTIVE && wd.def(b).isHouse && wd.bResidents[b] > 0 && !coveredBy(b, def.serves!!)) n++
            }
        }
        return n
    }

    private fun coveredBy(h: Int, service: String): Boolean {
        val hs = wd.def(h).size
        for (o in 0 until wd.buildingCount) {
            val d = wd.def(o)
            if (wd.bState[o] == B_GONE || d.serves != service) continue
            val dx = Math.abs(wd.bX[o] * 2 + d.size - wd.bX[h] * 2 - hs); val dy = Math.abs(wd.bY[o] * 2 + d.size - wd.bY[h] * 2 - hs)
            if (maxOf(dx, dy) <= d.radius * 2) return true
        }
        return false
    }

    /** Где ставить: дом - у мастерской, до которой рабочим дальше всего ходить; остальное - у ратуши */
    private fun anchor(id: String): Int {
        if (id != "house") return wd.hall
        var best = wd.hall; var bestD = 8
        for (a in 0 until wd.agentCount) {
            if (wd.job[a] != JOB_WORKER || wd.jobB[a] < 0 || wd.home[a] < 0) continue
            val j = wd.jobB[a]; val h = wd.home[a]
            val d = maxOf(Math.abs(wd.bX[j] - wd.bX[h]), Math.abs(wd.bY[j] - wd.bY[h]))
            if (d > bestD) { bestD = d; best = j }
        }
        return best
    }

    private val roadDone = HashSet<Int>()

    /** Дорога от ратуши к дальним мастерским (как делает игрок): камень есть - прокладываем по пути жителей */
    private fun roads() {
        val stone = c.resource("stone").index
        for (b in 0 until wd.buildingCount) {
            if (b in roadDone || wd.bState[b] != B_ACTIVE || !wd.def(b).isProducer) continue
            if (Math.abs(wd.bX[b] - wd.bX[wd.hall]) + Math.abs(wd.bY[b] - wd.bY[wd.hall]) < ROAD_FROM) continue
            val path = wd.pf.find(wd.entrance(wd.hall), wd.entrance(b)) ?: continue
            if (wd.stockAvailable(stone) < path.size + 10) return
            for (cell in path) if (wd.tiles[cell] == GRASS) wd.submit(Command.ROAD, cell % wd.w, cell / wd.w, 1)
            for (cell in path) if (cell !in roadCells) roadCells.add(cell)
            roadDone.add(b)
            return
        }
    }

    /**
     * Расширить границу: склад внутри границы там, где за ней больше всего нужного добытчику ресурса
     * (в радиусе, который склад добавит к границе)
     */
    private fun expandFor(gdef: town.data.BuildingDef): Boolean {
        val store = c.building("store")
        if (!wd.canAfford(store.index)) return false
        if (sitesOf("store") > 0) return false
        val reach = store.border + 2
        var best = -1; var bestScore = 0
        for (y in 1 until wd.h step 2) for (x in 0 until wd.w step 2) {
            if (!wd.canBuild(store.index, x, y)) continue
            var score = 0
            for (yy in y - reach..y + reach) for (xx in x - reach..x + reach) {
                if (xx !in 0 until wd.w || yy !in 0 until wd.h) continue
                val i = yy * wd.w + xx
                if (wd.border[i] || (xx - x) * (xx - x) + (yy - y) * (yy - y) > reach * reach) continue
                if (if (gdef.gatherCode != G_NONE) wd.hasResource(i, gdef.gatherCode) else wd.tiles[i] == GRASS) score++
            }
            if (score > bestScore) { bestScore = score; best = y * wd.w + x }
        }
        if (best < 0 || bestScore < (if (gdef.gatherCode != G_NONE) gdef.needMin * 2 else 20)) return false
        wd.submit(Command.PLACE, store.index, best % wd.w, best / wd.w)
        return true
    }

    private fun storeNear(b: Int): Boolean {
        for (s in 0 until wd.buildingCount) {
            if (wd.bState[s] == B_GONE || !wd.def(s).isStore) continue
            if (Math.abs(wd.bX[s] - wd.bX[b]) + Math.abs(wd.bY[s] - wd.bY[b]) <= 10) return true
        }
        return false
    }

    /** Здание, которому не нашлось места, - до какого тика его не пытаться ставить (перебор всей карты дорог) */
    private val noSpotUntil = HashMap<String, Int>()

    private fun tryPlace(id: String, near: Int = wd.hall): Boolean {
        val def = c.building(id)
        if (!wd.unlocked(def.index) || !wd.canAfford(def.index) || wd.wonderTaken(def.index)) return false
        if ((noSpotUntil[id] ?: 0) > wd.tick) return false
        // новое содержание - только если налоги его покрывают (кроме жизненно нужного), иначе здания встанут без оплаты
        // копь - как жизненно нужное, только когда топлива не хватает (иначе цепочка инструментов плодит копи)
        if (def.upkeep > 0 && id !in VITAL && !(id == "coalmine" && fuelShort) && !affordUpkeep(wd.upkeepFor(def) + 5)) return false
        val spot = spot(def.index, near)
        if (spot < 0) {
            noSpotUntil[id] = wd.tick + Tune.TICKS_PER_DAY
            // негде встать внутри границы - склад на краю: добытчику - в сторону ресурса, остальным - к свободному лугу
            if (id != "store" && id != "harbor") return expandFor(def)
            return false
        }
        wd.submit(Command.PLACE, def.index, spot % wd.w, spot / wd.w)
        return true
    }

    /**
     * Ближайшее к ратуше место; для добытчиков - где ресурса вдвое больше минимума;
     * для фермы, сада, колодца - лучшая земля среди мест ещё на 6 клеток дальше первого найденного
     */
    private fun spot(defIndex: Int, near: Int): Int {
        val def = c.buildings[defIndex]
        // добытчик: лучше с двойным запасом ресурса, но если такого места нет - хватит и минимума
        if (def.needTile != null || def.gatherCode != G_NONE) {
            val rich = spotWith(defIndex, near, def.needMin * 2)
            return if (rich >= 0) rich else spotWith(defIndex, near, def.needMin)
        }
        return spotWith(defIndex, near, 0)
    }

    private fun spotWith(defIndex: Int, near: Int, minRes: Int): Int {
        val def = c.buildings[defIndex]
        val hx = wd.bX[near]; val hy = wd.bY[near]
        var best = -1; var bestYield = -1; var stopAt = 60
        for (d in 3 until 60) {
            if (d > stopAt) break
            for (dx in -d..d) for (s in intArrayOf(-1, 1)) {
                val dy = (d - Math.abs(dx)) * s
                val x = hx + dx; val y = hy + dy
                if (x !in 0 until wd.w || y !in 1 until wd.h || !wd.canBuild(defIndex, x, y)) continue
                if ((def.needTile != null || def.gatherCode != G_NONE) && wd.nearIfPlaced(defIndex, x, y) < minRes) continue
                if (def.soil == null) return y * wd.w + x
                val yl = wd.yieldFor(defIndex, x, y)
                if (yl > bestYield) { bestYield = yl; best = y * wd.w + x }
                if (stopAt == 60) stopAt = d + 6
            }
        }
        return best
    }

    // ---------- версия 0.9 ----------

    /** Налог: высокий, пока довольство высокое, а золота мало; низкий, когда довольство упало и золота с запасом; не чаще раза в сутки */
    private val offSet = (System.getenv("BOT_OFF") ?: "").split(',').toSet()
    private fun off(k: String) = k in offSet

    private fun tax() {
        if (wd.day - lastTaxDay < 1) return
        val h = wd.townHappy
        // задание «накопить золото», а копится медленно: высокий налог, пока довольство терпит
        val goldQuest = (0 until Tune.MAX_QUESTS).any { wd.qType[it] == town.data.Quest.GOLD && wd.gold < wd.qB[it] }
        val want = when {
            goldQuest && h >= 50 -> 2
            h >= 78 && wd.gold < reserve() -> 2
            h < 55 && wd.gold >= reserve() * 2 -> 0
            else -> 1
        }
        lastTaxDay = wd.day
        if (want != wd.taxLevel) wd.submit(Command.TAX, want, 0, 0)
    }

    /** Приоритет «срочно»: площадки домов без свободных кроватей и площадки мастерских еды при нехватке еды */
    private fun priorities() {
        val pop = wd.agentCount
        val foodIds = listOf("bread", "fish", "meat", "apples")
        val foodRes = foodIds.map { c.resource(it).index }
        val foodLow = foodRes.sumOf { wd.totalStock(it) } < pop
        val noBeds = beds() - pop <= 0
        var urgent = 0
        for (b in 0 until wd.buildingCount) if (wd.bState[b] == B_SITE && wd.bPrio[b] == 1) urgent++
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_SITE || wd.bPrio[b] == 1 || urgent >= 2) continue
            val d = wd.def(b)
            val foodShop = d.isProducer && foodRes.any { d.output[it] > 0 }
            if ((d.isHouse && noBeds) || (foodShop && foodLow)) { wd.submit(Command.PRIORITY, b, 1, 0); urgent++ }
        }
    }

    /** Мощёная дорога: уже проложенные дороги (от ратуши), когда камня много */
    private fun paving() {
        if (wd.level < Tune.PAVE_LEVEL) return
        val stone = c.resource("stone").index
        var n = 0
        for (cell in roadCells) {
            if (wd.stockAvailable(stone) <= 60 || n >= 12) return
            if (wd.tiles[cell] != ROAD) continue
            val x = cell % wd.w; val y = cell / wd.w
            if (!wd.canPave(x, y)) continue
            wd.submit(Command.ROAD, x, y, 2)
            n++
        }
    }

    /** Заказ короны: отдать, как только можно */
    private fun crownDeliver() {
        if (wd.crR >= 0 && wd.canDeliverCrown()) wd.submit(Command.CROWN, 0, 0, 0)
    }

    /** Пока заказ висит - товар заказа в желания, лимиты его мастерских выше заказа */
    private fun crownWishes(): List<String> {
        val r = wd.crR
        if (r < 0) return emptyList()
        val list = ArrayList<String>()
        if (wd.stockAvailable(r) < wd.crN) chain(r, list, 0)
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] == B_ACTIVE && wd.def(b).output[r] > 0 && wd.bLimit[b] in 0 until wd.crN + 10)
                wd.submit(Command.LIMIT, b, wd.crN + 10, 0)
        }
        return list
    }

    // ---------- версия 0.11-0.12 ----------

    /** Школа у домов с детьми, куда ни одна школа не достаёт */
    private fun school(): Boolean {
        if (wd.kidsTotal() < 2 || sitesOf("school") > 0 || count("school") >= 1 + houses() / 15) return false
        val def = c.building("school")
        if (!wd.unlocked(def.index) || !wd.canAfford(def.index)) return false
        for (b in 0 until wd.buildingCount) {
            if (wd.bState[b] != B_ACTIVE || !wd.def(b).isHouse || wd.kids(b) == 0 || coveredBy(b, "school")) continue
            return tryPlace("school", b)
        }
        return false
    }

    private val noTrade = setOf("water", "logs", "bread", "fish", "meat", "apples", "tools", "barrow", "wagon")

    /** Можно ли отдавать товар r по n в неделю: не еда и не дрова, делаем за неделю не меньше n и есть запас */
    private fun canGive(r: Int, n: Int): Boolean {
        if (c.resources[r].id in noTrade || questNeeds(r) || r == wd.crR) return false
        var made = 0
        for (d in 1..wd.histDays) made += wd.produced(r, d)
        val week = made * 7 / maxOf(1, wd.histDays)
        // задание «поставки»: хватит и того, что делаем понемногу
        val need = (0 until Tune.MAX_QUESTS).any { wd.qType[it] == town.data.Quest.DELIVER }
        if (need && c.resources[r].id != "planks" && c.resources[r].id != "stone") return week >= n / 2 || wd.totalStock(r) >= n
        return week >= n * 3 / 2 && wd.totalStock(r) >= n / 2
    }

    /** Лучшее предложение соседей (сосед * 2 + k) из тех, что городу по силам, или -1; золото - лучше */
    private fun goodOffer(): Int {
        var best = -1; var bestV = 0
        for (i in 0 until wd.nbCount) {
            if (wd.ctGiveR[i] >= 0) continue
            for (k in 0..1) {
                val o = i * 2 + k
                val r = wd.ofGiveR[o]
                if (r < 0 || !canGive(r, wd.ofGiveN[o])) continue
                val v = if (wd.ofGetR[o] < 0) wd.ofGetN[o] * 12 / 10 else wd.ofGetN[o] * c.resources[wd.ofGetR[o]].price
                if (v > bestV) { bestV = v; best = o }
            }
        }
        return best
    }

    /** Договоры: заключить выгодный, если есть пост; сорванный и без товара - расторгнуть */
    private fun contracts() {
        for (i in 0 until wd.nbCount) {
            val r = wd.ctGiveR[i]
            if (r >= 0 && wd.ctFails[i] > 0 && wd.totalStock(r) < wd.ctGiveN[i]) wd.submit(Command.CONTRACT, i, -1, 0)
        }
        if (!wd.hasTradePost() || wd.contracts() >= Tune.CONTRACTS_MAX) return
        val o = goodOffer()
        if (o >= 0 && wd.contractBlock(o / 2, o % 2) == null) wd.submit(Command.CONTRACT, o / 2, o % 2, 0)
    }

    /** Есть ли у воды место под пристань (перебор не чаще раза в сутки) */
    private fun harborCanBe(): Boolean {
        val def = c.building("harbor")
        if (!wd.unlocked(def.index) || !wd.canAfford(def.index)) return false
        return (noSpotUntil["harbor"] ?: 0) <= wd.tick
    }

    /** Корабль в гавани: излишки товаров, которые ему особенно нужны, - продать (с запасом на нужды города) */
    private fun sellToShip() {
        for (r in 0 until c.resources.size) {
            if (!wd.shipWanted(r) || c.resources[r].price == 0) continue
            val id = c.resources[r].id
            if (id in KEEP || r == wd.crR) continue
            if (questNeeds(r)) continue
            val keep = maxOf(30, wd.agentCount / 2)
            val spare = wd.stockAvailable(r) - keep
            if (spare > 0) wd.submit(Command.TRADE, r, -spare, 0)
        }
    }

    private fun questNeeds(r: Int): Boolean {
        for (i in 0 until Tune.MAX_QUESTS) if ((wd.qType[i] == town.data.Quest.STOCK || wd.qType[i] == town.data.Quest.MADE) && wd.qA[i] == r) return true
        return false
    }

    companion object {
        const val DECIDE = 100
        const val SAMPLE = 50
        const val ROAD_FROM = 16
        const val GOLD_RESERVE = 150
        const val HOUSE_RESERVE = 60
        /** Ресурсных клеток у добытчика, с которых он работает в полную силу (меньше - выработка в доле) */
        const val THIN_NEAR = 12
        val NEW = setOf("carpenter", "wheelwright", "harbor", "fairground", "guildhall")
        val KEEP = setOf("logs", "planks", "stone", "water", "bread", "fish", "meat", "apples", "grain", "flour", "tools", "firewood")
        val VITAL = setOf("woodcutter", "well", "fisher", "hunter", "quarry", "sawmill", "forester", "house", "guardtower")
        /** Простой, при котором лишнее здание сносится: выборок подряд (днём SAMPLE тиков; ~2 суток) */
        const val IDLE_PRUNE = 96
        /** Не сносятся за простой: тачки (лимит по жителям) и цепочка хлеба (стоит после зимы, пока нет зерна) */
        val KEEP_BUILT = setOf("carpenter", "wheelwright", "farm", "mill", "bakery")
        val STATUS = arrayOf("ok", "site", "noWorkers", "noRes", "full", "noInput", "walk", "season", "limit", "far", "fire", "unpaid", "flood", "?")
    }
}
