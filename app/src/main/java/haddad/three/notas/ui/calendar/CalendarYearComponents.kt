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
import androidx.compose.ui.draw.clipToBounds
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
import haddad.three.notas.R
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.LocalReducedMotion
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

private val MonthNamesEn = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val WeekdayNarrowLabelsEn = listOf("M", "T", "W", "T", "F", "S", "S")

private fun getLocalizedMonthName(month: YearMonth): String {
    val locale = Locale.getDefault()
    return if (locale.language == "en") {
        MonthNamesEn[month.monthValue - 1]
    } else {
        val formatter = DateTimeFormatter.ofPattern("MMMM", locale)
        month.format(formatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
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
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    val buttonBg = CalendarDesignTokens.resolveCardBg(isDark)

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
            // 1. Stationary Navigation Header with snappy Year Title
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
            ) {
                // Centered Year Title (lightweight 100ms slide/fade)
                AnimatedContent(
                    targetState = displayedYear,
                    transitionSpec = {
                        val direction = if (targetState >= initialState) 1 else -1
                        (
                            slideInHorizontally(animationSpec = tween(100, easing = FastOutSlowInEasing)) { width -> direction * width / 3 } +
                                fadeIn(animationSpec = tween(100))
                        ).togetherWith(
                            slideOutHorizontally(animationSpec = tween(80, easing = FastOutSlowInEasing)) { width -> -direction * width / 3 } +
                                fadeOut(animationSpec = tween(80))
                        )
                    },
                    modifier = Modifier.align(Alignment.Center),
                    label = "YearTitleAnimation",
                ) { targetYear ->
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

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Months Grid (Directly rendered in-place without destroying/recreating 12 cards)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                monthRows.forEach { rowMonths ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowMonths.forEach { month ->
                            YearMonthCard(
                                month = month,
                                selectedDate = selectedDate,
                                today = today,
                                onMonthClick = onMonthSelected,
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

            Spacer(modifier = Modifier.height(24.dp))
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
    onDateClick: ((LocalDate) -> Unit)? = null,
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

    val monthName = remember(month.monthValue) {
        getLocalizedMonthName(month)
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
 * Always renders 6 rows of 7 columns (42 cells) so all cards share an exact uniform height naturally.
 * Lightweight non-clickable cells for maximum recomposition and scrolling performance.
 */
@Composable
private fun YearMonthDateGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)

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
                week.forEach { dayNumber ->
                    if (dayNumber == null) {
                        Spacer(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(20.dp),
                        )
                    } else {
                        val isSelectedDate = dayNumber == selectedDay
                        val isToday = dayNumber == todayDay

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isSelectedDate -> {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(19.dp)
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
                                                    fontSize = 10.5.sp,
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
                                                .size(19.dp)
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
                                                    fontSize = 10.5.sp,
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
