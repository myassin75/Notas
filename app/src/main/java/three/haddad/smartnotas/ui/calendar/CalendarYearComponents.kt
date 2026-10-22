package three.haddad.smartnotas.ui.calendar

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark
import three.haddad.smartnotas.ui.theme.LocalReducedMotion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Navigation header for Year View with centered year title and stationary previous/next arrows.
 */
@Composable
fun YearNavigationHeader(
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    val buttonBg = CalendarDesignTokens.resolveCardBg(isDark)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
    ) {
        // Centered Year Title
        Text(
            text = year.toString(),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = textPrimary,
                ),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
        )

        // Previous Year Button (<)
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onPreviousYear,
                shape = CircleShape,
                color = buttonBg,
                border = border,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        modifier = Modifier.graphicsLayer { scaleX = -1f },
                        size = 20.dp,
                        tint = textPrimary,
                    )
                }
            }
        }

        // Next Year Button (>)
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onNextYear,
                shape = CircleShape,
                color = buttonBg,
                border = border,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        size = 20.dp,
                        tint = textPrimary,
                    )
                }
            }
        }
    }
}

/**
 * Root Year View composable presenting an adaptive 12-month calendar overview.
 *
 * - Mobile portrait (<540dp): 2 columns for maximum legibility and comfortable touch targets.
 * - Tablet / wide landscape (>=540dp): 3 columns (or 4 on large tablets >=760dp).
 * - Animated year transition: Directional horizontal slide and fade when navigating years.
 */
@Composable
fun CalendarYearView(
    displayedYear: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    val buttonBg = CalendarDesignTokens.resolveCardBg(isDark)
    val reducedMotion = LocalReducedMotion.current

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        // 1. Animated Year Content: Centered Year Title and 12-Month Grid animate together
        AnimatedContent(
            targetState = displayedYear,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                } else {
                    val direction = if (targetState >= initialState) 1 else -1
                    val slideDistanceFactor = 0.35f
                    val enterSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
                    val enterSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
                    val exitSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
                    val exitSlideSpec = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)

                    (
                        slideInHorizontally(
                            animationSpec = enterSlideSpec,
                            initialOffsetX = { fullWidth -> (direction * fullWidth * slideDistanceFactor).toInt() },
                        ) +
                            fadeIn(
                                animationSpec = enterSpec,
                            )
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = exitSlideSpec,
                            targetOffsetX = { fullWidth -> (-direction * fullWidth * slideDistanceFactor).toInt() },
                        ) +
                            fadeOut(
                                animationSpec = exitSpec,
                            ),
                    )
                }
            },
            label = "YearViewAnimation",
        ) { targetYear ->
            val months =
                remember(targetYear) {
                    (1..12).map { YearMonth.of(targetYear, it) }
                }

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Centered Year Title Row (animated with the month grid)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = targetYear.toString(),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = textPrimary,
                            ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Responsive Months Grid
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                ) {
                    val columns =
                        when {
                            maxWidth >= 760.dp -> 4
                            maxWidth >= 540.dp -> 3
                            else -> 2
                        }

                    val monthRows =
                        remember(months, columns) {
                            months.chunked(columns)
                        }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        monthRows.forEach { rowMonths ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowMonths.forEach { month ->
                                    YearMonthCard(
                                        month = month,
                                        selectedDate = selectedDate,
                                        today = today,
                                        onMonthClick = onMonthSelected,
                                        onDateClick = onDateSelected,
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                    )
                                }

                                val missingSlots = columns - rowMonths.size
                                repeat(missingSlots) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // 2. Stationary Navigation Arrows (< on left, > on right) pinned in place
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Previous Year Button (<)
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onPreviousYear,
                    shape = CircleShape,
                    color = buttonBg,
                    border = border,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            modifier = Modifier.graphicsLayer { scaleX = -1f },
                            size = 20.dp,
                            tint = textPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Next Year Button (>)
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onNextYear,
                    shape = CircleShape,
                    color = buttonBg,
                    border = border,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            size = 20.dp,
                            tint = textPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual month card inside the Year View.
 */
@Composable
fun YearMonthCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onMonthClick: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val isSelectedMonth = month.year == selectedDate.year && month.monthValue == selectedDate.monthValue

    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)

    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)

    val cardBorder =
        if (isSelectedMonth) {
            BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.85f else 0.75f),
            )
        } else {
            BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
        }

    val monthName =
        remember(month) {
            val formatter = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
            month
                .format(formatter)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

    Surface(
        shape = CalendarDesignTokens.CardShape,
        color = cardBg,
        border = cardBorder,
        modifier =
            modifier
                .clip(CalendarDesignTokens.CardShape)
                .clickable(
                    onClickLabel = "Open $monthName calendar",
                    role = Role.Button,
                ) { onMonthClick(month) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // Month Title & Highlight Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = monthName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isSelectedMonth) MaterialTheme.colorScheme.primary else textPrimary,
                        ),
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (isSelectedMonth) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday Headers (M T W T F S S)
            YearWeekdayHeaderRow()

            Spacer(modifier = Modifier.height(4.dp))

            // Date Grid
            YearMonthDateGrid(
                month = month,
                selectedDate = selectedDate,
                today = today,
                onDateClick = onDateClick,
            )
        }
    }
}

/**
 * Compact localized 7-column weekday row for mini-calendars.
 */
@Composable
private fun YearWeekdayHeaderRow(
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    val isDark = LocalIsDark.current
    val textColor = CalendarDesignTokens.resolveTextSecondary(isDark)
    val dayLabels =
        remember(firstDayOfWeek) {
            val locale = Locale.getDefault()
            (0L..6L).map { offset ->
                firstDayOfWeek.plus(offset).getDisplayName(TextStyle.NARROW, locale)
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dayLabels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                        ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Mini date grid for an individual month card in Year View.
 */
@Composable
private fun YearMonthDateGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)

    // Compute calendar cells (weeks of 7 days, with null for leading/trailing days)
    val weeks =
        remember(month) {
            val firstOfMonth = month.atDay(1)
            val leadingBlanks = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
            val daysInMonth = month.lengthOfMonth()

            val allCells = mutableListOf<Int?>()
            repeat(leadingBlanks) { allCells.add(null) }
            for (day in 1..daysInMonth) {
                allCells.add(day)
            }
            while (allCells.size % 7 != 0) {
                allCells.add(null)
            }
            allCells.chunked(7)
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                week.forEach { dayNumber ->
                    if (dayNumber == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val date = remember(month, dayNumber) { month.atDay(dayNumber) }
                        val isSelectedDate = date == selectedDate
                        val isToday = date == today

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(22.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Select $date",
                                    ) { onDateClick(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isSelectedDate -> {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                                .then(
                                                    if (isToday) {
                                                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                    } else {
                                                        Modifier
                                                    }
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                isToday -> {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = dayNumber.toString(),
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
