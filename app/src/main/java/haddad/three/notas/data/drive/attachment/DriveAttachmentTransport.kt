package haddad.three.notas.data.drive.attachment

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import haddad.three.notas.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encrypted attachment upload, download, and deduplication within Google Drive `appDataFolder`.
 *
 * Guarantees:
 * - End-to-end client-side encryption using NOTAS_E1 binary format with attachment-scoped AAD.
 * - Streaming cipher operations to prevent OutOfMemory (OOM) errors on large media.
 * - Resumable upload protocol for files larger than 5 MB.
 * - Deduplication: verifies remote presence and SHA-256 hash before initiating redundant uploads.
 * - Full SHA-256 integrity verification upon download.
 * - Non-destructive local lifecycle: cloud upload success never alters or deletes local attachment files.
 */
@Singleton
class DriveAttachmentTransport
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val transport: GoogleDriveSyncTransport,
        private val driveApiClient: GoogleDriveApiClient,
        private val mekManager: MekManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Uploads a local attachment file to Google Drive `appDataFolder` using client-side encryption.
         * Skips upload if an identical attachment with matching SHA-256 already exists in Drive.
         */
        suspend fun uploadAttachment(
            attachmentId: String,
            localPlaintextFile: File,
            mimeType: String = "application/octet-stream",
            accountId: String? = null,
        ): DriveResult<DriveFileDto> =
            withContext(ioDispatcher) {
                if (!localPlaintextFile.exists() || localPlaintextFile.length() <= 0L) {
                    return@withContext DriveResult.Error("Local attachment file does not exist or is empty: ${localPlaintextFile.absolutePath}")
                }
                if (localPlaintextFile.length() > MAX_ATTACHMENT_SIZE_BYTES) {
                    return@withContext DriveResult.Error(
                        "Attachment size ${localPlaintextFile.length()} exceeds maximum limit of $MAX_ATTACHMENT_SIZE_BYTES bytes",
                    )
                }

                val mek =
                    mekManager.getActiveMasterKey(accountId)
                        ?: return@withContext DriveResult.Error("Master Encryption Key (MEK) not initialized")

                val localPlainSha256 = computeSha256(localPlaintextFile)

                // 1. Deduplication check: does this attachment already exist in Drive?
                val existingCheck = findAttachmentById(attachmentId)
                if (existingCheck is DriveResult.Success && existingCheck.data != null) {
                    val existing = existingCheck.data
                    val remotePlainSha256 = existing.appProperties?.get("plainSha256")
                    if (remotePlainSha256 == localPlainSha256) {
                        return@withContext DriveResult.Success(existing)
                    }
                } else if (existingCheck is DriveResult.Unauthorized) {
                    return@withContext DriveResult.Unauthorized
                } else if (existingCheck is DriveResult.RateLimited) {
                    return@withContext existingCheck
                }

                // 2. Stream-encrypt local plaintext file to a temporary staging file
                val tempEncFile = File.createTempFile("drive_att_enc_", ".bin", context.cacheDir)
                try {
                    val aad = buildAttachmentAad(attachmentId)
                    FileInputStream(localPlaintextFile).use { fis ->
                        FileOutputStream(tempEncFile).use { fos ->
                            EncryptedContainer.encryptStream(
                                input = fis,
                                output = fos,
                                key = mek,
                                aad = aad,
                            )
                        }
                    }

                    val encLength = tempEncFile.length()
                    val fileName = "attachment_${attachmentId}.bin"
                    val now = System.currentTimeMillis()

                    val metadataMap =
                        mapOf(
                            "name" to fileName,
                            "parents" to listOf("appDataFolder"),
                            "appProperties" to
                                mapOf(
                                    "appId" to GoogleDriveSyncTransport.APP_ID,
                                    "type" to TYPE_ATTACHMENT,
                                    "attachmentId" to attachmentId,
                                    "plainSha256" to localPlainSha256,
                                    "mimeType" to mimeType,
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

                    // 3. Perform upload (multipart for small files, resumable for > 5 MB)
                    transport.executeWithAuthAndRetry { token ->
                        if (encLength <= GoogleDriveSyncTransport.MULTIPART_MAX_SIZE_BYTES) {
                            driveApiClient.uploadMultipart(
                                accessToken = token,
                                metadataJson = metadataJson,
                                fileData = tempEncFile.readBytes(),
                                mimeType = GoogleDriveSyncTransport.MIME_TYPE_OCTET,
                            )
                        } else {
                            driveApiClient.uploadStreamResumable(
                                accessToken = token,
                                metadataJson = metadataJson,
                                file = tempEncFile,
                                mimeType = GoogleDriveSyncTransport.MIME_TYPE_OCTET,
                            )
                        }
                    }
                } catch (e: Exception) {
                    DriveResult.Error("Failed to encrypt or upload attachment: ${e.message}", cause = e)
                } finally {
                    if (tempEncFile.exists()) {
                        tempEncFile.delete()
                    }
                }
            }

        /**
         * Downloads and decrypts an attachment from Google Drive to [targetPlaintextFile].
         * Validates SHA-256 integrity against [expectedPlainSha256] if provided.
         */
        suspend fun downloadAttachment(
            driveFileId: String,
            attachmentId: String,
            targetPlaintextFile: File,
            expectedPlainSha256: String? = null,
            accountId: String? = null,
        ): DriveResult<File> =
            withContext(ioDispatcher) {
                val mek =
                    mekManager.getActiveMasterKey(accountId)
                        ?: return@withContext DriveResult.Error("Master Encryption Key (MEK) not initialized")

                val tempEncFile = File.createTempFile("drive_att_down_", ".bin", context.cacheDir)
                try {
                    // 1. Download encrypted file to temp storage
                    val downloadRes =
                        transport.executeWithAuthAndRetry { token ->
                            driveApiClient.downloadFile(token, driveFileId, tempEncFile)
                        }

                    if (downloadRes !is DriveResult.Success) {
                        return@withContext when (downloadRes) {
                            is DriveResult.Unauthorized -> DriveResult.Unauthorized
                            is DriveResult.RateLimited -> downloadRes
                            is DriveResult.Offline -> DriveResult.Offline
                            is DriveResult.CursorExpired -> DriveResult.CursorExpired
                            is DriveResult.Error -> downloadRes
                            is DriveResult.Success -> error("Unreachable")
                        }
                    }

                    // 2. Stream-decrypt into destination plaintext file
                    val aad = buildAttachmentAad(attachmentId)
                    targetPlaintextFile.parentFile?.mkdirs()
                    FileInputStream(tempEncFile).use { fis ->
                        FileOutputStream(targetPlaintextFile).use { fos ->
                            EncryptedContainer.decryptStream(
                                input = fis,
                                output = fos,
                                key = mek,
                                expectedAad = aad,
                            )
                        }
                    }

                    // 3. Verify SHA-256 integrity
                    if (!expectedPlainSha256.isNullOrBlank()) {
                        val actualHash = computeSha256(targetPlaintextFile)
                        if (actualHash != expectedPlainSha256) {
                            if (targetPlaintextFile.exists()) {
                                targetPlaintextFile.delete()
                            }
                            return@withContext DriveResult.Error(
                                "Downloaded attachment hash mismatch: expected $expectedPlainSha256, actual $actualHash",
                            )
                        }
                    }

                    DriveResult.Success(targetPlaintextFile)
                } catch (e: Exception) {
                    if (targetPlaintextFile.exists()) {
                        targetPlaintextFile.delete()
                    }
                    DriveResult.Error("Failed to download or decrypt attachment: ${e.message}", cause = e)
                } finally {
                    if (tempEncFile.exists()) {
                        tempEncFile.delete()
                    }
                }
            }

        /**
         * Searches for an attachment file in Google Drive `appDataFolder` by its stable [attachmentId].
         */
        suspend fun findAttachmentById(attachmentId: String): DriveResult<DriveFileDto?> =
            withContext(ioDispatcher) {
                transport.executeWithAuthAndRetry { token ->
                    val query =
                        "'appDataFolder' in parents and appProperties has { key='appId' and value='${GoogleDriveSyncTransport.APP_ID}' } and appProperties has { key='type' and value='$TYPE_ATTACHMENT' } and appProperties has { key='attachmentId' and value='$attachmentId' } and trashed = false"
                    val listResult =
                        driveApiClient.listFiles(
                            accessToken = token,
                            spaces = "appDataFolder",
                            pageSize = 10,
                            query = query,
                        )
                    when (listResult) {
                        is DriveResult.Success -> {
                            val match = listResult.data.files.firstOrNull { it.appProperties?.get("attachmentId") == attachmentId }
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

        /**
         * Deletes an attachment file from Google Drive when explicitly requested.
         */
        suspend fun deleteAttachment(driveFileId: String): DriveResult<Unit> =
            withContext(ioDispatcher) {
                transport.executeWithAuthAndRetry { token ->
                    driveApiClient.deleteFile(token, driveFileId)
                }
            }

        private fun buildAttachmentAad(attachmentId: String): ByteArray =
            "appId=${GoogleDriveSyncTransport.APP_ID}&type=$TYPE_ATTACHMENT&id=$attachmentId".toByteArray(Charsets.UTF_8)

        private fun computeSha256(file: File): String {
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
            const val TYPE_ATTACHMENT = "attachment"
            const val MAX_ATTACHMENT_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB limit
        }
    }

