package town.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane

/**
 * Автопроверка раскладки (съёмка интерфейса, desktop Shots): видимые элементы, вылезающие за экран, и надписи,
 * не влезающие в свою ширину без переноса. Содержимое прокрутки не проверяется - оно и должно быть длиннее.
 */
object Overflow {
    fun check(stage: Stage): List<String> {
        val out = ArrayList<String>()
        val w = stage.width; val h = stage.height
        fun walk(a: Actor, path: String) {
            if (!a.isVisible) return
            if (a is ScrollPane) return
            val p = a.localToStageCoordinates(Vector2(0f, 0f))
            val name = path + "/" + (a.name ?: a.javaClass.simpleName)
            if (a.width > 0f && a.height > 0f && (p.x < -1f || p.y < -1f || p.x + a.width > w + 1f || p.y + a.height > h + 1f)) {
                val text = (a as? Label)?.text?.toString()?.take(30)?.let { " \"$it\"" } ?: ""
                out += "%s%s: %.0f,%.0f %.0fx%.0f (экран %.0fx%.0f)".format(name, text, p.x, p.y, a.width, a.height, w, h)
            }
            // надпись без переноса шире своей ячейки - текст наезжает на соседей
            if (a is Label && !a.wrap && a.prefWidth > a.width + 2f && a.width > 0f) out += "$name \"${a.text.toString().take(30)}\": текст ${a.prefWidth.toInt()} > ${a.width.toInt()}"
            if (a is Group) for (c in a.children) walk(c, name)
        }
        for (a in stage.actors) walk(a, "")
        return out
    }
}
