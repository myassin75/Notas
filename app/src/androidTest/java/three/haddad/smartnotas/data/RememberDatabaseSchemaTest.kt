package three.haddad.smartnotas.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RememberDatabaseSchemaTest {
    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RememberDatabase::class.java,
        )

    @Test
    fun migration_from_version_1_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_1_DATABASE_NAME, 1).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_1_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_1_2,
            RememberDatabase.MIGRATION_2_3,
            RememberDatabase.MIGRATION_3_4,
            RememberDatabase.MIGRATION_4_5,
            RememberDatabase.MIGRATION_5_6,
            RememberDatabase.MIGRATION_6_7,
            RememberDatabase.MIGRATION_7_8,
        )
    }

    @Test
    fun migration_from_version_2_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_2_DATABASE_NAME, 2).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_2_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_2_3,
            RememberDatabase.MIGRATION_3_4,
            RememberDatabase.MIGRATION_4_5,
            RememberDatabase.MIGRATION_5_6,
            RememberDatabase.MIGRATION_6_7,
            RememberDatabase.MIGRATION_7_8,
        )
    }

    @Test
    fun migration_from_version_3_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_3_DATABASE_NAME, 3).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_3_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_3_4,
            RememberDatabase.MIGRATION_4_5,
            RememberDatabase.MIGRATION_5_6,
            RememberDatabase.MIGRATION_6_7,
            RememberDatabase.MIGRATION_7_8,
        )
    }

    @Test
    fun migration_from_version_4_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_4_DATABASE_NAME, 4).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_4_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_4_5,
            RememberDatabase.MIGRATION_5_6,
            RememberDatabase.MIGRATION_6_7,
            RememberDatabase.MIGRATION_7_8,
        )
    }

    @Test
    fun migration_from_version_5_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_5_DATABASE_NAME, 5).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_5_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_5_6,
            RememberDatabase.MIGRATION_6_7,
            RememberDatabase.MIGRATION_7_8,
        )
    }

    @Test
    fun migration_from_version_6_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_6_DATABASE_NAME, 6).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_6_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_6_7,
            RememberDatabase.MIGRATION_7_8,
            RememberDatabase.MIGRATION_8_9,
        )
    }

    @Test
    fun migration_from_version_7_to_8_preserves_populated_data_and_adds_uuids() {
        val dbV7 = migrationHelper.createDatabase(VERSION_7_DATABASE_NAME, 7)

        // Populate database with version 7 schema
        dbV7.execSQL(
            "INSERT INTO shoulders (id, name, normalizedName, colorHex, createdAt, updatedAt) " +
                "VALUES (1, 'Work', 'work', '#4F46E5', 1700000000000, 1700000000000)",
        )
        dbV7.execSQL(
            "INSERT INTO notes (id, kind, title, body, checklistText, attachmentText, actionsText, colorIndex, trashed, createdAt, updatedAt, reminderAt, endAt, isAllDay, importance, visibility, locked, actions, reminders, shoulderId, archived) " +
                "VALUES (10, 'NOTE', 'Q4 Strategy', 'Launch plan', '', '', '', 2, 0, 1700000000000, 1700000000000, NULL, NULL, 0, 'DEFAULT', 'DEFAULT', 0, '[]', '[]', 1, 0)",
        )
        dbV7.execSQL(
            "INSERT INTO checklist_items (id, noteId, text, checked, sortOrder, depth, details) " +
                "VALUES (100, 10, 'Finalize deck', 0, 1.0, 0, '')",
        )
        dbV7.execSQL(
            "INSERT INTO attachments (id, noteId, uri, displayName, mimeType) " +
                "VALUES (200, 10, 'content://media/deck.pdf', 'deck.pdf', 'application/pdf')",
        )
        dbV7.close()

        val dbV9 =
            migrationHelper.runMigrationsAndValidate(
                VERSION_7_DATABASE_NAME,
                CURRENT_VERSION,
                true,
                RememberDatabase.MIGRATION_7_8,
                RememberDatabase.MIGRATION_8_9,
            )

        // Verify shoulders preservation & UUID
        dbV9.query("SELECT id, name, uuid FROM shoulders WHERE id = 1").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals("Work", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            val shoulderUuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"))
            org.junit.Assert.assertNotNull(shoulderUuid)
            org.junit.Assert.assertTrue(shoulderUuid.isNotBlank())
            java.util.UUID.fromString(shoulderUuid) // Valid UUID format
        }

        // Verify notes preservation, relationships & UUID
        dbV9.query("SELECT id, title, shoulderId, uuid FROM notes WHERE id = 10").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals("Q4 Strategy", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            org.junit.Assert.assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("shoulderId")))
            val noteUuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"))
            org.junit.Assert.assertNotNull(noteUuid)
            org.junit.Assert.assertTrue(noteUuid.isNotBlank())
            java.util.UUID.fromString(noteUuid)
        }

        // Verify checklist item preservation & UUID
        dbV9.query("SELECT id, noteId, text, uuid FROM checklist_items WHERE id = 100").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(100L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("noteId")))
            org.junit.Assert.assertEquals("Finalize deck", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            val itemUuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"))
            org.junit.Assert.assertNotNull(itemUuid)
            org.junit.Assert.assertTrue(itemUuid.isNotBlank())
            java.util.UUID.fromString(itemUuid)
        }

        // Verify attachment preservation & UUID
        dbV9.query("SELECT id, noteId, displayName, uuid FROM attachments WHERE id = 200").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(200L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("noteId")))
            org.junit.Assert.assertEquals("deck.pdf", cursor.getString(cursor.getColumnIndexOrThrow("displayName")))
            val attachmentUuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"))
            org.junit.Assert.assertNotNull(attachmentUuid)
            org.junit.Assert.assertTrue(attachmentUuid.isNotBlank())
            java.util.UUID.fromString(attachmentUuid)
        }

        dbV9.close()
    }

    @Test
    fun migration_from_version_8_to_9_preserves_populated_data_and_adds_ownerId() {
        val dbV8 = migrationHelper.createDatabase(VERSION_8_DATABASE_NAME, 8)

        // Populate database with version 8 schema
        dbV8.execSQL(
            "INSERT INTO shoulders (id, uuid, name, normalizedName, colorHex, createdAt, updatedAt) " +
                "VALUES (1, 'sh-uuid-1', 'Work', 'work', '#4F46E5', 1700000000000, 1700000000000)",
        )
        dbV8.execSQL(
            "INSERT INTO notes (id, uuid, kind, title, body, checklistText, attachmentText, actionsText, colorIndex, trashed, createdAt, updatedAt, reminderAt, endAt, isAllDay, importance, visibility, locked, actions, reminders, shoulderId, archived) " +
                "VALUES (10, 'note-uuid-1', 'NOTE', 'Q4 Strategy', 'Launch plan', '', '', '', 2, 0, 1700000000000, 1700000000000, NULL, NULL, 0, 'DEFAULT', 'DEFAULT', 0, '[]', '[]', 1, 0)",
        )
        dbV8.execSQL(
            "INSERT INTO checklist_items (id, uuid, noteId, text, checked, sortOrder, depth, details) " +
                "VALUES (100, 'item-uuid-1', 10, 'Finalize deck', 0, 1.0, 0, '')",
        )
        dbV8.execSQL(
            "INSERT INTO attachments (id, uuid, noteId, uri, displayName, mimeType) " +
                "VALUES (200, 'att-uuid-1', 10, 'content://media/deck.pdf', 'deck.pdf', 'application/pdf')",
        )
        dbV8.close()

        val dbV9 =
            migrationHelper.runMigrationsAndValidate(
                VERSION_8_DATABASE_NAME,
                CURRENT_VERSION,
                true,
                RememberDatabase.MIGRATION_8_9,
            )

        // Verify shoulders preservation & nullable ownerId
        dbV9.query("SELECT id, name, uuid, ownerId FROM shoulders WHERE id = 1").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals("Work", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            org.junit.Assert.assertEquals("sh-uuid-1", cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
            org.junit.Assert.assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("ownerId")))
        }

        // Verify notes preservation & nullable ownerId
        dbV9.query("SELECT id, title, shoulderId, uuid, ownerId FROM notes WHERE id = 10").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals("Q4 Strategy", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            org.junit.Assert.assertEquals("note-uuid-1", cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
            org.junit.Assert.assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("ownerId")))
        }

        // Verify checklist items preservation & nullable ownerId
        dbV9.query("SELECT id, noteId, text, uuid, ownerId FROM checklist_items WHERE id = 100").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(100L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals("item-uuid-1", cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
            org.junit.Assert.assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("ownerId")))
        }

        // Verify attachments preservation & nullable ownerId
        dbV9.query("SELECT id, noteId, displayName, uuid, ownerId FROM attachments WHERE id = 200").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToNext())
            org.junit.Assert.assertEquals(200L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            org.junit.Assert.assertEquals("att-uuid-1", cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
            org.junit.Assert.assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("ownerId")))
        }

        dbV9.close()
    }

    companion object {
        private const val CURRENT_VERSION = 9
        private const val VERSION_1_DATABASE_NAME = "remember-schema-v1-test.db"
        private const val VERSION_2_DATABASE_NAME = "remember-schema-v2-test.db"
        private const val VERSION_3_DATABASE_NAME = "remember-schema-v3-test.db"
        private const val VERSION_4_DATABASE_NAME = "remember-schema-v4-test.db"
        private const val VERSION_5_DATABASE_NAME = "remember-schema-v5-test.db"
        private const val VERSION_6_DATABASE_NAME = "remember-schema-v6-test.db"
        private const val VERSION_7_DATABASE_NAME = "remember-schema-v7-test.db"
        private const val VERSION_8_DATABASE_NAME = "remember-schema-v8-test.db"
    }
}
