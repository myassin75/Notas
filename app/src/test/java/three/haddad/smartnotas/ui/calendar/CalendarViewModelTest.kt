package three.haddad.smartnotas.ui.calendar

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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val activeNotesFlow = MutableStateFlow<List<NoteWithItems>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): CalendarViewModel {
        val fakeDao = object : NoteDao {
            override fun observeActive(): Flow<List<NoteWithItems>> = activeNotesFlow
            override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())
            override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(null)
            override suspend fun get(id: Long): NoteWithItems? = null
            override suspend fun allNotes(): List<NoteWithItems> = emptyList()
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
            override suspend fun countByUri(uri: String): Int = 0
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun deleteForNote(noteId: Long) = Unit
        }

        val repository = NoteRepository(
            noteDao = fakeDao,
            itemDao = fakeItemDao,
            attachmentDao = fakeAttachmentDao,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        val viewModel = CalendarViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    @Test
    fun initial_state_defaults_to_today_and_current_month() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.now(), state.selectedDate)
        assertEquals(YearMonth.now(), state.displayedMonth)
        assertEquals(CalendarViewMode.MONTH, state.viewMode)
        assertTrue(state.monthDays.isNotEmpty())
    }

    @Test
    fun month_navigation_preserves_selected_date_across_boundaries() = runTest {
        val viewModel = createViewModel()
        val targetDate = LocalDate.of(2026, 10, 3)
        viewModel.onDateSelected(targetDate)
        advanceUntilIdle()

        assertEquals(targetDate, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.displayedMonth)

        // Navigate forward into next month
        viewModel.onNextMonth()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 11), viewModel.uiState.value.displayedMonth)
        // Selected date must NOT be overwritten by month navigation
        assertEquals(targetDate, viewModel.uiState.value.selectedDate)

        // Navigate across year boundary (Nov 2026 -> Dec 2026 -> Jan 2027)
        viewModel.onNextMonth()
        viewModel.onNextMonth()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2027, 1), viewModel.uiState.value.displayedMonth)
        assertEquals(targetDate, viewModel.uiState.value.selectedDate)

        // Navigate backwards across year boundary to Dec 2026
        viewModel.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.displayedMonth)
        assertEquals(targetDate, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun today_button_resets_selected_date_and_displayed_month() = runTest {
        val viewModel = createViewModel()
        val pastDate = LocalDate.of(2024, 2, 14)
        viewModel.onDateSelected(pastDate)
        advanceUntilIdle()

        assertEquals(pastDate, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2024, 2), viewModel.uiState.value.displayedMonth)

        viewModel.onTodayClicked()
        advanceUntilIdle()

        assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.now(), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun task_with_multiple_reminders_on_same_day_is_not_duplicated() = runTest {
        val viewModel = createViewModel()
        val date = LocalDate.of(2026, 10, 15)
        val epochMillis = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val epochMillis2 = date.atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val multiReminderNote = NoteWithItems(
            note = NoteEntity(
                id = 42L,
                kind = NoteKind.NOTE,
                title = "Sprint Planning",
                body = "Discuss roadmap",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = epochMillis,
                reminders = listOf(
                    NoteReminder(reminderAt = epochMillis),
                    NoteReminder(reminderAt = epochMillis2),
                ),
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(multiReminderNote)
        viewModel.onDateSelected(date)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val dayCell = state.monthDays.find { it.date == date }
        // Must have exactly 1 dot, not duplicated
        assertEquals(1, dayCell?.dots?.size)
        // Must have exactly 1 agenda item
        assertEquals(1, state.selectedDayEvents.size)
        assertEquals("Sprint Planning", state.selectedDayEvents[0].title)
    }

    @Test
    fun recurring_weekly_events_project_across_month_grid() = runTest {
        val viewModel = createViewModel()
        val startDate = LocalDate.of(2026, 10, 5) // Monday
        val epochMillis = startDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val recurringNote = NoteWithItems(
            note = NoteEntity(
                id = 88L,
                kind = NoteKind.NOTE,
                title = "Weekly Standup",
                body = "",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = epochMillis,
                recurrence = RecurrenceRule(
                    unit = RecurrenceUnit.WEEK,
                    interval = 1,
                ),
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(recurringNote)
        viewModel.onDateSelected(startDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Mondays in Oct 2026: 5, 12, 19, 26
        val oct5 = state.monthDays.find { it.date == LocalDate.of(2026, 10, 5) }
        val oct12 = state.monthDays.find { it.date == LocalDate.of(2026, 10, 12) }
        val oct19 = state.monthDays.find { it.date == LocalDate.of(2026, 10, 19) }
        val oct26 = state.monthDays.find { it.date == LocalDate.of(2026, 10, 26) }

        assertEquals(1, oct5?.dots?.size)
        assertEquals(1, oct12?.dots?.size)
        assertEquals(1, oct19?.dots?.size)
        assertEquals(1, oct26?.dots?.size)

        // Tuesday Oct 6 should have 0 dots
        val oct6 = state.monthDays.find { it.date == LocalDate.of(2026, 10, 6) }
        assertEquals(0, oct6?.dots?.size)
    }

    @Test
    fun birthday_notes_are_surfaced_in_birthdays_section() = runTest {
        val viewModel = createViewModel()
        val bdayDate = LocalDate.of(2026, 10, 3)
        val epochMillis = bdayDate.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val birthdayNote = NoteWithItems(
            note = NoteEntity(
                id = 999L,
                kind = NoteKind.NOTE,
                title = "Tommy's birthday",
                body = "Bring cake",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = epochMillis,
                iconKey = "cake",
            ),
            items = emptyList(),
            shoulder = ShoulderEntity(
                id = 1L,
                name = "Birthdays",
                normalizedName = "birthdays",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        activeNotesFlow.value = listOf(birthdayNote)
        viewModel.onDateSelected(bdayDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedDayBirthdays.size)
        assertEquals("Tommy's birthday", state.selectedDayBirthdays[0].name)
    }

    @Test
    fun week_view_calculates_seven_consecutive_days_starting_monday() = runTest {
        val viewModel = createViewModel()
        val saturday = LocalDate.of(2026, 10, 3)
        viewModel.onDateSelected(saturday)
        viewModel.onViewModeSelected(CalendarViewMode.WEEK)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(7, state.weekDays.size)
        assertEquals(LocalDate.of(2026, 9, 28), state.weekDays[0].date)
        assertEquals("M", state.weekDays[0].dayOfWeekLetter)
        assertEquals(28, state.weekDays[0].dayNumber)

        assertEquals(LocalDate.of(2026, 10, 4), state.weekDays[6].date)
        assertEquals("S", state.weekDays[6].dayOfWeekLetter)
        assertEquals(4, state.weekDays[6].dayNumber)

        assertEquals("Sep 28 – Oct 4", state.weekRangeTitle)
        assertEquals("2026", state.weekYearLabel)

        // Saturday Oct 3 is the 6th day (index 5)
        assertTrue(state.weekDays[5].isSelected)
        assertFalse(state.weekDays[0].isSelected)
    }

    @Test
    fun week_navigation_pages_across_months_and_years() = runTest {
        val viewModel = createViewModel()
        val initialDate = LocalDate.of(2026, 10, 3)
        viewModel.onDateSelected(initialDate)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 28), viewModel.uiState.value.currentWeekStart)

        // Navigate to next week: Oct 5 - Oct 11
        viewModel.onNextWeek()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 10, 5), viewModel.uiState.value.currentWeekStart)
        assertEquals("Oct 5 – 11", viewModel.uiState.value.weekRangeTitle)

        // Navigate backwards 2 weeks: Sep 21 - Sep 27
        viewModel.onPreviousWeek()
        viewModel.onPreviousWeek()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 21), viewModel.uiState.value.currentWeekStart)
        assertEquals("Sep 21 – 27", viewModel.uiState.value.weekRangeTitle)

        // Jump to year boundary: Dec 28, 2026
        val endOfYearDate = LocalDate.of(2026, 12, 30)
        viewModel.onDateSelected(endOfYearDate)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 12, 28), viewModel.uiState.value.currentWeekStart)
        assertEquals("Dec 28 – Jan 3", viewModel.uiState.value.weekRangeTitle)
        assertEquals("2026 / 2027", viewModel.uiState.value.weekYearLabel)
    }

    @Test
    fun week_view_calculates_total_events_and_groups_by_day() = runTest {
        val viewModel = createViewModel()
        val monday = LocalDate.of(2026, 9, 28)
        val saturday = LocalDate.of(2026, 10, 3)

        val mondayMillis = monday.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val satMillis = saturday.atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val note1 = NoteWithItems(
            note = NoteEntity(
                id = 101L,
                kind = NoteKind.NOTE,
                title = "Weekly Planning",
                body = "",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = mondayMillis,
            ),
            items = emptyList(),
        )

        val note2 = NoteWithItems(
            note = NoteEntity(
                id = 102L,
                kind = NoteKind.NOTE,
                title = "Coffee with Sarah",
                body = "",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = satMillis,
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(note1, note2)
        viewModel.onDateSelected(saturday)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.weekTotalEventsCount)
        assertEquals(1, state.weekDays[0].events.size) // Monday
        assertEquals("Weekly Planning", state.weekDays[0].events[0].title)
        assertEquals(0, state.weekDays[1].events.size) // Tuesday
        assertEquals(1, state.weekDays[5].events.size) // Saturday
        assertEquals("Coffee with Sarah", state.weekDays[5].events[0].title)
    }

    @Test
    fun day_navigation_increments_and_decrements_date_correctly() = runTest {
        val viewModel = createViewModel()
        val date = LocalDate.of(2026, 10, 3)
        viewModel.onDateSelected(date)
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.selectedDate)

        // Navigate to next day
        viewModel.onNextDay()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 4), viewModel.uiState.value.selectedDate)

        // Navigate to previous day
        viewModel.onPreviousDay()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 3), viewModel.uiState.value.selectedDate)

        // Navigate across month boundary
        viewModel.onDateSelected(LocalDate.of(2026, 10, 31))
        advanceUntilIdle()
        viewModel.onNextDay()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 11, 1), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 11), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun timeline_distinguishes_duration_events_from_instantaneous_tasks() = runTest {
        val viewModel = createViewModel()
        val testDate = LocalDate.of(2026, 10, 3)
        val eventEpoch = testDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val taskEpoch = testDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val eventNote = NoteWithItems(
            note = NoteEntity(
                id = 201L,
                kind = NoteKind.NOTE,
                title = "Weekly Planning",
                body = "9:00–9:30 AM · Personal",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = eventEpoch,
            ),
            items = emptyList(),
        )

        val taskNote = NoteWithItems(
            note = NoteEntity(
                id = 202L,
                kind = NoteKind.LIST,
                title = "Buy Groceries",
                body = "",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = taskEpoch,
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(eventNote, taskNote)
        viewModel.onDateSelected(testDate)
        advanceUntilIdle()

        val timelineItems = viewModel.uiState.value.dayTimelineItems
        assertEquals(2, timelineItems.size)

        val eventItem = timelineItems.find { it.id == 201L }!!
        assertEquals(TimelineItemType.EVENT, eventItem.type)
        assertEquals(540, eventItem.startMinuteOfDay) // 9:00 AM
        assertEquals(570, eventItem.endMinuteOfDay)   // 9:30 AM (30 min duration)

        val taskItem = timelineItems.find { it.id == 202L }!!
        assertEquals(TimelineItemType.TASK, taskItem.type)
        assertEquals(600, taskItem.startMinuteOfDay) // 10:00 AM
        assertEquals(600, taskItem.endMinuteOfDay)   // instantaneous
    }

    @Test
    fun timeline_deduplicates_notes_with_multiple_reminders_on_same_day() = runTest {
        val viewModel = createViewModel()
        val testDate = LocalDate.of(2026, 10, 3)
        val time1 = testDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val time2 = testDate.atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val multiReminderNote = NoteWithItems(
            note = NoteEntity(
                id = 301L,
                kind = NoteKind.NOTE,
                title = "Team Retro",
                body = "",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = time1,
                reminders = listOf(
                    NoteReminder(reminderAt = time1),
                    NoteReminder(reminderAt = time2),
                ),
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(multiReminderNote)
        viewModel.onDateSelected(testDate)
        advanceUntilIdle()

        val timelineItems = viewModel.uiState.value.dayTimelineItems
        assertEquals(1, timelineItems.size)
        assertEquals(301L, timelineItems[0].id)
    }

    @Test
    fun timeline_assigns_non_overlapping_columns_for_concurrent_events() = runTest {
        val viewModel = createViewModel()
        val testDate = LocalDate.of(2026, 10, 3)
        val time1 = testDate.atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val time2 = testDate.atTime(14, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val event1 = NoteWithItems(
            note = NoteEntity(
                id = 401L,
                kind = NoteKind.NOTE,
                title = "Coffee with Sarah",
                body = "2:00–3:00 PM · Café",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = time1,
            ),
            items = emptyList(),
        )

        val event2 = NoteWithItems(
            note = NoteEntity(
                id = 402L,
                kind = NoteKind.NOTE,
                title = "Product Review",
                body = "2:30–3:30 PM",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = time2,
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(event1, event2)
        viewModel.onDateSelected(testDate)
        advanceUntilIdle()

        val timelineItems = viewModel.uiState.value.dayTimelineItems
        assertEquals(2, timelineItems.size)

        val item1 = timelineItems.find { it.id == 401L }!!
        val item2 = timelineItems.find { it.id == 402L }!!

        assertEquals(2, item1.totalCols)
        assertEquals(2, item2.totalCols)
        assertFalse(item1.colIndex == item2.colIndex)
    }

    @Test
    fun timeline_handles_cross_midnight_events() = runTest {
        val viewModel = createViewModel()
        val oct3 = LocalDate.of(2026, 10, 3)
        val oct4 = LocalDate.of(2026, 10, 4)
        val lateEpoch = oct3.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lateEvent = NoteWithItems(
            note = NoteEntity(
                id = 501L,
                kind = NoteKind.NOTE,
                title = "Late Night Hackathon",
                body = "2 hours",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = lateEpoch,
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(lateEvent)

        // Check on Oct 3: starts at 23:00 (1380), clamped at midnight (1440)
        viewModel.onDateSelected(oct3)
        advanceUntilIdle()

        val oct3Items = viewModel.uiState.value.dayTimelineItems
        assertEquals(1, oct3Items.size)
        assertEquals(1380, oct3Items[0].startMinuteOfDay)
        assertEquals(1440, oct3Items[0].endMinuteOfDay)
        assertTrue(oct3Items[0].isCrossMidnightStart)

        // Check on Oct 4: carries over from midnight (0) to 01:00 AM (60)
        viewModel.onDateSelected(oct4)
        advanceUntilIdle()

        val oct4Items = viewModel.uiState.value.dayTimelineItems
        assertEquals(1, oct4Items.size)
        assertEquals(0, oct4Items[0].startMinuteOfDay)
        assertEquals(60, oct4Items[0].endMinuteOfDay)
        assertTrue(oct4Items[0].isCrossMidnightEnd)
    }

    @Test
    fun timeline_places_unscheduled_items_in_allday_section() = runTest {
        val viewModel = createViewModel()
        val testDate = LocalDate.of(2026, 10, 3)
        val createdMillis = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val unscheduledNote = NoteWithItems(
            note = NoteEntity(
                id = 601L,
                kind = NoteKind.NOTE,
                title = "Ideas to research",
                body = "No reminder",
                colorIndex = 0,
                trashed = false,
                createdAt = createdMillis,
                updatedAt = createdMillis,
                reminderAt = null,
            ),
            items = emptyList(),
        )

        activeNotesFlow.value = listOf(unscheduledNote)
        viewModel.onDateSelected(testDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.dayTimelineItems.size)
        assertEquals(1, state.dayAllDayItems.size)
        assertEquals(TimelineItemType.ALL_DAY, state.dayAllDayItems[0].type)
        assertEquals("Ideas to research", state.dayAllDayItems[0].title)
    }
}
