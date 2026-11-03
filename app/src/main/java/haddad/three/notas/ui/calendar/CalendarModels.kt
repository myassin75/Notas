package haddad.three.notas.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import haddad.three.notas.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * View modes supported by the Calendar.
 */
enum class CalendarViewMode(
    @param:StringRes val labelRes: Int,
    val symbolName: String,
) {
    DAY(R.string.calendar_view_day, "event"),
    WEEK(R.string.calendar_view_week, "calendar_clock"),
    MONTH(R.string.calendar_view_month, "calendar_month"),
    YEAR(R.string.calendar_view_year, "grid_view"),
}

/**
 * Visual rendering state for an individual day cell in a month or week view.
 */
enum class CalendarDayState {
    NORMAL,
    SELECTED,
    TODAY,
    OUT_OF_MONTH,
    DISABLED,
}

/**
 * Indicator dot rendered below day cell numbers to represent scheduled items.
 */
data class CalendarDot(
    val color: Color,
)

/**
 * UI model representing a day cell in the calendar grid.
 */
data class CalendarDayUiModel(
    val date: LocalDate,
    val dayNumber: Int,
    val state: CalendarDayState = CalendarDayState.NORMAL,
    val dots: List<CalendarDot> = emptyList(),
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
)

/**
 * UI model representing a day in the 7-day week view selector and agenda.
 */
data class CalendarWeekDayUiModel(
    val date: LocalDate,
    val dayOfWeekLetter: String,
    val dayNumber: Int,
    val dayShortName: String,
    val fullDateLabel: String,
    val state: CalendarDayState = CalendarDayState.NORMAL,
    val dots: List<CalendarDot> = emptyList(),
    val events: List<CalendarEventUiModel> = emptyList(),
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
)

/**
 * State holder for week navigation transitions.
 */
data class CalendarWeekViewState(
    val weekStart: LocalDate,
    val weekDays: List<CalendarWeekDayUiModel>,
)

/**
 * UI model representing an event or task card in the agenda or day timeline.
 */
data class CalendarEventUiModel(
    val id: Long,
    val title: String,
    val subtitle: String,
    val timeDisplay: String,
    val iconName: String = "edit",
    val iconBgColor: Color = CalendarDesignTokens.CategoryPurpleBg,
    val iconTint: Color = CalendarDesignTokens.CategoryPurpleIcon,
    val isDone: Boolean = false,
    val isTask: Boolean = false,
    val description: String = "",
    val isAllDay: Boolean = false,
    val shoulderName: String? = null,
    val shoulderColorHex: String? = null,
)

/**
 * UI model representing a birthday item.
 */
data class CalendarBirthdayUiModel(
    val id: Long,
    val name: String,
    val subtitle: String,
    val iconName: String = "cake",
)

/**
 * Types of items that can appear on the day timeline.
 */
enum class TimelineItemType {
    EVENT,
    TASK,
    ALL_DAY,
}

/**
 * UI model representing an item plotted on the daily timeline.
 */
data class CalendarTimelineItemUiModel(
    val id: Long,
    val title: String,
    val subtitle: String,
    val timeRangeDisplay: String,
    val type: TimelineItemType,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val iconName: String = "edit",
    val accentColor: Color = CalendarDesignTokens.PrimaryPurple,
    val containerBgColor: Color = CalendarDesignTokens.CategoryPurpleBg,
    val isDone: Boolean = false,
    val isCrossMidnightStart: Boolean = false,
    val isCrossMidnightEnd: Boolean = false,
    val colIndex: Int = 0,
    val totalCols: Int = 1,
)

/**
 * UI state for the Calendar screen.
 */
data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val currentWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weekRangeTitle: String = "",
    val weekYearLabel: String = "",
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val monthDays: List<CalendarDayUiModel> = emptyList(),
    val weekDays: List<CalendarWeekDayUiModel> = emptyList(),
    val selectedDayEvents: List<CalendarEventUiModel> = emptyList(),
    val selectedDayTasks: List<CalendarEventUiModel> = emptyList(),
    val selectedDayBirthdays: List<CalendarBirthdayUiModel> = emptyList(),
    val dayTimelineItems: List<CalendarTimelineItemUiModel> = emptyList(),
    val dayAllDayItems: List<CalendarTimelineItemUiModel> = emptyList(),
    val weekTotalEventsCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
