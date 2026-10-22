package three.haddad.smartnotas.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NUVORA Color System Tokens.
 *
 * Defines the seven selectable accent color seeds and their contrast-verified Light and Dark
 * semantic role colors, as well as the shared neutral backgrounds and surfaces.
 */
object ThemeColors {
    // -----------------------------------------------------------------------------------------
    // Accent Seeds (used for identity, swatch previews, and brand references)
    // -----------------------------------------------------------------------------------------
    val MaroonSeed = Color(0xFF800000)
    val RoyalBlueSeed = Color(0xFF04147D)
    val ForestGreenSeed = Color(0xFF047D06)
    val DarkBrownSeed = Color(0xFF4D2002)
    val OceanTealSeed = Color(0xFF29A4AB)
    val PureBlackSeed = Color(0xFF000000)
    val AmethystSeed = Color(0xFF98549C)

    // -----------------------------------------------------------------------------------------
    // Shared Neutral Tokens — Light
    // -----------------------------------------------------------------------------------------
    val LightBackground = Color(0xFFF7F7F9)
    val LightOnBackground = Color(0xFF1B1B20)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF1B1B20)
    val LightSurfaceVariant = Color(0xFFF0EFF4)
    val LightOnSurfaceVariant = Color(0xFF6F6C7B)
    val LightOutline = Color(0xFFCBC8D6)
    val LightOutlineVariant = Color(0xFFE6E4ED)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFF9F8FC)
    val LightSurfaceContainer = Color(0xFFF2F1F6)
    val LightSurfaceContainerHigh = Color(0xFFECEBF1)
    val LightSurfaceContainerHighest = Color(0xFFE5E4EB)
    val LightSurfaceBright = Color(0xFFFFFFFF)
    val LightSurfaceDim = Color(0xFFDEDDE4)
    val LightInverseSurface = Color(0xFF303036)
    val LightInverseOnSurface = Color(0xFFF3F2F8)

    // -----------------------------------------------------------------------------------------
    // Shared Neutral Tokens — Dark (Charcoal)
    // -----------------------------------------------------------------------------------------
    val DarkBackground = Color(0xFF121216)
    val DarkOnBackground = Color(0xFFE4E3EC)
    val DarkSurface = Color(0xFF191920)
    val DarkOnSurface = Color(0xFFE4E3EC)
    val DarkSurfaceVariant = Color(0xFF262630)
    val DarkOnSurfaceVariant = Color(0xFFA6A4B4)
    val DarkOutline = Color(0xFF4A4858)
    val DarkOutlineVariant = Color(0xFF2E2C38)
    val DarkSurfaceContainerLowest = Color(0xFF0E0E12)
    val DarkSurfaceContainerLow = Color(0xFF1C1C23)
    val DarkSurfaceContainer = Color(0xFF22222B)
    val DarkSurfaceContainerHigh = Color(0xFF292934)
    val DarkSurfaceContainerHighest = Color(0xFF31313E)
    val DarkSurfaceBright = Color(0xFF3A3A46)
    val DarkSurfaceDim = Color(0xFF15151B)
    val DarkInverseSurface = Color(0xFFE4E3EC)
    val DarkInverseOnSurface = Color(0xFF191920)

    // -----------------------------------------------------------------------------------------
    // Shared Neutral Tokens — Pure Black / OLED Dark
    // -----------------------------------------------------------------------------------------
    val OledBackground = Color(0xFF000000)
    val OledOnBackground = Color(0xFFEDEDF4)
    val OledSurface = Color(0xFF0F0F12)
    val OledOnSurface = Color(0xFFEDEDF4)
    val OledSurfaceVariant = Color(0xFF1C1C22)
    val OledOnSurfaceVariant = Color(0xFFA8A6B5)
    val OledOutline = Color(0xFF444252)
    val OledOutlineVariant = Color(0xFF282732)
    val OledSurfaceContainerLowest = Color(0xFF070709)
    val OledSurfaceContainerLow = Color(0xFF15151A)
    val OledSurfaceContainer = Color(0xFF1C1C22)
    val OledSurfaceContainerHigh = Color(0xFF23232A)
    val OledSurfaceContainerHighest = Color(0xFF2C2C34)
    val OledSurfaceBright = Color(0xFF353540)
    val OledSurfaceDim = Color(0xFF0B0B0E)

    // -----------------------------------------------------------------------------------------
    // Shared Semantic Status Tokens
    // -----------------------------------------------------------------------------------------
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)

    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val Scrim = Color(0xFF000000)

    // -----------------------------------------------------------------------------------------
    // 1. Maroon Palette
    // -----------------------------------------------------------------------------------------
    object Maroon {
        val LightPrimary = Color(0xFF800000)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFFBE8EA)
        val LightOnPrimaryContainer = Color(0xFF450000)
        val LightSecondary = Color(0xFF755054)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFF5DEE1)
        val LightOnSecondaryContainer = Color(0xFF32171A)
        val LightTertiary = Color(0xFF795634)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFF8E7D8)
        val LightOnTertiaryContainer = Color(0xFF381F04)
        val LightInversePrimary = Color(0xFFFFB3B8)

        val DarkPrimary = Color(0xFFFFB3B8)
        val DarkOnPrimary = Color(0xFF4D0005)
        val DarkPrimaryContainer = Color(0xFF670008)
        val DarkOnPrimaryContainer = Color(0xFFFFD9DC)
        val DarkSecondary = Color(0xFFE2BDC0)
        val DarkOnSecondary = Color(0xFF42282C)
        val DarkSecondaryContainer = Color(0xFF5A3D41)
        val DarkOnSecondaryContainer = Color(0xFFFFD9DC)
        val DarkTertiary = Color(0xFFE6BE9C)
        val DarkOnTertiary = Color(0xFF442A0F)
        val DarkTertiaryContainer = Color(0xFF5D3F22)
        val DarkOnTertiaryContainer = Color(0xFFFFDDBF)
        val DarkInversePrimary = Color(0xFF800000)

        // Primary-aware dark background and stepped surfaces (warm burgundy undertone)
        val DarkBackground = Color(0xFF141011)
        val DarkSurface = Color(0xFF1B1617)
        val DarkSurfaceVariant = Color(0xFF282023)
        val DarkSurfaceContainerLowest = Color(0xFF0F0C0D)
        val DarkSurfaceContainerLow = Color(0xFF1F1A1B)
        val DarkSurfaceContainer = Color(0xFF241E20)
        val DarkSurfaceContainerHigh = Color(0xFF2B2426)
        val DarkSurfaceContainerHighest = Color(0xFF332B2E)
        val DarkSurfaceBright = Color(0xFF3D3337)
        val DarkSurfaceDim = Color(0xFF161213)
        val DarkOutline = Color(0xFF554448)
        val DarkOutlineVariant = Color(0xFF352A2D)
        val DarkOnBackground = Color(0xFFEDE0E2)
        val DarkOnSurface = Color(0xFFEDE0E2)
        val DarkOnSurfaceVariant = Color(0xFFAFA3A6)
    }

    // -----------------------------------------------------------------------------------------
    // 2. Royal Blue Palette
    // -----------------------------------------------------------------------------------------
    object RoyalBlue {
        val LightPrimary = Color(0xFF14247E)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFE8EDFB)
        val LightOnPrimaryContainer = Color(0xFF051254)
        val LightSecondary = Color(0xFF455177)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFE0E5F5)
        val LightOnSecondaryContainer = Color(0xFF1C274B)
        val LightTertiary = Color(0xFF3B5C77)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFDCEAF5)
        val LightOnTertiaryContainer = Color(0xFF13324C)
        val LightInversePrimary = Color(0xFFAEC4FF)

        val DarkPrimary = Color(0xFFAEC4FF)
        val DarkOnPrimary = Color(0xFF00194A)
        val DarkPrimaryContainer = Color(0xFF0D2570)
        val DarkOnPrimaryContainer = Color(0xFFD4E1FF)
        val DarkSecondary = Color(0xFFBCC6E2)
        val DarkOnSecondary = Color(0xFF28324C)
        val DarkSecondaryContainer = Color(0xFF3E4864)
        val DarkOnSecondaryContainer = Color(0xFFDCE2FA)
        val DarkTertiary = Color(0xFFA8C9E6)
        val DarkOnTertiary = Color(0xFF1E384F)
        val DarkTertiaryContainer = Color(0xFF344F67)
        val DarkOnTertiaryContainer = Color(0xFFCCE4FA)
        val DarkInversePrimary = Color(0xFF14247E)

        // Primary-aware dark background and stepped surfaces (cool blue undertone)
        val DarkBackground = Color(0xFF101218)
        val DarkSurface = Color(0xFF161922)
        val DarkSurfaceVariant = Color(0xFF222634)
        val DarkSurfaceContainerLowest = Color(0xFF0C0E13)
        val DarkSurfaceContainerLow = Color(0xFF1A1D27)
        val DarkSurfaceContainer = Color(0xFF20232F)
        val DarkSurfaceContainerHigh = Color(0xFF262B39)
        val DarkSurfaceContainerHighest = Color(0xFF2E3344)
        val DarkSurfaceBright = Color(0xFF383E51)
        val DarkSurfaceDim = Color(0xFF12141C)
        val DarkOutline = Color(0xFF484E66)
        val DarkOutlineVariant = Color(0xFF2D3243)
        val DarkOnBackground = Color(0xFFE2E4ED)
        val DarkOnSurface = Color(0xFFE2E4ED)
        val DarkOnSurfaceVariant = Color(0xFFA3A7B6)
    }

    // -----------------------------------------------------------------------------------------
    // 3. Forest Green Palette
    // -----------------------------------------------------------------------------------------
    object ForestGreen {
        val LightPrimary = Color(0xFF036905)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFE4F6E5)
        val LightOnPrimaryContainer = Color(0xFF013B03)
        val LightSecondary = Color(0xFF436544)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFE0F1E1)
        val LightOnSecondaryContainer = Color(0xFF1E3D20)
        val LightTertiary = Color(0xFF3A6658)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFDCF2E9)
        val LightOnTertiaryContainer = Color(0xFF143B2F)
        val LightInversePrimary = Color(0xFF75DE78)

        val DarkPrimary = Color(0xFF75DE78)
        val DarkOnPrimary = Color(0xFF003A03)
        val DarkPrimaryContainer = Color(0xFF015504)
        val DarkOnPrimaryContainer = Color(0xFFA6F6A9)
        val DarkSecondary = Color(0xFFB6D3B7)
        val DarkOnSecondary = Color(0xFF263E27)
        val DarkSecondaryContainer = Color(0xFF3B553C)
        val DarkOnSecondaryContainer = Color(0xFFD5F0D6)
        val DarkTertiary = Color(0xFFA5D2C2)
        val DarkOnTertiary = Color(0xFF1E3F34)
        val DarkTertiaryContainer = Color(0xFF33554A)
        val DarkOnTertiaryContainer = Color(0xFFC7EFE0)
        val DarkInversePrimary = Color(0xFF036905)

        // Primary-aware dark background and stepped surfaces (restrained green undertone)
        val DarkBackground = Color(0xFF101411)
        val DarkSurface = Color(0xFF161B17)
        val DarkSurfaceVariant = Color(0xFF212822)
        val DarkSurfaceContainerLowest = Color(0xFF0C0F0D)
        val DarkSurfaceContainerLow = Color(0xFF1A201B)
        val DarkSurfaceContainer = Color(0xFF202621)
        val DarkSurfaceContainerHigh = Color(0xFF272F29)
        val DarkSurfaceContainerHighest = Color(0xFF2E3830)
        val DarkSurfaceBright = Color(0xFF38433B)
        val DarkSurfaceDim = Color(0xFF121613)
        val DarkOutline = Color(0xFF475449)
        val DarkOutlineVariant = Color(0xFF2C352E)
        val DarkOnBackground = Color(0xFFE1E5E2)
        val DarkOnSurface = Color(0xFFE1E5E2)
        val DarkOnSurfaceVariant = Color(0xFFA2A8A3)
    }

    // -----------------------------------------------------------------------------------------
    // 4. Dark Brown Palette
    // -----------------------------------------------------------------------------------------
    object DarkBrown {
        val LightPrimary = Color(0xFF4D2002)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFFCECE3)
        val LightOnPrimaryContainer = Color(0xFF301200)
        val LightSecondary = Color(0xFF6F4F3C)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFF5E2D6)
        val LightOnSecondaryContainer = Color(0xFF3E2414)
        val LightTertiary = Color(0xFF6B5532)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFF5E6CC)
        val LightOnTertiaryContainer = Color(0xFF37270B)
        val LightInversePrimary = Color(0xFFFFB68C)

        val DarkPrimary = Color(0xFFFFB68C)
        val DarkOnPrimary = Color(0xFF471800)
        val DarkPrimaryContainer = Color(0xFF5E2B06)
        val DarkOnPrimaryContainer = Color(0xFFFFDBC8)
        val DarkSecondary = Color(0xFFDFC0B0)
        val DarkOnSecondary = Color(0xFF432C1E)
        val DarkSecondaryContainer = Color(0xFF5B4233)
        val DarkOnSecondaryContainer = Color(0xFFF8D9CE)
        val DarkTertiary = Color(0xFFDFBF98)
        val DarkOnTertiary = Color(0xFF402E13)
        val DarkTertiaryContainer = Color(0xFF574326)
        val DarkOnTertiaryContainer = Color(0xFFFBDDB6)
        val DarkInversePrimary = Color(0xFF4D2002)

        // Primary-aware dark background and stepped surfaces (charcoal brown undertone)
        val DarkBackground = Color(0xFF141210)
        val DarkSurface = Color(0xFF1B1815)
        val DarkSurfaceVariant = Color(0xFF28231E)
        val DarkSurfaceContainerLowest = Color(0xFF0F0E0C)
        val DarkSurfaceContainerLow = Color(0xFF1F1B17)
        val DarkSurfaceContainer = Color(0xFF26221E)
        val DarkSurfaceContainerHigh = Color(0xFF2D2823)
        val DarkSurfaceContainerHighest = Color(0xFF352F29)
        val DarkSurfaceBright = Color(0xFF403932)
        val DarkSurfaceDim = Color(0xFF161411)
        val DarkOutline = Color(0xFF554A41)
        val DarkOutlineVariant = Color(0xFF342D28)
        val DarkOnBackground = Color(0xFFEDE4DC)
        val DarkOnSurface = Color(0xFFEDE4DC)
        val DarkOnSurfaceVariant = Color(0xFFAEA59D)
    }

    // -----------------------------------------------------------------------------------------
    // 5. Ocean Teal Palette
    // -----------------------------------------------------------------------------------------
    object OceanTeal {
        // Deepened to #136B71 in light mode for >= 5.5:1 text and button contrast
        val LightPrimary = Color(0xFF136B71)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFDEF2F3)
        val LightOnPrimaryContainer = Color(0xFF083C40)
        val LightSecondary = Color(0xFF3D676A)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFD9ECEE)
        val LightOnSecondaryContainer = Color(0xFF1B3E41)
        val LightTertiary = Color(0xFF396478)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFD7EEFB)
        val LightOnTertiaryContainer = Color(0xFF14374A)
        val LightInversePrimary = Color(0xFF70D7DE)

        val DarkPrimary = Color(0xFF70D7DE)
        val DarkOnPrimary = Color(0xFF00373B)
        val DarkPrimaryContainer = Color(0xFF065055)
        val DarkOnPrimaryContainer = Color(0xFFA2EFF5)
        val DarkSecondary = Color(0xFFB1CBD0)
        val DarkOnSecondary = Color(0xFF223A3E)
        val DarkSecondaryContainer = Color(0xFF385256)
        val DarkOnSecondaryContainer = Color(0xFFD1ECF1)
        val DarkTertiary = Color(0xFFA5CBE1)
        val DarkOnTertiary = Color(0xFF1C3A4D)
        val DarkTertiaryContainer = Color(0xFF315064)
        val DarkOnTertiaryContainer = Color(0xFFC7E8FC)
        val DarkInversePrimary = Color(0xFF136B71)

        // Primary-aware dark background and stepped surfaces (cool teal undertone)
        val DarkBackground = Color(0xFF0F1415)
        val DarkSurface = Color(0xFF151B1C)
        val DarkSurfaceVariant = Color(0xFF20292B)
        val DarkSurfaceContainerLowest = Color(0xFF0B0F10)
        val DarkSurfaceContainerLow = Color(0xFF1A2122)
        val DarkSurfaceContainer = Color(0xFF1F2729)
        val DarkSurfaceContainerHigh = Color(0xFF263032)
        val DarkSurfaceContainerHighest = Color(0xFF2E393C)
        val DarkSurfaceBright = Color(0xFF374448)
        val DarkSurfaceDim = Color(0xFF121718)
        val DarkOutline = Color(0xFF455558)
        val DarkOutlineVariant = Color(0xFF2B3638)
        val DarkOnBackground = Color(0xFFE0E5E6)
        val DarkOnSurface = Color(0xFFE0E5E6)
        val DarkOnSurfaceVariant = Color(0xFFA1A8AA)
    }

    // -----------------------------------------------------------------------------------------
    // 6. Pure Black Palette (Monochrome identity)
    // -----------------------------------------------------------------------------------------
    object PureBlack {
        val LightPrimary = Color(0xFF16161A)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFE5E5EB)
        val LightOnPrimaryContainer = Color(0xFF16161A)
        val LightSecondary = Color(0xFF474750)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFECECEF)
        val LightOnSecondaryContainer = Color(0xFF1E1E23)
        val LightTertiary = Color(0xFF5E5E68)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFF1F1F5)
        val LightOnTertiaryContainer = Color(0xFF2B2B33)
        val LightInversePrimary = Color(0xFFFFFFFF)

        val DarkPrimary = Color(0xFFD4D4DE)
        val DarkOnPrimary = Color(0xFF16161C)
        val DarkPrimaryContainer = Color(0xFF282832)
        val DarkOnPrimaryContainer = Color(0xFFF1F1F6)
        val DarkSecondary = Color(0xFFC6C6D0)
        val DarkOnSecondary = Color(0xFF16161C)
        val DarkSecondaryContainer = Color(0xFF27272E)
        val DarkOnSecondaryContainer = Color(0xFFDFDFE7)
        val DarkTertiary = Color(0xFFAAAAB5)
        val DarkOnTertiary = Color(0xFF191920)
        val DarkTertiaryContainer = Color(0xFF26262F)
        val DarkOnTertiaryContainer = Color(0xFFCFCFD8)
        val DarkInversePrimary = Color(0xFF16161A)

        // True black background with differentiated charcoal surfaces
        val DarkBackground = Color(0xFF000000)
        val DarkSurface = Color(0xFF121214)
        val DarkSurfaceVariant = Color(0xFF1E1E22)
        val DarkSurfaceContainerLowest = Color(0xFF070708)
        val DarkSurfaceContainerLow = Color(0xFF161619)
        val DarkSurfaceContainer = Color(0xFF1D1D21)
        val DarkSurfaceContainerHigh = Color(0xFF242429)
        val DarkSurfaceContainerHighest = Color(0xFF2C2C32)
        val DarkSurfaceBright = Color(0xFF36363E)
        val DarkSurfaceDim = Color(0xFF0D0D0F)
        val DarkOutline = Color(0xFF44444D)
        val DarkOutlineVariant = Color(0xFF2B2B32)
        val DarkOnBackground = Color(0xFFEDEDF4)
        val DarkOnSurface = Color(0xFFEDEDF4)
        val DarkOnSurfaceVariant = Color(0xFFA8A6B5)
    }

    // -----------------------------------------------------------------------------------------
    // 7. Amethyst Palette (Brand Hallmark)
    // -----------------------------------------------------------------------------------------
    object Amethyst {
        val LightPrimary = Color(0xFF863E8B)
        val LightOnPrimary = Color(0xFFFFFFFF)
        val LightPrimaryContainer = Color(0xFFF6EBF8)
        val LightOnPrimaryContainer = Color(0xFF4C1052)
        val LightSecondary = Color(0xFF6C4B70)
        val LightOnSecondary = Color(0xFFFFFFFF)
        val LightSecondaryContainer = Color(0xFFF3E4F6)
        val LightOnSecondaryContainer = Color(0xFF381B3C)
        val LightTertiary = Color(0xFF7A4A58)
        val LightOnTertiary = Color(0xFFFFFFFF)
        val LightTertiaryContainer = Color(0xFFF8E5EB)
        val LightOnTertiaryContainer = Color(0xFF401824)
        val LightInversePrimary = Color(0xFFE8A8EE)

        val DarkPrimary = Color(0xFFE8A8EE)
        val DarkOnPrimary = Color(0xFF4B0C52)
        val DarkPrimaryContainer = Color(0xFF67236E)
        val DarkOnPrimaryContainer = Color(0xFFFCD3FF)
        val DarkSecondary = Color(0xFFD4B7D8)
        val DarkOnSecondary = Color(0xFF402844)
        val DarkSecondaryContainer = Color(0xFF563B5B)
        val DarkOnSecondaryContainer = Color(0xFFF1D8F5)
        val DarkTertiary = Color(0xFFE4B6C3)
        val DarkOnTertiary = Color(0xFF45222E)
        val DarkTertiaryContainer = Color(0xFF5C3744)
        val DarkOnTertiaryContainer = Color(0xFFF8DCE3)
        val DarkInversePrimary = Color(0xFF863E8B)

        // Primary-aware dark background and stepped surfaces (restrained plum undertone)
        val DarkBackground = Color(0xFF131116)
        val DarkSurface = Color(0xFF1A171F)
        val DarkSurfaceVariant = Color(0xFF26212E)
        val DarkSurfaceContainerLowest = Color(0xFF0E0C11)
        val DarkSurfaceContainerLow = Color(0xFF1E1B24)
        val DarkSurfaceContainer = Color(0xFF25202C)
        val DarkSurfaceContainerHigh = Color(0xFF2B2634)
        val DarkSurfaceContainerHighest = Color(0xFF332D3E)
        val DarkSurfaceBright = Color(0xFF3E364A)
        val DarkSurfaceDim = Color(0xFF151318)
        val DarkOutline = Color(0xFF51465F)
        val DarkOutlineVariant = Color(0xFF322B3A)
        val DarkOnBackground = Color(0xFFEBE3ED)
        val DarkOnSurface = Color(0xFFEBE3ED)
        val DarkOnSurfaceVariant = Color(0xFFAAA1AD)
    }
}

/**
 * Centralized, theme-aware semantic color palette for NUVORA.
 *
 * Provides contrast-verified, role-specific colors for all 7 application accent themes:
 * - [ambientTint]: Theme-tinted upper ambient lighting bloom
 * - [primaryAction]: Light, accessible accent for buttons, active indicators, and FABs
 * - [primaryActionForeground]: High-contrast foreground color on primaryAction buttons
 * - [selectedStateForeground]: Foreground tint for selected items/icons
 * - [selectedStateContainer]: Subtle accent-tinted container background for selected items
 * - [cardSurface]: Translucent charcoal surface for standard content cards
 * - [cardBorder]: Fine 1.dp border stroke for cards
 * - [modalSurface]: High-opacity container for bottom sheets and dialogs
 * - [primaryText]: High-contrast primary reading text
 * - [secondaryText]: Accessible muted secondary/label text
 * - [divider]: Subtle hairline separator
 * - [inputSurface]: Recessed field container for inputs and search bars
 * - [error]: Semantic error/destructive indicator
 * - [success]: Semantic success/completion indicator
 * - [warning]: Semantic warning/alert indicator
 */
data class SemanticPalette(
    val ambientTint: Color,
    val primaryAction: Color,
    val primaryActionForeground: Color,
    val selectedStateForeground: Color,
    val selectedStateContainer: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val modalSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val divider: Color,
    val inputSurface: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
)

/**
 * Resolves the centralized [SemanticPalette] for a given [three.haddad.smartnotas.data.AccentTheme]
 * under Light Mode, Dark Mode, or OLED Pure Black Mode.
 */
fun resolveSemanticPalette(
    accentTheme: three.haddad.smartnotas.data.AccentTheme,
    darkTheme: Boolean,
    isOledBlack: Boolean = false,
): SemanticPalette {
    if (!darkTheme) {
        val (primaryAction, primaryActionFg, selectedContainer) =
            when (accentTheme) {
                three.haddad.smartnotas.data.AccentTheme.MAROON ->
                    Triple(ThemeColors.Maroon.LightPrimary, ThemeColors.Maroon.LightOnPrimary, ThemeColors.Maroon.LightPrimaryContainer)
                three.haddad.smartnotas.data.AccentTheme.ROYAL_BLUE ->
                    Triple(ThemeColors.RoyalBlue.LightPrimary, ThemeColors.RoyalBlue.LightOnPrimary, ThemeColors.RoyalBlue.LightPrimaryContainer)
                three.haddad.smartnotas.data.AccentTheme.FOREST_GREEN ->
                    Triple(ThemeColors.ForestGreen.LightPrimary, ThemeColors.ForestGreen.LightOnPrimary, ThemeColors.ForestGreen.LightPrimaryContainer)
                three.haddad.smartnotas.data.AccentTheme.DARK_BROWN ->
                    Triple(ThemeColors.DarkBrown.LightPrimary, ThemeColors.DarkBrown.LightOnPrimary, ThemeColors.DarkBrown.LightPrimaryContainer)
                three.haddad.smartnotas.data.AccentTheme.OCEAN_TEAL ->
                    Triple(ThemeColors.OceanTeal.LightPrimary, ThemeColors.OceanTeal.LightOnPrimary, ThemeColors.OceanTeal.LightPrimaryContainer)
                three.haddad.smartnotas.data.AccentTheme.PURE_BLACK ->
                    Triple(ThemeColors.PureBlack.LightPrimary, ThemeColors.PureBlack.LightOnPrimary, ThemeColors.PureBlack.LightPrimaryContainer)
                three.haddad.smartnotas.data.AccentTheme.AMETHYST ->
                    Triple(ThemeColors.Amethyst.LightPrimary, ThemeColors.Amethyst.LightOnPrimary, ThemeColors.Amethyst.LightPrimaryContainer)
            }

        return SemanticPalette(
            ambientTint = Color.Transparent,
            primaryAction = primaryAction,
            primaryActionForeground = primaryActionFg,
            selectedStateForeground = primaryAction,
            selectedStateContainer = selectedContainer,
            cardSurface = ThemeColors.LightSurface,
            cardBorder = ThemeColors.LightOutlineVariant.copy(alpha = 0.5f),
            modalSurface = ThemeColors.LightSurfaceContainerHigh,
            primaryText = ThemeColors.LightOnSurface,
            secondaryText = ThemeColors.LightOnSurfaceVariant,
            divider = ThemeColors.LightOutlineVariant,
            inputSurface = ThemeColors.LightSurfaceVariant,
            error = ThemeColors.LightError,
            success = Color(0xFF1B6D24),
            warning = Color(0xFF7C5800),
        )
    }

    // Dark Mode Semantic Palette
    val ambientTint =
        when (accentTheme) {
            three.haddad.smartnotas.data.AccentTheme.MAROON -> Color(0xFF481016)
            three.haddad.smartnotas.data.AccentTheme.ROYAL_BLUE -> Color(0xFF141E42)
            three.haddad.smartnotas.data.AccentTheme.FOREST_GREEN -> Color(0xFF102814)
            three.haddad.smartnotas.data.AccentTheme.DARK_BROWN -> Color(0xFF321C0E)
            three.haddad.smartnotas.data.AccentTheme.OCEAN_TEAL -> Color(0xFF112B30)
            three.haddad.smartnotas.data.AccentTheme.PURE_BLACK -> Color(0xFF202028)
            three.haddad.smartnotas.data.AccentTheme.AMETHYST -> Color(0xFF34153A)
        }

    val (primaryAction, primaryActionFg) =
        when (accentTheme) {
            three.haddad.smartnotas.data.AccentTheme.MAROON ->
                Pair(ThemeColors.Maroon.DarkPrimary, ThemeColors.Maroon.DarkOnPrimary)
            three.haddad.smartnotas.data.AccentTheme.ROYAL_BLUE ->
                Pair(ThemeColors.RoyalBlue.DarkPrimary, ThemeColors.RoyalBlue.DarkOnPrimary)
            three.haddad.smartnotas.data.AccentTheme.FOREST_GREEN ->
                Pair(ThemeColors.ForestGreen.DarkPrimary, ThemeColors.ForestGreen.DarkOnPrimary)
            three.haddad.smartnotas.data.AccentTheme.DARK_BROWN ->
                Pair(ThemeColors.DarkBrown.DarkPrimary, ThemeColors.DarkBrown.DarkOnPrimary)
            three.haddad.smartnotas.data.AccentTheme.OCEAN_TEAL ->
                Pair(ThemeColors.OceanTeal.DarkPrimary, ThemeColors.OceanTeal.DarkOnPrimary)
            three.haddad.smartnotas.data.AccentTheme.PURE_BLACK ->
                Pair(Color(0xFFD4D4DE), Color(0xFF16161C))
            three.haddad.smartnotas.data.AccentTheme.AMETHYST ->
                Pair(ThemeColors.Amethyst.DarkPrimary, ThemeColors.Amethyst.DarkOnPrimary)
        }

    val selectedContainer = primaryAction.copy(alpha = 0.16f)

    val cardSurface =
        if (isOledBlack || accentTheme == three.haddad.smartnotas.data.AccentTheme.PURE_BLACK) {
            Color(0xFF141418)
        } else {
            Color(0xFF1B1B22)
        }

    val cardBorder =
        if (isOledBlack || accentTheme == three.haddad.smartnotas.data.AccentTheme.PURE_BLACK) {
            Color(0xFF282832).copy(alpha = 0.60f)
        } else {
            Color(0xFF2C2C36).copy(alpha = 0.65f)
        }

    val modalSurface =
        if (isOledBlack || accentTheme == three.haddad.smartnotas.data.AccentTheme.PURE_BLACK) {
            Color(0xFF16161C).copy(alpha = 0.98f)
        } else {
            Color(0xFF1E1D25).copy(alpha = 0.96f)
        }

    val inputSurface =
        if (isOledBlack || accentTheme == three.haddad.smartnotas.data.AccentTheme.PURE_BLACK) {
            Color(0xFF101014)
        } else {
            Color(0xFF15151C)
        }

    return SemanticPalette(
        ambientTint = ambientTint,
        primaryAction = primaryAction,
        primaryActionForeground = primaryActionFg,
        selectedStateForeground = primaryAction,
        selectedStateContainer = selectedContainer,
        cardSurface = cardSurface,
        cardBorder = cardBorder,
        modalSurface = modalSurface,
        primaryText = Color(0xFFEDEDF2),
        secondaryText = Color(0xFFA6A4B4),
        divider = Color(0xFF282832).copy(alpha = 0.55f),
        inputSurface = inputSurface,
        error = ThemeColors.DarkError,
        success = Color(0xFF7DD080),
        warning = Color(0xFFFFD188),
    )
}

