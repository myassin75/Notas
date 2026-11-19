package haddad.three.notas.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
class SyncApiClientTest {
    private class FakeHttpURLConnection(
        private val code: Int,
        private val responseStream: InputStream? = null,
        private val errStream: InputStream? = null,
    ) : HttpURLConnection(URL("https://api.smartnotas.com/api/v1/sync/push")) {
        val capturedOutput = ByteArrayOutputStream()

        override fun connect() {}

        override fun disconnect() {}

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = code

        override fun getInputStream(): InputStream = responseStream ?: throw IOException("No input stream")

        override fun getErrorStream(): InputStream? = errStream

        override fun getOutputStream(): OutputStream = capturedOutput
    }

    @Test
    fun `pushSync success 200 parses serverRevision and counts correctly`() =
        runTest {
            val jsonPayload =
                """
                {
                    "success": true,
                    "data": {
                        "serverRevision": "1735000000000",
                        "appliedCount": {
                            "categories": 2,
                            "notes": 5,
                            "settings": 0
                        },
                        "conflicts": []
                    }
                }
                """.trimIndent()

            val fakeConnection =
                FakeHttpURLConnection(
                    code = 200,
                    responseStream = ByteArrayInputStream(jsonPayload.toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val payload =
                SyncPushRequestDto(
                    deviceId = "dev-123",
                    deviceName = "Pixel 8",
                    appVersion = "1.0.0",
                )

            val result = client.pushSync("test-token", payload)

            assertTrue(result is SyncResult.Success)
            val response = (result as SyncResult.Success).data
            assertEquals("1735000000000", response.serverRevision)
            assertEquals(2, response.appliedCount.categories)
            assertEquals(5, response.appliedCount.notes)
            assertTrue(response.conflicts.isEmpty())
            assertTrue(fakeConnection.capturedOutput.size() > 0)
        }

    @Test
    fun `pushSync success 200 parses conflicts correctly`() =
        runTest {
            val jsonPayload =
                """
                {
                    "success": true,
                    "data": {
                        "serverRevision": "1735000000000",
                        "appliedCount": {
                            "categories": 0,
                            "notes": 0,
                            "settings": 0
                        },
                        "conflicts": [
                            {
                                "entityType": "note",
                                "id": "note-uuid-stale",
                                "serverRevision": "1734999999999",
                                "reason": "Stale update: Server has newer note modification"
                            }
                        ]
                    }
                }
                """.trimIndent()

            val fakeConnection =
                FakeHttpURLConnection(
                    code = 200,
                    responseStream = ByteArrayInputStream(jsonPayload.toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.pushSync("test-token", SyncPushRequestDto(deviceId = "dev-1"))

            assertTrue(result is SyncResult.Success)
            val response = (result as SyncResult.Success).data
            assertEquals(1, response.conflicts.size)
            assertEquals("note", response.conflicts[0].entityType)
            assertEquals("note-uuid-stale", response.conflicts[0].id)
            assertEquals("Stale update: Server has newer note modification", response.conflicts[0].reason)
        }

    @Test
    fun `pushSync 401 returns Unauthorized`() =
        runTest {
            val fakeConnection =
                FakeHttpURLConnection(
                    code = 401,
                    errStream = ByteArrayInputStream("""{"error":"token_expired"}""".toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.pushSync("expired-token", SyncPushRequestDto(deviceId = "dev-1"))

            assertTrue(result is SyncResult.Unauthorized)
        }

    @Test
    fun `pushSync 429 returns RateLimited`() =
        runTest {
            val fakeConnection =
                FakeHttpURLConnection(
                    code = 429,
                    errStream = ByteArrayInputStream("Rate limit exceeded".toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.pushSync("token", SyncPushRequestDto(deviceId = "dev-1"))

            assertTrue(result is SyncResult.RateLimited)
        }

    @Test
    fun `pushSync 500 returns ServerError`() =
        runTest {
            val fakeConnection =
                FakeHttpURLConnection(
                    code = 500,
                    errStream = ByteArrayInputStream("Database error".toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.pushSync("token", SyncPushRequestDto(deviceId = "dev-1"))

            assertTrue(result is SyncResult.Error)
            val error = result as SyncResult.Error
            assertEquals("SERVER_ERROR", error.code)
            assertEquals(500, error.statusCode)
        }

    @Test
    fun `pushSync ioException returns NetworkError`() =
        runTest {
            val client = SyncApiClient { throw IOException("Connection reset by peer") }
            val result = client.pushSync("token", SyncPushRequestDto(deviceId = "dev-1"))

            assertTrue(result is SyncResult.Error)
            val error = result as SyncResult.Error
            assertEquals("NETWORK_ERROR", error.code)
            assertNotNull(error.cause)
        }

    @Test
    fun `fetchChanges success 200 parses changes and pagination correctly`() =
        runTest {
            val jsonPayload =
                """
                {
                    "success": true,
                    "data": {
                        "currentServerRevision": "1735100000000",
                        "categories": [
                            {
                                "id": "cat-uuid-1",
                                "name": "Finance",
                                "normalizedName": "finance",
                                "colorHex": "#00FF00",
                                "clientCreatedAt": "2026-01-01T12:00:00Z",
                                "clientUpdatedAt": "2026-01-01T12:00:00Z",
                                "isDeleted": false
                            }
                        ],
                        "notes": [
                            {
                                "id": "note-uuid-1",
                                "categoryId": "cat-uuid-1",
                                "kind": "NOTE",
                                "title": "Taxes",
                                "body": "File quarterly taxes",
                                "colorIndex": 2,
                                "importance": "HIGH",
                                "visibility": "DEFAULT",
                                "locked": false,
                                "isAllDay": false,
                                "archived": false,
                                "trashed": false,
                                "clientCreatedAt": "2026-01-01T12:00:00Z",
                                "clientUpdatedAt": "2026-01-01T12:30:00Z",
                                "isDeleted": false,
                                "checklistItems": [],
                                "attachments": []
                            }
                        ],
                        "settings": null,
                        "hasMore": false
                    }
                }
                """.trimIndent()

            val fakeConnection =
                FakeHttpURLConnection(
                    code = 200,
                    responseStream = ByteArrayInputStream(jsonPayload.toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.fetchChanges("token", sinceRevision = "0", limit = 100)

            assertTrue(result is SyncResult.Success)
            val changes = (result as SyncResult.Success).data
            assertEquals("1735100000000", changes.currentServerRevision)
            assertEquals(1, changes.categories.size)
            assertEquals("Finance", changes.categories[0].name)
            assertEquals(1, changes.notes.size)
            assertEquals("Taxes", changes.notes[0].title)
            assertFalse(changes.hasMore)
        }

    @Test
    fun `fetchChanges 401 returns Unauthorized`() =
        runTest {
            val fakeConnection =
                FakeHttpURLConnection(
                    code = 401,
                    errStream = ByteArrayInputStream("""{"error":"token_expired"}""".toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.fetchChanges("token", sinceRevision = "0")

            assertTrue(result is SyncResult.Unauthorized)
        }

    @Test
    fun `pushSync 404 returns NotFound`() =
        runTest {
            val fakeConnection =
                FakeHttpURLConnection(
                    code = 404,
                    errStream = ByteArrayInputStream("""{"success":false,"error":{"code":"NOT_FOUND","message":"Sync endpoint not found"}}""".toByteArray()),
                )

            val client = SyncApiClient { fakeConnection }
            val result = client.pushSync("test-token", SyncPushRequestDto("dev-1", "Pixel", "1.0"))

            assertTrue(result is SyncResult.Error)
            val err = result as SyncResult.Error
            assertEquals("NOT_FOUND", err.code)
            assertEquals(404, err.statusCode)
            assertEquals("Sync endpoint not found", err.message)
        }

    @Test
    fun `pushSync baseUrlSanitization strips trailing slashes and api v1`() =
        runTest {
            var requestedUrl: String? = null
            val fakeConnection =
                FakeHttpURLConnection(
                    code = 200,
                    responseStream = ByteArrayInputStream("""{"success":true,"data":{"serverRevision":"1","appliedCount":{"categories":0,"notes":0,"settings":0},"conflicts":[]}}""".toByteArray()),
                )

            val client =
                SyncApiClient(
                    httpFactory = { url ->
                        requestedUrl = url
                        fakeConnection
                    },
                    customBaseUrl = "https://notas-backend-iota.vercel.app/api/v1/",
                )

            val result = client.pushSync("token", SyncPushRequestDto("dev-1", "Pixel", "1.0"))
            assertTrue(result is SyncResult.Success)
            assertEquals("https://notas-backend-iota.vercel.app/api/v1/sync/push", requestedUrl)
        }
}
