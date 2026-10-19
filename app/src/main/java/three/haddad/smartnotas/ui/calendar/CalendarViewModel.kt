package three.haddad.smartnotas.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import three.haddad.smartnotas.di.DefaultDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import three.haddad.smartnotas.data.Importance
import three.haddad.smartnotas.data.MonthlyMode
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteReminder
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.RecurrenceUnit
import three.haddad.smartnotas.data.getActiveReminders
import three.haddad.smartnotas.ui.theme.NoteColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private val MonthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val DayOnlyFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())
private val EEEFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
private val Time12HourMinuteFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
private val Time12HourMinuteAmPmFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val AmPmFormatter = DateTimeFormatter.ofPattern("a", Locale.getDefault())

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val noteRepository: NoteRepository,
        private val savedStateHandle: SavedStateHandle,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : ViewModel() {
        constructor(noteRepository: NoteRepository) : this(noteRepository, SavedStateHandle(), Dispatchers.Default)

        companion object {
            const val KEY_SELECTED_DATE = "calendar_selected_date"
            const val KEY_DISPLAYED_MONTH = "calendar_displayed_month"
            const val KEY_VIEW_MODE = "calendar_view_mode"
        }

        private val _selectedDate =
            MutableStateFlow(
                savedStateHandle
                    .get<String>(KEY_SELECTED_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now(),
            )
        private val _displayedMonth =
            MutableStateFlow(
                savedStateHandle
                    .get<String>(KEY_DISPLAYED_MONTH)
                    ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                    ?: YearMonth.from(_selectedDate.value),
            )
        private val _currentWeekStart =
            MutableStateFlow(
                _selectedDate.value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            )
        private val _viewMode =
            MutableStateFlow(
                savedStateHandle
                    .get<String>(KEY_VIEW_MODE)
                    ?.let { runCatching { CalendarViewMode.valueOf(it) }.getOrNull() }
                    ?: CalendarViewMode.MONTH,
            )
        private val _errorMessage = MutableStateFlow<String?>(null)

        val uiState: StateFlow<CalendarUiState> =
            combine(
                _selectedDate,
                _displayedMonth,
                _currentWeekStart,
                _viewMode,
                noteRepository.observeActive().catch { e ->
                    _errorMessage.value = e.message ?: "Failed to load events"
                    emit(emptyList())
                },
            ) { selectedDate, displayedMonth, currentWeekStart, viewMode, activeNotes ->
                buildCalendarUiState(selectedDate, displayedMonth, currentWeekStart, viewMode, activeNotes, _errorMessage.value)
            }.flowOn(defaultDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CalendarUiState(today = LocalDate.now()),
            )

        fun onDateSelected(date: LocalDate) {
            _selectedDate.value = date
            savedStateHandle[KEY_SELECTED_DATE] = date.toString()
            _currentWeekStart.value = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val targetMonth = YearMonth.from(date)
            if (targetMonth != _displayedMonth.value) {
                _displayedMonth.value = targetMonth
                savedStateHandle[KEY_DISPLAYED_MONTH] = targetMonth.toString()
            }
        }

        fun onPreviousMonth() {
            _displayedMonth.update {
                val prev = it.minusMonths(1)
                savedStateHandle[KEY_DISPLAYED_MONTH] = prev.toString()
                prev
            }
        }

        fun onNextMonth() {
            _displayedMonth.update {
                val next = it.plusMonths(1)
                savedStateHandle[KEY_DISPLAYED_MONTH] = next.toString()
                next
            }
        }

        fun onPreviousWeek() {
            _currentWeekStart.update { it.minusWeeks(1) }
        }

        fun onNextWeek() {
            _currentWeekStart.update { it.plusWeeks(1) }
        }

        fun onPreviousDay() {
            onDateSelected(_selectedDate.value.minusDays(1))
        }

        fun onNextDay() {
            onDateSelected(_selectedDate.value.plusDays(1))
        }

        fun onTodayClicked() {
            val today = LocalDate.now()
            _selectedDate.value = today
            savedStateHandle[KEY_SELECTED_DATE] = today.toString()
            _currentWeekStart.value = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val todayMonth = YearMonth.from(today)
            _displayedMonth.value = todayMonth
            savedStateHandle[KEY_DISPLAYED_MONTH] = todayMonth.toString()
        }

        fun onViewModeSelected(mode: CalendarViewMode) {
            _viewMode.value = mode
            savedStateHandle[KEY_VIEW_MODE] = mode.name
        }

        fun onPreviousYear() {
            _displayedMonth.update {
                val prev = it.minusYears(1)
                savedStateHandle[KEY_DISPLAYED_MONTH] = prev.toString()
                prev
            }
        }

        fun onNextYear() {
            _displayedMonth.update {
                val next = it.plusYears(1)
                savedStateHandle[KEY_DISPLAYED_MONTH] = next.toString()
                next
            }
        }

        fun onMonthSelected(yearMonth: YearMonth) {
            _displayedMonth.value = yearMonth
            savedStateHandle[KEY_DISPLAYED_MONTH] = yearMonth.toString()
            val currentDay = _selectedDate.value.dayOfMonth
            val maxDay = yearMonth.lengthOfMonth()
            val targetDay = minOf(currentDay, maxDay)
            val newDate = yearMonth.atDay(targetDay)
            _selectedDate.value = newDate
            savedStateHandle[KEY_SELECTED_DATE] = newDate.toString()
            _currentWeekStart.value = newDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            _viewMode.value = CalendarViewMode.MONTH
            savedStateHandle[KEY_VIEW_MODE] = CalendarViewMode.MONTH.name
        }

        fun toggleEventDone(
            noteId: Long,
            currentDone: Boolean,
        ) {
            viewModelScope.launch {
                if (currentDone) {
                    noteRepository.markIncomplete(noteId)
                } else {
                    noteRepository.markCompleted(noteId)
                }
            }
        }

        private fun buildCalendarUiState(
            selectedDate: LocalDate,
            displayedMonth: YearMonth,
            currentWeekStart: LocalDate,
            viewMode: CalendarViewMode,
            activeNotes: List<NoteWithItems>,
            errorMessage: String? = null,
        ): CalendarUiState {
            val today = LocalDate.now()

            // 1. Compute month grid dates (Monday-to-Sunday)
            val firstOfMonth = displayedMonth.atDay(1)
            val firstGridDate = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val lastOfMonth = displayedMonth.atEndOfMonth()
            val lastGridDate = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

            val gridDates = mutableListOf<LocalDate>()
            var curr = firstGridDate
            while (!curr.isAfter(lastGridDate)) {
                gridDates.add(curr)
                curr = curr.plusDays(1)
            }

            while (gridDates.size < 35 || gridDates.size % 7 != 0) {
                gridDates.add(curr)
                curr = curr.plusDays(1)
            }

            val monthDays =
                gridDates.map { date ->
                    val notesOnDate = activeNotes.filter { matchesDate(it, date) }
                    val isSelected = date == selectedDate
                    val isOutOfMonth = YearMonth.from(date) != displayedMonth
                    val isToday = (date == today) && !isOutOfMonth

                    val state =
                        when {
                            isSelected -> CalendarDayState.SELECTED
                            isToday -> CalendarDayState.TODAY
                            isOutOfMonth -> CalendarDayState.OUT_OF_MONTH
                            else -> CalendarDayState.NORMAL
                        }

                    val dots =
                        notesOnDate.distinctBy { it.note.id }.take(4).map { noteWithItems ->
                            val color =
                                when {
                                    noteWithItems.note.importance == Importance.HIGH ||
                                        noteWithItems.note.importance == Importance.CRITICAL -> CalendarDesignTokens.DotOrange
                                    !NoteColors.isNone(noteWithItems.note.colorIndex) -> NoteColors.at(noteWithItems.note.colorIndex).light
                                    noteWithItems.note.kind == NoteKind.LIST || noteWithItems.note.checklistText.isNotBlank() -> CalendarDesignTokens.DotGreen
                                    noteWithItems.shoulder?.name?.contains("work", ignoreCase = true) == true -> CalendarDesignTokens.DotBlue
                                    else -> CalendarDesignTokens.DotPurple
                                }
                            CalendarDot(color)
                        }

                    CalendarDayUiModel(
                        date = date,
                        dayNumber = date.dayOfMonth,
                        state = state,
                        dots = dots,
                        isSelected = isSelected,
                        isToday = isToday,
                    )
                }

            // 2. Compute 7 consecutive days for the Week View
            val weekDateList = (0L..6L).map { currentWeekStart.plusDays(it) }
            val weekStart = weekDateList.first()
            val weekEnd = weekDateList.last()

            val weekRangeTitle =
                if (weekStart.month == weekEnd.month) {
                    "${weekStart.format(MonthDayFormatter)} – ${weekEnd.format(DayOnlyFormatter)}"
                } else {
                    "${weekStart.format(MonthDayFormatter)} – ${weekEnd.format(MonthDayFormatter)}"
                }

            val weekYearLabel =
                if (weekStart.year == weekEnd.year) {
                    "${weekStart.year}"
                } else {
                    "${weekStart.year} / ${weekEnd.year}"
                }

            var totalWeekEvents = 0
            val weekDays =
                weekDateList.map { date ->
                    val notesOnDate = activeNotes.filter { matchesDate(it, date) }
                    val standardNotes = notesOnDate.filter { !isBirthday(it) }
                    val dayEvents =
                        standardNotes.map { noteWithItems ->
                            val isTaskItem = noteWithItems.note.kind == NoteKind.LIST || noteWithItems.note.checklistText.isNotBlank()
                            mapToEventUiModel(noteWithItems, date, isTask = isTaskItem)
                        }
                    totalWeekEvents += dayEvents.size

                    val dots =
                        notesOnDate.distinctBy { it.note.id }.take(4).map { noteWithItems ->
                            val color =
                                when {
                                    noteWithItems.note.importance == Importance.HIGH ||
                                        noteWithItems.note.importance == Importance.CRITICAL -> CalendarDesignTokens.DotOrange
                                    !NoteColors.isNone(noteWithItems.note.colorIndex) -> NoteColors.at(noteWithItems.note.colorIndex).light
                                    noteWithItems.note.kind == NoteKind.LIST || noteWithItems.note.checklistText.isNotBlank() -> CalendarDesignTokens.DotGreen
                                    noteWithItems.shoulder?.name?.contains("work", ignoreCase = true) == true -> CalendarDesignTokens.DotBlue
                                    else -> CalendarDesignTokens.DotPurple
                                }
                            CalendarDot(color)
                        }

                    val dayOfWeekLetter =
                        when (date.dayOfWeek) {
                            DayOfWeek.MONDAY -> "M"
                            DayOfWeek.TUESDAY -> "T"
                            DayOfWeek.WEDNESDAY -> "W"
                            DayOfWeek.THURSDAY -> "T"
                            DayOfWeek.FRIDAY -> "F"
                            DayOfWeek.SATURDAY -> "S"
                            DayOfWeek.SUNDAY -> "S"
                        }

                    val dayShortName = date.format(EEEFormatter)
                    val fullDateLabel = date.format(FullDateFormatter)
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val isOutOfMonth = YearMonth.from(date) != displayedMonth
                    val state =
                        when {
                            isSelected -> CalendarDayState.SELECTED
                            isToday -> CalendarDayState.TODAY
                            isOutOfMonth -> CalendarDayState.OUT_OF_MONTH
                            else -> CalendarDayState.NORMAL
                        }

                    CalendarWeekDayUiModel(
                        date = date,
                        dayOfWeekLetter = dayOfWeekLetter,
                        dayNumber = date.dayOfMonth,
                        dayShortName = dayShortName,
                        fullDateLabel = fullDateLabel,
                        state = state,
                        dots = dots,
                        events = dayEvents,
                        isSelected = isSelected,
                        isToday = isToday,
                    )
                }

            // 3. Extract agenda items for the selected date
            val selectedDateNotes =
                activeNotes
                    .filter { matchesDate(it, selectedDate) }
                    .distinctBy { it.note.id }
            val birthdayNotesOnDate = selectedDateNotes.filter { isBirthday(it) }
            val standardNotesOnDate = selectedDateNotes.filter { !isBirthday(it) }

            val (taskNotes, eventNotes) =
                standardNotesOnDate.partition { noteWithItems ->
                    noteWithItems.note.kind == NoteKind.LIST || noteWithItems.note.checklistText.isNotBlank()
                }

            val agendaEvents =
                eventNotes
                    .sortedBy { it.note.reminderAt ?: it.note.createdAt }
                    .map { noteWithItems ->
                        mapToEventUiModel(noteWithItems, selectedDate, isTask = false)
                    }

            val agendaTasks =
                taskNotes
                    .sortedBy { it.note.reminderAt ?: it.note.createdAt }
                    .map { noteWithItems ->
                        mapToEventUiModel(noteWithItems, selectedDate, isTask = true)
                    }

            // 4. Extract genuine birthdays for this date without fabricating records
            val birthdayModels =
                birthdayNotesOnDate
                    .sortedBy { it.note.reminderAt ?: it.note.createdAt }
                    .map { noteWithItems ->
                        val subtitle = formatBirthdaySubtitle(noteWithItems)
                        CalendarBirthdayUiModel(
                            id = noteWithItems.note.id,
                            name = noteWithItems.note.title.ifBlank { "Birthday" },
                            subtitle = subtitle,
                            iconName = "cake",
                        )
                    }

            // 5. Extract daily timeline items for Day View
            val (dayTimelineItems, dayAllDayItems) = extractDayTimelineItems(selectedDate, activeNotes)

            return CalendarUiState(
                selectedDate = selectedDate,
                displayedMonth = displayedMonth,
                today = today,
                currentWeekStart = currentWeekStart,
                weekRangeTitle = weekRangeTitle,
                weekYearLabel = weekYearLabel,
                viewMode = viewMode,
                monthDays = monthDays,
                weekDays = weekDays,
                selectedDayEvents = agendaEvents,
                selectedDayTasks = agendaTasks,
                selectedDayBirthdays = birthdayModels,
                dayTimelineItems = dayTimelineItems,
                dayAllDayItems = dayAllDayItems,
                weekTotalEventsCount = totalWeekEvents,
                isLoading = false,
                errorMessage = errorMessage,
            )
        }

        private fun extractDayTimelineItems(
            selectedDate: LocalDate,
            activeNotes: List<NoteWithItems>,
        ): Pair<List<CalendarTimelineItemUiModel>, List<CalendarTimelineItemUiModel>> {
            val notesOnDate =
                activeNotes
                    .filter { matchesDate(it, selectedDate) && !isBirthday(it) }
                    .distinctBy { it.note.id }

            val allDayItems = mutableListOf<CalendarTimelineItemUiModel>()
            val rawTimedItems = mutableListOf<CalendarTimelineItemUiModel>()

            for (noteWithItems in notesOnDate) {
                val reminders = noteWithItems.note.getActiveReminders()
                val reminderOnDate =
                    reminders.firstOrNull { reminder ->
                        val rDate =
                            Instant
                                .ofEpochMilli(reminder.reminderAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        rDate == selectedDate || isRecurringMatch(reminder, selectedDate)
                    }

                val isAllDay = noteWithItems.note.isAllDay || (reminderOnDate == null && noteWithItems.note.reminderAt == null)
                if (isAllDay) {
                    allDayItems.add(
                        createTimelineItem(
                            noteWithItems = noteWithItems,
                            type = TimelineItemType.ALL_DAY,
                            startMinute = 0,
                            endMinute = 0,
                            selectedDate = selectedDate,
                        ),
                    )
                    continue
                }

                val epochMillis = reminderOnDate?.reminderAt ?: noteWithItems.note.reminderAt ?: 0L
                val reminderTime =
                    Instant
                        .ofEpochMilli(epochMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()

                val startMinute = reminderTime.hour * 60 + reminderTime.minute
                val isTask = noteWithItems.note.kind == NoteKind.LIST || noteWithItems.note.checklistText.isNotBlank()

                if (isTask) {
                    rawTimedItems.add(
                        createTimelineItem(
                            noteWithItems = noteWithItems,
                            type = TimelineItemType.TASK,
                            startMinute = startMinute,
                            endMinute = startMinute,
                            selectedDate = selectedDate,
                        ),
                    )
                } else {
                    val durationMinutes =
                        if (noteWithItems.note.endAt != null && noteWithItems.note.endAt > epochMillis) {
                            val diffMillis = noteWithItems.note.endAt - epochMillis
                            val mins = (diffMillis / (60 * 1000L)).toInt()
                            mins.coerceIn(15, 1440)
                        } else {
                            parseDurationMinutes(noteWithItems) ?: 30
                        }
                    val rawEndMinute = startMinute + durationMinutes
                    val (clampedEnd, isCrossStart) =
                        if (rawEndMinute > 1440) {
                            1440 to true
                        } else {
                            rawEndMinute to false
                        }

                    rawTimedItems.add(
                        createTimelineItem(
                            noteWithItems = noteWithItems,
                            type = TimelineItemType.EVENT,
                            startMinute = startMinute,
                            endMinute = clampedEnd,
                            selectedDate = selectedDate,
                            isCrossMidnightStart = isCrossStart,
                        ),
                    )
                }
            }

            // Check for cross-midnight events from yesterday
            val yesterday = selectedDate.minusDays(1)
            val yesterdayNotes =
                activeNotes
                    .filter { matchesDate(it, yesterday) && !isBirthday(it) }
                    .distinctBy { it.note.id }

            for (noteWithItems in yesterdayNotes) {
                if (noteWithItems.note.kind == NoteKind.LIST) continue
                if (noteWithItems.note.endAt != null) {
                    val endInstant = Instant.ofEpochMilli(noteWithItems.note.endAt).atZone(ZoneId.systemDefault())
                    val endDate = endInstant.toLocalDate()
                    if (endDate >= selectedDate) {
                        val carryOver = if (endDate > selectedDate) 1440 else (endInstant.hour * 60 + endInstant.minute).coerceIn(1, 1440)
                        rawTimedItems.add(
                            createTimelineItem(
                                noteWithItems = noteWithItems,
                                type = TimelineItemType.EVENT,
                                startMinute = 0,
                                endMinute = carryOver,
                                selectedDate = selectedDate,
                                isCrossMidnightEnd = true,
                            ),
                        )
                        continue
                    }
                }
                val reminders = noteWithItems.note.getActiveReminders()
                val reminderYesterday =
                    reminders.firstOrNull { reminder ->
                        val rDate =
                            Instant
                                .ofEpochMilli(reminder.reminderAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        rDate == yesterday || isRecurringMatch(reminder, yesterday)
                    } ?: continue

                val reminderTime =
                    Instant
                        .ofEpochMilli(reminderYesterday.reminderAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()

                val startMinute = reminderTime.hour * 60 + reminderTime.minute
                val durationMinutes = parseDurationMinutes(noteWithItems) ?: 30
                val totalEnd = startMinute + durationMinutes
                if (totalEnd > 1440) {
                    val carryOver = minOf(1440, totalEnd - 1440)
                    rawTimedItems.add(
                        createTimelineItem(
                            noteWithItems = noteWithItems,
                            type = TimelineItemType.EVENT,
                            startMinute = 0,
                            endMinute = carryOver,
                            selectedDate = selectedDate,
                            isCrossMidnightEnd = true,
                        ),
                    )
                }
            }

            val timedWithColumns = layoutOverlappingColumns(rawTimedItems)
            return timedWithColumns to allDayItems
        }

        private fun parseDurationMinutes(noteWithItems: NoteWithItems): Int? {
            val fullText = "${noteWithItems.note.title} ${noteWithItems.note.body}"

            // Check for time ranges like "9:00-9:30 AM" or "2:00–3:00 PM" or "14:00-15:00"
            val rangeRegex =
                Regex(
                    """(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*[-–—]\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""",
                    RegexOption.IGNORE_CASE,
                )
            rangeRegex.find(fullText)?.let { match ->
                val (h1Str, m1Str, ampm1, h2Str, m2Str, ampm2) = match.destructured
                var h1 = h1Str.toIntOrNull() ?: return@let
                val m1 = m1Str.toIntOrNull() ?: 0
                var h2 = h2Str.toIntOrNull() ?: return@let
                val m2 = m2Str.toIntOrNull() ?: 0

                val p2 = ampm2.lowercase(Locale.getDefault())
                val p1 = if (ampm1.isNotBlank()) ampm1.lowercase(Locale.getDefault()) else p2

                if (p1 == "pm" && h1 < 12) h1 += 12
                if (p1 == "am" && h1 == 12) h1 = 0
                if (p2 == "pm" && h2 < 12) h2 += 12
                if (p2 == "am" && h2 == 12) h2 = 0

                val startMin = h1 * 60 + m1
                var endMin = h2 * 60 + m2
                if (endMin < startMin) endMin += 1440
                val diff = endMin - startMin
                if (diff in 5..1440) return diff
            }

            // Check for duration specifications like "30 min", "1 hour", "45m"
            val minRegex = Regex("""(\d+)\s*(?:min|mins|minutes|m)\b""", RegexOption.IGNORE_CASE)
            minRegex.find(fullText)?.let { match ->
                val mins = match.groupValues[1].toIntOrNull()
                if (mins != null && mins in 5..1440) return mins
            }

            val hourRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:hour|hours|hr|hrs|h)\b""", RegexOption.IGNORE_CASE)
            hourRegex.find(fullText)?.let { match ->
                val hours = match.groupValues[1].toDoubleOrNull()
                if (hours != null) {
                    val mins = (hours * 60).toInt()
                    if (mins in 5..1440) return mins
                }
            }

            return null
        }

        private fun layoutOverlappingColumns(
            items: List<CalendarTimelineItemUiModel>,
        ): List<CalendarTimelineItemUiModel> {
            if (items.isEmpty()) return emptyList()

            val sorted =
                items.sortedWith(
                    compareBy<CalendarTimelineItemUiModel> { it.startMinuteOfDay }
                        .thenByDescending { it.endMinuteOfDay - it.startMinuteOfDay },
                )

            fun effectiveEnd(item: CalendarTimelineItemUiModel) = maxOf(item.endMinuteOfDay, item.startMinuteOfDay + 20)

            val clusters = mutableListOf<MutableList<CalendarTimelineItemUiModel>>()
            var currentCluster = mutableListOf<CalendarTimelineItemUiModel>()
            var clusterEnd = -1

            for (item in sorted) {
                val itemStart = item.startMinuteOfDay
                val itemEnd = effectiveEnd(item)

                if (currentCluster.isEmpty()) {
                    currentCluster.add(item)
                    clusterEnd = itemEnd
                } else if (itemStart < clusterEnd) {
                    currentCluster.add(item)
                    clusterEnd = maxOf(clusterEnd, itemEnd)
                } else {
                    clusters.add(currentCluster)
                    currentCluster = mutableListOf(item)
                    clusterEnd = itemEnd
                }
            }
            if (currentCluster.isNotEmpty()) {
                clusters.add(currentCluster)
            }

            val result = mutableListOf<CalendarTimelineItemUiModel>()
            for (cluster in clusters) {
                val colEndTimes = mutableListOf<Int>()
                val clusterAssigned = mutableListOf<CalendarTimelineItemUiModel>()

                for (item in cluster) {
                    val itemStart = item.startMinuteOfDay
                    val itemEnd = effectiveEnd(item)

                    var assignedCol = -1
                    for (col in colEndTimes.indices) {
                        if (colEndTimes[col] <= itemStart) {
                            assignedCol = col
                            colEndTimes[col] = itemEnd
                            break
                        }
                    }
                    if (assignedCol == -1) {
                        assignedCol = colEndTimes.size
                        colEndTimes.add(itemEnd)
                    }

                    clusterAssigned.add(item.copy(colIndex = assignedCol))
                }

                val totalCols = maxOf(1, colEndTimes.size)
                result.addAll(clusterAssigned.map { it.copy(totalCols = totalCols) })
            }

            return result
        }

        private fun createTimelineItem(
            noteWithItems: NoteWithItems,
            type: TimelineItemType,
            startMinute: Int,
            endMinute: Int,
            selectedDate: LocalDate,
            isCrossMidnightStart: Boolean = false,
            isCrossMidnightEnd: Boolean = false,
        ): CalendarTimelineItemUiModel {
            val title =
                noteWithItems.note.title.ifBlank {
                    noteWithItems.note.body
                        .lineSequence()
                        .firstOrNull()
                        ?.take(30) ?: "Untitled note"
                }
            val iconName = resolveIconName(noteWithItems)
            val isList = noteWithItems.note.kind == NoteKind.LIST
            val isWork = noteWithItems.shoulder?.name?.contains("work", ignoreCase = true) == true
            val isHighImportance =
                noteWithItems.note.importance == Importance.HIGH ||
                    noteWithItems.note.importance == Importance.CRITICAL

            val (accentColor, containerBg) =
                when {
                    isHighImportance -> CalendarDesignTokens.DotOrange to Color(0xFFFFF3E0)
                    !NoteColors.isNone(noteWithItems.note.colorIndex) -> {
                        val accent = NoteColors.at(noteWithItems.note.colorIndex)
                        accent.light to accent.light.copy(alpha = 0.12f)
                    }
                    !noteWithItems.shoulder?.colorHex.isNullOrBlank() -> {
                        val parsed =
                            runCatching {
                                val hex = noteWithItems.shoulder!!.colorHex!!
                                val fullHex = if (hex.startsWith("#")) hex else "#$hex"
                                Color(android.graphics.Color.parseColor(fullHex))
                            }.getOrNull()
                        if (parsed != null) {
                            parsed to parsed.copy(alpha = 0.12f)
                        } else {
                            CalendarDesignTokens.PrimaryPurple to CalendarDesignTokens.CategoryPurpleBg
                        }
                    }
                    isList -> CalendarDesignTokens.DotGreen to CalendarDesignTokens.CategoryGreenBg
                    isWork -> CalendarDesignTokens.DotBlue to CalendarDesignTokens.CategoryBlueBg
                    else -> CalendarDesignTokens.PrimaryPurple to CalendarDesignTokens.CategoryPurpleBg
                }

            val timeRangeDisplay =
                when (type) {
                    TimelineItemType.ALL_DAY -> "All day"
                    TimelineItemType.TASK -> {
                        val hour = startMinute / 60
                        val min = startMinute % 60
                        val localTime = LocalTime.of(hour, min)
                        localTime.format(Time12HourMinuteAmPmFormatter)
                    }
                    TimelineItemType.EVENT -> {
                        val startH = startMinute / 60
                        val startM = startMinute % 60
                        val startTime = LocalTime.of(startH, startM)
                        val startStr = startTime.format(Time12HourMinuteFormatter)

                        if (isCrossMidnightStart) {
                            val ampm = startTime.format(AmPmFormatter)
                            "$startStr $ampm – 12:00 AM (+1)"
                        } else if (isCrossMidnightEnd) {
                            val endH = endMinute / 60
                            val endM = endMinute % 60
                            val endTime = LocalTime.of(endH, endM)
                            val endStr = endTime.format(Time12HourMinuteAmPmFormatter)
                            "12:00 AM – $endStr"
                        } else {
                            val endH = endMinute / 60
                            val endM = endMinute % 60
                            val endTime = LocalTime.of(endH, endM)
                            val endStr = endTime.format(Time12HourMinuteAmPmFormatter)
                            "$startStr–$endStr"
                        }
                    }
                }

            val shoulder = noteWithItems.shoulder?.name
            val subtitle =
                if (!shoulder.isNullOrBlank()) {
                    "$timeRangeDisplay · $shoulder"
                } else {
                    val firstLine =
                        noteWithItems.note.body
                            .lineSequence()
                            .firstOrNull()
                            ?.take(30)
                    if (!firstLine.isNullOrBlank()) "$timeRangeDisplay · $firstLine" else timeRangeDisplay
                }

            return CalendarTimelineItemUiModel(
                id = noteWithItems.note.id,
                title = title,
                subtitle = subtitle,
                timeRangeDisplay = timeRangeDisplay,
                type = type,
                startMinuteOfDay = startMinute,
                endMinuteOfDay = endMinute,
                iconName = iconName,
                accentColor = accentColor,
                containerBgColor = containerBg,
                isDone = noteWithItems.note.completedAt != null,
                isCrossMidnightStart = isCrossMidnightStart,
                isCrossMidnightEnd = isCrossMidnightEnd,
                colIndex = 0,
                totalCols = 1,
            )
        }

        private fun mapToEventUiModel(
            noteWithItems: NoteWithItems,
            date: LocalDate,
            isTask: Boolean = false,
        ): CalendarEventUiModel {
            val timeDisplay = formatTimeDisplay(noteWithItems, date)
            val subtitle = formatEventSubtitle(noteWithItems, timeDisplay)
            val iconName = resolveIconName(noteWithItems)
            val isList = isTask || noteWithItems.note.kind == NoteKind.LIST || noteWithItems.note.checklistText.isNotBlank()
            val isWork = noteWithItems.shoulder?.name?.contains("work", ignoreCase = true) == true

            val (iconBg, iconTint) =
                when {
                    !NoteColors.isNone(noteWithItems.note.colorIndex) -> {
                        val accent = NoteColors.at(noteWithItems.note.colorIndex)
                        accent.light.copy(alpha = 0.15f) to accent.light
                    }
                    !noteWithItems.shoulder?.colorHex.isNullOrBlank() -> {
                        val parsed =
                            runCatching {
                                val hex = noteWithItems.shoulder!!.colorHex!!
                                val fullHex = if (hex.startsWith("#")) hex else "#$hex"
                                Color(android.graphics.Color.parseColor(fullHex))
                            }.getOrNull()
                        if (parsed != null) {
                            parsed.copy(alpha = 0.15f) to parsed
                        } else {
                            CalendarDesignTokens.CategoryPurpleBg to CalendarDesignTokens.CategoryPurpleIcon
                        }
                    }
                    isList -> CalendarDesignTokens.CategoryGreenBg to CalendarDesignTokens.CategoryGreenIcon
                    isWork -> CalendarDesignTokens.CategoryBlueBg to CalendarDesignTokens.CategoryBlueIcon
                    else -> CalendarDesignTokens.CategoryPurpleBg to CalendarDesignTokens.CategoryPurpleIcon
                }

            return CalendarEventUiModel(
                id = noteWithItems.note.id,
                title =
                    noteWithItems.note.title.ifBlank {
                        noteWithItems.note.body
                            .lineSequence()
                            .firstOrNull()
                            ?.take(30) ?: "Untitled note"
                    },
                subtitle = subtitle,
                timeDisplay = timeDisplay,
                iconName = iconName,
                iconBgColor = iconBg,
                iconTint = iconTint,
                isDone = noteWithItems.note.completedAt != null,
                isTask = isList,
            )
        }

        private fun matchesDate(
            note: NoteWithItems,
            date: LocalDate,
        ): Boolean {
            if (note.note.endAt != null) {
                val startMillis = note.note.reminderAt ?: note.note.createdAt
                val startDate =
                    Instant
                        .ofEpochMilli(startMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                val endDate =
                    Instant
                        .ofEpochMilli(note.note.endAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) return true
            }

            val reminders = note.note.getActiveReminders()
            if (reminders.isNotEmpty()) {
                for (reminder in reminders) {
                    val reminderDate =
                        Instant
                            .ofEpochMilli(reminder.reminderAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()

                    if (reminderDate == date) return true
                    if (isRecurringMatch(reminder, date)) return true
                }
            }

            if (isBirthday(note)) {
                val primaryAt = note.note.reminderAt ?: note.note.createdAt
                val bdayDate = Instant.ofEpochMilli(primaryAt).atZone(ZoneId.systemDefault()).toLocalDate()
                if (bdayDate.month == date.month && bdayDate.dayOfMonth == date.dayOfMonth) return true
            }

            if (note.note.reminderAt == null && note.note.reminders.isEmpty()) {
                val createdDate =
                    Instant
                        .ofEpochMilli(note.note.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                if (createdDate == date) return true
            }

            return false
        }

        private fun isRecurringMatch(
            reminder: NoteReminder,
            date: LocalDate,
        ): Boolean {
            val reminderDate =
                Instant
                    .ofEpochMilli(reminder.reminderAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            val recurrence = reminder.recurrence ?: return false
            if (date.isBefore(reminderDate)) return false
            if (recurrence.endDate != null) {
                val dateStartMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (dateStartMillis > recurrence.endDate) return false
            }

            val interval = maxOf(1, recurrence.interval)
            return when (recurrence.unit) {
                RecurrenceUnit.HOUR, RecurrenceUnit.DAY -> {
                    val daysDiff = ChronoUnit.DAYS.between(reminderDate, date)
                    daysDiff % interval == 0L
                }
                RecurrenceUnit.WEEK -> {
                    val weeksDiff = ChronoUnit.WEEKS.between(reminderDate, date)
                    if (recurrence.daysOfWeek.isEmpty()) {
                        date.dayOfWeek == reminderDate.dayOfWeek && weeksDiff % interval == 0L
                    } else {
                        val calWeekday =
                            when (date.dayOfWeek) {
                                DayOfWeek.SUNDAY -> Calendar.SUNDAY
                                else -> date.dayOfWeek.value + 1
                            }
                        calWeekday in recurrence.daysOfWeek && weeksDiff % interval == 0L
                    }
                }
                RecurrenceUnit.MONTH -> {
                    val monthsDiff = ChronoUnit.MONTHS.between(YearMonth.from(reminderDate), YearMonth.from(date))
                    if (monthsDiff % interval == 0L) {
                        when (val mode = recurrence.monthlyMode) {
                            null -> date.dayOfMonth == minOf(reminderDate.dayOfMonth, date.lengthOfMonth())
                            is MonthlyMode.ByDayOfMonth -> date.dayOfMonth == minOf(mode.day, date.lengthOfMonth())
                            is MonthlyMode.ByNthWeekday -> {
                                val calWeekday =
                                    when (date.dayOfWeek) {
                                        DayOfWeek.SUNDAY -> Calendar.SUNDAY
                                        else -> date.dayOfWeek.value + 1
                                    }
                                calWeekday == mode.weekday
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                RecurrenceUnit.YEAR -> {
                    if (date.month == reminderDate.month && date.dayOfMonth == reminderDate.dayOfMonth) {
                        val yearsDiff = date.year - reminderDate.year
                        yearsDiff >= 0 && yearsDiff % interval == 0
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        private fun isBirthday(note: NoteWithItems): Boolean {
            val shoulderName = note.shoulder?.name?.lowercase(Locale.getDefault()) ?: ""
            val title = note.note.title.lowercase(Locale.getDefault())
            val iconKey = note.note.iconKey?.lowercase(Locale.getDefault()) ?: ""
            return shoulderName.contains("birthday") ||
                title.contains("birthday") ||
                iconKey.contains("cake")
        }

        private fun formatTimeDisplay(
            note: NoteWithItems,
            date: LocalDate,
        ): String {
            if (note.note.isAllDay) return "All day"
            val reminderAt = note.note.reminderAt
            return if (reminderAt != null) {
                val dateTime = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault())
                dateTime.format(Time12HourMinuteAmPmFormatter)
            } else {
                "All day"
            }
        }

        private fun formatEventSubtitle(
            note: NoteWithItems,
            timeDisplay: String,
        ): String {
            val shoulder = note.shoulder?.name
            return if (!shoulder.isNullOrBlank()) {
                "$timeDisplay • $shoulder"
            } else {
                val firstLine =
                    note.note.body
                        .lineSequence()
                        .firstOrNull()
                        ?.take(40)
                if (!firstLine.isNullOrBlank()) "$timeDisplay • $firstLine" else timeDisplay
            }
        }

        private fun formatBirthdaySubtitle(note: NoteWithItems): String {
            val reminderAt = note.note.reminderAt ?: note.note.createdAt
            val dateTime = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault())
            val datePart = dateTime.format(DateTimeFormatter.ofPattern("MMMM d, EEEE", Locale.getDefault()))
            val timePart = dateTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
            return "$datePart • $timePart"
        }

        private fun resolveIconName(note: NoteWithItems): String {
            note.note.iconKey?.let { return it }
            val titleLower = note.note.title.lowercase(Locale.getDefault())
            return when {
                note.note.kind == NoteKind.LIST -> "checklist"
                titleLower.contains("coffee") || titleLower.contains("cafe") -> "local_cafe"
                titleLower.contains("meet") || titleLower.contains("sync") -> "groups"
                titleLower.contains("call") || titleLower.contains("phone") -> "call"
                titleLower.contains("lunch") || titleLower.contains("dinner") || titleLower.contains("food") -> "restaurant"
                titleLower.contains("flight") || titleLower.contains("travel") -> "flight"
                titleLower.contains("doctor") || titleLower.contains("hospital") -> "local_hospital"
                titleLower.contains("shop") || titleLower.contains("grocery") -> "shopping_cart"
                else -> "edit_note"
            }
        }
    }
