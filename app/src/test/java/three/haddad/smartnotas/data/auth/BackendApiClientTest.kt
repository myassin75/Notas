package three.haddad.smartnotas.data.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
class BackendApiClientTest {

    private class FakeHttpURLConnection(
        private val code: Int,
        private val responseStream: InputStream? = null,
        private val errStream: InputStream? = null,
    ) : HttpURLConnection(URL("https://api.smartnotas.com/api/v1/me")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = responseStream ?: throw IOException("No input stream")
        override fun getErrorStream(): InputStream? = errStream
    }

    @Test
    fun getProfile_success200_parsesProfileCorrectly() = runTest {
        val jsonPayload = """
            {
                "success": true,
                "data": {
                    "id": "user-456",
                    "email": "alex@example.com",
                    "emailVerified": true,
                    "displayName": "Alex Morgan"
                }
            }
        """.trimIndent()

        val fakeConnection = FakeHttpURLConnection(
            code = 200,
            responseStream = ByteArrayInputStream(jsonPayload.toByteArray()),
        )

        val client = BackendApiClient { fakeConnection }
        val result = client.getProfile("sample-id-token")

        assertTrue(result is AuthResult.Success)
        val profile = (result as AuthResult.Success).data
        assertEquals("user-456", profile.id)
        assertEquals("alex@example.com", profile.email)
        assertTrue(profile.emailVerified)
        assertEquals("Alex Morgan", profile.displayName)
    }

    @Test
    fun getProfile_401Unauthorized_returnsUnauthorizedError() = runTest {
        val fakeConnection = FakeHttpURLConnection(
            code = 401,
            errStream = ByteArrayInputStream("""{"error":"token_expired"}""".toByteArray()),
        )

        val client = BackendApiClient { fakeConnection }
        val result = client.getProfile("expired-token")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals("UNAUTHORIZED", error.code)
        assertTrue(error.message.contains("expired"))
    }

    @Test
    fun getProfile_403Forbidden_returnsForbiddenError() = runTest {
        val fakeConnection = FakeHttpURLConnection(
            code = 403,
            errStream = ByteArrayInputStream("Forbidden".toByteArray()),
        )

        val client = BackendApiClient { fakeConnection }
        val result = client.getProfile("forbidden-token")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals("FORBIDDEN", error.code)
    }

    @Test
    fun getProfile_429RateLimit_returnsRateLimitError() = runTest {
        val fakeConnection = FakeHttpURLConnection(
            code = 429,
            errStream = ByteArrayInputStream("Rate limit exceeded".toByteArray()),
        )

        val client = BackendApiClient { fakeConnection }
        val result = client.getProfile("token-xyz")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals("RATE_LIMIT", error.code)
    }

    @Test
    fun getProfile_500ServerError_returnsServerError() = runTest {
        val fakeConnection = FakeHttpURLConnection(
            code = 500,
            errStream = ByteArrayInputStream("Internal server error".toByteArray()),
        )

        val client = BackendApiClient { fakeConnection }
        val result = client.getProfile("token-xyz")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals("SERVER_ERROR", error.code)
    }

    @Test
    fun getProfile_ioException_returnsNetworkError() = runTest {
        val client = BackendApiClient {
            throw IOException("Unable to resolve host")
        }
        val result = client.getProfile("token-xyz")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals("NETWORK_ERROR", error.code)
        assertNotNull(error.cause)
    }
}
