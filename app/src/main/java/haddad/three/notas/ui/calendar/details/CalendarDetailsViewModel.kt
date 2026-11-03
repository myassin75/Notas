package haddad.three.notas.ui.calendar.details

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import haddad.three.notas.data.ActionType
import haddad.three.notas.data.ChecklistItemEntity
import haddad.three.notas.data.MonthlyMode
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.RecurrenceRule
import haddad.three.notas.data.RecurrenceUnit
import haddad.three.notas.data.getActiveReminders
import haddad.three.notas.ui.components.parseColorHexOrNull
import haddad.three.notas.ui.event.EditEventViewModel
import haddad.three.notas.ui.nav.Routes
import haddad.three.notas.ui.theme.NoteColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class CalendarDetailsUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val id: Long = -1L,
    val isTask: Boolean = false,
    val title: String = "",
    val description: String = "",
    val dateText: String = "",
    val weekdayText: String = "",
    val accentColor: Color = Color.Unspecified,
    val isBirthday: Boolean = false,
    val iconKey: String? = null,
    val colorIndex: Int = 0,
    val isAllDay: Boolean = false,
    val startTimeText: String? = null,
    val endTimeText: String? = null,
    val durationText: String? = null,
    val taskScheduledTimeText: String? = null,
    val recurrencePrimary: String? = null,
    val recurrenceSecondary: String? = null,
    val isDone: Boolean = false,
    val checklistSummary: String? = null,
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
    val reminderSummary: String? = null,
    val locationSummary: String? = null,
    val shoulderName: String? = null,
    val shoulderColor: Color? = null,
    val rawNote: NoteWithItems? = null,
)

@HiltViewModel
class CalendarDetailsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val noteRepository: NoteRepository,
    ) : ViewModel() {
        private val itemId: Long = savedStateHandle.get<Long>(Routes.ARG_ITEM_ID) ?: -1L
        private val isTaskParam: Boolean = savedStateHandle.get<Boolean>(Routes.ARG_IS_TASK) ?: false
        private val dateMillisParam: Long? = savedStateHandle.get<Long>(Routes.ARG_DATE_MILLIS)?.takeIf { it > 0L }

        val uiState: StateFlow<CalendarDetailsUiState> =
            if (itemId <= 0L) {
                MutableStateFlow(CalendarDetailsUiState(isLoading = false, notFound = true))
            } else {
                noteRepository
                    .observe(itemId)
                    .map { noteWithItems ->
                        if (noteWithItems == null) {
                            CalendarDetailsUiState(isLoading = false, notFound = true)
                        } else {
                            mapToDetailsUiState(noteWithItems, isTaskParam, dateMillisParam)
                        }
                    }.stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.WhileSubscribed(5_000),
                        initialValue = CalendarDetailsUiState(isLoading = true),
                    )
            }

        fun toggleTaskCompletion() {
            val note = uiState.value.rawNote ?: return
            viewModelScope.launch {
                if (note.note.completedAt != null) {
                    noteRepository.markIncomplete(note.note.id)
                } else {
                    noteRepository.markCompleted(note.note.id)
                }
            }
        }

        private fun mapToDetailsUiState(
            noteWithItems: NoteWithItems,
            requestedAsTask: Boolean,
            targetDateMillis: Long?,
        ): CalendarDetailsUiState {
            val note = noteWithItems.note
            val isTask = requestedAsTask || note.kind == NoteKind.LIST || note.checklistText.isNotBlank()
            val isBirthday = isBirthdayItem(noteWithItems)

            val locale = Locale.getDefault()

            // 1. Target Date for Header
            val targetDate =
                when {
                    targetDateMillis != null && targetDateMillis > 0L -> {
                        Instant.ofEpochMilli(targetDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    note.reminderAt != null && note.reminderAt > 0L -> {
                        Instant.ofEpochMilli(note.reminderAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    note.getActiveReminders().isNotEmpty() -> {
                        val soonest = note.getActiveReminders().minByOrNull { it.reminderAt }?.reminderAt
                        if (soonest != null && soonest > 0L) {
                            Instant.ofEpochMilli(soonest).atZone(ZoneId.systemDefault()).toLocalDate()
                        } else {
                            LocalDate.now()
                        }
                    }
                    else -> {
                        LocalDate.now()
                    }
                }

            val datePattern = DateTimeFormatter.ofPattern("MMMM d", locale)
            val weekdayPattern = DateTimeFormatter.ofPattern("EEEE", locale)
            val dateText = targetDate.format(datePattern)
            val weekdayText = targetDate.format(weekdayPattern)

            // 2. Accent Color
            val accentColor =
                when {
                    !NoteColors.isNone(note.colorIndex) -> NoteColors.at(note.colorIndex).light
                    !noteWithItems.shoulder?.colorHex.isNullOrBlank() -> {
                        parseColorHexOrNull(noteWithItems.shoulder.colorHex) ?: Color.Unspecified
                    }
                    else -> Color.Unspecified
                }

            // 3. Title & Description
            val title =
                note.title.ifBlank {
                    note.body
                        .lineSequence()
                        .firstOrNull()
                        ?.take(40) ?: "Untitled"
                }

            val (isAllDay, startTime, endTime, parsedDescription) =
                if (isTask) {
                    ParsedBodyResult(
                        isAllDay = false,
                        startTime = null,
                        endTime = null,
                        description = note.body.trim(),
                    )
                } else if (note.endAt != null || note.isAllDay) {
                    val start =
                        if (!note.isAllDay && note.reminderAt != null && note.reminderAt > 0L) {
                            Instant.ofEpochMilli(note.reminderAt).atZone(ZoneId.systemDefault()).toLocalTime()
                        } else {
                            null
                        }
                    val end =
                        if (!note.isAllDay && note.endAt != null && note.endAt > 0L) {
                            Instant.ofEpochMilli(note.endAt).atZone(ZoneId.systemDefault()).toLocalTime()
                        } else {
                            null
                        }
                    ParsedBodyResult(
                        isAllDay = note.isAllDay,
                        startTime = start,
                        endTime = end,
                        description = EditEventViewModel.cleanLegacyTimeFromBody(note.body),
                    )
                } else {
                    parseEventBody(note.body, note.reminderAt, isTask)
                }

            // 4. Time range & duration
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
            val startTimeText = startTime?.format(timeFormatter)
            val endTimeText = endTime?.format(timeFormatter)
            val durationText =
                if (startTime != null && endTime != null) {
                    val startMin = startTime.hour * 60 + startTime.minute
                    val endMin = endTime.hour * 60 + endTime.minute
                    formatDuration(startMin, endMin)
                } else {
                    null
                }

            val taskScheduledTimeText =
                if (isTask && note.reminderAt != null) {
                    val localTime = Instant.ofEpochMilli(note.reminderAt).atZone(ZoneId.systemDefault()).toLocalTime()
                    localTime.format(timeFormatter)
                } else {
                    null
                }

            // 5. Recurrence
            val rule = note.recurrence ?: note.getActiveReminders().firstOrNull()?.recurrence
            val (recurrencePrimary, recurrenceSecondary) = formatRecurrenceRule(rule, targetDate, locale)

            // 6. Checklist
            val checklistSummary =
                if (noteWithItems.items.isNotEmpty()) {
                    val total = noteWithItems.items.size
                    val done = noteWithItems.items.count { it.checked }
                    "$done of $total completed"
                } else {
                    null
                }

            // 7. Reminders
            val activeReminders = note.getActiveReminders()
            val reminderSummary =
                if (activeReminders.isNotEmpty()) {
                    val soonest = activeReminders.minByOrNull { it.reminderAt }
                    if (soonest != null) {
                        val reminderTime = Instant.ofEpochMilli(soonest.reminderAt).atZone(ZoneId.systemDefault()).toLocalTime()
                        val extra = if (activeReminders.size > 1) " (+${activeReminders.size - 1} more)" else ""
                        "${reminderTime.format(timeFormatter)}$extra"
                    } else {
                        null
                    }
                } else {
                    null
                }

            // 8. Location Action
            val locationAction = note.actions.firstOrNull { it.type == ActionType.GET_DIRECTIONS }
            val locationSummary =
                locationAction?.let {
                    it.details.ifBlank { it.title }
                }

            // 9. Shoulder
            val shoulderName = noteWithItems.shoulder?.name
            val shoulderColor = parseColorHexOrNull(noteWithItems.shoulder?.colorHex)

            return CalendarDetailsUiState(
                isLoading = false,
                notFound = false,
                id = note.id,
                isTask = isTask,
                title = title,
                description = parsedDescription,
                dateText = dateText,
                weekdayText = weekdayText,
                accentColor = accentColor,
                isBirthday = isBirthday,
                iconKey = note.iconKey,
                colorIndex = note.colorIndex,
                isAllDay = isAllDay,
                startTimeText = startTimeText,
                endTimeText = endTimeText,
                durationText = durationText,
                taskScheduledTimeText = taskScheduledTimeText,
                recurrencePrimary = recurrencePrimary,
                recurrenceSecondary = recurrenceSecondary,
                isDone = note.completedAt != null,
                checklistSummary = checklistSummary,
                checklistItems = noteWithItems.items,
                reminderSummary = reminderSummary,
                locationSummary = locationSummary,
                shoulderName = shoulderName,
                shoulderColor = shoulderColor,
                rawNote = noteWithItems,
            )
        }

        private fun isBirthdayItem(note: NoteWithItems): Boolean {
            val shoulderName = note.shoulder?.name?.lowercase(Locale.getDefault()) ?: ""
            val title = note.note.title.lowercase(Locale.getDefault())
            val iconKey = note.note.iconKey?.lowercase(Locale.getDefault()) ?: ""
            return shoulderName.contains("birthday") ||
                title.contains("birthday") ||
                iconKey.contains("cake")
        }

        private data class ParsedBodyResult(
            val isAllDay: Boolean,
            val startTime: LocalTime?,
            val endTime: LocalTime?,
            val description: String,
        )

        private fun parseEventBody(
            body: String,
            reminderAt: Long?,
            isTask: Boolean,
        ): ParsedBodyResult {
            val trimmed = body.trim()
            if (isTask) {
                return ParsedBodyResult(
                    isAllDay = false,
                    startTime = null,
                    endTime = null,
                    description = trimmed,
                )
            }

            if (trimmed.contains("[All Day]", ignoreCase = true)) {
                val desc = trimmed.replace("[All Day]", "").trim()
                return ParsedBodyResult(
                    isAllDay = true,
                    startTime = null,
                    endTime = null,
                    description = desc,
                )
            }

            val lines = trimmed.lines()
            val firstLine = lines.firstOrNull()?.trim().orEmpty()
            val rangeRegex =
                Regex(
                    """^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*[-–—]\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""",
                    RegexOption.IGNORE_CASE,
                )
            val match = rangeRegex.find(firstLine)
            if (match != null) {
                val (h1Str, m1Str, ampm1, h2Str, m2Str, ampm2) = match.destructured
                var h1 = h1Str.toIntOrNull() ?: 9
                val m1 = m1Str.toIntOrNull() ?: 0
                var h2 = h2Str.toIntOrNull() ?: 10
                val m2 = m2Str.toIntOrNull() ?: 0

                val p2 = ampm2.lowercase(Locale.getDefault())
                val p1 = if (ampm1.isNotBlank()) ampm1.lowercase(Locale.getDefault()) else p2

                if (p1 == "pm" && h1 < 12) h1 += 12
                if (p1 == "am" && h1 == 12) h1 = 0
                if (p2 == "pm" && h2 < 12) h2 += 12
                if (p2 == "am" && h2 == 12) h2 = 0

                val start = LocalTime.of(h1.coerceIn(0, 23), m1.coerceIn(0, 59))
                val end = LocalTime.of(h2.coerceIn(0, 23), m2.coerceIn(0, 59))
                val remainingDesc = lines.drop(1).joinToString("\n").trim()
                return ParsedBodyResult(
                    isAllDay = false,
                    startTime = start,
                    endTime = end,
                    description = remainingDesc,
                )
            }

            if (reminderAt != null && reminderAt > 0L) {
                val start = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault()).toLocalTime()
                val durationMinutes = parseDurationMinutes(body) ?: 60
                val end = start.plusMinutes(durationMinutes.toLong())
                return ParsedBodyResult(
                    isAllDay = false,
                    startTime = start,
                    endTime = end,
                    description = trimmed,
                )
            }

            return ParsedBodyResult(
                isAllDay = false,
                startTime = null,
                endTime = null,
                description = trimmed,
            )
        }

        private fun parseDurationMinutes(text: String): Int? {
            val minRegex = Regex("""(\d+)\s*(?:min|mins|minutes|m)\b""", RegexOption.IGNORE_CASE)
            minRegex.find(text)?.let { match ->
                return match.groupValues[1].toIntOrNull()?.coerceIn(5, 1440)
            }

            val hourRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:hour|hours|hr|hrs|h)\b""", RegexOption.IGNORE_CASE)
            hourRegex.find(text)?.let { match ->
                val hours = match.groupValues[1].toDoubleOrNull() ?: return null
                return (hours * 60).toInt().coerceIn(5, 1440)
            }

            return null
        }

        private fun formatDuration(
            startMinutes: Int,
            endMinutes: Int,
        ): String {
            val totalMinutes =
                if (endMinutes >= startMinutes) {
                    endMinutes - startMinutes
                } else {
                    (1440 - startMinutes) + endMinutes
                }

            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return when {
                hours > 0 && mins > 0 -> "$hours hr${if (hours > 1) "s" else ""} $mins min${if (mins > 1) "s" else ""}"
                hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
                mins > 0 -> "$mins mins"
                else -> "0 mins"
            }
        }

        private fun formatRecurrenceRule(
            rule: RecurrenceRule?,
            targetDate: LocalDate,
            locale: Locale,
        ): Pair<String?, String?> {
            if (rule == null) return null to null

            val interval = rule.interval.coerceAtLeast(1)
            val dayName = targetDate.format(DateTimeFormatter.ofPattern("EEEE", locale))

            return when (rule.unit) {
                RecurrenceUnit.HOUR -> {
                    if (interval == 1) "Every hour" to "" else "Every $interval hours" to ""
                }
                RecurrenceUnit.DAY -> {
                    if (interval == 1) "Every day" to "" else "Every $interval days" to ""
                }
                RecurrenceUnit.WEEK -> {
                    val primary = if (interval == 1) "Every week" else "Every $interval weeks"
                    primary to "on $dayName"
                }
                RecurrenceUnit.MONTH -> {
                    val primary = if (interval == 1) "Every month" else "Every $interval months"
                    val secondary =
                        when (val mode = rule.monthlyMode) {
                            null -> "on day ${targetDate.dayOfMonth}"
                            is MonthlyMode.ByDayOfMonth -> "on day ${mode.day}"
                            is MonthlyMode.ByNthWeekday -> "on the ${ordinalSuffix(mode.ordinal)} $dayName"
                        }
                    primary to secondary
                }
                RecurrenceUnit.YEAR -> {
                    val primary = if (interval == 1) "Every year" else "Every $interval years"
                    primary to "on $dayName"
                }
            }
        }

        private fun ordinalSuffix(n: Int): String =
            when (n) {
                1 -> "first"
                2 -> "second"
                3 -> "third"
                4 -> "fourth"
                5 -> "last"
                else -> "${n}th"
            }
    }
