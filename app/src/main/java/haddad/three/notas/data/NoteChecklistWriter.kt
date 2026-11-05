package haddad.three.notas.data

import haddad.three.notas.data.sync.change.LocalChangeRecorder
import java.util.UUID

/**
 * Persists the checklist rows of a list-kind note, including the two-pass remap that turns a
 * caller's [PersistableChecklistItem.localKey] pointers into the real Room ids Room hands back
 * on insert. Split out of [NoteRepository] so the hierarchy bookkeeping lives beside itself
 * instead of in the middle of note-level code, and so first-save and re-save share it.
 */
internal class NoteChecklistWriter(
    private val itemDao: ChecklistItemDao,
    private val changeRecorder: LocalChangeRecorder? = null,
    private val noteUuidResolver: (suspend (Long) -> String?)? = null,
) {
    /**
     * Writes a flat list of rows that already carry weighted [PersistableChecklistItem.sortOrder]
     * and [PersistableChecklistItem.parentLocalKey] relations. Works in two passes:
     *
     *  1. Insert every row with `parentId = null` so the table is always in a valid state, even
     *     if the caller ordered children before their parents.
     *  2. Re-update children with the freshly minted parent id resolved via [PersistableChecklistItem.localKey].
     *
     * Dangling pointers (children whose parent is missing from [items]) are left as top-level rows.
     */
    suspend fun insertHierarchy(
        noteId: Long,
        items: List<PersistableChecklistItem>,
        ownerId: String? = null,
        noteUuid: String? = null,
    ) {
        if (items.isEmpty()) return
        val resolvedNoteUuid = noteUuid ?: noteUuidResolver?.invoke(noteId) ?: ""
        val (parents, children) = items.partition { it.parentLocalKey == null }
        val keyToRealId = mutableMapOf<Long, Long>()

        // 1. Insert parents (depth 0)
        parents.forEach { draft ->
            val entity =
                ChecklistItemEntity(
                    id = 0,
                    uuid = draft.uuid ?: UUID.randomUUID().toString(),
                    ownerId = ownerId,
                    noteId = noteId,
                    text = draft.text,
                    details = draft.details,
                    checked = draft.checked,
                    sortOrder = draft.sortOrder,
                    parentId = null,
                    depth = 0,
                )
            val newId = itemDao.insert(entity)
            if (resolvedNoteUuid.isNotBlank()) {
                changeRecorder?.recordChecklistItemUpsert(entity.copy(id = newId), resolvedNoteUuid)
            }
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }

        // 2. Insert children (depth 1)
        children.forEach { draft ->
            val realParentId = draft.parentLocalKey?.let { keyToRealId[it] }
            val resolvedDepth = if (realParentId != null) draft.depth.coerceIn(0, 1) else 0
            val entity =
                ChecklistItemEntity(
                    id = 0,
                    uuid = draft.uuid ?: UUID.randomUUID().toString(),
                    ownerId = ownerId,
                    noteId = noteId,
                    text = draft.text,
                    details = draft.details,
                    checked = draft.checked,
                    sortOrder = draft.sortOrder,
                    parentId = realParentId,
                    depth = resolvedDepth,
                )
            val newId = itemDao.insert(entity)
            if (resolvedNoteUuid.isNotBlank()) {
                changeRecorder?.recordChecklistItemUpsert(entity.copy(id = newId), resolvedNoteUuid)
            }
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }
    }

    /**
     * Re-saves the rows of an existing list: updates rows the draft still references, inserts the
     * new ones, and deletes whatever the user removed while editing.
     */
    suspend fun syncHierarchy(
        noteId: Long,
        items: List<PersistableChecklistItem>,
        ownerId: String? = null,
        noteUuid: String? = null,
    ) {
        val resolvedNoteUuid = noteUuid ?: noteUuidResolver?.invoke(noteId) ?: ""
        val existingItems = itemDao.itemsFor(noteId)
        val existingById = existingItems.associateBy { it.id }

        // 1. Partition incoming items into parents and children
        val (parents, children) = items.partition { it.parentLocalKey == null }
        val keyToRealId = mutableMapOf<Long, Long>()

        // 2. Process parents (depth 0)
        parents.forEach { draft ->
            val existing = existingById[draft.localKey]
            if (existing != null) {
                val updated =
                    ChecklistItemEntity(
                        id = existing.id,
                        uuid = existing.uuid,
                        ownerId = existing.ownerId,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = null,
                        depth = 0,
                    )
                itemDao.update(updated)
                if (resolvedNoteUuid.isNotBlank()) {
                    changeRecorder?.recordChecklistItemUpsert(updated, resolvedNoteUuid)
                }
                keyToRealId[draft.localKey] = existing.id
            } else {
                val entity =
                    ChecklistItemEntity(
                        id = 0,
                        uuid = draft.uuid ?: UUID.randomUUID().toString(),
                        ownerId = ownerId ?: existingById.values.firstOrNull()?.ownerId,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = null,
                        depth = 0,
                    )
                val newId = itemDao.insert(entity)
                if (resolvedNoteUuid.isNotBlank()) {
                    changeRecorder?.recordChecklistItemUpsert(entity.copy(id = newId), resolvedNoteUuid)
                }
                keyToRealId[draft.localKey] = newId
            }
        }

        // 3. Process children (depth 1)
        children.forEach { draft ->
            val realParentId = draft.parentLocalKey?.let { keyToRealId[it] }
            val resolvedDepth = if (realParentId != null) draft.depth.coerceIn(0, 1) else 0

            val existing = existingById[draft.localKey]
            if (existing != null) {
                val updated =
                    ChecklistItemEntity(
                        id = existing.id,
                        uuid = existing.uuid,
                        ownerId = existing.ownerId,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = realParentId,
                        depth = resolvedDepth,
                    )
                itemDao.update(updated)
                if (resolvedNoteUuid.isNotBlank()) {
                    changeRecorder?.recordChecklistItemUpsert(updated, resolvedNoteUuid)
                }
                keyToRealId[draft.localKey] = existing.id
            } else {
                val entity =
                    ChecklistItemEntity(
                        id = 0,
                        uuid = draft.uuid ?: UUID.randomUUID().toString(),
                        ownerId = ownerId ?: existingById.values.firstOrNull()?.ownerId,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = realParentId,
                        depth = resolvedDepth,
                    )
                val newId = itemDao.insert(entity)
                if (resolvedNoteUuid.isNotBlank()) {
                    changeRecorder?.recordChecklistItemUpsert(entity.copy(id = newId), resolvedNoteUuid)
                }
                keyToRealId[draft.localKey] = newId
            }
        }

        // 4. Delete items that were in the database but are no longer in our saved set
        val savedRealIds = keyToRealId.values.toSet()
        existingItems.forEach { existing ->
            if (existing.id !in savedRealIds) {
                if (resolvedNoteUuid.isNotBlank()) {
                    changeRecorder?.recordChecklistItemDelete(existing.uuid, resolvedNoteUuid)
                }
                itemDao.deleteById(existing.id)
            }
        }
    }
}
