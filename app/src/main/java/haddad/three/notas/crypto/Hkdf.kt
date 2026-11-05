package haddad.three.notas.crypto

import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * Standard RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function (HKDF) with SHA-256.
 */
object Hkdf {
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 output length in bytes

    /**
     * HKDF-Extract(salt, IKM) -> PRK
     */
    fun extract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(effectiveSalt, HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand(PRK, info, L) -> OKM
     */
    fun expand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length <= 255 * HASH_LEN) { "Requested output length exceeds maximum allowable HKDF limit" }

        val n = ceil(length.toDouble() / HASH_LEN).toInt()
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        val result = ByteArrayOutputStream(n * HASH_LEN)
        var t = ByteArray(0)

        for (i in 1..n) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            result.write(t)
        }

        return result.toByteArray().copyOf(length)
    }

    /**
     * Combined HKDF: Extract then Expand.
     */
    fun deriveKey(
        salt: ByteArray,
        ikm: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
