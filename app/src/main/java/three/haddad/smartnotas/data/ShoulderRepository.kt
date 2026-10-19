package three.haddad.smartnotas.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoulderRepository
    @Inject
    constructor(
        private val shoulderDao: ShoulderDao,
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
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
            return shoulderDao.insertShoulder(entity)
        }

        suspend fun updateShoulder(shoulder: ShoulderEntity) {
            shoulderDao.updateShoulder(shoulder.copy(updatedAt = System.currentTimeMillis()))
        }

        suspend fun deleteShoulder(shoulder: ShoulderEntity) {
            shoulderDao.deleteShoulder(shoulder)
        }
    }
