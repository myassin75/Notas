package haddad.three.notas.data.sync.journal

import android.content.Context
import androidx.room.withTransaction
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of creating an encrypted immutable synchronization journal.
 */
sealed interface JournalCreationResult {
    data class Success(val journal: SyncJournalEntity, val journalDto: SyncJournalDto) : JournalCreationResult
    data object NoEligibleChanges : JournalCreationResult
    data class Error(val message: String, val cause: Throwable? = null) : JournalCreationResult
}

/**
 * Creates, serializes, encrypts, and durably persists immutable sync journals
 * from eligible pending changes.
 */
@Singleton
class SyncJournalWriter @Inject constructor(
    private val context: Context,
    private val database: RememberDatabase?,
    private val pendingChangeDao: PendingChangeDao,
    private val sequenceDao: SyncSequenceDao,
    private val journalDao: SyncJournalDao,
    private val mekManager: MekManager,
    private val deviceIdProvider: suspend () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val writerMutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    companion object {
        const val DEFAULT_MAX_OPERATIONS = 100
        const val MAX_BATCH_SIZE_BYTES = 1_000_000L // 1 MB limit per journal payload
        const val JOURNALS_DIR_NAME = "sync_journals"

        fun buildAad(formatVersion: Int, journalId: String, deviceId: String, sequence: Long): ByteArray =
            "formatVersion=$formatVersion&journalId=$journalId&deviceId=$deviceId&sequence=$sequence"
                .toByteArray(Charsets.UTF_8)
    }

    /**
     * Gathers pending changes, groups them into a bounded batch, allocates a sequence,
     * serializes and encrypts the journal, and atomically commits the journal and updates
     * pending change statuses.
     */
    suspend fun createNextJournal(
        maxOperations: Int = DEFAULT_MAX_OPERATIONS,
        customDeviceId: String? = null,
    ): JournalCreationResult = withContext(Dispatchers.IO) {
        writerMutex.withLock {
            val deviceId = customDeviceId ?: deviceIdProvider()
            val pendingChanges = pendingChangeDao.getPendingChanges(maxOperations)

            if (pendingChanges.isEmpty()) {
                return@withLock JournalCreationResult.NoEligibleChanges
            }

            // 1. Validate operations and detect duplicates
            val seenChangeIds = mutableSetOf<String>()
            val validOperations = mutableListOf<JournalOperationDto>()
            var accumulatedSizeBytes = 0L

            for (change in pendingChanges) {
                if (!seenChangeIds.add(change.changeId)) {
                    // Duplicate changeId detected: skip duplicate to enforce uniqueness
                    continue
                }

                val payloadSize = change.payload?.toByteArray(Charsets.UTF_8)?.size ?: 0
                if (validOperations.isNotEmpty() && (accumulatedSizeBytes + payloadSize) > MAX_BATCH_SIZE_BYTES) {
                    // Reached max batch size: stop batching to enforce byte boundaries
                    break
                }

                accumulatedSizeBytes += payloadSize
                validOperations.add(
                    JournalOperationDto(
                        changeId = change.changeId,
                        entityType = change.entityType,
                        entityId = change.entityId,
                        operationType = change.operationType,
                        schemaVersion = change.schemaVersion,
                        createdAt = change.createdAt,
                        payload = change.payload,
                        hlcTimestamp = change.hlcTimestamp,
                        hlcCounter = change.hlcCounter,
                        hlcNodeId = change.hlcNodeId,
                    ),
                )
            }

            if (validOperations.isEmpty()) {
                return@withLock JournalCreationResult.NoEligibleChanges
            }

            val journalId = UUID.randomUUID().toString()
            val now = clock()

            // 2. Obtain active Master Encryption Key (MEK)
            val mek = mekManager.getActiveMasterKey()
                ?: return@withLock JournalCreationResult.Error("Master Encryption Key is not initialized on this device.")

            // 3. Allocate next device sequence atomically
            val sequence = if (database != null) {
                database.withTransaction {
                    sequenceDao.allocateNextSequence(deviceId, now)
                }
            } else {
                sequenceDao.allocateNextSequence(deviceId, now)
            }

            // 4. Construct Journal DTO
            val journalDto = SyncJournalDto(
                formatVersion = SyncJournalDto.CURRENT_FORMAT_VERSION,
                journalId = journalId,
                deviceId = deviceId,
                sequence = sequence,
                createdAt = now,
                operationCount = validOperations.size,
                operations = validOperations,
            )

            // 5. Serialize deterministically
            val plainBytes = json.encodeToString(journalDto).toByteArray(Charsets.UTF_8)

            // 6. Encrypt with AES-256-GCM via existing EncryptedContainer
            val aad = buildAad(journalDto.formatVersion, journalId, deviceId, sequence)
            val encryptedBytes = try {
                EncryptedContainer.encrypt(
                    plaintext = plainBytes,
                    key = mek,
                    aad = aad,
                )
            } catch (e: Exception) {
                return@withLock JournalCreationResult.Error("Failed to encrypt sync journal: ${e.message}", cause = e)
            }

            // 7. Persist encrypted journal file to local storage
            val baseDir = File(context.filesDir, JOURNALS_DIR_NAME)
            val deviceDir = File(baseDir, deviceId).apply { mkdirs() }
            val journalFile = File(deviceDir, "${sequence}_$journalId.journal")
            val tempFile = File(deviceDir, "${sequence}_$journalId.tmp")

            try {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(encryptedBytes)
                    fos.flush()
                }
                if (!tempFile.renameTo(journalFile)) {
                    tempFile.copyTo(journalFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                tempFile.delete()
                journalFile.delete()
                return@withLock JournalCreationResult.Error("Failed to write encrypted journal to disk: ${e.message}", cause = e)
            }

            // 8. Commit journal metadata and transition pending changes state in an atomic transaction
            val journalEntity = SyncJournalEntity(
                journalId = journalId,
                deviceId = deviceId,
                sequence = sequence,
                formatVersion = journalDto.formatVersion,
                createdAt = now,
                operationCount = validOperations.size,
                storagePath = journalFile.absolutePath,
                encryptedSizeBytes = journalFile.length(),
                status = JournalStatus.READY_TO_UPLOAD.name,
                uploadAttempts = 0,
                lastError = null,
                updatedAt = now,
            )

            val committed: Boolean = try {
                val operationBlock: suspend () -> Unit = {
                    journalDao.insert(journalEntity)
                    pendingChangeDao.markChangesJournaled(
                        changeIds = validOperations.map { it.changeId },
                        journalId = journalId,
                    )
                }

                if (database != null) {
                    database.withTransaction { operationBlock() }
                } else {
                    operationBlock()
                }
                true
            } catch (e: Exception) {
                // If database commit fails, delete disk file so we don't leave orphaned files
                journalFile.delete()
                return@withLock JournalCreationResult.Error("Failed to commit journal metadata: ${e.message}", cause = e)
            }

            JournalCreationResult.Success(journalEntity, journalDto)
        }
    }
}

