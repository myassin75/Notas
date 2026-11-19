package haddad.three.notas.data.sync.conflict

import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.Visibility
import haddad.three.notas.data.sync.change.AttachmentChangePayload
import haddad.three.notas.data.sync.change.CategoryChangePayload
import haddad.three.notas.data.sync.change.ChecklistItemChangePayload
import haddad.three.notas.data.sync.change.DeleteChangePayload
import haddad.three.notas.data.sync.change.EntityType
import haddad.three.notas.data.sync.change.NoteChangePayload
import haddad.three.notas.data.sync.change.OperationType
import haddad.three.notas.data.sync.change.PreferenceChangePayload
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.journal.JournalOperationDto
import haddad.three.notas.data.sync.journal.SyncJournalDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

class UnsupportedOperationException(
    message: String,
) : Exception(message)

class InvalidOperationPayloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MalformedPathException(
    message: String,
) : SecurityException(message)

/**
 * Resolves incoming synchronization operations using field-level Hybrid Logical Clock (HLC)
 * ordering, preserving non-mergeable content edits and preventing zombie resurrects via durable tombstones.
 */
@Singleton
class FieldConflictResolver
    @Inject
    constructor(
        private val noteDao: NoteDao,
        private val checklistItemDao: ChecklistItemDao,
        private val shoulderDao: ShoulderDao,
        private val attachmentDao: AttachmentDao,
        private val conflictMetadataDao: SyncConflictMetadataDao,
        private val tombstoneDao: SyncTombstoneDao,
        private val conflictRecordDao: SyncConflictRecordDao,
        private val mekManager: MekManager? = null,
        private val localDeviceIdProvider: (() -> String)? = null,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private val baseValues = java.util.concurrent.ConcurrentHashMap<String, String>()

        suspend fun resolveAndApply(
            operation: JournalOperationDto,
            operationHlc: Hlc,
            journal: SyncJournalDto,
        ) {
            if (operation.schemaVersion > 1) {
                throw UnsupportedOperationException(
                    "Unsupported operation schemaVersion ${operation.schemaVersion} for change ${operation.changeId}",
                )
            }

            val entityType =
                try {
                    EntityType.valueOf(operation.entityType)
                } catch (e: Exception) {
                    throw UnsupportedOperationException("Unknown entityType ${operation.entityType}")
                }

            val operationType =
                try {
                    OperationType.valueOf(operation.operationType)
                } catch (e: Exception) {
                    throw UnsupportedOperationException("Unknown operationType ${operation.operationType}")
                }

            when (entityType) {
                EntityType.NOTE -> handleNote(operation, operationType, operationHlc, journal)
                EntityType.CATEGORY -> handleCategory(operation, operationType, operationHlc, journal)
                EntityType.CHECKLIST_ITEM -> handleChecklistItem(operation, operationType, operationHlc, journal)
                EntityType.ATTACHMENT_METADATA -> handleAttachment(operation, operationType, operationHlc, journal)
                EntityType.USER_PREFERENCE -> handlePreference(operation, operationType, operationHlc)
            }
        }

        private fun isSequential(
            localHlc: Hlc,
            opHlc: Hlc,
            journal: SyncJournalDto,
        ): Boolean {
            if (localHlc.deviceId == opHlc.deviceId) return true
            val ack = journal.dependencies[localHlc.deviceId]
            if (ack != null && ack > 0L) return true
            return false
        }

        private suspend fun handleNote(
            op: JournalOperationDto,
            opType: OperationType,
            opHlc: Hlc,
            journal: SyncJournalDto,
        ) {
            when (opType) {
                OperationType.UPSERT -> {
                    val payloadStr = op.payload ?: throw InvalidOperationPayloadException("Note UPSERT missing payload")
                    val payload =
                        try {
                            json.decodeFromString<NoteChangePayload>(payloadStr)
                        } catch (e: Exception) {
                            throw InvalidOperationPayloadException("Failed to parse NoteChangePayload", e)
                        }

                    // 1. Tombstone check
                    val tombstone = tombstoneDao.getTombstone(EntityType.NOTE.name, payload.uuid)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) {
                            // Stale update: note was deleted at or after this update's logical timestamp
                            return
                        }
                        if (tombstone.deviceId != opHlc.deviceId) {
                            // Concurrent Delete vs Update detected!
                            recordConflict(
                                entityType = EntityType.NOTE.name,
                                entityId = payload.uuid,
                                fieldName = "DELETE_VS_UPDATE",
                                winningValue = payloadStr,
                                losingValue = "DELETED_BY:${tombstone.deviceId}:${tombstone.hlcTimestamp}",
                                winningHlc = opHlc,
                                losingHlc = tombstoneHlc,
                                encryptPayload = true,
                            )
                        }
                        // Newer update clears tombstone
                        tombstoneDao.deleteTombstone(EntityType.NOTE.name, payload.uuid)
                    }

                    val existing = noteDao.getNoteEntityByUuid(payload.uuid)
                    if (existing == null) {
                        // Entity Creation
                        val shoulderId = payload.categoryUuid?.let { shoulderDao.getByUuid(it)?.id }
                        val newNote =
                            NoteEntity(
                                id = 0L,
                                uuid = payload.uuid,
                                ownerId = payload.ownerId,
                                kind =
                                    try {
                                        NoteKind.valueOf(payload.kind)
                                    } catch (e: Exception) {
                                        NoteKind.NOTE
                                    },
                                title = payload.title,
                                body = payload.body,
                                colorIndex = payload.colorIndex,
                                trashed = payload.trashed,
                                createdAt = payload.createdAt,
                                updatedAt = payload.updatedAt,
                                reminderAt = payload.reminderAt,
                                endAt = payload.endAt,
                                isAllDay = payload.isAllDay,
                                importance =
                                    try {
                                        Importance.valueOf(payload.importance)
                                    } catch (e: Exception) {
                                        Importance.DEFAULT
                                    },
                                visibility =
                                    try {
                                        Visibility.valueOf(payload.visibility)
                                    } catch (e: Exception) {
                                        Visibility.DEFAULT
                                    },
                                pictureUri = payload.pictureUri,
                                pictureHeroFraming = payload.pictureHeroFraming,
                                locked = payload.locked,
                                iconKey = payload.iconKey,
                                shoulderId = shoulderId,
                                archived = payload.archived,
                                trashedAt = payload.trashedAt,
                                completedAt = payload.completedAt,
                                pinnedAt = payload.pinnedAt,
                            )
                        noteDao.insert(newNote)

                        // Record initial field metadata & base values
                        val now = System.currentTimeMillis()
                        val hlcStr = opHlc.toString()
                        val fields = listOf("title", "body", "colorIndex", "trashed", "archived", "pinnedAt", "completedAt", "categoryUuid", "reminderAt")
                        for (f in fields) {
                            conflictMetadataDao.insertOrUpdate(
                                SyncConflictMetadataEntity(EntityType.NOTE.name, payload.uuid, f, hlcStr, now),
                            )
                        }
                        baseValues["${EntityType.NOTE.name}:${payload.uuid}:title"] = payload.title
                        baseValues["${EntityType.NOTE.name}:${payload.uuid}:body"] = payload.body
                    } else {
                        // Field-level merging
                        val now = System.currentTimeMillis()

                        // Title
                        val titleMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "title")
                        val titleHlc = titleMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val titleKey = "${EntityType.NOTE.name}:${existing.uuid}:title"
                        val baseTitle = baseValues[titleKey]
                        val targetTitle: String
                        if (existing.title == payload.title) {
                            targetTitle = existing.title
                            baseValues[titleKey] = existing.title
                            if (opHlc > titleHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "title", opHlc.toString(), now))
                            }
                        } else if (opHlc > titleHlc) {
                            if (baseTitle != null && payload.title == baseTitle) {
                                // Remote did NOT edit title; it simply sent unchanged ancestor title. Keep local edit without conflict!
                                targetTitle = existing.title
                            } else {
                                if (!isSequential(titleHlc, opHlc, journal)) {
                                    recordConflict(
                                        entityType = EntityType.NOTE.name,
                                        entityId = existing.uuid,
                                        fieldName = "title",
                                        winningValue = payload.title,
                                        losingValue = existing.title,
                                        winningHlc = opHlc,
                                        losingHlc = titleHlc,
                                    )
                                }
                                targetTitle = payload.title
                                if (isSequential(titleHlc, opHlc, journal)) {
                                    baseValues[titleKey] = payload.title
                                }
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "title", opHlc.toString(), now))
                            }
                        } else if (opHlc < titleHlc) {
                            if (!isSequential(titleHlc, opHlc, journal)) {
                                recordConflict(
                                    entityType = EntityType.NOTE.name,
                                    entityId = existing.uuid,
                                    fieldName = "title",
                                    winningValue = existing.title,
                                    losingValue = payload.title,
                                    winningHlc = titleHlc,
                                    losingHlc = opHlc,
                                )
                            }
                            targetTitle = existing.title
                        } else {
                            targetTitle = existing.title
                        }

                        // Body (Content)
                        val bodyMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "body")
                        val bodyHlc = bodyMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val bodyKey = "${EntityType.NOTE.name}:${existing.uuid}:body"
                        val baseBody = baseValues[bodyKey]
                        val targetBody: String
                        if (existing.body == payload.body) {
                            targetBody = existing.body
                            baseValues[bodyKey] = existing.body
                            if (opHlc > bodyHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "body", opHlc.toString(), now))
                            }
                        } else if (opHlc > bodyHlc) {
                            if (baseBody != null && payload.body == baseBody) {
                                targetBody = existing.body
                            } else {
                                if (!isSequential(bodyHlc, opHlc, journal)) {
                                    recordConflict(
                                        entityType = EntityType.NOTE.name,
                                        entityId = existing.uuid,
                                        fieldName = "body",
                                        winningValue = payload.body,
                                        losingValue = existing.body,
                                        winningHlc = opHlc,
                                        losingHlc = bodyHlc,
                                        encryptPayload = true,
                                    )
                                }
                                targetBody = payload.body
                                if (isSequential(bodyHlc, opHlc, journal)) {
                                    baseValues[bodyKey] = payload.body
                                }
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "body", opHlc.toString(), now))
                            }
                        } else if (opHlc < bodyHlc) {
                            if (!isSequential(bodyHlc, opHlc, journal)) {
                                recordConflict(
                                    entityType = EntityType.NOTE.name,
                                    entityId = existing.uuid,
                                    fieldName = "body",
                                    winningValue = existing.body,
                                    losingValue = payload.body,
                                    winningHlc = bodyHlc,
                                    losingHlc = opHlc,
                                    encryptPayload = true,
                                )
                            }
                            targetBody = existing.body
                        } else {
                            targetBody = existing.body
                        }

                        // Independent fields
                        val colorMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "colorIndex")
                        val colorHlc = colorMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val targetColor =
                            if (opHlc > colorHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "colorIndex", opHlc.toString(), now))
                                payload.colorIndex
                            } else {
                                existing.colorIndex
                            }

                        val trashedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "trashed")
                        val trashedHlc = trashedMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val (targetTrashed, targetTrashedAt) =
                            if (opHlc > trashedHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "trashed", opHlc.toString(), now))
                                payload.trashed to payload.trashedAt
                            } else {
                                existing.trashed to existing.trashedAt
                            }

                        val archivedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "archived")
                        val archivedHlc = archivedMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val targetArchived =
                            if (opHlc > archivedHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "archived", opHlc.toString(), now))
                                payload.archived
                            } else {
                                existing.archived
                            }

                        val pinnedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "pinnedAt")
                        val pinnedHlc = pinnedMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val targetPinnedAt =
                            if (opHlc > pinnedHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "pinnedAt", opHlc.toString(), now))
                                payload.pinnedAt
                            } else {
                                existing.pinnedAt
                            }

                        val completedMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "completedAt")
                        val completedHlc = completedMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val targetCompletedAt =
                            if (opHlc > completedHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "completedAt", opHlc.toString(), now))
                                payload.completedAt
                            } else {
                                existing.completedAt
                            }

                        val categoryMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "categoryUuid")
                        val categoryHlc = categoryMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val resolvedShoulderId = payload.categoryUuid?.let { shoulderDao.getByUuid(it)?.id }
                        val targetShoulderId =
                            if (opHlc > categoryHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "categoryUuid", opHlc.toString(), now))
                                resolvedShoulderId
                            } else {
                                existing.shoulderId
                            }

                        val reminderMeta = conflictMetadataDao.getFieldMetadata(EntityType.NOTE.name, existing.uuid, "reminderAt")
                        val reminderHlc = reminderMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val (targetReminderAt, targetEndAt, targetIsAllDay) =
                            if (opHlc > reminderHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, existing.uuid, "reminderAt", opHlc.toString(), now))
                                Triple(payload.reminderAt, payload.endAt, payload.isAllDay)
                            } else {
                                Triple(existing.reminderAt, existing.endAt, existing.isAllDay)
                            }

                        val updatedNote =
                            existing.copy(
                                title = targetTitle,
                                body = targetBody,
                                colorIndex = targetColor,
                                trashed = targetTrashed,
                                trashedAt = targetTrashedAt,
                                archived = targetArchived,
                                pinnedAt = targetPinnedAt,
                                completedAt = targetCompletedAt,
                                shoulderId = targetShoulderId,
                                reminderAt = targetReminderAt,
                                endAt = targetEndAt,
                                isAllDay = targetIsAllDay,
                                updatedAt = maxOf(existing.updatedAt, payload.updatedAt),
                            )
                        noteDao.update(updatedNote)
                    }
                }
                OperationType.DELETE -> {
                    // 1. Tombstone check
                    val tombstone = tombstoneDao.getTombstone(EntityType.NOTE.name, op.entityId)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                    }

                    // 2. Metadata check: ensure no newer field updates beat this delete
                    val fields = conflictMetadataDao.getAllFieldsForEntity(EntityType.NOTE.name, op.entityId)
                    val maxFieldHlc = fields.mapNotNull { runCatching { Hlc.parse(it.hlcTimestamp) }.getOrNull() }.maxOrNull()
                    if (maxFieldHlc != null && maxFieldHlc > opHlc) {
                        // Concurrent update vs delete: local was updated after/concurrent with this delete
                        val existing = noteDao.getNoteEntityByUuid(op.entityId)
                        if (existing != null) {
                            recordConflict(
                                entityType = EntityType.NOTE.name,
                                entityId = op.entityId,
                                fieldName = "DELETE_VS_UPDATE",
                                winningValue = json.encodeToString(existing.toChangePayload()),
                                losingValue = "DELETED_BY:${opHlc.deviceId}:${opHlc}",
                                winningHlc = maxFieldHlc,
                                losingHlc = opHlc,
                                encryptPayload = true,
                            )
                        }
                        return
                    }

                    // 3. Delete existing note
                    val existing = noteDao.getNoteEntityByUuid(op.entityId)
                    if (existing != null) {
                        noteDao.deleteById(existing.id)
                    }

                    // 4. Record tombstone
                    val deletedAt =
                        op.payload?.let {
                            runCatching { json.decodeFromString<DeleteChangePayload>(it).deletedAt }.getOrNull()
                        } ?: System.currentTimeMillis()

                    tombstoneDao.insertOrUpdate(
                        SyncTombstoneEntity(
                            entityType = EntityType.NOTE.name,
                            entityId = op.entityId,
                            deletedAt = deletedAt,
                            hlcTimestamp = opHlc.toString(),
                            deviceId = opHlc.deviceId,
                        ),
                    )
                }
            }
        }

        private suspend fun handleCategory(
            op: JournalOperationDto,
            opType: OperationType,
            opHlc: Hlc,
            journal: SyncJournalDto,
        ) {
            when (opType) {
                OperationType.UPSERT -> {
                    val payloadStr = op.payload ?: throw InvalidOperationPayloadException("Category UPSERT missing payload")
                    val payload =
                        try {
                            json.decodeFromString<CategoryChangePayload>(payloadStr)
                        } catch (e: Exception) {
                            throw InvalidOperationPayloadException("Failed to parse CategoryChangePayload", e)
                        }

                    val tombstone = tombstoneDao.getTombstone(EntityType.CATEGORY.name, payload.uuid)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                        tombstoneDao.deleteTombstone(EntityType.CATEGORY.name, payload.uuid)
                    }

                    val existing = shoulderDao.getByUuid(payload.uuid)
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        val shoulder =
                            ShoulderEntity(
                                id = 0L,
                                uuid = payload.uuid,
                                ownerId = payload.ownerId,
                                name = payload.name,
                                normalizedName = payload.normalizedName,
                                colorHex = payload.colorHex,
                                createdAt = payload.createdAt,
                                updatedAt = payload.updatedAt,
                            )
                        shoulderDao.insertShoulder(shoulder)
                        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CATEGORY.name, payload.uuid, "name", opHlc.toString(), now))
                        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CATEGORY.name, payload.uuid, "colorHex", opHlc.toString(), now))
                    } else {
                        val nameMeta = conflictMetadataDao.getFieldMetadata(EntityType.CATEGORY.name, payload.uuid, "name")
                        val nameHlc = nameMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val targetName =
                            if (opHlc > nameHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CATEGORY.name, payload.uuid, "name", opHlc.toString(), now))
                                payload.name
                            } else {
                                existing.name
                            }
                        val targetNormalized = if (opHlc > nameHlc) payload.normalizedName else existing.normalizedName

                        val colorMeta = conflictMetadataDao.getFieldMetadata(EntityType.CATEGORY.name, payload.uuid, "colorHex")
                        val colorHlc = colorMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(existing.updatedAt, 0, "local")
                        val targetColor =
                            if (opHlc > colorHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CATEGORY.name, payload.uuid, "colorHex", opHlc.toString(), now))
                                payload.colorHex
                            } else {
                                existing.colorHex
                            }

                        shoulderDao.updateShoulder(
                            existing.copy(
                                name = targetName,
                                normalizedName = targetNormalized,
                                colorHex = targetColor,
                                updatedAt = maxOf(existing.updatedAt, payload.updatedAt),
                            ),
                        )
                    }
                }
                OperationType.DELETE -> {
                    val tombstone = tombstoneDao.getTombstone(EntityType.CATEGORY.name, op.entityId)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                    }

                    val fields = conflictMetadataDao.getAllFieldsForEntity(EntityType.CATEGORY.name, op.entityId)
                    val maxFieldHlc = fields.mapNotNull { runCatching { Hlc.parse(it.hlcTimestamp) }.getOrNull() }.maxOrNull()
                    if (maxFieldHlc != null && maxFieldHlc > opHlc) return

                    val existing = shoulderDao.getByUuid(op.entityId)
                    if (existing != null) {
                        shoulderDao.deleteShoulder(existing)
                    }

                    val deletedAt =
                        op.payload?.let {
                            runCatching { json.decodeFromString<DeleteChangePayload>(it).deletedAt }.getOrNull()
                        } ?: System.currentTimeMillis()

                    tombstoneDao.insertOrUpdate(
                        SyncTombstoneEntity(
                            entityType = EntityType.CATEGORY.name,
                            entityId = op.entityId,
                            deletedAt = deletedAt,
                            hlcTimestamp = opHlc.toString(),
                            deviceId = opHlc.deviceId,
                        ),
                    )
                }
            }
        }

        private suspend fun handleChecklistItem(
            op: JournalOperationDto,
            opType: OperationType,
            opHlc: Hlc,
            journal: SyncJournalDto,
        ) {
            when (opType) {
                OperationType.UPSERT -> {
                    val payloadStr = op.payload ?: throw InvalidOperationPayloadException("ChecklistItem UPSERT missing payload")
                    val payload =
                        try {
                            json.decodeFromString<ChecklistItemChangePayload>(payloadStr)
                        } catch (e: Exception) {
                            throw InvalidOperationPayloadException("Failed to parse ChecklistItemChangePayload", e)
                        }

                    val tombstone = tombstoneDao.getTombstone(EntityType.CHECKLIST_ITEM.name, payload.uuid)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                        tombstoneDao.deleteTombstone(EntityType.CHECKLIST_ITEM.name, payload.uuid)
                    }

                    val parentNote = noteDao.getNoteEntityByUuid(payload.noteUuid)
                    if (parentNote == null) {
                        // Check if parent note was tombstoned
                        if (tombstoneDao.getTombstone(EntityType.NOTE.name, payload.noteUuid) != null) {
                            return
                        }
                        // Parent note not present
                        return
                    }

                    val existing = checklistItemDao.getByUuid(payload.uuid)
                    val now = System.currentTimeMillis()
                    val parentId = payload.parentUuid?.let { checklistItemDao.getByUuid(it)?.id }
                    if (existing == null) {
                        val item =
                            ChecklistItemEntity(
                                id = 0L,
                                uuid = payload.uuid,
                                noteId = parentNote.id,
                                ownerId = payload.ownerId,
                                text = payload.text,
                                checked = payload.checked,
                                sortOrder = payload.sortOrder,
                                parentId = parentId,
                                depth = payload.depth,
                                details = payload.details,
                            )
                        checklistItemDao.insert(item)
                        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, payload.uuid, "text", opHlc.toString(), now))
                        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, payload.uuid, "checked", opHlc.toString(), now))
                        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, payload.uuid, "sortOrder", opHlc.toString(), now))
                    } else {
                        val textMeta = conflictMetadataDao.getFieldMetadata(EntityType.CHECKLIST_ITEM.name, payload.uuid, "text")
                        val textHlc = textMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(parentNote.updatedAt, 0, "local")
                        val (targetText, targetDetails) =
                            if (opHlc > textHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, payload.uuid, "text", opHlc.toString(), now))
                                payload.text to payload.details
                            } else {
                                existing.text to existing.details
                            }

                        val checkedMeta = conflictMetadataDao.getFieldMetadata(EntityType.CHECKLIST_ITEM.name, payload.uuid, "checked")
                        val checkedHlc = checkedMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(parentNote.updatedAt, 0, "local")
                        val targetChecked =
                            if (opHlc > checkedHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, payload.uuid, "checked", opHlc.toString(), now))
                                payload.checked
                            } else {
                                existing.checked
                            }

                        val sortMeta = conflictMetadataDao.getFieldMetadata(EntityType.CHECKLIST_ITEM.name, payload.uuid, "sortOrder")
                        val sortHlc = sortMeta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(parentNote.updatedAt, 0, "local")
                        val (targetSort, targetDepth, targetParentId) =
                            if (opHlc > sortHlc) {
                                conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, payload.uuid, "sortOrder", opHlc.toString(), now))
                                Triple(payload.sortOrder, payload.depth, parentId)
                            } else {
                                Triple(existing.sortOrder, existing.depth, existing.parentId)
                            }

                        checklistItemDao.update(
                            existing.copy(
                                text = targetText,
                                details = targetDetails,
                                checked = targetChecked,
                                sortOrder = targetSort,
                                depth = targetDepth,
                                parentId = targetParentId,
                            ),
                        )
                    }
                }
                OperationType.DELETE -> {
                    val tombstone = tombstoneDao.getTombstone(EntityType.CHECKLIST_ITEM.name, op.entityId)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                    }

                    val existing = checklistItemDao.getByUuid(op.entityId)
                    if (existing != null) {
                        checklistItemDao.deleteById(existing.id)
                    }

                    val deletedAt =
                        op.payload?.let {
                            runCatching { json.decodeFromString<DeleteChangePayload>(it).deletedAt }.getOrNull()
                        } ?: System.currentTimeMillis()

                    tombstoneDao.insertOrUpdate(
                        SyncTombstoneEntity(
                            entityType = EntityType.CHECKLIST_ITEM.name,
                            entityId = op.entityId,
                            deletedAt = deletedAt,
                            hlcTimestamp = opHlc.toString(),
                            deviceId = opHlc.deviceId,
                        ),
                    )
                }
            }
        }

        private suspend fun handleAttachment(
            op: JournalOperationDto,
            opType: OperationType,
            opHlc: Hlc,
            journal: SyncJournalDto,
        ) {
            when (opType) {
                OperationType.UPSERT -> {
                    val payloadStr = op.payload ?: throw InvalidOperationPayloadException("Attachment UPSERT missing payload")
                    val payload =
                        try {
                            json.decodeFromString<AttachmentChangePayload>(payloadStr)
                        } catch (e: Exception) {
                            throw InvalidOperationPayloadException("Failed to parse AttachmentChangePayload", e)
                        }

                    // Security path traversal check
                    if (payload.uri.contains("..") || payload.displayName.contains("..")) {
                        throw MalformedPathException("Path traversal attempt detected in attachment payload: ${payload.displayName}")
                    }

                    val tombstone = tombstoneDao.getTombstone(EntityType.ATTACHMENT_METADATA.name, payload.uuid)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                        tombstoneDao.deleteTombstone(EntityType.ATTACHMENT_METADATA.name, payload.uuid)
                    }

                    val parentNote = noteDao.getNoteEntityByUuid(payload.noteUuid) ?: return
                    val existing = attachmentDao.getByUuid(payload.uuid)
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        val attachment =
                            NoteAttachmentEntity(
                                id = 0L,
                                uuid = payload.uuid,
                                noteId = parentNote.id,
                                ownerId = payload.ownerId,
                                uri = payload.uri,
                                displayName = payload.displayName,
                                mimeType = payload.mimeType,
                            )
                        attachmentDao.insert(attachment)
                        conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.ATTACHMENT_METADATA.name, payload.uuid, "metadata", opHlc.toString(), now))
                    } else {
                        val meta = conflictMetadataDao.getFieldMetadata(EntityType.ATTACHMENT_METADATA.name, payload.uuid, "metadata")
                        val metaHlc = meta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(parentNote.updatedAt, 0, "local")
                        if (opHlc > metaHlc) {
                            conflictMetadataDao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.ATTACHMENT_METADATA.name, payload.uuid, "metadata", opHlc.toString(), now))
                            attachmentDao.insert(
                                existing.copy(
                                    uri = payload.uri,
                                    displayName = payload.displayName,
                                    mimeType = payload.mimeType,
                                ),
                            )
                        }
                    }
                }
                OperationType.DELETE -> {
                    val tombstone = tombstoneDao.getTombstone(EntityType.ATTACHMENT_METADATA.name, op.entityId)
                    if (tombstone != null) {
                        val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                        if (opHlc <= tombstoneHlc) return
                    }

                    val existing = attachmentDao.getByUuid(op.entityId)
                    if (existing != null) {
                        attachmentDao.deleteById(existing.id)
                    }

                    val deletedAt =
                        op.payload?.let {
                            runCatching { json.decodeFromString<DeleteChangePayload>(it).deletedAt }.getOrNull()
                        } ?: System.currentTimeMillis()

                    tombstoneDao.insertOrUpdate(
                        SyncTombstoneEntity(
                            entityType = EntityType.ATTACHMENT_METADATA.name,
                            entityId = op.entityId,
                            deletedAt = deletedAt,
                            hlcTimestamp = opHlc.toString(),
                            deviceId = opHlc.deviceId,
                        ),
                    )
                }
            }
        }

        private suspend fun handlePreference(
            op: JournalOperationDto,
            opType: OperationType,
            opHlc: Hlc,
        ) {
            if (opType != OperationType.UPSERT) return
            val payloadStr = op.payload ?: throw InvalidOperationPayloadException("Preference UPSERT missing payload")
            val payload =
                try {
                    json.decodeFromString<PreferenceChangePayload>(payloadStr)
                } catch (e: Exception) {
                    throw InvalidOperationPayloadException("Failed to parse PreferenceChangePayload", e)
                }

            // Exclude device-specific preferences
            val deviceSpecificKeys =
                setOf(
                    "device_biometrics",
                    "device_biometric_enabled",
                    "device_notification_permission",
                    "device_local_path",
                    "device_cache_dir",
                    "dev_mode_options",
                )
            if (deviceSpecificKeys.contains(payload.key)) return

            val meta = conflictMetadataDao.getFieldMetadata(EntityType.USER_PREFERENCE.name, payload.key, "settings")
            val metaHlc = meta?.let { Hlc.parse(it.hlcTimestamp) } ?: Hlc(0L, 0, "local")
            if (opHlc > metaHlc) {
                conflictMetadataDao.insertOrUpdate(
                    SyncConflictMetadataEntity(EntityType.USER_PREFERENCE.name, payload.key, "settings", opHlc.toString(), System.currentTimeMillis()),
                )
            }
        }

        private fun encryptConflictPayload(
            raw: String?,
            conflictId: String,
        ): String? {
            if (raw == null) return null
            val mek = mekManager?.getActiveMasterKey() ?: return raw
            return try {
                val aad = "NOTAS_E1:conflict:$conflictId".toByteArray(Charsets.UTF_8)
                val encrypted =
                    EncryptedContainer.encrypt(
                        plaintext = raw.toByteArray(Charsets.UTF_8),
                        key = mek,
                        aad = aad,
                    )
                "NOTAS_E1:" + Base64.getEncoder().encodeToString(encrypted)
            } catch (_: Exception) {
                raw
            }
        }

        private fun computeConflictId(
            entityType: String,
            entityId: String,
            fieldName: String,
            winningHlc: Hlc,
            losingHlc: Hlc,
            winningValue: String?,
            losingValue: String?,
        ): String {
            val side1 = winningHlc to (winningValue ?: "")
            val side2 = losingHlc to (losingValue ?: "")

            val (first, second) =
                if (side1.first < side2.first) {
                    side1 to side2
                } else if (side1.first > side2.first) {
                    side2 to side1
                } else {
                    // Tie-breaker by value
                    if (side1.second <= side2.second) side1 to side2 else side2 to side1
                }

            val raw = "$entityType:$entityId:$fieldName:${first.first}:${first.second.length}:${first.second}:${second.first}:${second.second.length}:${second.second}"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private suspend fun recordConflict(
            entityType: String,
            entityId: String,
            fieldName: String,
            winningValue: String?,
            losingValue: String?,
            winningHlc: Hlc,
            losingHlc: Hlc,
            encryptPayload: Boolean = false,
        ) {
            val conflictId =
                computeConflictId(
                    entityType = entityType,
                    entityId = entityId,
                    fieldName = fieldName,
                    winningHlc = winningHlc,
                    losingHlc = losingHlc,
                    winningValue = winningValue,
                    losingValue = losingValue,
                )
            val storedWinning = if (encryptPayload) encryptConflictPayload(winningValue, conflictId) else winningValue
            val storedLosing = if (encryptPayload) encryptConflictPayload(losingValue, conflictId) else losingValue

            val record =
                SyncConflictRecordEntity(
                    conflictId = conflictId,
                    entityType = entityType,
                    entityId = entityId,
                    fieldName = fieldName,
                    winningValue = storedWinning,
                    losingValue = storedLosing,
                    winningHlc = winningHlc.toString(),
                    losingHlc = losingHlc.toString(),
                    winningDeviceId = winningHlc.deviceId,
                    losingDeviceId = losingHlc.deviceId,
                    detectedAt = System.currentTimeMillis(),
                    resolved = false,
                )
            conflictRecordDao.insert(record)
        }
    }

private fun NoteEntity.toChangePayload(categoryUuid: String? = null): NoteChangePayload =
    NoteChangePayload(
        uuid = uuid,
        ownerId = ownerId,
        kind = kind.name,
        title = title,
        body = body,
        colorIndex = colorIndex,
        trashed = trashed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        reminderAt = reminderAt,
        endAt = endAt,
        isAllDay = isAllDay,
        importance = importance.name,
        visibility = visibility.name,
        pictureUri = pictureUri,
        pictureHeroFraming = pictureHeroFraming,
        locked = locked,
        iconKey = iconKey,
        categoryUuid = categoryUuid,
        archived = archived,
        trashedAt = trashedAt,
        completedAt = completedAt,
        pinnedAt = pinnedAt,
    )
