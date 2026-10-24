package three.haddad.smartnotas.ui.theme

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import three.haddad.smartnotas.data.NotesUiState
import three.haddad.smartnotas.data.ThemeState

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

data class ProgressiveBlurStyle(
    val topHeightPx: Float,
    val bottomHeightPx: Float,
    val blurRadius: Float,
    val overlayAlpha: Float,
    val overlayAlphaBottom: Float,
    /** Top-edge blur curve exponent; lower values keep blur stronger over overlaid chrome. */
    val topBlurProgressPower: Float = 2.5f,
)

val LocalProgressiveBlurStyle = staticCompositionLocalOf<ProgressiveBlurStyle?> { null }

val LocalUseGradient = compositionLocalOf { false }

val LocalHeroOnCards = compositionLocalOf { false }

/** When false, note cards show the title and metadata only. Defaults to showing content. */
val LocalShowNoteContentOnCards = compositionLocalOf { true }

val LocalAdaptiveNoteThemes = compositionLocalOf { true }

/**
 * Notes UI preferences, already collected. Provided from [RememberTheme] so settings can read them
 * on first composition.
 */
val LocalNotesUiState = compositionLocalOf { NotesUiState() }

val LocalBlurBars = compositionLocalOf { true }

val LocalUseEnhancedShading = compositionLocalOf { false }

val LocalReducedMotion = compositionLocalOf { false }

/**
 * The full, already-collected [ThemeState]. Provided once from RememberTheme so downstream
 * screens (Settings, etc.) can read it synchronously on first composition — no flash from
 * default values → DataStore-backed values.
 */
val LocalThemeState = compositionLocalOf { ThemeState() }

/**
 * The centralized, theme-aware [SemanticPalette]. Provided once from RememberTheme
 * so any screen or component can access role-specific semantic colors.
 */
val LocalSemanticPalette = staticCompositionLocalOf<SemanticPalette> {
    resolveSemanticPalette(
        accentTheme = three.haddad.smartnotas.data.AccentTheme.PURE_BLACK,
        darkTheme = false,
        isOledBlack = false,
    )
}

/**
 * Accessor for the centralized [SemanticPalette] on [androidx.compose.material3.MaterialTheme].
 */
val androidx.compose.material3.MaterialTheme.semanticPalette: SemanticPalette
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalSemanticPalette.current

