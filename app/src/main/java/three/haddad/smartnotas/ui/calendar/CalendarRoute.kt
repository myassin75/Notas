package three.haddad.smartnotas.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
    onOpenNote: (Long) -> Unit = {},
    onOpenItemDetails: (id: Long, isTask: Boolean) -> Unit = { id, _ -> onOpenNote(id) },
    onCreateEvent: (LocalDate) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDark.current

    val monthTitle =
        remember(uiState.displayedMonth) {
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
            uiState.displayedMonth
                .format(formatter)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

    val isTodaySelected = uiState.selectedDate == uiState.today

    val dayHeaderTitle =
        remember(uiState.selectedDate) {
            val formatter = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
            uiState.selectedDate.format(formatter)
        }

    val dayHeaderSubtitle =
        remember(uiState.selectedDate) {
            val formatter = DateTimeFormatter.ofPattern("EEEE · yyyy", Locale.getDefault())
            uiState.selectedDate.format(formatter)
        }

    val dayAgendaDatePrefix =
        remember(uiState.selectedDate) {
            val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            uiState.selectedDate.format(formatter)
        }

    val selectedDateSubtitle =
        remember(uiState.selectedDate) {
            val formatter = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault())
            uiState.selectedDate.format(formatter)
        }

    val screenBg = CalendarDesignTokens.resolveScreenBg(isDark)
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(screenBg)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        // 1. Top Bar with view dropdown and notifications (pinned)
        CalendarTopBar(
            title = stringResource(R.string.main_tab_calendar),
            subtitle = selectedDateSubtitle,
            currentViewMode = uiState.viewMode,
            onViewModeSelected = viewModel::onViewModeSelected,
            onNotificationClick = {},
        )

        Spacer(modifier = Modifier.height(8.dp))

        val scrollState = key(uiState.viewMode) { rememberScrollState() }

        // Scrollable calendar content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
        ) {
            AnimatedContent(
                targetState = uiState.viewMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "CalendarViewTransition",
            ) { currentViewMode ->
                when (currentViewMode) {
                    CalendarViewMode.DAY -> {
                        // ==================== DAY VIEW ====================
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 2. Day Navigation Header (< Today >) inside rounded card
                            Surface(
                                shape = CalendarDesignTokens.CardShape,
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                            ) {
                                CalendarNavigationHeader(
                                    title = dayHeaderTitle,
                                    subtitle = dayHeaderSubtitle,
                                    onPrevious = viewModel::onPreviousDay,
                                    onToday = viewModel::onTodayClicked,
                                    onNext = viewModel::onNextDay,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 3. Day Agenda Section Header
                            val isTodaySelected = uiState.selectedDate == uiState.today
                            val dayAgendaTitle =
                                if (isTodaySelected) {
                                    stringResource(R.string.calendar_todays_agenda)
                                } else {
                                    stringResource(R.string.calendar_date_agenda, dayAgendaDatePrefix)
                                }

                            val totalDayItems = uiState.dayTimelineItems.size + uiState.dayAllDayItems.size
                            val dayAgendaSubtitle =
                                when {
                                    totalDayItems == 1 -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_event_count_single)
                                    totalDayItems > 1 -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_events_count, totalDayItems)
                                    else -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_no_events_or_tasks)
                                }

                            AgendaSectionHeader(
                                title = dayAgendaTitle,
                                subtitle = dayAgendaSubtitle,
                                onNewEventClick = { onCreateEvent(uiState.selectedDate) },
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 4. Daily Timeline Card
                            DayTimelineCard(
                                timelineItems = uiState.dayTimelineItems,
                                allDayItems = uiState.dayAllDayItems,
                                selectedDate = uiState.selectedDate,
                                onOpenItemDetails = onOpenItemDetails,
                                onToggleDone = viewModel::toggleEventDone,
                            )
                        }
                    }

                CalendarViewMode.WEEK -> {
                    // ==================== WEEK VIEW ====================
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 2. Week Navigation Header (< Today >) inside week view card
                        Surface(
                            shape = CalendarDesignTokens.CardShape,
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                        ) {
                            Column(
                                modifier =
                                    Modifier
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
                        val weekAgendaSubtitle =
                            if (uiState.weekTotalEventsCount == 1) {
                                "${uiState.weekRangeTitle} • " + stringResource(R.string.calendar_event_count_single)
                            } else {
                                "${uiState.weekRangeTitle} • " + stringResource(R.string.calendar_events_count, uiState.weekTotalEventsCount)
                            }

                        AgendaSectionHeader(
                            title = stringResource(R.string.calendar_weeks_agenda),
                            subtitle = weekAgendaSubtitle,
                            onNewEventClick = { onCreateEvent(uiState.selectedDate) },
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 4. Week Agenda Card grouped by day
                        WeekAgendaCard(
                            weekDays = uiState.weekDays,
                            onDateSelected = viewModel::onDateSelected,
                            onOpenItemDetails = onOpenItemDetails,
                        )
                    }
                }

                CalendarViewMode.YEAR -> {
                    // ==================== YEAR VIEW ====================
                    CalendarYearView(
                        displayedYear = uiState.displayedMonth.year,
                        selectedDate = uiState.selectedDate,
                        today = uiState.today,
                        onPreviousYear = viewModel::onPreviousYear,
                        onNextYear = viewModel::onNextYear,
                        onMonthSelected = viewModel::onMonthSelected,
                        onDateSelected = { date ->
                            viewModel.onDateSelected(date)
                            viewModel.onViewModeSelected(CalendarViewMode.MONTH)
                        },
                    )
                }

                CalendarViewMode.MONTH -> {
                    // ==================== MONTH VIEW ====================
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 2. Calendar Month Card enclosing navigation header, weekday row, and grid
                        Surface(
                        shape = CalendarDesignTokens.CardShape,
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            // Month Navigation Header inside the card (< Today >)
                            CalendarNavigationHeader(
                                title = monthTitle,
                                onPrevious = viewModel::onPreviousMonth,
                                onToday = viewModel::onTodayClicked,
                                onNext = viewModel::onNextMonth,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Day of week labels row (M T W T F S S)
                            DayOfWeekHeaderRow()

                            Spacer(modifier = Modifier.height(4.dp))

                            // Calendar days grid
                            val rows = uiState.monthDays.chunked(7)
                            rows.forEach { weekDays ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    weekDays.forEach { dayModel ->
                                        CalendarDayCell(
                                            day = dayModel,
                                            onClick = viewModel::onDateSelected,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. Month View Selected Day Agenda Section Header & Content
                    val errorMessage = uiState.errorMessage
                    val isLoaded = !uiState.isLoading && errorMessage == null
                    val isAgendaEmpty = isLoaded && uiState.selectedDayEvents.isEmpty() && uiState.selectedDayTasks.isEmpty()
                    val isTodaySelected = uiState.selectedDate == uiState.today

                    val emptyStateDateSubtitle =
                        remember(uiState.selectedDate) {
                            val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
                            uiState.selectedDate.format(formatter)
                        }

                    if (isAgendaEmpty) {
                        // When agenda is empty: Display "Your agenda" heading and formatted date subtitle,
                        // and hide the header New event button to enforce exactly one button inside the empty state card.
                        AgendaSectionHeader(
                            title = stringResource(R.string.calendar_your_agenda),
                            subtitle = emptyStateDateSubtitle,
                            showNewEventButton = false,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        CalendarEmptyState(
                            selectedDate = uiState.selectedDate,
                            onCreateEventClick = { onCreateEvent(uiState.selectedDate) },
                        )
                    } else if (uiState.isLoading) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = CalendarDesignTokens.PrimaryPurple,
                            )
                        }
                    } else if (errorMessage != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = errorMessage,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = CalendarDesignTokens.resolveTextSecondary(isDark),
                                    ),
                            )
                        }
                    } else {
                        val agendaTitle =
                            if (isTodaySelected) {
                                stringResource(R.string.calendar_todays_agenda)
                            } else {
                                stringResource(R.string.calendar_date_agenda, dayAgendaDatePrefix)
                            }

                        val eventCount = uiState.selectedDayEvents.size
                        val taskCount = uiState.selectedDayTasks.size
                        val totalItems = eventCount + taskCount
                        val agendaSubtitle =
                            when {
                                totalItems == 0 -> "$dayAgendaDatePrefix · " + stringResource(R.string.calendar_no_events_or_tasks)
                                eventCount > 0 && taskCount > 0 -> {
                                    val evText = if (eventCount == 1) stringResource(R.string.calendar_event_count_single) else stringResource(R.string.calendar_events_count, eventCount)
                                    val taskText = if (taskCount == 1) stringResource(R.string.calendar_task_count_single) else stringResource(R.string.calendar_tasks_count, taskCount)
                                    "$dayAgendaDatePrefix · $evText, $taskText"
                                }
                                eventCount > 0 -> {
                                    val evText = if (eventCount == 1) stringResource(R.string.calendar_event_count_single) else stringResource(R.string.calendar_events_count, eventCount)
                                    "$dayAgendaDatePrefix · $evText"
                                }
                                else -> {
                                    val taskText = if (taskCount == 1) stringResource(R.string.calendar_task_count_single) else stringResource(R.string.calendar_tasks_count, taskCount)
                                    "$dayAgendaDatePrefix · $taskText"
                                }
                            }

                        AgendaSectionHeader(
                            title = agendaTitle,
                            subtitle = agendaSubtitle,
                            showNewEventButton = true,
                            onNewEventClick = { onCreateEvent(uiState.selectedDate) },
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        if (uiState.selectedDayEvents.isNotEmpty()) {
                            AgendaCategoryHeader(
                                title = stringResource(R.string.calendar_events),
                                iconName = "calendar_month",
                                count = uiState.selectedDayEvents.size,
                                iconTint = CalendarDesignTokens.PrimaryPurple,
                            )
                            uiState.selectedDayEvents.forEach { event ->
                                key(event.id) {
                                    AgendaEventCard(
                                        event = event,
                                        onClick = { onOpenItemDetails(event.id, false) },
                                    )
                                }
                            }
                        }

                        if (uiState.selectedDayTasks.isNotEmpty()) {
                            if (uiState.selectedDayEvents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            AgendaCategoryHeader(
                                title = stringResource(R.string.calendar_tasks),
                                iconName = "task_alt",
                                count = uiState.selectedDayTasks.size,
                                iconTint = CalendarDesignTokens.CategoryGreenIcon,
                                badgeBgColor = if (isDark) Color(0xFF1B3828) else CalendarDesignTokens.CategoryGreenBg,
                                badgeTextColor = if (isDark) Color(0xFF81C784) else CalendarDesignTokens.CategoryGreenIcon,
                            )
                            uiState.selectedDayTasks.forEach { task ->
                                key(task.id) {
                                    AgendaEventCard(
                                        event = task,
                                        onClick = { onOpenItemDetails(task.id, true) },
                                        onToggleDone = viewModel::toggleEventDone,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

            // Common: Birthdays Section (for Day, Week, and Month views)
            if (uiState.viewMode != CalendarViewMode.YEAR && uiState.selectedDayBirthdays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                AgendaCategoryHeader(
                    title = stringResource(R.string.calendar_birthdays),
                    iconName = "cake",
                    count = uiState.selectedDayBirthdays.size,
                    iconTint = CalendarDesignTokens.BirthdayIconTint,
                    badgeBgColor = if (isDark) Color(0xFF3E1C28) else CalendarDesignTokens.BirthdayBoxBg,
                    badgeTextColor = if (isDark) Color(0xFFF48FB1) else CalendarDesignTokens.BirthdayIconTint,
                )
                uiState.selectedDayBirthdays.forEach { birthday ->
                    key(birthday.id) {
                        BirthdayCard(
                            birthday = birthday,
                            onClick = { onOpenItemDetails(birthday.id, false) },
                        )
                    }
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
