package haddad.three.notas.data.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAuthRepository(
        var user: AuthUser? = null,
        var token: String? = "valid-token",
    ) : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? get() = user

        override suspend fun registerWithEmailAndPassword(email: String, password: String, username: String?): AuthResult<AuthUser> =
            user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun loginWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser> =
            user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun signInWithGoogle(context: android.content.Context): AuthResult<AuthUser> =
            user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun reloadUser(): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun confirmPasswordReset(actionCode: String, newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = AuthResult.Success("test@example.com")
        override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("Not implemented")
        override suspend fun signOut() { user = null }
        override suspend fun getIdToken(forceRefresh: Boolean): String? = token
    }

    private class FakeAttachmentDao : AttachmentDao {
        val attachments = mutableMapOf<Long, NoteAttachmentEntity>()
        var idCounter = 1L
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

    private class MockBackupApiClient : BackupApiClient({ url -> error("Unused in mock") }) {
        var requestedUploadDto: AttachmentUploadUrlRequestDto? = null
        var uploadedS3File: File? = null
        var verifiedAttachmentId: String? = null
        var deletedAttachmentId: String? = null
        var simulateS3UploadFailure: Boolean = false

        override suspend fun getAttachmentUploadUrl(
            idToken: String,
            payload: AttachmentUploadUrlRequestDto,
        ): CloudBackupResult<AttachmentUploadUrlResponseDto> {
            requestedUploadDto = payload
            return CloudBackupResult.Success(
                AttachmentUploadUrlResponseDto(
                    attachmentId = payload.attachmentId,
                    uploadUrl = "https://s3.example.com/upload-att-${payload.attachmentId}",
                    storageKey = "attachments/user-alice/${payload.attachmentId}",
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
            if (simulateS3UploadFailure) {
                return CloudBackupResult.Error("Network connection reset", code = "S3_NETWORK_ERROR")
            }
            uploadedS3File = file
            return CloudBackupResult.Success(Unit)
        }

        override suspend fun verifyAttachment(
            idToken: String,
            attachmentId: String,
        ): CloudBackupResult<AttachmentVerifyResponseDto> {
            verifiedAttachmentId = attachmentId
            return CloudBackupResult.Success(
                AttachmentVerifyResponseDto(
                    attachmentId = attachmentId,
                    storageKey = "attachments/user-alice/$attachmentId",
                    sizeBytes = "1024",
                    status = "AVAILABLE",
                    verifiedAt = "2026-10-24T00:00:00Z",
                ),
            )
        }

        override suspend fun getAttachmentDownloadUrl(
            idToken: String,
            attachmentId: String,
        ): CloudBackupResult<AttachmentDownloadUrlResponseDto> {
            return CloudBackupResult.Success(
                AttachmentDownloadUrlResponseDto(
                    attachmentId = attachmentId,
                    downloadUrl = "https://s3.example.com/download-att-$attachmentId",
                    expiresInSeconds = 900,
                    mimeType = "image/jpeg",
                    displayName = "photo.jpg",
                ),
            )
        }

        override suspend fun downloadFileFromPresignedUrl(
            presignedUrl: String,
            targetFile: File,
            onProgress: ((Float) -> Unit)?,
        ): CloudBackupResult<Unit> {
            targetFile.writeBytes("downloaded-content".toByteArray())
            return CloudBackupResult.Success(Unit)
        }

        override suspend fun deleteAttachment(idToken: String, attachmentId: String): CloudBackupResult<Unit> {
            deletedAttachmentId = attachmentId
            return CloudBackupResult.Success(Unit)
        }
    }

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var backupApiClient: MockBackupApiClient
    private lateinit var attachmentStorageManager: AttachmentStorageManager

    @Before
    fun setUp() {
        authRepository = FakeAuthRepository(
            user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true),
        )
        attachmentDao = FakeAttachmentDao()
        backupApiClient = MockBackupApiClient()

        attachmentStorageManager = AttachmentStorageManager(
            authRepository = authRepository,
            backupApiClient = backupApiClient,
            attachmentDao = attachmentDao,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun uploadAttachmentFile_requestsPresignedUrlAndStreamsToS3() = runTest(testDispatcher) {
        val testFile = File(tempFolder.root, "sample.jpg").apply {
            writeBytes(ByteArray(256) { 0x11 })
        }
        val attachment = NoteAttachmentEntity(
            id = 1L,
            uuid = "att-uuid-1",
            noteId = 10L,
            ownerId = "user_alice",
            uri = testFile.toURI().toString(),
            displayName = "sample.jpg",
            mimeType = "image/jpeg",
        )

        val result = attachmentStorageManager.uploadAttachmentFile(attachment, testFile)
        assertTrue(result is CloudBackupResult.Success)

        // Verifies presigned URL was requested with correct metadata
        assertEquals("att-uuid-1", backupApiClient.requestedUploadDto?.attachmentId)
        assertEquals("sample.jpg", backupApiClient.requestedUploadDto?.displayName)
        assertEquals("image/jpeg", backupApiClient.requestedUploadDto?.mimeType)

        // Verifies file was streamed to S3
        assertNotNull(backupApiClient.uploadedS3File)
        assertEquals(256L, backupApiClient.uploadedS3File?.length())

        // Verifies confirmation was called
        assertEquals("att-uuid-1", backupApiClient.verifiedAttachmentId)
    }

    @Test
    fun uploadAttachmentFile_preservesLocalFileOnFailure() = runTest(testDispatcher) {
        val testFile = File(tempFolder.root, "important_doc.pdf").apply {
            writeBytes("Important content".toByteArray())
        }
        val attachment = NoteAttachmentEntity(
            id = 2L,
            uuid = "att-uuid-2",
            noteId = 20L,
            ownerId = "user_alice",
            uri = testFile.toURI().toString(),
            displayName = "important_doc.pdf",
            mimeType = "application/pdf",
        )

        backupApiClient.simulateS3UploadFailure = true

        val result = attachmentStorageManager.uploadAttachmentFile(attachment, testFile)
        assertTrue(result is CloudBackupResult.Error)

        // Local file MUST remain intact
        assertTrue("Local attachment file must be preserved upon upload failure", testFile.exists())
        assertEquals("Important content", testFile.readText())
    }

    @Test
    fun downloadAttachmentFile_streamsFromPresignedUrl() = runTest(testDispatcher) {
        val destinationFile = File(tempFolder.root, "downloaded.jpg")

        val result = attachmentStorageManager.downloadAttachmentFile("att-uuid-3", destinationFile)
        assertTrue(result is CloudBackupResult.Success)
        assertTrue(destinationFile.exists())
        assertEquals("downloaded-content", destinationFile.readText())
    }

    @Test
    fun deleteRemoteAttachment_callsBackendDeleteEndpoint() = runTest(testDispatcher) {
        val result = attachmentStorageManager.deleteRemoteAttachment("att-uuid-4")
        assertTrue(result is CloudBackupResult.Success)
        assertEquals("att-uuid-4", backupApiClient.deletedAttachmentId)
    }
}
