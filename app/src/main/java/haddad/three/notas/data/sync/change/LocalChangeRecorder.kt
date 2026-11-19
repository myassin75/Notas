package haddad.three.notas.data.sync.change

import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataDao
import haddad.three.notas.data.sync.conflict.SyncConflictMetadataEntity
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.hlc.HlcClock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for recording transactional local changes into [PendingChangeDao].
 */
interface LocalChangeRecorder {
    suspend fun recordNoteUpsert(
        note: NoteEntity,
        categoryUuid: String? = null,
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordNoteDelete(
        noteUuid: String,
        deletedAt: Long = System.currentTimeMillis(),
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordCategoryUpsert(
        shoulder: ShoulderEntity,
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordCategoryDelete(
        categoryUuid: String,
        deletedAt: Long = System.currentTimeMillis(),
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordChecklistItemUpsert(
        item: ChecklistItemEntity,
        noteUuid: String,
        parentUuid: String? = null,
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordChecklistItemDelete(
        itemUuid: String,
        noteUuid: String,
        deletedAt: Long = System.currentTimeMillis(),
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordAttachmentUpsert(
        attachment: NoteAttachmentEntity,
        noteUuid: String,
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordAttachmentDelete(
        attachmentUuid: String,
        noteUuid: String,
        deletedAt: Long = System.currentTimeMillis(),
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?

    suspend fun recordPreferenceUpsert(
        key: String,
        settingsJson: String,
        origin: OperationOrigin = OperationOrigin.LOCAL,
    ): String?
}

@Singleton
class DefaultLocalChangeRecorder
    @Inject
    constructor(
        private val pendingChangeDao: PendingChangeDao,
        private val deviceIdProvider: suspend () -> String,
        private val clock: () -> Long = System::currentTimeMillis,
        private val hlcClock: HlcClock? = null,
        private val conflictMetadataDao: SyncConflictMetadataDao? = null,
        private val onMutationRecorded: ((origin: OperationOrigin) -> Unit)? = null,
    ) : LocalChangeRecorder {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        private fun notifyMutation(origin: OperationOrigin) {
            if (origin == OperationOrigin.LOCAL) {
                onMutationRecorded?.invoke(origin)
            }
        }

        private suspend fun nextHlc(): Hlc =
            hlcClock?.generateNext() ?: Hlc(clock(), 0, deviceIdProvider())

        override suspend fun recordNoteUpsert(
            note: NoteEntity,
            categoryUuid: String?,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                NoteChangePayload(
                    uuid = note.uuid,
                    ownerId = note.ownerId,
                    kind = note.kind.name,
                    title = note.title,
                    body = note.body,
                    colorIndex = note.colorIndex,
                    trashed = note.trashed,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    reminderAt = note.reminderAt,
                    endAt = note.endAt,
                    isAllDay = note.isAllDay,
                    importance = note.importance.name,
                    visibility = note.visibility.name,
                    pictureUri = note.pictureUri,
                    pictureHeroFraming = note.pictureHeroFraming,
                    locked = note.locked,
                    iconKey = note.iconKey,
                    categoryUuid = categoryUuid,
                    archived = note.archived,
                    trashedAt = note.trashedAt,
                    completedAt = note.completedAt,
                    pinnedAt = note.pinnedAt,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.NOTE.name,
                    entityId = note.uuid,
                    operationType = OperationType.UPSERT.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)

            conflictMetadataDao?.let { dao ->
                val now = clock()
                val hlcStr = hlc.toString()
                val fields = listOf("title", "body", "colorIndex", "trashed", "archived", "pinnedAt", "completedAt", "categoryUuid", "reminderAt")
                for (f in fields) {
                    dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.NOTE.name, note.uuid, f, hlcStr, now))
                }
            }

            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordNoteDelete(
            noteUuid: String,
            deletedAt: Long,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                DeleteChangePayload(
                    uuid = noteUuid,
                    deletedAt = deletedAt,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.NOTE.name,
                    entityId = noteUuid,
                    operationType = OperationType.DELETE.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)
            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordCategoryUpsert(
            shoulder: ShoulderEntity,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                CategoryChangePayload(
                    uuid = shoulder.uuid,
                    ownerId = shoulder.ownerId,
                    name = shoulder.name,
                    normalizedName = shoulder.normalizedName,
                    colorHex = shoulder.colorHex,
                    createdAt = shoulder.createdAt,
                    updatedAt = shoulder.updatedAt,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.CATEGORY.name,
                    entityId = shoulder.uuid,
                    operationType = OperationType.UPSERT.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)

            conflictMetadataDao?.let { dao ->
                val now = clock()
                val hlcStr = hlc.toString()
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CATEGORY.name, shoulder.uuid, "name", hlcStr, now))
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CATEGORY.name, shoulder.uuid, "colorHex", hlcStr, now))
            }

            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordCategoryDelete(
            categoryUuid: String,
            deletedAt: Long,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                DeleteChangePayload(
                    uuid = categoryUuid,
                    deletedAt = deletedAt,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.CATEGORY.name,
                    entityId = categoryUuid,
                    operationType = OperationType.DELETE.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)
            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordChecklistItemUpsert(
            item: ChecklistItemEntity,
            noteUuid: String,
            parentUuid: String?,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                ChecklistItemChangePayload(
                    uuid = item.uuid,
                    noteUuid = noteUuid,
                    ownerId = item.ownerId,
                    text = item.text,
                    checked = item.checked,
                    sortOrder = item.sortOrder,
                    parentUuid = parentUuid,
                    depth = item.depth,
                    details = item.details,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.CHECKLIST_ITEM.name,
                    entityId = item.uuid,
                    operationType = OperationType.UPSERT.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)

            conflictMetadataDao?.let { dao ->
                val now = clock()
                val hlcStr = hlc.toString()
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, item.uuid, "text", hlcStr, now))
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, item.uuid, "checked", hlcStr, now))
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.CHECKLIST_ITEM.name, item.uuid, "sortOrder", hlcStr, now))
            }

            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordChecklistItemDelete(
            itemUuid: String,
            noteUuid: String,
            deletedAt: Long,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                DeleteChangePayload(
                    uuid = itemUuid,
                    deletedAt = deletedAt,
                    parentUuid = noteUuid,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.CHECKLIST_ITEM.name,
                    entityId = itemUuid,
                    operationType = OperationType.DELETE.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)
            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordAttachmentUpsert(
            attachment: NoteAttachmentEntity,
            noteUuid: String,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                AttachmentChangePayload(
                    uuid = attachment.uuid,
                    noteUuid = noteUuid,
                    ownerId = attachment.ownerId,
                    uri = attachment.uri,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.ATTACHMENT_METADATA.name,
                    entityId = attachment.uuid,
                    operationType = OperationType.UPSERT.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)

            conflictMetadataDao?.let { dao ->
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.ATTACHMENT_METADATA.name, attachment.uuid, "metadata", hlc.toString(), clock()))
            }

            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordAttachmentDelete(
            attachmentUuid: String,
            noteUuid: String,
            deletedAt: Long,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val payload =
                DeleteChangePayload(
                    uuid = attachmentUuid,
                    deletedAt = deletedAt,
                    parentUuid = noteUuid,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.ATTACHMENT_METADATA.name,
                    entityId = attachmentUuid,
                    operationType = OperationType.DELETE.name,
                    deviceId = deviceIdProvider(),
                    createdAt = clock(),
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)
            notifyMutation(origin)
            return changeId
        }

        override suspend fun recordPreferenceUpsert(
            key: String,
            settingsJson: String,
            origin: OperationOrigin,
        ): String? {
            if (shouldSuppress(origin)) return null

            val now = clock()
            val payload =
                PreferenceChangePayload(
                    key = key,
                    settingsJson = settingsJson,
                    updatedAt = now,
                )

            val hlc = nextHlc()
            val changeId = UUID.randomUUID().toString()
            val entity =
                PendingChangeEntity(
                    changeId = changeId,
                    entityType = EntityType.USER_PREFERENCE.name,
                    entityId = key,
                    operationType = OperationType.UPSERT.name,
                    deviceId = deviceIdProvider(),
                    createdAt = now,
                    schemaVersion = 1,
                    payload = json.encodeToString(payload),
                    hlcTimestamp = hlc.toString(),
                    hlcCounter = hlc.logicalCounter,
                    hlcNodeId = hlc.deviceId,
                    status = PendingChangeStatus.PENDING.name,
                )

            pendingChangeDao.insert(entity)

            conflictMetadataDao?.let { dao ->
                dao.insertOrUpdate(SyncConflictMetadataEntity(EntityType.USER_PREFERENCE.name, key, "settings", hlc.toString(), now))
            }

            notifyMutation(origin)
            return changeId
        }

        private suspend fun shouldSuppress(origin: OperationOrigin): Boolean {
            if (origin == OperationOrigin.REMOTE) return true
            return SyncContext.isSuppressed()
        }
    }
