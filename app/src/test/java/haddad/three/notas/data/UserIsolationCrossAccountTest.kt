package haddad.three.notas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser

/**
 * Task F04: Regression tests for User Isolation and Cross-Account boundaries.
 * Verifies that active notes, search, trash, and archive are strictly partitioned by ownerId.
 */
class UserIsolationCrossAccountTest {

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
    fun `cross account active notes isolation - User B cannot see User A active notes`() = runBlocking {
        // Step 1: User Alice creates a note
        activeOwnerId = "user_alice"
        val aliceNoteId = repository.createNote(
            title = "Alice Confidential Note",
            body = "Top secret details",
            colorIndex = 1,
        )

        // Step 2: Switch to User Bob
        activeOwnerId = "user_bob"
        val bobNoteId = repository.createNote(
            title = "Bob Public Note",
            body = "Bob's ideas",
            colorIndex = 2,
        )

        // Verify: User Bob observing active notes must only see Bob's note
        val bobActive = repository.observeActive().first()
        assertEquals("User Bob must see exactly 1 note", 1, bobActive.size)
        assertEquals("User Bob must see only his own note", bobNoteId, bobActive.first().note.id)
        assertFalse(
            "User Bob must not see Alice's note",
            bobActive.any { it.note.id == aliceNoteId }
        )

        // Step 3: Switch back to User Alice
        activeOwnerId = "user_alice"
        val aliceActive = repository.observeActive().first()
        assertEquals("User Alice must see exactly 1 note", 1, aliceActive.size)
        assertEquals("User Alice must see only her own note", aliceNoteId, aliceActive.first().note.id)
    }

    @Test
    fun `guest and authenticated account active notes isolation`() = runBlocking {
        // Step 1: Guest creates a note
        activeOwnerId = null
        val guestNoteId = repository.createNote(
            title = "Guest Local Note",
            body = "Not synced anywhere",
            colorIndex = 0,
        )

        // Step 2: User Alice signs in and creates a note
        activeOwnerId = "user_alice"
        val aliceNoteId = repository.createNote(
            title = "Alice Cloud Note",
            body = "Synced with backend",
            colorIndex = 3,
        )

        // Alice must not see Guest's note in active list
        val aliceActive = repository.observeActive().first()
        assertEquals("Alice must only see her own note", 1, aliceActive.size)
        assertEquals(aliceNoteId, aliceActive.first().note.id)

        // Guest session must only see Guest's note
        activeOwnerId = null
        val guestActive = repository.observeActive().first()
        assertEquals("Guest must only see guest note", 1, guestActive.size)
        assertEquals(guestNoteId, guestActive.first().note.id)
    }

    @Test
    fun `cross account search isolation - User B search query does not return User A notes`() = runBlocking {
        activeOwnerId = "user_alice"
        repository.createNote(
            title = "Quarterly Financial Secret",
            body = "Budget allocation for 2027",
            colorIndex = 0,
        )

        // Switch to Bob
        activeOwnerId = "user_bob"
        repository.createNote(
            title = "Grocery shopping list",
            body = "Apples, bananas",
            colorIndex = 0,
        )

        // Bob searches for "Financial" or "Secret"
        val bobResults = repository.searchNotes("Financial").first()
        assertTrue(
            "Bob's search for 'Financial' must be empty since only Alice owns that keyword",
            bobResults.isEmpty()
        )
    }

    @Test
    fun `cross account trash isolation - User B cannot view or empty User A trashed notes`() = runBlocking {
        // Alice trashes a note
        activeOwnerId = "user_alice"
        val aliceNoteId = repository.createNote(
            title = "Alice Deprecated Plan",
            body = "To be deleted",
            colorIndex = 0,
        )
        repository.moveToTrash(aliceNoteId)

        // Bob has his own note
        activeOwnerId = "user_bob"
        val bobNoteId = repository.createNote(
            title = "Bob Trash Candidate",
            body = "Bob wants to delete this",
            colorIndex = 0,
        )
        repository.moveToTrash(bobNoteId)

        // Bob checks trash
        val bobTrash = repository.observeTrashed().first()
        assertEquals("Bob must only see his 1 trashed note", 1, bobTrash.size)
        assertEquals(bobNoteId, bobTrash.first().note.id)

        // Bob empties trash
        repository.emptyTrash()

        // Bob's trash is now empty
        val bobTrashAfter = repository.observeTrashed().first()
        assertTrue("Bob's trash should now be empty", bobTrashAfter.isEmpty())

        // Alice's trashed note must NOT have been destroyed by Bob emptying his trash!
        activeOwnerId = "user_alice"
        val aliceTrash = repository.observeTrashed().first()
        assertEquals("Alice's trashed note must still exist intact", 1, aliceTrash.size)
        assertEquals(aliceNoteId, aliceTrash.first().note.id)
    }

    @Test
    fun `cross account archive isolation - User B cannot view User A archived notes`() = runBlocking {
        // Alice archives a note
        activeOwnerId = "user_alice"
        val aliceNoteId = repository.createNote(
            title = "Alice Old Project",
            body = "Archived material",
            colorIndex = 0,
        )
        repository.archiveNote(aliceNoteId)

        // Bob checks archive
        activeOwnerId = "user_bob"
        val bobArchive = repository.observeArchived().first()
        assertTrue("Bob's archive should be empty", bobArchive.isEmpty())
    }

    @Test
    fun `guest to account promotion flow migrates guest notes to authenticated user`() = runBlocking {
        // Step 1: Create notes as Guest
        activeOwnerId = null
        val guestNote1 = repository.createNote("Guest Idea 1", "Body 1", 0)
        val guestNote2 = repository.createNote("Guest Idea 2", "Body 2", 1)

        assertEquals("Guest should see 2 notes", 2, repository.observeActive().first().size)

        // Step 2: User registers / logs in as Alice
        val migratedCount = repository.migrateGuestDataToOwner("user_alice")
        assertEquals("Should migrate 2 notes", 2, migratedCount)

        // Step 3: Switch session to Alice
        activeOwnerId = "user_alice"
        val aliceActive = repository.observeActive().first()
        assertEquals("Alice should now see the 2 migrated notes", 2, aliceActive.size)
        assertTrue(aliceActive.any { it.note.id == guestNote1 && it.note.ownerId == "user_alice" })
        assertTrue(aliceActive.any { it.note.id == guestNote2 && it.note.ownerId == "user_alice" })

        // Step 4: Guest partition is now empty
        activeOwnerId = null
        val guestActive = repository.observeActive().first()
        assertTrue("Guest partition should now have 0 notes", guestActive.isEmpty())
    }

    @Test
    fun `guest migration merges into existing account without overwriting existing notes`() = runBlocking {
        // Step 1: Alice already has 2 notes in her account
        activeOwnerId = "user_alice"
        val aliceExisting1 = repository.createNote("Alice Existing 1", "Existing body 1", 1)
        val aliceExisting2 = repository.createNote("Alice Existing 2", "Existing body 2", 2)

        // Step 2: User logs out and creates 2 notes in Guest mode
        activeOwnerId = null
        val guestNote1 = repository.createNote("Guest Note 1", "Guest body 1", 3)
        val guestNote2 = repository.createNote("Guest Note 2", "Guest body 2", 4)

        // Step 3: Guest signs into Alice's account, triggering migration
        val migratedCount = repository.migrateGuestDataToOwner("user_alice")
        assertEquals("Must migrate 2 guest notes", 2, migratedCount)

        // Step 4: Verify Alice's account has ALL 4 notes (2 existing + 2 migrated)
        activeOwnerId = "user_alice"
        val aliceNotes = repository.observeActive().first()
        assertEquals("Alice should have exactly 4 notes after merge", 4, aliceNotes.size)

        // Check that Alice's existing notes were NOT overwritten or modified
        val existing1 = aliceNotes.find { it.note.id == aliceExisting1 }
        assertNotNull("Alice existing note 1 must still exist", existing1)
        assertEquals("Alice Existing 1", existing1?.note?.title)

        val existing2 = aliceNotes.find { it.note.id == aliceExisting2 }
        assertNotNull("Alice existing note 2 must still exist", existing2)
        assertEquals("Alice Existing 2", existing2?.note?.title)

        // Check that guest notes were migrated and assigned to Alice
        val migrated1 = aliceNotes.find { it.note.id == guestNote1 }
        assertNotNull("Migrated guest note 1 must exist in Alice's list", migrated1)
        assertEquals("user_alice", migrated1?.note?.ownerId)

        val migrated2 = aliceNotes.find { it.note.id == guestNote2 }
        assertNotNull("Migrated guest note 2 must exist in Alice's list", migrated2)
        assertEquals("user_alice", migrated2?.note?.ownerId)

        // Check that Guest partition is now 0
        activeOwnerId = null
        assertTrue("Guest notes must be 0", repository.observeActive().first().isEmpty())
    }

    @Test
    fun `guest migration atomicity across notes checklist attachments and MEK`() = runBlocking {
        // Step 1: Set up MEK manager with in-memory storage
        var currentAccount: String? = null
        val keyStore = haddad.three.notas.crypto.SoftwareKeyStoreManager()
        val storage = haddad.three.notas.crypto.InMemoryCryptoStorage(accountIdProvider = { currentAccount })
        val mekManager = haddad.three.notas.crypto.MekManager(
            keyStoreManager = keyStore,
            storage = storage,
            context = null,
            accountIdProvider = { currentAccount },
        )

        // Step 2: Guest creates a MEK and encrypted data
        currentAccount = null
        activeOwnerId = null
        val guestMek = mekManager.getOrCreateMasterKey()
        assertNotNull("Guest MEK must be created", guestMek)

        // Guest creates a note with checklist item and attachment
        val noteId = repository.createNote("Guest Master Note", "Body", 0)
        fakeChecklistDao.insert(
            ChecklistItemEntity(
                id = 1L,
                uuid = "item-uuid-1",
                ownerId = null,
                noteId = noteId,
                text = "Guest item",
                checked = false,
                sortOrder = 1.0,
                parentId = null,
                depth = 0,
                details = "",
            )
        )
        fakeAttachmentDao.insert(
            NoteAttachmentEntity(
                id = 1L,
                uuid = "att-uuid-1",
                ownerId = null,
                noteId = noteId,
                uri = "file:///data/guest_img.png",
                displayName = "guest_img.png",
                mimeType = "image/png",
            )
        )

        // Step 3: Trigger combined migration to Alice (MEK + Notes + Checklists + Attachments)
        val targetUser = "user_alice"
        mekManager.migrateGuestKeysToAccount(targetUser)
        val migratedNotesCount = repository.migrateGuestDataToOwner(targetUser)

        assertEquals(1, migratedNotesCount)

        // Step 4: Verify MEK belongs to Alice and decrypts accurately
        currentAccount = targetUser
        val aliceMek = mekManager.getOrCreateMasterKey()
        org.junit.Assert.assertArrayEquals(
            "Alice MEK must match migrated guest MEK",
            guestMek.encoded,
            aliceMek.encoded,
        )

        // Step 5: Verify note, checklist items, and attachments all transferred to Alice
        activeOwnerId = targetUser
        val aliceNotes = repository.observeActive().first()
        assertEquals(1, aliceNotes.size)
        assertEquals(targetUser, aliceNotes.first().note.ownerId)

        val aliceItems = fakeChecklistDao.itemsFor(noteId)
        assertEquals(1, aliceItems.size)
        assertEquals(targetUser, aliceItems.first().ownerId)

        val aliceAttachments = fakeAttachmentDao.attachmentsFor(noteId)
        assertEquals(1, aliceAttachments.size)
        assertEquals(targetUser, aliceAttachments.first().ownerId)
    }

    @Test
    fun `simulated crash during guest migration with notes migrated but MEK not migrated documents end state and verifies recovery`() = runBlocking {
        // Step 1: Set up MEK manager with in-memory storage
        var currentAccount: String? = null
        val keyStore = haddad.three.notas.crypto.SoftwareKeyStoreManager()
        val storage = haddad.three.notas.crypto.InMemoryCryptoStorage(accountIdProvider = { currentAccount })
        val mekManager = haddad.three.notas.crypto.MekManager(
            keyStoreManager = keyStore,
            storage = storage,
            context = null,
            accountIdProvider = { currentAccount },
        )

        // Step 2: Guest creates a note and has an active MEK
        currentAccount = null
        activeOwnerId = null
        val guestMek = mekManager.getOrCreateMasterKey()
        assertNotNull(guestMek)

        val noteId = repository.createNote("Guest Note Before Crash", "Secret Content", 0)
        assertEquals(1, repository.observeActive().first().size)

        // Step 3: Simulate crash during migration:
        // Room migrates notes to Alice, BUT a crash occurs before migrateGuestKeysToAccount is called
        val targetUser = "user_alice"
        val count = repository.migrateGuestDataToOwner(targetUser)
        assertEquals(1, count)

        // Document the end state after this crash:
        // 1. In Room DB: note is already claimed by Alice
        activeOwnerId = targetUser
        val aliceNotes = repository.observeActive().first()
        assertEquals(1, aliceNotes.size)
        assertEquals(targetUser, aliceNotes.first().note.ownerId)

        // 2. In Keystore/Storage: Alice has NO active MEK recorded yet
        assertNull("Alice has no MEK in storage because crash aborted before MEK migration", mekManager.getActiveMasterKey(targetUser))

        // 3. In Guest Keystore/Storage: Guest MEK is still preserved intact
        val preservedGuestMek = mekManager.getActiveMasterKey(haddad.three.notas.crypto.CryptoStorage.GUEST_ACCOUNT_ID)
        assertNotNull("Guest MEK must remain preserved in storage", preservedGuestMek)
        org.junit.Assert.assertArrayEquals(guestMek.encoded, preservedGuestMek!!.encoded)

        // Step 4: Recovery step
        // To fix this inconsistency, on next app startup / auth reconciliation,
        // invoking migrateGuestKeysToAccount(targetUser) restores the key to Alice
        mekManager.migrateGuestKeysToAccount(targetUser)

        currentAccount = targetUser
        val aliceMek = mekManager.getActiveMasterKey()
        assertNotNull("Alice now has an active MEK after recovery", aliceMek)
        org.junit.Assert.assertArrayEquals(
            "Alice MEK matches the preserved guest MEK, ensuring zero data loss",
            guestMek.encoded,
            aliceMek!!.encoded,
        )
    }
}

/**
 * In-memory test NoteDao simulating unpartitioned baseline behavior (prior to F04 fix).
 */
typealias IsolationTestNoteDao = BaseFakeNoteDao
typealias IsolationTestChecklistItemDao = BaseFakeChecklistItemDao
typealias IsolationTestAttachmentDao = BaseFakeAttachmentDao
