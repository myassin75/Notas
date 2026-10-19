package three.haddad.smartnotas.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import three.haddad.smartnotas.data.ThemeState

internal data class RememberColorResolution(
    val colorScheme: ColorScheme,
    val backgroundScheme: ColorScheme,
)

@Composable
internal fun rememberResolvedColorScheme(
    context: Context,
    themeState: ThemeState,
    darkTheme: Boolean,
    black: Boolean,
): RememberColorResolution {
    val brandPrimary = Color(0xFFA64FA8)

    val colorScheme =
        if (darkTheme) {
            darkColorScheme(
                primary = brandPrimary,
            ).let { if (black) it.copy(background = Color.Black, surface = Color.Black) else it }
        } else {
            lightColorScheme(
                primary = brandPrimary,
                primaryContainer = Color(0xFFF1E5F4),
                background = Color(0xFFF5F5F8),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF383743),
                onSurfaceVariant = Color(0xFF8A879B),
                outline = Color(0xFFE3DFEA),
            )
        }

    return RememberColorResolution(
        colorScheme = colorScheme,
        backgroundScheme = colorScheme,
    )
}
