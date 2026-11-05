package haddad.three.notas.data.drive.envelope

import haddad.three.notas.crypto.Bip39
import haddad.three.notas.crypto.CryptoStorage
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.IntegrityVerificationException
import haddad.three.notas.crypto.KeyStoreManager
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.RecoverySetupManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveFileDto
import haddad.three.notas.data.drive.model.DriveRecoveryEnvelopeDto
import haddad.three.notas.data.drive.model.DriveResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class DriveRecoveryEnvelopeSecurityTest {

    private class MemoryDriveServer {
        val files = mutableMapOf<String, ByteArray>()
        val fileMetadata = mutableMapOf<String, String>()
    }

    private class MockDriveConnection(
        url: URL,
        private val server: MemoryDriveServer,
    ) : HttpURLConnection(url) {
        val outputBuffer = ByteArrayOutputStream()

        override fun getResponseCode(): Int = 200

        override fun getInputStream(): InputStream {
            val urlStr = url.toString()
            if (urlStr.contains("uploadType=multipart")) {
                val bodyStr = outputBuffer.toString("UTF-8")
                val parts = bodyStr.split("--====notas_drive_boundary_")
                val mediaPart = parts.getOrNull(2)?.substringAfter("\r\n\r\n")?.substringBefore("\r\n")
                if (mediaPart != null) {
                    server.files["envelope_file_1"] = mediaPart.toByteArray(Charsets.UTF_8)
                }
                server.fileMetadata["envelope_file_1"] = parts.getOrNull(1)?.substringAfter("\r\n\r\n")?.substringBefore("\r\n").orEmpty()
                val json = """{"id":"envelope_file_1","name":"notas_recovery_envelope.json","size":"128"}"""
                return ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
            }
            if (urlStr.contains("spaces=appDataFolder")) {
                val envelopeFile = server.files.keys.firstOrNull()
                val json = if (envelopeFile != null) {
                    """{"files":[{"id":"$envelopeFile","name":"notas_recovery_envelope.json","size":"${server.files[envelopeFile]?.size ?: 0}"}]}"""
                } else {
                    """{"files":[]}"""
                }
                return ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
            }
            if (urlStr.contains("alt=media")) {
                val fileId = urlStr.substringAfter("/files/").substringBefore("?")
                val bytes = server.files[fileId] ?: ByteArray(0)
                return ByteArrayInputStream(bytes)
            }
            return ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8))
        }

        override fun getOutputStream(): OutputStream = outputBuffer
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun connect() {}
    }

    private lateinit var server: MemoryDriveServer
    private lateinit var driveApiClient: GoogleDriveApiClient

    @Before
    fun setUp() {
        server = MemoryDriveServer()
        driveApiClient = GoogleDriveApiClient(
            httpFactory = { url -> MockDriveConnection(URL(url), server) },
        )
    }

    @Test
    fun testNewDeviceRecoveryWithoutOldSharedPreferences() {
        runBlocking {
            // Device 1: Source
            val dev1KeyStore = SoftwareKeyStoreManager()
            val dev1Storage = InMemoryCryptoStorage()
            val dev1MekManager = MekManager(dev1KeyStore, dev1Storage)
            val dev1Recovery = RecoverySetupManager(dev1MekManager)
            val dev1EnvelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, dev1MekManager, dev1Storage)

            val originalMek = dev1MekManager.getOrCreateMasterKey()

            // Complete recovery setup with 12 words
            val session = dev1Recovery.startSetupSession()
            val responses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
            dev1Recovery.verifyChallengeAndComplete(session, responses)

            // Sync envelope to Google Drive
            val syncRes = dev1EnvelopeManager.syncRecoveryEnvelopeToDrive("token")
            assertTrue(syncRes is DriveResult.Success)

            // Device 2: New fresh device (zero old SharedPreferences)
            val dev2KeyStore = SoftwareKeyStoreManager()
            val dev2Storage = InMemoryCryptoStorage()
            val dev2MekManager = MekManager(dev2KeyStore, dev2Storage)
            val dev2EnvelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, dev2MekManager, dev2Storage)

            assertNull(dev2Storage.getLocalWrappedMek())
            assertNull(dev2Storage.getRecoveryWrappedMek())

            // Device 2 fetches envelope from Google Drive
            val fetchRes = dev2EnvelopeManager.fetchRecoveryEnvelopeFromDrive("token")
            assertTrue(fetchRes is DriveResult.Success)
            val envelope = (fetchRes as DriveResult.Success).data
            assertNotNull(envelope)

            // Device 2 enters original 12 words
            val recoveredMek = dev2EnvelopeManager.recoverMekOnNewDevice(
                mnemonic = session.mnemonicWords,
                passphrase = "",
                envelope = envelope!!,
            )

            // Recovered MEK must match original MEK byte-for-byte
            assertArrayEquals(originalMek.encoded, recoveredMek.encoded)
            assertNotNull(dev2Storage.getLocalWrappedMek())
        }
    }

    @Test
    fun testCorruptedRecoveryEnvelopeDetected() {
        runBlocking {
            val dev1KeyStore = SoftwareKeyStoreManager()
            val dev1Storage = InMemoryCryptoStorage()
            val dev1MekManager = MekManager(dev1KeyStore, dev1Storage)
            val dev1Recovery = RecoverySetupManager(dev1MekManager)
            val dev1EnvelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, dev1MekManager, dev1Storage)

            dev1MekManager.getOrCreateMasterKey()
            val session = dev1Recovery.startSetupSession()
            val responses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
            dev1Recovery.verifyChallengeAndComplete(session, responses)

            dev1EnvelopeManager.syncRecoveryEnvelopeToDrive("token")

            // Tamper the stored envelope blob in cloud storage
            val originalBytes = server.files["envelope_file_1"]!!
            val envelopeDto = Json { ignoreUnknownKeys = true }.decodeFromString<DriveRecoveryEnvelopeDto>(originalBytes.toString(Charsets.UTF_8))

            // Tamper wrapped MEK base64
            val wrappedBytes = Base64.getDecoder().decode(envelopeDto.recoveryWrappedMekBase64)
            wrappedBytes[wrappedBytes.size - 2] = (wrappedBytes[wrappedBytes.size - 2].toInt() xor 0xFF).toByte()
            val tamperedDto = envelopeDto.copy(recoveryWrappedMekBase64 = Base64.getEncoder().encodeToString(wrappedBytes))
            server.files["envelope_file_1"] = Json.encodeToString(tamperedDto).toByteArray(Charsets.UTF_8)

            // New Device attempts recovery
            val dev2KeyStore = SoftwareKeyStoreManager()
            val dev2Storage = InMemoryCryptoStorage()
            val dev2MekManager = MekManager(dev2KeyStore, dev2Storage)
            val dev2EnvelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, dev2MekManager, dev2Storage)

            val fetchRes = dev2EnvelopeManager.fetchRecoveryEnvelopeFromDrive("token")
            val fetchedEnvelope = (fetchRes as DriveResult.Success).data!!

            // Must throw IntegrityVerificationException due to GCM tag mismatch
            assertThrows(IntegrityVerificationException::class.java) {
                dev2EnvelopeManager.recoverMekOnNewDevice(
                    mnemonic = session.mnemonicWords,
                    envelope = fetchedEnvelope,
                )
            }
        }
    }

    @Test
    fun testBehaviorWhenEnvelopeDeletedIndependently() {
        runBlocking {
            // When no envelope exists in appDataFolder
            server.files.clear()

            val devKeyStore = SoftwareKeyStoreManager()
            val devStorage = InMemoryCryptoStorage()
            val devMekManager = MekManager(devKeyStore, devStorage)
            val envelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, devMekManager, devStorage)

            val fetchRes = envelopeManager.fetchRecoveryEnvelopeFromDrive("token")
            assertTrue(fetchRes is DriveResult.Success)
            // Should return null gracefully without throwing an exception
            assertNull((fetchRes as DriveResult.Success).data)
        }
    }

    @Test
    fun testNoPlaintextMekOrMnemonicInCloudMetadata() {
        runBlocking {
            val devKeyStore = SoftwareKeyStoreManager()
            val devStorage = InMemoryCryptoStorage()
            val devMekManager = MekManager(devKeyStore, devStorage)
            val devRecovery = RecoverySetupManager(devMekManager)
            val envelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, devMekManager, devStorage)

            val mek = devMekManager.getOrCreateMasterKey()
            val rawMekHex = mek.encoded.joinToString("") { "%02x".format(it) }

            val session = devRecovery.startSetupSession()
            val responses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
            devRecovery.verifyChallengeAndComplete(session, responses)

            envelopeManager.syncRecoveryEnvelopeToDrive("token")

            val uploadedMetadata = server.fileMetadata["envelope_file_1"].orEmpty()
            val uploadedContent = server.files["envelope_file_1"]?.toString(Charsets.UTF_8).orEmpty()

            // Verify raw MEK hex does not appear anywhere
            assertFalse(uploadedMetadata.contains(rawMekHex))
            assertFalse(uploadedContent.contains(rawMekHex))

            // Verify none of the 12 mnemonic words appear in plaintext in the cloud payload
            for (word in session.mnemonicWords) {
                assertFalse("Cloud metadata must never contain mnemonic word '$word'", uploadedMetadata.contains(word))
                assertFalse("Cloud content must never contain plaintext mnemonic word '$word'", uploadedContent.contains("\"$word\""))
            }
        }
    }
}
