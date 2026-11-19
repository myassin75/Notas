package haddad.three.notas.data.drive.model

import kotlinx.serialization.Serializable

/**
 * Metadata for a file returned by Google Drive API v3.
 */
@Serializable
data class DriveFileDto(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val size: String? = null,
    val createdTime: String? = null,
    val modifiedTime: String? = null,
    val properties: Map<String, String>? = null,
    val appProperties: Map<String, String>? = null,
)

/**
 * Paginated response from Google Drive API v3 files.list.
 */
@Serializable
data class DriveFileListResponse(
    val files: List<DriveFileDto> = emptyList(),
    val nextPageToken: String? = null,
)

/**
 * Domain representation of a Google Drive encrypted backup.
 */
data class DriveBackupItem(
    val fileId: String,
    val backupId: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
    val schemaVersion: Int,
    val keyId: Int,
    val notesCount: Int,
    val tasksCount: Int,
    val categoriesCount: Int,
    val attachmentsCount: Int,
    val sourceDevice: String,
    val plainSha256: String,
    val isEncrypted: Boolean = true,
)

/**
 * Encrypted recovery envelope stored in Google Drive appDataFolder.
 * Allows a new device to recover the Master Encryption Key (MEK) using the 12-word recovery phrase.
 */
@Serializable
data class DriveRecoveryEnvelopeDto(
    val format: String = FORMAT_VERSION_1,
    val version: Int = 1,
    val keyId: Int = 1,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val recoveryWrappedMekBase64: String,
) {
    companion object {
        const val FORMAT_VERSION_1 = "NOTAS_RECOVERY_ENVELOPE_V1"
        const val FILE_NAME = "notas_recovery_envelope.json"
    }
}

/**
 * Internal plain manifest stored inside the encrypted backup ZIP archive.
 */
@Serializable
data class DriveBackupManifest(
    val backupId: String,
    val createdAtEpochMillis: Long,
    val schemaVersion: Int,
    val appVersion: String = "1.0.0",
    val notesCount: Int,
    val tasksCount: Int,
    val categoriesCount: Int,
    val attachmentsCount: Int,
    val ownerId: String? = null,
    val sourceDevice: String = "",
    val plainSha256: String = "",
)

/**
 * Mode of restore operation chosen by the user.
 */
enum class RestoreMode {
    /**
     * Completely replaces local user data with the backup contents after creating a local safety snapshot.
     */
    REPLACE,

    /**
     * Non-destructive merge: preserves existing non-conflicting notes, imports missing records,
     * and uses the latest modified timestamp to resolve differences.
     */
    MERGE,
}

/**
 * Result summary returned after a restore operation.
 */
data class RestoreSummary(
    val backupId: String,
    val restoredNotesCount: Int,
    val restoredCategoriesCount: Int,
    val restoredAttachmentsCount: Int,
    val restoreMode: RestoreMode,
    val preRestoreSnapshotPath: String?,
)

/**
 * Result wrapper for Google Drive operations.
 */
sealed interface DriveResult<out T> {
    data class Success<T>(
        val data: T,
    ) : DriveResult<T>

    data class Error(
        val message: String,
        val code: String? = null,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
    ) : DriveResult<Nothing>

    data object Unauthorized : DriveResult<Nothing>

    open class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : DriveResult<Nothing> {
        override fun equals(other: Any?): Boolean = other is RateLimited && retryAfterSeconds == other.retryAfterSeconds

        override fun hashCode(): Int = retryAfterSeconds?.hashCode() ?: 0

        override fun toString(): String = "RateLimited(retryAfterSeconds=$retryAfterSeconds)"

        companion object : RateLimited(null)
    }

    data object Offline : DriveResult<Nothing>
}

@Suppress("UNCHECKED_CAST")
fun <T> DriveResult<*>.toErrorResult(): DriveResult<T> =
    when (this) {
        is DriveResult.Error -> this
        is DriveResult.Unauthorized -> DriveResult.Unauthorized
        is DriveResult.RateLimited -> this
        is DriveResult.Offline -> DriveResult.Offline
        is DriveResult.Success -> error("Cannot convert Success to Error")
    }

/**
 * UI State for Google Drive backup operations.
 */
enum class DriveBackupStatus {
    IDLE,
    AUTHORIZING,
    BACKUP_IN_PROGRESS,
    UPLOADING,
    BACKUP_COMPLETED,
    BACKUP_FAILED,
    RESTORE_IN_PROGRESS,
    DOWNLOADING,
    DECRYPTING,
    APPLYING_RESTORE,
    RESTORE_COMPLETED,
    RESTORE_FAILED,
    DELETING,
}

val DriveBackupStatus.isLoading: Boolean
    get() =
        this == DriveBackupStatus.AUTHORIZING ||
            this == DriveBackupStatus.BACKUP_IN_PROGRESS ||
            this == DriveBackupStatus.UPLOADING ||
            this == DriveBackupStatus.RESTORE_IN_PROGRESS ||
            this == DriveBackupStatus.DOWNLOADING ||
            this == DriveBackupStatus.DECRYPTING ||
            this == DriveBackupStatus.APPLYING_RESTORE ||
            this == DriveBackupStatus.DELETING

data class DriveBackupUiState(
    val status: DriveBackupStatus = DriveBackupStatus.IDLE,
    val isConnected: Boolean = false,
    val connectedAccountEmail: String? = null,
    val progressPercent: Float = 0f,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val backups: List<DriveBackupItem> = emptyList(),
    val isLoadingBackups: Boolean = false,
    val nextPageToken: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequencyDays: Int = 1,
    val autoBackupRequiresWifi: Boolean = true,
)
