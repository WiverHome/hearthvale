package town.sim

import java.io.File
import kotlin.test.Test
import town.data.Content

/**
 * Баланс: бот играет песочницу на нескольких сидах, отчёт по суткам - в build/balance/<сид>.txt и в вывод.
 * Запуск: ./gradlew :core:balance (в обычный :core:test не входит - долго).
 */
class BalanceTest {
    /** Отметки отчёта - минуты игры при x1 (не сутки: длина суток может меняться) и сутки, на которые они приходятся */
    private val MINUTES = listOf(10, 20, 30, 40)
    private fun minuteDay(min: Int) = maxOf(1, min * 60 * Tune.TICKS_PER_SECOND / Tune.TICKS_PER_DAY)

    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())

    @Test
    fun sandboxBot() {
        // по умолчанию 40 минут игры при x1 (сутки - Tune.TICKS_PER_DAY тиков)
        val days = (System.getProperty("balance.days") ?: "${minuteDay(40)}").toInt()
        val seeds = (System.getProperty("balance.seeds") ?: "42,7,2026,1,99,555").split(',').map { it.trim().toInt() }
        // сдвиги случайности: партия с солью k - та же карта, но k лишних чисел RNG после создания (торговец, бедствия,
        // погода идут иначе); средние по нескольким солям меньше зависят от удачи
        val salts = salts()
        val out = File("build/balance"); out.mkdirs()
        val summary = StringBuilder()
        val pops = ArrayList<List<Int>>()
        val builds = java.util.TreeMap<String, MutableList<Int>>()
        val upgrades = java.util.TreeMap<String, MutableList<Int>>()
        // карты независимы - играются параллельно (по ядрам), отчёт собирается по порядку сидов
        class Run(val seed: Int, val salt: Int, val wd: World, val bot: Bot, val ms: Long)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(maxOf(1, minOf(seeds.size, Runtime.getRuntime().availableProcessors())))
        val runs = try {
            seeds.flatMap { seed -> (0 until salts).map { salt -> seed to salt } }.map { (seed, salt) ->
                pool.submit<Run> {
                    val wd = World.create(content, seed, MapSize.MEDIUM)
                    repeat(salt) { wd.rng.next() }
                    val bot = Bot(wd)
                    val t0 = System.nanoTime()
                    bot.run(days)
                    Run(seed, salt, wd, bot, (System.nanoTime() - t0) / 1_000_000)
                }
            }.map { it.get() }
        } finally { pool.shutdown() }
        for (run in runs) {
            val seed = run.seed; val wd = run.wd; val bot = run.bot
            if (run.salt == 0) File(out, "$seed.txt").writeText(bot.report.toString())
            println("== seed $seed, $days days, ${run.ms} ms")
            fun at(d: Int) = if (d <= bot.popByDay.size) "${bot.popByDay[d - 1]}/${bot.happyByDay[d - 1]}%" else "-"
            pops.add(bot.popByDay.toList())
            for ((k, v) in bot.buildTicks) builds.getOrPut(k) { ArrayList() }.addAll(v)
            for ((k, v) in bot.upgradeTicks) upgrades.getOrPut(k) { ArrayList() }.addAll(v)
            summary.append("seed $seed" + (if (salts > 1) "/${run.salt}" else "") + ": " + MINUTES.joinToString("  ") { "min$it ${at(minuteDay(it))}" } + "  max pop ${bot.popByDay.maxOrNull()}  level ${wd.level}  gold ${wd.gold}  ${bot.summaryLine()}  fame@min40 ${bot.fameByDay.getOrNull(minuteDay(40) - 1) ?: "-"}  carts@min40 ${bot.cartsByDay.getOrNull(minuteDay(40) - 1) ?: "-"}\n")
        }
        fun avg(d: Int) = pops.filter { it.size >= d }.map { it[d - 1] }.average().toInt()
        fun min(d: Int) = pops.filter { it.size >= d }.minOf { it[d - 1] }
        val last = minOf(minuteDay(40), days)
        summary.append("average pop: " + MINUTES.joinToString("  ") { "min$it ${avg(minOf(minuteDay(it), days))}" } + "  (worst min40 ${min(last)})\n")
        File(out, "summary.txt").writeText(summary.toString())
        // сроки стройки и улучшений по всем картам: секунды при x1 - медиана, четверть самых долгих, число замеров
        val times = StringBuilder("seconds at x1: median / p75 / max (samples)\n== build\n")
        fun line(k: String, v: List<Int>) {
            val s = v.sorted().map { it / Tune.TICKS_PER_SECOND }
            times.append(String.format("%-14s %4d / %4d / %4d  (%d)%n", k, s[s.size / 2], s[s.size * 3 / 4], s.last(), s.size))
        }
        for ((k, v) in builds) line(k, v)
        times.append("== upgrade\n")
        for ((k, v) in upgrades) line(k, v)
        File(out, "times.txt").writeText(times.toString())
        // кривая сложности: веха - медиана минуты игры по партиям (и в скольких партиях случилась)
        val ms = java.util.TreeMap<String, MutableList<Int>>()
        for (run in runs) for ((k, d) in run.bot.milestones) ms.getOrPut(k) { ArrayList() }.add(d)
        val mtxt = StringBuilder("milestone: median minute at x1 (min-max), runs reached / total ${runs.size}" + System.lineSeparator())
        val dayMin = Tune.TICKS_PER_DAY / Tune.TICKS_PER_SECOND / 60.0
        for ((k, v) in ms.entries.sortedBy { e -> e.value.sorted()[e.value.size / 2] }) {
            val sv = v.sorted()
            mtxt.append(String.format("%-22s %5.0f  (%3.0f-%3.0f)  %d/%d%n", k, sv[sv.size / 2] * dayMin, sv.first() * dayMin, sv.last() * dayMin, v.size, runs.size))
        }
        File(out, "milestones.txt").writeText(mtxt.toString())
        println(times)
        println("== summary (pop/happy)\n$summary")
    }

    private fun salts() = (System.getProperty("balance.salts") ?: "1").toInt()

    /**
     * Сохранение большого города (200 жителей) для проверки скорости на телефоне: build/balance/big.json -
     * подложить в слот (Preferences "hearthvale", ключ save.1) и загрузить в игре.
     */
    @Test
    fun bigTownSave() {
        val wd = World.create(content, 64, MapSize.MEDIUM)
        val bot = Bot(wd)
        while (wd.agentCount < 200 && wd.day < 100) bot.run(1)
        val out = File("build/balance"); out.mkdirs()
        File(out, "big.json").writeText(SaveCodec.write(wd))
        println("== big town: ${wd.agentCount} citizens, ${wd.buildingCount} buildings, day ${wd.day}")
    }

    /**
     * Сценарии: бот играет каждый до победы или провала; в отчёте - в какие сутки выполнено каждое задание
     * и сколько запаса до срока. Отчёт - build/balance/scenario_<id>.txt и scenarios.txt.
     */
    @Test
    fun scenarioBot() {
        val out = File("build/balance"); out.mkdirs()
        val summary = StringBuilder()
        // сценарии тоже параллельно, строки отчёта - по порядку
        val pool = java.util.concurrent.Executors.newFixedThreadPool(maxOf(1, minOf(content.scenarios.size, Runtime.getRuntime().availableProcessors())))
        val lines = try {
            content.scenarios.flatMap { sc -> (0 until salts()).map { sc to it } }.map { (sc, salt) ->
                pool.submit<String> {
                    val wd = World.create(content, sc)
                    repeat(salt) { wd.rng.next() }
                    val bot = Bot(wd)
                    bot.run(if (sc.deadline > 0) sc.deadline + 2 else 80)
                    if (salt == 0) File(out, "scenario_${sc.id}.txt").writeText(bot.report.toString())
                    val result = when (wd.outcome) { WON -> "WON"; LOST -> "LOST"; else -> "PLAYING" }
                    val margin = if (sc.deadline > 0 && wd.outcome == WON) "  margin ${(sc.deadline - wd.day) * 100 / sc.deadline}%" else ""
                    "${sc.id}" + (if (salts() > 1) "/$salt" else "") + ": $result on day ${wd.day} (deadline ${sc.deadline})$margin  quests ${wd.questsDone}/${sc.quests.size} on days ${bot.questDays}" +
                        "  pop ${wd.agentCount} happy ${wd.townHappy} gold ${wd.gold}  ${bot.summaryLine()}" +
                        (if (wd.threatsOn) "  raids ${wd.raidsTotal} repelled ${wd.raidsRepelled} stopped ${wd.raidsStopped}" else "") + "\n"
                }
            }.map { it.get() }
        } finally { pool.shutdown() }
        for (l in lines) summary.append(l)
        // по сценарию: сколько побед из солей, запасы до срока (меньший - самый неудачный)
        if (salts() > 1) for (sc in content.scenarios) {
            val mine = lines.filter { it.startsWith(sc.id + "/") }
            val margins = mine.mapNotNull { Regex("margin (-?[0-9]+)%").find(it)?.groupValues?.get(1)?.toInt() }
            summary.append("== ${sc.id}: won ${mine.count { it.contains(": WON") }}/${mine.size}  margins $margins\n")
        }
        File(out, "scenarios.txt").writeText(summary.toString())
        println("== scenarios\n$summary")
    }
}
