package haddad.three.notas.data.sync

import kotlinx.serialization.Serializable

/**
 * Payload sent to POST /api/v1/sync/push.
 */
@Serializable
data class SyncPushRequestDto(
    val deviceId: String,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val categories: List<CategorySyncDto> = emptyList(),
    val notes: List<NoteSyncDto> = emptyList(),
    val settings: UserSettingsSyncDto? = null,
)

/**
 * User settings DTO for sync.
 */
@Serializable
data class UserSettingsSyncDto(
    val themeSettings: Map<String, String>? = null,
    val reminderSettings: Map<String, String>? = null,
    val defaultNoteSettings: Map<String, String>? = null,
    val interactionSettings: Map<String, String>? = null,
    val viewOptionsSettings: Map<String, String>? = null,
    val clientUpdatedAt: String,
)

/**
 * Applied entity count breakdown returned by POST /api/v1/sync/push.
 */
@Serializable
data class SyncAppliedCountDto(
    val categories: Int = 0,
    val notes: Int = 0,
    val settings: Int = 0,
)

/**
 * Individual conflict description returned by POST /api/v1/sync/push.
 */
@Serializable
data class SyncPushConflictDto(
    val entityType: String,
    val id: String,
    val serverRevision: String,
    val reason: String,
)

/**
 * Sanitized record of a push conflict stored locally for observability and UI exposure.
 * Strictly excludes tokens, passwords, and note contents.
 */
@Serializable
data class SyncConflictRecord(
    val entityType: String,
    val entityId: String,
    val serverRevision: String,
    val reason: String,
    val timestamp: Long,
)

/**
 * Response payload from POST /api/v1/sync/push.
 */
@Serializable
data class SyncPushResponseDto(
    val serverRevision: String,
    val appliedCount: SyncAppliedCountDto,
    val conflicts: List<SyncPushConflictDto> = emptyList(),
)

/**
 * Response payload from GET /api/v1/sync/changes.
 */
@Serializable
data class SyncChangesResponseDto(
    val currentServerRevision: String,
    val categories: List<CategorySyncDto> = emptyList(),
    val notes: List<NoteSyncDto> = emptyList(),
    val settings: UserSettingsSyncDto? = null,
    val hasMore: Boolean = false,
)

/**
 * Local tombstone representing a record that was permanently deleted on this client
 * while signed in, pending upload to the backend.
 */
@Serializable
data class SyncTombstone(
    val entityType: String, // "category", "note", "checklist_item", "attachment"
    val uuid: String,
    val deletedAt: Long,
    val noteUuid: String? = null, // Parent note UUID for checklist items & attachments
)

/**
 * Result of a sync operation.
 */
sealed interface SyncResult<out T> {
    data class Success<T>(
        val data: T,
    ) : SyncResult<T>

    data class Error(
        val message: String,
        val code: String? = null,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
    ) : SyncResult<Nothing>

    data object Unauthorized : SyncResult<Nothing>

    data object RateLimited : SyncResult<Nothing>

    data object Offline : SyncResult<Nothing>
}
