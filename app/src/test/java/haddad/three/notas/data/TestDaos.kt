package haddad.three.notas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class BaseFakeNoteDao : NoteDao {
    val notes = mutableMapOf<Long, NoteEntity>()
    var idCounter = 1L

    private fun matchesOwner(
        noteOwnerId: String?,
        filterOwnerId: String?,
    ): Boolean = (filterOwnerId == null && noteOwnerId == null) || (filterOwnerId != null && noteOwnerId == filterOwnerId)

    override fun observeActive(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(notes.values.filter { !it.trashed && !it.archived && matchesOwner(it.ownerId, ownerId) }.map { NoteWithItems(it, emptyList()) })

    override fun observeScheduled(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(notes.values.filter { !it.trashed && !it.archived && it.reminderAt != null && matchesOwner(it.ownerId, ownerId) }.map { NoteWithItems(it, emptyList()) })

    override fun observeTrashed(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(notes.values.filter { it.trashed && matchesOwner(it.ownerId, ownerId) }.map { NoteWithItems(it, emptyList()) })

    override fun observeArchived(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(notes.values.filter { it.archived && !it.trashed && matchesOwner(it.ownerId, ownerId) }.map { NoteWithItems(it, emptyList()) })

    override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(notes[id]?.let { NoteWithItems(it, emptyList()) })

    override suspend fun get(id: Long): NoteWithItems? = notes[id]?.let { NoteWithItems(it, emptyList()) }

    override suspend fun getByUuid(uuid: String): NoteWithItems? = notes.values.find { it.uuid == uuid }?.let { NoteWithItems(it, emptyList()) }

    override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes.values.find { it.uuid == uuid }

    override suspend fun allNotes(): List<NoteWithItems> = notes.values.map { NoteWithItems(it, emptyList()) }

    override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = notes.values.filter { it.ownerId == ownerId }.map { NoteWithItems(it, emptyList()) }

    override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()

    override fun searchNotes(
        ftsQuery: String,
        ownerId: String?,
    ): Flow<List<NoteWithItems>> {
        val clean = ftsQuery.replace("*", "").lowercase()
        return flowOf(
            notes.values
                .filter {
                    !it.trashed && !it.archived &&
                        matchesOwner(it.ownerId, ownerId) &&
                        (it.title.lowercase().contains(clean) || it.body.lowercase().contains(clean))
                }.map { NoteWithItems(it, emptyList()) },
        )
    }

    override fun searchArchived(
        ftsQuery: String,
        ownerId: String?,
    ): Flow<List<NoteWithItems>> {
        val clean = ftsQuery.replace("*", "").lowercase()
        return flowOf(
            notes.values
                .filter {
                    it.archived && !it.trashed &&
                        matchesOwner(it.ownerId, ownerId) &&
                        (it.title.lowercase().contains(clean) || it.body.lowercase().contains(clean))
                }.map { NoteWithItems(it, emptyList()) },
        )
    }

    override fun searchTrashed(
        ftsQuery: String,
        ownerId: String?,
    ): Flow<List<NoteWithItems>> {
        val clean = ftsQuery.replace("*", "").lowercase()
        return flowOf(
            notes.values
                .filter {
                    it.trashed &&
                        matchesOwner(it.ownerId, ownerId) &&
                        (it.title.lowercase().contains(clean) || it.body.lowercase().contains(clean))
                }.map { NoteWithItems(it, emptyList()) },
        )
    }

    override suspend fun insert(note: NoteEntity): Long {
        val id = if (note.id != 0L) note.id else idCounter++
        val entity = note.copy(id = id)
        notes[id] = entity
        return id
    }

    override suspend fun update(note: NoteEntity) {
        notes[note.id] = note
    }

    override suspend fun setTrashed(
        id: Long,
        trashed: Boolean,
        updatedAt: Long,
    ) {
        notes[id]?.let { notes[id] = it.copy(trashed = trashed, updatedAt = updatedAt) }
    }

    override suspend fun setArchived(
        id: Long,
        archived: Boolean,
        updatedAt: Long,
    ) {
        notes[id]?.let { notes[id] = it.copy(archived = archived, updatedAt = updatedAt) }
    }

    override suspend fun deleteById(id: Long) {
        notes.remove(id)
    }

    override suspend fun allNoteIds(): List<Long> = notes.keys.toList()

    override suspend fun countPictureUri(uri: String): Int = notes.values.count { it.pictureUri == uri }

    override suspend fun trashedNoteIds(ownerId: String?): List<Long> = notes.values.filter { it.trashed && matchesOwner(it.ownerId, ownerId) }.map { it.id }

    override suspend fun archivedNoteIds(ownerId: String?): List<Long> = notes.values.filter { it.archived && !it.trashed && matchesOwner(it.ownerId, ownerId) }.map { it.id }

    override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()

    override suspend fun deleteAllNotes() {
        notes.clear()
    }

    override suspend fun emptyTrash(ownerId: String?) {
        notes.entries.removeIf { it.value.trashed && matchesOwner(it.value.ownerId, ownerId) }
    }

    override suspend fun moveAllArchivedToTrash(
        ownerId: String?,
        updatedAt: Long,
    ) {
        notes.entries.forEach { entry ->
            if (entry.value.archived && !entry.value.trashed && matchesOwner(entry.value.ownerId, ownerId)) {
                notes[entry.key] = entry.value.copy(trashed = true, archived = false, trashedAt = updatedAt, updatedAt = updatedAt)
            }
        }
    }

    override suspend fun deleteTrashedOlderThan(cutoff: Long) {}

    override suspend fun deleteNotesForOwner(ownerId: String?) {
        notes.entries.removeIf { matchesOwner(it.value.ownerId, ownerId) }
    }

    override suspend fun guestNotes(): List<NoteWithItems> = notes.values.filter { it.ownerId == null }.map { NoteWithItems(it, emptyList()) }

    override suspend fun claimGuestNotes(
        targetOwnerId: String,
        now: Long,
    ): Int {
        var count = 0
        notes.entries.forEach { entry ->
            if (entry.value.ownerId == null) {
                notes[entry.key] = entry.value.copy(ownerId = targetOwnerId, updatedAt = now)
                count++
            }
        }
        return count
    }
}

open class BaseFakeChecklistItemDao : ChecklistItemDao {
    val items = mutableMapOf<Long, ChecklistItemEntity>()
    var idCounter = 1L

    override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = items.values.filter { it.noteId == noteId }

    override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = items.values.find { it.uuid == uuid }

    override suspend fun insert(item: ChecklistItemEntity): Long {
        val id = if (item.id != 0L) item.id else idCounter++
        items[id] = item.copy(id = id)
        return id
    }

    override suspend fun insertAll(itemsToInsert: List<ChecklistItemEntity>) {
        itemsToInsert.forEach { insert(it) }
    }

    override suspend fun update(item: ChecklistItemEntity) {
        items[item.id] = item
    }

    override suspend fun deleteById(id: Long) {
        items.remove(id)
    }

    override suspend fun deleteForNote(noteId: Long) {
        items.entries.removeIf { it.value.noteId == noteId }
    }

    override suspend fun claimGuestChecklistItems(targetOwnerId: String): Int {
        var count = 0
        items.entries.forEach { entry ->
            if (entry.value.ownerId == null) {
                items[entry.key] = entry.value.copy(ownerId = targetOwnerId)
                count++
            }
        }
        return count
    }
}

open class BaseFakeAttachmentDao : AttachmentDao {
    val attachments = mutableMapOf<Long, NoteAttachmentEntity>()
    var idCounter = 1L

    override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = attachments.values.filter { it.noteId == noteId }

    override suspend fun insert(attachment: NoteAttachmentEntity): Long {
        val id = if (attachment.id != 0L) attachment.id else idCounter++
        attachments[id] = attachment.copy(id = id)
        return id
    }

    override suspend fun insertAll(attachmentsToInsert: List<NoteAttachmentEntity>) {
        attachmentsToInsert.forEach { insert(it) }
    }

    override suspend fun getById(id: Long): NoteAttachmentEntity? = attachments[id]

    override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = attachments.values.find { it.uuid == uuid }

    override suspend fun countByUri(uri: String): Int = attachments.values.count { it.uri == uri }

    override suspend fun deleteById(id: Long) {
        attachments.remove(id)
    }

    override suspend fun deleteForNote(noteId: Long) {
        attachments.entries.removeIf { it.value.noteId == noteId }
    }

    override suspend fun claimGuestAttachments(targetOwnerId: String): Int {
        var count = 0
        attachments.entries.forEach { entry ->
            if (entry.value.ownerId == null) {
                attachments[entry.key] = entry.value.copy(ownerId = targetOwnerId)
                count++
            }
        }
        return count
    }
}

open class BaseFakeShoulderDao : ShoulderDao {
    val shoulders = mutableMapOf<Long, ShoulderEntity>()
    var idCounter = 1L

    override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders.values.toList())

    override suspend fun allShoulders(): List<ShoulderEntity> = shoulders.values.toList()

    override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = shoulders.values.filter { it.ownerId == ownerId }

    override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())

    override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders.values.find { it.id == id }

    override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders.values.find { it.uuid == uuid }

    override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.values.find { it.normalizedName == normalizedName }

    override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()

    override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
        val id = if (shoulder.id != 0L) shoulder.id else idCounter++
        shoulders[id] = shoulder.copy(id = id)
        return id
    }

    override suspend fun updateShoulder(shoulder: ShoulderEntity) {
        shoulders[shoulder.id] = shoulder
    }

    override suspend fun deleteShoulder(shoulder: ShoulderEntity) {
        shoulders.remove(shoulder.id)
    }

    override suspend fun setShoulderForNote(
        noteId: Long,
        shoulderId: Long?,
    ) {}

    override suspend fun claimGuestShoulders(targetOwnerId: String): Int {
        var count = 0
        shoulders.entries.forEach { entry ->
            if (entry.value.ownerId == null) {
                shoulders[entry.key] = entry.value.copy(ownerId = targetOwnerId)
                count++
            }
        }
        return count
    }
}

open class UuidBaseFakeNoteDao : NoteDao {
    val notes = mutableMapOf<String, NoteEntity>()

    override suspend fun insert(note: NoteEntity): Long {
        val assignedId = if (note.id == 0L) (notes.size + 1).toLong() else note.id
        val stored = note.copy(id = assignedId)
        notes[note.uuid] = stored
        return assignedId
    }

    override suspend fun update(note: NoteEntity) {
        notes[note.uuid] = note
    }

    override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes[uuid]

    override suspend fun deleteById(id: Long) {
        notes.values.find { it.id == id }?.let { notes.remove(it.uuid) }
    }

    override fun observeActive(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observeScheduled(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observeTrashed(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observeArchived(ownerId: String?): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(null)

    override suspend fun get(id: Long): NoteWithItems? = null

    override suspend fun getByUuid(uuid: String): NoteWithItems? = null

    override suspend fun allNotes(): List<NoteWithItems> = emptyList()

    override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = emptyList()

    override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()

    override fun searchNotes(
        ftsQuery: String,
        ownerId: String?,
    ): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun searchArchived(
        ftsQuery: String,
        ownerId: String?,
    ): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun searchTrashed(
        ftsQuery: String,
        ownerId: String?,
    ): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override suspend fun setTrashed(
        id: Long,
        trashed: Boolean,
        updatedAt: Long,
    ) {}

    override suspend fun setArchived(
        id: Long,
        archived: Boolean,
        updatedAt: Long,
    ) {}

    override suspend fun allNoteIds(): List<Long> = notes.values.map { it.id }

    override suspend fun countPictureUri(uri: String): Int = 0

    override suspend fun trashedNoteIds(ownerId: String?): List<Long> = emptyList()

    override suspend fun archivedNoteIds(ownerId: String?): List<Long> = emptyList()

    override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()

    override suspend fun deleteAllNotes() {
        notes.clear()
    }

    override suspend fun emptyTrash(ownerId: String?) {}

    override suspend fun moveAllArchivedToTrash(
        ownerId: String?,
        updatedAt: Long,
    ) {}

    override suspend fun deleteTrashedOlderThan(cutoff: Long) {}

    override suspend fun deleteNotesForOwner(ownerId: String?) {
        notes.entries.removeIf { it.value.ownerId == ownerId }
    }

    override suspend fun guestNotes(): List<NoteWithItems> = emptyList()

    override suspend fun claimGuestNotes(
        targetOwnerId: String,
        now: Long,
    ): Int = 0
}

open class UuidBaseFakeChecklistItemDao : ChecklistItemDao {
    val items = mutableMapOf<String, ChecklistItemEntity>()

    override suspend fun insert(item: ChecklistItemEntity): Long {
        val assignedId = if (item.id == 0L) (items.size + 1).toLong() else item.id
        val stored = item.copy(id = assignedId)
        items[item.uuid] = stored
        return assignedId
    }

    override suspend fun insertAll(items: List<ChecklistItemEntity>) {
        items.forEach { insert(it) }
    }

    override suspend fun update(item: ChecklistItemEntity) {
        items[item.uuid] = item
    }

    override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = items[uuid]

    override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = items.values.filter { it.noteId == noteId }

    override suspend fun deleteById(id: Long) {
        items.values.find { it.id == id }?.let { items.remove(it.uuid) }
    }

    override suspend fun deleteForNote(noteId: Long) {
        items.values.filter { it.noteId == noteId }.forEach { items.remove(it.uuid) }
    }

    override suspend fun claimGuestChecklistItems(targetOwnerId: String): Int = 0
}

open class UuidBaseFakeShoulderDao : ShoulderDao {
    val shoulders = mutableMapOf<String, ShoulderEntity>()

    override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
        val assignedId = if (shoulder.id == 0L) (shoulders.size + 1).toLong() else shoulder.id
        val stored = shoulder.copy(id = assignedId)
        shoulders[shoulder.uuid] = stored
        return assignedId
    }

    override suspend fun updateShoulder(shoulder: ShoulderEntity) {
        shoulders[shoulder.uuid] = shoulder
    }

    override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders[uuid]

    override suspend fun deleteShoulder(shoulder: ShoulderEntity) {
        shoulders.remove(shoulder.uuid)
    }

    override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders.values.toList())

    override suspend fun allShoulders(): List<ShoulderEntity> = shoulders.values.toList()

    override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = shoulders.values.filter { it.ownerId == ownerId }

    override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())

    override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders.values.find { it.id == id }

    override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.values.find { it.normalizedName == normalizedName }

    override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()

    override suspend fun setShoulderForNote(
        noteId: Long,
        shoulderId: Long?,
    ) {}

    override suspend fun claimGuestShoulders(targetOwnerId: String): Int = 0
}

open class UuidBaseFakeAttachmentDao : AttachmentDao {
    val attachments = mutableMapOf<String, NoteAttachmentEntity>()

    override suspend fun insert(attachment: NoteAttachmentEntity): Long {
        val assignedId = if (attachment.id == 0L) (attachments.size + 1).toLong() else attachment.id
        val stored = attachment.copy(id = assignedId)
        attachments[attachment.uuid] = stored
        return assignedId
    }

    override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) {
        attachments.forEach { insert(it) }
    }

    override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = attachments[uuid]

    override suspend fun getById(id: Long): NoteAttachmentEntity? = attachments.values.find { it.id == id }

    override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = attachments.values.filter { it.noteId == noteId }

    override suspend fun countByUri(uri: String): Int = attachments.values.count { it.uri == uri }

    override suspend fun deleteById(id: Long) {
        attachments.values.find { it.id == id }?.let { attachments.remove(it.uuid) }
    }

    override suspend fun deleteForNote(noteId: Long) {
        attachments.values.filter { it.noteId == noteId }.forEach { attachments.remove(it.uuid) }
    }

    override suspend fun claimGuestAttachments(targetOwnerId: String): Int = 0
}
