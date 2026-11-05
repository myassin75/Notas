package haddad.three.notas.data.sync.transport

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.auth.DriveAuthConstants
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Dedicated Google Drive transport abstraction for encrypted immutable journals.
 * Operates strictly within Google Drive's private `appDataFolder` using the narrow
 * `https://www.googleapis.com/auth/drive.appdata` OAuth scope.
 *
 * Provides:
 * - Deterministic, idempotent uploads with pre- and post-upload verification.
 * - Complete pagination discovery via `nextPageToken`.
 * - Streaming download directly to local staging storage with SHA-256 integrity checks.
 * - Robust error handling for 401 (re-auth), 429 (rate limit), and 5xx (transient errors).
 */
@Singleton
class GoogleDriveSyncTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveApiClient: GoogleDriveApiClient,
    private val driveAuthDelegate: GoogleDriveAuthDelegate,
    private val drivePrefs: DriveAuthPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Obtains a valid Google Drive OAuth access token for `drive.appdata`.
     * If the token was invalidated or expired, attempts re-authorization.
     */
    suspend fun obtainAccessToken(forceRefresh: Boolean = false): String? = withContext(ioDispatcher) {
        val authResult = driveAuthDelegate.authorize(context, forceAccountSelection = false)
        when (authResult) {
            is GoogleDriveAuthorizationResult.Success -> {
                drivePrefs.setConnected(authResult.accountEmail, authResult.accountId)
                authResult.accessToken
            }
            else -> null
        }
    }

    /**
     * Executes a Drive API call with automatic 401 re-authorization, 429 rate limit backoff,
     * and bounded retry with exponential backoff and jitter for transient errors.
     */
    suspend fun <T> executeWithAuthAndRetry(
        maxAttempts: Int = 3,
        action: suspend (accessToken: String) -> DriveResult<T>,
    ): DriveResult<T> = withContext(ioDispatcher) {
        var token = obtainAccessToken() ?: return@withContext DriveResult.Unauthorized

        for (attempt in 1..maxAttempts) {
            val result = try {
                action(token)
            } catch (io: IOException) {
                DriveResult.Error("Network error contacting Drive: ${io.message}", cause = io)
            } catch (e: Exception) {
                DriveResult.Error("Unexpected error during Drive transport: ${e.message}", cause = e)
            }

            when (result) {
                is DriveResult.Success -> return@withContext result
                is DriveResult.Unauthorized -> {
                    // Invalidate token and attempt re-authorization once
                    driveAuthDelegate.invalidateToken(context, token)
                    val refreshedToken = obtainAccessToken(forceRefresh = true)
                    if (refreshedToken != null && attempt < maxAttempts) {
                        token = refreshedToken
                        continue
                    }
                    return@withContext DriveResult.Unauthorized
                }
                is DriveResult.RateLimited -> {
                    if (result.retryAfterSeconds != null && result.retryAfterSeconds > 30L) {
                        return@withContext result
                    }
                    if (attempt < maxAttempts) {
                        val delayMs = if (result.retryAfterSeconds != null && result.retryAfterSeconds > 0) {
                            (result.retryAfterSeconds * 1000L).coerceIn(100L, 30_000L)
                        } else {
                            (300L * (1 shl (attempt - 1))) + Random.nextLong(50, 200)
                        }
                        delay(delayMs)
                        continue
                    }
                    return@withContext result
                }
                is DriveResult.Error -> {
                    val code = result.statusCode
                    if ((code in 500..599 || code == 408) && attempt < maxAttempts) {
                        val backoff = (200L * (1 shl (attempt - 1))) + Random.nextLong(20, 100)
                        delay(backoff)
                        continue
                    }
                    return@withContext result
                }
                is DriveResult.Offline -> return@withContext DriveResult.Offline
            }
        }
        DriveResult.Error("Transport operation exhausted maximum retry attempts ($maxAttempts)")
    }

    /**
     * Searches for a journal file in `appDataFolder` by its stable [journalId].
     * Paginates across all available pages to guarantee complete recovery search.
     */
    suspend fun findJournalById(journalId: String): DriveResult<DriveFileDto?> = withContext(ioDispatcher) {
        if (journalId.isBlank() || journalId.length > MAX_IDENTIFIER_LENGTH) {
            return@withContext DriveResult.Error("Invalid journalId length: must be 1..$MAX_IDENTIFIER_LENGTH")
        }

        executeWithAuthAndRetry { token ->
            var pageToken: String? = null
            var foundFile: DriveFileDto? = null
            var isFirstPage = true

            while (isFirstPage || !pageToken.isNullOrBlank()) {
                isFirstPage = false
                val query = "'appDataFolder' in parents and appProperties has { key='journalId' and value='$journalId' } and trashed = false"
                val listResult = driveApiClient.listFiles(
                    accessToken = token,
                    spaces = "appDataFolder",
                    pageSize = 20,
                    pageToken = pageToken,
                    query = query,
                )
                when (listResult) {
                    is DriveResult.Success -> {
                        val match = listResult.data.files.firstOrNull { it.appProperties?.get("journalId") == journalId }
                        if (match != null) {
                            foundFile = match
                            break
                        }
                        pageToken = listResult.data.nextPageToken
                    }
                    is DriveResult.Unauthorized -> return@executeWithAuthAndRetry DriveResult.Unauthorized
                    is DriveResult.RateLimited -> return@executeWithAuthAndRetry listResult
                    is DriveResult.Offline -> return@executeWithAuthAndRetry DriveResult.Offline
                    is DriveResult.Error -> return@executeWithAuthAndRetry listResult
                }
            }
            DriveResult.Success(foundFile)
        }
    }

    /**
     * Uploads an immutable encrypted journal file to Google Drive `appDataFolder`.
     *
     * Idempotency & Safety:
     * 1. Validates strict schema and size bounds (≤ 4MB, ≤ 500 ops).
     * 2. Checks if a file with [metadata.journalId] already exists in Drive across pages.
     * 3. If existing file has matching SHA-256 and metadata, reuses it without duplicating.
     * 4. If existing file has different SHA-256, fails with [ConflictingJournalException].
     * 5. Uploads with Drive metadata `appProperties` (note contents never exposed).
     * 6. Handles uncertain upload responses (timeouts/5xx) by checking if the file landed before retrying.
     */
    suspend fun uploadJournal(
        journalFile: File,
        metadata: JournalTransportMetadata,
    ): JournalUploadResult = withContext(ioDispatcher) {
        if (!journalFile.exists() || journalFile.length() == 0L) {
            return@withContext JournalUploadResult.Failure("Journal file does not exist or is empty at ${journalFile.absolutePath}")
        }
        if (journalFile.length() > MAX_ENCRYPTED_JOURNAL_SIZE_BYTES) {
            return@withContext JournalUploadResult.Failure(
                "Journal file size ${journalFile.length()} exceeds maximum allowed $MAX_ENCRYPTED_JOURNAL_SIZE_BYTES bytes"
            )
        }
        if (metadata.journalId.isBlank() || metadata.journalId.length > MAX_IDENTIFIER_LENGTH) {
            return@withContext JournalUploadResult.Failure("Invalid journalId length: ${metadata.journalId.length}")
        }
        if (metadata.deviceId.isBlank() || metadata.deviceId.length > MAX_IDENTIFIER_LENGTH) {
            return@withContext JournalUploadResult.Failure("Invalid deviceId length: ${metadata.deviceId.length}")
        }
        if (metadata.sequence <= 0L) {
            return@withContext JournalUploadResult.Failure("Sequence must be positive, got ${metadata.sequence}")
        }
        if (metadata.operationCount !in 0..MAX_OPERATION_COUNT) {
            return@withContext JournalUploadResult.Failure("Operation count ${metadata.operationCount} out of bounds (0..$MAX_OPERATION_COUNT)")
        }
        if (!metadata.contentSha256.matches(SHA256_HEX_REGEX)) {
            return@withContext JournalUploadResult.Failure("Malformed contentSha256 hex string: ${metadata.contentSha256}")
        }

        // Verify local SHA-256 before upload
        val localHash = computeSha256(journalFile)
        if (localHash != metadata.contentSha256) {
            return@withContext JournalUploadResult.Failure(
                "Local journal hash mismatch: claimed ${metadata.contentSha256}, actual $localHash"
            )
        }

        // Step 1: Pre-upload check: Does this journalId already exist in Drive?
        val existingCheck = findJournalById(metadata.journalId)
        if (existingCheck is DriveResult.Success && existingCheck.data != null) {
            val existing = existingCheck.data
            val existingHash = existing.appProperties?.get("contentSha256")
            if (existingHash == metadata.contentSha256) {
                // Confirmed identical journal already exists
                return@withContext JournalUploadResult.Success(existing.id, alreadyExisted = true)
            } else {
                throw ConflictingJournalException(
                    "Remote journal ${existing.id} has same journalId ${metadata.journalId} but differing SHA-256 (remote: $existingHash, local: ${metadata.contentSha256})"
                )
            }
        } else if (existingCheck is DriveResult.Unauthorized) {
            return@withContext JournalUploadResult.Unauthorized
        } else if (existingCheck is DriveResult.RateLimited) {
            return@withContext JournalUploadResult.RateLimited(existingCheck.retryAfterSeconds)
        }

        // Step 2: Build metadata JSON
        val fileName = "journal_${metadata.deviceId}_${metadata.sequence}_${metadata.journalId}.bin"
        val metadataMap = mapOf(
            "name" to fileName,
            "parents" to listOf("appDataFolder"),
            "appProperties" to mapOf(
                "appId" to APP_ID,
                "type" to TYPE_JOURNAL,
                "journalId" to metadata.journalId,
                "deviceId" to metadata.deviceId,
                "sequence" to metadata.sequence.toString(),
                "formatVersion" to metadata.formatVersion.toString(),
                "contentSha256" to metadata.contentSha256,
                "operationCount" to metadata.operationCount.toString(),
                "uploadedAt" to metadata.uploadedAt.toString(),
            )
        )
        val metadataJson = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.Json.parseToJsonElement(
                org.json.JSONObject(metadataMap).toString()
            ) as kotlinx.serialization.json.JsonObject
        )

        // Step 3: Perform upload
        val uploadResult = executeWithAuthAndRetry { token ->
            if (journalFile.length() <= MULTIPART_MAX_SIZE_BYTES) {
                driveApiClient.uploadMultipart(
                    accessToken = token,
                    metadataJson = metadataJson,
                    fileData = journalFile.readBytes(),
                    mimeType = MIME_TYPE_OCTET,
                )
            } else {
                driveApiClient.uploadStreamResumable(
                    accessToken = token,
                    metadataJson = metadataJson,
                    file = journalFile,
                    mimeType = MIME_TYPE_OCTET,
                )
            }
        }

        when (uploadResult) {
            is DriveResult.Success -> {
                val driveFile = uploadResult.data
                JournalUploadResult.Success(driveFile.id)
            }
            is DriveResult.Unauthorized -> JournalUploadResult.Unauthorized
            is DriveResult.RateLimited -> JournalUploadResult.RateLimited(uploadResult.retryAfterSeconds)
            is DriveResult.Error -> {
                // Step 4: Uncertain response recovery: Check if Drive accepted the upload before failing
                val recoveryCheck = findJournalById(metadata.journalId)
                if (recoveryCheck is DriveResult.Success && recoveryCheck.data != null) {
                    val file = recoveryCheck.data
                    val remoteHash = file.appProperties?.get("contentSha256")
                    if (remoteHash == metadata.contentSha256) {
                        return@withContext JournalUploadResult.Success(file.id, alreadyExisted = true)
                    }
                }
                JournalUploadResult.Failure(uploadResult.message, uploadResult.statusCode, uploadResult.cause)
            }
            is DriveResult.Offline -> JournalUploadResult.Failure("Device is offline")
        }
    }

    /**
     * Lists remote journal files in `appDataFolder` with pagination.
     * Optionally excludes journals originating from [excludeDeviceId].
     */
    suspend fun listRemoteJournals(
        excludeDeviceId: String? = null,
        pageSize: Int = 50,
        pageToken: String? = null,
    ): DriveResult<RemoteJournalsPage> = withContext(ioDispatcher) {
        executeWithAuthAndRetry { token ->
            val query = "'appDataFolder' in parents and appProperties has { key='appId' and value='$APP_ID' } and appProperties has { key='type' and value='$TYPE_JOURNAL' } and trashed = false"
            val result = driveApiClient.listFiles(
                accessToken = token,
                spaces = "appDataFolder",
                pageSize = pageSize,
                pageToken = pageToken,
                query = query,
            )

            when (result) {
                is DriveResult.Success -> {
                    val response = result.data
                    val headers = mutableListOf<RemoteJournalHeader>()
                    for (file in response.files) {
                        val props = file.appProperties ?: continue
                        val journalId = props["journalId"] ?: continue
                        val deviceId = props["deviceId"] ?: continue
                        val seq = props["sequence"]?.toLongOrNull() ?: continue
                        val formatVer = props["formatVersion"]?.toIntOrNull() ?: 1
                        val hash = props["contentSha256"] ?: continue
                        val opCount = props["operationCount"]?.toIntOrNull() ?: 0
                        val uploadedAt = props["uploadedAt"]?.toLongOrNull() ?: 0L

                        if (excludeDeviceId != null && deviceId == excludeDeviceId) {
                            continue
                        }

                        headers.add(
                            RemoteJournalHeader(
                                driveFileId = file.id,
                                journalId = journalId,
                                deviceId = deviceId,
                                sequence = seq,
                                formatVersion = formatVer,
                                contentSha256 = hash,
                                operationCount = opCount,
                                sizeBytes = file.size?.toLongOrNull() ?: 0L,
                                createdAtEpochMillis = uploadedAt,
                            )
                        )
                    }
                    DriveResult.Success(
                        RemoteJournalsPage(
                            journals = headers,
                            nextPageToken = response.nextPageToken,
                        )
                    )
                }
                is DriveResult.Unauthorized -> DriveResult.Unauthorized
                is DriveResult.RateLimited -> result
                is DriveResult.Offline -> DriveResult.Offline
                is DriveResult.Error -> result
            }
        }
    }

    /**
     * Downloads an encrypted journal directly to [destinationFile] and verifies its SHA-256 hash
     * and file size limits (≤ 4MB).
     */
    suspend fun downloadJournal(
        driveFileId: String,
        destinationFile: File,
        expectedSha256: String?,
    ): DriveResult<Unit> = withContext(ioDispatcher) {
        if (expectedSha256 != null && !expectedSha256.matches(SHA256_HEX_REGEX)) {
            throw MalformedJournalException("Invalid expected SHA-256 format: $expectedSha256")
        }

        val downloadResult = executeWithAuthAndRetry { token ->
            driveApiClient.downloadFile(token, driveFileId, destinationFile)
        }

        if (downloadResult is DriveResult.Success) {
            if (destinationFile.length() > MAX_ENCRYPTED_JOURNAL_SIZE_BYTES) {
                if (destinationFile.exists()) destinationFile.delete()
                throw MalformedJournalException(
                    "Downloaded journal exceeds size limit: ${destinationFile.length()} bytes"
                )
            }
            if (expectedSha256 != null) {
                val actualHash = computeSha256(destinationFile)
                if (actualHash != expectedSha256) {
                    if (destinationFile.exists()) destinationFile.delete()
                    throw ContentHashMismatchException(
                        "Downloaded journal hash mismatch: expected $expectedSha256, actual $actualHash"
                    )
                }
            }
        } else {
            if (destinationFile.exists()) destinationFile.delete()
        }

        downloadResult
    }

    /**
     * Retrieves file metadata from Drive.
     */
    suspend fun getJournalMetadata(driveFileId: String): DriveResult<DriveFileDto> = withContext(ioDispatcher) {
        executeWithAuthAndRetry { token ->
            driveApiClient.getFileMetadata(token, driveFileId)
        }
    }

    /**
     * Computes the SHA-256 hash string of a local file in lowercase hex.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val APP_ID = "notas"
        const val TYPE_JOURNAL = "sync_journal"
        const val MIME_TYPE_OCTET = "application/octet-stream"
        const val MULTIPART_MAX_SIZE_BYTES = 5 * 1024 * 1024L
        const val MAX_ENCRYPTED_JOURNAL_SIZE_BYTES = 4 * 1024 * 1024L
        const val MAX_OPERATION_COUNT = 500
        const val MAX_IDENTIFIER_LENGTH = 128
        val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
    }
}

