package haddad.three.notas.data.sync.worker

/**
 * Observable synchronization states for Google Drive Continuous Sync.
 */
sealed interface DriveSyncState {
    data object Idle : DriveSyncState

    data object Scheduled : DriveSyncState

    data object Running : DriveSyncState

    data object WaitingForNetwork : DriveSyncState

    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
        val delayMillis: Long? = null,
    ) : DriveSyncState

    data class AuthenticationRequired(
        val message: String? = null,
    ) : DriveSyncState

    data class Completed(
        val timestampEpochMillis: Long,
    ) : DriveSyncState

    data class Failed(
        val errorCategory: SyncErrorCategory,
        val message: String? = null,
    ) : DriveSyncState
}

/**
 * Coarse error categories for observability and diagnostic reporting.
 * NEVER contains sensitive note content, encryption keys, or auth tokens.
 */
enum class SyncErrorCategory {
    NETWORK,
    RATE_LIMITED,
    AUTHENTICATION,
    ACCOUNT_MISMATCH,
    MEK_UNAVAILABLE,
    INTEGRITY,
    SERVER_ERROR,
    UNKNOWN,
}

/**
 * Result returned by [DriveSyncCoordinator.executeSync].
 */
sealed interface SyncExecutionResult {
    data class Success(
        val completedAtEpochMillis: Long,
    ) : SyncExecutionResult

    data class Skipped(
        val reason: String,
    ) : SyncExecutionResult

    data class Retry(
        val errorCategory: SyncErrorCategory,
        val message: String,
        val retryAfterSeconds: Long? = null,
    ) : SyncExecutionResult

    data class Fatal(
        val errorCategory: SyncErrorCategory,
        val message: String,
    ) : SyncExecutionResult
}

/**
 * Persisted sync settings and metadata.
 */
data class DriveSyncPreferencesState(
    val isSyncEnabled: Boolean = true,
    val syncFrequencyMinutes: Long = 15L,
    val syncRequiresWifi: Boolean = false,
    val lastSyncEpochMillis: Long = 0L,
    val lastErrorCategory: SyncErrorCategory? = null,
    val lastErrorMessage: String? = null,
)
