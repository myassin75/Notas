package haddad.three.notas.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import haddad.three.notas.ui.components.SyncStatus

class SyncStateStoreTest {

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            data.value = updated
            return updated
        }
    }

    @Test
    fun `getOrCreateDeviceId generates stable device ID across multiple calls`() = runTest {
        val store = SyncStateStore(MemoryPreferencesDataStore())
        val id1 = store.getOrCreateDeviceId()
        val id2 = store.getOrCreateDeviceId()

        assertTrue(id1.isNotBlank())
        assertEquals(id1, id2)
    }

    @Test
    fun `cursor is tracked independently per account`() = runTest {
        val store = SyncStateStore(MemoryPreferencesDataStore())

        assertEquals("0", store.getSyncCursor("userA"))
        assertEquals("0", store.getSyncCursor("userB"))

        store.setSyncCursor("userA", "100")
        store.setSyncCursor("userB", "250")

        assertEquals("100", store.getSyncCursor("userA"))
        assertEquals("250", store.getSyncCursor("userB"))
    }

    @Test
    fun `timestamps are scoped per account`() = runTest {
        val store = SyncStateStore(MemoryPreferencesDataStore())

        store.setLastPushedAt("userA", 1000L)
        store.setLastSyncedAt("userA", 1500L)

        assertEquals(1000L, store.getLastPushedAt("userA"))
        assertEquals(1500L, store.getLastSyncedAt("userA"))

        // userB defaults to 0
        assertEquals(0L, store.getLastPushedAt("userB"))
        assertEquals(0L, store.getLastSyncedAt("userB"))
    }

    @Test
    fun `tombstones are added, deduplicated, and removed correctly per account`() = runTest {
        val store = SyncStateStore(MemoryPreferencesDataStore())

        val t1 = SyncTombstone("note", "uuid-1", 1000L)
        val t2 = SyncTombstone("category", "uuid-2", 1001L)

        store.addTombstones("userA", listOf(t1, t2))
        assertEquals(2, store.getTombstones("userA").size)
        assertTrue(store.getTombstones("userB").isEmpty())

        // Adding duplicate t1 should not duplicate in list
        store.addTombstones("userA", listOf(t1))
        assertEquals(2, store.getTombstones("userA").size)

        // Remove t1
        store.removeTombstones("userA", setOf("uuid-1"))
        val remaining = store.getTombstones("userA")
        assertEquals(1, remaining.size)
        assertEquals("uuid-2", remaining[0].uuid)
    }

    @Test
    fun `clearAccountState clears only target account`() = runTest {
        val store = SyncStateStore(MemoryPreferencesDataStore())

        store.setSyncCursor("userA", "500")
        store.setSyncCursor("userB", "700")

        store.clearAccountState("userA")

        assertEquals("0", store.getSyncCursor("userA"))
        assertEquals("700", store.getSyncCursor("userB"))
    }

    @Test
    fun `syncStatus flow updates correctly`() {
        val store = SyncStateStore(MemoryPreferencesDataStore())
        assertEquals(SyncStatus.NOT_CONNECTED, store.syncStatus.value)

        store.updateSyncStatus(SyncStatus.SYNCING)
        assertEquals(SyncStatus.SYNCING, store.syncStatus.value)

        store.updateSyncStatus(SyncStatus.SYNCED)
        assertEquals(SyncStatus.SYNCED, store.syncStatus.value)
    }
}
