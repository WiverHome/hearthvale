package town.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import town.data.Lang

/** Строка настройки громкости: подпись, «-», значение в процентах, «+»; шаг 10, применяется сразу */
fun volumeRow(ui: Ui, labelKey: String, get: () -> Int, set: (Int) -> Unit): Table {
    val t = Table()
    val value = Label("", ui.labelBold)
    value.setAlignment(com.badlogic.gdx.utils.Align.center)
    fun refresh() { value.setText("${get()}%") }
    refresh()
    val minus = TextButton("-", ui.button)
    val plus = TextButton("+", ui.button)
    minus.onClick { set((get() - 10).coerceAtLeast(0)); refresh() }
    plus.onClick { set((get() + 10).coerceAtMost(100)); refresh() }
    val cap = Label(Lang.t(labelKey), ui.label)
    t.add(cap).left().expandX().padLeft(4f)
    t.add(minus).size(44f).padRight(4f)
    t.add(value).width(52f)
    t.add(plus).size(44f).padLeft(4f)
    return t
}
