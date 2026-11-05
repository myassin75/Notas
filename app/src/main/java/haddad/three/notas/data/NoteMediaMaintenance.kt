package haddad.three.notas.data

import androidx.room.withTransaction
import haddad.three.notas.data.sync.change.LocalChangeRecorder

/**
 * Owns a note's picture and attachment rows plus the app-storage files behind them. Split out of
 * [NoteRepository] because every media write has the same tail: keep the searchable attachment
 * text in sync, then delete any app-stored file that no row references any more.
 */
internal class NoteMediaMaintenance(
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao,
    private val appMediaStorage: AppMediaStorage?,
    private val clock: () -> Long,
    private val database: RememberDatabase? = null,
    private val changeRecorder: LocalChangeRecorder? = null,
) {
    suspend fun addAttachment(
        noteId: Long,
        uri: String,
        displayName: String,
        mimeType: String?,
    ): Long {
        val parentNote = noteDao.get(noteId)?.note
        val parentOwnerId = parentNote?.ownerId
        val entity =
            NoteAttachmentEntity(
                noteId = noteId,
                ownerId = parentOwnerId,
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
            )
        val block: suspend () -> Long = {
            val attachmentId = attachmentDao.insert(entity)
            if (parentNote != null) {
                changeRecorder?.recordAttachmentUpsert(entity.copy(id = attachmentId), parentNote.uuid)
            }
            refreshAttachmentSearchText(noteId)
            attachmentId
        }
        return if (database != null) database.withTransaction { block() } else block()
    }

    suspend fun removeAttachment(id: Long) {
        val removedAttachment = attachmentDao.getById(id)
        val parentNote = removedAttachment?.noteId?.let { noteDao.get(it)?.note }
        val block: suspend () -> Unit = {
            if (removedAttachment != null && parentNote != null) {
                changeRecorder?.recordAttachmentDelete(removedAttachment.uuid, parentNote.uuid)
            }
            attachmentDao.deleteById(id)
            removedAttachment?.noteId?.let { noteId -> refreshAttachmentSearchText(noteId) }
        }
        if (database != null) database.withTransaction { block() } else block()
        cleanupUnreferencedMedia(listOfNotNull(removedAttachment?.uri))
    }

    suspend fun updatePictureUri(
        noteId: Long,
        pictureUri: String?,
    ) {
        val existing = noteDao.get(noteId)?.note ?: return
        val oldPictureUri = existing.pictureUri
        val updated =
            existing.copy(
                pictureUri = pictureUri,
                pictureHeroFraming = if (pictureUri == null) null else existing.pictureHeroFraming,
                updatedAt = clock(),
            )
        val block: suspend () -> Unit = {
            noteDao.update(updated)
            changeRecorder?.recordNoteUpsert(updated)
        }
        if (database != null) database.withTransaction { block() } else block()
        if (oldPictureUri != null && oldPictureUri != pictureUri) {
            cleanupUnreferencedMedia(listOf(oldPictureUri))
        }
    }

    suspend fun refreshAttachmentSearchText(noteId: Long) {
        val existing = noteDao.get(noteId)?.note ?: return
        val attachments = attachmentDao.attachmentsFor(noteId)
        noteDao.update(
            existing.copy(
                attachmentText = attachmentSearchText(attachments),
                updatedAt = clock(),
            ),
        )
    }

    suspend fun cleanupUnreferencedMedia(mediaUris: List<String>) {
        val storage = appMediaStorage ?: return
        mediaUris
            .distinct()
            .filter { uri -> storage.isAppStoredMediaUri(uri) }
            .forEach { uri ->
                val remainingReferences = noteDao.countPictureUri(uri) + attachmentDao.countByUri(uri)
                if (remainingReferences == 0) {
                    storage.deleteAppStoredMedia(uri)
                }
            }
    }
}

/** Every app-storage URI a note points at: its hero picture plus each attachment. */
internal fun NoteWithItems.mediaUris(): List<String> =
    buildList {
        note.pictureUri?.takeIf { uri -> uri.isNotBlank() }?.let { uri -> add(uri) }
        attachments.mapNotNullTo(this) { attachment -> attachment.uri.takeIf { uri -> uri.isNotBlank() } }
    }
