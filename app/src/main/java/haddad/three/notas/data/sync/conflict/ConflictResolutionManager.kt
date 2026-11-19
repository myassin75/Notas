package haddad.three.notas.data.sync.conflict

import androidx.room.withTransaction
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.sync.change.EntityType
import haddad.three.notas.data.sync.change.LocalChangeRecorder
import haddad.three.notas.data.sync.change.NoteChangePayload
import haddad.three.notas.data.sync.hlc.HlcClock
import haddad.three.notas.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConflictResolutionResult {
    data class Success(
        val message: String,
        val affectedNoteUuid: String? = null,
    ) : ConflictResolutionResult

    data class Error(
        val error: String,
    ) : ConflictResolutionResult
}

/**
 * Domain-level conflict resolution coordinator implementing user-driven resolution actions:
 * Keep Local, Keep Remote, Keep Both, Merge, and Restore Deleted Item.
 *
 * Guarantees:
 * 1. Idempotency across repeated invocations.
 * 2. Causal ordering via monotonic HLC generation.
 * 3. Immediate transactional synchronization publication via LocalChangeRecorder.
 * 4. Zero plaintext leakage for encrypted sensitive payloads.
 */
@Singleton
class ConflictResolutionManager
    @Inject
    constructor(
        private val database: RememberDatabase?,
        private val noteDao: NoteDao,
        private val conflictRecordDao: SyncConflictRecordDao,
        private val conflictMetadataDao: SyncConflictMetadataDao,
        private val tombstoneDao: SyncTombstoneDao,
        private val localChangeRecorder: LocalChangeRecorder,
        private val hlcClock: HlcClock,
        private val mekManager: MekManager? = null,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        fun decryptPayload(
            stored: String?,
            conflictId: String,
        ): String? {
            if (stored == null) return null
            if (!stored.startsWith("NOTAS_E1:")) return stored
            val rawB64 = stored.removePrefix("NOTAS_E1:")
            val mek = mekManager?.getActiveMasterKey() ?: return null
            return try {
                val encrypted = Base64.getDecoder().decode(rawB64)
                val aad = "NOTAS_E1:conflict:$conflictId".toByteArray(Charsets.UTF_8)
                val decrypted =
                    EncryptedContainer.decrypt(
                        containerBytes = encrypted,
                        key = mek,
                        expectedAad = aad,
                    )
                String(decrypted, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun keepLocal(conflictId: String): ConflictResolutionResult =
            withContext(ioDispatcher) {
                val conflict =
                    conflictRecordDao.getByConflictId(conflictId)
                        ?: return@withContext ConflictResolutionResult.Error("Conflict $conflictId not found")
                if (conflict.resolved) {
                    return@withContext ConflictResolutionResult.Success("Conflict already resolved", conflict.entityId)
                }

                val note =
                    noteDao.getNoteEntityByUuid(conflict.entityId)
                        ?: return@withContext ConflictResolutionResult.Error("Note ${conflict.entityId} not found")

                val localDevId = hlcClock.generateNext().deviceId
                val localValue =
                    if (conflict.losingDeviceId == localDevId) {
                        decryptPayload(conflict.losingValue, conflictId) ?: conflict.losingValue
                    } else {
                        decryptPayload(conflict.winningValue, conflictId) ?: conflict.winningValue
                    }

                val updatedNote =
                    when (conflict.fieldName) {
                        "title" -> if (localValue != null) note.copy(title = localValue, updatedAt = System.currentTimeMillis()) else note
                        "body" -> if (localValue != null) note.copy(body = localValue, updatedAt = System.currentTimeMillis()) else note
                        else -> note.copy(updatedAt = System.currentTimeMillis())
                    }

                runInTx {
                    noteDao.update(updatedNote)
                    val newHlc = hlcClock.generateNext()
                    conflictMetadataDao.insertOrUpdate(
                        SyncConflictMetadataEntity(
                            entityType = EntityType.NOTE.name,
                            entityId = updatedNote.uuid,
                            fieldName = conflict.fieldName,
                            hlcTimestamp = newHlc.toString(),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    conflictRecordDao.markResolvedByConflictId(conflictId)
                    localChangeRecorder.recordNoteUpsert(updatedNote)
                }

                ConflictResolutionResult.Success("Kept local version", updatedNote.uuid)
            }

        suspend fun keepRemote(conflictId: String): ConflictResolutionResult =
            withContext(ioDispatcher) {
                val conflict =
                    conflictRecordDao.getByConflictId(conflictId)
                        ?: return@withContext ConflictResolutionResult.Error("Conflict $conflictId not found")
                if (conflict.resolved) {
                    return@withContext ConflictResolutionResult.Success("Conflict already resolved", conflict.entityId)
                }

                val note =
                    noteDao.getNoteEntityByUuid(conflict.entityId)
                        ?: return@withContext ConflictResolutionResult.Error("Note ${conflict.entityId} not found")

                val localDevId = hlcClock.generateNext().deviceId
                val remoteValue =
                    if (conflict.winningDeviceId != localDevId) {
                        decryptPayload(conflict.winningValue, conflictId) ?: conflict.winningValue
                    } else {
                        decryptPayload(conflict.losingValue, conflictId) ?: conflict.losingValue
                    }

                val updatedNote =
                    when (conflict.fieldName) {
                        "title" -> if (remoteValue != null) note.copy(title = remoteValue, updatedAt = System.currentTimeMillis()) else note
                        "body" -> if (remoteValue != null) note.copy(body = remoteValue, updatedAt = System.currentTimeMillis()) else note
                        else -> note.copy(updatedAt = System.currentTimeMillis())
                    }

                runInTx {
                    noteDao.update(updatedNote)
                    val newHlc = hlcClock.generateNext()
                    conflictMetadataDao.insertOrUpdate(
                        SyncConflictMetadataEntity(
                            entityType = EntityType.NOTE.name,
                            entityId = updatedNote.uuid,
                            fieldName = conflict.fieldName,
                            hlcTimestamp = newHlc.toString(),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    conflictRecordDao.markResolvedByConflictId(conflictId)
                    localChangeRecorder.recordNoteUpsert(updatedNote)
                }

                ConflictResolutionResult.Success("Kept remote version", updatedNote.uuid)
            }

        suspend fun keepBoth(conflictId: String): ConflictResolutionResult =
            withContext(ioDispatcher) {
                val conflict =
                    conflictRecordDao.getByConflictId(conflictId)
                        ?: return@withContext ConflictResolutionResult.Error("Conflict $conflictId not found")
                if (conflict.resolved) {
                    return@withContext ConflictResolutionResult.Success("Conflict already resolved", conflict.entityId)
                }

                val existingNote =
                    noteDao.getNoteEntityByUuid(conflict.entityId)
                        ?: return@withContext ConflictResolutionResult.Error("Note ${conflict.entityId} not found")

                val losingVal = decryptPayload(conflict.losingValue, conflictId) ?: conflict.losingValue ?: ""
                val newUuid = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val copyTitle: String
                val copyBody: String
                if (conflict.fieldName == "title") {
                    copyTitle = if (losingVal.isNotBlank()) losingVal else "${existingNote.title} (Conflict Copy)"
                    copyBody = existingNote.body
                } else if (conflict.fieldName == "body") {
                    copyTitle = "${existingNote.title} (Conflict Copy)"
                    copyBody = losingVal
                } else {
                    copyTitle = "${existingNote.title} (Conflict Copy)"
                    copyBody = existingNote.body
                }

                val copyNote =
                    existingNote.copy(
                        id = 0L,
                        uuid = newUuid,
                        title = copyTitle,
                        body = copyBody,
                        createdAt = now,
                        updatedAt = now,
                    )

                runInTx {
                    noteDao.insert(copyNote)
                    conflictRecordDao.markResolvedByConflictId(conflictId)
                    localChangeRecorder.recordNoteUpsert(copyNote)
                }

                ConflictResolutionResult.Success("Preserved both versions as separate notes", newUuid)
            }

        suspend fun merge(
            conflictId: String,
            mergedTitle: String,
            mergedBody: String,
        ): ConflictResolutionResult =
            withContext(ioDispatcher) {
                val conflict =
                    conflictRecordDao.getByConflictId(conflictId)
                        ?: return@withContext ConflictResolutionResult.Error("Conflict $conflictId not found")
                if (conflict.resolved) {
                    return@withContext ConflictResolutionResult.Success("Conflict already resolved", conflict.entityId)
                }

                val note =
                    noteDao.getNoteEntityByUuid(conflict.entityId)
                        ?: return@withContext ConflictResolutionResult.Error("Note ${conflict.entityId} not found")

                val updated =
                    note.copy(
                        title = mergedTitle,
                        body = mergedBody,
                        updatedAt = System.currentTimeMillis(),
                    )

                runInTx {
                    noteDao.update(updated)
                    val newHlc = hlcClock.generateNext()
                    val now = System.currentTimeMillis()
                    conflictMetadataDao.insertOrUpdate(
                        SyncConflictMetadataEntity(EntityType.NOTE.name, updated.uuid, "title", newHlc.toString(), now),
                    )
                    conflictMetadataDao.insertOrUpdate(
                        SyncConflictMetadataEntity(EntityType.NOTE.name, updated.uuid, "body", newHlc.toString(), now),
                    )
                    conflictRecordDao.markResolvedByConflictId(conflictId)
                    localChangeRecorder.recordNoteUpsert(updated)
                }

                ConflictResolutionResult.Success("Merged versions applied", updated.uuid)
            }

        suspend fun restoreDeleted(conflictId: String): ConflictResolutionResult =
            withContext(ioDispatcher) {
                val conflict =
                    conflictRecordDao.getByConflictId(conflictId)
                        ?: return@withContext ConflictResolutionResult.Error("Conflict $conflictId not found")

                val now = System.currentTimeMillis()
                runInTx {
                    tombstoneDao.deleteTombstone(conflict.entityType, conflict.entityId)
                    var note = noteDao.getNoteEntityByUuid(conflict.entityId)
                    if (note != null) {
                        if (note.trashed) {
                            note = note.copy(trashed = false, trashedAt = null, updatedAt = now)
                            noteDao.update(note)
                        }
                    } else {
                        val rawPayload =
                            decryptPayload(conflict.winningValue, conflictId)
                                ?: decryptPayload(conflict.losingValue, conflictId)
                        if (rawPayload != null && rawPayload.startsWith("{")) {
                            val parsed = runCatching { json.decodeFromString<NoteChangePayload>(rawPayload) }.getOrNull()
                            if (parsed != null) {
                                note =
                                    NoteEntity(
                                        id = 0L,
                                        uuid = parsed.uuid,
                                        ownerId = parsed.ownerId,
                                        kind = runCatching { haddad.three.notas.data.NoteKind.valueOf(parsed.kind) }.getOrDefault(haddad.three.notas.data.NoteKind.NOTE),
                                        title = parsed.title,
                                        body = parsed.body,
                                        colorIndex = parsed.colorIndex,
                                        trashed = false,
                                        createdAt = parsed.createdAt,
                                        updatedAt = now,
                                    )
                                noteDao.insert(note)
                            }
                        }
                    }

                    note?.let { restored ->
                        val newHlc = hlcClock.generateNext()
                        conflictMetadataDao.insertOrUpdate(
                            SyncConflictMetadataEntity(EntityType.NOTE.name, restored.uuid, "title", newHlc.toString(), now),
                        )
                        localChangeRecorder.recordNoteUpsert(restored)
                    }

                    conflictRecordDao.markResolvedByConflictId(conflictId)
                }

                ConflictResolutionResult.Success("Restored deleted item", conflict.entityId)
            }

        private suspend fun runInTx(block: suspend () -> Unit) {
            if (database != null) {
                database.withTransaction { block() }
            } else {
                block()
            }
        }
    }

