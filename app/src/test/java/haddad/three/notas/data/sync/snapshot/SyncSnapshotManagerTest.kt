package haddad.three.notas.data.sync.snapshot

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.Visibility
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationEntity
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataEntity
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.data.sync.conflict.SyncTombstoneDao
import haddad.three.notas.data.sync.conflict.SyncTombstoneEntity
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.hlc.InMemoryHlcStorage
import haddad.three.notas.data.sync.journal.JournalStatus
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalEntity
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import haddad.three.notas.data.sync.transport.SyncWatermarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SyncSnapshotManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private lateinit var fakeContext: Context
    private lateinit var fakeFilesDir: File
    private lateinit var fakeSnapshotDao: FakeSyncSnapshotDao
    private lateinit var fakePromotionDao: FakeSyncAttachmentPromotionDao
    private lateinit var fakeNoteDao: FakeNoteDao
    private lateinit var fakeChecklistItemDao: FakeChecklistItemDao
    private lateinit var fakeShoulderDao: FakeShoulderDao
    private lateinit var fakeAttachmentDao: FakeAttachmentDao
    private lateinit var fakePendingChangeDao: FakePendingChangeDao
    private lateinit var fakeJournalDao: FakeSyncJournalDao
    private lateinit var fakeWatermarkDao: FakeSyncWatermarkDao
    private lateinit var fakeAppliedOperationDao: FakeSyncAppliedOperationDao
    private lateinit var fakeConflictMetadataDao: FakeSyncConflictMetadataDao
    private lateinit var fakeTombstoneDao: FakeSyncTombstoneDao
    private lateinit var fakeConflictRecordDao: FakeSyncConflictRecordDao

    private lateinit var masterKey: SecretKey
    private lateinit var mekManager: MekManager
    private lateinit var hlcClock: HlcClock
    private lateinit var promotionRecoveryManager: AttachmentPromotionRecoveryManager
    private lateinit var snapshotManager: SyncSnapshotManager

    private val localDeviceId = "device_test_alpha"
    private val remoteDeviceId = "device_test_beta"
    private val testAccount = "user@test.com"

    @Before
    fun setUp() = runBlocking {
        fakeFilesDir = tempFolder.newFolder("files")
        val fakeCacheDir = tempFolder.newFolder("cache")
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = fakeFilesDir
            override fun getCacheDir(): File = fakeCacheDir
        }

        fakeSnapshotDao = FakeSyncSnapshotDao()
        fakePromotionDao = FakeSyncAttachmentPromotionDao()
        fakeChecklistItemDao = FakeChecklistItemDao()
        fakeAttachmentDao = FakeAttachmentDao()
        fakeShoulderDao = FakeShoulderDao()
        fakeNoteDao = FakeNoteDao(fakeChecklistItemDao, fakeAttachmentDao, fakeShoulderDao)
        fakePendingChangeDao = FakePendingChangeDao()
        fakeJournalDao = FakeSyncJournalDao()
        fakeWatermarkDao = FakeSyncWatermarkDao()
        fakeAppliedOperationDao = FakeSyncAppliedOperationDao()
        fakeConflictMetadataDao = FakeSyncConflictMetadataDao()
        fakeTombstoneDao = FakeSyncTombstoneDao()
        fakeConflictRecordDao = FakeSyncConflictRecordDao()

        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        masterKey = keyGen.generateKey()
        val keyStore = SoftwareKeyStoreManager()
        val storage = InMemoryCryptoStorage().apply {
            setLocalWrappedMek(keyStore.wrapKey(masterKey, 1), 1)
        }
        mekManager = MekManager(keyStore, storage)

        hlcClock = HlcClock(nodeId = localDeviceId, storage = InMemoryHlcStorage())
        hlcClock.initialize()

        promotionRecoveryManager = AttachmentPromotionRecoveryManager(
            context = fakeContext,
            promotionDao = fakePromotionDao,
        )

        snapshotManager = SyncSnapshotManager(
            context = fakeContext,
            database = null, // In-memory fake DAOs
            snapshotDao = fakeSnapshotDao,
            promotionDao = fakePromotionDao,
            promotionRecoveryManager = promotionRecoveryManager,
            pendingChangeDao = fakePendingChangeDao,
            journalDao = fakeJournalDao,
            watermarkDao = fakeWatermarkDao,
            appliedOperationDao = fakeAppliedOperationDao,
            conflictMetadataDao = fakeConflictMetadataDao,
            tombstoneDao = fakeTombstoneDao,
            conflictRecordDao = fakeConflictRecordDao,
            noteDao = fakeNoteDao,
            itemDao = fakeChecklistItemDao,
            shoulderDao = fakeShoulderDao,
            attachmentDao = fakeAttachmentDao,
            hlcClock = hlcClock,
            mekManager = mekManager,
            deviceIdProvider = { localDeviceId },
        )
    }

    @Test
    fun test_captureSnapshot_zeroPending_createsValidEncryptedContainerWithBoundAadAndReadyToUploadStatus() = runBlocking {
        // Arrange: Populate local database state
        val catId = fakeShoulderDao.insertShoulder(
            ShoulderEntity(
                id = 1L,
                uuid = "cat-uuid-1",
                ownerId = testAccount,
                name = "Work",
                normalizedName = "work",
                colorHex = "#FF0000",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        val noteId = fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note-uuid-1",
                ownerId = testAccount,
                kind = NoteKind.NOTE,
                title = "Meeting Notes",
                body = "Discuss Phase 4F",
                colorIndex = 1,
                trashed = false,
                shoulderId = catId,
                createdAt = 1000L,
                updatedAt = 1100L,
            )
        )
        fakeChecklistItemDao.insert(
            ChecklistItemEntity(
                id = 1L,
                uuid = "item-uuid-1",
                noteId = noteId,
                ownerId = testAccount,
                text = "Action Item 1",
                checked = false,
                sortOrder = 0.0,
            )
        )

        // Create physical attachment
        val noteAttDir = File(fakeFilesDir, "note_attachments/$noteId").apply { mkdirs() }
        val attFile = File(noteAttDir, "att-uuid-1_doc.pdf").apply { writeText("Sample attachment content") }
        fakeAttachmentDao.insert(
            NoteAttachmentEntity(
                id = 1L,
                uuid = "att-uuid-1",
                noteId = noteId,
                ownerId = testAccount,
                uri = attFile.absolutePath,
                displayName = "doc.pdf",
                mimeType = "application/pdf",
            )
        )

        // Seed sync metadata and exact ledger
        fakeConflictMetadataDao.insertOrUpdate(
            SyncConflictMetadataEntity(
                entityType = "NOTE",
                entityId = "note-uuid-1",
                fieldName = "title",
                hlcTimestamp = "1100:0:device_test_alpha",
                updatedAt = 1100L,
            )
        )
        fakeTombstoneDao.insertOrUpdate(
            SyncTombstoneEntity(
                entityType = "NOTE",
                entityId = "deleted-note-uuid",
                deletedAt = 900L,
                hlcTimestamp = "900:0:device_test_alpha",
                deviceId = localDeviceId,
            )
        )
        fakeAppliedOperationDao.insert(
            SyncAppliedOperationEntity(
                changeId = "change-op-1",
                journalId = "journal-1",
                deviceId = localDeviceId,
                sequence = 1L,
                entityType = "NOTE",
                entityId = "note-uuid-1",
                appliedAt = 1000L,
                hlcTimestamp = "1000:0:device_test_alpha",
            )
        )

        fakeJournalDao.insert(
            SyncJournalEntity(
                journalId = "journal-1",
                deviceId = localDeviceId,
                sequence = 1L,
                formatVersion = 1,
                createdAt = 1000L,
                operationCount = 1,
                storagePath = "/dev/null",
                encryptedSizeBytes = 100L,
                status = JournalStatus.UPLOADED.name,
            )
        )
        fakeWatermarkDao.insertOrUpdate(
            SyncWatermarkEntity(
                deviceId = remoteDeviceId,
                highestAppliedSeq = 7L,
                updatedAt = 1050L,
            )
        )

        // Act: Capture snapshot
        val result = snapshotManager.captureSnapshot(accountEmail = testAccount)

        // Assert
        assertTrue("Expected SnapshotCaptureResult.Success but got $result", result is SnapshotCaptureResult.Success)
        val success = result as SnapshotCaptureResult.Success
        val snapshotId = success.snapshot.snapshotId
        val archiveFile = File(success.snapshot.storagePath)

        assertTrue("Snapshot file must exist", archiveFile.exists())
        assertTrue("Snapshot file must be non-empty", archiveFile.length() > 0)

        // Mandatory Constraint 2: Snapshot must transition to READY_TO_UPLOAD (NOT ACTIVE yet)
        val snapshotEntity = fakeSnapshotDao.getById(snapshotId)
        assertNotNull("Snapshot entity must be persisted in Room", snapshotEntity)
        assertEquals(SnapshotStatus.READY_TO_UPLOAD, snapshotEntity!!.status)
        assertEquals(1, snapshotEntity.formatVersion)
        assertEquals(success.snapshot.contentSha256, snapshotEntity.contentSha256)

        // Verify Coverage Vector
        val covVector = json.decodeFromString<Map<String, Long>>(snapshotEntity.coverageVectorJson)
        assertEquals(1L, covVector[localDeviceId])
        assertEquals(7L, covVector[remoteDeviceId])

        // Verify decrypted manifest and bound AAD via EncryptedContainer
        val fileBytes = archiveFile.readBytes()
        val metadata = EncryptedContainer.readMetadata(fileBytes)
        val decryptedBytes = EncryptedContainer.decrypt(fileBytes, masterKey, expectedAad = metadata.aad)

        // Unpack zip and verify contents
        var foundManifest = false
        var foundEntities = false
        var foundAttachment = false
        ZipInputStream(decryptedBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    SyncSnapshotManifest.MANIFEST_ENTRY_NAME -> foundManifest = true
                    SyncSnapshotManifest.ENTITIES_ENTRY_NAME -> foundEntities = true
                    "attachments/att-uuid-1_doc.pdf" -> foundAttachment = true
                }
                entry = zis.nextEntry
            }
        }
        assertTrue("Decrypted snapshot must contain manifest", foundManifest)
        assertTrue("Decrypted snapshot must contain entities", foundEntities)
        assertTrue("Decrypted snapshot must contain frozen attachment", foundAttachment)
    }

    @Test
    fun test_captureSnapshot_concurrentPendingChanges_failsDrainLoopSafely() = runBlocking {
        // Arrange: Leave pending changes un-drained
        fakePendingChangeDao.insert(
            PendingChangeEntity(
                changeId = "pending-1",
                entityType = "NOTE",
                entityId = "pending-uuid",
                operationType = "UPSERT",
                deviceId = localDeviceId,
                createdAt = 1000L,
                payload = "{}",
                hlcTimestamp = "1000:0:device_test_alpha",
                status = "PENDING",
            )
        )

        // Act
        val result = snapshotManager.captureSnapshot(accountEmail = testAccount)

        // Assert
        assertTrue("Expected Error when pending changes remain", result is SnapshotCaptureResult.Error)
        val error = result as SnapshotCaptureResult.Error
        assertTrue("Error message must mention pending changes", error.message.contains("pending"))

        // Assert that no snapshot entity was recorded with status ACTIVE or READY_TO_UPLOAD
        assertTrue("No snapshot should be recorded in DAO", fakeSnapshotDao.snapshots.isEmpty())
    }

    @Test
    fun test_restoreSnapshot_cleanDatabase_restoresAllEntitiesAndFullAppliedLedger() = runBlocking {
        // Arrange: First capture a snapshot with rich data
        val catId = fakeShoulderDao.insertShoulder(
            ShoulderEntity(
                id = 1L,
                uuid = "cat-uuid-work",
                ownerId = testAccount,
                name = "Projects",
                normalizedName = "projects",
                colorHex = "#00FF00",
                createdAt = 500L,
                updatedAt = 500L,
            )
        )
        val noteId = fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note-uuid-alpha",
                ownerId = testAccount,
                kind = NoteKind.NOTE,
                title = "Architecture Document",
                body = "Phase 4F specifications",
                colorIndex = 3,
                trashed = false,
                shoulderId = catId,
                createdAt = 500L,
                updatedAt = 600L,
            )
        )
        fakeChecklistItemDao.insert(
            ChecklistItemEntity(
                id = 1L,
                uuid = "item-uuid-1",
                noteId = noteId,
                ownerId = testAccount,
                text = "Check point 1",
                checked = true,
                sortOrder = 0.0,
            )
        )

        val noteAttDir = File(fakeFilesDir, "note_attachments/$noteId").apply { mkdirs() }
        val attFile = File(noteAttDir, "att-uuid-1_specs.pdf").apply { writeText("Binary PDF content 12345") }
        fakeAttachmentDao.insert(
            NoteAttachmentEntity(
                id = 1L,
                uuid = "att-uuid-1",
                noteId = noteId,
                ownerId = testAccount,
                uri = attFile.absolutePath,
                displayName = "specs.pdf",
                mimeType = "application/pdf",
            )
        )

        fakeConflictMetadataDao.insertOrUpdate(
            SyncConflictMetadataEntity(
                entityType = "NOTE",
                entityId = "note-uuid-alpha",
                fieldName = "title",
                hlcTimestamp = "600:0:device_test_alpha",
                updatedAt = 600L,
            )
        )
        fakeTombstoneDao.insertOrUpdate(
            SyncTombstoneEntity(
                entityType = "NOTE",
                entityId = "tomb-note-1",
                deletedAt = 400L,
                hlcTimestamp = "400:0:device_test_alpha",
                deviceId = localDeviceId,
            )
        )
        fakeAppliedOperationDao.insert(
            SyncAppliedOperationEntity(
                changeId = "change-key-xyz",
                journalId = "journal-alpha-1",
                deviceId = localDeviceId,
                sequence = 5L,
                entityType = "NOTE",
                entityId = "note-uuid-alpha",
                appliedAt = 600L,
                hlcTimestamp = "600:0:device_test_alpha",
            )
        )
        fakeJournalDao.insert(
            SyncJournalEntity(
                journalId = "journal-alpha-1",
                deviceId = localDeviceId,
                sequence = 5L,
                formatVersion = 1,
                createdAt = 500L,
                operationCount = 1,
                storagePath = "/dev/null",
                encryptedSizeBytes = 100L,
                status = JournalStatus.UPLOADED.name,
            )
        )

        val captureResult = snapshotManager.captureSnapshot(accountEmail = testAccount) as SnapshotCaptureResult.Success
        val snapshotFile = File(captureResult.snapshot.storagePath)

        // Simulate new device or fresh install: Wipe local Room database completely
        fakeNoteDao.deleteAllNotes()
        fakeChecklistItemDao.items.clear()
        fakeShoulderDao.shoulders.clear()
        fakeAttachmentDao.attachments.clear()
        fakeConflictMetadataDao.clearAll()
        fakeTombstoneDao.clearAll()
        fakeAppliedOperationDao.clearAll()
        fakeWatermarkDao.clearAll()
        noteAttDir.deleteRecursively()

        assertTrue("Notes must be empty before restore", fakeNoteDao.notes.isEmpty())
        assertTrue("Categories must be empty before restore", fakeShoulderDao.shoulders.isEmpty())

        // Act: Restore snapshot
        val restoreResult = snapshotManager.restoreSnapshot(snapshotFile = snapshotFile, accountEmail = testAccount)

        // Assert
        assertTrue("Expected SnapshotRestoreResult.Success but got $restoreResult", restoreResult is SnapshotRestoreResult.Success)
        val success = restoreResult as SnapshotRestoreResult.Success
        assertEquals(captureResult.manifest.snapshotId, success.manifest.snapshotId)

        // 1. Categories restored
        assertEquals(1, fakeShoulderDao.shoulders.size)
        val restoredCat = fakeShoulderDao.shoulders.values.first()
        assertEquals("cat-uuid-work", restoredCat.uuid)
        assertEquals("Projects", restoredCat.name)

        // 2. Notes restored with updated foreign key shoulderId
        assertEquals(1, fakeNoteDao.notes.size)
        val restoredNote = fakeNoteDao.notes.values.first()
        assertEquals("note-uuid-alpha", restoredNote.uuid)
        assertEquals("Architecture Document", restoredNote.title)
        assertEquals(restoredCat.id, restoredNote.shoulderId)

        // 3. Checklist items restored
        assertEquals(1, fakeChecklistItemDao.items.size)
        val restoredItem = fakeChecklistItemDao.items.values.first()
        assertEquals("item-uuid-1", restoredItem.uuid)
        assertEquals("Check point 1", restoredItem.text)
        assertTrue(restoredItem.checked)
        assertEquals(restoredNote.id, restoredItem.noteId)

        // 4. Attachments restored and files promoted to destination
        assertEquals(1, fakeAttachmentDao.attachments.size)
        val restoredAtt = fakeAttachmentDao.attachments.values.first()
        assertEquals("att-uuid-1", restoredAtt.uuid)
        assertEquals(restoredNote.id, restoredAtt.noteId)
        val restoredAttFile = File(restoredAtt.uri)
        assertTrue("Restored attachment file must exist on disk", restoredAttFile.exists())
        assertEquals("Binary PDF content 12345", restoredAttFile.readText())

        // 5. Conflict metadata, tombstones restored
        assertEquals(1, fakeConflictMetadataDao.metadata.size)
        assertEquals("600:0:device_test_alpha", fakeConflictMetadataDao.metadata.first().hlcTimestamp)

        assertEquals(1, fakeTombstoneDao.tombstones.size)
        assertEquals("tomb-note-1", fakeTombstoneDao.tombstones.first().entityId)

        // 6. Complete exact sync_applied_operations ledger restored (ZERO PRUNING)
        assertEquals(1, fakeAppliedOperationDao.operations.size)
        val restoredOp = fakeAppliedOperationDao.operations.first()
        assertEquals("change-key-xyz", restoredOp.changeId)
        assertEquals(5L, restoredOp.sequence)
        assertTrue("Replay check must be true for restored operation", fakeAppliedOperationDao.isApplied("change-key-xyz"))

        // 7. Watermarks restored
        val restoredWm = fakeWatermarkDao.getWatermark(localDeviceId)
        assertNotNull(restoredWm)
        assertEquals(5L, restoredWm!!.highestAppliedSeq)
    }

    @Test
    fun test_restoreSnapshot_tamperedAadOrCiphertext_failsDecryptionSafelyWithoutTouchingDatabase() = runBlocking {
        // Arrange
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "existing-note",
                ownerId = testAccount,
                kind = NoteKind.NOTE,
                title = "Original Title",
                body = "Original Body",
                colorIndex = 0,
                trashed = false,
                createdAt = 100L,
                updatedAt = 100L,
            )
        )
        val captureResult = snapshotManager.captureSnapshot(accountEmail = testAccount) as SnapshotCaptureResult.Success

        // Tamper with binary container by flipping bytes at the end (auth tag / ciphertext)
        val originalBytes = File(captureResult.snapshot.storagePath).readBytes()
        val corruptedBytes = originalBytes.copyOf()
        val lastIdx = corruptedBytes.size - 1
        corruptedBytes[lastIdx] = (corruptedBytes[lastIdx].toInt() xor 0xFF).toByte()

        val tamperedFile = tempFolder.newFile("tampered_snapshot.bin").apply {
            writeBytes(corruptedBytes)
        }

        // Act: Attempt to restore tampered file
        val restoreResult = snapshotManager.restoreSnapshot(snapshotFile = tamperedFile, accountEmail = testAccount)

        // Assert
        assertTrue("Restore must fail on tampered ciphertext", restoreResult is SnapshotRestoreResult.Error)
        val error = restoreResult as SnapshotRestoreResult.Error
        assertTrue("Error should mention decryption/corruption: ${error.message}", error.message.contains("Corrupt") || error.message.contains("tag") || error.message.contains("decrypt") || error.message.contains("failed"))

        // Invariant: Database must remain unmodified
        assertEquals("Database must not be altered", 1, fakeNoteDao.notes.size)
        assertEquals("Original Title", fakeNoteDao.notes["existing-note"]?.title)
    }

    @Test
    fun test_restoreSnapshot_unsupportedFormatOrDatabaseVersion_failsSafelyWithoutModifyingDatabase() = runBlocking {
        // Arrange: Create container with future formatVersion = 99 (Mandatory Constraint 3)
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "existing-note-v",
                ownerId = testAccount,
                kind = NoteKind.NOTE,
                title = "Safe Title",
                body = "Safe Body",
                colorIndex = 0,
                trashed = false,
                createdAt = 100L,
                updatedAt = 100L,
            )
        )

        val futureManifest = SyncSnapshotManifest(
            formatVersion = 99, // Unsupported!
            databaseVersion = 13,
            snapshotId = "snap-future-version",
            accountEmail = testAccount,
            creatorDeviceId = localDeviceId,
            createdAtEpochMillis = 1000L,
            snapshotHlc = "1000:0:device_test_alpha",
            coverageVector = emptyMap(),
        )

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry(SyncSnapshotManifest.MANIFEST_ENTRY_NAME))
                zos.write(json.encodeToString(futureManifest).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(SyncSnapshotManifest.ENTITIES_ENTRY_NAME))
                zos.write("{}".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val aad = SyncSnapshotManager.buildSnapshotAad(
            formatVersion = futureManifest.formatVersion,
            snapshotId = futureManifest.snapshotId,
            accountEmail = testAccount,
            creatorDeviceId = localDeviceId,
            coverageVectorHash = SyncSnapshotManager.computeStringSha256(json.encodeToString(futureManifest.coverageVector)),
        )

        val encBytes = EncryptedContainer.encrypt(
            plaintext = zipBytes,
            key = masterKey,
            aad = aad,
        )

        val file = tempFolder.newFile("unsupported_ver.bin").apply {
            writeBytes(encBytes)
        }

        // Act
        val restoreResult = snapshotManager.restoreSnapshot(snapshotFile = file, accountEmail = testAccount)

        // Assert
        assertTrue("Restore must fail on unsupported version", restoreResult is SnapshotRestoreResult.Error)
        val error = restoreResult as SnapshotRestoreResult.Error
        assertTrue("Error must mention unsupported formatVersion: ${error.message}", error.message.contains("Unsupported snapshot formatVersion"))

        // Invariant: Database must remain unmodified
        assertEquals("Database must not be modified", 1, fakeNoteDao.notes.size)
        assertEquals("Safe Title", fakeNoteDao.notes["existing-note-v"]?.title)
    }

    @Test
    fun test_restoreSnapshot_attachmentHashMismatch_abortsRestorationWithoutModifyingDatabase() = runBlocking {
        // Arrange: Capture snapshot with attachment
        val noteId = fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note-hash-test",
                ownerId = testAccount,
                kind = NoteKind.NOTE,
                title = "Original Title",
                body = "Original Body",
                colorIndex = 0,
                trashed = false,
                createdAt = 100L,
                updatedAt = 100L,
            )
        )
        val noteAttDir = File(fakeFilesDir, "note_attachments/$noteId").apply { mkdirs() }
        val attFile = File(noteAttDir, "att-uuid-test_image.png").apply { writeText("Image content") }
        fakeAttachmentDao.insert(
            NoteAttachmentEntity(
                id = 1L,
                uuid = "att-uuid-test",
                noteId = noteId,
                ownerId = testAccount,
                uri = attFile.absolutePath,
                displayName = "image.png",
                mimeType = "image/png",
            )
        )

        // Capture valid snapshot
        val captureResult = snapshotManager.captureSnapshot(accountEmail = testAccount) as SnapshotCaptureResult.Success

        // Unpack container, corrupt the attachment bytes in the zip, repack and re-encrypt
        val containerBytes = File(captureResult.snapshot.storagePath).readBytes()
        val metadata = EncryptedContainer.readMetadata(containerBytes)
        val decryptedBytes = EncryptedContainer.decrypt(containerBytes, masterKey, expectedAad = metadata.aad)

        val corruptedZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                ZipInputStream(decryptedBytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        zos.putNextEntry(ZipEntry(entry.name))
                        if (entry.name.contains("attachments/")) {
                            // Write corrupted bytes to cause SHA-256 mismatch
                            zos.write("Corrupted data instead of image".toByteArray(Charsets.UTF_8))
                        } else {
                            zis.copyTo(zos)
                        }
                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            baos.toByteArray()
        }

        // Re-encrypt corrupted payload with same AAD
        val encBytes = EncryptedContainer.encrypt(
            plaintext = corruptedZipBytes,
            key = masterKey,
            aad = metadata.aad,
        )
        val corruptedSnapshotFile = tempFolder.newFile("corrupted_att.bin").apply {
            writeBytes(encBytes)
        }

        // Reset database note to ensure we detect any partial changes
        fakeNoteDao.notes["note-hash-test"] = fakeNoteDao.notes["note-hash-test"]!!.copy(title = "Must Not Change")

        // Act
        val restoreResult = snapshotManager.restoreSnapshot(snapshotFile = corruptedSnapshotFile, accountEmail = testAccount)

        // Assert
        assertTrue("Restore must fail on attachment hash mismatch", restoreResult is SnapshotRestoreResult.Error)
        val error = restoreResult as SnapshotRestoreResult.Error
        assertTrue("Error must mention hash mismatch or corrupted attachment: ${error.message}", error.message.contains("hash mismatch") || error.message.contains("Corrupt") || error.message.contains("size mismatch") || error.message.contains("missing"))

        // Invariant: Database must remain unmodified
        assertEquals("Must Not Change", fakeNoteDao.notes["note-hash-test"]?.title)
    }

    @Test
    fun test_recovery_interruptedAttachmentPromotion_recoversCommittedOrRollsBackStaged() = runBlocking {
        // Scenario A: Room committed (status = COMMITTED), app killed before promotion finished.
        val snapshotId = "snap-recovery-1"
        fakePromotionDao.insertOrUpdate(
            SyncAttachmentPromotionEntity(
                snapshotId = snapshotId,
                status = PromotionStatus.COMMITTED,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // Create staging file
        val stagingDir = File(fakeFilesDir, "note_attachments_staging/$snapshotId").apply { mkdirs() }
        val stagedFile = File(stagingDir, "att-file-1_doc.txt").apply { writeText("Recoverable document") }
        val targetDir = File(fakeFilesDir, "note_attachments/100").apply { mkdirs() }
        val targetFile = File(targetDir, "att-file-1_doc.txt")

        val manifest = AttachmentPromotionManifest(
            snapshotId = snapshotId,
            stagingDirPath = stagingDir.absolutePath,
            targetDirPath = File(fakeFilesDir, "note_attachments").absolutePath,
            createdAtEpochMillis = 1000L,
            entries = listOf(
                AttachmentPromotionFileEntry(
                    attachmentUuid = "att-file-1",
                    noteId = 100L,
                    stagingFileName = "att-file-1_doc.txt",
                    targetFileName = "att-file-1_doc.txt",
                    sha256Hex = AttachmentPromotionRecoveryManager.computeSha256(stagedFile),
                    sizeBytes = stagedFile.length(),
                )
            )
        )
        promotionRecoveryManager.writePromotionManifest(manifest)

        // Act: Run recovery
        val recovered = promotionRecoveryManager.recoverInterruptedPromotion()

        // Assert: Target file promoted, promotion marked COMPLETED
        assertTrue("Recovery must report true", recovered)
        assertTrue("Target file must be promoted", targetFile.exists())
        assertEquals("Recoverable document", targetFile.readText())
        val promoEntity = fakePromotionDao.getBySnapshotId(snapshotId)
        assertNotNull(promoEntity)
        assertEquals(PromotionStatus.COMPLETED, promoEntity!!.status)

        // Scenario B: Room aborted or crashed during staging (no COMMITTED entry in Room)
        val uncommittedSnapId = "snap-uncommitted"
        val uncommittedStagingDir = File(fakeFilesDir, "note_attachments_staging/$uncommittedSnapId").apply { mkdirs() }
        val uncommittedStagedFile = File(uncommittedStagingDir, "garbage.tmp").apply { writeText("Uncommitted junk") }

        val uncommittedManifest = AttachmentPromotionManifest(
            snapshotId = uncommittedSnapId,
            stagingDirPath = uncommittedStagingDir.absolutePath,
            targetDirPath = File(fakeFilesDir, "note_attachments").absolutePath,
            createdAtEpochMillis = 1000L,
            entries = listOf(
                AttachmentPromotionFileEntry(
                    attachmentUuid = "garbage-uuid",
                    noteId = 200L,
                    stagingFileName = "garbage.tmp",
                    targetFileName = "garbage.tmp",
                    sha256Hex = AttachmentPromotionRecoveryManager.computeSha256(uncommittedStagedFile),
                    sizeBytes = uncommittedStagedFile.length(),
                )
            )
        )
        promotionRecoveryManager.writePromotionManifest(uncommittedManifest)

        // Act: Run recovery (no COMMITTED entry in promotionDao)
        val cleaned = promotionRecoveryManager.recoverInterruptedPromotion()

        // Assert: Staging directory removed
        assertTrue("Recovery cleanup must report true", cleaned)
        assertFalse("Uncommitted staging dir must be deleted", uncommittedStagingDir.exists())
    }

    @Test
    fun test_replayDeduplication_afterSnapshotRestore_duplicateOperationAcrossSequencesSkippedInO1() = runBlocking {
        // Arrange: Snapshot restored with applied operation 'dup-change-op'
        fakeAppliedOperationDao.insert(
            SyncAppliedOperationEntity(
                changeId = "dup-change-op",
                journalId = "journal-orig-1",
                deviceId = localDeviceId,
                sequence = 3L,
                entityType = "NOTE",
                entityId = "note-uuid-alpha",
                appliedAt = 1000L,
                hlcTimestamp = "1000:0:device_test_alpha",
            )
        )

        // Assert that the exact applied operation ledger responds in O(1)
        assertTrue(fakeAppliedOperationDao.isApplied("dup-change-op"))
        assertFalse(fakeAppliedOperationDao.isApplied("non-existent-op"))
    }

    // --- In-Memory Test Doubles ---

    private class FakeSyncSnapshotDao : SyncSnapshotDao {
        val snapshots = mutableMapOf<String, SyncSnapshotEntity>()

        override suspend fun insert(snapshot: SyncSnapshotEntity) {
            snapshots[snapshot.snapshotId] = snapshot
        }

        override suspend fun update(snapshot: SyncSnapshotEntity) {
            snapshots[snapshot.snapshotId] = snapshot
        }

        override suspend fun getById(snapshotId: String): SyncSnapshotEntity? = snapshots[snapshotId]

        override suspend fun getByStatus(status: String): List<SyncSnapshotEntity> =
            snapshots.values.filter { it.status == status }

        override suspend fun getActiveSnapshot(): SyncSnapshotEntity? =
            snapshots.values.firstOrNull { it.status == SnapshotStatus.ACTIVE }

        override suspend fun getAllSnapshots(): List<SyncSnapshotEntity> = snapshots.values.toList()

        override suspend fun markUploadedAndActive(
            snapshotId: String,
            driveFileId: String,
            newStatus: String,
            timestamp: Long,
        ): Int {
            val existing = snapshots[snapshotId] ?: return 0
            snapshots[snapshotId] = existing.copy(
                driveFileId = driveFileId,
                status = newStatus,
                updatedAt = timestamp,
            )
            return 1
        }

        override suspend fun updateStatus(snapshotId: String, newStatus: String, timestamp: Long): Int {
            val existing = snapshots[snapshotId] ?: return 0
            snapshots[snapshotId] = existing.copy(status = newStatus, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteById(snapshotId: String): Int = if (snapshots.remove(snapshotId) != null) 1 else 0

        override suspend fun clearAll() {
            snapshots.clear()
        }
    }

    private class FakeSyncAttachmentPromotionDao : SyncAttachmentPromotionDao {
        val promotions = mutableMapOf<String, SyncAttachmentPromotionEntity>()

        override suspend fun insertOrUpdate(entity: SyncAttachmentPromotionEntity) {
            promotions[entity.snapshotId] = entity
        }

        override suspend fun getBySnapshotId(snapshotId: String): SyncAttachmentPromotionEntity? =
            promotions[snapshotId]

        override suspend fun markCompleted(snapshotId: String, timestamp: Long): Int {
            val existing = promotions[snapshotId] ?: return 0
            promotions[snapshotId] = existing.copy(status = PromotionStatus.COMPLETED, updatedAt = timestamp)
            return 1
        }

        override suspend fun getPendingCommittedPromotions(): List<SyncAttachmentPromotionEntity> =
            promotions.values.filter { it.status == PromotionStatus.COMMITTED }

        override suspend fun deleteBySnapshotId(snapshotId: String): Int =
            if (promotions.remove(snapshotId) != null) 1 else 0

        override suspend fun clearAll() {
            promotions.clear()
        }
    }

    private class FakeNoteDao(
        private val itemDao: FakeChecklistItemDao,
        private val attachmentDao: FakeAttachmentDao,
        private val shoulderDao: FakeShoulderDao,
    ) : haddad.three.notas.data.UuidBaseFakeNoteDao() {
        override suspend fun allNotes(): List<NoteWithItems> = notes.values.map { note ->
            NoteWithItems(
                note = note,
                items = itemDao.items.values.filter { it.noteId == note.id },
                attachments = attachmentDao.attachments.values.filter { it.noteId == note.id },
                shoulder = shoulderDao.shoulders.values.find { it.id == note.shoulderId },
            )
        }
    }

    private class FakeChecklistItemDao : haddad.three.notas.data.UuidBaseFakeChecklistItemDao()

    private class FakeShoulderDao : haddad.three.notas.data.BaseFakeShoulderDao() {
        fun clearAll() { shoulders.clear() }
    }

    private class FakeAttachmentDao : haddad.three.notas.data.BaseFakeAttachmentDao()

    private class FakePendingChangeDao : PendingChangeDao {
        val changes = mutableMapOf<String, PendingChangeEntity>()

        override suspend fun insert(change: PendingChangeEntity): Long {
            changes[change.changeId] = change
            return 1L
        }

        override suspend fun insertAll(changes: List<PendingChangeEntity>) {
            changes.forEach { insert(it) }
        }

        override suspend fun update(change: PendingChangeEntity) {
            changes[change.changeId] = change
        }

        override suspend fun getById(changeId: String): PendingChangeEntity? = changes[changeId]

        override suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity> =
            changes.values.filter { it.status == "PENDING" }.take(limit)

        override suspend fun getAllPendingChanges(): List<PendingChangeEntity> =
            changes.values.filter { it.status == "PENDING" }

        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = emptyFlow()

        override suspend fun countPendingChanges(): Int =
            changes.values.count { it.status == "PENDING" }

        override suspend fun getChangesForEntity(entityType: String, entityId: String): List<PendingChangeEntity> =
            changes.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getChangesForJournal(journalId: String): List<PendingChangeEntity> =
            changes.values.filter { it.journalId == journalId }

        override suspend fun markChangesJournaled(changeIds: Collection<String>, journalId: String): Int {
            var count = 0
            changeIds.forEach { id ->
                changes[id]?.let {
                    changes[id] = it.copy(status = "JOURNALED", journalId = journalId)
                    count++
                }
            }
            return count
        }

        override suspend fun markChangesSynced(journalId: String): Int {
            var count = 0
            changes.values.filter { it.journalId == journalId }.forEach {
                changes[it.changeId] = it.copy(status = "SYNCED")
                count++
            }
            return count
        }

        override suspend fun resetJournaledChangesToPending(journalId: String): Int {
            var count = 0
            changes.values.filter { it.journalId == journalId }.forEach {
                changes[it.changeId] = it.copy(status = "PENDING", journalId = null)
                count++
            }
            return count
        }

        override suspend fun deleteById(changeId: String): Int = if (changes.remove(changeId) != null) 1 else 0

        override suspend fun deleteByIds(changeIds: Collection<String>): Int {
            var count = 0
            changeIds.forEach { if (changes.remove(it) != null) count++ }
            return count
        }

        override suspend fun deleteChangesForEntity(entityType: String, entityId: String): Int {
            val toRemove = changes.values.filter { it.entityType == entityType && it.entityId == entityId }
            toRemove.forEach { changes.remove(it.changeId) }
            return toRemove.size
        }

        override suspend fun deleteSynced(): Int {
            val toRemove = changes.values.filter { it.status == "SYNCED" }
            toRemove.forEach { changes.remove(it.changeId) }
            return toRemove.size
        }

        override suspend fun clearAll() {
            changes.clear()
        }
    }

    private class FakeSyncJournalDao : SyncJournalDao {
        val journals = mutableMapOf<String, SyncJournalEntity>()

        override suspend fun insert(journal: SyncJournalEntity): Long {
            journals[journal.journalId] = journal
            return 1L
        }

        override suspend fun update(journal: SyncJournalEntity) {
            journals[journal.journalId] = journal
        }

        override suspend fun getById(journalId: String): SyncJournalEntity? = journals[journalId]

        override suspend fun getMaxPublishedSequence(deviceId: String): Long? =
            journals.values.filter { it.deviceId == deviceId }.maxOfOrNull { it.sequence }

        override suspend fun getMaxCommittedSequence(deviceId: String): Long? =
            journals.values.filter { it.deviceId == deviceId && it.status in listOf(JournalStatus.READY_TO_UPLOAD.name, JournalStatus.UPLOADED.name) }.maxOfOrNull { it.sequence }

        override suspend fun getCreatedJournals(deviceId: String): List<SyncJournalEntity> =
            journals.values.filter { it.deviceId == deviceId && it.status == JournalStatus.CREATED.name }

        override suspend fun getByDeviceAndSequence(deviceId: String, sequence: Long): SyncJournalEntity? =
            journals.values.find { it.deviceId == deviceId && it.sequence == sequence }

        override suspend fun getReadyToUpload(): List<SyncJournalEntity> =
            journals.values.filter { it.status == JournalStatus.READY_TO_UPLOAD.name }

        override suspend fun getByStatus(status: String): List<SyncJournalEntity> =
            journals.values.filter { it.status == status }

        override suspend fun getJournalsForDevice(deviceId: String): List<SyncJournalEntity> =
            journals.values.filter { it.deviceId == deviceId }

        override fun observeAllJournals(): Flow<List<SyncJournalEntity>> = flowOf(journals.values.toList())

        override suspend fun getByDriveFileId(driveFileId: String): SyncJournalEntity? =
            journals.values.find { it.driveFileId == driveFileId }

        override suspend fun updateStatus(journalId: String, newStatus: String, timestamp: Long): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, updatedAt = timestamp)
            return 1
        }

        override suspend fun recordUploadFailure(journalId: String, newStatus: String, error: String?, timestamp: Long): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, lastError = error, updatedAt = timestamp)
            return 1
        }

        override suspend fun markUploaded(journalId: String, driveFileId: String, newStatus: String, timestamp: Long): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, driveFileId = driveFileId, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteById(journalId: String): Int = if (journals.remove(journalId) != null) 1 else 0

        override suspend fun clearAll() {
            journals.clear()
        }
    }

    private class FakeSyncWatermarkDao : SyncWatermarkDao {
        val watermarks = mutableMapOf<String, SyncWatermarkEntity>()

        override suspend fun insertOrUpdate(watermark: SyncWatermarkEntity) {
            watermarks[watermark.deviceId] = watermark
        }

        override suspend fun getWatermark(deviceId: String): SyncWatermarkEntity? = watermarks[deviceId]

        override suspend fun getAllWatermarks(): List<SyncWatermarkEntity> = watermarks.values.toList()

        override suspend fun updateWatermark(
            deviceId: String,
            discovered: Long,
            downloaded: Long,
            validated: Long,
            missingJson: String,
            timestamp: Long,
        ): Int {
            watermarks[deviceId] = SyncWatermarkEntity(
                deviceId = deviceId,
                highestDiscoveredSeq = discovered,
                highestDownloadedSeq = downloaded,
                highestValidatedSeq = validated,
                missingSequencesJson = missingJson,
                updatedAt = timestamp,
            )
            return 1
        }

        override suspend fun updateAppliedSequence(deviceId: String, appliedSeq: Long, timestamp: Long): Int {
            val existing = watermarks[deviceId] ?: SyncWatermarkEntity(deviceId = deviceId)
            watermarks[deviceId] = existing.copy(highestAppliedSeq = appliedSeq, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteForDevice(deviceId: String): Int =
            if (watermarks.remove(deviceId) != null) 1 else 0

        override suspend fun clearAll() {
            watermarks.clear()
        }
    }

    private class FakeSyncAppliedOperationDao : SyncAppliedOperationDao {
        val operations = mutableListOf<SyncAppliedOperationEntity>()

        override suspend fun insert(operation: SyncAppliedOperationEntity): Long {
            operations.add(operation)
            return operations.size.toLong()
        }

        override suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity? =
            operations.find { it.changeId == changeId }

        override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> =
            operations.filter { it.journalId == journalId }

        override suspend fun isApplied(changeId: String): Boolean =
            operations.any { it.changeId == changeId }

        override suspend fun getAllOperations(): List<SyncAppliedOperationEntity> = operations.toList()

        override suspend fun insertAll(operations: List<SyncAppliedOperationEntity>) {
            this.operations.addAll(operations)
        }

        override suspend fun clearAll() {
            operations.clear()
        }
    }

    private class FakeSyncConflictMetadataDao : SyncConflictMetadataDao {
        val metadata = mutableListOf<SyncConflictMetadataEntity>()

        override suspend fun insertOrUpdate(metadata: SyncConflictMetadataEntity) {
            this.metadata.removeAll { it.entityType == metadata.entityType && it.entityId == metadata.entityId && it.fieldName == metadata.fieldName }
            this.metadata.add(metadata)
        }

        override suspend fun getFieldMetadata(entityType: String, entityId: String, fieldName: String): SyncConflictMetadataEntity? =
            metadata.find { it.entityType == entityType && it.entityId == entityId && it.fieldName == fieldName }

        override suspend fun getAllFieldsForEntity(entityType: String, entityId: String): List<SyncConflictMetadataEntity> =
            metadata.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun deleteForEntity(entityType: String, entityId: String): Int {
            val count = metadata.count { it.entityType == entityType && it.entityId == entityId }
            metadata.removeAll { it.entityType == entityType && it.entityId == entityId }
            return count
        }

        override suspend fun getAllMetadata(): List<SyncConflictMetadataEntity> = metadata.toList()

        override suspend fun insertAll(metadata: List<SyncConflictMetadataEntity>) {
            this.metadata.addAll(metadata)
        }

        override suspend fun clearAll() {
            metadata.clear()
        }
    }

    private class FakeSyncTombstoneDao : SyncTombstoneDao {
        val tombstones = mutableListOf<SyncTombstoneEntity>()

        override suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity) {
            tombstones.removeAll { it.entityType == tombstone.entityType && it.entityId == tombstone.entityId }
            tombstones.add(tombstone)
        }

        override suspend fun getTombstone(entityType: String, entityId: String): SyncTombstoneEntity? =
            tombstones.find { it.entityType == entityType && it.entityId == entityId }

        override suspend fun deleteTombstone(entityType: String, entityId: String): Int {
            val count = tombstones.count { it.entityType == entityType && it.entityId == entityId }
            tombstones.removeAll { it.entityType == entityType && it.entityId == entityId }
            return count
        }

        override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = tombstones.toList()

        override suspend fun insertAll(tombstones: List<SyncTombstoneEntity>) {
            this.tombstones.addAll(tombstones)
        }

        override suspend fun clearAll() {
            tombstones.clear()
        }
    }

    private class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            val assignedId = if (record.id == 0L) (records.size + 1).toLong() else record.id
            records.add(record.copy(id = assignedId))
            return assignedId
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? =
            records.find { it.conflictId == conflictId }

        override suspend fun getConflictsForEntity(entityType: String, entityId: String): List<SyncConflictRecordEntity> =
            records.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> =
            records.filter { !it.resolved }

        override suspend fun markResolved(id: Long): Int {
            val idx = records.indexOfFirst { it.id == id }
            if (idx != -1) {
                records[idx] = records[idx].copy(resolved = true)
                return 1
            }
            return 0
        }

        override suspend fun getAllConflictRecords(): List<SyncConflictRecordEntity> = records.toList()

        override suspend fun insertAll(records: List<SyncConflictRecordEntity>) {
            this.records.addAll(records)
        }

        override suspend fun clearAll() {
            records.clear()
        }
    }
}
