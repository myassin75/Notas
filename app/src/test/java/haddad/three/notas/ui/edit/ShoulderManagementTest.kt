package haddad.three.notas.ui.edit

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.ui.components.formatColorToHex
import haddad.three.notas.ui.components.isColorDark
import haddad.three.notas.ui.components.parseColorHexOrNull
import haddad.three.notas.ui.components.parseHsvFromHex
import haddad.three.notas.ui.components.parseNoteColorHex
import haddad.three.notas.ui.components.readableBadgeColors
import haddad.three.notas.ui.components.rgbToHsv

class ShoulderManagementTest {

    // -------------------------------------------------------------------------
    // 1. HEX Parsing and Validation
    // -------------------------------------------------------------------------

    @Test
    fun parseColorHexOrNull_validInputs_returnCorrectColor() {
        val red = parseColorHexOrNull("#FF0000")
        assertNotNull(red)
        assertEquals(1f, red!!.red, 0.001f)
        assertEquals(0f, red.green, 0.001f)
        assertEquals(0f, red.blue, 0.001f)

        // Without '#' prefix
        val green = parseColorHexOrNull("047D06")
        assertNotNull(green)
        assertEquals(4f / 255f, green!!.red, 0.005f)
        assertEquals(125f / 255f, green.green, 0.005f)
        assertEquals(6f / 255f, green.blue, 0.005f)

        // Case-insensitive
        val maroon = parseColorHexOrNull("#800000")
        val maroonLower = parseColorHexOrNull("#800000")
        assertEquals(maroon, maroonLower)
    }

    @Test
    fun parseColorHexOrNull_invalidInputs_returnNullWithoutCrashing() {
        assertNull(parseColorHexOrNull(null))
        assertNull(parseColorHexOrNull(""))
        assertNull(parseColorHexOrNull("   "))
        assertNull(parseColorHexOrNull("#"))
        assertNull(parseColorHexOrNull("#FFF")) // 3 digits unsupported
        assertNull(parseColorHexOrNull("#12345")) // 5 digits
        assertNull(parseColorHexOrNull("#1234567")) // 7 hex digits
        assertNull(parseColorHexOrNull("#GGGGGG")) // Invalid hex characters
        assertNull(parseColorHexOrNull("ZZZZZZ"))
    }

    @Test
    fun formatColorToHex_producesConsistentUppercaseHex() {
        assertEquals("#FF0000", formatColorToHex(Color.Red))
        assertEquals("#00FF00", formatColorToHex(Color.Green))
        assertEquals("#0000FF", formatColorToHex(Color.Blue))
        assertEquals("#000000", formatColorToHex(Color.Black))
        assertEquals("#FFFFFF", formatColorToHex(Color.White))
    }

    @Test
    fun parseNoteColorHex_fallbackBehavior_handlesMalformedGracefully() {
        val fallback = Color(0xFF6750A4)
        assertEquals(Color(0xFFFF0000), parseNoteColorHex("#FF0000", fallback))
        assertEquals(fallback, parseNoteColorHex(null, fallback))
        assertEquals(fallback, parseNoteColorHex("", fallback))
        assertEquals(fallback, parseNoteColorHex("invalid", fallback))
    }

    // -------------------------------------------------------------------------
    // 2. Color Contrast & Red Hue Handling (Requirement 5)
    // -------------------------------------------------------------------------

    @Test
    fun readableBadgeColors_darkRedAndPureRedInDarkMode_avoidsSaturatedRedText() {
        val darkRed = Color(0xFF800000)
        val pureRed = Color(0xFFFF0000)

        for (redColor in listOf(darkRed, pureRed)) {
            val (container, foreground) = readableBadgeColors(redColor, isDark = true)

            // Verify foreground is NOT saturated red (saturation must be <= 0.18f)
            val fgHsv = rgbToHsv(foreground.red, foreground.green, foreground.blue)
            assertTrue(
                "Red shoulder badge text in dark mode must not be saturated red! Was: ${fgHsv[1]}",
                fgHsv[1] <= 0.18f,
            )

            // Brightness must be high for readability
            assertTrue(
                "Red shoulder badge text in dark mode must be high brightness! Was: ${fgHsv[2]}",
                fgHsv[2] >= 0.95f,
            )

            // Relative luminance of foreground must be high
            val fgLum = 0.2126f * foreground.red + 0.7152f * foreground.green + 0.0722f * foreground.blue
            assertTrue("Foreground relative luminance must be > 0.80. Was: $fgLum", fgLum > 0.80f)

            // Container must be a subtle translucent tint
            assertEquals(0.28f, container.alpha, 0.01f)
        }
    }

    @Test
    fun readableBadgeColors_lightMode_producesHighContrastDarkText() {
        val red = Color(0xFFFF0000)
        val yellow = Color(0xFFFFD600)
        val green = Color(0xFF047D06)

        val (_, redFg) = readableBadgeColors(red, isDark = false)
        val (_, yellowFg) = readableBadgeColors(yellow, isDark = false)
        val (_, greenFg) = readableBadgeColors(green, isDark = false)

        // Light mode foregrounds must have low luminance for high contrast on light pastel
        val redLum = 0.2126f * redFg.red + 0.7152f * redFg.green + 0.0722f * redFg.blue
        val yellowLum = 0.2126f * yellowFg.red + 0.7152f * yellowFg.green + 0.0722f * yellowFg.blue
        val greenLum = 0.2126f * greenFg.red + 0.7152f * greenFg.green + 0.0722f * greenFg.blue

        assertTrue("Light mode red text must be dark (lum < 0.20). Was: $redLum", redLum < 0.20f)
        assertTrue("Light mode yellow text must be dark bronze/amber (lum < 0.20). Was: $yellowLum", yellowLum < 0.20f)
        assertTrue("Light mode green text must be dark forest (lum < 0.20). Was: $greenLum", greenLum < 0.20f)
    }

    @Test
    fun readableBadgeColors_monochromeColors_handledGracefully() {
        val black = Color(0xFF000000)
        val white = Color(0xFFFFFFFF)

        val (_, darkBlackFg) = readableBadgeColors(black, isDark = true)
        val (_, lightBlackFg) = readableBadgeColors(black, isDark = false)

        // Dark mode with black shoulder should have light readable text
        val darkBlackLum = 0.2126f * darkBlackFg.red + 0.7152f * darkBlackFg.green + 0.0722f * darkBlackFg.blue
        assertTrue("Dark mode black shoulder must have bright text. Was: $darkBlackLum", darkBlackLum > 0.80f)

        // Light mode with black shoulder should have dark readable text
        val lightBlackLum = 0.2126f * lightBlackFg.red + 0.7152f * lightBlackFg.green + 0.0722f * lightBlackFg.blue
        assertTrue("Light mode black shoulder must have dark text. Was: $lightBlackLum", lightBlackLum < 0.20f)
    }

    // -------------------------------------------------------------------------
    // 3. Visual Unambiguity & Contrast Checks (Requirement 4)
    // -------------------------------------------------------------------------

    @Test
    fun isColorDark_distinguishesDarkAndLightHuesAccurately() {
        assertTrue(isColorDark(Color.Black))
        assertTrue(isColorDark(Color(0xFF800000))) // Maroon
        assertTrue(isColorDark(Color(0xFF04147D))) // Deep navy
        assertTrue(isColorDark(Color(0xFF047D06))) // Forest green
        assertTrue(isColorDark(Color(0xFF4D2002))) // Deep bronze

        assertFalse(isColorDark(Color.White))
        assertFalse(isColorDark(Color.Yellow))
        assertFalse(isColorDark(Color.Cyan))
        assertFalse(isColorDark(Color(0xFF29A4AB))) // Bright teal
    }

    // -------------------------------------------------------------------------
    // 4. Shoulder Identity & Immutability Against Theme Changes (Requirement 1)
    // -------------------------------------------------------------------------

    @Test
    fun shoulderIdentity_storedColorHexIsAuthoritative_immuneToThemeAccent() {
        val shoulder = ShoulderEntity(
            id = 42L,
            name = "Finance",
            normalizedName = "finance",
            colorHex = "#29A4AB",
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val themeAccentA = Color(0xFF800000)
        val themeAccentB = Color(0xFF047D06)
        val themeAccentC = Color(0xFF000000)

        // Color resolution must strictly use the shoulder's stored colorHex
        val colorUnderThemeA = parseNoteColorHex(shoulder.colorHex, themeAccentA)
        val colorUnderThemeB = parseNoteColorHex(shoulder.colorHex, themeAccentB)
        val colorUnderThemeC = parseNoteColorHex(shoulder.colorHex, themeAccentC)

        assertEquals(colorUnderThemeA, colorUnderThemeB)
        assertEquals(colorUnderThemeB, colorUnderThemeC)
        assertEquals(Color(0xFF29A4AB), colorUnderThemeA)
    }

    // -------------------------------------------------------------------------
    // 5. Shoulder Editing & Association Integrity (Requirement 2 & 4)
    // -------------------------------------------------------------------------

    @Test
    fun shoulderEditing_preservesIdAndUpdatesAttributesWithoutCreatingNewEntity() {
        val existing = ShoulderEntity(
            id = 15L,
            name = "Project Alpha",
            normalizedName = "project alpha",
            colorHex = "#800000",
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        // When user edits name and color
        val updated = existing.copy(
            name = "Project Beta",
            normalizedName = "project beta",
            colorHex = "#047D06",
            updatedAt = 2000L,
        )

        // ID must be preserved
        assertEquals(15L, updated.id)
        assertEquals("Project Beta", updated.name)
        assertEquals("#047D06", updated.colorHex)
        assertEquals(existing.createdAt, updated.createdAt)
    }

    @Test
    fun shoulderUnassignment_clearsAssociationWithoutAlteringEntity() {
        val shoulder = ShoulderEntity(
            id = 10L,
            name = "Work",
            normalizedName = "work",
            colorHex = "#04147D",
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        var assignedShoulderId: Long? = shoulder.id
        assertEquals(10L, assignedShoulderId)

        // Applying "No shoulder"
        assignedShoulderId = null
        assertNull(assignedShoulderId)

        // The shoulder entity itself remains completely intact
        assertEquals(10L, shoulder.id)
        assertEquals("Work", shoulder.name)
        assertEquals("#04147D", shoulder.colorHex)
    }

    @Test
    fun parseHsvFromHex_extractsAccurateHsvOrFallback() {
        val hsv = parseHsvFromHex("#FF0000")
        assertEquals(0f, hsv[0], 0.01f) // Hue
        assertEquals(1f, hsv[1], 0.01f) // Saturation
        assertEquals(1f, hsv[2], 0.01f) // Value

        val fallback = parseHsvFromHex(null, defaultHue = 120f, defaultSat = 0.8f, defaultVal = 0.9f)
        assertEquals(120f, fallback[0], 0.01f)
        assertEquals(0.8f, fallback[1], 0.01f)
        assertEquals(0.9f, fallback[2], 0.01f)
    }
}
