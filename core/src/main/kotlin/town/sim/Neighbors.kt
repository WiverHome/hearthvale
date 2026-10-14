package town.sim

/*
 * Соседи: 2-3 села за краями карты (по сиду). У каждого есть товар в избытке и товар в нужде, отношения 0..100.
 * Раз в неделю сосед предлагает два договора: «N нашего товара за M своего» и «N товара за золото». Договор
 * заключается через торговый пост (serves: trade): носильщики свозят туда товар, в день поставки повозка уезжает
 * от поста к краю карты, через WAGON_AWAY тиков возвращается с оплатой (товар - в пост, оттуда на склад; золото -
 * в казну). Поставка в срок - отношения растут, сорвана (товара нет, поста нет) - падают, CONTRACT_FAILS срывов
 * подряд - договор расторгнут. С хорошими отношениями сосед платит больше и берёт больше.
 */

/** Товары, которыми торгуют соседи: с ценой, кроме воды, тачек и телег */
private fun World.tradeable(r: Int): Boolean {
    val res = content.resources[r]
    return res.price >= 2 && res.id != "barrow" && res.id != "wagon"
}

/** Соседи при создании партии: стороны карты, имена, избыток и нужда (rng мира) */
internal fun World.setupNeighbors() {
    val pool = ArrayList<Int>()
    for (r in 0 until R) if (tradeable(r)) pool.add(r)
    if (pool.size < 2) return
    nbCount = Tune.NB_MIN + rng.next(Tune.NB_MAX - Tune.NB_MIN + 1)
    val sides = intArrayOf(0, 1, 2, 3)
    for (i in 3 downTo 1) { val j = rng.next(i + 1); val t = sides[i]; sides[i] = sides[j]; sides[j] = t }
    val nameBase = rng.next(1000)
    for (i in 0 until nbCount) {
        nbSide[i] = sides[i]
        nbName[i] = nameBase + i * 5
        nbSurplus[i] = pool[rng.next(pool.size)]
        var need = pool[rng.next(pool.size)]
        if (need == nbSurplus[i]) need = pool[(pool.indexOf(need) + 1 + rng.next(pool.size - 1)) % pool.size]
        nbNeed[i] = need
        nbRel[i] = Tune.REL_START
        ctGiveR[i] = -1; ctGetR[i] = -1; wgState[i] = 0; wgPost[i] = -1; wgGetR[i] = -1
    }
    offersDay = 0
    makeOffers()
}

/** Надбавка соседа за отношения, процентов */
fun World.relBonus(i: Int) = nbRel[i] / Tune.REL_BONUS_DIV

/** Новые предложения всех соседей (раз в CONTRACT_DAYS суток) */
internal fun World.makeOffers() {
    // что город делал за последние сутки - сосед просит то, что у нас есть
    val made = ArrayList<Int>()
    val vital = BooleanArray(R)
    for (n in content.needs) if (n.vital) for (g in n.goods) vital[g] = true
    for (r in 0 until R) if (tradeable(r) && !vital[r] && (produced(r, 1) > 0 || produced(r, 2) > 0)) made.add(r)
    for (i in 0 until nbCount) {
        val good = nbRel[i] >= Tune.REL_GOOD
        fun amount(): Int {
            var n = Tune.OFFER_N_MIN + rng.next(Tune.OFFER_N_MAX - Tune.OFFER_N_MIN + 1)
            if (good) n += n * Tune.REL_GOOD_MORE / 100
            return maxOf(Tune.OFFER_STEP, n / Tune.OFFER_STEP * Tune.OFFER_STEP)
        }
        // 0: наш товар (что им нужно) за их товар
        val n0 = amount()
        val give0 = nbNeed[i]
        val value0 = n0 * content.resources[give0].price * (Tune.OFFER_GAIN + relBonus(i)) / 100
        ofGiveR[i * 2] = give0; ofGiveN[i * 2] = n0
        ofGetR[i * 2] = nbSurplus[i]; ofGetN[i * 2] = maxOf(1, value0 / content.resources[nbSurplus[i]].price)
        // 1: что город делает - за золото
        val n1 = amount()
        val give1 = if (made.isNotEmpty()) made[rng.next(made.size)] else nbNeed[i]
        ofGiveR[i * 2 + 1] = give1; ofGiveN[i * 2 + 1] = n1
        ofGetR[i * 2 + 1] = -1; ofGetN[i * 2 + 1] = n1 * content.resources[give1].price * (Tune.OFFER_GOLD + relBonus(i)) / 100
    }
    offersDay = day + Tune.CONTRACT_DAYS
}

/** Работающий торговый пост (готов, есть работники, не горит, не в воде, оплачен) или -1 */
fun World.tradePost(): Int {
    for (b in 0 until buildingCount) {
        if (bState[b] == B_ACTIVE && def(b).serves == "trade" && bWorkers[b] > 0 && bFire[b] == 0 && bUnpaid[b] == 0 && !flooded(b)) return b
    }
    return -1
}

/** Пост, где копится товар для договоров и откуда уходит повозка: всегда первый готовый (второй пост товар не делит) */
internal fun World.tradeStore(): Int {
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).serves == "trade") return b
    return -1
}

/** Есть ли готовый торговый пост (можно заключать договоры) */
fun World.hasTradePost(): Boolean {
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).serves == "trade") return true
    return false
}

fun World.contracts(): Int {
    var n = 0
    for (i in 0 until nbCount) if (ctGiveR[i] >= 0) n++
    return n
}

/** Можно ли заключить договор по предложению k соседа i; null - можно, иначе ключ строки причины */
fun World.contractBlock(i: Int, k: Int): String? = when {
    i !in 0 until nbCount || k !in 0..1 || ofGiveR[i * 2 + k] < 0 -> "nb_no_offer"
    !hasTradePost() -> "nb_need_post"
    ctGiveR[i] >= 0 -> "nb_has_contract"
    contracts() >= Tune.CONTRACTS_MAX -> "nb_too_many"
    else -> null
}

internal fun World.signContract(i: Int, k: Int) {
    if (contractBlock(i, k) != null) return
    val o = i * 2 + k
    ctGiveR[i] = ofGiveR[o]; ctGiveN[i] = ofGiveN[o]; ctGetR[i] = ofGetR[o]; ctGetN[i] = ofGetN[o]
    ctDue[i] = day + Tune.CONTRACT_FIRST
    ctFails[i] = 0
    ctLate[i] = 0
    ofGiveR[o] = -1
    event(EV_CONTRACT, i)
}

internal fun World.cancelContract(i: Int) {
    if (i !in 0 until nbCount || ctGiveR[i] < 0) return
    ctGiveR[i] = -1
    nbRel[i] = maxOf(0, nbRel[i] - Tune.REL_FAIL / Tune.REL_CANCEL_DIV)
}

/** Сколько товара r торговый пост держит для договоров */
fun World.tradeKeep(r: Int): Int {
    var n = 0
    for (i in 0 until nbCount) if (ctGiveR[i] == r) n += ctGiveN[i]
    return n
}

/** Рассвет: новые предложения раз в неделю, поставки по договорам */
internal fun World.neighborsDawn() {
    if (nbCount == 0) return
    if (day >= offersDay) makeOffers()
    for (i in 0 until nbCount) {
        if (ctGiveR[i] < 0 || day < ctDue[i]) continue
        if (wgState[i] != 0) { ctDue[i] = day + 1; continue }   // повозка ещё в пути - завтра
        val p = tradeStore()
        val r = ctGiveR[i]
        // пост горит или в воде - поставка завтра, но не больше CONTRACT_LATE раз (потом - срыв)
        val ready = p >= 0 && available(p, r) >= ctGiveN[i]
        val blocked = p >= 0 && (bFire[p] > 0 || flooded(p))
        if (blocked && ctLate[i] < Tune.CONTRACT_LATE) { ctLate[i]++; ctDue[i] = day + 1; continue }
        ctLate[i] = 0
        if (ready && !blocked) {
            inv[p * R + r] -= ctGiveN[i]
            statUse[r] += ctGiveN[i]
            wgGetR[i] = ctGetR[i]; wgGetN[i] = ctGetN[i]
            ctDue[i] = day + Tune.CONTRACT_DAYS
            ctFails[i] = 0
            wagonOut(i, p)
            event(EV_WAGON_OUT, i)
        } else {
            ctFails[i]++
            nbRel[i] = maxOf(0, nbRel[i] - Tune.REL_FAIL)
            ctDue[i] = day + Tune.CONTRACT_DAYS
            val cancel = ctFails[i] >= Tune.CONTRACT_FAILS
            if (cancel) ctGiveR[i] = -1
            event(EV_CONTRACT_FAIL, i, if (cancel) 1 else 0)
        }
    }
}

/** Клетка края карты со стороны соседа i, ближайшая к клетке from и проходимая; -1 - нет */
private fun World.edgeCell(i: Int, from: Int): Int {
    val fx = from % w; val fy = from / w
    val along = if (nbSide[i] == 0 || nbSide[i] == 2) w else h
    val start = if (nbSide[i] == 0 || nbSide[i] == 2) fx else fy
    for (d in 0 until along) for (sgn in intArrayOf(1, -1)) {
        val t = start + d * sgn
        if (t !in 0 until along) continue
        val c = when (nbSide[i]) { 0 -> t; 1 -> t * w + w - 1; 2 -> (h - 1) * w + t; else -> t * w }
        if (cost[c] > 0) return c
    }
    return -1
}

private fun World.wagonOut(i: Int, p: Int) {
    wgPost[i] = p
    val e = entrance(p)
    wgX[i] = (e % w) * Tune.SUB + Tune.SUB / 2; wgY[i] = (e / w) * Tune.SUB + Tune.SUB / 2
    val edge = edgeCell(i, e)
    val path = if (edge >= 0) findPath(e, edge) else null
    wgPath[i] = path; wgPos[i] = 0
    // дороги к краю нет (вода, скалы) - повозка сразу «за краем»
    if (path == null || path.isEmpty()) { wgState[i] = 2; wgTimer[i] = Tune.WAGON_AWAY; wgPath[i] = null } else wgState[i] = 1
}

/** Только для съёмки экранов: повозка к соседу i выезжает от поста и проезжает ticks тиков */
fun World.debugWagon(i: Int, ticks: Int) {
    val p = tradeStore()
    if (p < 0 || i !in 0 until nbCount) return
    wagonOut(i, p)
    repeat(ticks) { wagonsStep() }
}

/** Каждый тик: повозки едут к краю, ждут за ним, возвращаются к посту и сдают оплату */
internal fun World.wagonsStep() {
    for (i in 0 until nbCount) {
        when (wgState[i]) {
            1, 3 -> {
                val path = wgPath[i]
                if (path == null || wgPos[i] >= path.size) { wagonArrived(i); continue }
                val c = path[wgPos[i]]
                val tx = (c % w) * Tune.SUB + Tune.SUB / 2; val ty = (c / w) * Tune.SUB + Tune.SUB / 2
                val dx = tx - wgX[i]; val dy = ty - wgY[i]
                val step = Tune.WAGON_SPEED
                if (Math.abs(dx) <= step && Math.abs(dy) <= step) { wgX[i] = tx; wgY[i] = ty; wgPos[i]++ }
                else { wgX[i] += dx.coerceIn(-step, step); wgY[i] += dy.coerceIn(-step, step) }
            }
            2 -> if (--wgTimer[i] <= 0) wagonBack(i)
        }
    }
}

private fun World.wagonArrived(i: Int) {
    if (wgState[i] == 1) { wgState[i] = 2; wgTimer[i] = Tune.WAGON_AWAY; wgPath[i] = null; return }
    // вернулась: оплата в пост (или в ратушу, если поста больше нет)
    val p = wgPost[i].let { if (it in 0 until buildingCount && bState[it] == B_ACTIVE && def(it).serves == "trade") it else hall }
    if (wgGetR[i] < 0) gold += wgGetN[i] else if (p >= 0) inv[p * R + wgGetR[i]] += wgGetN[i]
    nbRel[i] = minOf(100, nbRel[i] + Tune.REL_OK)
    deliveries++
    event(EV_WAGON_BACK, i, wgGetR[i] + 1)
    wgState[i] = 0; wgPath[i] = null; wgPost[i] = -1; wgGetR[i] = -1; wgGetN[i] = 0
}

/** Повозка возвращается из-за края карты к своему посту (снесли - к другому посту или к ратуше) */
private fun World.wagonBack(i: Int) {
    var p = wgPost[i]
    if (p !in 0 until buildingCount || bState[p] != B_ACTIVE || def(p).serves != "trade") p = tradePost()
    if (p < 0) p = hall
    wgPost[i] = p
    val e = entrance(p)
    val edge = edgeCell(i, e)
    val path = if (edge >= 0) findPath(edge, e) else null
    if (path == null || edge < 0) { wgState[i] = 3; wgPath[i] = null; wagonArrived(i); return }
    wgX[i] = (edge % w) * Tune.SUB + Tune.SUB / 2; wgY[i] = (edge / w) * Tune.SUB + Tune.SUB / 2
    wgPath[i] = path; wgPos[i] = 0
    wgState[i] = 3
}
