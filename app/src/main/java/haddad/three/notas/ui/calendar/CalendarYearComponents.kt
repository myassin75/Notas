package haddad.three.notas.ui.calendar

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalIsDark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthNamesEn =
    arrayOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

private val WeekdayNarrowLabelsEn = listOf("M", "T", "W", "T", "F", "S", "S")

private fun getLocalizedMonthName(month: YearMonth): String {
    val locale = Locale.getDefault()
    return if (locale.language == "en") {
        MonthNamesEn[month.monthValue - 1]
    } else {
        val formatter = DateTimeFormatter.ofPattern("MMMM", locale)
        month
            .format(formatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

/**
 * Prominent Year Header with giant year typography and paired navigation controls.
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

    val prevBtnBg = if (isDark) Color(0xFF282830) else Color(0xFFE5E5EB)
    val prevBtnBorder = BorderStroke(1.dp, if (isDark) Color(0xFF383842) else Color(0xFFDCDCE4))

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Giant prominent year heading
        AnimatedContent(
            targetState = year,
            transitionSpec = {
                val direction = if (targetState >= initialState) 1 else -1
                (
                    slideInHorizontally(animationSpec = tween(130, easing = FastOutSlowInEasing)) { width -> direction * width / 3 } +
                        fadeIn(animationSpec = tween(130))
                ).togetherWith(
                    slideOutHorizontally(animationSpec = tween(110, easing = FastOutSlowInEasing)) { width -> -direction * width / 3 } +
                        fadeOut(animationSpec = tween(110)),
                )
            },
            label = "YearTitleAnimation",
        ) { targetYear ->
            Text(
                text = targetYear.toString(),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 58.sp,
                        letterSpacing = (-1.5).sp,
                        color = textPrimary,
                    ),
                maxLines = 1,
            )
        }

        // Previous & Next Year navigation buttons paired side-by-side
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous Year Button (<)
            Surface(
                onClick = onPreviousYear,
                shape = CircleShape,
                color = prevBtnBg,
                border = prevBtnBorder,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        modifier = Modifier.graphicsLayer { scaleX = -1f },
                        size = 22.dp,
                        tint = textPrimary,
                    )
                }
            }

            // Next Year Button (>)
            Surface(
                onClick = onNextYear,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * Section Header: "THE YEAR AT A GLANCE" and "12 months" indicator.
 */
@Composable
fun YearGlanceSectionHeader(
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "THE YEAR AT A GLANCE",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = textSecondary.copy(alpha = 0.70f),
                ),
        )
        Text(
            text = "12 months",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.60f),
                ),
        )
    }
}

/**
 * Root Year View composable presenting an adaptive 12-month calendar overview.
 *
 * - Mobile portrait (<540dp): 2 columns for maximum legibility and comfortable touch targets.
 * - Tablet / wide landscape (>=540dp): 3 columns (or 4 on large tablets >=760dp).
 * - Fast, optimized year transition with lightweight layout and precomputed grid geometry.
 */
@Composable
fun CalendarYearView(
    displayedYear: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val columns =
            when {
                maxWidth >= 760.dp -> 4
                maxWidth >= 540.dp -> 3
                else -> 2
            }

        val monthRows =
            remember(displayedYear, columns) {
                (1..12).map { YearMonth.of(displayedYear, it) }.chunked(columns)
            }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 1. Prominent Year Header with paired navigation buttons
            YearNavigationHeader(
                year = displayedYear,
                onPreviousYear = onPreviousYear,
                onNextYear = onNextYear,
            )

            // 2. "THE YEAR AT A GLANCE" section indicator
            YearGlanceSectionHeader()

            Spacer(modifier = Modifier.height(6.dp))

            // 3. 12 Monthly Calendar Grids
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                monthRows.forEach { rowMonths ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowMonths.forEach { month ->
                            YearMonthCard(
                                month = month,
                                selectedDate = selectedDate,
                                today = today,
                                onMonthClick = onMonthSelected,
                                onDateClick = onDateSelected,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        val missingSlots = columns - rowMonths.size
                        repeat(missingSlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Bottom clearance for floating Today bar
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

/**
 * Individual month card inside the Year View.
 *
 * Highlighted with a distinctive card container when it matches the selected date's month,
 * while maintaining a minimal and airy aesthetic for other months.
 */
@Composable
fun YearMonthCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onMonthClick: (YearMonth) -> Unit,
    onDateClick: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val isSelectedMonth = month.year == selectedDate.year && month.monthValue == selectedDate.monthValue

    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    val cardBg =
        if (isSelectedMonth) {
            if (isDark) Color(0xFF242428) else Color(0xFFEDEDF2)
        } else {
            Color.Transparent
        }

    val cardBorder =
        if (isSelectedMonth) {
            BorderStroke(1.dp, if (isDark) Color(0xFF383842) else Color(0xFFDCDCE4))
        } else {
            null
        }

    val monthName =
        remember(month.monthValue) {
            getLocalizedMonthName(month)
        }
    val monthNumberString =
        remember(month.monthValue) {
            String.format(Locale.US, "%02d", month.monthValue)
        }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = cardBorder,
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    onClickLabel = "Open $monthName calendar",
                    role = Role.Button,
                ) { onMonthClick(month) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            // Month Title & Two-digit number with active accent indicator dot
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
                            color = textPrimary,
                        ),
                    modifier = Modifier.weight(1f, fill = false),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = monthNumberString,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = textSecondary.copy(alpha = 0.55f),
                            ),
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday Headers (M T W T F S S) with weekends highlighted
            YearWeekdayHeaderRow()

            Spacer(modifier = Modifier.height(4.dp))

            // 6-week x 7-day Date Grid
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
 * Compact localized 7-column weekday row for mini-calendars with weekends in red.
 */
@Composable
private fun YearWeekdayHeaderRow(
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    val isDark = LocalIsDark.current
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val weekendColor = CalendarDesignTokens.resolveWeekendColor(isDark)

    val dayLabels =
        remember(firstDayOfWeek) {
            val locale = Locale.getDefault()
            if (locale.language == "en" && firstDayOfWeek == DayOfWeek.MONDAY) {
                WeekdayNarrowLabelsEn
            } else {
                (0L..6L).map { offset ->
                    firstDayOfWeek.plus(offset).getDisplayName(TextStyle.NARROW, locale)
                }
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dayLabels.forEachIndexed { colIndex, label ->
            val isWeekend = colIndex == 5 || colIndex == 6
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            color = if (isWeekend) weekendColor else textSecondary.copy(alpha = 0.65f),
                            fontWeight = if (isWeekend) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 10.sp,
                        ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Mini date grid for an individual month card in Year View.
 * Always renders 6 rows of 7 columns (42 cells) so all cards share an exact uniform height naturally.
 * Weekends are styled in red, selected day in prominent accent badge, and today in circular border.
 */
@Composable
private fun YearMonthDateGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: ((LocalDate) -> Unit)? = null,
) {
    val isDark = LocalIsDark.current
    val textPrimary =
        if (isDark) Color(0xFFE2E2E8) else Color(0xFF2C2C32)
    val weekendColor = CalendarDesignTokens.resolveWeekendColor(isDark)

    // Compute calendar cells (always fixed to 6 weeks x 7 columns = 42 cells)
    val weeks =
        remember(month) {
            val firstOfMonth = month.atDay(1)
            val leadingBlanks = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
            val daysInMonth = month.lengthOfMonth()

            val allCells = ArrayList<Int?>(42)
            repeat(leadingBlanks) { allCells.add(null) }
            for (day in 1..daysInMonth) {
                allCells.add(day)
            }
            while (allCells.size < 42) {
                allCells.add(null)
            }
            allCells.chunked(7)
        }

    val isSelectedMonth = month.year == selectedDate.year && month.monthValue == selectedDate.monthValue
    val selectedDay = if (isSelectedMonth) selectedDate.dayOfMonth else -1
    val isTodayMonth = month.year == today.year && month.monthValue == today.monthValue
    val todayDay = if (isTodayMonth) today.dayOfMonth else -1

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
                week.forEachIndexed { colIndex, dayNumber ->
                    if (dayNumber == null) {
                        Spacer(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(20.dp),
                        )
                    } else {
                        val isWeekend = colIndex == 5 || colIndex == 6
                        val isSelectedDate = dayNumber == selectedDay
                        val isToday = dayNumber == todayDay

                        val cellClickable =
                            if (onDateClick != null) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 12.dp),
                                    onClick = { onDateClick(month.atDay(dayNumber)) },
                                )
                            } else {
                                Modifier
                            }

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(20.dp)
                                    .then(cellClickable),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isSelectedDate -> {
                                    // Prominent selected date badge
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .then(
                                                    if (isToday) {
                                                        Modifier.border(1.dp, Color.White, CircleShape)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                isToday -> {
                                    // Today outline badge
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .border(
                                                    1.2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                isWeekend -> {
                                    // Weekend date in red
                                    Text(
                                        text = dayNumber.toString(),
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = weekendColor,
                                            ),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                else -> {
                                    // Regular weekday date
                                    Text(
                                        text = dayNumber.toString(),
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
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

/**
 * Polished floating Today navigation bar anchored above the bottom safe area.
 * Displays "TODAY" accent badge, formatted date string, and jump arrow icon.
 */
@Composable
fun YearTodayFloatingBar(
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)

    val barBg = if (isDark) Color(0xFF222228) else Color(0xFFEDEDF2)
    val barBorder = BorderStroke(1.dp, if (isDark) Color(0xFF383842) else Color(0xFFDCDCE4))

    val todayFormatted =
        remember(today) {
            val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
            today.format(formatter)
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = barBg,
        border = barBorder,
        shadowElevation = 6.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Jump to Today, $todayFormatted"
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // "TODAY" Accent Pill Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "TODAY",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                // Full Formatted Date String
                Text(
                    text = todayFormatted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = textPrimary,
                        ),
                )
            }

            // Navigation Arrow
            RememberMaterialRoundedSymbol(
                name = "arrow_forward",
                size = 18.dp,
                tint = if (isDark) Color(0xFFC4C4CE) else Color(0xFF555562),
                weight = FontWeight.SemiBold,
            )
        }
    }
}
