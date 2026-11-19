package haddad.three.notas.data.sync.snapshot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.transport.ConflictingJournalException
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SnapshotUploadResult {
    data class Success(
        val driveFileId: String,
        val alreadyExisted: Boolean = false,
    ) : SnapshotUploadResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : SnapshotUploadResult

    data object Unauthorized : SnapshotUploadResult

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : SnapshotUploadResult
}

sealed interface SnapshotDownloadResult {
    data class Success(
        val targetFile: File,
    ) : SnapshotDownloadResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : SnapshotDownloadResult

    data object Unauthorized : SnapshotDownloadResult

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : SnapshotDownloadResult
}

data class RemoteSnapshotHeader(
    val driveFileId: String,
    val snapshotId: String,
    val creatorDeviceId: String,
    val formatVersion: Int,
    val contentSha256: String,
    val coverageVectorJson: String,
    val snapshotHlc: String,
    val sizeBytes: Long,
    val uploadedAtEpochMillis: Long,
)

/**
 * Handles Google Drive cloud transport for immutable encrypted snapshots.
 * Enforces mandatory constraint that a snapshot transitions to ACTIVE only after
 * verified upload and remote integrity confirmation.
 */
@Singleton
class SnapshotTransportService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val transport: GoogleDriveSyncTransport,
        private val driveApiClient: GoogleDriveApiClient,
        private val snapshotDao: SyncSnapshotDao,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        companion object {
            const val TYPE_SNAPSHOT = "sync_snapshot"
            const val MAX_ENCRYPTED_SNAPSHOT_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
        }

        /**
         * Uploads an immutable encrypted snapshot container to Google Drive appDataFolder.
         * Transitions local status to ACTIVE only upon verified cloud landing.
         */
        suspend fun uploadSnapshot(
            snapshotEntity: SyncSnapshotEntity,
            manifest: SyncSnapshotManifest,
        ): SnapshotUploadResult =
            withContext(ioDispatcher) {
                val snapshotFile = File(snapshotEntity.storagePath)
                if (!snapshotFile.exists() || snapshotFile.length() <= 0) {
                    return@withContext SnapshotUploadResult.Failure("Snapshot file not found or empty at ${snapshotEntity.storagePath}")
                }
                if (snapshotFile.length() > MAX_ENCRYPTED_SNAPSHOT_SIZE_BYTES) {
                    return@withContext SnapshotUploadResult.Failure("Snapshot file size ${snapshotFile.length()} exceeds maximum limit")
                }

                val localSha256 = transport.computeSha256(snapshotFile)
                if (localSha256 != snapshotEntity.contentSha256) {
                    return@withContext SnapshotUploadResult.Failure("Local snapshot hash mismatch: claimed ${snapshotEntity.contentSha256}, actual $localSha256")
                }

                // 1. Pre-upload check: Does this snapshot already exist in Drive?
                val existingCheck = findSnapshotById(snapshotEntity.snapshotId)
                if (existingCheck is DriveResult.Success && existingCheck.data != null) {
                    val existing = existingCheck.data
                    val remoteHash = existing.appProperties?.get("contentSha256")
                    if (remoteHash == snapshotEntity.contentSha256) {
                        snapshotDao.markUploadedAndActive(snapshotEntity.snapshotId, existing.id)
                        return@withContext SnapshotUploadResult.Success(existing.id, alreadyExisted = true)
                    } else {
                        throw ConflictingJournalException(
                            "Remote snapshot ${existing.id} has same snapshotId ${snapshotEntity.snapshotId} but differing SHA-256",
                        )
                    }
                } else if (existingCheck is DriveResult.Unauthorized) {
                    return@withContext SnapshotUploadResult.Unauthorized
                } else if (existingCheck is DriveResult.RateLimited) {
                    return@withContext SnapshotUploadResult.RateLimited(existingCheck.retryAfterSeconds)
                }

                // 2. Build metadata JSON with strict appProperties
                val fileName = "snapshot_${snapshotEntity.creatorDeviceId}_${snapshotEntity.snapshotId}.bin"
                val coverageVectorJson = json.encodeToString(manifest.coverageVector)
                val now = System.currentTimeMillis()

                val metadataMap =
                    mapOf(
                        "name" to fileName,
                        "parents" to listOf("appDataFolder"),
                        "appProperties" to
                            mapOf(
                                "appId" to GoogleDriveSyncTransport.APP_ID,
                                "type" to TYPE_SNAPSHOT,
                                "snapshotId" to snapshotEntity.snapshotId,
                                "creatorDeviceId" to snapshotEntity.creatorDeviceId,
                                "formatVersion" to snapshotEntity.formatVersion.toString(),
                                "contentSha256" to snapshotEntity.contentSha256,
                                "coverageVectorJson" to coverageVectorJson,
                                "snapshotHlc" to snapshotEntity.snapshotHlc,
                                "uploadedAt" to now.toString(),
                            ),
                    )

                val metadataJson =
                    json.encodeToString(
                        kotlinx.serialization.json.JsonObject
                            .serializer(),
                        kotlinx.serialization.json.Json.parseToJsonElement(
                            org.json.JSONObject(metadataMap).toString(),
                        ) as kotlinx.serialization.json.JsonObject,
                    )

                // 3. Upload to Google Drive
                val uploadResult =
                    transport.executeWithAuthAndRetry { token ->
                        if (snapshotFile.length() <= GoogleDriveSyncTransport.MULTIPART_MAX_SIZE_BYTES) {
                            driveApiClient.uploadMultipart(
                                accessToken = token,
                                metadataJson = metadataJson,
                                fileData = snapshotFile.readBytes(),
                                mimeType = GoogleDriveSyncTransport.MIME_TYPE_OCTET,
                            )
                        } else {
                            driveApiClient.uploadStreamResumable(
                                accessToken = token,
                                metadataJson = metadataJson,
                                file = snapshotFile,
                                mimeType = GoogleDriveSyncTransport.MIME_TYPE_OCTET,
                            )
                        }
                    }

                when (uploadResult) {
                    is DriveResult.Success -> {
                        val driveFileId = uploadResult.data.id
                        // Mandatory Constraint 2: Transition to ACTIVE only after verified upload
                        snapshotDao.markUploadedAndActive(
                            snapshotId = snapshotEntity.snapshotId,
                            driveFileId = driveFileId,
                            newStatus = SnapshotStatus.ACTIVE,
                            timestamp = now,
                        )
                        SnapshotUploadResult.Success(driveFileId)
                    }
                    is DriveResult.Unauthorized -> SnapshotUploadResult.Unauthorized
                    is DriveResult.RateLimited -> SnapshotUploadResult.RateLimited(uploadResult.retryAfterSeconds)
                    is DriveResult.Error -> SnapshotUploadResult.Failure(uploadResult.message, uploadResult.cause)
                    is DriveResult.Offline -> SnapshotUploadResult.Failure("Network offline during snapshot upload")
                    is DriveResult.CursorExpired -> SnapshotUploadResult.Failure("Changes cursor expired during snapshot upload")
                }
            }

        /**
         * Searches for a snapshot file in Google Drive appDataFolder by its stable [snapshotId].
         */
        suspend fun findSnapshotById(snapshotId: String): DriveResult<DriveFileDto?> =
            withContext(ioDispatcher) {
                transport.executeWithAuthAndRetry { token ->
                    var pageToken: String? = null
                    var foundFile: DriveFileDto? = null
                    var isFirstPage = true

                    while (isFirstPage || !pageToken.isNullOrBlank()) {
                        isFirstPage = false
                        val query = "'appDataFolder' in parents and appProperties has { key='type' and value='$TYPE_SNAPSHOT' } and appProperties has { key='snapshotId' and value='$snapshotId' } and trashed = false"
                        val listResult =
                            driveApiClient.listFiles(
                                accessToken = token,
                                spaces = "appDataFolder",
                                pageSize = 20,
                                pageToken = pageToken,
                                query = query,
                            )
                        when (listResult) {
                            is DriveResult.Success -> {
                                val match = listResult.data.files.firstOrNull { it.appProperties?.get("snapshotId") == snapshotId }
                                if (match != null) {
                                    foundFile = match
                                    break
                                }
                                pageToken = listResult.data.nextPageToken
                            }
                            is DriveResult.Unauthorized -> return@executeWithAuthAndRetry DriveResult.Unauthorized
                            is DriveResult.RateLimited -> return@executeWithAuthAndRetry listResult
                            is DriveResult.Offline -> return@executeWithAuthAndRetry DriveResult.Offline
                            is DriveResult.CursorExpired -> return@executeWithAuthAndRetry DriveResult.CursorExpired
                            is DriveResult.Error -> return@executeWithAuthAndRetry listResult
                        }
                    }
                    DriveResult.Success(foundFile)
                }
            }

        /**
         * Discovers all available remote snapshots published in Google Drive appDataFolder.
         */
        suspend fun discoverRemoteSnapshots(): DriveResult<List<RemoteSnapshotHeader>> =
            withContext(ioDispatcher) {
                val allSnapshots = mutableListOf<RemoteSnapshotHeader>()
                var pageToken: String? = null
                var isFirstPage = true

                while (isFirstPage || !pageToken.isNullOrBlank()) {
                    isFirstPage = false
                    val pageResult =
                        transport.executeWithAuthAndRetry { token ->
                            val query = "'appDataFolder' in parents and appProperties has { key='type' and value='$TYPE_SNAPSHOT' } and trashed = false"
                            driveApiClient.listFiles(
                                accessToken = token,
                                spaces = "appDataFolder",
                                pageSize = 50,
                                pageToken = pageToken,
                                query = query,
                            )
                        }

                    when (pageResult) {
                        is DriveResult.Success -> {
                            for (file in pageResult.data.files) {
                                val props = file.appProperties ?: continue
                                val snapId = props["snapshotId"] ?: continue
                                val creatorId = props["creatorDeviceId"] ?: continue
                                val formatVer = props["formatVersion"]?.toIntOrNull() ?: 1
                                val sha256 = props["contentSha256"] ?: continue
                                val covJson = props["coverageVectorJson"] ?: continue
                                val hlc = props["snapshotHlc"] ?: continue
                                val uploadedAt =
                                    props["uploadedAt"]?.toLongOrNull()
                                        ?: file.modifiedTime?.let {
                                            runCatching {
                                                java.time.Instant
                                                    .parse(it)
                                                    .toEpochMilli()
                                            }.getOrNull()
                                        }
                                        ?: 0L

                                allSnapshots.add(
                                    RemoteSnapshotHeader(
                                        driveFileId = file.id,
                                        snapshotId = snapId,
                                        creatorDeviceId = creatorId,
                                        formatVersion = formatVer,
                                        contentSha256 = sha256,
                                        coverageVectorJson = covJson,
                                        snapshotHlc = hlc,
                                        sizeBytes = file.size?.toLongOrNull() ?: 0L,
                                        uploadedAtEpochMillis = uploadedAt,
                                    ),
                                )
                            }
                            pageToken = pageResult.data.nextPageToken
                        }
                        is DriveResult.Unauthorized -> return@withContext DriveResult.Unauthorized
                        is DriveResult.RateLimited -> return@withContext pageResult
                        is DriveResult.Offline -> return@withContext DriveResult.Offline
                        is DriveResult.CursorExpired -> return@withContext pageResult
                        is DriveResult.Error -> return@withContext pageResult
                    }
                }
                DriveResult.Success(allSnapshots.sortedByDescending { it.uploadedAtEpochMillis })
            }

        /**
         * Downloads an encrypted snapshot container from Google Drive to [destinationFile]
         * with SHA-256 verification.
         */
        suspend fun downloadSnapshot(
            driveFileId: String,
            destinationFile: File,
            expectedSha256: String?,
        ): SnapshotDownloadResult =
            withContext(ioDispatcher) {
                val downloadRes =
                    try {
                        transport.downloadJournal(driveFileId, destinationFile, expectedSha256)
                    } catch (e: Exception) {
                        return@withContext SnapshotDownloadResult.Failure(e.message ?: "Snapshot download failed", e)
                    }
                when (downloadRes) {
                    is DriveResult.Success -> SnapshotDownloadResult.Success(destinationFile)
                    is DriveResult.Unauthorized -> SnapshotDownloadResult.Unauthorized
                    is DriveResult.RateLimited -> SnapshotDownloadResult.RateLimited(downloadRes.retryAfterSeconds)
                    is DriveResult.Error -> SnapshotDownloadResult.Failure(downloadRes.message, downloadRes.cause)
                    is DriveResult.Offline -> SnapshotDownloadResult.Failure("Network offline during snapshot download")
                    is DriveResult.CursorExpired -> SnapshotDownloadResult.Failure("Changes cursor expired during snapshot download")
                }
            }
    }
