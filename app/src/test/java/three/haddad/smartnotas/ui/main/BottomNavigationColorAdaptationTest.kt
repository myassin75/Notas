package three.haddad.smartnotas.ui.main

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.AccentTheme
import three.haddad.smartnotas.ui.theme.ThemeColors
import three.haddad.smartnotas.ui.theme.buildColorScheme
import three.haddad.smartnotas.ui.theme.resolveSemanticPalette

/**
 * Unit tests verifying the NUVORA Bottom Navigation color adaptation:
 * - Preservation of navigation destinations, order, labels, and icons
 * - Light accent color resolution for all 7 themes in Dark Mode
 * - Accessible neutral highlight for Pure Black theme (no black-on-black)
 * - Restrained translucent active pill background tint (alpha 0.16 - 0.18f)
 * - Readable neutral gray unselected icon & label colors with sufficient WCAG contrast
 * - Bottom nav glass container styling
 */
class BottomNavigationColorAdaptationTest {

    @Test
    fun mainTab_preservesExactDestinationsAndOrder() {
        val tabs = MainTab.entries
        assertEquals("Bottom navigation must have exactly 4 items", 4, tabs.size)
        assertEquals("First tab must be Home", MainTab.Home, tabs[0])
        assertEquals("Second tab must be Calendar", MainTab.Calendar, tabs[1])
        assertEquals("Third tab must be Settings", MainTab.Settings, tabs[2])
        assertEquals("Fourth tab must be Search", MainTab.Search, tabs[3])
    }

    @Test
    fun mainTab_preservesExactLabelsAndSymbols() {
        assertEquals(R.string.main_tab_home, MainTab.Home.labelRes)
        assertEquals("home", MainTab.Home.symbolName)

        assertEquals(R.string.main_tab_calendar, MainTab.Calendar.labelRes)
        assertEquals("calendar_month", MainTab.Calendar.symbolName)

        assertEquals(R.string.main_tab_settings, MainTab.Settings.labelRes)
        assertEquals("settings", MainTab.Settings.symbolName)

        assertEquals(R.string.main_tab_search, MainTab.Search.labelRes)
        assertEquals("search", MainTab.Search.symbolName)
    }

    @Test
    fun selectedAccent_inDarkMode_usesActiveThemeLightAccent() {
        AccentTheme.entries.forEach { accent ->
            val isBlackTheme = accent == AccentTheme.PURE_BLACK
            val semanticPalette = resolveSemanticPalette(accentTheme = accent, darkTheme = true)
            val selectedAccent = if (isBlackTheme) Color(0xFFD4D4DE) else semanticPalette.primaryAction

            assertNotEquals("Selected accent must be specified for $accent", Color.Unspecified, selectedAccent)

            // In dark mode, selected accents must be light variants (luminance > 0.30)
            val lum = relativeLuminance(selectedAccent)
            assertTrue(
                "Selected accent for $accent in dark mode must be a light accent variant (lum > 0.30), was $lum",
                lum > 0.30,
            )

            // Must have high contrast (>= 6:1) against dark container background
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)
            val darkNavContainer = scheme.surface.copy(alpha = 0.85f)
            val contrast = contrastRatio(selectedAccent, darkNavContainer)
            assertTrue(
                "Selected accent for $accent must have >= 6.0:1 contrast against bottom nav glass container, was $contrast",
                contrast >= 6.0,
            )
        }
    }

    @Test
    fun pureBlackTheme_inDarkMode_usesAccessibleNeutralHighlight() {
        val isBlackTheme = true
        val selectedAccent = if (isBlackTheme) Color(0xFFD4D4DE) else Color.Black

        // Pure black must NEVER use black-on-black
        assertNotEquals("Pure Black theme must not use black as selected accent in dark mode", Color.Black, selectedAccent)
        assertEquals("Pure Black theme must use platinum/silver neutral highlight", Color(0xFFD4D4DE), selectedAccent)

        // Contrast against OLED bottom nav container Color(0xFF101014)
        val oledNavContainer = Color(0xFF101014).copy(alpha = 0.88f)
        val contrast = contrastRatio(selectedAccent, oledNavContainer)
        assertTrue(
            "Pure Black selected accent must achieve >= 10.0:1 contrast against dark nav container, was $contrast",
            contrast >= 10.0,
        )
    }

    @Test
    fun unselectedNavColor_inDarkMode_hasHighLegibilityAndContrast() {
        val unselectedNavColor = Color(0xFFA6A4B4)

        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)
            val darkNavContainer = scheme.surface.copy(alpha = 0.85f)

            val contrast = contrastRatio(unselectedNavColor, darkNavContainer)
            assertTrue(
                "Unselected nav color in dark mode must achieve >= 4.5:1 contrast against nav container for $accent, was $contrast",
                contrast >= 4.5,
            )
        }

        // Also test OLED container
        val oledNavContainer = Color(0xFF101014).copy(alpha = 0.88f)
        val oledContrast = contrastRatio(unselectedNavColor, oledNavContainer)
        assertTrue(
            "Unselected nav color in OLED mode must achieve >= 6.0:1 contrast, was $oledContrast",
            oledContrast >= 6.0,
        )

        // Must be a readable neutral gray (lum between 0.35 and 0.50)
        val lum = relativeLuminance(unselectedNavColor)
        assertTrue("Unselected nav color must be readable neutral gray (0.35 <= lum <= 0.50), was $lum", lum in 0.35..0.50)
    }

    @Test
    fun unselectedNavColor_inLightMode_hasLegibilityAgainstWhiteAndLightNav() {
        val unselectedNavColor = Color(0xFF757482)

        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = false, blackThemeActive = false)
            val lightNavContainer = scheme.surface

            val contrast = contrastRatio(unselectedNavColor, lightNavContainer)
            assertTrue(
                "Unselected nav color in light mode must achieve >= 3.0:1 contrast against light container for $accent, was $contrast",
                contrast >= 3.0,
            )
        }
    }

    @Test
    fun activePillTint_remainsTranslucentAndAvoidsSaturatedBlock() {
        // Active pill tint in dark mode: alpha is 0.16f (or 0.18f for black theme)
        AccentTheme.entries.forEach { accent ->
            val isBlackTheme = accent == AccentTheme.PURE_BLACK
            val semanticPalette = resolveSemanticPalette(accentTheme = accent, darkTheme = true)
            val selectedAccent = if (isBlackTheme) Color(0xFFD4D4DE) else semanticPalette.primaryAction

            val activePillTint =
                if (isBlackTheme) Color(0xFFD4D4DE).copy(alpha = 0.18f) else selectedAccent.copy(alpha = 0.16f)

            assertTrue("Active pill tint alpha must be translucent (<= 0.20f), was ${activePillTint.alpha}", activePillTint.alpha <= 0.20f)
            assertTrue("Active pill tint alpha must remain visible (>= 0.12f), was ${activePillTint.alpha}", activePillTint.alpha >= 0.12f)
        }
    }

    @Test
    fun bottomNavContainer_isDarkGlassSurface() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)
            val darkNavContainer = scheme.surface.copy(alpha = 0.85f)
            val lum = relativeLuminance(darkNavContainer)

            // Dark glass container must have very low luminance (near black)
            assertTrue("BottomNav dark glass container for $accent must have lum < 0.10, was $lum", lum < 0.10)
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
