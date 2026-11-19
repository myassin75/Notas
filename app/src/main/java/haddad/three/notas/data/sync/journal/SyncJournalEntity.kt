package haddad.three.notas.data.sync.journal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Tracks metadata and publication status of an immutable, encrypted sync journal file.
 * The journal content itself resides encrypted on disk at [storagePath].
 */
@Entity(
    tableName = "sync_journals",
    indices = [
        Index(value = ["deviceId", "sequence"], unique = true),
        Index("status"),
        Index("createdAt"),
        Index("driveFileId"),
    ],
)
data class SyncJournalEntity(
    @PrimaryKey
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val formatVersion: Int,
    val createdAt: Long,
    val operationCount: Int,
    val storagePath: String,
    val encryptedSizeBytes: Long,
    val status: String = JournalStatus.CREATED.name,
    val uploadAttempts: Int = 0,
    val lastError: String? = null,
    val driveFileId: String? = null,
    val updatedAt: Long = createdAt,
)

@Dao
interface SyncJournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(journal: SyncJournalEntity): Long

    @Update
    suspend fun update(journal: SyncJournalEntity)

    @Query("SELECT * FROM sync_journals WHERE journalId = :journalId LIMIT 1")
    suspend fun getById(journalId: String): SyncJournalEntity?

    @Query("SELECT * FROM sync_journals WHERE deviceId = :deviceId AND sequence = :sequence LIMIT 1")
    suspend fun getByDeviceAndSequence(
        deviceId: String,
        sequence: Long,
    ): SyncJournalEntity?

    @Query("SELECT * FROM sync_journals WHERE status = :status ORDER BY sequence ASC")
    suspend fun getByStatus(status: String): List<SyncJournalEntity>

    @Query("SELECT * FROM sync_journals WHERE status = 'READY_TO_UPLOAD' ORDER BY sequence ASC")
    suspend fun getReadyToUpload(): List<SyncJournalEntity>

    @Query("SELECT * FROM sync_journals WHERE deviceId = :deviceId ORDER BY sequence ASC")
    suspend fun getJournalsForDevice(deviceId: String): List<SyncJournalEntity>

    @Query("SELECT * FROM sync_journals ORDER BY createdAt ASC")
    fun observeAllJournals(): Flow<List<SyncJournalEntity>>

    @Query("SELECT MAX(sequence) FROM sync_journals WHERE deviceId = :deviceId")
    suspend fun getMaxPublishedSequence(deviceId: String): Long?

    @Query("SELECT MAX(sequence) FROM sync_journals WHERE deviceId = :deviceId AND status IN ('READY_TO_UPLOAD', 'UPLOADED')")
    suspend fun getMaxCommittedSequence(deviceId: String): Long?

    @Query("SELECT * FROM sync_journals WHERE deviceId = :deviceId AND status = 'CREATED'")
    suspend fun getCreatedJournals(deviceId: String): List<SyncJournalEntity>

    @Query("UPDATE sync_journals SET status = :newStatus, driveFileId = :driveFileId, updatedAt = :timestamp WHERE journalId = :journalId")
    suspend fun markUploaded(
        journalId: String,
        driveFileId: String,
        newStatus: String = JournalStatus.UPLOADED.name,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query("SELECT * FROM sync_journals WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveFileId(driveFileId: String): SyncJournalEntity?

    @Query("UPDATE sync_journals SET status = :newStatus, updatedAt = :timestamp WHERE journalId = :journalId")
    suspend fun updateStatus(
        journalId: String,
        newStatus: String,
        timestamp: Long,
    ): Int

    @Query("UPDATE sync_journals SET status = :newStatus, uploadAttempts = uploadAttempts + 1, lastError = :error, updatedAt = :timestamp WHERE journalId = :journalId")
    suspend fun recordUploadFailure(
        journalId: String,
        newStatus: String,
        error: String?,
        timestamp: Long,
    ): Int

    @Query("DELETE FROM sync_journals WHERE journalId = :journalId")
    suspend fun deleteById(journalId: String): Int

    @Query("DELETE FROM sync_journals")
    suspend fun clearAll()
}
