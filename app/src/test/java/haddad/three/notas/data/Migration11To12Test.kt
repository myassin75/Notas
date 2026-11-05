package haddad.three.notas.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class Migration11To12Test {

    @Test
    fun migration_11_to_12_adds_watermark_column_and_creates_all_sync_tables_and_indices() {
        val executedSqls = mutableListOf<String>()
        val fakeDb = createFakeDatabase(executedSqls)

        // Execute migration 11 -> 12
        RememberDatabase.MIGRATION_11_12.migrate(fakeDb)

        // 1. Verify ALTER TABLE sync_watermarks adds highestAppliedSeq column
        assertTrue(
            "Expected ALTER TABLE sync_watermarks ADD COLUMN highestAppliedSeq",
            executedSqls.any { it.contains("ALTER TABLE `sync_watermarks` ADD COLUMN `highestAppliedSeq` INTEGER NOT NULL DEFAULT 0") },
        )

        // 2. Verify CREATE TABLE for sync_applied_operations
        assertTrue(
            "Expected CREATE TABLE sync_applied_operations",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_applied_operations`") },
        )
        assertTrue(
            "Expected index on deviceId, sequence for sync_applied_operations",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_applied_operations_deviceId_sequence`") },
        )
        assertTrue(
            "Expected index on entityType, entityId for sync_applied_operations",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_applied_operations_entityType_entityId`") },
        )

        // 3. Verify CREATE TABLE for sync_conflict_metadata
        assertTrue(
            "Expected CREATE TABLE sync_conflict_metadata",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_conflict_metadata`") },
        )
        assertTrue(
            "Expected index on entityType, entityId for sync_conflict_metadata",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_conflict_metadata_entityType_entityId`") },
        )

        // 4. Verify CREATE TABLE for sync_tombstones
        assertTrue(
            "Expected CREATE TABLE sync_tombstones",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_tombstones`") },
        )
        assertTrue(
            "Expected index on deletedAt for sync_tombstones",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_tombstones_deletedAt`") },
        )

        // 5. Verify CREATE TABLE for sync_conflict_records
        assertTrue(
            "Expected CREATE TABLE sync_conflict_records",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_conflict_records`") },
        )
        assertTrue(
            "Expected unique index on conflictId for sync_conflict_records",
            executedSqls.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflict_records_conflictId`") },
        )
        assertTrue(
            "Expected index on entityType, entityId for sync_conflict_records",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sync_conflict_records_entityType_entityId`") },
        )

        // 6. Verify CREATE TABLE for sync_hlc_state
        assertTrue(
            "Expected CREATE TABLE sync_hlc_state",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `sync_hlc_state`") },
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

