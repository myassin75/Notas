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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import haddad.three.notas.ui.components.SyncStatus
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

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
        var forceRefreshSucceeds: Boolean = true,
    ) : AuthRepository {
        var tokenRefreshCallCount = 0

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
        override suspend fun signOut() { user = null }

        override suspend fun getIdToken(forceRefresh: Boolean): String? {
            if (forceRefresh) {
                tokenRefreshCallCount++
                if (!forceRefreshSucceeds) return null
                token = "refreshed-token-$tokenRefreshCallCount"
            }
            return token
        }

        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("Not implemented")
    }

    private class InMemoryNoteDao : haddad.three.notas.data.BaseFakeNoteDao()
    private class InMemoryChecklistItemDao : haddad.three.notas.data.BaseFakeChecklistItemDao()
    private class InMemoryAttachmentDao : haddad.three.notas.data.BaseFakeAttachmentDao()
    private class InMemoryShoulderDao : haddad.three.notas.data.BaseFakeShoulderDao()
    private class MockSyncApiClient : SyncApiClient({ url ->
        (URL("https://example.com").openConnection() as HttpURLConnection)
    }) {
        var pushCallCount = 0
        var fetchCallCount = 0
        var lastPushedPayload: SyncPushRequestDto? = null
        val pushedPayloads = mutableListOf<SyncPushRequestDto>()

        /**
         * Optional FIFO queue of push results. When non-empty each push call consumes the
         * next entry (multi-batch drainage); when empty `pushResultToReturn` is used.
         */
        val pushResultQueue = ArrayDeque<SyncResult<SyncPushResponseDto>>()

        var pushResultToReturn: SyncResult<SyncPushResponseDto> = SyncResult.Success(
            SyncPushResponseDto(
                serverRevision = "1000",
                appliedCount = SyncAppliedCountDto(categories = 1, notes = 1),
            ),
        )

        var fetchResultToReturn: SyncResult<SyncChangesResponseDto> = SyncResult.Success(
            SyncChangesResponseDto(
                currentServerRevision = "1000",
                categories = emptyList(),
                notes = emptyList(),
                hasMore = false,
            ),
        )

        override suspend fun pushSync(idToken: String, payload: SyncPushRequestDto): SyncResult<SyncPushResponseDto> {
            pushCallCount++
            pushedPayloads.add(payload)
            lastPushedPayload = payload
            return if (pushResultQueue.isNotEmpty()) pushResultQueue.removeFirst() else pushResultToReturn
        }

        override suspend fun fetchChanges(idToken: String, sinceRevision: String, limit: Int): SyncResult<SyncChangesResponseDto> {
            fetchCallCount++
            return fetchResultToReturn
        }
    }

    @Test
    fun `synchronize in guest mode returns Offline without calling API`() = runTest {
        val authRepo = FakeAuthRepository(user = null)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Offline)
        assertEquals(SyncStatus.NOT_CONNECTED, stateStore.syncStatus.value)
        assertEquals(0, apiClient.pushCallCount)
        assertEquals(0, apiClient.fetchCallCount)
    }

    @Test
    fun `successful push sends local modifications and clears tombstones`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // Seed a local note and a tombstone
        val noteId = noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note-uuid-1",
                ownerId = "user-1",
                kind = NoteKind.NOTE,
                title = "Local Note",
                body = "Body",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 2000L,
            ),
        )

        stateStore.addTombstones("user-1", listOf(SyncTombstone(entityType = "note", uuid = "deleted-note-uuid", deletedAt = 2005L)))

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Success)
        assertEquals(1, apiClient.pushCallCount)
        val pushed = apiClient.lastPushedPayload
        assertNotNull(pushed)
        assertEquals(2, pushed!!.notes.size)

        // One active note, one tombstone
        val activePushed = pushed.notes.find { it.id == "note-uuid-1" }
        assertNotNull(activePushed)
        assertFalse(activePushed!!.isDeleted)

        val tombstonePushed = pushed.notes.find { it.id == "deleted-note-uuid" }
        assertNotNull(tombstonePushed)
        assertTrue(tombstonePushed!!.isDeleted)

        // Tombstones cleared
        assertTrue(stateStore.getTombstones("user-1").isEmpty())
        assertTrue(stateStore.getLastPushedAt("user-1") > 0L)
        assertEquals(SyncStatus.SYNCED, stateStore.syncStatus.value)
    }

    @Test
    fun `successful pull inserts new records and updates existing records preserving Long IDs`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // Seed existing local category and note
        val existingCatId = shoulderDao.insertShoulder(
            ShoulderEntity(id = 50L, uuid = "cat-uuid-existing", name = "Old Name", normalizedName = "old name", createdAt = 100L, updatedAt = 100L),
        )
        val existingNoteId = noteDao.insert(
            NoteEntity(id = 100L, uuid = "note-uuid-existing", kind = NoteKind.NOTE, title = "Old Title", body = "Old Body", colorIndex = 0, trashed = false, createdAt = 100L, updatedAt = 100L),
        )

        // Remote changes:
        // 1. Update existing category
        // 2. Insert new category
        // 3. Update existing note
        // 4. Insert new note with checklist items
        apiClient.fetchResultToReturn = SyncResult.Success(
            SyncChangesResponseDto(
                currentServerRevision = "5000",
                categories = listOf(
                    CategorySyncDto(id = "cat-uuid-existing", name = "Updated Work", normalizedName = "updated work", clientCreatedAt = "2026-01-01T00:00:00Z", clientUpdatedAt = "2026-01-02T00:00:00Z"),
                    CategorySyncDto(id = "cat-uuid-new", name = "Personal", normalizedName = "personal", clientCreatedAt = "2026-01-01T00:00:00Z", clientUpdatedAt = "2026-01-02T00:00:00Z"),
                ),
                notes = listOf(
                    NoteSyncDto(id = "note-uuid-existing", kind = "NOTE", title = "New Title", body = "New Body", clientCreatedAt = "2026-01-01T00:00:00Z", clientUpdatedAt = "2026-01-02T00:00:00Z"),
                    NoteSyncDto(
                        id = "note-uuid-new",
                        kind = "LIST",
                        title = "Remote List",
                        body = "",
                        categoryId = "cat-uuid-new",
                        clientCreatedAt = "2026-01-01T00:00:00Z",
                        clientUpdatedAt = "2026-01-02T00:00:00Z",
                        checklistItems = listOf(
                            ChecklistItemSyncDto(id = "item-uuid-1", text = "First task", sortOrder = 0.0),
                            ChecklistItemSyncDto(id = "item-uuid-2", text = "Child task", sortOrder = 1.0, parentId = "item-uuid-1"),
                        ),
                    ),
                ),
                hasMore = false,
            ),
        )

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Success)

        // Verify category preservation and update
        val updatedCat = shoulderDao.getByUuid("cat-uuid-existing")
        assertNotNull(updatedCat)
        assertEquals(existingCatId, updatedCat!!.id) // Preserved Long ID 50
        assertEquals("Updated Work", updatedCat.name)

        val newCat = shoulderDao.getByUuid("cat-uuid-new")
        assertNotNull(newCat)
        assertTrue(newCat!!.id > 0L)
        assertEquals("Personal", newCat.name)

        // Verify note preservation and update
        val updatedNote = noteDao.getByUuid("note-uuid-existing")
        assertNotNull(updatedNote)
        assertEquals(existingNoteId, updatedNote!!.note.id) // Preserved Long ID 100
        assertEquals("New Title", updatedNote.note.title)

        // Verify new note and its checklist items
        val newNote = noteDao.getByUuid("note-uuid-new")
        assertNotNull(newNote)
        assertTrue(newNote!!.note.id > 0L)
        assertEquals("Remote List", newNote.note.title)
        assertEquals(newCat.id, newNote.note.shoulderId) // Category relationship mapped to local Long ID

        val items = itemDao.itemsFor(newNote.note.id)
        assertEquals(2, items.size)
        val parentItem = items.find { it.uuid == "item-uuid-1" }
        val childItem = items.find { it.uuid == "item-uuid-2" }
        assertNotNull(parentItem)
        assertNotNull(childItem)
        assertNull(parentItem!!.parentId)
        assertEquals(parentItem.id, childItem!!.parentId) // Child parentId correctly mapped to parent's local Long ID

        assertEquals("5000", stateStore.getSyncCursor("user-1"))
    }

    @Test
    fun `remote deletion removes local record`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // Seed note locally
        val id = noteDao.insert(
            NoteEntity(id = 1L, uuid = "to-be-deleted-uuid", kind = NoteKind.NOTE, title = "Will Delete", body = "", colorIndex = 0, trashed = false, createdAt = 0L, updatedAt = 0L),
        )
        assertNotNull(noteDao.get(id))

        // Remote deletion change
        apiClient.fetchResultToReturn = SyncResult.Success(
            SyncChangesResponseDto(
                currentServerRevision = "9999",
                notes = listOf(
                    NoteSyncDto(id = "to-be-deleted-uuid", kind = "NOTE", title = "", clientCreatedAt = "", clientUpdatedAt = "", isDeleted = true),
                ),
                hasMore = false,
            ),
        )

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Success)
        assertNull(noteDao.get(id))
        assertNull(noteDao.getByUuid("to-be-deleted-uuid"))
    }

    @Test
    fun `auth 401 triggers token refresh and retries`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // First push returns Unauthorized
        apiClient.pushResultToReturn = SyncResult.Unauthorized

        // Seed a note so push is triggered
        noteDao.insert(NoteEntity(id = 1L, uuid = "uuid-1", ownerId = "user-1", kind = NoteKind.NOTE, title = "Title", body = "", colorIndex = 0, trashed = false, createdAt = 100L, updatedAt = 200L))

        val result = engine.synchronize()

        // Token refresh was attempted
        assertEquals(1, authRepo.tokenRefreshCallCount)
    }

    // ---------------------------------------------------------------------------------
    // Audit AUD-01 regressions: watermark must only advance over records that were
    // actually pushed. The old implementation sent only `take(500)` per list but advanced
    // the watermark over the FULL modified lists, permanently skipping records 501+.
    // ---------------------------------------------------------------------------------
    @Test
    fun `push is drained in batches and watermark advances to max applied after full drain`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // 600 modified notes => two batches of 500 + 100
        val maxUpdatedAt = 100_000L + 599L
        repeat(600) { i ->
            noteDao.insert(
                NoteEntity(
                    id = i + 1L,
                    uuid = "note-uuid-$i",
                    ownerId = "user-1",
                    kind = NoteKind.NOTE,
                    title = "Note $i",
                    body = "",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = 90_000L,
                    updatedAt = 100_000L + i,
                ),
            )
        }

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Success)
        assertEquals(2, apiClient.pushCallCount)
        assertEquals(500, apiClient.pushedPayloads[0].notes.size)
        assertEquals(100, apiClient.pushedPayloads[1].notes.size)

        // First-batch boundaries: records 501+ only appear in the second batch
        assertTrue(apiClient.pushedPayloads[0].notes.any { it.id == "note-uuid-0" })
        assertFalse(apiClient.pushedPayloads[0].notes.any { it.id == "note-uuid-599" })
        assertTrue(apiClient.pushedPayloads[1].notes.any { it.id == "note-uuid-599" })

        // Complete drain: watermark advanced to the max applied updatedAt
        assertEquals(maxUpdatedAt, stateStore.getLastPushedAt("user-1"))
    }

    @Test
    fun `watermark does not advance when a batch fails mid-drain`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val maxUpdatedAt = 100_000L + 599L
        repeat(600) { i ->
            noteDao.insert(
                NoteEntity(
                    id = i + 1L,
                    uuid = "note-uuid-$i",
                    ownerId = "user-1",
                    kind = NoteKind.NOTE,
                    title = "Note $i",
                    body = "",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = 90_000L,
                    updatedAt = 100_000L + i,
                ),
            )
        }

        // Batch 1 applies, batch 2 fails mid-drain
        apiClient.pushResultQueue.addLast(
            SyncResult.Success(
                SyncPushResponseDto(
                    serverRevision = "r1",
                    appliedCount = SyncAppliedCountDto(categories = 0, notes = 500),
                ),
            ),
        )
        apiClient.pushResultQueue.addLast(SyncResult.Error("network drop during batch 2"))

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Error)
        assertEquals(2, apiClient.pushCallCount)

        // Records 501..600 were never applied — advancing the watermark over them would
        // orphan them forever (the original defect). Watermark stays untouched so the
        // next sync re-pushes the entire pending set (idempotent under LWW).
        assertEquals(0L, stateStore.getLastPushedAt("user-1"))
        // Sanity: the failed run must not have reported a state beyond what was applied
        assertFalse(stateStore.getLastPushedAt("user-1") >= maxUpdatedAt)
    }

    @Test
    fun `watermark skips records rejected by the server`() = runTest {
        val user = AuthUser(uid = "user-1", email = "user@test.com", isEmailVerified = true)
        val authRepo = FakeAuthRepository(user = user)
        val stateStore = SyncStateStore(MemoryPreferencesDataStore())
        val apiClient = MockSyncApiClient()
        val noteDao = InMemoryNoteDao()
        val itemDao = InMemoryChecklistItemDao()
        val attachmentDao = InMemoryAttachmentDao()
        val shoulderDao = InMemoryShoulderDao()

        val engine = SyncEngine(
            authRepository = authRepo,
            syncStateStore = stateStore,
            syncApiClient = apiClient,
            database = null,
            noteDao = noteDao,
            itemDao = itemDao,
            attachmentDao = attachmentDao,
            shoulderDao = shoulderDao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        noteDao.insert(
            NoteEntity(
                id = 1L,
                uuid = "note-uuid-conflicted",
                ownerId = "user-1",
                kind = NoteKind.NOTE,
                title = "Stale local edit",
                body = "",
                colorIndex = 0,
                trashed = false,
                createdAt = 0L,
                updatedAt = 5000L,
            ),
        )

        apiClient.pushResultToReturn = SyncResult.Success(
            SyncPushResponseDto(
                serverRevision = "1000",
                appliedCount = SyncAppliedCountDto(categories = 0, notes = 0),
                conflicts = listOf(
                    SyncPushConflictDto(
                        entityType = "note",
                        id = "note-uuid-conflicted",
                        serverRevision = "900",
                        reason = "Stale revision",
                    ),
                ),
            ),
        )

        val result = engine.synchronize()

        assertTrue(result is SyncResult.Success)
        assertEquals(1, apiClient.pushCallCount)
        // The only modified record was conflicted — nothing was applied, so the watermark must not move
        assertEquals(0L, stateStore.getLastPushedAt("user-1"))
    }
}
