package town

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture

/**
 * Экран загрузки: логотип (assets/web/logo.png, тот же, что у веб-версии) и полоса прогресса.
 * Каждый кадр - один шаг загрузки Hearthvale.loadStep, затем титульный экран.
 */
class LoadingScreen(private val game: Hearthvale) : ScreenAdapter() {
    private val logo = Texture(Gdx.files.internal("web/logo.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val white = Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888).apply { setColor(1f, 1f, 1f, 1f); fill() })
    private val camera = OrthographicCamera()
    private var step = 0
    private var progress = 0f

    override fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, width / game.scale, height / game.scale)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.12f, 0.16f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        val b = game.batch
        b.projectionMatrix = camera.combined
        val w = camera.viewportWidth; val h = camera.viewportHeight
        val lw = minOf(logo.width.toFloat(), w * 0.8f)
        val lh = lw * logo.height / logo.width
        val y = h / 2 - lh / 2 + 20
        b.begin()
        b.setColor(1f, 1f, 1f, 1f)
        b.draw(logo, w / 2 - lw / 2, y, lw, lh)
        val bw = minOf(260f, w * 0.7f)
        b.setColor(0.3f, 0.25f, 0.18f, 1f)
        b.draw(white, w / 2 - bw / 2, y - 30, bw, 8f)
        b.setColor(1f, 0.85f, 0.45f, 1f)
        b.draw(white, w / 2 - bw / 2, y - 30, bw * progress, 8f)
        b.setColor(1f, 1f, 1f, 1f)
        b.end()
        // кадр с полосой уже показан - следующий шаг
        if (step < Hearthvale.LOAD_STEPS) progress = game.loadStep(step++)
        else game.showTitle()
    }

    override fun dispose() {
        logo.dispose()
        white.dispose()
    }
}
