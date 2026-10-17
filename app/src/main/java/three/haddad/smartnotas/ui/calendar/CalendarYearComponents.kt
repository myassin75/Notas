package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate
import java.time.YearMonth

/**
 * Top year navigation card containing title, leap-year subtitle, and (< Today >) controls.
 */
@Composable
fun YearNavigationHeaderCard(
    yearTitle: String,
    yearSubtitle: String,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)

    Surface(
        shape = CalendarDesignTokens.CardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
    ) {
        CalendarNavigationHeader(
            title = yearTitle,
            subtitle = yearSubtitle,
            onPrevious = onPrevious,
            onToday = onToday,
            onNext = onNext,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

/**
 * Responsive grid content displaying all 12 months for the selected year.
 */
@Composable
fun YearViewContent(
    yearUiModel: CalendarYearUiModel,
    onMonthClick: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
    ) {
        val columns = when {
            maxWidth >= 720.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }

        val monthRows = yearUiModel.months.chunked(columns)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            monthRows.forEach { rowMonths ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowMonths.forEach { monthModel ->
                        Box(modifier = Modifier.weight(1f)) {
                            YearMonthMiniCard(
                                month = monthModel,
                                onMonthClick = onMonthClick,
                                onDateClick = onDateClick,
                            )
                        }
                    }
                    repeat(columns - rowMonths.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Compact mini month card within the Year View.
 * Displays month title, weekday abbreviations, and 7-column date grid.
 */
@Composable
fun YearMonthMiniCard(
    month: CalendarYearMonthUiModel,
    onMonthClick: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textMuted = CalendarDesignTokens.resolveTextMuted(isDark)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            // Month Header (Clickable to jump into Month View)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMonthClick(month.yearMonth) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = month.monthName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimary,
                    ),
                )

                if (month.totalEventsCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${month.totalEventsCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = CalendarDesignTokens.PrimaryPurple,
                                ),
                            )
                        }
                    }
                } else {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        size = 14.dp,
                        tint = textMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Weekday labels: M T W T F S S
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { letter ->
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted,
                        ),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Calendar days grid
            val weeks = month.days.chunked(7)
            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                MiniDayCell(
                                    day = day,
                                    onClick = { onDateClick(day.date) },
                                )
                            }
                        }
                    }
                    // Pad incomplete week rows
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Mini day cell inside a month card.
 */
@Composable
private fun MiniDayCell(
    day: CalendarYearDayUiModel,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    val cellModifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .then(
            when {
                day.isSelected -> Modifier.background(CalendarDesignTokens.PrimaryPurple, CircleShape)
                day.isToday -> Modifier
                    .border(1.dp, CalendarDesignTokens.PrimaryPurple, CircleShape)
                    .background(
                        if (isDark) CalendarDesignTokens.DarkPurpleSelected.copy(alpha = 0.4f)
                        else CalendarDesignTokens.SoftLavender,
                        CircleShape,
                    )
                else -> Modifier
            },
        )
        .clickable(onClick = onClick)

    Box(
        modifier = cellModifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${day.dayNumber}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (day.isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        day.isSelected -> Color.White
                        day.isToday -> CalendarDesignTokens.PrimaryPurple
                        else -> if (day.hasEvents) textPrimary else textSecondary
                    },
                ),
            )
            if (day.hasEvents && !day.isSelected) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(day.dotColor ?: CalendarDesignTokens.PrimaryPurple),
                )
            }
        }
    }
}
