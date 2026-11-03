package haddad.three.notas.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSafetyRegressionTest {

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            data.value = updated
            return updated
        }
    }

    private class FakeAuthRepository(
        var user: AuthUser? = null,
        var token: String? = "valid-token",
    ) : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? get() = user

        override suspend fun registerWithEmailAndPassword(email: String, password: String, username: String?): AuthResult<AuthUser> =
            user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun loginWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser> =
            user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun signInWithGoogle(context: android.content.Context): AuthResult<AuthUser> =
            user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun reloadUser(): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")
        override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun confirmPasswordReset(actionCode: String, newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = AuthResult.Success("test@example.com")
        override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)
        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("Not implemented")
        override suspend fun signOut() { user = null }
        override suspend fun getIdToken(forceRefresh: Boolean): String? = token
    }

    private class TestNoteDao : NoteDao {
        val notes = mutableMapOf<Long, NoteEntity>()
        var idCounter = 1L

        override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(notes[id]?.let { NoteWithItems(it, emptyList(), emptyList(), null) })
        override suspend fun get(id: Long): NoteWithItems? = notes[id]?.let { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun getByUuid(uuid: String): NoteWithItems? =
            notes.values.find { it.uuid == uuid }?.let { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes.values.find { it.uuid == uuid }
        override suspend fun allNotes(): List<NoteWithItems> =
            notes.values.map { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> =
            notes.values.filter { it.ownerId == ownerId }.map { NoteWithItems(it, emptyList(), emptyList(), null) }
        override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()
        override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
        override suspend fun insert(note: NoteEntity): Long {
            val id = if (note.id != 0L) note.id else idCounter++
            notes[id] = note.copy(id = id)
            return id
        }
        override suspend fun update(note: NoteEntity) { notes[note.id] = note }
        override suspend fun setTrashed(id: Long, trashed: Boolean, updatedAt: Long) {
            notes[id]?.let { notes[id] = it.copy(trashed = trashed, updatedAt = updatedAt) }
        }
        override suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long) {
            notes[id]?.let { notes[id] = it.copy(archived = archived, updatedAt = updatedAt) }
        }
        override suspend fun deleteById(id: Long) { notes.remove(id) }
        override suspend fun allNoteIds(): List<Long> = notes.keys.toList()
        override suspend fun countPictureUri(uri: String): Int = notes.values.count { it.pictureUri == uri }
        override suspend fun trashedNoteIds(): List<Long> = notes.values.filter { it.trashed }.map { it.id }
        override suspend fun archivedNoteIds(): List<Long> = notes.values.filter { it.archived }.map { it.id }
        override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()
        override suspend fun deleteAllNotes() { notes.clear() }
        override suspend fun emptyTrash() { notes.entries.removeIf { it.value.trashed } }
        override suspend fun deleteTrashedOlderThan(cutoff: Long) {}
    }

    private class TestShoulderDao : ShoulderDao {
        val shoulders = mutableMapOf<Long, ShoulderEntity>()
        var idCounter = 1L

        override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders.values.toList())
        override suspend fun allShoulders(): List<ShoulderEntity> = shoulders.values.toList()
        override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> =
            shoulders.values.filter { it.ownerId == ownerId }
        override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())
        override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders[id]
        override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders.values.find { it.uuid == uuid }
        override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.values.find { it.normalizedName == normalizedName }
        override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()
        override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
            val id = if (shoulder.id != 0L) shoulder.id else idCounter++
            shoulders[id] = shoulder.copy(id = id)
            return id
        }
        override suspend fun updateShoulder(shoulder: ShoulderEntity) { shoulders[shoulder.id] = shoulder }
        override suspend fun deleteShoulder(shoulder: ShoulderEntity) { shoulders.remove(shoulder.id) }
        override suspend fun setShoulderForNote(noteId: Long, shoulderId: Long?) {}
    }

    private class TestChecklistItemDao : ChecklistItemDao {
        val items = mutableMapOf<Long, ChecklistItemEntity>()
        var idCounter = 1L

        override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = items.values.filter { it.noteId == noteId }
        override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = items.values.find { it.uuid == uuid }
        override suspend fun insert(item: ChecklistItemEntity): Long {
            val id = if (item.id != 0L) item.id else idCounter++
            items[id] = item.copy(id = id)
            return id
        }
        override suspend fun insertAll(items: List<ChecklistItemEntity>) { items.forEach { insert(it) } }
        override suspend fun update(item: ChecklistItemEntity) { items[item.id] = item }
        override suspend fun deleteById(id: Long) { items.remove(id) }
        override suspend fun deleteForNote(noteId: Long) { items.entries.removeIf { it.value.noteId == noteId } }
    }

    private class TestAttachmentDao : AttachmentDao {
        val attachments = mutableMapOf<Long, NoteAttachmentEntity>()
        var idCounter = 1L

        override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = attachments.values.filter { it.noteId == noteId }
        override suspend fun insert(attachment: NoteAttachmentEntity): Long {
            val id = if (attachment.id != 0L) attachment.id else idCounter++
            attachments[id] = attachment.copy(id = id)
            return id
        }
        override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) { attachments.forEach { insert(it) } }
        override suspend fun getById(id: Long): NoteAttachmentEntity? = attachments[id]
        override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = attachments.values.find { it.uuid == uuid }
        override suspend fun countByUri(uri: String): Int = attachments.values.count { it.uri == uri }
        override suspend fun deleteById(id: Long) { attachments.remove(id) }
        override suspend fun deleteForNote(noteId: Long) { attachments.entries.removeIf { it.value.noteId == noteId } }
    }

    private class MockSyncApiClient : SyncApiClient({ url ->
        (URL("https://example.com").openConnection() as HttpURLConnection)
    }) {
        var lastPushedPayload: SyncPushRequestDto? = null
        var pushResultToReturn: SyncResult<SyncPushResponseDto> = SyncResult.Success(
            SyncPushResponseDto(serverRevision = "100", appliedCount = SyncAppliedCountDto()),
        )
        var fetchResultToReturn: SyncResult<SyncChangesResponseDto> = SyncResult.Success(
            SyncChangesResponseDto(currentServerRevision = "100", hasMore = false),
        )

        override suspend fun pushSync(idToken: String, payload: SyncPushRequestDto): SyncResult<SyncPushResponseDto> {
            lastPushedPayload = payload
            return pushResultToReturn
        }

        override suspend fun fetchChanges(idToken: String, sinceRevision: String, limit: Int): SyncResult<SyncChangesResponseDto> {
            return fetchResultToReturn
        }
    }

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var syncStateStore: SyncStateStore
    private lateinit var syncApiClient: MockSyncApiClient
    private lateinit var noteDao: TestNoteDao
    private lateinit var shoulderDao: TestShoulderDao
    private lateinit var itemDao: TestChecklistItemDao
    private lateinit var attachmentDao: TestAttachmentDao
    private lateinit var syncEngine: SyncEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        authRepository = FakeAuthRepository()
        syncStateStore = SyncStateStore(MemoryPreferencesDataStore())
        syncApiClient = MockSyncApiClient()
        noteDao = TestNoteDao()
        shoulderDao = TestShoulderDao()
        itemDao = TestChecklistItemDao()
        attachmentDao = TestAttachmentDao()

        syncEngine = SyncEngine(
            authRepository = authRepository,
            syncStateStore = syncStateStore,
            syncApiClient = syncApiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            scheduler = null,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun guest_to_login_transition_never_uploads_guest_records() = runTest(testDispatcher) {
        // 1. Populate guest records in local Room (ownerId = null)
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "guest-note-1",
                ownerId = null,
                kind = NoteKind.NOTE,
                title = "Guest Secret",
                body = "Private guest thoughts",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        shoulderDao.insertShoulder(
            ShoulderEntity(
                id = 1L,
                uuid = "guest-shoulder-1",
                ownerId = null,
                name = "Guest Category",
                normalizedName = "guest category",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        // 2. User logs in with Firebase UID "user_alice"
        authRepository.user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true)

        // 3. Perform sync
        val result = syncEngine.synchronize()
        assertTrue(result is SyncResult.Success)

        // 4. Assert: Sync push was NOT invoked or payload contained 0 categories and 0 notes
        val payload = syncApiClient.lastPushedPayload
        if (payload != null) {
            assertTrue("Pushed notes must not contain guest records", payload.notes.isEmpty())
            assertTrue("Pushed categories must not contain guest records", payload.categories.isEmpty())
        }

        // 5. Guest records remain intact in Room with ownerId = null
        val guestNote = noteDao.getByUuid("guest-note-1")
        assertNotNull(guestNote)
        assertNull(guestNote?.note?.ownerId)
    }

    @Test
    fun account_switching_never_exposes_or_pushes_other_account_records() = runTest(testDispatcher) {
        // 1. User Alice created a note
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "alice-note-1",
                ownerId = "user_alice",
                kind = NoteKind.NOTE,
                title = "Alice's Project",
                body = "Classified",
                colorIndex = 1,
                trashed = false,
                createdAt = 2000L,
                updatedAt = 2000L,
            ),
        )

        // 2. User Bob signs in
        authRepository.user = AuthUser(uid = "user_bob", email = "bob@example.com", isEmailVerified = true)

        // 3. User Bob performs sync
        val result = syncEngine.synchronize()
        assertTrue(result is SyncResult.Success)

        // 4. Bob's sync payload must NOT include Alice's notes
        val payload = syncApiClient.lastPushedPayload
        if (payload != null) {
            assertFalse(payload.notes.any { it.id == "alice-note-1" })
        }

        // 5. Querying notes for Bob returns 0 notes
        val bobNotes = noteDao.notesForOwner("user_bob")
        assertTrue(bobNotes.isEmpty())

        // 6. Querying notes for Alice still preserves Alice's data safely
        val aliceNotes = noteDao.notesForOwner("user_alice")
        assertEquals(1, aliceNotes.size)
        assertEquals("Alice's Project", aliceNotes[0].note.title)
    }

    @Test
    fun push_conflict_advances_watermark_only_for_confirmed_applied_records() = runTest(testDispatcher) {
        authRepository.user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true)

        // Two notes with different update timestamps
        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note-applied",
                ownerId = "user_alice",
                kind = NoteKind.NOTE,
                title = "Applied Note",
                body = "OK",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 2000L,
            ),
        )
        noteDao.insert(
            NoteEntity(
                id = 2L,
                uuid = "note-conflicted",
                ownerId = "user_alice",
                kind = NoteKind.NOTE,
                title = "Conflicted Note",
                body = "Conflict",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 3000L,
            ),
        )

        // Backend confirms note-applied applied, but reports conflict on note-conflicted
        syncApiClient.pushResultToReturn = SyncResult.Success(
            SyncPushResponseDto(
                serverRevision = "200",
                appliedCount = SyncAppliedCountDto(notes = 1),
                conflicts = listOf(
                    SyncPushConflictDto(
                        entityType = "note",
                        id = "note-conflicted",
                        serverRevision = "200",
                        reason = "Server revision is newer",
                    ),
                ),
            ),
        )

        val result = syncEngine.synchronize()
        assertTrue(result is SyncResult.Success)

        // Watermark should advance to 2000L (the timestamp of note-applied), NOT 3000L (note-conflicted)
        val lastPushedAt = syncStateStore.getLastPushedAt("user_alice")
        assertEquals(2000L, lastPushedAt)

        // Conflicts must be recorded and accessible without sensitive data
        val conflicts = syncStateStore.getConflicts("user_alice")
        assertEquals(1, conflicts.size)
        assertEquals("note-conflicted", conflicts[0].entityId)
        assertEquals("Server revision is newer", conflicts[0].reason)
    }

    @Test
    fun push_conflict_retains_rejected_tombstones() = runTest(testDispatcher) {
        authRepository.user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true)

        // Record two local tombstones
        syncStateStore.addTombstones(
            "user_alice",
            listOf(
                SyncTombstone(entityType = "note", uuid = "tomb-success", deletedAt = 5000L),
                SyncTombstone(entityType = "note", uuid = "tomb-rejected", deletedAt = 5000L),
            ),
        )

        // Backend accepts tomb-success, but rejects tomb-rejected due to conflict
        syncApiClient.pushResultToReturn = SyncResult.Success(
            SyncPushResponseDto(
                serverRevision = "300",
                appliedCount = SyncAppliedCountDto(notes = 1),
                conflicts = listOf(
                    SyncPushConflictDto(
                        entityType = "note",
                        id = "tomb-rejected",
                        serverRevision = "300",
                        reason = "Entity already archived on server",
                    ),
                ),
            ),
        )

        val result = syncEngine.synchronize()
        assertTrue(result is SyncResult.Success)

        // Tombstones in store: tomb-success must be removed, tomb-rejected must be RETAINED
        val remainingTombstones = syncStateStore.getTombstones("user_alice")
        assertEquals(1, remainingTombstones.size)
        assertEquals("tomb-rejected", remainingTombstones[0].uuid)
    }

    @Test
    fun pull_does_not_resurrect_records_with_pending_local_deletion_tombstones() = runTest(testDispatcher) {
        authRepository.user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true)

        // Client deleted "note-deleted-locally" at epoch 10,000L
        syncStateStore.addTombstones(
            "user_alice",
            listOf(
                SyncTombstone(entityType = "note", uuid = "note-deleted-locally", deletedAt = 10000L),
            ),
        )

        // Server sends changes with older update timestamp (9,000L) for the same note
        val changes = SyncChangesResponseDto(
            currentServerRevision = "400",
            notes = listOf(
                NoteSyncDto(
                    id = "note-deleted-locally",
                    kind = "NOTE",
                    title = "Zombie Note",
                    body = "Should not resurrect!",
                    clientCreatedAt = "1970-01-01T00:00:01Z",
                    clientUpdatedAt = "1970-01-01T00:00:09Z", // 9000 ms epoch, older than local deletion tombstone at 10000L
                    isDeleted = false,
                ),
            ),
        )

        // Apply changes
        syncEngine.applyRemoteChanges("user_alice", changes)

        // Assert: The note was NOT inserted/resurrected into Room
        val resurrected = noteDao.getByUuid("note-deleted-locally")
        assertNull("Locally deleted note must not be resurrected by stale remote pull", resurrected)
    }

    @Test
    fun safe_pagination_guard_terminates_loop_when_cursor_does_not_advance() = runTest(testDispatcher) {
        authRepository.user = AuthUser(uid = "user_alice", email = "alice@example.com", isEmailVerified = true)

        // Server responds with hasMore = true but cursor is stuck at "rev-50" and empty changes
        syncApiClient.fetchResultToReturn = SyncResult.Success(
            SyncChangesResponseDto(
                currentServerRevision = "rev-50",
                hasMore = true,
                notes = emptyList(),
                categories = emptyList(),
            ),
        )
        syncStateStore.setSyncCursor("user_alice", "rev-50")

        // Must terminate safely and not loop infinitely
        val result = syncEngine.synchronize()
        assertTrue(result is SyncResult.Success)
    }
}
