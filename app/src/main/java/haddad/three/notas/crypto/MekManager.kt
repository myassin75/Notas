package haddad.three.notas.crypto

import android.content.Context
import java.security.SecureRandom
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
 * Manages the full lifecycle of the Master Encryption Key (MEK):
 * - Generation of 256-bit cryptographically secure keys.
 * - Hardware protection via Android Keystore KEK wrapping.
 * - Zero-knowledge cloud recovery wrapping via BIP-39 derived recovery keys.
 * - New device migration by unwrapping and re-wrapping under local hardware Keystore.
 * - Seamless key rotation with versioning.
 */
class MekManager(
    val keyStoreManager: KeyStoreManager = DefaultKeyStoreManager(),
    val storage: CryptoStorage = InMemoryCryptoStorage(),
    private val context: Context? = null,
) {
    private val lock = Any()

    @Volatile
    private var cachedMek: SecretKey? = null
    private val secureRandom = SecureRandom()

    /**
     * Retrieves the currently active Master Encryption Key (MEK) if already initialized,
     * or returns null if no MEK has been created/recovered yet.
     */
    fun getActiveMasterKey(): SecretKey? {
        cachedMek?.let { return it }
        synchronized(lock) {
            cachedMek?.let { return it }
            val wrapped = storage.getLocalWrappedMek() ?: return null
            val keyId = storage.getActiveKeyId()
            val mek = keyStoreManager.unwrapKey(wrapped, keyId)
            cachedMek = mek
            return mek
        }
    }

    /**
     * Gets the active Master Encryption Key (MEK), generating and storing a new 256-bit random
     * MEK locally wrapped by the Keystore KEK if one does not exist yet.
     */
    fun getOrCreateMasterKey(): SecretKey {
        getActiveMasterKey()?.let { return it }
        synchronized(lock) {
            getActiveMasterKey()?.let { return it }

            val rawMek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
            val mek = SecretKeySpec(rawMek, "AES")
            val defaultKeyId = CryptoConstants.DEFAULT_KEY_ID

            val wrapped = keyStoreManager.wrapKey(mek, defaultKeyId)
            storage.setLocalWrappedMek(wrapped, defaultKeyId)
            cachedMek = mek
            return mek
        }
    }

    /**
     * Wraps the active MEK for cloud zero-knowledge recovery using a key derived from a BIP-39 mnemonic.
     */
    fun wrapMekForRecovery(
        mnemonic: List<String>,
        passphrase: String = "",
    ): ByteArray {
        val recoveryKey = Bip39.deriveRecoveryKey(mnemonic, passphrase, context)
        val mek = getOrCreateMasterKey()
        val keyId = storage.getActiveKeyId()

        val recoveryWrappedBlob =
            EncryptedContainer.encrypt(
                plaintext = mek.encoded,
                key = recoveryKey,
                keyId = keyId,
                aad = CryptoConstants.AAD_MEK_RECOVERY,
            )

        storage.setRecoveryWrappedMek(recoveryWrappedBlob)
        storage.setRecoveryPhraseBackedUp(true)
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
    ): SecretKey {
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
            val localWrapped = keyStoreManager.wrapKey(recoveredMek, keyId)
            storage.setLocalWrappedMek(localWrapped, keyId)
            storage.setRecoveryWrappedMek(wrappedRecoveryBlob)
            storage.setRecoveryPhraseBackedUp(true)
            cachedMek = recoveredMek
        }

        return recoveredMek
    }

    /**
     * Rotates the MEK to a new version (v + 1):
     * 1. Generates a new 256-bit cryptographically secure random MEK.
     * 2. Wraps the new MEK with a newly provisioned Keystore KEK (v + 1).
     * 3. Wraps the new MEK with the recovery key derived from the user's BIP-39 phrase.
     * 4. Persists the new active key version and wrapped blobs.
     */
    fun rotateMasterKey(
        mnemonic: List<String>,
        passphrase: String = "",
    ): KeyRotationResult {
        Bip39.validateMnemonic(mnemonic, context).getOrThrow()
        val recoveryKey = Bip39.deriveRecoveryKey(mnemonic, passphrase, context)

        synchronized(lock) {
            val nextKeyId = storage.getActiveKeyId() + 1
            val rawNewMek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
            val newMek = SecretKeySpec(rawNewMek, "AES")

            val localWrapped = keyStoreManager.wrapKey(newMek, nextKeyId)
            val recoveryWrapped =
                EncryptedContainer.encrypt(
                    plaintext = newMek.encoded,
                    key = recoveryKey,
                    keyId = nextKeyId,
                    aad = CryptoConstants.AAD_MEK_RECOVERY,
                )

            storage.setLocalWrappedMek(localWrapped, nextKeyId)
            storage.setRecoveryWrappedMek(recoveryWrapped)
            storage.setRecoveryPhraseBackedUp(true)
            cachedMek = newMek

            return KeyRotationResult(
                newKeyId = nextKeyId,
                newMek = newMek,
                recoveryWrappedBlob = recoveryWrapped,
            )
        }
    }

    /**
     * Checks if the Master Encryption Key exists and has been backed up via recovery phrase.
     */
    fun isRecoveryConfigured(): Boolean = storage.isRecoveryPhraseBackedUp() && storage.getRecoveryWrappedMek() != null

    /**
     * Clears all local keys and cached secrets from memory and storage.
     */
    fun clearKeys() {
        synchronized(lock) {
            val currentKeyId = storage.getActiveKeyId()
            keyStoreManager.deleteKek(currentKeyId)
            storage.clear()
            cachedMek = null
        }
    }
}
