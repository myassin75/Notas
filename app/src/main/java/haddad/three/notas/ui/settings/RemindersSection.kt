package haddad.three.notas.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import haddad.three.notas.R
import haddad.three.notas.data.QuickCapturePrefs
import haddad.three.notas.data.QuickCaptureState
import haddad.three.notas.data.ReminderPreferencesState
import haddad.three.notas.data.ReminderPrefs
import haddad.three.notas.data.SnoozeType
import haddad.three.notas.ui.components.RememberDropdownMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RemindersSection(
    reminderState: ReminderPreferencesState,
    reminderPrefs: ReminderPrefs,
    quickCaptureState: QuickCaptureState,
    quickCapturePrefs: QuickCapturePrefs,
    notificationsGranted: Boolean,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    permissionLinked: Boolean,
    canScheduleExactAlarms: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    scope: CoroutineScope,
    highlightItemKey: String? = null,
    highlightItemRequestId: Int = 0,
) {
    val context = LocalContext.current
    val keepUntilDoneHighlightActive =
        rememberSettingsItemHighlightActive(
            itemKey = "keep_until_done",
            highlightItemKey = highlightItemKey,
            highlightItemRequestId = highlightItemRequestId,
        )
    val keepUntilDoneHighlightAlpha = rememberSectionHighlightPulseAlpha(keepUntilDoneHighlightActive)
    val snoozeTypeHighlightActive =
        rememberSettingsItemHighlightActive(
            itemKey = "snooze_type",
            highlightItemKey = highlightItemKey,
            highlightItemRequestId = highlightItemRequestId,
        )
    val snoozeTypeHighlightAlpha = rememberSectionHighlightPulseAlpha(snoozeTypeHighlightActive)

    SettingsSectionCard {
        // Row 1: Notifications
        SettingsRowItem(
            materialSymbolName = "notifications",
            title = stringResource(R.string.settings_notifications),
            subtitle = stringResource(R.string.settings_notifications_desc),
            onClick = {
                if (!notificationsGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        context.startActivity(notificationsAppSettingsIntent(context))
                    }
                } else {
                    context.startActivity(notificationsAppSettingsIntent(context))
                }
            },
            trailingContent = {
                SettingsCustomSwitch(
                    checked = notificationsGranted,
                    onCheckedChange = { wantEnabled ->
                        when {
                            wantEnabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED ->
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            wantEnabled && !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                context.startActivity(notificationsAppSettingsIntent(context))
                            !wantEnabled ->
                                context.startActivity(notificationsAppSettingsIntent(context))
                            else -> { }
                        }
                    },
                )
            },
        )
        SettingsDivider()

        // Row 2: Reliable reminders
        if (permissionLinked) {
            SettingsRowItem(
                materialSymbolName = "timer",
                title = stringResource(R.string.settings_reliable_reminders),
                subtitle = stringResource(R.string.settings_reliable_reminders_desc),
                onClick = {
                    val wantEnabled = !(canScheduleExactAlarms && isIgnoringBatteryOptimizations)
                    val intent =
                        if (wantEnabled && !canScheduleExactAlarms) {
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        } else if (wantEnabled && !isIgnoringBatteryOptimizations) {
                            batteryOptimizationIntent(context)
                        } else {
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        }
                    context.startActivity(intent)
                },
                trailingContent = {
                    SettingsCustomSwitch(
                        checked = canScheduleExactAlarms && isIgnoringBatteryOptimizations,
                        onCheckedChange = { wantEnabled ->
                            val intent =
                                if (wantEnabled && !canScheduleExactAlarms) {
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                } else if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                    batteryOptimizationIntent(context)
                                } else {
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                }
                            context.startActivity(intent)
                        },
                    )
                },
            )
        } else {
            SettingsRowItem(
                materialSymbolName = "timer",
                title = stringResource(R.string.settings_reliable_reminders),
                subtitle = stringResource(R.string.settings_reliable_reminders_exact_desc),
                onClick = {
                    val intent =
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                    context.startActivity(intent)
                },
                trailingContent = {
                    SettingsCustomSwitch(
                        checked = canScheduleExactAlarms,
                        onCheckedChange = {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            context.startActivity(intent)
                        },
                    )
                },
            )
        }
        SettingsDivider()

        // Row 3: Keep reminder notifications until done
        Box(
            modifier =
                Modifier
                    .zIndex(if (keepUntilDoneHighlightActive) 1f else 0f)
                    .pulsingSectionHighlightOutline(
                        active = keepUntilDoneHighlightActive,
                        outlineColor = MaterialTheme.colorScheme.primary.copy(alpha = keepUntilDoneHighlightAlpha),
                    ),
        ) {
            SettingsRowItem(
                materialSymbolName = "notification_important",
                title = stringResource(R.string.settings_restore_notifications),
                subtitle = stringResource(R.string.settings_restore_notifications_desc),
                onClick = {
                    scope.launch {
                        reminderPrefs.setKeepReminderNotificationsUntilDone(!reminderState.keepReminderNotificationsUntilDone)
                    }
                },
                trailingContent = {
                    SettingsCustomSwitch(
                        checked = reminderState.keepReminderNotificationsUntilDone,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                reminderPrefs.setKeepReminderNotificationsUntilDone(enabled)
                            }
                        },
                    )
                },
            )
        }
        SettingsDivider()

        // Row 4: Reminder summary notification
        SettingsRowItem(
            materialSymbolName = "format_list_bulleted",
            title = stringResource(R.string.settings_reminder_summary_notification),
            subtitle = stringResource(R.string.settings_reminder_summary_notification_desc),
            onClick = {
                scope.launch {
                    reminderPrefs.setReminderSummaryNotificationEnabled(!reminderState.reminderSummaryNotificationEnabled)
                }
            },
            trailingContent = {
                SettingsCustomSwitch(
                    checked = reminderState.reminderSummaryNotificationEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            reminderPrefs.setReminderSummaryNotificationEnabled(enabled)
                        }
                    },
                )
            },
        )
        SettingsDivider()

        // Row 5: Quick capture notification
        SettingsRowItem(
            materialSymbolName = "bolt",
            title = stringResource(R.string.settings_quick_capture_title),
            subtitle = stringResource(R.string.settings_quick_capture_subtitle),
            onClick = {
                scope.launch { quickCapturePrefs.setEnabled(!quickCaptureState.enabled) }
            },
            trailingContent = {
                SettingsCustomSwitch(
                    checked = quickCaptureState.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch { quickCapturePrefs.setEnabled(enabled) }
                    },
                )
            },
        )
        SettingsDivider()

        // Row 6: Snooze type
        Box(
            modifier =
                Modifier
                    .zIndex(if (snoozeTypeHighlightActive) 1f else 0f)
                    .pulsingSectionHighlightOutline(
                        active = snoozeTypeHighlightActive,
                        outlineColor = MaterialTheme.colorScheme.primary.copy(alpha = snoozeTypeHighlightAlpha),
                    ),
        ) {
            SnoozeTypeRow(
                snoozeType = reminderState.snoozeType,
                onSelect = { selected ->
                    scope.launch { reminderPrefs.setSnoozeType(selected) }
                },
            )
        }
    }
}

@Composable
private fun rememberSettingsItemHighlightActive(
    itemKey: String,
    highlightItemKey: String?,
    highlightItemRequestId: Int,
): Boolean {
    var highlighted by rememberSaveable { mutableStateOf(false) }
    var highlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var handledHighlightRequestId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(highlightItemKey, highlightItemRequestId) {
        if (highlightItemKey == itemKey && highlightItemRequestId != handledHighlightRequestId) {
            handledHighlightRequestId = highlightItemRequestId
            highlighted = true
            highlightExpiresAtMillis =
                SystemClock.elapsedRealtime() + SETTINGS_SECTION_HIGHLIGHT_DURATION_MS
        } else if (highlightItemKey != itemKey) {
            highlighted = false
            highlightExpiresAtMillis = 0L
        }
    }
    LaunchedEffect(highlighted, highlightExpiresAtMillis) {
        if (!highlighted) return@LaunchedEffect
        val remainingHighlightMillis = highlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        highlighted = false
        highlightExpiresAtMillis = 0L
    }
    return highlighted && highlightExpiresAtMillis > SystemClock.elapsedRealtime()
}

@Composable
private fun SnoozeTypeRow(
    snoozeType: SnoozeType,
    onSelect: (SnoozeType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel =
        when (snoozeType) {
            SnoozeType.RELATIVE -> stringResource(R.string.settings_preset_type_timing)
            SnoozeType.ABSOLUTE -> stringResource(R.string.settings_preset_type_duration)
        }

    Box {
        SettingsRowItem(
            materialSymbolName = "alarm",
            title = stringResource(R.string.settings_preset_type_title),
            subtitle = stringResource(R.string.settings_preset_type_tooltip),
            onClick = { expanded = true },
            trailingContent = {
                SettingsValuePill(
                    text = selectedLabel,
                    onClick = { expanded = true },
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SnoozeType.entries.forEach { option ->
                val optionLabelRes =
                    when (option) {
                        SnoozeType.RELATIVE -> R.string.settings_preset_type_timing
                        SnoozeType.ABSOLUTE -> R.string.settings_preset_type_duration
                    }
                RememberDropdownMenuItem(
                    text = { Text(stringResource(optionLabelRes)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun notificationsAppSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

@SuppressLint("BatteryLife")
private fun batteryOptimizationIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
    }
