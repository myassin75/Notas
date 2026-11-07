package haddad.three.notas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoulderDao {
    @Query("SELECT * FROM shoulders ORDER BY name COLLATE NOCASE ASC")
    fun observeAllShoulders(): Flow<List<ShoulderEntity>>

    @Query("SELECT * FROM shoulders ORDER BY name COLLATE NOCASE ASC")
    suspend fun allShoulders(): List<ShoulderEntity>

    @Query("SELECT * FROM shoulders WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE ASC")
    suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity>

    @Query("SELECT * FROM shoulders WHERE id IN (SELECT DISTINCT shoulderId FROM notes WHERE shoulderId IS NOT NULL) ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveShoulders(): Flow<List<ShoulderEntity>>

    @Query("SELECT * FROM shoulders WHERE id = :id")
    suspend fun getShoulderById(id: Long): ShoulderEntity?

    @Query("SELECT * FROM shoulders WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ShoulderEntity?

    @Query("SELECT * FROM shoulders WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity?

    @Query("SELECT id FROM notes WHERE shoulderId = :shoulderId")
    suspend fun noteIdsForShoulder(shoulderId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoulder(shoulder: ShoulderEntity): Long

    @Update
    suspend fun updateShoulder(shoulder: ShoulderEntity)

    @Delete
    suspend fun deleteShoulder(shoulder: ShoulderEntity)

    @Query("UPDATE notes SET shoulderId = :shoulderId WHERE id = :noteId")
    suspend fun setShoulderForNote(
        noteId: Long,
        shoulderId: Long?,
    )

    @Query("UPDATE shoulders SET ownerId = :targetOwnerId WHERE ownerId IS NULL")
    suspend fun claimGuestShoulders(targetOwnerId: String): Int
}
