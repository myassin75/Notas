package three.haddad.smartnotas.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val colorScheme =
        remember(themeState.accentTheme, darkTheme, black) {
            buildColorScheme(
                accentTheme = themeState.accentTheme,
                darkTheme = darkTheme,
                blackThemeActive = black,
            )
        }

    return RememberColorResolution(
        colorScheme = colorScheme,
        backgroundScheme = colorScheme,
    )
}
