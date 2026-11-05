package haddad.three.notas.crypto

import android.content.Context
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Storage interface for encrypted key blobs, key IDs, and recovery setup status.
 * Supports account-scoped key storage partitions (Guest and Authenticated accounts).
 */
interface CryptoStorage {
    fun getLocalWrappedMek(accountId: String? = null): ByteArray?

    fun setLocalWrappedMek(
        bytes: ByteArray,
        version: Int,
        accountId: String? = null,
    )

    fun getActiveKeyId(accountId: String? = null): Int

    fun getRecoveryWrappedMek(accountId: String? = null): ByteArray?

    fun setRecoveryWrappedMek(
        bytes: ByteArray,
        accountId: String? = null,
    )

    fun isRecoveryPhraseBackedUp(accountId: String? = null): Boolean

    fun setRecoveryPhraseBackedUp(
        backedUp: Boolean,
        accountId: String? = null,
    )

    /** Clears keys for a specific account partition (or current account if null). */
    fun clear(accountId: String? = null)

    /** Clears all stored keys across all partitions (e.g. during complete app reset/wipe). */
    fun clearAll()

    /** Checks if key material exists for a given account partition. */
    fun hasKeys(accountId: String? = null): Boolean

    /** Copies or migrates key material from one account partition to another. */
    fun copyKeys(sourceAccountId: String, targetAccountId: String)

    companion object {
        const val GUEST_ACCOUNT_ID = "guest"
    }
}

/**
 * Android SharedPreferences-backed storage for encrypted key blobs and recovery status.
 * Fully partitions key material by account identity with safe legacy un-partitioned fallback.
 */
class SharedPrefsCryptoStorage(
    context: Context,
    private val accountIdProvider: () -> String? = { null },
) : CryptoStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun resolveAccountId(accountId: String?): String =
        accountId ?: accountIdProvider() ?: CryptoStorage.GUEST_ACCOUNT_ID

    private fun sanitizeAccount(accountId: String): String =
        accountId.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    private fun getKey(baseKey: String, accountId: String): String {
        val safeAcc = sanitizeAccount(accountId)
        return "${safeAcc}_$baseKey"
    }

    override fun getLocalWrappedMek(accountId: String?): ByteArray? {
        val target = resolveAccountId(accountId)
        val accountKey = getKey(KEY_LOCAL_WRAPPED_MEK, target)
        val b64 = prefs.getString(accountKey, null)
        if (b64 != null) {
            return decodeBase64(b64)
        }

        // Backward compatibility migration:
        // If legacy unpartitioned key exists and has not been migrated to another account,
        // migrate it to this account partition.
        synchronized(this) {
            val existing = prefs.getString(accountKey, null)
            if (existing != null) return decodeBase64(existing)

            val legacyB64 = prefs.getString(KEY_LOCAL_WRAPPED_MEK, null) ?: return null
            val migratedTo = prefs.getString(KEY_LEGACY_MIGRATED_TO, null)
            if (migratedTo == null || migratedTo == target) {
                val legacyVersion = prefs.getInt(KEY_ACTIVE_KEY_ID, CryptoConstants.DEFAULT_KEY_ID)
                val legacyRecovery = prefs.getString(KEY_RECOVERY_WRAPPED_MEK, null)
                val legacyRecoveryBackedUp = prefs.getBoolean(KEY_RECOVERY_BACKED_UP, false)

                val editor = prefs.edit()
                editor.putString(accountKey, legacyB64)
                editor.putInt(getKey(KEY_ACTIVE_KEY_ID, target), legacyVersion)
                if (legacyRecovery != null) {
                    editor.putString(getKey(KEY_RECOVERY_WRAPPED_MEK, target), legacyRecovery)
                }
                editor.putBoolean(getKey(KEY_RECOVERY_BACKED_UP, target), legacyRecoveryBackedUp)
                editor.putString(KEY_LEGACY_MIGRATED_TO, target)
                editor.apply()

                return decodeBase64(legacyB64)
            }
            return null
        }
    }

    override fun setLocalWrappedMek(
        bytes: ByteArray,
        version: Int,
        accountId: String?,
    ) {
        val target = resolveAccountId(accountId)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        prefs
            .edit()
            .putString(getKey(KEY_LOCAL_WRAPPED_MEK, target), b64)
            .putInt(getKey(KEY_ACTIVE_KEY_ID, target), version)
            .apply()
    }

    override fun getActiveKeyId(accountId: String?): Int {
        val target = resolveAccountId(accountId)
        val accountKey = getKey(KEY_ACTIVE_KEY_ID, target)
        if (prefs.contains(accountKey)) {
            return prefs.getInt(accountKey, CryptoConstants.DEFAULT_KEY_ID)
        }
        val migratedTo = prefs.getString(KEY_LEGACY_MIGRATED_TO, null)
        if ((migratedTo == null || migratedTo == target) && prefs.contains(KEY_ACTIVE_KEY_ID)) {
            return prefs.getInt(KEY_ACTIVE_KEY_ID, CryptoConstants.DEFAULT_KEY_ID)
        }
        return CryptoConstants.DEFAULT_KEY_ID
    }

    override fun getRecoveryWrappedMek(accountId: String?): ByteArray? {
        val target = resolveAccountId(accountId)
        val accountKey = getKey(KEY_RECOVERY_WRAPPED_MEK, target)
        val b64 = prefs.getString(accountKey, null)
        if (b64 != null) {
            return decodeBase64(b64)
        }
        val migratedTo = prefs.getString(KEY_LEGACY_MIGRATED_TO, null)
        if (migratedTo == null || migratedTo == target) {
            val legacyB64 = prefs.getString(KEY_RECOVERY_WRAPPED_MEK, null)
            if (legacyB64 != null) {
                return decodeBase64(legacyB64)
            }
        }
        return null
    }

    override fun setRecoveryWrappedMek(bytes: ByteArray, accountId: String?) {
        val target = resolveAccountId(accountId)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        prefs
            .edit()
            .putString(getKey(KEY_RECOVERY_WRAPPED_MEK, target), b64)
            .apply()
    }

    override fun isRecoveryPhraseBackedUp(accountId: String?): Boolean {
        val target = resolveAccountId(accountId)
        val accountKey = getKey(KEY_RECOVERY_BACKED_UP, target)
        if (prefs.contains(accountKey)) {
            return prefs.getBoolean(accountKey, false)
        }
        val migratedTo = prefs.getString(KEY_LEGACY_MIGRATED_TO, null)
        if (migratedTo == null || migratedTo == target) {
            return prefs.getBoolean(KEY_RECOVERY_BACKED_UP, false)
        }
        return false
    }

    override fun setRecoveryPhraseBackedUp(backedUp: Boolean, accountId: String?) {
        val target = resolveAccountId(accountId)
        prefs
            .edit()
            .putBoolean(getKey(KEY_RECOVERY_BACKED_UP, target), backedUp)
            .apply()
    }

    override fun clear(accountId: String?) {
        val target = resolveAccountId(accountId)
        val safeAcc = sanitizeAccount(target)
        val prefix = "${safeAcc}_"
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) {
                editor.remove(key)
            }
        }
        if (prefs.getString(KEY_LEGACY_MIGRATED_TO, null) == target) {
            editor.remove(KEY_LEGACY_MIGRATED_TO)
        }
        editor.apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    override fun hasKeys(accountId: String?): Boolean {
        val target = resolveAccountId(accountId)
        val accountKey = getKey(KEY_LOCAL_WRAPPED_MEK, target)
        if (prefs.contains(accountKey)) return true
        val migratedTo = prefs.getString(KEY_LEGACY_MIGRATED_TO, null)
        return (migratedTo == null || migratedTo == target) && prefs.contains(KEY_LOCAL_WRAPPED_MEK)
    }

    override fun copyKeys(sourceAccountId: String, targetAccountId: String) {
        val local = getLocalWrappedMek(sourceAccountId) ?: return
        val version = getActiveKeyId(sourceAccountId)
        setLocalWrappedMek(local, version, targetAccountId)

        val recovery = getRecoveryWrappedMek(sourceAccountId)
        if (recovery != null) {
            setRecoveryWrappedMek(recovery, targetAccountId)
        }
        val backedUp = isRecoveryPhraseBackedUp(sourceAccountId)
        setRecoveryPhraseBackedUp(backedUp, targetAccountId)
    }

    private fun decodeBase64(b64: String): ByteArray? {
        return try {
            Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "notas_crypto_prefs"
        private const val KEY_LOCAL_WRAPPED_MEK = "local_wrapped_mek"
        private const val KEY_ACTIVE_KEY_ID = "active_key_id"
        private const val KEY_RECOVERY_WRAPPED_MEK = "recovery_wrapped_mek"
        private const val KEY_RECOVERY_BACKED_UP = "recovery_phrase_backed_up"
        private const val KEY_LEGACY_MIGRATED_TO = "legacy_mek_migrated_to"
    }
}

/**
 * In-memory storage for unit testing and ephemeral sessions.
 * Supports multi-account partitions and legacy migration simulation.
 */
class InMemoryCryptoStorage(
    private val accountIdProvider: () -> String? = { null },
) : CryptoStorage {
    data class AccountData(
        var localWrappedMek: ByteArray? = null,
        var activeKeyId: Int = CryptoConstants.DEFAULT_KEY_ID,
        var recoveryWrappedMek: ByteArray? = null,
        var recoveryBackedUp: Boolean = false,
    )

    private val partitions = ConcurrentHashMap<String, AccountData>()
    private var legacyData: AccountData? = null
    private var legacyMigratedTo: String? = null

    private fun resolveAccountId(accountId: String?): String =
        accountId ?: accountIdProvider() ?: CryptoStorage.GUEST_ACCOUNT_ID

    /** Helper for unit testing legacy pre-F03 state */
    fun seedLegacyData(
        localWrappedMek: ByteArray,
        activeKeyId: Int = CryptoConstants.DEFAULT_KEY_ID,
        recoveryWrappedMek: ByteArray? = null,
        recoveryBackedUp: Boolean = false,
    ) {
        legacyData = AccountData(
            localWrappedMek = localWrappedMek.clone(),
            activeKeyId = activeKeyId,
            recoveryWrappedMek = recoveryWrappedMek?.clone(),
            recoveryBackedUp = recoveryBackedUp,
        )
        legacyMigratedTo = null
    }

    @Synchronized
    override fun getLocalWrappedMek(accountId: String?): ByteArray? {
        val target = resolveAccountId(accountId)
        val data = partitions[target]
        if (data?.localWrappedMek != null) {
            return data.localWrappedMek?.clone()
        }

        val legacy = legacyData
        if (legacy?.localWrappedMek != null && (legacyMigratedTo == null || legacyMigratedTo == target)) {
            val migrated = AccountData(
                localWrappedMek = legacy.localWrappedMek?.clone(),
                activeKeyId = legacy.activeKeyId,
                recoveryWrappedMek = legacy.recoveryWrappedMek?.clone(),
                recoveryBackedUp = legacy.recoveryBackedUp,
            )
            partitions[target] = migrated
            legacyMigratedTo = target
            return migrated.localWrappedMek?.clone()
        }
        return null
    }

    @Synchronized
    override fun setLocalWrappedMek(
        bytes: ByteArray,
        version: Int,
        accountId: String?,
    ) {
        val target = resolveAccountId(accountId)
        val data = partitions.computeIfAbsent(target) { AccountData() }
        data.localWrappedMek = bytes.clone()
        data.activeKeyId = version
    }

    @Synchronized
    override fun getActiveKeyId(accountId: String?): Int {
        val target = resolveAccountId(accountId)
        val data = partitions[target]
        if (data != null && data.localWrappedMek != null) {
            return data.activeKeyId
        }
        val legacy = legacyData
        if (legacy != null && (legacyMigratedTo == null || legacyMigratedTo == target)) {
            return legacy.activeKeyId
        }
        return CryptoConstants.DEFAULT_KEY_ID
    }

    @Synchronized
    override fun getRecoveryWrappedMek(accountId: String?): ByteArray? {
        val target = resolveAccountId(accountId)
        val data = partitions[target]
        if (data?.recoveryWrappedMek != null) {
            return data.recoveryWrappedMek?.clone()
        }
        val legacy = legacyData
        if (legacy?.recoveryWrappedMek != null && (legacyMigratedTo == null || legacyMigratedTo == target)) {
            return legacy.recoveryWrappedMek?.clone()
        }
        return null
    }

    @Synchronized
    override fun setRecoveryWrappedMek(bytes: ByteArray, accountId: String?) {
        val target = resolveAccountId(accountId)
        val data = partitions.computeIfAbsent(target) { AccountData() }
        data.recoveryWrappedMek = bytes.clone()
    }

    @Synchronized
    override fun isRecoveryPhraseBackedUp(accountId: String?): Boolean {
        val target = resolveAccountId(accountId)
        val data = partitions[target]
        if (data != null && data.localWrappedMek != null) {
            return data.recoveryBackedUp
        }
        val legacy = legacyData
        if (legacy != null && (legacyMigratedTo == null || legacyMigratedTo == target)) {
            return legacy.recoveryBackedUp
        }
        return false
    }

    @Synchronized
    override fun setRecoveryPhraseBackedUp(backedUp: Boolean, accountId: String?) {
        val target = resolveAccountId(accountId)
        val data = partitions.computeIfAbsent(target) { AccountData() }
        data.recoveryBackedUp = backedUp
    }

    @Synchronized
    override fun clear(accountId: String?) {
        val target = resolveAccountId(accountId)
        partitions.remove(target)
        if (legacyMigratedTo == target) {
            legacyMigratedTo = null
        }
    }

    @Synchronized
    override fun clearAll() {
        partitions.clear()
        legacyData = null
        legacyMigratedTo = null
    }

    @Synchronized
    override fun hasKeys(accountId: String?): Boolean {
        val target = resolveAccountId(accountId)
        if (partitions[target]?.localWrappedMek != null) return true
        val legacy = legacyData
        return legacy?.localWrappedMek != null && (legacyMigratedTo == null || legacyMigratedTo == target)
    }

    @Synchronized
    override fun copyKeys(sourceAccountId: String, targetAccountId: String) {
        val source = partitions[sourceAccountId] ?: return
        val target = partitions.computeIfAbsent(targetAccountId) { AccountData() }
        target.localWrappedMek = source.localWrappedMek?.clone()
        target.activeKeyId = source.activeKeyId
        target.recoveryWrappedMek = source.recoveryWrappedMek?.clone()
        target.recoveryBackedUp = source.recoveryBackedUp
    }
}
