package haddad.three.notas.data.sync.change

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual local mutation waiting to be batched
 * into an immutable synchronization journal.
 */
@Entity(
    tableName = "pending_changes",
    indices = [
        Index("createdAt"),
        Index(value = ["entityType", "entityId"]),
        Index("status"),
        Index("journalId"),
    ],
)
data class PendingChangeEntity(
    @PrimaryKey
    val changeId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val deviceId: String,
    val createdAt: Long,
    val schemaVersion: Int = 1,
    val payload: String?,
    val hlcTimestamp: String? = null,
    val hlcCounter: Int? = null,
    val hlcNodeId: String? = null,
    val status: String = PendingChangeStatus.PENDING.name,
    val journalId: String? = null,
) {
    fun toSyncOperation(): SyncOperation =
        SyncOperation(
            changeId = changeId,
            entityType = EntityType.valueOf(entityType),
            entityId = entityId,
            operationType = OperationType.valueOf(operationType),
            deviceId = deviceId,
            createdAt = createdAt,
            schemaVersion = schemaVersion,
            payload = payload,
            hlcTimestamp = hlcTimestamp,
            hlcCounter = hlcCounter,
            hlcNodeId = hlcNodeId,
            status = runCatching { PendingChangeStatus.valueOf(status) }.getOrDefault(PendingChangeStatus.PENDING),
            journalId = journalId,
        )

    companion object {
        fun fromSyncOperation(op: SyncOperation): PendingChangeEntity =
            PendingChangeEntity(
                changeId = op.changeId,
                entityType = op.entityType.name,
                entityId = op.entityId,
                operationType = op.operationType.name,
                deviceId = op.deviceId,
                createdAt = op.createdAt,
                schemaVersion = op.schemaVersion,
                payload = op.payload,
                hlcTimestamp = op.hlcTimestamp,
                hlcCounter = op.hlcCounter,
                hlcNodeId = op.hlcNodeId,
                status = op.status.name,
                journalId = op.journalId,
            )
    }
}
