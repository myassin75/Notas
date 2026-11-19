package haddad.three.notas.data.sync.worker

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.ListenableWorker
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.sync.SyncStateStore
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.conflict.FieldConflictResolver
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
import haddad.three.notas.data.sync.ingestion.RemoteJournalIngestionEngine
import haddad.three.notas.data.sync.journal.JournalStatus
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalEntity
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import haddad.three.notas.data.sync.journal.SyncSequenceDao
import haddad.three.notas.data.sync.journal.SyncSequenceEntity
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import haddad.three.notas.data.sync.transport.InboxStatus
import haddad.three.notas.data.sync.transport.JournalDiscoveryService
import haddad.three.notas.data.sync.transport.JournalDownloader
import haddad.three.notas.data.sync.transport.JournalUploader
import haddad.three.notas.data.sync.transport.SyncInboxDao
import haddad.three.notas.data.sync.transport.SyncInboxEntity
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import haddad.three.notas.data.sync.transport.SyncWatermarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SyncWorkerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = Dispatchers.IO

    private lateinit var fakeContext: Context
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var driveSyncStateStore: DriveSyncStateStore
    private lateinit var syncStateStore: SyncStateStore
    private lateinit var mekManager: MekManager
    private lateinit var masterKey: SecretKey

    private lateinit var fakeJournalDao: FakeSyncJournalDao
    private lateinit var fakePendingChangeDao: FakePendingChangeDao
    private lateinit var fakeSequenceDao: FakeSyncSequenceDao
    private lateinit var fakeInboxDao: FakeSyncInboxDao
    private lateinit var fakeWatermarkDao: FakeSyncWatermarkDao
    private lateinit var fakeAppliedOpDao: FakeSyncAppliedOperationDao
    private lateinit var fakeAuthDelegate: FakeGoogleDriveAuthDelegate
    private lateinit var fakeDriveApiClient: FakeGoogleDriveApiClient

    private lateinit var transport: GoogleDriveSyncTransport
    private lateinit var discoveryService: JournalDiscoveryService
    private lateinit var downloader: JournalDownloader
    private lateinit var ingestionEngine: RemoteJournalIngestionEngine
    private lateinit var journalWriter: SyncJournalWriter
    private lateinit var uploader: JournalUploader
    private lateinit var coordinator: DriveSyncCoordinator

    private lateinit var localDeviceId: String
    private val testEmail = "test@example.com"

    @Before
    fun setUp() =
        runBlocking {
            val fakeFilesDir = tempFolder.newFolder("files")
            val fakeCacheDir = tempFolder.newFolder("cache")
            fakeContext =
                object : ContextWrapper(null) {
                    override fun getApplicationContext(): Context = this

                    override fun getFilesDir(): File = fakeFilesDir

                    override fun getCacheDir(): File = fakeCacheDir
                }

            val drivePrefsFile = tempFolder.newFile("test_drive_prefs.preferences_pb")
            drivePrefs = DriveAuthPreferences(PreferenceDataStoreFactory.create { drivePrefsFile })

            val driveSyncFile = tempFolder.newFile("test_drive_sync_prefs.preferences_pb")
            driveSyncStateStore = DriveSyncStateStore(PreferenceDataStoreFactory.create { driveSyncFile })

            val syncStateFile = tempFolder.newFile("test_sync_state.preferences_pb")
            syncStateStore = SyncStateStore(PreferenceDataStoreFactory.create { syncStateFile })
            localDeviceId = syncStateStore.getOrCreateDeviceId()

            val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
            masterKey = keyGen.generateKey()
            val keyStore = SoftwareKeyStoreManager()
            val cryptoStorage =
                InMemoryCryptoStorage().apply {
                    setLocalWrappedMek(keyStore.wrapKey(masterKey, 1), 1)
                }
            mekManager = MekManager(keyStore, cryptoStorage)

            fakeJournalDao = FakeSyncJournalDao()
            fakePendingChangeDao = FakePendingChangeDao()
            fakeSequenceDao = FakeSyncSequenceDao()
            fakeInboxDao = FakeSyncInboxDao()
            fakeWatermarkDao = FakeSyncWatermarkDao()
            fakeAppliedOpDao = FakeSyncAppliedOperationDao()
            fakeAuthDelegate = FakeGoogleDriveAuthDelegate()
            fakeDriveApiClient = FakeGoogleDriveApiClient()

            transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = fakeDriveApiClient.asClient(),
                    driveAuthDelegate = fakeAuthDelegate,
                    drivePrefs = drivePrefs,
                    ioDispatcher = testDispatcher,
                )

            discoveryService =
                JournalDiscoveryService(
                    transport = transport,
                    watermarkDao = fakeWatermarkDao,
                    ioDispatcher = testDispatcher,
                )

            downloader =
                JournalDownloader(
                    context = fakeContext,
                    transport = transport,
                    inboxDao = fakeInboxDao,
                    watermarkDao = fakeWatermarkDao,
                    mekManager = mekManager,
                    database = null,
                    ioDispatcher = testDispatcher,
                )

            val fakeFieldConflictResolver =
                FieldConflictResolver(
                    noteDao = FakeNoteDao(),
                    checklistItemDao = FakeChecklistItemDao(),
                    shoulderDao = FakeShoulderDao(),
                    attachmentDao = FakeAttachmentDao(),
                    conflictMetadataDao = FakeSyncConflictMetadataDao(),
                    tombstoneDao = FakeSyncTombstoneDao(),
                    conflictRecordDao = FakeSyncConflictRecordDao(),
                )

            val hlcClock = HlcClock(localDeviceId, InMemoryHlcStorage())

            ingestionEngine =
                RemoteJournalIngestionEngine(
                    database = null,
                    syncInboxDao = fakeInboxDao,
                    syncWatermarkDao = fakeWatermarkDao,
                    appliedOperationDao = fakeAppliedOpDao,
                    fieldConflictResolver = fakeFieldConflictResolver,
                    hlcClock = hlcClock,
                    mekManager = mekManager,
                    transactionRunner = { block -> block() },
                )

            journalWriter =
                SyncJournalWriter(
                    context = fakeContext,
                    database = null,
                    pendingChangeDao = fakePendingChangeDao,
                    sequenceDao = fakeSequenceDao,
                    journalDao = fakeJournalDao,
                    mekManager = mekManager,
                    deviceIdProvider = { localDeviceId },
                )

            uploader =
                JournalUploader(
                    journalDao = fakeJournalDao,
                    pendingChangeDao = fakePendingChangeDao,
                    transport = transport,
                    database = null,
                    ioDispatcher = testDispatcher,
                )

            coordinator =
                DriveSyncCoordinator(
                    context = fakeContext,
                    drivePrefs = drivePrefs,
                    driveSyncStateStore = driveSyncStateStore,
                    mekManager = mekManager,
                    syncStateStore = syncStateStore,
                    discoveryService = discoveryService,
                    downloader = downloader,
                    ingestionEngine = ingestionEngine,
                    journalWriter = journalWriter,
                    uploader = uploader,
                    journalDao = fakeJournalDao,
                    pendingChangeDao = fakePendingChangeDao,
                    ioDispatcher = testDispatcher,
                )
        }

    private fun assumeDriveSyncSupported() {
        assumeTrue(
            "Test applies when Google Drive sync is enabled in flavor",
            SyncWorkScheduler.isGoogleDriveSyncSupported(),
        )
    }

    // =========================================================================
    // 1. Precondition Checks
    // =========================================================================

    @Test
    fun testSkippedWhenDriveNotConnected() =
        runBlocking {
            drivePrefs.setDisconnected()

            val result = coordinator.executeSync()
            assertTrue(result is SyncExecutionResult.Skipped)
            assertEquals(ListenableWorker.Result.success(), SyncWorker.mapResult(result))
            assertEquals(DriveSyncState.Idle, driveSyncStateStore.syncState.value)
        }

    @Test
    fun testSkippedWhenSyncDisabledInPreferences() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)
            driveSyncStateStore.setSyncPreferences(enabled = false)

            val result = coordinator.executeSync()
            assertTrue(result is SyncExecutionResult.Skipped)
            assertEquals(ListenableWorker.Result.success(), SyncWorker.mapResult(result))
            assertEquals(DriveSyncState.Idle, driveSyncStateStore.syncState.value)
        }

    @Test
    fun testFatalWhenConnectedEmailMissing() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected("")

            val result = coordinator.executeSync()
            assertTrue(result is SyncExecutionResult.Fatal)
            val fatal = result as SyncExecutionResult.Fatal
            assertEquals(SyncErrorCategory.ACCOUNT_MISMATCH, fatal.errorCategory)
            assertEquals(ListenableWorker.Result.failure(), SyncWorker.mapResult(result))
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.AuthenticationRequired)
        }

    @Test
    fun testFatalWhenMekNotAvailable() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)
            val emptyStorage = InMemoryCryptoStorage()
            val emptyMekManager = MekManager(SoftwareKeyStoreManager(), emptyStorage)

            val unprovisionedCoordinator =
                DriveSyncCoordinator(
                    context = fakeContext,
                    drivePrefs = drivePrefs,
                    driveSyncStateStore = driveSyncStateStore,
                    mekManager = emptyMekManager,
                    syncStateStore = syncStateStore,
                    discoveryService = discoveryService,
                    downloader = downloader,
                    ingestionEngine = ingestionEngine,
                    journalWriter = journalWriter,
                    uploader = uploader,
                    journalDao = fakeJournalDao,
                    pendingChangeDao = fakePendingChangeDao,
                    ioDispatcher = testDispatcher,
                )

            val result = unprovisionedCoordinator.executeSync()
            assertTrue(result is SyncExecutionResult.Fatal)
            val fatal = result as SyncExecutionResult.Fatal
            assertEquals(SyncErrorCategory.MEK_UNAVAILABLE, fatal.errorCategory)
            assertEquals(ListenableWorker.Result.failure(), SyncWorker.mapResult(result))
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.AuthenticationRequired)
        }

    // =========================================================================
    // 2. Discovery Failures & Failure Classification
    // =========================================================================

    @Test
    fun testDiscoveryUnauthorizedTriggersAuthenticationRequired() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)
            fakeAuthDelegate.authorizeResult =
                GoogleDriveAuthorizationResult.Failure(
                    IllegalStateException("Revoked"),
                )
            fakeDriveApiClient.simulateUnauthorized = true

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Fatal)
            val fatal = result as SyncExecutionResult.Fatal
            assertEquals(SyncErrorCategory.AUTHENTICATION, fatal.errorCategory)
            assertEquals(ListenableWorker.Result.failure(), SyncWorker.mapResult(result))
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.AuthenticationRequired)

            val prefs = driveSyncStateStore.getPreferences()
            assertEquals(SyncErrorCategory.AUTHENTICATION, prefs.lastErrorCategory)
        }

    @Test
    fun testDiscoveryRateLimitedTriggersRetryThenFatalOnMaxAttempts() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)
            fakeDriveApiClient.rateLimitAttempts = 10
            fakeDriveApiClient.retryAfterSecondsHeader = 30L

            // Attempt 0: Should return Retry and set Retrying state
            val retryResult = coordinator.executeSync(runAttempt = 0)
            assertTrue(retryResult is SyncExecutionResult.Retry)
            assertEquals(SyncErrorCategory.RATE_LIMITED, (retryResult as SyncExecutionResult.Retry).errorCategory)
            assertEquals(ListenableWorker.Result.retry(), SyncWorker.mapResult(retryResult))
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.Retrying)

            // Attempt 3 (MAX_RETRIES): Should return Fatal and set Failed state
            val fatalResult = coordinator.executeSync(runAttempt = 3)
            assertTrue("Expected Fatal but got $fatalResult", fatalResult is SyncExecutionResult.Fatal)
            assertEquals(SyncErrorCategory.RATE_LIMITED, (fatalResult as SyncExecutionResult.Fatal).errorCategory)
            assertEquals(ListenableWorker.Result.failure(), SyncWorker.mapResult(fatalResult))
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.Failed)
        }

    @Test
    fun testDiscoveryOfflineTriggersWaitingForNetwork() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)
            fakeDriveApiClient.simulateOffline = true

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Retry)
            assertEquals(SyncErrorCategory.NETWORK, (result as SyncExecutionResult.Retry).errorCategory)
            assertEquals(ListenableWorker.Result.retry(), SyncWorker.mapResult(result))
            assertEquals(DriveSyncState.WaitingForNetwork, driveSyncStateStore.syncState.value)
        }

    @Test
    fun testDiscoveryServerErrorBoundedRetries() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)
            fakeDriveApiClient.simulateServerError = true

            val retryResult = coordinator.executeSync(runAttempt = 1)
            assertTrue(retryResult is SyncExecutionResult.Retry)
            assertEquals(SyncErrorCategory.SERVER_ERROR, (retryResult as SyncExecutionResult.Retry).errorCategory)
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.Retrying)

            val fatalResult = coordinator.executeSync(runAttempt = 3)
            assertTrue("Expected Fatal but got $fatalResult", fatalResult is SyncExecutionResult.Fatal)
            assertEquals(SyncErrorCategory.SERVER_ERROR, (fatalResult as SyncExecutionResult.Fatal).errorCategory)
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.Failed)
        }

    // =========================================================================
    // 3. Upload Failures
    // =========================================================================

    @Test
    fun testUploadUnauthorizedTriggersAuthenticationRequired() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)

            // Create a local pending change
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = UUID.randomUUID().toString(),
                    entityType = "NOTE",
                    entityId = "101",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                ),
            )

            // Make Drive upload fail with 401 Unauthorized
            fakeDriveApiClient.simulateUploadUnauthorized = true

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Fatal)
            assertEquals(SyncErrorCategory.AUTHENTICATION, (result as SyncExecutionResult.Fatal).errorCategory)
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.AuthenticationRequired)
        }

    @Test
    fun testUploadRateLimitedTriggersRetry() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)

            // Create a local pending change
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = UUID.randomUUID().toString(),
                    entityType = "NOTE",
                    entityId = "101",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                ),
            )

            fakeDriveApiClient.uploadRateLimitAttempts = 5

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Retry)
            assertEquals(SyncErrorCategory.RATE_LIMITED, (result as SyncExecutionResult.Retry).errorCategory)
            assertTrue(driveSyncStateStore.syncState.value is DriveSyncState.Retrying)
        }

    // =========================================================================
    // 4. End-to-End Successful Sync
    // =========================================================================

    @Test
    fun testSuccessfulFullSyncExecution() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail)

            // Add 2 pending changes
            val changeId1 = UUID.randomUUID().toString()
            val changeId2 = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId1,
                    entityType = "NOTE",
                    entityId = "101",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                ),
            )
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId2,
                    entityType = "NOTE",
                    entityId = "102",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                ),
            )

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)
            assertEquals(ListenableWorker.Result.success(), SyncWorker.mapResult(result))

            // Verify state is Completed
            val state = driveSyncStateStore.syncState.value
            assertTrue(state is DriveSyncState.Completed)

            // Verify timestamp was updated in preferences
            val prefs = driveSyncStateStore.getPreferences()
            assertTrue(prefs.lastSyncEpochMillis > 0L)
            assertEquals(null, prefs.lastErrorCategory)

            // Verify pending changes are now marked SYNCED
            assertEquals("SYNCED", fakePendingChangeDao.getById(changeId1)?.status)
            assertEquals("SYNCED", fakePendingChangeDao.getById(changeId2)?.status)

            // Verify journal was marked UPLOADED in Room DAO
            val journals = fakeJournalDao.getJournalsForDevice(localDeviceId)
            assertEquals(1, journals.size)
            assertEquals(JournalStatus.UPLOADED.name, journals[0].status)
        }

    // =========================================================================
    // 5. Concurrency, Lost Wake-up & Drain Semantics
    // =========================================================================

    @Test
    fun testMutationArrivingDuringActiveSyncIsDrainedAndUploaded() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            // 1. Initial pending change
            val changeId1 = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId1,
                    entityType = "NOTE",
                    entityId = "note-1",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{\"title\":\"Note 1\"}",
                    status = "PENDING",
                ),
            )

            // 2. Simulate a mutation arriving while sync is actively packaging/uploading
            val changeId2 = UUID.randomUUID().toString()
            fakeJournalDao.onInsertHook = {
                runBlocking {
                    fakePendingChangeDao.insert(
                        PendingChangeEntity(
                            changeId = changeId2,
                            entityType = "NOTE",
                            entityId = "note-2",
                            operationType = "CREATE",
                            deviceId = localDeviceId,
                            createdAt = System.currentTimeMillis() + 10,
                            schemaVersion = 1,
                            payload = "{\"title\":\"Note 2\"}",
                            status = "PENDING",
                        ),
                    )
                }
            }

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)

            // Both changes MUST be marked SYNCED via the post-upload drain pass
            val c1 = fakePendingChangeDao.getById(changeId1)
            val c2 = fakePendingChangeDao.getById(changeId2)
            assertNotNull(c1)
            assertEquals("SYNCED", c1?.status)
            assertNotNull(c2)
            assertEquals("SYNCED", c2?.status)

            // Two distinct journals were packaged and uploaded without lost wake-up
            assertEquals(2, fakeJournalDao.journals.size)
            assertTrue(fakeJournalDao.journals.values.all { it.status == JournalStatus.UPLOADED.name })
        }

    @Test
    fun testConcurrentExecutionSequentiallySerializedWithoutLostWakeup() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "note-concurrent",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{\"title\":\"Concurrent Note\"}",
                    status = "PENDING",
                ),
            )

            val pair =
                coroutineScope {
                    val job1 = async(Dispatchers.IO) { coordinator.executeSync(runAttempt = 0) }
                    val job2 = async(Dispatchers.IO) { coordinator.executeSync(runAttempt = 0) }
                    Pair(job1.await(), job2.await())
                }
            val res1 = pair.first
            val res2 = pair.second

            assertTrue(res1 is SyncExecutionResult.Success)
            assertTrue(res2 is SyncExecutionResult.Success)

            val change = fakePendingChangeDao.getById(changeId)
            assertEquals("SYNCED", change?.status)
        }

    // =========================================================================
    // 6. Account Isolation & Pending Changes Preservation
    // =========================================================================

    @Test
    fun testAccountSwitchingMidSyncAbortsAndPreservesPendingData() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "note-isolated",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{\"title\":\"Account Isolation Note\"}",
                    status = "PENDING",
                ),
            )

            // Simulate user switching accounts in settings while sync is active
            fakeJournalDao.onInsertHook = {
                runBlocking {
                    drivePrefs.setConnected("switched_user@example.com", "account_2")
                }
            }

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Fatal)
            val fatal = result as SyncExecutionResult.Fatal
            assertEquals(SyncErrorCategory.ACCOUNT_MISMATCH, fatal.errorCategory)

            // Pending change MUST NOT be marked SYNCED and MUST NOT be deleted
            val change = fakePendingChangeDao.getById(changeId)
            assertNotNull(change)
            assertEquals("PENDING", change?.status)
        }

    @Test
    fun testSignOutBeforeUploadAbortsAndPreservesPendingData() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "note-signout",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{\"title\":\"Signout Note\"}",
                    status = "PENDING",
                ),
            )

            fakeJournalDao.onInsertHook = {
                runBlocking {
                    drivePrefs.setDisconnected()
                }
            }

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Fatal)
            val fatal = result as SyncExecutionResult.Fatal
            assertEquals(SyncErrorCategory.ACCOUNT_MISMATCH, fatal.errorCategory)

            // Pending change preserved
            val change = fakePendingChangeDao.getById(changeId)
            assertNotNull(change)
            assertEquals("PENDING", change?.status)
        }

    // =========================================================================
    // 7. Retry, Rate Limiting & Sequence Safety
    // =========================================================================

    @Test
    fun testHttp429WithLongRetryAfterSetsStateAndDefersSubsequentExecutions() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            fakeDriveApiClient.rateLimitAttempts = 5
            fakeDriveApiClient.retryAfterSecondsHeader = 300L // 5 minutes

            val beforeTime = System.currentTimeMillis()
            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Retry)
            val retry = result as SyncExecutionResult.Retry
            assertEquals(SyncErrorCategory.RATE_LIMITED, retry.errorCategory)
            assertEquals(300L, retry.retryAfterSeconds)

            // Verify state store recorded expiration
            val rateLimitExpiresAt = driveSyncStateStore.getRateLimitExpiresAt()
            assertTrue(rateLimitExpiresAt >= beforeTime + 300_000L)

            // Verify worker mapping schedules delayed one-time work
            val workerResult = SyncWorker.mapResult(result, fakeContext)
            assertEquals(ListenableWorker.Result.success(), workerResult)

            // Immediate subsequent call is blocked by rate limit without calling API
            val secondResult = coordinator.executeSync(runAttempt = 0)
            assertTrue(secondResult is SyncExecutionResult.Retry)
            assertEquals(SyncErrorCategory.RATE_LIMITED, (secondResult as SyncExecutionResult.Retry).errorCategory)
            assertTrue(secondResult.retryAfterSeconds != null && secondResult.retryAfterSeconds!! <= 300L)
        }

    @Test
    fun testProcessDeathAndWorkerRecreationRecoversExistingReadyJournal() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            // Simulate journal created by previous worker before process was killed
            val journalId = UUID.randomUUID().toString()
            val journalFile = File(fakeContext.filesDir, "sync_journals/journal_${localDeviceId}_1_$journalId.bin")
            journalFile.parentFile?.mkdirs()
            journalFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "recovered-note",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "JOURNALED",
                    journalId = journalId,
                ),
            )

            fakeJournalDao.insert(
                SyncJournalEntity(
                    journalId = journalId,
                    deviceId = localDeviceId,
                    sequence = 1L,
                    formatVersion = 1,
                    createdAt = System.currentTimeMillis(),
                    operationCount = 1,
                    storagePath = journalFile.absolutePath,
                    encryptedSizeBytes = 5L,
                    status = JournalStatus.READY_TO_UPLOAD.name,
                ),
            )

            // Next worker runs executeSync
            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)

            // The existing ready journal was uploaded
            val uploadedJournal = fakeJournalDao.getById(journalId)
            assertNotNull(uploadedJournal)
            assertEquals(JournalStatus.UPLOADED.name, uploadedJournal?.status)

            // The journaled change was marked SYNCED
            val syncedChange = fakePendingChangeDao.getById(changeId)
            assertNotNull(syncedChange)
            assertEquals("SYNCED", syncedChange?.status)
        }

    @Test
    fun testRetryAfterPartialUploadDoesNotDuplicateAlreadyUploadedJournal() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            val j1Id = UUID.randomUUID().toString()
            val j1File = File(fakeContext.filesDir, "sync_journals/journal_${localDeviceId}_1_$j1Id.bin")
            j1File.parentFile?.mkdirs()
            j1File.writeBytes(byteArrayOf(1, 2, 3))

            val j2Id = UUID.randomUUID().toString()
            val j2File = File(fakeContext.filesDir, "sync_journals/journal_${localDeviceId}_2_$j2Id.bin")
            j2File.parentFile?.mkdirs()
            j2File.writeBytes(byteArrayOf(4, 5, 6))

            fakeJournalDao.insert(
                SyncJournalEntity(
                    journalId = j1Id,
                    deviceId = localDeviceId,
                    sequence = 1L,
                    formatVersion = 1,
                    createdAt = System.currentTimeMillis(),
                    operationCount = 1,
                    storagePath = j1File.absolutePath,
                    encryptedSizeBytes = 3L,
                    status = JournalStatus.READY_TO_UPLOAD.name,
                ),
            )
            fakeJournalDao.insert(
                SyncJournalEntity(
                    journalId = j2Id,
                    deviceId = localDeviceId,
                    sequence = 2L,
                    formatVersion = 1,
                    createdAt = System.currentTimeMillis() + 10,
                    operationCount = 1,
                    storagePath = j2File.absolutePath,
                    encryptedSizeBytes = 3L,
                    status = JournalStatus.READY_TO_UPLOAD.name,
                ),
            )

            // Simulate 2nd upload failure (J1 succeeds, J2 fails with 500)
            fakeDriveApiClient.failOnUploadIndex = 2

            val res1 = coordinator.executeSync(runAttempt = 0)
            assertTrue("Expected Retry but got $res1", res1 is SyncExecutionResult.Retry)

            // J1 was uploaded, J2 failed
            assertEquals(JournalStatus.UPLOADED.name, fakeJournalDao.getById(j1Id)?.status)
            assertEquals(JournalStatus.FAILED.name, fakeJournalDao.getById(j2Id)?.status)

            // Reset J2 to READY_TO_UPLOAD and clear failure trigger for retry
            fakeDriveApiClient.failOnUploadIndex = null
            fakeJournalDao.update(fakeJournalDao.getById(j2Id)!!.copy(status = JournalStatus.READY_TO_UPLOAD.name))

            // Run next sync
            val res2 = coordinator.executeSync(runAttempt = 1)
            assertTrue(res2 is SyncExecutionResult.Success)

            // Both are uploaded now
            assertEquals(JournalStatus.UPLOADED.name, fakeJournalDao.getById(j1Id)?.status)
            assertEquals(JournalStatus.UPLOADED.name, fakeJournalDao.getById(j2Id)?.status)
        }

    @Test
    fun testInvalidJournalQuarantinedDoesNotAdvanceWatermarkAndBlocksDependentSequence() =
        runBlocking {
            assumeDriveSyncSupported()
            drivePrefs.setConnected(testEmail, "account_1")

            val peerDeviceId = "peer_device_xyz"

            // Ingest inbox setup: seq 1 has corrupt data, seq 2 is valid
            fakeInboxDao.insert(
                SyncInboxEntity(
                    journalId = "corrupt_j1",
                    deviceId = peerDeviceId,
                    sequence = 1L,
                    formatVersion = 1,
                    driveFileId = "drive_corrupt_1",
                    localFilePath = "/non_existent/path/corrupt.bin",
                    contentSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                    operationCount = 1,
                    downloadedAt = System.currentTimeMillis(),
                    status = InboxStatus.VALIDATED,
                ),
            )
            fakeInboxDao.insert(
                SyncInboxEntity(
                    journalId = "valid_j2",
                    deviceId = peerDeviceId,
                    sequence = 2L,
                    formatVersion = 1,
                    driveFileId = "drive_valid_2",
                    localFilePath = "/non_existent/path/valid.bin",
                    contentSha256 = "1111111111111111111111111111111111111111111111111111111111111111",
                    operationCount = 1,
                    downloadedAt = System.currentTimeMillis() + 10,
                    status = InboxStatus.VALIDATED,
                ),
            )

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)

            // Seq 1 must be marked FAILED
            val j1 = fakeInboxDao.getByJournalId("corrupt_j1")
            assertEquals(InboxStatus.FAILED, j1?.status)

            // Seq 2 was NOT ingested because seq 1 failed (cannot skip sequence gap)
            val j2 = fakeInboxDao.getByJournalId("valid_j2")
            assertEquals(InboxStatus.VALIDATED, j2?.status)

            // Watermark remains at 0
            val watermark = fakeWatermarkDao.getWatermark(peerDeviceId)
            val highestApplied = watermark?.highestAppliedSeq ?: 0L
            assertEquals(0L, highestApplied)
        }

    // =========================================================================
    // 6. Account Isolation & Pending Changes Preservation
    // =========================================================================

    @Test
    fun testAccountDisconnectPreservesPendingLocalChanges() =
        runBlocking {
            drivePrefs.setConnected(testEmail)

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "999",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                ),
            )

            // Simulate disconnect / sign-out
            drivePrefs.setDisconnected()
            driveSyncStateStore.clear()
            SyncWorkScheduler.cancel(fakeContext)

            // Pending change MUST NOT be deleted
            val preserved = fakePendingChangeDao.getById(changeId)
            assertNotNull(preserved)
            assertEquals("PENDING", preserved?.status)
            assertEquals("999", preserved?.entityId)
        }

    // =========================================================================
    // 7. Scheduler API Safety & Flavor Isolation
    // =========================================================================

    @Test
    fun testSchedulerMethodsDoNotCrashWithoutWorkManagerRuntime() {
        SyncWorkScheduler.schedulePeriodicSync(fakeContext, frequencyMinutes = 15L)
        SyncWorkScheduler.scheduleImmediateSync(fakeContext, debounceSeconds = 10L)
        SyncWorkScheduler.cancel(fakeContext)
        assertEquals(haddad.three.notas.BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED, SyncWorkScheduler.isGoogleDriveSyncSupported())
    }

    @Test
    fun testOfflineFlavorDisablesSync() =
        runBlocking {
            if (!haddad.three.notas.BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED) {
                assertFalse(SyncWorkScheduler.isGoogleDriveSyncSupported())
                val result = coordinator.executeSync()
                assertTrue(result is SyncExecutionResult.Skipped)
                assertEquals(ListenableWorker.Result.success(), SyncWorker.mapResult(result))
            }
        }

    // =========================================================================
    // Test Doubles / Fakes
    // =========================================================================

    private class FakeSyncJournalDao : SyncJournalDao {
        val journals = mutableMapOf<String, SyncJournalEntity>()
        var onInsertHook: (() -> Unit)? = null

        override suspend fun insert(journal: SyncJournalEntity): Long {
            journals[journal.journalId] = journal
            onInsertHook?.invoke()
            onInsertHook = null
            return 1L
        }

        override suspend fun update(journal: SyncJournalEntity) {
            journals[journal.journalId] = journal
        }

        override suspend fun getById(journalId: String): SyncJournalEntity? = journals[journalId]

        override suspend fun getByDeviceAndSequence(
            deviceId: String,
            sequence: Long,
        ): SyncJournalEntity? = journals.values.find { it.deviceId == deviceId && it.sequence == sequence }

        override suspend fun getByStatus(status: String): List<SyncJournalEntity> = journals.values.filter { it.status == status }

        override suspend fun getReadyToUpload(): List<SyncJournalEntity> = journals.values.filter { it.status == JournalStatus.READY_TO_UPLOAD.name }

        override suspend fun getJournalsForDevice(deviceId: String): List<SyncJournalEntity> = journals.values.filter { it.deviceId == deviceId }

        override fun observeAllJournals(): Flow<List<SyncJournalEntity>> = emptyFlow()

        override suspend fun getMaxPublishedSequence(deviceId: String): Long? = journals.values.filter { it.deviceId == deviceId }.maxOfOrNull { it.sequence }

        override suspend fun getMaxCommittedSequence(deviceId: String): Long? = journals.values.filter { it.deviceId == deviceId && it.status in listOf(JournalStatus.READY_TO_UPLOAD.name, JournalStatus.UPLOADED.name) }.maxOfOrNull { it.sequence }

        override suspend fun getCreatedJournals(deviceId: String): List<SyncJournalEntity> = journals.values.filter { it.deviceId == deviceId && it.status == JournalStatus.CREATED.name }

        override suspend fun markUploaded(
            journalId: String,
            driveFileId: String,
            newStatus: String,
            timestamp: Long,
        ): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, driveFileId = driveFileId, updatedAt = timestamp)
            return 1
        }

        override suspend fun getByDriveFileId(driveFileId: String): SyncJournalEntity? = journals.values.find { it.driveFileId == driveFileId }

        override suspend fun updateStatus(
            journalId: String,
            newStatus: String,
            timestamp: Long,
        ): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, updatedAt = timestamp)
            return 1
        }

        override suspend fun recordUploadFailure(
            journalId: String,
            newStatus: String,
            error: String?,
            timestamp: Long,
        ): Int {
            val j = journals[journalId] ?: return 0
            journals[journalId] = j.copy(status = newStatus, uploadAttempts = j.uploadAttempts + 1, lastError = error, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteById(journalId: String): Int = if (journals.remove(journalId) != null) 1 else 0

        override suspend fun clearAll() {
            journals.clear()
        }
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

        override suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity> = changes.values.filter { it.status == "PENDING" }.take(limit)

        override suspend fun getAllPendingChanges(): List<PendingChangeEntity> = changes.values.filter { it.status == "PENDING" }

        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = emptyFlow()

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

    private class FakeSyncSequenceDao : SyncSequenceDao {
        val sequences = mutableMapOf<String, Long>()

        override suspend fun getSequence(deviceId: String): SyncSequenceEntity? {
            val seq = sequences[deviceId] ?: return null
            return SyncSequenceEntity(deviceId, seq, System.currentTimeMillis())
        }

        override suspend fun getLastAllocatedSequence(deviceId: String): Long? = sequences[deviceId]

        override suspend fun insertOrUpdate(entity: SyncSequenceEntity) {
            sequences[entity.deviceId] = entity.lastAllocatedSequence
        }

        override suspend fun setLastAllocatedSequence(
            deviceId: String,
            sequence: Long,
            timestamp: Long,
        ): Int {
            sequences[deviceId] = sequence
            return 1
        }

        override suspend fun allocateNextSequence(
            deviceId: String,
            timestamp: Long,
        ): Long {
            val current = sequences[deviceId] ?: 0L
            val next = current + 1L
            sequences[deviceId] = next
            return next
        }

        override suspend fun deleteForDevice(deviceId: String): Int = if (sequences.remove(deviceId) != null) 1 else 0

        override suspend fun clearAll() {
            sequences.clear()
        }
    }

    private class FakeSyncInboxDao : SyncInboxDao {
        val inbox = mutableMapOf<String, SyncInboxEntity>()

        override suspend fun insert(entry: SyncInboxEntity): Long {
            inbox[entry.journalId] = entry
            return 1L
        }

        override suspend fun update(entry: SyncInboxEntity) {
            inbox[entry.journalId] = entry
        }

        override suspend fun updateStatus(
            journalId: String,
            status: String,
        ): Int {
            val entry = inbox[journalId] ?: return 0
            inbox[journalId] = entry.copy(status = status)
            return 1
        }

        override suspend fun getByJournalId(journalId: String): SyncInboxEntity? = inbox[journalId]

        override suspend fun getByDeviceAndSequence(
            deviceId: String,
            sequence: Long,
        ): SyncInboxEntity? = inbox.values.find { it.deviceId == deviceId && it.sequence == sequence }

        override suspend fun getValidatedEntries(): List<SyncInboxEntity> = inbox.values.filter { it.status == InboxStatus.VALIDATED }

        override suspend fun getActiveDevicesForIngestion(): List<String> =
            inbox.values
                .filter { it.status in listOf(InboxStatus.VALIDATED, InboxStatus.APPLYING) }
                .map { it.deviceId }
                .distinct()

        override suspend fun getPendingEntriesForDevice(deviceId: String): List<SyncInboxEntity> = inbox.values.filter { it.deviceId == deviceId && it.status in listOf(InboxStatus.VALIDATED, InboxStatus.APPLYING) }.sortedBy { it.sequence }

        override suspend fun getEntriesForDevice(deviceId: String): List<SyncInboxEntity> = inbox.values.filter { it.deviceId == deviceId }

        override suspend fun getMaxValidatedSequence(deviceId: String): Long? = inbox.values.filter { it.deviceId == deviceId && it.status == InboxStatus.VALIDATED }.maxOfOrNull { it.sequence }

        override suspend fun getAllEntries(): List<SyncInboxEntity> = inbox.values.toList()

        override suspend fun deleteByJournalId(journalId: String): Int = if (inbox.remove(journalId) != null) 1 else 0

        override suspend fun clearAll() {
            inbox.clear()
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
            val existing =
                watermarks[deviceId] ?: SyncWatermarkEntity(
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

        override suspend fun clearAll() {
            watermarks.clear()
        }
    }

    private class FakeSyncAppliedOperationDao : SyncAppliedOperationDao {
        val ops = mutableMapOf<String, SyncAppliedOperationEntity>()

        override suspend fun insert(operation: SyncAppliedOperationEntity): Long {
            ops[operation.changeId] = operation
            return 1L
        }

        override suspend fun getByChangeId(changeId: String): SyncAppliedOperationEntity? = ops[changeId]

        override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> = ops.values.filter { it.journalId == journalId }

        override suspend fun isApplied(changeId: String): Boolean = ops.containsKey(changeId)

        override suspend fun clearAll() {
            ops.clear()
        }
    }

    private class FakeSyncConflictMetadataDao : SyncConflictMetadataDao {
        val meta = mutableMapOf<Triple<String, String, String>, SyncConflictMetadataEntity>()

        override suspend fun insertOrUpdate(metadata: SyncConflictMetadataEntity) {
            this.meta[Triple(metadata.entityType, metadata.entityId, metadata.fieldName)] = metadata
        }

        override suspend fun getFieldMetadata(
            entityType: String,
            entityId: String,
            fieldName: String,
        ): SyncConflictMetadataEntity? = meta[Triple(entityType, entityId, fieldName)]

        override suspend fun getAllFieldsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictMetadataEntity> = meta.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun deleteForEntity(
            entityType: String,
            entityId: String,
        ): Int {
            val keysToRemove = meta.keys.filter { it.first == entityType && it.second == entityId }
            keysToRemove.forEach { meta.remove(it) }
            return keysToRemove.size
        }

        override suspend fun clearAll() {
            meta.clear()
        }
    }

    private class FakeSyncTombstoneDao : SyncTombstoneDao {
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

    private class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            if (records.any { it.conflictId == record.conflictId }) return -1L
            records.add(record)
            return records.size.toLong()
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? = records.find { it.conflictId == conflictId }

        override suspend fun getConflictsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictRecordEntity> = records.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> = records.filter { !it.resolved }

        override suspend fun markResolved(id: Long): Int = 1

        override suspend fun clearAll() {
            records.clear()
        }
    }

    private class FakeNoteDao : haddad.three.notas.data.UuidBaseFakeNoteDao()

    private class FakeChecklistItemDao : haddad.three.notas.data.UuidBaseFakeChecklistItemDao()

    private class FakeShoulderDao : haddad.three.notas.data.UuidBaseFakeShoulderDao()

    private class FakeAttachmentDao : haddad.three.notas.data.UuidBaseFakeAttachmentDao()

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var authorizeResult: GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success(
                accountEmail = "test@example.com",
                accessToken = "fake_access_token_123",
            )

        override suspend fun authorize(
            context: Context,
            forceAccountSelection: Boolean,
        ): GoogleDriveAuthorizationResult = authorizeResult

        override suspend fun parseConsentResult(
            context: Context,
            data: android.content.Intent?,
        ): GoogleDriveAuthorizationResult = authorizeResult

        override suspend fun disconnect(
            context: Context,
            accountEmail: String?,
            accessToken: String?,
        ) {}

        override suspend fun invalidateToken(
            context: Context,
            token: String,
        ) {}
    }

    private class FakeGoogleDriveApiClient {
        var simulateUnauthorized = false
        var simulateOffline = false
        var simulateServerError = false
        var rateLimitAttempts = 0
        var serverErrorAttempts = 0
        var retryAfterSecondsHeader: Long? = null
        var simulateUploadUnauthorized = false
        var uploadRateLimitAttempts = 0
        var uploadCount = 0
        var failOnUploadIndex: Int? = null
        val files = mutableMapOf<String, DriveEntry>()
        private var idCounter = 1000
        private var pendingUploadId: String? = null

        data class DriveEntry(
            val id: String,
            val name: String,
            val bytes: ByteArray,
            val appProperties: Map<String, String>,
        )

        fun asClient(): GoogleDriveApiClient {
            return GoogleDriveApiClient(
                httpFactory = { urlString ->
                    if (simulateOffline) {
                        throw java.io.IOException("Network offline")
                    }
                    object : HttpURLConnection(URL(urlString)) {
                        override fun getResponseCode(): Int {
                            if (simulateUnauthorized) return HTTP_UNAUTHORIZED
                            if (urlString.contains("uploadType=multipart")) {
                                if (simulateUploadUnauthorized) return HTTP_UNAUTHORIZED
                                if (requestMethod == "POST") {
                                    if (failOnUploadIndex != null && uploadCount >= failOnUploadIndex!!) {
                                        return 500
                                    }
                                }
                                if (uploadRateLimitAttempts > 0) {
                                    uploadRateLimitAttempts--
                                    return 429
                                }
                            }
                            if (rateLimitAttempts > 0) {
                                rateLimitAttempts--
                                return 429
                            }
                            if (simulateServerError) {
                                return 500
                            }
                            if (serverErrorAttempts > 0) {
                                serverErrorAttempts--
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
                            if (simulateUnauthorized || (urlString.contains("uploadType=multipart") && simulateUploadUnauthorized)) {
                                return ByteArrayInputStream(byteArrayOf())
                            }
                            if (urlString.contains("/files?")) {
                                if (requestMethod == "POST") {
                                    val id = pendingUploadId ?: "drive_file_${idCounter++}"
                                    pendingUploadId = null
                                    return ByteArrayInputStream("""{"id":"$id","name":"uploaded"}""".toByteArray())
                                } else {
                                    val sortedEntries = files.values.sortedBy { it.id }
                                    val filesJson =
                                        sortedEntries.joinToString(",") { entry ->
                                            val propsJson = entry.appProperties.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
                                            """{"id":"${entry.id}","name":"${entry.name}","size":"${entry.bytes.size}","appProperties":{$propsJson}}"""
                                        }
                                    return ByteArrayInputStream("""{"files":[$filesJson]}""".toByteArray())
                                }
                            } else if (urlString.contains("alt=media")) {
                                val fileId = urlString.substringAfter("/files/").substringBefore("?")
                                val entry = files[fileId] ?: files.values.find { it.id == fileId }
                                return ByteArrayInputStream(entry?.bytes ?: byteArrayOf())
                            } else {
                                return ByteArrayInputStream("""{"id":"file","name":"file"}""".toByteArray())
                            }
                        }

                        override fun getOutputStream(): OutputStream {
                            if (urlString.contains("uploadType=multipart") && requestMethod == "POST") {
                                uploadCount++
                            }
                            return object : ByteArrayOutputStream() {
                                override fun close() {
                                    super.close()
                                    if (urlString.contains("uploadType=multipart")) {
                                        if (failOnUploadIndex != null && uploadCount >= failOnUploadIndex!!) {
                                            return
                                        }
                                        val body = String(toByteArray(), Charsets.UTF_8)
                                        val journalId = body.substringAfter("\"journalId\":\"", "").substringBefore("\"")
                                        val deviceId = body.substringAfter("\"deviceId\":\"", "").substringBefore("\"")
                                        val seq = body.substringAfter("\"sequence\":\"", "").substringBefore("\"")
                                        val hash = body.substringAfter("\"contentSha256\":\"", "").substringBefore("\"")
                                        val id = "drive_file_${idCounter++}"
                                        pendingUploadId = id
                                        files[id] =
                                            DriveEntry(
                                                id = id,
                                                name = "journal_${deviceId}_${seq}_$journalId.bin",
                                                bytes = byteArrayOf(1, 2, 3),
                                                appProperties =
                                                    mapOf(
                                                        "appId" to "notas",
                                                        "type" to "sync_journal",
                                                        "journalId" to journalId,
                                                        "deviceId" to deviceId,
                                                        "sequence" to seq,
                                                        "formatVersion" to "1",
                                                        "contentSha256" to hash,
                                                    ),
                                            )
                                    }
                                }
                            }
                        }

                        override fun disconnect() {}

                        override fun connect() {}

                        override fun usingProxy(): Boolean = false
                    }
                },
            )
        }
    }
}
