package haddad.three.notas.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class Migration13To14Test {
    @Test
    fun migration_13_to_14_creates_sync_history_records_table_and_indices() {
        val executedSqls = mutableListOf<String>()
        val fakeDb = createFakeDatabase(executedSqls)

        // Execute migration 13 -> 14
        RememberDatabase.MIGRATION_13_14.migrate(fakeDb)

        // Verify CREATE TABLE for sync_history_records
        assertTrue(
            "Expected CREATE TABLE sync_history_records",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_history_records`") },
        )
        assertTrue(
            "Expected index on timestampEpochMillis",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_history_records_timestampEpochMillis`") },
        )
        assertTrue(
            "Expected index on status",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_history_records_status`") },
        )
    }

    private fun createFakeDatabase(executedSqls: MutableList<String>): SupportSQLiteDatabase {
        val handler =
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        val sql = args?.get(0) as String
                        executedSqls.add(sql)
                        null
                    }
                    "query" -> {
                        val queryStr =
                            when (val firstArg = args?.get(0)) {
                                is String -> firstArg
                                is SupportSQLiteQuery -> firstArg.sql
                                else -> firstArg.toString()
                            }
                        executedSqls.add(queryStr)
                        null
                    }
                    else -> null
                }
            }
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase
    }
}

