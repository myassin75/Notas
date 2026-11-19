package haddad.three.notas.data.sync.snapshot

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.sync.SyncDtoMapping
import haddad.three.notas.data.sync.change.AttachmentChangePayload
import haddad.three.notas.data.sync.change.CategoryChangePayload
import haddad.three.notas.data.sync.change.ChecklistItemChangePayload
import haddad.three.notas.data.sync.change.NoteChangePayload
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncTombstoneDao
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.ingestion.RemoteJournalIngestionEngine
import haddad.three.notas.data.sync.journal.JournalCreationResult
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SnapshotCaptureResult {
    data class Success(
        val snapshot: SyncSnapshotEntity,
        val manifest: SyncSnapshotManifest,
    ) : SnapshotCaptureResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : SnapshotCaptureResult
}

sealed interface SnapshotRestoreResult {
    data class Success(
        val manifest: SyncSnapshotManifest,
    ) : SnapshotRestoreResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : SnapshotRestoreResult
}

/**
 * Coordinates snapshot capture, archive creation, AES-256-GCM encryption,
 * atomic database restoration, and attachment consistency.
 */
@Singleton
class SyncSnapshotManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val database: RememberDatabase? = null,
        private val snapshotDao: SyncSnapshotDao,
        private val promotionDao: SyncAttachmentPromotionDao,
        private val promotionRecoveryManager: AttachmentPromotionRecoveryManager,
        private val pendingChangeDao: PendingChangeDao,
        private val journalDao: SyncJournalDao,
        private val watermarkDao: SyncWatermarkDao,
        private val appliedOperationDao: SyncAppliedOperationDao,
        private val conflictMetadataDao: SyncConflictMetadataDao,
        private val tombstoneDao: SyncTombstoneDao,
        private val conflictRecordDao: SyncConflictRecordDao,
        private val noteDao: NoteDao,
        private val itemDao: ChecklistItemDao,
        private val shoulderDao: ShoulderDao,
        private val attachmentDao: AttachmentDao,
        private val hlcClock: HlcClock,
        private val mekManager: MekManager,
        private val journalWriter: SyncJournalWriter? = null,
        private val ingestionEngine: RemoteJournalIngestionEngine? = null,
        private val deviceIdProvider: suspend () -> String,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        private val clock: () -> Long = System::currentTimeMillis,
    ) {
        private val snapshotMutex = Mutex()
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        companion object {
            const val SNAPSHOTS_DIR_NAME = "sync_snapshots"
            const val SNAPSHOT_STAGING_DIR_NAME = "sync_snapshots_staging"

            fun buildSnapshotAad(
                formatVersion: Int,
                snapshotId: String,
                accountEmail: String,
                creatorDeviceId: String,
                coverageVectorHash: String,
            ): ByteArray =
                "formatVersion=$formatVersion&snapshotId=$snapshotId&accountEmail=$accountEmail&creatorDeviceId=$creatorDeviceId&coverageVectorHash=$coverageVectorHash"
                    .toByteArray(Charsets.UTF_8)

            fun computeStringSha256(input: String): String {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
                return hashBytes.joinToString("") { "%02x".format(it) }
            }
        }

        private suspend fun <R> runInTransaction(block: suspend () -> R): R =
            if (database != null) {
                database.withTransaction { block() }
            } else {
                block()
            }

        /**
         * Captures a consistent snapshot of the current Room database state,
         * freezes attachment binaries into an encrypted container, and stages it
         * with status READY_TO_UPLOAD.
         */
        suspend fun captureSnapshot(
            accountEmail: String,
            accountId: String? = null,
            customDeviceId: String? = null,
            overrideKey: SecretKey? = null,
        ): SnapshotCaptureResult =
            withContext(ioDispatcher) {
                snapshotMutex.withLock {
                    val localDeviceId = customDeviceId ?: deviceIdProvider()
                    val now = clock()
                    val snapshotId = UUID.randomUUID().toString()

                    val key =
                        overrideKey ?: mekManager.getActiveMasterKey()
                            ?: return@withLock SnapshotCaptureResult.Error("Master Encryption Key is not available")

                    // 1. Pre-Capture Drain Loop: Ingest pending inbox and drain local pending changes
                    ingestionEngine?.ingestAll()

                    var drainAttempts = 0
                    while (pendingChangeDao.countPendingChanges() > 0 && drainAttempts < 10) {
                        drainAttempts++
                        val created = journalWriter?.createNextJournal(customDeviceId = localDeviceId)
                        if (created is JournalCreationResult.NoEligibleChanges || created is JournalCreationResult.Error) {
                            break
                        }
                    }

                    // 2. Frozen Transaction Read (SQLite WAL Snapshot Isolation)
                    val capturedData =
                        try {
                            runInTransaction {
                                val pendingCount = pendingChangeDao.countPendingChanges()
                                if (pendingCount > 0) {
                                    throw IncompleteSnapshotException("Concurrent pending changes detected during snapshot freeze: $pendingCount changes remaining")
                                }

                                // Build Coverage Vector
                                val localMaxSeq = journalDao.getMaxPublishedSequence(localDeviceId) ?: 0L
                                val coverageVector = mutableMapOf<String, Long>()
                                coverageVector[localDeviceId] = localMaxSeq

                                val peerWatermarks = watermarkDao.getAllWatermarks()
                                for (wm in peerWatermarks) {
                                    if (wm.deviceId != localDeviceId && wm.highestAppliedSeq > 0L) {
                                        coverageVector[wm.deviceId] = wm.highestAppliedSeq
                                    }
                                }

                                // Export Canonical Entities
                                val notesWithItems = noteDao.allNotes()
                                val allCategories = shoulderDao.allShoulders()
                                val allAttachments = notesWithItems.flatMap { it.attachments }
                                val allItems = notesWithItems.flatMap { it.items }

                                val notePayloads =
                                    notesWithItems.map { noteWithItems ->
                                        val n = noteWithItems.note
                                        val categoryUuid =
                                            n.shoulderId?.let { sId ->
                                                allCategories.firstOrNull { it.id == sId }?.uuid
                                            }
                                        NoteChangePayload(
                                            uuid = n.uuid,
                                            ownerId = n.ownerId,
                                            kind = n.kind.name,
                                            title = n.title,
                                            body = n.body,
                                            colorIndex = n.colorIndex,
                                            trashed = n.trashed,
                                            createdAt = n.createdAt,
                                            updatedAt = n.updatedAt,
                                            reminderAt = n.reminderAt,
                                            endAt = n.endAt,
                                            isAllDay = n.isAllDay,
                                            importance = n.importance.name,
                                            visibility = n.visibility.name,
                                            pictureUri = n.pictureUri,
                                            pictureHeroFraming = n.pictureHeroFraming,
                                            locked = n.locked,
                                            iconKey = n.iconKey,
                                            categoryUuid = categoryUuid,
                                            archived = n.archived,
                                            trashedAt = n.trashedAt,
                                            completedAt = n.completedAt,
                                            pinnedAt = n.pinnedAt,
                                        )
                                    }

                                val categoryPayloads =
                                    allCategories.map { c ->
                                        CategoryChangePayload(
                                            uuid = c.uuid,
                                            ownerId = c.ownerId,
                                            name = c.name,
                                            normalizedName = c.normalizedName,
                                            colorHex = c.colorHex,
                                            createdAt = c.createdAt,
                                            updatedAt = c.updatedAt,
                                        )
                                    }

                                val itemPayloads =
                                    allItems.map { item ->
                                        val parentNote = notesWithItems.firstOrNull { it.note.id == item.noteId }?.note
                                        val parentUuid =
                                            item.parentId?.let { pId ->
                                                allItems.firstOrNull { it.id == pId }?.uuid
                                            }
                                        ChecklistItemChangePayload(
                                            uuid = item.uuid,
                                            noteUuid = parentNote?.uuid ?: "",
                                            ownerId = item.ownerId,
                                            text = item.text,
                                            checked = item.checked,
                                            sortOrder = item.sortOrder,
                                            parentUuid = parentUuid,
                                            depth = item.depth,
                                            details = item.details,
                                        )
                                    }

                                val attachmentPayloads =
                                    allAttachments.map { att ->
                                        val parentNote = notesWithItems.firstOrNull { it.note.id == att.noteId }?.note
                                        AttachmentChangePayload(
                                            uuid = att.uuid,
                                            noteUuid = parentNote?.uuid ?: "",
                                            ownerId = att.ownerId,
                                            uri = att.uri,
                                            displayName = att.displayName,
                                            mimeType = att.mimeType,
                                        )
                                    }

                                // Export Conflict Metadata, Tombstones, Conflicts, Full Exact Ledger
                                val allMetadata =
                                    conflictMetadataDao.getAllMetadata().map {
                                        SnapshotConflictMetadataDto(it.entityType, it.entityId, it.fieldName, it.hlcTimestamp, it.updatedAt)
                                    }
                                val allTombstones =
                                    tombstoneDao.getAllTombstones().map {
                                        SnapshotTombstoneDto(it.entityType, it.entityId, it.deletedAt, it.hlcTimestamp, it.deviceId)
                                    }
                                val allConflicts =
                                    conflictRecordDao.getAllConflictRecords().map {
                                        SnapshotConflictRecordDto(
                                            conflictId = it.conflictId,
                                            entityType = it.entityType,
                                            entityId = it.entityId,
                                            fieldName = it.fieldName,
                                            winningValue = it.winningValue,
                                            losingValue = it.losingValue,
                                            winningHlc = it.winningHlc,
                                            losingHlc = it.losingHlc,
                                            winningDeviceId = it.winningDeviceId,
                                            losingDeviceId = it.losingDeviceId,
                                            detectedAt = it.detectedAt,
                                            resolved = it.resolved,
                                        )
                                    }
                                val allAppliedOps =
                                    appliedOperationDao.getAllOperations().map {
                                        SnapshotAppliedOperationDto(
                                            changeId = it.changeId,
                                            journalId = it.journalId,
                                            deviceId = it.deviceId,
                                            sequence = it.sequence,
                                            entityType = it.entityType,
                                            entityId = it.entityId,
                                            appliedAt = it.appliedAt,
                                            hlcTimestamp = it.hlcTimestamp,
                                        )
                                    }

                                val snapshotHlc = hlcClock.generateNext().toString()

                                val entitiesDto =
                                    SyncSnapshotEntitiesDto(
                                        notes = notePayloads,
                                        checklistItems = itemPayloads,
                                        categories = categoryPayloads,
                                        attachments = attachmentPayloads,
                                        conflictMetadata = allMetadata,
                                        tombstones = allTombstones,
                                        unresolvedConflicts = allConflicts,
                                        appliedOperations = allAppliedOps,
                                    )

                                Triple(coverageVector, snapshotHlc, entitiesDto)
                            }
                        } catch (e: Exception) {
                            return@withLock SnapshotCaptureResult.Error("Failed to freeze and read Room state: ${e.message}", cause = e)
                        }

                    val coverageVector = capturedData.first
                    val snapshotHlc = capturedData.second
                    val entitiesDto = capturedData.third

                    // 3. Freeze Attachment Binaries into Snapshot Staging Directory
                    val stagingDir = File(context.filesDir, "$SNAPSHOT_STAGING_DIR_NAME/$snapshotId").apply { mkdirs() }
                    val stagingAttachmentsDir = File(stagingDir, SyncSnapshotManifest.ATTACHMENTS_DIR_NAME).apply { mkdirs() }
                    val attachmentMetadatas = mutableListOf<SnapshotAttachmentMetadata>()

                    try {
                        for (att in entitiesDto.attachments) {
                            val localFile = resolveAttachmentFile(att.uri)
                            if (localFile != null && localFile.exists() && localFile.isFile) {
                                val safeName = sanitizeFileName(att.displayName)
                                val stagedFile = File(stagingAttachmentsDir, "${att.uuid}_$safeName")
                                localFile.copyTo(stagedFile, overwrite = true)

                                val sha256 = AttachmentPromotionRecoveryManager.computeSha256(stagedFile)
                                attachmentMetadatas.add(
                                    SnapshotAttachmentMetadata(
                                        attachmentUuid = att.uuid,
                                        noteUuid = att.noteUuid,
                                        displayName = att.displayName,
                                        mimeType = att.mimeType,
                                        entryPath = "${SyncSnapshotManifest.ATTACHMENTS_DIR_NAME}/${stagedFile.name}",
                                        sha256Hex = sha256,
                                        sizeBytes = stagedFile.length(),
                                    ),
                                )
                            }
                        }

                        // 4. Create Plain ZIP Archive
                        val plainZipFile = File(stagingDir, "plain_snapshot.zip")
                        val coverageVectorJson = json.encodeToString(coverageVector)
                        val coverageVectorHash = computeStringSha256(coverageVectorJson)

                        val initialManifest =
                            SyncSnapshotManifest(
                                formatVersion = SyncSnapshotManifest.CURRENT_SNAPSHOT_FORMAT_VERSION,
                                databaseVersion = SyncSnapshotManifest.CURRENT_DATABASE_VERSION,
                                snapshotId = snapshotId,
                                accountEmail = accountEmail,
                                accountId = accountId,
                                creatorDeviceId = localDeviceId,
                                createdAtEpochMillis = now,
                                snapshotHlc = snapshotHlc,
                                coverageVector = coverageVector,
                                attachments = attachmentMetadatas,
                                plainArchiveSha256 = "",
                            )

                        FileOutputStream(plainZipFile).use { fos ->
                            ZipOutputStream(fos).use { zip ->
                                // 4a. manifest.json
                                zip.putNextEntry(ZipEntry(SyncSnapshotManifest.MANIFEST_ENTRY_NAME))
                                zip.write(json.encodeToString(initialManifest).toByteArray(Charsets.UTF_8))
                                zip.closeEntry()

                                // 4b. entities.json
                                zip.putNextEntry(ZipEntry(SyncSnapshotManifest.ENTITIES_ENTRY_NAME))
                                zip.write(json.encodeToString(entitiesDto).toByteArray(Charsets.UTF_8))
                                zip.closeEntry()

                                // 4c. attachments/
                                for (meta in attachmentMetadatas) {
                                    val fileToZip = File(stagingAttachmentsDir, File(meta.entryPath).name)
                                    if (fileToZip.exists()) {
                                        zip.putNextEntry(ZipEntry(meta.entryPath))
                                        FileInputStream(fileToZip).use { fis -> fis.copyTo(zip, bufferSize = 8192) }
                                        zip.closeEntry()
                                    }
                                }
                            }
                        }

                        val plainSha256 = AttachmentPromotionRecoveryManager.computeSha256(plainZipFile)
                        val finalManifest = initialManifest.copy(plainArchiveSha256 = plainSha256)

                        // 5. Encrypt with AES-256-GCM via EncryptedContainer
                        val plainBytes = plainZipFile.readBytes()
                        val aad =
                            buildSnapshotAad(
                                formatVersion = finalManifest.formatVersion,
                                snapshotId = snapshotId,
                                accountEmail = accountEmail,
                                creatorDeviceId = localDeviceId,
                                coverageVectorHash = coverageVectorHash,
                            )

                        val encryptedBytes =
                            EncryptedContainer.encrypt(
                                plaintext = plainBytes,
                                key = key,
                                aad = aad,
                            )

                        // 6. Atomically persist encrypted container to permanent snapshot storage
                        val snapshotsDir = File(context.filesDir, SNAPSHOTS_DIR_NAME).apply { mkdirs() }
                        val targetFile = File(snapshotsDir, "snapshot_$snapshotId.bin")
                        val tempTarget = File(snapshotsDir, "snapshot_$snapshotId.tmp")

                        FileOutputStream(tempTarget).use { fos ->
                            fos.write(encryptedBytes)
                            fos.flush()
                            fos.fd.sync()
                        }

                        if (!tempTarget.renameTo(targetFile)) {
                            tempTarget.copyTo(targetFile, overwrite = true)
                            tempTarget.delete()
                        }

                        val contentSha256 = AttachmentPromotionRecoveryManager.computeSha256(targetFile)

                        // 7. Insert into Room with status READY_TO_UPLOAD (Mandatory Constraint 2)
                        val snapshotEntity =
                            SyncSnapshotEntity(
                                snapshotId = snapshotId,
                                creatorDeviceId = localDeviceId,
                                createdAt = now,
                                formatVersion = finalManifest.formatVersion,
                                storagePath = targetFile.absolutePath,
                                contentSha256 = contentSha256,
                                driveFileId = null,
                                coverageVectorJson = coverageVectorJson,
                                snapshotHlc = snapshotHlc,
                                status = SnapshotStatus.READY_TO_UPLOAD,
                                updatedAt = now,
                            )

                        snapshotDao.insert(snapshotEntity)
                        SnapshotCaptureResult.Success(snapshotEntity, finalManifest)
                    } catch (e: Exception) {
                        SnapshotCaptureResult.Error("Failed to package and encrypt snapshot: ${e.message}", cause = e)
                    } finally {
                        stagingDir.deleteRecursively()
                    }
                }
            }

        /**
         * Atomically restores the local Room database and attachments from a verified snapshot container.
         */
        suspend fun restoreSnapshot(
            snapshotFile: File,
            accountEmail: String,
            overrideKey: SecretKey? = null,
        ): SnapshotRestoreResult =
            withContext(ioDispatcher) {
                snapshotMutex.withLock {
                    if (!snapshotFile.exists() || snapshotFile.length() <= 0) {
                        return@withLock SnapshotRestoreResult.Error("Snapshot file does not exist or is empty")
                    }

                    val key =
                        overrideKey ?: mekManager.getActiveMasterKey()
                            ?: return@withLock SnapshotRestoreResult.Error("Master Encryption Key is not available for restoration")

                    val stagingDir = File(context.filesDir, "$SNAPSHOT_STAGING_DIR_NAME/restore_${UUID.randomUUID()}").apply { mkdirs() }

                    try {
                        // 1. Decrypt AES-256-GCM container
                        val fileBytes = snapshotFile.readBytes()
                        val (plainBytes, decryptedManifest) =
                            try {
                                val decrypted =
                                    EncryptedContainer.decrypt(
                                        containerBytes = fileBytes,
                                        key = key,
                                    )
                                // Extract manifest to compute expected AAD
                                val manifest = peekManifestFromZip(decrypted)
                                val expectedAad =
                                    buildSnapshotAad(
                                        formatVersion = manifest.formatVersion,
                                        snapshotId = manifest.snapshotId,
                                        accountEmail = accountEmail,
                                        creatorDeviceId = manifest.creatorDeviceId,
                                        coverageVectorHash = computeStringSha256(json.encodeToString(manifest.coverageVector)),
                                    )

                                // Strict authenticated re-check with exact AAD
                                val authenticated =
                                    EncryptedContainer.decrypt(
                                        containerBytes = fileBytes,
                                        key = key,
                                        expectedAad = expectedAad,
                                    )
                                authenticated to manifest
                            } catch (aead: AEADBadTagException) {
                                throw CorruptSnapshotException("Cryptographic verification failed: authentication tag mismatch or corrupted ciphertext", aead)
                            } catch (e: Exception) {
                                throw CorruptSnapshotException("Failed to decrypt snapshot container: ${e.message}", cause = e)
                            }

                        // 2. Validate formatVersion and databaseVersion compatibility (Mandatory Constraint 3)
                        if (decryptedManifest.formatVersion > SyncSnapshotManifest.CURRENT_SNAPSHOT_FORMAT_VERSION) {
                            throw UnsupportedSnapshotVersionException(
                                "Unsupported snapshot formatVersion ${decryptedManifest.formatVersion}, maximum supported is ${SyncSnapshotManifest.CURRENT_SNAPSHOT_FORMAT_VERSION}",
                            )
                        }
                        if (decryptedManifest.databaseVersion > SyncSnapshotManifest.CURRENT_DATABASE_VERSION) {
                            throw UnsupportedSnapshotVersionException(
                                "Unsupported snapshot databaseVersion ${decryptedManifest.databaseVersion}, local database version is ${SyncSnapshotManifest.CURRENT_DATABASE_VERSION}",
                            )
                        }

                        // 3. Extract Archive to Staging Directory
                        val extractedAttachmentsDir = File(stagingDir, SyncSnapshotManifest.ATTACHMENTS_DIR_NAME).apply { mkdirs() }
                        var entitiesDto: SyncSnapshotEntitiesDto? = null

                        ZipInputStream(plainBytes.inputStream()).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name
                                when {
                                    name == SyncSnapshotManifest.ENTITIES_ENTRY_NAME -> {
                                        val content = zip.readBytes().toString(Charsets.UTF_8)
                                        entitiesDto = json.decodeFromString<SyncSnapshotEntitiesDto>(content)
                                    }
                                    name.startsWith("${SyncSnapshotManifest.ATTACHMENTS_DIR_NAME}/") && !entry.isDirectory -> {
                                        val fileName = File(name).name
                                        val targetFile = File(extractedAttachmentsDir, fileName)
                                        targetFile.outputStream().use { fos -> zip.copyTo(fos) }
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }

                        if (entitiesDto == null) {
                            throw CorruptSnapshotException("Snapshot archive is missing ${SyncSnapshotManifest.ENTITIES_ENTRY_NAME}")
                        }

                        // 4. Verify all attachment hashes against manifest
                        val targetAttachmentsDir = File(context.filesDir, AttachmentPromotionRecoveryManager.NOTE_ATTACHMENTS_DIR)

                        for (meta in decryptedManifest.attachments) {
                            val stagedFile = File(extractedAttachmentsDir, File(meta.entryPath).name)
                            if (!stagedFile.exists() || stagedFile.length() != meta.sizeBytes) {
                                throw AttachmentIntegrityException("Attachment file ${meta.displayName} is missing or size mismatch in snapshot payload")
                            }
                            val actualHash = AttachmentPromotionRecoveryManager.computeSha256(stagedFile)
                            if (!actualHash.equals(meta.sha256Hex, ignoreCase = true)) {
                                throw AttachmentIntegrityException("Attachment hash mismatch for ${meta.displayName}: expected ${meta.sha256Hex}, actual $actualHash")
                            }
                        }

                        // 5. Atomic SQLite Hydration inside database.withTransaction
                        val entities = entitiesDto
                        var promotionManifest: AttachmentPromotionManifest? = null
                        runInTransaction {
                            // Record promotion state COMMITTED
                            promotionDao.insertOrUpdate(
                                SyncAttachmentPromotionEntity(
                                    snapshotId = decryptedManifest.snapshotId,
                                    status = PromotionStatus.COMMITTED,
                                    createdAt = clock(),
                                    updatedAt = clock(),
                                ),
                            )

                            // Clear existing entity tables
                            noteDao.deleteAllNotes()
                            shoulderDao.allShoulders().forEach { shoulderDao.deleteShoulder(it) }
                            itemDao.deleteForNote(-1L)
                            attachmentDao.deleteForNote(-1L)

                            // Clear and restore conflict metadata, tombstones, conflict records, full ledger
                            conflictMetadataDao.clearAll()
                            tombstoneDao.clearAll()
                            conflictRecordDao.clearAll()
                            appliedOperationDao.clearAll()

                            // Restore Categories
                            val categoryUuidToIdMap = mutableMapOf<String, Long>()
                            for (cat in entities.categories) {
                                val shoulder =
                                    haddad.three.notas.data.ShoulderEntity(
                                        id = 0L,
                                        uuid = cat.uuid,
                                        ownerId = cat.ownerId,
                                        name = cat.name,
                                        normalizedName = cat.normalizedName,
                                        colorHex = cat.colorHex,
                                        createdAt = cat.createdAt,
                                        updatedAt = cat.updatedAt,
                                    )
                                val insertedId = shoulderDao.insertShoulder(shoulder)
                                categoryUuidToIdMap[cat.uuid] = insertedId
                            }

                            // Restore Notes
                            val noteUuidToIdMap = mutableMapOf<String, Long>()
                            for (n in entities.notes) {
                                val shoulderId = n.categoryUuid?.let { categoryUuidToIdMap[it] }
                                val noteEntity =
                                    haddad.three.notas.data.NoteEntity(
                                        id = 0L,
                                        uuid = n.uuid,
                                        ownerId = n.ownerId,
                                        kind =
                                            runCatching {
                                                haddad.three.notas.data.NoteKind
                                                    .valueOf(n.kind)
                                            }.getOrDefault(haddad.three.notas.data.NoteKind.NOTE),
                                        title = n.title,
                                        body = n.body,
                                        colorIndex = n.colorIndex,
                                        trashed = n.trashed,
                                        createdAt = n.createdAt,
                                        updatedAt = n.updatedAt,
                                        reminderAt = n.reminderAt,
                                        endAt = n.endAt,
                                        isAllDay = n.isAllDay,
                                        importance =
                                            runCatching {
                                                haddad.three.notas.data.Importance
                                                    .valueOf(n.importance)
                                            }.getOrDefault(haddad.three.notas.data.Importance.DEFAULT),
                                        visibility =
                                            runCatching {
                                                haddad.three.notas.data.Visibility
                                                    .valueOf(n.visibility)
                                            }.getOrDefault(haddad.three.notas.data.Visibility.DEFAULT),
                                        pictureUri = n.pictureUri,
                                        pictureHeroFraming = n.pictureHeroFraming,
                                        locked = n.locked,
                                        iconKey = n.iconKey,
                                        shoulderId = shoulderId,
                                        archived = n.archived,
                                        trashedAt = n.trashedAt,
                                        completedAt = n.completedAt,
                                        pinnedAt = n.pinnedAt,
                                    )
                                val insertedId = noteDao.insert(noteEntity)
                                noteUuidToIdMap[n.uuid] = insertedId
                            }

                            // Restore Checklist Items
                            for (item in entities.checklistItems) {
                                val parentNoteId = noteUuidToIdMap[item.noteUuid] ?: continue
                                val checklistEntity =
                                    haddad.three.notas.data.ChecklistItemEntity(
                                        id = 0L,
                                        uuid = item.uuid,
                                        noteId = parentNoteId,
                                        ownerId = item.ownerId,
                                        text = item.text,
                                        checked = item.checked,
                                        sortOrder = item.sortOrder,
                                        parentId = null,
                                        depth = item.depth,
                                        details = item.details,
                                    )
                                itemDao.insert(checklistEntity)
                            }

                            // Restore Attachments with target URIs
                            for (att in entities.attachments) {
                                val parentNoteId = noteUuidToIdMap[att.noteUuid] ?: continue
                                val safeName = sanitizeFileName(att.displayName)
                                val targetFile = File(targetAttachmentsDir, "$parentNoteId/${att.uuid}_$safeName")
                                val attachmentEntity =
                                    haddad.three.notas.data.NoteAttachmentEntity(
                                        id = 0L,
                                        uuid = att.uuid,
                                        noteId = parentNoteId,
                                        ownerId = att.ownerId,
                                        uri = targetFile.absolutePath,
                                        displayName = att.displayName,
                                        mimeType = att.mimeType,
                                    )
                                attachmentDao.insert(attachmentEntity)
                            }

                            // Build and stage Promotion Manifest with resolved note IDs before committing transaction
                            val resolvedPromotionEntries =
                                decryptedManifest.attachments.mapNotNull { meta ->
                                    val parentNoteId = noteUuidToIdMap[meta.noteUuid] ?: return@mapNotNull null
                                    val stagedFile = File(extractedAttachmentsDir, File(meta.entryPath).name)
                                    val safeName = sanitizeFileName(meta.displayName)
                                    AttachmentPromotionFileEntry(
                                        attachmentUuid = meta.attachmentUuid,
                                        noteId = parentNoteId,
                                        stagingFileName = stagedFile.name,
                                        targetFileName = "${meta.attachmentUuid}_$safeName",
                                        sha256Hex = meta.sha256Hex,
                                        sizeBytes = meta.sizeBytes,
                                    )
                                }
                            val builtManifest =
                                AttachmentPromotionManifest(
                                    snapshotId = decryptedManifest.snapshotId,
                                    stagingDirPath = extractedAttachmentsDir.absolutePath,
                                    targetDirPath = targetAttachmentsDir.absolutePath,
                                    createdAtEpochMillis = clock(),
                                    entries = resolvedPromotionEntries,
                                )
                            promotionRecoveryManager.writePromotionManifest(builtManifest)
                            promotionManifest = builtManifest

                            // Restore Conflict Metadata, Tombstones, Conflicts, Full Exact Ledger
                            conflictMetadataDao.insertAll(
                                entities.conflictMetadata.map {
                                    haddad.three.notas.data.sync.conflict
                                        .SyncConflictMetadataEntity(it.entityType, it.entityId, it.fieldName, it.hlcTimestamp, it.updatedAt)
                                },
                            )
                            tombstoneDao.insertAll(
                                entities.tombstones.map {
                                    haddad.three.notas.data.sync.conflict
                                        .SyncTombstoneEntity(it.entityType, it.entityId, it.deletedAt, it.hlcTimestamp, it.deviceId)
                                },
                            )
                            conflictRecordDao.insertAll(
                                entities.unresolvedConflicts.map {
                                    haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity(
                                        conflictId = it.conflictId,
                                        entityType = it.entityType,
                                        entityId = it.entityId,
                                        fieldName = it.fieldName,
                                        winningValue = it.winningValue,
                                        losingValue = it.losingValue,
                                        winningHlc = it.winningHlc,
                                        losingHlc = it.losingHlc,
                                        winningDeviceId = it.winningDeviceId,
                                        losingDeviceId = it.losingDeviceId,
                                        detectedAt = it.detectedAt,
                                        resolved = it.resolved,
                                    )
                                },
                            )
                            appliedOperationDao.insertAll(
                                entities.appliedOperations.map {
                                    haddad.three.notas.data.sync.conflict.SyncAppliedOperationEntity(
                                        changeId = it.changeId,
                                        journalId = it.journalId,
                                        deviceId = it.deviceId,
                                        sequence = it.sequence,
                                        entityType = it.entityType,
                                        entityId = it.entityId,
                                        appliedAt = it.appliedAt,
                                        hlcTimestamp = it.hlcTimestamp,
                                    )
                                },
                            )

                            // Set Watermarks for all devices
                            for ((devId, seq) in decryptedManifest.coverageVector) {
                                watermarkDao.insertOrUpdate(
                                    haddad.three.notas.data.sync.transport.SyncWatermarkEntity(
                                        deviceId = devId,
                                        highestDiscoveredSeq = seq,
                                        highestDownloadedSeq = seq,
                                        highestValidatedSeq = seq,
                                        highestAppliedSeq = seq,
                                        updatedAt = clock(),
                                    ),
                                )
                            }

                            // Advance HLC
                            hlcClock.mergeRemote(
                                haddad.three.notas.data.sync.hlc.Hlc
                                    .parse(decryptedManifest.snapshotHlc),
                            )
                        }

                        // 7. Physical Promotion: Promote Staged Attachments to Permanent Storage
                        promotionManifest?.let {
                            promotionRecoveryManager.promoteStagedAttachments(it)
                        }
                        SnapshotRestoreResult.Success(decryptedManifest)
                    } catch (e: Exception) {
                        SnapshotRestoreResult.Error("Failed to restore snapshot: ${e.message}", cause = e)
                    } finally {
                        stagingDir.deleteRecursively()
                    }
                }
            }

        private fun peekManifestFromZip(zipBytes: ByteArray): SyncSnapshotManifest {
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == SyncSnapshotManifest.MANIFEST_ENTRY_NAME) {
                        val content = zip.readBytes().toString(Charsets.UTF_8)
                        return json.decodeFromString<SyncSnapshotManifest>(content)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            throw CorruptSnapshotException("Manifest not found in snapshot zip")
        }

        private fun resolveAttachmentFile(uriString: String): File? {
            val file = File(uriString)
            if (file.exists() && file.isFile) return file
            return null
        }

        private fun sanitizeFileName(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
    }
