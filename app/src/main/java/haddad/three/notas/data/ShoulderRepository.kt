package haddad.three.notas.data

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
            return shoulderDao.insertShoulder(entity)
        }

        suspend fun updateShoulder(shoulder: ShoulderEntity) {
            shoulderDao.updateShoulder(shoulder.copy(updatedAt = System.currentTimeMillis()))
        }

        suspend fun deleteShoulder(shoulder: ShoulderEntity) {
            tombstoneRecorder?.recordShoulderDeleted(shoulder.uuid)
            shoulderDao.deleteShoulder(shoulder)
        }
    }
