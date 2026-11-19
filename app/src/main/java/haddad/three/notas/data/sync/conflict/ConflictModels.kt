package haddad.three.notas.data.sync.conflict

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

    @Query("SELECT * FROM sync_applied_operations")
    suspend fun getAllOperations(): List<SyncAppliedOperationEntity> = emptyList()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(operations: List<SyncAppliedOperationEntity>) {}

    @Query("DELETE FROM sync_applied_operations")
    suspend fun clearAll() {}
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
    suspend fun getFieldMetadata(
        entityType: String,
        entityId: String,
        fieldName: String,
    ): SyncConflictMetadataEntity?

    @Query("SELECT * FROM sync_conflict_metadata WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun getAllFieldsForEntity(
        entityType: String,
        entityId: String,
    ): List<SyncConflictMetadataEntity>

    @Query("DELETE FROM sync_conflict_metadata WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteForEntity(
        entityType: String,
        entityId: String,
    ): Int

    @Query("SELECT * FROM sync_conflict_metadata")
    suspend fun getAllMetadata(): List<SyncConflictMetadataEntity> = emptyList()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<SyncConflictMetadataEntity>) {}

    @Query("DELETE FROM sync_conflict_metadata")
    suspend fun clearAll() {}
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
    suspend fun getTombstone(
        entityType: String,
        entityId: String,
    ): SyncTombstoneEntity?

    @Query("DELETE FROM sync_tombstones WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteTombstone(
        entityType: String,
        entityId: String,
    ): Int

    @Query("SELECT * FROM sync_tombstones ORDER BY deletedAt DESC")
    suspend fun getAllTombstones(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :cutoffTimestamp")
    suspend fun deleteTombstonesOlderThan(cutoffTimestamp: Long): Int = 0

    @Query("SELECT COUNT(*) FROM sync_tombstones")
    suspend fun getTombstoneCount(): Int = 0

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tombstones: List<SyncTombstoneEntity>) {}

    @Query("DELETE FROM sync_tombstones")
    suspend fun clearAll() {}
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
    suspend fun getConflictsForEntity(
        entityType: String,
        entityId: String,
    ): List<SyncConflictRecordEntity>

    @Query("SELECT * FROM sync_conflict_records WHERE resolved = 0 ORDER BY detectedAt DESC")
    suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity>

    @Query("SELECT * FROM sync_conflict_records WHERE resolved = 0 ORDER BY detectedAt DESC")
    fun getUnresolvedConflictsFlow(): Flow<List<SyncConflictRecordEntity>> = emptyFlow()

    @Query("SELECT COUNT(*) FROM sync_conflict_records WHERE resolved = 0")
    suspend fun getUnresolvedConflictCount(): Int = 0

    @Query("SELECT COUNT(*) FROM sync_conflict_records WHERE resolved = 0")
    fun getUnresolvedConflictCountFlow(): Flow<Int> = emptyFlow()

    @Query("UPDATE sync_conflict_records SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long): Int

    @Query("UPDATE sync_conflict_records SET resolved = 1 WHERE conflictId = :conflictId")
    suspend fun markResolvedByConflictId(conflictId: String): Int = 0

    @Query("SELECT * FROM sync_conflict_records")
    suspend fun getAllConflictRecords(): List<SyncConflictRecordEntity> = emptyList()

    @Query("SELECT * FROM sync_conflict_records ORDER BY detectedAt DESC")
    fun getAllConflictRecordsFlow(): Flow<List<SyncConflictRecordEntity>> = emptyFlow()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<SyncConflictRecordEntity>) {}

    @Query("DELETE FROM sync_conflict_records")
    suspend fun clearAll() {}
}

/**
 * Domain-level conflict types.
 */
enum class ConflictType {
    CONCURRENT_TITLE_EDIT,
    CONCURRENT_BODY_EDIT,
    DELETE_VS_UPDATE,
    CHECKLIST_CONFLICT,
    CATEGORY_CONFLICT,
    GENERAL_FIELD_CONFLICT;

    companion object {
        fun fromFieldName(fieldName: String): ConflictType =
            when (fieldName) {
                "title" -> CONCURRENT_TITLE_EDIT
                "body" -> CONCURRENT_BODY_EDIT
                "DELETE_VS_UPDATE" -> DELETE_VS_UPDATE
                "text", "checked", "sortOrder", "checklist" -> CHECKLIST_CONFLICT
                "name", "colorHex", "category" -> CATEGORY_CONFLICT
                else -> GENERAL_FIELD_CONFLICT
            }
    }
}

/**
 * Presentation-layer conflict model suitable for ViewModels and UI rendering.
 */
data class ConflictUiModel(
    val id: Long,
    val conflictId: String,
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val conflictType: ConflictType,
    val winningValue: String?,
    val losingValue: String?,
    val winningHlc: String,
    val losingHlc: String,
    val winningDeviceId: String,
    val losingDeviceId: String,
    val detectedAt: Long,
    val resolved: Boolean,
)

/**
 * Summary of distributed synchronization conflict state.
 */
data class ConflictSummary(
    val unresolvedCount: Int,
    val unresolvedConflicts: List<ConflictUiModel>,
    val recentResolvedConflicts: List<ConflictUiModel> = emptyList(),
)

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
