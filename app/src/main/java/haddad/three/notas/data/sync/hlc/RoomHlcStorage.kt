package haddad.three.notas.data.sync.hlc

import haddad.three.notas.data.sync.conflict.SyncHlcStateDao
import haddad.three.notas.data.sync.conflict.SyncHlcStateEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHlcStorage @Inject constructor(
    private val hlcStateDao: SyncHlcStateDao,
) : HlcStorage {
    override suspend fun loadState(nodeId: String): Pair<Long, Int>? {
        val state = hlcStateDao.getState(nodeId) ?: return null
        return state.physicalTime to state.logicalCounter
    }

    override suspend fun saveState(nodeId: String, physicalTime: Long, logicalCounter: Int) {
        hlcStateDao.insertOrUpdate(
            SyncHlcStateEntity(
                nodeId = nodeId,
                physicalTime = physicalTime,
                logicalCounter = logicalCounter,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}

