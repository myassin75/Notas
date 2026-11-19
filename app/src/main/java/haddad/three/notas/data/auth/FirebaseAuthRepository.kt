package haddad.three.notas.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Offline no-op implementation of [AuthRepository].
 *
 * This implementation contains zero dependencies on Firebase or Google Play Services,
 * ensuring the application remains completely free of proprietary telemetry,
 * analytics, and cloud authentication SDKs.
 */
@Singleton
class FirebaseAuthRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val backendApiClient: BackendApiClient,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val mekManagerProvider: Provider<MekManager>? = null,
    ) : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: Flow<AuthState> = _authState.asStateFlow()

        private val _appAuthState = MutableStateFlow<AppAuthState>(AppAuthState.Guest)
        override val appAuthState: Flow<AppAuthState> = _appAuthState.asStateFlow()

        override val currentUser: AuthUser? = null

        private val offlineError =
            AuthResult.Error(
                message = "Authentication is disabled in the offline build.",
                code = "OFFLINE_BUILD",
            )

        override suspend fun registerWithEmailAndPassword(
            email: String,
            password: String,
            username: String?,
        ): AuthResult<AuthUser> = offlineError

        override suspend fun loginWithEmailAndPassword(
            email: String,
            password: String,
        ): AuthResult<AuthUser> = offlineError

        override suspend fun signInWithGoogle(context: Context): AuthResult<AuthUser> = offlineError

        override suspend fun sendEmailVerification(): AuthResult<Unit> = offlineError

        override suspend fun reloadUser(): AuthResult<AuthUser> = offlineError

        override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = offlineError

        override suspend fun confirmPasswordReset(
            actionCode: String,
            newPassword: String,
        ): AuthResult<Unit> = offlineError

        override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = offlineError

        override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = offlineError

        override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = offlineError

        override suspend fun sendOtpVerification(): AuthResult<Unit> = offlineError

        override suspend fun verifyOtpCode(code: String): AuthResult<Unit> = offlineError

        override suspend fun requestPasswordResetOtp(email: String): AuthResult<Unit> = offlineError

        override suspend fun resetPasswordWithOtp(
            email: String,
            code: String,
            newPassword: String,
        ): AuthResult<Unit> = offlineError

        override suspend fun checkOtpStatus(): AuthResult<Boolean> = AuthResult.Success(false)

        override suspend fun signOut() {
            _authState.value = AuthState.Unauthenticated
            _appAuthState.value = AppAuthState.Guest
        }

        override suspend fun getIdToken(forceRefresh: Boolean): String? = null

        override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = offlineError
    }
