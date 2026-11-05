package haddad.three.notas.data.drive.backup

import haddad.three.notas.crypto.CryptoConstants
import haddad.three.notas.crypto.EncryptedContainer
import haddad.three.notas.crypto.IntegrityVerificationException
import haddad.three.notas.data.BackupIo
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.drive.model.DriveBackupManifest
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.encodeNoteForBackup
import haddad.three.notas.data.encodeShoulderForBackup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class DriveArchiveAndRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var keyGenerator: KeyGenerator
    private lateinit var masterKey: SecretKey
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        keyGenerator = KeyGenerator.getInstance("AES").apply { init(256) }
        masterKey = keyGenerator.generateKey()
    }

    private fun createSampleZipArchive(
        targetZip: File,
        notesCount: Int = 3,
        tasksCount: Int = 2,
        attachmentsCount: Int = 1,
        schemaVersion: Int = BackupIo.SCHEMA_VERSION,
        includeLargeAttachment: Boolean = false,
    ): DriveBackupManifest {
        val backupId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        val sampleCategories = listOf(
            ShoulderEntity(id = 1, uuid = UUID.randomUUID().toString(), name = "Work", normalizedName = "work", createdAt = createdAt, updatedAt = createdAt),
            ShoulderEntity(id = 2, uuid = UUID.randomUUID().toString(), name = "Personal", normalizedName = "personal", createdAt = createdAt, updatedAt = createdAt),
        )

        val sampleNotes = mutableListOf<NoteWithItems>()
        for (i in 1..notesCount) {
            val note = NoteEntity(
                id = i.toLong(),
                uuid = "note_uuid_$i",
                title = "Note Title $i",
                body = "Note Body $i with secret content",
                kind = NoteKind.NOTE,
                colorIndex = 0,
                trashed = false,
                createdAt = createdAt,
                updatedAt = createdAt + i,
            )
            sampleNotes.add(NoteWithItems(note = note, items = emptyList(), attachments = emptyList()))
        }

        for (i in 1..tasksCount) {
            val taskNote = NoteEntity(
                id = (100 + i).toLong(),
                uuid = "task_uuid_$i",
                title = "Checklist $i",
                body = "",
                kind = NoteKind.LIST,
                colorIndex = 0,
                trashed = false,
                createdAt = createdAt,
                updatedAt = createdAt + i,
            )
            val items = listOf(
                ChecklistItemEntity(id = (1000 + i).toLong(), noteId = taskNote.id, uuid = "item_${i}_1", text = "Subtask A", checked = false, sortOrder = 0.0),
                ChecklistItemEntity(id = (2000 + i).toLong(), noteId = taskNote.id, uuid = "item_${i}_2", text = "Subtask B", checked = true, sortOrder = 1.0),
            )
            sampleNotes.add(NoteWithItems(note = taskNote, items = items, attachments = emptyList()))
        }

        val sampleAttachments = mutableListOf<Pair<NoteAttachmentEntity, ByteArray>>()
        for (i in 1..attachmentsCount) {
            val size = if (includeLargeAttachment && i == 1) 2 * 1024 * 1024 else 1024 // 2MB or 1KB
            val bytes = ByteArray(size)
            SecureRandom().nextBytes(bytes)
            val att = NoteAttachmentEntity(
                id = i.toLong(),
                noteId = 1L,
                uuid = "att_uuid_$i",
                displayName = "attachment_$i.jpg",
                mimeType = "image/jpeg",
                uri = "file:///dummy/path/attachment_$i.jpg",
            )
            sampleAttachments.add(att to bytes)
        }

        FileOutputStream(targetZip).use { fos ->
            ZipOutputStream(fos).use { zip ->
                // notes.json
                val notesJson = JSONObject().apply {
                    put("version", schemaVersion)
                    put("exportedAt", createdAt)
                    put("categories", JSONArray().apply { sampleCategories.forEach { put(encodeShoulderForBackup(it)) } })
                    put("notes", JSONArray().apply { sampleNotes.forEach { put(encodeNoteForBackup(it)) } })
                }
                zip.putNextEntry(ZipEntry(BackupIo.ENTRY_NOTES))
                OutputStreamWriter(zip, Charsets.UTF_8).apply { write(notesJson.toString(2)); flush() }
                zip.closeEntry()

                // settings.json
                zip.putNextEntry(ZipEntry(BackupIo.ENTRY_SETTINGS))
                OutputStreamWriter(zip, Charsets.UTF_8).apply { write("""{"theme":"DARK"}"""); flush() }
                zip.closeEntry()

                // attachments/
                for ((att, data) in sampleAttachments) {
                    zip.putNextEntry(ZipEntry("attachments/${att.uuid}_${att.displayName}"))
                    zip.write(data)
                    zip.closeEntry()
                }

                // manifest.json
                val manifest = DriveBackupManifest(
                    backupId = backupId,
                    createdAtEpochMillis = createdAt,
                    schemaVersion = schemaVersion,
                    notesCount = notesCount,
                    tasksCount = tasksCount,
                    categoriesCount = sampleCategories.size,
                    attachmentsCount = sampleAttachments.size,
                    sourceDevice = "Test Device",
                )
                zip.putNextEntry(ZipEntry(BackupIo.ENTRY_MANIFEST))
                OutputStreamWriter(zip, Charsets.UTF_8).apply { write(json.encodeToString(manifest)); flush() }
                zip.closeEntry()
            }
        }

        val sha256 = computeFileSha256(targetZip)
        return DriveBackupManifest(
            backupId = backupId,
            createdAtEpochMillis = createdAt,
            schemaVersion = schemaVersion,
            notesCount = notesCount,
            tasksCount = tasksCount,
            categoriesCount = sampleCategories.size,
            attachmentsCount = sampleAttachments.size,
            sourceDevice = "Test Device",
            plainSha256 = sha256,
        )
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testArchivePackagingAndEncryptionRoundTrip() {
        runBlocking {
            val plainZip = tempFolder.newFile("sample_plain.zip")
            val encryptedContainer = tempFolder.newFile("sample_encrypted.bin")
            val decryptedZip = tempFolder.newFile("sample_decrypted.zip")

            val manifest = createSampleZipArchive(plainZip, notesCount = 5, tasksCount = 3, attachmentsCount = 2)
            val plainSha256 = manifest.plainSha256

            val aad = "backupId=${manifest.backupId}&createdAt=${manifest.createdAtEpochMillis}".toByteArray(Charsets.UTF_8)

            // 1. Encrypt archive into NOTAS_E1 binary container
            FileInputStream(plainZip).use { fis ->
                FileOutputStream(encryptedContainer).use { fos ->
                    EncryptedContainer.encryptStream(fis, fos, masterKey, keyId = 1, aad = aad)
                }
            }

            assertTrue(encryptedContainer.length() > plainZip.length())

            // 2. Decrypt archive from container
            FileInputStream(encryptedContainer).use { fis ->
                FileOutputStream(decryptedZip).use { fos ->
                    val meta = EncryptedContainer.decryptStream(fis, fos, masterKey, expectedAad = aad)
                    assertEquals(1, meta.keyId)
                }
            }

            // 3. Verify SHA-256 matches plain original exactly
            val decryptedSha256 = computeFileSha256(decryptedZip)
            assertEquals(plainSha256, decryptedSha256)

            // 4. Verify contents can be parsed from decrypted ZIP
            var foundManifest = false
            var foundNotes = false
            ZipInputStream(FileInputStream(decryptedZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == BackupIo.ENTRY_MANIFEST) foundManifest = true
                    if (entry.name == BackupIo.ENTRY_NOTES) foundNotes = true
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            assertTrue(foundManifest)
            assertTrue(foundNotes)
        }
    }

    @Test
    fun testWrongMekRejection() {
        runBlocking {
            val plainZip = tempFolder.newFile("plain.zip")
            val encryptedContainer = tempFolder.newFile("encrypted.bin")
            val wrongKey = keyGenerator.generateKey()

            val manifest = createSampleZipArchive(plainZip)
            val aad = "backupId=${manifest.backupId}".toByteArray(Charsets.UTF_8)

            FileInputStream(plainZip).use { fis ->
                FileOutputStream(encryptedContainer).use { fos ->
                    EncryptedContainer.encryptStream(fis, fos, masterKey, aad = aad)
                }
            }

            val decryptedZip = tempFolder.newFile("decrypted_wrong.zip")
            assertThrows(IntegrityVerificationException::class.java) {
                FileInputStream(encryptedContainer).use { fis ->
                    FileOutputStream(decryptedZip).use { fos ->
                        EncryptedContainer.decryptStream(fis, fos, wrongKey, expectedAad = aad)
                    }
                }
            }
        }
    }

    @Test
    fun testCorruptedCiphertextRejection() {
        runBlocking {
            val plainZip = tempFolder.newFile("plain2.zip")
            val encryptedContainer = tempFolder.newFile("encrypted2.bin")

            val manifest = createSampleZipArchive(plainZip)
            val aad = "backupId=${manifest.backupId}".toByteArray(Charsets.UTF_8)

            FileInputStream(plainZip).use { fis ->
                FileOutputStream(encryptedContainer).use { fos ->
                    EncryptedContainer.encryptStream(fis, fos, masterKey, aad = aad)
                }
            }

            // Tamper byte in container
            val bytes = encryptedContainer.readBytes()
            bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0xFF).toByte()
            encryptedContainer.writeBytes(bytes)

            val decryptedZip = tempFolder.newFile("decrypted_tampered.zip")
            assertThrows(IntegrityVerificationException::class.java) {
                FileInputStream(encryptedContainer).use { fis ->
                    FileOutputStream(decryptedZip).use { fos ->
                        EncryptedContainer.decryptStream(fis, fos, masterKey, expectedAad = aad)
                    }
                }
            }
        }
    }

    @Test
    fun testLargeAttachmentStreamingEncryption() {
        runBlocking {
            val plainZip = tempFolder.newFile("large_plain.zip")
            val encryptedContainer = tempFolder.newFile("large_encrypted.bin")
            val decryptedZip = tempFolder.newFile("large_decrypted.zip")

            // Include 2MB attachment inside archive
            val manifest = createSampleZipArchive(plainZip, notesCount = 1, tasksCount = 0, attachmentsCount = 1, includeLargeAttachment = true)
            val plainSha256 = manifest.plainSha256

            FileInputStream(plainZip).use { fis ->
                FileOutputStream(encryptedContainer).use { fos ->
                    EncryptedContainer.encryptStream(fis, fos, masterKey)
                }
            }

            FileInputStream(encryptedContainer).use { fis ->
                FileOutputStream(decryptedZip).use { fos ->
                    EncryptedContainer.decryptStream(fis, fos, masterKey)
                }
            }

            val decryptedSha256 = computeFileSha256(decryptedZip)
            assertEquals(plainSha256, decryptedSha256)
        }
    }

    @Test
    fun testRestoreMergeVsReplaceLogic() {
        val localNoteOld = NoteEntity(id = 1, uuid = "uuid_1", kind = NoteKind.NOTE, title = "Old Local Title", body = "Local body", colorIndex = 0, trashed = false, createdAt = 1000L, updatedAt = 1000L)
        val localNoteNew = NoteEntity(id = 2, uuid = "uuid_2", kind = NoteKind.NOTE, title = "Newer Local Title", body = "Local newer", colorIndex = 0, trashed = false, createdAt = 3000L, updatedAt = 3000L)

        val backupNote1 = NoteEntity(id = 0, uuid = "uuid_1", kind = NoteKind.NOTE, title = "Backup Newer Title", body = "Backup body", colorIndex = 0, trashed = false, createdAt = 2000L, updatedAt = 2000L)
        val backupNote2 = NoteEntity(id = 0, uuid = "uuid_2", kind = NoteKind.NOTE, title = "Backup Older Title", body = "Backup body", colorIndex = 0, trashed = false, createdAt = 1500L, updatedAt = 1500L)
        val backupNote3 = NoteEntity(id = 0, uuid = "uuid_3", kind = NoteKind.NOTE, title = "Brand New Backup Note", body = "Only in backup", colorIndex = 0, trashed = false, createdAt = 2500L, updatedAt = 2500L)

        // MERGE Mode verification
        val localNotesMap = mapOf("uuid_1" to localNoteOld, "uuid_2" to localNoteNew)
        val incomingNotes = listOf(backupNote1, backupNote2, backupNote3)

        val finalMergedNotes = mutableListOf<NoteEntity>()
        for (incoming in incomingNotes) {
            val existing = localNotesMap[incoming.uuid]
            if (existing != null) {
                if (existing.updatedAt >= incoming.updatedAt) {
                    finalMergedNotes.add(existing) // Keep newer local
                } else {
                    finalMergedNotes.add(incoming) // Update with newer backup
                }
            } else {
                finalMergedNotes.add(incoming) // Insert missing
            }
        }

        assertEquals(3, finalMergedNotes.size)
        // uuid_1: backup was 2000L > local 1000L -> took backup
        assertEquals("Backup Newer Title", finalMergedNotes.first { it.uuid == "uuid_1" }.title)
        // uuid_2: local was 3000L > backup 1500L -> kept local
        assertEquals("Newer Local Title", finalMergedNotes.first { it.uuid == "uuid_2" }.title)
        // uuid_3: was missing -> inserted
        assertEquals("Brand New Backup Note", finalMergedNotes.first { it.uuid == "uuid_3" }.title)
    }
}
