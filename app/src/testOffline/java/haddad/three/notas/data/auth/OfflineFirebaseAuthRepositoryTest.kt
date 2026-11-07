package haddad.three.notas.data.auth

import android.content.ContextWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirebaseAuthRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun offlineRepository_alwaysGuestAndRejectsCloudAuth() = runTest(testDispatcher) {
        val fakeContext = object : ContextWrapper(null) {}
        val fakeApiClient = BackendApiClient { throw IOException("Offline build") }

        val repository = FirebaseAuthRepository(
            context = fakeContext,
            backendApiClient = fakeApiClient,
            ioDispatcher = testDispatcher,
        )

        assertNull(repository.currentUser)
        assertEquals(AppAuthState.Guest, repository.appAuthState.first())
        assertEquals(AuthState.Unauthenticated, repository.authState.first())

        val regResult = repository.registerWithEmailAndPassword("test@example.com", "Password123")
        assertTrue(regResult is AuthResult.Error)
        assertEquals("OFFLINE_BUILD", (regResult as AuthResult.Error).code)

        val loginResult = repository.loginWithEmailAndPassword("test@example.com", "Password123")
        assertTrue(loginResult is AuthResult.Error)
        assertEquals("OFFLINE_BUILD", (loginResult as AuthResult.Error).code)

        val googleResult = repository.signInWithGoogle(fakeContext)
        assertTrue(googleResult is AuthResult.Error)
        assertEquals("OFFLINE_BUILD", (googleResult as AuthResult.Error).code)

        val sendVerificationResult = repository.sendEmailVerification()
        assertTrue(sendVerificationResult is AuthResult.Error)
        assertEquals("OFFLINE_BUILD", (sendVerificationResult as AuthResult.Error).code)

        val sendResetResult = repository.sendPasswordResetEmail("test@example.com")
        assertTrue(sendResetResult is AuthResult.Error)
        assertEquals("OFFLINE_BUILD", (sendResetResult as AuthResult.Error).code)

        repository.signOut()
        assertEquals(AppAuthState.Guest, repository.appAuthState.first())
        assertEquals(AuthState.Unauthenticated, repository.authState.first())

        val token = repository.getIdToken()
        assertNull(token)
    }
}

