package haddad.three.notas.data.sync.snapshot

import android.content.Context
import android.content.ContextWrapper
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
import java.security.MessageDigest

class SnapshotTransportServiceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = Dispatchers.IO

    private lateinit var fakeContext: Context
    private lateinit var fakeSnapshotDao: FakeSyncSnapshotDao
    private lateinit var fakeAuthDelegate: FakeGoogleDriveAuthDelegate
    private lateinit var fakeDriveApiClient: FakeGoogleDriveApiClient
    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var transport: GoogleDriveSyncTransport
    private lateinit var transportService: SnapshotTransportService

    private val localDeviceId = "device_test_alpha"
    private val testAccount = "user@test.com"

    @Before
    fun setUp() {
        val fakeFilesDir = tempFolder.newFolder("files")
        val fakeCacheDir = tempFolder.newFolder("cache")
        fakeContext =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this

                override fun getFilesDir(): File = fakeFilesDir

                override fun getCacheDir(): File = fakeCacheDir
            }

        fakeSnapshotDao = FakeSyncSnapshotDao()
        fakeAuthDelegate = FakeGoogleDriveAuthDelegate()
        fakeDriveApiClient = FakeGoogleDriveApiClient()
        drivePrefs = DriveAuthPreferences(fakeContext)

        transport =
            GoogleDriveSyncTransport(
                context = fakeContext,
                driveApiClient = fakeDriveApiClient.asClient(),
                driveAuthDelegate = fakeAuthDelegate,
                drivePrefs = drivePrefs,
                ioDispatcher = testDispatcher,
            )

        transportService =
            SnapshotTransportService(
                context = fakeContext,
                transport = transport,
                driveApiClient = fakeDriveApiClient.asClient(),
                snapshotDao = fakeSnapshotDao,
                ioDispatcher = testDispatcher,
            )
    }

    @Test
    fun test_uploadSnapshot_transitionsToActiveOnlyAfterVerifiedUpload() =
        runBlocking {
            // Arrange
            val snapshotFile =
                tempFolder.newFile("test_snapshot.bin").apply {
                    writeBytes("DUMMY_ENCRYPTED_SNAPSHOT_DATA_12345".toByteArray(Charsets.UTF_8))
                }
            val sha256 = computeSha256(snapshotFile)
            val snapshotId = "snap-upload-001"

            val entity =
                SyncSnapshotEntity(
                    snapshotId = snapshotId,
                    creatorDeviceId = localDeviceId,
                    createdAt = 1000L,
                    formatVersion = 1,
                    storagePath = snapshotFile.absolutePath,
                    contentSha256 = sha256,
                    coverageVectorJson = """{"$localDeviceId":5}""",
                    snapshotHlc = "1000:0:$localDeviceId",
                    status = SnapshotStatus.READY_TO_UPLOAD,
                )
            fakeSnapshotDao.insert(entity)

            val manifest =
                SyncSnapshotManifest(
                    formatVersion = 1,
                    databaseVersion = 13,
                    snapshotId = snapshotId,
                    accountEmail = testAccount,
                    creatorDeviceId = localDeviceId,
                    createdAtEpochMillis = 1000L,
                    snapshotHlc = "1000:0:$localDeviceId",
                    coverageVector = mapOf(localDeviceId to 5L),
                )

            // Act
            val result = transportService.uploadSnapshot(snapshotEntity = entity, manifest = manifest)

            // Assert
            assertTrue("Upload must succeed: $result", result is SnapshotUploadResult.Success)
            val success = result as SnapshotUploadResult.Success
            assertNotNull(success.driveFileId)

            // Mandatory Constraint 2: Transition to ACTIVE only after verified upload
            val updated = fakeSnapshotDao.getById(snapshotId)
            assertNotNull(updated)
            assertEquals(SnapshotStatus.ACTIVE, updated!!.status)
            assertEquals(success.driveFileId, updated.driveFileId)
        }

    @Test
    fun test_uploadSnapshot_authFailure_leavesStatusReadyToUpload() =
        runBlocking {
            // Arrange: Simulate unauthorized
            fakeDriveApiClient.simulateUnauthorized = true

            val snapshotFile =
                tempFolder.newFile("test_snapshot_unauth.bin").apply {
                    writeBytes("SNAPSHOT_PAYLOAD".toByteArray(Charsets.UTF_8))
                }
            val sha256 = computeSha256(snapshotFile)
            val snapshotId = "snap-upload-unauth"

            val entity =
                SyncSnapshotEntity(
                    snapshotId = snapshotId,
                    creatorDeviceId = localDeviceId,
                    createdAt = 1000L,
                    formatVersion = 1,
                    storagePath = snapshotFile.absolutePath,
                    contentSha256 = sha256,
                    coverageVectorJson = """{"$localDeviceId":1}""",
                    snapshotHlc = "1000:0:$localDeviceId",
                    status = SnapshotStatus.READY_TO_UPLOAD,
                )
            fakeSnapshotDao.insert(entity)

            val manifest =
                SyncSnapshotManifest(
                    snapshotId = snapshotId,
                    accountEmail = testAccount,
                    creatorDeviceId = localDeviceId,
                    createdAtEpochMillis = 1000L,
                    snapshotHlc = "1000:0:$localDeviceId",
                    coverageVector = mapOf(localDeviceId to 1L),
                )

            // Act
            val result = transportService.uploadSnapshot(snapshotEntity = entity, manifest = manifest)

            // Assert
            assertTrue("Upload must fail with Unauthorized: $result", result is SnapshotUploadResult.Unauthorized)

            // Invariant: Status must NEVER transition to ACTIVE
            val notUpdated = fakeSnapshotDao.getById(snapshotId)
            assertNotNull(notUpdated)
            assertEquals(SnapshotStatus.READY_TO_UPLOAD, notUpdated!!.status)
            assertNull(notUpdated.driveFileId)
        }

    @Test
    fun test_downloadSnapshot_verifiesSha256Integrity() =
        runBlocking {
            // Arrange
            val content = "DOWNLOADABLE_ENCRYPTED_SNAPSHOT_BYTES".toByteArray(Charsets.UTF_8)
            val expectedSha = computeSha256(content)
            val driveFileId =
                fakeDriveApiClient.addFile(
                    name = "snapshot_file.bin",
                    bytes = content,
                    appProperties =
                        mapOf(
                            "type" to SnapshotTransportService.TYPE_SNAPSHOT,
                            "snapshotId" to "snap-download-1",
                            "contentSha256" to expectedSha,
                        ),
                )

            val destFile = tempFolder.newFile("downloaded.bin")

            // Act 1: Download with valid expectedSha256
            val resultSuccess =
                transportService.downloadSnapshot(
                    driveFileId = driveFileId,
                    destinationFile = destFile,
                    expectedSha256 = expectedSha,
                )
            assertTrue("Download with valid hash must succeed: $resultSuccess", resultSuccess is SnapshotDownloadResult.Success)
            assertEquals(expectedSha, computeSha256(destFile))

            // Act 2: Download with invalid/tampered expectedSha256
            val tamperedDestFile = tempFolder.newFile("tampered_down.bin")
            val resultFailure =
                transportService.downloadSnapshot(
                    driveFileId = driveFileId,
                    destinationFile = tamperedDestFile,
                    expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                )
            assertTrue("Download with corrupted hash must fail: $resultFailure", resultFailure is SnapshotDownloadResult.Failure)
        }

    @Test
    fun test_discoverRemoteSnapshots_parsesAndSortsValidHeaders() =
        runBlocking {
            // Arrange: Add 2 snapshot files in Drive with different upload timestamps
            fakeDriveApiClient.addFile(
                name = "snapshot_old.bin",
                bytes = byteArrayOf(1, 2, 3),
                appProperties =
                    mapOf(
                        "appId" to GoogleDriveSyncTransport.APP_ID,
                        "type" to SnapshotTransportService.TYPE_SNAPSHOT,
                        "snapshotId" to "snap-old",
                        "creatorDeviceId" to "device-alpha",
                        "formatVersion" to "1",
                        "contentSha256" to "sha-old",
                        "coverageVectorJson" to """{"device-alpha":10}""",
                        "snapshotHlc" to "1000:0:device-alpha",
                        "uploadedAt" to "100000",
                    ),
            )

            fakeDriveApiClient.addFile(
                name = "snapshot_new.bin",
                bytes = byteArrayOf(4, 5, 6),
                appProperties =
                    mapOf(
                        "appId" to GoogleDriveSyncTransport.APP_ID,
                        "type" to SnapshotTransportService.TYPE_SNAPSHOT,
                        "snapshotId" to "snap-new",
                        "creatorDeviceId" to "device-beta",
                        "formatVersion" to "1",
                        "contentSha256" to "sha-new",
                        "coverageVectorJson" to """{"device-beta":20}""",
                        "snapshotHlc" to "2000:0:device-beta",
                        "uploadedAt" to "200000",
                    ),
            )

            // Act
            val result = transportService.discoverRemoteSnapshots()

            // Assert
            assertTrue("Discovery must succeed: $result", result is DriveResult.Success)
            val snapshots = (result as DriveResult.Success).data
            assertEquals(2, snapshots.size)

            // Must be sorted by uploadedAt descending
            assertEquals("snap-new", snapshots[0].snapshotId)
            assertEquals("snap-old", snapshots[1].snapshotId)
            assertEquals(200000L, snapshots[0].uploadedAtEpochMillis)
            assertEquals(100000L, snapshots[1].uploadedAtEpochMillis)
        }

    private fun computeSha256(file: File): String = computeSha256(file.readBytes())

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // --- Test Doubles ---

    private class FakeSyncSnapshotDao : SyncSnapshotDao {
        val snapshots = mutableMapOf<String, SyncSnapshotEntity>()

        override suspend fun insert(snapshot: SyncSnapshotEntity) {
            snapshots[snapshot.snapshotId] = snapshot
        }

        override suspend fun update(snapshot: SyncSnapshotEntity) {
            snapshots[snapshot.snapshotId] = snapshot
        }

        override suspend fun getById(snapshotId: String): SyncSnapshotEntity? = snapshots[snapshotId]

        override suspend fun getByStatus(status: String): List<SyncSnapshotEntity> = snapshots.values.filter { it.status == status }

        override suspend fun getActiveSnapshot(): SyncSnapshotEntity? = snapshots.values.firstOrNull { it.status == SnapshotStatus.ACTIVE }

        override suspend fun getAllSnapshots(): List<SyncSnapshotEntity> = snapshots.values.toList()

        override suspend fun markUploadedAndActive(
            snapshotId: String,
            driveFileId: String,
            newStatus: String,
            timestamp: Long,
        ): Int {
            val existing = snapshots[snapshotId] ?: return 0
            snapshots[snapshotId] =
                existing.copy(
                    driveFileId = driveFileId,
                    status = newStatus,
                    updatedAt = timestamp,
                )
            return 1
        }

        override suspend fun updateStatus(
            snapshotId: String,
            newStatus: String,
            timestamp: Long,
        ): Int {
            val existing = snapshots[snapshotId] ?: return 0
            snapshots[snapshotId] = existing.copy(status = newStatus, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteById(snapshotId: String): Int = if (snapshots.remove(snapshotId) != null) 1 else 0

        override suspend fun clearAll() {
            snapshots.clear()
        }
    }

    private class FakeGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
        var isRevoked = false

        override suspend fun authorize(
            context: Context,
            forceAccountSelection: Boolean,
        ): GoogleDriveAuthorizationResult {
            if (isRevoked) return GoogleDriveAuthorizationResult.Failure(IllegalStateException("Revoked"))
            return GoogleDriveAuthorizationResult.Success("test_token_123", "user@test.com")
        }

        override suspend fun parseConsentResult(
            context: Context,
            data: android.content.Intent?,
        ): GoogleDriveAuthorizationResult = GoogleDriveAuthorizationResult.Success("test_token_123", "user@test.com")

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
        ) {}
    }

    private class FakeGoogleDriveApiClient {
        data class DriveEntry(
            val id: String,
            val name: String,
            val bytes: ByteArray,
            val appProperties: Map<String, String>,
        )

        val files = mutableMapOf<String, DriveEntry>()
        var simulateUnauthorized = false
        var failDownloadAttempts = 0
        var rateLimitAttempts = 0
        var retryAfterSecondsHeader: Long? = null
        var pendingUploadId: String? = null
        private var idCounter = 1

        fun addFile(
            name: String,
            bytes: ByteArray,
            appProperties: Map<String, String>,
        ): String {
            val id = "drive_file_${idCounter++}"
            files[id] = DriveEntry(id, name, bytes, appProperties)
            return id
        }

        fun asClient(): GoogleDriveApiClient {
            return GoogleDriveApiClient(
                httpFactory = { urlString ->
                    object : HttpURLConnection(URL(urlString)) {
                        override fun getResponseCode(): Int {
                            if (simulateUnauthorized) return HTTP_UNAUTHORIZED
                            if (rateLimitAttempts > 0) {
                                rateLimitAttempts--
                                return 429
                            }
                            if (failDownloadAttempts > 0 && urlString.contains("alt=media")) {
                                failDownloadAttempts--
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
                            if (simulateUnauthorized) return ByteArrayInputStream(byteArrayOf())
                            if (urlString.contains("alt=media")) {
                                val fileId = urlString.substringAfter("/files/").substringBefore("?")
                                val entry = files[fileId] ?: error("File not found $fileId")
                                return ByteArrayInputStream(entry.bytes)
                            }
                            if (urlString.contains("/files?")) {
                                if (requestMethod == "POST") {
                                    val id = pendingUploadId ?: "drive_file_${idCounter++}"
                                    pendingUploadId = null
                                    val jsonResp = """{"id":"$id","name":"uploaded"}"""
                                    return ByteArrayInputStream(jsonResp.toByteArray())
                                } else {
                                    val query = URL(urlString).query ?: ""
                                    val pageSize = query.substringAfter("pageSize=", "").substringBefore("&").toIntOrNull() ?: 50
                                    val pageToken = query.substringAfter("pageToken=", "").substringBefore("&")

                                    val sortedEntries = files.values.sortedBy { it.id }
                                    val startIndex = pageToken.toIntOrNull() ?: 0
                                    val pagedEntries = sortedEntries.drop(startIndex).take(pageSize)
                                    val nextIndex = startIndex + pagedEntries.size
                                    val nextTokenStr = if (nextIndex < sortedEntries.size) nextIndex.toString() else null

                                    val filesJson =
                                        pagedEntries
                                            .map { entry ->
                                                val propsJson =
                                                    entry.appProperties.entries.joinToString(",") { (k, v) ->
                                                        val escapedVal = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                                                        "\"$k\":\"$escapedVal\""
                                                    }
                                                """{"id":"${entry.id}","name":"${entry.name}","size":"${entry.bytes.size}","appProperties":{$propsJson}}"""
                                            }.joinToString(",")

                                    val resp =
                                        if (nextTokenStr != null) {
                                            """{"files":[$filesJson],"nextPageToken":"$nextTokenStr"}"""
                                        } else {
                                            """{"files":[$filesJson]}"""
                                        }
                                    return ByteArrayInputStream(resp.toByteArray())
                                }
                            }
                            return ByteArrayInputStream(byteArrayOf())
                        }

                        override fun getOutputStream(): OutputStream =
                            object : ByteArrayOutputStream() {
                                override fun close() {
                                    super.close()
                                    if (urlString.contains("uploadType=multipart")) {
                                        val body = String(toByteArray(), Charsets.UTF_8)
                                        val snapId = body.substringAfter("\"snapshotId\":\"", "").substringBefore("\"")
                                        val devId = body.substringAfter("\"creatorDeviceId\":\"", "").substringBefore("\"")
                                        val id = pendingUploadId ?: "drive_file_${idCounter++}"
                                        pendingUploadId = null
                                        files[id] =
                                            DriveEntry(
                                                id = id,
                                                name = "snapshot_${devId}_$snapId.bin",
                                                bytes = toByteArray(),
                                                appProperties = mapOf("snapshotId" to snapId, "type" to SnapshotTransportService.TYPE_SNAPSHOT),
                                            )
                                    }
                                }
                            }

                        override fun connect() {}

                        override fun disconnect() {}

                        override fun usingProxy(): Boolean = false
                    }
                },
            )
        }
    }
}
