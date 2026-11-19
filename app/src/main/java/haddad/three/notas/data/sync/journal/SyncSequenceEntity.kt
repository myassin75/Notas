package haddad.three.notas.data.sync.journal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * Tracks the persistent, monotonically increasing sequence counter for each device.
 */
@Entity(tableName = "sync_sequences")
data class SyncSequenceEntity(
    @PrimaryKey
    val deviceId: String,
    val lastAllocatedSequence: Long,
    val updatedAt: Long,
)

@Dao
interface SyncSequenceDao {
    @Query("SELECT * FROM sync_sequences WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getSequence(deviceId: String): SyncSequenceEntity?

    @Query("SELECT lastAllocatedSequence FROM sync_sequences WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getLastAllocatedSequence(deviceId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: SyncSequenceEntity)

    @Query("UPDATE sync_sequences SET lastAllocatedSequence = :sequence, updatedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun setLastAllocatedSequence(
        deviceId: String,
        sequence: Long,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    /**
     * Atomically sets or inserts the sequence for [deviceId].
     */
    @Transaction
    suspend fun setSequence(
        deviceId: String,
        sequence: Long,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val updated = setLastAllocatedSequence(deviceId, sequence, timestamp)
        if (updated == 0) {
            insertOrUpdate(
                SyncSequenceEntity(
                    deviceId = deviceId,
                    lastAllocatedSequence = sequence,
                    updatedAt = timestamp,
                ),
            )
        }
    }

    /**
     * Atomically allocates and persists the next sequence number for [deviceId].
     * Guarantees monotonicity and survives application restarts.
     */
    @Transaction
    suspend fun allocateNextSequence(
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Long {
        val current = getSequence(deviceId)
        val nextSeq = (current?.lastAllocatedSequence ?: 0L) + 1L
        insertOrUpdate(
            SyncSequenceEntity(
                deviceId = deviceId,
                lastAllocatedSequence = nextSeq,
                updatedAt = timestamp,
            ),
        )
        return nextSeq
    }

    @Query("DELETE FROM sync_sequences WHERE deviceId = :deviceId")
    suspend fun deleteForDevice(deviceId: String): Int

    @Query("DELETE FROM sync_sequences")
    suspend fun clearAll()
}
