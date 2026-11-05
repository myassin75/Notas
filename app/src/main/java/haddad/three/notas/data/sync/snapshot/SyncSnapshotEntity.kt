package haddad.three.notas.data.sync.snapshot

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

object SnapshotStatus {
    const val CREATED = "CREATED"
    const val READY_TO_UPLOAD = "READY_TO_UPLOAD"
    const val ACTIVE = "ACTIVE"
    const val FAILED = "FAILED"
}

/**
 * Tracks metadata and publication status of an immutable, encrypted snapshot.
 */
@Entity(
    tableName = "sync_snapshots",
    indices = [
        Index("status"),
        Index("createdAt"),
    ],
)
data class SyncSnapshotEntity(
    @PrimaryKey
    val snapshotId: String,
    val creatorDeviceId: String,
    val createdAt: Long,
    val formatVersion: Int,
    val storagePath: String,
    val contentSha256: String,
    val driveFileId: String? = null,
    val coverageVectorJson: String,
    val snapshotHlc: String,
    val status: String = SnapshotStatus.CREATED,
    val updatedAt: Long = createdAt,
)

@Dao
interface SyncSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SyncSnapshotEntity)

    @Update
    suspend fun update(snapshot: SyncSnapshotEntity)

    @Query("SELECT * FROM sync_snapshots WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun getById(snapshotId: String): SyncSnapshotEntity?

    @Query("SELECT * FROM sync_snapshots WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<SyncSnapshotEntity>

    @Query("SELECT * FROM sync_snapshots WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveSnapshot(): SyncSnapshotEntity?

    @Query("SELECT * FROM sync_snapshots ORDER BY createdAt DESC")
    suspend fun getAllSnapshots(): List<SyncSnapshotEntity>

    @Query("UPDATE sync_snapshots SET status = :newStatus, driveFileId = :driveFileId, updatedAt = :timestamp WHERE snapshotId = :snapshotId")
    suspend fun markUploadedAndActive(
        snapshotId: String,
        driveFileId: String,
        newStatus: String = SnapshotStatus.ACTIVE,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query("UPDATE sync_snapshots SET status = :newStatus, updatedAt = :timestamp WHERE snapshotId = :snapshotId")
    suspend fun updateStatus(snapshotId: String, newStatus: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM sync_snapshots WHERE snapshotId = :snapshotId")
    suspend fun deleteById(snapshotId: String): Int

    @Query("DELETE FROM sync_snapshots")
    suspend fun clearAll()
}

object PromotionStatus {
    const val STAGED = "STAGED"
    const val COMMITTED = "COMMITTED"
    const val COMPLETED = "COMPLETED"
}

/**
 * Tracks durable atomic progress of two-phase attachment staging and promotion.
 */
@Entity(tableName = "sync_attachment_promotions")
data class SyncAttachmentPromotionEntity(
    @PrimaryKey
    val snapshotId: String,
    val status: String = PromotionStatus.STAGED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Dao
interface SyncAttachmentPromotionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: SyncAttachmentPromotionEntity)

    @Query("SELECT * FROM sync_attachment_promotions WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun getBySnapshotId(snapshotId: String): SyncAttachmentPromotionEntity?

    @Query("UPDATE sync_attachment_promotions SET status = 'COMPLETED', updatedAt = :timestamp WHERE snapshotId = :snapshotId")
    suspend fun markCompleted(snapshotId: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM sync_attachment_promotions WHERE status = 'COMMITTED' ORDER BY createdAt ASC")
    suspend fun getPendingCommittedPromotions(): List<SyncAttachmentPromotionEntity>

    @Query("DELETE FROM sync_attachment_promotions WHERE snapshotId = :snapshotId")
    suspend fun deleteBySnapshotId(snapshotId: String): Int

    @Query("DELETE FROM sync_attachment_promotions")
    suspend fun clearAll()
}

