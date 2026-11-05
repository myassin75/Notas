package haddad.three.notas.data.sync.transport

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveFileListResponse
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.journal.JournalOperationDto
import haddad.three.notas.data.sync.journal.JournalStatus
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalDto
import haddad.three.notas.data.sync.journal.SyncJournalEntity
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class GoogleDriveJournalTransportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = kotlinx.coroutines.Dispatchers.IO
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Fakes
    private lateinit var fakeContext: Context
    private lateinit var fakeJournalDao: FakeSyncJournalDao
    private lateinit var fakePendingChangeDao: FakePendingChangeDao
    private lateinit var fakeInboxDao: FakeSyncInboxDao
    private lateinit var fakeWatermarkDao: FakeSyncWatermarkDao
    private lateinit var fakeAuthDelegate: FakeGoogleDriveAuthDelegate
    private lateinit var fakeDriveApiClient: FakeGoogleDriveApiClient
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var mekManager: MekManager
    private lateinit var masterKey: SecretKey

    // SUT
    private lateinit var transport: GoogleDriveSyncTransport
    private lateinit var journalUploader: JournalUploader
    private lateinit var discoveryService: JournalDiscoveryService
    private lateinit var journalDownloader: JournalDownloader

    private val localDeviceId = "test_device_local_1"
    private val remoteDeviceId = "test_device_remote_2"

    @Before
    fun setUp() {
        val fakeFilesDir = tempFolder.newFolder("files")
        val fakeCacheDir = tempFolder.newFolder("cache")
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = fakeFilesDir
            override fun getCacheDir(): File = fakeCacheDir
        }

        fakeJournalDao = FakeSyncJournalDao()
        fakePendingChangeDao = FakePendingChangeDao()
        fakeInboxDao = FakeSyncInboxDao()
        fakeWatermarkDao = FakeSyncWatermarkDao()
        fakeAuthDelegate = FakeGoogleDriveAuthDelegate()
        fakeDriveApiClient = FakeGoogleDriveApiClient()
        drivePrefs = DriveAuthPreferences(fakeContext)

        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        masterKey = keyGen.generateKey()
        val keyStore = SoftwareKeyStoreManager()
        val storage = InMemoryCryptoStorage().apply {
            setLocalWrappedMek(keyStore.wrapKey(masterKey, 1), 1)
        }
        mekManager = MekManager(keyStore, storage)

        transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = fakeDriveApiClient.asClient(),
            driveAuthDelegate = fakeAuthDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        journalUploader = JournalUploader(
            journalDao = fakeJournalDao,
            pendingChangeDao = fakePendingChangeDao,
            transport = transport,
            database = null,
            ioDispatcher = testDispatcher,
        )

        discoveryService = JournalDiscoveryService(
            transport = transport,
            watermarkDao = fakeWatermarkDao,
            ioDispatcher = testDispatcher,
        )

        journalDownloader = JournalDownloader(
            context = fakeContext,
            transport = transport,
            inboxDao = fakeInboxDao,
            watermarkDao = fakeWatermarkDao,
            mekManager = mekManager,
            database = null,
            ioDispatcher = testDispatcher,
        )
    }

    // =========================================================================
    // Test 1: Successful journal upload
    // =========================================================================
    @Test
    fun testSuccessfulJournalUpload() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val file = createLocalJournalFile(localDeviceId, 1L, journalId, masterKey)
        val hash = transport.computeSha256(file)

        // Seed pending change and journal
        val changeId = UUID.randomUUID().toString()
        fakePendingChangeDao.insert(
            PendingChangeEntity(
                changeId = changeId,
                entityType = "NOTE",
                entityId = "101",
                operationType = "CREATE",
                deviceId = localDeviceId,
                createdAt = System.currentTimeMillis(),
                schemaVersion = 1,
                payload = "{}",
                status = "JOURNALED",
                journalId = journalId,
            )
        )

        val entity = SyncJournalEntity(
            journalId = journalId,
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file.absolutePath,
            encryptedSizeBytes = file.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )
        fakeJournalDao.insert(entity)

        val batchResult = journalUploader.uploadReadyJournals(localDeviceId)
        assertEquals(1, batchResult.totalJournals)
        assertEquals(1, batchResult.successfulJournals)
        assertEquals(0, batchResult.failedJournals)

        // Verify journal entity status and driveFileId in Room DAO
        val updated = fakeJournalDao.getById(journalId)
        assertNotNull(updated)
        assertEquals(JournalStatus.UPLOADED.name, updated?.status)
        assertNotNull(updated?.driveFileId)

        // Verify pending change marked SYNCED
        val change = fakePendingChangeDao.getById(changeId)
        assertEquals("SYNCED", change?.status)

        // Verify Drive received file with valid appProperties
        val uploadedFile = fakeDriveApiClient.files.values.find { it.appProperties["journalId"] == journalId }
        assertNotNull(uploadedFile)
        assertEquals(localDeviceId, uploadedFile?.appProperties?.get("deviceId"))
        assertEquals("1", uploadedFile?.appProperties?.get("sequence"))
        assertEquals(hash, uploadedFile?.appProperties?.get("contentSha256"))
    }

    // =========================================================================
    // Test 2: Successful journal download
    // =========================================================================
    @Test
    fun testSuccessfulJournalDownload() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val fileBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, journalId, masterKey)
        val hash = sha256(fileBytes)

        val driveFileId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = fileBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to System.currentTimeMillis().toString(),
            )
        )

        val discovery = discoveryService.discoverRemoteJournals(localDeviceId)
        assertTrue(discovery is DiscoveryResult.Success)
        val remoteJournals = (discovery as DiscoveryResult.Success).discoveredJournalsByDevice[remoteDeviceId]
        assertEquals(1, remoteJournals?.size)

        val downloadResult = journalDownloader.downloadAndValidateJournals(remoteJournals!!)
        assertEquals(1, downloadResult.downloadedAndValidated)
        assertEquals(0, downloadResult.failed)

        // Check local inbox
        val inboxEntry = fakeInboxDao.getByJournalId(journalId)
        assertNotNull(inboxEntry)
        assertEquals(InboxStatus.VALIDATED, inboxEntry?.status)
        assertEquals(hash, inboxEntry?.contentSha256)

        // Check watermark
        val watermark = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertNotNull(watermark)
        assertEquals(1L, watermark?.highestDiscoveredSeq)
        assertEquals(1L, watermark?.highestDownloadedSeq)
        assertEquals(1L, watermark?.highestValidatedSeq)
    }

    // =========================================================================
    // Test 3: Pagination across multiple pages
    // =========================================================================
    @Test
    fun testPaginationAcrossMultiplePages() = runBlocking {
        // Add 120 journals from remote device (3 pages: 50, 50, 20)
        for (seq in 1L..120L) {
            val jId = "j_seq_$seq"
            val dummyBytes = "enc_$seq".toByteArray()
            fakeDriveApiClient.addFile(
                name = "journal_${remoteDeviceId}_${seq}_$jId.bin",
                bytes = dummyBytes,
                appProperties = mapOf(
                    "appId" to "notas",
                    "type" to "sync_journal",
                    "journalId" to jId,
                    "deviceId" to remoteDeviceId,
                    "sequence" to seq.toString(),
                    "formatVersion" to "1",
                    "contentSha256" to sha256(dummyBytes),
                    "operationCount" to "1",
                    "uploadedAt" to "1000",
                )
            )
        }

        val discovery = discoveryService.discoverRemoteJournals(localDeviceId)
        assertTrue(discovery is DiscoveryResult.Success)
        val allRemote = (discovery as DiscoveryResult.Success).discoveredJournalsByDevice[remoteDeviceId]
        assertEquals(120, allRemote?.size)
        assertEquals(1L, allRemote?.first()?.sequence)
        assertEquals(120L, allRemote?.last()?.sequence)
    }

    // =========================================================================
    // Test 4: Empty journal folder
    // =========================================================================
    @Test
    fun testEmptyJournalFolder() = runBlocking {
        fakeDriveApiClient.files.clear()
        val result = discoveryService.discoverRemoteJournals(localDeviceId)
        assertTrue(result is DiscoveryResult.Success)
        assertTrue((result as DiscoveryResult.Success).discoveredJournalsByDevice.isEmpty())
    }

    // =========================================================================
    // Test 5: Interrupted upload recovery (idempotent lookup after failure)
    // =========================================================================
    @Test
    fun testInterruptedUploadRecovery() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val file = createLocalJournalFile(localDeviceId, 1L, journalId, masterKey)
        val hash = transport.computeSha256(file)

        val entity = SyncJournalEntity(
            journalId = journalId,
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file.absolutePath,
            encryptedSizeBytes = file.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )
        fakeJournalDao.insert(entity)

        // Simulate that Drive received and stored the file, but response dropped
        val existingDriveId = fakeDriveApiClient.addFile(
            name = "journal_${localDeviceId}_1_$journalId.bin",
            bytes = file.readBytes(),
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to localDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        // Uploading should find existing file, confirm hash, and succeed without re-uploading
        val uploadResult = journalUploader.uploadJournal(entity)
        assertTrue(uploadResult is JournalUploadResult.Success)
        assertEquals(existingDriveId, (uploadResult as JournalUploadResult.Success).driveFileId)
        assertTrue(uploadResult.alreadyExisted)

        // Only 1 file should exist on Drive (no duplicate)
        assertEquals(1, fakeDriveApiClient.files.size)
    }

    // =========================================================================
    // Test 6: Duplicate upload prevention
    // =========================================================================
    @Test
    fun testDuplicateUploadPrevention() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val file = createLocalJournalFile(localDeviceId, 1L, journalId, masterKey)

        val entity = SyncJournalEntity(
            journalId = journalId,
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file.absolutePath,
            encryptedSizeBytes = file.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )
        fakeJournalDao.insert(entity)

        // First upload
        val res1 = journalUploader.uploadJournal(entity)
        assertTrue(res1 is JournalUploadResult.Success)
        assertEquals(1, fakeDriveApiClient.files.size)

        // Second upload attempt of the same journal
        val entityAgain = entity.copy(status = JournalStatus.READY_TO_UPLOAD.name)
        val res2 = journalUploader.uploadJournal(entityAgain)
        assertTrue(res2 is JournalUploadResult.Success)
        assertEquals((res1 as JournalUploadResult.Success).driveFileId, (res2 as JournalUploadResult.Success).driveFileId)
        assertEquals(1, fakeDriveApiClient.files.size) // No duplicate created
    }

    // =========================================================================
    // Test 7: Same journal ID with different content rejection
    // =========================================================================
    @Test
    fun testSameJournalIdWithDifferentContentRejection() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val file1 = createLocalJournalFile(localDeviceId, 1L, journalId, masterKey)

        // Drive already has a file with the same journalId but DIFFERENT content
        fakeDriveApiClient.addFile(
            name = "journal_${localDeviceId}_1_$journalId.bin",
            bytes = "tampered_bytes".toByteArray(),
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to localDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to "different_hash_123",
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val entity = SyncJournalEntity(
            journalId = journalId,
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file1.absolutePath,
            encryptedSizeBytes = file1.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )

        val result = journalUploader.uploadJournal(entity)
        assertTrue(result is JournalUploadResult.Failure)
        assertTrue((result as JournalUploadResult.Failure).cause is ConflictingJournalException)
    }

    // =========================================================================
    // Test 8: Interrupted download recovery
    // =========================================================================
    @Test
    fun testInterruptedDownloadRecovery() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val fileBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, journalId, masterKey)
        val hash = sha256(fileBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = fileBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 1,
            sizeBytes = fileBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        // Make first download fail across all retries
        fakeDriveApiClient.failDownloadAttempts = 10
        val failResult = journalDownloader.downloadAndValidateJournal(header)
        assertTrue(failResult is JournalDownloadResult.Failure)

        // Staging file should be cleaned up
        val stagingFile = File(fakeContext.filesDir, "sync_inbox_staging/staging_$journalId.tmp")
        assertFalse(stagingFile.exists())

        // Reset failure: second download attempt succeeds
        fakeDriveApiClient.failDownloadAttempts = 0
        val successResult = journalDownloader.downloadAndValidateJournal(header)
        assertTrue(successResult is JournalDownloadResult.Success)
    }

    // =========================================================================
    // Test 9: Encrypted content hash mismatch
    // =========================================================================
    @Test
    fun testEncryptedContentHashMismatch() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val fileBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, journalId, masterKey)

        val advertisedWrongHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = fileBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to advertisedWrongHash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = advertisedWrongHash,
            operationCount = 1,
            sizeBytes = fileBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(ContentHashMismatchException::class.java) {
            runBlocking { journalDownloader.downloadAndValidateJournal(header) }
        }

        // Must not be added to inbox
        assertNull(fakeInboxDao.getByJournalId(journalId))
    }

    // =========================================================================
    // Test 10: Invalid AES-GCM authentication tag
    // =========================================================================
    @Test
    fun testInvalidAesGcmAuthenticationTag() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val fileBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, journalId, masterKey)

        // Flip bit in ciphertext
        fileBytes[fileBytes.size - 5] = (fileBytes[fileBytes.size - 5].toInt() xor 0xFF).toByte()
        val tamperedHash = sha256(fileBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = fileBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to tamperedHash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = tamperedHash,
            operationCount = 1,
            sizeBytes = fileBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(InvalidEncryptedContainerException::class.java) {
            runBlocking { journalDownloader.downloadAndValidateJournal(header) }
        }
        assertNull(fakeInboxDao.getByJournalId(journalId))
    }

    // =========================================================================
    // Test 11: Invalid MEK
    // =========================================================================
    @Test
    fun testInvalidMek() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val fileBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, journalId, masterKey)
        val hash = sha256(fileBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = fileBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 1,
            sizeBytes = fileBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        // Try decrypting with completely different key
        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(InvalidEncryptedContainerException::class.java) {
            runBlocking { journalDownloader.downloadAndValidateJournal(header, overrideKey = wrongKey) }
        }
        assertNull(fakeInboxDao.getByJournalId(journalId))
    }

    // =========================================================================
    // Test 12: Malformed journal schema
    // =========================================================================
    @Test
    fun testMalformedJournalSchema(): Unit = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val corruptedPayload = "{\"journalId\":\"$journalId\", \"incomplete_json\":".toByteArray()
        val aad = SyncJournalWriter.buildAad(1, journalId, remoteDeviceId, 1L)
        val encryptedBytes = EncryptedContainer.encrypt(plaintext = corruptedPayload, key = masterKey, aad = aad)
        val hash = sha256(encryptedBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = encryptedBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 1,
            sizeBytes = encryptedBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(MalformedJournalException::class.java) {
            runBlocking { journalDownloader.downloadAndValidateJournal(header) }
        }
    }

    // =========================================================================
    // Test 13: Unsupported journal format version
    // =========================================================================
    @Test
    fun testUnsupportedJournalFormatVersion(): Unit = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val unsupportedDto = SyncJournalDto(
            formatVersion = 99,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            createdAt = System.currentTimeMillis(),
            operationCount = 0,
            operations = emptyList(),
        )
        val aad = SyncJournalWriter.buildAad(99, journalId, remoteDeviceId, 1L)
        val encryptedBytes = EncryptedContainer.encrypt(plaintext = json.encodeToString(unsupportedDto).toByteArray(), key = masterKey, aad = aad)
        val hash = sha256(encryptedBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$journalId.bin",
            bytes = encryptedBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to journalId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "99",
                "contentSha256" to hash,
                "operationCount" to "0",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 99,
            contentSha256 = hash,
            operationCount = 0,
            sizeBytes = encryptedBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(UnsupportedJournalVersionException::class.java) {
            runBlocking { journalDownloader.downloadAndValidateJournal(header) }
        }
    }

    // =========================================================================
    // Test 14: Missing sequence detection & gap preservation
    // =========================================================================
    @Test
    fun testMissingSequenceDetection() = runBlocking {
        // Peer device has sequences 1, 2, and 4 in Drive (sequence 3 is MISSING)
        val j1 = createRemoteJournal(remoteDeviceId, 1L, masterKey)
        val j2 = createRemoteJournal(remoteDeviceId, 2L, masterKey)
        val j4 = createRemoteJournal(remoteDeviceId, 4L, masterKey)

        // 1. Discovery should detect sequence 3 as missing
        val discovery = discoveryService.discoverRemoteJournals(localDeviceId)
        assertTrue(discovery is DiscoveryResult.Success)

        val watermark = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertNotNull(watermark)
        assertEquals(4L, watermark?.highestDiscoveredSeq)
        assertTrue(watermark!!.missingSequencesJson.contains("3"))

        // 2. Download sequence 1 and 2
        journalDownloader.downloadAndValidateJournal(j1)
        val wm1 = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(1L, wm1?.highestValidatedSeq)

        journalDownloader.downloadAndValidateJournal(j2)
        val wm2 = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(2L, wm2?.highestValidatedSeq)

        // 3. Download sequence 4 (sequence 3 is still missing!)
        journalDownloader.downloadAndValidateJournal(j4)
        val wm4 = fakeWatermarkDao.getWatermark(remoteDeviceId)
        // highestValidatedSeq MUST NOT advance past 2 because sequence 3 is missing!
        assertEquals(2L, wm4?.highestValidatedSeq)
        assertEquals(4L, wm4?.highestDownloadedSeq)
        assertTrue(wm4!!.missingSequencesJson.contains("3"))

        // 4. Now sequence 3 arrives and is downloaded
        val j3 = createRemoteJournal(remoteDeviceId, 3L, masterKey)
        journalDownloader.downloadAndValidateJournal(j3)

        // Now contiguous validation advances to 4!
        val wmFinal = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(4L, wmFinal?.highestValidatedSeq)
        assertEquals("[]", wmFinal?.missingSequencesJson)
    }

    // =========================================================================
    // Test 15: Duplicate inbox insertion prevention
    // =========================================================================
    @Test
    fun testDuplicateInboxInsertionPrevention() = runBlocking {
        val j1 = createRemoteJournal(remoteDeviceId, 1L, masterKey)

        val res1 = journalDownloader.downloadAndValidateJournal(j1)
        assertTrue(res1 is JournalDownloadResult.Success)

        val res2 = journalDownloader.downloadAndValidateJournal(j1)
        assertTrue(res2 is JournalDownloadResult.SkippedAlreadyDownloaded)
        assertEquals(1, fakeInboxDao.getEntriesForDevice(remoteDeviceId).size)
    }

    // =========================================================================
    // Test 16: Drive authorization revocation
    // =========================================================================
    @Test
    fun testDriveAuthorizationRevocation() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val file = createLocalJournalFile(localDeviceId, 1L, journalId, masterKey)

        val entity = SyncJournalEntity(
            journalId = journalId,
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file.absolutePath,
            encryptedSizeBytes = file.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )

        fakeDriveApiClient.simulateUnauthorized = true
        fakeAuthDelegate.isRevoked = true

        val uploadResult = journalUploader.uploadJournal(entity)
        assertTrue(uploadResult is JournalUploadResult.Unauthorized)

        val discoveryResult = discoveryService.discoverRemoteJournals(localDeviceId)
        assertTrue(discoveryResult is DiscoveryResult.Unauthorized)
    }

    // =========================================================================
    // Test 17: Rate limiting and retry behavior
    // =========================================================================
    @Test
    fun testRateLimitingAndRetryBehavior() = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val file = createLocalJournalFile(localDeviceId, 1L, journalId, masterKey)

        val entity = SyncJournalEntity(
            journalId = journalId,
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file.absolutePath,
            encryptedSizeBytes = file.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )

        // Fail first attempt with 429, then succeed
        fakeDriveApiClient.rateLimitAttempts = 1

        val result = journalUploader.uploadJournal(entity)
        assertTrue(result is JournalUploadResult.Success)
    }

    // =========================================================================
    // Test 18: Application restart recovery
    // =========================================================================
    @Test
    fun testApplicationRestartRecovery() = runBlocking {
        val j1 = createRemoteJournal(remoteDeviceId, 1L, masterKey)
        journalDownloader.downloadAndValidateJournal(j1)

        assertEquals(1L, fakeWatermarkDao.getWatermark(remoteDeviceId)?.highestValidatedSeq)

        // Simulate app restart by re-instantiating downloader and discovery service
        val freshDiscovery = JournalDiscoveryService(transport, fakeWatermarkDao, testDispatcher)
        val freshDownloader = JournalDownloader(
            fakeContext,
            transport,
            fakeInboxDao,
            fakeWatermarkDao,
            mekManager,
            database = null,
            testDispatcher,
        )

        // Validate state was fully preserved across restart
        val wm = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(1L, wm?.highestValidatedSeq)
        assertNotNull(fakeInboxDao.getByJournalId(j1.journalId))

        // Redownload attempt after restart is cleanly recognized as already downloaded
        val retryResult = freshDownloader.downloadAndValidateJournal(j1)
        assertTrue(retryResult is JournalDownloadResult.SkippedAlreadyDownloaded)
    }

    // =========================================================================
    // Test 19: Crash Consistency - Purges Dangling Staging Files
    // =========================================================================
    @Test
    fun testCrashConsistencyPurgesDanglingStagingFiles(): Unit = runBlocking {
        val stagingDir = File(fakeContext.filesDir, JournalDownloader.STAGING_DIR_NAME).apply { mkdirs() }
        val danglingTmp1 = File(stagingDir, "staging_interrupted_1.tmp").apply { writeText("partial download") }
        val danglingTmp2 = File(stagingDir, "staging_interrupted_2.tmp").apply { writeText("corrupted download") }
        val unrelatedFile = File(stagingDir, "keep_me.txt").apply { writeText("not a tmp file") }

        assertTrue(danglingTmp1.exists())
        assertTrue(danglingTmp2.exists())

        val result = journalDownloader.reconcileInboxStorage(masterKey)
        assertEquals(2, result.purgedStagingFiles)
        assertFalse(danglingTmp1.exists())
        assertFalse(danglingTmp2.exists())
        assertTrue(unrelatedFile.exists())
    }

    // =========================================================================
    // Test 20: Crash Consistency - Cleans Up Room Record When Durable File Deleted
    // =========================================================================
    @Test
    fun testCrashConsistencyCleansUpRoomRecordWhenFileDeleted(): Unit = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val nonExistentPath = File(fakeContext.filesDir, "sync_inbox_storage/missing_${journalId}.bin").absolutePath

        val entity = SyncInboxEntity(
            journalId = journalId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            driveFileId = "drive_123",
            localFilePath = nonExistentPath,
            contentSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            operationCount = 1,
            downloadedAt = System.currentTimeMillis(),
            status = InboxStatus.VALIDATED,
        )
        fakeInboxDao.insert(entity)
        fakeWatermarkDao.insertOrUpdate(
            SyncWatermarkEntity(
                deviceId = remoteDeviceId,
                highestDownloadedSeq = 1L,
                highestValidatedSeq = 1L,
            )
        )

        // Reconciliation detects physical file is missing
        val result = journalDownloader.reconcileInboxStorage(masterKey)
        assertEquals(1, result.missingFilesCleaned)
        assertNull(fakeInboxDao.getByJournalId(journalId))

        // Device watermark rolled back
        val wm = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(0L, wm?.highestValidatedSeq)
    }

    // =========================================================================
    // Test 21: Crash Consistency - Reconciles Valid Orphan File In Storage
    // =========================================================================
    @Test
    fun testCrashConsistencyReconcilesValidOrphanFileInStorage(): Unit = runBlocking {
        val journalId = UUID.randomUUID().toString()
        val storageDir = File(fakeContext.filesDir, JournalDownloader.INBOX_STORAGE_DIR_NAME).apply { mkdirs() }
        val orphanFile = File(storageDir, "${remoteDeviceId}_1_${journalId}.bin")
        val fileBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, journalId, masterKey)
        orphanFile.writeBytes(fileBytes)
        val expectedHash = sha256(fileBytes)

        // Room has NO entry for this file yet (simulating crash right after atomic move before commit)
        assertNull(fakeInboxDao.getByJournalId(journalId))

        val result = journalDownloader.reconcileInboxStorage(masterKey)
        assertEquals(1, result.orphanFilesReconciled)

        // Safe registration in Room and watermark update verified
        val recovered = fakeInboxDao.getByJournalId(journalId)
        assertNotNull(recovered)
        assertEquals(InboxStatus.VALIDATED, recovered?.status)
        assertEquals(expectedHash, recovered?.contentSha256)
        assertEquals(1, recovered?.operationCount)

        val wm = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(1L, wm?.highestValidatedSeq)
    }

    // =========================================================================
    // Test 22: Crash Consistency - Purges Corrupted Orphan File In Storage
    // =========================================================================
    @Test
    fun testCrashConsistencyPurgesCorruptedOrphanFileInStorage(): Unit = runBlocking {
        val storageDir = File(fakeContext.filesDir, JournalDownloader.INBOX_STORAGE_DIR_NAME).apply { mkdirs() }
        val corruptedFile = File(storageDir, "corrupted_orphan.bin").apply { writeText("garbage data") }

        assertTrue(corruptedFile.exists())
        val result = journalDownloader.reconcileInboxStorage(masterKey)
        assertEquals(0, result.orphanFilesReconciled)
        assertFalse(corruptedFile.exists()) // Purged
    }

    // =========================================================================
    // Test 23: Sequence Collision - Quarantines Colliding Sequences in Discovery
    // =========================================================================
    @Test
    fun testSequenceCollisionDetectionAndQuarantineInDiscovery(): Unit = runBlocking {
        val j1 = createRemoteJournal(remoteDeviceId, 1L, masterKey)

        // Create two conflicting journals for sequence 2
        val j2aId = UUID.randomUUID().toString()
        val bytes2a = createEncryptedJournalBytes(remoteDeviceId, 2L, j2aId, masterKey)
        fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_2_$j2aId.bin",
            bytes = bytes2a,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to j2aId,
                "deviceId" to remoteDeviceId,
                "sequence" to "2",
                "formatVersion" to "1",
                "contentSha256" to sha256(bytes2a),
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val j2bId = UUID.randomUUID().toString()
        val bytes2b = createEncryptedJournalBytes(remoteDeviceId, 2L, j2bId, masterKey)
        fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_2_$j2bId.bin",
            bytes = bytes2b,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to j2bId,
                "deviceId" to remoteDeviceId,
                "sequence" to "2",
                "formatVersion" to "1",
                "contentSha256" to sha256(bytes2b),
                "operationCount" to "1",
                "uploadedAt" to "2000",
            )
        )

        val j3 = createRemoteJournal(remoteDeviceId, 3L, masterKey)

        val discovery = discoveryService.discoverRemoteJournals(localDeviceId)
        assertTrue(discovery is DiscoveryResult.Success)
        val discoveredList = (discovery as DiscoveryResult.Success).discoveredJournalsByDevice[remoteDeviceId] ?: emptyList()

        // Sequence 2 has a collision and MUST be quarantined (not in discovered list)
        val seqs = discoveredList.map { it.sequence }
        assertTrue(seqs.contains(1L))
        assertFalse("Sequence 2 must be quarantined due to collision", seqs.contains(2L))
        assertTrue(seqs.contains(3L))

        // Watermark check: missingSequences MUST include sequence 2
        val wm = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertNotNull(wm)
        assertTrue(wm!!.missingSequencesJson.contains("2"))

        // Download valid journals: sequence 1 and 3
        journalDownloader.downloadAndValidateJournals(discoveredList)

        // Watermark highestValidatedSeq CANNOT advance across sequence 2 gap
        val finalWm = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(1L, finalWm?.highestValidatedSeq)
        assertTrue(finalWm!!.missingSequencesJson.contains("2"))
    }

    // =========================================================================
    // Test 24: Cryptographic Validation - Wrong MEK Rejection
    // =========================================================================
    @Test
    fun testDecryptionRejectsWrongMasterKey(): Unit = runBlocking {
        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val j1 = createRemoteJournal(remoteDeviceId, 1L, masterKey)

        assertThrows(InvalidEncryptedContainerException::class.java) {
            runBlocking {
                journalDownloader.downloadAndValidateJournal(j1, overrideKey = wrongKey)
            }
        }
        assertNull(fakeInboxDao.getByJournalId(j1.journalId))
    }

    // =========================================================================
    // Test 25: Cryptographic Validation - Tampered Ciphertext Rejection
    // =========================================================================
    @Test
    fun testDecryptionRejectsTamperedCiphertext(): Unit = runBlocking {
        val jId = UUID.randomUUID().toString()
        val originalBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, jId, masterKey)
        // Flip a byte in the ciphertext body
        val tamperedBytes = originalBytes.clone()
        tamperedBytes[tamperedBytes.size - 5] = (tamperedBytes[tamperedBytes.size - 5].toInt() xor 0xFF).toByte()
        val tamperedHash = sha256(tamperedBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$jId.bin",
            bytes = tamperedBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to jId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to tamperedHash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = jId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = tamperedHash,
            operationCount = 1,
            sizeBytes = tamperedBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(InvalidEncryptedContainerException::class.java) {
            runBlocking {
                journalDownloader.downloadAndValidateJournal(header)
            }
        }
        assertNull(fakeInboxDao.getByJournalId(jId))
    }

    // =========================================================================
    // Test 26: Cryptographic Validation - Tampered AAD Rejection
    // =========================================================================
    @Test
    fun testDecryptionRejectsTamperedAad(): Unit = runBlocking {
        val jId = UUID.randomUUID().toString()
        // Encrypted with sequence 1 in AAD
        val originalBytes = createEncryptedJournalBytes(remoteDeviceId, 1L, jId, masterKey)
        val hash = sha256(originalBytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$jId.bin",
            bytes = originalBytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to jId,
                "deviceId" to remoteDeviceId,
                "sequence" to "2", // Claimed sequence 2 in header, mismatching AAD sequence 1
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = jId,
            deviceId = remoteDeviceId,
            sequence = 2L,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 1,
            sizeBytes = originalBytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(InvalidEncryptedContainerException::class.java) {
            runBlocking {
                journalDownloader.downloadAndValidateJournal(header)
            }
        }
        assertNull(fakeInboxDao.getByJournalId(jId))
    }

    // =========================================================================
    // Test 27: Metadata Binding - Operation Count Mismatch Rejection
    // =========================================================================
    @Test
    fun testMetadataPayloadBindingOperationCountMismatch(): Unit = runBlocking {
        val jId = UUID.randomUUID().toString()
        val bytes = createEncryptedJournalBytes(remoteDeviceId, 1L, jId, masterKey) // Has 1 operation
        val hash = sha256(bytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$jId.bin",
            bytes = bytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to jId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "99", // Lies about operation count
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = jId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 99,
            sizeBytes = bytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(MalformedJournalException::class.java) {
            runBlocking {
                journalDownloader.downloadAndValidateJournal(header)
            }
        }
        assertNull(fakeInboxDao.getByJournalId(jId))
    }

    // =========================================================================
    // Test 28: Resource Limits - Oversized Encrypted Journal Rejection
    // =========================================================================
    @Test
    fun testOversizedEncryptedJournalRejection(): Unit = runBlocking {
        val jId = UUID.randomUUID().toString()
        val oversizedHeader = RemoteJournalHeader(
            driveFileId = "drive_huge",
            journalId = jId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            operationCount = 1,
            sizeBytes = 5 * 1024 * 1024L, // 5MB > 4MB limit
            createdAtEpochMillis = 1000L,
        )

        assertThrows(MalformedJournalException::class.java) {
            runBlocking {
                journalDownloader.downloadAndValidateJournal(oversizedHeader)
            }
        }
    }

    // =========================================================================
    // Test 29: Resource Limits - Excessive Operation Count Rejection
    // =========================================================================
    @Test
    fun testExcessiveOperationCountRejection(): Unit = runBlocking {
        val jId = UUID.randomUUID().toString()
        val ops = (1..501).map {
            JournalOperationDto(
                changeId = UUID.randomUUID().toString(),
                entityType = "NOTE",
                entityId = "note_$it",
                operationType = "CREATE",
                schemaVersion = 1,
                createdAt = System.currentTimeMillis(),
                payload = "{}",
            )
        }
        val dto = SyncJournalDto(
            formatVersion = 1,
            journalId = jId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            createdAt = System.currentTimeMillis(),
            operationCount = 501,
            operations = ops,
        )
        val aad = SyncJournalWriter.buildAad(1, jId, remoteDeviceId, 1L)
        val plainBytes = json.encodeToString(dto).toByteArray(Charsets.UTF_8)
        val bytes = EncryptedContainer.encrypt(plaintext = plainBytes, key = masterKey, aad = aad)
        val hash = sha256(bytes)

        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${remoteDeviceId}_1_$jId.bin",
            bytes = bytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to jId,
                "deviceId" to remoteDeviceId,
                "sequence" to "1",
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "501",
                "uploadedAt" to "1000",
            )
        )

        val header = RemoteJournalHeader(
            driveFileId = driveId,
            journalId = jId,
            deviceId = remoteDeviceId,
            sequence = 1L,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 501,
            sizeBytes = bytes.size.toLong(),
            createdAtEpochMillis = 1000L,
        )

        assertThrows(MalformedJournalException::class.java) {
            runBlocking {
                journalDownloader.downloadAndValidateJournal(header)
            }
        }
    }

    // =========================================================================
    // Test 30: Network Failure Mode - Respect Retry-After Header
    // =========================================================================
    @Test
    fun testRespectRetryAfterHeaderOn429(): Unit = runBlocking {
        fakeDriveApiClient.rateLimitAttempts = 1
        fakeDriveApiClient.retryAfterSecondsHeader = 1L // 1 second

        val file = createLocalJournalFile(localDeviceId, 1L, UUID.randomUUID().toString(), masterKey)
        val entity = SyncJournalEntity(
            journalId = UUID.randomUUID().toString(),
            deviceId = localDeviceId,
            sequence = 1L,
            formatVersion = 1,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            storagePath = file.absolutePath,
            encryptedSizeBytes = file.length(),
            status = JournalStatus.READY_TO_UPLOAD.name,
        )

        val res = journalUploader.uploadJournal(entity)
        assertTrue(res is JournalUploadResult.Success)
    }

    // =========================================================================
    // Test 31: Pagination - findJournalById across Multiple Pages
    // =========================================================================
    @Test
    fun testFindJournalByIdPaginatesAcrossMultiplePages(): Unit = runBlocking {
        val targetJournalId = UUID.randomUUID().toString()

        // Add 45 dummy files to fill pages
        for (i in 1..45) {
            fakeDriveApiClient.addFile(
                name = "dummy_$i.bin",
                bytes = byteArrayOf(1),
                appProperties = mapOf("journalId" to "dummy_$i")
            )
        }

        // Add target file
        fakeDriveApiClient.addFile(
            name = "target.bin",
            bytes = byteArrayOf(2),
            appProperties = mapOf("journalId" to targetJournalId)
        )

        val findRes = transport.findJournalById(targetJournalId)
        assertTrue(findRes is DriveResult.Success)
        assertNotNull((findRes as DriveResult.Success).data)
        assertEquals(targetJournalId, findRes.data?.appProperties?.get("journalId"))
    }

    // =========================================================================
    // Test 32: Watermark Safety - Gap Repair Progression
    // =========================================================================
    @Test
    fun testWatermarkGapRepairProgression(): Unit = runBlocking {
        val j1 = createRemoteJournal(remoteDeviceId, 1L, masterKey)
        val j2 = createRemoteJournal(remoteDeviceId, 2L, masterKey)
        val j3 = createRemoteJournal(remoteDeviceId, 3L, masterKey)

        // Download j1 and j3 (simulating out-of-order arrival or missing j2)
        journalDownloader.downloadAndValidateJournal(j1)
        journalDownloader.downloadAndValidateJournal(j3)

        val wmStep1 = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(1L, wmStep1?.highestValidatedSeq)
        assertEquals(3L, wmStep1?.highestDownloadedSeq)

        // Late arrival of missing gap j2
        journalDownloader.downloadAndValidateJournal(j2)

        val wmStep2 = fakeWatermarkDao.getWatermark(remoteDeviceId)
        assertEquals(3L, wmStep2?.highestValidatedSeq)
        assertEquals(3L, wmStep2?.highestDownloadedSeq)
        assertEquals("[]", wmStep2?.missingSequencesJson)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun createLocalJournalFile(
        deviceId: String,
        seq: Long,
        journalId: String,
        key: SecretKey,
    ): File {
        val dir = File(fakeContext.filesDir, "sync_journals/$deviceId").apply { mkdirs() }
        val file = File(dir, "journal_${deviceId}_${seq}.bin")
        val bytes = createEncryptedJournalBytes(deviceId, seq, journalId, key)
        file.writeBytes(bytes)
        return file
    }

    private fun createEncryptedJournalBytes(
        deviceId: String,
        seq: Long,
        journalId: String,
        key: SecretKey,
    ): ByteArray {
        val dto = SyncJournalDto(
            formatVersion = 1,
            journalId = journalId,
            deviceId = deviceId,
            sequence = seq,
            createdAt = System.currentTimeMillis(),
            operationCount = 1,
            operations = listOf(
                JournalOperationDto(
                    changeId = UUID.randomUUID().toString(),
                    entityType = "NOTE",
                    entityId = "note_$seq",
                    operationType = "CREATE",
                    schemaVersion = 1,
                    createdAt = System.currentTimeMillis(),
                    payload = "{}",
                )
            ),
        )
        val aad = SyncJournalWriter.buildAad(1, journalId, deviceId, seq)
        val plainBytes = json.encodeToString(dto).toByteArray(Charsets.UTF_8)
        return EncryptedContainer.encrypt(plaintext = plainBytes, key = key, aad = aad)
    }

    private fun createRemoteJournal(
        deviceId: String,
        seq: Long,
        key: SecretKey,
    ): RemoteJournalHeader {
        val jId = UUID.randomUUID().toString()
        val bytes = createEncryptedJournalBytes(deviceId, seq, jId, key)
        val hash = sha256(bytes)
        val driveId = fakeDriveApiClient.addFile(
            name = "journal_${deviceId}_${seq}_$jId.bin",
            bytes = bytes,
            appProperties = mapOf(
                "appId" to "notas",
                "type" to "sync_journal",
                "journalId" to jId,
                "deviceId" to deviceId,
                "sequence" to seq.toString(),
                "formatVersion" to "1",
                "contentSha256" to hash,
                "operationCount" to "1",
                "uploadedAt" to System.currentTimeMillis().toString(),
            )
        )
        return RemoteJournalHeader(
            driveFileId = driveId,
            journalId = jId,
            deviceId = deviceId,
            sequence = seq,
            formatVersion = 1,
            contentSha256 = hash,
            operationCount = 1,
            sizeBytes = bytes.size.toLong(),
            createdAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // =========================================================================
    // Fake DAOs and Clients
    // =========================================================================

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var isRevoked = false
        override suspend fun authorize(context: Context, forceAccountSelection: Boolean): GoogleDriveAuthorizationResult {
            if (isRevoked) return GoogleDriveAuthorizationResult.Failure(IllegalStateException("Revoked"))
            return GoogleDriveAuthorizationResult.Success("test_token_123", "alice@example.com")
        }
        override suspend fun parseConsentResult(context: Context, data: android.content.Intent?): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("test_token_123", "alice@example.com")
        override suspend fun disconnect(context: Context, accountEmail: String?, accessToken: String?) {
            isRevoked = true
        }
        override suspend fun invalidateToken(context: Context, token: String) {}
    }

    private class FakeGoogleDriveApiClient {
        data class DriveEntry(val id: String, val name: String, val bytes: ByteArray, val appProperties: Map<String, String>)

        val files = mutableMapOf<String, DriveEntry>()
        var simulateUnauthorized = false
        var failDownloadAttempts = 0
        var rateLimitAttempts = 0
        var retryAfterSecondsHeader: Long? = null
        var pendingUploadId: String? = null
        private var idCounter = 1

        fun addFile(name: String, bytes: ByteArray, appProperties: Map<String, String>): String {
            val id = "drive_file_${idCounter++}"
            files[id] = DriveEntry(id, name, bytes, appProperties)
            return id
        }

        fun asClient(): GoogleDriveApiClient {
            return GoogleDriveApiClient(
                httpFactory = { urlString ->
                    object : HttpURLConnection(URL(urlString)) {
                        override fun getResponseCode(): Int {
                            if (simulateUnauthorized) return HTTP_UNAUTHORIZED
                            if (rateLimitAttempts > 0) {
                                rateLimitAttempts--
                                return 429
                            }
                            if (failDownloadAttempts > 0 && urlString.contains("alt=media")) {
                                failDownloadAttempts--
                                return 500
                            }
                            return HTTP_OK
                        }

                        override fun getHeaderField(name: String?): String? {
                            if (name.equals("Retry-After", ignoreCase = true) && retryAfterSecondsHeader != null) {
                                return retryAfterSecondsHeader.toString()
                            }
                            return super.getHeaderField(name)
                        }

                        override fun getInputStream(): InputStream {
                            if (simulateUnauthorized) return ByteArrayInputStream(byteArrayOf())
                            if (urlString.contains("/files?")) {
                                // listFiles or upload
                                if (requestMethod == "POST") {
                                    // Upload response
                                    val id = pendingUploadId ?: "drive_file_${idCounter++}"
                                    pendingUploadId = null
                                    val jsonResp = """{"id":"$id","name":"uploaded"}"""
                                    return ByteArrayInputStream(jsonResp.toByteArray())
                                } else {
                                    // listFiles
                                    val query = URL(urlString).query ?: ""
                                    val pageSize = query.substringAfter("pageSize=", "").substringBefore("&").toIntOrNull() ?: 50
                                    val pageToken = query.substringAfter("pageToken=", "").substringBefore("&")

                                    val sortedEntries = files.values.sortedBy { it.id }
                                    val startIndex = pageToken.toIntOrNull() ?: 0
                                    val pagedEntries = sortedEntries.drop(startIndex).take(pageSize)
                                    val nextIndex = startIndex + pagedEntries.size
                                    val nextTokenStr = if (nextIndex < sortedEntries.size) nextIndex.toString() else null

                                    val filesJson = pagedEntries.map { entry ->
                                        val propsJson = entry.appProperties.entries.joinToString(",") { (k, v) ->
                                            "\"$k\":\"$v\""
                                        }
                                        """{"id":"${entry.id}","name":"${entry.name}","size":"${entry.bytes.size}","appProperties":{$propsJson}}"""
                                    }.joinToString(",")

                                    val resp = if (nextTokenStr != null) {
                                        """{"nextPageToken":"$nextTokenStr","files":[$filesJson]}"""
                                    } else {
                                        """{"files":[$filesJson]}"""
                                    }
                                    return ByteArrayInputStream(resp.toByteArray())
                                }
                            } else if (urlString.contains("alt=media")) {
                                // downloadFile
                                val fileId = urlString.substringAfter("/files/").substringBefore("?")
                                val entry = files[fileId] ?: files.values.find { it.id == fileId }
                                return ByteArrayInputStream(entry?.bytes ?: byteArrayOf())
                            } else {
                                // getFileMetadata
                                val fileId = urlString.substringAfter("/files/").substringBefore("?")
                                val entry = files[fileId]
                                val propsJson = entry?.appProperties?.entries?.joinToString(",") { (k, v) ->
                                    "\"$k\":\"$v\""
                                } ?: ""
                                val resp = """{"id":"${entry?.id}","name":"${entry?.name}","size":"${entry?.bytes?.size ?: 0}","appProperties":{$propsJson}}"""
                                return ByteArrayInputStream(resp.toByteArray())
                            }
                        }

                        override fun getOutputStream(): OutputStream {
                            return object : ByteArrayOutputStream() {
                                override fun close() {
                                    super.close()
                                    val body = String(toByteArray(), Charsets.UTF_8)
                                    // If upload multipart, parse and store file
                                    if (urlString.contains("uploadType=multipart")) {
                                        val journalId = body.substringAfter("\"journalId\":\"", "").substringBefore("\"")
                                        if (journalId.isNotBlank()) {
                                            val deviceId = body.substringAfter("\"deviceId\":\"", "").substringBefore("\"")
                                            val seq = body.substringAfter("\"sequence\":\"", "").substringBefore("\"")
                                            val hash = body.substringAfter("\"contentSha256\":\"", "").substringBefore("\"")
                                            val id = "drive_file_${idCounter++}"
                                            pendingUploadId = id
                                            files[id] = DriveEntry(
                                                id = id,
                                                name = "journal_${deviceId}_${seq}_$journalId.bin",
                                                bytes = byteArrayOf(1, 2, 3), // dummy bytes in multipart stream
                                                appProperties = mapOf(
                                                    "appId" to "notas",
                                                    "type" to "sync_journal",
                                                    "journalId" to journalId,
                                                    "deviceId" to deviceId,
                                                    "sequence" to seq,
                                                    "formatVersion" to "1",
                                                    "contentSha256" to hash,
                                                    "operationCount" to "1",
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        override fun disconnect() {}
                        override fun connect() {}
                        override fun usingProxy(): Boolean = false
                    }
                }
            )
        }
    }

    private class FakeSyncJournalDao : SyncJournalDao {
        val journals = mutableMapOf<String, SyncJournalEntity>()

        override suspend fun insert(journal: SyncJournalEntity): Long {
            journals[journal.journalId] = journal
            return 1L
        }
        override suspend fun update(journal: SyncJournalEntity) { journals[journal.journalId] = journal }
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
        override suspend fun deleteById(journalId: String): Int = if (journals.remove(journalId) != null) 1 else 0
        override suspend fun clearAll() { journals.clear() }
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
        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = emptyFlow()
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

    private class FakeSyncInboxDao : SyncInboxDao {
        val inbox = mutableMapOf<String, SyncInboxEntity>()

        override suspend fun insert(entry: SyncInboxEntity): Long {
            if (inbox.containsKey(entry.journalId)) {
                throw android.database.sqlite.SQLiteConstraintException("Duplicate journalId: ${entry.journalId}")
            }
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
            val existing = watermarks[deviceId]
            if (existing != null) {
                watermarks[deviceId] = existing.copy(highestAppliedSeq = appliedSeq, updatedAt = timestamp)
                return 1
            }
            return 0
        }
        override suspend fun deleteForDevice(deviceId: String): Int = if (watermarks.remove(deviceId) != null) 1 else 0
        override suspend fun clearAll() { watermarks.clear() }
    }
}
