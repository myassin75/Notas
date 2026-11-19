package haddad.three.notas.data.sync.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import haddad.three.notas.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling and lifecycle of Google Drive background synchronization jobs via WorkManager.
 */
object SyncWorkScheduler {
    const val PERIODIC_WORK_NAME = "notas_drive_sync_periodic"
    const val ONE_TIME_WORK_NAME = "notas_drive_sync_one_time"
    const val WORK_TAG_SYNC = "notas_sync_worker"
    const val WORK_TAG_PERIODIC = "notas_sync_periodic"
    const val WORK_TAG_ONE_TIME = "notas_sync_one_time"

    fun sanitizeAccountEmail(accountEmail: String?): String {
        if (accountEmail.isNullOrBlank()) return "default"
        return accountEmail.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(64)
    }

    fun getPeriodicWorkName(accountEmail: String? = null): String {
        val sanitized = sanitizeAccountEmail(accountEmail)
        return if (sanitized == "default") PERIODIC_WORK_NAME else "${PERIODIC_WORK_NAME}_$sanitized"
    }

    fun getOneTimeWorkName(accountEmail: String? = null): String {
        val sanitized = sanitizeAccountEmail(accountEmail)
        return if (sanitized == "default") ONE_TIME_WORK_NAME else "${ONE_TIME_WORK_NAME}_$sanitized"
    }

    fun getAccountTag(accountEmail: String? = null): String {
        val sanitized = sanitizeAccountEmail(accountEmail)
        return "account_$sanitized"
    }

    /**
     * Builds a PeriodicWorkRequest for synchronization with battery-friendly constraints and backoff.
     */
    fun buildPeriodicWorkRequest(
        frequencyMinutes: Long = 15L,
        requiresWifi: Boolean = false,
        accountEmail: String? = null,
    ): PeriodicWorkRequest {
        val networkType = if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

        val interval = frequencyMinutes.coerceAtLeast(15L)
        val builder =
            PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_TAG_SYNC)
                .addTag(WORK_TAG_PERIODIC)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

        if (!accountEmail.isNullOrBlank()) {
            builder.addTag(getAccountTag(accountEmail))
        }

        return builder.build()
    }

    /**
     * Builds a OneTimeWorkRequest for immediate or debounced synchronization.
     */
    fun buildOneTimeWorkRequest(
        debounceSeconds: Long = 0L,
        requiresWifi: Boolean = false,
        accountEmail: String? = null,
    ): OneTimeWorkRequest {
        val networkType = if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(networkType)
                .build()

        val builder =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG_SYNC)
                .addTag(WORK_TAG_ONE_TIME)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

        if (!accountEmail.isNullOrBlank()) {
            builder.addTag(getAccountTag(accountEmail))
        }

        if (debounceSeconds > 0) {
            builder.setInitialDelay(debounceSeconds, TimeUnit.SECONDS)
        }

        return builder.build()
    }

    /**
     * Schedules periodic background sync.
     * Enforces battery-friendly constraints and exponential backoff.
     */
    fun schedulePeriodicSync(
        context: Context,
        frequencyMinutes: Long = 15L,
        requiresWifi: Boolean = false,
    ) {
        schedulePeriodicSync(context, null, frequencyMinutes, requiresWifi)
    }

    fun schedulePeriodicSync(
        context: Context,
        accountEmail: String?,
        frequencyMinutes: Long = 15L,
        requiresWifi: Boolean = false,
    ) {
        if (!isGoogleDriveSyncSupported()) return

        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val request = buildPeriodicWorkRequest(frequencyMinutes, requiresWifi, accountEmail)
        val workName = getPeriodicWorkName(accountEmail)

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Schedules an immediate or debounced one-time sync.
     * Useful for user manual triggers or debouncing rapid local database mutations.
     *
     * @param debounceSeconds Initial delay before execution. E.g. 15s to debounce typing/batch mutations.
     * @param replaceExisting When true, resets the debounce timer if work is already queued.
     */
    fun scheduleImmediateSync(
        context: Context,
        debounceSeconds: Long = 0L,
        replaceExisting: Boolean = true,
        requiresWifi: Boolean = false,
    ) {
        scheduleImmediateSync(context, null, debounceSeconds, replaceExisting, requiresWifi)
    }

    fun scheduleImmediateSync(
        context: Context,
        accountEmail: String?,
        debounceSeconds: Long = 0L,
        replaceExisting: Boolean = true,
        requiresWifi: Boolean = false,
    ) {
        if (!isGoogleDriveSyncSupported()) return

        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val request = buildOneTimeWorkRequest(debounceSeconds, requiresWifi, accountEmail)
        val workName = getOneTimeWorkName(accountEmail)

        val policy = if (replaceExisting) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(
            workName,
            policy,
            request,
        )
    }

    /**
     * Entry point for manual "Sync Now" request.
     * Executes immediately via the exact same WorkManager and sync engine.
     */
    fun syncNow(
        context: Context,
        accountEmail: String? = null,
        requiresWifi: Boolean = false,
    ) {
        scheduleImmediateSync(
            context = context,
            accountEmail = accountEmail,
            debounceSeconds = 0L,
            replaceExisting = true,
            requiresWifi = requiresWifi,
        )
    }

    /**
     * Cancels all scheduled sync jobs (used on sign-out, disconnect, or account switch).
     * Note: Pending local database changes are never deleted during cancellation.
     */
    fun cancel(context: Context) {
        cancelAll(context)
    }

    fun cancelAll(context: Context) {
        if (!isGoogleDriveSyncSupported()) return
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelAllWorkByTag(WORK_TAG_SYNC)
    }

    /**
     * Cancels work isolated to a specific account.
     */
    fun cancelForAccount(
        context: Context,
        accountEmail: String?,
    ) {
        if (!isGoogleDriveSyncSupported()) return
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val periodicName = getPeriodicWorkName(accountEmail)
        val oneTimeName = getOneTimeWorkName(accountEmail)
        val accountTag = getAccountTag(accountEmail)
        workManager.cancelUniqueWork(periodicName)
        workManager.cancelUniqueWork(oneTimeName)
        workManager.cancelAllWorkByTag(accountTag)
    }

    /**
     * Applies user sync preferences to WorkManager scheduling.
     */
    fun applyPreferences(
        context: Context,
        accountEmail: String? = null,
        prefs: DriveSyncPreferencesState,
    ) {
        if (!isGoogleDriveSyncSupported()) return
        if (!prefs.isSyncEnabled) {
            if (accountEmail != null) {
                cancelForAccount(context, accountEmail)
            } else {
                cancelAll(context)
            }
            return
        }
        schedulePeriodicSync(
            context = context,
            accountEmail = accountEmail,
            frequencyMinutes = prefs.syncFrequencyMinutes,
            requiresWifi = prefs.syncRequiresWifi,
        )
    }

    fun isGoogleDriveSyncSupported(): Boolean = BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED
}
