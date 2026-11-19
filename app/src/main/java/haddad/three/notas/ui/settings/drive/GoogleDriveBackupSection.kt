package haddad.three.notas.ui.settings.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import haddad.three.notas.R
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.backup.GoogleDriveBackupProvider
import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.drive.model.isLoading
import haddad.three.notas.data.drive.worker.DriveAutoBackupScheduler
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberActionLabel
import haddad.three.notas.ui.components.RememberButton
import haddad.three.notas.ui.components.RememberOutlinedButton
import haddad.three.notas.ui.components.RememberTextButton
import haddad.three.notas.ui.components.settings.GroupPosition
import haddad.three.notas.ui.components.settings.GroupedListColumn
import haddad.three.notas.ui.components.settings.GroupedListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Google Drive End-to-End Encrypted Backup & Restore section.
 */
@Composable
fun GoogleDriveBackupSection(
    driveBackupProvider: GoogleDriveBackupProvider?,
    driveAuthDelegate: GoogleDriveAuthDelegate?,
    drivePrefs: DriveAuthPreferences?,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by (
        driveBackupProvider?.uiState ?: flowOf(
            haddad.three.notas.data.drive.model
                .DriveBackupUiState(),
        )
    ).collectAsStateWithLifecycle(
        initialValue =
            haddad.three.notas.data.drive.model
                .DriveBackupUiState(),
    )
    val prefsState by (
        drivePrefs?.state ?: flowOf(
            haddad.three.notas.data.drive.auth
                .DrivePreferencesState(),
        )
    ).collectAsStateWithLifecycle(
        initialValue =
            haddad.three.notas.data.drive.auth
                .DrivePreferencesState(),
    )

    var backupToRestore by remember { mutableStateOf<DriveBackupItem?>(null) }
    var backupToDelete by remember { mutableStateOf<DriveBackupItem?>(null) }
    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.REPLACE) }

    // Consent prompt launcher for Google Identity Services
    val consentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                scope.launch {
                    val parsed = driveAuthDelegate?.parseConsentResult(context, result.data)
                    if (parsed is GoogleDriveAuthorizationResult.Success) {
                        drivePrefs?.setConnected(parsed.accountEmail)
                        driveBackupProvider?.refreshBackupsList()
                        snackbarHostState.showSnackbar("Connected Google Drive: ${parsed.accountEmail}")
                    } else if (parsed is GoogleDriveAuthorizationResult.Failure) {
                        snackbarHostState.showSnackbar("Authorization failed: ${parsed.cause.localizedMessage}")
                    }
                }
            }
        }

    LaunchedEffect(prefsState.isConnected) {
        if (prefsState.isConnected) {
            driveBackupProvider?.refreshBackupsList()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GroupedListColumn {
            // Header Item
            GroupedListItem(position = if (!prefsState.isConnected) GroupPosition.ONLY else GroupPosition.FIRST) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_brand_googledrive),
                            contentDescription = "Google Drive",
                            tint = androidx.compose.ui.graphics.Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Drive Backup (E2EE)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text =
                                    if (prefsState.isConnected) {
                                        "Connected as ${prefsState.connectedEmail ?: "Google Account"}"
                                    } else {
                                        "Client-side encrypted with AES-256-GCM. Stored in private appDataFolder."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!prefsState.isConnected) {
                        RememberButton(
                            onClick = {
                                scope.launch {
                                    val authResult = driveAuthDelegate?.authorize(context, forceAccountSelection = true)
                                    when (authResult) {
                                        is GoogleDriveAuthorizationResult.Success -> {
                                            drivePrefs?.setConnected(authResult.accountEmail)
                                            driveBackupProvider?.refreshBackupsList()
                                            snackbarHostState.showSnackbar("Connected Google Drive: ${authResult.accountEmail}")
                                        }
                                        is GoogleDriveAuthorizationResult.NeedsConsent -> {
                                            consentLauncher.launch(authResult.request)
                                        }
                                        is GoogleDriveAuthorizationResult.Failure -> {
                                            snackbarHostState.showSnackbar("Error: ${authResult.cause.localizedMessage}")
                                        }
                                        else -> Unit
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RememberActionLabel("Connect Google Drive")
                        }
                    }
                }
            }

            if (prefsState.isConnected) {
                // Actions & Status Item
                GroupedListItem(position = GroupPosition.MIDDLE) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (uiState.status.isLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = uiState.statusMessage ?: "Processing...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (uiState.progressPercent > 0f) {
                                    LinearProgressIndicator(
                                        progress = { uiState.progressPercent },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RememberButton(
                                onClick = {
                                    scope.launch {
                                        val res = driveBackupProvider?.createBackup()
                                        if (res is DriveResult.Success) {
                                            snackbarHostState.showSnackbar("Google Drive backup completed successfully!")
                                        } else if (res is DriveResult.Error) {
                                            snackbarHostState.showSnackbar(res.message)
                                        }
                                    }
                                },
                                enabled = !uiState.status.isLoading,
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberActionLabel("Back Up Now")
                            }

                            RememberOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        driveBackupProvider?.disconnect()
                                        DriveAutoBackupScheduler.cancel(context)
                                        snackbarHostState.showSnackbar("Disconnected Google Drive.")
                                    }
                                },
                                enabled = !uiState.status.isLoading,
                            ) {
                                RememberActionLabel("Disconnect")
                            }
                        }
                    }
                }

                // Automatic Backup Settings Item
                GroupedListItem(position = GroupPosition.MIDDLE) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Daily Backup",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Periodically encrypts and syncs notes to Google Drive in the background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefsState.autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    drivePrefs?.setAutoBackupSettings(
                                        enabled = enabled,
                                        frequencyDays = prefsState.autoBackupFrequencyDays,
                                        requiresWifi = prefsState.autoBackupRequiresWifi,
                                    )
                                    if (enabled) {
                                        DriveAutoBackupScheduler.schedule(context, prefsState.autoBackupFrequencyDays, prefsState.autoBackupRequiresWifi)
                                        snackbarHostState.showSnackbar("Automatic Google Drive backup enabled")
                                    } else {
                                        DriveAutoBackupScheduler.cancel(context)
                                        snackbarHostState.showSnackbar("Automatic Google Drive backup disabled")
                                    }
                                }
                            },
                        )
                    }
                }

                // Backup History Header & List
                GroupedListItem(position = GroupPosition.LAST) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Google Drive Backups (${uiState.backups.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            if (uiState.isLoadingBackups) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                RememberTextButton(onClick = { scope.launch { driveBackupProvider?.refreshBackupsList() } }) {
                                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (uiState.backups.isEmpty() && !uiState.isLoadingBackups) {
                            Text(
                                text = "No Google Drive backups yet. Tap 'Back Up Now' to create one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            uiState.backups.forEachIndexed { index, backup ->
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                BackupHistoryRow(
                                    item = backup,
                                    isBusy = uiState.status.isLoading,
                                    onRestore = { backupToRestore = backup },
                                    onDelete = { backupToDelete = backup },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Restore Dialog
    backupToRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = { Text("Restore from Google Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Backup Date: ${formatEpochMillis(backup.createdAtEpochMillis)}\n" +
                            "Notes: ${backup.notesCount} | Tasks: ${backup.tasksCount}\n" +
                            "Size: ${formatBytes(backup.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Select Restore Mode:", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedRestoreMode == RestoreMode.REPLACE,
                            onClick = { selectedRestoreMode = RestoreMode.REPLACE },
                        )
                        Text("Replace (Overwrite local data)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedRestoreMode == RestoreMode.MERGE,
                            onClick = { selectedRestoreMode = RestoreMode.MERGE },
                        )
                        Text("Non-Destructive Merge (Keep newest)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "A local safety backup will be created automatically before restoring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                RememberButton(
                    onClick = {
                        val toRestore = backupToRestore
                        val mode = selectedRestoreMode
                        backupToRestore = null
                        if (toRestore != null) {
                            scope.launch {
                                val result = driveBackupProvider?.restoreBackup(toRestore.fileId, mode)
                                if (result is DriveResult.Success) {
                                    snackbarHostState.showSnackbar("Successfully restored ${result.data.restoredNotesCount} notes!")
                                } else if (result is DriveResult.Error) {
                                    snackbarHostState.showSnackbar("Restore failed: ${result.message}")
                                }
                            }
                        }
                    },
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { backupToRestore = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete Confirmation Dialog
    backupToDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = { Text("Delete Google Drive Backup?") },
            text = {
                Text(
                    "Are you sure you want to delete the backup from ${formatEpochMillis(backup.createdAtEpochMillis)}? " +
                        "This will not delete any S3/R2 backups or local notes.",
                )
            },
            confirmButton = {
                RememberTextButton(
                    onClick = {
                        val fileId = backup.fileId
                        backupToDelete = null
                        scope.launch {
                            val res = driveBackupProvider?.deleteBackup(fileId)
                            if (res is DriveResult.Success) {
                                snackbarHostState.showSnackbar("Backup deleted from Google Drive.")
                            } else if (res is DriveResult.Error) {
                                snackbarHostState.showSnackbar("Failed to delete backup: ${res.message}")
                            }
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { backupToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BackupHistoryRow(
    item: DriveBackupItem,
    isBusy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatEpochMillis(item.createdAtEpochMillis),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${formatBytes(item.sizeBytes)} • ${item.notesCount} notes • ${item.attachmentsCount} files • ${item.sourceDevice.ifBlank { "Android" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RememberTextButton(onClick = onRestore, enabled = !isBusy) {
                Text("Restore", style = MaterialTheme.typography.labelSmall)
            }
            RememberTextButton(onClick = onDelete, enabled = !isBusy) {
                Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatEpochMillis(millis: Long): String = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
