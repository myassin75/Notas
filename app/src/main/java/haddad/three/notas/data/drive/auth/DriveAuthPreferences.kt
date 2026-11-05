package haddad.three.notas.data.drive.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.drivePrefsDataStore by preferencesDataStore(name = "notas_drive_prefs")

data class DrivePreferencesState(
    val isConnected: Boolean = false,
    val connectedEmail: String? = null,
    val connectedAccountId: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequencyDays: Int = 1,
    val autoBackupRequiresWifi: Boolean = true,
    val lastBackupEpochMillis: Long = 0L,
)

/**
 * Manages Google Drive configuration and metadata.
 * Note: Raw OAuth access tokens are NEVER persisted in SharedPreferences or DataStore.
 * Tokens are retrieved fresh on-demand via Google Identity Services or kept transiently in memory.
 */
@Singleton
class DriveAuthPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(context: Context) : this(context.drivePrefsDataStore)

    private object Keys {
        val IS_CONNECTED = booleanPreferencesKey("drive_is_connected")
        val CONNECTED_EMAIL = stringPreferencesKey("drive_connected_email")
        val CONNECTED_ACCOUNT_ID = stringPreferencesKey("drive_connected_account_id")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("drive_auto_backup_enabled")
        val AUTO_BACKUP_FREQUENCY_DAYS = intPreferencesKey("drive_auto_backup_frequency_days")
        val AUTO_BACKUP_REQUIRES_WIFI = booleanPreferencesKey("drive_auto_backup_requires_wifi")
        val LAST_BACKUP_EPOCH_MILLIS = androidx.datastore.preferences.core.longPreferencesKey("drive_last_backup_epoch_millis")
    }

    val state: Flow<DrivePreferencesState> = dataStore.data.map { prefs ->
        DrivePreferencesState(
            isConnected = prefs[Keys.IS_CONNECTED] ?: false,
            connectedEmail = prefs[Keys.CONNECTED_EMAIL],
            connectedAccountId = prefs[Keys.CONNECTED_ACCOUNT_ID],
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            autoBackupFrequencyDays = prefs[Keys.AUTO_BACKUP_FREQUENCY_DAYS] ?: 1,
            autoBackupRequiresWifi = prefs[Keys.AUTO_BACKUP_REQUIRES_WIFI] ?: true,
            lastBackupEpochMillis = prefs[Keys.LAST_BACKUP_EPOCH_MILLIS] ?: 0L,
        )
    }

    suspend fun getState(): DrivePreferencesState = state.first()

    suspend fun setConnected(email: String, accountId: String? = null) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_CONNECTED] = true
            prefs[Keys.CONNECTED_EMAIL] = email
            if (accountId != null) {
                prefs[Keys.CONNECTED_ACCOUNT_ID] = accountId
            }
        }
    }

    suspend fun setDisconnected() {
        dataStore.edit { prefs ->
            prefs[Keys.IS_CONNECTED] = false
            prefs.remove(Keys.CONNECTED_EMAIL)
            prefs.remove(Keys.CONNECTED_ACCOUNT_ID)
        }
    }

    suspend fun setAutoBackupSettings(
        enabled: Boolean,
        frequencyDays: Int = 1,
        requiresWifi: Boolean = true,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
            prefs[Keys.AUTO_BACKUP_FREQUENCY_DAYS] = frequencyDays.coerceIn(1, 30)
            prefs[Keys.AUTO_BACKUP_REQUIRES_WIFI] = requiresWifi
        }
    }

    suspend fun recordBackupCompleted(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_EPOCH_MILLIS] = timestamp
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

