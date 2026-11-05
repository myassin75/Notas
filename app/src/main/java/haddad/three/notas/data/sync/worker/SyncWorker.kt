package haddad.three.notas.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Dedicated WorkManager worker executing continuous background synchronization with Google Drive.
 * Delegates orchestration, error classification, and state updates to [DriveSyncCoordinator].
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncCoordinator: DriveSyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        mapResult(syncCoordinator.executeSync(runAttempt = runAttemptCount), applicationContext)

    companion object {
        fun mapResult(executionResult: SyncExecutionResult, context: Context? = null): Result =
            when (executionResult) {
                is SyncExecutionResult.Success, is SyncExecutionResult.Skipped -> Result.success()
                is SyncExecutionResult.Retry -> {
                    if (executionResult.retryAfterSeconds != null && executionResult.retryAfterSeconds > 30L && context != null) {
                        SyncWorkScheduler.scheduleImmediateSync(
                            context = context,
                            debounceSeconds = executionResult.retryAfterSeconds,
                            replaceExisting = true,
                        )
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
                is SyncExecutionResult.Fatal -> Result.failure()
            }
    }
}

