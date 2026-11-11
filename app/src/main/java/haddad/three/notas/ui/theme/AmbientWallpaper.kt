package haddad.three.notas.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import haddad.three.notas.data.AccentTheme

/**
 * Ambient wallpaper color definition for an accent theme.
 *
 * Combines a deep base color with two soft, diffuse ambient glows:
 * - [base]: Deep near-black background
 * - [glowPrimary]: Primary diffuse bloom with accent undertone
 * - [glowSecondary]: Secondary diffuse bloom providing ambient depth
 */
data class AmbientPalette(
    val base: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
)

/**
 * Resolves the deterministic, centralized [AmbientPalette] for the given [AccentTheme].
 *
 * Supports all 7 theme identities:
 * - Maroon: deep charcoal base with subtle burgundy bloom
 * - Royal Blue: deep midnight base with restrained cool-blue illumination
 * - Forest Green: near-black charcoal with muted forest-green glow
 * - Dark Brown: warm charcoal base with subtle bronze-brown glow
 * - Ocean Teal: deep neutral base with restrained teal illumination
 * - Pure Black: true black base with subtle neutral charcoal lighting
 * - Amethyst: deep charcoal base with muted plum-colored glow
 *
 * When [isOledBlack] is active, the base is pure true black (#000000) with
 * restrained neutral charcoal blooms for OLED battery savings and true black depth.
 */
fun resolveAmbientPalette(
    accentTheme: AccentTheme,
    isOledBlack: Boolean = false,
): AmbientPalette {
    if (isOledBlack) {
        return AmbientPalette(
            base = Color(0xFF000000),
            glowPrimary = Color(0xFF000000),
            glowSecondary = Color(0xFF000000),
        )
    }

    return AmbientPalette(
        base = Color(0xFF181818),
        glowPrimary = Color(0xFF181818),
        glowSecondary = Color(0xFF181818),
    )
}

/**
 * Renders the ambient background wallpaper at the application root.
 * In Dark Mode: solid uniform #181818 background (or pure #000000 for OLED black).
 */
@Composable
fun AmbientBackground(
    accentTheme: AccentTheme,
    isOledBlack: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isOledBlack) Color(0xFF000000) else ThemeColors.DarkBackground
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
    )
}
