package haddad.three.notas.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTombstoneRecorderTest {
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
    ) : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override val currentUser: AuthUser? get() = user

        override suspend fun registerWithEmailAndPassword(
            email: String,
            password: String,
            username: String?,
        ): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun loginWithEmailAndPassword(
            email: String,
            password: String,
        ): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun signInWithGoogle(context: android.content.Context): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun reloadUser(): AuthResult<AuthUser> = user?.let { AuthResult.Success(it) } ?: AuthResult.Error("No user")

        override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun confirmPasswordReset(
            actionCode: String,
            newPassword: String,
        ): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = AuthResult.Success("test@example.com")

        override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = AuthResult.Success(Unit)

        override suspend fun signOut() {
            user = null
        }

        override suspend fun getIdToken(forceRefresh: Boolean): String? = user?.let { "fake-token" }

        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = AuthResult.Error("Not implemented")
    }

    @Test
    fun `guest user does not record tombstones, preserving guest data isolation`() =
        runTest {
            val authRepo = FakeAuthRepository(user = null)
            val stateStore = SyncStateStore(MemoryPreferencesDataStore())
            val recorder = DefaultSyncTombstoneRecorder(authRepo, stateStore)

            val note =
                NoteWithItems(
                    note = NoteEntity(id = 1L, uuid = "note-uuid-1", kind = NoteKind.NOTE, title = "Title", body = "Body", colorIndex = 0, trashed = false, createdAt = 0L, updatedAt = 0L),
                    items = listOf(ChecklistItemEntity(id = 10L, uuid = "item-uuid-1", noteId = 1L, text = "Task", checked = false, sortOrder = 0.0)),
                    attachments = listOf(NoteAttachmentEntity(id = 20L, uuid = "att-uuid-1", noteId = 1L, uri = "content://att", displayName = "doc.pdf")),
                )

            recorder.recordNoteDeleted(note)
            recorder.recordShoulderDeleted("shoulder-uuid-1")

            // No tombstones stored anywhere
            assertTrue(stateStore.getTombstones("").isEmpty())
        }

    @Test
    fun `authenticated user records note, checklist item, attachment, and category tombstones`() =
        runTest {
            val user = AuthUser(uid = "user-123", email = "test@example.com", isEmailVerified = true)
            val authRepo = FakeAuthRepository(user = user)
            val stateStore = SyncStateStore(MemoryPreferencesDataStore())
            val recorder = DefaultSyncTombstoneRecorder(authRepo, stateStore)

            val note =
                NoteWithItems(
                    note = NoteEntity(id = 1L, uuid = "note-uuid-1", kind = NoteKind.NOTE, title = "Title", body = "Body", colorIndex = 0, trashed = false, createdAt = 0L, updatedAt = 0L),
                    items = listOf(ChecklistItemEntity(id = 10L, uuid = "item-uuid-1", noteId = 1L, text = "Task", checked = false, sortOrder = 0.0)),
                    attachments = listOf(NoteAttachmentEntity(id = 20L, uuid = "att-uuid-1", noteId = 1L, uri = "content://att", displayName = "doc.pdf")),
                )

            recorder.recordNoteDeleted(note)
            recorder.recordShoulderDeleted("shoulder-uuid-1")

            val tombstones = stateStore.getTombstones("user-123")
            assertEquals(4, tombstones.size)

            val noteTombstone = tombstones.find { it.entityType == "note" }
            assertEquals("note-uuid-1", noteTombstone?.uuid)

            val itemTombstone = tombstones.find { it.entityType == "checklist_item" }
            assertEquals("item-uuid-1", itemTombstone?.uuid)
            assertEquals("note-uuid-1", itemTombstone?.noteUuid)

            val attTombstone = tombstones.find { it.entityType == "attachment" }
            assertEquals("att-uuid-1", attTombstone?.uuid)
            assertEquals("note-uuid-1", attTombstone?.noteUuid)

            val catTombstone = tombstones.find { it.entityType == "category" }
            assertEquals("shoulder-uuid-1", catTombstone?.uuid)
        }
}
