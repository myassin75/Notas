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
 */
interface KeyStoreManager {
    fun getOrCreateKek(version: Int = CryptoConstants.DEFAULT_KEY_ID): SecretKey

    fun hasKek(version: Int = CryptoConstants.DEFAULT_KEY_ID): Boolean

    fun wrapKey(
        keyToWrap: SecretKey,
        kekVersion: Int = CryptoConstants.DEFAULT_KEY_ID,
    ): ByteArray

    fun unwrapKey(
        wrappedKeyBytes: ByteArray,
        kekVersion: Int = CryptoConstants.DEFAULT_KEY_ID,
    ): SecretKey

    fun deleteKek(version: Int = CryptoConstants.DEFAULT_KEY_ID)
}

/**
 * Production Android KeyStore manager using hardware-backed AES-256 keys.
 */
class AndroidKeyStoreManager : KeyStoreManager {
    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    override fun getOrCreateKek(version: Int): SecretKey {
        val alias = getAlias(version)
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

    override fun hasKek(version: Int): Boolean = keyStore.containsAlias(getAlias(version))

    override fun wrapKey(
        keyToWrap: SecretKey,
        kekVersion: Int,
    ): ByteArray {
        val kek = getOrCreateKek(kekVersion)
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
    ): SecretKey {
        val kek = getOrCreateKek(kekVersion)
        val rawKeyBytes =
            EncryptedContainer.decrypt(
                containerBytes = wrappedKeyBytes,
                key = kek,
                expectedAad = CryptoConstants.AAD_MEK_LOCAL,
            )
        return SecretKeySpec(rawKeyBytes, "AES")
    }

    override fun deleteKek(version: Int) {
        val alias = getAlias(version)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private fun getAlias(version: Int): String = "${CryptoConstants.KEYSTORE_ALIAS_PREFIX}$version"

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

    override fun getOrCreateKek(version: Int): SecretKey {
        val alias = getAlias(version)
        return keyStoreMap.computeIfAbsent(alias) {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, secureRandom)
            keyGen.generateKey()
        }
    }

    override fun hasKek(version: Int): Boolean = keyStoreMap.containsKey(getAlias(version))

    override fun wrapKey(
        keyToWrap: SecretKey,
        kekVersion: Int,
    ): ByteArray {
        val kek = getOrCreateKek(kekVersion)
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
    ): SecretKey {
        val kek = getOrCreateKek(kekVersion)
        val rawKeyBytes =
            EncryptedContainer.decrypt(
                containerBytes = wrappedKeyBytes,
                key = kek,
                expectedAad = CryptoConstants.AAD_MEK_LOCAL,
            )
        return SecretKeySpec(rawKeyBytes, "AES")
    }

    override fun deleteKek(version: Int) {
        keyStoreMap.remove(getAlias(version))
    }

    fun clearAll() {
        keyStoreMap.clear()
    }

    private fun getAlias(version: Int): String = "${CryptoConstants.KEYSTORE_ALIAS_PREFIX}$version"
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
