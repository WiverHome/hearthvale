package town.render

import com.badlogic.gdx.graphics.Color
import town.Storage

/**
 * Цвета «хорошо / плохо / внимание»: красно-зелёные по умолчанию, сине-оранжевые в режиме для дальтоников (Storage.colorblind).
 * Возвращаемые цвета общие - не менять; ramp пользуется одним временным цветом, читать сразу.
 */
object Palette {
    private val cb get() = Storage.colorblind

    private val goodN = Color(0.55f, 0.9f, 0.5f, 1f)
    private val goodC = Color(0.45f, 0.72f, 1f, 1f)
    private val badN = Color(1f, 0.55f, 0.45f, 1f)
    private val badC = Color(1f, 0.68f, 0.25f, 1f)
    private val warnN = Color(1f, 0.6f, 0.45f, 1f)
    private val warnC = Color(1f, 0.7f, 0.3f, 1f)

    // трёхступенчатая шкала плохо - средне - хорошо
    private val rampN = arrayOf(floatArrayOf(1f, 0.25f, 0.2f), floatArrayOf(1f, 0.85f, 0.2f), floatArrayOf(0.3f, 0.9f, 0.3f))
    private val rampC = arrayOf(floatArrayOf(1f, 0.5f, 0.05f), floatArrayOf(0.96f, 0.93f, 0.78f), floatArrayOf(0.15f, 0.45f, 1f))
    private val tmp = Color(1f, 1f, 1f, 1f)

    fun good(): Color = if (cb) goodC else goodN
    fun bad(): Color = if (cb) badC else badN
    fun warn(): Color = if (cb) warnC else warnN

    /** t = 0 плохо ... 1 хорошо; цвет временный, альфа 1 */
    fun ramp(t: Float): Color {
        val s = if (cb) rampC else rampN
        val q = t.coerceIn(0f, 1f) * 2f
        val i = minOf(1, q.toInt())
        val f = q - i
        val a = s[i]; val b = s[i + 1]
        return tmp.set(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, 1f)
    }

    // оттенок спрайта дома в слое довольства (умножается на цвета спрайта - светлее шкалы)
    private val tintN = arrayOf(Color(1f, 0.45f, 0.4f, 1f), Color(1f, 0.9f, 0.45f, 1f), Color(0.6f, 1f, 0.55f, 1f))
    private val tintC = arrayOf(Color(1f, 0.68f, 0.3f, 1f), Color(1f, 0.97f, 0.88f, 1f), Color(0.5f, 0.72f, 1f, 1f))

    /** Оттенок дома в слое довольства: ниже 40, ниже 70, выше */
    fun houseTint(h: Int): Color = (if (cb) tintC else tintN)[if (h < 40) 0 else if (h < 70) 1 else 2]

    /** Довольство дома: ниже 40 плохо, ниже 70 средне, иначе хорошо */
    fun happy(h: Int): Color = ramp(if (h < 40) 0f else if (h < 70) 0.5f else 1f)

    /** Площадка призрака: ставить можно / нельзя (клетки) */
    fun ghost(ok: Boolean): Color = if (cb) (if (ok) tmp.set(0.2f, 0.5f, 1f, 1f) else tmp.set(1f, 0.55f, 0.05f, 1f))
        else (if (ok) tmp.set(0.35f, 1f, 0.3f, 1f) else tmp.set(1f, 0.3f, 0.3f, 1f))

    /** Подкраска спрайта призрака */
    fun ghostTint(ok: Boolean): Color = if (cb) (if (ok) tmp.set(0.6f, 0.78f, 1f, 1f) else tmp.set(1f, 0.7f, 0.3f, 1f))
        else (if (ok) tmp.set(0.6f, 1f, 0.6f, 1f) else tmp.set(1f, 0.45f, 0.45f, 1f))
}
