package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val PrimaryPurple = Color(0xFF734B8E)
    val SoftLavender = Color(0xFFF3EAF6)
    val DarkPurpleSelected = Color(0xFF5D2E77)

    // Background & Card Surfaces
    val ScreenBgLight = Color(0xFFF8F7FA)
    val ScreenBgDark = Color(0xFF131318)
    val CardBgLight = Color(0xFFFFFFFF)
    val CardBgDark = Color(0xFF1E1E26)
    val CardBorderLight = Color(0xFFEFEFF3)
    val CardBorderDark = Color(0xFF2C2C38)

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
    val DotPurple = Color(0xFF734B8E)
    val DotBlue = Color(0xFF4285F4)
    val DotWhite = Color(0xFFFFFFFF)

    // Category Box Tints
    val CategoryPurpleBg = Color(0xFFF3EAF6)
    val CategoryPurpleIcon = Color(0xFF734B8E)
    val CategoryGreenBg = Color(0xFFEBF5F0)
    val CategoryGreenIcon = Color(0xFF2E8B57)
    val CategoryBlueBg = Color(0xFFEBF2FD)
    val CategoryBlueIcon = Color(0xFF3C78D8)

    // Birthday Section Theme
    val BirthdayBoxBg = Color(0xFFFDF0F5)
    val BirthdayIconTint = Color(0xFFD84C7B)

    // Component Shapes
    val CardShape = RoundedCornerShape(22.dp)
    val ItemCardShape = RoundedCornerShape(18.dp)
    val IconBoxShape = RoundedCornerShape(12.dp)
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
    fun resolveScreenBg(isDark: Boolean): Color = if (isDark) ScreenBgDark else ScreenBgLight

    fun resolveCardBg(isDark: Boolean): Color = if (isDark) CardBgDark else CardBgLight

    fun resolveCardBorder(isDark: Boolean): Color = if (isDark) CardBorderDark else CardBorderLight

    fun resolveTextPrimary(isDark: Boolean): Color = if (isDark) TextPrimaryDark else TextPrimaryLight

    fun resolveTextSecondary(isDark: Boolean): Color = if (isDark) TextSecondaryDark else TextSecondaryLight

    fun resolveTextMuted(isDark: Boolean): Color = if (isDark) TextMutedDark else TextMutedLight

    fun resolveOutOfMonthText(isDark: Boolean): Color = if (isDark) OutOfMonthTextDark else OutOfMonthTextLight

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
