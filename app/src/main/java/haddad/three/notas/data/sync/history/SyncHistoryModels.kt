package haddad.three.notas.data.sync.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sync_history_records",
    indices = [
        Index("timestampEpochMillis"),
        Index("status"),
    ],
)
data class SyncHistoryRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMillis: Long,
    val durationMillis: Long,
    val status: String, // SUCCESS, FAILED, PARTIAL, CANCELLED
    val uploadedBatches: Int = 0,
    val downloadedBatches: Int = 0,
    val appliedOperations: Int = 0,
    val conflictsDetected: Int = 0,
    val errorCategory: String? = null,
    val errorMessage: String? = null,
    val triggeredBy: String = "AUTOMATIC", // MANUAL, AUTOMATIC, DEBOUNCED, PERIODIC
)

@Dao
interface SyncHistoryRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SyncHistoryRecordEntity): Long

    @Query("SELECT * FROM sync_history_records ORDER BY timestampEpochMillis DESC LIMIT :limit")
    fun getAllHistoryFlow(limit: Int = 100): Flow<List<SyncHistoryRecordEntity>>

    @Query("SELECT * FROM sync_history_records ORDER BY timestampEpochMillis DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 100): List<SyncHistoryRecordEntity>

    @Query("SELECT * FROM sync_history_records WHERE status = 'SUCCESS' ORDER BY timestampEpochMillis DESC LIMIT :limit")
    fun getSuccessfulHistoryFlow(limit: Int = 100): Flow<List<SyncHistoryRecordEntity>>

    @Query("SELECT * FROM sync_history_records WHERE status = 'FAILED' ORDER BY timestampEpochMillis DESC LIMIT :limit")
    fun getFailedHistoryFlow(limit: Int = 100): Flow<List<SyncHistoryRecordEntity>>

    @Query("DELETE FROM sync_history_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sync_history_records")
    fun getHistoryCountFlow(): Flow<Int>
}

