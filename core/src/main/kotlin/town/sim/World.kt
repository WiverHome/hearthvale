package town.sim

import town.data.BuildingDef
import town.data.Content
import town.data.ScenarioDef

// Типы клеток
const val GRASS = 0
const val FOREST = 1
const val WATER = 2
const val ROAD = 3
const val BUILDING = 4
const val ROCK = 5
/** Стена: частокол и каменная (жители проходят - калитки, угрозы - нет) */
const val WALL_W = 6
const val WALL_S = 7
/** Мощёная дорога (быстрее обычной) и мост над водой */
const val PAVED = 8
const val BRIDGE = 9

// Состояние здания
const val B_SITE = 0
const val B_ACTIVE = 1
const val B_GONE = 2        // снесено; номер освободится для следующей постройки

// Почему здание (не) работает - для панели здания и будущих значков
const val BS_OK = 0
const val BS_SITE = 1
const val BS_NO_WORKERS = 2
const val BS_NO_RESOURCE = 3
const val BS_OUTPUT_FULL = 4
const val BS_NO_INPUT = 5
const val BS_WAITING = 6
const val BS_SEASON = 7     // не сезон (ферма зимой)
const val BS_LIMIT = 8      // на складах уже есть сколько велел игрок
const val BS_FAR = 9        // ждёт носильщиков: склад далеко
const val BS_FIRE = 10      // горит
const val BS_UNPAID = 11    // нет золота на содержание
const val BS_FLOOD = 12     // паводок: здание в воде

// почему нельзя строить (World.placeProblem)
const val PP_OK = 0
const val PP_EDGE = 1
const val PP_BUSY = 2       // x100 + клетка
const val PP_DOORWAY = 3    // вход другого здания
const val PP_DOOR = 4       // вход упирается
const val PP_PEOPLE = 5
const val PP_BORDER = 6
const val PP_UNREACHABLE = 7
const val PP_NEED = 8
const val PP_CUTS = 9

// События для ленты (не влияют на симуляцию): вид и номер здания (или другое число)
const val EV_HUNGRY = 1       // в доме кончилась еда, вода или дрова
const val EV_LEFT = 2         // житель ушёл из дома
const val EV_BUILT = 3        // достроено
const val EV_UPGRADING = 4    // дом начал улучшаться (ждёт материалов)
const val EV_UPGRADED = 5     // дом улучшен
const val EV_FIRE = 6         // пожар
const val EV_FIRE_OUT = 7     // пожар потушен (a - здание, b = 1 - запасы сгорели)
const val EV_SICK = 8         // болезнь в доме
const val EV_TRADER = 9       // пришёл торговец
const val EV_LEVEL = 10       // новый уровень города (a - уровень)
const val EV_QUEST = 11       // задание выполнено
const val EV_BAD_HARVEST = 12 // неурожай до зимы
const val EV_HARSH_WINTER = 13 // ожидается суровая зима
const val EV_STORE_FULL = 14  // склады переполнены, готовое некуда везти
const val EV_UNPAID = 15      // не хватило золота на содержание зданий
const val EV_FESTIVAL = 16    // в городе праздник (куплен в ратуше)
const val EV_RAID = 17        // угроза идёт: a - цель, b - вид (RAID_WOLVES, RAID_BANDITS)
const val EV_RAID_REPELLED = 18 // стража прогнала: a - караул, b - вид
const val EV_RAID_BLOCKED = 19  // стены отрезали путь: b - вид
const val EV_WOLVES = 20      // волки дошли: a - здание, b = 1 - ранили жителей, 0 - зарезали скот
const val EV_BANDITS = 21     // разбойники обчистили склад a: b = украдено товаров + золото x 1000
const val EV_CROWN = 22       // новый заказ короны: a - ресурс, b - сколько
const val EV_CROWN_DONE = 23  // заказ отдан: a - ресурс, b - золото
const val EV_CROWN_FAILED = 24 // заказ не отдан в срок: a - ресурс
const val EV_SHIP = 25        // пришёл корабль: a - пристань
const val EV_DAILY_END = 26   // партия дня окончена: a - очки
const val EV_FORECAST = 30    // прогноз: a - что кончается (FC_FOOD, FC_WATER, FC_HEAT, FC_GOLD), b - на сколько суток хватит
const val EV_FLOOD = 31       // паводок: b = 1 - вода пришла, 0 - сошла
const val EV_DROUGHT = 32     // засуха (событие на первые сутки)
const val EV_GROWN = 33       // в доме a вырос ребёнок (b = 1 - учёный)
const val EV_CONTRACT = 34    // договор с соседом a заключён
const val EV_WAGON_OUT = 35   // повозка ушла к соседу a
const val EV_WAGON_BACK = 36  // повозка вернулась от соседа a (b - ресурс + 1, 0 - золото)
const val EV_CONTRACT_FAIL = 37 // поставка соседу a сорвана (b = 1 - договор расторгнут)

// Сезоны
const val SPRING = 0
const val SUMMER = 1
const val AUTUMN = 2
const val WINTER = 3

/** Числа симуляции в одном месте */
object Tune {
    const val TICKS_PER_SECOND = 20
    const val SUB = 256                // доля клетки для координат с фиксированной точкой
    const val SPEED = 36               // шаг за тик по траве, в 1/SUB клетки
    const val PATH_BUDGET = 40         // сколько маршрутов считается за тик
    const val PATH_CACHE = 4096        // маршрутов в кэше, больше - кэш очищается
    const val COST_GRASS = 10
    const val COST_ROAD = 5
    const val COST_PAVED = 4           // мощёная дорога быстрее обычной
    const val COST_FOREST = 20

    const val TICKS_PER_DAY = 3000     // сутки = 2,5 минуты при скорости x1: день 2 минуты,
    const val NIGHT_START = 2400       // ночь 30 секунд - все дома
    /**
     * Сутки, под которые настроены числа «в сутки» в content.json (расход товаров домами, налог, содержание): 1200 тиков.
     * Расход, налоги и содержание пересчитываются на длину суток - темп экономики в минутах не зависит от TICKS_PER_DAY.
     */
    const val BASE_DAY = 1200

    const val CARRY = 5                // сколько единиц носильщик несёт за раз
    const val CARRIER_SHARE = 3        // не меньше 1/CARRIER_SHARE жителей остаются носильщиками
    const val IN_BUF = 6               // запас сырья в мастерской
    const val OUT_BUF = 10             // сколько готового помещается в мастерской
    const val ARRIVAL_INTERVAL = 240   // раз в столько тиков приходит новый житель, если есть кровать
    const val JOBS_INTERVAL = 10       // раз в столько тиков пересчёт профессий
    const val RELOCATE_INTERVAL = 200  // раз в столько тиков рабочие переселяются ближе к работе
    const val RELOCATE_NEAR = 6        // ближе этого к работе и так хорошо
    const val RELOCATE_GAIN = 4        // переезд, если до работы станет хотя бы на столько клеток ближе
    const val ASSIGN_BUDGET = 8        // сколько носильщиков ищут дело за тик
    const val RETRY = 40               // пауза носильщика, если дела нет или путь не найден
    const val IDLE_TO_STORE = 200      // столько тиков без дела на улице - и носильщик идёт к складу
    const val PICKUP_PENALTY = 12      // вывоз готового, пока мастерская не почти полна, менее срочен

    const val SEASON_DAYS = 7          // суток в сезоне, год = 4 сезона
    const val NEEDS_INTERVAL = 20      // раз в столько тиков дома тратят запасы и пересчитывают довольство
    const val HOUSE_BUF = 5            // сколько каждого нужного товара носильщики держат в доме
    const val HOUSE_PENALTY = 6        // подвоз в дома чуть менее срочен, чем в мастерские,
    const val URGENT_BONUS = 16        // а в дом, где нужный товар кончился, и на стройку - срочнее всего
    const val HAPPY_START = 70         // довольство нового дома, 0..100; меняется на 1 за NEEDS_INTERVAL
    const val ARRIVE_MIN = 40          // ниже этого довольства города новые жители не приходят
    const val LEAVE_BELOW = 25         // из дома с довольством ниже на рассвете уходит один житель
    const val VITAL_CAP = 20           // без жизненной потребности (еда, вода, тепло) довольство не выше
    const val TAX = 2                  // золото с жителя в сутки при довольстве 100
    const val UPKEEP_BASE = 3          // содержание здания x (UPKEEP_BASE + уровень города) / (UPKEEP_BASE + 1)
    const val UPKEEP_POP = 1           // x (1 + UPKEEP_POP промилле за жителя): большой город дороже в содержании
    // угрозы
    const val RAID_LEVEL = 1           // с какого уровня города приходят (волки); разбойники - с BANDIT_LEVEL
    const val BANDIT_LEVEL = 2
    const val WOLF_CHANCE = 30         // процентов на рассвете зимнего дня
    const val BANDIT_CHANCE = 6        // процентов в день, + 1 за каждые BANDIT_GOLD_STEP золота, не больше BANDIT_CHANCE_MAX
    const val BANDIT_GOLD_STEP = 500
    const val BANDIT_CHANCE_MAX = 20
    const val MAX_RAIDS = 4
    const val RAID_SPEED = 30          // шаг угрозы за тик, в 1/SUB клетки (жители - SPEED)
    const val GUARD_INTERVAL = 8       // как часто стража проверяет радиус (тиков)
    const val LEVEL_GUARD = 2          // радиус караула за уровень
    const val RAID_STEAL = 40          // разбойники уносят товаров
    const val RAID_GOLD_PCT = 10       // и процент казны
    const val RAID_GOLD_MAX = 400
    const val RAID_FIRE_CHANCE = 30    // процентов: поджигают здание
    const val FEAR_RADIUS = 8          // волки пугают дома в этом радиусе (по клеткам)
    const val FEAR_HAPPY = 15          // минус довольства
    const val WALL_WOOD_COST = 2       // брёвен на клетку частокола
    const val WALL_STONE_COST = 3      // камня на клетку каменной стены
    const val WALL_STONE_LEVEL = 3     // каменная стена - с уровня города
    const val HISTORY_RES = 120        // сколько суток помнить запас каждого товара на складах (графики)
    const val FORECAST_RED = 3         // меньше стольких суток запаса - тревога
    const val FORECAST_YELLOW = 7      // меньше стольких суток - предупреждение
    const val HISTORY = 720            // сколько суток помнить для итогов партии (жители, довольство, золото)
    const val LEVEL_TOOLS = 2          // инструментов на 3-й уровень мастерской, услуги, склада
    const val LEVEL_TOOLS2 = 1         // инструментов на 2-й уровень
    const val TOOL_LIFE = 24000        // тиков работы (циклов рецепта), на сколько хватает одного инструмента
    const val TOOL_SLOW = 30           // на сколько процентов медленнее работа без инструмента
    const val WONDER_HAPPY = 6         // собор: довольство всех домов выше на столько
    const val HIRE_GOLD = 25           // найм поселенца: HIRE_GOLD x (уровень города + 1) золота
    const val HIRE_MAX = 4             // за раз не больше
    const val FESTIVAL_PER_PERSON = 3  // праздник: золота с жителя (не меньше FESTIVAL_MIN)
    const val FESTIVAL_MIN = 100
    const val FESTIVAL_HAPPY = 15      // довольство домов на время праздника выше на столько
    const val FESTIVAL_DAYS = 2        // сколько суток длится
    const val FESTIVAL_WAIT_DAYS = 6   // следующий - не раньше, чем через столько суток после начала
    const val RUSH_MATERIAL = 3        // достроить за золото: недостающие материалы по базовой цене x RUSH_MATERIAL
    const val RUSH_WORK = 5            // и 1 золото за RUSH_WORK тиков оставшейся работы

    const val TRADER_EVERY = 1         // торговец приходит раз в столько суток
    const val TRADER_STAY = 1200       // и стоит у ратуши столько тиков (до полудня)
    const val PRICE_MIN = 80           // цены торговца - от PRICE_MIN до PRICE_MAX процентов базовых
    const val PRICE_MAX = 150
    const val SELL_SHARE = 60          // торговец покупает за столько процентов своей цены
    const val TRADER_SUPPLY = 10       // товара у торговца: TRADER_SUPPLY + случайно до TRADER_SUPPLY * 2,
    const val TRADER_SUPPLY_LEVEL = 6  // и больше на столько за каждый уровень города
    const val TRADER_CALL = 40         // позвать торговца вне очереди: TRADER_CALL + TRADER_CALL_LEVEL * уровень золота
    const val TRADER_CALL_LEVEL = 20

    const val REFUND = 50              // при сносе и переносе готового здания возвращается столько процентов материалов
    const val FAR = 24                 // склад дальше стольких клеток - «далеко носить»
    const val ROAD_STONE = 1           // камня со склада на клетку дороги
    const val PAVE_STONE = 2           // камня на клетку мощёной дороги
    const val PAVE_LEVEL = 2           // мостить можно с этого уровня города (Село)
    const val BRIDGE_PLANKS = 4        // досок на клетку моста
    const val BRIDGE_STONE = 1         // и камня
    const val BRIDGE_LEVEL = 1         // мосты - с этого уровня города
    const val CARRY_BARROW = 8         // носильщик с тачкой несёт за раз
    const val CARRY_WAGON = 12         // с телегой
    const val CART_WEAR = 25           // в сутки ломается 1/CART_WEAR тачек и телег
    val TAX_PCT = intArrayOf(60, 100, 150)   // налог: низкий / обычный / высокий, процентов
    val TAX_HAPPY = intArrayOf(8, 0, -12)    // и довольство домов от него
    const val PRIO_BONUS = 20          // заявки к срочной мастерской выгоднее на столько клеток, к второстепенной - дороже
    const val PRIO_SITE = 30           // срочная стройка
    const val MARKET_FILL = 6          // рынок держит столько каждого своего товара
    const val MARKET_CAP = 30          // и всего товаров
    const val DAILY_DAYS = 12          // партия дня длится столько суток
    // корона: заказы на товар за славу и золото
    const val CROWN_LEVEL = 3          // заказы - с этого уровня города
    const val CROWN_DAYS = 14          // срок заказа, суток
    const val CROWN_GAP = 7            // пауза до следующего заказа
    const val CROWN_PAY = 200          // награда золотом, процентов базовой цены
    const val CROWN_PRESTIGE = 25      // слава за заказ
    const val CROWN_FAIL = 15          // слава, потерянная за провал
    const val CROWN_SHARE = 60         // заказ - столько процентов недельной выработки товара
    const val CROWN_MIN = 20
    const val CROWN_MAX = 300
    const val FAME_HAPPY_HOUSES = 3    // +1 славы в сутки за каждые столько домов с довольством не ниже UPGRADE_HAPPY
    // корабль у пристани
    const val SHIP_EVERY = 3           // суток между кораблями, -1 за уровень пристани, не меньше 1
    const val SHIP_SHARE = 80          // корабль покупает за столько процентов своей цены
    const val SHIP_SUPPLY = 2          // товара у него вдвое больше
    const val SHIP_WANT = 3            // столько товаров корабль хочет дороже
    const val SHIP_WANT_MIN = 60       // надбавка к цене желанного товара, процентов
    const val SHIP_WANT_MAX = 100
    const val FAIR_FESTIVAL = 60       // с ярмаркой праздник стоит столько процентов
    const val FAIR_SHARE = 10          // и торговец платит на столько больше
    const val WONDER_SPEED = 15        // гильдейский дом: мастерские и добытчики быстрее на столько процентов
    val SEASON_FOOD = intArrayOf(0, 0, -15, 25)   // цены торговца на еду по сезонам, процентов
    val SEASON_LOGS = intArrayOf(0, 0, 10, 30)    // и на дрова

    const val UPGRADE_HAPPY = 80       // дом улучшается, если полон и довольство не ниже
    const val UPGRADE_DAYS = 1         // столько рассветов подряд
    const val FIRE_FROM_DAY = 3        // пожары не раньше этого дня
    const val FIRE_CHANCE = 500        // промилле на рассвете, что где-то загорится
    const val FIRE_TICKS = 800         // горит без каланчи; запасы потом пропадают
    const val FIRE_TICKS_WATCH = 200   // с каланчей рядом - быстро тушат, без потерь
    const val FIRE_HAPPY = 10          // довольство горящего дома не выше
    const val SICK_TICKS = 2400        // сколько болеет дом
    const val SICK_HAPPY = 30          // довольство больного дома не выше
    const val SICK_CHANCE_LOW = 100    // промилле на рассвете для дома с довольством ниже 70 (без лекаря рядом)
    const val SICK_CHANCE = 25         // для остальных домов без лекаря
    const val BAD_HARVEST_CHANCE = 25  // процентов в начале лета: поля работают вполсилы до зимы
    const val HARSH_WINTER_CHANCE = 33 // процентов в начале осени: зимой дров вдвое больше
    const val STAT_DAYS = 7            // сколько суток статистики хранить
    const val EVENTS = 48              // сколько событий в ленте

    const val MAX_QUESTS = 3           // сколько заданий одновременно
    const val SANDBOX_QUESTS = 2       // в песочнице держим столько заданий
    const val FAIL_GRACE = 2 * BASE_DAY  // столько времени на исправление, прежде чем сценарий провален

    const val MAX_AGENTS = 1500
    const val MAX_BUILDINGS = 1200

    // генерация на карте 128x128; на других размерах - пропорционально площади
    const val GEN_FORESTS = 45
    const val GEN_ROCKS = 14
    const val FISH_SHOALS = 14         // косяков рыбы на карте 128x128

    // природа (Nature.kt)
    const val TREE_LOGS = 6            // брёвен на зрелом дереве
    const val TREE_GROW_DAYS = 3       // саженец становится деревом за столько суток
    const val STUMP_DAYS = 1           // пень гниёт столько суток (потом луг)
    const val FOREST_SPREAD = 37       // промилле в сутки, что луг у леса (4+ соседа) зарастёт саженцем
    const val ROCK_MIN = 20            // камня в скале: от ROCK_MIN до ROCK_MAX
    const val ROCK_MAX = 45
    const val START_ROCK = 14          // песочница: скала не дальше стольких клеток от ратуши (иначе кладётся своя)
    const val START_ROCK_MIN = 7       // и не ближе этого
    const val FISH_MAX = 10            // рыбы в клетке косяка
    const val FISH_REGEN = 7           // рыбы прибавляется в клетке косяка за сутки
    const val GAME_MAX = 2             // дичи в клетке густого леса
    const val GAME_REGEN = 75          // процентов в сутки, что дичи прибавится
    const val GATHER_LOAD = 3          // циклов рецепта за одну ходку добытчика
    const val GATHER_WORK = 45         // работа у ресурса - столько процентов времени рецепта (остальное - дорога)
    const val NEAR_INTERVAL = 100      // раз в столько тиков пересчёт «ресурса рядом» у добытчиков

    // граница поселения: радиус от ратуши (растёт с уровнем города) и от достроенных складов
    const val BORDER_HALL = 18
    const val BORDER_LEVEL = 4

    // уровни зданий (мастерские, добытчики, услуги, склады): 1..3, по кнопке за материалы и золото
    const val MAX_LEVEL = 3
    // оценка сроков для игрока (Buildings.kt buildEta), подобрана по медианам бота (times.txt, 12 карт)
    const val ETA_BASE = 900           // тиков: очередь строек, строители и носильщики добираются до площадки
    const val ETA_UNIT = 46            // тиков на единицу материала (подвоз)
    const val ETA_WORK = 33            // процентов от def.build - работа (строителей трое)
    const val ETA_UPG_BASE = 200       // улучшение: до первой ходки (очереди строителей нет)
    const val ETA_UPG_UNIT = 26        // тиков на единицу материала улучшения
    const val LEVEL_SPEED = 25         // процентов скорости работы за уровень
    const val LEVEL_UPKEEP = 50        // процентов содержания за уровень
    const val LEVEL_CAPACITY = 50      // процентов вместимости склада за уровень
    const val LEVEL_BORDER = 3         // клеток границы за уровень у зданий со своей землёй (склад, вышка)
    const val LEVEL_SERVE = 2          // клеток радиуса услуги за уровень
    const val LEVEL_STONE = 4          // камня сверх материалов здания за уровень
    val LEVEL_GOLD = intArrayOf(0, 0, 30, 80)       // золота на уровень 2, 3
    val LEVEL_CITY = intArrayOf(0, 0, 1, 2)         // с какого уровня города (Деревня, Село)

    // строители: у ратуши def.builders, и ещё по одному за уровень города, пока строек больше, чем строителей
    const val BUILDERS_LEVEL = 1
    // тачки и телеги изнашиваются в работе: ломается одна из CART_TRIPS ходок с ними
    const val CART_TRIPS = 120

    // дети (Life.kt): рождаются у довольных домов, растут KID_DAYS суток, едят KID_EAT процентов взрослого
    const val KIDS_MAX = 2             // детей в доме не больше
    const val KID_HAPPY = 75           // довольство дома, при котором рождаются дети
    const val KID_CHANCE = 15          // процентов на рассвете (дом полон наполовину и больше)
    const val KID_LEVEL = 1            // дети рождаются с этого уровня города (Деревня)
    const val KID_DAYS = 6             // суток до взросления
    const val KID_EAT = 30             // ест столько процентов взрослого
    const val KID_SCHOOL_DAYS = 3      // столько суток в радиусе школы - и выросший учёный
    const val SKILL_BONUS = 25         // учёный работает быстрее на столько процентов

    // погода (Weather.kt): на рассвете сегодняшняя = вчерашний прогноз, прогноз на завтра - по сезону
    val WEATHER_ODDS = arrayOf(        // проценты: ясно, дождь, снег, туман, засуха - по сезонам
        intArrayOf(45, 35, 0, 20, 0),
        intArrayOf(55, 20, 0, 10, 15),
        intArrayOf(35, 40, 0, 25, 0),
        intArrayOf(40, 0, 45, 15, 0),
    )
    const val RAIN_FIELDS = 20         // дождь: поля, сады, пастбища быстрее на столько процентов
    const val RAIN_WALK = 90           // и все ходят по земле (не по мощёной) со столькими процентами скорости
    const val SNOW_WALK = 85           // снегопад
    const val DROUGHT_SOIL = 30        // засуха: поля, сады, пастбища и колодцы медленнее на столько процентов
    const val DROUGHT_FIRE = 150       // и шанс пожара - столько процентов обычного
    const val FOG_GUARD = 2            // туман: радиус караула меньше на столько клеток
    // весенний паводок у реки: низкие клетки у воды; постройки на них стоят, потом земля плодороднее до зимы
    const val FLOOD_CHANCE = 22        // процентов в каждый из первых FLOOD_WINDOW дней весны (за весну ~50%)
    const val FLOOD_WINDOW = 3
    const val KEY_QUEST_LEVEL = 2      // с этого уровня песочница сначала просит церковь и торговый пост
    const val FLOOD_DAYS = 2           // сколько суток стоит вода
    const val FLOOD_DIST = 2           // низкие клетки - не дальше стольких клеток от реки
    const val FLOOD_BONUS = 25         // плодородие после паводка выше на столько процентов (до зимы)

    // соседи (Neighbors.kt): 2-3 села за краем карты, договоры раз в неделю через торговый пост
    const val NB_MAX = 3
    const val NB_MIN = 2               // соседей в партии - от NB_MIN до NB_MAX
    const val CONTRACT_DAYS = 7        // поставка раз в столько суток
    const val CONTRACTS_MAX = 3        // договоров одновременно (не больше одного с каждым соседом)
    const val REL_START = 20           // отношения 0..100
    const val REL_OK = 8               // за поставку в срок
    const val REL_FAIL = 15            // за сорванную
    const val REL_GOOD = 50            // с такими отношениями сосед платит больше и предлагает больше
    const val REL_BONUS_DIV = 5        // надбавка соседа к цене - отношения / столько, процентов
    const val REL_GOOD_MORE = 50       // с хорошими отношениями товара в предложении больше на столько процентов
    const val REL_CANCEL_DIV = 3       // расторгли сами - отношения падают на REL_FAIL / столько
    const val CONTRACT_FIRST = 3       // первая поставка - через столько суток после договора
    const val OFFER_STEP = 5           // количество товара в предложении кратно этому (и не меньше)
    const val CONTRACT_FAILS = 2       // столько срывов подряд - договор расторгнут
    const val CONTRACT_LATE = 3        // пост горит или в воде - поставка переносится на завтра, но не больше стольких раз
    const val WAGON_SPEED = 26         // шаг повозки за тик, в 1/SUB клетки
    const val WAGON_AWAY = 600         // тиков повозка за краем карты (туда и обратно)
    const val OFFER_N_MIN = 10         // товара в поставке
    const val OFFER_N_MAX = 40
    const val OFFER_GAIN = 130         // сосед даёт за товар столько процентов его цены своим товаром
    const val OFFER_GOLD = 100         // или золотом
    // звёзды кампании за запас времени до срока, процентов
    const val STARS_3 = 35
    const val STARS_2 = 15

    // настройки песочницы: богатство ресурсов, стартовый запас, частота бедствий (0 - мало, 1 - обычно, 2 - много)
    val RICH_PCT = intArrayOf(60, 100, 150)
    val STOCK_PCT = intArrayOf(50, 100, 200)
    val DISASTER_PCT = intArrayOf(40, 100, 170)
}

/** Настройки новой песочницы: богатство ресурсов, стартовый запас, частота бедствий (0..2, 1 - обычно), спокойный режим */
class SandboxSettings(val rich: Int = 1, val stock: Int = 1, val disasters: Int = 1, val calm: Boolean = false)

/** Размер карты песочницы */
enum class MapSize(val tiles: Int) {
    SMALL(64), MEDIUM(96), LARGE(128);

    companion object {
        fun of(name: String) = values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
    }
}

// Итог партии
const val PLAYING = 0
const val WON = 1
const val LOST = 2

/** Команда игрока. Применяется в начале тика [tick]; журнал команд + сид = повтор партии. */
/** d - доп. параметр (PLACE, MOVE: сторона входа 0..3; у остальных 0) */
class Command(val tick: Int, val type: Int, val a: Int, val b: Int, val c: Int, val d: Int = 0) {
    companion object {
        const val PLACE = 1        // a = тип здания, b, c = левый нижний угол, d = сторона входа (0: -y, 1: +x, 2: +y, 3: -x)
        const val SET_WORKERS = 2  // a = здание, b = сколько рабочих нужно
        const val TRADE = 3        // a = ресурс, b > 0 - купить столько, b < 0 - продать
        const val SELL_ABOVE = 4   // a = ресурс, b = продавать торговцу всё сверх b на складах (-1 - не продавать)
        const val DEMOLISH = 5     // a = здание (площадка - полный возврат, готовое - REFUND процентов)
        const val MOVE = 6         // a = здание, b, c = новое место: там закладывается площадка, по готовности всё переезжает
        const val LIMIT = 7        // a = мастерская, b = не производить, если на складах уже b (-1 - без лимита)
        const val UNDO = 8         // отменить последнюю постройку или перенос, пока это площадка
        const val ROAD = 9         // a, b = клетка; c = 1 - проложить дорогу (луг -> дорога), 2 - замостить, 3 - мост, 0 - убрать
        const val CALL_TRADER = 10 // позвать торговца за золото (днём, если его нет)
        const val UPGRADE = 11     // a = здание: улучшить (дом - до следующего вида, остальные - уровень 2, 3)
        const val RUSH = 12        // a = площадка: достроить сейчас за золото (rushCost)
        const val FESTIVAL = 13    // праздник за золото (festivalCost): довольство выше на FESTIVAL_DAYS суток
        const val STOCK_LIMIT = 14 // a = ресурс, b = хранить в городе не больше b (-1 - без лимита): мастерские встают
        const val HIRE = 15        // a = сколько поселенцев нанять за золото (hireCost каждый), есть свободные кровати
        const val WALL = 16        // a, b = клетка; c = WALL_WOOD / WALL_STONE - поставить, 0 - убрать
        const val TAX = 17         // a = уровень налога 0..2 (низкий, обычный, высокий)
        const val PRIORITY = 18    // a = здание, b = -1 (второстепенно), 0, 1 (срочно)
        const val CROWN = 19       // отдать заказ короны, если на складах хватает
        const val STORE_ACCEPT = 20 // a = склад, b = ресурс (0..R-1) или набор (-1 всё, -2 ничего, -3 еда, -4 стройка, -5 ремесло), c = 1 принимает / 0 нет (наборы задают фильтр целиком, c не нужен)
        const val CONTRACT = 21    // a = сосед, b = 0..1 - заключить договор по его предложению b, -1 - расторгнуть
    }
}

private val FOOD_SET = setOf("bread", "fish", "meat", "apples", "smoked", "honey", "water", "grain", "flour", "pork", "ale")
private val BUILD_SET = setOf("logs", "planks", "stone", "ore", "coal", "iron", "tools")

/**
 * Мир: карта, здания (площадки и готовые), жители с профессиями. Всё на целых, свой RNG, обход по возрастанию
 * номеров - одинаковый результат на JVM, в браузере и на Android. Поведение жителей - в Citizens.kt.
 */
class World internal constructor(val content: Content, val seed: Int, val w: Int, val h: Int) {
    val R = content.resources.size
    /** Число потребностей домов */
    val N = content.needs.size
    internal val defs = content.buildings
    internal val rng = Rng(seed)
    val tiles = IntArray(w * h)
    val cost = IntArray(w * h)
    /** Номер здания на клетке или -1 (снесённых на карте нет) */
    val buildingAt = IntArray(w * h) { -1 }
    /** Номер здания, чей это вход, или -1 (на входы строить нельзя) */
    internal val entranceOf = IntArray(w * h) { -1 }
    /** Здание, чей вход в клетке c (-1 - ничей) */
    fun entranceOf(c: Int) = entranceOf[c]
    internal val pf = Pathfinder(w, h, cost, Tune.COST_PAVED)
    /** Ресурс на клетке (брёвна, камень, рыба), максимум рыбы косяка, суток до роста дерева / исчезновения пня, дичь */
    val res = IntArray(w * h)
    val resMax = IntArray(w * h)
    val grow = IntArray(w * h)
    val game = IntArray(w * h)
    /** Плодородие и подземная вода 0..100 (из сида, не сохраняются) */
    val fert = IntArray(w * h)
    val ground = IntArray(w * h)
    /** Клетка внутри границы поселения: строить (и класть дороги) можно только здесь; выводится из зданий и уровня */
    val border = BooleanArray(w * h)
    /** Меняется, когда природа меняет карту (вырубка, саженцы, выбранная скала) - для отрисовки; не сохраняется */
    var mapVersion = 0
        internal set
    /** Связные области проходимых клеток (по 4 соседям; -1 - непроходимо): строить можно, только куда дойти от ратуши */
    internal val region = IntArray(w * h)
    // поиск в ширину для проверки «не отрезает ли постройка входы» (метка вместо очистки массива)
    private val bfsQueue = IntArray(w * h)
    private val bfsSeen = IntArray(w * h)
    private var bfsMark = 0

    // ---- здания ----
    var buildingCount = 0
        internal set
    val bDef = IntArray(Tune.MAX_BUILDINGS)
    val bX = IntArray(Tune.MAX_BUILDINGS)
    val bY = IntArray(Tune.MAX_BUILDINGS)
    val bState = IntArray(Tune.MAX_BUILDINGS)
    /** Ход стройки, тиков работы строителей */
    val bBuild = IntArray(Tune.MAX_BUILDINGS)
    /** Ход текущего цикла производства */
    internal val bWork = IntArray(Tune.MAX_BUILDINGS)
    /** Сколько рабочих хочет игрок (по умолчанию - сколько мест) */
    val bWanted = IntArray(Tune.MAX_BUILDINGS)
    /** Сколько рабочих назначено */
    val bWorkers = IntArray(Tune.MAX_BUILDINGS)
    /** Сколько рабочих сейчас внутри (считается каждый тик) */
    val bInside = IntArray(Tune.MAX_BUILDINGS)
    val bResidents = IntArray(Tune.MAX_BUILDINGS)
    val bStatus = IntArray(Tune.MAX_BUILDINGS)
    /** Сколько клеток с ресурсом рядом (у добытчиков - где ещё есть что добывать) */
    val bNear = IntArray(Tune.MAX_BUILDINGS)
    /** Выработка в процентах (плодородие, подземная вода); у остальных 100 */
    val bYield = IntArray(Tune.MAX_BUILDINGS) { 100 }
    /** Уровень здания 1..MAX_LEVEL и привезённые для следующего уровня материалы (у домов - в inv) */
    val bLevel = IntArray(Tune.MAX_BUILDINGS) { 1 }
    val upInv = IntArray(Tune.MAX_BUILDINGS * R)
    /** Остаток жизни инструмента здания (у def.tools), тиков работы */
    val bTool = IntArray(Tune.MAX_BUILDINGS)
    /** Лимит «не больше N» на складах для мастерской (-1 - нет) */
    val bLimit = IntArray(Tune.MAX_BUILDINGS)
    /** Площадка переноса: какое здание сюда переедет (-1 - обычная постройка) */
    val bMoveFrom = IntArray(Tune.MAX_BUILDINGS)
    /** Куда смотрит здание (сторона входа): 0: -y, 1: +x, 2: +y, 3: -x; модель повёрнута так же */
    val bDir = IntArray(Tune.MAX_BUILDINGS)
    /** Запасы, ожидаемые поставки и зарезервированное к вывозу: [здание * R + ресурс] */
    val inv = IntArray(Tune.MAX_BUILDINGS * R)
    internal val incoming = IntArray(Tune.MAX_BUILDINGS * R)
    internal val reserved = IntArray(Tune.MAX_BUILDINGS * R)
    /** Запасы на складах по ресурсам - для лимитов «не больше N», считается раз за тик в produce */
    internal val totals = IntArray(R)
    /** Довольство дома 0..100 и какие потребности сейчас удовлетворены (бит на потребность) */
    val bHappy = IntArray(Tune.MAX_BUILDINGS)
    val bNeedOk = IntArray(Tune.MAX_BUILDINGS)
    /** Накопленный расход товаров дома: [здание * N + потребность], единица товара = 100 * TICKS_PER_DAY */
    internal val bNeedAcc = IntArray(Tune.MAX_BUILDINGS * N)
    var hall = -1
        internal set
    /** Последняя площадка, заложенная игроком (для отмены), или -1 */
    var lastPlaced = -1
        internal set

    // ---- город ----
    var gold = 0
        internal set
    /** Уровень города (номер в content.levels), не убывает */
    var level = 0
        internal set
    /** Только для съёмки экранов (desktop readmeShots): золото и уровень без игры; в журнал команд не пишется */
    fun debugGrant(gold: Int, level: Int) { this.gold = gold; this.level = level; computeBorder() }
    /** Только для съёмки экранов: погода сегодня и паводок (тиков) */
    fun debugWeather(w: Int, flood: Int) { weather = w; floodLeft = flood; if (flood > 0) floodYear = year }
    /** Довольство города: среднее по жителям */
    var townHappy = Tune.HAPPY_START
        internal set
    /** Сколько ещё тиков торговец у ратуши (0 - его нет) */
    var traderLeft = 0
    /** Праздник: сколько тиков ещё идёт; сколько тиков до следующего возможного */
    /** Угрозы включены (песочница - по выбору при создании, сценарий - по своему описанию) */
    var threatsOn = true
    /** Идущие угрозы: вид (0 - слот пуст), цель, путь и шаг по нему, позиция (1/SUB клетки), сколько их (для отрисовки) */
    val rType = IntArray(Tune.MAX_RAIDS)
    val rTarget = IntArray(Tune.MAX_RAIDS) { -1 }
    val rPath = arrayOfNulls<IntArray>(Tune.MAX_RAIDS)
    val rPos = IntArray(Tune.MAX_RAIDS)
    val rX = IntArray(Tune.MAX_RAIDS)
    val rY = IntArray(Tune.MAX_RAIDS)
    val rCount = IntArray(Tune.MAX_RAIDS)
    internal val rVersion = IntArray(Tune.MAX_RAIDS)
    /** Для сохранения: путь угрозы устарел (карта изменилась после расчёта); счётчики версий карты не сохраняются */
    private val rStale = IntArray(Tune.MAX_RAIDS)
    /** Сколько угроз прогнала стража, сколько остановили стены */
    var raidsRepelled = 0
    var raidsStopped = 0
    /** Сколько угроз приходило за партию и в какой день последняя */
    var raidsTotal = 0
    var raidLastDay = 0
    var festivalLeft = 0
    var festivalWait = 0
        internal set
    /** Налог: 0 - низкий, 1 - обычный, 2 - высокий */
    var taxLevel = 1
        internal set
    /** Приоритет здания: -1 второстепенно, 0 обычно, 1 срочно */
    val bPrio = IntArray(Tune.MAX_BUILDINGS)
    /** Фильтр склада: 1 - принимает ресурс на хранение, 0 - нет (ратуша принимает всё всегда) */
    val bAccept = IntArray(Tune.MAX_BUILDINGS * R) { 1 }
    /** Дата партии дня (ГГГГММДД), 0 - обычная партия; очки, зафиксированные при окончании (-1 - ещё идёт) */
    var dailyDate = 0
        internal set
    var dailyFinalScore = -1
        internal set
    /** Слава города: открывает здания (prestige в content.json), растёт от украшений и заказов короны */
    var prestige = 0
        internal set
    /** Заказ короны: ресурс (-1 - нет), сколько, до какого дня, награда золотом и славой; выполнено, провалено, когда следующий (день) */
    var crR = -1
        internal set
    var crN = 0
        internal set
    var crEnd = 0
        internal set
    var crGold = 0
        internal set
    var crPrestige = 0
        internal set
    var crDone = 0
        internal set
    var crFailed = 0
        internal set
    var crNext = 0
        internal set
    /** Пришёл корабль (торговец у пристани) и когда следующий (день) */
    var traderShip = false
        internal set
    var shipNext = 0
        internal set
    /** Выводится из зданий (не сохраняется): стоит гильдейский дом / работает ярмарка */
    var guildOn = false
        private set
    var fairOn = false
        private set
    /** Цены торговца в процентах от базовых и его товар */
    val traderPrice = IntArray(R) { 100 }
    val traderStock = IntArray(R)
    /** Корабль хочет этот товар дороже (1) */
    internal val shipWant = IntArray(R)
    /** Автопродажа: всё сверх этого на складах продаётся торговцу (-1 - выключено) */
    val sellAbove = IntArray(R) { -1 }
    /** Произведено за партию (до вчерашнего дня; сегодня - statProd) - задания «сделать N» */
    val madeTotal = IntArray(R)
    /** Итоги партии: по суткам (кольцо на Tune.HISTORY) жители, довольство, золото; dayCount - сколько суток записано */
    val dayPop = IntArray(Tune.HISTORY)
    val dayHappy = IntArray(Tune.HISTORY)
    val dayGold = IntArray(Tune.HISTORY)
    var dayCount = 0
    /** Запас каждого товара на складах на рассвете: кольцо на Tune.HISTORY_RES суток, строка - сутки, столбец - ресурс */
    val dayStock = IntArray(Tune.HISTORY_RES * R)
    /** Какие предупреждения прогноза уже выданы (биты по FC_*), пока не станет лучше - не повторяются */
    internal var fcState = 0
    /** Хранить в городе не больше (-1 - без лимита): все мастерские этого товара встают, когда на складах столько */
    val townLimit = IntArray(R) { -1 }

    // ---- погода и паводок (Weather.kt) ----
    /** Погода сегодня и прогноз на завтра: W_CLEAR, W_RAIN, W_SNOW, W_FOG, W_DROUGHT */
    var weather = W_CLEAR
        internal set
    var weatherNext = W_CLEAR
        internal set
    /** Паводок: сколько тиков ещё стоит вода (0 - нет); год (1, 2...), до зимы которого земля у реки плодороднее (0 - нет) */
    var floodLeft = 0
        internal set
    var floodYear = 0
        internal set
    /** Какие объяснения «впервые» (дождь, засуха, ребёнок, паводок, пост) уже показаны в этой партии - биты; только для интерфейса */
    var tipsSeen = 0
    /** Низкие клетки у реки - их заливает паводок (из карты, не сохраняется) */
    val low = BooleanArray(w * h)
    /** Здание стоит на низкой клетке (выводится, не сохраняется) */
    internal val bLow = BooleanArray(Tune.MAX_BUILDINGS)

    // ---- дети (Life.kt) ----
    /** Дети дома: суток до взросления (0 - места нет) и сколько суток провели в радиусе школы, [дом * KIDS_MAX + i] */
    val kidAge = IntArray(Tune.MAX_BUILDINGS * Tune.KIDS_MAX)
    val kidSchool = IntArray(Tune.MAX_BUILDINGS * Tune.KIDS_MAX)
    /** Сколько детей выросло за партию */
    var kidsGrown = 0
        internal set
    /** Учёных рабочих внутри здания (считается каждый тик) */
    internal val bSkilled = IntArray(Tune.MAX_BUILDINGS)

    // ---- соседи (Neighbors.kt) ----
    /** Сколько соседей; у каждого: край карты (0: -y, 1: +x, 2: +y, 3: -x), имя (номер в списке), чего много и что нужно, отношения */
    var nbCount = 0
        internal set
    val nbSide = IntArray(Tune.NB_MAX)
    val nbName = IntArray(Tune.NB_MAX)
    val nbSurplus = IntArray(Tune.NB_MAX)
    val nbNeed = IntArray(Tune.NB_MAX)
    val nbRel = IntArray(Tune.NB_MAX)
    /** Предложения соседа на эту неделю (2 на соседа, [сосед * 2 + k]): отдаём giveN товара giveR, получаем getN товара getR (-1 - золото) */
    val ofGiveR = IntArray(Tune.NB_MAX * 2) { -1 }
    val ofGiveN = IntArray(Tune.NB_MAX * 2)
    val ofGetR = IntArray(Tune.NB_MAX * 2) { -1 }
    val ofGetN = IntArray(Tune.NB_MAX * 2)
    /** Договор с соседом (ctGiveR -1 - нет): что и сколько отдаём, что получаем, день поставки, срывов подряд, поставок всего */
    val ctGiveR = IntArray(Tune.NB_MAX) { -1 }
    val ctGiveN = IntArray(Tune.NB_MAX)
    val ctGetR = IntArray(Tune.NB_MAX) { -1 }
    val ctGetN = IntArray(Tune.NB_MAX)
    val ctDue = IntArray(Tune.NB_MAX)
    val ctFails = IntArray(Tune.NB_MAX)
    /** Сколько раз нынешняя поставка уже перенесена (пост горит или в воде) */
    val ctLate = IntArray(Tune.NB_MAX)
    /** Сколько поставок соседям сделано за партию */
    var deliveries = 0
        internal set
    /** Когда соседи обновляют предложения (день) */
    var offersDay = 0
        internal set
    /** Повозка к соседу: состояние (0 - нет, 1 - едет туда, 2 - за краем, 3 - едет назад), торговый пост, путь, позиция */
    val wgState = IntArray(Tune.NB_MAX)
    val wgPost = IntArray(Tune.NB_MAX) { -1 }
    val wgPath = arrayOfNulls<IntArray>(Tune.NB_MAX)
    val wgPos = IntArray(Tune.NB_MAX)
    val wgX = IntArray(Tune.NB_MAX)
    val wgY = IntArray(Tune.NB_MAX)
    val wgTimer = IntArray(Tune.NB_MAX)
    /** Что повозка привезёт (ресурс, -1 - золото) и сколько */
    val wgGetR = IntArray(Tune.NB_MAX) { -1 }
    val wgGetN = IntArray(Tune.NB_MAX)

    // ---- настройки партии (песочница) ----
    /** Богатство ресурсов, стартовый запас, частота бедствий: 0 - мало, 1 - обычно, 2 - много */
    var setRich = 1
        internal set
    var setStock = 1
        internal set
    var setDisasters = 1
        internal set
    /** Спокойный режим: жители не уходят, угроз нет */
    var calm = false
        internal set

    /** Витрина (живая заставка заглавного экрана): без бедствий, угроз и ухода жителей, пожары потушены */
    fun makeShowcase() {
        showcase = true; calm = true; threatsOn = false
        bFire.fill(0)
    }
    /** Витрина: бедствий нет совсем (только заставка, в сохранение не пишется) */
    internal var showcase = false

    // ---- улучшения, бедствия ----
    /** Дом ждёт материалов для улучшения (1) или нет (0); сколько рассветов подряд готов к улучшению */
    val bUpg = IntArray(Tune.MAX_BUILDINGS)
    val bUpgDays = IntArray(Tune.MAX_BUILDINGS)
    /** Содержание не заплачено (1): здание стоит, пока не найдётся золото */
    val bUnpaid = IntArray(Tune.MAX_BUILDINGS)
    /** Сколько ещё тиков горит / болеет (0 - нет) */
    val bFire = IntArray(Tune.MAX_BUILDINGS)
    val bSick = IntArray(Tune.MAX_BUILDINGS)
    /** Каланча рядом была при начале пожара (тушат быстро, без потерь) */
    internal val bFireSafe = IntArray(Tune.MAX_BUILDINGS)
    /** Дому не хватало жизненной потребности при прошлой проверке (для события «голод» только при переходе) */
    internal val bHungry = IntArray(Tune.MAX_BUILDINGS)
    var badHarvest = false
        internal set
    var harshWinter = false
        internal set

    // ---- статистика: за текущие сутки и за прошлые STAT_DAYS (кольцо) ----
    internal val statProd = IntArray(R)
    internal val statUse = IntArray(R)
    internal val histProd = IntArray(R * Tune.STAT_DAYS)
    internal val histUse = IntArray(R * Tune.STAT_DAYS)
    /** Сколько суток истории накоплено (до STAT_DAYS) и куда пишется следующая */
    var histDays = 0
        internal set
    internal var histPos = 0
    /** Налоги, собранные на последнем рассвете */
    var lastTax = 0
        internal set
    /** Содержание зданий, заплаченное за сутки (с последнего рассвета) */
    var lastUpkeep = 0
        internal set

    // ---- лента событий (кольцо) ----
    internal val evType = IntArray(Tune.EVENTS)
    internal val evA = IntArray(Tune.EVENTS)
    internal val evB = IntArray(Tune.EVENTS)
    internal val evTick = IntArray(Tune.EVENTS)
    /** Сколько событий было всего (номер следующего); последние EVENTS лежат в кольце */
    var evCount = 0
        internal set

    // ---- задания и сценарий ----
    /** Номер сценария в content.scenarios или -1 (песочница) */
    var scenario = -1
        internal set
    /** Сколько заданий цепочки сценария уже выдано */
    var chainPos = 0
        internal set
    var outcome = PLAYING
        internal set
    /** Сколько тиков осталось до провала (-1 - условия провала не выполняются) */
    var failTimer = -1
        internal set
    var questsDone = 0
        internal set
    /** Задания: вид (0 - слот пуст), параметры, прогресс (суток подряд для HAPPY), награда, номер в цепочке сценария (-1 - сгенерировано) */
    val qType = IntArray(Tune.MAX_QUESTS)
    val qA = IntArray(Tune.MAX_QUESTS)
    val qB = IntArray(Tune.MAX_QUESTS)
    val qDays = IntArray(Tune.MAX_QUESTS)
    val qProg = IntArray(Tune.MAX_QUESTS)
    val qGold = IntArray(Tune.MAX_QUESTS)
    val qResR = IntArray(Tune.MAX_QUESTS)
    val qResN = IntArray(Tune.MAX_QUESTS)
    val qSrc = IntArray(Tune.MAX_QUESTS)
    val scenarioDef: ScenarioDef? get() = if (scenario >= 0) content.scenarios[scenario] else null

    // ---- жители ----
    var agentCount = 0
        internal set
    val px = IntArray(Tune.MAX_AGENTS)
    val py = IntArray(Tune.MAX_AGENTS)
    val prevX = IntArray(Tune.MAX_AGENTS)
    val prevY = IntArray(Tune.MAX_AGENTS)
    val state = IntArray(Tune.MAX_AGENTS)
    /** В каком здании житель (-1 - на улице) */
    val where = IntArray(Tune.MAX_AGENTS)
    val home = IntArray(Tune.MAX_AGENTS)
    val job = IntArray(Tune.MAX_AGENTS)
    /** Место работы (мастерская) или стройка строителя */
    val jobB = IntArray(Tune.MAX_AGENTS)
    internal val goal = IntArray(Tune.MAX_AGENTS)
    internal val goalB = IntArray(Tune.MAX_AGENTS)
    internal val timer = IntArray(Tune.MAX_AGENTS)
    internal val idle = IntArray(Tune.MAX_AGENTS)
    val carryR = IntArray(Tune.MAX_AGENTS)
    val carryN = IntArray(Tune.MAX_AGENTS)
    internal val taskSrc = IntArray(Tune.MAX_AGENTS)
    internal val taskDst = IntArray(Tune.MAX_AGENTS)
    internal val taskR = IntArray(Tune.MAX_AGENTS)
    internal val taskN = IntArray(Tune.MAX_AGENTS)
    /** Что везёт носильщик на время заявки: 0 - ничего, 1 - тачка, 2 - телега */
    val aCart = IntArray(Tune.MAX_AGENTS)
    /** Постоянный номер жителя (имя берётся из сида и номера) */
    val aSerial = IntArray(Tune.MAX_AGENTS)
    var nextSerial = 0
        internal set
    /** История: сутки прихода, смен работы, всего отнесено, самый частый груз (ресурс + 1, 0 - нет) и счётчик Мисры-Гриса */
    val aArrived = IntArray(Tune.MAX_AGENTS)
    val aJobChanges = IntArray(Tune.MAX_AGENTS)
    val aCarried = IntArray(Tune.MAX_AGENTS)
    val aFavR = IntArray(Tune.MAX_AGENTS)
    val aFavN = IntArray(Tune.MAX_AGENTS)
    /** Учёный (вырос у школы): работает быстрее на SKILL_BONUS процентов */
    val aSkill = IntArray(Tune.MAX_AGENTS)
    /** Индексы ресурсов тачки и телеги (-1 - в контенте нет: механика выключена) */
    internal val barrowR = content.resources.indexOfFirst { it.id == "barrow" }
    internal val wagonR = content.resources.indexOfFirst { it.id == "wagon" }
    internal val toolsR = content.resources.indexOfFirst { it.id == "tools" }
    /** Типы зданий, что делают товар жизненной потребности (еда, вода, дрова) - им рабочие раздаются первыми */
    internal val vitalDef: BooleanArray = run {
        val vital = BooleanArray(R)
        for (n in content.needs) if (n.vital) for (g in n.goods) vital[g] = true
        BooleanArray(defs.size) { i -> (0 until R).any { defs[i].output[it] > 0 && vital[it] } }
    }
    /** Торговый пост, куда свозят товар для договоров (на этот тик, собирается в assignTasks) */
    internal var tradeMain = -1
    /** Рабочие рынки на этот тик (собирается в assignTasks) */
    internal val markets = IntArray(Tune.MAX_BUILDINGS)
    internal var marketCount = 0
    internal val paths = arrayOfNulls<IntArray>(Tune.MAX_AGENTS)
    /** Добытчик: клетка ресурса и где у неё встать */
    internal val gCell = IntArray(Tune.MAX_AGENTS) { -1 }
    internal val gStand = IntArray(Tune.MAX_AGENTS) { -1 }
    internal val pathPos = IntArray(Tune.MAX_AGENTS)

    /** Все целые массивы жителя (кроме paths) - для переноса жителя на другой номер */
    internal fun agentArrays() = arrayOf(
        px, py, prevX, prevY, state, where, home, job, jobB, goal, goalB, timer, idle,
        carryR, carryN, taskSrc, taskDst, taskR, taskN, pathPos, gCell, gStand, aCart,
        aSerial, aArrived, aJobChanges, aCarried, aFavR, aFavN, aSkill,
    )

    // очередь запросов маршрута (кольцо)
    internal val queue = IntArray(Tune.MAX_AGENTS)
    internal var qHead = 0
    internal var qSize = 0

    // команды: ожидающие (по тикам) и уже применённые
    internal val pending = ArrayList<Command>()
    val log = ArrayList<Command>()

    var tick = 0
        internal set
    var pathsDone = 0L
        internal set
    var nodesExpanded = 0L
        internal set
    val queueSize get() = qSize
    val day get() = tick / Tune.TICKS_PER_DAY + 1
    val isNight get() = tick % Tune.TICKS_PER_DAY >= Tune.NIGHT_START
    val season get() = (tick / Tune.TICKS_PER_DAY / Tune.SEASON_DAYS) % 4
    val year get() = tick / Tune.TICKS_PER_DAY / Tune.SEASON_DAYS / 4 + 1
    val traderHere get() = traderLeft > 0
    /** Цена вызова торговца и можно ли позвать его сейчас (днём, его нет, золота хватает) */
    fun traderCallPrice() = Tune.TRADER_CALL + Tune.TRADER_CALL_LEVEL * level

    /** Найм поселенца: цена растёт с уровнем города */
    fun hireCost() = Tune.HIRE_GOLD * (level + 1)

    fun freeBeds(): Int {
        var n = 0
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).isHouse) n += def(b).beds - bResidents[b]
        return n
    }

    /** Есть ли готовое чудо с этим id (собор, замок) */
    fun hasWonder(id: String): Boolean {
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).wonder && def(b).id == id) return true
        return false
    }

    /** Чудо одно на город: уже стоит или строится */
    fun wonderTaken(defIndex: Int): Boolean {
        if (!defs[defIndex].wonder) return false
        for (b in 0 until buildingCount) if (bState[b] != B_GONE && bDef[b] == defIndex) return true
        return false
    }

    /** Праздник: цена растёт с числом жителей */
    fun festivalCost() = maxOf(Tune.FESTIVAL_MIN, agentCount * Tune.FESTIVAL_PER_PERSON) * (if (fairOn) Tune.FAIR_FESTIVAL else 100) / 100
    fun canFestival() = festivalLeft == 0 && festivalWait == 0 && gold >= festivalCost() && agentCount > 0

    /** Достроить площадку сейчас: недостающие материалы втридорога и оставшаяся работа */
    fun rushCost(s: Int): Int {
        if (s !in 0 until buildingCount || bState[s] != B_SITE) return 0
        val d = def(s)
        var c = 0
        for (r in 0 until R) c += maxOf(0, d.cost[r] - inv[s * R + r]) * content.resources[r].price * Tune.RUSH_MATERIAL
        return c + (maxOf(0, d.build - bBuild[s]) + Tune.RUSH_WORK - 1) / Tune.RUSH_WORK
    }
    fun canCallTrader() = !traderHere && !isNight && hall >= 0 && gold >= traderCallPrice()

    companion object {
        /** Новая песочница: пустая долина с ратушей, парой домов и первыми жителями */
        fun create(content: Content, seed: Int, size: MapSize, threats: Boolean = true, settings: SandboxSettings = SandboxSettings()): World {
            val world = World(content, seed, size.tiles, size.tiles)
            world.calm = settings.calm
            world.threatsOn = threats && !settings.calm
            world.setRich = settings.rich.coerceIn(0, 2)
            world.setStock = settings.stock.coerceIn(0, 2)
            world.setDisasters = settings.disasters.coerceIn(0, 2)
            world.generate()
            world.setupStart(null)
            return world
        }

        /** Партия дня: одна карта для всех на дату (ГГГГММДД), средняя, с угрозами; длится Tune.DAILY_DAYS суток */
        fun createDaily(content: Content, date: Int): World {
            val world = World(content, date * 2654435761L.toInt() xor 0x5bd1e995, MapSize.MEDIUM.tiles, MapSize.MEDIUM.tiles)
            world.threatsOn = true
            world.dailyDate = date
            world.generate()
            world.setupStart(null)
            return world
        }

        /** Новая партия по сценарию: карта по его сиду, стартовые условия и первое задание цепочки */
        fun create(content: Content, sc: ScenarioDef): World {
            val size = MapSize.of(sc.size)
            val world = World(content, sc.seed, size.tiles, size.tiles)
            world.threatsOn = sc.threats
            world.generate()
            world.scenario = sc.index
            world.setupStart(sc)
            world.nextScenarioQuest()
            return world
        }

        internal fun scale(v: Int, area: Int) = maxOf(1, v * area / (128 * 128))
    }

    fun def(b: Int): BuildingDef = defs[bDef[b]]
    fun entrance(b: Int) = entranceCell(bX[b], bY[b], def(b).size, bDir[b])

    /** Клетка входа здания размера s в (x, y), обращённого стороной dir (вход - у первой клетки этой стороны) */
    fun entranceCell(x: Int, y: Int, s: Int, dir: Int): Int = when (dir and 3) {
        0 -> (y - 1) * w + x
        1 -> y * w + x + s
        2 -> (y + s) * w + x + s - 1
        else -> (y + s - 1) * w + x - 1
    }

    /** Вход внутри карты */
    private fun entranceInMap(x: Int, y: Int, s: Int, dir: Int): Boolean = when (dir and 3) {
        0 -> y >= 1
        1 -> x + s < w
        2 -> y + s < h
        else -> x >= 1
    }
    fun stock(b: Int, r: Int) = inv[b * R + r]

    /** Всего ресурса на складах (ратуша и склады) */
    fun totalStock(r: Int): Int {
        var n = 0
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).isStore) n += inv[b * R + r]
        return n
    }

    /** Можно ли отменить последнюю постройку */
    val canUndo get() = lastPlaced in 0 until buildingCount && bState[lastPlaced] == B_SITE

    /** Строится ли перенос этого здания */
    fun movingTo(b: Int): Int {
        for (s in 0 until buildingCount) if (bState[s] == B_SITE && bMoveFrom[s] == b) return s
        return -1
    }

    /** Произведено / потрачено ресурса r за сутки d назад (d = 1 - вчера; 0 - текущие сутки) */
    fun produced(r: Int, d: Int = 1): Int = if (d == 0) statProd[r] else if (d > histDays) 0 else histProd[histIndex(d) * R + r]
    fun used(r: Int, d: Int = 1): Int = if (d == 0) statUse[r] else if (d > histDays) 0 else histUse[histIndex(d) * R + r]
    private fun histIndex(d: Int) = Math.floorMod(histPos - d, Tune.STAT_DAYS)

    /** Событие в ленту (только для показа, на симуляцию не влияет) */
    internal fun event(type: Int, a: Int, b: Int = 0) {
        val i = evCount % Tune.EVENTS
        evType[i] = type; evA[i] = a; evB[i] = b; evTick[i] = tick
        evCount++
    }

    /** Событие номер n (от 0 до evCount-1; доступны последние EVENTS): вид, a, b, тик */
    fun eventAt(n: Int): IntArray {
        val i = n % Tune.EVENTS
        return intArrayOf(evType[i], evA[i], evB[i], evTick[i])
    }

    fun totalBeds(): Int {
        var n = 0
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE) n += def(b).beds
        return n
    }

    internal fun updateCost(i: Int) {
        val c = when (tiles[i]) {
            GRASS -> Tune.COST_GRASS
            ROAD, BRIDGE -> Tune.COST_ROAD
            PAVED -> Tune.COST_PAVED
            WALL_W, WALL_S -> Tune.COST_GRASS
            FOREST -> Tune.COST_FOREST
            else -> 0
        }
        if (cost[i] != c) { cost[i] = c; costVersion++ }
    }

    // ---------- кэш маршрутов ----------

    /** Меняется при любой смене проходимости клетки (стройка, дорога, вырубка) - кэш маршрутов тогда сбрасывается */
    internal var costVersion = 0

    /** Меняется при любой смене клеток или проходимости - угрозы тогда ищут путь заново */
    internal fun raidVer() = costVersion * 31 + mapVersion
    private var cacheVersion = -1
    /**
     * Кэш маршрутов (откуда, куда) -> путь и сколько клеток раскрыл A*. Носильщики ходят между одними и теми же входами,
     * поэтому большинство поисков повторяются. Не сохраняется: путь из кэша тот же, что дал бы A* на той же карте, а бюджет
     * поисков за тик и статистика считаются одинаково - после загрузки (кэш пуст) симуляция идёт тик в тик.
     */
    private val pathCache = HashMap<Long, IntArray>()
    private val pathCacheNodes = HashMap<Long, Int>()
    private val noPath = IntArray(0)
    var pathCacheHits = 0L
        private set

    /** Маршрут из клетки start в goal (без start) или null; lastExpanded - сколько клеток раскрыл бы A* */
    internal fun findPath(start: Int, goal: Int): IntArray? {
        if (cacheVersion != costVersion || pathCache.size >= Tune.PATH_CACHE) {
            pathCache.clear(); pathCacheNodes.clear(); cacheVersion = costVersion
        }
        val key = start.toLong() * (w * h) + goal
        val hit = pathCache[key]
        if (hit != null) {
            pathCacheHits++
            lastExpanded = pathCacheNodes[key]!!
            return if (hit === noPath) null else hit
        }
        val path = pf.find(start, goal)
        lastExpanded = pf.lastExpanded
        pathCache[key] = path ?: noPath
        pathCacheNodes[key] = lastExpanded
        return path
    }
    internal var lastExpanded = 0

    // ---------- генерация ----------

    private fun generate() {
        val area = w * h
        val rich = Tune.RICH_PCT[setRich]
        repeat(scale(Tune.GEN_FORESTS * rich / 100, area)) { blob(FOREST, 2 + rng.next(6), 80) }
        // река сверху вниз, ширина 3, с тремя бродами
        var rx = w / 3 + rng.next(w / 3)
        val riverX = IntArray(h)
        for (y in 0 until h) {
            rx += rng.next(3) - 1
            if (rx < 4) rx = 4
            if (rx > w - 5) rx = w - 5
            riverX[y] = rx
            for (x in rx - 1..rx + 1) tiles[y * w + x] = WATER
        }
        for (by in intArrayOf(h / 6, h / 2, h * 5 / 6)) {
            val bx = riverX[by]
            for (x in bx - 1..bx + 1) tiles[by * w + x] = ROAD
        }
        repeat(scale(Tune.GEN_ROCKS * rich / 100, area)) { blob(ROCK, 1 + rng.next(3), 70) }
        for (i in 0 until area) updateCost(i)
        generateNature()
        computeLow()
    }

    /** Пятно клеток type радиусом r (вода и броды не трогаются) */
    private fun blob(type: Int, r: Int, percent: Int) {
        val cx = rng.next(w); val cy = rng.next(h)
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            val i = y * w + x
            if (tiles[i] == WATER || tiles[i] == ROAD) continue
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy <= r * r && rng.next(100) < percent) tiles[i] = type
        }
    }

    private fun setupStart(sc: ScenarioDef?) {
        val start = content.start
        if (sc != null) {
            tick = (sc.startDay - 1) * Tune.TICKS_PER_DAY
            level = sc.level
        }
        val hallDef = content.building(start.hall)
        val hx = spiral(hallDef.index, w / 2, h / 2) ?: error("no place for the town hall")
        hall = addBuilding(hallDef.index, hx % w, hx / w, B_ACTIVE)
        // песочница: камень должен быть в своей земле с начала (иначе нет каменоломни и город упирается в потолок)
        if (sc == null) ensureStartRock()
        val stock = sc?.stock ?: start.stock
        val sp = if (sc == null) Tune.STOCK_PCT[setStock] else 100
        for (r in 0 until R) inv[hall * R + r] = stock[r] * sp / 100
        gold = (sc?.gold ?: start.gold) * sp / 100
        val houseDef = content.building(start.house)
        repeat(sc?.houses ?: start.houses) {
            val p = spiral(houseDef.index, bX[hall] + 1, bY[hall] - 3, reach = true) ?: return@repeat
            addBuilding(houseDef.index, p % w, p / w, B_ACTIVE)
        }
        repeat(sc?.citizens ?: start.citizens) { spawnCitizen() }
        computeRegions()
        computeBorder()
        // соседи за краем карты: песочница, партия дня и сценарии, где они есть
        if (sc == null || sc.neighbors) setupNeighbors()
        weatherNext = rollWeather(season)
        refreshDerived()
    }

    /**
     * Нет скал ближе START_ROCK клеток от ратуши - кладём небольшой выход камня (7 клеток) на лугу в кольце
     * START_ROCK_MIN..START_ROCK от неё: первое подходящее место по обходу (без RNG - карты со скалами не меняются)
     */
    private fun ensureStartRock() {
        val cx = bX[hall] + 1; val cy = bY[hall] + 1
        val r = Tune.START_ROCK
        for (y in maxOf(0, cy - r)..minOf(h - 1, cy + r)) for (x in maxOf(0, cx - r)..minOf(w - 1, cx + r)) {
            if (tiles[y * w + x] == ROCK && (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) return
        }
        val shape = intArrayOf(0, 0, 1, 0, -1, 0, 0, 1, 0, -1, 1, 1, -1, -1)
        for (d in Tune.START_ROCK_MIN..r - 2) for (dy in -d..d) for (dx in -d..d) {
            if (maxOf(Math.abs(dx), Math.abs(dy)) != d) continue
            val x = cx + dx; val y = cy + dy
            var ok = true
            for (yy in y - 2..y + 2) for (xx in x - 2..x + 2) {
                if (xx !in 0 until w || yy !in 0 until h) { ok = false; continue }
                val i = yy * w + xx
                if (tiles[i] != GRASS && tiles[i] != FOREST && tiles[i] != ROCK) ok = false
                if (buildingAt[i] >= 0 || entranceOf[i] >= 0) ok = false
            }
            if (!ok) continue
            for (k in 0 until shape.size step 2) {
                val i = (y + shape[k + 1]) * w + x + shape[k]
                tiles[i] = ROCK; res[i] = Tune.ROCK_MAX; grow[i] = 0; game[i] = 0
                updateCost(i)
            }
            mapVersion++
            return
        }
    }

    /** Ближайшее по спирали от (cx, cy) место, куда можно поставить здание; клетка левого нижнего угла */
    private fun spiral(defIndex: Int, cx: Int, cy: Int, reach: Boolean = false): Int? {
        for (r in 0 until maxOf(w, h)) {
            for (dy in -r..r) for (dx in -r..r) {
                if (maxOf(Math.abs(dx), Math.abs(dy)) != r) continue
                val x = cx + dx; val y = cy + dy
                if (!canPlace(defIndex, x, y)) continue
                // стартовый дом: до входа можно дойти от ратуши, и он никого не отрезает
                if (reach && (region[entranceCell(x, y, defs[defIndex].size, 0)] != region[entrance(hall)] || !keepsConnected(defIndex, x, y))) continue
                return y * w + x
            }
        }
        return null
    }

    // ---------- здания ----------

    /**
     * Можно ли поставить здание: основание - луг внутри карты и не на чужом входе, вход (клетка под левым нижним углом)
     * - луг или дорога, на основании нет жителей.
     */
    fun canPlace(defIndex: Int, x: Int, y: Int, dir: Int = 0): Boolean {
        val s = defs[defIndex].size
        if (x < 0 || y < 0 || x + s > w || y + s > h || !entranceInMap(x, y, s, dir)) return false
        for (yy in y until y + s) for (xx in x until x + s) {
            val i = yy * w + xx
            if (tiles[i] != GRASS || entranceOf[i] >= 0) return false
        }
        val e = tiles[entranceCell(x, y, s, dir)]
        if (e != GRASS && e != ROAD && e != PAVED) return false
        for (a in 0 until agentCount) {
            if (state[a] == ST_INSIDE) continue
            val ax = px[a] / Tune.SUB; val ay = py[a] / Tune.SUB
            if (ax >= x && ax < x + s && ay >= y && ay < y + s) return false
        }
        return true
    }

    /** Входит ли ресурс r в набор склада p (-1 всё, -2 ничего, -3 еда, -4 стройка, -5 ремесло) */
    fun storePresetHas(p: Int, r: Int): Boolean {
        val id = content.resources[r].id
        return when (p) {
            -1 -> true
            -2 -> false
            -3 -> id in FOOD_SET
            -4 -> id in BUILD_SET
            -5 -> id !in FOOD_SET && id !in BUILD_SET
            else -> false
        }
    }

    /** Склад s принимает ресурс r (ратуша - всё) */
    fun storeAccepts(s: Int, r: Int) = r < 0 || def(s).role == "hall" || bAccept[s * R + r] != 0

    internal fun addBuilding(defIndex: Int, x: Int, y: Int, st: Int, dir: Int = 0): Int {
        val def = defs[defIndex]
        // номер снесённого здания используется снова
        var b = 0
        while (b < buildingCount && bState[b] != B_GONE) b++
        if (b == buildingCount) buildingCount++
        bDef[b] = defIndex; bX[b] = x; bY[b] = y; bState[b] = st
        bWork[b] = 0; bWorkers[b] = 0; bInside[b] = 0; bResidents[b] = 0
        bLimit[b] = -1; bMoveFrom[b] = -1; bDir[b] = dir and 3; bPrio[b] = 0
        for (r in 0 until R) bAccept[b * R + r] = 1
        for (r in 0 until R) if (def.output[r] > 0 && content.resources[r].limit >= 0) bLimit[b] = content.resources[r].limit
        for (r in 0 until R) { inv[b * R + r] = 0; incoming[b * R + r] = 0; reserved[b * R + r] = 0 }
        bBuild[b] = if (st == B_ACTIVE) def.build else 0
        bWanted[b] = def.workers
        bHappy[b] = Tune.HAPPY_START
        bNeedOk[b] = 0
        bLevel[b] = 1; bTool[b] = 0; for (r in 0 until R) upInv[b * R + r] = 0
        for (k in 0 until Tune.KIDS_MAX) { kidAge[b * Tune.KIDS_MAX + k] = 0; kidSchool[b * Tune.KIDS_MAX + k] = 0 }
        bUpg[b] = 0; bUpgDays[b] = 0; bFire[b] = 0; bSick[b] = 0; bFireSafe[b] = 0; bHungry[b] = 0; bUnpaid[b] = 0
        for (n in 0 until N) bNeedAcc[b * N + n] = 0
        bStatus[b] = if (st == B_SITE) BS_SITE else BS_OK
        for (yy in y until y + def.size) for (xx in x until x + def.size) {
            val i = yy * w + xx
            tiles[i] = BUILDING
            buildingAt[i] = b
            updateCost(i)
        }
        entranceOf[entrance(b)] = b
        computeRegions()
        computeBorder()
        bNear[b] = countNear(b)
        bYield[b] = yieldFor(defIndex, x, y)
        bLow[b] = lowUnder(b)
        return b
    }

    internal fun needTileCode(name: String?): Int = when (name) {
        "forest" -> FOREST
        "water" -> WATER
        "rock" -> ROCK
        else -> -1
    }

    /** Сколько нужных зданию клеток в радиусе от его центра */
    internal fun countNear(b: Int): Int {
        val def = def(b)
        if (def.gatherCode != G_NONE) return scanGather(def.index, bX[b], bY[b], -1, -1, gatherRadius(b))
        val t = needTileCode(def.needTile)
        if (t < 0) return 0
        val cx = bX[b] + def.size / 2; val cy = bY[b] + def.size / 2
        var n = 0
        for (y in cy - def.needRadius..cy + def.needRadius) for (x in cx - def.needRadius..cx + def.needRadius) {
            if (x in 0 until w && y in 0 until h && tiles[y * w + x] == t) n++
        }
        return n
    }

    /**
     * Граница поселения: круг вокруг ратуши (BORDER_HALL + BORDER_LEVEL x уровень) и вокруг каждого достроенного здания
     * со своей землёй (поле border в content.json: склад, вышка, каланча, церковь; + LEVEL_BORDER за уровень здания).
     * Пересчёт при постройке, сносе, достройке, новом уровне и загрузке.
     */
    /** Счётчик пересчётов границы - для отрисовки (плавный край), не состояние мира */
    var borderVer = 0
        private set

    internal fun computeBorder() {
        borderVer++
        java.util.Arrays.fill(border, false)
        for (b in 0 until buildingCount) {
            if (bState[b] != B_ACTIVE) continue
            val def = def(b)
            val r = when {
                b == hall -> Tune.BORDER_HALL + Tune.BORDER_LEVEL * level
                def.border > 0 -> def.border + Tune.LEVEL_BORDER * (bLevel[b] - 1)
                else -> continue
            }
            // центр здания в половинках клетки
            val cx2 = bX[b] * 2 + def.size; val cy2 = bY[b] * 2 + def.size
            for (y in maxOf(0, bY[b] - r)..minOf(h - 1, bY[b] + def.size + r)) for (x in maxOf(0, bX[b] - r)..minOf(w - 1, bX[b] + def.size + r)) {
                val dx = x * 2 + 1 - cx2; val dy = y * 2 + 1 - cy2
                if (dx * dx + dy * dy <= 4 * r * r) border[y * w + x] = true
            }
        }
    }

    /** Здание со входом целиком внутри границы */
    fun insideBorder(defIndex: Int, x: Int, y: Int, dir: Int = 0): Boolean {
        val s = defs[defIndex].size
        if (x < 0 || y < 0 || x + s > w || y + s > h || !entranceInMap(x, y, s, dir)) return false
        for (yy in y until y + s) for (xx in x until x + s) if (!border[yy * w + xx]) return false
        return border[entranceCell(x, y, s, dir)]
    }

    /** Пересчёт связных областей после изменения карты (постройка, снос, загрузка) */
    internal fun computeRegions() {
        java.util.Arrays.fill(region, -1)
        val stack = IntArray(w * h)
        var next = 0
        for (s in 0 until w * h) {
            if (region[s] >= 0 || cost[s] == 0) continue
            var top = 0
            stack[top++] = s
            region[s] = next
            while (top > 0) {
                val c = stack[--top]
                val cx = c % w; val cy = c / w
                if (cx > 0) visit(c - 1, next, stack, top).let { top = it }
                if (cx < w - 1) visit(c + 1, next, stack, top).let { top = it }
                if (cy > 0) visit(c - w, next, stack, top).let { top = it }
                if (cy < h - 1) visit(c + w, next, stack, top).let { top = it }
            }
            next++
        }
    }

    private fun visit(c: Int, id: Int, stack: IntArray, top: Int): Int {
        if (region[c] >= 0 || cost[c] == 0) return top
        region[c] = id
        stack[top] = c
        return top + 1
    }

    /** Останутся ли после постройки все входы (и вход нового здания) достижимы от ратуши */
    fun keepsConnected(defIndex: Int, x: Int, y: Int, dir: Int = 0): Boolean {
        if (hall < 0) return true
        val s = defs[defIndex].size
        fun blocked(c: Int): Boolean {
            if (cost[c] == 0) return true
            val cx = c % w; val cy = c / w
            return cx >= x && cx < x + s && cy >= y && cy < y + s
        }
        bfsMark++
        var head = 0; var tail = 0
        val start = entrance(hall)
        bfsQueue[tail++] = start; bfsSeen[start] = bfsMark
        while (head < tail) {
            val c = bfsQueue[head++]
            val cx = c % w; val cy = c / w
            for (k in 0 until 4) {
                val nx = cx + (if (k == 0) 1 else if (k == 1) -1 else 0)
                val ny = cy + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val n = ny * w + nx
                if (bfsSeen[n] == bfsMark || blocked(n)) continue
                bfsSeen[n] = bfsMark
                bfsQueue[tail++] = n
            }
        }
        if (bfsSeen[entranceCell(x, y, s, dir)] != bfsMark) return false
        // входы, до которых и без этой стройки не дойти (другая связная область), не в счёт - иначе один такой вход запрещает строить всё
        for (b in 0 until buildingCount) if (bState[b] != B_GONE && region[entrance(b)] == region[entrance(hall)] && bfsSeen[entrance(b)] != bfsMark) return false
        return true
    }

    /** Можно ли игроку строить здесь: место свободно, до входа можно дойти от ратуши и рядом хватает нужных клеток */
    fun canBuild(defIndex: Int, x: Int, y: Int, dir: Int = 0): Boolean {
        val def = defs[defIndex]
        if (!def.buildable || !canPlace(defIndex, x, y, dir) || !insideBorder(defIndex, x, y, dir)) return false
        if (hall >= 0 && region[entranceCell(x, y, def.size, dir)] != region[entrance(hall)]) return false
        // нужные клетки рядом - дешёвая проверка до обхода связности (тот же итог, но в разы быстрее)
        if (!((def.needTile == null && def.gatherCode == G_NONE) || nearIfPlaced(defIndex, x, y) >= def.needMin)) return false
        return keepsConnected(defIndex, x, y, dir)
    }

    /**
     * Почему здесь нельзя строить (для подсказки при постановке): PP_* или PP_BUSY * 100 + клетка-помеха
     * (тот же порядок проверок, что в canBuild); PP_OK - можно.
     */
    fun placeProblem(defIndex: Int, x: Int, y: Int, dir: Int = 0): Int {
        val def = defs[defIndex]
        val s = def.size
        if (x < 0 || y < 0 || x + s > w || y + s > h || !entranceInMap(x, y, s, dir)) return PP_EDGE
        for (yy in y until y + s) for (xx in x until x + s) {
            val i = yy * w + xx
            if (entranceOf[i] >= 0) return PP_DOORWAY
            if (tiles[i] != GRASS) return PP_BUSY * 100 + tiles[i]
        }
        val e = tiles[entranceCell(x, y, s, dir)]
        if (e != GRASS && e != ROAD && e != PAVED) return PP_DOOR
        for (a in 0 until agentCount) {
            if (state[a] == ST_INSIDE) continue
            val ax = px[a] / Tune.SUB; val ay = py[a] / Tune.SUB
            if (ax >= x && ax < x + s && ay >= y && ay < y + s) return PP_PEOPLE
        }
        if (!insideBorder(defIndex, x, y, dir)) return PP_BORDER
        if (hall >= 0 && region[entranceCell(x, y, s, dir)] != region[entrance(hall)]) return PP_UNREACHABLE
        if (!((def.needTile == null && def.gatherCode == G_NONE) || nearIfPlaced(defIndex, x, y) >= def.needMin)) return PP_NEED
        if (!keepsConnected(defIndex, x, y, dir)) return PP_CUTS
        return PP_OK
    }

    /** Сколько нужных клеток будет рядом, если поставить здание в (x, y) - для предпросмотра */
    fun nearIfPlaced(defIndex: Int, x: Int, y: Int): Int {
        val def = defs[defIndex]
        if (def.gatherCode != G_NONE) return resourceCells(defIndex, x, y)
        val t = needTileCode(def.needTile)
        if (t < 0) return 0
        val cx = x + def.size / 2; val cy = y + def.size / 2
        var n = 0
        for (yy in cy - def.needRadius..cy + def.needRadius) for (xx in cx - def.needRadius..cx + def.needRadius) {
            if (xx in 0 until w && yy in 0 until h && tiles[yy * w + xx] == t) n++
        }
        return n
    }

    // ---------- дороги ----------

    /** Можно ли проложить дорогу на клетке: луг внутри карты и на складах есть камень */
    fun canRoad(x: Int, y: Int): Boolean =
        x in 0 until w && y in 0 until h && tiles[y * w + x] == GRASS && border[y * w + x] && stockAvailable(content.resource("stone").index) >= Tune.ROAD_STONE

    /** Можно ли замостить клетку: луг или дорога внутри границы, город дорос, камень есть на складах */
    fun canPave(x: Int, y: Int): Boolean {
        if (x !in 0 until w || y !in 0 until h || level < Tune.PAVE_LEVEL) return false
        val c = y * w + x
        return (tiles[c] == GRASS || tiles[c] == ROAD) && border[c] && stockAvailable(content.resource("stone").index) >= Tune.PAVE_STONE
    }

    /** Можно ли построить мост: вода внутри границы с берегом (проходимой клеткой или мостом) по 4 сторонам, доски и камень есть */
    fun canBridge(x: Int, y: Int): Boolean {
        if (x !in 0 until w || y !in 0 until h || level < Tune.BRIDGE_LEVEL) return false
        val c = y * w + x
        if (tiles[c] != WATER || !border[c]) return false
        if (stockAvailable(content.resource("planks").index) < Tune.BRIDGE_PLANKS || stockAvailable(content.resource("stone").index) < Tune.BRIDGE_STONE) return false
        for (k in 0 until 4) {
            val nx = x + (if (k == 0) 1 else if (k == 1) -1 else 0)
            val ny = y + (if (k == 2) 1 else if (k == 3) -1 else 0)
            if (nx !in 0 until w || ny !in 0 until h) continue
            val n = ny * w + nx
            if (tiles[n] == BRIDGE || (tiles[n] != WATER && cost[n] > 0)) return true
        }
        return false
    }

    /** Можно ли убрать дорогу: дорога (не брод, рядом нет воды), мощёная или мост (на нём никого и входы не отрезает) */
    fun canRemoveRoad(x: Int, y: Int): Boolean {
        if (x !in 0 until w || y !in 0 until h) return false
        val t = tiles[y * w + x]
        if (t == PAVED) return true
        if (t == BRIDGE) return bridgeRemovable(y * w + x)
        if (t != ROAD) return false
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && tiles[ny * w + nx] == WATER) return false
        }
        return true
    }

    private fun bridgeRemovable(cell: Int): Boolean {
        for (a in 0 until agentCount) {
            if (state[a] == ST_INSIDE) continue
            if ((py[a] / Tune.SUB) * w + px[a] / Tune.SUB == cell) return false
        }
        return connectedWithout(cell)
    }

    /** Все входы из области ратуши остаются достижимыми, если клетка cell станет непроходимой */
    private fun connectedWithout(cell: Int): Boolean {
        if (hall < 0) return true
        bfsMark++
        var head = 0; var tail = 0
        val start = entrance(hall)
        bfsQueue[tail++] = start; bfsSeen[start] = bfsMark
        while (head < tail) {
            val c = bfsQueue[head++]
            val cx = c % w; val cy = c / w
            for (k in 0 until 4) {
                val nx = cx + (if (k == 0) 1 else if (k == 1) -1 else 0)
                val ny = cy + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val n = ny * w + nx
                if (bfsSeen[n] == bfsMark || cost[n] == 0 || n == cell) continue
                bfsSeen[n] = bfsMark
                bfsQueue[tail++] = n
            }
        }
        for (b in 0 until buildingCount) if (bState[b] != B_GONE && region[entrance(b)] == region[start] && bfsSeen[entrance(b)] != bfsMark) return false
        return true
    }

    /** Свободного (не забронированного) ресурса на складах */
    fun stockAvailable(r: Int): Int {
        var n = 0
        for (s in 0 until buildingCount) if (bState[s] == B_ACTIVE && defs[bDef[s]].isStore) n += inv[s * R + r] - reserved[s * R + r]
        return n
    }

    private fun buildRoad(x: Int, y: Int) {
        if (!canRoad(x, y)) return
        takeFromStores(content.resource("stone").index, Tune.ROAD_STONE)
        tiles[y * w + x] = ROAD
        updateCost(y * w + x)
    }

    private fun buildPaved(x: Int, y: Int) {
        if (!canPave(x, y)) return
        takeFromStores(content.resource("stone").index, Tune.PAVE_STONE)
        tiles[y * w + x] = PAVED
        updateCost(y * w + x)
        mapVersion++
    }

    private fun buildBridge(x: Int, y: Int) {
        if (!canBridge(x, y)) return
        takeFromStores(content.resource("planks").index, Tune.BRIDGE_PLANKS)
        takeFromStores(content.resource("stone").index, Tune.BRIDGE_STONE)
        val c = y * w + x
        tiles[c] = BRIDGE
        res[c] = 0; resMax[c] = 0
        updateCost(c)
        computeRegions()
        mapVersion++
    }

    private fun removeRoad(x: Int, y: Int) {
        if (!canRemoveRoad(x, y)) return
        val c = y * w + x
        when (tiles[c]) {
            PAVED -> {
                var ford = false
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && tiles[ny * w + nx] == WATER) ford = true
                }
                tiles[c] = if (ford) ROAD else GRASS
                updateCost(c)
                mapVersion++
            }
            BRIDGE -> {
                tiles[c] = WATER
                updateCost(c)
                computeRegions()
                rerouteThrough(x, y, 1)
                mapVersion++
            }
            else -> { tiles[c] = GRASS; updateCost(c) }
        }
    }

    /** Можно ли перенести здание: готовое, не ратуша, перенос ещё не начат */
    fun canMove(b: Int) = b != hall && bState[b] == B_ACTIVE && defs[bDef[b]].buildable && movingTo(b) < 0

    /** Открыто ли здание: хватает уровня города и славы */
    fun unlocked(defIndex: Int) = defs[defIndex].level <= level && defs[defIndex].prestige <= prestige

    /** Почему здание закрыто: null - открыто, "lock_level" - мал город, "lock_prestige" - мало славы */
    fun lockReason(defIndex: Int): String? = when {
        defs[defIndex].level > level -> "lock_level"
        defs[defIndex].prestige > prestige -> "lock_prestige"
        else -> null
    }
    /**
     * Можно ли заложить: золото есть, и все материалы уже лежат на складах - с учётом обещанного
     * другим стройкам (нельзя заложить десяток строек «в долг»)
     */
    fun canAfford(defIndex: Int) = gold >= defs[defIndex].gold && hasMaterials(defIndex)

    fun hasMaterials(defIndex: Int): Boolean {
        val def = defs[defIndex]
        for (r in 0 until R) if (def.cost[r] > 0 && freeStock(r) < def.cost[r]) return false
        return true
    }

    /** Материалы, обещанные незавершённым стройкам и улучшениям: ещё не привезены и не в пути */
    fun pledged(r: Int): Int {
        var n = 0
        for (s in 0 until buildingCount) {
            if (bState[s] == B_SITE) n += maxOf(0, def(s).cost[r] - inv[s * R + r] - incoming[s * R + r])
            else if (bState[s] == B_ACTIVE && bUpg[s] == 1) {
                val got = if (def(s).isHouse) inv[s * R + r] else upInv[s * R + r]
                n += maxOf(0, upgradeCost(s, r) - got - incoming[s * R + r])
            }
        }
        return n
    }

    // ---------- уровни зданий ----------

    /** Есть уровни: мастерские и добытчики, услуги с работниками, склады (не дома, не ратуша, не украшения) */
    fun hasLevels(b: Int): Boolean {
        val d = def(b)
        return !d.wonder && (d.isProducer || (d.isService && !d.isDecor) || d.role == "store" || d.isTower || d.isGuard)
    }

    /** Материалы следующего уровня (дом - стоимость следующего вида дома) */
    fun upgradeCost(b: Int, r: Int): Int {
        val d = def(b)
        if (d.isHouse) return d.upgradeTo?.let { content.building(it).cost[r] } ?: 0
        val next = bLevel[b] + 1
        val k = next - 1   // уровень 2 - материалы здания, уровень 3 - вдвое
        val stone = if (content.resources[r].id == "stone") Tune.LEVEL_STONE * k else 0
        // 2-й уровень - один инструмент из кузницы, 3-й - больше
        val tools = if (next >= 2 && content.resources[r].id == "tools") (if (next >= 3) Tune.LEVEL_TOOLS else Tune.LEVEL_TOOLS2) else 0
        return d.cost[r] * k + stone + tools
    }

    fun upgradeGold(b: Int): Int {
        val d = def(b)
        if (d.isHouse) return d.upgradeTo?.let { content.building(it).gold } ?: 0
        return Tune.LEVEL_GOLD[minOf(bLevel[b] + 1, Tune.MAX_LEVEL)]
    }

    /** Дом готов к улучшению: полон и доволен UPGRADE_DAYS рассветов, следующий вид открыт */
    fun houseReady(b: Int): Boolean {
        val d = def(b)
        val next = d.upgradeTo?.let { content.building(it) } ?: return false
        return d.isHouse && bState[b] == B_ACTIVE && bUpg[b] == 0 && bUpgDays[b] >= Tune.UPGRADE_DAYS && unlocked(next.index)
    }

    /** Почему нельзя улучшить сейчас (ключ строки) или null - можно */
    fun upgradeBlock(b: Int): String? {
        if (b !in 0 until buildingCount || bState[b] != B_ACTIVE || bUpg[b] != 0) return "upg_no"
        val d = def(b)
        if (d.isHouse) {
            if (d.upgradeTo == null) return "upg_max"
            if (!unlocked(content.building(d.upgradeTo).index)) return "upg_city"
            if (bUpgDays[b] < Tune.UPGRADE_DAYS) return "upg_house_wait"
        } else {
            if (!hasLevels(b)) return "upg_no"
            if (bLevel[b] >= Tune.MAX_LEVEL) return "upg_max"
            if (level < Tune.LEVEL_CITY[bLevel[b] + 1]) return "upg_city"
        }
        if (gold < upgradeGold(b)) return "no_gold"
        for (r in 0 until R) if (upgradeCost(b, r) > 0 && freeStock(r) < upgradeCost(b, r)) return "no_materials"
        return null
    }

    /** Скорость работы в процентах: уровень здания (у мастерских, добытчиков, услуг) */
    fun levelSpeed(b: Int) = 100 + Tune.LEVEL_SPEED * (bLevel[b] - 1) + (if (guildOn && def(b).isProducer) Tune.WONDER_SPEED else 0)

    /** Скорость работы с учётом инструмента: без него на TOOL_SLOW процентов медленнее */
    fun workSpeed(b: Int) = levelSpeed(b) * (if (def(b).tools && bTool[b] <= 0) 100 - Tune.TOOL_SLOW else 100) / 100

    /** Износ инструмента: amount тиков работы; кончился - берётся запасной из мастерской */
    internal fun wearTool(b: Int, amount: Int) {
        if (!def(b).tools) return
        bTool[b] -= amount
        takeSpareTool(b)
    }

    internal fun takeSpareTool(b: Int) {
        if (bTool[b] > 0 || toolsR < 0 || inv[b * R + toolsR] <= 0) return
        inv[b * R + toolsR]--; statUse[toolsR]++
        bTool[b] += Tune.TOOL_LIFE
        if (bTool[b] <= 0) bTool[b] = Tune.TOOL_LIFE
    }

    /** Рабочих мест: на 3-м уровне - на одно больше */
    fun maxWorkers(b: Int): Int {
        val w0 = def(b).workers
        return if (w0 > 0 && bLevel[b] >= Tune.MAX_LEVEL) w0 + 1 else w0
    }

    /** Радиус услуги и добычи с учётом уровня */
    fun serveRadius(b: Int) = def(b).radius + Tune.LEVEL_SERVE * (bLevel[b] - 1)
    fun gatherRadius(b: Int) = def(b).needRadius + (bLevel[b] - 1)

    /** Вместимость склада с учётом уровня */
    fun capacityOf(b: Int) = def(b).capacity * (100 + Tune.LEVEL_CAPACITY * (bLevel[b] - 1)) / 100

    /** Свободно на складах: не забронировано носильщиками и не обещано стройкам */
    fun freeStock(r: Int) = stockAvailable(r) - pledged(r)

    // ---------- команды ----------

    /**
     * Команда от игрока применяется сразу (и на паузе - так планируют стройку), а в журнал пишется со следующим тиком:
     * при повторе она применится в начале того тика, до всего остального, - состояние то же самое.
     */
    fun submit(type: Int, a: Int, b: Int, c: Int, d: Int = 0) {
        val cmd = Command(tick + 1, type, a, b, c, d)
        apply(cmd)
        log.add(cmd)
    }

    /** Свободна ли постройка (новых) зданий: есть место под площадку */
    private fun hasSlot() = buildingCount < Tune.MAX_BUILDINGS || (0 until buildingCount).any { bState[it] == B_GONE }

    /** Для повтора: команды с их исходными тиками */
    fun schedule(commands: List<Command>) {
        pending.addAll(commands)
    }

    private fun applyCommands() {
        var i = 0
        while (i < pending.size) {
            val cmd = pending[i]
            if (cmd.tick == tick) {
                pending.removeAt(i)
                apply(cmd)
                log.add(cmd)
            } else i++
        }
    }

    /** Производное от зданий (не сохраняется): гильдейский дом и ярмарка; после каждой команды и тика, при загрузке */
    internal fun refreshDerived() {
        guildOn = hasWonder("guildhall")
        var fair = false
        for (b in 0 until buildingCount) if (bState[b] == B_ACTIVE && def(b).serves == "fair" && bWorkers[b] > 0) { fair = true; break }
        fairOn = fair
    }

    private fun apply(cmd: Command) {
        applyCmd(cmd)
        refreshDerived()
    }

    private fun applyCmd(cmd: Command) {
        when (cmd.type) {
            Command.PLACE -> if (cmd.a in defs.indices && unlocked(cmd.a) && canAfford(cmd.a) && !wonderTaken(cmd.a) &&
                canBuild(cmd.a, cmd.b, cmd.c, cmd.d) && hasSlot()
            ) {
                gold -= defs[cmd.a].gold
                lastPlaced = addBuilding(cmd.a, cmd.b, cmd.c, B_SITE, cmd.d)
                rerouteThrough(cmd.b, cmd.c, defs[cmd.a].size)
            }
            Command.MOVE -> if (cmd.a in 0 until buildingCount && canMove(cmd.a) && canBuild(bDef[cmd.a], cmd.b, cmd.c, cmd.d) && hasMaterials(bDef[cmd.a]) && hasSlot()) {
                // золото за перенос не берётся, материалы - как за новую постройку
                val s = addBuilding(bDef[cmd.a], cmd.b, cmd.c, B_SITE, cmd.d)
                bMoveFrom[s] = cmd.a
                lastPlaced = s
                rerouteThrough(cmd.b, cmd.c, def(s).size)
            }
            Command.DEMOLISH -> if (cmd.a in 0 until buildingCount && cmd.a != hall && bState[cmd.a] != B_GONE) demolish(cmd.a, -1)
            Command.UNDO -> if (canUndo) { demolish(lastPlaced, -1); lastPlaced = -1 }
            Command.LIMIT -> if (cmd.a in 0 until buildingCount && def(cmd.a).isProducer) bLimit[cmd.a] = maxOf(-1, cmd.b)
            Command.ROAD -> when (cmd.c) {
                1 -> buildRoad(cmd.a, cmd.b)
                2 -> buildPaved(cmd.a, cmd.b)
                3 -> buildBridge(cmd.a, cmd.b)
                else -> removeRoad(cmd.a, cmd.b)
            }
            Command.TAX -> taxLevel = cmd.a.coerceIn(0, 2)
            Command.PRIORITY -> if (cmd.a in 0 until buildingCount) {
                val v = cmd.b.coerceIn(-1, 1)
                val d = def(cmd.a)
                if (bState[cmd.a] == B_SITE) { if (v >= 0) bPrio[cmd.a] = v }
                else if (bState[cmd.a] == B_ACTIVE && (d.isProducer || d.isService) && d.workers > 0) bPrio[cmd.a] = v
            }
            Command.CROWN -> if (canDeliverCrown()) deliverCrown()
            Command.CONTRACT -> if (cmd.b < 0) cancelContract(cmd.a) else signContract(cmd.a, cmd.b)
            Command.STORE_ACCEPT -> if (cmd.a in 0 until buildingCount && bState[cmd.a] == B_ACTIVE && def(cmd.a).role == "store") {
                if (cmd.b in 0 until R) bAccept[cmd.a * R + cmd.b] = if (cmd.c != 0) 1 else 0
                else if (cmd.b in -5..-1) for (r in 0 until R) bAccept[cmd.a * R + r] = if (storePresetHas(cmd.b, r)) 1 else 0
            }
            Command.SET_WORKERS -> if (cmd.a in 0 until buildingCount && def(cmd.a).workers > 0) {
                bWanted[cmd.a] = cmd.b.coerceIn(0, maxWorkers(cmd.a))
            }
            Command.TRADE -> if (cmd.a in 0 until R) {
                if (cmd.b > 0) buy(cmd.a, cmd.b) else if (cmd.b < 0) sell(cmd.a, -cmd.b)
            }
            Command.SELL_ABOVE -> if (cmd.a in 0 until R) {
                sellAbove[cmd.a] = maxOf(-1, cmd.b)
                autoSell()
            }
            Command.UPGRADE -> if (upgradeBlock(cmd.a) == null) {
                gold -= upgradeGold(cmd.a)
                bUpg[cmd.a] = 1
                bUpgDays[cmd.a] = 0
                event(EV_UPGRADING, cmd.a)
            }
            Command.RUSH -> if (cmd.a in 0 until buildingCount && bState[cmd.a] == B_SITE && gold >= rushCost(cmd.a)) {
                gold -= rushCost(cmd.a)
                rush(cmd.a)
            }
            Command.STOCK_LIMIT -> if (cmd.a in 0 until R) townLimit[cmd.a] = maxOf(-1, cmd.b)
            Command.WALL -> if (cmd.c == 0) removeWall(cmd.a, cmd.b) else buildWall(cmd.a, cmd.b, cmd.c)
            Command.HIRE -> {
                var n = minOf(cmd.a, Tune.HIRE_MAX)
                while (n-- > 0 && gold >= hireCost() && freeBeds() > 0 && agentCount < Tune.MAX_AGENTS) {
                    gold -= hireCost()
                    spawnCitizen()
                }
            }
            Command.FESTIVAL -> if (canFestival()) {
                gold -= festivalCost()
                festivalLeft = (Tune.FESTIVAL_DAYS + if (fairOn) 1 else 0) * Tune.TICKS_PER_DAY
                festivalWait = Tune.FESTIVAL_WAIT_DAYS * Tune.TICKS_PER_DAY
                event(EV_FESTIVAL, hall)
            }
            Command.CALL_TRADER -> if (canCallTrader()) {
                gold -= traderCallPrice()
                traderArrive()
            }
        }
    }

    /** Жители, чей оставшийся путь проходит через новое здание, заново просят маршрут */
    private fun rerouteThrough(x: Int, y: Int, s: Int) {
        for (a in 0 until agentCount) {
            if (state[a] != ST_MOVE) continue
            val path = paths[a] ?: continue
            for (k in pathPos[a] until path.size) {
                val cx = path[k] % w; val cy = path[k] / w
                if (cx >= x && cx < x + s && cy >= y && cy < y + s) {
                    paths[a] = null
                    enqueue(a)
                    break
                }
            }
        }
    }

    // ---------- тик ----------

    fun step() {
        tick++
        applyCommands()
        System.arraycopy(px, 0, prevX, 0, agentCount)
        System.arraycopy(py, 0, prevY, 0, agentCount)
        if (tick % Tune.TICKS_PER_DAY == 0) { weatherDawn(); dawn(); disasters(); natureDawn(); threatsDawn(); lifeDawn(); neighborsDawn() }
        if (floodLeft > 0 && --floodLeft == 0) event(EV_FLOOD, 0, 0)
        wagonsStep()
        if (tick % Tune.NEAR_INTERVAL == 0) updateNear()
        if (traderLeft > 0 && --traderLeft == 0) traderShip = false
        raidsStep()
        if (festivalLeft > 0) festivalLeft--
        if (festivalWait > 0) festivalWait--
        if (tick % Tune.NEEDS_INTERVAL == 0) { updateNeeds(); updateQuests(); finishLevels() }
        if (tick % Tune.ARRIVAL_INTERVAL == 0 && townHappy >= Tune.ARRIVE_MIN) spawnCitizen()
        if (tick % Tune.JOBS_INTERVAL == 0) assignJobs()
        if (tick % Tune.RELOCATE_INTERVAL == 0) relocate()
        processPaths()
        countInside()
        if (!isNight) assignTasks()
        for (a in 0 until agentCount) think(a)
        produce()
        refreshDerived()
    }

    /** Сумма состояния для сравнения веба, JVM и Android */
    fun checksum(): Int {
        var hsh = tick
        for (a in 0 until agentCount) {
            hsh = hsh * 31 + px[a]
            hsh = hsh * 31 + py[a]
            hsh = hsh * 31 + state[a] * 7 + job[a]
            hsh = hsh * 31 + carryN[a]
        }
        hsh = hsh * 31 + gold
        hsh = hsh * 31 + questsDone * 7 + outcome
        hsh = hsh * 31 + (if (badHarvest) 2 else 0) + (if (harshWinter) 1 else 0)
        hsh = hsh * 31 + level * 1000 + townHappy
        hsh = hsh * 31 + prestige * 7 + taxLevel * 3 + crDone
        hsh = hsh * 31 + weather * 11 + weatherNext * 3 + floodLeft + kidsGrown * 7 + deliveries * 13
        for (i in 0 until nbCount) hsh = hsh * 31 + nbRel[i] * 7 + ctGiveR[i] * 3 + wgState[i] + wgX[i] + wgY[i]
        for (k in 0 until buildingCount * Tune.KIDS_MAX) hsh = hsh * 31 + kidAge[k] + kidSchool[k] * 5
        for (i in 0 until w * h) if (res[i] != 0 || game[i] != 0) hsh = hsh * 31 + i * 7 + res[i] * 3 + game[i]
        for (b in 0 until buildingCount) {
            hsh = hsh * 31 + bHappy[b]
            hsh = hsh * 31 + bFire[b] * 3 + bSick[b] * 5 + bUpg[b] + bUnpaid[b] * 11 + bLevel[b] * 13
            hsh = hsh * 31 + bDef[b] * 65536 + bY[b] * 256 + bX[b]
            hsh = hsh * 31 + bState[b] * 100000 + bBuild[b]
            for (r in 0 until R) hsh = hsh * 31 + inv[b * R + r]
        }
        return hsh
    }

    /** Сохранение и загрузка одним описанием полей (см. SaveCodec) */
    internal fun io(s: StateIO) {
        tick = s.int("tick", tick)
        rng.s = s.int("rng", rng.s)
        pathsDone = s.long("pathsDone", pathsDone)
        nodesExpanded = s.long("nodes", nodesExpanded)
        s.ints("tiles", tiles, w * h)
        s.ints("res", res, w * h); s.ints("resMax", resMax, w * h); s.ints("grow", grow, w * h); s.ints("game", game, w * h)
        hall = s.int("hall", hall)
        buildingCount = s.int("buildings", buildingCount)
        val nb = buildingCount
        // сколько было ресурсов и потребностей, когда сохраняли (в старых сохранениях поля нет - по длине массивов);
        // новые ресурсы и потребности дописываются в конец content.json - старые сохранения раскладываются по новой ширине
        val oldR = s.int("R", R).let { if (s.reading && s.size("traderPrice") > 0 && it == R) s.size("traderPrice") else it }
        val oldN = s.int("N", N).let { if (s.reading && nb > 0 && s.size("bNeedAcc") >= 0 && it == N) s.size("bNeedAcc") / nb else it }
        for ((name, arr) in listOf(
            "bDef" to bDef, "bX" to bX, "bY" to bY, "bState" to bState, "bBuild" to bBuild, "bWork" to bWork,
            "bWanted" to bWanted, "bWorkers" to bWorkers, "bResidents" to bResidents, "bStatus" to bStatus,
            "bHappy" to bHappy, "bNeedOk" to bNeedOk, "bLimit" to bLimit, "bMoveFrom" to bMoveFrom,
            "bUpg" to bUpg, "bUpgDays" to bUpgDays, "bFire" to bFire, "bSick" to bSick, "bFireSafe" to bFireSafe, "bHungry" to bHungry,
            "bUnpaid" to bUnpaid, "bLevel" to bLevel, "bDir" to bDir, "bPrio" to bPrio, "bTool" to bTool,
        )) s.ints(name, arr, nb)
        // старое сохранение без износа: у зданий с инструментами инструмент новый
        if (s.reading && s.size("bTool") < 0) for (b in 0 until nb) if (bState[b] == B_ACTIVE && def(b).tools) bTool[b] = Tune.TOOL_LIFE
        s.table("upInv", upInv, nb, R, oldR)
        s.table("bAccept", bAccept, nb, R, oldR, 1)
        lastPlaced = s.int("lastPlaced", lastPlaced)
        s.table("bNeedAcc", bNeedAcc, nb, N, oldN)
        gold = s.int("gold", gold)
        level = s.int("level", level)
        townHappy = s.int("townHappy", townHappy)
        traderLeft = s.int("traderLeft", traderLeft)
        threatsOn = s.int("threatsOn", if (threatsOn) 1 else 0) != 0
        for ((name, arr) in listOf("rType" to rType, "rTarget" to rTarget, "rPos" to rPos, "rX" to rX, "rY" to rY, "rCount" to rCount))
            s.table(name, arr, 1, Tune.MAX_RAIDS, Tune.MAX_RAIDS, if (name == "rTarget") -1 else 0)
        if (!s.reading || s.size("rPath") >= 0) s.paths("rPath", rPath, Tune.MAX_RAIDS)
        if (!s.reading) for (i in 0 until Tune.MAX_RAIDS) rStale[i] = if (rVersion[i] != raidVer()) 1 else 0
        s.table("rStale", rStale, 1, Tune.MAX_RAIDS, Tune.MAX_RAIDS)
        raidsRepelled = s.int("raidsRepelled", raidsRepelled)
        raidsStopped = s.int("raidsStopped", raidsStopped)
        raidsTotal = s.int("raidsTotal", raidsTotal)
        raidLastDay = s.int("raidLastDay", raidLastDay)
        festivalLeft = s.int("festivalLeft", festivalLeft)
        festivalWait = s.int("festivalWait", festivalWait)
        taxLevel = s.int("taxLevel", taxLevel)
        dailyDate = s.int("dailyDate", dailyDate); dailyFinalScore = s.int("dailyFinal", dailyFinalScore)
        prestige = s.int("prestige", prestige)
        crR = s.int("crR", crR); crN = s.int("crN", crN); crEnd = s.int("crEnd", crEnd); crGold = s.int("crGold", crGold)
        crPrestige = s.int("crPrestige", crPrestige); crDone = s.int("crDone", crDone); crFailed = s.int("crFailed", crFailed)
        crNext = s.int("crNext", crNext)
        traderShip = s.int("traderShip", if (traderShip) 1 else 0) != 0
        shipNext = s.int("shipNext", shipNext)
        s.table("traderPrice", traderPrice, 1, R, oldR, 100)
        s.table("traderStock", traderStock, 1, R, oldR)
        s.table("shipWant", shipWant, 1, R, oldR)
        s.table("sellAbove", sellAbove, 1, R, oldR, -1)
        s.table("townLimit", townLimit, 1, R, oldR, -1)
        s.table("madeTotal", madeTotal, 1, R, oldR)
        dayCount = s.int("dayCount", dayCount)
        for ((name, arr) in listOf("dayPop" to dayPop, "dayHappy" to dayHappy, "dayGold" to dayGold))
            s.table(name, arr, 1, Tune.HISTORY, Tune.HISTORY)
        s.table("dayStock", dayStock, Tune.HISTORY_RES, R, oldR)
        fcState = s.int("fcState", fcState)
        weather = s.int("weather", weather); weatherNext = s.int("weatherNext", weatherNext)
        floodLeft = s.int("floodLeft", floodLeft); floodYear = s.int("floodYear", floodYear)
        tipsSeen = s.int("tipsSeen", tipsSeen)
        s.table("kidAge", kidAge, nb, Tune.KIDS_MAX, Tune.KIDS_MAX)
        s.table("kidSchool", kidSchool, nb, Tune.KIDS_MAX, Tune.KIDS_MAX)
        kidsGrown = s.int("kidsGrown", kidsGrown)
        val oldNb = s.reading && s.size("nbSide") < 0
        nbCount = s.int("nbCount", nbCount)
        for ((name, arr) in listOf(
            "nbSide" to nbSide, "nbName" to nbName, "nbSurplus" to nbSurplus, "nbNeed" to nbNeed, "nbRel" to nbRel,
            "ctGiveR" to ctGiveR, "ctGiveN" to ctGiveN, "ctGetR" to ctGetR, "ctGetN" to ctGetN, "ctDue" to ctDue, "ctFails" to ctFails, "ctLate" to ctLate,
            "wgState" to wgState, "wgPost" to wgPost, "wgPos" to wgPos, "wgX" to wgX, "wgY" to wgY, "wgTimer" to wgTimer,
            "wgGetR" to wgGetR, "wgGetN" to wgGetN,
        )) s.table(name, arr, 1, Tune.NB_MAX, Tune.NB_MAX, if (name == "ctGiveR" || name == "ctGetR" || name == "wgPost" || name == "wgGetR") -1 else 0)
        for ((name, arr) in listOf("ofGiveR" to ofGiveR, "ofGiveN" to ofGiveN, "ofGetR" to ofGetR, "ofGetN" to ofGetN))
            s.table(name, arr, 1, Tune.NB_MAX * 2, Tune.NB_MAX * 2, if (name == "ofGiveR" || name == "ofGetR") -1 else 0)
        if (!s.reading || s.size("wgPath") >= 0) s.paths("wgPath", wgPath, Tune.NB_MAX)
        deliveries = s.int("deliveries", deliveries); offersDay = s.int("offersDay", offersDay)
        setRich = s.int("setRich", setRich); setStock = s.int("setStock", setStock); setDisasters = s.int("setDisasters", setDisasters)
        calm = s.int("calm", if (calm) 1 else 0) != 0
        badHarvest = s.int("badHarvest", if (badHarvest) 1 else 0) != 0
        harshWinter = s.int("harshWinter", if (harshWinter) 1 else 0) != 0
        s.table("statProd", statProd, 1, R, oldR); s.table("statUse", statUse, 1, R, oldR)
        s.table("histProd", histProd, Tune.STAT_DAYS, R, oldR); s.table("histUse", histUse, Tune.STAT_DAYS, R, oldR)
        histDays = s.int("histDays", histDays); histPos = s.int("histPos", histPos); lastTax = s.int("lastTax", lastTax)
        lastUpkeep = s.int("lastUpkeep", lastUpkeep)
        for ((name, arr) in listOf("evType" to evType, "evA" to evA, "evB" to evB, "evTick" to evTick)) s.ints(name, arr, Tune.EVENTS)
        evCount = s.int("evCount", evCount)
        scenario = s.int("scenario", scenario)
        chainPos = s.int("chainPos", chainPos)
        outcome = s.int("outcome", outcome)
        failTimer = s.int("failTimer", failTimer)
        questsDone = s.int("questsDone", questsDone)
        for ((name, arr) in listOf(
            "qType" to qType, "qA" to qA, "qB" to qB, "qDays" to qDays, "qProg" to qProg,
            "qGold" to qGold, "qResR" to qResR, "qResN" to qResN, "qSrc" to qSrc,
        )) s.ints(name, arr, Tune.MAX_QUESTS)
        s.table("inv", inv, nb, R, oldR)
        s.table("incoming", incoming, nb, R, oldR)
        s.table("reserved", reserved, nb, R, oldR)
        agentCount = s.int("agents", agentCount)
        val na = agentCount
        for ((name, arr) in listOf(
            "px" to px, "py" to py, "state" to state, "where" to where, "home" to home, "job" to job, "jobB" to jobB,
            "goal" to goal, "goalB" to goalB, "timer" to timer, "idle" to idle, "carryR" to carryR, "carryN" to carryN,
            "taskSrc" to taskSrc, "taskDst" to taskDst, "taskR" to taskR, "taskN" to taskN, "pathPos" to pathPos,
            "gCell" to gCell, "gStand" to gStand, "aCart" to aCart, "aSkill" to aSkill,
        )) s.ints(name, arr, na)
        val hadSerial = s.reading && s.size("aSerial") >= 0
        for ((name, arr) in listOf(
            "aSerial" to aSerial, "aArrived" to aArrived, "aJobChanges" to aJobChanges, "aCarried" to aCarried,
            "aFavR" to aFavR, "aFavN" to aFavN,
        )) s.ints(name, arr, na)
        nextSerial = s.int("nextSerial", nextSerial)
        if (s.reading && !hadSerial) {
            for (i in 0 until na) aSerial[i] = i
            nextSerial = na
        }
        s.paths("paths", paths, na)
        val q = s.ints("queue", IntArray(qSize) { queue[(qHead + it) % Tune.MAX_AGENTS] }, -1)
        if (s.reading) {
            for (i in q.indices) queue[i] = q[i]
            qHead = 0; qSize = q.size
        }
        s.commands("log", log)
        s.commands("pending", pending)
        if (s.reading) {
            rebuildDerived()
            for (i in 0 until Tune.MAX_RAIDS) rVersion[i] = raidVer() + rStale[i]
        }
        // сохранение до 0.12: соседи появляются при загрузке (в песочнице)
        if (oldNb && scenario < 0 && hall >= 0) setupNeighbors()
    }

    /** После загрузки: то, что выводится из сохранённого (стоимость клеток, карта зданий, входы, ресурсы рядом) */
    private fun rebuildDerived() {
        for (i in 0 until w * h) updateCost(i)
        java.util.Arrays.fill(buildingAt, -1)
        java.util.Arrays.fill(entranceOf, -1)
        for (b in 0 until buildingCount) {
            if (bState[b] == B_GONE) continue
            val s = def(b).size
            for (yy in bY[b] until bY[b] + s) for (xx in bX[b] until bX[b] + s) buildingAt[yy * w + xx] = b
            entranceOf[entrance(b)] = b
        }
        computeRegions()
        computeBorder()
        computeSoil()
        computeLow()
        for (b in 0 until buildingCount) if (bState[b] != B_GONE) { bNear[b] = countNear(b); bYield[b] = yieldFor(bDef[b], bX[b], bY[b]); bLow[b] = lowUnder(b) }
        System.arraycopy(px, 0, prevX, 0, agentCount)
        System.arraycopy(py, 0, prevY, 0, agentCount)
        refreshDerived()
    }
}
