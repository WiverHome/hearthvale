package town

/**
 * Код партии для переноса через буфер обмена: текст сохранения -> LZW (коды до 16 бит, ширина растёт,
 * словарь сбрасывается при заполнении) -> base64 (свой URL-безопасный алфавит). Без java.util.zip (TeaVM).
 * Формат: "HV1:" + base64(4 байта длины исходника, 4 байта суммы, поток кодов). decode при порче даёт null.
 */
object SaveCode {
    private const val PREFIX = "HV1:"
    private const val CLEAR = 256
    private const val FIRST = 257
    private const val CAP = 65535
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private class Bits {
        var buf = ByteArray(1024)
        var len = 0
        private var acc = 0L
        private var n = 0
        fun put(code: Int, width: Int) {
            acc = acc or (code.toLong() shl n)
            n += width
            while (n >= 8) { byte((acc and 0xFF).toInt()); acc = acc ushr 8; n -= 8 }
        }
        fun byte(b: Int) {
            if (len == buf.size) buf = buf.copyOf(len * 2)
            buf[len++] = b.toByte()
        }
        fun finish(): ByteArray { if (n > 0) { byte((acc and 0xFF).toInt()); acc = 0; n = 0 }; return buf.copyOf(len) }
    }

    private fun width(next: Int): Int {
        var w = 9
        while ((1 shl w) < next) w++
        return w
    }

    private fun sum(b: ByteArray): Int {
        var s1 = 1
        var s2 = 0
        for (x in b) { s1 = (s1 + (x.toInt() and 0xFF)) % 65521; s2 = (s2 + s1) % 65521 }
        return (s2 shl 16) or s1
    }

    fun encode(text: String): String {
        val raw = text.encodeToByteArray()
        val out = Bits()
        for (sh in intArrayOf(24, 16, 8, 0)) out.byte((raw.size ushr sh) and 0xFF)
        val cs = sum(raw)
        for (sh in intArrayOf(24, 16, 8, 0)) out.byte((cs ushr sh) and 0xFF)
        val dict = HashMap<Int, Int>()
        var next = FIRST
        var cur = -1
        for (x in raw) {
            val b = x.toInt() and 0xFF
            if (cur < 0) { cur = b; continue }
            val key = (cur shl 8) or b
            val hit = dict[key]
            if (hit != null) { cur = hit; continue }
            out.put(cur, width(next))
            dict[key] = next
            next++
            if (next == CAP) {
                out.put(CLEAR, width(next))
                dict.clear()
                next = FIRST
            }
            cur = b
        }
        if (cur >= 0) out.put(cur, width(next))
        return PREFIX + base64(out.finish())
    }

    fun decode(code: String): String? = try { decodeOrThrow(code) } catch (e: Exception) { null }

    private fun decodeOrThrow(code: String): String? {
        val s = code.filter { !it.isWhitespace() }
        if (!s.startsWith(PREFIX)) return null
        val data = unbase64(s.substring(PREFIX.length)) ?: return null
        if (data.size < 8) return null
        var size = 0
        var cs = 0
        for (i in 0 until 4) { size = (size shl 8) or (data[i].toInt() and 0xFF); cs = (cs shl 8) or (data[4 + i].toInt() and 0xFF) }
        if (size < 0 || size > 64_000_000) return null
        val raw = ByteArray(size)
        var pos = 0
        val prefix = IntArray(65536)
        val suffix = ByteArray(65536)
        val length = IntArray(65536)
        val tmp = ByteArray(65536)
        var k = 0
        var prev = -1
        var bitPos = 8L * 8
        val totalBits = data.size * 8L
        while (true) {
            val next = FIRST + k
            val w = width(next)
            if (bitPos + w > totalBits) break
            var c = 0
            for (i in 0 until w) {
                val p = bitPos + i
                if ((data[(p shr 3).toInt()].toInt() ushr (p and 7).toInt()) and 1 != 0) c = c or (1 shl i)
            }
            bitPos += w
            if (c == CLEAR) { k = 0; prev = -1; continue }
            // длина и первый байт записи c
            val dictNext = FIRST + maxOf(k - 1, 0)
            val first: Int
            val len: Int
            if (c < 256) { len = 1 } else if (c < dictNext) { len = length[c] } else if (c == dictNext && prev >= 0) { len = (if (prev < 256) 1 else length[prev]) + 1 } else return null
            // собрать запись в tmp (с конца)
            if (c < 256) tmp[0] = c.toByte()
            else if (c < dictNext) {
                var e = c
                var i = len - 1
                while (e >= 256) { tmp[i--] = suffix[e]; e = prefix[e] }
                tmp[0] = e.toByte()
            } else {
                // KwKwK: запись prev + её первый байт
                val pl = len - 1
                var e = prev
                var i = pl - 1
                while (e >= 256) { tmp[i--] = suffix[e]; e = prefix[e] }
                tmp[0] = e.toByte()
                tmp[pl] = tmp[0]
            }
            first = tmp[0].toInt() and 0xFF
            if (pos + len > size) return null
            for (i in 0 until len) raw[pos++] = tmp[i]
            if (prev >= 0 && dictNext < 65536) {
                prefix[dictNext] = prev
                suffix[dictNext] = first.toByte()
                length[dictNext] = (if (prev < 256) 1 else length[prev]) + 1
            }
            // записи < 256 имеют длину 1
            prev = c
            k++
        }
        if (pos != size) return null
        if (sum(raw) != cs) return null
        return raw.decodeToString()
    }

    private fun base64(b: ByteArray): String {
        val sb = StringBuilder(b.size * 4 / 3 + 4)
        var i = 0
        while (i < b.size) {
            val n = minOf(3, b.size - i)
            var v = 0
            for (j in 0 until 3) v = (v shl 8) or (if (j < n) b[i + j].toInt() and 0xFF else 0)
            for (j in 0..n) sb.append(ALPHABET[(v ushr (18 - 6 * j)) and 63])
            i += 3
        }
        return sb.toString()
    }

    private fun unbase64(s: String): ByteArray? {
        val out = ByteArray(s.length * 3 / 4)
        var o = 0
        var i = 0
        while (i < s.length) {
            val n = minOf(4, s.length - i)
            if (n < 2) return null
            var v = 0
            for (j in 0 until 4) {
                val d = if (j < n) ALPHABET.indexOf(s[i + j]) else 0
                if (d < 0) return null
                v = (v shl 6) or d
            }
            for (j in 0 until n - 1) out[o++] = (v ushr (16 - 8 * j)).toByte()
            i += 4
        }
        return out.copyOf(o)
    }
}
