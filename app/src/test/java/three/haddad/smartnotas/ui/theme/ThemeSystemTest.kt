package three.haddad.smartnotas.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import three.haddad.smartnotas.data.AccentTheme
import three.haddad.smartnotas.data.AppearanceMode

/**
 * Unit tests for NUVORA's centralized theme system:
 * - AccentTheme enum and stable ID serialization
 * - AppearanceMode enum
 * - Color scheme generation for all 7 accents in Light, Dark, and OLED modes
 * - WCAG contrast calculations for legibility and accessibility
 */
class ThemeSystemTest {
    @Test
    fun accentThemeEnum_hasCorrectStableIds() {
        assertEquals("maroon", AccentTheme.MAROON.id)
        assertEquals("royal_blue", AccentTheme.ROYAL_BLUE.id)
        assertEquals("forest_green", AccentTheme.FOREST_GREEN.id)
        assertEquals("dark_brown", AccentTheme.DARK_BROWN.id)
        assertEquals("ocean_teal", AccentTheme.OCEAN_TEAL.id)
        assertEquals("pure_black", AccentTheme.PURE_BLACK.id)
        assertEquals("amethyst", AccentTheme.AMETHYST.id)
        assertEquals(7, AccentTheme.entries.size)
    }

    @Test
    fun accentTheme_fromId_handlesKnownAndFallbackSafely() {
        assertEquals(AccentTheme.MAROON, AccentTheme.fromId("maroon"))
        assertEquals(AccentTheme.ROYAL_BLUE, AccentTheme.fromId("royal_blue"))
        assertEquals(AccentTheme.FOREST_GREEN, AccentTheme.fromId("forest_green"))
        assertEquals(AccentTheme.DARK_BROWN, AccentTheme.fromId("dark_brown"))
        assertEquals(AccentTheme.OCEAN_TEAL, AccentTheme.fromId("ocean_teal"))
        assertEquals(AccentTheme.PURE_BLACK, AccentTheme.fromId("pure_black"))
        assertEquals(AccentTheme.AMETHYST, AccentTheme.fromId("amethyst"))

        // Unknown or null falls back to AMETHYST
        assertEquals(AccentTheme.AMETHYST, AccentTheme.fromId("invalid_color_theme"))
        assertEquals(AccentTheme.AMETHYST, AccentTheme.fromId(""))
        assertEquals(AccentTheme.AMETHYST, AccentTheme.fromId(null))
    }

    @Test
    fun appearanceMode_containsExpectedModes() {
        val modes = AppearanceMode.entries
        assertTrue(modes.contains(AppearanceMode.LIGHT))
        assertTrue(modes.contains(AppearanceMode.DARK))
        assertTrue(modes.contains(AppearanceMode.SYSTEM))
    }

    @Test
    fun buildColorScheme_lightMode_forAll7Accents_populatesRolesDeterministically() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = false, blackThemeActive = false)
            assertNotEquals("primary must be specified for $accent in Light", Color.Unspecified, scheme.primary)
            assertNotEquals("onPrimary must be specified for $accent in Light", Color.Unspecified, scheme.onPrimary)
            assertNotEquals("surface must be specified for $accent in Light", Color.Unspecified, scheme.surface)
            assertNotEquals("onSurface must be specified for $accent in Light", Color.Unspecified, scheme.onSurface)
            assertNotEquals("background must be specified for $accent in Light", Color.Unspecified, scheme.background)
            assertNotEquals("onBackground must be specified for $accent in Light", Color.Unspecified, scheme.onBackground)
            assertNotEquals("primaryContainer must be specified for $accent in Light", Color.Unspecified, scheme.primaryContainer)
            assertNotEquals("onPrimaryContainer must be specified for $accent in Light", Color.Unspecified, scheme.onPrimaryContainer)
            assertNotEquals("error must be specified for $accent in Light", Color.Unspecified, scheme.error)
            assertNotEquals("outline must be specified for $accent in Light", Color.Unspecified, scheme.outline)
        }
    }

    @Test
    fun buildColorScheme_darkMode_forAll7Accents_populatesRolesDeterministically() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)
            assertNotEquals("primary must be specified for $accent in Dark", Color.Unspecified, scheme.primary)
            assertNotEquals("onPrimary must be specified for $accent in Dark", Color.Unspecified, scheme.onPrimary)
            assertNotEquals("surface must be specified for $accent in Dark", Color.Unspecified, scheme.surface)
            assertNotEquals("onSurface must be specified for $accent in Dark", Color.Unspecified, scheme.onSurface)
            assertNotEquals("background must be specified for $accent in Dark", Color.Unspecified, scheme.background)
            assertNotEquals("onBackground must be specified for $accent in Dark", Color.Unspecified, scheme.onBackground)

            // Dark surfaces must not be white
            val surfaceLum = relativeLuminance(scheme.surface)
            assertTrue("Dark mode surface for $accent should be dark (lum < 0.2), was $surfaceLum", surfaceLum < 0.2)
        }
    }

    @Test
    fun buildColorScheme_pureBlack_oledMode_usesTrueBlackBackground() {
        val oledScheme = buildColorScheme(accentTheme = AccentTheme.PURE_BLACK, darkTheme = true, blackThemeActive = true)
        assertEquals(ThemeColors.OledBackground, oledScheme.background)
        assertEquals(ThemeColors.OledSurface, oledScheme.surface)

        // Text must remain highly legible
        val textContrast = contrastRatio(oledScheme.onBackground, oledScheme.background)
        assertTrue("OLED onBackground must have high contrast against black (>= 10.0), was $textContrast", textContrast >= 10.0)
    }

    @Test
    fun pureBlack_lightMode_isHighContrastMonochrome() {
        val scheme = buildColorScheme(accentTheme = AccentTheme.PURE_BLACK, darkTheme = false, blackThemeActive = false)
        assertEquals(ThemeColors.PureBlack.LightPrimary, scheme.primary)
        assertEquals(ThemeColors.PureBlack.LightOnPrimary, scheme.onPrimary)
        val contrast = contrastRatio(scheme.onPrimary, scheme.primary)
        assertTrue("Pure Black Light mode primary button must have maximum contrast (>= 15.0), was $contrast", contrast >= 15.0)
    }

    @Test
    fun oceanTeal_lightPrimary_isSufficientlyDarkForAccessibleContrast() {
        // Ocean teal accent seed #29A4AB has ~3.0:1 contrast against white, which is inadequate for text/buttons.
        // The LightPrimary variant (#136B71) must achieve >= 4.5:1 contrast against white.
        val contrastAgainstWhite = contrastRatio(Color.White, ThemeColors.OceanTeal.LightPrimary)
        assertTrue("Ocean Teal LightPrimary must have >= 4.5:1 contrast against white, was $contrastAgainstWhite", contrastAgainstWhite >= 4.5)
    }

    @Test
    fun allAccents_lightMode_meetWcagTextContrastStandards() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = false, blackThemeActive = false)

            // Normal text on surface: >= 4.5:1
            val onSurfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
            assertTrue("onSurface on surface for $accent in Light must be >= 4.5:1, was $onSurfaceContrast", onSurfaceContrast >= 4.5)

            // Normal text on background: >= 4.5:1
            val onBgContrast = contrastRatio(scheme.onBackground, scheme.background)
            assertTrue("onBackground on background for $accent in Light must be >= 4.5:1, was $onBgContrast", onBgContrast >= 4.5)

            // Button/large graphical elements: onPrimary on primary >= 3.0:1
            val onPrimaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)
            assertTrue("onPrimary on primary for $accent in Light must be >= 3.0:1, was $onPrimaryContrast", onPrimaryContrast >= 3.0)
        }
    }

    @Test
    fun allAccents_darkMode_meetWcagTextContrastStandards() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)

            // Normal text on surface: >= 4.5:1
            val onSurfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
            assertTrue("onSurface on surface for $accent in Dark must be >= 4.5:1, was $onSurfaceContrast", onSurfaceContrast >= 4.5)

            // Normal text on background: >= 4.5:1
            val onBgContrast = contrastRatio(scheme.onBackground, scheme.background)
            assertTrue("onBackground on background for $accent in Dark must be >= 4.5:1, was $onBgContrast", onBgContrast >= 4.5)

            // Button/large graphical elements: onPrimary on primary >= 3.0:1
            val onPrimaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)
            assertTrue("onPrimary on primary for $accent in Dark must be >= 3.0:1, was $onPrimaryContrast", onPrimaryContrast >= 3.0)
        }
    }

    // Helper functions for WCAG 2.1 relative luminance and contrast ratio calculations
    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double =
            if (c <= 0.03928f) {
                c.toDouble() / 12.92
            } else {
                Math.pow((c + 0.055) / 1.055, 2.4)
            }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(
        c1: Color,
        c2: Color,
    ): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
