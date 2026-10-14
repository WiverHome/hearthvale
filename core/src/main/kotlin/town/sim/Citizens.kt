package town.sim

// Состояния жителя
const val ST_IDLE = 0       // стоит на улице, ждёт дела
const val ST_WAIT_PATH = 1  // ждёт маршрута
const val ST_MOVE = 2       // идёт
const val ST_INSIDE = 3     // внутри здания (не рисуется)
const val ST_BUILD = 4      // строит у площадки
const val ST_GATHER = 5     // добытчик работает у ресурса (рубит, ломает камень, ловит рыбу, охотится, сажает)

// Профессии: свободные жители - носильщики
const val JOB_CARRIER = 0
const val JOB_WORKER = 1
const val JOB_BUILDER = 2

// Куда идёт житель
const val GOAL_NONE = 0
const val GOAL_HOME = 1
const val GOAL_WORK = 2
const val GOAL_STORE = 3
const val GOAL_SRC = 4      // забрать груз
const val GOAL_DST = 5      // отнести груз
const val GOAL_SITE = 6     // строить
const val GOAL_GATHER = 7   // добытчик идёт к клетке gStand у ресурса gCell
const val GOAL_BACK = 8     // добытчик несёт добычу в мастерскую

/*
 * Поведение жителей. Весь перенос делают носильщики по заявкам: вывоз готового из мастерских на склад,
 * подвоз сырья со склада в мастерские и материалов на стройки. Забронированное у источника (reserved)
 * и ожидаемое у получателя (incoming) не даёт двум носильщикам взять одну и ту же работу.
 * Ночью все идут домой; носильщик с грузом сначала доносит его.
 */

internal fun World.cellOf(a: Int) = (py[a] / Tune.SUB) * w + px[a] / Tune.SUB

private fun World.dist(c1: Int, c2: Int): Int {
    val dx = Math.abs(c1 % w - c2 % w)
    val dy = Math.abs(c1 / w - c2 / w)
    return if (dx > dy) dx else dy
}

internal fun World.setPos(a: Int, cell: Int) {
    px[a] = (cell % w) * Tune.SUB + Tune.SUB / 2
    py[a] = (cell / w) * Tune.SUB + Tune.SUB / 2
    prevX[a] = px[a]; prevY[a] = py[a]
}

/** Новый житель приходит к ратуше, если в каком-нибудь доме есть кровать */
internal fun World.spawnCitizen() {
    if (agentCount >= Tune.MAX_AGENTS || hall < 0) return
    val house = houseWithBed()
    if (house < 0) return
    spawnCitizenIn(house, entrance(hall))
}

/** Новый житель дома house появляется в клетке cell (пришёл к ратуше или вырос в доме); номер жителя */
internal fun World.spawnCitizenIn(house: Int, cell: Int): Int {
    val a = agentCount++
    setPos(a, cell)
    state[a] = ST_IDLE; where[a] = -1
    home[a] = house; bResidents[house]++
    job[a] = JOB_CARRIER; jobB[a] = -1
    goal[a] = GOAL_NONE; goalB[a] = -1
    timer[a] = 0; idle[a] = 0
    carryR[a] = 0; carryN[a] = 0
    taskSrc[a] = -1; taskDst[a] = -1; taskR[a] = 0; taskN[a] = 0; aCart[a] = 0
    aSerial[a] = nextSerial++; aArrived[a] = day; aJobChanges[a] = 0; aCarried[a] = 0; aFavR[a] = 0; aFavN[a] = 0
    paths[a] = null; pathPos[a] = 0
    gCell[a] = -1; gStand[a] = -1
    aSkill[a] = 0
    return a
}

/** История жителя: отнесено всего и самый частый груз (один счётчик Мисры-Гриса) */
private fun World.noteCarried(a: Int, r: Int, n: Int) {
    if (n <= 0) return
    aCarried[a] += n
    if (aFavR[a] == r + 1) aFavN[a] += n
    else {
        aFavN[a] -= n
        if (aFavN[a] <= 0) { aFavR[a] = r + 1; aFavN[a] = -aFavN[a] }
    }
}

private fun World.isFreeCarrier(a: Int) = job[a] == JOB_CARRIER && taskDst[a] < 0 && state[a] != ST_WAIT_PATH && state[a] != ST_MOVE

private fun World.freeCarrier(): Int {
    for (a in 0 until agentCount) if (isFreeCarrier(a)) return a
    return -1
}

/** Свободный носильщик, который живёт ближе всех к зданию b, - меньше ходить на работу */
private fun World.freeCarrierNear(b: Int): Int {
    var best = -1; var bestD = Int.MAX_VALUE
    val e = entrance(b)
    for (a in 0 until agentCount) {
        if (!isFreeCarrier(a)) continue
        val d = if (home[a] >= 0) dist(entrance(home[a]), e) else 0
        if (d < bestD) { bestD = d; best = a }
    }
    return best
}

/** Профессии: строители (пока есть стройки), рабочие в мастерские; не меньше трети жителей (CARRIER_SHARE) остаются носильщиками */
internal fun World.assignJobs() {
    var carriers = 0
    var builders = 0
    for (a in 0 until agentCount) {
        if (job[a] == JOB_CARRIER) carriers++
        if (job[a] == JOB_BUILDER) builders++
    }
    val minCarriers = maxOf(2, agentCount / Tune.CARRIER_SHARE)

    // лишние рабочие (игрок уменьшил число или не сезон) снова становятся носильщиками
    for (b in 0 until buildingCount) {
        var a = agentCount - 1
        while (bWorkers[b] > wantedWorkers(b) && a >= 0) {
            // добытчик с добычей или за работой у ресурса уйдёт позже - сначала донесёт
            if (job[a] == JOB_WORKER && jobB[a] == b && carryN[a] == 0 && state[a] != ST_GATHER) {
                job[a] = JOB_CARRIER; aJobChanges[a]++; jobB[a] = -1; bWorkers[b]--; carriers++
            }
            a--
        }
    }

    var hasSite = false
    for (b in 0 until buildingCount) if (bState[b] == B_SITE) { hasSite = true; break }
    if (!hasSite) {
        // строек нет - строители, которые не заняты, возвращаются в носильщики
        for (a in 0 until agentCount) if (job[a] == JOB_BUILDER && state[a] != ST_BUILD) {
            job[a] = JOB_CARRIER; aJobChanges[a]++; jobB[a] = -1; carriers++; builders--
        }
    } else if (hall >= 0) {
        while (builders < builderTarget() && carriers > minCarriers) {
            val a = freeCarrier()
            if (a < 0) break
            job[a] = JOB_BUILDER; aJobChanges[a]++; jobB[a] = -1
            builders++; carriers--
        }
    }

    // рабочих раздаём сначала срочным зданиям, потом обычным, потом второстепенным; при равном приоритете - сначала
    // тем, что дают еду, воду и дрова (жителей стало мало - колодцы и еда не остаются без людей)
    for (pass in 1 downTo -1) for (vital in 1 downTo 0) for (b in 0 until buildingCount) {
        if (bPrio[b] != pass || bState[b] != B_ACTIVE || def(b).workers == 0) continue
        if ((if (makesVital(bDef[b])) 1 else 0) != vital) continue
        while (bWorkers[b] < wantedWorkers(b) && carriers > minCarriers) {
            val a = freeCarrierNear(b)
            if (a < 0) return
            job[a] = JOB_WORKER; aJobChanges[a]++; jobB[a] = b
            bWorkers[b]++; carriers--
        }
    }
}

/** Здание делает товар жизненной потребности (еда, вода, дрова) */
internal fun World.makesVital(d: Int): Boolean = vitalDef[d]

/**
 * Сколько строителей нужно: у ратуши свои, и по одному за уровень города, пока строек больше, чем строителей
 * (лишние строители без стройки отнимали бы носильщиков)
 */
fun World.builderTarget(): Int {
    if (hall < 0) return 0
    val base = def(hall).builders
    var sites = 0
    for (b in 0 until buildingCount) if (bState[b] == B_SITE) sites++
    return base + minOf(level * Tune.BUILDERS_LEVEL, maxOf(0, sites - base))
}

/**
 * Переселение ближе к работе: рабочий, живущий далеко от мастерской, переезжает в свободную кровать поближе
 * или меняется домами с носильщиком, который живёт рядом с ней (носильщику всё равно, откуда выходить)
 */
internal fun World.relocate() {
    for (a in 0 until agentCount) {
        if (job[a] != JOB_WORKER || jobB[a] < 0 || home[a] < 0) continue
        val e = entrance(jobB[a])
        val dHome = dist(entrance(home[a]), e)
        if (dHome <= Tune.RELOCATE_NEAR) continue
        var bestHouse = -1; var bestD = dHome - Tune.RELOCATE_GAIN
        for (b in 0 until buildingCount) {
            if (bState[b] != B_ACTIVE || !def(b).isHouse || bResidents[b] >= def(b).beds) continue
            val d = dist(entrance(b), e)
            if (d < bestD) { bestD = d; bestHouse = b }
        }
        var bestC = -1
        for (c in 0 until agentCount) {
            if (job[c] != JOB_CARRIER || home[c] < 0) continue
            val d = dist(entrance(home[c]), e)
            if (d < bestD) { bestD = d; bestC = c; bestHouse = -1 }
        }
        if (bestC >= 0) {
            val h = home[a]; home[a] = home[bestC]; home[bestC] = h
        } else if (bestHouse >= 0) {
            bResidents[home[a]]--; bResidents[bestHouse]++; home[a] = bestHouse
        }
    }
}

/**
 * Сколько рабочих нужно зданию сейчас: не в свой сезон - ни одного; мастерская стоит (лимит, готовое некуда
 * или некому вывозить, нет сырья) - рабочие идут носить, пока причина не уйдёт
 */
private fun World.wantedWorkers(b: Int): Int {
    if (!def(b).seasons[season] || bUnpaid[b] != 0 || bFire[b] > 0 || flooded(b)) return 0
    if (def(b).isProducer && bStatus[b].let { it == BS_LIMIT || it == BS_OUTPUT_FULL || it == BS_FAR || it == BS_NO_INPUT || it == BS_NO_RESOURCE }) return 0
    return bWanted[b]
}

internal fun World.countInside() {
    java.util.Arrays.fill(bInside, 0, buildingCount, 0)
    java.util.Arrays.fill(bSkilled, 0, buildingCount, 0)
    for (a in 0 until agentCount) {
        if (state[a] == ST_INSIDE && job[a] == JOB_WORKER && where[a] == jobB[a]) {
            bInside[where[a]]++
            bSkilled[where[a]] += aSkill[a]
        }
    }
}

// ---------- заявки носильщиков ----------

internal fun World.space(s: Int): Int {
    var used = 0
    for (r in 0 until R) used += inv[s * R + r] + incoming[s * R + r]
    return capacityOf(s) - used
}

internal fun World.available(b: Int, r: Int) = inv[b * R + r] - reserved[b * R + r]

/** Ближайший к зданию b склад со свободным местом (r >= 0 - только те, что принимают ресурс r) */
internal fun World.storeWithSpace(b: Int, r: Int = -1, skip: Int = -1): Int {
    var best = -1; var bestD = Int.MAX_VALUE
    val e = entrance(b)
    for (s in 0 until buildingCount) {
        if (s == skip || bState[s] != B_ACTIVE || !def(s).isStore || space(s) <= 0 || !storeAccepts(s, r)) continue
        val d = dist(e, entrance(s))
        if (d < bestD) { bestD = d; best = s }
    }
    return best
}

/**
 * Ближайший к зданию b источник ресурса r: склад или мастерская, которая его делает (готовое у неё можно
 * нести сразу получателю, минуя склад - вдвое меньше носки)
 */
private fun World.sourceWithStock(b: Int, r: Int): Int {
    var best = -1; var bestD = Int.MAX_VALUE
    val e = entrance(b)
    for (s in 0 until buildingCount) {
        if (s == b || bState[s] != B_ACTIVE || available(s, r) <= 0) continue
        val d0 = def(s)
        if (!d0.isStore && d0.output[r] == 0) continue
        val d = dist(e, entrance(s))
        if (d < bestD) { bestD = d; best = s }
    }
    return best
}

/** Сколько тачек и телег в ходу у жителей (kind: 1 - тачки, 2 - телеги) */
fun World.cartsInUse(kind: Int): Int {
    var n = 0
    for (a in 0 until agentCount) if (aCart[a] == kind) n++
    return n
}

/** Тачки и телеги изнашиваются в работе: после ходки с ними одна из CART_TRIPS ломается (лежащие на складе - целы) */
internal fun World.wearCart(a: Int) {
    val r = when (aCart[a]) { 1 -> barrowR; 2 -> wagonR; else -> -1 }
    if (r < 0 || rng.next(Tune.CART_TRIPS) != 0 || stockAvailable(r) <= 0) return
    takeFromStores(r, 1)
    statUse[r]++
}

internal fun World.assignTasks() {
    var budget = Tune.ASSIGN_BUDGET
    // рынки: список на этот тик (дома берут товар и с рынка)
    marketCount = 0
    for (m in 0 until buildingCount) {
        if (bState[m] == B_ACTIVE && def(m).distributes.isNotEmpty() && bWorkers[m] > 0 && bFire[m] == 0 && bUnpaid[m] == 0) markets[marketCount++] = m
    }
    tradeMain = tradeStore()
    var wagons = if (wagonR >= 0) stockAvailable(wagonR) - cartsInUse(2) else 0
    var barrows = if (barrowR >= 0) stockAvailable(barrowR) - cartsInUse(1) else 0
    for (a in 0 until agentCount) {
        if (budget <= 0) return
        if (!isFreeCarrier(a) || timer[a] > 0) continue
        budget--
        val cap = if (wagons > 0) Tune.CARRY_WAGON else if (barrows > 0) Tune.CARRY_BARROW else Tune.CARRY
        if (!findTask(a, cap)) { timer[a] = Tune.RETRY; continue }
        // тачка или телега нужна, только если груз больше обычного
        if (taskN[a] > Tune.CARRY) {
            if (cap == Tune.CARRY_WAGON) { aCart[a] = 2; wagons-- } else { aCart[a] = 1; barrows-- }
        }
    }
}

/** Самая выгодная заявка для носильщика a: меньше всего идти (вывоз, пока не срочно, - со штрафом) */
private fun World.findTask(a: Int, cap: Int): Boolean {
    val here = cellOf(a)
    var bestScore = Int.MAX_VALUE
    var bSrc = -1; var bDst = -1; var bR = -1; var bN = 0
    for (b in 0 until buildingCount) {
        val def = def(b)
        val e = entrance(b)
        // материалы для улучшения мастерской, услуги, склада - как на стройку
        if (bState[b] == B_ACTIVE && bUpg[b] == 1 && !def.isHouse) for (r in 0 until R) {
            val need = upgradeCost(b, r) - upInv[b * R + r] - incoming[b * R + r]
            if (need <= 0) continue
            val s = sourceWithStock(b, r)
            if (s < 0) continue
            val score = dist(here, entrance(s)) + dist(entrance(s), e) - Tune.PRIO_BONUS * bPrio[b]
            if (score < bestScore) {
                bestScore = score; bSrc = s; bDst = b; bR = r
                bN = minOf(cap, need, available(s, r))
            }
        }
        // склад больше не принимает товар - выносим остаток на другой склад
        if (bState[b] == B_ACTIVE && def.role == "store") for (r in 0 until R) {
            if (bAccept[b * R + r] != 0) continue
            val avail = available(b, r)
            if (avail <= 0) continue
            val s = storeWithSpace(b, r, b)
            if (s < 0) continue
            val score = dist(here, e) + dist(e, entrance(s)) + Tune.PICKUP_PENALTY
            if (score < bestScore) {
                bestScore = score; bSrc = b; bDst = s; bR = r
                bN = minOf(cap, avail, space(s))
            }
        }
        // торговый пост: товар для договоров - со складов, привезённое соседями и лишнее - на склад
        if (bState[b] == B_ACTIVE && def.serves == "trade") for (r in 0 until R) {
            val keep = if (b == tradeMain) tradeKeep(r) else 0
            val have = inv[b * R + r] + incoming[b * R + r]
            if (keep > have) {
                val s = sourceWithStock(b, r)
                if (s >= 0) {
                    val score = dist(here, entrance(s)) + dist(entrance(s), e) + Tune.HOUSE_PENALTY - Tune.PRIO_BONUS * bPrio[b]
                    if (score < bestScore) {
                        bestScore = score; bSrc = s; bDst = b; bR = r
                        bN = minOf(cap, keep - have, available(s, r))
                    }
                }
            }
            val ex = available(b, r) - keep
            if (ex > 0) {
                val s = storeWithSpace(b, r)
                if (s >= 0) {
                    val score = dist(here, e) + dist(e, entrance(s))
                    if (score < bestScore) {
                        bestScore = score; bSrc = b; bDst = s; bR = r
                        bN = minOf(cap, ex, space(s))
                    }
                }
            }
        }
        if (bState[b] == B_ACTIVE && (def.isProducer || (def.isService && def.input.any { it > 0 }))) {
            for (r in 0 until R) {
                if (def.output[r] > 0) {
                    val avail = available(b, r)
                    if (avail <= 0) continue
                    val s = storeWithSpace(b, r)
                    if (s < 0) continue
                    // срочно: почти полна или уже стоит полной (ждёт, пока вывезут половину)
                    val urgent = inv[b * R + r] + def.output[r] > Tune.OUT_BUF - 2 || bStatus[b] == BS_OUTPUT_FULL || bStatus[b] == BS_FAR
                    val score = dist(here, e) + dist(e, entrance(s)) + (if (urgent) 0 else Tune.PICKUP_PENALTY) - Tune.PRIO_BONUS * bPrio[b]
                    if (score < bestScore) {
                        bestScore = score; bSrc = b; bDst = s; bR = r
                        bN = minOf(cap, avail, space(s))
                    }
                }
                if (r == toolsR && def.tools && bTool[b] <= Tune.TOOL_LIFE / 2 && inv[b * R + r] + incoming[b * R + r] == 0) {
                    // запасной инструмент взамен изношенного
                    val s = sourceWithStock(b, r)
                    if (s >= 0) {
                        val score = dist(here, entrance(s)) + dist(entrance(s), e) + Tune.HOUSE_PENALTY - Tune.PRIO_BONUS * bPrio[b]
                        if (score < bestScore) {
                            bestScore = score; bSrc = s; bDst = b; bR = r
                            bN = minOf(cap, 1, available(s, r))
                        }
                    }
                }
                if (def.input[r] > 0) {
                    val need = maxOf(Tune.IN_BUF, def.input[r] * 2) - inv[b * R + r] - incoming[b * R + r]
                    if (need <= 0) continue
                    val s = sourceWithStock(b, r)
                    if (s < 0) continue
                    val score = dist(here, entrance(s)) + dist(entrance(s), e) - Tune.PRIO_BONUS * bPrio[b]
                    if (score < bestScore) {
                        bestScore = score; bSrc = s; bDst = b; bR = r
                        bN = minOf(cap, need, available(s, r))
                    }
                }
            }
        } else if (bState[b] == B_ACTIVE && def.isService && def.distributes.isNotEmpty() && bWorkers[b] > 0) {
            // рынок держит запас своих товаров, чтобы развозить по домам
            var total = 0
            for (r in 0 until R) total += inv[b * R + r] + incoming[b * R + r]
            for (r in def.distributes) {
                val need = minOf(Tune.MARKET_FILL - inv[b * R + r] - incoming[b * R + r], Tune.MARKET_CAP - total)
                if (need <= 0) continue
                val s = sourceWithStock(b, r)
                if (s < 0) continue
                val score = dist(here, entrance(s)) + dist(entrance(s), e) + Tune.HOUSE_PENALTY - Tune.PRIO_BONUS * bPrio[b]
                if (score < bestScore) {
                    bestScore = score; bSrc = s; bDst = b; bR = r
                    bN = minOf(cap, need, available(s, r))
                }
            }
        } else if (bState[b] == B_ACTIVE && def.isHouse && bUpg[b] == 1) {
            // материалы для улучшения дома - как на стройку
            val next = def.upgradeTo?.let { content.building(it) }
            if (next != null) for (r in 0 until R) {
                val need = next.cost[r] - inv[b * R + r] - incoming[b * R + r]
                if (need <= 0) continue
                val s = sourceWithStock(b, r)
                if (s < 0) continue
                val score = dist(here, entrance(s)) + dist(entrance(s), e)
                if (score < bestScore) {
                    bestScore = score; bSrc = s; bDst = b; bR = r
                    bN = minOf(cap, need, available(s, r))
                }
            }
            houseGoods(b, here, e, cap) { s, r, n, score -> if (score < bestScore) { bestScore = score; bSrc = s; bDst = b; bR = r; bN = n } }
        } else if (bState[b] == B_ACTIVE && def.isHouse && bResidents[b] > 0) {
            houseGoods(b, here, e, cap) { s, r, n, score -> if (score < bestScore) { bestScore = score; bSrc = s; bDst = b; bR = r; bN = n } }
        } else if (bState[b] == B_SITE) {
            for (r in 0 until R) {
                val need = def.cost[r] - inv[b * R + r] - incoming[b * R + r]
                if (need <= 0) continue
                val s = sourceWithStock(b, r)
                if (s < 0) continue
                val score = dist(here, entrance(s)) + dist(entrance(s), e) - Tune.URGENT_BONUS - (if (bPrio[b] == 1) Tune.PRIO_SITE else 0)
                if (score < bestScore) {
                    bestScore = score; bSrc = s; bDst = b; bR = r
                    bN = minOf(cap, need, available(s, r))
                }
            }
        }
    }
    if (bSrc < 0 || bN <= 0) return false
    reserved[bSrc * R + bR] += bN
    incoming[bDst * R + bR] += bN
    taskSrc[a] = bSrc; taskDst[a] = bDst; taskR[a] = bR; taskN[a] = bN
    idle[a] = 0
    goTo(a, bSrc, GOAL_SRC)
    return true
}

/**
 * Заявки на товары для дома b: по каждому нужному товару (кроме не нужных в этот сезон, ещё не открытых и не нужных
 * дому этого уровня) - склад-источник, сколько везти и «цена» (путь; в дом без этой потребности - срочнее)
 */
private inline fun World.houseGoods(b: Int, here: Int, e: Int, cap: Int, consider: (Int, Int, Int, Int) -> Unit) {
    if (bResidents[b] == 0) return
    val tier = def(b).tier
    for (n in content.needs) {
        if (n.goods.isEmpty() || n.level > level || n.tier > tier || n.season[season] == 0) continue
        var have = 0
        for (r in n.goods) have += inv[b * R + r] + incoming[b * R + r]
        val bonus = if (have == 0) -Tune.URGENT_BONUS else Tune.HOUSE_PENALTY
        for (r in n.goods) {
            val need = Tune.HOUSE_BUF - inv[b * R + r] - incoming[b * R + r]
            if (need <= 0) continue
            val s = houseSource(b, r)
            if (s < 0) continue
            consider(s, r, minOf(cap, need, available(s, r)), dist(here, entrance(s)) + dist(entrance(s), e) + bonus)
        }
    }
}

/**
 * Источник товара r для дома b: ближайший из обычного источника и рынков, что держат этот товар и достают до дома
 * (рынок работает, не горит, не стоит без денег)
 */
private fun World.houseSource(b: Int, r: Int): Int {
    var best = sourceWithStock(b, r)
    if (marketCount == 0) return best
    val e = entrance(b)
    var bestD = if (best >= 0) dist(e, entrance(best)) else Int.MAX_VALUE
    val cx = bX[b] * 2 + def(b).size; val cy = bY[b] * 2 + def(b).size
    for (k in 0 until marketCount) {
        val m = markets[k]
        if (available(m, r) <= 0 || r !in def(m).distributes) continue
        val dx = Math.abs(bX[m] * 2 + def(m).size - cx); val dy = Math.abs(bY[m] * 2 + def(m).size - cy)
        if (maxOf(dx, dy) > serveRadius(m) * 2) continue
        val d = dist(e, entrance(m))
        if (d < bestD) { bestD = d; best = m }
    }
    return best
}

/** Сколько товара r не хватает дому b до HOUSE_BUF, если он ему нужен сейчас (уровень города, вид дома, сезон, жильцы) */
internal fun World.houseNeedOf(b: Int, r: Int): Int {
    if (bState[b] != B_ACTIVE || !def(b).isHouse || bResidents[b] == 0) return 0
    val tier = def(b).tier
    for (n in content.needs) {
        if (n.goods.isEmpty() || n.level > level || n.tier > tier || n.season[season] == 0) continue
        for (g in n.goods) if (g == r) return maxOf(0, Tune.HOUSE_BUF - inv[b * R + r] - incoming[b * R + r])
    }
    return 0
}

/** Сколько у дома b (с привозимым) товаров той потребности, в которую входит r */
private fun World.houseHaveOf(b: Int, r: Int): Int {
    for (n in content.needs) {
        var has = false
        for (g in n.goods) if (g == r) has = true
        if (!has) continue
        var have = 0
        for (g in n.goods) have += inv[b * R + g] + incoming[b * R + g]
        return have
    }
    return 0
}

/**
 * Работник рынка развозит товар по домам в радиусе: ближайший дом, которому чего-то не хватает из того, что есть
 * на рынке (где у потребности нет ни одного товара - срочнее). true - заявка создана, идёт за товаром
 */
private fun World.marketDeliver(a: Int, m: Int): Boolean {
    if (timer[a] > 0) return false
    val md = def(m)
    val me = entrance(m)
    val mx = bX[m] * 2 + md.size; val my = bY[m] * 2 + md.size
    var bestScore = Int.MAX_VALUE
    var bDst = -1; var bR = -1; var bN = 0
    for (h in 0 until buildingCount) {
        if (bState[h] != B_ACTIVE || !def(h).isHouse || bResidents[h] == 0) continue
        val dx = Math.abs(bX[h] * 2 + def(h).size - mx); val dy = Math.abs(bY[h] * 2 + def(h).size - my)
        if (maxOf(dx, dy) > serveRadius(m) * 2) continue
        for (r in md.distributes) {
            val avail = available(m, r)
            if (avail <= 0) continue
            val need = houseNeedOf(h, r)
            if (need <= 0) continue
            val score = dist(me, entrance(h)) - (if (houseHaveOf(h, r) == 0) Tune.URGENT_BONUS else 0)
            if (score < bestScore) { bestScore = score; bDst = h; bR = r; bN = minOf(Tune.CARRY, need, avail) }
        }
    }
    if (bDst < 0) { timer[a] = Tune.RETRY; return false }
    reserved[m * R + bR] += bN
    incoming[bDst * R + bR] += bN
    taskSrc[a] = m; taskDst[a] = bDst; taskR[a] = bR; taskN[a] = bN
    idle[a] = 0
    goTo(a, m, GOAL_SRC)
    return true
}

internal fun World.clearTask(a: Int) {
    aCart[a] = 0
    taskSrc[a] = -1; taskDst[a] = -1; taskN[a] = 0
    carryN[a] = 0
}

/** Отмена заявки (путь не найден): снять брони, груз вернуть источнику */
internal fun World.cancelTask(a: Int) {
    if (taskDst[a] < 0) return
    val r = taskR[a]
    if (carryN[a] > 0) inv[taskSrc[a] * R + r] += carryN[a]
    else reserved[taskSrc[a] * R + r] -= taskN[a]
    incoming[taskDst[a] * R + r] -= taskN[a]
    clearTask(a)
}

// ---------- передвижение ----------

internal fun World.enqueue(a: Int) {
    queue[(qHead + qSize) % Tune.MAX_AGENTS] = a
    qSize++
    state[a] = ST_WAIT_PATH
}

/** Идти к клетке cell (добытчик - к ресурсу) */
internal fun World.goToCell(a: Int, cell: Int, g: Int) {
    goal[a] = g; goalB[a] = -1
    where[a] = -1
    if (cellOf(a) == cell) {
        state[a] = ST_IDLE
        arrived(a)
    } else enqueue(a)
}

/** Идти ко входу здания b с целью g */
internal fun World.goTo(a: Int, b: Int, g: Int) {
    goal[a] = g; goalB[a] = b
    where[a] = -1
    if (cellOf(a) == entrance(b)) {
        state[a] = ST_IDLE
        arrived(a)
    } else enqueue(a)
}

internal fun World.processPaths() {
    var budget = Tune.PATH_BUDGET
    while (budget > 0 && qSize > 0) {
        val a = queue[qHead]
        qHead = (qHead + 1) % Tune.MAX_AGENTS
        qSize--
        if (state[a] != ST_WAIT_PATH) continue
        budget--
        val path = findPath(cellOf(a), if (goal[a] == GOAL_GATHER) gStand[a] else entrance(goalB[a]))
        pathsDone++
        nodesExpanded += lastExpanded
        when {
            path == null -> {
                cancelTask(a)
                gCell[a] = -1
                state[a] = ST_IDLE; goal[a] = GOAL_NONE
                timer[a] = Tune.RETRY
            }
            path.isEmpty() -> { state[a] = ST_IDLE; arrived(a) }
            else -> { paths[a] = path; pathPos[a] = 0; state[a] = ST_MOVE }
        }
    }
}

private fun World.move(a: Int) {
    val path = paths[a]!!
    val cell = path[pathPos[a]]
    val tx = (cell % w) * Tune.SUB + Tune.SUB / 2
    val ty = (cell / w) * Tune.SUB + Tune.SUB / 2
    val here = cellOf(a)
    val c = cost[here]
    val speed = Tune.SPEED * Tune.COST_GRASS / (if (c > 0) c else Tune.COST_GRASS) * weatherWalk(here) / 100
    var dx = tx - px[a]
    var dy = ty - py[a]
    val s = if (dx != 0 && dy != 0) speed * 7 / 10 else speed
    dx = dx.coerceIn(-s, s)
    dy = dy.coerceIn(-s, s)
    px[a] += dx
    py[a] += dy
    if (px[a] == tx && py[a] == ty) {
        pathPos[a]++
        if (pathPos[a] >= path.size) {
            paths[a] = null
            state[a] = ST_IDLE
            arrived(a)
        }
    }
}

private fun World.arrived(a: Int) {
    val b = goalB[a]
    when (goal[a]) {
        GOAL_HOME, GOAL_WORK, GOAL_STORE -> { state[a] = ST_INSIDE; where[a] = b }
        GOAL_SRC -> {
            val r = taskR[a]
            val n = minOf(taskN[a], inv[b * R + r])
            inv[b * R + r] -= n
            reserved[b * R + r] -= taskN[a]
            carryR[a] = r; carryN[a] = n
            if (n <= 0) {
                incoming[taskDst[a] * R + r] -= taskN[a]
                clearTask(a)
            } else goTo(a, taskDst[a], GOAL_DST)
        }
        GOAL_DST -> {
            val r = taskR[a]
            // материалы улучшения (не дом) - в отдельный запас, остальное - в обычный
            var n = carryN[a]
            if (bUpg[b] == 1 && !def(b).isHouse) {
                val put = minOf(n, maxOf(0, upgradeCost(b, r) - upInv[b * R + r]))
                upInv[b * R + r] += put; n -= put
            }
            inv[b * R + r] += n
            incoming[b * R + r] -= taskN[a]
            noteCarried(a, r, carryN[a])
            wearCart(a)
            clearTask(a)
        }
        // по пути на стройку строитель мог снова стать носильщиком (стройки кончились) - тогда не строит
        GOAL_SITE -> if (job[a] == JOB_BUILDER) state[a] = ST_BUILD else goal[a] = GOAL_NONE
        GOAL_GATHER -> {
            val jb = jobB[a]
            if (job[a] == JOB_WORKER && jb >= 0 && bState[jb] == B_ACTIVE) {
                state[a] = ST_GATHER
                timer[a] = maxOf(1, def(jb).time * Tune.GATHER_LOAD * Tune.GATHER_WORK / agentSpeed(a, jb))
            } else gCell[a] = -1
        }
        GOAL_BACK -> {
            // добыча - в мастерскую (если её снесли - пропадает)
            if (bState[b] == B_ACTIVE && carryN[a] > 0) {
                inv[b * R + carryR[a]] += carryN[a]
                statProd[carryR[a]] += carryN[a]
            }
            carryN[a] = 0
            state[a] = ST_INSIDE; where[a] = b
        }
    }
    if (state[a] != ST_MOVE && state[a] != ST_WAIT_PATH && goal[a] != GOAL_SITE) goal[a] = GOAL_NONE
}

// ---------- решения ----------

internal fun World.think(a: Int) {
    when (state[a]) {
        ST_MOVE -> move(a)
        ST_WAIT_PATH -> {}
        ST_BUILD -> build(a)
        ST_GATHER -> gatherWork(a)
        else -> decide(a)
    }
}

private fun World.decide(a: Int) {
    if (timer[a] > 0) timer[a]--
    if (taskDst[a] >= 0) return
    if (carryN[a] > 0) {
        // добытчик сначала доносит добычу (и вечером); груз без дела (мастерскую снесли) пропадает
        if (job[a] == JOB_WORKER && jobB[a] >= 0 && bState[jobB[a]] == B_ACTIVE) { goTo(a, jobB[a], GOAL_BACK); return }
        carryN[a] = 0
    }
    if (isNight) {
        if (where[a] != home[a]) goTo(a, home[a], GOAL_HOME)
        return
    }
    when (job[a]) {
        JOB_WORKER -> if (def(jobB[a]).gatherCode != G_NONE) gatherDecide(a, jobB[a])
            else if (def(jobB[a]).distributes.isNotEmpty() && bState[jobB[a]] == B_ACTIVE && marketDeliver(a, jobB[a])) { }
            else if (where[a] != jobB[a]) goTo(a, jobB[a], GOAL_WORK)
        JOB_BUILDER -> {
            val s = siteFor(a)
            if (s >= 0) { jobB[a] = s; goTo(a, s, GOAL_SITE) }
            else if (where[a] != hall) goTo(a, hall, GOAL_STORE)
        }
        else -> if (state[a] == ST_IDLE) {
            // носильщик без дела на улице через время уходит на ближайший склад
            if (++idle[a] > Tune.IDLE_TO_STORE) {
                idle[a] = 0
                val s = nearestStore(cellOf(a))
                if (s >= 0) goTo(a, s, GOAL_STORE)
            }
        }
    }
}

/** Добытчик: мастерская работает и есть ресурс - идти к нему; иначе ждать в мастерской */
private fun World.gatherDecide(a: Int, b: Int) {
    if (timer[a] > 0 || bStatus[b] != BS_OK) {
        if (where[a] != b) goTo(a, b, GOAL_WORK)
        return
    }
    val c = findGather(a, b)
    if (c < 0) {
        timer[a] = Tune.RETRY
        if (where[a] != b) goTo(a, b, GOAL_WORK)
        return
    }
    gCell[a] = c
    goToCell(a, gStand[a], GOAL_GATHER)
}

/** Работа у ресурса: по окончании - добыча в руки и назад в мастерскую (лесник сразу идёт сажать дальше) */
private fun World.gatherWork(a: Int) {
    val b = jobB[a]
    if (job[a] != JOB_WORKER || b < 0 || bState[b] != B_ACTIVE) {
        state[a] = ST_IDLE; gCell[a] = -1; timer[a] = 0
        return
    }
    // вечер: засчитывается сделанная доля работы (целые циклы рецепта), остаток - пропадает
    val full = maxOf(1, def(b).time * Tune.GATHER_LOAD * Tune.GATHER_WORK / agentSpeed(a, b))
    if (!isNight && --timer[a] > 0) return
    val cycles = if (isNight) Tune.GATHER_LOAD * (full - timer[a]) / full else Tune.GATHER_LOAD
    val n = harvest(b, gCell[a], cycles)
    gCell[a] = -1; timer[a] = 0
    state[a] = ST_IDLE
    if (n > 0) {
        val def = def(b)
        wearTool(b, cycles * def.time)
        var r = 0
        for (k in 0 until R) if (def.output[k] > def.output[r]) r = k
        carryR[a] = r; carryN[a] = n
        goTo(a, b, GOAL_BACK)
    }
}

private fun World.nearestStore(cell: Int): Int {
    var best = -1; var bestD = Int.MAX_VALUE
    for (s in 0 until buildingCount) {
        if (bState[s] != B_ACTIVE || !def(s).isStore) continue
        val d = dist(cell, entrance(s))
        if (d < bestD) { bestD = d; best = s }
    }
    return best
}

/**
 * Стройка, где сейчас можно работать (материалов хватает на следующий шаг): срочная раньше; текущая остаётся, если нет
 * срочной с материалами; иначе первая по порядку (сначала срочная)
 */
private fun World.siteFor(a: Int): Int {
    val cur = jobB[a]
    val curSite = cur in 0 until buildingCount && bState[cur] == B_SITE
    if (curSite && bPrio[cur] == 1) return cur
    for (b in 0 until buildingCount) if (bState[b] == B_SITE && bPrio[b] == 1 && bBuild[b] < allowedBuild(b)) return b
    if (curSite) return cur
    var first = -1; var firstUrgent = -1
    for (b in 0 until buildingCount) {
        if (bState[b] != B_SITE) continue
        if (first < 0) first = b
        if (firstUrgent < 0 && bPrio[b] == 1) firstUrgent = b
        if (bBuild[b] < allowedBuild(b)) return b
    }
    return if (firstUrgent >= 0) firstUrgent else first
}

/** До какого хода можно дойти с уже привезёнными материалами */
private fun World.allowedBuild(b: Int): Int {
    val def = def(b)
    if (def.costTotal == 0) return def.build
    var delivered = 0
    for (r in 0 until R) delivered += minOf(inv[b * R + r], def.cost[r])
    return def.build * delivered / def.costTotal
}

private fun World.build(a: Int) {
    val s = goalB[a]
    if (bState[s] != B_SITE || isNight) {
        state[a] = ST_IDLE; goal[a] = GOAL_NONE
        if (bState[s] != B_SITE) jobB[a] = -1
        return
    }
    if (bBuild[s] < allowedBuild(s)) bBuild[s]++
    if (bBuild[s] >= def(s).build) complete(s)
}

/** Достроить за золото: носильщики с грузом сюда - отбой (груз - обратно источнику), материалы - сразу, работа - сделана */
internal fun World.rush(s: Int) {
    for (a in 0 until agentCount) if (taskDst[a] == s) cancelTask(a)
    for (r in 0 until R) inv[s * R + r] = maxOf(inv[s * R + r], def(s).cost[r])
    bBuild[s] = def(s).build
    complete(s)
}

private fun World.complete(s: Int) {
    bState[s] = B_ACTIVE
    bStatus[s] = BS_OK
    for (r in 0 until R) { statUse[r] += minOf(inv[s * R + r], def(s).cost[r]); inv[s * R + r] = 0 }
    event(EV_BUILT, s)
    if (def(s).border > 0) computeBorder()
    for (a in 0 until agentCount) {
        if (state[a] == ST_BUILD && goalB[a] == s) { state[a] = ST_IDLE; goal[a] = GOAL_NONE; jobB[a] = -1 }
    }
    if (bMoveFrom[s] >= 0) finishMove(s)
}

// ---------- производство ----------

internal fun World.produce() {
    java.util.Arrays.fill(totals, 0)
    for (s in 0 until buildingCount) if (bState[s] == B_ACTIVE && def(s).isStore) for (r in 0 until R) totals[r] += inv[s * R + r]
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE) continue
        val def = def(b)
        if (!def.isProducer) continue
        if (def.tools && bTool[b] <= 0) takeSpareTool(b)
        val gatherer = def.gatherCode != G_NONE
        // причины простоя - раньше «нет рабочих»: при них рабочие уходят носить (wantedWorkers)
        bStatus[b] = when {
            bFire[b] > 0 -> BS_FIRE
            flooded(b) -> BS_FLOOD
            bUnpaid[b] != 0 -> BS_UNPAID
            !def.seasons[season] -> BS_SEASON
            gatherer && bNear[b] == 0 -> BS_NO_RESOURCE
            !gatherer && def.needTile != null && bNear[b] < def.needMin -> BS_NO_RESOURCE
            limitReached(b) -> BS_LIMIT
            outputFull(b) -> if (outputFar(b)) BS_FAR else BS_OUTPUT_FULL
            missingInput(b) -> if (inputFar(b)) BS_FAR else BS_NO_INPUT
            bWorkers[b] == 0 -> BS_NO_WORKERS
            !gatherer && bInside[b] == 0 -> BS_WAITING
            else -> BS_OK
        }
        // добытчики производят ходками (gatherWork), а не в мастерской
        if (bStatus[b] != BS_OK || gatherer) continue
        // неурожай: сезонные поля и сады работают вполсилы до зимы
        if (badHarvest && !def.seasons[WINTER] && tick % 2 == 1) continue
        // выработка: плодородие / подземная вода (bYield), погода и паводок, учёные рабочие, уровень здания - проценты
        val yld = bYield[b] * weatherYield(b) / 100
        bWork[b] += (bInside[b] * 100 + bSkilled[b] * Tune.SKILL_BONUS) * yld / 100 * workSpeed(b) / 100
        if (bWork[b] >= def.time * 100) {
            bWork[b] = 0
            for (r in 0 until R) {
                inv[b * R + r] += def.output[r] - def.input[r]
                statProd[r] += def.output[r]; statUse[r] += def.input[r]
            }
            wearTool(b, def.time)
        }
    }
}

/** Готовому некуда; полная мастерская (рабочие ушли носить) снова работает, когда вывезут половину */
private fun World.outputFull(b: Int): Boolean {
    val def = def(b)
    val cap = if (bStatus[b] == BS_OUTPUT_FULL || bStatus[b] == BS_FAR) Tune.OUT_BUF / 2 else Tune.OUT_BUF
    for (r in 0 until R) if (def.output[r] > 0 && inv[b * R + r] + def.output[r] > cap) return true
    return false
}

/**
 * Лимит достигнут: свой лимит мастерской и лимит города на товар (меньший из двух); у мастерской с несколькими
 * товарами - когда упёрлись все. Уже стоящая на лимите ждёт, пока запас не упадёт на десятую часть (рабочие не мечутся)
 */
private fun World.limitReached(b: Int): Boolean {
    val def = def(b)
    var any = false
    for (r in 0 until R) {
        if (def.output[r] <= 0) continue
        val own = bLimit[b]; val town = townLimit[r]
        val base = if (own < 0) town else if (town < 0) own else minOf(own, town)
        if (base < 0) return false
        val lim = if (bStatus[b] == BS_LIMIT) base * 9 / 10 else base
        if (totals[r] + inv[b * R + r] < lim) return false
        any = true
    }
    return any
}

/** Готовое не вывозят, потому что до склада со свободным местом далеко */
private fun World.outputFar(b: Int): Boolean {
    for (r in 0 until R) {
        if (def(b).output[r] <= 0) continue
        val s = storeWithSpace(b, r)
        if (s >= 0 && dist(entrance(b), entrance(s)) > Tune.FAR) return true
    }
    return false
}

/** Сырьё есть на складах, но ближайший такой склад далеко */
private fun World.inputFar(b: Int): Boolean {
    val def = def(b)
    for (r in 0 until R) {
        if (inv[b * R + r] >= def.input[r]) continue
        val s = sourceWithStock(b, r)
        if (s >= 0 && dist(entrance(b), entrance(s)) > Tune.FAR) return true
    }
    return false
}

/** Сырья нет; мастерская без сырья (рабочие ушли носить) снова работает, когда сырья хватит на два раза */
private fun World.missingInput(b: Int): Boolean {
    val def = def(b)
    val k = if (bStatus[b] == BS_NO_INPUT) 2 else 1
    for (r in 0 until R) if (inv[b * R + r] < def.input[r] * k) return true
    return false
}
