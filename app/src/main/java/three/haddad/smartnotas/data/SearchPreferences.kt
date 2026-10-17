package three.haddad.smartnotas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long,
)

private val Context.searchDataStore by preferencesDataStore(name = "search_prefs")

@Singleton
open class SearchPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.searchDataStore)

    private object Keys {
        val RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
    }

    open val recentSearches: Flow<List<SearchHistoryEntry>> =
        dataStore.data.map { prefs ->
            val jsonStr = prefs[Keys.RECENT_SEARCHES_JSON] ?: "[]"
            parseRecentSearchesJson(jsonStr)
        }

    open suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { prefs ->
            val jsonStr = prefs[Keys.RECENT_SEARCHES_JSON] ?: "[]"
            val currentList = parseRecentSearchesJson(jsonStr).toMutableList()
            currentList.removeAll { it.query.equals(trimmed, ignoreCase = true) }
            currentList.add(0, SearchHistoryEntry(trimmed, System.currentTimeMillis()))
            val truncated = currentList.take(10)
            prefs[Keys.RECENT_SEARCHES_JSON] = serializeRecentSearchesJson(truncated)
        }
    }

    open suspend fun removeSearchQuery(query: String) {
        dataStore.edit { prefs ->
            val jsonStr = prefs[Keys.RECENT_SEARCHES_JSON] ?: "[]"
            val currentList = parseRecentSearchesJson(jsonStr).toMutableList()
            currentList.removeAll { it.query.equals(query, ignoreCase = true) }
            prefs[Keys.RECENT_SEARCHES_JSON] = serializeRecentSearchesJson(currentList)
        }
    }

    open suspend fun clearRecentSearches() {
        dataStore.edit { prefs ->
            prefs[Keys.RECENT_SEARCHES_JSON] = "[]"
        }
    }

    private fun parseRecentSearchesJson(jsonStr: String): List<SearchHistoryEntry> {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SearchHistoryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val query = obj.getString("query")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                list.add(SearchHistoryEntry(query, timestamp))
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun serializeRecentSearchesJson(list: List<SearchHistoryEntry>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("query", item.query)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
