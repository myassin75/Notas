package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private val HourHeight: Dp = 68.dp
private val TimeColumnWidth: Dp = 50.dp

/**
 * Daily timeline card matching the reference design:
 * - Rounded card container (22.dp).
 * - Optional All-Day / Unscheduled items bar.
 * - Hourly timeline grid (00:00–23:00) with time labels and horizontal lines.
 * - Duration-based event blocks with left category accent bars.
 * - Instantaneous task markers with interactive completion checkboxes.
 * - Automatic scroll anchoring to earliest event or 08:00 AM.
 */
@Composable
fun DayTimelineCard(
    timelineItems: List<CalendarTimelineItemUiModel>,
    allDayItems: List<CalendarTimelineItemUiModel>,
    selectedDate: LocalDate,
    onItemClick: (Long) -> Unit = {},
    onOpenItemDetails: (id: Long, isTask: Boolean) -> Unit = { id, _ -> onItemClick(id) },
    onToggleDone: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textMuted = CalendarDesignTokens.resolveTextMuted(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)

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
                    .padding(vertical = 12.dp),
        ) {
            // 1. All-Day / Unscheduled section if any items exist
            if (allDayItems.isNotEmpty()) {
                AllDayItemsSection(
                    items = allDayItems,
                    onItemClick = { id -> onOpenItemDetails(id, false) },
                    onToggleDone = onToggleDone,
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = cardBorder,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. Vertically scrollable hourly timeline
            val scrollState = rememberScrollState()
            val density = LocalDensity.current

            LaunchedEffect(selectedDate, timelineItems) {
                val targetHour =
                    when {
                        timelineItems.isNotEmpty() -> {
                            val earliestMinute = timelineItems.minOf { it.startMinuteOfDay }
                            maxOf(0, (earliestMinute / 60) - 1)
                        }
                        selectedDate == LocalDate.now() -> {
                            maxOf(0, LocalTime.now().hour - 1)
                        }
                        else -> 8 // Default to 08:00 AM
                    }
                val scrollPx = with(density) { (targetHour * HourHeight.toPx()).toInt() }
                scrollState.scrollTo(scrollPx)
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                        .verticalScroll(scrollState),
            ) {
                // Background hourly grid (00:00 to 23:00)
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (hour in 0..23) {
                        val timeLabel = String.format(Locale.getDefault(), "%02d:00", hour)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(HourHeight),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = timeLabel,
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textMuted,
                                    ),
                                modifier =
                                    Modifier
                                        .width(TimeColumnWidth)
                                        .padding(start = 12.dp, top = 0.dp),
                            )

                            HorizontalDivider(
                                thickness = 0.8.dp,
                                color = cardBorder,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(top = 7.dp, end = 12.dp),
                            )
                        }
                    }
                }

                // Overlaid timed events & tasks
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(HourHeight * 24),
                ) {
                    // Live Current-Time indicator line (rendered behind event cards)
                    LiveCurrentTimeLine(
                        selectedDate = selectedDate,
                        hourHeight = HourHeight,
                        timeColumnWidth = TimeColumnWidth,
                    )

                    val availableWidth = maxWidth - TimeColumnWidth - 12.dp

                    timelineItems.forEach { item ->
                        val topOffset = HourHeight * (item.startMinuteOfDay / 60f)
                        val colWidth = availableWidth / maxOf(1, item.totalCols)
                        val xOffset = TimeColumnWidth + (colWidth * item.colIndex)

                        if (item.type == TimelineItemType.EVENT) {
                            val durationMinutes = maxOf(20, item.endMinuteOfDay - item.startMinuteOfDay)
                            val blockHeight = maxOf(38.dp, HourHeight * (durationMinutes / 60f))

                            Box(
                                modifier =
                                    Modifier
                                        .offset(x = xOffset, y = topOffset)
                                        .width(colWidth - 4.dp)
                                        .height(blockHeight)
                                        .padding(end = 4.dp),
                            ) {
                                TimelineEventBlock(
                                    item = item,
                                    onClick = { onOpenItemDetails(item.id, false) },
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                        } else {
                            // Instantaneous Task
                            Box(
                                modifier =
                                    Modifier
                                        .offset(x = xOffset, y = topOffset)
                                        .width(colWidth - 4.dp)
                                        .padding(end = 4.dp),
                            ) {
                                TimelineTaskMarker(
                                    item = item,
                                    onClick = { onOpenItemDetails(item.id, true) },
                                    onToggleDone = { onToggleDone(item.id, item.isDone) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live horizontal current-time indicator line across the Day timeline:
 * - Visible ONLY when viewing today.
 * - Shows a small 8.dp circle dot indicator at the beginning of the timeline grid.
 * - Draws a thin 1.8.dp clearly visible horizontal line across the timeline.
 * - Positioned by the current time according to the timeline scale (hourHeight / 60 per minute).
 * - Lifecycle-aware: updates on app resume (ON_RESUME) and recalculates when day changes.
 * - Efficient minute-based delay ticker isolated to this composable (zero parent recomposition).
 * - Layered behind event blocks so titles and descriptions are never obscured.
 */
@Composable
fun LiveCurrentTimeLine(
    selectedDate: LocalDate,
    hourHeight: Dp = HourHeight,
    timeColumnWidth: Dp = TimeColumnWidth,
    modifier: Modifier = Modifier,
) {
    var todayDate by remember { mutableStateOf(LocalDate.now()) }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    todayDate = LocalDate.now()
                    currentTime = LocalTime.now()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lifecycleOwner, selectedDate) {
        while (true) {
            val now = LocalTime.now()
            val nowDate = LocalDate.now()
            todayDate = nowDate
            currentTime = now
            val millisUntilNextMinute =
                ((60 - now.second) * 1000L - (now.nano / 1_000_000L)).coerceIn(1000L, 60_000L)
            delay(millisUntilNextMinute)
        }
    }

    if (selectedDate != todayDate) return

    val currentMinuteOfDay = currentTime.hour * 60 + currentTime.minute
    val lineTopOffset = hourHeight * (currentMinuteOfDay / 60f)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .offset(y = lineTopOffset - 4.dp)
                .height(8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Horizontal line starting at timeColumnWidth and spanning across to the right edge
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = timeColumnWidth, end = 12.dp)
                    .height(1.8.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(1.dp)),
        )

        // Small indicator circle at the beginning of the line
        Box(
            modifier =
                Modifier
                    .padding(start = timeColumnWidth)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
        )
    }
}

/**
 * Event block matching the reference screenshot:
 * Rounded background card with vertical accent indicator bar on the left,
 * title, time range, optional category icon, and completion state.
 */
@Composable
fun TimelineEventBlock(
    item: CalendarTimelineItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val effectiveAccent =
        if (item.accentColor == CalendarDesignTokens.PrimaryPurple) {
            MaterialTheme.colorScheme.primary
        } else {
            item.accentColor
        }
    val effectiveBg =
        if (item.containerBgColor == CalendarDesignTokens.CategoryPurpleBg) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            item.containerBgColor
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = effectiveBg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left vertical accent bar
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            effectiveAccent,
                            shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                        ),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Optional category/location icon
                if (item.iconName != "edit" && item.iconName != "edit_note") {
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .background(item.accentColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CalendarEventIcon(
                            iconName = item.iconName,
                            tint = item.accentColor,
                            size = 16.dp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = item.title,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = textPrimary,
                                textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = item.subtitle,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = textSecondary,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (item.isDone) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(CalendarDesignTokens.DotGreen.copy(alpha = 0.2f), shape = CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "check",
                            size = 12.dp,
                            tint = CalendarDesignTokens.DotGreen,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Instantaneous Task Marker:
 * Compact marker with interactive completion checkbox and title.
 */
@Composable
fun TimelineTaskMarker(
    item: CalendarTimelineItemUiModel,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val cardBg = if (isDark) CalendarDesignTokens.CardBgDark else Color.White

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Interactive checkbox
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleDone),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (item.isDone) "check_box" else "check_box_outline_blank",
                    size = 18.dp,
                    tint = if (item.isDone) CalendarDesignTokens.DotGreen else textSecondary,
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = textPrimary,
                            textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.timeRangeDisplay,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            color = textSecondary,
                        ),
                )
            }
        }
    }
}

/**
 * All-Day / Unscheduled items section.
 */
@Composable
private fun AllDayItemsSection(
    items: List<CalendarTimelineItemUiModel>,
    onItemClick: (Long) -> Unit,
    onToggleDone: (Long, Boolean) -> Unit,
) {
    val isDark = LocalIsDark.current
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
    ) {
        Text(
            text = "All day",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = textSecondary,
                ),
            modifier = Modifier.padding(bottom = 6.dp),
        )

        items.forEach { item ->
            Surface(
                onClick = { onItemClick(item.id) },
                shape = RoundedCornerShape(8.dp),
                color = item.containerBgColor,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(item.accentColor, shape = CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = textPrimary,
                                textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.type == TimelineItemType.TASK) {
                        Box(
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleDone(item.id, item.isDone) },
                            contentAlignment = Alignment.Center,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = if (item.isDone) "check_box" else "check_box_outline_blank",
                                size = 16.dp,
                                tint = if (item.isDone) CalendarDesignTokens.DotGreen else textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
