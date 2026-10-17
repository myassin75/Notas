package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Header row displaying days of the week localized to the current locale.
 */
@Composable
fun DayOfWeekHeaderRow(
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * An individual day cell in the month/week calendar grid.
 *
 * Supports distinguishable states:
 * - Current date (today)
 * - Selected date (rounded rectangle / squircle)
 * - Today when selected
 * - Dates outside the displayed month
 * - Normal dates in the month
 *
 * Renders up to 4 colored indicator dots below the date number.
 */
@Composable
fun CalendarDayCell(
    day: CalendarDayUiModel,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val isSelected = day.isSelected || day.state == CalendarDayState.SELECTED
    val isToday = day.isToday || day.state == CalendarDayState.TODAY
    val isOutOfMonth = day.state == CalendarDayState.OUT_OF_MONTH
    val isDisabled = day.state == CalendarDayState.DISABLED

    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textOutOfMonth = CalendarDesignTokens.resolveOutOfMonthText(isDark)

    val textColor = when {
        isSelected -> Color.White
        isToday -> CalendarDesignTokens.PrimaryPurple
        isOutOfMonth || isDisabled -> textOutOfMonth
        else -> textPrimary
    }

    val fontWeight = when {
        isSelected || isToday -> FontWeight.Bold
        isOutOfMonth -> FontWeight.Normal
        else -> FontWeight.Medium
    }

    val dayShape = CalendarDesignTokens.SelectedDayShape
    val cellBackgroundModifier = when {
        isSelected && isToday -> Modifier
            .background(CalendarDesignTokens.PrimaryPurple, shape = dayShape)
            .border(
                1.5.dp,
                if (isDark) Color.White.copy(alpha = 0.5f) else CalendarDesignTokens.DarkPurpleSelected,
                shape = dayShape,
            )
        isSelected -> Modifier.background(CalendarDesignTokens.PrimaryPurple, shape = dayShape)
        isToday -> Modifier.border(
            width = 1.5.dp,
            color = CalendarDesignTokens.PrimaryPurple,
            shape = dayShape,
        )
        else -> Modifier
    }

    val fullDateDescription = remember(day.date, isToday, isSelected, day.dots.size) {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
        val dateText = day.date.format(formatter)
        val todayText = if (isToday) ", Today" else ""
        val selectedText = if (isSelected) ", Selected" else ""
        val dotsText = if (day.dots.isNotEmpty()) ", ${day.dots.size} items" else ""
        "$dateText$todayText$selectedText$dotsText"
    }

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = CalendarDesignTokens.DayCellMinWidth,
                minHeight = CalendarDesignTokens.DayCellMinHeight,
            )
            .semantics {
                contentDescription = fullDateDescription
                role = Role.Button
            }
            .then(
                if (!isDisabled) {
                    Modifier.clickable { onClick(day.date) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = CalendarDesignTokens.SelectedIndicatorWidth,
                    height = CalendarDesignTokens.SelectedIndicatorHeight,
                )
                .then(cellBackgroundModifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = day.dayNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontWeight = fontWeight,
                        fontSize = 14.5.sp,
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Indicator dots row
                if (day.dots.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        day.dots.take(4).forEach { dot ->
                            val dotColor = when {
                                isSelected -> Color.White
                                isOutOfMonth -> dot.color.copy(alpha = 0.35f)
                                else -> dot.color
                            }
                            Box(
                                modifier = Modifier
                                    .size(CalendarDesignTokens.DotIndicatorSize)
                                    .background(dotColor, shape = CircleShape),
                            )
                        }
                    }
                } else {
                    // Consistent spacing when there are no dots
                    Spacer(modifier = Modifier.height(CalendarDesignTokens.DotIndicatorSize))
                }
            }
        }
    }
}
