package town.sim

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import town.data.Content

/**
 * Описание полей состояния для сохранения: World.io вызывает эти методы по порядку,
 * при записи они пишут значения, при чтении - возвращают прочитанные (массивы заполняются на месте).
 */
internal interface StateIO {
    val reading: Boolean
    fun int(name: String, v: Int): Int
    fun long(name: String, v: Long): Long
    /** n >= 0 - первые n элементов arr; n = -1 - массив целиком, при чтении возвращается новый нужной длины */
    fun ints(name: String, arr: IntArray, n: Int): IntArray
    /**
     * Таблица rows x width (строка - здание или сутки, столбец - ресурс или потребность). При чтении сохранения,
     * где столбцов было oldWidth (до новых ресурсов), строки раскладываются по новой ширине, новые столбцы - fill
     */
    fun table(name: String, arr: IntArray, rows: Int, width: Int, oldWidth: Int, fill: Int = 0)
    /** Длина массива в сохранении (-1 - нет такого поля или идёт запись) */
    fun size(name: String): Int
    fun paths(name: String, arr: Array<IntArray?>, n: Int)
    fun commands(name: String, list: ArrayList<Command>)
}

/**
 * Сохранение = полный снимок мира в JSON (одинаковый на вебе и Android).
 * После загрузки симуляция идёт тик в тик как без сохранения (проверяет WorldTest).
 * Версия в поле "v"; сохранения другой версии не загружаются (до выпуска 0.1 формат меняется свободно).
 */
object SaveCodec {
    const val VERSION = 7

    fun write(wd: World): String {
        val sb = StringBuilder(128 * 1024)
        sb.append("{\"v\":").append(VERSION)
        sb.append(",\"seed\":").append(wd.seed).append(",\"w\":").append(wd.w).append(",\"h\":").append(wd.h)
        wd.io(Writer(sb))
        sb.append('}')
        return sb.toString()
    }

    fun read(content: Content, json: String): World {
        val r = JsonReader().parse(json)
        val v = r.getInt("v")
        require(v == VERSION) { "save version $v, expected $VERSION" }
        val wd = World(content, r.getInt("seed"), r.getInt("w"), r.getInt("h"))
        wd.io(Reader(r))
        return wd
    }

    /** Совместима ли запись с текущей версией (для списка слотов) */
    fun compatible(json: String): Boolean = try {
        JsonReader().parse(json).getInt("v") == VERSION
    } catch (e: Exception) {
        false
    }

    /** Короткие сведения о сохранении для списка слотов: размер карты, тик, жителей */
    fun summary(json: String): IntArray {
        val r = JsonReader().parse(json)
        return intArrayOf(r.getInt("w"), r.getInt("tick"), r.getInt("agents"))
    }

    private class Writer(private val sb: StringBuilder) : StateIO {
        override val reading = false

        private fun key(name: String) {
            sb.append(",\"").append(name).append("\":")
        }

        override fun int(name: String, v: Int): Int { key(name); sb.append(v); return v }

        override fun long(name: String, v: Long): Long { key(name); sb.append(v); return v }

        override fun ints(name: String, arr: IntArray, n: Int): IntArray {
            key(name)
            list(arr, if (n < 0) arr.size else n)
            return arr
        }

        override fun table(name: String, arr: IntArray, rows: Int, width: Int, oldWidth: Int, fill: Int) {
            key(name)
            list(arr, rows * width)
        }

        override fun size(name: String) = -1

        private fun list(arr: IntArray, n: Int) {
            sb.append('[')
            for (i in 0 until n) {
                if (i > 0) sb.append(',')
                sb.append(arr[i])
            }
            sb.append(']')
        }

        override fun paths(name: String, arr: Array<IntArray?>, n: Int) {
            key(name)
            sb.append('[')
            for (i in 0 until n) {
                if (i > 0) sb.append(',')
                val p = arr[i] ?: IntArray(0)
                list(p, p.size)
            }
            sb.append(']')
        }

        override fun commands(name: String, list: ArrayList<Command>) {
            key(name)
            sb.append('[')
            for (i in list.indices) {
                val c = list[i]
                if (i > 0) sb.append(',')
                // шестое число (d) - только если не 0: старые журналы читаются как прежде
                if (c.d != 0) list(intArrayOf(c.tick, c.type, c.a, c.b, c.c, c.d), 6) else list(intArrayOf(c.tick, c.type, c.a, c.b, c.c), 5)
            }
            sb.append(']')
        }
    }

    private class Reader(private val r: JsonValue) : StateIO {
        override val reading = true

        // поля нет (сохранение из версии до него) - значение по умолчанию
        override fun int(name: String, v: Int) = if (r.has(name)) r.getLong(name).toInt() else v

        override fun long(name: String, v: Long) = r.getLong(name)

        override fun ints(name: String, arr: IntArray, n: Int): IntArray {
            // поля нет (сохранение из версии до него) - нули
            val v = r.get(name) ?: run { if (n >= 0) java.util.Arrays.fill(arr, 0, n, 0); return if (n < 0) IntArray(0) else arr }
            val src = v.asIntArray()
            if (n < 0) return src
            require(src.size == n) { "save field $name: ${src.size} != $n" }
            System.arraycopy(src, 0, arr, 0, n)
            return arr
        }

        override fun table(name: String, arr: IntArray, rows: Int, width: Int, oldWidth: Int, fill: Int) {
            val v = r.get(name)
            if (v == null) { java.util.Arrays.fill(arr, 0, rows * width, fill); return }
            val src = v.asIntArray()
            if (oldWidth == width) {
                require(src.size == rows * width) { "save field $name: ${src.size} != ${rows * width}" }
                System.arraycopy(src, 0, arr, 0, rows * width)
                return
            }
            require(src.size == rows * oldWidth) { "save field $name: ${src.size} != ${rows * oldWidth}" }
            for (row in 0 until rows) for (c in 0 until width)
                arr[row * width + c] = if (c < oldWidth) src[row * oldWidth + c] else fill
        }

        override fun size(name: String): Int = r.get(name)?.size ?: -1

        override fun paths(name: String, arr: Array<IntArray?>, n: Int) {
            var p = r.get(name).child
            var i = 0
            while (p != null && i < n) {
                val path = p.asIntArray()
                arr[i++] = if (path.isEmpty()) null else path
                p = p.next
            }
        }

        override fun commands(name: String, list: ArrayList<Command>) {
            var c = r.get(name)?.child
            while (c != null) {
                val x = c.asIntArray()
                list.add(Command(x[0], x[1], x[2], x[3], x[4], if (x.size > 5) x[5] else 0))
                c = c.next
            }
        }
    }
}
