package haddad.three.notas.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import haddad.three.notas.ui.components.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncStateStore by preferencesDataStore(name = "notas_sync_state")

/**
 * Persists synchronization watermarks, cursors, stable device ID, and tombstones
 * scoped per authenticated account.
 */
@Singleton
class SyncStateStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(context: Context) : this(context.syncStateStore)

    private val json = Json { ignoreUnknownKeys = true }

    private val _syncStatus = MutableStateFlow(SyncStatus.NOT_CONNECTED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _syncConflicts = MutableStateFlow<List<SyncConflictRecord>>(emptyList())
    val syncConflicts: StateFlow<List<SyncConflictRecord>> = _syncConflicts.asStateFlow()

    fun updateSyncStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun getSyncCursor(userId: String): String {
        val prefs = dataStore.data.first()
        return prefs[cursorKey(userId)] ?: "0"
    }

    suspend fun setSyncCursor(
        userId: String,
        cursor: String,
    ) {
        dataStore.edit { it[cursorKey(userId)] = cursor }
    }

    suspend fun getLastPushedAt(userId: String): Long {
        val prefs = dataStore.data.first()
        return prefs[lastPushedKey(userId)] ?: 0L
    }

    suspend fun setLastPushedAt(
        userId: String,
        timestamp: Long,
    ) {
        dataStore.edit { it[lastPushedKey(userId)] = timestamp }
    }

    suspend fun getLastSyncedAt(userId: String): Long {
        val prefs = dataStore.data.first()
        return prefs[lastSyncedKey(userId)] ?: 0L
    }

    suspend fun setLastSyncedAt(
        userId: String,
        timestamp: Long,
    ) {
        dataStore.edit { it[lastSyncedKey(userId)] = timestamp }
    }

    suspend fun getTombstones(userId: String): List<SyncTombstone> {
        val prefs = dataStore.data.first()
        val rawJson = prefs[tombstonesKey(userId)] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<SyncTombstone>>(rawJson)
        }.getOrDefault(emptyList())
    }

    suspend fun addTombstones(
        userId: String,
        tombstones: List<SyncTombstone>,
    ) {
        if (tombstones.isEmpty()) return
        dataStore.edit { prefs ->
            val key = tombstonesKey(userId)
            val current =
                runCatching {
                    prefs[key]?.let { json.decodeFromString<List<SyncTombstone>>(it) }
                }.getOrNull().orEmpty()

            val existingUuids = current.map { it.uuid }.toSet()
            val newItems = tombstones.filter { it.uuid !in existingUuids }
            val merged = current + newItems
            prefs[key] = json.encodeToString(merged)
        }
    }

    suspend fun removeTombstones(
        userId: String,
        uuids: Set<String>,
    ) {
        if (uuids.isEmpty()) return
        dataStore.edit { prefs ->
            val key = tombstonesKey(userId)
            val current =
                runCatching {
                    prefs[key]?.let { json.decodeFromString<List<SyncTombstone>>(it) }
                }.getOrNull().orEmpty()

            val filtered = current.filter { it.uuid !in uuids }
            prefs[key] = json.encodeToString(filtered)
        }
    }

    suspend fun getConflicts(userId: String): List<SyncConflictRecord> {
        val prefs = dataStore.data.first()
        val rawJson = prefs[conflictsKey(userId)] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<SyncConflictRecord>>(rawJson)
        }.getOrDefault(emptyList())
    }

    suspend fun recordConflicts(
        userId: String,
        conflicts: List<SyncPushConflictDto>,
    ) {
        if (conflicts.isEmpty()) return
        val now = System.currentTimeMillis()
        val records =
            conflicts.map { conflict ->
                SyncConflictRecord(
                    entityType = conflict.entityType,
                    entityId = conflict.id,
                    serverRevision = conflict.serverRevision,
                    reason = conflict.reason.take(200), // sanitize length, contains no tokens/content
                    timestamp = now,
                )
            }
        _syncConflicts.value = records
        dataStore.edit { prefs ->
            val key = conflictsKey(userId)
            val current =
                runCatching {
                    prefs[key]?.let { json.decodeFromString<List<SyncConflictRecord>>(it) }
                }.getOrNull().orEmpty()
            // Keep at most 50 recent conflicts
            val updated = (records + current).take(50)
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun clearAccountState(userId: String) {
        dataStore.edit { prefs ->
            prefs.remove(cursorKey(userId))
            prefs.remove(lastPushedKey(userId))
            prefs.remove(lastSyncedKey(userId))
            prefs.remove(tombstonesKey(userId))
            prefs.remove(conflictsKey(userId))
        }
        _syncConflicts.value = emptyList()
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("sync_device_id")

        fun cursorKey(userId: String) = stringPreferencesKey("sync_cursor_$userId")

        fun lastPushedKey(userId: String) = longPreferencesKey("sync_last_pushed_$userId")

        fun lastSyncedKey(userId: String) = longPreferencesKey("sync_last_synced_$userId")

        fun tombstonesKey(userId: String) = stringPreferencesKey("sync_tombstones_$userId")

        fun conflictsKey(userId: String) = stringPreferencesKey("sync_conflicts_$userId")
    }
}
