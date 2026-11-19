package haddad.three.notas.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ShoulderColorUtilsTest {
    @Test
    fun hsvToRgb_doesNotCrash_atAnySpectrumBoundaries() {
        for (h in 0..360 step 5) {
            for (s in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                for (v in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                    val color = hsvToRgb(h.toFloat(), s, v)
                    assertTrue("Red channel out of bounds: ${color.red}", color.red in 0f..1f)
                    assertTrue("Green channel out of bounds: ${color.green}", color.green in 0f..1f)
                    assertTrue("Blue channel out of bounds: ${color.blue}", color.blue in 0f..1f)
                }
            }
        }
    }

    @Test
    fun formatColorToHex_usesAsciiDigits_evenUnderArabicLocale() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "MA"))
            val color = Color(0.5f, 0.25f, 0.85f)
            val hex = formatColorToHex(color)
            assertTrue("Hex must start with #: $hex", hex.startsWith("#"))
            assertEquals("Hex length must be 7", 7, hex.length)
            assertTrue("Hex must contain only uppercase hex characters: $hex", hex.matches(Regex("^#[0-9A-F]{6}$")))
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun readableBadgeColors_returnsValidContrastColors() {
        val base = Color(0xFF9C27B0)
        val (darkBg, darkFg) = readableBadgeColors(base, isDark = true)
        assertNotNull(darkBg)
        assertNotNull(darkFg)

        val (lightBg, lightFg) = readableBadgeColors(base, isDark = false)
        assertNotNull(lightBg)
        assertNotNull(lightFg)
    }

    @Test
    fun parseColorHexOrNull_parsesValidHexCorrectly() {
        val color = parseColorHexOrNull("#FF00AA")
        assertNotNull(color)
        val roundtripHex = formatColorToHex(color!!)
        assertEquals("#FF00AA", roundtripHex)
    }
}
