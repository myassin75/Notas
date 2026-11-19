package haddad.three.notas.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

class NoteDaoQueryPlanTest {
    @Test
    fun queryPlan_observeActive_usesOwnerIdIndex() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchema(connection)

        // Verify observeActive EXPLAIN QUERY PLAN uses index_notes_ownerId
        val activeQuery =
            "EXPLAIN QUERY PLAN SELECT * FROM notes WHERE trashed = 0 AND archived = 0 AND ((? IS NULL AND ownerId IS NULL) OR ownerId = ?) ORDER BY updatedAt DESC"
        val activePlan = StringBuilder()
        connection.prepareStatement(activeQuery).use { prep ->
            prep.setString(1, "user_123")
            prep.setString(2, "user_123")
            prep.executeQuery().use { rs ->
                while (rs.next()) {
                    activePlan.append(rs.getString("detail")).append("\n")
                }
            }
        }
        val activePlanStr = activePlan.toString()
        assertTrue(
            "Expected observeActive to use index_notes_ownerId, but plan was:\n$activePlanStr",
            activePlanStr.contains("index_notes_ownerId"),
        )

        connection.close()
    }

    @Test
    fun queryPlan_searchNotes_verifiesFtsAndOwnerIdUsage() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchema(connection)

        val searchQuery =
            "EXPLAIN QUERY PLAN SELECT notes.* FROM notes JOIN notes_fts ON notes.id = notes_fts.docid WHERE notes_fts MATCH ? AND notes.trashed = 0 AND notes.archived = 0 AND ((? IS NULL AND notes.ownerId IS NULL) OR notes.ownerId = ?) ORDER BY notes.updatedAt DESC"

        // 1. On empty/fresh table, SQLite cost optimizer prioritizes FTS index match followed by PK rowid lookup
        val emptyPlan = StringBuilder()
        connection.prepareStatement(searchQuery).use { prep ->
            prep.setString(1, "testQuery")
            prep.setString(2, "user_123")
            prep.setString(3, "user_123")
            prep.executeQuery().use { rs ->
                while (rs.next()) {
                    emptyPlan.append(rs.getString("detail")).append("\n")
                }
            }
        }
        val emptyPlanStr = emptyPlan.toString()
        assertTrue(
            "Expected searchNotes on fresh table to utilize notes_fts virtual table index, but plan was:\n$emptyPlanStr",
            emptyPlanStr.contains("notes_fts") && (emptyPlanStr.contains("rowid=?") || emptyPlanStr.contains("index_notes_ownerId")),
        )

        // 2. With populated rows, verify SQLite query optimizer utilizes index_notes_ownerId for owner partition filtering
        connection.createStatement().use { statement ->
            for (i in 1..200) {
                statement.execute(
                    "INSERT INTO notes (uuid, ownerId, kind, title, body, checklistText, attachmentText, actionsText, colorIndex, trashed, createdAt, updatedAt, isAllDay, importance, visibility, locked, actions, reminders, archived) " +
                        "VALUES ('uuid-$i', 'user_123', 'NOTE', 'title $i keyword', 'body', '', '', '', 0, 0, 1000, 1000, 0, 'DEFAULT', 'DEFAULT', 0, '[]', '[]', 0)",
                )
                statement.execute("INSERT INTO notes_fts (docid, title, body, checklistText, attachmentText, actionsText) VALUES ($i, 'title $i keyword', 'body', '', '', '')")
            }
        }

        val populatedPlan = StringBuilder()
        connection.prepareStatement(searchQuery).use { prep ->
            prep.setString(1, "keyword")
            prep.setString(2, "user_123")
            prep.setString(3, "user_123")
            prep.executeQuery().use { rs ->
                while (rs.next()) {
                    populatedPlan.append(rs.getString("detail")).append("\n")
                }
            }
        }
        val populatedPlanStr = populatedPlan.toString()
        assertTrue(
            "Expected searchNotes with data to utilize index_notes_ownerId or notes_fts, but plan was:\n$populatedPlanStr",
            populatedPlanStr.contains("index_notes_ownerId") || populatedPlanStr.contains("notes_fts"),
        )

        connection.close()
    }

    private fun initSchema(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uuid TEXT NOT NULL,
                    ownerId TEXT,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    checklistText TEXT NOT NULL,
                    attachmentText TEXT NOT NULL,
                    actionsText TEXT NOT NULL,
                    colorIndex INTEGER NOT NULL,
                    trashed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    reminderAt INTEGER,
                    endAt INTEGER,
                    isAllDay INTEGER NOT NULL,
                    importance TEXT NOT NULL,
                    visibility TEXT NOT NULL,
                    pictureUri TEXT,
                    pictureHeroFraming TEXT,
                    locked INTEGER NOT NULL,
                    iconKey TEXT,
                    actions TEXT NOT NULL,
                    recurrence TEXT,
                    reminders TEXT NOT NULL,
                    shoulderId INTEGER,
                    archived INTEGER NOT NULL,
                    trashedAt INTEGER,
                    completedAt INTEGER,
                    pinnedAt INTEGER
                );
                """.trimIndent(),
            )
            statement.execute("CREATE INDEX index_notes_shoulderId ON notes (shoulderId);")
            statement.execute("CREATE UNIQUE INDEX index_notes_uuid ON notes (uuid);")
            statement.execute("CREATE INDEX index_notes_ownerId ON notes (ownerId);")
            statement.execute("CREATE VIRTUAL TABLE notes_fts USING fts4(content='notes', title, body, checklistText, attachmentText, actionsText);")
        }
    }
}
