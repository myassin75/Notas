package haddad.three.notas.data.sync.journal

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.CryptoStorage
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.KeyGenerator

/**
 * Regression tests for TASK F02: Crash-Safe Journal Sequence Allocation.
 *
 * Verifies:
 * 1. Monotonic sequential journal allocation without gaps.
 * 2. Disk failures during encryption / writing do NOT permanently consume sequences.
 * 3. Encryption failures do NOT consume sequences.
 * 4. Process death between disk flush and Room commit forward-reconciles cleanly.
 * 5. Process death with a corrupted / partial file reclaims the sequence without gap.
 * 6. Stale CREATED journals without disk files roll back and reclaim the sequence.
 * 7. Immutable journal identity and changeId bindings.
 */
class CrashSafeJournalSequenceAllocationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeFilesDir: File
    private lateinit var fakeContext: Context
    private lateinit var pendingChangeDao: TestPendingChangeDao
    private lateinit var sequenceDao: TestSyncSequenceDao
    private lateinit var journalDao: TestSyncJournalDao
    private lateinit var mekManager: MekManager
    private lateinit var journalWriter: SyncJournalWriter
    private lateinit var journalRepository: SyncJournalRepository

    private val deviceId = "test_device_alpha"

    @Before
    fun setUp() {
        fakeFilesDir = tempFolder.newFolder("app_files")
        val baseContext = mock(Context::class.java)
        fakeContext = object : ContextWrapper(baseContext) {
            override fun getFilesDir(): File = fakeFilesDir
        }

        pendingChangeDao = TestPendingChangeDao()
        sequenceDao = TestSyncSequenceDao()
        journalDao = TestSyncJournalDao()

        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val masterKey = keyGen.generateKey()
        val keyStore = SoftwareKeyStoreManager()
        val cryptoStorage = InMemoryCryptoStorage().apply {
            setLocalWrappedMek(keyStore.wrapKey(masterKey, 1), 1)
        }
        mekManager = MekManager(keyStore, cryptoStorage)

        journalWriter = SyncJournalWriter(
            context = fakeContext,
            database = null,
            pendingChangeDao = pendingChangeDao,
            sequenceDao = sequenceDao,
            journalDao = journalDao,
            mekManager = mekManager,
            deviceIdProvider = { deviceId },
        )

        journalRepository = SyncJournalRepository(
            context = fakeContext,
            database = null,
            journalDao = journalDao,
            sequenceDao = sequenceDao,
            pendingChangeDao = pendingChangeDao,
            mekManager = mekManager,
        )
    }

    private fun seedChange(id: String, payload: String = "payload_$id") {
        pendingChangeDao.insert(
            PendingChangeEntity(
                changeId = id,
                entityType = "NOTE",
                entityId = "entity_$id",
                operationType = "UPSERT",
                schemaVersion = 1,
                createdAt = System.currentTimeMillis(),
                payload = payload,
            )
        )
    }

    // -------------------------------------------------------------------------
    // 1. Monotonic Allocation
    // -------------------------------------------------------------------------

    @Test
    fun testNormalSequentialJournalAllocationIncrementsMonotonically() = runBlocking {
        seedChange("change_1")
        val res1 = journalWriter.createNextJournal()
        assertTrue(res1 is JournalCreationResult.Success)
        val j1 = (res1 as JournalCreationResult.Success).journal
        assertEquals(1L, j1.sequence)
        assertEquals(1L, sequenceDao.getLastAllocatedSequence(deviceId))

        seedChange("change_2")
        val res2 = journalWriter.createNextJournal()
        assertTrue(res2 is JournalCreationResult.Success)
        val j2 = (res2 as JournalCreationResult.Success).journal
        assertEquals(2L, j2.sequence)
        assertEquals(2L, sequenceDao.getLastAllocatedSequence(deviceId))

        seedChange("change_3")
        val res3 = journalWriter.createNextJournal()
        assertTrue(res3 is JournalCreationResult.Success)
        val j3 = (res3 as JournalCreationResult.Success).journal
        assertEquals(3L, j3.sequence)
        assertEquals(3L, sequenceDao.getLastAllocatedSequence(deviceId))
    }

    // -------------------------------------------------------------------------
    // 2. Disk Failure Does NOT Consume Sequence
    // -------------------------------------------------------------------------

    @Test
    fun testDiskFailureDuringCreationDoesNotConsumeSequence() = runBlocking {
        seedChange("change_fail_disk")

        // Point filesDir to a non-writable / impossible path to simulate disk failure
        val unwriteableContext = object : ContextWrapper(fakeContext) {
            override fun getFilesDir(): File {
                val nonDir = File(fakeFilesDir, "not_a_dir.txt")
                nonDir.createNewFile()
                return nonDir // Returning a file as a directory forces FileOutputStream to fail with IOException
            }
        }

        val failingWriter = SyncJournalWriter(
            context = unwriteableContext,
            database = null,
            pendingChangeDao = pendingChangeDao,
            sequenceDao = sequenceDao,
            journalDao = journalDao,
            mekManager = mekManager,
            deviceIdProvider = { deviceId },
        )

        val result = failingWriter.createNextJournal()
        assertTrue("Creation must fail cleanly on disk error", result is JournalCreationResult.Error)

        // Verify sequence was NOT consumed!
        val lastSeq = sequenceDao.getLastAllocatedSequence(deviceId)
        assertEquals("Sequence must remain unallocated after disk failure", null, lastSeq)

        // Retry with valid context: must allocate sequence 1 without gap!
        val retryResult = journalWriter.createNextJournal()
        assertTrue(retryResult is JournalCreationResult.Success)
        val successfulJournal = (retryResult as JournalCreationResult.Success).journal
        assertEquals(1L, successfulJournal.sequence)
        assertEquals(1L, sequenceDao.getLastAllocatedSequence(deviceId))
    }

    // -------------------------------------------------------------------------
    // 3. Encryption Failure Does NOT Consume Sequence
    // -------------------------------------------------------------------------

    @Test
    fun testEncryptionFailureDoesNotConsumeSequence() = runBlocking {
        seedChange("change_fail_enc")

        // Create writer with uninitialized MEK
        val emptyStorage = InMemoryCryptoStorage()
        val emptyMekManager = MekManager(SoftwareKeyStoreManager(), emptyStorage)

        val failingWriter = SyncJournalWriter(
            context = fakeContext,
            database = null,
            pendingChangeDao = pendingChangeDao,
            sequenceDao = sequenceDao,
            journalDao = journalDao,
            mekManager = emptyMekManager,
            deviceIdProvider = { deviceId },
        )

        val result = failingWriter.createNextJournal()
        assertTrue(result is JournalCreationResult.Error)

        // Verify sequence was NOT consumed
        assertEquals(null, sequenceDao.getLastAllocatedSequence(deviceId))

        // Retry with valid MEK
        val retryResult = journalWriter.createNextJournal()
        assertTrue(retryResult is JournalCreationResult.Success)
        assertEquals(1L, (retryResult as JournalCreationResult.Success).journal.sequence)
    }

    // -------------------------------------------------------------------------
    // 4. Process Death Between Disk Write & Room Commit Reconciles Cleanly
    // -------------------------------------------------------------------------

    @Test
    fun testProcessDeathBetweenDiskWriteAndRoomCommitReconcilesCleanly() = runBlocking {
        seedChange("change_staged")

        // Simulate crash right after file write:
        // Journal 1 was written to disk, sequence was allocated in sequenceDao,
        // but process was killed before Room journalDao.insert completed.
        val targetSequence = 1L
        val journalId = "staged_journal_uuid_1"
        val deviceDir = File(fakeFilesDir, "sync_journals/$deviceId").apply { mkdirs() }
        val journalFile = File(deviceDir, "${targetSequence}_$journalId.journal")

        val journalDto = SyncJournalDto(
            formatVersion = SyncJournalDto.CURRENT_FORMAT_VERSION,
            journalId = journalId,
            deviceId = deviceId,
            sequence = targetSequence,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            operations = listOf(
                JournalOperationDto(
                    changeId = "change_staged",
                    entityType = "NOTE",
                    entityId = "entity_staged",
                    operationType = "UPSERT",
                    schemaVersion = 1,
                    createdAt = System.currentTimeMillis(),
                    payload = "test payload",
                )
            ),
        )

        val jsonBytes = kotlinx.serialization.json.Json.encodeToString(
            SyncJournalDto.serializer(),
            journalDto,
        ).toByteArray(Charsets.UTF_8)

        val aad = SyncJournalWriter.buildAad(1, journalId, deviceId, targetSequence)
        val encryptedBytes = EncryptedContainer.encrypt(jsonBytes, mekManager.getActiveMasterKey()!!, aad = aad)
        FileOutputStream(journalFile).use { it.write(encryptedBytes) }

        // Simulate sequence counter advanced by aborted run
        sequenceDao.setSequence(deviceId, targetSequence)
        assertEquals(1L, sequenceDao.getLastAllocatedSequence(deviceId))
        assertEquals(null, journalDao.getMaxCommittedSequence(deviceId))

        // App reboots: Next creation reconciles the staged file
        seedChange("change_next")
        val res = journalWriter.createNextJournal()
        assertTrue(res is JournalCreationResult.Success)

        // The staged journal was recovered into journalDao as sequence 1!
        val recovered = journalDao.getByDeviceAndSequence(deviceId, 1L)
        assertNotNull("Staged journal must be forward-recovered", recovered)
        assertEquals(JournalStatus.READY_TO_UPLOAD.name, recovered!!.status)

        // The new journal cleanly received sequence 2!
        val newJournal = (res as JournalCreationResult.Success).journal
        assertEquals(2L, newJournal.sequence)
        assertEquals(2L, sequenceDao.getLastAllocatedSequence(deviceId))
    }

    // -------------------------------------------------------------------------
    // 5. Process Death with Corrupted File Reclaims Sequence Without Gap
    // -------------------------------------------------------------------------

    @Test
    fun testProcessDeathWithCorruptedDiskFileReclaimsSequenceWithoutGap() = runBlocking {
        seedChange("change_corrupted")

        // Simulate crash with partial / truncated disk file
        val targetSequence = 1L
        val journalId = "corrupted_journal_uuid"
        val deviceDir = File(fakeFilesDir, "sync_journals/$deviceId").apply { mkdirs() }
        val journalFile = File(deviceDir, "${targetSequence}_$journalId.journal")
        journalFile.writeBytes("garbage truncated bytes".toByteArray())

        // Sequence counter was set to 1 before crash
        sequenceDao.setSequence(deviceId, targetSequence)

        // Next journal creation reconciles: detects corrupted file, deletes it,
        // and RECLAIMS sequence 1!
        val res = journalWriter.createNextJournal()
        assertTrue(res is JournalCreationResult.Success)

        val createdJournal = (res as JournalCreationResult.Success).journal
        assertEquals("Sequence 1 must be reclaimed and used cleanly", 1L, createdJournal.sequence)
        assertEquals(1L, sequenceDao.getLastAllocatedSequence(deviceId))
    }

    // -------------------------------------------------------------------------
    // 6. Stale CREATED Journal Without File Rolls Back & Reclaims Sequence
    // -------------------------------------------------------------------------

    @Test
    fun testStaleCreatedJournalWithoutFileRollsBackAndReclaimsSequence() = runBlocking {
        seedChange("change_stale")

        // Seed a journal entity with status CREATED pointing to a non-existent file
        val staleEntity = SyncJournalEntity(
            journalId = "stale_id",
            deviceId = deviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = File(fakeFilesDir, "non_existent.journal").absolutePath,
            encryptedSizeBytes = 100L,
            status = JournalStatus.CREATED.name,
        )
        journalDao.insert(staleEntity)
        sequenceDao.setSequence(deviceId, 1L)

        // Run repository recovery
        val recoveredCount = journalRepository.recoverInterruptedJournals(deviceId)
        assertEquals(1, recoveredCount)

        // Stale record must be removed
        assertNull(journalDao.getById("stale_id"))

        // Sequence counter must be reset to 0
        assertEquals(0L, sequenceDao.getLastAllocatedSequence(deviceId))

        // Next journal creation allocates sequence 1 without gap
        val res = journalWriter.createNextJournal()
        assertTrue(res is JournalCreationResult.Success)
        assertEquals(1L, (res as JournalCreationResult.Success).journal.sequence)
    }

    // -------------------------------------------------------------------------
    // 7. Immutable Journal Identity & ChangeId Bindings
    // -------------------------------------------------------------------------

    @Test
    fun testImmutableJournalIdentityAndChangeIdBindings() = runBlocking {
        seedChange("change_imm_1", "payload_1")
        seedChange("change_imm_2", "payload_2")

        val result = journalWriter.createNextJournal()
        assertTrue(result is JournalCreationResult.Success)
        val success = result as JournalCreationResult.Success

        // Decrypt and verify journal contents
        val decryptedDto = journalRepository.decryptJournal(success.journal)
        assertEquals(success.journal.journalId, decryptedDto.journalId)
        assertEquals(success.journal.sequence, decryptedDto.sequence)
        assertEquals(deviceId, decryptedDto.deviceId)
        assertEquals(2, decryptedDto.operationCount)

        val changeIds = decryptedDto.operations.map { it.changeId }
        assertTrue(changeIds.contains("change_imm_1"))
        assertTrue(changeIds.contains("change_imm_2"))

        // Attempting to decrypt with tampered AAD must fail cryptographic verification
        val fileBytes = File(success.journal.storagePath).readBytes()
        val badAad = SyncJournalWriter.buildAad(1, success.journal.journalId, deviceId, 999L)
        var tagVerificationFailed = false
        try {
            EncryptedContainer.decrypt(
                containerBytes = fileBytes,
                key = mekManager.getActiveMasterKey()!!,
                expectedAad = badAad,
            )
        } catch (_: Exception) {
            tagVerificationFailed = true
        }
        assertTrue("AES-256-GCM authentication tag must reject tampered metadata", tagVerificationFailed)
    }

    // -------------------------------------------------------------------------
    // In-Memory Test DAOs
    // -------------------------------------------------------------------------

    private class TestPendingChangeDao : PendingChangeDao {
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

        override suspend fun markChangesJournaled(changeIds: List<String>, journalId: String) {
            for (id in changeIds) {
                changes[id]?.let {
                    changes[id] = it.copy(status = "JOURNALED", journalId = journalId)
                }
            }
        }

        override suspend fun resetJournaledChangesToPending(journalId: String) {
            for ((id, change) in changes) {
                if (change.journalId == journalId) {
                    changes[id] = change.copy(status = "PENDING", journalId = null)
                }
            }
        }

        override suspend fun deleteChanges(changeIds: List<String>): Int =
            changeIds.count { changes.remove(it) != null }

        override suspend fun deleteChangesForJournal(journalId: String): Int =
            changes.values.filter { it.journalId == journalId }.count { changes.remove(it.changeId) != null }

        override suspend fun clearAll() {
            changes.clear()
        }
    }

    private class TestSyncSequenceDao : SyncSequenceDao {
        val sequences = mutableMapOf<String, Long>()

        override suspend fun getSequence(deviceId: String): SyncSequenceEntity? {
            val seq = sequences[deviceId] ?: return null
            return SyncSequenceEntity(deviceId, seq, System.currentTimeMillis())
        }

        override suspend fun getLastAllocatedSequence(deviceId: String): Long? = sequences[deviceId]

        override suspend fun insertOrUpdate(entity: SyncSequenceEntity) {
            sequences[entity.deviceId] = entity.lastAllocatedSequence
        }

        override suspend fun setLastAllocatedSequence(deviceId: String, sequence: Long, timestamp: Long): Int {
            sequences[deviceId] = sequence
            return 1
        }

        override suspend fun deleteForDevice(deviceId: String): Int =
            if (sequences.remove(deviceId) != null) 1 else 0

        override suspend fun clearAll() {
            sequences.clear()
        }
    }

    private class TestSyncJournalDao : SyncJournalDao {
        val journals = mutableMapOf<String, SyncJournalEntity>()

        override suspend fun insert(journal: SyncJournalEntity): Long {
            journals[journal.journalId] = journal
            return 1L
        }

        override suspend fun update(journal: SyncJournalEntity) {
            journals[journal.journalId] = journal
        }

        override suspend fun getById(journalId: String): SyncJournalEntity? = journals[journalId]

        override suspend fun getByDeviceAndSequence(deviceId: String, sequence: Long): SyncJournalEntity? =
            journals.values.find { it.deviceId == deviceId && it.sequence == sequence }

        override suspend fun getByStatus(status: String): List<SyncJournalEntity> =
            journals.values.filter { it.status == status }

        override suspend fun getReadyToUpload(): List<SyncJournalEntity> =
            journals.values.filter { it.status == JournalStatus.READY_TO_UPLOAD.name }

        override suspend fun getJournalsForDevice(deviceId: String): List<SyncJournalEntity> =
            journals.values.filter { it.deviceId == deviceId }

        override fun observeAllJournals(): Flow<List<SyncJournalEntity>> = emptyFlow()

        override suspend fun getMaxPublishedSequence(deviceId: String): Long? =
            journals.values.filter { it.deviceId == deviceId }.maxOfOrNull { it.sequence }

        override suspend fun getMaxCommittedSequence(deviceId: String): Long? =
            journals.values.filter { it.deviceId == deviceId && it.status in listOf(JournalStatus.READY_TO_UPLOAD.name, JournalStatus.UPLOADED.name) }.maxOfOrNull { it.sequence }

        override suspend fun getCreatedJournals(deviceId: String): List<SyncJournalEntity> =
            journals.values.filter { it.deviceId == deviceId && it.status == JournalStatus.CREATED.name }

        override suspend fun markUploaded(journalId: String, driveFileId: String, newStatus: String, timestamp: Long): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, driveFileId = driveFileId, updatedAt = timestamp)
            return 1
        }

        override suspend fun getByDriveFileId(driveFileId: String): SyncJournalEntity? =
            journals.values.find { it.driveFileId == driveFileId }

        override suspend fun updateStatus(journalId: String, newStatus: String, timestamp: Long): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, updatedAt = timestamp)
            return 1
        }

        override suspend fun recordUploadFailure(journalId: String, newStatus: String, error: String?, timestamp: Long): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, uploadAttempts = j.uploadAttempts + 1, lastError = error, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteById(journalId: String): Int =
            if (journals.remove(journalId) != null) 1 else 0

        override suspend fun clearAll() {
            journals.clear()
        }
    }
}

