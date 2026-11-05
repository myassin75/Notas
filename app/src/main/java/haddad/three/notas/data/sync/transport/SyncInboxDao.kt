package haddad.three.notas.data.sync.transport

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncInboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SyncInboxEntity): Long

    @Update
    suspend fun update(entry: SyncInboxEntity)

    @Query("UPDATE sync_inbox SET status = :status WHERE journalId = :journalId")
    suspend fun updateStatus(journalId: String, status: String): Int

    @Query("SELECT * FROM sync_inbox WHERE journalId = :journalId LIMIT 1")
    suspend fun getByJournalId(journalId: String): SyncInboxEntity?

    @Query("SELECT * FROM sync_inbox WHERE deviceId = :deviceId AND sequence = :sequence LIMIT 1")
    suspend fun getByDeviceAndSequence(deviceId: String, sequence: Long): SyncInboxEntity?

    @Query("SELECT * FROM sync_inbox WHERE status = 'VALIDATED' ORDER BY sequence ASC")
    suspend fun getValidatedEntries(): List<SyncInboxEntity>

    @Query("SELECT DISTINCT deviceId FROM sync_inbox WHERE status IN ('VALIDATED', 'APPLYING')")
    suspend fun getActiveDevicesForIngestion(): List<String>

    @Query("SELECT * FROM sync_inbox WHERE deviceId = :deviceId AND status IN ('VALIDATED', 'APPLYING') ORDER BY sequence ASC")
    suspend fun getPendingEntriesForDevice(deviceId: String): List<SyncInboxEntity>

    @Query("SELECT * FROM sync_inbox WHERE deviceId = :deviceId ORDER BY sequence ASC")
    suspend fun getEntriesForDevice(deviceId: String): List<SyncInboxEntity>

    @Query("SELECT MAX(sequence) FROM sync_inbox WHERE deviceId = :deviceId AND status = 'VALIDATED'")
    suspend fun getMaxValidatedSequence(deviceId: String): Long?

    @Query("SELECT * FROM sync_inbox")
    suspend fun getAllEntries(): List<SyncInboxEntity>

    @Query("DELETE FROM sync_inbox WHERE journalId = :journalId")
    suspend fun deleteByJournalId(journalId: String): Int

    @Query("DELETE FROM sync_inbox")
    suspend fun clearAll()
}

