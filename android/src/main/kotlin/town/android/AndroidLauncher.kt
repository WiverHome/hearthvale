package town.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import java.util.Locale
import town.Hearthvale

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()
        config.useImmersiveMode = true
        val game = Hearthvale(Locale.getDefault().language)
        game.frameCap = { cap -> capFrames(cap) }
        game.saveFiles = object : Hearthvale.SaveFiles {
            override fun export(name: String, text: String) {
                pendingText = text
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, name)
                startActivityForResult(i, REQ_EXPORT)
            }

            override fun exportImage(name: String, w: Int, h: Int, rgba: ByteArray) {
                try {
                    val pm = com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888)
                    pm.pixels.put(rgba).rewind()
                    val out = java.io.ByteArrayOutputStream()
                    val png = com.badlogic.gdx.graphics.PixmapIO.PNG(); png.setFlipY(false)
                    png.write(out, pm); png.dispose(); pm.dispose()
                    pendingBytes = out.toByteArray()
                } catch (e: Exception) { e.printStackTrace(); return }
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/png").putExtra(Intent.EXTRA_TITLE, name)
                startActivityForResult(i, REQ_EXPORT_IMAGE)
            }

            override fun import(done: (String?) -> Unit) {
                pendingDone = done
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                startActivityForResult(i, REQ_IMPORT)
            }
        }
        initialize(game, config)
    }

    private var pendingText: String? = null
    private var pendingBytes: ByteArray? = null
    private var pendingDone: ((String?) -> Unit)? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
        if (requestCode == REQ_EXPORT) {
            val text = pendingText
            pendingText = null
            if (uri != null && text != null) {
                try { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } } catch (e: Exception) { e.printStackTrace() }
            }
        } else if (requestCode == REQ_EXPORT_IMAGE) {
            val bytes = pendingBytes
            pendingBytes = null
            if (uri != null && bytes != null) {
                try { contentResolver.openOutputStream(uri)?.use { it.write(bytes) } } catch (e: Exception) { e.printStackTrace() }
            }
        } else if (requestCode == REQ_IMPORT) {
            val done = pendingDone
            pendingDone = null
            val text = if (uri == null) null else try {
                contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) { null }
            if (done != null) com.badlogic.gdx.Gdx.app.postRunnable { done(text) }
        }
    }

    private companion object {
        const val REQ_EXPORT = 7001
        const val REQ_IMPORT = 7002
        const val REQ_EXPORT_IMAGE = 7003
    }

    /** 30 кадров/с: кадры по запросу раз в 33 мс (setForegroundFPS на Android не работает) */
    private var ticker: Thread? = null

    private fun capFrames(cap: Boolean) {
        graphics.isContinuousRendering = !cap
        if (cap && ticker == null) ticker = Thread {
            while (true) {
                try { Thread.sleep(33) } catch (e: InterruptedException) { break }
                if (!graphics.isContinuousRendering) graphics.requestRendering()
            }
        }.apply { isDaemon = true; start() }
    }
}
