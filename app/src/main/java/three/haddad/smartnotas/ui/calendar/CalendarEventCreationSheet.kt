package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.ui.common.AppBottomSheet
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.edit.CalendarPickerDialog
import three.haddad.smartnotas.ui.edit.ReminderTimePickerDialog
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom sheet allowing quick creation of events on the Calendar.
 * Supports date, start time, end time, cross-midnight duration, all-day toggle,
 * shoulder category assignment, description, and opening the full note editor.
 */
@Composable
fun CalendarEventCreationSheet(
    initialDate: LocalDate,
    availableShoulders: List<ShoulderEntity>,
    onDismiss: () -> Unit,
    onSaveEvent: suspend (
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        shoulderId: Long?,
        description: String,
    ) -> Result<Long>,
    onOpenFullEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val coroutineScope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableStateOf(initialDate) }
    var isAllDay by rememberSaveable { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var selectedShoulderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var description by rememberSaveable { mutableStateOf("") }

    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var generalError by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)

    val isOvernight = remember(startTime, endTime, isAllDay) {
        !isAllDay && endTime.isBefore(startTime)
    }
    val isSameTime = remember(startTime, endTime, isAllDay) {
        !isAllDay && startTime == endTime
    }

    AppBottomSheet(
        title = stringResource(R.string.calendar_create_event_title),
        onDismiss = onDismiss,
        containerColor = cardBg,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Event Title Field
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (titleError != null) titleError = null
                    if (generalError != null) generalError = null
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.calendar_event_title_placeholder),
                        color = CalendarDesignTokens.resolveTextMuted(isDark),
                    )
                },
                singleLine = true,
                isError = titleError != null,
                supportingText = titleError?.let {
                    { Text(text = it, color = MaterialTheme.colorScheme.error) }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CalendarDesignTokens.PrimaryPurple,
                    unfocusedBorderColor = cardBorder,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // 2. Date Selection Card
            val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()) }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) CalendarDesignTokens.CardBgDark else CalendarDesignTokens.ScreenBgLight,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showDatePicker = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "calendar_month",
                        size = 20.dp,
                        tint = CalendarDesignTokens.PrimaryPurple,
                    )
                    Text(
                        text = selectedDate.format(dateFormatter),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                        ),
                    )
                }
            }

            // 3. All-Day Switch Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "wb_sunny",
                        size = 20.dp,
                        tint = textSecondary,
                    )
                    Text(
                        text = stringResource(R.string.calendar_event_all_day),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                        ),
                    )
                }

                Switch(
                    checked = isAllDay,
                    onCheckedChange = { isAllDay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CalendarDesignTokens.PrimaryPurple,
                    ),
                )
            }

            // 4. Start & End Time Pickers (Visible when !isAllDay)
            if (!isAllDay) {
                val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Start Time Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) CalendarDesignTokens.CardBgDark else CalendarDesignTokens.ScreenBgLight,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showStartTimePicker = true },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.calendar_event_start_time),
                                style = MaterialTheme.typography.labelSmall.copy(color = textSecondary),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = startTime.format(timeFormatter),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                ),
                            )
                        }
                    }

                    // End Time Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) CalendarDesignTokens.CardBgDark else CalendarDesignTokens.ScreenBgLight,
                        border = BorderStroke(
                            1.dp,
                            if (isSameTime) MaterialTheme.colorScheme.error else cardBorder,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showEndTimePicker = true },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.calendar_event_end_time),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSameTime) MaterialTheme.colorScheme.error else textSecondary,
                                ),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = endTime.format(timeFormatter),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSameTime) MaterialTheme.colorScheme.error else textPrimary,
                                ),
                            )
                        }
                    }
                }

                // Cross-midnight badge or same-time error
                if (isOvernight) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CalendarDesignTokens.CategoryBlueBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "nightlight",
                                size = 16.dp,
                                tint = CalendarDesignTokens.CategoryBlueIcon,
                            )
                            Text(
                                text = stringResource(R.string.calendar_event_overnight),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = CalendarDesignTokens.CategoryBlueIcon,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                } else if (isSameTime) {
                    Text(
                        text = stringResource(R.string.calendar_event_same_time_error),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // 5. Shoulder Category Chips
            if (availableShoulders.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.calendar_event_category),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // "None" Chip
                        val isNoneSelected = selectedShoulderId == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNoneSelected) {
                                if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender
                            } else {
                                if (isDark) CalendarDesignTokens.CardBgDark else CalendarDesignTokens.ScreenBgLight
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isNoneSelected) CalendarDesignTokens.PrimaryPurple else cardBorder,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedShoulderId = null },
                        ) {
                            Text(
                                text = stringResource(R.string.calendar_event_category_none),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isNoneSelected) CalendarDesignTokens.PrimaryPurple else textSecondary,
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }

                        // Shoulder chips
                        availableShoulders.forEach { shoulder ->
                            val isSelected = selectedShoulderId == shoulder.id
                            val shoulderColor = parseHexColor(shoulder.colorHex)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    shoulderColor.copy(alpha = 0.15f)
                                } else {
                                    if (isDark) CalendarDesignTokens.CardBgDark else CalendarDesignTokens.ScreenBgLight
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) shoulderColor else cardBorder,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedShoulderId = shoulder.id },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(shoulderColor),
                                    )
                                    Text(
                                        text = shoulder.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) textPrimary else textSecondary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Description Field (Optional)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.calendar_event_description_placeholder),
                        color = CalendarDesignTokens.resolveTextMuted(isDark),
                    )
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CalendarDesignTokens.PrimaryPurple,
                    unfocusedBorderColor = cardBorder,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            // 7. General Error Message
            if (generalError != null) {
                Text(
                    text = generalError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // 8. Actions Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // "Open full editor" link
                TextButton(
                    onClick = {
                        val reminderEpochMillis = if (isAllDay) {
                            selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } else {
                            selectedDate.atTime(startTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        onDismiss()
                        onOpenFullEditor(reminderEpochMillis)
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "edit_note",
                            size = 18.dp,
                            tint = CalendarDesignTokens.PrimaryPurple,
                        )
                        Text(
                            text = stringResource(R.string.calendar_event_open_full_editor),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = CalendarDesignTokens.PrimaryPurple,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_event_cancel),
                            color = textSecondary,
                        )
                    }

                    val saveTitleRequiredMsg = stringResource(R.string.calendar_event_title_required)
                    val saveSameTimeMsg = stringResource(R.string.calendar_event_same_time_error)

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = saveTitleRequiredMsg
                                return@Button
                            }
                            if (isSameTime) {
                                generalError = saveSameTimeMsg
                                return@Button
                            }

                            isSaving = true
                            coroutineScope.launch {
                                val result = onSaveEvent(
                                    title.trim(),
                                    selectedDate,
                                    if (isAllDay) null else startTime,
                                    if (isAllDay) null else endTime,
                                    isAllDay,
                                    selectedShoulderId,
                                    description.trim(),
                                )
                                if (result.isSuccess) {
                                    onDismiss()
                                } else {
                                    isSaving = false
                                    generalError = result.exceptionOrNull()?.message ?: "Failed to save event"
                                }
                            }
                        },
                        enabled = !isSaving && title.isNotBlank() && !isSameTime,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CalendarDesignTokens.PrimaryPurple,
                            disabledContainerColor = CalendarDesignTokens.PrimaryPurple.copy(alpha = 0.4f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_event_save),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val initialUtcMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        CalendarPickerDialog(
            initial = initialUtcMillis,
            onConfirm = { pickedUtcMillis ->
                selectedDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // Start Time Picker Dialog
    if (showStartTimePicker) {
        ReminderTimePickerDialog(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                startTime = LocalTime.of(h, m)
                showStartTimePicker = false
            },
        )
    }

    // End Time Picker Dialog
    if (showEndTimePicker) {
        ReminderTimePickerDialog(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                endTime = LocalTime.of(h, m)
                showEndTimePicker = false
            },
        )
    }
}

private fun parseHexColor(colorHex: String?, fallback: Color = CalendarDesignTokens.PrimaryPurple): Color {
    if (colorHex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(if (colorHex.startsWith("#")) colorHex else "#$colorHex"))
    } catch (_: Exception) {
        fallback
    }
}
