package haddad.three.notas.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class Migration12To13Test {

    @Test
    fun migration_12_to_13_creates_sync_snapshots_and_attachment_promotions_tables_and_indices() {
        val executedSqls = mutableListOf<String>()
        val fakeDb = createFakeDatabase(executedSqls)

        // Execute migration 12 -> 13
        RememberDatabase.MIGRATION_12_13.migrate(fakeDb)

        // 1. Verify CREATE TABLE for sync_snapshots
        assertTrue(
            "Expected CREATE TABLE sync_snapshots",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_snapshots`") },
        )
        assertTrue(
            "Expected index on status for sync_snapshots",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_snapshots_status`") },
        )
        assertTrue(
            "Expected index on createdAt for sync_snapshots",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_snapshots_createdAt`") },
        )

        // 2. Verify CREATE TABLE for sync_attachment_promotions
        assertTrue(
            "Expected CREATE TABLE sync_attachment_promotions",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_attachment_promotions`") },
        )
    }

    private fun createFakeDatabase(executedSqls: MutableList<String>): SupportSQLiteDatabase {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    val sql = args?.get(0) as String
                    executedSqls.add(sql)
                    null
                }
                "query" -> {
                    val queryStr = when (val firstArg = args?.get(0)) {
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

