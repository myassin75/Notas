package three.haddad.smartnotas.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

class Migration7To8Test {

    private data class TestRow(
        val id: Long,
        var uuid: String = "",
        val name: String = "",
    )

    @Test
    fun migration_7_to_8_adds_columns_populates_uuids_and_creates_unique_indices() {
        val shoulders = mutableListOf(
            TestRow(id = 1L, name = "Inbox"),
            TestRow(id = 2L, name = "Projects"),
        )
        val notes = mutableListOf(
            TestRow(id = 10L, name = "Note 1"),
            TestRow(id = 20L, name = "Note 2"),
            TestRow(id = 30L, name = "Note 3"),
        )
        val checklistItems = mutableListOf(
            TestRow(id = 100L, name = "Task 1"),
            TestRow(id = 101L, name = "Task 2"),
        )
        val attachments = mutableListOf(
            TestRow(id = 200L, name = "photo.jpg"),
        )

        val tableData = mapOf(
            "shoulders" to shoulders,
            "notes" to notes,
            "checklist_items" to checklistItems,
            "attachments" to attachments,
        )

        val executedSqls = mutableListOf<String>()

        val fakeDb = createFakeDatabase(tableData, executedSqls)

        // Execute migration 7 -> 8
        RememberDatabase.MIGRATION_7_8.migrate(fakeDb)

        // 1. Verify ALTER TABLE was called for all 4 tables
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `shoulders` ADD COLUMN `uuid`") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `notes` ADD COLUMN `uuid`") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `checklist_items` ADD COLUMN `uuid`") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE `attachments` ADD COLUMN `uuid`") })

        // 2. Verify UNIQUE INDEX creation for all 4 tables
        assertTrue(executedSqls.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_shoulders_uuid` ON `shoulders` (`uuid`)") })
        assertTrue(executedSqls.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_uuid` ON `notes` (`uuid`)") })
        assertTrue(executedSqls.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_items_uuid` ON `checklist_items` (`uuid`)") })
        assertTrue(executedSqls.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)") })

        // 3. Verify all existing records received non-empty valid UUIDs
        val allUuids = mutableSetOf<String>()

        tableData.forEach { (tableName, rows) ->
            rows.forEach { row ->
                assertNotNull("Row in $tableName has null UUID", row.uuid)
                assertFalse("Row in $tableName has blank UUID", row.uuid.isBlank())
                // Verify valid RFC-4122 UUID format
                val parsed = UUID.fromString(row.uuid)
                assertNotNull(parsed)

                // Verify global uniqueness
                assertTrue("Duplicate UUID generated: ${row.uuid}", allUuids.add(row.uuid))
            }
        }

        // 4. Verify record counts and original Long IDs are preserved
        assertEquals(2, shoulders.size)
        assertEquals(1L, shoulders[0].id)
        assertEquals(2L, shoulders[1].id)

        assertEquals(3, notes.size)
        assertEquals(10L, notes[0].id)
        assertEquals(20L, notes[1].id)
        assertEquals(30L, notes[2].id)

        assertEquals(2, checklistItems.size)
        assertEquals(100L, checklistItems[0].id)
        assertEquals(101L, checklistItems[1].id)

        assertEquals(1, attachments.size)
        assertEquals(200L, attachments[0].id)
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
                        if (args.size > 1 && args[1] != null) {
                            @Suppress("UNCHECKED_CAST")
                            val bindArgs = args[1] as Array<Any?>
                            val uuid = bindArgs[0] as String
                            val id = (bindArgs[1] as Number).toLong()

                            // Update corresponding table row
                            tableData.values.forEach { rows ->
                                rows.firstOrNull { it.id == id }?.let { it.uuid = uuid }
                            }
                        }
                        return null
                    }
                    "query" -> {
                        val queryStr = when (val firstArg = args?.get(0)) {
                            is String -> firstArg
                            is SupportSQLiteQuery -> firstArg.sql
                            else -> firstArg.toString()
                        }
                        executedSqls.add(queryStr)

                        // Find matching table
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
        var currentIndex = -1
        val handler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                return when (method.name) {
                    "moveToNext" -> {
                        currentIndex++
                        currentIndex < rows.size
                    }
                    "getColumnIndexOrThrow", "getColumnIndex" -> {
                        val colName = args?.get(0) as String
                        if (colName == "id") 0 else -1
                    }
                    "getLong" -> {
                        rows[currentIndex].id
                    }
                    "getString" -> {
                        rows[currentIndex].name
                    }
                    "close" -> null
                    else -> null
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
