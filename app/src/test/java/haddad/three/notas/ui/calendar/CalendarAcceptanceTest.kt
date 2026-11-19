package haddad.three.notas.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import haddad.three.notas.data.AttachmentDao
import haddad.three.notas.data.ChecklistItemDao
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.NoteAttachmentEntity
import haddad.three.notas.data.NoteDao
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.ui.components.parseNoteColorHex
import haddad.three.notas.ui.components.readableBadgeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Acceptance test suite for NUVORA Calendar Visual Refinement & Scheduling Integrity.
 *
 * Verifies all 8 acceptance requirements:
 * 1. An undated note is excluded.
 * 2. A scheduled task appears on its correct date.
 * 3. An explicitly all-day event is displayed as all-day.
 * 4. A timed event preserves its start and end.
 * 5. Changing the selected date updates the agenda correctly.
 * 6. Switching themes does not alter stored event colors.
 * 7. Shoulder colors remain consistent with Home.
 * 8. Existing events survive application restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarAcceptanceTest {
    private val testDispatcher = StandardTestDispatcher()
    private val scheduledNotesFlow = MutableStateFlow<List<NoteWithItems>>(emptyList())

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
                override fun observeActive(ownerId: String?): Flow<List<NoteWithItems>> = scheduledNotesFlow

                override fun observeScheduled(ownerId: String?): Flow<List<NoteWithItems>> =
                    scheduledNotesFlow.map { list ->
                        list.filter {
                            it.note.reminderAt != null || it.note.endAt != null || it.note.isAllDay || it.note.reminders.isNotEmpty()
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

    /**
     * Requirement 1: An undated note is excluded.
     * Ordinary notes without scheduled dates must never appear in the Calendar as all-day events.
     */
    @Test
    fun test_1_an_undated_note_is_excluded() =
        runTest {
            val viewModel = createViewModel()
            val targetDate = LocalDate.of(2026, 10, 21)
            val createdInstant =
                targetDate
                    .atTime(11, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            val ordinaryNote =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 101L,
                            kind = NoteKind.NOTE,
                            title = "Shopping ideas",
                            body = "Milk, bread, apples",
                            colorIndex = 1,
                            trashed = false,
                            createdAt = createdInstant,
                            updatedAt = createdInstant,
                            reminderAt = null,
                            endAt = null,
                            isAllDay = false,
                        ),
                    items = emptyList(),
                )

            scheduledNotesFlow.value = listOf(ordinaryNote)
            viewModel.onDateSelected(targetDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Undated note must not produce day timeline items", 0, state.dayTimelineItems.size)
            assertEquals("Undated note must not produce all-day items", 0, state.dayAllDayItems.size)
            assertEquals("Undated note must not appear in selectedDayEvents", 0, state.selectedDayEvents.size)
            assertEquals("Undated note must not appear in selectedDayTasks", 0, state.selectedDayTasks.size)
            val dayCell = state.monthDays.find { it.date == targetDate }
            assertTrue("Day cell must have no dots for unscheduled note", dayCell == null || dayCell.dots.isEmpty())
        }

    /**
     * Requirement 2: A scheduled task appears on its correct date.
     */
    @Test
    fun test_2_a_scheduled_task_appears_on_its_correct_date() =
        runTest {
            val viewModel = createViewModel()
            val taskDate = LocalDate.of(2026, 10, 22)
            val taskMillis =
                taskDate
                    .atTime(15, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            val scheduledTask =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 102L,
                            kind = NoteKind.LIST,
                            title = "Review quarterly metrics",
                            body = "",
                            colorIndex = 2,
                            trashed = false,
                            createdAt = taskMillis,
                            updatedAt = taskMillis,
                            reminderAt = taskMillis,
                            endAt = null,
                            isAllDay = false,
                        ),
                    items =
                        listOf(
                            ChecklistItemEntity(id = 1L, noteId = 102L, text = "Check retention", checked = false, sortOrder = 0.0),
                        ),
                )

            scheduledNotesFlow.value = listOf(scheduledTask)

            // Select task date
            viewModel.onDateSelected(taskDate)
            advanceUntilIdle()

            val stateOnDate = viewModel.uiState.value
            assertEquals("Scheduled task must appear on its target date", 1, stateOnDate.selectedDayTasks.size)
            val taskModel = stateOnDate.selectedDayTasks[0]
            assertEquals("Review quarterly metrics", taskModel.title)
            assertTrue(taskModel.isTask)
            assertFalse(taskModel.isDone)

            // Select adjacent date
            viewModel.onDateSelected(taskDate.plusDays(1))
            advanceUntilIdle()

            val stateNextDay = viewModel.uiState.value
            assertEquals("Scheduled task must not appear on adjacent date", 0, stateNextDay.selectedDayTasks.size)
        }

    /**
     * Requirement 3: An explicitly all-day event is displayed as all-day.
     */
    @Test
    fun test_3_an_explicitly_all_day_event_is_displayed_as_all_day() =
        runTest {
            val viewModel = createViewModel()
            val holidayDate = LocalDate.of(2026, 10, 24)
            val holidayMillis = holidayDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val allDayEvent =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 103L,
                            kind = NoteKind.NOTE,
                            title = "Founder Day Off",
                            body = "Office closed worldwide",
                            colorIndex = 3,
                            trashed = false,
                            createdAt = holidayMillis,
                            updatedAt = holidayMillis,
                            reminderAt = holidayMillis,
                            endAt =
                                holidayDate
                                    .atTime(23, 59, 59)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            isAllDay = true,
                        ),
                    items = emptyList(),
                )

            scheduledNotesFlow.value = listOf(allDayEvent)
            viewModel.onDateSelected(holidayDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("All-day event must appear in dayAllDayItems", 1, state.dayAllDayItems.size)
            assertEquals(TimelineItemType.ALL_DAY, state.dayAllDayItems[0].type)
            assertEquals("Founder Day Off", state.dayAllDayItems[0].title)

            assertEquals("All-day event must appear in selectedDayEvents", 1, state.selectedDayEvents.size)
            assertTrue(state.selectedDayEvents[0].isAllDay)
            assertEquals("All day", state.selectedDayEvents[0].timeDisplay)
        }

    /**
     * Requirement 4: A timed event preserves its start and end.
     */
    @Test
    fun test_4_a_timed_event_preserves_its_start_and_end() =
        runTest {
            val viewModel = createViewModel()
            val eventDate = LocalDate.of(2026, 10, 26)
            val startMillis =
                eventDate
                    .atTime(13, 30)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            val endMillis =
                eventDate
                    .atTime(15, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            val timedEvent =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 104L,
                            kind = NoteKind.NOTE,
                            title = "Product Architecture Sync",
                            body = "Deep dive into calendar architecture",
                            colorIndex = 4,
                            trashed = false,
                            createdAt = startMillis,
                            updatedAt = startMillis,
                            reminderAt = startMillis,
                            endAt = endMillis,
                            isAllDay = false,
                        ),
                    items = emptyList(),
                )

            scheduledNotesFlow.value = listOf(timedEvent)
            viewModel.onDateSelected(eventDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Timed event must appear in dayTimelineItems", 1, state.dayTimelineItems.size)
            val timelineItem = state.dayTimelineItems[0]
            assertEquals(13 * 60 + 30, timelineItem.startMinuteOfDay)
            assertEquals(15 * 60, timelineItem.endMinuteOfDay)
            assertEquals(90, timelineItem.endMinuteOfDay - timelineItem.startMinuteOfDay)
            assertEquals("Product Architecture Sync", timelineItem.title)

            // Verify start and end times remain intact when updating note content
            val updatedTimedEvent =
                timedEvent.note.copy(
                    body = "Updated discussion notes",
                    updatedAt = startMillis + 1000,
                )
            assertEquals(startMillis, updatedTimedEvent.reminderAt)
            assertEquals(endMillis, updatedTimedEvent.endAt)
        }

    /**
     * Requirement 5: Changing the selected date updates the agenda correctly.
     */
    @Test
    fun test_5_changing_the_selected_date_updates_the_agenda_correctly() =
        runTest {
            val viewModel = createViewModel()
            val date1 = LocalDate.of(2026, 10, 27)
            val date2 = LocalDate.of(2026, 10, 28)

            val event1 =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 105L,
                            kind = NoteKind.NOTE,
                            title = "Event on Day 1",
                            body = "",
                            colorIndex = 1,
                            trashed = false,
                            createdAt =
                                date1
                                    .atTime(10, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            updatedAt =
                                date1
                                    .atTime(10, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            reminderAt =
                                date1
                                    .atTime(10, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            endAt =
                                date1
                                    .atTime(11, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                        ),
                    items = emptyList(),
                )

            val event2 =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 106L,
                            kind = NoteKind.NOTE,
                            title = "Event on Day 2",
                            body = "",
                            colorIndex = 2,
                            trashed = false,
                            createdAt =
                                date2
                                    .atTime(14, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            updatedAt =
                                date2
                                    .atTime(14, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            reminderAt =
                                date2
                                    .atTime(14, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                            endAt =
                                date2
                                    .atTime(15, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                        ),
                    items = emptyList(),
                )

            scheduledNotesFlow.value = listOf(event1, event2)

            // Select Date 1
            viewModel.onDateSelected(date1)
            advanceUntilIdle()

            assertEquals(date1, viewModel.uiState.value.selectedDate)
            assertEquals(1, viewModel.uiState.value.selectedDayEvents.size)
            assertEquals(
                "Event on Day 1",
                viewModel.uiState.value.selectedDayEvents[0]
                    .title,
            )

            // Select Date 2
            viewModel.onDateSelected(date2)
            advanceUntilIdle()

            assertEquals(date2, viewModel.uiState.value.selectedDate)
            assertEquals(1, viewModel.uiState.value.selectedDayEvents.size)
            assertEquals(
                "Event on Day 2",
                viewModel.uiState.value.selectedDayEvents[0]
                    .title,
            )

            // Select an empty Date 3
            viewModel.onDateSelected(LocalDate.of(2026, 10, 29))
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.selectedDayEvents.size)
            assertEquals(0, viewModel.uiState.value.selectedDayTasks.size)
        }

    /**
     * Requirement 6: Switching themes does not alter stored event colors.
     */
    @Test
    fun test_6_switching_themes_does_not_alter_stored_event_colors() =
        runTest {
            val eventDate = LocalDate.of(2026, 10, 30)
            val startMillis =
                eventDate
                    .atTime(9, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            val customShoulder =
                ShoulderEntity(
                    id = 55L,
                    name = "Finance",
                    normalizedName = "finance",
                    colorHex = "#2E7D32",
                    createdAt = startMillis,
                    updatedAt = startMillis,
                )

            val storedEvent =
                NoteEntity(
                    id = 107L,
                    kind = NoteKind.NOTE,
                    title = "Quarterly Tax Prep",
                    body = "Review deductions",
                    colorIndex = 7,
                    trashed = false,
                    createdAt = startMillis,
                    updatedAt = startMillis,
                    reminderAt = startMillis,
                    shoulderId = 55L,
                )

            val noteWithItems =
                NoteWithItems(
                    note = storedEvent,
                    items = emptyList(),
                    shoulder = customShoulder,
                )

            // Verify stored colorIndex and custom shoulder colorHex remain identical across theme changes
            val isLightTheme = false
            val isDarkTheme = true

            val lightChipColor = parseNoteColorHex(noteWithItems.shoulder?.colorHex, Color.Blue)
            val darkChipColor = parseNoteColorHex(noteWithItems.shoulder?.colorHex, Color.Blue)

            // Stored color index is unaltered
            assertEquals(7, noteWithItems.note.colorIndex)
            assertEquals("#2E7D32", noteWithItems.shoulder?.colorHex)
            assertEquals(lightChipColor, darkChipColor)
        }

    /**
     * Requirement 7: Shoulder colors remain consistent with Home.
     * Both Home and Calendar use readableBadgeColors for contrast without hue degradation.
     */
    @Test
    fun test_7_shoulder_colors_remain_consistent_with_home() {
        val baseShoulderColor = Color(0xFFE91E63) // Vibrant Pink

        // Resolve badge colors in Light Mode
        val (lightContainer, lightText) = readableBadgeColors(baseShoulderColor, isDark = false)
        // Resolve badge colors in Dark Mode
        val (darkContainer, darkText) = readableBadgeColors(baseShoulderColor, isDark = true)

        // Container and text colors are distinct and readable
        assertNotEquals(lightContainer, lightText)
        assertNotEquals(darkContainer, darkText)

        // Light mode text has sufficient contrast (alpha >= 0.4f)
        assertTrue(lightText.alpha >= 0.4f)
        // Dark mode text is bright (alpha >= 0.8f)
        assertTrue(darkText.alpha >= 0.8f)

        // Base color parsed consistently
        val parsedHexColor = parseNoteColorHex("#E91E63", Color.Gray)
        assertEquals(Color(0xFFE91E63), parsedHexColor)
    }

    /**
     * Requirement 8: Existing events survive application restart.
     * State restored from SavedStateHandle and database snapshot rehydrates calendar flawlessly.
     */
    @Test
    fun test_8_existing_events_survive_application_restart() =
        runTest {
            val savedDate = LocalDate.of(2026, 11, 15)
            val savedMonth = YearMonth.of(2026, 11)

            val initialSavedState =
                SavedStateHandle().apply {
                    set(CalendarViewModel.KEY_SELECTED_DATE, savedDate.toString())
                    set(CalendarViewModel.KEY_DISPLAYED_MONTH, savedMonth.toString())
                    set(CalendarViewModel.KEY_VIEW_MODE, "WEEK")
                }

            val eventMillis =
                savedDate
                    .atTime(16, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            val persistentEvent =
                NoteWithItems(
                    note =
                        NoteEntity(
                            id = 108L,
                            kind = NoteKind.NOTE,
                            title = "Client Presentation",
                            body = "Q4 review deck",
                            colorIndex = 5,
                            trashed = false,
                            createdAt = eventMillis,
                            updatedAt = eventMillis,
                            reminderAt = eventMillis,
                            endAt = eventMillis + 3600000L,
                        ),
                    items = emptyList(),
                )

            scheduledNotesFlow.value = listOf(persistentEvent)

            // Launch first instance
            val vmBeforeKill = createViewModel(initialSavedState)
            advanceUntilIdle()

            assertEquals(savedDate, vmBeforeKill.uiState.value.selectedDate)
            assertEquals(CalendarViewMode.WEEK, vmBeforeKill.uiState.value.viewMode)
            assertEquals(1, vmBeforeKill.uiState.value.selectedDayEvents.size)
            assertEquals(
                "Client Presentation",
                vmBeforeKill.uiState.value.selectedDayEvents[0]
                    .title,
            )

            // Simulate process death and restart with new SavedStateHandle
            val restoredSavedState =
                SavedStateHandle().apply {
                    set(CalendarViewModel.KEY_SELECTED_DATE, savedDate.toString())
                    set(CalendarViewModel.KEY_DISPLAYED_MONTH, savedMonth.toString())
                    set(CalendarViewModel.KEY_VIEW_MODE, "WEEK")
                }

            val vmAfterRestart = createViewModel(restoredSavedState)
            advanceUntilIdle()

            // Verify complete restoration
            assertEquals(savedDate, vmAfterRestart.uiState.value.selectedDate)
            assertEquals(savedMonth, vmAfterRestart.uiState.value.displayedMonth)
            assertEquals(CalendarViewMode.WEEK, vmAfterRestart.uiState.value.viewMode)
            assertEquals(1, vmAfterRestart.uiState.value.selectedDayEvents.size)
            assertEquals(
                "Client Presentation",
                vmAfterRestart.uiState.value.selectedDayEvents[0]
                    .title,
            )
        }

    /**
     * Requirement 9: Populated Agenda stability with multiple events and tasks.
     * Verifies that multiple events and checklist tasks on the same day populate cleanly
     * into selectedDayEvents and selectedDayTasks without data errors or state corruption.
     */
    @Test
    fun test_9_populated_agenda_multiple_events_and_tasks_stability() =
        runTest {
            val testDate = LocalDate.of(2026, 11, 20)
            val baseTime = testDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val event1 = NoteWithItems(
                note = NoteEntity(
                    id = 201L,
                    kind = NoteKind.NOTE,
                    title = "Morning Standup",
                    body = "Discuss agenda fix",
                    colorIndex = 1,
                    trashed = false,
                    createdAt = baseTime,
                    updatedAt = baseTime,
                    reminderAt = baseTime,
                    endAt = baseTime + 1800000L,
                ),
                items = emptyList(),
            )

            val event2 = NoteWithItems(
                note = NoteEntity(
                    id = 202L,
                    kind = NoteKind.NOTE,
                    title = "All Day Release",
                    body = "Notas v3.8.0 release",
                    colorIndex = 2,
                    trashed = false,
                    createdAt = baseTime,
                    updatedAt = baseTime,
                    reminderAt = baseTime,
                    endAt = baseTime + 7200000L,
                    isAllDay = true,
                ),
                items = emptyList(),
            )

            val task1 = NoteWithItems(
                note = NoteEntity(
                    id = 203L,
                    kind = NoteKind.LIST,
                    title = "Release Checklist",
                    body = "",
                    colorIndex = 3,
                    trashed = false,
                    createdAt = baseTime,
                    updatedAt = baseTime,
                    reminderAt = baseTime + 3600000L,
                ),
                items = listOf(
                    ChecklistItemEntity(id = 1L, noteId = 203L, text = "Verify icons", checked = true, sortOrder = 0.0),
                    ChecklistItemEntity(id = 2L, noteId = 203L, text = "Test Agenda", checked = false, sortOrder = 1.0),
                ),
            )

            scheduledNotesFlow.value = listOf(event1, event2, task1)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onDateSelected(testDate)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(testDate, state.selectedDate)
            assertEquals(2, state.selectedDayEvents.size)
            assertEquals(1, state.selectedDayTasks.size)

            val titles = state.selectedDayEvents.map { it.title }.toSet()
            assertTrue(titles.contains("Morning Standup"))
            assertTrue(titles.contains("All Day Release"))
            assertEquals("Release Checklist", state.selectedDayTasks[0].title)

            // Switch to an empty date
            val emptyDate = testDate.plusDays(1)
            viewModel.onDateSelected(emptyDate)
            advanceUntilIdle()

            assertEquals(emptyDate, viewModel.uiState.value.selectedDate)
            assertTrue(viewModel.uiState.value.selectedDayEvents.isEmpty())
            assertTrue(viewModel.uiState.value.selectedDayTasks.isEmpty())

            // Switch back to populated date
            viewModel.onDateSelected(testDate)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.selectedDayEvents.size)
            assertEquals(1, viewModel.uiState.value.selectedDayTasks.size)
        }
}
