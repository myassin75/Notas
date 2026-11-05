package haddad.three.notas.data.sync.ingestion

import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.sync.change.AttachmentChangePayload
import haddad.three.notas.data.sync.change.CategoryChangePayload
import haddad.three.notas.data.sync.change.ChecklistItemChangePayload
import haddad.three.notas.data.sync.change.DefaultLocalChangeRecorder
import haddad.three.notas.data.sync.change.DeleteChangePayload
import haddad.three.notas.data.sync.change.EntityType
import haddad.three.notas.data.sync.change.NoteChangePayload
import haddad.three.notas.data.sync.change.OperationType
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.change.PreferenceChangePayload
import haddad.three.notas.data.sync.conflict.FieldConflictResolver
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationEntity
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataEntity
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.data.sync.conflict.SyncTombstoneDao
import haddad.three.notas.data.sync.conflict.SyncTombstoneEntity
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.hlc.InMemoryHlcStorage
import haddad.three.notas.data.sync.journal.JournalOperationDto
import haddad.three.notas.data.sync.journal.SyncJournalDto
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import haddad.three.notas.data.sync.transport.InboxStatus
import haddad.three.notas.data.sync.transport.SyncInboxDao
import haddad.three.notas.data.sync.transport.SyncInboxEntity
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import haddad.three.notas.data.sync.transport.SyncWatermarkEntity
import kotlinx.coroutines.flow.Flow
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
import java.io.File
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class RemoteJournalIngestionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private lateinit var masterKey: SecretKey
    private lateinit var mekManager: MekManager
    private lateinit var hlcClock: HlcClock

    private lateinit var noteDao: FakeNoteDao
    private lateinit var itemDao: FakeChecklistItemDao
    private lateinit var shoulderDao: FakeShoulderDao
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var syncInboxDao: FakeSyncInboxDao
    private lateinit var syncWatermarkDao: FakeSyncWatermarkDao
    private lateinit var appliedOperationDao: FakeSyncAppliedOperationDao
    private lateinit var conflictMetadataDao: FakeSyncConflictMetadataDao
    private lateinit var tombstoneDao: FakeSyncTombstoneDao
    private lateinit var conflictRecordDao: FakeSyncConflictRecordDao
    private lateinit var pendingChangeDao: FakePendingChangeDao

    private lateinit var fieldConflictResolver: FieldConflictResolver
    private lateinit var ingestionEngine: RemoteJournalIngestionEngine
    private lateinit var changeRecorder: DefaultLocalChangeRecorder

    private val localDeviceId = "local-device-001"
    private val remoteDeviceId = "remote-device-002"

    @Before
    fun setUp() = runBlocking {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        masterKey = keyGen.generateKey()

        val keyStore = SoftwareKeyStoreManager()
        val storage = InMemoryCryptoStorage()
        val wrapped = keyStore.wrapKey(masterKey, 1)
        storage.setLocalWrappedMek(wrapped, 1)
        mekManager = MekManager(keyStore, storage)

        hlcClock = HlcClock(nodeId = localDeviceId, storage = InMemoryHlcStorage())
        hlcClock.initialize()

        noteDao = FakeNoteDao()
        itemDao = FakeChecklistItemDao()
        shoulderDao = FakeShoulderDao()
        attachmentDao = FakeAttachmentDao()
        syncInboxDao = FakeSyncInboxDao()
        syncWatermarkDao = FakeSyncWatermarkDao()
        appliedOperationDao = FakeSyncAppliedOperationDao()
        conflictMetadataDao = FakeSyncConflictMetadataDao()
        tombstoneDao = FakeSyncTombstoneDao()
        conflictRecordDao = FakeSyncConflictRecordDao()
        pendingChangeDao = FakePendingChangeDao()

        fieldConflictResolver = FieldConflictResolver(
            noteDao = noteDao,
            checklistItemDao = itemDao,
            shoulderDao = shoulderDao,
            attachmentDao = attachmentDao,
            conflictMetadataDao = conflictMetadataDao,
            tombstoneDao = tombstoneDao,
            conflictRecordDao = conflictRecordDao,
        )

        ingestionEngine = RemoteJournalIngestionEngine(
            database = null, // unit tests run without full SQLite binding
            syncInboxDao = syncInboxDao,
            syncWatermarkDao = syncWatermarkDao,
            appliedOperationDao = appliedOperationDao,
            fieldConflictResolver = fieldConflictResolver,
            hlcClock = hlcClock,
            mekManager = mekManager,
            overrideKey = masterKey,
        )

        changeRecorder = DefaultLocalChangeRecorder(
            pendingChangeDao = pendingChangeDao,
            deviceIdProvider = { localDeviceId },
        )
    }

    private fun createEncryptedJournal(
        journalId: String,
        deviceId: String,
        sequence: Long,
        operations: List<JournalOperationDto>,
        formatVersion: Int = 1,
    ): File {
        val dto = SyncJournalDto(
            formatVersion = formatVersion,
            journalId = journalId,
            deviceId = deviceId,
            sequence = sequence,
            createdAt = System.currentTimeMillis(),
            operationCount = operations.size,
            operations = operations,
        )
        val jsonStr = json.encodeToString(dto)
        val aad = SyncJournalWriter.buildAad(formatVersion, journalId, deviceId, sequence)
        val encryptedBytes = EncryptedContainer.encrypt(
            plaintext = jsonStr.toByteArray(Charsets.UTF_8),
            key = masterKey,
            aad = aad,
        )
        val file = File(tempFolder.root, "journal_${journalId}.bin")
        file.writeBytes(encryptedBytes)
        return file
    }

    @Test
    fun testSuccessfulRemoteJournalApplication(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val notePayload = NoteChangePayload(
            uuid = noteUuid,
            kind = "NOTE",
            title = "Remote Note",
            body = "Remote Body Content",
            colorIndex = 3,
            trashed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val op = JournalOperationDto(
            changeId = "change-001",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(notePayload),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val jFile = createEncryptedJournal("j-1", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(
            SyncInboxEntity(
                journalId = "j-1",
                deviceId = remoteDeviceId,
                sequence = 1L,
                formatVersion = 1,
                driveFileId = "drive-1",
                localFilePath = jFile.absolutePath,
                contentSha256 = "dummy-sha",
                operationCount = 1,
                downloadedAt = System.currentTimeMillis(),
                status = InboxStatus.VALIDATED,
            ),
        )

        val summary = ingestionEngine.ingestAll()

        assertEquals(1, summary.processedJournalsCount)
        assertEquals(1, summary.appliedOperationsCount)
        assertEquals(0, summary.failedJournalsCount)

        val appliedInbox = syncInboxDao.getByJournalId("j-1")
        assertNotNull(appliedInbox)
        assertEquals(InboxStatus.APPLIED, appliedInbox?.status)

        val watermark = syncWatermarkDao.getWatermark(remoteDeviceId)
        assertNotNull(watermark)
        assertEquals(1L, watermark?.highestAppliedSeq)

        val insertedNote = noteDao.getNoteEntityByUuid(noteUuid)
        assertNotNull(insertedNote)
        assertEquals("Remote Note", insertedNote?.title)
        assertEquals("Remote Body Content", insertedNote?.body)
        assertEquals(3, insertedNote?.colorIndex)
    }

    @Test
    fun testMonotonicSequenceOrderEnforcement(): Unit = runBlocking {
        val noteUuid1 = UUID.randomUUID().toString()
        val noteUuid2 = UUID.randomUUID().toString()

        val op1 = JournalOperationDto(
            changeId = "change-1",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid1,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid1, kind = "NOTE", title = "Note 1", body = "Body 1", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )
        val op2 = JournalOperationDto(
            changeId = "change-2",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid2,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid2, kind = "NOTE", title = "Note 2", body = "Body 2", colorIndex = 1, trashed = false, createdAt = 2000L, updatedAt = 2000L)),
            hlcTimestamp = Hlc(2000L, 0, remoteDeviceId).toString(),
        )

        val file1 = createEncryptedJournal("j-seq-1", remoteDeviceId, 1L, listOf(op1))
        val file2 = createEncryptedJournal("j-seq-2", remoteDeviceId, 2L, listOf(op2))

        syncInboxDao.insert(SyncInboxEntity("j-seq-1", remoteDeviceId, 1L, 1, "d-1", file1.absolutePath, "sha1", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        syncInboxDao.insert(SyncInboxEntity("j-seq-2", remoteDeviceId, 2L, 1, "d-2", file2.absolutePath, "sha2", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        val summary = ingestionEngine.ingestAll()

        assertEquals(2, summary.processedJournalsCount)
        assertEquals(2, summary.appliedOperationsCount)
        assertEquals(2L, syncWatermarkDao.getWatermark(remoteDeviceId)?.highestAppliedSeq)
        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid1))
        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid2))
    }

    @Test
    fun testMissingSequenceRejectionAndWatermarkFreeze(): Unit = runBlocking {
        val noteUuid3 = UUID.randomUUID().toString()
        val op3 = JournalOperationDto(
            changeId = "change-3",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid3,
            operationType = OperationType.UPSERT.name,
            createdAt = 3000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid3, kind = "NOTE", title = "Note 3", body = "Body 3", colorIndex = 0, trashed = false, createdAt = 3000L, updatedAt = 3000L)),
            hlcTimestamp = Hlc(3000L, 0, remoteDeviceId).toString(),
        )

        // Only insert sequence 3 when highestAppliedSeq is 0 (sequence 1 and 2 are missing)
        val file3 = createEncryptedJournal("j-seq-3", remoteDeviceId, 3L, listOf(op3))
        syncInboxDao.insert(SyncInboxEntity("j-seq-3", remoteDeviceId, 3L, 1, "d-3", file3.absolutePath, "sha3", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        val summary = ingestionEngine.ingestAll()

        assertEquals(0, summary.processedJournalsCount)
        assertEquals(1, summary.errors.size)
        assertTrue(summary.errors[0].errorMessage.contains("Sequence gap detected"))

        // Watermark should remain 0 (frozen)
        val watermark = syncWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(0L, watermark?.highestAppliedSeq ?: 0L)

        // Note 3 must NOT have been inserted
        assertNull(noteDao.getNoteEntityByUuid(noteUuid3))
    }

    @Test
    fun testSequenceGapRepair(): Unit = runBlocking {
        val noteUuid1 = UUID.randomUUID().toString()
        val noteUuid2 = UUID.randomUUID().toString()

        val op1 = JournalOperationDto(
            changeId = "change-1",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid1,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid1, kind = "NOTE", title = "Note 1", body = "Body 1", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )
        val op2 = JournalOperationDto(
            changeId = "change-2",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid2,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid2, kind = "NOTE", title = "Note 2", body = "Body 2", colorIndex = 1, trashed = false, createdAt = 2000L, updatedAt = 2000L)),
            hlcTimestamp = Hlc(2000L, 0, remoteDeviceId).toString(),
        )

        val file2 = createEncryptedJournal("j-seq-2", remoteDeviceId, 2L, listOf(op2))
        syncInboxDao.insert(SyncInboxEntity("j-seq-2", remoteDeviceId, 2L, 1, "d-2", file2.absolutePath, "sha2", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        // First attempt fails due to missing sequence 1
        val summary1 = ingestionEngine.ingestAll()
        assertEquals(0, summary1.processedJournalsCount)

        // Sequence 1 arrives
        val file1 = createEncryptedJournal("j-seq-1", remoteDeviceId, 1L, listOf(op1))
        syncInboxDao.insert(SyncInboxEntity("j-seq-1", remoteDeviceId, 1L, 1, "d-1", file1.absolutePath, "sha1", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        // Second attempt ingests both sequence 1 and 2 in order
        val summary2 = ingestionEngine.ingestAll()
        assertEquals(2, summary2.processedJournalsCount)
        assertEquals(2L, syncWatermarkDao.getWatermark(remoteDeviceId)?.highestAppliedSeq)
        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid1))
        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid2))
    }

    @Test
    fun testDuplicateJournalReplayIdempotency(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val op = JournalOperationDto(
            changeId = "change-single",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Original", body = "Original Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-rep", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-rep", remoteDeviceId, 1L, 1, "d-rep", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        // First ingestion
        val summary1 = ingestionEngine.ingestAll()
        assertEquals(1, summary1.processedJournalsCount)
        assertEquals(1, summary1.appliedOperationsCount)

        // Second ingestion: replay
        val summary2 = ingestionEngine.ingestAll()
        assertEquals(0, summary2.processedJournalsCount)
        assertEquals(0, summary2.appliedOperationsCount)
        assertEquals(1L, syncWatermarkDao.getWatermark(remoteDeviceId)?.highestAppliedSeq)
    }

    @Test
    fun testDuplicateOperationReplayIdempotency(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val op = JournalOperationDto(
            changeId = "op-dup",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Dup Op", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        // Pre-record operation as already applied
        appliedOperationDao.insert(
            SyncAppliedOperationEntity(
                changeId = "op-dup",
                journalId = "j-prior",
                deviceId = remoteDeviceId,
                sequence = 1L,
                entityType = EntityType.NOTE.name,
                entityId = noteUuid,
                appliedAt = 1000L,
                hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
            ),
        )

        val file = createEncryptedJournal("j-op-dup", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-op-dup", remoteDeviceId, 1L, 1, "d-dup", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        val summary = ingestionEngine.ingestAll()
        assertEquals(1, summary.processedJournalsCount)
        assertEquals(0, summary.appliedOperationsCount)
        assertEquals(1, summary.skippedOperationsCount)
        assertNull("Pre-applied operation should not be executed again", noteDao.getNoteEntityByUuid(noteUuid))
    }

    @Test
    fun testIndependentFieldUpdatesMergeCorrectly(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        // Create baseline note
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Common Title",
                body = "Common Body",
                colorIndex = 1,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                pinnedAt = null,
            ),
        )
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "colorIndex", Hlc(1000L, 0, "dev0").toString(), 1000L))
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "pinnedAt", Hlc(1000L, 0, "dev0").toString(), 1000L))

        // Remote journal from Device A updates colorIndex to 4
        val opA = JournalOperationDto(
            changeId = "change-color",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Common Title", body = "Common Body", colorIndex = 4, trashed = false, createdAt = 1000L, updatedAt = 2000L)),
            hlcTimestamp = Hlc(2000L, 0, remoteDeviceId).toString(),
        )

        // Remote journal from Device B updates pinnedAt to 2500L
        val devB = "remote-device-003"
        val opB = JournalOperationDto(
            changeId = "change-pin",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2500L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Common Title", body = "Common Body", colorIndex = 4, trashed = false, createdAt = 1000L, updatedAt = 2500L, pinnedAt = 2500L)),
            hlcTimestamp = Hlc(2500L, 0, devB).toString(),
        )

        val fileA = createEncryptedJournal("j-devA", remoteDeviceId, 1L, listOf(opA))
        val fileB = createEncryptedJournal("j-devB", devB, 1L, listOf(opB))

        syncInboxDao.insert(SyncInboxEntity("j-devA", remoteDeviceId, 1L, 1, "dA", fileA.absolutePath, "shaA", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        syncInboxDao.insert(SyncInboxEntity("j-devB", devB, 1L, 1, "dB", fileB.absolutePath, "shaB", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        val mergedNote = noteDao.getNoteEntityByUuid(noteUuid)
        assertNotNull(mergedNote)
        assertEquals("Independent colorIndex should be updated to 4", 4, mergedNote?.colorIndex)
        assertEquals("Independent pinnedAt should be updated to 2500L", 2500L, mergedNote?.pinnedAt)
        assertEquals("Title should remain intact", "Common Title", mergedNote?.title)
        assertEquals("Body should remain intact", "Common Body", mergedNote?.body)
    }

    @Test
    fun testSameFieldConcurrentUpdateHlcLww(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Title",
                body = "Body",
                colorIndex = 1,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        // Device A sets colorIndex = 2 at HLC(2000, 0, devA)
        val opA = JournalOperationDto(
            changeId = "c-1",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "Body", colorIndex = 2, trashed = false, createdAt = 1000L, updatedAt = 2000L)),
            hlcTimestamp = Hlc(2000L, 0, "devA").toString(),
        )

        // Device B sets colorIndex = 5 at HLC(3000, 0, devB)
        val opB = JournalOperationDto(
            changeId = "c-2",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 3000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "Body", colorIndex = 5, trashed = false, createdAt = 1000L, updatedAt = 3000L)),
            hlcTimestamp = Hlc(3000L, 0, "devB").toString(),
        )

        val fileA = createEncryptedJournal("j-lww-A", "devA", 1L, listOf(opA))
        val fileB = createEncryptedJournal("j-lww-B", "devB", 1L, listOf(opB))

        syncInboxDao.insert(SyncInboxEntity("j-lww-A", "devA", 1L, 1, "dA", fileA.absolutePath, "shaA", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        syncInboxDao.insert(SyncInboxEntity("j-lww-B", "devB", 1L, 1, "dB", fileB.absolutePath, "shaB", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        val resultNote = noteDao.getNoteEntityByUuid(noteUuid)
        assertEquals(5, resultNote?.colorIndex)
    }

    @Test
    fun testConcurrentTitleEditConflictPreservation(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Local Title",
                body = "Unchanged Body",
                colorIndex = 1,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", Hlc(1000L, 0, localDeviceId).toString(), 1000L))

        // Remote concurrent title change with higher HLC
        val remoteHlc = Hlc(2000L, 0, remoteDeviceId)
        val op = JournalOperationDto(
            changeId = "title-change",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Remote Title", body = "Unchanged Body", colorIndex = 1, trashed = false, createdAt = 1000L, updatedAt = 2000L)),
            hlcTimestamp = remoteHlc.toString(),
        )

        val file = createEncryptedJournal("j-title", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-title", remoteDeviceId, 1L, 1, "dt", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        // 1. Winning title is applied to the note
        val updatedNote = noteDao.getNoteEntityByUuid(noteUuid)
        assertEquals("Remote Title", updatedNote?.title)

        // 2. Losing title is preserved in sync_conflict_records
        val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
        assertEquals(1, conflicts.size)
        val record = conflicts[0]
        assertEquals("title", record.fieldName)
        assertEquals("Remote Title", record.winningValue)
        assertEquals("Local Title", record.losingValue)
        assertEquals(remoteHlc.toString(), record.winningHlc)
        assertEquals(Hlc(1000L, 0, localDeviceId).toString(), record.losingHlc)
    }

    @Test
    fun testConcurrentBodyEditConflictPreservation(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Title",
                body = "Local Body Version",
                colorIndex = 1,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "body", Hlc(1000L, 0, localDeviceId).toString(), 1000L))

        // Remote concurrent body edit
        val remoteHlc = Hlc(2000L, 0, remoteDeviceId)
        val op = JournalOperationDto(
            changeId = "body-change",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "Remote Body Version", colorIndex = 1, trashed = false, createdAt = 1000L, updatedAt = 2000L)),
            hlcTimestamp = remoteHlc.toString(),
        )

        val file = createEncryptedJournal("j-body", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-body", remoteDeviceId, 1L, 1, "db", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        val updatedNote = noteDao.getNoteEntityByUuid(noteUuid)
        assertEquals("Remote Body Version", updatedNote?.body)

        val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
        assertEquals(1, conflicts.size)
        assertEquals("body", conflicts[0].fieldName)
        assertEquals("Remote Body Version", conflicts[0].winningValue)
        assertEquals("Local Body Version", conflicts[0].losingValue)
    }

    @Test
    fun testConflictRecordIdempotencyOnJournalReplay(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Title",
                body = "Original Body",
                colorIndex = 1,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "body", Hlc(1000L, 0, localDeviceId).toString(), 1000L))

        val remoteHlc = Hlc(2000L, 0, remoteDeviceId)
        val op = JournalOperationDto(
            changeId = "body-change-replay",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "New Body", colorIndex = 1, trashed = false, createdAt = 1000L, updatedAt = 2000L)),
            hlcTimestamp = remoteHlc.toString(),
        )

        val file = createEncryptedJournal("j-body-replay", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-body-replay", remoteDeviceId, 1L, 1, "db", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()
        assertEquals(1, conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid).size)

        // Reset inbox entry status to VALIDATED to simulate replay
        syncInboxDao.updateStatus("j-body-replay", InboxStatus.VALIDATED)
        syncWatermarkDao.updateAppliedSequence(remoteDeviceId, 0L, System.currentTimeMillis())

        ingestionEngine.ingestAll()
        // Deduplication guarantee: should still be exactly 1 conflict record
        assertEquals(1, conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid).size)
    }

    @Test
    fun testTombstonePreventsStaleUpdateResurrection(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()

        // Tombstone recorded at Hlc(2000, 0, devA)
        tombstoneDao.insertOrUpdate(
            SyncTombstoneEntity(
                entityType = EntityType.NOTE.name,
                entityId = noteUuid,
                deletedAt = 2000L,
                hlcTimestamp = Hlc(2000L, 0, "devA").toString(),
                deviceId = "devA",
            ),
        )

        // Stale update arrives with Hlc(1500, 0, devB)
        val staleOp = JournalOperationDto(
            changeId = "stale-op",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1500L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Zombie Note", body = "Should Not Exist", colorIndex = 0, trashed = false, createdAt = 1500L, updatedAt = 1500L)),
            hlcTimestamp = Hlc(1500L, 0, "devB").toString(),
        )

        val file = createEncryptedJournal("j-zombie", "devB", 1L, listOf(staleOp))
        syncInboxDao.insert(SyncInboxEntity("j-zombie", "devB", 1L, 1, "dz", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        // Zombie note must NOT be created
        assertNull(noteDao.getNoteEntityByUuid(noteUuid))
        // Tombstone must remain intact
        assertNotNull(tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid))
    }

    @Test
    fun testNewerUpdateResurrectsEntityAndClearsTombstone(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()

        // Deleted at Hlc(2000, 0, devA)
        tombstoneDao.insertOrUpdate(
            SyncTombstoneEntity(
                entityType = EntityType.NOTE.name,
                entityId = noteUuid,
                deletedAt = 2000L,
                hlcTimestamp = Hlc(2000L, 0, "devA").toString(),
                deviceId = "devA",
            ),
        )

        // Newer update arrives with Hlc(3000, 0, devB)
        val newerOp = JournalOperationDto(
            changeId = "resurrect-op",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 3000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Resurrected Note", body = "Back to Life", colorIndex = 1, trashed = false, createdAt = 3000L, updatedAt = 3000L)),
            hlcTimestamp = Hlc(3000L, 0, "devB").toString(),
        )

        val file = createEncryptedJournal("j-resurrect", "devB", 1L, listOf(newerOp))
        syncInboxDao.insert(SyncInboxEntity("j-resurrect", "devB", 1L, 1, "dr", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid))
        assertEquals("Resurrected Note", noteDao.getNoteEntityByUuid(noteUuid)?.title)
        assertNull("Tombstone should be cleared by newer update", tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid))
    }

    @Test
    fun testStaleDeleteRejectedWhenUpdateIsNewer(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Active Note",
                body = "Active Content",
                colorIndex = 1,
                trashed = false,
                createdAt = 3000L,
                updatedAt = 3000L,
            ),
        )
        // Record field metadata at HLC 3000
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", Hlc(3000L, 0, "devA").toString(), 3000L))

        // Stale delete arrives at HLC 2000
        val staleDeleteOp = JournalOperationDto(
            changeId = "stale-del",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.DELETE.name,
            createdAt = 2000L,
            payload = json.encodeToString(DeleteChangePayload(uuid = noteUuid, deletedAt = 2000L)),
            hlcTimestamp = Hlc(2000L, 0, "devB").toString(),
        )

        val file = createEncryptedJournal("j-stale-del", "devB", 1L, listOf(staleDeleteOp))
        syncInboxDao.insert(SyncInboxEntity("j-stale-del", "devB", 1L, 1, "dsd", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        // Note must NOT be deleted
        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid))
    }

    @Test
    fun testChecklistItemUpsertAndHlcFieldMerge(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val note = NoteEntity(id = 10L, uuid = noteUuid, kind = NoteKind.LIST, title = "List Note", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)
        noteDao.insert(note)

        val itemUuid = UUID.randomUUID().toString()
        val itemPayload = ChecklistItemChangePayload(
            uuid = itemUuid,
            noteUuid = noteUuid,
            text = "Buy Milk",
            checked = false,
            sortOrder = 1.0,
        )
        val op = JournalOperationDto(
            changeId = "item-op-1",
            entityType = EntityType.CHECKLIST_ITEM.name,
            entityId = itemUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1200L,
            payload = json.encodeToString(itemPayload),
            hlcTimestamp = Hlc(1200L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-item", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-item", remoteDeviceId, 1L, 1, "di", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        val item = itemDao.getByUuid(itemUuid)
        assertNotNull(item)
        assertEquals("Buy Milk", item?.text)
        assertFalse(item?.checked ?: true)
    }

    @Test
    fun testCategoryUpsertAndNameConflictResolution(): Unit = runBlocking {
        val catUuid = UUID.randomUUID().toString()
        val catPayload = CategoryChangePayload(
            uuid = catUuid,
            name = "Work",
            normalizedName = "work",
            colorHex = "#FF0000",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val op = JournalOperationDto(
            changeId = "cat-op-1",
            entityType = EntityType.CATEGORY.name,
            entityId = catUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(catPayload),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-cat", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-cat", remoteDeviceId, 1L, 1, "dc", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        val shoulder = shoulderDao.getByUuid(catUuid)
        assertNotNull(shoulder)
        assertEquals("Work", shoulder?.name)
        assertEquals("#FF0000", shoulder?.colorHex)
    }

    @Test
    fun testAttachmentPathTraversalSecurityRejection(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val note = NoteEntity(id = 5L, uuid = noteUuid, kind = NoteKind.NOTE, title = "Note", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)
        noteDao.insert(note)

        val attPayload = AttachmentChangePayload(
            uuid = UUID.randomUUID().toString(),
            noteUuid = noteUuid,
            uri = "../../../etc/passwd",
            displayName = "../malicious.txt",
        )
        val evilOp = JournalOperationDto(
            changeId = "evil-att",
            entityType = EntityType.ATTACHMENT_METADATA.name,
            entityId = attPayload.uuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(attPayload),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-evil", remoteDeviceId, 1L, listOf(evilOp))
        syncInboxDao.insert(SyncInboxEntity("j-evil", remoteDeviceId, 1L, 1, "de", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        val summary = ingestionEngine.ingestAll()

        assertEquals(1, summary.failedJournalsCount)
        assertEquals(InboxStatus.FAILED, syncInboxDao.getByJournalId("j-evil")?.status)
        assertEquals(0L, syncWatermarkDao.getWatermark(remoteDeviceId)?.highestAppliedSeq ?: 0L)
    }

    @Test
    fun testUnsupportedOperationThrowsAndFailsJournal(): Unit = runBlocking {
        val op = JournalOperationDto(
            changeId = "unknown-op",
            entityType = "FUTURE_QUANTUM_ENTITY",
            entityId = "some-id",
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = "{}",
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-unknown", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-unknown", remoteDeviceId, 1L, 1, "du", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        val summary = ingestionEngine.ingestAll()

        assertEquals(1, summary.failedJournalsCount)
        assertEquals(InboxStatus.FAILED, syncInboxDao.getByJournalId("j-unknown")?.status)
    }

    @Test(timeout = 10_000L)
    fun testCrashRecoveryInterruptedApplyingState(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val op = JournalOperationDto(
            changeId = "interrupted-op",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Interrupted", body = "Recovered", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-crash", remoteDeviceId, 1L, listOf(op))
        // Simulated journal left in APPLYING state after crash
        syncInboxDao.insert(SyncInboxEntity("j-crash", remoteDeviceId, 1L, 1, "dc", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.APPLYING))

        val summary = ingestionEngine.ingestAll()

        assertEquals(1, summary.processedJournalsCount)
        assertEquals(InboxStatus.APPLIED, syncInboxDao.getByJournalId("j-crash")?.status)
        assertEquals(1L, syncWatermarkDao.getWatermark(remoteDeviceId)?.highestAppliedSeq)
        assertNotNull(noteDao.getNoteEntityByUuid(noteUuid))
    }

    @Test
    fun testLocalOperationIsolationZeroPendingChangesGenerated(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val op = JournalOperationDto(
            changeId = "iso-op",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Remote Title", body = "Remote Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )

        val file = createEncryptedJournal("j-iso", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-iso", remoteDeviceId, 1L, 1, "di", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        // Pre-condition: pending changes are empty
        assertEquals(0, pendingChangeDao.changes.size)

        ingestionEngine.ingestAll()

        // Post-condition: remote ingestion MUST NOT create outbound local pending changes
        assertEquals("Remote ingestion should generate zero outbound pending changes", 0, pendingChangeDao.changes.size)
    }

    @Test
    fun testLocalPendingChangesPreservedAcrossRemoteIngestion(): Unit = runBlocking {
        // Local user created a pending change
        val localNote = NoteEntity(id = 1L, uuid = "local-uuid", kind = NoteKind.NOTE, title = "Local Unsynced", body = "Local Draft", colorIndex = 0, trashed = false, createdAt = 500L, updatedAt = 500L)
        noteDao.insert(localNote)
        changeRecorder.recordNoteUpsert(localNote)
        assertEquals(1, pendingChangeDao.changes.size)

        // Ingest remote journal
        val remoteNoteUuid = UUID.randomUUID().toString()
        val op = JournalOperationDto(
            changeId = "remote-op-p",
            entityType = EntityType.NOTE.name,
            entityId = remoteNoteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(NoteChangePayload(uuid = remoteNoteUuid, kind = "NOTE", title = "Remote", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
            hlcTimestamp = Hlc(1000L, 0, remoteDeviceId).toString(),
        )
        val file = createEncryptedJournal("j-pres", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-pres", remoteDeviceId, 1L, 1, "dp", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        // Local pending changes must remain intact
        assertEquals(1, pendingChangeDao.changes.size)
        assertEquals("local-uuid", pendingChangeDao.changes.values.first().entityId)
    }

    @Test
    fun testHlcClockCausallyMergesRemoteTimestamps(): Unit = runBlocking {
        val initialHlc = hlcClock.peek()

        // Remote operation with physical timestamp in the future
        val futureRemoteTime = System.currentTimeMillis() + 100_000L
        val remoteHlc = Hlc(futureRemoteTime, 42, remoteDeviceId)

        val op = JournalOperationDto(
            changeId = "hlc-op",
            entityType = EntityType.NOTE.name,
            entityId = UUID.randomUUID().toString(),
            operationType = OperationType.UPSERT.name,
            createdAt = futureRemoteTime,
            payload = json.encodeToString(NoteChangePayload(uuid = UUID.randomUUID().toString(), kind = "NOTE", title = "Future", body = "", colorIndex = 0, trashed = false, createdAt = futureRemoteTime, updatedAt = futureRemoteTime)),
            hlcTimestamp = remoteHlc.toString(),
        )

        val file = createEncryptedJournal("j-hlc", remoteDeviceId, 1L, listOf(op))
        syncInboxDao.insert(SyncInboxEntity("j-hlc", remoteDeviceId, 1L, 1, "dh", file.absolutePath, "sha", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))

        ingestionEngine.ingestAll()

        val postIngestionHlc = hlcClock.peek()
        assertTrue("Local HLC must causally advance past remote HLC", postIngestionHlc >= remoteHlc)
    }

    @Test
    fun testIdenticalValueNewerHlcFollowedByOlderConflictingHlc(): Unit = runBlocking {
        val noteUuid = UUID.randomUUID().toString()
        val origTime = 1000L
        val origHlc = Hlc(origTime, 0, "dev-orig")

        // 1. Existing baseline note in database with Title A, colorIndex = 1
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = noteUuid,
                kind = NoteKind.NOTE,
                title = "Title A",
                body = "Body Initial",
                colorIndex = 1,
                trashed = false,
                createdAt = origTime,
                updatedAt = origTime,
                pinnedAt = null,
            ),
        )
        // Explicitly set field metadata at HLC 1000
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", origHlc.toString(), origTime))
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "colorIndex", origHlc.toString(), origTime))
        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "pinnedAt", origHlc.toString(), origTime))

        // 2. Incoming operation 2 with newer HLC 2000 has value = "Title A" and colorIndex = 1 (identical values)
        val newerTime = 2000L
        val devNewer = "remote-dev-newer"
        val newerHlc = Hlc(newerTime, 0, devNewer)
        val op2 = JournalOperationDto(
            changeId = "op-newer",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = newerTime,
            payload = json.encodeToString(
                NoteChangePayload(
                    uuid = noteUuid,
                    kind = "NOTE",
                    title = "Title A", // identical
                    body = "Body Initial", // identical
                    colorIndex = 1, // identical
                    trashed = false,
                    createdAt = origTime,
                    updatedAt = newerTime,
                    pinnedAt = 2000L, // changed
                ),
            ),
            hlcTimestamp = newerHlc.toString(),
        )

        val file2 = createEncryptedJournal("j-newer", devNewer, 1L, listOf(op2))
        syncInboxDao.insert(
            SyncInboxEntity("j-newer", devNewer, 1L, 1, "d2", file2.absolutePath, "sha2", 1, System.currentTimeMillis(), InboxStatus.VALIDATED),
        )

        val summary1 = ingestionEngine.ingestAll()
        assertEquals(1, summary1.processedJournalsCount)

        // Verify that field values remain Title A and colorIndex = 1, pinnedAt updated
        val noteAfterOp2 = noteDao.getNoteEntityByUuid(noteUuid)
        assertNotNull(noteAfterOp2)
        assertEquals("Title A", noteAfterOp2?.title)
        assertEquals(1, noteAfterOp2?.colorIndex)
        assertEquals(2000L, noteAfterOp2?.pinnedAt)

        // CRITICAL: Stored field HLC metadata MUST advance to newerHlc (2000) even though values were identical!
        val titleMetaAfterOp2 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "title")
        val colorMetaAfterOp2 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "colorIndex")
        val pinnedMetaAfterOp2 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "pinnedAt")

        assertEquals(newerHlc.toString(), titleMetaAfterOp2?.hlcTimestamp)
        assertEquals(newerHlc.toString(), colorMetaAfterOp2?.hlcTimestamp)
        assertEquals(newerHlc.toString(), pinnedMetaAfterOp2?.hlcTimestamp)

        // 3. Incoming operation 3 with intermediate HLC 1500 has value = "Title B" and colorIndex = 5
        val olderTime = 1500L
        val devOlder = "remote-dev-older"
        val olderHlc = Hlc(olderTime, 0, devOlder)
        val op3 = JournalOperationDto(
            changeId = "op-older",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = olderTime,
            payload = json.encodeToString(
                NoteChangePayload(
                    uuid = noteUuid,
                    kind = "NOTE",
                    title = "Title B", // conflicting
                    body = "Body Initial",
                    colorIndex = 5, // conflicting
                    trashed = false,
                    createdAt = origTime,
                    updatedAt = olderTime,
                    pinnedAt = null, // conflicting
                ),
            ),
            hlcTimestamp = olderHlc.toString(),
        )

        val file3 = createEncryptedJournal("j-older", devOlder, 1L, listOf(op3))
        syncInboxDao.insert(
            SyncInboxEntity("j-older", devOlder, 1L, 1, "d3", file3.absolutePath, "sha3", 1, System.currentTimeMillis(), InboxStatus.VALIDATED),
        )

        val summary2 = ingestionEngine.ingestAll()
        assertEquals(1, summary2.processedJournalsCount)

        // Operation 3 must be rejected as stale for winning fields
        val finalNote = noteDao.getNoteEntityByUuid(noteUuid)
        assertNotNull(finalNote)
        assertEquals("Title A must win over stale Title B", "Title A", finalNote?.title)
        assertEquals("colorIndex 1 must win over stale colorIndex 5", 1, finalNote?.colorIndex)
        assertEquals("pinnedAt 2000L must win over stale pinnedAt null", 2000L, finalNote?.pinnedAt)

        // Metadata must NOT have regressed to 1500
        val finalTitleMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "title")
        val finalColorMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "colorIndex")
        val finalPinnedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "pinnedAt")

        assertEquals("Title metadata must remain at 2000", newerHlc.toString(), finalTitleMeta?.hlcTimestamp)
        assertEquals("Color metadata must remain at 2000", newerHlc.toString(), finalColorMeta?.hlcTimestamp)
        assertEquals("Pinned metadata must remain at 2000", newerHlc.toString(), finalPinnedMeta?.hlcTimestamp)
    }

    @Test
    fun testArrivalOrderIndependenceForConcurrentFieldUpdates(): Unit = runBlocking {
        val note1Uuid = UUID.randomUUID().toString()
        val note2Uuid = UUID.randomUUID().toString()
        val origTime = 1000L
        val origHlc = Hlc(origTime, 0, "dev-orig")

        // Setup identical baseline notes in database
        listOf(note1Uuid, note2Uuid).forEachIndexed { idx, uuid ->
            noteDao.insert(
                NoteEntity(
                    id = (idx + 1).toLong(),
                    uuid = uuid,
                    kind = NoteKind.NOTE,
                    title = "Title A",
                    body = "Common Body",
                    colorIndex = 1,
                    trashed = false,
                    createdAt = origTime,
                    updatedAt = origTime,
                    pinnedAt = null,
                ),
            )
            conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, uuid, "title", origHlc.toString(), origTime))
            conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, uuid, "colorIndex", origHlc.toString(), origTime))
            conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, uuid, "pinnedAt", origHlc.toString(), origTime))
        }

        val devNewer = "dev-newer"
        val devOlder = "dev-older"
        val newerHlc = Hlc(2000L, 0, devNewer)
        val olderHlc = Hlc(1500L, 0, devOlder)

        fun createOpForNote(uuid: String, isNewer: Boolean): JournalOperationDto {
            return if (isNewer) {
                JournalOperationDto(
                    changeId = "op-newer-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload = json.encodeToString(
                        NoteChangePayload(
                            uuid = uuid,
                            kind = "NOTE",
                            title = "Title A",
                            body = "Common Body",
                            colorIndex = 1,
                            trashed = false,
                            createdAt = origTime,
                            updatedAt = 2000L,
                            pinnedAt = 2000L,
                        ),
                    ),
                    hlcTimestamp = newerHlc.toString(),
                )
            } else {
                JournalOperationDto(
                    changeId = "op-older-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(
                        NoteChangePayload(
                            uuid = uuid,
                            kind = "NOTE",
                            title = "Title B",
                            body = "Common Body",
                            colorIndex = 5,
                            trashed = false,
                            createdAt = origTime,
                            updatedAt = 1500L,
                            pinnedAt = null,
                        ),
                    ),
                    hlcTimestamp = olderHlc.toString(),
                )
            }
        }

        // Note 1: Arrival order = Op Newer (HLC 2000) then Op Older (HLC 1500)
        val op1Newer = createOpForNote(note1Uuid, isNewer = true)
        val op1Older = createOpForNote(note1Uuid, isNewer = false)
        val file1Newer = createEncryptedJournal("j-n1-newer", devNewer, 1L, listOf(op1Newer))
        val file1Older = createEncryptedJournal("j-n1-older", devOlder, 1L, listOf(op1Older))

        syncInboxDao.insert(SyncInboxEntity("j-n1-newer", devNewer, 1L, 1, "d1n", file1Newer.absolutePath, "sha1n", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        syncInboxDao.insert(SyncInboxEntity("j-n1-older", devOlder, 1L, 1, "d1o", file1Older.absolutePath, "sha1o", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        // Note 2: Arrival order = Op Older (HLC 1500) then Op Newer (HLC 2000)
        val devOlder2 = "dev-older2"
        val devNewer2 = "dev-newer2"
        val op2Older = createOpForNote(note2Uuid, isNewer = false)
        val op2Newer = createOpForNote(note2Uuid, isNewer = true)
        val file2Older = createEncryptedJournal("j-n2-older", devOlder2, 1L, listOf(op2Older))
        val file2Newer = createEncryptedJournal("j-n2-newer", devNewer2, 1L, listOf(op2Newer))

        syncInboxDao.insert(SyncInboxEntity("j-n2-older", devOlder2, 1L, 1, "d2o", file2Older.absolutePath, "sha2o", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        syncInboxDao.insert(SyncInboxEntity("j-n2-newer", devNewer2, 1L, 1, "d2n", file2Newer.absolutePath, "sha2n", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        // ASSERT: Final states must be identical regardless of arrival order
        val note1 = noteDao.getNoteEntityByUuid(note1Uuid)
        val note2 = noteDao.getNoteEntityByUuid(note2Uuid)

        assertNotNull(note1)
        assertNotNull(note2)
        assertEquals("Title must match across arrival orders", note1?.title, note2?.title)
        assertEquals("Title must be Title A", "Title A", note1?.title)

        assertEquals("ColorIndex must match across arrival orders", note1?.colorIndex, note2?.colorIndex)
        assertEquals("ColorIndex must be 1", 1, note1?.colorIndex)

        assertEquals("PinnedAt must match across arrival orders", note1?.pinnedAt, note2?.pinnedAt)
        assertEquals("PinnedAt must be 2000L", 2000L, note1?.pinnedAt)

        // Metadata must match
        val n1TitleMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note1Uuid, "title")
        val n2TitleMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note2Uuid, "title")
        assertEquals(n1TitleMeta?.hlcTimestamp, n2TitleMeta?.hlcTimestamp)

        val n1ColorMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note1Uuid, "colorIndex")
        val n2ColorMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note2Uuid, "colorIndex")
        assertEquals(n1ColorMeta?.hlcTimestamp, n2ColorMeta?.hlcTimestamp)

        val n1PinnedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note1Uuid, "pinnedAt")
        val n2PinnedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note2Uuid, "pinnedAt")
        assertEquals(n1PinnedMeta?.hlcTimestamp, n2PinnedMeta?.hlcTimestamp)
    }

    @Test
    fun testComprehensiveArrivalOrderIndependenceAcrossAllFiveDimensions(): Unit = runBlocking {
        // Two independent device simulation streams for Note 1 and Note 2
        val note1Uuid = UUID.randomUUID().toString()
        val note2Uuid = UUID.randomUUID().toString()
        val baseTime = 1000L
        val baseHlc = Hlc(baseTime, 0, "dev-base")

        // Initial baseline note
        fun baselineOp(uuid: String) = JournalOperationDto(
            changeId = "base-$uuid",
            entityType = EntityType.NOTE.name,
            entityId = uuid,
            operationType = OperationType.UPSERT.name,
            createdAt = baseTime,
            payload = json.encodeToString(
                NoteChangePayload(
                    uuid = uuid,
                    kind = "NOTE",
                    title = "Title Base",
                    body = "Body Base",
                    colorIndex = 1,
                    trashed = false,
                    archived = false,
                    pinnedAt = null,
                    completedAt = null,
                    categoryUuid = null,
                    reminderAt = null,
                    createdAt = baseTime,
                    updatedAt = baseTime,
                ),
            ),
            hlcTimestamp = baseHlc.toString(),
        )

        val jBase1 = createEncryptedJournal("j-base-1", "dev-init-1", 1L, listOf(baselineOp(note1Uuid)))
        val jBase2 = createEncryptedJournal("j-base-2", "dev-init-2", 1L, listOf(baselineOp(note2Uuid)))
        syncInboxDao.insert(SyncInboxEntity("j-base-1", "dev-init-1", 1L, 1, "d1", jBase1.absolutePath, "s1", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        syncInboxDao.insert(SyncInboxEntity("j-base-2", "dev-init-2", 1L, 1, "d2", jBase2.absolutePath, "s2", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        val hlcA = Hlc(1500L, 0, "dev-stream-A")
        val hlcB = Hlc(2000L, 0, "dev-stream-B")

        fun opStreamA(uuid: String) = JournalOperationDto(
            changeId = "op-A-$uuid",
            entityType = EntityType.NOTE.name,
            entityId = uuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1500L,
            payload = json.encodeToString(
                NoteChangePayload(
                    uuid = uuid,
                    kind = "NOTE",
                    title = "Title Stream A",
                    body = "Body Stream A",
                    colorIndex = 2,
                    trashed = true,
                    trashedAt = 1500L,
                    archived = false,
                    pinnedAt = 1500L,
                    completedAt = null,
                    createdAt = baseTime,
                    updatedAt = 1500L,
                ),
            ),
            hlcTimestamp = hlcA.toString(),
        )

        fun opStreamB(uuid: String) = JournalOperationDto(
            changeId = "op-B-$uuid",
            entityType = EntityType.NOTE.name,
            entityId = uuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 2000L,
            payload = json.encodeToString(
                NoteChangePayload(
                    uuid = uuid,
                    kind = "NOTE",
                    title = "Title Stream B",
                    body = "Body Stream B",
                    colorIndex = 2,
                    trashed = false,
                    archived = true,
                    pinnedAt = null,
                    completedAt = 2000L,
                    createdAt = baseTime,
                    updatedAt = 2000L,
                ),
            ),
            hlcTimestamp = hlcB.toString(),
        )

        // Path 1 for Note 1: Stream A (1500) applied before Stream B (2000)
        val jA1 = createEncryptedJournal("j-A-1", "dev-sA", 1L, listOf(opStreamA(note1Uuid)))
        val jB1 = createEncryptedJournal("j-B-1", "dev-sB", 1L, listOf(opStreamB(note1Uuid)))
        syncInboxDao.insert(SyncInboxEntity("j-A-1", "dev-sA", 1L, 1, "dA1", jA1.absolutePath, "sA1", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()
        syncInboxDao.insert(SyncInboxEntity("j-B-1", "dev-sB", 1L, 1, "dB1", jB1.absolutePath, "sB1", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        // Path 2 for Note 2: Stream B (2000) applied before Stream A (1500)
        val jB2 = createEncryptedJournal("j-B-2", "dev-sB2", 1L, listOf(opStreamB(note2Uuid)))
        val jA2 = createEncryptedJournal("j-A-2", "dev-sA2", 1L, listOf(opStreamA(note2Uuid)))
        syncInboxDao.insert(SyncInboxEntity("j-B-2", "dev-sB2", 1L, 1, "dB2", jB2.absolutePath, "sB2", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()
        syncInboxDao.insert(SyncInboxEntity("j-A-2", "dev-sA2", 1L, 1, "dA2", jA2.absolutePath, "sA2", 1, System.currentTimeMillis(), InboxStatus.VALIDATED))
        ingestionEngine.ingestAll()

        val n1 = noteDao.getNoteEntityByUuid(note1Uuid)
        val n2 = noteDao.getNoteEntityByUuid(note2Uuid)

        // 1. DIMENSION 1: Final Entity Values Equivalence
        assertNotNull(n1)
        assertNotNull(n2)
        assertEquals("Title converges to Stream B", n1?.title, n2?.title)
        assertEquals("Title Stream B", n1?.title)
        assertEquals("Body converges to Stream B", n1?.body, n2?.body)
        assertEquals("Body Stream B", n1?.body)
        assertEquals("Color converges to 2", n1?.colorIndex, n2?.colorIndex)
        assertEquals(2, n1?.colorIndex)
        assertEquals("Trashed converges to false", n1?.trashed, n2?.trashed)
        assertEquals(false, n1?.trashed)
        assertEquals("Archived converges to true", n1?.archived, n2?.archived)
        assertEquals(true, n1?.archived)
        assertEquals("PinnedAt converges to null", n1?.pinnedAt, n2?.pinnedAt)
        assertEquals(null, n1?.pinnedAt)
        assertEquals("CompletedAt converges to 2000L", n1?.completedAt, n2?.completedAt)
        assertEquals(2000L, n1?.completedAt)

        // 2. DIMENSION 2: Per-Field HLC Metadata Equivalence
        listOf("title", "body", "colorIndex", "trashed", "archived", "pinnedAt", "completedAt").forEach { field ->
            val m1 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note1Uuid, field)
            val m2 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note2Uuid, field)
            assertEquals("Metadata for field $field must match", m1?.hlcTimestamp, m2?.hlcTimestamp)
            assertEquals("Dominating HLC must be 2000 for field $field", hlcB.toString(), m1?.hlcTimestamp)
        }

        // 3. DIMENSION 3: Conflict Records Preservation
        val c1 = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, note1Uuid)
        val c2 = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, note2Uuid)
        assertTrue("Conflict records preserved for Path 1", c1.isNotEmpty())
        assertTrue("Conflict records preserved for Path 2", c2.isNotEmpty())

        // 4. DIMENSION 4: Applied Operation Ledger IDs
        assertTrue(appliedOperationDao.isApplied("op-A-$note1Uuid"))
        assertTrue(appliedOperationDao.isApplied("op-B-$note1Uuid"))
        assertTrue(appliedOperationDao.isApplied("op-A-$note2Uuid"))
        assertTrue(appliedOperationDao.isApplied("op-B-$note2Uuid"))

        // 5. DIMENSION 5: Journal Application States
        assertEquals(InboxStatus.APPLIED, syncInboxDao.getByJournalId("j-A-1")?.status)
        assertEquals(InboxStatus.APPLIED, syncInboxDao.getByJournalId("j-B-1")?.status)
        assertEquals(InboxStatus.APPLIED, syncInboxDao.getByJournalId("j-B-2")?.status)
        assertEquals(InboxStatus.APPLIED, syncInboxDao.getByJournalId("j-A-2")?.status)

        assertEquals(1L, syncWatermarkDao.getWatermark("dev-sA")?.highestAppliedSeq)
        assertEquals(1L, syncWatermarkDao.getWatermark("dev-sB")?.highestAppliedSeq)
        assertEquals(1L, syncWatermarkDao.getWatermark("dev-sB2")?.highestAppliedSeq)
        assertEquals(1L, syncWatermarkDao.getWatermark("dev-sA2")?.highestAppliedSeq)
    }

    @Test
    fun testTransactionalRollbackOnFieldMutationFailure(): Unit = runBlocking {
        // Create an engine with a transactional runner that supports snapshot rollback
        val transactionalRunner: suspend (suspend () -> Unit) -> Unit = { block ->
            val noteSnapshot = noteDao.notes.toMap()
            val metaSnapshot = conflictMetadataDao.metadata.toMap()
            val appliedSnapshot = appliedOperationDao.operations.toMap()
            try {
                block()
            } catch (e: Throwable) {
                // Rollback in-memory state on exception
                noteDao.notes.clear()
                noteDao.notes.putAll(noteSnapshot)
                conflictMetadataDao.metadata.clear()
                conflictMetadataDao.metadata.putAll(metaSnapshot)
                appliedOperationDao.operations.clear()
                appliedOperationDao.operations.putAll(appliedSnapshot)
                throw e
            }
        }

        val transactionalEngine = RemoteJournalIngestionEngine(
            database = null,
            syncInboxDao = syncInboxDao,
            syncWatermarkDao = syncWatermarkDao,
            appliedOperationDao = appliedOperationDao,
            fieldConflictResolver = fieldConflictResolver,
            hlcClock = hlcClock,
            mekManager = mekManager,
            overrideKey = masterKey,
            transactionRunner = transactionalRunner,
        )

        val noteUuid1 = UUID.randomUUID().toString()
        val noteUuid2 = UUID.randomUUID().toString()

        // Valid operation 1
        val op1 = JournalOperationDto(
            changeId = "tx-op-1",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid1,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(
                NoteChangePayload(uuid = noteUuid1, kind = "NOTE", title = "Valid Note", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
            ),
            hlcTimestamp = Hlc(1000L, 0, "dev-tx").toString(),
        )

        // Invalid operation 2: unsupported schema version causes UnsupportedOperationException
        val op2 = JournalOperationDto(
            changeId = "tx-op-2",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid2,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            schemaVersion = 999, // unsupported schema version throws in FieldConflictResolver
            payload = "{}",
            hlcTimestamp = Hlc(1000L, 1, "dev-tx").toString(),
        )

        val file = createEncryptedJournal("j-tx-fail", "dev-tx", 1L, listOf(op1, op2))
        syncInboxDao.insert(
            SyncInboxEntity("j-tx-fail", "dev-tx", 1L, 1, "dtx", file.absolutePath, "shatx", 1, System.currentTimeMillis(), InboxStatus.VALIDATED),
        )

        val summary = transactionalEngine.ingestAll()

        // Summary must report 1 failed journal
        assertEquals(1, summary.failedJournalsCount)
        assertEquals(0, summary.appliedOperationsCount)

        // VERIFY COMPLETE ROLLBACK:
        // 1. Op1 note must NOT exist in the database!
        assertNull("Note from Op 1 must be rolled back", noteDao.getNoteEntityByUuid(noteUuid1))

        // 2. Op1 metadata must NOT exist in conflictMetadataDao!
        val meta1 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid1, "title")
        assertNull("Field metadata from Op 1 must be rolled back", meta1)

        // 3. Op1 must NOT be marked in appliedOperationDao!
        assertFalse("Applied operation ledger must not record rolled-back op1", appliedOperationDao.isApplied("tx-op-1"))

        // 4. Journal status in inbox must be FAILED!
        assertEquals("Journal status must transition to FAILED", InboxStatus.FAILED, syncInboxDao.getByJournalId("j-tx-fail")?.status)

        // 5. Watermark must NOT advance!
        val watermark = syncWatermarkDao.getWatermark("dev-tx")
        assertEquals(0L, watermark?.highestAppliedSeq ?: 0L)
    }

    @Test
    fun testTransactionalRollbackOnAppliedOperationLedgerFailure(): Unit = runBlocking {
        // Create an applied operation DAO that throws on insert
        var failOnInsert = true
        val failingAppliedDao = object : SyncAppliedOperationDao {
            val ops = mutableMapOf<String, SyncAppliedOperationEntity>()
            override suspend fun insert(operation: SyncAppliedOperationEntity): Long {
                if (failOnInsert) throw RuntimeException("Disk full / constraint failure in appliedOperationDao")
                ops[operation.changeId] = operation
                return 1L
            }
            override suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity? = ops[changeId]
            override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> = ops.values.filter { it.journalId == journalId }
            override suspend fun isApplied(changeId: String): Boolean = ops.containsKey(changeId)
            override suspend fun clearAll() { ops.clear() }
        }

        val transactionalRunner: suspend (suspend () -> Unit) -> Unit = { block ->
            val noteSnapshot = noteDao.notes.toMap()
            val metaSnapshot = conflictMetadataDao.metadata.toMap()
            try {
                block()
            } catch (e: Throwable) {
                noteDao.notes.clear()
                noteDao.notes.putAll(noteSnapshot)
                conflictMetadataDao.metadata.clear()
                conflictMetadataDao.metadata.putAll(metaSnapshot)
                throw e
            }
        }

        val failingEngine = RemoteJournalIngestionEngine(
            database = null,
            syncInboxDao = syncInboxDao,
            syncWatermarkDao = syncWatermarkDao,
            appliedOperationDao = failingAppliedDao,
            fieldConflictResolver = fieldConflictResolver,
            hlcClock = hlcClock,
            mekManager = mekManager,
            overrideKey = masterKey,
            transactionRunner = transactionalRunner,
        )

        val noteUuid = UUID.randomUUID().toString()
        val op = JournalOperationDto(
            changeId = "fail-ledger-op",
            entityType = EntityType.NOTE.name,
            entityId = noteUuid,
            operationType = OperationType.UPSERT.name,
            createdAt = 1000L,
            payload = json.encodeToString(
                NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Will Roll Back", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
            ),
            hlcTimestamp = Hlc(1000L, 0, "dev-fail-ledger").toString(),
        )

        val file = createEncryptedJournal("j-fail-ledger", "dev-fail-ledger", 1L, listOf(op))
        syncInboxDao.insert(
            SyncInboxEntity("j-fail-ledger", "dev-fail-ledger", 1L, 1, "dfl", file.absolutePath, "shafl", 1, System.currentTimeMillis(), InboxStatus.VALIDATED),
        )

        val summary = failingEngine.ingestAll()
        assertEquals(1, summary.failedJournalsCount)

        // VERIFY ROLLBACK:
        assertNull("Note must be rolled back", noteDao.getNoteEntityByUuid(noteUuid))
        assertNull("Metadata must be rolled back", conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "title"))
        assertEquals(InboxStatus.FAILED, syncInboxDao.getByJournalId("j-fail-ledger")?.status)
        assertEquals(0L, syncWatermarkDao.getWatermark("dev-fail-ledger")?.highestAppliedSeq ?: 0L)
    }

    // --- Fake Test DAOs ---

    private class FakeNoteDao : NoteDao {
        val notes = mutableMapOf<String, NoteEntity>()

        override suspend fun insert(note: NoteEntity): Long {
            val assignedId = if (note.id == 0L) (notes.size + 1).toLong() else note.id
            val stored = note.copy(id = assignedId)
            notes[note.uuid] = stored
            return assignedId
        }

        override suspend fun update(note: NoteEntity) {
            notes[note.uuid] = note
        }

        override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes[uuid]

        override suspend fun deleteById(id: Long) {
            notes.values.find { it.id == id }?.let { notes.remove(it.uuid) }
        }

        override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(null)
        override suspend fun get(id: Long): NoteWithItems? = null
        override suspend fun getByUuid(uuid: String): NoteWithItems? = null
        override suspend fun allNotes(): List<NoteWithItems> = emptyList()
        override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = emptyList()
        override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()
        override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override suspend fun setTrashed(id: Long, trashed: Boolean, updatedAt: Long) {}
        override suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long) {}
        override suspend fun allNoteIds(): List<Long> = notes.values.map { it.id }
        override suspend fun countPictureUri(uri: String): Int = 0
        override suspend fun trashedNoteIds(): List<Long> = emptyList()
        override suspend fun archivedNoteIds(): List<Long> = emptyList()
        override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()
        override suspend fun deleteAllNotes() { notes.clear() }
        override suspend fun emptyTrash() {}
        override suspend fun deleteTrashedOlderThan(cutoff: Long) {}
    }

    private class FakeChecklistItemDao : ChecklistItemDao {
        val items = mutableMapOf<String, ChecklistItemEntity>()

        override suspend fun insert(item: ChecklistItemEntity): Long {
            val assignedId = if (item.id == 0L) (items.size + 1).toLong() else item.id
            val stored = item.copy(id = assignedId)
            items[item.uuid] = stored
            return assignedId
        }

        override suspend fun insertAll(items: List<ChecklistItemEntity>) {
            items.forEach { insert(it) }
        }

        override suspend fun update(item: ChecklistItemEntity) {
            items[item.uuid] = item
        }

        override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = items[uuid]

        override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> =
            items.values.filter { it.noteId == noteId }

        override suspend fun deleteById(id: Long) {
            items.values.find { it.id == id }?.let { items.remove(it.uuid) }
        }

        override suspend fun deleteForNote(noteId: Long) {
            items.values.filter { it.noteId == noteId }.forEach { items.remove(it.uuid) }
        }
    }

    private class FakeShoulderDao : ShoulderDao {
        val shoulders = mutableMapOf<String, ShoulderEntity>()

        override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
            val assignedId = if (shoulder.id == 0L) (shoulders.size + 1).toLong() else shoulder.id
            val stored = shoulder.copy(id = assignedId)
            shoulders[shoulder.uuid] = stored
            return assignedId
        }

        override suspend fun updateShoulder(shoulder: ShoulderEntity) {
            shoulders[shoulder.uuid] = shoulder
        }

        override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders[uuid]

        override suspend fun deleteShoulder(shoulder: ShoulderEntity) {
            shoulders.remove(shoulder.uuid)
        }

        override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders.values.toList())
        override suspend fun allShoulders(): List<ShoulderEntity> = shoulders.values.toList()
        override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = shoulders.values.filter { it.ownerId == ownerId }
        override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())
        override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders.values.find { it.id == id }
        override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.values.find { it.normalizedName == normalizedName }
        override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()
        override suspend fun setShoulderForNote(noteId: Long, shoulderId: Long?) {}
    }

    private class FakeAttachmentDao : AttachmentDao {
        val attachments = mutableMapOf<String, NoteAttachmentEntity>()

        override suspend fun insert(attachment: NoteAttachmentEntity): Long {
            val assignedId = if (attachment.id == 0L) (attachments.size + 1).toLong() else attachment.id
            val stored = attachment.copy(id = assignedId)
            attachments[attachment.uuid] = stored
            return assignedId
        }

        override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) {
            attachments.forEach { insert(it) }
        }

        override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = attachments[uuid]
        override suspend fun getById(id: Long): NoteAttachmentEntity? = attachments.values.find { it.id == id }
        override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = attachments.values.filter { it.noteId == noteId }
        override suspend fun countByUri(uri: String): Int = attachments.values.count { it.uri == uri }
        override suspend fun deleteById(id: Long) { attachments.values.find { it.id == id }?.let { attachments.remove(it.uuid) } }
        override suspend fun deleteForNote(noteId: Long) { attachments.values.filter { it.noteId == noteId }.forEach { attachments.remove(it.uuid) } }
    }

    private class FakeSyncInboxDao : SyncInboxDao {
        val inbox = mutableMapOf<String, SyncInboxEntity>()

        override suspend fun insert(entry: SyncInboxEntity): Long {
            inbox[entry.journalId] = entry
            return 1L
        }

        override suspend fun update(entry: SyncInboxEntity) { inbox[entry.journalId] = entry }

        override suspend fun updateStatus(journalId: String, status: String): Int {
            val entry = inbox[journalId] ?: return 0
            inbox[journalId] = entry.copy(status = status)
            return 1
        }

        override suspend fun getByJournalId(journalId: String): SyncInboxEntity? = inbox[journalId]

        override suspend fun getByDeviceAndSequence(deviceId: String, sequence: Long): SyncInboxEntity? =
            inbox.values.find { it.deviceId == deviceId && it.sequence == sequence }

        override suspend fun getValidatedEntries(): List<SyncInboxEntity> =
            inbox.values.filter { it.status == InboxStatus.VALIDATED }

        override suspend fun getActiveDevicesForIngestion(): List<String> =
            inbox.values.filter { it.status in listOf(InboxStatus.VALIDATED, InboxStatus.APPLYING) }.map { it.deviceId }.distinct()

        override suspend fun getPendingEntriesForDevice(deviceId: String): List<SyncInboxEntity> =
            inbox.values.filter { it.deviceId == deviceId && it.status in listOf(InboxStatus.VALIDATED, InboxStatus.APPLYING) }.sortedBy { it.sequence }

        override suspend fun getEntriesForDevice(deviceId: String): List<SyncInboxEntity> =
            inbox.values.filter { it.deviceId == deviceId }

        override suspend fun getMaxValidatedSequence(deviceId: String): Long? =
            inbox.values.filter { it.deviceId == deviceId && it.status == InboxStatus.VALIDATED }.maxOfOrNull { it.sequence }

        override suspend fun getAllEntries(): List<SyncInboxEntity> = inbox.values.toList()
        override suspend fun deleteByJournalId(journalId: String): Int = if (inbox.remove(journalId) != null) 1 else 0
        override suspend fun clearAll() { inbox.clear() }
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
                highestAppliedSeq = watermarks[deviceId]?.highestAppliedSeq ?: 0L,
                missingSequencesJson = missingJson,
                updatedAt = timestamp,
            )
            return 1
        }

        override suspend fun updateAppliedSequence(deviceId: String, appliedSeq: Long, timestamp: Long): Int {
            val existing = watermarks[deviceId] ?: SyncWatermarkEntity(
                deviceId = deviceId,
                highestDiscoveredSeq = appliedSeq,
                highestDownloadedSeq = appliedSeq,
                highestValidatedSeq = appliedSeq,
                highestAppliedSeq = appliedSeq,
                missingSequencesJson = "[]",
                updatedAt = timestamp,
            )
            watermarks[deviceId] = existing.copy(highestAppliedSeq = appliedSeq, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteForDevice(deviceId: String): Int = if (watermarks.remove(deviceId) != null) 1 else 0
        override suspend fun clearAll() { watermarks.clear() }
    }

    private class FakeSyncAppliedOperationDao : SyncAppliedOperationDao {
        val operations = mutableMapOf<String, SyncAppliedOperationEntity>()

        override suspend fun insert(operation: SyncAppliedOperationEntity): Long {
            operations[operation.changeId] = operation
            return 1L
        }

        override suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity? = operations[changeId]

        override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> =
            operations.values.filter { it.journalId == journalId }

        override suspend fun isApplied(changeId: String): Boolean = operations.containsKey(changeId)

        override suspend fun clearAll() { operations.clear() }
    }

    private class FakeSyncConflictMetadataDao : SyncConflictMetadataDao {
        val metadata = mutableMapOf<Triple<String, String, String>, SyncConflictMetadataEntity>()

        override suspend fun insertOrUpdate(metadata: SyncConflictMetadataEntity) {
            this.metadata[Triple(metadata.entityType, metadata.entityId, metadata.fieldName)] = metadata
        }

        override suspend fun getFieldMetadata(entityType: String, entityId: String, fieldName: String): SyncConflictMetadataEntity? =
            metadata[Triple(entityType, entityId, fieldName)]

        override suspend fun getAllFieldsForEntity(entityType: String, entityId: String): List<SyncConflictMetadataEntity> =
            metadata.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun deleteForEntity(entityType: String, entityId: String): Int {
            val keysToRemove = metadata.keys.filter { it.first == entityType && it.second == entityId }
            keysToRemove.forEach { metadata.remove(it) }
            return keysToRemove.size
        }

        override suspend fun clearAll() { metadata.clear() }
    }

    private class FakeSyncTombstoneDao : SyncTombstoneDao {
        val tombstones = mutableMapOf<Pair<String, String>, SyncTombstoneEntity>()

        override suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity) {
            tombstones[Pair(tombstone.entityType, tombstone.entityId)] = tombstone
        }

        override suspend fun getTombstone(entityType: String, entityId: String): SyncTombstoneEntity? =
            tombstones[Pair(entityType, entityId)]

        override suspend fun deleteTombstone(entityType: String, entityId: String): Int =
            if (tombstones.remove(Pair(entityType, entityId)) != null) 1 else 0

        override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = tombstones.values.toList()

        override suspend fun clearAll() { tombstones.clear() }
    }

    private class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            if (records.any { it.conflictId == record.conflictId }) {
                return -1L // OnConflictStrategy.IGNORE
            }
            records.add(record)
            return records.size.toLong()
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? =
            records.find { it.conflictId == conflictId }

        override suspend fun getConflictsForEntity(entityType: String, entityId: String): List<SyncConflictRecordEntity> =
            records.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> =
            records.filter { !it.resolved }

        override suspend fun markResolved(id: Long): Int = 1

        override suspend fun clearAll() { records.clear() }
    }

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

        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = flowOf(emptyList())

        override suspend fun countPendingChanges(): Int = changes.values.count { it.status == "PENDING" }

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

        override suspend fun clearAll() { changes.clear() }
    }
}
