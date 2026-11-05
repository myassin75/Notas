package haddad.three.notas.data.drive.backup

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.data.BackupIo
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.LockPrefs
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.UpdatePrefs
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.data.drive.model.DriveBackupManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DriveZipSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var archiveManager: DriveBackupArchiveManager
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> dummyDao(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, _, _ -> null } as T
    }

    @Before
    fun setUp() {
        val fakeFilesDir = tempFolder.newFolder("files")
        val fakeCacheDir = tempFolder.newFolder("cache")
        val fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = fakeFilesDir
            override fun getCacheDir(): File = fakeCacheDir
        }

        archiveManager = DriveBackupArchiveManager(
            noteDao = dummyDao<NoteDao>(),
            shoulderDao = dummyDao<ShoulderDao>(),
            itemDao = dummyDao<ChecklistItemDao>(),
            themePrefs = ThemePrefs(fakeContext),
            notesUiPrefs = NotesUiPrefs(fakeContext),
            viewOptionsPrefs = ViewOptionsPrefs(fakeContext),
            lockPrefs = LockPrefs(fakeContext),
            interactionPrefs = InteractionPrefs(fakeContext),
            quickCapturePrefs = QuickCapturePrefs(fakeContext),
            reminderPrefs = ReminderPrefs(fakeContext),
            defaultNotePrefs = DefaultNotePrefs(fakeContext),
            updatePrefs = UpdatePrefs(fakeContext),
            json = json,
        )
    }

    private fun createZipWithEntries(
        zipFile: File,
        entries: List<Pair<String, ByteArray>>,
    ) {
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zip ->
                for ((name, data) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }
    }

    @Test
    fun testRejectZipSlipRelativePath() {
        runBlocking {
            val maliciousZip = tempFolder.newFile("malicious_slip.zip")
            createZipWithEntries(
                maliciousZip,
                listOf(
                    BackupIo.ENTRY_MANIFEST to """{"backupId":"b1","createdAtEpochMillis":100,"schemaVersion":1,"notesCount":0,"tasksCount":0,"categoriesCount":0,"attachmentsCount":1}""".toByteArray(),
                    BackupIo.ENTRY_NOTES to """{"version":1,"exportedAt":100,"notes":[]}""".toByteArray(),
                    "attachments/../../etc_passwd.txt" to "root:x:0:0".toByteArray(),
                ),
            )

            val ex = assertThrows(SecurityException::class.java) {
                runBlocking { archiveManager.extractArchiveContents(maliciousZip) }
            }
            assertTrue(ex.message!!.contains("path traversal", ignoreCase = true))
        }
    }

    @Test
    fun testRejectAbsolutePathInArchive() {
        runBlocking {
            val maliciousZip = tempFolder.newFile("malicious_absolute.zip")
            createZipWithEntries(
                maliciousZip,
                listOf(
                    BackupIo.ENTRY_MANIFEST to """{"backupId":"b1","createdAtEpochMillis":100,"schemaVersion":1,"notesCount":0,"tasksCount":0,"categoriesCount":0,"attachmentsCount":0}""".toByteArray(),
                    "/root/exploit.sh" to "#!/bin/sh\nrm -rf /".toByteArray(),
                ),
            )

            val ex = assertThrows(SecurityException::class.java) {
                runBlocking { archiveManager.extractArchiveContents(maliciousZip) }
            }
            assertTrue(ex.message!!.contains("path traversal", ignoreCase = true))
        }
    }

    @Test
    fun testRejectDuplicateArchiveEntries() {
        runBlocking {
            val maliciousZip = tempFolder.newFile("duplicate_entries.zip")
            FileOutputStream(maliciousZip).use { fos ->
                val payload1 = """{"backupId":"b1","createdAtEpochMillis":100,"schemaVersion":1,"notesCount":0,"tasksCount":0,"categoriesCount":0,"attachmentsCount":0}""".toByteArray()
                writeRawStoredZipEntry(fos, BackupIo.ENTRY_MANIFEST, payload1)
                val payload2 = """{"backupId":"b2","createdAtEpochMillis":200,"schemaVersion":1,"notesCount":0,"tasksCount":0,"categoriesCount":0,"attachmentsCount":0}""".toByteArray()
                writeRawStoredZipEntry(fos, BackupIo.ENTRY_MANIFEST, payload2)
            }

            val ex = assertThrows(SecurityException::class.java) {
                runBlocking { archiveManager.extractArchiveContents(maliciousZip) }
            }
            assertTrue(ex.message!!.contains("Duplicate archive entry", ignoreCase = true))
        }
    }

    private fun writeRawStoredZipEntry(out: java.io.OutputStream, name: String, data: ByteArray) {
        val crc = java.util.zip.CRC32().apply { update(data) }.value
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val bb = java.nio.ByteBuffer.allocate(30).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0x04034b50)
        bb.putShort(20)
        bb.putShort(0)
        bb.putShort(0)
        bb.putShort(0)
        bb.putShort(0)
        bb.putInt(crc.toInt())
        bb.putInt(data.size)
        bb.putInt(data.size)
        bb.putShort(nameBytes.size.toShort())
        bb.putShort(0)
        out.write(bb.array())
        out.write(nameBytes)
        out.write(data)
    }

    @Test
    fun testRejectZipBombDecompressedSizeExceeded() {
        runBlocking {
            val zipBomb = tempFolder.newFile("zip_bomb.zip")
            // Create a small compressed file that expands into over 100MB per entry limit
            val oversizedBuffer = ByteArray(1024 * 1024) { 0x41 } // 1MB buffer
            FileOutputStream(zipBomb).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    zip.putNextEntry(ZipEntry("attachments/oversized.bin"))
                    // Write 101 MB (exceeds MAX_ENTRY_SIZE_BYTES = 100MB)
                    for (i in 1..101) {
                        zip.write(oversizedBuffer)
                    }
                    zip.closeEntry()
                }
            }

            val ex = assertThrows(SecurityException::class.java) {
                runBlocking { archiveManager.extractArchiveContents(zipBomb) }
            }
            assertTrue(ex.message!!.contains("exceeds maximum", ignoreCase = true))
        }
    }

    @Test
    fun testRejectTruncatedArchive() {
        runBlocking {
            val truncatedZip = tempFolder.newFile("truncated.zip")
            // Write partial ZIP header and truncate
            truncatedZip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00))

            // Should throw IOException or EOFException when reading corrupted zip
            assertThrows(Exception::class.java) {
                runBlocking { archiveManager.extractArchiveContents(truncatedZip) }
            }
        }
    }

    @Test
    fun testRejectUnexpectedFileType() {
        runBlocking {
            val maliciousZip = tempFolder.newFile("unexpected_file.zip")
            createZipWithEntries(
                maliciousZip,
                listOf(
                    BackupIo.ENTRY_MANIFEST to """{"backupId":"b1","createdAtEpochMillis":100,"schemaVersion":1,"notesCount":0,"tasksCount":0,"categoriesCount":0,"attachmentsCount":0}""".toByteArray(),
                    "evil.exe" to byteArrayOf(0x4D, 0x5A, 0x90.toByte()),
                ),
            )

            val ex = assertThrows(SecurityException::class.java) {
                runBlocking { archiveManager.extractArchiveContents(maliciousZip) }
            }
            assertTrue(ex.message!!.contains("Unexpected file entry", ignoreCase = true))
        }
    }

    @Test
    fun testPeekManifestValidatesSchemaVersion() {
        runBlocking {
            val futureSchemaZip = tempFolder.newFile("future_schema.zip")
            val futureManifest = DriveBackupManifest(
                backupId = "future_123",
                createdAtEpochMillis = System.currentTimeMillis(),
                schemaVersion = 9999, // Future schema version
                notesCount = 10,
                tasksCount = 0,
                categoriesCount = 0,
                attachmentsCount = 0,
            )
            createZipWithEntries(
                futureSchemaZip,
                listOf(
                    BackupIo.ENTRY_MANIFEST to json.encodeToString(futureManifest).toByteArray(),
                    BackupIo.ENTRY_NOTES to """{"version":9999,"exportedAt":100,"notes":[]}""".toByteArray(),
                ),
            )

            val manifest = archiveManager.peekManifest(futureSchemaZip)
            assertTrue(manifest.schemaVersion > BackupIo.SCHEMA_VERSION)
        }
    }
}
