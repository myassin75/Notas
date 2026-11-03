package haddad.three.notas.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography()

private val MaterialDefaultTypography = Typography()

/** Material default type scale with a user-supplied font (ObtainX parity). */
fun customFontTypography(fontFamily: FontFamily): Typography = MaterialDefaultTypography.withFontFamilyOnly(fontFamily)

/**
 * ObtainX parity: swap only [fontFamily] on each role, keep sizes/line heights from [this].
 * Call on [Typography] defaults, not a custom explicit scale.
 */
fun Typography.withFontFamilyOnly(fontFamily: FontFamily): Typography =
    copy(
        displayLarge = displayLarge.withFontFamilyOnly(fontFamily),
        displayMedium = displayMedium.withFontFamilyOnly(fontFamily),
        displaySmall = displaySmall.withFontFamilyOnly(fontFamily),
        headlineLarge = headlineLarge.withFontFamilyOnly(fontFamily),
        headlineMedium = headlineMedium.withFontFamilyOnly(fontFamily),
        headlineSmall = headlineSmall.withFontFamilyOnly(fontFamily),
        titleLarge = titleLarge.withFontFamilyOnly(fontFamily),
        titleMedium = titleMedium.withFontFamilyOnly(fontFamily),
        titleSmall = titleSmall.withFontFamilyOnly(fontFamily),
        bodyLarge = bodyLarge.withFontFamilyOnly(fontFamily),
        bodyMedium = bodyMedium.withFontFamilyOnly(fontFamily),
        bodySmall = bodySmall.withFontFamilyOnly(fontFamily),
        labelLarge = labelLarge.withFontFamilyOnly(fontFamily),
        labelMedium = labelMedium.withFontFamilyOnly(fontFamily),
        labelSmall = labelSmall.withFontFamilyOnly(fontFamily),
        displayLargeEmphasized = displayLargeEmphasized.withFontFamilyOnly(fontFamily),
        displayMediumEmphasized = displayMediumEmphasized.withFontFamilyOnly(fontFamily),
        displaySmallEmphasized = displaySmallEmphasized.withFontFamilyOnly(fontFamily),
        headlineLargeEmphasized = headlineLargeEmphasized.withFontFamilyOnly(fontFamily),
        headlineMediumEmphasized = headlineMediumEmphasized.withFontFamilyOnly(fontFamily),
        headlineSmallEmphasized = headlineSmallEmphasized.withFontFamilyOnly(fontFamily),
        titleLargeEmphasized = titleLargeEmphasized.withFontFamilyOnly(fontFamily),
        titleMediumEmphasized = titleMediumEmphasized.withFontFamilyOnly(fontFamily),
        titleSmallEmphasized = titleSmallEmphasized.withFontFamilyOnly(fontFamily),
        bodyLargeEmphasized = bodyLargeEmphasized.withFontFamilyOnly(fontFamily),
        bodyMediumEmphasized = bodyMediumEmphasized.withFontFamilyOnly(fontFamily),
        bodySmallEmphasized = bodySmallEmphasized.withFontFamilyOnly(fontFamily),
        labelLargeEmphasized = labelLargeEmphasized.withFontFamilyOnly(fontFamily),
        labelMediumEmphasized = labelMediumEmphasized.withFontFamilyOnly(fontFamily),
        labelSmallEmphasized = labelSmallEmphasized.withFontFamilyOnly(fontFamily),
    )

private fun TextStyle.withFontFamilyOnly(fontFamily: FontFamily): TextStyle = copy(fontFamily = fontFamily)

/**
 * Notas Typography Scale matched to Figma references.
 *
 * Inter font family is specified in the design. When font assets are not bundled in res/font,
 * system default sans-serif is used with identical font weights, sizes, and line heights.
 */
object NotasTypography {
    val DisplayTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    )

    val ScreenTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    )

    val ModalTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    )

    val SectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    )

    val SectionHeader = SectionTitle

    val CardHeader = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    val Button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    val BodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val BodyMedium13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val Label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val Helper = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val Caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    val CaptionMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    val TabLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )
}


