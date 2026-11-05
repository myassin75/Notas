package haddad.three.notas.crypto

import android.content.Context
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class KeyRotationResult(
    val newKeyId: Int,
    val newMek: SecretKey,
    val recoveryWrappedBlob: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyRotationResult) return false
        return newKeyId == other.newKeyId &&
            newMek == other.newMek &&
            recoveryWrappedBlob.contentEquals(other.recoveryWrappedBlob)
    }

    override fun hashCode(): Int {
        var result = newKeyId
        result = 31 * result + newMek.hashCode()
        result = 31 * result + recoveryWrappedBlob.contentHashCode()
        return result
    }
}

/**
 * Manages the full lifecycle of the Master Encryption Key (MEK) across user partitions:
 * - Generation of 256-bit cryptographically secure keys.
 * - Hardware protection via Android Keystore KEK wrapping partitioned by user identity.
 * - Zero-knowledge cloud recovery wrapping via BIP-39 derived recovery keys.
 * - Transparent migration of legacy un-partitioned keys.
 * - Full account isolation between Guest mode and multiple authenticated accounts.
 * - Seamless key rotation with versioning.
 */
class MekManager(
    val keyStoreManager: KeyStoreManager = DefaultKeyStoreManager(),
    val storage: CryptoStorage = InMemoryCryptoStorage(),
    private val context: Context? = null,
    private val accountIdProvider: () -> String? = { null },
) {
    private val lock = Any()
    private val cachedMeks = ConcurrentHashMap<String, SecretKey>()
    private val secureRandom = SecureRandom()

    fun resolveAccountId(explicitAccountId: String? = null): String =
        explicitAccountId ?: accountIdProvider() ?: CryptoStorage.GUEST_ACCOUNT_ID

    /**
     * Retrieves the currently active Master Encryption Key (MEK) for the given account
     * (or active account if null) if already initialized, or returns null.
     */
    fun getActiveMasterKey(accountId: String? = null): SecretKey? {
        val target = resolveAccountId(accountId)
        cachedMeks[target]?.let { return it }
        synchronized(lock) {
            cachedMeks[target]?.let { return it }
            val wrapped = storage.getLocalWrappedMek(target) ?: return null
            val keyId = storage.getActiveKeyId(target)
            val mek = keyStoreManager.unwrapKey(wrapped, keyId, target)
            cachedMeks[target] = mek
            return mek
        }
    }

    /**
     * Gets the active Master Encryption Key (MEK) for the given account (or active account if null),
     * generating and storing a new 256-bit random MEK locally wrapped by the Keystore KEK if one
     * does not exist yet.
     */
    fun getOrCreateMasterKey(accountId: String? = null): SecretKey {
        val target = resolveAccountId(accountId)
        getActiveMasterKey(target)?.let { return it }
        synchronized(lock) {
            getActiveMasterKey(target)?.let { return it }

            val rawMek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
            val mek = SecretKeySpec(rawMek, "AES")
            val defaultKeyId = CryptoConstants.DEFAULT_KEY_ID

            val wrapped = keyStoreManager.wrapKey(mek, defaultKeyId, target)
            storage.setLocalWrappedMek(wrapped, defaultKeyId, target)
            cachedMeks[target] = mek
            return mek
        }
    }

    /**
     * Wraps the active MEK for cloud zero-knowledge recovery using a key derived from a BIP-39 mnemonic.
     */
    fun wrapMekForRecovery(
        mnemonic: List<String>,
        passphrase: String = "",
        accountId: String? = null,
    ): ByteArray {
        val target = resolveAccountId(accountId)
        val recoveryKey = Bip39.deriveRecoveryKey(mnemonic, passphrase, context)
        val mek = getOrCreateMasterKey(target)
        val keyId = storage.getActiveKeyId(target)

        val recoveryWrappedBlob =
            EncryptedContainer.encrypt(
                plaintext = mek.encoded,
                key = recoveryKey,
                keyId = keyId,
                aad = CryptoConstants.AAD_MEK_RECOVERY,
            )

        storage.setRecoveryWrappedMek(recoveryWrappedBlob, target)
        storage.setRecoveryPhraseBackedUp(true, target)
        return recoveryWrappedBlob
    }

    /**
     * Recovers an existing MEK from a 12-word BIP-39 mnemonic on a new or wiped device.
     * Unwraps the MEK using the recovery key derived from the mnemonic,
     * provisions a fresh Keystore KEK on this device, and re-wraps the MEK locally.
     */
    fun recoverMasterKeyFromMnemonic(
        mnemonic: List<String>,
        passphrase: String = "",
        wrappedRecoveryBlob: ByteArray,
        accountId: String? = null,
    ): SecretKey {
        val target = resolveAccountId(accountId)
        Bip39.validateMnemonic(mnemonic, context).getOrThrow()
        val recoveryKey = Bip39.deriveRecoveryKey(mnemonic, passphrase, context)

        val rawMekBytes =
            EncryptedContainer.decrypt(
                containerBytes = wrappedRecoveryBlob,
                key = recoveryKey,
                expectedAad = CryptoConstants.AAD_MEK_RECOVERY,
            )

        val recoveredMek = SecretKeySpec(rawMekBytes, "AES")
        val keyId = CryptoConstants.DEFAULT_KEY_ID

        synchronized(lock) {
            val localWrapped = keyStoreManager.wrapKey(recoveredMek, keyId, target)
            storage.setLocalWrappedMek(localWrapped, keyId, target)
            storage.setRecoveryWrappedMek(wrappedRecoveryBlob, target)
            storage.setRecoveryPhraseBackedUp(true, target)
            cachedMeks[target] = recoveredMek
        }

        return recoveredMek
    }

    /**
     * Rotates the MEK to a new version (v + 1):
     * 1. Generates a new 256-bit cryptographically secure random MEK.
     * 2. Wraps the new MEK with a newly provisioned Keystore KEK (v + 1).
     * 3. Wraps the new MEK with the recovery key derived from the user's BIP-39 phrase.
     * 4. Persists the new active key version and wrapped blobs for this partition.
     */
    fun rotateMasterKey(
        mnemonic: List<String>,
        passphrase: String = "",
        accountId: String? = null,
    ): KeyRotationResult {
        val target = resolveAccountId(accountId)
        Bip39.validateMnemonic(mnemonic, context).getOrThrow()
        val recoveryKey = Bip39.deriveRecoveryKey(mnemonic, passphrase, context)

        synchronized(lock) {
            val nextKeyId = storage.getActiveKeyId(target) + 1
            val rawNewMek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
            val newMek = SecretKeySpec(rawNewMek, "AES")

            val localWrapped = keyStoreManager.wrapKey(newMek, nextKeyId, target)
            val recoveryWrapped =
                EncryptedContainer.encrypt(
                    plaintext = newMek.encoded,
                    key = recoveryKey,
                    keyId = nextKeyId,
                    aad = CryptoConstants.AAD_MEK_RECOVERY,
                )

            storage.setLocalWrappedMek(localWrapped, nextKeyId, target)
            storage.setRecoveryWrappedMek(recoveryWrapped, target)
            storage.setRecoveryPhraseBackedUp(true, target)
            cachedMeks[target] = newMek

            return KeyRotationResult(
                newKeyId = nextKeyId,
                newMek = newMek,
                recoveryWrappedBlob = recoveryWrapped,
            )
        }
    }

    /**
     * Checks if the Master Encryption Key exists and has been backed up via recovery phrase
     * for the target account (or active account if null).
     */
    fun isRecoveryConfigured(accountId: String? = null): Boolean {
        val target = resolveAccountId(accountId)
        return storage.isRecoveryPhraseBackedUp(target) && storage.getRecoveryWrappedMek(target) != null
    }

    /**
     * Clears all local keys and cached secrets for a specific account from memory and storage.
     * Never affects other account partitions.
     */
    fun clearKeys(accountId: String? = null) {
        val target = resolveAccountId(accountId)
        synchronized(lock) {
            val currentKeyId = storage.getActiveKeyId(target)
            keyStoreManager.deleteKek(currentKeyId, target)
            storage.clear(target)
            cachedMeks.remove(target)
        }
    }

    /**
     * Clears the in-memory cached MEK instances across all partitions.
     * Must be invoked on sign-out and account switching so keys do not linger in RAM.
     */
    fun clearMemoryCache() {
        synchronized(lock) {
            cachedMeks.clear()
        }
    }

    /**
     * Explicit, transactional migration of Guest keys to an authenticated account partition.
     * Preserves Guest keys while provisioning the authenticated account partition.
     */
    fun migrateGuestKeysToAccount(targetAccountId: String) {
        synchronized(lock) {
            val guestMek = getActiveMasterKey(CryptoStorage.GUEST_ACCOUNT_ID) ?: return
            val keyId = storage.getActiveKeyId(CryptoStorage.GUEST_ACCOUNT_ID)
            val localWrapped = keyStoreManager.wrapKey(guestMek, keyId, targetAccountId)
            storage.setLocalWrappedMek(localWrapped, keyId, targetAccountId)

            val recoveryWrapped = storage.getRecoveryWrappedMek(CryptoStorage.GUEST_ACCOUNT_ID)
            if (recoveryWrapped != null) {
                storage.setRecoveryWrappedMek(recoveryWrapped, targetAccountId)
            }
            val backedUp = storage.isRecoveryPhraseBackedUp(CryptoStorage.GUEST_ACCOUNT_ID)
            storage.setRecoveryPhraseBackedUp(backedUp, targetAccountId)
            cachedMeks[targetAccountId] = guestMek
        }
    }
}
