package haddad.three.notas.data.drive.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import haddad.three.notas.data.drive.api.GoogleDriveApiClient
import haddad.three.notas.data.drive.model.DriveResult
import haddad.three.notas.data.sync.transport.GoogleDriveSyncTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveAuthLifecycleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeContext = object : ContextWrapper(null) {}
    private lateinit var drivePrefs: DriveAuthPreferences

    @Before
    fun setUp() {
        val prefsFile = File(tempFolder.root, "test_drive_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            produceFile = { prefsFile }
        )
        drivePrefs = DriveAuthPreferences(dataStore)
    }

    private class FakeAuthDelegate(
        var authResult: GoogleDriveAuthorizationResult = GoogleDriveAuthorizationResult.Success(
            accessToken = "initial_token",
            accountEmail = "user@example.com",
            accountId = "12345"
        )
    ) : GoogleDriveAuthDelegate {
        var invalidatedToken: String? = null
        var disconnectedEmail: String? = null
        var authorizeCalls = 0
        var invalidateCalls = 0
        var disconnectCalls = 0

        override suspend fun authorize(
            context: Context,
            forceAccountSelection: Boolean,
        ): GoogleDriveAuthorizationResult {
            authorizeCalls++
            return authResult
        }

        override suspend fun parseConsentResult(
            context: Context,
            data: Intent?,
        ): GoogleDriveAuthorizationResult {
            return authResult
        }

        override suspend fun disconnect(
            context: Context,
            accountEmail: String?,
            accessToken: String?,
        ) {
            disconnectCalls++
            disconnectedEmail = accountEmail
        }

        override suspend fun invalidateToken(
            context: Context,
            token: String,
        ) {
            invalidateCalls++
            invalidatedToken = token
        }
    }

    @Test
    fun testAuthorizationSuccess_returnsTokenAndAccountMetadata() = runTest(testDispatcher) {
        val fakeDelegate = FakeAuthDelegate(
            GoogleDriveAuthorizationResult.Success(
                accessToken = "valid_access_token_abc",
                accountEmail = "testuser@gmail.com",
                accountId = "google_sub_1001"
            )
        )

        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = fakeDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        val token = transport.obtainAccessToken()
        assertEquals("valid_access_token_abc", token)

        val state = drivePrefs.getState()
        assertTrue(state.isConnected)
        assertEquals("testuser@gmail.com", state.connectedEmail)
        assertEquals("google_sub_1001", state.connectedAccountId)
    }

    @Test
    fun testAuthorizationCancellation_doesNotConnect() = runTest(testDispatcher) {
        val fakeDelegate = FakeAuthDelegate(GoogleDriveAuthorizationResult.Cancelled)

        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = fakeDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        val token = transport.obtainAccessToken()
        assertNull(token)

        val state = drivePrefs.getState()
        assertFalse(state.isConnected)
        assertNull(state.connectedEmail)
    }

    @Test
    fun testTokenExpirationAndInvalidation_reauthorizesAutomatically() = runTest(testDispatcher) {
        val fakeDelegate = FakeAuthDelegate(
            GoogleDriveAuthorizationResult.Success(
                accessToken = "expired_token_1",
                accountEmail = "user@example.com"
            )
        )

        var callCount = 0
        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = fakeDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        val result = transport.executeWithAuthAndRetry<String> { token ->
            callCount++
            if (callCount == 1) {
                assertEquals("expired_token_1", token)
                // Simulate refresh token on next authorize call
                fakeDelegate.authResult = GoogleDriveAuthorizationResult.Success(
                    accessToken = "refreshed_token_2",
                    accountEmail = "user@example.com"
                )
                DriveResult.Unauthorized
            } else {
                assertEquals("refreshed_token_2", token)
                DriveResult.Success("file_sync_completed")
            }
        }

        assertTrue(result is DriveResult.Success)
        assertEquals("file_sync_completed", (result as DriveResult.Success).data)
        assertEquals(1, fakeDelegate.invalidateCalls)
        assertEquals("expired_token_1", fakeDelegate.invalidatedToken)
        assertEquals(2, callCount)
    }

    @Test
    fun testReauthorizationFatalFailure_returnsUnauthorizedWithoutLooping() = runTest(testDispatcher) {
        val fakeDelegate = FakeAuthDelegate(
            GoogleDriveAuthorizationResult.Success(
                accessToken = "expired_token",
                accountEmail = "user@example.com"
            )
        )

        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = fakeDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        // When reauthorization fails
        val result = transport.executeWithAuthAndRetry<String> {
            fakeDelegate.authResult = GoogleDriveAuthorizationResult.Failure(
                IllegalStateException("Refresh token revoked")
            )
            DriveResult.Unauthorized
        }

        assertTrue(result is DriveResult.Unauthorized)
        assertEquals(1, fakeDelegate.invalidateCalls)
    }

    @Test
    fun testDisconnect_clearsLocalDriveStateAndDelegates() = runTest(testDispatcher) {
        drivePrefs.setConnected("old_account@gmail.com", "sub_123")
        assertTrue(drivePrefs.getState().isConnected)

        val fakeDelegate = FakeAuthDelegate()
        fakeDelegate.disconnect(fakeContext, "old_account@gmail.com")
        drivePrefs.setDisconnected()

        assertEquals(1, fakeDelegate.disconnectCalls)
        assertEquals("old_account@gmail.com", fakeDelegate.disconnectedEmail)

        val state = drivePrefs.getState()
        assertFalse(state.isConnected)
        assertNull(state.connectedEmail)
        assertNull(state.connectedAccountId)
    }

    @Test
    fun testAccountSwitching_updatesConnectedUserCleanly() = runTest(testDispatcher) {
        val fakeDelegate = FakeAuthDelegate(
            GoogleDriveAuthorizationResult.Success(
                accessToken = "token_alice",
                accountEmail = "alice@gmail.com",
                accountId = "alice_id"
            )
        )

        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = fakeDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        // 1. Connect Alice
        transport.obtainAccessToken()
        assertEquals("alice@gmail.com", drivePrefs.getState().connectedEmail)

        // 2. Disconnect Alice
        fakeDelegate.disconnect(fakeContext, "alice@gmail.com")
        drivePrefs.setDisconnected()
        assertFalse(drivePrefs.getState().isConnected)

        // 3. Connect Bob
        fakeDelegate.authResult = GoogleDriveAuthorizationResult.Success(
            accessToken = "token_bob",
            accountEmail = "bob@gmail.com",
            accountId = "bob_id"
        )
        transport.obtainAccessToken()
        assertEquals("bob@gmail.com", drivePrefs.getState().connectedEmail)
        assertEquals("bob_id", drivePrefs.getState().connectedAccountId)
    }

    @Test
    fun testDriveForbidden403_isClassifiedCorrectly() = runTest(testDispatcher) {
        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = FakeAuthDelegate(),
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        val result = transport.executeWithAuthAndRetry<String> {
            DriveResult.Error("Access forbidden", code = "FORBIDDEN", statusCode = 403)
        }

        assertTrue(result is DriveResult.Error)
        val error = result as DriveResult.Error
        assertEquals("FORBIDDEN", error.code)
        assertEquals(403, error.statusCode)
    }

    @Test
    fun testNetworkFailure_doesNotTriggerInvalidateToken() = runTest(testDispatcher) {
        val fakeDelegate = FakeAuthDelegate()
        val transport = GoogleDriveSyncTransport(
            context = fakeContext,
            driveApiClient = GoogleDriveApiClient(),
            driveAuthDelegate = fakeDelegate,
            drivePrefs = drivePrefs,
            ioDispatcher = testDispatcher,
        )

        val result = transport.executeWithAuthAndRetry<String> {
            DriveResult.Error("Connection timeout", cause = java.net.SocketTimeoutException("timeout"))
        }

        assertTrue(result is DriveResult.Error)
        assertEquals(0, fakeDelegate.invalidateCalls)
    }
}
