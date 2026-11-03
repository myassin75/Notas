package haddad.three.notas.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BackupJsonCodecUuidTest {

    @Test
    fun encodeNoteForBackup_includesUuidsForNoteAndChildren() {
        val note = NoteEntity(
            id = 55L,
            uuid = UUID.randomUUID().toString(),
            kind = NoteKind.LIST,
            title = "Test UUID Note",
            body = "Body content",
            colorIndex = 1,
            trashed = false,
            createdAt = 1700000000000L,
            updatedAt = 1700001000000L,
        )
        val item = ChecklistItemEntity(
            id = 101L,
            uuid = UUID.randomUUID().toString(),
            noteId = 55L,
            text = "Checklist item",
            checked = false,
            sortOrder = 1.0,
        )
        val attachment = NoteAttachmentEntity(
            id = 201L,
            uuid = UUID.randomUUID().toString(),
            noteId = 55L,
            uri = "content://media/1",
            displayName = "doc.pdf",
        )

        val noteWithItems = NoteWithItems(
            note = note,
            items = listOf(item),
            attachments = listOf(attachment),
        )

        val json = encodeNoteForBackup(noteWithItems)

        assertEquals(note.uuid, json.getString("uuid"))
        assertEquals(note.id, json.getLong("id"))

        val itemsJson = json.getJSONArray("items")
        assertEquals(1, itemsJson.length())
        assertEquals(item.uuid, itemsJson.getJSONObject(0).getString("uuid"))

        val attachmentsJson = json.getJSONArray("attachments")
        assertEquals(1, attachmentsJson.length())
        assertEquals(attachment.uuid, attachmentsJson.getJSONObject(0).getString("uuid"))
    }

    @Test
    fun decodeNoteEntity_preservesExistingUuid() {
        val expectedUuid = UUID.randomUUID().toString()
        val json = JSONObject().apply {
            put("id", 12L)
            put("uuid", expectedUuid)
            put("kind", "NOTE")
            put("title", "Existing Note")
            put("body", "Content")
            put("createdAt", 1700000000000L)
            put("updatedAt", 1700001000000L)
        }

        val decoded = decodeNoteEntity(json)

        assertEquals(expectedUuid, decoded.uuid)
        assertEquals(12L, decoded.id)
    }

    @Test
    fun decodeNoteEntity_generatesFreshUuidWhenMissingInLegacyBackup() {
        val json = JSONObject().apply {
            put("id", 15L)
            put("kind", "NOTE")
            put("title", "Legacy Note Without UUID")
            put("body", "Legacy content")
            put("createdAt", 1700000000000L)
            put("updatedAt", 1700001000000L)
        }

        val decoded = decodeNoteEntity(json)

        assertNotNull(decoded.uuid)
        assertTrue(decoded.uuid.isNotBlank())
        // Verify it is a valid UUID
        val parsedUuid = UUID.fromString(decoded.uuid)
        assertNotNull(parsedUuid)
    }

    @Test
    fun decodeChecklistItems_preservesUuidOrGeneratesFresh() {
        val itemUuid = UUID.randomUUID().toString()
        val root = JSONObject().apply {
            put("items", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("uuid", itemUuid)
                    put("text", "Item with UUID")
                    put("sortOrder", 1.0)
                })
                put(JSONObject().apply {
                    put("id", 2L)
                    put("text", "Legacy Item without UUID")
                    put("sortOrder", 2.0)
                })
            })
        }

        val items = decodeChecklistItems(root)

        assertEquals(2, items.size)
        assertEquals(itemUuid, items[0].uuid)
        assertNotNull(items[1].uuid)
        assertTrue(items[1].uuid.isNotBlank())
        assertNotEquals(items[0].uuid, items[1].uuid)
    }

    @Test
    fun decodeAttachmentMetas_preservesUuidOrGeneratesFresh() {
        val attachmentUuid = UUID.randomUUID().toString()
        val root = JSONObject().apply {
            put("attachments", JSONArray().apply {
                put(JSONObject().apply {
                    put("uuid", attachmentUuid)
                    put("uri", "file:///test1.jpg")
                    put("displayName", "test1.jpg")
                })
                put(JSONObject().apply {
                    put("uri", "file:///test2.jpg")
                    put("displayName", "test2.jpg")
                })
            })
        }

        val attachments = decodeAttachmentMetas(root)

        assertEquals(2, attachments.size)
        assertEquals(attachmentUuid, attachments[0].uuid)
        assertNotNull(attachments[1].uuid)
        assertTrue(attachments[1].uuid.isNotBlank())
        assertNotEquals(attachments[0].uuid, attachments[1].uuid)
    }

    @Test
    fun encodeAndDecodeNote_preservesOwnerIdWhenPresent() {
        val note = NoteEntity(
            id = 77L,
            uuid = "note-uuid-77",
            ownerId = "user_firebase_123",
            kind = NoteKind.NOTE,
            title = "Owned Note",
            body = "Secret",
            colorIndex = 2,
            trashed = false,
            createdAt = 1000L,
            updatedAt = 2000L,
        )
        val noteWithItems = NoteWithItems(
            note = note,
            items = listOf(ChecklistItemEntity(id = 1L, uuid = "item-1", noteId = 77L, ownerId = "user_firebase_123", text = "task", checked = false, sortOrder = 0.0)),
            attachments = listOf(NoteAttachmentEntity(id = 2L, uuid = "att-1", noteId = 77L, ownerId = "user_firebase_123", uri = "uri", displayName = "pic.jpg")),
        )

        val json = encodeNoteForBackup(noteWithItems)
        assertEquals("user_firebase_123", json.optString("ownerId"))

        val decoded = decodeNoteEntity(json)
        assertEquals("user_firebase_123", decoded.ownerId)

        val decodedItems = decodeChecklistItems(json)
        assertEquals("user_firebase_123", decodedItems[0].ownerId)

        val decodedAtts = decodeAttachmentMetas(json)
        assertEquals("user_firebase_123", decodedAtts[0].ownerId)
    }

    @Test
    fun decodeNoteEntity_legacyBackupWithoutOwnerIdDecodesWithNullOwnerId() {
        val json = JSONObject().apply {
            put("id", 88L)
            put("uuid", "legacy-uuid")
            put("kind", "NOTE")
            put("title", "Legacy Note")
            put("body", "Legacy Body")
            put("createdAt", 1000L)
            put("updatedAt", 2000L)
        }

        val decoded = decodeNoteEntity(json)
        org.junit.Assert.assertNull("Legacy backup must decode with ownerId = null", decoded.ownerId)
    }

    @Test
    fun encodeAndDecodeShoulder_preservesAllFieldsAndOwnerId() {
        val shoulder = ShoulderEntity(
            id = 5L,
            uuid = "cat-uuid-5",
            ownerId = "user_firebase_123",
            name = "Work",
            normalizedName = "work",
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        val json = encodeShoulderForBackup(shoulder)
        assertEquals("user_firebase_123", json.getString("ownerId"))
        assertEquals("cat-uuid-5", json.getString("uuid"))

        val decoded = decodeShoulderEntity(json)
        assertEquals(shoulder.id, decoded.id)
        assertEquals(shoulder.uuid, decoded.uuid)
        assertEquals(shoulder.ownerId, decoded.ownerId)
        assertEquals(shoulder.name, decoded.name)
        assertEquals(shoulder.normalizedName, decoded.normalizedName)
    }
}

