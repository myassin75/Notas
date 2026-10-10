package three.haddad.smartnotas.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.stringPreferencesKey

val KEY_SELECTED_NOTES_FILTER_TYPE = stringPreferencesKey("selected_notes_filter_type")
val KEY_SELECTED_NOTES_SHOULDER = stringPreferencesKey("selected_notes_shoulder")

enum class SelectedNotesFilterType {
    ALL,
    SHOULDER,
    PINNED,
    
}

data class SelectedNotesWidgetConfig(
    val filterType: SelectedNotesFilterType = SelectedNotesFilterType.ALL,
    val shoulder: String = "",
)

object SelectedNotesWidgetConfigStore {
    private const val PREFS_NAME = "selected_notes_widget_prefs"
    private const val KEY_FILTER_TYPE_PREFIX = "widget_filter_type_"
    private const val KEY_SHOULDER_PREFIX = "widget_shoulder_"
    private const val KEY_REVISION = "widget_config_revision"

    private fun getPreferences(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRevision(context: Context): Long = getPreferences(context).getLong(KEY_REVISION, 0L)

    fun loadConfig(
        context: Context,
        appWidgetId: Int,
    ): SelectedNotesWidgetConfig {
        val preferences = getPreferences(context)
        val rawFilterType =
            preferences.getString("$KEY_FILTER_TYPE_PREFIX$appWidgetId", null)
                ?: preferences.getString(KEY_FILTER_TYPE_PREFIX, null)
                ?: return SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.ALL)
        val filterType =
            runCatching {
                SelectedNotesFilterType.valueOf(rawFilterType)
            }.getOrDefault(SelectedNotesFilterType.ALL)
        val shoulder =
            preferences.getString("$KEY_SHOULDER_PREFIX$appWidgetId", null)
                ?: preferences.getString(KEY_SHOULDER_PREFIX, "")
                ?: ""
        return SelectedNotesWidgetConfig(filterType = filterType, shoulder = shoulder)
    }

    fun saveConfig(
        context: Context,
        appWidgetId: Int,
        config: SelectedNotesWidgetConfig,
    ) {
        val nextRevision = getRevision(context) + 1L
        getPreferences(context).edit {
            putString("$KEY_FILTER_TYPE_PREFIX$appWidgetId", config.filterType.name)
            putString("$KEY_SHOULDER_PREFIX$appWidgetId", config.shoulder)
            // Also store as latest default so any widget instance without an explicit ID can fall back to it
            putString(KEY_FILTER_TYPE_PREFIX, config.filterType.name)
            putString(KEY_SHOULDER_PREFIX, config.shoulder)
            putLong(KEY_REVISION, nextRevision)
        }
    }

    fun deleteConfig(
        context: Context,
        appWidgetId: Int,
    ) {
        getPreferences(context).edit {
            remove("$KEY_FILTER_TYPE_PREFIX$appWidgetId")
            remove("$KEY_SHOULDER_PREFIX$appWidgetId")
        }
    }

    fun deleteConfigs(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        getPreferences(context).edit {
            for (widgetId in appWidgetIds) {
                remove("$KEY_FILTER_TYPE_PREFIX$widgetId")
                remove("$KEY_SHOULDER_PREFIX$widgetId")
            }
        }
    }
}
