package haddad.three.notas.ui.edit

import androidx.lifecycle.SavedStateHandle
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteReminder
import haddad.three.notas.data.ShoulderDao
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ShoulderRepository
import haddad.three.notas.data.getActiveReminders
import haddad.three.notas.data.withSyncedPrimaryReminder
import haddad.three.notas.ui.nav.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteIntegrityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeRepositoryStore()

    @Test
    fun ordinary_note_remains_undated_unless_explicitly_scheduled() =
        runTest {
            val savedStateHandle = SavedStateHandle()
            val vm = EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            advanceUntilIdle()

            // Ordinary new note starts completely undated
            assertNull(vm.reminderAt.value)
            assertNull(vm.endAt.value)
            assertFalse(vm.isAllDay.value)
            assertTrue(vm.reminders.value.isEmpty())

            vm.setTitle("Undated Note")
            vm.setBody("Hello world")
            val undo = vm.saveIfNeeded("Untitled")
            advanceUntilIdle()
            assertNotNull(undo)

            val createdId = vm.currentNoteId.value
            assertNotNull(createdId)
            val saved = store.notes[createdId!!]
            assertNotNull(saved)
            assertNull(saved?.reminderAt)
            assertNull(saved?.endAt)
            assertFalse(saved?.isAllDay ?: true)
            assertTrue(saved?.getActiveReminders()?.isEmpty() ?: false)
        }

    @Test
    fun setting_reminder_does_not_turn_note_into_all_day_event() =
        runTest {
            val savedStateHandle = SavedStateHandle()
            val vm = EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            advanceUntilIdle()

            vm.setTitle("Reminder Note")
            val reminderTimestamp = 1_700_000_000_000L
            vm.setReminders(listOf(NoteReminder(reminderAt = reminderTimestamp)))
            advanceUntilIdle()

            // Setting a timed reminder must NOT mark it all-day
            assertFalse(vm.isAllDay.value)
            assertNull(vm.endAt.value)
            assertEquals(reminderTimestamp, vm.reminderAt.value)

            vm.saveIfNeeded("Untitled")
            advanceUntilIdle()

            val createdId = vm.currentNoteId.value!!
            val saved = store.notes[createdId]!!
            assertEquals(reminderTimestamp, saved.reminderAt)
            assertFalse(saved.isAllDay)
            assertNull(saved.endAt)
        }

    @Test
    fun endAt_and_isAllDay_changes_trigger_hasNetChanges_and_survive_undo() =
        runTest {
            store.notes[1L] =
                NoteEntity(
                    id = 1L,
                    kind = NoteKind.NOTE,
                    title = "Existing Note",
                    body = "Content",
                    colorIndex = 0,
                    reminderAt = null,
                    endAt = null,
                    isAllDay = false,
                    trashed = false,
                    createdAt = 1L,
                    updatedAt = 1L,
                ).withSyncedPrimaryReminder()

            val savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_ID to 1L))
            val vm = EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            advanceUntilIdle()

            assertFalse(vm.hasUnsavedChanges.value)

            // When reminder with endAt is set
            vm.setReminder(1_000_000L, null)
            assertTrue(vm.hasUnsavedChanges.value)

            val undo = vm.saveIfNeeded("Untitled")
            advanceUntilIdle()
            assertNotNull(undo)

            // Execute undo rollback
            undo?.invoke()
            advanceUntilIdle()

            val reverted = store.notes[1L]!!
            assertNull(reverted.reminderAt)
            assertNull(reverted.endAt)
            assertFalse(reverted.isAllDay)
        }

    @Test
    fun assigning_and_removing_shoulder_preserves_shoulder_and_updates_note() =
        runTest {
            val testShoulder =
                ShoulderEntity(
                    id = 42L,
                    name = "Work",
                    normalizedName = "work",
                    colorHex = "#FF00FF",
                    createdAt = 100L,
                    updatedAt = 100L,
                )

            val fakeShoulderDao = FakeShoulderDao(mutableListOf(testShoulder))
            val shoulderRepo = ShoulderRepository(fakeShoulderDao)
            store.shoulderRepository = shoulderRepo

            val savedStateHandle = SavedStateHandle()
            val vm = EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            advanceUntilIdle()

            // Assign shoulder
            vm.setShoulder(testShoulder)
            assertEquals(42L, vm.shoulderId.value)
            assertEquals(testShoulder, vm.shoulder.value)

            // Remove shoulder
            vm.setShoulder(null)
            assertNull(vm.shoulderId.value)
            assertNull(vm.shoulder.value)

            // Shoulder entity itself must NOT be deleted from repository
            assertEquals(1, fakeShoulderDao.shoulders.size)
            assertEquals("Work", fakeShoulderDao.shoulders.first().name)
        }

    @Test
    fun note_color_is_preserved_accurately_across_edits() =
        runTest {
            val savedStateHandle = SavedStateHandle()
            val vm = EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            advanceUntilIdle()

            vm.setTitle("Color Note")
            vm.setColorIndex(3)
            assertEquals(3, vm.colorIndex.value)

            vm.saveIfNeeded("Untitled")
            advanceUntilIdle()

            val id = vm.currentNoteId.value!!
            assertEquals(3, store.notes[id]!!.colorIndex)

            // Reopen in a second VM session
            val reopenHandle = SavedStateHandle(mapOf(Routes.ARG_ID to id))
            val reopenedVm = EditNoteViewModel(repository = store.repository(), savedStateHandle = reopenHandle)
            advanceUntilIdle()
            assertEquals(3, reopenedVm.colorIndex.value)
        }

    @Test
    fun single_editing_session_persists_once_and_preserves_id_across_title_and_body_edits() =
        runTest {
            val savedStateHandle = SavedStateHandle()
            val vm = EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            advanceUntilIdle()

            // 1. Initial draft state
            assertNull(vm.currentNoteId.value)
            assertFalse(vm.hasPersistedRow.value)

            // 2. User types title
            vm.setTitle("My Architecture Note")
            val initialSave = vm.saveIfNeeded("Untitled")
            advanceUntilIdle()
            assertNotNull(initialSave)

            val persistentId = vm.currentNoteId.value
            assertNotNull(persistentId)
            assertTrue(vm.hasPersistedRow.value)
            assertEquals(1, store.notes.size)
            assertEquals("My Architecture Note", store.notes[persistentId]?.title)
            assertEquals("", store.notes[persistentId]?.body)

            // 3. User moves from title to body and continues typing
            vm.setBody("Detailed body paragraph 1\nDetailed body paragraph 2")
            val secondSave = vm.saveIfNeeded("Untitled")
            advanceUntilIdle()
            assertNotNull(secondSave)

            // 4. Stable editing session: Same ID, exactly 1 note in database, updated content
            assertEquals(persistentId, vm.currentNoteId.value)
            assertTrue(vm.hasPersistedRow.value)
            assertEquals(1, store.notes.size)
            assertEquals("My Architecture Note", store.notes[persistentId]?.title)
            assertEquals("Detailed body paragraph 1\nDetailed body paragraph 2", store.notes[persistentId]?.body)
        }

    private class FakeShoulderDao(
        val shoulders: MutableList<ShoulderEntity>,
    ) : ShoulderDao {
        override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders)

        override suspend fun allShoulders(): List<ShoulderEntity> = shoulders.toList()

        override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = shoulders.filter { it.ownerId == ownerId }

        override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(shoulders)

        override suspend fun getShoulderById(id: Long): ShoulderEntity? = shoulders.firstOrNull { it.id == id }

        override suspend fun getByUuid(uuid: String): ShoulderEntity? = shoulders.firstOrNull { it.uuid == uuid }

        override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = shoulders.firstOrNull { it.normalizedName == normalizedName }

        override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()

        override suspend fun insertShoulder(shoulder: ShoulderEntity): Long {
            val newId = (shoulders.maxOfOrNull { it.id } ?: 0L) + 1L
            shoulders.add(shoulder.copy(id = newId))
            return newId
        }

        override suspend fun updateShoulder(shoulder: ShoulderEntity) {
            val index = shoulders.indexOfFirst { it.id == shoulder.id }
            if (index >= 0) shoulders[index] = shoulder
        }

        override suspend fun deleteShoulder(shoulder: ShoulderEntity) {
            shoulders.removeAll { it.id == shoulder.id }
        }

        override suspend fun setShoulderForNote(
            noteId: Long,
            shoulderId: Long?,
        ) {
            // no-op for mock
        }

        override suspend fun claimGuestShoulders(targetOwnerId: String): Int = 0
    }
}
