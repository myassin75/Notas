package haddad.three.notas.data.sync.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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

    /**
     * Schedules periodic background sync.
     * Enforces battery-friendly constraints and exponential backoff.
     */
    fun schedulePeriodicSync(
        context: Context,
        frequencyMinutes: Long = 15L,
        requiresWifi: Boolean = false,
    ) {
        if (!isGoogleDriveSyncSupported()) return

        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val networkType = if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

        val interval = frequencyMinutes.coerceAtLeast(15L)
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_TAG_SYNC)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
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
        if (!isGoogleDriveSyncSupported()) return

        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

        if (debounceSeconds > 0) {
            builder.setInitialDelay(debounceSeconds, TimeUnit.SECONDS)
        }

        val policy = if (replaceExisting) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            policy,
            builder.build(),
        )
    }

    /**
     * Cancels all scheduled sync jobs (used on sign-out, disconnect, or account switch).
     * Note: Pending local database changes are never deleted during cancellation.
     */
    fun cancel(context: Context) {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelAllWorkByTag(WORK_TAG_SYNC)
    }

    fun isGoogleDriveSyncSupported(): Boolean = BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED
}
