package haddad.three.notas.crypto

import android.content.Context
import java.util.UUID

/**
 * Representation of a challenged word position during backup verification.
 */
data class ChallengeWord(
    val index: Int, // 0-based index in the 12-word phrase (0..11)
    val wordNumber: Int, // 1-based human-readable position (1..12)
    val promptLabel: String, // e.g. "Word #4"
)

/**
 * Ephemeral recovery setup session holding the newly generated mnemonic and random challenges.
 * This object is NEVER persisted to disk, database, or shared preferences.
 */
data class RecoverySetupSession(
    val mnemonicWords: List<String>,
    val challenges: List<ChallengeWord>,
    val sessionId: String = UUID.randomUUID().toString(),
)

/**
 * Coordinates the secure user recovery phrase setup flow:
 * 1. Generates 12 standard BIP-39 words.
 * 2. Formulates random 3-word verification challenges.
 * 3. Verifies user proof-of-backup before enabling cloud encryption features.
 * 4. Ensures zero plaintext persistence and zero plaintext transmission.
 */
class RecoverySetupManager(
    private val mekManager: MekManager,
    private val context: Context? = null,
) {
    /**
     * Initiates a new recovery phrase setup session.
     * Generates a 12-word BIP-39 phrase and selects 3 random word positions to challenge the user.
     */
    fun startSetupSession(
        customEntropy: ByteArray? = null,
    ): RecoverySetupSession {
        val mnemonic = Bip39.generateMnemonic(customEntropy = customEntropy, context = context)
        val selectedIndices =
            (0 until CryptoConstants.BIP39_WORD_COUNT)
                .shuffled()
                .take(3)
                .sorted()

        val challenges =
            selectedIndices.map { idx ->
                ChallengeWord(
                    index = idx,
                    wordNumber = idx + 1,
                    promptLabel = "Word #${idx + 1}",
                )
            }

        return RecoverySetupSession(
            mnemonicWords = mnemonic,
            challenges = challenges,
        )
    }

    /**
     * Validates the user's answers to the verification challenges.
     * If all challenge positions match the original phrase:
     * - Derives the recovery key.
     * - Wraps the MEK with the recovery key into a self-contained container.
     * - Marks recovery phrase as confirmed and backed up.
     * - Returns the wrapped recovery blob for zero-knowledge cloud vault synchronization.
     */
    fun verifyChallengeAndComplete(
        session: RecoverySetupSession,
        userResponses: Map<Int, String>,
        passphrase: String = "",
    ): Result<ByteArray> {
        for (challenge in session.challenges) {
            val expectedWord =
                session.mnemonicWords
                    .getOrNull(challenge.index)
                    ?.trim()
                    ?.lowercase()
                    ?: return Result.failure(IllegalStateException("Invalid challenge index ${challenge.index}"))

            val userWord = userResponses[challenge.index]?.trim()?.lowercase().orEmpty()

            if (userWord != expectedWord) {
                return Result.failure(
                    IllegalArgumentException(
                        "Verification failed: Word #${challenge.wordNumber} is incorrect. Please check your written backup.",
                    ),
                )
            }
        }

        return try {
            val recoveryBlob =
                mekManager.wrapMekForRecovery(
                    mnemonic = session.mnemonicWords,
                    passphrase = passphrase,
                )
            Result.success(recoveryBlob)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns true if recovery has been fully configured and confirmed.
     */
    fun isRecoveryReady(): Boolean = mekManager.isRecoveryConfigured()
}
