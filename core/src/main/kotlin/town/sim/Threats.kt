package town.sim

/*
 * Угрозы (в песочнице включены по умолчанию, отключаются при создании партии): на рассвете зимой из леса за границей
 * может выйти стая волков, а с уровня Tune.BANDIT_LEVEL с края карты - шайка разбойников. Они идут к цели по земле
 * (вода, скалы, здания и стены непроходимы; жители ходят сквозь стены - там калитки), стража караульных вышек
 * и замка прогоняет их в своём радиусе. Дошли: волки режут скот (пастбище, свинарник) или ранят жителей дома (дом болеет)
 * и пугают соседей; разбойники уносят товары и золото со склада и могут поджечь здание. Стенами можно отрезать путь
 * совсем - тогда угроза уходит ни с чем.
 */

const val RAID_WOLVES = 1
const val RAID_BANDITS = 2

/** Рассвет: может ли сегодня прийти угроза (случайность - только через rng мира) */
internal fun World.threatsDawn() {
    if (!threatsOn || hall < 0 || level < Tune.RAID_LEVEL) return
    if (season == WINTER && rng.next(100) < Tune.WOLF_CHANCE) spawnRaid(RAID_WOLVES)
    if (level >= Tune.BANDIT_LEVEL && rng.next(100) < banditChance()) spawnRaid(RAID_BANDITS)
    // сценарий с заданием «отбить набеги»: разбойники приходят не реже раза в raidEvery суток (задание не ждёт удачи)
    else {
        val every = if (scenario >= 0) content.scenarios[scenario].raidEvery else 0
        if (every > 0 && day - raidLastDay >= every && (0 until Tune.MAX_QUESTS).any { qType[it] == town.data.Quest.DEFEND }) spawnRaid(RAID_BANDITS)
    }
}

/** Для съёмки экранов: выпустить угрозу вида type (как на рассвете, со случайной точкой и целью) */
fun World.debugRaid(type: Int) = spawnRaid(type)

/** Чем богаче казна, тем чаще разбойники */
fun World.banditChance() = minOf(Tune.BANDIT_CHANCE_MAX, Tune.BANDIT_CHANCE + gold / Tune.BANDIT_GOLD_STEP)

/** Проходима ли клетка для угрозы */
private fun World.raidPassable(c: Int): Boolean = when (tiles[c]) {
    GRASS, FOREST, ROAD, PAVED, BRIDGE -> true
    else -> false
}

private fun World.spawnRaid(type: Int) {
    var slot = -1
    for (i in 0 until Tune.MAX_RAIDS) if (rType[i] == 0) { slot = i; break }
    if (slot < 0) return
    val target = raidTarget(type)
    if (target < 0) return
    // откуда: волки - из леса за границей, разбойники - с края карты
    var start = -1
    for (k in 0 until 200) {
        val c = if (type == RAID_WOLVES) rng.next(w * h) else {
            val side = rng.next(4)
            when (side) {
                0 -> rng.next(w)
                1 -> (h - 1) * w + rng.next(w)
                2 -> rng.next(h) * w
                else -> rng.next(h) * w + w - 1
            }
        }
        val ok = if (type == RAID_WOLVES) tiles[c] == FOREST && !border[c] else raidPassable(c) && !border[c]
        if (ok) { start = c; break }
    }
    if (start < 0) return
    startRaid(slot, type, start, target)
}

/** Угроза вида type выходит из клетки start к зданию target (слот slot свободен); путь отрезан - уходит, false */
internal fun World.startRaid(slot: Int, type: Int, start: Int, target: Int): Boolean {
    raidsTotal++
    raidLastDay = day
    val path = raidPath(start, entrance(target))
    if (path == null) {
        // стены отрезали путь - угроза уходит ни с чем
        raidsStopped++
        event(EV_RAID_BLOCKED, -1, type)
        return false
    }
    rType[slot] = type
    rTarget[slot] = target
    rPath[slot] = path
    rPos[slot] = 0
    rX[slot] = (start % w) * Tune.SUB + Tune.SUB / 2
    rY[slot] = (start / w) * Tune.SUB + Tune.SUB / 2
    rCount[slot] = if (type == RAID_WOLVES) 3 else 3
    rVersion[slot] = raidVer()
    event(EV_RAID, target, type)
    return true
}

/** Куда идут: волки - к скоту (пастбище, свинарник), иначе к дому; разбойники - к складу или ратуше */
private fun World.raidTarget(type: Int): Int {
    val list = ArrayList<Int>()
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE) continue
        val d = def(b)
        val ok = if (type == RAID_WOLVES) d.id == "pasture" || d.id == "piggery" else d.isStore
        if (ok) list.add(b)
    }
    if (list.isEmpty() && type == RAID_WOLVES) for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).isHouse) list.add(b)
    if (list.isEmpty()) return -1
    return list[rng.next(list.size)]
}

/** Путь угрозы поиском в ширину по проходимой земле; null - не дойти (стены, вода) */
internal fun World.raidPath(from: Int, to: Int): IntArray? {
    if (from == to) return intArrayOf(to)
    val prev = IntArray(w * h) { -1 }
    val queue = IntArray(w * h)
    var head = 0; var tail = 0
    queue[tail++] = from; prev[from] = from
    while (head < tail) {
        val c = queue[head++]
        if (c == to) break
        val cx = c % w; val cy = c / w
        for (k in 0 until 4) {
            val nx = cx + (if (k == 0) 1 else if (k == 1) -1 else 0)
            val ny = cy + (if (k == 2) 1 else if (k == 3) -1 else 0)
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
            val n = ny * w + nx
            if (prev[n] >= 0 || (n != to && !raidPassable(n))) continue
            prev[n] = c
            queue[tail++] = n
        }
    }
    if (prev[to] < 0) return null
    val out = ArrayList<Int>()
    var c = to
    while (c != from) { out.add(c); c = prev[c] }
    out.reverse()
    return out.toIntArray()
}

/** Каждый тик: угрозы идут; стража в радиусе прогоняет; стену поставили на пути - новый путь или уход */
internal fun World.raidsStep() {
    for (i in 0 until Tune.MAX_RAIDS) {
        if (rType[i] == 0) continue
        val t = rTarget[i]
        if (t !in 0 until buildingCount || bState[t] != B_ACTIVE) { clearRaid(i); continue }
        if (tick % Tune.GUARD_INTERVAL == 0) {
            val g = guardNear(rX[i], rY[i])
            if (g >= 0) {
                raidsRepelled++
                event(EV_RAID_REPELLED, g, rType[i])
                clearRaid(i)
                continue
            }
        }
        // карта изменилась (стена, стройка) - путь заново от текущей клетки
        if (rVersion[i] != raidVer()) {
            rVersion[i] = raidVer()
            val here = (rY[i] / Tune.SUB) * w + rX[i] / Tune.SUB
            val p = raidPath(here, entrance(t))
            if (p == null) { raidsStopped++; event(EV_RAID_BLOCKED, -1, rType[i]); clearRaid(i); continue }
            rPath[i] = p; rPos[i] = 0
        }
        val path = rPath[i] ?: run { clearRaid(i); null } ?: continue
        if (rPos[i] >= path.size) { raidArrive(i); continue }
        val c = path[rPos[i]]
        val tx = (c % w) * Tune.SUB + Tune.SUB / 2; val ty = (c / w) * Tune.SUB + Tune.SUB / 2
        val dx = tx - rX[i]; val dy = ty - rY[i]
        val step = Tune.RAID_SPEED
        if (Math.abs(dx) <= step && Math.abs(dy) <= step) { rX[i] = tx; rY[i] = ty; rPos[i]++ }
        else {
            rX[i] += dx.coerceIn(-step, step)
            rY[i] += dy.coerceIn(-step, step)
        }
    }
}

/** Караул со стражей (или чудо-замок), в радиусе которого точка (x, y в 1/SUB клетки); -1 - нет */
internal fun World.guardNear(x: Int, y: Int): Int {
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE) continue
        val d = def(b)
        if (!d.isGuard || (bWorkers[b] == 0 && !d.wonder) || bUnpaid[b] != 0) continue
        // туман: караул видит хуже
        val r = (d.radius + Tune.LEVEL_GUARD * (bLevel[b] - 1) - (if (weather == W_FOG) Tune.FOG_GUARD else 0)) * Tune.SUB
        val cx = bX[b] * Tune.SUB + d.size * Tune.SUB / 2; val cy = bY[b] * Tune.SUB + d.size * Tune.SUB / 2
        val ddx = (x - cx).toLong(); val ddy = (y - cy).toLong()
        if (ddx * ddx + ddy * ddy <= r.toLong() * r) return b
    }
    return -1
}

private fun World.clearRaid(i: Int) {
    rType[i] = 0; rPath[i] = null; rPos[i] = 0; rTarget[i] = -1
}

/** Дошли до цели */
private fun World.raidArrive(i: Int) {
    val t = rTarget[i]
    val d = def(t)
    if (rType[i] == RAID_WOLVES) {
        if (d.isHouse) {
            if (bResidents[t] > 0) { bSick[t] = Tune.SICK_TICKS; event(EV_WOLVES, t, 1) }
        } else {
            // скот: что пастбище или свинарник успели вырастить
            var lost = 0
            for (r in 0 until R) if (d.output[r] > 0) { lost += inv[t * R + r]; inv[t * R + r] = 0 }
            event(EV_WOLVES, t, 0)
        }
        // соседи напуганы
        for (b in 0 until buildingCount) {
            if (bState[b] != B_ACTIVE || !def(b).isHouse) continue
            if (Math.abs(bX[b] - bX[t]) + Math.abs(bY[b] - bY[t]) <= Tune.FEAR_RADIUS) bHappy[b] = maxOf(0, bHappy[b] - Tune.FEAR_HAPPY)
        }
    } else {
        // разбойники: самое ценное со склада и часть казны
        var left = Tune.RAID_STEAL
        while (left > 0) {
            var best = -1; var bestV = 0
            for (r in 0 until R) {
                val v = minOf(inv[t * R + r] - reserved[t * R + r], left) * content.resources[r].price
                if (v > bestV) { bestV = v; best = r }
            }
            if (best < 0) break
            val k = minOf(inv[t * R + best] - reserved[t * R + best], left)
            inv[t * R + best] -= k; left -= k
        }
        val g = minOf(gold * Tune.RAID_GOLD_PCT / 100, Tune.RAID_GOLD_MAX)
        gold -= g
        event(EV_BANDITS, t, Tune.RAID_STEAL - left + g * 1000)
        // поджог: ближайшее к шайке здание (не ратуша)
        if (rng.next(100) < Tune.RAID_FIRE_CHANCE) {
            var best = -1; var bestD = Int.MAX_VALUE
            for (b in 0 until buildingCount) {
                if (bState[b] != B_ACTIVE || b == hall || bFire[b] != 0) continue
                val dd = Math.abs(bX[b] * Tune.SUB - rX[i]) + Math.abs(bY[b] * Tune.SUB - rY[i])
                if (dd < bestD) { bestD = dd; best = b }
            }
            if (best >= 0) startFire(best)
        }
    }
    clearRaid(i)
}

/** Пожар в здании (каланча рядом - тушат быстрее) */
internal fun World.startFire(b: Int) {
    val safe = served(b, "fire")
    bFireSafe[b] = if (safe) 1 else 0
    bFire[b] = if (safe) Tune.FIRE_TICKS_WATCH else Tune.FIRE_TICKS
    event(EV_FIRE, b, if (safe) 1 else 0)
}

// ---------- стены ----------

const val WALL_WOOD = 1
const val WALL_STONE = 2

/** Можно ли поставить стену: луг внутри границы, не вход здания, есть материал (каменная - с уровня WALL_STONE_LEVEL) */
fun World.canWall(x: Int, y: Int, kind: Int): Boolean {
    if (x !in 0 until w || y !in 0 until h) return false
    val c = y * w + x
    if (tiles[c] != GRASS || !border[c] || entranceOf[c] >= 0) return false
    if (kind == WALL_STONE && level < Tune.WALL_STONE_LEVEL) return false
    val (r, n) = wallCost(kind)
    return stockAvailable(r) >= n
}

/** Материал и количество на клетку стены */
fun World.wallCost(kind: Int): Pair<Int, Int> =
    if (kind == WALL_STONE) content.resource("stone").index to Tune.WALL_STONE_COST else content.resource("logs").index to Tune.WALL_WOOD_COST

internal fun World.buildWall(x: Int, y: Int, kind: Int) {
    if (!canWall(x, y, kind)) return
    val (r, n) = wallCost(kind)
    takeFromStores(r, n)
    tiles[y * w + x] = if (kind == WALL_STONE) WALL_S else WALL_W
    updateCost(y * w + x)
    mapVersion++
}

internal fun World.removeWall(x: Int, y: Int) {
    if (x !in 0 until w || y !in 0 until h) return
    val c = y * w + x
    if (tiles[c] != WALL_W && tiles[c] != WALL_S) return
    tiles[c] = GRASS
    updateCost(c)
    mapVersion++
}

/** Снять со складов n ресурса r (свободного, не забронированного) */
internal fun World.takeFromStores(r: Int, n: Int) {
    var left = n
    for (s in 0 until buildingCount) {
        if (left == 0) break
        if (bState[s] != B_ACTIVE || !def(s).isStore) continue
        val k = minOf(left, inv[s * R + r] - reserved[s * R + r])
        if (k > 0) { inv[s * R + r] -= k; left -= k }
    }
}
