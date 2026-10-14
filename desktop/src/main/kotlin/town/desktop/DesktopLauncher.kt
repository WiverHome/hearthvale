package town.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import java.util.Locale
import town.Hearthvale

/** ПК-версия: окно 1280x720, можно менять размер */
fun main() {
    val config = Lwjgl3ApplicationConfiguration()
    config.setTitle("Hearthvale")
    config.setWindowedMode(1280, 720)
    config.useVsync(true)
    config.setWindowIcon("web/icon-192.png")
    val game = Hearthvale(Locale.getDefault().language)
    game.saveFiles = FileDialogs
    Lwjgl3Application(game, config)
}

/** Сохранение в файл и из файла через системный диалог (AWT FileDialog в отдельном потоке) */
private object FileDialogs : Hearthvale.SaveFiles {
    override fun export(name: String, text: String) {
        Thread {
            val d = java.awt.FileDialog(null as java.awt.Frame?, "Hearthvale", java.awt.FileDialog.SAVE)
            d.file = name
            d.isVisible = true
            val f = d.file ?: return@Thread
            try { java.io.File(d.directory, f).writeText(text, Charsets.UTF_8) } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    override fun exportImage(name: String, w: Int, h: Int, rgba: ByteArray) {
        Thread {
            val d = java.awt.FileDialog(null as java.awt.Frame?, "Hearthvale", java.awt.FileDialog.SAVE)
            d.file = name
            d.isVisible = true
            val f = d.file ?: return@Thread
            try {
                val pm = com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888)
                pm.pixels.put(rgba).rewind()
                val out = java.io.ByteArrayOutputStream()
                val png = com.badlogic.gdx.graphics.PixmapIO.PNG(); png.setFlipY(false)
                png.write(out, pm); png.dispose(); pm.dispose()
                java.io.File(d.directory, f).writeBytes(out.toByteArray())
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    override fun import(done: (String?) -> Unit) {
        Thread {
            val d = java.awt.FileDialog(null as java.awt.Frame?, "Hearthvale", java.awt.FileDialog.LOAD)
            d.isVisible = true
            val f = d.file
            val text = if (f == null) null else try { java.io.File(d.directory, f).readText(Charsets.UTF_8) } catch (e: Exception) { null }
            com.badlogic.gdx.Gdx.app.postRunnable { done(text) }
        }.start()
    }
}
