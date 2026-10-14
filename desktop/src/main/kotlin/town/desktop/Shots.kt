package town.desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.utils.ScreenUtils
import java.io.File
import town.Hearthvale
import town.screens.GameScreen
import town.sim.lowCells
import town.sim.debugWagon
import town.sim.debugRaid
import town.sim.B_ACTIVE
import town.sim.Command
import town.sim.MapSize
import town.sim.World

/**
 * Съёмка интерфейса: игра в скрытом окне, город на 4-й день, по кадру на состояние (карта, панель ратуши, постройка,
 * торговец, меню, титульный экран) в каждом размере окна. Размеры - ПК (1920x1080, 1366x768) и телефон в точках
 * (412x915 - так телефон видит веб-версия, 915x412 - он же лёжа). Файлы: <папка>/<размер>-<состояние>.png
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "shots" })
    out.mkdirs()
    val config = Lwjgl3ApplicationConfiguration()
    config.setTitle("Hearthvale shots")
    config.setWindowedMode(1920, 1080)
    config.setInitialVisible(false)
    config.setResizable(true)
    Lwjgl3Application(when (args.getOrNull(1)) {
        "readme" -> ReadmeShots(out)
        "titletown" -> ReadmeShots(out, titleTown = true)
        else -> Shots(out)
    }, config)
}

/** Снимки для README (docs/screenshots): большой город крупным планом, английский интерфейс */
/** titleTown - только записать город для живой заставки заглавного экрана (out/title_town.json) и выйти */
private class ReadmeShots(val out: File, val titleTown: Boolean = false) : ApplicationAdapter() {
    val game = Hearthvale("en")
    var frame = 0
    var step = 0
    var world: World? = null

    override fun create() = game.create()
    override fun resize(width: Int, height: Int) = game.resize(width, height)

    override fun render() {
        game.render()
        frame++
        if (frame < 12) return
        if (world == null) {
            world = bigTown()
            if (titleTown) {
                // утро летнего дня: впереди весь день, ночь на заставке наступит не скоро
                val wd = world!!
                while (wd.tick % town.sim.Tune.TICKS_PER_DAY != 60) { wd.step(); if (wd.tick % 100 == 0) supply(wd) }
                File(out, "title_town.json").writeText(town.sim.SaveCodec.write(wd))
                Gdx.app.exit()
                return
            }
            // для снимка: часть мастерских и услуг улучшена (флажки уровней видны)
            world!!.let { w -> var k = 0; for (b in 0 until w.buildingCount) if (w.bState[b] == town.sim.B_ACTIVE && w.hasLevels(b)) w.bLevel[b] = 1 + (k++ % 3) }
            world!!.tipsSeen = -1   // окна «впервые» не поверх кадров
            game.play(world!!)
            frame = 12
            return
        }
        if ((frame - 12) % 4 != 3) return
        val screen = game.screen as? GameScreen
        val wd = world!!
        when (step) {
            0 -> { screen?.debugAction("center", wd.hall); screen?.debugAction("zoom", 55) }
            1 -> { snap("town"); screen?.debugAction("select", wd.hall) }
            2 -> { snap("panel"); screen?.debugAction("close"); screen?.debugAction("stats") }
            3 -> { snap("stats"); screen?.debugAction("close"); Gdx.graphics.setWindowedMode(412, 915) }
            4 -> { screen?.debugAction("center", wd.hall); screen?.debugAction("zoom", 70) }
            5 -> {
                snap("phone")
                Gdx.graphics.setWindowedMode(1920, 1080)
                while (wd.season != town.sim.WINTER) { wd.step(); if (wd.tick % 100 == 0) supply(wd) }
                repeat(600) { wd.step(); if (wd.tick % 100 == 0) supply(wd) }
                wd.debugGrant(910, 3)
            }
            6 -> { screen?.debugAction("center", wd.hall); screen?.debugAction("zoom", 55) }
            7 -> screen?.debugAction("zoom", 55)   // убрать всплывшие за кадр сообщения
            8 -> { snap("winter"); Gdx.app.exit() }
        }
        step++
    }

    /** Город уровня «Село» со всеми цепочками: золото и материалы выданы, площадки ставятся по очереди */
    fun bigTown(): World {
        val c = game.content
        val wd = World.create(c, 42, MapSize.MEDIUM)
        val ids = listOf("house", "woodcutter", "sawmill", "well", "house", "fisher", "house", "quarry", "store",
            "house", "farm", "mill", "bakery", "house", "market", "woodcutter", "house", "church", "orchard", "pasture",
            "house", "weaver", "well", "tavern", "house", "firewatch", "healer", "farm", "house", "brewery", "house")
        for (id in ids) {
            wd.debugGrant(5000, 3)
            for (r in listOf("logs", "planks", "stone")) wd.inv[wd.hall * wd.R + c.resource(r).index] = 150
            val def = c.building(id).index
            val spot = nearest(wd, def)
            if (spot >= 0) wd.submit(Command.PLACE, def, spot % wd.w, spot / wd.w)
            repeat(250) { wd.step(); if (wd.tick % 100 == 0) supply(wd) }
        }
        // город растёт на выданных еде, воде и дровах, затем сутки живёт сам
        repeat(1200 * 8) { wd.step(); if (wd.tick % 100 == 0) supply(wd) }
        while (wd.season != town.sim.SUMMER) { wd.step(); if (wd.tick % 100 == 0) supply(wd) }
        repeat(1200) { wd.step() }
        wd.debugGrant(640, 3)
        return wd
    }

    fun supply(wd: World) {
        // готовые дома улучшаются, как это делал бы игрок
        for (b in 0 until wd.buildingCount) if (wd.houseReady(b) && wd.upgradeBlock(b) == null) wd.submit(town.sim.Command.UPGRADE, b, 0, 0)
        for (r in listOf("logs", "planks", "stone", "bread", "fish", "water", "cloth", "ale"))
            wd.inv[wd.hall * wd.R + game.content.resource(r).index] = 55
    }

    fun nearest(wd: World, def: Int): Int {
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        for (d in 3 until 40) for (dx in -d..d) for (s in intArrayOf(-1, 1)) {
            val dy = (d - Math.abs(dx)) * s
            val x = hx + dx; val y = hy + dy
            if (x in 0 until wd.w && y in 1 until wd.h && wd.canBuild(def, x, y)) return y * wd.w + x
        }
        return -1
    }

    /** JPEG 90%, чтобы снимки в git весили немного */
    fun snap(name: String) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        val pm = ScreenUtils.getFrameBufferPixmap(0, 0, w, h)
        val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, h - 1 - y, pm.getPixel(x, y) ushr 8)
        pm.dispose()
        val writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam
        param.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = 0.9f
        javax.imageio.ImageIO.createImageOutputStream(File(out, "$name.jpg")).use { stream ->
            writer.output = stream
            writer.write(null, javax.imageio.IIOImage(img, null, null), param)
        }
        writer.dispose()
        println("shot $name ${w}x$h")
    }
}

private class Shots(val out: File) : ApplicationAdapter() {
    val game = Hearthvale("ru")
    val sizes = listOf(1920 to 1080, 1366 to 768, 412 to 915, 915 to 412)
    var frame = 0
    var sizeIdx = 0
    var step = 0
    var world: World? = null
    var hall = -1
    var woodcutter = -1
    var storeB = -1
    var bridgeCell = -1
    var kidHouse = -1
    var postB = -1

    /** Здание в пойме (или ратуша) - для снимка паводка */
    fun lowBuilding(): Int {
        val wd = world!!
        return (0 until wd.buildingCount).firstOrNull { wd.bState[it] == B_ACTIVE && (0 until wd.w * wd.h).any { c -> wd.low[c] && wd.buildingAt[c] == it } } ?: hall
    }

    /** -PbigText: съёмка с «Крупным текстом»; прежняя настройка возвращается по выходу */
    private val big = System.getProperty("shots.big") != null
    private var bigWas = false

    override fun create() {
        if (big) { bigWas = town.Storage.bigText; town.Storage.bigText = true }
        game.create()
    }
    override fun dispose() { if (big) town.Storage.bigText = bigWas }
    override fun resize(width: Int, height: Int) = game.resize(width, height)

    override fun render() {
        game.render()
        frame++
        if (frame < 12) return   // загрузка по шагам и титульный экран
        if (world == null) {
            town.Storage.helpSeen = true   // справка первой песочницы - отдельным кадром, не поверх всех
            world = town()
            world!!.tipsSeen = -1   // окна «впервые» не поверх кадров
            game.play(world!!)
            Gdx.graphics.setWindowedMode(sizes[0].first, sizes[0].second)
            frame = 12; step = 0
            return
        }
        // на каждое состояние: действие, 3 кадра на раскладку, снимок
        if ((frame - 12) % 4 != 3) return
        val screen = game.screen as? GameScreen
        val (w, h) = sizes[minOf(sizeIdx, sizes.size - 1)]
        val name = "${w}x$h"
        when (step) {
            0 -> { screen?.debugAction("center", hall); snap("$name-1-map") ; screen?.debugAction("select", hall) }
            1 -> { snap("$name-2-hall"); screen?.debugAction("select", woodcutter) }
            2 -> { snap("$name-3-workshop"); screen?.debugAction("build", game.content.building("farm").index); screen?.debugAction("ghost", world!!.bX[hall] + 5, world!!.bY[hall] + 1); screen?.debugAction("turn") }
            3 -> { snap("$name-4-build"); screen?.debugAction("close"); screen?.debugAction("trade") }
            4 -> { snap("$name-5-trade"); screen?.debugAction("close"); screen?.debugAction("menu") }
            5 -> { snap("$name-6-menu"); screen?.debugAction("close"); screen?.debugAction("buildmenu", 1) }
            6 -> { snap("$name-7-buildmenu"); screen?.debugAction("close"); screen?.debugAction("stats") }
            7 -> { snap("$name-8-stats"); screen?.debugAction("close"); screen?.debugAction("events") }
            8 -> { snap("$name-9-events"); screen?.debugAction("close"); screen?.debugAction("tile", forestNear()) }
            9 -> { snap("$name-10-tile"); screen?.debugAction("close"); screen?.debugAction("help") }
            10 -> { snap("$name-11-help"); screen?.debugAction("close"); screen?.debugAction("summary") }
            11 -> { snap("$name-12-summary"); screen?.debugAction("close"); screen?.debugAction("layers") }
            12 -> { snap("$name-13-layers"); screen?.debugAction("close"); screen?.debugAction("layer", town.render.WorldRenderer.OV_HAPPY) }
            13 -> { snap("$name-14-happy"); screen?.debugAction("layer", town.render.WorldRenderer.OV_NONE); screen?.debugAction("chain", game.content.resource("planks").index) }
            14 -> { snap("$name-15-chain"); screen?.debugAction("close"); bridgeCell.takeIf { it >= 0 }?.let { screen?.debugAction("tile", it) } }
            15 -> { snap("$name-16-bridge"); screen?.debugAction("close"); if (storeB >= 0 && world!!.bState[storeB] == B_ACTIVE) screen?.debugAction("goods", storeB) }
            16 -> { snap("$name-17-goods"); screen?.debugAction("close"); screen?.debugAction("statstab", 1) }
            17 -> { snap("$name-18-forecast"); while (world!!.dayCount < 4) world!!.step(); screen?.debugAction("statstab", 2) }
            18 -> {
                snap("$name-19-charts"); screen?.debugAction("statstab", 0); screen?.debugAction("close")
                val wd = world!!
                // носильщик с грузом в пути (не у двери - там его закрывает дом)
                var guard = 0
                while (guard++ < 3000 && (0 until wd.agentCount).none { wd.state[it] != town.sim.ST_INSIDE && wd.carryN[it] > 0 }) wd.step()
                repeat(40) { wd.step() }   // отходит от двери, где его закрывает дом
                val a = (0 until wd.agentCount).firstOrNull { wd.state[it] != town.sim.ST_INSIDE && wd.carryN[it] > 0 }
                    ?: (0 until wd.agentCount).firstOrNull { wd.state[it] != town.sim.ST_INSIDE } ?: 0
                println("shots: tap citizen $a state ${wd.state[a]}")
                screen?.debugAction("tapAgent", a)
            }
            19 -> { snap("$name-20-citizen"); screen?.debugAction("close"); screen?.debugAction("neighbors") }
            20 -> { snap("$name-21-neighbors"); screen?.debugAction("close"); if (kidHouse >= 0) screen?.debugAction("select", kidHouse) }
            21 -> { snap("$name-22-kids"); screen?.debugAction("close"); if (postB >= 0) screen?.debugAction("select", postB) }
            22 -> {
                snap("$name-23-tradepost"); screen?.debugAction("close")
                if (sizeIdx == 1 && kidHouse >= 0) { screen?.debugAction("center", kidHouse); screen?.debugAction("zoom", 30); world!!.debugWagon(0, 60) }
            }
            23 -> { if (sizeIdx == 1) { snap("kids-zoom"); if (postB >= 0) screen?.debugAction("center", postB) } }
            24 -> { if (sizeIdx == 1) snap("wagon-zoom") }
            25 -> {
                sizeIdx++
                if (sizeIdx >= sizes.size) {
                    // зима: город идёт до первого зимнего дня, снимок в 1366x768
                    game.touchScreen = false
                    Gdx.graphics.setWindowedMode(1366, 768)
                    val wd = world!!
                    while (wd.season != town.sim.WINTER) wd.step()
                    repeat(300) { wd.step() }
                    screen?.debugAction("center", hall)
                    step = 99; return
                }
                // телефон лёжа шире 700 точек - без сенсорного флага снимался бы режим ПК
                game.touchScreen = sizes[sizeIdx].first < 1000
                Gdx.graphics.setWindowedMode(sizes[sizeIdx].first, sizes[sizeIdx].second)
                step = -1
            }
            99 -> { snap("winter"); screen?.debugAction("rotate", 1) }
            200 -> { snap("winter-r1"); screen?.debugAction("rotate", 1) }
            201 -> { snap("winter-r2"); screen?.debugAction("rotate", 1) }
            202 -> { snap("winter-r3"); screen?.debugAction("rotate", 1); game.setLowGraphics(true) }
            203 -> { snap("winter-low"); game.setLowGraphics(false); screen?.debugAction("center", hall); screen?.debugAction("zoom", 30) }
            204 -> {
                snap("day-zoom")
                // сумерки крупным планом: свет в окнах
                val wd = world!!
                while (wd.tick % town.sim.Tune.TICKS_PER_DAY != town.sim.Tune.NIGHT_START - 20) wd.step()
            }
            205 -> {
                snap("dusk-zoom")
                // весенний день крупно: бельё у домов, флаг ратуши
                val wd = world!!
                while (wd.season != town.sim.SPRING || wd.tick % town.sim.Tune.TICKS_PER_DAY != 600) wd.step()
            }
            206 -> { snap("spring-zoom"); world!!.debugWeather(town.sim.W_RAIN, 0) }
            207 -> { snap("rain-zoom"); world!!.debugWeather(town.sim.W_CLEAR, 2000); screen?.debugAction("center", lowBuilding()); screen?.debugAction("zoom", 12) }
            208 -> { snap("flood"); world!!.debugWeather(town.sim.W_CLEAR, 0); game.showTitle() }
            209 -> { snap("title"); (game.screen as? town.screens.TitleScreen)?.debugAbout() }
            210 -> { snap("1366x768-about"); (game.screen as? town.screens.TitleScreen)?.debugCampaign() }
            211 -> { snap("1366x768-campaign"); (game.screen as? town.screens.TitleScreen)?.debugSandbox() }
            212 -> { snap("1366x768-sandbox"); Gdx.graphics.setWindowedMode(412, 915) }
            213 -> { (game.screen as? town.screens.TitleScreen)?.debugAbout() }
            214 -> { snap("412x915-about"); (game.screen as? town.screens.TitleScreen)?.debugCampaign() }
            215 -> { snap("412x915-campaign"); (game.screen as? town.screens.TitleScreen)?.debugSandbox() }
            216 -> { snap("412x915-sandbox"); Gdx.app.exit() }
        }
        step = if (step == 99) 200 else step + 1
    }

    /** Ближняя к ратуше клетка леса - для снимка панели клетки */
    fun forestNear(): Int {
        val wd = world!!
        val hx = wd.bX[hall]; val hy = wd.bY[hall]
        return (0 until wd.w * wd.h).filter { wd.tiles[it] == town.sim.FOREST && wd.buildingAt[it] < 0 }
            .minByOrNull { Math.abs(it % wd.w - hx) + Math.abs(it / wd.w - hy) } ?: -1
    }

    /** Город: дровосек, лесопилка, колодец, рыбак, дома, каменоломня; 4 суток при x10 */
    fun town(): World {
        val wd = World.create(game.content, 42, MapSize.MEDIUM)
        hall = wd.hall
        for (id in listOf("woodcutter", "sawmill", "well", "house", "fisher", "house", "quarry", "store", "watchtower")) {
            val def = game.content.building(id).index
            val spot = nearest(wd, def)
            if (spot >= 0) wd.submit(Command.PLACE, def, spot % wd.w, spot / wd.w)
            if (id == "woodcutter") woodcutter = wd.lastPlaced
            if (id == "store") storeB = wd.lastPlaced
            repeat(300) { wd.step() }
        }
        repeat(1200 * 2) { wd.step() }
        if (woodcutter < 0 || wd.bState[woodcutter] != B_ACTIVE) woodcutter = hall
        // частокол уголком у ратуши и разбойники на подходе - стены и угрозы в кадре
        val hx = wd.bX[hall]; val hy = wd.bY[hall]
        for (k in 0 until 6) { wd.submit(town.sim.Command.WALL, hx - 4 + k, hy + 6, town.sim.WALL_WOOD); wd.submit(town.sim.Command.WALL, hx - 4, hy + 1 + k, town.sim.WALL_WOOD) }
        wd.debugGrant(wd.gold, maxOf(wd.level, 2))
        // мощёная дорога от ратуши и мост через реку у ближайшего берега - новые клетки в кадре
        val stone = game.content.resource("stone").index; val planks = game.content.resource("planks").index
        wd.inv[hall * wd.R + stone] += 60; wd.inv[hall * wd.R + planks] += 40
        for (k in 0 until 8) wd.submit(Command.ROAD, hx + 1 + k, hy - 2, 2)
        val shore = (0 until wd.w * wd.h).filter { wd.canBridge(it % wd.w, it / wd.w) }
            .minByOrNull { Math.abs(it % wd.w - hx) + Math.abs(it / wd.w - hy) } ?: -1
        if (shore >= 0) {
            // мост поперёк реки: от берега дальше по воде, пока можно
            var x = shore % wd.w; val y = shore / wd.w
            val dir = if (x + 1 < wd.w && wd.tiles[y * wd.w + x + 1] == town.sim.WATER) 1 else -1
            while (x in 0 until wd.w && wd.canBridge(x, y)) { wd.submit(Command.ROAD, x, y, 3); bridgeCell = y * wd.w + x; x += dir }
        }
        // торговый пост с договором, дети в доме, ферма в пойме - новое 0.11-0.12 в кадре
        for (r in listOf("logs", "planks", "stone")) wd.inv[hall * wd.R + game.content.resource(r).index] += 40
        wd.debugGrant(wd.gold + 500, wd.level)
        val tp = game.content.building("tradepost").index
        nearest(wd, tp).takeIf { it >= 0 }?.let { wd.submit(Command.PLACE, tp, it % wd.w, it / wd.w); postB = wd.lastPlaced; wd.submit(Command.RUSH, postB, 0, 0) }
        if (postB >= 0) wd.submit(Command.CONTRACT, 0, 1, 0)
        val farm = game.content.building("farm").index
        (0 until wd.w * wd.h).firstOrNull { wd.canBuild(farm, it % wd.w, it / wd.w) && wd.lowCells(farm, it % wd.w, it / wd.w) > 0 }?.let {
            wd.submit(Command.PLACE, farm, it % wd.w, it / wd.w); wd.submit(Command.RUSH, wd.lastPlaced, 0, 0)
        }
        kidHouse = (0 until wd.buildingCount).firstOrNull { wd.bState[it] == B_ACTIVE && wd.def(it).isHouse } ?: -1
        if (kidHouse >= 0) { wd.kidAge[kidHouse * town.sim.Tune.KIDS_MAX] = 3; wd.kidAge[kidHouse * town.sim.Tune.KIDS_MAX + 1] = 5 }
        wd.debugRaid(town.sim.RAID_BANDITS)
        repeat(60) { wd.step() }
        return wd
    }

    fun nearest(wd: World, def: Int): Int {
        val hx = wd.bX[wd.hall]; val hy = wd.bY[wd.hall]
        for (d in 3 until 40) for (dx in -d..d) for (s in intArrayOf(-1, 1)) {
            val dy = (d - Math.abs(dx)) * s
            val x = hx + dx; val y = hy + dy
            if (x in 0 until wd.w && y in 1 until wd.h && wd.canBuild(def, x, y)) return y * wd.w + x
        }
        return -1
    }

    fun snap(name: String) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        val pm = ScreenUtils.getFrameBufferPixmap(0, 0, w, h)
        PixmapIO.writePNG(Gdx.files.absolute(File(out, "$name.png").absolutePath), pm, -1, true)
        pm.dispose()
        println("shot $name ${w}x$h")
        // автопроверка раскладки: что вылезает за экран или не влезает - в overflow.txt
        val problems = (game.screen as? town.screens.GameScreen)?.debugOverflow() ?: (game.screen as? town.screens.TitleScreen)?.debugOverflow() ?: emptyList()
        if (problems.isNotEmpty()) {
            File(out, "overflow.txt").appendText("== $name\n" + problems.joinToString("\n") + "\n")
            println("overflow in $name: ${problems.size}")
        }
    }
}
