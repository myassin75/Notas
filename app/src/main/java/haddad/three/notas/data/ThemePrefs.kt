package haddad.three.notas.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import haddad.three.notas.ui.theme.CustomFontStorage

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,

    /** Legacy: migrated to [DARK] + [ThemeState.useBlackTheme]. Kept so [valueOf] can parse old backups. */
    @Deprecated("Use DARK with useBlackTheme")
    BLACK,
}

const val LEGACY_BLACK_THEME_MODE_NAME = "BLACK"

fun isLegacyBlackThemeModeName(raw: String?): Boolean = raw == LEGACY_BLACK_THEME_MODE_NAME

fun ThemeMode.migrated(): ThemeMode =
    if (name == LEGACY_BLACK_THEME_MODE_NAME) {
        ThemeMode.DARK
    } else {
        this
    }

fun ThemeMode.effectiveDarkTheme(systemDark: Boolean): Boolean =
    when (name) {
        ThemeMode.LIGHT.name -> false
        ThemeMode.DARK.name, LEGACY_BLACK_THEME_MODE_NAME -> true
        else -> systemDark
    }

fun ThemeMode.blackThemeEligible(isDarkTheme: Boolean): Boolean =
    when (name) {
        ThemeMode.LIGHT.name -> false
        ThemeMode.DARK.name, LEGACY_BLACK_THEME_MODE_NAME -> true
        else -> isDarkTheme
    }

typealias AppearanceMode = ThemeMode

enum class AccentTheme(
    val id: String,
    val displayName: String,
    val seedHex: String,
    @param:androidx.annotation.StringRes val titleRes: Int,
) {
    MAROON("maroon", "Maroon", "#800000", haddad.three.notas.R.string.appearance_accent_maroon),
    ROYAL_BLUE("royal_blue", "Royal Blue", "#04147D", haddad.three.notas.R.string.appearance_accent_royal_blue),
    FOREST_GREEN("forest_green", "Forest Green", "#047D06", haddad.three.notas.R.string.appearance_accent_forest_green),
    DARK_BROWN("dark_brown", "Dark Brown", "#4D2002", haddad.three.notas.R.string.appearance_accent_dark_brown),
    OCEAN_TEAL("ocean_teal", "Ocean Teal", "#29A4AB", haddad.three.notas.R.string.appearance_accent_ocean_teal),
    PURE_BLACK("pure_black", "Pure Black", "#000000", haddad.three.notas.R.string.appearance_accent_pure_black),
    AMETHYST("amethyst", "Amethyst", "#98549C", haddad.three.notas.R.string.appearance_accent_amethyst),
    ;

    companion object {
        fun fromId(id: String?): AccentTheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: MAROON
    }
}

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentTheme: AccentTheme = AccentTheme.MAROON,
    val useBlackTheme: Boolean = false,
    val blurBars: Boolean = true,
    val customFontPath: String = "",
    val customFontName: String = "",
) {
    val appearanceMode: ThemeMode get() = themeMode
    val useEnhancedShading: Boolean = false

    fun blackThemeActive(isDarkTheme: Boolean): Boolean = useBlackTheme && themeMode.blackThemeEligible(isDarkTheme)

    fun effectiveShadingIntensity(blackThemeActive: Boolean): Float = 0f

    fun effectiveUseGradient(blackThemeActive: Boolean): Boolean = false
}

class ThemePrefs(
    private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val USE_BLACK_THEME = booleanPreferencesKey("use_black_theme")
        val BLUR_BARS = booleanPreferencesKey("blur_bars")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        // We leave keys for backward compatibility but don't use them anymore
    }

    val state: Flow<ThemeState> =
        context.themePrefsDataStore.data.map { p ->
            val storedThemeModeRaw = p[Keys.THEME_MODE].orEmpty()
            val storedThemeMode =
                runCatching { ThemeMode.valueOf(storedThemeModeRaw) }
                    .getOrDefault(ThemeMode.SYSTEM)
            val legacyBlackTheme = isLegacyBlackThemeModeName(storedThemeModeRaw)
            val storedAccent = AccentTheme.fromId(p[Keys.ACCENT_THEME])
            ThemeState(
                themeMode = storedThemeMode.migrated(),
                accentTheme = storedAccent,
                useBlackTheme = (p[Keys.USE_BLACK_THEME] ?: false) || legacyBlackTheme,
                blurBars = p[Keys.BLUR_BARS] ?: true,
                customFontPath = p[Keys.CUSTOM_FONT_PATH].orEmpty(),
                customFontName = p[Keys.CUSTOM_FONT_NAME].orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themePrefsDataStore.edit { it[Keys.THEME_MODE] = mode.migrated().name }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) = setThemeMode(mode)

    suspend fun setAccentTheme(theme: AccentTheme) {
        context.themePrefsDataStore.edit { it[Keys.ACCENT_THEME] = theme.id }
    }

    suspend fun setUseBlackTheme(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.USE_BLACK_THEME] = value }
    }

    suspend fun migrateLegacyBlackThemeIfNeeded() {
        context.themePrefsDataStore.edit { prefs ->
            if (!isLegacyBlackThemeModeName(prefs[Keys.THEME_MODE])) return@edit
            prefs[Keys.THEME_MODE] = ThemeMode.DARK.name
            prefs[Keys.USE_BLACK_THEME] = true
        }
    }

    suspend fun setBlurBars(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.BLUR_BARS] = value }
    }

    suspend fun setCustomFont(
        path: String,
        displayName: String,
    ) {
        context.themePrefsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_FONT_PATH] = path
            prefs[Keys.CUSTOM_FONT_NAME] = displayName
        }
    }

    suspend fun clearCustomFont() {
        CustomFontStorage.deleteStoredFontFiles(context)
        context.themePrefsDataStore.edit { prefs ->
            prefs.remove(Keys.CUSTOM_FONT_PATH)
            prefs.remove(Keys.CUSTOM_FONT_NAME)
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.themePrefsDataStore.data.first()
        return JSONObject().apply {
            val storedThemeMode = prefs[Keys.THEME_MODE].orEmpty()
            val exportBlackTheme =
                (prefs[Keys.USE_BLACK_THEME] ?: false) || isLegacyBlackThemeModeName(storedThemeMode)
            put(
                Keys.THEME_MODE.name,
                if (isLegacyBlackThemeModeName(storedThemeMode)) {
                    ThemeMode.DARK.name
                } else {
                    storedThemeMode
                },
            )
            put(Keys.ACCENT_THEME.name, prefs[Keys.ACCENT_THEME] ?: AccentTheme.PURE_BLACK.id)
            put(Keys.USE_BLACK_THEME.name, exportBlackTheme)
            put(Keys.BLUR_BARS.name, prefs[Keys.BLUR_BARS] ?: true)
            put(Keys.CUSTOM_FONT_PATH.name, prefs[Keys.CUSTOM_FONT_PATH].orEmpty())
            put(Keys.CUSTOM_FONT_NAME.name, prefs[Keys.CUSTOM_FONT_NAME].orEmpty())
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.themePrefsDataStore.edit { mutable ->
            fun stringOrNull(key: String): String? {
                if (!json.has(key) || json.isNull(key)) return null
                return runCatching { json.getString(key) }.getOrNull()
            }

            fun booleanOrNull(key: String): Boolean? {
                if (!json.has(key) || json.isNull(key)) return null
                return when (val rawValue = json.opt(key)) {
                    null -> null
                    is Boolean -> rawValue
                    is String ->
                        when (rawValue.trim().lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    else -> null
                }
            }

            val themeModeRaw = stringOrNull(Keys.THEME_MODE.name)
            when {
                isLegacyBlackThemeModeName(themeModeRaw) -> {
                    mutable[Keys.THEME_MODE] = ThemeMode.DARK.name
                    mutable[Keys.USE_BLACK_THEME] = true
                }

                themeModeRaw != null -> {
                    val themeMode = runCatching { ThemeMode.valueOf(themeModeRaw) }.getOrNull()
                    if (themeMode != null) {
                        mutable[Keys.THEME_MODE] = themeMode.migrated().name
                        booleanOrNull(Keys.USE_BLACK_THEME.name)?.let { value ->
                            mutable[Keys.USE_BLACK_THEME] = value
                        }
                    }
                }
            }
            val accentRaw = stringOrNull(Keys.ACCENT_THEME.name)
            if (accentRaw != null) {
                mutable[Keys.ACCENT_THEME] = AccentTheme.fromId(accentRaw).id
            }
            booleanOrNull(Keys.BLUR_BARS.name)?.let { value ->
                mutable[Keys.BLUR_BARS] = value
            }

            val restoredFontPath = stringOrNull(Keys.CUSTOM_FONT_PATH.name).orEmpty().trim()
            if (restoredFontPath.isNotBlank() && java.io.File(restoredFontPath).isFile) {
                mutable[Keys.CUSTOM_FONT_PATH] = restoredFontPath
                val restoredFontName = stringOrNull(Keys.CUSTOM_FONT_NAME.name).orEmpty().trim()
                if (restoredFontName.isNotBlank()) {
                    mutable[Keys.CUSTOM_FONT_NAME] = restoredFontName
                } else {
                    mutable.remove(Keys.CUSTOM_FONT_NAME)
                }
            } else {
                mutable.remove(Keys.CUSTOM_FONT_PATH)
                mutable.remove(Keys.CUSTOM_FONT_NAME)
            }
        }
    }

    suspend fun reset() {
        context.themePrefsDataStore.edit { it.clear() }
    }
}

fun normalizeHex(raw: String): String? {
    val stripped = raw.trim().removePrefix("#")
    val hex =
        when (stripped.length) {
            3 -> stripped.map { "$it$it" }.joinToString("")
            6 -> stripped
            else -> return null
        }
    if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + hex.uppercase()
}

fun normalizeCustomSeed(raw: String): String? {
    if (raw.contains("|")) {
        val parts = raw.split("|")
        val normalizedParts =
            parts.map { part ->
                normalizeHex(part) ?: return null
            }
        return normalizedParts.joinToString("|")
    }
    return normalizeHex(raw)
}
