package haddad.three.notas.data.sync.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.driveSyncDataStore by preferencesDataStore(name = "notas_drive_sync_prefs")

@Singleton
class DriveSyncStateStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.driveSyncDataStore)

    private val _syncState = MutableStateFlow<DriveSyncState>(DriveSyncState.Idle)
    val syncState: StateFlow<DriveSyncState> = _syncState.asStateFlow()

    private object Keys {
        val SYNC_ENABLED = booleanPreferencesKey("drive_sync_enabled")
        val SYNC_FREQUENCY_MINUTES = longPreferencesKey("drive_sync_frequency_minutes")
        val SYNC_REQUIRES_WIFI = booleanPreferencesKey("drive_sync_requires_wifi")
        val LAST_SYNC_EPOCH_MILLIS = longPreferencesKey("drive_last_sync_epoch_millis")
        val LAST_ERROR_CATEGORY = stringPreferencesKey("drive_last_error_category")
        val LAST_ERROR_MESSAGE = stringPreferencesKey("drive_last_error_message")
        val RATE_LIMIT_EXPIRES_AT = longPreferencesKey("drive_rate_limit_expires_at")
    }

    val preferencesState: Flow<DriveSyncPreferencesState> =
        dataStore.data.map { prefs ->
            val rawCat = prefs[Keys.LAST_ERROR_CATEGORY]
            val cat = rawCat?.let { runCatching { SyncErrorCategory.valueOf(it) }.getOrNull() }
            DriveSyncPreferencesState(
                isSyncEnabled = prefs[Keys.SYNC_ENABLED] ?: true,
                syncFrequencyMinutes = (prefs[Keys.SYNC_FREQUENCY_MINUTES] ?: 15L).coerceAtLeast(15L),
                syncRequiresWifi = prefs[Keys.SYNC_REQUIRES_WIFI] ?: false,
                lastSyncEpochMillis = prefs[Keys.LAST_SYNC_EPOCH_MILLIS] ?: 0L,
                lastErrorCategory = cat,
                lastErrorMessage = prefs[Keys.LAST_ERROR_MESSAGE],
            )
        }

    suspend fun getPreferences(): DriveSyncPreferencesState = preferencesState.first()

    fun updateSyncState(state: DriveSyncState) {
        _syncState.value = state
    }

    suspend fun setSyncPreferences(
        enabled: Boolean,
        frequencyMinutes: Long = 15L,
        requiresWifi: Boolean = false,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_ENABLED] = enabled
            prefs[Keys.SYNC_FREQUENCY_MINUTES] = frequencyMinutes.coerceAtLeast(15L)
            prefs[Keys.SYNC_REQUIRES_WIFI] = requiresWifi
        }
    }

    suspend fun recordSyncCompleted(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_EPOCH_MILLIS] = timestamp
            prefs.remove(Keys.LAST_ERROR_CATEGORY)
            prefs.remove(Keys.LAST_ERROR_MESSAGE)
            prefs.remove(Keys.RATE_LIMIT_EXPIRES_AT)
        }
    }

    suspend fun recordRateLimit(retryAfterSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + (retryAfterSeconds.coerceAtLeast(1L) * 1000L)
        dataStore.edit { prefs ->
            prefs[Keys.RATE_LIMIT_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun getRateLimitExpiresAt(): Long = dataStore.data.first()[Keys.RATE_LIMIT_EXPIRES_AT] ?: 0L

    suspend fun clearRateLimit() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.RATE_LIMIT_EXPIRES_AT)
        }
    }

    suspend fun recordSyncFailed(
        category: SyncErrorCategory,
        message: String?,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_ERROR_CATEGORY] = category.name
            if (message != null) {
                // Ensure message contains no note content / sanitized length
                prefs[Keys.LAST_ERROR_MESSAGE] = message.take(250)
            } else {
                prefs.remove(Keys.LAST_ERROR_MESSAGE)
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
        _syncState.value = DriveSyncState.Idle
    }
}
