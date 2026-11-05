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
 * using a crash-safe state machine.
 *
 * Guarantees:
 * 1. Monotonicity: Journal sequences strictly advance without gaps.
 * 2. Crash-Safety: Failures during encryption, filesystem writes, or process death
 *    cannot permanently consume or skip a sequence number.
 * 3. Durable Reconciliation: Stale or interrupted journal states are reconciled before
 *    new allocations.
 * 4. Immutability: Journal payload, journalId, and changeId bindings are fixed and tamper-evident.
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
     * Reconciles any interrupted or stale journal creation state for [deviceId].
     * 1. If any journal is stuck in [JournalStatus.CREATED], checks disk file:
     *    - If valid & decryptable: commits promotion to [JournalStatus.READY_TO_UPLOAD].
     *    - If missing or corrupted: rolls back pending changes and deletes the orphan journal record.
     * 2. Checks sequence counter consistency: if lastAllocatedSequence exceeds committed journals
     *    and no valid journal exists on disk, reclaims the uncommitted sequence.
     */
    suspend fun reconcileIncompleteState(deviceId: String, timestamp: Long = clock()) = withContext(Dispatchers.IO) {
        // 1. Reconcile CREATED status journals
        val createdJournals = journalDao.getCreatedJournals(deviceId)
        for (journal in createdJournals) {
            val file = File(journal.storagePath)
            var isValid = false
            var decodedDto: SyncJournalDto? = null

            if (file.exists() && file.length() > 0L) {
                try {
                    val key = mekManager.getActiveMasterKey()
                    if (key != null) {
                        val encryptedBytes = file.readBytes()
                        val aad = buildAad(journal.formatVersion, journal.journalId, journal.deviceId, journal.sequence)
                        val decrypted = EncryptedContainer.decrypt(encryptedBytes, key, expectedAad = aad)
                        decodedDto = json.decodeFromString<SyncJournalDto>(String(decrypted, Charsets.UTF_8))
                        isValid = true
                    }
                } catch (_: Exception) {
                    isValid = false
                }
            }

            if (isValid && decodedDto != null) {
                val operationBlock: suspend () -> Unit = {
                    journalDao.updateStatus(journal.journalId, JournalStatus.READY_TO_UPLOAD.name, timestamp)
                    pendingChangeDao.markChangesJournaled(decodedDto.operations.map { it.changeId }, journal.journalId)
                    sequenceDao.setSequence(deviceId, journal.sequence, timestamp)
                }
                if (database != null) database.withTransaction { operationBlock() } else operationBlock()
            } else {
                val rollbackBlock: suspend () -> Unit = {
                    pendingChangeDao.resetJournaledChangesToPending(journal.journalId)
                    journalDao.deleteById(journal.journalId)
                    if (file.exists()) file.delete()
                }
                if (database != null) database.withTransaction { rollbackBlock() } else rollbackBlock()
            }
        }

        // 2. Reconcile uncommitted sequence allocations
        val maxCommitted = journalDao.getMaxCommittedSequence(deviceId) ?: 0L
        val currentAllocated = sequenceDao.getLastAllocatedSequence(deviceId) ?: 0L

        if (currentAllocated > maxCommitted) {
            val baseDir = File(context.filesDir, JOURNALS_DIR_NAME)
            val deviceDir = File(baseDir, deviceId)
            val candidateFiles = deviceDir.listFiles { _, name ->
                name.startsWith("${currentAllocated}_") && name.endsWith(".journal")
            }
            var recovered = false

            if (!candidateFiles.isNullOrEmpty()) {
                val candidateFile = candidateFiles.first()
                try {
                    val key = mekManager.getActiveMasterKey()
                    if (key != null) {
                        val encryptedBytes = candidateFile.readBytes()
                        val filename = candidateFile.name
                        val journalId = filename.substringAfter("${currentAllocated}_").substringBefore(".journal")
                        val aad = buildAad(SyncJournalDto.CURRENT_FORMAT_VERSION, journalId, deviceId, currentAllocated)
                        val decrypted = EncryptedContainer.decrypt(encryptedBytes, key, expectedAad = aad)
                        val dto = json.decodeFromString<SyncJournalDto>(String(decrypted, Charsets.UTF_8))

                        val entity = SyncJournalEntity(
                            journalId = dto.journalId,
                            deviceId = deviceId,
                            sequence = currentAllocated,
                            formatVersion = dto.formatVersion,
                            createdAt = dto.createdAt,
                            operationCount = dto.operationCount,
                            storagePath = candidateFile.absolutePath,
                            encryptedSizeBytes = candidateFile.length(),
                            status = JournalStatus.READY_TO_UPLOAD.name,
                            uploadAttempts = 0,
                            lastError = null,
                            updatedAt = timestamp,
                        )

                        val commitBlock: suspend () -> Unit = {
                            journalDao.insert(entity)
                            pendingChangeDao.markChangesJournaled(dto.operations.map { it.changeId }, dto.journalId)
                            sequenceDao.setSequence(deviceId, currentAllocated, timestamp)
                        }
                        if (database != null) database.withTransaction { commitBlock() } else commitBlock()
                        recovered = true
                    }
                } catch (_: Exception) {
                    recovered = false
                }
            }

            if (!recovered) {
                // Interrupted allocation without valid journal: reclaim sequence
                sequenceDao.setSequence(deviceId, maxCommitted, timestamp)
            }
        }
    }

    /**
     * Gathers pending changes, groups them into a bounded batch, generates deterministic DTO,
     * encrypts with AES-256-GCM, flushes to disk with fsync, and atomically commits the sequence,
     * journal metadata, and change statuses in Room.
     */
    suspend fun createNextJournal(
        maxOperations: Int = DEFAULT_MAX_OPERATIONS,
        customDeviceId: String? = null,
    ): JournalCreationResult = withContext(Dispatchers.IO) {
        writerMutex.withLock {
            val deviceId = customDeviceId ?: deviceIdProvider()

            // 1. Reconcile any prior incomplete state to guarantee contiguous sequences
            reconcileIncompleteState(deviceId)

            val pendingChanges = pendingChangeDao.getPendingChanges(maxOperations)
            if (pendingChanges.isEmpty()) {
                return@withLock JournalCreationResult.NoEligibleChanges
            }

            // 2. Validate operations and enforce batch size boundaries
            val seenChangeIds = mutableSetOf<String>()
            val validOperations = mutableListOf<JournalOperationDto>()
            var accumulatedSizeBytes = 0L

            for (change in pendingChanges) {
                if (!seenChangeIds.add(change.changeId)) {
                    continue
                }

                val payloadSize = change.payload?.toByteArray(Charsets.UTF_8)?.size ?: 0
                if (validOperations.isNotEmpty() && (accumulatedSizeBytes + payloadSize) > MAX_BATCH_SIZE_BYTES) {
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

            // 3. Obtain Master Encryption Key (MEK)
            val mek = mekManager.getActiveMasterKey()
                ?: return@withLock JournalCreationResult.Error("Master Encryption Key is not initialized on this device.")

            val journalId = UUID.randomUUID().toString()
            val now = clock()

            // 4. Calculate next sequence monotonically from committed state
            val maxCommitted = journalDao.getMaxCommittedSequence(deviceId) ?: 0L
            val sequence = maxCommitted + 1L

            // 5. Construct Journal DTO
            val journalDto = SyncJournalDto(
                formatVersion = SyncJournalDto.CURRENT_FORMAT_VERSION,
                journalId = journalId,
                deviceId = deviceId,
                sequence = sequence,
                createdAt = now,
                operationCount = validOperations.size,
                operations = validOperations,
            )

            // 6. Serialize deterministically
            val plainBytes = json.encodeToString(journalDto).toByteArray(Charsets.UTF_8)

            // 7. Encrypt with AES-256-GCM via EncryptedContainer
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

            // 8. Persist encrypted journal file to local storage with fsync
            val baseDir = File(context.filesDir, JOURNALS_DIR_NAME)
            val deviceDir = File(baseDir, deviceId).apply { mkdirs() }
            val journalFile = File(deviceDir, "${sequence}_$journalId.journal")
            val tempFile = File(deviceDir, "${sequence}_$journalId.tmp")

            try {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(encryptedBytes)
                    fos.flush()
                    try {
                        fos.fd.sync()
                    } catch (_: Exception) {
                    }
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

            // 9. Atomic Room transaction: commit journal entity, record sequence, and mark changes journaled
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

            try {
                val commitBlock: suspend () -> Unit = {
                    journalDao.insert(journalEntity)
                    sequenceDao.setSequence(deviceId, sequence, now)
                    pendingChangeDao.markChangesJournaled(
                        changeIds = validOperations.map { it.changeId },
                        journalId = journalId,
                    )
                }

                if (database != null) {
                    database.withTransaction { commitBlock() }
                } else {
                    commitBlock()
                }
            } catch (e: Exception) {
                journalFile.delete()
                return@withLock JournalCreationResult.Error("Failed to commit journal metadata: ${e.message}", cause = e)
            }

            JournalCreationResult.Success(journalEntity, journalDto)
        }
    }
}
