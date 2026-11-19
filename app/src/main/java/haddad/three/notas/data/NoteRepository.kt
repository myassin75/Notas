package haddad.three.notas.data

import androidx.room.withTransaction
import haddad.three.notas.di.DefaultDispatcher
import haddad.three.notas.di.IoDispatcher
import haddad.three.notas.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Locale

data class NoteOptions(
    val reminderAt: Long? = null,
    val endAt: Long? = null,
    val isAllDay: Boolean = false,
    val importance: Importance = Importance.DEFAULT,
    val visibility: Visibility = Visibility.DEFAULT,
    val pictureUri: String? = null,
    val pictureHeroFraming: String? = null,
    val locked: Boolean = false,
    val iconKey: String? = null,
    val actions: List<NoteAction> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val reminders: List<NoteReminder> = emptyList(),
    val shoulderId: Long? = null,
)

/**
 * Save-side representation of a checklist row. Callers use [localKey] to express parent/child
 * linkage between rows that may or may not already exist in the database: persisted rows carry
 * their Room id as [localKey]; drafts assign a unique negative value. The repository remaps these
 * keys to the real auto-generated ids after insert so [parentLocalKey] pointers survive.
 */
data class PersistableChecklistItem(
    val localKey: Long,
    val text: String,
    val checked: Boolean,
    val sortOrder: Double,
    val parentLocalKey: Long? = null,
    val depth: Int = 0,
    val details: String = "",
    val uuid: String? = null,
)

/**
 * Pre-completion snapshot used by Undo to fully reverse a mark-done — including for
 * recurring notes whose [reminderAt]/[recurrence] would otherwise be advanced or
 * consumed by [NoteRepository.markCompleted]. Stored in the snackbar's pending-action
 * record so a subsequent tap on Undo restores the row's exact prior state.
 */
data class NoteCompletionSnapshot(
    val reminderAt: Long?,
    val recurrence: RecurrenceRule?,
    val reminders: List<NoteReminder> = emptyList(),
)

/**
 * Single entry point the app uses to read and write notes. The heavy lifting is delegated to
 * focused collaborators created below - content writes, checklist rows, reminders, bulk
 * selection actions and media - so this class stays the stable, public-facing surface while each
 * responsibility keeps its own file.
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val attachmentDao: AttachmentDao,
    private val scheduler: ReminderScheduler? = null,
    val shoulderRepository: ShoulderRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val database: RememberDatabase? = null,
    private val appMediaStorage: AppMediaStorage? = null,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val tombstoneRecorder: haddad.three.notas.data.sync.SyncTombstoneRecorder? = null,
    private val authRepository: haddad.three.notas.data.auth.AuthRepository? = null,
    private val ownerIdProvider: () -> String? = { authRepository?.currentUser?.uid },
    private val changeRecorder: haddad.three.notas.data.sync.change.LocalChangeRecorder? = null,
) {
    // NoteRefreshObserver owns widget and summary refreshes after committed database changes.
    // Per-note alarm scheduling/cancellation stays synchronous here to preserve ordering.

    private val checklistWriter =
        NoteChecklistWriter(
            itemDao = itemDao,
            changeRecorder = changeRecorder,
            noteUuidResolver = { id -> noteDao.get(id)?.note?.uuid },
        )

    private val mediaMaintenance =
        NoteMediaMaintenance(
            noteDao = noteDao,
            attachmentDao = attachmentDao,
            appMediaStorage = appMediaStorage,
            clock = clock,
            database = database,
            changeRecorder = changeRecorder,
        )

    private val reminderCoordinator =
        NoteReminderCoordinator(
            noteDao = noteDao,
            scheduler = scheduler,
            clock = clock,
            database = database,
            changeRecorder = changeRecorder,
        )

    private val contentWriter =
        NoteContentWriter(
            noteDao = noteDao,
            itemDao = itemDao,
            checklistWriter = checklistWriter,
            mediaMaintenance = mediaMaintenance,
            reminderCoordinator = reminderCoordinator,
            shoulderRepository = shoulderRepository,
            scheduler = scheduler,
            clock = clock,
            database = database,
            ownerIdProvider = ownerIdProvider,
            changeRecorder = changeRecorder,
        )

    private val bulkOperations =
        NoteBulkOperations(
            noteDao = noteDao,
            scheduler = scheduler,
            clock = clock,
            database = database,
            reminderCoordinator = reminderCoordinator,
            mediaMaintenance = mediaMaintenance,
            tombstoneRecorder = tombstoneRecorder,
            changeRecorder = changeRecorder,
        )

    private fun matchesOwner(
        noteOwnerId: String?,
        filterOwnerId: String?,
    ): Boolean = (filterOwnerId == null && noteOwnerId == null) || (filterOwnerId != null && noteOwnerId == filterOwnerId)

    fun observeActive(ownerId: String? = ownerIdProvider()): Flow<List<NoteWithItems>> = noteDao.observeActive(ownerId).flowOn(ioDispatcher)

    fun observeScheduled(ownerId: String? = ownerIdProvider()): Flow<List<NoteWithItems>> = noteDao.observeScheduled(ownerId).flowOn(ioDispatcher)

    fun observeTrashed(ownerId: String? = ownerIdProvider()): Flow<List<NoteWithItems>> = noteDao.observeTrashed(ownerId).flowOn(ioDispatcher)

    fun observeArchived(ownerId: String? = ownerIdProvider()): Flow<List<NoteWithItems>> = noteDao.observeArchived(ownerId).flowOn(ioDispatcher)

    fun observe(id: Long): Flow<NoteWithItems?> = noteDao.observe(id).flowOn(ioDispatcher)

    suspend fun get(id: Long): NoteWithItems? = noteDao.get(id)

    suspend fun snapshotAllNotes(): List<NoteWithItems> = noteDao.allNotes()

    /**
     * FTS4 search across active notes. Empty / whitespace-only [query] returns an empty flow
     * (callers should fall back to [observeActive] instead). The query is tokenised on
     * whitespace, each term is escaped and turned into a prefix match (`term*`), so typing
     * "buy mil" will match "buy milk" as soon as the user has typed the third letter.
     */
    fun searchNotes(
        query: String,
        ownerId: String? = ownerIdProvider(),
    ): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            noteDao.searchNotes(fts, ownerId).flowOn(ioDispatcher)
        }.flowOn(defaultDispatcher)
    }

    /** Same FTS prefix match as [searchNotes], but scoped to archived notes only. */
    fun searchArchivedNotes(
        query: String,
        ownerId: String? = ownerIdProvider(),
    ): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            noteDao.searchArchived(fts, ownerId).flowOn(ioDispatcher)
        }.flowOn(defaultDispatcher)
    }

    /** Same FTS prefix match as [searchNotes], but scoped to trashed notes only. */
    fun searchTrashedNotes(
        query: String,
        ownerId: String? = ownerIdProvider(),
    ): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            noteDao.searchTrashed(fts, ownerId).flowOn(ioDispatcher)
        }.flowOn(defaultDispatcher)
    }

    fun resolveUpdatedReminders(
        existingNote: NoteEntity?,
        options: NoteOptions,
    ): List<NoteReminder> = reminderCoordinator.resolveUpdatedReminders(existingNote, options)

    suspend fun createNote(
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions = NoteOptions(),
    ): Long = contentWriter.createNote(title, body, colorIndex, options)

    suspend fun createList(
        title: String,
        colorIndex: Int,
        items: List<String>,
        options: NoteOptions = NoteOptions(),
    ): Long = contentWriter.createList(title, colorIndex, items, options)

    suspend fun createListWithItems(
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions = NoteOptions(),
    ): Long = contentWriter.createListWithItems(title, colorIndex, items, options)

    suspend fun updateNote(
        id: Long,
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions,
    ) = contentWriter.updateNote(id, title, body, colorIndex, options)

    suspend fun updateList(
        id: Long,
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions,
    ) = contentWriter.updateList(id, title, colorIndex, items, options)

    /**
     * Pins or unpins [id]. Pinning only affects placement on Home (the top "Pinned" section).
     *
     * Deliberately does not touch [NoteEntity.updatedAt]: pinning is a view concern, and
     * bumping the modified timestamp would silently reshuffle the list under a
     * "sort by last modified" user, which is exactly the confusion pinning is meant to avoid.
     */
    suspend fun setPinned(
        id: Long,
        pinned: Boolean,
    ) {
        val row = noteDao.get(id) ?: return
        if (row.note.pinned == pinned) return
        val updated = row.note.copy(pinnedAt = if (pinned) clock() else null)
        val block: suspend () -> Unit = {
            noteDao.update(updated)
            val catUuid = updated.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
            changeRecorder?.recordNoteUpsert(updated, catUuid)
        }
        if (database != null) database.withTransaction { block() } else block()
    }

    suspend fun moveToTrash(id: Long) {
        val now = clock()
        val block: suspend () -> Unit = {
            noteDao.setTrashed(id, true, now)
            noteDao.get(id)?.note?.let { trashedNote ->
                val catUuid = trashedNote.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
                changeRecorder?.recordNoteUpsert(trashedNote, catUuid)
            }
        }
        if (database != null) database.withTransaction { block() } else block()
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
    }

    suspend fun snoozeSoonestReminder(
        noteId: Long,
        snoozedUntil: Long,
    ): Boolean {
        val noteWithItems = noteDao.get(noteId) ?: return false
        val note = noteWithItems.note
        if (note.trashed || note.archived || note.completedAt != null) return false
        val activeReminders = note.getActiveReminders()
        val soonestReminder = activeReminders.minByOrNull { reminder -> reminder.reminderAt }
        val updatedReminders =
            if (soonestReminder != null) {
                activeReminders.map { reminder ->
                    if (reminder == soonestReminder) {
                        reminder.copy(
                            reminderAt = snoozedUntil,
                            originalReminderAt = reminder.originalReminderAt ?: reminder.reminderAt,
                        )
                    } else {
                        reminder
                    }
                }
            } else {
                listOf(
                    NoteReminder(
                        reminderAt = snoozedUntil,
                        recurrence = note.recurrence,
                    ),
                )
            }
        val options =
            NoteOptions(
                reminderAt = snoozedUntil,
                importance = note.importance,
                visibility = note.visibility,
                pictureUri = note.pictureUri,
                pictureHeroFraming = note.pictureHeroFraming,
                locked = note.locked,
                iconKey = note.iconKey,
                actions = note.actions,
                recurrence = note.recurrence,
                reminders = updatedReminders,
            )
        if (note.kind == NoteKind.NOTE) {
            updateNote(note.id, note.title, note.body, note.colorIndex, options)
        } else {
            val persistableItems =
                noteWithItems.items.map { item ->
                    PersistableChecklistItem(
                        localKey = item.id,
                        text = item.text,
                        details = item.details,
                        checked = item.checked,
                        sortOrder = item.sortOrder,
                        parentLocalKey = item.parentId,
                        depth = item.depth,
                    )
                }
            updateList(note.id, note.title, note.colorIndex, persistableItems, options)
        }
        return true
    }

    suspend fun moveAllArchivedToTrash(ownerId: String? = ownerIdProvider()) {
        val archivedIds = noteDao.archivedNoteIds(ownerId)
        if (archivedIds.isEmpty()) return
        val now = clock()
        noteDao.moveAllArchivedToTrash(ownerId, now)
        archivedIds.forEach { archivedId ->
            scheduler?.cancel(archivedId)
            scheduler?.cancelNotification(archivedId)
        }
    }

    suspend fun restoreFromTrash(id: Long) {
        val now = clock()
        val block: suspend () -> Unit = {
            noteDao.setTrashed(id, false, now)
            noteDao.get(id)?.note?.let { restoredNote ->
                val catUuid = restoredNote.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
                changeRecorder?.recordNoteUpsert(restoredNote, catUuid)
            }
        }
        if (database != null) database.withTransaction { block() } else block()
        val noteWithItems = noteDao.get(id)
        if (noteWithItems != null) {
            val restoredNote = noteWithItems.note
            if (restoredNote.reminderAt != null) {
                scheduler?.scheduleOrShow(restoredNote, noteWithItems.items)
            }
        }
    }

    /**
     * Move to the archive shelf. Mirrors [moveToTrash] for reminder-cancel behaviour:
     * archived notes are considered "put away" so their alarms stop firing until unarchived.
     */
    suspend fun archiveNote(id: Long) {
        val now = clock()
        val block: suspend () -> Unit = {
            noteDao.setArchived(id, true, now)
            noteDao.get(id)?.note?.let { archivedNote ->
                val catUuid = archivedNote.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
                changeRecorder?.recordNoteUpsert(archivedNote, catUuid)
            }
        }
        if (database != null) database.withTransaction { block() } else block()
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
    }

    suspend fun unarchiveNote(id: Long) {
        val now = clock()
        val block: suspend () -> Unit = {
            noteDao.setArchived(id, false, now)
            noteDao.get(id)?.note?.let { unarchivedNote ->
                val catUuid = unarchivedNote.shoulderId?.let { sId -> shoulderRepository?.getShoulderById(sId)?.uuid }
                changeRecorder?.recordNoteUpsert(unarchivedNote, catUuid)
            }
        }
        if (database != null) database.withTransaction { block() } else block()
        val noteWithItems = noteDao.get(id)
        if (noteWithItems != null) {
            val unarchivedNote = noteWithItems.note
            if (unarchivedNote.reminderAt != null) {
                scheduler?.scheduleOrShow(unarchivedNote, noteWithItems.items)
            }
        }
    }

    suspend fun deleteForever(id: Long) {
        val deletedNote = noteDao.get(id)
        val block: suspend () -> Unit = {
            if (deletedNote != null) {
                tombstoneRecorder?.recordNoteDeleted(deletedNote)
                changeRecorder?.recordNoteDelete(deletedNote.note.uuid)
                deletedNote.items.forEach { item ->
                    changeRecorder?.recordChecklistItemDelete(item.uuid, deletedNote.note.uuid)
                }
                deletedNote.attachments.forEach { att ->
                    changeRecorder?.recordAttachmentDelete(att.uuid, deletedNote.note.uuid)
                }
            }
            noteDao.deleteById(id)
        }
        if (database != null) database.withTransaction { block() } else block()
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        mediaMaintenance.cleanupUnreferencedMedia(deletedNote?.mediaUris().orEmpty())
    }

    suspend fun emptyTrash(ownerId: String? = ownerIdProvider()) {
        val deletedNotes = noteDao.trashedNoteIds(ownerId).mapNotNull { trashedId -> noteDao.get(trashedId) }
        val block: suspend () -> Unit = {
            tombstoneRecorder?.recordNotesDeleted(deletedNotes)
            deletedNotes.forEach { deletedNote ->
                changeRecorder?.recordNoteDelete(deletedNote.note.uuid)
                deletedNote.items.forEach { item ->
                    changeRecorder?.recordChecklistItemDelete(item.uuid, deletedNote.note.uuid)
                }
                deletedNote.attachments.forEach { att ->
                    changeRecorder?.recordAttachmentDelete(att.uuid, deletedNote.note.uuid)
                }
            }
            noteDao.emptyTrash(ownerId)
        }
        if (database != null) database.withTransaction { block() } else block()
        deletedNotes.forEach { deletedNote ->
            val trashedId = deletedNote.note.id
            scheduler?.cancel(trashedId)
            scheduler?.cancelNotification(trashedId)
        }
        mediaMaintenance.cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
    }

    // ---------------------------------------------------------------------------
    // Bulk operations - see NoteBulkOperations for the single-transaction rationale.
    // ---------------------------------------------------------------------------

    suspend fun setPinned(
        ids: Collection<Long>,
        pinned: Boolean,
    ): Set<Long> = bulkOperations.setPinned(ids, pinned)

    suspend fun archiveNotes(ids: Collection<Long>) = bulkOperations.archiveNotes(ids)

    suspend fun unarchiveNotes(ids: Collection<Long>) = bulkOperations.unarchiveNotes(ids)

    suspend fun moveToTrash(ids: Collection<Long>) = bulkOperations.moveToTrash(ids)

    suspend fun restoreFromTrash(ids: Collection<Long>) = bulkOperations.restoreFromTrash(ids)

    suspend fun deleteForever(ids: Collection<Long>) = bulkOperations.deleteForever(ids)

    suspend fun markCompleted(ids: Collection<Long>): Map<Long, NoteCompletionSnapshot> = bulkOperations.markCompleted(ids)

    suspend fun markIncomplete(
        ids: Collection<Long>,
        snapshots: Map<Long, NoteCompletionSnapshot> = emptyMap(),
    ) = bulkOperations.markIncomplete(ids, snapshots)

    /**
     * Deletes trashed notes whose [NoteEntity.trashedAt] is older than [cutoffMillis].
     * Called by the daily WorkManager sweep to implement the 30-day retention policy.
     * Returns the number of ids that were purged (including those already missing a
     * pending reminder so we don't try to cancel an unscheduled alarm).
     */
    suspend fun autoEmptyTrashOlderThan(cutoffMillis: Long): Int {
        val deletedNotes = noteDao.trashedNoteIdsOlderThan(cutoffMillis).mapNotNull { noteId -> noteDao.get(noteId) }
        deletedNotes.forEach { deletedNote ->
            val noteId = deletedNote.note.id
            scheduler?.cancel(noteId)
            scheduler?.cancelNotification(noteId)
        }
        noteDao.deleteTrashedOlderThan(cutoffMillis)
        mediaMaintenance.cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
        return deletedNotes.size
    }

    /**
     * Removes every note (active and trashed), checklist rows, and attachments (Room cascades).
     * Cancels scheduled reminders for each note id first.
     */
    suspend fun deleteAllNotes() {
        val deletedNotes = noteDao.allNoteIds().mapNotNull { noteId -> noteDao.get(noteId) }
        deletedNotes.forEach { deletedNote ->
            val noteId = deletedNote.note.id
            scheduler?.cancel(noteId)
            scheduler?.cancelNotification(noteId)
        }
        noteDao.deleteAllNotes()
        mediaMaintenance.cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
    }

    /**
     * Runs [importBlock] only after the notes table has been cleared, inside a single Room
     * transaction when [database] is non-null so a failed import rolls back the delete and
     * leaves existing notes intact. Reminder [PendingIntent]s are reconciled after a successful
     * commit only (never cancel the old schedule before we know the replace succeeded).
     */
    suspend fun restoreNotesFullReplace(
        ownerId: String? = ownerIdProvider(),
        importBlock: suspend () -> Int,
    ): Int {
        val oldNotes = if (ownerId != null) noteDao.notesForOwner(ownerId) else noteDao.guestNotes()
        val oldIds = oldNotes.map { it.note.id }.toSet()
        val replacedNotes = oldNotes
        val count =
            haddad.three.notas.data.sync.change.SyncContext.withRemoteSuppression {
                if (database != null) {
                    database.withTransaction {
                        noteDao.deleteNotesForOwner(ownerId)
                        importBlock()
                    }
                } else {
                    oldIds.forEach { noteId ->
                        scheduler?.cancel(noteId)
                        scheduler?.cancelNotification(noteId)
                    }
                    noteDao.deleteNotesForOwner(ownerId)
                    importBlock()
                }
            }
        mediaMaintenance.cleanupUnreferencedMedia(replacedNotes.flatMap { replacedNote -> replacedNote.mediaUris() })
        resyncRemindersAfterMassReplace(oldIds)
        return count
    }

    suspend fun migrateGuestDataToOwner(targetOwnerId: String): Int {
        val now = clock()
        val block: suspend () -> Int = {
            val count = noteDao.claimGuestNotes(targetOwnerId, now)
            itemDao.claimGuestChecklistItems(targetOwnerId)
            attachmentDao.claimGuestAttachments(targetOwnerId)
            shoulderRepository?.claimGuestShoulders(targetOwnerId)
            count
        }
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    internal suspend fun <Result> runImportTransaction(importBlock: suspend () -> Result): Result {
        return haddad.three.notas.data.sync.change.SyncContext.withRemoteSuppression {
            if (database != null) {
                database.withTransaction {
                    importBlock()
                }
            } else {
                importBlock()
            }
        }
    }

    internal suspend fun reconcileImportedNotes(importedNoteIds: Collection<Long>) {
        val schedulerNonNull = scheduler
        if (schedulerNonNull != null) {
            importedNoteIds.distinct().forEach { noteId ->
                val noteWithItems = noteDao.get(noteId)
                if (noteWithItems == null) {
                    schedulerNonNull.cancel(noteId)
                } else {
                    val note = noteWithItems.note
                    if (note.trashed || note.archived || note.completedAt != null) {
                        schedulerNonNull.cancel(noteId)
                    } else {
                        schedulerNonNull.scheduleOrShow(note, noteWithItems.items)
                    }
                }
            }
        }
    }

    private suspend fun resyncRemindersAfterMassReplace(oldIds: Set<Long>) {
        val schedulerNonNull = scheduler ?: return
        val newIds = noteDao.allNoteIds().toSet()
        (oldIds + newIds).forEach { noteId -> schedulerNonNull.cancel(noteId) }
        newIds.forEach { noteId ->
            val noteWithItems = noteDao.get(noteId) ?: return@forEach
            val note = noteWithItems.note
            if (!note.trashed && !note.archived && note.completedAt == null && note.reminderAt != null) {
                schedulerNonNull.scheduleOrShow(note, noteWithItems.items)
            }
        }
    }

    /**
     * Copies the note or list into a new row. Reminders are not copied on the duplicate.
     * Neither are the starred and pinned flags (both go through the create path, which
     * defaults them off) - two identical cards side by side at the top of Home is not what
     * "duplicate" should mean.
     */
    suspend fun duplicateNote(id: Long): Long? {
        val existing = get(id) ?: return null
        val note = existing.note
        val duplicatedHeroUri = appMediaStorage?.copyHeroForDuplicate(note.pictureUri) ?: note.pictureUri
        val optionsWithoutReminder =
            NoteOptions(
                reminderAt = null,
                importance = note.importance,
                visibility = note.visibility,
                pictureUri = duplicatedHeroUri,
                pictureHeroFraming = note.pictureHeroFraming,
                locked = note.locked,
                iconKey = note.iconKey,
                actions = note.actions,
                recurrence = null,
            )
        val newId =
            when (note.kind) {
                NoteKind.NOTE ->
                    createNote(
                        title = note.title,
                        body = note.body,
                        colorIndex = note.colorIndex,
                        options = optionsWithoutReminder,
                    )
                NoteKind.LIST -> {
                    val persistableItems =
                        existing.items.map { item ->
                            PersistableChecklistItem(
                                localKey = item.id,
                                text = item.text,
                                details = item.details,
                                checked = item.checked,
                                sortOrder = item.sortOrder,
                                parentLocalKey = item.parentId,
                                depth = item.depth,
                            )
                        }
                    createListWithItems(
                        title = note.title,
                        colorIndex = note.colorIndex,
                        items = persistableItems,
                        options = optionsWithoutReminder,
                    )
                }
            }
        existing.attachments.forEach { attachment ->
            val duplicatedAttachmentUri =
                appMediaStorage?.copyAttachmentForDuplicate(
                    noteId = newId,
                    uriString = attachment.uri,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                ) ?: attachment.uri
            addAttachment(
                noteId = newId,
                uri = duplicatedAttachmentUri,
                displayName = attachment.displayName,
                mimeType = attachment.mimeType,
            )
        }
        return newId
    }

    suspend fun addAttachment(
        noteId: Long,
        uri: String,
        displayName: String,
        mimeType: String?,
    ): Long = mediaMaintenance.addAttachment(noteId, uri, displayName, mimeType)

    /**
     * Inserts a note or list with checklist rows and attachments.
     * When [note].id is non-zero, Room uses that id (used after [deleteAllNotes] for full restore).
     */
    suspend fun importNoteWithChildren(
        note: NoteEntity,
        items: List<ChecklistItemEntity>,
        attachments: List<NoteAttachmentEntity>,
        suppressReminderSchedule: Boolean = false,
    ): Long {
        val noteWithSynced = note.withSyncedPrimaryReminder()
        val noteId =
            noteDao.insert(
                noteWithSynced.copy(
                    checklistText =
                        checklistSearchText(
                            items
                                .sortedBy { item -> item.sortOrder }
                                .flatMap { item -> listOf(item.text, item.details) },
                        ),
                    attachmentText = attachmentSearchText(attachments),
                    actionsText = actionsSearchText(noteWithSynced.actions),
                ),
            )
        if (items.isNotEmpty()) {
            // Backup rows carry stable pre-export ids so parentId pointers can be remapped
            // after Room assigns fresh autogenerated ids. Fall back to preserving input order
            // via sortOrder when the import lacks explicit ids (legacy archives).
            val sorted = items.sortedBy { it.sortOrder }
            checklistWriter.insertHierarchy(
                noteId = noteId,
                items =
                    sorted.map { item ->
                        PersistableChecklistItem(
                            localKey = item.id,
                            text = item.text,
                            details = item.details,
                            checked = item.checked,
                            sortOrder = item.sortOrder,
                            parentLocalKey = item.parentId,
                            depth = item.depth,
                            uuid = item.uuid,
                        )
                    },
                ownerId = note.ownerId,
            )
        }
        attachments.forEach { attachment ->
            attachmentDao.insert(
                attachment.copy(id = 0, noteId = noteId, ownerId = note.ownerId),
            )
        }
        if (!suppressReminderSchedule && !noteWithSynced.trashed) {
            if (noteWithSynced.reminderAt != null) {
                scheduler?.scheduleOrShow(noteWithSynced.copy(id = noteId), items)
            }
        }
        return noteId
    }

    suspend fun updatePictureUri(
        noteId: Long,
        pictureUri: String?,
    ) = mediaMaintenance.updatePictureUri(noteId, pictureUri)

    suspend fun removeAttachment(id: Long) = mediaMaintenance.removeAttachment(id)

    suspend fun refreshNotificationVisibilityPreview(
        id: Long,
        visibility: Visibility,
    ) = reminderCoordinator.refreshNotificationVisibilityPreview(id, visibility)

    suspend fun refreshActiveReminderNotifications() = reminderCoordinator.refreshActiveReminderNotifications()

    @Suppress("ktlint:standard:function-expression-body")
    suspend fun activeReminderNotes(): List<NoteWithItems> {
        return noteDao.activeRemindersUntil(Long.MAX_VALUE)
    }

    suspend fun refreshReminderSummaryNotification() = reminderCoordinator.refreshReminderSummaryNotification()

    suspend fun reminderSummaryItems(now: Long = clock()): List<NoteWithItems> = reminderCoordinator.reminderSummaryItems(now)

    suspend fun refreshCriticalReminderRepeat() = reminderCoordinator.refreshCriticalReminderRepeat()

    /** Active Critical notes with a due reminder, which the shared repeat re-alerts. */
    suspend fun criticalRepeatNotes(now: Long = clock()): List<NoteWithItems> = reminderCoordinator.criticalRepeatNotes(now)

    suspend fun toggleItemChecked(item: ChecklistItemEntity) {
        val updated = item.copy(checked = !item.checked)
        val block: suspend () -> Unit = {
            itemDao.update(updated)
            val noteUuid = noteDao.get(item.noteId)?.note?.uuid ?: ""
            if (noteUuid.isNotBlank()) {
                changeRecorder?.recordChecklistItemUpsert(updated, noteUuid)
            }
        }
        if (database != null) database.withTransaction { block() } else block()
    }

    /**
     * Used when the user taps "Mark as done" on a reminder notification action.
     * Recurring notes advance to their next occurrence; one-shot notes enter Done.
     */
    suspend fun clearReminderFromNotificationAction(noteId: Long): Boolean = markCompleted(noteId) != null

    /**
     * Mark a note done: recurring notes roll forward to their next occurrence, one-shot notes
     * enter Done. Returns the pre-completion snapshot the snackbar Undo needs (null when the
     * note id no longer exists). See [NoteReminderCoordinator] for the full rules.
     */
    suspend fun markCompleted(noteId: Long): NoteCompletionSnapshot? = reminderCoordinator.markCompleted(noteId)

    suspend fun markIncomplete(
        noteId: Long,
        snapshot: NoteCompletionSnapshot? = null,
    ): Boolean = reminderCoordinator.markIncomplete(noteId, snapshot)

    suspend fun restoreCompletionStates(snapshots: Map<Long, NoteCompletionSnapshot>) = reminderCoordinator.restoreCompletionStates(snapshots)

    companion object {
        /** 30 days in milliseconds -- the retention window for trashed notes. */
        const val TRASH_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}

/**
 * Tokenise [raw] into an FTS4-safe query string that performs prefix matching on every term.
 * Strips characters FTS treats as syntax (quotes, parens, columns, operators) to avoid
 * accidentally activating boolean operators when the user is just typing a search. Returns
 * an empty string when there's nothing matchable left (caller substitutes an empty result).
 */
internal fun toFtsPrefixQuery(raw: String): String {
    if (raw.isBlank()) return ""
    val sanitised =
        raw
            .replace(Regex("[\"'*:()\\-^]"), " ")
            .trim()
    if (sanitised.isEmpty()) return ""
    return sanitised
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ") { "$it*" }
}
