package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate

/**
 * 7-day compact selector row for Week View matching the reference design:
 * Days are aligned horizontally with weekday letter above and day number below.
 * The selected day is wrapped in a prominent purple pill with white text.
 */
@Composable
fun WeekDaySelectorRow(
    weekDays: List<CalendarWeekDayUiModel>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val textMuted = CalendarDesignTokens.resolveTextMuted(isDark)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weekDays.forEach { day ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (day.isSelected) {
                    // Selected Day: Tall purple pill
                    Surface(
                        onClick = { onDateSelected(day.date) },
                        shape = RoundedCornerShape(18.dp),
                        color = CalendarDesignTokens.PrimaryPurple,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 42.dp)
                                .height(68.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = day.dayOfWeekLetter,
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                    ),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = day.dayNumber.toString(),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    ),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Indicator dots inside selected pill
                            if (day.dots.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    day.dots.take(3).forEach {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(4.dp)
                                                    .background(Color.White, shape = CircleShape),
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                } else {
                    // Non-selected Day
                    val isTodayNonSelected = day.isToday
                    val dayTextColor =
                        when {
                            isTodayNonSelected -> CalendarDesignTokens.PrimaryPurple
                            day.state == CalendarDayState.OUT_OF_MONTH -> textMuted
                            else -> textPrimary
                        }
                    val nonSelectedBg =
                        if (isTodayNonSelected) {
                            if (isDark) CalendarDesignTokens.DarkPurpleSelected.copy(alpha = 0.35f) else CalendarDesignTokens.SoftLavender.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        }
                    val nonSelectedBorder =
                        if (isTodayNonSelected) {
                            BorderStroke(1.5.dp, CalendarDesignTokens.PrimaryPurple)
                        } else {
                            null
                        }

                    Surface(
                        onClick = { onDateSelected(day.date) },
                        shape = RoundedCornerShape(16.dp),
                        color = nonSelectedBg,
                        border = nonSelectedBorder,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 42.dp)
                                .height(68.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = day.dayOfWeekLetter,
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        color = if (isTodayNonSelected) CalendarDesignTokens.PrimaryPurple else textSecondary,
                                        fontWeight = if (isTodayNonSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                    ),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = day.dayNumber.toString(),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = dayTextColor,
                                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    ),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (day.dots.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    day.dots.take(3).forEach { dot ->
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(4.dp)
                                                    .background(dot.color, shape = CircleShape),
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Week Agenda component: displays the 7 days of the selected week grouped by day.
 * - Days without items display clean rows with day label and "No events".
 * - The selected day expands into a soft lavender card with its event cards and "Today" badge.
 */
@Composable
fun WeekAgendaCard(
    weekDays: List<CalendarWeekDayUiModel>,
    onDateSelected: (LocalDate) -> Unit,
    onOpenNote: (Long) -> Unit = {},
    onOpenItemDetails: (id: Long, isTask: Boolean) -> Unit = { id, _ -> onOpenNote(id) },
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val textMuted = CalendarDesignTokens.resolveTextMuted(isDark)

    Surface(
        shape = CalendarDesignTokens.CardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            weekDays.forEach { day ->
                if (day.isSelected) {
                    // Highlighted Selected Day Container
                    val highlightBg =
                        if (isDark) {
                            CalendarDesignTokens.DarkPurpleSelected
                        } else {
                            CalendarDesignTokens.SoftLavender
                        }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = highlightBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            // Day header row: "Saturday, October 3" + optional "Today" badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = day.fullDateLabel,
                                    style =
                                        MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = CalendarDesignTokens.PrimaryPurple,
                                        ),
                                )
                                if (day.isToday) {
                                    Text(
                                        text = stringResource(R.string.calendar_today),
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = CalendarDesignTokens.PrimaryPurple,
                                            ),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Day's event cards or empty placeholder
                            if (day.events.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.calendar_no_events),
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            color = textSecondary,
                                        ),
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    day.events.forEach { event ->
                                        WeekEventItemCard(
                                            event = event,
                                            onClick = { onOpenItemDetails(event.id, event.isTask) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Compact unselected day row: "Mon 28     No events"
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDateSelected(day.date) }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = day.dayShortName,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.5.sp,
                                        color = textSecondary,
                                    ),
                            )
                            Text(
                                text = day.dayNumber.toString(),
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = textPrimary,
                                    ),
                            )
                        }

                        if (day.events.isEmpty()) {
                            Text(
                                text = stringResource(R.string.calendar_no_events),
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        color = textMuted,
                                    ),
                            )
                        } else {
                            val count = day.events.size
                            Text(
                                text =
                                    if (count == 1) {
                                        stringResource(R.string.calendar_event_count_single)
                                    } else {
                                        stringResource(R.string.calendar_events_count, count)
                                    },
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CalendarDesignTokens.PrimaryPurple,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * An event card rendered inside the week highlighted day container.
 */
@Composable
private fun WeekEventItemCard(
    event: CalendarEventUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val itemBg = if (isDark) CalendarDesignTokens.CardBgDark else Color.White
    val border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = itemBg,
        border = border,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category Icon Box
            Box(
                modifier =
                    Modifier
                        .size(CalendarDesignTokens.IconBoxSize)
                        .background(event.iconBgColor, shape = CalendarDesignTokens.IconBoxShape),
                contentAlignment = Alignment.Center,
            ) {
                CalendarEventIcon(
                    iconName = event.iconName,
                    tint = event.iconTint,
                    size = 20.dp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPrimary,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.subtitle,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = textSecondary,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (event.isDone) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .background(CalendarDesignTokens.DotGreen.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = 14.dp,
                        tint = CalendarDesignTokens.DotGreen,
                    )
                }
            }
        }
    }
}
