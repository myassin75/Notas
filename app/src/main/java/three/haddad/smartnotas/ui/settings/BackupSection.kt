package three.haddad.smartnotas.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import three.haddad.smartnotas.R
import three.haddad.smartnotas.backup.RememberBackupWork
import three.haddad.smartnotas.data.BackupIo
import three.haddad.smartnotas.data.BackupPreferencesState
import three.haddad.smartnotas.data.BackupPrefs
import three.haddad.smartnotas.data.auth.AuthRepository
import three.haddad.smartnotas.data.backup.CloudBackupManager
import three.haddad.smartnotas.data.backup.CloudBackupResult
import three.haddad.smartnotas.data.backup.CloudBackupStatus
import three.haddad.smartnotas.data.backup.CloudBackupUiState
import three.haddad.smartnotas.data.backup.isLoading
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.common.ResponsiveActionLayout
import three.haddad.smartnotas.ui.common.responsiveActionLayout
import three.haddad.smartnotas.ui.components.RememberActionLabel
import three.haddad.smartnotas.ui.components.RememberOutlinedButton
import three.haddad.smartnotas.ui.components.RememberTextButton
import three.haddad.smartnotas.ui.components.settings.GroupPosition
import three.haddad.smartnotas.ui.components.settings.GroupedListColumn
import three.haddad.smartnotas.ui.components.settings.GroupedListItem
import three.haddad.smartnotas.ui.feedback.appClickable

/**
 * Backup section: local and cloud folder pickers (long-click to clear), three
 * include/auto/schedule toggles that disable until at least one folder is selected,
 * an "import" / "export now" pair, and a "restore (replace)" pill.
 *
 * Pulled out of [SettingsRoute] in audit 3.1. The four launcher actions (local /
 * cloud folder pick, import-merge, import-replace) are exposed as plain callbacks so
 * this composable does not need to know about the underlying ActivityResult plumbing.
 */
@Composable
internal fun BackupSection(
    backupState: BackupPreferencesState,
    backupPrefs: BackupPrefs,
    backupIo: BackupIo,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onPickLocalFolder: () -> Unit,
    onPickCloudFolder: () -> Unit,
    onLaunchImportMerge: () -> Unit,
    onLaunchImportReplace: () -> Unit,
    cloudBackupManager: CloudBackupManager? = null,
    authRepository: AuthRepository? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val chooseLocalFolderLabel = stringResource(R.string.settings_choose_local_backup_folder)
    val chooseCloudFolderLabel = stringResource(R.string.settings_choose_cloud_backup_file)
    val resolvedLocalFolderLabel by produceState(
        initialValue =
            backupState.exportFolderUri
                .takeIf { it.isNotBlank() }
                ?.let { internalStorageDisplayName } ?: chooseLocalFolderLabel,
        backupState.exportFolderUri,
        internalStorageDisplayName,
        chooseLocalFolderLabel,
    ) {
        val uriString = backupState.exportFolderUri
        value =
            if (uriString.isBlank()) {
                chooseLocalFolderLabel
            } else {
                withContext(Dispatchers.IO) {
                    exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                }
            }
    }
    val resolvedCloudFolderLabel by produceState(
        initialValue =
            backupState.cloudExportFolderUri
                .takeIf { it.isNotBlank() }
                ?.let { internalStorageDisplayName } ?: chooseCloudFolderLabel,
        backupState.cloudExportFolderUri,
        internalStorageDisplayName,
        chooseCloudFolderLabel,
    ) {
        val uriString = backupState.cloudExportFolderUri
        value =
            if (uriString.isBlank()) {
                chooseCloudFolderLabel
            } else {
                withContext(Dispatchers.IO) {
                    exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                }
            }
    }
    val localFolderLabel = resolvedLocalFolderLabel
    val cloudFolderLabel = resolvedCloudFolderLabel
    val exportFolderReady =
        backupState.exportFolderUri.isNotBlank() ||
            backupState.cloudExportFolderUri.isNotBlank()
    val includeMediaSwitchEnabled = exportFolderReady || backupState.includeMediaInBackup
    val autoExportSwitchEnabled = exportFolderReady || backupState.autoExportOnChange
    val scheduledExportSwitchEnabled = exportFolderReady || backupState.scheduledExportEnabled

    val authState by (authRepository?.authState ?: flowOf(three.haddad.smartnotas.data.auth.AuthState.Unauthenticated))
        .collectAsStateWithLifecycle(initialValue = three.haddad.smartnotas.data.auth.AuthState.Unauthenticated)
    val authUser = (authState as? three.haddad.smartnotas.data.auth.AuthState.Authenticated)?.user ?: authRepository?.currentUser
    val cloudState by (cloudBackupManager?.uiState ?: flowOf(CloudBackupUiState()))
        .collectAsStateWithLifecycle(initialValue = CloudBackupUiState())
    var showCloudRestoreConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(authUser?.uid) {
        if (authUser != null) {
            cloudBackupManager?.refreshBackupsList()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. Notas Cloud Backup & Private Attachment Storage ---
        GroupedListColumn {
            if (authUser == null) {
                GroupedListItem(position = GroupPosition.ONLY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "cloud_off",
                            size = 28.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_cloud_backup_header),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_cloud_backup_guest_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                GroupedListItem(position = GroupPosition.FIRST) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "cloud_sync",
                            size = 28.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_cloud_backup_header),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = authUser.email.ifBlank { authUser.uid },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                GroupedListItem(position = GroupPosition.MIDDLE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when (cloudState.status) {
                            CloudBackupStatus.BACKUP_IN_PROGRESS -> {
                                Text(
                                    text = stringResource(
                                        R.string.settings_cloud_backup_in_progress,
                                        (cloudState.progressPercent * 100).toInt(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                LinearProgressIndicator(
                                    progress = { cloudState.progressPercent },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CloudBackupStatus.VERIFYING_BACKUP -> {
                                Text(
                                    text = stringResource(R.string.settings_cloud_backup_verifying),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CloudBackupStatus.RESTORE_IN_PROGRESS -> {
                                Text(
                                    text = stringResource(
                                        R.string.settings_cloud_restore_in_progress,
                                        (cloudState.progressPercent * 100).toInt(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                LinearProgressIndicator(
                                    progress = { cloudState.progressPercent },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CloudBackupStatus.BACKUP_FAILED,
                            CloudBackupStatus.RESTORE_FAILED -> {
                                Text(
                                    text = stringResource(
                                        if (cloudState.status == CloudBackupStatus.RESTORE_FAILED) {
                                            R.string.settings_cloud_restore_failed
                                        } else {
                                            R.string.settings_cloud_backup_failed
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                cloudState.errorMessage?.let { errorMsg ->
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            CloudBackupStatus.BACKUP_COMPLETED,
                            CloudBackupStatus.RESTORE_COMPLETED -> {
                                Text(
                                    text = stringResource(
                                        if (cloudState.status == CloudBackupStatus.RESTORE_COMPLETED) {
                                            R.string.settings_cloud_restore_completed
                                        } else {
                                            R.string.settings_cloud_backup_completed
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            CloudBackupStatus.IDLE -> {
                                if (cloudState.backups.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.settings_cloud_backup_no_backups),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    val latest = cloudState.backups.first()
                                    Text(
                                        text = "${latest.notesCount} notes • ${latest.tasksCount} tasks • ${formatBackupSize(latest.sizeBytes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Backed up ${latest.createdAt.take(10)} • Checksum verified",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "attach_file",
                                size = 16.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            Text(
                                text = stringResource(R.string.settings_cloud_attachments_synced),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                GroupedListItem(position = GroupPosition.LAST) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isBusy = cloudState.status.isLoading
                        RememberOutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = cloudBackupManager?.createCloudBackup()
                                    if (res is CloudBackupResult.Success) {
                                        snackbarHostState.showSnackbar(
                                            message = resources.getString(R.string.settings_cloud_backup_completed),
                                            duration = SnackbarDuration.Short,
                                        )
                                    } else if (res is CloudBackupResult.Error) {
                                        snackbarHostState.showSnackbar(
                                            message = res.message,
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            RememberActionLabel(stringResource(R.string.settings_cloud_backup_now))
                        }

                        RememberOutlinedButton(
                            onClick = {
                                showCloudRestoreConfirmDialog = true
                            },
                            enabled = !isBusy && cloudState.backups.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            RememberActionLabel(stringResource(R.string.settings_cloud_restore_latest))
                        }
                    }
                }
            }
        }

        // --- 2. Local & External Storage Folder Pickers ---
        GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            BackupFolderPickerItem(
                title = localFolderLabel,
                subtitle = stringResource(R.string.settings_local_backup_folder_hint),
                accessibilityLabel = stringResource(R.string.settings_choose_local_backup_folder),
                onClick = onPickLocalFolder,
                onLongClick = {
                    if (backupState.exportFolderUri.isNotBlank()) {
                        scope.launch {
                            backupPrefs.setExportFolderUri("")
                            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_local_backup_folder_cleared),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderPickerItem(
                title = cloudFolderLabel,
                subtitle = stringResource(R.string.settings_cloud_backup_folder_hint),
                accessibilityLabel = stringResource(R.string.settings_choose_cloud_backup_file),
                onClick = onPickCloudFolder,
                onLongClick = {
                    if (backupState.cloudExportFolderUri.isNotBlank()) {
                        scope.launch {
                            backupPrefs.setCloudExportFolderUri("")
                            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_cloud_backup_file_cleared),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_include_media_in_backup),
                subtitle = stringResource(R.string.settings_include_media_in_backup_hint),
                infoTooltipText = stringResource(R.string.settings_include_media_in_backup_tooltip),
                infoContentDescription = stringResource(R.string.settings_include_media_info_cd),
                checked = backupState.includeMediaInBackup,
                switchEnabled = includeMediaSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setIncludeMediaInBackup(enabled)
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_compress_images),
                subtitle = stringResource(R.string.settings_compress_images_hint),
                infoTooltipText = stringResource(R.string.settings_compress_images_tooltip),
                infoContentDescription = stringResource(R.string.settings_compress_images_info_cd),
                checked = backupState.compressImages,
                switchEnabled = true,
                onDisabledInteraction = null,
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setCompressImages(enabled)
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_auto_export_on_change),
                subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                checked = backupState.autoExportOnChange,
                switchEnabled = autoExportSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setAutoExportOnChange(enabled)
                        RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_scheduled_export),
                subtitle = stringResource(R.string.settings_scheduled_export_hint),
                checked = backupState.scheduledExportEnabled,
                switchEnabled = scheduledExportSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setScheduledExportEnabled(enabled)
                        RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.LAST) {
            val backupHelpCd = stringResource(R.string.settings_backup_help_icon_cd)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stacked =
                        responsiveActionLayout(
                            availableWidth = maxWidth,
                            effectiveFontScale = LocalDensity.current.fontScale,
                            itemCount = 2,
                        ) == ResponsiveActionLayout.STACKED
                    if (stacked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RememberOutlinedButton(
                                onClick = onLaunchImportMerge,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_import_rules))
                            }
                            RememberOutlinedButton(
                                onClick = {
                                    if (exportFolderReady) {
                                        scope.launch {
                                            val backupDestinations =
                                                listOf(
                                                    backupState.exportFolderUri,
                                                    backupState.cloudExportFolderUri,
                                                ).filter { it.isNotBlank() }
                                            val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
                                            val message =
                                                exportOutcome.fold(
                                                    onSuccess = { fileNames ->
                                                        resources.getQuantityString(
                                                            R.plurals.toast_exported_to_destinations,
                                                            fileNames.size,
                                                            fileNames.size,
                                                        )
                                                    },
                                                    onFailure = {
                                                        resources.getString(R.string.toast_export_failed)
                                                    },
                                                )
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_export_select_folder_first),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_export_now))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RememberOutlinedButton(
                                onClick = onLaunchImportMerge,
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_import_rules))
                            }
                            RememberOutlinedButton(
                                onClick = {
                                    if (exportFolderReady) {
                                        scope.launch {
                                            val backupDestinations =
                                                listOf(
                                                    backupState.exportFolderUri,
                                                    backupState.cloudExportFolderUri,
                                                ).filter { it.isNotBlank() }
                                            val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
                                            val message =
                                                exportOutcome.fold(
                                                    onSuccess = { fileNames ->
                                                        resources.getQuantityString(
                                                            R.plurals.toast_exported_to_destinations,
                                                            fileNames.size,
                                                            fileNames.size,
                                                        )
                                                    },
                                                    onFailure = {
                                                        resources.getString(R.string.toast_export_failed)
                                                    },
                                                )
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_export_select_folder_first),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_export_now))
                            }
                        }
                    }
                }
                val restoreShape = ButtonDefaults.outlinedShape
                val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                val restoreLabelColor = MaterialTheme.colorScheme.error
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // heightIn (not a fixed height) lets the button grow to fit the label
                            // on large-font devices instead of vertically clipping it.
                            .heightIn(min = 40.dp)
                            .clip(restoreShape)
                            .border(BorderStroke(1.dp, restoreOutline), restoreShape),
                ) {
                    // The label is the height driver; the click layer matches the final size.
                    RememberActionLabel(
                        text = stringResource(R.string.settings_restore_backup),
                        style = MaterialTheme.typography.labelLarge.copy(color = restoreLabelColor),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                // Keep the centered label clear of the trailing 40.dp info icon.
                                .padding(horizontal = 40.dp, vertical = 6.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .appClickable(onClick = onLaunchImportReplace),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SettingsInfoDropdown(
                            title = stringResource(R.string.settings_backup_help_title),
                            tipText = stringResource(R.string.settings_backup_help_body),
                            contentDescription = backupHelpCd,
                            iconTint = restoreLabelColor.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

    if (showCloudRestoreConfirmDialog && cloudState.backups.isNotEmpty()) {
        val latestBackup = cloudState.backups.first()
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_cloud_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_cloud_restore_confirm_body)) },
            confirmButton = {
                RememberTextButton(
                    onClick = {
                        showCloudRestoreConfirmDialog = false
                        scope.launch {
                            val res = cloudBackupManager?.restoreCloudBackup(latestBackup.id)
                            if (res is CloudBackupResult.Success) {
                                snackbarHostState.showSnackbar(
                                    message = resources.getString(R.string.settings_cloud_restore_completed),
                                    duration = SnackbarDuration.Short,
                                )
                            } else if (res is CloudBackupResult.Error) {
                                snackbarHostState.showSnackbar(
                                    message = res.message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_restore_go_ahead),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                RememberTextButton(
                    onClick = { showCloudRestoreConfirmDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private fun formatBackupSize(sizeBytes: String): String {
    val bytes = sizeBytes.toLongOrNull() ?: return sizeBytes
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
}
