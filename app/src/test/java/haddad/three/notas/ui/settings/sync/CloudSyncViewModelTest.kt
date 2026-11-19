package haddad.three.notas.ui.settings.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import haddad.three.notas.BuildConfig
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
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.conflict.ConflictResolutionManager
import haddad.three.notas.data.sync.conflict.FieldConflictResolver
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationDao
import haddad.three.notas.data.sync.conflict.SyncAppliedOperationEntity
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataEntity
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.data.sync.conflict.SyncTombstoneDao
import haddad.three.notas.data.sync.conflict.SyncTombstoneEntity
import haddad.three.notas.data.sync.history.SyncHistoryRecordDao
import haddad.three.notas.data.sync.history.SyncHistoryRecordEntity
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
import haddad.three.notas.data.sync.worker.DriveSyncCoordinator
import haddad.three.notas.data.sync.worker.DriveSyncState
import haddad.three.notas.data.sync.worker.DriveSyncStateStore
import haddad.three.notas.data.sync.worker.SyncWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudSyncViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeContext: Context
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var driveSyncStateStore: DriveSyncStateStore
    private lateinit var syncStateStore: SyncStateStore
    private lateinit var mekManager: MekManager
    private lateinit var masterKey: SecretKey

    private lateinit var fakePendingChangeDao: FakePendingChangeDao
    private lateinit var fakeConflictRecordDao: FakeSyncConflictRecordDao
    private lateinit var fakeConflictMetadataDao: FakeSyncConflictMetadataDao
    private lateinit var fakeTombstoneDao: FakeSyncTombstoneDao
    private lateinit var fakeHistoryDao: FakeSyncHistoryRecordDao
    private lateinit var fakeNoteDao: FakeNoteDao
    private lateinit var fakeAuthDelegate: FakeGoogleDriveAuthDelegate
    private lateinit var conflictResolutionManager: ConflictResolutionManager

    private lateinit var coordinator: DriveSyncCoordinator
    private lateinit var syncWorkScheduler: SyncWorkScheduler
    private lateinit var viewModel: CloudSyncViewModel
    private var collectJob: kotlinx.coroutines.Job? = null

    private val testEmail = "testuser@example.com"
    private val localDeviceId = "dev-vm-1"

    @Before
    fun setUp() =
        runBlocking {
            Dispatchers.setMain(testDispatcher)
            val fakeFilesDir = tempFolder.newFolder("files")
            val fakeCacheDir = tempFolder.newFolder("cache")
            fakeContext =
                object : ContextWrapper(null) {
                    override fun getApplicationContext(): Context = this

                    override fun getFilesDir(): File = fakeFilesDir

                    override fun getCacheDir(): File = fakeCacheDir
                }

            val drivePrefsFile = tempFolder.newFile("test_vm_drive_prefs.preferences_pb")
            drivePrefs = DriveAuthPreferences(PreferenceDataStoreFactory.create { drivePrefsFile })

            val syncStateFile = tempFolder.newFile("test_vm_sync_state.preferences_pb")
            driveSyncStateStore = DriveSyncStateStore(PreferenceDataStoreFactory.create { syncStateFile })

            val internalSyncFile = tempFolder.newFile("test_vm_internal_sync.preferences_pb")
            syncStateStore = SyncStateStore(PreferenceDataStoreFactory.create { internalSyncFile })

            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            masterKey = keyGen.generateKey()

            val keyStore = SoftwareKeyStoreManager()
            val cryptoStorage =
                InMemoryCryptoStorage().apply {
                    setLocalWrappedMek(keyStore.wrapKey(masterKey, 1), 1)
                }
            mekManager = MekManager(keyStore, cryptoStorage)

            fakePendingChangeDao = FakePendingChangeDao()
            fakeConflictRecordDao = FakeSyncConflictRecordDao()
            fakeConflictMetadataDao = FakeSyncConflictMetadataDao()
            fakeTombstoneDao = FakeSyncTombstoneDao()
            fakeHistoryDao = FakeSyncHistoryRecordDao()
            fakeNoteDao = FakeNoteDao()
            fakeAuthDelegate = FakeGoogleDriveAuthDelegate()

            val hlcClock = HlcClock(localDeviceId, InMemoryHlcStorage())
            val localChangeRecorder =
                DefaultLocalChangeRecorder(
                    pendingChangeDao = fakePendingChangeDao,
                    deviceIdProvider = { localDeviceId },
                    hlcClock = hlcClock,
                )

            conflictResolutionManager =
                ConflictResolutionManager(
                    database = null,
                    noteDao = fakeNoteDao,
                    conflictRecordDao = fakeConflictRecordDao,
                    conflictMetadataDao = fakeConflictMetadataDao,
                    tombstoneDao = fakeTombstoneDao,
                    localChangeRecorder = localChangeRecorder,
                    hlcClock = hlcClock,
                    mekManager = mekManager,
                    ioDispatcher = testDispatcher,
                )

            val fakeJournalDao = FakeSyncJournalDao()
            val fakeSequenceDao = FakeSyncSequenceDao()
            val fakeInboxDao = FakeSyncInboxDao()
            val fakeWatermarkDao = FakeSyncWatermarkDao()
            val fakeAppliedOpDao = FakeSyncAppliedOperationDao()

            val fieldResolver =
                FieldConflictResolver(
                    noteDao = fakeNoteDao,
                    checklistItemDao = FakeChecklistItemDao(),
                    shoulderDao = FakeShoulderDao(),
                    attachmentDao = FakeAttachmentDao(),
                    conflictMetadataDao = fakeConflictMetadataDao,
                    tombstoneDao = fakeTombstoneDao,
                    conflictRecordDao = fakeConflictRecordDao,
                )

            val fakeDriveApiClient = FakeGoogleDriveApiClient()
            val transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = fakeDriveApiClient.asClient(),
                    driveAuthDelegate = fakeAuthDelegate,
                    drivePrefs = drivePrefs,
                    ioDispatcher = testDispatcher,
                )

            val discoveryService =
                JournalDiscoveryService(
                    transport = transport,
                    watermarkDao = fakeWatermarkDao,
                    ioDispatcher = testDispatcher,
                )

            val downloader =
                JournalDownloader(
                    context = fakeContext,
                    transport = transport,
                    inboxDao = fakeInboxDao,
                    watermarkDao = fakeWatermarkDao,
                    mekManager = mekManager,
                    database = null,
                    ioDispatcher = testDispatcher,
                )

            val ingestionEngine =
                RemoteJournalIngestionEngine(
                    database = null,
                    syncInboxDao = fakeInboxDao,
                    syncWatermarkDao = fakeWatermarkDao,
                    appliedOperationDao = fakeAppliedOpDao,
                    fieldConflictResolver = fieldResolver,
                    hlcClock = hlcClock,
                    mekManager = mekManager,
                    transactionRunner = { block -> block() },
                )

            val journalWriter =
                SyncJournalWriter(
                    context = fakeContext,
                    database = null,
                    pendingChangeDao = fakePendingChangeDao,
                    sequenceDao = fakeSequenceDao,
                    journalDao = fakeJournalDao,
                    mekManager = mekManager,
                    deviceIdProvider = { localDeviceId },
                )

            val uploader =
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
                    syncHistoryDao = fakeHistoryDao,
                )

            syncWorkScheduler = SyncWorkScheduler

            viewModel =
                CloudSyncViewModel(
                    context = fakeContext,
                    drivePrefs = drivePrefs,
                    driveSyncStateStore = driveSyncStateStore,
                    driveSyncCoordinator = coordinator,
                    syncWorkScheduler = syncWorkScheduler,
                    conflictResolutionManager = conflictResolutionManager,
                    conflictRecordDao = fakeConflictRecordDao,
                    syncHistoryDao = fakeHistoryDao,
                    pendingChangeDao = fakePendingChangeDao,
                    authDelegate = fakeAuthDelegate,
                )

            collectJob = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
                viewModel.uiState.collect {}
            }
        }

    @After
    fun tearDown() {
        collectJob?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialDisconnectedState() =
        runBlocking {
            val state = viewModel.uiState.first()
            assertFalse(state.isConnected)
            assertNull(state.connectedEmail)
            assertEquals(CloudConnectionStatus.DISCONNECTED, state.connectionStatus)
            assertEquals(DriveSyncState.Idle, state.syncState)
            assertEquals(0, state.pendingChangesCount)
            assertEquals(0, state.unresolvedConflictsCount)
            assertFalse(state.showDisconnectConfirmation)
        }

    @Test
    fun testConnectGoogleDriveSuccess() =
        runBlocking {
            if (!BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED) {
                var consentTriggered = false
                viewModel.connectGoogleDrive(fakeContext) {
                    consentTriggered = true
                }.join()
                assertFalse(consentTriggered)
                assertEquals("Google Drive sync is not supported in this flavor", viewModel.uiState.value.errorMessage)
                return@runBlocking
            }

            var consentTriggered = false
            fakeAuthDelegate.authorizeResult = GoogleDriveAuthorizationResult.Success("test_token", testEmail, "acc_123")

            viewModel.connectGoogleDrive(fakeContext) {
                consentTriggered = true
            }.join()

            assertFalse(consentTriggered)
            val updatedPrefs = drivePrefs.getState()
            assertTrue(updatedPrefs.isConnected)
            assertEquals(testEmail, updatedPrefs.connectedEmail)
        }

    @Test
    fun testDisconnectGoogleDriveFlow() =
        runBlocking {
            // First connect
            drivePrefs.setConnected(testEmail)
            assertTrue(drivePrefs.getState().isConnected)

            viewModel.showDisconnectDialog()
            assertTrue(viewModel.uiState.value.showDisconnectConfirmation)

            viewModel.disconnectGoogleDrive(fakeContext).join()

            val updatedPrefs = drivePrefs.getState()
            assertFalse(updatedPrefs.isConnected)
            assertNull(updatedPrefs.connectedEmail)
            assertFalse(viewModel.uiState.value.showDisconnectConfirmation)
            assertTrue(fakeAuthDelegate.disconnectCalled)
        }

    @Test
    fun testAutoSyncToggle() =
        runBlocking {
            drivePrefs.setConnected(testEmail)

            viewModel.setAutoSyncEnabled(fakeContext, false).join()
            val prefsDisabled = driveSyncStateStore.getPreferences()
            assertFalse(prefsDisabled.isSyncEnabled)

            viewModel.setAutoSyncEnabled(fakeContext, true).join()
            val prefsEnabled = driveSyncStateStore.getPreferences()
            assertTrue(prefsEnabled.isSyncEnabled)
        }

    @Test
    fun testNetworkPolicyChange() =
        runBlocking {
            viewModel.setNetworkPolicy(fakeContext, requiresWifi = true).join()
            val prefs = driveSyncStateStore.getPreferences()
            assertTrue(prefs.syncRequiresWifi)

            viewModel.setNetworkPolicy(fakeContext, requiresWifi = false).join()
            val prefsAny = driveSyncStateStore.getPreferences()
            assertFalse(prefsAny.syncRequiresWifi)
        }

    @Test
    fun testSyncFrequencyConfigurationAndClamping() =
        runBlocking {
            viewModel.setSyncFrequency(fakeContext, minutes = 10L).join() // Under 15m minimum
            val prefsClamped = driveSyncStateStore.getPreferences()
            assertEquals(15L, prefsClamped.syncFrequencyMinutes)

            viewModel.setSyncFrequency(fakeContext, minutes = 360L).join()
            val prefs6h = driveSyncStateStore.getPreferences()
            assertEquals(360L, prefs6h.syncFrequencyMinutes)
        }

    @Test
    fun testConflictResolutionKeepLocal() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val note =
                NoteEntity(
                    id = 1L,
                    uuid = noteUuid,
                    kind = NoteKind.NOTE,
                    title = "Original Local",
                    body = "Local Body",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            fakeNoteDao.insert(note)

            val conflict =
                SyncConflictRecordEntity(
                    id = 1L,
                    conflictId = "conf_1",
                    entityType = "NOTE",
                    entityId = noteUuid,
                    fieldName = "title",
                    winningValue = "Remote Title",
                    losingValue = "Original Local",
                    winningHlc = "100-001-dev2",
                    losingHlc = "100-002-dev-vm-1",
                    winningDeviceId = "dev2",
                    losingDeviceId = "dev-vm-1",
                    detectedAt = System.currentTimeMillis(),
                    resolved = false,
                )
            fakeConflictRecordDao.insert(conflict)

            viewModel.resolveConflictKeepLocal("conf_1").join()

            val resolved = fakeConflictRecordDao.getByConflictId("conf_1")
            assertNotNull(resolved)
            assertTrue(resolved!!.resolved)
        }

    @Test
    fun testConflictResolutionKeepRemote() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val note =
                NoteEntity(
                    id = 2L,
                    uuid = noteUuid,
                    kind = NoteKind.NOTE,
                    title = "Original Local",
                    body = "Body",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            fakeNoteDao.insert(note)

            val conflict =
                SyncConflictRecordEntity(
                    id = 2L,
                    conflictId = "conf_2",
                    entityType = "NOTE",
                    entityId = noteUuid,
                    fieldName = "title",
                    winningValue = "Remote Winner",
                    losingValue = "Original Local",
                    winningHlc = "100-001-dev2",
                    losingHlc = "100-002-dev-vm-1",
                    winningDeviceId = "dev2",
                    losingDeviceId = "dev-vm-1",
                    detectedAt = System.currentTimeMillis(),
                    resolved = false,
                )
            fakeConflictRecordDao.insert(conflict)

            viewModel.resolveConflictKeepRemote("conf_2").join()

            val updated = fakeNoteDao.getNoteEntityByUuid(noteUuid)
            assertNotNull(updated)
            assertEquals("Remote Winner", updated!!.title)
            assertTrue(fakeConflictRecordDao.getByConflictId("conf_2")!!.resolved)
        }

    @Test
    fun testConflictResolutionKeepBoth() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val note =
                NoteEntity(
                    id = 3L,
                    uuid = noteUuid,
                    kind = NoteKind.NOTE,
                    title = "Shared Note",
                    body = "Local Body",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            fakeNoteDao.insert(note)

            val conflict =
                SyncConflictRecordEntity(
                    id = 3L,
                    conflictId = "conf_3",
                    entityType = "NOTE",
                    entityId = noteUuid,
                    fieldName = "title",
                    winningValue = "Shared Note",
                    losingValue = "Conflicting Title",
                    winningHlc = "100-001-dev1",
                    losingHlc = "100-002-dev2",
                    winningDeviceId = "dev1",
                    losingDeviceId = "dev2",
                    detectedAt = System.currentTimeMillis(),
                    resolved = false,
                )
            fakeConflictRecordDao.insert(conflict)

            viewModel.resolveConflictKeepBoth("conf_3").join()

            assertTrue(fakeConflictRecordDao.getByConflictId("conf_3")!!.resolved)
            assertEquals(2, fakeNoteDao.notes.size)
        }

    @Test
    fun testConflictResolutionMerge() =
        runBlocking {
            val noteUuid = UUID.randomUUID().toString()
            val note =
                NoteEntity(
                    id = 4L,
                    uuid = noteUuid,
                    kind = NoteKind.NOTE,
                    title = "Draft",
                    body = "Draft Body",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            fakeNoteDao.insert(note)

            val conflict =
                SyncConflictRecordEntity(
                    id = 4L,
                    conflictId = "conf_4",
                    entityType = "NOTE",
                    entityId = noteUuid,
                    fieldName = "body",
                    winningValue = "Remote Content",
                    losingValue = "Local Content",
                    winningHlc = "100-001-dev1",
                    losingHlc = "100-002-dev2",
                    winningDeviceId = "dev1",
                    losingDeviceId = "dev2",
                    detectedAt = System.currentTimeMillis(),
                    resolved = false,
                )
            fakeConflictRecordDao.insert(conflict)

            viewModel.resolveConflictMerge("conf_4", title = "Merged Title", body = "Merged Content").join()

            val updated = fakeNoteDao.getNoteEntityByUuid(noteUuid)
            assertNotNull(updated)
            assertEquals("Merged Title", updated!!.title)
            assertEquals("Merged Content", updated.body)
            assertTrue(fakeConflictRecordDao.getByConflictId("conf_4")!!.resolved)
        }

    @Test
    fun testSyncHistoryRecordingAndClearing() =
        runBlocking {
            val record1 =
                SyncHistoryRecordEntity(
                    id = 1L,
                    timestampEpochMillis = System.currentTimeMillis() - 10000,
                    durationMillis = 1500,
                    status = "SUCCESS",
                    uploadedBatches = 2,
                    downloadedBatches = 1,
                    appliedOperations = 5,
                    conflictsDetected = 0,
                    triggeredBy = "MANUAL",
                )
            fakeHistoryDao.insert(record1)

            val history = fakeHistoryDao.getRecentHistory()
            assertEquals(1, history.size)
            assertEquals("SUCCESS", history[0].status)
            assertEquals(5, history[0].appliedOperations)

            viewModel.clearSyncHistory()
            assertTrue(fakeHistoryDao.getRecentHistory().isEmpty())
        }

    // Fakes
    private class FakeNoteDao : haddad.three.notas.data.UuidBaseFakeNoteDao()
    private class FakeChecklistItemDao : haddad.three.notas.data.UuidBaseFakeChecklistItemDao()
    private class FakeShoulderDao : haddad.three.notas.data.UuidBaseFakeShoulderDao()
    private class FakeAttachmentDao : haddad.three.notas.data.UuidBaseFakeAttachmentDao()

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var authorizeResult: GoogleDriveAuthorizationResult = GoogleDriveAuthorizationResult.Cancelled
        var disconnectCalled = false

        override suspend fun authorize(
            context: Context,
            forceAccountSelection: Boolean,
        ): GoogleDriveAuthorizationResult = authorizeResult

        override suspend fun parseConsentResult(
            context: Context,
            data: Intent?,
        ): GoogleDriveAuthorizationResult = authorizeResult

        override suspend fun disconnect(
            context: Context,
            accountEmail: String?,
            accessToken: String?,
        ) {
            disconnectCalled = true
        }

        override suspend fun invalidateToken(
            context: Context,
            token: String,
        ) {}
    }

    private class FakeSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()
        private val recordsFlow = MutableStateFlow<List<SyncConflictRecordEntity>>(emptyList())

        override suspend fun insert(record: SyncConflictRecordEntity): Long {
            records.add(record)
            recordsFlow.value = records.toList()
            return record.id
        }

        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? = records.firstOrNull { it.conflictId == conflictId }

        override suspend fun getConflictsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictRecordEntity> = records.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> = records.filter { !it.resolved }

        override fun getUnresolvedConflictsFlow(): Flow<List<SyncConflictRecordEntity>> = recordsFlow.map { it.filter { r -> !r.resolved } }

        override suspend fun getUnresolvedConflictCount(): Int = records.count { !it.resolved }

        override fun getUnresolvedConflictCountFlow(): Flow<Int> = recordsFlow.map { it.count { r -> !r.resolved } }

        override suspend fun markResolved(id: Long): Int {
            val idx = records.indexOfFirst { it.id == id }
            if (idx != -1) {
                records[idx] = records[idx].copy(resolved = true)
                recordsFlow.value = records.toList()
                return 1
            }
            return 0
        }

        override suspend fun markResolvedByConflictId(conflictId: String): Int {
            val idx = records.indexOfFirst { it.conflictId == conflictId }
            if (idx != -1) {
                records[idx] = records[idx].copy(resolved = true)
                recordsFlow.value = records.toList()
                return 1
            }
            return 0
        }

        override suspend fun getAllConflictRecords(): List<SyncConflictRecordEntity> = records

        override fun getAllConflictRecordsFlow(): Flow<List<SyncConflictRecordEntity>> = recordsFlow
    }

    private class FakeSyncConflictMetadataDao : SyncConflictMetadataDao {
        val metadata = mutableListOf<SyncConflictMetadataEntity>()

        override suspend fun insertOrUpdate(entity: SyncConflictMetadataEntity) {
            metadata.removeAll { it.entityType == entity.entityType && it.entityId == entity.entityId && it.fieldName == entity.fieldName }
            metadata.add(entity)
        }

        override suspend fun getFieldMetadata(
            entityType: String,
            entityId: String,
            fieldName: String,
        ): SyncConflictMetadataEntity? =
            metadata.firstOrNull {
                it.entityType == entityType && it.entityId == entityId && it.fieldName == fieldName
            }

        override suspend fun getAllFieldsForEntity(
            entityType: String,
            entityId: String,
        ): List<SyncConflictMetadataEntity> = metadata.filter { it.entityType == entityType && it.entityId == entityId }

        override suspend fun deleteForEntity(
            entityType: String,
            entityId: String,
        ): Int {
            val initial = metadata.size
            metadata.removeAll { it.entityType == entityType && it.entityId == entityId }
            return initial - metadata.size
        }

        override suspend fun clearAll() {
            metadata.clear()
        }
    }

    private class FakeSyncTombstoneDao : SyncTombstoneDao {
        val tombstones = mutableMapOf<String, SyncTombstoneEntity>()

        override suspend fun insertOrUpdate(tombstone: SyncTombstoneEntity) {
            tombstones["${tombstone.entityType}:${tombstone.entityId}"] = tombstone
        }

        override suspend fun getTombstone(
            entityType: String,
            entityId: String,
        ): SyncTombstoneEntity? = tombstones["$entityType:$entityId"]

        override suspend fun deleteTombstone(
            entityType: String,
            entityId: String,
        ): Int = if (tombstones.remove("$entityType:$entityId") != null) 1 else 0

        override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = tombstones.values.toList()

        override suspend fun clearAll() {
            tombstones.clear()
        }
    }

    private class FakeSyncHistoryRecordDao : SyncHistoryRecordDao {
        val records = mutableListOf<SyncHistoryRecordEntity>()
        private val recordsFlow = MutableStateFlow<List<SyncHistoryRecordEntity>>(emptyList())

        override suspend fun insert(record: SyncHistoryRecordEntity): Long {
            records.add(0, record)
            recordsFlow.value = records.toList()
            return record.id
        }

        override fun getAllHistoryFlow(limit: Int): Flow<List<SyncHistoryRecordEntity>> = recordsFlow

        override suspend fun getRecentHistory(limit: Int): List<SyncHistoryRecordEntity> = records.take(limit)

        override fun getSuccessfulHistoryFlow(limit: Int): Flow<List<SyncHistoryRecordEntity>> =
            recordsFlow.map { it.filter { r -> r.status == "SUCCESS" || r.status == "PARTIAL" }.take(limit) }

        override fun getFailedHistoryFlow(limit: Int): Flow<List<SyncHistoryRecordEntity>> =
            recordsFlow.map { it.filter { r -> r.status == "FAILED" }.take(limit) }

        override suspend fun clearAll() {
            records.clear()
            recordsFlow.value = emptyList()
        }

        override fun getHistoryCountFlow(): Flow<Int> = recordsFlow.map { it.size }
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

        override suspend fun deleteById(journalId: String): Int = if (journals.remove(journalId) != null) 1 else 0

        override suspend fun clearAll() {
            journals.clear()
        }
    }

    private class FakePendingChangeDao : PendingChangeDao {
        val changes = mutableMapOf<String, PendingChangeEntity>()
        private val countFlow = MutableStateFlow(0)

        override suspend fun insert(change: PendingChangeEntity): Long {
            changes[change.changeId] = change
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return 1L
        }

        override suspend fun insertAll(changes: List<PendingChangeEntity>) {
            changes.forEach { insert(it) }
        }

        override suspend fun update(change: PendingChangeEntity) {
            changes[change.changeId] = change
            countFlow.value = changes.values.count { it.status == "PENDING" }
        }

        override suspend fun getById(changeId: String): PendingChangeEntity? = changes[changeId]

        override suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity> =
            changes.values.filter { it.status == "PENDING" }.take(limit)

        override suspend fun getAllPendingChanges(): List<PendingChangeEntity> =
            changes.values.filter { it.status == "PENDING" }

        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = emptyFlow()

        override suspend fun countPendingChanges(): Int = changes.values.count { it.status == "PENDING" }

        override fun countPendingChangesFlow(): Flow<Int> = countFlow

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
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return count
        }

        override suspend fun markChangesSynced(journalId: String): Int {
            var count = 0
            changes.values.filter { it.journalId == journalId }.forEach {
                changes[it.changeId] = it.copy(status = "SYNCED")
                count++
            }
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return count
        }

        override suspend fun resetJournaledChangesToPending(journalId: String): Int {
            var count = 0
            changes.values.filter { it.journalId == journalId }.forEach {
                changes[it.changeId] = it.copy(status = "PENDING", journalId = null)
                count++
            }
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return count
        }

        override suspend fun deleteById(changeId: String): Int {
            val removed = changes.remove(changeId) != null
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return if (removed) 1 else 0
        }

        override suspend fun deleteByIds(changeIds: Collection<String>): Int {
            var count = 0
            changeIds.forEach { if (changes.remove(it) != null) count++ }
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return count
        }

        override suspend fun deleteChangesForEntity(
            entityType: String,
            entityId: String,
        ): Int {
            val toRemove = changes.values.filter { it.entityType == entityType && it.entityId == entityId }
            toRemove.forEach { changes.remove(it.changeId) }
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return toRemove.size
        }

        override suspend fun deleteSynced(): Int {
            val toRemove = changes.values.filter { it.status == "SYNCED" }
            toRemove.forEach { changes.remove(it.changeId) }
            countFlow.value = changes.values.count { it.status == "PENDING" }
            return toRemove.size
        }

        override suspend fun clearAll() {
            changes.clear()
            countFlow.value = 0
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

        fun asClient(): GoogleDriveApiClient {
            val fake = this
            return GoogleDriveApiClient(
                httpFactory = { urlString ->
                    if (fake.simulateOffline) {
                        throw java.io.IOException("Network offline")
                    }
                    object : HttpURLConnection(URL(urlString)) {
                        override fun getResponseCode(): Int {
                            if (fake.simulateUnauthorized) return HTTP_UNAUTHORIZED
                            if (fake.simulateServerError) return HTTP_INTERNAL_ERROR
                            return HTTP_OK
                        }

                        override fun getResponseMessage(): String = "OK"

                        override fun getInputStream(): InputStream =
                            ByteArrayInputStream("{\"kind\": \"drive#fileList\", \"files\": []}".toByteArray(Charsets.UTF_8))

                        override fun getErrorStream(): InputStream =
                            ByteArrayInputStream("{\"error\": {\"code\": 400}}".toByteArray(Charsets.UTF_8))

                        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

                        override fun connect() {}

                        override fun disconnect() {}

                        override fun usingProxy(): Boolean = false
                    }
                },
            )
        }
    }
}
