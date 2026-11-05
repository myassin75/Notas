package haddad.three.notas.data

import androidx.room.withTransaction
import haddad.three.notas.data.sync.change.LocalChangeRecorder
import haddad.three.notas.reminders.ReminderScheduler

/**
 * The create / update half of [NoteRepository]: turns a screen's title, body, colour, checklist
 * rows and [NoteOptions] into stored rows, then hands the follow-up work to the checklist,
 * reminder and media collaborators. Split out so the four near-identical entity-building paths
 * (note, simple list, hierarchical list, and their update counterparts) sit together.
 */
internal class NoteContentWriter(
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val checklistWriter: NoteChecklistWriter,
    private val mediaMaintenance: NoteMediaMaintenance,
    private val reminderCoordinator: NoteReminderCoordinator,
    private val shoulderRepository: ShoulderRepository?,
    private val scheduler: ReminderScheduler?,
    private val clock: () -> Long,
    private val database: RememberDatabase?,
    private val ownerIdProvider: () -> String? = { null },
    private val changeRecorder: LocalChangeRecorder? = null,
) {
    suspend fun createNote(
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions,
    ): Long {
        val now = clock()
        val currentOwnerId = ownerIdProvider()
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(null, options)
        val unsyncedNote =
            NoteEntity(
                kind = NoteKind.NOTE,
                ownerId = currentOwnerId,
                title = title,
                body = body,
                colorIndex = colorIndex,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = options.reminderAt,
                endAt = options.endAt,
                isAllDay = options.isAllDay,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                actionsText = actionsSearchText(options.actions),
                shoulderId = options.shoulderId,
                recurrence = options.recurrence?.sanitized(),
                reminders = resolvedReminders,
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val applyInsert: suspend () -> Long = {
            val noteId = noteDao.insert(noteEntity)
            val catUuid = options.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
            changeRecorder?.recordNoteUpsert(noteEntity.copy(id = noteId), catUuid)
            noteId
        }
        val noteId = if (database != null) database.withTransaction { applyInsert() } else applyInsert()
        if (noteEntity.reminderAt != null) {
            val createdNote = noteDao.get(noteId)?.note
            if (createdNote != null) {
                scheduler?.scheduleOrShow(createdNote, emptyList())
            }
        }
        return noteId
    }

    suspend fun createList(
        title: String,
        colorIndex: Int,
        items: List<String>,
        options: NoteOptions,
    ): Long {
        val now = clock()
        val currentOwnerId = ownerIdProvider()
        val validItems = items.map { it.trim() }.filter { it.isNotEmpty() }
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(null, options)
        val unsyncedNote =
            NoteEntity(
                kind = NoteKind.LIST,
                ownerId = currentOwnerId,
                title = title,
                body = "",
                checklistText = checklistSearchText(validItems),
                colorIndex = colorIndex,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = options.reminderAt,
                endAt = options.endAt,
                isAllDay = options.isAllDay,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                actionsText = actionsSearchText(options.actions),
                shoulderId = options.shoulderId,
                recurrence = options.recurrence?.sanitized(),
                reminders = resolvedReminders,
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val applyInsert: suspend () -> Long = {
            val id = noteDao.insert(noteEntity)
            val catUuid = options.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
            changeRecorder?.recordNoteUpsert(noteEntity.copy(id = id), catUuid)
            if (validItems.isNotEmpty()) {
                val checklistEntities = validItems.mapIndexed { index, text ->
                    ChecklistItemEntity(
                        noteId = id,
                        ownerId = currentOwnerId,
                        text = text,
                        details = "",
                        checked = false,
                        sortOrder = (index + 1).toDouble(),
                        parentId = null,
                        depth = 0,
                    )
                }
                itemDao.insertAll(checklistEntities)
                checklistEntities.forEach { itemEntity ->
                    changeRecorder?.recordChecklistItemUpsert(itemEntity, noteEntity.uuid)
                }
            }
            id
        }
        val id = if (database != null) database.withTransaction { applyInsert() } else applyInsert()
        if (noteEntity.reminderAt != null) {
            val createdNoteWithItems = noteDao.get(id)
            if (createdNoteWithItems != null) {
                scheduler?.scheduleOrShow(createdNoteWithItems.note, createdNoteWithItems.items)
            }
        }
        return id
    }

    suspend fun createListWithItems(
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions,
    ): Long {
        val now = clock()
        val currentOwnerId = ownerIdProvider()
        val validItems = items.filter { item -> item.text.isNotBlank() || item.details.isNotBlank() }
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(null, options)
        val unsyncedNote =
            NoteEntity(
                kind = NoteKind.LIST,
                ownerId = currentOwnerId,
                title = title,
                body = "",
                checklistText =
                    checklistSearchText(
                        validItems
                            .sortedBy { item -> item.sortOrder }
                            .flatMap { item -> listOf(item.text, item.details) },
                    ),
                colorIndex = colorIndex,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = options.reminderAt,
                endAt = options.endAt,
                isAllDay = options.isAllDay,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                actionsText = actionsSearchText(options.actions),
                shoulderId = options.shoulderId,
                recurrence = options.recurrence?.sanitized(),
                reminders = resolvedReminders,
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val applyInsert: suspend () -> Long = {
            val id = noteDao.insert(noteEntity)
            val catUuid = options.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
            changeRecorder?.recordNoteUpsert(noteEntity.copy(id = id), catUuid)
            checklistWriter.insertHierarchy(noteId = id, items = validItems, ownerId = currentOwnerId, noteUuid = noteEntity.uuid)
            id
        }
        val id = if (database != null) database.withTransaction { applyInsert() } else applyInsert()
        if (noteEntity.reminderAt != null) {
            val createdNoteWithItems = noteDao.get(id)
            if (createdNoteWithItems != null) {
                scheduler?.scheduleOrShow(createdNoteWithItems.note, createdNoteWithItems.items)
            }
        }
        return id
    }

    suspend fun updateNote(
        id: Long,
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions,
    ) {
        val existing = noteDao.get(id)?.note ?: return
        val oldPictureUri = existing.pictureUri
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(existing, options)
        val unsyncedNote =
            existing.copy(
                title = title,
                body = body,
                colorIndex = colorIndex,
                updatedAt = clock(),
                reminderAt = options.reminderAt,
                endAt = options.endAt,
                isAllDay = options.isAllDay,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                actionsText = actionsSearchText(options.actions),
                shoulderId = options.shoulderId,
                recurrence = options.recurrence?.sanitized(),
                reminders = resolvedReminders,
                completedAt =
                    if (existing.completedAt != null && (options.reminderAt != null || resolvedReminders.isNotEmpty())) {
                        null
                    } else {
                        existing.completedAt
                    },
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val applyUpdate: suspend () -> Unit = {
            noteDao.update(noteEntity)
            val catUuid = options.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
            changeRecorder?.recordNoteUpsert(noteEntity, catUuid)
        }
        if (database != null) database.withTransaction { applyUpdate() } else applyUpdate()
        reminderCoordinator.rescheduleReminder(id, noteEntity.reminderAt)
        reminderCoordinator.refreshNotificationIfActive(id)
        if (oldPictureUri != null && oldPictureUri != options.pictureUri) {
            mediaMaintenance.cleanupUnreferencedMedia(listOf(oldPictureUri))
        }
    }

    suspend fun updateList(
        id: Long,
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions,
    ) {
        val applyUpdates: suspend () -> Boolean = {
            val existing = noteDao.get(id)?.note
            if (existing == null) {
                false
            } else {
                val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(existing, options)
                val unsyncedNote =
                    existing.copy(
                        title = title,
                        colorIndex = colorIndex,
                        checklistText =
                            checklistSearchText(
                                items
                                    .sortedBy { item -> item.sortOrder }
                                    .flatMap { item -> listOf(item.text, item.details) },
                            ),
                        updatedAt = clock(),
                        reminderAt = options.reminderAt,
                        importance = options.importance,
                        visibility = options.visibility,
                        pictureUri = options.pictureUri,
                        pictureHeroFraming = options.pictureHeroFraming,
                        locked = options.locked,
                        iconKey = options.iconKey,
                        actions = options.actions,
                        actionsText = actionsSearchText(options.actions),
                        shoulderId = options.shoulderId,
                        recurrence = options.recurrence?.sanitized(),
                        reminders = resolvedReminders,
                        completedAt =
                            if (existing.completedAt != null && (options.reminderAt != null || resolvedReminders.isNotEmpty())) {
                                null
                            } else {
                                existing.completedAt
                            },
                    )
                val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
                noteDao.update(noteEntity)
                val catUuid = options.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
                changeRecorder?.recordNoteUpsert(noteEntity, catUuid)
                checklistWriter.syncHierarchy(noteId = id, items = items, ownerId = existing.ownerId, noteUuid = noteEntity.uuid)
                true
            }
        }
        val existingNote = noteDao.get(id)?.note
        val oldPictureUri = existingNote?.pictureUri

        val didUpdate =
            if (database != null) {
                database.withTransaction { applyUpdates() }
            } else {
                applyUpdates()
            }
        if (didUpdate) {
            val noteEntity = noteDao.get(id)?.note
            reminderCoordinator.rescheduleReminder(id, noteEntity?.reminderAt)
            reminderCoordinator.refreshNotificationIfActive(id)
            if (oldPictureUri != null && oldPictureUri != options.pictureUri) {
                mediaMaintenance.cleanupUnreferencedMedia(listOf(oldPictureUri))
            }
        }
    }
}
