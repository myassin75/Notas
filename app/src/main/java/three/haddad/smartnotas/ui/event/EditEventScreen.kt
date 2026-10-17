package three.haddad.smartnotas.ui.event

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.Importance
import three.haddad.smartnotas.data.NoteAttachmentEntity
import three.haddad.smartnotas.data.RecurrenceRule
import three.haddad.smartnotas.data.RecurrenceUnit
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.data.Visibility
import three.haddad.smartnotas.ui.calendar.CalendarDesignTokens
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.edit.ActionPicker
import three.haddad.smartnotas.ui.edit.AttachmentsSheet
import three.haddad.smartnotas.ui.edit.CalendarPickerDialog
import three.haddad.smartnotas.ui.edit.ReminderTimePickerDialog
import three.haddad.smartnotas.ui.edit.ShoulderPickerSheet
import three.haddad.smartnotas.ui.edit.rememberHeroImagePickThenCopy
import three.haddad.smartnotas.ui.theme.LocalIsDark
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.ui.edit.ICON_DRAWABLE_PREFIX
import three.haddad.smartnotas.ui.edit.ICON_EMOJI_PREFIX
import three.haddad.smartnotas.ui.edit.ICON_SYMBOL_FILLED_PREFIX
import three.haddad.smartnotas.ui.edit.ICON_SYMBOL_OUTLINED_PREFIX
import three.haddad.smartnotas.ui.edit.ICON_SYMBOL_PREFIX
import three.haddad.smartnotas.ui.edit.IconPicker
import three.haddad.smartnotas.ui.edit.NoteIcon
import three.haddad.smartnotas.ui.edit.humanizeIconKey
import three.haddad.smartnotas.ui.edit.iconDrawableRes
import three.haddad.smartnotas.ui.edit.iconEmojiPayload
import three.haddad.smartnotas.ui.edit.iconLabelFor
import three.haddad.smartnotas.ui.edit.resolveNoteIcon
import three.haddad.smartnotas.ui.theme.NoteColors
import three.haddad.smartnotas.domain.formatTimeOfDay
import three.haddad.smartnotas.ui.edit.ReminderPickerSheet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EditEventScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditEventViewModel = hiltViewModel(),
) {
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val title by viewModel.title.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val startTime by viewModel.startTime.collectAsStateWithLifecycle()
    val endTime by viewModel.endTime.collectAsStateWithLifecycle()
    val isAllDay by viewModel.isAllDay.collectAsStateWithLifecycle()
    val recurrence by viewModel.recurrence.collectAsStateWithLifecycle()
    val selectedShoulderId by viewModel.selectedShoulderId.collectAsStateWithLifecycle()
    val selectedShoulder by viewModel.selectedShoulder.collectAsStateWithLifecycle()
    val availableShoulders by viewModel.availableShoulders.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val reminderAtStart by viewModel.reminderAtStart.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val visibility by viewModel.visibility.collectAsStateWithLifecycle()
    val importance by viewModel.importance.collectAsStateWithLifecycle()
    val pictureUri by viewModel.pictureUri.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val colorIndex by viewModel.colorIndex.collectAsStateWithLifecycle()
    val iconKey by viewModel.iconKey.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }
    var showShoulderPicker by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showIconColorSheet by remember { mutableStateOf(false) }
    var showFullIconPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var showVisibilityDialog by remember { mutableStateOf(false) }
    var showImportanceDialog by remember { mutableStateOf(false) }
    var showAttachmentsSheet by remember { mutableStateOf(false) }

    val heroPicker = rememberHeroImagePickThenCopy(
        onImageReady = { uriString, _ ->
            viewModel.setPictureUri(uriString)
        },
    )

    val screenBg = CalendarDesignTokens.resolveScreenBg(isDark)
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val textMuted = CalendarDesignTokens.resolveTextMuted(isDark)
    val innerCardBg = if (isDark) Color(0xFF282834) else Color(0xFFF7F7FA)

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    val recurrenceSummary = remember(recurrence, selectedDate) {
        when {
            recurrence == null -> "Does not repeat"
            recurrence?.unit == RecurrenceUnit.DAY -> "Daily"
            recurrence?.unit == RecurrenceUnit.WEEK -> {
                val dayName = selectedDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                "Weekly on $dayName"
            }
            recurrence?.unit == RecurrenceUnit.MONTH -> "Monthly"
            recurrence?.unit == RecurrenceUnit.YEAR -> "Yearly"
            else -> "Repeats"
        }
    }

    val reminderSubtitle = remember(reminders, reminderAtStart, context) {
        if (reminders.isNotEmpty()) {
            val soonest = reminders.minByOrNull { it.reminderAt }
            if (soonest != null) {
                val baseTime = soonest.originalReminderAt ?: soonest.reminderAt
                val datePart = android.text.format.DateFormat.getMediumDateFormat(context).format(Date(baseTime))
                val timePart = formatTimeOfDay(context, baseTime)
                val moreText = if (reminders.size > 1) " (+${reminders.size - 1} more)" else ""
                "$datePart, $timePart$moreText"
            } else {
                null
            }
        } else if (reminderAtStart) {
            context.getString(R.string.event_reminder_at_start)
        } else {
            context.getString(R.string.event_reminder_none)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "arrow_back",
                        size = 20.dp,
                        tint = textPrimary,
                    )
                }
            }

            Text(
                text = if (viewModel.isEditMode) {
                    stringResource(R.string.event_edit_title)
                } else {
                    stringResource(R.string.event_add_title)
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    fontSize = 20.sp,
                ),
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        val result = viewModel.saveEvent()
                        if (result.isSuccess) {
                            onBack()
                        }
                    }
                },
                enabled = !isSaving && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CalendarDesignTokens.PrimaryPurple,
                    disabledContainerColor = CalendarDesignTokens.PrimaryPurple.copy(alpha = 0.4f),
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.calendar_event_save),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Error Banner if validation failed
        if (saveError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = saveError.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 2. Event Information Card (Title, Category, Notes)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.calendar_event_title_placeholder),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        color = textSecondary,
                    ),
                )

                BasicTextField(
                    value = title,
                    onValueChange = viewModel::setTitle,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
                    ),
                    cursorBrush = SolidColor(CalendarDesignTokens.PrimaryPurple),
                    decorationBox = { innerTextField ->
                        if (title.isBlank()) {
                            Text(
                                text = stringResource(R.string.calendar_event_title_placeholder),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textMuted,
                                ),
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Shoulder category row beneath title
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showShoulderPicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selectedShoulder != null) {
                        val shoulderColor = parseHexColor(selectedShoulder?.colorHex)
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(shoulderColor),
                        )
                        Text(
                            text = selectedShoulder?.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    } else {
                        RememberMaterialRoundedSymbol(
                            name = "sell",
                            size = 16.dp,
                            tint = CalendarDesignTokens.PrimaryPurple,
                        )
                        Text(
                            text = stringResource(R.string.event_no_shoulder),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                }

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                Text(
                    text = stringResource(R.string.event_notes_label),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        color = textSecondary,
                    ),
                )

                BasicTextField(
                    value = notes,
                    onValueChange = viewModel::setNotes,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = textPrimary,
                    ),
                    cursorBrush = SolidColor(CalendarDesignTokens.PrimaryPurple),
                    minLines = 2,
                    maxLines = 8,
                    decorationBox = { innerTextField ->
                        if (notes.isBlank()) {
                            Text(
                                text = stringResource(R.string.event_notes_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = textMuted,
                                ),
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Icon & color Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            EventIconAndColorRow(
                colorIndex = colorIndex,
                iconKey = iconKey,
                isDark = isDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = { showIconColorSheet = true },
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Date & Time Section Header
        SectionHeader(
            title = stringResource(R.string.event_date_and_time),
            color = textSecondary,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Date & Time Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Date Row
                SettingRow(
                    iconName = "calendar_month",
                    title = stringResource(R.string.event_date_label),
                    subtitle = selectedDate.format(dateFormatter),
                    onClick = { showDatePicker = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )

                // Starts & Ends row (visible when !isAllDay)
                if (!isAllDay) {
                    HorizontalDivider(color = cardBorder, thickness = 1.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Starts Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = innerCardBg,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showStartTimePicker = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.event_starts_label),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = textSecondary,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = startTime.format(timeFormatter),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary,
                                        ),
                                    )
                                }
                                RememberMaterialRoundedSymbol(
                                    name = "keyboard_arrow_down",
                                    size = 20.dp,
                                    tint = textSecondary,
                                )
                            }
                        }

                        // Ends Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = innerCardBg,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showEndTimePicker = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.event_ends_label),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = textSecondary,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = endTime.format(timeFormatter),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary,
                                        ),
                                    )
                                }
                                RememberMaterialRoundedSymbol(
                                    name = "keyboard_arrow_down",
                                    size = 20.dp,
                                    tint = textSecondary,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // All-day Row
                SettingRow(
                    iconName = "wb_sunny",
                    title = stringResource(R.string.event_all_day_label),
                    subtitle = if (isAllDay) "On" else "Off",
                    onClick = { viewModel.setAllDay(!isAllDay) },
                    trailingContent = {
                        Switch(
                            checked = isAllDay,
                            onCheckedChange = viewModel::setAllDay,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CalendarDesignTokens.PrimaryPurple,
                            ),
                        )
                    },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // Repeat Row
                SettingRow(
                    iconName = "sync",
                    title = stringResource(R.string.event_repeat_label),
                    subtitle = recurrenceSummary,
                    onClick = { showRecurrenceDialog = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Event Options Card (Shoulders, Reminder, Notification action)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Shoulders Row
                SettingRow(
                    iconName = "sell",
                    title = stringResource(R.string.event_shoulders_label),
                    subtitle = selectedShoulder?.name ?: stringResource(R.string.event_no_shoulder),
                    onClick = { showShoulderPicker = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // Reminder Row
                SettingRow(
                    iconName = "alarm",
                    title = stringResource(R.string.event_reminder_label),
                    subtitle = reminderSubtitle ?: stringResource(R.string.event_reminder_none),
                    onClick = { showReminderSheet = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // Notification action Row
                val actionLabel = if (actions.isNotEmpty()) {
                    actions.first().type.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
                } else {
                    stringResource(R.string.event_notification_action_default)
                }
                SettingRow(
                    iconName = "notifications",
                    title = stringResource(R.string.event_notification_action_label),
                    subtitle = actionLabel,
                    onClick = { showActionPicker = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Behavior Section Header
        SectionHeader(
            title = stringResource(R.string.event_behavior_label),
            color = textSecondary,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Behavior Card (Visibility, Importance)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val visibilityLabel = when (visibility) {
                    Visibility.DEFAULT -> "Visible"
                    Visibility.PRIVATE -> "Private"
                    Visibility.SECRET -> "Secret"
                }
                SettingRow(
                    iconName = "visibility",
                    title = stringResource(R.string.event_visibility_label),
                    subtitle = visibilityLabel,
                    onClick = { showVisibilityDialog = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                val importanceLabel = when (importance) {
                    Importance.LOW -> "Low"
                    Importance.DEFAULT -> "Normal"
                    Importance.HIGH -> "High"
                    Importance.CRITICAL -> "Critical"
                }
                SettingRow(
                    iconName = "flag",
                    title = stringResource(R.string.event_importance_label),
                    subtitle = importanceLabel,
                    onClick = { showImportanceDialog = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. Media & Attachments Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Picture Row
                SettingRow(
                    iconName = "image",
                    title = stringResource(R.string.event_picture_label),
                    subtitle = if (!pictureUri.isNullOrBlank()) "Picture attached" else stringResource(R.string.event_add_picture),
                    onClick = { heroPicker.pickWithPhotoPicker() },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // Attachments Row
                val totalAttachments = attachments.size + pendingAttachments.size
                val attachmentsSubtitle = if (totalAttachments > 0) {
                    "$totalAttachments attachment${if (totalAttachments > 1) "s" else ""}"
                } else {
                    stringResource(R.string.event_add_attachment)
                }
                SettingRow(
                    iconName = "attach_file",
                    title = stringResource(R.string.event_attachments_label),
                    subtitle = attachmentsSubtitle,
                    onClick = { showAttachmentsSheet = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // ==================== PICKERS & DIALOGS ====================

    // 1. Date Picker Dialog
    if (showDatePicker) {
        val initialUtcMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        CalendarPickerDialog(
            initial = initialUtcMillis,
            onConfirm = { pickedUtcMillis ->
                viewModel.setSelectedDate(
                    Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate(),
                )
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // 2. Start Time Picker Dialog
    if (showStartTimePicker) {
        ReminderTimePickerDialog(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                viewModel.setStartTime(LocalTime.of(h, m))
                showStartTimePicker = false
            },
        )
    }

    // 3. End Time Picker Dialog
    if (showEndTimePicker) {
        ReminderTimePickerDialog(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                viewModel.setEndTime(LocalTime.of(h, m))
                showEndTimePicker = false
            },
        )
    }

    // 4. Recurrence Selection Dialog
    if (showRecurrenceDialog) {
        RecurrenceSelectionDialog(
            currentRecurrence = recurrence,
            selectedDate = selectedDate,
            onSelect = { newRule ->
                viewModel.setRecurrence(newRule)
                showRecurrenceDialog = false
            },
            onDismiss = { showRecurrenceDialog = false },
        )
    }

    // 5. Shoulder Picker Sheet
    if (showShoulderPicker) {
        ShoulderPickerSheet(
            shoulders = availableShoulders,
            selectedShoulderId = selectedShoulderId,
            noteTitle = title,
            onSelectShoulder = { shoulder ->
                viewModel.setSelectedShoulderId(shoulder?.id)
                showShoulderPicker = false
            },
            onCreateShoulder = { name, colorHex ->
                viewModel.createShoulder(name, colorHex)
            },
            onUpdateShoulder = { shoulder ->
                viewModel.updateShoulder(shoulder)
            },
            onDeleteShoulder = { shoulder ->
                viewModel.deleteShoulder(shoulder)
            },
            onDismiss = { showShoulderPicker = false },
        )
    }

    // 6. Reminder Picker Sheet (Exact same sheet as in Tasks/Notes)
    if (showReminderSheet) {
        val initialSheetReminders = if (reminders.isEmpty() && reminderAtStart) {
            val date = selectedDate
            val time = startTime
            val epoch = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            listOf(three.haddad.smartnotas.data.NoteReminder(reminderAt = epoch, recurrence = recurrence))
        } else {
            reminders
        }
        ReminderPickerSheet(
            initialReminders = initialSheetReminders,
            defaultReminderMinutesOfDay = startTime.hour * 60 + startTime.minute,
            defaultRecurrence = recurrence,
            onConfirm = { updatedReminders ->
                viewModel.setReminders(updatedReminders)
                showReminderSheet = false
            },
            onDismiss = { showReminderSheet = false },
        )
    }

    // 7. Action Picker Sheet
    if (showActionPicker) {
        ActionPicker(
            current = actions,
            onConfirm = { newActions ->
                viewModel.setActions(newActions)
                showActionPicker = false
            },
            onDismiss = { showActionPicker = false },
        )
    }

    // 8. Visibility Dialog
    if (showVisibilityDialog) {
        SelectionOptionDialog(
            title = stringResource(R.string.event_visibility_label),
            options = listOf(
                "Visible" to Visibility.DEFAULT,
                "Private" to Visibility.PRIVATE,
                "Secret" to Visibility.SECRET,
            ),
            selected = visibility,
            onSelect = {
                viewModel.setVisibility(it)
                showVisibilityDialog = false
            },
            onDismiss = { showVisibilityDialog = false },
        )
    }

    // 9. Importance Dialog
    if (showImportanceDialog) {
        SelectionOptionDialog(
            title = stringResource(R.string.event_importance_label),
            options = listOf(
                "Low" to Importance.LOW,
                "Normal" to Importance.DEFAULT,
                "High" to Importance.HIGH,
                "Critical" to Importance.CRITICAL,
            ),
            selected = importance,
            onSelect = {
                viewModel.setImportance(it)
                showImportanceDialog = false
            },
            onDismiss = { showImportanceDialog = false },
        )
    }

    // 10. Attachments Sheet
    if (showAttachmentsSheet) {
        val allAttachmentEntities = remember(attachments, pendingAttachments) {
            attachments + pendingAttachments.map { pending ->
                NoteAttachmentEntity(
                    id = pending.id,
                    noteId = viewModel.eventId ?: 0L,
                    uri = pending.uriString,
                    displayName = pending.displayName,
                    mimeType = pending.mimeType,
                )
            }
        }
        AttachmentsSheet(
            attachments = allAttachmentEntities,
            onDismiss = { showAttachmentsSheet = false },
            onAdd = { uri, displayName, mimeType ->
                viewModel.addAttachment(uri, displayName, mimeType)
            },
            onRemove = { id ->
                viewModel.removeAttachment(id)
            },
        )
    }

    // 11. Icon & Color Sheet
    if (showIconColorSheet) {
        IconAndColorSheet(
            currentColorIndex = colorIndex,
            currentIconKey = iconKey,
            isDark = isDark,
            onColorSelected = { viewModel.setColorIndex(it) },
            onIconSelected = { viewModel.setIconKey(it) },
            onRequestMoreIcons = {
                showIconColorSheet = false
                showFullIconPicker = true
            },
            onDismiss = { showIconColorSheet = false },
        )
    }

    // 12. Full Icon Picker
    if (showFullIconPicker) {
        IconPicker(
            current = iconKey,
            onPick = { pickedKey ->
                viewModel.setIconKey(pickedKey)
                showFullIconPicker = false
            },
            onDismiss = { showFullIconPicker = false },
            isChecklist = false,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    color: Color,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        ),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingRow(
    iconName: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    iconBg: Color = CalendarDesignTokens.SoftLavender,
    iconTint: Color = CalendarDesignTokens.PrimaryPurple,
    textPrimary: Color,
    textSecondary: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = iconName,
                size = 20.dp,
                tint = iconTint,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                ),
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = textSecondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 20.dp,
                tint = textSecondary,
            )
        }
    }
}

@Composable
private fun RecurrenceSelectionDialog(
    currentRecurrence: RecurrenceRule?,
    selectedDate: LocalDate,
    onSelect: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val dayOfWeekName = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    }
    val calWeekday = remember(selectedDate) {
        when (selectedDate.dayOfWeek) {
            DayOfWeek.SUNDAY -> Calendar.SUNDAY
            else -> selectedDate.dayOfWeek.value + 1
        }
    }

    val options = listOf(
        "Does not repeat" to null,
        "Daily" to RecurrenceRule(unit = RecurrenceUnit.DAY, interval = 1),
        "Weekly on $dayOfWeekName" to RecurrenceRule(
            unit = RecurrenceUnit.WEEK,
            interval = 1,
            daysOfWeek = setOf(calWeekday),
        ),
        "Monthly" to RecurrenceRule(unit = RecurrenceUnit.MONTH, interval = 1),
        "Yearly" to RecurrenceRule(unit = RecurrenceUnit.YEAR, interval = 1),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.event_repeat_label),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                options.forEach { (label, rule) ->
                    val isSelected = when {
                        currentRecurrence == null && rule == null -> true
                        currentRecurrence != null && rule != null -> currentRecurrence.unit == rule.unit
                        else -> false
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(rule) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) CalendarDesignTokens.PrimaryPurple else MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(rule) },
                            colors = RadioButtonDefaults.colors(selectedColor = CalendarDesignTokens.PrimaryPurple),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SelectionOptionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                options.forEach { (label, value) ->
                    val isSelected = selected == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) CalendarDesignTokens.PrimaryPurple else MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = CalendarDesignTokens.PrimaryPurple),
                        )
                    }
                }
            }
        }
    }
}

private fun parseHexColor(colorHex: String?, fallback: Color = CalendarDesignTokens.PrimaryPurple): Color {
    if (colorHex.isNullOrBlank()) return fallback
    return try {
        Color(AndroidColor.parseColor(if (colorHex.startsWith("#")) colorHex else "#$colorHex"))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
private fun EventIconAndColorRow(
    colorIndex: Int,
    iconKey: String?,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF382942) else CalendarDesignTokens.SoftLavender),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "push_pin",
                size = 20.dp,
                tint = CalendarDesignTokens.PrimaryPurple,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.event_icon_and_color_title),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                ),
            )

            Spacer(modifier = Modifier.height(3.dp))

            val accent = NoteColors.at(colorIndex)
            val resolvedColor = if (NoteColors.isNone(colorIndex)) {
                CalendarDesignTokens.PrimaryPurple
            } else if (isDark) accent.dark else accent.light

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(resolvedColor),
                )

                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(resolvedColor.copy(alpha = if (isDark) 0.25f else 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EventSmallIconRenderer(
                        iconKey = iconKey,
                        tint = resolvedColor,
                    )
                }

                val colorDisplayName = when {
                    NoteColors.isNone(colorIndex) -> stringResource(R.string.color_none)
                    accent.name.equals("Orchid", ignoreCase = true) -> stringResource(R.string.color_purple)
                    else -> accent.name
                }
                val iconDisplayName = getEventIconDisplayName(iconKey)

                Text(
                    text = "$colorDisplayName · $iconDisplayName",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = textSecondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        RememberMaterialRoundedSymbol(
            name = "chevron_right",
            size = 20.dp,
            tint = textSecondary,
        )
    }
}

private fun extractSymbolName(iconKey: String?): String {
    if (iconKey.isNullOrBlank()) return "calendar_month"
    return iconKey
        .removePrefix(ICON_SYMBOL_PREFIX)
        .removePrefix(ICON_SYMBOL_FILLED_PREFIX)
        .removePrefix(ICON_SYMBOL_OUTLINED_PREFIX)
        .removePrefix(ICON_EMOJI_PREFIX)
        .removePrefix(ICON_DRAWABLE_PREFIX)
        .ifBlank { "calendar_month" }
}

private fun ensureCatalogIconKey(key: String?): String {
    if (key.isNullOrBlank()) return "${ICON_SYMBOL_PREFIX}calendar_month"
    if (key.startsWith(ICON_SYMBOL_PREFIX) ||
        key.startsWith(ICON_SYMBOL_FILLED_PREFIX) ||
        key.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) ||
        key.startsWith(ICON_EMOJI_PREFIX) ||
        key.startsWith(ICON_DRAWABLE_PREFIX)
    ) {
        return key
    }
    return "${ICON_SYMBOL_PREFIX}$key"
}

private fun getEventIconDisplayName(iconKey: String?): String {
    if (iconKey.isNullOrBlank()) return "Calendar"
    if (iconKey.startsWith(ICON_EMOJI_PREFIX)) {
        return iconEmojiPayload(iconKey) ?: "Emoji"
    }
    val symbol = extractSymbolName(iconKey)
    if (symbol.equals("calendar_month", ignoreCase = true) || symbol.equals("event", ignoreCase = true)) {
        return "Calendar"
    }
    val label = iconLabelFor(iconKey) ?: humanizeIconKey(iconKey)
    if (label.contains("calendar", ignoreCase = true)) return "Calendar"
    return label
}

@Composable
private fun EventSmallIconRenderer(
    iconKey: String?,
    tint: Color,
) {
    if (iconKey?.startsWith(ICON_EMOJI_PREFIX) == true) {
        val emoji = iconEmojiPayload(iconKey) ?: ""
        Text(
            text = emoji,
            fontSize = 11.sp,
            lineHeight = 11.sp,
        )
    } else if (iconKey?.startsWith(ICON_DRAWABLE_PREFIX) == true) {
        val resId = iconDrawableRes(iconKey)
        if (resId != null) {
            Icon(
                painter = painterResource(resId),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        } else {
            RememberMaterialRoundedSymbol(
                name = "calendar_month",
                size = 12.dp,
                tint = tint,
            )
        }
    } else {
        val symbolName = extractSymbolName(iconKey)
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 12.dp,
            tint = tint,
        )
    }
}

@Composable
private fun EventMediumIconRenderer(
    iconKey: String?,
    tint: Color,
) {
    if (iconKey?.startsWith(ICON_EMOJI_PREFIX) == true) {
        val emoji = iconEmojiPayload(iconKey) ?: ""
        Text(
            text = emoji,
            fontSize = 26.sp,
        )
    } else if (iconKey?.startsWith(ICON_DRAWABLE_PREFIX) == true) {
        val resId = iconDrawableRes(iconKey)
        if (resId != null) {
            Icon(
                painter = painterResource(resId),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        } else {
            RememberMaterialRoundedSymbol(
                name = "calendar_month",
                size = 28.dp,
                tint = tint,
            )
        }
    } else {
        val symbolName = extractSymbolName(iconKey)
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 28.dp,
            tint = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconAndColorSheet(
    currentColorIndex: Int,
    currentIconKey: String?,
    isDark: Boolean,
    onColorSelected: (Int) -> Unit,
    onIconSelected: (String?) -> Unit,
    onRequestMoreIcons: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (isDark) Color(0xFF1F1F28) else Color.White
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val innerCardBg = if (isDark) Color(0xFF282834) else Color(0xFFF7F7FA)

    val currentAccent = NoteColors.at(currentColorIndex)
    val currentColor = if (NoteColors.isNone(currentColorIndex)) {
        CalendarDesignTokens.PrimaryPurple
    } else if (isDark) currentAccent.dark else currentAccent.light

    val presetIcons = remember {
        listOf(
            "calendar_month",
            "event",
            "celebration",
            "cake",
            "star",
            "sports_soccer",
            "flight",
            "fitness_center",
            "restaurant",
            "school",
            "computer",
            "medical_services",
            "shopping_cart",
            "pets",
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    ),
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.calendar_event_save),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = CalendarDesignTokens.PrimaryPurple,
                        ),
                    )
                }
            }

            // Live preview card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = innerCardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(currentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        EventMediumIconRenderer(
                            iconKey = currentIconKey,
                            tint = Color.White,
                        )
                    }

                    Column(verticalArrangement = Arrangement.Center) {
                        val colorDisplayName = when {
                            NoteColors.isNone(currentColorIndex) -> stringResource(R.string.color_none)
                            currentAccent.name.equals("Orchid", ignoreCase = true) -> stringResource(R.string.color_purple)
                            else -> currentAccent.name
                        }
                        val iconDisplayName = getEventIconDisplayName(currentIconKey)

                        Text(
                            text = "$colorDisplayName · $iconDisplayName",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.event_icon_and_color_title),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondary,
                            ),
                        )
                    }
                }
            }

            // Color Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_color_label),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary,
                    ),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(NoteColors.palette) { index, accent ->
                        val isSelected = index == currentColorIndex
                        val dotColor = if (index == 0) {
                            if (isDark) Color(0xFF4A4A58) else Color(0xFFD4D4DC)
                        } else if (isDark) accent.dark else accent.light

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            BorderStroke(3.dp, if (isDark) Color.White else Color(0xFF202028)),
                                            CircleShape,
                                        )
                                    } else Modifier,
                                )
                                .clickable { onColorSelected(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 18.dp,
                                    tint = if (index == 0 || accent.name == "Sun" || accent.name == "Lime") Color.Black else Color.White,
                                )
                            } else if (index == 0) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    size = 16.dp,
                                    tint = if (isDark) Color.White else Color(0xFF6B6B76),
                                )
                            }
                        }
                    }
                }
            }

            // Icon Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_icon_label),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary,
                    ),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(presetIcons) { iconName ->
                        val currentSymbol = extractSymbolName(currentIconKey)
                        val isSelected = currentSymbol.equals(iconName, ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) currentColor.copy(alpha = 0.18f) else innerCardBg,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) currentColor else cardBorder,
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { onIconSelected("${ICON_SYMBOL_PREFIX}$iconName") },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = iconName,
                                    size = 22.dp,
                                    tint = if (isSelected) currentColor else textSecondary,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // "More icons…" button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = innerCardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestMoreIcons() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "category",
                            size = 20.dp,
                            tint = CalendarDesignTokens.PrimaryPurple,
                        )
                        Text(
                            text = stringResource(R.string.event_icon_and_color_more_icons),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            size = 20.dp,
                            tint = textSecondary,
                        )
                    }
                }
            }
        }
    }
}

