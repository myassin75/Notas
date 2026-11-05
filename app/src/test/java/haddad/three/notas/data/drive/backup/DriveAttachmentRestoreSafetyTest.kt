package haddad.three.notas.data.drive.backup

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.crypto.CryptoConstants
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.BackupIo
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
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.envelope.DriveRecoveryEnvelopeManager
import haddad.three.notas.data.drive.model.DriveBackupManifest
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.encodeNoteForBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class DriveAttachmentRestoreSafetyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private class FakeNoteDao : NoteDao {
        val notes = mutableMapOf<Long, NoteEntity>()
        var shouldThrowOnInsert = false
        private var idCounter = 1L

        override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(null)
        override suspend fun get(id: Long): NoteWithItems? = notes[id]?.let { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun getByUuid(uuid: String): NoteWithItems? =
            notes.values.find { it.uuid == uuid }?.let { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes.values.find { it.uuid == uuid }
        override suspend fun allNotes(): List<NoteWithItems> = notes.values.map { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> =
            notes.values.filter { it.ownerId == ownerId }.map { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()
        override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override suspend fun insert(note: NoteEntity): Long {
            if (shouldThrowOnInsert) {
                throw RuntimeException("Simulated SQLite constraint failure on insert")
            }
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
        override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders.values.toList())
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

    private class FakeAuthRepository(
        var user: AuthUser? = AuthUser(uid = "user_test", email = "test@example.com", isEmailVerified = true),
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
        override suspend fun getIdToken(forceRefresh: Boolean): String? = "mock_token"
    }

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        override suspend fun authorize(context: Context, forceAccountSelection: Boolean): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("test_drive_token", "drive_user@example.com")
        override suspend fun parseConsentResult(context: Context, data: android.content.Intent?): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("test_drive_token", "drive_user@example.com")
        override suspend fun disconnect(context: Context, accountEmail: String?, accessToken: String?) {}
        override suspend fun invalidateToken(context: Context, token: String) {}
    }

    private lateinit var fakeContext: Context
    private lateinit var fakeFilesDir: File
    private lateinit var fakeCacheDir: File

    private lateinit var noteDao: FakeNoteDao
    private lateinit var shoulderDao: FakeShoulderDao
    private lateinit var itemDao: FakeChecklistItemDao
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var archiveManager: DriveBackupArchiveManager
    private lateinit var mekManager: MekManager
    private lateinit var masterKey: SecretKey

    @Before
    fun setUp() {
        fakeFilesDir = tempFolder.newFolder("files")
        fakeCacheDir = tempFolder.newFolder("cache")
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = fakeFilesDir
            override fun getCacheDir(): File = fakeCacheDir
        }

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        masterKey = keyGen.generateKey()

        val keyStore = SoftwareKeyStoreManager()
        val storage = InMemoryCryptoStorage()
        val wrapped = keyStore.wrapKey(masterKey, 1)
        storage.setLocalWrappedMek(wrapped, 1)
        mekManager = MekManager(keyStore, storage)

        noteDao = FakeNoteDao()
        shoulderDao = FakeShoulderDao()
        itemDao = FakeChecklistItemDao()
        attachmentDao = FakeAttachmentDao()
        authRepository = FakeAuthRepository()

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
            json = json,
        )
    }

    private fun buildSampleEncryptedBackup(
        targetEncryptedFile: File,
        attachmentName: String = "photo.jpg",
        attachmentBytes: ByteArray = "VALID_PHOTO_BYTES".toByteArray(),
    ): DriveBackupManifest {
        val tempZip = tempFolder.newFile("temp_${UUID.randomUUID()}.zip")
        val createdAt = System.currentTimeMillis()

        val sampleNote = NoteEntity(
            id = 1L,
            uuid = "note_1",
            kind = NoteKind.NOTE,
            title = "Test Note With Attachment",
            body = "Body",
            colorIndex = 0,
            trashed = false,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val attUuid = UUID.randomUUID().toString()
        val sampleAttachment = NoteAttachmentEntity(
            id = 10L,
            noteId = 1L,
            uuid = attUuid,
            displayName = attachmentName,
            mimeType = "image/jpeg",
            uri = "file:///dummy/$attachmentName",
        )

        val noteWithItems = NoteWithItems(
            note = sampleNote,
            items = emptyList(),
            attachments = listOf(sampleAttachment),
        )

        // 1. Pack note JSON and attachments
        FileOutputStream(tempZip).use { fos ->
            ZipOutputStream(fos).use { zip ->
                val notesJson = JSONObject().apply {
                    put("version", 1)
                    put("exportedAt", createdAt)
                    put("categories", JSONArray())
                    put("notes", JSONArray().apply { put(encodeNoteForBackup(noteWithItems)) })
                }
                zip.putNextEntry(ZipEntry(BackupIo.ENTRY_NOTES))
                OutputStreamWriter(zip, Charsets.UTF_8).apply { write(notesJson.toString(2)); flush() }
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(BackupIo.ENTRY_SETTINGS))
                OutputStreamWriter(zip, Charsets.UTF_8).apply { write("{}"); flush() }
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("attachments/${sampleAttachment.uuid}_$attachmentName"))
                zip.write(attachmentBytes)
                zip.closeEntry()
            }
        }

        // 2. Build final ZIP with manifest
        val manifest = DriveBackupManifest(
            backupId = "backup_test_1",
            createdAtEpochMillis = createdAt,
            schemaVersion = 1,
            notesCount = 1,
            tasksCount = 0,
            categoriesCount = 0,
            attachmentsCount = 1,
            plainSha256 = "",
        )

        val perfectPlainZip = tempFolder.newFile("perfect_${UUID.randomUUID()}.zip")
        ZipOutputStream(FileOutputStream(perfectPlainZip)).use { outZip ->
            outZip.putNextEntry(ZipEntry(BackupIo.ENTRY_MANIFEST))
            OutputStreamWriter(outZip, Charsets.UTF_8).apply { write(json.encodeToString(manifest)); flush() }
            outZip.closeEntry()

            java.util.zip.ZipInputStream(FileInputStream(tempZip)).use { inZip ->
                var entry = inZip.nextEntry
                while (entry != null) {
                    outZip.putNextEntry(ZipEntry(entry.name))
                    inZip.copyTo(outZip)
                    outZip.closeEntry()
                    inZip.closeEntry()
                    entry = inZip.nextEntry
                }
            }
        }

        // Encrypt to NOTAS_E1
        FileInputStream(perfectPlainZip).use { fis ->
            FileOutputStream(targetEncryptedFile).use { fos ->
                EncryptedContainer.encryptStream(fis, fos, masterKey)
            }
        }
        return manifest
    }

    @Test
    fun testFilesystemRollbackOnDatabaseFailure() {
        runBlocking {
            // Setup existing attachments on disk
            val existingAttDir = File(fakeFilesDir, "note_attachments").apply { mkdirs() }
            val existingFile = File(existingAttDir, "existing_precious_photo.jpg").apply {
                writeText("PREVIOUS_ATTACHMENT_DO_NOT_LOSE")
            }

            val encryptedBackup = tempFolder.newFile("encrypted_backup.bin")
            buildSampleEncryptedBackup(encryptedBackup)

            // Setup mock HTTP connection returning encrypted backup
            val mockApiClient = GoogleDriveApiClient(
                httpFactory = { url ->
                    object : HttpURLConnection(URL(url)) {
                        override fun getResponseCode(): Int = 200
                        override fun getInputStream(): InputStream = FileInputStream(encryptedBackup)
                        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
                        override fun disconnect() {}
                        override fun usingProxy(): Boolean = false
                        override fun connect() {}
                    }
                },
            )

            // Make NoteDao throw on insert to simulate database failure
            noteDao.shouldThrowOnInsert = true

            val drivePrefs = DriveAuthPreferences(fakeContext)
            val envelopeManager = DriveRecoveryEnvelopeManager(mockApiClient, mekManager, InMemoryCryptoStorage())

            val provider = GoogleDriveBackupProvider(
                context = fakeContext,
                driveApiClient = mockApiClient,
                driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                drivePrefs = drivePrefs,
                mekManager = mekManager,
                envelopeManager = envelopeManager,
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
            )

            val result = provider.restoreBackup("drive_file_123", RestoreMode.REPLACE)
            assertTrue(result is DriveResult.Error)

            // Assert that the previous attachment file was preserved and restored via rollback!
            assertTrue("Existing attachment file must be preserved after rollback", existingFile.exists())
            assertEquals("PREVIOUS_ATTACHMENT_DO_NOT_LOSE", existingFile.readText())
        }
    }

    @Test
    fun testPostRestoreAttachmentReferenceVerification() {
        runBlocking {
            val encryptedBackup = tempFolder.newFile("encrypted_backup.bin")
            // Build backup with empty attachment (0 bytes)
            buildSampleEncryptedBackup(encryptedBackup, attachmentBytes = ByteArray(0))

            val mockApiClient = GoogleDriveApiClient(
                httpFactory = { url ->
                    object : HttpURLConnection(URL(url)) {
                        override fun getResponseCode(): Int = 200
                        override fun getInputStream(): InputStream = FileInputStream(encryptedBackup)
                        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
                        override fun disconnect() {}
                        override fun usingProxy(): Boolean = false
                        override fun connect() {}
                    }
                },
            )

            val drivePrefs = DriveAuthPreferences(fakeContext)
            val envelopeManager = DriveRecoveryEnvelopeManager(mockApiClient, mekManager, InMemoryCryptoStorage())

            val provider = GoogleDriveBackupProvider(
                context = fakeContext,
                driveApiClient = mockApiClient,
                driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                drivePrefs = drivePrefs,
                mekManager = mekManager,
                envelopeManager = envelopeManager,
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
            )

            val result = provider.restoreBackup("drive_file_empty_att", RestoreMode.REPLACE)
            assertTrue(result is DriveResult.Error)
            val err = result as DriveResult.Error
            assertTrue(err.message.contains("missing or empty file") || err.message.contains("verification failed"))
        }
    }
}
