package haddad.three.notas.data.drive.attachment

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class DriveAttachmentTransportTest {
    private lateinit var mockStorage: InMemoryCryptoStorage
    private lateinit var mekManager: MekManager
    private lateinit var fakeContext: Context
    private lateinit var drivePrefs: DriveAuthPreferences
    private val rawKey = ByteArray(32) { 0x55 }
    private val secretKey = SecretKeySpec(rawKey, "AES")

    private class MockHttpConnection(
        url: URL,
        private val responseCodeToReturn: Int = 200,
        private val responseHeaders: Map<String, String> = emptyMap(),
        private val responseBody: String = "{}",
        private val responseStreamProvider: (() -> InputStream)? = null,
    ) : HttpURLConnection(url) {
        val outputBytes = ByteArrayOutputStream()

        override fun getResponseCode(): Int = responseCodeToReturn

        override fun getHeaderField(name: String?): String? =
            responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getInputStream(): InputStream =
            responseStreamProvider?.invoke() ?: ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getErrorStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getOutputStream(): OutputStream = outputBytes

        override fun disconnect() {}

        override fun usingProxy(): Boolean = false

        override fun connect() {}
    }

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        override suspend fun authorize(context: Context, forceAccountSelection: Boolean): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("test_token", "test@example.com")

        override suspend fun parseConsentResult(context: Context, data: Intent?): GoogleDriveAuthorizationResult =
            GoogleDriveAuthorizationResult.Success("test_token", "test@example.com")

        override suspend fun disconnect(context: Context, accountEmail: String?, accessToken: String?) {}

        override suspend fun invalidateToken(context: Context, token: String) {}
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var r: Int
            while (fis.read(buf).also { r = it } != -1) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Before
    fun setUp() {
        val fakeFilesDir = File(System.getProperty("java.io.tmpdir"), "test_att_files_" + UUID.randomUUID()).apply { mkdirs() }
        val fakeCacheDir = File(System.getProperty("java.io.tmpdir"), "test_att_cache_" + UUID.randomUUID()).apply { mkdirs() }
        fakeContext =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
                override fun getFilesDir(): File = fakeFilesDir
                override fun getCacheDir(): File = fakeCacheDir
            }
        drivePrefs = DriveAuthPreferences(fakeContext)

        val keyStore = SoftwareKeyStoreManager()
        mockStorage =
            InMemoryCryptoStorage().apply {
                setLocalWrappedMek(keyStore.wrapKey(secretKey, 1), 1)
            }
        mekManager = MekManager(keyStore, mockStorage)
    }

    @Test
    fun testUploadAttachmentSuccessAndPreservesLocalFile(): Unit =
        runBlocking {
            val localFile = File.createTempFile("test_att_", ".jpg")
            localFile.writeBytes("IMAGE_BINARY_DATA_TEST".toByteArray(Charsets.UTF_8))
            val initialSize = localFile.length()

            var uploadMultipartCalled = false
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        when {
                            url.contains("q=") -> {
                                // findAttachmentById -> not found
                                MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = """{"files":[]}""")
                            }
                            url.contains("uploadType=multipart") -> {
                                uploadMultipartCalled = true
                                MockHttpConnection(
                                    URL(url),
                                    responseCodeToReturn = 200,
                                    responseBody = """{"id":"att_cloud_123","name":"attachment_att_1.bin"}""",
                                )
                            }
                            else -> MockHttpConnection(URL(url), responseCodeToReturn = 200)
                        }
                    },
                )

            val transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = client,
                    driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                    drivePrefs = drivePrefs,
                )

            val attachmentTransport =
                DriveAttachmentTransport(
                    context = fakeContext,
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result =
                attachmentTransport.uploadAttachment(
                    attachmentId = "att_1",
                    localPlaintextFile = localFile,
                    mimeType = "image/jpeg",
                )

            assertTrue(result is DriveResult.Success)
            assertEquals("att_cloud_123", (result as DriveResult.Success).data.id)
            assertTrue(uploadMultipartCalled)

            // Crucial: verify local file was NOT deleted or altered
            assertTrue(localFile.exists())
            assertEquals(initialSize, localFile.length())
            assertEquals("IMAGE_BINARY_DATA_TEST", localFile.readText(Charsets.UTF_8))

            localFile.delete()
        }

    @Test
    fun testUploadAttachmentDeduplication(): Unit =
        runBlocking {
            val localFile = File.createTempFile("test_att_dedup_", ".png")
            localFile.writeBytes("DEDUPLICATED_ATTACHMENT_BYTES".toByteArray(Charsets.UTF_8))
            val hash = sha256(localFile)

            var uploadCalled = false
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        when {
                            url.contains("q=") -> {
                                // Already present with matching plainSha256
                                val jsonResponse =
                                    """
                                    {
                                      "files": [
                                        {
                                          "id": "existing_cloud_att_999",
                                          "name": "attachment_att_dedup.bin",
                                          "appProperties": {
                                            "appId": "notas",
                                            "type": "attachment",
                                            "attachmentId": "att_dedup",
                                            "plainSha256": "$hash"
                                          }
                                        }
                                      ]
                                    }
                                    """.trimIndent()
                                MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = jsonResponse)
                            }
                            url.contains("uploadType=") -> {
                                uploadCalled = true
                                MockHttpConnection(URL(url), responseCodeToReturn = 200)
                            }
                            else -> MockHttpConnection(URL(url), responseCodeToReturn = 200)
                        }
                    },
                )

            val transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = client,
                    driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                    drivePrefs = drivePrefs,
                )

            val attachmentTransport =
                DriveAttachmentTransport(
                    context = fakeContext,
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result =
                attachmentTransport.uploadAttachment(
                    attachmentId = "att_dedup",
                    localPlaintextFile = localFile,
                    mimeType = "image/png",
                )

            assertTrue(result is DriveResult.Success)
            assertEquals("existing_cloud_att_999", (result as DriveResult.Success).data.id)
            assertFalse(uploadCalled) // Zero network upload calls made!

            localFile.delete()
        }

    @Test
    fun testDownloadAttachmentSuccessWithSha256Verification(): Unit =
        runBlocking {
            val plainBytes = "DOWNLOADED_ATTACHMENT_CONTENT_OK".toByteArray(Charsets.UTF_8)
            val plainSha256 = MessageDigest.getInstance("SHA-256").digest(plainBytes).joinToString("") { "%02x".format(it) }

            // Create encrypted container
            val encryptedBytes =
                EncryptedContainer.encrypt(
                    plaintext = plainBytes,
                    key = secretKey,
                    aad = "appId=notas&type=attachment&id=att_down_1".toByteArray(Charsets.UTF_8),
                )

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        MockHttpConnection(
                            URL(url),
                            responseCodeToReturn = 200,
                            responseHeaders = mapOf("Content-Length" to encryptedBytes.size.toString()),
                            responseStreamProvider = { ByteArrayInputStream(encryptedBytes) },
                        )
                    },
                )

            val transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = client,
                    driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                    drivePrefs = drivePrefs,
                )

            val attachmentTransport =
                DriveAttachmentTransport(
                    context = fakeContext,
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val targetFile = File.createTempFile("target_down_", ".bin")
            targetFile.delete() // will be created by download

            val result =
                attachmentTransport.downloadAttachment(
                    driveFileId = "cloud_id_123",
                    attachmentId = "att_down_1",
                    targetPlaintextFile = targetFile,
                    expectedPlainSha256 = plainSha256,
                )

            assertTrue(result is DriveResult.Success)
            assertTrue(targetFile.exists())
            assertEquals("DOWNLOADED_ATTACHMENT_CONTENT_OK", targetFile.readText(Charsets.UTF_8))

            targetFile.delete()
        }

    @Test
    fun testDownloadAttachmentSha256MismatchDeletesFile(): Unit =
        runBlocking {
            val plainBytes = "CORRUPTED_DOWNLOAD_CONTENT".toByteArray(Charsets.UTF_8)
            val expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

            val encryptedBytes =
                EncryptedContainer.encrypt(
                    plaintext = plainBytes,
                    key = secretKey,
                    aad = "appId=notas&type=attachment&id=att_mismatch".toByteArray(Charsets.UTF_8),
                )

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        MockHttpConnection(
                            URL(url),
                            responseCodeToReturn = 200,
                            responseHeaders = mapOf("Content-Length" to encryptedBytes.size.toString()),
                            responseStreamProvider = { ByteArrayInputStream(encryptedBytes) },
                        )
                    },
                )

            val transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = client,
                    driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                    drivePrefs = drivePrefs,
                )

            val attachmentTransport =
                DriveAttachmentTransport(
                    context = fakeContext,
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val targetFile = File.createTempFile("target_mismatch_", ".bin")
            val result =
                attachmentTransport.downloadAttachment(
                    driveFileId = "cloud_id_999",
                    attachmentId = "att_mismatch",
                    targetPlaintextFile = targetFile,
                    expectedPlainSha256 = expectedSha256,
                )

            assertTrue(result is DriveResult.Error)
            assertTrue((result as DriveResult.Error).message.contains("hash mismatch"))
            assertFalse(targetFile.exists()) // Target file cleaned up upon failure!
        }
}
