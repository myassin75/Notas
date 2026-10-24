package three.haddad.smartnotas.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import three.haddad.smartnotas.BuildConfig
import three.haddad.smartnotas.data.auth.BackendApiResponse
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles authenticated HTTP communication with the Notas backend sync endpoints.
 */
@Singleton
open class SyncApiClient(
    private val httpFactory: (String) -> HttpURLConnection,
) {
    @Inject
    constructor() : this({ url ->
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
        }
    })

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    open suspend fun pushSync(
        idToken: String,
        payload: SyncPushRequestDto,
    ): SyncResult<SyncPushResponseDto> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BACKEND_API_BASE_URL.trimEnd('/')
        val endpoint = "$baseUrl/api/v1/sync/push"

        try {
            val connection = httpFactory(endpoint).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val requestBody = json.encodeToString(payload)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            when (statusCode) {
                in 200..299 -> {
                    val apiResponse = json.decodeFromString<BackendApiResponse<SyncPushResponseDto>>(responseBody)
                    if (apiResponse.success && apiResponse.data != null) {
                        SyncResult.Success(apiResponse.data)
                    } else {
                        SyncResult.Error(
                            message = apiResponse.error?.message ?: "Unknown sync push error",
                            code = apiResponse.error?.code ?: "PUSH_FAILED",
                            statusCode = statusCode,
                        )
                    }
                }
                401 -> SyncResult.Unauthorized
                429 -> SyncResult.RateLimited
                in 500..599 -> SyncResult.Error(
                    message = "Backend server error ($statusCode)",
                    code = "SERVER_ERROR",
                    statusCode = statusCode,
                )
                else -> SyncResult.Error(
                    message = "HTTP push failed with status $statusCode",
                    code = "HTTP_$statusCode",
                    statusCode = statusCode,
                )
            }
        } catch (e: Exception) {
            SyncResult.Error(
                message = e.localizedMessage ?: "Network error during sync push",
                code = "NETWORK_ERROR",
                cause = e,
            )
        }
    }

    open suspend fun fetchChanges(
        idToken: String,
        sinceRevision: String,
        limit: Int = 100,
    ): SyncResult<SyncChangesResponseDto> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BACKEND_API_BASE_URL.trimEnd('/')
        val endpoint = "$baseUrl/api/v1/sync/changes?sinceRevision=$sinceRevision&limit=$limit"

        try {
            val connection = httpFactory(endpoint).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            when (statusCode) {
                in 200..299 -> {
                    val apiResponse = json.decodeFromString<BackendApiResponse<SyncChangesResponseDto>>(responseBody)
                    if (apiResponse.success && apiResponse.data != null) {
                        SyncResult.Success(apiResponse.data)
                    } else {
                        SyncResult.Error(
                            message = apiResponse.error?.message ?: "Unknown sync changes error",
                            code = apiResponse.error?.code ?: "FETCH_FAILED",
                            statusCode = statusCode,
                        )
                    }
                }
                401 -> SyncResult.Unauthorized
                429 -> SyncResult.RateLimited
                in 500..599 -> SyncResult.Error(
                    message = "Backend server error ($statusCode)",
                    code = "SERVER_ERROR",
                    statusCode = statusCode,
                )
                else -> SyncResult.Error(
                    message = "HTTP fetch failed with status $statusCode",
                    code = "HTTP_$statusCode",
                    statusCode = statusCode,
                )
            }
        } catch (e: Exception) {
            SyncResult.Error(
                message = e.localizedMessage ?: "Network error during sync fetch",
                code = "NETWORK_ERROR",
                cause = e,
            )
        }
    }
}
