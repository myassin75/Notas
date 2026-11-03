package haddad.three.notas.data.sync

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import haddad.three.notas.BuildConfig
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.RememberDatabase
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.di.IoDispatcher
import haddad.three.notas.reminders.ReminderScheduler
import haddad.three.notas.ui.components.SyncStatus
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Offline-first synchronization engine connecting local Room persistence
 * with the remote PostgreSQL backend API.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncStateStore: SyncStateStore,
    private val syncApiClient: SyncApiClient,
    private val database: RememberDatabase? = null,
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val attachmentDao: AttachmentDao,
    private val shoulderDao: ShoulderDao,
    private val scheduler: ReminderScheduler? = null,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()
    private val formatter = DateTimeFormatter.ISO_INSTANT

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (database != null) database.withTransaction { block() } else block()
    }

    /**
     * Executes a full synchronization cycle (push pending local modifications,
     * then pull remote changes since last cursor).
     */
    suspend fun synchronize(): SyncResult<Unit> = withContext(ioDispatcher) {
        val user = authRepository.currentUser
        if (user == null) {
            syncStateStore.updateSyncStatus(SyncStatus.NOT_CONNECTED)
            return@withContext SyncResult.Offline
        }

        if (!syncMutex.tryLock()) {
            // Already syncing, do not spawn concurrent sync jobs
            return@withContext SyncResult.Success(Unit)
        }

        try {
            syncStateStore.updateSyncStatus(SyncStatus.SYNCING)

            val initialToken = authRepository.getIdToken()
            if (initialToken == null) {
                syncStateStore.updateSyncStatus(SyncStatus.SESSION_EXPIRED)
                return@withContext SyncResult.Unauthorized
            }
            var currentToken: String = initialToken

            // ------------------------------------------------------------------------------------
            // 1. PUSH PHASE: Collect local modifications & tombstones
            // ------------------------------------------------------------------------------------
            val syncStartTime = System.currentTimeMillis()
            val lastPushedAt = syncStateStore.getLastPushedAt(user.uid)

            val localShoulders = shoulderDao.shouldersForOwner(user.uid)
            val modifiedShoulders = localShoulders.filter { it.ownerId == user.uid && it.updatedAt > lastPushedAt }

            val localNotes = noteDao.notesForOwner(user.uid)
            val modifiedNotes = localNotes.filter { it.note.ownerId == user.uid && it.note.updatedAt > lastPushedAt }

            val tombstones = syncStateStore.getTombstones(user.uid)

            val categoriesToPush = mutableListOf<CategorySyncDto>()
            val notesToPush = mutableListOf<NoteSyncDto>()

            // Map modified shoulders
            for (shoulder in modifiedShoulders) {
                categoriesToPush.add(SyncDtoMapping.shoulderToDto(shoulder, isDeleted = false))
            }

            // Map shoulder tombstones
            val categoryTombstones = tombstones.filter { it.entityType == "category" }
            for (ct in categoryTombstones) {
                val iso = formatter.format(Instant.ofEpochMilli(ct.deletedAt))
                categoriesToPush.add(
                    CategorySyncDto(
                        id = ct.uuid,
                        name = "",
                        normalizedName = ct.uuid,
                        colorHex = null,
                        clientCreatedAt = iso,
                        clientUpdatedAt = iso,
                        isDeleted = true,
                    ),
                )
            }

            // Map modified notes
            val itemTombstonesByNoteUuid = tombstones.filter { it.entityType == "checklist_item" && it.noteUuid != null }
                .groupBy { it.noteUuid!! }
            val attTombstonesByNoteUuid = tombstones.filter { it.entityType == "attachment" && it.noteUuid != null }
                .groupBy { it.noteUuid!! }

            for (noteWithItems in modifiedNotes) {
                val baseDto = SyncDtoMapping.noteWithItemsToDto(noteWithItems, isDeleted = false)
                val noteUuid = noteWithItems.note.uuid

                val extraDeletedItems = itemTombstonesByNoteUuid[noteUuid]?.map { itemTombstone ->
                    ChecklistItemSyncDto(
                        id = itemTombstone.uuid,
                        text = "",
                        isDeleted = true,
                    )
                }.orEmpty()

                val extraDeletedAttachments = attTombstonesByNoteUuid[noteUuid]?.map { attTombstone ->
                    AttachmentSyncDto(
                        id = attTombstone.uuid,
                        uri = "",
                        displayName = "",
                        isDeleted = true,
                    )
                }.orEmpty()

                notesToPush.add(
                    baseDto.copy(
                        checklistItems = baseDto.checklistItems + extraDeletedItems,
                        attachments = baseDto.attachments + extraDeletedAttachments,
                    ),
                )
            }

            // Map note tombstones
            val noteTombstones = tombstones.filter { it.entityType == "note" }
            for (nt in noteTombstones) {
                val iso = formatter.format(Instant.ofEpochMilli(nt.deletedAt))
                notesToPush.add(
                    NoteSyncDto(
                        id = nt.uuid,
                        kind = "NOTE",
                        title = "",
                        clientCreatedAt = iso,
                        clientUpdatedAt = iso,
                        isDeleted = true,
                    ),
                )
            }

            // Push in bounded batches (max 500 records per model per backend validation).
            // Audit AUD-24: the previous implementation pushed only `take(500)` of each list
            // yet advanced the lastPushedAt watermark over the FULL modified lists, so records
            // 501+ were silently skipped until their next edit. We now drain in batches and
            // advance the watermark ONLY after a complete drain (every batch applied, minus
            // accumulated conflicts). A partial drain leaves the watermark untouched — the
            // remainder is re-pushed on the next sync, which is safe because applies are
            // idempotent under last-write-wins revision comparison.
            if (categoriesToPush.isNotEmpty() || notesToPush.isNotEmpty()) {
                val deviceId = syncStateStore.getOrCreateDeviceId()
                val accumulatedConflictIds = mutableSetOf<String>()
                var catCursor = 0
                var noteCursor = 0
                var pushCycles = 0
                val maxPushCycles = 100

                while ((catCursor < categoriesToPush.size || noteCursor < notesToPush.size) && pushCycles < maxPushCycles) {
                    pushCycles++

                    val catBatch = categoriesToPush.drop(catCursor).take(500)
                    val noteBatch = notesToPush.drop(noteCursor).take(500)
                    val batchCatIds = catBatch.map { it.id }.toSet()
                    val batchNoteIds = noteBatch.map { it.id }.toSet()

                    val pushPayload = SyncPushRequestDto(
                        deviceId = deviceId,
                        deviceName = android.os.Build.MODEL,
                        appVersion = BuildConfig.VERSION_NAME,
                        categories = catBatch,
                        notes = noteBatch,
                    )

                    var pushResult = syncApiClient.pushSync(currentToken, pushPayload)

                    if (pushResult is SyncResult.Unauthorized) {
                        val refreshedToken = authRepository.getIdToken(forceRefresh = true)
                        if (refreshedToken != null) {
                            currentToken = refreshedToken
                            pushResult = syncApiClient.pushSync(currentToken, pushPayload)
                        }
                    }

                    when (pushResult) {
                        is SyncResult.Success -> {
                            val pushData = pushResult.data
                            val batchConflictIds = pushData.conflicts.map { it.id }.toSet()
                            accumulatedConflictIds.addAll(batchConflictIds)

                            // Record sanitized conflicts for observability and UI status
                            if (pushData.conflicts.isNotEmpty()) {
                                syncStateStore.recordConflicts(user.uid, pushData.conflicts)
                            }

                            // Only tombstones carried by THIS batch are candidates for removal:
                            // top-level tombstones map to their own record id, child tombstones
                            // (checklist items / attachments) ride inside their parent note DTO
                            // and are confirmed indirectly via the parent note id.
                            val batchChildTombstones = noteBatch.filter { !it.isDeleted }.flatMap { note ->
                                (itemTombstonesByNoteUuid[note.id].orEmpty() +
                                    attTombstonesByNoteUuid[note.id].orEmpty())
                            }
                            val batchTombstones =
                                categoryTombstones.filter { it.uuid in batchCatIds } +
                                    noteTombstones.filter { it.uuid in batchNoteIds } +
                                    batchChildTombstones

                            // Retain rejected/conflicted tombstones; remove only confirmed applied ones
                            val confirmedBatchTombstones = batchTombstones.filter {
                                it.uuid !in batchConflictIds &&
                                    (it.noteUuid == null || it.noteUuid !in batchConflictIds)
                            }
                            if (confirmedBatchTombstones.isNotEmpty()) {
                                syncStateStore.removeTombstones(
                                    user.uid,
                                    confirmedBatchTombstones.map { it.uuid }.toSet(),
                                )
                            }

                            catCursor += catBatch.size
                            noteCursor += noteBatch.size
                        }
                        is SyncResult.Unauthorized -> {
                            syncStateStore.updateSyncStatus(SyncStatus.SESSION_EXPIRED)
                            return@withContext SyncResult.Unauthorized
                        }
                        is SyncResult.RateLimited -> {
                            syncStateStore.updateSyncStatus(SyncStatus.NETWORK_ERROR)
                            return@withContext SyncResult.RateLimited
                        }
                        is SyncResult.Error -> {
                            syncStateStore.updateSyncStatus(SyncStatus.NETWORK_ERROR)
                            return@withContext pushResult
                        }
                        SyncResult.Offline -> {
                            syncStateStore.updateSyncStatus(SyncStatus.OFFLINE)
                            return@withContext SyncResult.Offline
                        }
                    }
                }

                val fullyDrained = catCursor >= categoriesToPush.size && noteCursor >= notesToPush.size

                // Advance watermark only for confirmed applied modifications on a complete drain
                if (fullyDrained) {
                    val appliedShoulders = modifiedShoulders.filter { it.uuid !in accumulatedConflictIds }
                    val appliedNotes = modifiedNotes.filter { it.note.uuid !in accumulatedConflictIds }
                    val appliedTimestamps = appliedShoulders.map { it.updatedAt } + appliedNotes.map { it.note.updatedAt }

                    if (appliedTimestamps.isNotEmpty()) {
                        val maxAppliedTime = appliedTimestamps.maxOrNull() ?: lastPushedAt
                        if (maxAppliedTime > lastPushedAt) {
                            syncStateStore.setLastPushedAt(user.uid, maxAppliedTime)
                        }
                    }
                }
            }

            // ------------------------------------------------------------------------------------
            // 2. PULL PHASE: Retrieve changes since lastSyncedRevision
            // ------------------------------------------------------------------------------------
            var currentCursor = syncStateStore.getSyncCursor(user.uid)
            var hasMore = true
            var pagesFetched = 0
            val maxPages = 100

            while (hasMore && pagesFetched < maxPages) {
                pagesFetched++
                var fetchResult = syncApiClient.fetchChanges(currentToken, sinceRevision = currentCursor, limit = 100)

                if (fetchResult is SyncResult.Unauthorized) {
                    val refreshedToken = authRepository.getIdToken(forceRefresh = true)
                    if (refreshedToken != null) {
                        currentToken = refreshedToken
                        fetchResult = syncApiClient.fetchChanges(currentToken, sinceRevision = currentCursor, limit = 100)
                    }
                }

                when (fetchResult) {
                    is SyncResult.Success -> {
                        val changes = fetchResult.data
                        applyRemoteChanges(user.uid, changes)
                        val newCursor = changes.currentServerRevision

                        // Safe pagination termination guard: break if cursor does not advance despite hasMore = true
                        val noProgress = (newCursor == currentCursor) && changes.categories.isEmpty() && changes.notes.isEmpty()
                        if (noProgress || (newCursor == currentCursor && changes.hasMore)) {
                            hasMore = false
                        } else {
                            currentCursor = newCursor
                            syncStateStore.setSyncCursor(user.uid, currentCursor)
                            hasMore = changes.hasMore
                        }
                    }
                    is SyncResult.Unauthorized -> {
                        syncStateStore.updateSyncStatus(SyncStatus.SESSION_EXPIRED)
                        return@withContext SyncResult.Unauthorized
                    }
                    is SyncResult.RateLimited -> {
                        syncStateStore.updateSyncStatus(SyncStatus.NETWORK_ERROR)
                        return@withContext SyncResult.RateLimited
                    }
                    is SyncResult.Error -> {
                        syncStateStore.updateSyncStatus(SyncStatus.NETWORK_ERROR)
                        return@withContext fetchResult
                    }
                    SyncResult.Offline -> {
                        syncStateStore.updateSyncStatus(SyncStatus.OFFLINE)
                        return@withContext SyncResult.Offline
                    }
                }
            }

            syncStateStore.setLastSyncedAt(user.uid, System.currentTimeMillis())
            syncStateStore.updateSyncStatus(SyncStatus.SYNCED)
            SyncResult.Success(Unit)
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Executes synchronization with bounded exponential backoff and jitter.
     */
    suspend fun synchronizeWithRetry(maxAttempts: Int = 3): SyncResult<Unit> {
        var currentDelay = 1000L
        val maxDelay = 30000L

        repeat(maxAttempts) { attempt ->
            val result = synchronize()
            when (result) {
                is SyncResult.Success -> return result
                is SyncResult.Unauthorized -> return result // Non-retryable
                is SyncResult.Offline -> return result
                is SyncResult.RateLimited,
                is SyncResult.Error -> {
                    if (attempt == maxAttempts - 1) return result
                    val jitter = (Random.nextDouble() * 0.4 - 0.2) * currentDelay
                    val delayMs = (currentDelay + jitter).toLong().coerceIn(500L, maxDelay)
                    delay(delayMs)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
                }
            }
        }
        return SyncResult.Error("Max retry attempts reached", code = "RETRY_EXHAUSTED")
    }

    /**
     * Applies remote pulled categories and notes transactionally into the local Room database.
     */
    /**
     * Backward-compatible overload for tests.
     */
    internal suspend fun applyRemoteChanges(changes: SyncChangesResponseDto) {
        val uid = authRepository.currentUser?.uid ?: "test_user"
        applyRemoteChanges(uid, changes)
    }

    /**
     * Applies remote pulled categories and notes transactionally into the local Room database,
     * assigning records to the authenticated owner and preventing stale deletion resurrects.
     */
    internal suspend fun applyRemoteChanges(userId: String, changes: SyncChangesResponseDto) {
        runInTransaction {
            val pendingTombstones = syncStateStore.getTombstones(userId).associateBy { it.uuid }

            // 1. Apply categories
            for (categoryDto in changes.categories) {
                val tombstone = pendingTombstones[categoryDto.id]
                val remoteUpdatedAt = SyncDtoMapping.parseInstant(categoryDto.clientUpdatedAt) ?: 0L

                if (categoryDto.isDeleted) {
                    val existing = shoulderDao.getByUuid(categoryDto.id)
                    if (existing != null) {
                        shoulderDao.deleteShoulder(existing)
                    }
                } else {
                    // Prevent resurrection if locally deleted with deletedAt >= remoteUpdatedAt
                    if (tombstone != null && tombstone.deletedAt >= remoteUpdatedAt) {
                        continue
                    }

                    val existingByUuid = shoulderDao.getByUuid(categoryDto.id)
                    if (existingByUuid != null) {
                        val updated = SyncDtoMapping.dtoToShoulder(categoryDto, existingId = existingByUuid.id, ownerId = userId)
                        shoulderDao.updateShoulder(updated)
                    } else {
                        val existingByName = shoulderDao.getByNormalizedName(categoryDto.normalizedName)
                        if (existingByName != null && (existingByName.ownerId == null || existingByName.ownerId == userId)) {
                            val updated = SyncDtoMapping.dtoToShoulder(categoryDto, existingId = existingByName.id, ownerId = userId)
                            shoulderDao.updateShoulder(updated)
                        } else if (existingByName == null) {
                            val newShoulder = SyncDtoMapping.dtoToShoulder(categoryDto, existingId = 0L, ownerId = userId)
                            shoulderDao.insertShoulder(newShoulder)
                        }
                    }
                }
            }

            // 2. Apply notes
            for (noteDto in changes.notes) {
                val noteTombstone = pendingTombstones[noteDto.id]
                val remoteUpdatedAt = SyncDtoMapping.parseInstant(noteDto.clientUpdatedAt) ?: 0L

                if (noteDto.isDeleted) {
                    val existing = noteDao.getByUuid(noteDto.id)
                    if (existing != null) {
                        val localId = existing.note.id
                        scheduler?.cancel(localId)
                        scheduler?.cancelNotification(localId)
                        noteDao.deleteById(localId)
                    }
                } else {
                    // Prevent resurrection if locally deleted with deletedAt >= remoteUpdatedAt
                    if (noteTombstone != null && noteTombstone.deletedAt >= remoteUpdatedAt) {
                        continue
                    }

                    val existing = noteDao.getByUuid(noteDto.id)
                    val shoulder = noteDto.categoryId?.let { catUuid ->
                        shoulderDao.getByUuid(catUuid)
                    }

                    val checklistText = noteDto.checklistItems
                        .filter { !it.isDeleted }
                        .joinToString(" ") { it.text }
                    val attachmentText = noteDto.attachments
                        .filter { !it.isDeleted }
                        .joinToString(" ") { it.displayName }

                    val noteEntity = SyncDtoMapping.dtoToNote(
                        dto = noteDto,
                        existingId = existing?.note?.id ?: 0L,
                        shoulderId = shoulder?.id,
                        checklistText = checklistText,
                        attachmentText = attachmentText,
                        ownerId = userId,
                    )

                    val resolvedNoteId = if (existing != null) {
                        noteDao.update(noteEntity)
                        existing.note.id
                    } else {
                        noteDao.insert(noteEntity)
                    }

                    // Checklist items
                    if (noteDto.checklistItems.isNotEmpty()) {
                        val uuidToLocalId = mutableMapOf<String, Long>()

                        // First pass: upsert or delete items, capturing generated/existing local IDs
                        for (itemDto in noteDto.checklistItems) {
                            val itemTombstone = pendingTombstones[itemDto.id]
                            if (itemDto.isDeleted || (itemTombstone != null && itemTombstone.deletedAt >= remoteUpdatedAt)) {
                                val existingItem = itemDao.getByUuid(itemDto.id)
                                if (existingItem != null) {
                                    itemDao.deleteById(existingItem.id)
                                }
                            } else {
                                val existingItem = itemDao.getByUuid(itemDto.id)
                                val itemEntity = SyncDtoMapping.dtoToChecklistItem(
                                    dto = itemDto,
                                    existingId = existingItem?.id ?: 0L,
                                    noteId = resolvedNoteId,
                                    parentId = null,
                                    ownerId = userId,
                                )
                                val itemId = if (existingItem != null) {
                                    itemDao.update(itemEntity)
                                    existingItem.id
                                } else {
                                    itemDao.insert(itemEntity)
                                }
                                uuidToLocalId[itemDto.id] = itemId
                            }
                        }

                        // Second pass: resolve parentId relationships
                        for (itemDto in noteDto.checklistItems) {
                            if (!itemDto.isDeleted && itemDto.parentId != null) {
                                val localParentId = uuidToLocalId[itemDto.parentId]
                                if (localParentId != null) {
                                    val currentItem = itemDao.getByUuid(itemDto.id)
                                    if (currentItem != null && currentItem.parentId != localParentId) {
                                        itemDao.update(currentItem.copy(parentId = localParentId))
                                    }
                                }
                            }
                        }
                    }

                    // Attachments
                    if (noteDto.attachments.isNotEmpty()) {
                        for (attDto in noteDto.attachments) {
                            val attTombstone = pendingTombstones[attDto.id]
                            if (attDto.isDeleted || (attTombstone != null && attTombstone.deletedAt >= remoteUpdatedAt)) {
                                val existingAtt = attachmentDao.getByUuid(attDto.id)
                                if (existingAtt != null) {
                                    attachmentDao.deleteById(existingAtt.id)
                                }
                            } else {
                                val existingAtt = attachmentDao.getByUuid(attDto.id)
                                val attEntity = SyncDtoMapping.dtoToAttachment(
                                    dto = attDto,
                                    existingId = existingAtt?.id ?: 0L,
                                    noteId = resolvedNoteId,
                                    ownerId = userId,
                                )
                                if (existingAtt != null) {
                                    // Preserve local file URI if remote uri is placeholder
                                    val preservedUri = if (existingAtt.uri.isNotBlank()) existingAtt.uri else attEntity.uri
                                    attachmentDao.insert(attEntity.copy(uri = preservedUri))
                                } else {
                                    attachmentDao.insert(attEntity)
                                }
                            }
                        }
                    }

                    // Schedule or update notification reminders
                    if (noteEntity.reminderAt != null && !noteEntity.trashed && !noteEntity.archived && noteEntity.completedAt == null) {
                        val currentItems = itemDao.itemsFor(resolvedNoteId)
                        scheduler?.scheduleOrShow(noteEntity, currentItems)
                    }
                }
            }
        }
    }
}
