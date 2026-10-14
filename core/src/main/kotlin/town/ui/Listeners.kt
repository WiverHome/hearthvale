package town.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

/** Короткая запись обработчика нажатия */
fun Actor.onClick(action: () -> Unit) {
    addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            town.Sfx.play("click", 0.5f)
            action()
        }
    })
}
