package haddad.three.notas.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Functional variants for the frosted-glass design system:
 * - [StandardCard] (alias [Standard]): For primary content cards and grouped settings.
 * - [SelectedCard]: For selected content cards with accent-colored container and border.
 * - [BottomNav] (alias [Subtle]): For bottom navigation bar, top bars, and low-emphasis containers.
 * - [ModalSheet] (alias [Modal]): For bottom sheets, using higher opacity (0.94f+) for maximum focus and legibility.
 * - [Dialog]: For dialogs and confirmation modals (0.96f+ opacity with prominent border).
 * - [Input]: For text input fields, search bars, and option boxes.
 * - [Chip]: For filter chips, tag capsules, and action buttons.
 * - [SolidReadability]: For text-heavy, high-focus areas (e.g. Note Editor body) where transparency is undesirable.
 */
enum class GlassVariant {
    StandardCard,
    SelectedCard,
    BottomNav,
    ModalSheet,
    Dialog,
    Input,
    Chip,
    SolidReadability;

    companion object {
        // Aliases for backward compatibility
        val Standard get() = StandardCard
        val Subtle get() = BottomNav
        val Modal get() = ModalSheet
    }
}

/**
 * Resolves the theme-aware container color for the given [GlassVariant].
 *
 * In Dark Mode: resolves to calibrated translucent charcoal containers over the ambient background.
 * In Light Mode: resolves to clean, solid Material 3 surfaces, preserving Light Mode appearance 100%.
 */
@Composable
fun glassContainerColor(
    variant: GlassVariant = GlassVariant.StandardCard,
    isDark: Boolean = LocalIsDark.current,
): Color {
    if (!isDark) {
        return when (variant) {
            GlassVariant.StandardCard -> MaterialTheme.colorScheme.surface
            GlassVariant.SelectedCard -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            GlassVariant.BottomNav -> MaterialTheme.colorScheme.surface
            GlassVariant.ModalSheet -> MaterialTheme.colorScheme.surfaceContainerHigh
            GlassVariant.Dialog -> MaterialTheme.colorScheme.surfaceContainerHigh
            GlassVariant.Input -> MaterialTheme.colorScheme.surfaceContainerLow
            GlassVariant.Chip -> MaterialTheme.colorScheme.surfaceContainer
            GlassVariant.SolidReadability -> MaterialTheme.colorScheme.surface
        }
    }

    val themeState = LocalThemeState.current
    val isOledBlack = themeState.blackThemeActive(isDark) || themeState.accentTheme == haddad.three.notas.data.AccentTheme.PURE_BLACK

    return when (variant) {
        GlassVariant.StandardCard ->
            if (isOledBlack) {
                Color(0xFF141418)
            } else {
                Color(0xFF262626)
            }
        GlassVariant.SelectedCard ->
            if (isOledBlack) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            }
        GlassVariant.BottomNav ->
            if (isOledBlack) {
                Color(0xFF101014).copy(alpha = 0.94f)
            } else {
                Color(0xFF262626)
            }
        GlassVariant.ModalSheet ->
            if (isOledBlack) {
                Color(0xFF16161C).copy(alpha = 0.98f)
            } else {
                Color(0xFF262626)
            }
        GlassVariant.Dialog ->
            if (isOledBlack) {
                Color(0xFF16161C).copy(alpha = 0.98f)
            } else {
                Color(0xFF262626)
            }
        GlassVariant.Input ->
            if (isOledBlack) {
                Color(0xFF101014)
            } else {
                Color(0xFF242424)
            }
        GlassVariant.Chip ->
            if (isOledBlack) {
                Color(0xFF16161C)
            } else {
                Color(0xFF3A3A3A)
            }
        GlassVariant.SolidReadability ->
            if (isOledBlack) {
                Color(0xFF101014)
            } else {
                Color(0xFF262626)
            }
    }
}

/**
 * Resolves the theme-aware border stroke for a glass surface.
 *
 * In Dark Mode: subtle 1.dp outlineVariant border.
 * In Light Mode: standard outline or null depending on variant.
 */
@Composable
fun glassBorderStroke(
    variant: GlassVariant = GlassVariant.StandardCard,
    isDark: Boolean = LocalIsDark.current,
): BorderStroke? {
    if (!isDark) {
        return when (variant) {
            GlassVariant.StandardCard, GlassVariant.SolidReadability ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            GlassVariant.SelectedCard ->
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            GlassVariant.Input ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
            GlassVariant.Chip ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
            GlassVariant.BottomNav, GlassVariant.ModalSheet, GlassVariant.Dialog -> null
        }
    }

    val themeState = LocalThemeState.current
    val isOledBlack = themeState.blackThemeActive(isDark) || themeState.accentTheme == haddad.three.notas.data.AccentTheme.PURE_BLACK

    return when (variant) {
        GlassVariant.StandardCard ->
            if (isOledBlack) BorderStroke(1.dp, Color(0xFF282832).copy(alpha = 0.60f)) else BorderStroke(1.dp, Color(0xFF3A3A3A))
        GlassVariant.SelectedCard ->
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        GlassVariant.BottomNav ->
            if (isOledBlack) BorderStroke(0.5.dp, Color(0xFF282834).copy(alpha = 0.45f)) else BorderStroke(0.5.dp, Color(0xFF3A3A3A))
        GlassVariant.ModalSheet ->
            if (isOledBlack) BorderStroke(1.dp, Color(0xFF282832).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color(0xFF3A3A3A))
        GlassVariant.Dialog ->
            if (isOledBlack) BorderStroke(1.dp, Color(0xFF333340).copy(alpha = 0.60f)) else BorderStroke(1.dp, Color(0xFF3A3A3A))
        GlassVariant.Input ->
            if (isOledBlack) BorderStroke(1.dp, Color(0xFF282834).copy(alpha = 0.45f)) else BorderStroke(1.dp, Color(0xFF3A3A3A))
        GlassVariant.Chip ->
            if (isOledBlack) BorderStroke(1.dp, Color(0xFF2C2C36).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color(0xFF3A3A3A))
        GlassVariant.SolidReadability ->
            if (isOledBlack) BorderStroke(1.dp, Color(0xFF282834).copy(alpha = 0.45f)) else BorderStroke(1.dp, Color(0xFF3A3A3A))
    }
}

/**
 * Resolves the theme-aware content color for the given [GlassVariant].
 */
@Composable
fun glassContentColor(
    variant: GlassVariant = GlassVariant.StandardCard,
    isDark: Boolean = LocalIsDark.current,
): Color {
    return when (variant) {
        GlassVariant.SelectedCard ->
            if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
        GlassVariant.Chip ->
            if (isDark) Color(0xFFE8E7F0) else MaterialTheme.colorScheme.onSurface
        else ->
            if (isDark) Color(0xFFE8E7F0) else MaterialTheme.colorScheme.onSurface
    }
}

/**
 * Reusable frosted-glass surface component.
 *
 * In Dark Mode:
 * - Renders a balanced, translucent surface with subtle accent-aware tone.
 * - Differentiates surfaces through intentional [variant] hierarchies.
 *
 * In Light Mode:
 * - Seamlessly renders the clean, solid Material 3 surface with zero regressions.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.StandardCard,
    shape: Shape = RoundedCornerShape(22.dp),
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val isDark = LocalIsDark.current
    val containerColor = glassContainerColor(variant = variant, isDark = isDark)
    val resolvedBorder = border ?: glassBorderStroke(variant = variant, isDark = isDark)

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = resolvedBorder,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        content()
    }
}

/**
 * Specialized Glass composable for standard cards.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        variant = GlassVariant.StandardCard,
        shape = shape,
        border = border,
        content = content,
    )
}

/**
 * Specialized Glass composable for selected cards.
 */
@Composable
fun GlassSelectedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        variant = GlassVariant.SelectedCard,
        shape = shape,
        content = content,
    )
}

/**
 * Specialized Glass composable for chips and pill capsules.
 */
@Composable
fun GlassChip(
    selected: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val variant = if (selected) GlassVariant.SelectedCard else GlassVariant.Chip
    GlassSurface(
        modifier = modifier,
        variant = variant,
        shape = shape,
        content = content,
    )
}
