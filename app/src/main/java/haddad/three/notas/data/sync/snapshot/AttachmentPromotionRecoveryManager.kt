package haddad.three.notas.data.sync.snapshot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages two-phase attachment staging and promotion recovery to guarantee that
 * SQLite database references never silently point to missing or unpromoted files.
 */
@Singleton
class AttachmentPromotionRecoveryManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val promotionDao: SyncAttachmentPromotionDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    companion object {
        const val PROMOTION_MANIFEST_FILE_NAME = "sync_promotion_manifest.json"
        const val NOTE_ATTACHMENTS_DIR = "note_attachments"
        const val NOTE_ATTACHMENTS_STAGING_DIR = "note_attachments_staging"

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Writes and fsyncs the promotion manifest to disk before entering the Room transaction.
     */
    suspend fun writePromotionManifest(manifest: AttachmentPromotionManifest): File = withContext(ioDispatcher) {
        val manifestFile = File(context.filesDir, PROMOTION_MANIFEST_FILE_NAME)
        val tempFile = File(context.filesDir, "$PROMOTION_MANIFEST_FILE_NAME.tmp")

        val serialized = json.encodeToString(manifest)
        tempFile.outputStream().use { fos ->
            fos.write(serialized.toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }

        if (!tempFile.renameTo(manifestFile)) {
            tempFile.copyTo(manifestFile, overwrite = true)
            tempFile.delete()
        }
        manifestFile
    }

    /**
     * Executes physical directory promotion after successful Room transaction commit.
     */
    suspend fun promoteStagedAttachments(manifest: AttachmentPromotionManifest): Boolean = withContext(ioDispatcher) {
        for (entry in manifest.entries) {
            val stagedFile = File(manifest.stagingDirPath, entry.stagingFileName)
            if (!stagedFile.exists() || stagedFile.length() != entry.sizeBytes) {
                throw AttachmentIntegrityException("Staged attachment ${entry.stagingFileName} missing or size mismatch")
            }
            val actualHash = computeSha256(stagedFile)
            if (!actualHash.equals(entry.sha256Hex, ignoreCase = true)) {
                throw AttachmentIntegrityException("Staged attachment ${entry.stagingFileName} hash mismatch: expected ${entry.sha256Hex}, got $actualHash")
            }

            val targetDir = File(manifest.targetDirPath, entry.noteId.toString()).apply { mkdirs() }
            val targetFile = File(targetDir, entry.targetFileName)

            stagedFile.copyTo(targetFile, overwrite = true)
        }

        // Mark completed in database
        promotionDao.markCompleted(manifest.snapshotId)

        // Cleanup manifest and staging directory
        val manifestFile = File(context.filesDir, PROMOTION_MANIFEST_FILE_NAME)
        if (manifestFile.exists()) manifestFile.delete()

        val stagingDir = File(manifest.stagingDirPath)
        if (stagingDir.exists()) stagingDir.deleteRecursively()

        true
    }

    /**
     * Startup recovery hook. Scans for interrupted promotions on application boot.
     *
     * Invariants guaranteed:
     * - If Room committed: finishes physical promotion so DB references never point to missing files.
     * - If Room did NOT commit: purges staging files so no orphan data remains.
     */
    suspend fun recoverInterruptedPromotion(): Boolean = withContext(ioDispatcher) {
        val manifestFile = File(context.filesDir, PROMOTION_MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) return@withContext false

        val manifest = runCatching {
            json.decodeFromString<AttachmentPromotionManifest>(manifestFile.readText())
        }.getOrNull()

        if (manifest == null) {
            manifestFile.delete()
            return@withContext false
        }

        val promotionRecord = promotionDao.getBySnapshotId(manifest.snapshotId)
        if (promotionRecord != null && promotionRecord.status == PromotionStatus.COMMITTED) {
            // Room committed! Resume and finish physical promotion
            runCatching {
                promoteStagedAttachments(manifest)
            }.isSuccess
        } else {
            // Room transaction never committed (rolled back by SQLite).
            // Clean up staging files and manifest.
            val stagingDir = File(manifest.stagingDirPath)
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            manifestFile.delete()
            true
        }
    }
}

