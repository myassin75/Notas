package haddad.three.notas.data.drive.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.backup.GoogleDriveBackupProvider
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.diagnostics.DiagnosticLog
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker executing background encrypted backups to Google Drive.
 *
 * Enforces strict preconditions:
 * - User must have opted in.
 * - Valid Google Drive authorization must exist.
 * - Master Encryption Key (MEK) must be provisioned and accessible.
 * - Network and battery constraints must be satisfied.
 */
@HiltWorker
class DriveAutoBackupWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val drivePrefs: DriveAuthPreferences,
        private val driveBackupProvider: GoogleDriveBackupProvider,
        private val mekManager: MekManager,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result = executeAutoBackup(runAttempt = runAttemptCount)

        suspend fun executeAutoBackup(runAttempt: Int = 0): Result {
            val state = drivePrefs.getState()
            if (!state.autoBackupEnabled) {
                return Result.success()
            }

            if (!state.isConnected) {
                DiagnosticLog.record(applicationContext, "Drive auto-backup skipped: not connected to Google Drive")
                return Result.failure()
            }

            if (mekManager.getActiveMasterKey() == null) {
                DiagnosticLog.record(applicationContext, "Drive auto-backup skipped: MEK not available")
                return Result.failure()
            }

            return try {
                val result = driveBackupProvider.createBackup()
                when (result) {
                    is DriveResult.Success -> {
                        DiagnosticLog.record(applicationContext, "Drive auto-backup completed: backupId=${result.data.backupId}")
                        Result.success()
                    }
                    is DriveResult.RateLimited -> Result.retry()
                    is DriveResult.Offline -> Result.retry()
                    is DriveResult.Unauthorized -> {
                        DiagnosticLog.record(applicationContext, "Drive auto-backup failed: unauthorized token")
                        Result.failure()
                    }
                    is DriveResult.Error -> {
                        val attempt = runAttempt + 1
                        DiagnosticLog.record(applicationContext, "Drive auto-backup error (attempt $attempt): ${result.message}")
                        if (attempt < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
                    }
                }
            } catch (e: Exception) {
                DiagnosticLog.record(applicationContext, "Drive auto-backup crashed: ${e.message}", e)
                if (runAttempt < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
            }
        }

        companion object {
            private const val MAX_RETRY_ATTEMPTS = 3
        }
    }

/**
 * Idempotent scheduler for Google Drive automatic periodic backups.
 */
object DriveAutoBackupScheduler {
    const val UNIQUE_WORK_NAME = "notas_drive_auto_backup"

    fun schedule(
        context: Context,
        frequencyDays: Int = 1,
        requiresWifi: Boolean = true,
    ) {
        val workManager = WorkManager.getInstance(context)
        val networkType = if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

        val request =
            PeriodicWorkRequestBuilder<DriveAutoBackupWorker>(
                repeatInterval = frequencyDays.toLong().coerceAtLeast(1L),
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            ).setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
