package three.haddad.smartnotas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.auth.AuthRepository
import three.haddad.smartnotas.data.auth.AuthState
import three.haddad.smartnotas.data.backup.CloudBackupManager
import three.haddad.smartnotas.data.backup.CloudBackupResult
import three.haddad.smartnotas.data.backup.CloudBackupStatus
import three.haddad.smartnotas.data.backup.CloudBackupUiState
import three.haddad.smartnotas.data.backup.isLoading
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.RememberActionLabel
import three.haddad.smartnotas.ui.components.RememberOutlinedButton
import three.haddad.smartnotas.ui.components.RememberTextButton
import three.haddad.smartnotas.ui.components.settings.GroupPosition
import three.haddad.smartnotas.ui.components.settings.GroupedListColumn
import three.haddad.smartnotas.ui.components.settings.GroupedListItem

/**
 * Cloud Backup & Private Attachment Storage section for Notas.
 * Strict online backup syncing with backend API and private user storage.
 */
@Composable
internal fun BackupSection(
    cloudBackupManager: CloudBackupManager? = null,
    authRepository: AuthRepository? = null,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val resources = LocalResources.current

    val authState by (authRepository?.authState ?: flowOf(AuthState.Unauthenticated))
        .collectAsStateWithLifecycle(initialValue = AuthState.Unauthenticated)
    val authUser = (authState as? AuthState.Authenticated)?.user ?: authRepository?.currentUser
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
