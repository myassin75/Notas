package haddad.three.notas.data

import androidx.room.withTransaction
import haddad.three.notas.data.sync.change.LocalChangeRecorder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoulderRepository
    @Inject
    constructor(
        private val shoulderDao: ShoulderDao,
        private val tombstoneRecorder: haddad.three.notas.data.sync.SyncTombstoneRecorder? = null,
        private val authRepository: haddad.three.notas.data.auth.AuthRepository? = null,
        private val database: RememberDatabase? = null,
        private val changeRecorder: LocalChangeRecorder? = null,
    ) {
        fun getAllShoulders(): Flow<List<ShoulderEntity>> = shoulderDao.observeAllShoulders()

        suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulderDao.getShoulderById(id)

        suspend fun createShoulder(
            name: String,
            colorHex: String? = null,
            timestamp: Long = System.currentTimeMillis(),
        ): Long {
            val entity =
                ShoulderEntity(
                    name = name,
                    normalizedName = name.trim().lowercase(),
                    colorHex = colorHex,
                    ownerId = authRepository?.currentUser?.uid,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
            val block: suspend () -> Long = {
                val id = shoulderDao.insertShoulder(entity)
                changeRecorder?.recordCategoryUpsert(entity.copy(id = id))
                id
            }
            return if (database != null) database.withTransaction { block() } else block()
        }

        suspend fun updateShoulder(shoulder: ShoulderEntity) {
            val updated = shoulder.copy(updatedAt = System.currentTimeMillis())
            val block: suspend () -> Unit = {
                shoulderDao.updateShoulder(updated)
                changeRecorder?.recordCategoryUpsert(updated)
            }
            if (database != null) database.withTransaction { block() } else block()
        }

        suspend fun deleteShoulder(shoulder: ShoulderEntity) {
            val block: suspend () -> Unit = {
                tombstoneRecorder?.recordShoulderDeleted(shoulder.uuid)
                changeRecorder?.recordCategoryDelete(shoulder.uuid)
                shoulderDao.deleteShoulder(shoulder)
            }
            if (database != null) database.withTransaction { block() } else block()
        }
    }
