package haddad.three.notas.data.auth

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>
    val appAuthState: Flow<AppAuthState> get() = kotlinx.coroutines.flow.emptyFlow()
    val currentUser: AuthUser?

    suspend fun registerWithEmailAndPassword(
        email: String,
        password: String,
        username: String? = null,
    ): AuthResult<AuthUser>

    suspend fun loginWithEmailAndPassword(
        email: String,
        password: String,
    ): AuthResult<AuthUser>

    suspend fun signInWithGoogle(context: Context): AuthResult<AuthUser>

    suspend fun sendEmailVerification(): AuthResult<Unit>

    suspend fun reloadUser(): AuthResult<AuthUser>

    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>

    suspend fun confirmPasswordReset(
        actionCode: String,
        newPassword: String,
    ): AuthResult<Unit>

    suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String>

    suspend fun applyActionCode(actionCode: String): AuthResult<Unit>

    suspend fun updatePassword(newPassword: String): AuthResult<Unit>

    suspend fun sendOtpVerification(): AuthResult<Unit> = AuthResult.Success(Unit)

    suspend fun verifyOtpCode(code: String): AuthResult<Unit> = AuthResult.Success(Unit)

    suspend fun requestPasswordResetOtp(email: String): AuthResult<Unit> = AuthResult.Success(Unit)

    suspend fun resetPasswordWithOtp(
        email: String,
        code: String,
        newPassword: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)

    suspend fun checkOtpStatus(): AuthResult<Boolean> = AuthResult.Success(currentUser?.isEmailVerified == true)

    suspend fun signOut()

    suspend fun getIdToken(forceRefresh: Boolean = false): String?

    suspend fun syncBackendProfile(): AuthResult<BackendProfile>
}
