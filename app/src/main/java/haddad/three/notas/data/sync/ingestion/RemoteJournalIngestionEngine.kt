package haddad.three.notas.data.sync.ingestion

import androidx.room.withTransaction
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.sync.change.SyncContext
import haddad.three.notas.data.sync.conflict.FieldConflictResolver
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationEntity
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.journal.SyncJournalDto
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import haddad.three.notas.data.sync.transport.InboxStatus
import haddad.three.notas.data.sync.transport.SyncInboxDao
import haddad.three.notas.data.sync.transport.SyncInboxEntity
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

class MissingSequenceException(
    val deviceId: String,
    val expectedSeq: Long,
    val actualSeq: Long,
) : IllegalStateException("Missing sequence for device $deviceId: expected $expectedSeq but found $actualSeq")

class InvalidJournalException(message: String, cause: Throwable? = null) : Exception(message, cause)
class JournalFileNotFoundException(message: String) : Exception(message)

data class IngestionSummary(
    val processedJournalsCount: Int,
    val appliedOperationsCount: Int,
    val skippedOperationsCount: Int,
    val failedJournalsCount: Int,
    val errors: List<IngestionError> = emptyList(),
)

data class IngestionError(
    val deviceId: String,
    val journalId: String?,
    val sequence: Long?,
    val errorMessage: String,
)

/**
 * Dedicated engine for safely ingesting validated remote journals into the local Room database,
 * merging Hybrid Logical Clocks, and applying field-level conflict resolution.
 */
@Singleton
class RemoteJournalIngestionEngine @Inject constructor(
    private val database: RememberDatabase? = null,
    private val syncInboxDao: SyncInboxDao,
    private val syncWatermarkDao: SyncWatermarkDao,
    private val appliedOperationDao: SyncAppliedOperationDao,
    private val fieldConflictResolver: FieldConflictResolver,
    private val hlcClock: HlcClock,
    private val mekManager: MekManager,
    private val overrideKey: SecretKey? = null,
    private val transactionRunner: (suspend (suspend () -> Unit) -> Unit)? = null,
) {
    private val ingestionMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Ingests all pending VALIDATED and interrupted APPLYING journals across all devices
     * in deterministic sequence order.
     */
    suspend fun ingestAll(): IngestionSummary = withContext(Dispatchers.IO) {
        ingestionMutex.withLock {
            SyncContext.withRemoteSuppression {
                var totalJournals = 0
                var totalAppliedOps = 0
                var totalSkippedOps = 0
                var failedJournals = 0
                val errors = mutableListOf<IngestionError>()

                val activeDevices = syncInboxDao.getActiveDevicesForIngestion()
                for (deviceId in activeDevices.sorted()) {
                    val result = ingestDeviceInternal(deviceId)
                    totalJournals += result.processedJournalsCount
                    totalAppliedOps += result.appliedOperationsCount
                    totalSkippedOps += result.skippedOperationsCount
                    failedJournals += result.failedJournalsCount
                    errors.addAll(result.errors)
                }

                IngestionSummary(
                    processedJournalsCount = totalJournals,
                    appliedOperationsCount = totalAppliedOps,
                    skippedOperationsCount = totalSkippedOps,
                    failedJournalsCount = failedJournals,
                    errors = errors,
                )
            }
        }
    }

    /**
     * Ingests journals for a single device in strict sequence order.
     */
    suspend fun ingestDevice(deviceId: String): IngestionSummary = withContext(Dispatchers.IO) {
        ingestionMutex.withLock {
            SyncContext.withRemoteSuppression {
                ingestDeviceInternal(deviceId)
            }
        }
    }

    private suspend fun ingestDeviceInternal(deviceId: String): IngestionSummary {
        var journalsCount = 0
        var appliedOpsCount = 0
        var skippedOpsCount = 0
        var failedCount = 0
        val errors = mutableListOf<IngestionError>()

        val watermark = syncWatermarkDao.getWatermark(deviceId)
        var currentAppliedSeq = watermark?.highestAppliedSeq ?: 0L

        val pendingEntries = syncInboxDao.getPendingEntriesForDevice(deviceId)
        for (entry in pendingEntries) {
            val expectedSeq = currentAppliedSeq + 1L

            if (entry.sequence < expectedSeq) {
                // Already applied in watermark: mark APPLIED if not already
                if (entry.status != InboxStatus.APPLIED) {
                    syncInboxDao.updateStatus(entry.journalId, InboxStatus.APPLIED)
                }
                continue
            }

            if (entry.sequence > expectedSeq) {
                // Missing sequence gap: do NOT advance watermark or skip over missing journals
                val err = IngestionError(
                    deviceId = deviceId,
                    journalId = entry.journalId,
                    sequence = entry.sequence,
                    errorMessage = "Sequence gap detected for device $deviceId: expected $expectedSeq, but found ${entry.sequence}",
                )
                errors.add(err)
                // Freeze ingestion for this device until the missing sequence arrives
                break
            }

            // entry.sequence == expectedSeq: apply this journal
            try {
                val (appliedOps, skippedOps) = applySingleJournal(entry)
                journalsCount++
                appliedOpsCount += appliedOps
                skippedOpsCount += skippedOps
                currentAppliedSeq = entry.sequence
            } catch (e: Exception) {
                failedCount++
                errors.add(
                    IngestionError(
                        deviceId = deviceId,
                        journalId = entry.journalId,
                        sequence = entry.sequence,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    ),
                )
                // Mark entry as FAILED in sync_inbox and stop advancing watermark for this device
                syncInboxDao.updateStatus(entry.journalId, InboxStatus.FAILED)
                break
            }
        }

        return IngestionSummary(
            processedJournalsCount = journalsCount,
            appliedOperationsCount = appliedOpsCount,
            skippedOperationsCount = skippedOpsCount,
            failedJournalsCount = failedCount,
            errors = errors,
        )
    }

    private suspend fun applySingleJournal(entry: SyncInboxEntity): Pair<Int, Int> {
        // Step 1: Mark status as APPLYING
        syncInboxDao.updateStatus(entry.journalId, InboxStatus.APPLYING)

        // Step 2: Read file
        val file = File(entry.localFilePath)
        if (!file.exists()) {
            throw JournalFileNotFoundException("Encrypted journal file does not exist at ${entry.localFilePath}")
        }

        // Step 3: Decrypt journal
        val key = overrideKey ?: mekManager.getActiveMasterKey()
            ?: throw IllegalStateException("Master Encryption Key is not available for journal ingestion")

        val expectedAad = SyncJournalWriter.buildAad(
            formatVersion = entry.formatVersion,
            journalId = entry.journalId,
            deviceId = entry.deviceId,
            sequence = entry.sequence,
        )

        val decryptedBytes = try {
            EncryptedContainer.decrypt(
                containerBytes = file.readBytes(),
                key = key,
                expectedAad = expectedAad,
            )
        } catch (e: Exception) {
            throw InvalidJournalException("Failed to decrypt or authenticate journal container", e)
        }

        // Step 4: Parse & validate journal DTO
        val journalDto = try {
            json.decodeFromString<SyncJournalDto>(decryptedBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw InvalidJournalException("Malformed journal JSON payload", e)
        }

        if (journalDto.journalId != entry.journalId) {
            throw InvalidJournalException("Journal ID mismatch: expected ${entry.journalId}, found ${journalDto.journalId}")
        }
        if (journalDto.deviceId != entry.deviceId) {
            throw InvalidJournalException("Device ID mismatch: expected ${entry.deviceId}, found ${journalDto.deviceId}")
        }
        if (journalDto.sequence != entry.sequence) {
            throw InvalidJournalException("Sequence mismatch: expected ${entry.sequence}, found ${journalDto.sequence}")
        }
        if (journalDto.formatVersion > SyncJournalDto.CURRENT_FORMAT_VERSION) {
            throw InvalidJournalException("Unsupported journal format version ${journalDto.formatVersion}")
        }
        if (journalDto.operations.size != journalDto.operationCount) {
            throw InvalidJournalException("Operation count mismatch: declared ${journalDto.operationCount}, actual ${journalDto.operations.size}")
        }

        // Step 5: Atomic transaction execution
        var appliedOps = 0
        var skippedOps = 0

        runInTransaction {
            for (op in journalDto.operations) {
                if (appliedOperationDao.isApplied(op.changeId)) {
                    skippedOps++
                    continue
                }

                val opHlc = op.hlcTimestamp?.let { Hlc.parse(it) } ?: Hlc(
                    physicalTime = op.createdAt,
                    logicalCounter = op.hlcCounter ?: 0,
                    deviceId = op.hlcNodeId ?: journalDto.deviceId,
                )

                // Causal HLC merge
                hlcClock.mergeRemote(opHlc)

                // Field-level conflict resolution & application
                fieldConflictResolver.resolveAndApply(op, opHlc, journalDto)

                // Record in durable applied operations ledger
                appliedOperationDao.insert(
                    SyncAppliedOperationEntity(
                        changeId = op.changeId,
                        journalId = entry.journalId,
                        deviceId = entry.deviceId,
                        sequence = entry.sequence,
                        entityType = op.entityType,
                        entityId = op.entityId,
                        appliedAt = System.currentTimeMillis(),
                        hlcTimestamp = opHlc.toString(),
                    ),
                )
                appliedOps++
            }

            // Mark journal as APPLIED
            syncInboxDao.updateStatus(entry.journalId, InboxStatus.APPLIED)

            // Monotonically advance applied sequence watermark
            syncWatermarkDao.updateAppliedSequence(
                deviceId = entry.deviceId,
                appliedSeq = entry.sequence,
                timestamp = System.currentTimeMillis(),
            )
        }

        return appliedOps to skippedOps
    }

    private suspend fun runInTransaction(block: suspend () -> Unit) {
        if (transactionRunner != null) {
            transactionRunner.invoke(block)
        } else if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }
}
