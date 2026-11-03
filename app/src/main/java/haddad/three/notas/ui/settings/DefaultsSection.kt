package haddad.three.notas.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import haddad.three.notas.R
import haddad.three.notas.data.DefaultNotePreferencesState
import haddad.three.notas.data.DefaultNotePrefs
import haddad.three.notas.data.Importance
import haddad.three.notas.data.RecurrenceRule
import haddad.three.notas.data.RecurrenceUnit
import haddad.three.notas.data.labelRes
import haddad.three.notas.domain.formatTimeOfDay
import haddad.three.notas.ui.components.RememberDropdownMenuItem
import haddad.three.notas.ui.edit.ReminderTimePickerDialog
import haddad.three.notas.data.Visibility as NoteVisibility

@Composable
internal fun DefaultsSection(
    defaultsState: DefaultNotePreferencesState,
    defaultNotePrefs: DefaultNotePrefs,
    scope: CoroutineScope,
) {
    var timePickerOpen by remember { mutableStateOf(false) }
    var visibilityExpanded by remember { mutableStateOf(false) }
    var importanceExpanded by remember { mutableStateOf(false) }

    SettingsSectionCard {
        // Row 1: Default visibility
        Box {
            SettingsRowItem(
                materialSymbolName = "visibility",
                title = stringResource(R.string.settings_defaults_visibility_title),
                subtitle = stringResource(R.string.settings_defaults_visibility_desc),
                onClick = { visibilityExpanded = true },
                trailingContent = {
                    SettingsValuePill(
                        text = stringResource(defaultsState.defaultVisibility.labelRes()),
                        onClick = { visibilityExpanded = true },
                    )
                },
            )
            DropdownMenu(
                expanded = visibilityExpanded,
                onDismissRequest = { visibilityExpanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                NoteVisibility.entries.forEach { option ->
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            scope.launch { defaultNotePrefs.setDefaultVisibility(option) }
                            visibilityExpanded = false
                        },
                    )
                }
            }
        }
        SettingsDivider()

        // Row 2: Default importance
        Box {
            SettingsRowItem(
                materialSymbolName = "priority_high",
                title = stringResource(R.string.settings_defaults_importance_title),
                subtitle = stringResource(R.string.settings_defaults_importance_desc),
                onClick = { importanceExpanded = true },
                trailingContent = {
                    SettingsValuePill(
                        text = stringResource(defaultsState.defaultImportance.labelRes()),
                        onClick = { importanceExpanded = true },
                    )
                },
            )
            DropdownMenu(
                expanded = importanceExpanded,
                onDismissRequest = { importanceExpanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Importance.entries.forEach { option ->
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            scope.launch { defaultNotePrefs.setDefaultImportance(option) }
                            importanceExpanded = false
                        },
                    )
                }
            }
        }
        SettingsDivider()

        // Row 3: Default reminder time
        val context = LocalContext.current
        val reminderTimeSummary =
            formatTimeOfDay(
                context,
                DefaultNotePrefs.hourFromMinutesOfDay(defaultsState.defaultReminderMinutesOfDay),
                DefaultNotePrefs.minuteFromMinutesOfDay(defaultsState.defaultReminderMinutesOfDay),
            )
        SettingsRowItem(
            materialSymbolName = "alarm",
            title = stringResource(R.string.settings_defaults_reminder_time_title),
            subtitle = stringResource(R.string.settings_defaults_reminder_time_desc),
            onClick = { timePickerOpen = true },
            trailingContent = {
                SettingsValuePill(
                    text = reminderTimeSummary,
                    onClick = { timePickerOpen = true },
                )
            },
        )
        SettingsDivider()

        // Row 4: Default recurrence
        DefaultsRecurrenceRow(
            recurrence = defaultsState.defaultRecurrence,
            onSelect = { rule ->
                scope.launch { defaultNotePrefs.setDefaultRecurrence(rule) }
            },
        )
    }

    if (timePickerOpen) {
        val initialMinutes = defaultsState.defaultReminderMinutesOfDay
        ReminderTimePickerDialog(
            initialHour = DefaultNotePrefs.hourFromMinutesOfDay(initialMinutes),
            initialMinute = DefaultNotePrefs.minuteFromMinutesOfDay(initialMinutes),
            onDismiss = { timePickerOpen = false },
            onConfirm = { hour, minute ->
                timePickerOpen = false
                scope.launch {
                    defaultNotePrefs.setDefaultReminderMinutesOfDay(
                        DefaultNotePrefs.minutesOfDay(hour, minute),
                    )
                }
            },
        )
    }
}

@Composable
private fun DefaultsRecurrenceRow(
    recurrence: RecurrenceRule?,
    onSelect: (RecurrenceRule) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPreset = DefaultRecurrencePreset.fromRule(recurrence)
    Box {
        SettingsRowItem(
            materialSymbolName = "event_repeat",
            title = stringResource(R.string.settings_defaults_recurrence_title),
            subtitle = stringResource(R.string.settings_defaults_recurrence_desc),
            onClick = { expanded = true },
            trailingContent = {
                SettingsValuePill(
                    text = stringResource(selectedPreset.labelRes),
                    onClick = { expanded = true },
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            DefaultRecurrencePreset.entries.forEach { preset ->
                RememberDropdownMenuItem(
                    text = { Text(stringResource(preset.labelRes)) },
                    onClick = {
                        onSelect(preset.toRule())
                        expanded = false
                    },
                )
            }
        }
    }
}

private enum class DefaultRecurrencePreset(
    val labelRes: Int,
) {
    DAILY(R.string.settings_defaults_recurrence_daily),
    WEEKLY(R.string.settings_defaults_recurrence_weekly),
    MONTHLY(R.string.settings_defaults_recurrence_monthly),
    YEARLY(R.string.settings_defaults_recurrence_yearly),
    ;

    fun toRule(): RecurrenceRule =
        when (this) {
            DAILY -> RecurrenceRule(unit = RecurrenceUnit.DAY, interval = 1)
            WEEKLY -> RecurrenceRule(unit = RecurrenceUnit.WEEK, interval = 1)
            MONTHLY -> RecurrenceRule(unit = RecurrenceUnit.MONTH, interval = 1)
            YEARLY -> RecurrenceRule(unit = RecurrenceUnit.YEAR, interval = 1)
        }

    companion object {
        fun fromRule(rule: RecurrenceRule?): DefaultRecurrencePreset =
            when (rule?.sanitized()?.unit) {
                RecurrenceUnit.WEEK -> WEEKLY
                RecurrenceUnit.MONTH -> MONTHLY
                RecurrenceUnit.YEAR -> YEARLY
                else -> DAILY
            }
    }
}
