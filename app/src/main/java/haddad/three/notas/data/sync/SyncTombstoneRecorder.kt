package three.haddad.smartnotas.data.sync

import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for safely capturing entity deletions as tombstones so they can be
 * propagated during synchronization.
 */
interface SyncTombstoneRecorder {
    suspend fun recordNotesDeleted(notes: List<NoteWithItems>)
    suspend fun recordNoteDeleted(note: NoteWithItems)
    suspend fun recordChecklistItemDeleted(noteUuid: String, itemUuid: String)
    suspend fun recordAttachmentDeleted(noteUuid: String, attachmentUuid: String)
    suspend fun recordShoulderDeleted(shoulderUuid: String)
}

/**
 * Default implementation that scopes tombstones to the authenticated user.
 * For guest users, no tombstones are stored, preserving guest isolation.
 */
@Singleton
class DefaultSyncTombstoneRecorder @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncStateStore: SyncStateStore,
) : SyncTombstoneRecorder {

    override suspend fun recordNotesDeleted(notes: List<NoteWithItems>) {
        val user = authRepository.currentUser ?: return
        if (notes.isEmpty()) return

        val now = System.currentTimeMillis()
        val tombstones = mutableListOf<SyncTombstone>()

        for (noteWithItems in notes) {
            val noteUuid = noteWithItems.note.uuid
            tombstones.add(
                SyncTombstone(
                    entityType = "note",
                    uuid = noteUuid,
                    deletedAt = now,
                ),
            )
            for (item in noteWithItems.items) {
                tombstones.add(
                    SyncTombstone(
                        entityType = "checklist_item",
                        uuid = item.uuid,
                        deletedAt = now,
                        noteUuid = noteUuid,
                    ),
                )
            }
            for (attachment in noteWithItems.attachments) {
                tombstones.add(
                    SyncTombstone(
                        entityType = "attachment",
                        uuid = attachment.uuid,
                        deletedAt = now,
                        noteUuid = noteUuid,
                    ),
                )
            }
        }

        syncStateStore.addTombstones(user.uid, tombstones)
    }

    override suspend fun recordNoteDeleted(note: NoteWithItems) {
        recordNotesDeleted(listOf(note))
    }

    override suspend fun recordChecklistItemDeleted(noteUuid: String, itemUuid: String) {
        val user = authRepository.currentUser ?: return
        syncStateStore.addTombstones(
            user.uid,
            listOf(
                SyncTombstone(
                    entityType = "checklist_item",
                    uuid = itemUuid,
                    deletedAt = System.currentTimeMillis(),
                    noteUuid = noteUuid,
                ),
            ),
        )
    }

    override suspend fun recordAttachmentDeleted(noteUuid: String, attachmentUuid: String) {
        val user = authRepository.currentUser ?: return
        syncStateStore.addTombstones(
            user.uid,
            listOf(
                SyncTombstone(
                    entityType = "attachment",
                    uuid = attachmentUuid,
                    deletedAt = System.currentTimeMillis(),
                    noteUuid = noteUuid,
                ),
            ),
        )
    }

    override suspend fun recordShoulderDeleted(shoulderUuid: String) {
        val user = authRepository.currentUser ?: return
        syncStateStore.addTombstones(
            user.uid,
            listOf(
                SyncTombstone(
                    entityType = "category",
                    uuid = shoulderUuid,
                    deletedAt = System.currentTimeMillis(),
                ),
            ),
        )
    }
}
