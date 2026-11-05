package haddad.three.notas.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class CryptoManagerTest {
    private lateinit var keyStoreManager: SoftwareKeyStoreManager
    private lateinit var storage: InMemoryCryptoStorage
    private lateinit var mekManager: MekManager
    private lateinit var recoverySetupManager: RecoverySetupManager

    @Before
    fun setUp() {
        keyStoreManager = SoftwareKeyStoreManager()
        storage = InMemoryCryptoStorage()
        mekManager =
            MekManager(
                keyStoreManager = keyStoreManager,
                storage = storage,
                context = null,
            )
        recoverySetupManager =
            RecoverySetupManager(
                mekManager = mekManager,
                context = null,
            )
    }

    // -------------------------------------------------------------------------
    // 1. BIP-39 Standard & Wordlist Tests
    // -------------------------------------------------------------------------

    @Test
    fun testBip39WordlistLoading() {
        val wordlist = Bip39.getWordlist(null)
        assertEquals(CryptoConstants.BIP39_WORDLIST_SIZE, wordlist.size)
        assertEquals("abandon", wordlist.first())
        assertEquals("zoo", wordlist.last())
    }

    @Test
    fun testBip39StandardTestVectors() {
        // Vector 1 (Trezor / BIP-0039 Official Test Vector 1)
        // 16 zero bytes
        val entropy1 = ByteArray(16) { 0x00.toByte() }
        val mnemonic1 = Bip39.generateMnemonic(customEntropy = entropy1)
        val expectedWords1 =
            listOf(
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "about",
            )
        assertEquals(expectedWords1, mnemonic1)

        val seed1 = Bip39.mnemonicToSeed(mnemonic1, passphrase = "TREZOR")
        val hexSeed1 = seed1.joinToString("") { "%02x".format(it) }
        assertEquals(
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            hexSeed1,
        )

        // Vector 2 (0x7f repeating)
        val entropy2 = ByteArray(16) { 0x7f.toByte() }
        val mnemonic2 = Bip39.generateMnemonic(customEntropy = entropy2)
        val expectedWords2 =
            listOf(
                "legal",
                "winner",
                "thank",
                "year",
                "wave",
                "sausage",
                "worth",
                "useful",
                "legal",
                "winner",
                "thank",
                "yellow",
            )
        assertEquals(expectedWords2, mnemonic2)

        val seed2 = Bip39.mnemonicToSeed(mnemonic2, passphrase = "TREZOR")
        val hexSeed2 = seed2.joinToString("") { "%02x".format(it) }
        assertEquals(
            "2e8905819b8723fe2c1d161860e5ee1830318dbf49a83bd451cfb8440c28bd6fa457fe1296106559a3c80937a1c1069be3a3a5bd381ee6260e8d9739fce1f607",
            hexSeed2,
        )

        // Vector 3 (0x80 repeating - 128-bit entropy)
        val entropy3 = ByteArray(16) { 0x80.toByte() }
        val mnemonic3 = Bip39.generateMnemonic(customEntropy = entropy3)
        val expectedWords3 =
            listOf(
                "letter",
                "advice",
                "cage",
                "absurd",
                "amount",
                "doctor",
                "acoustic",
                "avoid",
                "letter",
                "advice",
                "cage",
                "above",
            )
        assertEquals(expectedWords3, mnemonic3)

        val seed3 = Bip39.mnemonicToSeed(mnemonic3, passphrase = "TREZOR")
        val hexSeed3 = seed3.joinToString("") { "%02x".format(it) }
        assertEquals(
            "d71de856f81a8acc65e6fc851a38d4d7ec216fd0796d0a6827a3ad6ed5511a30fa280f12eb2e47ed2ac03b5c462a0358d18d69fe4f985ec81778c1b370b652a8",
            hexSeed3,
        )
    }

    @Test
    fun testBip39InvalidChecksumAndWordRejection() {
        // Valid 12 words
        val validMnemonic =
            listOf(
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "about",
            )
        assertTrue(Bip39.validateMnemonic(validMnemonic).isSuccess)

        // Corrupt the checksum word: replace "about" with "abandon"
        val corruptedChecksum = validMnemonic.toMutableList().also { it[11] = "abandon" }
        val resultCorrupted = Bip39.validateMnemonic(corruptedChecksum)
        assertTrue(resultCorrupted.isFailure)
        assertTrue(resultCorrupted.exceptionOrNull()?.message?.contains("checksum") == true)

        // Invalid word not in BIP-39 dictionary
        val invalidWordMnemonic = validMnemonic.toMutableList().also { it[0] = "notasapp" }
        val resultInvalidWord = Bip39.validateMnemonic(invalidWordMnemonic)
        assertTrue(resultInvalidWord.isFailure)
        assertTrue(resultInvalidWord.exceptionOrNull()?.message?.contains("not present in BIP-39") == true)

        // Wrong word count
        val shortMnemonic = validMnemonic.take(11)
        val resultShort = Bip39.validateMnemonic(shortMnemonic)
        assertTrue(resultShort.isFailure)
    }

    // -------------------------------------------------------------------------
    // 2. Encrypted Container & AES-256-GCM Round-Trip & Tampering Tests
    // -------------------------------------------------------------------------

    @Test
    fun testEncryptedContainerRoundTrip() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()

        val plaintext = "Notas confidential note content with UTF-8 characters: 🔐✨🌍".toByteArray(Charsets.UTF_8)
        val aad = "note_id=42&user_id=usr_9981".toByteArray(Charsets.UTF_8)

        val container =
            EncryptedContainer.encrypt(
                plaintext = plaintext,
                key = key,
                keyId = 1,
                aad = aad,
            )

        val decrypted =
            EncryptedContainer.decrypt(
                containerBytes = container,
                key = key,
                expectedAad = aad,
            )

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testEncryptedContainerWrongKeyRejection() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val correctKey = keyGen.generateKey()
        val wrongKey = keyGen.generateKey()

        val plaintext = "Top Secret Information".toByteArray(Charsets.UTF_8)
        val container =
            EncryptedContainer.encrypt(
                plaintext = plaintext,
                key = correctKey,
            )

        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(
                containerBytes = container,
                key = wrongKey,
            )
        }
    }

    @Test
    fun testEncryptedContainerTamperedAadRejection() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()

        val plaintext = "Critical Financial Record".toByteArray(Charsets.UTF_8)
        val originalAad = "account_id=123".toByteArray(Charsets.UTF_8)
        val alteredAad = "account_id=999".toByteArray(Charsets.UTF_8)

        val container =
            EncryptedContainer.encrypt(
                plaintext = plaintext,
                key = key,
                aad = originalAad,
            )

        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(
                containerBytes = container,
                key = key,
                expectedAad = alteredAad,
            )
        }
    }

    @Test
    fun testEncryptedContainerTamperedPayloadRejection() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()

        val plaintext = "Unmodified text before tampering".toByteArray(Charsets.UTF_8)
        val container =
            EncryptedContainer.encrypt(
                plaintext = plaintext,
                key = key,
            )

        // 1. Tamper Magic Header
        val tamperedMagic = container.clone().also { it[0] = 'X'.code.toByte() }
        assertThrows(InvalidContainerException::class.java) {
            EncryptedContainer.decrypt(tamperedMagic, key)
        }

        // 2. Tamper Ciphertext byte (middle of payload)
        val tamperedCiphertext =
            container.clone().also {
                val midIndex = it.size - 10
                it[midIndex] = (it[midIndex].toInt() xor 0xFF).toByte()
            }
        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(tamperedCiphertext, key)
        }

        // 3. Tamper GCM Auth Tag (last byte)
        val tamperedTag =
            container.clone().also {
                val lastIdx = it.size - 1
                it[lastIdx] = (it[lastIdx].toInt() xor 0x01).toByte()
            }
        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(tamperedTag, key)
        }
    }

    @Test
    fun testNonceUniqueness() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val plaintext = "Repeated encryption payload".toByteArray(Charsets.UTF_8)

        val nonces = HashSet<String>()
        val iterations = 500

        for (i in 0 until iterations) {
            val container = EncryptedContainer.encrypt(plaintext, key)
            // Nonce is located at offset: magic(8) + version(2) + keyId(4) = 14, length 12
            val nonceBytes = container.copyOfRange(14, 26)
            val nonceHex = nonceBytes.joinToString("") { "%02x".format(it) }
            val added = nonces.add(nonceHex)
            assertTrue("Detected repeated nonce across iterations! Nonce: $nonceHex", added)
        }

        assertEquals(iterations, nonces.size)
    }

    // -------------------------------------------------------------------------
    // 3. Streaming Encryption & Decryption (Large Attachments)
    // -------------------------------------------------------------------------

    @Test
    fun testLargeAttachmentStreamingEncryption() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()

        // Generate 5 MB of pseudo-random binary data
        val payloadSize = 5 * 1024 * 1024
        val rawData = ByteArray(payloadSize)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(rawData)

        val originalDigest = MessageDigest.getInstance("SHA-256").digest(rawData)
        val aad = "attachment_id=att_img_49104.png".toByteArray(Charsets.UTF_8)

        // Stream Encrypt
        val inputStream = ByteArrayInputStream(rawData)
        val encryptedOut = ByteArrayOutputStream()
        EncryptedContainer.encryptStream(
            input = inputStream,
            output = encryptedOut,
            key = key,
            keyId = 1,
            aad = aad,
        )

        val encryptedBytes = encryptedOut.toByteArray()
        assertTrue("Encrypted output should be larger than original payload", encryptedBytes.size > payloadSize)

        // Stream Decrypt
        val encryptedIn = ByteArrayInputStream(encryptedBytes)
        val decryptedOut = ByteArrayOutputStream()
        val metadata =
            EncryptedContainer.decryptStream(
                input = encryptedIn,
                output = decryptedOut,
                key = key,
                expectedAad = aad,
            )

        assertEquals(1, metadata.keyId)
        assertArrayEquals(aad, metadata.aad)

        val decryptedBytes = decryptedOut.toByteArray()
        assertEquals(payloadSize, decryptedBytes.size)

        val decryptedDigest = MessageDigest.getInstance("SHA-256").digest(decryptedBytes)
        assertArrayEquals("SHA-256 hashes must match after streaming round-trip", originalDigest, decryptedDigest)
    }

    // -------------------------------------------------------------------------
    // 4. MEK Lifecycle, Android Keystore Wrapping & Recovery Tests
    // -------------------------------------------------------------------------

    @Test
    fun testMekGenerationAndKeystoreWrapping() {
        assertFalse(mekManager.isRecoveryConfigured())
        assertEquals(null, mekManager.getActiveMasterKey())

        // Initial generation
        val mek1 = mekManager.getOrCreateMasterKey()
        assertNotNull(mek1)
        assertEquals(CryptoConstants.AES_KEY_SIZE_BYTES, mek1.encoded.size)

        // Wrapped MEK should be saved in storage
        val localWrapped = storage.getLocalWrappedMek()
        assertNotNull(localWrapped)
        assertEquals(CryptoConstants.DEFAULT_KEY_ID, storage.getActiveKeyId())

        // Consecutive retrieval returns the same cached / unwrapped key
        val mek2 = mekManager.getOrCreateMasterKey()
        assertArrayEquals(mek1.encoded, mek2.encoded)
    }

    @Test
    fun testRecoverySetupAndChallengeValidation() {
        val session = recoverySetupManager.startSetupSession()
        assertEquals(CryptoConstants.BIP39_WORD_COUNT, session.mnemonicWords.size)
        assertEquals(3, session.challenges.size)

        // 1. Invalid response fails
        val wrongResponses = session.challenges.associate { it.index to "incorrectword" }
        val failedResult =
            recoverySetupManager.verifyChallengeAndComplete(
                session = session,
                userResponses = wrongResponses,
            )
        assertTrue(failedResult.isFailure)
        assertFalse(recoverySetupManager.isRecoveryReady())

        // 2. Correct responses succeed
        val correctResponses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
        val successResult =
            recoverySetupManager.verifyChallengeAndComplete(
                session = session,
                userResponses = correctResponses,
            )
        assertTrue(successResult.isSuccess)
        assertTrue(recoverySetupManager.isRecoveryReady())

        val recoveryWrappedBlob = successResult.getOrThrow()
        assertNotNull(recoveryWrappedBlob)
        assertArrayEquals(storage.getRecoveryWrappedMek(), recoveryWrappedBlob)
    }

    @Test
    fun testSimulatedNewDeviceMigration() {
        // --- DEVICE 1: Initial Setup ---
        val device1KeyStore = SoftwareKeyStoreManager()
        val device1Storage = InMemoryCryptoStorage()
        val device1MekManager = MekManager(device1KeyStore, device1Storage)
        val device1RecoveryManager = RecoverySetupManager(device1MekManager)

        val mek1 = device1MekManager.getOrCreateMasterKey()

        // Device 1 encrypts a user note
        val secretNote = "Private journal entry encrypted on Device 1".toByteArray(Charsets.UTF_8)
        val encryptedNoteContainer =
            EncryptedContainer.encrypt(
                plaintext = secretNote,
                key = mek1,
                aad = "note_id=1".toByteArray(Charsets.UTF_8),
            )

        // Device 1 user completes 12-word recovery setup
        val session = device1RecoveryManager.startSetupSession()
        val userResponses = session.challenges.associate { it.index to session.mnemonicWords[it.index] }
        val recoveryBlob =
            device1RecoveryManager
                .verifyChallengeAndComplete(
                    session = session,
                    userResponses = userResponses,
                    passphrase = "user-optional-passphrase",
                ).getOrThrow()

        // --- DEVICE 2: Blank New Device ---
        // Clean Keystore and clean local storage (simulates freshly installed app on new phone)
        val device2KeyStore = SoftwareKeyStoreManager()
        val device2Storage = InMemoryCryptoStorage()
        val device2MekManager = MekManager(device2KeyStore, device2Storage)

        // New device has no local keys initially
        assertFalse(device2MekManager.isRecoveryConfigured())
        assertEquals(null, device2MekManager.getActiveMasterKey())

        // User enters the 12-word recovery phrase on Device 2
        val recoveredMek2 =
            device2MekManager.recoverMasterKeyFromMnemonic(
                mnemonic = session.mnemonicWords,
                passphrase = "user-optional-passphrase",
                wrappedRecoveryBlob = recoveryBlob,
            )

        // Master Encryption Keys must be byte-for-byte identical
        assertArrayEquals("Recovered MEK on Device 2 must match original MEK from Device 1", mek1.encoded, recoveredMek2.encoded)

        // Device 2 must successfully decrypt the encrypted note created on Device 1
        val decryptedNote =
            EncryptedContainer.decrypt(
                containerBytes = encryptedNoteContainer,
                key = recoveredMek2,
                expectedAad = "note_id=1".toByteArray(Charsets.UTF_8),
            )
        assertArrayEquals(secretNote, decryptedNote)

        // Device 2 now has local Keystore wrapping active
        assertTrue(device2KeyStore.hasKek(CryptoConstants.DEFAULT_KEY_ID))
        assertNotNull(device2Storage.getLocalWrappedMek())
        assertTrue(device2MekManager.isRecoveryConfigured())
    }

    @Test
    fun testKeyRotation() {
        val mnemonic = Bip39.generateMnemonic()
        val oldMek = mekManager.getOrCreateMasterKey()
        assertEquals(1, storage.getActiveKeyId())

        // Encrypt note with MEK v1
        val noteV1 = "Note encrypted under MEK v1".toByteArray(Charsets.UTF_8)
        val containerV1 = EncryptedContainer.encrypt(noteV1, oldMek, keyId = 1)

        // Rotate Key
        val rotationResult =
            mekManager.rotateMasterKey(
                mnemonic = mnemonic,
                passphrase = "test-passphrase",
            )

        assertEquals(2, rotationResult.newKeyId)
        assertEquals(2, storage.getActiveKeyId())
        assertFalse(oldMek.encoded.contentEquals(rotationResult.newMek.encoded))

        // Decrypt note V1 with old key, re-encrypt with rotated MEK v2
        val decryptedNoteV1 = EncryptedContainer.decrypt(containerV1, oldMek)
        assertArrayEquals(noteV1, decryptedNoteV1)

        val reEncryptedContainerV2 =
            EncryptedContainer.encrypt(
                plaintext = decryptedNoteV1,
                key = rotationResult.newMek,
                keyId = rotationResult.newKeyId,
            )

        // Assert re-encrypted container decrypts cleanly with new MEK v2
        val decryptedV2 = EncryptedContainer.decrypt(reEncryptedContainerV2, rotationResult.newMek)
        assertArrayEquals(noteV1, decryptedV2)

        // Attempting to decrypt V2 with old MEK must fail
        assertThrows(IntegrityVerificationException::class.java) {
            EncryptedContainer.decrypt(reEncryptedContainerV2, oldMek)
        }
    }
}
