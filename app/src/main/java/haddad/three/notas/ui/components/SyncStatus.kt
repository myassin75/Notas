package haddad.three.notas.ui.components

/**
 * Synchronization statuses used by the cloud sync engine.
 */
enum class SyncStatus {
    NOT_CONNECTED,
    SYNCING,
    SYNCED,
    OFFLINE,
    NETWORK_ERROR,
    SESSION_EXPIRED,
    ;

    val title: String
        get() =
            when (this) {
                NOT_CONNECTED -> "Not connected"
                SYNCING -> "Syncing"
                SYNCED -> "Synced"
                OFFLINE -> "Offline"
                NETWORK_ERROR -> "Error"
                SESSION_EXPIRED -> "Session Expired"
            }

    val bannerTitle: String
        get() =
            when (this) {
                NOT_CONNECTED -> "Status unavailable"
                SYNCING -> "Sync in progress"
                SYNCED -> "Cloud Sync is up to date"
                OFFLINE -> "You’re offline"
                NETWORK_ERROR -> "Network Error"
                SESSION_EXPIRED -> "Session Expired"
            }

    val bannerDescription: String
        get() =
            when (this) {
                NOT_CONNECTED -> "Connect your account to load sync status and timestamps."
                SYNCING -> "Keep your connection active. Cloud Sync is updating your device and private server."
                SYNCED -> "Automatic sync is separate from manual backup. History and timestamps are not loaded."
                OFFLINE -> "Your local notes, tasks and events are preserved. Reconnect to the internet to resume cloud operations."
                NETWORK_ERROR -> "Unable to reach your private server. Your local data is preserved. Check your connection and try again."
                SESSION_EXPIRED -> "Sign in again to resume cloud operations. Your local notes, tasks and events are preserved on this device."
            }
}
