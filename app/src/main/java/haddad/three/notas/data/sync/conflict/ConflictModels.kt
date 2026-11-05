package haddad.three.notas.data.sync.conflict

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Durable ledger of applied remote operations to guarantee idempotency across restarts.
 */
@Entity(
    tableName = "sync_applied_operations",
    indices = [
        Index(value = ["deviceId", "sequence"]),
        Index(value = ["entityType", "entityId"]),
    ],
)
data class SyncAppliedOperationEntity(
    @PrimaryKey
    val changeId: String,
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val entityType: String,
    val entityId: String,
    val appliedAt: Long,
    val hlcTimestamp: String,
)

@Dao
interface SyncAppliedOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(operation: SyncAppliedOperationEntity): Long

    @Query("SELECT * FROM sync_applied_operations WHERE changeId = :changeId LIMIT 1")
    suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity?

    @Query("SELECT * FROM sync_applied_operations WHERE journalId = :journalId")
    suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_applied_operations WHERE changeId = :changeId")
    suspend fun isApplied(changeId: String): Boolean

    @Query("DELETE FROM sync_applied_operations")
    suspend fun clearAll()
}

/**
 * Field-level conflict resolution metadata storing the last-applied HLC timestamp per field.
 */
@Entity(
    tableName = "sync_conflict_metadata",
    primaryKeys = ["entityType", "entityId", "fieldName"],
    indices = [
        Index(value = ["entityType", "entityId"]),
    ],
)
data class SyncConflictMetadataEntity(
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val hlcTimestamp: String,
    val updatedAt: Long,
)

@Dao
interface SyncConflictMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SyncConflictMetadataEntity)

    @Query("SELECT * FROM sync_conflict_metadata WHERE entityType = :entityType AND entityId = :entityId AND fieldName = :fieldName LIMIT 1")
    suspend fun getFieldMetadata(entityType: String, entityId: String, fieldName: String): SyncConflictMetadataEntity?

    @Query("SELECT * FROM sync_conflict_metadata WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun getAllFieldsForEntity(entityType: String, entityId: String): List<SyncConflictMetadataEntity>

    @Query("DELETE FROM sync_conflict_metadata WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteForEntity(entityType: String, entityId: String): Int

    @Query("DELETE FROM sync_conflict_metadata")
    suspend fun clearAll()
}

/**
 * Persistent tombstone record to prevent stale updates from resurrecting deleted entities.
 */
@Entity(
    tableName = "sync_tombstones",
    primaryKeys = ["entityType", "entityId"],
    indices = [
        Index("deletedAt"),
    ],
)
data class SyncTombstoneEntity(
    val entityType: String,
    val entityId: String,
    val deletedAt: Long,
    val hlcTimestamp: String,
    val deviceId: String,
)

@Dao
interface SyncTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getTombstone(entityType: String, entityId: String): SyncTombstoneEntity?

    @Query("DELETE FROM sync_tombstones WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteTombstone(entityType: String, entityId: String): Int

    @Query("SELECT * FROM sync_tombstones ORDER BY deletedAt DESC")
    suspend fun getAllTombstones(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones")
    suspend fun clearAll()
}

/**
 * Preserved conflicting versions of non-mergeable user edits (e.g. concurrent content edits).
 */
@Entity(
    tableName = "sync_conflict_records",
    indices = [
        Index(value = ["conflictId"], unique = true),
        Index(value = ["entityType", "entityId"]),
        Index("detectedAt"),
    ],
)
data class SyncConflictRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conflictId: String,
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val winningValue: String?,
    val losingValue: String?,
    val winningHlc: String,
    val losingHlc: String,
    val winningDeviceId: String,
    val losingDeviceId: String,
    val detectedAt: Long,
    val resolved: Boolean = false,
)

@Dao
interface SyncConflictRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SyncConflictRecordEntity): Long

    @Query("SELECT * FROM sync_conflict_records WHERE conflictId = :conflictId LIMIT 1")
    suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity?

    @Query("SELECT * FROM sync_conflict_records WHERE entityType = :entityType AND entityId = :entityId ORDER BY detectedAt DESC")
    suspend fun getConflictsForEntity(entityType: String, entityId: String): List<SyncConflictRecordEntity>

    @Query("SELECT * FROM sync_conflict_records WHERE resolved = 0 ORDER BY detectedAt DESC")
    suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity>

    @Query("UPDATE sync_conflict_records SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long): Int

    @Query("DELETE FROM sync_conflict_records")
    suspend fun clearAll()
}

/**
 * Persistent HLC clock state in Room.
 */
@Entity(tableName = "sync_hlc_state")
data class SyncHlcStateEntity(
    @PrimaryKey
    val nodeId: String,
    val physicalTime: Long,
    val logicalCounter: Int,
    val updatedAt: Long,
)

@Dao
interface SyncHlcStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: SyncHlcStateEntity)

    @Query("SELECT * FROM sync_hlc_state WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getState(nodeId: String): SyncHlcStateEntity?

    @Query("DELETE FROM sync_hlc_state")
    suspend fun clearAll()
}

