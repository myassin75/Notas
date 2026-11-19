package haddad.three.notas.data.sync.snapshot

import kotlinx.serialization.Serializable

/**
 * Manifest header describing snapshot container metadata, coverage vector,
 * database compatibility, and binary attachment inventory.
 */
@Serializable
data class SyncSnapshotManifest(
    val formatVersion: Int = CURRENT_SNAPSHOT_FORMAT_VERSION,
    val databaseVersion: Int = CURRENT_DATABASE_VERSION,
    val snapshotId: String,
    val accountEmail: String,
    val accountId: String? = null,
    val creatorDeviceId: String,
    val createdAtEpochMillis: Long,
    val snapshotHlc: String,
    val coverageVector: Map<String, Long>,
    val attachments: List<SnapshotAttachmentMetadata> = emptyList(),
    val plainArchiveSha256: String = "",
) {
    companion object {
        const val CURRENT_SNAPSHOT_FORMAT_VERSION = 1
        const val CURRENT_DATABASE_VERSION = 13
        const val MANIFEST_ENTRY_NAME = "manifest.json"
        const val ENTITIES_ENTRY_NAME = "entities.json"
        const val ATTACHMENTS_DIR_NAME = "attachments"
    }
}

/**
 * Metadata for a binary attachment packaged inside the snapshot archive.
 */
@Serializable
data class SnapshotAttachmentMetadata(
    val attachmentUuid: String,
    val noteUuid: String,
    val displayName: String,
    val mimeType: String? = null,
    val entryPath: String, // e.g. "attachments/uuid_name"
    val sha256Hex: String,
    val sizeBytes: Long,
)

/**
 * Complete canonical export of Room entities, conflict resolution metadata,
 * tombstones, and exact applied-operations ledger.
 */
@Serializable
data class SyncSnapshotEntitiesDto(
    val notes: List<haddad.three.notas.data.sync.change.NoteChangePayload>,
    val checklistItems: List<haddad.three.notas.data.sync.change.ChecklistItemChangePayload>,
    val categories: List<haddad.three.notas.data.sync.change.CategoryChangePayload>,
    val attachments: List<haddad.three.notas.data.sync.change.AttachmentChangePayload>,
    val conflictMetadata: List<SnapshotConflictMetadataDto>,
    val tombstones: List<SnapshotTombstoneDto>,
    val unresolvedConflicts: List<SnapshotConflictRecordDto>,
    val appliedOperations: List<SnapshotAppliedOperationDto>,
)

@Serializable
data class SnapshotConflictMetadataDto(
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val hlcTimestamp: String,
    val updatedAt: Long,
)

@Serializable
data class SnapshotTombstoneDto(
    val entityType: String,
    val entityId: String,
    val deletedAt: Long,
    val hlcTimestamp: String,
    val deviceId: String,
)

@Serializable
data class SnapshotConflictRecordDto(
    val conflictId: String,
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val winningValue: String?,
    val losingValue: String?,
    val winningHlc: String,
    val losingHlc: String,
    val winningDeviceId: String,
    val losingDeviceId: String,
    val detectedAt: Long,
    val resolved: Boolean = false,
)

@Serializable
data class SnapshotAppliedOperationDto(
    val changeId: String,
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val entityType: String,
    val entityId: String,
    val appliedAt: Long,
    val hlcTimestamp: String,
)

/**
 * Durable promotion manifest written to disk before Room commit
 * to guarantee crash-safe binary promotion recovery.
 */
@Serializable
data class AttachmentPromotionManifest(
    val snapshotId: String,
    val stagingDirPath: String,
    val targetDirPath: String,
    val createdAtEpochMillis: Long,
    val entries: List<AttachmentPromotionFileEntry>,
)

@Serializable
data class AttachmentPromotionFileEntry(
    val attachmentUuid: String,
    val noteId: Long,
    val stagingFileName: String,
    val targetFileName: String,
    val sha256Hex: String,
    val sizeBytes: Long,
)

// Exceptions for Structured Error Handling
class UnsupportedSnapshotVersionException(
    message: String,
) : IllegalArgumentException(message)

class CorruptSnapshotException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

class IncompleteSnapshotException(
    message: String,
) : IllegalStateException(message)

class AttachmentIntegrityException(
    message: String,
) : SecurityException(message)
