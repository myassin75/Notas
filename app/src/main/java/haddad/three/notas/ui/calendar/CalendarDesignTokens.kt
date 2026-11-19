package haddad.three.notas.ui.calendar

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the Calendar screen and its subcomponents.
 *
 * Aligned with the application's Material 3 visual identity and the
 * reference Calendar design.
 */
object CalendarDesignTokens {
    // Brand & Primary Accents
    val PrimaryPurple = Color(0xFF800000)
    val SoftLavender = Color(0xFFFBE8EA)
    val DarkPurpleSelected = Color(0xFF5A0000)

    val DynamicPrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
    val DynamicPrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val DynamicOnPrimary: Color @Composable get() = MaterialTheme.colorScheme.onPrimary

    // Background & Card Surfaces
    val ScreenBgLight = Color(0xFFF8F7FA)
    val ScreenBgDark = Color(0xFF181818)
    val CardBgLight = Color(0xFFF2F2F4)
    val CardBgDark = Color(0xFF1F1F22)
    val CardBorderLight = Color(0xFFE5E5EA)
    val CardBorderDark = Color(0xFF2D2D30)

    // Typography Colors
    val TextPrimaryLight = Color(0xFF1E1B26)
    val TextPrimaryDark = Color(0xFFF2F1F8)
    val TextSecondaryLight = Color(0xFF7A7786)
    val TextSecondaryDark = Color(0xFFB4B3C5)
    val TextMutedLight = Color(0xFF8E8A98)
    val TextMutedDark = Color(0xFF6B6A78)
    val OutOfMonthTextLight = Color(0xFFCBC8D4)
    val OutOfMonthTextDark = Color(0xFF555462)

    // Event Category & Dot Indicators
    val DotGreen = Color(0xFF34A853)
    val DotOrange = Color(0xFFFA7B17)
    val DotPurple = Color(0xFF800000)
    val DotBlue = Color(0xFF4285F4)
    val DotWhite = Color(0xFFFFFFFF)

    // Category Box Tints
    val CategoryPurpleBg = Color(0xFFFBE8EA)
    val CategoryPurpleIcon = Color(0xFF800000)
    val CategoryGreenBg = Color(0xFFEBF5F0)
    val CategoryGreenIcon = Color(0xFF2E8B57)
    val CategoryBlueBg = Color(0xFFEBF2FD)
    val CategoryBlueIcon = Color(0xFF3C78D8)

    // Birthday Section Theme
    val BirthdayBoxBg = Color(0xFFFDF0F5)
    val BirthdayIconTint = Color(0xFFD84C7B)

    // Component Shapes
    val CardShape = RoundedCornerShape(28.dp)
    val ItemCardShape = RoundedCornerShape(24.dp)
    val IconBoxShape = RoundedCornerShape(14.dp)
    val SelectedDayShape = RoundedCornerShape(12.dp)
    val PillShape = CircleShape
    val MenuButtonShape = RoundedCornerShape(12.dp)

    // Dimensions
    val ScreenHorizontalPadding = 16.dp
    val ContentVerticalSpacing = 16.dp
    val DayCellMinHeight = 44.dp
    val DayCellMinWidth = 36.dp
    val SelectedIndicatorWidth = 36.dp
    val SelectedIndicatorHeight = 42.dp
    val SelectedIndicatorSize = 34.dp
    val DotIndicatorSize = 4.5.dp
    val IconBoxSize = 42.dp

    // Theme Helpers
    @Composable
    fun resolveScreenBg(isDark: Boolean = false): Color = if (isDark) Color.Transparent else MaterialTheme.colorScheme.background

    @Composable
    fun resolveCardBg(isDark: Boolean = false): Color =
        haddad.three.notas.ui.theme
            .glassContainerColor(haddad.three.notas.ui.theme.GlassVariant.StandardCard, isDark)

    @Composable
    fun resolveCardBorder(isDark: Boolean = false): Color = if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    @Composable
    fun resolveTextPrimary(isDark: Boolean = false): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    fun resolveTextSecondary(isDark: Boolean = false): Color = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun resolveTextMuted(isDark: Boolean = false): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    @Composable
    fun resolveOutOfMonthText(isDark: Boolean = false): Color = MaterialTheme.colorScheme.outline

    val WeekendColorLight = Color(0xFFD32F2F)
    val WeekendColorDark = Color(0xFFFF5252)

    @Composable
    fun resolveWeekendColor(isDark: Boolean = false): Color = if (isDark) WeekendColorDark else WeekendColorLight

    /**
     * Cleans icon key strings (e.g. "symbol:school", "sym:school", "symbol_filled:school")
     * down to the raw Material Symbols ligature name.
     */
    fun cleanIconName(raw: String): String =
        raw
            .removePrefix("symbol_filled:")
            .removePrefix("symbol_outlined:")
            .removePrefix("symbol:")
            .removePrefix("sym:")
}
