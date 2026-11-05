package haddad.three.notas.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class InvalidContainerException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class IntegrityVerificationException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

data class ContainerMetadata(
    val version: Short,
    val keyId: Int,
    val iv: ByteArray,
    val aad: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContainerMetadata) return false
        return version == other.version &&
            keyId == other.keyId &&
            iv.contentEquals(other.iv) &&
            aad.contentEquals(other.aad)
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + keyId
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + aad.contentHashCode()
        return result
    }
}

/**
 * Binary serializer, deserializer, and streaming cipher for versioned NOTAS_E1 containers.
 *
 * Binary Layout:
 * +------------------------+-------------------+-------------------+--------------------+
 * | Magic (8 bytes)        | Version (2 bytes) | KeyId (4 bytes)   | Nonce/IV (12 bytes)|
 * | "NOTAS_E1"             | 0x0001            | uint32            | random 96-bit IV   |
 * +------------------------+-------------------+-------------------+--------------------+
 * | AAD Length (2 bytes)   | AAD (N bytes)     | Ciphertext + GCM Tag (variable length) |
 * | uint16                 | Associated Data   | Payload + 128-bit Auth Tag             |
 * +------------------------+-------------------+-------------------+--------------------+
 */
object EncryptedContainer {
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private val secureRandom = SecureRandom()

    /**
     * Encrypts a plaintext byte array into a self-contained, authenticated NOTAS_E1 container.
     */
    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        keyId: Int = CryptoConstants.DEFAULT_KEY_ID,
        aad: ByteArray = ByteArray(0),
        customIv: ByteArray? = null,
    ): ByteArray {
        val iv = customIv ?: ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        require(iv.size == CryptoConstants.GCM_NONCE_SIZE_BYTES) {
            "IV must be exactly ${CryptoConstants.GCM_NONCE_SIZE_BYTES} bytes"
        }
        require(aad.size <= 65535) { "AAD size cannot exceed 65,535 bytes" }

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE_BITS, iv),
        )

        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }

        val ciphertextWithTag = cipher.doFinal(plaintext)

        val out =
            ByteArrayOutputStream(
                CryptoConstants.CONTAINER_MAGIC.size + 2 + 4 + iv.size + 2 + aad.size + ciphertextWithTag.size,
            )
        val dataOut = DataOutputStream(out)

        dataOut.write(CryptoConstants.CONTAINER_MAGIC)
        dataOut.writeShort(CryptoConstants.CONTAINER_VERSION_1.toInt())
        dataOut.writeInt(keyId)
        dataOut.write(iv)
        dataOut.writeShort(aad.size)
        if (aad.isNotEmpty()) {
            dataOut.write(aad)
        }
        dataOut.write(ciphertextWithTag)
        dataOut.flush()

        return out.toByteArray()
    }

    /**
     * Decrypts an authenticated NOTAS_E1 container byte array, validating integrity and metadata.
     */
    fun decrypt(
        containerBytes: ByteArray,
        key: SecretKey,
        expectedAad: ByteArray? = null,
    ): ByteArray {
        val minHeaderSize = CryptoConstants.CONTAINER_MAGIC.size + 2 + 4 + CryptoConstants.GCM_NONCE_SIZE_BYTES + 2 + CryptoConstants.GCM_TAG_SIZE_BYTES
        if (containerBytes.size < minHeaderSize) {
            throw InvalidContainerException("Encrypted container payload too small (${containerBytes.size} bytes)")
        }

        val inputStream = ByteArrayInputStream(containerBytes)
        val dataIn = DataInputStream(inputStream)

        // 1. Verify Magic Header
        val magic = ByteArray(CryptoConstants.CONTAINER_MAGIC.size)
        dataIn.readFully(magic)
        if (!Arrays.equals(magic, CryptoConstants.CONTAINER_MAGIC)) {
            throw InvalidContainerException("Invalid container magic header: not a NOTAS_E1 archive")
        }

        // 2. Read Version
        val version = dataIn.readShort()
        if (version != CryptoConstants.CONTAINER_VERSION_1) {
            throw InvalidContainerException("Unsupported container version: $version")
        }

        // 3. Read KeyId & IV
        val keyId = dataIn.readInt()
        val iv = ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES)
        dataIn.readFully(iv)

        // 4. Read AAD
        val aadLength = dataIn.readUnsignedShort()
        val aad = ByteArray(aadLength)
        if (aadLength > 0) {
            dataIn.readFully(aad)
        }

        if (expectedAad != null && !Arrays.equals(aad, expectedAad)) {
            throw IntegrityVerificationException("Container AAD mismatch: metadata has been altered")
        }

        // 5. Read Ciphertext + Tag
        val ciphertextWithTag = inputStream.readBytes()
        if (ciphertextWithTag.size < CryptoConstants.GCM_TAG_SIZE_BYTES) {
            throw InvalidContainerException("Corrupted container: missing GCM authentication tag")
        }

        // 6. Decrypt & Verify Integrity
        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE_BITS, iv),
            )

            if (aad.isNotEmpty()) {
                cipher.updateAAD(aad)
            }

            return cipher.doFinal(ciphertextWithTag)
        } catch (e: Exception) {
            throw IntegrityVerificationException(
                "Decryption failed: integrity verification failed or wrong key provided (${e.message})",
                e,
            )
        }
    }

    /**
     * Streams encryption directly to an OutputStream for large attachments and snapshots.
     */
    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        key: SecretKey,
        keyId: Int = CryptoConstants.DEFAULT_KEY_ID,
        aad: ByteArray = ByteArray(0),
        customIv: ByteArray? = null,
    ) {
        val iv = customIv ?: ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        require(aad.size <= 65535) { "AAD size cannot exceed 65,535 bytes" }

        val dataOut = DataOutputStream(output)
        dataOut.write(CryptoConstants.CONTAINER_MAGIC)
        dataOut.writeShort(CryptoConstants.CONTAINER_VERSION_1.toInt())
        dataOut.writeInt(keyId)
        dataOut.write(iv)
        dataOut.writeShort(aad.size)
        if (aad.isNotEmpty()) {
            dataOut.write(aad)
        }
        dataOut.flush()

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE_BITS, iv),
        )

        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }

        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut, bufferSize = 8192)
        }
    }

    /**
     * Reads and parses container metadata (magic, version, keyId, IV, AAD) without decrypting payload.
     */
    fun readMetadata(containerBytes: ByteArray): ContainerMetadata {
        val minHeaderSize = CryptoConstants.CONTAINER_MAGIC.size + 2 + 4 + CryptoConstants.GCM_NONCE_SIZE_BYTES + 2
        if (containerBytes.size < minHeaderSize) {
            throw InvalidContainerException("Encrypted container payload too small (${containerBytes.size} bytes)")
        }

        val inputStream = ByteArrayInputStream(containerBytes)
        val dataIn = DataInputStream(inputStream)

        val magic = ByteArray(CryptoConstants.CONTAINER_MAGIC.size)
        dataIn.readFully(magic)
        if (!Arrays.equals(magic, CryptoConstants.CONTAINER_MAGIC)) {
            throw InvalidContainerException("Invalid container magic header: not a NOTAS_E1 archive")
        }

        val version = dataIn.readShort()
        if (version != CryptoConstants.CONTAINER_VERSION_1) {
            throw InvalidContainerException("Unsupported container version: $version")
        }

        val keyId = dataIn.readInt()
        val iv = ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES)
        dataIn.readFully(iv)

        val aadLength = dataIn.readUnsignedShort()
        val aad = ByteArray(aadLength)
        if (aadLength > 0) {
            dataIn.readFully(aad)
        }

        return ContainerMetadata(version, keyId, iv, aad)
    }

    /**
     * Streams decryption from an InputStream, writing plaintext directly to OutputStream.
     */
    fun decryptStream(
        input: InputStream,
        output: OutputStream,
        key: SecretKey,
        expectedAad: ByteArray? = null,
    ): ContainerMetadata {
        val dataIn = DataInputStream(input)

        val magic = ByteArray(CryptoConstants.CONTAINER_MAGIC.size)
        try {
            dataIn.readFully(magic)
        } catch (e: EOFException) {
            throw InvalidContainerException("Unexpected end of stream while reading container magic", e)
        }

        if (!Arrays.equals(magic, CryptoConstants.CONTAINER_MAGIC)) {
            throw InvalidContainerException("Invalid container magic header: not a NOTAS_E1 archive")
        }

        val version = dataIn.readShort()
        if (version != CryptoConstants.CONTAINER_VERSION_1) {
            throw InvalidContainerException("Unsupported container version: $version")
        }

        val keyId = dataIn.readInt()
        val iv = ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES)
        dataIn.readFully(iv)

        val aadLength = dataIn.readUnsignedShort()
        val aad = ByteArray(aadLength)
        if (aadLength > 0) {
            dataIn.readFully(aad)
        }

        if (expectedAad != null && !Arrays.equals(aad, expectedAad)) {
            throw IntegrityVerificationException("Container AAD mismatch: metadata has been altered")
        }

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE_BITS, iv),
        )

        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }

        try {
            CipherInputStream(input, cipher).use { cipherIn ->
                cipherIn.copyTo(output, bufferSize = 8192)
            }
        } catch (e: Exception) {
            throw IntegrityVerificationException(
                "Streaming decryption failed: corrupted data or wrong key (${e.message})",
                e,
            )
        }

        return ContainerMetadata(version, keyId, iv, aad)
    }
}
