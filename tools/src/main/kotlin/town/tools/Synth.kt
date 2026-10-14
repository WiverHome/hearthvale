package town.tools

import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/*
 * Звуки и музыка синтезируются кодом (своя работа проекта, CC BY-NC 4.0): колокольчики - сумма негармонических
 * частот с затуханием, удары - шум с фильтром, лютня - щипок Карплуса-Стронга. Звуки - WAV 22050 Гц, моно, 16 бит;
 * музыка (три темы по временам года) кодируется в MP3 чистой Java-версией LAME (jump3r), моно 22050 Гц, 64 кбит/с.
 */
object Synth {
    private const val SR = 22050

    fun all(dir: File) {
        dir.mkdirs()
        write(File(dir, "click.wav"), click())
        write(File(dir, "place.wav"), place())
        write(File(dir, "built.wav"), chime(doubleArrayOf(659.0, 988.0), 0.11))
        write(File(dir, "coin.wav"), coin())
        write(File(dir, "quest.wav"), chime(doubleArrayOf(784.0, 1047.0, 1319.0), 0.09))
        write(File(dir, "level.wav"), fanfare())
        write(File(dir, "trader.wav"), horn())
        write(File(dir, "demolish.wav"), crash())
        write(File(dir, "warn.wav"), warn())
        File(dir, "music.wav").delete()
        for (theme in 0 until 3) writeMp3(File(dir, "music_$theme.mp3"), music(theme))
        println("sounds written to $dir")
    }

    private fun buf(sec: Double) = DoubleArray((sec * SR).toInt())

    private fun bell(out: DoubleArray, start: Double, f: Double, amp: Double, decay: Double, maxSec: Double = 1e6) {
        val partials = doubleArrayOf(1.0, 2.0, 2.76, 5.4)
        val amps = doubleArrayOf(1.0, 0.4, 0.25, 0.1)
        val s0 = (start * SR).toInt()
        val end = if (maxSec > 1e5) out.size else minOf(out.size, s0 + (maxSec * SR).toInt())
        for (i in s0 until end) {
            val t = (i - s0).toDouble() / SR
            var v = 0.0
            for (k in partials.indices) v += amps[k] * sin(2 * PI * f * partials[k] * t) * exp(-t * decay * (1 + k * 0.7))
            out[i] += v * amp * (1 - exp(-t * 400))
        }
    }

    private fun chime(notes: DoubleArray, gap: Double): DoubleArray {
        val out = buf(gap * notes.size + 1.0)
        for ((k, f) in notes.withIndex()) bell(out, k * gap, f, 0.35, 3.0)
        return out
    }

    private fun click(): DoubleArray {
        val out = buf(0.06)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            out[i] = sin(2 * PI * 1400 * t) * exp(-t * 90) * 0.35
        }
        return out
    }

    private fun place(): DoubleArray {
        val out = buf(0.3)
        val r = java.util.Random(5)
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            lp += (r.nextDouble() * 2 - 1 - lp) * 0.08
            out[i] = (sin(2 * PI * (110 - 50 * t) * t) * 0.7 + lp * 1.5) * exp(-t * 18) * 0.6
        }
        return out
    }

    private fun coin(): DoubleArray {
        val out = buf(0.6)
        bell(out, 0.0, 1760.0, 0.25, 9.0)
        bell(out, 0.07, 2349.0, 0.25, 8.0)
        return out
    }

    private fun brass(out: DoubleArray, start: Double, dur: Double, f: Double, amp: Double, vibrato: Double = 0.0) {
        val s0 = (start * SR).toInt()
        val n = (dur * SR).toInt()
        var lp = 0.0
        var phase = 0.0
        for (i in 0 until n) {
            if (s0 + i >= out.size) break
            val t = i.toDouble() / SR
            phase += f * (1 + vibrato * sin(2 * PI * 5.5 * t)) / SR
            val saw = 2 * (phase - Math.floor(phase)) - 1
            lp += (saw - lp) * 0.18
            val env = minOf(1.0, t * 30) * minOf(1.0, (dur - t) * 12)
            out[s0 + i] += lp * env * amp
        }
    }

    private fun fanfare(): DoubleArray {
        val out = buf(1.6)
        val notes = doubleArrayOf(523.0, 659.0, 784.0, 1047.0)
        for ((k, f) in notes.withIndex()) brass(out, k * 0.14, if (k == 3) 0.9 else 0.16, f, 0.28, 0.006)
        return out
    }

    private fun horn(): DoubleArray {
        val out = buf(1.2)
        brass(out, 0.0, 0.35, 392.0, 0.3, 0.01)
        brass(out, 0.38, 0.7, 523.0, 0.3, 0.012)
        return out
    }

    private fun crash(): DoubleArray {
        val out = buf(0.7)
        val r = java.util.Random(9)
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            lp += (r.nextDouble() * 2 - 1 - lp) * (0.25 - t * 0.25)
            out[i] = lp * exp(-t * 6) * 0.8
        }
        return out
    }

    private fun warn(): DoubleArray {
        val out = buf(0.9)
        brass(out, 0.0, 0.3, 220.0, 0.3)
        brass(out, 0.35, 0.45, 185.0, 0.3)
        return out
    }

    // ---------- музыка ----------

    /** Щипок струны (Карплус-Стронг) */
    private fun pluck(out: DoubleArray, start: Double, f: Double, amp: Double, r: java.util.Random, decay: Double = 0.996) {
        val n = (SR / f).toInt().coerceAtLeast(2)
        val ring = DoubleArray(n) { r.nextDouble() * 2 - 1 }
        for (i in 1 until n) ring[i] = (ring[i] + ring[i - 1]) * 0.5   // мягче начало
        val s0 = (start * SR).toInt()
        val len = (2.5 * SR).toInt()
        var idx = 0
        for (i in 0 until len) {
            val o = s0 + i
            if (o >= out.size) break
            val cur = ring[idx]
            val next = ring[(idx + 1) % n]
            ring[idx] = (cur + next) * 0.5 * decay
            out[o] += cur * amp
            idx = (idx + 1) % n
        }
    }

    private fun recorder(out: DoubleArray, start: Double, dur: Double, f: Double, amp: Double) {
        val s0 = (start * SR).toInt()
        for (i in 0 until (dur * SR).toInt()) {
            if (s0 + i >= out.size) break
            val t = i.toDouble() / SR
            val v = sin(2 * PI * f * t + 0.02 * sin(2 * PI * 5 * t)) + 0.15 * sin(4 * PI * f * t)
            val env = minOf(1.0, t * 15) * minOf(1.0, (dur - t) * 8)
            out[s0 + i] += v * env * amp
        }
    }

    /** Свернуть буфер с хвостами в петлю длиной loop секунд: хвосты ложатся на начало, щелчка нет */
    private fun fold(out: DoubleArray, loop: Double): DoubleArray {
        val n = (loop * SR).toInt()
        val res = DoubleArray(n)
        for (i in out.indices) res[i % n] += out[i]
        return res
    }

    /** Ровный фон: синус с частотой, подогнанной под целое число периодов в петле (на стыке нет скачка фазы) */
    private fun drone(out: DoubleArray, loop: Double, f: Double, amp: Double, swellCycles: Int) {
        val ff = Math.round(f * loop) / loop
        val n = (loop * SR).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val swell = 0.65 + 0.35 * sin(2 * PI * swellCycles * t / loop)
            out[i] += sin(2 * PI * ff * t) * amp * swell
        }
    }

    /** Музыка по временам года: 0 - весна/лето (дорийская лютня), 1 - осень, 2 - зима */
    private fun music(theme: Int): DoubleArray = when (theme) {
        0 -> musicSpring()
        1 -> musicAutumn()
        else -> musicWinter()
    }

    /**
     * Спокойная тема в ре-дорийском: 16 тактов (A A' B A), лютня мелодией, бас на сильные доли, во второй половине
     * мелодию дублирует блок-флейта. Хвосты нот после конца переносятся в начало - петля без щелчка.
     */
    private fun musicSpring(): DoubleArray {
        val bpm = 84.0
        val beat = 60 / bpm
        val bars = 16
        val loop = bars * 4 * beat
        val out = buf(loop + 3)
        val r = java.util.Random(1207)
        // ступени ре-дорийского от D4
        val scale = doubleArrayOf(0.0, 2.0, 3.0, 5.0, 7.0, 9.0, 10.0, 12.0, 14.0, 15.0)
        fun freq(step: Int, octave: Int = 0) = 293.66 * 2.0.pow((scale[step.coerceIn(0, scale.size - 1)] + 12 * octave) / 12)
        // фразы: ступени по восьмым (-1 - пауза)
        val a = intArrayOf(4, -1, 3, 2, 3, 4, 5, 4, 2, -1, 1, 2, 0, -1, -1, -1)
        val a2 = intArrayOf(4, -1, 3, 2, 3, 4, 7, 6, 5, 4, 3, 2, 1, -1, -1, -1)
        val b = intArrayOf(7, 6, 5, -1, 6, 5, 4, -1, 5, 4, 3, 2, 3, -1, 4, -1)
        val form = listOf(a, a, a2, a2, b, b, a, a2)
        val bass = intArrayOf(0, 0, 3, 4, 0, 0, 3, 0)   // корень на каждый такт фразы (2 такта на фразу)
        var t = 0.0
        for ((ph, phrase) in form.withIndex()) {
            for ((k, step) in phrase.withIndex()) {
                if (step >= 0) {
                    val st = t + k * beat / 2 + (r.nextDouble() - 0.5) * 0.012
                    pluck(out, st, freq(step), 0.32, r)
                    if (ph >= 4 && k % 2 == 0) recorder(out, st, beat * 0.9, freq(step, 1), 0.06)
                }
            }
            for (barK in 0 until 2) {
                val root = bass[(ph * 2 + barK) % bass.size]
                pluck(out, t + barK * 4 * beat, freq(root, -1), 0.3, r, 0.997)
                pluck(out, t + barK * 4 * beat + 2 * beat, freq(root + 4, -2), 0.22, r, 0.997)
            }
            t += 8 * beat
        }
        return fold(out, loop)
    }

    /** Осень: ля-эолийский, медленнее (72 уд/мин), щипки лютни и мягкий низкий бурдон */
    private fun musicAutumn(): DoubleArray {
        val beat = 60 / 72.0
        val phrases = 7
        val loop = phrases * 8 * beat
        val out = buf(loop + 4)
        val r = java.util.Random(2210)
        // ступени ля-эолийского от A3
        val scale = doubleArrayOf(0.0, 2.0, 3.0, 5.0, 7.0, 8.0, 10.0, 12.0, 14.0, 15.0)
        fun freq(step: Int, octave: Int = 0) = 220.0 * 2.0.pow((scale[step.coerceIn(0, scale.size - 1)] + 12 * octave) / 12)
        val a = intArrayOf(4, -1, 3, 4, 5, -1, 4, 3, 2, -1, 1, 2, 0, -1, -1, -1)
        val a2 = intArrayOf(4, -1, 3, 4, 5, 6, 7, -1, 6, 5, 4, -1, 3, -1, 2, -1)
        val b = intArrayOf(7, -1, 6, 5, 4, -1, 5, 6, 4, -1, 3, 2, 3, -1, -1, -1)
        val form = listOf(a, a2, b, a, a2, b, a)
        val bass = intArrayOf(0, 0, 5, 3, 0, 0, 4, 3, 5, 3, 0, 0, 3, 0)
        var t = 0.0
        for ((ph, phrase) in form.withIndex()) {
            for ((k, step) in phrase.withIndex()) {
                if (step >= 0) pluck(out, t + k * beat / 2 + (r.nextDouble() - 0.5) * 0.015, freq(step), 0.28, r, 0.9965)
            }
            for (barK in 0 until 2) {
                val root = bass[(ph * 2 + barK) % bass.size]
                pluck(out, t + barK * 4 * beat, freq(root, -1), 0.3, r, 0.9975)
                pluck(out, t + barK * 4 * beat + 2 * beat, freq(root + 4, -1), 0.16, r, 0.9975)
            }
            t += 8 * beat
        }
        val res = fold(out, loop)
        drone(res, loop, 110.0, 0.07, 3)
        drone(res, loop, 164.8, 0.04, 2)
        return res
    }

    /** Зима: ре-минорная пентатоника, 66 уд/мин, редкие колокольчики и блок-флейта, много тишины */
    private fun musicWinter(): DoubleArray {
        val beat = 60 / 66.0
        val form = listOf(
            intArrayOf(4, -1, -1, -1, 2, -1, -1, -1, 3, -1, -1, -1, 0, -1, -1, -1),
            intArrayOf(5, -1, -1, -1, 4, -1, 3, -1, 2, -1, -1, -1, -1, -1, -1, -1),
            intArrayOf(2, -1, -1, -1, -1, -1, 3, -1, 4, -1, -1, -1, 6, -1, -1, -1),
            intArrayOf(7, -1, -1, -1, 5, -1, -1, -1, 4, -1, -1, -1, 2, -1, 0, -1),
            intArrayOf(5, -1, -1, -1, 4, -1, 3, -1, 2, -1, -1, -1, -1, -1, -1, -1),
            intArrayOf(4, -1, -1, -1, 2, -1, -1, -1, 3, -1, -1, -1, 0, -1, -1, -1),
        )
        val loop = form.size * 8 * beat
        val out = buf(loop + 5)
        val r = java.util.Random(3303)
        val scale = doubleArrayOf(0.0, 3.0, 5.0, 7.0, 10.0, 12.0, 15.0, 17.0, 19.0, 22.0)
        fun freq(step: Int, octave: Int = 0) = 293.66 * 2.0.pow((scale[step.coerceIn(0, scale.size - 1)] + 12 * octave) / 12)
        var t = 0.0
        for ((ph, phrase) in form.withIndex()) {
            for ((k, step) in phrase.withIndex()) {
                if (step < 0) continue
                val st = t + k * beat / 2 + (r.nextDouble() - 0.5) * 0.02
                bell(out, st, freq(step), 0.2, 1.3, 5.0)
                if (ph % 2 == 1) recorder(out, st + 0.02, beat * 1.6, freq(step), 0.05)
            }
            // низкий колокол на начало каждой фразы
            val root = if (ph % 3 == 2) 3 else 0
            bell(out, t, freq(root, -1), 0.17, 0.8, 6.0)
            t += 8 * beat
        }
        val res = fold(out, loop)
        drone(res, loop, 73.4, 0.05, 2)
        drone(res, loop, 110.0, 0.03, 3)
        return res
    }

    /** MP3 (LAME на чистой Java): моно, 22050 Гц, 64 кбит/с, высокое качество */
    private fun writeMp3(f: File, data: DoubleArray) {
        var peak = 1e-9
        for (v in data) peak = maxOf(peak, Math.abs(v))
        val gain = if (peak > 0.9) 0.9 / peak else 1.0
        val pcm = ByteArray(data.size * 2)
        for (i in data.indices) {
            val v = (data[i] * gain * 32767).toInt().coerceIn(-32768, 32767)
            pcm[i * 2] = (v and 255).toByte(); pcm[i * 2 + 1] = (v shr 8 and 255).toByte()
        }
        val fmt = javax.sound.sampled.AudioFormat(SR.toFloat(), 16, 1, true, false)
        val enc = de.sciss.jump3r.lowlevel.LameEncoder(
            fmt, 64, de.sciss.jump3r.lowlevel.LameEncoder.CHANNEL_MODE_MONO, de.sciss.jump3r.lowlevel.LameEncoder.QUALITY_HIGH, false,
        )
        val out = java.io.ByteArrayOutputStream()
        val mp3 = ByteArray(enc.getMP3BufferSize())
        val chunk = enc.getPCMBufferSize()
        var off = 0
        while (off < pcm.size) {
            val len = minOf(chunk, pcm.size - off)
            val n = enc.encodeBuffer(pcm, off, len, mp3)
            out.write(mp3, 0, n)
            off += len
        }
        out.write(mp3, 0, enc.encodeFinish(mp3))
        f.writeBytes(out.toByteArray())
        println("${f.name}: ${out.size() / 1024} KB, ${"%.1f".format(data.size.toDouble() / SR)} s")
    }

    private fun write(f: File, data: DoubleArray) {
        var peak = 1e-9
        for (v in data) peak = maxOf(peak, Math.abs(v))
        val gain = if (peak > 0.95) 0.95 / peak else 1.0
        val bytes = java.io.ByteArrayOutputStream()
        fun i32(v: Int) { bytes.write(v and 255); bytes.write(v shr 8 and 255); bytes.write(v shr 16 and 255); bytes.write(v shr 24 and 255) }
        fun i16(v: Int) { bytes.write(v and 255); bytes.write(v shr 8 and 255) }
        val dataLen = data.size * 2
        bytes.write("RIFF".toByteArray()); i32(36 + dataLen); bytes.write("WAVE".toByteArray())
        bytes.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(SR); i32(SR * 2); i16(2); i16(16)
        bytes.write("data".toByteArray()); i32(dataLen)
        for (v in data) i16((v * gain * 32767).toInt().coerceIn(-32768, 32767))
        f.writeBytes(bytes.toByteArray())
    }
}
