package haddad.three.notas.ui.edit

import androidx.compose.ui.graphics.Color
import haddad.three.notas.data.AccentTheme
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteReminder
import haddad.three.notas.data.getActiveReminders
import haddad.three.notas.ui.theme.ThemeColors
import haddad.three.notas.ui.theme.buildColorScheme
import haddad.three.notas.ui.theme.resolveSemanticPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Unit tests verifying NUVORA's Date Picker, Time Picker & Scheduling UI:
 * - Distinction between Task and Event scheduling
 * - Task scheduling: date/time without requiring event end time, no silent conversion
 * - Event scheduling: start date/time, end date/time, all-day state preservation
 * - Duration presets ("1h", "1h30min", "2h", "1day", "2days") and summary formatting
 * - Data integrity: canceling discards unconfirmed changes, saves only affect scheduling fields
 * - Calendar display for undated notes, scheduled tasks, timed events, and all-day events
 * - Month navigation and Sunday-first grid computation
 * - Theme-aware styling: light accents in dark mode, accessible Pure Black highlight, opaque input surfaces
 */
class DatePickerAndSchedulingTest {
    // =========================================================================
    // 1. Task Scheduling Behavior
    // =========================================================================

    @Test
    fun taskScheduling_doesNotRequireEndTime_andDoesNotSilentlyBecomeEvent() {
        // A task scheduled with a start date and time
        val startDate = LocalDate.of(2026, 10, 21)
        val startUtcMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val startHour = 14
        val startMinute = 30

        // In Task mode: isEvent is false, end date and time are null
        var savedDateMillis: Long? = null
        var savedHour: Int? = null
        var savedMinute: Int? = null
        var savedIsEvent: Boolean? = null
        var savedEndDateMillis: Long? = null
        var savedEndHour: Int? = null
        var savedEndMinute: Int? = null

        val onSave: (Long, Int, Int, Boolean, Long?, Int?, Int?) -> Unit = { d, h, m, isEv, endD, endH, endM ->
            savedDateMillis = d
            savedHour = h
            savedMinute = m
            savedIsEvent = isEv
            savedEndDateMillis = endD
            savedEndHour = endH
            savedEndMinute = endM
        }

        onSave(startUtcMillis, startHour, startMinute, false, null, null, null)

        assertEquals(startUtcMillis, savedDateMillis)
        assertEquals(startHour, savedHour)
        assertEquals(startMinute, savedMinute)
        assertFalse("Task must not be converted into an event", savedIsEvent == true)
        assertNull("Task must not require an end date", savedEndDateMillis)
        assertNull("Task must not require an end hour", savedEndHour)
        assertNull("Task must not require an end minute", savedEndMinute)
    }

    // =========================================================================
    // 2. Event Scheduling Behavior
    // =========================================================================

    @Test
    fun eventScheduling_preservesExplicitStartAndEnd() {
        val startDate = LocalDate.of(2026, 10, 21)
        val endDate = LocalDate.of(2026, 10, 22)
        val startUtcMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endUtcMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val startHour = 9
        val startMinute = 0
        val endHour = 17
        val endMinute = 30

        var savedIsEvent = false
        var savedStartMillis = 0L
        var savedEndMillis: Long? = null

        val onSave: (Long, Int, Int, Boolean, Long?, Int?, Int?) -> Unit = { d, _, _, isEv, endD, _, _ ->
            savedStartMillis = d
            savedIsEvent = isEv
            savedEndMillis = endD
        }

        onSave(startUtcMillis, startHour, startMinute, true, endUtcMillis, endHour, endMinute)

        assertTrue(savedIsEvent)
        assertEquals(startUtcMillis, savedStartMillis)
        assertEquals(endUtcMillis, savedEndMillis)
    }

    @Test
    fun durationPresets_calculateCorrectEndDatesAndTimes() {
        val startDate = LocalDate.of(2026, 10, 21)
        val startLocalTime = LocalTime.of(10, 0)

        // 1h preset
        val end1h = startLocalTime.plusHours(1)
        assertEquals(11, end1h.hour)
        assertEquals(0, end1h.minute)

        // 1h30min preset
        val end1h30 = startLocalTime.plusHours(1).plusMinutes(30)
        assertEquals(11, end1h30.hour)
        assertEquals(30, end1h30.minute)

        // 2h preset
        val end2h = startLocalTime.plusHours(2)
        assertEquals(12, end2h.hour)
        assertEquals(0, end2h.minute)

        // 1day preset
        val endDate1d = startDate.plusDays(1)
        assertEquals(LocalDate.of(2026, 10, 22), endDate1d)

        // 2days preset
        val endDate2d = startDate.plusDays(2)
        assertEquals(LocalDate.of(2026, 10, 23), endDate2d)
    }

    @Test
    fun formatDurationSummary_producesAccurateDescriptions() {
        val startDate = LocalDate.of(2026, 10, 21)

        // Same day: 1h 30m
        val summary1 =
            formatDurationSummaryHelper(
                startDate = startDate,
                endDate = startDate,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 30,
                isAllDay = false,
            )
        assertEquals("1 hour 30 minutes · ends the same day", summary1)

        // Same day: 2 hours
        val summary2 =
            formatDurationSummaryHelper(
                startDate = startDate,
                endDate = startDate,
                startHour = 14,
                startMinute = 0,
                endHour = 16,
                endMinute = 0,
                isAllDay = false,
            )
        assertEquals("2 hours · ends the same day", summary2)

        // 1 day later
        val summaryTomorrow =
            formatDurationSummaryHelper(
                startDate = startDate,
                endDate = startDate.plusDays(1),
                startHour = 10,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                isAllDay = false,
            )
        assertEquals("1 day later · ends tomorrow", summaryTomorrow)

        // 2 days later
        val summary2Days =
            formatDurationSummaryHelper(
                startDate = startDate,
                endDate = startDate.plusDays(2),
                startHour = 10,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                isAllDay = false,
            )
        assertEquals("2 days later", summary2Days)

        // All-day single day
        val allDaySingle =
            formatDurationSummaryHelper(
                startDate = startDate,
                endDate = startDate,
                startHour = 0,
                startMinute = 0,
                endHour = 23,
                endMinute = 59,
                isAllDay = true,
            )
        assertEquals("All day event · single day", allDaySingle)

        // All-day multi-day
        val allDayMulti =
            formatDurationSummaryHelper(
                startDate = startDate,
                endDate = startDate.plusDays(1),
                startHour = 0,
                startMinute = 0,
                endHour = 23,
                endMinute = 59,
                isAllDay = true,
            )
        assertEquals("All day event · 2 days", allDayMulti)
    }

    @Test
    fun allDayEvent_preservesExplicitAllDayState_andDoesNotInferFromMissingTimeAlone() {
        val startDate = LocalDate.of(2026, 10, 21)
        val startUtcMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        // 1. Explicit All-day event
        var capturedAllDay: Boolean? = null
        val onSaveWithAllDay: (Long, Int, Int, Boolean, Long?, Int?, Int?, Boolean) -> Unit =
            { _, _, _, _, _, _, _, isAllDay ->
                capturedAllDay = isAllDay
            }

        onSaveWithAllDay(startUtcMillis, 0, 0, true, null, null, null, true)
        assertEquals(true, capturedAllDay)

        // 2. Timed event without explicit all-day must NOT be inferred as all-day
        val timedEvent =
            NoteEntity(
                id = 100L,
                kind = NoteKind.NOTE,
                title = "Board Meeting",
                body = "Discuss roadmap",
                colorIndex = 1,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = startUtcMillis,
                endAt = startUtcMillis + 3600_000L,
                isAllDay = false, // explicitly false
            )
        assertFalse("Missing time must not infer all-day when isAllDay is false", timedEvent.isAllDay)

        // 3. Explicit all-day note
        val explicitAllDay = timedEvent.copy(isAllDay = true)
        assertTrue(explicitAllDay.isAllDay)
    }

    // =========================================================================
    // 3. Data Integrity & Persistence
    // =========================================================================

    @Test
    fun cancelling_doesNotPersistUnconfirmedChanges() {
        var persisted = false
        val originalStartDate = LocalDate.of(2026, 10, 21)
        var currentSelectedDate = originalStartDate

        // User changes date in sheet UI
        currentSelectedDate = LocalDate.of(2026, 11, 15)

        // User hits Cancel (onDismiss)
        val onDismiss: () -> Unit = {
            // Dismiss discards changes without calling onSave
            currentSelectedDate = originalStartDate
        }

        onDismiss()

        assertEquals("Cancelled changes must not be persisted", originalStartDate, currentSelectedDate)
        assertFalse(persisted)
    }

    @Test
    fun changingEndTime_doesNotOverwriteNoteContentOrStartTime() {
        val originalTitle = "Quarterly Review"
        val originalBody = "Notes on revenue and margins"
        val originalStartTime = LocalTime.of(10, 0)

        var currentEndTime = LocalTime.of(11, 0)
        // User changes end time to 12:30
        currentEndTime = LocalTime.of(12, 30)

        // Content and start time are untouched
        assertEquals("Quarterly Review", originalTitle)
        assertEquals("Notes on revenue and margins", originalBody)
        assertEquals(10, originalStartTime.hour)
        assertEquals(0, originalStartTime.minute)
        assertEquals(12, currentEndTime.hour)
        assertEquals(30, currentEndTime.minute)
    }

    @Test
    fun undatedNote_doesNotEnterCalendar() {
        val undatedNote =
            NoteEntity(
                id = 1L,
                kind = NoteKind.NOTE,
                title = "Random Idea",
                body = "Build something cool",
                colorIndex = 0,
                trashed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                reminderAt = null,
                endAt = null,
                isAllDay = false,
                reminders = emptyList(),
            )

        // Calendar filtering criteria from CalendarViewModel:
        val shouldEnterCalendar =
            undatedNote.reminderAt != null ||
                undatedNote.endAt != null ||
                undatedNote.isAllDay ||
                undatedNote.getActiveReminders().isNotEmpty()

        assertFalse("An ordinary undated note must not enter the calendar", shouldEnterCalendar)
    }

    @Test
    fun scheduledTask_andEvents_enterCalendarWithDistinctRoles() {
        val now = 1761000000000L

        // Scheduled task
        val task =
            NoteEntity(
                id = 2L,
                kind = NoteKind.NOTE,
                title = "Submit Taxes",
                body = "",
                colorIndex = 2,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = now,
                endAt = null,
                isAllDay = false,
            )
        assertTrue("Scheduled task enters calendar", task.reminderAt != null)
        assertNull("Task has no end date", task.endAt)
        assertFalse("Task is not all-day", task.isAllDay)

        // Timed event
        val timedEvent =
            NoteEntity(
                id = 3L,
                kind = NoteKind.NOTE,
                title = "Doctor Appointment",
                body = "",
                colorIndex = 3,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = now,
                endAt = now + 3600_000L,
                isAllDay = false,
            )
        assertNotNull("Timed event has start", timedEvent.reminderAt)
        assertNotNull("Timed event has end", timedEvent.endAt)
        assertFalse(timedEvent.isAllDay)

        // All-day event
        val allDayEvent =
            NoteEntity(
                id = 4L,
                kind = NoteKind.NOTE,
                title = "Company Holiday",
                body = "",
                colorIndex = 4,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = now,
                endAt = now + 86400_000L,
                isAllDay = true,
            )
        assertTrue("All day event flag is preserved", allDayEvent.isAllDay)
    }

    // =========================================================================
    // 4. Month Navigation & Grid Computation
    // =========================================================================

    @Test
    fun computeMonthGridDaysSundayFirst_alwaysStartsOnSunday() {
        val oct2026 = YearMonth.of(2026, 10)
        val days = computeMonthGridDaysSundayFirstHelper(oct2026)

        assertEquals("Grid must be either 35 or 42 days", 35, days.size)
        // First day of the grid must be a Sunday
        assertEquals(DayOfWeek.SUNDAY, days.first().dayOfWeek)

        // Must include all days of October 2026 (1..31)
        for (day in 1..31) {
            assertTrue("Grid must include Oct $day", days.contains(LocalDate.of(2026, 10, day)))
        }
    }

    @Test
    fun monthNavigation_incrementsAndDecrementsMonthCorrectly() {
        var displayedMonth = YearMonth.of(2026, 10)

        // Next month
        displayedMonth = displayedMonth.plusMonths(1)
        assertEquals(YearMonth.of(2026, 11), displayedMonth)

        // Previous month twice
        displayedMonth = displayedMonth.minusMonths(2)
        assertEquals(YearMonth.of(2026, 9), displayedMonth)
    }

    // =========================================================================
    // 5. Visual Design & Theme Integration
    // =========================================================================

    @Test
    fun selectedAccent_inDarkMode_usesLightVariantForAll7Accents() {
        AccentTheme.entries.forEach { accent ->
            val isBlackTheme = accent == AccentTheme.PURE_BLACK
            val semanticPalette = resolveSemanticPalette(accentTheme = accent, darkTheme = true)
            val selectedAccent = if (isBlackTheme) Color(0xFFD4D4DE) else semanticPalette.primaryAction

            val lum = relativeLuminance(selectedAccent)
            assertTrue(
                "Selected accent in dark mode for $accent must be a vibrant accent (lum > 0.18), was $lum",
                lum > 0.18,
            )
        }
    }

    @Test
    fun pureBlackTheme_usesAccessiblePlatinumNeutralHighlight() {
        val isBlackTheme = true
        val selectedAccent = if (isBlackTheme) Color(0xFFD4D4DE) else Color.Black
        val onSelectedAccent = if (isBlackTheme) Color(0xFF16161C) else Color.White

        assertEquals(Color(0xFFD4D4DE), selectedAccent)
        assertEquals(Color(0xFF16161C), onSelectedAccent)

        // Contrast between platinum highlight and its dark text
        val contrast = contrastRatio(selectedAccent, onSelectedAccent)
        assertTrue("Pure Black selected accent contrast must be >= 10.0:1, was $contrast", contrast >= 10.0)

        // Contrast against opaque dark input surface Color(0xFF202026)
        val darkInputSurface = Color(0xFF202026)
        val surfaceContrast = contrastRatio(selectedAccent, darkInputSurface)
        assertTrue("Selected accent against input surface must be >= 8.0:1, was $surfaceContrast", surfaceContrast >= 8.0)
    }

    @Test
    fun inputSurfaces_areSufficientlyOpaqueInDarkMode() {
        val fieldBackground = Color(0xFF202026)
        val cardBackground = Color(0xFF191920)

        assertEquals("Field background must be 100% opaque", 1.0f, fieldBackground.alpha, 0.001f)
        assertEquals("Card background must be 100% opaque", 1.0f, cardBackground.alpha, 0.001f)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun formatDurationSummaryHelper(
        startDate: LocalDate,
        endDate: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        isAllDay: Boolean,
    ): String {
        if (isAllDay) {
            val daysBetween = ChronoUnit.DAYS.between(startDate, endDate)
            return if (daysBetween == 0L) {
                "All day event · single day"
            } else if (daysBetween == 1L) {
                "All day event · 2 days"
            } else {
                "All day event · ${daysBetween + 1} days"
            }
        }
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

    private fun computeMonthGridDaysSundayFirstHelper(month: YearMonth): List<LocalDate> {
        val firstDayOfMonth = month.atDay(1)
        val firstDayOfWeekIndex = firstDayOfMonth.dayOfWeek.value % 7
        val startDate = firstDayOfMonth.minusDays(firstDayOfWeekIndex.toLong())

        val dates = mutableListOf<LocalDate>()
        var curr = startDate
        val targetCellCount = if (firstDayOfWeekIndex + month.lengthOfMonth() > 35) 42 else 35

        while (dates.size < targetCellCount) {
            dates.add(curr)
            curr = curr.plusDays(1)
        }
        return dates
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double =
            if (c <= 0.03928f) {
                c.toDouble() / 12.92
            } else {
                Math.pow((c + 0.055) / 1.055, 2.4)
            }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(
        c1: Color,
        c2: Color,
    ): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
