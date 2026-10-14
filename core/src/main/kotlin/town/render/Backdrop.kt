package town.render

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import town.Spr
import town.Sprites
import town.data.Content

/**
 * Заставка титульного экрана: маленькая деревня в изометрии из спрайтов атласа (луг, ратуша, дома, рынок,
 * мельница, деревья), слегка затемнённая, чтобы меню читалось. Мир не нужен - раскладка задана здесь.
 */
class Backdrop(private val sprites: Sprites, content: Content) {
    private val camera = OrthographicCamera()
    private class Item(val s: Spr, val x: Float, val y: Float, val depth: Float, val flip: Boolean)
    private val ground = ArrayList<Item>()
    private val objects = ArrayList<Item>()
    private val tw = Sprites.TW / 2f
    private val th = Sprites.TH / 2f
    private val r = 9

    companion object {
        /** Размер и центр сцены в единицах мира (с высокими зданиями) */
        const val SCENE_W = 560f
        const val SCENE_H = 300f
        const val SCENE_CY = 30f
    }

    init {
        fun sx(x: Float, y: Float) = (x - y) * tw
        fun sy(x: Float, y: Float) = (x + y) * th
        for (y in -r..r) for (x in -r..r) {
            if (Math.abs(x) + Math.abs(y) > r + 3) continue
            val type = if ((x - y) in 6..7 && x + y < 6) "water" else "grass"
            val list = if (type == "water") sprites.ground[town.sim.WATER]!! else sprites.ground[town.sim.GRASS]!!
            val h = (x * 7349 + y * 9311) and 0x7fff
            ground += Item(list[h % list.size], sx(x.toFloat(), y.toFloat()), sy(x.toFloat(), y.toFloat()), 0f, false)
        }
        fun building(id: String, x: Int, y: Int) {
            val def = content.buildings.firstOrNull { it.id == id } ?: return
            val s = sprites.buildings[def.index]
            objects += Item(s, sx(x.toFloat(), y.toFloat()), sy(x.toFloat(), y.toFloat()), (x + y + def.size).toFloat(), false)
        }
        building("hall", -1, -1)
        building("house", 3, -2)
        building("house", -3, 2)
        building("market", 2, 2)
        building("mill", -5, -3)
        building("church", 4, 4)
        building("well", 0, 3)
        // деревья по краю
        var k = 0
        for (y in -r..r) for (x in -r..r) {
            val d = Math.abs(x) + Math.abs(y)
            if (d < 7 || d > r + 2 || ((x * 31 + y * 17) and 3) == 0) continue
            if ((x - y) in 5..8 && x + y < 6) continue
            val t = sprites.trees[(k++ * 7) % sprites.trees.size]
            objects += Item(t, sx(x + 0.5f, y + 0.5f), sy(x + 0.5f, y + 0.5f), x + y + 1f, (x + y) % 3 == 0)
        }
        objects.sortByDescending { it.depth }
    }

    fun draw(batch: SpriteBatch, width: Float, height: Float) {
        camera.setToOrtho(false, width, height)
        // деревня занимает середину экрана между названием и меню; по бокам может обрезаться
        camera.zoom = maxOf(SCENE_H / (height * 0.5f), SCENE_W / (width * 1.35f))
        camera.position.set(0f, SCENE_CY - height * camera.zoom * 0.05f, 0f)
        camera.update()
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.setColor(0.8f, 0.8f, 0.76f, 1f)
        for (i in ground) sprites.draw(batch, i.s, i.x, i.y)
        for (i in objects) sprites.draw(batch, i.s, i.x, i.y, i.flip)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.end()
    }
}
