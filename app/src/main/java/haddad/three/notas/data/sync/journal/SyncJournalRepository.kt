package haddad.three.notas.data.sync.journal

import android.content.Context
import androidx.room.withTransaction
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.sync.change.PendingChangeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncJournalRepository @Inject constructor(
    private val context: Context,
    private val database: RememberDatabase?,
    private val journalDao: SyncJournalDao,
    private val sequenceDao: SyncSequenceDao,
    private val pendingChangeDao: PendingChangeDao,
    private val mekManager: MekManager,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun getJournalById(journalId: String): SyncJournalEntity? = withContext(Dispatchers.IO) {
        journalDao.getById(journalId)
    }

    suspend fun getJournalsForDevice(deviceId: String): List<SyncJournalEntity> = withContext(Dispatchers.IO) {
        journalDao.getJournalsForDevice(deviceId)
    }

    suspend fun getReadyToUploadJournals(): List<SyncJournalEntity> = withContext(Dispatchers.IO) {
        journalDao.getReadyToUpload()
    }

    suspend fun readEncryptedBytes(journal: SyncJournalEntity): ByteArray = withContext(Dispatchers.IO) {
        val file = File(journal.storagePath)
        if (!file.exists() || !file.isFile) {
            throw java.io.FileNotFoundException("Journal file not found at ${journal.storagePath}")
        }
        file.readBytes()
    }

    /**
     * Decrypts and deserializes an immutable sync journal.
     * Validates NOTAS_E1 header, formatVersion, AAD, and AES-256-GCM authentication tag.
     */
    suspend fun decryptJournal(
        journal: SyncJournalEntity,
        overrideKey: SecretKey? = null,
    ): SyncJournalDto = withContext(Dispatchers.IO) {
        val key = overrideKey ?: mekManager.getActiveMasterKey()
            ?: throw IllegalStateException("Master Encryption Key is not available.")

        val encryptedBytes = readEncryptedBytes(journal)
        val aad = SyncJournalWriter.buildAad(
            formatVersion = journal.formatVersion,
            journalId = journal.journalId,
            deviceId = journal.deviceId,
            sequence = journal.sequence,
        )

        val decryptedBytes = EncryptedContainer.decrypt(
            containerBytes = encryptedBytes,
            key = key,
            expectedAad = aad,
        )

        val jsonString = String(decryptedBytes, Charsets.UTF_8)
        json.decodeFromString<SyncJournalDto>(jsonString)
    }

    /**
     * Reconciles any interrupted or stale journal creation.
     * If a journal was recorded as [JournalStatus.CREATED] and its encrypted file exists and
     * decrypts cleanly, it is promoted to [JournalStatus.READY_TO_UPLOAD].
     * If its file is missing or corrupted, the journal record is removed and its pending changes
     * are reverted back to PENDING.
     */
    suspend fun recoverInterruptedJournals(deviceId: String): Int = withContext(Dispatchers.IO) {
        val incomplete = journalDao.getByStatus(JournalStatus.CREATED.name).filter { it.deviceId == deviceId }
        var recoveredCount = 0

        for (journal in incomplete) {
            val file = File(journal.storagePath)
            var isValid = false
            if (file.exists() && file.length() > 0L) {
                try {
                    decryptJournal(journal)
                    isValid = true
                } catch (_: Exception) {
                    isValid = false
                }
            }

            if (isValid) {
                journalDao.updateStatus(journal.journalId, JournalStatus.READY_TO_UPLOAD.name, System.currentTimeMillis())
                recoveredCount++
            } else {
                // Interrupted / corrupted: roll back pending changes to PENDING
                val rollbackBlock: suspend () -> Unit = {
                    pendingChangeDao.resetJournaledChangesToPending(journal.journalId)
                    journalDao.deleteById(journal.journalId)
                    if (file.exists()) file.delete()
                }

                if (database != null) {
                    database.withTransaction { rollbackBlock() }
                } else {
                    rollbackBlock()
                }
                recoveredCount++
            }
        }
        recoveredCount
    }
}

