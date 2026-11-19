package haddad.three.notas.data.sync.transport

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Status of an incoming remote sync journal in the local inbox.
 */
object InboxStatus {
    const val VALIDATED = "VALIDATED"
    const val APPLYING = "APPLYING"
    const val APPLIED = "APPLIED"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
}

/**
 * Persists downloaded, cryptographically verified remote journals in a durable local inbox.
 * Remote operations are strictly NOT applied to Room in Phase 4C; they are securely held here for Phase 4D.
 */
@Entity(
    tableName = "sync_inbox",
    indices = [
        Index(value = ["deviceId", "sequence"], unique = true),
        Index("status"),
        Index("downloadedAt"),
    ],
)
data class SyncInboxEntity(
    @PrimaryKey
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val formatVersion: Int,
    val driveFileId: String,
    val localFilePath: String,
    val contentSha256: String,
    val operationCount: Int,
    val downloadedAt: Long = System.currentTimeMillis(),
    val status: String = InboxStatus.VALIDATED,
)

/**
 * Tracks persistent per-device transport watermarks.
 * Monotonically records discovered, downloaded, and validated sequence progression,
 * while detecting sequence gaps without skipping them.
 */
@Entity(tableName = "sync_watermarks")
data class SyncWatermarkEntity(
    @PrimaryKey
    val deviceId: String,
    val highestDiscoveredSeq: Long = 0L,
    val highestDownloadedSeq: Long = 0L,
    val highestValidatedSeq: Long = 0L,
    val highestAppliedSeq: Long = 0L,
    val missingSequencesJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Metadata associated with a sync journal file uploaded to Google Drive.
 * Stored strictly in Google Drive file `appProperties` (invisible to outside apps and note content is never exposed).
 */
data class JournalTransportMetadata(
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val formatVersion: Int,
    val contentSha256: String,
    val operationCount: Int,
    val uploadedAt: Long = System.currentTimeMillis(),
)

/**
 * Remote journal file header discovered in Google Drive `appDataFolder`.
 */
data class RemoteJournalHeader(
    val driveFileId: String,
    val journalId: String,
    val deviceId: String,
    val sequence: Long,
    val formatVersion: Int,
    val contentSha256: String,
    val operationCount: Int,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
)

/**
 * Paginated result of discovered remote journals.
 */
data class RemoteJournalsPage(
    val journals: List<RemoteJournalHeader>,
    val nextPageToken: String?,
)

/**
 * Result of uploading a journal to Google Drive.
 */
sealed interface JournalUploadResult {
    data class Success(
        val driveFileId: String,
        val alreadyExisted: Boolean = false,
    ) : JournalUploadResult

    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
    ) : JournalUploadResult

    data object Unauthorized : JournalUploadResult

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : JournalUploadResult
}

/**
 * Batch upload summary.
 */
data class JournalUploadBatchResult(
    val totalJournals: Int,
    val successfulJournals: Int,
    val failedJournals: Int,
    val errors: List<String> = emptyList(),
    val rateLimitRetryAfterSeconds: Long? = null,
)

/**
 * Result of downloading a remote journal from Google Drive.
 */
sealed interface JournalDownloadResult {
    data class Success(
        val inboxEntity: SyncInboxEntity,
    ) : JournalDownloadResult

    data class SkippedAlreadyDownloaded(
        val journalId: String,
    ) : JournalDownloadResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : JournalDownloadResult

    data object Unauthorized : JournalDownloadResult

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : JournalDownloadResult
}

/**
 * Batch download summary.
 */
data class JournalDownloadBatchResult(
    val totalDiscovered: Int,
    val downloadedAndValidated: Int,
    val skippedAlreadyDownloaded: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
    val rateLimitRetryAfterSeconds: Long? = null,
)

/**
 * Discovery result.
 */
sealed interface DiscoveryResult {
    data class Success(
        val discoveredJournalsByDevice: Map<String, List<RemoteJournalHeader>>,
    ) : DiscoveryResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : DiscoveryResult

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : DiscoveryResult

    data object Unauthorized : DiscoveryResult
}

/**
 * Result of startup/crash reconciliation across local inbox files and Room database.
 */
data class InboxReconciliationResult(
    val purgedStagingFiles: Int,
    val missingFilesCleaned: Int,
    val orphanFilesReconciled: Int,
    val errors: List<String> = emptyList(),
)

// Structured Exceptions for Integrity, Security & Schema Failures
class ConflictingJournalException(
    message: String,
) : SecurityException(message)

class InvalidEncryptedContainerException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

class InvalidMekException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

class UnsupportedJournalVersionException(
    message: String,
) : IllegalArgumentException(message)

class MalformedJournalException(
    message: String,
) : IllegalArgumentException(message)

class ContentHashMismatchException(
    message: String,
) : SecurityException(message)

class MissingSequenceException(
    message: String,
) : IllegalStateException(message)

class DriveAuthorizationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
