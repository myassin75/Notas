package haddad.three.notas.data.drive.envelope

import haddad.three.notas.crypto.Bip39
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.IntegrityVerificationException
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.RecoverySetupManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

class DriveRecoveryEnvelopeTest {
    private class MemoryDriveServer {
        val files = mutableMapOf<String, ByteArray>()
        val fileMetadata = mutableMapOf<String, String>()

        fun clear() {
            files.clear()
            fileMetadata.clear()
        }
    }

    private class MockDriveConnection(
        url: URL,
        private val server: MemoryDriveServer,
    ) : HttpURLConnection(url) {
        val outputBuffer = ByteArrayOutputStream()

        override fun getResponseCode(): Int {
            val urlStr = url.toString()
            return when {
                urlStr.contains("uploadType=multipart") -> 200
                urlStr.contains("spaces=appDataFolder") -> 200
                urlStr.contains("alt=media") -> 200
                requestMethod == "DELETE" -> 204
                else -> 200
            }
        }

        override fun getInputStream(): InputStream {
            val urlStr = url.toString()
            if (urlStr.contains("uploadType=multipart")) {
                val bodyStr = outputBuffer.toString("UTF-8")
                // Extract part 2 (JSON payload of the envelope)
                val parts = bodyStr.split("--====notas_drive_boundary_")
                val mediaPart = parts.getOrNull(2)?.substringAfter("\r\n\r\n")?.substringBefore("\r\n")
                if (mediaPart != null) {
                    val fileId = "envelope_file_1"
                    server.files[fileId] = mediaPart.toByteArray(Charsets.UTF_8)
                }
                val json = """{"id":"envelope_file_1","name":"notas_recovery_envelope.json","size":"128"}"""
                return ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
            }
            if (urlStr.contains("spaces=appDataFolder")) {
                val envelopeFile = server.files.keys.firstOrNull()
                val json =
                    if (envelopeFile != null) {
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
            return ByteArrayInputStream("""{"id":"mock_file","name":"mock"}""".toByteArray(Charsets.UTF_8))
        }

        override fun getOutputStream(): OutputStream = outputBuffer

        override fun disconnect() {
            val urlStr = url.toString()
            if (requestMethod == "DELETE") {
                val fileId = urlStr.substringAfter("/files/")
                server.files.remove(fileId)
            }
        }

        override fun usingProxy(): Boolean = false

        override fun connect() {}
    }

    private lateinit var server: MemoryDriveServer
    private lateinit var driveApiClient: GoogleDriveApiClient

    @Before
    fun setUp() {
        server = MemoryDriveServer()
        driveApiClient =
            GoogleDriveApiClient(
                httpFactory = { url -> MockDriveConnection(URL(url), server) },
            )
    }

    @Test
    fun testSyncAndFetchRecoveryEnvelope() {
        runBlocking {
            val device1KeyStore = SoftwareKeyStoreManager()
            val device1Storage = InMemoryCryptoStorage()
            val device1MekManager = MekManager(device1KeyStore, device1Storage)
            val device1RecoveryManager = RecoverySetupManager(device1MekManager)
            val envelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, device1MekManager, device1Storage)

            // Generate MEK
            device1MekManager.getOrCreateMasterKey()

            // Complete 12-word recovery setup
            val session = device1RecoveryManager.startSetupSession()
            val userResponses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
            device1RecoveryManager.verifyChallengeAndComplete(session, userResponses)

            // Sync recovery envelope to Drive
            val syncResult = envelopeManager.syncRecoveryEnvelopeToDrive("mock_token")
            assertTrue(syncResult is DriveResult.Success)

            // Fetch recovery envelope from Drive
            val fetchResult = envelopeManager.fetchRecoveryEnvelopeFromDrive("mock_token")
            assertTrue(fetchResult is DriveResult.Success)
            val envelope = (fetchResult as DriveResult.Success).data
            assertNotNull(envelope)
            assertEquals(1, envelope?.version)
            assertNotNull(envelope?.recoveryWrappedMekBase64)
        }
    }

    @Test
    fun testNewDeviceRecoveryUsing12WordMnemonic() {
        runBlocking {
            // --- DEVICE 1: Source Device ---
            val device1KeyStore = SoftwareKeyStoreManager()
            val device1Storage = InMemoryCryptoStorage()
            val device1MekManager = MekManager(device1KeyStore, device1Storage)
            val device1RecoveryManager = RecoverySetupManager(device1MekManager)
            val device1EnvelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, device1MekManager, device1Storage)

            val mek1 = device1MekManager.getOrCreateMasterKey()

            // Device 1 user completes 12-word recovery setup
            val session = device1RecoveryManager.startSetupSession()
            val responses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
            device1RecoveryManager.verifyChallengeAndComplete(session, responses, passphrase = "optional-passphrase")

            // Sync recovery envelope to Google Drive
            device1EnvelopeManager.syncRecoveryEnvelopeToDrive("mock_token")

            // Encrypt confidential note with MEK1 on Device 1
            val confidentialNote = "Confidential Journal Entry from Device 1".toByteArray(Charsets.UTF_8)
            val encryptedNoteContainer = EncryptedContainer.encrypt(confidentialNote, mek1, aad = "note_42".toByteArray(Charsets.UTF_8))

            // --- DEVICE 2: Fresh New Device (Simulated blank install, zero local SharedPreferences) ---
            val device2KeyStore = SoftwareKeyStoreManager()
            val device2Storage = InMemoryCryptoStorage() // Empty SharedPreferences
            val device2MekManager = MekManager(device2KeyStore, device2Storage)
            val device2EnvelopeManager = DriveRecoveryEnvelopeManager(driveApiClient, device2MekManager, device2Storage)

            // Device 2 initially has no MEK
            assertEquals(null, device2MekManager.getActiveMasterKey())

            // Fetch envelope from Google Drive
            val fetchedEnvelopeResult = device2EnvelopeManager.fetchRecoveryEnvelopeFromDrive("mock_token")
            assertTrue(fetchedEnvelopeResult is DriveResult.Success)
            val envelope = (fetchedEnvelopeResult as DriveResult.Success).data
            assertNotNull(envelope)

            // User enters their 12-word recovery phrase on Device 2
            val recoveredMek2 =
                device2EnvelopeManager.recoverMekOnNewDevice(
                    mnemonic = session.mnemonicWords,
                    passphrase = "optional-passphrase",
                    envelope = envelope!!,
                )

            // Assert recovered MEK matches original MEK from Device 1 byte-for-byte
            assertArrayEquals(mek1.encoded, recoveredMek2.encoded)

            // Device 2 can decrypt notes created on Device 1
            val decrypted =
                EncryptedContainer.decrypt(
                    containerBytes = encryptedNoteContainer,
                    key = recoveredMek2,
                    expectedAad = "note_42".toByteArray(Charsets.UTF_8),
                )
            assertArrayEquals(confidentialNote, decrypted)

            // Wrong passphrase on new device must be rejected with IntegrityVerificationException
            assertThrows(IntegrityVerificationException::class.java) {
                device2EnvelopeManager.recoverMekOnNewDevice(
                    mnemonic = session.mnemonicWords,
                    passphrase = "wrong-passphrase",
                    envelope = envelope,
                )
            }
        }
    }
}
