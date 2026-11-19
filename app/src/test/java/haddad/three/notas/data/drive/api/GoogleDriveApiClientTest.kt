package haddad.three.notas.data.drive.api

import haddad.three.notas.data.drive.model.DriveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class GoogleDriveApiClientTest {
    private class MockHttpConnection(
        url: URL,
        private val responseCodeToReturn: Int = 200,
        private val responseHeaders: Map<String, String> = emptyMap(),
        private val responseBody: String = "{}",
    ) : HttpURLConnection(url) {
        val outputBytes = ByteArrayOutputStream()

        override fun getResponseCode(): Int = responseCodeToReturn

        override fun getHeaderField(name: String?): String? = responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getOutputStream(): OutputStream = outputBytes

        override fun disconnect() {}

        override fun usingProxy(): Boolean = false

        override fun connect() {}
    }

    @Test
    fun testListFilesSuccessWithPagination() =
        kotlinx.coroutines.runBlocking {
            val jsonResponse =
                """
                {
                  "files": [
                    {
                      "id": "file_123",
                      "name": "notas_backup_1001.bin",
                      "mimeType": "application/octet-stream",
                      "size": "1048576",
                      "createdTime": "2026-11-04T12:00:00.000Z",
                      "appProperties": {
                        "backupId": "backup_abc",
                        "schemaVersion": "5",
                        "notesCount": "10"
                      }
                    }
                  ],
                  "nextPageToken": "page_token_xyz"
                }
                """.trimIndent()

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("spaces=appDataFolder"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = jsonResponse)
                    },
                )

            val result = client.listFiles("test_token", pageSize = 10)
            assertTrue(result is DriveResult.Success)
            val data = (result as DriveResult.Success).data
            assertEquals(1, data.files.size)
            assertEquals("file_123", data.files[0].id)
            assertEquals("page_token_xyz", data.nextPageToken)
        }

    @Test
    fun testUploadMultipart() =
        kotlinx.coroutines.runBlocking {
            val responseBody =
                """
                {
                  "id": "uploaded_file_456",
                  "name": "notas_backup_2002.bin",
                  "size": "512"
                }
                """.trimIndent()

            lateinit var capturedConn: MockHttpConnection

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("uploadType=multipart"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = responseBody).also {
                            capturedConn = it
                        }
                    },
                )

            val metadataJson = """{"name":"notas_backup_2002.bin","parents":["appDataFolder"]}"""
            val payload = "ENCRYPTED_CONTAINER_BYTES".toByteArray(Charsets.UTF_8)

            val result = client.uploadMultipart("test_token", metadataJson, payload)
            assertTrue(result is DriveResult.Success)
            assertEquals("uploaded_file_456", (result as DriveResult.Success).data.id)

            // Verify multipart boundary & parts in request output
            val requestString = capturedConn.outputBytes.toString("UTF-8")
            assertTrue(requestString.contains("Content-Type: application/json; charset=UTF-8"))
            assertTrue(requestString.contains("notas_backup_2002.bin"))
            assertTrue(requestString.contains("ENCRYPTED_CONTAINER_BYTES"))
        }

    @Test
    fun testUploadStreamResumable() =
        kotlinx.coroutines.runBlocking {
            val sessionUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=session_777"
            val finalResponseBody = """{"id":"resumable_file_888","name":"large_backup.bin","size":"10240"}"""

            val tempFile =
                File.createTempFile("resumable_test_", ".bin").apply {
                    writeBytes(ByteArray(10240) { 0x42 })
                    deleteOnExit()
                }

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        if (url == sessionUrl) {
                            // Step 2: Stream upload to session URL
                            MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = finalResponseBody)
                        } else {
                            // Step 1: Session initialization
                            MockHttpConnection(
                                URL(url),
                                responseCodeToReturn = 200,
                                responseHeaders = mapOf("Location" to sessionUrl),
                            )
                        }
                    },
                )

            var reportedProgress = 0f
            val result =
                client.uploadStreamResumable(
                    accessToken = "test_token",
                    metadataJson = """{"name":"large_backup.bin","parents":["appDataFolder"]}""",
                    file = tempFile,
                    onProgress = { reportedProgress = it },
                )

            assertTrue(result is DriveResult.Success)
            assertEquals("resumable_file_888", (result as DriveResult.Success).data.id)
            assertEquals(1.0f, reportedProgress, 0.001f)
        }

    @Test
    fun testDownloadFile() =
        kotlinx.coroutines.runBlocking {
            val payload = "DECRYPTED_BINARY_CONTAINER_STREAM".toByteArray(Charsets.UTF_8)
            val targetFile = File.createTempFile("download_target_", ".bin").apply { deleteOnExit() }

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("alt=media"))
                        MockHttpConnection(
                            URL(url),
                            responseCodeToReturn = 200,
                            responseHeaders = mapOf("Content-Length" to payload.size.toString()),
                            responseBody = String(payload, Charsets.UTF_8),
                        )
                    },
                )

            val result = client.downloadFile("test_token", "file_999", targetFile)
            assertTrue(result is DriveResult.Success)
            assertEquals("DECRYPTED_BINARY_CONTAINER_STREAM", targetFile.readText(Charsets.UTF_8))
        }

    @Test
    fun testDeleteFile() =
        kotlinx.coroutines.runBlocking {
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("file_to_delete"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 204)
                    },
                )

            val result = client.deleteFile("test_token", "file_to_delete")
            assertTrue(result is DriveResult.Success)
        }

    @Test
    fun testUnauthorizedAndRateLimitHandling() =
        kotlinx.coroutines.runBlocking {
            // 401 Unauthorized
            val unauthClient =
                GoogleDriveApiClient(
                    httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 401) },
                )
            val unauthResult = unauthClient.listFiles("expired_token")
            assertTrue(unauthResult is DriveResult.Unauthorized)

            // 403 Forbidden
            val forbiddenClient =
                GoogleDriveApiClient(
                    httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 403) },
                )
            val forbiddenResult = forbiddenClient.listFiles("forbidden_token")
            assertTrue(forbiddenResult is DriveResult.Error)
            assertEquals("FORBIDDEN", (forbiddenResult as DriveResult.Error).code)
            assertEquals(403, forbiddenResult.statusCode)

            // 429 Rate Limited
            val rateLimitClient =
                GoogleDriveApiClient(
                    httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 429) },
                )
            val rateLimitResult = rateLimitClient.listFiles("token")
            assertTrue(rateLimitResult is DriveResult.RateLimited)
        }

    @Test
    fun testNetworkFailureThrowsOrReturnsError() =
        kotlinx.coroutines.runBlocking {
            val networkErrorClient =
                GoogleDriveApiClient(
                    httpFactory = { _ -> throw java.io.IOException("Network unreachable") },
                )
            val result = networkErrorClient.listFiles("token")
            assertTrue(result is DriveResult.Error)
            assertTrue((result as DriveResult.Error).message.contains("Network unreachable"))
        }

    @Test
    fun testUpdateFileContent() =
        kotlinx.coroutines.runBlocking {
            val responseBody =
                """
                {
                  "id": "file_patch_123",
                  "name": "manifest.enc",
                  "size": "256"
                }
                """.trimIndent()

            lateinit var capturedConn: MockHttpConnection

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("uploadType=media"))
                        assertTrue(url.contains("file_patch_123"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = responseBody).also {
                            capturedConn = it
                        }
                    },
                )

            val payload = "UPDATED_ENCRYPTED_MANIFEST".toByteArray(Charsets.UTF_8)
            val result = client.updateFileContent("test_token", "file_patch_123", payload)

            assertTrue(result is DriveResult.Success)
            assertEquals("file_patch_123", (result as DriveResult.Success).data.id)
            assertEquals(String(payload, Charsets.UTF_8), capturedConn.outputBytes.toString("UTF-8"))
        }

    @Test
    fun testUpdateFileContentNotFoundAndConflict() =
        kotlinx.coroutines.runBlocking {
            val notFoundClient =
                GoogleDriveApiClient(
                    httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 404) },
                )
            val notFoundResult = notFoundClient.updateFileContent("token", "missing_id", ByteArray(10))
            assertTrue(notFoundResult is DriveResult.Error)
            assertEquals("NOT_FOUND", (notFoundResult as DriveResult.Error).code)
            assertEquals(404, notFoundResult.statusCode)

            val conflictClient =
                GoogleDriveApiClient(
                    httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 409) },
                )
            val conflictResult = conflictClient.updateFileContent("token", "conflicting_id", ByteArray(10))
            assertTrue(conflictResult is DriveResult.Error)
            assertEquals("CONFLICT", (conflictResult as DriveResult.Error).code)
            assertEquals(409, conflictResult.statusCode)
        }

    @Test
    fun testDownloadFileBytes() =
        kotlinx.coroutines.runBlocking {
            val payload = "BINARY_MANIFEST_OR_ATTACHMENT_PAYLOAD".toByteArray(Charsets.UTF_8)
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("alt=media"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = String(payload, Charsets.UTF_8))
                    },
                )

            val result = client.downloadFileBytes("token", "file_abc")
            assertTrue(result is DriveResult.Success)
            assertEquals(String(payload, Charsets.UTF_8), String((result as DriveResult.Success).data, Charsets.UTF_8))
        }

    @Test
    fun testGetChangesStartPageToken() =
        kotlinx.coroutines.runBlocking {
            val jsonResponse = """{"startPageToken": "start_token_12345"}"""
            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("/changes/startPageToken"))
                        assertTrue(url.contains("spaces=appDataFolder"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = jsonResponse)
                    },
                )

            val result = client.getChangesStartPageToken("token")
            assertTrue(result is DriveResult.Success)
            assertEquals("start_token_12345", (result as DriveResult.Success).data.startPageToken)
        }

    @Test
    fun testListChangesSuccess() =
        kotlinx.coroutines.runBlocking {
            val jsonResponse =
                """
                {
                  "nextPageToken": "page_2",
                  "newStartPageToken": "new_start_999",
                  "changes": [
                    {
                      "fileId": "file_mod_1",
                      "removed": false,
                      "file": {
                        "id": "file_mod_1",
                        "name": "journal_dev1_1.bin",
                        "appProperties": {
                          "appId": "notas",
                          "type": "sync_journal"
                        }
                      }
                    },
                    {
                      "fileId": "file_del_2",
                      "removed": true
                    }
                  ]
                }
                """.trimIndent()

            val client =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        assertTrue(url.contains("/changes"))
                        assertTrue(url.contains("pageToken=page_1"))
                        assertTrue(url.contains("spaces=appDataFolder"))
                        MockHttpConnection(URL(url), responseCodeToReturn = 200, responseBody = jsonResponse)
                    },
                )

            val result = client.listChanges("token", pageToken = "page_1")
            assertTrue(result is DriveResult.Success)
            val data = (result as DriveResult.Success).data
            assertEquals("page_2", data.nextPageToken)
            assertEquals("new_start_999", data.newStartPageToken)
            assertEquals(2, data.changes.size)
            assertEquals("file_mod_1", data.changes[0].fileId)
            assertEquals(false, data.changes[0].removed)
            assertEquals("file_del_2", data.changes[1].fileId)
            assertEquals(true, data.changes[1].removed)
        }

    @Test
    fun testListChangesCursorExpired() =
        kotlinx.coroutines.runBlocking {
            // HTTP 410 Gone
            val goneClient =
                GoogleDriveApiClient(
                    httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 410) },
                )
            val goneResult = goneClient.listChanges("token", pageToken = "expired_token")
            assertTrue(goneResult is DriveResult.CursorExpired)

            // HTTP 400 with startPageTokenExpired error body
            val expiredBodyClient =
                GoogleDriveApiClient(
                    httpFactory = { url ->
                        MockHttpConnection(
                            URL(url),
                            responseCodeToReturn = 400,
                            responseBody = """{"error": {"errors": [{"reason": "startPageTokenExpired"}]}}""",
                        )
                    },
                )
            val expiredBodyResult = expiredBodyClient.listChanges("token", pageToken = "expired_token_2")
            assertTrue(expiredBodyResult is DriveResult.CursorExpired)
        }
}
