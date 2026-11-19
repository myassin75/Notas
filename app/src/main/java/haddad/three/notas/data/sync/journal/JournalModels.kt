package haddad.three.notas.data.sync.journal

import kotlinx.serialization.Serializable

/**
 * Publication lifecycle status of an encrypted synchronization journal.
 */
enum class JournalStatus {
    CREATED,
    READY_TO_UPLOAD,
    UPLOADED,
    FAILED,
}

/**
 * Single operation entry inside an immutable journal.
 */
@Serializable
data class JournalOperationDto(
    val changeId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val payload: String?,
    val hlcTimestamp: String? = null,
    val hlcCounter: Int? = null,
    val hlcNodeId: String? = null,
)

/**
 * Structured, deterministic payload of a device-partitioned sync journal.
 * Once created and serialized, it is encrypted via EncryptedContainer and stored immutably.
 */
@Serializable
data class SyncJournalDto(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val createdAt: Long,
    val operationCount: Int,
    val operations: List<JournalOperationDto>,
    val hlcTimestamp: String? = null,
    val hlcCounter: Int? = null,
    val hlcNodeId: String? = null,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}
