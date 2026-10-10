package three.haddad.smartnotas.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import three.haddad.smartnotas.data.NotesUiState
import three.haddad.smartnotas.data.ThemeState
import three.haddad.smartnotas.data.effectiveDarkTheme
import three.haddad.smartnotas.ui.common.responsiveTextScaleForWidth
import three.haddad.smartnotas.ui.feedback.LocalHapticEnabled

private const val MAX_APP_DISPLAY_SCALE = 1.15f
private const val MAX_SYSTEM_DISPLAY_SIZE_SCALE = 1.5f

internal fun appDisplayDensity(
    systemDensity: Float,
    stableDensity: Float,
): Float =
    if (systemDensity > stableDensity * MAX_SYSTEM_DISPLAY_SIZE_SCALE) {
        systemDensity
    } else {
        systemDensity.coerceAtMost(stableDensity * MAX_APP_DISPLAY_SCALE)
    }

private const val MAX_APP_FONT_SCALE = 1.10f

val LocalIsDark = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RememberTheme(
    themeState: ThemeState = ThemeState(),
    notesUiState: NotesUiState = NotesUiState(),
    paintBackground: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeState.themeMode.effectiveDarkTheme(systemDark)
    val blackThemeActive = themeState.blackThemeActive(darkTheme)

    val context = LocalContext.current
    val reducedMotion = rememberSystemReducedMotionEnabled(context)
    val customFontFamily =
        remember(themeState.customFontPath) {
            CustomFontStorage.loadFontFamily(themeState.customFontPath)
        }
    val typography =
        remember(customFontFamily) {
            customFontFamily?.let { customFontTypography(it) } ?: AppTypography
        }
    val baseDensity = LocalDensity.current
    val stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    val responsiveTextScale =
        with(baseDensity) {
            responsiveTextScaleForWidth(
                LocalWindowInfo.current.containerSize.width
                    .toDp(),
            )
        }
    val responsiveDensity =
        remember(baseDensity.density, baseDensity.fontScale, responsiveTextScale, stableDensity) {
            Density(
                density = appDisplayDensity(baseDensity.density, stableDensity),
                fontScale = (baseDensity.fontScale * responsiveTextScale).coerceAtMost(MAX_APP_FONT_SCALE),
            )
        }

    val colorResolution =
        rememberResolvedColorScheme(
            context = context,
            themeState = themeState,
            darkTheme = darkTheme,
            black = blackThemeActive,
        )

    val view = LocalView.current
    LaunchedEffect(view, darkTheme) {
        if (view.isInEditMode) return@LaunchedEffect
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val targetColorScheme = colorResolution.colorScheme
    val targetBackgroundScheme = colorResolution.backgroundScheme

    CompositionLocalProvider(
        LocalDensity provides responsiveDensity,
        LocalIsDark provides darkTheme,
        LocalUseGradient provides false,
        LocalHeroOnCards provides notesUiState.heroOnCards,
        LocalShowNoteContentOnCards provides notesUiState.showNoteContentOnCards,
        LocalAdaptiveNoteThemes provides notesUiState.adaptiveNoteThemes,
        LocalNotesUiState provides notesUiState,
        LocalBlurBars provides themeState.blurBars,
        LocalUseEnhancedShading provides themeState.useEnhancedShading,
        LocalThemeState provides themeState,
        LocalReducedMotion provides reducedMotion,
        LocalHapticEnabled provides hapticFeedbackEnabled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = targetColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = typography,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (paintBackground) {
                    Box(Modifier.fillMaxSize().background(targetBackgroundScheme.background))
                }
                content()
            }
        }
    }
}

@Composable
private fun rememberSystemReducedMotionEnabled(context: android.content.Context): Boolean {
    val contentResolver = context.contentResolver

    fun readReducedMotion(): Boolean {
        val animationScale =
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        return animationScale == 0f
    }

    var reducedMotion by remember(contentResolver) { mutableStateOf(readReducedMotion()) }
    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reducedMotion = readReducedMotion()
                }
            }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    return reducedMotion
}
