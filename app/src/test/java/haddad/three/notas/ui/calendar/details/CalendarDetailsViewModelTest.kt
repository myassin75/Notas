package three.haddad.smartnotas.ui.calendar.details

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import three.haddad.smartnotas.data.AttachmentDao
import three.haddad.smartnotas.data.ChecklistItemDao
import three.haddad.smartnotas.data.ChecklistItemEntity
import three.haddad.smartnotas.data.NoteAttachmentEntity
import three.haddad.smartnotas.data.NoteDao
import three.haddad.smartnotas.data.NoteEntity
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteReminder
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.RecurrenceRule
import three.haddad.smartnotas.data.RecurrenceUnit
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.ui.nav.Routes
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarDetailsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val noteFlow = MutableStateFlow<NoteWithItems?>(null)
    private var completedCalledId: Long? = null
    private var incompleteCalledId: Long? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepository(): NoteRepository {
        val fakeDao =
            object : NoteDao {
                override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observe(id: Long): Flow<NoteWithItems?> = noteFlow

                override suspend fun get(id: Long): NoteWithItems? = noteFlow.value

                override suspend fun getByUuid(uuid: String): NoteWithItems? = noteFlow.value?.takeIf { it.note.uuid == uuid }

                override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = noteFlow.value?.note?.takeIf { it.uuid == uuid }

                override suspend fun allNotes(): List<NoteWithItems> = emptyList()

                override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = emptyList()

                override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()

                override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override suspend fun insert(note: NoteEntity): Long = 1L

                override suspend fun update(note: NoteEntity) {
                    if (note.completedAt != null) {
                        completedCalledId = note.id
                    } else {
                        incompleteCalledId = note.id
                    }
                }

                override suspend fun setTrashed(
                    id: Long,
                    trashed: Boolean,
                    updatedAt: Long,
                ) = Unit

                override suspend fun setArchived(
                    id: Long,
                    archived: Boolean,
                    updatedAt: Long,
                ) = Unit

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

        val fakeItemDao =
            object : ChecklistItemDao {
                override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = emptyList()

                override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = null

                override suspend fun insert(item: ChecklistItemEntity): Long = 1L

                override suspend fun insertAll(items: List<ChecklistItemEntity>) = Unit

                override suspend fun update(item: ChecklistItemEntity) = Unit

                override suspend fun deleteById(id: Long) = Unit

                override suspend fun deleteForNote(noteId: Long) = Unit
            }

        val fakeAttachmentDao =
            object : AttachmentDao {
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

    private fun makeNote(
        id: Long = 1L,
        title: String = "Tommy’s birthday (Jacob and Lisa)",
        body: String = "8:00 AM – 10:00 AM\nPlan activities: Plan activities that guests can enjoy, such as games, dancing, or karaoke.",
        kind: NoteKind = NoteKind.NOTE,
        colorIndex: Int = 1,
        reminderAt: Long? = null,
        recurrence: RecurrenceRule? = null,
        isCompleted: Boolean = false,
        shoulder: ShoulderEntity? = null,
    ): NoteWithItems {
        val entity =
            NoteEntity(
                id = id,
                kind = kind,
                title = title,
                body = body,
                colorIndex = colorIndex,
                trashed = false,
                createdAt = 1718438400000L, // June 15, 2024
                updatedAt = 1718438400000L,
                reminderAt = reminderAt,
                recurrence = recurrence,
                completedAt = if (isCompleted) 1718438400000L else null,
                shoulderId = shoulder?.id,
            )
        return NoteWithItems(
            note = entity,
            items = emptyList(),
            attachments = emptyList(),
            shoulder = shoulder,
        )
    }

    @Test
    fun testTimedEventDetailsMappedCorrectly() =
        runTest {
            val repo = createRepository()
            val targetDate = LocalDate.of(2026, 6, 16) // A Tuesday
            val targetMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val sampleNote =
                makeNote(
                    id = 42L,
                    title = "Tommy’s birthday (Jacob and Lisa)",
                    body = "8:00 AM – 10:00 AM\nPlan activities: Plan activities that guests can enjoy.",
                    reminderAt = targetMillis + (8 * 3600 * 1000), // 8:00 AM
                    recurrence = RecurrenceRule(unit = RecurrenceUnit.YEAR),
                )
            noteFlow.value = sampleNote

            val handle =
                SavedStateHandle(
                    mapOf(
                        Routes.ARG_ITEM_ID to 42L,
                        Routes.ARG_IS_TASK to false,
                        Routes.ARG_DATE_MILLIS to targetMillis,
                    ),
                )

            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    vm.uiState.collect {}
                }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.notFound)
            assertEquals(42L, state.id)
            assertEquals("Tommy’s birthday (Jacob and Lisa)", state.title)
            assertEquals("Plan activities: Plan activities that guests can enjoy.", state.description)
            assertEquals("June 16", state.dateText)
            assertEquals("Tuesday", state.weekdayText)
            assertEquals("8:00 AM", state.startTimeText)
            assertEquals("10:00 AM", state.endTimeText)
            assertEquals("2 hours", state.durationText)
            assertFalse(state.isAllDay)
            assertFalse(state.isTask)
            assertTrue(state.isBirthday)
            assertEquals("Every year", state.recurrencePrimary)
            assertNotNull(state.recurrenceSecondary)

            collectJob.cancel()
        }

    @Test
    fun testAllDayEventMappedCorrectly() =
        runTest {
            val repo = createRepository()
            val sampleNote =
                makeNote(
                    id = 10L,
                    title = "Annual Company Picnic",
                    body = "[All Day]\nDon't forget sunscreen and picnic blankets.",
                )
            noteFlow.value = sampleNote

            val handle =
                SavedStateHandle(
                    mapOf(
                        Routes.ARG_ITEM_ID to 10L,
                        Routes.ARG_IS_TASK to false,
                    ),
                )

            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    vm.uiState.collect {}
                }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isAllDay)
            assertEquals("Don't forget sunscreen and picnic blankets.", state.description)

            collectJob.cancel()
        }

    @Test
    fun testTaskMappedAndToggledCorrectly() =
        runTest {
            val repo = createRepository()
            val sampleNote =
                makeNote(
                    id = 99L,
                    title = "Finish project documentation",
                    body = "Review PR and write markdown summary",
                    kind = NoteKind.LIST,
                    isCompleted = false,
                )
            noteFlow.value = sampleNote

            val handle =
                SavedStateHandle(
                    mapOf(
                        Routes.ARG_ITEM_ID to 99L,
                        Routes.ARG_IS_TASK to true,
                    ),
                )

            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    vm.uiState.collect {}
                }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isTask)
            assertFalse(state.isDone)
            assertEquals("Finish project documentation", state.title)

            vm.toggleTaskCompletion()
            advanceUntilIdle()

            assertEquals(99L, completedCalledId)

            collectJob.cancel()
        }

    @Test
    fun testNonExistentItemReturnsNotFound() =
        runTest {
            val repo = createRepository()
            noteFlow.value = null

            val handle =
                SavedStateHandle(
                    mapOf(
                        Routes.ARG_ITEM_ID to 9999L,
                    ),
                )

            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    vm.uiState.collect {}
                }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.notFound)

            collectJob.cancel()
        }

    @Test
    fun testNoteWithShoulderPresentsShoulderNameAndColor() =
        runTest {
            val repo = createRepository()
            val shoulder =
                ShoulderEntity(
                    id = 5L,
                    name = "Work",
                    normalizedName = "work",
                    colorHex = "#29A4AB",
                    createdAt = 1718438400000L,
                    updatedAt = 1718438400000L,
                )
            val sampleNote =
                makeNote(
                    id = 15L,
                    title = "Work Meeting",
                    body = "Quarterly planning",
                    shoulder = shoulder,
                )
            noteFlow.value = sampleNote

            val handle = SavedStateHandle(mapOf(Routes.ARG_ITEM_ID to 15L))
            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("Work", state.shoulderName)
            assertNotNull(state.shoulderColor)

            collectJob.cancel()
        }

    @Test
    fun testNoteWithoutShoulderHasNullShoulderColor() =
        runTest {
            val repo = createRepository()
            val sampleNote =
                makeNote(
                    id = 16L,
                    title = "Uncategorized Note",
                    body = "Some notes",
                    shoulder = null,
                )
            noteFlow.value = sampleNote

            val handle = SavedStateHandle(mapOf(Routes.ARG_ITEM_ID to 16L))
            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(null, state.shoulderName)
            assertEquals(null, state.shoulderColor)

            collectJob.cancel()
        }

    @Test
    fun testScheduledTaskDoesNotDeriveEventEndTime() =
        runTest {
            val repo = createRepository()
            val targetDate = LocalDate.of(2026, 7, 20)
            val targetMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val sampleTask =
                makeNote(
                    id = 77L,
                    title = "Buy Groceries",
                    body = "Milk, Eggs, Bread",
                    kind = NoteKind.NOTE,
                    reminderAt = targetMillis + (10 * 3600 * 1000), // 10:00 AM
                )
            noteFlow.value = sampleTask

            val handle =
                SavedStateHandle(
                    mapOf(
                        Routes.ARG_ITEM_ID to 77L,
                        Routes.ARG_IS_TASK to true,
                        Routes.ARG_DATE_MILLIS to targetMillis,
                    ),
                )
            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isTask)
            assertEquals("10:00 AM", state.taskScheduledTimeText)
            assertEquals(null, state.endTimeText)
            assertEquals(null, state.durationText)

            collectJob.cancel()
        }

    @Test
    fun testTargetDateNotDerivedFromCreationTimestamp() =
        runTest {
            val repo = createRepository()
            val sampleNote =
                makeNote(
                    id = 88L,
                    title = "Old Note",
                    body = "Created years ago",
                    reminderAt = null,
                )
            noteFlow.value = sampleNote

            // No dateMillis passed, no reminderAt on note
            val handle = SavedStateHandle(mapOf(Routes.ARG_ITEM_ID to 88L))
            val vm = CalendarDetailsViewModel(handle, repo)

            val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()

            val state = vm.uiState.value
            // Should resolve to today's date, NOT the 2024 creation date ("June 15, 2024")
            val today = LocalDate.now()
            val currentYearText = today.year.toString()
            assertFalse("Date should not derive from 2024 creation timestamp", state.dateText.contains("2024"))

            collectJob.cancel()
        }
}
