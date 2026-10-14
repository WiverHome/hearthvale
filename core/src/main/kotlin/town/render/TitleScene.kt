package town.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import town.Sprites
import town.data.Content
import town.sim.SaveCodec
import town.sim.Tune
import town.sim.World

/**
 * Живая заставка заглавного экрана: город из сохранения assets/data/title_town.json (снимается
 * `./gradlew :desktop:titleTown`) живёт сам на скорости x1, камера медленно плывёт вокруг ратуши.
 * Сохранения нет или оно не читается - null из [load], заглавный экран берёт картину.
 */
class TitleScene private constructor(private val content: Content, private val sprites: Sprites, private val json: String) {
    private var world = SaveCodec.read(content, json).also { it.makeShowcase() }
    private var renderer = WorldRenderer(world, sprites).also { it.hideIcons = true }
    private var startTick = world.tick
    private var acc = 0f
    private var t = 0f
    /** Доля ширины экрана, на которую город сдвинут вправо (на широком экране слева меню) */
    var shiftX = 0f

    companion object {
        const val FILE = "data/title_town.json"
        /** Город живёт без игрока - через двое суток он снова берётся из сохранения (не разваливается и не уходит в ночь надолго) */
        const val RESET_TICKS = 2 * Tune.TICKS_PER_DAY
        /** Сколько точек мира видно по короткой стороне экрана и радиус облёта (точки мира) */
        const val VIEW = 520f
        const val ORBIT = 90f
        /** Оборот камеры - за столько секунд */
        const val ORBIT_SECONDS = 140f

        fun load(content: Content, sprites: Sprites): TitleScene? {
            val f = Gdx.files.internal(FILE)
            if (!f.exists()) return null
            return try { TitleScene(content, sprites, f.readString("UTF-8")) } catch (e: Exception) { null }
        }
    }

    fun draw(batch: SpriteBatch, w: Float, h: Float, delta: Float) {
        val tickDt = 1f / Tune.TICKS_PER_SECOND
        acc += delta.coerceAtMost(0.25f)
        var steps = 0
        while (acc >= tickDt && steps < 10) { world.step(); acc -= tickDt; steps++ }
        if (steps == 10) acc = 0f
        if (world.tick - startTick > RESET_TICKS) {
            world = SaveCodec.read(content, json).also { it.makeShowcase() }
            renderer = WorldRenderer(world, sprites).also { it.hideIcons = true }
            startTick = world.tick
        }
        t += delta
        val cam = renderer.camera
        cam.setToOrtho(false, w, h)
        cam.zoom = minOf(1.2f, VIEW / minOf(w, h)).coerceAtLeast(0.4f)
        val hall = world.hall
        val cx = if (hall >= 0) world.bX[hall] + 1.5f else world.w / 2f
        val cy = if (hall >= 0) world.bY[hall] + 1.5f else world.h / 2f
        val a = t / ORBIT_SECONDS * 2f * Math.PI.toFloat()
        cam.position.set(renderer.sx(cx, cy) - shiftX * w * cam.zoom + ORBIT * kotlin.math.cos(a), renderer.sy(cx, cy) + ORBIT * 0.5f * kotlin.math.sin(a), 0f)
        batch.begin()
        renderer.draw(batch, (acc / tickDt).coerceIn(0f, 1f))
        batch.end()
    }
}
