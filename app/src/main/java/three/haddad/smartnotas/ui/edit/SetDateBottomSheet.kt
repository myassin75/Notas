package three.haddad.smartnotas.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.domain.formatTimeOfDay
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class DatePickerTarget {
    START,
    END,
}

private val PurplePrimary = Color(0xFF8E5BA4)
private val PurpleDark = Color(0xFF734B8E)
private val LavenderBg = Color(0xFFF3EAF6)
private val LavenderCardBg = Color(0xFFF7F4FA)
private val LavenderBorder = Color(0xFFE8E3EE)
private val LavenderCancelBg = Color(0xFFF3EEF6)
private val DragHandleColor = Color(0xFFD6CEDC)
private val TextDark = Color(0xFF1E1926)
private val TextMuted = Color(0xFF757082)
private val TextOutOfMonth = Color(0xFFBCB7C4)
private val FieldBackground = Color(0xFFF8F6FA)

/**
 * Redesigned "Set date" bottom sheet conforming strictly to the reference UI.
 *
 * Supports:
 * - Task and Event modes with clear subtitle and badge indicator.
 * - Start date field and Start time field.
 * - Monthly calendar grid with previous/next month navigation, filled purple selected day circle.
 * - For Events: collapsible "Add end date" section with checkbox, end date and time fields,
 *   quick duration presets ("1h", "1h30min", "2h", "1day", "2days"), and dynamic summary text.
 * - Cancel and Save date footer buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDateBottomSheet(
    initialDateMillis: Long,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSave: (dateMillis: Long, hour: Int, minute: Int, isEvent: Boolean, endDateMillis: Long?, endHour: Int?, endMinute: Int?) -> Unit,
    initialEndDateMillis: Long? = null,
    initialEndHour: Int? = null,
    initialEndMinute: Int? = null,
    initialIsEvent: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current

    // Convert initial UTC millis to local dates
    val initialStartDate =
        remember(initialDateMillis) {
            Instant.ofEpochMilli(initialDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        }
    val initialEndDate =
        remember(initialEndDateMillis, initialStartDate) {
            if (initialEndDateMillis != null) {
                Instant.ofEpochMilli(initialEndDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
            } else {
                initialStartDate
            }
        }

    var isEvent by rememberSaveable { mutableStateOf(initialIsEvent || initialEndDateMillis != null) }
    var hasEndDate by rememberSaveable { mutableStateOf(initialEndDateMillis != null) }

    var selectedStartDate by rememberSaveable { mutableStateOf(initialStartDate) }
    var selectedStartHour by rememberSaveable { mutableStateOf(initialHour) }
    var selectedStartMinute by rememberSaveable { mutableStateOf(initialMinute) }

    var selectedEndDate by rememberSaveable { mutableStateOf(initialEndDate) }
    var selectedEndHour by rememberSaveable {
        mutableStateOf(initialEndHour ?: (initialHour + 1).coerceAtMost(23))
    }
    var selectedEndMinute by rememberSaveable {
        mutableStateOf(initialEndMinute ?: ((initialMinute + 30) % 60))
    }

    var activeTarget by rememberSaveable { mutableStateOf(DatePickerTarget.START) }
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(initialStartDate)) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var selectedPreset by rememberSaveable { mutableStateOf<String?>(null) }

    // Date formatter for Start and End date fields (e.g. "Mon, Oct 19, 2026")
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(DragHandleColor),
                )
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            // ==================== 1. HEADER ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Set date",
                    style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                        ),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Type indicator badge ("Task" or "Event") - clickable to toggle mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LavenderBg,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isEvent = !isEvent
                                    if (isEvent) {
                                        hasEndDate = true
                                        if (selectedEndDate < selectedStartDate) {
                                            selectedEndDate = selectedStartDate
                                        }
                                        if (selectedPreset == null) {
                                            selectedPreset = "1h30min"
                                            val startLocalTime = LocalTime.of(selectedStartHour, selectedStartMinute)
                                            val end = startLocalTime.plusHours(1).plusMinutes(30)
                                            selectedEndHour = end.hour
                                            selectedEndMinute = end.minute
                                            selectedEndDate = selectedStartDate
                                        }
                                    } else {
                                        hasEndDate = false
                                        activeTarget = DatePickerTarget.START
                                    }
                                },
                    ) {
                        Text(
                            text = if (isEvent) "Event" else "Task",
                            style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PurplePrimary,
                                ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    // Close 'X' Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF5F3F8),
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDismiss),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            RememberMaterialRoundedSymbol(
                                name = "close",
                                size = 18.dp,
                                tint = Color(0xFF49454F),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle text matching reference
            Text(
                text =
                    if (isEvent) {
                        "An event has a start and an end."
                    } else {
                        "A task only needs a start date."
                    },
                style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 13.5.sp,
                        color = TextMuted,
                    ),
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ==================== 2. START DATE & TIME FIELDS ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Start Date Field
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Start date",
                        style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted,
                            ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FieldBackground,
                        border =
                            BorderStroke(
                                width = 1.dp,
                                color = if (activeTarget == DatePickerTarget.START) PurplePrimary else LavenderBorder,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    activeTarget = DatePickerTarget.START
                                    displayedMonth = YearMonth.from(selectedStartDate)
                                },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "calendar_month",
                                    size = 18.dp,
                                    tint = PurplePrimary,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedStartDate.format(dateFormatter),
                                    style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            RememberMaterialRoundedSymbol(
                                name = "keyboard_arrow_down",
                                size = 18.dp,
                                tint = TextMuted,
                            )
                        }
                    }
                }

                // Start Time Field
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Start time",
                        style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted,
                            ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FieldBackground,
                        border = BorderStroke(1.dp, LavenderBorder),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showStartTimePicker = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "schedule",
                                    size = 18.dp,
                                    tint = PurplePrimary,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatTimeOfDay(context, selectedStartHour, selectedStartMinute),
                                    style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark,
                                        ),
                                    maxLines = 1,
                                )
                            }
                            RememberMaterialRoundedSymbol(
                                name = "keyboard_arrow_down",
                                size = 18.dp,
                                tint = TextMuted,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== 3. MONTHLY CALENDAR ====================
            // Month & Navigation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val monthTitleFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
                Text(
                    text = displayedMonth.atDay(1).format(monthTitleFormatter),
                    style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                        ),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            modifier = Modifier.graphicsLayer { scaleX = -1f },
                            size = 22.dp,
                            tint = Color(0xFF49454F),
                        )
                    }

                    IconButton(
                        onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            size = 22.dp,
                            tint = Color(0xFF49454F),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday Headings (S M T W T F S)
            val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekdayLetters.forEach { letter ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = letter,
                            style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted,
                                ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Calendar Days Grid (Sunday-based, 7 columns, up to 6 rows)
            val calendarDays =
                remember(displayedMonth) {
                    computeMonthGridDaysSundayFirst(displayedMonth)
                }

            val targetDate = if (activeTarget == DatePickerTarget.START) selectedStartDate else selectedEndDate

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                calendarDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        week.forEach { date ->
                            val isSelected = (date == targetDate)
                            val isCurrentMonth = YearMonth.from(date) == displayedMonth

                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (activeTarget == DatePickerTarget.START) {
                                                selectedStartDate = date
                                                if (hasEndDate && selectedEndDate < date) {
                                                    selectedEndDate = date
                                                }
                                            } else {
                                                if (date >= selectedStartDate) {
                                                    selectedEndDate = date
                                                } else {
                                                    selectedStartDate = date
                                                    selectedEndDate = date
                                                }
                                            }
                                            selectedPreset = null
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) PurplePrimary else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style =
                                            androidx.compose.ui.text.TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color =
                                                    when {
                                                        isSelected -> Color.White
                                                        !isCurrentMonth -> TextOutOfMonth
                                                        else -> TextDark
                                                    },
                                            ),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== 4. ADD END DATE CONTROL (EVENT MODE) ====================
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LavenderCardBg,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                ) {
                    // Header / Toggle Row
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    hasEndDate = !hasEndDate
                                    if (hasEndDate) {
                                        isEvent = true
                                        if (selectedEndDate < selectedStartDate) {
                                            selectedEndDate = selectedStartDate
                                        }
                                        activeTarget = DatePickerTarget.END
                                        displayedMonth = YearMonth.from(selectedEndDate)
                                        if (selectedPreset == null) {
                                            selectedPreset = "1h30min"
                                            val startLocalTime = LocalTime.of(selectedStartHour, selectedStartMinute)
                                            val end = startLocalTime.plusHours(1).plusMinutes(30)
                                            selectedEndHour = end.hour
                                            selectedEndMinute = end.minute
                                            selectedEndDate = selectedStartDate
                                        }
                                    } else {
                                        isEvent = false
                                        activeTarget = DatePickerTarget.START
                                        selectedPreset = null
                                    }
                                },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            // Checkbox icon
                            Box(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (hasEndDate) PurplePrimary else Color.Transparent)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (hasEndDate) PurplePrimary else TextOutOfMonth,
                                            shape = RoundedCornerShape(5.dp),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hasEndDate) {
                                    RememberMaterialRoundedSymbol(
                                        name = "check",
                                        size = 15.dp,
                                        tint = Color.White,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Add end date",
                                    style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark,
                                        ),
                                )
                                if (!hasEndDate) {
                                    Text(
                                        text = "Optional · add one to make an event",
                                        style =
                                            androidx.compose.ui.text.TextStyle(
                                                fontSize = 11.5.sp,
                                                color = TextMuted,
                                            ),
                                    )
                                }
                            }
                        }

                        RememberMaterialRoundedSymbol(
                            name = "keyboard_arrow_down",
                            modifier = if (hasEndDate) Modifier.graphicsLayer { scaleY = -1f } else Modifier,
                            size = 20.dp,
                            tint = TextMuted,
                        )
                    }

                    // Expanded Section: End Date & End Time Fields + Duration Presets
                    AnimatedVisibility(
                        visible = hasEndDate,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(14.dp))

                            // End Date and End Time Fields Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // End Date Field
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End date",
                                        style =
                                            androidx.compose.ui.text.TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextMuted,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = FieldBackground,
                                        border =
                                            BorderStroke(
                                                width = 1.dp,
                                                color = if (activeTarget == DatePickerTarget.END) PurplePrimary else LavenderBorder,
                                            ),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    activeTarget = DatePickerTarget.END
                                                    displayedMonth = YearMonth.from(selectedEndDate)
                                                },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                RememberMaterialRoundedSymbol(
                                                    name = "calendar_month",
                                                    size = 18.dp,
                                                    tint = PurplePrimary,
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = selectedEndDate.format(dateFormatter),
                                                    style =
                                                        androidx.compose.ui.text.TextStyle(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextDark,
                                                        ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            RememberMaterialRoundedSymbol(
                                                name = "keyboard_arrow_down",
                                                size = 18.dp,
                                                tint = TextMuted,
                                            )
                                        }
                                    }
                                }

                                // End Time Field
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End time",
                                        style =
                                            androidx.compose.ui.text.TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextMuted,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = FieldBackground,
                                        border = BorderStroke(1.dp, LavenderBorder),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { showEndTimePicker = true },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                RememberMaterialRoundedSymbol(
                                                    name = "schedule",
                                                    size = 18.dp,
                                                    tint = PurplePrimary,
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = formatTimeOfDay(context, selectedEndHour, selectedEndMinute),
                                                    style =
                                                        androidx.compose.ui.text.TextStyle(
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextDark,
                                                        ),
                                                    maxLines = 1,
                                                )
                                            }
                                            RememberMaterialRoundedSymbol(
                                                name = "keyboard_arrow_down",
                                                size = 18.dp,
                                                tint = TextMuted,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // "End after" Label & Duration Chips
                            Text(
                                text = "End after",
                                style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextMuted,
                                    ),
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val presets = listOf("1h", "1h30min", "2h", "1day", "2days")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                presets.forEach { preset ->
                                    val isSelected = (selectedPreset == preset)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) PurplePrimary else Color(0xFFF3EFF6),
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    selectedPreset = preset
                                                    val startLocalTime = LocalTime.of(selectedStartHour, selectedStartMinute)
                                                    when (preset) {
                                                        "1h" -> {
                                                            selectedEndDate = selectedStartDate
                                                            val end = startLocalTime.plusHours(1)
                                                            selectedEndHour = end.hour
                                                            selectedEndMinute = end.minute
                                                        }
                                                        "1h30min" -> {
                                                            selectedEndDate = selectedStartDate
                                                            val end = startLocalTime.plusHours(1).plusMinutes(30)
                                                            selectedEndHour = end.hour
                                                            selectedEndMinute = end.minute
                                                        }
                                                        "2h" -> {
                                                            selectedEndDate = selectedStartDate
                                                            val end = startLocalTime.plusHours(2)
                                                            selectedEndHour = end.hour
                                                            selectedEndMinute = end.minute
                                                        }
                                                        "1day" -> {
                                                            selectedEndDate = selectedStartDate.plusDays(1)
                                                            selectedEndHour = selectedStartHour
                                                            selectedEndMinute = selectedStartMinute
                                                        }
                                                        "2days" -> {
                                                            selectedEndDate = selectedStartDate.plusDays(2)
                                                            selectedEndHour = selectedStartHour
                                                            selectedEndMinute = selectedStartMinute
                                                        }
                                                    }
                                                },
                                    ) {
                                        Text(
                                            text = preset,
                                            style =
                                                androidx.compose.ui.text.TextStyle(
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xFF49454F),
                                                ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dynamic Duration Summary with clock icon
                            val summaryText =
                                remember(
                                    selectedStartDate,
                                    selectedEndDate,
                                    selectedStartHour,
                                    selectedStartMinute,
                                    selectedEndHour,
                                    selectedEndMinute,
                                ) {
                                    formatDurationSummary(
                                        startDate = selectedStartDate,
                                        endDate = selectedEndDate,
                                        startHour = selectedStartHour,
                                        startMinute = selectedStartMinute,
                                        endHour = selectedEndHour,
                                        endMinute = selectedEndMinute,
                                    )
                                }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "schedule",
                                    size = 14.dp,
                                    tint = PurplePrimary,
                                )
                                Text(
                                    text = summaryText,
                                    style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 5. FOOTER ACTIONS ====================
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cancel Button
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = LavenderCancelBg,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onDismiss),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel",
                            style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PurplePrimary,
                                ),
                        )
                    }
                }

                // Save Date Button
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = PurplePrimary,
                    modifier =
                        Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                val startUtcMillis =
                                    selectedStartDate
                                        .atStartOfDay(ZoneOffset.UTC)
                                        .toInstant()
                                        .toEpochMilli()
                                val endUtcMillis =
                                    if (isEvent && hasEndDate) {
                                        selectedEndDate
                                            .atStartOfDay(ZoneOffset.UTC)
                                            .toInstant()
                                            .toEpochMilli()
                                    } else {
                                        null
                                    }

                                onSave(
                                    startUtcMillis,
                                    selectedStartHour,
                                    selectedStartMinute,
                                    isEvent,
                                    endUtcMillis,
                                    if (isEvent && hasEndDate) selectedEndHour else null,
                                    if (isEvent && hasEndDate) selectedEndMinute else null,
                                )
                            },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "check",
                            size = 18.dp,
                            tint = Color.White,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save date",
                            style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                ),
                        )
                    }
                }
            }
        }
    }

    // ==================== TIME PICKER DIALOGS ====================
    if (showStartTimePicker) {
        ReminderTimePickerDialog(
            initialHour = selectedStartHour,
            initialMinute = selectedStartMinute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                selectedStartHour = h
                selectedStartMinute = m
                selectedPreset = null
                showStartTimePicker = false
            },
        )
    }

    if (showEndTimePicker) {
        ReminderTimePickerDialog(
            initialHour = selectedEndHour,
            initialMinute = selectedEndMinute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                selectedEndHour = h
                selectedEndMinute = m
                selectedPreset = null
                showEndTimePicker = false
            },
        )
    }
}

/**
 * Computes calendar dates for a month grid starting on Sunday.
 */
private fun computeMonthGridDaysSundayFirst(month: YearMonth): List<LocalDate> {
    val firstDayOfMonth = month.atDay(1)
    // Sunday is 7 in java.time DayOfWeek, we want Sunday = 0, Monday = 1 ... Saturday = 6
    val firstDayOfWeekIndex = firstDayOfMonth.dayOfWeek.value % 7
    val startDate = firstDayOfMonth.minusDays(firstDayOfWeekIndex.toLong())

    val dates = mutableListOf<LocalDate>()
    var curr = startDate
    // Render standard 35 or 42 cells grid
    val targetCellCount =
        if (firstDayOfWeekIndex + month.lengthOfMonth() > 35) 42 else 35

    while (dates.size < targetCellCount) {
        dates.add(curr)
        curr = curr.plusDays(1)
    }
    return dates
}

/**
 * Formats duration summary text matching reference (e.g. "1 hour 30 minutes · ends the same day").
 */
private fun formatDurationSummary(
    startDate: LocalDate,
    endDate: LocalDate,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
): String {
    val daysBetween = ChronoUnit.DAYS.between(startDate, endDate)
    val startTotalMinutes = startHour * 60 + startMinute
    val endTotalMinutes = endHour * 60 + endMinute

    return if (daysBetween == 0L) {
        val diffMinutes = endTotalMinutes - startTotalMinutes
        if (diffMinutes > 0) {
            val h = diffMinutes / 60
            val m = diffMinutes % 60
            val hStr = if (h > 0) "$h hour${if (h > 1) "s" else ""}" else ""
            val mStr = if (m > 0) "$m minute${if (m > 1) "s" else ""}" else ""
            val timeSpan = listOf(hStr, mStr).filter { it.isNotBlank() }.joinToString(" ")
            "$timeSpan · ends the same day"
        } else {
            "Ends the same day"
        }
    } else if (daysBetween == 1L) {
        "1 day later · ends tomorrow"
    } else {
        "$daysBetween days later"
    }
}
