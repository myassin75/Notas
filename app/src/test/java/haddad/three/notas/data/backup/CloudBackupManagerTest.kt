package haddad.three.notas.data.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            data.value = updated
            return updated
        }
    }

    private class FakeAuthRepository(
        var user: AuthUser? = null,
        var token: String? = "valid-token",
    ) : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? get() = user

        override suspend fun registerWithEmailAndPassword(
            email: String,
            password: String,
            username: String?,
        ): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun loginWithEmailAndPassword(
            email: String,
            password: String,
        ): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun signInWithGoogle(context: android.content.Context): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun reloadUser(): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

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

        override suspend fun getIdToken(forceRefresh: Boolean): String? = token
    }

    private class FakeNoteDao : haddad.three.notas.data.BaseFakeNoteDao()

    private class FakeShoulderDao : haddad.three.notas.data.BaseFakeShoulderDao()

    private class FakeChecklistItemDao : haddad.three.notas.data.BaseFakeChecklistItemDao()

    private class FakeAttachmentDao : haddad.three.notas.data.BaseFakeAttachmentDao()

    private class MockBackupApiClient : BackupApiClient({ url -> error("Unused in mock") }) {
        var lastCreatedRequest: CreateBackupRequestDto? = null
        var lastUploadedZipBytes: ByteArray? = null
        var createResultToReturn: CloudBackupResult<CreateBackupResponseDto>? = null
        var verifyResultToReturn: CloudBackupResult<VerifyBackupResponseDto>? = null
        var restoreResultToReturn: CloudBackupResult<RestoreBackupResponseDto>? = null
        var listResultToReturn: CloudBackupResult<List<BackupItemDto>>? = null
        var downloadZipBytesToReturn: ByteArray? = null

        override suspend fun listBackups(idToken: String): CloudBackupResult<List<BackupItemDto>> = listResultToReturn ?: CloudBackupResult.Success(emptyList())

        override suspend fun createBackup(
            idToken: String,
            payload: CreateBackupRequestDto,
        ): CloudBackupResult<CreateBackupResponseDto> {
            lastCreatedRequest = payload
            return createResultToReturn ?: CloudBackupResult.Success(
                CreateBackupResponseDto(
                    backupId = "backup-123",
                    uploadUrl = "https://s3.example.com/upload-backup-123",
                    expiresInSeconds = 900,
                ),
            )
        }

        override suspend fun uploadFileToPresignedUrl(
            presignedUrl: String,
            file: File,
            contentType: String,
            onProgress: ((Float) -> Unit)?,
        ): CloudBackupResult<Unit> {
            lastUploadedZipBytes = file.readBytes()
            onProgress?.invoke(1.0f)
            return CloudBackupResult.Success(Unit)
        }

        override suspend fun verifyBackup(
            idToken: String,
            backupId: String,
        ): CloudBackupResult<VerifyBackupResponseDto> =
            verifyResultToReturn ?: CloudBackupResult.Success(
                VerifyBackupResponseDto(
                    backupId = backupId,
                    status = "COMPLETED",
                    verifiedAt = "2026-10-24T00:00:00Z",
                    actualSizeBytes = lastUploadedZipBytes?.size?.toString() ?: "1024",
                    message = "Verified",
                ),
            )

        override suspend fun restoreBackup(
            idToken: String,
            backupId: String,
        ): CloudBackupResult<RestoreBackupResponseDto> =
            restoreResultToReturn ?: CloudBackupResult.Success(
                RestoreBackupResponseDto(
                    backupId = backupId,
                    downloadUrl = "https://s3.example.com/download-backup-123",
                    expiresInSeconds = 900,
                    sha256Checksum = "dummy-checksum",
                    sizeBytes = (downloadZipBytesToReturn?.size ?: 1024).toString(),
                ),
            )

        override suspend fun downloadFileFromPresignedUrl(
            presignedUrl: String,
            targetFile: File,
            onProgress: ((Float) -> Unit)?,
        ): CloudBackupResult<Unit> {
            val bytes = downloadZipBytesToReturn ?: ByteArray(0)
            targetFile.writeBytes(bytes)
            onProgress?.invoke(1.0f)
            return CloudBackupResult.Success(Unit)
        }
    }

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var noteDao: FakeNoteDao
    private lateinit var shoulderDao: FakeShoulderDao
    private lateinit var itemDao: FakeChecklistItemDao
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var noteRepository: NoteRepository
    private lateinit var backupApiClient: MockBackupApiClient
    private lateinit var cloudBackupManager: CloudBackupManager

    @Before
    fun setUp() {
        val fakeFilesDir = tempFolder.newFolder("files")
        val fakeCacheDir = tempFolder.newFolder("cache")
        val fakeContext =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this

                override fun getFilesDir(): File = fakeFilesDir

                override fun getCacheDir(): File = fakeCacheDir
            }

        authRepository = FakeAuthRepository(user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true))
        noteDao = FakeNoteDao()
        shoulderDao = FakeShoulderDao()
        itemDao = FakeChecklistItemDao()
        attachmentDao = FakeAttachmentDao()
        backupApiClient = MockBackupApiClient()

        val shoulderRepository =
            haddad.three.notas.data.ShoulderRepository(
                shoulderDao = shoulderDao,
                tombstoneRecorder = null,
                authRepository = authRepository,
            )

        noteRepository =
            NoteRepository(
                noteDao = noteDao,
                database = null,
                itemDao = itemDao,
                attachmentDao = attachmentDao,
                scheduler = null,
                shoulderRepository = shoulderRepository,
                appMediaStorage = null,
                tombstoneRecorder = null,
                authRepository = authRepository,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
            )

        val memoryPrefs = MemoryPreferencesDataStore()
        cloudBackupManager =
            CloudBackupManager(
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
    }

    @Test
    fun createCloudBackup_includesOnlyAuthenticatedUserRecords() =
        runTest(testDispatcher) {
            // 1. Seed Alice's note, Bob's note, and a Guest note (ownerId = null)
            noteDao.insert(NoteEntity(id = 1L, uuid = "alice-note", ownerId = "user_alice", kind = NoteKind.NOTE, title = "Alice Note", body = "Secret", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L))
            noteDao.insert(NoteEntity(id = 2L, uuid = "bob-note", ownerId = "user_bob", kind = NoteKind.NOTE, title = "Bob Note", body = "Other", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L))
            noteDao.insert(NoteEntity(id = 3L, uuid = "guest-note", ownerId = null, kind = NoteKind.NOTE, title = "Guest Note", body = "Local only", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L))

            shoulderDao.insertShoulder(ShoulderEntity(id = 10L, uuid = "alice-cat", ownerId = "user_alice", name = "Alice Work", normalizedName = "alice work", createdAt = 1000L, updatedAt = 1000L))
            shoulderDao.insertShoulder(ShoulderEntity(id = 20L, uuid = "guest-cat", ownerId = null, name = "Guest Cat", normalizedName = "guest cat", createdAt = 1000L, updatedAt = 1000L))

            // 2. Run cloud backup
            val result = cloudBackupManager.createCloudBackup()
            assertTrue(result is CloudBackupResult.Success)

            // 3. Inspect ZIP payload uploaded to S3
            val uploadedBytes = backupApiClient.lastUploadedZipBytes
            assertNotNull(uploadedBytes)

            var extractedNotesJson: String? = null
            ZipInputStream(ByteArrayInputStream(uploadedBytes!!)).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    if (entry.name == BackupIo.ENTRY_NOTES) {
                        extractedNotesJson = zipInput.bufferedReader().readText()
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
            }

            assertNotNull(extractedNotesJson)
            val notesRoot = JSONObject(extractedNotesJson!!)
            val notesArray = notesRoot.getJSONArray("notes")
            val categoriesArray = notesRoot.getJSONArray("categories")

            // Alice has exactly 1 note in backup
            assertEquals(1, notesArray.length())
            assertEquals("alice-note", notesArray.getJSONObject(0).getString("uuid"))
            assertEquals("user_alice", notesArray.getJSONObject(0).getString("ownerId"))

            // Alice has exactly 1 category in backup
            assertEquals(1, categoriesArray.length())
            assertEquals("alice-cat", categoriesArray.getJSONObject(0).getString("uuid"))
            assertEquals("user_alice", categoriesArray.getJSONObject(0).getString("ownerId"))
        }

    @Test
    fun createCloudBackup_unauthorizedWhenNoActiveUser() =
        runTest(testDispatcher) {
            authRepository.user = null
            val result = cloudBackupManager.createCloudBackup()
            assertTrue(result is CloudBackupResult.Unauthorized)
        }

    @Test
    fun restoreCloudBackup_sizeMismatchRejectsWithoutModifyingDb() =
        runTest(testDispatcher) {
            // Seed an existing local note
            noteDao.insert(NoteEntity(id = 5L, uuid = "existing-note", ownerId = "user_alice", kind = NoteKind.NOTE, title = "Original", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L))

            // Mock restore response expecting 5000 bytes, but downloaded file is only 10 bytes
            backupApiClient.restoreResultToReturn =
                CloudBackupResult.Success(
                    RestoreBackupResponseDto(
                        backupId = "backup-size-fail",
                        downloadUrl = "https://s3.example.com/download",
                        expiresInSeconds = 900,
                        sha256Checksum = "a".repeat(64),
                        sizeBytes = "5000",
                    ),
                )
            backupApiClient.downloadZipBytesToReturn = ByteArray(10) { 0x42 }

            val result = cloudBackupManager.restoreCloudBackup("backup-size-fail")
            assertTrue(result is CloudBackupResult.Error)
            assertEquals("SIZE_MISMATCH", (result as CloudBackupResult.Error).code)

            // Assert: Local DB is completely untouched
            val note = noteDao.getByUuid("existing-note")
            assertNotNull(note)
            assertEquals("Original", note?.note?.title)
        }

    @Test
    fun restoreCloudBackup_checksumMismatchRejectsWithoutModifyingDb() =
        runTest(testDispatcher) {
            noteDao.insert(NoteEntity(id = 5L, uuid = "existing-note", ownerId = "user_alice", kind = NoteKind.NOTE, title = "Original", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L))

            val dummyBytes = ByteArray(100) { 0x42 }
            backupApiClient.restoreResultToReturn =
                CloudBackupResult.Success(
                    RestoreBackupResponseDto(
                        backupId = "backup-hash-fail",
                        downloadUrl = "https://s3.example.com/download",
                        expiresInSeconds = 900,
                        sha256Checksum = "0".repeat(64), // Mismatched checksum
                        sizeBytes = "100",
                    ),
                )
            backupApiClient.downloadZipBytesToReturn = dummyBytes

            val result = cloudBackupManager.restoreCloudBackup("backup-hash-fail")
            assertTrue(result is CloudBackupResult.Error)
            assertEquals("CHECKSUM_MISMATCH", (result as CloudBackupResult.Error).code)

            // Local DB untouched
            val note = noteDao.getByUuid("existing-note")
            assertNotNull(note)
            assertEquals("Original", note?.note?.title)
        }

    @Test
    fun restoreCloudBackup_corruptedArchiveRejectsWithoutModifyingDb() =
        runTest(testDispatcher) {
            noteDao.insert(NoteEntity(id = 5L, uuid = "existing-note", ownerId = "user_alice", kind = NoteKind.NOTE, title = "Original", body = "", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L))

            // Create a ZIP without notes.json
            val tempZip = File(tempFolder.root, "corrupted.zip")
            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    zip.putNextEntry(ZipEntry("other.txt"))
                    zip.write("garbage".toByteArray())
                    zip.closeEntry()
                }
            }
            val zipBytes = tempZip.readBytes()
            val digest = MessageDigest.getInstance("SHA-256")
            val sha256 = digest.digest(zipBytes).joinToString("") { "%02x".format(it) }

            backupApiClient.restoreResultToReturn =
                CloudBackupResult.Success(
                    RestoreBackupResponseDto(
                        backupId = "backup-corrupt",
                        downloadUrl = "https://s3.example.com/download",
                        expiresInSeconds = 900,
                        sha256Checksum = sha256,
                        sizeBytes = zipBytes.size.toString(),
                    ),
                )
            backupApiClient.downloadZipBytesToReturn = zipBytes

            val result = cloudBackupManager.restoreCloudBackup("backup-corrupt")
            assertTrue(result is CloudBackupResult.Error)
            assertEquals("INVALID_ARCHIVE", (result as CloudBackupResult.Error).code)

            // Local DB untouched
            val note = noteDao.getByUuid("existing-note")
            assertNotNull(note)
        }

    @Test
    fun restoreCloudBackup_safeRestorePreservesGuestDataAndAssignsAuthenticatedOwner() =
        runTest(testDispatcher) {
            // 1. Existing local guest note
            noteDao.insert(
                NoteEntity(
                    id = 99L,
                    uuid = "guest-note-99",
                    ownerId = null,
                    kind = NoteKind.NOTE,
                    title = "Local Guest Note",
                    body = "Must stay intact",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = 500L,
                    updatedAt = 500L,
                ),
            )

            // 2. Build a valid restore archive containing 1 note
            val tempZip = File(tempFolder.root, "valid_restore.zip")
            val notesJson =
                JSONObject().apply {
                    put("version", 5)
                    put("exportedAt", 2000L)
                    put(
                        "notes",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("id", 1L)
                                    put("uuid", "restored-note-1")
                                    put("kind", "NOTE")
                                    put("title", "Cloud Restored Note")
                                    put("body", "Restored body")
                                    put("colorIndex", 1)
                                    put("trashed", false)
                                    put("archived", false)
                                    put("createdAt", 1500L)
                                    put("updatedAt", 1500L)
                                },
                            )
                        },
                    )
                }

            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    zip.putNextEntry(ZipEntry(BackupIo.ENTRY_NOTES))
                    zip.write(notesJson.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            val zipBytes = tempZip.readBytes()
            val digest = MessageDigest.getInstance("SHA-256")
            val sha256 = digest.digest(zipBytes).joinToString("") { "%02x".format(it) }

            backupApiClient.restoreResultToReturn =
                CloudBackupResult.Success(
                    RestoreBackupResponseDto(
                        backupId = "backup-valid",
                        downloadUrl = "https://s3.example.com/download",
                        expiresInSeconds = 900,
                        sha256Checksum = sha256,
                        sizeBytes = zipBytes.size.toString(),
                    ),
                )
            backupApiClient.downloadZipBytesToReturn = zipBytes

            // 3. Execute restore
            val result = cloudBackupManager.restoreCloudBackup("backup-valid")
            assertTrue(result is CloudBackupResult.Success)
            assertEquals(1, (result as CloudBackupResult.Success).data)

            // 4. Guest note MUST remain preserved with ownerId = null
            val guestNote = noteDao.getByUuid("guest-note-99")
            assertNotNull("Guest note must not be deleted during account cloud restore", guestNote)
            assertNull(guestNote?.note?.ownerId)

            // 5. Restored note MUST be assigned to the authenticated user "user_alice"
            val restoredNote = noteDao.getByUuid("restored-note-1")
            assertNotNull(restoredNote)
            assertEquals("user_alice", restoredNote?.note?.ownerId)
            assertEquals("Cloud Restored Note", restoredNote?.note?.title)
        }

    @Test
    fun listCloudBackups_returnsBackupsFromBackend() =
        runTest(testDispatcher) {
            val backupsList =
                listOf(
                    BackupItemDto(
                        id = "b1",
                        fileName = "backup-1.zip",
                        sizeBytes = "1024",
                        sha256Checksum = "checksum1",
                        notesCount = 10,
                        tasksCount = 5,
                        eventsCount = 0,
                        categoriesCount = 2,
                        schemaVersion = 5,
                        isEncrypted = false,
                        status = "COMPLETED",
                        verifiedAt = "2026-10-24T00:01:00Z",
                        createdAt = "2026-10-24T00:00:00Z",
                        updatedAt = "2026-10-24T00:00:00Z",
                    ),
                )
            backupApiClient.listResultToReturn = CloudBackupResult.Success(backupsList)

            val result = cloudBackupManager.listCloudBackups()
            assertTrue(result is CloudBackupResult.Success)
            val data = (result as CloudBackupResult.Success).data
            assertEquals(1, data.size)
            assertEquals("b1", data[0].id)
            assertEquals(10, data[0].notesCount)
        }

    @Test
    fun restoreCloudBackup_legacyV1BackupSupported() =
        runTest(testDispatcher) {
            // Construct a legacy v1 backup without ownerId or categories
            val tempZip = File(tempFolder.root, "legacy_v1.zip")
            val notesJson =
                JSONObject().apply {
                    put("version", 1) // Legacy schema v1
                    put("exportedAt", 1000L)
                    put(
                        "notes",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("id", 42L)
                                    put("uuid", "legacy-note-42")
                                    put("title", "Legacy Title")
                                    put("body", "Legacy Body")
                                    put("colorIndex", 2)
                                    put("trashed", false)
                                    put("createdAt", 1000L)
                                    put("updatedAt", 1000L)
                                },
                            )
                        },
                    )
                }

            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    zip.putNextEntry(ZipEntry(BackupIo.ENTRY_NOTES))
                    zip.write(notesJson.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            val zipBytes = tempZip.readBytes()
            val digest = MessageDigest.getInstance("SHA-256")
            val sha256 = digest.digest(zipBytes).joinToString("") { "%02x".format(it) }

            backupApiClient.restoreResultToReturn =
                CloudBackupResult.Success(
                    RestoreBackupResponseDto(
                        backupId = "backup-legacy",
                        downloadUrl = "https://s3.example.com/download",
                        expiresInSeconds = 900,
                        sha256Checksum = sha256,
                        sizeBytes = zipBytes.size.toString(),
                    ),
                )
            backupApiClient.downloadZipBytesToReturn = zipBytes

            val result = cloudBackupManager.restoreCloudBackup("backup-legacy")
            assertTrue(result is CloudBackupResult.Success)
            assertEquals(1, (result as CloudBackupResult.Success).data)

            val restored = noteDao.getByUuid("legacy-note-42")
            assertNotNull(restored)
            assertEquals("user_alice", restored?.note?.ownerId)
            assertEquals("Legacy Title", restored?.note?.title)
        }
}
