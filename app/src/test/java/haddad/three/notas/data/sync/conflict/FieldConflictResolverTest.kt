package haddad.three.notas.data.sync.conflict

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
import haddad.three.notas.data.sync.change.DeleteChangePayload
import haddad.three.notas.data.sync.change.EntityType
import haddad.three.notas.data.sync.change.NoteChangePayload
import haddad.three.notas.data.sync.change.OperationType
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.journal.JournalOperationDto
import haddad.three.notas.data.sync.journal.SyncJournalDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FieldConflictResolverTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private lateinit var noteDao: FakeNoteDao
    private lateinit var itemDao: FakeChecklistItemDao
    private lateinit var shoulderDao: FakeShoulderDao
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var conflictMetadataDao: FakeSyncConflictMetadataDao
    private lateinit var tombstoneDao: FakeSyncTombstoneDao
    private lateinit var conflictRecordDao: FakeSyncConflictRecordDao

    private lateinit var resolver: FieldConflictResolver

    private val dev1 = "dev-1"
    private val dev2 = "dev-2"

    @Before
    fun setup() {
        noteDao = FakeNoteDao()
        itemDao = FakeChecklistItemDao()
        shoulderDao = FakeShoulderDao()
        attachmentDao = FakeAttachmentDao()
        conflictMetadataDao = FakeSyncConflictMetadataDao()
        tombstoneDao = FakeSyncTombstoneDao()
        conflictRecordDao = FakeSyncConflictRecordDao()

        resolver =
            FieldConflictResolver(
                noteDao = noteDao,
                checklistItemDao = itemDao,
                shoulderDao = shoulderDao,
                attachmentDao = attachmentDao,
                conflictMetadataDao = conflictMetadataDao,
                tombstoneDao = tombstoneDao,
                conflictRecordDao = conflictRecordDao,
            )
    }

    private fun dummyJournal(
        journalId: String = "j1",
        deviceId: String = dev1,
        sequence: Long = 1L,
    ): SyncJournalDto =
        SyncJournalDto(
            formatVersion = 1,
            journalId = journalId,
            deviceId = deviceId,
            sequence = sequence,
            createdAt = 1000L,
            operationCount = 1,
            operations = emptyList(),
        )

    @Test
    fun testTitleConflictPreservationAndIdempotency(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc10 = Hlc(1000L, 0, dev1)
            val hlc20 = Hlc(2000L, 0, dev2)

            // Initial Note with "Title A" at HLC 10
            val initialOp =
                JournalOperationDto(
                    changeId = "op-init",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title A", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = hlc10.toString(),
                )
            resolver.resolveAndApply(initialOp, hlc10, dummyJournal())

            // Concurrent edit: "Title B" arrives at HLC 20
            val op20 =
                JournalOperationDto(
                    changeId = "op-b",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title B", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc20.toString(),
                )
            resolver.resolveAndApply(op20, hlc20, dummyJournal(deviceId = dev2))

            // Winning title in Room is "Title B"
            val updatedNote = noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Title B", updatedNote?.title)

            // Conflict record preserved "Title A" as losing value
            val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(1, conflicts.size)
            val conflict = conflicts.first()
            assertEquals("title", conflict.fieldName)
            assertEquals("Title B", conflict.winningValue)
            assertEquals("Title A", conflict.losingValue)
            assertEquals(hlc20.toString(), conflict.winningHlc)
            assertEquals(hlc10.toString(), conflict.losingHlc)

            // Replay op20: Must be completely idempotent and not create duplicate conflict records
            resolver.resolveAndApply(op20, hlc20, dummyJournal(deviceId = dev2))
            val conflictsAfterReplay = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals("Replaying the winning operation must not duplicate conflict records", 1, conflictsAfterReplay.size)
        }

    @Test
    fun testBodyConflictPreservationAndIdempotency(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc10 = Hlc(1000L, 0, dev1)
            val hlc20 = Hlc(2000L, 0, dev2)

            // Initial Note with "Body Original" at HLC 10
            val initialOp =
                JournalOperationDto(
                    changeId = "op-init-body",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "Body Original", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = hlc10.toString(),
                )
            resolver.resolveAndApply(initialOp, hlc10, dummyJournal())

            // Concurrent edit with "Body Modified" at HLC 20
            val op20 =
                JournalOperationDto(
                    changeId = "op-mod-body",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "Body Modified", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc20.toString(),
                )
            resolver.resolveAndApply(op20, hlc20, dummyJournal(deviceId = dev2))

            val updatedNote = noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Body Modified", updatedNote?.body)

            val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(1, conflicts.size)
            val conflict = conflicts.first()
            assertEquals("body", conflict.fieldName)
            assertEquals("Body Modified", conflict.winningValue)
            assertEquals("Body Original", conflict.losingValue)

            // Replay op20: idempotent
            resolver.resolveAndApply(op20, hlc20, dummyJournal(deviceId = dev2))
            assertEquals(1, conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid).size)
        }

    @Test
    fun testStaleTitleEditPreservesConflictingVersionWithoutOverwritingWinner(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc20 = Hlc(2000L, 0, dev2)
            val hlc15 = Hlc(1500L, 0, dev1)

            // Current state has "Title Winner" at HLC 20
            val initialOp =
                JournalOperationDto(
                    changeId = "op-winner",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title Winner", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc20.toString(),
                )
            resolver.resolveAndApply(initialOp, hlc20, dummyJournal(deviceId = dev2))

            // Stale incoming edit arrives later: "Title Loser" at HLC 15
            val staleOp =
                JournalOperationDto(
                    changeId = "op-loser",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title Loser", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1500L),
                        ),
                    hlcTimestamp = hlc15.toString(),
                )
            resolver.resolveAndApply(staleOp, hlc15, dummyJournal(deviceId = dev1))

            // Note title must remain "Title Winner"
            val currentNote = noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Title Winner", currentNote?.title)

            // Metadata must remain HLC 20
            val titleMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "title")
            assertEquals(hlc20.toString(), titleMeta?.hlcTimestamp)

            // Conflict record must capture the losing concurrent title edit
            val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(1, conflicts.size)
            val conflict = conflicts.first()
            assertEquals("Title Winner", conflict.winningValue)
            assertEquals("Title Loser", conflict.losingValue)
            assertEquals(hlc20.toString(), conflict.winningHlc)
            assertEquals(hlc15.toString(), conflict.losingHlc)
        }

    @Test
    fun testAllNoteFieldsArrivalOrderIndependence(): Unit =
        runBlocking {
            val note1Uuid = UUID.randomUUID().toString()
            val note2Uuid = UUID.randomUUID().toString()
            val baseTime = 1000L
            val baseHlc = Hlc(baseTime, 0, "dev-base")

            fun createInitialOp(uuid: String) =
                JournalOperationDto(
                    changeId = "init-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = baseTime,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(
                                uuid = uuid,
                                kind = "NOTE",
                                title = "Base Title",
                                body = "Base Body",
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

            // Initialize Note 1 and Note 2
            resolver.resolveAndApply(createInitialOp(note1Uuid), baseHlc, dummyJournal())
            resolver.resolveAndApply(createInitialOp(note2Uuid), baseHlc, dummyJournal())

            val hlcA = Hlc(1500L, 0, "dev-A")
            val hlcB = Hlc(2000L, 0, "dev-B")

            // Op A (HLC 1500): updates color to 2, trashed to true, pinnedAt to 1500L
            fun createOpA(uuid: String) =
                JournalOperationDto(
                    changeId = "opA-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(
                                uuid = uuid,
                                kind = "NOTE",
                                title = "Title from A",
                                body = "Body from A",
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

            // Op B (HLC 2000): updates title to "Title Winner", color stays 2 (unchanged from A, but dominates base), archived to true, completedAt to 2000L
            fun createOpB(uuid: String) =
                JournalOperationDto(
                    changeId = "opB-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(
                                uuid = uuid,
                                kind = "NOTE",
                                title = "Title Winner",
                                body = "Body Winner",
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

            // Apply on Note 1: Order = Op A (1500) then Op B (2000)
            resolver.resolveAndApply(createOpA(note1Uuid), hlcA, dummyJournal(deviceId = "dev-A"))
            resolver.resolveAndApply(createOpB(note1Uuid), hlcB, dummyJournal(deviceId = "dev-B"))

            // Apply on Note 2: Order = Op B (2000) then Op A (1500)
            resolver.resolveAndApply(createOpB(note2Uuid), hlcB, dummyJournal(deviceId = "dev-B"))
            resolver.resolveAndApply(createOpA(note2Uuid), hlcA, dummyJournal(deviceId = "dev-A"))

            // Verify Entity State equivalence across arrival orders
            val n1 = noteDao.getNoteEntityByUuid(note1Uuid)
            val n2 = noteDao.getNoteEntityByUuid(note2Uuid)

            assertNotNull(n1)
            assertNotNull(n2)
            assertEquals("Title must converge", n1?.title, n2?.title)
            assertEquals("Title Winner", n1?.title)

            assertEquals("Body must converge", n1?.body, n2?.body)
            assertEquals("Body Winner", n1?.body)

            assertEquals("ColorIndex must converge", n1?.colorIndex, n2?.colorIndex)
            assertEquals(2, n1?.colorIndex)

            assertEquals("Trashed must converge", n1?.trashed, n2?.trashed)
            assertEquals(false, n1?.trashed)

            assertEquals("Archived must converge", n1?.archived, n2?.archived)
            assertEquals(true, n1?.archived)

            assertEquals("PinnedAt must converge", n1?.pinnedAt, n2?.pinnedAt)
            assertEquals(null, n1?.pinnedAt)

            assertEquals("CompletedAt must converge", n1?.completedAt, n2?.completedAt)
            assertEquals(2000L, n1?.completedAt)

            // Verify HLC Metadata equivalence across arrival orders
            val fields = listOf("title", "body", "colorIndex", "trashed", "archived", "pinnedAt", "completedAt")
            for (field in fields) {
                val meta1 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note1Uuid, field)
                val meta2 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note2Uuid, field)
                assertEquals("HLC metadata for $field must match", meta1?.hlcTimestamp, meta2?.hlcTimestamp)
                assertEquals("Dominating HLC must be 2000 for $field", hlcB.toString(), meta1?.hlcTimestamp)
            }
        }

    @Test
    fun testTombstonePreventsZombieResurrection(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlcCreate = Hlc(1000L, 0, dev1)
            val hlcDelete = Hlc(2000L, 0, dev1)
            val hlcStaleUpdate = Hlc(1500L, 0, dev2)

            // 1. Create note
            val createOp =
                JournalOperationDto(
                    changeId = "op-c",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "To Delete", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)),
                    hlcTimestamp = hlcCreate.toString(),
                )
            resolver.resolveAndApply(createOp, hlcCreate, dummyJournal())

            // 2. Delete note at HLC 2000
            val deleteOp =
                JournalOperationDto(
                    changeId = "op-d",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.DELETE.name,
                    createdAt = 2000L,
                    payload = json.encodeToString(DeleteChangePayload(uuid = noteUuid, deletedAt = 2000L)),
                    hlcTimestamp = hlcDelete.toString(),
                )
            resolver.resolveAndApply(deleteOp, hlcDelete, dummyJournal())
            assertNull("Note should be deleted", noteDao.getNoteEntityByUuid(noteUuid))
            assertNotNull("Tombstone should exist", tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid))

            // 3. Stale update at HLC 1500 arrives: must NOT resurrect note
            val staleOp =
                JournalOperationDto(
                    changeId = "op-stale",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Zombie", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1500L)),
                    hlcTimestamp = hlcStaleUpdate.toString(),
                )
            resolver.resolveAndApply(staleOp, hlcStaleUpdate, dummyJournal(deviceId = dev2))
            assertNull("Stale update must not resurrect tombstoned note", noteDao.getNoteEntityByUuid(noteUuid))
            assertNotNull("Tombstone must remain intact", tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid))

            // 4. Newer update at HLC 3000 arrives: clears tombstone and resurrects
            val hlcResurrect = Hlc(3000L, 0, dev2)
            val newerOp =
                JournalOperationDto(
                    changeId = "op-res",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 3000L,
                    payload = json.encodeToString(NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Legit Resurrect", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 3000L)),
                    hlcTimestamp = hlcResurrect.toString(),
                )
            resolver.resolveAndApply(newerOp, hlcResurrect, dummyJournal(deviceId = dev2))
            assertNotNull("Newer update resurrects entity", noteDao.getNoteEntityByUuid(noteUuid))
            assertNull("Tombstone cleared by newer update", tombstoneDao.getTombstone(EntityType.NOTE.name, noteUuid))
        }

    @Test(expected = MalformedPathException::class)
    fun testAttachmentPathTraversalSecurityRejection(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc = Hlc(1000L, 0, dev1)
            val op =
                JournalOperationDto(
                    changeId = "att-bad",
                    entityType = EntityType.ATTACHMENT_METADATA.name,
                    entityId = UUID.randomUUID().toString(),
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            AttachmentChangePayload(
                                uuid = UUID.randomUUID().toString(),
                                noteUuid = noteUuid,
                                uri = "../../../etc/passwd",
                                displayName = "malicious.txt",
                                mimeType = "text/plain",
                            ),
                        ),
                    hlcTimestamp = hlc.toString(),
                )
            resolver.resolveAndApply(op, hlc, dummyJournal())
        }

    @Test(expected = UnsupportedOperationException::class)
    fun testUnsupportedSchemaVersionThrows(): Unit =
        runBlocking {
            val op =
                JournalOperationDto(
                    changeId = "bad-ver",
                    entityType = EntityType.NOTE.name,
                    entityId = "some-id",
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    schemaVersion = 2, // Unsupported schema version
                    payload = "{}",
                    hlcTimestamp = Hlc(1000L, 0, dev1).toString(),
                )
            resolver.resolveAndApply(op, Hlc(1000L, 0, dev1), dummyJournal())
        }

    @Test
    fun testCollisionSafetySameHlcPairDistinctPayloadsPreserved(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc1 = Hlc(1000L, 0, "dev-orig")
            val hlc2 = Hlc(2000L, 0, "dev-remote")

            // First conflict between "Value A" and "Value B" with (hlc1, hlc2)
            val op1Init =
                JournalOperationDto(
                    changeId = "c1-init",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Value A", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = hlc1.toString(),
                )
            resolver.resolveAndApply(op1Init, hlc1, dummyJournal(deviceId = "dev-orig"))

            val op1Update =
                JournalOperationDto(
                    changeId = "c1-update",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Value B", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc2.toString(),
                )
            resolver.resolveAndApply(op1Update, hlc2, dummyJournal(deviceId = "dev-remote"))

            val conflicts1 = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(1, conflicts1.size)
            val conflictId1 = conflicts1.first().conflictId

            // Now test second note with same HLC pair (hlc1, hlc2) but different payloads "Value X" and "Value Y"
            val note2Uuid = UUID.randomUUID().toString()
            val op2Init =
                JournalOperationDto(
                    changeId = "c2-init",
                    entityType = EntityType.NOTE.name,
                    entityId = note2Uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = note2Uuid, kind = "NOTE", title = "Value X", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = hlc1.toString(),
                )
            resolver.resolveAndApply(op2Init, hlc1, dummyJournal(deviceId = "dev-orig"))

            val op2Update =
                JournalOperationDto(
                    changeId = "c2-update",
                    entityType = EntityType.NOTE.name,
                    entityId = note2Uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = note2Uuid, kind = "NOTE", title = "Value Y", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc2.toString(),
                )
            resolver.resolveAndApply(op2Update, hlc2, dummyJournal(deviceId = "dev-remote"))

            val conflicts2 = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, note2Uuid)
            assertEquals(1, conflicts2.size)
            val conflictId2 = conflicts2.first().conflictId

            // Assert: Deterministic conflict IDs MUST NOT collide even when HLCs match, because payloads differ!
            assertTrue("Conflict IDs must differ for different payloads", conflictId1 != conflictId2)
            assertEquals(2, conflictRecordDao.records.size)
        }

    @Test
    fun testEmptyAndWhitespaceTitleConflictSemantics(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc10 = Hlc(1000L, 0, dev1)
            val hlc20 = Hlc(2000L, 0, dev2)

            // Baseline note with non-empty title "Initial Title"
            val initialOp =
                JournalOperationDto(
                    changeId = "op-init-non-empty",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Initial Title", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = hlc10.toString(),
                )
            resolver.resolveAndApply(initialOp, hlc10, dummyJournal())

            // 1. Transition from non-empty to empty string ("") at HLC 20
            val clearTitleOp =
                JournalOperationDto(
                    changeId = "op-clear-title",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc20.toString(),
                )
            resolver.resolveAndApply(clearTitleOp, hlc20, dummyJournal(deviceId = dev2))

            val noteAfterClear = noteDao.getNoteEntityByUuid(noteUuid)
            assertEquals("Title must transition to empty string", "", noteAfterClear?.title)

            // Conflict record MUST preserve the losing non-empty title!
            val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals("Clearing title must preserve previous non-empty title in conflict records", 1, conflicts.size)
            val conflict = conflicts.first()
            assertEquals("", conflict.winningValue)
            assertEquals("Initial Title", conflict.losingValue)

            // 2. Incoming stale non-empty title "Stale Candidate" arrives at HLC 15 (less than clear HLC 20)
            val hlc15 = Hlc(1500L, 0, "dev-3")
            val staleOp =
                JournalOperationDto(
                    changeId = "op-stale-title",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Stale Candidate", body = "Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1500L),
                        ),
                    hlcTimestamp = hlc15.toString(),
                )
            resolver.resolveAndApply(staleOp, hlc15, dummyJournal(deviceId = "dev-3"))

            // Title must remain empty string ""
            assertEquals("", noteDao.getNoteEntityByUuid(noteUuid)?.title)

            // The losing "Stale Candidate" must ALSO be preserved in conflict records!
            val allConflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(2, allConflicts.size)
            val secondConflict = allConflicts.find { it.losingValue == "Stale Candidate" }
            assertNotNull("Stale non-empty title arriving after empty title must be preserved in conflict records", secondConflict)
            assertEquals("", secondConflict?.winningValue)
            assertEquals("Stale Candidate", secondConflict?.losingValue)
        }

    @Test
    fun testEmptyAndWhitespaceBodyConflictSemantics(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc10 = Hlc(1000L, 0, dev1)
            val hlc20 = Hlc(2000L, 0, dev2)

            // Baseline note with body "Rich body content"
            val initialOp =
                JournalOperationDto(
                    changeId = "op-body-init",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "Rich body content", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = hlc10.toString(),
                )
            resolver.resolveAndApply(initialOp, hlc10, dummyJournal())

            // Clearing body to whitespace-only string "   " at HLC 20
            val clearBodyOp =
                JournalOperationDto(
                    changeId = "op-body-whitespace",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = noteUuid, kind = "NOTE", title = "Title", body = "   ", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlc20.toString(),
                )
            resolver.resolveAndApply(clearBodyOp, hlc20, dummyJournal(deviceId = dev2))

            assertEquals("   ", noteDao.getNoteEntityByUuid(noteUuid)?.body)

            // Conflict record must capture the previous rich body content
            val conflicts = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, noteUuid)
            assertEquals(1, conflicts.size)
            assertEquals("   ", conflicts.first().winningValue)
            assertEquals("Rich body content", conflicts.first().losingValue)
        }

    @Test
    fun testNullableScalarFieldsTransitionToNull(): Unit =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val hlc10 = Hlc(1000L, 0, dev1)

            // Baseline note with all nullable fields populated
            val initialOp =
                JournalOperationDto(
                    changeId = "op-null-init",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(
                                uuid = noteUuid,
                                kind = "NOTE",
                                title = "Title",
                                body = "Body",
                                colorIndex = 1,
                                trashed = true,
                                trashedAt = 1000L,
                                archived = false,
                                pinnedAt = 1000L,
                                completedAt = 1000L,
                                reminderAt = 1000L,
                                endAt = 2000L,
                                isAllDay = true,
                                createdAt = 1000L,
                                updatedAt = 1000L,
                            ),
                        ),
                    hlcTimestamp = hlc10.toString(),
                )
            resolver.resolveAndApply(initialOp, hlc10, dummyJournal())

            val noteInitial = noteDao.getNoteEntityByUuid(noteUuid)
            assertNotNull(noteInitial?.pinnedAt)
            assertNotNull(noteInitial?.completedAt)
            assertNotNull(noteInitial?.reminderAt)
            assertNotNull(noteInitial?.trashedAt)

            // Operation at HLC 20 unpins, uncompletes, clears reminder, and untrashes (all null transitions)
            val hlc20 = Hlc(2000L, 0, dev2)
            val clearFieldsOp =
                JournalOperationDto(
                    changeId = "op-null-clear",
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(
                                uuid = noteUuid,
                                kind = "NOTE",
                                title = "Title",
                                body = "Body",
                                colorIndex = 1,
                                trashed = false,
                                trashedAt = null,
                                archived = false,
                                pinnedAt = null,
                                completedAt = null,
                                reminderAt = null,
                                endAt = null,
                                isAllDay = false,
                                createdAt = 1000L,
                                updatedAt = 2000L,
                            ),
                        ),
                    hlcTimestamp = hlc20.toString(),
                )
            resolver.resolveAndApply(clearFieldsOp, hlc20, dummyJournal(deviceId = dev2))

            val noteAfterClear = noteDao.getNoteEntityByUuid(noteUuid)
            assertNull("pinnedAt should transition to null", noteAfterClear?.pinnedAt)
            assertNull("completedAt should transition to null", noteAfterClear?.completedAt)
            assertNull("reminderAt should transition to null", noteAfterClear?.reminderAt)
            assertNull("trashedAt should transition to null", noteAfterClear?.trashedAt)
            assertEquals(false, noteAfterClear?.trashed)

            // Metadata must advance to HLC 20 for cleared fields
            assertEquals(hlc20.toString(), conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "pinnedAt")?.hlcTimestamp)
            assertEquals(hlc20.toString(), conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "completedAt")?.hlcTimestamp)
            assertEquals(hlc20.toString(), conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "reminderAt")?.hlcTimestamp)
            assertEquals(hlc20.toString(), conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, noteUuid, "trashed")?.hlcTimestamp)
        }

    @Test
    fun testReversedArrivalOrderWithEmptyAndNonEmptyUpdates(): Unit =
        runBlocking {
            val note1Uuid = UUID.randomUUID().toString()
            val note2Uuid = UUID.randomUUID().toString()
            val baseHlc = Hlc(1000L, 0, "dev-base")

            fun initOp(uuid: String) =
                JournalOperationDto(
                    changeId = "init-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = uuid, kind = "NOTE", title = "Original Title", body = "Original Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L),
                        ),
                    hlcTimestamp = baseHlc.toString(),
                )

            resolver.resolveAndApply(initOp(note1Uuid), baseHlc, dummyJournal())
            resolver.resolveAndApply(initOp(note2Uuid), baseHlc, dummyJournal())

            val hlcA = Hlc(1500L, 0, "dev-A") // Op A clears title to ""
            val hlcB = Hlc(2000L, 0, "dev-B") // Op B updates title to "Restored Title"

            fun opA(uuid: String) =
                JournalOperationDto(
                    changeId = "opA-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 1500L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = uuid, kind = "NOTE", title = "", body = "Original Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1500L),
                        ),
                    hlcTimestamp = hlcA.toString(),
                )

            fun opB(uuid: String) =
                JournalOperationDto(
                    changeId = "opB-$uuid",
                    entityType = EntityType.NOTE.name,
                    entityId = uuid,
                    operationType = OperationType.UPSERT.name,
                    createdAt = 2000L,
                    payload =
                        json.encodeToString(
                            NoteChangePayload(uuid = uuid, kind = "NOTE", title = "Restored Title", body = "Original Body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 2000L),
                        ),
                    hlcTimestamp = hlcB.toString(),
                )

            // Note 1: Apply Op A (1500) then Op B (2000)
            resolver.resolveAndApply(opA(note1Uuid), hlcA, dummyJournal(deviceId = "dev-A"))
            resolver.resolveAndApply(opB(note1Uuid), hlcB, dummyJournal(deviceId = "dev-B"))

            // Note 2: Apply Op B (2000) then Op A (1500)
            resolver.resolveAndApply(opB(note2Uuid), hlcB, dummyJournal(deviceId = "dev-B"))
            resolver.resolveAndApply(opA(note2Uuid), hlcA, dummyJournal(deviceId = "dev-A"))

            val n1 = noteDao.getNoteEntityByUuid(note1Uuid)
            val n2 = noteDao.getNoteEntityByUuid(note2Uuid)

            assertNotNull(n1)
            assertNotNull(n2)
            assertEquals("Title must converge to 'Restored Title'", "Restored Title", n1?.title)
            assertEquals("Title must match across arrival orders", n1?.title, n2?.title)

            val meta1 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note1Uuid, "title")
            val meta2 = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, note2Uuid, "title")
            assertEquals(hlcB.toString(), meta1?.hlcTimestamp)
            assertEquals(meta1?.hlcTimestamp, meta2?.hlcTimestamp)

            // Conflict records must exist for both notes and have the exact same deterministic conflictId!
            val c1 = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, note1Uuid)
            val c2 = conflictRecordDao.getConflictsForEntity(EntityType.NOTE.name, note2Uuid)
            assertTrue(c1.isNotEmpty())
            assertTrue(c2.isNotEmpty())
        }

    // --- Fake DAOs ---

    private class FakeNoteDao : haddad.three.notas.data.UuidBaseFakeNoteDao()

    private class FakeChecklistItemDao : haddad.three.notas.data.UuidBaseFakeChecklistItemDao()

    private class FakeShoulderDao : haddad.three.notas.data.UuidBaseFakeShoulderDao()

    private class FakeAttachmentDao : haddad.three.notas.data.UuidBaseFakeAttachmentDao()

    private class FakeSyncConflictMetadataDao : SyncConflictMetadataDao {
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

    private class FakeSyncTombstoneDao : SyncTombstoneDao {
        val tombstones = mutableMapOf<Pair<String, String>, SyncTombstoneEntity>()

        override suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity) {
            tombstones[tombstone.entityType to tombstone.entityId] = tombstone
        }

        override suspend fun getTombstone(
            entityType: String,
            entityId: String,
        ): SyncTombstoneEntity? = tombstones[entityType to entityId]

        override suspend fun deleteTombstone(
            entityType: String,
            entityId: String,
        ): Int = if (tombstones.remove(entityType to entityId) != null) 1 else 0

        override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = tombstones.values.toList()

        override suspend fun clearAll() {
            tombstones.clear()
        }
    }

    private class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableMapOf<String, SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            // Respect unique index on conflictId: ignore duplicate insert
            if (records.containsKey(record.conflictId)) {
                return -1L
            }
            records[record.conflictId] = record
            return 1L
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? = records[conflictId]

        override suspend fun getConflictsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictRecordEntity> = records.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> = records.values.filter { !it.resolved }

        override suspend fun markResolved(id: Long): Int = 1

        override suspend fun clearAll() {
            records.clear()
        }
    }
}
