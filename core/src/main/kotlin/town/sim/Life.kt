package town.sim

/*
 * Дети. В довольном доме (довольство от KID_HAPPY, жильцов не меньше половины кроватей) на рассвете может родиться
 * ребёнок - до KIDS_MAX на дом. Дети не работают и не носят, едят KID_EAT процентов взрослого (Town.kt), через KID_DAYS
 * суток взрослеют: становятся жителями этого дома (нет кровати - любого со свободной; нет нигде - ждут). Кто провёл
 * KID_SCHOOL_DAYS суток в радиусе работающей школы, вырастает учёным: работает быстрее на SKILL_BONUS процентов.
 * Старости и смерти нет. Случайность - только rng мира.
 */

/** Детей в доме b */
fun World.kids(b: Int): Int {
    var n = 0
    for (k in 0 until Tune.KIDS_MAX) if (kidAge[b * Tune.KIDS_MAX + k] > 0) n++
    return n
}

/** Детей в городе */
fun World.kidsTotal(): Int {
    var n = 0
    for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).isHouse) n += kids(b)
    return n
}

/** Учёных среди жителей */
fun World.skilledTotal(): Int {
    var n = 0
    for (a in 0 until agentCount) n += aSkill[a]
    return n
}

/** Рассвет: дети подрастают (у школы - учатся), выросшие становятся жителями, в довольных домах рождаются новые */
internal fun World.lifeDawn() {
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE || !def(b).isHouse) continue
        // семья ушла - дети уходят с ней
        if (bResidents[b] == 0) {
            for (k in 0 until Tune.KIDS_MAX) { kidAge[b * Tune.KIDS_MAX + k] = 0; kidSchool[b * Tune.KIDS_MAX + k] = 0 }
            continue
        }
        val school = served(b, "school")
        for (k in 0 until Tune.KIDS_MAX) {
            val i = b * Tune.KIDS_MAX + k
            if (kidAge[i] == 0) continue
            if (school) kidSchool[i]++
            if (kidAge[i] > 1) { kidAge[i]--; continue }
            // вырос: в свой дом, если есть кровать, иначе в любой свободный; нет - ждёт
            val home = if (bResidents[b] < def(b).beds) b else houseWithBed()
            if (home < 0 || agentCount >= Tune.MAX_AGENTS) continue
            val a = spawnCitizenIn(home, entrance(b))
            aSkill[a] = if (kidSchool[i] >= Tune.KID_SCHOOL_DAYS) 1 else 0
            kidsGrown++
            event(EV_GROWN, b, aSkill[a])
            kidAge[i] = 0; kidSchool[i] = 0
        }
    }
    if (level < Tune.KID_LEVEL) return
    for (b in 0 until buildingCount) {
        if (bState[b] != B_ACTIVE || !def(b).isHouse) continue
        val res = bResidents[b]
        if (res < 2 || res * 2 < def(b).beds || bHappy[b] < Tune.KID_HAPPY || bFire[b] > 0 || bSick[b] > 0 || flooded(b)) continue
        var slot = -1
        for (k in 0 until Tune.KIDS_MAX) if (kidAge[b * Tune.KIDS_MAX + k] == 0) { slot = k; break }
        if (slot < 0) continue
        if (rng.next(100) < Tune.KID_CHANCE) kidAge[b * Tune.KIDS_MAX + slot] = Tune.KID_DAYS
    }
}

/** Первый готовый дом со свободной кроватью или -1 */
internal fun World.houseWithBed(): Int {
    for (h in 0 until buildingCount) if (bState[h] == B_ACTIVE && def(h).isHouse && bResidents[h] < def(h).beds) return h
    return -1
}

/** Скорость работы жителя a в здании b в процентах: уровень, инструмент и учёность */
internal fun World.agentSpeed(a: Int, b: Int) = workSpeed(b) * (100 + aSkill[a] * Tune.SKILL_BONUS) / 100
