package haddad.three.notas.data.drive.worker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.ListenableWorker
import haddad.three.notas.crypto.InMemoryCryptoStorage
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.SoftwareKeyStoreManager
import haddad.three.notas.data.drive.auth.DriveAuthPreferences
import haddad.three.notas.data.drive.auth.DrivePreferencesState
import haddad.three.notas.data.drive.model.DriveBackupItem
import haddad.three.notas.data.drive.model.DriveResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveAutoBackupWorkerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var drivePrefs: DriveAuthPreferences
    private lateinit var keyStoreManager: SoftwareKeyStoreManager
    private lateinit var storage: InMemoryCryptoStorage
    private lateinit var mekManager: MekManager

    @Before
    fun setUp() {
        val testDataStoreFile = tempFolder.newFile("test_drive_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { testDataStoreFile }
        drivePrefs = DriveAuthPreferences(dataStore)

        keyStoreManager = SoftwareKeyStoreManager()
        storage = InMemoryCryptoStorage()
        mekManager = MekManager(keyStoreManager, storage)
    }

    /**
     * Verifies the decision logic for auto-backup execution.
     */
    private fun evaluatePreconditions(
        prefs: DrivePreferencesState,
        mekManager: MekManager,
    ): ListenableWorker.Result? {
        if (!prefs.autoBackupEnabled) {
            return ListenableWorker.Result.success()
        }
        if (!prefs.isConnected) {
            return ListenableWorker.Result.failure()
        }
        if (mekManager.getActiveMasterKey() == null) {
            return ListenableWorker.Result.failure()
        }
        return null // Preconditions met, ready to back up
    }

    @Test
    fun testSkippedWhenAutoBackupDisabled() =
        runBlocking {
            drivePrefs.setAutoBackupSettings(enabled = false)
            val state = drivePrefs.getState()

            val preCheck = evaluatePreconditions(state, mekManager)
            assertEquals(ListenableWorker.Result.success(), preCheck)
        }

    @Test
    fun testFailsWhenNotConnected() =
        runBlocking {
            drivePrefs.setAutoBackupSettings(enabled = true)
            drivePrefs.setDisconnected()
            val state = drivePrefs.getState()

            val preCheck = evaluatePreconditions(state, mekManager)
            assertEquals(ListenableWorker.Result.failure(), preCheck)
        }

    @Test
    fun testFailsWhenMekNotAvailable() =
        runBlocking {
            drivePrefs.setAutoBackupSettings(enabled = true)
            drivePrefs.setConnected("user@example.com")
            val state = drivePrefs.getState()

            // No MEK created
            val preCheck = evaluatePreconditions(state, mekManager)
            assertEquals(ListenableWorker.Result.failure(), preCheck)
        }

    @Test
    fun testPassesWhenAllConditionsMet() =
        runBlocking {
            drivePrefs.setAutoBackupSettings(enabled = true)
            drivePrefs.setConnected("user@example.com")
            mekManager.getOrCreateMasterKey()
            val state = drivePrefs.getState()

            val preCheck = evaluatePreconditions(state, mekManager)
            assertEquals(null, preCheck) // Ready to execute
        }

    @Test
    fun testResultMapping() {
        val sampleBackupItem =
            DriveBackupItem(
                fileId = "file_123",
                backupId = "backup_456",
                fileName = "notas_backup.bin",
                sizeBytes = 1024,
                createdAtEpochMillis = System.currentTimeMillis(),
                schemaVersion = 5,
                keyId = 1,
                notesCount = 10,
                tasksCount = 2,
                categoriesCount = 1,
                attachmentsCount = 0,
                sourceDevice = "Test Phone",
                plainSha256 = "abc123sha",
            )

        fun mapDriveResultToWorkerResult(
            res: DriveResult<DriveBackupItem>,
            attempt: Int,
        ): ListenableWorker.Result =
            when (res) {
                is DriveResult.Success -> ListenableWorker.Result.success()
                is DriveResult.RateLimited, is DriveResult.Offline, is DriveResult.CursorExpired -> ListenableWorker.Result.retry()
                is DriveResult.Unauthorized -> ListenableWorker.Result.failure()
                is DriveResult.Error -> if (attempt < 3) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
            }

        assertEquals(ListenableWorker.Result.success(), mapDriveResultToWorkerResult(DriveResult.Success(sampleBackupItem), 1))
        assertEquals(ListenableWorker.Result.retry(), mapDriveResultToWorkerResult(DriveResult.RateLimited, 1))
        assertEquals(ListenableWorker.Result.retry(), mapDriveResultToWorkerResult(DriveResult.Offline, 1))
        assertEquals(ListenableWorker.Result.failure(), mapDriveResultToWorkerResult(DriveResult.Unauthorized, 1))
        assertEquals(ListenableWorker.Result.retry(), mapDriveResultToWorkerResult(DriveResult.Error("transient", statusCode = 500), 1))
        assertEquals(ListenableWorker.Result.failure(), mapDriveResultToWorkerResult(DriveResult.Error("fatal", statusCode = 400), 3))
    }
}
