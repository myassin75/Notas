package haddad.three.notas.ui.settings.sync

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import haddad.three.notas.BuildConfig
import haddad.three.notas.R
import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveBackupStatus
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.drive.model.isLoading
import haddad.three.notas.data.sync.worker.DriveSyncState
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberActionLabel
import haddad.three.notas.ui.components.RememberButton
import haddad.three.notas.ui.components.RememberOutlinedButton
import haddad.three.notas.ui.components.RememberTextButton
import haddad.three.notas.ui.components.settings.GroupPosition
import haddad.three.notas.ui.components.settings.GroupedListColumn
import haddad.three.notas.ui.components.settings.GroupedListItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncSection(
    modifier: Modifier = Modifier,
    viewModel: CloudSyncViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.REPLACE) }
    var recoveryPhraseText by remember { mutableStateOf("") }
    var backupToDelete by remember { mutableStateOf<DriveBackupItem?>(null) }

    val consentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onConsentResult(context, result.data)
            }
        }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            viewModel.refreshBackups()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val err = uiState.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState?.showSnackbar("Error: $err")
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState?.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    if (!uiState.isGoogleDriveSupported) {
        // Offline flavor clean fallback
        GroupedListColumn(modifier = modifier.fillMaxWidth()) {
            GroupedListItem(position = GroupPosition.ONLY) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "cloud_off",
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.cloud_sync_offline_flavor_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Account Management Section
        GroupedListColumn {
            GroupedListItem(position = if (uiState.isConnected) GroupPosition.FIRST else GroupPosition.ONLY) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.cloud_sync_account_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (uiState.isConnected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E7D32)),
                                    )
                                    Text(
                                        text = stringResource(R.string.cloud_sync_connected_as, uiState.connectedEmail ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.cloud_sync_not_connected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.cloud_sync_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )

                    if (!uiState.isConnected) {
                        RememberButton(
                            onClick = {
                                viewModel.connectGoogleDrive(context) { request ->
                                    consentLauncher.launch(request)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RememberActionLabel(stringResource(R.string.cloud_sync_connect_button))
                        }
                    }
                }
            }

            if (uiState.isConnected) {
                GroupedListItem(position = GroupPosition.LAST) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.showDisconnectDialog() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "logout",
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.cloud_sync_disconnect_button),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // 2. Synchronization Dashboard & Manual Sync Now (when connected)
        if (uiState.isConnected) {
            GroupedListColumn {
                GroupedListItem(position = GroupPosition.FIRST) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_sync_status_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            // Status badge
                            SyncStateBadge(syncState = uiState.syncState)
                        }

                        // Progress indicator when actively syncing
                        AnimatedVisibility(visible = uiState.isSyncingNow) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = getSyncStateDescription(uiState.syncState),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Metrics
                        val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                        val lastSyncStr =
                            remember(uiState.lastSyncEpochMillis) {
                                if (uiState.lastSyncEpochMillis > 0L) {
                                    dateFormat.format(Date(uiState.lastSyncEpochMillis))
                                } else {
                                    "Never"
                                }
                            }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_sync_last_sync, lastSyncStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.cloud_sync_pending_changes, uiState.pendingChangesCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Reauth button if authorization expired
                        if (uiState.connectionStatus == CloudConnectionStatus.AUTH_REQUIRED) {
                            RememberButton(
                                onClick = {
                                    viewModel.connectGoogleDrive(context) { request ->
                                        consentLauncher.launch(request)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RememberActionLabel(stringResource(R.string.cloud_sync_reauth_button))
                            }
                        } else {
                            // Sync Now button
                            RememberButton(
                                onClick = { viewModel.syncNow(context) },
                                enabled = !uiState.isSyncingNow && uiState.connectionStatus != CloudConnectionStatus.NETWORK_UNAVAILABLE,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uiState.isSyncingNow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    RememberMaterialRoundedSymbol(
                                        name = "sync",
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                RememberActionLabel(stringResource(R.string.cloud_sync_sync_now))
                            }
                        }
                    }
                }

                // 3. Automatic Synchronization Settings
                GroupedListItem(position = GroupPosition.MIDDLE) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cloud_sync_auto_sync_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.cloud_sync_auto_sync_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = uiState.isAutoSyncEnabled,
                                onCheckedChange = { viewModel.setAutoSyncEnabled(context, it) },
                            )
                        }

                        if (uiState.isAutoSyncEnabled) {
                            HorizontalDivider()

                            // Network Policy
                            Text(
                                text = stringResource(R.string.cloud_sync_network_policy_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.setNetworkPolicy(context, false) },
                                ) {
                                    RadioButton(
                                        selected = !uiState.requiresWifiOnly,
                                        onClick = { viewModel.setNetworkPolicy(context, false) },
                                    )
                                    Text(
                                        text = stringResource(R.string.cloud_sync_network_any),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.setNetworkPolicy(context, true) },
                                ) {
                                    RadioButton(
                                        selected = uiState.requiresWifiOnly,
                                        onClick = { viewModel.setNetworkPolicy(context, true) },
                                    )
                                    Text(
                                        text = stringResource(R.string.cloud_sync_network_wifi_only),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            HorizontalDivider()

                            // Frequency Options
                            Text(
                                text = stringResource(R.string.cloud_sync_frequency_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            val frequencies =
                                listOf(
                                    15L to stringResource(R.string.cloud_sync_freq_15m),
                                    30L to stringResource(R.string.cloud_sync_freq_30m),
                                    60L to stringResource(R.string.cloud_sync_freq_1h),
                                    360L to stringResource(R.string.cloud_sync_freq_6h),
                                    720L to stringResource(R.string.cloud_sync_freq_12h),
                                    1440L to stringResource(R.string.cloud_sync_freq_24h),
                                )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                frequencies.forEach { (mins, label) ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.setSyncFrequency(context, mins) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = uiState.syncFrequencyMinutes == mins,
                                            onClick = { viewModel.setSyncFrequency(context, mins) },
                                        )
                                        Text(text = label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Conflict Management Entry
                GroupedListItem(position = GroupPosition.MIDDLE) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (uiState.unresolvedConflicts.isNotEmpty()) {
                                        viewModel.selectConflict(uiState.unresolvedConflicts.first())
                                    }
                                }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.cloud_sync_conflicts_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text =
                                    if (uiState.unresolvedConflictsCount > 0) {
                                        stringResource(R.string.cloud_sync_conflicts_subtitle, uiState.unresolvedConflictsCount)
                                    } else {
                                        stringResource(R.string.cloud_sync_no_conflicts)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (uiState.unresolvedConflictsCount > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }

                        if (uiState.unresolvedConflictsCount > 0) {
                            RememberOutlinedButton(
                                onClick = { viewModel.selectConflict(uiState.unresolvedConflicts.first()) },
                            ) {
                                RememberActionLabel("Resolve")
                            }
                        } else {
                            RememberMaterialRoundedSymbol(
                                name = "check_circle",
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // 5. Sync History & Diagnostics Entry
                GroupedListItem(position = GroupPosition.LAST) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { showHistorySheet = true }
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.cloud_sync_history_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${uiState.syncHistory.size} records",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 3. Encrypted Google Drive Backups
            GroupedListColumn {
                GroupedListItem(position = GroupPosition.FIRST) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cloud_backup_section_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.cloud_backup_section_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (uiState.backupStatus.isLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = uiState.backupStatusMessage ?: "Processing backup...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (uiState.backupProgressPercent > 0f) {
                                    LinearProgressIndicator(
                                        progress = { uiState.backupProgressPercent },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }

                        RememberButton(
                            onClick = { viewModel.createBackup() },
                            enabled = !uiState.backupStatus.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (uiState.backupStatus.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                RememberMaterialRoundedSymbol(
                                    name = "backup",
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            RememberActionLabel(stringResource(R.string.cloud_backup_backup_now))
                        }
                    }
                }

                // Automatic Daily Backup Settings
                GroupedListItem(position = GroupPosition.MIDDLE) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cloud_backup_auto_backup_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.cloud_backup_auto_backup_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = uiState.autoBackupEnabled,
                                onCheckedChange = { viewModel.setAutoBackupEnabled(context, it) },
                            )
                        }

                        if (uiState.autoBackupEnabled) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.cloud_sync_network_wifi_only),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Switch(
                                    checked = uiState.autoBackupRequiresWifi,
                                    onCheckedChange = { viewModel.setAutoBackupWifiRequired(context, it) },
                                )
                            }
                        }
                    }
                }

                // Backups List
                GroupedListItem(position = GroupPosition.LAST) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Available Backups (${uiState.backups.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (uiState.isLoadingBackups) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                RememberTextButton(onClick = { viewModel.refreshBackups() }) {
                                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (uiState.backups.isEmpty() && !uiState.isLoadingBackups) {
                            Text(
                                text = stringResource(R.string.cloud_backup_no_backups),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val backupDateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
                            uiState.backups.forEachIndexed { index, backup ->
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = backupDateFormat.format(Date(backup.createdAtEpochMillis)),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = "${formatBackupBytes(backup.sizeBytes)} • ${backup.notesCount} notes • ${backup.eventsCount} events • ${backup.attachmentsCount} files • ${backup.sourceDevice.ifBlank { "Android" }} (v${backup.appVersion})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        RememberTextButton(
                                            onClick = { viewModel.requestRestorePreview(backup) },
                                            enabled = !uiState.backupStatus.isLoading,
                                        ) {
                                            Text(stringResource(R.string.cloud_backup_restore_button), style = MaterialTheme.typography.labelSmall)
                                        }
                                        RememberTextButton(
                                            onClick = { backupToDelete = backup },
                                            enabled = !uiState.backupStatus.isLoading,
                                        ) {
                                            Text(
                                                stringResource(R.string.cloud_backup_delete_button),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Disconnect Confirmation Dialog
    if (uiState.showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDisconnectDialog() },
            title = {
                Text(
                    text = stringResource(R.string.cloud_sync_disconnect_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.cloud_sync_disconnect_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                RememberButton(
                    onClick = { viewModel.disconnectGoogleDrive(context) },
                ) {
                    RememberActionLabel(stringResource(R.string.cloud_sync_disconnect_confirm))
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { viewModel.dismissDisconnectDialog() }) {
                    RememberActionLabel("Cancel")
                }
            },
        )
    }

    // Conflict Resolution Dialog
    uiState.selectedConflict?.let { conflict ->
        ConflictManagementDialog(
            conflict = conflict,
            onDismiss = { viewModel.selectConflict(null) },
            onKeepLocal = { viewModel.resolveConflictKeepLocal(conflict.conflictId) },
            onKeepRemote = { viewModel.resolveConflictKeepRemote(conflict.conflictId) },
            onKeepBoth = { viewModel.resolveConflictKeepBoth(conflict.conflictId) },
            onMerge = { title, body -> viewModel.resolveConflictMerge(conflict.conflictId, title, body) },
            onRestoreDeleted = { viewModel.resolveConflictRestoreDeleted(conflict.conflictId) },
            decryptPayload = viewModel::decryptPayload,
            isResolving = uiState.isResolvingConflict,
        )
    }

    // History Bottom Sheet
    if (showHistorySheet) {
        SyncHistoryBottomSheet(
            sheetState = historySheetState,
            history = uiState.syncHistory,
            pendingChangesCount = uiState.pendingChangesCount,
            onClearHistory = { viewModel.clearSyncHistory() },
            onDismiss = { showHistorySheet = false },
        )
    }

    // Restore Preview Dialog
    uiState.restorePreview?.let { preview ->
        val backup = preview.backupItem
        val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestorePreview() },
            title = {
                Text(
                    text = stringResource(R.string.cloud_backup_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.cloud_backup_preview_source, backup.sourceDevice.ifBlank { "Android" }, backup.appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Created: ${dateFormat.format(Date(backup.createdAtEpochMillis))}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.cloud_backup_preview_items,
                            backup.notesCount,
                            backup.tasksCount,
                            backup.eventsCount,
                            backup.categoriesCount,
                            backup.attachmentsCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.cloud_backup_preview_size, formatBackupBytes(backup.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (!preview.isCompatible) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = "Incompatible schema version: v${backup.schemaVersion}. Please update the app.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Restore Mode:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedRestoreMode = RestoreMode.REPLACE },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedRestoreMode == RestoreMode.REPLACE,
                            onClick = { selectedRestoreMode = RestoreMode.REPLACE },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(stringResource(R.string.cloud_backup_mode_replace), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.cloud_backup_mode_replace_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedRestoreMode = RestoreMode.MERGE },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedRestoreMode == RestoreMode.MERGE,
                            onClick = { selectedRestoreMode = RestoreMode.MERGE },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(stringResource(R.string.cloud_backup_mode_merge), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.cloud_backup_mode_merge_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                RememberButton(
                    onClick = { viewModel.confirmRestore(backup, selectedRestoreMode) },
                    enabled = preview.isCompatible,
                ) {
                    RememberActionLabel(stringResource(R.string.cloud_backup_restore_button))
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { viewModel.dismissRestorePreview() }) {
                    RememberActionLabel("Cancel")
                }
            },
        )
    }

    // Recovery Phrase Dialog (for new device / missing MEK)
    uiState.needsRecoveryPhraseBackup?.let { backup ->
        val words = recoveryPhraseText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val isValidWordCount = words.size == 12

        AlertDialog(
            onDismissRequest = { viewModel.dismissRecoveryPhraseDialog() },
            title = {
                Text(
                    text = stringResource(R.string.cloud_backup_recovery_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.cloud_backup_recovery_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = recoveryPhraseText,
                        onValueChange = { recoveryPhraseText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.cloud_backup_recovery_input_hint)) },
                        supportingText = {
                            Text(
                                text = "${words.size} / 12 words",
                                color = if (isValidWordCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            },
            confirmButton = {
                RememberButton(
                    onClick = {
                        val phrase = recoveryPhraseText.trim()
                        recoveryPhraseText = ""
                        viewModel.confirmRestore(backup, selectedRestoreMode, phrase)
                    },
                    enabled = isValidWordCount,
                ) {
                    RememberActionLabel(stringResource(R.string.cloud_backup_recovery_confirm))
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { viewModel.dismissRecoveryPhraseDialog() }) {
                    RememberActionLabel("Cancel")
                }
            },
        )
    }

    // Delete Confirmation Dialog
    backupToDelete?.let { backup ->
        val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = { Text("Delete Cloud Backup?") },
            text = {
                Text(
                    "Are you sure you want to delete the backup from ${dateFormat.format(Date(backup.createdAtEpochMillis))}? " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                RememberTextButton(
                    onClick = {
                        val fileId = backup.fileId
                        backupToDelete = null
                        viewModel.deleteBackup(fileId)
                    },
                ) {
                    Text(stringResource(R.string.cloud_backup_delete_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { backupToDelete = null }) {
                    RememberActionLabel("Cancel")
                }
            },
        )
    }
}

private fun formatBackupBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    }

@Composable
private fun SyncStateBadge(syncState: DriveSyncState) {
    val (label, color) =
        when (syncState) {
            DriveSyncState.Idle -> stringResource(R.string.cloud_sync_state_idle) to Color(0xFF2E7D32)
            DriveSyncState.Queued -> stringResource(R.string.cloud_sync_state_queued) to MaterialTheme.colorScheme.primary
            DriveSyncState.Running -> stringResource(R.string.cloud_sync_state_running) to MaterialTheme.colorScheme.primary
            DriveSyncState.Uploading -> stringResource(R.string.cloud_sync_state_uploading) to MaterialTheme.colorScheme.primary
            DriveSyncState.Downloading -> stringResource(R.string.cloud_sync_state_downloading) to MaterialTheme.colorScheme.primary
            DriveSyncState.Applying -> stringResource(R.string.cloud_sync_state_applying) to MaterialTheme.colorScheme.primary
            DriveSyncState.WaitingForNetwork -> stringResource(R.string.cloud_sync_state_waiting_network) to MaterialTheme.colorScheme.outline
            is DriveSyncState.WaitingForAuthorization -> stringResource(R.string.cloud_sync_state_waiting_auth) to Color(0xFFF57C00)
            is DriveSyncState.AuthenticationRequired -> stringResource(R.string.cloud_sync_state_waiting_auth) to Color(0xFFF57C00)
            is DriveSyncState.Retrying -> stringResource(R.string.cloud_sync_state_retrying, syncState.attempt, syncState.maxAttempts) to Color(0xFFF57C00)
            is DriveSyncState.ConflictsDetected -> stringResource(R.string.cloud_sync_state_conflicts, syncState.count) to MaterialTheme.colorScheme.error
            is DriveSyncState.Completed -> stringResource(R.string.cloud_sync_state_idle) to Color(0xFF2E7D32)
            is DriveSyncState.Failed -> stringResource(R.string.cloud_sync_state_failed) to MaterialTheme.colorScheme.error
            else -> "Idle" to Color(0xFF2E7D32)
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun getSyncStateDescription(syncState: DriveSyncState): String =
    when (syncState) {
        DriveSyncState.Uploading -> stringResource(R.string.cloud_sync_state_uploading)
        DriveSyncState.Downloading -> stringResource(R.string.cloud_sync_state_downloading)
        DriveSyncState.Applying -> stringResource(R.string.cloud_sync_state_applying)
        DriveSyncState.Running -> stringResource(R.string.cloud_sync_state_running)
        DriveSyncState.Queued -> stringResource(R.string.cloud_sync_state_queued)
        else -> ""
    }

