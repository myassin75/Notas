package haddad.three.notas.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import haddad.three.notas.data.AccentTheme
import haddad.three.notas.data.AppearanceMode
import haddad.three.notas.data.ThemeMode
import haddad.three.notas.data.effectiveDarkTheme

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

        // Unknown or null falls back to PURE_BLACK
        assertEquals(AccentTheme.MAROON, AccentTheme.fromId("invalid_color_theme"))
        assertEquals(AccentTheme.MAROON, AccentTheme.fromId(""))
        assertEquals(AccentTheme.MAROON, AccentTheme.fromId(null))
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
    fun buildColorScheme_darkMode_hasThemeSpecificPrimaryAwareBackgrounds() {
        assertEquals(ThemeColors.Maroon.DarkBackground, buildColorScheme(AccentTheme.MAROON, darkTheme = true, blackThemeActive = false).background)
        assertEquals(ThemeColors.RoyalBlue.DarkBackground, buildColorScheme(AccentTheme.ROYAL_BLUE, darkTheme = true, blackThemeActive = false).background)
        assertEquals(ThemeColors.ForestGreen.DarkBackground, buildColorScheme(AccentTheme.FOREST_GREEN, darkTheme = true, blackThemeActive = false).background)
        assertEquals(ThemeColors.DarkBrown.DarkBackground, buildColorScheme(AccentTheme.DARK_BROWN, darkTheme = true, blackThemeActive = false).background)
        assertEquals(ThemeColors.OceanTeal.DarkBackground, buildColorScheme(AccentTheme.OCEAN_TEAL, darkTheme = true, blackThemeActive = false).background)
        assertEquals(ThemeColors.PureBlack.DarkBackground, buildColorScheme(AccentTheme.PURE_BLACK, darkTheme = true, blackThemeActive = false).background)
        assertEquals(ThemeColors.Amethyst.DarkBackground, buildColorScheme(AccentTheme.AMETHYST, darkTheme = true, blackThemeActive = false).background)
    }

    @Test
    fun buildColorScheme_darkMode_surfacesAreDifferentiatedFromBackground() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)
            assertNotEquals("Surface should be differentiated from background for $accent", scheme.background, scheme.surface)
            assertNotEquals("outlineVariant should be specified for $accent", Color.Unspecified, scheme.outlineVariant)
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

    @Test
    fun resolveAmbientPalette_forAll7Accents_producesDistinctThemeIdentities() {
        AccentTheme.entries.forEach { accent ->
            val palette = resolveAmbientPalette(accent, isOledBlack = false)
            assertNotEquals("Base color for $accent must be specified", Color.Unspecified, palette.base)
            assertNotEquals("glowPrimary for $accent must be specified", Color.Unspecified, palette.glowPrimary)
            assertNotEquals("glowSecondary for $accent must be specified", Color.Unspecified, palette.glowSecondary)

            // Base must be dark near-black
            val baseLum = relativeLuminance(palette.base)
            assertTrue("Ambient base for $accent must be very dark near-black (lum < 0.05), was $baseLum", baseLum < 0.05)
        }
    }

    @Test
    fun resolveAmbientPalette_oled_usesTrueBlackBase() {
        val oledMaroonPalette = resolveAmbientPalette(AccentTheme.MAROON, isOledBlack = true)
        assertEquals("OLED mode must have true black base regardless of accent", Color(0xFF000000), oledMaroonPalette.base)
        val oledPureBlackPalette = resolveAmbientPalette(AccentTheme.PURE_BLACK, isOledBlack = true)
        assertEquals("OLED Pure Black must have true black base", Color(0xFF000000), oledPureBlackPalette.base)
    }

    @Test
    fun semanticPalette_resolvesForAll7Accents_inBothLightAndDark() {
        AccentTheme.entries.forEach { accent ->
            // Light
            val lightPalette = resolveSemanticPalette(accentTheme = accent, darkTheme = false)
            assertNotEquals(Color.Unspecified, lightPalette.primaryAction)
            assertNotEquals(Color.Unspecified, lightPalette.primaryActionForeground)
            assertNotEquals(Color.Unspecified, lightPalette.cardSurface)
            assertNotEquals(Color.Unspecified, lightPalette.cardBorder)
            assertNotEquals(Color.Unspecified, lightPalette.modalSurface)
            assertNotEquals(Color.Unspecified, lightPalette.primaryText)
            assertNotEquals(Color.Unspecified, lightPalette.secondaryText)
            assertNotEquals(Color.Unspecified, lightPalette.divider)
            assertNotEquals(Color.Unspecified, lightPalette.inputSurface)
            assertNotEquals(Color.Unspecified, lightPalette.error)
            assertNotEquals(Color.Unspecified, lightPalette.success)
            assertNotEquals(Color.Unspecified, lightPalette.warning)

            // Dark
            val darkPalette = resolveSemanticPalette(accentTheme = accent, darkTheme = true)
            assertNotEquals(Color.Unspecified, darkPalette.ambientTint)
            assertNotEquals(Color.Unspecified, darkPalette.primaryAction)
            assertNotEquals(Color.Unspecified, darkPalette.primaryActionForeground)
            assertNotEquals(Color.Unspecified, darkPalette.selectedStateForeground)
            assertNotEquals(Color.Unspecified, darkPalette.selectedStateContainer)
            assertNotEquals(Color.Unspecified, darkPalette.cardSurface)
            assertNotEquals(Color.Unspecified, darkPalette.cardBorder)
            assertNotEquals(Color.Unspecified, darkPalette.modalSurface)
            assertNotEquals(Color.Unspecified, darkPalette.primaryText)
            assertNotEquals(Color.Unspecified, darkPalette.secondaryText)
            assertNotEquals(Color.Unspecified, darkPalette.divider)
            assertNotEquals(Color.Unspecified, darkPalette.inputSurface)
            assertNotEquals(Color.Unspecified, darkPalette.error)
            assertNotEquals(Color.Unspecified, darkPalette.success)
            assertNotEquals(Color.Unspecified, darkPalette.warning)

            // Action contrast check: primaryActionFg against primaryAction >= 4.5:1
            val actionContrast = contrastRatio(darkPalette.primaryActionForeground, darkPalette.primaryAction)
            assertTrue("Primary action for $accent in dark must have >= 4.5:1 contrast, was $actionContrast", actionContrast >= 4.5)
        }
    }

    @Test
    fun pureBlack_accentTheme_usesSilverActionAccentAndNearBlackAmbient() {
        val darkPalette = resolveSemanticPalette(AccentTheme.PURE_BLACK, darkTheme = true)
        // Primary action is light neutral silver, not black
        val silverLum = relativeLuminance(darkPalette.primaryAction)
        assertTrue("Pure Black primaryAction must be light silver (lum > 0.4), was $silverLum", silverLum > 0.4)

        // Action foreground on silver button must have high contrast
        val contrast = contrastRatio(darkPalette.primaryActionForeground, darkPalette.primaryAction)
        assertTrue("Pure Black button contrast must be >= 7.0, was $contrast", contrast >= 7.0)

        // Ambient tint is near-black/charcoal neutral
        val ambientLum = relativeLuminance(darkPalette.ambientTint)
        assertTrue("Ambient tint must be near-black (lum < 0.05), was $ambientLum", ambientLum < 0.05)
    }

    @Test
    fun glassVariant_containsAllRequiredVariants() {
        val variants = GlassVariant.entries
        assertTrue(variants.contains(GlassVariant.StandardCard))
        assertTrue(variants.contains(GlassVariant.SelectedCard))
        assertTrue(variants.contains(GlassVariant.BottomNav))
        assertTrue(variants.contains(GlassVariant.ModalSheet))
        assertTrue(variants.contains(GlassVariant.Dialog))
        assertTrue(variants.contains(GlassVariant.Input))
        assertTrue(variants.contains(GlassVariant.Chip))
        assertTrue(variants.contains(GlassVariant.SolidReadability))
    }

    @Test
    fun accentThemeEnum_matchesRequiredSeedHexCodes() {
        assertEquals("#800000", AccentTheme.MAROON.seedHex)
        assertEquals("#04147D", AccentTheme.ROYAL_BLUE.seedHex)
        assertEquals("#047D06", AccentTheme.FOREST_GREEN.seedHex)
        assertEquals("#4D2002", AccentTheme.DARK_BROWN.seedHex)
        assertEquals("#29A4AB", AccentTheme.OCEAN_TEAL.seedHex)
        assertEquals("#000000", AccentTheme.PURE_BLACK.seedHex)
        assertEquals("#98549C", AccentTheme.AMETHYST.seedHex)
    }

    @Test
    fun appearanceMode_effectiveDarkTheme_systemAndExplicitBehaviors() {
        // System Default must follow Android system appearance
        assertEquals(true, haddad.three.notas.data.ThemeMode.SYSTEM.effectiveDarkTheme(systemDark = true))
        assertEquals(false, haddad.three.notas.data.ThemeMode.SYSTEM.effectiveDarkTheme(systemDark = false))

        // Explicit Light selection must always be light regardless of system
        assertEquals(false, haddad.three.notas.data.ThemeMode.LIGHT.effectiveDarkTheme(systemDark = true))
        assertEquals(false, haddad.three.notas.data.ThemeMode.LIGHT.effectiveDarkTheme(systemDark = false))

        // Explicit Dark selection must always be dark regardless of system
        assertEquals(true, haddad.three.notas.data.ThemeMode.DARK.effectiveDarkTheme(systemDark = true))
        assertEquals(true, haddad.three.notas.data.ThemeMode.DARK.effectiveDarkTheme(systemDark = false))
    }

    @Test
    fun allAccents_darkMode_useLightAccentVariantsForPrimaryRoles() {
        AccentTheme.entries.forEach { accent ->
            val scheme = buildColorScheme(accentTheme = accent, darkTheme = true, blackThemeActive = false)
            // Primary in dark mode must be a light accent variant (luminance > 0.3) for high contrast against dark backgrounds
            val primaryLum = relativeLuminance(scheme.primary)
            assertTrue("Primary role for $accent in dark mode must be a light accent variant (lum > 0.3), was $primaryLum", primaryLum > 0.3)

            // Primary action on dark background must achieve high contrast >= 5.0
            val contrastAgainstDark = contrastRatio(scheme.primary, scheme.background)
            assertTrue("Primary for $accent must have >= 5.0:1 contrast against dark background, was $contrastAgainstDark", contrastAgainstDark >= 5.0)
        }
    }

    @Test
    fun pureBlack_darkMode_usesReadableNeutralActionAccent() {
        val scheme = buildColorScheme(accentTheme = AccentTheme.PURE_BLACK, darkTheme = true, blackThemeActive = false)
        // Primary role is neutral silver-platinum #D4D4DE
        assertEquals(Color(0xFFD4D4DE), scheme.primary)
        assertEquals(Color(0xFF16161C), scheme.onPrimary)
        val contrast = contrastRatio(scheme.onPrimary, scheme.primary)
        assertTrue("Neutral action accent button must have high contrast >= 10.0, was $contrast", contrast >= 10.0)
    }

    @Test
    fun accentThemes_haveValidStringResources() {
        AccentTheme.entries.forEach { accent ->
            assertTrue("titleRes must be non-zero for $accent", accent.titleRes != 0)
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
