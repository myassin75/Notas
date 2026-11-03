package haddad.three.notas.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import haddad.three.notas.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendApiClient(
    private val httpFactory: (String) -> HttpURLConnection,
) {
    @Inject
    constructor() : this({ url ->
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = false
        }
    })
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getProfile(idToken: String): AuthResult<BackendProfile> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BACKEND_API_BASE_URL.trimEnd('/')
        val endpoint = "$baseUrl/api/v1/me"

        try {
            val connection = httpFactory(endpoint).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            when (statusCode) {
                in 200..299 -> {
                    val apiResponse = json.decodeFromString<BackendApiResponse<BackendProfile>>(responseBody)
                    if (apiResponse.success && apiResponse.data != null) {
                        AuthResult.Success(apiResponse.data)
                    } else {
                        AuthResult.Error(apiResponse.error?.message ?: "Unknown backend error")
                    }
                }
                in listOf(301, 302, 307, 308) -> {
                    val location = connection.getHeaderField("Location").orEmpty()
                    if (location.contains("vercel.com") || location.contains("sso")) {
                        AuthResult.Error(
                            "Staging server redirect: Vercel deployment protection active. Please disable deployment protection on the staging project in Vercel dashboard.",
                            code = "DEPLOYMENT_PROTECTION_REDIRECT"
                        )
                    } else {
                        AuthResult.Error("Unexpected HTTP redirect to $location", code = "REDIRECT_$statusCode")
                    }
                }
                401 -> AuthResult.Error("Unauthorized: Session has expired. Please sign in again.", code = "UNAUTHORIZED")
                403 -> AuthResult.Error("Access forbidden: Account cannot access this resource.", code = "FORBIDDEN")
                429 -> AuthResult.Error("Rate limit exceeded. Please wait a moment before trying again.", code = "RATE_LIMIT")
                in 500..599 -> AuthResult.Error("Backend server error ($statusCode). Please try again later.", code = "SERVER_ERROR")
                else -> AuthResult.Error("HTTP request failed with status $statusCode", code = "HTTP_$statusCode")
            }
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.localizedMessage ?: "Network error connecting to backend",
                code = "NETWORK_ERROR",
                cause = e,
            )
        }
    }
}
