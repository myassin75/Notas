package haddad.three.notas.data.sync.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.BuildConfig
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.sync.SyncStateStore
import haddad.three.notas.data.sync.change.PendingChangeDao
import haddad.three.notas.data.sync.ingestion.RemoteJournalIngestionEngine
import haddad.three.notas.data.sync.journal.JournalCreationResult
import haddad.three.notas.data.sync.journal.SyncJournalDao
import haddad.three.notas.data.sync.journal.SyncJournalWriter
import haddad.three.notas.data.sync.transport.DiscoveryResult
import haddad.three.notas.data.sync.transport.JournalDiscoveryService
import haddad.three.notas.data.sync.transport.JournalDownloader
import haddad.three.notas.data.sync.transport.JournalUploader
import haddad.three.notas.diagnostics.DiagnosticLog
import haddad.three.notas.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates end-to-end continuous synchronization with Google Drive appDataFolder.
 *
 * Sequence of operations:
 * 1. Validate preconditions: build flavor support, account authorization, MEK availability, rate limit status.
 * 2. Pull remote changes: discover headers -> download and validate -> ingest into local Room DB.
 * 3. Push local changes: package pending changes into encrypted journals -> upload to Drive.
 * 4. Post-upload drain pass: package and upload any pending changes that arrived while sync was active.
 * 5. Update sync state, timestamps, and error classifications.
 */
@Singleton
class DriveSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drivePrefs: DriveAuthPreferences,
    private val driveSyncStateStore: DriveSyncStateStore,
    private val mekManager: MekManager,
    private val syncStateStore: SyncStateStore,
    private val discoveryService: JournalDiscoveryService,
    private val downloader: JournalDownloader,
    private val ingestionEngine: RemoteJournalIngestionEngine,
    private val journalWriter: SyncJournalWriter,
    private val uploader: JournalUploader,
    private val journalDao: SyncJournalDao,
    private val pendingChangeDao: PendingChangeDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()

    companion object {
        const val MAX_RETRIES = 3
        const val MAX_JOURNAL_CREATION_BATCHES = 50
        const val MAX_DRAIN_PASSES = 3
    }

    private fun log(message: String, throwable: Throwable? = null) {
        runCatching { DiagnosticLog.record(context, message, throwable) }
    }

    suspend fun executeSync(runAttempt: Int = 0): SyncExecutionResult = withContext(ioDispatcher) {
        if (!BuildConfig.GOOGLE_DRIVE_SYNC_ENABLED) {
            log("Drive sync skipped: not supported in build flavor")
            return@withContext SyncExecutionResult.Skipped("Google Drive sync disabled in this flavor")
        }

        val prefs = drivePrefs.getState()
        if (!prefs.isConnected) {
            log("Drive sync skipped: Google Drive is not connected")
            driveSyncStateStore.updateSyncState(DriveSyncState.Idle)
            return@withContext SyncExecutionResult.Skipped("Google Drive not connected")
        }

        val syncPrefs = driveSyncStateStore.getPreferences()
        if (!syncPrefs.isSyncEnabled) {
            log("Drive sync skipped: sync disabled in preferences")
            driveSyncStateStore.updateSyncState(DriveSyncState.Idle)
            return@withContext SyncExecutionResult.Skipped("Sync disabled in preferences")
        }

        if (prefs.connectedEmail.isNullOrBlank()) {
            log("Drive sync aborted: connected account email is missing")
            driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Account email missing"))
            driveSyncStateStore.recordSyncFailed(SyncErrorCategory.ACCOUNT_MISMATCH, "Account email missing")
            return@withContext SyncExecutionResult.Fatal(SyncErrorCategory.ACCOUNT_MISMATCH, "Account email missing")
        }

        if (mekManager.getActiveMasterKey() == null) {
            log("Drive sync aborted: Master Encryption Key (MEK) not available")
            driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Master Encryption Key unavailable"))
            driveSyncStateStore.recordSyncFailed(SyncErrorCategory.MEK_UNAVAILABLE, "MEK unavailable")
            return@withContext SyncExecutionResult.Fatal(SyncErrorCategory.MEK_UNAVAILABLE, "MEK unavailable")
        }

        val localDeviceId = syncStateStore.getOrCreateDeviceId()
        if (localDeviceId.isBlank()) {
            log("Drive sync aborted: local device ID is blank")
            driveSyncStateStore.updateSyncState(DriveSyncState.Failed(SyncErrorCategory.UNKNOWN, "Device ID unavailable"))
            return@withContext SyncExecutionResult.Fatal(SyncErrorCategory.UNKNOWN, "Device ID unavailable")
        }

        // Check if server rate limit is active
        val rateLimitExpiresAt = driveSyncStateStore.getRateLimitExpiresAt()
        val now = System.currentTimeMillis()
        if (now < rateLimitExpiresAt) {
            val remainingSeconds = (rateLimitExpiresAt - now + 999) / 1000
            log("Drive sync deferred: server rate limit active ($remainingSeconds seconds remaining)")
            if (runAttempt < MAX_RETRIES) {
                driveSyncStateStore.updateSyncState(DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES, remainingSeconds * 1000))
                return@withContext SyncExecutionResult.Retry(
                    SyncErrorCategory.RATE_LIMITED,
                    "Server rate limit active until $rateLimitExpiresAt",
                    retryAfterSeconds = remainingSeconds,
                )
            } else {
                driveSyncStateStore.updateSyncState(DriveSyncState.Failed(SyncErrorCategory.RATE_LIMITED, "Server rate limit active"))
                driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Server rate limit active")
                return@withContext SyncExecutionResult.Fatal(
                    SyncErrorCategory.RATE_LIMITED,
                    "Server rate limit active until $rateLimitExpiresAt",
                )
            }
        }

        // Mutual exclusion: suspend to guarantee sequential execution without lost wake-ups
        return@withContext syncMutex.withLock {
            val initialAccountEmail = prefs.connectedEmail
            val initialAccountId = prefs.connectedAccountId

            suspend fun checkAccountValid(): Boolean {
                val current = drivePrefs.getState()
                return current.isConnected &&
                    current.connectedEmail == initialAccountEmail &&
                    (initialAccountId == null || current.connectedAccountId == initialAccountId)
            }

            if (!checkAccountValid()) {
                log("Drive sync aborted: account changed or disconnected before starting")
                driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Account switched or disconnected"))
                driveSyncStateStore.recordSyncFailed(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed during sync")
                return@withLock SyncExecutionResult.Fatal(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed during sync")
            }

            try {
                driveSyncStateStore.updateSyncState(DriveSyncState.Running)
                log("Drive sync started (attempt $runAttempt) for device $localDeviceId")

                // 1. Discover remote journals
                val discoveryResult = discoveryService.discoverRemoteJournals(localDeviceId)
                when (discoveryResult) {
                    is DiscoveryResult.Unauthorized -> {
                        log("Drive sync: discovery returned unauthorized")
                        driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Drive auth expired"))
                        driveSyncStateStore.recordSyncFailed(SyncErrorCategory.AUTHENTICATION, "Unauthorized")
                        return@withLock SyncExecutionResult.Fatal(SyncErrorCategory.AUTHENTICATION, "Drive auth expired")
                    }
                    is DiscoveryResult.RateLimited -> {
                        val retryAfter = discoveryResult.retryAfterSeconds ?: 60L
                        log("Drive sync: discovery returned rate limited (retry after $retryAfter seconds)")
                        driveSyncStateStore.recordRateLimit(retryAfter)
                        if (runAttempt < MAX_RETRIES) {
                            driveSyncStateStore.updateSyncState(DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES, retryAfter * 1000))
                            driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Rate limited during discovery")
                            return@withLock SyncExecutionResult.Retry(
                                SyncErrorCategory.RATE_LIMITED,
                                "Rate limited during discovery",
                                retryAfterSeconds = retryAfter,
                            )
                        } else {
                            driveSyncStateStore.updateSyncState(DriveSyncState.Failed(SyncErrorCategory.RATE_LIMITED, "Rate limited during discovery"))
                            driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Rate limited during discovery")
                            return@withLock SyncExecutionResult.Fatal(
                                SyncErrorCategory.RATE_LIMITED,
                                "Rate limited during discovery",
                            )
                        }
                    }
                    is DiscoveryResult.Failure -> {
                        val isRateLimit = discoveryResult.message.contains("rate limit", ignoreCase = true)
                        val isNetwork = discoveryResult.message.contains("offline", ignoreCase = true) ||
                            discoveryResult.message.contains("network", ignoreCase = true) ||
                            discoveryResult.cause is IOException

                        val category = if (isRateLimit) SyncErrorCategory.RATE_LIMITED
                            else if (isNetwork) SyncErrorCategory.NETWORK
                            else SyncErrorCategory.SERVER_ERROR

                        log("Drive sync: discovery failed: ${discoveryResult.message}")
                        if (runAttempt < MAX_RETRIES) {
                            val nextState = if (category == SyncErrorCategory.NETWORK) DriveSyncState.WaitingForNetwork
                                else DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES)
                            driveSyncStateStore.updateSyncState(nextState)
                            driveSyncStateStore.recordSyncFailed(category, discoveryResult.message)
                            return@withLock SyncExecutionResult.Retry(category, discoveryResult.message)
                        } else {
                            driveSyncStateStore.updateSyncState(DriveSyncState.Failed(category, discoveryResult.message))
                            driveSyncStateStore.recordSyncFailed(category, discoveryResult.message)
                            return@withLock SyncExecutionResult.Fatal(category, discoveryResult.message)
                        }
                    }
                    is DiscoveryResult.Success -> {
                        val remoteHeaders = discoveryResult.discoveredJournalsByDevice.values.flatten()
                        if (remoteHeaders.isNotEmpty()) {
                            log("Drive sync: downloading ${remoteHeaders.size} remote journals")
                            val downloadBatch = downloader.downloadAndValidateJournals(remoteHeaders)
                            if (downloadBatch.failed > 0) {
                                log("Drive sync: download batch completed with ${downloadBatch.failed} failures: ${downloadBatch.errors}")
                                val unauthorized = downloadBatch.errors.any { it.contains("unauthorized", ignoreCase = true) }
                                if (unauthorized) {
                                    driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Drive auth expired during download"))
                                    driveSyncStateStore.recordSyncFailed(SyncErrorCategory.AUTHENTICATION, "Unauthorized")
                                    return@withLock SyncExecutionResult.Fatal(SyncErrorCategory.AUTHENTICATION, "Unauthorized during download")
                                }

                                if (downloadBatch.rateLimitRetryAfterSeconds != null) {
                                    val retryAfter = downloadBatch.rateLimitRetryAfterSeconds
                                    driveSyncStateStore.recordRateLimit(retryAfter)
                                    if (runAttempt < MAX_RETRIES) {
                                        driveSyncStateStore.updateSyncState(DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES, retryAfter * 1000))
                                        driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Rate limited during download")
                                        return@withLock SyncExecutionResult.Retry(
                                            SyncErrorCategory.RATE_LIMITED,
                                            "Rate limited during download",
                                            retryAfterSeconds = retryAfter,
                                        )
                                    } else {
                                        driveSyncStateStore.updateSyncState(DriveSyncState.Failed(SyncErrorCategory.RATE_LIMITED, "Rate limited during download"))
                                        driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Rate limited during download")
                                        return@withLock SyncExecutionResult.Fatal(
                                            SyncErrorCategory.RATE_LIMITED,
                                            "Rate limited during download",
                                        )
                                    }
                                }

                                val isRateLimit = downloadBatch.errors.any { it.contains("rate limit", ignoreCase = true) }
                                val isNetwork = downloadBatch.errors.any { it.contains("offline", ignoreCase = true) || it.contains("network", ignoreCase = true) }
                                val category = if (isRateLimit) SyncErrorCategory.RATE_LIMITED
                                    else if (isNetwork) SyncErrorCategory.NETWORK
                                    else SyncErrorCategory.SERVER_ERROR

                                val firstErr = downloadBatch.errors.firstOrNull() ?: "Download failed"
                                if (runAttempt < MAX_RETRIES) {
                                    val nextState = if (category == SyncErrorCategory.NETWORK) DriveSyncState.WaitingForNetwork
                                        else DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES)
                                    driveSyncStateStore.updateSyncState(nextState)
                                    driveSyncStateStore.recordSyncFailed(category, firstErr)
                                    return@withLock SyncExecutionResult.Retry(category, firstErr)
                                } else {
                                    driveSyncStateStore.updateSyncState(DriveSyncState.Failed(category, firstErr))
                                    driveSyncStateStore.recordSyncFailed(category, firstErr)
                                    return@withLock SyncExecutionResult.Fatal(category, firstErr)
                                }
                            }
                        }
                    }
                }

                // 2. Strong Account Revalidation before Ingestion
                if (!checkAccountValid()) {
                    log("Drive sync aborted before ingestion: account switched or disconnected")
                    driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Account switched or disconnected"))
                    driveSyncStateStore.recordSyncFailed(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed during sync")
                    return@withLock SyncExecutionResult.Fatal(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed during sync")
                }

                // Ingest validated remote journals into database
                log("Drive sync: ingesting remote journals into database")
                val ingestionSummary = ingestionEngine.ingestAll()
                log("Drive sync: ingestion finished: applied=${ingestionSummary.appliedOperationsCount}, skipped=${ingestionSummary.skippedOperationsCount}, failedJournals=${ingestionSummary.failedJournalsCount}")

                // 3. Package local pending changes into encrypted journals
                log("Drive sync: packaging local pending changes")
                suspend fun packagePending(): Int {
                    var created = 0
                    while (created < MAX_JOURNAL_CREATION_BATCHES) {
                        when (val createRes = journalWriter.createNextJournal(customDeviceId = localDeviceId)) {
                            is JournalCreationResult.Success -> created++
                            is JournalCreationResult.NoEligibleChanges -> break
                            is JournalCreationResult.Error -> {
                                log("Drive sync: journal writer error: ${createRes.message}")
                                break
                            }
                        }
                    }
                    return created
                }

                val createdCount = packagePending()
                log("Drive sync: packaged $createdCount local journals")

                // 4. Strong Account Revalidation before Upload
                if (!checkAccountValid()) {
                    log("Drive sync aborted before upload: account switched or disconnected")
                    val stagedJournals = journalDao.getReadyToUpload().filter { it.deviceId == localDeviceId }
                    for (staged in stagedJournals) {
                        pendingChangeDao.resetJournaledChangesToPending(staged.journalId)
                        journalDao.deleteById(staged.journalId)
                    }
                    driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Account switched or disconnected"))
                    driveSyncStateStore.recordSyncFailed(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed during sync")
                    return@withLock SyncExecutionResult.Fatal(SyncErrorCategory.ACCOUNT_MISMATCH, "Account changed during sync")
                }

                // Upload ready journals to Google Drive
                log("Drive sync: uploading ready journals")
                val uploadBatch = uploader.uploadReadyJournals(localDeviceId)
                if (uploadBatch.failedJournals > 0) {
                    log("Drive sync: upload batch failed (${uploadBatch.failedJournals} failed): ${uploadBatch.errors}")
                    val unauthorized = uploadBatch.errors.any { it.contains("unauthorized", ignoreCase = true) }
                    if (unauthorized) {
                        driveSyncStateStore.updateSyncState(DriveSyncState.AuthenticationRequired("Drive auth expired during upload"))
                        driveSyncStateStore.recordSyncFailed(SyncErrorCategory.AUTHENTICATION, "Unauthorized")
                        return@withLock SyncExecutionResult.Fatal(SyncErrorCategory.AUTHENTICATION, "Unauthorized during upload")
                    }

                    if (uploadBatch.rateLimitRetryAfterSeconds != null) {
                        val retryAfter = uploadBatch.rateLimitRetryAfterSeconds
                        driveSyncStateStore.recordRateLimit(retryAfter)
                        if (runAttempt < MAX_RETRIES) {
                            driveSyncStateStore.updateSyncState(DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES, retryAfter * 1000))
                            driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Rate limited during upload")
                            return@withLock SyncExecutionResult.Retry(
                                SyncErrorCategory.RATE_LIMITED,
                                "Rate limited during upload",
                                retryAfterSeconds = retryAfter,
                            )
                        } else {
                            driveSyncStateStore.updateSyncState(DriveSyncState.Failed(SyncErrorCategory.RATE_LIMITED, "Rate limited during upload"))
                            driveSyncStateStore.recordSyncFailed(SyncErrorCategory.RATE_LIMITED, "Rate limited during upload")
                            return@withLock SyncExecutionResult.Fatal(
                                SyncErrorCategory.RATE_LIMITED,
                                "Rate limited during upload",
                            )
                        }
                    }

                    val isRateLimit = uploadBatch.errors.any { it.contains("rate limit", ignoreCase = true) }
                    val isNetwork = uploadBatch.errors.any { it.contains("offline", ignoreCase = true) || it.contains("network", ignoreCase = true) }
                    val category = if (isRateLimit) SyncErrorCategory.RATE_LIMITED
                        else if (isNetwork) SyncErrorCategory.NETWORK
                        else SyncErrorCategory.SERVER_ERROR

                    val firstErr = uploadBatch.errors.firstOrNull() ?: "Upload failed"
                    if (runAttempt < MAX_RETRIES) {
                        val nextState = if (category == SyncErrorCategory.NETWORK) DriveSyncState.WaitingForNetwork
                            else DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES)
                        driveSyncStateStore.updateSyncState(nextState)
                        driveSyncStateStore.recordSyncFailed(category, firstErr)
                        return@withLock SyncExecutionResult.Retry(category, firstErr)
                    } else {
                        driveSyncStateStore.updateSyncState(DriveSyncState.Failed(category, firstErr))
                        driveSyncStateStore.recordSyncFailed(category, firstErr)
                        return@withLock SyncExecutionResult.Fatal(category, firstErr)
                    }
                }

                // 5. Post-upload Drain Pass: Catch any local changes that arrived while active
                var drainPass = 0
                while (pendingChangeDao.countPendingChanges() > 0 && drainPass < MAX_DRAIN_PASSES) {
                    drainPass++
                    log("Drive sync: draining pending changes created during sync execution (pass $drainPass)")
                    val newlyPackaged = packagePending()
                    if (newlyPackaged > 0) {
                        if (!checkAccountValid()) {
                            log("Drive sync drain pass aborted: account changed or disconnected")
                            val stagedJournals = journalDao.getReadyToUpload().filter { it.deviceId == localDeviceId }
                            for (staged in stagedJournals) {
                                pendingChangeDao.resetJournaledChangesToPending(staged.journalId)
                                journalDao.deleteById(staged.journalId)
                            }
                            break
                        }
                        val drainUpload = uploader.uploadReadyJournals(localDeviceId)
                        if (drainUpload.failedJournals > 0) {
                            log("Drive sync: drain upload encountered errors: ${drainUpload.errors}")
                            break
                        }
                    } else {
                        break
                    }
                }

                // 6. Completion
                val completionTime = System.currentTimeMillis()
                driveSyncStateStore.recordSyncCompleted(completionTime)
                driveSyncStateStore.updateSyncState(DriveSyncState.Completed(completionTime))
                log("Drive sync: finished successfully at $completionTime")
                SyncExecutionResult.Success(completionTime)
            } catch (ce: CancellationException) {
                log("Drive sync cancelled")
                throw ce
            } catch (e: Exception) {
                log("Drive sync crashed: ${e.message}", e)
                val isNetwork = e is IOException
                val category = if (isNetwork) SyncErrorCategory.NETWORK else SyncErrorCategory.UNKNOWN
                val errMsg = e.message ?: "Unexpected error"
                if (runAttempt < MAX_RETRIES) {
                    val nextState = if (category == SyncErrorCategory.NETWORK) DriveSyncState.WaitingForNetwork
                        else DriveSyncState.Retrying(runAttempt + 1, MAX_RETRIES)
                    driveSyncStateStore.updateSyncState(nextState)
                    driveSyncStateStore.recordSyncFailed(category, errMsg)
                    SyncExecutionResult.Retry(category, errMsg)
                } else {
                    driveSyncStateStore.updateSyncState(DriveSyncState.Failed(category, errMsg))
                    driveSyncStateStore.recordSyncFailed(category, errMsg)
                    SyncExecutionResult.Fatal(category, errMsg)
                }
            }
        }
    }
}
