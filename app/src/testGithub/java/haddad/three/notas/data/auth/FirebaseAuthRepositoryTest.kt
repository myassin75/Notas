package haddad.three.notas.data.auth

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun unconfiguredFirebase_returnsFirebaseNotConfiguredError() = runTest(testDispatcher) {
        val fakeContext = object : ContextWrapper(null) {}
        val fakeApiClient = BackendApiClient { throw IOException("No network in test") }

        val repository = FirebaseAuthRepository(
            context = fakeContext,
            backendApiClient = fakeApiClient,
            ioDispatcher = testDispatcher,
        )

        assertNull(repository.currentUser)

        val regResult = repository.registerWithEmailAndPassword("test@example.com", "Password123")
        assertTrue(regResult is AuthResult.Error)
        assertEquals("FIREBASE_NOT_CONFIGURED", (regResult as AuthResult.Error).code)

        val loginResult = repository.loginWithEmailAndPassword("test@example.com", "Password123")
        assertTrue(loginResult is AuthResult.Error)
        assertEquals("FIREBASE_NOT_CONFIGURED", (loginResult as AuthResult.Error).code)

        val resetResult = repository.sendPasswordResetEmail("test@example.com")
        assertTrue(resetResult is AuthResult.Error)
        assertEquals("FIREBASE_NOT_CONFIGURED", (resetResult as AuthResult.Error).code)

        val verifyResult = repository.sendEmailVerification()
        assertTrue(verifyResult is AuthResult.Error)
        assertEquals("FIREBASE_NOT_CONFIGURED", (verifyResult as AuthResult.Error).code)

        val googleResult = repository.signInWithGoogle(fakeContext)
        assertTrue(googleResult is AuthResult.Error)
        assertEquals("FIREBASE_NOT_CONFIGURED", (googleResult as AuthResult.Error).code)

        // Initial state should transition to Guest when Firebase is not configured
        val appState = (repository.appAuthState as kotlinx.coroutines.flow.StateFlow).value
        assertEquals(AppAuthState.Guest, appState)

        // Sign out should not crash and preserve Guest state
        repository.signOut()
        assertEquals(AppAuthState.Guest, (repository.appAuthState as kotlinx.coroutines.flow.StateFlow).value)

        // Profile sync without logged-in user returns UNAUTHENTICATED error
        val syncResult = repository.syncBackendProfile()
        assertTrue(syncResult is AuthResult.Error)
        assertEquals("UNAUTHENTICATED", (syncResult as AuthResult.Error).code)

        // Token should be null
        val token = repository.getIdToken()
        assertNull(token)
    }

    @Test
    fun buildActionCodeSettings_returnsConfiguredSettings() {
        val fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "haddad.three.notas"
        }
        val fakeApiClient = BackendApiClient { throw IOException("No network in test") }

        val repository = FirebaseAuthRepository(
            context = fakeContext,
            backendApiClient = fakeApiClient,
            ioDispatcher = testDispatcher,
        )

        val settings = repository.buildActionCodeSettings()
        assertNotNull(settings)
        assertEquals("https://notas-7db14.firebaseapp.com/__/auth/action", settings?.url)
        assertEquals(true, settings?.canHandleCodeInApp())
        assertEquals("haddad.three.notas", settings?.androidPackageName)
    }
}
