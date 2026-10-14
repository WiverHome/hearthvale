package town.data

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/** Текст на двух языках */
class Text(val ru: String, val en: String) {
    fun get(lang: String) = if (lang == "ru") ru else en

    companion object {
        fun of(v: JsonValue) = Text(v.getString("ru"), v.getString("en"))
    }
}

/**
 * price - базовая цена у торговца в золоте (0 - торговец не берёт и не продаёт);
 * limit - лимит «не больше N на складах» у новой мастерской этого ресурса (-1 - без лимита), чтобы вода и
 * прочее дешёвое не забивали склады; игрок меняет его в панели здания
 */
/** lasts - на сколько единиц потребности дома хватает одной штуки (уголь греет дольше брёвен) */
class ResourceDef(val index: Int, val id: String, val name: Text, val color: Long, val price: Int, val limit: Int, val lasts: Int = 1)

/**
 * Потребность дома: товары (goods - любой из ресурсов, use - расход на жителя в сутки в сотых, season - множитель
 * по сезонам в процентах, 0 - в этот сезон не нужно) или услуга (service - здание с таким serves рядом).
 * level - с какого уровня города потребность учитывается; weight - вклад в довольство (сумма весов 100);
 * vital - без неё довольство не выше Tune.VITAL_CAP (голод, жажда, холод выгоняют жителей).
 */
class NeedDef(
    val index: Int,
    val id: String,
    val name: Text,
    val goods: IntArray,
    val use: Int,
    val season: IntArray,
    val service: String?,
    val level: Int,
    val weight: Int,
    val vital: Boolean,
    /** С какого уровня дома (1 - обычный, 2 - крепкий, 3 - каменный) потребность появляется */
    val tier: Int,
)

/** Уровень города: открывается при pop жителей и довольстве не ниже happy */
class LevelDef(val pop: Int, val happy: Int, val name: Text)

class BuildingLook(val wall: Long, val roof: Long, val wallH: Int, val roofH: Int)

/**
 * Тип здания. role: hall (ратуша: склад + строители), house (кровати), store (склад), producer (производство),
 * service (услуга для домов в радиусе: рынок, церковь).
 * Количества ресурсов - массивы по индексу ресурса.
 */
class BuildingDef(
    val index: Int,
    val id: String,
    val role: String,
    val size: Int,
    val buildable: Boolean,
    val name: Text,
    val cost: IntArray,
    /** Сколько тиков работы строителя нужно на постройку */
    val build: Int,
    val capacity: Int,
    val beds: Int,
    val builders: Int,
    val workers: Int,
    /** Нужны инструменты: изнашиваются, без них работа медленнее */
    val tools: Boolean,
    val input: IntArray,
    val output: IntArray,
    /** Тиков на один цикл производства при одном рабочем */
    val time: Int,
    /** Нужная клетка рядом ("forest", "water", "rock") или null */
    val needTile: String?,
    val needRadius: Int,
    val needMin: Int,
    val look: BuildingLook,
    /** С какого уровня города можно строить */
    val level: Int,
    /** Цена постройки в золоте (списывается при закладке) */
    val gold: Int,
    /** Какую услугу даёт (role service) и в каком радиусе от центра */
    val serves: String?,
    val radius: Int,
    /** В какие сезоны работает (весна, лето, осень, зима) */
    val seasons: BooleanArray,
    /** Дом: уровень (1..3), во что улучшается (id или null), налог с жильца в процентах от обычного */
    val tier: Int,
    val upgradeTo: String?,
    val tax: Int,
    /** Содержание: золота в сутки (на рассвете); не заплачено - здание стоит */
    val upkeep: Int,
    /** Вкладка меню строительства: home, food, materials, services, decor, land */
    val cat: String = "home",
    /** Добытчик: что добывает на карте (wood, stone, fish, game) или сажает (plant); рабочие ходят к ресурсу */
    val gather: String? = null,
    /** Выработка зависит от земли: fertility (плодородие) или groundwater (подземная вода) */
    val soil: String? = null,
    /** Своя земля: радиус границы поселения вокруг готового здания (0 - не расширяет); растёт с уровнем здания */
    val border: Int = 0,
    /** Чудо (собор, замок): одно на город, без уровней, своё действие (см. Tune.WONDER_*) */
    val wonder: Boolean = false,
    /** Сколько славы города нужно, чтобы строить (0 - не нужно) */
    val prestige: Int = 0,
    /** Слава города в сутки, пока здание работает */
    val fame: Int = 0,
    /** Рынок: ресурсы (объединение товаров перечисленных потребностей), которые он держит и развозит по домам */
    val distributes: IntArray = IntArray(0),
) {
    val gatherCode = town.sim.gatherCode(gather)
    val isStore get() = role == "hall" || role == "store"
    val isProducer get() = role == "producer"
    val isService get() = role == "service"
    val isHouse get() = role == "house"
    /** Смотровая вышка: без работников, только расширяет границу */
    val isTower get() = role == "tower"
    /** Караул (караульная вышка, замок): рабочие - стража, отгоняют угрозы в радиусе radius */
    val isGuard get() = role == "guard"
    /** Украшение: услуга без работников (действует всегда) */
    val isDecor get() = role == "service" && workers == 0
    val costTotal = cost.sum()
}

/** Виды заданий: условие «значение >= цель» над состоянием мира */
object Quest {
    const val STOCK = 1   // a = ресурс, b = сколько на складах
    const val BUILD = 2   // a = тип здания, b = сколько готовых
    const val POP = 3     // b = жителей
    const val HAPPY = 4   // b = довольство города, days = суток подряд (проверка на рассвете)
    const val LEVEL = 5   // b = уровень города
    const val GOLD = 6    // b = золота
    const val MADE = 7    // a = ресурс, b = сколько произведено за партию
    const val DEFEND = 8  // b = сколько угроз отбито (стража прогнала или стены остановили)
    const val DELIVER = 9 // b = сколько поставок соседям сделано
    const val GROWN = 10  // b = сколько детей выросло

    fun type(name: String) = when (name) {
        "stock" -> STOCK; "build" -> BUILD; "pop" -> POP; "happy" -> HAPPY; "level" -> LEVEL; "gold" -> GOLD
        "made" -> MADE; "defend" -> DEFEND; "deliver" -> DELIVER; "grown" -> GROWN
        else -> error("unknown quest type '$name'")
    }
}

/** Задание сценария: условие, награда (золото и один ресурс в ратушу), подсказка игроку */
class QuestDef(val type: Int, val a: Int, val b: Int, val days: Int, val gold: Int, val resR: Int, val resN: Int, val hint: Text?)

/**
 * Сценарий: карта (сид и размер), стартовые условия (поверх content.start), цепочка заданий по одному,
 * победа - все задания выполнены; провал - срок (deadline, день) или жителей/довольства ниже порога дольше Tune.FAIL_GRACE.
 */
class ScenarioDef(
    val index: Int,
    val id: String,
    val name: Text,
    val desc: Text,
    val seed: Int,
    val size: String,
    val startDay: Int,
    val level: Int,
    val gold: Int,
    val stock: IntArray?,
    val citizens: Int,
    val houses: Int,
    val quests: List<QuestDef>,
    val failPop: Int,
    val failHappy: Int,
    val deadline: Int,
    /** Угрозы (волки, разбойники) в этом сценарии */
    val threats: Boolean = false,
    /** Соседи за краем карты (договоры через торговый пост) */
    val neighbors: Boolean = false,
    /** Мягкое начало (обучение): до уровня «Деревня» без пожаров и болезней */
    val gentle: Boolean = false,
    /** Разбойники не реже раза в столько суток, пока есть задание «отбить набеги» (0 - как повезёт) */
    val raidEvery: Int = 0,
    /** Место на карте королевства (кампания), проценты ширины и высоты */
    val mapX: Int = 50,
    val mapY: Int = 50,
)

/** Стартовые условия новой игры */
class StartDef(val hall: String, val house: String, val houses: Int, val citizens: Int, val gold: Int, val stock: IntArray)

/** Весь контент игры из assets/data/content.json. Разбор вручную (JsonReader), без рефлексии - так работает и в TeaVM. */
class Content(
    val resources: List<ResourceDef>,
    val buildings: List<BuildingDef>,
    val needs: List<NeedDef>,
    val levels: List<LevelDef>,
    val start: StartDef,
    val scenarios: List<ScenarioDef>,
) {

    /** Имена жителей из names.json: [язык][0 - мужские, 1 - женские] */
    private var names: Map<String, Array<List<String>>> = emptyMap()
    private var villages: Map<String, List<String>> = emptyMap()

    fun loadNames(json: String) {
        val root = JsonReader().parse(json)
        val m = HashMap<String, Array<List<String>>>()
        for (lang in listOf("ru", "en")) {
            val l = root.get(lang) ?: continue
            m[lang] = arrayOf(l.get("m").asStringArray().toList(), l.get("f").asStringArray().toList())
            l.get("villages")?.let { v -> villages = villages + (lang to v.asStringArray().toList()) }
        }
        names = m
    }

    /** Имя жителя по сиду мира и номеру: хеш, без генератора симуляции */
    fun citizenName(seed: Int, serial: Int, lang: String): String {
        val l = names[lang] ?: return "#$serial"
        val h = mix(seed, serial)
        val list = l[(h ushr 7) and 1]
        if (list.isEmpty()) return "#$serial"
        return list[((h ushr 9) and 0x7fffff) % list.size]
    }

    /** Название села соседа по номеру (World.nbName) */
    fun villageName(n: Int, lang: String): String {
        val l = villages[lang] ?: return "#$n"
        return if (l.isEmpty()) "#$n" else l[Math.floorMod(n, l.size)]
    }

    fun citizenFemale(seed: Int, serial: Int) = ((mix(seed, serial) ushr 7) and 1) == 1

    private fun mix(seed: Int, serial: Int): Int {
        var h = seed * -1640531535 + serial * 668265263 + 374761393
        h = (h xor (h ushr 15)) * -2048144777
        h = (h xor (h ushr 13)) * -1028477379
        return h xor (h ushr 16)
    }

    fun building(id: String) = buildings.first { it.id == id }
    fun resource(id: String) = resources.first { it.id == id }

    companion object {
        /** json - content.json, scenariosJson - scenarios.json (может не быть) */
        fun parse(json: String, scenariosJson: String? = null): Content {
            val root = JsonReader().parse(json)
            val resources = ArrayList<ResourceDef>()
            each(root.get("resources")) { r ->
                resources.add(ResourceDef(resources.size, r.getString("id"), Text.of(r.get("name")), hexColor(r.getString("color")), r.getInt("price", 0), r.getInt("limit", -1), maxOf(1, r.getInt("lasts", 1))))
            }
            val byId = HashMap<String, Int>()
            for (res in resources) byId[res.id] = res.index

            fun amounts(v: JsonValue?, where: String): IntArray {
                val a = IntArray(resources.size)
                each(v) { c ->
                    val idx = byId[c.name] ?: error("unknown resource '${c.name}' in $where")
                    a[idx] = c.asInt()
                }
                return a
            }

            fun ints(v: JsonValue?, n: Int, def: Int): IntArray {
                val a = IntArray(n) { def }
                var i = 0
                each(v) { c -> if (i < n) a[i++] = c.asInt() }
                return a
            }

            val needs = ArrayList<NeedDef>()
            each(root.get("needs")) { n ->
                val goods = ArrayList<Int>()
                each(n.get("goods")) { g -> goods.add(byId[g.asString()] ?: error("unknown resource '${g.asString()}' in needs")) }
                needs.add(NeedDef(
                    index = needs.size,
                    id = n.getString("id"),
                    name = Text.of(n.get("name")),
                    goods = goods.toIntArray(),
                    use = n.getInt("use", 0),
                    season = ints(n.get("season"), 4, 100),
                    service = n.getString("service", null),
                    level = n.getInt("level", 0),
                    weight = n.getInt("weight", 0),
                    vital = n.getBoolean("vital", false),
                    tier = n.getInt("tier", 1),
                ))
            }
            val needs0 = needs
            val levels = ArrayList<LevelDef>()
            each(root.get("levels")) { l -> levels.add(LevelDef(l.getInt("pop"), l.getInt("happy"), Text.of(l.get("name")))) }

            val buildings = ArrayList<BuildingDef>()
            each(root.get("buildings")) { b ->
                val id = b.getString("id")
                val l = b.get("look")
                val recipe = b.get("recipe")
                val needs = b.get("needs")
                buildings.add(
                    BuildingDef(
                        index = buildings.size,
                        id = id,
                        role = b.getString("role"),
                        size = b.getInt("size"),
                        buildable = b.getBoolean("buildable", true),
                        name = Text.of(b.get("name")),
                        cost = amounts(b.get("cost"), id),
                        build = b.getInt("build", 200),
                        capacity = b.getInt("capacity", 0),
                        beds = b.getInt("beds", 0),
                        builders = b.getInt("builders", 0),
                        workers = b.getInt("workers", 0),
                        tools = b.getBoolean("tools", false),
                        input = amounts(recipe?.get("in"), id),
                        output = amounts(recipe?.get("out"), id),
                        time = recipe?.getInt("time", 100) ?: 0,
                        needTile = needs?.getString("tile", null),
                        needRadius = needs?.getInt("radius", 0) ?: 0,
                        needMin = needs?.getInt("min", 0) ?: 0,
                        look = BuildingLook(hexColor(l.getString("wall")), hexColor(l.getString("roof")), l.getInt("wallH"), l.getInt("roofH")),
                        level = b.getInt("level", 0),
                        gold = b.getInt("gold", 0),
                        serves = b.getString("serves", null),
                        radius = b.getInt("radius", 0),
                        seasons = ints(b.get("seasons"), 4, 1).let { a -> BooleanArray(4) { a[it] != 0 } },
                        tier = b.getInt("tier", 1),
                        upgradeTo = b.getString("upgrade", null),
                        tax = b.getInt("tax", 100),
                        upkeep = b.getInt("upkeep", 0),
                        cat = b.getString("cat", "home"),
                        gather = b.getString("gather", null),
                        soil = b.getString("soil", null),
                        border = b.getInt("border", 0),
                        wonder = b.getBoolean("wonder", false),
                        prestige = b.getInt("prestige", 0),
                        fame = b.getInt("fame", 0),
                        distributes = distributedGoods(b.get("distributes"), needs0),
                    )
                )
            }
            val s = root.get("start")
            val start = StartDef(s.getString("hall"), s.getString("house"), s.getInt("houses"), s.getInt("citizens"), s.getInt("gold", 0), amounts(s.get("stock"), "start"))
            val bById = HashMap<String, Int>()
            for (b in buildings) bById[b.id] = b.index
            val scenarios = ArrayList<ScenarioDef>()
            if (scenariosJson != null) each(JsonReader().parse(scenariosJson).get("scenarios")) { sc ->
                val quests = ArrayList<QuestDef>()
                each(sc.get("quests")) { q ->
                    val type = Quest.type(q.getString("type"))
                    val a = when (type) {
                        Quest.STOCK, Quest.MADE -> byId[q.getString("resource")] ?: error("unknown resource in quest")
                        Quest.BUILD -> bById[q.getString("building")] ?: error("unknown building in quest")
                        else -> 0
                    }
                    val reward = q.get("reward")
                    var gold = 0; var resR = -1; var resN = 0
                    each(reward) { r ->
                        if (r.name == "gold") gold = r.asInt()
                        else { resR = byId[r.name] ?: error("unknown resource '${r.name}' in reward"); resN = r.asInt() }
                    }
                    quests.add(QuestDef(type, a, q.getInt("n"), q.getInt("days", 0), gold, resR, resN, q.get("hint")?.let { Text.of(it) }))
                }
                val fail = sc.get("fail")
                scenarios.add(ScenarioDef(
                    index = scenarios.size,
                    id = sc.getString("id"),
                    name = Text.of(sc.get("name")),
                    desc = Text.of(sc.get("desc")),
                    seed = sc.getInt("seed"),
                    size = sc.getString("size", "medium"),
                    startDay = sc.getInt("startDay", 1),
                    level = sc.getInt("level", 0),
                    gold = sc.getInt("gold", start.gold),
                    stock = sc.get("stock")?.let { amounts(it, sc.getString("id")) },
                    citizens = sc.getInt("citizens", start.citizens),
                    houses = sc.getInt("houses", start.houses),
                    quests = quests,
                    failPop = fail?.getInt("pop", 0) ?: 0,
                    failHappy = fail?.getInt("happy", 0) ?: 0,
                    deadline = fail?.getInt("deadline", 0) ?: 0,
                    threats = sc.getBoolean("threats", false),
                    neighbors = sc.getBoolean("neighbors", false),
                    gentle = sc.getBoolean("gentle", false),
                    raidEvery = sc.getInt("raidEvery", 0),
                    mapX = sc.get("map")?.get(0)?.asInt() ?: 50,
                    mapY = sc.get("map")?.get(1)?.asInt() ?: 50,
                ))
            }
            return Content(resources, buildings, needs, levels, start, scenarios)
        }

        /** Товары перечисленных потребностей (id), без повторов, по возрастанию индекса ресурса */
        private fun distributedGoods(v: JsonValue?, needs: List<NeedDef>): IntArray {
            val set = java.util.TreeSet<Int>()
            each(v) { c -> needs.firstOrNull { it.id == c.asString() }?.let { n -> for (g in n.goods) set.add(g) } }
            return set.toIntArray()
        }

        private inline fun each(v: JsonValue?, f: (JsonValue) -> Unit) {
            var c = v?.child
            while (c != null) {
                f(c)
                c = c.next
            }
        }

        /** "rrggbb" -> RGBA8888 с полной непрозрачностью */
        private fun hexColor(s: String): Long = (s.toLong(16) shl 8) or 0xff
    }
}
