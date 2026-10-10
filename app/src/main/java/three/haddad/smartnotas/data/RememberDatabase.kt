package three.haddad.smartnotas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @TypeConverter fun fromKind(kind: NoteKind): String = kind.name

    @TypeConverter fun toKind(value: String): NoteKind = NoteKind.valueOf(value)

    @TypeConverter fun fromImportance(v: Importance): String = v.name

    @TypeConverter fun toImportance(value: String): Importance = Importance.valueOf(value)

    @TypeConverter fun fromVisibility(v: Visibility): String = v.name

    @TypeConverter fun toVisibility(value: String): Visibility = Visibility.valueOf(value)

    @TypeConverter
    fun fromActions(v: List<NoteAction>): String = json.encodeToString(v)

    @TypeConverter
    fun toActions(value: String): List<NoteAction> {
        if (value.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<NoteAction>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromStringList(v: List<String>): String = json.encodeToString(v)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromRecurrence(v: RecurrenceRule?): String? = RecurrenceRule.toJson(v)

    @TypeConverter
    fun toRecurrence(value: String?): RecurrenceRule? = RecurrenceRule.fromJson(value)

    @TypeConverter
    fun fromReminders(v: List<NoteReminder>): String = json.encodeToString(v)

    @TypeConverter
    fun toReminders(value: String): List<NoteReminder> {
        if (value.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<NoteReminder>>(value) }.getOrDefault(emptyList())
    }
}

@Database(
    entities = [
        NoteEntity::class,
        ChecklistItemEntity::class,
        NoteAttachmentEntity::class,
        NoteFtsEntity::class,
        ShoulderEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RememberDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun checklistItemDao(): ChecklistItemDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun shoulderDao(): ShoulderDao

    companion object {
        fun build(context: Context): RememberDatabase =
            Room
                .databaseBuilder(context, RememberDatabase::class.java, "remember.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(false)
                .build()

        internal val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN checklistText TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE notes ADD COLUMN attachmentText TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE notes ADD COLUMN actionsText TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        """
                        UPDATE notes
                        SET checklistText = COALESCE(
                            (
                                SELECT GROUP_CONCAT(text, ' ')
                                FROM checklist_items
                                WHERE checklist_items.noteId = notes.id
                            ),
                            ''
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        UPDATE notes
                        SET attachmentText = COALESCE(
                            (
                                SELECT GROUP_CONCAT(displayName, ' ')
                                FROM attachments
                                WHERE attachments.noteId = notes.id
                            ),
                            ''
                        )
                        """.trimIndent(),
                    )
                    db
                        .query(
                            """
                            SELECT rowid, actions
                            FROM notes
                            WHERE actions IS NOT NULL AND actions != '' AND actions != '[]'
                            """.trimIndent(),
                        ).use { cursor ->
                            val rowIdColumn = cursor.getColumnIndex("rowid")
                            val actionsColumn = cursor.getColumnIndex("actions")
                            while (cursor.moveToNext()) {
                                val rowId = cursor.getLong(rowIdColumn)
                                val actionsJson = cursor.getString(actionsColumn) ?: continue
                                val actionsText = actionsSearchTextFromJson(actionsJson)
                                db.execSQL(
                                    "UPDATE notes SET actionsText = ? WHERE rowid = ?",
                                    arrayOf<Any?>(actionsText, rowId),
                                )
                            }
                        }
                    dropNotesFtsSyncTriggers(db)
                    db.execSQL("DROP TABLE IF EXISTS notes_fts")
                    createNotesFtsTableLegacy(db)
                    createNotesFtsSyncTriggersLegacy(db)
                    db.execSQL(
                        """
                        INSERT INTO notes_fts(docid, title, body, tags, checklistText, attachmentText, actionsText)
                        SELECT rowid, title, body, tags, checklistText, attachmentText, actionsText FROM notes
                        """.trimIndent(),
                    )
                }
            }

        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE checklist_items ADD COLUMN details TEXT NOT NULL DEFAULT ''")
                }
            }

        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN reminders TEXT NOT NULL DEFAULT '[]'")
                }
            }

        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN pinnedAt INTEGER")
                }
            }

        internal val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Create shoulders table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `shoulders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `name` TEXT NOT NULL, 
                            `normalizedName` TEXT NOT NULL, 
                            `colorHex` TEXT, 
                            `createdAt` INTEGER NOT NULL, 
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shoulders_normalizedName` ON `shoulders` (`normalizedName`)")

                    // Create new notes table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `notes_new` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `kind` TEXT NOT NULL, 
                            `title` TEXT NOT NULL, 
                            `body` TEXT NOT NULL, 
                            `checklistText` TEXT NOT NULL, 
                            `attachmentText` TEXT NOT NULL, 
                            `actionsText` TEXT NOT NULL, 
                            `colorIndex` INTEGER NOT NULL, 
                            `trashed` INTEGER NOT NULL, 
                            `createdAt` INTEGER NOT NULL, 
                            `updatedAt` INTEGER NOT NULL, 
                            `reminderAt` INTEGER, 
                            `importance` TEXT NOT NULL, 
                            `visibility` TEXT NOT NULL, 
                            `pictureUri` TEXT, 
                            `pictureHeroFraming` TEXT, 
                            `locked` INTEGER NOT NULL, 
                            `iconKey` TEXT, 
                            `actions` TEXT NOT NULL, 
                            `recurrence` TEXT, 
                            `reminders` TEXT NOT NULL, 
                            `shoulderId` INTEGER, 
                            `archived` INTEGER NOT NULL, 
                            `trashedAt` INTEGER, 
                            `completedAt` INTEGER, 
                            `pinnedAt` INTEGER, 
                            FOREIGN KEY(`shoulderId`) REFERENCES `shoulders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    
                    // Add index for shoulderId
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_shoulderId` ON `notes_new` (`shoulderId`)")

                    // Copy data from old notes to new notes
                    db.execSQL(
                        """
                        INSERT INTO `notes_new` (
                            `id`, `kind`, `title`, `body`, `checklistText`, `attachmentText`, `actionsText`, 
                            `colorIndex`, `trashed`, `createdAt`, `updatedAt`, `reminderAt`, `importance`, 
                            `visibility`, `pictureUri`, `pictureHeroFraming`, `locked`, `iconKey`, `actions`, 
                            `recurrence`, `reminders`, `archived`, `trashedAt`, `completedAt`, `pinnedAt`
                        )
                        SELECT 
                            `id`, `kind`, `title`, `body`, `checklistText`, `attachmentText`, `actionsText`, 
                            `colorIndex`, `trashed`, `createdAt`, `updatedAt`, `reminderAt`, `importance`, 
                            `visibility`, `pictureUri`, `pictureHeroFraming`, `locked`, `iconKey`, `actions`, 
                            `recurrence`, `reminders`, `archived`, `trashedAt`, `completedAt`, `pinnedAt`
                        FROM `notes`
                        """.trimIndent()
                    )

                    // Drop old table and rename new one
                    db.execSQL("DROP TABLE `notes`")
                    db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")

                    // Drop tag related tables
                    db.execSQL("DROP TABLE IF EXISTS `tags`")
                    db.execSQL("DROP TABLE IF EXISTS `note_tags`")
                    
                    // Recreate notes_fts
                    dropNotesFtsSyncTriggers(db)
                    db.execSQL("DROP TABLE IF EXISTS notes_fts")
                    createNotesFtsTable(db)
                    createNotesFtsSyncTriggers(db)
                    db.execSQL(
                        """
                        INSERT INTO notes_fts(docid, title, body, checklistText, attachmentText, actionsText)
                        SELECT rowid, title, body, checklistText, attachmentText, actionsText FROM notes
                        """.trimIndent(),
                    )
                }
            }

        private fun dropNotesFtsSyncTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT")
        }

        private fun createNotesFtsTableLegacy(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `tags` TEXT NOT NULL,
                    `checklistText` TEXT NOT NULL,
                    `attachmentText` TEXT NOT NULL,
                    `actionsText` TEXT NOT NULL,
                    tokenize=unicode61 `remove_diacritics=2`,
                    content=`notes`
                )
                """.trimIndent(),
            )
        }

        private fun createNotesFtsSyncTriggersLegacy(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE
                BEFORE UPDATE ON notes
                BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE
                BEFORE DELETE ON notes
                BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE
                AFTER UPDATE ON notes
                BEGIN
                    INSERT INTO notes_fts(docid, title, body, tags, checklistText, attachmentText, actionsText)
                    VALUES (NEW.rowid, NEW.title, NEW.body, NEW.tags, NEW.checklistText, NEW.attachmentText, NEW.actionsText);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT
                AFTER INSERT ON notes
                BEGIN
                    INSERT INTO notes_fts(docid, title, body, tags, checklistText, attachmentText, actionsText)
                    VALUES (NEW.rowid, NEW.title, NEW.body, NEW.tags, NEW.checklistText, NEW.attachmentText, NEW.actionsText);
                END
                """.trimIndent(),
            )
        }

        private fun createNotesFtsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `checklistText` TEXT NOT NULL,
                    `attachmentText` TEXT NOT NULL,
                    `actionsText` TEXT NOT NULL,
                    tokenize=unicode61 `remove_diacritics=2`,
                    content=`notes`
                )
                """.trimIndent(),
            )
        }

        private fun createNotesFtsSyncTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE
                BEFORE UPDATE ON notes
                BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE
                BEFORE DELETE ON notes
                BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE
                AFTER UPDATE ON notes
                BEGIN
                    INSERT INTO notes_fts(docid, title, body, checklistText, attachmentText, actionsText)
                    VALUES (NEW.rowid, NEW.title, NEW.body, NEW.checklistText, NEW.attachmentText, NEW.actionsText);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT
                AFTER INSERT ON notes
                BEGIN
                    INSERT INTO notes_fts(docid, title, body, checklistText, attachmentText, actionsText)
                    VALUES (NEW.rowid, NEW.title, NEW.body, NEW.checklistText, NEW.attachmentText, NEW.actionsText);
                END
                """.trimIndent(),
            )
        }
    }
}
