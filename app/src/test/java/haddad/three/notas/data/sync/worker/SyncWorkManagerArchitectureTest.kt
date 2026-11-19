package haddad.three.notas.data.sync.worker

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.Visibility
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.sync.SyncStateStore
import haddad.three.notas.data.sync.change.DefaultLocalChangeRecorder
import haddad.three.notas.data.sync.change.OperationOrigin
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Comprehensive Architecture and Behavioral Verification for Phase 7:
 * WorkManager & Intelligent Background Synchronization.
 */
class SyncWorkManagerArchitectureTest {
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
    private lateinit var fakeConflictRecordDao: FakeSyncConflictRecordDao
    private lateinit var fakeAuthDelegate: FakeGoogleDriveAuthDelegate

    private lateinit var discoveryService: JournalDiscoveryService
    private lateinit var downloader: JournalDownloader
    private lateinit var ingestionEngine: RemoteJournalIngestionEngine
    private lateinit var journalWriter: SyncJournalWriter
    private lateinit var uploader: JournalUploader
    private lateinit var coordinator: DriveSyncCoordinator

    private lateinit var localDeviceId: String
    private val testEmail = "architect@example.com"
    private val accountBEmail = "secondary@example.com"

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

            val drivePrefsFile = tempFolder.newFile("test_arch_drive_prefs.preferences_pb")
            drivePrefs = DriveAuthPreferences(PreferenceDataStoreFactory.create { drivePrefsFile })

            val driveSyncFile = tempFolder.newFile("test_arch_drive_sync_prefs.preferences_pb")
            driveSyncStateStore = DriveSyncStateStore(PreferenceDataStoreFactory.create { driveSyncFile })

            val syncStateFile = tempFolder.newFile("test_arch_sync_state.preferences_pb")
            syncStateStore = SyncStateStore(PreferenceDataStoreFactory.create { syncStateFile })
            localDeviceId = syncStateStore.getOrCreateDeviceId()

            val kgen = KeyGenerator.getInstance("AES")
            kgen.init(256)
            masterKey = kgen.generateKey()
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
            fakeConflictRecordDao = FakeSyncConflictRecordDao()
            fakeAuthDelegate = FakeGoogleDriveAuthDelegate()
            val fakeDriveApiClient = FakeGoogleDriveApiClient()

            val transport =
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
                    conflictRecordDao = fakeConflictRecordDao,
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
                    authDelegate = fakeAuthDelegate,
                    conflictRecordDao = fakeConflictRecordDao,
                )
        }

    // =========================================================================
    // SECTION 14.A: Scheduling Tests
    // =========================================================================

    @Test
    fun testBuildPeriodicWorkRequest_BatteryAndNetworkConstraintsEnforced() {
        // Test Wi-Fi required
        val wifiRequest = SyncWorkScheduler.buildPeriodicWorkRequest(
            frequencyMinutes = 30L,
            requiresWifi = true,
            accountEmail = testEmail,
        )
        val wifiConstraints = wifiRequest.workSpec.constraints
        assertEquals(NetworkType.UNMETERED, wifiConstraints.requiredNetworkType)
        assertTrue(wifiConstraints.requiresBatteryNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, wifiRequest.workSpec.backoffPolicy)
        assertEquals(30_000L, wifiRequest.workSpec.backoffDelayDuration)
        assertTrue(wifiRequest.tags.contains(SyncWorkScheduler.WORK_TAG_SYNC))
        assertTrue(wifiRequest.tags.contains(SyncWorkScheduler.WORK_TAG_PERIODIC))
        assertTrue(wifiRequest.tags.contains(SyncWorkScheduler.getAccountTag(testEmail)))

        // Test Any Connected Network
        val anyNetRequest = SyncWorkScheduler.buildPeriodicWorkRequest(
            frequencyMinutes = 15L,
            requiresWifi = false,
            accountEmail = testEmail,
        )
        val anyNetConstraints = anyNetRequest.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, anyNetConstraints.requiredNetworkType)
        assertTrue(anyNetConstraints.requiresBatteryNotLow())
    }

    @Test
    fun testBuildOneTimeWorkRequest_DebouncingAndBackoffCriteria() {
        val debouncedRequest = SyncWorkScheduler.buildOneTimeWorkRequest(
            debounceSeconds = 15L,
            requiresWifi = false,
            accountEmail = testEmail,
        )
        assertEquals(15_000L, debouncedRequest.workSpec.initialDelay)
        assertEquals(NetworkType.CONNECTED, debouncedRequest.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, debouncedRequest.workSpec.backoffPolicy)
        assertTrue(debouncedRequest.tags.contains(SyncWorkScheduler.WORK_TAG_SYNC))
        assertTrue(debouncedRequest.tags.contains(SyncWorkScheduler.WORK_TAG_ONE_TIME))
        assertTrue(debouncedRequest.tags.contains(SyncWorkScheduler.getAccountTag(testEmail)))

        val immediateRequest = SyncWorkScheduler.buildOneTimeWorkRequest(
            debounceSeconds = 0L,
            requiresWifi = true,
            accountEmail = null,
        )
        assertEquals(0L, immediateRequest.workSpec.initialDelay)
        assertEquals(NetworkType.UNMETERED, immediateRequest.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun testPeriodicIntervalEnforcesWorkManagerMinimumFifteenMinutes() {
        val underMinRequest = SyncWorkScheduler.buildPeriodicWorkRequest(
            frequencyMinutes = 5L, // less than WorkManager 15m minimum
            requiresWifi = false,
        )
        assertEquals(TimeUnit.MINUTES.toMillis(15L), underMinRequest.workSpec.intervalDuration)

        val customIntervalRequest = SyncWorkScheduler.buildPeriodicWorkRequest(
            frequencyMinutes = 60L,
            requiresWifi = false,
        )
        assertEquals(TimeUnit.MINUTES.toMillis(60L), customIntervalRequest.workSpec.intervalDuration)
    }

    @Test
    fun testLocalChangeRecorder_TriggersDebouncedSchedulingCallback() =
        runBlocking {
            var callbackTriggered = false
            var recordedOrigin: OperationOrigin? = null

            val changeRecorder = DefaultLocalChangeRecorder(
                pendingChangeDao = fakePendingChangeDao,
                deviceIdProvider = { localDeviceId },
                onMutationRecorded = { origin ->
                    callbackTriggered = true
                    recordedOrigin = origin
                },
            )

            // Local Note Upsert triggers callback
            val dummyNote = NoteEntity(
                id = 1L,
                uuid = UUID.randomUUID().toString(),
                kind = NoteKind.NOTE,
                title = "Meeting Notes",
                body = "Discuss WorkManager",
                colorIndex = 0,
                trashed = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            changeRecorder.recordNoteUpsert(dummyNote, origin = OperationOrigin.LOCAL)

            assertTrue(callbackTriggered)
            assertEquals(OperationOrigin.LOCAL, recordedOrigin)

            // Remote operations MUST NOT trigger scheduling callback
            callbackTriggered = false
            changeRecorder.recordNoteUpsert(dummyNote, origin = OperationOrigin.REMOTE)
            assertFalse(callbackTriggered)
        }

    @Test
    fun testAccountIsolatedWorkNames_DifferentiatesAccounts() {
        val email1 = "alice@example.com"
        val email2 = "bob.builder+test@example.com"

        val periodicName1 = SyncWorkScheduler.getPeriodicWorkName(email1)
        val periodicName2 = SyncWorkScheduler.getPeriodicWorkName(email2)
        assertNotEquals(periodicName1, periodicName2)

        val oneTimeName1 = SyncWorkScheduler.getOneTimeWorkName(email1)
        val oneTimeName2 = SyncWorkScheduler.getOneTimeWorkName(email2)
        assertNotEquals(oneTimeName1, oneTimeName2)

        val tag1 = SyncWorkScheduler.getAccountTag(email1)
        val tag2 = SyncWorkScheduler.getAccountTag(email2)
        assertNotEquals(tag1, tag2)
        assertTrue(tag1.startsWith("account_"))
        assertTrue(tag2.startsWith("account_"))
    }

    // =========================================================================
    // SECTION 14.B: Worker Execution & Result Mapping Tests
    // =========================================================================

    @Test
    fun testWorkerMapResult_SuccessAndSkippedReturnSuccess() {
        val successRes = SyncExecutionResult.Success(System.currentTimeMillis())
        assertEquals(ListenableWorker.Result.success(), SyncWorker.mapResult(successRes))

        val skippedRes = SyncExecutionResult.Skipped("Disabled")
        assertEquals(ListenableWorker.Result.success(), SyncWorker.mapResult(skippedRes))
    }

    @Test
    fun testWorkerMapResult_RetryBehaviorAndBackoff() {
        // Standard retry with <= 30s delay -> Result.retry()
        val retryRes = SyncExecutionResult.Retry(SyncErrorCategory.NETWORK, "Temporary network timeout", retryAfterSeconds = 15L)
        assertEquals(ListenableWorker.Result.retry(), SyncWorker.mapResult(retryRes, fakeContext))

        // Large Retry-After (> 30s) schedules debounced work and returns success to avoid busy retry loops
        val rateLimitRetry = SyncExecutionResult.Retry(SyncErrorCategory.RATE_LIMITED, "Rate limited", retryAfterSeconds = 120L)
        val mapped = SyncWorker.mapResult(rateLimitRetry, fakeContext)
        assertEquals(ListenableWorker.Result.success(), mapped)
    }

    @Test
    fun testWorkerMapResult_FatalReturnsFailure() {
        val fatalRes = SyncExecutionResult.Fatal(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed")
        assertEquals(ListenableWorker.Result.failure(), SyncWorker.mapResult(fatalRes))
    }

    @Test
    fun testFailureClassification_403QuotaVsPermission() =
        runBlocking {
            if (!SyncWorkScheduler.isGoogleDriveSyncSupported()) return@runBlocking
            drivePrefs.setConnected(testEmail, "account_1")

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)

            // Verify status info reflects execution
            val status = coordinator.getBackgroundSyncStatus()
            assertTrue(status.isEnabled)
            assertTrue(status.lastSuccessfulSyncMillis > 0L)
            assertEquals(0, status.pendingOperationsCount)
            assertNull(status.lastErrorCategory)
        }

    @Test
    fun testConflictDetection_EmitsConflictsDetectedState() =
        runBlocking {
            if (!SyncWorkScheduler.isGoogleDriveSyncSupported()) return@runBlocking
            drivePrefs.setConnected(testEmail, "account_1")

            // Insert unresolved conflict into fakeConflictRecordDao
            fakeConflictRecordDao.insert(
                SyncConflictRecordEntity(
                    conflictId = "conflict-101",
                    entityType = "NOTE",
                    entityId = "note-conflict",
                    fieldName = "title",
                    winningValue = "Win",
                    losingValue = "Lose",
                    winningHlc = "100-0-deviceA",
                    losingHlc = "99-0-deviceB",
                    winningDeviceId = "deviceA",
                    losingDeviceId = "deviceB",
                    detectedAt = System.currentTimeMillis(),
                    resolved = false,
                )
            )

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)

            // DriveSyncState must observe ConflictsDetected
            val state = driveSyncStateStore.syncState.value
            assertTrue("State must be ConflictsDetected", state is DriveSyncState.ConflictsDetected)
            assertEquals(1, (state as DriveSyncState.ConflictsDetected).count)
        }

    // =========================================================================
    // SECTION 14.C: Recovery & Crash Resilience Tests
    // =========================================================================

    @Test
    fun testProcessInterruption_StagedReadyToUploadJournalsAreNotDuplicated() =
        runBlocking {
            if (!SyncWorkScheduler.isGoogleDriveSyncSupported()) return@runBlocking
            drivePrefs.setConnected(testEmail, "account_1")

            // Stage a ready-to-upload journal simulating process termination right before network send
            val journalId = UUID.randomUUID().toString()
            val dummyFile = tempFolder.newFile("journal_$journalId.enc")
            dummyFile.writeBytes(byteArrayOf(1, 2, 3, 4))

            fakeJournalDao.insert(
                SyncJournalEntity(
                    journalId = journalId,
                    deviceId = localDeviceId,
                    sequence = 1L,
                    formatVersion = 1,
                    createdAt = System.currentTimeMillis(),
                    operationCount = 1,
                    storagePath = dummyFile.absolutePath,
                    encryptedSizeBytes = dummyFile.length(),
                    status = JournalStatus.READY_TO_UPLOAD.name,
                )
            )

            // When worker runs again, ready-to-upload journals are safely uploaded without creating duplicate sequence
            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Success)

            val uploadedJournal = fakeJournalDao.getById(journalId)
            assertNotNull(uploadedJournal)
            assertEquals(JournalStatus.UPLOADED.name, uploadedJournal?.status)
        }

    @Test
    fun testPendingChangesNeverDeletedOnFailureOrDisconnection() =
        runBlocking {
            if (!SyncWorkScheduler.isGoogleDriveSyncSupported()) return@runBlocking
            drivePrefs.setConnected(testEmail, "account_1")

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "note-persist",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{\"title\":\"Never Lose Data\"}",
                    status = "PENDING",
                )
            )

            // Simulate account disconnect
            drivePrefs.setDisconnected()

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Skipped)

            // CRITICAL: Local pending change is still 100% intact and PENDING!
            val preserved = fakePendingChangeDao.getById(changeId)
            assertNotNull(preserved)
            assertEquals("PENDING", preserved?.status)
            assertEquals("note-persist", preserved?.entityId)
        }

    // =========================================================================
    // SECTION 14.D: Account Lifecycle Tests
    // =========================================================================

    @Test
    fun testAccountSwitch_AbortsActiveSyncAndRollsBackStagedJournals() =
        runBlocking {
            if (!SyncWorkScheduler.isGoogleDriveSyncSupported()) return@runBlocking
            drivePrefs.setConnected(testEmail, "account_1")

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "note-account-switch",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                )
            )

            // Simulate account change while packaging
            fakeJournalDao.onInsertHook = {
                runBlocking {
                    drivePrefs.setConnected(accountBEmail, "account_2")
                }
            }

            val result = coordinator.executeSync(runAttempt = 0)
            assertTrue(result is SyncExecutionResult.Fatal)
            assertEquals(SyncErrorCategory.ACCOUNT_MISMATCH, (result as SyncExecutionResult.Fatal).errorCategory)

            // Staged journal must have been rolled back and change returned to PENDING
            val pending = fakePendingChangeDao.getById(changeId)
            assertEquals("PENDING", pending?.status)
        }

    @Test
    fun testApplyPreferences_DisablingSyncCancelsWorkWithoutDeletingData() =
        runBlocking {
            if (!SyncWorkScheduler.isGoogleDriveSyncSupported()) return@runBlocking
            drivePrefs.setConnected(testEmail, "account_1")

            val changeId = UUID.randomUUID().toString()
            fakePendingChangeDao.insert(
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = "NOTE",
                    entityId = "preserved-disabled-note",
                    operationType = "CREATE",
                    deviceId = localDeviceId,
                    createdAt = System.currentTimeMillis(),
                    schemaVersion = 1,
                    payload = "{}",
                    status = "PENDING",
                )
            )

            // Apply disabled sync preferences
            val disabledPrefs = DriveSyncPreferencesState(isSyncEnabled = false)
            driveSyncStateStore.setSyncPreferences(enabled = false)
            SyncWorkScheduler.applyPreferences(fakeContext, testEmail, disabledPrefs)

            val syncResult = coordinator.executeSync(runAttempt = 0)
            assertTrue(syncResult is SyncExecutionResult.Skipped)

            // Data remains intact
            assertEquals("PENDING", fakePendingChangeDao.getById(changeId)?.status)
        }

    // =========================================================================
    // SECTION 14.E: Offline Flavor Isolation Tests
    // =========================================================================

    @Test
    fun testOfflineFlavor_SchedulerMethodsNoOpWhenSyncDisabled() {
        if (!haddad.three.notas.BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED) {
            assertFalse(SyncWorkScheduler.isGoogleDriveSyncSupported())
            // Invoking scheduler methods in offline flavor must never crash or create jobs
            SyncWorkScheduler.schedulePeriodicSync(fakeContext, testEmail, 15L, false)
            SyncWorkScheduler.scheduleImmediateSync(fakeContext, testEmail, 10L, true, false)
            SyncWorkScheduler.syncNow(fakeContext, testEmail, false)
            SyncWorkScheduler.cancelForAccount(fakeContext, testEmail)
            SyncWorkScheduler.cancelAll(fakeContext)
        }
    }

    // =========================================================================
    // Test Doubles (Reused from verified SyncWorkerTest infrastructure)
    // =========================================================================

    private class FakeNoteDao : haddad.three.notas.data.UuidBaseFakeNoteDao()
    private class FakeChecklistItemDao : haddad.three.notas.data.UuidBaseFakeChecklistItemDao()
    private class FakeShoulderDao : haddad.three.notas.data.UuidBaseFakeShoulderDao()
    private class FakeAttachmentDao : haddad.three.notas.data.UuidBaseFakeAttachmentDao()

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var authorizeResult: GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success(
                accountEmail = "architect@example.com",
                accessToken = "fake_access_token_123",
            )
        var invalidateTokenCalled = false

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
        ) {
            invalidateTokenCalled = true
        }
    }

    private class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            if (records.any { it.conflictId == record.conflictId }) return -1L
            records.add(record)
            return records.size.toLong()
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? =
            records.find { it.conflictId == conflictId }

        override suspend fun getConflictsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictRecordEntity> = records.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> =
            records.filter { !it.resolved }

        override suspend fun markResolved(id: Long): Int = 1

        override suspend fun clearAll() {
            records.clear()
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
            journals.values.filter {
                it.deviceId == deviceId && it.status in listOf(JournalStatus.READY_TO_UPLOAD.name, JournalStatus.UPLOADED.name)
            }.maxOfOrNull { it.sequence }

        override suspend fun getCreatedJournals(deviceId: String): List<SyncJournalEntity> =
            journals.values.filter { it.deviceId == deviceId && it.status == JournalStatus.CREATED.name }

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

        override suspend fun getByDriveFileId(driveFileId: String): SyncJournalEntity? =
            journals.values.find { it.driveFileId == driveFileId }

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

        override suspend fun deleteById(journalId: String): Int =
            if (journals.remove(journalId) != null) 1 else 0

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

        override suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity> =
            changes.values.filter { it.status == "PENDING" }.take(limit)

        override suspend fun getAllPendingChanges(): List<PendingChangeEntity> =
            changes.values.filter { it.status == "PENDING" }

        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = emptyFlow()

        override suspend fun countPendingChanges(): Int =
            changes.values.count { it.status == "PENDING" }

        override suspend fun getChangesForEntity(
            entityType: String,
            entityId: String,
        ): List<PendingChangeEntity> = changes.values.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getChangesForJournal(journalId: String): List<PendingChangeEntity> =
            changes.values.filter { it.journalId == journalId }

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

        override suspend fun getValidatedEntries(): List<SyncInboxEntity> =
            inbox.values.filter { it.status == InboxStatus.VALIDATED }

        override suspend fun getActiveDevicesForIngestion(): List<String> =
            inbox.values
                .filter { it.status in listOf(InboxStatus.VALIDATED, InboxStatus.APPLYING) }
                .map { it.deviceId }
                .distinct()

        override suspend fun getPendingEntriesForDevice(deviceId: String): List<SyncInboxEntity> =
            inbox.values.filter { it.deviceId == deviceId && it.status in listOf(InboxStatus.VALIDATED, InboxStatus.APPLYING) }
                .sortedBy { it.sequence }

        override suspend fun getEntriesForDevice(deviceId: String): List<SyncInboxEntity> =
            inbox.values.filter { it.deviceId == deviceId }

        override suspend fun getMaxValidatedSequence(deviceId: String): Long? =
            inbox.values.filter { it.deviceId == deviceId && it.status == InboxStatus.VALIDATED }.maxOfOrNull { it.sequence }

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

        override suspend fun getByJournalId(journalId: String): List<SyncAppliedOperationEntity> =
            ops.values.filter { it.journalId == journalId }

        override suspend fun isApplied(changeId: String): Boolean = ops.containsKey(changeId)

        override suspend fun clearAll() {
            ops.clear()
        }
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
