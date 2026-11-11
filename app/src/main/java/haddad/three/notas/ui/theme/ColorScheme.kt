package haddad.three.notas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import haddad.three.notas.data.AccentTheme

/**
 * Builds the complete, semantic Material 3 [ColorScheme] for a given [AccentTheme]
 * under Light Mode, Dark Mode, or OLED Pure Black Mode.
 */
fun buildColorScheme(
    accentTheme: AccentTheme,
    darkTheme: Boolean,
    blackThemeActive: Boolean = false,
): ColorScheme {
    val isOledBlack = darkTheme && (blackThemeActive || accentTheme == AccentTheme.PURE_BLACK)

    return if (darkTheme) {
        buildDarkColorScheme(accentTheme, isOledBlack)
    } else {
        buildLightColorScheme(accentTheme)
    }
}

private fun buildLightColorScheme(accentTheme: AccentTheme): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer, invPrimary) =
        when (accentTheme) {
            AccentTheme.MAROON ->
                Quintuple(
                    ThemeColors.Maroon.LightPrimary,
                    ThemeColors.Maroon.LightOnPrimary,
                    ThemeColors.Maroon.LightPrimaryContainer,
                    ThemeColors.Maroon.LightOnPrimaryContainer,
                    ThemeColors.Maroon.LightInversePrimary,
                )
            AccentTheme.ROYAL_BLUE ->
                Quintuple(
                    ThemeColors.RoyalBlue.LightPrimary,
                    ThemeColors.RoyalBlue.LightOnPrimary,
                    ThemeColors.RoyalBlue.LightPrimaryContainer,
                    ThemeColors.RoyalBlue.LightOnPrimaryContainer,
                    ThemeColors.RoyalBlue.LightInversePrimary,
                )
            AccentTheme.FOREST_GREEN ->
                Quintuple(
                    ThemeColors.ForestGreen.LightPrimary,
                    ThemeColors.ForestGreen.LightOnPrimary,
                    ThemeColors.ForestGreen.LightPrimaryContainer,
                    ThemeColors.ForestGreen.LightOnPrimaryContainer,
                    ThemeColors.ForestGreen.LightInversePrimary,
                )
            AccentTheme.DARK_BROWN ->
                Quintuple(
                    ThemeColors.DarkBrown.LightPrimary,
                    ThemeColors.DarkBrown.LightOnPrimary,
                    ThemeColors.DarkBrown.LightPrimaryContainer,
                    ThemeColors.DarkBrown.LightOnPrimaryContainer,
                    ThemeColors.DarkBrown.LightInversePrimary,
                )
            AccentTheme.OCEAN_TEAL ->
                Quintuple(
                    ThemeColors.OceanTeal.LightPrimary,
                    ThemeColors.OceanTeal.LightOnPrimary,
                    ThemeColors.OceanTeal.LightPrimaryContainer,
                    ThemeColors.OceanTeal.LightOnPrimaryContainer,
                    ThemeColors.OceanTeal.LightInversePrimary,
                )
            AccentTheme.PURE_BLACK ->
                Quintuple(
                    ThemeColors.PureBlack.LightPrimary,
                    ThemeColors.PureBlack.LightOnPrimary,
                    ThemeColors.PureBlack.LightPrimaryContainer,
                    ThemeColors.PureBlack.LightOnPrimaryContainer,
                    ThemeColors.PureBlack.LightInversePrimary,
                )
            AccentTheme.AMETHYST ->
                Quintuple(
                    ThemeColors.Amethyst.LightPrimary,
                    ThemeColors.Amethyst.LightOnPrimary,
                    ThemeColors.Amethyst.LightPrimaryContainer,
                    ThemeColors.Amethyst.LightOnPrimaryContainer,
                    ThemeColors.Amethyst.LightInversePrimary,
                )
        }

    val (secondary, onSecondary, secondaryContainer, onSecondaryContainer) =
        when (accentTheme) {
            AccentTheme.MAROON ->
                Quadruple(
                    ThemeColors.Maroon.LightSecondary,
                    ThemeColors.Maroon.LightOnSecondary,
                    ThemeColors.Maroon.LightSecondaryContainer,
                    ThemeColors.Maroon.LightOnSecondaryContainer,
                )
            AccentTheme.ROYAL_BLUE ->
                Quadruple(
                    ThemeColors.RoyalBlue.LightSecondary,
                    ThemeColors.RoyalBlue.LightOnSecondary,
                    ThemeColors.RoyalBlue.LightSecondaryContainer,
                    ThemeColors.RoyalBlue.LightOnSecondaryContainer,
                )
            AccentTheme.FOREST_GREEN ->
                Quadruple(
                    ThemeColors.ForestGreen.LightSecondary,
                    ThemeColors.ForestGreen.LightOnSecondary,
                    ThemeColors.ForestGreen.LightSecondaryContainer,
                    ThemeColors.ForestGreen.LightOnSecondaryContainer,
                )
            AccentTheme.DARK_BROWN ->
                Quadruple(
                    ThemeColors.DarkBrown.LightSecondary,
                    ThemeColors.DarkBrown.LightOnSecondary,
                    ThemeColors.DarkBrown.LightSecondaryContainer,
                    ThemeColors.DarkBrown.LightOnSecondaryContainer,
                )
            AccentTheme.OCEAN_TEAL ->
                Quadruple(
                    ThemeColors.OceanTeal.LightSecondary,
                    ThemeColors.OceanTeal.LightOnSecondary,
                    ThemeColors.OceanTeal.LightSecondaryContainer,
                    ThemeColors.OceanTeal.LightOnSecondaryContainer,
                )
            AccentTheme.PURE_BLACK ->
                Quadruple(
                    ThemeColors.PureBlack.LightSecondary,
                    ThemeColors.PureBlack.LightOnSecondary,
                    ThemeColors.PureBlack.LightSecondaryContainer,
                    ThemeColors.PureBlack.LightOnSecondaryContainer,
                )
            AccentTheme.AMETHYST ->
                Quadruple(
                    ThemeColors.Amethyst.LightSecondary,
                    ThemeColors.Amethyst.LightOnSecondary,
                    ThemeColors.Amethyst.LightSecondaryContainer,
                    ThemeColors.Amethyst.LightOnSecondaryContainer,
                )
        }

    val (tertiary, onTertiary, tertiaryContainer, onTertiaryContainer) =
        when (accentTheme) {
            AccentTheme.MAROON ->
                Quadruple(
                    ThemeColors.Maroon.LightTertiary,
                    ThemeColors.Maroon.LightOnTertiary,
                    ThemeColors.Maroon.LightTertiaryContainer,
                    ThemeColors.Maroon.LightOnTertiaryContainer,
                )
            AccentTheme.ROYAL_BLUE ->
                Quadruple(
                    ThemeColors.RoyalBlue.LightTertiary,
                    ThemeColors.RoyalBlue.LightOnTertiary,
                    ThemeColors.RoyalBlue.LightTertiaryContainer,
                    ThemeColors.RoyalBlue.LightOnTertiaryContainer,
                )
            AccentTheme.FOREST_GREEN ->
                Quadruple(
                    ThemeColors.ForestGreen.LightTertiary,
                    ThemeColors.ForestGreen.LightOnTertiary,
                    ThemeColors.ForestGreen.LightTertiaryContainer,
                    ThemeColors.ForestGreen.LightOnTertiaryContainer,
                )
            AccentTheme.DARK_BROWN ->
                Quadruple(
                    ThemeColors.DarkBrown.LightTertiary,
                    ThemeColors.DarkBrown.LightOnTertiary,
                    ThemeColors.DarkBrown.LightTertiaryContainer,
                    ThemeColors.DarkBrown.LightOnTertiaryContainer,
                )
            AccentTheme.OCEAN_TEAL ->
                Quadruple(
                    ThemeColors.OceanTeal.LightTertiary,
                    ThemeColors.OceanTeal.LightOnTertiary,
                    ThemeColors.OceanTeal.LightTertiaryContainer,
                    ThemeColors.OceanTeal.LightOnTertiaryContainer,
                )
            AccentTheme.PURE_BLACK ->
                Quadruple(
                    ThemeColors.PureBlack.LightTertiary,
                    ThemeColors.PureBlack.LightOnTertiary,
                    ThemeColors.PureBlack.LightTertiaryContainer,
                    ThemeColors.PureBlack.LightOnTertiaryContainer,
                )
            AccentTheme.AMETHYST ->
                Quadruple(
                    ThemeColors.Amethyst.LightTertiary,
                    ThemeColors.Amethyst.LightOnTertiary,
                    ThemeColors.Amethyst.LightTertiaryContainer,
                    ThemeColors.Amethyst.LightOnTertiaryContainer,
                )
        }

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = invPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = ThemeColors.LightBackground,
        onBackground = ThemeColors.LightOnBackground,
        surface = ThemeColors.LightSurface,
        onSurface = ThemeColors.LightOnSurface,
        surfaceVariant = ThemeColors.LightSurfaceVariant,
        onSurfaceVariant = ThemeColors.LightOnSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = ThemeColors.LightInverseSurface,
        inverseOnSurface = ThemeColors.LightInverseOnSurface,
        error = ThemeColors.LightError,
        onError = ThemeColors.LightOnError,
        errorContainer = ThemeColors.LightErrorContainer,
        onErrorContainer = ThemeColors.LightOnErrorContainer,
        outline = ThemeColors.LightOutline,
        outlineVariant = ThemeColors.LightOutlineVariant,
        scrim = ThemeColors.Scrim,
        surfaceBright = ThemeColors.LightSurfaceBright,
        surfaceDim = ThemeColors.LightSurfaceDim,
        surfaceContainerLowest = ThemeColors.LightSurfaceContainerLowest,
        surfaceContainerLow = ThemeColors.LightSurfaceContainerLow,
        surfaceContainer = ThemeColors.LightSurfaceContainer,
        surfaceContainerHigh = ThemeColors.LightSurfaceContainerHigh,
        surfaceContainerHighest = ThemeColors.LightSurfaceContainerHighest,
    )
}

private fun buildDarkColorScheme(
    accentTheme: AccentTheme,
    isOledBlack: Boolean,
): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer, invPrimary) =
        when (accentTheme) {
            AccentTheme.MAROON ->
                Quintuple(
                    ThemeColors.Maroon.DarkPrimary,
                    ThemeColors.Maroon.DarkOnPrimary,
                    ThemeColors.Maroon.DarkPrimaryContainer,
                    ThemeColors.Maroon.DarkOnPrimaryContainer,
                    ThemeColors.Maroon.DarkInversePrimary,
                )
            AccentTheme.ROYAL_BLUE ->
                Quintuple(
                    ThemeColors.RoyalBlue.DarkPrimary,
                    ThemeColors.RoyalBlue.DarkOnPrimary,
                    ThemeColors.RoyalBlue.DarkPrimaryContainer,
                    ThemeColors.RoyalBlue.DarkOnPrimaryContainer,
                    ThemeColors.RoyalBlue.DarkInversePrimary,
                )
            AccentTheme.FOREST_GREEN ->
                Quintuple(
                    ThemeColors.ForestGreen.DarkPrimary,
                    ThemeColors.ForestGreen.DarkOnPrimary,
                    ThemeColors.ForestGreen.DarkPrimaryContainer,
                    ThemeColors.ForestGreen.DarkOnPrimaryContainer,
                    ThemeColors.ForestGreen.DarkInversePrimary,
                )
            AccentTheme.DARK_BROWN ->
                Quintuple(
                    ThemeColors.DarkBrown.DarkPrimary,
                    ThemeColors.DarkBrown.DarkOnPrimary,
                    ThemeColors.DarkBrown.DarkPrimaryContainer,
                    ThemeColors.DarkBrown.DarkOnPrimaryContainer,
                    ThemeColors.DarkBrown.DarkInversePrimary,
                )
            AccentTheme.OCEAN_TEAL ->
                Quintuple(
                    ThemeColors.OceanTeal.DarkPrimary,
                    ThemeColors.OceanTeal.DarkOnPrimary,
                    ThemeColors.OceanTeal.DarkPrimaryContainer,
                    ThemeColors.OceanTeal.DarkOnPrimaryContainer,
                    ThemeColors.OceanTeal.DarkInversePrimary,
                )
            AccentTheme.PURE_BLACK ->
                Quintuple(
                    ThemeColors.PureBlack.DarkPrimary,
                    ThemeColors.PureBlack.DarkOnPrimary,
                    ThemeColors.PureBlack.DarkPrimaryContainer,
                    ThemeColors.PureBlack.DarkOnPrimaryContainer,
                    ThemeColors.PureBlack.DarkInversePrimary,
                )
            AccentTheme.AMETHYST ->
                Quintuple(
                    ThemeColors.Amethyst.DarkPrimary,
                    ThemeColors.Amethyst.DarkOnPrimary,
                    ThemeColors.Amethyst.DarkPrimaryContainer,
                    ThemeColors.Amethyst.DarkOnPrimaryContainer,
                    ThemeColors.Amethyst.DarkInversePrimary,
                )
        }

    val (secondary, onSecondary, secondaryContainer, onSecondaryContainer) =
        when (accentTheme) {
            AccentTheme.MAROON ->
                Quadruple(
                    ThemeColors.Maroon.DarkSecondary,
                    ThemeColors.Maroon.DarkOnSecondary,
                    ThemeColors.Maroon.DarkSecondaryContainer,
                    ThemeColors.Maroon.DarkOnSecondaryContainer,
                )
            AccentTheme.ROYAL_BLUE ->
                Quadruple(
                    ThemeColors.RoyalBlue.DarkSecondary,
                    ThemeColors.RoyalBlue.DarkOnSecondary,
                    ThemeColors.RoyalBlue.DarkSecondaryContainer,
                    ThemeColors.RoyalBlue.DarkOnSecondaryContainer,
                )
            AccentTheme.FOREST_GREEN ->
                Quadruple(
                    ThemeColors.ForestGreen.DarkSecondary,
                    ThemeColors.ForestGreen.DarkOnSecondary,
                    ThemeColors.ForestGreen.DarkSecondaryContainer,
                    ThemeColors.ForestGreen.DarkOnSecondaryContainer,
                )
            AccentTheme.DARK_BROWN ->
                Quadruple(
                    ThemeColors.DarkBrown.DarkSecondary,
                    ThemeColors.DarkBrown.DarkOnSecondary,
                    ThemeColors.DarkBrown.DarkSecondaryContainer,
                    ThemeColors.DarkBrown.DarkOnSecondaryContainer,
                )
            AccentTheme.OCEAN_TEAL ->
                Quadruple(
                    ThemeColors.OceanTeal.DarkSecondary,
                    ThemeColors.OceanTeal.DarkOnSecondary,
                    ThemeColors.OceanTeal.DarkSecondaryContainer,
                    ThemeColors.OceanTeal.DarkOnSecondaryContainer,
                )
            AccentTheme.PURE_BLACK ->
                Quadruple(
                    ThemeColors.PureBlack.DarkSecondary,
                    ThemeColors.PureBlack.DarkOnSecondary,
                    ThemeColors.PureBlack.DarkSecondaryContainer,
                    ThemeColors.PureBlack.DarkOnSecondaryContainer,
                )
            AccentTheme.AMETHYST ->
                Quadruple(
                    ThemeColors.Amethyst.DarkSecondary,
                    ThemeColors.Amethyst.DarkOnSecondary,
                    ThemeColors.Amethyst.DarkSecondaryContainer,
                    ThemeColors.Amethyst.DarkOnSecondaryContainer,
                )
        }

    val (tertiary, onTertiary, tertiaryContainer, onTertiaryContainer) =
        when (accentTheme) {
            AccentTheme.MAROON ->
                Quadruple(
                    ThemeColors.Maroon.DarkTertiary,
                    ThemeColors.Maroon.DarkOnTertiary,
                    ThemeColors.Maroon.DarkTertiaryContainer,
                    ThemeColors.Maroon.DarkOnTertiaryContainer,
                )
            AccentTheme.ROYAL_BLUE ->
                Quadruple(
                    ThemeColors.RoyalBlue.DarkTertiary,
                    ThemeColors.RoyalBlue.DarkOnTertiary,
                    ThemeColors.RoyalBlue.DarkTertiaryContainer,
                    ThemeColors.RoyalBlue.DarkOnTertiaryContainer,
                )
            AccentTheme.FOREST_GREEN ->
                Quadruple(
                    ThemeColors.ForestGreen.DarkTertiary,
                    ThemeColors.ForestGreen.DarkOnTertiary,
                    ThemeColors.ForestGreen.DarkTertiaryContainer,
                    ThemeColors.ForestGreen.DarkOnTertiaryContainer,
                )
            AccentTheme.DARK_BROWN ->
                Quadruple(
                    ThemeColors.DarkBrown.DarkTertiary,
                    ThemeColors.DarkBrown.DarkOnTertiary,
                    ThemeColors.DarkBrown.DarkTertiaryContainer,
                    ThemeColors.DarkBrown.DarkOnTertiaryContainer,
                )
            AccentTheme.OCEAN_TEAL ->
                Quadruple(
                    ThemeColors.OceanTeal.DarkTertiary,
                    ThemeColors.OceanTeal.DarkOnTertiary,
                    ThemeColors.OceanTeal.DarkTertiaryContainer,
                    ThemeColors.OceanTeal.DarkOnTertiaryContainer,
                )
            AccentTheme.PURE_BLACK ->
                Quadruple(
                    ThemeColors.PureBlack.DarkTertiary,
                    ThemeColors.PureBlack.DarkOnTertiary,
                    ThemeColors.PureBlack.DarkTertiaryContainer,
                    ThemeColors.PureBlack.DarkOnTertiaryContainer,
                )
            AccentTheme.AMETHYST ->
                Quadruple(
                    ThemeColors.Amethyst.DarkTertiary,
                    ThemeColors.Amethyst.DarkOnTertiary,
                    ThemeColors.Amethyst.DarkTertiaryContainer,
                    ThemeColors.Amethyst.DarkOnTertiaryContainer,
                )
        }

    val surfaces =
        if (isOledBlack) {
            DarkThemeSurfaces(
                background = ThemeColors.OledBackground,
                surface = ThemeColors.OledSurface,
                surfaceVariant = ThemeColors.OledSurfaceVariant,
                onBackground = ThemeColors.OledOnBackground,
                onSurface = ThemeColors.OledOnSurface,
                onSurfaceVariant = ThemeColors.OledOnSurfaceVariant,
                outline = ThemeColors.OledOutline,
                outlineVariant = ThemeColors.OledOutlineVariant,
                surfaceContainerLowest = ThemeColors.OledSurfaceContainerLowest,
                surfaceContainerLow = ThemeColors.OledSurfaceContainerLow,
                surfaceContainer = ThemeColors.OledSurfaceContainer,
                surfaceContainerHigh = ThemeColors.OledSurfaceContainerHigh,
                surfaceContainerHighest = ThemeColors.OledSurfaceContainerHighest,
                surfaceBright = ThemeColors.OledSurfaceBright,
                surfaceDim = ThemeColors.OledSurfaceDim,
            )
        } else {
            // Unified Dark Mode Palette (#242424 background, #262626 surface, #3A3A3A outlines)
            DarkThemeSurfaces(
                background = ThemeColors.DarkBackground,
                surface = ThemeColors.DarkSurface,
                surfaceVariant = ThemeColors.DarkSurfaceVariant,
                onBackground = ThemeColors.DarkOnBackground,
                onSurface = ThemeColors.DarkOnSurface,
                onSurfaceVariant = ThemeColors.DarkOnSurfaceVariant,
                outline = ThemeColors.DarkOutline,
                outlineVariant = ThemeColors.DarkOutlineVariant,
                surfaceContainerLowest = ThemeColors.DarkSurfaceContainerLowest,
                surfaceContainerLow = ThemeColors.DarkSurfaceContainerLow,
                surfaceContainer = ThemeColors.DarkSurfaceContainer,
                surfaceContainerHigh = ThemeColors.DarkSurfaceContainerHigh,
                surfaceContainerHighest = ThemeColors.DarkSurfaceContainerHighest,
                surfaceBright = ThemeColors.DarkSurfaceBright,
                surfaceDim = ThemeColors.DarkSurfaceDim,
            )
        }

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = invPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = surfaces.background,
        onBackground = surfaces.onBackground,
        surface = surfaces.surface,
        onSurface = surfaces.onSurface,
        surfaceVariant = surfaces.surfaceVariant,
        onSurfaceVariant = surfaces.onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = ThemeColors.DarkInverseSurface,
        inverseOnSurface = ThemeColors.DarkInverseOnSurface,
        error = ThemeColors.DarkError,
        onError = ThemeColors.DarkOnError,
        errorContainer = ThemeColors.DarkErrorContainer,
        onErrorContainer = ThemeColors.DarkOnErrorContainer,
        outline = surfaces.outline,
        outlineVariant = surfaces.outlineVariant,
        scrim = ThemeColors.Scrim,
        surfaceBright = surfaces.surfaceBright,
        surfaceDim = surfaces.surfaceDim,
        surfaceContainerLowest = surfaces.surfaceContainerLowest,
        surfaceContainerLow = surfaces.surfaceContainerLow,
        surfaceContainer = surfaces.surfaceContainer,
        surfaceContainerHigh = surfaces.surfaceContainerHigh,
        surfaceContainerHighest = surfaces.surfaceContainerHighest,
    )
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)

private data class DarkThemeSurfaces(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
)
