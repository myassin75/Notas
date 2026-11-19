package haddad.three.notas.data.drive.manifest

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
import haddad.three.notas.data.drive.model.DriveManifestDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class DriveManifestManagerTest {
    private lateinit var mockStorage: InMemoryCryptoStorage
    private lateinit var mekManager: MekManager
    private lateinit var fakeContext: Context
    private lateinit var drivePrefs: DriveAuthPreferences
    private val rawKey = ByteArray(32) { 0x42 }
    private val secretKey = SecretKeySpec(rawKey, "AES")
    private val json = Json { ignoreUnknownKeys = true }

    private class MockHttpConnection(
        url: URL,
        private val responseCodeToReturn: Int = 200,
        private val responseHeaders: Map<String, String> = emptyMap(),
        private val responseBody: String = "{}",
        private val responseBytes: ByteArray? = null,
    ) : HttpURLConnection(url) {
        val outputBytes = ByteArrayOutputStream()

        override fun getResponseCode(): Int = responseCodeToReturn

        override fun getHeaderField(name: String?): String? =
            responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getInputStream(): InputStream =
            if (responseBytes != null) ByteArrayInputStream(responseBytes)
            else ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

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

    @Before
    fun setUp() {
        val fakeFilesDir = File(System.getProperty("java.io.tmpdir"), "test_manifest_files_" + UUID.randomUUID()).apply { mkdirs() }
        val fakeCacheDir = File(System.getProperty("java.io.tmpdir"), "test_manifest_cache_" + UUID.randomUUID()).apply { mkdirs() }
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
    fun testReconcileParticipatingDevices(): Unit {
        val manager =
            DriveManifestManager(
                transport = GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = GoogleDriveApiClient(),
                    driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                    drivePrefs = drivePrefs,
                ),
                driveApiClient = GoogleDriveApiClient(),
                mekManager = mekManager,
            )

        val local = mapOf("dev_A" to 10L, "dev_B" to 5L)
        val remote = mapOf("dev_A" to 8L, "dev_B" to 12L, "dev_C" to 3L)

        val reconciled = manager.reconcileParticipatingDevices(local, remote)

        assertEquals(10L, reconciled["dev_A"])
        assertEquals(12L, reconciled["dev_B"])
        assertEquals(3L, reconciled["dev_C"])
    }

    @Test
    fun testPublishManifestNewFile(): Unit =
        runBlocking {
            var uploadCaptured = false
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        when {
                            url.contains("q=") -> {
                                // Find manifest -> empty list
                                MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = """{"files":[]}""")
                            }
                            url.contains("uploadType=multipart") -> {
                                uploadCaptured = true
                                MockHttpConnection(
                                    URL(url),
                                    responseCodeToReturn = 200,
                                    responseBody = """{"id":"manifest_file_1","name":"manifest.enc"}""",
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

            val manager =
                DriveManifestManager(
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val manifest =
                DriveManifestDto(
                    schemaVersion = 13,
                    participatingDevices = mapOf("dev_1" to 42L),
                    recoveryEnvelopePresent = true,
                )

            val result = manager.publishManifest(manifest)
            assertTrue(result is DriveResult.Success)
            assertEquals("manifest_file_1", (result as DriveResult.Success).data.id)
            assertTrue(uploadCaptured)
        }

    @Test
    fun testFetchManifestSuccess(): Unit =
        runBlocking {
            val manifest =
                DriveManifestDto(
                    schemaVersion = 13,
                    participatingDevices = mapOf("dev_1" to 15L, "dev_2" to 30L),
                    lastSnapshotId = "snap_xyz",
                )
            val manifestJson = json.encodeToString(DriveManifestDto.serializer(), manifest)
            val encryptedBytes =
                EncryptedContainer.encrypt(
                    plaintext = manifestJson.toByteArray(Charsets.UTF_8),
                    key = secretKey,
                    aad = DriveManifestManager.MANIFEST_AAD,
                )

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        when {
                            url.contains("q=") -> {
                                MockHttpConnection(
                                    URL(url),
                                    responseCodeToReturn = 200,
                                    responseBody = """{"files":[{"id":"manifest_file_99","name":"manifest.enc"}]}""",
                                )
                            }
                            url.contains("alt=media") -> {
                                MockHttpConnection(
                                    URL(url),
                                    responseCodeToReturn = 200,
                                    responseBytes = encryptedBytes,
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

            val manager =
                DriveManifestManager(
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result = manager.fetchManifest()
            assertTrue(result is DriveResult.Success)
            val fetched = (result as DriveResult.Success).data
            assertNotNull(fetched)
            assertEquals(13, fetched!!.schemaVersion)
            assertEquals("snap_xyz", fetched.lastSnapshotId)
            assertEquals(15L, fetched.participatingDevices["dev_1"])
            assertEquals(30L, fetched.participatingDevices["dev_2"])
        }

    @Test
    fun testFetchManifestNotFound(): Unit =
        runBlocking {
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = """{"files":[]}""")
                    },
                )

            val transport =
                GoogleDriveSyncTransport(
                    context = fakeContext,
                    driveApiClient = client,
                    driveAuthDelegate = FakeGoogleDriveAuthDelegate(),
                    drivePrefs = drivePrefs,
                )

            val manager =
                DriveManifestManager(
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result = manager.fetchManifest()
            assertTrue(result is DriveResult.Success)
            assertNull((result as DriveResult.Success).data)
        }

    @Test
    fun testFetchManifestCorruptedRejection(): Unit =
        runBlocking {
            val tamperedBytes = "NOT_A_VALID_NOTAS_E1_ARCHIVE".toByteArray(Charsets.UTF_8)
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        when {
                            url.contains("q=") -> {
                                MockHttpConnection(
                                    URL(url),
                                    responseCodeToReturn = 200,
                                    responseBody = """{"files":[{"id":"manifest_corrupted","name":"manifest.enc"}]}""",
                                )
                            }
                            url.contains("alt=media") -> {
                                MockHttpConnection(
                                    URL(url),
                                    responseCodeToReturn = 200,
                                    responseBytes = tamperedBytes,
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

            val manager =
                DriveManifestManager(
                    transport = transport,
                    driveApiClient = client,
                    mekManager = mekManager,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result = manager.fetchManifest()
            assertTrue(result is DriveResult.Error)
            assertTrue((result as DriveResult.Error).message.contains("Failed to decrypt"))
        }
}
