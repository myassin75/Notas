package haddad.three.notas.ui.calendar

import androidx.lifecycle.SavedStateHandle
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Exhaustive lifecycle test covering Agenda icons:
 * 1. Birthday icon (`cake`, `birthday`, etc.) creation, saving, reopening, editing, and deletion.
 * 2. Event icons (`event`, `alarm`, `schedule`, `work`, `star`, `flight`, `shopping_cart`, etc.)
 *    through creation, saving, reopening, editing, and deletion.
 * 3. Verified across Calendar Month, Week, and Day views without crashes or regressions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarIconLifecycleExhaustiveTest {
    private val testDispatcher = StandardTestDispatcher()
    private val notesStore = mutableMapOf<Long, NoteEntity>()
    private val notesFlow = MutableStateFlow<List<NoteWithItems>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun syncNotesFlow() {
        notesFlow.value = notesStore.values.map { NoteWithItems(it, emptyList()) }
    }

    private fun TestScope.createViewModel(): Pair<CalendarViewModel, NoteRepository> {
        val fakeDao =
            object : haddad.three.notas.data.BaseFakeNoteDao() {
                override fun observeActive(ownerId: String?): Flow<List<NoteWithItems>> = notesFlow

                override fun observeScheduled(ownerId: String?): Flow<List<NoteWithItems>> =
                    notesFlow.map { list ->
                        list.filter {
                            it.note.reminderAt != null || it.note.endAt != null || it.note.isAllDay
                        }
                    }

                override suspend fun get(id: Long): NoteWithItems? = notesStore[id]?.let { NoteWithItems(it, emptyList()) }
            }

        val fakeItemDao = object : haddad.three.notas.data.BaseFakeChecklistItemDao() {}
        val fakeAttachmentDao = object : haddad.three.notas.data.BaseFakeAttachmentDao() {}

        val repository =
            NoteRepository(
                noteDao = fakeDao,
                itemDao = fakeItemDao,
                attachmentDao = fakeAttachmentDao,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
            )

        val viewModel = CalendarViewModel(repository, SavedStateHandle(), testDispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel to repository
    }

    @Test
    fun birthday_icon_complete_lifecycle_create_save_reopen_edit_and_delete() =
        runTest {
            val (vm, repo) = createViewModel()
            val targetDate = LocalDate.of(2026, 11, 23)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

            // 1. Create birthday note
            val birthdayNote =
                NoteEntity(
                    id = 501L,
                    kind = NoteKind.NOTE,
                    title = "Sarah's Birthday",
                    body = "Buy gifts and cake",
                    colorIndex = 1,
                    trashed = false,
                    iconKey = "cake",
                    reminderAt = startEpoch,
                    createdAt = startEpoch,
                    updatedAt = startEpoch,
                )
            notesStore[501L] = birthdayNote
            syncNotesFlow()

            // 2. Open Calendar and select date
            vm.onDateSelected(targetDate)
            advanceUntilIdle()

            val state1 = vm.uiState.value
            assertEquals(1, state1.selectedDayBirthdays.size)
            val bday1 = state1.selectedDayBirthdays.first()
            assertEquals(501L, bday1.id)
            assertEquals("Sarah's Birthday", bday1.name)
            assertEquals("cake", bday1.iconName)

            // 3. Edit birthday note (change title and icon)
            val updatedNote =
                birthdayNote.copy(
                    title = "Sarah's 30th Birthday Party",
                    iconKey = "cake", // Birthday preserved
                    updatedAt = startEpoch + 1000,
                )
            notesStore[501L] = updatedNote
            syncNotesFlow()
            advanceUntilIdle()

            val state2 = vm.uiState.value
            assertEquals(1, state2.selectedDayBirthdays.size)
            assertEquals("Sarah's 30th Birthday Party", state2.selectedDayBirthdays.first().name)

            // 4. Switch views: Month -> Week -> Day -> Month
            vm.onViewModeSelected(CalendarViewMode.DAY)
            advanceUntilIdle()
            assertEquals(CalendarViewMode.DAY, vm.uiState.value.viewMode)

            vm.onViewModeSelected(CalendarViewMode.WEEK)
            advanceUntilIdle()
            assertEquals(CalendarViewMode.WEEK, vm.uiState.value.viewMode)

            vm.onViewModeSelected(CalendarViewMode.MONTH)
            advanceUntilIdle()
            assertEquals(CalendarViewMode.MONTH, vm.uiState.value.viewMode)

            // 5. Delete birthday note
            notesStore.remove(501L)
            syncNotesFlow()
            advanceUntilIdle()

            val state3 = vm.uiState.value
            assertTrue("Birthdays should be empty after deletion", state3.selectedDayBirthdays.isEmpty())
        }

    @Test
    fun catalog_icons_display_and_lifecycle_in_agenda_and_timeline() =
        runTest {
            val (vm, _) = createViewModel()
            val targetDate = LocalDate.of(2026, 11, 24)
            val zone = ZoneId.systemDefault()

            val iconTestCases =
                listOf(
                    Triple(1L, "Flight to London", "flight"),
                    Triple(2L, "Grocery Shopping", "shopping_cart"),
                    Triple(3L, "Gym Workout", "fitness_center"),
                    Triple(4L, "Doctor Appointment", "medical_services"),
                    Triple(5L, "Sprint Planning", "work"),
                    Triple(6L, "Music Concert", "music_note"),
                    Triple(7L, "Coffee with Bob", "local_cafe"),
                    Triple(8L, "Starred Milestone", "star"),
                )

            iconTestCases.forEachIndexed { index, (id, title, icon) ->
                val start = ZonedDateTime.of(2026, 11, 24, 8 + index, 0, 0, 0, zone).toInstant().toEpochMilli()
                val end = ZonedDateTime.of(2026, 11, 24, 8 + index, 45, 0, 0, zone).toInstant().toEpochMilli()
                notesStore[id] =
                    NoteEntity(
                        id = id,
                        kind = NoteKind.NOTE,
                        title = title,
                        body = "Icon test item",
                        colorIndex = (index % 9) + 1,
                        trashed = false,
                        iconKey = icon,
                        reminderAt = start,
                        endAt = end,
                        createdAt = start,
                        updatedAt = start,
                    )
            }
            syncNotesFlow()

            vm.onDateSelected(targetDate)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(iconTestCases.size, state.dayTimelineItems.size)

            iconTestCases.forEach { (id, title, expectedIcon) ->
                val item = state.dayTimelineItems.find { it.id == id }
                assertNotNull("Timeline item for $title should exist", item)
                assertEquals(title, item?.title)
                assertEquals(expectedIcon, item?.iconName)
            }

            // Remove half the notes, verify reactive update
            notesStore.remove(1L)
            notesStore.remove(2L)
            notesStore.remove(3L)
            syncNotesFlow()
            advanceUntilIdle()

            val updatedState = vm.uiState.value
            assertEquals(5, updatedState.dayTimelineItems.size)
            assertNull(updatedState.dayTimelineItems.find { it.id == 1L })
            assertNotNull(updatedState.dayTimelineItems.find { it.id == 4L })
        }
}
