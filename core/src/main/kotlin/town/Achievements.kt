package town

import town.data.Content
import town.sim.World

/**
 * Достижения: условия над состоянием партии, проверяются в интерфейсе (на симуляцию не влияют), открытые хранятся
 * между партиями (Storage). Победа в сценарии - своё достижение на каждый сценарий.
 */
object Achievements {
    class Def(val id: String, val check: (World) -> Boolean)

    private fun count(w: World, id: String): Int {
        var n = 0
        for (b in 0 until w.buildingCount) if (w.bState[b] == town.sim.B_ACTIVE && w.def(b).id == id) n++
        return n
    }

    val all = listOf(
        Def("pop_100") { it.agentCount >= 100 },
        Def("pop_300") { it.agentCount >= 300 },
        Def("level_town") { it.level >= 3 },
        Def("level_capital") { it.level >= 7 },
        Def("stone_20") { count(it, "house3") >= 20 },
        Def("happy_95") { it.townHappy >= 95 && it.agentCount >= 50 },
        Def("defend_10") { it.raidsRepelled + it.raidsStopped >= 10 },
        Def("tools_100") { w -> w.content.resources.firstOrNull { it.id == "tools" }?.let { w.madeTotal[it.index] >= 100 } ?: false },
        Def("rich") { it.gold >= 10000 },
        Def("cathedral") { it.hasWonder("cathedral") },
        Def("castle") { it.hasWonder("castle") },
        Def("crown_3") { it.crDone >= 3 },
        Def("guilds") { it.hasWonder("guildhall") },
        Def("early_bird") { it.dailyFinalScore >= 2000 },
        Def("trade_10") { it.deliveries >= 10 },
        Def("pupils_10") { it.kidsGrown >= 10 },
        Def("stars_all") { _ -> Storage.allStars() },
    )

    /** Все id: обычные и «победа в сценарии» по каждому сценарию */
    fun ids(content: Content): List<String> = all.map { it.id } + content.scenarios.map { "win_" + it.id }

    /** Новые достижения этой партии (и запомнить их); пустой список - ничего нового */
    fun check(w: World): List<String> {
        val got = Storage.achievements
        val out = ArrayList<String>()
        for (d in all) if (d.id !in got && d.check(w)) out.add(d.id)
        val sc = w.scenarioDef
        if (sc != null && w.outcome == town.sim.WON && ("win_" + sc.id) !in got) out.add("win_" + sc.id)
        if (out.isNotEmpty()) Storage.achievements = got + out
        return out
    }
}
