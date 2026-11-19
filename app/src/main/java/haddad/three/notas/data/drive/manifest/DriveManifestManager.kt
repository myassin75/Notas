package haddad.three.notas.data.drive.manifest

import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveManifestDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import haddad.three.notas.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the top-level encrypted manifest (`manifest.enc`) stored in Google Drive `appDataFolder`.
 *
 * Guarantees:
 * - End-to-end client-side encryption using NOTAS_E1 (AES-256-GCM) with authenticated associated data (AAD).
 * - Tracks application schema version (13), latest snapshot pointer, participating device sequence watermarks,
 *   and recovery envelope presence.
 * - Supports monotonic device sequence watermark reconciliation: max(local, remote).
 * - Safe creation and update via PATCH media upload to avoid orphan files.
 */
@Singleton
class DriveManifestManager
    @Inject
    constructor(
        private val transport: GoogleDriveSyncTransport,
        private val driveApiClient: GoogleDriveApiClient,
        private val mekManager: MekManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Fetches and decrypts the current cloud manifest from `appDataFolder`.
         * Returns `Success(null)` if no manifest has been created yet.
         */
        suspend fun fetchManifest(accountId: String? = null): DriveResult<DriveManifestDto?> =
            withContext(ioDispatcher) {
                val mek =
                    mekManager.getActiveMasterKey(accountId)
                        ?: return@withContext DriveResult.Error("Master Encryption Key (MEK) not initialized for account")

                val existingFileResult = findManifestFile()
                if (existingFileResult !is DriveResult.Success) {
                    return@withContext when (existingFileResult) {
                        is DriveResult.Unauthorized -> DriveResult.Unauthorized
                        is DriveResult.RateLimited -> existingFileResult
                        is DriveResult.Offline -> DriveResult.Offline
                        is DriveResult.CursorExpired -> DriveResult.CursorExpired
                        is DriveResult.Error -> existingFileResult
                        is DriveResult.Success -> error("Unreachable")
                    }
                }

                val existingFile = existingFileResult.data ?: return@withContext DriveResult.Success(null)

                val downloadBytesResult = transport.downloadFileBytes(existingFile.id)
                if (downloadBytesResult !is DriveResult.Success) {
                    return@withContext when (downloadBytesResult) {
                        is DriveResult.Unauthorized -> DriveResult.Unauthorized
                        is DriveResult.RateLimited -> downloadBytesResult
                        is DriveResult.Offline -> DriveResult.Offline
                        is DriveResult.CursorExpired -> DriveResult.CursorExpired
                        is DriveResult.Error -> downloadBytesResult
                        is DriveResult.Success -> error("Unreachable")
                    }
                }

                val encryptedBytes = downloadBytesResult.data
                try {
                    val decryptedBytes =
                        EncryptedContainer.decrypt(
                            containerBytes = encryptedBytes,
                            key = mek,
                            expectedAad = MANIFEST_AAD,
                        )
                    val manifestDto = json.decodeFromString(DriveManifestDto.serializer(), decryptedBytes.toString(Charsets.UTF_8))
                    DriveResult.Success(manifestDto)
                } catch (e: Exception) {
                    DriveResult.Error("Failed to decrypt or deserialize remote manifest: ${e.message}", cause = e)
                }
            }

        /**
         * Encrypts and publishes an updated manifest to `appDataFolder`.
         * Updates existing file in-place via PATCH, or creates a new file via multipart upload.
         */
        suspend fun publishManifest(
            manifest: DriveManifestDto,
            accountId: String? = null,
        ): DriveResult<DriveFileDto> =
            withContext(ioDispatcher) {
                val mek =
                    mekManager.getActiveMasterKey(accountId)
                        ?: return@withContext DriveResult.Error("Master Encryption Key (MEK) not initialized for account")

                val plaintext = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                val encryptedBytes =
                    EncryptedContainer.encrypt(
                        plaintext = plaintext,
                        key = mek,
                        aad = MANIFEST_AAD,
                    )

                val existingFileResult = findManifestFile()
                if (existingFileResult !is DriveResult.Success) {
                    return@withContext when (existingFileResult) {
                        is DriveResult.Unauthorized -> DriveResult.Unauthorized
                        is DriveResult.RateLimited -> existingFileResult
                        is DriveResult.Offline -> DriveResult.Offline
                        is DriveResult.CursorExpired -> DriveResult.CursorExpired
                        is DriveResult.Error -> existingFileResult
                        is DriveResult.Success -> error("Unreachable")
                    }
                }

                val existingFile = existingFileResult.data
                if (existingFile != null) {
                    // Update existing file in-place
                    transport.updateFileContent(
                        driveFileId = existingFile.id,
                        content = encryptedBytes,
                        mimeType = GoogleDriveSyncTransport.MIME_TYPE_OCTET,
                    )
                } else {
                    // Create fresh manifest file in appDataFolder
                    val now = System.currentTimeMillis()
                    val metadataMap =
                        mapOf(
                            "name" to DriveManifestDto.FILE_NAME,
                            "parents" to listOf("appDataFolder"),
                            "appProperties" to
                                mapOf(
                                    "appId" to GoogleDriveSyncTransport.APP_ID,
                                    "type" to DriveManifestDto.TYPE_MANIFEST,
                                    "schemaVersion" to manifest.schemaVersion.toString(),
                                    "formatVersion" to DriveManifestDto.FORMAT_VERSION,
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

                    transport.executeWithAuthAndRetry { token ->
                        driveApiClient.uploadMultipart(
                            accessToken = token,
                            metadataJson = metadataJson,
                            fileData = encryptedBytes,
                            mimeType = GoogleDriveSyncTransport.MIME_TYPE_OCTET,
                        )
                    }
                }
            }

        /**
         * Reconciles two participating device sequence maps by taking the maximum sequence per device ID.
         * Ensures monotonic watermark progression across distributed devices.
         */
        fun reconcileParticipatingDevices(
            local: Map<String, Long>,
            remote: Map<String, Long>,
        ): Map<String, Long> {
            val result = HashMap(local)
            for ((deviceId, remoteSeq) in remote) {
                val current = result[deviceId] ?: 0L
                result[deviceId] = maxOf(current, remoteSeq)
            }
            return result
        }

        /**
         * Locates the active `manifest.enc` file in Google Drive `appDataFolder`.
         */
        suspend fun findManifestFile(): DriveResult<DriveFileDto?> =
            withContext(ioDispatcher) {
                transport.executeWithAuthAndRetry { token ->
                    val query =
                        "'appDataFolder' in parents and appProperties has { key='appId' and value='${GoogleDriveSyncTransport.APP_ID}' } and appProperties has { key='type' and value='${DriveManifestDto.TYPE_MANIFEST}' } and trashed = false"
                    val listResult =
                        driveApiClient.listFiles(
                            accessToken = token,
                            spaces = "appDataFolder",
                            pageSize = 10,
                            query = query,
                        )
                    when (listResult) {
                        is DriveResult.Success -> {
                            val match = listResult.data.files.firstOrNull { it.name == DriveManifestDto.FILE_NAME }
                            DriveResult.Success(match)
                        }
                        is DriveResult.Unauthorized -> DriveResult.Unauthorized
                        is DriveResult.RateLimited -> listResult
                        is DriveResult.Offline -> DriveResult.Offline
                        is DriveResult.CursorExpired -> DriveResult.CursorExpired
                        is DriveResult.Error -> listResult
                    }
                }
            }

        companion object {
            val MANIFEST_AAD = "appId=notas&type=manifest&schemaVersion=13".toByteArray(Charsets.UTF_8)
        }
    }

