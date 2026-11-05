package haddad.three.notas.data.drive.backup

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ShoulderDao
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
import haddad.three.notas.data.backup.BackupApiClient
import haddad.three.notas.data.backup.BackupItemDto
import haddad.three.notas.data.backup.CloudBackupManager
import haddad.three.notas.data.backup.CloudBackupResult
import haddad.three.notas.data.backup.CreateBackupRequestDto
import haddad.three.notas.data.backup.CreateBackupResponseDto
import haddad.three.notas.data.backup.RestoreBackupResponseDto
import haddad.three.notas.data.backup.VerifyBackupResponseDto
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.envelope.DriveRecoveryEnvelopeManager
import haddad.three.notas.data.drive.model.DriveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.KeyGenerator

class DriveProviderCompatibilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeAuthRepository(
        var user: AuthUser? = AuthUser(uid = "firebase_user_777", email = "alice@example.com", isEmailVerified = true),
    ) : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? get() = user
        override suspend fun registerWithEmailAndPassword(email: String, password: String, username: String?): AuthResult<AuthUser> = AuthResult.Success(user!!)
        override suspend fun loginWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser> = AuthResult.Success(user!!)
        override suspend fun signInWithGoogle(context: Context): AuthResult<AuthUser> = AuthResult.Success(user!!)
        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun reloadUser(): AuthResult<AuthUser> = AuthResult.Success(user!!)
        override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun confirmPasswordReset(actionCode: String, newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = AuthResult.Success("test@example.com")
        override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("Not implemented")
        override suspend fun signOut() { user = null }
        override suspend fun getIdToken(forceRefresh: Boolean): String? = "firebase_id_token"
    }

    private class FakeBackupApiClient : BackupApiClient({ _ -> error("Unused") }) {
        val backups = mutableListOf(
            BackupItemDto(
                id = "s3_backup_1",
                fileName = "notas_backup_s3.zip",
                sizeBytes = "1048576",
                sha256Checksum = "sha_abc",
                notesCount = 15,
                tasksCount = 5,
                eventsCount = 0,
                categoriesCount = 2,
                schemaVersion = 1,
                isEncrypted = false,
                status = "ready",
                createdAt = "2026-11-04T12:00:00Z",
                updatedAt = "2026-11-04T12:00:00Z",
            ),
        )

        override suspend fun listBackups(idToken: String): CloudBackupResult<List<BackupItemDto>> =
            CloudBackupResult.Success(backups)

        override suspend fun createBackup(idToken: String, payload: CreateBackupRequestDto): CloudBackupResult<CreateBackupResponseDto> =
            CloudBackupResult.Success(CreateBackupResponseDto("s3_b2", "https://s3/up", 900))

        override suspend fun uploadFileToPresignedUrl(presignedUrl: String, file: File, contentType: String, onProgress: ((Float) -> Unit)?): CloudBackupResult<Unit> =
            CloudBackupResult.Success(Unit)

        override suspend fun verifyBackup(idToken: String, backupId: String): CloudBackupResult<VerifyBackupResponseDto> =
            CloudBackupResult.Success(VerifyBackupResponseDto(backupId, "ready", "2026-11-04T12:00:00Z", "1048576", "Verified successfully"))

        override suspend fun downloadFileFromPresignedUrl(presignedUrl: String, targetFile: File, onProgress: ((Float) -> Unit)?): CloudBackupResult<Unit> =
            CloudBackupResult.Success(Unit)
    }

    private class FakeNoteDao : NoteDao {
        val notes = mutableMapOf<Long, NoteEntity>()
        private var idCounter = 1L
        override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(notes[id]?.let { NoteWithItems(it, emptyList(), emptyList(), null) })
        override suspend fun get(id: Long): NoteWithItems? = notes[id]?.let { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun getByUuid(uuid: String): NoteWithItems? = notes.values.find { it.uuid == uuid }?.let { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes.values.find { it.uuid == uuid }
        override suspend fun allNotes(): List<NoteWithItems> = notes.values.map { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = notes.values.filter { it.ownerId == ownerId }.map { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()
        override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override suspend fun insert(note: NoteEntity): Long {
            val id = if (note.id != 0L) note.id else idCounter++
            notes[id] = note.copy(id = id)
            return id
        }
        override suspend fun update(note: NoteEntity) { notes[note.id] = note }
        override suspend fun setTrashed(id: Long, trashed: Boolean, updatedAt: Long) {
            notes[id]?.let { notes[id] = it.copy(trashed = trashed, updatedAt = updatedAt) }
        }
        override suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long) {
            notes[id]?.let { notes[id] = it.copy(archived = archived, updatedAt = updatedAt) }
        }
        override suspend fun deleteById(id: Long) { notes.remove(id) }
        override suspend fun allNoteIds(): List<Long> = notes.keys.toList()
        override suspend fun countPictureUri(uri: String): Int = notes.values.count { it.pictureUri == uri }
        override suspend fun trashedNoteIds(): List<Long> = notes.values.filter { it.trashed }.map { it.id }
        override suspend fun archivedNoteIds(): List<Long> = notes.values.filter { it.archived }.map { it.id }
        override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()
        override suspend fun deleteAllNotes() { notes.clear() }
        override suspend fun emptyTrash() { notes.entries.removeIf { it.value.trashed } }
        override suspend fun deleteTrashedOlderThan(cutoff: Long) {}
    }

    private class FakeShoulderDao : ShoulderDao {
        val shoulders = mutableMapOf<Long, ShoulderEntity>()
        private var idCounter = 1L
        override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())
        override suspend fun allShoulders(): List<ShoulderEntity> = shoulders.values.toList()
        override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = shoulders.values.filter { it.ownerId == ownerId }
        override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())
        override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders[id]
        override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders.values.find { it.uuid == uuid }
        override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.values.find { it.normalizedName == normalizedName }
        override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()
        override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
            val id = if (shoulder.id != 0L) shoulder.id else idCounter++
            shoulders[id] = shoulder.copy(id = id)
            return id
        }
        override suspend fun updateShoulder(shoulder: ShoulderEntity) { shoulders[shoulder.id] = shoulder }
        override suspend fun deleteShoulder(shoulder: ShoulderEntity) { shoulders.remove(shoulder.id) }
        override suspend fun setShoulderForNote(noteId: Long, shoulderId: Long?) {}
    }

    private class FakeChecklistItemDao : ChecklistItemDao {
        val items = mutableMapOf<Long, ChecklistItemEntity>()
        private var idCounter = 1L
        override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = items.values.filter { it.noteId == noteId }
        override suspend fun insert(item: ChecklistItemEntity): Long {
            val id = if (item.id != 0L) item.id else idCounter++
            items[id] = item.copy(id = id)
            return id
        }
        override suspend fun insertAll(items: List<ChecklistItemEntity>) { items.forEach { insert(it) } }
        override suspend fun update(item: ChecklistItemEntity) { items[item.id] = item }
        override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = items.values.find { it.uuid == uuid }
        override suspend fun deleteById(id: Long) { items.remove(id) }
        override suspend fun deleteForNote(noteId: Long) { items.entries.removeIf { it.value.noteId == noteId } }
    }

    private class FakeAttachmentDao : AttachmentDao {
        val attachments = mutableMapOf<Long, NoteAttachmentEntity>()
        private var idCounter = 1L
        override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = attachments.values.filter { it.noteId == noteId }
        override suspend fun insert(attachment: NoteAttachmentEntity): Long {
            val id = if (attachment.id != 0L) attachment.id else idCounter++
            attachments[id] = attachment.copy(id = id)
            return id
        }
        override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) { attachments.forEach { insert(it) } }
        override suspend fun getById(id: Long): NoteAttachmentEntity? = attachments[id]
        override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = attachments.values.find { it.uuid == uuid }
        override suspend fun countByUri(uri: String): Int = attachments.values.count { it.uri == uri }
        override suspend fun deleteById(id: Long) { attachments.remove(id) }
        override suspend fun deleteForNote(noteId: Long) { attachments.entries.removeIf { it.value.noteId == noteId } }
    }

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var isRevoked = false
        override suspend fun authorize(context: Context, forceAccountSelection: Boolean): GoogleDriveAuthorizationResult {
            if (isRevoked) return GoogleDriveAuthorizationResult.Failure(IllegalStateException("Revoked"))
            return GoogleDriveAuthorizationResult.Success("drive_token", "alice@example.com")
        }
        override suspend fun parseConsentResult(context: Context, data: android.content.Intent?): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("drive_token", "alice@example.com")
        override suspend fun disconnect(context: Context, accountEmail: String?, accessToken: String?) {
            isRevoked = true
        }
        override suspend fun invalidateToken(context: Context, token: String) {
            isRevoked = true
        }
    }

    private lateinit var fakeContext: Context
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var backupApiClient: FakeBackupApiClient
    private lateinit var noteDao: FakeNoteDao
    private lateinit var shoulderDao: FakeShoulderDao
    private lateinit var itemDao: FakeChecklistItemDao
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var noteRepository: NoteRepository
    private lateinit var mekManager: MekManager
    private lateinit var driveAuthDelegate: FakeGoogleDriveAuthDelegate
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var driveApiClient: GoogleDriveApiClient
    private lateinit var archiveManager: DriveBackupArchiveManager

    @Before
    fun setUp() {
        val fakeFilesDir = tempFolder.newFolder("files")
        val fakeCacheDir = tempFolder.newFolder("cache")
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = fakeFilesDir
            override fun getCacheDir(): File = fakeCacheDir
        }

        authRepository = FakeAuthRepository()
        backupApiClient = FakeBackupApiClient()
        noteDao = FakeNoteDao()
        shoulderDao = FakeShoulderDao()
        itemDao = FakeChecklistItemDao()
        attachmentDao = FakeAttachmentDao()

        val shoulderRepo = ShoulderRepository(shoulderDao, null, authRepository)
        noteRepository = NoteRepository(
            noteDao = noteDao,
            database = null,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            scheduler = null,
            shoulderRepository = shoulderRepo,
            appMediaStorage = null,
            tombstoneRecorder = null,
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val masterKey = keyGen.generateKey()
        val keyStore = SoftwareKeyStoreManager()
        val storage = InMemoryCryptoStorage().apply { setLocalWrappedMek(masterKey.encoded, 1) }
        mekManager = MekManager(keyStore, storage)

        driveAuthDelegate = FakeGoogleDriveAuthDelegate()
        drivePrefs = DriveAuthPreferences(fakeContext)
        driveApiClient = GoogleDriveApiClient(
            httpFactory = { url ->
                object : HttpURLConnection(URL(url)) {
                    override fun getResponseCode(): Int = 200
                    override fun getInputStream(): java.io.InputStream = java.io.ByteArrayInputStream("{}".toByteArray())
                    override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
                    override fun disconnect() {}
                    override fun usingProxy(): Boolean = false
                    override fun connect() {}
                }
            },
        )

        archiveManager = DriveBackupArchiveManager(
            noteDao = noteDao,
            shoulderDao = shoulderDao,
            itemDao = itemDao,
            themePrefs = ThemePrefs(fakeContext),
            notesUiPrefs = NotesUiPrefs(fakeContext),
            viewOptionsPrefs = ViewOptionsPrefs(fakeContext),
            lockPrefs = LockPrefs(fakeContext),
            interactionPrefs = InteractionPrefs(fakeContext),
            quickCapturePrefs = QuickCapturePrefs(fakeContext),
            reminderPrefs = ReminderPrefs(fakeContext),
            defaultNotePrefs = DefaultNotePrefs(fakeContext),
            updatePrefs = UpdatePrefs(fakeContext),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    @Test
    fun testS3BackupsRemainAccessibleAndUnaffectedByDrive() {
        runBlocking {
            val s3Manager = CloudBackupManager(
                context = fakeContext,
                authRepository = authRepository,
                backupApiClient = backupApiClient,
                noteDao = noteDao,
                shoulderDao = shoulderDao,
                itemDao = itemDao,
                attachmentDao = attachmentDao,
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
            )

            val driveProvider = GoogleDriveBackupProvider(
                context = fakeContext,
                driveApiClient = driveApiClient,
                driveAuthDelegate = driveAuthDelegate,
                drivePrefs = drivePrefs,
                mekManager = mekManager,
                envelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, mekManager, InMemoryCryptoStorage()),
                archiveManager = archiveManager,
                authRepository = authRepository,
                noteDao = noteDao,
                shoulderDao = shoulderDao,
                itemDao = itemDao,
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
            )

            // 1. Fetch S3 backups
            s3Manager.refreshBackupsList()
            assertEquals(1, s3Manager.uiState.value.backups.size)
            assertEquals("s3_backup_1", s3Manager.uiState.value.backups.first().id)

            // 2. Disconnect Google Drive
            driveProvider.disconnect()
            assertFalse(drivePrefs.state.first().isConnected)

            // 3. Verify S3 backups are completely unaffected and still accessible
            s3Manager.refreshBackupsList()
            assertEquals(1, s3Manager.uiState.value.backups.size)
            assertEquals("s3_backup_1", s3Manager.uiState.value.backups.first().id)
        }
    }

    @Test
    fun testGoogleDriveDisconnectDoesNotAffectFirebaseAuth() {
        runBlocking {
            val driveProvider = GoogleDriveBackupProvider(
                context = fakeContext,
                driveApiClient = driveApiClient,
                driveAuthDelegate = driveAuthDelegate,
                drivePrefs = drivePrefs,
                mekManager = mekManager,
                envelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, mekManager, InMemoryCryptoStorage()),
                archiveManager = archiveManager,
                authRepository = authRepository,
                noteDao = noteDao,
                shoulderDao = shoulderDao,
                itemDao = itemDao,
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
            )

            // User is signed into Firebase Auth
            assertNotNull(authRepository.currentUser)
            assertEquals("firebase_user_777", authRepository.currentUser?.uid)

            // Disconnect Google Drive
            driveProvider.disconnect()

            // Firebase authentication state is preserved completely
            assertNotNull(authRepository.currentUser)
            assertEquals("firebase_user_777", authRepository.currentUser?.uid)
            assertEquals("alice@example.com", authRepository.currentUser?.email)
        }
    }

    @Test
    fun testDriveRevocationPausesDriveOperationsWithoutAffectingLocalData() {
        runBlocking {
            // Seed a local note
            noteDao.insert(
                NoteEntity(id = 1, uuid = "u1", kind = NoteKind.NOTE, title = "Important Local Note", body = "B", colorIndex = 0, trashed = false, createdAt = 100, updatedAt = 100),
            )

            // Simulate revoked Google Drive credentials
            driveAuthDelegate.disconnect(fakeContext, "alice@example.com")

            val driveProvider = GoogleDriveBackupProvider(
                context = fakeContext,
                driveApiClient = driveApiClient,
                driveAuthDelegate = driveAuthDelegate,
                drivePrefs = drivePrefs,
                mekManager = mekManager,
                envelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, mekManager, InMemoryCryptoStorage()),
                archiveManager = archiveManager,
                authRepository = authRepository,
                noteDao = noteDao,
                shoulderDao = shoulderDao,
                itemDao = itemDao,
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
            )

            val result = driveProvider.createBackup()
            assertTrue(result is DriveResult.Unauthorized)

            // Local notes are completely unharmed
            val notes = noteDao.allNotes()
            assertEquals(1, notes.size)
            assertEquals("Important Local Note", notes.first().note.title)
        }
    }
}
