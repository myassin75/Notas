package three.haddad.smartnotas.ui.profile

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
import three.haddad.smartnotas.data.AttachmentDao
import three.haddad.smartnotas.data.ChecklistItemDao
import three.haddad.smartnotas.data.ChecklistItemEntity
import three.haddad.smartnotas.data.NoteAttachmentEntity
import three.haddad.smartnotas.data.NoteDao
import three.haddad.smartnotas.data.NoteEntity
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NoteWithItems

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
        val fakeDao = object : NoteDao {
            override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(notes)
            override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(null)
            override suspend fun get(id: Long): NoteWithItems? = null
            override suspend fun getByUuid(uuid: String): NoteWithItems? = notes.firstOrNull { it.note.uuid == uuid }
            override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = notes.firstOrNull { it.note.uuid == uuid }?.note
            override suspend fun allNotes(): List<NoteWithItems> = notes
            override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = notes.filter { it.note.ownerId == ownerId }
            override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()
            override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override suspend fun insert(note: NoteEntity): Long = 1L
            override suspend fun update(note: NoteEntity) = Unit
            override suspend fun setTrashed(id: Long, trashed: Boolean, updatedAt: Long) = Unit
            override suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long) = Unit
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun allNoteIds(): List<Long> = emptyList()
            override suspend fun countPictureUri(uri: String): Int = 0
            override suspend fun trashedNoteIds(): List<Long> = emptyList()
            override suspend fun archivedNoteIds(): List<Long> = emptyList()
            override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()
            override suspend fun deleteAllNotes() = Unit
            override suspend fun emptyTrash() = Unit
            override suspend fun deleteTrashedOlderThan(cutoff: Long) = Unit
        }

        val fakeItemDao = object : ChecklistItemDao {
            override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = emptyList()
            override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = null
            override suspend fun insert(item: ChecklistItemEntity): Long = 1L
            override suspend fun insertAll(items: List<ChecklistItemEntity>) = Unit
            override suspend fun update(item: ChecklistItemEntity) = Unit
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun deleteForNote(noteId: Long) = Unit
        }

        val fakeAttachmentDao = object : AttachmentDao {
            override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = emptyList()
            override suspend fun insert(attachment: NoteAttachmentEntity): Long = 1L
            override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) = Unit
            override suspend fun getById(id: Long): NoteAttachmentEntity? = null
            override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = null
            override suspend fun countByUri(uri: String): Int = 0
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun deleteForNote(noteId: Long) = Unit
        }

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
