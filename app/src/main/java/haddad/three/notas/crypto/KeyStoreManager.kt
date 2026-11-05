package haddad.three.notas.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Interface managing hardware-backed Key Encryption Keys (KEKs) in Android KeyStore.
 * Supports account-partitioned KEK aliases with transparent legacy alias fallback.
 */
interface KeyStoreManager {
    fun getOrCreateKek(
        version: Int = CryptoConstants.DEFAULT_KEY_ID,
        accountId: String = CryptoStorage.GUEST_ACCOUNT_ID,
    ): SecretKey

    fun hasKek(
        version: Int = CryptoConstants.DEFAULT_KEY_ID,
        accountId: String = CryptoStorage.GUEST_ACCOUNT_ID,
    ): Boolean

    fun wrapKey(
        keyToWrap: SecretKey,
        kekVersion: Int = CryptoConstants.DEFAULT_KEY_ID,
        accountId: String = CryptoStorage.GUEST_ACCOUNT_ID,
    ): ByteArray

    fun unwrapKey(
        wrappedKeyBytes: ByteArray,
        kekVersion: Int = CryptoConstants.DEFAULT_KEY_ID,
        accountId: String = CryptoStorage.GUEST_ACCOUNT_ID,
    ): SecretKey

    fun deleteKek(
        version: Int = CryptoConstants.DEFAULT_KEY_ID,
        accountId: String = CryptoStorage.GUEST_ACCOUNT_ID,
    )
}

/**
 * Production Android KeyStore manager using hardware-backed AES-256 keys.
 */
class AndroidKeyStoreManager : KeyStoreManager {
    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    override fun getOrCreateKek(
        version: Int,
        accountId: String,
    ): SecretKey {
        val alias = getAlias(version, accountId)
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
        val spec =
            KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun hasKek(
        version: Int,
        accountId: String,
    ): Boolean = keyStore.containsAlias(getAlias(version, accountId))

    override fun wrapKey(
        keyToWrap: SecretKey,
        kekVersion: Int,
        accountId: String,
    ): ByteArray {
        val kek = getOrCreateKek(kekVersion, accountId)
        return EncryptedContainer.encrypt(
            plaintext = keyToWrap.encoded,
            key = kek,
            keyId = kekVersion,
            aad = CryptoConstants.AAD_MEK_LOCAL,
        )
    }

    override fun unwrapKey(
        wrappedKeyBytes: ByteArray,
        kekVersion: Int,
        accountId: String,
    ): SecretKey {
        // Try account-scoped KEK alias first. If missing, fall back to legacy un-partitioned KEK alias.
        val kek = if (hasKek(kekVersion, accountId)) {
            getOrCreateKek(kekVersion, accountId)
        } else if (hasKek(kekVersion, CryptoStorage.GUEST_ACCOUNT_ID)) {
            getOrCreateKek(kekVersion, CryptoStorage.GUEST_ACCOUNT_ID)
        } else {
            getOrCreateKek(kekVersion, accountId)
        }

        val rawKeyBytes =
            EncryptedContainer.decrypt(
                containerBytes = wrappedKeyBytes,
                key = kek,
                expectedAad = CryptoConstants.AAD_MEK_LOCAL,
            )
        return SecretKeySpec(rawKeyBytes, "AES")
    }

    override fun deleteKek(
        version: Int,
        accountId: String,
    ) {
        val alias = getAlias(version, accountId)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private fun getAlias(
        version: Int,
        accountId: String,
    ): String =
        if (accountId == CryptoStorage.GUEST_ACCOUNT_ID) {
            "${CryptoConstants.KEYSTORE_ALIAS_PREFIX}$version"
        } else {
            val safe = accountId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            "${CryptoConstants.KEYSTORE_ALIAS_PREFIX}${safe}_v$version"
        }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * In-memory / software KeyStore manager for JVM unit testing and non-Android environments.
 */
class SoftwareKeyStoreManager : KeyStoreManager {
    private val keyStoreMap = ConcurrentHashMap<String, SecretKey>()
    private val secureRandom = SecureRandom()

    override fun getOrCreateKek(
        version: Int,
        accountId: String,
    ): SecretKey {
        val alias = getAlias(version, accountId)
        return keyStoreMap.computeIfAbsent(alias) {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, secureRandom)
            keyGen.generateKey()
        }
    }

    override fun hasKek(
        version: Int,
        accountId: String,
    ): Boolean = keyStoreMap.containsKey(getAlias(version, accountId))

    override fun wrapKey(
        keyToWrap: SecretKey,
        kekVersion: Int,
        accountId: String,
    ): ByteArray {
        val kek = getOrCreateKek(kekVersion, accountId)
        return EncryptedContainer.encrypt(
            plaintext = keyToWrap.encoded,
            key = kek,
            keyId = kekVersion,
            aad = CryptoConstants.AAD_MEK_LOCAL,
        )
    }

    override fun unwrapKey(
        wrappedKeyBytes: ByteArray,
        kekVersion: Int,
        accountId: String,
    ): SecretKey {
        val kek = if (hasKek(kekVersion, accountId)) {
            getOrCreateKek(kekVersion, accountId)
        } else if (hasKek(kekVersion, CryptoStorage.GUEST_ACCOUNT_ID)) {
            getOrCreateKek(kekVersion, CryptoStorage.GUEST_ACCOUNT_ID)
        } else {
            getOrCreateKek(kekVersion, accountId)
        }

        val rawKeyBytes =
            EncryptedContainer.decrypt(
                containerBytes = wrappedKeyBytes,
                key = kek,
                expectedAad = CryptoConstants.AAD_MEK_LOCAL,
            )
        return SecretKeySpec(rawKeyBytes, "AES")
    }

    override fun deleteKek(
        version: Int,
        accountId: String,
    ) {
        keyStoreMap.remove(getAlias(version, accountId))
    }

    fun clearAll() {
        keyStoreMap.clear()
    }

    private fun getAlias(
        version: Int,
        accountId: String,
    ): String =
        if (accountId == CryptoStorage.GUEST_ACCOUNT_ID) {
            "${CryptoConstants.KEYSTORE_ALIAS_PREFIX}$version"
        } else {
            val safe = accountId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            "${CryptoConstants.KEYSTORE_ALIAS_PREFIX}${safe}_v$version"
        }
}

/**
 * Default KeyStore manager that transparently resolves the hardware AndroidKeyStore provider
 * if available, or falls back to software provider in JVM unit test suites.
 */
class DefaultKeyStoreManager(
    private val delegate: KeyStoreManager = createDefaultDelegate(),
) : KeyStoreManager by delegate {
    companion object {
        fun createDefaultDelegate(): KeyStoreManager =
            try {
                val ks = KeyStore.getInstance(AndroidKeyStoreManager.ANDROID_KEYSTORE)
                ks.load(null)
                AndroidKeyStoreManager()
            } catch (_: Throwable) {
                SoftwareKeyStoreManager()
            }
    }
}
