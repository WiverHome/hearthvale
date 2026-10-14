package town.sim

/*
 * Жизнь города: потребности домов и довольство, налоги, торговец, сезоны и уровни города,
 * улучшение домов (полный и довольный дом сам заказывает материалы и вырастает), бедствия (пожар, болезнь,
 * неурожай, суровая зима), статистика за сутки и лента событий.
 * Товары в дома носят носильщики (заявки в Citizens.kt), здесь дома их тратят. Услуги (рынок, церковь)
 * действуют на дома в радиусе, пока в здании есть работник. Довольство дома плавно идёт к сумме весов
 * удовлетворённых потребностей; от него зависят налоги, приход новых жителей и уход недовольных.
 */

/** Расход товаров домами, довольство домов и города, рост уровня, работа услуг, автопродажа */
internal fun World.updateNeeds() {
    val needs = content.needs
    val unit = 100 * Tune.BASE_DAY
    val hasCathedral = hasWonder("cathedral")
    var sumHappy = 0
    var sumRes = 0
    for (b in 0 until buildingCount) {
        // пожар и болезнь идут своим чередом у любых зданий
        if (bFire[b] > 0) {
            bFire[b] = maxOf(0, bFire[b] - Tune.NEEDS_INTERVAL)
            if (bFire[b] == 0) fireOut(b)
        }
        if (bSick[b] > 0) bSick[b] = maxOf(0, bSick[b] - Tune.NEEDS_INTERVAL)
        if (bState[b] != B_ACTIVE) continue
        val def = def(b)
        if (def.isService) {
            serviceWork(b)
            continue
        }
        if (!def.isHouse) continue
        val res = bResidents[b]
        // едоки в сотых: дети едят KID_EAT процентов взрослого
        val eaters = res * 100 + kids(b) * Tune.KID_EAT
        var ok = 0
        var target = 0
        var vitalMissing = false
        var vitalMask = 0   // каких жизненных потребностей нет - для текста события
        // веса доступных дому потребностей (по уровню города и виду дома) и выполненных из них
        var wAll = 0
        var wSat = 0
        for (n in needs) {
            val sat = when {
                n.level > level || n.tier > def.tier -> true
                n.service != null -> served(b, n.service)
                else -> {
                    // суровая зима: дров вдвое больше
                    val m = n.season[season] * (if (harshWinter && season == WINTER && n.id == "heat") 2 else 1)
                    if (m == 0) true
                    else {
                        val k = b * N + n.index
                        bNeedAcc[k] += eaters * n.use / 100 * m / 100 * Tune.NEEDS_INTERVAL
                        if (bNeedAcc[k] >= unit) {
                            val r = richestGood(b, n.goods)
                            if (r >= 0) { inv[b * R + r]--; statUse[r]++; bNeedAcc[k] -= unit * content.resources[r].lasts } else bNeedAcc[k] = unit
                        }
                        richestGood(b, n.goods) >= 0
                    }
                }
            }
            val applicable = n.level <= level && n.tier <= def.tier
            if (applicable) { wAll += n.weight; if (sat) wSat += n.weight }
            if (sat) { ok = ok or (1 shl n.index); target += n.weight } else if (n.vital) { vitalMissing = true; vitalMask = vitalMask or (1 shl n.index) }
        }
        // доступных потребностей больше чем на 100 - мягкая доля: каждая важна для 100%, но нехватка
        // новых (копчёности, платье, свечи) не обваливает довольство; до 100 - как прежде (сумма до 100)
        if (wAll > 100) target = minOf(100, wSat * 100 / ((wAll + 100) / 2))
        if (hasCathedral) target += Tune.WONDER_HAPPY
        if (festivalLeft > 0) target += Tune.FESTIVAL_HAPPY
        if (target > 100) target = 100
        // налог - после ограничения сверху: высокий налог заметен и у полностью довольного дома
        target = (target + Tune.TAX_HAPPY[taxLevel]).coerceIn(0, 100)
        if (vitalMissing && target > Tune.VITAL_CAP) target = Tune.VITAL_CAP
        if (bFire[b] > 0 && target > Tune.FIRE_HAPPY) target = Tune.FIRE_HAPPY
        if (bSick[b] > 0 && target > Tune.SICK_HAPPY) target = Tune.SICK_HAPPY
        if (flooded(b) && target > Tune.SICK_HAPPY) target = Tune.SICK_HAPPY
        // голод, жажда, холод - событие только при переходе
        val hungry = vitalMissing && res > 0
        if (hungry && bHungry[b] == 0) event(EV_HUNGRY, b, vitalMask)
        bHungry[b] = if (hungry) 1 else 0
        if (bUpg[b] == 1) finishUpgrade(b)
        bNeedOk[b] = ok
        if (bHappy[b] < target) bHappy[b]++ else if (bHappy[b] > target) bHappy[b]--
        sumHappy += bHappy[b] * res
        sumRes += res
    }
    if (sumRes > 0) townHappy = sumHappy / sumRes   // без жильцов - прежнее значение
    val levels = content.levels
    while (level + 1 < levels.size && agentCount >= levels[level + 1].pop && townHappy >= levels[level + 1].happy) {
        level++
        computeBorder()
        event(EV_LEVEL, level)
    }
    if (traderHere) autoSell()
    payUpkeep(false)
}

/**
 * Содержание зданий: сначала тех, что дают еду, воду и топливо (без них город вымирает, когда золота мало),
 * потом остальных - по порядку номеров. На рассвете (dawn) платится за новые сутки; днём - повторная попытка
 * за те, кому не хватило. Законсервированное игроком (0 рабочих) и площадки не платят.
 */
private fun World.payUpkeep(dawn: Boolean) {
    var short = false
    for (vital in 1 downTo 0) for (b in 0 until buildingCount) {
        val def = def(b)
        if (def.upkeep == 0 || bState[b] != B_ACTIVE) continue
        if ((if (makesVital(bDef[b])) 1 else 0) != vital) continue
        if (!dawn && bUnpaid[b] == 0) continue
        if (def.workers > 0 && bWanted[b] == 0) { bUnpaid[b] = 0; continue }
        val cost = upkeepOf(b)
        if (gold >= cost) {
            gold -= cost
            lastUpkeep += cost
            bUnpaid[b] = 0
        } else {
            if (bUnpaid[b] == 0) short = true
            bUnpaid[b] = 1
        }
    }
    if (short) event(EV_UNPAID, 0)
}

/** Содержание здания в сутки: растёт с уровнем города (жалованье в богатом городе выше) */
fun World.upkeepOf(b: Int): Int = upkeepFor(def(b)) * (100 + Tune.LEVEL_UPKEEP * (bLevel[b] - 1)) / 100
fun World.upkeepFor(def: town.data.BuildingDef): Int =
    (def.upkeep.toLong() * (Tune.UPKEEP_BASE + level) * Tune.TICKS_PER_DAY * (1000 + agentCount * Tune.UPKEEP_POP) /
        ((Tune.UPKEEP_BASE + 1).toLong() * Tune.BASE_DAY * 1000)).toInt()

/** Содержание всех готовых зданий за сутки (для статистики и подсказок) */
fun World.upkeepPerDay(): Int {
    var n = 0
    for (b in 0 until buildingCount) {
        val def = def(b)
        if (bState[b] == B_ACTIVE && !(def.workers > 0 && bWanted[b] == 0)) n += upkeepOf(b)
    }
    return n
}

/** Услуга: есть ли работник; таверна и подобные ещё тратят свой товар (эль) по рецепту */
private fun World.serviceWork(b: Int) {
    val def = def(b)
    val needsInput = def.input.any { it > 0 }
    if (def.isDecor) { bStatus[b] = BS_OK; return }
    bStatus[b] = when {
        bFire[b] > 0 -> BS_FIRE
        flooded(b) -> BS_FLOOD
        bUnpaid[b] != 0 -> BS_UNPAID
        bWorkers[b] == 0 -> BS_NO_WORKERS
        needsInput && !hasInput(b) -> BS_NO_INPUT
        else -> BS_OK
    }
    if (bStatus[b] != BS_OK || !needsInput) return
    bWork[b] += Tune.NEEDS_INTERVAL
    if (bWork[b] >= def.time) {
        bWork[b] = 0
        for (r in 0 until R) if (def.input[r] > 0) { inv[b * R + r] -= def.input[r]; statUse[r] += def.input[r] }
    }
}

private fun World.hasInput(b: Int): Boolean {
    val def = def(b)
    for (r in 0 until R) if (inv[b * R + r] < def.input[r]) return false
    return true
}

/** Улучшение дома: все материалы привезены - дом становится следующим по уровню, жильцы остаются */
private fun World.finishUpgrade(b: Int) {
    val next = def(b).upgradeTo?.let { content.building(it) } ?: run { bUpg[b] = 0; return }
    for (r in 0 until R) if (inv[b * R + r] < next.cost[r]) return
    for (r in 0 until R) { inv[b * R + r] -= next.cost[r]; statUse[r] += next.cost[r] }
    bDef[b] = next.index
    bUpg[b] = 0; bUpgDays[b] = 0
    event(EV_UPGRADED, b)
}

/** Улучшение мастерской, услуги, склада: все материалы следующего уровня привезены - уровень растёт */
internal fun World.finishLevels() {
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE || bUpg[b] != 1 || def(b).isHouse) continue
        var ready = true
        for (r in 0 until R) if (upInv[b * R + r] < upgradeCost(b, r)) { ready = false; break }
        if (!ready) continue
        for (r in 0 until R) { statUse[r] += upgradeCost(b, r); upInv[b * R + r] = 0 }
        val wasFull = bWanted[b] == maxWorkers(b)
        bLevel[b]++
        if (wasFull) bWanted[b] = maxWorkers(b)
        bUpg[b] = 0
        if (def(b).border > 0) computeBorder()
        if (def(b).gatherCode != G_NONE) bNear[b] = countNear(b)
        event(EV_UPGRADED, b)
    }
}

/** Пожар кончился: без каланчи рядом запасы здания сгорают (кроме уже забронированных носильщиками) */
private fun World.fireOut(b: Int) {
    val burned = bFireSafe[b] == 0 && bState[b] != B_GONE
    if (burned) for (r in 0 until R) {
        val k = b * R + r
        if (inv[k] > reserved[k]) inv[k] = reserved[k]
    }
    bFireSafe[b] = 0
    event(EV_FIRE_OUT, b, if (burned) 1 else 0)
}

/** Не хватает ли дому жизненной потребности (для значка над домом) */
fun World.lacksVital(b: Int): Boolean {
    if (bResidents[b] == 0) return false
    for (n in content.needs) {
        if (!n.vital || n.level > level || n.tier > def(b).tier || (n.service == null && n.season[season] == 0)) continue
        if (bNeedOk[b] and (1 shl n.index) == 0) return true
    }
    return false
}

/** Товар потребности, которого в доме больше всего (-1 - нет ни одного) */
private fun World.richestGood(b: Int, goods: IntArray): Int {
    var best = -1; var bestN = 0
    for (r in goods) if (inv[b * R + r] > bestN) { bestN = inv[b * R + r]; best = r }
    return best
}

/** Есть ли рядом со зданием b работающее здание с услугой service (таверна - ещё и с элем) */
internal fun World.served(b: Int, service: String): Boolean {
    val s = def(b).size
    val cx = bX[b] * 2 + s; val cy = bY[b] * 2 + s
    for (o in 0 until buildingCount) {
        val d = def(o)
        if (bState[o] != B_ACTIVE || d.serves != service || bFire[o] > 0 || bUnpaid[o] != 0 || flooded(o)) continue
        if (!d.isDecor && bWorkers[o] == 0) continue
        if (d.input.any { it > 0 } && !hasInput(o)) continue
        // центры в половинах клетки, чтобы не терять половину у зданий чётного размера
        val dx = Math.abs(bX[o] * 2 + d.size - cx); val dy = Math.abs(bY[o] * 2 + d.size - cy)
        if (maxOf(dx, dy) <= serveRadius(o) * 2) return true
    }
    return false
}

/** Рассвет: статистика суток, налоги, улучшения домов, уход жителей, торговец, сезонные события, пожары и болезни */
internal fun World.dawn() {
    // статистика: прошедшие сутки - в историю
    // история партии для итогов: жители, довольство, золото на рассвете
    val hd = dayCount % Tune.HISTORY
    dayPop[hd] = agentCount; dayHappy[hd] = townHappy; dayGold[hd] = gold
    val hr = dayCount % Tune.HISTORY_RES
    for (r in 0 until R) dayStock[hr * R + r] = totalStock(r)
    dayCount++
    for (r in 0 until R) {
        madeTotal[r] += statProd[r]
        histProd[histPos * R + r] = statProd[r]; histUse[histPos * R + r] = statUse[r]
        statProd[r] = 0; statUse[r] = 0
    }
    histPos = (histPos + 1) % Tune.STAT_DAYS
    if (histDays < Tune.STAT_DAYS) histDays++

    lastTax = taxPreview(taxLevel)
    gold += lastTax
    lastUpkeep = 0
    payUpkeep(true)
    prestige += fameDaily()
    crownDawn()
    forecastDawn()

    // полный и довольный дом готовится к улучшению; когда город дорос - заказывает материалы
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE || !def(b).isHouse || bUpg[b] != 0) continue
        val next = def(b).upgradeTo?.let { content.building(it) } ?: continue
        val ready = bResidents[b] >= def(b).beds && bHappy[b] >= Tune.UPGRADE_HAPPY && bFire[b] == 0 && bSick[b] == 0
        bUpgDays[b] = if (ready) bUpgDays[b] + 1 else 0
        // готов - игрок улучшает кнопкой (Command.UPGRADE): золото сразу, материалы везут носильщики
    }

    // спокойный режим: жители не уходят
    if (!calm) for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE || !def(b).isHouse || bResidents[b] == 0 || bHappy[b] >= Tune.LEAVE_BELOW) continue
        var a = agentCount - 1
        while (a >= 0 && !(home[a] == b && state[a] == ST_INSIDE && where[a] == b && taskDst[a] < 0)) a--
        if (a >= 0) { removeCitizen(a); event(EV_LEFT, b) }
    }

    // готовое некуда везти: склады полны
    for (b in 0 until buildingCount) {
        if (bState[b] == B_ACTIVE && bStatus[b] == BS_OUTPUT_FULL && noStoreFor(b)) { event(EV_STORE_FULL, b); break }
    }

    if ((tick / Tune.TICKS_PER_DAY) % Tune.TRADER_EVERY == 1 % Tune.TRADER_EVERY && !traderHere) traderArrive()
    // корабль у пристани: не зимой, раз в несколько суток
    if (season != WINTER && day >= shipNext) {
        val h = workingHarbor()
        if (h >= 0) shipArrive(h)
    }
}

/** Налог за сутки при уровне налога level (по нынешним жителям и довольству) - для интерфейса и рассвета */
fun World.taxPreview(level: Int): Int {
    var sum = 0L
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).isHouse) sum += bResidents[b].toLong() * bHappy[b] * def(b).tax
    return (sum * Tune.TAX * Tune.TICKS_PER_DAY * Tune.TAX_PCT[level.coerceIn(0, 2)] / (1000000L * Tune.BASE_DAY)).toInt()
}

/** Первая работающая пристань (готова, есть работники, не горит, содержание заплачено) или -1 */
internal fun World.workingHarbor(): Int {
    for (b in 0 until buildingCount) {
        if (bState[b] == B_ACTIVE && def(b).serves == "port" && bWorkers[b] > 0 && bFire[b] == 0 && bUnpaid[b] == 0) return b
    }
    return -1
}

/** Корабль пришёл к пристани h: как торговец, но товара вдвое больше и несколько товаров нужны ему особенно */
internal fun World.shipArrive(h: Int) {
    traderArrive(false)
    traderShip = true
    traderLeft = Tune.NIGHT_START - tick % Tune.TICKS_PER_DAY
    for (r in 0 until R) {
        traderStock[r] *= Tune.SHIP_SUPPLY
        shipWant[r] = 0
    }
    val pool = ArrayList<Int>()
    for (r in 0 until R) if (content.resources[r].price > 0) pool.add(r)
    var k = 0
    while (k < Tune.SHIP_WANT && pool.isNotEmpty()) {
        val r = pool.removeAt(rng.next(pool.size))
        traderPrice[r] += Tune.SHIP_WANT_MIN + rng.next(Tune.SHIP_WANT_MAX - Tune.SHIP_WANT_MIN + 1)
        shipWant[r] = 1
        k++
    }
    event(EV_SHIP, h)
    shipNext = day + maxOf(1, Tune.SHIP_EVERY - (bLevel[h] - 1))
    autoSell()
}

/** Корабль пришёл и хочет этот товар дороже (для подсветки в интерфейсе) */
fun World.shipWanted(r: Int): Boolean = traderShip && shipWant[r] != 0

/** Торговец пришёл (по расписанию или позвали): стоит до вечера, товара больше с уровнем города */
internal fun World.traderArrive(announce: Boolean = true) {
    traderShip = false
    traderLeft = Tune.TRADER_STAY - tick % Tune.TICKS_PER_DAY
    if (announce) event(EV_TRADER, hall)
    for (r in 0 until R) {
        if (content.resources[r].price == 0) { traderStock[r] = 0; continue }
        traderPrice[r] = Tune.PRICE_MIN + rng.next(Tune.PRICE_MAX - Tune.PRICE_MIN + 1)
        val base = Tune.TRADER_SUPPLY + Tune.TRADER_SUPPLY_LEVEL * level
        traderStock[r] = base + rng.next(base * 2 + 1)
    }
    // сезонные цены: еда и дрова дорожают к зиме
    for (n in content.needs) {
        if (n.id != "food") continue
        for (r in n.goods) seasonPrice(r, Tune.SEASON_FOOD[season])
    }
    seasonPrice(content.resource("logs").index, Tune.SEASON_LOGS[season])
    if (announce) autoSell()
}

private fun World.seasonPrice(r: Int, pct: Int) {
    if (content.resources[r].price == 0 || pct == 0) return
    traderPrice[r] = maxOf(10, traderPrice[r] * (100 + pct) / 100)
}

/** Сезонные события (в первый день сезона), пожары и болезни; всё через RNG мира - повтор тот же */
internal fun World.disasters() {
    if (showcase) return
    val dayIndex = tick / Tune.TICKS_PER_DAY
    // частота бедствий из настроек песочницы; засуха - пожары чаще
    val dpct = Tune.DISASTER_PCT[setDisasters]
    // обучение: до «Деревни» без пожаров и болезней - новичок ещё осваивается
    val gentle = scenario >= 0 && content.scenarios[scenario].gentle && level < 1
    if (dayIndex % Tune.SEASON_DAYS == 0) when (season) {
        SPRING -> { badHarvest = false; harshWinter = false }
        SUMMER -> if (rng.next(100) < Tune.BAD_HARVEST_CHANCE * dpct / 100) { badHarvest = true; event(EV_BAD_HARVEST, 0) }
        AUTUMN -> if (rng.next(100) < Tune.HARSH_WINTER_CHANCE * dpct / 100) { harshWinter = true; event(EV_HARSH_WINTER, 0) }
        WINTER -> badHarvest = false
    }
    // пожар: одно случайное здание (кроме ратуши)
    if (!gentle && dayIndex + 1 >= Tune.FIRE_FROM_DAY && rng.next(1000) < Tune.FIRE_CHANCE * dpct / 100 * (if (weather == W_DROUGHT) Tune.DROUGHT_FIRE else 100) / 100) {
        var n = 0
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && b != hall && bFire[b] == 0) n++
        if (n > 0) {
            var k = rng.next(n)
            for (b in 0 until buildingCount) {
                if (bState[b] != B_ACTIVE || b == hall || bFire[b] != 0) continue
                if (k-- == 0) {
                    val safe = served(b, "fire")
                    bFireSafe[b] = if (safe) 1 else 0
                    bFire[b] = if (safe) Tune.FIRE_TICKS_WATCH else Tune.FIRE_TICKS
                    event(EV_FIRE, b, if (safe) 1 else 0)
                    break
                }
            }
        }
    }
    // болезнь: дома без лекаря рядом, чаще - недовольные
    for (b in 0 until buildingCount) {
        if (gentle || bState[b] != B_ACTIVE || !def(b).isHouse || bResidents[b] == 0 || bSick[b] > 0) continue
        val chance = (if (bHappy[b] < 70) Tune.SICK_CHANCE_LOW else Tune.SICK_CHANCE) * dpct / 100
        if (rng.next(1000) < chance && !served(b, "health")) { bSick[b] = Tune.SICK_TICKS; event(EV_SICK, b) }
    }
}

/** Житель a уходит из города; на его место в массивах переезжает последний */
internal fun World.removeCitizen(a: Int) {
    cancelTask(a)
    bResidents[home[a]]--
    if (job[a] == JOB_WORKER && jobB[a] >= 0) bWorkers[jobB[a]]--
    val last = agentCount - 1
    // очередь путей: записи ушедшего убираем, записи последнего переводим на новый номер
    var n = 0
    for (i in 0 until qSize) {
        val v = queue[(qHead + i) % Tune.MAX_AGENTS]
        if (v == a) continue
        queue[(qHead + n) % Tune.MAX_AGENTS] = if (v == last) a else v
        n++
    }
    qSize = n
    if (a != last) {
        for (arr in agentArrays()) arr[a] = arr[last]
        paths[a] = paths[last]
    }
    paths[last] = null
    agentCount--
}

// ---------- торговец ----------

/** Цена, за которую торговец продаёт ресурс r, и за которую покупает */
fun World.buyPrice(r: Int) = maxOf(1, content.resources[r].price * traderPrice[r] / 100)
fun World.sellPrice(r: Int) = maxOf(1, buyPrice(r) * ((if (traderShip) Tune.SHIP_SHARE else Tune.SELL_SHARE) + (if (fairOn) Tune.FAIR_SHARE else 0)) / 100)

/** Купленное сразу попадает в ратушу (сколько влезет, хватит золота и есть у торговца) */
internal fun World.buy(r: Int, want: Int) {
    if (!traderHere || hall < 0 || content.resources[r].price == 0) return
    val n = minOf(want, traderStock[r], gold / buyPrice(r), space(hall))
    if (n <= 0) return
    gold -= n * buyPrice(r)
    traderStock[r] -= n
    inv[hall * R + r] += n
}

/** Продажа со складов (не трогая забронированное носильщиками) */
internal fun World.sell(r: Int, want: Int) {
    if (!traderHere || content.resources[r].price == 0) return
    var left = want
    for (s in 0 until buildingCount) {
        if (left <= 0) break
        if (bState[s] != B_ACTIVE || !def(s).isStore) continue
        val n = minOf(left, available(s, r))
        if (n <= 0) continue
        inv[s * R + r] -= n
        left -= n
    }
    val sold = want - left
    gold += sold * sellPrice(r)
    traderStock[r] += sold
}

/** Всё сверх лимита автопродажи уходит торговцу */
internal fun World.autoSell() {
    if (!traderHere) return
    for (r in 0 until R) {
        if (sellAbove[r] < 0) continue
        var total = 0
        for (s in 0 until buildingCount) if (bState[s] == B_ACTIVE && def(s).isStore) total += available(s, r)
        if (total > sellAbove[r]) sell(r, total - sellAbove[r])
    }
}

/** Нет склада со свободным местом хотя бы для одного из товаров здания b */
private fun World.noStoreFor(b: Int): Boolean {
    for (r in 0 until R) if (def(b).output[r] > 0 && storeWithSpace(b, r) < 0) return true
    return false
}
