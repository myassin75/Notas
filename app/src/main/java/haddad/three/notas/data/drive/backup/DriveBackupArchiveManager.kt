package haddad.three.notas.data.drive.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import haddad.three.notas.backup.SettingsBackup
import haddad.three.notas.crypto.CryptoConstants
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.data.AppMediaStorage
import haddad.three.notas.data.BackupIo
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.data.drive.model.DriveBackupManifest
import haddad.three.notas.data.encodeNoteForBackup
import haddad.three.notas.data.encodeShoulderForBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds, encrypts, validates, and unpacks versioned backup archives for Google Drive.
 *
 * Archive Structure (Plain ZIP before AES-256-GCM containerization):
 * - `manifest.json`: backup metadata, schema version, creation timestamp, plain SHA-256 hash.
 * - `notes.json`: all notes, items, categories, and attachment metadata.
 * - `settings.json`: user settings & preferences.
 * - `attachments/`: raw binary files for attachments belonging to user's notes.
 */
@Singleton
class DriveBackupArchiveManager
    @Inject
    constructor(
        private val noteDao: NoteDao,
        private val shoulderDao: ShoulderDao,
        private val itemDao: ChecklistItemDao,
        private val themePrefs: ThemePrefs,
        private val notesUiPrefs: NotesUiPrefs,
        private val viewOptionsPrefs: ViewOptionsPrefs,
        private val lockPrefs: LockPrefs,
        private val interactionPrefs: InteractionPrefs,
        private val quickCapturePrefs: QuickCapturePrefs,
        private val reminderPrefs: ReminderPrefs,
        private val defaultNotePrefs: DefaultNotePrefs,
        private val updatePrefs: UpdatePrefs,
        private val json: Json = Json { ignoreUnknownKeys = true },
    ) {
        /**
         * Builds a plain ZIP archive from Room data and local attachments, computes its SHA-256 hash,
         * and embeds the manifest.
         */
        suspend fun buildPlainZipArchive(
            context: Context,
            ownerId: String?,
            targetZipFile: File,
            backupId: String = UUID.randomUUID().toString(),
        ): DriveBackupManifest =
            withContext(Dispatchers.IO) {
                val userNotes = if (ownerId != null) noteDao.notesForOwner(ownerId) else noteDao.allNotes()
                val userCategories = if (ownerId != null) shoulderDao.shouldersForOwner(ownerId) else shoulderDao.allShoulders()

                val notesCount = userNotes.count { it.note.kind == NoteKind.NOTE }
                val tasksCount = userNotes.count { it.note.kind == NoteKind.LIST }
                val eventsCount = userNotes.count { it.note.reminderAt != null || it.note.endAt != null || it.note.isAllDay }
                val categoriesCount = userCategories.size
                val allAttachments = userNotes.flatMap { it.attachments }

                val createdAt = System.currentTimeMillis()
                val sourceDevice = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

                // 1. Build initial ZIP without plainSha256 in manifest
                FileOutputStream(targetZipFile).use { fos ->
                    ZipOutputStream(fos).use { zip ->
                        // notes.json
                        val notesJson =
                            JSONObject().apply {
                                put("version", BackupIo.SCHEMA_VERSION)
                                put("exportedAt", createdAt)
                                ownerId?.let { put("ownerId", it) }
                                put("categories", JSONArray().apply { userCategories.forEach { put(encodeShoulderForBackup(it)) } })
                                put("notes", JSONArray().apply { userNotes.forEach { put(encodeNoteForBackup(it)) } })
                            }
                        writeZipTextEntry(zip, BackupIo.ENTRY_NOTES, notesJson.toString(2))

                        // settings.json
                        val settingsJson =
                            SettingsBackup.exportJson(
                                themePrefs,
                                notesUiPrefs,
                                viewOptionsPrefs,
                                lockPrefs,
                                interactionPrefs,
                                quickCapturePrefs,
                                reminderPrefs,
                                defaultNotePrefs,
                                updatePrefs,
                            )
                        writeZipTextEntry(zip, BackupIo.ENTRY_SETTINGS, settingsJson.toString(2))

                        // attachments/
                        for (att in allAttachments) {
                            val file = resolveAttachmentFile(context, att.uri)
                            if (file != null && file.exists() && file.isFile) {
                                val entryName = "attachments/${att.uuid}_${sanitizeFileName(att.displayName)}"
                                zip.putNextEntry(ZipEntry(entryName))
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(zip, bufferSize = 8192)
                                }
                                zip.closeEntry()
                            }
                        }

                        // manifest.json
                        val manifest =
                            DriveBackupManifest(
                                backupId = backupId,
                                createdAtEpochMillis = createdAt,
                                schemaVersion = BackupIo.SCHEMA_VERSION,
                                appVersion = "1.0.0",
                                notesCount = notesCount,
                                tasksCount = tasksCount,
                                eventsCount = eventsCount,
                                categoriesCount = categoriesCount,
                                attachmentsCount = allAttachments.size,
                                ownerId = ownerId,
                                sourceDevice = sourceDevice,
                                plainSha256 = "", // Updated below
                            )
                        writeZipTextEntry(zip, BackupIo.ENTRY_MANIFEST, json.encodeToString(manifest))
                    }
                }

                // 2. Compute SHA-256 of plain archive
                val sha256 = computeFileSha256(targetZipFile)

                DriveBackupManifest(
                    backupId = backupId,
                    createdAtEpochMillis = createdAt,
                    schemaVersion = BackupIo.SCHEMA_VERSION,
                    appVersion = "1.0.0",
                    notesCount = notesCount,
                    tasksCount = tasksCount,
                    eventsCount = eventsCount,
                    categoriesCount = categoriesCount,
                    attachmentsCount = allAttachments.size,
                    ownerId = ownerId,
                    sourceDevice = sourceDevice,
                    plainSha256 = sha256,
                )
            }

        /**
         * Encrypts the plain ZIP archive into a versioned `NOTAS_E1` binary container.
         */
        suspend fun encryptArchiveToFile(
            plainZipFile: File,
            encryptedContainerFile: File,
            mek: SecretKey,
            keyId: Int = CryptoConstants.DEFAULT_KEY_ID,
            aad: ByteArray = ByteArray(0),
        ) = withContext(Dispatchers.IO) {
            FileInputStream(plainZipFile).use { fis ->
                FileOutputStream(encryptedContainerFile).use { fos ->
                    EncryptedContainer.encryptStream(
                        input = fis,
                        output = fos,
                        key = mek,
                        keyId = keyId,
                        aad = aad,
                    )
                }
            }
        }

        /**
         * Decrypts a `NOTAS_E1` container into a staging plain ZIP archive, verifying integrity.
         */
        suspend fun decryptArchiveToFile(
            encryptedContainerFile: File,
            plainZipFile: File,
            mek: SecretKey,
            expectedAad: ByteArray? = null,
        ) = withContext(Dispatchers.IO) {
            FileInputStream(encryptedContainerFile).use { fis ->
                FileOutputStream(plainZipFile).use { fos ->
                    EncryptedContainer.decryptStream(
                        input = fis,
                        output = fos,
                        key = mek,
                        expectedAad = expectedAad,
                    )
                }
            }
        }

        companion object {
            const val MAX_ENTRIES = 10_000
            const val MAX_TOTAL_DECOMPRESSED_BYTES = 500L * 1024 * 1024 // 500 MB limit against ZIP bombs
            const val MAX_ENTRY_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB per entry limit
        }

        /**
         * Inspects only the `manifest.json` header entry of a plain ZIP archive without extracting all entries.
         */
        suspend fun peekManifest(plainZipFile: File): DriveBackupManifest =
            withContext(Dispatchers.IO) {
                ZipInputStream(FileInputStream(plainZipFile)).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        if (entry.name == BackupIo.ENTRY_MANIFEST && !entry.isDirectory) {
                            val bytes = readStreamWithLimit(zipInput, MAX_ENTRY_SIZE_BYTES, entry.name)
                            val text = bytes.toString(Charsets.UTF_8)
                            return@withContext json.decodeFromString<DriveBackupManifest>(text)
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
                throw IllegalArgumentException("Archive is missing required manifest.json")
            }

        /**
         * Extracts and validates manifest and JSON bodies from a decrypted plain ZIP archive into an isolated staging directory.
         * Enforces strict anti-Zip Slip checks, entry count limits, decompressed size limits, duplicate entry prevention,
         * and file type whitelisting.
         */
        suspend fun extractArchiveContents(
            plainZipFile: File,
            targetStagingDir: File? = null,
        ): ExtractedArchiveData =
            withContext(Dispatchers.IO) {
                var manifestText: String? = null
                var notesText: String? = null
                var settingsText: String? = null
                val attachmentFiles = mutableMapOf<String, File>()

                val extractDir = targetStagingDir ?: File(plainZipFile.parentFile, "extracted_${UUID.randomUUID()}").apply { mkdirs() }
                val canonicalExtractDir = extractDir.canonicalPath

                val seenEntries = mutableSetOf<String>()
                var totalEntries = 0
                var totalDecompressedBytes = 0L

                try {
                    ZipInputStream(FileInputStream(plainZipFile)).use { zipInput ->
                        var entry = zipInput.nextEntry
                        while (entry != null) {
                            totalEntries++
                            if (totalEntries > MAX_ENTRIES) {
                                throw SecurityException("Archive exceeds maximum entry limit ($MAX_ENTRIES)")
                            }

                            val entryName = entry.name
                            // Reject absolute paths, path traversal, backslashes, colon drives
                            if (entryName.startsWith("/") || entryName.startsWith("\\") ||
                                entryName.contains("..") || entryName.contains(":")
                            ) {
                                throw SecurityException("Malicious path traversal detected in archive entry: $entryName")
                            }

                            // Reject duplicate entries
                            if (!seenEntries.add(entryName)) {
                                throw SecurityException("Duplicate archive entry detected: $entryName")
                            }

                            when {
                                entryName == BackupIo.ENTRY_MANIFEST -> {
                                    val data = readStreamWithLimit(zipInput, MAX_ENTRY_SIZE_BYTES, entryName)
                                    totalDecompressedBytes += data.size
                                    checkTotalSize(totalDecompressedBytes)
                                    manifestText = data.toString(Charsets.UTF_8)
                                }
                                entryName == BackupIo.ENTRY_NOTES -> {
                                    val data = readStreamWithLimit(zipInput, MAX_ENTRY_SIZE_BYTES, entryName)
                                    totalDecompressedBytes += data.size
                                    checkTotalSize(totalDecompressedBytes)
                                    notesText = data.toString(Charsets.UTF_8)
                                }
                                entryName == BackupIo.ENTRY_SETTINGS -> {
                                    val data = readStreamWithLimit(zipInput, MAX_ENTRY_SIZE_BYTES, entryName)
                                    totalDecompressedBytes += data.size
                                    checkTotalSize(totalDecompressedBytes)
                                    settingsText = data.toString(Charsets.UTF_8)
                                }
                                entryName.startsWith("attachments/") && !entry.isDirectory -> {
                                    val subName = entryName.removePrefix("attachments/")
                                    // Enforce strict flat filename whitelist for attachments
                                    if (!subName.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
                                        throw SecurityException("Invalid attachment filename: $subName")
                                    }

                                    val attFile = File(extractDir, subName)
                                    val canonicalAttPath = attFile.canonicalPath
                                    if (!canonicalAttPath.startsWith(canonicalExtractDir + File.separator)) {
                                        throw SecurityException("Zip Slip path traversal blocked: $entryName")
                                    }

                                    attFile.parentFile?.mkdirs()
                                    var entryBytes = 0L
                                    FileOutputStream(attFile).use { fos ->
                                        val buffer = ByteArray(8192)
                                        var read: Int
                                        while (zipInput.read(buffer).also { read = it } != -1) {
                                            entryBytes += read
                                            totalDecompressedBytes += read
                                            if (entryBytes > MAX_ENTRY_SIZE_BYTES) {
                                                throw SecurityException("Attachment $entryName exceeds maximum entry size ($MAX_ENTRY_SIZE_BYTES bytes)")
                                            }
                                            checkTotalSize(totalDecompressedBytes)
                                            fos.write(buffer, 0, read)
                                        }
                                    }
                                    val uuid = attFile.name.substringBefore("_")
                                    attachmentFiles[uuid] = attFile
                                }
                                entry.isDirectory -> {
                                    // Only allow the top-level attachments/ directory entry if present
                                    if (entryName != "attachments/") {
                                        throw SecurityException("Unexpected directory in backup archive: $entryName")
                                    }
                                }
                                else -> {
                                    // Reject unexpected file types (executables, unexpected scripts, hidden files, unknown root files)
                                    throw SecurityException("Unexpected file entry in backup archive: $entryName")
                                }
                            }
                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    extractDir.deleteRecursively()
                    throw e
                }

                val manifest =
                    manifestText?.let {
                        runCatching { json.decodeFromString<DriveBackupManifest>(it) }.getOrNull()
                    } ?: throw IllegalArgumentException("Archive is missing or has corrupted manifest.json")

                ExtractedArchiveData(
                    manifest = manifest,
                    notesJson = notesText,
                    settingsJson = settingsText,
                    attachmentFiles = attachmentFiles,
                    extractedDirectory = extractDir,
                )
            }

        private fun readStreamWithLimit(
            input: ZipInputStream,
            maxBytes: Long,
            entryName: String,
        ): ByteArray {
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                total += read
                if (total > maxBytes) {
                    throw SecurityException("Archive entry $entryName exceeds maximum decompressed size limit ($maxBytes bytes)")
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        private fun checkTotalSize(totalBytes: Long) {
            if (totalBytes > MAX_TOTAL_DECOMPRESSED_BYTES) {
                throw SecurityException("Archive exceeds maximum total decompressed size limit ($MAX_TOTAL_DECOMPRESSED_BYTES bytes)")
            }
        }

        fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun writeZipTextEntry(
            zip: ZipOutputStream,
            entryName: String,
            text: String,
        ) {
            zip.putNextEntry(ZipEntry(entryName))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            writer.write(text)
            writer.flush()
            zip.closeEntry()
        }

        private fun resolveAttachmentFile(
            context: Context,
            uriString: String,
        ): File? =
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    uri.path?.let { File(it) }
                } else {
                    File(context.filesDir, "note_attachments/${uri.lastPathSegment}")
                }
            } catch (_: Exception) {
                null
            }

        private fun sanitizeFileName(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
    }

data class ExtractedArchiveData(
    val manifest: DriveBackupManifest?,
    val notesJson: String?,
    val settingsJson: String?,
    val attachmentFiles: Map<String, File>,
    val extractedDirectory: File,
) {
    fun cleanup() {
        extractedDirectory.deleteRecursively()
    }
}
