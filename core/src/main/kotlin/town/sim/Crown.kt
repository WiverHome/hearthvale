package town.sim

/*
 * Слава города и заказы короны. Слава прибавляется на рассвете от украшений, зданий со славой и довольных домов,
 * открывает здания (prestige в content.json). Корона с уровня CROWN_LEVEL просит партию товара, который город уже
 * делает: отдал в срок - золото и слава, не отдал - слава падает. Только песочница и партия дня.
 */

/** Слава в сутки: здания со славой (с работниками - пока они есть; украшения - всегда) и довольные дома */
fun World.fameDaily(): Int {
    var n = 0
    var happy = 0
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE) continue
        val d = def(b)
        if (d.fame > 0 && (d.workers == 0 || bWorkers[b] > 0)) n += d.fame
        if (d.isHouse && bHappy[b] >= Tune.UPGRADE_HAPPY) happy++
    }
    return n + happy / Tune.FAME_HAPPY_HOUSES
}

/** Рассвет: просроченный заказ короны - провал; нет заказа и город дорос - новый */
internal fun World.crownDawn() {
    if (scenario >= 0) return
    val d = day
    if (crR >= 0 && d > crEnd) {
        prestige = maxOf(0, prestige - Tune.CROWN_FAIL)
        crFailed++
        event(EV_CROWN_FAILED, crR)
        crR = -1
        crNext = d + Tune.CROWN_GAP
    }
    if (crR >= 0 || level < Tune.CROWN_LEVEL || d < crNext) return
    // товары жизненных потребностей (еда, вода, дрова) корона не берёт - заказ не должен оставлять дома голодными
    val vital = BooleanArray(R)
    for (n in content.needs) if (n.vital) for (g in n.goods) vital[g] = true
    val cands = ArrayList<Int>()
    for (r in 0 until R) {
        if (vital[r] || content.resources[r].price < 4) continue
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).output[r] > 0) { cands.add(r); break }
    }
    if (cands.isEmpty()) { crNext = d + 1; return }
    val r = cands[rng.next(cands.size)]
    val price = content.resources[r].price
    // размер - доля недельной выработки города (по статистике прошлых суток), чтобы заказ был по силам
    var made = 0
    for (k in 1..histDays) made += produced(r, k)
    val perWeek = made * 7 / maxOf(1, histDays)
    val n = ((perWeek * Tune.CROWN_SHARE / 100).coerceIn(Tune.CROWN_MIN, Tune.CROWN_MAX) + 9) / 10 * 10
    crR = r; crN = n; crEnd = d + Tune.CROWN_DAYS
    crGold = n * price * Tune.CROWN_PAY / 100
    crPrestige = Tune.CROWN_PRESTIGE
    event(EV_CROWN, r, n)
}

/** Заказ короны есть, и на складах свободно хватает товара */
fun World.canDeliverCrown(): Boolean = crR >= 0 && stockAvailable(crR) >= crN

/** Отдать заказ: товар со складов, золото и слава */
internal fun World.deliverCrown() {
    val r = crR
    takeFromStores(r, crN)
    statUse[r] += crN
    gold += crGold
    prestige += crPrestige
    crDone++
    event(EV_CROWN_DONE, r, crGold)
    crR = -1
    crNext = day + Tune.CROWN_GAP
}

/** Очки партии дня */
fun World.dailyScore(): Int =
    agentCount * 10 + townHappy * 5 + questsDone * 30 + prestige * 2 + level * 100 + gold / 10 + crDone * 50
