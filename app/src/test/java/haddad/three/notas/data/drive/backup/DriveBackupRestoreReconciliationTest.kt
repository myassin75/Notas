package haddad.three.notas.data.drive.backup

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.BackupIo
import haddad.three.notas.data.BaseFakeAttachmentDao
import haddad.three.notas.data.BaseFakeChecklistItemDao
import haddad.three.notas.data.BaseFakeNoteDao
import haddad.three.notas.data.BaseFakeShoulderDao
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ShoulderRepository
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.envelope.DriveRecoveryEnvelopeManager
import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveFileListResponse
import haddad.three.notas.data.drive.model.DriveRecoveryEnvelopeDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.drive.model.RestoreSummary
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.change.PendingChangeEntity
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataEntity
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.data.sync.hlc.InMemoryHlcStorage
import haddad.three.notas.data.sync.worker.DriveSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DriveBackupRestoreReconciliationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var fakeContext: Context
    private lateinit var keyStore: SoftwareKeyStoreManager
    private lateinit var cryptoStorage: InMemoryCryptoStorage
    private lateinit var mekManager: MekManager
    private lateinit var masterKey: SecretKey
    private lateinit var envelopeManager: DriveRecoveryEnvelopeManager
    private lateinit var archiveManager: DriveBackupArchiveManager
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var hlcClock: HlcClock

    private lateinit var fakeNoteDao: FakeTestNoteDao
    private lateinit var fakeShoulderDao: FakeTestShoulderDao
    private lateinit var fakeItemDao: BaseFakeChecklistItemDao
    private lateinit var fakeAttachmentDao: BaseFakeAttachmentDao
    private lateinit var fakePendingChangeDao: FakeTestPendingChangeDao
    private lateinit var fakeConflictMetadataDao: FakeTestSyncConflictMetadataDao
    private lateinit var fakeConflictRecordDao: FakeTestSyncConflictRecordDao
    private lateinit var fakeAuthRepository: FakeTestAuthRepository
    private lateinit var fakeAuthDelegate: FakeTestGoogleDriveAuthDelegate
    private lateinit var fakeDriveApiClient: FakeTestGoogleDriveApiClient
    private lateinit var noteRepository: NoteRepository

    private val testMnemonic = listOf(
        "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "about"
    )

    private val testUser = AuthUser(uid = "user_backup_test", email = "test@example.com", isEmailVerified = true)

    @Before
    fun setUp() {
        runBlocking {
            val fakeFilesDir = tempFolder.newFolder("files")
            val fakeCacheDir = tempFolder.newFolder("cache")
            fakeContext = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
                override fun getFilesDir(): File = fakeFilesDir
                override fun getCacheDir(): File = fakeCacheDir
            }

            keyStore = SoftwareKeyStoreManager()
            cryptoStorage = InMemoryCryptoStorage()
            val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
            masterKey = keyGen.generateKey()

            // Configure initial MEK
            val wrappedKey = keyStore.wrapKey(masterKey, 1)
            cryptoStorage.setLocalWrappedMek(wrappedKey, 1)
            mekManager = MekManager(keyStore, cryptoStorage)

            // Setup 12-word recovery phrase on initial device
            val recoveryWrapped = mekManager.wrapMekForRecovery(testMnemonic, "")
            cryptoStorage.setRecoveryWrappedMek(recoveryWrapped)
            cryptoStorage.setRecoveryPhraseBackedUp(true)

            drivePrefs = DriveAuthPreferences(fakeContext)
            drivePrefs.setConnected("test@example.com")

            hlcClock = HlcClock("device_test_1", InMemoryHlcStorage())
            hlcClock.initialize()

            fakeNoteDao = FakeTestNoteDao()
            fakeShoulderDao = FakeTestShoulderDao()
            fakeItemDao = BaseFakeChecklistItemDao()
            fakeAttachmentDao = BaseFakeAttachmentDao()
            fakePendingChangeDao = FakeTestPendingChangeDao()
            fakeConflictMetadataDao = FakeTestSyncConflictMetadataDao()
            fakeConflictRecordDao = FakeTestSyncConflictRecordDao()
            fakeAuthRepository = FakeTestAuthRepository(testUser)
            fakeAuthDelegate = FakeTestGoogleDriveAuthDelegate()
            fakeDriveApiClient = FakeTestGoogleDriveApiClient()

            val shoulderRepo = ShoulderRepository(fakeShoulderDao, null, fakeAuthRepository)
            noteRepository = NoteRepository(
                noteDao = fakeNoteDao,
                database = null,
                itemDao = fakeItemDao,
                attachmentDao = fakeAttachmentDao,
                scheduler = null,
                shoulderRepository = shoulderRepo,
                appMediaStorage = null,
                tombstoneRecorder = null,
                authRepository = fakeAuthRepository,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
            )

            archiveManager = DriveBackupArchiveManager(
                noteDao = fakeNoteDao,
                shoulderDao = fakeShoulderDao,
                itemDao = fakeItemDao,
                themePrefs = ThemePrefs(fakeContext),
                notesUiPrefs = NotesUiPrefs(fakeContext),
                viewOptionsPrefs = ViewOptionsPrefs(fakeContext),
                lockPrefs = LockPrefs(fakeContext),
                interactionPrefs = InteractionPrefs(fakeContext),
                quickCapturePrefs = QuickCapturePrefs(fakeContext),
                reminderPrefs = ReminderPrefs(fakeContext),
                defaultNotePrefs = DefaultNotePrefs(fakeContext),
                updatePrefs = UpdatePrefs(fakeContext),
            )

            envelopeManager = DriveRecoveryEnvelopeManager(
                driveApiClient = fakeDriveApiClient,
                mekManager = mekManager,
                cryptoStorage = cryptoStorage,
            )

            // Upload recovery envelope to Google Drive
            envelopeManager.syncRecoveryEnvelopeToDrive("token_abc")
        }
    }

    private fun createProvider(
        customMekManager: MekManager = mekManager,
        customEnvelopeManager: DriveRecoveryEnvelopeManager = envelopeManager,
    ): GoogleDriveBackupProvider {
        return GoogleDriveBackupProvider(
            context = fakeContext,
            driveApiClient = fakeDriveApiClient,
            driveAuthDelegate = fakeAuthDelegate,
            drivePrefs = drivePrefs,
            mekManager = customMekManager,
            envelopeManager = customEnvelopeManager,
            archiveManager = archiveManager,
            authRepository = fakeAuthRepository,
            noteDao = fakeNoteDao,
            shoulderDao = fakeShoulderDao,
            itemDao = fakeItemDao,
            noteRepository = noteRepository,
            themePrefs = ThemePrefs(fakeContext),
            notesUiPrefs = NotesUiPrefs(fakeContext),
            viewOptionsPrefs = ViewOptionsPrefs(fakeContext),
            lockPrefs = LockPrefs(fakeContext),
            interactionPrefs = InteractionPrefs(fakeContext),
            quickCapturePrefs = QuickCapturePrefs(fakeContext),
            reminderPrefs = ReminderPrefs(fakeContext),
            defaultNotePrefs = DefaultNotePrefs(fakeContext),
            updatePrefs = UpdatePrefs(fakeContext),
            database = null,
            scheduler = null,
            ioDispatcher = testDispatcher,
            hlcClock = hlcClock,
            pendingChangeDao = fakePendingChangeDao,
            conflictMetadataDao = fakeConflictMetadataDao,
            conflictRecordDao = fakeConflictRecordDao,
            driveSyncStateStore = null,
        )
    }

    @Test
    fun testCreateBackupIncludesEventsCountAndValidManifest() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Regular Note
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note_1",
                title = "Regular Note",
                body = "Body",
                kind = NoteKind.NOTE,
                colorIndex = 0,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                ownerId = testUser.uid,
            )
        )

        // 2. Checklist Task
        fakeNoteDao.insert(
            NoteEntity(
                id = 2L,
                uuid = "task_1",
                title = "Checklist Task",
                body = "",
                kind = NoteKind.LIST,
                colorIndex = 1,
                trashed = false,
                createdAt = now,
                updatedAt = now + 10,
                ownerId = testUser.uid,
            )
        )

        // 3. Calendar Event (reminderAt is set)
        fakeNoteDao.insert(
            NoteEntity(
                id = 3L,
                uuid = "event_1",
                title = "Doctor Appointment",
                body = "10 AM",
                kind = NoteKind.NOTE,
                colorIndex = 2,
                trashed = false,
                reminderAt = now + 3600_000L,
                createdAt = now,
                updatedAt = now + 20,
                ownerId = testUser.uid,
            )
        )

        // 4. Category
        fakeShoulderDao.insertShoulder(
            ShoulderEntity(
                id = 1L,
                uuid = "cat_1",
                name = "Health",
                normalizedName = "health",
                createdAt = now,
                updatedAt = now,
                ownerId = testUser.uid,
            )
        )

        val provider = createProvider()
        val result = provider.createBackup()
        assertTrue("Create backup should succeed: $result", result is DriveResult.Success)

        val backupItem = (result as DriveResult.Success).data
        assertEquals("Should have 2 notes (regular + event)", 2, backupItem.notesCount)
        assertEquals("Should have 1 task", 1, backupItem.tasksCount)
        assertEquals("Should have 1 calendar event", 1, backupItem.eventsCount)
        assertEquals("Should have 1 category", 1, backupItem.categoriesCount)
        assertTrue("Encrypted size should be > 0", backupItem.sizeBytes > 0)
        assertTrue("Plain SHA-256 must be present", backupItem.plainSha256.isNotBlank())
        assertTrue("Backup item must be marked encrypted", backupItem.isEncrypted)
        assertEquals("App version should be 1.0.0", "1.0.0", backupItem.appVersion)
    }

    @Test
    fun testPeekManifestAndRestorePreview() = runBlocking {
        val now = System.currentTimeMillis()
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "n_preview",
                title = "Preview Note",
                body = "Content",
                kind = NoteKind.NOTE,
                colorIndex = 0,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                ownerId = testUser.uid,
            )
        )

        val provider = createProvider()
        val createResult = provider.createBackup()
        assertTrue(createResult is DriveResult.Success)
        val backupItem = (createResult as DriveResult.Success).data

        val preview = provider.getRestorePreview(backupItem)
        assertEquals(backupItem, preview.backupItem)
        assertTrue("Backup should be compatible", preview.isCompatible)
        assertTrue("No warnings for compatible backup", preview.warningMessages.isEmpty())

        // Incompatible schema check
        val incompatibleItem = backupItem.copy(schemaVersion = BackupIo.SCHEMA_VERSION + 1)
        val incompatiblePreview = provider.getRestorePreview(incompatibleItem)
        assertFalse("Future schema should be marked incompatible", incompatiblePreview.isCompatible)
        assertTrue("Should contain schema warning", incompatiblePreview.warningMessages.any { it.contains("newer than current app") })
    }

    @Test
    fun testRestoreOnNewDeviceWithRecoveryPhrase() = runBlocking {
        val now = 1700000000000L

        // Populate initial note and create backup on Device A
        fakeNoteDao.insert(
            NoteEntity(
                id = 10L,
                uuid = "note_device_a",
                title = "Device A Secret Note",
                body = "Encrypted across devices",
                kind = NoteKind.NOTE,
                colorIndex = 3,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                ownerId = testUser.uid,
            )
        )

        val providerDeviceA = createProvider()
        val backupResult = providerDeviceA.createBackup()
        assertTrue(backupResult is DriveResult.Success)
        val backupItem = (backupResult as DriveResult.Success).data

        // SIMULATE NEW DEVICE (Device B): Clean install, empty Keystore, zero local MEK
        val cleanKeyStore = SoftwareKeyStoreManager()
        val cleanCryptoStorage = InMemoryCryptoStorage()
        val cleanMekManager = MekManager(cleanKeyStore, cleanCryptoStorage)
        assertNull("Device B initially has no active master key", cleanMekManager.getActiveMasterKey())

        val cleanEnvelopeManager = DriveRecoveryEnvelopeManager(
            driveApiClient = fakeDriveApiClient,
            mekManager = cleanMekManager,
            cryptoStorage = cleanCryptoStorage,
        )

        // Clear local database on Device B to simulate empty device
        fakeNoteDao.clear()
        assertEquals(0, fakeNoteDao.allNotes().size)

        val providerDeviceB = createProvider(
            customMekManager = cleanMekManager,
            customEnvelopeManager = cleanEnvelopeManager,
        )

        // 1. Attempt restore without recovery phrase -> NeedsRecoveryPhrase result
        val firstAttempt = providerDeviceB.restoreBackup(backupItem.fileId, RestoreMode.REPLACE, recoveryPhrase = null)
        assertTrue("Must require recovery phrase on clean device: $firstAttempt", firstAttempt is DriveResult.NeedsRecoveryPhrase)
        val needsRecovery = firstAttempt as DriveResult.NeedsRecoveryPhrase
        assertEquals(backupItem.fileId, needsRecovery.backupItem.fileId)

        // 2. Supply wrong/invalid recovery phrase -> Rejected with error
        val wrongPhraseAttempt = providerDeviceB.restoreBackup(
            backupItem.fileId,
            RestoreMode.REPLACE,
            recoveryPhrase = "apple banana orange cherry lemon grape peach pear plum mango lime wrong",
        )
        assertTrue("Invalid phrase must fail: $wrongPhraseAttempt", wrongPhraseAttempt is DriveResult.Error)
        assertEquals(0, fakeNoteDao.allNotes().size)

        // 3. Supply correct 12-word recovery phrase -> Recovers MEK, decrypts, restores data!
        val correctPhrase = testMnemonic.joinToString(" ")
        val successAttempt = providerDeviceB.restoreBackup(
            backupItem.fileId,
            RestoreMode.REPLACE,
            recoveryPhrase = correctPhrase,
        )
        assertTrue("Restore with correct phrase must succeed: $successAttempt", successAttempt is DriveResult.Success)
        val summary = (successAttempt as DriveResult.Success).data
        assertEquals(1, summary.restoredNotesCount)

        // Verify MEK is now provisioned in Device B's Keystore
        assertNotNull("Device B now has active master key", cleanMekManager.getActiveMasterKey())

        // Verify database contents on Device B
        val restoredNotes = fakeNoteDao.allNotes()
        assertEquals(1, restoredNotes.size)
        assertEquals("Device A Secret Note", restoredNotes.first().note.title)
        assertEquals("Encrypted across devices", restoredNotes.first().note.body)
    }

    @Test
    fun testIncrementalSyncReconciliationAfterRestore() = runBlocking {
        val backupTime = 1710000000000L
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note_sync_reconcile",
                title = "Restored Note",
                body = "Sync test",
                kind = NoteKind.NOTE,
                colorIndex = 1,
                trashed = false,
                createdAt = backupTime,
                updatedAt = backupTime,
                ownerId = testUser.uid,
            )
        )

        val provider = createProvider()
        val backupRes = provider.createBackup()
        assertTrue(backupRes is DriveResult.Success)
        val backupItem = (backupRes as DriveResult.Success).data

        // Insert a pending change that should be cleared in REPLACE mode
        fakePendingChangeDao.insert(
            PendingChangeEntity(
                changeId = "stale_pending_1",
                entityType = "note",
                entityId = "old_uuid",
                operationType = "CREATE",
                deviceId = "device_test_1",
                createdAt = backupTime - 1000L,
                schemaVersion = 1,
                payload = "{}",
            )
        )
        assertEquals(1, fakePendingChangeDao.countPendingChanges())

        // Insert stale conflict record
        fakeConflictRecordDao.insert(
            SyncConflictRecordEntity(
                conflictId = "conflict_stale",
                entityType = "note",
                entityId = "old_uuid",
                fieldName = "body",
                winningValue = "winner",
                losingValue = "loser",
                winningHlc = "100:0:remote",
                losingHlc = "100:0:local",
                winningDeviceId = "dev2",
                losingDeviceId = "dev1",
                detectedAt = backupTime - 1000L,
                resolved = false,
            )
        )
        assertEquals(1, fakeConflictRecordDao.getUnresolvedConflicts().size)

        // Execute Restore in REPLACE mode
        val restoreResult = provider.restoreBackup(backupItem.fileId, RestoreMode.REPLACE)
        assertTrue(restoreResult is DriveResult.Success)

        // 1. Verify HLC Clock was advanced past backup timestamp
        val currentHlc = hlcClock.generateNext()
        assertTrue(
            "HLC physical time (${currentHlc.physicalTime}) must be strictly greater than backup time ($backupTime)",
            currentHlc.physicalTime > backupTime
        )

        // 2. Verify stale pending changes and conflict records were purged in REPLACE mode
        assertEquals("Pending changes must be purged after REPLACE restore", 0, fakePendingChangeDao.countPendingChanges())
        assertEquals("Conflict records must be purged after REPLACE restore", 0, fakeConflictRecordDao.getUnresolvedConflicts().size)

        // 3. Verify baseline conflict metadata was created for restored note
        val titleMetadata = fakeConflictMetadataDao.getFieldMetadata("note", "note_sync_reconcile", "title")
        assertNotNull("Baseline conflict metadata for title must exist", titleMetadata)
        assertEquals(backupTime, titleMetadata?.updatedAt)
        assertTrue("HLC string must contain backup timestamp", titleMetadata?.hlcTimestamp?.contains(backupTime.toString()) == true)
    }

    @Test
    fun testCorruptedArchiveAndTamperRejection() = runBlocking {
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note_tamper",
                title = "Protected Note",
                body = "Tamper check",
                kind = NoteKind.NOTE,
                colorIndex = 0,
                trashed = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                ownerId = testUser.uid,
            )
        )

        val provider = createProvider()
        val backupResult = provider.createBackup()
        assertTrue(backupResult is DriveResult.Success)
        val backupItem = (backupResult as DriveResult.Success).data

        // Tamper with the raw encrypted bytes on Google Drive
        val uploadedFile = fakeDriveApiClient.storedFiles[backupItem.fileId]
        assertNotNull(uploadedFile)
        val tamperedBytes = uploadedFile!!.clone()
        // Invert bytes in the ciphertext body
        tamperedBytes[tamperedBytes.size / 2] = (tamperedBytes[tamperedBytes.size / 2].toInt() xor 0xFF).toByte()
        fakeDriveApiClient.storedFiles[backupItem.fileId] = tamperedBytes

        // Attempt restore of tampered backup -> Must fail GCM authentication
        val restoreAttempt = provider.restoreBackup(backupItem.fileId, RestoreMode.REPLACE)
        assertTrue("Tampered container must fail decryption: $restoreAttempt", restoreAttempt is DriveResult.Error)
    }

    @Test
    fun testPreRestoreSafetySnapshotCreated() = runBlocking {
        fakeNoteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note_safety",
                title = "Initial Note",
                body = "Will be backed up safely before restore",
                kind = NoteKind.NOTE,
                colorIndex = 0,
                trashed = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                ownerId = testUser.uid,
            )
        )

        val provider = createProvider()
        val backupResult = provider.createBackup()
        assertTrue(backupResult is DriveResult.Success)
        val backupItem = (backupResult as DriveResult.Success).data

        val restoreResult = provider.restoreBackup(backupItem.fileId, RestoreMode.REPLACE)
        assertTrue(restoreResult is DriveResult.Success)
        val summary = (restoreResult as DriveResult.Success).data

        assertNotNull("Pre-restore snapshot path must be set", summary.preRestoreSnapshotPath)
        val snapshotFile = File(summary.preRestoreSnapshotPath!!)
        assertTrue("Snapshot file must exist on disk", snapshotFile.exists())
        assertTrue("Snapshot file must have non-zero size", snapshotFile.length() > 0)
    }

    // --- Fakes ---

    private class FakeTestNoteDao : BaseFakeNoteDao() {
        fun clear() = notes.clear()
    }

    private class FakeTestShoulderDao : BaseFakeShoulderDao()

    private class FakeTestPendingChangeDao : PendingChangeDao {
        val changes = mutableListOf<PendingChangeEntity>()

        override suspend fun insert(change: PendingChangeEntity): Long { changes.add(change); return changes.size.toLong() }
        override suspend fun insertAll(changes: List<PendingChangeEntity>) { this.changes.addAll(changes) }
        override suspend fun update(change: PendingChangeEntity) {
            val idx = changes.indexOfFirst { it.changeId == change.changeId }
            if (idx >= 0) changes[idx] = change
        }
        override suspend fun getById(changeId: String): PendingChangeEntity? = changes.firstOrNull { it.changeId == changeId }
        override suspend fun getPendingChanges(limit: Int): List<PendingChangeEntity> = changes.filter { it.status == "PENDING" }.take(limit)
        override suspend fun getAllPendingChanges(): List<PendingChangeEntity> = changes.filter { it.status == "PENDING" }
        override fun observePendingChanges(): Flow<List<PendingChangeEntity>> = flowOf(changes.filter { it.status == "PENDING" })
        override suspend fun countPendingChanges(): Int = changes.count { it.status == "PENDING" }
        override fun countPendingChangesFlow(): Flow<Int> = flowOf(changes.count { it.status == "PENDING" })
        override suspend fun getChangesForEntity(entityType: String, entityId: String): List<PendingChangeEntity> =
            changes.filter { it.entityType == entityType && it.entityId == entityId }
        override suspend fun getChangesForJournal(journalId: String): List<PendingChangeEntity> =
            changes.filter { it.journalId == journalId }
        override suspend fun markChangesJournaled(changeIds: Collection<String>, journalId: String): Int {
            var count = 0
            changes.forEachIndexed { i, c ->
                if (c.changeId in changeIds) {
                    changes[i] = c.copy(status = "JOURNALED", journalId = journalId)
                    count++
                }
            }
            return count
        }
        override suspend fun markChangesSynced(journalId: String): Int {
            var count = 0
            changes.forEachIndexed { i, c ->
                if (c.journalId == journalId) {
                    changes[i] = c.copy(status = "SYNCED")
                    count++
                }
            }
            return count
        }
        override suspend fun resetJournaledChangesToPending(journalId: String): Int {
            var count = 0
            changes.forEachIndexed { i, c ->
                if (c.journalId == journalId) {
                    changes[i] = c.copy(status = "PENDING", journalId = null)
                    count++
                }
            }
            return count
        }
        override suspend fun deleteById(changeId: String): Int {
            val removed = changes.removeIf { it.changeId == changeId }
            return if (removed) 1 else 0
        }
        override suspend fun deleteByIds(changeIds: Collection<String>): Int {
            val count = changes.count { it.changeId in changeIds }
            changes.removeIf { it.changeId in changeIds }
            return count
        }
        override suspend fun deleteChangesForEntity(entityType: String, entityId: String): Int {
            val count = changes.count { it.entityType == entityType && it.entityId == entityId }
            changes.removeIf { it.entityType == entityType && it.entityId == entityId }
            return count
        }
        override suspend fun deleteSynced(): Int {
            val count = changes.count { it.status == "SYNCED" }
            changes.removeIf { it.status == "SYNCED" }
            return count
        }
        override suspend fun clearAll() { changes.clear() }
    }

    private class FakeTestSyncConflictMetadataDao : SyncConflictMetadataDao {
        val map = mutableMapOf<String, SyncConflictMetadataEntity>()

        override suspend fun insertOrUpdate(metadata: SyncConflictMetadataEntity) {
            map["${metadata.entityType}:${metadata.entityId}:${metadata.fieldName}"] = metadata
        }
        override suspend fun getFieldMetadata(entityType: String, entityId: String, fieldName: String): SyncConflictMetadataEntity? =
            map["$entityType:$entityId:$fieldName"]
        override suspend fun getAllFieldsForEntity(entityType: String, entityId: String): List<SyncConflictMetadataEntity> =
            map.values.filter { it.entityType == entityType && it.entityId == entityId }
        override suspend fun deleteForEntity(entityType: String, entityId: String): Int {
            val count = map.values.count { it.entityType == entityType && it.entityId == entityId }
            map.entries.removeIf { it.value.entityType == entityType && it.value.entityId == entityId }
            return count
        }
        override suspend fun getAllMetadata(): List<SyncConflictMetadataEntity> = map.values.toList()
        override suspend fun insertAll(metadata: List<SyncConflictMetadataEntity>) {
            metadata.forEach { insertOrUpdate(it) }
        }
        override suspend fun clearAll() { map.clear() }
    }

    private class FakeTestSyncConflictRecordDao : SyncConflictRecordDao {
        val records = mutableListOf<SyncConflictRecordEntity>()

        override suspend fun insert(record: SyncConflictRecordEntity): Long { records.add(record); return records.size.toLong() }
        override suspend fun getByConflictId(conflictId: String): SyncConflictRecordEntity? = records.firstOrNull { it.conflictId == conflictId }
        override suspend fun getConflictsForEntity(entityType: String, entityId: String): List<SyncConflictRecordEntity> =
            records.filter { it.entityType == entityType && it.entityId == entityId }
        override suspend fun getUnresolvedConflicts(): List<SyncConflictRecordEntity> = records.filter { !it.resolved }
        override fun getUnresolvedConflictsFlow(): Flow<List<SyncConflictRecordEntity>> = flowOf(records.filter { !it.resolved })
        override suspend fun getUnresolvedConflictCount(): Int = records.count { !it.resolved }
        override fun getUnresolvedConflictCountFlow(): Flow<Int> = flowOf(records.count { !it.resolved })
        override suspend fun markResolved(id: Long): Int = 1
        override suspend fun markResolvedByConflictId(conflictId: String): Int = 1
        override suspend fun clearAll() { records.clear() }
    }

    private class FakeTestAuthRepository(
        var user: AuthUser? = AuthUser(uid = "user_backup_test", email = "test@example.com", isEmailVerified = true),
    ) : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? get() = user

        override suspend fun registerWithEmailAndPassword(
            email: String,
            password: String,
            username: String?,
        ): AuthResult<AuthUser> = AuthResult.Success(user!!)

        override suspend fun loginWithEmailAndPassword(
            email: String,
            password: String,
        ): AuthResult<AuthUser> = AuthResult.Success(user!!)

        override suspend fun signInWithGoogle(context: Context): AuthResult<AuthUser> = AuthResult.Success(user!!)

        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun reloadUser(): AuthResult<AuthUser> = AuthResult.Success(user!!)

        override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun confirmPasswordReset(
            actionCode: String,
            newPassword: String,
        ): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = AuthResult.Success("test@example.com")

        override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("Not implemented")

        override suspend fun signOut() {
            user = null
        }

        override suspend fun getIdToken(forceRefresh: Boolean): String? = "firebase_id_token"
    }

    private class FakeTestGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var isRevoked = false

        override suspend fun authorize(
            context: Context,
            forceAccountSelection: Boolean,
        ): GoogleDriveAuthorizationResult {
            if (isRevoked) return GoogleDriveAuthorizationResult.Failure(IllegalStateException("Revoked"))
            return GoogleDriveAuthorizationResult.Success("token_abc", "test@example.com")
        }

        override suspend fun parseConsentResult(
            context: Context,
            data: android.content.Intent?,
        ): GoogleDriveAuthorizationResult = GoogleDriveAuthorizationResult.Success("token_abc", "test@example.com")

        override suspend fun disconnect(
            context: Context,
            accountEmail: String?,
            accessToken: String?,
        ) {
            isRevoked = true
        }

        override suspend fun invalidateToken(
            context: Context,
            token: String,
        ) {
            isRevoked = true
        }
    }

    private class FakeTestGoogleDriveApiClient : GoogleDriveApiClient() {
        val storedFiles = mutableMapOf<String, ByteArray>()
        val fileMetadata = mutableMapOf<String, DriveFileDto>()

        override suspend fun uploadMultipart(
            accessToken: String,
            metadataJson: String,
            fileData: ByteArray,
            mimeType: String,
        ): DriveResult<DriveFileDto> {
            val id = "file_" + UUID.randomUUID().toString().take(8)
            storedFiles[id] = fileData
            val jsonRoot = org.json.JSONObject(metadataJson)
            val name = jsonRoot.optString("name", "backup.bin")
            val appProps = mutableMapOf<String, String>()
            val propsObj = jsonRoot.optJSONObject("appProperties")
            propsObj?.keys()?.forEach { k -> appProps[k] = propsObj.getString(k) }

            val dto = DriveFileDto(
                id = id,
                name = name,
                mimeType = mimeType,
                size = fileData.size.toString(),
                createdTime = "2026-11-17T00:00:00Z",
                modifiedTime = "2026-11-17T00:00:00Z",
                appProperties = appProps,
            )
            fileMetadata[id] = dto
            return DriveResult.Success(dto)
        }

        override suspend fun uploadStreamResumable(
            accessToken: String,
            metadataJson: String,
            file: File,
            mimeType: String,
            onProgress: ((Float) -> Unit)?,
        ): DriveResult<DriveFileDto> {
            return uploadMultipart(accessToken, metadataJson, file.readBytes(), mimeType)
        }

        override suspend fun downloadFile(
            accessToken: String,
            fileId: String,
            targetFile: File,
            onProgress: ((Float) -> Unit)?,
        ): DriveResult<Unit> {
            val bytes = storedFiles[fileId] ?: return DriveResult.Error("File not found on Drive: $fileId")
            targetFile.writeBytes(bytes)
            return DriveResult.Success(Unit)
        }

        override suspend fun listFiles(
            accessToken: String,
            spaces: String,
            pageSize: Int,
            pageToken: String?,
            query: String?,
        ): DriveResult<DriveFileListResponse> {
            val list = fileMetadata.values.toList()
            val filtered = if (query != null && query.contains("name = '${DriveRecoveryEnvelopeDto.FILE_NAME}'")) {
                list.filter { it.name == DriveRecoveryEnvelopeDto.FILE_NAME }
            } else if (query != null && query.contains("name contains 'notas_backup_'")) {
                list.filter { it.name.startsWith("notas_backup_") }
            } else {
                list
            }
            return DriveResult.Success(DriveFileListResponse(files = filtered, nextPageToken = null))
        }

        override suspend fun deleteFile(accessToken: String, fileId: String): DriveResult<Unit> {
            storedFiles.remove(fileId)
            fileMetadata.remove(fileId)
            return DriveResult.Success(Unit)
        }
    }
}
