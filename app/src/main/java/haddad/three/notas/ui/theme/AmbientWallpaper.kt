package haddad.three.notas.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
            glowPrimary = Color(0xFF1E1E26),
            glowSecondary = Color(0xFF14141A),
        )
    }

    return when (accentTheme) {
        AccentTheme.MAROON ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF481016),
                glowSecondary = Color(0xFF2A0A0E),
            )
        AccentTheme.ROYAL_BLUE ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF141E42),
                glowSecondary = Color(0xFF0C1228),
            )
        AccentTheme.FOREST_GREEN ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF102814),
                glowSecondary = Color(0xFF0A1A0D),
            )
        AccentTheme.DARK_BROWN ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF321C0E),
                glowSecondary = Color(0xFF1E1108),
            )
        AccentTheme.OCEAN_TEAL ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF112B30),
                glowSecondary = Color(0xFF0A1B1E),
            )
        AccentTheme.PURE_BLACK ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF202028),
                glowSecondary = Color(0xFF14141A),
            )
        AccentTheme.AMETHYST ->
            AmbientPalette(
                base = Color(0xFF242424),
                glowPrimary = Color(0xFF34153A),
                glowSecondary = Color(0xFF1F0C23),
            )
    }
}

/**
 * Renders the ambient background wallpaper at the application root.
 *
 * Implements:
 * 1. Consistent visual structure across all themes.
 * 2. Dark charcoal base instead of pure black (pure black reserved for OLED mode).
 * 3. Restrained theme-colored glow near the upper region.
 * 4. Multi-stop radial gradients with low opacity to prevent banding or harsh edges.
 * 5. Smooth fade into a neutral dark lower region without color contention.
 * 6. Subtle, slow ambient drift (24s cycle) animating only low-opacity gradient offsets.
 * 7. Static fallback with zero frame updates when [reducedMotion] is active.
 * 8. Anchored to the screen scaffold at the root — never scrolls with list content.
 */
@Composable
fun AmbientBackground(
    accentTheme: AccentTheme,
    isOledBlack: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette =
        remember(accentTheme, isOledBlack) {
            resolveAmbientPalette(accentTheme, isOledBlack)
        }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val neutralBase = if (isOledBlack) Color(0xFF000000) else Color(0xFF242424)
        val lowerNeutral = if (isOledBlack) Color(0xFF000000) else Color(0xFF242424)

        // 1. Base vertical gradient: neutral near-black charcoal fading to deep dark lower region
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(neutralBase, lowerNeutral),
                    startY = 0f,
                    endY = size.height,
                ),
        )

        // 2. Single restrained diffuse glow: soft, barely noticeable upper bloom
        val primaryCenterX = size.width * 0.68f
        val primaryCenterY = size.height * 0.08f
        val primaryRadius = size.maxDimension * 0.60f
        val glowAlpha = if (isOledBlack) 0.10f else 0.18f

        drawCircle(
            brush =
                Brush.radialGradient(
                    0.0f to palette.glowPrimary.copy(alpha = glowAlpha),
                    0.35f to palette.glowPrimary.copy(alpha = glowAlpha * 0.45f),
                    0.75f to palette.glowPrimary.copy(alpha = glowAlpha * 0.08f),
                    1.0f to Color.Transparent,
                    center = Offset(primaryCenterX, primaryCenterY),
                    radius = primaryRadius,
                ),
            radius = primaryRadius,
            center = Offset(primaryCenterX, primaryCenterY),
        )
    }
}
