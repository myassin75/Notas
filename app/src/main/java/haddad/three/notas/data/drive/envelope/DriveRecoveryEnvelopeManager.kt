package haddad.three.notas.data.drive.envelope

import haddad.three.notas.crypto.CryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveRecoveryEnvelopeDto
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.toErrorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistence and retrieval of the zero-knowledge Encrypted Recovery Key Envelope in Google Drive.
 *
 * Guarantees:
 * - Only the encrypted wrapped MEK container and non-secret version metadata are uploaded.
 * - Raw Master Encryption Keys and BIP-39 recovery phrases are NEVER sent to Google Drive.
 * - Allows a freshly installed app on a new device (with zero local SharedPreferences) to restore
 *   the original MEK after the user enters their 12-word recovery phrase.
 */
@Singleton
class DriveRecoveryEnvelopeManager @Inject constructor(
    private val driveApiClient: GoogleDriveApiClient,
    private val mekManager: MekManager,
    private val cryptoStorage: CryptoStorage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Uploads or updates the encrypted recovery envelope in Google Drive appDataFolder.
     */
    suspend fun syncRecoveryEnvelopeToDrive(accessToken: String): DriveResult<DriveFileDto> = withContext(Dispatchers.IO) {
        val recoveryWrappedMek = cryptoStorage.getRecoveryWrappedMek()
            ?: return@withContext DriveResult.Error(
                "Cannot sync recovery envelope: 12-word recovery phrase setup has not been completed locally."
            )

        val envelopeDto = DriveRecoveryEnvelopeDto(
            version = 1,
            keyId = cryptoStorage.getActiveKeyId(),
            createdAtEpochMillis = System.currentTimeMillis(),
            recoveryWrappedMekBase64 = Base64.getEncoder().encodeToString(recoveryWrappedMek),
        )

        val envelopeJson = json.encodeToString(envelopeDto)

        // 1. Check if envelope already exists in Drive appDataFolder
        val existingFiles = driveApiClient.listFiles(
            accessToken = accessToken,
            query = "name = '${DriveRecoveryEnvelopeDto.FILE_NAME}' and trashed = false",
        )

        if (existingFiles is DriveResult.Success && existingFiles.data.files.isNotEmpty()) {
            // Delete existing envelope file to avoid duplicates
            for (file in existingFiles.data.files) {
                driveApiClient.deleteFile(accessToken, file.id)
            }
        }

        // 2. Upload fresh envelope file
        val metadataJson = """
            {
              "name": "${DriveRecoveryEnvelopeDto.FILE_NAME}",
              "parents": ["appDataFolder"],
              "mimeType": "application/json",
              "appProperties": {
                "format": "${DriveRecoveryEnvelopeDto.FORMAT_VERSION_1}",
                "keyId": "${envelopeDto.keyId}"
              }
            }
        """.trimIndent()

        driveApiClient.uploadMultipart(
            accessToken = accessToken,
            metadataJson = metadataJson,
            fileData = envelopeJson.toByteArray(Charsets.UTF_8),
            mimeType = "application/json",
        )
    }

    /**
     * Downloads and parses the recovery envelope from Google Drive appDataFolder.
     */
    suspend fun fetchRecoveryEnvelopeFromDrive(accessToken: String): DriveResult<DriveRecoveryEnvelopeDto?> = withContext(Dispatchers.IO) {
        val listResult = driveApiClient.listFiles(
            accessToken = accessToken,
            query = "name = '${DriveRecoveryEnvelopeDto.FILE_NAME}' and trashed = false",
        )

        if (listResult !is DriveResult.Success) {
            return@withContext listResult.toErrorResult()
        }

        val file = listResult.data.files.firstOrNull() ?: return@withContext DriveResult.Success(null)

        val tempFile = File.createTempFile("drive_envelope_", ".json")
        try {
            val downloadResult = driveApiClient.downloadFile(accessToken, file.id, tempFile)
            if (downloadResult !is DriveResult.Success) {
                return@withContext downloadResult.toErrorResult()
            }

            val body = tempFile.readText(Charsets.UTF_8)
            val envelope = json.decodeFromString(DriveRecoveryEnvelopeDto.serializer(), body)
            DriveResult.Success(envelope)
        } catch (e: Exception) {
            DriveResult.Error("Failed to parse recovery envelope from Drive: ${e.message}", cause = e)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Recovers the original MEK on a new device using the user's 12-word BIP-39 mnemonic
     * and the recovery envelope fetched from Google Drive.
     */
    fun recoverMekOnNewDevice(
        mnemonic: List<String>,
        passphrase: String = "",
        envelope: DriveRecoveryEnvelopeDto,
    ): SecretKey {
        val rawWrappedBytes = Base64.getDecoder().decode(envelope.recoveryWrappedMekBase64)
        return mekManager.recoverMasterKeyFromMnemonic(
            mnemonic = mnemonic,
            passphrase = passphrase,
            wrappedRecoveryBlob = rawWrappedBytes,
        )
    }
}

