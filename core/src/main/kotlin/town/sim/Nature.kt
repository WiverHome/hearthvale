package town.sim

/*
 * Природа: ресурсы на клетках, их истощение и восстановление, плодородие и подземная вода.
 * - лес: на зрелом дереве res = TREE_LOGS брёвен; срубили - пень (луг, grow = дней до исчезновения);
 *   саженец - лес с res = 0 и grow = дней до зрелости; лес сам разрастается на соседний луг, лесник сажает;
 * - скала: res = камня; выбрали до конца - на её месте луг;
 * - вода: косяки рыбы у берега - res рыбы, resMax - сколько восстанавливается;
 * - дичь: game в густом лесу, восстанавливается;
 * - плодородие и подземная вода (0..100) - из сида, не сохраняются: выработка фермы, сада, пастбища, колодца 70..130%.
 * Добытчики (gather в content.json) ходят к ресурсу: см. Citizens.kt (GOAL_GATHER, ST_GATHER).
 * Всё на целых, случайность - только rng мира, обход клеток по возрастанию номера.
 */

// Что добывает здание (BuildingDef.gather)
const val G_NONE = 0
const val G_WOOD = 1
const val G_STONE = 2
const val G_FISH = 3
const val G_GAME = 4
const val G_PLANT = 5   // лесник: сажает деревья

fun gatherCode(name: String?): Int = when (name) {
    "wood" -> G_WOOD
    "stone" -> G_STONE
    "fish" -> G_FISH
    "game" -> G_GAME
    "plant" -> G_PLANT
    else -> G_NONE
}

/** Ресурсы на карте: генерация после рельефа (деревья зрелые, скалы разного запаса, косяки у берега, дичь) */
internal fun World.generateNature() {
    val area = w * h
    for (i in 0 until area) {
        when (tiles[i]) {
            FOREST -> res[i] = Tune.TREE_LOGS
            ROCK -> res[i] = Tune.ROCK_MIN + rng.next(Tune.ROCK_MAX - Tune.ROCK_MIN + 1)
        }
    }
    // косяки: клетка воды у берега и вода вокруг неё; первый - у ближайшего к центру карты берега
    // (ратуша в центре - рыбалка доступна с начала на любой карте), остальные - где придётся
    fun shoal(c: Int) {
        val cx = c % w; val cy = c / w
        for (y in cy - 1..cy + 1) for (x in cx - 1..cx + 1) {
            if (x !in 0 until w || y !in 0 until h) continue
            val i = y * w + x
            if (tiles[i] == WATER) { resMax[i] = Tune.FISH_MAX; res[i] = Tune.FISH_MAX }
        }
    }
    var first = -1; var firstD = Int.MAX_VALUE
    for (c in 0 until area) {
        if (tiles[c] != WATER || !nearLand(c)) continue
        val d = Math.abs(c % w - w / 2) + Math.abs(c / w - h / 2)
        if (d < firstD) { firstD = d; first = c }
    }
    if (first >= 0) shoal(first)
    var placed = if (first >= 0) 1 else 0
    var tries = 0
    val want = World.scale(Tune.FISH_SHOALS, area)
    while (placed < want && tries < want * 40) {
        tries++
        val c = rng.next(area)
        if (tiles[c] != WATER || !nearLand(c) || resMax[c] > 0) continue
        shoal(c)
        placed++
    }
    // дичь - в густом лесу
    for (i in 0 until area) if (tiles[i] == FOREST && forestAround(i) >= 5) game[i] = rng.next(Tune.GAME_MAX + 1)
    computeSoil()
}

/** Есть ли рядом (по 4 соседям) проходимая суша */
internal fun World.nearLand(c: Int): Boolean {
    val x = c % w; val y = c / w
    return (x > 0 && cost[c - 1] > 0) || (x < w - 1 && cost[c + 1] > 0) || (y > 0 && cost[c - w] > 0) || (y < h - 1 && cost[c + w] > 0)
}

/** Леса среди 8 соседей */
internal fun World.forestAround(c: Int): Int {
    val x = c % w; val y = c / w
    var n = 0
    for (dy in -1..1) for (dx in -1..1) {
        if (dx == 0 && dy == 0) continue
        val xx = x + dx; val yy = y + dy
        if (xx in 0 until w && yy in 0 until h && tiles[yy * w + xx] == FOREST) n++
    }
    return n
}

/**
 * Расстояние каждой клетки до воды по сторонам (мост - тоже вода: на месте моста была река), не дальше maxD;
 * дальше - больше maxD
 */
internal fun World.waterDistance(maxD: Int): IntArray {
    val dist = IntArray(w * h) { if (tiles[it] == WATER || tiles[it] == BRIDGE) 0 else 99 }
    repeat(maxD) {
        for (y in 0 until h) for (x in 0 until w) {
            val c = y * w + x
            var m = dist[c]
            if (x > 0) m = minOf(m, dist[c - 1] + 1)
            if (x < w - 1) m = minOf(m, dist[c + 1] + 1)
            if (y > 0) m = minOf(m, dist[c - w] + 1)
            if (y < h - 1) m = minOf(m, dist[c + w] + 1)
            dist[c] = m
        }
    }
    return dist
}

/** Плодородие и подземная вода из сида (без rng мира - карта и ход партии не меняются): плавный шум, у воды - больше */
internal fun World.computeSoil() {
    val dist = waterDistance(6)
    for (y in 0 until h) for (x in 0 until w) {
        val c = y * w + x
        val wet = maxOf(0, 6 - dist[c]) * 5   // у воды до +30
        fert[c] = (20 + valueNoise(x, y, 9, seed * 31 + 7) * 70 / 1000 + wet).coerceIn(10, 100)
        ground[c] = (15 + valueNoise(x, y, 7, seed * 17 + 3) * 65 / 1000 + wet).coerceIn(10, 100)
    }
}

/** Плавный шум 0..1000 по решётке с шагом step (целочисленно; значения в узлах - из хеша) */
private fun valueNoise(x: Int, y: Int, step: Int, salt: Int): Int {
    val x0 = Math.floorDiv(x, step); val y0 = Math.floorDiv(y, step)
    val tx = (x - x0 * step) * 1000 / step; val ty = (y - y0 * step) * 1000 / step
    fun v(i: Int, j: Int): Int {
        var h = i * 374761393 + j * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h ushr 8) and 0x3ff) * 1000 / 1023
    }
    // сглаживание 3t^2 - 2t^3 в тысячных
    fun s(t: Int) = (t * t / 1000) * (3000 - 2 * t) / 1000
    val sx = s(tx); val sy = s(ty)
    val a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * sx / 1000
    val b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * sx / 1000
    return a + (b - a) * sy / 1000
}

/** Выработка здания в процентах: плодородие (ферма, сад, пастбище) или подземная вода (колодец) под ним и вокруг */
internal fun World.yieldFor(defIndex: Int, x: Int, y: Int): Int {
    val def = defs[defIndex]
    val field = when (def.soil) { "fertility" -> fert; "groundwater" -> ground; else -> return 100 }
    var sum = 0; var n = 0
    for (yy in y - 1..y + def.size) for (xx in x - 1..x + def.size) {
        if (xx !in 0 until w || yy !in 0 until h) continue
        sum += field[yy * w + xx]; n++
    }
    val avg = if (n > 0) sum / n else 50
    // 70% на бедной земле (10) .. 130% на лучшей (100)
    return 70 + (avg - 10) * 60 / 90
}

/** Утро: деревья растут, пни гниют, лес разрастается, рыба и дичь восстанавливаются */
internal fun World.natureDawn() {
    var changed = false
    for (i in 0 until w * h) {
        when (tiles[i]) {
            FOREST -> {
                if (res[i] == 0 && grow[i] > 0 && --grow[i] == 0) res[i] = Tune.TREE_LOGS
                if (res[i] > 0 && game[i] < Tune.GAME_MAX && forestAround(i) >= 5 && rng.next(100) < Tune.GAME_REGEN) game[i]++
            }
            GRASS -> {
                if (grow[i] > 0) { grow[i]--; continue }
                if (buildingAt[i] >= 0 || entranceOf[i] >= 0 || nearBuilding(i)) continue
                if (forestAround(i) >= 4 && rng.next(1000) < Tune.FOREST_SPREAD) { plant(i); changed = true }
            }
            WATER -> if (res[i] < resMax[i]) res[i] = minOf(resMax[i], res[i] + Tune.FISH_REGEN)
        }
    }
    if (changed) mapVersion++
}

/** Рядом (8 соседей) здание или вход - там лес сам не растёт */
private fun World.nearBuilding(c: Int): Boolean {
    val x = c % w; val y = c / w
    for (dy in -1..1) for (dx in -1..1) {
        val xx = x + dx; val yy = y + dy
        if (xx !in 0 until w || yy !in 0 until h) continue
        val i = yy * w + xx
        if (buildingAt[i] >= 0 || entranceOf[i] >= 0 || tiles[i] == ROAD || tiles[i] == PAVED) return true
    }
    return false
}

/** Посадить саженец на луг */
internal fun World.plant(i: Int) {
    tiles[i] = FOREST
    res[i] = 0
    grow[i] = Tune.TREE_GROW_DAYS
    updateCost(i)
}

/** Есть ли на клетке ресурс для добытчика вида g */
internal fun World.hasResource(i: Int, g: Int): Boolean = when (g) {
    G_WOOD -> tiles[i] == FOREST && res[i] > 0
    G_STONE -> tiles[i] == ROCK && res[i] > 0
    G_FISH -> tiles[i] == WATER && res[i] > 0
    G_GAME -> tiles[i] == FOREST && game[i] > 0
    G_PLANT -> tiles[i] == GRASS && grow[i] == 0 && buildingAt[i] < 0 && entranceOf[i] < 0 && !nearBuilding(i)
    else -> false
}

/** Сколько клеток с ресурсом в радиусе здания (если поставить его в (x, y)), до которых можно дойти */
internal fun World.resourceCells(defIndex: Int, x: Int, y: Int): Int = scanGather(defIndex, x, y, -1, -1)

/**
 * Ресурс вокруг здания defIndex с углом (x, y), до которого можно дойти от входа (x, y - 1): обход в ширину по
 * проходимым клеткам окрестности - не на тот берег реки через брод. Место, где встать: лес и луг - сама клетка,
 * скала и вода - соседняя суша. Если a >= 0 - выбрать ближайшую по шагам клетку, не занятую другим рабочим
 * здания b (место пишется в gStand[a]) и вернуть её; иначе вернуть, сколько таких клеток.
 */
internal fun World.scanGather(defIndex: Int, x: Int, y: Int, a: Int, b: Int, radius: Int = defs[defIndex].needRadius): Int {
    val def = defs[defIndex]
    val g = def.gatherCode
    val e = (y - 1) * w + x
    val cx = x + def.size / 2; val cy = y + def.size / 2
    val r = radius + 2
    val x0 = maxOf(0, cx - r); val y0 = maxOf(0, cy - r)
    val x1 = minOf(w - 1, cx + r); val y1 = minOf(h - 1, cy + r)
    val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
    if (y < 1 || e % w !in x0..x1 || e / w !in y0..y1) return if (a >= 0) -1 else 0
    // шагов от входа до каждой проходимой клетки окрестности
    val dist = IntArray(bw * bh) { -1 }
    val queue = IntArray(bw * bh)
    var head = 0; var tail = 0
    val e0 = (e / w - y0) * bw + (e % w - x0)
    dist[e0] = 0; queue[tail++] = e0
    while (head < tail) {
        val q = queue[head++]
        val qx = q % bw; val qy = q / bw
        for (k in 0 until 4) {
            val nx = qx + (if (k == 0) 1 else if (k == 1) -1 else 0)
            val ny = qy + (if (k == 2) 1 else if (k == 3) -1 else 0)
            if (nx !in 0 until bw || ny !in 0 until bh) continue
            val n = ny * bw + nx
            if (dist[n] >= 0 || cost[(ny + y0) * w + nx + x0] == 0) continue
            dist[n] = dist[q] + 1
            queue[tail++] = n
        }
    }
    var count = 0
    var best = -1; var bestStand = -1; var bestD = Int.MAX_VALUE
    for (yy in cy - radius..cy + radius) for (xx in cx - radius..cx + radius) {
        if (xx !in 0 until w || yy !in 0 until h) continue
        val i = yy * w + xx
        if (!hasResource(i, g)) continue
        // место: сама клетка (проходимая) или ближайшая по шагам соседняя суша
        var stand = -1; var sd = Int.MAX_VALUE
        for (k in 0 until 5) {
            if (k > 0 && cost[i] > 0) break
            val nx = xx + (if (k == 1) 1 else if (k == 2) -1 else 0)
            val ny = yy + (if (k == 3) 1 else if (k == 4) -1 else 0)
            val lx = nx - x0; val ly = ny - y0
            if (lx !in 0 until bw || ly !in 0 until bh) continue
            val d = dist[ly * bw + lx]
            if (d >= 0 && d < sd) { sd = d; stand = ny * w + nx }
        }
        if (stand < 0) continue
        count++
        if (a < 0 || sd >= bestD || claimed(a, b, i)) continue
        best = i; bestStand = stand; bestD = sd
    }
    if (a < 0) return count
    if (best >= 0) gStand[a] = bestStand
    return best
}

/** Куда идти добытчику a здания b (клетка ресурса, место - в gStand[a]) или -1 */
internal fun World.findGather(a: Int, b: Int): Int = scanGather(bDef[b], bX[b], bY[b], a, b, gatherRadius(b))

/** Клетку уже выбрал другой добытчик (любого здания) */
private fun World.claimed(a: Int, b: Int, i: Int): Boolean {
    for (o in 0 until agentCount) if (o != a && job[o] == JOB_WORKER && gCell[o] == i && (state[o] == ST_GATHER || goal[o] == GOAL_GATHER)) return true
    return false
}

/**
 * Работа на клетке закончена: взять ресурс (до GATHER_LOAD циклов рецепта) или посадить дерево.
 * Возвращает, сколько единиц выхода рабочий несёт в мастерскую.
 */
internal fun World.harvest(b: Int, i: Int, cycles: Int = Tune.GATHER_LOAD): Int {
    val def = def(b)
    val g = def.gatherCode
    if (cycles <= 0 || !hasResource(i, g)) return 0
    val per = def.output.maxOrNull() ?: 0
    when (g) {
        G_PLANT -> { plant(i); mapVersion++; return 0 }
        G_GAME -> {
            val take = minOf(cycles, game[i])
            game[i] -= take
            return take * per
        }
        else -> {
            val take = minOf(cycles, res[i])
            res[i] -= take
            if (res[i] == 0) when (g) {
                G_WOOD -> { tiles[i] = GRASS; grow[i] = Tune.STUMP_DAYS; game[i] = 0; updateCost(i); mapVersion++ }
                G_STONE -> { tiles[i] = GRASS; updateCost(i); computeRegions(); mapVersion++ }
            }
            return take * per
        }
    }
}

/** Пересчёт «сколько ресурса рядом» у добытчиков (раз в NEAR_INTERVAL тиков и после загрузки) */
internal fun World.updateNear() {
    for (b in 0 until buildingCount) {
        if (bState[b] == B_GONE) continue
        val def = def(b)
        if (def.gatherCode != G_NONE) bNear[b] = scanGather(def.index, bX[b], bY[b], -1, -1, gatherRadius(b))
    }
}
