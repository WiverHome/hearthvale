package town.sim

/**
 * Код карты песочницы: сид, размер, настройки и угрозы в 10 знаках («7KQ2M-X9D4A») - по нему друг получит ту же
 * долину с теми же условиями. 42 бита данных + 8 бит проверки, алфавит Крокфорда (без I, L, O, U - не спутать
 * с 1 и 0). Любой другой текст («мойгород») - тоже сид: хеш строки, условия - выбранные на экране.
 */
object MapCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    class Params(val seed: Int, val size: MapSize, val settings: SandboxSettings, val threats: Boolean)

    fun encode(p: Params): String {
        val s = p.settings
        var v = (p.seed.toLong() and 0xffffffffL)
        v = v or (p.size.ordinal.toLong() shl 32)
        v = v or (s.rich.toLong() shl 34) or (s.stock.toLong() shl 36) or (s.disasters.toLong() shl 38)
        v = v or ((if (s.calm) 1L else 0L) shl 40) or ((if (p.threats) 1L else 0L) shl 41)
        v = v or (check(v) shl 42)
        val sb = StringBuilder()
        for (i in 9 downTo 0) {
            sb.append(ALPHABET[((v ushr (i * 5)) and 31L).toInt()])
            if (i == 5) sb.append('-')
        }
        return sb.toString()
    }

    /** Код карты или null (не код: другой текст, опечатка - не сходится проверка) */
    fun decode(text: String): Params? {
        val t = text.uppercase().replace("-", "").replace(" ", "").replace('O', '0').replace('I', '1').replace('L', '1')
        if (t.length != 10) return null
        var v = 0L
        for (c in t) {
            val k = ALPHABET.indexOf(c)
            if (k < 0) return null
            v = (v shl 5) or k.toLong()
        }
        val data = v and ((1L shl 42) - 1)
        if ((v ushr 42) != check(data)) return null
        val size = ((data ushr 32) and 3L).toInt()
        val rich = ((data ushr 34) and 3L).toInt(); val stock = ((data ushr 36) and 3L).toInt(); val dis = ((data ushr 38) and 3L).toInt()
        if (size > 2 || rich > 2 || stock > 2 || dis > 2) return null
        return Params((data and 0xffffffffL).toInt(), MapSize.values()[size],
            SandboxSettings(rich, stock, dis, ((data ushr 40) and 1L) != 0L), ((data ushr 41) and 1L) != 0L)
    }

    /** Сид из любого текста (слово, число): одинаковый на всех платформах */
    fun seedOf(text: String): Int {
        val t = text.trim()
        t.toIntOrNull()?.let { return it }
        var h = 0x811c9dc5.toInt()
        for (c in t.lowercase()) { h = h xor c.code; h *= 16777619 }
        return h
    }

    /** 8 бит проверки от данных */
    private fun check(v: Long): Long {
        var h = (v xor (v ushr 21)) * -7046029254386353131L
        h = h xor (h ushr 29)
        return (h ushr 13) and 0xffL
    }
}

/** Код карты этой партии (песочница; у сценария и партии дня - null: у них карта и так общая) */
fun World.mapCode(): String? {
    if (scenario >= 0 || dailyDate > 0) return null
    val size = MapSize.values().firstOrNull { it.tiles == w } ?: return null
    return MapCode.encode(MapCode.Params(seed, size, SandboxSettings(setRich, setStock, setDisasters, calm), threatsOn))
}
