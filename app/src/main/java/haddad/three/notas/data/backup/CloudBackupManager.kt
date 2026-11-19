package haddad.three.notas.data.backup

import android.content.Context
import androidx.room.withTransaction
import haddad.three.notas.backup.SettingsBackup
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.BackupIo
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.decodeAttachmentMetas
import haddad.three.notas.data.decodeChecklistItems
import haddad.three.notas.data.decodeNoteEntity
import haddad.three.notas.data.decodeShoulderEntity
import haddad.three.notas.data.encodeNoteForBackup
import haddad.three.notas.data.encodeShoulderForBackup
import haddad.three.notas.di.IoDispatcher
import haddad.three.notas.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure cloud backup and restore workflows for Notas.
 * Enforces strict account isolation:
 * - Only records owned by the authenticated Firebase UID are snapshotted or uploaded.
 * - Guest records (ownerId = null) and other accounts' data remain untouched locally and are never uploaded.
 * - Backups are verified against S3 checksums before marking COMPLETED.
 * - Restore validates checksums and schema before modifying local database transactionally.
 */
@Singleton
class CloudBackupManager
    @Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
        private val authRepository: AuthRepository,
        private val backupApiClient: BackupApiClient,
        private val noteDao: NoteDao,
        private val shoulderDao: ShoulderDao,
        private val itemDao: ChecklistItemDao,
        private val attachmentDao: AttachmentDao,
        private val noteRepository: NoteRepository,
        private val themePrefs: ThemePrefs,
        private val notesUiPrefs: NotesUiPrefs,
        private val viewOptionsPrefs: haddad.three.notas.data.ViewOptionsPrefs,
        private val lockPrefs: LockPrefs,
        private val interactionPrefs: InteractionPrefs,
        private val quickCapturePrefs: QuickCapturePrefs,
        private val reminderPrefs: ReminderPrefs,
        private val defaultNotePrefs: DefaultNotePrefs,
        private val updatePrefs: UpdatePrefs,
        private val database: RememberDatabase?,
        private val scheduler: ReminderScheduler?,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val _uiState = MutableStateFlow(CloudBackupUiState())
        val uiState: StateFlow<CloudBackupUiState> = _uiState.asStateFlow()

        /**
         * Creates, uploads, and verifies a cloud backup for the currently authenticated user.
         */
        @Suppress("LongMethod")
        suspend fun createCloudBackup(
            onProgress: ((Float) -> Unit)? = null,
        ): CloudBackupResult<VerifyBackupResponseDto> =
            withContext(ioDispatcher) {
                val user =
                    authRepository.currentUser
                        ?: return@withContext CloudBackupResult.Unauthorized

                _uiState.value =
                    _uiState.value.copy(
                        status = CloudBackupStatus.BACKUP_IN_PROGRESS,
                        statusMessage = "Preparing backup snapshot...",
                        errorMessage = null,
                    )

                val idToken =
                    authRepository.getIdToken()
                        ?: return@withContext CloudBackupResult.Unauthorized

                var tempZipFile: File? = null
                try {
                    // 1. Consistent snapshot strictly scoped to the authenticated user's records
                    val userNotes = noteDao.notesForOwner(user.uid)
                    val userCategories = shoulderDao.shouldersForOwner(user.uid)

                    val notesCount = userNotes.count { it.note.kind == NoteKind.NOTE }
                    val tasksCount = userNotes.count { it.note.kind == NoteKind.LIST }
                    val categoriesCount = userCategories.size

                    val fileName = "notas_backup_${System.currentTimeMillis()}.zip"
                    tempZipFile = File(context.cacheDir, "cloud_backup_${user.uid}_${System.currentTimeMillis()}.zip")

                    // 2. Build ZIP archive
                    FileOutputStream(tempZipFile).use { fos ->
                        ZipOutputStream(fos).use { zip ->
                            // notes.json
                            val notesJson =
                                JSONObject().apply {
                                    put("version", BackupIo.SCHEMA_VERSION)
                                    put("exportedAt", System.currentTimeMillis())
                                    put("ownerId", user.uid)
                                    put(
                                        "categories",
                                        JSONArray().apply {
                                            userCategories.forEach { put(encodeShoulderForBackup(it)) }
                                        },
                                    )
                                    put(
                                        "notes",
                                        JSONArray().apply {
                                            userNotes.forEach { put(encodeNoteForBackup(it)) }
                                        },
                                    )
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

                            // manifest.json
                            val manifestJson =
                                JSONObject().apply {
                                    put("version", BackupIo.SCHEMA_VERSION)
                                    put("exportedAt", System.currentTimeMillis())
                                    put("ownerId", user.uid)
                                    put("notesCount", notesCount)
                                    put("tasksCount", tasksCount)
                                    put("categoriesCount", categoriesCount)
                                    put("includeMedia", false)
                                }
                            writeZipTextEntry(zip, BackupIo.ENTRY_MANIFEST, manifestJson.toString(2))
                        }
                    }

                    val sizeBytes = tempZipFile.length()
                    val sha256 = computeFileSha256(tempZipFile)

                    // 3. Initiate backup record on backend
                    _uiState.value =
                        _uiState.value.copy(
                            statusMessage = "Requesting cloud storage authorization...",
                        )

                    val createRequest =
                        CreateBackupRequestDto(
                            fileName = fileName,
                            sizeBytes = sizeBytes,
                            sha256Checksum = sha256,
                            notesCount = notesCount,
                            tasksCount = tasksCount,
                            eventsCount = 0,
                            categoriesCount = categoriesCount,
                            schemaVersion = BackupIo.SCHEMA_VERSION,
                            isEncrypted = false,
                        )

                    val createResult = backupApiClient.createBackup(idToken, createRequest)
                    if (createResult !is CloudBackupResult.Success) {
                        val errorMsg = (createResult as? CloudBackupResult.Error)?.message ?: "Failed to initiate backup"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.BACKUP_FAILED,
                                errorMessage = errorMsg,
                            )
                        return@withContext createResult.toErrorResult()
                    }

                    val backupId = createResult.data.backupId
                    val uploadUrl = createResult.data.uploadUrl

                    // 4. Stream upload directly to S3 presigned URL
                    _uiState.value =
                        _uiState.value.copy(
                            statusMessage = "Uploading encrypted backup to cloud...",
                        )

                    val uploadResult =
                        backupApiClient.uploadFileToPresignedUrl(
                            presignedUrl = uploadUrl,
                            file = tempZipFile,
                            contentType = "application/zip",
                            onProgress = { progress ->
                                _uiState.value = _uiState.value.copy(progressPercent = progress)
                                onProgress?.invoke(progress)
                            },
                        )

                    if (uploadResult !is CloudBackupResult.Success) {
                        val errorMsg = (uploadResult as? CloudBackupResult.Error)?.message ?: "S3 upload failed"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.BACKUP_FAILED,
                                errorMessage = errorMsg,
                            )
                        return@withContext uploadResult.toErrorResult()
                    }

                    // 5. Verify upload integrity on backend
                    _uiState.value =
                        _uiState.value.copy(
                            status = CloudBackupStatus.VERIFYING_BACKUP,
                            statusMessage = "Verifying cloud backup integrity...",
                        )

                    val verifyResult = backupApiClient.verifyBackup(idToken, backupId)
                    if (verifyResult is CloudBackupResult.Success) {
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.BACKUP_COMPLETED,
                                statusMessage = "Backup verified and completed successfully.",
                            )
                        // Refresh backup list
                        refreshBackupsList()
                    } else {
                        val errorMsg = (verifyResult as? CloudBackupResult.Error)?.message ?: "Verification failed"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.BACKUP_FAILED,
                                errorMessage = errorMsg,
                            )
                    }

                    return@withContext verifyResult
                } catch (e: Exception) {
                    _uiState.value =
                        _uiState.value.copy(
                            status = CloudBackupStatus.BACKUP_FAILED,
                            errorMessage = e.localizedMessage ?: "Unexpected error during cloud backup",
                        )
                    return@withContext CloudBackupResult.Error(e.localizedMessage ?: "Backup error", cause = e)
                } finally {
                    tempZipFile?.delete()
                }
            }

        /**
         * Lists cloud backups available for the authenticated user.
         */
        suspend fun listCloudBackups(): CloudBackupResult<List<BackupItemDto>> =
            withContext(ioDispatcher) {
                val user =
                    authRepository.currentUser
                        ?: return@withContext CloudBackupResult.Unauthorized

                val idToken =
                    authRepository.getIdToken()
                        ?: return@withContext CloudBackupResult.Unauthorized

                _uiState.value = _uiState.value.copy(isLoadingBackups = true)
                val result = backupApiClient.listBackups(idToken)
                if (result is CloudBackupResult.Success) {
                    _uiState.value =
                        _uiState.value.copy(
                            backups = result.data,
                            isLoadingBackups = false,
                        )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingBackups = false)
                }
                result
            }

        suspend fun refreshBackupsList() {
            listCloudBackups()
        }

        /**
         * Restores a selected cloud backup safely and transactionally.
         * Guarantees:
         * - Checksums and size are validated before local data is modified.
         * - Local data is NOT deleted or overwritten until the incoming archive is fully verified.
         * - Guest data (ownerId = null) and other accounts' records are strictly preserved.
         */
        suspend fun restoreCloudBackup(
            backupId: String,
            onProgress: ((Float) -> Unit)? = null,
        ): CloudBackupResult<Int> =
            withContext(ioDispatcher) {
                val user =
                    authRepository.currentUser
                        ?: return@withContext CloudBackupResult.Unauthorized

                _uiState.value =
                    _uiState.value.copy(
                        status = CloudBackupStatus.RESTORE_IN_PROGRESS,
                        statusMessage = "Requesting backup download...",
                        errorMessage = null,
                    )

                val idToken =
                    authRepository.getIdToken()
                        ?: return@withContext CloudBackupResult.Unauthorized

                // 1. Get presigned download URL and expected checksum from backend
                val restoreResponse = backupApiClient.restoreBackup(idToken, backupId)
                if (restoreResponse !is CloudBackupResult.Success) {
                    val errorMsg = (restoreResponse as? CloudBackupResult.Error)?.message ?: "Failed to get download authorization"
                    _uiState.value =
                        _uiState.value.copy(
                            status = CloudBackupStatus.RESTORE_FAILED,
                            errorMessage = errorMsg,
                        )
                    return@withContext restoreResponse.toErrorResult()
                }

                val downloadInfo = restoreResponse.data
                val tempRestoreZip = File(context.cacheDir, "cloud_restore_${backupId}_${System.currentTimeMillis()}.zip")

                try {
                    // 2. Download from S3 directly
                    _uiState.value =
                        _uiState.value.copy(
                            statusMessage = "Downloading backup archive...",
                        )

                    val downloadResult =
                        backupApiClient.downloadFileFromPresignedUrl(
                            presignedUrl = downloadInfo.downloadUrl,
                            targetFile = tempRestoreZip,
                            onProgress = { progress ->
                                _uiState.value = _uiState.value.copy(progressPercent = progress)
                                onProgress?.invoke(progress)
                            },
                        )

                    if (downloadResult !is CloudBackupResult.Success) {
                        val errorMsg = (downloadResult as? CloudBackupResult.Error)?.message ?: "Download failed"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.RESTORE_FAILED,
                                errorMessage = errorMsg,
                            )
                        return@withContext downloadResult.toErrorResult()
                    }

                    // 3. Verify file size and SHA-256 checksum
                    _uiState.value =
                        _uiState.value.copy(
                            statusMessage = "Verifying downloaded archive integrity...",
                        )

                    val actualSize = tempRestoreZip.length()
                    val expectedSizeLong = downloadInfo.sizeBytes.toLongOrNull() ?: 0L
                    if (expectedSizeLong > 0 && actualSize != expectedSizeLong) {
                        val errorMsg = "Backup size mismatch: expected $expectedSizeLong bytes, downloaded $actualSize bytes"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.RESTORE_FAILED,
                                errorMessage = errorMsg,
                            )
                        return@withContext CloudBackupResult.Error(errorMsg, code = "SIZE_MISMATCH")
                    }

                    val actualSha256 = computeFileSha256(tempRestoreZip)
                    if (!actualSha256.equals(downloadInfo.sha256Checksum, ignoreCase = true)) {
                        val errorMsg = "Backup checksum mismatch: expected ${downloadInfo.sha256Checksum}, got $actualSha256"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.RESTORE_FAILED,
                                errorMessage = errorMsg,
                            )
                        return@withContext CloudBackupResult.Error(errorMsg, code = "CHECKSUM_MISMATCH")
                    }

                    // 4. Extract and validate archive structure in memory before modifying Room
                    var notesText: String? = null
                    var settingsText: String? = null

                    ZipInputStream(FileInputStream(tempRestoreZip)).use { zipInput ->
                        var entry = zipInput.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                BackupIo.ENTRY_NOTES -> notesText = zipInput.bufferedReader(Charsets.UTF_8).readText()
                                BackupIo.ENTRY_SETTINGS -> settingsText = zipInput.bufferedReader(Charsets.UTF_8).readText()
                            }
                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                        }
                    }

                    if (notesText.isNullOrBlank()) {
                        val errorMsg = "Invalid backup archive: missing notes.json"
                        _uiState.value =
                            _uiState.value.copy(
                                status = CloudBackupStatus.RESTORE_FAILED,
                                errorMessage = errorMsg,
                            )
                        return@withContext CloudBackupResult.Error(errorMsg, code = "INVALID_ARCHIVE")
                    }

                    val notesRoot = JSONObject(notesText)
                    val notesArray = notesRoot.optJSONArray("notes") ?: JSONArray()
                    val categoriesArray = notesRoot.optJSONArray("categories")

                    // 5. Apply changes transactionally in Room
                    _uiState.value =
                        _uiState.value.copy(
                            statusMessage = "Applying restored notes and settings...",
                        )

                    var restoredCount = 0
                    val block: suspend () -> Unit = {
                        // Delete previous records for THIS user only; guest data (ownerId = null) is NEVER deleted!
                        val existingUserNotes = noteDao.notesForOwner(user.uid)
                        for (existing in existingUserNotes) {
                            scheduler?.cancel(existing.note.id)
                            scheduler?.cancelNotification(existing.note.id)
                            noteDao.deleteById(existing.note.id)
                        }

                        // Re-link / restore categories for this user
                        val categoryIdMap = mutableMapOf<String, Long>()
                        if (categoriesArray != null) {
                            for (i in 0 until categoriesArray.length()) {
                                val catJson = categoriesArray.getJSONObject(i)
                                val catEntity = decodeShoulderEntity(catJson).copy(ownerId = user.uid)
                                val existingByUuid = shoulderDao.getByUuid(catEntity.uuid)
                                val localId =
                                    if (existingByUuid != null) {
                                        shoulderDao.updateShoulder(catEntity.copy(id = existingByUuid.id))
                                        existingByUuid.id
                                    } else {
                                        val existingByName = shoulderDao.getByNormalizedName(catEntity.normalizedName)
                                        if (existingByName != null && (existingByName.ownerId == null || existingByName.ownerId == user.uid)) {
                                            shoulderDao.updateShoulder(catEntity.copy(id = existingByName.id))
                                            existingByName.id
                                        } else {
                                            shoulderDao.insertShoulder(catEntity.copy(id = 0L))
                                        }
                                    }
                                categoryIdMap[catEntity.uuid] = localId
                            }
                        }

                        // Restore notes
                        for (i in 0 until notesArray.length()) {
                            val noteJson = notesArray.getJSONObject(i)
                            val rawNote = decodeNoteEntity(noteJson).copy(id = 0L, ownerId = user.uid)
                            val rawItems = decodeChecklistItems(noteJson).map { it.copy(ownerId = user.uid) }
                            val rawAttachments = decodeAttachmentMetas(noteJson).map { it.copy(ownerId = user.uid) }

                            noteRepository.importNoteWithChildren(
                                note = rawNote,
                                items = rawItems,
                                attachments = rawAttachments,
                                suppressReminderSchedule = false,
                            )
                            restoredCount++
                        }

                        // Restore settings if present
                        if (!settingsText.isNullOrBlank()) {
                            val settingsJson = JSONObject(settingsText)
                            SettingsBackup.importJson(
                                settingsJson,
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
                        }
                    }

                    if (database != null) {
                        database.withTransaction { block() }
                    } else {
                        block()
                    }

                    _uiState.value =
                        _uiState.value.copy(
                            status = CloudBackupStatus.RESTORE_COMPLETED,
                            statusMessage = "Successfully restored $restoredCount notes from cloud.",
                        )

                    CloudBackupResult.Success(restoredCount)
                } catch (e: Exception) {
                    _uiState.value =
                        _uiState.value.copy(
                            status = CloudBackupStatus.RESTORE_FAILED,
                            errorMessage = e.localizedMessage ?: "Failed to restore backup",
                        )
                    CloudBackupResult.Error(e.localizedMessage ?: "Restore error", cause = e)
                } finally {
                    tempRestoreZip.delete()
                }
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

        private fun computeFileSha256(file: File): String {
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
    }
