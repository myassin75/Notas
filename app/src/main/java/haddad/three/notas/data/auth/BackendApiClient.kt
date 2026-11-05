package haddad.three.notas.data.auth

import haddad.three.notas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendApiClient(
    private val httpFactory: (String) -> HttpURLConnection,
    private val customBaseUrl: String? = null,
) {
    @Inject
    constructor() : this({ url ->
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = false
        }
    }, null)

    constructor(httpFactory: (String) -> HttpURLConnection) : this(httpFactory, null)

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    val baseUrl: String
        get() =
            (customBaseUrl ?: BuildConfig.BACKEND_API_BASE_URL)
                .trimEnd('/')
                .removeSuffix("/api/v1")
                .removeSuffix("/api")

    suspend fun getProfile(idToken: String): AuthResult<BackendProfile> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/me"

            try {
                val connection =
                    httpFactory(endpoint).apply {
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
                                code = "DEPLOYMENT_PROTECTION_REDIRECT",
                            )
                        } else {
                            AuthResult.Error("Unexpected HTTP redirect to $location", code = "REDIRECT_$statusCode")
                        }
                    }
                    401 -> AuthResult.Error("Unauthorized: Session has expired. Please sign in again.", code = "UNAUTHORIZED")
                    403 -> AuthResult.Error("Access forbidden: Account cannot access this resource.", code = "FORBIDDEN")
                    404 -> {
                        val errorMsg =
                            try {
                                val apiResponse = json.decodeFromString<BackendApiResponse<BackendProfile>>(responseBody)
                                apiResponse.error?.message ?: "Backend endpoint not found (HTTP 404)"
                            } catch (_: Exception) {
                                "Backend endpoint not found (HTTP 404). Please verify that the backend is deployed and reachable."
                            }
                        AuthResult.Error(errorMsg, code = "NOT_FOUND")
                    }
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

    suspend fun sendVerificationEmail(idToken: String): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/auth/send-verification-email"

            try {
                val connection =
                    httpFactory(endpoint).apply {
                        requestMethod = "POST"
                        instanceFollowRedirects = false
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                    }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                when (statusCode) {
                    in 200..299 -> AuthResult.Success(Unit)
                    400 -> {
                        val errorMsg =
                            try {
                                val apiResponse = json.decodeFromString<BackendApiResponse<SendVerificationEmailResponse>>(responseBody)
                                apiResponse.error?.message ?: "Bad Request (HTTP 400)"
                            } catch (_: Exception) {
                                "Bad Request (HTTP 400)"
                            }
                        AuthResult.Error(errorMsg, code = "BAD_REQUEST")
                    }
                    401 -> AuthResult.Error("Unauthorized: Session has expired. Please sign in again.", code = "UNAUTHORIZED")
                    429 -> AuthResult.Error("Rate limit exceeded. Please wait a moment before requesting another verification email.", code = "RATE_LIMIT")
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

    suspend fun sendOtpVerification(idToken: String): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/auth/otp/send-verification"

            try {
                val connection =
                    httpFactory(endpoint).apply {
                        requestMethod = "POST"
                        instanceFollowRedirects = false
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                    }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                when (statusCode) {
                    in 200..299 -> AuthResult.Success(Unit)
                    429 -> AuthResult.Error("Please wait before requesting a new code.", code = "COOLDOWN_ACTIVE")
                    401 -> AuthResult.Error("Session expired. Please sign in again.", code = "UNAUTHORIZED")
                    else -> {
                        val errorMsg =
                            try {
                                val apiResponse = json.decodeFromString<BackendApiResponse<Unit>>(responseBody)
                                apiResponse.error?.message ?: "Failed to send verification code ($statusCode)"
                            } catch (_: Exception) {
                                "Failed to send verification code ($statusCode)"
                            }
                        AuthResult.Error(errorMsg, code = "HTTP_$statusCode")
                    }
                }
            } catch (e: Exception) {
                AuthResult.Error(
                    message = e.localizedMessage ?: "Network error connecting to backend",
                    code = "NETWORK_ERROR",
                    cause = e,
                )
            }
        }

    suspend fun verifyOtpCode(
        idToken: String,
        code: String,
    ): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/auth/otp/verify-code"

            try {
                val connection =
                    httpFactory(endpoint).apply {
                        requestMethod = "POST"
                        doOutput = true
                        instanceFollowRedirects = false
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                    }

                val requestJson = json.encodeToString(VerifyOtpRequest.serializer(), VerifyOtpRequest(code))
                connection.outputStream.bufferedWriter().use { it.write(requestJson) }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                when (statusCode) {
                    200, 202 -> AuthResult.Success(Unit)
                    400 -> {
                        val errorMsg =
                            try {
                                val apiResponse = json.decodeFromString<BackendApiResponse<Unit>>(responseBody)
                                apiResponse.error?.message ?: "Invalid verification code."
                            } catch (_: Exception) {
                                "Invalid verification code."
                            }
                        AuthResult.Error(errorMsg, code = "INVALID_CODE")
                    }
                    409 -> AuthResult.Error("Verification code was already used.", code = "CODE_ALREADY_USED")
                    401 -> AuthResult.Error("Session expired. Please sign in again.", code = "UNAUTHORIZED")
                    else -> AuthResult.Error("Verification failed with status $statusCode", code = "HTTP_$statusCode")
                }
            } catch (e: Exception) {
                AuthResult.Error(
                    message = e.localizedMessage ?: "Network error connecting to backend",
                    code = "NETWORK_ERROR",
                    cause = e,
                )
            }
        }

    suspend fun requestPasswordResetOtp(email: String): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/auth/otp/request-password-reset"

            try {
                val connection =
                    httpFactory(endpoint).apply {
                        requestMethod = "POST"
                        doOutput = true
                        instanceFollowRedirects = false
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                    }

                val requestJson = json.encodeToString(RequestPasswordResetRequest.serializer(), RequestPasswordResetRequest(email))
                connection.outputStream.bufferedWriter().use { it.write(requestJson) }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                when (statusCode) {
                    in 200..299 -> AuthResult.Success(Unit)
                    429 -> AuthResult.Error("Too many password reset requests. Please wait a moment.", code = "RATE_LIMIT")
                    else -> AuthResult.Error("Failed to request password reset code ($statusCode)", code = "HTTP_$statusCode")
                }
            } catch (e: Exception) {
                AuthResult.Error(
                    message = e.localizedMessage ?: "Network error connecting to backend",
                    code = "NETWORK_ERROR",
                    cause = e,
                )
            }
        }

    suspend fun resetPasswordWithOtp(
        email: String,
        code: String,
        newPassword: String,
    ): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/auth/otp/reset-password"

            try {
                val connection =
                    httpFactory(endpoint).apply {
                        requestMethod = "POST"
                        doOutput = true
                        instanceFollowRedirects = false
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                    }

                val requestJson = json.encodeToString(ResetPasswordRequest.serializer(), ResetPasswordRequest(email, code, newPassword))
                connection.outputStream.bufferedWriter().use { it.write(requestJson) }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                when (statusCode) {
                    in 200..299 -> AuthResult.Success(Unit)
                    400 -> {
                        val errorMsg =
                            try {
                                val apiResponse = json.decodeFromString<BackendApiResponse<Unit>>(responseBody)
                                apiResponse.error?.message ?: "Invalid reset code or password."
                            } catch (_: Exception) {
                                "Invalid reset code or password."
                            }
                        AuthResult.Error(errorMsg, code = "BAD_REQUEST")
                    }
                    else -> AuthResult.Error("Password reset failed ($statusCode)", code = "HTTP_$statusCode")
                }
            } catch (e: Exception) {
                AuthResult.Error(
                    message = e.localizedMessage ?: "Network error connecting to backend",
                    code = "NETWORK_ERROR",
                    cause = e,
                )
            }
        }

    suspend fun getOtpStatus(idToken: String): AuthResult<Boolean> =
        withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/v1/auth/otp/status"

            try {
                val connection =
                    httpFactory(endpoint).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Accept", "application/json")
                    }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                if (statusCode in 200..299) {
                    val apiResponse = json.decodeFromString<BackendApiResponse<OtpStatusResponse>>(responseBody)
                    val isVerified = apiResponse.data?.emailVerified ?: false
                    AuthResult.Success(isVerified)
                } else {
                    AuthResult.Error("Failed to check verification status ($statusCode)", code = "HTTP_$statusCode")
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
