package three.haddad.smartnotas.data.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import three.haddad.smartnotas.data.AttachmentDao
import three.haddad.smartnotas.data.NoteAttachmentEntity
import three.haddad.smartnotas.data.auth.AuthRepository
import three.haddad.smartnotas.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure cloud storage for note attachments, images, and audio files.
 * Guarantees:
 * - Direct upload/download via short-lived presigned URLs.
 * - Objects are strictly scoped to the authenticated user's private storage path.
 * - Local files and metadata are preserved if cloud upload fails.
 */
@Singleton
class AttachmentStorageManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val backupApiClient: BackupApiClient,
    private val attachmentDao: AttachmentDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Uploads a local attachment file to private cloud storage.
     * Preserves local file and metadata if upload fails.
     */
    suspend fun uploadAttachmentFile(
        attachment: NoteAttachmentEntity,
        localFile: File,
    ): CloudBackupResult<AttachmentVerifyResponseDto> = withContext(ioDispatcher) {
        val user = authRepository.currentUser
            ?: return@withContext CloudBackupResult.Unauthorized

        if (!localFile.exists() || localFile.length() <= 0) {
            return@withContext CloudBackupResult.Error("Local attachment file does not exist or is empty")
        }

        val idToken = authRepository.getIdToken()
            ?: return@withContext CloudBackupResult.Unauthorized

        val mimeType = attachment.mimeType ?: "application/octet-stream"

        // 1. Request presigned upload URL from backend
        val urlResult = backupApiClient.getAttachmentUploadUrl(
            idToken = idToken,
            payload = AttachmentUploadUrlRequestDto(
                attachmentId = attachment.uuid,
                displayName = attachment.displayName,
                mimeType = mimeType,
                sizeBytes = localFile.length(),
            ),
        )

        if (urlResult !is CloudBackupResult.Success) {
            return@withContext urlResult.toErrorResult()
        }

        val uploadInfo = urlResult.data

        // 2. Stream file directly to S3/R2 presigned URL
        val s3Result = backupApiClient.uploadFileToPresignedUrl(
            presignedUrl = uploadInfo.uploadUrl,
            file = localFile,
            contentType = mimeType,
        )

        if (s3Result !is CloudBackupResult.Success) {
            return@withContext s3Result.toErrorResult()
        }

        // 3. Confirm and verify storage object on backend
        backupApiClient.verifyAttachment(
            idToken = idToken,
            attachmentId = attachment.uuid,
        )
    }

    /**
     * Downloads a private cloud attachment file to the specified target file.
     */
    suspend fun downloadAttachmentFile(
        attachmentId: String,
        targetFile: File,
    ): CloudBackupResult<File> = withContext(ioDispatcher) {
        val user = authRepository.currentUser
            ?: return@withContext CloudBackupResult.Unauthorized

        val idToken = authRepository.getIdToken()
            ?: return@withContext CloudBackupResult.Unauthorized

        // 1. Get presigned download URL from backend
        val urlResult = backupApiClient.getAttachmentDownloadUrl(
            idToken = idToken,
            attachmentId = attachmentId,
        )

        if (urlResult !is CloudBackupResult.Success) {
            return@withContext urlResult.toErrorResult()
        }

        // 2. Stream file from S3 directly
        val downloadResult = backupApiClient.downloadFileFromPresignedUrl(
            presignedUrl = urlResult.data.downloadUrl,
            targetFile = targetFile,
        )

        if (downloadResult is CloudBackupResult.Success) {
            CloudBackupResult.Success(targetFile)
        } else {
            downloadResult.toErrorResult()
        }
    }

    /**
     * Deletes a private cloud attachment object.
     */
    suspend fun deleteRemoteAttachment(
        attachmentId: String,
    ): CloudBackupResult<Unit> = withContext(ioDispatcher) {
        val user = authRepository.currentUser
            ?: return@withContext CloudBackupResult.Unauthorized

        val idToken = authRepository.getIdToken()
            ?: return@withContext CloudBackupResult.Unauthorized

        backupApiClient.deleteAttachment(idToken, attachmentId)
    }
}
