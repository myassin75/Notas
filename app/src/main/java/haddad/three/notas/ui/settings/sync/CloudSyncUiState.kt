package haddad.three.notas.ui.settings.sync

import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveBackupStatus
import haddad.three.notas.data.drive.model.DriveRecoveryEnvelopeDto
import haddad.three.notas.data.drive.model.RestorePreview
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.data.sync.history.SyncHistoryRecordEntity
import haddad.three.notas.data.sync.worker.DriveSyncState

enum class CloudConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    AUTH_REQUIRED,
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
}

data class CloudSyncUiState(
    val isGoogleDriveSupported: Boolean = true,
    val isConnected: Boolean = false,
    val connectedEmail: String? = null,
    val connectionStatus: CloudConnectionStatus = CloudConnectionStatus.DISCONNECTED,
    val syncState: DriveSyncState = DriveSyncState.Idle,
    val lastSyncEpochMillis: Long = 0L,
    val pendingChangesCount: Int = 0,
    val unresolvedConflictsCount: Int = 0,
    val isAutoSyncEnabled: Boolean = true,
    val requiresWifiOnly: Boolean = false,
    val syncFrequencyMinutes: Long = 60L,
    val unresolvedConflicts: List<SyncConflictRecordEntity> = emptyList(),
    val allConflicts: List<SyncConflictRecordEntity> = emptyList(),
    val syncHistory: List<SyncHistoryRecordEntity> = emptyList(),
    val isSyncingNow: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val selectedConflict: SyncConflictRecordEntity? = null,
    val isResolvingConflict: Boolean = false,
    val showDisconnectConfirmation: Boolean = false,
    val backups: List<DriveBackupItem> = emptyList(),
    val isLoadingBackups: Boolean = false,
    val backupStatus: DriveBackupStatus = DriveBackupStatus.IDLE,
    val backupStatusMessage: String? = null,
    val backupProgressPercent: Float = 0f,
    val restorePreview: RestorePreview? = null,
    val needsRecoveryPhraseBackup: DriveBackupItem? = null,
    val recoveryEnvelope: DriveRecoveryEnvelopeDto? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequencyDays: Int = 1,
    val autoBackupRequiresWifi: Boolean = true,
)

