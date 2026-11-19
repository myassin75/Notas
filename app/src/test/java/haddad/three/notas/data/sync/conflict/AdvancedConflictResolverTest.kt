package haddad.three.notas.data.sync.conflict

import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.Visibility
import haddad.three.notas.data.sync.change.CategoryChangePayload
import haddad.three.notas.data.sync.change.ChecklistItemChangePayload
import haddad.three.notas.data.sync.change.DefaultLocalChangeRecorder
import haddad.three.notas.data.sync.change.DeleteChangePayload
import haddad.three.notas.data.sync.change.EntityType
import haddad.three.notas.data.sync.change.NoteChangePayload
import haddad.three.notas.data.sync.change.OperationOrigin
import haddad.three.notas.data.sync.change.OperationType
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.change.PreferenceChangePayload
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.hlc.InMemoryHlcStorage
import haddad.three.notas.data.sync.journal.JournalOperationDto
import haddad.three.notas.data.sync.journal.SyncJournalDto
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import haddad.three.notas.data.sync.transport.SyncWatermarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Deterministic automated conflict resolution test suite covering Phase 6 requirements:
 * Scenarios A through H across multiple independent device identities.
 */
class AdvancedConflictResolverTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val devA = "device-A"
    private val devB = "device-B"

    private lateinit var masterKey: SecretKey
    private lateinit var mekManagerA: MekManager
    private lateinit var mekManagerB: MekManager

    private lateinit var fixtureA: DeviceFixture
    private lateinit var fixtureB: DeviceFixture

    class DeviceFixture(
        val deviceId: String,
        val mekManager: MekManager,
    ) {
        val noteDao = FakeNoteDao()
        val itemDao = FakeChecklistItemDao()
        val shoulderDao = FakeShoulderDao()
        val attachmentDao = FakeAttachmentDao()
        val conflictMetadataDao = FakeSyncConflictMetadataDao()
        val tombstoneDao = FakeSyncTombstoneDao()
        val conflictRecordDao = FakeSyncConflictRecordDao()
        val pendingChangeDao = FakePendingChangeDao()
        val watermarkDao = FakeSyncWatermarkDao()
        val appliedOpDao = FakeSyncAppliedOperationDao()

        val hlcClock = HlcClock(nodeId = deviceId, storage = InMemoryHlcStorage())
        val localChangeRecorder =
            DefaultLocalChangeRecorder(
                pendingChangeDao = pendingChangeDao,
                deviceIdProvider = { deviceId },
                hlcClock = hlcClock,
                conflictMetadataDao = conflictMetadataDao,
            )

        val fieldResolver =
            FieldConflictResolver(
                noteDao = noteDao,
                checklistItemDao = itemDao,
                shoulderDao = shoulderDao,
                attachmentDao = attachmentDao,
                conflictMetadataDao = conflictMetadataDao,
                tombstoneDao = tombstoneDao,
                conflictRecordDao = conflictRecordDao,
                mekManager = mekManager,
                localDeviceIdProvider = { deviceId },
            )

        val resolutionManager =
            ConflictResolutionManager(
                database = null,
                noteDao = noteDao,
                conflictRecordDao = conflictRecordDao,
                conflictMetadataDao = conflictMetadataDao,
                tombstoneDao = tombstoneDao,
                localChangeRecorder = localChangeRecorder,
                hlcClock = hlcClock,
                mekManager = mekManager,
            )

        val conflictResolver =
            ConflictResolver(
                fieldConflictResolver = fieldResolver,
                resolutionManager = resolutionManager,
                conflictRecordDao = conflictRecordDao,
                conflictMetadataDao = conflictMetadataDao,
                tombstoneDao = tombstoneDao,
                syncWatermarkDao = watermarkDao,
                appliedOperationDao = appliedOpDao,
                localDeviceIdProvider = { deviceId },
            )
    }

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        masterKey = keyGen.generateKey()

        val keyStoreA = SoftwareKeyStoreManager()
        val storageA = InMemoryCryptoStorage()
        storageA.setLocalWrappedMek(keyStoreA.wrapKey(masterKey, 1), 1)
        mekManagerA = MekManager(keyStoreA, storageA)

        val keyStoreB = SoftwareKeyStoreManager()
        val storageB = InMemoryCryptoStorage()
        storageB.setLocalWrappedMek(keyStoreB.wrapKey(masterKey, 1), 1)
        mekManagerB = MekManager(keyStoreB, storageB)

        fixtureA = DeviceFixture(devA, mekManagerA)
        fixtureB = DeviceFixture(devB, mekManagerB)
    }

    private fun makeNote(
        id: Long = 0L,
        uuid: String = UUID.randomUUID().toString(),
        title: String = "",
        body: String = "",
        colorIndex: Int = 0,
        trashed: Boolean = false,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        completedAt: Long? = null,
        kind: NoteKind = NoteKind.NOTE,
    ) = NoteEntity(
        id = id,
        uuid = uuid,
        kind = kind,
        title = title,
        body = body,
        colorIndex = colorIndex,
        trashed = trashed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
    )

    private fun makeJournal(
        deviceId: String,
        sequence: Long,
        ops: List<JournalOperationDto>,
        dependencies: Map<String, Long> = emptyMap(),
    ): SyncJournalDto =
        SyncJournalDto(
            formatVersion = 1,
            journalId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            sequence = sequence,
            createdAt = System.currentTimeMillis(),
            operationCount = ops.size,
            operations = ops,
            dependencies = dependencies,
        )

    // ==========================================
    // SECTION 15.A: SEQUENTIAL CHANGES
    // ==========================================

    @Test
    fun testScenarioA_SequentialChanges_NoConflict() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()

            // 1. Device A creates a Note
            val hlcA1 = fixtureA.hlcClock.generateNext()
            val noteA =
                makeNote(
                    id = 1L,
                    uuid = noteUuid,
                    title = "Original Title",
                    body = "Original Body",
                    createdAt = hlcA1.physicalTime,
                    updatedAt = hlcA1.physicalTime,
                )
            fixtureA.noteDao.insert(noteA)
            fixtureA.localChangeRecorder.recordNoteUpsert(noteA)

            // Create Journal 1 on Device A
            val opA1 =
                JournalOperationDto(
                    changeId = "op-create-note",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = hlcA1.physicalTime,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Original Title", body = "Original Body", colorIndex = 0, trashed = false, createdAt = hlcA1.physicalTime, updatedAt = hlcA1.physicalTime)),
                    hlcTimestamp = hlcA1.toString(),
                )
            val journalA1 = makeJournal(devA, 1L, listOf(opA1))

            // 2. Device B receives it
            fixtureB.hlcClock.mergeRemote(hlcA1)
            fixtureB.conflictResolver.resolveAndApply(opA1, hlcA1, journalA1)
            fixtureB.watermarkDao.updateAppliedSequence(devA, 1L, System.currentTimeMillis())

            val noteOnB = fixtureB.noteDao.getNoteEntityByUuid(noteUuid)
            assertNotNull(noteOnB)
            assertEquals("Original Title", noteOnB?.title)

            // 3. Device B modifies it sequentially
            val hlcB2 = fixtureB.hlcClock.generateNext()
            val modifiedNoteB = noteOnB!!.copy(title = "Title Updated by B", updatedAt = hlcB2.physicalTime)
            fixtureB.noteDao.update(modifiedNoteB)
            fixtureB.localChangeRecorder.recordNoteUpsert(modifiedNoteB)

            val opB2 =
                JournalOperationDto(
                    changeId = "op-edit-b",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = hlcB2.physicalTime,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title Updated by B", body = "Original Body", colorIndex = 0, trashed = false, createdAt = hlcA1.physicalTime, updatedAt = hlcB2.physicalTime)),
                    hlcTimestamp = hlcB2.toString(),
                )
            // Device B carries causal dependency: devA -> 1L
            val journalB1 = makeJournal(devB, 1L, listOf(opB2), dependencies = mapOf(devA to 1L))

            // 4. Device A receives the modification from Device B
            fixtureA.hlcClock.mergeRemote(hlcB2)
            fixtureA.conflictResolver.resolveAndApply(opB2, hlcB2, journalB1)

            // 5. Verify causal ordering: Device A accepted B's update with NO CONFLICT created
            val finalNoteA = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Title Updated by B", finalNoteA?.title)
            val conflictsA = fixtureA.conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertTrue("Sequential modification must NOT create conflict records", conflictsA.isEmpty())
        }

    // ==========================================
    // SECTION 15.B: CONCURRENT NOTE CHANGES
    // ==========================================

    @Test
    fun testScenarioB_ConcurrentNoteChanges_BothPreservedAndRecoverable() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()

            // Common ancestor
            val baseTime = 1000L
            val baseHlc = Hlc(baseTime, 0, "origin")
            val baseOp =
                JournalOperationDto(
                    changeId = "op-base",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = baseTime,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Base Title", body = "Base Body", colorIndex = 0, trashed = false, createdAt = baseTime, updatedAt = baseTime)),
                    hlcTimestamp = baseHlc.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(baseOp, baseHlc, makeJournal("origin", 1L, listOf(baseOp)))
            fixtureB.conflictResolver.resolveAndApply(baseOp, baseHlc, makeJournal("origin", 1L, listOf(baseOp)))

            // 1. Both go offline and modify the same note concurrently
            val hlcA = Hlc(1500L, 0, devA)
            val noteA = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)!!.copy(title = "Alice Title", body = "Alice Content", updatedAt = 1500L)
            fixtureA.noteDao.update(noteA)
            fixtureA.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", hlcA.toString(), 1500L))
            fixtureA.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "body", hlcA.toString(), 1500L))

            val hlcB = Hlc(2000L, 0, devB)
            val noteB = fixtureB.noteDao.getNoteEntityByUuid(noteUuid)!!.copy(title = "Bob Title", body = "Bob Content", updatedAt = 2000L)
            fixtureB.noteDao.update(noteB)
            fixtureB.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", hlcB.toString(), 2000L))
            fixtureB.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "body", hlcB.toString(), 2000L))

            // Both prepare journals without knowledge of each other
            val opA =
                JournalOperationDto(
                    changeId = "op-alice",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Alice Title", body = "Alice Content", colorIndex = 0, trashed = false, createdAt = baseTime, updatedAt = 1500L)),
                    hlcTimestamp = hlcA.toString(),
                )
            val journalA = makeJournal(devA, 1L, listOf(opA))

            val opB =
                JournalOperationDto(
                    changeId = "op-bob",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Bob Title", body = "Bob Content", colorIndex = 0, trashed = false, createdAt = baseTime, updatedAt = 2000L)),
                    hlcTimestamp = hlcB.toString(),
                )
            val journalB = makeJournal(devB, 1L, listOf(opB))

            // 2. Both exchange and ingest remote journals
            fixtureA.conflictResolver.resolveAndApply(opB, hlcB, journalB)
            fixtureB.conflictResolver.resolveAndApply(opA, hlcA, journalA)

            // 3. Both detect the conflicts!
            val conflictsA = fixtureA.conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            val conflictsB = fixtureB.conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertTrue("Device A must record conflicts", conflictsA.isNotEmpty())
            assertTrue("Device B must record conflicts", conflictsB.isNotEmpty())

            // 4. Deterministic active state: Higher HLC (Bob at 2000) wins active view on both devices
            val currentA = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)
            val currentB = fixtureB.noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Bob Title", currentA?.title)
            assertEquals("Bob Title", currentB?.title)
            assertEquals("Bob Content", currentA?.body)
            assertEquals("Bob Content", currentB?.body)

            // 5. Competing version (Alice) is preserved and recoverable
            val bodyConflict = conflictsA.find { it.fieldName == "body" }
            assertNotNull(bodyConflict)
            val decryptedLosingBody = fixtureA.resolutionManager.decryptPayload(bodyConflict?.losingValue, bodyConflict!!.conflictId)
            assertEquals("Alice Content", decryptedLosingBody)
        }

    // ==========================================
    // SECTION 15.C: INDEPENDENT FIELD CHANGES
    // ==========================================

    @Test
    fun testScenarioC_IndependentFieldChanges_CleanMerge() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val baseTime = 1000L
            val baseHlc = Hlc(baseTime, 0, "origin")

            val baseOp =
                JournalOperationDto(
                    changeId = "op-ind-base",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = baseTime,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Original Title", body = "Original Body", colorIndex = 0, trashed = false, createdAt = baseTime, updatedAt = baseTime)),
                    hlcTimestamp = baseHlc.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(baseOp, baseHlc, makeJournal("origin", 1L, listOf(baseOp)))

            // Device A edits Title locally at 1500
            val hlcA = Hlc(1500L, 0, devA)
            val noteA = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)!!.copy(title = "New Title From A", updatedAt = 1500L)
            fixtureA.noteDao.update(noteA)
            fixtureA.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", hlcA.toString(), 1500L))

            // Device B concurrently changed colorIndex to 3 at 2000 (kept original title)
            val hlcB = Hlc(2000L, 0, devB)
            val opB =
                JournalOperationDto(
                    changeId = "op-color-b",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Original Title", body = "Original Body", colorIndex = 3, trashed = false, createdAt = baseTime, updatedAt = 2000L)),
                    hlcTimestamp = hlcB.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(opB, hlcB, makeJournal(devB, 1L, listOf(opB)))

            // Result on Device A: Title from A and ColorIndex from B merged cleanly!
            val mergedNote = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("New Title From A", mergedNote?.title)
            assertEquals(3, mergedNote?.colorIndex)
        }

    @Test
    fun testScenarioC_TaskCompletionPreservedAgainstStaleUncompletedUpdate() =
        runBlocking {
            val taskUuid = UUID.randomUUID().toString()
            val baseTime = 1000L
            val baseHlc = Hlc(baseTime, 0, "origin")

            val baseOp =
                JournalOperationDto(
                    changeId = "op-task-base",
                    entityType = EntityType.NOTE.name,
                    entityId = taskUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = baseTime,
                    payload = json.encodeToString(NoteChangePayload(uuid = taskUuid, kind = "TASK", title = "Task", body = "Desc", colorIndex = 0, trashed = false, completedAt = null, createdAt = baseTime, updatedAt = baseTime)),
                    hlcTimestamp = baseHlc.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(baseOp, baseHlc, makeJournal("origin", 1L, listOf(baseOp)))

            // Device A completes the task at 2000
            val hlcA = Hlc(2000L, 0, devA)
            val taskA = fixtureA.noteDao.getNoteEntityByUuid(taskUuid)!!.copy(completedAt = 2000L, updatedAt = 2000L)
            fixtureA.noteDao.update(taskA)
            fixtureA.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, taskUuid, "completedAt", hlcA.toString(), 2000L))

            // Stale update from Device B arrives (offline edit of description at 1500 with completedAt=null)
            val hlcB = Hlc(1500L, 0, devB)
            val staleOpB =
                JournalOperationDto(
                    changeId = "op-stale-b",
                    entityType = EntityType.NOTE.name,
                    entityId = taskUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(NoteChangePayload(uuid = taskUuid, kind = "TASK", title = "Task", body = "Updated Desc", colorIndex = 0, trashed = false, completedAt = null, createdAt = baseTime, updatedAt = 1500L)),
                    hlcTimestamp = hlcB.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(staleOpB, hlcB, makeJournal(devB, 1L, listOf(staleOpB)))

            // Completion state must be preserved and NOT reverted to null!
            val finalTask = fixtureA.noteDao.getNoteEntityByUuid(taskUuid)
            assertNotNull("Task completedAt must remain non-null", finalTask?.completedAt)
            assertEquals(2000L, finalTask?.completedAt)
        }

    // ==========================================
    // SECTION 15.D: DELETE VERSUS UPDATE
    // ==========================================

    @Test
    fun testScenarioD_DeleteVersusOlderUpdate_AntiResurrection() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()

            // Note deleted at HLC 2000
            val hlcDel = Hlc(2000L, 0, devA)
            fixtureA.tombstoneDao.insertOrUpdate(
                SyncTombstoneEntity(
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    deletedAt = 2000L,
                    hlcTimestamp = hlcDel.toString(),
                    deviceId = devA,
                ),
            )

            // Stale update from Device B at HLC 1500 arrives
            val hlcOld = Hlc(1500L, 0, devB)
            val staleOp =
                JournalOperationDto(
                    changeId = "op-old-resurrect",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Zombie Note", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1500L)),
                    hlcTimestamp = hlcOld.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(staleOp, hlcOld, makeJournal(devB, 1L, listOf(staleOp)))

            // Must NOT resurrect: note remains absent from Room
            assertNull("Stale update must not resurrect deleted note", fixtureA.noteDao.getNoteEntityByUuid(noteUuid))
            assertNotNull("Tombstone must be preserved", fixtureA.tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid))
        }

    @Test
    fun testScenarioD_ConcurrentDeleteAndUpdate_PreservesConflictAndData() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()

            // Note exists in Room
            val initialNote = makeNote(id = 1L, uuid = noteUuid, title = "Original", body = "Body", createdAt = 1000L, updatedAt = 1000L)
            fixtureA.noteDao.insert(initialNote)

            // Local user on Device A edits note at 2000
            val hlcUpd = Hlc(2000L, 0, devA)
            val updatedNote = initialNote.copy(title = "Local Important Edits", updatedAt = 2000L)
            fixtureA.noteDao.update(updatedNote)
            fixtureA.conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, noteUuid, "title", hlcUpd.toString(), 2000L))

            // Delete arrives from Device B authored at 1500 (concurrent / older)
            val hlcDel = Hlc(1500L, 0, devB)
            val delOp =
                JournalOperationDto(
                    changeId = "op-del-concurrent",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.DELETE.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(DeleteChangePayload(uuid = noteUuid, deletedAt = 1500L)),
                    hlcTimestamp = hlcDel.toString(),
                )
            fixtureA.conflictResolver.resolveAndApply(delOp, hlcDel, makeJournal(devB, 1L, listOf(delOp)))

            // Note must NOT be silently deleted!
            val noteAfter = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)
            assertNotNull("Concurrent update must NOT be deleted without recovery option", noteAfter)
            assertEquals("Local Important Edits", noteAfter?.title)

            // Conflict record must be preserved for DELETE_VS_UPDATE
            val conflicts = fixtureA.conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(1, conflicts.size)
            assertEquals("DELETE_VS_UPDATE", conflicts.first().fieldName)
        }

    @Test
    fun testScenarioD_TwoConcurrentDeletions_Idempotent() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlcDelA = Hlc(1500L, 0, devA)
            val hlcDelB = Hlc(1600L, 0, devB)

            val delOpA = JournalOperationDto(changeId = "delA", entityType = EntityType.NOTE.name, entityId = noteUuid, operationType = OperationType.DELETE.name, createdAt = 1500L, payload = null, hlcTimestamp = hlcDelA.toString())
            val delOpB = JournalOperationDto(changeId = "delB", entityType = EntityType.NOTE.name, entityId = noteUuid, operationType = OperationType.DELETE.name, createdAt = 1600L, payload = null, hlcTimestamp = hlcDelB.toString())

            fixtureA.conflictResolver.resolveAndApply(delOpA, hlcDelA, makeJournal(devA, 1L, listOf(delOpA)))
            fixtureA.conflictResolver.resolveAndApply(delOpB, hlcDelB, makeJournal(devB, 1L, listOf(delOpB)))

            val tombstone = fixtureA.tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid)
            assertNotNull(tombstone)
            assertEquals(hlcDelB.toString(), tombstone?.hlcTimestamp)
        }

    @Test
    fun testScenarioD_TombstoneSafeRetentionPolicy() =
        runBlocking {
            val tombstone =
                SyncTombstoneEntity(
                    entityType = EntityType.NOTE.name,
                    entityId = "note-1",
                    deletedAt = 1000L,
                    hlcTimestamp = "1000:0:devA",
                    deviceId = devA,
                )

            // 1. Retention period not elapsed: cannot prune
            assertFalse(
                fixtureA.conflictResolver.canSafelyPruneTombstone(
                    tombstone = tombstone,
                    activeWatermarks = listOf(SyncWatermarkEntity(deviceId = devB, highestAppliedSeq = 5L)),
                    now = 1000L + 1000L, // only 1 second later
                    minRetentionMillis = 30_000L,
                ),
            )

            // 2. Active devices have zero watermark (never synced): cannot prune
            assertFalse(
                fixtureA.conflictResolver.canSafelyPruneTombstone(
                    tombstone = tombstone,
                    activeWatermarks = listOf(SyncWatermarkEntity(deviceId = devB, highestAppliedSeq = 0L)),
                    now = 1000L + 50_000L,
                    minRetentionMillis = 30_000L,
                ),
            )

            // 3. Retention period elapsed AND active devices advanced sequence: can prune
            assertTrue(
                fixtureA.conflictResolver.canSafelyPruneTombstone(
                    tombstone = tombstone,
                    activeWatermarks = listOf(SyncWatermarkEntity(deviceId = devB, highestAppliedSeq = 5L)),
                    now = 1000L + 50_000L,
                    minRetentionMillis = 30_000L,
                ),
            )
        }

    // ==========================================
    // SECTION 15.E: CHECKLIST CONFLICT RESOLUTION
    // ==========================================

    @Test
    fun testScenarioE_ChecklistConcurrentAdditions_BothPreserved() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val parentNote = makeNote(id = 10L, uuid = noteUuid, title = "Checklist Note", body = "", createdAt = 1000L, updatedAt = 1000L)
            fixtureA.noteDao.insert(parentNote)

            val itemAUuid = UUID.randomUUID().toString()
            val itemBUuid = UUID.randomUUID().toString()

            val hlcA = Hlc(1500L, 0, devA)
            val opItemA =
                JournalOperationDto(
                    changeId = "op-item-a",
                    entityType = EntityType.CHECKLIST_ITEM.name,
                    entityId = itemAUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(ChecklistItemChangePayload(uuid = itemAUuid, noteUuid = noteUuid, text = "Buy Milk", checked = false, sortOrder = 1.0)),
                    hlcTimestamp = hlcA.toString(),
                )

            val hlcB = Hlc(1600L, 0, devB)
            val opItemB =
                JournalOperationDto(
                    changeId = "op-item-b",
                    entityType = EntityType.CHECKLIST_ITEM.name,
                    entityId = itemBUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1600L,
                    payload = json.encodeToString(ChecklistItemChangePayload(uuid = itemBUuid, noteUuid = noteUuid, text = "Buy Bread", checked = false, sortOrder = 2.0)),
                    hlcTimestamp = hlcB.toString(),
                )

            fixtureA.conflictResolver.resolveAndApply(opItemA, hlcA, makeJournal(devA, 1L, listOf(opItemA)))
            fixtureA.conflictResolver.resolveAndApply(opItemB, hlcB, makeJournal(devB, 1L, listOf(opItemB)))

            // Both items must exist under the parent note with stable IDs
            val itemA = fixtureA.itemDao.getByUuid(itemAUuid)
            val itemB = fixtureA.itemDao.getByUuid(itemBUuid)
            assertNotNull(itemA)
            assertNotNull(itemB)
            assertEquals("Buy Milk", itemA?.text)
            assertEquals("Buy Bread", itemB?.text)
        }

    // ==========================================
    // SECTION 15.F: DELIVERY ORDER INDEPENDENCE
    // ==========================================

    @Test
    fun testScenarioF_DeliveryOrderIndependence_Convergence() =
        runBlocking {
            val note1Uuid = UUID.randomUUID().toString()
            val note2Uuid = UUID.randomUUID().toString()

            val hlc1 = Hlc(1000L, 0, "node-1")
            val hlc2 = Hlc(2000L, 0, "node-2")

            fun createOp(
                uuid: String,
                title: String,
                hlc: Hlc,
            ) = JournalOperationDto(
                changeId = "op-$title-$uuid",
                entityType = EntityType.NOTE.name,
                entityId = uuid,
                operationType = OperationType.UPSERT.name,
                createdAt = hlc.physicalTime,
                payload = json.encodeToString(NoteChangePayload(uuid = uuid, kind = "NOTE", title = title, body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = hlc.physicalTime)),
                hlcTimestamp = hlc.toString(),
            )

            val opA1 = createOp(note1Uuid, "Title A", hlc1)
            val opB1 = createOp(note1Uuid, "Title B", hlc2)

            val opA2 = createOp(note2Uuid, "Title A", hlc1)
            val opB2 = createOp(note2Uuid, "Title B", hlc2)

            // Note 1 receives Op A then Op B
            fixtureA.conflictResolver.resolveAndApply(opA1, hlc1, makeJournal("node-1", 1L, listOf(opA1)))
            fixtureA.conflictResolver.resolveAndApply(opB1, hlc2, makeJournal("node-2", 1L, listOf(opB1)))

            // Note 2 receives Op B then Op A
            fixtureA.conflictResolver.resolveAndApply(opB2, hlc2, makeJournal("node-2", 1L, listOf(opB2)))
            fixtureA.conflictResolver.resolveAndApply(opA2, hlc1, makeJournal("node-1", 1L, listOf(opA2)))

            val final1 = fixtureA.noteDao.getNoteEntityByUuid(note1Uuid)
            val final2 = fixtureA.noteDao.getNoteEntityByUuid(note2Uuid)

            // Both must converge to "Title B" (higher HLC)
            assertEquals("Title B", final1?.title)
            assertEquals("Title B", final2?.title)
        }

    // ==========================================
    // SECTION 15.G: IDEMPOTENCY
    // ==========================================

    @Test
    fun testScenarioG_Idempotency_DuplicateOperationsSafe() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc = Hlc(1000L, 0, devA)
            val op =
                JournalOperationDto(
                    changeId = "op-idempotent",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Idempotent Title", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
                    hlcTimestamp = hlc.toString(),
                )

            val journal = makeJournal(devA, 1L, listOf(op))

            // Apply once
            fixtureA.conflictResolver.resolveAndApply(op, hlc, journal)
            assertEquals("Idempotent Title", fixtureA.noteDao.getNoteEntityByUuid(noteUuid)?.title)

            // Apply twice: completely idempotent
            fixtureA.conflictResolver.resolveAndApply(op, hlc, journal)
            assertEquals("Idempotent Title", fixtureA.noteDao.getNoteEntityByUuid(noteUuid)?.title)
        }

    // ==========================================
    // SECTION 15.H: SECURITY AND ERROR REJECTION
    // ==========================================

    @Test(expected = UnsupportedOperationException::class)
    fun testScenarioH_UnsupportedSchemaVersion_Rejected() =
        runBlocking {
            val op =
                JournalOperationDto(
                    changeId = "op-unsupported",
                    entityType = EntityType.NOTE.name,
                    entityId = "note-1",
                    operationType = OperationType.UPSERT.name,
                    schemaVersion = 99, // Incompatible
                    createdAt = 1000L,
                    payload = null,
                )
            fixtureA.conflictResolver.resolveAndApply(op, Hlc(1000L, 0, devA), makeJournal(devA, 1L, listOf(op)))
        }

    @Test(expected = MalformedPathException::class)
    fun testScenarioH_PathTraversalAttachment_Rejected() =
        runBlocking {
            val op =
                JournalOperationDto(
                    changeId = "op-traversal",
                    entityType = EntityType.ATTACHMENT_METADATA.name,
                    entityId = "att-1",
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload = json.encodeToString(haddad.three.notas.data.sync.change.AttachmentChangePayload(uuid = "att-1", noteUuid = "note-1", uri = "../../../etc/passwd", displayName = "../secret")),
                )
            fixtureA.conflictResolver.resolveAndApply(op, Hlc(1000L, 0, devA), makeJournal(devA, 1L, listOf(op)))
        }

    @Test
    fun testScenarioH_DeviceSpecificPreferencesFiltered() =
        runBlocking {
            val op =
                JournalOperationDto(
                    changeId = "op-pref-bio",
                    entityType = EntityType.USER_PREFERENCE.name,
                    entityId = "device_biometrics",
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload = json.encodeToString(PreferenceChangePayload(key = "device_biometrics", settingsJson = "{\"enabled\":true}", updatedAt = 1000L)),
                )
            fixtureA.conflictResolver.resolveAndApply(op, Hlc(1000L, 0, devA), makeJournal(devA, 1L, listOf(op)))

            // Must NOT be stored in sync conflict metadata
            assertNull(fixtureA.conflictMetadataDao.getFieldMetadata(EntityType.USER_PREFERENCE.name, "device_biometrics", "settings"))
        }

    // ==========================================
    // CONFLICT RESOLUTION ACTIONS VERIFICATION
    // ==========================================

    @Test
    fun testConflictResolution_KeepBothAction() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val note = makeNote(id = 1L, uuid = noteUuid, title = "Original Title", body = "Original Body", createdAt = 1000L, updatedAt = 1000L)
            fixtureA.noteDao.insert(note)

            val conflict =
                SyncConflictRecordEntity(
                    id = 1L,
                    conflictId = "conflict-123",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    fieldName = "title",
                    winningValue = "Original Title",
                    losingValue = "Remote Alternative Title",
                    winningHlc = "1000:0:devA",
                    losingHlc = "1000:0:devB",
                    winningDeviceId = devA,
                    losingDeviceId = devB,
                    detectedAt = 1000L,
                    resolved = false,
                )
            fixtureA.conflictRecordDao.insert(conflict)

            // User clicks "Keep Both"
            val result = fixtureA.resolutionManager.keepBoth("conflict-123")
            assertTrue(result is ConflictResolutionResult.Success)

            val copyUuid = (result as ConflictResolutionResult.Success).affectedNoteUuid
            assertNotNull(copyUuid)

            val original = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)
            val copy = fixtureA.noteDao.getNoteEntityByUuid(copyUuid!!)

            assertNotNull(original)
            assertNotNull(copy)
            assertEquals("Original Title", original?.title)
            assertEquals("Remote Alternative Title", copy?.title)

            // Conflict marked resolved
            assertTrue(fixtureA.conflictRecordDao.getByConflictId("conflict-123")!!.resolved)
        }

    @Test
    fun testConflictResolution_MergeAction() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val note = makeNote(id = 1L, uuid = noteUuid, title = "Title A", body = "Body A", createdAt = 1000L, updatedAt = 1000L)
            fixtureA.noteDao.insert(note)

            val conflict =
                SyncConflictRecordEntity(
                    id = 2L,
                    conflictId = "conflict-merge-1",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    fieldName = "title",
                    winningValue = "Title A",
                    losingValue = "Title B",
                    winningHlc = "1000:0:devA",
                    losingHlc = "1000:0:devB",
                    winningDeviceId = devA,
                    losingDeviceId = devB,
                    detectedAt = 1000L,
                    resolved = false,
                )
            fixtureA.conflictRecordDao.insert(conflict)

            val result = fixtureA.resolutionManager.merge("conflict-merge-1", "Merged Title", "Merged Body")
            assertTrue(result is ConflictResolutionResult.Success)

            val updated = fixtureA.noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Merged Title", updated?.title)
            assertEquals("Merged Body", updated?.body)
            assertTrue(fixtureA.conflictRecordDao.getByConflictId("conflict-merge-1")!!.resolved)
        }

    @Test
    fun testConflictPresentationFlows() =
        runBlocking {
            val conflict =
                SyncConflictRecordEntity(
                    id = 10L,
                    conflictId = "conf-flow-1",
                    entityType = EntityType.NOTE.name,
                    entityId = "note-abc",
                    fieldName = "title",
                    winningValue = "Winner",
                    losingValue = "Loser",
                    winningHlc = "2000:0:devB",
                    losingHlc = "1500:0:devA",
                    winningDeviceId = devB,
                    losingDeviceId = devA,
                    detectedAt = 2000L,
                    resolved = false,
                )
            fixtureA.conflictRecordDao.insert(conflict)

            val unresolvedList = fixtureA.conflictResolver.getUnresolvedConflictsFlow().first()
            assertEquals(1, unresolvedList.size)
            assertEquals("conf-flow-1", unresolvedList.first().conflictId)
            assertEquals(ConflictType.CONCURRENT_TITLE_EDIT, unresolvedList.first().conflictType)

            val summary = fixtureA.conflictResolver.getConflictSummary()
            assertEquals(1, summary.unresolvedCount)
        }

    // --- Fake DAOs ---

    class FakeNoteDao : haddad.three.notas.data.UuidBaseFakeNoteDao()

    class FakeChecklistItemDao : haddad.three.notas.data.UuidBaseFakeChecklistItemDao()

    class FakeShoulderDao : haddad.three.notas.data.UuidBaseFakeShoulderDao()

    class FakeAttachmentDao : haddad.three.notas.data.UuidBaseFakeAttachmentDao()

    class FakeSyncConflictMetadataDao : SyncConflictMetadataDao {
        val metadata = mutableMapOf<Triple<String, String, String>, SyncConflictMetadataEntity>()

        override suspend fun insertOrUpdate(metadata: SyncConflictMetadataEntity) {
            this.metadata[Triple(metadata.entityType, metadata.entityId, metadata.fieldName)] = metadata
        }

        override suspend fun getFieldMetadata(
            entityType: String,
            entityId: String,
            fieldName: String,
        ): SyncConflictMetadataEntity? = metadata[Triple(entityType, entityId, fieldName)]

        override suspend fun getAllFieldsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictMetadataEntity> = metadata.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun deleteForEntity(
            entityType: String,
            entityId: String,
        ): Int {
            val keysToRemove = metadata.keys.filter { it.first == entityType && it.second == entityId }
            keysToRemove.forEach { metadata.remove(it) }
            return keysToRemove.size
        }

        override suspend fun clearAll() {
            metadata.clear()
        }
    }

    class FakeSyncTombstoneDao : SyncTombstoneDao {
        val tombstones = mutableMapOf<Pair<String, String>, SyncTombstoneEntity>()

        override suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity) {
            tombstones[Pair(tombstone.entityType, tombstone.entityId)] = tombstone
        }

        override suspend fun getTombstone(
            entityType: String,
            entityId: String,
        ): SyncTombstoneEntity? = tombstones[Pair(entityType, entityId)]

        override suspend fun deleteTombstone(
            entityType: String,
            entityId: String,
        ): Int = if (tombstones.remove(Pair(entityType, entityId)) != null) 1 else 0

        override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = tombstones.values.toList()

        override suspend fun clearAll() {
            tombstones.clear()
        }
    }

    class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            if (records.any { it.conflictId == record.conflictId }) {
                return -1L
            }
            records.add(record)
            return records.size.toLong()
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? = records.find { it.conflictId == conflictId }

        override suspend fun getConflictsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictRecordEntity> = records.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> = records.filter { !it.resolved }

        override fun getUnresolvedConflictsFlow(): kotlinx.coroutines.flow.Flow<List<SyncConflictRecordEntity>> =
            kotlinx.coroutines.flow.flowOf(records.filter { !it.resolved })

        override suspend fun getUnresolvedConflictCount(): Int = records.count { !it.resolved }

        override fun getUnresolvedConflictCountFlow(): kotlinx.coroutines.flow.Flow<Int> =
            kotlinx.coroutines.flow.flowOf(records.count { !it.resolved })

        override suspend fun markResolved(id: Long): Int {
            val idx = records.indexOfFirst { it.id == id }
            if (idx >= 0) {
                records[idx] = records[idx].copy(resolved = true)
                return 1
            }
            return 0
        }

        override suspend fun markResolvedByConflictId(conflictId: String): Int {
            val idx = records.indexOfFirst { it.conflictId == conflictId }
            if (idx >= 0) {
                records[idx] = records[idx].copy(resolved = true)
                return 1
            }
            return 0
        }

        override suspend fun getAllConflictRecords(): List<SyncConflictRecordEntity> = records.toList()

        override fun getAllConflictRecordsFlow(): kotlinx.coroutines.flow.Flow<List<SyncConflictRecordEntity>> =
            kotlinx.coroutines.flow.flowOf(records.toList())

        override suspend fun clearAll() {
            records.clear()
        }
    }

    class FakePendingChangeDao : PendingChangeDao {
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

        override suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity> = changes.values.filter { it.status == "PENDING" }.take(limit)

        override suspend fun getAllPendingChanges(): List<PendingChangeEntity> = changes.values.filter { it.status == "PENDING" }

        override fun observePendingChanges(): kotlinx.coroutines.flow.Flow<List<PendingChangeEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun countPendingChanges(): Int = changes.values.count { it.status == "PENDING" }

        override suspend fun getChangesForEntity(
            entityType: String,
            entityId: String,
        ): List<PendingChangeEntity> = changes.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getChangesForJournal(journalId: String): List<PendingChangeEntity> = changes.values.filter { it.journalId == journalId }

        override suspend fun markChangesJournaled(
            changeIds: Collection<String>,
            journalId: String,
        ): Int {
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

        override suspend fun deleteChangesForEntity(
            entityType: String,
            entityId: String,
        ): Int {
            val toRemove = changes.values.filter { it.entityType == entityType && it.entityId == entityId }.map { it.changeId }
            return deleteByIds(toRemove)
        }

        override suspend fun deleteSynced(): Int {
            val toRemove = changes.values.filter { it.status == "SYNCED" }.map { it.changeId }
            return deleteByIds(toRemove)
        }

        override suspend fun clearAll() {
            changes.clear()
        }
    }

    class FakeSyncWatermarkDao : SyncWatermarkDao {
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
            watermarks[deviceId] =
                SyncWatermarkEntity(
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

        override suspend fun updateAppliedSequence(
            deviceId: String,
            appliedSeq: Long,
            timestamp: Long,
        ): Int {
            val existing = watermarks[deviceId] ?: SyncWatermarkEntity(deviceId = deviceId)
            watermarks[deviceId] = existing.copy(highestAppliedSeq = appliedSeq, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteForDevice(deviceId: String): Int = if (watermarks.remove(deviceId) != null) 1 else 0

        override suspend fun clearAll() {
            watermarks.clear()
        }
    }

    class FakeSyncAppliedOperationDao : SyncAppliedOperationDao {
        val operations = mutableMapOf<String, SyncAppliedOperationEntity>()

        override suspend fun insert(operation: SyncAppliedOperationEntity): Long {
            operations[operation.changeId] = operation
            return 1L
        }

        override suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity? = operations[changeId]

        override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> = operations.values.filter { it.journalId == journalId }

        override suspend fun isApplied(changeId: String): Boolean = operations.containsKey(changeId)

        override suspend fun clearAll() {
            operations.clear()
        }
    }
}
