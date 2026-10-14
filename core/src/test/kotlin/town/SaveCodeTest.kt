package town

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import town.data.Content
import town.sim.MapSize
import town.sim.SaveCodec
import town.sim.World

class SaveCodeTest {
    private val content = Content.parse(File("../assets/data/content.json").readText(), File("../assets/data/scenarios.json").readText())

    @Test
    fun roundTripOnRealSave() {
        val big = File("build/balance/big.json")
        val text = if (big.exists()) big.readText() else {
            val w = World.create(content, 42, MapSize.MEDIUM)
            repeat(2000) { w.step() }
            SaveCodec.write(w)
        }
        val code = SaveCode.encode(text)
        println("SaveCode: ${text.length} -> ${code.length} знаков (${100 * code.length / text.length}%)")
        assertEquals(text, SaveCode.decode(code))
        assertEquals(text, SaveCode.decode(code.chunked(60).joinToString("\n")))
        val i = code.length / 2
        val bad = code.substring(0, i) + (if (code[i] == 'A') 'B' else 'A') + code.substring(i + 1)
        assertNull(SaveCode.decode(bad))
        assertNull(SaveCode.decode("HV1:"))
        assertNull(SaveCode.decode("abc"))
        assertNull(SaveCode.decode(code.dropLast(5)))
    }

    @Test
    fun smallAndRepeating() {
        for (t in listOf("", "a", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Привет, мир! абвабвабв", "abababababababababab")) {
            assertEquals(t, SaveCode.decode(SaveCode.encode(t)), t)
        }
        // много данных: словарь заполняется и сбрасывается
        val sb = StringBuilder()
        var x = 12345
        repeat(400_000) { x = x * 1103515245 + 12345; sb.append('a' + ((x ushr 16) % 7)) }
        val t = sb.toString()
        assertEquals(t, SaveCode.decode(SaveCode.encode(t)))
    }

    @Test
    fun dates() {
        assertEquals(19700101, dateFromDays(0))
        assertEquals(20261012, dateFromDays(20738))
        assertEquals("12.10.2026", dateText(20261012))
        assertTrue(todayDate() > 20250000)
        assertNotNull(SaveCode.encode("x"))
    }
}
