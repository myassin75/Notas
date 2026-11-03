package haddad.three.notas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import haddad.three.notas.data.sync.SyncDtoMapping
import java.util.UUID

class SyncDtoMappingTest {

    @Test
    fun shoulderToDto_mapsAllFieldsAndPreservesUuid() {
        val shoulder = ShoulderEntity(
            id = 42L,
            uuid = UUID.randomUUID().toString(),
            name = "Work Projects",
            normalizedName = "work projects",
            colorHex = "#3B82F6",
            createdAt = 1700000000000L,
            updatedAt = 1700001000000L,
        )

        val dto = SyncDtoMapping.shoulderToDto(shoulder)

        assertEquals(shoulder.uuid, dto.id)
        assertEquals("Work Projects", dto.name)
        assertEquals("work projects", dto.normalizedName)
        assertEquals("#3B82F6", dto.colorHex)
        assertFalse(dto.isDeleted)
        assertNotNull(dto.clientCreatedAt)
        assertNotNull(dto.clientUpdatedAt)
    }

    @Test
    fun checklistItemToDto_mapsParentUuidRelation() {
        val parentItem = ChecklistItemEntity(
            id = 100L,
            uuid = UUID.randomUUID().toString(),
            noteId = 1L,
            text = "Parent task",
            checked = false,
            sortOrder = 1.0,
            parentId = null,
            depth = 0,
            details = "Parent details",
        )
        val childItem = ChecklistItemEntity(
            id = 101L,
            uuid = UUID.randomUUID().toString(),
            noteId = 1L,
            text = "Child subtask",
            checked = true,
            sortOrder = 2.0,
            parentId = parentItem.id,
            depth = 1,
            details = "Child details",
        )

        val parentDto = SyncDtoMapping.checklistItemToDto(parentItem, parentUuid = null)
        val childDto = SyncDtoMapping.checklistItemToDto(childItem, parentUuid = parentItem.uuid)

        assertEquals(parentItem.uuid, parentDto.id)
        assertNull(parentDto.parentId)
        assertEquals(0, parentDto.depth)
        assertFalse(parentDto.checked)

        assertEquals(childItem.uuid, childDto.id)
        assertEquals(parentItem.uuid, childDto.parentId)
        assertEquals(1, childDto.depth)
        assertTrue(childDto.checked)
    }

    @Test
    fun noteWithItemsToDto_mapsRelationshipsToUuids() {
        val shoulder = ShoulderEntity(
            id = 10L,
            uuid = UUID.randomUUID().toString(),
            name = "Personal",
            normalizedName = "personal",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
        )

        val note = NoteEntity(
            id = 1L,
            uuid = UUID.randomUUID().toString(),
            kind = NoteKind.LIST,
            title = "Trip Prep",
            body = "Pack essentials",
            colorIndex = 3,
            shoulderId = shoulder.id,
            trashed = false,
            createdAt = 1700000000000L,
            updatedAt = 1700002000000L,
            reminderAt = 1700010000000L,
            importance = Importance.HIGH,
            visibility = Visibility.DEFAULT,
        )

        val parentItem = ChecklistItemEntity(
            id = 201L,
            uuid = UUID.randomUUID().toString(),
            noteId = note.id,
            text = "Passport",
            checked = true,
            sortOrder = 1.0,
            parentId = null,
            depth = 0,
        )

        val childItem = ChecklistItemEntity(
            id = 202L,
            uuid = UUID.randomUUID().toString(),
            noteId = note.id,
            text = "Check visa pages",
            checked = false,
            sortOrder = 2.0,
            parentId = parentItem.id,
            depth = 1,
        )

        val attachment = NoteAttachmentEntity(
            id = 301L,
            uuid = UUID.randomUUID().toString(),
            noteId = note.id,
            uri = "content://media/tickets.pdf",
            displayName = "Tickets.pdf",
            mimeType = "application/pdf",
        )

        val noteWithItems = NoteWithItems(
            note = note,
            items = listOf(parentItem, childItem),
            attachments = listOf(attachment),
            shoulder = shoulder,
        )

        val dto = SyncDtoMapping.noteWithItemsToDto(noteWithItems)

        assertEquals(note.uuid, dto.id)
        assertEquals(shoulder.uuid, dto.categoryId)
        assertEquals("LIST", dto.kind)
        assertEquals("Trip Prep", dto.title)
        assertEquals("Pack essentials", dto.body)
        assertEquals(3, dto.colorIndex)
        assertEquals("HIGH", dto.importance)
        assertEquals(2, dto.checklistItems.size)
        assertEquals(1, dto.attachments.size)

        // Verify checklist hierarchy mapped to parent's UUID
        val childItemDto = dto.checklistItems.first { it.id == childItem.uuid }
        assertEquals(parentItem.uuid, childItemDto.parentId)

        // Verify attachment UUID
        val attachmentDto = dto.attachments.first()
        assertEquals(attachment.uuid, attachmentDto.id)
        assertEquals("Tickets.pdf", attachmentDto.displayName)
    }

    @Test
    fun timestampConversion_formatsAndParsesIso8601() {
        val millis = 1718000000000L
        val iso = SyncDtoMapping.formatInstant(millis)
        assertNotNull(iso)

        val parsed = SyncDtoMapping.parseInstant(iso)
        assertEquals(millis, parsed)

        assertNull(SyncDtoMapping.formatInstant(null))
        assertNull(SyncDtoMapping.formatInstant(0L))
        assertNull(SyncDtoMapping.parseInstant(null))
        assertNull(SyncDtoMapping.parseInstant(""))
    }
}
