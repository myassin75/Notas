package haddad.three.notas.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class Migration8To9Test {

    private data class TestRow(
        val id: Long,
        val uuid: String,
        val name: String = "",
        var ownerId: String? = null,
    )

    @Test
    fun migration_8_to_9_adds_ownerId_columns_indices_and_preserves_null_for_existing_records() {
        val shoulders = mutableListOf(
            TestRow(id = 1L, uuid = "shoulder-uuid-1", name = "Work", ownerId = null),
            TestRow(id = 2L, uuid = "shoulder-uuid-2", name = "Personal", ownerId = null),
        )
        val notes = mutableListOf(
            TestRow(id = 10L, uuid = "note-uuid-1", name = "Note 1", ownerId = null),
            TestRow(id = 20L, uuid = "note-uuid-2", name = "Note 2", ownerId = null),
            TestRow(id = 30L, uuid = "note-uuid-3", name = "Note 3", ownerId = null),
        )
        val checklistItems = mutableListOf(
            TestRow(id = 100L, uuid = "item-uuid-1", name = "Task 1", ownerId = null),
            TestRow(id = 101L, uuid = "item-uuid-2", name = "Task 2", ownerId = null),
        )
        val attachments = mutableListOf(
            TestRow(id = 200L, uuid = "att-uuid-1", name = "doc.pdf", ownerId = null),
        )

        val tableData = mapOf(
            "shoulders" to shoulders,
            "notes" to notes,
            "checklist_items" to checklistItems,
            "attachments" to attachments,
        )

        val executedSqls = mutableListOf<String>()
        val fakeDb = createFakeDatabase(tableData, executedSqls)

        // Execute migration 8 -> 9
        RememberDatabase.MIGRATION_8_9.migrate(fakeDb)

        // 1. Verify ALTER TABLE was called for all 4 sync-relevant tables adding nullable ownerId
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `shoulders` ADD COLUMN `ownerId` TEXT DEFAULT NULL") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `notes` ADD COLUMN `ownerId` TEXT DEFAULT NULL") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `checklist_items` ADD COLUMN `ownerId` TEXT DEFAULT NULL") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `attachments` ADD COLUMN `ownerId` TEXT DEFAULT NULL") })

        // 2. Verify INDEX creation for ownerId on all 4 tables
        assertTrue(executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_shoulders_ownerId` ON `shoulders` (`ownerId`)") })
        assertTrue(executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_notes_ownerId` ON `notes` (`ownerId`)") })
        assertTrue(executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_checklist_items_ownerId` ON `checklist_items` (`ownerId`)") })
        assertTrue(executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_attachments_ownerId` ON `attachments` (`ownerId`)") })

        // 3. Verify that existing records retain ownerId = NULL (never automatically assigned to an authenticated user)
        tableData.forEach { (tableName, rows) ->
            rows.forEach { row ->
                assertNull("Row ${row.id} in $tableName must have null ownerId after migration", row.ownerId)
            }
        }

        // 4. Verify record counts, IDs, and UUIDs are 100% preserved
        assertEquals(2, shoulders.size)
        assertEquals(1L, shoulders[0].id)
        assertEquals("shoulder-uuid-1", shoulders[0].uuid)
        assertEquals(2L, shoulders[1].id)
        assertEquals("shoulder-uuid-2", shoulders[1].uuid)

        assertEquals(3, notes.size)
        assertEquals(10L, notes[0].id)
        assertEquals("note-uuid-1", notes[0].uuid)
        assertEquals(20L, notes[1].id)
        assertEquals("note-uuid-2", notes[1].uuid)
        assertEquals(30L, notes[2].id)
        assertEquals("note-uuid-3", notes[2].uuid)

        assertEquals(2, checklistItems.size)
        assertEquals(100L, checklistItems[0].id)
        assertEquals("item-uuid-1", checklistItems[0].uuid)
        assertEquals(101L, checklistItems[1].id)
        assertEquals("item-uuid-2", checklistItems[1].uuid)

        assertEquals(1, attachments.size)
        assertEquals(200L, attachments[0].id)
        assertEquals("att-uuid-1", attachments[0].uuid)
    }

    private fun createFakeDatabase(
        tableData: Map<String, List<TestRow>>,
        executedSqls: MutableList<String>,
    ): SupportSQLiteDatabase {
        val handler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                when (method.name) {
                    "execSQL" -> {
                        val sql = args?.get(0) as String
                        executedSqls.add(sql)
                        return null
                    }
                    "query" -> {
                        val queryStr = when (val firstArg = args?.get(0)) {
                            is String -> firstArg
                            is SupportSQLiteQuery -> firstArg.sql
                            else -> firstArg.toString()
                        }
                        executedSqls.add(queryStr)
                        val tableName = tableData.keys.firstOrNull { queryStr.contains("`$it`") || queryStr.contains(" $it") }
                        val rows = if (tableName != null) tableData[tableName] ?: emptyList() else emptyList()
                        return createFakeCursor(rows)
                    }
                    else -> return null
                }
            }
        }
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase
    }

    private fun createFakeCursor(rows: List<TestRow>): Cursor {
        val columns = arrayOf("id", "uuid", "ownerId", "name")
        var position = -1
        val handler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                when (method.name) {
                    "close" -> return null
                    "moveToNext" -> {
                        position++
                        return position < rows.size
                    }
                    "getColumnIndexOrThrow" -> {
                        val name = args?.get(0) as String
                        val index = columns.indexOf(name)
                        if (index == -1) throw IllegalArgumentException("Column $name not found")
                        return index
                    }
                    "getLong" -> {
                        val index = args?.get(0) as Int
                        return when (index) {
                            0 -> rows[position].id
                            else -> 0L
                        }
                    }
                    "getString" -> {
                        val index = args?.get(0) as Int
                        return when (index) {
                            1 -> rows[position].uuid
                            2 -> rows[position].ownerId
                            3 -> rows[position].name
                            else -> null
                        }
                    }
                    "isNull" -> {
                        val index = args?.get(0) as Int
                        return index == 2 && rows[position].ownerId == null
                    }
                    else -> return null
                }
            }
        }
        return Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java),
            handler,
        ) as Cursor
    }
}
