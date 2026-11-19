package haddad.three.notas.ui.calendar

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import haddad.three.notas.R
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.LocalReducedMotion
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
    onOpenNote: (Long) -> Unit = {},
    onOpenItemDetails: (id: Long, isTask: Boolean) -> Unit = { id, _ -> onOpenNote(id) },
    onCreateEvent: (LocalDate) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
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

    var lastClickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var lastClickTimestamp by remember { mutableLongStateOf(0L) }

    val handleDateSelection: (LocalDate) -> Unit = { clickedDate ->
        val now = SystemClock.uptimeMillis()
        val isSecondClick =
            (clickedDate == uiState.selectedDate) ||
                (clickedDate == lastClickedDate && (now - lastClickTimestamp) < 800L)

        if (isSecondClick) {
            onCreateEvent(clickedDate)
            lastClickedDate = null
            lastClickTimestamp = 0L
        } else {
            lastClickedDate = clickedDate
            lastClickTimestamp = now
            viewModel.onDateSelected(clickedDate)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(screenBg)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        // 1. Top Bar with view dropdown (pinned)
        CalendarTopBar(
            title = stringResource(R.string.main_tab_calendar),
            subtitle = selectedDateSubtitle,
            currentViewMode = uiState.viewMode,
            onViewModeSelected = viewModel::onViewModeSelected,
            onSearchClick = onNavigateToSearch,
        )

        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 720.dp
            val containerMaxHeight = maxHeight
            val shouldScrollOuter = !(isWideScreen && uiState.viewMode == CalendarViewMode.MONTH && containerMaxHeight >= 500.dp)
            val scrollState = key(uiState.viewMode) { rememberScrollState() }

            // Scrollable calendar content
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (shouldScrollOuter) Modifier.verticalScroll(scrollState) else Modifier),
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
                            val reducedMotion = LocalReducedMotion.current
                            val isDayViewOnToday = uiState.selectedDate == uiState.today
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
                                        showTodayButton = true,
                                        onPrevious = viewModel::onPreviousDay,
                                        onToday = viewModel::onTodayClicked,
                                        onNext = viewModel::onNextDay,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // 3. Day Agenda Section Header
                                val isTodaySelected = isDayViewOnToday
                                val dayAgendaTitle =
                                    if (isTodaySelected) {
                                        stringResource(R.string.calendar_todays_agenda)
                                    } else {
                                        stringResource(R.string.calendar_date_agenda, dayAgendaDatePrefix)
                                    }

                                val dayAgendaSubtitle = dayAgendaDatePrefix

                                AgendaSectionHeader(
                                    title = dayAgendaTitle,
                                    subtitle = dayAgendaSubtitle,
                                    onNewEventClick = { onCreateEvent(uiState.selectedDate) },
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // 4. Daily Timeline Card with directional day transition animation
                                AnimatedContent(
                                    targetState = uiState.selectedDate,
                                    transitionSpec = {
                                        if (reducedMotion) {
                                            fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                                        } else {
                                            val direction = if (targetState.isAfter(initialState)) 1 else -1
                                            val slideDistanceFactor = 0.35f
                                            val enterSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
                                            val enterSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
                                            val exitSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
                                            val exitSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)

                                            (
                                                slideInHorizontally(
                                                    animationSpec = enterSlideSpec,
                                                    initialOffsetX = { fullWidth -> (direction * fullWidth * slideDistanceFactor).toInt() },
                                                ) + fadeIn(animationSpec = enterSpec)
                                            ).togetherWith(
                                                slideOutHorizontally(
                                                    animationSpec = exitSlideSpec,
                                                    targetOffsetX = { fullWidth -> (-direction * fullWidth * slideDistanceFactor).toInt() },
                                                ) + fadeOut(animationSpec = exitSpec),
                                            )
                                        }
                                    },
                                    modifier = Modifier.clipToBounds(),
                                    label = "DayTimelineCardAnimation",
                                ) { targetDate ->
                                    DayTimelineCard(
                                        timelineItems = uiState.dayTimelineItems,
                                        allDayItems = uiState.dayAllDayItems,
                                        selectedDate = targetDate,
                                        onOpenItemDetails = onOpenItemDetails,
                                        onToggleDone = viewModel::toggleEventDone,
                                    )
                                }
                            }
                        }

                        CalendarViewMode.WEEK -> {
                            // ==================== WEEK VIEW ====================
                            val reducedMotion = LocalReducedMotion.current
                            val weekAnimationState =
                                CalendarWeekViewState(
                                    weekStart = uiState.currentWeekStart,
                                    weekDays = uiState.weekDays,
                                )

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
                                        val isWeekViewOnToday = uiState.weekDays.any { it.isToday && it.isSelected }
                                        CalendarNavigationHeader(
                                            title = uiState.weekRangeTitle,
                                            subtitle = uiState.weekYearLabel,
                                            showTodayButton = true,
                                            onPrevious = viewModel::onPreviousWeek,
                                            onToday = viewModel::onTodayClicked,
                                            onNext = viewModel::onNextWeek,
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 7-day compact selector row with tall selected pill
                                        AnimatedContent(
                                            targetState = weekAnimationState,
                                            contentKey = { it.weekStart },
                                            transitionSpec = {
                                                if (reducedMotion) {
                                                    fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                                                } else {
                                                    val direction = if (targetState.weekStart.isAfter(initialState.weekStart)) 1 else -1
                                                    val slideDistanceFactor = 0.35f
                                                    val enterSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
                                                    val enterSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
                                                    val exitSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
                                                    val exitSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)

                                                    (
                                                        slideInHorizontally(
                                                            animationSpec = enterSlideSpec,
                                                            initialOffsetX = { fullWidth -> (direction * fullWidth * slideDistanceFactor).toInt() },
                                                        ) + fadeIn(animationSpec = enterSpec)
                                                    ).togetherWith(
                                                        slideOutHorizontally(
                                                            animationSpec = exitSlideSpec,
                                                            targetOffsetX = { fullWidth -> (-direction * fullWidth * slideDistanceFactor).toInt() },
                                                        ) + fadeOut(animationSpec = exitSpec),
                                                    )
                                                }
                                            },
                                            modifier = Modifier.clipToBounds(),
                                            label = "WeekDaySelectorRowAnimation",
                                        ) { state ->
                                            WeekDaySelectorRow(
                                                weekDays = state.weekDays,
                                                onDateSelected = handleDateSelection,
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // 3. "This week's agenda" section header
                                val weekAgendaSubtitle = uiState.weekRangeTitle

                                AgendaSectionHeader(
                                    title = stringResource(R.string.calendar_weeks_agenda),
                                    subtitle = weekAgendaSubtitle,
                                    onNewEventClick = { onCreateEvent(uiState.selectedDate) },
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // 4. Week Agenda Card grouped by day
                                AnimatedContent(
                                    targetState = weekAnimationState,
                                    contentKey = { it.weekStart },
                                    transitionSpec = {
                                        if (reducedMotion) {
                                            fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                                        } else {
                                            val direction = if (targetState.weekStart.isAfter(initialState.weekStart)) 1 else -1
                                            val slideDistanceFactor = 0.35f
                                            val enterSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
                                            val enterSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
                                            val exitSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
                                            val exitSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)

                                            (
                                                slideInHorizontally(
                                                    animationSpec = enterSlideSpec,
                                                    initialOffsetX = { fullWidth -> (direction * fullWidth * slideDistanceFactor).toInt() },
                                                ) + fadeIn(animationSpec = enterSpec)
                                            ).togetherWith(
                                                slideOutHorizontally(
                                                    animationSpec = exitSlideSpec,
                                                    targetOffsetX = { fullWidth -> (-direction * fullWidth * slideDistanceFactor).toInt() },
                                                ) + fadeOut(animationSpec = exitSpec),
                                            )
                                        }
                                    },
                                    modifier = Modifier.clipToBounds(),
                                    label = "WeekAgendaCardAnimation",
                                ) { state ->
                                    WeekAgendaCard(
                                        weekDays = state.weekDays,
                                        onDateSelected = viewModel::onDateSelected,
                                        onOpenItemDetails = onOpenItemDetails,
                                    )
                                }
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
                                onMonthSelected = { month ->
                                    viewModel.onMonthSelected(month)
                                    viewModel.onViewModeSelected(CalendarViewMode.MONTH)
                                },
                                onDateSelected = { date ->
                                    viewModel.onDateSelected(date)
                                    viewModel.onViewModeSelected(CalendarViewMode.MONTH)
                                },
                            )
                        }

                        CalendarViewMode.MONTH -> {
                            // ==================== MONTH VIEW ====================
                            val shouldFillHeight = isWideScreen && containerMaxHeight >= 500.dp
                            val monthCard: @Composable (Modifier) -> Unit = { cardModifier ->
                                Surface(
                                    shape = CalendarDesignTokens.CardShape,
                                    color = cardBg,
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = cardModifier,
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .then(if (shouldFillHeight) Modifier.fillMaxHeight() else Modifier)
                                                .padding(vertical = 14.dp, horizontal = 4.dp),
                                    ) {
                                        val isMonthViewOnToday =
                                            uiState.displayedMonth == YearMonth.from(uiState.today) && uiState.selectedDate == uiState.today
                                        CalendarNavigationHeader(
                                            title = monthTitle,
                                            showTodayButton = true,
                                            onPrevious = viewModel::onPreviousMonth,
                                            onToday = viewModel::onTodayClicked,
                                            onNext = viewModel::onNextMonth,
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Day of week labels row (M T W T F S S)
                                        DayOfWeekHeaderRow()

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Calendar days grid
                                        val rows = uiState.monthDays.chunked(7)
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .then(if (shouldFillHeight) Modifier.weight(1f) else Modifier),
                                            verticalArrangement = if (shouldFillHeight) Arrangement.SpaceEvenly else Arrangement.spacedBy(2.dp),
                                        ) {
                                            rows.forEach { weekDays ->
                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 1.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    weekDays.forEach { dayModel ->
                                                        CalendarDayCell(
                                                            day = dayModel,
                                                            onClick = handleDateSelection,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(
                                            color = cardBorder,
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Footer row matching redesign: • Today · 3 events, 3 tasks (or Today · Friday, November 13)
                                        val eventCount = uiState.selectedDayEvents.size
                                        val isToday = uiState.selectedDate == uiState.today

                                        val footerPrefix =
                                            if (isToday) {
                                                stringResource(R.string.calendar_today)
                                            } else {
                                                remember(uiState.selectedDate) {
                                                    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
                                                    uiState.selectedDate.format(formatter)
                                                }
                                            }

                                        val fullDate =
                                            remember(uiState.selectedDate) {
                                                val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
                                                uiState.selectedDate.format(formatter)
                                            }
                                        val footerText = "$footerPrefix · $fullDate"

                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(6.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            )
                                            Text(
                                                text = footerText,
                                                style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.5.sp,
                                                        color = CalendarDesignTokens.resolveTextSecondary(isDark),
                                                    ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }

                            val monthAgendaSection: @Composable (Modifier) -> Unit = { sectionModifier ->
                                val errorMessage = uiState.errorMessage
                                val isLoaded = !uiState.isLoading && errorMessage == null
                                val isAgendaEmpty = isLoaded && uiState.selectedDayEvents.isEmpty() && uiState.selectedDayTasks.isEmpty() && uiState.selectedDayBirthdays.isEmpty()
                                val isTodaySelected = uiState.selectedDate == uiState.today

                                val emptyStateDateSubtitle =
                                    remember(uiState.selectedDate) {
                                        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
                                        uiState.selectedDate.format(formatter)
                                    }

                                val agendaTitle =
                                    when {
                                        isAgendaEmpty -> stringResource(R.string.calendar_your_agenda)
                                        isTodaySelected -> stringResource(R.string.calendar_todays_agenda)
                                        else -> stringResource(R.string.calendar_date_agenda, dayAgendaDatePrefix)
                                    }

                                val agendaSubtitle =
                                    if (isAgendaEmpty) emptyStateDateSubtitle else dayAgendaDatePrefix

                                Surface(
                                    shape = CalendarDesignTokens.CardShape,
                                    color = cardBg,
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = sectionModifier,
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .then(if (shouldFillHeight) Modifier.fillMaxHeight() else Modifier)
                                                .padding(vertical = 16.dp, horizontal = 16.dp),
                                    ) {
                                        // Header inside Agenda Card
                                        Text(
                                            text = agendaTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                    color = CalendarDesignTokens.resolveTextPrimary(isDark),
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = agendaSubtitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style =
                                                MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.5.sp,
                                                    color = CalendarDesignTokens.resolveTextSecondary(isDark),
                                                ),
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = cardBorder)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (uiState.isLoading) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .then(if (shouldFillHeight) Modifier.weight(1f) else Modifier.padding(32.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        } else if (errorMessage != null) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .then(if (shouldFillHeight) Modifier.weight(1f) else Modifier.padding(24.dp)),
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
                                        } else if (isAgendaEmpty) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .then(if (shouldFillHeight) Modifier.weight(1f) else Modifier.padding(vertical = 16.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                ) {
                                                    CalendarEmptyIllustration(selectedDate = uiState.selectedDate)
                                                    Spacer(modifier = Modifier.height(18.dp))
                                                    Text(
                                                        text = stringResource(R.string.calendar_empty_room_title),
                                                        style =
                                                            MaterialTheme.typography.titleLarge.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 22.sp,
                                                                color = CalendarDesignTokens.resolveTextPrimary(isDark),
                                                                textAlign = TextAlign.Center,
                                                            ),
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.calendar_empty_no_events_desc) + "\n" +
                                                            stringResource(R.string.calendar_empty_enjoy_space_desc),
                                                        style =
                                                            MaterialTheme.typography.bodyMedium.copy(
                                                                fontSize = 13.5.sp,
                                                                color = CalendarDesignTokens.resolveTextSecondary(isDark),
                                                                textAlign = TextAlign.Center,
                                                                lineHeight = 20.sp,
                                                            ),
                                                    )
                                                    Spacer(modifier = Modifier.height(20.dp))
                                                    Surface(
                                                        onClick = { onCreateEvent(uiState.selectedDate) },
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier =
                                                            Modifier
                                                                .height(44.dp)
                                                                .defaultMinSize(minWidth = 140.dp),
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            RememberMaterialRoundedSymbol(
                                                                name = "add",
                                                                size = 18.dp,
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                            )
                                                            Text(
                                                                text = stringResource(R.string.calendar_new_event),
                                                                style =
                                                                    MaterialTheme.typography.labelLarge.copy(
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        fontSize = 14.sp,
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                    ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Bottom footer matching Month Card footer level
                                            Spacer(modifier = Modifier.height(6.dp))
                                            HorizontalDivider(color = cardBorder)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = stringResource(R.string.calendar_empty_footer),
                                                style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.5.sp,
                                                        color = CalendarDesignTokens.resolveTextSecondary(isDark),
                                                    ),
                                                modifier = Modifier.padding(horizontal = 6.dp),
                                            )
                                        } else {
                                            // Populated state with internal scroll for events and tasks when height is bounded (split-screen / tablet)
                                            val agendaScrollModifier =
                                                if (shouldFillHeight) {
                                                    Modifier
                                                        .weight(1f)
                                                        .verticalScroll(rememberScrollState())
                                                } else {
                                                    Modifier
                                                }
                                            Column(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .then(agendaScrollModifier),
                                            ) {
                                                if (uiState.selectedDayEvents.isNotEmpty()) {
                                                    AgendaCategoryHeader(
                                                        title = stringResource(R.string.calendar_events),
                                                        count = 0,
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    uiState.selectedDayEvents.forEach { event ->
                                                        key(event.id) {
                                                            AgendaEventCard(
                                                                event = event,
                                                                onClick = { onOpenItemDetails(event.id, false) },
                                                                showOutsideTimeColumn = false,
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                        }
                                                    }
                                                }

                                                if (uiState.selectedDayTasks.isNotEmpty()) {
                                                    if (uiState.selectedDayEvents.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                    }
                                                    AgendaCategoryHeader(
                                                        title = stringResource(R.string.calendar_tasks),
                                                        count = 0,
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    uiState.selectedDayTasks.forEach { task ->
                                                        key(task.id) {
                                                            AgendaEventCard(
                                                                event = task,
                                                                onClick = { onOpenItemDetails(task.id, true) },
                                                                onToggleDone = viewModel::toggleEventDone,
                                                                showOutsideTimeColumn = false,
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                        }
                                                    }
                                                }

                                                if (uiState.selectedDayBirthdays.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    AgendaCategoryHeader(
                                                        title = stringResource(R.string.calendar_birthdays),
                                                        iconName = "cake",
                                                        count = 0,
                                                        iconTint = CalendarDesignTokens.BirthdayIconTint,
                                                        badgeBgColor = if (isDark) Color(0xFF3E1C28) else CalendarDesignTokens.BirthdayBoxBg,
                                                        badgeTextColor = if (isDark) Color(0xFFF48FB1) else CalendarDesignTokens.BirthdayIconTint,
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    uiState.selectedDayBirthdays.forEach { birthday ->
                                                        key(birthday.id) {
                                                            BirthdayCard(
                                                                birthday = birthday,
                                                                onClick = { onOpenItemDetails(birthday.id, false) },
                                                                horizontalPadding = 0.dp,
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isWideScreen) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .then(if (shouldFillHeight) Modifier.fillMaxHeight().padding(bottom = 16.dp) else Modifier)
                                            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1.1f)
                                                .then(if (shouldFillHeight) Modifier.fillMaxHeight() else Modifier),
                                    ) {
                                        monthCard(
                                            Modifier
                                                .fillMaxWidth()
                                                .then(if (shouldFillHeight) Modifier.fillMaxHeight() else Modifier),
                                        )
                                    }
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(if (shouldFillHeight) Modifier.fillMaxHeight() else Modifier),
                                    ) {
                                        monthAgendaSection(
                                            Modifier
                                                .fillMaxWidth()
                                                .then(if (shouldFillHeight) Modifier.fillMaxHeight() else Modifier),
                                        )
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    monthCard(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    monthAgendaSection(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                                    )
                                }
                            }
                        }
                    }
                }

                // Common: Birthdays Section (only for Day and Week views, since Month view displays it inside the Agenda card)
                if (uiState.viewMode != CalendarViewMode.YEAR && uiState.viewMode != CalendarViewMode.MONTH && uiState.selectedDayBirthdays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AgendaCategoryHeader(
                        title = stringResource(R.string.calendar_birthdays),
                        iconName = "cake",
                        count = 0,
                        modifier = Modifier.padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
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

            // 3. Floating Today Bar in Year View anchored at bottom
            if (uiState.viewMode == CalendarViewMode.YEAR) {
                YearTodayFloatingBar(
                    today = uiState.today,
                    onClick = {
                        viewModel.onTodayClicked()
                        viewModel.onViewModeSelected(CalendarViewMode.MONTH)
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                horizontal = CalendarDesignTokens.ScreenHorizontalPadding,
                                vertical = 12.dp,
                            ),
                )
            }
        }
    }
}
