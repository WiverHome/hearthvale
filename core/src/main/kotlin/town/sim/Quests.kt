package town.sim

import town.data.Quest

/*
 * Задания - условия «значение >= цель» над состоянием мира с наградой. В сценарии задания идут цепочкой
 * по одному (с подсказками - так устроено обучение), все выполнены - победа. В песочнице и после победы
 * задания придумываются по состоянию города (через RNG мира - повтор по журналу тот же).
 * Провал сценария: срок вышел или жителей/довольства меньше порога дольше Tune.FAIL_GRACE.
 */

/** Текущее значение для задания i (сравнивается с questTarget) */
fun World.questValue(i: Int): Int = when (qType[i]) {
    Quest.STOCK -> totalStock(qA[i])
    Quest.BUILD -> countBuilt(qA[i])
    Quest.POP -> agentCount
    Quest.HAPPY -> qProg[i]
    Quest.LEVEL -> level
    Quest.GOLD -> gold
    Quest.MADE -> madeTotal[qA[i]] + statProd[qA[i]]
    Quest.DEFEND -> raidsRepelled + raidsStopped
    Quest.DELIVER -> deliveries
    Quest.GROWN -> kidsGrown
    else -> 0
}

/**
 * Звёзды кампании за выигранный сценарий: по запасу времени до срока (как в отчёте бота) - от STARS_3% три,
 * от STARS_2% две, иначе одна; сценарий без срока - три. Не выигран - 0
 */
fun World.campaignStars(): Int {
    val sc = scenarioDef ?: return 0
    if (outcome != WON) return 0
    if (sc.deadline <= 0) return 3
    val margin = (sc.deadline - day) * 100 / sc.deadline
    return when { margin >= Tune.STARS_3 -> 3; margin >= Tune.STARS_2 -> 2; else -> 1 }
}

fun World.questTarget(i: Int): Int = if (qType[i] == Quest.HAPPY) qDays[i] else qB[i]

fun World.countBuilt(def: Int): Int {
    var n = 0
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && bDef[b] == def) n++
    return n
}

/** Проверка заданий, выдача новых, условия провала - раз в Tune.NEEDS_INTERVAL */
internal fun World.updateQuests() {
    val dawn = tick % Tune.TICKS_PER_DAY == 0
    for (i in 0 until Tune.MAX_QUESTS) {
        if (qType[i] == 0) continue
        if (qType[i] == Quest.HAPPY && dawn) qProg[i] = if (townHappy >= qB[i]) qProg[i] + 1 else 0
        if (questValue(i) < questTarget(i)) continue
        // выполнено: награда в ратушу
        gold += qGold[i]
        if (qResR[i] >= 0 && hall >= 0) inv[hall * R + qResR[i]] += qResN[i]
        questsDone++
        event(EV_QUEST, i)
        val fromChain = qSrc[i] >= 0
        qType[i] = 0
        if (fromChain && outcome == PLAYING) nextScenarioQuest()
    }
    if (dailyDate > 0 && outcome == PLAYING && day > Tune.DAILY_DAYS) {
        outcome = WON
        dailyFinalScore = dailyScore()
        event(EV_DAILY_END, dailyFinalScore)
    }
    val sc = scenarioDef
    if (sc == null || outcome == WON) {
        while (activeQuests() < Tune.SANDBOX_QUESTS && generateQuest()) { }
        return
    }
    if (outcome != PLAYING) return
    if (sc.deadline > 0 && day > sc.deadline) { outcome = LOST; return }
    val bad = (sc.failPop > 0 && agentCount < sc.failPop) || (sc.failHappy > 0 && townHappy < sc.failHappy)
    if (!bad) failTimer = -1
    else if (failTimer < 0) failTimer = Tune.FAIL_GRACE
    else {
        failTimer -= Tune.NEEDS_INTERVAL
        if (failTimer <= 0) { failTimer = 0; outcome = LOST }
    }
}

fun World.activeQuests(): Int {
    var n = 0
    for (i in 0 until Tune.MAX_QUESTS) if (qType[i] != 0) n++
    return n
}

/** Следующее задание цепочки сценария в свободный слот; цепочка кончилась - победа */
internal fun World.nextScenarioQuest() {
    val sc = scenarioDef ?: return
    if (chainPos >= sc.quests.size) {
        if (activeQuests() == 0) outcome = WON
        return
    }
    val q = sc.quests[chainPos]
    val i = freeSlot()
    if (i < 0) return
    qType[i] = q.type; qA[i] = q.a; qB[i] = q.b; qDays[i] = q.days; qProg[i] = 0
    qGold[i] = q.gold; qResR[i] = q.resR; qResN[i] = q.resN; qSrc[i] = chainPos
    chainPos++
}

private fun World.freeSlot(): Int {
    for (i in 0 until Tune.MAX_QUESTS) if (qType[i] == 0) return i
    return -1
}

private fun World.hasQuest(type: Int): Boolean {
    for (i in 0 until Tune.MAX_QUESTS) if (qType[i] == type) return true
    return false
}

/** Придумать задание по состоянию города (виды не повторяются); false - придумать нечего */
private fun World.generateQuest(): Boolean {
    val i = freeSlot()
    if (i < 0) return false
    val start = rng.next(6)
    for (k in 0 until 6) {
        val type = (start + k) % 6 + 1
        if (hasQuest(type)) continue
        var a = 0; var b: Int; var days = 0; var g: Int; var resR = -1; var resN = 0
        when (type) {
            Quest.STOCK -> {
                // ресурс, который город уже умеет делать
                val made = (0 until R).filter { r -> content.resources[r].price > 0 && producesNow(r) }
                if (made.isEmpty()) continue
                a = made[rng.next(made.size)]
                b = roundUp(totalStock(a) + 20 + rng.next(30))
                g = 20 + b * content.resources[a].price / 4
            }
            Quest.BUILD -> {
                // не просить ещё одно здание того вида, что уже стоит без дела (полное, без ресурса, лимит)
                val opts = defs.filter { it.buildable && unlocked(it.index) && (it.isProducer || it.isHouse || it.isService) && !typeIdle(it.index) }
                if (opts.isEmpty()) continue
                // с «Села» - сначала то, без чего его новое не увидеть: церковь (потребность домов) и торговый пост (соседи)
                val key = if (level >= Tune.KEY_QUEST_LEVEL) KEY_BUILDINGS.map { content.building(it) }
                    .firstOrNull { it.buildable && unlocked(it.index) && countBuilt(it.index) == 0 } else null
                val d = key ?: opts[rng.next(opts.size)]
                a = d.index
                b = countBuilt(a) + 1
                g = 10 + d.costTotal * 2
            }
            Quest.POP -> { b = agentCount + 6 + rng.next(8); g = 40 + b }
            Quest.HAPPY -> { b = 60 + rng.next(5) * 5; days = 1; g = 50; resR = foodResource(); resN = if (resR >= 0) 10 else 0 }
            Quest.LEVEL -> {
                if (level + 1 >= content.levels.size) continue
                b = level + 1; g = 80 + 40 * level
            }
            else -> { b = roundUp(gold + 80 + rng.next(120)); g = 0; resR = content.resource("planks").index; resN = 20 }
        }
        qType[i] = type; qA[i] = a; qB[i] = b; qDays[i] = days; qProg[i] = 0
        qGold[i] = g; qResR[i] = resR; qResN[i] = resN; qSrc[i] = -1
        return true
    }
    return false
}

private fun roundUp(n: Int) = (n + 9) / 10 * 10

private val KEY_BUILDINGS = listOf("church", "tradepost")

/** Есть ли готовое здание вида def, которое стоит: готовое некуда девать, нечего добывать, лимит */
private fun World.typeIdle(def: Int): Boolean {
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && bDef[b] == def &&
        bStatus[b].let { it == BS_OUTPUT_FULL || it == BS_FAR || it == BS_NO_RESOURCE || it == BS_LIMIT }) return true
    return false
}

/** Есть ли готовая мастерская, которая делает r */
private fun World.producesNow(r: Int): Boolean {
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).output[r] > 0) return true
    return false
}

private fun World.foodResource(): Int = content.needs.firstOrNull { it.id == "food" }?.goods?.firstOrNull() ?: -1
