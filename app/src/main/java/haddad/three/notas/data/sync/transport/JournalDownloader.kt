package haddad.three.notas.data.sync.transport

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.journal.SyncJournalDto
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Downloads, cryptographically verifies, and safely stages remote encrypted journals into the local inbox.
 *
 * Requirements strictly enforced:
 * 1. Stream download to staging storage.
 * 2. Verify SHA-256 hash against Drive metadata.
 * 3. Validate NOTAS_E1 container format, AAD, and AES-256-GCM authentication tag using the account MEK.
 * 4. Validate journal schema, version, sequence, operationCount, and unique change IDs.
 * 5. Reject corrupted, tampered, or malformed journals cleanly.
 * 6. Store validated journal files in durable inbox storage and record in [SyncInboxDao].
 * 7. Advance per-device watermarks without skipping sequence gaps.
 * 8. Never apply journal operations to Room in this phase (Phase 4D boundary).
 */
@Singleton
class JournalDownloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val transport: GoogleDriveSyncTransport,
        private val inboxDao: SyncInboxDao,
        private val watermarkDao: SyncWatermarkDao,
        private val mekManager: MekManager,
        private val database: RememberDatabase?,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        /**
         * Downloads and validates a collection of discovered remote journals in sequence order.
         */
        suspend fun downloadAndValidateJournals(
            discoveredHeaders: List<RemoteJournalHeader>,
            overrideKey: SecretKey? = null,
            onProgress: ((completed: Int, total: Int) -> Unit)? = null,
        ): JournalDownloadBatchResult =
            withContext(ioDispatcher) {
                var downloadedAndValidated = 0
                var skippedCount = 0
                var failedCount = 0
                val errors = mutableListOf<String>()

                val sortedHeaders = discoveredHeaders.sortedBy { it.sequence }
                val total = sortedHeaders.size

                var rateLimitRetryAfterSeconds: Long? = null
                for ((index, header) in sortedHeaders.withIndex()) {
                    when (val result = downloadAndValidateJournal(header, overrideKey)) {
                        is JournalDownloadResult.Success -> {
                            downloadedAndValidated++
                        }
                        is JournalDownloadResult.SkippedAlreadyDownloaded -> {
                            skippedCount++
                        }
                        is JournalDownloadResult.Failure -> {
                            failedCount++
                            errors.add("Journal ${header.journalId} (seq ${header.sequence}) download failed: ${result.message}")
                        }
                        is JournalDownloadResult.Unauthorized -> {
                            failedCount++
                            errors.add("Drive authorization failed during journal ${header.journalId} download")
                            break
                        }
                        is JournalDownloadResult.RateLimited -> {
                            failedCount++
                            rateLimitRetryAfterSeconds = result.retryAfterSeconds
                            errors.add("Drive rate limited during journal ${header.journalId} download")
                            break
                        }
                    }
                    onProgress?.invoke(index + 1, total)
                }

                JournalDownloadBatchResult(
                    totalDiscovered = total,
                    downloadedAndValidated = downloadedAndValidated,
                    skippedAlreadyDownloaded = skippedCount,
                    failed = failedCount,
                    errors = errors,
                    rateLimitRetryAfterSeconds = rateLimitRetryAfterSeconds,
                )
            }

        /**
         * Downloads and validates a single remote journal file.
         */
        suspend fun downloadAndValidateJournal(
            header: RemoteJournalHeader,
            overrideKey: SecretKey? = null,
        ): JournalDownloadResult =
            withContext(ioDispatcher) {
                // Step 1: Pre-download structural validation
                if (header.journalId.isBlank() || header.journalId.length > GoogleDriveSyncTransport.MAX_IDENTIFIER_LENGTH) {
                    throw MalformedJournalException("Invalid journalId length: ${header.journalId.length}")
                }
                if (header.deviceId.isBlank() || header.deviceId.length > GoogleDriveSyncTransport.MAX_IDENTIFIER_LENGTH) {
                    throw MalformedJournalException("Invalid deviceId length: ${header.deviceId.length}")
                }
                if (header.sequence <= 0L) {
                    throw MalformedJournalException("Sequence must be positive, got ${header.sequence}")
                }
                if (!header.contentSha256.matches(GoogleDriveSyncTransport.SHA256_HEX_REGEX)) {
                    throw MalformedJournalException("Malformed contentSha256: ${header.contentSha256}")
                }
                if (header.sizeBytes > GoogleDriveSyncTransport.MAX_ENCRYPTED_JOURNAL_SIZE_BYTES) {
                    throw MalformedJournalException("Journal header size ${header.sizeBytes} exceeds maximum allowed 4MB")
                }

                // Step 2: Idempotency check: Already present in inbox?
                val existingByJournalId = inboxDao.getByJournalId(header.journalId)
                if (existingByJournalId != null) {
                    if (existingByJournalId.contentSha256 == header.contentSha256) {
                        return@withContext JournalDownloadResult.SkippedAlreadyDownloaded(header.journalId)
                    } else {
                        throw ConflictingJournalException(
                            "Local inbox already contains journal ${header.journalId} with conflicting hash: " +
                                "existing ${existingByJournalId.contentSha256}, incoming ${header.contentSha256}",
                        )
                    }
                }

                val existingBySequence = inboxDao.getByDeviceAndSequence(header.deviceId, header.sequence)
                if (existingBySequence != null && existingBySequence.journalId != header.journalId) {
                    throw ConflictingJournalException(
                        "Sequence ${header.sequence} for device ${header.deviceId} is already occupied by journal ${existingBySequence.journalId}",
                    )
                }

                // Step 3: Prepare staging directory and file
                val stagingDir = File(context.filesDir, STAGING_DIR_NAME).apply { mkdirs() }
                val stagingFile = File(stagingDir, "staging_${header.journalId}.tmp")

                try {
                    // Step 4: Stream download from Google Drive
                    val downloadResult =
                        transport.downloadJournal(
                            driveFileId = header.driveFileId,
                            destinationFile = stagingFile,
                            expectedSha256 = header.contentSha256,
                        )

                    when (downloadResult) {
                        is DriveResult.Unauthorized -> return@withContext JournalDownloadResult.Unauthorized
                        is DriveResult.RateLimited -> return@withContext JournalDownloadResult.RateLimited(downloadResult.retryAfterSeconds)
                        is DriveResult.Offline -> return@withContext JournalDownloadResult.Failure("Network offline")
                        is DriveResult.Error -> return@withContext JournalDownloadResult.Failure(downloadResult.message, downloadResult.cause)
                        is DriveResult.Success -> { /* Continue with validation */ }
                    }

                    if (stagingFile.length() > GoogleDriveSyncTransport.MAX_ENCRYPTED_JOURNAL_SIZE_BYTES) {
                        throw MalformedJournalException("Downloaded file size ${stagingFile.length()} exceeds maximum allowed 4MB")
                    }

                    // Step 5: Cryptographic container validation & decryption
                    val key =
                        overrideKey ?: mekManager.getActiveMasterKey()
                            ?: throw InvalidMekException("Master Encryption Key is not available to decrypt incoming journal")

                    val fileBytes = stagingFile.readBytes()
                    val expectedAad =
                        SyncJournalWriter.buildAad(
                            formatVersion = header.formatVersion,
                            journalId = header.journalId,
                            deviceId = header.deviceId,
                            sequence = header.sequence,
                        )

                    val plainBytes =
                        try {
                            EncryptedContainer.decrypt(
                                containerBytes = fileBytes,
                                key = key,
                                expectedAad = expectedAad,
                            )
                        } catch (aead: AEADBadTagException) {
                            throw InvalidEncryptedContainerException("Invalid AES-GCM authentication tag: journal ciphertext has been tampered with or corrupted", aead)
                        } catch (e: Exception) {
                            throw InvalidEncryptedContainerException("Failed to decrypt encrypted container: ${e.message}", cause = e)
                        }

                    if (plainBytes.size > MAX_DECRYPTED_PAYLOAD_SIZE_BYTES) {
                        throw MalformedJournalException("Decrypted payload size ${plainBytes.size} exceeds maximum 2MB limit")
                    }

                    // Step 6: Validate decrypted journal payload schema
                    val dto =
                        try {
                            json.decodeFromString<SyncJournalDto>(plainBytes.toString(Charsets.UTF_8))
                        } catch (e: Exception) {
                            throw MalformedJournalException("Failed to parse journal JSON payload: ${e.message}")
                        }

                    // Step 7: Verify schema constraints & metadata alignment
                    if (dto.formatVersion != 1) {
                        throw UnsupportedJournalVersionException("Unsupported journal format version ${dto.formatVersion}, expected 1")
                    }
                    if (dto.formatVersion != header.formatVersion) {
                        throw MalformedJournalException("Format version mismatch: metadata claimed ${header.formatVersion}, payload contains ${dto.formatVersion}")
                    }
                    if (dto.journalId != header.journalId) {
                        throw MalformedJournalException("Journal ID mismatch: metadata claimed ${header.journalId}, payload contains ${dto.journalId}")
                    }
                    if (dto.deviceId != header.deviceId) {
                        throw MalformedJournalException("Device ID mismatch: metadata claimed ${header.deviceId}, payload contains ${dto.deviceId}")
                    }
                    if (dto.sequence != header.sequence) {
                        throw MalformedJournalException("Sequence mismatch: metadata claimed ${header.sequence}, payload contains ${dto.sequence}")
                    }
                    if (dto.operationCount != header.operationCount) {
                        throw MalformedJournalException("Operation count mismatch: metadata claimed ${header.operationCount}, payload contains ${dto.operationCount}")
                    }
                    if (dto.operationCount != dto.operations.size) {
                        throw MalformedJournalException("Operation count mismatch: claimed ${dto.operationCount}, actual operations ${dto.operations.size}")
                    }
                    if (dto.operations.size > GoogleDriveSyncTransport.MAX_OPERATION_COUNT) {
                        throw MalformedJournalException("Operation count ${dto.operations.size} exceeds maximum allowed ${GoogleDriveSyncTransport.MAX_OPERATION_COUNT}")
                    }

                    // Verify unique changeIds within the journal
                    val uniqueChangeIds = dto.operations.map { it.changeId }.toSet()
                    if (uniqueChangeIds.size != dto.operations.size) {
                        throw MalformedJournalException("Duplicate changeId detected in journal operations")
                    }

                    for (op in dto.operations) {
                        if (op.changeId.isBlank() || op.changeId.length > GoogleDriveSyncTransport.MAX_IDENTIFIER_LENGTH) {
                            throw MalformedJournalException("Invalid operation changeId: '${op.changeId}'")
                        }
                        if (op.entityType.isBlank() || op.entityType.length > 64) {
                            throw MalformedJournalException("Invalid operation entityType: '${op.entityType}'")
                        }
                        if (op.entityId.isBlank() || op.entityId.length > GoogleDriveSyncTransport.MAX_IDENTIFIER_LENGTH) {
                            throw MalformedJournalException("Invalid operation entityId: '${op.entityId}'")
                        }
                        if (op.operationType.isBlank() || op.operationType.length > 32) {
                            throw MalformedJournalException("Invalid operation operationType: '${op.operationType}'")
                        }
                        if (op.schemaVersion < 1) {
                            throw MalformedJournalException("Invalid operation schemaVersion: ${op.schemaVersion}")
                        }
                        if (op.payload != null && op.payload.length > MAX_PAYLOAD_STRING_LENGTH) {
                            throw MalformedJournalException("Operation payload length exceeds 1MB limit")
                        }
                    }

                    // Step 8: Atomic move to permanent inbox storage with fallback
                    val storageDir = File(context.filesDir, INBOX_STORAGE_DIR_NAME).apply { mkdirs() }
                    val permanentFile = File(storageDir, "${header.deviceId}_${header.sequence}_${header.journalId}.bin")
                    try {
                        java.nio.file.Files.move(
                            stagingFile.toPath(),
                            permanentFile.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (_: Exception) {
                        stagingFile.copyTo(permanentFile, overwrite = true)
                        stagingFile.delete()
                    }

                    // Step 9: Persist to Room inbox and update watermark transactionally
                    val inboxEntity =
                        SyncInboxEntity(
                            journalId = dto.journalId,
                            deviceId = dto.deviceId,
                            sequence = dto.sequence,
                            formatVersion = dto.formatVersion,
                            driveFileId = header.driveFileId,
                            localFilePath = permanentFile.absolutePath,
                            contentSha256 = header.contentSha256,
                            operationCount = dto.operationCount,
                            downloadedAt = System.currentTimeMillis(),
                            status = InboxStatus.VALIDATED,
                        )

                    commitInboxAndWatermark(inboxEntity)
                    JournalDownloadResult.Success(inboxEntity)
                } catch (e: Exception) {
                    if (stagingFile.exists()) stagingFile.delete()
                    throw e
                }
            }

        /**
         * Startup and crash-consistency reconciler for filesystem inbox storage and Room database.
         *
         * Invariants restored:
         * 1. Purges dangling .tmp files left in sync_inbox_staging/ by process death during download.
         * 2. Cleans up any Room sync_inbox records where the physical durable file is missing,
         *    and recomputes device watermarks so missing journals can be re-downloaded.
         * 3. Reconciles orphan .bin files found in sync_inbox_storage/ that are missing Room records:
         *    if cryptographically authentic and schema-valid, safely registers them into Room;
         *    if corrupted, removes them cleanly.
         */
        suspend fun reconcileInboxStorage(overrideKey: SecretKey? = null): InboxReconciliationResult =
            withContext(ioDispatcher) {
                var purgedStaging = 0
                var missingCleaned = 0
                var orphanReconciled = 0
                val errors = mutableListOf<String>()

                // 1. Purge staging temp files from interrupted downloads
                val stagingDir = File(context.filesDir, STAGING_DIR_NAME)
                if (stagingDir.exists() && stagingDir.isDirectory) {
                    stagingDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".tmp")) {
                            if (file.delete()) purgedStaging++
                        }
                    }
                }

                // 2. Clean up Room records whose physical file is missing
                val allInboxEntries = inboxDao.getAllEntries()
                val affectedDevices = mutableSetOf<String>()
                for (entry in allInboxEntries) {
                    if (entry.status == InboxStatus.VALIDATED) {
                        val file = File(entry.localFilePath)
                        if (!file.exists() || file.length() == 0L) {
                            inboxDao.deleteByJournalId(entry.journalId)
                            missingCleaned++
                            affectedDevices.add(entry.deviceId)
                        }
                    }
                }
                for (deviceId in affectedDevices) {
                    recomputeWatermarkForDevice(deviceId)
                }

                // 3. Reconcile orphan files in inbox storage
                val storageDir = File(context.filesDir, INBOX_STORAGE_DIR_NAME)
                if (storageDir.exists() && storageDir.isDirectory) {
                    val key = overrideKey ?: mekManager.getActiveMasterKey()
                    val existingPaths = inboxDao.getAllEntries().map { File(it.localFilePath).canonicalPath }.toSet()
                    val storageFiles = storageDir.listFiles()?.filter { it.isFile && it.name.endsWith(".bin") } ?: emptyList()

                    for (file in storageFiles) {
                        if (existingPaths.contains(file.canonicalPath)) continue

                        if (key == null) {
                            errors.add("Cannot reconcile orphan file ${file.name}: MEK not available")
                            continue
                        }

                        if (file.length() > GoogleDriveSyncTransport.MAX_ENCRYPTED_JOURNAL_SIZE_BYTES || file.length() == 0L) {
                            file.delete()
                            continue
                        }

                        try {
                            val bytes = file.readBytes()
                            val hash = transport.computeSha256(file)
                            val meta = EncryptedContainer.readMetadata(bytes)
                            val plainBytes = EncryptedContainer.decrypt(bytes, key, expectedAad = meta.aad)
                            val dto = json.decodeFromString<SyncJournalDto>(plainBytes.toString(Charsets.UTF_8))

                            // Validate AAD against decrypted DTO
                            val expectedAad = SyncJournalWriter.buildAad(dto.formatVersion, dto.journalId, dto.deviceId, dto.sequence)
                            if (!meta.aad.contentEquals(expectedAad)) {
                                file.delete()
                                continue
                            }

                            // Check for sequence conflict in DB
                            val existingBySeq = inboxDao.getByDeviceAndSequence(dto.deviceId, dto.sequence)
                            if (existingBySeq != null && existingBySeq.journalId != dto.journalId) {
                                file.delete()
                                continue
                            }

                            val entity =
                                SyncInboxEntity(
                                    journalId = dto.journalId,
                                    deviceId = dto.deviceId,
                                    sequence = dto.sequence,
                                    formatVersion = dto.formatVersion,
                                    driveFileId = "reconciled_orphan_${dto.journalId}",
                                    localFilePath = file.absolutePath,
                                    contentSha256 = hash,
                                    operationCount = dto.operationCount,
                                    downloadedAt = System.currentTimeMillis(),
                                    status = InboxStatus.VALIDATED,
                                )
                            commitInboxAndWatermark(entity)
                            orphanReconciled++
                        } catch (_: Exception) {
                            // Corrupted or unparseable orphan file - safely remove
                            file.delete()
                        }
                    }
                }

                InboxReconciliationResult(
                    purgedStagingFiles = purgedStaging,
                    missingFilesCleaned = missingCleaned,
                    orphanFilesReconciled = orphanReconciled,
                    errors = errors,
                )
            }

        private suspend fun recomputeWatermarkForDevice(deviceId: String) {
            val existingWatermark = watermarkDao.getWatermark(deviceId) ?: return

            val validatedEntries =
                inboxDao
                    .getEntriesForDevice(deviceId)
                    .filter { it.status == InboxStatus.VALIDATED }
                    .map { it.sequence }
                    .toSet()

            var contiguousValidated = 0L
            while (validatedEntries.contains(contiguousValidated + 1L)) {
                contiguousValidated++
            }

            val highestDownloaded = validatedEntries.maxOrNull() ?: 0L

            val missing = mutableListOf<Long>()
            for (seq in 1L..existingWatermark.highestDiscoveredSeq) {
                if (!validatedEntries.contains(seq)) {
                    missing.add(seq)
                }
            }

            val updated =
                existingWatermark.copy(
                    highestDownloadedSeq = highestDownloaded,
                    highestValidatedSeq = contiguousValidated,
                    missingSequencesJson = json.encodeToString(missing.sorted()),
                    updatedAt = System.currentTimeMillis(),
                )
            watermarkDao.insertOrUpdate(updated)
        }

        private suspend fun commitInboxAndWatermark(inboxEntity: SyncInboxEntity) {
            val commitBlock: suspend () -> Unit = {
                inboxDao.insert(inboxEntity)

                val existingWatermark =
                    watermarkDao.getWatermark(inboxEntity.deviceId)
                        ?: SyncWatermarkEntity(deviceId = inboxEntity.deviceId)

                val newHighestDownloaded = max(existingWatermark.highestDownloadedSeq, inboxEntity.sequence)

                // Recompute contiguous validated sequence without gaps
                val validatedEntries =
                    inboxDao
                        .getEntriesForDevice(inboxEntity.deviceId)
                        .filter { it.status == InboxStatus.VALIDATED }
                        .map { it.sequence }
                        .toSet()

                var contiguousValidated = 0L
                while (validatedEntries.contains(contiguousValidated + 1L)) {
                    contiguousValidated++
                }

                // Remove newly validated sequence from missing set
                val missing = parseMissingSequences(existingWatermark.missingSequencesJson).toMutableSet()
                missing.remove(inboxEntity.sequence)

                val updatedWatermark =
                    existingWatermark.copy(
                        highestDownloadedSeq = newHighestDownloaded,
                        highestValidatedSeq = contiguousValidated,
                        missingSequencesJson = json.encodeToString(missing.sorted()),
                        updatedAt = System.currentTimeMillis(),
                    )

                watermarkDao.insertOrUpdate(updatedWatermark)
            }

            if (database != null) {
                database.withTransaction { commitBlock() }
            } else {
                commitBlock()
            }
        }

        private fun parseMissingSequences(jsonString: String): List<Long> {
            if (jsonString.isBlank() || jsonString == "[]") return emptyList()
            return runCatching { json.decodeFromString<List<Long>>(jsonString) }.getOrDefault(emptyList())
        }

        companion object {
            const val STAGING_DIR_NAME = "sync_inbox_staging"
            const val INBOX_STORAGE_DIR_NAME = "sync_inbox_storage"
            const val MAX_DECRYPTED_PAYLOAD_SIZE_BYTES = 2 * 1024 * 1024L
            const val MAX_PAYLOAD_STRING_LENGTH = 1_000_000
        }
    }
