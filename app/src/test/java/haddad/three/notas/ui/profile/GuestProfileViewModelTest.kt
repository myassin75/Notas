package haddad.three.notas.ui.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems

@OptIn(ExperimentalCoroutinesApi::class)
class GuestProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepository(notes: List<NoteWithItems>): NoteRepository {
        val fakeDao = object : haddad.three.notas.data.BaseFakeNoteDao() {
            init {
                notes.forEach { insertDirect(it.note) }
            }
            private fun insertDirect(note: NoteEntity) {
                this.notes[note.id] = note
            }
        }

        val fakeItemDao = object : haddad.three.notas.data.BaseFakeChecklistItemDao() {}
        val fakeAttachmentDao = object : haddad.three.notas.data.BaseFakeAttachmentDao() {}

        return NoteRepository(
            noteDao = fakeDao,
            itemDao = fakeItemDao,
            attachmentDao = fakeAttachmentDao,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )
    }

    private fun sampleNote(
        id: Long,
        kind: NoteKind,
        reminderAt: Long? = null,
        endAt: Long? = null,
    ): NoteWithItems {
        return NoteWithItems(
            note = NoteEntity(
                id = id,
                title = "Note $id",
                body = "Body $id",
                kind = kind,
                colorIndex = 1,
                trashed = false,
                reminderAt = reminderAt,
                endAt = endAt,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
            items = emptyList(),
            attachments = emptyList(),
        )
    }

    @Test
    fun guestProfile_computesRealNoteTaskAndEventCounts() = runTest {
        val notes = listOf(
            sampleNote(1, NoteKind.NOTE),
            sampleNote(2, NoteKind.NOTE),
            sampleNote(3, NoteKind.LIST),
            sampleNote(4, NoteKind.NOTE, reminderAt = 2000L), // Event with reminder
        )

        val repository = createRepository(notes)
        val viewModel = GuestProfileViewModel(repository)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.stats.collect {}
        }

        advanceUntilIdle()

        val stats = viewModel.stats.value
        assertEquals(2, stats.notesCount)
        assertEquals(1, stats.tasksCount)
        assertEquals(1, stats.eventsCount)

        collectJob.cancel()
    }
}
