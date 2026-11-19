package haddad.three.notas.ui.settings.sync

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.BuildConfig
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.GoogleDriveAuthDelegate
import haddad.three.notas.data.drive.auth.GoogleDriveAuthorizationResult
import haddad.three.notas.data.drive.backup.GoogleDriveBackupProvider
import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveBackupUiState
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.drive.model.RestoreMode
import haddad.three.notas.data.drive.model.RestorePreview
import haddad.three.notas.data.drive.worker.DriveAutoBackupScheduler
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.conflict.ConflictResolutionManager
import haddad.three.notas.data.sync.conflict.ConflictResolutionResult
import haddad.three.notas.data.sync.conflict.SyncConflictRecordDao
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.data.sync.history.SyncHistoryRecordDao
import haddad.three.notas.data.sync.history.SyncHistoryRecordEntity
import haddad.three.notas.data.sync.worker.DriveSyncCoordinator
import haddad.three.notas.data.sync.worker.DriveSyncState
import haddad.three.notas.data.sync.worker.DriveSyncStateStore
import haddad.three.notas.data.sync.worker.SyncErrorCategory
import haddad.three.notas.data.sync.worker.SyncWorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SyncDataSnapshot(
    val pendingCount: Int,
    val unresolvedConflicts: List<SyncConflictRecordEntity>,
    val allConflicts: List<SyncConflictRecordEntity>,
    val history: List<SyncHistoryRecordEntity>,
)

@HiltViewModel
class CloudSyncViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val drivePrefs: DriveAuthPreferences,
        private val driveSyncStateStore: DriveSyncStateStore,
        private val driveSyncCoordinator: DriveSyncCoordinator,
        private val syncWorkScheduler: SyncWorkScheduler,
        private val conflictResolutionManager: ConflictResolutionManager,
        private val conflictRecordDao: SyncConflictRecordDao,
        private val syncHistoryDao: SyncHistoryRecordDao,
        private val pendingChangeDao: PendingChangeDao,
        private val authDelegate: GoogleDriveAuthDelegate,
        private val driveBackupProvider: GoogleDriveBackupProvider? = null,
    ) : ViewModel() {

        private val _internalUiState = MutableStateFlow(CloudSyncUiState())

        private val backupUiStateFlow: Flow<DriveBackupUiState> =
            driveBackupProvider?.uiState ?: flowOf(DriveBackupUiState())

        private val dataSnapshotFlow: Flow<SyncDataSnapshot> =
            combine(
                pendingChangeDao.countPendingChangesFlow(),
                conflictRecordDao.getUnresolvedConflictsFlow(),
                conflictRecordDao.getAllConflictRecordsFlow(),
                syncHistoryDao.getAllHistoryFlow(),
            ) { pending, unresolved, all, history ->
                SyncDataSnapshot(pending, unresolved, all, history)
            }

        val uiState: StateFlow<CloudSyncUiState> =
            combine(
                combine(
                    drivePrefs.state,
                    driveSyncStateStore.syncState,
                    driveSyncStateStore.preferencesState,
                ) { driveState, syncState, syncPrefs ->
                    Triple(driveState, syncState, syncPrefs)
                },
                dataSnapshotFlow,
                backupUiStateFlow,
                _internalUiState,
            ) { (driveState, syncState, syncPrefs), dataSnapshot, backupState, internalState ->
                val isSupported = BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED

                val connectionStatus =
                    when {
                        !isSupported -> CloudConnectionStatus.DISCONNECTED
                        !driveState.isConnected -> CloudConnectionStatus.DISCONNECTED
                        syncState is DriveSyncState.AuthenticationRequired || syncState is DriveSyncState.WaitingForAuthorization -> CloudConnectionStatus.AUTH_REQUIRED
                        syncState is DriveSyncState.Failed && syncState.errorCategory == SyncErrorCategory.PERMISSION_DENIED -> CloudConnectionStatus.PERMISSION_DENIED
                        syncState is DriveSyncState.WaitingForNetwork -> CloudConnectionStatus.NETWORK_UNAVAILABLE
                        else -> CloudConnectionStatus.CONNECTED
                    }

                val isSyncing =
                    syncState is DriveSyncState.Running ||
                        syncState is DriveSyncState.Uploading ||
                        syncState is DriveSyncState.Downloading ||
                        syncState is DriveSyncState.Applying ||
                        internalState.isSyncingNow

                internalState.copy(
                    isGoogleDriveSupported = isSupported,
                    isConnected = driveState.isConnected,
                    connectedEmail = driveState.connectedEmail,
                    connectionStatus = connectionStatus,
                    syncState = syncState,
                    lastSyncEpochMillis = syncPrefs.lastSyncEpochMillis,
                    pendingChangesCount = dataSnapshot.pendingCount,
                    unresolvedConflictsCount = dataSnapshot.unresolvedConflicts.size,
                    isAutoSyncEnabled = syncPrefs.isSyncEnabled,
                    requiresWifiOnly = syncPrefs.syncRequiresWifi,
                    syncFrequencyMinutes = syncPrefs.syncFrequencyMinutes,
                    unresolvedConflicts = dataSnapshot.unresolvedConflicts,
                    allConflicts = dataSnapshot.allConflicts,
                    syncHistory = dataSnapshot.history,
                    isSyncingNow = isSyncing,
                    backups = backupState.backups,
                    isLoadingBackups = backupState.isLoadingBackups,
                    backupStatus = backupState.status,
                    backupStatusMessage = backupState.statusMessage,
                    backupProgressPercent = backupState.progressPercent,
                    autoBackupEnabled = driveState.autoBackupEnabled,
                    autoBackupFrequencyDays = driveState.autoBackupFrequencyDays,
                    autoBackupRequiresWifi = driveState.autoBackupRequiresWifi,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CloudSyncUiState(),
            )

        fun connectGoogleDrive(
            context: Context,
            onNeedsConsent: (IntentSenderRequest) -> Unit,
        ): Job {
            if (!BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED) {
                _internalUiState.update { it.copy(errorMessage = "Google Drive sync is not supported in this flavor") }
                return Job().apply { complete() }
            }

            return viewModelScope.launch {
                _internalUiState.update { it.copy(statusMessage = "Connecting to Google Drive...") }
                when (val result = authDelegate.authorize(context, forceAccountSelection = true)) {
                    is GoogleDriveAuthorizationResult.Success -> {
                        drivePrefs.setConnected(result.accountEmail, result.accountId)
                        val prefs = driveSyncStateStore.getPreferences()
                        if (prefs.isSyncEnabled) {
                            syncWorkScheduler.schedulePeriodicSync(
                                context = context,
                                frequencyMinutes = prefs.syncFrequencyMinutes,
                                requiresWifi = prefs.syncRequiresWifi,
                                accountEmail = result.accountEmail,
                            )
                        }
                        _internalUiState.update { it.copy(statusMessage = "Connected as ${result.accountEmail}", errorMessage = null) }
                    }
                    is GoogleDriveAuthorizationResult.NeedsConsent -> {
                        _internalUiState.update { it.copy(statusMessage = null) }
                        onNeedsConsent(result.request)
                    }
                    is GoogleDriveAuthorizationResult.Failure -> {
                        _internalUiState.update { it.copy(statusMessage = null, errorMessage = result.cause.localizedMessage ?: "Authorization failed") }
                    }
                    GoogleDriveAuthorizationResult.Cancelled -> {
                        _internalUiState.update { it.copy(statusMessage = null) }
                    }
                }
            }
        }

        fun onConsentResult(context: Context, data: Intent?): Job =
            viewModelScope.launch {
                when (val parsed = authDelegate.parseConsentResult(context, data)) {
                    is GoogleDriveAuthorizationResult.Success -> {
                        drivePrefs.setConnected(parsed.accountEmail, parsed.accountId)
                        val prefs = driveSyncStateStore.getPreferences()
                        if (prefs.isSyncEnabled) {
                            syncWorkScheduler.schedulePeriodicSync(
                                context = context,
                                frequencyMinutes = prefs.syncFrequencyMinutes,
                                requiresWifi = prefs.syncRequiresWifi,
                                accountEmail = parsed.accountEmail,
                            )
                        }
                        _internalUiState.update { it.copy(statusMessage = "Connected as ${parsed.accountEmail}", errorMessage = null) }
                    }
                    is GoogleDriveAuthorizationResult.Failure -> {
                        _internalUiState.update { it.copy(errorMessage = parsed.cause.localizedMessage ?: "Consent failed") }
                    }
                    else -> Unit
                }
            }

        fun showDisconnectDialog() {
            _internalUiState.update { it.copy(showDisconnectConfirmation = true) }
        }

        fun dismissDisconnectDialog() {
            _internalUiState.update { it.copy(showDisconnectConfirmation = false) }
        }

        fun disconnectGoogleDrive(context: Context): Job =
            viewModelScope.launch {
                val currentEmail = drivePrefs.getState().connectedEmail
                if (!currentEmail.isNullOrBlank()) {
                    syncWorkScheduler.cancelForAccount(context, currentEmail)
                }
                authDelegate.disconnect(context, currentEmail)
                drivePrefs.setDisconnected()
                driveSyncStateStore.updateSyncState(DriveSyncState.Idle)
                _internalUiState.update {
                    it.copy(
                        showDisconnectConfirmation = false,
                        statusMessage = "Google Drive disconnected",
                        errorMessage = null,
                    )
                }
            }

        fun syncNow(context: Context): Job =
            viewModelScope.launch {
                val prefs = drivePrefs.getState()
                if (!prefs.isConnected || prefs.connectedEmail.isNullOrBlank()) {
                    _internalUiState.update { it.copy(errorMessage = "Please connect Google Drive first") }
                    return@launch
                }

                _internalUiState.update { it.copy(isSyncingNow = true, statusMessage = "Starting sync...") }
                try {
                    driveSyncCoordinator.executeSync()
                } finally {
                    _internalUiState.update { it.copy(isSyncingNow = false, statusMessage = null) }
                }
            }

        fun setAutoSyncEnabled(context: Context, enabled: Boolean): Job =
            viewModelScope.launch {
                val current = driveSyncStateStore.getPreferences()
                driveSyncStateStore.setSyncPreferences(
                    enabled = enabled,
                    frequencyMinutes = current.syncFrequencyMinutes,
                    requiresWifi = current.syncRequiresWifi,
                )
                val accountEmail = drivePrefs.getState().connectedEmail
                if (enabled) {
                    syncWorkScheduler.schedulePeriodicSync(
                        context = context,
                        frequencyMinutes = current.syncFrequencyMinutes,
                        requiresWifi = current.syncRequiresWifi,
                        accountEmail = accountEmail,
                    )
                } else {
                    syncWorkScheduler.cancel(context)
                }
            }

        fun setNetworkPolicy(context: Context, requiresWifi: Boolean): Job =
            viewModelScope.launch {
                val current = driveSyncStateStore.getPreferences()
                driveSyncStateStore.setSyncPreferences(
                    enabled = current.isSyncEnabled,
                    frequencyMinutes = current.syncFrequencyMinutes,
                    requiresWifi = requiresWifi,
                )
                val accountEmail = drivePrefs.getState().connectedEmail
                if (current.isSyncEnabled) {
                    syncWorkScheduler.schedulePeriodicSync(
                        context = context,
                        frequencyMinutes = current.syncFrequencyMinutes,
                        requiresWifi = requiresWifi,
                        accountEmail = accountEmail,
                    )
                }
            }

        fun setSyncFrequency(context: Context, minutes: Long): Job =
            viewModelScope.launch {
                val current = driveSyncStateStore.getPreferences()
                val clampedMinutes = minutes.coerceAtLeast(15L)
                driveSyncStateStore.setSyncPreferences(
                    enabled = current.isSyncEnabled,
                    frequencyMinutes = clampedMinutes,
                    requiresWifi = current.syncRequiresWifi,
                )
                val accountEmail = drivePrefs.getState().connectedEmail
                if (current.isSyncEnabled) {
                    syncWorkScheduler.schedulePeriodicSync(
                        context = context,
                        frequencyMinutes = clampedMinutes,
                        requiresWifi = current.syncRequiresWifi,
                        accountEmail = accountEmail,
                    )
                }
            }

        fun selectConflict(conflict: SyncConflictRecordEntity?) {
            _internalUiState.update { it.copy(selectedConflict = conflict) }
        }

        fun resolveConflictKeepLocal(conflictId: String): Job =
            viewModelScope.launch {
                _internalUiState.update { it.copy(isResolvingConflict = true) }
                val result = conflictResolutionManager.keepLocal(conflictId)
                handleResolutionResult(result)
            }

        fun resolveConflictKeepRemote(conflictId: String): Job =
            viewModelScope.launch {
                _internalUiState.update { it.copy(isResolvingConflict = true) }
                val result = conflictResolutionManager.keepRemote(conflictId)
                handleResolutionResult(result)
            }

        fun resolveConflictKeepBoth(conflictId: String): Job =
            viewModelScope.launch {
                _internalUiState.update { it.copy(isResolvingConflict = true) }
                val result = conflictResolutionManager.keepBoth(conflictId)
                handleResolutionResult(result)
            }

        fun resolveConflictMerge(conflictId: String, title: String, body: String): Job =
            viewModelScope.launch {
                _internalUiState.update { it.copy(isResolvingConflict = true) }
                val result = conflictResolutionManager.merge(conflictId, title, body)
                handleResolutionResult(result)
            }

        fun resolveConflictRestoreDeleted(conflictId: String): Job =
            viewModelScope.launch {
                _internalUiState.update { it.copy(isResolvingConflict = true) }
                val result = conflictResolutionManager.restoreDeleted(conflictId)
                handleResolutionResult(result)
            }

        private fun handleResolutionResult(result: ConflictResolutionResult) {
            when (result) {
                is ConflictResolutionResult.Success -> {
                    _internalUiState.update {
                        it.copy(
                            isResolvingConflict = false,
                            selectedConflict = null,
                            statusMessage = result.message,
                        )
                    }
                }
                is ConflictResolutionResult.Error -> {
                    _internalUiState.update {
                        it.copy(
                            isResolvingConflict = false,
                            errorMessage = result.error,
                        )
                    }
                }
            }
        }

        fun decryptPayload(stored: String?, conflictId: String): String? =
            conflictResolutionManager.decryptPayload(stored, conflictId)

        fun clearSyncHistory(): Job =
            viewModelScope.launch {
                syncHistoryDao.clearAll()
            }

        fun clearError() {
            _internalUiState.update { it.copy(errorMessage = null) }
        }

        fun clearStatus() {
            _internalUiState.update { it.copy(statusMessage = null) }
        }

        fun refreshBackups() {
            viewModelScope.launch {
                driveBackupProvider?.refreshBackupsList()
            }
        }

        fun createBackup() {
            viewModelScope.launch {
                _internalUiState.update { it.copy(statusMessage = "Starting backup...") }
                val res = driveBackupProvider?.createBackup()
                when (res) {
                    is DriveResult.Success -> {
                        _internalUiState.update { it.copy(statusMessage = "Backup created successfully!") }
                    }
                    is DriveResult.Error -> {
                        _internalUiState.update { it.copy(errorMessage = res.message) }
                    }
                    else -> Unit
                }
            }
        }

        fun deleteBackup(fileId: String) {
            viewModelScope.launch {
                val res = driveBackupProvider?.deleteBackup(fileId)
                if (res is DriveResult.Error) {
                    _internalUiState.update { it.copy(errorMessage = res.message) }
                }
            }
        }

        fun requestRestorePreview(item: DriveBackupItem) {
            val preview =
                driveBackupProvider?.getRestorePreview(item)
                    ?: RestorePreview(backupItem = item)
            _internalUiState.update { it.copy(restorePreview = preview) }
        }

        fun dismissRestorePreview() {
            _internalUiState.update { it.copy(restorePreview = null) }
        }

        fun confirmRestore(
            item: DriveBackupItem,
            mode: RestoreMode,
            recoveryPhrase: String? = null,
        ) {
            _internalUiState.update { it.copy(restorePreview = null) }
            viewModelScope.launch {
                _internalUiState.update { it.copy(statusMessage = "Starting restore...") }
                val res =
                    driveBackupProvider?.restoreBackup(
                        fileId = item.fileId,
                        mode = mode,
                        recoveryPhrase = recoveryPhrase,
                    )
                when (res) {
                    is DriveResult.Success -> {
                        _internalUiState.update {
                            it.copy(
                                statusMessage = "Restore completed successfully: ${res.data.restoredNotesCount} notes restored.",
                                needsRecoveryPhraseBackup = null,
                                recoveryEnvelope = null,
                            )
                        }
                    }
                    is DriveResult.NeedsRecoveryPhrase -> {
                        _internalUiState.update {
                            it.copy(
                                needsRecoveryPhraseBackup = res.backupItem,
                                recoveryEnvelope = res.envelope,
                            )
                        }
                    }
                    is DriveResult.Error -> {
                        _internalUiState.update { it.copy(errorMessage = res.message) }
                    }
                    else -> Unit
                }
            }
        }

        fun dismissRecoveryPhraseDialog() {
            _internalUiState.update {
                it.copy(
                    needsRecoveryPhraseBackup = null,
                    recoveryEnvelope = null,
                )
            }
        }

        fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
            viewModelScope.launch {
                val state = drivePrefs.getState()
                drivePrefs.setAutoBackupSettings(
                    enabled = enabled,
                    frequencyDays = state.autoBackupFrequencyDays,
                    requiresWifi = state.autoBackupRequiresWifi,
                )
                if (enabled) {
                    DriveAutoBackupScheduler.schedule(
                        context = context,
                        frequencyDays = state.autoBackupFrequencyDays,
                        requiresWifi = state.autoBackupRequiresWifi,
                    )
                } else {
                    DriveAutoBackupScheduler.cancel(context)
                }
            }
        }

        fun setAutoBackupFrequency(context: Context, days: Int) {
            viewModelScope.launch {
                val state = drivePrefs.getState()
                drivePrefs.setAutoBackupSettings(
                    enabled = state.autoBackupEnabled,
                    frequencyDays = days,
                    requiresWifi = state.autoBackupRequiresWifi,
                )
                if (state.autoBackupEnabled) {
                    DriveAutoBackupScheduler.schedule(
                        context = context,
                        frequencyDays = days,
                        requiresWifi = state.autoBackupRequiresWifi,
                    )
                }
            }
        }

        fun setAutoBackupWifiRequired(context: Context, wifiOnly: Boolean) {
            viewModelScope.launch {
                val state = drivePrefs.getState()
                drivePrefs.setAutoBackupSettings(
                    enabled = state.autoBackupEnabled,
                    frequencyDays = state.autoBackupFrequencyDays,
                    requiresWifi = wifiOnly,
                )
                if (state.autoBackupEnabled) {
                    DriveAutoBackupScheduler.schedule(
                        context = context,
                        frequencyDays = state.autoBackupFrequencyDays,
                        requiresWifi = wifiOnly,
                    )
                }
            }
        }
    }
