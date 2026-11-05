package haddad.three.notas.data.sync.change

import kotlinx.serialization.Serializable

/**
 * Types of entities tracked for continuous synchronization.
 */
enum class EntityType {
    NOTE,
    CATEGORY,
    CHECKLIST_ITEM,
    ATTACHMENT_METADATA,
    USER_PREFERENCE,
}

/**
 * Type of mutation operation.
 */
enum class OperationType {
    UPSERT,
    DELETE,
}

/**
 * Lifecycle status of a pending change.
 */
enum class PendingChangeStatus {
    PENDING,
    JOURNALED,
    SYNCED,
}

/**
 * Origin of a data mutation, used to prevent synchronization echo loops.
 */
enum class OperationOrigin {
    LOCAL,
    REMOTE,
}

/**
 * Domain representation of a synchronization operation.
 */
@Serializable
data class SyncOperation(
    val changeId: String,
    val entityType: EntityType,
    val entityId: String,
    val operationType: OperationType,
    val deviceId: String,
    val createdAt: Long,
    val schemaVersion: Int = 1,
    val payload: String?,
    val hlcTimestamp: String? = null,
    val hlcCounter: Int? = null,
    val hlcNodeId: String? = null,
    val status: PendingChangeStatus = PendingChangeStatus.PENDING,
    val journalId: String? = null,
)

/**
 * Structured payload for Note upsert operations.
 */
@Serializable
data class NoteChangePayload(
    val uuid: String,
    val ownerId: String? = null,
    val kind: String,
    val title: String,
    val body: String,
    val colorIndex: Int,
    val trashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val reminderAt: Long? = null,
    val endAt: Long? = null,
    val isAllDay: Boolean = false,
    val importance: String = "DEFAULT",
    val visibility: String = "DEFAULT",
    val pictureUri: String? = null,
    val pictureHeroFraming: String? = null,
    val locked: Boolean = false,
    val iconKey: String? = null,
    val categoryUuid: String? = null,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val completedAt: Long? = null,
    val pinnedAt: Long? = null,
)

/**
 * Structured payload for Category (Shoulder) upsert operations.
 */
@Serializable
data class CategoryChangePayload(
    val uuid: String,
    val ownerId: String? = null,
    val name: String,
    val normalizedName: String,
    val colorHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Structured payload for Checklist item upsert operations.
 */
@Serializable
data class ChecklistItemChangePayload(
    val uuid: String,
    val noteUuid: String,
    val ownerId: String? = null,
    val text: String,
    val checked: Boolean,
    val sortOrder: Double,
    val parentUuid: String? = null,
    val depth: Int = 0,
    val details: String = "",
)

/**
 * Structured payload for Attachment metadata upsert operations.
 * Note: Only attachment metadata is synchronized in this phase, not attachment binaries.
 */
@Serializable
data class AttachmentChangePayload(
    val uuid: String,
    val noteUuid: String,
    val ownerId: String? = null,
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
)

/**
 * Structured payload for User Preference upsert operations.
 */
@Serializable
data class PreferenceChangePayload(
    val key: String,
    val settingsJson: String,
    val updatedAt: Long,
)

/**
 * Minimal payload for entity deletion operations.
 */
@Serializable
data class DeleteChangePayload(
    val uuid: String,
    val deletedAt: Long,
    val parentUuid: String? = null,
)

