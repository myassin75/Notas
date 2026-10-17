package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
    onOpenNote: (Long) -> Unit = {},
    onCreateEvent: (Long) -> Unit = {},
    onOpenEditorWithPrefill: ((Long) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableShoulders by viewModel.availableShoulders.collectAsStateWithLifecycle()
    val isDark = LocalIsDark.current
    val today = remember { LocalDate.now() }

    val monthTitle = remember(uiState.displayedMonth) {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        uiState.displayedMonth.format(formatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val dayTitle = remember(uiState.selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
        uiState.selectedDate.format(formatter)
    }

    val daySubtitle = remember(uiState.selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("EEEE · yyyy", Locale.getDefault())
        uiState.selectedDate.format(formatter)
    }

    val dayAgendaDatePrefix = remember(uiState.selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        uiState.selectedDate.format(formatter)
    }

    val selectedDateSubtitle = remember(uiState.selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault())
        uiState.selectedDate.format(formatter)
    }

    val screenBg = CalendarDesignTokens.resolveScreenBg(isDark)
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. Top Bar with view dropdown and notifications
        CalendarTopBar(
            title = if (uiState.viewMode == CalendarViewMode.MONTH) {
                monthTitle
            } else {
                stringResource(R.string.main_tab_calendar)
            },
            subtitle = selectedDateSubtitle,
            currentViewMode = uiState.viewMode,
            onViewModeSelected = viewModel::onViewModeSelected,
            onNotificationClick = {},
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (uiState.viewMode) {
            CalendarViewMode.DAY -> {
                // ==================== DAY VIEW ====================

                // 2. Day Navigation Header (< Today >) inside rounded card
                Surface(
                    shape = CalendarDesignTokens.CardShape,
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                ) {
                    CalendarNavigationHeader(
                        title = dayTitle,
                        subtitle = daySubtitle,
                        onPrevious = viewModel::onPreviousDay,
                        onToday = viewModel::onTodayClicked,
                        onNext = viewModel::onNextDay,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Day Agenda Section Header
                val isTodaySelected = uiState.selectedDate == today
                val dayAgendaTitle = if (isTodaySelected) {
                    stringResource(R.string.calendar_todays_agenda)
                } else {
                    val formatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
                    "${uiState.selectedDate.format(formatter)}’s agenda"
                }

                val totalDayItems = uiState.dayTimelineItems.size + uiState.dayAllDayItems.size
                val dayAgendaSubtitle = when {
                    totalDayItems == 1 -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_event_count_single)
                    totalDayItems > 1 -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_events_count, totalDayItems)
                    else -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_no_events)
                }

                AgendaSectionHeader(
                    title = dayAgendaTitle,
                    subtitle = dayAgendaSubtitle,
                    onNewEventClick = {
                        val dateMillis = uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onCreateEvent(dateMillis)
                    },
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Daily Timeline Card
                DayTimelineCard(
                    timelineItems = uiState.dayTimelineItems,
                    allDayItems = uiState.dayAllDayItems,
                    selectedDate = uiState.selectedDate,
                    onItemClick = onOpenNote,
                    onToggleDone = viewModel::toggleEventDone,
                )
            }

            CalendarViewMode.WEEK -> {
                // ==================== WEEK VIEW ====================

                // 2. Week Navigation Header (< Today >) inside week view card
                Surface(
                    shape = CalendarDesignTokens.CardShape,
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 12.dp),
                    ) {
                        CalendarNavigationHeader(
                            title = uiState.weekRangeTitle,
                            subtitle = uiState.weekYearLabel,
                            onPrevious = viewModel::onPreviousWeek,
                            onToday = viewModel::onTodayClicked,
                            onNext = viewModel::onNextWeek,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 7-day compact selector row with tall selected pill
                        WeekDaySelectorRow(
                            weekDays = uiState.weekDays,
                            onDateSelected = viewModel::onDateSelected,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. "This week's agenda" section header
                val weekAgendaSubtitle = if (uiState.weekTotalEventsCount == 1) {
                    "${uiState.weekRangeTitle} • " + stringResource(R.string.calendar_event_count_single)
                } else {
                    "${uiState.weekRangeTitle} • " + stringResource(R.string.calendar_events_count, uiState.weekTotalEventsCount)
                }

                AgendaSectionHeader(
                    title = stringResource(R.string.calendar_weeks_agenda),
                    subtitle = weekAgendaSubtitle,
                    onNewEventClick = {
                        val dateMillis = uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onCreateEvent(dateMillis)
                    },
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Week Agenda Card grouped by day
                WeekAgendaCard(
                    weekDays = uiState.weekDays,
                    onDateSelected = viewModel::onDateSelected,
                    onOpenNote = onOpenNote,
                )
            }

            CalendarViewMode.YEAR -> {
                // ==================== YEAR VIEW ====================

                // 2. Year Navigation Header (< Today >) inside rounded card
                YearNavigationHeaderCard(
                    yearTitle = uiState.yearUiModel.yearTitle,
                    yearSubtitle = uiState.yearUiModel.yearSubtitle,
                    onPrevious = viewModel::onPreviousYear,
                    onToday = viewModel::onTodayClicked,
                    onNext = viewModel::onNextYear,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Year Overview Section Header
                val yearEventsCount = uiState.yearUiModel.totalYearEventsCount
                val yearOverviewSubtitle = if (yearEventsCount == 1) {
                    stringResource(R.string.calendar_event_count_single)
                } else if (yearEventsCount > 1) {
                    stringResource(R.string.calendar_events_count, yearEventsCount)
                } else {
                    stringResource(R.string.calendar_no_events)
                }

                AgendaSectionHeader(
                    title = "${uiState.yearUiModel.yearTitle} Overview",
                    subtitle = yearOverviewSubtitle,
                    onNewEventClick = {
                        val dateMillis = uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onCreateEvent(dateMillis)
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. 12 Months Responsive Grid
                YearViewContent(
                    yearUiModel = uiState.yearUiModel,
                    onMonthClick = viewModel::onMonthSelectedInYearView,
                    onDateClick = viewModel::onDateSelectedInYearView,
                )
            }

            CalendarViewMode.MONTH -> {
                // ==================== MONTH VIEW ====================

                // 2. Month Navigation Header (< Today >)
                CalendarNavigationHeader(
                    title = monthTitle,
                    onPrevious = viewModel::onPreviousMonth,
                    onToday = viewModel::onTodayClicked,
                    onNext = viewModel::onNextMonth,
                )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Calendar Month Card
            Surface(
                shape = CalendarDesignTokens.CardShape,
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                ) {
                    // Day of week labels row (M T W T F S S)
                    DayOfWeekHeaderRow()

                    Spacer(modifier = Modifier.height(6.dp))

                    // Calendar days grid
                    val rows = uiState.monthDays.chunked(7)
                    rows.forEach { weekDays ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            weekDays.forEach { dayModel ->
                                Box(modifier = Modifier.weight(1f)) {
                                    CalendarDayCell(
                                        day = dayModel,
                                        onClick = viewModel::onDateSelected,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Month View Selected Day Agenda Section Header
            val isTodaySelected = uiState.selectedDate == today
            val agendaTitle = if (isTodaySelected) {
                stringResource(R.string.calendar_todays_agenda)
            } else {
                val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                "${uiState.selectedDate.format(formatter)} Agenda"
            }

            val eventCount = uiState.selectedDayEvents.size
            val agendaSubtitle = if (eventCount == 1) {
                stringResource(R.string.calendar_event_count_single)
            } else if (eventCount > 1) {
                stringResource(R.string.calendar_events_count, eventCount)
            } else {
                stringResource(R.string.calendar_no_events)
            }

            AgendaSectionHeader(
                title = agendaTitle,
                subtitle = agendaSubtitle,
                onNewEventClick = {
                    val dateMillis = uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onCreateEvent(dateMillis)
                },
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 5. Month View Selected Day Event Cards or Empty State
            if (uiState.selectedDayEvents.isEmpty()) {
                CalendarEmptyState(
                    message = stringResource(R.string.calendar_no_events),
                    helperText = "Tap + New event to add one for this date",
                )
            } else {
                uiState.selectedDayEvents.forEach { event ->
                    AgendaEventCard(
                        event = event,
                        onClick = { onOpenNote(event.id) },
                    )
                }
            }
        }
    }

        // Common: Birthdays Section
        if (uiState.selectedDayBirthdays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.calendar_birthdays),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CalendarDesignTokens.resolveTextPrimary(isDark),
                ),
                modifier = Modifier.padding(
                    horizontal = CalendarDesignTokens.ScreenHorizontalPadding,
                    vertical = 4.dp,
                ),
            )
            uiState.selectedDayBirthdays.forEach { birthday ->
                BirthdayCard(
                    birthday = birthday,
                    onClick = { onOpenNote(birthday.id) },
                )
            }
        }

        // Bottom navigation padding so content scrolls above navigation bar
        Spacer(modifier = Modifier.height(96.dp))
    }
}

