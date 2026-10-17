package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Navigation header for Year View displaying the current year and controls.
 */
@Composable
fun YearNavigationHeader(
    year: Int,
    isCurrentYear: Boolean,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    val buttonBg = if (isDark) CalendarDesignTokens.CardBgDark else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = CalendarDesignTokens.ScreenHorizontalPadding,
                vertical = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Active Year Title
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = textPrimary,
            ),
        )

        // Navigation controls (< Today >)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            modifier = Modifier.graphicsLayer { scaleX = -1f },
                            size = 18.dp,
                            tint = textPrimary,
                        )
                    }
                }
            }

            // Today Button (jump back to current year)
            if (!isCurrentYear) {
                Surface(
                    onClick = onToday,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender,
                    modifier = Modifier
                        .height(34.dp)
                        .defaultMinSize(minWidth = 52.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_today),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = CalendarDesignTokens.PrimaryPurple,
                            ),
                        )
                    }
                }
            }

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
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            size = 18.dp,
                            tint = textPrimary,
                        )
                    }
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
 */
@Composable
fun CalendarYearView(
    displayedYear: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onToday: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = remember(displayedYear) {
        (1..12).map { YearMonth.of(displayedYear, it) }
    }
    val isCurrentYear = displayedYear == today.year

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Year Navigation Header
        YearNavigationHeader(
            year = displayedYear,
            isCurrentYear = isCurrentYear,
            onPreviousYear = onPreviousYear,
            onNextYear = onNextYear,
            onToday = onToday,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Responsive Months Grid
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
        ) {
            val columns = when {
                maxWidth >= 760.dp -> 4
                maxWidth >= 540.dp -> 3
                else -> 2
            }

            val monthRows = remember(months, columns) {
                months.chunked(columns)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                monthRows.forEach { rowMonths ->
                    Row(
                        modifier = Modifier
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
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }

                        // Fill trailing slots if row has fewer columns
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

    val cardBg = if (isSelectedMonth) {
        if (isDark) CalendarDesignTokens.CardBgDark else Color.White
    } else {
        CalendarDesignTokens.resolveCardBg(isDark)
    }

    val cardBorder = if (isSelectedMonth) {
        BorderStroke(
            1.5.dp,
            CalendarDesignTokens.PrimaryPurple.copy(alpha = if (isDark) 0.85f else 0.75f),
        )
    } else {
        BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    }

    val monthName = remember(month) {
        val formatter = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
        month.format(formatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    Surface(
        shape = CalendarDesignTokens.CardShape,
        color = cardBg,
        border = cardBorder,
        modifier = modifier
            .clip(CalendarDesignTokens.CardShape)
            .clickable(
                onClickLabel = "Open $monthName calendar",
                role = Role.Button,
            ) { onMonthClick(month) },
    ) {
        Column(
            modifier = Modifier
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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isSelectedMonth) CalendarDesignTokens.PrimaryPurple else textPrimary,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (isSelectedMonth) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CalendarDesignTokens.PrimaryPurple, CircleShape),
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
    val dayLabels = remember(firstDayOfWeek) {
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
                    style = MaterialTheme.typography.labelSmall.copy(
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
    val weeks = remember(month) {
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
                            modifier = Modifier
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
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(CalendarDesignTokens.PrimaryPurple, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                isToday -> {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender,
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CalendarDesignTokens.PrimaryPurple,
                                            ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
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
