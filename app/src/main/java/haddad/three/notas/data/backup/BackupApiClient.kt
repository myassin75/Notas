package haddad.three.notas.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import haddad.three.notas.BuildConfig
import haddad.three.notas.data.auth.BackendApiResponse
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles authenticated HTTP communication with Notas cloud backup and private attachment endpoints,
 * as well as direct streaming transfers to and from presigned S3/R2 storage URLs.
 */
@Singleton
open class BackupApiClient(
    private val httpFactory: (String) -> HttpURLConnection,
) {
    @Inject
    constructor() : this({ url ->
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
        }
    })

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val apiBaseUrl: String
        get() = BuildConfig.BACKEND_API_BASE_URL.trimEnd('/')

    open suspend fun createBackup(
        idToken: String,
        payload: CreateBackupRequestDto,
    ): CloudBackupResult<CreateBackupResponseDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/backups"
        postJson(endpoint, idToken, payload)
    }

    open suspend fun verifyBackup(
        idToken: String,
        backupId: String,
    ): CloudBackupResult<VerifyBackupResponseDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/backups/$backupId/verify"
        postJson(endpoint, idToken, null as String?)
    }

    open suspend fun listBackups(
        idToken: String,
    ): CloudBackupResult<List<BackupItemDto>> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/backups"
        getJson(endpoint, idToken)
    }

    open suspend fun getBackup(
        idToken: String,
        backupId: String,
    ): CloudBackupResult<BackupItemDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/backups/$backupId"
        getJson(endpoint, idToken)
    }

    open suspend fun restoreBackup(
        idToken: String,
        backupId: String,
    ): CloudBackupResult<RestoreBackupResponseDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/backups/$backupId/restore"
        postJson(endpoint, idToken, null as String?)
    }

    open suspend fun getAttachmentUploadUrl(
        idToken: String,
        payload: AttachmentUploadUrlRequestDto,
    ): CloudBackupResult<AttachmentUploadUrlResponseDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/attachments/upload-url"
        postJson(endpoint, idToken, payload)
    }

    open suspend fun verifyAttachment(
        idToken: String,
        attachmentId: String,
    ): CloudBackupResult<AttachmentVerifyResponseDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/attachments/$attachmentId/verify"
        postJson(endpoint, idToken, null as String?)
    }

    open suspend fun getAttachmentDownloadUrl(
        idToken: String,
        attachmentId: String,
    ): CloudBackupResult<AttachmentDownloadUrlResponseDto> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/attachments/$attachmentId/download-url"
        getJson(endpoint, idToken)
    }

    open suspend fun deleteAttachment(
        idToken: String,
        attachmentId: String,
    ): CloudBackupResult<Unit> = withContext(Dispatchers.IO) {
        val endpoint = "$apiBaseUrl/api/v1/attachments/$attachmentId"
        try {
            val connection = httpFactory(endpoint).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
            }
            val statusCode = connection.responseCode
            if (statusCode in 200..299) {
                CloudBackupResult.Success(Unit)
            } else {
                CloudBackupResult.Error("Failed to delete attachment: HTTP $statusCode", statusCode = statusCode)
            }
        } catch (e: Exception) {
            CloudBackupResult.Error(e.localizedMessage ?: "Failed to delete attachment", cause = e)
        }
    }

    /**
     * Uploads a file directly to an S3/R2 presigned upload URL via HTTP PUT.
     */
    open suspend fun uploadFileToPresignedUrl(
        presignedUrl: String,
        file: File,
        contentType: String = "application/zip",
        onProgress: ((Float) -> Unit)? = null,
    ): CloudBackupResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val connection = httpFactory(presignedUrl).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setFixedLengthStreamingMode(file.length())
            }

            val totalBytes = file.length()
            var bytesUploaded = 0L

            FileInputStream(file).use { input ->
                connection.outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesUploaded += read
                        if (totalBytes > 0) {
                            onProgress?.invoke(bytesUploaded.toFloat() / totalBytes.toFloat())
                        }
                    }
                    output.flush()
                }
            }

            val statusCode = connection.responseCode
            if (statusCode in 200..299) {
                CloudBackupResult.Success(Unit)
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                CloudBackupResult.Error(
                    message = "S3 direct upload failed with status $statusCode: $errorMsg",
                    code = "S3_UPLOAD_ERROR",
                    statusCode = statusCode,
                )
            }
        } catch (e: Exception) {
            CloudBackupResult.Error(
                message = e.localizedMessage ?: "Network error during S3 direct upload",
                code = "S3_NETWORK_ERROR",
                cause = e,
            )
        }
    }

    /**
     * Downloads a file directly from an S3/R2 presigned download URL via HTTP GET.
     */
    open suspend fun downloadFileFromPresignedUrl(
        presignedUrl: String,
        targetFile: File,
        onProgress: ((Float) -> Unit)? = null,
    ): CloudBackupResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val connection = httpFactory(presignedUrl).apply {
                requestMethod = "GET"
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                return@withContext CloudBackupResult.Error(
                    message = "S3 direct download failed with status $statusCode",
                    code = "S3_DOWNLOAD_ERROR",
                    statusCode = statusCode,
                )
            }

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            targetFile.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        if (totalBytes > 0) {
                            onProgress?.invoke(bytesDownloaded.toFloat() / totalBytes.toFloat())
                        }
                    }
                    output.flush()
                }
            }

            CloudBackupResult.Success(Unit)
        } catch (e: Exception) {
            CloudBackupResult.Error(
                message = e.localizedMessage ?: "Network error during S3 direct download",
                code = "S3_DOWNLOAD_NETWORK_ERROR",
                cause = e,
            )
        }
    }

    private inline fun <reified T, reified B> postJson(
        endpoint: String,
        idToken: String,
        body: B?,
    ): CloudBackupResult<T> {
        try {
            val connection = httpFactory(endpoint).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            if (body != null) {
                val requestBody = json.encodeToString(body)
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }
            } else {
                connection.outputStream.close()
            }

            return handleApiResponse(connection)
        } catch (e: Exception) {
            return CloudBackupResult.Error(
                message = e.localizedMessage ?: "Network error during POST request",
                code = "NETWORK_ERROR",
                cause = e,
            )
        }
    }

    private inline fun <reified T> getJson(
        endpoint: String,
        idToken: String,
    ): CloudBackupResult<T> {
        try {
            val connection = httpFactory(endpoint).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
            }
            return handleApiResponse(connection)
        } catch (e: Exception) {
            return CloudBackupResult.Error(
                message = e.localizedMessage ?: "Network error during GET request",
                code = "NETWORK_ERROR",
                cause = e,
            )
        }
    }

    private inline fun <reified T> handleApiResponse(connection: HttpURLConnection): CloudBackupResult<T> {
        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

        return when (statusCode) {
            in 200..299 -> {
                val apiResponse = json.decodeFromString<BackendApiResponse<T>>(responseBody)
                if (apiResponse.success && apiResponse.data != null) {
                    CloudBackupResult.Success(apiResponse.data)
                } else {
                    CloudBackupResult.Error(
                        message = apiResponse.error?.message ?: "Unknown API error",
                        code = apiResponse.error?.code ?: "API_ERROR",
                        statusCode = statusCode,
                    )
                }
            }
            401 -> CloudBackupResult.Unauthorized
            429 -> CloudBackupResult.RateLimited
            in 500..599 -> CloudBackupResult.Error(
                message = "Backend server error ($statusCode)",
                code = "SERVER_ERROR",
                statusCode = statusCode,
            )
            else -> CloudBackupResult.Error(
                message = "HTTP request failed with status $statusCode",
                code = "HTTP_$statusCode",
                statusCode = statusCode,
            )
        }
    }
}
