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

        val DarkPrimary = Color(0xFFFFFFFF)
        val DarkOnPrimary = Color(0xFF000000)
        val DarkPrimaryContainer = Color(0xFF2D2D35)
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
    }
}
