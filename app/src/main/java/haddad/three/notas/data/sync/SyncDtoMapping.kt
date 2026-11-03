package haddad.three.notas.data.sync

import kotlinx.serialization.Serializable
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.Visibility
import java.time.Instant
import java.time.format.DateTimeFormatter

@Serializable
data class CategorySyncDto(
    val id: String,
    val name: String,
    val normalizedName: String,
    val colorHex: String? = null,
    val clientCreatedAt: String,
    val clientUpdatedAt: String,
    val isDeleted: Boolean = false,
)

@Serializable
data class ChecklistItemSyncDto(
    val id: String,
    val text: String,
    val checked: Boolean = false,
    val sortOrder: Double = 0.0,
    val parentId: String? = null,
    val depth: Int = 0,
    val details: String = "",
    val isDeleted: Boolean = false,
)

@Serializable
data class AttachmentSyncDto(
    val id: String,
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val storageKey: String? = null,
    val isDeleted: Boolean = false,
)

@Serializable
data class NoteSyncDto(
    val id: String,
    val categoryId: String? = null,
    val kind: String,
    val title: String,
    val body: String = "",
    val colorIndex: Int = 0,
    val importance: String = "DEFAULT",
    val visibility: String = "DEFAULT",
    val locked: Boolean = false,
    val iconKey: String? = null,
    val pictureUri: String? = null,
    val pictureHeroFraming: String? = null,
    val reminderAt: String? = null,
    val endAt: String? = null,
    val isAllDay: Boolean = false,
    val pinnedAt: String? = null,
    val completedAt: String? = null,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val trashedAt: String? = null,
    val clientCreatedAt: String,
    val clientUpdatedAt: String,
    val isDeleted: Boolean = false,
    val checklistItems: List<ChecklistItemSyncDto> = emptyList(),
    val attachments: List<AttachmentSyncDto> = emptyList(),
)

object SyncDtoMapping {
    private val formatter = DateTimeFormatter.ISO_INSTANT

    fun formatInstant(epochMilli: Long?): String? {
        if (epochMilli == null || epochMilli <= 0L) return null
        return runCatching { formatter.format(Instant.ofEpochMilli(epochMilli)) }.getOrNull()
    }

    fun parseInstant(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return runCatching { Instant.parse(isoString).toEpochMilli() }.getOrNull()
    }

    fun shoulderToDto(shoulder: ShoulderEntity, isDeleted: Boolean = false): CategorySyncDto =
        CategorySyncDto(
            id = shoulder.uuid,
            name = shoulder.name,
            normalizedName = shoulder.normalizedName,
            colorHex = shoulder.colorHex,
            clientCreatedAt = formatInstant(shoulder.createdAt) ?: formatter.format(Instant.now()),
            clientUpdatedAt = formatInstant(shoulder.updatedAt) ?: formatter.format(Instant.now()),
            isDeleted = isDeleted,
        )

    fun checklistItemToDto(
        item: ChecklistItemEntity,
        parentUuid: String? = null,
        isDeleted: Boolean = false,
    ): ChecklistItemSyncDto =
        ChecklistItemSyncDto(
            id = item.uuid,
            text = item.text,
            checked = item.checked,
            sortOrder = item.sortOrder,
            parentId = parentUuid,
            depth = item.depth,
            details = item.details,
            isDeleted = isDeleted,
        )

    fun attachmentToDto(
        attachment: NoteAttachmentEntity,
        isDeleted: Boolean = false,
    ): AttachmentSyncDto =
        AttachmentSyncDto(
            id = attachment.uuid,
            uri = attachment.uri,
            displayName = attachment.displayName,
            mimeType = attachment.mimeType,
            isDeleted = isDeleted,
        )

    fun noteWithItemsToDto(
        noteWithItems: NoteWithItems,
        isDeleted: Boolean = false,
    ): NoteSyncDto {
        val note = noteWithItems.note
        val itemsById = noteWithItems.items.associateBy { it.id }

        val itemsDto =
            noteWithItems.items.map { item ->
                val parentUuid = item.parentId?.let { itemsById[it]?.uuid }
                checklistItemToDto(item, parentUuid = parentUuid)
            }

        val attachmentsDto =
            noteWithItems.attachments.map { attachment ->
                attachmentToDto(attachment)
            }

        return NoteSyncDto(
            id = note.uuid,
            categoryId = noteWithItems.shoulder?.uuid,
            kind = note.kind.name,
            title = note.title,
            body = note.body,
            colorIndex = note.colorIndex,
            importance = note.importance.name,
            visibility = note.visibility.name,
            locked = note.locked,
            iconKey = note.iconKey,
            pictureUri = note.pictureUri,
            pictureHeroFraming = note.pictureHeroFraming,
            reminderAt = formatInstant(note.reminderAt),
            endAt = formatInstant(note.endAt),
            isAllDay = note.isAllDay,
            pinnedAt = formatInstant(note.pinnedAt),
            completedAt = formatInstant(note.completedAt),
            archived = note.archived,
            trashed = note.trashed,
            trashedAt = formatInstant(note.trashedAt),
            clientCreatedAt = formatInstant(note.createdAt) ?: formatter.format(Instant.now()),
            clientUpdatedAt = formatInstant(note.updatedAt) ?: formatter.format(Instant.now()),
            isDeleted = isDeleted,
            checklistItems = itemsDto,
            attachments = attachmentsDto,
        )
    }

    fun dtoToShoulder(
        dto: CategorySyncDto,
        existingId: Long = 0L,
        ownerId: String? = null,
    ): ShoulderEntity =
        ShoulderEntity(
            id = existingId,
            uuid = dto.id,
            ownerId = ownerId,
            name = dto.name,
            normalizedName = dto.normalizedName,
            colorHex = dto.colorHex,
            createdAt = parseInstant(dto.clientCreatedAt) ?: System.currentTimeMillis(),
            updatedAt = parseInstant(dto.clientUpdatedAt) ?: System.currentTimeMillis(),
        )

    fun dtoToNote(
        dto: NoteSyncDto,
        existingId: Long = 0L,
        shoulderId: Long? = null,
        checklistText: String = "",
        attachmentText: String = "",
        actionsText: String = "",
        ownerId: String? = null,
    ): NoteEntity =
        NoteEntity(
            id = existingId,
            uuid = dto.id,
            ownerId = ownerId,
            kind = runCatching { NoteKind.valueOf(dto.kind) }.getOrDefault(NoteKind.NOTE),
            title = dto.title,
            body = dto.body,
            checklistText = checklistText,
            attachmentText = attachmentText,
            actionsText = actionsText,
            colorIndex = dto.colorIndex,
            trashed = dto.trashed,
            createdAt = parseInstant(dto.clientCreatedAt) ?: System.currentTimeMillis(),
            updatedAt = parseInstant(dto.clientUpdatedAt) ?: System.currentTimeMillis(),
            reminderAt = parseInstant(dto.reminderAt),
            endAt = parseInstant(dto.endAt),
            isAllDay = dto.isAllDay,
            importance = runCatching { Importance.valueOf(dto.importance) }.getOrDefault(Importance.DEFAULT),
            visibility = runCatching { Visibility.valueOf(dto.visibility) }.getOrDefault(Visibility.DEFAULT),
            pictureUri = dto.pictureUri,
            pictureHeroFraming = dto.pictureHeroFraming,
            locked = dto.locked,
            iconKey = dto.iconKey,
            actions = emptyList(),
            recurrence = null,
            reminders = emptyList(),
            shoulderId = shoulderId,
            archived = dto.archived,
            trashedAt = parseInstant(dto.trashedAt),
            completedAt = parseInstant(dto.completedAt),
            pinnedAt = parseInstant(dto.pinnedAt),
        )

    fun dtoToChecklistItem(
        dto: ChecklistItemSyncDto,
        existingId: Long = 0L,
        noteId: Long,
        parentId: Long? = null,
        ownerId: String? = null,
    ): ChecklistItemEntity =
        ChecklistItemEntity(
            id = existingId,
            uuid = dto.id,
            ownerId = ownerId,
            noteId = noteId,
            text = dto.text,
            checked = dto.checked,
            sortOrder = dto.sortOrder,
            parentId = parentId,
            depth = dto.depth,
            details = dto.details,
        )

    fun dtoToAttachment(
        dto: AttachmentSyncDto,
        existingId: Long = 0L,
        noteId: Long,
        ownerId: String? = null,
    ): NoteAttachmentEntity =
        NoteAttachmentEntity(
            id = existingId,
            uuid = dto.id,
            ownerId = ownerId,
            noteId = noteId,
            uri = dto.uri,
            displayName = dto.displayName,
            mimeType = dto.mimeType,
        )
}
