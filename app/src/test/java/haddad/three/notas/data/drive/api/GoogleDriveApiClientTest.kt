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

        override fun getHeaderField(name: String?): String? =
            responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getInputStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getErrorStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getOutputStream(): OutputStream = outputBytes

        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun connect() {}
    }

    @Test
    fun testListFilesSuccessWithPagination() = kotlinx.coroutines.runBlocking {
        val jsonResponse = """
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

        val client = GoogleDriveApiClient(
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
    fun testUploadMultipart() = kotlinx.coroutines.runBlocking {
        val responseBody = """
            {
              "id": "uploaded_file_456",
              "name": "notas_backup_2002.bin",
              "size": "512"
            }
        """.trimIndent()

        lateinit var capturedConn: MockHttpConnection

        val client = GoogleDriveApiClient(
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
    fun testUploadStreamResumable() = kotlinx.coroutines.runBlocking {
        val sessionUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=session_777"
        val finalResponseBody = """{"id":"resumable_file_888","name":"large_backup.bin","size":"10240"}"""

        val tempFile = File.createTempFile("resumable_test_", ".bin").apply {
            writeBytes(ByteArray(10240) { 0x42 })
            deleteOnExit()
        }

        val client = GoogleDriveApiClient(
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
        val result = client.uploadStreamResumable(
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
    fun testDownloadFile() = kotlinx.coroutines.runBlocking {
        val payload = "DECRYPTED_BINARY_CONTAINER_STREAM".toByteArray(Charsets.UTF_8)
        val targetFile = File.createTempFile("download_target_", ".bin").apply { deleteOnExit() }

        val client = GoogleDriveApiClient(
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
    fun testDeleteFile() = kotlinx.coroutines.runBlocking {
        val client = GoogleDriveApiClient(
            httpFactory = { url ->
                assertTrue(url.contains("file_to_delete"))
                MockHttpConnection(URL(url), responseCodeToReturn = 204)
            },
        )

        val result = client.deleteFile("test_token", "file_to_delete")
        assertTrue(result is DriveResult.Success)
    }

    @Test
    fun testUnauthorizedAndRateLimitHandling() = kotlinx.coroutines.runBlocking {
        // 401 Unauthorized
        val unauthClient = GoogleDriveApiClient(
            httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 401) },
        )
        val unauthResult = unauthClient.listFiles("expired_token")
        assertTrue(unauthResult is DriveResult.Unauthorized)

        // 429 Rate Limited
        val rateLimitClient = GoogleDriveApiClient(
            httpFactory = { url -> MockHttpConnection(URL(url), responseCodeToReturn = 429) },
        )
        val rateLimitResult = rateLimitClient.listFiles("token")
        assertTrue(rateLimitResult is DriveResult.RateLimited)
    }
}
