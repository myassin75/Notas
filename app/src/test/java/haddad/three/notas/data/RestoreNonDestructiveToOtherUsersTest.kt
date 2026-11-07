package haddad.three.notas.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task F04: Regression tests for Non-Destructive Restore across multiple accounts.
 * Verifies that restoreNotesFullReplace only purges the active user's partition,
 * preserving other authenticated accounts and guest notes completely intact.
 */
class RestoreNonDestructiveToOtherUsersTest {

    private lateinit var fakeNoteDao: IsolationTestNoteDao
    private lateinit var fakeChecklistDao: IsolationTestChecklistItemDao
    private lateinit var fakeAttachmentDao: IsolationTestAttachmentDao
    private var activeOwnerId: String? = null
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        fakeNoteDao = IsolationTestNoteDao()
        fakeChecklistDao = IsolationTestChecklistItemDao()
        fakeAttachmentDao = IsolationTestAttachmentDao()
        activeOwnerId = null
        repository = NoteRepository(
            noteDao = fakeNoteDao,
            itemDao = fakeChecklistDao,
            attachmentDao = fakeAttachmentDao,
            ownerIdProvider = { activeOwnerId },
        )
    }

    @Test
    fun `restore by account A does not destroy account B or guest notes`() = runBlocking {
        // Step 1: Guest creates a note
        activeOwnerId = null
        val guestNoteId = repository.createNote("Guest note", "Important local thoughts", 0)

        // Step 2: User Bob creates a note
        activeOwnerId = "user_bob"
        val bobNoteId = repository.createNote("Bob note", "Bob's private plans", 1)

        // Step 3: User Alice creates 2 notes
        activeOwnerId = "user_alice"
        val aliceOldNote1 = repository.createNote("Alice note 1", "To be replaced by restore", 2)
        val aliceOldNote2 = repository.createNote("Alice note 2", "Also replaced", 2)

        assertEquals("Total notes in DB should be 4", 4, fakeNoteDao.notes.size)

        // Step 4: Alice restores a backup containing 1 fresh note
        repository.restoreNotesFullReplace {
            repository.createNote("Alice restored note", "Restored from cloud backup", 3)
            1
        }

        // Verify:
        // 1. Guest note must still exist intact
        val guestNote = fakeNoteDao.notes[guestNoteId]
        assertNotNull("Guest note must NOT be deleted by Alice's restore", guestNote)
        assertEquals("Guest note", guestNote?.title)

        // 2. Bob's note must still exist intact
        val bobNote = fakeNoteDao.notes[bobNoteId]
        assertNotNull("Bob's note must NOT be deleted by Alice's restore", bobNote)
        assertEquals("Bob note", bobNote?.title)

        // 3. Alice's old notes are gone, and her new restored note is present
        assertTrue("Alice's old note 1 should be gone", fakeNoteDao.notes[aliceOldNote1] == null)
        assertTrue("Alice's old note 2 should be gone", fakeNoteDao.notes[aliceOldNote2] == null)
        assertEquals(
            "Alice should have exactly 1 restored note in DB",
            1,
            fakeNoteDao.notes.values.count { it.ownerId == "user_alice" }
        )
    }

    @Test
    fun `restore by guest does not destroy authenticated user notes`() = runBlocking {
        // Alice has a note
        activeOwnerId = "user_alice"
        val aliceNoteId = repository.createNote("Alice note", "Should survive guest restore", 0)

        // Guest has an old note
        activeOwnerId = null
        val guestOldId = repository.createNote("Guest old", "To be replaced", 0)

        // Guest restores backup
        repository.restoreNotesFullReplace {
            repository.createNote("Guest fresh", "Restored guest note", 0)
            1
        }

        // Alice note must be preserved
        assertNotNull("Alice note must NOT be deleted by guest restore", fakeNoteDao.notes[aliceNoteId])

        // Guest old note replaced by guest fresh note
        assertTrue("Guest old note should be removed", fakeNoteDao.notes[guestOldId] == null)
        assertEquals(
            "Guest should have exactly 1 note in DB",
            1,
            fakeNoteDao.notes.values.count { it.ownerId == null }
        )
    }
}

