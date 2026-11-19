package haddad.three.notas.data.sync.transport

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncWatermarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(watermark: SyncWatermarkEntity)

    @Query("SELECT * FROM sync_watermarks WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getWatermark(deviceId: String): SyncWatermarkEntity?

    @Query("SELECT * FROM sync_watermarks ORDER BY deviceId ASC")
    suspend fun getAllWatermarks(): List<SyncWatermarkEntity>

    @Query("UPDATE sync_watermarks SET highestDiscoveredSeq = :discovered, highestDownloadedSeq = :downloaded, highestValidatedSeq = :validated, missingSequencesJson = :missingJson, updatedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateWatermark(
        deviceId: String,
        discovered: Long,
        downloaded: Long,
        validated: Long,
        missingJson: String,
        timestamp: Long,
    ): Int

    @Query("UPDATE sync_watermarks SET highestAppliedSeq = :appliedSeq, updatedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateAppliedSequence(
        deviceId: String,
        appliedSeq: Long,
        timestamp: Long,
    ): Int

    @Query("DELETE FROM sync_watermarks WHERE deviceId = :deviceId")
    suspend fun deleteForDevice(deviceId: String): Int

    @Query("DELETE FROM sync_watermarks")
    suspend fun clearAll()
}
