package haddad.three.notas.crypto

import android.content.Context
import java.util.Base64

/**
 * Storage interface for encrypted key blobs, key IDs, and recovery setup status.
 */
interface CryptoStorage {
    fun getLocalWrappedMek(): ByteArray?

    fun setLocalWrappedMek(
        bytes: ByteArray,
        version: Int,
    )

    fun getActiveKeyId(): Int

    fun getRecoveryWrappedMek(): ByteArray?

    fun setRecoveryWrappedMek(bytes: ByteArray)

    fun isRecoveryPhraseBackedUp(): Boolean

    fun setRecoveryPhraseBackedUp(backedUp: Boolean)

    fun clear()
}

/**
 * Android SharedPreferences-backed storage for encrypted key blobs and recovery status.
 */
class SharedPrefsCryptoStorage(
    context: Context,
) : CryptoStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getLocalWrappedMek(): ByteArray? {
        val b64 = prefs.getString(KEY_LOCAL_WRAPPED_MEK, null) ?: return null
        return try {
            Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            null
        }
    }

    override fun setLocalWrappedMek(
        bytes: ByteArray,
        version: Int,
    ) {
        val b64 = Base64.getEncoder().encodeToString(bytes)
        prefs
            .edit()
            .putString(KEY_LOCAL_WRAPPED_MEK, b64)
            .putInt(KEY_ACTIVE_KEY_ID, version)
            .apply()
    }

    override fun getActiveKeyId(): Int = prefs.getInt(KEY_ACTIVE_KEY_ID, CryptoConstants.DEFAULT_KEY_ID)

    override fun getRecoveryWrappedMek(): ByteArray? {
        val b64 = prefs.getString(KEY_RECOVERY_WRAPPED_MEK, null) ?: return null
        return try {
            Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            null
        }
    }

    override fun setRecoveryWrappedMek(bytes: ByteArray) {
        val b64 = Base64.getEncoder().encodeToString(bytes)
        prefs
            .edit()
            .putString(KEY_RECOVERY_WRAPPED_MEK, b64)
            .apply()
    }

    override fun isRecoveryPhraseBackedUp(): Boolean = prefs.getBoolean(KEY_RECOVERY_BACKED_UP, false)

    override fun setRecoveryPhraseBackedUp(backedUp: Boolean) {
        prefs.edit().putBoolean(KEY_RECOVERY_BACKED_UP, backedUp).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "notas_crypto_prefs"
        private const val KEY_LOCAL_WRAPPED_MEK = "local_wrapped_mek"
        private const val KEY_ACTIVE_KEY_ID = "active_key_id"
        private const val KEY_RECOVERY_WRAPPED_MEK = "recovery_wrapped_mek"
        private const val KEY_RECOVERY_BACKED_UP = "recovery_phrase_backed_up"
    }
}

/**
 * In-memory storage for unit testing and ephemeral sessions.
 */
class InMemoryCryptoStorage : CryptoStorage {
    private var localWrappedMek: ByteArray? = null
    private var activeKeyId: Int = CryptoConstants.DEFAULT_KEY_ID
    private var recoveryWrappedMek: ByteArray? = null
    private var recoveryBackedUp: Boolean = false

    @Synchronized
    override fun getLocalWrappedMek(): ByteArray? = localWrappedMek?.clone()

    @Synchronized
    override fun setLocalWrappedMek(
        bytes: ByteArray,
        version: Int,
    ) {
        localWrappedMek = bytes.clone()
        activeKeyId = version
    }

    @Synchronized
    override fun getActiveKeyId(): Int = activeKeyId

    @Synchronized
    override fun getRecoveryWrappedMek(): ByteArray? = recoveryWrappedMek?.clone()

    @Synchronized
    override fun setRecoveryWrappedMek(bytes: ByteArray) {
        recoveryWrappedMek = bytes.clone()
    }

    @Synchronized
    override fun isRecoveryPhraseBackedUp(): Boolean = recoveryBackedUp

    @Synchronized
    override fun setRecoveryPhraseBackedUp(backedUp: Boolean) {
        recoveryBackedUp = backedUp
    }

    @Synchronized
    override fun clear() {
        localWrappedMek = null
        activeKeyId = CryptoConstants.DEFAULT_KEY_ID
        recoveryWrappedMek = null
        recoveryBackedUp = false
    }
}
