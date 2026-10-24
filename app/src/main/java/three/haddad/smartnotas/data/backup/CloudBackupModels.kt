package three.haddad.smartnotas.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class CreateBackupRequestDto(
    val fileName: String,
    val sizeBytes: Long,
    val sha256Checksum: String,
    val notesCount: Int,
    val tasksCount: Int,
    val eventsCount: Int,
    val categoriesCount: Int,
    val schemaVersion: Int,
    val isEncrypted: Boolean = false,
)

@Serializable
data class CreateBackupResponseDto(
    val backupId: String,
    val uploadUrl: String,
    val expiresInSeconds: Int,
)

@Serializable
data class BackupItemDto(
    val id: String,
    val fileName: String,
    val sizeBytes: String,
    val sha256Checksum: String,
    val notesCount: Int,
    val tasksCount: Int,
    val eventsCount: Int,
    val categoriesCount: Int,
    val schemaVersion: Int,
    val isEncrypted: Boolean,
    val status: String,
    val verifiedAt: String? = null,
    val verificationError: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class VerifyBackupResponseDto(
    val backupId: String,
    val status: String,
    val verifiedAt: String,
    val actualSizeBytes: String,
    val message: String,
)

@Serializable
data class RestoreBackupResponseDto(
    val backupId: String,
    val downloadUrl: String,
    val expiresInSeconds: Int,
    val sha256Checksum: String,
    val sizeBytes: String,
)

@Serializable
data class AttachmentUploadUrlRequestDto(
    val attachmentId: String,
    val noteId: String? = null,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

@Serializable
data class AttachmentUploadUrlResponseDto(
    val attachmentId: String,
    val uploadUrl: String,
    val storageKey: String,
    val expiresInSeconds: Int,
)

@Serializable
data class AttachmentVerifyResponseDto(
    val attachmentId: String,
    val storageKey: String,
    val sizeBytes: String,
    val status: String,
    val verifiedAt: String,
)

@Serializable
data class AttachmentDownloadUrlResponseDto(
    val attachmentId: String,
    val downloadUrl: String,
    val expiresInSeconds: Int,
    val mimeType: String? = null,
    val displayName: String? = null,
)

sealed interface CloudBackupResult<out T> {
    data class Success<T>(val data: T) : CloudBackupResult<T>
    data class Error(
        val message: String,
        val code: String? = null,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
    ) : CloudBackupResult<Nothing>
    data object Unauthorized : CloudBackupResult<Nothing>
    data object RateLimited : CloudBackupResult<Nothing>
    data object Offline : CloudBackupResult<Nothing>
}

@Suppress("UNCHECKED_CAST")
fun <T> CloudBackupResult<*>.toErrorResult(): CloudBackupResult<T> = when (this) {
    is CloudBackupResult.Error -> this
    is CloudBackupResult.Unauthorized -> CloudBackupResult.Unauthorized
    is CloudBackupResult.RateLimited -> CloudBackupResult.RateLimited
    is CloudBackupResult.Offline -> CloudBackupResult.Offline
    is CloudBackupResult.Success -> error("Cannot convert Success to Error")
}

enum class CloudBackupStatus {
    IDLE,
    BACKUP_IN_PROGRESS,
    VERIFYING_BACKUP,
    BACKUP_COMPLETED,
    BACKUP_FAILED,
    RESTORE_IN_PROGRESS,
    RESTORE_COMPLETED,
    RESTORE_FAILED,
}

data class CloudBackupUiState(
    val status: CloudBackupStatus = CloudBackupStatus.IDLE,
    val progressPercent: Float = 0f,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val backups: List<BackupItemDto> = emptyList(),
    val isLoadingBackups: Boolean = false,
)

val CloudBackupStatus.isLoading: Boolean
    get() = this == CloudBackupStatus.BACKUP_IN_PROGRESS ||
        this == CloudBackupStatus.VERIFYING_BACKUP ||
        this == CloudBackupStatus.RESTORE_IN_PROGRESS
