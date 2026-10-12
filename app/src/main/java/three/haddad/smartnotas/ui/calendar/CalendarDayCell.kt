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

/**
 * Header row displaying days of the week (e.g. M T W T F S S).
 */
@Composable
fun DayOfWeekHeaderRow(
    modifier: Modifier = Modifier,
    weekStartsOnSunday: Boolean = false,
) {
    val isDark = LocalIsDark.current
    val textColor = CalendarDesignTokens.resolveTextSecondary(isDark)
    val dayLabels = if (weekStartsOnSunday) {
        listOf("S", "M", "T", "W", "T", "F", "S")
    } else {
        listOf("M", "T", "W", "T", "F", "S", "S")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 8.dp),
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
 * Supports states: NORMAL, SELECTED, TODAY, OUT_OF_MONTH, DISABLED.
 * Renders up to 4 colored indicator dots below the date number.
 */
@Composable
fun CalendarDayCell(
    day: CalendarDayUiModel,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val isSelected = day.state == CalendarDayState.SELECTED
    val isToday = day.state == CalendarDayState.TODAY
    val isOutOfMonth = day.state == CalendarDayState.OUT_OF_MONTH
    val isDisabled = day.state == CalendarDayState.DISABLED

    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textMuted = CalendarDesignTokens.resolveTextMuted(isDark)

    val textColor = when {
        isSelected -> Color.White
        isToday -> CalendarDesignTokens.PrimaryPurple
        isOutOfMonth || isDisabled -> textMuted
        else -> textPrimary
    }

    val fontWeight = when {
        isSelected || isToday -> FontWeight.Bold
        else -> FontWeight.Medium
    }

    val cellBackgroundModifier = when {
        isSelected -> Modifier.background(CalendarDesignTokens.PrimaryPurple, shape = CircleShape)
        isToday -> Modifier.border(
            width = 1.5.dp,
            color = CalendarDesignTokens.PrimaryPurple,
            shape = CircleShape,
        )
        else -> Modifier
    }

    val clickableModifier = if (!isDisabled) {
        Modifier
            .clip(CircleShape)
            .clickable { onClick(day.date) }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .defaultMinSize(
                minWidth = CalendarDesignTokens.DayCellMinWidth,
                minHeight = CalendarDesignTokens.DayCellMinHeight,
            )
            .then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Date circle / container
        Box(
            modifier = Modifier
                .size(CalendarDesignTokens.SelectedIndicatorSize)
                .then(cellBackgroundModifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = textColor,
                    fontWeight = fontWeight,
                    fontSize = 14.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Indicator dots row
        if (day.dots.isNotEmpty() && !isOutOfMonth) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                day.dots.take(4).forEach { dot ->
                    val dotColor = if (isSelected) Color.White else dot.color
                    Box(
                        modifier = Modifier
                            .size(CalendarDesignTokens.DotIndicatorSize)
                            .background(dotColor, shape = CircleShape),
                    )
                }
            }
        } else {
            // Keep consistent height so cells without dots don't shrink
            Spacer(modifier = Modifier.height(CalendarDesignTokens.DotIndicatorSize))
        }
    }
}
