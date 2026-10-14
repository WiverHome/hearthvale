package town

import town.data.Lang
import kotlin.test.Test
import kotlin.test.assertEquals

class LangTest {
    @Test
    fun pluralForms() {
        Lang.load("""{ "pts": { "ru": "{0} {0|очко|очка|очков}", "en": "{0} {0|point|points}" } }""")
        Lang.code = "ru"
        assertEquals("1 очко", Lang.t("pts", 1))
        assertEquals("3 очка", Lang.t("pts", 3))
        assertEquals("5 очков", Lang.t("pts", 5))
        assertEquals("11 очков", Lang.t("pts", 11))
        assertEquals("21 очко", Lang.t("pts", 21))
        assertEquals("112 очков", Lang.t("pts", 112))
        assertEquals("1024 очка", Lang.t("pts", 1024))
        Lang.code = "en"
        assertEquals("1 point", Lang.t("pts", 1))
        assertEquals("0 points", Lang.t("pts", 0))
        assertEquals("2 points", Lang.t("pts", 2))
    }
}
