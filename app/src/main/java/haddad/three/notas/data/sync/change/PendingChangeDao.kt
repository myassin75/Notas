package haddad.three.notas.data.sync.change

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: PendingChangeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(changes: List<PendingChangeEntity>)

    @Update
    suspend fun update(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_changes WHERE changeId = :changeId LIMIT 1")
    suspend fun getById(changeId: String): PendingChangeEntity?

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' ORDER BY createdAt ASC, rowid ASC LIMIT :limit")
    suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' ORDER BY createdAt ASC, rowid ASC")
    suspend fun getAllPendingChanges(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun observePendingChanges(): Flow<List<PendingChangeEntity>>

    @Query("SELECT COUNT(*) FROM pending_changes WHERE status = 'PENDING'")
    suspend fun countPendingChanges(): Int

    @Query("SELECT * FROM pending_changes WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt ASC")
    suspend fun getChangesForEntity(
        entityType: String,
        entityId: String,
    ): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE journalId = :journalId ORDER BY createdAt ASC")
    suspend fun getChangesForJournal(journalId: String): List<PendingChangeEntity>

    @Query("UPDATE pending_changes SET status = 'JOURNALED', journalId = :journalId WHERE changeId IN (:changeIds)")
    suspend fun markChangesJournaled(
        changeIds: Collection<String>,
        journalId: String,
    ): Int

    @Query("UPDATE pending_changes SET status = 'SYNCED' WHERE journalId = :journalId")
    suspend fun markChangesSynced(journalId: String): Int

    @Query("UPDATE pending_changes SET status = 'PENDING', journalId = NULL WHERE journalId = :journalId")
    suspend fun resetJournaledChangesToPending(journalId: String): Int

    @Query("DELETE FROM pending_changes WHERE changeId = :changeId")
    suspend fun deleteById(changeId: String): Int

    @Query("DELETE FROM pending_changes WHERE changeId IN (:changeIds)")
    suspend fun deleteByIds(changeIds: Collection<String>): Int

    @Query("DELETE FROM pending_changes WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteChangesForEntity(
        entityType: String,
        entityId: String,
    ): Int

    @Query("DELETE FROM pending_changes WHERE status = 'SYNCED'")
    suspend fun deleteSynced(): Int

    @Query("DELETE FROM pending_changes")
    suspend fun clearAll()
}
