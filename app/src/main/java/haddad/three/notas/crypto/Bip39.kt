package haddad.three.notas.crypto

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard BIP-39 mnemonic generator, validator, and seed derivation engine.
 * Fully conforms to the Bitcoin BIP-0039 standard specifications.
 */
object Bip39 {
    @Volatile
    private var cachedWordlist: List<String>? = null
    private val wordlistLock = Any()

    /**
     * Retrieves the official BIP-39 English wordlist (2,048 words), cached in memory.
     */
    fun getWordlist(context: Context? = null): List<String> {
        cachedWordlist?.let { return it }

        synchronized(wordlistLock) {
            cachedWordlist?.let { return it }

            val stream =
                context?.assets?.open("bip39_english.txt")
                    ?: Bip39::class.java.classLoader?.getResourceAsStream("bip39_english.txt")
                    ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("bip39_english.txt")
                    ?: throw IllegalStateException("BIP-39 english wordlist asset/resource 'bip39_english.txt' not found")

            val words =
                stream.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                }

            check(words.size == CryptoConstants.BIP39_WORDLIST_SIZE) {
                "Invalid wordlist size: expected ${CryptoConstants.BIP39_WORDLIST_SIZE}, found ${words.size}"
            }

            cachedWordlist = words
            return words
        }
    }

    /**
     * Generates a standard BIP-39 12-word mnemonic phrase from 128 bits of cryptographically secure entropy.
     */
    fun generateMnemonic(
        customEntropy: ByteArray? = null,
        context: Context? = null,
    ): List<String> {
        val entropy =
            if (customEntropy != null) {
                require(customEntropy.size == CryptoConstants.BIP39_ENTROPY_BYTES) {
                    "Entropy must be exactly ${CryptoConstants.BIP39_ENTROPY_BYTES} bytes (128 bits) for a 12-word mnemonic"
                }
                customEntropy
            } else {
                val bytes = ByteArray(CryptoConstants.BIP39_ENTROPY_BYTES)
                SecureRandom().nextBytes(bytes)
                bytes
            }

        // Checksum is the first ENT / 32 bits of SHA-256(entropy) -> 128 / 32 = 4 bits
        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = (sha256[0].toInt() and 0xFF) ushr 4 // first 4 bits

        // 128 bits entropy + 4 bits checksum = 132 bits
        // Split into 12 groups of 11 bits
        val bitString =
            buildString(132) {
                for (byte in entropy) {
                    append((byte.toInt() and 0xFF).toString(2).padStart(8, '0'))
                }
                append(checksumBits.toString(2).padStart(4, '0'))
            }

        val wordlist = getWordlist(context)
        val mnemonic = ArrayList<String>(CryptoConstants.BIP39_WORD_COUNT)

        for (i in 0 until CryptoConstants.BIP39_WORD_COUNT) {
            val chunk = bitString.substring(i * 11, (i + 1) * 11)
            val index = chunk.toInt(2)
            mnemonic.add(wordlist[index])
        }

        return mnemonic
    }

    /**
     * Validates a BIP-39 mnemonic phrase against the wordlist and verifies its checksum.
     */
    fun validateMnemonic(
        mnemonic: List<String>,
        context: Context? = null,
    ): Result<Unit> {
        if (mnemonic.size != CryptoConstants.BIP39_WORD_COUNT) {
            return Result.failure(
                IllegalArgumentException("Invalid mnemonic word count: expected 12, got ${mnemonic.size}"),
            )
        }

        val wordlist = getWordlist(context)
        val wordIndexMap = wordlist.withIndex().associate { it.value to it.index }

        val bitStringBuilder = StringBuilder(132)
        for (word in mnemonic) {
            val index =
                wordIndexMap[word.lowercase()]
                    ?: return Result.failure(IllegalArgumentException("Word '$word' is not present in BIP-39 wordlist"))
            bitStringBuilder.append(index.toString(2).padStart(11, '0'))
        }

        val bitString = bitStringBuilder.toString()
        val entropyBitString = bitString.substring(0, 128)
        val expectedChecksumBits = bitString.substring(128, 132).toInt(2)

        // Parse reconstructed entropy bytes
        val reconstructedEntropy = ByteArray(16)
        for (i in 0 until 16) {
            val byteStr = entropyBitString.substring(i * 8, (i + 1) * 8)
            reconstructedEntropy[i] = byteStr.toInt(2).toByte()
        }

        val actualChecksum = (MessageDigest.getInstance("SHA-256").digest(reconstructedEntropy)[0].toInt() and 0xFF) ushr 4
        if (actualChecksum != expectedChecksumBits) {
            return Result.failure(
                IllegalArgumentException("Invalid BIP-39 mnemonic checksum: phrase is corrupted or mistyped"),
            )
        }

        return Result.success(Unit)
    }

    /**
     * Derives a 512-bit (64-byte) seed from a BIP-39 mnemonic and optional passphrase
     * using standard BIP-39 PBKDF2-HMAC-SHA512 with 2048 iterations.
     */
    fun mnemonicToSeed(
        mnemonic: List<String>,
        passphrase: String = "",
    ): ByteArray {
        val normalizedMnemonic =
            Normalizer.normalize(
                mnemonic.joinToString(" ").lowercase(),
                Normalizer.Form.NFKD,
            )
        val normalizedSalt = Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD)

        return pbkdf2HmacSha512(
            password = normalizedMnemonic.toCharArray(),
            salt = normalizedSalt.toByteArray(Charsets.UTF_8),
            iterations = CryptoConstants.BIP39_PBKDF2_ITERATIONS,
            keyLengthBytes = 64,
        )
    }

    /**
     * Derives a 256-bit AES Recovery Key from a BIP-39 mnemonic using standard BIP-39 seed
     * derivation followed by HKDF-SHA256.
     */
    fun deriveRecoveryKey(
        mnemonic: List<String>,
        passphrase: String = "",
        context: Context? = null,
    ): SecretKey {
        validateMnemonic(mnemonic, context).getOrThrow()
        val seed = mnemonicToSeed(mnemonic, passphrase)
        val recoveryKeyBytes =
            Hkdf.deriveKey(
                salt = CryptoConstants.HKDF_RECOVERY_SALT,
                ikm = seed,
                info = CryptoConstants.HKDF_RECOVERY_INFO,
                length = CryptoConstants.AES_KEY_SIZE_BYTES,
            )
        return SecretKeySpec(recoveryKeyBytes, "AES")
    }

    /**
     * Portable, robust PBKDF2 with HMAC-SHA512.
     * Uses standard JCA SecretKeyFactory when available, with pure fallback.
     */
    private fun pbkdf2HmacSha512(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        try {
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = PBEKeySpec(password, salt, iterations, keyLengthBytes * 8)
            return factory.generateSecret(spec).encoded
        } catch (_: Exception) {
            // Pure fallback implementation of PBKDF2-HMAC-SHA512 for platforms lacking the factory
            return pbkdf2HmacSha512Fallback(password, salt, iterations, keyLengthBytes)
        }
    }

    private fun pbkdf2HmacSha512Fallback(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        val hmac = Mac.getInstance("HmacSHA512")
        val passwordBytes = Normalizer.normalize(String(password), Normalizer.Form.NFKD).toByteArray(Charsets.UTF_8)
        hmac.init(SecretKeySpec(passwordBytes, "HmacSHA512"))

        val hLen = 64 // SHA-512 outputs 64 bytes
        val l = Math.ceil(keyLengthBytes.toDouble() / hLen).toInt()
        val r = keyLengthBytes - (l - 1) * hLen
        val out = ByteArray(keyLengthBytes)

        for (i in 1..l) {
            hmac.reset()
            hmac.update(salt)
            hmac.update((i ushr 24).toByte())
            hmac.update((i ushr 16).toByte())
            hmac.update((i ushr 8).toByte())
            hmac.update(i.toByte())
            var u = hmac.doFinal()
            val t = u.clone()

            for (c in 2..iterations) {
                hmac.reset()
                u = hmac.doFinal(u)
                for (k in t.indices) {
                    t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
                }
            }

            val copyLen = if (i == l) r else hLen
            System.arraycopy(t, 0, out, (i - 1) * hLen, copyLen)
        }

        return out
    }
}
