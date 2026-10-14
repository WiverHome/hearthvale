package town

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound

/**
 * Звуки и музыка из assets/sounds (синтезированы генератором ресурсов). Включение звука и музыки хранится в Storage.
 * Музыка - три темы (весна-лето, осень, зима): трек играет до конца, следующий выбирается по текущему сезону.
 * Браузер не даёт играть звук до первого касания - ошибки воспроизведения глушим, игра от звука не зависит.
 */
object Sfx {
    private val names = listOf("click", "place", "built", "coin", "quest", "level", "trader", "demolish", "warn")
    private val sounds = HashMap<String, Sound>()
    private val themes = ArrayList<Music>()
    private var current = -1
    /** Какую тему играть следующей: 0 - весна и лето, 1 - осень, 2 - зима */
    private var wanted = 0

    private const val MUSIC_VOLUME = 0.35f
    private const val THEMES = 3

    fun load() {
        for (n in names) try {
            sounds[n] = Gdx.audio.newSound(Gdx.files.internal("sounds/$n.wav"))
        } catch (e: Exception) {
            Gdx.app.error("sfx", "cannot load $n", e)
        }
        for (i in 0 until THEMES) try {
            val m = Gdx.audio.newMusic(Gdx.files.internal("sounds/music_$i.mp3"))
            m.volume = MUSIC_VOLUME
            m.isLooping = false
            m.setOnCompletionListener { nextTheme() }
            themes.add(m)
        } catch (e: Exception) {
            Gdx.app.error("sfx", "cannot load music $i", e)
        }
    }

    fun play(name: String, volume: Float = 0.7f) {
        val v = Storage.soundVol
        if (v <= 0) return
        try { sounds[name]?.play(volume * v / 100f) } catch (e: Exception) { }
    }

    /** Сезон игры (0..3) - какую тему играть следующей; текущий трек доигрывает */
    fun setSeason(season: Int) {
        wanted = when (season) { 2 -> 1; 3 -> 2; else -> 0 }
    }

    private fun nextTheme() {
        if (Storage.musicVol <= 0 || themes.isEmpty()) return
        current = wanted.coerceIn(0, themes.size - 1)
        try { themes[current].volume = MUSIC_VOLUME * Storage.musicVol / 100f; themes[current].play() } catch (e: Exception) { }
    }

    /** Музыка играет, если громкость не нулевая; вызывается при входе в игру и при смене громкости */
    fun updateMusic() {
        if (themes.isEmpty()) return
        val on = Storage.musicVol > 0
        val playing = current >= 0 && themes[current].isPlaying
        try {
            if (current >= 0) themes[current].volume = MUSIC_VOLUME * Storage.musicVol / 100f
            if (on && !playing) nextTheme()
            else if (!on && playing) themes[current].pause()
        } catch (e: Exception) { }
    }

    fun dispose() {
        for (s in sounds.values) s.dispose()
        for (m in themes) m.dispose()
    }
}
