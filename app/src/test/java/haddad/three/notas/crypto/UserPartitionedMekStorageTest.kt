package haddad.three.notas.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * Rigorous regression tests for TASK F03: User-Partitioned MEK Storage.
 *
 * Verifies:
 * 1. Guest and Multi-Account Isolation (Guest, Account A, Account B).
 * 2. In-Memory Cache Clearing on Sign-Out & Account Switching.
 * 3. Cross-Account Decryption Defense (Account B cannot decrypt Account A's payloads).
 * 4. Backward-Compatible Legacy MEK Migration (pre-F03 keys seamlessly migrated without data loss).
 * 5. Account-Scoped BIP-39 Recovery (mnemonic restores MEK into correct partition).
 * 6. Explicit Guest-to-Account Key Migration (transactional promotion without deleting Guest).
 * 7. Partition-Scoped Key Deletion (clearing Account A never wipes Account B or Guest).
 */
class UserPartitionedMekStorageTest {
    private lateinit var keyStoreManager: SoftwareKeyStoreManager
    private lateinit var storage: InMemoryCryptoStorage
    private var activeAccount: String? = null
    private lateinit var mekManager: MekManager

    @Before
    fun setUp() {
        keyStoreManager = SoftwareKeyStoreManager()
        storage = InMemoryCryptoStorage(accountIdProvider = { activeAccount })
        activeAccount = null // Starts in Guest mode
        mekManager =
            MekManager(
                keyStoreManager = keyStoreManager,
                storage = storage,
                context = null,
                accountIdProvider = { activeAccount },
            )
    }

    // -------------------------------------------------------------------------
    // 1. Guest and Multi-Account Isolation
    // -------------------------------------------------------------------------

    @Test
    fun testGuestAndAuthenticatedAccountsHaveIsolatedMeks() {
        // 1. Guest creates MEK
        activeAccount = null
        val guestMek = mekManager.getOrCreateMasterKey()
        assertNotNull(guestMek)
        assertEquals(guestMek, mekManager.getActiveMasterKey())

        // 2. Switch to Account A
        activeAccount = "account_alice_123"
        val aliceMek = mekManager.getOrCreateMasterKey()
        assertNotNull(aliceMek)
        assertEquals(aliceMek, mekManager.getActiveMasterKey())

        // Alice's MEK must be cryptographically distinct from Guest's MEK
        assertFalse(guestMek.encoded.contentEquals(aliceMek.encoded))

        // 3. Switch to Account B
        activeAccount = "account_bob_456"
        val bobMek = mekManager.getOrCreateMasterKey()
        assertNotNull(bobMek)
        assertEquals(bobMek, mekManager.getActiveMasterKey())

        // Bob's MEK must be distinct from Alice's and Guest's
        assertFalse(bobMek.encoded.contentEquals(aliceMek.encoded))
        assertFalse(bobMek.encoded.contentEquals(guestMek.encoded))

        // 4. Switch back to Guest: Guest MEK must match original Guest MEK
        activeAccount = null
        val retrievedGuestMek = mekManager.getActiveMasterKey()
        assertNotNull(retrievedGuestMek)
        assertArrayEquals(guestMek.encoded, retrievedGuestMek!!.encoded)

        // 5. Switch back to Alice: Alice MEK must match original Alice MEK
        activeAccount = "account_alice_123"
        val retrievedAliceMek = mekManager.getActiveMasterKey()
        assertNotNull(retrievedAliceMek)
        assertArrayEquals(aliceMek.encoded, retrievedAliceMek!!.encoded)
    }

    // -------------------------------------------------------------------------
    // 2. Cross-Account Decryption Protection
    // -------------------------------------------------------------------------

    @Test
    fun testCrossAccountDecryptionFailsSecurely() {
        // Account A encrypts a sensitive note
        activeAccount = "user_alice"
        val aliceMek = mekManager.getOrCreateMasterKey()
        val sensitiveData = "Secret note content for Alice".toByteArray(Charsets.UTF_8)
        val encryptedContainer =
            EncryptedContainer.encrypt(
                plaintext = sensitiveData,
                key = aliceMek,
                keyId = 1,
                aad = "note_1".toByteArray(Charsets.UTF_8),
            )

        // Account B signs in and gets its own MEK
        activeAccount = "user_bob"
        val bobMek = mekManager.getOrCreateMasterKey()

        // Bob attempts to decrypt Alice's container with Bob's MEK -> must fail
        var decryptionFailed = false
        try {
            EncryptedContainer.decrypt(
                containerBytes = encryptedContainer,
                key = bobMek,
                expectedAad = "note_1".toByteArray(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            decryptionFailed = true
        }
        assertTrue("Account B must not be able to decrypt Account A's data", decryptionFailed)

        // Switch back to Alice -> decryption succeeds
        activeAccount = "user_alice"
        val recoveredAliceMek = mekManager.getActiveMasterKey()!!
        val decryptedData =
            EncryptedContainer.decrypt(
                containerBytes = encryptedContainer,
                key = recoveredAliceMek,
                expectedAad = "note_1".toByteArray(Charsets.UTF_8),
            )
        assertEquals("Secret note content for Alice", String(decryptedData, Charsets.UTF_8))
    }

    // -------------------------------------------------------------------------
    // 3. Sign-Out & Account Switch Clears Memory Cache
    // -------------------------------------------------------------------------

    @Test
    fun testSignOutClearsMemoryCacheAndPreservesStorage() {
        // Alice logs in and initializes MEK
        activeAccount = "user_alice"
        val aliceMek = mekManager.getOrCreateMasterKey()

        // Simulate sign out: memory cache cleared, active account becomes Guest
        mekManager.clearMemoryCache()
        activeAccount = null

        // In Guest mode, active master key must be null (or guest key, never Alice's key)
        val guestKey = mekManager.getActiveMasterKey()
        assertNull("Guest partition must not contain Alice's MEK", guestKey)

        // Alice logs back in: her key is safely loaded from persistent storage
        activeAccount = "user_alice"
        val reloadedAliceKey = mekManager.getActiveMasterKey()
        assertNotNull("Alice's key must persist across sign-out", reloadedAliceKey)
        assertArrayEquals(aliceMek.encoded, reloadedAliceKey!!.encoded)
    }

    // -------------------------------------------------------------------------
    // 4. Backward-Compatible Legacy MEK Migration
    // -------------------------------------------------------------------------

    @Test
    fun testLegacyUnpartitionedMekMigratesSeamlessly() {
        // Seed legacy pre-F03 key into storage without account partition
        val legacyRawKey = ByteArray(32) { (it * 7).toByte() }
        val legacyMek = SecretKeySpec(legacyRawKey, "AES")
        val legacyWrapped = keyStoreManager.wrapKey(legacyMek, 1, CryptoStorage.GUEST_ACCOUNT_ID)

        storage.seedLegacyData(
            localWrappedMek = legacyWrapped,
            activeKeyId = 1,
            recoveryWrappedMek = "legacy_recovery".toByteArray(Charsets.UTF_8),
            recoveryBackedUp = true,
        )

        // Existing user opens the app as Guest after update:
        activeAccount = null
        val migratedGuestMek = mekManager.getActiveMasterKey()
        assertNotNull("Legacy MEK must be accessible to Guest", migratedGuestMek)
        assertArrayEquals(legacyRawKey, migratedGuestMek!!.encoded)
        assertTrue(storage.isRecoveryPhraseBackedUp())
        assertEquals(1, storage.getActiveKeyId())

        // User data encrypted with legacy MEK is successfully decrypted
        val ciphertext =
            EncryptedContainer.encrypt(
                plaintext = "Legacy Note".toByteArray(Charsets.UTF_8),
                key = legacyMek,
                keyId = 1,
                aad = "note_legacy".toByteArray(Charsets.UTF_8),
            )
        val decrypted =
            EncryptedContainer.decrypt(
                containerBytes = ciphertext,
                key = migratedGuestMek,
                expectedAad = "note_legacy".toByteArray(Charsets.UTF_8),
            )
        assertEquals("Legacy Note", String(decrypted, Charsets.UTF_8))
    }

    // -------------------------------------------------------------------------
    // 5. Account-Scoped BIP-39 Cloud Recovery
    // -------------------------------------------------------------------------

    @Test
    fun testMnemonicRecoveryScopedToTargetAccount() {
        val mnemonic =
            listOf(
                "abandon", "abandon", "abandon", "abandon",
                "abandon", "abandon", "abandon", "abandon",
                "abandon", "abandon", "abandon", "about",
            )

        // Device 1: Alice sets up recovery
        activeAccount = "user_alice"
        val aliceMek = mekManager.getOrCreateMasterKey()
        val recoveryBlob = mekManager.wrapMekForRecovery(mnemonic)

        // Device 2: Fresh installation with fresh storage & keystore
        val dev2KeyStore = SoftwareKeyStoreManager()
        val dev2Storage = InMemoryCryptoStorage()
        val dev2MekManager =
            MekManager(
                keyStoreManager = dev2KeyStore,
                storage = dev2Storage,
                context = null,
            )

        // Recover specifically into Alice's account on Device 2
        val recoveredAliceMek =
            dev2MekManager.recoverMasterKeyFromMnemonic(
                mnemonic = mnemonic,
                wrappedRecoveryBlob = recoveryBlob,
                accountId = "user_alice",
            )

        assertArrayEquals(aliceMek.encoded, recoveredAliceMek.encoded)
        assertTrue(dev2MekManager.isRecoveryConfigured("user_alice"))
        assertFalse(dev2MekManager.isRecoveryConfigured(CryptoStorage.GUEST_ACCOUNT_ID))
    }

    // -------------------------------------------------------------------------
    // 6. Explicit Guest-to-Account Key Migration
    // -------------------------------------------------------------------------

    @Test
    fun testMigrateGuestKeysToAccountPreservesBoth() {
        // Guest creates notes and MEK
        activeAccount = null
        val guestMek = mekManager.getOrCreateMasterKey()
        val payload = "Pre-registration guest note".toByteArray(Charsets.UTF_8)
        val encryptedGuestNote =
            EncryptedContainer.encrypt(
                plaintext = payload,
                key = guestMek,
                keyId = 1,
                aad = "guest_1".toByteArray(Charsets.UTF_8),
            )

        // User registers as Alice and explicitly migrates guest data
        mekManager.migrateGuestKeysToAccount("user_alice")

        // Switch to Alice
        activeAccount = "user_alice"
        val aliceMek = mekManager.getActiveMasterKey()
        assertNotNull(aliceMek)
        assertArrayEquals(guestMek.encoded, aliceMek!!.encoded)

        // Alice can decrypt the guest note
        val decrypted =
            EncryptedContainer.decrypt(
                containerBytes = encryptedGuestNote,
                key = aliceMek,
                expectedAad = "guest_1".toByteArray(Charsets.UTF_8),
            )
        assertEquals("Pre-registration guest note", String(decrypted, Charsets.UTF_8))

        // Guest partition still preserves its keys
        activeAccount = null
        val preservedGuestMek = mekManager.getActiveMasterKey()
        assertNotNull(preservedGuestMek)
        assertArrayEquals(guestMek.encoded, preservedGuestMek!!.encoded)
    }

    // -------------------------------------------------------------------------
    // 7. Partition-Scoped Key Deletion
    // -------------------------------------------------------------------------

    @Test
    fun testClearKeysOnlyWipesTargetAccount() {
        // Provision Guest, Alice, and Bob
        activeAccount = null
        val guestMek = mekManager.getOrCreateMasterKey()

        activeAccount = "user_alice"
        val aliceMek = mekManager.getOrCreateMasterKey()

        activeAccount = "user_bob"
        val bobMek = mekManager.getOrCreateMasterKey()

        // Clear Alice's keys
        mekManager.clearKeys("user_alice")

        // Alice's keys must be gone
        assertNull(mekManager.getActiveMasterKey("user_alice"))

        // Bob's and Guest's keys must remain completely intact
        activeAccount = "user_bob"
        val retrievedBob = mekManager.getActiveMasterKey()
        assertNotNull(retrievedBob)
        assertArrayEquals(bobMek.encoded, retrievedBob!!.encoded)

        activeAccount = null
        val retrievedGuest = mekManager.getActiveMasterKey()
        assertNotNull(retrievedGuest)
        assertArrayEquals(guestMek.encoded, retrievedGuest!!.encoded)
    }
}

