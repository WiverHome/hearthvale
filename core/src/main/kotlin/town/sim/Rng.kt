package town.sim

/** Детерминированный RNG (xorshift32) на целых: одинаковый результат на JVM, в TeaVM и на Android. */
class Rng(seed: Int) {
    /** Внутреннее состояние (для сохранения) */
    var s: Int = if (seed == 0) 0x6D2B79F5 else seed

    fun next(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** Число в [0, n) */
    fun next(n: Int): Int = (next() ushr 1) % n
}
