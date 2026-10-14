package town.data

import com.badlogic.gdx.utils.JsonReader

/**
 * Строки интерфейса из assets/i18n/strings.json; язык "ru" или "en". Подстановка {0}, {1}...;
 * слово по числу - {0|очко|очка|очков} (русский: 1, 2-4, 5+) или {0|point|points} (английский: 1, иначе)
 */
object Lang {
    var code = "en"
    private val strings = HashMap<String, Text>()

    fun load(json: String) {
        strings.clear()
        var s = JsonReader().parse(json).child
        while (s != null) {
            strings[s.name] = Text.of(s)
            s = s.next
        }
    }

    /** Язык по коду системы: русский для ru/uk/be/kk, иначе английский */
    fun fromSystem(systemLang: String?): String {
        val l = (systemLang ?: "").lowercase()
        return if (l.startsWith("ru") || l.startsWith("uk") || l.startsWith("be") || l.startsWith("kk")) "ru" else "en"
    }

    fun t(key: String, vararg args: Any): String {
        var s = strings[key]?.get(code) ?: key
        for (i in args.indices) {
            val a = args[i]
            if (a is Number) s = plurals(s, i, a.toLong())
            s = s.replace("{$i}", a.toString())
        }
        return s
    }

    /** Все {i|форма|форма...} в s - формой для числа n */
    private fun plurals(s: String, i: Int, n: Long): String {
        val open = "{$i|"
        var out = s
        while (true) {
            val a = out.indexOf(open)
            if (a < 0) return out
            val b = out.indexOf('}', a)
            if (b < 0) return out
            val forms = out.substring(a + open.length, b).split('|')
            out = out.substring(0, a) + forms[pluralIndex(n, forms.size)] + out.substring(b + 1)
        }
    }

    /** Номер формы слова для числа n: русский - 1 / 2-4 / 5-20 (по последним цифрам), английский - 1 / остальные */
    fun pluralIndex(n: Long, forms: Int): Int {
        val m = kotlin.math.abs(n)
        val k = if (forms >= 3) when {
            m % 10 == 1L && m % 100 != 11L -> 0
            m % 10 in 2L..4L && m % 100 !in 12L..14L -> 1
            else -> 2
        } else if (m == 1L) 0 else 1
        return minOf(k, forms - 1)
    }

    /** Название посреди фразы - со строчной («20 доски», "20 planks") */
    fun lower(s: String): String = if (s.isEmpty()) s else s[0].lowercaseChar() + s.substring(1)

    fun t(text: Text): String = text.get(code)
}
