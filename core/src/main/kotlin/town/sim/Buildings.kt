package town.sim

/*
 * Снос и перенос зданий. Площадка сносится с полным возвратом (привезённые материалы и золото),
 * готовое здание - с возвратом Tune.REFUND процентов стоимости и всех запасов на склады.
 * Перенос: на новом месте строится площадка, старое здание работает до её готовности, потом жители,
 * рабочие и запасы переезжают, а старое сносится с частичным возвратом. Своих номеров здания
 * после сноса не теряют сразу - номер помечается B_GONE и отдаётся следующей постройке.
 */

/** Снести здание b; repl >= 0 - здание, куда переезжает всё (перенос) */
internal fun World.demolish(b: Int, repl: Int) {
    val def = def(b)
    val wasSite = bState[b] == B_SITE
    bState[b] = B_GONE   // сразу: поиски складов и домов ниже его уже не видят

    // носильщики, чьи заявки касаются этого здания
    for (a in 0 until agentCount) {
        if (taskDst[a] < 0) continue
        val r = taskR[a]
        if (taskDst[a] == b) {
            if (carryN[a] > 0) {
                val s = if (repl >= 0) repl else storeWithSpace(b, r)
                if (s >= 0) {
                    taskDst[a] = s
                    incoming[s * R + r] += taskN[a]
                    goTo(a, s, GOAL_DST)
                } else { clearTask(a); stopAgent(a) }
            } else {
                reserved[taskSrc[a] * R + r] -= taskN[a]
                clearTask(a); stopAgent(a)
            }
        } else if (taskSrc[a] == b && carryN[a] == 0) {
            if (repl >= 0) {
                taskSrc[a] = repl
                reserved[repl * R + r] += taskN[a]
                goTo(a, repl, GOAL_SRC)
            } else {
                incoming[taskDst[a] * R + r] -= taskN[a]
                clearTask(a); stopAgent(a)
            }
        }
    }
    // остальные: кто шёл сюда, был внутри, работал или строил
    for (a in 0 until agentCount) {
        if (where[a] == b) { setPos(a, entrance(b)); where[a] = -1; state[a] = ST_IDLE; goal[a] = GOAL_NONE }
        if (goalB[a] == b && goal[a] != GOAL_NONE && taskDst[a] < 0) stopAgent(a)
        if (job[a] == JOB_WORKER && jobB[a] == b) {
            if (repl >= 0) jobB[a] = repl else { job[a] = JOB_CARRIER; jobB[a] = -1 }
        }
        if (job[a] == JOB_BUILDER && jobB[a] == b) jobB[a] = -1
    }
    if (repl >= 0) {
        bWorkers[repl] = bWorkers[b]; bWanted[repl] = bWanted[b]; bLimit[repl] = bLimit[b]; bHappy[repl] = bHappy[b]
        bLevel[repl] = bLevel[b]
        for (r in 0 until R) bAccept[repl * R + r] = bAccept[b * R + r]
        // дети переезжают с семьёй
        for (k in 0 until Tune.KIDS_MAX) {
            kidAge[repl * Tune.KIDS_MAX + k] = kidAge[b * Tune.KIDS_MAX + k]
            kidSchool[repl * Tune.KIDS_MAX + k] = kidSchool[b * Tune.KIDS_MAX + k]
        }
    }
    for (k in 0 until Tune.KIDS_MAX) { kidAge[b * Tune.KIDS_MAX + k] = 0; kidSchool[b * Tune.KIDS_MAX + k] = 0 }
    bWorkers[b] = 0

    // запасы и возврат стоимости
    for (r in 0 until R) {
        val k = b * R + r
        var n = inv[k]
        if (!wasSite) n += def.cost[r] * Tune.REFUND / 100
        if (repl >= 0) { inv[repl * R + r] += inv[k]; n -= inv[k] }
        putToStores(b, r, n)
        inv[k] = 0; incoming[k] = 0; reserved[k] = 0
    }
    if (wasSite && bMoveFrom[b] < 0) gold += def.gold
    for (s in 0 until buildingCount) if (bMoveFrom[s] == b) bMoveFrom[s] = -1
    bMoveFrom[b] = -1

    // жильцы: в новый дом, в другой дом со свободной кроватью или из города
    for (a in agentCount - 1 downTo 0) {
        if (home[a] != b) continue
        val h = if (repl >= 0) repl else houseWithBed()
        if (h >= 0) { home[a] = h; bResidents[h]++; bResidents[b]-- } else removeCitizen(a)
    }
    bResidents[b] = 0

    // клетки освобождаются
    for (yy in bY[b] until bY[b] + def.size) for (xx in bX[b] until bX[b] + def.size) {
        val i = yy * w + xx
        tiles[i] = GRASS
        buildingAt[i] = -1
        updateCost(i)
    }
    entranceOf[entrance(b)] = -1
    computeRegions()
    computeBorder()
}

/** Перенос готов: площадка s стала зданием, переезжаем из старого */
internal fun World.finishMove(s: Int) {
    val o = bMoveFrom[s]
    bMoveFrom[s] = -1
    if (o < 0 || bState[o] != B_ACTIVE) return
    bPrio[s] = bPrio[o]; bTool[s] = bTool[o]
    demolish(o, s)
}

private fun World.stopAgent(a: Int) {
    if (state[a] == ST_MOVE || state[a] == ST_WAIT_PATH || state[a] == ST_BUILD) state[a] = ST_IDLE
    paths[a] = null
    goal[a] = GOAL_NONE
}

/** Положить n единиц ресурса r на ближайшие к зданию b склады со свободным местом; что не влезло - пропадает */
private fun World.putToStores(b: Int, r: Int, n: Int) {
    var left = n
    while (left > 0) {
        val s = storeWithSpace(b, r)
        if (s < 0) return
        val k = minOf(left, space(s))
        inv[s * R + r] += k
        left -= k
    }
}

// ---------- сроки стройки и улучшения (оценка для игрока, тики при x1) ----------

/**
 * Сколько строится новое здание: ожидание очереди и подвоз материалов носильщиками (ETA_UNIT тиков на единицу)
 * плюс работа строителей (ETA_WORK процентов от def.build). Коэффициенты подобраны по замерам бота
 * (`:core:balance`, build/balance/times.txt - медианы по 12 картам): дом ~1 мин, церковь ~1 мин 45 с.
 */
fun World.buildEta(def: town.data.BuildingDef): Int =
    Tune.ETA_BASE + def.costTotal * Tune.ETA_UNIT + def.build * Tune.ETA_WORK / 100

/** Сколько осталось строить площадку b: недовезённые материалы и оставшаяся работа */
fun World.siteEta(b: Int): Int {
    val def = def(b)
    var missing = 0
    for (r in 0 until R) missing += maxOf(0, def.cost[r] - inv[b * R + r])
    val work = (def.build - bBuild[b]) * Tune.ETA_WORK / 100
    val wait = if (def.costTotal > 0) Tune.ETA_BASE * missing / def.costTotal else 0
    return wait + missing * Tune.ETA_UNIT + work
}

/** Сколько займёт улучшение b (заказ - готово): только подвоз материалов; started - уже идёт, считать недовезённое */
fun World.upgradeEta(b: Int, started: Boolean): Int {
    var missing = 0
    for (r in 0 until R) {
        val got = if (!started) 0 else if (def(b).isHouse) inv[b * R + r] else upInv[b * R + r]
        missing += maxOf(0, upgradeCost(b, r) - got)
    }
    return (if (missing > 0) Tune.ETA_UPG_BASE else 0) + missing * Tune.ETA_UPG_UNIT
}
