package haddad.three.notas.data.sync.transport

import androidx.room.withTransaction
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.journal.JournalStatus
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates uploading local immutable encrypted journals to Google Drive.
 *
 * Requirements enforced:
 * - Only uploads journals in [JournalStatus.READY_TO_UPLOAD] state.
 * - Never uploads plaintext note or journal data.
 * - Atomically marks journal as UPLOADED and pending changes as SYNCED upon verified upload.
 * - Records upload failures and handles uncertain responses without creating duplicates.
 */
@Singleton
class JournalUploader @Inject constructor(
    private val journalDao: SyncJournalDao,
    private val pendingChangeDao: PendingChangeDao,
    private val transport: GoogleDriveSyncTransport,
    private val database: RememberDatabase?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Uploads all local journals that are ready for upload, ordered monotonically by sequence.
     */
    suspend fun uploadReadyJournals(deviceId: String): JournalUploadBatchResult = withContext(ioDispatcher) {
        val readyJournals = journalDao.getReadyToUpload().filter { it.deviceId == deviceId }
        var successCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        var rateLimitRetryAfterSeconds: Long? = null

        for (journal in readyJournals) {
            when (val uploadResult = uploadJournal(journal)) {
                is JournalUploadResult.Success -> {
                    successCount++
                }
                is JournalUploadResult.Failure -> {
                    failedCount++
                    errors.add("Journal ${journal.journalId} (seq ${journal.sequence}) failed: ${uploadResult.message}")
                    // Stop further uploads for this device to preserve sequence ordering in transport
                    break
                }
                is JournalUploadResult.Unauthorized -> {
                    failedCount++
                    errors.add("Google Drive unauthorized during journal ${journal.journalId} upload")
                    break
                }
                is JournalUploadResult.RateLimited -> {
                    failedCount++
                    rateLimitRetryAfterSeconds = uploadResult.retryAfterSeconds
                    errors.add("Google Drive rate limited during journal ${journal.journalId} upload")
                    break
                }
            }
        }

        JournalUploadBatchResult(
            totalJournals = readyJournals.size,
            successfulJournals = successCount,
            failedJournals = failedCount,
            errors = errors,
            rateLimitRetryAfterSeconds = rateLimitRetryAfterSeconds,
        )
    }

    /**
     * Uploads a single journal file, verifying local integrity and committing cloud state.
     */
    suspend fun uploadJournal(journal: SyncJournalEntity): JournalUploadResult = withContext(ioDispatcher) {
        if (journal.status != JournalStatus.READY_TO_UPLOAD.name) {
            return@withContext JournalUploadResult.Failure(
                "Cannot upload journal ${journal.journalId}: status is ${journal.status}, expected READY_TO_UPLOAD"
            )
        }

        val journalFile = File(journal.storagePath)
        if (!journalFile.exists() || journalFile.length() == 0L) {
            val err = "Encrypted journal file not found on disk: ${journal.storagePath}"
            recordFailure(journal.journalId, err)
            return@withContext JournalUploadResult.Failure(err)
        }

        val contentSha256 = transport.computeSha256(journalFile)

        val metadata = JournalTransportMetadata(
            journalId = journal.journalId,
            deviceId = journal.deviceId,
            sequence = journal.sequence,
            formatVersion = journal.formatVersion,
            contentSha256 = contentSha256,
            operationCount = journal.operationCount,
            uploadedAt = System.currentTimeMillis(),
        )

        val result = try {
            transport.uploadJournal(journalFile, metadata)
        } catch (e: Exception) {
            recordFailure(journal.journalId, e.message ?: "Unknown upload error")
            return@withContext JournalUploadResult.Failure("Upload exception: ${e.message}", cause = e)
        }

        when (result) {
            is JournalUploadResult.Success -> {
                commitUploadSuccess(journal.journalId, result.driveFileId)
                result
            }
            is JournalUploadResult.Failure -> {
                recordFailure(journal.journalId, result.message)
                result
            }
            is JournalUploadResult.Unauthorized, is JournalUploadResult.RateLimited -> {
                recordFailure(journal.journalId, result::class.simpleName ?: "Auth or rate error")
                result
            }
        }
    }

    private suspend fun commitUploadSuccess(journalId: String, driveFileId: String) {
        val now = System.currentTimeMillis()
        val updateBlock: suspend () -> Unit = {
            journalDao.markUploaded(
                journalId = journalId,
                driveFileId = driveFileId,
                newStatus = JournalStatus.UPLOADED.name,
                timestamp = now,
            )
            pendingChangeDao.markChangesSynced(journalId)
        }

        if (database != null) {
            database.withTransaction { updateBlock() }
        } else {
            updateBlock()
        }
    }

    private suspend fun recordFailure(journalId: String, error: String) {
        val now = System.currentTimeMillis()
        journalDao.recordUploadFailure(
            journalId = journalId,
            newStatus = JournalStatus.FAILED.name,
            error = error,
            timestamp = now,
        )
    }
}

