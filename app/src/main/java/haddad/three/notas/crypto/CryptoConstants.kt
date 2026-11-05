package haddad.three.notas.crypto

/**
 * Constants and specifications for Notas Client-Side End-to-End Encryption (E2EE).
 */
object CryptoConstants {
    /** 8-byte magic identifier at the start of all encrypted containers */
    val CONTAINER_MAGIC =
        byteArrayOf(
            'N'.code.toByte(),
            'O'.code.toByte(),
            'T'.code.toByte(),
            'A'.code.toByte(),
            'S'.code.toByte(),
            '_'.code.toByte(),
            'E'.code.toByte(),
            '1'.code.toByte(),
        )

    /** Current container binary format version */
    const val CONTAINER_VERSION_1: Short = 1

    /** Default Master Encryption Key ID */
    const val DEFAULT_KEY_ID: Int = 1

    /** AES-256 key size in bytes */
    const val AES_KEY_SIZE_BYTES = 32

    /** AES-GCM standard nonce (IV) size in bytes (96 bits) */
    const val GCM_NONCE_SIZE_BYTES = 12

    /** AES-GCM authentication tag size in bits */
    const val GCM_TAG_SIZE_BITS = 128

    /** AES-GCM authentication tag size in bytes */
    const val GCM_TAG_SIZE_BYTES = 16

    /** Standard BIP-39 English wordlist size */
    const val BIP39_WORDLIST_SIZE = 2048

    /** BIP-39 entropy size for 12 words (128 bits = 16 bytes) */
    const val BIP39_ENTROPY_BYTES = 16

    /** BIP-39 total word count for 128-bit entropy */
    const val BIP39_WORD_COUNT = 12

    /** BIP-39 standard PBKDF2 iteration count */
    const val BIP39_PBKDF2_ITERATIONS = 2048

    /** AAD tag for MEK recovery wrapping */
    val AAD_MEK_RECOVERY = "NOTAS_MEK_RECOVERY_v1".toByteArray(Charsets.UTF_8)

    /** AAD tag for local Keystore MEK wrapping */
    val AAD_MEK_LOCAL = "NOTAS_MEK_LOCAL_v1".toByteArray(Charsets.UTF_8)

    /** Salt for HKDF recovery key derivation from BIP-39 seed */
    val HKDF_RECOVERY_SALT = "Notas_BIP39_Recovery_Salt_v1".toByteArray(Charsets.UTF_8)

    /** Info for HKDF recovery key derivation */
    val HKDF_RECOVERY_INFO = "notas-mek-recovery-key-v1".toByteArray(Charsets.UTF_8)

    /** Keystore alias prefix */
    const val KEYSTORE_ALIAS_PREFIX = "notas_mek_kek_v"
}
