package haddad.three.notas.data.drive.api

import haddad.three.notas.data.drive.model.DriveChangeListResponse
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveFileListResponse
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.DriveStartPageTokenResponse
import haddad.three.notas.data.drive.model.toErrorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight, dependency-free Google Drive API v3 REST client.
 * Connects directly to Drive v3 endpoints using HttpURLConnection and OAuth Bearer tokens.
 * Restricted strictly to `appDataFolder` operations.
 */
open class GoogleDriveApiClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val httpFactory: (String) -> HttpURLConnection = { url ->
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
    },
) {
    /**
     * Lists files contained in the private application data folder.
     */
    open suspend fun listFiles(
        accessToken: String,
        spaces: String = "appDataFolder",
        pageSize: Int = 50,
        pageToken: String? = null,
        query: String? = null,
    ): DriveResult<DriveFileListResponse> =
        withContext(Dispatchers.IO) {
            val queryParams = mutableListOf<String>()
            queryParams.add("spaces=${urlEncode(spaces)}")
            queryParams.add("pageSize=$pageSize")
            queryParams.add("fields=${urlEncode("nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,properties,appProperties)")}")
            queryParams.add("orderBy=${urlEncode("createdTime desc")}")

            if (!pageToken.isNullOrBlank()) {
                queryParams.add("pageToken=${urlEncode(pageToken)}")
            }
            if (!query.isNullOrBlank()) {
                queryParams.add("q=${urlEncode(query)}")
            }

            val urlString = "$DRIVE_BASE_URL/files?${queryParams.joinToString("&")}"
            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val response = json.decodeFromString(DriveFileListResponse.serializer(), body)
                        DriveResult.Success(response)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val errorBody =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive listFiles failed with HTTP $code: $errorBody", statusCode = code)
                    }
                }
            }
        }

    /**
     * Uploads file data using multipart/related upload for files up to 5 MB.
     */
    open suspend fun uploadMultipart(
        accessToken: String,
        metadataJson: String,
        fileData: ByteArray,
        mimeType: String = "application/octet-stream",
    ): DriveResult<DriveFileDto> =
        withContext(Dispatchers.IO) {
            val boundary = "====notas_drive_boundary_${System.currentTimeMillis()}===="
            val urlString = "$DRIVE_UPLOAD_BASE_URL/files?uploadType=multipart"

            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                conn.doOutput = true

                conn.outputStream.use { os ->
                    // Part 1: Metadata
                    os.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
                    os.write(metadataJson.toByteArray(Charsets.UTF_8))
                    os.write("\r\n".toByteArray(Charsets.UTF_8))

                    // Part 2: Media
                    os.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    os.write("Content-Type: $mimeType\r\n\r\n".toByteArray(Charsets.UTF_8))
                    os.write(fileData)
                    os.write("\r\n".toByteArray(Charsets.UTF_8))

                    // End Boundary
                    os.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        DriveResult.Success(json.decodeFromString(DriveFileDto.serializer(), body))
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val errorBody =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive multipart upload failed ($code): $errorBody", statusCode = code)
                    }
                }
            }
        }

    /**
     * Streams an encrypted backup archive to Google Drive using a resumable upload session.
     * Prevents memory exhaustion on large backups with multiple attachments and handles interrupted connections.
     */
    open suspend fun uploadStreamResumable(
        accessToken: String,
        metadataJson: String,
        file: File,
        mimeType: String = "application/octet-stream",
        onProgress: ((Float) -> Unit)? = null,
    ): DriveResult<DriveFileDto> =
        withContext(Dispatchers.IO) {
            val totalLength = file.length()

            // 1. Initiate Resumable Upload Session
            val initUrl = "$DRIVE_UPLOAD_BASE_URL/files?uploadType=resumable"
            val sessionUrlResult: DriveResult<String> =
                executeWithRetry {
                    val conn = httpFactory(initUrl)
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    conn.setRequestProperty("X-Upload-Content-Type", mimeType)
                    conn.setRequestProperty("X-Upload-Content-Length", totalLength.toString())
                    conn.doOutput = true

                    conn.outputStream.use { it.write(metadataJson.toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    when (code) {
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                            val location =
                                conn.getHeaderField("Location")
                                    ?: return@executeWithRetry DriveResult.Error("Drive resumable session missing Location header")
                            DriveResult.Success(location)
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                        429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                        else -> {
                            val errorBody =
                                conn.errorStream
                                    ?.bufferedReader(Charsets.UTF_8)
                                    ?.use { it.readText() }
                                    .orEmpty()
                            DriveResult.Error("Drive init resumable failed ($code): $errorBody", statusCode = code)
                        }
                    }
                }

            if (sessionUrlResult !is DriveResult.Success) {
                return@withContext sessionUrlResult.toErrorResult()
            }

            val sessionUrl = sessionUrlResult.data

            // 2. Stream File Content with Chunked Upload & Progress
            var uploadedBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            try {
                FileInputStream(file).use { fis ->
                    val uploadConn = httpFactory(sessionUrl)
                    uploadConn.requestMethod = "PUT"
                    uploadConn.setRequestProperty("Content-Length", totalLength.toString())
                    uploadConn.setRequestProperty("Content-Range", "bytes 0-${totalLength - 1}/$totalLength")
                    uploadConn.doOutput = true
                    uploadConn.setFixedLengthStreamingMode(totalLength)

                    uploadConn.outputStream.use { os ->
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            os.write(buffer, 0, read)
                            uploadedBytes += read
                            if (totalLength > 0) {
                                onProgress?.invoke(uploadedBytes.toFloat() / totalLength)
                            }
                        }
                        os.flush()
                    }

                    val code = uploadConn.responseCode
                    when (code) {
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                            val body = uploadConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            onProgress?.invoke(1.0f)
                            DriveResult.Success(json.decodeFromString(DriveFileDto.serializer(), body))
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                        HttpURLConnection.HTTP_FORBIDDEN -> {
                            val err = uploadConn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                            DriveResult.Error("Drive stream upload forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                        }
                        429 -> DriveResult.RateLimited(uploadConn.getHeaderField("Retry-After")?.toLongOrNull())
                        else -> {
                            val err =
                                uploadConn.errorStream
                                    ?.bufferedReader(Charsets.UTF_8)
                                    ?.use { it.readText() }
                                    .orEmpty()
                            DriveResult.Error("Drive stream upload failed ($code): $err", statusCode = code)
                        }
                    }
                }
            } catch (e: Exception) {
                DriveResult.Error("Resumable upload failed due to network error", cause = e)
            }
        }

    /**
     * Downloads file payload directly to a staging target file with progress tracking.
     */
    open suspend fun downloadFile(
        accessToken: String,
        fileId: String,
        targetFile: File,
        onProgress: ((Float) -> Unit)? = null,
    ): DriveResult<Unit> =
        withContext(Dispatchers.IO) {
            val urlString = "$DRIVE_BASE_URL/files/${urlEncode(fileId)}?alt=media"

            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val contentLength = conn.contentLengthLong
                        var totalRead = 0L
                        val buffer = ByteArray(BUFFER_SIZE)

                        FileOutputStream(targetFile).use { fos ->
                            conn.inputStream.use { ins ->
                                var read: Int
                                while (ins.read(buffer).also { read = it } != -1) {
                                    fos.write(buffer, 0, read)
                                    totalRead += read
                                    if (contentLength > 0) {
                                        onProgress?.invoke(totalRead.toFloat() / contentLength)
                                    }
                                }
                                fos.flush()
                            }
                        }
                        onProgress?.invoke(1.0f)
                        DriveResult.Success(Unit)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive download failed ($code): $err", statusCode = code)
                    }
                }
            }
        }

    /**
     * Deletes a file from Google Drive.
     */
    open suspend fun deleteFile(
        accessToken: String,
        fileId: String,
    ): DriveResult<Unit> =
        withContext(Dispatchers.IO) {
            val urlString = "$DRIVE_BASE_URL/files/${urlEncode(fileId)}"

            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "DELETE"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_OK -> DriveResult.Success(Unit)
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive delete failed ($code): $err", statusCode = code)
                    }
                }
            }
        }

    /**
     * Retrieves file metadata including properties and appProperties.
     */
    open suspend fun getFileMetadata(
        accessToken: String,
        fileId: String,
    ): DriveResult<DriveFileDto> =
        withContext(Dispatchers.IO) {
            val fields = urlEncode("id,name,mimeType,size,createdTime,modifiedTime,properties,appProperties")
            val urlString = "$DRIVE_BASE_URL/files/${urlEncode(fileId)}?fields=$fields"

            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        DriveResult.Success(json.decodeFromString(DriveFileDto.serializer(), body))
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive getMetadata failed ($code): $err", statusCode = code)
                    }
                }
            }
        }

    /**
     * Updates content of an existing Drive file using PATCH media upload.
     */
    open suspend fun updateFileContent(
        accessToken: String,
        fileId: String,
        fileData: ByteArray,
        mimeType: String = "application/octet-stream",
    ): DriveResult<DriveFileDto> =
        withContext(Dispatchers.IO) {
            val urlString = "$DRIVE_UPLOAD_BASE_URL/files/${urlEncode(fileId)}?uploadType=media"
            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("Content-Type", mimeType)
                conn.setRequestProperty("Content-Length", fileData.size.toString())
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(fileData)
                    os.flush()
                }

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        DriveResult.Success(json.decodeFromString(DriveFileDto.serializer(), body))
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        DriveResult.Error("File not found: $fileId", code = "NOT_FOUND", statusCode = 404)
                    }
                    HttpURLConnection.HTTP_CONFLICT -> {
                        DriveResult.Error("Conflict updating file: $fileId", code = "CONFLICT", statusCode = 409)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive updateFileContent failed ($code): $err", statusCode = code)
                    }
                }
            }
        }

    /**
     * Downloads file content directly to a byte array in memory.
     * Suitable for small metadata or manifest files.
     */
    open suspend fun downloadFileBytes(
        accessToken: String,
        fileId: String,
    ): DriveResult<ByteArray> =
        withContext(Dispatchers.IO) {
            val urlString = "$DRIVE_BASE_URL/files/${urlEncode(fileId)}?alt=media"
            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        DriveResult.Success(bytes)
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        DriveResult.Error("File not found: $fileId", code = "NOT_FOUND", statusCode = 404)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive download failed ($code): $err", statusCode = code)
                    }
                }
            }
        }

    /**
     * Retrieves the starting page token for tracking changes using the Google Drive Changes API.
     */
    open suspend fun getChangesStartPageToken(
        accessToken: String,
        spaces: String = "appDataFolder",
    ): DriveResult<DriveStartPageTokenResponse> =
        withContext(Dispatchers.IO) {
            val urlString = "$DRIVE_BASE_URL/changes/startPageToken?spaces=${urlEncode(spaces)}"
            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        DriveResult.Success(json.decodeFromString(DriveStartPageTokenResponse.serializer(), body))
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        DriveResult.Error("Drive getChangesStartPageToken failed ($code): $err", statusCode = code)
                    }
                }
            }
        }

    /**
     * Lists change events occurring in the appDataFolder space using the Changes API.
     * Detects cursor expiration (HTTP 410 or startPageTokenExpired) and returns [DriveResult.CursorExpired].
     */
    open suspend fun listChanges(
        accessToken: String,
        pageToken: String,
        spaces: String = "appDataFolder",
        pageSize: Int = 100,
    ): DriveResult<DriveChangeListResponse> =
        withContext(Dispatchers.IO) {
            val fields = urlEncode("nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,size,createdTime,modifiedTime,properties,appProperties))")
            val urlString = "$DRIVE_BASE_URL/changes?pageToken=${urlEncode(pageToken)}&spaces=${urlEncode(spaces)}&pageSize=$pageSize&fields=$fields"
            executeWithRetry {
                val conn = httpFactory(urlString)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        DriveResult.Success(json.decodeFromString(DriveChangeListResponse.serializer(), body))
                    }
                    410 -> DriveResult.CursorExpired
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveResult.Unauthorized
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        DriveResult.Error("Drive access forbidden: check permissions or quota", code = "FORBIDDEN", statusCode = 403)
                    }
                    429 -> DriveResult.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull())
                    else -> {
                        val err =
                            conn.errorStream
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                        if (err.contains("startPageTokenExpired", ignoreCase = true) ||
                            err.contains("changePageTokenExpired", ignoreCase = true) ||
                            err.contains("tokenExpired", ignoreCase = true)
                        ) {
                            DriveResult.CursorExpired
                        } else {
                            DriveResult.Error("Drive listChanges failed ($code): $err", statusCode = code)
                        }
                    }
                }
            }
        }

    private inline fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        block: () -> DriveResult<T>,
    ): DriveResult<T> {
        var lastError: DriveResult<T>? = null
        for (attempt in 1..maxAttempts) {
            try {
                val res = block()
                if (res is DriveResult.Error && res.statusCode in 500..599 && attempt < maxAttempts) {
                    lastError = res
                    Thread.sleep((100L * (1 shl (attempt - 1))).coerceAtMost(1000L))
                    continue
                }
                return res
            } catch (io: IOException) {
                lastError = DriveResult.Error("Network error contacting Google Drive: ${io.message}", cause = io)
                if (attempt < maxAttempts) {
                    Thread.sleep((100L * (1 shl (attempt - 1))).coerceAtMost(1000L))
                }
            } catch (e: Exception) {
                return DriveResult.Error("Unexpected error communicating with Google Drive: ${e.message}", cause = e)
            }
        }
        return lastError ?: DriveResult.Error("Max retry attempts reached")
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val DRIVE_BASE_URL = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 8192
    }
}
