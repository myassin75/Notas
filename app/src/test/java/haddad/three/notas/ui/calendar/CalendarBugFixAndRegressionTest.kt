package haddad.three.notas.ui.calendar

import androidx.lifecycle.SavedStateHandle
import haddad.three.notas.data.Converters
import haddad.three.notas.data.Importance
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteLayoutMode
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ViewOptions
import haddad.three.notas.data.Visibility
import haddad.three.notas.ui.components.toNoteCardUiModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regression and stability test suite covering:
 * 1. Cross-midnight / end-of-day timeline calculation without DateTimeException.
 * 2. Agenda Birthday visibility and non-empty agenda state.
 * 3. Room Converters fallback resilience against invalid enum constants.
 * 4. Actionable task status in NoteCardUiModel (taskList and task vs standard note).
 * 5. Default home screen layout mode set to LIST.
 * 6. Timeline boundary tests: 23:00 to 00:00, 23:30 to 00:30, 22:00 to 02:00,
 *    month boundaries (Oct 31 to Nov 1), and leap-year boundaries (Feb 28 to Feb 29).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarBugFixAndRegressionTest {
    private val testDispatcher = StandardTestDispatcher()
    private val notesFlow = MutableStateFlow<List<NoteWithItems>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): CalendarViewModel {
        val fakeDao =
            object : haddad.three.notas.data.BaseFakeNoteDao() {
                override fun observeActive(ownerId: String?): Flow<List<NoteWithItems>> = notesFlow

                override fun observeScheduled(ownerId: String?): Flow<List<NoteWithItems>> =
                    notesFlow.map { list ->
                        list.filter {
                            it.note.reminderAt != null || it.note.endAt != null || it.note.isAllDay
                        }
                    }
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

        val viewModel = CalendarViewModel(repository, savedStateHandle, testDispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    @Test
    fun cross_midnight_event_does_not_throw_DateTimeException() =
        runTest {
            val targetDate = LocalDate.of(2026, 11, 17)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 11, 17, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
            val endEpoch = ZonedDateTime.of(2026, 11, 18, 0, 30, 0, 0, zone).toInstant().toEpochMilli()

            val midnightNote =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 777L,
                            kind = NoteKind.NOTE,
                            title = "Late Night Event",
                            body = "Crosses midnight boundary",
                            colorIndex = 0,
                            trashed = false,
                            reminderAt = startEpoch,
                            endAt = endEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(midnightNote)

            val viewModel = createViewModel()
            viewModel.onDateSelected(targetDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.dayTimelineItems)
            val midnightItem = state.dayTimelineItems.find { it.id == 777L }
            assertNotNull("Cross-midnight event should appear in timeline", midnightItem)
            assertEquals("Late Night Event", midnightItem?.title)
        }

    @Test
    fun birthday_event_is_categorized_and_agenda_is_not_empty() =
        runTest {
            val targetDate = LocalDate.of(2026, 11, 17)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

            val birthdayNote =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 888L,
                            kind = NoteKind.NOTE,
                            title = "Alice Birthday",
                            body = "Celebrate Alice's birthday",
                            colorIndex = 0,
                            trashed = false,
                            iconKey = "cake",
                            reminderAt = startEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(birthdayNote)

            val viewModel = createViewModel()
            viewModel.onDateSelected(targetDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.selectedDayBirthdays.size)
            assertEquals("Alice Birthday", state.selectedDayBirthdays.first().name)
            assertEquals("cake", state.selectedDayBirthdays.first().iconName)
        }

    @Test
    fun room_converters_safely_handle_unknown_or_corrupted_enum_strings() {
        val converters = Converters()

        val fallbackKind = converters.toKind("UNKNOWN_CORRUPTED_KIND")
        assertEquals(NoteKind.NOTE, fallbackKind)

        val fallbackImportance = converters.toImportance("EXTREME_SUPER_HIGH")
        assertEquals(Importance.DEFAULT, fallbackImportance)

        val fallbackVisibility = converters.toVisibility("SECRET_GHOST")
        assertEquals(Visibility.DEFAULT, fallbackVisibility)

        assertEquals(NoteKind.LIST, converters.toKind("LIST"))
        assertEquals(Importance.HIGH, converters.toImportance("HIGH"))
        assertEquals(Visibility.SECRET, converters.toVisibility("SECRET"))
    }

    @Test
    fun noteCardUiModel_marks_actionable_tasks_correctly() {
        val now = System.currentTimeMillis()

        // 1. Regular text note (not actionable)
        val textNote =
            NoteEntity(
                id = 1L,
                title = "Regular Note",
                body = "Just a text note",
                colorIndex = 0,
                trashed = false,
                kind = NoteKind.NOTE,
                reminderAt = null,
                endAt = null,
                createdAt = now,
                updatedAt = now,
            )
        val textModel = NoteWithItems(textNote, emptyList()).toNoteCardUiModel()
        assertFalse("Regular note should not be an actionable task", textModel.isActionableTask)

        // 2. Checklist / List note (actionable)
        val listNote =
            NoteEntity(
                id = 2L,
                title = "Checklist Note",
                body = "",
                colorIndex = 0,
                trashed = false,
                kind = NoteKind.LIST,
                reminderAt = null,
                endAt = null,
                createdAt = now,
                updatedAt = now,
            )
        val listModel = NoteWithItems(listNote, emptyList()).toNoteCardUiModel()
        assertTrue("Checklist note should be an actionable task", listModel.isActionableTask)

        // 3. Scheduled task without end date (actionable task)
        val taskNote =
            NoteEntity(
                id = 3L,
                title = "Scheduled Task",
                body = "Needs to be done",
                colorIndex = 0,
                trashed = false,
                kind = NoteKind.NOTE,
                reminderAt = now + 3600000,
                endAt = null,
                createdAt = now,
                updatedAt = now,
            )
        val taskModel = NoteWithItems(taskNote, emptyList()).toNoteCardUiModel()
        assertTrue("Task with start date but no end date should be actionable", taskModel.isActionableTask)

        // 4. Calendar event with start and end date (not an actionable task)
        val eventNote =
            NoteEntity(
                id = 4L,
                title = "Scheduled Event",
                body = "Conference call",
                colorIndex = 0,
                trashed = false,
                kind = NoteKind.NOTE,
                reminderAt = now + 3600000,
                endAt = now + 7200000,
                createdAt = now,
                updatedAt = now,
            )
        val eventModel = NoteWithItems(eventNote, emptyList()).toNoteCardUiModel()
        assertFalse("Calendar event with end date is not an actionable task", eventModel.isActionableTask)
    }

    @Test
    fun viewOptions_defaults_to_list_layout_mode() {
        val defaultOptions = ViewOptions()
        assertEquals(
            "Default note layout mode must be LIST for new installations",
            NoteLayoutMode.LIST,
            defaultOptions.noteLayoutMode,
        )
    }

    @Test
    fun timeline_23_00_to_00_00_ends_at_midnight_cleanly() =
        runTest {
            val targetDate = LocalDate.of(2026, 11, 17)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 11, 17, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
            val endEpoch = ZonedDateTime.of(2026, 11, 18, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

            val note =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 1001L,
                            kind = NoteKind.NOTE,
                            title = "Midnight Border Event",
                            body = "",
                            colorIndex = 0,
                            trashed = false,
                            reminderAt = startEpoch,
                            endAt = endEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(note)

            val viewModel = createViewModel()
            viewModel.onDateSelected(targetDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val item = state.dayTimelineItems.find { it.id == 1001L }
            assertNotNull(item)
            assertEquals(1380, item?.startMinuteOfDay) // 23:00 = 1380 min
            assertEquals(1440, item?.endMinuteOfDay)   // 24:00 = 1440 min
            assertFalse(item?.isCrossMidnightStart ?: true)
            assertEquals("11:00 PM – 12:00 AM", item?.timeRangeDisplay)
        }

    @Test
    fun timeline_23_30_to_00_30_crosses_midnight_with_plus_one_and_next_day_carryover() =
        runTest {
            val date1 = LocalDate.of(2026, 11, 17)
            val date2 = LocalDate.of(2026, 11, 18)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 11, 17, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
            val endEpoch = ZonedDateTime.of(2026, 11, 18, 0, 30, 0, 0, zone).toInstant().toEpochMilli()

            val note =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 1002L,
                            kind = NoteKind.NOTE,
                            title = "Cross Midnight Event",
                            body = "",
                            colorIndex = 0,
                            trashed = false,
                            reminderAt = startEpoch,
                            endAt = endEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(note)

            // Day 1 verification (23:30 to midnight (+1))
            val vmDay1 = createViewModel()
            vmDay1.onDateSelected(date1)
            advanceUntilIdle()

            val itemDay1 = vmDay1.uiState.value.dayTimelineItems.find { it.id == 1002L }
            assertNotNull(itemDay1)
            assertEquals(1410, itemDay1?.startMinuteOfDay) // 23:30
            assertEquals(1440, itemDay1?.endMinuteOfDay)   // Clamped to 1440
            assertTrue(itemDay1?.isCrossMidnightStart ?: false)
            assertEquals("11:30 PM – 12:00 AM (+1)", itemDay1?.timeRangeDisplay)

            // Day 2 verification (00:00 to 00:30 carryover)
            val vmDay2 = createViewModel()
            vmDay2.onDateSelected(date2)
            advanceUntilIdle()

            val itemDay2 = vmDay2.uiState.value.dayTimelineItems.find { it.id == 1002L }
            assertNotNull("Carryover item must appear on day 2", itemDay2)
            assertEquals(0, itemDay2?.startMinuteOfDay)  // Midnight start
            assertEquals(30, itemDay2?.endMinuteOfDay)  // 00:30
            assertTrue(itemDay2?.isCrossMidnightEnd ?: false)
            assertEquals("12:00 AM – 12:30 AM", itemDay2?.timeRangeDisplay)
        }

    @Test
    fun timeline_22_00_to_02_00_spans_across_midnight_on_both_days() =
        runTest {
            val date1 = LocalDate.of(2026, 11, 17)
            val date2 = LocalDate.of(2026, 11, 18)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 11, 17, 22, 0, 0, 0, zone).toInstant().toEpochMilli()
            val endEpoch = ZonedDateTime.of(2026, 11, 18, 2, 0, 0, 0, zone).toInstant().toEpochMilli()

            val note =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 1003L,
                            kind = NoteKind.NOTE,
                            title = "Late Night Gig",
                            body = "",
                            colorIndex = 0,
                            trashed = false,
                            reminderAt = startEpoch,
                            endAt = endEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(note)

            // Day 1
            val vm1 = createViewModel()
            vm1.onDateSelected(date1)
            advanceUntilIdle()
            val item1 = vm1.uiState.value.dayTimelineItems.find { it.id == 1003L }
            assertNotNull(item1)
            assertEquals(1320, item1?.startMinuteOfDay)
            assertEquals(1440, item1?.endMinuteOfDay)
            assertTrue(item1?.isCrossMidnightStart ?: false)

            // Day 2
            val vm2 = createViewModel()
            vm2.onDateSelected(date2)
            advanceUntilIdle()
            val item2 = vm2.uiState.value.dayTimelineItems.find { it.id == 1003L }
            assertNotNull(item2)
            assertEquals(0, item2?.startMinuteOfDay)
            assertEquals(120, item2?.endMinuteOfDay) // 2 hours = 120 minutes
            assertTrue(item2?.isCrossMidnightEnd ?: false)
            assertEquals("12:00 AM – 2:00 AM", item2?.timeRangeDisplay)
        }

    @Test
    fun timeline_month_boundary_rollover_oct31_to_nov01() =
        runTest {
            val date1 = LocalDate.of(2026, 10, 31)
            val date2 = LocalDate.of(2026, 11, 1)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2026, 10, 31, 23, 15, 0, 0, zone).toInstant().toEpochMilli()
            val endEpoch = ZonedDateTime.of(2026, 11, 1, 1, 15, 0, 0, zone).toInstant().toEpochMilli()

            val note =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 1004L,
                            kind = NoteKind.NOTE,
                            title = "Halloween Rollover",
                            body = "",
                            colorIndex = 0,
                            trashed = false,
                            reminderAt = startEpoch,
                            endAt = endEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(note)

            val vmNov1 = createViewModel()
            vmNov1.onDateSelected(date2)
            advanceUntilIdle()
            val itemNov1 = vmNov1.uiState.value.dayTimelineItems.find { it.id == 1004L }
            assertNotNull("Carryover across month boundary must appear", itemNov1)
            assertEquals(0, itemNov1?.startMinuteOfDay)
            assertEquals(75, itemNov1?.endMinuteOfDay) // 1:15 = 75 minutes
            assertTrue(itemNov1?.isCrossMidnightEnd ?: false)
            assertEquals("12:00 AM – 1:15 AM", itemNov1?.timeRangeDisplay)
        }

    @Test
    fun timeline_leap_year_boundary_rollover_feb28_to_feb29() =
        runTest {
            val dateFeb28 = LocalDate.of(2028, 2, 28) // 2028 is leap year
            val dateFeb29 = LocalDate.of(2028, 2, 29)
            val zone = ZoneId.systemDefault()
            val startEpoch = ZonedDateTime.of(2028, 2, 28, 23, 45, 0, 0, zone).toInstant().toEpochMilli()
            val endEpoch = ZonedDateTime.of(2028, 2, 29, 1, 0, 0, 0, zone).toInstant().toEpochMilli()

            val note =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 1005L,
                            kind = NoteKind.NOTE,
                            title = "Leap Day Eve Party",
                            body = "",
                            colorIndex = 0,
                            trashed = false,
                            reminderAt = startEpoch,
                            endAt = endEpoch,
                            createdAt = startEpoch,
                            updatedAt = startEpoch,
                        ),
                    items = emptyList(),
                )

            notesFlow.value = listOf(note)

            val vmFeb29 = createViewModel()
            vmFeb29.onDateSelected(dateFeb29)
            advanceUntilIdle()
            val itemFeb29 = vmFeb29.uiState.value.dayTimelineItems.find { it.id == 1005L }
            assertNotNull("Carryover across leap year day must appear", itemFeb29)
            assertEquals(0, itemFeb29?.startMinuteOfDay)
            assertEquals(60, itemFeb29?.endMinuteOfDay) // 1 hour = 60 minutes
            assertTrue(itemFeb29?.isCrossMidnightEnd ?: false)
            assertEquals("12:00 AM – 1:00 AM", itemFeb29?.timeRangeDisplay)
        }
}
