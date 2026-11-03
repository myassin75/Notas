package haddad.three.notas.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.Visibility
import java.time.Instant

class SyncDtoMappingExtendedTest {

    @Test
    fun `dtoToShoulder maps correctly and preserves existing Long id`() {
        val nowIso = Instant.now().toString()
        val dto = CategorySyncDto(
            id = "cat-uuid-123",
            name = "Work",
            normalizedName = "work",
            colorHex = "#FF5733",
            clientCreatedAt = nowIso,
            clientUpdatedAt = nowIso,
            isDeleted = false,
        )

        val shoulder = SyncDtoMapping.dtoToShoulder(dto, existingId = 99L)

        assertEquals(99L, shoulder.id)
        assertEquals("cat-uuid-123", shoulder.uuid)
        assertEquals("Work", shoulder.name)
        assertEquals("work", shoulder.normalizedName)
        assertEquals("#FF5733", shoulder.colorHex)
        assertTrue(shoulder.createdAt > 0L)
        assertTrue(shoulder.updatedAt > 0L)
    }

    @Test
    fun `dtoToNote maps all fields and preserves existing Long id and relationships`() {
        val createdIso = "2026-01-01T10:00:00Z"
        val updatedIso = "2026-01-02T12:00:00Z"
        val reminderIso = "2026-01-03T15:00:00Z"
        val endIso = "2026-01-03T16:00:00Z"
        val pinnedIso = "2026-01-02T12:30:00Z"
        val completedIso = "2026-01-04T09:00:00Z"

        val dto = NoteSyncDto(
            id = "note-uuid-456",
            categoryId = "cat-uuid-123",
            kind = "NOTE",
            title = "Test Note",
            body = "Test Body",
            colorIndex = 3,
            importance = "CRITICAL",
            visibility = "PRIVATE",
            locked = true,
            iconKey = "work_icon",
            pictureUri = "file:///data/user/0/picture.png",
            pictureHeroFraming = "center",
            reminderAt = reminderIso,
            endAt = endIso,
            isAllDay = false,
            pinnedAt = pinnedIso,
            completedAt = completedIso,
            archived = true,
            trashed = false,
            trashedAt = null,
            clientCreatedAt = createdIso,
            clientUpdatedAt = updatedIso,
            isDeleted = false,
        )

        val entity = SyncDtoMapping.dtoToNote(
            dto = dto,
            existingId = 42L,
            shoulderId = 77L,
            checklistText = "item1 item2",
            attachmentText = "report.pdf",
            actionsText = "call alice",
        )

        assertEquals(42L, entity.id)
        assertEquals("note-uuid-456", entity.uuid)
        assertEquals(77L, entity.shoulderId)
        assertEquals(NoteKind.NOTE, entity.kind)
        assertEquals("Test Note", entity.title)
        assertEquals("Test Body", entity.body)
        assertEquals("item1 item2", entity.checklistText)
        assertEquals("report.pdf", entity.attachmentText)
        assertEquals("call alice", entity.actionsText)
        assertEquals(3, entity.colorIndex)
        assertEquals(Importance.CRITICAL, entity.importance)
        assertEquals(Visibility.PRIVATE, entity.visibility)
        assertTrue(entity.locked)
        assertEquals("work_icon", entity.iconKey)
        assertEquals("file:///data/user/0/picture.png", entity.pictureUri)
        assertEquals("center", entity.pictureHeroFraming)
        assertEquals(Instant.parse(reminderIso).toEpochMilli(), entity.reminderAt)
        assertEquals(Instant.parse(endIso).toEpochMilli(), entity.endAt)
        assertFalse(entity.isAllDay)
        assertEquals(Instant.parse(pinnedIso).toEpochMilli(), entity.pinnedAt)
        assertEquals(Instant.parse(completedIso).toEpochMilli(), entity.completedAt)
        assertTrue(entity.archived)
        assertFalse(entity.trashed)
        assertNull(entity.trashedAt)
    }

    @Test
    fun `dtoToChecklistItem maps correctly and preserves parentId`() {
        val dto = ChecklistItemSyncDto(
            id = "item-uuid-789",
            text = "Buy Groceries",
            checked = true,
            sortOrder = 1.5,
            parentId = "parent-uuid-001",
            depth = 1,
            details = "Fresh milk and eggs",
            isDeleted = false,
        )

        val entity = SyncDtoMapping.dtoToChecklistItem(
            dto = dto,
            existingId = 15L,
            noteId = 42L,
            parentId = 10L,
        )

        assertEquals(15L, entity.id)
        assertEquals("item-uuid-789", entity.uuid)
        assertEquals(42L, entity.noteId)
        assertEquals(10L, entity.parentId)
        assertEquals("Buy Groceries", entity.text)
        assertTrue(entity.checked)
        assertEquals(1.5, entity.sortOrder, 0.001)
        assertEquals(1, entity.depth)
        assertEquals("Fresh milk and eggs", entity.details)
    }

    @Test
    fun `dtoToAttachment maps correctly`() {
        val dto = AttachmentSyncDto(
            id = "att-uuid-111",
            uri = "content://media/external/123",
            displayName = "Receipt.jpg",
            mimeType = "image/jpeg",
        )

        val entity = SyncDtoMapping.dtoToAttachment(
            dto = dto,
            existingId = 88L,
            noteId = 42L,
        )

        assertEquals(88L, entity.id)
        assertEquals("att-uuid-111", entity.uuid)
        assertEquals(42L, entity.noteId)
        assertEquals("content://media/external/123", entity.uri)
        assertEquals("Receipt.jpg", entity.displayName)
        assertEquals("image/jpeg", entity.mimeType)
    }

    @Test
    fun `roundtrip noteWithItemsToDto and back preserves data`() {
        val shoulder = ShoulderEntity(
            id = 5L,
            uuid = "shoulder-uuid-1",
            name = "Personal",
            normalizedName = "personal",
            colorHex = "#00FF00",
            createdAt = 1700000000000L,
            updatedAt = 1700001000000L,
        )

        val note = NoteEntity(
            id = 10L,
            uuid = "note-uuid-2",
            kind = NoteKind.LIST,
            title = "Weekend Tasks",
            body = "",
            colorIndex = 1,
            trashed = false,
            createdAt = 1700002000000L,
            updatedAt = 1700003000000L,
            shoulderId = shoulder.id,
            importance = Importance.HIGH,
            visibility = Visibility.DEFAULT,
        )

        val item1 = ChecklistItemEntity(
            id = 20L,
            uuid = "item-uuid-parent",
            noteId = note.id,
            text = "Outdoor",
            checked = false,
            sortOrder = 0.0,
            parentId = null,
            depth = 0,
        )

        val item2 = ChecklistItemEntity(
            id = 21L,
            uuid = "item-uuid-child",
            noteId = note.id,
            text = "Mow lawn",
            checked = true,
            sortOrder = 1.0,
            parentId = item1.id,
            depth = 1,
        )

        val attachment = NoteAttachmentEntity(
            id = 30L,
            uuid = "att-uuid-1",
            noteId = note.id,
            uri = "file:///data/user/0/lawn.png",
            displayName = "lawn.png",
            mimeType = "image/png",
        )

        val noteWithItems = NoteWithItems(
            note = note,
            items = listOf(item1, item2),
            attachments = listOf(attachment),
            shoulder = shoulder,
        )

        val dto = SyncDtoMapping.noteWithItemsToDto(noteWithItems)

        assertEquals("note-uuid-2", dto.id)
        assertEquals("shoulder-uuid-1", dto.categoryId)
        assertEquals(2, dto.checklistItems.size)
        assertEquals("item-uuid-parent", dto.checklistItems[0].id)
        assertNull(dto.checklistItems[0].parentId)
        assertEquals("item-uuid-child", dto.checklistItems[1].id)
        assertEquals("item-uuid-parent", dto.checklistItems[1].parentId)
        assertEquals(1, dto.attachments.size)
        assertEquals("att-uuid-1", dto.attachments[0].id)
    }
}
